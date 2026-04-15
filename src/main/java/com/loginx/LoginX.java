package com.loginx;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
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
    private final HashMap<UUID, Integer> playtime = new HashMap<>();
    private final HashMap<UUID, Integer> blocksBroken = new HashMap<>();
    private final HashMap<UUID, Integer> kills = new HashMap<>();
    private long nextResetTime;

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
        lore.add(color("&fÖzellik: &7Sağ tık özel skill"));

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
            long left = (expire - System.currentTimeMillis()) / 1000;
            // Cooldown Subtitle Mesajı
            p.sendTitle("", color("&#ff3300Tekrar kullanmadan önce &#ffcc00" + left + " &#ff3300saniye bekleyin!"), 0, 40, 10);
            return;
        }
        cooldowns.get(p.getUniqueId()).put(type, System.currentTimeMillis() + (sec * 1000L));
        
        List<Player> nearby = p.getNearbyEntities(5, 5, 5).stream().filter(en -> en instanceof Player && en != p).map(en -> (Player)en).toList();

        // RGB Flop Action Bar
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(getFlopText("&l" + type.toUpperCase() + " KILIÇ YETENEĞİ AKTİF!", sec)));

        switch (type) {
            case "shulker" -> {
                p.sendMessage(color("&#e8473eYükseklerde uçuşun tadını çıkar!"));
                nearby.forEach(t -> t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 1)));
            }
            case "creeper" -> {
                p.sendMessage(color("&#00e600BUM! Patlayıcı gücü hisset."));
                p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation(), 1);
                nearby.forEach(t -> { t.damage(6); t.setVelocity(t.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.2)); });
            }
            case "orumcek" -> {
                p.sendMessage(color("&#bd0000Ağlara dolandılar, kaçış yok!"));
                nearby.forEach(t -> {
                    Location loc = t.getLocation();
                    loc.getBlock().setType(Material.COBWEB);
                    loc.add(0, 1, 0).getBlock().setType(Material.COBWEB);
                    new BukkitRunnable() { public void run() { loc.getBlock().setType(Material.AIR); loc.subtract(0, 1, 0).getBlock().setType(Material.AIR); }}.runTaskLater(this, 80);
                });
            }
            case "phantom" -> {
                p.sendMessage(color("&#0080ffGöklerden süzülen kabus... Elitralar kapalı."));
                nearby.forEach(t -> {
                    elytraBanned.put(t.getUniqueId(), System.currentTimeMillis() + 3000L);
                    if (t.isGliding()) t.setGliding(false);
                    t.sendTitle("", color("&#ff0000Elitran 3 saniye bozuldu!"), 5, 20, 5);
                    t.playSound(t.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1f, 1f);
                });
            }
            case "yildirim" -> {
                p.sendMessage(color("&#ffee00Göklerin gazabı üzerinize olsun!"));
                nearby.forEach(t -> { t.getWorld().strikeLightningEffect(t.getLocation()); t.damage(5); });
            }
            case "ejderha" -> {
                p.sendMessage(color("&#ffaa00Ejderha nefesi her şeyi yakıp geçer!"));
                nearby.forEach(t -> { t.setFireTicks(60); t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1)); });
            }
            case "gardiyan" -> {
                p.sendMessage(color("&#00e6b8Denizlerin laneti seni yavaşlatıyor..."));
                p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1f);
                nearby.forEach(t -> { t.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 2)); t.spawnParticle(Particle.ELDER_GUARDIAN, t.getLocation(), 1); });
            }
            case "wither" -> {
                p.sendMessage(color("&#404040Solgunluk tüm bedeni sarıyor."));
                nearby.forEach(t -> t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1)));
            }
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
        if (deathRecords.size() > 45) deathRecords.remove(deathRecords.size() - 1);
    }

    private void openIadeMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, getFearCraftHeader());
        updateIadeList(inv);
        p.openInventory(inv);
    }

    private void updateIadeList(Inventory inv) {
        inv.clear();
        for (int i = 0; i < deathRecords.size() && i < 45; i++) {
            DeathRecord dr = deathRecords.get(i);
            ItemStack chest = new ItemStack(Material.CHEST);
            ItemMeta m = chest.getItemMeta();
            m.setDisplayName(color("&#ffcc00" + dr.playerName + " &7#" + dr.id));
            m.setLore(Arrays.asList(
                color("&fÖldüren: &c" + dr.killerName),
                color("&8--------------------"),
                color("&eVeri ID: &f" + dr.id),
                color("&7Bu kayıt oyuncunun"),
                color("&7son öldüğü anı"),
                color("&7temsil eder."),
                color("&8--------------------"),
                color("&aTıkla ve İade Et")
            ));
            chest.setItemMeta(m);
            inv.setItem(i, chest);
        }
        // Simetrik Alt Seçenekler
        inv.setItem(48, createBtn(Material.PAPER, "&#00ccffBilgi", "&7Son 45 ölüm kaydı tutulur."));
        inv.setItem(49, createBtn(Material.BARRIER, "&#ff0000Kapat", "&7Menüyü kapatır."));
        inv.setItem(50, createBtn(Material.RECOVERY_COMPASS, "&#00ffccDestek", "&7Bir sorun varsa yetkiliye bildirin."));
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        if (title.contains("Özel Kılıç Menüsü")) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.NETHERITE_SWORD) e.getWhoClicked().getInventory().addItem(e.getCurrentItem().clone());
        } else if (title.contains("Fear Craft")) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null) {
                if (e.getCurrentItem().getType() == Material.CHEST) {
                    String id = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).split("#")[1];
                    DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
                    if (dr != null) {
                        Player target = Bukkit.getPlayer(dr.playerUUID);
                        if (target != null && target.isOnline()) { target.getInventory().setContents(dr.items); e.getWhoClicked().sendMessage(color("&aEşyalar @" + dr.playerName + " kişisine iade edildi.")); }
                    }
                } else if (e.getRawSlot() == 49) { e.getWhoClicked().closeInventory(); } // Kapat Butonu
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

    private String getFearCraftHeader() {
        return color("&#00ccffF&#00e6ffe&#33ffffa&#66ffffr &#99ffffC&#ccffffr&#e6ffffa&#fffffff&#e6fffft &#ccffff- &#99ffffİ&#66ffffa&#33ffffd&#00e6ffe &#00ccffS&#00ccffi&#00e6ffs&#33fffft&#66ffffe&#99ffffm&#ccffffi");
    }

    private String getFlopText(String t, long seed) {
        // scrolling gradient tabanlı RGB flop
        String base = ChatColor.stripColor(t);
        StringBuilder sb = new StringBuilder();
        String[] colors = {"&#ff0000", "&#ffee00", "&#00ff00", "&#00ffff", "&#0000ff", "&#ff00ff"};
        for (int i = 0; i < base.length(); i++) {
            int hIdx = (i + (int)flopTick + (int)seed) % colors.length;
            sb.append(colors[hIdx]).append(base.charAt(i));
        }
        return color(sb.toString());
    }

    private ItemStack createBtn(Material m, String name, String lore) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(Collections.singletonList(color(lore)));
        item.setItemMeta(meta);
        return item;
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
                                
