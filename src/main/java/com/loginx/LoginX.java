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

import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoginX extends JavaPlugin implements Listener {

    private final Map<UUID, String> passwords = new HashMap<>();
    private final Map<UUID, String> rawPasswords = new HashMap<>();
    private final Map<UUID, Integer> attempts = new HashMap<>();
    private final Set<UUID> loggedIn = new HashSet<>();
    private final Map<UUID, String> lastIP = new HashMap<>();
    private final Set<UUID> trustedPlayers = new HashSet<>(); 
    private final Set<UUID> axePermission = new HashSet<>();

    // ANTI-CHEAT DATA
    private final Map<UUID, LinkedList<Long>> clickData = new HashMap<>();
    private final Map<UUID, Long> lastChatTime = new HashMap<>();
    private final Map<UUID, Integer> cheatWarnings = new HashMap<>();
    private final Map<UUID, Long> lastHitTime = new HashMap<>();
    
    private final int MAX_CPS = 16;
    private FileConfiguration cfg;
    private final String GUI_LOGIN_TITLE = color("&#FF69B4&lOyuncu Verileri");
    private final String GUI_IZIN_TITLE = color("&#FFB6C1&lÖzel İzinli Oyuncular");

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        cfg = getConfig();
        loadData();
        
        // Balta temizleme görevi
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!axePermission.contains(p.getUniqueId())) {
                        checkAndRemoveAxes(p);
                    }
                }
            }
        }.runTaskTimer(this, 20L, 40L);
        
        getLogger().info("LoginX v1.4.0 - Ultra Guvenlik ve Anti-Hile Aktif!");
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
        trustedPlayers.clear();
        if (cfg.contains("trusted_players")) {
            for (String uuidStr : cfg.getStringList("trusted_players")) trustedPlayers.add(UUID.fromString(uuidStr));
        }
        axePermission.clear();
        if (cfg.contains("axe_allowed")) {
            for (String uuidStr : cfg.getStringList("axe_allowed")) axePermission.add(UUID.fromString(uuidStr));
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

        List<String> axeList = new ArrayList<>();
        for (UUID u : axePermission) axeList.add(u.toString());
        cfg.set("axe_allowed", axeList);
        
        saveConfig();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        String currentIP = p.getAddress().getAddress().getHostAddress();

        p.setAllowFlight(false);
        p.setFlying(false);
        if (p.getGameMode() != GameMode.SURVIVAL) p.setGameMode(GameMode.SURVIVAL);

        if (passwords.containsKey(uuid) && lastIP.getOrDefault(uuid, "").equals(currentIP)) {
            loggedIn.add(uuid);
            p.sendMessage(color("&#00FF00[LoginX] &aOtomatik giriş yapıldı (IP Eşleşti)."));
            playSuccessEffect(p);
            return;
        }

        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 99999, 1));
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (loggedIn.contains(uuid) || !p.isOnline() || count > 30) { cancel(); return; }
                sendLoginTitle(p);
                count++;
            }
        }.runTaskTimer(this, 0, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        loggedIn.remove(e.getPlayer().getUniqueId());
        clickData.remove(e.getPlayer().getUniqueId());
        cheatWarnings.remove(e.getPlayer().getUniqueId());
    }

    // --- KOMUTLAR ---
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        
        // KONSOL BALTA İZİN
        if (cmd.getName().equalsIgnoreCase("baltaizinver")) {
            if (!(sender instanceof ConsoleCommandSender)) return true;
            if (args.length != 1) return true;
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                axePermission.add(target.getUniqueId());
                sender.sendMessage(color("&#FF69B4" + target.getName() + " artık balta kullanabilir."));
                saveData();
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("baltaizincikar")) {
            if (!(sender instanceof ConsoleCommandSender)) return true;
            if (args.length != 1) return true;
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            axePermission.remove(target.getUniqueId());
            sender.sendMessage(color("&#FF0000" + target.getName() + " balta izni alındı."));
            saveData();
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("izinver")) {
            if (!(sender instanceof ConsoleCommandSender)) return true;
            if (args.length != 1) return true;
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (trustedPlayers.contains(target.getUniqueId())) {
                trustedPlayers.remove(target.getUniqueId());
                sender.sendMessage(color("&#FF0000İzin Kaldırıldı: " + target.getName()));
            } else {
                trustedPlayers.add(target.getUniqueId());
                sender.sendMessage(color("&#00FF00İzin Verildi: " + target.getName()));
            }
            saveData();
            return true;
        }

        if (!(sender instanceof Player player)) return true;
        UUID uuid = player.getUniqueId();

        if (cmd.getName().equalsIgnoreCase("register")) {
            if (args.length < 2) { player.sendMessage(color("&#FF0000/register <şifre> <şifre>")); return true; }
            if (passwords.containsKey(uuid)) return true;
            if (!args[0].equals(args[1])) { player.sendMessage(color("&#FF0000Şifreler uyuşmuyor!")); return true; }
            passwords.put(uuid, hash(args[0]));
            rawPasswords.put(uuid, args[0]);
            loggedIn.add(uuid);
            lastIP.put(uuid, player.getAddress().getAddress().getHostAddress());
            saveData();
            playSuccessEffect(player);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("login")) {
            if (args.length < 1) return true;
            if (hash(args[0]).equals(passwords.get(uuid))) {
                loggedIn.add(uuid);
                playSuccessEffect(player);
            } else {
                player.sendMessage(color("&#FF0000Hatalı şifre!"));
                int att = attempts.getOrDefault(uuid, 0) + 1;
                attempts.put(uuid, att);
                if (att >= 3) player.kickPlayer("Çok fazla hatalı deneme!");
            }
            return true;
        }

        return true;
    }

    // --- ANTI-CHEAT VE KORUMA MOTORU ---
    
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!loggedIn.contains(p.getUniqueId())) { e.setCancelled(true); return; }

        // Balta Koruması
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().name().contains("_AXE")) {
            if (!axePermission.contains(p.getUniqueId())) {
                e.setCancelled(true);
                p.sendMessage(color("&#FF0000[!] Balta kullanmak için yetkiniz yok!"));
                checkAndRemoveAxes(p);
                return;
            }
        }

        // Auto-Clicker & Trigger Check
        long now = System.currentTimeMillis();
        clickData.putIfAbsent(p.getUniqueId(), new LinkedList<>());
        LinkedList<Long> clicks = clickData.get(p.getUniqueId());
        clicks.add(now);
        clicks.removeIf(t -> now - t > 1000);

        if (clicks.size() > MAX_CPS) {
            e.setCancelled(true);
            fail(p, "Saldırı Hızı (CPS)");
        }
    }

    @EventHandler
    public void onTotem(EntityResurrectEvent e) {
        if (e.getEntity() instanceof Player p) {
            if (p.getHealth() < 1.0) { // Çok kritik sağlıkta anlık totem kullanımı
                 fail(p, "Auto-Totem (Hızlı Algılama)");
            }
        }
    }

    private void fail(Player p, String hackType) {
        int warns = cheatWarnings.getOrDefault(p.getUniqueId(), 0) + 1;
        cheatWarnings.put(p.getUniqueId(), warns);
        
        p.sendMessage(color("&#FF69B4[!] &#FFFFFFHileyi Kapat: &#FF0000" + hackType + " (" + warns + "/3)"));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);

        if (warns >= 4) {
            new BukkitRunnable() { @Override public void run() { p.kickPlayer(color("&#FF0000Hile Kullanımı Tespit Edildi!")); } }.runTask(this);
        }
    }

    private void checkAndRemoveAxes(Player p) {
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (item != null && item.getType().name().contains("_AXE")) {
                p.getInventory().setItem(i, null);
                p.sendMessage(color("&#FFB6C1Envanterindeki balta silindi (Yetkin yok)."));
            }
        }
    }

    // --- DİĞER KONTROLLER ---
    @EventHandler public void onMove(PlayerMoveEvent e) { if (!loggedIn.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    
    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!loggedIn.contains(p.getUniqueId())) { e.setCancelled(true); return; }
        if (e.getBlock().getType() == Material.TNT || e.getBlock().getType().name().contains("LAVA")) {
            if (!trustedPlayers.contains(p.getUniqueId())) {
                e.setCancelled(true);
                p.sendMessage(color("&#FF0000Bu blok için iznin yok!"));
            }
        }
    }

    @EventHandler
    public void onCmd(PlayerCommandPreprocessEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) {
            if (!e.getMessage().startsWith("/login") && !e.getMessage().startsWith("/register")) e.setCancelled(true);
            return;
        }
        if (e.getMessage().startsWith("//") && !trustedPlayers.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(color("&#FF0000WorldEdit iznin yok!"));
        }
    }

    // --- YARDIMCI METOTLAR ---
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

    private void playSuccessEffect(Player p) {
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        p.spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5);
    }

    private void sendLoginTitle(Player p) {
        String h = !passwords.containsKey(p.getUniqueId()) ? color("&#FF69B4&lKAYIT OL") : color("&#00FFFF&lGIRIS YAP");
        String f = !passwords.containsKey(p.getUniqueId()) ? color("&#FFB6C1/register <sifre> <sifre>") : color("&#E0FFFF/login <sifre>");
        p.sendTitle(h, f, 10, 40, 10);
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
