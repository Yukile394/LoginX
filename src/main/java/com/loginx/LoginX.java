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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LoginX extends JavaPlugin implements Listener {

    // ── Istatistik verileri (eski sistem) ────────────────────
    private final HashMap<UUID, Integer> kills       = new HashMap<>();
    private final HashMap<UUID, Integer> playtime    = new HashMap<>();
    private final HashMap<UUID, Integer> blocksBroken = new HashMap<>();

    // ── Hologram takibi (eski sistem) ────────────────────────
    private final HashMap<Location, String> activeHolograms = new HashMap<>();
    private final List<ArmorStand>          spawnedStands   = new ArrayList<>();

    private long nextResetTime;

    // ── Trap sistemi ─────────────────────────────────────────
    private TrapManager         trapManager;
    private TrapGUI             trapGUI;
    private EconomyBridge       economyBridge;
    private TrapCommand         trapCommandHandler;
    private TrapHologramManager trapHologramManager;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        loadStats();

        // Trap sistemi baslat
        initTrapSystem();

        // LoginX2 modulu
        try {
            LoginX2 modul = new LoginX2(this);
            Bukkit.getPluginManager().registerEvents(modul, this);
            getLogger().info("LoginX2 modulu basariyla aktif edildi!");
        } catch (Throwable t) {
            getLogger().warning("LoginX2 yuklenirken sorun olustu veya sinif bulunamadi!");
        }

        startPlaytimeTracker();
        startHologramUpdater();
        startWeeklyResetChecker();

        getLogger().info("LoginX Hologram & Istatistik & Trap Sistemleri Aktif!");
    }

    @Override
    public void onDisable() {
        saveStats();
        clearAllHolograms();
        if (trapManager != null)         trapManager.saveAll();
        if (trapHologramManager != null) trapHologramManager.clearAll();
        getLogger().info("LoginX devre disi.");
    }

    // ── Trap sistemi baslangic ────────────────────────────────
    private void initTrapSystem() {
        economyBridge       = new EconomyBridge(this);
        trapManager         = new TrapManager(this);
        trapHologramManager = new TrapHologramManager(this);
        trapHologramManager.setTrapManager(trapManager);
        trapGUI             = new TrapGUI(trapManager, economyBridge);
        trapCommandHandler  = new TrapCommand(trapManager, trapGUI, economyBridge, trapHologramManager);

        PluginCommand trapCmd = getCommand("trap");
        if (trapCmd != null) {
            trapCmd.setExecutor(trapCommandHandler);
        } else {
            getLogger().warning("[Trap] /trap komutu plugin.yml'de tanimli degil!");
        }

        TrapListener trapListener = new TrapListener(
                trapManager, trapGUI, economyBridge, trapCommandHandler, trapHologramManager);
        Bukkit.getPluginManager().registerEvents(trapListener, this);

        getLogger().info("[Trap] Trap sistemi basariyla baslatildi!");
    }

    // ── Komutlar (eski sistem korundu) ────────────────────────
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Sadece oyun ici kullanilabilir!");
            return true;
        }
        if (!p.hasPermission("loginx.admin")) {
            p.sendMessage(color("&#FF0000[!] &cBu komut icin yetkiniz yok!"));
            return true;
        }

        Location targetLoc = p.getTargetBlock(null, 10).getLocation().add(0.5, 3.0, 0.5);

        if (cmd.getName().equalsIgnoreCase("skorkill")) {
            spawnHologram(targetLoc, "KILLS");
            p.sendMessage(color("&#00FF00[!] &aOldurme siralaması baktığın yere olusturuldu!"));
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("skorzaman")) {
            spawnHologram(targetLoc, "PLAYTIME");
            p.sendMessage(color("&#00FF00[!] &aOynama suresi siralaması baktığın yere olusturuldu!"));
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("skorblok")) {
            spawnHologram(targetLoc, "BLOCKS");
            p.sendMessage(color("&#00FF00[!] &aBlok kirma siralaması baktığın yere olusturuldu!"));
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("skorsil")) {
            clearAllHolograms();
            activeHolograms.clear();
            p.sendMessage(color("&#FF0000[!] &cTum aktif skor hologramlari silindi!"));
            return true;
        }
        return true;
    }

    // ── Eventler (eski sistem korundu) ────────────────────────
    @EventHandler
    public void onKill(PlayerDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer != null) {
            UUID id = killer.getUniqueId();
            kills.put(id, kills.getOrDefault(id, 0) + 1);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        blocksBroken.put(id, blocksBroken.getOrDefault(id, 0) + 1);
    }

    // ── Gorevler (eski sistem korundu) ────────────────────────
    private void startPlaytimeTracker() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    UUID id = p.getUniqueId();
                    playtime.put(id, playtime.getOrDefault(id, 0) + 1);
                }
            }
        }.runTaskTimer(this, 1200L, 1200L);
    }

    private void startHologramUpdater() {
        new BukkitRunnable() {
            @Override public void run() {
                if (activeHolograms.isEmpty()) return;
                clearAllHolograms();
                for (Map.Entry<Location, String> entry : activeHolograms.entrySet())
                    buildHologramLines(entry.getKey(), entry.getValue());
            }
        }.runTaskTimer(this, 100L, 100L);
    }

    private void startWeeklyResetChecker() {
        new BukkitRunnable() {
            @Override public void run() {
                if (System.currentTimeMillis() >= nextResetTime) {
                    kills.clear(); playtime.clear(); blocksBroken.clear();
                    setNextResetTime();
                    Bukkit.broadcastMessage(color(
                            "&#00FFFF[!] &lHAFTALIK SIFIRLAMA! &fTum skor tablolari sifirlandı."));
                }
            }
        }.runTaskTimer(this, 20L * 60, 20L * 60 * 60);
    }

    // ── Hologram (eski sistem korundu) ────────────────────────
    private void spawnHologram(Location loc, String type) {
        activeHolograms.put(loc, type);
        buildHologramLines(loc, type);
    }

    private void buildHologramLines(Location baseLoc, String type) {
        List<String> lines = new ArrayList<>();
        if (type.equals("KILLS")) {
            lines.add("&#FF0000&l⚔ EN COK OLDURENLER ⚔");
            lines.addAll(getTop10(kills, "Oldurme"));
        } else if (type.equals("PLAYTIME")) {
            lines.add("&#00FFFF&l⏳ EN COK OYNAYANLAR ⏳");
            lines.addAll(getTop10Playtime(playtime));
        } else if (type.equals("BLOCKS")) {
            lines.add("&#00FF00&l⛏ EN COK BLOK KIRANLAR ⛏");
            lines.addAll(getTop10(blocksBroken, "Blok"));
        }
        lines.add("&7(Her hafta sifirlanir)");

        double yOffset = 0;
        for (String line : lines) {
            Location spawnLoc = baseLoc.clone().subtract(0, yOffset, 0);
            ArmorStand stand = (ArmorStand) baseLoc.getWorld()
                    .spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setCustomNameVisible(true);
            stand.setCustomName(color(line));
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setBasePlate(false);
            spawnedStands.add(stand);
            yOffset += 0.3;
        }
    }

    private void clearAllHolograms() {
        for (ArmorStand stand : spawnedStands)
            if (stand != null && !stand.isDead()) stand.remove();
        spawnedStands.clear();
    }

    // ── Siralama (eski sistem korundu) ────────────────────────
    private List<String> getTop10(HashMap<UUID, Integer> map, String suffix) {
        List<String> lines = new ArrayList<>();
        if (map.isEmpty()) { lines.add("&cHenuz veri yok..."); return lines; }
        List<Map.Entry<UUID, Integer>> sorted = map.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(10).collect(Collectors.toList());
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null) name = "Bilinmiyor";
            String rankColor = rank == 1 ? "&#FFD700" : rank == 2 ? "&#C0C0C0" : rank == 3 ? "&#CD7F32" : "&e";
            lines.add(color(rankColor + rank + ". &f" + name + " &7- &a" + entry.getValue() + " " + suffix));
            rank++;
        }
        return lines;
    }

    private List<String> getTop10Playtime(HashMap<UUID, Integer> map) {
        List<String> lines = new ArrayList<>();
        if (map.isEmpty()) { lines.add("&cHenuz veri yok..."); return lines; }
        List<Map.Entry<UUID, Integer>> sorted = map.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(10).collect(Collectors.toList());
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null) name = "Bilinmiyor";
            int h = entry.getValue() / 60, m = entry.getValue() % 60;
            String time = h > 0 ? h + "s " + m + "d" : m + "d";
            String rankColor = rank == 1 ? "&#FFD700" : rank == 2 ? "&#C0C0C0" : rank == 3 ? "&#CD7F32" : "&e";
            lines.add(color(rankColor + rank + ". &f" + name + " &7- &a" + time));
            rank++;
        }
        return lines;
    }

    // ── Veri kaydetme / yukleme (eski sistem korundu) ─────────
    private void saveStats() {
        FileConfiguration config = getConfig();
        config.set("next_reset", nextResetTime);
        kills.forEach((k, v)        -> config.set("stats.kills." + k, v));
        playtime.forEach((k, v)     -> config.set("stats.playtime." + k, v));
        blocksBroken.forEach((k, v) -> config.set("stats.blocks." + k, v));
        saveConfig();
    }

    private void loadStats() {
        FileConfiguration config = getConfig();
        nextResetTime = config.contains("next_reset")
                ? config.getLong("next_reset") : calcNextReset();

        if (config.contains("stats.kills"))
            config.getConfigurationSection("stats.kills").getKeys(false)
                    .forEach(s -> kills.put(UUID.fromString(s), config.getInt("stats.kills." + s)));
        if (config.contains("stats.playtime"))
            config.getConfigurationSection("stats.playtime").getKeys(false)
                    .forEach(s -> playtime.put(UUID.fromString(s), config.getInt("stats.playtime." + s)));
        if (config.contains("stats.blocks"))
            config.getConfigurationSection("stats.blocks").getKeys(false)
                    .forEach(s -> blocksBroken.put(UUID.fromString(s), config.getInt("stats.blocks." + s)));
    }

    private void setNextResetTime() {
        this.nextResetTime = calcNextReset();
    }

    private long calcNextReset() {
        return System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000);
    }

    // ── Hex renk yardimcisi ───────────────────────────────────
    public String color(String text) {
        Pattern pattern = Pattern.compile("&#([a-fA-F0-9]{6})");
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("\u00a7x");
            for (char c : hex.toCharArray()) replacement.append('\u00a7').append(c);
            matcher.appendReplacement(buffer, replacement.toString());
        }
        return ChatColor.translateAlternateColorCodes('&',
                matcher.appendTail(buffer).toString());
    }
}
