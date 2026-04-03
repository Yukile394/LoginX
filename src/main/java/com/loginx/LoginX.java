package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
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

    // --- TRAP VERİLERİ ---
    private final HashMap<String, Trap> traps = new HashMap<>();
    private final HashMap<UUID, Location> pos1 = new HashMap<>();
    private final HashMap<UUID, Location> pos2 = new HashMap<>();
    private Location trapWarp = null;
    private final HashMap<UUID, Double> playerMoney = new HashMap<>(); 

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        loadStats();
        loadTraps();
        
        startPlaytimeTracker();
        startWeeklyResetChecker();
        startTrapHologramUpdater();

        getLogger().info("LoginX Sistemleri Aktif Edildi!");
    }

    @Override
    public void onDisable() {
        saveStats();
        saveTraps();
        clearAllHolograms();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        Location targetLoc = p.getTargetBlock(null, 10).getLocation().add(0.5, 3.0, 0.5);

        // --- SKOR TABLOLARI ---
        if (p.hasPermission("loginx.admin")) {
            if (cmd.getName().equalsIgnoreCase("skorkill")) {
                spawnHologram(targetLoc, "KILLS");
                p.sendMessage(color("&#00FF00[!] &aÖldürme sıralaması oluşturuldu!"));
                return true;
            }
            if (cmd.getName().equalsIgnoreCase("skorzaman")) {
                spawnHologram(targetLoc, "PLAYTIME");
                p.sendMessage(color("&#00FF00[!] &aZaman sıralaması oluşturuldu!"));
                return true;
            }
            if (cmd.getName().equalsIgnoreCase("skorsil")) {
                clearAllHolograms();
                activeHolograms.clear();
                p.sendMessage(color("&#FF0000[!] &cTüm hologramlar silindi!"));
                return true;
            }
        }

        // --- TRAP SİSTEMİ ---
        if (cmd.getName().equalsIgnoreCase("trap")) {
            if (args.length == 0) {
                if (trapWarp != null) { p.teleport(trapWarp); p.sendMessage(color("&#FF66B2[Trap] &fIşınlandınız!")); }
                else p.sendMessage(color("&cWarp ayarlı değil!"));
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "satinal":
                    if (args.length < 2) return true;
                    Trap tBuy = traps.get(args[1]);
                    if (tBuy == null || tBuy.owner != null) return true;
                    double bal = playerMoney.getOrDefault(p.getUniqueId(), 0.0);
                    if (bal >= tBuy.price) {
                        playerMoney.put(p.getUniqueId(), bal - tBuy.price);
                        tBuy.owner = p.getUniqueId();
                        p.sendMessage(color("&#FF66B2[Trap] &fSatın alındı!"));
                    }
                    break;
                case "banka":
                    Trap tBank = getPlayerTrap(p.getUniqueId());
                    if (tBank != null) openTrapMenu(p, tBank);
                    break;
                case "arac":
                    if (p.hasPermission("loginx.admin")) {
                        ItemStack wand = new ItemStack(Material.LEATHER);
                        ItemMeta m = wand.getItemMeta(); m.setDisplayName(color("&#FF66B2Seçici"));
                        wand.setItemMeta(m); p.getInventory().addItem(wand);
                    }
                    break;
                case "yap":
                    if (p.hasPermission("loginx.admin") && args.length == 3) {
                        if (pos1.get(p.getUniqueId()) == null) return true;
                        traps.put(args[1], new Trap(args[1], Double.parseDouble(args[2]), pos1.get(p.getUniqueId()), pos2.get(p.getUniqueId())));
                        p.sendMessage(color("&aTrap oluşturuldu."));
                    }
                    break;
                case "hologram":
                    if (p.hasPermission("loginx.admin") && args.length == 2) {
                        if (traps.containsKey(args[1])) traps.get(args[1]).holoLoc = targetLoc;
                    }
                    break;
                case "setwarp":
                    if (p.hasPermission("loginx.admin")) trapWarp = p.getLocation();
                    break;
            }
        }
        return true;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getItem() == null || e.getItem().getType() != Material.LEATHER) return;
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            pos1.put(e.getPlayer().getUniqueId(), e.getClickedBlock().getLocation());
            e.getPlayer().sendMessage(color("&dPos 1 seçildi."));
            e.setCancelled(true);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            pos2.put(e.getPlayer().getUniqueId(), e.getClickedBlock().getLocation());
            e.getPlayer().sendMessage(color("&dPos 2 seçildi."));
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        for (Trap t : traps.values()) {
            if (t.isInRegion(e.getBlock().getLocation())) {
                if (e.getPlayer().hasPermission("loginx.admin")) return;
                if (t.owner == null || (!t.owner.equals(e.getPlayer().getUniqueId()) && !t.members.contains(e.getPlayer().getUniqueId()))) {
                    e.setCancelled(true);
                }
            }
        }
    }

    private void startTrapHologramUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                clearAllHolograms();
                for (Map.Entry<Location, String> entry : activeHolograms.entrySet()) buildHologramLines(entry.getKey(), entry.getValue());
                for (Trap t : traps.values()) if (t.holoLoc != null) buildTrapHologram(t);
            }
        }.runTaskTimer(this, 100L, 100L);
    }

    private void buildTrapHologram(Trap t) {
        List<String> lines = new ArrayList<>();
        lines.add("&#FF66B2&lTRAP " + t.id.toUpperCase());
        String oName = (t.owner == null) ? "&aSatılık!" : Bukkit.getOfflinePlayer(t.owner).getName();
        lines.add("&#FFB2D9Sahibi: &#FFFFFF" + oName);
        lines.add("&#FFB2D9Fiyat: &#FFFFFF" + t.price + " ₺");
        double y = 0;
        for (String l : lines) {
            ArmorStand s = (ArmorStand) t.holoLoc.getWorld().spawnEntity(t.holoLoc.clone().subtract(0, y, 0), EntityType.ARMOR_STAND);
            s.setVisible(false); s.setCustomNameVisible(true); s.setCustomName(color(l)); s.setGravity(false); s.setMarker(true);
            spawnedStands.add(s); y += 0.3;
        }
    }

    private void openTrapMenu(Player p, Trap t) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&#FF66B2&lTrap Bankası"));
        ItemStack item = new ItemStack(Material.PAPER); ItemMeta m = item.getItemMeta();
        m.setDisplayName(color("&eBanka: " + t.bank + " ₺")); item.setItemMeta(m);
        inv.setItem(13, item); p.openInventory(inv);
    }

    private Trap getPlayerTrap(UUID u) {
        for (Trap t : traps.values()) if (t.owner != null && t.owner.equals(u)) return t;
        return null;
    }

    private void startPlaytimeTracker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) playtime.put(p.getUniqueId(), playtime.getOrDefault(p.getUniqueId(), 0) + 1);
            }
        }.runTaskTimer(this, 1200L, 1200L);
    }

    private void startWeeklyResetChecker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (System.currentTimeMillis() >= nextResetTime) {
                    kills.clear(); playtime.clear(); setNextResetTime();
                }
            }
        }.runTaskTimer(this, 1200L, 72000L);
    }

    private void spawnHologram(Location loc, String type) { activeHolograms.put(loc, type); }

    private void buildHologramLines(Location loc, String type) {
        List<String> lines = new ArrayList<>();
        if (type.equals("KILLS")) lines.add("&#FF0000&l⚔ ÖLDÜRMELER");
        else lines.add("&#00FFFF&l⏳ ZAMAN");
        double y = 0;
        for (String l : lines) {
            ArmorStand s = (ArmorStand) loc.getWorld().spawnEntity(loc.clone().subtract(0, y, 0), EntityType.ARMOR_STAND);
            s.setVisible(false); s.setCustomNameVisible(true); s.setCustomName(color(l)); s.setGravity(false); s.setMarker(true);
            spawnedStands.add(s); y += 0.3;
        }
    }

    private void clearAllHolograms() {
        spawnedStands.forEach(s -> { if (s != null) s.remove(); });
        spawnedStands.clear();
    }

    private void saveStats() {
        getConfig().set("next_reset", nextResetTime);
        saveConfig();
    }

    private void loadStats() {
        nextResetTime = getConfig().getLong("next_reset", System.currentTimeMillis() + 604800000L);
    }

    private void saveTraps() {
        for (Trap t : traps.values()) {
            getConfig().set("traps." + t.id + ".price", t.price);
            getConfig().set("traps." + t.id + ".owner", t.owner != null ? t.owner.toString() : null);
        }
        saveConfig();
    }

    private void loadTraps() {
        if (!getConfig().contains("traps")) return;
        for (String key : getConfig().getConfigurationSection("traps").getKeys(false)) {
            // Basitleştirilmiş yükleme mantığı
        }
    }

    private void setNextResetTime() { nextResetTime = System.currentTimeMillis() + 604800000L; }

    public String color(String t) {
        Pattern p = Pattern.compile("&#([a-fA-F0-9]{6})");
        Matcher m = p.matcher(t);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            StringBuilder r = new StringBuilder("§x");
            for (char c : m.group(1).toCharArray()) r.append("§").append(c);
            m.appendReplacement(b, r.toString());
        }
        return ChatColor.translateAlternateColorCodes('&', m.appendTail(b).toString());
    }

    private static class Trap {
        String id; UUID owner; List<UUID> members = new ArrayList<>();
        double bank, price; Location min, max, holoLoc;
        public Trap(String id, double price, Location p1, Location p2) {
            this.id = id; this.price = price;
            this.min = new Location(p1.getWorld(), Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ()));
            this.max = new Location(p1.getWorld(), Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()), Math.max(p1.getZ(), p2.getZ()));
        }
        public boolean isInRegion(Location l) {
            return l.getWorld().equals(min.getWorld()) && l.getX() >= min.getX() && l.getX() <= max.getX() && l.getZ() >= min.getZ() && l.getZ() <= max.getZ();
        }
    }
}
