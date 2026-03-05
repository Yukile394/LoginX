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
                    plugin.color(" "),
                    plugin.color(" &#FFB6C1Sistem Durumu: &aAktif Veri Bulundu &8(YAML)"),
                    plugin.color(" &#FFB6C1Gerçekleşen Ölüm: &f" + deathsConfig.getInt("stats." + uuidStr + ".total") + " Adet Kayıt"),
                    plugin.color(" &#FFB6C1Son Ölüm Zamanı: &7" + deathsConfig.getString("stats." + uuidStr + ".last")),
                    plugin.color(" "),
                    plugin.color(" &#FFB6C1Açıklama: &fBu oyuncunun geçmişteki tüm ölüm"),
                    plugin.color(" &#FFB6C1kayıtlarına, eşyalarına ve lokasyonlarına"),
                    plugin.color(" &#FFB6C1buradan güvenli bir şekilde ulaşabilirsiniz."),
                    plugin.color(" "),
                    plugin.color(" &#00FF00[SAĞ TIKLA] &fÖlüm Kayıtlarını Görüntüle"),
                    plugin.color(" "),
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
                    plugin.color(" "),
                    plugin.color(" &#FFB6C1Kayıt Tarihi: &f" + deathsConfig.getString(path + ".date")),
                    plugin.color(" &#FFB6C1Ölüm Bölgesi: &7" + deathsConfig.getString(path + ".world") + " &8(&f" + deathsConfig.getString(path + ".coords") + "&8)"),
                    plugin.color(" "),
                    plugin.color(" &#FFB6C1Durum: &fİade İşlemi İçin Hazır &8(ID: " + ts + ")"),
                    plugin.color(" &#FFB6C1Uyarı: &7Eşyalar iade edildikten sonra silinecektir."),
                    plugin.color(" "),
                    plugin.color(" &#FF1493[SOL TIK] &fEşyaları Hemen Geri Ver"),
                    plugin.color(" &#FF1493[SAĞ TIK] &fSandık İçeriğine Göz At"),
                    plugin.color(" "),
                    plugin.color("&d&m----------------------------------------------------")
                ));
                chest.setItemMeta(meta);
                inv.addItem(chest);
            }
        }
        addSymmetricControls(inv, targetName);
        admin.openInventory(inv);
    }

    // --- 3. İÇERİK ÖNİZLEME (EŞYALARI LİSTELER) ---
    public void openPreview(Player admin, String targetName, String ts) {
        Inventory inv = Bukkit.createInventory(null, 54, plugin.color("&#FF1493" + targetName + " &8» &#FFB6C1Eşyalar"));
        String path = "deaths." + Bukkit.getOfflinePlayer(targetName).getUniqueId() + "." + ts + ".items";
        
        // Eşyaları SLOT Numarasına göre okuma (Kesin Çözüm)
        if (deathsConfig.getConfigurationSection(path) != null) {
            for (String slotStr : deathsConfig.getConfigurationSection(path).getKeys(false)) {
                try {
                    int slot = Integer.parseInt(slotStr);
                    ItemStack item = deathsConfig.getItemStack(path + "." + slotStr);
                    if (item != null && slot < 45) {
                        inv.setItem(slot, item);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta m = back.getItemMeta();
        m.setDisplayName(plugin.color("&#FF1493⬅ Geri Dön"));
        m.setLore(Arrays.asList(plugin.color("&7Kayıt arşivi sayfasına dönmek için tıkla.")));
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
        deathsConfig.set(path + ".location", p.getLocation());
        
        // EŞYALARI SLOT BAZLI KAYDETME (Boş menü sorununu çözer)
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && contents[i].getType() != Material.AIR) {
                deathsConfig.set(path + ".items." + i, contents[i]);
            }
        }
        
        deathsConfig.set("stats." + p.getUniqueId() + ".total", deathsConfig.getInt("stats." + p.getUniqueId() + ".total", 0) + 1);
        deathsConfig.set("stats." + p.getUniqueId() + ".last", new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()));
        deathsConfig.set("stats." + p.getUniqueId() + ".last_loc", p.getLocation());
        saveDeaths();
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        if (title.contains("SVX NW") || title.contains("Kayıt Arşivi") || title.contains("Eşyalar")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
            Player admin = (Player) e.getWhoClicked();
            String targetName = title.split(" ")[0]; // Başlıktan ismi çeker

            if (e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                String headName = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace(" Bilgileri", "");
                openDeathRecords(admin, headName);
            } 
            else if (e.getCurrentItem().getType() == Material.CHEST && title.contains("Kayıt Arşivi")) {
                // ID'yi 5. satırdan güvenli şekilde ayıklar
                String idLine = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getLore().get(5));
                String ts = idLine.substring(idLine.indexOf("ID: ") + 4, idLine.length() - 1);
                
                if (e.isLeftClick()) handleRestore(admin, targetName, ts);
                else openPreview(admin, targetName, ts);
            } 
            else if (e.getSlot() == 48) { 
                handleTeleport(admin, targetName);
            }
            else if (e.getSlot() == 49 && e.getCurrentItem().getType() == Material.ARROW) {
                openDeathRecords(admin, targetName);
            }
        }
    }

    private void handleRestore(Player admin, String targetName, String ts) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) { 
            admin.sendMessage(plugin.color("&#FF1493[SVX NW] &cOyuncu şu anda sunucuda aktif değil!")); 
            return; 
        }
        
        String path = "deaths." + target.getUniqueId() + "." + ts;
        String itemsPath = path + ".items";
        
        if (deathsConfig.getConfigurationSection(itemsPath) != null) {
            for (String slotStr : deathsConfig.getConfigurationSection(itemsPath).getKeys(false)) {
                ItemStack item = deathsConfig.getItemStack(itemsPath + "." + slotStr);
                if (item != null) {
                    if (target.getInventory().firstEmpty() == -1) {
                        target.getWorld().dropItemNaturally(target.getLocation(), item);
                    } else {
                        target.getInventory().addItem(item);
                    }
                }
            }
            
            target.sendMessage(plugin.color("&#FF1493[SVX NW] &fEşyaların yetkili tarafından başarıyla iade edildi!"));
            target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            target.spawnParticle(Particle.HEART, target.getLocation().add(0, 1.5, 0), 15);
            
            deathsConfig.set(path, null); // Kaydı sil
            saveDeaths();
            admin.sendMessage(plugin.color("&#FF1493[SVX NW] &#00FF00İade işlemi başarıyla tamamlandı."));
            admin.closeInventory();
        } else {
            admin.sendMessage(plugin.color("&#FF1493[SVX NW] &cBu kayda ait eşya verisi bulunamadı!"));
        }
    }

    private void handleTeleport(Player admin, String targetName) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
        Location loc = deathsConfig.getLocation("stats." + op.getUniqueId() + ".last_loc");
        
        if (loc != null) {
            admin.teleport(loc);
            admin.playSound(admin.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            admin.spawnParticle(Particle.PORTAL, admin.getLocation(), 100, 0.5, 1, 0.5);
            admin.sendMessage(plugin.color("&#FF1493[SVX NW] &fOyuncunun son ölüm konumuna ışınlandınız!"));
        } else {
            admin.sendMessage(plugin.color("&#FF1493[SVX NW] &cKayıtlı bir konum bulunamadı!"));
        }
    }

    private void addSymmetricControls(Inventory inv, String target) {
        ItemStack tp = new ItemStack(Material.ENDER_CHEST);
        ItemMeta m1 = tp.getItemMeta();
        m1.setDisplayName(plugin.color("&#FF1493Son Ölüm Konumuna Işınlan"));
        m1.setLore(Arrays.asList(
            plugin.color("&7Oyuncunun ölmeden hemen önceki son"),
            plugin.color("&7koordinatlarına ender partikülleri"),
            plugin.color("&7eşliğinde hızlıca ışınlanın."),
            plugin.color(" "),
            plugin.color("&#00FF00[TIKLA VE IŞINLAN]")
        ));
        tp.setItemMeta(m1);
        inv.setItem(48, tp);

        ItemStack info = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta m2 = info.getItemMeta();
        m2.setDisplayName(plugin.color("&#FF69B4İade Sistemi Rehberi"));
        m2.setLore(Arrays.asList(
            plugin.color("&#FFB6C1Eşyalar iade edildiğinde oyuncunun"),
            plugin.color("&#FFB6C1envanteri doluysa yere düşürülür."),
            plugin.color("&#FFB6C1Tüm işlemler sistemde kayıt altındadır.")
        ));
        info.setItemMeta(m2);
        inv.setItem(49, info);

        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta m3 = star.getItemMeta();
        m3.setDisplayName(plugin.color("&#FF1493Veritabanı Analizi"));
        m3.setLore(Arrays.asList(
            plugin.color("&7Tarih sırasına göre tüm kayıtlar"),
            plugin.color("&7listelenmiştir. Hatalı ölümleri"),
            plugin.color("&7telafi etmek için kullanılır."),
            plugin.color(" "),
            plugin.color("&#FFB6C1Sistem: &aAktif Senkronizasyon")
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
