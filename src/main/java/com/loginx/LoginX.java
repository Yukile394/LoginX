package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LoginX extends JavaPlugin implements Listener {

    private final HashMap<UUID, Integer> kills = new HashMap<>();
    private final HashMap<UUID, Integer> playtime = new HashMap<>();
    private final HashMap<UUID, Integer> blocksBroken = new HashMap<>();

    private final HashMap<Location, String> activeHolograms = new HashMap<>();
    private final List<ArmorStand> spawnedStands = new ArrayList<>();

    private long nextResetTime;

    // Sword system
    public SwordManager swordManager;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        saveDefaultConfig();
        loadStats();

        // ✔ SWORD SYSTEM BAĞLANTI
        swordManager = new SwordManager(this);
        getServer().getPluginManager().registerEvents(swordManager, this);

        startPlaytime();
        startHoloUpdater();
        startWeeklyReset();

        getLogger().info("LoginX aktif!");
    }

    @Override
    public void onDisable() {
        saveStats();
        clearHolos();
        getLogger().info("LoginX kapandi.");
    }

    // ─────────────────────────────
    // COMMANDS
    // ─────────────────────────────
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Sadece oyuncu!");
            return true;
        }

        if (!p.hasPermission("loginx.admin")) {
            p.sendMessage(color("&cYetkin yok!"));
            return true;
        }

        String cn = cmd.getName().toLowerCase();

        Location tl = p.getTargetBlock(null, 10).getLocation().add(0.5, 3, 0.5);

        if (cn.equals("skorkill")) {
            spawnHolo(tl, "KILLS");
            return true;
        }

        if (cn.equals("skorzaman")) {
            spawnHolo(tl, "PLAYTIME");
            return true;
        }

        if (cn.equals("skorblok")) {
            spawnHolo(tl, "BLOCKS");
            return true;
        }

        if (cn.equals("skorsil")) {
            clearHolos();
            activeHolograms.clear();
            return true;
        }

        // ✔ SWORD MENU COMMAND
        if (cn.equals("swordmenu")) {
            openSwordMenu(p);
            return true;
        }

        return true;
    }

    // ─────────────────────────────
    // GUI
    // ─────────────────────────────
    private void openSwordMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8Kılıç Menüsü");

        inv.setItem(10, new ItemStack(Material.NETHERITE_SWORD));
        inv.setItem(12, new ItemStack(Material.NETHERITE_SWORD));
        inv.setItem(14, new ItemStack(Material.NETHERITE_SWORD));
        inv.setItem(16, new ItemStack(Material.NETHERITE_SWORD));

        p.openInventory(inv);
    }

    // ─────────────────────────────
    // EVENTS
    // ─────────────────────────────
    @EventHandler
    public void onKill(PlayerDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k != null) kills.merge(k.getUniqueId(), 1, Integer::sum);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        blocksBroken.merge(e.getPlayer().getUniqueId(), 1, Integer::sum);
    }

    // ─────────────────────────────
    // HOLOGRAM SYSTEM
    // ─────────────────────────────

    private void startHoloUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeHolograms.isEmpty()) return;
                clearHolos();
                activeHolograms.forEach(LoginX.this::buildHolo);
            }
        }.runTaskTimer(this, 100L, 100L);
    }

    private void spawnHolo(Location loc, String type) {
        activeHolograms.put(loc, type);
        buildHolo(loc, type);
    }

    private void buildHolo(Location base, String type) {

        List<String> lines = new ArrayList<>();

        if (type.equals("KILLS")) {
            lines.add("§cEN ÇOK KILL");
            lines.add("Veri...");
        } else if (type.equals("PLAYTIME")) {
            lines.add("§bEN ÇOK OYNAYAN");
            lines.add("Veri...");
        } else {
            lines.add("§aEN ÇOK BLOK");
            lines.add("Veri...");
        }

        double y = 0;

        for (String line : lines) {
            ArmorStand s = (ArmorStand) base.getWorld().spawnEntity(
                    base.clone().subtract(0, y, 0),
                    EntityType.ARMOR_STAND
            );

            s.setVisible(false);
            s.setCustomNameVisible(true);
            s.setCustomName(color(line));
            s.setGravity(false);
            s.setMarker(true);

            spawnedStands.add(s);
            y += 0.3;
        }
    }

    private void clearHolos() {
        for (ArmorStand s : spawnedStands) {
            if (s != null && !s.isDead()) s.remove();
        }
        spawnedStands.clear();
    }

    // ─────────────────────────────
    // COLOR FIX
    // ─────────────────────────────
    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    // ─────────────────────────────
    // STATS
    // ─────────────────────────────

    private void startPlaytime() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    playtime.merge(p.getUniqueId(), 1, Integer::sum);
                }
            }
        }.runTaskTimer(this, 1200L, 1200L);
    }

    private void startWeeklyReset() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (System.currentTimeMillis() >= nextResetTime) {
                    kills.clear();
                    playtime.clear();
                    blocksBroken.clear();

                    nextResetTime = System.currentTimeMillis() + 604800000L;
                    Bukkit.broadcastMessage("§aHaftalık sıfırlama!");
                }
            }
        }.runTaskTimer(this, 20L * 60, 20L * 60 * 60);
    }

    private void loadStats() {
        FileConfiguration c = getConfig();
        nextResetTime = c.getLong("reset", System.currentTimeMillis() + 604800000L);
    }

    private void saveStats() {
        FileConfiguration c = getConfig();
        c.set("reset", nextResetTime);
        saveConfig();
    }
    }
