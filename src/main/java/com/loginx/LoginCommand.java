package com.loginx;

import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class LoginCommand implements CommandExecutor {

    private final LoginStorage storage;

    public LoginCommand(LoginStorage storage) {
        this.storage = storage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.hasPermission("loginx.log")) {
            sender.sendMessage("§cYetkin yok.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cBu komut sadece oyuncular için.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("§cKullanım: /logingoster <oyuncu>");
            return true;
        }

        LoginMenu.open(player, args[0], storage);
        return true;
    }
}
