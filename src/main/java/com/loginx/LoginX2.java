package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class LoginX2 implements Listener, CommandExecutor {

    private final LoginX plugin;
    private File deathsFile;
    private FileConfiguration deathsConfig;

    public LoginX2(LoginX plugin) {
        this.plugin = plugin;
        setupFiles();
        // Komut Kayıtları
        String[] cmds = {"ban", "ipban", "mute", "unban", "iade", "player"};
        for (String c : cmds) {
            PluginCommand pc = plugin.getCommand(c);
            if (pc != null) pc.setExecutor(this);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startScoreboardTask();
    }

    private void setupFiles() {
        deathsFile = new File(plugin.getDataFolder(), "deaths.yml");
        if (!deathsFile.exists()) {
            try { deathsFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        deathsConfig = YamlConfiguration.loadConfiguration(deathsFile);
    }

    // --- ÖLÜM KAYIT SİSTEMİ ---
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        String path = "deaths." + p.getUniqueId() + "." + System.currentTimeMillis();
        
        deathsConfig.set(path + ".date", new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()));
        deathsConfig.set(path + ".world", p.getWorld().getName());
        deathsConfig.set(path + ".loc", p.getLocation().toVector());
        deathsConfig.set(path + ".items", p.getInventory().getContents());
        deathsConfig.set(path + ".armor", p.getInventory().getArmorContents());
        
        int total = deathsConfig.getInt("stats." + p.getUniqueId() + ".total", 0);
        deathsConfig.set("stats." + p.getUniqueId() + ".total", total + 1);
        deathsConfig.set("stats." + p.getUniqueId() + ".last", new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()));
        
        saveDeaths();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (label.equalsIgnoreCase("iade") || (label.equalsIgnoreCase("player") && args.length > 0 && args[0].equalsIgnoreCase("all"))) {
            if (!p.hasPermission("loginx.admin")) return true;
            openMainGui(p);
            return true;
        }
        // ... Diğer ceza komutları (mevcut yapınla aynı kalabilir)
        return true;
    }

    // --- 1. ANA MENÜ (PLAYER HEADS) ---
    public void openMainGui(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, plugin.color("&#FF1493&lSVX NW &8» &#FFB6C1Oyuncu Kayıtları"));
        decorateGui(inv);

        if (deathsConfig.getConfigurationSection("stats") != null) {
            for (String uuidStr : deathsConfig.getConfigurationSection("stats").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setOwningPlayer(op);
                meta.setDisplayName(plugin.color("&#FF69B4" + op.getName()));
                
                List<String> lore = new ArrayList<>();
                lore.add("&#7&m---------------------------");
                lore.add(plugin.color("  &#FFB6C1Toplam Ölüm: &f" + deathsConfig.getInt("stats." + uuidStr + ".total")));
                lore.add(plugin.color("  &#FFB6C1Son Ölüm: &7" + deathsConfig.getString("stats." + uuidStr + ".last")));
                lore.add("");
                lore.add(plugin.color("  &#00FF00» &#fSağ tıkla detayları gör"));
                lore.add("&#7&m---------------------------");
                meta.setLore(lore);
                head.setItemMeta(meta);
                inv.addItem(head);
            }
        }
        p.openInventory(inv);
    }

    // --- 2. ÖLÜM LİSTESİ MENÜSÜ ---
    public void openDeathList(Player admin, String targetName) {
        UUID uuid = Bukkit.getOfflinePlayer(targetName).getUniqueId();
        Inventory inv = Bukkit.createInventory(null, 54, plugin.color("&#FF1493" + targetName + " &8» &#FFB6C1Kayıtlar"));
        decorateGui(inv);

        if (deathsConfig.getConfigurationSection("deaths." + uuid) != null) {
            for (String timestamp : deathsConfig.getConfigurationSection("deaths." + uuid).getKeys(false)) {
                String path = "deaths." + uuid + "." + timestamp;
                ItemStack chest = new ItemStack(Material.CHEST);
                ItemMeta meta = chest.getItemMeta();
                meta.setDisplayName(plugin.color("&#FF69B4Kayıt: " + timestamp));
                
                List<String> lore = new ArrayList<>();
                lore.add(plugin.color("&7&m----------------------------"));
                lore.add(plugin.color(" &#FFB6C1Tarih: &f" + deathsConfig.getString(path + ".date")));
                lore.add(plugin.color(" &#FFB6C1Dünya: &f" + deathsConfig.getString(path + ".world")));
                lore.add(plugin.color(" &#FFB6C1Konum: &7" + deathsConfig.getVector(path + ".loc").toString()));
                lore.add("");
                lore.add(plugin.color(" &#00FF00[SOL TIK] &fİade Et"));
                lore.add(plugin.color(" &#FFB6C1[SAĞ TIK] &fİçeriği Gör"));
                lore.add(plugin.color("&7&m----------------------------"));
                meta.setLore(lore);
                chest.setItemMeta(meta);
                inv.addItem(chest);
            }
        }
        addSymmetricControls(inv);
        admin.openInventory(inv);
    }

    // --- 3. İÇERİK ÖNİZLEME MENÜSÜ ---
    public void openPreview(Player admin, String targetName, String timestamp) {
        UUID uuid = Bukkit.getOfflinePlayer(targetName).getUniqueId();
        Inventory inv = Bukkit.createInventory(null, 54, plugin.color("&#FF1493Önizleme &8» &#FFB6C1" + timestamp));
        
        ItemStack[] items = ((List<ItemStack>) deathsConfig.get("deaths." + uuid + "." + timestamp + ".items")).toArray(new ItemStack[0]);
        ItemStack[] armor = ((List<ItemStack>) deathsConfig.get("deaths." + uuid + "." + timestamp + ".armor")).toArray(new ItemStack[0]);

        inv.setContents(items);
        for(int i=0; i<armor.length; i++) inv.setItem(45+i, armor[i]);

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta m = back.getItemMeta(); m.setDisplayName(plugin.color("&#FF1493⬅ Geri Dön")); back.setItemMeta(m);
        inv.setItem(53, back);
        
        admin.openInventory(inv);
    }

    // --- EVENT HANDLER (GUI TIKLAMA) ---
    @EventHandler
    public void onGuiClick(InventoryClickEvent e) {
        if (e.getView().getTitle().contains("SVX NW") || e.getView().getTitle().contains("Kayıtlar") || e.getView().getTitle().contains("Önizleme")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;
            Player p = (Player) e.getWhoClicked();

            // Ana Menüden Oyuncu Seçimi
            if (e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                String target = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
                openDeathList(p, target);
            }

            // Ölüm Listesi İşlemleri
            if (e.getCurrentItem().getType() == Material.CHEST) {
                String ts = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace("Kayıt: ", "");
                String targetName = e.getView().getTitle().split(" ")[0];
                
                if (e.isLeftClick()) {
                    restoreItems(p, targetName, ts);
                } else {
                    openPreview(p, targetName, ts);
                }
            }

            // Alt Kontroller
            if (e.getSlot() == 49) p.sendMessage(plugin.color("&#FF1493[SVX] &fBu plugin SVX NW için özel geliştirilmiştir."));
            if (e.getSlot() == 53 && e.getCurrentItem().getType() == Material.ARROW) openMainGui(p);
        }
    }

    private void restoreItems(Player admin, String targetName, String ts) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) { admin.sendMessage(plugin.color("&cOyuncu aktif değil!")); return; }
        
        UUID uuid = target.getUniqueId();
        String path = "deaths." + uuid + "." + ts;
        
        List<ItemStack> items = (List<ItemStack>) deathsConfig.get(path + ".items");
        for (ItemStack is : items) {
            if (is != null) {
                if (target.getInventory().firstEmpty() == -1) target.getWorld().dropItemNaturally(target.getLocation(), is);
                else target.getInventory().addItem(is);
            }
        }
        
        target.getWorld().spawnParticle(Particle.WITCH, target.getLocation().add(0, 1, 0), 50);
        target.playSound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
        admin.sendMessage(plugin.color("&#00FF00Eşyalar iade edildi!"));
        
        deathsConfig.set(path, null);
        saveDeaths();
        admin.closeInventory();
    }

    // --- YARDIMCI METOTLAR ---
    private void decorateGui(Inventory inv) {
        ItemStack pane = new ItemStack(Material.PINK_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta(); m.setDisplayName(" "); pane.setItemMeta(m);
        int[] rows = {0,1,2,3,4,5,6,7,8,45,46,47,48,49,50,51,52,53};
        for (int i : rows) inv.setItem(i, pane);
    }

    private void addSymmetricControls(Inventory inv) {
        // Sol: Işınlanma (Ender Chest)
        ItemStack tp = new ItemStack(Material.ENDER_CHEST);
        ItemMeta m1 = tp.getItemMeta();
        m1.setDisplayName(plugin.color("&#FF1493Ölüm Konumuna Git"));
        m1.setLore(Arrays.asList(plugin.color("&7Tıklayınca son ölüm noktasına efektle ışınlar.")));
        tp.setItemMeta(m1);
        inv.setItem(48, tp);

        // Orta: Bilgi (Book)
        ItemStack info = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta m2 = info.getItemMeta();
        m2.setDisplayName(plugin.color("&#FF69B4İade Sistemi Bilgi"));
        m2.setLore(Arrays.asList(plugin.color("&#FFB6C1Bu sistem yetkililerin veri kaybını önlemesi içindir.")));
        info.setItemMeta(m2);
        inv.setItem(49, info);

        // Sağ: Yenile/İstatistik (Nether Star)
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta m3 = star.getItemMeta();
        m3.setDisplayName(plugin.color("&#FF1493Kayıtları Yenile"));
        star.setItemMeta(m3);
        inv.setItem(50, star);
    }

    private void saveDeaths() {
        try { deathsConfig.save(deathsFile); } catch (IOException e) { e.printStackTrace(); }
    }

    // ... (Mevcut Scoreboard ve diğer yardımcı metotların buraya devam edecek)
            }
