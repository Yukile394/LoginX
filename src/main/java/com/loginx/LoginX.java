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

    private final Map<UUID, String> passwords = new HashMap<>(); 
    private final Map<UUID, String> rawPasswords = new HashMap<>(); 
    private final Map<UUID, Integer> attempts = new HashMap<>();
    private final Set<UUID> loggedIn = new HashSet<>();
    private final Map<UUID, String> lastIP = new HashMap<>();
    private final Set<UUID> trustedPlayers = new HashSet<>(); 
    
    private final Map<UUID, LinkedList<Long>> clickData = new HashMap<>();
    private final Map<UUID, Long> lastOffhandClick = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> violations = new HashMap<>();
    
    private final int MAX_CPS = 16; 
    private final double MAX_REACH = 4.3; 

    private FileConfiguration cfg;
    private final String GUI_LOGIN_TITLE = color("&#FF69B4&lOyuncu Verileri");
    private final String GUI_IZIN_TITLE = color("&#FFB6C1&lÖzel İzinli Oyuncular");

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        cfg = getConfig();
        loadData();
        
        new BukkitRunnable() {
            @Override
            public void run() { violations.clear(); }
        }.runTaskTimer(this, 3600L, 3600L);
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
        lastOffhandClick.remove(u);
        violations.remove(u);
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

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("izinver") || cmd.getName().equalsIgnoreCase("izinengelle")) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage(color("&#FF0000[!] Bu komut sadece KONSOL üzerinden kullanılabilir!"));
                return true;
            }
            if (args.length != 1) return false;
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (cmd.getName().equalsIgnoreCase("izinver")) {
                trustedPlayers.add(target.getUniqueId());
                sender.sendMessage(color("&#00FF00[LoginX] &f" + target.getName() + " &aonaylandı."));
            } else {
                trustedPlayers.remove(target.getUniqueId());
                sender.sendMessage(color("&#FF0000[LoginX] &f" + target.getName() + " &cengellendi."));
            }
            saveData();
            return true;
        }

        if (!(sender instanceof Player player)) return true;
        UUID uuid = player.getUniqueId();

        if (cmd.getName().equalsIgnoreCase("register")) {
            if (args.length < 2) return false;
            if (passwords.containsKey(uuid)) return true;
            if (!args[0].equals(args[1])) return true;
            passwords.put(uuid, hash(args[0]));
            rawPasswords.put(uuid, args[0]);
            loggedIn.add(uuid);
            saveData();
            playSuccessEffect(player);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("login")) {
            if (args.length < 1) return false;
            if (hash(args[0]).equals(passwords.get(uuid))) {
                loggedIn.add(uuid);
                playSuccessEffect(player);
            } else {
                attempts.put(uuid, attempts.getOrDefault(uuid, 0) + 1);
                if (attempts.get(uuid) >= 3) player.kickPlayer(color("&#FF0000Hatalı deneme!"));
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("logingoster")) {
            if (player.hasPermission("loginx.admin")) openLoginMenu(player);
            return true;
        }
        return true;
    }

    private void openLoginMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_LOGIN_TITLE);
        for (UUID id : rawPasswords.keySet()) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(id);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName(color("&#FF69B4" + target.getName()));
                meta.setLore(Collections.singletonList(color("&7Şifre: &f" + rawPasswords.get(id))));
                head.setItemMeta(meta);
            }
            gui.addItem(head);
        }
        player.openInventory(gui);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals(GUI_LOGIN_TITLE)) e.setCancelled(true);
    }

    private void addViolation(Player p, String cheatName, int maxVL) {
        UUID uuid = p.getUniqueId();
        violations.putIfAbsent(uuid, new HashMap<>());
        Map<String, Integer> pViolations = violations.get(uuid);
        int currentVL = pViolations.getOrDefault(cheatName, 0) + 1;
        pViolations.put(cheatName, currentVL);
        if (currentVL >= maxVL) {
            pViolations.put(cheatName, 0);
            kickCheater(p, cheatName);
        }
    }

    private void kickCheater(Player p, String reason) {
        new BukkitRunnable() {
            @Override
            public void run() {
                p.kickPlayer(color("&#FF0000[Anti-Cheat]\n\n&fSebep: &e" + reason));
                Bukkit.broadcastMessage(color("&#FF0000[Anti-Cheat] &e" + p.getName() + " &7- &f" + reason));
            }
        }.runTask(this);
    }

    @EventHandler(priority = EventPriority.HIGHEST) 
    public void onInteract(PlayerInteractEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) { e.setCancelled(true); return; }
        if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            UUID uuid = e.getPlayer().getUniqueId();
            long now = System.currentTimeMillis();
            clickData.putIfAbsent(uuid, new LinkedList<>());
            LinkedList<Long> clicks = clickData.get(uuid);
            clicks.add(now);
            clicks.removeIf(time -> now - time > 1000);
            if (clicks.size() > MAX_CPS) addViolation(e.getPlayer(), "AutoClicker", 3);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST) 
    public void onDamageDeal(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!loggedIn.contains(p.getUniqueId())) { e.setCancelled(true); return; }

        double dist = p.getEyeLocation().distance(e.getEntity().getLocation());
        if (dist > MAX_REACH) {
            e.setCancelled(true);
            addViolation(p, "Reach/Hitbox", 3);
        }

        Vector dir = p.getEyeLocation().getDirection().normalize();
        Vector toTarget = e.getEntity().getLocation().clone().subtract(p.getEyeLocation()).toVector().normalize();
        if (dist > 2.0 && dir.dot(toTarget) < 0.35) {
            e.setCancelled(true);
            addViolation(p, "KillAura/AimAssist", 4);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!loggedIn.contains(p.getUniqueId())) { e.setCancelled(true); return; }
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || p.getAllowFlight()) return;

        double distXZ = Math.sqrt(Math.pow(e.getTo().getX() - e.getFrom().getX(), 2) + Math.pow(e.getTo().getZ() - e.getFrom().getZ(), 2));
        if (distXZ > 0.85 && p.getFallDistance() == 0 && !p.isGliding() && !p.hasPotionEffect(PotionEffectType.SPEED)) {
            addViolation(p, "Fly/Speed", 5);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInv(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getSlot() == 40) {
            long now = System.currentTimeMillis();
            long last = lastOffhandClick.getOrDefault(p.getUniqueId(), 0L);
            if (now - last < 15) addViolation(p, "AutoTotem", 2);
            lastOffhandClick.put(p.getUniqueId(), now);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST) 
    public void onPlace(BlockPlaceEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) { e.setCancelled(true); return; }
        Material m = e.getBlock().getType();
        if ((m == Material.TNT || m == Material.LAVA) && !trustedPlayers.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
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

    private String color(String t) {
        Pattern p = Pattern.compile("&#([a-fA-F0-9]{6})");
        Matcher m = p.matcher(t);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            String h = m.group(1);
            StringBuilder r = new StringBuilder("§x");
            for (char c : h.toCharArray()) r.append("§").append(c);
            m.appendReplacement(b, r.toString());
        }
        return ChatColor.translateAlternateColorCodes('&', m.appendTail(b).toString());
    }
}
