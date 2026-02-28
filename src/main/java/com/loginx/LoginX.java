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
    private final Set<UUID> blockedMovement = new HashSet<>();

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

        blockedMovement.add(p.getUniqueId());

        // Title mesajı
        p.sendTitle(color("&dKayıt Ol"), color("&f/register <şifre> <şifre>"), 10, 70, 20);

        notifyOps(p, "§aOyuncu katıldı: " + p.getName());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (blockedMovement.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (cmd.getName().equalsIgnoreCase("register")) {
            if (args.length != 2) {
                player.sendMessage(color("&cKullanım: /register <şifre> <şifre>"));
                return true;
            }
            if (!args[0].equals(args[1])) {
                player.sendMessage(color("&cŞifreler uyuşmuyor!"));
                return true;
            }

            String hashed = hash(args[0]);
            passwords.put(uuid, hashed);
            loggedIn.add(uuid);
            blockedMovement.remove(uuid);

            player.sendTitle(color("&aBaşarılı!"), color("&fKayıt oldunuz."), 10, 70, 20);
            player.sendMessage(color("&dRGB Floplu: &fBaşarılı şekilde kayıt oldunuz!"));
            notifyOps(player, "§aYeni kayıt: " + player.getName());
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

            return true;
        }

        if (cmd.getName().equalsIgnoreCase("login")) {
            if (args.length != 1) {
                player.sendMessage(color("&cKullanım: /login <şifre>"));
                return true;
            }

            if (cooldown.containsKey(uuid) && now - cooldown.get(uuid) < 2000) {
                player.sendMessage(color("&cÇok hızlı deniyorsun!"));
                return true;
            }
            cooldown.put(uuid, now);

            String hashed = hash(args[0]);
            if (!passwords.containsKey(uuid)) {
                player.sendMessage(color("&cÖnce kayıt olmalısın! /register <şifre> <şifre>"));
                return true;
            }

            if (passwords.get(uuid).equals(hashed)) {
                loggedIn.add(uuid);
                blockedMovement.remove(uuid);
                attempts.put(uuid, 0);

                player.sendTitle(color("&aGiriş Başarılı!"), color("&fHoşgeldin " + player.getName()), 10, 70, 20);
                player.sendMessage(color("&dRGB Floplu: &fBaşarılı giriş!"));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

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
                player.sendMessage(color("&cYetkin yok."));
                return true;
            }
            if (args.length != 1) {
                player.sendMessage(color("/logingoster <oyuncu>"));
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            UUID targetUUID = target.getUniqueId();

            player.sendMessage(color("&6==== LoginX Güvenlik Raporu ===="));
            player.sendMessage(color("&eİsim: &f" + target.getName()));
            player.sendMessage(color("&eSon IP: &f" + lastIP.getOrDefault(targetUUID, "Bilinmiyor")));
            player.sendMessage(color("&eBaşarısız Deneme: &f" + attempts.getOrDefault(targetUUID, 0)));
            player.sendMessage(color("&eKayıtlı mı: &f" + passwords.containsKey(targetUUID)));
            player.sendMessage(color("&7LoginX güvenlik sistemi aktif."));

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
