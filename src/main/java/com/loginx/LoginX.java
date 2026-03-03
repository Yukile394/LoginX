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

    protected final Map<UUID, String> passwords = new HashMap<>(); 
    protected final Map<UUID, String> rawPasswords = new HashMap<>(); 
    protected final Set<UUID> loggedIn = new HashSet<>();
    protected final Map<UUID, String> lastIP = new HashMap<>();
    protected final Set<UUID> trustedPlayers = new HashSet<>(); 
    protected final Map<UUID, LinkedList<Long>> clickData = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();
        Bukkit.getPluginManager().registerEvents(this, this);
        // LoginX2'yi (Moderasyon ve AutoTotem) bağla
        Bukkit.getPluginManager().registerEvents(new LoginX2(this), this);
        getLogger().info("LoginX Sistemleri Aktif!");
    }

    private void loadData() {
        FileConfiguration cfg = getConfig();
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
        FileConfiguration cfg = getConfig();
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
        String currentIP = p.getAddress().getAddress().getHostAddress();
        if (passwords.containsKey(p.getUniqueId()) && Objects.equals(lastIP.get(p.getUniqueId()), currentIP)) {
            loggedIn.add(p.getUniqueId());
            p.sendMessage(color("&#00FF00[LoginX] &aAynı IP adresinden bağlandığın için otomatik giriş yapıldı!"));
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            return;
        }
        lastIP.put(p.getUniqueId(), currentIP);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1));
        new BukkitRunnable() {
            int count = 0;
            public void run() {
                if (loggedIn.contains(p.getUniqueId()) || !p.isOnline()) { cancel(); return; }
                String type = !passwords.containsKey(p.getUniqueId()) ? "register" : "login";
                p.sendTitle(color(getConfig().getString("title_colors." + type + ".header")), color(getConfig().getString("title_colors." + type + ".footer")), 10, 40, 10);
                if (++count > getConfig().getInt("login_timeout")) p.kickPlayer(color("&#FF0000Süre doldu!"));
            }
        }.runTaskTimer(this, 0, 40L);
    }

    // --- TEMEL HİLE KORUMALARI ---
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!loggedIn.contains(p.getUniqueId())) { e.setCancelled(true); return; }
        if (p.getGameMode() != GameMode.SURVIVAL || p.isFlying()) return;

        double yDiff = e.getTo().getY() - e.getFrom().getY();
        if (yDiff > 0.8 && p.getVelocity().getY() < 0.1) {
            e.setCancelled(true);
            p.kickPlayer(color("&#FF0000Fly Tespit Edildi!"));
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            if (!loggedIn.contains(p.getUniqueId())) { e.setCancelled(true); return; }
            if (p.getLocation().distance(e.getEntity().getLocation()) > 4.5) {
                e.setCancelled(true);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (cmd.getName().equalsIgnoreCase("register") && args.length > 1) {
            passwords.put(player.getUniqueId(), hash(args[0]));
            rawPasswords.put(player.getUniqueId(), args[0]);
            loggedIn.add(player.getUniqueId());
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.sendMessage(color("&#00FF00Kayıt başarılı!"));
        }
        if (cmd.getName().equalsIgnoreCase("login") && args.length > 0) {
            if (hash(args[0]).equals(passwords.get(player.getUniqueId()))) {
                loggedIn.add(player.getUniqueId());
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                player.sendMessage(color("&#00FF00Giriş başarılı!"));
            }
        }
        return true;
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
