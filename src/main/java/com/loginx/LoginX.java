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

    private FileConfiguration config;
    private File configFile;
    private HashMap<UUID, String> passwords = new HashMap<>();
    private HashMap<UUID, Boolean> loggedIn = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        this.getCommand("register").setExecutor(new RegisterCommand());
        this.getCommand("login").setExecutor(new LoginCommand());
        this.getCommand("logingoster").setExecutor(new LoginGosterCommand());
        this.getCommand("incele").setExecutor(new InceleCommand());
        loadConfig();
        getLogger().info("HitX aktif edildi!");
    }

    private void loadConfig() {
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public boolean isLoggedIn(Player player) {
        return loggedIn.getOrDefault(player.getUniqueId(), false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        loggedIn.put(p.getUniqueId(), false);
        p.sendTitle(ChatColor.LIGHT_PURPLE + "Kayıt Ol /register şifre şifre",
                    ChatColor.GRAY + "Sunucuya giriş için şifre belirleyin",
                    10, 70, 20);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!isLoggedIn(p)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Önce /login veya /register ile giriş yapmalısınız!");
        }
    }

    // --- COMMANDS ---

    class RegisterCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player player)) return true;
            if (args.length != 2) {
                player.sendMessage(ChatColor.RED + "Kullanım: /register <şifre> <şifre>");
                return true;
            }
            String şifre1 = args[0];
            String şifre2 = args[1];
            if (!şifre1.equals(şifre2)) {
                player.sendMessage(ChatColor.RED + "Şifreler uyuşmuyor!");
                return true;
            }
            passwords.put(player.getUniqueId(), şifre1);
            loggedIn.put(player.getUniqueId(), true);
            player.sendTitle(ChatColor.LIGHT_PURPLE + "Başarıyla kayıt oldunuz!", "",
                    10, 70, 20);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            savePasswords();
            return true;
        }
    }

    class LoginCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player player)) return true;
            if (args.length != 1) {
                player.sendMessage(ChatColor.RED + "Kullanım: /login <şifre>");
                return true;
            }
            String şifre = args[0];
            if (!passwords.containsKey(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Önce /register ile kayıt olun!");
                return true;
            }
            if (!passwords.get(player.getUniqueId()).equals(şifre)) {
                player.sendMessage(ChatColor.RED + "Şifre yanlış!");
                return true;
            }
            loggedIn.put(player.getUniqueId(), true);
            player.sendTitle(ChatColor.LIGHT_PURPLE + "Başarıyla giriş yaptınız!", "",
                    10, 70, 20);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (p.hasPermission("hitx.view")) {
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&d&lTarih &f| &d&l" + player.getName() + " &f| &d&lLogin: " + şifre));
                }
            });
            return true;
        }
    }

    class LoginGosterCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!sender.hasPermission("hitx.view")) return true;
            passwords.forEach((uuid, şifre) -> {
                Player p = Bukkit.getPlayer(uuid);
                String name = (p != null) ? p.getName() : "OfflinePlayer";
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&d&lTarih &f| &d&l" + name + " &f| &d&lLogin: " + şifre));
            });
            return true;
        }
    }

    class InceleCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player player)) return true;
            if (args.length != 1) {
                player.sendMessage(ChatColor.RED + "Kullanım: /incele <oyuncu>");
                return true;
            }
            Player hedef = Bukkit.getPlayer(args[0]);
            if (hedef == null) {
                player.sendMessage(ChatColor.RED + "Oyuncu bulunamadı!");
                return true;
            }
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Oyuncu " + hedef.getName() +
                    " hakkında bilgi:");
            // İleride daha detaylı bilgi ekleyebilirsiniz
            return true;
        }
    }

    private void savePasswords() {
        try {
            YamlConfiguration yml = new YamlConfiguration();
            passwords.forEach((uuid, pass) -> yml.set(uuid.toString(), pass));
            yml.save(new File(getDataFolder(), "passwords.yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
                }
