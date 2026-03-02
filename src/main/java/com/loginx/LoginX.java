package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoginX extends JavaPlugin implements Listener {

    // --- VERİ TABLOLARI ---
    private final Map<UUID, String> passwords = new HashMap<>();
    private final Map<UUID, String> rawPasswords = new HashMap<>();
    private final Map<UUID, Integer> attempts = new HashMap<>();
    private final Set<UUID> loggedIn = new HashSet<>();
    private final Map<UUID, String> lastIP = new HashMap<>();
    private final Set<UUID> trustedPlayers = new HashSet<>();

    // --- ANTİ-HİLE VERİLERİ ---
    private final Map<UUID, LinkedList<Long>> clickData = new HashMap<>();
    private final Map<UUID, Long> lastChatTime = new HashMap<>();
    private final Map<UUID, Long> lastTotemTime = new HashMap<>();
    private final int MAX_CPS = 14; 

    private FileConfiguration cfg;
    private final String GUI_LOGIN_TITLE = color("&#FF69B4&lOyuncu Verileri");
    private final String GUI_IZIN_TITLE = color("&#FFB6C1&lÖzel İzinli Oyuncular");
    private final String AC_PREFIX = color("&#FF0000&lLoginX AC &8» ");

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        cfg = getConfig();
        loadData();
        getLogger().info("LoginX Ultra Koruma ve Hile Karşıtı Sistemi Aktif!");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private void loadData() {
        if (cfg.contains("data")) {
            for (String key : cfg.getConfigurationSection("data").getKeys(false)) {
                UUID u = UUID.fromString(key);
                passwords.put(u, cfg.getString("data." + key + ".hash"));
                rawPasswords.put(u, cfg.getString("data." + key + ".raw"));
                lastIP.put(u, cfg.getString("data." + key + ".ip"));
            }
        }
        if (cfg.contains("trusted_players")) {
            for (String uuidStr : cfg.getStringList("trusted_players")) {
                trustedPlayers.add(UUID.fromString(uuidStr));
            }
        }
    }

    private void saveData() {
        cfg.set("data", null);
        for (UUID u : passwords.keySet()) {
            cfg.set("data." + u + ".hash", passwords.get(u));
            cfg.set("data." + u + ".raw", rawPasswords.get(u));
            cfg.set("data." + u + ".ip", lastIP.get(u));
        }
        List<String> trustedList = new ArrayList<>();
        for (UUID u : trustedPlayers) trustedList.add(u.toString());
        cfg.set("trusted_players", trustedList);
        saveConfig();
    }

    // --- GİRİŞ / ÇIKIŞ ---
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        String currentIP = p.getAddress().getAddress().getHostAddress();

        if (passwords.containsKey(uuid) && lastIP.containsKey(uuid) && lastIP.get(uuid).equals(currentIP)) {
            loggedIn.add(uuid);
            p.sendMessage(color("&#00FF00[LoginX] &aHoş geldin! IP adresin tanındı, giriş yapıldı."));
            playSuccessEffect(p);
            return;
        }

        lastIP.put(uuid, currentIP);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 60, 1));

        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (loggedIn.contains(uuid) || !p.isOnline()) { cancel(); return; }
                sendLoginTitle(p);
                count++;
                if(count > cfg.getInt("login_timeout") / 2) {
                     p.sendMessage(color("&#FF4500Lütfen giriş yapın, aksi halde atılacaksınız!"));
                }
            }
        }.runTaskTimer(this, 0, 40L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline() && !loggedIn.contains(uuid)) {
                    p.kickPlayer(color("&#FF0000Giriş süresi doldu!"));
                }
            }
        }.runTaskLater(this, cfg.getInt("login_timeout") * 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        loggedIn.remove(u);
        clickData.remove(u);
        lastChatTime.remove(u);
        lastTotemTime.remove(u);
    }

    // --- HİLE KORUMA SİSTEMİ (ENHANCED) ---

    // 1. FLY KORUMASI (GMC Hariç)
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (isNotLogged(p)) { e.setCancelled(true); return; }

        // Sadece Hayatta Kalma ve Macera modunda çalışır
        if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
            if (p.isFlying() && !p.hasPermission("loginx.fly")) {
                p.setFlying(false);
                p.setAllowFlight(false);
                p.teleport(e.getFrom());
                p.sendMessage(AC_PREFIX + color("&#FFB6C1Uçuş hilesi engellendi!"));
            }
        }
    }

    // 2. KILL-AURA & REACH & HITBOX KORUMASI
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombat(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (isNotLogged(p)) { e.setCancelled(true); return; }

        Entity target = e.getEntity();
        
        // Reach Kontrolü (Maksimum 3.8 blok)
        double distance = p.getLocation().distance(target.getLocation());
        if (distance > 3.8 && p.getGameMode() != GameMode.CREATIVE) {
            e.setCancelled(true);
            p.sendMessage(AC_PREFIX + color("&#FFB6C1Erişim mesafesi çok uzak! (Reach)"));
            return;
        }

        // AimAssist & KillAura (Bakış Açısı Kontrolü)
        Vector direction = p.getLocation().getDirection();
        Vector towardTarget = target.getLocation().toVector().subtract(p.getLocation().toVector());
        double dot = direction.normalize().dot(towardTarget.normalize());
        
        // Oyuncu hedefe çok ters bir açıyla vuruyorsa engelle
        if (dot < 0.65) { 
            e.setCancelled(true);
            p.sendMessage(AC_PREFIX + color("&#FFB6C1Hatalı vuruş açısı! (KillAura/Aim)"));
            return;
        }

        // CPS Kontrolü
        if (checkCPS(p)) e.setCancelled(true);
    }

    // 3. AUTO-TOTEM KORUMASI
    @EventHandler
    public void onTotem(EntityResurrectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        
        long now = System.currentTimeMillis();
        long last = lastTotemTime.getOrDefault(p.getUniqueId(), 0L);

        // Bir totem patladıktan sonra 50ms içinde tekrar patlaması imkansızdır (İnsan hızında)
        if (now - last < 50) {
            e.setCancelled(true);
            p.sendMessage(AC_PREFIX + color("&#FFB6C1Anormal Totem kullanımı! (AutoTotem)"));
        }
        lastTotemTime.put(p.getUniqueId(), now);
    }

    private boolean checkCPS(Player p) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        clickData.putIfAbsent(uuid, new LinkedList<>());
        LinkedList<Long> clicks = clickData.get(uuid);
        clicks.add(now);
        clicks.removeIf(time -> now - time > 1000);

        if (clicks.size() > MAX_CPS) {
            p.sendMessage(AC_PREFIX + color("&#FFB6C1Çok hızlı tıklıyorsun!"));
            return true;
        }
        return false;
    }

    // --- KOMUTLAR ---
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        
        if (cmd.getName().equalsIgnoreCase("izinver")) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage(color("&#FF0000[!] Bu komut sadece KONSOL içindir!"));
                return true;
            }
            if (args.length != 1) return false;
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            trustedPlayers.add(target.getUniqueId());
            sender.sendMessage(color("&#FF69B4[LoginX] &f" + target.getName() + " &#FF69B4listeye eklendi."));
            saveData();
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("izinengelle")) {
            if (!(sender instanceof ConsoleCommandSender)) return true;
            if (args.length != 1) return false;
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            trustedPlayers.remove(target.getUniqueId());
            sender.sendMessage(color("&#FF69B4[LoginX] &f" + target.getName() + " &#FF69B4listeden çıkarıldı."));
            saveData();
            return true;
        }

        if (!(sender instanceof Player player)) return true;
        UUID uuid = player.getUniqueId();

        if (cmd.getName().equalsIgnoreCase("register")) {
            if (passwords.containsKey(uuid)) {
                player.sendMessage(color("&#FF0000Zaten kayıtlısın!"));
                return true;
            }
            if (args.length < 2 || !args[0].equals(args[1])) {
                player.sendMessage(color("&#FF0000Şifreler uyuşmuyor veya eksik!"));
                return true;
            }
            passwords.put(uuid, hash(args[0]));
            rawPasswords.put(uuid, args[0]);
            loggedIn.add(uuid);
            saveData();
            playSuccessEffect(player);
            player.sendMessage(color("&#00FF00Başarıyla kayıt oldun!"));
            notifyOps(player, "&#FF69B4[YENİ KAYIT] &fŞifre: " + args[0]);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("login")) {
            if (args.length < 1) return false;
            if (hash(args[0]).equals(passwords.get(uuid))) {
                loggedIn.add(uuid);
                playSuccessEffect(player);
                player.sendMessage(color("&#00FF00Başarıyla giriş yaptın!"));
                notifyOps(player, "&#00FF00[GİRİŞ] &fŞifre: " + args[0]);
            } else {
                int a = attempts.getOrDefault(uuid, 0) + 1;
                attempts.put(uuid, a);
                player.sendMessage(color("&#FF0000Hatalı şifre!"));
                if (a >= 3) player.kickPlayer(color("&#FF00003 kez hatalı şifre girdiniz!"));
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("logingoster")) {
            if (player.hasPermission("loginx.admin")) openLoginMenu(player);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("izinvermenu")) {
            if (player.hasPermission("loginx.admin")) openIzinMenu(player);
            return true;
        }

        return true;
    }

    // --- YARDIMCI METOTLAR ---
    private void openLoginMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_LOGIN_TITLE);
        rawPasswords.forEach((id, pass) -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(id);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName(color("&#FF69B4&l" + (target.getName() != null ? target.getName() : "Bilinmiyor")));
                meta.setLore(Arrays.asList(color("&#FFB6C1► Şifre: &f" + pass), color("&#FFB6C1► IP: &f" + lastIP.get(id))));
                head.setItemMeta(meta);
            }
            gui.addItem(head);
        });
        player.openInventory(gui);
    }

    private void openIzinMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, GUI_IZIN_TITLE);
        for (UUID id : trustedPlayers) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(id);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName(color("&#FF69B4&l" + target.getName()));
                meta.setLore(Collections.singletonList(color("&aGüvenilir &7(TNT/WE İzinli)")));
                head.setItemMeta(meta);
            }
            gui.addItem(head);
        }
        player.openInventory(gui);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals(GUI_LOGIN_TITLE) || e.getView().getTitle().equals(GUI_IZIN_TITLE)) e.setCancelled(true);
    }

    private boolean isNotLogged(Player p) { return !loggedIn.contains(p.getUniqueId()); }
    
    private void sendLoginTitle(Player p) {
        boolean reg = !passwords.containsKey(p.getUniqueId());
        String h = color(cfg.getString("title_colors." + (reg ? "register" : "login") + ".header"));
        String f = color(cfg.getString("title_colors." + (reg ? "register" : "login") + ".footer"));
        p.sendTitle(h, f, 0, 40, 10);
    }

    private void playSuccessEffect(Player p) {
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        p.spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1.5, 0), 30, 0.5, 0.5, 0.5, 0.1);
    }

    private void notifyOps(Player p, String info) {
        String msg = color("&#FF69B4&lLoginX &8| &b" + p.getName() + " &8» " + info);
        Bukkit.getOnlinePlayers().stream().filter(Player::isOp).forEach(op -> op.sendMessage(msg));
    }

    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return input; }
    }

    private String color(String text) {
        if (text == null) return "";
        Pattern pattern = Pattern.compile("&#([a-fA-F0-9]{6})");
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) replacement.append("§").append(c);
            matcher.appendReplacement(buffer, replacement.toString());
        }
        return ChatColor.translateAlternateColorCodes('&', matcher.appendTail(buffer).toString());
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void onBlockPlace(BlockPlaceEvent e) {
        if (isNotLogged(e.getPlayer())) { e.setCancelled(true); return; }
        Material m = e.getBlock().getType();
        if ((m == Material.TNT || m == Material.LAVA) && !trustedPlayers.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(color("&#FF0000[!] Bu işlem için özel izniniz yok!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void onCommandProcess(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String m = e.getMessage().toLowerCase();
        if (isNotLogged(p)) {
            if (!m.startsWith("/login") && !m.startsWith("/register")) e.setCancelled(true);
            return;
        }
        if ((m.startsWith("//") || m.startsWith("/we ")) && !trustedPlayers.contains(p.getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(color("&#FF0000[!] WorldEdit izniniz bulunmuyor!"));
        }
    }
}
