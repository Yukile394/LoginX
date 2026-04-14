package com.loginx;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class SwordManager implements Listener {

    private final Plugin plugin;

    public SwordManager(Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // TAGS
    private static final String TAG_SHULKER = "§0§kSHULKER";
    private static final String TAG_ENDERMAN = "§0§kENDERMAN";
    private static final String TAG_SPIDER = "§0§kSPIDER";
    private static final String TAG_PHANTOM = "§0§kPHANTOM";
    private static final String TAG_GOLEM = "§0§kGOLEM";
    private static final String TAG_CREEPER = "§0§kCREEPER";

    // COOLDOWN
    private final Map<UUID, Long> cd = new HashMap<>();

    private boolean cooldown(Player p, int sec) {
        long now = System.currentTimeMillis();
        if (cd.containsKey(p.getUniqueId()) && now - cd.get(p.getUniqueId()) < sec * 1000L) {
            p.sendMessage("§cBekleme süresi var!");
            return false;
        }
        cd.put(p.getUniqueId(), now);
        return true;
    }

    // ================= KILIÇ OLUŞTURMA =================

    public ItemStack makeShulkerSword() {
        return build("§5Shulker Kılıcı", TAG_SHULKER);
    }

    public ItemStack makeEndermanSword() {
        return build("§1Enderman Kılıcı", TAG_ENDERMAN);
    }

    public ItemStack makeSpiderSword() {
        return build("§2Örümcek Kılıcı", TAG_SPIDER);
    }

    public ItemStack makePhantomSword() {
        return build("§3Phantom Kılıcı", TAG_PHANTOM);
    }

    public ItemStack makeGolemSword() {
        return build("§fGolem Kılıcı", TAG_GOLEM);
    }

    public ItemStack makeCreeperSword() {
        return build("§aCreeper Kılıcı", TAG_CREEPER);
    }

    private ItemStack build(String name, String tag) {
        ItemStack i = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(name);
        m.setLore(Arrays.asList("§7Özel kılıç", tag));
        m.addEnchant(Enchantment.SHARPNESS, 5, true);
        m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        i.setItemMeta(m);
        return i;
    }

    private boolean has(ItemStack i, String tag) {
        if (i == null || !i.hasItemMeta()) return false;
        if (!i.getItemMeta().hasLore()) return false;
        return i.getItemMeta().getLore().contains(tag);
    }

    // ================= SAĞ TIK =================

    @EventHandler
    public void click(PlayerInteractEvent e) {
        if (!e.getAction().toString().contains("RIGHT")) return;

        Player p = e.getPlayer();
        ItemStack i = p.getInventory().getItemInMainHand();

        if (has(i, TAG_SHULKER)) useShulker(p);
        if (has(i, TAG_ENDERMAN)) useEnderman(p);
        if (has(i, TAG_SPIDER)) useSpider(p);
        if (has(i, TAG_PHANTOM)) usePhantom(p);
        if (has(i, TAG_GOLEM)) useGolem(p);
        if (has(i, TAG_CREEPER)) useCreeper(p);
    }

    private List<Player> near(Player p, double r) {
        List<Player> list = new ArrayList<>();
        for (Entity e : p.getNearbyEntities(r, r, r))
            if (e instanceof Player && e != p) list.add((Player) e);
        return list;
    }

    // ================= SKILL =================

    private void useShulker(Player p) {
        if (!cooldown(p, 30)) return;
        for (Player t : near(p, 8)) {
            t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 1));
            t.getWorld().spawnParticle(Particle.DRAGON_BREATH, t.getLocation(), 20);
        }
    }

    private void useEnderman(Player p) {
        if (!cooldown(p, 30)) return;
        for (Player t : near(p, 8)) {
            t.setVelocity(t.getLocation().getDirection().multiply(2));
            t.getWorld().spawnParticle(Particle.WITCH, t.getLocation(), 20);
        }
    }

    private void useSpider(Player p) {
        if (!cooldown(p, 30)) return;
        for (Player t : near(p, 8)) {
            t.getWorld().spawnParticle(Particle.ITEM_SLIME, t.getLocation(), 20);
        }
    }

    private void usePhantom(Player p) {
        if (!cooldown(p, 60)) return;
        for (Player t : near(p, 8)) {
            t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 1));
            t.getWorld().spawnParticle(Particle.ENTITY_EFFECT, t.getLocation(), 20);
        }
    }

    private void useGolem(Player p) {
        if (!cooldown(p, 60)) return;
        for (Player t : near(p, 6)) {
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2));
            t.getWorld().spawnParticle(Particle.CRIT, t.getLocation(), 20);
        }
    }

    private void useCreeper(Player p) {
        if (!cooldown(p, 30)) return;

        Location loc = p.getLocation();
        World w = p.getWorld();

        w.spawnParticle(Particle.EXPLOSION, loc, 10);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 5);
        w.spawnParticle(Particle.LARGE_SMOKE, loc, 20);

        for (Player t : near(p, 5)) {
            Vector v = t.getLocation().toVector().subtract(loc.toVector()).normalize();
            t.setVelocity(v.multiply(2));
        }
    }

    // ================= VER =================

    public void giveToPlayer(Player p, ItemStack i) {
        p.getInventory().addItem(i);
        p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation(), 20);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1, 1);
    }
}
