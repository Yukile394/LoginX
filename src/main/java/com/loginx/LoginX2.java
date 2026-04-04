package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

class TrapGUI {
    static final String MKT  = "\u00a75\u00a7lTum Trapler";
    static final String MENU = "\u00a7d\u00a7lTrap Menusu";
    static final String CNF  = "\u00a74\u00a7lSatin Al";

    private final TrapX mgr;
    private final EconomyBridge eco;

    TrapGUI(TrapX mgr, EconomyBridge eco) { this.mgr=mgr; this.eco=eco; }

    void openMarket(Player p) {
        List<TrapData> list = mgr.market();
        int pages = Math.max(1,(int)Math.ceil(list.size()/45.0));
        Inventory inv = Bukkit.createInventory(null,54,MKT+" \u00a78- Sayfa 1/"+pages);
        ItemStack bg = mk(Material.GRAY_STAINED_GLASS_PANE,"\u00a7r",null);
        for (int i=45;i<54;i++) inv.setItem(i,bg);
        int slot=0;
        for (TrapData td : list) {
            if (slot>=45) break;
            inv.setItem(slot++, mkTrapItem(td));
        }
        if (list.isEmpty()) inv.setItem(22,mk(Material.BARRIER,"\u00a7c\u00a7lSatista Trap Yok",null));
        inv.setItem(53,mk(Material.RED_STAINED_GLASS_PANE,"\u00a7c\u00a7lKapat",null));
        p.openInventory(inv);
    }

    private ItemStack mkTrapItem(TrapData td) {
        List<String> l = new ArrayList<>();
        l.add("\u00a75\u00a7m                    ");
        l.add("\u00a7dSahibi: \u00a7f"+td.getOwnerName());
        l.add("\u00a7dSatilik: \u00a7aEvet");
        l.add("\u00a7dFiyat: \u00a76"+String.format("%.0f",td.getSalePrice()));
        l.add("\u00a7dBoyut: \u00a7f"+td.getSize());
        l.add("\u00a7dUye: \u00a7f"+td.getMemberCount());
        l.add("\u00a7dBanka: \u00a7a$"+String.format("%.0f",td.getBank()));
        l.add("\u00a75\u00a7m                    ");
        l.add("\u00a7e\u00a7lTiklayarak satin alin");
        l.add("\u00a77minecraft:chest");
        l.add("\u00a78ID:"+td.getId());
        return mk(Material.CHEST,"\u00a75\u00a7lTrap \u00a7d#"+td.getId(),l);
    }

    void openMenu(Player p, TrapData td) {
        Inventory inv = Bukkit.createInventory(null,27,MENU+" \u00a78#"+td.getId());
        ItemStack bg = mk(Material.PURPLE_STAINED_GLASS_PANE,"\u00a7r",null);
        for (int i=0;i<27;i++) inv.setItem(i,bg);
        boolean own = td.getOwner().equals(p.getUniqueId());
        List<String> il = new ArrayList<>();
        il.add("\u00a75\u00a7m                    ");
        il.add("\u00a7dSahip: \u00a7f"+td.getOwnerName());
        il.add("\u00a7dBanka: \u00a7a$"+String.format("%.2f",td.getBank()));
        il.add("\u00a7dUye: \u00a7f"+td.getMemberCount());
        il.add("\u00a7dBoyut: \u00a7f"+td.getSize());
        il.add("\u00a7dSatilik: "+(td.isForSale()?"\u00a7aEvet \u00a77($"+String.format("%.0f",td.getSalePrice())+")":"\u00a7cHayir"));
        il.add("\u00a75\u00a7m                    ");
        inv.setItem(13,mk(Material.NETHER_STAR,"\u00a7d\u00a7lTrap #"+td.getId(),il));
        inv.setItem(10,mk(Material.GOLD_INGOT,"\u00a76\u00a7lPara Yatir", Collections.singletonList("\u00a77/trap parayatir <miktar>")));
        inv.setItem(11,mk(Material.GOLD_NUGGET,"\u00a7e\u00a7lPara Cek",Collections.singletonList("\u00a77/trap paracek <miktar>")));
        inv.setItem(15,mk(Material.ENDER_PEARL,"\u00a7b\u00a7lTrapa Isinlan",Collections.singletonList("\u00a77Spawn noktasina isinlan.")));
        if (own) {
            inv.setItem(16,mk(Material.PLAYER_HEAD,"\u00a7a\u00a7lUye Davet",Collections.singletonList("\u00a77/trap davet <oyuncu>")));
            inv.setItem(12,mk(Material.BARRIER,"\u00a7c\u00a7lUye At",Collections.singletonList("\u00a77/trap kick <oyuncu>")));
            inv.setItem(14, td.isForSale()
                ? mk(Material.RED_BANNER,"\u00a7c\u00a7lSatistan Kaldir",Collections.singletonList("\u00a77/trap gericek"))
                : mk(Material.GREEN_BANNER,"\u00a7a\u00a7lSatisa Cikar",Collections.singletonList("\u00a77/trap sat <fiyat>")));
        }
        inv.setItem(26,mk(Material.RED_STAINED_GLASS_PANE,"\u00a7c\u00a7lKapat",null));
        p.openInventory(inv);
    }

    void openConfirm(Player p, TrapData td) {
        Inventory inv = Bukkit.createInventory(null,27,CNF);
        ItemStack bg = mk(Material.BLACK_STAINED_GLASS_PANE,"\u00a7r",null);
        for (int i=0;i<27;i++) inv.setItem(i,bg);
        List<String> l = new ArrayList<>();
        l.add("\u00a75\u00a7m                    ");
        l.add("\u00a7dSahip: \u00a7f"+td.getOwnerName());
        l.add("\u00a7dFiyat: \u00a76$"+String.format("%.0f",td.getSalePrice()));
        l.add("\u00a7dBoyut: \u00a7f"+td.getSize());
        l.add("\u00a7dBanka: \u00a7a$"+String.format("%.0f",td.getBank()));
        l.add("\u00a75\u00a7m                    ");
        inv.setItem(13,mk(Material.CHEST,"\u00a75\u00a7lTrap #"+td.getId(),l));
        inv.setItem(11,mk(Material.LIME_STAINED_GLASS_PANE,"\u00a7a\u00a7l\u2714 SATIN AL",Arrays.asList("\u00a77$"+String.format("%.0f",td.getSalePrice())+" odenecek.","\u00a7aOnaylamak icin tikla!")));
        inv.setItem(15,mk(Material.RED_STAINED_GLASS_PANE,"\u00a7c\u00a7l\u2718 IPTAL",Collections.singletonList("\u00a77Vazgec.")));
        inv.setItem(0,mk(Material.PAPER,"\u00a78ID:"+td.getId(),null));
        p.openInventory(inv);
    }

    static ItemStack mk(Material mat, String name, List<String> lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        if (m!=null) { m.setDisplayName(name); if (lore!=null) m.setLore(lore); i.setItemMeta(m); }
        return i;
    }
}

public class TrapX2 implements CommandExecutor {
    private final TrapX mgr;
    private final TrapGUI gui;
    private final EconomyBridge eco;
    private final TrapX3 listener;
    final Map<UUID,Integer> active = new HashMap<>();

    public TrapX2(TrapX mgr, TrapGUI gui, EconomyBridge eco, TrapX3 listener) {
        this.mgr=mgr; this.gui=gui; this.eco=eco; this.listener=listener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Sadece oyuncu!"); return true; }
        if (args.length==0) { gui.openMarket(p); return true; }
        String sub = args[0].toLowerCase();
        switch(sub) {
            case "yap" -> {
                if (!p.hasPermission("loginx.trap.admin")) { p.sendMessage(c("&cYetki yok!")); return true; }
                if (!mgr.hasBoth(p.getUniqueId())) { p.sendMessage(c("&cOnce balta ile 2 kose sec!")); return true; }
                double price=0;
                if (args.length>=2) { try { price=Double.parseDouble(args[1]); } catch(NumberFormatException e){ p.sendMessage(c("&cGecersiz fiyat!")); return true; } }
                TrapData td = mgr.create(p.getUniqueId(),p.getName(),mgr.getP1(p.getUniqueId()),mgr.getP2(p.getUniqueId()),price);
                mgr.clearSel(p.getUniqueId());
                active.put(p.getUniqueId(),td.getId());
                p.sendMessage(c("&a&lTrap #"+td.getId()+" olusturuldu! Boyut: "+td.getSize()+" Fiyat: $"+String.format("%.0f",price)));
            }
            case "sil" -> {
                if (!p.hasPermission("loginx.trap.admin")) { p.sendMessage(c("&cYetki yok!")); return true; }
                if (args.length<2) { p.sendMessage(c("&c/trap sil <id>")); return true; }
                try {
                    int id=Integer.parseInt(args[1]);
                    if (mgr.delete(id)) { listener.removeHolo(id); p.sendMessage(c("&aTrap #"+id+" silindi.")); }
                    else p.sendMessage(c("&cTrap bulunamadi!"));
                } catch(NumberFormatException e){ p.sendMessage(c("&cGecersiz ID!")); }
            }
            case "hologram" -> {
                if (!p.hasPermission("loginx.trap.admin")) { p.sendMessage(c("&cYetki yok!")); return true; }
                TrapData td = mgr.at(p.getLocation());
                if (td==null) { p.sendMessage(c("&cBir trapin icinde dur!")); return true; }
                listener.spawnHolo(td, p.getLocation().add(0,3,0));
                p.sendMessage(c("&aTrap #"+td.getId()+" hologrami olusturuldu!"));
            }
            case "hologramsil" -> {
                if (!p.hasPermission("loginx.trap.admin")) { p.sendMessage(c("&cYetki yok!")); return true; }
                listener.clearAllHolo();
                p.sendMessage(c("&cTum hologramlar silindi."));
            }
            case "menu" -> {
                TrapData td = mgr.at(p.getLocation());
                if (td==null||!td.isMember(p.getUniqueId())) { p.sendMessage(c("&cBir trapin icinde olmalisin!")); return true; }
                active.put(p.getUniqueId(),td.getId()); gui.openMenu(p,td);
            }
            case "sat" -> {
                if (args.length<2) { p.sendMessage(c("&c/trap sat <fiyat>")); return true; }
                double pr; try { pr=Double.parseDouble(args[1]); } catch(NumberFormatException e){ p.sendMessage(c("&cGecersiz fiyat!")); return true; }
                TrapData td = ownedTrap(p); if (td==null) { p.sendMessage(c("&cSahibi oldugun bir trapin icinde olmalisin!")); return true; }
                mgr.listMarket(td.getId(),pr);
                p.sendMessage(c("&aTrap #"+td.getId()+" $"+String.format("%.0f",pr)+" ile satisa cikarildi!"));
            }
            case "gericek" -> {
                TrapData td = ownedTrap(p); if (td==null) { p.sendMessage(c("&cSahibi oldugun bir trapin icinde olmalisin!")); return true; }
                mgr.unlistMarket(td.getId()); p.sendMessage(c("&aTrap #"+td.getId()+" satistan cekildi."));
            }
            case "parayatir" -> {
                if (args.length<2) { p.sendMessage(c("&c/trap parayatir <miktar>")); return true; }
                double am; try { am=Double.parseDouble(args[1]); } catch(NumberFormatException e){ p.sendMessage(c("&cGecersiz miktar!")); return true; }
                TrapData td = mgr.at(p.getLocation());
                if (td==null||!td.isMember(p.getUniqueId())) { p.sendMessage(c("&cBu trapin uyesi degilsin!")); return true; }
                if (!eco.take(p,am)) { p.sendMessage(c("&cYetersiz bakiye! Bakiye: "+eco.fmt(eco.bal(p)))); return true; }
                mgr.deposit(td.getId(),am);
                p.sendMessage(c("&a$"+String.format("%.2f",am)+" trap bankasina yatirildi. Banka: $"+String.format("%.2f",td.getBank())));
            }
            case "paracek" -> {
                if (args.length<2) { p.sendMessage(c("&c/trap paracek <miktar>")); return true; }
                double am; try { am=Double.parseDouble(args[1]); } catch(NumberFormatException e){ p.sendMessage(c("&cGecersiz miktar!")); return true; }
                TrapData td = mgr.at(p.getLocation());
                if (td==null) { p.sendMessage(c("&cBir trapin icinde degilsin!")); return true; }
                if (!td.getOwner().equals(p.getUniqueId())) { p.sendMessage(c("&cSadece sahip para cekebilir!")); return true; }
                if (!mgr.withdraw(td.getId(),am)) { p.sendMessage(c("&cBankada yeterli para yok! Banka: $"+String.format("%.2f",td.getBank()))); return true; }
                eco.give(p,am);
                p.sendMessage(c("&a$"+String.format("%.2f",am)+" cekildi. Banka: $"+String.format("%.2f",td.getBank())));
            }
            case "davet" -> {
                if (args.length<2) { p.sendMessage(c("&c/trap davet <oyuncu>")); return true; }
                TrapData td = ownedTrap(p); if (td==null) { p.sendMessage(c("&cSahibi oldugun bir trapin icinde olmalisin!")); return true; }
                Player t = Bukkit.getPlayer(args[1]); if (t==null) { p.sendMessage(c("&cOyuncu cevrimici degil!")); return true; }
                if (mgr.invite(td.getId(),t.getUniqueId())) {
                    p.sendMessage(c("&a"+t.getName()+" Trap #"+td.getId()+"'e eklendi!"));
                    t.sendMessage(c("&d"+p.getName()+" sizi &dTrap #"+td.getId()+"&a'ya davet etti!"));
                } else p.sendMessage(c("&cZaten uye!"));
            }
            case "kick" -> {
                if (args.length<2) { p.sendMessage(c("&c/trap kick <oyuncu>")); return true; }
                TrapData td = ownedTrap(p); if (td==null) { p.sendMessage(c("&cSahibi oldugun bir trapin icinde olmalisin!")); return true; }
                UUID tid = null;
                Player to = Bukkit.getPlayer(args[1]);
                if (to!=null) { tid=to.getUniqueId(); }
                else { for(UUID u:td.getMembers()) { String n=Bukkit.getOfflinePlayer(u).getName(); if(args[1].equalsIgnoreCase(n)){tid=u;break;} } }
                if (tid==null) { p.sendMessage(c("&cOyuncu uye degil!")); return true; }
                if (mgr.kick(td.getId(),tid)) {
                    p.sendMessage(c("&c"+args[1]+" Trap #"+td.getId()+"'den atiildi."));
                    if (to!=null) to.sendMessage(c("&cTrap #"+td.getId()+"'den cikarildiniz."));
                } else p.sendMessage(c("&cKick basarisiz!"));
            }
            case "banka" -> {
                TrapData td = mgr.at(p.getLocation());
                if (td==null||!td.isMember(p.getUniqueId())) { p.sendMessage(c("&cBir trapin icinde olmalisin!")); return true; }
                p.sendMessage(c("&5&m                  "));
                p.sendMessage(c("  &d&lTrap #"+td.getId()+" Banka"));
                p.sendMessage(c("  &7Sahip: &f"+td.getOwnerName()));
                p.sendMessage(c("  &7Banka: &a$"+String.format("%.2f",td.getBank())));
                p.sendMessage(c("&5&m                  "));
            }
            case "bilgi" -> {
                TrapData td = args.length>=2 ? tryGetById(p,args[1]) : mgr.at(p.getLocation());
                if (td==null) { p.sendMessage(c("&cTrap bulunamadi!")); return true; }
                showInfo(p,td);
            }
            case "tp" -> {
                TrapData td;
                if (args.length>=2) { td=tryGetById(p,args[1]); }
                else { List<TrapData> mine=mgr.ofPlayer(p.getUniqueId()); td=mine.isEmpty()?null:mine.get(0); }
                if (td==null) { p.sendMessage(c("&cTrap bulunamadi!")); return true; }
                Location tl = td.getSpawn()!=null ? td.getSpawn() : td.getPos1().clone().add(0.5,1,0.5);
                p.teleport(tl);
                p.sendMessage(c("&aTrap #"+td.getId()+" konumuna isinlandiniz!"));
            }
            case "liste" -> {
                List<TrapData> mine = mgr.ofPlayer(p.getUniqueId());
                if (mine.isEmpty()) { p.sendMessage(c("&cHic trapiniz yok.")); return true; }
                p.sendMessage(c("&d&lTraplariniz (&f"+mine.size()+"&d):"));
                for (TrapData td : mine)
                    p.sendMessage(c("  &5#"+td.getId()+" &7Boyut: &f"+td.getSize()+" &7Banka: &a$"+String.format("%.0f",td.getBank())+(td.isForSale()?" &c[SATISTA]":"")));
            }
            default -> showHelp(p);
        }
        return true;
    }

    private TrapData ownedTrap(Player p) {
        TrapData td = mgr.at(p.getLocation());
        if (td!=null&&td.getOwner().equals(p.getUniqueId())) return td;
        Integer aid = active.get(p.getUniqueId());
        if (aid!=null) { TrapData d=mgr.get(aid); if(d!=null&&d.getOwner().equals(p.getUniqueId())) return d; }
        return null;
    }

    private TrapData tryGetById(Player p, String s) {
        try { return mgr.get(Integer.parseInt(s)); } catch(NumberFormatException e) { p.sendMessage(c("&cGecersiz ID!")); return null; }
    }

    private void showInfo(Player p, TrapData td) {
        p.sendMessage(c("&5&m                        "));
        p.sendMessage(c("  &d&lTRAP &5#"+td.getId()));
        p.sendMessage(c("  &7Sahip: &f"+td.getOwnerName()));
        p.sendMessage(c("  &7Banka: &a$"+String.format("%.2f",td.getBank())));
        p.sendMessage(c("  &7Uye: &f"+td.getMemberCount()));
        p.sendMessage(c("  &7Boyut: &f"+td.getSize()));
        p.sendMessage(c("  &7Satilik: "+(td.isForSale()?"&aEvet &7($"+String.format("%.0f",td.getSalePrice())+")":"&cHayir")));
        p.sendMessage(c("&5&m                        "));
    }

    private void showHelp(Player p) {
        p.sendMessage(c("&5&m                        "));
        p.sendMessage(c("  &d&lTRAP KOMUTLARI"));
        p.sendMessage(c("  &5/trap &7- Market"));
        p.sendMessage(c("  &5/trap menu &7- Trap menusu"));
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
            p.sendMessage(c("  &c/trap yap &7[fiyat] &8(Yetkili - once balta ile sec)"));
            p.sendMessage(c("  &c/trap sil &6<id> &8(Yetkili)"));
            p.sendMessage(c("  &c/trap hologram &8(Yetkili)"));
            p.sendMessage(c("  &c/trap hologramsil &8(Yetkili)"));
        }
        p.sendMessage(c("&5&m                        "));
    }

    private String c(String t) { return ChatColor.translateAlternateColorCodes('&',t); }
                    }
