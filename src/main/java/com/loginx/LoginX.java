package com.loginx;

import org.bukkit.plugin.java.JavaPlugin;

public class LoginX extends JavaPlugin {

    private static LoginX instance;
    private LoginStorage storage;

    @Override
    public void onEnable() {
        instance = this;

        storage = new LoginStorage(this);
        storage.load();

        getServer().getPluginManager().registerEvents(new LoginListener(storage), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getCommand("logingoster").setExecutor(new LoginCommand(storage));

        getLogger().info("LoginX aktif edildi!");
    }

    @Override
    public void onDisable() {
        storage.save();
    }

    public static LoginX getInstance() {
        return instance;
    }
}
