package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;

public class SwordCommand implements CommandExecutor, Listener {

    private final Plugin plugin;
    private final SwordManager sm;

    public SwordCommand(Plugin plugin, SwordManager sm) {
        this.plugin = plugin;
        this.sm = sm;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cSadece oyuncular kullanabilir!");
            return true;
        }
        Player p = (Player) sender;

        if (!p.hasPermission("loginx.admin")) {
            p.sendMessage("§cBu komutu kullanmak için yetkin yok!");
            return true;
        }

        switch (cmd.getName().toLowerCase()) {
            case "shulkerkilicver":
                sm.giveToPlayer(p, sm.makeShulkerSword());
                p.sendMessage("§5§lShulker Kılıcı §7verildi!");
                break;
            case "endermankilicver":
                sm.giveToPlayer(p, sm.makeEndermanSword());
                p.sendMessage("§1§lEnderman Kılıcı §7verildi!");
                break;
            case "orumcekkilicver":
                sm.giveToPlayer(p, sm.makeSpiderSword());
                p.sendMessage("§2§lÖrümcek Kılıcı §7verildi!");
                break;
            case "phantomkilicver":
                sm.giveToPlayer(p, sm.makePhantomSword());
                p.sendMessage("§3§lPhantom Kılıcı §7verildi!");
                break;
            case "golemkilicver":
                sm.giveToPlayer(p, sm.makeGolemSword());
                p.sendMessage("§f§lGolem Kılıcı §7verildi!");
                break;
            case "creeperkilicver":
                sm.giveToPlayer(p, sm.makeCreeperSword());
                p.sendMessage("§a§lCreeper Kılıcı §7verildi!");
                break;
            case "kilicvermenu":
                openSwordMenu(p);
                break;
        }
        return true;
    }

    // ─────────────────────────────────────────────
    //   KILIC VER MENÜSÜ
    // ─────────────────────────────────────────────

    private static final String MENU_TITLE = "§8✦ §l§6Özel Kılıçlar §8✦";

    private void openSwordMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, MENU_TITLE);

        // Dekorasyon - kenarlara cam blok
        fillBorder(inv);

        // Kılıçları yerleştir
        inv.setItem(10, buildMenuItem(sm.makeShulkerSword(),  "§5Shulker Kılıcı",
            "§7• Yakın oyuncuları §53 saniye §7uçurur.",
            "§7• Yavaşlatma efekti ekler.",
            "§8⏱ Bekleme: §c30 saniye",
            "§a► §fKliklayarak almak için §aYETKİ §fgerekir."));

        inv.setItem(12, buildMenuItem(sm.makeEndermanSword(), "§1Enderman Kılıcı",
            "§7• Yakın oyuncuları §12.5 blok §7ileriye fırlatır.",
            "§7• Duvardan geçiremez, örümcek ağına atar.",
            "§8⏱ Bekleme: §c30 saniye",
            "§a► §fKliklayarak almak için §aYETKİ §fgerekir."));

        inv.setItem(14, buildMenuItem(sm.makeSpiderSword(),   "§2Örümcek Kılıcı",
            "§7• Yakın oyuncuların ayağına §2örümcek ağı §7koyar.",
            "§7• §23.5 saniye §7sonra otomatik silinir.",
            "§7• Harita dışına çıkamaz, blok tahribatı yok.",
            "§8⏱ Bekleme: §c30 saniye",
            "§a► §fKliklayarak almak için §aYETKİ §fgerekir."));

        inv.setItem(16, buildMenuItem(sm.makePhantomSword(),  "§3Phantom Kılıcı",
            "§7• En yakın §32 oyuncunun §7Elytra'sını engeller.",
            "§7• §33 saniye §7sürer.",
            "§8⏱ Bekleme: §c230 saniye",
            "§a► §fKliklayarak almak için §aYETKİ §fgerekir."));

        inv.setItem(11, buildMenuItem(sm.makeGolemSword(),    "§fGolem Kılıcı",
            "§7• Yakın oyuncuların §fDirencini §7bozar (3s).",
            "§7• §fYavaşlık 4 §7verir (1.8s).",
            "§8⏱ Bekleme: §c230 saniye",
            "§a► §fKliklayarak almak için §aYETKİ §fgerekir."));

        inv.setItem(15, buildMenuItem(sm.makeCreeperSword(),  "§aCreeper Kılıcı",
            "§7• Ayağının altında §aTNT §7patlar!",
            "§7• Yakın oyuncular 2-3 blok savrulur.",
            "§7• Can gitmez sadece tepme!",
            "§8⏱ Bekleme: §c30 saniye",
            "§a► §fKliklayarak almak için §aYETKİ §fgerekir."));

        p.openInventory(inv);
        startMenuRgbAnimation(p, inv);
    }

    /** Menü başlığındaki kılıçların isimlerini RGB anims ile güncelle */
    private void startMenuRgbAnimation(Player p, Inventory inv) {
        new BukkitRunnable() {
            double hue = 0;
            int runs = 0;

            @Override
            public void run() {
                runs++;
                if (runs > 100 || p.getOpenInventory() == null
                    || !p.getOpenInventory().getTitle().equals(MENU_TITLE)) {
                    cancel();
                    return;
                }

                hue = (hue + 6) % 360;

                // Slot 4'e RGB başlık dekor item
                ItemStack deco = new ItemStack(Material.NETHER_STAR);
                ItemMeta m = deco.getItemMeta();

                java.awt.Color c = java.awt.Color.getHSBColor((float)(hue / 360f), 1f, 1f);
                String hex = String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
                net.md_5.bungee.api.ChatColor cc = net.md_5.bungee.api.ChatColor.of(hex);

                m.setDisplayName(cc + "✦ Özel Kılıçlar ✦");
                m.setLore(Arrays.asList(
                    cc + "Bu kılıçlar yetkili oyunculara özeldir.",
                    cc + "Her kılıcın kendine özel gücü vardır!"
                ));
                deco.setItemMeta(m);
                inv.setItem(4, deco);

                p.updateInventory();
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    private ItemStack buildMenuItem(ItemStack baseSword, String name, String... loreLines) {
        ItemStack item = baseSword.clone();
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(name);
        m.setLore(Arrays.asList(loreLines));
        item.setItemMeta(m);
        return item;
    }

    private void fillBorder(Inventory inv) {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        gm.setDisplayName("§r");
        glass.setItemMeta(gm);
        for (int i = 0; i < 27; i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == 2 || col == 0 || col == 8) {
                inv.setItem(i, glass);
            }
        }
    }

    // ─────────────────────────────────────────────
    //   MENÜ TIKLAMA OLAYI
    // ─────────────────────────────────────────────

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (e.getView() == null) return;
        if (!e.getView().getTitle().equals(MENU_TITLE)) return;

        e.setCancelled(true);

        Player p = (Player) e.getWhoClicked();
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        if (!p.hasPermission("loginx.admin")) {
            p.sendMessage("§cBu kılıcı almak için yetkin yok!");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        ItemMeta m = clicked.getItemMeta();
        String name = m.getDisplayName();

        if      (name.contains("Shulker"))  { sm.giveToPlayer(p, sm.makeShulkerSword());  p.sendMessage("§5Shulker Kılıcı §7alındı!"); }
        else if (name.contains("Enderman")) { sm.giveToPlayer(p, sm.makeEndermanSword()); p.sendMessage("§1Enderman Kılıcı §7alındı!"); }
        else if (name.contains("Örümcek")) { sm.giveToPlayer(p, sm.makeSpiderSword());   p.sendMessage("§2Örümcek Kılıcı §7alındı!"); }
        else if (name.contains("Phantom"))  { sm.giveToPlayer(p, sm.makePhantomSword());  p.sendMessage("§3Phantom Kılıcı §7alındı!"); }
        else if (name.contains("Golem"))    { sm.giveToPlayer(p, sm.makeGolemSword());    p.sendMessage("§fGolem Kılıcı §7alındı!"); }
        else if (name.contains("Creeper"))  { sm.giveToPlayer(p, sm.makeCreeperSword());  p.sendMessage("§aCreeper Kılıcı §7alındı!"); }
        else return;

        p.closeInventory();
    }
}
