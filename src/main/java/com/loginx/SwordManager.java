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

public class SwordManager implements Listener, CommandExecutor {

    private final JavaPlugin plugin;
    private final String menuTitle = "§8» " + rgbText(new int[]{255, 60, 60}, "Özel Kılıçlar");

    // NBT Keys
    private static final String KEY_SHULKER  = "loginx_shulker_sword";
    private static final String KEY_ENDERMAN = "loginx_enderman_sword";
    private static final String KEY_SPIDER   = "loginx_spider_sword";
    private static final String KEY_PHANTOM  = "loginx_phantom_sword";
    private static final String KEY_GOLEM    = "loginx_golem_sword";
    private static final String KEY_CREEPER  = "loginx_creeper_sword";

    private final Map<Long, Long> cooldowns = new HashMap<>();
    private final Map<UUID, ItemStack> storedElytra = new HashMap<>();

    public SwordManager(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        String[] cmds = {"shulkerkilicver", "endermankilicver", "orumcekkilicver", 
                         "phantomkilicver", "golemkilicver", "creeperkilicver", "kilicozelikmenu"};
        for (String s : cmds) {
            PluginCommand c = plugin.getCommand(s);
            if (c != null) c.setExecutor(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (cmd.getName().equalsIgnoreCase("kilicozelikmenu")) {
            openMenu(p);
            return true;
        }

        Player target = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : p;
        if (target == null) { p.sendMessage("§cOyuncu bulunamadı."); return true; }

        ItemStack sword = switch (cmd.getName().toLowerCase()) {
            case "shulkerkilicver"  -> createShulkerSword();
            case "endermankilicver" -> createEndermanSword();
            case "orumcekkilicver"  -> createSpiderSword();
            case "phantomkilicver"  -> createPhantomSword();
            case "golemkilicver"    -> createGolemSword();
            case "creeperkilicver"  -> createCreeperSword();
            default -> null;
        };

        if (sword != null) {
            target.getInventory().addItem(sword);
            p.sendMessage("§a✔ §7Kılıç verildi: " + target.getName());
        }
        return true;
    }

    public void openMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, menuTitle);
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta gMeta = glass.getItemMeta(); gMeta.setDisplayName(" "); glass.setItemMeta(gMeta);
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        inv.setItem(10, createIcon(new int[]{160,32,240}, "Shulker Kılıcı", "Levitasyon Etkisi", "Rakipleri havaya uçurur."));
        inv.setItem(11, createIcon(new int[]{40,0,80}, "Enderman Kılıcı", "Ağdan Kaçış", "Ağların içinden geçer."));
        inv.setItem(12, createIcon(new int[]{200,0,0}, "Örümcek Kılıcı", "Ağ Atma", "Rakipleri ağa hapseder."));
        inv.setItem(14, createIcon(new int[]{0,120,255}, "Phantom Kılıcı", "Elytra Sökücü", "Havadakileri yere düşürür."));
        inv.setItem(15, createIcon(new int[]{100,100,100}, "Golem Kılıcı", "Ağır Darbe", "Yavaşlatır ve güç verir."));
        inv.setItem(16, createIcon(new int[]{0,200,0}, "Creeper Kılıcı", "Alan İtme", "Patlama ile geri iter."));

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }

    private ItemStack createIcon(int[] rgb, String name, String power, String desc) {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(rgbText(rgb, "✦ " + name));
        meta.setLore(Arrays.asList(
            "§7━━━━━━━━━━━━━━━━━━━━━━",
            " §fGüç: " + rgbText(rgb, power),
            " §7" + desc,
            " ",
            " §e» §fAlmak için §6Tıkla!",
            "§7━━━━━━━━━━━━━━━━━━━━━━"
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals(menuTitle)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;

        Player p = (Player) e.getWhoClicked();
        String dn = e.getCurrentItem().getItemMeta().getDisplayName();

        ItemStack s = null;
        if (dn.contains("Shulker")) s = createShulkerSword();
        else if (dn.contains("Enderman")) s = createEndermanSword();
        else if (dn.contains("Örümcek")) s = createSpiderSword();
        else if (dn.contains("Phantom")) s = createPhantomSword();
        else if (dn.contains("Golem")) s = createGolemSword();
        else if (dn.contains("Creeper")) s = createCreeperSword();

        if (s != null) {
            p.getInventory().addItem(s);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);
        }
    }

    // ── Kılıç Oluşturma Metotları ──
    private ItemStack build(String key, int[] rgb, String name, String ability) {
        ItemStack s = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta m = s.getItemMeta();
        m.setDisplayName(rgbText(rgb, "✦ " + name));
        m.setLore(Arrays.asList("§7━━━━━━━━━━━━━━━━━━━━━━", " §fYetenek: " + rgbText(rgb, ability), " §fKullanım: §7Sağ Tık", "§7━━━━━━━━━━━━━━━━━━━━━━"));
        m.addEnchant(Enchantment.SHARPNESS, 5, true);
        m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        m.getPersistentDataContainer().set(new NamespacedKey(plugin, key), PersistentDataType.BYTE, (byte) 1);
        s.setItemMeta(m);
        return s;
    }

    public ItemStack createShulkerSword() { return build(KEY_SHULKER, new int[]{160,32,240}, "Shulker Kılıcı", "Levitasyon"); }
    public ItemStack createEndermanSword() { return build(KEY_ENDERMAN, new int[]{40,0,80}, "Enderman Kılıcı", "Ağdan Kaçış"); }
    public ItemStack createSpiderSword() { return build(KEY_SPIDER, new int[]{200,0,0}, "Örümcek Kılıcı", "Ağ Atma"); }
    public ItemStack createPhantomSword() { return build(KEY_PHANTOM, new int[]{0,120,255}, "Phantom Kılıcı", "Elytra Sökücü"); }
    public ItemStack createGolemSword() { return build(KEY_GOLEM, new int[]{100,100,100}, "Golem Kılıcı", "Direnç & Yavaşlık"); }
    public ItemStack createCreeperSword() { return build(KEY_CREEPER, new int[]{0,200,0}, "Creeper Kılıcı", "Patlama"); }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        if (hasKey(pdc, KEY_SHULKER)) {
            e.setCancelled(true);
            if (!checkCD(p, KEY_SHULKER, 30)) return;
            List<Entity> targets = p.getNearbyEntities(8, 8, 8);
            for (Entity t : targets) if (t instanceof Player tp && !tp.equals(p)) tp.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 1));
            p.sendMessage("§d✦ Shulker gücü kullanıldı!");
        } else if (hasKey(pdc, KEY_ENDERMAN)) {
            e.setCancelled(true);
            if (!checkCD(p, KEY_ENDERMAN, 30)) return;
            if (p.getLocation().getBlock().getType() == Material.COBWEB) {
                p.teleport(p.getLocation().add(p.getLocation().getDirection().multiply(3)));
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            }
        } else if (hasKey(pdc, KEY_SPIDER)) {
            e.setCancelled(true);
            if (!checkCD(p, KEY_SPIDER, 30)) return;
            for (Entity t : p.getNearbyEntities(8, 8, 8)) {
                if (t instanceof Player tp && !tp.equals(p)) {
                    Block b = tp.getLocation().getBlock();
                    if (b.getType().isAir()) {
                        b.setType(Material.COBWEB);
                        new BukkitRunnable() { @Override public void run() { if(b.getType()==Material.COBWEB) b.setType(Material.AIR); } }.runTaskLater(plugin, 100L);
                    }
                }
            }
        }
        // ... Phantom, Golem ve Creeper yetenekleri de benzer mantıkla eklenebilir.
    }

    private boolean checkCD(Player p, String key, int sec) {
        long k = (long)key.hashCode() + p.getUniqueId().hashCode();
        long now = System.currentTimeMillis();
        if (cooldowns.containsKey(k) && (now - cooldowns.get(k)) < sec * 1000L) {
            p.sendMessage("§c⚠ Cooldown: §7" + (sec - (now - cooldowns.get(k))/1000) + "s");
            return false;
        }
        cooldowns.put(k, now);
        return true;
    }

    private boolean hasKey(PersistentDataContainer pdc, String k) { return pdc.has(new NamespacedKey(plugin, k), PersistentDataType.BYTE); }
    private String rgbText(int[] rgb, String t) { return String.format("§x§%c§%c§%c§%c§%c§%c%s", hex(rgb[0]>>4), hex(rgb[0]&15), hex(rgb[1]>>4), hex(rgb[1]&15), hex(rgb[2]>>4), hex(rgb[2]&15), t); }
    private char hex(int v) { return "0123456789abcdef".charAt(v & 15); }
                        }
                                                                       
