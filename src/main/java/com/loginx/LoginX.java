package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoginX extends JavaPlugin implements Listener {

    protected final Map<UUID, String> passwords = new HashMap<>(); 
    protected final Map<UUID, String> rawPasswords = new HashMap<>(); 
    protected final Set<UUID> loggedIn = new HashSet<>();
    protected final Map<UUID, String> lastIP = new HashMap<>();
    protected final Set<UUID> trustedPlayers = new HashSet<>(); 
    
    private final Map<UUID, LinkedList<Long>> clickData = new HashMap<>();
    private final int MAX_CPS = 15;

    private FileConfiguration cfg;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new LoginX2(this), this);
        saveDefaultConfig();
        cfg = getConfig();
        loadData();
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
    }

    public void saveData() {
        cfg.set("data", null);
        for (UUID u : passwords.keySet()) {
            cfg.set("data." + u + ".hash", passwords.get(u));
            cfg.set("data." + u + ".raw", rawPasswords.get(u));
            cfg.set("data." + u + ".ip", lastIP.get(u));
        }
        saveConfig();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        if (passwords.containsKey(uuid) && Objects.equals(lastIP.get(uuid), p.getAddress().getAddress().getHostAddress())) {
            loggedIn.add(uuid);
            return;
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1));
    }

    @EventHandler(priority = EventPriority.HIGHEST) 
    public void onInteract(PlayerInteractEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
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
