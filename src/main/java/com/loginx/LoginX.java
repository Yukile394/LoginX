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
import org.bukkit.event.player.PlayerJoinEvent;
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
    private int flopTick = 0;
    private final List<ArmorStand> spawnedStands = new ArrayList<>();
    private final HashMap<Location, String> activeHolograms = new HashMap<>();
    private final HashMap<UUID, Integer> playtime = new HashMap<>();
    private final HashMap<UUID, Integer> blocksBroken = new HashMap<>();
    private final HashMap<UUID, Integer> kills = new HashMap<>();
    private long nextResetTime;
    
    // Scoreboard Yöneticisi
    private AppScoreboard appScoreboard;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        loadStats();
        
        // Scoreboard'u başlat
        appScoreboard = new AppScoreboard(this);
        for (Player p : Bukkit.getOnlinePlayers()) {
            appScoreboard.updateScoreboard(p);
        }
        
        // Animasyon ve Veri Yenileme Görevi
        new BukkitRunnable() {
            @Override
            public void run() {
                flopTick++;
                updateAnimatedMenus(); // İade loresı ve kılıçAction bar
                appScoreboard.animateTitleAndFooter(); // Scoreboard
            }
        }.runTaskTimer(this, 0, 2); 

        getLogger().info("LoginX - Fear Craft Sistemleri Yuklendi!");
    }

    @Override
    public void onDisable() {
        saveStats();
        clearHolos();
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
        else if (cmd.getName().equalsIgnoreCase("iade")) openIadeMain(p);
        return true;
    }

    // ===================== KILIÇ SİSTEMİ =====================
    private void openSwordMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 36, color("&#3b3b3bÖzel Kılıç Menüsü"));
        // 9'lu simetri için ortalanmış dizilim (10-16 ve 22-24 slotları)
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
        
        // Cooldown ve Yetenek Dağılımı
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
            // Cooldown Subtitle Mesajı (Küçük/Soluk)
            p.sendTitle("", color("&bTekrar kullanmadan önce &f" + left + " &bsaniye bekleyin!"), 0, 20, 0);
            return;
        }

        List<Player> nearby = new ArrayList<>();
        for (Entity entity : p.getNearbyEntities(6, 6, 6)) {
            if (entity instanceof Player && entity != p) {
                nearby.add((Player) entity);
            }
        }

        if (nearby.isEmpty()) {
            p.sendMessage(color("&e[!] Yakınlarda bir oyuncu yok, yetenek kullanılamadı."));
            return;
        }

        // Delay'i önlemek için hemen cooldown'a al
        cooldowns.get(p.getUniqueId()).put(type, System.currentTimeMillis() + (sec * 1000L));
        
        // RGB Flop Action Bar
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(getFlopText("» " + type.toUpperCase() + " KILIÇ YETENEĞİ AKTİF!", sec)));

        switch (type) {
            case "creeper" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation(), 1);
                for (Player t : nearby) {
                    t.damage(6);
                    Vector kb = t.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.5).setY(0.5);
                    t.setVelocity(kb);
                    t.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, t.getLocation(), 1);
                }
            }
            case "orumcek" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_SPIDER_DEATH, 1f, 1f);
                for (Player t : nearby) {
                    Location targetFeetLoc = t.getLocation();
                    // Hitbox'un tam ortasına (y+1.0) bir ağ yerleştir
                    Location centerWebLoc = targetFeetLoc.getBlock().getLocation().add(0.5, 1.0, 0.5);
                    t.teleport(t.getLocation().getBlock().getLocation().add(0.5, 0, 0.5)); // center in block at feet
                    
                    if (centerWebLoc.getBlock().getType().isAir()) {
                        centerWebLoc.getBlock().setType(Material.COBWEB);
                        t.spawnParticle(Particle.BLOCK, centerWebLoc, 20, 0.5, 0.5, 0.5, Material.COBWEB.createBlockData());
                        
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (centerWebLoc.getBlock().getType() == Material.COBWEB) {
                                    centerWebLoc.getBlock().setType(Material.AIR);
                                }
                            }
                        }.runTaskLater(this, 70L); // 3.5 saniye
                    }
                }
            }
            case "phantom" -> { // Düzeltilen Phantom mantığı
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1f, 1f);
                for (Player t : nearby) {
                    elytraBanned.put(t.getUniqueId(), System.currentTimeMillis() + 3000L);
                    if (t.isGliding()) t.setGliding(false);
                    // Düşürme vektörü
                    t.setVelocity(new Vector(0, -1.0, 0));
                    t.sendTitle("", color("&b[!] Elitran 3 saniye bozuldu!"), 5, 20, 5);
                    t.getWorld().spawnParticle(Particle.LARGE_SMOKE, t.getLocation(), 40, 0.5, 1, 0.5, 0.1);
                    t.playSound(t.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1f, 1f);
                }
            }
            case "ejderha" -> { // Düzeltilen Ejderha dalgası
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                new BukkitRunnable() {
                    int i = 0;
                    @Override
                    public void run() {
                        if (i++ > 15) { // 15 nokta
                            this.cancel();
                            return;
                        }
                        Location start = p.getEyeLocation();
                        Vector dir = start.getDirection().normalize().multiply(i * 0.8);
                        Location particleLoc = start.add(dir);
                        particleLoc.getWorld().spawnParticle(Particle.DRAGON_BREATH, particleLoc, 10, 0.2, 0.2, 0.2, 0.05);

                        for (Entity e : particleLoc.getWorld().getNearbyEntities(particleLoc, 1.5, 1.5, 1.5)) {
                            if (e instanceof LivingEntity le && e != p) {
                                le.damage(4); // damage
                                le.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1)); // wither
                                le.setFireTicks(60); // fire
                            }
                        }
                    }
                }.runTaskTimer(this, 0, 1);
            }
            case "shulker" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1f, 1f);
                for (Player t : nearby) {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 1));
                    t.getWorld().spawnParticle(Particle.WITCH, t.getLocation(), 20, 0.5, 0.5, 0.5, 0.01);
                }
            }
            case "yildirim" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1f);
                for (Player t : nearby) { t.getWorld().strikeLightning(t.getLocation()); t.damage(5); }
            }
            case "gardiyan" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1f);
                for (Player t : nearby) { t.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 2)); t.spawnParticle(Particle.ELDER_GUARDIAN, t.getLocation(), 1); }
            }
            case "wither" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 1f);
                for (Player t : nearby) {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1));
                    t.getWorld().spawnParticle(Particle.WITCH, t.getLocation(), 20, 0.5, 0.5, 0.5, 0.01);
                }
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

    private void openIadeMain(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, getAnimatedFearCraftHeader(flopTick));
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
                getFlopText("&lFear Craft Ölüm Verisi", flopTick + (i * 2)),
                color("&8&m-------------------------"),
                color("&fVeri ID: &7#" + dr.id),
                color("&fÖldüren Kisi: &#ff3300" + dr.killerName),
                color("&e» &7Bu veri, sunucudaki kadim"),
                color("&e» &7izleme sistemleri tarafından"),
                color("&e» &7oyuncunun öldüğü an"),
                color("&e» &7tam slot dizilimiyle kaydedildi."),
                color("&fKonum: &a" + dr.loc.getBlockX() + "," + dr.loc.getBlockY() + "," + dr.loc.getBlockZ()),
                color("&8&m-------------------------"),
                getFlopText("&aSol Tık &e» Görüntüle / İade Et", flopTick + i)
            ));
            chest.setItemMeta(m);
            inv.setItem(i, chest);
        }

        // Simetrik Alt Butonlar
        inv.setItem(48, createBtn(Material.PAPER, "&#00ccffSistem Bilgisi", "&7Ölenlerin eşyalarını tam slotuna verir."));
        inv.setItem(49, createBtn(Material.BARRIER, "&#ff0000Kapat", "&7Menüyü kapatır."));
        inv.setItem(50, createBtn(Material.RECOVERY_COMPASS, "&#00ffccDestek", "&7Bir sorun varsa yetkiliye bildirin."));
    }

    private String getAnimatedFearCraftHeader(int tick) {
        String base = "Fear Craft - İade";
        return gradientFlop(base, tick, true); // True means blue-white
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        appScoreboard.updateScoreboard(e.getPlayer());
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        Player p = (Player) e.getWhoClicked();

        if (title.contains("İade Listesi")) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.CHEST) {
                String id = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).split("#")[1];
                DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
                if (dr != null) {
                    Player target = Bukkit.getPlayer(dr.playerUUID);
                    if (target != null && target.isOnline()) {
                        target.getInventory().setContents(dr.items);
                        p.sendMessage(color("&a[FearCraft] &fEşyalar @" + dr.playerName + " kişisine iade edildi."));
                    } else p.sendMessage(color("&c[!] Oyuncu şu an oyunda değil!"));
                }
            } else if (e.getRawSlot() == 49) { e.getWhoClicked().closeInventory(); } // Kapat Butonu
        } else if (title.contains("Özel Kılıç Menüsü")) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.NETHERITE_SWORD) e.getWhoClicked().getInventory().addItem(e.getCurrentItem().clone());
        }
    }

    // ===================== SCOREBOARD (LP'li & Mavi) =====================
    private AppScoreboard appScoreboard;

    // --- Hologram & Sayaç Sistemleri (Aynı Şekilde Korunmuştur) ---
    @EventHandler public void onKillStats(PlayerDeathEvent e) { if (e.getEntity().getKiller() != null) kills.merge(e.getEntity().getKiller().getUniqueId(), 1, Integer::sum); }
    @EventHandler public void onBreakStats(BlockBreakEvent e) { blocksBroken.merge(e.getPlayer().getUniqueId(), 1, Integer::sum); }
    private void startPlaytime() { new BukkitRunnable() { public void run() { Bukkit.getOnlinePlayers().forEach(p -> playtime.merge(p.getUniqueId(), 1, Integer::sum)); }}.runTaskTimer(this, 1200, 1200); }
    private void startHoloUpdater() { new BukkitRunnable() { public void run() { if (!activeHolograms.isEmpty()) { clearHolos(); activeHolograms.forEach(LoginX.this::buildHolo); }}}.runTaskTimer(this, 100, 100); }
    private void startWeeklyReset() { new BukkitRunnable() { public void run() { if (System.currentTimeMillis() > nextResetTime) { kills.clear(); playtime.clear(); blocksBroken.clear(); nextResetTime = System.currentTimeMillis() + 604800000; }}}.runTaskTimer(this, 1200, 1200); }
    private void spawnHolo(Location loc, String type) { activeHolograms.put(loc, type); buildHolo(loc, type); }
    private void buildHolo(Location base, String type) {
        // Hologram mantığı (Öncekiyle aynı)
        List<String> lines = top(type.equals("KILLS") ? kills : type.equals("PLAYTIME") ? playtime : blocksBroken, type);
        double y = 0;
        for (String l : lines) {
            ArmorStand a = (ArmorStand) base.getWorld().spawnEntity(base.clone().subtract(0, y, 0), EntityType.ARMOR_STAND);
            a.setInvisible(true); a.setMarker(true); a.setCustomNameVisible(true); a.setCustomName(color(l)); a.setGravity(false);
            spawnedStands.add(a); y += 0.3;
        }
    }
    private void clearHolos() { spawnedStands.forEach(Entity::remove); spawnedStands.clear(); }
    private List<String> top(HashMap<UUID, Integer> map, String s) { return map.entrySet().stream().sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()).li
