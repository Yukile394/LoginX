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

        getLogger().info("LoginX: İade Sistemi, Gardiyan ve Wither Kılıçları Aktif!");
    }

    @Override
    public void onDisable() {
        saveStats();
        clearHolos();
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
            case "shulkerkilicver" -> giveSword(p, "shulker");
            case "endermankilicver" -> giveSword(p, "enderman");
            case "orumcekkilicver" -> giveSword(p, "orumcek");
            case "phantomkilicver" -> giveSword(p, "phantom");
            case "golemkilicver" -> giveSword(p, "golem");
            case "creeperkilicver" -> giveSword(p, "creeper");
            case "ejderhakilicver" -> giveSword(p, "ejderha");
            case "gardiyankilicver" -> giveSword(p, "gardiyan");
            case "witherkilicver" -> giveSword(p, "wither");
            case "iade" -> openIadeMenu(p);
            case "skorkill" -> spawnHolo(p.getTargetBlock(null, 10).getLocation().add(0.5, 2.5, 0.5), "KILLS");
            case "skorzaman" -> spawnHolo(p.getTargetBlock(null, 10).getLocation().add(0.5, 2.5, 0.5), "PLAYTIME");
            case "skorblok" -> spawnHolo(p.getTargetBlock(null, 10).getLocation().add(0.5, 2.5, 0.5), "BLOCKS");
            case "skorsil" -> { clearHolos(); activeHolograms.clear(); }
        }
        return true;
    }

    private void giveSword(Player p, String type) {
        p.getInventory().addItem(getSpecialSword(type));
        p.spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation(), 30, 0.5, 1, 0.5, 0.1);
        p.sendMessage(color("&a" + type.toUpperCase() + " Kılıcı verildi!"));
    }

    // ===================== İADE SİSTEMİ (ÖLÜM KAYDI) =====================
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

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Player killer = p.getKiller();
        String kname = (killer != null) ? killer.getName() : "Bilinmeyen/Doğa";
        
        ItemStack[] original = p.getInventory().getContents();
        ItemStack[] cloned = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) {
            if (original[i] != null) cloned[i] = original[i].clone();
        }
        
        deathRecords.add(0, new DeathRecord(p.getUniqueId(), p.getName(), kname, p.getLocation(), cloned));
        
        // Sadece son 45 ölümü tutarak sunucuyu yormamış oluyoruz
        if (deathRecords.size() > 45) {
            deathRecords.remove(deathRecords.size() - 1);
        }
    }

    private void openIadeMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&#00ffccİade Menüsü - Ölümler"));
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        for (int i = 0; i < deathRecords.size(); i++) {
            DeathRecord dr = deathRecords.get(i);
            ItemStack chest = new ItemStack(Material.CHEST);
            ItemMeta meta = chest.getItemMeta();
            meta.setDisplayName(color("&#ffcc00" + dr.playerName + " &7- &cÖlüm Kaydı"));
            
            List<String> lore = new ArrayList<>();
            lore.add(color("&8----------------------"));
            lore.add(color("&fKayıt ID: &7#" + dr.id));
            lore.add(color("&fÖldüren: &c" + dr.killerName));
            lore.add(color("&fTarih: &e" + sdf.format(new Date(dr.time))));
            lore.add(color("&fKonum: &a" + dr.loc.getBlockX() + ", " + dr.loc.getBlockY() + ", " + dr.loc.getBlockZ()));
            lore.add(color("&8----------------------"));
            lore.add(color("&aSol Tık &7ile bu kaydı ve eşyaları görüntüle."));
            meta.setLore(lore);
            chest.setItemMeta(meta);
            inv.setItem(i, chest);
        }

        inv.setItem(45, createBtn(Material.PAPER, "&#00ccffBilgi", "&7Son 45 ölüm gösterilmektedir."));
        inv.setItem(49, createBtn(Material.EMERALD_BLOCK, "&#00ff00Sayfayı Yenile", "&7Güncel ölümleri getirir."));
        inv.setItem(53, createBtn(Material.LAVA_BUCKET, "&#ff0000Tüm Kayıtları Sil", "&7Sadece adminler temizleyebilir."));
        
        p.openInventory(inv);
    }

    private void openSpecificRecord(Player p, String id) {
        DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
        if (dr == null) {
            p.sendMessage(color("&cBu kayıt bulunamadı veya silinmiş!"));
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, color("&#ff3300Ölüm Kaydı: #" + dr.id));
        
        // 0'dan 40'a kadar oyuncunun tam slot dizilimi
        for (int i = 0; i < dr.items.length && i < 41; i++) {
            if (dr.items[i] != null) inv.setItem(i, dr.items[i]);
        }

        // Alt taraftaki 3 simetrik buton
        inv.setItem(45, createBtn(Material.ENDER_PEARL, "&#00ffccBölgeye Işınlan", "&7Oyuncunun öldüğü yere gidersin."));
        inv.setItem(49, createBtn(Material.DIAMOND, "&#00ff00Eşyaları İade Et", "&7Aktifse tam olarak eski slotlarına geri verir."));
        inv.setItem(53, createBtn(Material.BARRIER, "&#ff0000Kaydı Sil", "&7Bu kaydı listeden kaldırır."));
        
        p.openInventory(inv);
    }

    private ItemStack createBtn(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(Collections.singletonList(color(lore)));
        item.setItemMeta(meta);
        return item;
    }

    // ===================== KILIÇLAR =====================
    private void openSwordMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 36, color("&#3b3b3bÖzel Kılıç Menüsü"));
        inv.setItem(10, getSpecialSword("shulker"));
        inv.setItem(11, getSpecialSword("enderman"));
        inv.setItem(12, getSpecialSword("orumcek"));
        inv.setItem(13, getSpecialSword("phantom"));
        inv.setItem(14, getSpecialSword("golem"));
        inv.setItem(15, getSpecialSword("creeper"));
        inv.setItem(16, getSpecialSword("ejderha"));
        inv.setItem(21, getSpecialSword("gardiyan")); // YENİ
        inv.setItem(23, getSpecialSword("wither"));   // YENİ
        p.openInventory(inv);
    }

    private ItemStack getSpecialSword(String type) {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Özel Yetenek: Sağ Tık"));

        switch (type) {
            case "shulker": meta.setDisplayName(color("&#e8473eShulker Kılıcı")); break;
            case "enderman": meta.setDisplayName(color("&#8000ffEnderman Kılıcı")); break;
            case "orumcek": meta.setDisplayName(color("&#bd0000Örümcek Kılıcı")); break;
            case "phantom": meta.setDisplayName(color("&#0080ffPhantom Kılıcı")); break;
            case "golem": meta.setDisplayName(color("&#d9d9d9Golem Kılıcı")); break;
            case "creeper": meta.setDisplayName(color("&#00e600Creeper Kılıcı")); break;
            case "ejderha": meta.setDisplayName(color("&#ffaa00Ejderha Kılıcı")); break;
            case "gardiyan": // YENİ
                meta.setDisplayName(color("&#00e6b8Gardiyan Kılıcı"));
                lore.add(color("&#00e6b8Özellik: &fHedeflere 3sn Madenci Yorgunluğu (Efektli)."));
                lore.add(color("&#00e6b8Bekleme: &f60 saniye"));
                break;
            case "wither": // YENİ
                meta.setDisplayName(color("&#404040Wither Kılıcı"));
                lore.add(color("&#404040Özellik: &fHedefleri 4 saniyeliğine çürütür."));
                lore.add(color("&#404040Bekleme: &f131 saniye"));
                break;
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
        
        if (name.contains("shulker")) handleSkill(p, "shulker", 30);
        else if (name.contains("enderman")) handleSkill(p, "enderman", 30);
        else if (name.contains("örümcek")) handleSkill(p, "orumcek", 60);
        else if (name.contains("phantom")) handleSkill(p, "phantom", 65);
        else if (name.contains("golem")) handleSkill(p, "golem", 30);
        else if (name.contains("creeper")) handleSkill(p, "creeper", 25);
        else if (name.contains("ejderha")) handleSkill(p, "ejderha", 40);
        else if (name.contains("gardiyan")) handleSkill(p, "gardiyan", 60);
        else if (name.contains("wither")) handleSkill(p, "wither", 131);
    }

    private void handleSkill(Player p, String type, int seconds) {
        cooldowns.putIfAbsent(p.getUniqueId(), new HashMap<>());
        long time = cooldowns.get(p.getUniqueId()).getOrDefault(type, 0L);
        if (System.currentTimeMillis() < time) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(color("&cBekleme süresi: " + (time - System.currentTimeMillis()) / 1000 + "s")));
            return;
        }
        cooldowns.get(p.getUniqueId()).put(type, System.currentTimeMillis() + (seconds * 1000L));
        executeSkill(p, type);
    }

    private void executeSkill(Player p, String type) {
        List<Player> targets = p.getNearbyEntities(5, 5, 5).stream()
                .filter(ent -> ent instanceof Player && ent != p)
                .map(ent -> (Player) ent).collect(Collectors.toList());

        switch (type) {
            case "gardiyan":
                p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1f);
                for (Player t : targets) {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 3 * 20, 2));
                    // Madenci yorgunu hayaleti ekranda çıkar (Spigot/Bukkit 1.21 karşılığı)
                    t.spawnParticle(Particle.ELDER_GUARDIAN, t.getLocation(), 1);
                }
                break;
            case "wither":
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 1f);
                for (Player t : targets) {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 4 * 20, 1));
                }
                break;
            // DİĞER ESKİ KILIÇLAR:
            case "shulker": targets.forEach(t -> t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 0))); break;
            case "orumcek": targets.forEach(t -> {
                t.teleport(t.getLocation().getBlock().getLocation().add(0.5, 0, 0.5));
                t.getLocation().getBlock().setType(Material.COBWEB);
                t.getLocation().add(0, 1, 0).getBlock().setType(Material.COBWEB);
                new BukkitRunnable() { public void run() { 
                    t.getLocation().getBlock().setType(Material.AIR);
                    t.getLocation().add(0, 1, 0).getBlock().setType(Material.AIR);
                }}.runTaskLater(this, 70L);
            }); break;
            case "creeper": 
                p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation(), 1);
                targets.forEach(t -> { t.damage(6); t.setVelocity(t.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.5).setY(0.5)); }); 
                break;
            case "phantom": targets.stream().limit(2).forEach(t -> {
                elytraBanned.put(t.getUniqueId(), System.currentTimeMillis() + 3000L);
                if (t.isGliding()) t.setGliding(false);
                t.setVelocity(new Vector(0, -1.5, 0));
                t.sendMessage(color("&cElitran bozuldu!"));
            }); break;
            case "enderman": targets.forEach(t -> t.teleport(t.getLocation().add(p.getLocation().getDirection().multiply(3)))); break;
            case "golem": targets.forEach(t -> { t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 3)); t.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0)); }); break;
            case "ejderha": new BukkitRunnable() {
                Location loc = p.getEyeLocation(); Vector dir = loc.getDirection().multiply(0.5); int i = 0;
                public void run() {
                    if (i++ > 20) this.cancel();
                    loc.add(dir); loc.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 5, 0.1, 0.1, 0.1, 0.01);
                    loc.getWorld().getNearbyEntities(loc, 1, 1, 1).forEach(e -> { if (e instanceof LivingEntity le && e != p) { le.damage(4); le.setFireTicks(40); }});
                }
            }.runTaskTimer(this, 0, 1); break;
        }
    }

    // ===================== MENÜ TIKLAMALARI VE YARDIMCI METOTLAR =====================
    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        Player p = (Player) e.getWhoClicked();

        // Kılıç Menüsü
        if (title.contains("Özel Kılıç Menüsü")) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null) p.getInventory().addItem(e.getCurrentItem());
        } 
        
        // İade Ana Menü
        else if (title.contains("İade Menüsü")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 49) { openIadeMenu(p); }
            else if (e.getRawSlot() == 53) { 
                deathRecords.clear(); 
                p.sendMessage(color("&cTüm geçmiş ölüm kayıtları silindi."));
                openIadeMenu(p); 
            }
            else if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.CHEST) {
                // Tıklanan sandıktaki ID'yi bul
                for (String line : e.getCurrentItem().getItemMeta().getLore()) {
                    String st = ChatColor.stripColor(line);
                    if (st.contains("Kayıt ID: #")) {
                        String id = st.split("#")[1].trim();
                        openSpecificRecord(p, id);
                        break;
                    }
                }
            }
        } 
        
        // Eşya Gösterim ve İade Etme Menüsü
        else if (title.startsWith("Ölüm Kaydı: #")) {
            e.setCancelled(true);
            String id = title.split("#")[1].trim();
            DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
            if (dr == null) return;

            if (e.getRawSlot() == 45) { // Işınlan
                p.teleport(dr.loc);
                p.sendMessage(color("&aÖlüm noktasına ışınlandın."));
            } 
            else if (e.getRawSlot() == 49) { // İade Et
                Player target = Bukkit.getPlayer(dr.playerUUID);
                if (target != null && target.isOnline()) {
                    // Tam olarak eski slotlarına eşyaları koyar
                    target.getInventory().setContents(dr.items);
                    p.sendMessage(color("&aEşyalar, " + target.getName() + " adlı oyuncunun envanterindeki orijinal slotlarına başarıyla iade edildi!"));
                    target.sendMessage(color("&#00ff00Yetkili bir admin tarafından öldüğünde düşürdüğün eşyalar birebir eski slotlarına geri yüklendi!"));
                    p.closeInventory();
                } else {
                    p.sendMessage(color("&cOyuncu şu an sunucuda aktif değil, iade yapılamaz!"));
                }
            } 
            else if (e.getRawSlot() == 53) { // Sil
                deathRecords.remove(dr);
                p.sendMessage(color("&cKayıt kalıcı olarak silindi."));
                openIadeMenu(p);
            }
        }
    }

    @EventHandler
    public void onGlide(EntityToggleGlideEvent e) {
        if (e.getEntity() instanceof Player p && elytraBanned.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) {
            e.setCancelled(true);
        }
    }

    // Renk Ayarı
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
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    // Hologram & Sayaç Sistemleri (Aynı Şekilde Korunmuştur)
    @EventHandler public void onKillStats(PlayerDeathEvent e) { if (e.getEntity().getKiller() != null) kills.merge(e.getEntity().getKiller().getUniqueId(), 1, Integer::sum); }
    @EventHandler public void onBreakStats(BlockBreakEvent e) { blocksBroken.merge(e.getPlayer().getUniqueId(), 1, Integer::sum); }
    private void startPlaytime() { new BukkitRunnabl
