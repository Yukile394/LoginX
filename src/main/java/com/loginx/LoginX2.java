package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoginX2 implements Listener, CommandExecutor {

    private final LoginX plugin;

    public LoginX2(LoginX plugin) {
        this.plugin = plugin;
        // TÜM KOMUTLARIN KAYDI
        String[] cmds = {"ban", "ipban", "mute", "ipmute", "kick", "ipkick", "unban", "pardon", "unmute", "unipban"};
        for (String c : cmds) {
            PluginCommand pc = plugin.getCommand(c);
            if (pc != null) pc.setExecutor(this);
        }
        startVisuals();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("loginx.admin")) {
            sender.sendMessage(color("&#FF1493[SVX NW] &cBu işlem için yetkiniz yok!"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(color("&#FF69B4Kullanım: /" + label + " <oyuncu/IP> [süre] [sebep]"));
            return true;
        }

        String targetName = args[0];
        String timeStr = (args.length > 1) ? args[1] : "Süresiz";
        String reason = (args.length > 2) ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "Kurallara Aykırı Davranış";
        long duration = parseTime(timeStr);
        long expiry = (duration == -1) ? -1 : System.currentTimeMillis() + duration;

        Player onlineTarget = Bukkit.getPlayer(targetName);
        String targetIP = (onlineTarget != null) ? onlineTarget.getAddress().getAddress().getHostAddress() : targetName;

        switch (cmd.getName().toLowerCase()) {
            case "ban":
                save("bans", targetName, reason, expiry, sender.getName());
                announce("BAN", targetName, sender.getName(), reason, timeStr, false);
                if (onlineTarget != null) onlineTarget.kickPlayer(getKickScreen("YASAKLANDINIZ", reason, sender.getName(), timeStr));
                break;

            case "ipban":
                save("ipbans", targetIP.replace(".", "_"), reason, expiry, sender.getName());
                announce("IP-BAN", targetName, sender.getName(), reason, timeStr, false);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getAddress().getAddress().getHostAddress().equals(targetIP))
                        p.kickPlayer(getKickScreen("IP ADRESİNİZ YASAKLANDI", reason, sender.getName(), timeStr));
                }
                break;

            case "mute":
                save("mutes", targetName, reason, expiry, sender.getName());
                announce("MUTE", targetName, sender.getName(), reason, timeStr, false);
                break;

            case "ipmute":
                save("ipmutes", targetIP.replace(".", "_"), reason, expiry, sender.getName());
                announce("IP-MUTE", targetName, sender.getName(), reason, timeStr, false);
                break;

            case "kick":
                if (onlineTarget != null) {
                    onlineTarget.kickPlayer(getKickScreen("SUNUCUDAN ATILDINIZ", reason, sender.getName(), "Yok"));
                    announce("KICK", targetName, sender.getName(), reason, "Tek Seferlik", false);
                }
                break;

            case "ipkick":
                announce("IP-KICK", targetName, sender.getName(), reason, "Tek Seferlik", false);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getAddress().getAddress().getHostAddress().equals(targetIP))
                        p.kickPlayer(getKickScreen("IP KICK", reason, sender.getName(), "Yok"));
                }
                break;

            case "unban":
            case "pardon":
                plugin.getConfig().set("punishments.bans." + targetName, null);
                plugin.saveConfig();
                announce("CEZA KALDIRILDI", targetName, sender.getName(), "Affedildi", "-", true);
                break;

            case "unmute":
                plugin.getConfig().set("punishments.mutes." + targetName, null);
                plugin.saveConfig();
                announce("CEZA KALDIRILDI", targetName, sender.getName(), "Konuşma İzni Verildi", "-", true);
                break;

            case "unipban":
                plugin.getConfig().set("punishments.ipbans." + targetName.replace(".", "_"), null);
                plugin.saveConfig();
                announce("CEZA KALDIRILDI", targetName, sender.getName(), "IP Engeli Kalktı", "-", true);
                break;
        }
        return true;
    }

    // --- CEZA DUYURU SİSTEMİ (CHATE DÜŞEN KISIM) ---
    private void announce(String type, String target, String staff, String reason, String time, boolean removed) {
        String line = color("&#FF69B4&m----------------------------------------");
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(line);
        Bukkit.broadcastMessage(center(removed ? "&#00FF00( İşlem Tamamlandı )" : "&#FF1493( Ceza Sistemi )"));
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(color("  &#FFB6C1" + (removed ? "Affedilen" : "Cezalı") + " Oyuncu: &f" + target));
        Bukkit.broadcastMessage(color("  &#FFB6C1İşlem Yapan: &f" + staff));
        if (!removed) {
            Bukkit.broadcastMessage(color("  &#FFB6C1Ceza Süresi: &e" + time));
            Bukkit.broadcastMessage(color("  &#FFB6C1Sebep: &7" + reason));
        }
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(center("&#FF69B4[ Destek: discord.gg/svxnw ]"));
        Bukkit.broadcastMessage(center("&#FFB6C1[ SVX NW - Adalet Mülkün Temelidir ]"));
        Bukkit.broadcastMessage(line);
        Bukkit.broadcastMessage("");
    }

    private String getKickScreen(String title, String reason, String staff, String time) {
        return color(
            "&8&m----------------------------------------\n" +
            "&#FF1493&l⚡ SVX NW PROTECTION ⚡\n\n" +
            "&#FF69B4" + title + "!\n\n" +
            "&#FFB6C1► Sebep: &f" + reason + "\n" +
            "&#FFB6C1► Yetkili: &f" + staff + "\n" +
            "&#FFB6C1► Süre: &e" + time + "\n\n" +
            "&7&o[İtiraz: discord.gg/svxnw]\n" +
            "&8&m----------------------------------------"
        );
    }

    // --- MUTE, BAN VE /ME ENGELİ ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCmd(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().toLowerCase();
        if (msg.startsWith("/me ") || msg.startsWith("/minecraft:me ")) {
            if (isP(e.getPlayer(), "mutes") || isP(e.getPlayer(), "ipmutes")) {
                e.setCancelled(true);
                e.getPlayer().sendMessage(color("&#FF1493[SVX NW] &cMuteliyken bu komutu kullanamazsınız!"));
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (isP(e.getPlayer(), "mutes") || isP(e.getPlayer(), "ipmutes")) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(color("&#FF1493[SVX NW] &cSusturulmuş durumdasınız!"));
        }
    }

    @EventHandler
    public void onJoin(AsyncPlayerPreLoginEvent e) {
        String name = e.getName();
        String ip = e.getAddress().getHostAddress().replace(".", "_");
        if (checkP("bans", name) || checkP("ipbans", ip)) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, getKickScreen("GİRİŞ ENGELLENDİ", "Aktif bir yasağınız bulunuyor.", "Yönetim", "Bilinmiyor"));
        }
    }

    // --- TAB VE SCOREBOARD (GÖRSELDEKİ GİBİ PEMBE) ---
    private void startVisuals() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateScoreboard(p);
                    p.setPlayerListHeaderFooter(
                        color("\n&#FF1493&lSVX NW NETWORK\n&#FFB6C1Keyifli Oyunlar Dileriz!\n"),
                        color("\n&#FF69B4www.svxnw.com\n&#FFB6C1discord.gg/svxnw\n")
                    );
                }
            }
        }.runTaskTimer(plugin, 0, 20L);
    }

    private void updateScoreboard(Player p) {
        Scoreboard b = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = b.registerNewObjective("svx", "dummy", color("&#FF1493&lSVX NW"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        String[] lines = {
            "&7&m------------------",
            "&#FFB6C1İSİM &8» &f" + p.getName(),
            "&#FFB6C1RÜTBE &8» &fOyuncu",
            "&#FFB6C1KLAN &8» &cX",
            " ",
            "&#FFB6C1KATLETME &8» &a0",
            "&#FFB6C1KATLEDİLME &8» &c0",
            " ",
            "&#FFB6C1PING &8» &a" + p.getPing() + "ms",
            "&#FFB6C1AKTİF &8» &f" + Bukkit.getOnlinePlayers().size(),
            "&#FFB6C1OY PARTİSİ &8» &d0/50",
            "&7&m------------------ ",
            "&#FF69B4www.svxnw.com"
        };
        int i = lines.length;
        for (String s : lines) o.getScore(color(s)).setScore(i--);
        p.setScoreboard(b);
    }

    // --- YARDIMCI METOTLAR ---
    private void save(String t, String k, String r, long e, String s) {
        plugin.getConfig().set("punishments." + t + "." + k + ".reason", r);
        plugin.getConfig().set("punishments." + t + "." + k + ".expiry", e);
        plugin.getConfig().set("punishments." + t + "." + k + ".staff", s);
        plugin.saveConfig();
    }

    private boolean isP(Player p, String type) {
        String key = type.contains("ip") ? p.getAddress().getAddress().getHostAddress().replace(".", "_") : p.getName();
        return checkP(type, key);
    }

    private boolean checkP(String type, String key) {
        String path = "punishments." + type + "." + key;
        if (!plugin.getConfig().contains(path)) return false;
        long exp = plugin.getConfig().getLong(path + ".expiry");
        if (exp != -1 && exp < System.currentTimeMillis()) {
            plugin.getConfig().set(path, null); plugin.saveConfig(); return false;
        }
        return true;
    }

    private String color(String text) {
        Pattern pattern = Pattern.compile("&#([a-fA-F0-9]{6})");
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) replacement.append("§").append(c);
            matcher.appendReplacement(buffer, replacement.toString());
        }
        return ChatColor.translateAlternateColorCodes('&', matcher.appendTail(buffer).toString());
    }

    private String center(String t) {
        int s = (45 - ChatColor.stripColor(color(t)).length()) / 2;
        return " ".repeat(Math.max(0, s)) + color(t);
    }

    private long parseTime(String t) {
        try {
            long v = Long.parseLong(t.replaceAll("[^0-9]", ""));
            if (t.endsWith("s")) return v * 1000L;
            if (t.endsWith("dak")) return v * 60000L;
            if (t.endsWith("sa")) return v * 3600000L;
            if (t.endsWith("g")) return v * 86400000L;
        } catch (Exception e) {}
        return -1;
    }
}
