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
        String[] cmds = {"ban", "ipban", "mute", "ipmute", "kick", "ipkick", "unban", "pardon", "unmute", "unipban"};
        for (String c : cmds) {
            PluginCommand pc = plugin.getCommand(c);
            if (pc != null) pc.setExecutor(this);
        }
        startScoreboardTask();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("loginx.admin")) return true;
        if (args.length < 1) { sender.sendMessage(plugin.color("&#FF69B4Kullanım: /" + label + " <oyuncu/IP> [süre] [sebep]")); return true; }

        String targetStr = args[0];
        long duration = (args.length > 1) ? parseTime(args[1]) : -1;
        String reason = (args.length > 2) ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "Kural İhlali";
        long expiry = (duration == -1) ? -1 : System.currentTimeMillis() + duration;
        String timeStr = (duration == -1) ? "Süresiz" : args[1];

        switch (cmd.getName().toLowerCase()) {
            case "ban":
            case "ipban":
                executePunishment(sender, targetStr, reason, expiry, timeStr, cmd.getName().toUpperCase());
                break;
            case "mute":
            case "ipmute":
                executePunishment(sender, targetStr, reason, expiry, timeStr, cmd.getName().toUpperCase());
                break;
            case "unban":
            case "pardon":
            case "unmute":
            case "unipban":
                removePunishment(sender, targetStr, cmd.getName().toUpperCase());
                break;
        }
        return true;
    }

    // --- CEZA SİSTEMİ MESAJ FORMATI ---
    private void announce(String type, String target, String staff, String reason, String time, boolean removed) {
        String line = plugin.color("&#FF69B4&m----------------------------------------");
        String header = removed ? "&#00FF00( Ceza Kaldırıldı )" : "&#FF1493( Ceza Sistemi )";
        
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(line);
        Bukkit.broadcastMessage(center(header));
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(plugin.color("  &#FFB6C1" + (removed ? "Affedilen" : "Cezalı") + " Oyuncu: &f" + target));
        Bukkit.broadcastMessage(plugin.color("  &#FFB6C1İşlem Yapan: &f" + staff));
        if (!removed) {
            Bukkit.broadcastMessage(plugin.color("  &#FFB6C1Ceza Süresi: &e" + time));
            Bukkit.broadcastMessage(plugin.color("  &#FFB6C1Sebep: &7" + reason));
        }
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(center("&#FF69B4[ Destek: discord.gg/svxnw ]"));
        Bukkit.broadcastMessage(center("&#FFB6C1[ SVX NW - Adalet Sistemi ]"));
        Bukkit.broadcastMessage(line);
        Bukkit.broadcastMessage("");
    }

    // --- MUTE & ME KOMUTU ENGELİ ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
        String cmd = e.getMessage().toLowerCase();
        if (cmd.startsWith("/minecraft:me") || cmd.startsWith("/me")) {
            if (isMuted(e.getPlayer())) {
                e.setCancelled(true);
                e.getPlayer().sendMessage(plugin.color("&#FF1493[SVX NW] &cMuteliyken bu komutu kullanamazsın!"));
            }
        }
    }

    private boolean isMuted(Player p) {
        String uuid = p.getUniqueId().toString();
        String ip = p.getAddress().getAddress().getHostAddress().replace(".", "_");
        return isPunished("mutes", uuid) || isPunished("ipmutes", ip);
    }

    // --- TAB & SCOREBOARD SİSTEMİ (PEMBE RGB) ---
    private void startScoreboardTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateScoreboard(p);
                    p.setPlayerListHeaderFooter(
                        plugin.color("\n&#FF1493&lSVX NW NETWORK\n&#FFB6C1Keyifli Oyunlar Dileriz!\n"),
                        plugin.color("\n&#FF69B4www.svxnw.com\n&#FFB6C1discord.gg/svxnw\n")
                    );
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void updateScoreboard(Player p) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("svxnw", "dummy", plugin.color("&#FF1493&lSVX NW"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        String[] lines = {
            "&7&m------------------",
            "&#FFB6C1İSİM &8» &f" + p.getName(),
            "&#FFB6C1RÜTBE &8» &fOyuncu", // Buraya GroupManager/LuckPerms bağlanabilir
            "&#FFB6C1KLAN &8» &cX",
            " ",
            "&#FFB6C1KATLETME &8» &a0",
            "&#FFB6C1KATLEDİLME &8» &c0",
            " ",
            "&#FFB6C1PING &8» &f" + getPing(p) + " ms",
            "&#FFB6C1ÇEVRİMİÇİ &8» &f" + Bukkit.getOnlinePlayers().size(),
            "&#FFB6C1OY PARTİSİ &8» &d0/50",
            "&7&m------------------ ",
            "&#FF69B4www.svxnw.com"
        };

        int score = lines.length;
        for (String line : lines) {
            obj.getScore(plugin.color(line)).setScore(score--);
        }
        p.setScoreboard(board);
    }

    // Yardımcı Metotlar (Cezalandırma Mantığı, Zaman Çevirme, Ping vb.)
    private void executePunishment(CommandSender s, String t, String r, long e, String ts, String type) {
        // ... (Bir önceki kodundaki kayıt mantığı buraya gelecek)
        announce(type, t, s.getName(), r, ts, false);
    }

    private void removePunishment(CommandSender s, String t, String type) {
        // ... (Kayıt silme mantığı)
        announce(type, t, s.getName(), "Affedildi", "0", true);
    }

    private int getPing(Player p) {
        try { return p.getPing(); } catch (Exception e) { return 0; }
    }

    private String center(String text) {
        int space = (45 - ChatColor.stripColor(plugin.color(text)).length()) / 2;
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

    private boolean isPunished(String type, String key) {
        String path = "punishments." + type + "." + key;
        if (!plugin.getConfig().contains(path)) return false;
        long exp = plugin.getConfig().getLong(path + ".expiry");
        if (exp != -1 && exp < System.currentTimeMillis()) {
            plugin.getConfig().set(path, null); plugin.saveConfig(); return false;
        }
        return true;
    }
}
