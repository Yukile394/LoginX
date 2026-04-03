package com.loginx;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TrapData {

    private final int id;
    private UUID owner;
    private String ownerName;
    private double bank;
    private boolean forSale;
    private double salePrice;
    private Location pos1;
    private Location pos2;
    private Location spawnLocation;
    private final List<UUID> members = new ArrayList<>();

    public TrapData(int id, UUID owner, String ownerName,
                    Location pos1, Location pos2, double salePrice) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.salePrice = salePrice;
        this.bank = 0;
        this.forSale = false;
    }

    public boolean contains(Location loc) {
        if (!loc.getWorld().equals(pos1.getWorld())) return false;
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean isMember(UUID uuid) {
        return owner.equals(uuid) || members.contains(uuid);
    }
    public void addMember(UUID uuid) { if (!members.contains(uuid)) members.add(uuid); }
    public void removeMember(UUID uuid) { members.remove(uuid); }
    public List<UUID> getMembers() { return members; }
    public int getMemberCount() { return members.size() + 1; }

    public String getSizeString() {
        int dx = Math.abs(pos1.getBlockX() - pos2.getBlockX()) + 1;
        int dy = Math.abs(pos1.getBlockY() - pos2.getBlockY()) + 1;
        int dz = Math.abs(pos1.getBlockZ() - pos2.getBlockZ()) + 1;
        return dx + "x" + dy + "x" + dz;
    }

    public int getId() { return id; }
    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String n) { this.ownerName = n; }
    public double getBank() { return bank; }
    public void setBank(double bank) { this.bank = bank; }
    public boolean isForSale() { return forSale; }
    public void setForSale(boolean forSale) { this.forSale = forSale; }
    public double getSalePrice() { return salePrice; }
    public void setSalePrice(double salePrice) { this.salePrice = salePrice; }
    public Location getPos1() { return pos1; }
    public void setPos1(Location pos1) { this.pos1 = pos1; }
    public Location getPos2() { return pos2; }
    public void setPos2(Location pos2) { this.pos2 = pos2; }
    public Location getSpawnLocation() { return spawnLocation; }
    public void setSpawnLocation(Location spawnLocation) { this.spawnLocation = spawnLocation; }
}
