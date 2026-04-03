package com.loginx;

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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LoginX extends JavaPlugin implements Listener {

    // --- İSTATİSTİK VERİLERİ ---
    private final HashMap<UUID, Integer> kills = new HashMap<>();
    private final HashMap<UUID, Integer> playtime = new HashMap<>(); // Dakika cinsinden
    private final HashMap<UUID, Integer> blocksBroken = new HashMap<>();

    // --- HOLOGRAM TAKİBİ ---
    private final HashMap<Location, String> activeHolograms = new HashMap<>();
    private final List<ArmorStand> spawnedStands = new ArrayList<>();

    private long nextResetTime;

    // --- TRAP SİSTEMİ VERİLERİ ---
    private final HashMap<String, Trap> traps = new HashMap<>();
    private final HashMap<UUID, Location> pos1 = new HashMap<>();
    private final HashMap<UUID, Location> pos2 = new HashMap<>();
    private Location trapWarp = null;
    
    // NOT: Gerçek bir ekonomi plugini (Vault vb.) kullanıyorsan burayı ona bağlayabilirsin.
    // Şimdilik sistemin kendi içinde çalışması için basit bir bakiye haritası ekledim.
    private final HashMap<UUID, Double> playerMoney = new HashMap<>(); 

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        loadStats();
        loadTraps();

        // --- LoginX2 MODÜLÜ BAĞLANTISI ---
        try {
            // LoginX2 modul = new LoginX2(this);
            // Bukkit.getPluginManager().registerEvents(modul, this);
            getLogger().info("LoginX2 modulu baglantisi (Varsa) aktif edildi.");
        } catch (Throwable t) {
            getLogger().warning("LoginX2 yuklenirken bir sorun olustu veya sinif bulunamadi!");
        }

        // --- SİSTEM GÖREVLERİ ---
        startPlaytimeTracker();
        startHologramUpdater();
        startWeeklyResetChecker();
        startTrapHologramUpdater();

        getLogger().info("LoginX Hologram, İstatistik & TRAP Sistemleri Aktif Edildi!");
    }

    @Override
    public void onDisable() {
        saveStats();
        saveTraps();
        clearAllHolograms();
        getLogger().info("LoginX devre disi.");
    }

    // --- KOMUT YÖNETİCİSİ ---
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Sadece oyun içi kullanılabilir!");
            return true;
        }

        Location targetLoc = p.getTargetBlock(null, 10).getLocation().add(0.5, 3.0, 0.5);

        // --- ESKİ SKOR KOMUTLARI ---
        if (p.hasPermission("loginx.admin")) {
            if (cmd.getName().equalsIgnoreCase("skorkill")) {
                spawnHologram(targetLoc, "KILLS");
                p.sendMessage(color("&#00FF00[!] &aÖldürme sıralaması baktığın yere oluşturuldu!"));
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

        // --- YENİ TRAP KOMUTLARI ---
        if (cmd.getName().equalsIgnoreCase("trap")) {
            if (args.length == 0) {
                if (trapWarp != null) {
                    p.teleport(trapWarp);
                    p.sendMessage(color("&#FF66B2[Trap] &fTrap bölgesine ışınlandınız!"));
                } else {
                    p.sendMessage(color("&#FF0000[!] &cHenüz bir Trap bölgesi ayarlanmamış!"));
                }
                return true;
            }

            // OYUNCU KOMUTLARI
            if (args[0].equalsIgnoreCase("satinal") && args.length == 2) {
                String trapId = args[1];
                if (!traps.containsKey(trapId)) {
                    p.sendMessage(color("&#FF0000[!] &cBöyle bir trap bulunamadı.")); return true;
                }
                Trap t = traps.get(trapId);
                if (t.owner != null) {
                    p.sendMessage(color("&#FF0000[!] &cBu trap zaten satılmış!")); return true;
                }
                double money = playerMoney.getOrDefault(p.getUniqueId(), 0.0);
                if (money < t.price) {
                    p.sendMessage(color("&#FF0000[!] &cYetersiz bakiye! Gerekli: &e" + t.price)); return true;
                }
                playerMoney.put(p.getUniqueId(), money - t.price);
                t.owner = p.getUniqueId();
                p.sendMessage(color("&#FF66B2[Trap] &fBaşarıyla &e" + trapId + " &fisimli trapi satın aldınız!"));
                return true;
            }

            if (args[0].equalsIgnoreCase("sat")) {
                Trap t = getPlayerTrap(p.getUniqueId());
                if (t == null) {
                    p.sendMessage(color("&#FF0000[!] &cSize ait bir trap bulunamadı!")); return true;
                }
                double refund = t.price * 0.75; // %75 fiyatına geri satar
                playerMoney.put(p.getUniqueId(), playerMoney.getOrDefault(p.getUniqueId(), 0.0) + refund + t.bank);
                t.owner = null;
                t.members.clear();
                t.bank = 0;
                p.sendMessage(color("&#FF66B2[Trap] &fTrapinizi başarıyla satıp &e" + refund + " &fpara kazandınız!"));
                return true;
            }

            if (args[0].equalsIgnoreCase("davet") && args.length == 2) {
                Trap t = getPlayerTrap(p.getUniqueId());
                if (t == null) { p.sendMessage(color("&#FF0000[!] &cBir trapiniz yok!")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { p.sendMessage(color("&#FF0000[!] &cOyuncu bulunamadı!")); return true; }
                if (!t.members.contains(target.getUniqueId())) {
                    t.members.add(target.getUniqueId());
                    p.sendMessage(color("&#FF66B2[Trap] &e" + target.getName() + " &ftrape eklendi!"));
                    target.sendMessage(color("&#FF66B2[Trap] &e" + p.getName() + " &fsizi trap'e ekledi!"));
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("kick") && args.length == 2) {
                Trap t = getPlayerTrap(p.getUniqueId());
                if (t == null) return true;
                Player target = Bukkit.getPlayer(args[1]);
                UUID targetId = (target != null) ? target.getUniqueId() : Bukkit.getOfflinePlayer(args[1]).getUniqueId();
                if (t.members.remove(targetId)) {
                    p.sendMessage(color("&#FF66B2[Trap] &e" + args[1] + " &ftrapten atıldı!"));
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("parayatir") && args.length == 2) {
                Trap t = getPlayerTrap(p.getUniqueId());
                if (t == null) return true;
                try {
                    double amount = Double.parseDouble(args[1]);
                    double bal = playerMoney.getOrDefault(p.getUniqueId(), 0.0);
                    if (bal >= amount) {
                        playerMoney.put(p.getUniqueId(), bal - amount);
                        t.bank += amount;
                        p.sendMessage(color("&#FF66B2[Trap] &fBankaya &a" + amount + " &fyatırdınız."));
                    } else p.sendMessage(color("&#FF0000[!] &cYetersiz bakiye!"));
                } catch (Exception e) { p.sendMessage(color("&cGeçersiz miktar!")); }
                return true;
            }

            if (args[0].equalsIgnoreCase("paracek") && args.length == 2) {
                Trap t = getPlayerTrap(p.getUniqueId());
                if (t == null) return true;
                try {
                    double amount = Double.parseDouble(args[1]);
                    if (t.bank >= amount) {
                        t.bank -= amount;
                        playerMoney.put(p.getUniqueId(), playerMoney.getOrDefault(p.getUniqueId(), 0.0) + amount);
                        p.sendMessage(color("&#FF66B2[Trap] &fBankadan &a" + amount + " &fçektiniz."));
                    } else p.sendMessage(color("&#FF0000[!] &cBankada o kadar para yok!"));
                } catch (Exception e) { p.sendMessage(color("&cGeçersiz miktar!")); }
                return true;
            }

            if (args[0].equalsIgnoreCase("banka")) {
                Trap t = getPlayerTrap(p.getUniqueId());
                if (t == null) { p.sendMessage(color("&#FF0000[!] &cTrapiniz yok!")); return true; }
                openTrapMenu(p, t);
                return true;
            }
            
            // Sadece test amaçlı oyuncuya sahte para verme komutu
            if (args[0].equalsIgnoreCase("parahilesi")) {
                playerMoney.put(p.getUniqueId(), playerMoney.getOrDefault(p.getUniqueId(), 0.0) + 10000);
                p.sendMessage(color("&aHesabına 10.000 eklendi."));
                return true;
            }

            // YETKİLİ KOMUTLARI
            if (p.hasPermission("loginx.admin")) {
                if (args[0].equalsIgnoreCase("setwarp")) {
                    trapWarp = p.getLocation();
                    p.sendMessage(color("&#FF66B2[Trap] &fTrap ana ışınlanma noktası ayarlandı!"));
                    return true;
                }
                
                if (args[0].equalsIgnoreCase("arac")) {
                    ItemStack wand = new ItemStack(Material.LEATHER);
                    ItemMeta meta = wand.getItemMeta();
                    meta.setDisplayName(color("&#FF66B2&lTrap Seçici Araç"));
                    meta.setLore(Arrays.asList(color("&fSol Tık: &dPos 1"), color("&fSağ Tık: &dPos 2")));
                    wand.setItemMeta(meta);
                    p.getInventory().addItem(wand);
                    p.sendMessage(color("&#FF66B2[Trap] &fTrap seçim aracı (Deri) verildi!"));
                    return true;
                }

                if (args[0].equalsIgnoreCase("yap") && args.length == 3) {
                    String id = args[1];
                    double price;
                    try { price = Double.parseDouble(args[2]); } catch (Exception e) { p.sendMessage("&cFiyat sayı olmalı!"); return true; }
                    
                    if (!pos1.containsKey(p.getUniqueId()) || !pos2.containsKey(p.getUniqueId())) {
                        p.sendMessage(color("&#FF0000[!] &cÖnce Deri ile 2 nokta seçmelisin!")); return true;
                    }

                    Trap t = new Trap(id, price, pos1.get(p.getUniqueId()), pos2.get(p.getUniqueId()));
                    traps.put(id, t);
                    p.sendMessage(color("&#FF66B2[Trap] &a" + id + " &fisimli trap &e" + price + " &ffiyatıyla oluşturuldu!"));
                    return true;
                }

                if (args[0].equalsIgnoreCase("hologram") && args.length == 2) {
                    String id = args[1];
                    if (!traps.containsKey(id)) {
                        p.sendMessage(color("&#FF0000[!] &cBu ID'ye ait trap yok!")); return true;
                    }
                    traps.get(id).holoLoc = targetLoc;
                    p.sendMessage(color("&#FF66B2[Trap] &fTrap hologramı baktığın yere kuruldu!"));
                    return true;
                }
            }
            
            // Yardım Menüsü
            p.sendMessage(color("&#FF66B2--- Trap Sistem Komutları ---"));
            p.sendMessage(color("&f/trap satinal <id> &7- Trap satın alır"));
            p.sendMessage(color("&f/trap sat &7- Trapini satar"));
            p.sendMessage(color("&f/trap davet/kick <isim> &7- Üye yönetimi"));
            p.sendMessage(color("&f/trap parayatir/paracek <miktar> &7- Banka işlemi"));
            p.sendMessage(color("&f/trap banka &7- Trap menüsünü açar"));
            if(p.hasPermission("loginx.admin")) {
                p.sendMessage(color("&d/trap arac &7- Bölge seçim derisi verir"));
                p.sendMessage(color("&d/trap yap <#id> <fiyat> &7- Seçilen alanı trap yapar"));
                p.sendMessage(color("&d/trap hologram <#id> &7- Trap hologramı koyar"));
                p.sendMessage(color("&d/trap setwarp &7- Trap ana bölgesini belirler"));
            }
        }
        return true;
    }

    // --- EVENTLER ---
    
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getItem() != null && e.getItem().getType() == Material.LEATHER && e.getItem().hasItemMeta()) {
            if (e.getItem().getItemMeta().getDisplayName().contains("Trap Seçici")) {
                if (!p.hasPermission("loginx.admin")) return;
                if (e.getClickedBlock() == null) return;
                
                e.setCancelled(true);
                if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
                    pos1.put(p.getUniqueId(), e.getClickedBlock().getLocation());
                    p.sendMessage(color("&#FF66B2[Trap] &dPozisyon 1 ayarlandı!"));
                } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    pos2.put(p.getUniqueId(), e.getClickedBlock().getLocation());
                    p.sendMessage(color("&#FF66B2[Trap] &dPozisyon 2 ayarlandı!"));
                }
            }
        }
    }

    // Korumalar
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        
        // Trap Koruma Kontrolü
        for (Trap t : traps.values()) {
            if (t.isInRegion(e.getBlock().getLocation())) {
                if (p.hasPermission("loginx.admin")) continue; // Admin kırabilir
                if (t.owner == null || (!t.owner.equals(id) && !t.members.contains(id))) {
                    e.setCancelled(true);
                    p.sendMessage(color("&#FF0000[!] &cBu trape müdahale edemezsin!"));
                    return;
                }
            }
        }
        blocksBroken.put(id, blocksBroken.getOrDefault(id, 0) + 1);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        for (Trap t : traps.values()) {
            if (t.isInRegion(e.getBlock().getLocation())) {
                if (p.hasPermission("loginx.admin")) continue;
                if (t.owner == null || (!t.owner.equals(id) && !t.members.contains(id))) {
                    e.setCancelled(true);
                    p.sendMessage(color("&#FF0000[!] &cBu trape blok koyamazsın!"));
                    return;
                }
            }
        }
    }

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
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals(color("&#FF66B2&lTrap Bankası"))) {
            e.setCancelled(true); // Menüden eşya almayı engelle
        }
    }

    // --- TRAP HOLOGRAM GÜNCELLEYİCİSİ ---
    private void startTrapHologramUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Sadece trap hologramlarını güncelle
                clearAllHolograms(); // Mevcut tüm hologramları temizle
                
                // SKOR Hologramlarını yeniden oluştur
                for (Map.Entry<Location, String> entry : activeHolograms.entrySet()) {
                    buildHologramLines(entry.getKey(), entry.getValue());
                }
                
                // TRAP Hologramlarını oluştur
                for (Trap t : traps.values()) {
                    if (t.holoLoc != null) {
                        buildTrapHologram(t);
                    }
                }
            }
        }.runTaskTimer(this, 100L, 100L); // 5 Saniyede bir
    }

    private void buildTrapHologram(Trap t) {
        List<String> lines = new ArrayList<>();
        lines.add("&#FF66B2&lTRAP " + t.id.toUpperCase());
        
        String ownerName = "&#FFFFFFSatılık!";
        if (t.owner != null) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(t.owner);
            ownerName = "&#FFFFFF" + (op.getName() != null ? op.getName() : "Bilinmiyor");
        }
        
        lines.add("&#FFB2D9Sahibi: " + ownerName);
        lines.add("&#FFB2D9Fiyat: &#FFFFFF" + t.price + " ₺");
        lines.add("&#FFB2D9Üye Sayısı: &#FFFFFF" + t.members.size() + " Kişi");
        lines.add("&#FFB2D9Banka: &#FFFFFF" + t.bank + " ₺");
        
        if (t.owner == null) {
            lines.add("&aSatın Almak İçin: &f/trap satinal " + t.id);
        }

        double yOffset = 0;
        for (String line : lines) {
            Location spawnLoc = t.holoLoc.clone().subtract(0, yOffset, 0);
            ArmorStand stand = (ArmorStand) t.holoLoc.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setCustomNameVisible(true);
            stand.setCustomName(color(line));
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setBasePlate(false);
            spawnedStands.add(stand);
            yOffset += 0.3;
        }
    }

    // --- MENÜ (GUI) ---
    private void openTrapMenu(Player p, Trap t) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&#FF66B2&lTrap Bankası"));
        
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName(color("&#FF66B2Trap Bilgileri"));
        List<String> lore = new ArrayList<>();
        lore.add(color("&fID: &e" + t.id));
        lore.add(color("&fBanka: &a" + t.bank + " ₺"));
        lore.add(color("&fÜye Sayısı: &d" + t.members.size()));
        meta.setLore(lore);
        info.setItemMeta(meta);
        
        inv.setItem(13, info); // Tam ortaya koy
        p.openInventory(inv);
    }

    private Trap getPlayerTrap(UUID uuid) {
        for (Trap t : traps.values()) {
            if (t.owner != null && t.owner.equals(uuid)) return t;
        }
        return null;
    }

    // --- ESKİ GÖREVLER VE METOTLAR ---
    private void startPlaytimeTracker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    UUID id = p.getUniqueId();
                    p
