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
    
    // İADE SİSTEMİ VERİLERİ
    private final List<DeathRecord> deathRecords = new ArrayList<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        loadStats();

        startPlaytime();
        startHoloUpdater();
        startWeeklyReset();
        startMenuAnimator(); // Yeni menü animatörü

        getLogger().info("LoginX: Fear Craft Sistemleri Aktif!");
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

    // ===================== KOMUTLAR =====================
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (!p.hasPermission("loginx.admin")) {
            p.sendMessage(color("&cBunun için yetkin yok."));
            return true;
        }

        switch (cmd.getName().toLowerCase()) {
            case "kilicvermenu" -> openSwordMenu(p);
            case "iade" -> openIadeMenu(p);
            // Hologramlar (Öncekiyle aynı)
            case "skorkill" -> spawnHolo(p.getTargetBlock(null, 10).getLocation().add(0.5, 2.5, 0.5), "KILLS");
            case "skorzaman" -> spawnHolo(p.getTargetBlock(null, 10).getLocation().add(0.5, 2.5, 0.5), "PLAYTIME");
            case "skorblok" -> spawnHolo(p.getTargetBlock(null, 10).getLocation().add(0.5, 2.5, 0.5), "BLOCKS");
            case "skorsil" -> { clearHolos(); activeHolograms.clear(); }
        }
        return true;
    }

    // ===================== İADE SİSTEMİ (FEAR CRAFT) =====================
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Player killer = p.getKiller();
        String kname = (killer != null) ? killer.getName() : "Doga";
        
        ItemStack[] original = p.getInventory().getContents();
        ItemStack[] cloned = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) if (original[i] != null) cloned[i] = original[i].clone();
        
        deathRecords.add(0, new DeathRecord(p.getUniqueId(), p.getName(), kname, p.getLocation(), cloned));
        if (deathRecords.size() > 45) deathRecords.remove(deathRecords.size() - 1);
    }

    private void openIadeMenu(Player p) {
        // Başlangıçta flop renklerini oluştur
        Inventory inv = Bukkit.createInventory(null, 54, getFearCraftTitle(0)); 
        updateIadeList(inv);
        p.openInventory(inv);
    }

    private void updateIadeList(Inventory inv) {
        inv.clear();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm");
        for (int i = 0; i < deathRecords.size() && i < 45; i++) {
            DeathRecord dr = deathRecords.get(i);
            ItemStack chest = new ItemStack(Material.CHEST);
            ItemMeta m = chest.getItemMeta();
            m.setDisplayName(color("&#ffcc00" + dr.playerName));
            
            List<String> lore = new ArrayList<>();
            lore.add(color("&fKayıt ID: &7#" + dr.id));
            lore.add(color("&fÖldüren Kisi: &c" + dr.killerName));
            lore.add(color("&fNe Zaman Öldü: &e" + sdf.format(new Date(dr.time))));
            lore.add(color("&fKonum: &a" + dr.loc.getBlockX() + ", " + dr.loc.getBlockY() + ", " + dr.loc.getBlockZ()));
            lore.add(color("&8----------------------"));
            lore.add(color("&#00ff00Eşyaları görüntülemek için tıkla!"));
            m.setLore(lore);
            chest.setItemMeta(m);
            inv.setItem(i, chest);
        }
        // Alt 3 seçenek (Simetrik)
        inv.setItem(48, createBtn(Material.BARRIER, "&#ff0000Listeyi Temizle", "&7Tüm ölüm kayıtlarını siler."));
        inv.setItem(49, createBtn(Material.COMPASS, "&#00ffccYenile", "&7Sayfayı günceller."));
        inv.setItem(50, createBtn(Material.FEATHER, "&#ffff00Bilgi", "&7Son 45 ölüm kaydı tutulur."));
    }

    private void openSpecificRecord(Player p, String id) {
        DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
        if (dr == null) return;

        Inventory inv = Bukkit.createInventory(null, 54, color("&#ff3300Ölüm Kaydı: #" + dr.id));
        for (int i = 0; i < dr.items.length && i < 41; i++) if (dr.items[i] != null) inv.setItem(i, dr.items[i]);

        inv.setItem(46, createBtn(Material.ENDER_PEARL, "&#00ffccBölgeye Işınlan", "&7Ölüm konumuna gidersin."));
        inv.setItem(49, createBtn(Material.DIAMOND, "&#00ff00Eşyaları İade Et", "&7Aktifse tam slotlarına geri yükler."));
        inv.setItem(52, createBtn(Material.BARRIER, "&#ff0000Kaydı Sil", "&7Bu kaydı siler."));
        p.openInventory(inv);
    }

    // ===================== KILIÇLAR VE YETENEKLER =====================
    private void openSwordMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 36, color("&#3b3b3bÖzel Kılıç Menüsü"));
        // Kılıçları diz
        inv.setItem(10, getSpecialSword("shulker"));
        inv.setItem(11, getSpecialSword("enderman"));
        inv.setItem(12, getSpecialSword("orumcek"));
        inv.setItem(13, getSpecialSword("phantom"));
        inv.setItem(14, getSpecialSword("golem"));
        inv.setItem(15, getSpecialSword("creeper"));
        inv.setItem(16, getSpecialSword("ejderha"));
        inv.setItem(22, getSpecialSword("yildirim")); // YENİ
        p.openInventory(inv);
    }

    private ItemStack getSpecialSword(String type) {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add(color("&fÖzellik: &7Sağ tık özel skill"));
        
        // Cooldownları buraya da ekleyelim loreda gözüksün
        int cd = 30;
        switch (type) {
            case "orumcek": cd = 60; break;
            case "phantom": cd = 65; break;
            case "creeper": cd = 25; break;
        }
        lore.add(color("&8Bekleme: " + cd + " saniye"));

        switch (type) {
            case "shulker" -> meta.setDisplayName(color("&#e8473e&lShulker Kılıcı"));
            case "enderman" -> meta.setDisplayName(color("&#8000ff&lEnderman Kılıcı"));
            case "orumcek" -> meta.setDisplayName(color("&#bd0000&lÖrümcek Kılıcı"));
            case "phantom" -> meta.setDisplayName(color("&#0080ff&lPhantom Kılıcı"));
            case "golem" -> meta.setDisplayName(color("&#d9d9d9&lGolem Kılıcı"));
            case "creeper" -> meta.setDisplayName(color("&#00e600&lCreeper Kılıcı"));
            case "ejderha" -> meta.setDisplayName(color("&#ffaa00&lEjderha Kılıcı"));
            case "yildirim" -> meta.setDisplayName(color("&#ffee00&lYıldırım Kılıcı")); // YENİ
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onSwordUse(PlayerInteractEvent e) {
        if (!e.getAction().name().contains("RIGHT")) return;
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() != Material.NETHERITE_SWORD || !item.hasItemMeta()) return;

        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();
        
        // Cooldown ve Yetenek Dağılımı
        if (name.contains("shulker")) handleSkill(p, "shulker", 30);
        else if (name.contains("enderman")) handleSkill(p, "enderman", 30);
        else if (name.contains("örümcek")) handleSkill(p, "orumcek", 60); // 60s
        else if (name.contains("phantom")) handleSkill(p, "phantom", 65); // 65s
        else if (name.contains("golem")) handleSkill(p, "golem", 30);
        else if (name.contains("creeper")) handleSkill(p, "creeper", 25); // 25s
        else if (name.contains("ejderha")) handleSkill(p, "ejderha", 30);
        else if (name.contains("yıldırım")) handleSkill(p, "yildirim", 30); // 30s
    }

    private void handleSkill(Player p, String type, int seconds) {
        cooldowns.putIfAbsent(p.getUniqueId(), new HashMap<>());
        long time = cooldowns.get(p.getUniqueId()).getOrDefault(type, 0L);
        
        if (System.currentTimeMillis() < time) {
            long left = (time - System.currentTimeMillis()) / 1000;
            // RGB Flop Action Bar
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(getFlopText("&lBekleme Süresi: " + left + "s",seconds - left)));
            return;
        }
        cooldowns.get(p.getUniqueId()).put(type, System.currentTimeMillis() + (seconds * 1000L));
        executeSkill(p, type);
    }

    private void executeSkill(Player p, String type) {
        List<Player> targets = p.getNearbyEntities(5, 5, 5).stream()
                .filter(ent -> ent instanceof Player && ent != p)
                .map(ent -> (Player) ent).toList();

        switch (type) {
            case "shulker":
                targets.forEach(t -> {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 0));
                    t.getWorld().spawnParticle(Particle.WITCH, t.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.01);
                });
                break;
            case "orumcek":
                targets.forEach(t -> {
                    // Tam oturtmak için merkeze ışınla
                    Location loc = t.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
                    t.teleport(loc);
                    loc.getBlock().setType(Material.COBWEB);
                    loc.add(0, 1, 0).getBlock().setType(Material.COBWEB);
                    t.getWorld().spawnParticle(Particle.BLOCK, loc, 30, 0.5, 0.5, 0.5, Material.COBWEB.createBlockData());
                    new BukkitRunnable() { public void run() { 
                        loc.getBlock().setType(Material.AIR);
                        loc.add(0, 1, 0).getBlock().setType(Material.AIR);
                    }}.runTaskLater(this, 70L); // ~3.5s
                });
                break;
            case "creeper": 
                p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation(), 1);
                targets.forEach(t -> {
                    t.damage(6);
                    t.setVelocity(t.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.5).setY(0.5));
                }); 
                break;
            case "phantom": targets.stream().limit(2).forEach(t -> {
                // 3 saniye yasak (Cooldown handlerda yönetilir)
                elytraBanned.put(t.getUniqueId(), System.currentTimeMillis() + 3000L);
                if (t.isGliding()) t.setGliding(false);
                t.setVelocity(new Vector(0, -1.5, 0));
                t.getWorld().spawnParticle(Particle.LARGE_SMOKE, t.getLocation(), 20, 0.2, 0.2, 0.2, 0.01);
                t.sendMessage(color("&cElitran bozuldu ve 3sn kilitlendi!"));
            }); break;
            case "ejderha": new BukkitRunnable() {
                Location loc = p.getEyeLocation();
                Vector dir = loc.getDirection().multiply(0.5);
                int i = 0;
                public void run() {
                    if (i++ > 20) this.cancel();
                    loc.add(dir);
                    loc.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 5, 0.1, 0.1, 0.1, 0.01);
                    loc.getWorld().getNearbyEntities(loc, 1, 1, 1).forEach(e -> {
                        if (e instanceof LivingEntity le && e != p) { le.damage(4); le.setFireTicks(40); }
                    });
                }
            }.runTaskTimer(this, 0, 1); break;
            case "yildirim": // YENİ
                targets.forEach(t -> {
                    t.getWorld().strikeLightningEffect(t.getLocation());
                    t.damage(5);
                    p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1f);
                });
                break;
            case "enderman": targets.forEach(t -> t.teleport(t.getLocation().add(p.getLocation().getDirection().multiply(3)))); break;
            case "golem": targets.forEach(t -> {
                t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 3));
                t.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0));
                t.getWorld().spawnParticle(Particle.BLOCK, t.getLocation(), 20, 0.3, 0.3, 0.3, Material.IRON_BLOCK.createBlockData());
            }); break;
        }
    }

    // Phantom Koruması
    @EventHandler
    public void onGlide(EntityToggleGlideEvent e) {
        if (e.getEntity() instanceof Player p) {
            if (elytraBanned.containsKey(p.getUniqueId()) && elytraBanned.get(p.getUniqueId()) > System.currentTimeMillis()) {
                e.setCancelled(true);
            }
        }
    }

    // ===================== MENÜ KONTROLLLERİ (ALINMAMASI İÇİN) =====================
    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        Player p = (Player) e.getWhoClicked();

        // Kılıç Menüsü
        if (title.contains("Özel Kılıç Menüsü")) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.NETHERITE_SWORD) {
                p.getInventory().addItem(e.getCurrentItem().clone());
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }
        } 
        
        // İade Ana Menü
        else if (title.contains("Fear Craft")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 48) { deathRecords.clear(); openIadeMenu(p); } // Listeyi Temizle
            else if (e.getRawSlot() == 49) { updateIadeList(e.getInventory()); } // Yenile
            else if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.CHEST) {
                String loreLine = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getLore().get(0));
                if (loreLine.contains("Kayıt ID: #")) {
                    String id = loreLine.split("#")[1].trim();
                    openSpecificRecord(p, id);
                }
            }
        } 
        
        // Eşya İade Menüsü
        else if (title.startsWith("Ölüm Kaydı: #")) {
            e.setCancelled(true);
            String id = title.split("#")[1].trim();
            DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
            if (dr == null) return;

            if (e.getRawSlot() == 46) p.teleport(dr.loc); // Işınlan
            else if (e.getRawSlot() == 49) { // İade Et
                Player target = Bukkit.getPlayer(dr.playerUUID);
                if (target != null && target.isOnline()) {
                    target.getInventory().setContents(dr.items);
                    p.sendMessage(color("&aEşyalar @" + target.getName() + " kullanıcısına tam slotlarıyla iade edildi!"));
                    p.closeInventory();
                } else p.sendMessage(color("&cOyuncu aktif değil!"));
            } 
            else if (e.getRawSlot() == 52) { // Sil
                deathRecords.remove(dr);
                openIadeMenu(p);
            }
        }
    }

    // ===================== RGB FLOP VE ANİMASYON METOTLARI =====================
    private int flopIndex = 0;
    private void startMenuAnimator() {
        new BukkitRunnable() {
            public void run() {
                flopIndex++;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (ChatColor.stripColor(p.getOpenInventory().getTitle()).contains("Fear Craft")) {
                        p.openInventory().getTopInventory().getViewers().get(0).openInventory(Bukkit.createInventory(null, 54, getFearCraftTitle(flopIndex)));
                        // Inventorytitle update lag yapabilir, Paperda daha kolay ama Spigotda bu şekilde yenilenir.
                        // updateIadeList(p.getOpenInventory().getTopInventory());
                    }
                }
            }
        }.runTaskTimer(this, 0, 5); // Her 5 tickte bir (Hızlı flop)
    }

    private String getFearCraftTitle(int tick) {
        String base = "Fear Craft - İade";
        return gradientFlop(base, tick);
    }

    private String gradientFlop(String text, int tick) {
        // Basit flop mantığı, her tick renkleri kaydırır
        String[] hexes = {"&#ff0000", "&#ffee00", "&#00ff00", "&#00ffcc", "&#0080ff", "&#ff00ff"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            int hIdx = (i + tick) % hexes.length;
            sb.append(hexes[hIdx]).append(text.charAt(i));
        }
        return color(sb.toString());
    }

    private String getFlopText(String t, long seed) {
        return gradientFlop(ChatColor.stripColor(t), flopIndex + (int)seed);
    }

    // ===================== DİĞER METOTLAR (SİLİNMEYENLER) =====================
    private ItemStack createBtn(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(Collections.singletonList(color(lore)));
        item.setItemMeta(meta);
        return item;
    }

    public String color(String text) {
        Pattern p = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher m = p.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex = m.group(1);
            StringBuilder rep = new StringBuilder("§x");
            for (char c : hex.toCharArray()) rep.append("§").append(c);
            m.appendReplacement(sb, rep.toString());
        }
        m.appendTail
