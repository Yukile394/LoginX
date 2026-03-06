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
    private final Map<UUID, Long> clickCooldown = new HashMap<>();
    private final Map<UUID, Long> deathCooldown = new HashMap<>();

    public LoginX2(LoginX plugin) {
        this.plugin = plugin;
        setupFiles();
        
        String[] cmds = {"iade", "player"};
        for (String c : cmds) {
            PluginCommand pc = plugin.getCommand(c);
            if (pc != null) pc.setExecutor(this);
        }
        
        // Sunucuya /reload atıldığında eventlerin çift çalışmasını engeller
        HandlerList.unregisterAll(this);
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

    // --- 1. ANA MENÜ ---
    public void openMainGui(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, plugin.color("&#FF0000&lS&#FF1A40&lV&#FF3380&lX &#FF4DBF&lN&#FF66FF&lW &f» Oyuncu Veritabanı"));
        decorateGui(inv);

        if (deathsConfig.getConfigurationSection("stats") != null) {
            for (String uuidStr : deathsConfig.getConfigurationSection("stats").getKeys(false)) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr));
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setOwningPlayer(op);
                meta.setDisplayName(plugin.color("&#FF0000" + op.getName() + " &fBilgileri"));
                
                meta.setLore(Arrays.asList(
                    plugin.color("&f&m----------------------------------------------------"),
                    plugin.color(" "),
                    plugin.color(" &#FF3380Sistem Durumu: &aAktif Veri Bulundu"),
                    plugin.color(" &#FF3380Gerçekleşen Ölüm: &f" + deathsConfig.getInt("stats." + uuidStr + ".total") + " Adet"),
                    plugin.color(" &#FF3380Son Ölüm: &7" + deathsConfig.getString("stats." + uuidStr + ".last")),
                    plugin.color(" "),
                    plugin.color(" &#FF0000[SAĞ TIKLA] &fKayıtları Listele"),
                    plugin.color(" "),
                    plugin.color("&f&m----------------------------------------------------")
                ));
                head.setItemMeta(meta);
                inv.addItem(head);
            }
        }
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        p.openInventory(inv);
    }

    // --- 2. ÖLÜM KAYITLARI LİSTESİ ---
    public void openDeathRecords(Player admin, String targetName) {
        UUID uuid = Bukkit.getOfflinePlayer(targetName).getUniqueId();
        Inventory inv = Bukkit.createInventory(null, 54, plugin.color("&#FF0000" + targetName + " &f» &#FF3380Kayıt Arşivi"));
        decorateGui(inv);

        if (deathsConfig.getConfigurationSection("deaths." + uuid) != null) {
            List<String> timestamps = new ArrayList<>(deathsConfig.getConfigurationSection("deaths." + uuid).getKeys(false));
            timestamps.sort(Collections.reverseOrder());

            for (String ts : timestamps) {
                String path = "deaths." + uuid + "." + ts;
                ItemStack chest = new ItemStack(Material.CHEST);
                ItemMeta meta = chest.getItemMeta();
                meta.setDisplayName(plugin.color("&#FF0000Ölüm Kaydı &f#" + ts.substring(ts.length() - 5)));
                
                String cause = deathsConfig.getString(path + ".cause", "Bilinmeyen Sebep");
                String killer = deathsConfig.getString(path + ".killer", "Yok / Doğa");
                int level = deathsConfig.getInt(path + ".level", 0);
                int food = deathsConfig.getInt(path + ".food", 20);

                meta.setLore(Arrays.asList(
                    plugin.color("&f&m----------------------------------------------------"),
                    plugin.color(" "),
                    plugin.color(" &#FF3380Tarih: &f" + deathsConfig.getString(path + ".date")),
                    plugin.color(" &#FF3380Sebep: &7" + cause),
                    plugin.color(" &#FF3380Katil: &c" + killer),
                    plugin.color(" &#FF3380Bölge: &f" + deathsConfig.getString(path + ".world") + " &8(&f" + deathsConfig.getString(path + ".coords") + "&8)"),
                    plugin.color(" &#FF3380Kayıtlı XP / Açlık: &a" + level + " Lvl &8- &6" + food + " But"),
                    plugin.color(" "),
                    plugin.color(" &#FF3380Durum: &fİadeye Hazır &8(ID: " + ts + ")"),
                    plugin.color(" "),
                    plugin.color(" &#FF0000[SOL TIK] &fEşyaları Hemen Geri Ver"),
                    plugin.color(" &#FF0000[SAĞ TIK] &fİçeriğe Göz At"),
                    plugin.color(" "),
                    plugin.color("&f&m----------------------------------------------------")
                ));
                chest.setItemMeta(meta);
                inv.addItem(chest);
            }
        }
        addSymmetricControls(inv, targetName);
        admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        admin.openInventory(inv);
    }

    // --- 3. İÇERİK ÖNİZLEME ---
    public void openPreview(Player admin, String targetName, String ts) {
        Inventory inv = Bukkit.createInventory(null, 54, plugin.color("&#FF0000" + targetName + " &f» &#FF3380Eşyalar"));
        String path = "deaths." + Bukkit.getOfflinePlayer(targetName).getUniqueId() + "." + ts + ".items";
        
        if (deathsConfig.getConfigurationSection(path) != null) {
            for (String slotStr : deathsConfig.getConfigurationSection(path).getKeys(false)) {
                try {
                    int slot = Integer.parseInt(slotStr);
                    ItemStack item = deathsConfig.getItemStack(path + "." + slotStr);
                    if (item != null && slot < 45) inv.setItem(slot, item);
                } catch (NumberFormatException ignored) {}
            }
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta m = back.getItemMeta();
        m.setDisplayName(plugin.color("&#FF0000⬅ Geri Dön"));
        back.setItemMeta(m);
        inv.setItem(49, back); 
        admin.playSound(admin.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
        admin.openInventory(inv);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        UUID uuid = p.getUniqueId();

        // Çift Ölüm Kaydını Engelleme (2 Saniye Koruması)
        if (deathCooldown.containsKey(uuid) && System.currentTimeMillis() - deathCooldown.get(uuid) < 2000) return;
        deathCooldown.put(uuid, System.currentTimeMillis());

        String ts = String.valueOf(System.currentTimeMillis());
        String path = "deaths." + uuid + "." + ts;
        
        deathsConfig.set(path + ".date", new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()));
        deathsConfig.set(path + ".world", p.getWorld().getName());
        deathsConfig.set(path + ".coords", p.getLocation().getBlockX() + "X " + p.getLocation().getBlockY() + "Y " + p.getLocation().getBlockZ() + "Z");
        deathsConfig.set(path + ".location", p.getLocation());
        
        deathsConfig.set(path + ".cause", e.getDeathMessage() != null ? e.getDeathMessage() : "Bilinmiyor");
        deathsConfig.set(path + ".killer", p.getKiller() != null ? p.getKiller().getName() : "Yok");
        deathsConfig.set(path + ".level", p.getLevel());
        deathsConfig.set(path + ".food", p.getFoodLevel());
        
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && contents[i].getType() != Material.AIR) {
                deathsConfig.set(path + ".items." + i, contents[i]);
            }
        }
        
        deathsConfig.set("stats." + uuid + ".total", deathsConfig.getInt("stats." + uuid + ".total", 0) + 1);
        deathsConfig.set("stats." + uuid + ".last", new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()));
        deathsConfig.set("stats." + uuid + ".last_loc", p.getLocation());
        saveDeaths();
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        if (title.contains("SVX NW") || title.contains("Kayıt Arşivi") || title.contains("Eşyalar")) {
            e.setCancelled(true);
            
            // Çift Tıklamayı ve Alt Envanteri Engelleme
            if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
            
            Player admin = (Player) e.getWhoClicked();
            UUID uuid = admin.getUniqueId();

            // Tıklama Koruması (Yarım saniyede 1 tık)
            if (clickCooldown.containsKey(uuid) && System.currentTimeMillis() - clickCooldown.get(uuid) < 500) return;
            clickCooldown.put(uuid, System.currentTimeMillis());

            String targetName = title.split(" ")[0]; 

            if (e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                String headName = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).replace(" Bilgileri", "");
                openDeathRecords(admin, headName);
            } 
            else if (e.getCurrentItem().getType() == Material.CHEST && title.contains("Kayıt Arşivi")) {
                String idLine = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getLore().get(8)); // Lore satırı indexi (8)
                String ts = idLine.substring(idLine.indexOf("ID: ") + 4, idLine.length() - 1);
                
                if (e.isLeftClick()) handleRestore(admin, targetName, ts);
                else openPreview(admin, targetName, ts);
            } 
            else if (e.getSlot() == 48 && title.contains("Kayıt Arşivi")) { 
                handleTeleport(admin, targetName);
            }
            else if (e.getSlot() == 50 && e.getCurrentItem().getType() == Material.BARRIER && title.contains("Kayıt Arşivi")) {
                deathsConfig.set("deaths." + Bukkit.getOfflinePlayer(targetName).getUniqueId(), null);
                saveDeaths();
                admin.playSound(admin.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1f);
                admin.sendMessage(plugin.color("&#FF0000[SVX NW] &fOyuncuya ait tüm ölüm arşivi temizlendi."));
                admin.closeInventory();
            }
            else if (e.getSlot() == 49 && e.getCurrentItem().getType() == Material.ARROW) {
                openDeathRecords(admin, targetName);
            }
        }
    }

    private void handleRestore(Player admin, String targetName, String ts) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) { 
            admin.sendMessage(plugin.color("&#FF0000[SVX NW] &fOyuncu şu anda sunucuda aktif değil!")); 
            return; 
        }
        
        String path = "deaths." + target.getUniqueId() + "." + ts;
        String itemsPath = path + ".items";
        
        if (deathsConfig.getConfigurationSection(itemsPath) != null) {
            for (String slotStr : deathsConfig.getConfigurationSection(itemsPath).getKeys(false)) {
                int slot = Integer.parseInt(slotStr);
                ItemStack item = deathsConfig.getItemStack(itemsPath + "." + slotStr);
                
                if (item != null) {
                    ItemStack currentInSlot = target.getInventory().getItem(slot);
                    if (currentInSlot == null || currentInSlot.getType() == Material.AIR) {
                        target.getInventory().setItem(slot, item);
                    } else {
                        HashMap<Integer, ItemStack> leftOvers = target.getInventory().addItem(item);
                        if (!leftOvers.isEmpty()) {
                            target.getWorld().dropItemNaturally(target.getLocation(), leftOvers.get(0));
                        }
                    }
                }
            }
            
            int savedLevel = deathsConfig.getInt(path + ".level", 0);
            if (savedLevel > 0) target.setLevel(target.getLevel() + savedLevel);
            
            // --- YENİ GÖRSEL VE YAZILI EFEKTLER ---
            // 1. Oyuncunun kafasındaki HEART partikülü
            target.spawnParticle(Particle.HEART, target.getLocation().add(0, 1.5, 0), 20);
            
            // 2. Oyuncunun EKRANINA Kalp çıkması (Title)
            target.sendTitle(plugin.color("&#FF0000&l♥"), "", 10, 40, 10);
            
            // 3. EKRANDAKİ Kalbin altında SUBTITLE olarak adminin ismi (Açık Mavi RGB Karışık)
            new BukkitRunnable() {
                @Override public void run() {
                    target.sendTitle(plugin.color("&#FF0000&l♥"), plugin.color("&#33CCFF&lİade Yapan: &#66E0FF&l" + admin.getName()), 0, 40, 10);
                    target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                }
            }.runTaskLater(plugin, 5L); // 5 tick sonra subtitle'ı gönder (Title ile çakışmasın)

            deathsConfig.set(path, null);
            saveDeaths();
            
            // 4. Adminin ekranına (ACTIONBAR) Açık Mavi bildirim
            admin.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new net.md_5.bungee.api.chat.TextComponent(plugin.color("&#33CCFFİade başarıyla oyuncuya gönderildi!")));
            admin.closeInventory();
        }
    }

    private void handleTeleport(Player admin, String targetName) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
        Location loc = deathsConfig.getLocation("stats." + op.getUniqueId() + ".last_loc");
        
        if (loc != null) {
            admin.teleport(loc);
            admin.playSound(admin.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            admin.spawnParticle(Particle.PORTAL, admin.getLocation(), 100, 0.5, 1, 0.5);
            admin.sendMessage(plugin.color("&#FF0000[SVX NW] &fOyuncunun son ölüm konumuna ışınlandınız!"));
        } else {
            admin.sendMessage(plugin.color("&#FF0000[SVX NW] &fKayıtlı bir konum bulunamadı!"));
        }
    }

    private void addSymmetricControls(Inventory inv, String target) {
        ItemStack tp = new ItemStack(Material.ENDER_CHEST);
        ItemMeta m1 = tp.getItemMeta();
        m1.setDisplayName(plugin.color("&#FF0000Son Konuma Işınlan"));
        m1.setLore(Arrays.asList(
            plugin.color("&fOyuncunun ölmeden hemen önceki"),
            plugin.color("&fkoordinatlarına ışınlanın."),
            plugin.color(" "),
            plugin.color("&#FF3380[TIKLA VE IŞINLAN]")
        ));
        tp.setItemMeta(m1);
        inv.setItem(48, tp);

        ItemStack info = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta m2 = info.getItemMeta();
        m2.setDisplayName(plugin.color("&#FF3380Gelişmiş İade Sistemi V5"));
        m2.setLore(Arrays.asList(
            plugin.color("&fKatil bilgisi, açlık barı, XP"),
            plugin.color("&fve nokta atışı iade aktiftir."),
            plugin.color("&fV5: Ekran Title Efektleri!")
        ));
        info.setItemMeta(m2);
        inv.setItem(49, info);

        ItemStack clear = new ItemStack(Material.BARRIER);
        ItemMeta m3 = clear.getItemMeta();
        m3.setDisplayName(plugin.color("&#FF0000Tüm Kayıtları Temizle"));
        m3.setLore(Arrays.asList(
            plugin.color("&fBu oyuncuya ait veritabanındaki"),
            plugin.color("&ftüm geçmiş ölümleri kalıcı olarak"),
            plugin.color("&fsilmek için tıklayın."),
            plugin.color(" "),
            plugin.color("&#FF3380[DİKKAT! GERİ ALINAMAZ]")
        ));
        clear.setItemMeta(m3);
        inv.setItem(50, clear);
    }

    private void decorateGui(Inventory inv) {
        ItemStack redPane = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemStack pinkPane = new ItemStack(Material.PINK_STAINED_GLASS_PANE);
        ItemStack whitePane = new ItemStack(Material.WHITE_STAINED_GLASS_PANE); 
        
        ItemMeta rm = redPane.getItemMeta(); rm.setDisplayName(" "); redPane.setItemMeta(rm);
        ItemMeta pm = pinkPane.getItemMeta(); pm.setDisplayName(" "); pinkPane.setItemMeta(pm);
        ItemMeta wm = whitePane.getItemMeta(); wm.setDisplayName(" "); whitePane.setItemMeta(wm);
        
        // Daha simetrik ve RGB uyumlu cam dizilimi
        int[] redBorder = {0, 4, 8, 45, 49, 53}; 
        int[] whiteBorder = {1, 3, 5, 7, 46, 48, 50, 52};
        int[] pinkBorder = {2, 6, 47, 51}; 
        
        for (int i : redBorder) inv.setItem(i, redPane);
        for (int i : whiteBorder) inv.setItem(i, whitePane);
        for (int i : pinkBorder) inv.setItem(i, pinkPane);
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
        Objective o = b.registerNewObjective("svx", "dummy", plugin.color("&#FF0000&lS&#FF1A40&lV&#FF3380&lX"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        o.getScore(plugin.color("&#FF3380Ping: &f" + p.getPing() + "ms")).setScore(1);
        p.setScoreboard(b);
    }

    private void saveDeaths() {
        try { deathsConfig.save(deathsFile); } catch (IOException e) { e.printStackTrace(); }
    }
                            }
                    
