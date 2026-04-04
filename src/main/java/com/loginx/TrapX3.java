package com.loginx;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class TrapX3 implements Listener {

    private final TrapX mgr;
    private final TrapGUI gui;
    private final EconomyBridge eco;
    private final TrapX2 cmd;
    private final Plugin plugin;

    private final Map<UUID,Integer> pending = new HashMap<>();
    private final Map<Integer,List<ArmorStand>> holos = new HashMap<>();
    private final Map<Integer,Location> holoLocs = new HashMap<>();
    private int step = 0;

    public TrapX3(TrapX mgr, TrapGUI gui, EconomyBridge eco, Plugin plugin) {
        this.mgr=mgr; this.gui=gui; this.eco=eco; this.plugin=plugin; this.cmd=null;
        startAnim();
    }

    public TrapX3(TrapX mgr, TrapGUI gui, EconomyBridge eco, TrapX2 cmd, Plugin plugin) {
        this.mgr=mgr; this.gui=gui; this.eco=eco; this.cmd=cmd; this.plugin=plugin;
        startAnim();
    }

    private void startAnim() {
        new BukkitRunnable() {
            @Override public void run() {
                step=(step+12)%360;
                for (Map.Entry<Integer,List<ArmorStand>> e : holos.entrySet()) {
                    TrapData td = mgr.get(e.getKey()); if (td==null) continue;
                    List<String> lines = buildLines(td,step);
                    List<ArmorStand> sl = e.getValue();
                    for (int i=0;i<Math.min(sl.size(),lines.size());i++) {
                        ArmorStand s=sl.get(i); if(s!=null&&!s.isDead()) s.setCustomName(lines.get(i));
                    }
                }
            }
        }.runTaskTimer(plugin,40L,40L);
    }

    public void spawnHolo(TrapData td, Location base) {
        removeHolo(td.getId());
        holoLocs.put(td.getId(),base.clone());
        holos.put(td.getId(), spawn(base, buildLines(td,step)));
    }

    public void updateHolo(TrapData td) {
        List<ArmorStand> sl = holos.get(td.getId()); if(sl==null) return;
        List<String> lines = buildLines(td,step);
        for (int i=0;i<Math.min(sl.size(),lines.size());i++) {
            ArmorStand s=sl.get(i); if(s!=null&&!s.isDead()) s.setCustomName(lines.get(i));
        }
    }

    public void removeHolo(int id) {
        List<ArmorStand> sl=holos.remove(id); if(sl==null) return;
        for (ArmorStand s:sl) if(s!=null&&!s.isDead()) s.remove();
        holoLocs.remove(id);
    }

    public void clearAllHolo() {
        for (List<ArmorStand> sl:holos.values()) for(ArmorStand s:sl) if(s!=null&&!s.isDead()) s.remove();
        holos.clear(); holoLocs.clear();
    }

    private List<ArmorStand> spawn(Location base, List<String> lines) {
        List<ArmorStand> result = new ArrayList<>();
        double y=0;
        for (String line:lines) {
            Location loc=base.clone().add(0,y,0);
            ArmorStand s=(ArmorStand)base.getWorld().spawnEntity(loc,EntityType.ARMOR_STAND);
            s.setVisible(false); s.setCustomNameVisible(true); s.setCustomName(line);
            s.setGravity(false); s.setMarker(true); s.setBasePlate(false); s.setSmall(true);
            result.add(s); y-=0.28;
        }
        return result;
    }

    private List<String> buildLines(TrapData td, int st) {
        List<String> l = new ArrayList<>();
        l.add(grad("  \u2605 TRAP #"+td.getId()+" \u2605  ",st));
        l.add(h("FF69B4")+"\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac");
        l.add(h("FFB3DE")+"Sahip \u00bb "+ChatColor.WHITE+td.getOwnerName());
        l.add(h("FF85C8")+"Banka \u00bb "+h("00FF88")+"$"+String.format("%,.0f",td.getBank()));
        l.add(h("FFB3DE")+"Uyeler \u00bb "+ChatColor.WHITE+td.getMemberCount()+" Uye");
        l.add(h("FF85C8")+"Boyut \u00bb "+ChatColor.WHITE+td.getSize());
        l.add(td.isForSale() ? h("FFD700")+"\u2726 SATISTA \u00bb "+h("00FF88")+"$"+String.format("%,.0f",td.getSalePrice()) : h("AAAAAA")+"Satilik: "+ChatColor.RED+"Hayir");
        l.add(h("FF69B4")+"\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac\u25ac");
        return l;
    }

    private String grad(String text, int st) {
        StringBuilder sb=new StringBuilder();
        char[] ch=text.toCharArray();
        for (int i=0;i<ch.length;i++) {
            float hue=((st+i*(360f/ch.length))%360)/360f;
            float sat=0.55f+0.45f*(float)Math.abs(Math.sin(Math.toRadians(st+i*10)));
            float bri=Math.min(1f,0.9f+0.1f*(float)Math.abs(Math.cos(Math.toRadians(st+i*8))));
            java.awt.Color c=java.awt.Color.getHSBColor(hue,sat,bri);
            sb.append(h(String.format("%02X%02X%02X",c.getRed(),c.getGreen(),c.getBlue())));
            sb.append(ch[i]);
        }
        return sb.toString();
    }

    private String h(String code) {
        StringBuilder sb=new StringBuilder("\u00a7x");
        for (char c:code.toCharArray()) sb.append('\u00a7').append(c);
        return sb.toString();
    }

    // ── BALTA SECIMI ──────────────────────────────────────────
    @EventHandler(priority=EventPriority.HIGHEST)
    public void onAxe(PlayerInteractEvent e) {
        Player p=e.getPlayer();
        if (!p.hasPermission("loginx.trap.admin")) return;
        if (e.getClickedBlock()==null) return;
        if (!p.getInventory().getItemInMainHand().getType().name().endsWith("_AXE")) return;
        Location loc=e.getClickedBlock().getLocation();
        if (e.getAction()==Action.LEFT_CLICK_BLOCK) {
            mgr.setP1(p.getUniqueId(),loc);
            p.sendMessage(c("&d[Trap] &aKose 1: &f"+loc.getBlockX()+", "+loc.getBlockY()+", "+loc.getBlockZ()));
            e.setCancelled(true);
        } else if (e.getAction()==Action.RIGHT_CLICK_BLOCK) {
            mgr.setP2(p.getUniqueId(),loc);
            p.sendMessage(c("&d[Trap] &aKose 2: &f"+loc.getBlockX()+", "+loc.getBlockY()+", "+loc.getBlockZ()));
            e.setCancelled(true);
        }
    }

    // ── KORUMA ────────────────────────────────────────────────
    @EventHandler(priority=EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (e.getPlayer().hasPermission("loginx.trap.admin")) return;
        TrapData td=mgr.at(e.getBlock().getLocation()); if(td==null) return;
        if (!td.isMember(e.getPlayer().getUniqueId())) { e.setCancelled(true); e.getPlayer().sendMessage(c("&cX Bu trap icerisindeki esyalari/kapilari kullanamazsiniz!")); }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getPlayer().hasPermission("loginx.trap.admin")) return;
        TrapData td=mgr.at(e.getBlock().getLocation()); if(td==null) return;
        if (!td.isMember(e.getPlayer().getUniqueId())) { e.setCancelled(true); e.getPlayer().sendMessage(c("&cX Bu trap icerisindeki esyalari/kapilari kullanamazsiniz!")); }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p=e.getPlayer();
        if (p.hasPermission("loginx.trap.admin")) return;
        if (e.getClickedBlock()==null) return;
        String mn=e.getClickedBlock().getType().name();
        if (!mn.contains("CHEST")&&!mn.contains("DOOR")&&!mn.contains("GATE")&&!mn.contains("TRAPDOOR")
            &&!mn.contains("BUTTON")&&!mn.contains("LEVER")&&!mn.contains("BARREL")
            &&!mn.contains("FURNACE")&&!mn.contains("HOPPER")
            &&e.getClickedBlock().getType()!=Material.CRAFTING_TABLE
            &&e.getClickedBlock().getType()!=Material.ANVIL
            &&e.getClickedBlock().getType()!=Material.ENCHANTING_TABLE
            &&e.getClickedBlock().getType()!=Material.BREWING_STAND) return;
        TrapData td=mgr.at(e.getClickedBlock().getLocation()); if(td==null) return;
        if (!td.isMember(p.getUniqueId())) { e.setCancelled(true); p.sendMessage(c("&cX Bu trap icerisindeki esyalari/kapilari kullanamazsiniz!")); }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b->mgr.at(b.getLocation())!=null);
    }

    // ── GUI ───────────────────────────────────────────────────
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title=e.getView().getTitle();

        if (title.startsWith(TrapGUI.MKT)) {
            e.setCancelled(true);
            ItemStack item=e.getCurrentItem();
            if (item==null||item.getType()!=Material.CHEST||item.getItemMeta()==null) return;
            String idLine=null;
            for (String line:item.getItemMeta().getLore()) { if(line.startsWith("\u00a78ID:")){ idLine=line.replace("\u00a78ID:",""); break; } }
            if (idLine==null) return;
            try {
                TrapData td=mgr.get(Integer.parseInt(idLine.trim()));
                if (td==null) return;
                pending.put(p.getUniqueId(),td.getId());
                gui.openConfirm(p,td);
            } catch(NumberFormatException ignored){}
            return;
        }

        if (title.equals(TrapGUI.CNF)) {
            e.setCancelled(true);
            ItemStack item=e.getCurrentItem(); if(item==null) return;
            if (item.getType()==Material.LIME_STAINED_GLASS_PANE) {
                Integer tid=pending.get(p.getUniqueId()); if(tid==null){p.closeInventory();return;}
                TrapData td=mgr.get(tid);
                if (td==null||!td.isForSale()) { p.sendMessage(c("&cBu trap artik satista degil!")); p.closeInventory(); pending.remove(p.getUniqueId()); return; }
                if (td.getOwner().equals(p.getUniqueId())) { p.sendMessage(c("&cKendi trapini satin alamazsin!")); p.closeInventory(); return; }
                if (!eco.take(p,td.getSalePrice())) { p.sendMessage(c("&cYetersiz bakiye! Gereken: $"+String.format("%.0f",td.getSalePrice()))); p.closeInventory(); return; }
                Player old=Bukkit.getPlayer(td.getOwner());
                if (old!=null) { eco.give(old,td.getSalePrice()); old.sendMessage(c("&aTrap #"+td.getId()+" "+p.getName()+"'a $"+String.format("%.0f",td.getSalePrice())+" karsiliginda satildi!")); }
                td.setOwner(p.getUniqueId()); td.setOwnerName(p.getName());
                td.setForSale(false); mgr.save(td); mgr.unlistMarket(td.getId()); updateHolo(td);
                p.sendMessage(c("&a&lTebrikler! Trap #"+td.getId()+" artik sizin!"));
                p.closeInventory(); pending.remove(p.getUniqueId());
                Location tl=td.getSpawn()!=null?td.getSpawn():td.getPos1().clone().add(0.5,1,0.5);
                p.teleport(tl);
            } else if (item.getType()==Material.RED_STAINED_GLASS_PANE) {
                pending.remove(p.getUniqueId()); p.closeInventory(); gui.openMarket(p);
            }
            return;
        }

        if (title.startsWith(TrapGUI.MENU)) {
            e.setCancelled(true);
            ItemStack item=e.getCurrentItem(); if(item==null) return;
            Integer aid=cmd!=null?cmd.active.get(p.getUniqueId()):null;
            TrapData td=aid!=null?mgr.get(aid):mgr.at(p.getLocation());
            if (td==null){p.closeInventory();return;}
            switch(item.getType()) {
                case ENDER_PEARL -> { p.closeInventory(); Location tl=td.getSpawn()!=null?td.getSpawn():td.getPos1().clone().add(0.5,1,0.5); p.teleport(tl); p.sendMessage(c("&aTrapa isinlandiniz!")); }
                case GOLD_INGOT  -> { p.closeInventory(); p.sendMessage(c("&7/trap parayatir <miktar>")); }
                case GOLD_NUGGET -> { p.closeInventory(); p.sendMessage(c("&7/trap paracek <miktar>")); }
                case PLAYER_HEAD -> { p.closeInventory(); p.sendMessage(c("&7/trap davet <oyuncu>")); }
                case BARRIER     -> { p.closeInventory(); p.sendMessage(c("&7/trap kick <oyuncu>")); }
                case GREEN_BANNER-> { p.closeInventory(); p.sendMessage(c("&7/trap sat <fiyat>")); }
                case RED_BANNER  -> { mgr.unlistMarket(td.getId()); p.sendMessage(c("&cTrap satistan cekildi.")); gui.openMenu(p,td); }
                case RED_STAINED_GLASS_PANE -> p.closeInventory();
                default -> {}
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getView().getTitle().equals(TrapGUI.CNF)) pending.remove(e.getPlayer().getUniqueId());
    }

    private String c(String t) { return ChatColor.translateAlternateColorCodes('&',t); }
          }
