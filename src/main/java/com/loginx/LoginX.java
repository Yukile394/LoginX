package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
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
    // Hangi tür hologramın hangi lokasyonda olduğunu tutar
    private final HashMap<Location, String> activeHolograms = new HashMap<>();
    private final List<ArmorStand> spawnedStands = new ArrayList<>();

    private long nextResetTime;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        loadStats();

        // --- LoginX2 MODÜLÜ BAĞLANTISI (İstediğin gibi korundu) ---
        try {
            LoginX2 modul = new LoginX2(this); 
            Bukkit.getPluginManager().registerEvents(modul, this);
            getLogger().info("LoginX2 modulu basariyla aktif edildi!");
        } catch (Throwable t) {
            getLogger().warning("LoginX2 yuklenirken bir sorun olustu veya sinif bulunamadi!");
        }

        // --- SİSTEM GÖREVLERİ ---
        startPlaytimeTracker();
        startHologramUpdater();
        startWeeklyResetChecker();

        getLogger().info("LoginX Hologram & İstatistik Sistemleri Aktif Edildi!");
    }

    @Override
    public void onDisable() {
        saveStats();
        clearAllHolograms(); // Sunucu kapanırken hologramları temizle (hayalet entity kalmasın)
        getLogger().info("LoginX devre disi.");
    }

    // --- KOMUT YÖNETİCİSİ ---
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Sadece oyun içi kullanılabilir!");
            return true;
        }

        if (!p.hasPermission("loginx.admin")) {
            p.sendMessage(color("&#FF0000[!] &cBu komut için yetkiniz yok!"));
            return true;
        }

        // Baktığı bloğun lokasyonunu al (Düzgün bir yükseklik ekleyerek)
        Location targetLoc = p.getTargetBlock(null, 10).getLocation().add(0.5, 3.0, 0.5);

        if (cmd.getName().equalsIgnoreCase("skorkill")) {
            spawnHologram(targetLoc, "KILLS");
            p.sendMessage(color("&#00FF00[!] &aÖldürme sıralaması baktığın yere oluşturuldu!"));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("skorzaman")) {
            spawnHologram(targetLoc, "PLAYTIME");
            p.sendMessage(color("&#00FF00[!] &aOynama süresi sıralaması baktığın yere oluşturuldu!"));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("skorblok")) {
            spawnHologram(targetLoc, "BLOCKS");
            p.sendMessage(color("&#00FF00[!] &aBlok kırma sıralaması baktığın yere oluşturuldu!"));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("skorsil")) {
            clearAllHolograms();
            activeHolograms.clear();
            p.sendMessage(color("&#FF0000[!] &cTüm aktif skor hologramları silindi!"));
            return true;
        }

        return true;
    }

    // --- EVENTLER (Veri Toplama) ---
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

    // Dakikada bir oynama süresini artırır
    private void startPlaytimeTracker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    UUID id = p.getUniqueId();
                    playtime.put(id, playtime.getOrDefault(id, 0) + 1);
                }
            }
        }.runTaskTimer(this, 1200L, 1200L); // 60 saniyede bir
    }

    // Hologramları güncel tutar (5 saniyede bir yeniler)
    private void startHologramUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeHolograms.isEmpty()) return;
                
                // Önceki standları temizle ve yenilerini oluştur (Dinamik güncelleme için)
                clearAllHolograms();
                for (Map.Entry<Location, String> entry : activeHolograms.entrySet()) {
                    buildHologramLines(entry.getKey(), entry.getValue());
                }
            }
        }.runTaskTimer(this, 100L, 100L); 
    }

    // Haftalık sıfırlama kontrolcüsü
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
        }.runTaskTimer(this, 20L * 60, 20L * 60 * 60); // Saatte bir kontrol et
    }

    // --- HOLOGRAM OLUŞTURMA İŞLEMLERİ ---

    private void spawnHologram(Location loc, String type) {
        activeHolograms.put(loc, type);
        buildHologramLines(loc, type);
    }

    private void buildHologramLines(Location baseLoc, String type) {
        List<String> lines = new ArrayList<>();
        
        // Başlıklar
        if (type.equals("KILLS")) {
            lines.add("&#FF0000&l⚔ EN ÇOK ÖLDÜRENLER ⚔");
            lines.addAll(getTop10(kills, "Öldürme"));
        } else if (type.equals("PLAYTIME")) {
            lines.add("&#00FFFF&l⏳ EN ÇOK OYNAYANLAR ⏳");
            lines.addAll(getTop10Playtime(playtime));
        } else if (type.equals("BLOCKS")) {
            lines.add("&#00FF00&l⛏ EN ÇOK BLOK KIRANLAR ⛏");
            lines.addAll(getTop10(blocksBroken, "Blok"));
        }

        lines.add("&7(Her hafta sıfırlanır)");

        // Satırları zırh askısı olarak yukarıdan aşağı doğru diz
        double yOffset = 0;
        for (String line : lines) {
            Location spawnLoc = baseLoc.clone().subtract(0, yOffset, 0);
            ArmorStand stand = (ArmorStand) baseLoc.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
            
            stand.setVisible(false);
            stand.setCustomNameVisible(true);
            stand.setCustomName(color(line));
            stand.setGravity(false);
            stand.setMarker(true); // Hitbox'ı küçültür
            stand.setBasePlate(false);
            
            spawnedStands.add(stand);
            yOffset += 0.3; // Satır arası boşluk
        }
    }

    private void clearAllHolograms() {
        for (ArmorStand stand : spawnedStands) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
        spawnedStands.clear();
    }

    // --- SIRALAMA (TOP 10) ALGORİTMALARI ---

    private List<String> getTop10(HashMap<UUID, Integer> map, String suffix) {
        List<String> lines = new ArrayList<>();
        if (map.isEmpty()) {
            lines.add("&cHenüz veri yok...");
            return lines;
        }

        List<Map.Entry<UUID, Integer>> sorted = map.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toList());

        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null) name = "Bilinmiyor";
            
            // İlk 3'e özel renkler
            String rankColor = rank == 1 ? "&#FFD700" : (rank == 2 ? "&#C0C0C0" : (rank == 3 ? "&#CD7F32" : "&e"));
            lines.add(color(rankColor + rank + ". &f" + name + " &7- &a" + entry.getValue() + " " + suffix));
            rank++;
        }
        return lines;
    }

    private List<String> getTop10Playtime(HashMap<UUID, Integer> map) {
        List<String> lines = new ArrayList<>();
        if (map.isEmpty()) {
            lines.add("&cHenüz veri yok...");
            return lines;
        }

        List<Map.Entry<UUID, Integer>> sorted = map.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toList());

        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null) name = "Bilinmiyor";
            
            int totalMins = entry.getValue();
            int hours = totalMins / 60;
            int mins = totalMins % 60;
            String timeStr = hours > 0 ? hours + "s " + mins + "d" : mins + "d";

            String rankColor = rank == 1 ? "&#FFD700" : (rank == 2 ? "&#C0C0C0" : (rank == 3 ? "&#CD7F32" : "&e"));
            lines.add(color(rankColor + rank + ". &f" + name + " &7- &a" + timeStr));
            rank++;
        }
        return lines;
    }

    // --- VERİ KAYDETME VE YÜKLEME ---
    
    private void saveStats() {
        FileConfiguration config = getConfig();
        config.set("next_reset", nextResetTime);
        
        for (Map.Entry<UUID, Integer> entry : kills.entrySet()) {
            config.set("stats.kills." + entry.getKey().toString(), entry.getValue());
        }
        for (Map.Entry<UUID, Integer> entry : playtime.entrySet()) {
            config.set("stats.playtime." + entry.getKey().toString(), entry.getValue());
        }
        for (Map.Entry<UUID, Integer> entry : blocksBroken.entrySet()) {
            config.set("stats.blocks." + entry.getKey().toString(), entry.getValue());
        }
        saveConfig();
    }

    private void loadStats() {
        FileConfiguration config = getConfig();
        
        // Sıfırlama zamanını ayarla
        if (config.contains("next_reset")) {
            nextResetTime = config.getLong("next_reset");
        } else {
            setNextResetTime();
        }

        if (config.contains("stats.kills")) {
            for (String uuidStr : config.getConfigurationSection("stats.kills").getKeys(false)) {
                kills.put(UUID.fromString(uuidStr), config.getInt("stats.kills." + uuidStr));
            }
        }
        if (config.contains("stats.playtime")) {
            for (String uuidStr : config.getConfigurationSection("stats.playtime").getKeys(false)) {
                playtime.put(UUID.fromString(uuidStr), config.getInt("stats.playtime." + uuidStr));
            }
        }
        if (config.contains("stats.blocks")) {
            for (String uuidStr : config.getConfigurationSection("stats.blocks").getKeys(false)) {
                blocksBroken.put(UUID.fromString(uuidStr), config.getInt("stats.blocks." + uuidStr));
            }
        }
    }

    private void setNextResetTime() {
        // Şu anki zamana 7 gün (milisaniye cinsinden) ekler
        this.nextResetTime = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000);
    }

    // --- YARDIMCI METOT (HEX RENK DESTEĞİ) ---
    public String color(String text) {
        Pattern pattern = Pattern.compile("&#([a-fA-F0-9]{6})");
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) replacement.append("§").append(c);
            matcher.appendReplacement(buffer, replacement.toString());
        }
        return ChatColor.translateAlternateColorCodes('&', matcher.appendTail(buffer).toString());
    }
}
