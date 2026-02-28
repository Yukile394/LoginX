package com.loginx;

import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;

public class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals("§8LoginX Panel")) {
            e.setCancelled(true);
        }
    }
}
