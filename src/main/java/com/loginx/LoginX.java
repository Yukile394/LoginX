package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
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

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

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
    }

    // ===================== COMMAND =====================
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player p)) return true;
        if (!p.hasPermission("loginx.admin")) return true;

        Location tl = p.getTargetBlock(null, 10).getLocation().add(0.5, 2.5, 0.5);

        switch (cmd.getName().toLowerCase()) {

            case "skorkill" -> spawnHolo(tl, "KILLS");
            case "skorzaman" -> spawnHolo(tl, "PLAYTIME");
            case "skorblok" -> spawnHolo(tl, "BLOCKS");

            case "skorsil" -> {
                clearHolos();
                activeHolograms.clear();
                p.sendMessage(color("&cSilindi"));
            }

            case "kilicvermenu" -> openMenu(p);
        }

        return true;
    }

    // ===================== MENU =====================
    private void openMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8Kılıç Menü");

        inv.setItem(10, sword("§5Shulker"));
        inv.setItem(12, sword("§1Enderman"));
        inv.setItem(14, sword("§2Örümcek"));
        inv.setItem(16, sword("§3Phantom"));
        inv.setItem(22, sword("§aCreeper"));

        p.openInventory(inv);
    }

    private ItemStack sword(String name) {
        ItemStack i = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(name + " Kılıcı");
        m.setLore(List.of("§7Sağ tık özel skill"));
        i.setItemMeta(m);
        return i;
    }

    // ===================== EVENTS =====================
    @EventHandler
    public void onKill(PlayerDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k != null) kills.merge(k.getUniqueId(), 1, Integer::sum);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        blocksBroken.merge(e.getPlayer().getUniqueId(), 1, Integer::sum);
    }

    // ===================== HOLOGRAM =====================
    private void spawnHolo(Location loc, String type) {
        activeHolograms.put(loc, type);
        buildHolo(loc, type);
    }

    private void buildHolo(Location base, String type) {

        List<String> lines = new ArrayList<>();

        if (type.equals("KILLS")) {
            lines.add("&cTOP KILLS");
            lines.addAll(top(kills, "kill"));
        } else if (type.equals("PLAYTIME")) {
            lines.add("&bTOP TIME");
            lines.addAll(top(playtime, "dk"));
        } else {
            lines.add("&aTOP BLOCKS");
            lines.addAll(top(blocksBroken, "blok"));
        }

        double y = 0;

        for (String l : lines) {
            ArmorStand a = (ArmorStand) base.getWorld().spawnEntity(
                    base.clone().subtract(0, y, 0),
                    EntityType.ARMOR_STAND
            );

            a.setInvisible(true);
            a.setMarker(true);
            a.setCustomNameVisible(true);
            a.setCustomName(color(l));
            a.setGravity(false);

            spawnedStands.add(a);
            y += 0.25;
        }
    }

    private void clearHolos() {
        for (ArmorStand a : spawnedStands) {
            if (a != null) a.remove();
        }
        spawnedStands.clear();
    }

    // ===================== TOP =====================
    private List<String> top(HashMap<UUID, Integer> map, String suf) {
        if (map.isEmpty()) return List.of("&cNo data");

        List<String> out = new ArrayList<>();

        int r = 1;
        for (Map.Entry<UUID, Integer> e : map.entrySet()
                .stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toList())) {

            String n = Bukkit.getOfflinePlayer(e.getKey()).getName();
            if (n == null) n = "Unknown";

            out.add("&e" + r + ". &f" + n + " &7- &a" + e.getValue() + " " + suf);
            r++;
        }

        return out;
    }

    // ===================== TASKS =====================
    private void startPlaytime() {
        new BukkitRunnable() {
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    playtime.merge(p.getUniqueId(), 1, Integer::sum);
                }
            }
        }.runTaskTimer(this, 1200, 1200);
    }

    private void startHoloUpdater() {
        new BukkitRunnable() {
            public void run() {
                if (!activeHolograms.isEmpty()) {
                    clearHolos();
                    activeHolograms.forEach(LoginX.this::buildHolo);
                }
            }
        }.runTaskTimer(this, 100, 100);
    }

    private void startWeeklyReset() {
        new BukkitRunnable() {
            public void run() {
                if (System.currentTimeMillis() > nextResetTime) {
                    kills.clear();
                    playtime.clear();
                    blocksBroken.clear();

                    nextResetTime = System.currentTimeMillis() + 604800000;

                    Bukkit.broadcastMessage(color("&aHaftalık reset!"));
                }
            }
        }.runTaskTimer(this, 20 * 60, 20 * 60 * 60);
    }

    // ===================== SAVE =====================
    private void saveStats() {
        FileConfiguration c = getConfig();

        c.set("reset", nextResetTime);

        kills.forEach((k, v) -> c.set("kills." + k, v));
        playtime.forEach((k, v) -> c.set("time." + k, v));
        blocksBroken.forEach((k, v) -> c.set("blocks." + k, v));

        saveConfig();
    }

    private void loadStats() {
        FileConfiguration c = getConfig();

        nextResetTime = c.getLong("reset", System.currentTimeMillis() + 604800000);
    }

    // ===================== COLOR FIX =====================
    public String color(String text) {

        Pattern p = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher m = p.matcher(text);

        StringBuffer sb = new StringBuffer();

        while (m.find()) {
            String hex = m.group(1);
            StringBuilder rep = new StringBuilder("§x");

            for (char c : hex.toCharArray()) {
                rep.append("§").append(c);
            }

            m.appendReplacement(sb, rep.toString());
        }

        m.appendTail(sb);

        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
    }
