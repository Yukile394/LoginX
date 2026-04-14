package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin; // Derleme hatası için bu şart
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;

import java.util.*;

/**
 * SwordManager — Netherite kılıç özel yetenekleri
 * LoginX Projesi için geliştirilmiştir.
 */
public class SwordManager implements Listener, CommandExecutor {

    // ── NBT Anahtarları ──────────────────────────────────────────────────
    private static final String KEY_SHULKER  = "loginx_shulker_sword";
    private static final String KEY_ENDERMAN = "loginx_enderman_sword";
    private static final String KEY_SPIDER   = "loginx_spider_sword";
    private static final String KEY_PHANTOM  = "loginx_phantom_sword";
    private static final String KEY_GOLEM    = "loginx_golem_sword";
    private static final String KEY_CREEPER  = "loginx_creeper_sword";

    // ── Cooldown süreleri ────────────────────────────────────────────────
    private static final int CD_SHORT = 30;
    private static final int CD_LONG  = 230;

    // ── RGB Paletler ────────────────────────────────────────────────────
    private static final int[][] PAL_SHULKER  = {{160,32,240},{200,60,255},{220,120,255}};
    private static final int[][] PAL_ENDERMAN = {{20,0,30},{60,0,100},{100,0,160}};
    private static final int[][] PAL_SPIDER   = {{160,0,0},{210,50,0},{255,110,0}};
    private static final int[][] PAL_PHANTOM  = {{0,80,200},{40,140,255},{80,200,255}};
    private static final int[][] PAL_GOLEM    = {{90,90,90},{170,170,170},{240,240,240}};
    private static final int[][] PAL_CREEPER  = {{0,150,0},{40,200,0},{100,255,50}};

    private final Map<Long, Long>           cooldowns      = new HashMap<>();
    private final Map<UUID, BukkitRunnable> countdownTasks = new HashMap<>();
    private final Map<UUID, ItemStack>      storedElytra   = new HashMap<>();

    private final JavaPlugin plugin; // JavaPlugin olarak tanımlandı ki getCommand çalışsın

    public SwordManager(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerCmd("shulkerkilicver");
        registerCmd("endermankilicver");
        registerCmd("orumcekkilicver");
        registerCmd("phantomkilicver");
        registerCmd("golemkilicver");
        registerCmd("creeperkilicver");
    }

    private void registerCmd(String name) {
        PluginCommand c = plugin.getCommand(name);
        if (c != null) c.setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage("§cOyuncu bulunamadı: §7" + args[0]); return true; }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage("§cKullanım: /" + cmd.getName() + " <oyuncu>");
            return true;
        }

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
            giveWithEffect(target, sword);
            sender.sendMessage("§a✔ §7" + target.getName() + " §7adlı oyuncuya kılıç verildi.");
            return true;
        }
        return false;
    }

    private void giveWithEffect(Player p, ItemStack sword) {
        p.getInventory().addItem(sword).values().forEach(drop -> 
            p.getWorld().dropItemNaturally(p.getLocation(), drop));
        
        p.sendMessage("§7✦ " + sword.getItemMeta().getDisplayName() + " §r§7eline verildi!");
        p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1, 0), 80, 0.5, 0.5, 0.5, 1.0);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() != Material.NETHERITE_SWORD || !hand.hasItemMeta()) return;

        PersistentDataContainer pdc = hand.getItemMeta().getPersistentDataContainer();

        if (hasKey(pdc, KEY_SHULKER)) {
            e.setCancelled(true);
            if (checkCD(p, KEY_SHULKER, CD_SHORT)) {
                activateShulker(p);
                startCountdown(p, KEY_SHULKER, CD_SHORT, PAL_SHULKER, "Shulker");
            }
        } else if (hasKey(pdc, KEY_ENDERMAN)) {
            e.setCancelled(true);
            if (checkCD(p, KEY_ENDERMAN, CD_SHORT)) {
                activateEnderman(p);
                startCountdown(p, KEY_ENDERMAN, CD_SHORT, PAL_ENDERMAN, "Enderman");
            }
        } // ... (Diğer kılıç kontrolleri aynı mantıkla devam eder)
    }

    private boolean checkCD(Player p, String key, int seconds) {
        long k = ((long) key.hashCode()) * 0x100000001L + (long) p.getUniqueId().hashCode();
        long last = cooldowns.getOrDefault(k, 0L);
        long sec = (System.currentTimeMillis() - last) / 1000L;
        if (sec < seconds) {
            p.sendMessage("§c⚠ §7Cooldown! §c" + (seconds - sec) + "s §7kaldı.");
            return false;
        }
        return true;
    }

    private void startCountdown(Player p, String key, int total, int[][] pal, String name) {
        long k = ((long) key.hashCode()) * 0x100000001L + (long) p.getUniqueId().hashCode();
        cooldowns.put(k, System.currentTimeMillis());
        
        p.sendMessage(rgbText(pal[0], "✦ " + name) + rgbText(pal[1], " Kılıç Özelliği Aktif!"));

        new BukkitRunnable() {
            int rem = total;
            @Override public void run() {
                if (!p.isOnline() || rem <= 0) {
                    if (rem <= 0) p.sendActionBar(rgbText(pal[0], "✦ ") + rgbText(pal[1], name + " HAZIR!"));
                    this.cancel();
                    return;
                }
                p.sendActionBar(rgbText(pal[1], "⏳ " + name + " ") + "§8[" + rem + "s]");
                rem--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ── KILIÇ OLUŞTURMA (Orijinal Tasarım) ───────────────────────────────

    private ItemStack buildSword(String nbtKey, String displayName, List<String> lore) {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            meta.addEnchant(Enchantment.SHARPNESS, 5, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, nbtKey), PersistentDataType.BYTE, (byte) 1);
            sword.setItemMeta(meta);
        }
        return sword;
    }

    public ItemStack createShulkerSword() {
        return buildSword(KEY_SHULKER, rgbText(PAL_SHULKER[0], "✦ ") + rgbText(PAL_SHULKER[1], "Shulker Kılıcı"),
            Arrays.asList("§7━━━━━━━━━━━━━━━━━━━━━━", rgbText(PAL_SHULKER[1], "⚡ Sağ Tık") + " §7→ Havaya kaldır!", "§7━━━━━━━━━━━━━━━━━━━━━━", "§8Cooldown: §730s"));
    }

    public ItemStack createEndermanSword() {
        return buildSword(KEY_ENDERMAN, rgbText(PAL_ENDERMAN[2], "✦ ") + rgbText(PAL_ENDERMAN[2], "Enderman Kılıcı"),
            Arrays.asList("§7━━━━━━━━━━━━━━━━━━━━━━", rgbText(PAL_ENDERMAN[2], "⚡ Sağ Tık") + " §7→ İleri atıl!", "§7━━━━━━━━━━━━━━━━━━━━━━", "§8Cooldown: §730s"));
    }

    public ItemStack createSpiderSword() {
        return buildSword(KEY_SPIDER, rgbText(PAL_SPIDER[0], "✦ ") + rgbText(PAL_SPIDER[1], "Örümcek Kılıcı"),
            Arrays.asList("§7━━━━━━━━━━━━━━━━━━━━━━", rgbText(PAL_SPIDER[1], "⚡ Sağ Tık") + " §7→ Ağ atar!", "§7━━━━━━━━━━━━━━━━━━━━━━", "§8Cooldown: §730s"));
    }

    public ItemStack createPhantomSword() {
        return buildSword(KEY_PHANTOM, rgbText(PAL_PHANTOM[0], "✦ ") + rgbText(PAL_PHANTOM[1], "Phantom Kılıcı"),
            Arrays.asList("§7━━━━━━━━━━━━━━━━━━━━━━", rgbText(PAL_PHANTOM[1], "⚡ Sağ Tık") + " §7→ Elytra söker!", "§7━━━━━━━━━━━━━━━━━━━━━━", "§8Cooldown: §7230s"));
    }

    public ItemStack createGolemSword() {
        return buildSword(KEY_GOLEM, rgbText(PAL_GOLEM[1], "✦ ") + rgbText(PAL_GOLEM[2], "Golem Kılıcı"),
            Arrays.asList("§7━━━━━━━━━━━━━━━━━━━━━━", rgbText(PAL_GOLEM[2], "⚡ Sağ Tık") + " §7→ Yavaşlatır!", "§7━━━━━━━━━━━━━━━━━━━━━━", "§8Cooldown: §7230s"));
    }

    public ItemStack createCreeperSword() {
        return buildSword(KEY_CREEPER, rgbText(PAL_CREEPER[0], "✦ ") + rgbText(PAL_CREEPER[1], "Creeper Kılıcı"),
            Arrays.asList("§7━━━━━━━━━━━━━━━━━━━━━━", rgbText(PAL_CREEPER[1], "⚡ Sağ Tık") + " §7→ Geri iter!", "§7━━━━━━━━━━━━━━━━━━━━━━", "§8Cooldown: §730s"));
    }

    // ── Yardımcı Metotlar (Aynı Kalıyor) ─────────────────────────────────

    private void activateShulker(Player caster) { /* Orijinal efekt kodları buraya */ }
    private void activateEnderman(Player caster) { /* Orijinal efekt kodları buraya */ }
    
    private boolean hasKey(PersistentDataContainer pdc, String key) {
        return pdc.has(new NamespacedKey(plugin, key), PersistentDataType.BYTE);
    }

    private String rgbText(int[] rgb, String text) {
        return String.format("§x§%c§%c§%c§%c§%c§%c%s", hexChar(rgb[0] >> 4), hexChar(rgb[0] & 0xF), hexChar(rgb[1] >> 4), hexChar(rgb[1] & 0xF), hexChar(rgb[2] >> 4), hexChar(rgb[2] & 0xF), text);
    }

    private char hexChar(int v) { return "0123456789abcdef".charAt(v & 0xF); }
}
