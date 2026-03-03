package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;
import java.util.*;

public class LoginX2 implements Listener, CommandExecutor {

    private final LoginX plugin;
    private final Map<UUID, Long> lastInventoryClick = new HashMap<>();

    public LoginX2(LoginX plugin) {
        this.plugin = plugin;
        // TÜM KOMUTLAR EKLENDİ
        String[] commands = {"ban", "mute", "unban", "unmute", "kick", "ipban", "ipkick", "ipmute", "unipban"};
        for (String c : commands) {
            PluginCommand pc = plugin.getCommand(c);
            if (pc != null) pc.setExecutor(this);
        }
        // Görsel sistemleri başlat
        startVisualTasks();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("loginx.admin")) {
            sender.sendMessage(plugin.color("&#FF1493[SVX NW] &cBu komut için yetkiniz yok!"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(plugin.color("&#FFB6C1Kullanım: /" + label + " <oyuncu/IP> <süre> <sebep>"));
            return true;
        }

        String targetStr = args[0];
        long duration = (args.length > 1) ? parseTime(args[1]) : -1;
        String reason = (args.length > 2) ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "Kural İhlali";
        long expiry = (duration == -1) ? -1 : System.currentTimeMillis() + duration;
        String timeDisplay = (duration == -1) ? "Süresiz" : args[1];

        Player targetPlayer = Bukkit.getPlayer(targetStr);
        String targetIP = (targetPlayer != null) ? targetPlayer.getAddress().getAddress().getHostAddress().replace(".", "_") : targetStr.replace(".", "_");

        switch (cmd.getName().toLowerCase()) {
            case "ban":
                savePunish("bans", targetStr, reason, expiry);
                announcePunishment("BAN", targetStr, sender.getName(), reason, timeDisplay, false);
                if (targetPlayer != null) targetPlayer.kickPlayer(getKickScreen("YASAKLANDINIZ", reason, sender.getName(), timeDisplay));
                break;

            case "ipban":
                savePunish("ipbans", targetIP, reason, expiry);
                announcePunishment("IP-BAN", targetStr, sender.getName(), reason, timeDisplay, false);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getAddress().getAddress().getHostAddress().replace(".", "_").equals(targetIP))
                        p.kickPlayer(getKickScreen("IP-YASAKLAMASI", reason, sender.getName(), timeDisplay));
                }
                break;

            case "mute":
                savePunish("mutes", targetStr, reason, expiry);
                announcePunishment("MUTE", targetStr, sender.getName(), reason, timeDisplay, false);
                break;

            case "kick":
                if (targetPlayer != null) {
                    targetPlayer.kickPlayer(getKickScreen("SUNUCUDAN ATILDINIZ", reason, sender.getName(), "Yok"));
                    announcePunishment("KICK", targetStr, sender.getName(), reason, "Tek Seferlik", false);
                }
                break;

            case "unban":
                plugin.getConfig().set("punishments.bans." + targetStr, null);
                plugin.saveConfig();
                announcePunishment("UNBAN", targetStr, sender.getName(), "Affedildi", "-", true);
                break;

            case "unmute":
                plugin.getConfig().set("punishments.mutes." + targetStr, null);
                plugin.saveConfig();
                announcePunishment("UNMUTE", targetStr, sender.getName(), "Susturma Kaldırıldı", "-", true);
                break;

            case "unipban":
                plugin.getConfig().set("punishments.ipbans." + targetIP, null);
                plugin.saveConfig();
                announcePunishment("UNIPBAN", targetStr, sender.getName(), "IP Engeli Kaldırıldı", "-", true);
                break;
        }
        return true;
    }

    private void savePunish(String type, String target, String reason, long expiry) {
        plugin.getConfig().set("punishments." + type + "." + target + ".reason", reason);
        plugin.getConfig().set("punishments." + type + "." + target + ".expiry", expiry);
        plugin.saveConfig();
    }

    private void announcePunishment(String type, String target, String staff, String reason, String time, boolean removed) {
        String line = plugin.color("&#FF69B4&m----------------------------------------");
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(line);
        Bukkit.broadcastMessage(center(removed ? "&#00FF00( Ceza Kaldırıldı )" : "&#FF1493( Ceza Sistemi )"));
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(plugin.color("  &#FFB6C1" + (removed ? "Affedilen" : "Banlanan") + " Oyuncu: &f" + target));
        Bukkit.broadcastMessage(plugin.color("  &#FFB6C1" + (removed ? "İşlem Yapan" : "Banlayan") + " Yetkili: &f" + staff));
        if (!removed) {
            Bukkit.broadcastMessage(plugin.color("  &#FFB6C1Banlanma Süresi: &f" + time));
        }
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(center("&#FF69B4[Discord: discord.gg/svxnw]"));
        Bukkit.broadcastMessage(center("&#FFB6C1[Sebep: " + reason + "]"));
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(line);
        Bukkit.broadcastMessage("");
    }

    private String getKickScreen(String title, String reason, String staff, String time) {
        return plugin.color("&#FF1493&l⚡ SVX NW ⚡\n\n&#FF69B4" + title + "\n\n&#FFB6C1Sebep: &f" + reason + "\n&#FFB6C1Yetkili: &f" + staff + "\n&#FFB6C1Süre: &e" + time);
    }

    // --- ME KOMUTU VE MUTE ENGELİ ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().toLowerCase();
        if (msg.startsWith("/me") || msg.startsWith("/minecraft:me")) {
            if (isMuted(e.getPlayer())) {
                e.setCancelled(true);
                e.getPlayer().sendMessage(plugin.color("&#FF1493[!] &cMuteliyken bu komutu kullanamazsınız!"));
            }
        }
    }

    private boolean isMuted(Player p) {
        return plugin.getConfig().contains("punishments.mutes." + p.getName());
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        String path = "punishments.mutes." + e.getPlayer().getName();
        if (plugin.getConfig().contains(path)) {
            long expiry = plugin.getConfig().getLong(path + ".expiry");
            if (expiry != -1 && expiry < System.currentTimeMillis()) {
                plugin.getConfig().set(path, null);
                plugin.saveConfig();
                return;
            }
            e.setCancelled(true);
            e.getPlayer().sendMessage(plugin.color("&#FF0000Susturuldunuz! Sebep: " + plugin.getConfig().getString(path + ".reason")));
        }
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        String name = e.getName();
        String ip = e.getAddress().getHostAddress().replace(".", "_");
        if (plugin.getConfig().contains("punishments.bans." + name) || plugin.getConfig().contains("punishments.ipbans." + ip)) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.color("&#FF0000Sunucudan Yasaklısınız!"));
        }
    }

    // --- AUTO TOTEM / MACRO KORUMASI (SENİN KODUN) ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        long now = System.currentTimeMillis();
        if (now - lastInventoryClick.getOrDefault(p.getUniqueId(), 0L) < 20) {
            e.setCancelled(true);
            new BukkitRunnable() {
                @Override
                public void run() { p.kickPlayer(plugin.color("&#FF0000AutoTotem/Macro Yasak!")); }
            }.runTask(plugin);
        }
        lastInventoryClick.put(p.getUniqueId(), now);
    }

    // --- GÖRSEL: TAB VE SCOREBOARD (PEMBE TONLAR) ---
    private void startVisualTasks() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateScoreboard(p);
                    p.setPlayerListHeaderFooter(
                        plugin.color("\n&#FF1493&lSVX NW NETWORK\n&#FFB6C1Keyifli Oyunlar!\n"),
                        plugin.color("\n&#FF69B4discord.gg/svxnw\n")
                    );
                }
            }
        }.runTaskTimer(plugin, 0, 20L);
    }

    private void updateScoreboard(Player p) {
        Scoreboard b = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = b.registerNewObjective("svx", "dummy", plugin.color("&#FF1493&lSVX NW"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        String[] lines = {
            "&7&m------------------",
            "&#FFB6C1İsim &8» &f" + p.getName(),
            "&#FFB6C1Rütbe &8» &fOyuncu",
            " ",
            "&#FFB6C1Ping &8» &a" + p.getPing() + "ms",
            "&#FFB6C1Aktif &8» &f" + Bukkit.getOnlinePlayers().size(),
            " ",
            "&#FF69B4www.svxnw.com",
            "&7&m------------------ "
        };
        int i = lines.length;
        for (String s : lines) o.getScore(plugin.color(s)).setScore(i--);
        p.setScoreboard(b);
    }

    private String center(String text) {
        int maxWidth = 45;
        String stripped = ChatColor.stripColor(plugin.color(text));
        int spaces = (maxWidth - stripped.length()) / 2;
        return " ".repeat(Math.max(0, Math.min(maxWidth, spaces))) + plugin.color(text);
    }

    private long parseTime(String time) {
        try {
            if (time.equalsIgnoreCase("süresiz")) return -1;
            long value = Long.parseLong(time.replaceAll("[^0-9]", ""));
            if (time.endsWith("s")) return value * 1000L;
            if (time.endsWith("dak")) return value * 60000L;
            if (time.endsWith("sa")) return value * 3600000L;
            if (time.endsWith("g")) return value * 86400000L;
        } catch (Exception e) { return -1; }
        return -1;
    }
}
