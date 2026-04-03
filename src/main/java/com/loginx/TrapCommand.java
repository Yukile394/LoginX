package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class TrapCommand implements CommandExecutor {

    private final TrapManager manager;
    private final TrapGUI gui;
    private final EconomyBridge eco;
    private final TrapHologramManager hologramManager;

    // Oyuncunun aktif trap ID'si (GUI islemleri icin)
    private final HashMap<UUID, Integer> activeTrap = new HashMap<>();

    public TrapCommand(TrapManager manager, TrapGUI gui,
                       EconomyBridge eco, TrapHologramManager hologramManager) {
        this.manager = manager;
        this.gui = gui;
        this.eco = eco;
        this.hologramManager = hologramManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Sadece oyuncular kullanabilir!");
            return true;
        }

        // /trap -> marketi ac
        if (args.length == 0) {
            gui.openMarket(p);
            return true;
        }

        switch (args[0].toLowerCase()) {

            // ── YETKİLİ ──────────────────────────────────────
            case "yap" -> {
                if (!p.hasPermission("loginx.trap.admin")) { noPerms(p); return true; }
                if (!manager.hasBothSelections(p.getUniqueId())) {
                    p.sendMessage(c("&cOnce balta ile 2 kose sec! (Sol tik = Kose1, Sag tik = Kose2)"));
                    return true;
                }
                double price = 0;
                if (args.length >= 2) {
                    try { price = Double.parseDouble(args[1]); }
                    catch (NumberFormatException e) { p.sendMessage(c("&cGecersiz fiyat!")); return true; }
                }
                Location pos1 = manager.getPos1(p.getUniqueId());
                Location pos2 = manager.getPos2(p.getUniqueId());
                TrapData td = manager.createTrap(p.getUniqueId(), p.getName(), pos1, pos2, price);
                manager.clearSelection(p.getUniqueId());
                activeTrap.put(p.getUniqueId(), td.getId());
                p.sendMessage(c("&a&lTrap #" + td.getId() + " olusturuldu!"));
                p.sendMessage(c("&7Boyut: &f" + td.getSizeString()
                        + " &7| Fiyat: &6$" + String.format("%.0f", price)));
            }

            case "sil" -> {
                if (!p.hasPermission("loginx.trap.admin")) { noPerms(p); return true; }
                if (args.length < 2) { p.sendMessage(c("&cKulanim: /trap sil <id>")); return true; }
                try {
                    int id = Integer.parseInt(args[1]);
                    if (manager.deleteTrap(id)) {
                        hologramManager.removeHologram(id);
                        p.sendMessage(c("&aTrap #" + id + " silindi."));
                    } else {
                        p.sendMessage(c("&cBu ID'de trap bulunamadi!"));
                    }
                } catch (NumberFormatException e) { p.sendMessage(c("&cGecersiz ID!")); }
            }

            case "hologram" -> {
                if (!p.hasPermission("loginx.trap.admin")) { noPerms(p); return true; }
                TrapData td = manager.getTrapAt(p.getLocation());
                if (td == null) {
                    p.sendMessage(c("&cBir trapin icinde ya da onunde durmalısin!"));
                    return true;
                }
                hologramManager.spawnTrapHologram(td, p.getLocation().add(0, 3, 0));
                p.sendMessage(c("&aTrap #" + td.getId() + " icin hologram olusturuldu!"));
            }

            case "hologramsil" -> {
                if (!p.hasPermission("loginx.trap.admin")) { noPerms(p); return true; }
                hologramManager.clearAll();
                p.sendMessage(c("&cTum trap hologramlari silindi."));
            }

            // ── OYUNCU ───────────────────────────────────────
            case "menu" -> {
                TrapData td = manager.getTrapAt(p.getLocation());
                if (td == null || !td.isMember(p.getUniqueId())) {
                    p.sendMessage(c("&cBir trapin icinde olmalisiniz!"));
                    return true;
                }
                activeTrap.put(p.getUniqueId(), td.getId());
                gui.openTrapMenu(p, td);
            }

            case "sat" -> {
                if (args.length < 2) { p.sendMessage(c("&cKulanim: /trap sat <fiyat>")); return true; }
                double price;
                try { price = Double.parseDouble(args[1]); }
                catch (NumberFormatException e) { p.sendMessage(c("&cGecersiz fiyat!")); return true; }
                TrapData td = getOwnedTrap(p);
                if (td == null) { p.sendMessage(c("&cSahibi oldugunuz bir trapin icinde olmalisiniz!")); return true; }
                manager.listOnMarket(td.getId(), price);
                p.sendMessage(c("&aTrap #" + td.getId() + " &6$"
                        + String.format("%.0f", price) + " &afiyatiyla satisa cikarildi!"));
            }

            case "gericek" -> {
                TrapData td = getOwnedTrap(p);
                if (td == null) { p.sendMessage(c("&cSahibi oldugunuz bir trapin icinde olmalisiniz!")); return true; }
                manager.removeFromMarket(td.getId());
                p.sendMessage(c("&aTrap #" + td.getId() + " satistan cekildi."));
            }

            case "parayatir" -> {
                if (args.length < 2) { p.sendMessage(c("&cKulanim: /trap parayatir <miktar>")); return true; }
                double amount;
                try { amount = Double.parseDouble(args[1]); }
                catch (NumberFormatException e) { p.sendMessage(c("&cGecersiz miktar!")); return true; }
                TrapData td = manager.getTrapAt(p.getLocation());
                if (td == null || !td.isMember(p.getUniqueId())) {
                    p.sendMessage(c("&cBu trapin uyesi degilsiniz ya da bir trapin icinde degilsiniz!"));
                    return true;
                }
                if (!eco.withdraw(p, amount)) {
                    p.sendMessage(c("&cYetersiz bakiye! Bakiye: &6" + eco.format(eco.getBalance(p))));
                    return true;
                }
                manager.deposit(td.getId(), amount);
                p.sendMessage(c("&a&6$" + String.format("%.2f", amount) + " &atrap bankasina yatirildi."));
                p.sendMessage(c("&7Yeni banka bakiyesi: &a$" + String.format("%.2f", td.getBank())));
            }

            case "paracek" -> {
                if (args.length < 2) { p.sendMessage(c("&cKulanim: /trap paracek <miktar>")); return true; }
                double amount;
                try { amount = Double.parseDouble(args[1]); }
                catch (NumberFormatException e) { p.sendMessage(c("&cGecersiz miktar!")); return true; }
                TrapData td = manager.getTrapAt(p.getLocation());
                if (td == null) { p.sendMessage(c("&cBir trapin icinde degilsiniz!")); return true; }
                if (!td.getOwner().equals(p.getUniqueId())) {
                    p.sendMessage(c("&cPara cekme sadece trap sahibine ozgudur!"));
                    return true;
                }
                if (!manager.withdraw(td.getId(), amount)) {
                    p.sendMessage(c("&cTrap bankasinda yeterli para yok! Banka: &6$"
                            + String.format("%.2f", td.getBank())));
                    return true;
                }
                eco.deposit(p, amount);
                p.sendMessage(c("&a&6$" + String.format("%.2f", amount) + " &atrap bankasından cekildi."));
                p.sendMessage(c("&7Yeni banka bakiyesi: &a$" + String.format("%.2f", td.getBank())));
            }

            case "davet" -> {
                if (args.length < 2) { p.sendMessage(c("&cKulanim: /trap davet <oyuncu>")); return true; }
                TrapData td = getOwnedTrap(p);
                if (td == null) { p.sendMessage(c("&cSahibi oldugunuz bir trapin icinde olmalisiniz!")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { p.sendMessage(c("&cOyuncu cevrimici degil!")); return true; }
                if (manager.invite(td.getId(), target.getUniqueId())) {
                    p.sendMessage(c("&a" + target.getName() + " Trap #" + td.getId() + "'e eklendi!"));
                    target.sendMessage(c("&d" + p.getName() + " &asizi &dTrap #" + td.getId() + "&a'ya davet etti!"));
                } else {
                    p.sendMessage(c("&cBu oyuncu zaten uye!"));
                }
            }

            case "kick" -> {
                if (args.length < 2) { p.sendMessage(c("&cKulanim: /trap kick <oyuncu>")); return true; }
                TrapData td = getOwnedTrap(p);
                if (td == null) { p.sendMessage(c("&cSahibi oldugunuz bir trapin icinde olmalisiniz!")); return true; }
                // Cevrimdisi oyuncu destegi
                UUID targetId = null;
                Player online = Bukkit.getPlayer(args[1]);
                if (online != null) {
                    targetId = online.getUniqueId();
                } else {
                    for (UUID uid : td.getMembers()) {
                        String name = Bukkit.getOfflinePlayer(uid).getName();
                        if (args[1].equalsIgnoreCase(name)) { targetId = uid; break; }
                    }
                }
                if (targetId == null) { p.sendMessage(c("&cBu oyuncu trapin uyesi degil!")); return true; }
                if (manager.kick(td.getId(), targetId)) {
                    p.sendMessage(c("&c" + args[1] + " Trap #" + td.getId() + "'den atiildi."));
                    if (online != null) online.sendMessage(c("&cTrap #" + td.getId() + "'den cikarildiniz."));
                } else {
                    p.sendMessage(c("&cUye bulunamadi!"));
                }
            }

            case "banka" -> {
                TrapData td = manager.getTrapAt(p.getLocation());
                if (td == null || !td.isMember(p.getUniqueId())) {
                    p.sendMessage(c("&cBir trapin icinde degilsiniz ya da uye degilsiniz!"));
                    return true;
                }
                p.sendMessage(c("&5&m                    "));
                p.sendMessage(c("&d&lTrap #" + td.getId() + " Banka"));
                p.sendMessage(c("&7Sahip: &f" + td.getOwnerName()));
                p.sendMessage(c("&7Banka: &a$" + String.format("%.2f", td.getBank())));
                p.sendMessage(c("&5&m                    "));
            }

            case "bilgi" -> {
                TrapData td;
                if (args.length >= 2) {
                    try { td = manager.getTrap(Integer.parseInt(args[1])); }
                    catch (NumberFormatException e) { p.sendMessage(c("&cGecersiz ID!")); return true; }
                } else {
                    td = manager.getTrapAt(p.getLocation());
                }
                if (td == null) { p.sendMessage(c("&cTrap bulunamadi!")); return true; }
                sendInfo(p, td);
            }

            case "tp" -> {
                TrapData td;
                if (args.length >= 2) {
                    try { td = manager.getTrap(Integer.parseInt(args[1])); }
                    catch (NumberFormatException e) { p.sendMessage(c("&cGecersiz ID!")); return true; }
                } else {
                    List<TrapData> mine = manager.getPlayerTraps(p.getUniqueId());
                    td = mine.isEmpty() ? null : mine.get(0);
                }
                if (td == null) { p.sendMessage(c("&cTrap bulunamadi!")); return true; }
                Location tpLoc = td.getSpawnLocation() != null
                        ? td.getSpawnLocation()
                        : td.getPos1().clone().add(0.5, 1, 0.5);
                p.teleport(tpLoc);
                p.sendMessage(c("&aTrap #" + td.getId() + " konumuna isinlandiniz!"));
            }

            case "liste" -> {
                List<TrapData> myTraps = manager.getPlayerTraps(p.getUniqueId());
                if (myTraps.isEmpty()) { p.sendMessage(c("&cHic trapiniz yok.")); return true; }
                p.sendMessage(c("&d&lTraplariniz (&f" + myTraps.size() + "&d):"));
                for (TrapData td : myTraps) {
                    p.sendMessage(c("  &5#" + td.getId()
                            + " &7Boyut: &f" + td.getSizeString()
                            + " &7Banka: &a$" + String.format("%.0f", td.getBank())
                            + (td.isForSale() ? " &c[SATISTA]" : "")));
                }
            }

            default -> sendHelp(p);
        }
        return true;
    }

    // ── Yardimci ─────────────────────────────────────────────
    private TrapData getOwnedTrap(Player p) {
        TrapData td = manager.getTrapAt(p.getLocation());
        if (td != null && td.getOwner().equals(p.getUniqueId())) return td;
        Integer active = activeTrap.get(p.getUniqueId());
        if (active != null) {
            TrapData d = manager.getTrap(active);
            if (d != null && d.getOwner().equals(p.getUniqueId())) return d;
        }
        return null;
    }

    private void sendInfo(Player p, TrapData td) {
        p.sendMessage(c("&5&m                        "));
        p.sendMessage(c("  &d&lTRAP &5#" + td.getId()));
        p.sendMessage(c("  &7Sahip: &f" + td.getOwnerName()));
        p.sendMessage(c("  &7Banka: &a$" + String.format("%.2f", td.getBank())));
        p.sendMessage(c("  &7Uye: &f" + td.getMemberCount()));
        p.sendMessage(c("  &7Boyut: &f" + td.getSizeString()));
        p.sendMessage(c("  &7Satilik: " + (td.isForSale()
                ? "&aEvet &7($" + String.format("%.0f", td.getSalePrice()) + ")"
                : "&cHayir")));
        p.sendMessage(c("&5&m                        "));
    }

    private void sendHelp(Player p) {
        p.sendMessage(c("&5&m                        "));
        p.sendMessage(c("  &d&lTRAP KOMUTLARI"));
        p.sendMessage(c("  &5/trap &7\u2192 Market"));
        p.sendMessage(c("  &5/trap menu &7\u2192 Trap menusu"));
        p.sendMessage(c("  &5/trap sat &6<fiyat>"));
        p.sendMessage(c("  &5/trap gericek"));
        p.sendMessage(c("  &5/trap parayatir &6<miktar>"));
        p.sendMessage(c("  &5/trap paracek &6<miktar>"));
        p.sendMessage(c("  &5/trap davet &6<oyuncu>"));
        p.sendMessage(c("  &5/trap kick &6<oyuncu>"));
        p.sendMessage(c("  &5/trap banka"));
        p.sendMessage(c("  &5/trap bilgi &7[id]"));
        p.sendMessage(c("  &5/trap tp &7[id]"));
        p.sendMessage(c("  &5/trap liste"));
        if (p.hasPermission("loginx.trap.admin")) {
            p.sendMessage(c("  &c/trap yap &7[fiyat] &8(Yetkili)"));
            p.sendMessage(c("  &c/trap sil &6<id> &8(Yetkili)"));
            p.sendMessage(c("  &c/trap hologram &8(Yetkili)"));
            p.sendMessage(c("  &c/trap hologramsil &8(Yetkili)"));
        }
        p.sendMessage(c("&5&m                        "));
    }

    private void noPerms(Player p) {
        p.sendMessage(c("&cBu komut icin yetkiniz yok!"));
    }

    private String c(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public HashMap<UUID, Integer> getActiveTrap() { return activeTrap; }
                    }
