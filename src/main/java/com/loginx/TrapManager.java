package com.loginx;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TrapManager {

    private final Plugin plugin;
    private final HashMap<Integer, TrapData> traps = new HashMap<>();
    private int nextId = 1;
    private final File trapsFolder;

    private final HashMap<UUID, Location> selectionPos1 = new HashMap<>();
    private final HashMap<UUID, Location> selectionPos2 = new HashMap<>();

    public TrapManager(Plugin plugin) {
        this.plugin = plugin;
        trapsFolder = new File(plugin.getDataFolder(), "traps");
        if (!trapsFolder.exists()) trapsFolder.mkdirs();
        loadAllTraps();
    }

    // ── Trap CRUD ─────────────────────────────────────────────
    public TrapData createTrap(UUID owner, String ownerName,
                               Location pos1, Location pos2, double price) {
        int id = nextId++;
        TrapData data = new TrapData(id, owner, ownerName, pos1, pos2, price);
        traps.put(id, data);
        saveTrap(data);
        return data;
    }

    public boolean deleteTrap(int id) {
        TrapData data = traps.remove(id);
        if (data == null) return false;
        File f = new File(trapsFolder, id + ".yml");
        if (f.exists()) f.delete();
        return true;
    }

    public TrapData getTrap(int id) { return traps.get(id); }
    public Collection<TrapData> getAllTraps() { return traps.values(); }

    public List<TrapData> getPlayerTraps(UUID owner) {
        List<TrapData> list = new ArrayList<>();
        for (TrapData d : traps.values())
            if (d.getOwner().equals(owner)) list.add(d);
        return list;
    }

    public TrapData getTrapAt(Location loc) {
        for (TrapData d : traps.values())
            if (d.contains(loc)) return d;
        return null;
    }

    public List<TrapData> getMarketTraps() {
        List<TrapData> list = new ArrayList<>();
        for (TrapData d : traps.values())
            if (d.isForSale()) list.add(d);
        return list;
    }

    // ── Balta secimi ─────────────────────────────────────────
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

    // ── Market ───────────────────────────────────────────────
    public void listOnMarket(int trapId, double price) {
        TrapData d = traps.get(trapId);
        if (d == null) return;
        d.setForSale(true);
        d.setSalePrice(price);
        saveTrap(d);
    }

    public void removeFromMarket(int trapId) {
        TrapData d = traps.get(trapId);
        if (d == null) return;
        d.setForSale(false);
        saveTrap(d);
    }

    // ── Banka ────────────────────────────────────────────────
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

    // ── Uye ──────────────────────────────────────────────────
    public boolean invite(int trapId, UUID target) {
        TrapData d = traps.get(trapId);
        if (d == null || d.isMember(target)) return false;
        d.addMember(target);
        saveTrap(d);
        return true;
    }

    public boolean kick(int trapId, UUID target) {
        TrapData d = traps.get(trapId);
        if (d == null || !d.getMembers().contains(target)) return false;
        d.removeMember(target);
        saveTrap(d);
        return true;
    }

    // ── Kaydetme ─────────────────────────────────────────────
    public void saveTrap(TrapData data) {
        File file = new File(trapsFolder, data.getId() + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("id", data.getId());
        cfg.set("owner", data.getOwner().toString());
        cfg.set("ownerName", data.getOwnerName());
        cfg.set("bank", data.getBank());
        cfg.set("forSale", data.isForSale());
        cfg.set("salePrice", data.getSalePrice());

        cfg.set("pos1.world", data.getPos1().getWorld().getName());
        cfg.set("pos1.x", data.getPos1().getBlockX());
        cfg.set("pos1.y", data.getPos1().getBlockY());
        cfg.set("pos1.z", data.getPos1().getBlockZ());

        cfg.set("pos2.world", data.getPos2().getWorld().getName());
        cfg.set("pos2.x", data.getPos2().getBlockX());
        cfg.set("pos2.y", data.getPos2().getBlockY());
        cfg.set("pos2.z", data.getPos2().getBlockZ());

        List<String> memberList = new ArrayList<>();
        for (UUID u : data.getMembers()) memberList.add(u.toString());
        cfg.set("members", memberList);

        if (data.getSpawnLocation() != null) {
            Location sp = data.getSpawnLocation();
            cfg.set("spawn.world", sp.getWorld().getName());
            cfg.set("spawn.x", sp.getX());
            cfg.set("spawn.y", sp.getY());
            cfg.set("spawn.z", sp.getZ());
            cfg.set("spawn.yaw", (double) sp.getYaw());
            cfg.set("spawn.pitch", (double) sp.getPitch());
        }
        try { cfg.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Trap " + data.getId() + " kaydedilemedi!"); }
    }

    private void loadAllTraps() {
        File[] files = trapsFolder.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                int id = cfg.getInt("id");
                UUID owner = UUID.fromString(cfg.getString("owner", ""));
                String ownerName = cfg.getString("ownerName", "Bilinmiyor");
                double bank = cfg.getDouble("bank", 0);
                boolean forSale = cfg.getBoolean("forSale", false);
                double salePrice = cfg.getDouble("salePrice", 0);

                World w1 = Bukkit.getWorld(cfg.getString("pos1.world", ""));
                World w2 = Bukkit.getWorld(cfg.getString("pos2.world", ""));
                if (w1 == null || w2 == null) continue;

                Location pos1 = new Location(w1, cfg.getInt("pos1.x"),
                        cfg.getInt("pos1.y"), cfg.getInt("pos1.z"));
                Location pos2 = new Location(w2, cfg.getInt("pos2.x"),
                        cfg.getInt("pos2.y"), cfg.getInt("pos2.z"));

                TrapData data = new TrapData(id, owner, ownerName, pos1, pos2, salePrice);
                data.setBank(bank);
                data.setForSale(forSale);

                for (String s : cfg.getStringList("members")) {
                    try { data.addMember(UUID.fromString(s)); } catch (Exception ignored) {}
                }

                if (cfg.contains("spawn")) {
                    World sw = Bukkit.getWorld(cfg.getString("spawn.world", ""));
                    if (sw != null) {
                        data.setSpawnLocation(new Location(sw,
                                cfg.getDouble("spawn.x"), cfg.getDouble("spawn.y"),
                                cfg.getDouble("spawn.z"),
                                (float) cfg.getDouble("spawn.yaw"),
                                (float) cfg.getDouble("spawn.pitch")));
                    }
                }

                traps.put(id, data);
                if (id >= nextId) nextId = id + 1;

            } catch (Exception e) {
                plugin.getLogger().warning("Trap dosyasi yuklenemedi: " + file.getName());
            }
        }
        plugin.getLogger().info("[LoginX] " + traps.size() + " trap yuklendi.");
    }

    public void saveAll() {
        for (TrapData d : traps.values()) saveTrap(d);
    }
}
