package com.loginx.trap;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Tüm trap verilerini yöneten merkezi sınıf.
 * Her trap bir TrapData nesnesiyle temsil edilir.
 * Veriler plugins/LoginX/traps/ klasörüne kaydedilir.
 */
public class TrapManager {

    private final org.bukkit.plugin.Plugin plugin;

    // trapId -> TrapData
    private final HashMap<Integer, TrapData> traps = new HashMap<>();

    // Sonraki trap ID
    private int nextId = 1;

    // Trap veri dosyalarının bulunduğu klasör
    private File trapsFolder;

    // WorldEdit balta seçimleri: oyuncu UUID -> Pos1, Pos2
    private final HashMap<UUID, Location> selectionPos1 = new HashMap<>();
    private final HashMap<UUID, Location> selectionPos2 = new HashMap<>();

    // Satışa çıkarılan trapler: trapId -> satış fiyatı
    private final HashMap<Integer, Double> trapMarket = new HashMap<>();

    public TrapManager(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
        trapsFolder = new File(plugin.getDataFolder(), "traps");
        if (!trapsFolder.exists()) trapsFolder.mkdirs();
        loadAllTraps();
    }

    // =========================================================
    //  TRAP OLUŞTURMA
    // =========================================================

    /**
     * Yeni trap oluşturur.
     * @param owner     Sahibin UUID'si
     * @param ownerName Sahibin adı
     * @param pos1      Köşe 1
     * @param pos2      Köşe 2
     * @param price     Satın alma bedeli (trap market)
     * @return Oluşturulan TrapData
     */
    public TrapData createTrap(UUID owner, String ownerName, Location pos1, Location pos2, double price) {
        int id = nextId++;
        TrapData data = new TrapData(id, owner, ownerName, pos1, pos2, price);
        traps.put(id, data);
        saveTrap(data);
        return data;
    }

    // =========================================================
    //  TRAP SİLME
    // =========================================================

    public boolean deleteTrap(int id) {
        TrapData data = traps.remove(id);
        if (data == null) return false;
        File file = new File(trapsFolder, id + ".yml");
        if (file.exists()) file.delete();
        trapMarket.remove(id);
        return true;
    }

    // =========================================================
    //  VERİ ERİŞİM
    // =========================================================

    public TrapData getTrap(int id) {
        return traps.get(id);
    }

    public Collection<TrapData> getAllTraps() {
        return traps.values();
    }

    /** Oyuncunun sahip olduğu tüm trapler */
    public List<TrapData> getPlayerTraps(UUID owner) {
        List<TrapData> list = new ArrayList<>();
        for (TrapData d : traps.values()) {
            if (d.getOwner().equals(owner)) list.add(d);
        }
        return list;
    }

    /** Bir lokasyonun hangi trapa ait olduğunu döner (null = hiçbiri) */
    public TrapData getTrapAt(Location loc) {
        for (TrapData d : traps.values()) {
            if (d.contains(loc)) return d;
        }
        return null;
    }

    // =========================================================
    //  BALTA (AXE) SEÇİMLERİ
    // =========================================================

    public void setPos1(UUID uuid, Location loc) { selectionPos1.put(uuid, loc); }
    public void setPos2(UUID uuid, Location loc) { selectionPos2.put(uuid, loc); }
    public Location getPos1(UUID uuid) { return selectionPos1.get(uuid); }
    public Location getPos2(UUID uuid) { return selectionPos2.get(uuid); }
    public boolean hasBothSelections(UUID uuid) {
        return selectionPos1.containsKey(uuid) && selectionPos2.containsKey(uuid);
    }
    public void clearSelection(UUID uuid) {
        selectionPos1.remove(uuid);
        selectionPos2.remove(uuid);
    }

    // =========================================================
    //  MARKET (SATIŞ)
    // =========================================================

    public void listOnMarket(int trapId, double price) {
        TrapData data = traps.get(trapId);
        if (data == null) return;
        data.setForSale(true);
        data.setSalePrice(price);
        trapMarket.put(trapId, price);
        saveTrap(data);
    }

    public void removeFromMarket(int trapId) {
        TrapData data = traps.get(trapId);
        if (data != null) {
            data.setForSale(false);
            trapMarket.remove(trapId);
            saveTrap(data);
        }
    }

    /** Satıştaki tüm trapler */
    public List<TrapData> getMarketTraps() {
        List<TrapData> list = new ArrayList<>();
        for (TrapData d : traps.values()) {
            if (d.isForSale()) list.add(d);
        }
        return list;
    }

    // =========================================================
    //  BANKA
    // =========================================================

    public boolean deposit(int trapId, double amount) {
        TrapData d = traps.get(trapId);
        if (d == null) return false;
        d.setBank(d.getBank() + amount);
        saveTrap(d);
        return true;
    }

    public boolean withdraw(int trapId, double amount) {
        TrapData d = traps.get(trapId);
        if (d == null || d.getBank() < amount) return false;
        d.setBank(d.getBank() - amount);
        saveTrap(d);
        return true;
    }

    // =========================================================
    //  ÜYE YÖNETİMİ
    // =========================================================

    public boolean invite(int trapId, UUID target) {
        TrapData d = traps.get(trapId);
        if (d == null || d.isMember(target)) return false;
        d.addMember(target);
        saveTrap(d);
        return true;
    }

    public boolean kick(int trapId, UUID target) {
        TrapData d = traps.get(trapId);
        if (d == null || !d.isMember(target)) return false;
        d.removeMember(target);
        saveTrap(d);
        return true;
    }

    // =========================================================
    //  KAYDETME / YÜKLEME
    // =========================================================

    public void saveTrap(TrapData data) {
        File file = new File(trapsFolder, data.getId() + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();

        cfg.set("id", data.getId());
        cfg.set("owner", data.getOwner().toString());
        cfg.set("ownerName", data.getOwnerName());
        cfg.set("bank", data.getBank());
        cfg.set("forSale", data.isForSale());
        cfg.set("salePrice", data.getSalePrice());

        // Pos1
        cfg.set("pos1.world", data.getPos1().getWorld().getName());
        cfg.set("pos1.x", data.getPos1().getBlockX());
        cfg.set("pos1.y", data.getPos1().getBlockY());
        cfg.set("pos1.z", data.getPos1().getBlockZ());

        // Pos2
        cfg.set("pos2.world", data.getPos2().getWorld().getName());
        cfg.set("pos2.x", data.getPos2().getBlockX());
        cfg.set("pos2.y", data.getPos2().getBlockY());
        cfg.set("pos2.z", data.getPos2().getBlockZ());

        // Üyeler
        List<String> memberList = new ArrayList<>();
        for (UUID u : data.getMembers()) memberList.add(u.toString());
        cfg.set("members", memberList);

        // Spawn
        if (data.getSpawnLocation() != null) {
            Location sp = data.getSpawnLocation();
            cfg.set("spawn.world", sp.getWorld().getName());
            cfg.set("spawn.x", sp.getX());
            cfg.set("spawn.y", sp.getY());
            cfg.set("spawn.z", sp.getZ());
            cfg.set("spawn.yaw", sp.getYaw());
            cfg.set("spawn.pitch", sp.getPitch());
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Trap " + data.getId() + " kaydedilemedi: " + e.getMessage());
        }
    }

    private void loadAllTraps() {
        File[] files = trapsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                int id = cfg.getInt("id");
                UUID owner = UUID.fromString(cfg.getString("owner"));
                String ownerName = cfg.getString("ownerName", "Bilinmiyor");
                double bank = cfg.getDouble("bank", 0);
                boolean forSale = cfg.getBoolean("forSale", false);
                double salePrice = cfg.getDouble("salePrice", 0);

                World w1 = Bukkit.getWorld(cfg.getString("pos1.world"));
                World w2 = Bukkit.getWorld(cfg.getString("pos2.world"));
                if (w1 == null || w2 == null) continue;

                Location pos1 = new Location(w1,
                        cfg.getInt("pos1.x"), cfg.getInt("pos1.y"), cfg.getInt("pos1.z"));
                Location pos2 = new Location(w2,
                        cfg.getInt("pos2.x"), cfg.getInt("pos2.y"), cfg.getInt("pos2.z"));

                TrapData data = new TrapData(id, owner, ownerName, pos1, pos2, salePrice);
                data.setBank(bank);
                data.setForSale(forSale);
                data.setSalePrice(salePrice);

                // Üyeler
                List<String> memberList = cfg.getStringList("members");
                for (String s : memberList) {
                    try { data.addMember(UUID.fromString(s)); } catch (Exception ignored) {}
                }

                // Spawn
                if (cfg.contains("spawn")) {
                    World sw = Bukkit.getWorld(cfg.getString("spawn.world"));
                    if (sw != null) {
                        Location spawn = new Location(sw,
                                cfg.getDouble("spawn.x"), cfg.getDouble("spawn.y"),
                                cfg.getDouble("spawn.z"),
                                (float) cfg.getDouble("spawn.yaw"),
                                (float) cfg.getDouble("spawn.pitch"));
                        data.setSpawnLocation(spawn);
                    }
                }

                traps.put(id, data);
                if (id >= nextId) nextId = id + 1;

                if (forSale) trapMarket.put(id, salePrice);

            } catch (Exception e) {
                plugin.getLogger().warning("Trap dosyası yüklenemedi: " + file.getName());
            }
        }
        plugin.getLogger().info(traps.size() + " trap yüklendi.");
    }

    public void saveAll() {
        for (TrapData d : traps.values()) saveTrap(d);
    }
                  }

