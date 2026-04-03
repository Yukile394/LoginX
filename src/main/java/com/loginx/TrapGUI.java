package com.loginx.trap;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Tüm trap GUI menülerini yöneten sınıf.
 * Fotoğraftaki gibi: market listesi, trap bilgi paneli, küçük trap menüsü.
 */
public class TrapGUI {

    // GUI başlıkları (tanıma için kullanılır)
    public static final String MARKET_TITLE    = "§5§lTüm Trapler";
    public static final String TRAP_MENU_TITLE = "§d§lTrap Menüsü";
    public static final String CONFIRM_TITLE   = "§4§lSatın Al - Onayla";

    private final TrapManager manager;
    private final EconomyBridge eco;

    public TrapGUI(TrapManager manager, EconomyBridge eco) {
        this.manager = manager;
        this.eco = eco;
    }

    // =========================================================
    //  1. MARKET GUI  (fotoğraftaki "Tüm Trapler" menüsü)
    // =========================================================

    public void openMarket(Player player) {
        List<TrapData> market = manager.getMarketTraps();
        int size = Math.max(54, (int) Math.ceil((market.size() + 9) / 9.0) * 9);
        if (size > 54) size = 54;

        Inventory inv = Bukkit.createInventory(null, size, MARKET_TITLE);

        // Arka plan (mor/gri cam)
        ItemStack bg = makeItem(Material.GRAY_STAINED_GLASS_PANE, "§r", null);
        for (int i = 45; i < size; i++) inv.setItem(i, bg);

        // Trapler (Sandık ikonu)
        int slot = 0;
        for (TrapData td : market) {
            if (slot >= 45) break;
            inv.setItem(slot, makeTrapMarketItem(td));
            slot++;
        }

        // Boş bilgi öğesi
        if (market.isEmpty()) {
            inv.setItem(22, makeItem(Material.BARRIER,
                    "§c§lSatıştaki Trap Yok",
                    Arrays.asList("§7Henüz satıştaki trap bulunmuyor.")));
        }

        // Kapat butonu
        inv.setItem(size - 1, makeItem(Material.RED_STAINED_GLASS_PANE, "§c§lKapat", null));

        player.openInventory(inv);
    }

    private ItemStack makeTrapMarketItem(TrapData td) {
        List<String> lore = new ArrayList<>();
        lore.add("§5§m                    ");
        lore.add("§dSahip: §f" + td.getOwnerName());
        lore.add("§dSatılık: §aEvet");
        lore.add("§dFiyat: §6" + String.format("%.0f", td.getSalePrice()));
        lore.add("§dBoyut: §f" + td.getSizeString());
        lore.add("§dÜye: §f" + td.getMemberCount());
        lore.add("§dBanka: §a$" + String.format("%.0f", td.getBank()));
        lore.add("§5§m                    ");
        lore.add("§e§lTıklayarak satın alın");
        lore.add("§8minecraft:chest");
        lore.add("§8ID: " + td.getId());
        return makeItem(Material.CHEST, "§5§lTrap §d" + td.getId(), lore);
    }

    // =========================================================
    //  2. TRAP ANA MENÜSÜ  (trap sahibi/üyesi için)
    // =========================================================

    public void openTrapMenu(Player player, TrapData td) {
        Inventory inv = Bukkit.createInventory(null, 27, TRAP_MENU_TITLE + " §8#" + td.getId());

        // Arka plan
        ItemStack bg = makeItem(Material.PURPLE_STAINED_GLASS_PANE, "§r", null);
        for (int i = 0; i < 27; i++) inv.setItem(i, bg);

        boolean isOwner = td.getOwner().equals(player.getUniqueId());

        // Ortada Trap Bilgisi
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§5§m                    ");
        infoLore.add("§dSahip: §f" + td.getOwnerName());
        infoLore.add("§dBanka: §a$" + String.format("%.2f", td.getBank()));
        infoLore.add("§dÜye: §f" + td.getMemberCount());
        infoLore.add("§dBoyut: §f" + td.getSizeString());
        infoLore.add("§dSatılık: " + (td.isForSale() ? "§aEvet §7($" + String.format("%.0f", td.getSalePrice()) + ")" : "§cHayır"));
        infoLore.add("§5§m                    ");
        inv.setItem(13, makeItem(Material.NETHER_STAR, "§d§lTrap #" + td.getId(), infoLore));

        // Banka: para yatır
        inv.setItem(10, makeItem(Material.GOLD_INGOT, "§6§lPara Yatır",
                Arrays.asList("§7/trap parayatir <miktar>", "§7veya buraya tıkla")));

        // Banka: para çek
        inv.setItem(11, makeItem(Material.GOLD_NUGGET, "§e§lPara Çek",
                Arrays.asList("§7/trap paracek <miktar>", "§7veya buraya tıkla")));

        // TP: Trapa git
        inv.setItem(15, makeItem(Material.ENDER_PEARL, "§b§lTrapa Işınlan",
                Arrays.asList("§7Trapın spawn noktasına ışınlan.")));

        if (isOwner) {
            // Üye davet
            inv.setItem(16, makeItem(Material.PLAYER_HEAD, "§a§lÜye Davet Et",
                    Arrays.asList("§7/trap davet <oyuncu>")));

            // Üye at
            inv.setItem(12, makeItem(Material.BARRIER, "§c§lÜye At",
                    Arrays.asList("§7/trap kick <oyuncu>")));

            // Satışa çıkar / kaldır
            if (td.isForSale()) {
                inv.setItem(14, makeItem(Material.RED_BANNER, "§c§lSatıştan Kaldır",
                        Arrays.asList("§7/trap market kaldır")));
            } else {
                inv.setItem(14, makeItem(Material.GREEN_BANNER, "§a§lSatışa Çıkar",
                        Arrays.asList("§7/trap sat <fiyat>")));
            }
        }

        // Kapat
        inv.setItem(26, makeItem(Material.RED_STAINED_GLASS_PANE, "§c§lKapat", null));

        player.openInventory(inv);
    }

    // =========================================================
    //  3. ONAY MENÜSÜ (satın alma)
    // =========================================================

    public void openConfirm(Player player, TrapData td) {
        Inventory inv = Bukkit.createInventory(null, 27, CONFIRM_TITLE);

        ItemStack bg = makeItem(Material.BLACK_STAINED_GLASS_PANE, "§r", null);
        for (int i = 0; i < 27; i++) inv.setItem(i, bg);

        List<String> infoLore = new ArrayList<>();
        infoLore.add("§5§m                    ");
        infoLore.add("§dSahip: §f" + td.getOwnerName());
        infoLore.add("§dFiyat: §6$" + String.format("%.0f", td.getSalePrice()));
        infoLore.add("§dBoyut: §f" + td.getSizeString());
        infoLore.add("§dBanka: §a$" + String.format("%.0f", td.getBank()));
        infoLore.add("§5§m                    ");
        inv.setItem(13, makeItem(Material.CHEST, "§5§lTrap #" + td.getId(), infoLore));

        // Onayla (yeşil)
        inv.setItem(11, makeItem(Material.LIME_STAINED_GLASS_PANE, "§a§l✔ SATIN AL",
                Arrays.asList("§7$" + String.format("%.0f", td.getSalePrice()) + " ödenecek.",
                        "§aOnaylamak için tıkla!")));

        // İptal (kırmızı)
        inv.setItem(15, makeItem(Material.RED_STAINED_GLASS_PANE, "§c§l✘ İPTAL",
                Arrays.asList("§7Satın almaktan vazgeç.")));

        // Gizli ID bilgisi
        inv.setItem(0, makeItem(Material.PAPER, "§8TrapID:" + td.getId(), null));

        player.openInventory(inv);
    }

    // =========================================================
    //  YARDIMCI: ItemStack oluştur
    // =========================================================

    public static ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}

