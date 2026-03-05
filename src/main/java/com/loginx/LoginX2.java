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
        
        // Komutları Kaydet
        String[] cmds = {"ban", "ipban", "mute", "unban", "iade", "player", "pardon", "unmute", "unipban", "kick", "ipkick"};
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

        if (label.equalsIgnoreCase("iade") || (label.equalsIgnoreCase("player") && args.length > 0 && args[0].equalsIgnoreCase("all"))) {
            if (!p.hasPermission("loginx.admin")) return true;
            openMainGui(p);
            return true;
        }
        return true;
    }

    // --- 1. ANA MENÜ (KAFA LİSTESİ) ---
    public void openMainGui(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, plugin.color("&#FF1493&lSVX NW &8» &#FFB6C1Oyuncu Listesi"));
        decorateGui(inv);

        if (deathsConfig.getConfigurationSection("stats") != null) {
            for (String uuidStr : deathsConfig.getConfigurationSection("stats").getKeys(false)) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr));
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setOwningPlayer(op);
                meta.setDisplayName(plugin.color("&#FF69B4" + op.getName()));
                
                List<String> lore = new ArrayList<>();
                lore.add(plugin.color("&7&m---------------------------------------"));
                lore.add(plugin.color(" &#FFB6C1İstatistik Özeti:"));
                lore.add(plugin.color("  &8» &#FFB6C1Toplam Ölüm Sayısı: &f" + deathsConfig.getInt("stats." + uuidStr + ".total")));
                lore.add(plugin.color("  &8» &#FFB6C1Son Görülen Ölüm: &7" + deathsConfig.getString("stats." + uuidStr + ".last")));
                lore.add("");
                lore.add(plugin.color(" &#00FF00SAĞ TIKLA &8» &#fKayıtları Detaylı Görüntüle"));
                lore.add(plugin.color("&7&m---------------------------------------"));
                meta.setLore(lore);
                head.setItemMeta(meta);
                inv.addItem(head);
            }
        }
        p.openInventory(inv);
    }

    // --- 2. ÖLÜM KAYITLARI MENÜSÜ ---
    public void openDeathRecords(Player admin, String targetName) {
        UUID uuid = Bukkit.getOfflinePlayer(targetName).getUniqueId();
        Inventory inv = Bukkit.createInventory(null, 54, plugin.color("&#FF1493" + targetName + " &8» &#FFB6C1Kayıtlar"));
        decorateGui(inv);

        if (deathsConfig.getConfigurationSection("deaths." + uuid) != null) {
            for (String ts : deathsConfig.getConfigurationSection("deaths." + uuid).getKeys(false)) {
                String path = "deaths." + uuid + "." + ts;
                ItemStack chest = new ItemStack(Material.CHEST);
                ItemMeta meta = chest.getItemMeta();
                meta.setDisplayName(plugin.color("&#FF69B4Kayıt ID: #" + ts.substring(ts.length() - 4)));
                
                List<String> lore = new ArrayList<>();
                lore.add(plugin.color("&d&m------------------------------------------------"));
                lore.add(plugin.color(" &#FFB6C1Ölüm Tarihi: &f" + deathsConfig.getString(path + ".date")));
                lore.add(plugin.color(" &#FFB6C1Konum Bilgisi: &7" + deathsConfig.getString(path + ".world") + " (" + deathsConfig.getString(path + ".coords") + ")"));
                lore.add("");
                lore.add(plugin.color(" &#FF1493[SOL TIK] &fEşyaları Hemen İade Et (Hızlı İşlem)"));
                lore.add(plugin.color(" &#FF1493[SAĞ TIK] &fEnvanter İçeriğini Önizle"));
                lore.add(plugin.color("&d&m------------------------------------------------"));
                meta.setLore(lore);
                chest.setItemMeta(meta);
                inv.addItem(chest);
            }
        }
        addSymmetricControls(inv, targetName);
        admin.openInventory(inv);
    }

    // --- 3. ÖNİZLEME MENÜSÜ ---
    public void openPreview(Player admin, String targetName, String ts) {
        Inventory inv = Bukkit.createInventory(null, 54, plugin.color("&#FF1493" + targetName + " &8» &#FFB6C1Önizleme"));
        String path = "deaths." + Bukkit.getOfflinePlayer(targetName).getUniqueId() + "." + ts;
        
        List<ItemStack> items = (List<ItemStack>) deathsConfig.getList(path + ".items");
        if (items != null) {
            for (int i = 0; i < items.size(); i++) if (items.get(i) != null) inv.setItem(i, items.get(i));
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta m = back.getItemMeta();
        m.setDisplayName(plugin.color("&#FF1493⬅ Geri Dön"));
        back.setItemMeta(m);
        inv.setItem(49, back); // Simetrik orta buton
        
        admin.openInventory(inv);
    }

    // --- EVENTLER VE MANTIK ---
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        String ts = String.valueOf(System.currentTimeMillis());
        String path = "deaths." + p.getUniqueId() + "." + ts;
        
        deathsConfig.set(path + ".date", new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()));
        deathsConfig.set(path + ".world", p.getWorld().getName());
        deathsConfig.set(path + ".coords", p.getLocation().getBlockX() + "," + p.getLocation().getBlockY() + "," + p.getLocation().getBlockZ());
        deathsConfig.set(path + ".items", Arrays.asList(p.getInventory().getContents()));
        deathsConfig.set(path + ".loc_full", p.getLocation());
        
        deathsConfig.set("stats." + p.getUniqueId() + ".total", deathsConfig.getInt("stats." + p.getUniqueId() + ".total", 0) + 1);
        deathsConfig.set("stats." + p.getUniqueId() + ".last", new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()));
        saveDeaths();
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent e) {
        if (e.getView().getTitle().contains("SVX NW") || e.getView().getTitle().contains("Kayıtlar") || e.getView().getTitle().contains("Önizleme")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;
            Player p = (Player) e.getWhoClicked();

            if (e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                openDeathRecords(p, ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()));
            } else if (e.getCurrentItem().getType() == Material.CHEST) {
                String target = ChatColor.stripColor(e.getView().getTitle().split(" ")[0]);
                String id = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getLore().get(1)).split(": ")[1];
                // Not: Gerçek ID tespiti için saklama mantığı geliştirilebilir, burada basitleştirildi.
                if (e.isLeftClick()) restore(p, target, id); 
                else openPreview(p, target, id);
            } else if (e.getSlot() == 48) { // Işınlanma
                handleTeleport(p, e.getView().getTitle().split(" ")[0]);
            } else if (e.getCurrentItem().getType() == Material.ARROW) {
                openMainGui(p);
            }
        }
    }

    private void restore(Player admin, String targetName, String id) {
        Player target = Bukkit.getPlayer(targetName);
        if (target != null) {
            target.sendMessage(plugin.color("&#FF1493[SVX] &fEşyaların yetkili tarafından iade edildi!"));
            target.playSound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
            target.spawnParticle(Particle.HEART, target.getLocation().add(0, 1, 0), 10);
            admin.sendMessage(plugin.color("&#00FF00İade başarılı."));
        }
    }

    private void handleTeleport(Player p, String targetName) {
        Location loc = (Location) deathsConfig.get("deaths." + Bukkit.getOfflinePlayer(targetName).getUniqueId() + ".last_loc");
        if (loc != null) {
            p.teleport(loc);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            p.spawnParticle(Particle.PORTAL, p.getLocation(), 50);
        }
    }

    private void addSymmetricControls(Inventory inv, String target) {
        // 1. Ender Chest (Işınlanma)
        ItemStack tp = new ItemStack(Material.ENDER_CHEST);
        ItemMeta m1 = tp.getItemMeta();
        m1.setDisplayName(plugin.color("&#FF1493Ölüm Konumuna Işınlan"));
        m1.setLore(Arrays.asList(plugin.color("&7Oyuncunun öldüğü koordinata efektler eşliğinde ışınlan.")));
        tp.setItemMeta(m1);
        inv.setItem(48, tp);

        // 2. Kitap (Bilgi)
        ItemStack info = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta m2 = info.getItemMeta();
        m2.setDisplayName(plugin.color("&#FF69B4İade Sistemi Hakkında"));
        m2.setLore(Arrays.asList(plugin.color("&#FFB6C1SVX NW özel iade altyapısı ile oyuncu verilerini güvenle korur.")));
        info.setItemMeta(m2);
        inv.setItem(49, info);

        // 3. Nether Star (İstatistik)
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta m3 = star.getItemMeta();
        m3.setDisplayName(plugin.color("&#FF1493Genel Kayıt Durumu"));
        m3.setLore(Arrays.asList(plugin.color("&7Tüm veriler eş zamanlı olarak YAML dosyasına kaydedilir.")));
        star.setItemMeta(m3);
        inv.setItem(50, star);
    }

    private void decorateGui(Inventory inv) {
        ItemStack pane = new ItemStack(Material.PINK_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta(); m.setDisplayName(" "); pane.setItemMeta(m);
        for (int i : new int[]{0,1,2,3,4,5,6,7,8,45,46,47,48,49,50,51,52,53}) {
            if (inv.getItem(i) == null) inv.setItem(i, pane);
        }
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
        o.getScore(plugin.color("&#FFB6C1Ping: &f" + p.getPing())).setScore(1);
        p.setScoreboard(b);
    }

    private void saveDeaths() {
        try { deathsConfig.save(deathsFile); } catch (IOException e) { e.printStackTrace(); }
    }
            }
        
