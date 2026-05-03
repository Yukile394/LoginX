package com.loginx;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoginX extends JavaPlugin implements Listener {

    private final HashMap<UUID, Long> elytraBanned = new HashMap<>();
    private final List<DeathRecord> deathRecords = new ArrayList<>();
    private int flopTick = 0;

    // Kasa Sistemleri
    private final HashMap<String, Location> crates = new HashMap<>();
    private final HashMap<String, List<ItemStack>> crateItems = new HashMap<>();
    private final HashMap<String, Boolean> crateEffects = new HashMap<>();
    private NamespacedKey keyTag;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        keyTag = new NamespacedKey(this, "kasa_anahtari");
        
        saveDefaultConfig();
        loadData();

        new BukkitRunnable() {
            @Override
            public void run() {
                flopTick++;
                updateCrateEffects();
                updateAnimatedMenus();
            }
        }.runTaskTimer(this, 0, 3);
        
        getLogger().info("Fear Craft LoginX V1.1 Aktif! Kasa & İade Sistemleri yüklendi.");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private static class DeathRecord {
        String id;
        UUID playerUUID;
        String playerName, killerName, date;
        Location loc;
        ItemStack[] items;

        public DeathRecord(Player p, String killer, ItemStack[] itms) {
            this.id = Integer.toHexString(new Random().nextInt(0xFFFF)).toUpperCase();
            this.playerUUID = p.getUniqueId();
            this.playerName = p.getName();
            this.killerName = killer;
            this.loc = p.getLocation();
            this.items = itms;
            this.date = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (!p.hasPermission("loginx.admin")) return true;

        String c = cmd.getName().toLowerCase();
        
        if (c.equals("loginxreload")) {
            reloadConfig();
            loadData();
            p.sendMessage(color("&aLoginX başarıyla yenilendi!"));
        } 
        else if (c.equals("iade")) openIadeMenu(p);
        else if (c.equals("kasaanahtarmenu")) openKeyMenu(p);
        else if (c.equals("kasaayarla") && args.length == 1) {
            Block b = p.getTargetBlockExact(5);
            if (b == null || b.getType() == Material.AIR) {
                p.sendMessage(color("&cLütfen bir bloğa bakarak komutu girin."));
                return true;
            }
            crates.put(args[0].toLowerCase(), b.getLocation());
            crateItems.putIfAbsent(args[0].toLowerCase(), new ArrayList<>());
            crateEffects.put(args[0].toLowerCase(), true);
            p.sendMessage(color("&a" + args[0] + " kasası ayarlandı!"));
            saveData();
        }
        else if (c.equals("icineitemkoy") && args.length == 1) {
            ItemStack inHand = p.getInventory().getItemInMainHand();
            if (inHand.getType() == Material.AIR) {
                p.sendMessage(color("&cElinizde bir eşya olmalı!"));
                return true;
            }
            String kasaAdi = args[0].toLowerCase();
            if (!crates.containsKey(kasaAdi)) {
                p.sendMessage(color("&cBöyle bir kasa bulunamadı!"));
                return true;
            }
            crateItems.get(kasaAdi).add(inHand.clone());
            p.sendMessage(color("&aEşya " + kasaAdi + " kasasına eklendi."));
            saveData();
        }
        else if (c.equals("efektac") && args.length == 1) {
            crateEffects.put(args[0].toLowerCase(), true);
            p.sendMessage(color("&aEfekt açıldı: " + args[0]));
        }
        else if (c.equals("efektkapa") && args.length == 1) {
            crateEffects.put(args[0].toLowerCase(), false);
            p.sendMessage(color("&cEfekt kapatıldı: " + args[0]));
        }
        return true;
    }

    // ===================== KASA SİSTEMİ =====================
    @EventHandler
    public void onCrateInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        Location loc = e.getClickedBlock().getLocation();
        String activeCrate = null;

        for (Map.Entry<String, Location> entry : crates.entrySet()) {
            if (entry.getValue().equals(loc)) {
                activeCrate = entry.getKey();
                break;
            }
        }

        if (activeCrate == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();

        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            openCratePreview(p, activeCrate);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack inHand = p.getInventory().getItemInMainHand();
            if (!inHand.hasItemMeta() || !inHand.getItemMeta().getPersistentDataContainer().has(keyTag, PersistentDataType.STRING)) {
                // Anahtar Yok - Geri itme ve şık uyarı
                p.setVelocity(p.getLocation().getDirection().multiply(-0.5).setY(0.4));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                p.getWorld().spawnParticle(Particle.SMOKE_LARGE, loc.clone().add(0.5, 1, 0.5), 10, 0.2, 0.2, 0.2, 0.05);
                p.sendTitle(color("&#ff0000&lDUR!"), color("&#ff6666Kasa Anahtarın Yok"), 5, 60, 10);
                return;
            }

            String keyType = inHand.getItemMeta().getPersistentDataContainer().get(keyTag, PersistentDataType.STRING);
            
            // Eğer anahtar türü ile kasa eşleşmesini istersen buraya if(keyType.equals(activeCrate)) ekleyebilirsin.
            // Şimdilik anahtar varsa açar.
            
            inHand.setAmount(inHand.getAmount() - 1); // Anahtarı tüket
            List<ItemStack> pool = crateItems.getOrDefault(activeCrate, new ArrayList<>());
            if (pool.isEmpty()) {
                p.sendMessage(color("&cBu kasa henüz boş!"));
                return;
            }
            
            // Rastgele item ver
            ItemStack reward = pool.get(new Random().nextInt(pool.size()));
            p.getInventory().addItem(reward.clone());
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            p.getWorld().spawnParticle(Particle.TOTEM, loc.clone().add(0.5, 1.5, 0.5), 50, 0.5, 0.5, 0.5, 0.1);
            p.sendMessage(color("&#00ff00&lTebrikler! &#aaffaaKasadan eşya çıkardın."));
        }
    }

    private void openCratePreview(Player p, String crateName) {
        Inventory inv = Bukkit.createInventory(null, 45, color("&#00ccff&lKasa Önizleme: " + crateName));
        ItemStack cam = createBtn(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) if (i < 9 || i > 35 || i % 9 == 0 || i % 9 == 8) inv.setItem(i, cam);
        
        List<ItemStack> items = crateItems.getOrDefault(crateName, new ArrayList<>());
        int slot = 10;
        for (ItemStack item : items) {
            if (slot % 9 == 8) slot += 2;
            if (slot > 34) break;
            inv.setItem(slot++, item);
        }
        inv.setItem(40, createBtn(Material.BARRIER, "&#ff3333&lKapat"));
        p.openInventory(inv);
    }

    private void openKeyMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "Anahtar Menüsü");
        ItemStack cam = createBtn(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, cam);

        // Kasa anahtarları boyalarla
        inv.setItem(11, createKey(Material.RED_DYE, "Casino Key", "casino"));
        inv.setItem(12, createKey(Material.LIGHT_BLUE_DYE, "Afk Key", "afk"));
        inv.setItem(13, createKey(Material.ORANGE_DYE, "Gear Key", "gear"));
        inv.setItem(14, createKey(Material.YELLOW_DYE, "Booster Key", "booster"));
        inv.setItem(15, createKey(Material.NETHER_STAR, "Key All", "all"));

        p.openInventory(inv);
    }

    private ItemStack createKey(Material m, String name, String id) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name); // FlopText updateAnimatedMenus içinde eklenecek
        meta.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, id);
        meta.setLore(Collections.emptyList()); // Lore yok
        item.setItemMeta(meta);
        return item;
    }

    // ===================== GÜNCELLENMİŞ İADE SİSTEMİ =====================
    private void openIadeMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, getFlopText("Fear Craft - İade", flopTick));
        for (int i = 0; i < deathRecords.size() && i < 45; i++) {
            DeathRecord dr = deathRecords.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(dr.playerUUID));
            sm.setDisplayName(color("&#ffcc00&l" + dr.playerName + " &7(#" + dr.id + ")"));
            
            List<String> lore = new ArrayList<>();
            lore.add(color("&8&m------------------------"));
            lore.add(color("&7Ölüm Tarihi: &f" + dr.date));
            lore.add(color("&7Katil: &c" + dr.killerName));
            lore.add(color("&7Konum: &a" + dr.loc.getBlockX() + ", " + dr.loc.getBlockY() + ", " + dr.loc.getBlockZ()));
            lore.add(color("&8&m------------------------"));
            lore.add(color("&#00ffcc» Sağ Tıkla: &fDetaylı İncele"));
            lore.add(color("&#00ffcc» Sol Tıkla: &fDirekt İade Et"));
            
            sm.setLore(lore);
            head.setItemMeta(sm);
            inv.setItem(i, head);
        }
        
        // Alt 3 Yararlı Seçenek
        inv.setItem(48, createBtn(Material.LAVA_BUCKET, "&#ff3300Tüm Geçmişi Sil"));
        inv.setItem(49, createBtn(Material.BARRIER, "&#ff3333&lKapat"));
        inv.setItem(50, createBtn(Material.EMERALD_BLOCK, "&#00ff00Sayfayı Yenile"));
        
        p.openInventory(inv);
    }

    private void openIadeDetail(Player p, DeathRecord dr) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&#ff9900Ölüm Detayı: &f" + dr.playerName));
        // Eşyaları tam slotlarına diz
        for (int i = 0; i < dr.items.length; i++) {
            if (dr.items[i] != null && i < 41) inv.setItem(i, dr.items[i]);
        }
        
        ItemStack cam = createBtn(Material.WHITE_STAINED_GLASS_PANE, " ");
        for (int i = 41; i < 45; i++) inv.setItem(i, cam);
        
        // 3 Alt Seçenek
        inv.setItem(48, createBtn(Material.RED_DYE, "&#ff0000Bu Kaydı Sil", dr.id));
        inv.setItem(49, createBtn(Material.BARRIER, "&#ff3333Geri Dön", dr.id));
        inv.setItem(50, createBtn(Material.LIME_DYE, "&#00ff00Eşyaları İade Et", dr.id));
        
        p.openInventory(inv);
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        Player p = (Player) e.getWhoClicked();

        if (title.contains("İade") || title.contains("Anahtar") || title.contains("Kasa Önizleme") || title.contains("Ölüm Detayı")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;
            ItemStack clicked = e.getCurrentItem();
            Material m = clicked.getType();
            String itemName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

            // Anahtar Menüsü
            if (title.contains("Anahtar Menüsü") && m != Material.BLACK_STAINED_GLASS_PANE) {
                p.getInventory().addItem(clicked.clone());
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }
            // Ana İade Menüsü Seçenekleri
            else if (title.equals("Fear Craft - İade")) {
                if (m == Material.PLAYER_HEAD) {
                    String id = itemName.split("#")[1].replace(")", "");
                    DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
                    if (dr != null) {
                        if (e.isLeftClick()) {
                            Player target = Bukkit.getPlayer(dr.playerUUID);
                            if (target != null && target.isOnline()) {
                                target.getInventory().setContents(dr.items);
                                p.sendMessage(color("&aEşyalar @" + dr.playerName + " kişisine iade edildi."));
                            }
                        } else if (e.isRightClick()) {
                            openIadeDetail(p, dr);
                        }
                    }
                } else if (m == Material.BARRIER) p.closeInventory();
                else if (m == Material.EMERALD_BLOCK) openIadeMenu(p);
                else if (m == Material.LAVA_BUCKET) {
                    deathRecords.clear();
                    openIadeMenu(p);
                    p.sendMessage(color("&cTüm ölüm geçmişi silindi."));
                }
            }
            // Ölüm Detay Menüsü Seçenekleri
            else if (title.contains("Ölüm Detayı")) {
                if (m == Material.BARRIER) openIadeMenu(p);
                else if (m == Material.RED_DYE || m == Material.LIME_DYE) {
                    List<String> l = clicked.getItemMeta().getLore();
                    if(l == null || l.isEmpty()) return;
                    String id = ChatColor.stripColor(l.get(0));
                    DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
                    
                    if (dr != null && m == Material.LIME_DYE) {
                        Player target = Bukkit.getPlayer(dr.playerUUID);
                        if (target != null && target.isOnline()) {
                            target.getInventory().setContents(dr.items);
                            p.sendMessage(color("&aEşyalar iade edildi."));
                        }
                    }
                    if (dr != null && m == Material.RED_DYE) {
                        deathRecords.remove(dr);
                        openIadeMenu(p);
                    }
                }
            }
            else if (title.contains("Kasa Önizleme") && m == Material.BARRIER) p.closeInventory();
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        deathRecords.add(0, new DeathRecord(e.getEntity(), e.getEntity().getKiller() != null ? e.getEntity().getKiller().getName() : "Bilinmiyor", e.getEntity().getInventory().getContents()));
    }

    // ===================== UTILS & RUNNABLES =====================
    private void updateAnimatedMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            InventoryView view = p.getOpenInventory();
            String title = ChatColor.stripColor(view.getTitle());
            if (title.contains("Anahtar Menüsü")) {
                Inventory inv = view.getTopInventory();
                for (int i = 11; i <= 15; i++) {
                    ItemStack item = inv.getItem(i);
                    if (item != null && item.hasItemMeta()) {
                        ItemMeta m = item.getItemMeta();
                        String baseName = ChatColor.stripColor(m.getDisplayName());
                        m.setDisplayName(getFlopText("✦ " + baseName + " ✦", flopTick));
                        item.setItemMeta(m);
                    }
                }
            }
        }
    }

    private void updateCrateEffects() {
        for (Map.Entry<String, Location> entry : crates.entrySet()) {
            if (crateEffects.getOrDefault(entry.getKey(), false)) {
                Location loc = entry.getValue().clone().add(0.5, 0.5, 0.5);
                double radius = 1.0;
                double angle = flopTick * 0.2;
                double x = radius * Math.cos(angle);
                double z = radius * Math.sin(angle);
                loc.add(x, Math.sin(flopTick * 0.1) * 0.5, z);
                
                loc.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, loc, 1, 0, 0, 0, 0);
                loc.getWorld().spawnParticle(Particle.END_ROD, entry.getValue().clone().add(0.5, 1.2, 0.5), 1, 0, 0, 0, 0.01);
            }
        }
    }

    public String color(String text) {
        Pattern pattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder rep = new StringBuilder("§x");
            for (char c : hex.toCharArray()) rep.append("§").append(c);
            matcher.appendReplacement(sb, rep.toString());
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public String getFlopText(String t, int tick) {
        String[] colors = {"&#ff0000", "&#ff6600", "&#ffcc00", "&#33cc33", "&#00ccff", "&#9933ff"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            if(t.charAt(i) == ' ') sb.append(" ");
            else sb.append(colors[(i + tick) % colors.length]).append(t.charAt(i));
        }
        return color(sb.toString());
    }

    private ItemStack createBtn(Material m, String n) {
        ItemStack i = new ItemStack(m);
        ItemMeta mt = i.getItemMeta();
        mt.setDisplayName(color(n));
        i.setItemMeta(mt);
        return i;
    }
    
    private ItemStack createBtn(Material m, String n, String hiddenIdData) {
        ItemStack i = createBtn(m, n);
        ItemMeta mt = i.getItemMeta();
        mt.setLore(Collections.singletonList(color("&8" + hiddenIdData)));
        i.setItemMeta(mt);
        return i;
    }

    private void saveData() {
        for (String k : crates.keySet()) {
            getConfig().set("crates." + k + ".loc", crates.get(k));
            getConfig().set("crates." + k + ".items", crateItems.get(k));
        }
        saveConfig();
    }

    private void loadData() {
        if (getConfig().contains("crates")) {
            for (String k : getConfig().getConfigurationSection("crates").getKeys(false)) {
                crates.put(k, getConfig().getLocation("crates." + k + ".loc"));
                crateItems.put(k, (List<ItemStack>) getConfig().getList("crates." + k + ".items"));
                crateEffects.put(k, true);
            }
        }
    }

    @EventHandler public void onGlide(EntityToggleGlideEvent e) {
        if (e.getEntity() instanceof Player p && elytraBanned.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) e.setCancelled(true);
    }
                                           }
                             
