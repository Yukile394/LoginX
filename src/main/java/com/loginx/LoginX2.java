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

    private void broadcastPunishment(String type, String target, String staff, String reason, String time) {

        String line = "§d----------------------------------------";

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(line);
        Bukkit.broadcastMessage("§d§l(" + type + ")");
        Bukkit.broadcastMessage(" §fOyuncu: §d" + target);
        Bukkit.broadcastMessage(" §fYetkili: §d" + staff);
        Bukkit.broadcastMessage(" §fSüre: §d" + time);
        Bukkit.broadcastMessage(" §fSebep: §d" + reason);
        Bukkit.broadcastMessage(line);
        Bukkit.broadcastMessage("");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.hasPermission("loginx.admin")) return true;

        if (args.length < 1) {
            sender.sendMessage("§cKullanım: /" + label + " <oyuncu> [sebep]");
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
                sender.sendMessage("§aBan kaldırıldı.");
                break;

            case "mute":
                plugin.getConfig().set("punishments.mutes." + target.getUniqueId(), reason);
                plugin.saveConfig();
                broadcastPunishment("MUTE", targetName, staff, reason, "Süresiz");
                break;

            case "unmute":
                plugin.getConfig().set("punishments.mutes." + target.getUniqueId(), null);
                plugin.saveConfig();
                sender.sendMessage("§aMute kaldırıldı.");
                break;

            case "kick":
                if (target.isOnline()) {
                    ((Player) target).kickPlayer("§cAtıldınız!\n§fSebep: " + reason);
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
                    p.kickPlayer("§cIP Yasaklandı!");
                }
                break;
        }

        return true;
    }

    @EventHandler
    public void onLogin(AsyncPlayerPreLoginEvent e) {

        UUID uuid = e.getUniqueId();
        String ip = e.getAddress().getHostAddress().replace(".", "_");

        if (plugin.getConfig().contains("punishments.bans." + uuid)) {
            String reason = plugin.getConfig().getString("punishments.bans." + uuid);
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    "§cYasaklısınız!\n§fSebep: " + reason);
        }

        if (plugin.getConfig().contains("punishments.ipbans." + ip)) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    "§cIP Yasaklı!");
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {

        if (plugin.getConfig().contains("punishments.mutes." + e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            String reason = plugin.getConfig().getString("punishments.mutes." + e.getPlayer().getUniqueId());
            e.getPlayer().sendMessage("§cSusturuldunuz!\n§fSebep: " + reason);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!plugin.isLogged(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventory(InventoryClickEvent e) {

        if (!(e.getWhoClicked() instanceof Player p)) return;

        if (!plugin.isLogged(p.getUniqueId())) {
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
