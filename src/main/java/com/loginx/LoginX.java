package com.loginx;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LoginX extends JavaPlugin implements Listener {

    // İstatistikler
    private final HashMap<UUID, Integer> kills = new HashMap<>();
    private final HashMap<UUID, Integer> playtime = new HashMap<>();
    private final HashMap<UUID, Integer> blocksBroken = new HashMap<>();
    private final HashMap<Location, String> activeHolograms = new HashMap<>();
    private final List<ArmorStand> spawnedStands = new ArrayList<>();
    private long nextResetTime;

    // Kılıç Cooldownları (Oyuncu UUID -> (Kılıç Tipi -> Biteceği Zaman ms))
    private final HashMap<UUID, HashMap<String, Long>> cooldowns = new HashMap<>();
    
    // Phantom Elitra Engelleme (Oyuncu UUID -> Biteceği Zaman ms)
    private final HashMap<UUID, Long> elytraBanned = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        loadStats();

        startPlaytime();
        startHoloUpdater();
        startWeeklyReset();

        getLogger().info("LoginX Kılıç Sistemleri ile Birlikte Aktif!");
    }

    @Override
    public void onDisable() {
        saveStats();
        clearHolos();
    }

    // ===================== COMMANDS =====================
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (!p.hasPermission("loginx.admin")) {
            p.sendMessage(color("&cBunun için yetkin yok."));
            return true;
        }

        Location tl = p.getTargetBlock(null, 10).getLocation().add(0.5, 2.5, 0.5);

        switch (cmd.getName().toLowerCase()) {
            case "skorkill" -> spawnHolo(tl, "KILLS");
            case "skorzaman" -> spawnHolo(tl, "PLAYTIME");
            case "skorblok" -> spawnHolo(tl, "BLOCKS");
            case "skorsil" -> {
                clearHolos();
                activeHolograms.clear();
                p.sendMessage(color("&cHologramlar Silindi"));
            }
            case "kilicvermenu" -> openMenu(p);
        }
        return true;
    }

    @EventHandler
    public void onFastCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String msg = e.getMessage().toLowerCase();
        
        if (!p.hasPermission("loginx.admin")) return;

        String swordType = null;
        if (msg.equals("/shulkerkilicver")) swordType = "shulker";
        else if (msg.equals("/endermankilicver")) swordType = "enderman";
        else if (msg.equals("/orumcekkilicver")) swordType = "orumcek";
        else if (msg.equals("/phantomkilicver")) swordType = "phantom";
        else if (msg.equals("/golemkilicver")) swordType = "golem";
        else if (msg.equals("/creeperkilicver")) swordType = "creeper";

        if (swordType != null) {
            e.setCancelled(true);
            p.getInventory().addItem(getSpecialSword(swordType));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
            p.spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation(), 30, 0.5, 1, 0.5, 0.1);
            p.sendMessage(color("&a" + swordType.toUpperCase() + " Kılıcı verildi!"));
        }
    }

    // ===================== MENU =====================
    private void openMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&#3b3b3bÖzel Kılıç Menüsü"));

        inv.setItem(10, getSpecialSword("shulker"));
        inv.setItem(11, getSpecialSword("enderman"));
        inv.setItem(12, getSpecialSword("orumcek"));
        inv.setItem(14, getSpecialSword("phantom"));
        inv.setItem(15, getSpecialSword("golem"));
        inv.setItem(16, getSpecialSword("creeper"));

        p.openInventory(inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals(color("&#3b3b3bÖzel Kılıç Menüsü"))) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.NETHERITE_SWORD) {
                Player p = (Player) e.getWhoClicked();
                if (p.hasPermission("loginx.admin")) {
                    p.getInventory().addItem(e.getCurrentItem());
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                }
            }
        }
    }

    // ===================== SWORD DEFINITIONS =====================
    private ItemStack getSpecialSword(String type) {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        
        List<String> lore = new ArrayList<>();
        lore.add(color("&7Sağ tık özel skill!"));
        
        switch (type.toLowerCase()) {
            case "shulker":
                meta.setDisplayName(color("&#b02e26&lS&#b02e26&lh&#b02e26&lu&#cc3b33&ll&#cc3b33&lk&#cc3b33&le&#e8473e&lr &fKılıcı"));
                lore.add(color("&fÖzellik: &7Yakındaki oyuncuları 3sn uçurur."));
                lore.add(color("&8Bekleme: 30s"));
                break;
            case "enderman":
                meta.setDisplayName(color("&#5500aa&lE&#5500aa&ln&#5500aa&ld&#6a00d4&le&#6a00d4&lr&#6a00d4&lm&#8000ff&la&#8000ff&ln &fKılıcı"));
                lore.add(color("&fÖzellik: &7Yakındaki oyuncuları 2.5 blok ileri fırlatır."));
                lore.add(color("&8Bekleme: 30s"));
                break;
            case "orumcek":
                meta.setDisplayName(color("&#5c0000&lÖ&#5c0000&lr&#7a0000&lü&#7a0000&lm&#9c0000&lc&#9c0000&le&#bd0000&lk &fKılıcı"));
                lore.add(color("&fÖzellik: &7Hedeflerin üzerine 3.5 saniyeliğine ağ atar."));
                lore.add(color("&8Bekleme: 30s"));
                break;
            case "phantom":
                meta.setDisplayName(color("&#003366&lP&#003366&lh&#004c99&la&#004c99&ln&#0066cc&lt&#0066cc&lo&#0080ff&lm &fKılıcı"));
                lore.add(color("&fÖzellik: &7Rastgele 2 yakının Elitrasını 3sn bozar."));
                lore.add(color("&8Bekleme: 230s"));
                break;
            case "golem":
                meta.setDisplayName(color("&#d9d9d9&lG&#d9d9d9&lo&#e6e6e6&ll&#e6e6e6&le&#f2f2f2&lm &fKılıcı"));
                lore.add(color("&fÖzellik: &7Yakınlara Direnç I (3s) ve Yavaşlık IV (1.8s) verir."));
                lore.add(color("&8Bekleme: 230s"));
                break;
            case "creeper":
                meta.setDisplayName(color("&#009900&lC&#009900&lr&#00b300&le&#00b300&le&#00cc00&lp&#00cc00&le&#00e600&lr &fKılıcı"));
                lore.add(color("&fÖzellik: &7Etrafında TNT patlatır, düşmanları savurur."));
                lore.add(color("&8Bekleme: 30s"));
                break;
        }
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ===================== SWORD ABILITIES =====================
    @EventHandler
    public void onSwordUse(PlayerInteractEvent e) {
        if (!e.getAction().name().contains("RIGHT")) return;
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        
        if (item.getType() != Material.NETHERITE_SWORD || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;
        
        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();
        String type = null;
        int cdSeconds = 30;
        String hexColor = "&#ffffff";
        
        if (name.contains("shulker")) { type = "shulker"; hexColor = "&#e8473e"; }
        else if (name.contains("enderman")) { type = "enderman"; hexColor = "&#8000ff"; }
        else if (name.contains("örümcek")) { type = "orumcek"; hexColor = "&#bd0000"; }
        else if (name.contains("phantom")) { type = "phantom"; hexColor = "&#0080ff"; cdSeconds = 230; }
        else if (name.contains("golem")) { type = "golem"; hexColor = "&#d9d9d9"; cdSeconds = 230; }
        else if (name.contains("creeper")) { type = "creeper"; hexColor = "&#00e600"; }
        
        if (type == null) return;

        // Cooldown Kontrolü
        cooldowns.putIfAbsent(p.getUniqueId(), new HashMap<>());
        long expireTime = cooldowns.get(p.getUniqueId()).getOrDefault(type, 0L);
        if (System.currentTimeMillis() < expireTime) {
            long left = (expireTime - System.currentTimeMillis()) / 1000;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(color(hexColor + "&lBu Özelliği Kullanmak İçin " + left + " Saniye Bekle!")));
            return;
        }
        
        // Cooldown Uygula
        cooldowns.get(p.getUniqueId()).put(type, System.currentTimeMillis() + (cdSeconds * 1000L));
        
        List<Player> nearby = p.getNearbyEntities(5, 5, 5).stream()
                .filter(ent -> ent instanceof Player)
                .map(ent -> (Player) ent)
                .collect(Collectors.toList());

        // Yetenek Dağılımı
        switch (type) {
            case "shulker":
                p.sendMessage(color(hexColor + "Shulker Kılıç Özelliğini Kullandın Altet Onları ;)"));
                p.playSound(p.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1f, 1f);
                for (Player t : nearby) {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 3 * 20, 0));
                    t.spawnParticle(Particle.WITCH, t.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
                }
                break;
                
            case "enderman":
                p.sendMessage(color(hexColor + "Enderman Kılıç Özelliğini Kullandın, Işınla Onları ;)"));
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                for (Player t : nearby) {
                    Vector dir = p.getLocation().getDirection().normalize().setY(0.2).multiply(2.5);
                    Location targetLoc = t.getLocation().add(dir);
                    Block b = targetLoc.getBlock();
                    
                    if (b.getType().isAir() || b.getType() == Material.COBWEB || !b.getType().isSolid()) {
                        t.teleport(targetLoc);
                        t.spawnParticle(Particle.PORTAL, t.getLocation(), 30, 0.5, 1, 0.5, 0.1);
                    }
                }
                break;
                
            case "orumcek":
                p.sendMessage(color(hexColor + "Örümcek Kılıç Özelliğini Kullandın, Avla Onları ;)"));
                p.playSound(p.getLocation(), Sound.ENTITY_SPIDER_DEATH, 1f, 1f);
                for (Player t : nearby) {
                    Location loc = t.getLocation().add(0, 1, 0);
                    if (loc.getBlock().getType().isAir()) {
                        loc.getBlock().setType(Material.COBWEB);
                        t.spawnParticle(Particle.BLOCK, loc, 20, 0.5, 0.5, 0.5, Material.COBWEB.createBlockData());
                        
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (loc.getBlock().getType() == Material.COBWEB) {
                                    loc.getBlock().setType(Material.AIR);
                                }
                            }
                        }.runTaskLater(this, 70L);
                    }
                }
                break;
                
            case "phantom":
                p.sendMessage(color(hexColor + "Phantom Kılıç Özelliğini Kullandın, Yere Düşür Onları ;)"));
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1f, 1f);
                Collections.shuffle(nearby);
                int count = 0;
                for (Player t : nearby) {
                    if (count >= 2) break;
                    elytraBanned.put(t.getUniqueId(), System.currentTimeMillis() + 3000L);
                    t.sendMessage(color("&c&lElitra 3 saniyeliğine bozuldu!"));
                    t.spawnParticle(Particle.LARGE_SMOKE, t.getLocation(), 40, 0.5, 1, 0.5, 0.1);
                    if (t.isGliding()) t.setGliding(false);
                    count++;
                }
                break;

            case "golem":
                p.sendMessage(color(hexColor + "Golem Kılıç Özelliğini Kullandın, Ez Geç Onları ;)"));
                p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.5f);
                for (Player t : nearby) {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 3 * 20, 0));
                    t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int)(1.8 * 20), 3));
                    t.spawnParticle(Particle.BLOCK, t.getLocation(), 30, 0.5, 0.1, 0.5, Material.IRON_BLOCK.createBlockData());
                }
                break;

            case "creeper":
                p.sendMessage(color(hexColor + "Creeper Kılıç Özelliğini Kullandın, Patlat Onları ;)"));
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                p.spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation(), 2);
                for (Player t : nearby) {
                    t.damage(8.0, p);
                    Vector kb = t.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.5).setY(0.6);
                    t.setVelocity(kb);
                }
                break;
        }
    }

    @EventHandler
    public void onGlide(EntityToggleGlideEvent e) {
        if (e.getEntity() instanceof Player p && e.isGliding()) {
            if (elytraBanned.containsKey(p.getUniqueId())) {
                if (System.currentTimeMillis() < elytraBanned.get(p.getUniqueId())) {
                    e.setCancelled(true);
                } else {
                    elytraBanned.remove(p.getUniqueId());
                }
            }
        }
    }

    // ===================== HOLOGRAM & ISTATISTIK =====================
    @EventHandler
    public void onKill(PlayerDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k != null) kills.merge(k.getUniqueId(), 1, Integer::sum);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        blocksBroken.merge(e.getPlayer().getUniqueId(), 1, Integer::sum);
    }

    private void spawnHolo(Location loc, String type) {
        activeHolograms.put(loc, type);
        buildHolo(loc, type);
    }

    private void buildHolo(Location base, String type) {
        List<String> lines = new ArrayList<>();
        if (type.equals("KILLS")) {
            lines.add("&cTOP KILLS");
            lines.addAll(top(kills, "kill"));
        } else if (type.equals("PLAYTIME")) {
            lines.add("&bTOP TIME");
            lines.addAll(top(playtime, "dk"));
        } else {
            lines.add("&aTOP BLOCKS");
            lines.addAll(top(blocksBroken, "blok"));
        }

        double y = 0;
        for (String l : lines) {
            ArmorStand a = (ArmorStand) base.getWorld().spawnEntity(base.clone().subtract(0, y, 0), EntityType.ARMOR_STAND);
            a.setInvisible(true);
            a.setMarker(true);
            a.setCustomNameVisible(true);
            a.setCustomName(color(l));
            a.setGravity(false);
            spawnedStands.add(a);
            y += 0.25;
        }
    }

    private void clearHolos() {
        for (ArmorStand a : spawnedStands) {
            if (a != null) a.remove();
        }
        spawnedStands.clear();
    }

    private List<String> top(HashMap<UUID, Integer> map, String suf) {
        if (map.isEmpty()) return List.of("&cNo data");
        List<String> out = new ArrayList<>();
        int r = 1;
        for (Map.Entry<UUID, Integer> e : map.entrySet().stream().sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()).limit(10).collect(Collectors.toList())) {
            String n = Bukkit.getOfflinePlayer(e.getKey()).getName();
            if (n == null) n = "Unknown";
            out.add("&e" + r + ". &f" + n + " &7- &a" + e.getValue() + " " + suf);
            r++;
        }
        return out;
    }

    private void startPlaytime() {
        new BukkitRunnable() {
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    playtime.merge(p.getUniqueId(), 1, Integer::sum);
                }
            }
        }.runTaskTimer(this, 1200, 1200);
    }

    private void startHoloUpdater() {
        new BukkitRunnable() {
            public void run() {
                if (!activeHolograms.isEmpty()) {
                    clearHolos();
                    activeHolograms.forEach(LoginX.this::buildHolo);
                }
            }
        }.runTaskTimer(this, 100, 100);
    }

    private void startWeeklyReset() {
        new BukkitRunnable() {
            public void run() {
                if (System.currentTimeMillis() > nextResetTime) {
                    kills.clear();
                    playtime.clear();
                    blocksBroken.clear();
                    nextResetTime = System.currentTimeMillis() + 604800000;
                    Bukkit.broadcastMessage(color("&aHaftalık reset!"));
                }
            }
        }.runTaskTimer(this, 20 * 60, 20 * 60 * 60);
    }

    private void saveStats() {
        FileConfiguration c = getConfig();
        c.set("reset", nextResetTime);
        kills.forEach((k, v) -> c.set("kills." + k, v));
        playtime.forEach((k, v) -> c.set("time." + k, v));
        blocksBroken.forEach((k, v) -> c.set("blocks." + k, v));
        saveConfig();
    }

    private void loadStats() {
        FileConfiguration c = getConfig();
        nextResetTime = c.getLong("reset", System.currentTimeMillis() + 604800000);
    }

    public String color(String text) {
        Pattern p = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher m = p.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex = m.group(1);
            StringBuilder rep = new StringBuilder("§x");
            for (char c : hex.toCharArray()) { rep.append("§").append(c); }
            m.appendReplacement(sb, rep.toString());
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
                                                                                       }
