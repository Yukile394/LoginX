package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

public class LoginX2 implements Listener, CommandExecutor {

    private final LoginX plugin;
    private final Map<UUID, Long> lastInventoryClick = new HashMap<>();

    public LoginX2(LoginX plugin) {
        this.plugin = plugin;
        // Komutları bu sınıfa bağla
        String[] cmds = {"ban", "unban", "kick", "mute", "unmute", "ipban", "kickip"};
        for (String c : cmds) {
            plugin.getCommand(c).setExecutor(this);
        }
    }

    // --- MODERASYON TASARIMI (SİMETRİK PEMBE RGB) ---
    private void broadcastPunishment(String type, String target, String staff, String reason, String time) {
        String line = plugin.color("&#FF1493&m----------------------------------------");
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(line);
        Bukkit.broadcastMessage(centerText(plugin.color("&#FF69B4&l(" + type + ")")));
        Bukkit.broadcastMessage(plugin.color("  &#FFB6C1Banlanan Oyuncu: &f" + target));
        Bukkit.broadcastMessage(plugin.color("  &#FFB6C1Banlayan Yetkili: &f" + staff));
        Bukkit.broadcastMessage(plugin.color("  &#FFB6C1Banlanma Süresi: &f" + time));
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(centerText(plugin.color("&#FF69B4[Discord: discord.gg/loginx]")));
        Bukkit.broadcastMessage(centerText(plugin.color("&#FFB6C1[Cezası: " + reason + "]")));
        Bukkit.broadcastMessage(line);
        Bukkit.broadcastMessage("");
    }

    private String centerText(String text) {
        int maxWidth = 40;
        int spaces = (maxWidth - ChatColor.stripColor(text).length()) / 2;
        return " ".repeat(Math.max(0, spaces)) + text;
    }

    // --- KOMUT İŞLEYİCİ ---
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("loginx.admin")) return true;

        if (args.length < 1) {
            sender.sendMessage(plugin.color("&#FF0000Kullanım: /" + label + " <oyuncu> [sebep]"));
            return true;
        }

        String targetName = args[0];
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Kurallara Aykırı Hareket";
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        String staffName = sender.getName();

        switch (cmd.getName().toLowerCase()) {
            case "ban":
                plugin.getConfig().set("punishments.bans." + target.getUniqueId(), reason);
                plugin.saveConfig();
                broadcastPunishment("Ban", targetName, staffName, reason, "Süresiz");
                if (target.isOnline()) ((Player) target).kickPlayer(plugin.color("&#FF0000Yasaklandınız!\n&#FFB6C1Sebep: " + reason));
                break;

            case "unban":
                plugin.getConfig().set("punishments.bans." + target.getUniqueId(), null);
                plugin.saveConfig();
                sender.sendMessage(plugin.color("&#00FF00" + targetName + " adlı oyuncunun banı kaldırıldı."));
                break;

            case "mute":
                plugin.getConfig().set("punishments.mutes." + target.getUniqueId(), reason);
                plugin.saveConfig();
                broadcastPunishment("Mute", targetName, staffName, reason, "Süresiz");
                break;

            case "unmute":
                plugin.getConfig().set("punishments.mutes." + target.getUniqueId(), null);
                plugin.saveConfig();
                sender.sendMessage(plugin.color("&#00FF00" + targetName + " susturması kaldırıldı."));
                break;

            case "kick":
                if (target.isOnline()) {
                    ((Player) target).kickPlayer(plugin.color("&#FF0000Sunucudan Atıldınız!\n&#FFB6C1Yetkili: " + staffName + "\n&#FFB6C1Sebep: " + reason));
                    broadcastPunishment("Kick", targetName, staffName, reason, "Tek Seferlik");
                }
                break;

            case "ipban":
                if (target.isOnline()) {
                    String ip = ((Player) target).getAddress().getAddress().getHostAddress();
                    plugin.getConfig().set("punishments.ipbans." + ip.replace(".", "_"), reason);
                    plugin.saveConfig();
                    broadcastPunishment("IP-Ban", targetName, staffName, reason, "Süresiz");
                    ((Player) target).kickPlayer(plugin.color("&#FF0000IP Adresiniz Yasaklandı!"));
                }
                break;
        }
        return true;
    }

    // --- EVENTLER (BAN & MUTE KONTROLÜ) ---

    @EventHandler
    public void onLoginCheck(AsyncPlayerPreLoginEvent e) {
        UUID uuid = e.getUniqueId();
        String ip = e.getAddress().getHostAddress().replace(".", "_");

        if (plugin.getConfig().contains("punishments.bans." + uuid)) {
            String reason = plugin.getConfig().getString("punishments.bans." + uuid);
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.color("&#FF0000Sunucudan Yasaklısınız!\n&#FFB6C1Sebep: " + reason));
        }
        
        if (plugin.getConfig().contains("punishments.ipbans." + ip)) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.color("&#FF0000IP Adresiniz Bu Sunucudan Yasaklı!"));
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (plugin.getConfig().contains("punishments.mutes." + e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            String reason = plugin.getConfig().getString("punishments.mutes." + e.getPlayer().getUniqueId());
            e.getPlayer().sendMessage(plugin.color("&#FF0000Şu an susturulmuş haldesiniz!\n&#FFB6C1Sebep: " + reason));
        }
    }

    // --- ANTI-CHEAT BÖLÜMÜ ---

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!plugin.loggedIn.contains(p.getUniqueId())) { e.setCancelled(true); return; }
        if (p.getGameMode() != GameMode.SURVIVAL) return;

        double yDiff = e.getTo().getY() - e.getFrom().getY();
        double dist = e.getFrom().distance(e.getTo());

        if (yDiff > 0.85 && p.getVelocity().getY() < 0.1) {
            kickCheater(p, "Fly (Uçma)");
        }
        if (dist > 1.1 && p.getFallDistance() == 0 && !p.isGliding() && !p.hasPotionEffect(org.bukkit.potion.PotionEffectType.SPEED)) {
            kickCheater(p, "Speed (Hız)");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!plugin.loggedIn.contains(p.getUniqueId())) { e.setCancelled(true); return; }

        long now = System.currentTimeMillis();
        long lastClick = lastInventoryClick.getOrDefault(p.getUniqueId(), 0L);
        if (now - lastClick < 15) {
            e.setCancelled(true);
            kickCheater(p, "AutoTotem/Macro");
            return;
        }
        lastInventoryClick.put(p.getUniqueId(), now);
    }

    private void kickCheater(Player p, String reason) {
        new BukkitRunnable() {
            @Override
            public void run() {
                p.kickPlayer(plugin.color("&#FF0000[LoginX Shield]\n\n&fHile: &e" + reason));
                broadcastPunishment("Anti-Cheat", p.getName(), "Sistem", reason, "Süresiz");
            }
        }.runTask(plugin);
    }
}
