package com.loginx;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;

public class LoginListener implements Listener {

    private final LoginStorage storage;

    public LoginListener(LoginStorage storage) {
        this.storage = storage;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {

        Player player = e.getPlayer();
        String ip = player.getAddress().getAddress().getHostAddress();

        storage.addLogin(player.getName(), ip);

        String last = storage.getLogins(player.getName())
                .get(storage.getLoginCount(player.getName()) - 1);

        String msg =
                "§8[§cLoginX§8]\n" +
                "§7Oyuncu: §f" + player.getName() + "\n" +
                "§7Tarih: §f" + last;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("loginx.log")) {
                p.sendMessage(msg);
            }
        }
    }
}
