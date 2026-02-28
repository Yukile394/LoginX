package com.loginx;

import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.entity.Player;

import java.util.List;

public class LoginMenu {

    public static void open(Player viewer, String target, LoginStorage storage) {

        Inventory inv = Bukkit.createInventory(null, 27, "§8LoginX Panel");

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        OfflinePlayer offline = Bukkit.getOfflinePlayer(target);
        meta.setOwningPlayer(offline);
        meta.setDisplayName("§c" + target);

        List<String> logs = storage.getLogins(target);
        meta.setLore(logs);

        head.setItemMeta(meta);

        inv.setItem(13, head);
        viewer.openInventory(inv);
    }
}
