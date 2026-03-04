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

        String[] cmds = {"ban", "unban", "kick", "mute", "unmute", "ipban"};
        for (String c : cmds) {
            PluginCommand command = plugin.getCommand(c);
            if (command != null) {
                command.setExecutor(this);
            }
        }
    }

    // =========================
    // CEZA TASARIMI
    // =========================
    private void broadcastPunishment(String type, String target, String staff, String reason, String time) {
        String line = ChatColor.of("#FF1493") + "----------------------------------------";

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(line);
        Bukkit.broadcastMessage(center("§x§F§F§6§9§B§4§l(" + type + ")"));
        Bukkit.broadcastMessage(" §x§F§F§B§6§C§1Oyuncu: §f" + target);
        Bukkit.broadcastMessage(" §x§F§F§B§6§C§1Yetkili: §f" + staff);
        Bukkit.broadcastMessage(" §x§F§F§B§6§C§1Süre: §f" + time);
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(center("§x§F§F§6§9§B§4Sebep: §f" + reason));
        Bukkit.broadcastMessage(line);
        Bukkit.broadcastMessage("");
    }

    private String center(String text) {
        int max = 40;
        int spaces = (max - ChatColor.stripColor(text).length()) / 2;
        return " ".repeat(Math.max(0, spaces)) + text;
    }

    // =========================
    // KOMUTLAR
    // =========================
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.hasPermission("loginx.admin")) return true;

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Kullanım: /" + label + " <oyuncu> [sebep]");
            return true;
        }

        String targetName = args[0];
        String reason = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : "Kurallara Aykırı Hareket";

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        String staff = sender.getName();

        switch (cmd.getName().toLowerCase()) {

            case "ban":
                plugin.getConfig().set("punishments.bans." + target.getUniqueId(), reason);
                plugin.saveConfig();
                broadcastPunishment("BAN", targetName, staff, reason, "Süresiz");

                if (target.isOnline()) {
                    ((Player) target).kickPlayer("§cYasaklandınız!\n§fSebep: " + reason);
                }
                break;

            case "unban":
                plugin.getConfig().set("punishments.bans." + target.getUniqueId(), null);
                plugin.saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Ban kaldırıldı.");
                break;

            case "mute":
                plugin.getConfig().set("punishments.mutes." + target.getUniqueId(), reason);
                plugin.saveConfig();
                broadcastPunishment("MUTE", targetName, staff, reason, "Süresiz");
                break;

            case "unmute":
                plugin.getConfig().set("punishments.mutes." + target.getUniqueId(), null);
                plugin.saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Mute kaldırıldı.");
                break;

            case "kick":
                if (target.isOnline()) {
                    ((Player) target).kickPlayer("§cSunucudan Atıldınız!\n§fSebep: " + reason);
                    broadcastPunishment("KICK", targetName, staff, reason, "Tek Seferlik");
                }
                break;

            case "ipban":
                if (target.isOnline()) {
                    Player p = (Player) target;
                    String ip = p.getAddress().getAddress().getHostAddress().replace(".", "_");
                    plugin.getConfig().set("punishments.ipbans." + ip, reason);
                    plugin.saveConfig();

                    broadcastPunishment("IP-BAN", targetName, staff, reason, "Süresiz");
                    p.kickPlayer("§cIP Adresiniz Yasaklandı!");
                }
                break;
        }

        return true;
    }

    // =========================
    // BAN & MUTE KONTROL
    // =========================
    @EventHandler
    public void onLogin(AsyncPlayerPreLoginEvent e) {

        UUID uuid = e.getUniqueId();
        String ip = e.getAddress().getHostAddress().replace(".", "_");

        if (plugin.getConfig().contains("punishments.bans." + uuid)) {
            String reason = plugin.getConfig().getString("punishments.bans." + uuid);
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    "§cSunucudan Yasaklısınız!\n§fSebep: " + reason);
        }

        if (plugin.getConfig().contains("punishments.ipbans." + ip)) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    "§cIP Adresiniz Yasaklı!");
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {

        if (plugin.getConfig().contains("punishments.mutes." + e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            String reason = plugin.getConfig().getString("punishments.mutes." + e.getPlayer().getUniqueId());
            e.getPlayer().sendMessage("§cSusturulmuşsunuz!\n§fSebep: " + reason);
        }
    }

    // =========================
    // LOGIN KONTROL
    // =========================
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();

        if (!plugin.getLoggedIn().contains(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventory(InventoryClickEvent e) {

        if (!(e.getWhoClicked() instanceof Player p)) return;

        if (!plugin.getLoggedIn().contains(p.getUniqueId())) {
            e.setCancelled(true);
            return;
        }

        long now = System.currentTimeMillis();
        long last = lastInventoryClick.getOrDefault(p.getUniqueId(), 0L);

        if (now - last < 15) {
            e.setCancelled(true);
            p.kickPlayer("§cMacro Tespit Edildi!");
        }

        lastInventoryClick.put(p.getUniqueId(), now);
    }
}
