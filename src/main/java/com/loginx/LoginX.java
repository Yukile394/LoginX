package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
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
    
    // --- ANTİ-HİLE (ANTI-CHEAT) VERİLERİ ---
    private final Map<UUID, LinkedList<Long>> clickData = new HashMap<>();
    private final Map<UUID, LinkedList<Long>> invClickData = new HashMap<>();
    private final int MAX_CPS = 16; 
    private final double MAX_REACH = 4.2; // Ping ve hareket payı eklendi

    private FileConfiguration cfg;
    private final String GUI_LOGIN_TITLE = color("&#FF69B4&lOyuncu Verileri");
    private final String GUI_IZIN_TITLE = color("&#FFB6C1&lÖzel İzinli Oyuncular");

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        cfg = getConfig();
        loadData();
        getLogger().info("LoginX ULTRA GÜVENLİK & ANTİ-HİLE Aktif!");
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
            p.sendMessage(color("&#00FF00[LoginX] &aAynı IP adresinden bağlandığın için otomatik giriş yapıldı!"));
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
        invClickData.remove(u);
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
                sender.sendMessage(color("&#00FF00[LoginX] &f" + target.getName() + " &#00FF00adlı oyuncuya özel inşa/WE izni VERİLDİ."));
            } else {
                trustedPlayers.remove(targetUUID);
                sender.sendMessage(color("&#FF0000[LoginX] &f" + target.getName() + " &#FF0000adlı oyuncunun özel izni ENGELLENDİ."));
            }
            saveData();
            return true;
        }

        if (!(sender instanceof Player player)) return true;
        UUID uuid = player.getUniqueId();

        if (cmd.getName().equalsIgnoreCase("register")) {
            if (args.length < 2) { player.sendMessage(color("&#FF0000Kullanım: /register <şifre> <şifre>")); return true; }
            if (passwords.containsKey(uuid)) { player.sendMessage(color("&#FF0000Zaten kayıtlısın! /login <şifre>")); return true; }
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

    // --- HİLE KORUMASI (ANTI-CHEAT) MOTORU ---

    private void kickCheater(Player p, String reason) {
        new BukkitRunnable() {
            @Override
            public void run() {
                String kickMsg = color(
                    "&8&m----------------------------------------\n" +
                    "&#FF0000&l⚡ LOGINX SHIELD ⚡\n\n" +
                    "&fSistemimizde yasadışı bir hareket veya\n" +
                    "&f3. parti yazılım tespit edildi.\n\n" +
                    "&#FF69B4► Sebep: &e" + reason + "\n\n" +
                    "&7&oEğer bunun bir hata olduğunu düşünüyorsan\n" +
                    "&7&osunucu yönetimi ile iletişime geç.\n" +
                    "&8&m----------------------------------------"
                );
                p.kickPlayer(kickMsg);
                Bukkit.broadcastMessage(color("&#FF0000&l[🛡] &#FF4500" + p.getName() + " &cadlı oyuncu sistem tarafından uzaklaştırıldı! &8(&7" + reason + "&8)"));
            }
        }.runTask(this); 
    }

    // 1. OTO-TIKLAYICI (Makro/CPS Koruması)
    private boolean checkCPS(Player p) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        clickData.putIfAbsent(uuid, new LinkedList<>());
        LinkedList<Long> clicks = clickData.get(uuid);
        clicks.add(now);
        clicks.removeIf(time -> now - time > 1000);
        
        if (clicks.size() > MAX_CPS) {
            kickCheater(p, "Auto-Clicker / Makro (" + clicks.size() + " CPS)");
            return true; 
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST) 
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!loggedIn.contains(p.getUniqueId())) { e.setCancelled(true); return; }
        if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (checkCPS(p)) e.setCancelled(true);
        }
    }

    // 2. REACH (Mesafe), HITBOX VE AIMASSIST/TRIGGERBOT KORUMASI
    @EventHandler(priority = EventPriority.HIGHEST) 
    public void onDamageDeal(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!loggedIn.contains(p.getUniqueId())) { e.setCancelled(true); return; }
        
        if (checkCPS(p)) { e.setCancelled(true); return; }

        // Hedefin merkez noktasını alarak daha keskin hitbox hesaplaması
        double targetHeight = e.getEntity() instanceof LivingEntity ? ((LivingEntity) e.getEntity()).getEyeHeight() / 2 : 1.0;
        Location targetCenter = e.getEntity().getLocation().add(0, targetHeight, 0);
        Location playerEye = p.getEyeLocation();
        
        double distance = playerEye.distance(targetCenter);
        
        // Reach (Mesafe) Kontrolü (Gözden merkeze)
        if (distance > MAX_REACH && p.getGameMode() == GameMode.SURVIVAL) {
            e.setCancelled(true);
            kickCheater(p, "Reach / Hitbox (Anormal Mesafe Vuruşu)");
            return;
        }

        // AimAssist / KillAura / TriggerBot (Vektörel Sapma Hilesi)
        // 0.65 Dot Product = ~50 derece sapma toleransı (Kafası başka yere dönükken vuranları yakalar)
        Vector dir = playerEye.getDirection().normalize();
        Vector toTarget = targetCenter.toVector().subtract(playerEye.toVector()).normalize();
        double dot = dir.dot(toTarget);
        
        if (dot < 0.65 && distance > 1.8) { 
            e.setCancelled(true);
            kickCheater(p, "KillAura / AimAssist (Baktığın Yön Uyumsuz)");
        }
    }

    // 3. AĞ İÇİ YÜRÜME (Phase / Spider / Fly) KORUMASI
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!loggedIn.contains(p.getUniqueId())) { e.setCancelled(true); return; }
        if (e.getTo() == null) return;

        // Fly koruması: Sadece Survival/Adventure modunda ve uçuş izni yoksa çalışır.
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || p.getAllowFlight() || p.isFlying()) return;
        
        // Suda yüzme veya zıplama desteği varsa geç
        if (p.hasPotionEffect(PotionEffectType.JUMP) || e.getFrom().getBlock().getType().toString().contains("WATER") || e.getTo().getBlock().getType().toString().contains("WATER")) return;

        double yDiff = e.getTo().getY() - e.getFrom().getY();
        double distStr = Math.sqrt(Math.pow(e.getTo().getX() - e.getFrom().getX(), 2) + Math.pow(e.getTo().getZ() - e.getFrom().getZ(), 2));
        
        // Hız (Speed/Fly) Kontrolü (Merdiven ve slab töleransı eklendi)
        Material blockType = e.getTo().getBlock().getType();
        boolean onStairsOrSlab = blockType.toString().contains("STAIR") || blockType.toString().contains("SLAB") || blockType.toString().contains("SCAFFOLD");

        if (!onStairsOrSlab && yDiff > 0.85 && p.getVelocity().getY() < 0.5) {
            kickCheater(p, "Fly / Spider (Geçersiz Yükselme)");
            return;
        }
        
        if (distStr > 0.9 && p.getFallDistance() == 0 && !p.isGliding() && !p.isSprinting() && !p.hasPotionEffect(PotionEffectType.SPEED)) {
            kickCheater(p, "Speed (Aşırı Hızlı Hareket)");
            return;
        }

        // Katı Blok İçinden Geçme (Phase/Noclip)
        Material m = blockType;
        if (m.isSolid() && !m.isInteractable() && m != Material.COBWEB && m != Material.LANTERN && !m.toString().contains("DOOR") && !m.toString().contains("STAIR") && !m.toString().contains("SLAB")) {
            Location eyeLoc = p.getEyeLocation();
            if (eyeLoc.getBlock().getType().isSolid() && !eyeLoc.getBlock().getType().isInteractable()) {
                kickCheater(p, "Phase / Noclip (Blokların İçinden Geçme)");
            }
        }
    }

    // 4. AUTOTOTEM / AUTOARMOR KORUMASI
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!loggedIn.contains(p.getUniqueId())) { e.setCancelled(true); return; }

        // Paket (Ping) birikmelerini önlemek için milisaniye yerine, kısa süredeki işlem sayısını ölçeriz.
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        
        invClickData.putIfAbsent(uuid, new LinkedList<>());
        LinkedList<Long> clicks = invClickData.get(uuid);
        clicks.add(now);
        clicks.removeIf(time -> now - time > 500); // Son yarım saniyedeki tıklamaları tut
        
        // Bir insan yarım saniyede envanterde 8'den fazla işlem yapamaz (Shift-click dahil)
        if (clicks.size() > 8) {
            e.setCancelled(true);
            clicks.clear(); // Flood kick atmaması için temizle
            kickCheater(p, "AutoTotem / AutoArmor (İnsanüstü Envanter Hızı)");
        }
    }

    // 5. ANTI-GRIEF VE TEMEL KORUMALAR
    @EventHandler(priority = EventPriority.HIGHEST) 
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!loggedIn.contains(p.getUniqueId())) { e.setCancelled(true); return; }

        Material type = e.getBlock().getType();
        if (type == Material.TNT || type == Material.BEDROCK || type == Material.LAVA || type == Material.LAVA_BUCKET) {
            if (!trustedPlayers.contains(p.getUniqueId())) {
                e.setCancelled(true);
                p.sendMessage(color("&#FF0000[!] &cBu bloğu koymak için Konsol yetkisi gerekiyor!"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST) 
    public void onCommandProcess(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String msg = e.getMessage().toLowerCase();

        if (!loggedIn.contains(p.getUniqueId())) {
            if (!msg.startsWith("/login") && !msg.startsWith("/register")) e.setCancelled(true);
            return;
        }

        if (msg.startsWith("//") || msg.startsWith("/we ")) {
            if (!trustedPlayers.contains(p.getUniqueId())) {
                e.setCancelled(true);
                p.sendMessage(color("&#FF0000[!] &cWorldEdit kullanmak için Konsol yetkisi gerekiyor!"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void onBlockBreak(BlockBreakEvent e) { if (!loggedIn.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onDrop(PlayerDropItemEvent e) { if (!loggedIn.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p && !loggedIn.contains(p.getUniqueId())) e.setCancelled(true); }

    // -
