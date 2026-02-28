package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
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

    private final FileConfiguration cfg = getConfig();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        getLogger().info("§dLoginX güvenlik sistemi aktif.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String ip = p.getAddress().getAddress().getHostAddress();

        if (lastIP.containsKey(p.getUniqueId()) && !lastIP.get(p.getUniqueId()).equals(ip)) {
            notifyOps(p, "§cIP DEĞİŞTİ!");
        }
        lastIP.put(p.getUniqueId(), ip);

        if (!loggedIn.contains(p.getUniqueId())) {
            p.sendTitle(color("&dKayıt Ol /register şifre şifre"), color("&fLogin için bekleniyor..."), 10, 70, 20);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
            p.setWalkSpeed(0f); // Hareketi kısıtla
        }

        notifyOps(p, "§aOyuncu katıldı: " + p.getName());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!loggedIn.contains(p.getUniqueId())) {
            e.setCancelled(true); // Login olmadan hareket edemez
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        switch (cmd.getName().toLowerCase()) {
            case "register":
                if (args.length != 2) {
                    player.sendMessage(color("&cKullanım: /register <şifre> <şifre>"));
                    return true;
                }
                if (!args[0].equals(args[1])) {
                    player.sendMessage(color("&cŞifreler eşleşmiyor!"));
                    return true;
                }

                passwords.put(uuid, hash(args[0]));
                loggedIn.add(uuid);
                player.sendTitle(color("&aBaşarıyla Kayıt Oldun!"), color("&dGiriş yapabilirsiniz /login <şifre>"), 10, 70, 20);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                player.setWalkSpeed(0.2f); // Normal hız
                notifyOps(player, "§aYeni kayıt: " + player.getName());
                return true;

            case "login":
                if (args.length != 1) {
                    player.sendMessage(color("&cKullanım: /login <şifre>"));
                    return true;
                }

                if (cooldown.containsKey(uuid) && now - cooldown.get(uuid) < 2000) {
                    player.sendMessage(color("&cÇok hızlı deniyorsun!"));
                    return true;
                }
                cooldown.put(uuid, now);

                if (!passwords.containsKey(uuid)) {
                    player.sendMessage(color("&cÖnce /register ile kayıt olmalısın!"));
                    return true;
                }

                if (passwords.get(uuid).equals(hash(args[0]))) {
                    loggedIn.add(uuid);
                    attempts.put(uuid, 0);
                    player.sendMessage(color("&aBaşarılı giriş!"));
                    player.sendTitle(color("&aGiriş Başarılı!"), color("&dHoşgeldin " + player.getName()), 10, 70, 20);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    player.setWalkSpeed(0.2f);
                    notifyOps(player, "§aBaşarılı giriş: " + player.getName());
                } else {
                    int fail = attempts.getOrDefault(uuid, 0) + 1;
                    attempts.put(uuid, fail);
                    player.sendMessage(color("&cYanlış şifre!"));
                    notifyOps(player, "§cBaşarısız deneme: " + player.getName());
                    if (fail >= 3) player.kickPlayer("§cÇok fazla yanlış deneme!");
                }
                return true;

            case "logingoster":
                if (!player.hasPermission("loginx.view")) {
                    player.sendMessage("§cYetkin yok.");
                    return true;
                }
                if (args.length != 1) {
                    player.sendMessage("/logingoster <oyuncu>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                UUID targetUUID = target.getUniqueId();
                player.sendMessage(color("&d==== LoginX Güvenlik Raporu ===="));
                player.sendMessage(color("&eİsim: &f" + target.getName()));
                player.sendMessage(color("&eSon IP: &f" + lastIP.getOrDefault(targetUUID, "Bilinmiyor")));
                player.sendMessage(color("&eBaşarısız Deneme: &f" + attempts.getOrDefault(targetUUID, 0)));
                player.sendMessage(color("&eKayıtlı mı: &f" + passwords.containsKey(targetUUID)));
                player.sendMessage(color("&7LoginX güvenlik sistemi aktif."));
                return true;

            case "incele":
                if (args.length != 1) {
                    player.sendMessage(color("&cKullanım: /incele <oyuncu>"));
                    return true;
                }
                OfflinePlayer ipt = Bukkit.getOfflinePlayer(args[0]);
                player.sendMessage(color("&d==== Oyuncu İncele ===="));
                player.sendMessage(color("&eİsim: &f" + ipt.getName()));
                player.sendMessage(color("&eKayıtlı mı: &f" + passwords.containsKey(ipt.getUniqueId())));
                player.sendMessage(color("&eSon IP: &f" + lastIP.getOrDefault(ipt.getUniqueId(), "Bilinmiyor")));
                return true;

            default:
                return false;
        }
    }

    private void notifyOps(Player p, String status) {
        String time = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date());
        String ip = p.getAddress().getAddress().getHostAddress();

        String msg = "§x§F§F§A§5§0§0L§x§F§F§B§0§0§0o§x§F§F§B§B§0§0g§x§F§F§C§6§0§0i§x§F§F§D§1§0§0n§x§F§F§D§C§0§0X §7| "
                + "§e" + time + " §7| §b" + p.getName() + " §7| §dŞifre kontrolü: " + status;

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
