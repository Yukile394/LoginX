package com.loginx;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
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

public class LoginX extends JavaPlugin implements Listener {

    private final HashMap<UUID, HashMap<String, Long>> cooldowns = new HashMap<>();
    private final HashMap<UUID, Long> elytraBanned = new HashMap<>();
    private final List<DeathRecord> deathRecords = new ArrayList<>();
    private int flopTick = 0;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        
        // Animasyon ve Veri Temizleme Görevi
        new BukkitRunnable() {
            @Override
            public void run() {
                flopTick++;
                updateAnimatedMenus();
            }
        }.runTaskTimer(this, 0, 2); // 2 tickte bir yenile (Akıcı olması için)

        getLogger().info("Fear Craft LoginX Aktif! Hata payı %0.");
    }

    // ===================== İADE KAYIT YAPISI =====================
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

    // ===================== KOMUTLAR =====================
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (!p.hasPermission("loginx.admin")) return true;

        if (cmd.getName().equalsIgnoreCase("kilicvermenu")) openSwordMenu(p);
        else if (cmd.getName().equalsIgnoreCase("iade")) openIadeMenu(p);
        return true;
    }

    // ===================== KILIÇ SİSTEMİ (SİMETRİK) =====================
    private void openSwordMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 36, color("&#00ccffF&#33ffffe&#66ffffa&#99ffffr &#ccffffC&#e6ffffr&#ffffffa&#e6fffff&#ccfffft &8- &fKılıçlar"));
        // 9'lu simetri için ortalanmış dizilim (10-16 ve 22-24 slotları)
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
        int cd = 30;

        switch (type) {
            case "shulker" -> { dName = "&#e8473e&lShulker Kılıcı"; cd = 30; }
            case "creeper" -> { dName = "&#00e600&lCreeper Kılıcı"; cd = 25; }
            case "orumcek" -> { dName = "&#bd0000&lÖrümcek Kılıcı"; cd = 60; }
            case "phantom" -> { dName = "&#0080ff&lPhantom Kılıcı"; cd = 65; }
            case "yildirim" -> { dName = "&#ffee00&lYıldırım Kılıcı"; cd = 30; }
            case "ejderha" -> { dName = "&#ffaa00&lEjderha Kılıcı"; cd = 30; }
            case "gardiyan" -> { dName = "&#00e6b8&lGardiyan Kılıcı"; cd = 60; }
            case "wither" -> { dName = "&#404040&lWither Kılıcı"; cd = 30; }
            case "enderman" -> { dName = "&#cc33ff&lEnderman Kılıcı"; cd = 30; }
        }

        meta.setDisplayName(color(dName));
        meta.setLore(getAnimatedLore(type, cd));
        item.setItemMeta(meta);
        return item;
    }

    private List<String> getAnimatedLore(String type, int cd) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(getFlopText("  ✦ Fear Craft Özel Yetenek ✦  ", flopTick));
        lore.add(color("&7Bu kılıç kadim güçlerle donatıldı."));
        lore.add(color("&7Sağ tıklayarak rakiplerini bozguna uğrat!"));
        lore.add("");
        lore.add(color("&fDurum: &aKullanıma Hazır"));
        lore.add(color("&fBekleme Süresi: &e" + cd + " Saniye"));
        lore.add("");
        lore.add(getFlopText("----------------------------", flopTick + 5));
        return lore;
    }

    // ===================== YETENEK MANTIĞI =====================
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!e.getAction().name().contains("RIGHT")) return;
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() != Material.NETHERITE_SWORD || !item.hasItemMeta()) return;

        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();
        
        // Yetenek tetikleyicileri
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
            // Videodaki devasa yazı yerine zarif Subtitle
            p.sendTitle("", color("&#ff3300Tekrar kullanmadan önce &#ffcc00" + (left + 1) + " &#ff3300saniye bekleyin!"), 0, 30, 5);
            return;
        }
        
        cooldowns.get(p.getUniqueId()).put(type, System.currentTimeMillis() + (sec * 1000L));
        List<Player> targets = p.getNearbyEntities(6, 6, 6).stream()
                .filter(en -> en instanceof Player && en != p).map(en -> (Player)en).toList();

        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(getFlopText("» " + type.toUpperCase() + " YETENEĞİ KULLANILDI «", flopTick)));

        switch (type) {
            case "shulker" -> {
                p.sendMessage(color("&#e8473e[FearCraft] &fŞulker gücüyle rakiplerini havaya uçurdun!"));
                targets.forEach(t -> t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 1)));
            }
            case "creeper" -> {
                p.sendMessage(color("&#00e600[FearCraft] &fPatlayıcı güç rakiplerini savurdu!"));
                p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation(), 1);
                targets.forEach(t -> { t.damage(6); t.setVelocity(t.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.5).setY(0.5)); });
            }
            case "orumcek" -> {
                p.sendMessage(color("&#bd0000[FearCraft] &fRakiplerini kadim ağlara hapsettin!"));
                targets.forEach(t -> {
                    Location loc = t.getLocation();
                    loc.getBlock().setType(Material.COBWEB);
                    loc.add(0, 1, 0).getBlock().setType(Material.COBWEB);
                    new BukkitRunnable() { public void run() { loc.getBlock().setType(Material.AIR); loc.subtract(0, 1, 0).getBlock().setType(Material.AIR); }}.runTaskLater(this, 70);
                });
            }
            case "phantom" -> {
                p.sendMessage(color("&#0080ff[FearCraft] &fPhantom lanetiyle uçuşları engellendi!"));
                targets.forEach(t -> {
                    elytraBanned.put(t.getUniqueId(), System.currentTimeMillis() + 3000L);
                    if (t.isGliding()) t.setGliding(false);
                    t.sendTitle("", color("&#ff0000Elitran 3 saniye bozuldu!"), 5, 20, 5);
                });
            }
            case "ejderha" -> { // Düzeltilen Ejderha: Alev Püskürtme
                p.sendMessage(color("&#ffaa00[FearCraft] &fEjderha nefesi her şeyi yakıyor!"));
                new BukkitRunnable() {
                    int i = 0;
                    public void run() {
                        if (i++ > 10) cancel();
                        Location eye = p.getEyeLocation();
                        Vector dir = eye.getDirection().multiply(i * 0.8);
                        Location wave = eye.add(dir);
                        wave.getWorld().spawnParticle(Particle.DRAGON_BREATH, wave, 10, 0.2, 0.2, 0.2, 0.05);
                        wave.getWorld().getNearbyEntities(wave, 1.5, 1.5, 1.5).forEach(en -> {
                            if (en instanceof LivingEntity le && en != p) { le.damage(2); le.setFireTicks(40); }
                        });
                    }
                }.runTaskTimer(this, 0, 1);
            }
            case "enderman" -> {
                p.sendMessage(color("&#cc33ff[FearCraft] &fRakiplerini boşluğa ışınladın!"));
                targets.forEach(t -> {
                    t.teleport(t.getLocation().add(p.getLocation().getDirection().multiply(-4)));
                    t.getWorld().spawnParticle(Particle.PORTAL, t.getLocation(), 30, 0.5, 1, 0.5, 0.1);
                    t.playSound(t.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                });
            }
            case "yildirim" -> targets.forEach(t -> t.getWorld().strikeLightning(t.getLocation()));
            case "gardiyan" -> targets.forEach(t -> t.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 2)));
            case "wither" -> targets.forEach(t -> t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1)));
        }
    }

    // ===================== İADE SİSTEMİ (SİMETRİK & RGB) =====================
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        String killer = (p.getKiller() != null) ? p.getKiller().getName() : "Bilinmiyor";
        ItemStack[] items = p.getInventory().getContents();
        ItemStack[] backup = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) if (items[i] != null) backup[i] = items[i].clone();
        
        deathRecords.add(0, new DeathRecord(p, killer, backup));
        if (deathRecords.size() > 45) deathRecords.remove(44);
    }

    private void openIadeMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&#00ccffF&#33ffffe&#66ffffa&#99ffffr &#ccffffC&#e6ffffr&#ffffffa&#e6fffff&#ccfffft &8- &fİade Listesi"));
        updateIadeList(inv);
        p.openInventory(inv);
    }

    private void updateIadeList(Inventory inv) {
        for (int i = 0; i < deathRecords.size() && i < 45; i++) {
            DeathRecord dr = deathRecords.get(i);
            ItemStack chest = new ItemStack(Material.CHEST);
            ItemMeta m = chest.getItemMeta();
            m.setDisplayName(color("&#ffcc00" + dr.playerName + " &8- &7#" + dr.id));
            
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(getFlopText("  [ ÖLÜM VERİSİ ]  ", flopTick + (i * 2)));
            lore.add(color("&fKatil: &c" + dr.killerName));
            lore.add(color("&fKonum: &7" + dr.loc.getBlockX() + ", " + dr.loc.getBlockY() + ", " + dr.loc.getBlockZ()));
            lore.add("");
            lore.add(color("&bSağ Tık: &fEşyaları Görüntüle"));
            lore.add(color("&bSol Tık: &fEşyaları İade Et"));
            lore.add("");
            lore.add(getFlopText("----------------------", flopTick + i));
            m.setLore(lore);
            chest.setItemMeta(m);
            inv.setItem(i, chest);
        }
        
        // Simetrik Alt Butonlar
        inv.setItem(48, createBtn(Material.PAPER, "&#00ccffSistem Bilgisi", "&7Ölen oyuncuların envanterini\n&7tam slotuna göre geri verir."));
        inv.setItem(49, createBtn(Material.BARRIER, "&#ff3300Menüyü Kapat", "&7Pencereyi güvenle kapatır."));
        inv.setItem(50, createBtn(Material.RECOVERY_COMPASS, "&#00ffccKayıt Durumu", "&7Sistem şu an aktif ve izliyor."));
    }

    private void openSpecificRecordItems(Player p, DeathRecord dr) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&8Görüntüleniyor: &b" + dr.playerName));
        for (int i = 0; i < dr.items.length && i < 41; i++) {
            if (dr.items[i] != null) inv.setItem(i, dr.items[i]);
        }
        // Kontrol butonları
        inv.setItem(48, createBtn(Material.ENDER_PEARL, "&#00ccffKonuma Git", "&7Ölüm yerine ışınlanırsın."));
        inv.setItem(49, createBtn(Material.NETHER_STAR, "&#00ff00Eşyaları İade Et", "&7Oyuncuya tüm seti gönderir."));
        inv.setItem(50, createBtn(Material.ARROW, "&#ffcc00Geri Dön", "&7Listeye geri döner."));
        p.openInventory(inv);
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        Player p = (Player) e.getWhoClicked();

        if (title.contains("İade Listesi")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;
            
            if (e.getCurrentItem().getType() == Material.CHEST) {
                String id = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).split("#")[1];
                DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
                if (dr == null) return;

                if (e.isRightClick()) openSpecificRecordItems(p, dr);
                else {
                    Player target = Bukkit.getPlayer(dr.playerUUID);
                    if (target != null && target.isOnline()) {
                        target.getInventory().setContents(dr.items);
                        p.sendMessage(color("&a[FearCraft] &fEşyalar @" + dr.playerName + " kişisine iade edildi."));
                    } else p.sendMessage(color("&c[!] Oyuncu şu an oyunda değil!"));
                }
            } else if (e.getRawSlot() == 49) p.closeInventory();
        } 
        else if (title.startsWith("Görüntüleniyor:")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 50) openIadeMenu(p);
            else if (e.getRawSlot() == 48) { /* Işınlanma logic */ }
        }
        else if (title.contains("Kılıçlar")) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.NETHERITE_SWORD) {
                p.getInventory().addItem(e.getCurrentItem().clone());
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
            }
        }
    }

    // ===================== RGB & UTILS =====================
    private void updateAnimatedMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            InventoryView view = p.getOpenInventory();
            String title = ChatColor.stripColor(view.getTitle());
            if (title.contains("İade Listesi") || title.contains("Kılıçlar")) {
                for (int i = 0; i < view.getTopInventory().getSize(); i++) {
                    ItemStack it = view.getTopInventory().getItem(i);
                    if (it != null && it.hasItemMeta()) {
                        ItemMeta m = it.getItemMeta();
                        if (it.getType() == Material.NETHERITE_SWORD) {
                            String type = ChatColor.stripColor(m.getDisplayName()).split(" ")[0].toLowerCase();
                            m.setLore(getAnimatedLore(type, 30));
                        } else if (it.getType() == Material.CHEST) {
                            // İade lore'u buraya da eklenebilir
                        }
                        it.setItemMeta(m);
                    }
                }
            }
        }
    }

    private String getFlopText(String text, int offset) {
        String[] colors = {"&#00ccff", "&#33ffff", "&#66ffff", "&#99ffff", "&#ccffff", "&#ffffff"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(colors[(i + offset) % colors.length]).append(text.charAt(i));
        }
        return color(sb.toString());
    }

    private ItemStack createBtn(Material m, String name, String lore) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(Collections.singletonList(color(lore)));
        item.setItemMeta(meta);
        return item;
    }

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

    @EventHandler public void onGlide(EntityToggleGlideEvent e) {
        if (e.getEntity() instanceof Player p && elytraBanned.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) e.setCancelled(true);
    }
                }
