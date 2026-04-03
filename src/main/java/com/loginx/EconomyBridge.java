package com.loginx.trap;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault Economy ile iletişimi sağlayan köprü sınıfı.
 * Vault yüklü değilse tüm işlemler başarısız döner.
 */
public class EconomyBridge {

    private Economy economy = null;
    private boolean enabled = false;

    public EconomyBridge(org.bukkit.plugin.Plugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("[Trap] Vault bulunamadı! Para sistemi devre dışı.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("[Trap] Economy servisi bulunamadı!");
            return;
        }
        economy = rsp.getProvider();
        enabled = true;
        plugin.getLogger().info("[Trap] Vault Economy bağlantısı başarılı.");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getBalance(Player player) {
        if (!enabled) return 0;
        return economy.getBalance(player);
    }

    public boolean has(Player player, double amount) {
        if (!enabled) return false;
        return economy.has(player, amount);
    }

    /** Oyuncudan para çeker. Başarılıysa true döner. */
    public boolean withdraw(Player player, double amount) {
        if (!enabled) return false;
        if (!economy.has(player, amount)) return false;
        economy.withdrawPlayer(player, amount);
        return true;
    }

    /** Oyuncuya para verir. */
    public void deposit(Player player, double amount) {
        if (!enabled) return;
        economy.depositPlayer(player, amount);
    }

    /** Formatlanmış para metni (örn: "$1,500.00") */
    public String format(double amount) {
        if (!enabled) return "$" + String.format("%.2f", amount);
        return economy.format(amount);
    }
}

