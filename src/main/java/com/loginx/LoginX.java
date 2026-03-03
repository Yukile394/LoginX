package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

public class LoginX extends JavaPlugin implements Listener {

    // --- VERİLER ---
    private final Map<UUID, String> passwords = new HashMap<>();
    private final Map<UUID, String> rawPasswords = new HashMap<>();
    private final Map<UUID, Integer> attempts = new HashMap<>();
    private final Set<UUID> loggedIn = new HashSet<>();
    private final Map<UUID, String> lastIP = new HashMap<>();
    private final Set<UUID> trustedPlayers = new HashSet<>();

    // BALTA İZİN
    private final Set<UUID> axeAllowed = new HashSet<>();

    // ANTİ HİLE
    private final Map<UUID, LinkedList<Long>> clickData = new HashMap<>();
    private final Map<UUID, Integer> cheatWarnings = new HashMap<>();
    private final Map<UUID, Long> lastHitTime = new HashMap<>();
    private final Map<UUID, Double> reachData = new HashMap<>();
    private final Map<UUID, Integer> violationPoints = new HashMap<>();
    private final Map<UUID, LinkedList<Long>> blockBreakData = new HashMap<>();
    private final Map<UUID, Long> lastVelocityTime = new HashMap<>();

    private final int MAX_WARN = 3;
    private final int MAX_CPS = 14;
    private final double MAX_REACH = 3.1;
    private final int MAX_BLOCK_BREAK_PER_SEC = 15;
    private final double MIN_KNOCKBACK = 0.3;

    private FileConfiguration cfg;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        cfg = getConfig();
    }

    @Override
    public void onDisable() {
        saveConfig();
    }

    // =========================
    // BALTA KOMUTLARI (SADECE KONSOL)
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("baltaizinver")) {
            if (!(sender instanceof ConsoleCommandSender)) { sender.sendMessage("Sadece konsol!"); return true; }
            if (args.length != 1) return true;
            OfflinePlayer t = Bukkit.getOfflinePlayer(args[0]);
            axeAllowed.add(t.getUniqueId());
            sender.sendMessage("Balta izni verildi.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("baltaizincikar")) {
            if (!(sender instanceof ConsoleCommandSender)) { sender.sendMessage("Sadece konsol!"); return true; }
            if (args.length != 1) return true;
            OfflinePlayer t = Bukkit.getOfflinePlayer(args[0]);
            axeAllowed.remove(t.getUniqueId());
            sender.sendMessage("Balta izni kaldırıldı.");
            return true;
        }
        return false;
    }

    // =========================
    // BALTA ENGEL
    private boolean isAxe(Material m) { return m.name().endsWith("_AXE"); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAxeDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (p.getGameMode() == GameMode.CREATIVE) return;
        if (p.getAllowFlight()) return;
        if (!axeAllowed.contains(p.getUniqueId())) {
            ItemStack item = p.getInventory().getItemInMainHand();
            if (item != null && isAxe(item.getType())) {
                e.setCancelled(true);
                p.sendMessage("§cBalta kullanma iznin yok!");
            }
        }
    }

    // =========================
    // CPS KONTROL
    private boolean checkCPS(Player p) {
        if (p.getGameMode() == GameMode.CREATIVE || p.getAllowFlight()) return false;
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        clickData.putIfAbsent(uuid, new LinkedList<>());
        LinkedList<Long> clicks = clickData.get(uuid);
        clicks.add(now);
        clicks.removeIf(t -> now - t > 1000);
        if (clicks.size() > MAX_CPS) { warnCheater(p, "CPS"); return true; }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (checkCPS(p)) e.setCancelled(true);
    }

    // =========================
    // TRIGGERBOT
    @EventHandler
    public void onTrigger(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (p.getGameMode() == GameMode.CREATIVE) return;
        if (p.getAllowFlight()) return;
        long now = System.currentTimeMillis();
        long last = lastHitTime.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < 150) { warnCheater(p, "TriggerBot"); e.setCancelled(true); }
        lastHitTime.put(p.getUniqueId(), now);
    }

    // =========================
    // AUTOTOTEM
    @EventHandler
    public void onTotem(EntityResurrectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (p.getGameMode() == GameMode.CREATIVE || p.getAllowFlight()) return;
        if (!p.getInventory().contains(Material.TOTEM_OF_UNDYING)) {
            warnCheater(p, "AutoTotem");
            e.setCancelled(true);
        }
    }

    // =========================
    // REACH / KILLAURA
    @EventHandler
    public void onReachCheck(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (p.getGameMode() == GameMode.CREATIVE || p.getAllowFlight()) return;
        if (!(e.getEntity() instanceof Player target)) return;

        double reach = p.getLocation().distance(target.getLocation());
        reachData.put(p.getUniqueId(), reach);

        if (reach > MAX_REACH) {
            warnCheater(p, "Reach");
            e.setCancelled(true);
            addViolation(p, 1);
        }
    }

    // =========================
    // FAST BREAK (HIZLI BLOK KIRMA)
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE || p.getAllowFlight()) return;
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        blockBreakData.putIfAbsent(uuid, new LinkedList<>());
        LinkedList<Long> breaks = blockBreakData.get(uuid);
        breaks.add(now);
        breaks.removeIf(t -> now - t > 1000);
        if (breaks.size() > MAX_BLOCK_BREAK_PER_SEC) { warnCheater(p, "FastBreak"); e.setCancelled(true); }
    }

    // =========================
    // VELOCITY / KNOCKBACK KONTROLÜ
    @EventHandler
    public void onVelocity(PlayerVelocityEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE || p.getAllowFlight()) return;
        double vel = e.getVelocity().length();
        if (vel < MIN_KNOCKBACK) {
            warnCheater(p, "NoVelocity");
            addViolation(p, 1);
        }
        lastVelocityTime.put(p.getUniqueId(), System.currentTimeMillis());
    }

    // =========================
    // VIOLATION SYSTEM
    private void addViolation(Player p, int points) {
        int vl = violationPoints.getOrDefault(p.getUniqueId(), 0) + points;
        violationPoints.put(p.getUniqueId(), vl);
        if (vl >= 10) p.kickPlayer(color("&#FF0000Hile tespit edildi!"));
    }

    // =========================
    // UYARI SİSTEMİ
    private void warnCheater(Player p, String reason) {
        UUID id = p.getUniqueId();
        int warn = cheatWarnings.getOrDefault(id, 0) + 1;
        cheatWarnings.put(id, warn);

        if (warn == 1) {
            p.sendMessage(color("&#FF69B4[Anti-Hile] &fHile algılandı! &7(" + reason + ")"));
        } else if (warn == 2) {
            p.sendMessage(color("&#FFFFFF[Anti-Hile] &cHileyi kapat! Son uyarı!"));
        } else {
            p.kickPlayer(color("&#FF0000Hile tespit edildi!"));
        }
    }

    // =========================
    // RGB RENK
    private String color(String text) {
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

    // =========================
    // HASH
    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }
        }
