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
    private final Map<UUID, Long> lastQuitTime = new HashMap<>(); // 8 Dakika Kontrolü
    
    // --- ANTİ-HİLE VERİLERİ ---
    private final Map<UUID, LinkedList<Long>> clickData = new HashMap<>();
    private final Map<UUID, Long> lastChatTime = new HashMap<>();
    private final Map<UUID, Long> lastInventoryClick = new HashMap<>();
    private final int MAX_CPS = 16; 
    private final double MAX_REACH = 4.5;

    private FileConfiguration cfg;
    private final String GUI_LOGIN_TITLE = color("&#FF69B4&lOyuncu Verileri");
    private final String GUI_IZIN_TITLE = color("&#FFB6C1&lÖzel İzinli Oyuncular");

    @Override
    public void onEnable() {
        // Kendi eventlerini kaydet
        Bukkit.getPluginManager().registerEvents(this, this);
        
        // --- KRİTİK DÜZELTME: LoginX2'yi Başlatma ---
        try {
            // Eğer LoginX2 sınıfın bir Listener ise buradan kaydediyoruz
            // Not: LoginX2 constructor'ında (LoginX plugin) parametresi istiyorsa ona göre düzenlenebilir
            // Genelde: new LoginX2(this) şeklinde olur.
            LoginX2 egitimModulu = new LoginX2(); 
            Bukkit.getPluginManager().registerEvents(egitimModulu, this);
            getLogger().info("LoginX2 Modülü başarıyla bağlandı!");
        } catch (NoClassDefFoundError | Exception e) {
            getLogger().warning("LoginX2 modülü bulunamadı veya başlatılamadı!");
        }

        saveDefaultConfig();
        cfg = getConfig();
        loadData();
        getLogger().info("LoginX ULTRA GÜVENLİK & ANTİ-HİLE Aktif! (8 Dk IP Koruması)");
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

    // --- GİRİŞ / ÇIKIŞ (8 DAKİKA KONTROLLÜ) ---
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        String currentIP = p.getAddress().getAddress().getHostAddress();

        // 8 Dakika (480.000 ms) Zaman Limit Kontrolü
        long lastQuit = lastQuitTime.getOrDefault(uuid, 0L);
        boolean suresiDolmadi = (System.currentTimeMillis() - lastQuit) <= (8 * 60 * 1000);

        if (passwords.containsKey(uuid) && lastIP.containsKey(uuid) && lastIP.get(uuid).equals(currentIP) && suresiDolmadi && lastQuit != 0L) {
            loggedIn.add(uuid);
            p.sendMessage(color("&#00FF00[LoginX] &aHoş geldin! 8 dakika içinde döndüğün için otomatik giriş yapıldı."));
            playSuccessEffect(p);
            return;
        }

        lastIP.put(uuid, currentIP);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, false, false));

        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (loggedIn.contains(uuid) || count >= cfg.getInt("title_interval") || !p.isOnline()) { cancel(); return; }
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

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        loggedIn.remove(u);
        clickData.remove(u); 
        lastChatTime.remove(u);
        lastInventoryClick.remove(u);
        
        // Çıkış anını kaydet (8 dakika sayacı için)
        lastQuitTime.put(u, System.currentTimeMillis());
    }

    private void sendLoginTitle(Player p) {
        String header = !passwords.containsKey(p.getUniqueId()) ? cfg.getString("title_colors.register.header") : cfg.getString("title_colors.login.header");
        String footer = !passwords.containsKey(p.getUniqueId()) ? cfg.getString("title_colors.register.footer") : cfg.getString("title_colors.login.footer");
        p.sendTitle(color(header), color(footer), 10, 40, 10);
    }

    private void playSuccessEffect(Player p) {
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        p.spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
    }

    // --- KOMUTLAR ---
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        
        if (cmd.getName().equalsIgnoreCase("izinver") || cmd.getName().equalsIgnoreCase("izinengelle")) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage(color("&#FF0000[!] Bu komut sadece KONSOL üzerinden kullanılabilir!"));
                return true;
            }
            if (args.length != 1) {
                sender.sendMessage(color("&#FFB6C1Kullanım: /" + cmd.getName() + " <oyuncu>"));
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            UUID targetUUID = target.getUniqueId();
            
            if (cmd.getName().equalsIgnoreCase("izinver")) {
                trustedPlayers.add(targetUUID);
                sender.sendMessage(color("&#00FF00[LoginX] &f" + target.getName() + " &#00FF00adlı oyuncuya özel yetki VERİLDİ."));
            } else {
                trustedPlayers.remove(targetUUID);
                sender.sendMessage(color("&#FF0000[LoginX] &f" + target.getName() + " &#FF0000adlı oyuncunun özel yetkisi ALINDI."));
            }
            saveData();
            return true;
        }

        if (!(sender instanceof Player player)) return true;
        UUID uuid = player.getUniqueId();

        if (cmd.getName().equalsIgnoreCase("register")) {
            if (args.length < 2) { player.sendMessage(color("&#FF0000Kullanım: /register <şifre> <şifre>")); return true; }
            if (passwords.containsKey(uuid)) { player.sendMessage(color("&#FF0000Zaten kayıtlısın!")); return true; }
            if (!args[0].equals(args[1])) { player.sendMessage(color("&#FF0000Şifreler uyuşmuyor!")); return true; }
            
            passwords.put(uuid, hash(args[0]));
            rawPasswords.put(uuid, args[0]);
            loggedIn.add(uuid);
            saveData();
            player.sendMessage(color("&#00FF00Başarıyla kayıt oldun ve giriş yaptın!"));
            playSuccessEffect(player);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("login")) {
            if (args.length < 1) { player.sendMessage(color("&#FF0000Kullanım: /login <şifre>")); return true; }
            if (!passwords.containsKey(uuid)) { player.sendMessage(color("&#FF0000Önce kayıt olmalısın!")); return true; }
            
            if (hash(args[0]).equals(passwords.get(uuid))) {
                loggedIn.add(uuid);
                player.sendMessage(color("&#00FF00Giriş başarılı!"));
                playSuccessEffect(player);
            } else {
                player.sendMessage(color("&#FF0000Yanlış şifre!"));
                attempts.put(uuid, attempts.getOrDefault(uuid, 0) + 1);
                if (attempts.get(uuid) >= 3) player.kickPlayer(color("&#FF0000Hatalı deneme limiti aşıldı!"));
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("logingoster")) {
            if (!player.hasPermission("loginx.admin")) return true;
            openLoginMenu(player); return true;
        }

        if (cmd.getName().equalsIgnoreCase("izinvermenu")) {
            if (!player.hasPermission("loginx.admin")) return true;
            openIzinMenu(player); return true;
        }

        return true;
    }

    // --- GUI MENÜLER ---
    private void openLoginMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_LOGIN_TITLE);
        for (UUID id : rawPasswords.keySet()) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(id);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName(color("&#FF69B4&l" + (target.getName() != null ? target.getName() : "Bilinmiyor")));
                meta.setLore(Arrays.asList(
                    color("&#FFB6C1► Şifre: &f" + rawPasswords.get(id)),
                    color("&#FFB6C1► Son IP: &f" + lastIP.getOrDefault(id, "Yok")),
                    color("&#FFB6C1► Durum: &aKayıtlı")
                ));
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
                meta.setDisplayName(color("&#FF69B4&l" + (target.getName() != null ? target.getName() : "Bilinmiyor")));
                meta.setLore(Arrays.asList(
                    color("&#FFB6C1► Durum: &aGüvenilir"),
                    color("&7TNT/WE Yetkisi Var.")
                ));
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

    // --- HİLE KORUMASI (ANTI-CHEAT) ---
    private void kickCheater(Player p, String reason) {
        new BukkitRunnable() {
            @Override
            public void run() {
                p.kickPlayer(color("&#FF0000[LoginX Anti-Cheat]\n\n&#FF69B4Sebep: &e" + reason));
                Bukkit.broadcastMessage(color("&#FF0000[Anti-Cheat] &e" + p.getName() + " &catıldı: &7" + reason));
            }
        }.runTask(this);
    }

    private boolean checkCPS(Player p) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        clickData.putIfAbsent(uuid, new LinkedList<>());
        LinkedList<Long> clicks = clickData.get(uuid);
        clicks.add(now);
        clicks.removeIf(time -> now - time > 1000);
        if (clicks.size() > MAX_CPS) { kickCheater(p, "Makro (CPS: " + clicks.size() + ")"); return true; }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST) 
    public void onInteract(PlayerInteractEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) { e.setCancelled(true); return; }
        if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (checkCPS(e.getPlayer())) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST) 
    public void onDamageDeal(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!loggedIn.contains(p.getUniqueId())) { e.setCancelled(true); return; }
        if (checkCPS(p)) { e.setCancelled(true); return; }
        double distance = p.getLocation().distance(e.getEntity().getLocation());
        if (distance > MAX_REACH) { e.setCancelled(true); kickCheater(p, "Reach (Mesafe)"); }
    }

    // --- YARDIMCI METOTLAR (DIŞARIDAN ERİŞİLEBİLİR) ---
    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return input; }
    }

    // LoginX2'nin kullanabilmesi için Public yapıldı
    public String color(String text) {
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

    @EventHandler(priority = EventPriority.HIGHEST) public void onMove(PlayerMoveEvent e) { if (!loggedIn.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onBlockPlace(BlockPlaceEvent e) { if (!loggedIn.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onBlockBreak(BlockBreakEvent e) { if (!loggedIn.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    }
            
