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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.regex.*;

public class LoginX extends JavaPlugin implements Listener {

    private SwordManager swordManager; // 🔥 EKLENDİ

    private final HashMap<UUID, Integer> kills = new HashMap<>();
    private final HashMap<UUID, Integer> playtime = new HashMap<>();
    private final HashMap<UUID, Integer> blocksBroken = new HashMap<>();
    private final HashMap<Location, String> activeHolograms = new HashMap<>();
    private final List<ArmorStand> spawnedStands = new ArrayList<>();
    private long nextResetTime;

    @Override
    public void onEnable() {

        Bukkit.getPluginManager().registerEvents(this, this);

        // 🔥 SWORD SYSTEM BAĞLANTISI (EN ÖNEMLİ KISIM)
        swordManager = new SwordManager(this);

        saveDefaultConfig();
        loadStats();

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

    // ================= COMMAND =================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Sadece oyuncu!");
            return true;
        }

        String c = cmd.getName().toLowerCase();

        // ================= SKORLAR =================
        if (c.equals("skorkill")) {
            spawnHolo(p.getLocation(), "KILLS");
            return true;
        }

        if (c.equals("skorzaman")) {
            spawnHolo(p.getLocation(), "PLAYTIME");
            return true;
        }

        if (c.equals("skorblok")) {
            spawnHolo(p.getLocation(), "BLOCKS");
            return true;
        }

        if (c.equals("skorsil")) {
            clearHolos();
            activeHolograms.clear();
            return true;
        }

        // ================= KILIÇ MENU FIX =================
        if (c.equals("kilicvermenu")) {
            openSwordMenu(p);
            return true;
        }

        // ================= TEST / DIRECT GIVE =================
        if (c.equals("shulkerkilicver")) {
            swordManager.giveToPlayer(p, swordManager.makeShulkerSword());
            return true;
        }

        if (c.equals("endermankilicver")) {
            swordManager.giveToPlayer(p, swordManager.makeEndermanSword());
            return true;
        }

        if (c.equals("orumcekkilicver")) {
            swordManager.giveToPlayer(p, swordManager.makeSpiderSword());
            return true;
        }

        if (c.equals("phantomkilicver")) {
            swordManager.giveToPlayer(p, swordManager.makePhantomSword());
            return true;
        }

        if (c.equals("golemkilicver")) {
            swordManager.giveToPlayer(p, swordManager.makeGolemSword());
            return true;
        }

        if (c.equals("creeperkilicver")) {
            swordManager.giveToPlayer(p, swordManager.makeCreeperSword());
            return true;
        }

        return true;
    }

    // ================= SIMPLE GUI =================

    private void openSwordMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8Kılıç Menüsü");

        inv.setItem(10, new ItemStack(Material.NETHERITE_SWORD));
        inv.setItem(12, new ItemStack(Material.NETHERITE_SWORD));
        inv.setItem(14, new ItemStack(Material.NETHERITE_SWORD));
        inv.setItem(16, new ItemStack(Material.NETHERITE_SWORD));

        p.openInventory(inv);
    }

    // ================= EVENTS =================

    @EventHandler
    public void onKill(PlayerDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k != null) kills.merge(k.getUniqueId(), 1, Integer::sum);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        blocksBroken.merge(e.getPlayer().getUniqueId(), 1, Integer::sum);
    }

    // ================= PLAYTIME =================

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

    // ================= HOLOGRAM =================

    private void spawnHolo(Location loc, String type) {
        activeHolograms.put(loc, type);
    }

    private void clearHolos() {
        for (ArmorStand a : spawnedStands) {
            if (a != null && !a.isDead()) a.remove();
        }
        spawnedStands.clear();
    }

    // ================= WEEK RESET =================

    private void startWeeklyReset() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (System.currentTimeMillis() >= nextResetTime) {
                    kills.clear();
                    playtime.clear();
                    blocksBroken.clear();
                    nextResetTime = System.currentTimeMillis() + 604800000L;
                }
            }
        }.runTaskTimer(this, 20L * 60, 20L * 60 * 60);
    }

    // ================= SAVE =================

    private void saveStats() {
        FileConfiguration c = getConfig();
        c.set("next_reset", nextResetTime);
        saveConfig();
    }

    private void loadStats() {
        FileConfiguration c = getConfig();
        nextResetTime = c.getLong("next_reset", System.currentTimeMillis() + 604800000L);
    }
                                   }
