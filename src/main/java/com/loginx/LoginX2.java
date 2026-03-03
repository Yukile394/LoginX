package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

public class LoginX2 implements Listener, CommandExecutor {

    private final LoginX plugin;
    private final Map<UUID, Long> lastInventoryClick = new HashMap<>();

    public LoginX2(LoginX plugin) {
        this.plugin = plugin;
        String[] cmds = {"ban", "mute", "unban", "unmute", "kick"};
        for (String c : cmds) {
            PluginCommand pc = plugin.getCommand(c);
            if (pc != null) pc.setExecutor(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("loginx.admin")) return true;
        if (args.length < 1) { sender.sendMessage(plugin.color("&#FF0000Kullanım: /" + label + " <oyuncu> <süre> <sebep>")); return true; }

        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        long duration = (args.length > 1) ? parseTime(args[1]) : -1;
        String reason = (args.length > 2) ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "Kurallara Aykırı Hareket";
        long expiry = (duration == -1) ? -1 : System.currentTimeMillis() + duration;
        String timeStr = args.length > 1 ? args[1] : "Süresiz";

        switch (cmd.getName().toLowerCase()) {
            case "ban":
                plugin.getConfig().set("punishments.bans." + target.getUniqueId() + ".reason", reason);
                plugin.getConfig().set("punishments.bans." + target.getUniqueId() + ".expiry", expiry);
                plugin.saveConfig();
                announcePunishment("BAN", targetName, sender.getName(), reason, timeStr);
                if (target.isOnline()) ((Player) target).kickPlayer(punishKickScreen("Sunucu Erişimi Kapatıldı!", reason, sender.getName(), timeStr));
                break;
            case "mute":
                plugin.getConfig().set("punishments.mutes." + target.getUniqueId() + ".reason", reason);
                plugin.getConfig().set("punishments.mutes." + target.getUniqueId() + ".expiry", expiry);
                plugin.saveConfig();
                announcePunishment("MUTE", targetName, sender.getName(), reason, timeStr);
                break;
            case "unban":
                plugin.getConfig().set("punishments.bans." + target.getUniqueId(), null);
                plugin.saveConfig();
                sender.sendMessage("Ban kaldırıldı.");
                break;
        }
        return true;
    }

    private String punishKickScreen(String title, String reason, String staff, String time) {
        return plugin.color(
            "&8&m----------------------------------------\n" +
            "&#FF1493&l⚡ LOGINX PROTECTION ⚡\n\n" +
            "&#FF69B4" + title + "\n\n" +
            "&#FFB6C1► Durum: &fSunucudan Uzaklaştırıldınız\n" +
            "&#FFB6C1► Sebep: &e" + reason + "\n" +
            "&#FFB6C1► Yetkili: &f" + staff + "\n" +
            "&#FFB6C1► Süre: &e" + time + "\n\n" +
            "&7&o[İtiraz ve Bilgi için: discord.gg/loginx]\n" +
            "&8&m----------------------------------------"
        );
    }

    private void announcePunishment(String type, String target, String staff, String reason, String time) {
        String line = plugin.color("&#FF69B4&m----------------------------------------");
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(line);
        Bukkit.broadcastMessage(center("&#FF1493&l⚡ " + type + " İŞLEMİ ⚡"));
        Bukkit.broadcastMessage(plugin.color("  &#FFB6C1Oyuncu: &f" + target));
        Bukkit.broadcastMessage(plugin.color("  &#FFB6C1Yetkili: &f" + staff));
        Bukkit.broadcastMessage(plugin.color("  &#FFB6C1Süre: &f" + time));
        Bukkit.broadcastMessage(plugin.color("  &#FFB6C1Sebep: &e" + reason));
        Bukkit.broadcastMessage(line);
        Bukkit.broadcastMessage("");
    }

    private String center(String text) {
        int space = (40 - ChatColor.stripColor(plugin.color(text)).length()) / 2;
        return " ".repeat(Math.max(0, space)) + plugin.color(text);
    }

    private long parseTime(String time) {
        try {
            long value = Long.parseLong(time.replaceAll("[^0-9]", ""));
            if (time.endsWith("s")) return value * 1000L;
            if (time.endsWith("dak")) return value * 60000L;
            if (time.endsWith("sa")) return value * 3600000L;
            if (time.endsWith("g")) return value * 86400000L;
        } catch (Exception e) { return -1; }
        return -1;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        long now = System.currentTimeMillis();
        if (now - lastInventoryClick.getOrDefault(p.getUniqueId(), 0L) < 20) {
            e.setCancelled(true);
            new BukkitRunnable() { @Override public void run() { p.kickPlayer(plugin.color("&#FF0000[LoginX]\n&cAutoTotem/Macro Yasak!")); } }.runTask(plugin);
        }
        lastInventoryClick.put(p.getUniqueId(), now);
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        String path = "punishments.bans." + e.getUniqueId();
        if (plugin.getConfig().contains(path)) {
            long exp = plugin.getConfig().getLong(path + ".expiry");
            if (exp != -1 && exp < System.currentTimeMillis()) { plugin.getConfig().set(path, null); plugin.saveConfig(); return; }
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, punishKickScreen("Yasaklamanız Devam Ediyor!", plugin.getConfig().getString(path + ".reason"), "Sistem", "Kalan Süre Mevcut"));
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        String path = "punishments.mutes." + e.getPlayer().getUniqueId();
        if (plugin.getConfig().contains(path)) {
            long exp = plugin.getConfig().getLong(path + ".expiry");
            if (exp != -1 && exp < System.currentTimeMillis()) { plugin.getConfig().set(path, null); plugin.saveConfig(); return; }
            e.setCancelled(true);
            e.getPlayer().sendMessage(plugin.color("&#FF0000Susturuldunuz! Sebep: " + plugin.getConfig().getString(path + ".reason")));
        }
    }
}
