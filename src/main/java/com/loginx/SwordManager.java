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

    // Cooldown haritaları (UUID -> son kullanım zamanı ms)
    private final Map<UUID, Long> shulkerCd  = new HashMap<>();
    private final Map<UUID, Long> endermanCd = new HashMap<>();
    private final Map<UUID, Long> spiderCd   = new HashMap<>();
    private final Map<UUID, Long> phantomCd  = new HashMap<>();
    private final Map<UUID, Long> golemCd    = new HashMap<>();
    private final Map<UUID, Long> creeperCd  = new HashMap<>();

    // Kılıç kimlikleri (lore'da gizli tag yerine lore satırı kontrolü)
    private static final String TAG_SHULKER  = "§0§kSHULKER_SWORD";
    private static final String TAG_ENDERMAN = "§0§kENDERMAN_SWORD";
    private static final String TAG_SPIDER   = "§0§kSPIDER_SWORD";
    private static final String TAG_PHANTOM  = "§0§kPHANTOM_SWORD";
    private static final String TAG_GOLEM    = "§0§kGOLEM_SWORD";
    private static final String TAG_CREEPER  = "§0§kCREEPER_SWORD";

    // Cooldown süreleri (ms)
    private static final long CD_SHULKER  = 30_000L;
    private static final long CD_ENDERMAN = 30_000L;
    private static final long CD_SPIDER   = 30_000L;
    private static final long CD_PHANTOM  = 230_000L;
    private static final long CD_GOLEM    = 230_000L;
    private static final long CD_CREEPER  = 30_000L;

    // Geri sayım görev haritası
    private final Map<UUID, BukkitRunnable> countdownTasks = new HashMap<>();

    public SwordManager(Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ─────────────────────────────────────────────
    //   KILIC OLUŞTURMA METOTları
    // ─────────────────────────────────────────────

    public ItemStack makeShulkerSword() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName("§5§lShulker Kılıcı");
        m.setLore(Arrays.asList(
            "§7Yakındaki oyuncuları §53 §7saniye uçurur.",
            "§7Yavaşlatma efekti ile birlikte gelir.",
            "§8Bekleme: §c30 saniye",
            TAG_SHULKER
        ));
        applyNetheriteEnchants(m);
        m.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(m);
        return item;
    }

    public ItemStack makeEndermanSword() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName("§1§lEnderman Kılıcı");
        m.setLore(Arrays.asList(
            "§7Yakındaki oyuncuları §12.5 blok §7ileriye fırlatır.",
            "§7Duvarların içine giremezler — sadece örümcek ağına!",
            "§8Bekleme: §c30 saniye",
            TAG_ENDERMAN
        ));
        applyNetheriteEnchants(m);
        m.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(m);
        return item;
    }

    public ItemStack makeSpiderSword() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName("§2§lÖrümcek Kılıcı");
        m.setLore(Arrays.asList(
            "§7Yakındaki oyuncuların tam hitbox ortasına §2örümcek ağı §7yerleştirir.",
            "§73.5 saniye sonra otomatik silinir. Duvar/harita dışına çıkamazlar.",
            "§8Bekleme: §c30 saniye",
            TAG_SPIDER
        ));
        applyNetheriteEnchants(m);
        m.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(m);
        return item;
    }

    public ItemStack makePhantomSword() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName("§3§lPhantom Kılıcı");
        m.setLore(Arrays.asList(
            "§7En yakın §32 oyuncunun §7Elytra kullanımını §33 saniye §7engeller.",
            "§7Gökyüzü senin — onların değil!",
            "§8Bekleme: §c230 saniye",
            TAG_PHANTOM
        ));
        applyNetheriteEnchants(m);
        m.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(m);
        return item;
    }

    public ItemStack makeGolemSword() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName("§f§lGolem Kılıcı");
        m.setLore(Arrays.asList(
            "§7Yakındaki oyuncuların §fDirencini §73 saniye bozar.",
            "§7Yavaşlama 4 efektini §f1.8 saniye §7boyunca verir.",
            "§8Bekleme: §c230 saniye",
            TAG_GOLEM
        ));
        applyNetheriteEnchants(m);
        m.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(m);
        return item;
    }

    public ItemStack makeCreeperSword() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName("§a§lCreeper Kılıcı");
        m.setLore(Arrays.asList(
            "§7Ayağının altında §aTNT patlaması §7gerçekleşir!",
            "§7Can gitmez ama yakındaki düşmanlar savrulur.",
            "§72-3 blok tepme ile şaşırtıcı etki!",
            "§8Bekleme: §c30 saniye",
            TAG_CREEPER
        ));
        applyNetheriteEnchants(m);
        m.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(m);
        return item;
    }

    private void applyNetheriteEnchants(ItemMeta m) {
        m.addEnchant(Enchantment.SHARPNESS,      5, true);
        m.addEnchant(Enchantment.UNBREAKING,     3, true);
        m.addEnchant(Enchantment.MENDING,        1, true);
        m.addEnchant(Enchantment.SWEEPING_EDGE,  3, true);
        m.addEnchant(Enchantment.FIRE_ASPECT,    2, true);
        m.addEnchant(Enchantment.LOOTING,        3, true);
    }

    // ─────────────────────────────────────────────
    //   KILIC TİPİ TANIMA
    // ─────────────────────────────────────────────

    private boolean hasTag(ItemStack item, String tag) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta m = item.getItemMeta();
        if (!m.hasLore()) return false;
        for (String line : m.getLore()) if (line.equals(tag)) return true;
        return false;
    }

    // ─────────────────────────────────────────────
    //   SAĞ TIKLAMA OLAYI
    // ─────────────────────────────────────────────

    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
            && e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;

        if      (hasTag(item, TAG_SHULKER))  { e.setCancelled(true); useShulker(p);  }
        else if (hasTag(item, TAG_ENDERMAN)) { e.setCancelled(true); useEnderman(p); }
        else if (hasTag(item, TAG_SPIDER))   { e.setCancelled(true); useSpider(p);   }
        else if (hasTag(item, TAG_PHANTOM))  { e.setCancelled(true); usePhantom(p);  }
        else if (hasTag(item, TAG_GOLEM))    { e.setCancelled(true); useGolem(p);    }
        else if (hasTag(item, TAG_CREEPER))  { e.setCancelled(true); useCreeper(p);  }
    }

    // ─────────────────────────────────────────────
    //   COOLDOWN KONTROLÜ VE GERİ SAYIM
    // ─────────────────────────────────────────────

    private boolean checkCooldown(Player p, Map<UUID, Long> cdMap, long cdMs, int[] swordColor) {
        long now = System.currentTimeMillis();
        long last = cdMap.getOrDefault(p.getUniqueId(), 0L);
        long diff = now - last;
        if (diff < cdMs) {
            long remaining = (cdMs - diff) / 1000 + 1;
            p.sendMessage(colorize("§cBeklemelisin! §f" + remaining + " §csaniye kaldı.", swordColor));
            return false;
        }
        cdMap.put(p.getUniqueId(), now);
        startCountdown(p, cdMs / 1000, swordColor);
        return true;
    }

    /** RGB floplu geri sayım ActionBar'da gösterilir */
    private void startCountdown(Player p, long seconds, int[] rgb) {
        UUID uid = p.getUniqueId();

        // Varsa önceki görevi durdur
        if (countdownTasks.containsKey(uid)) {
            countdownTasks.get(uid).cancel();
        }

        BukkitRunnable task = new BukkitRunnable() {
            long remaining = seconds;
            double hue = 0;

            @Override
            public void run() {
                if (!p.isOnline() || remaining <= 0) {
                    p.sendActionBar("§aKılıç özelliği hazır!");
                    countdownTasks.remove(uid);
                    cancel();
                    return;
                }

                // RGB renk geçişi — kılıç rengine doğru bias
                hue = (hue + 5) % 360;
                java.awt.Color base = java.awt.Color.getHSBColor((float)(hue / 360f), 0.9f, 1.0f);
                // Kılıç rengini blendla
                int r = (base.getRed()   + rgb[0]) / 2;
                int g = (base.getGreen() + rgb[1]) / 2;
                int b = (base.getBlue()  + rgb[2]) / 2;

                String hexColor = String.format("#%02X%02X%02X", r, g, b);
                // Bukkit net.md_5.bungee renk kodu kullan
                net.md_5.bungee.api.ChatColor chatColor =
                    net.md_5.bungee.api.ChatColor.of(hexColor);

                // İlerleme çubuğu animasyonu
                int total = (int) seconds;
                int done  = (int)(total - remaining);
                StringBuilder bar = new StringBuilder();
                for (int i = 0; i < 20; i++) {
                    if (i < (done * 20 / total)) bar.append("§c■");
                    else bar.append("§7■");
                }

                p.sendActionBar(chatColor + "⏱ Bekleme: " + remaining + "s  " + bar.toString());
                remaining--;
            }
        };
        task.runTaskTimer(plugin, 0L, 20L);
        countdownTasks.put(uid, task);
    }

    private String colorize(String msg, int[] rgb) {
        net.md_5.bungee.api.ChatColor c =
            net.md_5.bungee.api.ChatColor.of(String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2]));
        return c + msg;
    }

    // ─────────────────────────────────────────────
    //   1. SHULKER KILIÇ
    //   RGB rengi: Mor = {148, 0, 211}
    // ─────────────────────────────────────────────

    private void useShulker(Player p) {
        int[] color = {148, 0, 211};
        if (!checkCooldown(p, shulkerCd, CD_SHULKER, color)) return;

        p.sendMessage("§5§lShulker Kılıcı §7► §dShulker Kılıç Özelliğini Kullandın Altet Onları ;)");

        List<Player> nearby = getNearbyPlayers(p, 8.0);
        for (Player t : nearby) {
            // Yavaş levitasyon + yukarı fırlatma
            t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 1));
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,   60, 3));

            // Shulker isabet efekti (mor partiküller)
            t.getWorld().spawnParticle(Particle.DRAGON_BREATH,
                t.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.05);
            t.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                t.getLocation().add(0, 1, 0), 30, 0.4, 0.4, 0.4, 0.1);
        }

        // Kullanıcı etrafında mor enerji dalgası
        spawnRingEffect(p.getLocation(), Particle.DRAGON_BREATH, color);
    }

    // ─────────────────────────────────────────────
    //   2. ENDERMAN KILIÇ
    //   RGB: Koyu mavi = {0, 0, 200}
    // ─────────────────────────────────────────────

    private void useEnderman(Player p) {
        int[] color = {0, 0, 200};
        if (!checkCooldown(p, endermanCd, CD_ENDERMAN, color)) return;

        p.sendMessage("§1§lEnderman Kılıcı §7► §9Enderman Kılıç Özelliğini Kullandın Altet Onları ;)");

        List<Player> nearby = getNearbyPlayers(p, 7.0);
        for (Player t : nearby) {
            Vector dir = t.getLocation().getDirection().normalize();
            Vector push = dir.multiply(2.5).setY(0.3);

            Location targetLoc = t.getLocation().add(push);

            // Duvar kontrolü — hedef konum geçerliyse fırlat
            if (!isSolid(targetLoc) || isInCobweb(targetLoc)) {
                t.setVelocity(push);
            } else {
                // Duvarsa en yakın boş yöne fırlat
                Vector safe = getSafeDirection(t.getLocation(), dir, 2.5);
                t.setVelocity(safe);
            }

            t.getWorld().spawnParticle(Particle.PORTAL,
                t.getLocation().add(0, 1, 0), 50, 0.3, 0.5, 0.3, 0.2);
            t.getWorld().spawnParticle(Particle.SPELL_WITCH,
                t.getLocation().add(0, 1, 0), 20, 0.2, 0.2, 0.2, 0.1);
        }

        spawnRingEffect(p.getLocation(), Particle.PORTAL, color);
    }

    private boolean isSolid(Location loc) {
        return loc.getBlock().getType().isSolid();
    }

    private boolean isInCobweb(Location loc) {
        return loc.getBlock().getType() == Material.COBWEB;
    }

    private Vector getSafeDirection(Location origin, Vector preferred, double dist) {
        // Tercih edilen yönü dene; değilse yukarı-ileri
        Vector[] options = {
            preferred.clone().multiply(dist),
            new Vector(preferred.getZ(), 0.5, -preferred.getX()).multiply(dist),
            new Vector(-preferred.getZ(), 0.5, preferred.getX()).multiply(dist),
            new Vector(0, 1, 0).multiply(dist)
        };
        for (Vector v : options) {
            Location test = origin.clone().add(v);
            if (!test.getBlock().getType().isSolid()) return v;
        }
        return new Vector(0, 0.5, 0);
    }

    // ─────────────────────────────────────────────
    //   3. ÖRÜMCEK KILIÇ
    //   RGB: Yeşil = {0, 180, 0}
    // ─────────────────────────────────────────────

    private void useSpider(Player p) {
        int[] color = {0, 180, 0};
        if (!checkCooldown(p, spiderCd, CD_SPIDER, color)) return;

        p.sendMessage("§2§lÖrümcek Kılıcı §7► §aÖrümcek Kılıç Özelliğini Kullandın Altet Onları ;)");

        List<Player> nearby = getNearbyPlayers(p, 8.0);
        for (Player t : nearby) {
            // Hitbox ortası — ayak bloğu ve bir üstü
            Location foot = t.getLocation().getBlock().getLocation();
            Location mid  = foot.clone().add(0.5, 0, 0.5); // Blok merkezine yerleştir

            // Duvar/sınır kontrolü
            if (mid.getBlock().getType().isSolid()) continue;
            if (isOutOfWorld(mid)) continue;

            mid.getBlock().setType(Material.COBWEB);
            Location webLoc = mid.clone();

            // Çevre efekt
            t.getWorld().spawnParticle(Particle.CRIT_MAGIC,
                t.getLocation().add(0, 1, 0), 30, 0.4, 0.4, 0.4, 0.05);
            t.getWorld().spawnParticle(Particle.SLIME,
                t.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);

            // 3.5 saniye sonra sil
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (webLoc.getBlock().getType() == Material.COBWEB) {
                        webLoc.getBlock().setType(Material.AIR);
                        webLoc.getWorld().spawnParticle(Particle.SMOKE_NORMAL,
                            webLoc.clone().add(0.5, 0.5, 0.5), 10, 0.2, 0.2, 0.2, 0.05);
                    }
                }
            }.runTaskLater(plugin, 70L); // 3.5s = 70 tick
        }

        spawnRingEffect(p.getLocation(), Particle.SLIME, color);
    }

    private boolean isOutOfWorld(Location loc) {
        return loc.getBlockY() < loc.getWorld().getMinHeight()
            || loc.getBlockY() > loc.getWorld().getMaxHeight() - 1;
    }

    // ─────────────────────────────────────────────
    //   4. PHANTOM KILIÇ
    //   RGB: Cyan = {0, 200, 200}
    // ─────────────────────────────────────────────

    private void usePhantom(Player p) {
        int[] color = {0, 200, 200};
        if (!checkCooldown(p, phantomCd, CD_PHANTOM, color)) return;

        p.sendMessage("§3§lPhantom Kılıcı §7► §bPhantom Kılıç Özelliğini Kullandın Altet Onları ;)");

        List<Player> nearby = getNearbyPlayers(p, 10.0);
        // Sadece en yakın 2 oyuncu
        nearby.sort(Comparator.comparingDouble(t -> t.getLocation().distance(p.getLocation())));
        int count = 0;
        for (Player t : nearby) {
            if (count >= 2) break;

            // Elytra engellemek için havada ise yere indir + yavaşlatma
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,  60, 2));
            t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,  60, 1));
            t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0));

            // Elytra'yı kapat (uçuş durdurma)
            if (t.isGliding()) t.setGliding(false);

            // 3 saniye boyunca her 10 tickte bir elytra'yı kapat
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (ticks >= 6 || !t.isOnline()) { cancel(); return; }
                    if (t.isGliding()) t.setGliding(false);
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 10L);

            // Phantom efektleri
            t.getWorld().spawnParticle(Particle.SPELL_MOB,
                t.getLocation().add(0, 1.5, 0), 40, 0.5, 0.5, 0.5, 0.05,
                new Particle.DustOptions(Color.fromRGB(0, 200, 200), 1.5f));
            t.getWorld().spawnParticle(Particle.CLOUD,
                t.getLocation().add(0, 2, 0), 20, 0.5, 0.2, 0.5, 0.05);

            count++;
        }

        spawnRingEffect(p.getLocation(), Particle.CLOUD, color);
    }

    // ─────────────────────────────────────────────
    //   5. GOLEM KILIÇ (Gözlem)
    //   RGB: Beyaz = {220, 220, 220}
    // ─────────────────────────────────────────────

    private void useGolem(Player p) {
        int[] color = {220, 220, 220};
        if (!checkCooldown(p, golemCd, CD_GOLEM, color)) return;

        p.sendMessage("§f§lGolem Kılıcı §7► §fGolem Kılıç Özelliğini Kullandın Altet Onları ;)");

        List<Player> nearby = getNearbyPlayers(p, 6.0);
        for (Player t : nearby) {
            // Direnç 1 → 3 saniye (60 tick)
            t.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0));
            // Ama NEGATIF: Yavaşlık 4 → 1.8 saniye (36 tick)
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 36, 3));

            // Efekt
            t.getWorld().spawnParticle(Particle.SPELL_MOB,
                t.getLocation().add(0, 1, 0), 35, 0.4, 0.4, 0.4, 0.03,
                new Particle.DustOptions(Color.WHITE, 1.2f));
            t.getWorld().spawnParticle(Particle.CRIT,
                t.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.1);
        }

        spawnRingEffect(p.getLocation(), Particle.CRIT, color);
    }

    // ─────────────────────────────────────────────
    //   6. CREEPER KILIÇ
    //   RGB: Yeşil = {50, 205, 50}
    // ─────────────────────────────────────────────

    private void useCreeper(Player p) {
        int[] color = {50, 205, 50};
        if (!checkCooldown(p, creeperCd, CD_CREEPER, color)) return;

        p.sendMessage("§a§lCreeper Kılıcı §7► §2Creeper Kılıç Özelliğini Kullandın Altet Onları ;)");

        Location loc = p.getLocation();
        World world = p.getWorld();

        // TNT sesi
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.8f);
        world.playSound(loc, Sound.ENTITY_CREEPER_PRIMED,  1.5f, 1.0f);

        // Patlama partikül efekti (daire şeklinde)
        for (int i = 0; i < 360; i += 20) {
            double rad = Math.toRadians(i);
            double x = Math.cos(rad) * 2.5;
            double z = Math.sin(rad) * 2.5;
            world.spawnParticle(Particle.EXPLOSION_NORMAL,
                loc.clone().add(x, 0.5, z), 3, 0.1, 0.1, 0.1, 0.05);
        }
        world.spawnParticle(Particle.EXPLOSION_LARGE, loc.clone().add(0, 0.5, 0), 5,
            0.5, 0.5, 0.5, 0.05);
        world.spawnParticle(Particle.SMOKE_LARGE, loc.clone().add(0, 1, 0), 20,
            0.8, 0.5, 0.8, 0.05);
        world.spawnParticle(Particle.FLAME, loc.clone().add(0, 0.5, 0), 30,
            0.6, 0.3, 0.6, 0.1);

        // Yakın oyuncuları fırlat (2-3 blok) — can gitmesin
        List<Player> nearby = getNearbyPlayers(p, 5.0);
        for (Player t : nearby) {
            Vector dir = t.getLocation().toVector()
                          .subtract(loc.toVector()).normalize();
            double force = 2.0 + Math.random(); // 2.0 – 3.0 arası
            dir.setY(0.6);
            t.setVelocity(dir.multiply(force));

            // Küçük bireysel patlama efekti
            t.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL,
                t.getLocation().add(0, 0.5, 0), 8, 0.2, 0.2, 0.2, 0.05);
        }

        spawnRingEffect(loc, Particle.FLAME, color);
    }

    // ─────────────────────────────────────────────
    //   YARDIMCI METODLAR
    // ─────────────────────────────────────────────

    private List<Player> getNearbyPlayers(Player src, double radius) {
        List<Player> list = new ArrayList<>();
        for (Entity e : src.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player && !e.getUniqueId().equals(src.getUniqueId())) {
                list.add((Player) e);
            }
        }
        return list;
    }

    /** Kullanıcı etrafında döner halka efekti */
    private void spawnRingEffect(Location center, Particle particle, int[] rgb) {
        new BukkitRunnable() {
            double angle = 0;
            int runs = 0;

            @Override
            public void run() {
                if (runs++ >= 10) { cancel(); return; }
                for (int i = 0; i < 12; i++) {
                    double a = Math.toRadians(angle + i * 30);
                    double x = Math.cos(a) * 1.5;
                    double z = Math.sin(a) * 1.5;
                    Location l = center.clone().add(x, 0.1, z);
                    try {
                        if (particle == Particle.SPELL_MOB) {
                            center.getWorld().spawnParticle(particle, l, 2, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(rgb[0], rgb[1], rgb[2]), 1.0f));
                        } else {
                            center.getWorld().spawnParticle(particle, l, 2, 0, 0, 0, 0.01);
                        }
                    } catch (Exception ignored) {
                        center.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, l, 2, 0, 0, 0, 0);
                    }
                }
                angle += 36;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ─────────────────────────────────────────────
    //   KILIC VER METODLARI (komut için)
    // ─────────────────────────────────────────────

    public void giveToPlayer(Player p, ItemStack sword) {
        // Boş slot bul, yoksa eline ver
        HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(sword);
        if (!leftover.isEmpty()) {
            p.getInventory().setItemInMainHand(sword);
        }
        p.updateInventory();

        // Verme efekti
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
        p.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE,
            p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 1.0);
    }
}
