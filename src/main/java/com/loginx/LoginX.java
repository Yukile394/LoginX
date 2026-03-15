package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

public class LoginX extends JavaPlugin implements Listener {

    // ==================== DEĞİŞKENLER ====================
    private boolean chatMuted = false;
    private final Set<UUID> staffChatEnabled      = new HashSet<>();
    private final Set<UUID> vanishedPlayers        = new HashSet<>();
    private final Set<UUID> frozenPlayers          = new HashSet<>();
    private final Set<UUID> godModePlayers         = new HashSet<>();
    private final Set<UUID> spyModePlayers         = new HashSet<>();   // /spy – tüm komutları dinler
    private final Set<UUID> afkPlayers             = new HashSet<>();
    private final Map<UUID, Long> lastMessage      = new HashMap<>();   // Spam koruması
    private final Map<UUID, String> lastMsg        = new HashMap<>();   // Tekrar mesaj koruması
    private final Map<UUID, Location> lastLocation = new HashMap<>();   // /geri (back)
    private final Map<UUID, Integer> warnCount     = new HashMap<>();   // Uyarı sayısı
    private final Map<String, Long> muteExpiry     = new HashMap<>();   // Süreli mute (ism -> ms)
    private final Map<UUID, Long> afkTime          = new HashMap<>();   // AFK başlama zamanı

    private final List<String> yasakliKelimeler = Arrays.asList(
        "küfür1","küfür2","amk","aq","sg","orospu","piç","göt","sik",
        ".com",".net",".tr","discord.gg","http://","https://"
    );

    private final List<String> duyurular = Arrays.asList(
        "&#FFB6C1[Duyuru] &fSunucumuza destek olmak için mağazamıza göz atın!",
        "&#FFB6C1[Duyuru] &fKurallara uymayanları Discord üzerinden bildirebilirsiniz.",
        "&#FFB6C1[Duyuru] &fYetkili alımlarımız yakında başlayacaktır!",
        "&#FFB6C1[Duyuru] &f/help yazarak tüm komutları görebilirsiniz.",
        "&#FFB6C1[Duyuru] &fSunucu IP: play.sunucu.com"
    );
    private int duyuruIndex = 0;

    private FileConfiguration punishConfig;
    private File punishFile;

    // ==================== ENABLE / DISABLE ====================
    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPunishConfig();

        Bukkit.getPluginManager().registerEvents(this, this);

        try {
            LoginX2 modul = new LoginX2(this);
            Bukkit.getPluginManager().registerEvents(modul, this);
            getLogger().info("LoginX2 modulu basariyla aktif edildi!");
        } catch (Throwable t) {
            getLogger().warning("LoginX2 yuklenirken bir sorun olustu: " + t.getMessage());
        }

        startAutoBroadcaster();
        startAfkChecker();
        getLogger().info("LoginX v2.0 | Tum sistemler aktif!");
    }

    @Override
    public void onDisable() {
        savePunishConfig();
        getLogger().info("LoginX devre disi.");
    }

    // ==================== CONFIG ====================
    private void loadPunishConfig() {
        punishFile = new File(getDataFolder(), "punishments.yml");
        if (!punishFile.exists()) saveResource("punishments.yml", false);
        punishConfig = YamlConfiguration.loadConfiguration(punishFile);
    }
    private void savePunishConfig() {
        try { punishConfig.save(punishFile); } catch (Exception e) { e.printStackTrace(); }
    }

    // ==================== OTOMATİK DUYURU ====================
    private void startAutoBroadcaster() {
        new BukkitRunnable() {
            @Override public void run() {
                if (Bukkit.getOnlinePlayers().isEmpty()) return;
                Bukkit.broadcastMessage(color(duyurular.get(duyuruIndex)));
                duyuruIndex = (duyuruIndex + 1) % duyurular.size();
            }
        }.runTaskTimer(this, 20L * 60, 20L * 180);
    }

    // ==================== AFK CHECKER ====================
    private void startAfkChecker() {
        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    UUID uuid = p.getUniqueId();
                    if (!afkTime.containsKey(uuid)) { afkTime.put(uuid, now); continue; }
                    long idle = now - afkTime.get(uuid);
                    if (idle >= 5 * 60 * 1000L && !afkPlayers.contains(uuid)) {
                        afkPlayers.add(uuid);
                        Bukkit.broadcastMessage(color("&#FFA500[AFK] &e" + p.getName() + " &7artık AFK modunda."));
                    }
                }
            }
        }.runTaskTimer(this, 20L * 30, 20L * 30);
    }

    // ==================== GİRİŞ / ÇIKIŞ ====================
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        e.setJoinMessage(color("&#00FF00[+] &7" + p.getName() + " sunucuya katıldı! &8[" + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers() + "]"));
        p.sendTitle(color("&#FF69B4&lHOŞ GELDİN"), color("&f" + p.getName() + ", umarım keyifli vakit geçirirsin!"), 10, 70, 10);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        afkTime.put(p.getUniqueId(), System.currentTimeMillis());

        // İlk kez giriyorsa özel mesaj
        if (!p.hasPlayedBefore()) {
            Bukkit.broadcastMessage(color("&#FFD700✦ &e" + p.getName() + " &7sunucuya ilk kez katıldı! Hoş geldin!"));
        }

        // Vanished oyunculara görünmez tab
        for (Player v : Bukkit.getOnlinePlayers()) {
            if (vanishedPlayers.contains(v.getUniqueId()) && !p.hasPermission("loginx.staff")) {
                p.hidePlayer(this, v);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        e.setQuitMessage(color("&#FF0000[-] &7" + p.getName() + " sunucudan ayrıldı."));
        staffChatEnabled.remove(p.getUniqueId());
        vanishedPlayers.remove(p.getUniqueId());
        frozenPlayers.remove(p.getUniqueId());
        godModePlayers.remove(p.getUniqueId());
        afkPlayers.remove(p.getUniqueId());
        afkTime.remove(p.getUniqueId());
        lastLocation.remove(p.getUniqueId());
    }

    // ==================== HAREKET – AFK SIFIRLA ====================
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        // Frozen kontrolü
        if (frozenPlayers.contains(uuid)) {
            Location from = e.getFrom();
            e.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(), e.getTo().getYaw(), e.getTo().getPitch()));
            return;
        }

        // AFK sıfırla
        if (e.getFrom().getBlockX() != e.getTo().getBlockX()
         || e.getFrom().getBlockZ() != e.getTo().getBlockZ()
         || e.getFrom().getBlockY() != e.getTo().getBlockY()) {
            afkTime.put(uuid, System.currentTimeMillis());
            if (afkPlayers.remove(uuid)) {
                Bukkit.broadcastMessage(color("&#00FF00[AFK] &a" + p.getName() + " &7artık AFK modunda değil."));
            }
        }
    }

    // ==================== SOHBET ====================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        String msg = e.getMessage();
        String msgLower = msg.toLowerCase();
        UUID uuid = p.getUniqueId();

        // Staff Chat
        if (staffChatEnabled.contains(uuid)) {
            e.setCancelled(true);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission("loginx.staff"))
                    online.sendMessage(color("&#FFA500[SC] &f" + p.getName() + ": &e" + msg));
            }
            Bukkit.getConsoleSender().sendMessage(color("&#FFA500[SC] " + p.getName() + ": " + msg));
            return;
        }

        // Chat Kapalı
        if (chatMuted && !p.hasPermission("loginx.admin")) {
            e.setCancelled(true);
            p.sendMessage(color("&#FF0000[!] &cSohbet şu anda kapalı!")); return;
        }

        // Susturulmuş mu?
        if (isMuted(p.getName()) && !p.hasPermission("loginx.admin")) {
            e.setCancelled(true);
            long kalan = (muteExpiry.getOrDefault(p.getName(), 0L) - System.currentTimeMillis()) / 1000;
            p.sendMessage(color("&#FF0000[!] &cSusturuldunuz! Kalan süre: &e" + kalan + "s")); return;
        }

        // Spam koruması (1 saniye)
        if (!p.hasPermission("loginx.admin")) {
            long now = System.currentTimeMillis();
            if (lastMessage.containsKey(uuid) && now - lastMessage.get(uuid) < 1000) {
                e.setCancelled(true);
                p.sendMessage(color("&#FF0000[!] &cÇok hızlı mesaj gönderiyorsunuz!")); return;
            }
            // Aynı mesaj tekrarı
            if (lastMsg.containsKey(uuid) && lastMsg.get(uuid).equalsIgnoreCase(msg)) {
                e.setCancelled(true);
                p.sendMessage(color("&#FF0000[!] &cAynı mesajı tekrar gönderemezsiniz!")); return;
            }
            lastMessage.put(uuid, now);
            lastMsg.put(uuid, msg);
        }

        // Küfür & Reklam
        if (!p.hasPermission("loginx.admin")) {
            for (String yasakli : yasakliKelimeler) {
                if (msgLower.contains(yasakli)) {
                    e.setCancelled(true);
                    p.sendMessage(color("&#FF0000[!] &cMesajınız yasaklı içerik içerdiği için engellendi!"));
                    // Yetkiliye bildir
                    for (Player staff : Bukkit.getOnlinePlayers()) {
                        if (staff.hasPermission("loginx.staff"))
                            staff.sendMessage(color("&#FF4500[Filtre] &c" + p.getName() + " &7yasaklı kelime kullandı: &f" + msg));
                    }
                    return;
                }
            }
        }

        // CAPS koruması (yüzde 70'den fazla büyük harf)
        if (!p.hasPermission("loginx.admin") && msg.length() > 6) {
            long capsCount = msg.chars().filter(Character::isUpperCase).count();
            if ((double) capsCount / msg.length() > 0.7) {
                e.setMessage(msg.toLowerCase());
                p.sendMessage(color("&#FFFF00[!] &eKapsüllü yazılar otomatik küçültüldü."));
            }
        }

        // Spy modu: diğer oyuncuların mesajlarını OP görebilir (zaten görüyor ama özel format)
        e.setFormat(color("&#AAAAAA[&f" + p.getName() + "&#AAAAAA] &f%2$s"));
    }

    // ==================== HASAR – GOD MODE ====================
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            if (godModePlayers.contains(p.getUniqueId())) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            if (vanishedPlayers.contains(p.getUniqueId())) e.setCancelled(true);
        }
    }

    // ==================== ÖLÜM – EŞYA KAYIT ====================
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        // Ölüm konumunu kaydet (iade için LoginX2'ye aktarılıyor)
        lastLocation.put(p.getUniqueId(), p.getLocation());
        e.setDeathMessage(color("&#FF4500☠ &c" + p.getName() + " &7öldü! " + (p.getKiller() != null ? "&c[" + p.getKiller().getName() + " tarafından]" : "")));
    }

    // ==================== ANTI-GRIEF ====================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Material type = e.getBlock().getType();
        if (!p.hasPermission("loginx.build")) {
            Set<Material> yasakli = Set.of(Material.TNT, Material.BEDROCK, Material.LAVA, Material.FIRE,
                Material.DISPENSER, Material.DROPPER, Material.OBSERVER, Material.PISTON);
            if (yasakli.contains(type)) {
                e.setCancelled(true);
                p.sendMessage(color("&#FF0000[!] &cBu bloğu koymak için yetkiniz yok!"));
            }
        }
    }

    // ==================== SPY – KOMUT DİNLEYİCİ ====================
    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String cmd = e.getMessage();
        for (Player spy : Bukkit.getOnlinePlayers()) {
            if (spyModePlayers.contains(spy.getUniqueId()) && !spy.equals(p)) {
                spy.sendMessage(color("&#888888[Spy] &7" + p.getName() + ": &f" + cmd));
            }
        }
    }

    // ==================== KOMUTLAR ====================
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase();

        switch (name) {

            // --- /sil ---
            case "sil" -> {
                if (!perm(sender, "loginx.admin")) return true;
                for (int i = 0; i < 100; i++) Bukkit.broadcastMessage("");
                Bukkit.broadcastMessage(color("&#00FF00[!] &aSohbet &f" + sender.getName() + " &atarafından temizlendi!"));
                log("CLEARCHAT", sender.getName(), "-");
            }

            // --- /sustur ---
            case "sustur" -> {
                if (!perm(sender, "loginx.admin")) return true;
                chatMuted = !chatMuted;
                String durum = chatMuted ? "&#FF0000KAPATILDI" : "&#00FF00AÇILDI";
                Bukkit.broadcastMessage(color("[!] Sohbet " + durum + color(" &7(" + sender.getName() + ")")));
                log("MUTECHAT", sender.getName(), chatMuted ? "muted" : "unmuted");
            }

            // --- /sc ---
            case "sc" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("Sadece oyuncular!"); return true; }
                if (!perm(sender, "loginx.staff")) return true;
                UUID uuid = p.getUniqueId();
                boolean now = staffChatEnabled.contains(uuid);
                if (now) { staffChatEnabled.remove(uuid); p.sendMessage(color("&#FF0000[SC] Yetkili sohbetinden çıktınız.")); }
                else      { staffChatEnabled.add(uuid);    p.sendMessage(color("&#00FF00[SC] Yetkili sohbetine girdiniz.")); }
            }

            // --- /vanish ---
            case "vanish", "v" -> {
                if (!(sender instanceof Player p)) return true;
                if (!perm(sender, "loginx.staff")) return true;
                UUID uuid = p.getUniqueId();
                if (vanishedPlayers.contains(uuid)) {
                    vanishedPlayers.remove(uuid);
                    for (Player online : Bukkit.getOnlinePlayers()) online.showPlayer(this, p);
                    p.sendMessage(color("&#FF0000[Vanish] Artık görünürsünüz."));
                } else {
                    vanishedPlayers.add(uuid);
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (!online.hasPermission("loginx.staff")) online.hidePlayer(this, p);
                    }
                    p.sendMessage(color("&#00FFFF[Vanish] Artık görünmezsiniz."));
                }
            }

            // --- /dondur ---
            case "dondur", "freeze" -> {
                if (!perm(sender, "loginx.admin")) return true;
                if (args.length == 0) { sender.sendMessage(color("&#FF0000Kullanım: /dondur <oyuncu>")); return true; }
                Player hedef = Bukkit.getPlayer(args[0]);
                if (hedef == null) { sender.sendMessage(color("&#FF0000Oyuncu bulunamadı!")); return true; }
                UUID uuid = hedef.getUniqueId();
                if (frozenPlayers.contains(uuid)) {
                    frozenPlayers.remove(uuid);
                    hedef.sendMessage(color("&#00FF00Artık hareket edebilirsiniz."));
                    sender.sendMessage(color("&#00FF00" + hedef.getName() + " çözüldü."));
                } else {
                    frozenPlayers.add(uuid);
                    hedef.sendMessage(color("&#FF0000Bir yetkili sizi dondurdu! Hareket edemezsiniz."));
                    sender.sendMessage(color("&#00FF00" + hedef.getName() + " donduruldu."));
                }
                log("FREEZE", sender.getName(), hedef.getName());
            }

            // --- /god ---
            case "god" -> {
                if (!perm(sender, "loginx.admin")) return true;
                Player hedef = (args.length > 0) ? Bukkit.getPlayer(args[0]) : (sender instanceof Player p ? p : null);
                if (hedef == null) { sender.sendMessage(color("&#FF0000Oyuncu bulunamadı!")); return true; }
                UUID uuid = hedef.getUniqueId();
                if (godModePlayers.contains(uuid)) {
                    godModePlayers.remove(uuid);
                    hedef.sendMessage(color("&#FF0000God modu kapatıldı."));
                } else {
                    godModePlayers.add(uuid);
                    hedef.sendMessage(color("&#00FF00God modu aktif!"));
                }
            }

            // --- /spy ---
            case "spy" -> {
                if (!(sender instanceof Player p)) return true;
                if (!perm(sender, "loginx.admin")) return true;
                UUID uuid = p.getUniqueId();
                if (spyModePlayers.contains(uuid)) {
                    spyModePlayers.remove(uuid); p.sendMessage(color("&#FF0000[Spy] Spy modu kapatıldı."));
                } else {
                    spyModePlayers.add(uuid);    p.sendMessage(color("&#00FFFF[Spy] Tüm komutları ve sohbeti izliyorsunuz."));
                }
            }

            // --- /afk ---
            case "afk" -> {
                if (!(sender instanceof Player p)) return true;
                UUID uuid = p.getUniqueId();
                if (afkPlayers.contains(uuid)) {
                    afkPlayers.remove(uuid); afkTime.put(uuid, System.currentTimeMillis());
                    Bukkit.broadcastMessage(color("&#00FF00[AFK] &a" + p.getName() + " &7artık AFK değil."));
                } else {
                    afkPlayers.add(uuid);
                    Bukkit.broadcastMessage(color("&#FFA500[AFK] &e" + p.getName() + " &7AFK moduna geçti."));
                }
            }

            // --- /uyar ---
            case "uyar", "warn" -> {
                if (!perm(sender, "loginx.admin")) return true;
                if (args.length < 2) { sender.sendMessage(color("&#FF0000Kullanım: /uyar <oyuncu> <sebep>")); return true; }
                Player hedef = Bukkit.getPlayer(args[0]);
                if (hedef == null) { sender.sendMessage(color("&#FF0000Oyuncu bulunamadı!")); return true; }
                String sebep = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                int sayi = warnCount.getOrDefault(hedef.getUniqueId(), 0) + 1;
                warnCount.put(hedef.getUniqueId(), sayi);
                hedef.sendMessage(color("&#FF4500⚠ Uyarıldınız! (&c" + sayi + "/3&f) Sebep: &e" + sebep));
                Bukkit.broadcastMessage(color("&#FF4500[Uyarı] &c" + hedef.getName() + " &7uyarıldı! Sebep: &f" + sebep + " &8(" + sayi + "/3)"));
                if (sayi >= 3) {
                    hedef.kickPlayer(color("&#FF0000Çok fazla uyarı aldınız! Sunucudan atıldınız."));
                    warnCount.remove(hedef.getUniqueId());
                }
                log("WARN", sender.getName(), hedef.getName() + " - " + sebep);
            }

            // --- /uyarlar ---
            case "uyarlar"                
