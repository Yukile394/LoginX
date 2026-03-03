package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

public class LoginX extends JavaPlugin implements Listener {

private final Map<UUID,String> passwords = new HashMap<>();
private final Map<UUID,String> rawPasswords = new HashMap<>();
private final Map<UUID,Integer> attempts = new HashMap<>();
private final Set<UUID> loggedIn = new HashSet<>();
private final Map<UUID,String> lastIP = new HashMap<>();
private final Set<UUID> trustedPlayers = new HashSet<>();
private final Set<UUID> baltaTrusted = new HashSet<>();

// AntiCheat
private final Map<UUID,LinkedList<Long>> clickData = new HashMap<>();
private final Map<UUID,Integer> cheatWarn = new HashMap<>();
private final int MAX_CPS = 14;

private FileConfiguration cfg;

@Override
public void onEnable(){
    Bukkit.getPluginManager().registerEvents(this,this);
    saveDefaultConfig();
    cfg=getConfig();
    loadData();
}

@Override
public void onDisable(){
    saveData();
}

/* =========================================================
   BALTA KORUMA SİSTEMİ
   ========================================================= */

private boolean isAxe(Material m){
    return m.name().endsWith("_AXE");
}

private void removeAxes(Player p){
    for(ItemStack item : p.getInventory().getContents()){
        if(item!=null && isAxe(item.getType())){
            p.getInventory().remove(item);
        }
    }
}

@EventHandler
public void onAxeInteract(PlayerInteractEvent e){
    Player p=e.getPlayer();
    if(p.getGameMode()==GameMode.CREATIVE || p.getAllowFlight()) return;

    ItemStack item=e.getItem();
    if(item!=null && isAxe(item.getType())){
        if(!baltaTrusted.contains(p.getUniqueId())){
            e.setCancelled(true);
            removeAxes(p);
            p.sendMessage(color("&#FF69B4[LoginX] &#FFFFFFBalta kullanma iznin yok!"));
        }
    }
}

@EventHandler
public void onAxeDamage(EntityDamageByEntityEvent e){
    if(!(e.getDamager() instanceof Player p)) return;
    if(p.getGameMode()==GameMode.CREATIVE || p.getAllowFlight()) return;

    ItemStack item=p.getInventory().getItemInMainHand();
    if(item!=null && isAxe(item.getType())){
        if(!baltaTrusted.contains(p.getUniqueId())){
            e.setCancelled(true);
            removeAxes(p);
        }
    }
}

/* =========================================================
   ANTİ HİLE
   ========================================================= */

private boolean bypass(Player p){
    return p.getGameMode()==GameMode.CREATIVE || p.getAllowFlight();
}

private void warnCheat(Player p,String reason){
    UUID u=p.getUniqueId();
    int w=cheatWarn.getOrDefault(u,0)+1;
    cheatWarn.put(u,w);

    if(w==1){
        p.sendMessage(color("&#FFB6C1[AntiHile] &#FFFFFFHile tespit edildi! Kapatmalısın."));
    }else if(w==2){
        p.sendMessage(color("&#FFFFFF[AntiHile] &#FF69B4Son uyarı! Hileyi kapat!"));
    }else{
        p.sendMessage(color("&#FF0000[AntiHile] Kickleniyorsun!"));
        p.kickPlayer(color("&#FF0000Hile tespit edildi."));
    }
}

private boolean checkCPS(Player p){
    if(bypass(p)) return false;

    UUID u=p.getUniqueId();
    long now=System.currentTimeMillis();

    clickData.putIfAbsent(u,new LinkedList<>());
    LinkedList<Long> list=clickData.get(u);
    list.add(now);
    list.removeIf(t-> now-t>1000);

    if(list.size()>MAX_CPS){
        warnCheat(p,"CPS");
        return true;
    }
    return false;
}

@EventHandler(priority=EventPriority.HIGHEST)
public void onDamage(EntityDamageByEntityEvent e){
    if(!(e.getDamager() instanceof Player p)) return;
    if(checkCPS(p)) e.setCancelled(true);
}

@EventHandler(priority=EventPriority.HIGHEST)
public void onTotem(EntityResurrectEvent e){
    if(!(e.getEntity() instanceof Player p)) return;
    if(bypass(p)) return;

    if(p.getInventory().contains(Material.TOTEM_OF_UNDYING)){
        warnCheat(p,"AutoTotem");
    }
}

/* =========================================================
   KOMUTLAR
   ========================================================= */

@Override
public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){

if(cmd.getName().equalsIgnoreCase("baltaizinver")){
    if(!(sender instanceof ConsoleCommandSender)){
        sender.sendMessage("Sadece konsol!");
        return true;
    }
    if(args.length!=1) return true;
    OfflinePlayer t=Bukkit.getOfflinePlayer(args[0]);
    baltaTrusted.add(t.getUniqueId());
    sender.sendMessage("Balta izni verildi.");
    return true;
}

if(cmd.getName().equalsIgnoreCase("baltaizincikar")){
    if(!(sender instanceof ConsoleCommandSender)){
        sender.sendMessage("Sadece konsol!");
        return true;
    }
    if(args.length!=1) return true;
    OfflinePlayer t=Bukkit.getOfflinePlayer(args[0]);
    baltaTrusted.remove(t.getUniqueId());
    if(t.isOnline()) removeAxes(t.getPlayer());
    sender.sendMessage("Balta izni kaldırıldı.");
    return true;
}

return false;
}

/* =========================================================
   HASH & COLOR
   ========================================================= */

private String hash(String input){
    try{
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        byte[] bytes=md.digest(input.getBytes());
        StringBuilder sb=new StringBuilder();
        for(byte b:bytes) sb.append(String.format("%02x",b));
        return sb.toString();
    }catch(Exception e){ return input;}
}

private String color(String text){
    Pattern p=Pattern.compile("&#([A-Fa-f0-9]{6})");
    Matcher m=p.matcher(text);
    StringBuffer sb=new StringBuffer();
    while(m.find()){
        String hex=m.group(1);
        StringBuilder rep=new StringBuilder("§x");
        for(char c:hex.toCharArray()) rep.append("§").append(c);
        m.appendReplacement(sb,rep.toString());
    }
    return ChatColor.translateAlternateColorCodes('&',m.appendTail(sb).toString());
}

/* ========================================================= */

private void loadData(){}
private void saveData(){}

                  }
