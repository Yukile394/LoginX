package com.hitx;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.UUID;

public class HitX extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private final HashMap<UUID, String> registered = new HashMap<>();
    private final HashMap<UUID, Boolean> loggedIn = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = getConfig();
        getServer().getPluginManager().registerEvents(this, this);

        getCommand("register").setExecutor(new RegisterCommand());
        getCommand("login").setExecutor(new LoginCommand());
        getCommand("logingoster").setExecutor(new LoginGosterCommand());
        getCommand("incele").setExecutor(new InceleCommand());
    }

    // Oyuncu Join
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if(!registered.containsKey(p.getUniqueId())){
            loggedIn.put(p.getUniqueId(), false);
            p.sendTitle(config.getString("title.register"), "", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.valueOf(config.getString("sounds.register_success")), 1, 1);
        } else {
            loggedIn.put(p.getUniqueId(), false);
            p.sendTitle(config.getString("title.login"), "", 10, 70, 20);
        }
    }

    // Chat kısıtlama
    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if(config.getBoolean("force_login") && !loggedIn.getOrDefault(e.getPlayer().getUniqueId(), false)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "Önce giriş yapmalısın!");
        }
    }

    // Hareket kısıtlama
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if(config.getBoolean("force_login") && !loggedIn.getOrDefault(e.getPlayer().getUniqueId(), false)) {
            e.setCancelled(true);
        }
    }

    // Envanter kısıtlama
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if(config.getBoolean("force_login") && !loggedIn.getOrDefault(((Player)e.getWhoClicked()).getUniqueId(), false)) {
            e.setCancelled(true);
        }
    }

    // /register komutu
    public class RegisterCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if(!(sender instanceof Player p)) return false;

            if(args.length != 2){
                p.sendMessage(ChatColor.RED + "Kullanım: /register <şifre> <şifre>");
                return true;
            }

            if(!args[0].equals(args[1])){
                p.sendMessage(ChatColor.RED + "Şifreler eşleşmiyor!");
                return true;
            }

            registered.put(p.getUniqueId(), args[0]);
            loggedIn.put(p.getUniqueId(), true);
            p.sendTitle(config.getString("title.register_success"), "", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.valueOf(config.getString("sounds.register_success")), 1, 1);
            return true;
        }
    }

    // /login komutu
    public class LoginCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if(!(sender instanceof Player p)) return false;

            if(args.length != 1){
                p.sendMessage(ChatColor.RED + "Kullanım: /login <şifre>");
                return true;
            }

            String pw = registered.get(p.getUniqueId());
            if(pw == null){
                p.sendMessage(ChatColor.RED + "Önce kayıt olmalısın!");
                return true;
            }

            if(!pw.equals(args[0])){
                p.sendMessage(ChatColor.RED + "Şifre yanlış!");
                return true;
            }

            loggedIn.put(p.getUniqueId(), true);
            p.sendTitle(config.getString("title.login_success"), "", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.valueOf(config.getString("sounds.login_success")), 1, 1);
            return true;
        }
    }

    // /logingoster komutu
    public class LoginGosterCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if(!sender.hasPermission("hitx.logingoster")){
                sender.sendMessage(ChatColor.RED + "Yetkin yok!");
                return true;
            }

            sender.sendMessage(ChatColor.LIGHT_PURPLE + "---- HitX Login List ----");
            for(UUID uuid : registered.keySet()){
                Player p = Bukkit.getPlayer(uuid);
                String name = p != null ? p.getName() : "Offline";
                sender.sendMessage(ChatColor.AQUA + name + " | " + ChatColor.LIGHT_PURPLE + registered.get(uuid));
            }
            return true;
        }
    }

    // /incele komutu
    public class InceleCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if(!sender.hasPermission("hitx.incele")) return true;
            if(args.length != 1){
                sender.sendMessage(ChatColor.RED + "Kullanım: /incele <oyuncu>");
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "--- Bilgi ---");
            sender.sendMessage(ChatColor.AQUA + "İsim: " + ChatColor.LIGHT_PURPLE + target.getName());
            sender.sendMessage(ChatColor.AQUA + "UUID: " + ChatColor.LIGHT_PURPLE + target.getUniqueId());
            sender.sendMessage(ChatColor.AQUA + "Çevrimiçi: " + ChatColor.LIGHT_PURPLE + target.isOnline());
            return true;
        }
    }
}
