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
import org.bukkit.event.player.*;
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

    protected final Map<UUID, String> passwords = new HashMap<>(); 
    protected final Map<UUID, String> rawPasswords = new HashMap<>(); 
    protected final Map<UUID, Integer> attempts = new HashMap<>();
    protected final Set<UUID> loggedIn = new HashSet<>();
    protected final Map<UUID, String> lastIP = new HashMap<>();
    protected final Set<UUID> trustedPlayers = new HashSet<>(); // İzinver Sistemi
    
    private final Map<UUID, LinkedList<Long>> clickData = new HashMap<>();
    private final Map<UUID, Long> lastChatTime = new HashMap<>();
    private final int MAX_CPS = 15; // Saniyedeki maksimum tıklama (Auto-Clicker sınırı)

    private FileConfiguration cfg;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        // LoginX2'yi sisteme bağla
        Bukkit.getPluginManager().registerEvents(new LoginX2(this), this);
        saveDefaultConfig();
        cfg = getConfig();
        loadData();
        getLogger().info("LoginX Ana Sistem Aktif!");
    }

    @Override
    public void onDisable() { saveData(); }

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
    }

    public void saveData() {
        cfg.set("data", null);
        for (UUID u : passwords.keySet()) {
            cfg.set("data." + u + ".hash", passwords.get(u));
            cfg.set("data." + u + ".raw", rawPasswords.get(u));
            cfg.set("data." + u + ".ip", lastIP.get(u));
        }
        cfg.set("trusted_players", new ArrayList<>(trustedPlayers.stream().map(UUID::toString).toList()));
        saveConfig();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        String currentIP = p.getAddress().getAddress().getHostAddress();
        if (passwords.containsKey(uuid) && Objects.equals(lastIP.get(uuid), currentIP)) {
            loggedIn.add(uuid);
            p.sendMessage(color("&#00FF00[LoginX] &aOtomatik giriş yapıldı!"));
            return;
        }
        lastIP.put(uuid, currentIP);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1));
        new BukkitRunnable() {
            int count = 0;
            public void run() {
                if (loggedIn.contains(uuid) || !p.isOnline()) { cancel(); return; }
                p.sendTitle(color(cfg.getString("title_colors.login.header")), color(cfg.getString("title_colors.login.footer")), 10, 40, 10);
            }
        }.runTaskTimer(this, 0, 40L);
    }

    // --- HİLE KORUMALARI (CPS & REACH) ---
    private boolean checkCPS(Player p) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        clickData.putIfAbsent(uuid, new LinkedList<>());
        LinkedList<Long> clicks = clickData.get(uuid);
        clicks.add(now);
        clicks.removeIf(time -> now - time > 1000);
        return clicks.size() > MAX_CPS;
    }

    @EventHandler(priority = EventPriority.HIGHEST) 
    public void onInteract(PlayerInteractEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
        if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (checkCPS(e.getPlayer())) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            if (!loggedIn.contains(p.getUniqueId())) e.setCancelled(true);
            if (checkCPS(p)) e.setCancelled(true);
            
            double targetHeight = e.getEntity() instanceof LivingEntity ? ((LivingEntity) e.getEntity()).getEyeHeight() / 2 : 1.0;
            Location targetCenter = e.getEntity().getLocation().add(0, targetHeight, 0);
            if (p.getEyeLocation().distance(targetCenter) > 4.3 && p.getGameMode() == GameMode.SURVIVAL) e.setCancelled(true);
        }
    }

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

    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return input; }
    }
    }
