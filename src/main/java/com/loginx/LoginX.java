package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

public class LoginX extends JavaPlugin implements Listener {

    private final Map<UUID, String> passwords = new HashMap<>();
    private final Map<UUID, Integer> attempts = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Set<UUID> loggedIn = new HashSet<>();
    private final Map<UUID, String> lastIP = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        getLogger().info("LoginX güvenlik sistemi aktif.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String ip = p.getAddress().getAddress().getHostAddress();

        if (lastIP.containsKey(p.getUniqueId()) && !lastIP.get(p.getUniqueId()).equals(ip)) {
            notifyOps(p, "§cIP DEĞİŞTİ!");
        }
        lastIP.put(p.getUniqueId(), ip);

        p.sendMessage(color("&eLogin için: &f/login <şifre>"));
        notifyOps(p, "§aOyuncu katıldı: " + p.getName());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (cmd.getName().equalsIgnoreCase("login")) {
            if (args.length != 1) {
                player.sendMessage(color("&cKullanım: /login <şifre>"));
                return true;
            }

            UUID uuid = player.getUniqueId();
            long now = System.currentTimeMillis();

            if (cooldown.containsKey(uuid) && now - cooldown.get(uuid) < 2000) {
                player.sendMessage(color("&cÇok hızlı deniyorsun!"));
                return true;
            }

            cooldown.put(uuid, now);

            String hashed = hash(args[0]);

            if (!passwords.containsKey(uuid)) {
                passwords.put(uuid, hashed);
                loggedIn.add(uuid);
                player.sendMessage(color("&aKayıt olundu."));
                notifyOps(player, "§aYeni kayıt: " + player.getName());
                return true;
            }

            if (passwords.get(uuid).equals(hashed)) {
                loggedIn.add(uuid);
                attempts.put(uuid, 0);
                player.sendMessage(color("&aBaşarılı giriş!"));
                notifyOps(player, "§aBaşarılı giriş: " + player.getName());
            } else {
                int fail = attempts.getOrDefault(uuid, 0) + 1;
                attempts.put(uuid, fail);
                player.sendMessage(color("&cYanlış şifre!"));

                notifyOps(player, "§cBaşarısız deneme: " + player.getName());

                if (fail >= 3) {
                    player.kickPlayer("§cÇok fazla yanlış deneme!");
                }
            }

            return true;
        }

        if (cmd.getName().equalsIgnoreCase("logingoster")) {
            if (!player.hasPermission("loginx.view")) {
                player.sendMessage("§cYetkin yok.");
                return true;
            }

            if (args.length != 1) {
                player.sendMessage("/logingoster <oyuncu>");
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            UUID uuid = target.getUniqueId();

            player.sendMessage("§6==== LoginX Güvenlik Raporu ====");
            player.sendMessage("§eİsim: §f" + target.getName());
            player.sendMessage("§eSon IP: §f" + lastIP.getOrDefault(uuid, "Bilinmiyor"));
            player.sendMessage("§eBaşarısız Deneme: §f" + attempts.getOrDefault(uuid, 0));
            player.sendMessage("§eKayıtlı mı: §f" + passwords.containsKey(uuid));
            player.sendMessage("§7LoginX güvenlik sistemi aktif.");

            return true;
        }

        return false;
    }

    private void notifyOps(Player p, String status) {
        String time = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date());
        String ip = p.getAddress().getAddress().getHostAddress();

        String msg = "§x§F§F§A§5§0§0L§x§F§F§B§0§0§0o§x§F§F§B§B§0§0g§x§F§F§C§6§0§0i§x§F§F§D§1§0§0n§x§F§F§D§C§0§0X §7| "
                + "§e" + time + " §7| §b" + p.getName() + " §7| §fIP: " + ip + " §7| " + status;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.isOp()) online.sendMessage(msg);
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
