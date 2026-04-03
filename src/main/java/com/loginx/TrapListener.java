package com.loginx.trap;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.UUID;

/**
 * Tüm olayları dinleyen sınıf:
 *  - Balta ile WorldEdit benzeri seçim (Sol tık = Pos1, Sağ tık = Pos2)
 *  - Trap koruma: eşya kullanımı, kapı, blok kırma, blok koyma engeli
 *  - GUI tıklamaları
 *  - Oyuncu ölümü (para düşürme engeli)
 */
public class TrapListener implements Listener {

    private final TrapManager manager;
    private final TrapGUI gui;
    private final EconomyBridge eco;
    private final TrapCommand trapCommand;
    private final TrapHologramManager hologramManager;

    // Satın alma onayı bekleyen oyuncular: UUID -> TrapID
    private final HashMap<UUID, Integer> pendingPurchase = new HashMap<>();

    public TrapListener(TrapManager manager, TrapGUI gui, EconomyBridge eco,
                        TrapCommand trapCommand, TrapHologramManager hologramManager) {
        this.manager = manager;
        this.gui = gui;
        this.eco = eco;
        this.trapCommand = trapCommand;
        this.hologramManager = hologramManager;
    }

    // =========================================================
    //  BALTA SEÇİMİ (WorldEdit mantığı)
    // =========================================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPermission("loginx.trap.admin")) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() != Material.WOODEN_AXE &&
            hand.getType() != Material.IRON_AXE &&
            hand.getType() != Material.GOLDEN_AXE &&
            hand.getType() != Material.DIAMOND_AXE &&
            hand.getType() != Material.NETHERITE_AXE) return;

        if (e.getClickedBlock() == null) return;

        Action action = e.getAction();
        Location loc = e.getClickedBlock().getLocation();

        if (action == Action.LEFT_CLICK_BLOCK) {
            manager.setPos1(p.getUniqueId(), loc);
            p.sendMessage(color("&d[Trap] &aPozisyon 1 seçildi: &f"
                    + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()));
            e.setCancelled(true);

        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            manager.setPos2(p.getUniqueId(), loc);
            p.sendMessage(color("&d[Trap] &aPozisyon 2 seçildi: &f"
                    + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()));
            e.setCancelled(true);
        }
    }

    // =========================================================
    //  TRAP KORUMASI - BLOK KIRMA
    // =========================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("loginx.trap.admin")) return; // Yetkililere izin ver

        TrapData td = manager.getTrapAt(e.getBlock().getLocation());
        if (td == null) return;

        if (!td.isMember(p.getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(color("&cX &cBu trap içerisindeki eşyaları/kapıları kullanamazsınız!"));
        }
    }

    // =========================================================
    //  TRAP KORUMASI - BLOK KOYMA
    // =========================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("loginx.trap.admin")) return;

        TrapData td = manager.getTrapAt(e.getBlock().getLocation());
        if (td == null) return;

        if (!td.isMember(p.getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(color("&cX &cBu trap içerisindeki eşyaları/kapıları kullanamazsınız!"));
        }
    }

    // =========================================================
    //  TRAP KORUMASI - EŞYA / KAPI KULLANIMI
    // =========================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractInTrap(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("loginx.trap.admin")) return;
        if (e.getClickedBlock() == null) return;

        Material type = e.getClickedBlock().getType();

        // Sadece interaktif bloklara uygula
        if (!isInteractable(type)) return;

        TrapData td = manager.getTrapAt(e.getClickedBlock().getLocation());
        if (td == null) return;

        if (!td.isMember(p.getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(color("&cX &cBu trap içerisindeki eşyaları/kapıları kullanamazsınız!"));
        }
    }

    private boolean isInteractable(Material m) {
        String name = m.name();
        return name.contains("CHEST") || name.contains("DOOR") || name.contains("GATE") ||
               name.contains("TRAPDOOR") || name.contains("BUTTON") || name.contains("LEVER") ||
               name.contains("BARREL") || name.contains("FURNACE") || name.contains("HOPPER") ||
               m == Material.CRAFTING_TABLE || m == Material.ANVIL || m == Material.ENCHANTING_TABLE ||
               m == Material.BREWING_STAND || m == Material.BEACON || m == Material.ENDER_CHEST ||
               m == Material.SHULKER_BOX;
    }

    // =========================================================
    //  TRAP KORUMASI - TNT & PATLAMA
    // =========================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onExplosion(EntityExplodeEvent e) {
        e.blockList().removeIf(block -> {
            TrapData td = manager.getTrapAt(block.getLocation());
            return td != null; // Trap içindeki bloklar patlamadan etkilenmesin
        });
    }

    // =========================================================
    //  GUI TIKLAMALARI
    // =========================================================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        String title = e.getView().getTitle();

        // ── MARKET GUI ──
        if (title.startsWith(TrapGUI.MARKET_TITLE)) {
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            if (clicked.getType() == Material.CHEST && clicked.getItemMeta() != null) {
                // ID'yi lore'dan al
                String idLine = null;
                for (String line : clicked.getItemMeta().getLore()) {
                    if (line.startsWith("§8ID: ")) { idLine = line.replace("§8ID: ", ""); break; }
                }
                if (idLine == null) return;
                try {
                    int trapId = Integer.parseInt(idLine);
                    TrapData td = manager.getTrap(trapId);
                    if (td == null) return;
                    // Onay menüsünü aç
                    pendingPurchase.put(p.getUniqueId(), trapId);
                    gui.openConfirm(p, td);
                } catch (NumberFormatException ignored) {}
            }
            return;
        }

        // ── ONAY GUI ──
        if (title.startsWith(TrapGUI.CONFIRM_TITLE)) {
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null) return;

            if (clicked.getType() == Material.LIME_STAINED_GLASS_PANE) {
                // Onayla → satın al
                Integer trapId = pendingPurchase.get(p.getUniqueId());
                if (trapId == null) { p.closeInventory(); return; }
                TrapData td = manager.getTrap(trapId);
                if (td == null || !td.isForSale()) {
                    p.sendMessage(color("&cBu trap artık satışta değil!"));
                    p.closeInventory();
                    pendingPurchase.remove(p.getUniqueId());
                    return;
                }
                if (td.getOwner().equals(p.getUniqueId())) {
                    p.sendMessage(color("&cKendi trapınızı satın alamazsınız!"));
                    p.closeInventory();
                    return;
                }
                double price = td.getSalePrice();
                if (!eco.withdraw(p, price)) {
                    p.sendMessage(color("&cYetersiz bakiye! Gereken: &6$" + String.format("%.0f", price)));
                    p.closeInventory();
                    return;
                }
                // Eski sahibine parayı ver
                Player oldOwner = Bukkit.getPlayer(td.getOwner());
                if (oldOwner != null) {
                    eco.deposit(oldOwner, price);
                    oldOwner.sendMessage(color("&aTrap #" + td.getId() + " &f"
                            + p.getName() + "&a'ya &6$" + String.format("%.0f", price) + " &akarşılığında satıldı!"));
                }
                // Sahiplik devret
                td.setOwner(p.getUniqueId());
                td.setOwnerName(p.getName());
                td.setForSale(false);
                manager.saveTrap(td);
                manager.removeFromMarket(td.getId());
                hologramManager.updateHologram(td);

                p.sendMessage(color("&a&lTebrikler! Trap #" + td.getId() + " artık sizin!"));
                p.closeInventory();
                pendingPurchase.remove(p.getUniqueId());

                // Trapa TP
                Location tpLoc = td.getSpawnLocation() != null ? td.getSpawnLocation()
                        : td.getPos1().clone().add(0.5, 1, 0.5);
                p.teleport(tpLoc);

            } else if (clicked.getType() == Material.RED_STAINED_GLASS_PANE) {
                pendingPurchase.remove(p.getUniqueId());
                p.closeInventory();
                gui.openMarket(p); // Markete geri dön
            }
            return;
        }

        // ── TRAP MENÜSÜ GUI ──
        if (title.startsWith(TrapGUI.TRAP_MENU_TITLE)) {
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null) return;

            // Hangi trap ID'si aktif?
            Integer trapId = trapCommand.getPlayerActiveTrap().get(p.getUniqueId());
            TrapData td = trapId != null ? manager.getTrap(trapId) : manager.getTrapAt(p.getLocation());
            if (td == null) { p.closeInventory(); return; }

            switch (clicked.getType()) {
                case ENDER_PEARL -> {
                    // Trapa TP
                    Location tpLoc = td.getSpawnLocation() != null ? td.getSpawnLocation()
                            : td.getPos1().clone().add(0.5, 1, 0.5);
                    p.closeInventory();
                    p.teleport(tpLoc);
                    p.sendMessage(color("&aTrapa ışınlandınız!"));
                }
                case GOLD_INGOT -> {
                    p.closeInventory();
                    p.sendMessage(color("&7Para yatırmak için: &e/trap parayatir <miktar>"));
                }
                case GOLD_NUGGET -> {
                    p.closeInventory();
                    p.sendMessage(color("&7Para çekmek için: &e/trap paracek <miktar>"));
                }
                case PLAYER_HEAD -> {
                    p.closeInventory();
                    p.sendMessage(color("&7Üye davet için: &e/trap davet <oyuncu>"));
                }
                case BARRIER -> {
                    p.closeInventory();
                    p.sendMessage(color("&7Üye atmak için: &e/trap kick <oyuncu>"));
                }
                case GREEN_BANNER -> {
                    p.closeInventory();
                    p.sendMessage(color("&7Satışa çıkarmak için: &e/trap sat <fiyat>"));
                }
                case RED_BANNER -> {
                    td.setForSale(false);
                    manager.removeFromMarket(td.getId());
                    p.sendMessage(color("&cTrap satıştan çekildi."));
                    gui.openTrapMenu(p, td);
                }
                case RED_STAINED_GLASS_PANE -> p.closeInventory();
                default -> {}
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        // Onay beklenirken kapatılırsa temizle
        if (e.getView().getTitle().startsWith(TrapGUI.CONFIRM_TITLE)) {
            pendingPurchase.remove(e.getPlayer().getUniqueId());
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
              }

