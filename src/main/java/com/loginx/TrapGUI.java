package com.loginx;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class TrapGUI {

    public static final String MARKET_TITLE  = "\u00a75\u00a7lTum Trapler";
    public static final String MENU_TITLE    = "\u00a7d\u00a7lTrap Menusu";
    public static final String CONFIRM_TITLE = "\u00a74\u00a7lSatin Al - Onayla";

    private final TrapManager manager;
    private final EconomyBridge eco;

    public TrapGUI(TrapManager manager, EconomyBridge eco) {
        this.manager = manager;
        this.eco = eco;
    }

    // ── Market GUI (fotodaki gibi) ────────────────────────────
    public void openMarket(Player player) {
        List<TrapData> market = manager.getMarketTraps();
        int pages = Math.max(1, (int) Math.ceil(market.size() / 45.0));
        Inventory inv = Bukkit.createInventory(null, 54,
                MARKET_TITLE + " \u00a78- Sayfa 1/" + pages);

        ItemStack bg = makeItem(Material.GRAY_STAINED_GLASS_PANE, "\u00a7r", null);
        for (int i = 45; i < 54; i++) inv.setItem(i, bg);

        int slot = 0;
        for (TrapData td : market) {
            if (slot >= 45) break;
            inv.setItem(slot++, makeTrapItem(td));
        }

        if (market.isEmpty()) {
            inv.setItem(22, makeItem(Material.BARRIER,
                    "\u00a7c\u00a7lSatistaki Trap Yok",
                    Collections.singletonList("\u00a77Henuez satilik trap bulunmuyor.")));
        }

        inv.setItem(53, makeItem(Material.RED_STAINED_GLASS_PANE,
                "\u00a7c\u00a7lKapat", null));
        player.openInventory(inv);
    }

    private ItemStack makeTrapItem(TrapData td) {
        List<String> lore = new ArrayList<>();
        lore.add("\u00a75\u00a7m                    ");
        lore.add("\u00a7dSahibi: \u00a7f" + td.getOwnerName());
        lore.add("\u00a7dSatilik: \u00a7aEvet");
        lore.add("\u00a7dFiyat: \u00a76" + String.format("%.0f", td.getSalePrice()));
        lore.add("\u00a7dBoyut: \u00a7f" + td.getSizeString());
        lore.add("\u00a7dUye: \u00a7f" + td.getMemberCount());
        lore.add("\u00a7dBanka: \u00a7a$" + String.format("%.0f", td.getBank()));
        lore.add("\u00a75\u00a7m                    ");
        lore.add("\u00a7e\u00a7lTiklayarak satin alin");
        lore.add("\u00a77minecraft:chest");
        lore.add("\u00a78ID:" + td.getId());
        return makeItem(Material.CHEST,
                "\u00a75\u00a7lTrap \u00a7d#" + td.getId(), lore);
    }

    // ── Trap ana menusu ───────────────────────────────────────
    public void openTrapMenu(Player player, TrapData td) {
        Inventory inv = Bukkit.createInventory(null, 27,
                MENU_TITLE + " \u00a78#" + td.getId());

        ItemStack bg = makeItem(Material.PURPLE_STAINED_GLASS_PANE, "\u00a7r", null);
        for (int i = 0; i < 27; i++) inv.setItem(i, bg);

        boolean isOwner = td.getOwner().equals(player.getUniqueId());

        // Bilgi itemi
        List<String> infoLore = new ArrayList<>();
        infoLore.add("\u00a75\u00a7m                    ");
        infoLore.add("\u00a7dSahip: \u00a7f" + td.getOwnerName());
        infoLore.add("\u00a7dBanka: \u00a7a$" + String.format("%.2f", td.getBank()));
        infoLore.add("\u00a7dUye: \u00a7f" + td.getMemberCount());
        infoLore.add("\u00a7dBoyut: \u00a7f" + td.getSizeString());
        infoLore.add("\u00a7dSatilik: " + (td.isForSale()
                ? "\u00a7aEvet \u00a77($" + String.format("%.0f", td.getSalePrice()) + ")"
                : "\u00a7cHayir"));
        infoLore.add("\u00a75\u00a7m                    ");
        inv.setItem(13, makeItem(Material.NETHER_STAR,
                "\u00a7d\u00a7lTrap #" + td.getId(), infoLore));

        // Para yatir
        inv.setItem(10, makeItem(Material.GOLD_INGOT, "\u00a76\u00a7lPara Yatir",
                Collections.singletonList("\u00a77/trap parayatir <miktar>")));
        // Para cek
        inv.setItem(11, makeItem(Material.GOLD_NUGGET, "\u00a7e\u00a7lPara Cek",
                Collections.singletonList("\u00a77/trap paracek <miktar>")));
        // TP
        inv.setItem(15, makeItem(Material.ENDER_PEARL, "\u00a7b\u00a7lTrapa Isinlan",
                Collections.singletonList("\u00a77Trap spawn noktasina isinlan.")));

        if (isOwner) {
            // Uye davet
            inv.setItem(16, makeItem(Material.PLAYER_HEAD, "\u00a7a\u00a7lUye Davet",
                    Collections.singletonList("\u00a77/trap davet <oyuncu>")));
            // Uye at
            inv.setItem(12, makeItem(Material.BARRIER, "\u00a7c\u00a7lUye At",
                    Collections.singletonList("\u00a77/trap kick <oyuncu>")));
            // Satis durumu
            if (td.isForSale()) {
                inv.setItem(14, makeItem(Material.RED_BANNER, "\u00a7c\u00a7lSatistan Kaldir",
                        Collections.singletonList("\u00a77/trap gericek")));
            } else {
                inv.setItem(14, makeItem(Material.GREEN_BANNER, "\u00a7a\u00a7lSatisa Cikar",
                        Collections.singletonList("\u00a77/trap sat <fiyat>")));
            }
        }

        inv.setItem(26, makeItem(Material.RED_STAINED_GLASS_PANE,
                "\u00a7c\u00a7lKapat", null));
        player.openInventory(inv);
    }

    // ── Onay menusu ───────────────────────────────────────────
    public void openConfirm(Player player, TrapData td) {
        Inventory inv = Bukkit.createInventory(null, 27, CONFIRM_TITLE);

        ItemStack bg = makeItem(Material.BLACK_STAINED_GLASS_PANE, "\u00a7r", null);
        for (int i = 0; i < 27; i++) inv.setItem(i, bg);

        List<String> lore = new ArrayList<>();
        lore.add("\u00a75\u00a7m                    ");
        lore.add("\u00a7dSahip: \u00a7f" + td.getOwnerName());
        lore.add("\u00a7dFiyat: \u00a76$" + String.format("%.0f", td.getSalePrice()));
        lore.add("\u00a7dBoyut: \u00a7f" + td.getSizeString());
        lore.add("\u00a7dBanka: \u00a7a$" + String.format("%.0f", td.getBank()));
        lore.add("\u00a75\u00a7m                    ");
        inv.setItem(13, makeItem(Material.CHEST,
                "\u00a75\u00a7lTrap #" + td.getId(), lore));

        inv.setItem(11, makeItem(Material.LIME_STAINED_GLASS_PANE,
                "\u00a7a\u00a7l\u2714 SATIN AL",
                Arrays.asList(
                        "\u00a77$" + String.format("%.0f", td.getSalePrice()) + " odenecek.",
                        "\u00a7aOnaylamak icin tikla!")));
        inv.setItem(15, makeItem(Material.RED_STAINED_GLASS_PANE,
                "\u00a7c\u00a7l\u2718 IPTAL",
                Collections.singletonList("\u00a77Satin almaktan vazgec.")));

        // Gizli ID taşıyıcı
        inv.setItem(0, makeItem(Material.PAPER,
                "\u00a78TrapID:" + td.getId(), null));
        player.openInventory(inv);
    }

    // ── Yardimci ─────────────────────────────────────────────
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
