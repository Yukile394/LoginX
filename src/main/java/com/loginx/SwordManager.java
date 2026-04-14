package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;

import java.util.*;

/**
 * LoginX - SwordManager (Menu Edition)
 * Tüm kılıçları tek bir şık menüden almanı sağlar.
 */
public class SwordManager implements Listener, CommandExecutor {

    private final JavaPlugin plugin;
    private final String menuTitle = "§8» " + rgbText(new int[]{255, 0, 0}, "Özel Kılıçlar");

    // NBT Anahtarları
    private static final String KEY_SHULKER  = "loginx_shulker_sword";
    private static final String KEY_ENDERMAN = "loginx_enderman_sword";
    private static final String KEY_SPIDER   = "loginx_spider_sword";
    private static final String KEY_PHANTOM  = "loginx_phantom_sword";
    private static final String KEY_GOLEM    = "loginx_golem_sword";
    private static final String KEY_CREEPER  = "loginx_creeper_sword";

    // Renk Paletleri
    private final int[][] P_SHU = {{160,32,240},{220,120,255}};
    private final int[][] P_END = {{20,0,30},{100,0,160}};
    private final int[][] P_SPI = {{160,0,0},{255,110,0}};
    private final int[][] P_PHA = {{0,80,200},{80,200,255}};
    private final int[][] P_GOL = {{90,90,90},{240,240,240}};
    private final int[][] P_CRE = {{0,150,0},{100,255,50}};

    private final Map<Long, Long> cooldowns = new HashMap<>();

    public SwordManager(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        // Komutu kaydet
        PluginCommand cmd = plugin.getCommand("kilicozelikmenu");
        if (cmd != null) cmd.setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cBu komut sadece oyuncular içindir!");
            return true;
        }
        openMenu(p);
        return true;
    }

    /** Şık ve Floplu Menüyü Açar */
    public void openMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, menuTitle);

        // Kenarları siyah camla doldur (Daha şık durur)
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta gMeta = glass.getItemMeta();
        gMeta.setDisplayName(" ");
        glass.setItemMeta(gMeta);
        for (int i : new int[]{0,1,2,3,4,5,6,7,8,9,17,18,19,20,21,22,23,24,25,26}) inv.setItem(i, glass);

        // Kılıçları Yerleştir
        inv.setItem(10, createMenuIcon(Material.NETHERITE_SWORD, P_SHU, "Shulker Kılıcı", "§7Vurduğun kişiyi gökyüzüne fırlatır."));
        inv.setItem(11, createMenuIcon(Material.NETHERITE_SWORD, P_END, "Enderman Kılıcı", "§7Ağların içinden ışınlanmanı sağlar."));
        inv.setItem(12, createMenuIcon(Material.NETHERITE_SWORD, P_SPI, "Örümcek Kılıcı", "§7Rakiplerini yapışkan ağlara hapseder."));
        inv.setItem(14, createMenuIcon(Material.NETHERITE_SWORD, P_PHA, "Phantom Kılıcı", "§7Uçan rakiplerin elytrasını söküp alır!"));
        inv.setItem(15, createMenuIcon(Material.NETHERITE_SWORD, P_GOL, "Golem Kılıcı", "§7Ağır bir darbe ile rakibi yavaşlatır."));
        inv.setItem(16, createMenuIcon(Material.NETHERITE_SWORD, P_CRE, "Creeper Kılıcı", "§7Patlama etkisiyle herkesi geri iter."));

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1.2f);
    }

    private ItemStack createMenuIcon(Material mat, int[][] pal, String name, String desc) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(rgbText(pal[0], "✦ " + name));
        meta.setLore(Arrays.asList(
            "§7━━━━━━━━━━━━━━━━━━━━━━",
            " " + desc,
            " ",
            " §e» §fAlmak için §6Tıkla!",
            "§7━━━━━━━━━━━━━━━━━━━━━━"
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(menuTitle)) return;
        e.setCancelled(true); // Menüden item çalınmasın

        if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
        Player p = (Player) e.getWhoClicked();
        String name = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());

        ItemStack sword = null;
        if (name.contains("Shulker")) sword = buildSword(KEY_SHULKER, P_SHU, "Shulker Kılıcı", "Levitasyon Etkisi");
        else if (name.contains("Enderman")) sword = buildSword(KEY_ENDERMAN, P_END, "Enderman Kılıcı", "Ağdan Kaçış");
        else if (name.contains("Örümcek")) sword = buildSword(KEY_SPIDER, P_SPI, "Örümcek Kılıcı", "Ağ Fırlatma");
        else if (name.contains("Phantom")) sword = buildSword(KEY_PHANTOM, P_PHA, "Phantom Kılıcı", "Elytra Sökücü");
        else if (name.contains("Golem")) sword = buildSword(KEY_GOLEM, P_GOL, "Golem Kılıcı", "Slowness & Direnç");
        else if (name.contains("Creeper")) sword = buildSword(KEY_CREEPER, P_CRE, "Creeper Kılıcı", "Alan İtme");

        if (sword != null) {
            p.getInventory().addItem(sword);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.5f);
            p.sendMessage("§a✔ §f" + name + " §7başarıyla envanterine eklendi!");
        }
    }

    private ItemStack buildSword(String nbtKey, int[][] pal, String name, String ability) {
        ItemStack s = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta m = s.getItemMeta();
        m.setDisplayName(rgbText(pal[0], "✦ " + name));
        m.setLore(Arrays.asList(
            "§7━━━━━━━━━━━━━━━━━━━━━━",
            " §fÖzellik: " + rgbText(pal[1], ability),
            " §fKullanım: §7Sağ Tık",
            "§7━━━━━━━━━━━━━━━━━━━━━━"
        ));
        m.addEnchant(Enchantment.SHARPNESS, 5, true);
        m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        m.getPersistentDataContainer().set(new NamespacedKey(plugin, nbtKey), PersistentDataType.BYTE, (byte) 1);
        s.setItemMeta(m);
        return s;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || !hand.hasItemMeta()) return;

        PersistentDataContainer pdc = hand.getItemMeta().getPersistentDataContainer();
        // Buraya yetenek kodlarını (activateShulker vb.) önceki mesajdaki gibi ekleyebilirsin.
    }

    private String rgbText(int[] rgb, String text) {
        return String.format("§x§%c§%c§%c§%c§%c§%c%s", 
            hex(rgb[0]>>4), hex(rgb[0]&15), hex(rgb[1]>>4), hex(rgb[1]&15), hex(rgb[2]>>4), hex(rgb[2]&15), text);
    }
    private char hex(int v) { return "0123456789abcdef".charAt(v & 15); }
                           }
                                 
