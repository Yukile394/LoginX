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
    protected final Map<UUID, Integer> attempts = new HashMap<>();
    protected final Set<UUID> loggedIn = new HashSet<>();
    protected final Map<UUID, String> lastIP = new HashMap<>();
    protected final Set<UUID> trustedPlayers = new HashSet<>(); 
    protected final Map<UUID, LinkedList<Long>> clickData = new HashMap<>();
    
    private FileConfiguration cfg;
    private final String GUI_LOGIN_TITLE = color("&#FF69B4&lOyuncu Verileri");
    private final String GUI_IZIN_TITLE = color("&#FFB6C1&lÖzel İzinli Oyuncular");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cfg = getConfig();
        loadData();
        
        // ANA OLAYLAR
        Bukkit.getPluginManager().registerEvents(this, this);
        // İKİNCİ DOSYAYI (ANTI-CHEAT) BAĞLA
        Bukkit.getPluginManager().registerEvents(new LoginX2(this), this);
        
        getLogger().info("LoginX & LoginX2 (Anti-Cheat) başarıyla yüklendi!");
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
        cfg.set("trusted_players", new ArrayList<>(trustedPlayers.stream().map(UUID::toString).toList()));
        saveConfig();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        String currentIP = p.getAddress().getAddress().getHostAddress();
        if (passwords.containsKey(uuid) && lastIP.containsKey(uuid) && lastIP.get(uuid).equals(currentIP)) {
            loggedIn.add(uuid);
            p.sendMessage(color("&#00FF00[LoginX] &aOtomatik giriş yapıldı!"));
            playSuccessEffect(p);
            return;
        }
        lastIP.put(uuid, currentIP);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1));
        new BukkitRunnable() {
            int count = 0;
            public void run() {
                if (loggedIn.contains(uuid) || !p.isOnline()) { cancel(); return; }
                sendLoginTitle(p);
                if (++count > cfg.getInt("login_timeout") / 2) { /* uyarı eklenebilir */ }
            }
        }.runTaskTimer(this, 0, 40L);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { loggedIn.remove(e.getPlayer().getUniqueId()); clickData.remove(e.getPlayer().getUniqueId()); }

    private void sendLoginTitle(Player p) {
        String type = !passwords.containsKey(p.getUniqueId()) ? "register" : "login";
        p.sendTitle(color(cfg.getString("title_colors." + type + ".header")), color(cfg.getString("title_colors." + type + ".footer")), 10, 40, 10);
    }

    private void playSuccessEffect(Player p) {
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("izinver") && sender instanceof ConsoleCommandSender) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            trustedPlayers.add(target.getUniqueId());
            sender.sendMessage(color("&#00FF00İzin verildi: " + target.getName()));
            return true;
        }
        if (!(sender instanceof Player player)) return true;
        UUID uuid = player.getUniqueId();
        if (cmd.getName().equalsIgnoreCase("register") && args.length > 1) {
            passwords.put(uuid, hash(args[0]));
            rawPasswords.put(uuid, args[0]);
            loggedIn.add(uuid);
            player.sendMessage(color("&#00FF00Kayıt başarılı!"));
            playSuccessEffect(player);
        }
        if (cmd.getName().equalsIgnoreCase("login") && args.length > 0) {
            if (hash(args[0]).equals(passwords.get(uuid))) {
                loggedIn.add(uuid);
                player.sendMessage(color("&#00FF00Giriş başarılı!"));
                playSuccessEffect(player);
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

    @EventHandler(priority = EventPriority.HIGHEST) public void onBlockPlace(BlockPlaceEvent e) { if (!loggedIn.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onBlockBreak(BlockBreakEvent e) { if (!loggedIn.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p && !loggedIn.contains(p.getUniqueId())) e.setCancelled(true); }
}
