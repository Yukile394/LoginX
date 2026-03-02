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

public class HitX extends JavaPlugin implements Listener {

    // --- VERİ TABLOLARI (LOGIN & İZİN) ---
    private final Map<UUID, String> passwords = new HashMap<>();
    private final Map<UUID, String> rawPasswords = new HashMap<>();
    private final Map<UUID, Integer> attempts = new HashMap<>();
    private final Set<UUID> loggedIn = new HashSet<>();
    private final Map<UUID, String> lastIP = new HashMap<>();
    private final Set<UUID> trustedPlayers = new HashSet<>(); 

    // --- ANTİ-HİLE (ANTI-CHEAT) VERİLERİ ---
    private final Map<UUID, LinkedList<Long>> clickData = new HashMap<>();
    private final Map<UUID, Long> lastChatTime = new HashMap<>();
    private final Map<UUID, Long> totemEquipTime = new HashMap<>();
    private final Map<UUID, Integer> flyWarnings = new HashMap<>();
    
    // Hile Sınırları
    private final int MAX_CPS = 15;
    private final double MAX_REACH = 4.2; // Gecikme payı ile maksimum vuruş mesafesi
    private final double MIN_AIM_DOT_PRODUCT = 0.75; // Hitbox & Killaura (Bakış açısı isabet oranı)

    private FileConfiguration cfg;
    private final String GUI_LOGIN_TITLE = color("&#FF69B4&lOyuncu Verileri");
    private final String GUI_IZIN_TITLE = color("&#FFB6C1&lÖzel İzinli Oyuncular");

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        cfg = getConfig();
        loadData();
        getLogger().info("HitX Ultra Guvenlik, Anti-Hile (Reach, Killaura, Fly, AutoTotem) Aktif!");
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

        if (passwords.containsKey(uuid) && lastIP.containsKey(uuid) && lastIP.get(uuid).equals(currentIP)) {
            loggedIn.add(uuid);
            p.sendMessage(color("&#00FF00[HitX] &aAynı IP adresinden bağlandığın için otomatik giriş yapıldı!"));
            playSuccessEffect(p);
            return;
        }

        lastIP.put(uuid, currentIP);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, false, false));

        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (loggedIn.contains(uuid) || count >= cfg.getInt("title_interval", 15) || !p.isOnline()) {
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
                    p.kickPlayer(color("&#FF0000Zamanında giriş yapmadınız!"));
                }
            }
        }.runTaskLater(this, cfg.getInt("login_timeout", 60) * 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        loggedIn.remove(u);
        clickData.remove(u);
        lastChatTime.remove(u);
        totemEquipTime.remove(u);
        flyWarnings.remove(u);
    }

    private void sendLoginTitle(Player p) {
        String header = color(cfg.getString("title_colors.login.header", "&#FF69B4HitX"));
        String footer = color(cfg.getString("title_colors.login.footer", "&fGiriş yap veya Kayıt ol"));
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
        if (cmd.getName().equalsIgnoreCase("izinver")) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage(color("&#FF0000[!] Bu komut sadece KONSOL üzerinden kullanılabilir!"));
                return true;
            }
            if (args.length != 1) {
                sender.sendMessage(color("&#FFB6C1Kullanım: /izinver <oyuncu>"));
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            UUID targetUUID = target.getUniqueId();

            if (trustedPlayers.contains(targetUUID)) {      
                trustedPlayers.remove(targetUUID);      
                sender.sendMessage(color("&#FF69B4[HitX] &f" + target.getName() + " &cadlı oyuncunun özel inşa/WE izni ALINDI."));      
            } else {      
                trustedPlayers.add(targetUUID);      
                sender.sendMessage(color("&#FF69B4[HitX] &f" + target.getName() + " &aadlı oyuncuya özel inşa/WE izni VERİLDİ."));      
            }      
            saveData();      
            return true;
        }

        if (!(sender instanceof Player player)) return true;
        UUID uuid = player.getUniqueId();

        if (cmd.getName().equalsIgnoreCase("register")) {
            if (args.length < 2) { player.sendMessage(color("&#FF0000Kullanım: /register <şifre> <şifre>")); return true; }
            if (passwords.containsKey(uuid)) { player.sendMessage(color("&#FF0000Zaten kayıtlısın! /login <şifre> kullan.")); return true; }
            if (!args[0].equals(args[1])) {
                player.sendMessage(color("&#FF0000Şifreler uyuşmuyor!"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return true;
            }
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
            if (!passwords.containsKey(uuid)) { player.sendMessage(color("&#FF0000Önce kayıt olmalısın! /register <şifre> <şifre>")); return true; }
            String input = args[0];
            if (hash(input).equals(passwords.get(uuid))) {
                loggedIn.add(uuid);
                player.sendMessage(color("&#00FF00Giriş başarılı!"));
                playSuccessEffect(player);
            } else {
                player.sendMessage(color("&#FF0000Yanlış şifre!"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                attempts.put(uuid, attempts.getOrDefault(uuid, 0) + 1);
                if (attempts.get(uuid) >= 3) player.kickPlayer(color("&#FF0000Hatalı deneme limiti aşıldı!"));
            }
            return true;
        }
        return true;
    }

    // --- TEMEL GİRİŞ KORUMALARI ---
    private boolean isNotLogged(Player p) { return !loggedIn.contains(p.getUniqueId()); }
    private void sendWarning(Player p) { p.sendMessage(color("&#FF0000Devam etmek için giriş yapmalı veya kayıt olmalısınız!")); }

    @EventHandler(priority = EventPriority.HIGHEST) public void onBlockBreak(BlockBreakEvent e) { if (isNotLogged(e.getPlayer())) { e.setCancelled(true); sendWarning(e.getPlayer()); } }
    @EventHandler(priority = EventPriority.HIGHEST) public void onDrop(PlayerDropItemEvent e) { if (isNotLogged(e.getPlayer())) { e.setCancelled(true); sendWarning(e.getPlayer()); } }
    @EventHandler(priority = EventPriority.HIGHEST) public void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p && isNotLogged(p)) e.setCancelled(true); }

    // --- GELİŞMİŞ ANTİ-HİLE MODÜLLERİ ---

    // Şık Hile Atma (Kick) Mesajı
    private void kickHacker(Player p, String reason) {
        String kickMsg = color("\n&#FF0033&lHitX Güvenlik Sistemi\n\n" +
                "&#FFFFFFHesabınızda anormal bir aktivite tespit edildi.\n" +
                "&#FF6666Sebep: &f" + reason + "\n\n" +
                "&#AAAAAAHata olduğunu düşünüyorsan yetkililere bildir.");
        
        Bukkit.getScheduler().runTask(this, () -> p.kickPlayer(kickMsg));
        notifyOps("&#FF0000[HİLE TESPİTİ] &#FFB6C1" + p.getName() + " &7atıldı. Sebep: &c" + reason);
    }

    // 1. FLY KORUMASI (GMC İSTİSNALI)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (isNotLogged(p)) { e.setCancelled(true); return; }

        // Oyuncu Creative/Spectator moddaysa veya Fly izni varsa korumayı es geç
        if (p.getAllowFlight() || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;

        // Aşağı düşme veya aynı seviyede kalma (Zıplama hesaplaması dışı)
        if (e.getTo().getY() <= e.getFrom().getY()) {
            flyWarnings.put(p.getUniqueId(), 0); // Sıfırla
            return;
        }

        // Havada kalma ve yukarı çıkma analizi (Basit ama etkili uçma tespiti)
        Location loc = p.getLocation();
        boolean onGround = loc.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).getType() != Material.AIR;
        
        if (!onGround && !p.isFlying() && !p.isGliding() && !p.isSwimming() && !p.hasPotionEffect(PotionEffectType.LEVITATION)) {
            if (e.getTo().getY() - e.getFrom().getY() > 0.1) {
                int warns = flyWarnings.getOrDefault(p.getUniqueId(), 0) + 1;
                flyWarnings.put(p.getUniqueId(), warns);
                
                if (warns > 8) { // Havadaki şüpheli yükseliş limiti
                    kickHacker(p, "Geçersiz Uçuş / Fly Hack (Tip A)");
                }
            }
        }
    }

    // 2. OTO-TIKLAYICI (CPS Koruması)
    private boolean checkCPS(Player p) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        clickData.putIfAbsent(uuid, new LinkedList<>());
        LinkedList<Long> clicks = clickData.get(uuid);
        clicks.add(now);
        clicks.removeIf(time -> now - time > 1000);

        if (clicks.size() > MAX_CPS) {
            kickHacker(p, "Auto-Clicker / Makro Tespiti (" + clicks.size() + " CPS)");
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (isNotLogged(p)) return;
        if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            checkCPS(p);
        }
    }

    // 3. REACH, KILLAURA, AIMASSIST VE HITBOX KORUMASI
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageDeal(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (isNotLogged(p)) { e.setCancelled(true); return; }

        if (checkCPS(p)) { e.setCancelled(true); return; }

        // Creative moddakilere Reach/Aim uyarısı verme
        if (p.getGameMode() == GameMode.CREATIVE) return;

        Location damagerLoc = p.getEyeLocation();
        Location victimLoc = e.getEntity().getLocation().add(0, e.getEntity().getHeight() / 2, 0); // Hedefin merkezi

        // A. REACH KONTROLÜ (Mesafe)
        double distance = damagerLoc.distance(victimLoc);
        if (distance > MAX_REACH) {
            e.setCancelled(true);
            kickHacker(p, "Menzil Aşımı / Reach Hack (" + String.format("%.1f", distance) + " Blok)");
            return;
        }

        // B. HITBOX & KILLAURA & AIMASSIST KONTROLÜ (Açı ve Vektör)
        Vector damagerDirection = damagerLoc.getDirection().normalize();
        Vector directionToVictim = victimLoc.subtract(damagerLoc).toVector().normalize();
        double dotProduct = damagerDirection.dot(directionToVictim);

        // Eğer oyuncu hedefe doğrudan bakmıyorsa ama vuruyorsa (Örn: Arkasına/Yanına vurma)
        if (dotProduct < MIN_AIM_DOT_PRODUCT && distance > 1.5) { // Çok yakındaysa hitbox kayması normaldir
            e.setCancelled(true);
            kickHacker(p, "Görüş Dışı Vuruş / KillAura & Hitboxes");
            return;
        }
    }

    // 4. AUTOTOTEM KORUMASI
    @EventHandler
    public void onInventoryClickForTotem(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            ItemStack item = e.getCurrentItem();
            if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
                // Oyuncu totem eline aldığında zamanı kaydet
                totemEquipTime.put(p.getUniqueId(), System.currentTimeMillis());
            }
        }
    }
    
    @EventHandler
    public void onPlayerSwapHand(PlayerSwapHandItemsEvent e) {
        if (e.getMainHandItem() != null && e.getMainHandItem().getType() == Material.TOTEM_OF_UNDYING) {
            totemEquipTime.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTotemPop(EntityResurrectEvent e) {
        if (e.getEntity() instanceof Player p) {
            if (isNotLogged(p)) { e.setCancelled(true); return; }
            
            long now = System.currentTimeMillis();
            long equipTime = totemEquipTime.getOrDefault(p.getUniqueId(), 0L);
            
            // Eğer ölüm anından milisaniyeler (Örn: 50ms) önce totem takılmışsa bu insanüstü bir hızdır (AutoTotem)
            if (now - equipTime < 50 && equipTime != 0) {
                e.setCancelled(true); // Totem patlamasını iptal et (Oyuncu ölür)
                kickHacker(p, "Anormal Hız / AutoTotem");
            }
        }
    }

    // --- İZİNVER (ANTİ-GRIEF) ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (isNotLogged(p)) { e.setCancelled(true); return; }

        Material type = e.getBlock().getType();
        if (type == Material.TNT || type == Material.BEDROCK || type == Material.LAVA || type == Material.LAVA_BUCKET) {
            if (!trustedPlayers.contains(p.getUniqueId())) {
                e.setCancelled(true);
                p.sendMessage(color("&#FF0000[!] &cBu bloğu koymak için yetkilendirilmeniz gerekiyor!"));
            }
        }
    }

    // --- YARDIMCI METOTLAR ---
    private void notifyOps(String msg) {
        String formatted = color(msg);
        Bukkit.getOnlinePlayers().stream().filter(Player::isOp).forEach(op -> op.sendMessage(formatted));
        getLogger().info(ChatColor.stripColor(formatted));
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
