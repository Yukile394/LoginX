package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class LoginX extends JavaPlugin implements Listener {

    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("LoginX Aktif");
    }

    // ===================== COMMAND =====================
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player p)) return true;

        if (!p.hasPermission("loginx.admin")) return true;

        switch (cmd.getName().toLowerCase()) {

            case "kilicvermenu" -> openMenu(p);

            case "shulkerkilicver" -> give(p, createSword("§5Shulker Kılıcı"), "shulker");

            case "endermankilicver" -> give(p, createSword("§1Enderman Kılıcı"), "enderman");

            case "orumcekkilicver" -> give(p, createSword("§2Örümcek Kılıcı"), "spider");

            case "phantomkilicver" -> give(p, createSword("§3Phantom Kılıcı"), "phantom");

            case "golemkilicver" -> give(p, createSword("§fGolem Kılıcı"), "golem");

            case "creeperkilicver" -> give(p, createSword("§aCreeper Kılıcı"), "creeper");
        }
        return true;
    }

    // ===================== MENU =====================
    private void openMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8Kılıç Menü");

        inv.setItem(10, createSword("§5Shulker Kılıcı"));
        inv.setItem(12, createSword("§1Enderman Kılıcı"));
        inv.setItem(14, createSword("§2Örümcek Kılıcı"));
        inv.setItem(16, createSword("§3Phantom Kılıcı"));
        inv.setItem(22, createSword("§aCreeper Kılıcı"));

        p.openInventory(inv);
    }

    private ItemStack createSword(String name) {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of("§7Sağ tık: özel yetenek"));
        item.setItemMeta(meta);
        return item;
    }

    private void give(Player p, ItemStack item, String id) {
        p.getInventory().addItem(item);
        p.sendMessage("§aKılıç verildi: " + id);
    }

    // ===================== COOLDOWN =====================
    private boolean cd(Player p, String key, int sec) {
        cooldowns.putIfAbsent(p.getUniqueId(), new HashMap<>());
        Map<String, Long> map = cooldowns.get(p.getUniqueId());

        long now = System.currentTimeMillis();
        long end = map.getOrDefault(key, 0L);

        if (now < end) {
            long left = (end - now) / 1000;
            p.sendMessage("§cCooldown: " + left + "s");
            return false;
        }

        map.put(key, now + (sec * 1000L));
        return true;
    }

    // ===================== RIGHT CLICK =====================
    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getItem() == null) return;

        Player p = e.getPlayer();
        ItemStack item = e.getItem();

        if (item.getType() != Material.NETHERITE_SWORD) return;
        if (!item.hasItemMeta()) return;

        String name = item.getItemMeta().getDisplayName();

        Location loc = p.getLocation();

        // ================= SHULKER =================
        if (name.contains("Shulker")) {
            if (!cd(p, "shulker", 30)) return;

            p.sendMessage("§5Shulker gücü aktif!");

            for (Entity en : p.getNearbyEntities(6, 6, 6)) {
                if (en instanceof Player t) {
                    t.setVelocity(new Vector(0, 1.2, 0));
                    t.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOWNESS, 60, 2));
                }
            }
        }

        // ================= ENDERMAN =================
        if (name.contains("Enderman")) {
            if (!cd(p, "enderman", 30)) return;

            for (Entity en : p.getNearbyEntities(6, 6, 6)) {
                if (en instanceof Player t) {
                    Vector v = p.getLocation().getDirection().multiply(2.5).setY(0.6);
                    t.setVelocity(v);
                }
            }
            p.sendMessage("§1Enderman dash!");
        }

        // ================= SPIDER =================
        if (name.contains("Örümcek")) {
            if (!cd(p, "spider", 30)) return;

            List<Location> webs = new ArrayList<>();

            for (Entity en : p.getNearbyEntities(4, 4, 4)) {
                if (en instanceof Player t) {
                    Location l = t.getLocation();
                    webs.add(l);

                    l.getBlock().setType(Material.COBWEB);
                }
            }

            p.sendMessage("§2Örümcek ağı!");

            new BukkitRunnable() {
                public void run() {
                    for (Location l : webs) {
                        l.getBlock().setType(Material.AIR);
                    }
                }
            }.runTaskLater(this, 70L);
        }

        // ================= PHANTOM =================
        if (name.contains("Phantom")) {
            if (!cd(p, "phantom", 230)) return;

            int i = 0;
            for (Entity en : p.getNearbyEntities(6, 6, 6)) {
                if (en instanceof Player t && i < 2) {
                    t.setGliding(false);
                    t.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOW_FALLING, 60, 1));
                    i++;
                }
            }

            p.sendMessage("§3Phantom kilidi!");
        }

        // ================= GOLEM =================
        if (name.contains("Golem")) {
            if (!cd(p, "golem", 230)) return;

            for (Entity en : p.getNearbyEntities(6, 6, 6)) {
                if (en instanceof Player t) {
                    t.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.RESISTANCE, 60, 2));

                    t.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOWNESS, 40, 3));
                }
            }

            p.sendMessage("§fGolem güç!");
        }

        // ================= CREEPER =================
        if (name.contains("Creeper")) {
            if (!cd(p, "creeper", 30)) return;

            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1, 1);
            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 2);

            for (Entity en : p.getNearbyEntities(4, 4, 4)) {
                if (en instanceof Player t) {
                    Vector v = t.getLocation().toVector()
                            .subtract(loc.toVector())
                            .normalize()
                            .multiply(1.5);
                    v.setY(0.6);
                    t.setVelocity(v);
                }
            }

            p.sendMessage("§aCreeper boom!");
        }
    }
}
