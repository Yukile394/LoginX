package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
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
    private final Set<UUID> blocked = new HashSet<>();
    private final Map<UUID, Long> joinTime = new HashMap<>();
    private final Map<UUID, Integer> titleSpamCount = new HashMap<>();

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
        lastIP.put(p.getUniqueId(), ip);

        blocked.add(p.getUniqueId());
        joinTime.put(p.getUniqueId(), System.currentTimeMillis());
        titleSpamCount.put(p.getUniqueId(), 0);

        startTitle(p);

        notifyOps(p, "&dOyuncu katıldı: " + p.getName());

        // 1 dakikada login/register olmazsa kick
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!loggedIn.contains(p.getUniqueId())) {
                    p.kickPlayer(color("&cKayıt veya giriş yapılmadığı için atıldınız!"));
                }
            }
        }.runTaskLater(this, 20 * 60); // 1 dakika
    }

    private void startTitle(Player p) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!blocked.contains(p.getUniqueId())) cancel();
                int spam = titleSpamCount.getOrDefault(p.getUniqueId(), 0);
                if (spam >= 4) cancel();

                String title;
                String subtitle;
                if (!passwords.containsKey(p.getUniqueId())) {
                    title = color("&dKayıt Ol");
                    subtitle = color("&f/register <şifre> <şifre>");
                } else {
                    title = color("&bGiriş Yap");
                    subtitle = color("&f/login <şifre>");
                }
                p.sendTitle(title, subtitle, 10, 70, 20);
                titleSpamCount.put(p.getUniqueId(), spam + 1);
            }
        }.runTaskTimer(this, 0L, 40L); // 2 saniye arayla
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (blocked.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (blocked.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(color("&cÖnce /register veya /login yapmalısın!"));
        }
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (blocked.contains(p.getUniqueId())) {
            String cmd = e.getMessage().split(" ")[0].toLowerCase();
            if (!cmd.equals("/login") && !cmd.equals("/register") && !cmd.equals("/logingoster") && !cmd.equals("/incele")) {
                e.setCancelled(true);
                p.sendMessage(color("&cÖnce /register veya /login yapmalısın!"));
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (cmd.getName().equalsIgnoreCase("register")) {
            if (loggedIn.contains(uuid)) {
                player.sendMessage(color("&aZaten giriş yaptınız!"));
                return true;
            }
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
            blocked.remove(uuid);
            player.sendTitle(color("&aBaşarılı!"), color("&fKayıt oldunuz."), 10, 70, 20);
            notifyOps(player, "&aYeni kayıt: " + player.getName());
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("login")) {
            if (loggedIn.contains(uuid)) {
                player.sendMessage(color("&aZaten giriş yaptınız!"));
                return true;
            }
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
                player.sendMessage(color("&cÖnce /register yapmalısın!"));
                return true;
            }

            String hashed = hash(args[0]);
            if (passwords.get(uuid).equals(hashed)) {
                loggedIn.add(uuid);
                blocked.remove(uuid);
                attempts.put(uuid, 0);
                player.sendTitle(color("&aGiriş Başarılı!"), color("&fHoşgeldin " + player.getName()), 10, 70, 20);
                notifyOps(player, "&aBaşarılı giriş: " + player.getName());
            } else {
                int fail = attempts.getOrDefault(uuid, 0) + 1;
                attempts.put(uuid, fail);
                player.sendMessage(color("&cYanlış şifre!"));
                notifyOps(player, "&cBaşarısız deneme: " + player.getName());
                if (fail >= 3) player.kickPlayer("§cÇok fazla yanlış deneme!");
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("logingoster") || cmd.getName().equalsIgnoreCase("incele")) {
            if (!player.hasPermission("loginx.view")) {
                player.sendMessage(color("&cYetkin yok."));
                return true;
            }
            if (args.length != 1) {
                player.sendMessage(color("/" + cmd.getName() + " <oyuncu>"));
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            UUID targetUUID = target.getUniqueId();
            player.sendMessage(color("&6==== LoginX Raporu ===="));
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
        String msg = ChatColor.LIGHT_PURPLE + "[LoginX] " + ChatColor.GOLD + time + " | " + ChatColor.AQUA + p.getName() + " | IP: " + ip + " | " + ChatColor.WHITE + status;
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
