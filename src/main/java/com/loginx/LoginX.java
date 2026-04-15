package com.loginx;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LoginX extends JavaPlugin implements Listener {

    private final HashMap<UUID, HashMap<String, Long>> cooldowns = new HashMap<>();
    private final HashMap<UUID, Long> elytraBanned = new HashMap<>();
    private final List<DeathRecord> deathRecords = new ArrayList<>();
    private int flopTick = 0;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                flopTick++;
            }
        }.runTaskTimer(this, 0, 2);
        
        getLogger().info("Fear Craft LoginX Aktif! Scoreboard silindi, sistemler optimize edildi.");
    }

    private static class DeathRecord {
        String id;
        UUID playerUUID;
        String playerName, killerName;
        Location loc;
        ItemStack[] items;

        public DeathRecord(Player p, String killer, ItemStack[] itms) {
            this.id = Integer.toHexString(new Random().nextInt(0xFFFF)).toUpperCase();
            this.playerUUID = p.getUniqueId();
            this.playerName = p.getName();
            this.killerName = killer;
            this.loc = p.getLocation();
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
        Inventory inv = Bukkit.createInventory(null, 36, color("&#3b3b3bKılıç Menü"));
        String[] types = {"shulker", "creeper", "orumcek", "phantom", "yildirim", "ejderha", "gardiyan", "wither", "enderman"};
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 21, 23};
        
        for (int i = 0; i < types.length; i++) {
            inv.setItem(slots[i], getSpecialSword(types[i]));
        }
        p.openInventory(inv);
    }

    private ItemStack getSpecialSword(String type) {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        String dName = "";
        switch (type) {
            case "shulker" -> dName = "&#e8473e&lShulker Kılıcı";
            case "creeper" -> dName = "&#00e600&lCreeper Kılıcı";
            case "orumcek" -> dName = "&#bd0000&lÖrümcek Kılıcı";
            case "phantom" -> dName = "&#0080ff&lPhantom Kılıcı";
            case "yildirim" -> dName = "&#ffee00&lYıldırım Kılıcı";
            case "ejderha" -> dName = "&#ffaa00&lEjderha Kılıcı";
            case "gardiyan" -> dName = "&#00e6b8&lGardiyan Kılıcı";
            case "wither" -> dName = "&#404040&lWither Kılıcı";
            case "enderman" -> dName = "&#cc33ff&lEnderman Kılıcı";
        }
        meta.setDisplayName(color(dName));
        meta.setLore(Arrays.asList(getFlopText("✦ Fear Craft Özel ✦", flopTick), color("&7Sağ tıkla yeteneği kullan!")));
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
        else if (name.contains("enderman")) handleSkill(p, "enderman", 30);
    }

    private void handleSkill(Player p, String type, int sec) {
        cooldowns.putIfAbsent(p.getUniqueId(), new HashMap<>());
        long expire = cooldowns.get(p.getUniqueId()).getOrDefault(type, 0L);
        
        if (System.currentTimeMillis() < expire) {
            long left = (expire - System.currentTimeMillis()) / 1000;
            p.sendTitle("", color("&#ff3300Tekrar kullanmadan önce &#ffcc00" + (left + 1) + " &#ff3300saniye bekleyin!"), 0, 30, 5);
            return;
        }

        List<Player> nearby = p.getNearbyEntities(6, 6, 6).stream()
                .filter(en -> en instanceof Player && en != p).map(en -> (Player)en).toList();

        if (nearby.isEmpty()) {
            p.sendMessage(color("&e[!] Yakınlarda oyuncu yok, yetenek harcanmadı."));
            return;
        }

        cooldowns.get(p.getUniqueId()).put(type, System.currentTimeMillis() + (sec * 1000L));
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(getFlopText("» " + type.toUpperCase() + " YETENEĞİ «", flopTick)));

        switch (type) {
            case "creeper" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation(), 1);
                nearby.forEach(t -> { t.damage(6); t.setVelocity(t.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.5).setY(0.5)); });
            }
            case "orumcek" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1f, 1f);
                nearby.forEach(t -> {
                    Location loc = t.getLocation().getBlock().getLocation().add(0.5, 1.0, 0.5);
                    loc.getBlock().setType(Material.COBWEB);
                    new BukkitRunnable() { public void run() { loc.getBlock().setType(Material.AIR); }}.runTaskLater(this, 70);
                });
            }
            case "ejderha" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                nearby.forEach(t -> { t.setFireTicks(60); t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1)); t.getWorld().spawnParticle(Particle.DRAGON_BREATH, t.getLocation(), 15); });
            }
            case "phantom" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1f, 1f);
                nearby.forEach(t -> { elytraBanned.put(t.getUniqueId(), System.currentTimeMillis() + 3000L); t.setGliding(false); t.sendTitle("", color("&#ff0000Elitran Bozuldu!"), 5, 20, 5); });
            }
            case "enderman" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                nearby.forEach(t -> { t.teleport(t.getLocation().add(0, 5, 0)); t.getWorld().spawnParticle(Particle.PORTAL, t.getLocation(), 20); });
            }
            case "shulker" -> nearby.forEach(t -> t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 1)));
            case "yildirim" -> nearby.forEach(t -> t.getWorld().strikeLightning(t.getLocation()));
            case "gardiyan" -> nearby.forEach(t -> t.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 2)));
            case "wither" -> nearby.forEach(t -> t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1)));
        }
    }

    // ===================== İADE SİSTEMİ =====================
    private void openIadeMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, getFlopText("Fear Craft - İade", flopTick));
        for (int i = 0; i < deathRecords.size() && i < 45; i++) {
            DeathRecord dr = deathRecords.get(i);
            ItemStack chest = new ItemStack(Material.CHEST);
            ItemMeta m = chest.getItemMeta();
            m.setDisplayName(color("&#ffcc00" + dr.playerName + " &7#" + dr.id));
            m.setLore(Arrays.asList(getFlopText("Ölüm Verisi", flopTick + i), color("&fKatil: &c" + dr.killerName), color("&aTıkla ve İade Et")));
            chest.setItemMeta(m);
            inv.setItem(i, chest);
        }
        inv.setItem(49, createBtn(Material.BARRIER, "&cKapat"));
        p.openInventory(inv);
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        if (title.contains("İade") || title.contains("Kılıç")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;
            if (e.getCurrentItem().getType() == Material.CHEST) {
                String id = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).split("#")[1];
                DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
                if (dr != null) {
                    Player target = Bukkit.getPlayer(dr.playerUUID);
                    if (target != null && target.isOnline()) {
                        target.getInventory().setContents(dr.items);
                        e.getWhoClicked().sendMessage(color("&aEşyalar @" + dr.playerName + " kişisine verildi."));
                    }
                }
            } else if (e.getCurrentItem().getType() == Material.NETHERITE_SWORD) {
                e.getWhoClicked().getInventory().addItem(e.getCurrentItem().clone());
            } else if (e.getRawSlot() == 49) e.getWhoClicked().closeInventory();
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        deathRecords.add(0, new DeathRecord(e.getEntity(), e.getEntity().getKiller() != null ? e.getEntity().getKiller().getName() : "Bilinmiyor", e.getEntity().getInventory().getContents()));
    }

    // ===================== UTILS =====================
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

    public String getFlopText(String t, int tick) {
        String[] colors = {"&#00ccff", "&#33ffff", "&#66ffff", "&#ffffff", "&#66ffff", "&#33ffff"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            sb.append(colors[(i + tick) % colors.length]).append(t.charAt(i));
        }
        return color(sb.toString());
    }

    private ItemStack createBtn(Material m, String n) {
        ItemStack i = new ItemStack(m);
        ItemMeta mt = i.getItemMeta();
        mt.setDisplayName(color(n));
        i.setItemMeta(mt);
        return i;
    }

    @EventHandler public void onGlide(EntityToggleGlideEvent e) {
        if (e.getEntity() instanceof Player p && elytraBanned.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) e.setCancelled(true);
    }
            }
                            
