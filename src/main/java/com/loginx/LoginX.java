package com.loginx;

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
    private final HashMap<UUID, Long> soundCooldown = new HashMap<>();
    private final List<DeathRecord> deathRecords = new ArrayList<>();
    private final HashMap<String, Location> crates = new HashMap<>();
    private final HashMap<String, List<ItemStack>> crateItems = new HashMap<>();
    private NamespacedKey keyTag;
    private int flopTick = 0;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        keyTag = new NamespacedKey(this, "kasa_anahtari");
        loadData();

        new BukkitRunnable() {
            @Override
            public void run() {
                flopTick++;
                updateCrateEffects();
                updateAnimatedMenus();
            }
        }.runTaskTimer(this, 0, 2);
        
        getLogger().info("LoginX V1.2 Aktif! Sigma Edition.");
    }

    @Override
    public void onDisable() { saveData(); }

    // ===================== KOMUTLAR =====================
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (!p.hasPermission("loginx.admin")) return true;

        String c = cmd.getName().toLowerCase();
        
        if (c.equals("loginxreload")) {
            reloadConfig();
            loadData();
            p.sendMessage(color("&7[&fLoginX&7] &aVeriler yenilendi."));
        } 
        else if (c.equals("iade")) openIadeMenu(p);
        else if (c.equals("kasaanahtarmenu")) openKeyMenu(p);
        else if (c.equals("kasaayarla") && args.length == 1) {
            Block b = p.getTargetBlockExact(5);
            if (b == null || b.getType() == Material.AIR) return true;
            crates.put(args[0].toLowerCase(), b.getLocation());
            crateItems.putIfAbsent(args[0].toLowerCase(), new ArrayList<>());
            p.sendMessage(color("&f&l" + args[0].toUpperCase() + " &7kasası başarıyla ayarlandı."));
            saveData();
        }
        else if (c.equals("kasasil") && args.length == 1) {
            crates.remove(args[0].toLowerCase());
            crateItems.remove(args[0].toLowerCase());
            p.sendMessage(color("&cKasa silindi: " + args[0]));
            saveData();
        }
        else if (c.equals("kasaicsil") && args.length == 2) {
            String kName = args[0].toLowerCase();
            if (!crateItems.containsKey(kName)) return true;
            try {
                int index = Integer.parseInt(args[1].replace("#", "")) - 1;
                crateItems.get(kName).remove(index);
                p.sendMessage(color("&aİtem #" + (index+1) + " silindi."));
                saveData();
            } catch (Exception e) { p.sendMessage(color("&cGeçersiz sıra numarası!")); }
        }
        else if (c.equals("icineitemkoy") && args.length == 1) {
            ItemStack inHand = p.getInventory().getItemInMainHand();
            if (inHand.getType() == Material.AIR) return true;
            String kName = args[0].toLowerCase();
            if (!crates.containsKey(kName)) return true;
            crateItems.get(kName).add(inHand.clone());
            p.sendMessage(color("&f" + kName + " &7kasasına yeni eşya eklendi."));
            saveData();
        }
        return true;
    }

    // ===================== KUMAR SİSTEMİ (ROLLING) =====================
    private void startCrateRoll(Player p, String crateName, ItemStack keyUsed) {
        List<ItemStack> pool = crateItems.get(crateName);
        if (pool == null || pool.isEmpty()) {
            p.sendMessage(color("&cBu kasa boş!"));
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, getGrayFlop("      KADER ÇARKI      ", flopTick));
        ItemStack border = createBtn(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, border);
        inv.setItem(4, createBtn(Material.WHITE_STAINED_GLASS_PANE, "&f▼ &7Şanslı Bölge &f▼"));
        inv.setItem(22, createBtn(Material.WHITE_STAINED_GLASS_PANE, "&f▲ &7Şanslı Bölge &f▲"));

        p.openInventory(inv);
        keyUsed.setAmount(keyUsed.getAmount() - 1);

        new BukkitRunnable() {
            int timer = 0;
            int speed = 1;
            final Random r = new Random();

            @Override
            public void run() {
                if (timer < 25) {
                    // Kaydırma efekti
                    for (int i = 9; i < 17; i++) {
                        inv.setItem(i, inv.getItem(i + 1));
                    }
                    inv.setItem(17, pool.get(r.nextInt(pool.size())));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, 0.5f, 1.5f);
                } else if (timer == 26) {
                    ItemStack winner = inv.getItem(13);
                    p.closeInventory();
                    p.getInventory().addItem(winner);
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation(), 30, 0.5, 0.5, 0.5, 0.1);
                    
                    String itemName = winner.hasItemMeta() && winner.getItemMeta().hasDisplayName() ? 
                                     winner.getItemMeta().getDisplayName() : winner.getType().name();
                    
                    p.sendTitle(color("&f&lKADERİN GÜLDÜ!"), color("&7" + itemName + " &fKazandın"), 10, 40, 10);
                    this.cancel();
                }
                timer++;
            }
        }.runTaskTimer(this, 0, 3);
    }

    // ===================== KASA ETKİLEŞİM =====================
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
                playLockedEffect(p, loc);
                return;
            }

            String keyType = inHand.getItemMeta().getPersistentDataContainer().get(keyTag, PersistentDataType.STRING);
            if (!keyType.equalsIgnoreCase(activeCrate) && !keyType.equalsIgnoreCase("all")) {
                playLockedEffect(p, loc);
                return;
            }
            startCrateRoll(p, activeCrate, inHand);
        }
    }

    private void playLockedEffect(Player p, Location loc) {
        if (soundCooldown.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) return;
        p.setVelocity(p.getLocation().getDirection().multiply(-0.3).setY(0.2));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        p.sendTitle(color("&8&lKİLİTLİ"), color("&7Doğru anahtarı kullanmalısın."), 5, 20, 5);
        soundCooldown.put(p.getUniqueId(), System.currentTimeMillis() + 1500);
    }

    // ===================== MENÜLER & GÖRSELLER =====================
    private void openCratePreview(Player p, String crateName) {
        Inventory inv = Bukkit.createInventory(null, 45, getGrayFlop("--- " + crateName.toUpperCase() + " ---", flopTick));
        ItemStack cam = createBtn(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) if (i < 9 || i > 35 || i % 9 == 0 || i % 9 == 8) inv.setItem(i, cam);
        
        List<ItemStack> items = crateItems.getOrDefault(crateName, new ArrayList<>());
        int slot = 10;
        for (ItemStack item : items) {
            if (slot % 9 == 8) slot += 2;
            if (slot > 34) break;
            inv.setItem(slot++, item);
        }
        inv.setItem(40, createBtn(Material.BARRIER, "&cKapat"));
        p.openInventory(inv);
    }

    private void openKeyMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&8Anahtar Dolabı"));
        for (int i = 0; i < 27; i++) inv.setItem(i, createBtn(Material.BLACK_STAINED_GLASS_PANE, " "));

        inv.setItem(11, createKey(Material.IRON_NUGGET, "Afk Key", "afk"));
        inv.setItem(12, createKey(Material.GOLD_NUGGET, "Casino Key", "casino"));
        inv.setItem(13, createKey(Material.TRIPWIRE_HOOK, "Gear Key", "gear"));
        inv.setItem(14, createKey(Material.NAME_TAG, "Booster Key", "booster"));
        inv.setItem(15, createKey(Material.NETHER_STAR, "&f&lMaster Key", "all"));
        p.openInventory(inv);
    }

    private ItemStack createKey(Material m, String name, String id) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(getGrayFlop(name, 0)); 
        meta.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    // ===================== EFEKTLER & RENKLER =====================
    private void updateCrateEffects() {
        for (Location loc : crates.values()) {
            Location l = loc.clone().add(0.5, 1.2, 0.5);
            double angle = flopTick * 0.15;
            l.add(Math.cos(angle) * 0.4, Math.sin(flopTick * 0.1) * 0.2, Math.sin(angle) * 0.4);
            l.getWorld().spawnParticle(Particle.END_ROD, l, 1, 0, 0, 0, 0);
        }
    }

    private void updateAnimatedMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            InventoryView view = p.getOpenInventory();
            String title = ChatColor.stripColor(view.getTitle());
            if (title.contains("---") || title.contains("KADER")) {
                Inventory inv = view.getTopInventory();
                // Sadece başlık animasyonu ve anahtar isimleri
                if (title.contains("Anahtar")) {
                   for (int i=11; i<=15; i++) {
                       ItemStack it = inv.getItem(i);
                       if (it == null) continue;
                       ItemMeta m = it.getItemMeta();
                       m.setDisplayName(getGrayFlop(ChatColor.stripColor(m.getDisplayName()), flopTick));
                       it.setItemMeta(m);
                   }
                }
            }
        }
    }

    public String getGrayFlop(String t, int tick) {
        String[] colors = {"&#ffffff", "&#f2f2f2", "&#e6e6e6", "&#d9d9d9", "&#cccccc", "&#bfbfbf", "&#b3b3b3", "&#a6a6a6"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            sb.append(colors[(i + tick) % colors.length]).append(t.charAt(i));
        }
        return color(sb.toString());
    }

    public String color(String text) {
        Pattern pattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder rep = new StringBuilder("§x");
            for (char ch : hex.toCharArray()) rep.append("§").append(ch);
            matcher.appendReplacement(sb, rep.toString());
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    private ItemStack createBtn(Material m, String n) {
        ItemStack i = new ItemStack(m);
        ItemMeta mt = i.getItemMeta();
        mt.setDisplayName(color(n));
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
            }
        }
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        if (title.contains("KADER") || title.contains("---") || title.contains("Anahtar")) {
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getView().getTopInventory()) return;
            if (title.contains("Anahtar") && e.getCurrentItem() != null && e.getCurrentItem().getType() != Material.BLACK_STAINED_GLASS_PANE) {
                e.getWhoClicked().getInventory().addItem(e.getCurrentItem().clone());
            }
        }
    }

    // İade ve Diğer Sistemler (Aynı mantıkla devam eder)
    private static class DeathRecord {
        String id; UUID playerUUID; String playerName, killerName, date; Location loc; ItemStack[] items;
        public DeathRecord(Player p, String killer, ItemStack[] itms) {
            this.id = Integer.toHexString(new Random().nextInt(0xFFFF)).toUpperCase();
            this.playerUUID = p.getUniqueId(); this.playerName = p.getName(); this.killerName = killer;
            this.loc = p.getLocation(); this.items = itms;
            this.date = new SimpleDateFormat("dd/MM HH:mm").format(new Date());
        }
    }

    @EventHandler public void onDeath(PlayerDeathEvent e) {
        deathRecords.add(0, new DeathRecord(e.getEntity(), e.getEntity().getKiller() != null ? e.getEntity().getKiller().getName() : "Bilinmiyor", e.getEntity().getInventory().getContents()));
    }
                    }
        
