package com.loginx;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
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
    private final HashMap<UUID, HashMap<String, Long>> cooldowns = new HashMap<>();
    private final HashMap<UUID, Long> elytraBanned = new HashMap<>();
    private long nextResetTime;

    // IADE SISTEMI VERILERI
    private final List<DeathRecord> deathRecords = new ArrayList<>();

    private static class DeathRecord {
        String id;
        UUID playerUUID;
        String playerName;
        String killerName;
        Location loc;
        long time;
        ItemStack[] contents;

        public DeathRecord(Player p, String killer, ItemStack[] items) {
            this.id = Integer.toHexString(new Random().nextInt(0xFFFF)).toUpperCase();
            this.playerUUID = p.getUniqueId();
            this.playerName = p.getName();
            this.killerName = killer;
            this.loc = p.getLocation();
            this.time = System.currentTimeMillis();
            this.contents = items;
        }
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        loadStats();
        startPlaytime();
        startHoloUpdater();
        startWeeklyReset();
        getLogger().info("LoginX Gelişmiş Sistemler Aktif!");
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
        if (!p.hasPermission("loginx.admin")) return true;

        switch (cmd.getName().toLowerCase()) {
            case "kilicvermenu" -> openSwordMenu(p);
            case "iade" -> openIadeMain(p);
            case "skorsil" -> { clearHolos(); activeHolograms.clear(); }
        }
        return true;
    }

    // ===================== IADE SISTEMI MANTIGI =====================
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        String killer = (p.getKiller() != null) ? p.getKiller().getName() : "Doğa/Bilinmeyen";
        
        // Envanterin tam kopyasını al (Slot koruması için)
        ItemStack[] items = p.getInventory().getContents();
        ItemStack[] backup = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) backup[i] = items[i].clone();
        }

        deathRecords.add(0, new DeathRecord(p, killer, backup));
        if (deathRecords.size() > 45) deathRecords.remove(deathRecords.size() - 1);
    }

    private void openIadeMain(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&#00ffcc&lİADE LİSTESİ"));
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm");

        for (int i = 0; i < deathRecords.size() && i < 45; i++) {
            DeathRecord dr = deathRecords.get(i);
            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta m = item.getItemMeta();
            m.setDisplayName(color("&#ffcc00" + dr.playerName + " &7#" + dr.id));
            m.setLore(Arrays.asList(
                color("&8&m-------------------------"),
                color("&fÖldüren: &#ff3300" + dr.killerName),
                color("&fZaman: &#00ccff" + sdf.format(new Date(dr.time))),
                color("&fKonum: &7" + dr.loc.getBlockX() + "," + dr.loc.getBlockY() + "," + dr.loc.getBlockZ()),
                color("&8&m-------------------------"),
                color("&#55ff55» Görüntülemek için tıkla!")
            ));
            item.setItemMeta(m);
            inv.setItem(i, item);
        }

        // Alt 3 Seçenek
        inv.setItem(48, createBtn(Material.BARRIER, "&#ff0000Kayıtları Temizle", "&7Tüm listeyi siler."));
        inv.setItem(49, createBtn(Material.RECOVERY_COMPASS, "&#00ffccSayfayı Yenile", "&7Listeyi günceller."));
        inv.setItem(50, createBtn(Material.BOOK, "&#ffff00Sistem Bilgisi", "&7Ölenlerin eşyalarını tam slotuna verir."));

        p.openInventory(inv);
    }

    private void openSpecificIade(Player p, String id) {
        DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
        if (dr == null) return;

        Inventory inv = Bukkit.createInventory(null, 54, color("&#ff3300İade: #" + dr.id));
        
        // Eşyaları orijinal yerlerine diz
        for (int i = 0; i < dr.contents.length && i < 45; i++) {
            if (dr.contents[i] != null) inv.setItem(i, dr.contents[i]);
        }

        // Alt Simetrik 3 Seçenek
        inv.setItem(46, createBtn(Material.ENDER_PEARL, "&#00ffccBölgeye Işınlan", "&7Ölüm konumuna gidersin."));
        inv.setItem(49, createBtn(Material.NETHER_STAR, "&#55ff55EŞYALARI İADE ET", "&7Eşyaları tam slotlarına yükler."));
        inv.setItem(52, createBtn(Material.LAVA_BUCKET, "&#ff3300Kaydı Sil", "&7Bu veriyi kalıcı olarak siler."));

        p.openInventory(inv);
    }

    // ===================== KILIÇ SİSTEMİ =====================
    private void openSwordMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 36, color("&#3b3b3bÖzel Kılıçlar"));
        String[] types = {"shulker", "enderman", "orumcek", "phantom", "golem", "creeper", "gardiyan", "wither"};
        for (int i = 0; i < types.length; i++) inv.setItem(10 + i + (i > 5 ? 2 : 0), getSpecialSword(types[i]));
        p.openInventory(inv);
    }

    private ItemStack getSpecialSword(String type) {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Sağ tık özel yetenek!"));

        switch (type) {
            case "gardiyan":
                meta.setDisplayName(color("&#00e6b8&lG&#14e9c1&la&#29ecd9&lr&#3df0f2&ld&#52f3fa&li&#66f6ff&ly&#7af9ff&la&#8ffcff&ln &fKılıcı"));
                lore.add(color("&fÖzellik: &7Madenci Yorgunluğu (3sn) + Hayalet Efekti."));
                lore.add(color("&8Bekleme: 60s"));
                break;
            case "wither":
                meta.setDisplayName(color("&#404040&lW&#4d4d4d&li&#5a5a5a&lt&#676767&lh&#747474&le&#818181&lr &fKılıcı"));
                lore.add(color("&fÖzellik: &7Wither Efekti (4sn)."));
                lore.add(color("&8Bekleme: 131s"));
                break;
            default: // Diğer kılıçlar buraya (önceki kodunla aynı isimler)
                meta.setDisplayName(color("&e" + type.toUpperCase() + " Kılıcı"));
                break;
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
        if (name.contains("gardiyan")) handleSkill(p, "gardiyan", 60);
        else if (name.contains("wither")) handleSkill(p, "wither", 131);
        // ... Diğer kılıçların handleSkill çağrıları buraya eklenebilir
    }

    private void handleSkill(Player p, String type, int sec) {
        cooldowns.putIfAbsent(p.getUniqueId(), new HashMap<>());
        long expire = cooldowns.get(p.getUniqueId()).getOrDefault(type, 0L);
        if (System.currentTimeMillis() < expire) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(color("&cBekle: " + (expire - System.currentTimeMillis()) / 1000 + "s")));
            return;
        }
        cooldowns.get(p.getUniqueId()).put(type, System.currentTimeMillis() + (sec * 1000L));
        
        List<Player> nearby = p.getNearbyEntities(5, 5, 5).stream().filter(en -> en instanceof Player && en != p).map(en -> (Player)en).collect(Collectors.toList());

        if (type.equals("gardiyan")) {
            p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1f);
            nearby.forEach(t -> {
                t.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 2));
                t.spawnParticle(Particle.ELDER_GUARDIAN, t.getLocation(), 1);
            });
        } else if (type.equals("wither")) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 1f);
            nearby.forEach(t -> t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1)));
        }
    }

    // ===================== ENVARTER TIKLAMA =====================
    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        Player p = (Player) e.getWhoClicked();

        if (title.contains("İADE LİSTESİ")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 48) { deathRecords.clear(); p.closeInventory(); p.sendMessage(color("&cTüm kayıtlar silindi!")); }
            else if (e.getRawSlot() == 49) openIadeMain(p);
            else if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.CHEST) {
                String id = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).split("#")[1];
                openSpecificIade(p, id);
            }
        } 
        else if (title.startsWith("İade: #")) {
            e.setCancelled(true);
            String id = title.split("#")[1];
            DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
            if (dr == null) return;

            if (e.getRawSlot() == 46) p.teleport(dr.loc);
            else if (e.getRawSlot() == 49) {
                Player target = Bukkit.getPlayer(dr.playerUUID);
                if (target != null && target.isOnline()) {
                    target.getInventory().setContents(dr.contents);
                    p.sendMessage(color("&aEşyalar @" + target.getName() + " kullanıcısına tam slotlarıyla iade edildi!"));
                } else p.sendMessage(color("&cOyuncu aktif değil!"));
            }
            else if (e.getRawSlot() == 52) { deathRecords.remove(dr); openIadeMain(p); }
        }
    }

    // ===================== YARDIMCI METOTLAR =====================
    private ItemStack createBtn(Material m, String name, String lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(Collections.singletonList(color(lore)));
        it.setItemMeta(meta);
        return it;
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
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    // --- İstatistik ve Hologram Sistemleri (Gerekli Metotlar) ---
    private void startPlaytime() { new BukkitRunnable() { public void run() { Bukkit.getOnlinePlayers().forEach(p -> playtime.merge(p.getUniqueId(), 1, Integer::sum)); }}.runTaskTimer(this, 1200, 1200); }
    private void startHoloUpdater() { new BukkitRunnable() { public void run() { if (!activeHolograms.isEmpty()) { clearHolos(); activeHolograms.forEach(LoginX.this::buildHolo); }}}.runTaskTimer(this, 100, 100); }
    private void startWeeklyReset() { new BukkitRunnable() { public void run() { if (System.currentTimeMillis() > nextResetTime) { kills.clear(); playtime.clear(); blocksBroken.clear(); nextResetTime = System.currentTimeMillis() + 604800000; }}}.runTaskTimer(this, 1200, 1200); }
    private void buildHolo(Location loc, String type) { /* Mevcut hologram kodun buraya gelebilir */ }
    private void clearHolos() { spawnedStands.forEach(Entity::remove); spawnedStands.clear(); }
    private void loadStats() { nextResetTime = getConfig().getLong("reset", System.currentTimeMillis() + 604800000); }
    private void saveStats() { getConfig().set("reset", nextResetTime); saveConfig(); }
                      }
