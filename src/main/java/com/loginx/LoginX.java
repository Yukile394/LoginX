package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * LoginX2 – Kayıt / Giriş Sistemi
 * ───────────────────────────────
 * • /register <sifre> <sifre_tekrar>
 * • /login <sifre>
 * • /sifredegis <eski> <yeni>
 * • /logingoster          (OP paneli)
 * • /izinver  /izinengelle  /izinvermenu
 * • /iade                 (ölüm eşyası iadesi)
 * • /ipban  /kickip       (IP cezaları)
 */
public class LoginX2 implements Listener, CommandExecutor {

    private final LoginX core;

    // UUID → şifre hash
    private final Map<UUID, String> passwords = new HashMap<>();
    // UUID → giriş durumu
    private final Set<UUID> loggedIn = new HashSet<>();
    // UUID → yanlış şifre sayısı
    private final Map<UUID, Integer> failCount = new HashMap<>();
    // UUID → giriş countdown task id
    private final Map<UUID, BukkitRunnable> loginTasks = new HashMap<>();
    // UUID → iptal edilmemiş ölüm eşyaları listesi
    private final Map<UUID, List<ItemStack>> deathItems = new HashMap<>();
    // Güvenilir oyuncular (özel eşya izni)
    private final Set<UUID> trustedPlayers = new HashSet<>();
    // IP ban listesi
    private final Set<String> bannedIPs = new HashSet<>();

    private FileConfiguration dataConfig;
    private File dataFile;

    // ==================== CONSTRUCTOR ====================
    public LoginX2(LoginX core) {
        this.core = core;
        loadData();
        registerCommands();
    }

    // ==================== KAYIT ====================
    private void loadData() {
        dataFile = new File(core.getDataFolder(), "players.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        // Mevcut şifreleri yükle
        if (dataConfig.contains("players")) {
            for (String key : Objects.requireNonNull(dataConfig.getConfigurationSection("players")).getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String hash = dataConfig.getString("players." + key + ".hash");
                    if (hash != null) passwords.put(uuid, hash);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        if (dataConfig.contains("ipbans")) {
            bannedIPs.addAll(dataConfig.getStringList("ipbans"));
        }
    }

    private void saveData() {
        try { dataConfig.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    // ==================== KOMUT KAYDI ====================
    private void registerCommands() {
        String[] cmds = {"register","login","logingoster","sifredegis","izinver","izinengelle","izinvermenu","iade","ipban","kickip"};
        for (String c : cmds) {
            PluginCommand cmd = core.getCommand(c);
            if (cmd != null) cmd.setExecutor(this);
        }
    }

    // ==================== EVENTLER ====================
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        // IP Ban
        String ip = Objects.requireNonNull(p.getAddress()).getAddress().getHostAddress();
        if (bannedIPs.contains(ip)) {
            p.kickPlayer(core.color("&#FF0000IP adresiniz banlanmıştır!"));
            return;
        }

        // Konumunu dondur, görünmez yap
        p.setWalkSpeed(0f);
        p.setFlySpeed(0f);
        p.setInvisible(true);

        if (!passwords.containsKey(uuid)) {
            showRegisterTitle(p);
        } else {
            showLoginTitle(p);
            startLoginTimer(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        loggedIn.remove(e.getPlayer().getUniqueId());
        failCount.remove(e.getPlayer().getUniqueId());
        cancelTask(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) {
            Location from = e.getFrom();
            e.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(),
                    e.getTo().getYaw(), e.getTo().getPitch()));
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) {
            if (!loggedIn.contains(p.getUniqueId())) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!trustedPlayers.contains(p.getUniqueId())) return;
        // Güvenilir oyuncu öldü → eşyaları sakla
        List<ItemStack> items = new ArrayList<>(e.getDrops());
        deathItems.put(p.getUniqueId(), items);
        e.getDrops().clear();
        e.setDeathMessage(core.color("&#FF4500☠ &c" + p.getName() + " &7öldü! Eşyaları korunuyor..."));
    }

    // ==================== KOMUTLAR ====================
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase();

        switch (name) {

            // /register
            case "register" -> {
                if (!(sender instanceof Player p)) return true;
                UUID uuid = p.getUniqueId();
                if (passwords.containsKey(uuid)) {
                    p.sendMessage(core.color("&#FF0000Zaten kayıtlısınız! /login ile giriş yapın.")); return true;
                }
                if (args.length < 2) {
                    p.sendMessage(core.color("&#FF0000Kullanım: /register <sifre> <sifre_tekrar>")); return true;
                }
                if (!args[0].equals(args[1])) {
                    p.sendMessage(core.color("&#FF0000Şifreler eşleşmiyor!")); return true;
                }
                if (args[0].length() < 6) {
                    p.sendMessage(core.color("&#FF0000Şifre en az 6 karakter olmalı!")); return true;
                }
                String hash = hashSHA256(args[0]);
                passwords.put(uuid, hash);
                dataConfig.set("players." + uuid + ".hash", hash);
                dataConfig.set("players." + uuid + ".ad", p.getName());
                dataConfig.set("players." + uuid + ".kayit_tarihi", new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date()));
                dataConfig.set("players." + uuid + ".ip", Objects.requireNonNull(p.getAddress()).getAddress().getHostAddress());
                saveData();
                doLogin(p);
                p.sendMessage(core.color("&#00FF00Başarıyla kayıt oldunuz ve giriş yaptınız!"));
            }

            // /login
            case "login" -> {
                if (!(sender instanceof Player p)) return true;
                UUID uuid = p.getUniqueId();
                if (loggedIn.contains(uuid)) {
                    p.sendMessage(core.color("&#FFFF00Zaten giriş yaptınız!")); return true;
                }
                if (!passwords.containsKey(uuid)) {
                    p.sendMessage(core.color("&#FF0000Önce /register ile kayıt olun!")); return true;
                }
                if (args.length == 0) {
                    p.sendMessage(core.color("&#FF0000Kullanım: /login <sifre>")); return true;
                }
                if (passwords.get(uuid).equals(hashSHA256(args[0]))) {
                    doLogin(p);
                    failCount.remove(uuid);
                    p.sendMessage(core.color("&#00FF00Giriş başarılı! Hoş geldiniz."));
                } else {
                    int fail = failCount.getOrDefault(uuid, 0) + 1;
                    failCount.put(uuid, fail);
                    p.sendMessage(core.color("&#FF0000Yanlış şifre! (" + fail + "/5)"));
                    if (fail >= 5) {
                        p.kickPlayer(core.color("&#FF0000Çok fazla yanlış şifre! Sunucudan atıldınız."));
                        failCount.remove(uuid);
                    }
                }
            }

            // /sifredegis
            case "sifredegis" -> {
                if (!(sender instanceof Player p)) return true;
                UUID uuid = p.getUniqueId();
                if (!loggedIn.contains(uuid)) { p.sendMessage(core.color("&#FF0000Önce giriş yapın!")); return true; }
                if (args.length < 2) { p.sendMessage(core.color("&#FF0000Kullanım: /sifredegis <eski_sifre> <yeni_sifre>")); return true; }
                if (!passwords.get(uuid).equals(hashSHA256(args[0]))) {
                    p.sendMessage(core.color("&#FF0000Eski şifre yanlış!")); return true;
                }
                if (args[1].length() < 6) { p.sendMessage(core.color("&#FF0000Yeni şifre en az 6 karakter olmalı!")); return true; }
                String newHash = hashSHA256(args[1]);
                passwords.put(uuid, newHash);
                dataConfig.set("players." + uuid + ".hash", newHash);
                saveData();
                p.sendMessage(core.color("&#00FF00Şifreniz başarıyla değiştirildi!"));
            }

            // /logingoster
            case "logingoster" -> {
                if (!sender.hasPermission("loginx.admin")) { sender.sendMessage(core.color("&#FF0000Yetkiniz yok!")); return true; }
                sender.sendMessage(core.color("&#FFB6C1===== Kayıtlı Oyuncular ====="));
                if (!dataConfig.contains("players")) { sender.sendMessage(core.color("&#FF0000Kayıt yok!")); return true; }
                int i = 1;
                for (String key : Objects.requireNonNull(dataConfig.getConfigurationSection("players")).getKeys(false)) {
                    String ad   = dataConfig.getString("players." + key + ".ad", "?");
                    String tarih = dataConfig.getString("players." + key + ".kayit_tarihi", "?");
                    String ip   = dataConfig.getString("players." + key + ".ip", "?");
                    sender.sendMessage(core.color("&#FFB6C1" + i + ". &f" + ad + " &8| " + tarih + " | " + ip));
                    i++;
                }
            }

            // /izinver
            case "izinver" -> {
                if (args.length == 0) { sender.sendMessage(core.color("&#FF0000Kullanım: /izinver <oyuncu>")); return true; }
                Player hedef = Bukkit.getPlayer(args[0]);
                if (hedef == null) { sender.sendMessage(core.color("&#FF0000Oyuncu bulunamadı!")); return true; }
                trustedPlayers.add(hedef.getUniqueId());
                sender.sendMessage(core.color("&#00FF00" + hedef.getName() + " güvenilir listeye eklendi."));
                hedef.sendMessage(core.color("&#00FF00Özel eşya izni verildi! Öldüğünüzde eşyalarınız korunur."));
            }

            // /izinengelle
            case "izinengelle" -> {
                if (args.length == 0) { sender.sendMessage(core.color("&#FF0000Kullanım: /izinengelle <oyuncu>")); return true; }
                Player hedef = Bukkit.getPlayer(args[0]);
                if (hedef == null) { sender.sendMessage(core.color("&#FF0000Oyuncu bulunamadı!")); return true; }
                trustedPlayers.remove(hedef.getUniqueId());
                sender.sendMessage(core.color("&#FF0000" + hedef.getName() + " güvenilir listeden çıkarıldı."));
            }

            // /izinvermenu
            case "izinvermenu" -> {
                if (!sender.hasPermission("loginx.admin")) { sender.sendMessage(core.color("&#FF0000Yetkiniz yok!")); return true; }
                sender.sendMessage(core.color("&#FFB6C1===== Güvenilir Oyuncular ====="));
                if (trustedPlayers.isEmpty()) { sender.sendMessage(core.color("&#FF0000Liste boş.")); return true; }
                for (UUID uuid : trustedPlayers) {
                    Player op = Bukkit.getPlayer(uuid);
                    sender.sendMessage(core.color("&#00FF00• &f" + (op != null ? op.getName() : uuid.toString())));
                }
            }

            // /iade
            case "iade" -> {
                if (!sender.hasPermission("loginx.admin")) { sender.sendMessage(core.color("&#FF0000Yetkiniz yok!")); return true; }
                if (args.length == 0) { sender.sendMessage(core.color("&#FF0000Kullanım: /iade <oyuncu>")); return true; }
                Player hedef = Bukkit.getPlayer(args[0]);
                if (hedef == null) { sender.sendMessage(core.color("&#FF0000Oyuncu çevrimiçi değil!")); return true; }
                List<ItemStack> items = deathItems.remove(hedef.getUniqueId());
                if (items == null || items.isEmpty()) { sender.sendMessage(core.color("&#FF0000Bu oyuncu için iade edilecek eşya yok!")); return true; }
                for (ItemStack item : items) hedef.getInventory().addItem(item);
                hedef.sendMessage(core.color("&#00FF00Eşyalarınız iade edildi!"));
                sender.sendMessage(core.color("&#00FF00" + hedef.getName() + " eşyaları iade edildi."));
            }

            // /ipban
            case "ipban" -> {
                if (!sender.hasPermission("loginx.admin")) { sender.sendMessage(core.color("&#FF0000Yetkiniz yok!")); return true; }
                if (args.length == 0) { sender.sendMessage(core.color("&#FF0000Kullanım: /ipban <oyuncu|ip>")); return true; }
                String ip = resolveIP(args[0]);
                if (ip == null) { sender.sendMessage(core.color("&#FF0000Oyuncu çevrimiçi değil, IP bulunamadı!")); return true; }
                bannedIPs.add(ip);
                dataConfig.set("ipbans", new ArrayList<>(bannedIPs));
                saveData();
                // Aynı IPdeki herkesi at
                for (Player online : Bukkit.getOnlinePlayers()) {
                    String oip = Objects.requireNonNull(online.getAddress()).getAddress().getHostAddress();
                    if (oip.equals(ip)) online.kickPlayer(core.color("&#FF0000IP adresiniz banlanmıştır!"));
                }
                sender.sendMessage(core.color("&#FF0000" + ip + " IP adresi banlandı."));
            }

            // /kickip
            case "kickip" -> {
                if (!sender.hasPermission("loginx.admin")) { sender.sendMessage(core.color("&#FF0000Yetkiniz yok!")); return true; }
                if (args.length == 0) { sender.sendMessage(core.color("&#FF0000Kullanım: /kickip <ip>")); return true; }
                int count = 0;
                for (Player online : Bukkit.getOnlinePlayers()) {
                    String oip = Objects.requireNonNull(online.getAddress()).getAddress().getHostAddress();
                    if (oip.equals(args[0])) { online.kickPlayer(core.color("&#FF4500IP üzerinden sunucudan atıldınız.")); count++; }
                }
                sender.sendMessage(core.color("&#00FF00" + count + " oyuncu atıldı."));
            }

            default -> { return false; }
        }
        return true;
    }

    // ==================== YARDIMCI ====================
    private void doLogin(Player p) {
        loggedIn.add(p.getUniqueId());
        cancelTask(p.getUniqueId());
        p.setWalkSpeed(0.2f);
        p.setFlySpeed(0.1f);
        p.setInvisible(false);
        // Kayıtlı konuma ışınla
        dataConfig.set("players." + p.getUniqueId() + ".son_giris", new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date()));
        saveData();
    }

    private void startLoginTimer(Player p) {
        int timeout = core.getConfig().getInt("login_timeout", 60);
        BukkitRunnable task = new BukkitRunnable() {
            int kalan = timeout;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                if (loggedIn.contains(p.getUniqueId())) { cancel(); return; }
                if (kalan <= 0) {
                    p.kickPlayer(core.color("&#FF0000Giriş süresi doldu!"));
                    cancel(); return;
                }
                p.sendTitle(core.color("&#00FFFF&lGİRİŞ YAP"), core.color("&f/login <sifre> &8| Kalan: &c" + kalan + "s"), 0, 25, 0);
                kalan--;
            }
        };
        task.runTaskTimer(core, 0L, 20L);
        loginTasks.put(p.getUniqueId(), task);
    }

    private void cancelTask(UUID uuid) {
        BukkitRunnable t = loginTasks.remove(uuid);
        if (t != null) t.cancel();
    }

    private void showRegisterTitle(Player p) {
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline() || loggedIn.contains(p.getUniqueId())) { cancel(); return; }
                p.sendTitle(core.color("&#FF69B4&lKAYIT OL"), core.color("&#FFB6C1&o/register <sifre> <sifre_tekrar>"), 0, 25, 0);
            }
        }.runTaskTimer(core, 0L, 20L);
    }

    private void showLoginTitle(Player p) {
        // Zaten startLoginTimer ile title gönderiliyor
    }

    private String hashSHA256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashed = md.digest((input + "loginx_salt_2025").getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return input; }
    }

    private String resolveIP(String target) {
        Player p = Bukkit.getPlayer(target);
        if (p != null) return Objects.requireNonNull(p.getAddress()).getAddress().getHostAddress();
        // Direkt IP girdiyse
        if (target.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) return target;
        return null;
    }
}
