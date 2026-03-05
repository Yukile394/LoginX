package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;
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
        
        String[] cmds = {"iade", "player"};
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

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        if (!p.hasPermission("loginx.admin")) return true;

        if (label.equalsIgnoreCase("iade") || (label.equalsIgnoreCase("player") && args.length > 0 && args[0].equalsIgnoreCase("all"))) {
            openMainGui(p);
            return true;
        }
        return true;
    }

    // --- 1. ANA MENÜ (KAFA LİSTESİ) ---
    public void openMainGui(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, plugin.color("&#FF1493&lSVX NW &8» &#FFB6C1Oyuncu Veritabanı"));
        decorateGui(inv);

        if (deathsConfig.getConfigurationSection("stats") != null) {
            for (String uuidStr : deathsConfig.getConfigurationSection("stats").getKeys(false)) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr));
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setOwningPlayer(op);
                meta.setDisplayName(plugin.color("&#FF69B4" + op.getName() + " &fBilgileri"));
                
                meta.setLore(Arrays.asList(
                    plugin.color("&d&m----------------------------------------------------"),
                    plugin.color(" &#FFB6C1Sistem Kayıt Durumu: &aAktif Veri Mevcut"),
                    plugin.color(" &#FFB6C1Toplam Gerçekleşen Ölüm: &f" + deathsConfig.getInt("stats." + uuidStr + ".total") + " Adet"),
                    plugin.color(" &#FFB6C1Son Ölüm Zamanı: &7" + deathsConfig.getString("stats." + uuidStr + ".last")),
                    plugin.color(" "),
                    plugin.color(" &#FFB6C1Açıklama: &fBu oyuncuya ait tüm envanter geçmişini"),
                    plugin.color(" &#FFB6C1ve ölüm koordinatlarını alt menüden görebilirsiniz."),
                    plugin.color(" "),
                    plugin.color(" &#00FF00SAĞ TIKLA &8» &#fÖlüm Kayıtlarını Listele"),
                    plugin.color("&d&m----------------------------------------------------")
                ));
                head.setItemMeta(meta);
                inv.addItem(head);
            }
        }
        p.openInventory(inv);
    }

    // --- 2. ÖLÜM KAYITLARI LİSTESİ ---
    public void openDeathRecords(Player admin, String targetName) {
        UUID uuid = Bukkit.getOfflinePlayer(targetName).getUniqueId();
        Inventory inv = Bukkit.createInventory(null, 54, plugin.color("&#FF1493" + targetName + " &8» &#FFB6C1Kayıt Arşivi"));
        decorateGui(inv);

        if (deathsConfig.getConfigurationSection("deaths." + uuid) != null) {
            for (String ts : deathsConfig.getConfigurationSection("deaths." + uuid).getKeys(false)) {
                String path = "deaths." + uuid + "." + ts;
                ItemStack chest = new ItemStack(Material.CHEST);
                ItemMeta meta = chest.getItemMeta();
                meta.setDisplayName(plugin.color("&#FF69B4Ölüm Kaydı &f#" + ts.substring(ts.length() - 5)));
                
                meta.setLore(Arrays.asList(
                    plugin.color("&d&m----------------------------------------------------"),
                    plugin.color(" &#FFB6C1Kayıt Tarihi: &f" + deathsConfig.getString(path + ".date")),
                    plugin.color(" &#FFB6C1Ölüm Bölgesi: &7" + deathsConfig.getString(path + ".world") + " &8(&f" + deathsConfig.getString(path + ".coords") + "&8)"),
                    plugin.color(" "),
                    plugin.color(" &#FFB6C1Durum: &fİade Edilmeye Hazır &8(ID: " + ts + ")"),
                    plugin.color(" &#FFB6C1Uyarı: &7İade işlemi yapıldığında bu kayıt silinir."),
                    plugin.color(" "),
                    plugin.color(" &#FF1493[SOL TIK] &fEnvanteri Hemen Geri Ver"),
                    plugin.color(" &#FF1493[SAĞ TIK] &fSandık İçeriğine Göz At"),
                    plugin.color("&d&m----------------------------------------------------")
                ));
                chest.setItemMeta(meta);
                inv.addItem(chest);
            }
        }
        addSymmetricControls(inv, targetName);
        admin.openInventory(inv);
    }

    // --- 3. İÇERİK ÖNİZLEME ---
    public void openPreview(Player admin, String targetName, String ts) {
        Inventory inv = Bukkit.createInventory(null, 54, plugin.color("&#FF1493" + targetName + " &8» &#FFB6C1Eşyalar"));
        String path = "deaths." + Bukkit.getOfflinePlayer(targetName).getUniqueId() + "." + ts;
        
        List<ItemStack> items = (List<ItemStack>) deathsConfig.get(path + ".items");
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) != null) inv.setItem(i, items.get(i));
            }
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta m = back.getItemMeta();
        m.setDisplayName(plugin.color("&#FF1493⬅ Geri Dön"));
        m.setLore(Arrays.asList(plugin.color("&7Kayıt listesine geri dönmek için tıkla.")));
        back.setItemMeta(m);
        inv.setItem(49, back); 
        
        admin.openInventory(inv);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        String ts = String.valueOf(System.currentTimeMillis());
        String path = "deaths." + p.getUniqueId() + "." + ts;
        
        deathsConfig.set(path + ".date", new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()));
        deathsConfig.set(path + ".world", p.getWorld().getName());
        deathsConfig.set(path + ".coords", p.getLocation().getBlockX() + "X " + p.getLocation().getBlockY() + "Y " + p.getLocation().getBlockZ() + "Z");
        deathsConfig.set(path + ".items", Arrays.asList(p.getInventory().getContents()));
        deathsConfig.set(path + ".location", p.getLocation()); // Işınlanma için tam obje
        
        deathsConfig.set("stats." + p.getUniqueId() + ".total", deathsConfig.getInt("stats." + p.getUniqueId() + ".total", 0) + 1);
        deathsConfig.set("stats." + p.getUniqueId() + ".last", new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()));
        deathsConfig.set("stats." + p.getUniqueId() + ".last_loc", p.getLocation());
        saveDeaths();
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent e) {
        if (e.getView().getTitle().contains("SVX NW")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
            Player p = (Player) e.getWhoClicked();

            if (e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                String target = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace(" Bilgileri", "");
                openDeathRecords(p, target);
            } 
            else if (e.getCurrentItem().getType() == Material.CHEST) {
                String target = ChatColor.stripColor(e.getView().getTitle().split(" ")[0]);
                String rawLore = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getLore().get(4));
                String ts = rawLore.substring(rawLore.indexOf("ID: ") + 4, rawLore.length() - 1);
                
                if (e.isLeftClick()) handleRestore(p, target, ts);
                else openPreview(p, target, ts);
            } 
            else if (e.getSlot() == 48) { 
                handleTeleport(p, ChatColor.stripColor(e.getView().getTitle().split(" ")[0]));
            }
            else if (e.getSlot() == 49 && e.getCurrentItem().getType() == Material.ARROW) {
                openMainGui(p);
            }
        }
    }

    private void handleRestore(Player admin, String targetName, String ts) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) { admin.sendMessage(plugin.color("&cOyuncu online değil!")); return; }
        
        String path = "deaths." + target.getUniqueId() + "." + ts;
        List<ItemStack> items = (List<ItemStack>) deathsConfig.get(path + ".items");
        
        if (items != null) {
            for (ItemStack item : items) {
                if (item != null) {
                    if (target.getInventory().firstEmpty() == -1) target.getWorld().dropItemNaturally(target.getLocation(), item);
                    else target.getInventory().addItem(item);
                }
            }
            target.sendMessage(plugin.color("&#FF1493[SVX NW] &fEşyaların yetkili tarafından başarıyla iade edildi!"));
            target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            target.spawnParticle(Particle.HAPPY_VILLAGER, target.getLocation().add(0, 1, 0), 30);
            
            deathsConfig.set(path, null);
            saveDeaths();
            admin.sendMessage(plugin.color("&#00FF00İade işlemi başarılı."));
            admin.closeInventory();
        }
    }

    private void handleTeleport(Player admin, String targetName) {
        Location loc = (Location) deathsConfig.get("stats." + Bukkit.getOfflinePlayer(targetName).getUniqueId() + ".last_loc");
        if (loc != null) {
            admin.teleport(loc);
            admin.playSound(admin.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            admin.spawnParticle(Particle.PORTAL, admin.getLocation(), 100, 0.5, 1, 0.5);
            admin.sendMessage(plugin.color("&#FF1493[SVX] &fÖlüm konumuna başarıyla ışınlandınız!"));
        } else {
            admin.sendMessage(plugin.color("&cIşınlanılacak konum bulunamadı!"));
        }
    }

    private void addSymmetricControls(Inventory inv, String target) {
        // 1. Ender Chest (Sol)
        ItemStack tp = new ItemStack(Material.ENDER_CHEST);
        ItemMeta m1 = tp.getItemMeta();
        m1.setDisplayName(plugin.color("&#FF1493Son Ölüm Konumuna Işınlan"));
        m1.setLore(Arrays.asList(
            plugin.color("&7Oyuncunun öldüğü son koordinatlara ender efektleri"),
            plugin.color("&7eşliğinde hızlıca ışınlanmanızı sağlayan sistemdir."),
            plugin.color(" "),
            plugin.color("&#00FF00TIKLA VE IŞINLAN")
        ));
        tp.setItemMeta(m1);
        inv.setItem(48, tp);

        // 2. Kitap (Orta)
        ItemStack info = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta m2 = info.getItemMeta();
        m2.setDisplayName(plugin.color("&#FF69B4SVX NW İade Sistemi Rehberi"));
        m2.setLore(Arrays.asList(
            plugin.color("&#FFB6C1Bu sistem sunucumuzdaki veri kayıplarını ve haksız ölümleri"),
            plugin.color("&#FFB6C1telafi etmek amacıyla özel RGB motoruyla kodlanmıştır."),
            plugin.color("&#FFB6C1Herhangi bir hata durumunda yönetime bildirmeyi unutmayın.")
        ));
        info.setItemMeta(m2);
        inv.setItem(49, info);

        // 3. Nether Star (Sağ)
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta m3 = star.getItemMeta();
        m3.setDisplayName(plugin.color("&#FF1493Veri Analiz ve Filtreleme"));
        m3.setLore(Arrays.asList(
            plugin.color("&7Şu an bu oyuncunun tüm geçmiş verilerini görmektesiniz."),
            plugin.color("&7Kayıtlar tarih sırasına göre otomatik olarak dizilir."),
            plugin.color(" "),
            plugin.color("&#FFB6C1Durum: &aVeriler Senkronize")
        ));
        star.setItemMeta(m3);
        inv.setItem(50, star);
    }

    private void decorateGui(Inventory inv) {
        ItemStack pane = new ItemStack(Material.PINK_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta(); m.setDisplayName(" "); pane.setItemMeta(m);
        int[] border = {0,1,2,3,4,5,6,7,8,45,46,47,51,52,53};
        for (int i : border) inv.setItem(i, pane);
    }

    private void startScoreboardTask() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) updateScoreboard(p);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void updateScoreboard(Player p) {
        Scoreboard b = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = b.registerNewObjective("svx", "dummy", plugin.color("&#FF1493&lSVX NW"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        o.getScore(plugin.color("&#FFB6C1Ping: &f" + p.getPing() + "ms")).setScore(1);
        p.setScoreboard(b);
    }

    private void saveDeaths() {
        try { deathsConfig.save(deathsFile); } catch (IOException e) { e.printStackTrace(); }
    }
            }
            
