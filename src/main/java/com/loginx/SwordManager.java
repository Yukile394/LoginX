package com.loginx;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class SwordManager implements Listener {

    private final Plugin plugin;

    private final Map<UUID, Long> shulkerCd  = new HashMap<>();
    private final Map<UUID, Long> endermanCd = new HashMap<>();
    private final Map<UUID, Long> spiderCd   = new HashMap<>();
    private final Map<UUID, Long> phantomCd  = new HashMap<>();
    private final Map<UUID, Long> golemCd    = new HashMap<>();
    private final Map<UUID, Long> creeperCd  = new HashMap<>();

    private static final String TAG_SHULKER  = "§0§kSHULKER_SWORD";
    private static final String TAG_ENDERMAN = "§0§kENDERMAN_SWORD";
    private static final String TAG_SPIDER   = "§0§kSPIDER_SWORD";
    private static final String TAG_PHANTOM  = "§0§kPHANTOM_SWORD";
    private static final String TAG_GOLEM    = "§0§kGOLEM_SWORD";
    private static final String TAG_CREEPER  = "§0§kCREEPER_SWORD";

    private static final long CD_SHULKER  = 30_000L;
    private static final long CD_ENDERMAN = 30_000L;
    private static final long CD_SPIDER   = 30_000L;
    private static final long CD_PHANTOM  = 230_000L;
    private static final long CD_GOLEM    = 230_000L;
    private static final long CD_CREEPER  = 30_000L;

    private final Map<UUID, BukkitRunnable> countdownTasks = new HashMap<>();

    public SwordManager(Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void applyNetheriteEnchants(ItemMeta m) {
        m.addEnchant(Enchantment.SHARPNESS, 5, true);
        m.addEnchant(Enchantment.UNBREAKING, 3, true);
        m.addEnchant(Enchantment.MENDING, 1, true);
        m.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
        m.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
        m.addEnchant(Enchantment.LOOTING, 3, true);
    }

    private boolean hasTag(ItemStack item, String tag) {
        if (item == null || !item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasLore()) return false;
        return item.getItemMeta().getLore().contains(tag);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
            e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null) return;

        if (hasTag(item, TAG_SHULKER)) { e.setCancelled(true); useShulker(p); }
        if (hasTag(item, TAG_ENDERMAN)) { e.setCancelled(true); useEnderman(p); }
        if (hasTag(item, TAG_SPIDER)) { e.setCancelled(true); useSpider(p); }
        if (hasTag(item, TAG_PHANTOM)) { e.setCancelled(true); usePhantom(p); }
        if (hasTag(item, TAG_GOLEM)) { e.setCancelled(true); useGolem(p); }
        if (hasTag(item, TAG_CREEPER)) { e.setCancelled(true); useCreeper(p); }
    }

    private boolean checkCooldown(Player p, Map<UUID, Long> map, long cd) {
        long now = System.currentTimeMillis();
        long last = map.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < cd) {
            p.sendMessage("§cBekleme süresi var!");
            return false;
        }
        map.put(p.getUniqueId(), now);
        return true;
    }

    private List<Player> getNearbyPlayers(Player p, double r) {
        List<Player> list = new ArrayList<>();
        for (Entity e : p.getNearbyEntities(r, r, r)) {
            if (e instanceof Player && !e.equals(p)) list.add((Player) e);
        }
        return list;
    }

    private void useShulker(Player p) {
        if (!checkCooldown(p, shulkerCd, CD_SHULKER)) return;

        for (Player t : getNearbyPlayers(p, 8)) {
            t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 1));
            t.getWorld().spawnParticle(Particle.DRAGON_BREATH, t.getLocation(), 30);
        }
    }

    private void useEnderman(Player p) {
        if (!checkCooldown(p, endermanCd, CD_ENDERMAN)) return;

        for (Player t : getNearbyPlayers(p, 7)) {
            t.setVelocity(t.getLocation().getDirection().multiply(2));
            t.getWorld().spawnParticle(Particle.WITCH, t.getLocation(), 20);
        }
    }

    private void useSpider(Player p) {
        if (!checkCooldown(p, spiderCd, CD_SPIDER)) return;

        for (Player t : getNearbyPlayers(p, 8)) {
            t.getWorld().spawnParticle(Particle.ITEM_SLIME, t.getLocation(), 20);
        }
    }

    private void usePhantom(Player p) {
        if (!checkCooldown(p, phantomCd, CD_PHANTOM)) return;

        for (Player t : getNearbyPlayers(p, 10)) {
            t.getWorld().spawnParticle(Particle.ENTITY_EFFECT, t.getLocation(), 20);
        }
    }

    private void useGolem(Player p) {
        if (!checkCooldown(p, golemCd, CD_GOLEM)) return;

        for (Player t : getNearbyPlayers(p, 6)) {
            t.getWorld().spawnParticle(Particle.CRIT, t.getLocation(), 20);
        }
    }

    private void useCreeper(Player p) {
        if (!checkCooldown(p, creeperCd, CD_CREEPER)) return;

        Location loc = p.getLocation();
        World w = p.getWorld();

        w.spawnParticle(Particle.EXPLOSION, loc, 10);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 5);
        w.spawnParticle(Particle.LARGE_SMOKE, loc, 20);
    }

    public void giveToPlayer(Player p, ItemStack sword) {
        p.getInventory().addItem(sword);
        p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation(), 20);
    }
}
