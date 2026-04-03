package com.loginx;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class TrapListener implements Listener {

    private final TrapManager manager;
    private final TrapGUI gui;
    private final EconomyBridge eco;
    private final TrapCommand trapCommand;
    private final TrapHologramManager hologramManager;

    // Satin alma onay bekleyen: UUID -> TrapID
    private final HashMap<UUID, Integer> pendingPurchase = new HashMap<>();

    public TrapListener(TrapManager manager, TrapGUI gui, EconomyBridge eco,
                        TrapCommand trapCommand, TrapHologramManager hologramManager) {
        this.manager = manager;
        this.gui = gui;
        this.eco = eco;
        this.trapCommand = trapCommand;
        this.hologramManager = hologramManager;
    }

    // ── Balta secimi ─────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAxeClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPermission("loginx.trap.admin")) return;
        if (e.getClickedBlock() == null) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        String matName = hand.getType().name();
        if (!matName.endsWith("_AXE")) return;

        Location loc = e.getClickedBlock().getLocation();

        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            manager.setPos1(p.getUniqueId(), loc);
            p.sendMessage(c("&d[Trap] &aPozisyon 1: &f"
                    + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()));
            e.setCancelled(true);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            manager.setPos2(p.getUniqueId(), loc);
            p.sendMessage(c("&d[Trap] &aPozisyon 2: &f"
                    + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()));
            e.setCancelled(true);
        }
    }

    // ── Koruma: blok kirma ────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("loginx.trap.admin")) return;
        TrapData td = manager.getTrapAt(e.getBlock().getLocation());
        if (td == null) return;
        if (!td.isMember(p.getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(c("&cX &cBu trap icerisindeki esyalari/kapilari kullanamazsiniz!"));
        }
    }

    // ── Koruma: blok koyma ────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("loginx.trap.admin")) return;
        TrapData td = manager.getTrapAt(e.getBlock().getLocation());
        if (td == null) return;
        if (!td.isMember(p.getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(c("&cX &cBu trap icerisindeki esyalari/kapilari kullanamazsiniz!"));
        }
    }

    // ── Koruma: etkilesim (kapi, sandik, vb.) ────────────────
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("loginx.trap.admin")) return;
        if (e.getClickedBlock() == null) return;
        if (!isProtectedBlock(e.getClickedBlock().getType())) return;

        TrapData td = manager.getTrapAt(e.getClickedBlock().getLocation());
        if (td == null) return;
        if (!td.isMember(p.getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(c("&cX &cBu trap icerisindeki esyalari/kapilari kullanamazsiniz!"));
        }
    }

    private boolean isProtectedBlock(Material m) {
        String n = m.name();
        return n.contains("CHEST") || n.contains("DOOR") || n.contains("GATE")
                || n.contains("TRAPDOOR") || n.contains("BUTTON") || n.contains("LEVER")
                || n.contains("BARREL") || n.contains("FURNACE") || n.contains("HOPPER")
                || m == Material.CRAFTING_TABLE || m == Material.ANVIL
                || m == Material.ENCHANTING_TABLE || m == Material.BREWING_STAND
                || m == Material.BEACON || m == Material.ENDER_CHEST;
    }

    // ── Koruma: patlama ───────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH)
    public void onExplosion(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> manager.getTrapAt(b.getLocation()) != null);
    }

    // ── GUI tıklamalari ───────────────────────────────────────
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();

        // ── Market ──
        if (title.startsWith(TrapGUI.MARKET_TITLE)) {
            e.setCancelled(true);
            ItemStack item = e.getCurrentItem();
            if (item == null || item.getType() != Material.CHEST) return;
            if (item.getItemMeta() == null) return;

            String idLine = null;
            for (String line : item.getItemMeta().getLore()) {
                if (line.startsWith("\u00a78ID:")) {
                    idLine = line.replace("\u00a78ID:", "");
                    break;
                }
            }
            if (idLine == null) return;
            try {
                int trapId = Integer.parseInt(idLine.trim());
                TrapData td = manager.getTrap(trapId);
                if (td == null) return;
                pendingPurchase.put(p.getUniqueId(), trapId);
                gui.openConfirm(p, td);
            } catch (NumberFormatException ignored) {}
            return;
        }

        // ── Onay ──
        if (title.equals(TrapGUI.CONFIRM_TITLE)) {
            e.setCancelled(true);
            ItemStack item = e.getCurrentItem();
            if (item == null) return;

            if (item.getType() == Material.LIME_STAINED_GLASS_PANE) {
                Integer trapId = pendingPurchase.get(p.getUniqueId());
                if (trapId == null) { p.closeInventory(); return; }
                TrapData td = manager.getTrap(trapId);

                if (td == null || !td.isForSale()) {
                    p.sendMessage(c("&cBu trap artik satista degil!"));
                    p.closeInventory();
                    pendingPurchase.remove(p.getUniqueId());
                    return;
                }
                if (td.getOwner().equals(p.getUniqueId())) {
                    p.sendMessage(c("&cKendi trapinizi satin alamazsiniz!"));
                    p.closeInventory();
                    return;
                }
                if (!eco.withdraw(p, td.getSalePrice())) {
                    p.sendMessage(c("&cYetersiz bakiye! Gereken: &6$"
                            + String.format("%.0f", td.getSalePrice())));
                    p.closeInventory();
                    return;
                }

                // Eski sahibine para ver
                Player oldOwner = Bukkit.getPlayer(td.getOwner());
                if (oldOwner != null) {
                    eco.deposit(oldOwner, td.getSalePrice());
                    oldOwner.sendMessage(c("&aTrap #" + td.getId() + " &f" + p.getName()
                            + "&a'ya &6$" + String.format("%.0f", td.getSalePrice())
                            + " &akarsiliginda satildi!"));
                }

                // Sahiplik devret
                td.setOwner(p.getUniqueId());
                td.setOwnerName(p.getName());
                td.setForSale(false);
                manager.saveTrap(td);
                manager.removeFromMarket(td.getId());
                hologramManager.updateHologram(td);

                p.sendMessage(c("&a&lTebrikler! Trap #" + td.getId() + " artik sizin!"));
                p.closeInventory();
                pendingPurchase.remove(p.getUniqueId());

                // Trapa isinla
                Location tpLoc = td.getSpawnLocation() != null
                        ? td.getSpawnLocation()
                        : td.getPos1().clone().add(0.5, 1, 0.5);
                p.teleport(tpLoc);

            } else if (item.getType() == Material.RED_STAINED_GLASS_PANE) {
                pendingPurchase.remove(p.getUniqueId());
                p.closeInventory();
                gui.openMarket(p);
            }
            return;
        }

        // ── Trap menusu ──
        if (title.startsWith(TrapGUI.MENU_TITLE)) {
            e.setCancelled(true);
            ItemStack item = e.getCurrentItem();
            if (item == null) return;

            Integer trapId = trapCommand.getActiveTrap().get(p.getUniqueId());
            TrapData td = trapId != null
                    ? manager.getTrap(trapId)
                    : manager.getTrapAt(p.getLocation());
            if (td == null) { p.closeInventory(); return; }

            switch (item.getType()) {
                case ENDER_PEARL -> {
                    p.closeInventory();
                    Location tpLoc = td.getSpawnLocation() != null
                            ? td.getSpawnLocation()
                            : td.getPos1().clone().add(0.5, 1, 0.5);
                    p.teleport(tpLoc);
                    p.sendMessage(c("&aTrapa isinlandiniz!"));
                }
                case GOLD_INGOT -> {
                    p.closeInventory();
                    p.sendMessage(c("&7Kullanim: &e/trap parayatir <miktar>"));
                }
                case GOLD_NUGGET -> {
                    p.closeInventory();
                    p.sendMessage(c("&7Kullanim: &e/trap paracek <miktar>"));
                }
                case PLAYER_HEAD -> {
                    p.closeInventory();
                    p.sendMessage(c("&7Kullanim: &e/trap davet <oyuncu>"));
                }
                case BARRIER -> {
                    p.closeInventory();
                    p.sendMessage(c("&7Kullanim: &e/trap kick <oyuncu>"));
                }
                case GREEN_BANNER -> {
                    p.closeInventory();
                    p.sendMessage(c("&7Kullanim: &e/trap sat <fiyat>"));
                }
                case RED_BANNER -> {
                    manager.removeFromMarket(td.getId());
                    p.sendMessage(c("&cTrap satistan cekildi."));
                    gui.openTrapMenu(p, td);
                }
                case RED_STAINED_GLASS_PANE -> p.closeInventory();
                default -> {}
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getView().getTitle().equals(TrapGUI.CONFIRM_TITLE))
            pendingPurchase.remove(e.getPlayer().getUniqueId());
    }

    private String c(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
                        }
