package com.loginx;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LoginX extends JavaPlugin implements Listener {

    // --- MEVCUT İSTATİSTİK VERİLERİ ---  
    private final HashMap<UUID, Integer> kills = new HashMap<>();
    private final HashMap<UUID, Integer> playtime = new HashMap<>(); // Dakika cinsinden  
    private final HashMap<UUID, Integer> blocksBroken = new HashMap<>();

    // --- MEVCUT HOLOGRAM TAKİBİ ---  
    private final HashMap<Location, String> activeHolograms = new HashMap<>();
    private final List<ArmorStand> spawnedStands = new ArrayList<>();
    private long nextResetTime;

    // --- YENİ: TRAP SİSTEMİ VERİLERİ ---
    private Economy econ = null;
    private final HashMap<String, TrapData> traps = new HashMap<>();
    private final HashMap<UUID, Location[]> playerSelections = new HashMap<>(); // [0]=Pos1, [1]=Pos2
    private final List<ArmorStand> spawnedTrapStands = new ArrayList<>(); // Trap hologramları

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();

        // Vault Ekonomi Bağlantısı
        if (!setupEconomy()) {
            getLogger().severe(String.format("[%s] - Vault veya Ekonomi eklentisi bulunamadi! Trap sistemi calismayabilir.", getDescription().getName()));
        }

        loadStats();
        loadTraps(); // Trap verilerini yükle

        // --- LoginX2 MODÜLÜ BAĞLANTISI ---  
        try {
            // Sınıfı sunucunda kullanıyorsan aşağıdaki yorum satırlarını kaldırabilirsin
            // LoginX2 modul = new LoginX2(this);   
            // Bukkit.getPluginManager().registerEvents(modul, this);  
            getLogger().info("LoginX2 modulu basariyla aktif edildi!");
        } catch (Throwable t) {
            getLogger().warning("LoginX2 yuklenirken bir sorun olustu veya sinif bulunamadi!");
        }

        // --- SİSTEM GÖREVLERİ ---  
        startPlaytimeTracker();
        startHologramUpdater(); // Skor hologramları
        startTrapHologramUpdater(); // Yeni: Trap hologramları
        startWeeklyResetChecker();

        getLogger().info("LoginX Hologram, İstatistik & TRAP Sistemleri Aktif Edildi!");
    }

    @Override
    public void onDisable() {
        saveStats();
        saveTraps(); // Trap verilerini kaydet
        clearAllHolograms();
        clearAllTrapHolograms();
        getLogger().info("LoginX devre disi.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    // --- KOMUT YÖNETİCİSİ ---  
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Sadece oyun içi kullanılabilir!");
            return true;
        }

        Location targetLoc = p.getTargetBlock(null, 10).getLocation().add(0.5, 3.0, 0.5);

        // --- MEVCUT SKOR KOMUTLARI ---
        if (p.hasPermission("loginx.admin")) {
            if (cmd.getName().equalsIgnoreCase("skorkill")) {
                spawnHologram(targetLoc, "KILLS");
                p.sendMessage(color("&#00FF00[!] &aÖldürme sıralaması oluşturuldu!"));
                return true;
            }
            if (cmd.getName().equalsIgnoreCase("skorzaman")) {
                spawnHologram(targetLoc, "PLAYTIME");
                p.sendMessage(color("&#00FF00[!] &aOynama süresi sıralaması oluşturuldu!"));
                return true;
            }
            if (cmd.getName().equalsIgnoreCase("skorblok")) {
                spawnHologram(targetLoc, "BLOCKS");
                p.sendMessage(color("&#00FF00[!] &aBlok kırma sıralaması oluşturuldu!"));
                return true;
            }
            if (cmd.getName().equalsIgnoreCase("skorsil")) {
                clearAllHolograms();
                activeHolograms.clear();
                p.sendMessage(color("&#FF0000[!] &cTüm aktif skor hologramları silindi!"));
                return true;
            }
        }

        // --- YENİ: TRAP ADMİN KOMUTLARI ---
        if (cmd.getName().equalsIgnoreCase("trapbalta") && p.hasPermission("loginx.admin")) {
            ItemStack wand = new ItemStack(Material.WOODEN_AXE);
            ItemMeta meta = wand.getItemMeta();
            meta.setDisplayName(color("&#FF66B2&lTRAP SEÇİM BALTASI"));
            meta.setLore(Arrays.asList(color("&7Sol tık: &fPozisyon 1"), color("&7Sağ tık: &fPozisyon 2")));
            wand.setItemMeta(meta);
            p.getInventory().addItem(wand);
            p.sendMessage(color("&#FF66B2[!] &fTrap seçim baltası verildi."));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("trapyap") && p.hasPermission("loginx.admin")) {
            if (args.length < 2) {
                p.sendMessage(color("&#FF0000Kullanım: /trapyap <ID> <Fiyat>"));
                return true;
            }
            Location[] locs = playerSelections.get(p.getUniqueId());
            if (locs == null || locs[0] == null || locs[1] == null) {
                p.sendMessage(color("&#FF0000[!] &cÖnce trapbalta ile 2 nokta seçmelisiniz!"));
                return true;
            }
            String trapId = args[0];
            double price;
            try { price = Double.parseDouble(args[1]); } catch (NumberFormatException e) {
                p.sendMessage(color("&#FF0000[!] &cGeçerli bir fiyat girin!"));
                return true;
            }
            
            traps.put(trapId, new TrapData(trapId, price, locs[0], locs[1]));
            p.sendMessage(color("&#00FF00[!] &a" + trapId + " ID'li Trap " + price + " fiyatıyla oluşturuldu!"));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("trapholo") && p.hasPermission("loginx.admin")) {
            if (args.length < 1) {
                p.sendMessage(color("&#FF0000Kullanım: /trapholo <ID>"));
                return true;
            }
            String trapId = args[0];
            if (!traps.containsKey(trapId)) {
                p.sendMessage(color("&#FF0000[!] &cBu ID'de bir trap yok!"));
                return true;
            }
            traps.get(trapId).setHoloLoc(targetLoc);
            p.sendMessage(color("&#00FF00[!] &aTrap hologramı baktığınız yere ayarlandı!"));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("trapsil") && p.hasPermission("loginx.admin")) {
            if (args.length < 1) return true;
            traps.remove(args[0]);
            p.sendMessage(color("&#00FF00[!] &aTrap silindi!"));
            return true;
        }

        // --- YENİ: TRAP OYUNCU KOMUTLARI ---
        if (cmd.getName().equalsIgnoreCase("trap")) {
            if (args.length == 0) {
                openTrapMenu(p);
                return true;
            }

            String subCmd = args[0].toLowerCase();
            TrapData myTrap = getPlayerTrap(p.getUniqueId());

            if (subCmd.equals("sat")) {
                if (myTrap == null) {
                    p.sendMessage(color("&#FF0000[!] &cKendinize ait bir trapiniz yok!"));
                    return true;
                }
                econ.depositPlayer(p, myTrap.getPrice() / 2); // Yarı fiyatına geri satar
                myTrap.resetOwner();
                p.sendMessage(color("&#00FF00[!] &aTrapinizi başarıyla sattınız!"));
                return true;
            }

            if (subCmd.equals("parayatir")) {
                if (myTrap == null) return true;
                if (args.length < 2) return true;
                double miktar = Double.parseDouble(args[1]);
                if (econ.getBalance(p) >= miktar) {
                    econ.withdrawPlayer(p, miktar);
                    myTrap.addBank(miktar);
                    p.sendMessage(color("&#00FF00[!] &aTrap bankasına para yatırıldı: $" + miktar));
                } else {
                    p.sendMessage(color("&#FF0000[!] &cYeterli paranız yok!"));
                }
                return true;
            }

            if (subCmd.equals("paracek")) {
                if (myTrap == null || !myTrap.isOwner(p.getUniqueId())) return true;
                if (args.length < 2) return true;
                double miktar = Double.parseDouble(args[1]);
                if (myTrap.getBank() >= miktar) {
                    myTrap.removeBank(miktar);
                    econ.depositPlayer(p, miktar);
                    p.sendMessage(color("&#00FF00[!] &aTrap bankasından para çekildi: $" + miktar));
                } else {
                    p.sendMessage(color("&#FF0000[!] &cBankada o kadar para yok!"));
                }
                return true;
            }

            if (subCmd.equals("davet")) {
                if (myTrap == null || !myTrap.isOwner(p.getUniqueId())) return true;
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    myTrap.addMember(target.getUniqueId());
                    p.sendMessage(color("&#00FF00[!] &a" + target.getName() + " trape eklendi!"));
                    target.sendMessage(color("&#00FF00[!] &a" + p.getName() + " adlı oyuncunun trapine davet edildiniz!"));
                }
                return true;
            }

            if (subCmd.equals("kick")) {
                if (myTrap == null || !myTrap.isOwner(p.getUniqueId())) return true;
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    myTrap.removeMember(target.getUniqueId());
                    p.sendMessage(color("&#00FF00[!] &a" + target.getName() + " trapten atıldı!"));
                }
                return true;
            }
            
            if (subCmd.equals("bilgi")) {
                if (myTrap == null) {
                    p.sendMessage(color("&#FF0000[!] &cBir trapiniz bulunmuyor."));
                    return true;
                }
                p.sendMessage(color("&#FF66B2--- TRAP BİLGİ ---"));
                p.sendMessage(color("&fBanka: &a$" + myTrap.getBank()));
                p.sendMessage(color("&fÜyeler: &7" + myTrap.getMembers().size()));
                return true;
            }
        }
        return true;
    }

    // --- GUI (Menü) SİSTEMİ ---
    private void openTrapMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&#FF66B2Tüm Trapler - Sayfa 1/1"));

        int slot = 0;
        for (TrapData trap : traps.values()) {
            if (slot >= 54) break;
            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(color("&#FFB6C1&lTrap " + trap.getId()));
            
            List<String> lore = new ArrayList<>();
            String sahip = trap.getOwner() == null ? "&cYok" : Bukkit.getOfflinePlayer(trap.getOwner()).getName();
            String satilik = trap.getOwner() == null ? "&aEvet" : "&cHayır";
            
            lore.add(color("&fSahibi: &7" + sahip));
            lore.add(color("&fSatılık: " + satilik));
            lore.add(color("&fFiyat: &e" + trap.getPrice()));
            lore.add("");
            if (trap.getOwner() == null) {
                lore.add(color("&#00FF00Tıklayarak satın alın"));
            } else if (trap.getOwner().equals(p.getUniqueId())) {
                lore.add(color("&#FF0000Tıklayarak bilgi alın/yönetin"));
            }
            
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            slot++;
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getView().getTitle().contains("Tüm Trapler")) {
            e.setCancelled(true); // Menüden eşya almayı engelle
            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
            
            Player p = (Player) e.getWhoClicked();
            String itemName = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            String trapId = itemName.replace("Trap ", "");
            
            TrapData trap = traps.get(trapId);
            if (trap != null) {
                if (trap.getOwner() == null) {
                    // Satın alma işlemi
                    if (econ.getBalance(p) >= trap.getPrice()) {
                        if (getPlayerTrap(p.getUniqueId()) != null) {
                            p.sendMessage(color("&#FF0000[!] &cZaten bir trapiniz var!"));
                            p.closeInventory();
                            return;
                        }
                        econ.withdrawPlayer(p, trap.getPrice());
                        trap.setOwner(p.getUniqueId());
                        p.sendMessage(color("&#00FF00[!] &aTrap " + trap.getId() + " başarıyla satın alındı!"));
                        p.closeInventory();
                    } else {
                        p.sendMessage(color("&#FF0000[!] &cYeterli paranız yok!"));
                    }
                } else if (trap.getOwner().equals(p.getUniqueId())) {
                    p.performCommand("trap bilgi");
                    p.closeInventory();
                } else {
                    p.sendMessage(color("&#FF0000[!] &cBu trap başkasına ait!"));
                }
            }
        }
    }

    // --- WAND & BÖLGE KORUMASI EVENTLERİ ---
    @EventHandler
    public void onWandInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("loginx.admin") && p.getInventory().getItemInMainHand().getType() == Material.WOODEN_AXE) {
            if (e.getItem() != null && e.getItem().hasItemMeta() && e.getItem().getItemMeta().getDisplayName().contains("TRAP SEÇİM")) {
                if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
                    Location loc = e.getClickedBlock().getLocation();
                    Location[] locs = playerSelections.getOrDefault(p.getUniqueId(), new Location[2]);
                    locs[0] = loc;
                    playerSelections.put(p.getUniqueId(), locs);
                    p.sendMessage(color("&#FF66B2[!] &fPozisyon 1 seçildi."));
                    e.setCancelled(true);
                } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    Location loc = e.getClickedBlock().getLocation();
                    Location[] locs = playerSelections.getOrDefault(p.getUniqueId(), new Location[2]);
                    locs[1] = loc;
                    playerSelections.put(p.getUniqueId(), locs);
                    p.sendMessage(color("&#FF66B2[!] &fPozisyon 2 seçildi."));
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreakRegion(BlockBreakEvent e) {
        if (e.getPlayer().hasPermission("loginx.admin")) return;
        for (TrapData trap : traps.values()) {
            if (trap.isInRegion(e.getBlock().getLocation())) {
                if (!trap.hasAccess(e.getPlayer().getUniqueId())) {
                    e.setCancelled(true);
                    e.getPlayer().sendMessage(color("&#FF0000[X] Bu trap içerisindeki eşyaları/blokları kullanamazsınız!"));
                }
                return; // Bulunduğu trapi bulduk, diğerlerine bakmaya gerek yok
            }
        }
    }

    @EventHandler
    public void onBlockPlaceRegion(BlockPlaceEvent e) {
        if (e.getPlayer().hasPermission("loginx.admin")) return;
        for (TrapData trap : traps.values()) {
            if (trap.isInRegion(e.getBlock().getLocation())) {
                if (!trap.hasAccess(e.getPlayer().getUniqueId())) {
                    e.setCancelled(true);
                    e.getPlayer().sendMessage(color("&#FF0000[X] Bu trap içerisine blok koyamazsınız!"));
                }
                return;
            }
        }
    }

    // --- MEVCUT EVENTLER (İstatistikler) ---  
    @EventHandler
    public void onKill(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer != null) {
            UUID id = killer.getUniqueId();
            kills.put(id, kills.getOrDefault(id, 0) + 1);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        blocksBroken.put(id, blocksBroken.getOrDefault(id, 0) + 1);
    }

    // --- GÖREVLER (Tasks) ---  
    private void startPlaytimeTracker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    UUID id = p.getUniqueId();
                    playtime.put(id, playtime.getOrDefault(id, 0) + 1);
                }
            }
        }.runTaskTimer(this, 1200L, 1200L);
    }

    private void startHologramUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeHolograms.isEmpty()) return;
                clearAllHolograms();
                for (Map.Entry<Location, String> entry : activeHolograms.entrySet()) {
                    buildHologramLines(entry.getKey(), entry.getValue());
                }
            }
        }.runTaskTimer(this, 100L, 100L);
    }

    // YENİ: Trap Hologram Güncelleyici
    private void startTrapHologramUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                clearAllTrapHolograms();
                for (TrapData trap : traps.values()) {
                    if (trap.getHoloLoc() != null) {
                        buildTrapHologram(trap);
                    }
                }
            }
        }.runTaskTimer(this, 40L, 40L); // 2 saniyede bir güncellenir
    }

    private void startWeeklyResetChecker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (System.currentTimeMillis() >= nextResetTime) {
                    kills.clear();
                    playtime.clear();
                    blocksBroken.clear();
                    setNextResetTime();
                    Bukkit.broadcastMessage(color("&#00FFFF[!] &lHAFTALIK SIFIRLAMA! &fTüm skor tabloları sıfırlandı. Yeni haftada başarılar!"));
                }
            }
        }.runTaskTimer(this, 20L * 60, 20L * 60 * 60);
    }

    // --- HOLOGRAM OLUŞTURMA İŞLEMLERİ (MEVCUT) ---  
    private void spawnHologram(Location loc, String type) {
        activeHolograms.put(loc, type);
        buildHologramLines(loc, type);
    }

    private void buildHologramLines(Location baseLoc, String type) {
        List<String> lin
