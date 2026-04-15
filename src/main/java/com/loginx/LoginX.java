package com.loginx;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
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

    private final HashMap<UUID, HashMap<String, Long>> cooldowns = new HashMap<>();
    private final HashMap<UUID, Long> elytraBanned = new HashMap<>();
    private final List<DeathRecord> deathRecords = new ArrayList<>();
    private final List<ArmorStand> spawnedStands = new ArrayList<>();
    private final HashMap<Location, String> activeHolograms = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        startMenuAnimator();
        getLogger().info("LoginX - Fear Craft Sistemleri Yuklendi!");
    }

    // İade Kayıt Yapısı
    private static class DeathRecord {
        String id;
        UUID playerUUID;
        String playerName, killerName;
        Location loc;
        long time;
        ItemStack[] items;

        public DeathRecord(Player p, String killer, ItemStack[] itms) {
            this.id = Integer.toHexString(new Random().nextInt(0xFFFF)).toUpperCase();
            this.playerUUID = p.getUniqueId();
            this.playerName = p.getName();
            this.killerName = killer;
            this.loc = p.getLocation();
            this.time = System.currentTimeMillis();
            this.items = itms;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (!p.hasPermission("loginx.admin")) return true;

        if (cmd.getName().equalsIgnoreCase("kilicvermenu")) openSwordMenu(p);
        else if (cmd.getName().equalsIgnoreCase("iade")) openIadeMenu(p);
        return true;
    }

    // ===================== KILIÇ SİSTEMİ =====================
    private void openSwordMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 36, color("&#3b3b3bÖzel Kılıç Menüsü"));
        String[] types = {"shulker", "creeper", "orumcek", "phantom", "yildirim", "ejderha", "gardiyan", "wither"};
        for (int i = 0; i < types.length; i++) inv.setItem(10 + i + (i > 5 ? 2 : 0), getSpecialSword(types[i]));
        p.openInventory(inv);
    }

    private ItemStack getSpecialSword(String type) {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Yetenek: Sağ Tık"));

        switch (type) {
            case "shulker" -> { meta.setDisplayName(color("&#e8473e&lShulker Kılıcı")); lore.add(color("&8Bekleme: 30s")); }
            case "creeper" -> { meta.setDisplayName(color("&#00e600&lCreeper Kılıcı")); lore.add(color("&8Bekleme: 25s")); }
            case "orumcek" -> { meta.setDisplayName(color("&#bd0000&lÖrümcek Kılıcı")); lore.add(color("&8Bekleme: 60s")); }
            case "phantom" -> { meta.setDisplayName(color("&#0080ff&lPhantom Kılıcı")); lore.add(color("&8Bekleme: 65s")); }
            case "yildirim" -> { meta.setDisplayName(color("&#ffee00&lYıldırım Kılıcı")); lore.add(color("&8Bekleme: 30s")); }
            case "ejderha" -> { meta.setDisplayName(color("&#ffaa00&lEjderha Kılıcı")); lore.add(color("&8Bekleme: 30s")); }
            case "gardiyan" -> { meta.setDisplayName(color("&#00e6b8&lGardiyan Kılıcı")); lore.add(color("&8Bekleme: 60s")); }
            case "wither" -> { meta.setDisplayName(color("&#404040&lWither Kılıcı")); lore.add(color("&8Bekleme: 30s")); }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!e.getAction().name().contains("RIGHT")) return;
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() != Material.NETHERITE_SWORD || !item.hasItemMeta()) return;

        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();
        if (name.contains("shulker")) handleSkill(p, "shulker", 30);
        else if (name.contains("creeper")) handleSkill(p, "creeper", 25);
        else if (name.contains("örümcek")) handleSkill(p, "orumcek", 60);
        else if (name.contains("phantom")) handleSkill(p, "phantom", 65);
        else if (name.contains("yıldırım")) handleSkill(p, "yildirim", 30);
        else if (name.contains("ejderha")) handleSkill(p, "ejderha", 30);
        else if (name.contains("gardiyan")) handleSkill(p, "gardiyan", 60);
        else if (name.contains("wither")) handleSkill(p, "wither", 30);
    }

    private void handleSkill(Player p, String type, int sec) {
        cooldowns.putIfAbsent(p.getUniqueId(), new HashMap<>());
        long expire = cooldowns.get(p.getUniqueId()).getOrDefault(type, 0L);
        if (System.currentTimeMillis() < expire) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(color("&cBekle: " + (expire - System.currentTimeMillis()) / 1000 + "s")));
            return;
        }
        cooldowns.get(p.getUniqueId()).put(type, System.currentTimeMillis() + (sec * 1000L));
        
        List<Player> nearby = p.getNearbyEntities(5, 5, 5).stream().filter(en -> en instanceof Player && en != p).map(en -> (Player)en).toList();

        switch (type) {
            case "shulker" -> nearby.forEach(t -> t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 1)));
            case "creeper" -> {
                p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation(), 1);
                nearby.forEach(t -> { t.damage(6); t.setVelocity(t.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.2)); });
            }
            case "orumcek" -> nearby.forEach(t -> {
                Location loc = t.getLocation();
                loc.getBlock().setType(Material.COBWEB);
                loc.add(0, 1, 0).getBlock().setType(Material.COBWEB);
                new BukkitRunnable() { public void run() { loc.getBlock().setType(Material.AIR); loc.subtract(0, 1, 0).getBlock().setType(Material.AIR); }}.runTaskLater(this, 80);
            });
            case "phantom" -> nearby.forEach(t -> {
                elytraBanned.put(t.getUniqueId(), System.currentTimeMillis() + 3000L);
                if (t.isGliding()) t.setGliding(false);
                t.sendTitle("", color("&#ff0000Elitran 3 saniye bozuldu!"), 5, 20, 5); // KÜÇÜK/ORTA MESAJ
                t.playSound(t.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1f, 1f);
            });
            case "yildirim" -> nearby.forEach(t -> { t.getWorld().strikeLightningEffect(t.getLocation()); t.damage(5); });
            case "ejderha" -> nearby.forEach(t -> { t.setFireTicks(60); t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1)); });
            case "gardiyan" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1f);
                nearby.forEach(t -> { t.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 2)); t.spawnParticle(Particle.ELDER_GUARDIAN, t.getLocation(), 1); });
            }
            case "wither" -> nearby.forEach(t -> t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1)));
        }
    }

    @EventHandler
    public void onGlide(EntityToggleGlideEvent e) {
        if (e.getEntity() instanceof Player p && e.isGliding()) {
            if (elytraBanned.containsKey(p.getUniqueId()) && elytraBanned.get(p.getUniqueId()) > System.currentTimeMillis()) e.setCancelled(true);
        }
    }

    // ===================== İADE SİSTEMİ =====================
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        String killer = (p.getKiller() != null) ? p.getKiller().getName() : "Doga";
        ItemStack[] items = p.getInventory().getContents();
        ItemStack[] backup = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) if (items[i] != null) backup[i] = items[i].clone();
        deathRecords.add(0, new DeathRecord(p, killer, backup));
        if (deathRecords.size() > 45) deathRecords.remove(44);
    }

    private void openIadeMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&#ff0000F&#ff4400e&#ff8800a&#ffcc00r &#ffff00C&#ccff00r&#88ff00a&#44ff00f&#00ff00t &f- &7İade"));
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        for (int i = 0; i < deathRecords.size(); i++) {
            DeathRecord dr = deathRecords.get(i);
            ItemStack chest = new ItemStack(Material.CHEST);
            ItemMeta m = chest.getItemMeta();
            m.setDisplayName(color("&#ffcc00" + dr.playerName + " &7#" + dr.id));
            m.setLore(Arrays.asList(color("&fÖldüren: &c" + dr.killerName), color("&fSaat: &e" + sdf.format(new Date(dr.time))), color("&aTıkla ve İade Et")));
            chest.setItemMeta(m);
            inv.setItem(i, chest);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        if (title.contains("Özel Kılıç Menüsü")) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.NETHERITE_SWORD) e.getWhoClicked().getInventory().addItem(e.getCurrentItem().clone());
        } else if (title.contains("Fear Craft")) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.CHEST) {
                String id = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).split("#")[1];
                DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
                if (dr != null) {
                    Player target = Bukkit.getPlayer(dr.playerUUID);
                    if (target != null) { target.getInventory().setContents(dr.items); e.getWhoClicked().sendMessage(color("&aEşyalar @" + dr.playerName + " kişisine iade edildi.")); }
                }
            }
        }
    }

    // ===================== YARDIMCI METOTLAR =====================
    private int flopTick = 0;
    private void startMenuAnimator() {
        new BukkitRunnable() {
            public void run() { flopTick++; }
        }.runTaskTimer(this, 0, 5);
    }

    public String color(String text) {
        Pattern pattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) replacement.append("§").append(c);
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
                                              }
