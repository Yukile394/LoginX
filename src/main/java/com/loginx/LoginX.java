package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LoginX extends JavaPlugin implements Listener {

    private final HashMap<UUID, Integer> kills        = new HashMap<>();
    private final HashMap<UUID, Integer> playtime     = new HashMap<>();
    private final HashMap<UUID, Integer> blocksBroken = new HashMap<>();
    private final HashMap<Location, String> activeHolograms = new HashMap<>();
    private final List<ArmorStand> spawnedStands = new ArrayList<>();
    private long nextResetTime;

    private TrapX        trapX;
    private TrapGUI      trapGUI;
    private EconomyBridge eco;
    private TrapX2       trapCmd;
    private TrapX3       trapListener;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        loadStats();
        initTrap();

        try {
            LoginX2 m = new LoginX2(this);
            Bukkit.getPluginManager().registerEvents(m, this);
            getLogger().info("LoginX2 aktif.");
        } catch (Throwable t) {
            getLogger().warning("LoginX2 yuklenemedi.");
        }

        startPlaytime();
        startHoloUpdater();
        startWeeklyReset();
        getLogger().info("LoginX aktif!");
    }

    @Override
    public void onDisable() {
        saveStats();
        clearHolos();
        if (trapX != null) trapX.saveAll();
        if (trapListener != null) trapListener.clearAllHolo();
        getLogger().info("LoginX kapandi.");
    }

    private void initTrap() {
        eco          = new EconomyBridge(this);
        trapX        = new TrapX(this);
        trapGUI      = new TrapGUI(trapX, eco);
        trapListener = new TrapX3(trapX, trapGUI, eco, this);
        trapCmd      = new TrapX2(trapX, trapGUI, eco, trapListener);

        // GUI için TrapX3'e TrapX2 referansını veriyoruz (İki kere başlatma hatası düzeltildi)
        trapListener.setCmd(trapCmd);

        PluginCommand tc = getCommand("trap");
        if (tc != null) tc.setExecutor(trapCmd);
        else getLogger().warning("/trap plugin.yml'de tanimli degil!");

        Bukkit.getPluginManager().registerEvents(trapListener, this);
        getLogger().info("[Trap] Trap sistemi baslatildi.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Sadece oyuncu!"); return true; }
        if (!p.hasPermission("loginx.admin")) { p.sendMessage(color("&#FF0000[!] &cYetki yok!")); return true; }
        Location tl = p.getTargetBlock(null, 10).getLocation().add(0.5, 3.0, 0.5);
        String cn = cmd.getName().toLowerCase();
        if (cn.equals("skorkill"))  { spawnHolo(tl,"KILLS");   p.sendMessage(color("&#00FF00[!] &aKill tablosu olusturuldu!")); return true; }
        if (cn.equals("skorzaman")) { spawnHolo(tl,"PLAYTIME");p.sendMessage(color("&#00FF00[!] &aZaman tablosu olusturuldu!")); return true; }
        if (cn.equals("skorblok"))  { spawnHolo(tl,"BLOCKS");  p.sendMessage(color("&#00FF00[!] &aBlok tablosu olusturuldu!")); return true; }
        if (cn.equals("skorsil"))   { clearHolos(); activeHolograms.clear(); p.sendMessage(color("&#FF0000[!] &cHologramlar silindi!")); return true; }
        return true;
    }

    @EventHandler public void onKill(PlayerDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k != null) kills.merge(k.getUniqueId(), 1, Integer::sum);
    }
    @EventHandler public void onBlockBreak(BlockBreakEvent e) {
        blocksBroken.merge(e.getPlayer().getUniqueId(), 1, Integer::sum);
    }

    private void startPlaytime() {
        new BukkitRunnable() { @Override public void run() {
            for (Player p : Bukkit.getOnlinePlayers()) playtime.merge(p.getUniqueId(),1,Integer::sum);
        }}.runTaskTimer(this, 1200L, 1200L);
    }

    private void startHoloUpdater() {
        new BukkitRunnable() { @Override public void run() {
            if (activeHolograms.isEmpty()) return;
            clearHolos();
            activeHolograms.forEach((loc,type)->buildHolo(loc,type));
        }}.runTaskTimer(this, 100L, 100L);
    }

    private void startWeeklyReset() {
        new BukkitRunnable() { @Override public void run() {
            if (System.currentTimeMillis() >= nextResetTime) {
                kills.clear(); playtime.clear(); blocksBroken.clear();
                nextResetTime = System.currentTimeMillis() + 7L*24*60*60*1000;
                Bukkit.broadcastMessage(color("&#00FFFF[!] &lHAFTALIK SIFIRLAMA!"));
            }
        }}.runTaskTimer(this, 20L*60, 20L*60*60);
    }

    private void spawnHolo(Location loc, String type) { activeHolograms.put(loc,type); buildHolo(loc,type); }

    private void buildHolo(Location base, String type) {
        List<String> lines = new ArrayList<>();
        if (type.equals("KILLS"))   { lines.add("&#FF0000&l\u2694 EN COK OLDURENLER \u2694"); lines.addAll(top10(kills,"Oldurme")); }
        else if (type.equals("PLAYTIME")) { lines.add("&#00FFFF&l\u23f3 EN COK OYNAYANLAR \u23f3"); lines.addAll(top10pt(playtime)); }
        else { lines.add("&#00FF00&l\u26cf EN COK BLOK KIRANLAR \u26cf"); lines.addAll(top10(blocksBroken,"Blok")); }
        lines.add("&7(Her hafta sifirlanir)");
        double y=0;
        for (String line : lines) {
            ArmorStand s=(ArmorStand)base.getWorld().spawnEntity(base.clone().subtract(0,y,0),EntityType.ARMOR_STAND);
            s.setVisible(false); s.setCustomNameVisible(true); s.setCustomName(color(line));
            s.setGravity(false); s.setMarker(true); s.setBasePlate(false);
            spawnedStands.add(s); y+=0.3;
        }
    }

    private void clearHolos() {
        spawnedStands.forEach(s->{ if(s!=null&&!s.isDead()) s.remove(); });
        spawnedStands.clear();
    }

    private List<String> top10(HashMap<UUID,Integer> map, String suf) {
        List<String> lines = new ArrayList<>();
        if (map.isEmpty()) { lines.add("&cVeri yok."); return lines; }
        List<Map.Entry<UUID,Integer>> sorted = map.entrySet().stream()
            .sorted(Map.Entry.<UUID,Integer>comparingByValue().reversed()).limit(10).collect(Collectors.toList());
        int r=1;
        for (Map.Entry<UUID,Integer> en : sorted) {
            String n = Bukkit.getOfflinePlayer(en.getKey()).getName(); if(n==null) n="Bilinmiyor";
            String rc = r==1?"&#FFD700":r==2?"&#C0C0C0":r==3?"&#CD7F32":"&e";
            lines.add(color(rc+r+". &f"+n+" &7- &a"+en.getValue()+" "+suf)); r++;
        }
        return lines;
    }

    private List<String> top10pt(HashMap<UUID,Integer> map) {
        List<String> lines = new ArrayList<>();
        if (map.isEmpty()) { lines.add("&cVeri yok."); return lines; }
        List<Map.Entry<UUID,Integer>> sorted = map.entrySet().stream()
            .sorted(Map.Entry.<UUID,Integer>comparingByValue().reversed()).limit(10).collect(Collectors.toList());
        int r=1;
        for (Map.Entry<UUID,Integer> en : sorted) {
            String n = Bukkit.getOfflinePlayer(en.getKey()).getName(); if(n==null) n="Bilinmiyor";
            int h=en.getValue()/60, m=en.getValue()%60;
            String rc = r==1?"&#FFD700":r==2?"&#C0C0C0":r==3?"&#CD7F32":"&e";
            lines.add(color(rc+r+". &f"+n+" &7- &a"+(h>0?h+"s "+m+"d":m+"d"))); r++;
        }
        return lines;
    }

    private void saveStats() {
        FileConfiguration c = getConfig();
        c.set("next_reset", nextResetTime);
        kills.forEach((k,v)->c.set("stats.kills."+k,v));
        playtime.forEach((k,v)->c.set("stats.playtime."+k,v));
        blocksBroken.forEach((k,v)->c.set("stats.blocks."+k,v));
        saveConfig();
    }

    private void loadStats() {
        FileConfiguration c = getConfig();
        nextResetTime = c.contains("next_reset") ? c.getLong("next_reset")
                : System.currentTimeMillis() + 7L*24*60*60*1000;
        if (c.contains("stats.kills"))
            c.getConfigurationSection("stats.kills").getKeys(false)
                .forEach(s->kills.put(UUID.fromString(s),c.getInt("stats.kills."+s)));
        if (c.contains("stats.playtime"))
            c.getConfigurationSection("stats.playtime").getKeys(false)
                .forEach(s->playtime.put(UUID.fromString(s),c.getInt("stats.playtime."+s)));
        if (c.contains("stats.blocks"))
            c.getConfigurationSection("stats.blocks").getKeys(false)
                .forEach(s->blocksBroken.put(UUID.fromString(s),c.getInt("stats.blocks."+s)));
    }

    public String color(String text) {
        Pattern pt = Pattern.compile("&#([a-fA-F0-9]{6})");
        Matcher mt = pt.matcher(text);
        StringBuffer buf = new StringBuffer();
        while (mt.find()) {
            StringBuilder rep = new StringBuilder("\u00a7x");
            for (char ch : mt.group(1).toCharArray()) rep.append('\u00a7').append(ch);
            mt.appendReplacement(buf, rep.toString());
        }
        return ChatColor.translateAlternateColorCodes('&', mt.appendTail(buf).toString());
    }
}
