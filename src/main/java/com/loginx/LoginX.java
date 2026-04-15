package com.loginx;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.text.SimpleDateFormat;
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

    private final HashMap<UUID, HashMap<String, Long>> cooldowns = new HashMap<>();
    private final HashMap<UUID, Long> elytraBanned = new HashMap<>();
    private final List<DeathRecord> deathRecords = new ArrayList<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        loadStats();
        startPlaytime();
        startHoloUpdater();
        startWeeklyReset();
        getLogger().info("LoginX Aktif!");
    }

    @Override
    public void onDisable() {
        saveStats();
        clearHolos();
    }

    // İade Kayıt Sınıfı
    private static class DeathRecord {
        String id;
        UUID playerUUID;
        String playerName;
        String killerName;
        Location loc;
        long time;
        ItemStack[] items;

        public DeathRecord(UUID pu, String pn, String kn, Location l, ItemStack[] itms) {
            this.id = Integer.toHexString(new Random().nextInt(0xFFFF)).toUpperCase();
            this.playerUUID = pu;
            this.playerName = pn;
            this.killerName = kn;
            this.loc = l;
            this.time = System.currentTimeMillis();
            this.items = itms;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (!p.hasPermission("loginx.admin")) return true;

        switch (cmd.getName().toLowerCase()) {
            case "kilicvermenu" -> openSwordMenu(p);
            case "gardiyankilicver" -> giveSword(p, "gardiyan");
            case "witherkilicver" -> giveSword(p, "wither");
            case "iade" -> openIadeMenu(p);
            case "skorsil" -> { clearHolos(); activeHolograms.clear(); }
        }
        return true;
    }

    private void giveSword(Player p, String type) {
        p.getInventory().addItem(getSpecialSword(type));
        p.sendMessage(color("&a" + type.toUpperCase() + " kılıcı verildi."));
    }

    private ItemStack getSpecialSword(String type) {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Yetenek: Sağ Tık"));
        if (type.equals("gardiyan")) {
            meta.setDisplayName(color("&#00e6b8Gardiyan Kılıcı"));
            lore.add(color("&fEfekt: Madenci Yorgunluğu (3s)"));
        } else if (type.equals("wither")) {
            meta.setDisplayName(color("&#404040Wither Kılıcı"));
            lore.add(color("&fEfekt: Wither (4s)"));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        ItemStack[] original = p.getInventory().getContents();
        ItemStack[] cloned = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) if (original[i] != null) cloned[i] = original[i].clone();
        
        String kname = (p.getKiller() != null) ? p.getKiller().getName() : "Doga";
        deathRecords.add(0, new DeathRecord(p.getUniqueId(), p.getName(), kname, p.getLocation(), cloned));
        if (deathRecords.size() > 45) deathRecords.remove(deathRecords.size() - 1);
    }

    private void openIadeMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&#00ffccİade Menüsü"));
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        for (int i = 0; i < deathRecords.size(); i++) {
            DeathRecord dr = deathRecords.get(i);
            ItemStack chest = new ItemStack(Material.CHEST);
            ItemMeta m = chest.getItemMeta();
            m.setDisplayName(color("&#ffcc00" + dr.playerName));
            m.setLore(Arrays.asList(color("&fID: &7#" + dr.id), color("&fÖldüren: &c" + dr.killerName), color("&fSaat: &e" + sdf.format(new Date(dr.time)))));
            chest.setItemMeta(m);
            inv.setItem(i, chest);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (e.getView().getTitle().contains("İade Menüsü")) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.CHEST) {
                String idLine = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getLore().get(0));
                String id = idLine.split("#")[1];
                openSpecificRecord((Player) e.getWhoClicked(), id);
            }
        } else if (e.getView().getTitle().startsWith(color("&#ff3300"))) {
            e.setCancelled(true);
            String title = ChatColor.stripColor(e.getView().getTitle());
            String id = title.split("#")[1];
            DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
            if (dr == null) return;

            if (e.getRawSlot() == 49) { // İade Butonu
                Player target = Bukkit.getPlayer(dr.playerUUID);
                if (target != null) {
                    target.getInventory().setContents(dr.items);
                    e.getWhoClicked().sendMessage(color("&aEşyalar slotlarına iade edildi!"));
                }
            }
        }
    }

    private void openSpecificRecord(Player p, String id) {
        DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
        if (dr == null) return;
        Inventory inv = Bukkit.createInventory(null, 54, color("&#ff3300Kayit: #" + dr.id));
        for (int i = 0; i < dr.items.length && i < 41; i++) if (dr.items[i] != null) inv.setItem(i, dr.items[i]);
        ItemStack btn = new ItemStack(Material.DIAMOND);
        ItemMeta m = btn.getItemMeta(); m.setDisplayName(color("&aIade Et")); btn.setItemMeta(m);
        inv.setItem(49, btn);
        p.openInventory(inv);
    }

    private void openSwordMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&8Kiliclar"));
        inv.setItem(10, getSpecialSword("gardiyan"));
        inv.setItem(11, getSpecialSword("wither"));
        p.openInventory(inv);
    }

    public String color(String t) {
        Pattern pattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher m = pattern.matcher(t);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex = m.group(1);
            StringBuilder r = new StringBuilder("§x");
            for (char c : hex.toCharArray()) r.append("§").append(c);
            m.appendReplacement(sb, r.toString());
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    // Gerekli metodlar
    private void startPlaytime() { new BukkitRunnable() { public void run() { Bukkit.getOnlinePlayers().forEach(p -> playtime.merge(p.getUniqueId(), 1, Integer::sum)); }}.runTaskTimer(this, 1200, 1200); }
    private void startHoloUpdater() { new BukkitRunnable() { public void run() { if (!activeHolograms.isEmpty()) { clearHolos(); activeHolograms.forEach(LoginX.this::buildHolo); }}}.runTaskTimer(this, 100, 100); }
    private void buildHolo(Location loc, String type) { /* Hologram inşa kodları buraya */ }
    private void clearHolos() { spawnedStands.forEach(Entity::remove); spawnedStands.clear(); }
    private void startWeeklyReset() { /* Reset kodları */ }
    private void loadStats() { /* Yükleme kodları */ }
    private void saveStats() { /* Kayıt kodları */ }
} // Hatanın sebebi olan eksik parantez burasıydı, eklendi.
