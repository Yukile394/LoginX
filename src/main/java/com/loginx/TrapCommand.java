package com.loginx.trap;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * /trap komutu ve tüm alt komutları:
 *
 *  /trap                      → market GUI'yi açar
 *  /trap yap [fiyat]          → (YETKİLİ) seçili alanı trap yapar
 *  /trap sil [id]             → (YETKİLİ) trap siler
 *  /trap sat <fiyat>          → kendi trapini satışa çıkarır
 *  /trap gericek              → satıştan çeker
 *  /trap parayatir <miktar>   → trap bankasına para yatırır
 *  /trap paracek <miktar>     → trap bankasından çeker (sadece sahip)
 *  /trap davet <oyuncu>       → üye davet eder
 *  /trap kick <oyuncu>        → üye atar
 *  /trap banka                → banka bakiyesini gösterir
 *  /trap bilgi [id]           → trap bilgisini gösterir
 *  /trap tp [id]              → trapa ışınlanır
 *  /trap liste                → kendi traplerini listeler
 *  /trap hologram [#1-#5]     → (YETKİLİ) trap önüne hologram koyar
 *  /trap hologramsil          → (YETKİLİ) trap hologramlarını siler
 */
public class TrapCommand implements CommandExecutor {

    private final TrapManager manager;
    private final TrapGUI gui;
    private final EconomyBridge eco;
    private final TrapHologramManager hologramManager;

    // Hangi trap üzerinde işlem yapıldığını takip etmek için (GUI işlemleri)
    // UUID -> TrapData (aktif seçili trap)
    private final HashMap<UUID, Integer> playerActiveTrap = new HashMap<>();

    public TrapCommand(TrapManager manager, TrapGUI gui, EconomyBridge eco,
                       TrapHologramManager hologramManager) {
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

        // /trap → marketi aç
        if (args.length == 0) {
            gui.openMarket(p);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {

            // ─────────────────────────────────────────────────
            //  YETKİLİ KOMUTLAR
            // ─────────────────────────────────────────────────

            case "yap" -> {
                if (!p.hasPermission("loginx.trap.admin")) {
                    p.sendMessage(color("&cBu komutu kullanmak için yetkiniz yok!"));
                    return true;
                }
                if (!manager.hasBothSelections(p.getUniqueId())) {
                    p.sendMessage(color("&cÖnce balta ile 2 köşe seçmelisin! (Sol tık = 1. köşe, Sağ tık = 2. köşe)"));
                    return true;
                }
                double price = 0;
                if (args.length >= 2) {
                    try { price = Double.parseDouble(args[1]); }
                    catch (NumberFormatException e) {
                        p.sendMessage(color("&cGeçersiz fiyat!"));
                        return true;
                    }
                }
                Location pos1 = manager.getPos1(p.getUniqueId());
                Location pos2 = manager.getPos2(p.getUniqueId());
                TrapData td = manager.createTrap(p.getUniqueId(), p.getName(), pos1, pos2, price);
                manager.clearSelection(p.getUniqueId());
                p.sendMessage(color("&a&lTrap #" + td.getId() + " başarıyla oluşturuldu!"));
                p.sendMessage(color("&7Boyut: &f" + td.getSizeString() + " &7| Fiyat: &6$" + String.format("%.0f", price)));
                playerActiveTrap.put(p.getUniqueId(), td.getId());
            }

            case "sil" -> {
                if (!p.hasPermission("loginx.trap.admin")) {
                    p.sendMessage(color("&cYetkiniz yok!"));
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage(color("&cKullanım: /trap sil <id>"));
                    return true;
                }
                try {
                    int id = Integer.parseInt(args[1]);
                    if (manager.deleteTrap(id)) {
                        hologramManager.removeHologram(id);
                        p.sendMessage(color("&aTrap #" + id + " silindi."));
                    } else {
                        p.sendMessage(color("&cBu ID'de trap bulunamadı!"));
                    }
                } catch (NumberFormatException e) {
                    p.sendMessage(color("&cGeçersiz ID!"));
                }
            }

            case "hologram" -> {
                if (!p.hasPermission("loginx.trap.admin")) {
                    p.sendMessage(color("&cYetkiniz yok!"));
                    return true;
                }
                // Bakılan trap
                TrapData td = manager.getTrapAt(p.getLocation());
                if (td == null) td = manager.getTrapAt(p.getTargetBlock(null, 10).getLocation());
                if (td == null) {
                    p.sendMessage(color("&cBir trapın içinde ya da önünde durmalısın!"));
                    return true;
                }
                hologramManager.spawnTrapHologram(td, p.getLocation().add(0, 3, 0));
                p.sendMessage(color("&aTrap #" + td.getId() + " için hologram oluşturuldu!"));
            }

            case "hologramsil" -> {
                if (!p.hasPermission("loginx.trap.admin")) {
                    p.sendMessage(color("&cYetkiniz yok!"));
                    return true;
                }
                hologramManager.clearAll();
                p.sendMessage(color("&cTüm trap hologramları silindi."));
            }

            // ─────────────────────────────────────────────────
            //  OYUNCU KOMUTLARI
            // ─────────────────────────────────────────────────

            case "sat" -> {
                if (args.length < 2) {
                    p.sendMessage(color("&cKullanım: /trap sat <fiyat>"));
                    return true;
                }
                double price;
                try { price = Double.parseDouble(args[1]); }
                catch (NumberFormatException e) {
                    p.sendMessage(color("&cGeçersiz fiyat!"));
                    return true;
                }
                TrapData owned = getPlayerOwnedTrap(p);
                if (owned == null) {
                    p.sendMessage(color("&cSahibi olduğunuz bir trap içinde durmanız gerekiyor!"));
                    return true;
                }
                manager.listOnMarket(owned.getId(), price);
                p.sendMessage(color("&aTrap #" + owned.getId() + " &6$" + String.format("%.0f", price) + " &afiyatıyla satışa çıkarıldı!"));
            }

            case "gericek" -> {
                TrapData owned = getPlayerOwnedTrap(p);
                if (owned == null) {
                    p.sendMessage(color("&cSahibi olduğunuz bir trap içinde durmanız gerekiyor!"));
                    return true;
                }
                manager.removeFromMarket(owned.getId());
                p.sendMessage(color("&aTrap #" + owned.getId() + " satıştan çekildi."));
            }

            case "parayatir" -> {
                if (args.length < 2) {
                    p.sendMessage(color("&cKullanım: /trap parayatir <miktar>"));
                    return true;
                }
                double amount;
                try { amount = Double.parseDouble(args[1]); }
                catch (NumberFormatException e) {
                    p.sendMessage(color("&cGeçersiz miktar!"));
                    return true;
                }
                TrapData td = manager.getTrapAt(p.getLocation());
                if (td == null || !td.isMember(p.getUniqueId())) {
                    p.sendMessage(color("&cBu trapın üyesi değilsiniz ya da bir trapın içinde değilsiniz!"));
                    return true;
                }
                if (!eco.withdraw(p, amount)) {
                    p.sendMessage(color("&cYeterli paran yok! Bakiye: &6" + eco.format(eco.getBalance(p))));
                    return true;
                }
                manager.deposit(td.getId(), amount);
                p.sendMessage(color("&a&6$" + String.format("%.2f", amount) + " &atrap bankasına yatırıldı."));
                p.sendMessage(color("&7Yeni banka bakiyesi: &a$" + String.format("%.2f", td.getBank())));
            }

            case "paracek" -> {
                if (args.length < 2) {
                    p.sendMessage(color("&cKullanım: /trap paracek <miktar>"));
                    return true;
                }
                double amount;
                try { amount = Double.parseDouble(args[1]); }
                catch (NumberFormatException e) {
                    p.sendMessage(color("&cGeçersiz miktar!"));
                    return true;
                }
                TrapData td = manager.getTrapAt(p.getLocation());
                if (td == null) {
                    p.sendMessage(color("&cBir trapın içinde değilsiniz!"));
                    return true;
                }
                if (!td.getOwner().equals(p.getUniqueId())) {
                    p.sendMessage(color("&cPara çekme sadece trap sahibine özeldir!"));
                    return true;
                }
                if (!manager.withdraw(td.getId(), amount)) {
                    p.sendMessage(color("&cTrap bankasında yeterli para yok! Banka: &6$" + String.format("%.2f", td.getBank())));
                    return true;
                }
                eco.deposit(p, amount);
                p.sendMessage(color("&a&6$" + String.format("%.2f", amount) + " &atrap bankasından çekildi."));
                p.sendMessage(color("&7Yeni banka bakiyesi: &a$" + String.format("%.2f", td.getBank())));
            }

            case "davet" -> {
                if (args.length < 2) {
                    p.sendMessage(color("&cKullanım: /trap davet <oyuncu>"));
                    return true;
                }
                TrapData td = getPlayerOwnedTrap(p);
                if (td == null) {
                    p.sendMessage(color("&cSahibi olduğunuz bir trap içinde olmalısınız!"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    p.sendMessage(color("&cOyuncu çevrimiçi değil!"));
                    return true;
                }
                if (manager.invite(td.getId(), target.getUniqueId())) {
                    p.sendMessage(color("&a" + target.getName() + " Trap #" + td.getId() + "'e eklendi!"));
                    target.sendMessage(color("&d" + p.getName() + " &asizi &dTrap #" + td.getId() + "&a'ya davet etti!"));
                } else {
                    p.sendMessage(color("&cBu oyuncu zaten üye!"));
                }
            }

            case "kick" -> {
                if (args.length < 2) {
                    p.sendMessage(color("&cKullanım: /trap kick <oyuncu>"));
                    return true;
                }
                TrapData td = getPlayerOwnedTrap(p);
                if (td == null) {
                    p.sendMessage(color("&cSahibi olduğunuz bir trap içinde olmalısınız!"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                UUID targetId = target != null ? target.getUniqueId() : null;
                // Çevrimdışı oyuncu desteği
                if (targetId == null) {
                    for (UUID uid : td.getMembers()) {
                        String name = Bukkit.getOfflinePlayer(uid).getName();
                        if (args[1].equalsIgnoreCase(name)) { targetId = uid; break; }
                    }
                }
                if (targetId == null) {
                    p.sendMessage(color("&cBu oyuncu trapın üyesi değil!"));
                    return true;
                }
                if (manager.kick(td.getId(), targetId)) {
                    p.sendMessage(color("&c" + args[1] + " Trap #" + td.getId() + "'den atıldı."));
                    if (target != null)
                        target.sendMessage(color("&cTrap #" + td.getId() + "'den çıkarıldınız."));
                } else {
                    p.sendMessage(color("&cBu oyuncu üye bulunamadı!"));
                }
            }

            case "banka" -> {
                TrapData td = manager.getTrapAt(p.getLocation());
                if (td == null || !td.isMember(p.getUniqueId())) {
                    p.sendMessage(color("&cBir trapın içinde değilsiniz ya da üye değilsiniz!"));
                    return true;
                }
                p.sendMessage(color("&5§m                    "));
                p.sendMessage(color("&d§lTrap #" + td.getId() + " Banka Bilgisi"));
                p.sendMessage(color("&7Sahip: &f" + td.getOwnerName()));
                p.sendMessage(color("&7Banka: &a$" + String.format("%.2f", td.getBank())));
                p.sendMessage(color("&5§m                    "));
            }

            case "bilgi" -> {
                TrapData td;
                if (args.length >= 2) {
                    try {
                        int id = Integer.parseInt(args[1]);
                        td = manager.getTrap(id);
                    } catch (NumberFormatException e) {
                        p.sendMessage(color("&cGeçersiz ID!"));
                        return true;
                    }
                } else {
                    td = manager.getTrapAt(p.getLocation());
                }
                if (td == null) {
                    p.sendMessage(color("&cTrap bulunamadı!"));
                    return true;
                }
                sendTrapInfo(p, td);
            }

            case "tp" -> {
                TrapData td;
                if (args.length >= 2) {
                    try {
                        int id = Integer.parseInt(args[1]);
                        td = manager.getTrap(id);
                    } catch (NumberFormatException e) {
                        p.sendMessage(color("&cGeçersiz ID!")); return true;
                    }
                } else {
                    td = getPlayerTrapAny(p);
                }
                if (td == null) {
                    p.sendMessage(color("&cTrap bulunamadı!"));
                    return true;
                }
                Location tpLoc = td.getSpawnLocation() != null ? td.getSpawnLocation()
                        : td.getPos1().clone().add(0.5, 1, 0.5);
                p.teleport(tpLoc);
                p.sendMessage(color("&aTrap #" + td.getId() + " konumuna ışınlandınız!"));
            }

            case "liste" -> {
                List<TrapData> myTraps = manager.getPlayerTraps(p.getUniqueId());
                if (myTraps.isEmpty()) {
                    p.sendMessage(color("&cHiç trapınız yok."));
                    return true;
                }
                p.sendMessage(color("&d§lTraplarınız (&f" + myTraps.size() + "&d):"));
                for (TrapData td : myTraps) {
                    p.sendMessage(color("  &5#" + td.getId() + " &f- Boyut: &7" + td.getSizeString()
                            + " &f| Banka: &a$" + String.format("%.0f", td.getBank())
                            + (td.isForSale() ? " &c[SATIŞTA]" : "")));
                }
            }

            case "menu" -> {
                TrapData td = manager.getTrapAt(p.getLocation());
                if (td == null || !td.isMember(p.getUniqueId())) {
                    p.sendMessage(color("&cBir trapın içinde olmalısınız!"));
                    return true;
                }
                gui.openTrapMenu(p, td);
                playerActiveTrap.put(p.getUniqueId(), td.getId());
            }

            default -> {
                sendHelp(p);
            }
        }

        return true;
    }

    // ─────────────────────────────────────────────────────────
    //  YARDIMCI METOTLAR
    // ─────────────────────────────────────────────────────────

    /** Oyuncunun durduğu trap; sadece sahibi ise döner */
    private TrapData getPlayerOwnedTrap(Player p) {
        TrapData td = manager.getTrapAt(p.getLocation());
        if (td != null && td.getOwner().equals(p.getUniqueId())) return td;
        // Aktif seçili trap
        Integer active = playerActiveTrap.get(p.getUniqueId());
        if (active != null) {
            TrapData d = manager.getTrap(active);
            if (d != null && d.getOwner().equals(p.getUniqueId())) return d;
        }
        return null;
    }

    /** Oyuncunun durduğu ya da sahibi/üyesi olduğu herhangi bir trap */
    private TrapData getPlayerTrapAny(Player p) {
        TrapData td = manager.getTrapAt(p.getLocation());
        if (td != null && td.isMember(p.getUniqueId())) return td;
        List<TrapData> myTraps = manager.getPlayerTraps(p.getUniqueId());
        return myTraps.isEmpty() ? null : myTraps.get(0);
    }

    private void sendTrapInfo(Player p, TrapData td) {
        p.sendMessage(color("&5§m                        "));
        p.sendMessage(color("  &d§lTRAP &5#" + td.getId()));
        p.sendMessage(color("  &7Sahip: &f" + td.getOwnerName()));
        p.sendMessage(color("  &7Banka: &a$" + String.format("%.2f", td.getBank())));
        p.sendMessage(color("  &7Üye: &f" + td.getMemberCount()));
        p.sendMessage(color("  &7Boyut: &f" + td.getSizeString()));
        p.sendMessage(color("  &7Satılık: " + (td.isForSale()
                ? "&aEvet &7($" + String.format("%.0f", td.getSalePrice()) + ")"
                : "&cHayır")));
        p.sendMessage(color("&5§m                        "));
    }

    private void sendHelp(Player p) {
        p.sendMessage(color("&5§m                        "));
        p.sendMessage(color("  &d§lTRAP KOMUTLARı"));
        p.sendMessage(color("  &5/trap &7→ Market görüntüle"));
        p.sendMessage(color("  &5/trap menu &7→ Trap menüsü aç"));
        p.sendMessage(color("  &5/trap sat &6<fiyat> &7→ Trapin satışa çıkar"));
        p.sendMessage(color("  &5/trap gericek &7→ Satıştan çek"));
        p.sendMessage(color("  &5/trap parayatir &6<miktar>"));
        p.sendMessage(color("  &5/trap paracek &6<miktar>"));
        p.sendMessage(color("  &5/trap davet &6<oyuncu>"));
        p.sendMessage(color("  &5/trap kick &6<oyuncu>"));
        p.sendMessage(color("  &5/trap banka &7→ Banka bakiyesi"));
        p.sendMessage(color("  &5/trap bilgi &7[id]"));
        p.sendMessage(color("  &5/trap tp &7[id]"));
        p.sendMessage(color("  &5/trap liste &7→ Traplarım"));
        if (p.hasPermission("loginx.trap.admin")) {
            p.sendMessage(color("  &c/trap yap &7[fiyat] &8(Yetkili)"));
            p.sendMessage(color("  &c/trap sil &6<id> &8(Yetkili)"));
            p.sendMessage(color("  &c/trap hologram &8(Yetkili)"));
        }
        p.sendMessage(color("&5§m                        "));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public HashMap<UUID, Integer> getPlayerActiveTrap() {
        return playerActiveTrap;
    }
                                           }

