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
        
        // Komut Kayıtları
        String[] cmds = {"ban", "ipban", "mute", "unban", "iade", "player", "pardon", "unmute", "unipban", "kick", "ipkick"};
        for (String c : cmds) {
            PluginCommand pc = plugin.getCommand(c);
            if (pc != null) pc.setExecutor(this);
        }
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startScoreboardTask(); // Hataya sebep olan kısım burasıydı, metot aşağıya eklendi.
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
        if (!(sender instanceof Player) && !label.equalsIgnoreCase("ban")) return true;
        
        // İade ve Oyuncu Menüsü
        if (label.equalsIgnoreCase("iade") || (label.equalsIgnoreCase("player") && args.length > 0 && args[0].equalsIgnoreCase("all"))) {
            if (!sender.hasPermission("loginx.admin")) return true;
            openMainGui((Player) sender);
            return true;
        }

        // Ceza Komutları Mantığı
        if (!sender.hasPermission("loginx.admin")) return true;
        if (args.length < 1) { sender.sendMessage(plugin.color("&#FF69B4Kullanım: /" + label + " <oyuncu> [süre] [sebep]")); return true; }

        // (Ceza uygulama mantığı buraya gelecek - Mevcut yapını koruyabilirsin)
        return true;
    }

    // --- SCOREBOARD VE TAB SİSTEMİ ---
    private void startScoreboardTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateScoreboard(p);
                    p.setPlayerListHeaderFooter(
                        plugin.color("\n&#FF1493&lSVX NW NETWORK\n&#FFB6C1Keyifli Oyunlar Dileriz!\n"),
                        plugin.color("\n&#FF69B4www.svxnw.com\n&#FFB6C1discord.gg/svxnw\n")
                    );
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void updateScoreboard(Player p) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("svxnw", "dummy", plugin.color("&#FF1493&lSVX NW"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        String[] lines = {
            "&7&m------------------",
            "&#FFB6C1İSİM &8» &f" + p.getName(),
            "&#FFB6C1RÜTBE &8» &fOyuncu",
            "&#FFB6C1KLAN &8» &cX",
            " ",
            "&#FFB6C1PING &8» &f" + getPing(p) + " ms",
            "&#FFB6C1ÇEVRİMİÇİ &8» &f" + Bukkit.getOnlinePlayers().size(),
            "&7&m------------------ ",
            "&#FF69B4www.svxnw.com"
        };

        int score = lines.length;
        for (String line : lines) {
            obj.getScore(plugin.color(line)).setScore(score--);
        }
        p.setScoreboard(board);
    }

    private int getPing(Player p) {
        try { return p.getPing(); } catch (Exception e) { return 0; }
    }

    // --- ÖLÜM KAYIT SİSTEMİ (İADE) ---
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        String path = "deaths." + p.getUniqueId() + "." + System.currentTimeMillis();
        
        deathsConfig.set(path + ".date", new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()));
        deathsConfig.set(path + ".world", p.getWorld().getName());
        deathsConfig.set(path + ".loc", p.getLocation().toVector());
        deathsConfig.set(path + ".items", Arrays.asList(p.getInventory().getContents()));
        deathsConfig.set(path + ".armor", Arrays.asList(p.getInventory().getArmorContents()));
        
        int total = deathsConfig.getInt("stats." + p.getUniqueId() + ".total", 0);
        deathsConfig.set("stats." + p.getUniqueId() + ".total", total + 1);
        deathsConfig.set("stats." + p.getUniqueId() + ".last", new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()));
        
        saveDeaths();
    }

    public void openMainGui(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, plugin.color("&#FF1493&lSVX NW &8» &#FFB6C1Oyuncular"));
        decorateGui(inv);

        if (deathsConfig.getConfigurationSection("stats") != null) {
            for (String uuidStr : deathsConfig.getConfigurationSection("stats").getKeys(false)) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr));
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setOwningPlayer(op);
                meta.setDisplayName(plugin.color("&#FF69B4" + op.getName()));
                meta.setLore(Arrays.asList(
                    plugin.color("&7&m---------------------------"),
                    plugin.color("  &#FFB6C1Toplam Ölüm: &f" + deathsConfig.getInt("stats." + uuidStr + ".total")),
                    plugin.color("  &#FFB6C1Son Ölüm: &7" + deathsConfig.getString("stats." + uuidStr + ".last")),
                    "",
                    plugin.color("  &#00FF00» &#fSağ tıkla detayları gör"),
                    plugin.color("&7&m---------------------------")
                ));
                head.setItemMeta(meta);
                inv.addItem(head);
            }
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent e) {
        if (e.getView().getTitle().contains("SVX NW")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
            Player p = (Player) e.getWhoClicked();

            if (e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                String target = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
                p.sendMessage(plugin.color("&#FF1493[SVX] &f" + target + " adlı oyuncunun kayıtları açılıyor..."));
                // Buraya openDeathList metodu eklenebilir.
            }
        }
    }

    private void decorateGui(Inventory inv) {
        ItemStack pane = new ItemStack(Material.PINK_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta(); m.setDisplayName(" "); pane.setItemMeta(m);
        for (int i : new int[]{0,1,2,3,4,5,6,7,8,45,46,47,48,49,50,51,52,53}) inv.setItem(i, pane);
    }

    private void saveDeaths() {
        try { deathsConfig.save(deathsFile); } catch (IOException e) { e.printStackTrace(); }
    }
}
