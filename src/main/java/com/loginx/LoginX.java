package com.loginx;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

public class HitX extends JavaPlugin implements Listener {

    private File configFile;
    private FileConfiguration config;
    private final HashMap<UUID, String> logins = new HashMap<>();
    private final HashMap<UUID, Boolean> loggedIn = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configFile = new File(getDataFolder(), "config.yml");
        config = YamlConfiguration.loadConfiguration(configFile);

        Bukkit.getPluginManager().registerEvents(this, this);
        this.getCommand("register").setExecutor(new RegisterCommand());
        this.getCommand("login").setExecutor(new LoginCommand());
        this.getCommand("logingoster").setExecutor(new LoginGosterCommand());

        getLogger().info("HitX aktif edildi!");
    }

    @Override
    public void onDisable() {
        getLogger().info("HitX kapatıldı!");
    }

    // Oyuncu join
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        loggedIn.put(p.getUniqueId(), false);

        p.sendTitle(ChatColor.LIGHT_PURPLE + "Kayıt Ol",
                ChatColor.translateAlternateColorCodes('&', "/register şifre şifre"),
                10, 70, 20);
        p.sendMessage(ChatColor.LIGHT_PURPLE + "Sunucuya giriş yaptın. Lütfen kaydol veya giriş yap!");
    }

    // Hareket engelleme login olmadan
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!loggedIn.getOrDefault(p.getUniqueId(), false)) {
            e.setCancelled(true);
        }
    }

    // /register komutu
    private class RegisterCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player p)) return false;
            if (args.length != 2) {
                p.sendMessage(ChatColor.RED + "Kullanım: /register <şifre> <şifre>");
                return false;
            }
            String sifre1 = args[0];
            String sifre2 = args[1];
            if (!sifre1.equals(sifre2)) {
                p.sendMessage(ChatColor.RED + "Şifreler uyuşmuyor!");
                return false;
            }
            logins.put(p.getUniqueId(), sifre1);
            loggedIn.put(p.getUniqueId(), true);
            p.sendTitle(ChatColor.LIGHT_PURPLE + "Başarıyla kayıt oldun!", "", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            return true;
        }
    }

    // /login komutu
    private class LoginCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player p)) return false;
            if (args.length != 1) {
                p.sendMessage(ChatColor.RED + "Kullanım: /login <şifre>");
                return false;
            }
            String sifre = args[0];
            String kayıtlı = logins.get(p.getUniqueId());
            if (kayıtlı == null) {
                p.sendMessage(ChatColor.RED + "Önce /register ile kaydol!");
                return false;
            }
            if (!kayıtlı.equals(sifre)) {
                p.sendMessage(ChatColor.RED + "Şifre yanlış!");
                return false;
            }
            loggedIn.put(p.getUniqueId(), true);
            p.sendTitle(ChatColor.GREEN + "Başarıyla giriş yaptın!", "", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            return true;
        }
    }

    // /logingoster komutu (yetkililere özel)
    private class LoginGosterCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!sender.hasPermission("hitx.view")) {
                sender.sendMessage(ChatColor.RED + "Yetkin yok!");
                return false;
            }
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "=== LoginX Logları ===");
            for (UUID id : logins.keySet()) {
                Player p = Bukkit.getPlayer(id);
                String name = (p != null) ? p.getName() : "Offline";
                String sifre = logins.get(id);
                sender.sendMessage(ChatColor.LIGHT_PURPLE + "Oyuncu: " + name + " | Şifre: " + sifre);
            }
            return true;
        }
    }
}
