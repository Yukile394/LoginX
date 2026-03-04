package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;
import org.bukkit.util.Vector;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoginX extends JavaPlugin implements Listener {

    // --- VERİ TABLOLARI ---
    private final Map<UUID, String> passwords = new HashMap<>(); 
    private final Map<UUID, String> rawPasswords = new HashMap<>(); 
    private final Map<UUID, Integer> attempts = new HashMap<>();
    public final Set<UUID> loggedIn = new HashSet<>(); // LoginX2'den erişim için public yapıldı veya tek dosyada kalacak
    private final Map<UUID, String> lastIP = new HashMap<>();
    private final Set<UUID> trustedPlayers = new HashSet<>(); 
    private final Map<UUID, Long> lastQuitTime = new HashMap<>();
    
    // --- CEZA & İADE VERİLERİ ---
    private final Map<UUID, DeathRecord> deathDatabase = new HashMap<>();
    
    // --- ANTİ-HİLE (ANTI-CHEAT) VERİLERİ ---
    private final Map<UUID, LinkedList<Long>> clickData = new HashMap<>();
    private final Map<UUID, Long> lastChatTime = new HashMap<>();
    private final Map<UUID, Long> lastInventoryClick = new HashMap<>();
    private final int MAX_CPS = 16; 
    private final double MAX_REACH = 4.5;

    private FileConfiguration cfg;
    private final String GUI_LOGIN_TITLE = color("&#FF69B4&lOyuncu Verileri");
    private final String GUI_IZIN_TITLE = color("&#FFB6C1&lÖzel İzinli Oyuncular");
    private final String GUI_IADE_TITLE = color("&#FF1493&lÖlüm Arşivi (İade)");

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        cfg = getConfig();
        loadData();
        startVisualTasks();
        getLogger().info("LoginX ULTRA GÜVENLİK, MODERASYON & İADE Aktif!");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    // --- ÖLÜM KAYIT SINIFI ---
    private static class DeathRecord {
        ItemStack[] items;
        String date;
        String loc;
        String killer;
        String reason;
        
        DeathRecord(ItemStack[] items, Location l, String k, String r) {
            this.items = items.clone();
            this.date = new SimpleDateFormat("dd/MM HH:mm").format(new Date());
            this.loc = l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
            this.killer = k;
            this.reason = r;
        }
    }

    private void loadData() {
        if (cfg.contains("data")) {
            for (String key : cfg.getConfigurationSection("data").getKeys(false)) {
                UUID u = UUID.fromString(key);
                passwords.put(u, cfg.getString("data." + key + ".hash"));
                rawPasswords.put(u, cfg.getString("data." + key + ".raw"));
                lastIP.put(u, cfg.getString("data." + key + ".ip"));
            }
        }
        if (cfg.contains("trusted_players")) {
            for (String uuidStr : cfg.getStringList("trusted_players")) {
                trustedPlayers.add(UUID.fromString(uuidStr));
            }
        }
    }

    private void saveData() {
        cfg.set("data", null);
        for (UUID u : passwords.keySet()) {
            cfg.set("data." + u + ".hash", passwords.get(u));
            cfg.set("data." + u + ".raw", rawPasswords.get(u));
            cfg.set("data." + u + ".ip", lastIP.get(u));
        }
        List<String> trustedList = new ArrayList<>();
        for (UUID u : trustedPlayers) trustedList.add(u.toString());
        cfg.set("trusted_players", trustedList);
        saveConfig();
    }

    // --- GİRİŞ / ÇIKIŞ ---
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        String currentIP = p.getAddress().getAddress().getHostAddress();

        // Ban Kontrolü
        if (cfg.contains("punishments.bans." + uuid) || cfg.contains("punishments.ipbans." + currentIP.replace(".", "_"))) {
            p.kickPlayer(color("&#FF0000Sunucudan Yasaklısınız!"));
            return;
        }

        long lastQuit = lastQuitTime.getOrDefault(uuid, 0L);
        boolean withinTimeLimit = (System.currentTimeMillis() - lastQuit) <= (8 * 60 * 1000);

        if (passwords.containsKey(uuid) && lastIP.containsKey(uuid) && lastIP.get(uuid).equals(currentIP) && withinTimeLimit && lastQuit != 0L) {
            loggedIn.add(uuid);
            p.sendMessage(color("&#00FF00[LoginX] &aOto-giriş yapıldı!"));
            playSuccessEffect(p);
            return;
        }

        lastIP.put(uuid, currentIP);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, false, false));
        sendLoginTitle(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        loggedIn.remove(u);
        clickData.remove(u); 
        lastQuitTime.put(u, System.currentTimeMillis());
    }

    // --- MODERASYON TASARIMI (PEMBE RGB) ---
    private void broadcastPunishment(String type, String target, String staff, String reason, String time) {
        String line = color("&#FF1493&m----------------------------------------");
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(line);
        Bukkit.broadcastMessage(centerText(color("&#FF69B4&l(" + type + " Sistemi)")));
        Bukkit.broadcastMessage(color("  &#FFB6C1İşlem Yapılan: &f" + target));
        Bukkit.broadcastMessage(color("  &#FFB6C1Yetkili: &f" + staff));
        Bukkit.broadcastMessage(color("  &#FFB6C1Süre: &f" + time));
        Bukkit.broadcastMessage(color("  &#FFB6C1Sebep: &7" + reason));
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(centerText(color("&#FF69B4[SVX NW - discord.gg/svxnw]")));
        Bukkit.broadcastMessage(line);
        Bukkit.broadcastMessage("");
    }

    // --- KOMUTLAR ---
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        
        // MODERASYON KOMUTLARI
        if (cmd.getName().equalsIgnoreCase("ban") || cmd.getName().equalsIgnoreCase("mute") || cmd.getName().equalsIgnoreCase("kick") || cmd.getName().equalsIgnoreCase("ipban") || cmd.getName().equalsIgnoreCase("kickip")) {
            if (!sender.hasPermission("loginx.admin")) return true;
            if (args.length < 1) { sender.sendMessage(color("&#FF0000Kullanım: /" + label + " <oyuncu> [sebep]")); return true; }
            
            String targetName = args[0];
            String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Kurallara Aykırı Hareket";
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            
            if (cmd.getName().equalsIgnoreCase("ban")) {
                cfg.set("punishments.bans." + target.getUniqueId(), reason); saveConfig();
                broadcastPunishment("Ban", targetName, sender.getName(), reason, "Süresiz");
                if (target.isOnline()) ((Player)target).kickPlayer(color("&#FF0000Yasaklandınız!"));
            } else if (cmd.getName().equalsIgnoreCase("mute")) {
                cfg.set("punishments.mutes." + target.getUniqueId(), reason); saveConfig();
                broadcastPunishment("Mute", targetName, sender.getName(), reason, "Süresiz");
            } else if (cmd.getName().equalsIgnoreCase("kick")) {
                if (target.isOnline()) {
                    ((Player)target).kickPlayer(color("&#FF0000Sunucudan Atıldınız!\n&fSebep: " + reason));
                    broadcastPunishment("Kick", targetName, sender.getName(), reason, "Tek Seferlik");
                }
            } else if (cmd.getName().equalsIgnoreCase("ipban")) {
                if (target.isOnline()) {
                    String ip = ((Player)target).getAddress().getAddress().getHostAddress().replace(".", "_");
                    cfg.set("punishments.ipbans." + ip, reason); saveConfig();
                    broadcastPunishment("IP-Ban", targetName, sender.getName(), reason, "Süresiz");
                    ((Player)target).kickPlayer(color("&#FF0000IP Adresiniz Yasaklandı!"));
                }
            }
            return true;
        }

        // İADE KOMUTU
        if (cmd.getName().equalsIgnoreCase("iade")) {
            if (!sender.hasPermission("loginx.admin")) return true;
            if (!(sender instanceof Player p)) return true;
            openIadeMenu(p);
            return true;
        }

        // --- DİĞER KOMUTLAR (SENİN ORİJİNAL YAPIN) ---
        if (!(sender instanceof Player player)) {
            // Konsol komutları (izinver vb. senin orijinal kodun buraya gelir)
            return true; 
        }
        UUID uuid = player.getUniqueId();

        if (cmd.getName().equalsIgnoreCase("register")) {
            if (args.length < 2) { player.sendMessage(color("&#FF0000Kullanım: /register <şifre> <şifre>")); return true; }
            if (passwords.containsKey(uuid)) { player.sendMessage(color("&#FF0000Zaten kayıtlısın!")); return true; }
            if (!args[0].equals(args[1])) { player.sendMessage(color("&#FF0000Şifreler uyuşmuyor!")); return true; }
            passwords.put(uuid, hash(args[0])); rawPasswords.put(uuid, args[0]); loggedIn.add(uuid); saveData();
            player.sendMessage(color("&#00FF00Başarıyla kayıt oldun!")); playSuccessEffect(player);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("login")) {
            if (args.length < 1) { player.sendMessage(color("&#FF0000Kullanım: /login <şifre>")); return true; }
            if (hash(args[0]).equals(passwords.get(uuid))) {
                loggedIn.add(uuid); player.sendMessage(color("&#00FF00Giriş başarılı!")); playSuccessEffect(player);
            } else {
                player.sendMessage(color("&#FF0000Yanlış şifre!"));
                attempts.put(uuid, attempts.getOrDefault(uuid, 0) + 1);
                if (attempts.get(uuid) >= 3) player.kickPlayer(color("&#FF0000Hatalı deneme limiti!"));
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("logingoster")) { if (player.hasPermission("loginx.admin")) openLoginMenu(player); return true; }

        return true;
    }

    // --- ÖLÜM & İADE EVENTLERİ ---
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        String killer = (p.getKiller() != null) ? p.getKiller().getName() : "Doğa/Bilinmiyor";
        deathDatabase.put(p.getUniqueId(), new DeathRecord(p.getInventory().getContents(), p.getLocation(), killer, e.getDeathMessage()));
    }

    private void openIadeMenu(Player p) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_IADE_TITLE);
        for (UUID id : deathDatabase.keySet()) {
            DeathRecord record = deathDatabase.get(id);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
            meta.setDisplayName(color("&#FF69B4" + Bukkit.getOfflinePlayer(id).getName()));
            meta.setLore(Arrays.asList(
                color("&7&m-----------------------"),
                color("&#FFB6C1Tarih: &f" + record.date),
                color("&#FFB6C1Konum: &f" + record.loc),
                color("&#FFB6C1Katil: &f" + record.killer),
                color(""),
                color("&#FF1493» Tıkla ve İade Et!"),
                color("&7&m-----------------------")
            ));
            head.setItemMeta(meta);
            gui.addItem(head);
        }
        p.openInventory(gui);
    }

    // --- GÖRSEL TASARIM (TAB & SCOREBOARD) ---
    private void startVisualTasks() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateScoreboard(p);
                    p.setPlayerListHeaderFooter(
                        color("\n&#FF1493&lSVX NW NETWORK\n&#FFB6C1Keyifli Oyunlar Dileriz!\n"),
                        color("\n&#FF69B4www.svxnw.com\n&#FFB6C1discord.gg/svxnw\n")
                    );
                }
            }
        }.runTaskTimer(this, 0, 20L);
    }

    private void updateScoreboard(Player p) {
        Scoreboard b = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = b.registerNewObjective("svx", "dummy", color("&#FF1493&lSVX NW"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        String[] lines = {
            "&7&m------------------",
            "&#FFB6C1İSİM &8» &f" + p.getName(),
            "&#FFB6C1RÜTBE &8» &fOyuncu",
            " ",
            "&#FFB6C1PING &8» &a" + p.getPing() + "ms",
            "&#FFB6C1AKTİF &8» &f" + Bukkit.getOnlinePlayers().size(),
            " ",
            "&#FF69B4www.svxnw.com",
            "&7&m------------------ "
        };
        int i = lines.length;
        for (String s : lines) o.getScore(color(s)).setScore(i--);
        p.setScoreboard(b);
    }

    // --- ANTİ-HİLE VE GENEL EVENTLER ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        
        if (e.getView().getTitle().equals(GUI_IADE_TITLE)) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getType() != Material.PLAYER_HEAD) return;
            SkullMeta sm = (SkullMeta) e.getCurrentItem().getItemMeta();
            if (sm.getOwningPlayer() != null) {
                Player target = Bukkit.getPlayer(sm.getOwningPlayer().getUniqueId());
                if (target != null && target.isOnline()) {
                    DeathRecord dr = deathDatabase.get(target.getUniqueId());
                    for (ItemStack item : dr.items) if (item != null) target.getInventory().addItem(item);
                    target.playSound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
                    p.sendMessage(color("&#00FF00Eşyalar iade edildi!"));
                    deathDatabase.remove(target.getUniqueId());
                    p.closeInventory();
                }
            }
            return;
        }

        if (e.getView().getTitle().equals(GUI_LOGIN_TITLE) || e.getView().getTitle().equals(GUI_IZIN_TITLE)) { e.setCancelled(true); return; }

        // Macro Koruması (Senin Orijinal Kodun)
        long now = System.currentTimeMillis();
        long lastClick = lastInventoryClick.getOrDefault(p.getUniqueId(), 0L);
        if (now - lastClick < 20) {
            e.setCancelled(true);
            kickCheater(p, "AutoTotem / Macro");
            return;
        }
        lastInventoryClick.put(p.getUniqueId(), now);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!loggedIn.contains(p.getUniqueId())) { e.setCancelled(true); return; }
        if (cfg.contains("punishments.mutes." + p.getUniqueId())) {
            e.setCancelled(true);
            p.sendMessage(color("&#FF0000Susturuldunuz! Sebep: " + cfg.getString("punishments.mutes." + p.getUniqueId())));
        }
    }

    @EventHandler
    public void onMeCommand(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().toLowerCase();
        if (msg.startsWith("/me") || msg.startsWith("/minecraft:me")) {
            if (cfg.contains("punishments.mutes." + e.getPlayer().getUniqueId())) {
                e.setCancelled(true);
                e.getPlayer().sendMessage(color("&#FF0000Muteliyken bu komutu kullanamazsınız!"));
            }
        }
    }

    // --- ANTİ-HİLE MOTORU (CPS, REACH, MOVE) ---
    @EventHandler(priority = EventPriority.HIGHEST) 
    public void onInteract(PlayerInteractEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) { e.setCancelled(true); return; }
        if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            UUID uuid = e.getPlayer().getUniqueId();
            long now = System.currentTimeMillis();
            clickData.putIfAbsent(uuid, new LinkedList<>());
            LinkedList<Long> clicks = clickData.get(uuid);
            clicks.add(now);
            clicks.removeIf(time -> now - time > 1000);
            if (clicks.size() > MAX_CPS) { e.setCancelled(true); kickCheater(e.getPlayer(), "Macro (" + clicks.size() + " CPS)"); }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST) 
    public void onDamageDeal(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!loggedIn.contains(p.getUniqueId())) { e.setCancelled(true); return; }
        double dist = p.getLocation().distance(e.getEntity().getLocation());
        if (dist > MAX_REACH) { e.setCancelled(true); kickCheater(p, "Reach (" + String.format("%.2f", dist) + "m)"); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!loggedIn.contains(p.getUniqueId())) { e.setCancelled(true); return; }
        double yDiff = e.getTo().getY() - e.getFrom().getY();
        if (yDiff > 0.85 && p.getVelocity().getY() < 0.1 && p.getGameMode() == GameMode.SURVIVAL) {
            kickCheater(p, "Fly/Speed");
        }
    }

    private void kickCheater(Player p, String reason) {
        new BukkitRunnable() { @Override public void run() { 
            p.kickPlayer(color("&#FF0000[Anti-Cheat]\n\n&fHile: &e" + reason)); 
            Bukkit.broadcastMessage(color("&#FF0000[!] &e" + p.getName() + " &chileden dolayı atıldı."));
        }}.runTask(this);
    }

    // --- YARDIMCI METOTLAR ---
    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return input; }
    }

    public String color(String text) {
        Pattern pattern = Pattern.compile("&#([a-fA-F0-9]{6})");
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) replacement.append("§").append(c);
            matcher.appendReplacement(buffer, replacement.toString());
        }
        return ChatColor.translateAlternateColorCodes('&', matcher.appendTail(buffer).toString());
    }

    private String centerText(String text) {
        int spaces = (45 - ChatColor.stripColor(text).length()) / 2;
        return " ".repeat(Math.max(0, spaces)) + text;
    }

    private void sendLoginTitle(Player p) {
        String h = !passwords.containsKey(p.getUniqueId()) ? cfg.getString("title_colors.register.header") : cfg.getString
