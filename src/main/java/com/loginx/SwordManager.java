package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;

import java.util.*;

/**
 * SwordManager — Netherite kılıç özel yetenekleri
 *
 * Komutlar (plugin.yml'e eklenmeli):
 *   /shulkerkilicver  [oyuncu]
 *   /endermankilicver [oyuncu]
 *   /orumcekkilicver  [oyuncu]
 *   /phantomkilicver  [oyuncu]
 *   /golemkilicver    [oyuncu]
 *   /creeperkilicver  [oyuncu]
 *
 * Cooldown: Phantom & Golem = 230s | diğerleri = 30s
 */
public class SwordManager implements Listener, CommandExecutor {

    // ── NBT Anahtarları ──────────────────────────────────────────────────
    private static final String KEY_SHULKER  = "loginx_shulker_sword";
    private static final String KEY_ENDERMAN = "loginx_enderman_sword";
    private static final String KEY_SPIDER   = "loginx_spider_sword";
    private static final String KEY_PHANTOM  = "loginx_phantom_sword";
    private static final String KEY_GOLEM    = "loginx_golem_sword";
    private static final String KEY_CREEPER  = "loginx_creeper_sword";

    // ── Cooldown süreleri ────────────────────────────────────────────────
    private static final int CD_SHORT = 30;
    private static final int CD_LONG  = 230;

    // ── RGB Paletler: her kılıç için [dark, mid, light] ─────────────────
    private static final int[][] PAL_SHULKER  = {{160,32,240},{200,60,255},{220,120,255}};
    private static final int[][] PAL_ENDERMAN = {{20,0,30},{60,0,100},{100,0,160}};
    private static final int[][] PAL_SPIDER   = {{160,0,0},{210,50,0},{255,110,0}};
    private static final int[][] PAL_PHANTOM  = {{0,80,200},{40,140,255},{80,200,255}};
    private static final int[][] PAL_GOLEM    = {{90,90,90},{170,170,170},{240,240,240}};
    private static final int[][] PAL_CREEPER  = {{0,150,0},{40,200,0},{100,255,50}};

    // ── Durum haritaları ─────────────────────────────────────────────────
    private final Map<Long, Long>           cooldowns      = new HashMap<>();
    private final Map<UUID, BukkitRunnable> countdownTasks = new HashMap<>();
    private final Map<UUID, ItemStack>      storedElytra   = new HashMap<>();

    private final Plugin plugin;

    // ════════════════════════════════════════════════════════════════════
    //  KURULUM
    // ════════════════════════════════════════════════════════════════════
    public SwordManager(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerCmd("shulkerkilicver");
        registerCmd("endermankilicver");
        registerCmd("orumcekkilicver");
        registerCmd("phantomkilicver");
        registerCmd("golemkilicver");
        registerCmd("creeperkilicver");
    }

    private void registerCmd(String name) {
        PluginCommand c = plugin.getCommand(name);
        if (c != null) c.setExecutor(this);
    }

    // ════════════════════════════════════════════════════════════════════
    //  KOMUT: /xkilicver
    // ════════════════════════════════════════════════════════════════════
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage("§cOyuncu bulunamadı: §7" + args[0]); return true; }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage("§cKullanım: /" + cmd.getName() + " <oyuncu>");
            return true;
        }

        ItemStack sword = switch (cmd.getName().toLowerCase()) {
            case "shulkerkilicver"  -> createShulkerSword();
            case "endermankilicver" -> createEndermanSword();
            case "orumcekkilicver"  -> createSpiderSword();
            case "phantomkilicver"  -> createPhantomSword();
            case "golemkilicver"    -> createGolemSword();
            case "creeperkilicver"  -> createCreeperSword();
            default -> null;
        };
        if (sword == null) return false;

        giveWithEffect(target, sword);
        sender.sendMessage("§a✔ §7" + target.getName() + " §7adlı oyuncuya verildi.");
        return true;
    }

    /** Kılıcı eline ver + enchant efekti */
    private void giveWithEffect(Player p, ItemStack sword) {
        ItemStack old = p.getInventory().getItemInMainHand();
        if (old != null && old.getType() != Material.AIR) {
            p.getInventory().addItem(old).values()
                    .forEach(drop -> p.getWorld().dropItemNaturally(p.getLocation(), drop));
        }
        p.getInventory().setItemInMainHand(sword);
        p.sendMessage("§7✦ " + sword.getItemMeta().getDisplayName() +
                      " §r§7eline verildi! §8Sağ tık ile özelliğini kullan.");
        p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1, 0),
                80, 0.5, 0.5, 0.5, 1.0);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
    }

    // ════════════════════════════════════════════════════════════════════
    //  SAĞ TIK DİNLEYİCİ
    // ════════════════════════════════════════════════════════════════════
    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR &&
            e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() != Material.NETHERITE_SWORD || !hand.hasItemMeta()) return;

        PersistentDataContainer pdc = hand.getItemMeta().getPersistentDataContainer();

        if (hasKey(pdc, KEY_SHULKER)) {
            e.setCancelled(true);
            if (!checkCD(p, KEY_SHULKER, CD_SHORT)) return;
            activateShulker(p);
            startCountdown(p, KEY_SHULKER, CD_SHORT, PAL_SHULKER, "Shulker");

        } else if (hasKey(pdc, KEY_ENDERMAN)) {
            e.setCancelled(true);
            if (!checkCD(p, KEY_ENDERMAN, CD_SHORT)) return;
            activateEnderman(p);
            startCountdown(p, KEY_ENDERMAN, CD_SHORT, PAL_ENDERMAN, "Enderman");

        } else if (hasKey(pdc, KEY_SPIDER)) {
            e.setCancelled(true);
            if (!checkCD(p, KEY_SPIDER, CD_SHORT)) return;
            activateSpider(p);
            startCountdown(p, KEY_SPIDER, CD_SHORT, PAL_SPIDER, "Örümcek");

        } else if (hasKey(pdc, KEY_PHANTOM)) {
            e.setCancelled(true);
            if (!checkCD(p, KEY_PHANTOM, CD_LONG)) return;
            activatePhantom(p);
            startCountdown(p, KEY_PHANTOM, CD_LONG, PAL_PHANTOM, "Phantom");

        } else if (hasKey(pdc, KEY_GOLEM)) {
            e.setCancelled(true);
            if (!checkCD(p, KEY_GOLEM, CD_LONG)) return;
            activateGolem(p);
            startCountdown(p, KEY_GOLEM, CD_LONG, PAL_GOLEM, "Golem");

        } else if (hasKey(pdc, KEY_CREEPER)) {
            e.setCancelled(true);
            if (!checkCD(p, KEY_CREEPER, CD_SHORT)) return;
            activateCreeper(p);
            startCountdown(p, KEY_CREEPER, CD_SHORT, PAL_CREEPER, "Creeper");
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  COOLDOWN & ANİMASYONLU GERİ SAYIM
    // ════════════════════════════════════════════════════════════════════

    private long cdKey(UUID id, String key) {
        return ((long) key.hashCode()) * 0x100000001L + (long) id.hashCode();
    }

    private boolean checkCD(Player p, String key, int seconds) {
        long k    = cdKey(p.getUniqueId(), key);
        long last = cooldowns.getOrDefault(k, 0L);
        long sec  = (System.currentTimeMillis() - last) / 1000L;
        if (sec < seconds) {
            long rem = seconds - sec;
            p.sendMessage("§c⚠ §7Cooldown! §c" + rem + "s §7kaldı.");
            return false;
        }
        return true;
    }

    private void startCountdown(Player p, String key, int total,
                                 int[][] pal, String name) {
        long k = cdKey(p.getUniqueId(), key);
        cooldowns.put(k, System.currentTimeMillis());
        UUID id = p.getUniqueId();

        // Chat mesajı — yalnız kullanan oyuncuya
        p.sendMessage(
            rgbText(pal[0], "✦ " + name) +
            rgbText(pal[1], " Kılıç Özelliğini Kullandın ") +
            rgbText(pal[2], "Altet Onları ;)")
        );

        // Eski task varsa iptal
        BukkitRunnable old = countdownTasks.remove(id);
        if (old != null) { try { old.cancel(); } catch (Exception ignored) {} }

        final int[] rem = {total};
        final int[] pi  = {0};

        BukkitRunnable task = new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) { this.cancel(); return; }
                if (rem[0] <= 0) {
                    p.sendActionBar(
                        rgbText(pal[0], "✦ ") +
                        rgbText(pal[1], name + " Kılıcı ") +
                        rgbText(pal[2], "HAZIR!")
                    );
                    countdownTasks.remove(id);
                    this.cancel();
                    return;
                }
                // RGB renk döngüsü — her tick ilerler
                int[] c1 = pal[pi[0] % pal.length];
                int[] c2 = pal[(pi[0] + 1) % pal.length];
                String bar = buildBar(rem[0], total, c1);
                p.sendActionBar(
                    rgbText(c1, "⏳ " + name + " ") + bar +
                    rgbText(c2, " " + rem[0] + "s")
                );
                pi[0]++;
                rem[0]--;
            }
        };
        countdownTasks.put(id, task);
        task.runTaskTimer(plugin, 0L, 20L);
    }

    /** RGB progress bar: █ dolu, ░ boş */
    private String buildBar(int rem, int total, int[] c) {
        int len    = 12;
        int filled = (int) Math.round(len * (double) rem / total);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(i < filled ? rgbText(c, "█") : "§8░");
        }
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════════
    //  KILIÇ YETENEKLERİ
    // ════════════════════════════════════════════════════════════════════

    // ── 1. Shulker: Levitation 3s ────────────────────────────────────────
    private void activateShulker(Player caster) {
        List<Player> targets = nearby(caster, 8.0);
        if (targets.isEmpty()) { noTarget(caster); return; }

        caster.getWorld().spawnParticle(Particle.PORTAL, caster.getLocation().add(0, 1, 0),
                80, 0.6, 0.6, 0.6, 0.1);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1.2f, 0.7f);

        for (Player t : targets) {
            // Amplifier 1 = Levitation II → yavaş yükseliş
            t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 1, false, true, true));
            t.getWorld().spawnParticle(Particle.WITCH, t.getLocation().add(0, 1, 0),
                    35, 0.3, 0.5, 0.3, 0.0);
            t.getWorld().playSound(t.getLocation(), Sound.ENTITY_SHULKER_HURT, 1.0f, 1.0f);
        }
    }

    // ── 2. Enderman: Örümcek ağındaki oyuncuyu 2.5 blok ileri at ─────────
    private void activateEnderman(Player caster) {
        List<Player> targets = nearby(caster, 8.0);
        if (targets.isEmpty()) { noTarget(caster); return; }

        caster.getWorld().spawnParticle(Particle.PORTAL, caster.getLocation().add(0, 1, 0),
                60, 0.5, 1.0, 0.5, 0.2);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);

        for (Player t : targets) {
            Block feet  = t.getLocation().getBlock();
            Block below = t.getLocation().clone().subtract(0, 1, 0).getBlock();
            if (feet.getType() != Material.COBWEB && below.getType() != Material.COBWEB) continue;

            Location safe = safeForward(t.getLocation(), 2.5);
            t.teleport(safe);
            t.getWorld().spawnParticle(Particle.PORTAL, t.getLocation().add(0, 1, 0),
                    40, 0.3, 0.5, 0.3, 0.1);
            t.getWorld().playSound(t.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        }
    }

    /** Duvar kontrolü ile güvenli ileri konum */
    private Location safeForward(Location from, double maxDist) {
        Vector dir = from.getDirection().clone().setY(0);
        if (dir.lengthSquared() == 0) dir = new Vector(1, 0, 0);
        dir.normalize();
        Location cur  = from.clone();
        double   step = 0.25;
        for (double d = 0; d < maxDist; d += step) {
            Location nxt = cur.clone().add(dir.clone().multiply(step));
            Block b1 = nxt.getBlock();
            Block b2 = nxt.clone().add(0, 1, 0).getBlock();
            if ((!b1.getType().isAir() && b1.getType() != Material.COBWEB) ||
                (!b2.getType().isAir() && b2.getType() != Material.COBWEB)) break;
            cur = nxt;
        }
        cur.setYaw(from.getYaw());
        cur.setPitch(from.getPitch());
        return cur;
    }

    // ── 3. Örümcek: Hitbox'a 3.5s örümcek ağı ───────────────────────────
    private void activateSpider(Player caster) {
        List<Player> targets = nearby(caster, 8.0);
        if (targets.isEmpty()) { noTarget(caster); return; }

        caster.getWorld().spawnParticle(Particle.LAVA, caster.getLocation().add(0, 1, 0),
                30, 0.3, 0.3, 0.3, 0.0);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1.2f, 0.6f);

        for (Player t : targets) {
            Block b = t.getLocation().getBlock();
            if (!b.getType().isAir()) continue; // Blok/duvar zarar görmez

            b.setType(Material.COBWEB);
            b.getWorld().spawnParticle(Particle.LAVA,
                    b.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.0);
            b.getWorld().playSound(b.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1.0f, 1.2f);

            // 3.5s = 70 tick sonra kaldır
            new BukkitRunnable() {
                @Override public void run() {
                    if (b.getType() == Material.COBWEB) {
                        b.setType(Material.AIR);
                        b.getWorld().spawnParticle(Particle.LAVA,
                                b.getLocation().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0.0);
                        b.getWorld().playSound(b.getLocation(), Sound.ENTITY_SPIDER_DEATH, 0.8f, 1.0f);
                    }
                }
            }.runTaskLater(plugin, 70L);
        }
    }

    // ── 4. Phantom: 2 oyuncunun elytra'sını 3s söker ─────────────────────
    private void activatePhantom(Player caster) {
        List<Player> targets = nearby(caster, 10.0);
        if (targets.isEmpty()) { noTarget(caster); return; }

        caster.getWorld().spawnParticle(Particle.CLOUD, caster.getLocation().add(0, 1, 0),
                60, 1.0, 1.0, 1.0, 0.2);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_PHANTOM_DEATH, 1.0f, 0.7f);

        int count = 0;
        for (Player t : targets) {
            if (count >= 2) break;
            count++;

            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, false, true, true));

            ItemStack chest = t.getInventory().getChestplate();
            if (chest != null && chest.getType() == Material.ELYTRA) {
                storedElytra.put(t.getUniqueId(), chest.clone());
                t.getInventory().setChestplate(null);
                t.sendMessage(rgbText(PAL_PHANTOM[0], "⚡ ") +
                              rgbText(PAL_PHANTOM[1], "Elytran Phantom Kılıcı tarafından 3 saniyeliğine söküldü!"));
                t.getWorld().spawnParticle(Particle.CLOUD, t.getLocation().add(0, 1, 0),
                        50, 0.5, 0.5, 0.5, 0.1);
                t.getWorld().playSound(t.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.2f, 0.5f);

                final Player ft = t;
                new BukkitRunnable() {
                    @Override public void run() {
                        if (!ft.isOnline()) return;
                        ItemStack stored = storedElytra.remove(ft.getUniqueId());
                        if (stored == null) return;
                        ItemStack cur = ft.getInventory().getChestplate();
                        if (cur == null || cur.getType() == Material.AIR) {
                            ft.getInventory().setChestplate(stored);
                        } else {
                            ft.getInventory().addItem(stored).values()
                              .forEach(drop -> ft.getWorld().dropItemNaturally(ft.getLocation(), drop));
                        }
                        ft.sendMessage(rgbText(PAL_PHANTOM[2], "✔ Elytran geri döndü!"));
                    }
                }.runTaskLater(plugin, 60L); // 3s
            } else {
                t.getWorld().spawnParticle(Particle.CLOUD, t.getLocation().add(0, 1, 0),
                        20, 0.3, 0.3, 0.3, 0.05);
                t.getWorld().playSound(t.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.8f, 0.8f);
            }
        }
    }

    // ── 5. Golem: Resistance I (3s) + Slowness IV (1.8s) ─────────────────
    private void activateGolem(Player caster) {
        List<Player> targets = nearby(caster, 7.0);
        if (targets.isEmpty()) { noTarget(caster); return; }

        caster.getWorld().spawnParticle(Particle.BLOCK, caster.getLocation().add(0, 1, 0),
                80, 0.5, 0.5, 0.5, 0.1, Material.IRON_BLOCK.createBlockData());
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.2f, 0.8f);

        for (Player t : targets) {
            t.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0, false, true, true)); // 3s
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,   36, 3, false, true, true)); // 1.8s
            t.getWorld().spawnParticle(Particle.BLOCK, t.getLocation().add(0, 1, 0),
                    40, 0.3, 0.5, 0.3, 0.0, Material.IRON_BLOCK.createBlockData());
            t.getWorld().playSound(t.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 1.0f, 1.0f);
        }
    }

    // ── 6. Creeper: TNT patlama efekti + knockback (can gitmez) ──────────
    private void activateCreeper(Player caster) {
        Location loc   = caster.getLocation().add(0, 0.5, 0);
        World    world = loc.getWorld();

        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);
        world.playSound(loc, Sound.ENTITY_CREEPER_PRIMED,  1.0f, 0.8f);

        world.spawnParticle(Particle.EXPLOSION,   loc, 4,  0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.LARGE_SMOKE, loc, 50, 1.5, 1.5, 1.5, 0.05);
        world.spawnParticle(Particle.FLAME,       loc, 60, 1.0, 1.0, 1.0, 0.10);
        world.spawnParticle(Particle.LAVA,        loc, 30, 1.0, 0.5, 1.0, 0.05);

        for (Player t : nearby(caster, 6.0)) {
            Vector dir = t.getLocation().toVector().subtract(caster.getLocation().toVector());
            if (dir.lengthSquared() < 0.001)
                dir = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5);
            dir.normalize().multiply(2.5).setY(0.45);
            t.setVelocity(dir);
            t.getWorld().spawnParticle(Particle.EXPLOSION, t.getLocation().add(0, 0.5, 0),
                    2, 0.2, 0.2, 0.2, 0.0);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  KILIÇ OLUŞTURMA
    // ════════════════════════════════════════════════════════════════════

    private ItemStack buildSword(String nbtKey, String displayName, List<String> lore) {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta  meta  = sword.getItemMeta();
        meta.setDisplayName(displayName);
        meta.setLore(lore);
        meta.addEnchant(Enchantment.SHARPNESS,  5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING,    1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, nbtKey), PersistentDataType.BYTE, (byte) 1
        );
        sword.setItemMeta(meta);
        return sword;
    }

    public ItemStack createShulkerSword() {
        return buildSword(KEY_SHULKER,
            rgbText(PAL_SHULKER[0], "✦ ") + rgbText(PAL_SHULKER[1], "Shulker Kılıcı"),
            Arrays.asList(
                "§7━━━━━━━━━━━━━━━━━━━━━━",
                rgbText(PAL_SHULKER[1], "⚡ Sağ Tık") + " §7→ Yakın oyuncuları",
                "   " + rgbText(PAL_SHULKER[0], "3 saniye") + " §7havaya kaldır!",
                "§7━━━━━━━━━━━━━━━━━━━━━━",
                "§8Cooldown: §7" + CD_SHORT + "s"
            ));
    }

    public ItemStack createEndermanSword() {
        return buildSword(KEY_ENDERMAN,
            rgbText(PAL_ENDERMAN[2], "✦ ") + rgbText(PAL_ENDERMAN[2], "Enderman Kılıcı"),
            Arrays.asList(
                "§7━━━━━━━━━━━━━━━━━━━━━━",
                rgbText(PAL_ENDERMAN[2], "⚡ Sağ Tık") + " §7→ Örümcek ağındaki",
                "   oyuncuları " + rgbText(PAL_ENDERMAN[2], "2.5 blok") + " §7ileri at",
                "   §8(Yalnız örümcek ağında çalışır)",
                "§7━━━━━━━━━━━━━━━━━━━━━━",
                "§8Cooldown: §7" + CD_SHORT + "s"
            ));
    }

    public ItemStack createSpiderSword() {
        return buildSword(KEY_SPIDER,
            rgbText(PAL_SPIDER[0], "✦ ") + rgbText(PAL_SPIDER[1], "Örümcek Kılıcı"),
            Arrays.asList(
                "§7━━━━━━━━━━━━━━━━━━━━━━",
                rgbText(PAL_SPIDER[1], "⚡ Sağ Tık") + " §7→ Hitbox'a örümcek ağı!",
                "   " + rgbText(PAL_SPIDER[0], "3.5 saniye") + " §7sonra silinir",
                "   §8(Blok & harita zarar görmez)",
                "§7━━━━━━━━━━━━━━━━━━━━━━",
                "§8Cooldown: §7" + CD_SHORT + "s"
            ));
    }

    public ItemStack createPhantomSword() {
        return buildSword(KEY_PHANTOM,
            rgbText(PAL_PHANTOM[0], "✦ ") + rgbText(PAL_PHANTOM[1], "Phantom Kılıcı"),
            Arrays.asList(
                "§7━━━━━━━━━━━━━━━━━━━━━━",
                rgbText(PAL_PHANTOM[1], "⚡ Sağ Tık") + " §7→ 2 oyuncunun elytra'sını",
                "   " + rgbText(PAL_PHANTOM[0], "3 saniye") + " §7için söker!",
                "§7━━━━━━━━━━━━━━━━━━━━━━",
                "§8Cooldown: §7" + CD_LONG + "s"
            ));
    }

    public ItemStack createGolemSword() {
        return buildSword(KEY_GOLEM,
            rgbText(PAL_GOLEM[1], "✦ ") + rgbText(PAL_GOLEM[2], "Golem Kılıcı"),
            Arrays.asList(
                "§7━━━━━━━━━━━━━━━━━━━━━━",
                rgbText(PAL_GOLEM[2], "⚡ Sağ Tık") + " §7→ Yakın oyunculara",
                "   " + rgbText(PAL_GOLEM[1], "Direnç I") + " §7(3s) + " +
                        rgbText(PAL_GOLEM[1], "Yavaşlık IV") + " §7(1.8s)",
                "§7━━━━━━━━━━━━━━━━━━━━━━",
                "§8Cooldown: §7" + CD_LONG + "s"
            ));
    }

    public ItemStack createCreeperSword() {
        return buildSword(KEY_CREEPER,
            rgbText(PAL_CREEPER[0], "✦ ") + rgbText(PAL_CREEPER[1], "Creeper Kılıcı"),
            Arrays.asList(
                "§7━━━━━━━━━━━━━━━━━━━━━━",
                rgbText(PAL_CREEPER[1], "⚡ Sağ Tık") + " §7→ Ayakta TNT patlaması!",
                "   " + rgbText(PAL_CREEPER[0], "Knockback") + " §72-3 blok",
                "   §8(Can gitmez, yalnız itme)",
                "§7━━━━━━━━━━━━━━━━━━━━━━",
                "§8Cooldown: §7" + CD_SHORT + "s"
            ));
    }

    // ════════════════════════════════════════════════════════════════════
    //  YARDIMCI METOTLAR
    // ════════════════════════════════════════════════════════════════════

    private List<Player> nearby(Player caster, double r) {
        List<Player> list = new ArrayList<>();
        for (Entity e : caster.getNearbyEntities(r, r, r)) {
            if (e instanceof Player np && !np.equals(caster)) list.add(np);
        }
        return list;
    }

    private boolean hasKey(PersistentDataContainer pdc, String key) {
        return pdc.has(new NamespacedKey(plugin, key), PersistentDataType.BYTE);
    }

    private void noTarget(Player p) {
        p.sendMessage("§8[§7!§8] §7Yakınında hedef bulunamadı.");
    }

    /** §x formatında RGB renk kodu (1.16+) */
    private String rgbText(int[] rgb, String text) {
        return String.format("§x§%c§%c§%c§%c§%c§%c%s",
            hexChar(rgb[0] >> 4), hexChar(rgb[0] & 0xF),
            hexChar(rgb[1] >> 4), hexChar(rgb[1] & 0xF),
            hexChar(rgb[2] >> 4), hexChar(rgb[2] & 0xF),
            text);
    }

    private char hexChar(int v) { return "0123456789abcdef".charAt(v & 0xF); }
}
