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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;
import org.bukkit.util.Vector;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoginX extends JavaPlugin implements Listener {

    private final Map<UUID, String> passwords = new HashMap<>(); 
    private final Map<UUID, String> rawPasswords = new HashMap<>(); 
    private final Map<UUID, Integer> attempts = new HashMap<>();
    public final Set<UUID> loggedIn = new HashSet<>(); 
    private final Map<UUID, String> lastIP = new HashMap<>();
    private final Set<UUID> trustedPlayers = new HashSet<>(); 
    private final Map<UUID, Long> lastQuitTime = new HashMap<>();
    private final Map<UUID, DeathRecord> deathDatabase = new HashMap<>();
    private final Map<UUID, LinkedList<Long>> clickData = new HashMap<>();
    private final Map<UUID, Long> lastInventoryClick = new HashMap<>();
    
    private final int MAX_CPS = 16; 
    private final double MAX_REACH = 4.5;
    private FileConfiguration cfg;
    
    private final String GUI_LOGIN_TITLE = color("&#FF69B4&lOyuncu Verileri");
    private final String GUI_IZIN_TITLE = color("&#FFB6C1&lÖzel İzinli Oyuncular");
    private final String GUI_IADE_TITLE = color("&#FF1493&lÖlüm Arşivi (İade)");

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        cfg = getConfig();
        loadData();
        startVisualTasks();
        getLogger().info("LoginX ULTRA SİSTEM Aktif! Hata giderildi.");
    }

    @Override
    public void onDisable() { saveData(); }

    private static class DeathRecord {
        ItemStack[] items;
        String date, loc, killer, reason;
        DeathRecord(ItemStack[] items, Location l, String k, String r) {
            this.items = items.clone();
            this.date = new SimpleDateFormat("dd/MM HH:mm").format(new Date());
            this.loc = l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
            this.killer = k;
            this.reason = r;
        }
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

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        String currentIP = p.getAddress().getAddress().getHostAddress();

        if (cfg.contains("punishments.bans." + uuid) || cfg.contains("punishments.ipbans." + currentIP.replace(".", "_"))) {
            p.kickPlayer(color("&#FF0000Sunucudan Yasaklısınız!"));
            return;
        }

        long lastQuit = lastQuitTime.getOrDefault(uuid, 0L);
        boolean withinTimeLimit = (System.currentTimeMillis() - lastQuit) <= (8 * 60 * 1000);

        if (passwords.containsKey(uuid) && lastIP.containsKey(uuid) && lastIP.get(uuid).equals(currentIP) && withinTimeLimit) {
            loggedIn.add(uuid);
            p.sendMessage(color("&#00FF00[LoginX] &aOto-giriş yapıldı!"));
            playSuccessEffect(p);
        } else {
            lastIP.put(uuid, currentIP);
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, false, false));
            sendLoginTitle(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        loggedIn.remove(u);
        lastQuitTime.put(u, System.currentTimeMillis());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("ban") || cmd.getName().equalsIgnoreCase("mute") || cmd.getName().equalsIgnoreCase("kick") || cmd.getName().equalsIgnoreCase("ipban")) {
            if (!sender.hasPermission("loginx.admin")) return true;
            if (args.length < 1) return false;
            String targetName = args[0];
            String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Kural İhlali";
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

            if (cmd.getName().equalsIgnoreCase("ban")) {
                cfg.set("punishments.bans." + target.getUniqueId(), reason); saveConfig();
                broadcastPunishment("Ban", targetName, sender.getName(), reason, "Süresiz");
                if (target.isOnline()) ((Player)target).kickPlayer(color("&#FF0000Yasaklandınız!"));
            } else if (cmd.getName().equalsIgnoreCase("mute")) {
                cfg.set("punishments.mutes." + target.getUniqueId(), reason); saveConfig();
                broadcastPunishment("Mute", targetName, sender.getName(), reason, "Süresiz");
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("iade")) {
            if (sender instanceof Player p && p.hasPermission("loginx.admin")) openIadeMenu(p);
            return true;
        }

        if (!(sender instanceof Player p)) return true;
        UUID uuid = p.getUniqueId();

        if (cmd.getName().equalsIgnoreCase("register")) {
            if (args.length < 2) return false;
            if (passwords.containsKey(uuid)) return true;
            if (!args[0].equals(args[1])) { p.sendMessage(color("&#FF0000Hata!")); return true; }
            passwords.put(uuid, hash(args[0])); rawPasswords.put(uuid, args[0]); loggedIn.add(uuid); saveData();
            p.sendMessage(color("&#00FF00Başarı!")); playSuccessEffect(p);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("login")) {
            if (args.length < 1) return false;
            if (hash(args[0]).equals(passwords.get(uuid))) {
                loggedIn.add(uuid); p.sendMessage(color("&#00FF00Giriş!")); playSuccessEffect(p);
            } else {
                p.sendMessage(color("&#FF0000Yanlış!"));
            }
            return true;
        }
        
        if (cmd.getName().equalsIgnoreCase("logingoster") && p.hasPermission("loginx.admin")) openLoginMenu(p);
        return true;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        String k = (p.getKiller() != null) ? p.getKiller().getName() : "Doğa";
        deathDatabase.put(p.getUniqueId(), new DeathRecord(p.getInventory().getContents(), p.getLocation(), k, e.getDeathMessage()));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        
        if (title.equals(GUI_IADE_TITLE)) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                SkullMeta sm = (SkullMeta) e.getCurrentItem().getItemMeta();
                if (sm != null && sm.getOwningPlayer() != null) {
                    Player t = Bukkit.getPlayer(sm.getOwningPlayer().getUniqueId());
                    if (t != null && t.isOnline() && deathDatabase.containsKey(t.getUniqueId())) {
                        for (ItemStack i : deathDatabase.get(t.getUniqueId()).items) if (i != null) t.getInventory().addItem(i);
                        t.playSound(t.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
                        deathDatabase.remove(t.getUniqueId()); p.closeInventory();
                    }
                }
            }
            return;
        }

        if (title.equals(GUI_LOGIN_TITLE) || title.equals(GUI_IZIN_TITLE)) { e.setCancelled(true); return; }

        long now = System.currentTimeMillis();
        if (now - lastInventoryClick.getOrDefault(p.getUniqueId(), 0L) < 20) {
            e.setCancelled(true); kickCheater(p, "Macro");
        }
        lastInventoryClick.put(p.getUniqueId(), now);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
        if (cfg.contains("punishments.mutes." + e.getPlayer().getUniqueId())) {
            e.setCancelled(true); e.getPlayer().sendMessage(color("&#FF0000Mutelisiniz!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST) 
    public void onInteract(PlayerInteractEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) { e.setCancelled(true); return; }
        if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            UUID u = e.getPlayer().getUniqueId();
            long now = System.currentTimeMillis();
            clickData.putIfAbsent(u, new LinkedList<>());
            LinkedList<Long> c = clickData.get(u);
            c.add(now); c.removeIf(t -> now - t > 1000);
            if (c.size() > MAX_CPS) kickCheater(e.getPlayer(), "CPS");
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            if (!loggedIn.contains(p.getUniqueId())) e.setCancelled(true);
            if (p.getLocation().distance(e.getEntity().getLocation()) > MAX_REACH) kickCheater(p, "Reach");
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    // --- YARDIMCI VE GÖRSEL METOTLAR ---
    private void broadcastPunishment(String type, String target, String staff, String reason, String time) {
        String line = color("&#FF1493&m----------------------------------------");
        Bukkit.broadcastMessage("\n" + line + "\n" + centerText(color("&#FF69B4&l(" + type + ")")) + 
            "\n  &#FFB6C1Oyuncu: &f" + target + "\n  &#FFB6C1Yetkili: &f" + staff + 
            "\n  &#FFB6C1Sebep: &7" + reason + "\n" + line);
    }

    private void startVisualTasks() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateScoreboard(p);
                    p.setPlayerListHeaderFooter(color("\n&#FF1493&lSVX NW\n"), color("\n&#FF69B4discord.gg/svxnw\n"));
                }
            }
        }.runTaskTimer(this, 0, 20L);
    }

    private void updateScoreboard(Player p) {
        Scoreboard b = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = b.registerNewObjective("svx", "dummy", color("&#FF1493&lSVX NW"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        o.getScore(color("&#FFB6C1İsim: &f" + p.getName())).setScore(2);
        o.getScore(color("&#FFB6C1Ping: &a" + p.getPing())).setScore(1);
        p.setScoreboard(b);
    }

    private void kickCheater(Player p, String r) {
        new BukkitRunnable() { @Override public void run() { p.kickPlayer(color("&#FF0000[AC] " + r)); }}.runTask(this);
    }

    private String hash(String i) {
        try {
            MessageDigest m = MessageDigest.getInstance("SHA-256");
            byte[] b = m.digest(i.getBytes());
            StringBuilder s = new StringBuilder();
            for (byte x : b) s.append(String.format("%02x", x));
            return s.toString();
        } catch (Exception e) { return i; }
    }

    public String color(String t) {
        Pattern p = Pattern.compile("&#([a-fA-F0-9]{6})");
        Matcher m = p.matcher(t);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            String h = m.group(1); StringBuilder r = new StringBuilder("§x");
            for (char c : h.toCharArray()) r.append("§").append(c);
            m.appendReplacement(b, r.toString());
        }
        return ChatColor.translateAlternateColorCodes('&', m.appendTail(b).toString());
    }

    private String centerText(String t) {
        int s = (45 - ChatColor.stripColor(t).length()) / 2;
        return " ".repeat(Math.max(0, s)) + t;
    }

    private void sendLoginTitle(Player p) {
        String h = !passwords.containsKey(p.getUniqueId()) ? cfg.getString("title_colors.register.header") : cfg.getString("title_colors.login.header");
        String f = !passwords.containsKey(p.getUniqueId()) ? cfg.getString("title_colors.register.footer") : cfg.getString("title_colors.login.footer");
        p.sendTitle(color(h), color(f), 10, 40, 10);
    }

    private void playSuccessEffect(Player p) {
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    private void openIadeMenu(Player p) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_IADE_TITLE);
        for (UUID id : deathDatabase.keySet()) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
                meta.setDisplayName(color("&#FF69B4" + Bukkit.getOfflinePlayer(id).getName()));
                head.setItemMeta(meta); gui.addItem(head);
            }
        }
        p.openInventory(gui);
    }

    private void openLoginMenu(Player p) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_LOGIN_TITLE);
        for (UUID id : rawPasswords.keySet()) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
                meta.setDisplayName(color("&#FF69B4" + Bukkit.getOfflinePlayer(id).getName()));
                head.setItemMeta(meta); gui.addItem(head);
            }
        }
        p.openInventory(gui);
    }
                }
                    
