package com.loginx;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.IOException;
import java.util.*;

class EconomyBridge {
    private Economy eco;
    private boolean ok;
    EconomyBridge(Plugin p) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) { p.getLogger().warning("[Trap] Vault yok!"); return; }
        RegisteredServiceProvider<Economy> r = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (r == null) { p.getLogger().warning("[Trap] Economy yok!"); return; }
        eco = r.getProvider(); ok = true;
    }
    boolean isOk() { return ok; }
    double bal(Player p) { return ok ? eco.getBalance(p) : 0; }
    boolean has(Player p, double a) { return ok && eco.has(p, a); }
    boolean take(Player p, double a) { if (!ok || !eco.has(p, a)) return false; eco.withdrawPlayer(p, a); return true; }
    void give(Player p, double a) { if (ok) eco.depositPlayer(p, a); }
    String fmt(double a) { return ok ? eco.format(a) : "$" + String.format("%.2f", a); }
}

class TrapData {
    private final int id;
    private UUID owner;
    private String ownerName;
    private double bank;
    private boolean forSale;
    private double salePrice;
    private Location pos1, pos2, spawn;
    private final List<UUID> members = new ArrayList<>();

    TrapData(int id, UUID owner, String ownerName, Location pos1, Location pos2, double price) {
        this.id = id; this.owner = owner; this.ownerName = ownerName;
        this.pos1 = pos1; this.pos2 = pos2; this.salePrice = price;
    }

    boolean contains(Location l) {
        if (!l.getWorld().equals(pos1.getWorld())) return false;
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        return x >= Math.min(pos1.getBlockX(), pos2.getBlockX()) && x <= Math.max(pos1.getBlockX(), pos2.getBlockX())
            && y >= Math.min(pos1.getBlockY(), pos2.getBlockY()) && y <= Math.max(pos1.getBlockY(), pos2.getBlockY())
            && z >= Math.min(pos1.getBlockZ(), pos2.getBlockZ()) && z <= Math.max(pos1.getBlockZ(), pos2.getBlockZ());
    }

    boolean isMember(UUID u) { return owner.equals(u) || members.contains(u); }
    void addMember(UUID u) { if (!members.contains(u)) members.add(u); }
    void removeMember(UUID u) { members.remove(u); }
    List<UUID> getMembers() { return members; }
    int getMemberCount() { return members.size() + 1; }
    String getSize() {
        return (Math.abs(pos1.getBlockX()-pos2.getBlockX())+1)+"x"
              +(Math.abs(pos1.getBlockY()-pos2.getBlockY())+1)+"x"
              +(Math.abs(pos1.getBlockZ()-pos2.getBlockZ())+1);
    }

    int getId() { return id; }
    UUID getOwner() { return owner; }
    void setOwner(UUID o) { owner = o; }
    String getOwnerName() { return ownerName; }
    void setOwnerName(String n) { ownerName = n; }
    double getBank() { return bank; }
    void setBank(double b) { bank = b; }
    boolean isForSale() { return forSale; }
    void setForSale(boolean f) { forSale = f; }
    double getSalePrice() { return salePrice; }
    void setSalePrice(double p) { salePrice = p; }
    Location getPos1() { return pos1; }
    Location getPos2() { return pos2; }
    Location getSpawn() { return spawn; }
    void setSpawn(Location s) { spawn = s; }
}

public class TrapX {
    private final Plugin plugin;
    private final Map<Integer, TrapData> traps = new HashMap<>();
    private int nextId = 1;
    private final File folder;
    private final Map<UUID, Location> p1 = new HashMap<>(), p2 = new HashMap<>();

    public TrapX(Plugin plugin) {
        this.plugin = plugin;
        folder = new File(plugin.getDataFolder(), "traps");
        folder.mkdirs();
        load();
    }

    public TrapData create(UUID owner, String name, Location pos1, Location pos2, double price) {
        TrapData d = new TrapData(nextId++, owner, name, pos1, pos2, price);
        traps.put(d.getId(), d);
        save(d);
        return d;
    }

    public boolean delete(int id) {
        TrapData d = traps.remove(id);
        if (d == null) return false;
        new File(folder, id + ".yml").delete();
        return true;
    }

    public TrapData get(int id) { return traps.get(id); }
    public Collection<TrapData> all() { return traps.values(); }

    public List<TrapData> ofPlayer(UUID owner) {
        List<TrapData> l = new ArrayList<>();
        for (TrapData d : traps.values()) if (d.getOwner().equals(owner)) l.add(d);
        return l;
    }

    public TrapData at(Location loc) {
        for (TrapData d : traps.values()) if (d.contains(loc)) return d;
        return null;
    }

    public List<TrapData> market() {
        List<TrapData> l = new ArrayList<>();
        for (TrapData d : traps.values()) if (d.isForSale()) l.add(d);
        return l;
    }

    public void setP1(UUID u, Location l) { p1.put(u, l); }
    public void setP2(UUID u, Location l) { p2.put(u, l); }
    public Location getP1(UUID u) { return p1.get(u); }
    public Location getP2(UUID u) { return p2.get(u); }
    public boolean hasBoth(UUID u) { return p1.containsKey(u) && p2.containsKey(u); }
    public void clearSel(UUID u) { p1.remove(u); p2.remove(u); }

    public boolean deposit(int id, double a) {
        TrapData d = traps.get(id); if (d == null) return false;
        d.setBank(d.getBank() + a); save(d); return true;
    }
    public boolean withdraw(int id, double a) {
        TrapData d = traps.get(id); if (d == null || d.getBank() < a) return false;
        d.setBank(d.getBank() - a); save(d); return true;
    }
    public boolean invite(int id, UUID u) {
        TrapData d = traps.get(id); if (d == null || d.isMember(u)) return false;
        d.addMember(u); save(d); return true;
    }
    public boolean kick(int id, UUID u) {
        TrapData d = traps.get(id); if (d == null || !d.getMembers().contains(u)) return false;
        d.removeMember(u); save(d); return true;
    }
    public void listMarket(int id, double price) {
        TrapData d = traps.get(id); if (d == null) return;
        d.setForSale(true); d.setSalePrice(price); save(d);
    }
    public void unlistMarket(int id) {
        TrapData d = traps.get(id); if (d == null) return;
        d.setForSale(false); save(d);
    }

    public void save(TrapData d) {
        File f = new File(folder, d.getId() + ".yml");
        YamlConfiguration c = new YamlConfiguration();
        c.set("id", d.getId()); c.set("owner", d.getOwner().toString());
        c.set("ownerName", d.getOwnerName()); c.set("bank", d.getBank());
        c.set("forSale", d.isForSale()); c.set("salePrice", d.getSalePrice());
        c.set("pos1.world", d.getPos1().getWorld().getName());
        c.set("pos1.x", d.getPos1().getBlockX()); c.set("pos1.y", d.getPos1().getBlockY()); c.set("pos1.z", d.getPos1().getBlockZ());
        c.set("pos2.world", d.getPos2().getWorld().getName());
        c.set("pos2.x", d.getPos2().getBlockX()); c.set("pos2.y", d.getPos2().getBlockY()); c.set("pos2.z", d.getPos2().getBlockZ());
        List<String> ml = new ArrayList<>(); for (UUID u : d.getMembers()) ml.add(u.toString()); c.set("members", ml);
        if (d.getSpawn() != null) {
            c.set("spawn.world", d.getSpawn().getWorld().getName());
            c.set("spawn.x", d.getSpawn().getX()); c.set("spawn.y", d.getSpawn().getY()); c.set("spawn.z", d.getSpawn().getZ());
            c.set("spawn.yaw", (double)d.getSpawn().getYaw()); c.set("spawn.pitch", (double)d.getSpawn().getPitch());
        }
        try { c.save(f); } catch (IOException e) { plugin.getLogger().warning("Trap kayit hatasi: " + d.getId()); }
    }

    private void load() {
        File[] files = folder.listFiles((d,n) -> n.endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            try {
                YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
                int id = c.getInt("id");
                UUID owner = UUID.fromString(c.getString("owner",""));
                String ownerName = c.getString("ownerName","?");
                World w1 = Bukkit.getWorld(c.getString("pos1.world","")); if (w1==null) continue;
                World w2 = Bukkit.getWorld(c.getString("pos2.world","")); if (w2==null) continue;
                Location pos1 = new Location(w1,c.getInt("pos1.x"),c.getInt("pos1.y"),c.getInt("pos1.z"));
                Location pos2 = new Location(w2,c.getInt("pos2.x"),c.getInt("pos2.y"),c.getInt("pos2.z"));
                TrapData d = new TrapData(id,owner,ownerName,pos1,pos2,c.getDouble("salePrice",0));
                d.setBank(c.getDouble("bank",0)); d.setForSale(c.getBoolean("forSale",false));
                for (String s : c.getStringList("members")) { try { d.addMember(UUID.fromString(s)); } catch(Exception ignored){} }
                if (c.contains("spawn")) {
                    World sw = Bukkit.getWorld(c.getString("spawn.world","")); if (sw!=null)
                    d.setSpawn(new Location(sw,c.getDouble("spawn.x"),c.getDouble("spawn.y"),c.getDouble("spawn.z"),(float)c.getDouble("spawn.yaw"),(float)c.getDouble("spawn.pitch")));
                }
                traps.put(id,d); if (id>=nextId) nextId=id+1;
            } catch(Exception e) { plugin.getLogger().warning("Trap yuklenemedi: "+f.getName()); }
        }
        plugin.getLogger().info("[Trap] "+traps.size()+" trap yuklendi.");
    }

    public void saveAll() { for (TrapData d : traps.values()) save(d); }
              }

