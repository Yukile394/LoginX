package com.loginx;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class LoginStorage {

    private final Plugin plugin;
    private File file;
    private YamlConfiguration config;

    public LoginStorage(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "logins.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    public void addLogin(String player, String ip) {

        List<String> logs = config.getStringList(player + ".logins");

        String date = new SimpleDateFormat("dd.MM.yyyy - HH:mm").format(new Date());
        logs.add(date + " | IP_HASH: " + hash(ip));

        config.set(player + ".logins", logs);
        save();
    }

    public List<String> getLogins(String player) {
        return config.getStringList(player + ".logins");
    }

    public int getLoginCount(String player) {
        return getLogins(player).size();
    }

    private String hash(String ip) {
        return Integer.toHexString(ip.hashCode());
    }
}
