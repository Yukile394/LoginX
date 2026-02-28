package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

public class LoginX extends JavaPlugin implements Listener {

    private final Map<UUID, String> passwords = new HashMap<>();
    private final Map<UUID, Integer> attempts = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Set<UUID> loggedIn = new HashSet<>();
    private final Map<UUID, String> lastIP = new HashMap<>();
    private final Map<UUID, Integer> titleCount = new HashMap<>();
    private FileConfiguration cfg;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        cfg = getConfig();
        getLogger().info("LoginX güvenlik sistemi aktif.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        lastIP.put(uuid, p.getAddress().getAddress().getHostAddress());
        titleCount.put(uuid, 0);

        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (loggedIn.contains(uuid) || count >= cfg.getInt("title_interval")) {
                    cancel();
                    return;
                }
                sendLoginTitle(p);
                count++;
            }
        }.runTaskTimer(this, 0, 20L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!loggedIn.contains(uuid)) {
                    p.kickPlayer(ChatColor.RED + "§cZamanında kayıt veya giriş yapmadınız!");
                }
            }
        }.runTaskLater(this, cfg.getInt("login_timeout") * 20L);
    }

    private void sendLoginTitle(Player p) {
        UUID uuid = p.getUniqueId();
        String header, footer;
        if (!passwords.containsKey(uuid)) {
            header = color(cfg.getString("title_colors.register.header"));
            footer = color(cfg.getString("title_colors.register.footer"));
        } else if (!loggedIn.contains(uuid)) {
            header = color(cfg.getString("title_colors.login.header"));
            footer = color(cfg.getString("title_colors.login.footer"));
        } else {
            header = color(cfg.getString("title_colors.already_logged_in.header"));
            footer = color(cfg.getString("title_colors.already_logged_in.footer"));
        }
        p.sendTitle(header, footer, 10, 70, 10);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(color("&cGiriş yapmadan hareket edemezsin!"));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(color("&cGiriş yapmadan etkileşim yasak!"));
        }
    }

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!loggedIn.contains(e.getWhoClicked().getUniqueId())) {
            e.setCancelled(true);
            ((Player) e.getWhoClicked()).sendMessage(color("&cGiriş yapmadan envanteri kullanamazsın!"));
        }
    }

    @EventHandler
    public void onDrop(org.bukkit.event.player.PlayerDropItemEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(color("&cGiriş yapmadan eşya atamazsın!"));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        UUID uuid = player.getUniqueId();

        switch (cmd.getName().toLowerCase()) {
            case "register":
                if (args.length != 2) {
                    player.sendMessage(color("&cKullanım: /register <şifre> <şifre>"));
                    return true;
                }
                if (!args[0].equals(args[1])) {
                    player.sendMessage(color("&cŞifreler uyuşmuyor!"));
                    return true;
                }
                passwords.put(uuid, hash(args[0]));
                loggedIn.add(uuid);
                player.sendMessage(color("&aBaşarıyla kayıt oldun!"));
                sendLoginTitle(player);
                notifyOps(player, "§aYeni kayıt: " + player.getName());
                return true;

            case "login":
                if (!passwords.containsKey(uuid)) {
                    player.sendMessage(color("&cÖnce /register kullanmalısın!"));
                    return true;
                }
                if (args.length != 1) {
                    player.sendMessage(color("&cKullanım: /login <şifre>"));
                    return true;
                }
                String hashed = hash(args[0]);
                if (passwords.get(uuid).equals(hashed)) {
                    loggedIn.add(uuid);
                    player.sendMessage(color("&aBaşarılı giriş!"));
                    sendLoginTitle(player);
                    notifyOps(player, "§aBaşarılı giriş: " + player.getName());
                } else {
                    player.sendMessage(color("&cYanlış şifre!"));
                    attempts.put(uuid, attempts.getOrDefault(uuid, 0) + 1);
                    if (attempts.get(uuid) >= 3) player.kickPlayer("§cÇok fazla yanlış deneme!");
                }
                return true;

            case "logingoster":
                if (!player.hasPermission("loginx.view")) {
                    player.sendMessage(color("&cYetkin yok!"));
                    return true;
                }
                if (args.length != 1) {
                    player.sendMessage(color("&cKullanım: /logingoster <oyuncu>"));
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                UUID tUUID = target.getUniqueId();
                player.sendMessage(color("&6==== LoginX Raporu ===="));
                player.sendMessage(color("&eİsim: &f" + target.getName()));
                player.sendMessage(color("&eSon IP: &f" + lastIP.getOrDefault(tUUID, "Bilinmiyor")));
                player.sendMessage(color("&eBaşarısız Deneme: &f" + attempts.getOrDefault(tUUID, 0)));
                player.sendMessage(color("&eKayıtlı mı: &f" + passwords.containsKey(tUUID)));
                player.sendMessage(color("&7LoginX sistemi aktif."));
                return true;

            case "incele":
                if (!player.hasPermission("loginx.view")) {
                    player.sendMessage(color("&cYetkin yok!"));
                    return true;
                }
                player.sendMessage(color("&6==== Oyuncu İncele ===="));
                if (args.length != 1) return true;
                OfflinePlayer p2 = Bukkit.getOfflinePlayer(args[0]);
                player.sendMessage(color("&eİsim: &f" + p2.getName()));
                return true;
        }

        return false;
    }

    private void notifyOps(Player p, String status) {
        String time = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date());
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.isOp()) online.sendMessage(color("§dLoginX §7| §e" + time + " §7| §b" + p.getName() + " §7| " + status));
        }
    }

    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
