package com.loginx;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoginX extends JavaPlugin implements Listener {

    // ─────────────────────────────────────────────
    //  ALANLAR
    // ─────────────────────────────────────────────
    private final HashMap<UUID, Long>              elytraBanned    = new HashMap<>();
    private final List<DeathRecord>                deathRecords    = new ArrayList<>();
    private int flopTick = 0;

    // Kasa
    private final HashMap<String, Location>        crates          = new HashMap<>();
    private final HashMap<String, List<ItemStack>> crateItems      = new HashMap<>();
    private final HashMap<String, Boolean>         crateEffects    = new HashMap<>();
    private final HashMap<String, String>          crateEffectType = new HashMap<>();
    private final HashMap<String, List<Float>>     crateChances    = new HashMap<>();
    // Kasa hologram entity id'leri
    private final HashMap<String, List<Integer>>   crateHolograms  = new HashMap<>();
    private NamespacedKey keyTag;

    // Kumar menüsü
    private final HashMap<UUID, CrateSession> activeSessions    = new HashMap<>();
    private final HashMap<String, Integer>    crateSpinDuration = new HashMap<>();
    private static final int DEFAULT_SPIN_TICKS = 40;

    // Yanlış anahtar spam engeli
    private final HashMap<UUID, Long> wrongKeyCooldown = new HashMap<>();
    private static final long WRONG_KEY_COOLDOWN_MS = 3000L;

    // ─── 1v1 Sistemi ───
    private final HashMap<UUID, Location>   wand1Pos       = new HashMap<>();
    private final HashMap<UUID, Location>   wand2Pos       = new HashMap<>();
    private final HashMap<String, UUID[]>   arenaPlayers   = new HashMap<>(); // arenaId -> [p1, p2]
    private final HashMap<UUID, String>     playerArena    = new HashMap<>(); // uid -> arenaId
    private final HashMap<UUID, BoundingBox> arenaBounds   = new HashMap<>(); // p1 uid -> bbox
    private final HashMap<UUID, Material>   arenaBlock     = new HashMap<>(); // p1 uid -> block
    private int arenaCounter = 0;

    // ─── Koruma Sistemi ───
    private final HashMap<UUID, Long>    protectedUntil = new HashMap<>();
    private static final long PROTECTION_MS = 300_000L; // 5 dakika
    private final HashMap<UUID, Integer> protectionTask = new HashMap<>();
    private final HashMap<UUID, Boolean> pendingProtOff  = new HashMap<>();

    // ─── Stash Sistemi ───
    private final HashMap<UUID, ItemStack[]> playerStash = new HashMap<>();
    private final HashMap<UUID, Boolean>     stashOpen   = new HashMap<>();

    // ─── Bakım Sistemi ───
    private boolean bakimModu = false;
    private final Set<UUID> bakimIzinli = new HashSet<>(); // bakımdayken girebilecek oyuncular

    // ─── Cooldown Hızı Sistemi ───
    private final HashMap<UUID, Double> itemCooldownSpeed = new HashMap<>();
    private NamespacedKey cooldownSpeedTag;

    // ─── 1v1 Ayarla Sistemi (eski - kaldırıldı, /1v1randomsil ile silinir) ───
    private final HashMap<UUID, Location>      ayarlaPos1        = new HashMap<>();
    private final HashMap<UUID, Location>      ayarlaPos2        = new HashMap<>();
    private final HashMap<String, UUID>        ayarlaArenas      = new HashMap<>();
    private final HashMap<String, BoundingBox> ayarlaBounds      = new HashMap<>();
    private final HashMap<String, UUID[]>      ayarlaQueue       = new HashMap<>();
    private final HashMap<UUID, String>        playerAyarlaArena = new HashMap<>();
    private final HashMap<UUID, ItemStack>     ayarlaItem1       = new HashMap<>();
    private final HashMap<UUID, ItemStack>     ayarlaItem2       = new HashMap<>();
    private final HashMap<String, ItemStack[]> ayarlaWalls       = new HashMap<>();
    private final HashMap<String, Map<String, Material>> ayarlaOriginalBlocks = new HashMap<>(); // arenaId -> konum->eski blok

    // ─── DUEL Sistemi ───
    // DuelArena: id, spawn1, spawn2, dolu mu
    private final HashMap<String, Location>  duelSpawn1    = new HashMap<>(); // duelId -> spawn1
    private final HashMap<String, Location>  duelSpawn2    = new HashMap<>(); // duelId -> spawn2
    private final HashMap<String, UUID[]>    duelPlayers   = new HashMap<>(); // duelId -> [p1,p2] (aktif)
    private final HashMap<UUID, String>      playerDuel    = new HashMap<>(); // uid -> duelId
    // Duel ayarları (oyuncu bazlı)
    private final HashMap<UUID, Boolean>     duelWebEnabled    = new HashMap<>(); // ağ kullan mı
    private final HashMap<UUID, Boolean>     duelElyEnabled    = new HashMap<>(); // elytra kullan mı
    private final HashMap<UUID, Boolean>     duelLadderEnabled = new HashMap<>(); // merdiven kullan mı
    private final HashMap<UUID, Boolean>     duelOakEnabled    = new HashMap<>(); // meşe odun kullan mı
    private final HashMap<UUID, Boolean>     duelStickEnabled  = new HashMap<>(); // tahta (meşe hariç) kullan mı
    private final HashMap<UUID, Boolean>     duelChestEnabled  = new HashMap<>(); // göğüslük kullan mı
    // Duel davetiyeleri
    private final HashMap<UUID, UUID>        duelInvite       = new HashMap<>(); // davet alan -> gönderen
    private final HashMap<UUID, Boolean>     duelInviteWeb    = new HashMap<>();
    private final HashMap<UUID, Boolean>     duelInviteEly    = new HashMap<>();
    private final HashMap<UUID, Boolean>     duelInviteLadder = new HashMap<>();
    private final HashMap<UUID, Boolean>     duelInviteOak    = new HashMap<>();
    private final HashMap<UUID, Boolean>     duelInviteStick  = new HashMap<>();
    private final HashMap<UUID, Boolean>     duelInviteChest  = new HashMap<>();
    private final HashMap<UUID, Integer>     duelInviteTask   = new HashMap<>(); // timeout task
    // /duel <oyuncu> ön-ayar menüsü: davet gönderilmeden önce seçilen hedef
    private final HashMap<UUID, UUID>        duelPendingTarget = new HashMap<>();
    // Aktif dueldeki kurallar: arenaId -> [web, ely, merdiven, meşe, tahta, göğüslük]
    private final HashMap<String, boolean[]> duelArenaRules    = new HashMap<>();
    // Duel countdown/freeze
    private final HashMap<UUID, Boolean>     duelFrozen    = new HashMap<>();
    // ─── /duelspawnayarla ile ayarlanan özel duel/1v1 spawn noktası ───
    private Location duelOzelSpawn = null;
    // ─── Duel berabere teklifi: sadece dueldeyken kullanılabilir ───
    private final HashMap<UUID, UUID> duelDrawRequest = new HashMap<>(); // teklif alan -> teklif eden

    // ─── Duel Sandığı (loot chest) — gerçek sandık değil, sağ tıkla açılan sahte GUI itemi ───
    private NamespacedKey lootChestTag;   // bu item bir duel sandığı mı (marker)
    private NamespacedKey lootChestData;  // içindeki eşyaların serileştirilmiş (Base64) hali
    private NamespacedKey lootChestOwner; // eşyaların sahibinin adı (lore/yeniden oluşturma için)

    // ─── Duel/1v1 Room ölüm sonrası komut kilidi: uid -> ölüm zamanı (ms) ───
    private final HashMap<UUID, Long> duelDeathTime = new HashMap<>();
    private static final long DUEL_DEATH_LOCK_MS       = 10_000L; // ölümden sonraki ilk 10 sn: hiçbir komut yok
    private static final long DUEL_DEATH_ECPV_WINDOW_MS = 60_000L; // sonraki 60 sn: sadece /ec ve /pv

    // ─── Kontrol (Kont) Sistemi ───
    private Location kontYeri = null;
    private final HashMap<UUID, Long>         kontUntil       = new HashMap<>();
    private final HashMap<UUID, UUID>         kontYetkilisi   = new HashMap<>();
    private final HashMap<UUID, Integer>      kontTimeoutTask = new HashMap<>();
    private final HashMap<UUID, ItemStack[]>  kontSavedInv    = new HashMap<>();
    private final HashMap<String, Location>   kontYetkiliYeri = new HashMap<>(); // yetkili adı → konum
    private NamespacedKey kontTag;

    // ─── /izinver sistemi ───
    private final Set<UUID> izinliOyuncular = new HashSet<>();

    // ─── Login/Register şifre kayıt ───
    private final HashMap<String, String> oyuncuSifre = new HashMap<>(); // isim → şifre

    // ─── Spam / Küfür Sistemi ───
    private final HashMap<UUID, List<Long>>  spamMessages    = new HashMap<>();
    private final HashMap<UUID, Integer>     spamWarnCount   = new HashMap<>();
    private final HashMap<UUID, Long>        spamMuted       = new HashMap<>();
    private final HashMap<UUID, Integer>     kufurWarnCount  = new HashMap<>();
    private final HashMap<UUID, Long>        kufurMuted      = new HashMap<>();
    private static final long SPAM_WINDOW_MS  = 3000L;  // 3 saniyede kaç mesaj
    private static final int  SPAM_THRESHOLD  = 3;       // bu kadar mesajda uyar
    private static final long MUTE_DURATION_MS = 600_000L; // 10 dakika
    private static final Set<String> KUFUR_LIST = new HashSet<>(Arrays.asList(
        "orospu","orospu çocuğu","oç","göt","amk","amına","sik","sikerim","siktir",
        "piç","yarrak","ibne","it","bok","kahpe","gerizekalı","salak","aptal","mal"
    ));

    // ─── İade Log Sistemi ───
    // IadeLog: kim verdi, kime, ne zaman, hangi kayıt id
    private static class IadeLog {
        String adminName, playerName, date, recordId;
        IadeLog(String adminName, String playerName, String recordId) {
            this.adminName = adminName; this.playerName = playerName; this.recordId = recordId;
            this.date = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date());
        }
    }
    private final List<IadeLog> iadeLogs = new ArrayList<>();

    // ─── Kont Komut Whitelist (oyuncu için) ───
    private static final Set<String> KONT_ALLOWED_CMDS = new HashSet<>(Arrays.asList(
        "itiraf", "anydesk", "discord2"
    ));

    // Renk paletleri
    private static final Map<String, String[]> CRATE_COLORS = new LinkedHashMap<>();
    static {
        CRATE_COLORS.put("afk",     new String[]{"&#aaaaaa","&#cccccc","&#ffffff","&#eeeeee","&#dddddd","&#bbbbbb"});
        CRATE_COLORS.put("casino",  new String[]{"&#ff2200","&#ff6600","&#ffaa00","&#ffdd00","&#ff6600","&#ff2200"});
        CRATE_COLORS.put("gear",    new String[]{"&#ff8800","&#ffaa00","&#ffdd00","&#ffaa00","&#ff8800","&#ff5500"});
        CRATE_COLORS.put("booster", new String[]{"&#00aa44","&#00cc66","&#00ff88","&#00cc66","&#00aa44","&#008833"});
        CRATE_COLORS.put("all",     new String[]{"&#ff0000","&#ff4444","&#ff8888","&#ffffff","&#ff8888","&#ff4444","&#ff0000","&#cc0000"});
    }

    private static final Map<String, String> KEY_BASE_NAMES = new LinkedHashMap<>();
    static {
        KEY_BASE_NAMES.put("afk",     "Afk Key");
        KEY_BASE_NAMES.put("casino",  "Casino Key");
        KEY_BASE_NAMES.put("gear",    "Gear Key");
        KEY_BASE_NAMES.put("booster", "Booster Key");
        KEY_BASE_NAMES.put("all",     "Key All");
    }

    private static final int SPIN_SLOTS  = 45;
    private static final int RESULT_SLOT = 22;

    // ─────────────────────────────────────────────
    //  INNER: CrateSession
    // ─────────────────────────────────────────────
    private static class CrateSession {
        Inventory inv; ItemStack reward; int tick; boolean done; String crateName; int spinDuration;
        CrateSession(Inventory inv, ItemStack reward, String crateName, int spinDuration) {
            this.inv = inv; this.reward = reward; this.tick = 0;
            this.done = false; this.crateName = crateName; this.spinDuration = spinDuration;
        }
    }

    // ─────────────────────────────────────────────
    //  INNER: DeathRecord
    // ─────────────────────────────────────────────
    private static class DeathRecord {
        String id, playerName, killerName, date; UUID playerUUID; Location loc; ItemStack[] items;
        DeathRecord(Player p, String killer, ItemStack[] itms) {
            this.id = Integer.toHexString(new Random().nextInt(0xFFFF)).toUpperCase();
            this.playerUUID = p.getUniqueId(); this.playerName = p.getName();
            this.killerName = killer; this.loc = p.getLocation(); this.items = itms;
            this.date = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date());
        }
    }

    // ─────────────────────────────────────────────
    //  ENABLE / DISABLE
    // ─────────────────────────────────────────────
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        keyTag = new NamespacedKey(this, "kasa_anahtari");
        cooldownSpeedTag = new NamespacedKey(this, "cooldown_hizi");
        kontTag = new NamespacedKey(this, "kont_item");
        lootChestTag = new NamespacedKey(this, "duel_sandigi");
        lootChestData = new NamespacedKey(this, "duel_sandigi_veri");
        lootChestOwner = new NamespacedKey(this, "duel_sandigi_sahibi");
        saveDefaultConfig(); loadData();
        // Kont yeri config'den yükle
        if (getConfig().contains("kontyeri")) kontYeri = getConfig().getLocation("kontyeri");
        // Yetkili konumlarını yükle
        if (getConfig().contains("kontyetkili")) {
            for (String name : getConfig().getConfigurationSection("kontyetkili").getKeys(false)) {
                Location loc = getConfig().getLocation("kontyetkili." + name);
                if (loc != null) kontYetkiliYeri.put(name, loc);
            }
        }
        // İzinli oyuncuları yükle
        if (getConfig().contains("izinli")) {
            for (String uid2 : getConfig().getConfigurationSection("izinli").getKeys(false)) {
                try { izinliOyuncular.add(UUID.fromString(uid2)); } catch (Exception ignored) {}
            }
        }
        // Şifreleri yükle
        if (getConfig().contains("sifreler")) {
            for (String pName : getConfig().getConfigurationSection("sifreler").getKeys(false)) {
                oyuncuSifre.put(pName, getConfig().getString("sifreler." + pName));
            }
        }
        // Duel arenaları yükle
        if (getConfig().contains("duels")) {
            for (String did : getConfig().getConfigurationSection("duels").getKeys(false)) {
                duelSpawn1.put(did, getConfig().getLocation("duels." + did + ".spawn1"));
                duelSpawn2.put(did, getConfig().getLocation("duels." + did + ".spawn2"));
            }
        }
        // Özel duel/1v1 spawn noktası yükle
        if (getConfig().contains("duelozelspawn")) duelOzelSpawn = getConfig().getLocation("duelozelspawn");

        new BukkitRunnable() {
            @Override public void run() {
                flopTick++;
                updateCrateEffects();
                updateAnimatedMenus();
                tickAllSessions();
                tickCrateHolograms();
                tickProtectionCountdowns();
            }
        }.runTaskTimer(this, 0L, 3L);

        // ─── Sonsuz Gece Görüşü — 3 sn'de bir yenile, 60 sn süre — asla inmesin ───
        new BukkitRunnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.NIGHT_VISION,
                        1200,  // 60 saniye (her 3 sn'de yenilenir — asla bitmez)
                        0,     // seviye 1
                        false, // ambient: false
                        false, // particles: false
                        false  // icon: false
                    ), true);
                }
            }
        }.runTaskTimer(this, 0L, 60L); // her 3 saniyede bir

        getLogger().info("Fear Craft LoginX V2.0 Aktif!");
    }

    @Override
    public void onDisable() {
        // Hologramları temizle
        removeAllHolograms();
        saveData();
    }

    // ─────────────────────────────────────────────
    //  KOMUTLAR
    // ─────────────────────────────────────────────
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String cmdName = cmd.getName().toLowerCase();

        // Koruma komutları — tüm oyuncular kullanabilir
        if (cmdName.equals("korumasurem")) {
            if (!(sender instanceof Player p)) return true;
            long until = protectedUntil.getOrDefault(p.getUniqueId(), 0L);
            if (until <= System.currentTimeMillis()) {
                p.sendMessage(color("&#ff4444&lSilvera &8» &cAktif koruman bulunmuyor."));
                return true;
            }
            long kalan = until - System.currentTimeMillis();
            long saat  = kalan / 3_600_000L;
            long dak   = (kalan % 3_600_000L) / 60_000L;
            long sn    = (kalan % 60_000L) / 1_000L;
            p.sendMessage(color("&#00ccff&lSilvera Koruma &8» &fKorumanın Bitmesine &b"
                + saat + " saat " + dak + " dakika " + sn + " saniye &fkaldı"));
            return true;
        }

        if (cmdName.equals("korumakapat")) {
            if (!(sender instanceof Player p)) return true;
            long until = protectedUntil.getOrDefault(p.getUniqueId(), 0L);
            if (until <= System.currentTimeMillis()) {
                p.sendMessage(color("&#ff4444&lSilvera &8» &cAktif koruman bulunmuyor."));
                return true;
            }
            pendingProtOff.put(p.getUniqueId(), true);
            p.sendMessage(color("&#ffcc00&lSilvera &8» &fKorumani Kapatmak İstiyormusun?"));
            p.sendMessage(color("  &7Onaylamak için &e/evet &7yaz. &c(Bir daha giriş yapınca gelmez!)"));
            return true;
        }

        if (cmdName.equals("evet")) {
            if (!(sender instanceof Player p)) return true;
            if (pendingProtOff.getOrDefault(p.getUniqueId(), false)) {
                pendingProtOff.remove(p.getUniqueId());
                protectedUntil.remove(p.getUniqueId());
                // Kalıcı olarak iptal edildi işareti — artık login'de yeniden verilmez
                getConfig().set("protection_disabled." + p.getUniqueId().toString(), true);
                saveConfig();
                p.sendTitle(color("&#ff4444&lKoruma Kapatıldı!"), color("&#aaaaaa&oArtık savunmasızsın"), 5, 50, 10);
                p.sendMessage(color("&#ff4444&lSilvera &8» &cKoruman kalıcı olarak kapatıldı."));
            }
            return true;
        }

        // /1v1randomsil (isim) — ayarla arenasını sil
        if (cmdName.equals("1v1randomsil")) {
            if (!(sender instanceof Player p)) return true;
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(color("&cYetkisiz.")); return true; }
            if (args.length < 1) { p.sendMessage(color("&cKullanim: /1v1randomsil <arenaId>")); return true; }
            String id = args[0];
            if (!ayarlaArenas.containsKey(id)) { p.sendMessage(color("&cArena bulunamadı: " + id)); return true; }
            ayarlaArenas.remove(id); ayarlaBounds.remove(id);
            ayarlaQueue.remove(id); ayarlaWalls.remove(id);
            for (UUID uid2 : new HashSet<>(playerAyarlaArena.keySet()))
                if (id.equals(playerAyarlaArena.get(uid2))) playerAyarlaArena.remove(uid2);
            p.sendMessage(color("&#ff4444&l1v1 &8» &f" + id + " &c1v1 arenası silindi."));
            return true;
        }

        // ─────────────────────────────────────────────
        //  DUEL SİSTEMİ KOMUTLARİ
        // ─────────────────────────────────────────────

        // /duelolustur (id örn: #1)
        if (cmdName.equals("duelolustur")) {
            if (!(sender instanceof Player p)) return true;
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(color("&cYetkisiz.")); return true; }
            if (args.length < 1) { p.sendMessage(color("&cKullanim: /duelolustur <id örn: #1>")); return true; }
            String did = args[0];
            if (duelSpawn1.containsKey(did)) { p.sendMessage(color("&cBu ID zaten var: " + did + " — /duelsil ile sil.")); return true; }
            duelSpawn1.put(did, null); duelSpawn2.put(did, null);
            getConfig().set("duels." + did + ".id", did); saveConfig();
            p.sendMessage(color("&#00ff88&l[DUEL] &f" + did + " &7oluşturuldu. Şimdi &e/duelyeriekle1 " + did + " &7ve &e/duelyeriekle2 " + did + " &7yaz."));
            return true;
        }

        // /duelsil (id)
        if (cmdName.equals("duelsil")) {
            if (!(sender instanceof Player p)) return true;
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(color("&cYetkisiz.")); return true; }
            if (args.length < 1) { p.sendMessage(color("&cKullanim: /duelsil <id>")); return true; }
            String did = args[0];
            duelSpawn1.remove(did); duelSpawn2.remove(did);
            UUID[] dp = duelPlayers.remove(did);
            duelArenaRules.remove(did);
            if (dp != null) for (UUID u : dp) playerDuel.remove(u);
            getConfig().set("duels." + did, null); saveConfig();
            p.sendMessage(color("&#ff4444&l[DUEL] &f" + did + " &c silindi."));
            return true;
        }

        // /duelyeriekle1 (duelid)
        if (cmdName.equals("duelyeriekle1")) {
            if (!(sender instanceof Player p)) return true;
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(color("&cYetkisiz.")); return true; }
            if (args.length < 1) { p.sendMessage(color("&cKullanim: /duelyeriekle1 <id>")); return true; }
            String did = args[0];
            if (!duelSpawn1.containsKey(did)) { p.sendMessage(color("&cDuel bulunamadı. Önce /duelolustur " + did)); return true; }
            duelSpawn1.put(did, p.getLocation().clone());
            getConfig().set("duels." + did + ".spawn1", p.getLocation()); saveConfig();
            p.sendMessage(color("&#00ff88&l[DUEL] &f" + did + " &7— Spawn 1 ayarlandı: &b" + p.getLocation().getBlockX() + "," + p.getLocation().getBlockY() + "," + p.getLocation().getBlockZ()));
            return true;
        }

        // /duelyeriekle2 (duelid)
        if (cmdName.equals("duelyeriekle2")) {
            if (!(sender instanceof Player p)) return true;
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(color("&cYetkisiz.")); return true; }
            if (args.length < 1) { p.sendMessage(color("&cKullanim: /duelyeriekle2 <id>")); return true; }
            String did = args[0];
            if (!duelSpawn2.containsKey(did)) { p.sendMessage(color("&cDuel bulunamadı. Önce /duelolustur " + did)); return true; }
            duelSpawn2.put(did, p.getLocation().clone());
            getConfig().set("duels." + did + ".spawn2", p.getLocation()); saveConfig();
            p.sendMessage(color("&#00ff88&l[DUEL] &f" + did + " &7— Spawn 2 ayarlandı: &b" + p.getLocation().getBlockX() + "," + p.getLocation().getBlockY() + "," + p.getLocation().getBlockZ()));
            return true;
        }

        // /dueller — duel arenalarını listele / gir
        if (cmdName.equals("dueller")) {
            if (!(sender instanceof Player p)) return true;
            openDuelListMenu(p); return true;
        }

        // /duelberabere — SADECE dueldeyken kullanılabilir. Rakibine berabere teklifi gönderir.
        if (cmdName.equals("duelberabere")) {
            if (!(sender instanceof Player p)) return true;
            String arenaId = playerDuel.get(p.getUniqueId());
            if (arenaId == null) { p.sendMessage(color("&#c94848&l[DUEL] &fBu komutu sadece dueldeyken kullanabilirsin!")); return true; }
            UUID[] dp = duelPlayers.get(arenaId);
            if (dp == null) { p.sendMessage(color("&#c94848&l[DUEL] &fDuel bulunamadı.")); return true; }
            Player opponent = null;
            for (UUID u : dp) if (!u.equals(p.getUniqueId())) opponent = Bukkit.getPlayer(u);
            if (opponent == null || !opponent.isOnline()) { p.sendMessage(color("&#c94848&l[DUEL] &fRakip çevrimdışı.")); return true; }

            duelDrawRequest.put(opponent.getUniqueId(), p.getUniqueId());

            net.md_5.bungee.api.chat.TextComponent btnDrawKabul = new net.md_5.bungee.api.chat.TextComponent(
                color("&#2ea86b&l[Berabere Kabul]"));
            btnDrawKabul.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/duelberaberekabul"));
            net.md_5.bungee.api.chat.TextComponent spaceDraw = new net.md_5.bungee.api.chat.TextComponent("  ");
            net.md_5.bungee.api.chat.TextComponent btnDrawReddet = new net.md_5.bungee.api.chat.TextComponent(
                color("&#c94848&l[Berabere Reddet]"));
            btnDrawReddet.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/duelberaberereddet"));

            opponent.sendMessage(color(""));
            opponent.sendMessage(color("  " + duelFlop("⚔ " + p.getName() + " berabere teklif etti!", true)));
            opponent.spigot().sendMessage(btnDrawKabul, spaceDraw, btnDrawReddet);
            opponent.sendMessage(color(""));
            opponent.playSound(opponent.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.3f);
            p.sendMessage(color("  " + duelFlop("Berabere teklifin " + opponent.getName() + " kişisine gönderildi.", false)));
            return true;
        }

        // /duelberaberekabul — berabere teklifini kabul et → ikisi de spawna ışınlanır
        if (cmdName.equals("duelberaberekabul")) {
            if (!(sender instanceof Player p)) return true;
            UUID requesterUid = duelDrawRequest.remove(p.getUniqueId());
            if (requesterUid == null) { p.sendMessage(color("&#c94848&l[DUEL] &fBekleyen bir berabere teklifin yok.")); return true; }
            String arenaId = playerDuel.get(p.getUniqueId());
            Player requester = Bukkit.getPlayer(requesterUid);
            if (arenaId == null || requester == null || !requester.isOnline()
                || !arenaId.equals(playerDuel.get(requesterUid))) {
                p.sendMessage(color("&#c94848&l[DUEL] &fDuel artık geçerli değil.")); return true;
            }
            UUID[] dp = duelPlayers.remove(arenaId);
            duelArenaRules.remove(arenaId);
            if (dp != null) for (UUID u : dp) { playerDuel.remove(u); duelFrozen.remove(u); }

            for (Player dpX : new Player[]{p, requester}) {
                if (dpX != null && dpX.isOnline()) {
                    dpX.sendTitle(
                        duelFlop("🤝 BERABERE!", true),
                        color("&#aaaaaa&oSpawna ışınlanıyorsunuz..."),
                        5, 60, 15);
                    dpX.sendMessage(color("  " + duelFlop("🤝 Duel berabere sonuçlandı!", true)));
                    dpX.playSound(dpX.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
                    teleportToSpawn(dpX);
                }
            }
            return true;
        }

        // /duelberaberereddet — berabere teklifini reddet
        if (cmdName.equals("duelberaberereddet")) {
            if (!(sender instanceof Player p)) return true;
            UUID requesterUid = duelDrawRequest.remove(p.getUniqueId());
            if (requesterUid == null) { p.sendMessage(color("&#c94848&l[DUEL] &fBekleyen bir berabere teklifin yok.")); return true; }
            Player requester = Bukkit.getPlayer(requesterUid);
            p.sendMessage(color("&#c94848&l[DUEL] &fBerabere teklifini reddettin."));
            if (requester != null && requester.isOnline())
                requester.sendMessage(color("&#c94848&l[DUEL] &f" + p.getName() + " &7berabere teklifini reddetti."));
            return true;
        }

        // /duelspawnayarla — durduğun bloğu duel/1v1 bitince ışınlanılacak özel spawn yap
        if (cmdName.equals("duelspawnayarla")) {
            if (!(sender instanceof Player p)) return true;
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(color("&cYetkisiz.")); return true; }
            duelOzelSpawn = p.getLocation().clone();
            getConfig().set("duelozelspawn", duelOzelSpawn); saveConfig();
            p.sendMessage(color("&#00ff88&l[DUEL SPAWN] &fDuel/1v1 bitince ışınlanılacak nokta ayarlandı: &b"
                + duelOzelSpawn.getBlockX() + ", " + duelOzelSpawn.getBlockY() + ", " + duelOzelSpawn.getBlockZ()));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.4f);
            return true;
        }

        // /duelayarlar — oyuncu kendi duel ayarlarını görür
        if (cmdName.equals("duelayarlar")) {
            if (!(sender instanceof Player p)) return true;
            openDuelSettingsMenu(p, false); return true;
        }

        // /duelayarlaradmin — yetkili duel ayarlarını görür
        if (cmdName.equals("duelayarlaradmin")) {
            if (!(sender instanceof Player p)) return true;
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(color("&cYetkisiz.")); return true; }
            openDuelSettingsMenu(p, true); return true;
        }

        // /1v1 (oyuncu) — duel daveti gönder (o anki kayıtlı kişisel ayarlarla)
        if (cmdName.equals("1v1")) {
            if (!(sender instanceof Player p)) return true;
            if (args.length < 1) { p.sendMessage(color("&cKullanim: /1v1 <oyuncu>")); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            sendDuelInvite(p, target, args.length > 0 ? args[0] : "");
            return true;
        }

        // /duel (oyuncu) — davet göndermeden önce ayar ekranını açar
        if (cmdName.equals("duel")) {
            if (!(sender instanceof Player p)) return true;
            if (args.length < 1) { p.sendMessage(color("&cKullanim: /duel <oyuncu>")); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || target.equals(p)) { p.sendMessage(color("&c" + args[0] + " çevrimiçi değil.")); return true; }
            if (playerDuel.containsKey(p.getUniqueId())) { p.sendMessage(color("&#ff4444&l[DUEL] &cZaten bir dueldasın.")); return true; }
            if (playerDuel.containsKey(target.getUniqueId())) { p.sendMessage(color("&#ff4444&l[DUEL] &c" + target.getName() + " zaten bir duelda.")); return true; }
            duelPendingTarget.put(p.getUniqueId(), target.getUniqueId());
            openDuelInviteMenu(p, target);
            return true;
        }

        // /1v1kabul
        if (cmdName.equals("1v1kabul")) {
            if (!(sender instanceof Player p)) return true;
            UUID inviterUid = duelInvite.get(p.getUniqueId());
            if (inviterUid == null) { p.sendMessage(color("&cBekleyen duel davetin yok.")); return true; }
            Player inviter = Bukkit.getPlayer(inviterUid);
            if (inviter == null) { p.sendMessage(color("&cDavet eden oyuncu çevrimdışı.")); duelInvite.remove(p.getUniqueId()); return true; }
            cancelDuelInviteTimeout(p.getUniqueId());
            boolean[] rules = new boolean[]{
                duelInviteWeb.getOrDefault(p.getUniqueId(), false),
                duelInviteEly.getOrDefault(p.getUniqueId(), false),
                duelInviteLadder.getOrDefault(p.getUniqueId(), false),
                duelInviteOak.getOrDefault(p.getUniqueId(), false),
                duelInviteStick.getOrDefault(p.getUniqueId(), false),
                duelInviteChest.getOrDefault(p.getUniqueId(), false)
            };
            // ── Elytra engellemesi açıkken (ely kapalı) taraflardan birinde Elytra varsa duel başlamasın ──
            if (!rules[1] && (hasElytraInInventory(inviter) || hasElytraInInventory(p))) {
                String culprit = hasElytraInInventory(p) ? p.getName() : inviter.getName();
                p.sendMessage(color("&#ff4444&l[DUEL] &fBu duelde Elytra kapalı! &f" + culprit + " &7üzerinde/envanterinde Elytra olduğu sürece duel başlayamaz."));
                inviter.sendMessage(color("&#ff4444&l[DUEL] &fBu duelde Elytra kapalı! &f" + culprit + " &7üzerinde/envanterinde Elytra olduğu sürece duel başlayamaz."));
                return true;
            }
            duelInvite.remove(p.getUniqueId());
            duelInviteWeb.remove(p.getUniqueId()); duelInviteEly.remove(p.getUniqueId());
            duelInviteLadder.remove(p.getUniqueId()); duelInviteOak.remove(p.getUniqueId());
            duelInviteStick.remove(p.getUniqueId()); duelInviteChest.remove(p.getUniqueId());
            String freeArena = findFreeDuelArena();
            if (freeArena == null) { p.sendMessage(color("&#ff4444&l[DUEL] &cBoş arena kalmadı!")); return true; }
            startDuel(inviter, p, freeArena, rules);
            return true;
        }

        // /1v1reddet
        if (cmdName.equals("1v1reddet")) {
            if (!(sender instanceof Player p)) return true;
            UUID inviterUid = duelInvite.remove(p.getUniqueId());
            if (inviterUid == null) { p.sendMessage(color("&cBekleyen duel davetin yok.")); return true; }
            cancelDuelInviteTimeout(p.getUniqueId());
            duelInviteWeb.remove(p.getUniqueId()); duelInviteEly.remove(p.getUniqueId());
            duelInviteLadder.remove(p.getUniqueId()); duelInviteOak.remove(p.getUniqueId());
            duelInviteStick.remove(p.getUniqueId()); duelInviteChest.remove(p.getUniqueId());
            Player inviter = Bukkit.getPlayer(inviterUid);
            p.sendMessage(color("&#ff4444&l[DUEL] &fDaveti reddettiniz."));
            if (inviter != null) inviter.sendMessage(color("&#ff4444&l[DUEL] &f" + p.getName() + " &7duel davetini reddetti."));
            return true;
        }

        // 1v1 wand
        if (cmdName.equals("1v1wand")) {
            if (!(sender instanceof Player p)) return true;
            ItemStack wand = new ItemStack(Material.GOLDEN_AXE);
            ItemMeta wm = wand.getItemMeta();
            wm.setDisplayName(color("&#ff6600&l⚔ &r&#ffcc00&l1v1 Wand &r&#ff6600&l⚔"));
            List<String> wl = new ArrayList<>();
            wl.add(color("&#555555▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            wl.add(color("  &7Sağ tık &f→ &bBirinci köşe"));
            wl.add(color("  &7Sol tık &f→ &eBirinci köşe'den ikinci köşe"));
            wl.add(color("  &7İki köşe seçtikten sonra arena oluşur"));
            wl.add(color("&#555555▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            wm.setLore(wl);
            wand.setItemMeta(wm);
            p.getInventory().addItem(wand);
            p.sendMessage(color("&#ffcc00&l1v1 &8» &f1v1 Wand envanterine eklendi!"));
            return true;
        }

        // /1v1ayarla — wand gibi ama orda kalır, 2 kişi girince sol/sağ tık itemleriyle kapanır
        if (cmdName.equals("1v1ayarla")) {
            if (!(sender instanceof Player p)) return true;
            ItemStack wand = new ItemStack(Material.BLAZE_ROD);
            ItemMeta wm = wand.getItemMeta();
            wm.setDisplayName(color("&#ff4400&l⚙ &r&#ffaa00&l1v1 Ayarla &r&#ff4400&l⚙"));
            List<String> wl = new ArrayList<>();
            wl.add(color("&#553300▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            wl.add(color("  &7Sağ tık &f→ &bBirinci köşeyi seç"));
            wl.add(color("  &7Sol tık &f→ &eİkinci köşeyi seç & arena kur"));
            wl.add(color("  &72 oyuncu girince arena kapanır"));
            wl.add(color("  &7Elinizde tuttuğunuz item duvar olur"));
            wl.add(color("&#553300▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            wm.setLore(wl);
            wand.setItemMeta(wm);
            p.getInventory().addItem(wand);
            p.sendMessage(color("&#ffaa00&l1v1 Ayarla &8» &fAyarla wand envanterine eklendi!"));
            return true;
        }

        // /stash, /stashac, /stashaç, /stashkapa — KALDIRILDI (talep üzerine)

        // ─── BAKIM SİSTEMİ ───

        // /bakimaa — op'suz herkesi atar, bakım modunu açar
        if (cmdName.equals("bakimaa")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("[BAKIM] Oyuncu komutudur."); return true; }
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(color("&cYetkisiz.")); return true; }
            bakimModu = true;
            getConfig().set("bakim.aktif", true); saveConfig();
            // Op olmayan ve izinli olmayan herkesi at
            int atilan = 0;
            for (Player on : new ArrayList<>(Bukkit.getOnlinePlayers())) {
                if (on.isOp() || on.hasPermission("loginx.admin") || bakimIzinli.contains(on.getUniqueId())) continue;
                on.kickPlayer(color(
                    "&#0099ff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                    "&#ffffff&l      ⚙  SUNUCU BAKIMDA  ⚙\n" +
                    "&#0099ff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                    "\n" +
                    "&#aaaaaa  Sunucumuz bakım ve güncelleme\n" +
                    "&#aaaaaa  işlemleri için geçici olarak\n" +
                    "&#aaaaaa  kapatılmıştır.\n" +
                    "\n" +
                    "&#ffcc00  ✦ &fBakım bitince tekrar\n" +
                    "&#ffcc00  ✦ &fbağlanabilirsin.\n" +
                    "\n" +
                    "&#0099ff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                    "&#555555  Anlayışın için teşekkürler!"
                ));
                atilan++;
            }
            for (Player on : Bukkit.getOnlinePlayers())
                if (on.hasPermission("loginx.admin"))
                    on.sendMessage(color("&#ffcc00&l[BAKIM] &fBakım modu &aACTIF &7— " + atilan + " oyuncu atıldı."));
            return true;
        }

        // /bakimdancikar — bakım modunu kapat
        if (cmdName.equals("bakimdancikar")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("[BAKIM] Oyuncu komutudur."); return true; }
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(color("&cYetkisiz.")); return true; }
            bakimModu = false;
            bakimIzinli.clear();
            getConfig().set("bakim.aktif", false); saveConfig();
            p.sendMessage(color("&#00ff88&l[BAKIM] &fBakım modu &cKAPATILDI&f. Sunucu herkese açık."));
            for (Player on : Bukkit.getOnlinePlayers())
                if (on.hasPermission("loginx.admin") && !on.equals(p))
                    on.sendMessage(color("&#00ff88&l[BAKIM] &f" + p.getName() + " &7bakımı kapattı."));
            return true;
        }

        // /bakimaoyuncual <isim> — bakımdayken belirli oyuncunun girmesine izin ver
        if (cmdName.equals("bakimaoyuncual")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("[BAKIM] Oyuncu komutudur."); return true; }
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(color("&cYetkisiz.")); return true; }
            if (args.length < 1) { p.sendMessage(color("&cKullanim: /bakimaoyuncual <oyuncu>")); return true; }
            // Çevrimiçiyse UUID al, değilse OfflinePlayer
            Player target = Bukkit.getPlayer(args[0]);
            UUID targetUid = target != null ? target.getUniqueId() : Bukkit.getOfflinePlayer(args[0]).getUniqueId();
            bakimIzinli.add(targetUid);
            p.sendMessage(color("&#00ff88&l[BAKIM] &f" + args[0] + " &7bakımdayken girebilir."));
            if (target != null) target.sendMessage(color("&#00ff88&l[BAKIM] &fBakım sırasında sunucuya girebilirsin."));
            return true;
        }

        // /cooldownhiziayarla <hiz> — elindeki item vurma hızını ayarla
        if (cmdName.equals("cooldownhiziayarla")) {
            if (!(sender instanceof Player p)) return true;
            if (args.length < 1) {
                p.sendMessage(color("&#00ff88&lCooldown &8» &fKullanim: &e/cooldownhiziayarla <hiz>"));
                p.sendMessage(color("  &7Örnek: &e/cooldownhiziayarla 2.0 &7(2x hız)"));
                p.sendMessage(color("  &7Min: &e0.1  &7Max: &e10.0"));
                p.sendMessage(color("  &7Elinizde item tutmanız gerekiyor."));
                return true;
            }
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(color("&cYetkisiz.")); return true; }
            double speed;
            try { speed = Double.parseDouble(args[0].replace(",",".")); }
            catch (NumberFormatException ex) { p.sendMessage(color("&cGeçersiz hız. Örnek: 1.5")); return true; }
            if (speed < 0.1 || speed > 10) { p.sendMessage(color("&cHız 0.1-10 arasında olmalı.")); return true; }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) { p.sendMessage(color("&cElinizde bir item tutmalısınız.")); return true; }
            ItemMeta hm = hand.getItemMeta();
            hm.getPersistentDataContainer().set(cooldownSpeedTag, PersistentDataType.DOUBLE, speed);
            if (!hm.hasDisplayName()) hm.setDisplayName(color("&f" + formatMaterial(hand.getType())));
            hand.setItemMeta(hm);
            p.getInventory().setItemInMainHand(hand);
            p.sendMessage(color("&#00ff88&lCooldown &8» &fElinizdeki &e" + formatMaterial(hand.getType()) + " &fitemine &a" + speed + "x &fvurma hızı ayarlandı."));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, (float)(speed / 5.0 + 0.5));
            return true;
        }

        // /kilicvurmahiziayarla — /cooldownhiziayarla ile aynı (kılıç/item için)
        if (cmdName.equals("kilicvurmahiziayarla")) {
            if (!(sender instanceof Player p)) return true;
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(color("&cYetkisiz.")); return true; }
            if (args.length < 1) {
                p.sendMessage(color("&#00ff88&lKılıç Hız &8» &fKullanim: &e/kilicvurmahiziayarla <hiz>"));
                p.sendMessage(color("  &7Örnek: &e/kilicvurmahiziayarla 2.0 &7(2x hız)  Min: 0.1  Max: 10"));
                return true;
            }
            double spd;
            try { spd = Double.parseDouble(args[0].replace(",",".")); }
            catch (NumberFormatException ex) { p.sendMessage(color("&cGeçersiz hız. Örnek: 1.5")); return true; }
            if (spd < 0.1 || spd > 10) { p.sendMessage(color("&cHız 0.1-10 arasında olmalı.")); return true; }
            ItemStack khand = p.getInventory().getItemInMainHand();
            if (khand.getType() == Material.AIR) { p.sendMessage(color("&cElinizde item/kılıç tutmalısınız.")); return true; }
            ItemMeta khm = khand.getItemMeta();
            khm.getPersistentDataContainer().set(cooldownSpeedTag, PersistentDataType.DOUBLE, spd);
            if (!khm.hasDisplayName()) khm.setDisplayName(color("&f" + formatMaterial(khand.getType())));
            khand.setItemMeta(khm);
            p.getInventory().setItemInMainHand(khand);
            p.sendMessage(color("&#00ff88&lKılıç Hız &8» &fElinizdeki &e" + formatMaterial(khand.getType()) + " &fitemine &a" + spd + "x &fvurma hızı ayarlandı."));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, (float)(spd / 5.0 + 0.5));
            return true;
        }

        // /yerekoymaengelle — elindeki item/blok'u yere koymayı engelle / aç
        if (cmdName.equals("yerekoymaengelle")) {
            if (!(sender instanceof Player p)) return true;
            ItemStack yHand = p.getInventory().getItemInMainHand();
            if (yHand.getType() == Material.AIR) { p.sendMessage(color("&cElinizde bir item/blok tutmalısınız.")); return true; }
            if (!yHand.hasItemMeta()) yHand.setItemMeta(Bukkit.getItemFactory().getItemMeta(yHand.getType()));
            ItemMeta yMeta = yHand.getItemMeta();
            org.bukkit.NamespacedKey placeKey = new org.bukkit.NamespacedKey(this, "place_blocked");
            boolean blocked = yMeta.getPersistentDataContainer().has(placeKey, PersistentDataType.BYTE);
            if (blocked) {
                yMeta.getPersistentDataContainer().remove(placeKey);
                yHand.setItemMeta(yMeta);
                p.getInventory().setItemInMainHand(yHand);
                p.sendMessage(color("&#00ff88&l[Engel] &fElinizdeki &e" + formatMaterial(yHand.getType()) + " &f&7— artık yere &akonabilir."));
            } else {
                yMeta.getPersistentDataContainer().set(placeKey, PersistentDataType.BYTE, (byte)1);
                yHand.setItemMeta(yMeta);
                p.getInventory().setItemInMainHand(yHand);
                p.sendMessage(color("&#ff4444&l[Engel] &fElinizdeki &e" + formatMaterial(yHand.getType()) + " &f— artık &chiç kimse &fyere koyamaz."));
            }
            return true;
        }

        // ─────────────────────────────────────────────
        //  KONT SİSTEMİ KOMUTLARİ
        // ─────────────────────────────────────────────

        // /kontyeri ayarla — yetkili kendi konumunu kont yeri olarak ayarlar
        if (cmdName.equals("kontyreriayarla") || cmdName.equals("kontyeriayarla")) {
            if (!(sender instanceof Player p)) return true;
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(color("&cYetkisiz.")); return true; }
            kontYeri = p.getLocation().clone();
            getConfig().set("kontyeri", kontYeri);
            saveConfig();
            p.sendMessage(color("&#00ff88&lKont &8» &fKont yeri bu konuma ayarlandı: &b"
                + kontYeri.getBlockX() + ", " + kontYeri.getBlockY() + ", " + kontYeri.getBlockZ()));
            return true;
        }

        // /kontyeri — kont yerine ışınlan (admin)
        if (cmdName.equals("kontyeri")) {
            if (!(sender instanceof Player p)) return true;
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(color("&cYetkisiz.")); return true; }
            if (kontYeri == null) { p.sendMessage(color("&#ff4444&lKont &8» &cKont yeri ayarlanmamış! &e/kontyeriayarla &cyaz.")); return true; }
            p.teleport(kontYeri);
            p.sendMessage(color("&#00ff88&lKont &8» &fKont yerine ışınlandın."));
            return true;
        }

        // /kontal (oyuncu) (süre örnek: 7gün / 30dk / 2saat)
        if (cmdName.equals("kontal")) {
            if (!(sender instanceof Player admin)) return true;
            if (!admin.hasPermission("loginx.admin")) { admin.sendMessage(color("&cYetkisiz.")); return true; }
            if (args.length < 2) { admin.sendMessage(color("&cKullanim: /kontal <oyuncu> <süre örn: 7gün 30dk 2saat>")); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { admin.sendMessage(color("&cOyuncu çevrimiçi değil: " + args[0])); return true; }
            long sureMs = parseSure(args[1]);
            if (sureMs <= 0) { admin.sendMessage(color("&cGeçersiz süre. Örnek: 7gün 30dk 2saat")); return true; }
            if (kontYeri == null) { admin.sendMessage(color("&#ff4444&lKont &8» &cÖnce /kontyeriayarla ile kont yeri belirle!")); return true; }

            // Hedef zaten kontta mı?
            if (kontUntil.containsKey(target.getUniqueId())) {
                admin.sendMessage(color("&#ff4444&l[KONT] &f" + target.getName() + " &czaten kont altında! Önce &e/kontcikar &cyaz."));
                return true;
            }

            UUID uid = target.getUniqueId();
            kontUntil.put(uid, System.currentTimeMillis() + sureMs);
            kontYetkilisi.put(uid, admin.getUniqueId());

            // Eşyaları RAM'e kaydet
            kontSavedInv.put(uid, target.getInventory().getContents().clone());
            target.getInventory().clear();

            // Kont itemi — hotbar ortası slot 4
            ItemStack kontItem = buildKontItem(target);
            target.getInventory().setItem(4, kontItem);

            // Oyuncuyu YETKİLİNİN AYARLADIĞI YERE ışınla (kontYetkiliYeri'ne, yoksa kontYeri'ne)
            Location adminLoc = kontYetkiliYeri.get(admin.getName());
            Location hedefLoc = adminLoc != null ? adminLoc : kontYeri;
            target.teleport(hedefLoc);

            // Yetkili de aynı anda aynı yere ışınlansın (ikisi birlikte kont yerinde olsun)
            admin.teleport(hedefLoc);

            // Freeze — walkSpeed DEĞİL, event ile dondurulur
            kontUntil.put(uid, System.currentTimeMillis() + sureMs); // yeniden set

            // Title ve ses
            target.sendTitle(
                color("&#ff0000&l⚠ HİLE KONTROLE ALINDI! ⚠"),
                color("&#ffcc00/itiraf &f&7, &e/anydesk &7veya &b/discord2 &7yaz"),
                10, 120, 30);
            target.playSound(target.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.5f);
            target.playSound(target.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.2f);
            target.sendMessage(color("&#ff0000&l⚠ &r&#ffcc00Hile Kontrole Alındın! &r&#aaaaaa&o/itiraf, /anydesk veya /discord2 komutlarından birini kullan."));

            // Yetkililere bildir
            for (Player on : Bukkit.getOnlinePlayers()) {
                if (on.hasPermission("loginx.admin")) {
                    on.sendMessage(color("&#ff0000&l[KONT] &r&#ffcc00" + target.getName() + " &fhile kontrole alındı. &8(" + admin.getName() + " tarafından)"));
                    on.playSound(on.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                }
            }

            // 3 dakika timeout — gelmezsee tempban
            cancelKontTimeout(uid);
            int taskId = new BukkitRunnable() {
                @Override public void run() {
                    if (!kontUntil.containsKey(uid)) return;
                    Player tp = Bukkit.getPlayer(uid);
                    String name = tp != null ? tp.getName() : Bukkit.getOfflinePlayer(uid).getName();
                    // 3 dakikada cevap vermedi — sadece bildir, ban yok
                    for (Player on : Bukkit.getOnlinePlayers()) {
                        if (on.hasPermission("loginx.admin"))
                            on.sendMessage(color("&#ffcc00&l[KONT] &r&#ff6600" + name + " &73 dakikada cevap vermedi! &e/kontcikar &7ile çıkarabilirsin."));
                    }
                    if (tp != null) {
                        tp.sendMessage(color("&#ff4444&l[KONT] &f3 dakika geçti. Lütfen &e/itiraf&f, &b/anydesk &fveya &9/discord2 &fyaz!"));
                    }
                }
            }.runTaskLater(this, 20L * 180).getTaskId(); // 3 dakika
            kontTimeoutTask.put(uid, taskId);

            admin.sendMessage(color("&#00ff88&l[KONT] &r&f" + target.getName() + " &7kont alındı. Süre: &e" + args[1]));
            return true;
        }

        // /kontcikar (oyuncu) — eşyaları geri ver, serbest bırak
        if (cmdName.equals("kontcikar")) {
            if (!(sender instanceof Player admin)) return true;
            if (!admin.hasPermission("loginx.admin")) { admin.sendMessage(color("&cYetkisiz.")); return true; }
            if (args.length < 1) { admin.sendMessage(color("&cKullanim: /kontcikar <oyuncu>")); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            UUID uid = target != null ? target.getUniqueId() : null;
            if (uid == null) {
                // Çevrimdışı — isimle bul
                for (UUID k : kontUntil.keySet()) {
                    if (Bukkit.getOfflinePlayer(k).getName() != null &&
                        Bukkit.getOfflinePlayer(k).getName().equalsIgnoreCase(args[0])) { uid = k; break; }
                }
            }
            if (uid == null || !kontUntil.containsKey(uid)) { admin.sendMessage(color("&cBu oyuncu kontta değil: " + args[0])); return true; }
            releaseKont(uid, true);
            admin.sendMessage(color("&#00ff88&l[KONT] &r&f" + args[0] + " &7kontten çıkarıldı, eşyaları iade edildi."));
            return true;
        }

        // /kontdurdur — 3 dakika boyunca dondurul (kont süresince)
        if (cmdName.equals("kontdurdur")) {
            if (!(sender instanceof Player admin)) return true;
            if (!admin.hasPermission("loginx.admin")) { admin.sendMessage(color("&cYetkisiz.")); return true; }
            if (args.length < 1) { admin.sendMessage(color("&cKullanim: /kontdurdur <oyuncu>")); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { admin.sendMessage(color("&cOyuncu bulunamadı.")); return true; }
            if (!kontUntil.containsKey(target.getUniqueId())) {
                admin.sendMessage(color("&cBu oyuncu kont altında değil.")); return true;
            }
            // Zaten kont başlayınca zaten dondurulmuş — kont yerine ışınla ve yeniden kilitle
            target.teleport(kontYeri != null ? kontYeri : target.getLocation());
            target.setWalkSpeed(0f);
            target.setFlySpeed(0f);
            target.sendTitle(color("&#ff0000&l⚠ DURDURULDUN!"), color("&#ffcc00Kaçmaya çalışma!"), 5, 60, 10);
            target.sendMessage(color("&#ff0000&l⚠ &fHile kontrolünden kaçamazsın!"));
            admin.sendMessage(color("&#00ff88&l[KONT] &r&f" + target.getName() + " &7durduruldu ve kont yerine ışınlandı."));
            return true;
        }

        // /kontyerisil — kayıtlı kont yerini sil
        if (cmdName.equals("kontyerisil")) {
            if (!(sender instanceof Player admin)) return true;
            if (!admin.hasPermission("loginx.admin")) { admin.sendMessage(color("&cYetkisiz.")); return true; }
            if (kontYeri == null) { admin.sendMessage(color("&#ff4444&lKont &8» &cZaten kayıtlı kont yeri yok.")); return true; }
            kontYeri = null;
            getConfig().set("kontyeri", null);
            saveConfig();
            admin.sendMessage(color("&#ff4444&l[KONT] &r&fKont yeri silindi."));
            return true;
        }

        // /kontyeryetkili ayarla <isim>   ya da   /kontyeryetkili sil <isim>
        // Ayrıca eski tek-kelime komutları da destekle (plugin.yml uyumluluğu için)
        if (cmdName.equals("kontyeryetkili") || cmdName.equals("kontyeryetkiliayarla") || cmdName.equals("kontyeryetkilisil")) {
            if (!(sender instanceof Player admin)) { sender.sendMessage("[KONT] Bu komut oyuncu tarafindan kullanilir."); return true; }
            if (!admin.hasPermission("loginx.admin") && !admin.isOp()) { admin.sendMessage(color("&cYetkisiz.")); return true; }
            // Eski komutlar: /kontyeryetkiliayarla <isim>  /kontyeryetkilisil <isim>
            String alt;
            String hedef;
            if (cmdName.equals("kontyeryetkiliayarla")) {
                alt = "ayarla"; hedef = args.length > 0 ? args[0] : admin.getName();
            } else if (cmdName.equals("kontyeryetkilisil")) {
                alt = "sil"; hedef = args.length > 0 ? args[0] : admin.getName();
            } else {
                // /kontyeryetkili ayarla <isim>
                if (args.length < 2) { admin.sendMessage(color("&cKullanim: /kontyeryetkili ayarla <isim>")); return true; }
                alt = args[0].toLowerCase(); hedef = args[1];
            }
            if (alt.equals("ayarla")) {
                kontYetkiliYeri.put(hedef, admin.getLocation().clone());
                getConfig().set("kontyetkili." + hedef, admin.getLocation());
                saveConfig();
                admin.sendMessage(color("&#00ff88&l[KONT] &f" + hedef + " &7için yetkili yeri &aayarlandı&7."));
            } else if (alt.equals("sil")) {
                kontYetkiliYeri.remove(hedef);
                getConfig().set("kontyetkili." + hedef, null);
                saveConfig();
                admin.sendMessage(color("&#ff4444&l[KONT] &f" + hedef + " &7için yetkili yeri &csilindi&7."));
            } else {
                admin.sendMessage(color("&cKullanim: /kontyeryetkili ayarla <isim>  |  /kontyeryetkili sil <isim>"));
            }
            return true;
        }

        // /izinver <oyuncu> — SADECE KONSOL
        if (cmdName.equals("izinver")) {
            if (sender instanceof Player) { ((Player)sender).sendMessage(color("&cBu komut sadece &lkonsoldan&r&c çalışır.")); return true; }
            if (args.length < 1) { sender.sendMessage("Kullanim: /izinver <oyuncu>"); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { sender.sendMessage("[İZİN] Oyuncu çevrimiçi değil: " + args[0]); return true; }
            izinliOyuncular.add(target.getUniqueId());
            getConfig().set("izinli." + target.getUniqueId().toString(), true); saveConfig();
            sender.sendMessage("[İZİN] " + target.getName() + " -> TNT/WorldEdit izni VERİLDİ.");
            target.sendMessage(color("&#00ff88&l[İZİN] &fSana TNT ve WorldEdit izni verildi!"));
            return true;
        }

        // /izinsil <oyuncu> — SADECE KONSOL
        if (cmdName.equals("izinsil") || cmdName.equals("izinkaldır") || cmdName.equals("izinkaldir")) {
            if (sender instanceof Player) { ((Player)sender).sendMessage(color("&cBu komut sadece &lkonsoldan&r&c çalışır.")); return true; }
            if (args.length < 1) { sender.sendMessage("Kullanim: /izinsil <oyuncu>"); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                izinliOyuncular.remove(target.getUniqueId());
                getConfig().set("izinli." + target.getUniqueId().toString(), null); saveConfig();
                target.sendMessage(color("&#ff4444&l[İZİN] &fTNT ve WorldEdit iznin kaldırıldı."));
            } else {
                // UUID'yi config'den sil — çevrimdışı oyuncu
                for (String key : getConfig().getConfigurationSection("izinli") != null ?
                        getConfig().getConfigurationSection("izinli").getKeys(false) : new java.util.HashSet<String>()) {
                    getConfig().set("izinli." + key, null);
                }
                saveConfig();
            }
            sender.sendMessage("[İZİN] " + args[0] + " -> izin KALDIRILDI.");
            return true;
        }

        // /logingoster — kayıtlı şifreleri göster (sadece admin)
        if (cmdName.equals("logingoster")) {
            if (!(sender instanceof Player admin)) { 
                // Konsoldan çalıştır
                sender.sendMessage("=== LOGIN ŞİFRELERİ ===");
                for (Map.Entry<String, String> e2 : oyuncuSifre.entrySet())
                    sender.sendMessage(e2.getKey() + " → " + e2.getValue());
                return true;
            }
            if (!admin.hasPermission("loginx.admin")) { admin.sendMessage(color("&cYetkisiz.")); return true; }
            if (oyuncuSifre.isEmpty()) { admin.sendMessage(color("&7Henüz kayıtlı şifre yok.")); return true; }
            admin.sendMessage(color("&#ffcc00&l=== Kayıtlı Şifreler ==="));
            for (Map.Entry<String, String> e2 : oyuncuSifre.entrySet())
                admin.sendMessage(color("  &#aaaaaa" + e2.getKey() + " &8→ &f" + e2.getValue()));
            admin.sendMessage(color("&#ffcc00&l====================="));
            return true;
        }

        // /itiraf — kont sırasında → 4 gün tempban
        if (cmdName.equals("itiraf")) {
            if (!(sender instanceof Player p)) return true;
            if (!kontUntil.containsKey(p.getUniqueId())) { p.sendMessage(color("&cKont altında değilsin.")); return true; }
            String msg = args.length > 0 ? String.join(" ", args) : "(boş)";
            notifyKontYetkilisi(p.getUniqueId(), color("&#ff6600&l[İTİRAF] &r&#ffcc00" + p.getName() + ": &f" + msg));
            p.sendMessage(color("&#00ff88» &fİtirafın yetkililere iletildi."));
            // İtiraf eden oyuncuya 4 gün tempban
            new BukkitRunnable() {
                @Override public void run() {
                    releaseKont(p.getUniqueId(), true);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + p.getName() + " Hile İtirafı - 4 gün ban | Bitiş: " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(System.currentTimeMillis() + 4*86400000L)));
                    for (Player on : Bukkit.getOnlinePlayers())
                        if (on.hasPermission("loginx.admin"))
                            on.sendMessage(color("&#ff0000&l[KONT] &r&#ffcc00" + p.getName() + " &fitiraf etti — &c4 gün ban uygulandı."));
                }
            }.runTaskLater(this, 20L);
            return true;
        }

        // /anydesk (kod)
        if (cmdName.equals("anydesk")) {
            if (!(sender instanceof Player p)) return true;
            if (!kontUntil.containsKey(p.getUniqueId())) { p.sendMessage(color("&cKont altında değilsin.")); return true; }
            String kod = args.length > 0 ? String.join(" ", args) : "(boş)";
            notifyKontYetkilisi(p.getUniqueId(), color("&#00ccff&l[ANYDESK] &r&#ffffff" + p.getName() + " &7kodu: &b" + kod));
            p.sendMessage(color("&#00ff88» &fAnyDesk kodun yetkililere iletildi: &b" + kod));
            return true;
        }

        // /discord2 (discord nicki)
        if (cmdName.equals("discord2")) {
            if (!(sender instanceof Player p)) return true;
            if (!kontUntil.containsKey(p.getUniqueId())) { p.sendMessage(color("&cKont altında değilsin.")); return true; }
            String nick = args.length > 0 ? String.join(" ", args) : "(boş)";
            notifyKontYetkilisi(p.getUniqueId(), color("&#7289da&l[DİSCORD] &r&#ffffff" + p.getName() + " &7Discord: &b" + nick));
            p.sendMessage(color("&#00ff88» &fDiscord nickin yetkililere iletildi: &b" + nick));
            return true;
        }

        // ─── Admin gerektiren komutlar ───
        if (!(sender instanceof Player p)) return true;
        if (!p.hasPermission("loginx.admin")) {
            // Bazı komutlar her oyuncuya açık değil
            return true;
        }

        switch (cmdName) {

            case "loginxreload" -> { reloadConfig(); loadData(); p.sendMessage(color("&aLoginX yenilendi!")); }
            case "iade"            -> openIadeMenu(p);
            case "kasaanahtarmenu" -> openKeyMenu(p);
            case "kasabilgi"       -> openInfoMenu(p);

            case "kasaayarla" -> {
                if (args.length < 1) { p.sendMessage(color("&cKullanim: /kasaayarla <isim>")); return true; }
                Block b = p.getTargetBlockExact(5);
                if (b == null || b.getType() == Material.AIR) { p.sendMessage(color("&cBir bloga bakin.")); return true; }
                String ad = args[0].toLowerCase();
                crates.put(ad, b.getLocation());
                crateItems.putIfAbsent(ad, new ArrayList<>());
                crateChances.putIfAbsent(ad, new ArrayList<>());
                crateEffects.put(ad, true);
                crateEffectType.put(ad, "varsayilan");
                // Hologram yükle
                spawnCrateHologram(ad);
                p.sendMessage(color("&a" + ad + " kasasi ayarlandi!")); saveData();
            }

            case "kasasil" -> {
                if (args.length < 1) { p.sendMessage(color("&cKullanim: /kasasil <isim>")); return true; }
                String ad = args[0].toLowerCase();
                if (!crates.containsKey(ad)) { p.sendMessage(color("&cKasa bulunamadi: " + ad)); return true; }
                removeHologram(ad);
                crates.remove(ad); crateItems.remove(ad); crateEffects.remove(ad);
                crateChances.remove(ad); crateEffectType.remove(ad);
                // Config'den tamamen sil — restart'ta geri gelmesin
                getConfig().set("crates." + ad, null);
                saveConfig();
                p.sendMessage(color("&c" + ad + " kasasi silindi ve config'den kaldırıldı."));
            }

            case "kasaicsil" -> {
                if (args.length < 2) { p.sendMessage(color("&cKullanim: /kasaicsil <kasa> #<sira>")); return true; }
                String ad = args[0].toLowerCase();
                List<ItemStack> pool = crateItems.get(ad);
                if (pool == null) { p.sendMessage(color("&cKasa bulunamadi: " + ad)); return true; }
                int sira;
                try { sira = Integer.parseInt(args[1].replace("#","").trim()) - 1; }
                catch (NumberFormatException ex) { p.sendMessage(color("&cGecersiz sira.")); return true; }
                if (sira < 0 || sira >= pool.size()) { p.sendMessage(color("&cSira aralik disi.")); return true; }
                ItemStack removed = pool.remove(sira);
                List<Float> chs = crateChances.getOrDefault(ad, new ArrayList<>());
                if (sira < chs.size()) chs.remove(sira);
                p.sendMessage(color("&c" + ad + " kasasindan #" + (sira+1) + " (" + removed.getType().name() + ") silindi.")); saveData();
            }

            case "icineitemkoy" -> {
                if (args.length < 1) { p.sendMessage(color("&cKullanim: /icineitemkoy <kasa>")); return true; }
                ItemStack ih = p.getInventory().getItemInMainHand();
                if (ih.getType() == Material.AIR) { p.sendMessage(color("&cElinizde esya olmali!")); return true; }
                String ad = args[0].toLowerCase();
                if (!crates.containsKey(ad)) { p.sendMessage(color("&cKasa bulunamadi: " + ad)); return true; }
                crateItems.get(ad).add(ih.clone());
                crateChances.get(ad).add(10.0f);
                p.sendMessage(color("&aEsya &e" + ad + " &akasasina eklendi. (#" + crateItems.get(ad).size() + ") Varsayilan sans: &b%10")); saveData();
            }

            case "kasaidlist" -> {
                if (args.length < 1) { p.sendMessage(color("&cKullanim: /kasaidlist <kasa>")); return true; }
                String ad = args[0].toLowerCase();
                List<ItemStack> pool = crateItems.get(ad);
                if (pool == null || pool.isEmpty()) { p.sendMessage(color("&cKasa bos ya da bulunamadi.")); return true; }
                List<Float> chs = crateChances.getOrDefault(ad, new ArrayList<>());
                p.sendMessage(color("&#ffcc00&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("&#ffcc00&l  ✦ " + capitalize(ad) + " Kasası İçeriği &8(" + pool.size() + " eşya)"));
                p.sendMessage(color("&#ffcc00&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                for (int i = 0; i < pool.size(); i++) {
                    String iName = getName(pool.get(i));
                    float ch = (i < chs.size()) ? chs.get(i) : 10.0f;
                    p.sendMessage(color("  &8#" + (i+1) + " &f" + iName + " &8│ &7Çıkma Şansı: &b%" + ch + " &8│ " + getRarityName(ch)));
                }
                p.sendMessage(color("&#ffcc00&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            }

            case "kasasansayarla" -> {
                if (args.length < 3) { p.sendMessage(color("&cKullanim: /kasasansayarla <kasa> <#sira> <sans>")); return true; }
                String ad = args[0].toLowerCase();
                List<ItemStack> pool = crateItems.get(ad);
                if (pool == null) { p.sendMessage(color("&cKasa bulunamadi.")); return true; }
                int sira; float chance;
                try {
                    sira   = Integer.parseInt(args[1].replace("#","").trim()) - 1;
                    chance = Float.parseFloat(args[2].replace(",",".").replace("%",""));
                } catch (NumberFormatException ex) { p.sendMessage(color("&cGecersiz deger.")); return true; }
                if (sira < 0 || sira >= pool.size()) { p.sendMessage(color("&cSira aralik disi.")); return true; }
                if (chance <= 0 || chance > 100) { p.sendMessage(color("&cSans 0-100 arasinda olmali.")); return true; }
                List<Float> chs = crateChances.getOrDefault(ad, new ArrayList<>());
                while (chs.size() <= sira) chs.add(10.0f);
                chs.set(sira, chance); crateChances.put(ad, chs);
                String[] clrS = getCrateColors(ad);
                p.sendMessage(color("&#ffcc00&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("&#00ff88&l  ✔ Şans Güncellendi!"));
                p.sendMessage(color("  &7Kasa: " + clrS[0] + capitalize(ad)));
                p.sendMessage(color("  &7Eşya: &f" + getName(pool.get(sira))));
                p.sendMessage(color("  &7Çıkma Şansı: &b%" + chance + " &8│ " + getRarityName(chance)));
                p.sendMessage(color("&#ffcc00&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                saveData();
            }

            case "kasakumarmenu" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("ayarla")) openKumarMenuSettings(p);
                else p.sendMessage(color("&cKullanim: /kasakumarmenu ayarla"));
            }

            case "kasakeyver" -> {
                if (args.length < 2) { p.sendMessage(color("&cKullanim: /kasakeyver <oyuncu|all> <kasaismi>")); return true; }
                String kasaId  = args[1].toLowerCase();
                String keyName = KEY_BASE_NAMES.getOrDefault(kasaId, capitalize(kasaId) + " Key");
                ItemStack key  = buildPhysicalKey(kasaId);
                String[] clr   = getCrateColors(kasaId);
                if (args[0].equalsIgnoreCase("all")) {
                    int cnt = 0;
                    for (Player on : Bukkit.getOnlinePlayers()) {
                        on.getInventory().addItem(key.clone());
                        on.sendTitle(
                            color(clr[0] + "&l✦ " + clr[1] + "&l" + keyName.toUpperCase() + " " + clr[2] + "&l✦"),
                            color("&#ffffff&l» &r&#dddddd" + capitalize(kasaId) + " Kasası Anahtarı &r&#00ff88Envantere Eklendi &r&#ffffff«"),
                            5, 70, 15);
                        on.playSound(on.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.3f);
                        cnt++;
                    }
                    p.sendMessage(color("&#00ff88&l✔ &r&f" + cnt + " oyuncuya &e" + keyName + " &fverildi."));
                    // Sunucu geneli EVENT mesajı
                    for (Player on : Bukkit.getOnlinePlayers()) {
                        on.sendMessage(color(""));
                        on.sendMessage(color("  &#ff6600&l★ &r&#ffcc00&lEVENT &r&#ff6600&l★"));
                        on.sendMessage(color("  &#ffffff&lTüm oyunculara &r" + clr[0] + "&l" + keyName + " &r&#ffffff&lverildi!"));
                        on.sendMessage(color("  &#aaaaaa&oKasanı aç, şansını dene!"));
                        on.sendMessage(color(""));
                    }
                } else {
                    Player tgt = Bukkit.getPlayer(args[0]);
                    if (tgt == null) { p.sendMessage(color("&cOyuncu bulunamadi: " + args[0])); return true; }
                    tgt.getInventory().addItem(key.clone());
                    tgt.sendTitle(
                        color(clr[0] + "&l✦ " + clr[1] + "&l" + keyName.toUpperCase() + " " + clr[2] + "&l✦"),
                        color("&#ffffff&l» &r&#dddddd" + capitalize(kasaId) + " Kasası Anahtarı &r&#00ff88Envantere Eklendi &r&#ffffff«"),
                        5, 70, 15);
                    tgt.playSound(tgt.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.3f);
                    p.sendMessage(color("&#00ff88&l✔ &r&f" + tgt.getName() + " &7oyuncusuna &e" + keyName + " &fverildi."));
                    // Sunucu geneli EVENT mesajı (kişiye özel)
                    for (Player on : Bukkit.getOnlinePlayers()) {
                        on.sendMessage(color(""));
                        on.sendMessage(color("  &#ff6600&l★ &r&#ffcc00&lEVENT &r&#ff6600&l★"));
                        on.sendMessage(color("  &#ffcc00&l" + tgt.getName() + " &r&#ffffff&l" + clr[0] + "&l" + keyName + " &r&#ffffff&lkazandı!"));
                        on.sendMessage(color(""));
                    }
                }
            }

            case "kasafarkliefektler" -> openEffectSelectMenu(p, args.length > 0 ? args[0].toLowerCase() : "");

            case "efektac"   -> { if (args.length > 0) { crateEffects.put(args[0].toLowerCase(), true);  p.sendMessage(color("&aEfekt acildi.")); } }
            case "efektkapa" -> { if (args.length > 0) { crateEffects.put(args[0].toLowerCase(), false); p.sendMessage(color("&cEfekt kapatildi.")); } }
        }
        return true;
    }

    // ─────────────────────────────────────────────
    //  HOLOGRAM SİSTEMİ
    // ─────────────────────────────────────────────
    private void spawnCrateHologram(String crateName) {
        removeHologram(crateName);
        Location loc = crates.get(crateName);
        if (loc == null || loc.getWorld() == null) return;

        List<Integer> ids = new ArrayList<>();
        String nameLine = buildHologramLine(crateName);

        // Fotoğraftaki gibi: daha aşağıda, blok üzerinde yakın
        double yOff = 1.6;
        spawnTextStand(loc, yOff,        nameLine,                                       ids);
        spawnTextStand(loc, yOff - 0.28, color("&#ffffff Açmak için sağ tıkla"),        ids);
        spawnTextStand(loc, yOff - 0.56, color("&#dddddd Önizlemek için sol tıkla"),    ids);

        crateHolograms.put(crateName, ids);
    }

    private String buildHologramLine(String crateName) {
        String[] clr = getCrateColors(crateName);
        String text = capitalize(crateName) + " Kasası";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') { sb.append(' '); continue; }
            // İnce (bold yok) — fotoğraftaki Gear Kasası gibi
            sb.append(clr[i % clr.length]).append(c);
        }
        return color(sb.toString());
    }

    private void spawnTextStand(Location base, double yOffset, String text, List<Integer> ids) {
        Location spawnLoc = base.clone().add(0.5, yOffset, 0.5);
        ArmorStand stand = (ArmorStand) base.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
        stand.setGravity(false);
        stand.setVisible(false);
        stand.setCustomNameVisible(true);
        stand.setCustomName(text);
        stand.setSmall(true);
        stand.setMarker(true);
        ids.add(stand.getEntityId());
    }

    private void removeHologram(String crateName) {
        List<Integer> ids = crateHolograms.remove(crateName);
        if (ids == null) return;
        Location loc = crates.get(crateName);
        if (loc == null || loc.getWorld() == null) return;
        for (Entity e : loc.getWorld().getEntities()) {
            if (ids.contains(e.getEntityId())) e.remove();
        }
    }

    private void removeAllHolograms() {
        for (String k : new ArrayList<>(crateHolograms.keySet())) removeHologram(k);
    }

    // Hologram title animasyonu — hız 1.2, bold yok
    private void tickCrateHolograms() {
        if (flopTick % 4 != 0) return; // ~1.2x hız (5 yerine 4)
        for (Map.Entry<String, List<Integer>> entry : crateHolograms.entrySet()) {
            String cn = entry.getKey();
            List<Integer> ids = entry.getValue();
            if (ids.isEmpty()) continue;
            Location loc = crates.get(cn);
            if (loc == null || loc.getWorld() == null) continue;
            int firstId = ids.get(0);
            for (Entity e : loc.getWorld().getNearbyEntities(loc.clone().add(0.5, 1.6, 0.5), 1, 0.5, 1)) {
                if (e.getEntityId() == firstId && e instanceof ArmorStand as) {
                    String[] clr = getCrateColors(cn);
                    String text = capitalize(cn) + " Kasası";
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < text.length(); i++) {
                        char c = text.charAt(i);
                        if (c == ' ') { sb.append(' '); continue; }
                        // Bold yok — ince stil
                        sb.append(clr[(i + flopTick / 4) % clr.length]).append(c);
                    }
                    as.setCustomName(color(sb.toString()));
                    break;
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    //  KASA ETKİLEŞİM
    // ─────────────────────────────────────────────
    @EventHandler
    public void onCrateInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        Location loc = e.getClickedBlock().getLocation();
        String activeCrate = null;
        for (Map.Entry<String, Location> entry : crates.entrySet())
            if (entry.getValue().equals(loc)) { activeCrate = entry.getKey(); break; }
        if (activeCrate == null) return;
        e.setCancelled(true);

        Player p = e.getPlayer();
        if (activeSessions.containsKey(p.getUniqueId())) return;

        if (e.getAction() == Action.LEFT_CLICK_BLOCK) { openCratePreview(p, activeCrate); return; }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack inHand = p.getInventory().getItemInMainHand();
        if (!inHand.hasItemMeta() ||
                !inHand.getItemMeta().getPersistentDataContainer().has(keyTag, PersistentDataType.STRING)) {
            p.setVelocity(p.getLocation().getDirection().multiply(-1.3).setY(0.5));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            p.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0.5,1,0.5), 6, 0.15, 0.15, 0.15, 0.02);
            p.sendTitle(color("&#ff2200&l✖ ANAHTAR GEREKLİ ✖"), color("&#ff8866Bu kasayi acmak icin bir anahtara ihtiyacin var"), 5, 50, 10);
            return;
        }

        String keyType = inHand.getItemMeta().getPersistentDataContainer().get(keyTag, PersistentDataType.STRING);
        if (!keyType.equalsIgnoreCase(activeCrate) && !keyType.equalsIgnoreCase("all")) {
            long now = System.currentTimeMillis(); UUID uid = p.getUniqueId();
            if (wrongKeyCooldown.getOrDefault(uid, 0L) > now) return;
            wrongKeyCooldown.put(uid, now + WRONG_KEY_COOLDOWN_MS);
            p.setVelocity(p.getLocation().getDirection().multiply(-1.3).setY(0.35));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            p.sendTitle(color("&#ffcc00&lYANLIS ANAHTAR!"), color("&#ffff66Bu anahtar bu kasayi acamaz."), 5, 40, 10);
            return;
        }

        List<ItemStack> pool = crateItems.getOrDefault(activeCrate, new ArrayList<>());
        if (pool.isEmpty()) { p.sendMessage(color("&cBu kasa henuz bos!")); return; }

        inHand.setAmount(inHand.getAmount() - 1);
        openSpinMenu(p, activeCrate, pool, pickReward(activeCrate, pool));
    }

    // ─────────────────────────────────────────────
    //  1v1 WAND ETKİLEŞİM
    // ─────────────────────────────────────────────
    @EventHandler
    public void onWandUse(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() != Material.GOLDEN_AXE) return;
        if (!item.hasItemMeta() || item.getItemMeta().getDisplayName() == null) return;
        String dn = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        if (!dn.contains("1v1 Wand")) return;

        e.setCancelled(true);
        if (e.getClickedBlock() == null) return;
        Location clicked = e.getClickedBlock().getLocation();

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Birinci köşe
            wand1Pos.put(p.getUniqueId(), clicked);
            wand2Pos.remove(p.getUniqueId());
            p.sendMessage(color("&#00ccff&l1v1 &8» &fBirinci köşe ayarlandı: &b"
                + clicked.getBlockX() + ", " + clicked.getBlockY() + ", " + clicked.getBlockZ()));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);

        } else if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            // İkinci köşe
            if (!wand1Pos.containsKey(p.getUniqueId())) {
                p.sendMessage(color("&#ff4444&l1v1 &8» &cÖnce sağ tıkla ile birinci köşeyi seç!"));
                return;
            }
            wand2Pos.put(p.getUniqueId(), clicked);
            Location l1 = wand1Pos.get(p.getUniqueId());
            p.sendMessage(color("&#00ff88&l1v1 &8» &fİkinci köşe ayarlandı! Arena oluşturuldu."));
            p.sendMessage(color("  &7Alan: &f" + Math.abs(l1.getBlockX()-clicked.getBlockX()+1)
                + "x" + Math.abs(l1.getBlockY()-clicked.getBlockY()+1)
                + "x" + Math.abs(l1.getBlockZ()-clicked.getBlockZ()+1)));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
            // Köşeleri kaydet — başka oyuncu daveti gelince kullanılacak
            // (Wand sadece seçer, arena kurulumu /1v1 davet ile olur)
        }
    }

    // ─────────────────────────────────────────────
    //  1v1 AYARLA WAND ETKİLEŞİM
    // ─────────────────────────────────────────────
    @EventHandler
    public void onAyarlaWandUse(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() != Material.BLAZE_ROD) return;
        if (!item.hasItemMeta() || item.getItemMeta().getDisplayName() == null) return;
        String dn = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        if (!dn.contains("1v1 Ayarla")) return;

        e.setCancelled(true);
        if (e.getClickedBlock() == null) return;
        Location clicked = e.getClickedBlock().getLocation();

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ayarlaPos1.put(p.getUniqueId(), clicked);
            ayarlaPos2.remove(p.getUniqueId());
            p.sendMessage(color("&#ffaa00&l1v1 Ayarla &8» &fBirinci köşe: &b"
                + clicked.getBlockX() + ", " + clicked.getBlockY() + ", " + clicked.getBlockZ()));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
            // Sol tık itemini al
            ItemStack offHand = p.getInventory().getItemInOffHand();
            if (offHand.getType() != Material.AIR) {
                ayarlaItem1.put(p.getUniqueId(), offHand.clone());
                p.sendMessage(color("  &7Sol tık duvar item: &e" + offHand.getType().name()));
            }

        } else if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (!ayarlaPos1.containsKey(p.getUniqueId())) {
                p.sendMessage(color("&#ff4444&l1v1 Ayarla &8» &cÖnce sağ tıkla ile birinci köşeyi seç!"));
                return;
            }
            ayarlaPos2.put(p.getUniqueId(), clicked);
            Location l1 = ayarlaPos1.get(p.getUniqueId());
            Location l2 = clicked;

            // Arena kaydet
            String arenaId = "ayarla_" + (++arenaCounter);
            BoundingBox bbox = BoundingBox.of(l1, l2);
            ayarlaBounds.put(arenaId, bbox);
            ayarlaArenas.put(arenaId, p.getUniqueId());
            ayarlaItem2.put(p.getUniqueId(), item.clone());

            // Envanterdeki ana el itemini duvar materyali olarak kullan
            ItemStack wallItem = p.getInventory().getItemInMainHand();
            ayarlaWalls.put(arenaId, new ItemStack[]{
                ayarlaItem1.getOrDefault(p.getUniqueId(), new ItemStack(Material.GLASS)),
                wallItem
            });

            p.sendMessage(color("&#00ff88&l1v1 Ayarla &8» &fArena kuruldu! &7ID: &e" + arenaId));
            p.sendMessage(color("  &7Alan: &f"
                + Math.abs(l1.getBlockX()-l2.getBlockX()+1) + "x"
                + Math.abs(l1.getBlockY()-l2.getBlockY()+1) + "x"
                + Math.abs(l1.getBlockZ()-l2.getBlockZ()+1)));
            p.sendMessage(color("  &72 oyuncu girince arena eşyalarla kapanır."));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
        }
    }

    // Oyuncu 1v1ayarla arenasına girince: 2. oyuncu girdiğinde kapat (döngü)
    @EventHandler
    public void onPlayerMoveAyarla(PlayerMoveEvent ev) {
        Player p = ev.getPlayer();
        // Zaten bu arenada kayıtlıysa tekrar işlem yapma
        if (playerAyarlaArena.containsKey(p.getUniqueId())) return;
        Location to = ev.getTo(); if (to == null) return;
        // Sadece blok değişiminde kontrol et (gereksiz CPU yükünü önler)
        Location from = ev.getFrom();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) return;

        for (Map.Entry<String, BoundingBox> entry : ayarlaBounds.entrySet()) {
            String arenaId = entry.getKey();
            BoundingBox bbox = entry.getValue();
            if (!bbox.contains(to.toVector())) continue;

            UUID[] queue = ayarlaQueue.computeIfAbsent(arenaId, k -> new UUID[2]);

            if (queue[0] == null) {
                // İlk oyuncu
                queue[0] = p.getUniqueId();
                playerAyarlaArena.put(p.getUniqueId(), arenaId);
                p.sendMessage(color(""));
                p.sendMessage(color("  " + duelFlop("⚔ ARENAYA GİRDİNİZ!", true)));
                p.sendMessage(color("  &#aaaaaa&o2. oyuncu bekleniyor..."));
                p.sendMessage(color(""));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
            } else if (queue[1] == null && !queue[0].equals(p.getUniqueId())) {
                // İkinci oyuncu — aynı oyuncu tekrar girmesin
                // queue[0]'ın hâlâ oyunda olup olmadığını kontrol et
                Player firstPlayer = Bukkit.getPlayer(queue[0]);
                if (firstPlayer == null || !playerAyarlaArena.containsKey(queue[0])) {
                    // İlk oyuncu ayrılmış, slotu yenile
                    queue[0] = p.getUniqueId();
                    playerAyarlaArena.put(p.getUniqueId(), arenaId);
                    p.sendMessage(color(""));
                    p.sendMessage(color("  " + duelFlop("⚔ ARENAYA GİRDİNİZ!", true)));
                    p.sendMessage(color("  &#aaaaaa&o2. oyuncu bekleniyor..."));
                    p.sendMessage(color(""));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
                    return;
                }

                queue[1] = p.getUniqueId();
                playerAyarlaArena.put(p.getUniqueId(), arenaId);
                // 2 kişi var — sadece boş yerlere cam koy (yere değil)
                closeAyarlaArena(arenaId, bbox, p.getWorld());
                // Arenaları tekrar kaydet — döngü için silinmesin
                ayarlaArenas.putIfAbsent(arenaId, queue[0]);
                for (UUID uid : queue) {
                    Player ap = Bukkit.getPlayer(uid);
                    if (ap != null) {
                        ap.sendMessage(color(""));
                        ap.sendMessage(color("  " + duelFlop("⚔ 1v1 BAŞLADI!", true) + " &r&#aaaaaa&oİyi şanslar."));
                        ap.sendMessage(color("  &#aaaaaa&o⚠ Oyundan çıkarsan eşyaların shulker'da düşer!"));
                        ap.sendMessage(color(""));
                        ap.sendTitle(duelFlop("⚔ 1v1 BAŞLADI ⚔", true), color("&#aaaaaa&oİyi şanslar!"), 5, 60, 15);
                        ap.playSound(ap.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
                    }
                }

                // ─── 10 dakikalık üst süre sınırı — bitmezse ikisini de duelspawnayarla'ya ışınla ───
                final String finalArenaIdTimeout = arenaId;
                final UUID p1u = queue[0], p2u = queue[1];
                new BukkitRunnable() {
                    @Override public void run() {
                        UUID[] q = ayarlaQueue.get(finalArenaIdTimeout);
                        // Hâlâ aynı ikili aktifse (maç bitmemişse) süre doldu say
                        if (q == null || q[0] == null || q[1] == null
                            || !q[0].equals(p1u) || !q[1].equals(p2u)) return;
                        playerAyarlaArena.remove(p1u);
                        playerAyarlaArena.remove(p2u);
                        ayarlaQueue.put(finalArenaIdTimeout, new UUID[2]);
                        BoundingBox tbbox = ayarlaBounds.get(finalArenaIdTimeout);
                        for (UUID uid2 : new UUID[]{p1u, p2u}) {
                            Player tp = Bukkit.getPlayer(uid2);
                            if (tp != null && tp.isOnline()) {
                                tp.sendTitle(
                                    duelFlop("⏰ SÜRE DOLDU!", true),
                                    color("&#aaaaaa&o10 dakika içinde bitirilemedi, ışınlanıyorsun..."),
                                    5, 60, 15);
                                tp.sendMessage(color("  " + duelFlop("⏰ 1v1 ROOM 10 DAKİKA SINIRINA ULAŞTI!", true)));
                                tp.playSound(tp.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
                                teleportToSpawn(tp);
                            }
                        }
                        // Oda tekrar herkese açık olsun
                        if (tbbox != null && p.getWorld() != null) openAyarlaArena(finalArenaIdTimeout, tbbox, p.getWorld());
                    }
                }.runTaskLater(LoginX.this, 20L * 600); // 10 dakika = 600 saniye
            }
            break; // Sadece bir arenayla eşleş
        }
    }

    private void closeAyarlaArena(String arenaId, BoundingBox bbox, World world) {
        // SADECE yan duvarlar ve tavan — YERE (minY bloğu) cam koyma!
        int minX = (int) bbox.getMinX(); int maxX = (int) bbox.getMaxX();
        int minY = (int) bbox.getMinY(); int maxY = (int) bbox.getMaxY();
        int minZ = (int) bbox.getMinZ(); int maxZ = (int) bbox.getMaxZ();

        // Orijinal blokları kaydet (geri yükleme için)
        Map<String, Material> savedBlocks = new HashMap<>();

        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    // Yalnızca YAN kenarlara ve tavana cam koy — TABANA koyma
                    boolean isYanKenar = (x==minX||x==maxX||z==minZ||z==maxZ) && y > minY;
                    boolean isTavan   = (y==maxY);
                    if ((isYanKenar || isTavan) && world.getBlockAt(x,y,z).getType() == Material.AIR) {
                        savedBlocks.put(x+","+y+","+z, Material.AIR);
                        world.getBlockAt(x,y,z).setType(Material.GLASS);
                    }
                }
        // Blokları arena kaydına sakla
        ayarlaOriginalBlocks.put(arenaId, savedBlocks);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();

        // ─── Eşyalar dağılmasın — tek noktada düşsün ───
        e.setKeepInventory(false);
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        e.getDrops().clear(); // varsayılan dağıtımı iptal et

        Player killer = dead.getKiller();
        deathRecords.add(0, new DeathRecord(dead,
            killer != null ? killer.getName() : "Bilinmiyor",
            dead.getInventory().getContents()));

        // ─── Totem patlaması ───
        if (killer != null) {
            killer.playSound(killer.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
            dead.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                dead.getLocation().add(0, 1, 0), 30, 0.3, 0.3, 0.3, 0.2);
        }

        Location dropLoc = dead.getLocation().clone();

        // ─── Duel veya 1v1 ayarla arena ölümü: eşyaları duel sandığına koy, direkt envantere ver ───
        boolean isDuelDeath = playerDuel.containsKey(dead.getUniqueId())
                || playerAyarlaArena.containsKey(dead.getUniqueId());

        if (isDuelDeath) {
            // ── Sandığı kim alacak: önce gerçek öldüren, yoksa (çevresel ölüm vs.) arenadaki rakip ──
            Player duelLootRecipient = killer;
            if (duelLootRecipient == null) {
                String dArenaEarly = playerDuel.get(dead.getUniqueId());
                if (dArenaEarly != null) {
                    UUID[] dpEarly = duelPlayers.get(dArenaEarly);
                    if (dpEarly != null) for (UUID u : dpEarly) if (!u.equals(dead.getUniqueId())) duelLootRecipient = Bukkit.getPlayer(u);
                }
                if (duelLootRecipient == null) {
                    String aArenaEarly = playerAyarlaArena.get(dead.getUniqueId());
                    if (aArenaEarly != null) {
                        UUID[] apsEarly = ayarlaQueue.get(aArenaEarly);
                        if (apsEarly != null) for (UUID u : apsEarly) if (u != null && !u.equals(dead.getUniqueId())) duelLootRecipient = Bukkit.getPlayer(u);
                    }
                }
            }
            giveDuelLootChest(dead, duelLootRecipient, dropLoc);
            // ── Ölüm sonrası komut kilidi başlasın (10sn hiçbir şey, sonra sadece /ec ve /pv) ──
            duelDeathTime.put(dead.getUniqueId(), System.currentTimeMillis());
        } else {
            // ─── Normal ölüm: Envanteri tek noktaya dök (dağılmadan) ───
            ItemStack[] inv = dead.getInventory().getContents();
            for (ItemStack it : inv) {
                if (it == null || it.getType() == Material.AIR) continue;
                org.bukkit.entity.Item dropped = dead.getWorld().dropItem(dropLoc, it.clone());
                dropped.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                dropped.setPickupDelay(40);
            }
            dead.getInventory().clear();
        }

        // ─── Stash varsa her durumda normal item olarak tek noktaya dök ───
        ItemStack[] stashContents = playerStash.remove(dead.getUniqueId());
        stashOpen.put(dead.getUniqueId(), false);
        if (stashContents != null) {
            for (ItemStack it : stashContents) {
                if (it == null || it.getType() == Material.AIR) continue;
                org.bukkit.entity.Item dropped = dead.getWorld().dropItem(dropLoc, it.clone());
                dropped.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                dropped.setPickupDelay(40);
            }
        }

        // ─── Duel ölüm ───
        String dArena = playerDuel.get(dead.getUniqueId());
        if (dArena != null) {
            UUID[] dp = duelPlayers.get(dArena);
            Player dWinner = null;
            if (dp != null) for (UUID u : dp) if (!u.equals(dead.getUniqueId())) dWinner = Bukkit.getPlayer(u);
            endDuel(dArena, dWinner, dead);
        }

        // 1v1 wand arena — ölünce arena aç
        if (playerArena.containsKey(dead.getUniqueId())) {
            String arenaId = playerArena.get(dead.getUniqueId());
            UUID[] players = arenaPlayers.get(arenaId);
            if (players != null) {
                for (UUID uid : players) {
                    Player ap = Bukkit.getPlayer(uid);
                    if (ap != null) {
                        ap.sendTitle(
                            color("&#ff4444&l⚔ 1v1 BİTTİ ⚔"),
                            color("&#ffcc00" + dead.getName() + " &felendi!"),
                            5, 60, 15);
                        ap.playSound(ap.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                    }
                    playerArena.remove(uid);
                }
                BoundingBox bbox = arenaBounds.get(players[0]);
                Material wallMat = arenaBlock.get(players[0]);
                if (bbox != null && wallMat != null && dead.getWorld() != null) {
                    removeArenaBorder(dead.getWorld(), bbox, wallMat);
                }
                arenaPlayers.remove(arenaId);
                arenaBounds.remove(players[0]);
                arenaBlock.remove(players[0]);
            }
        }

        // 1v1ayarla arena — ölünce duvarları kaldır, 6sn geri sayım, döngü
        if (playerAyarlaArena.containsKey(dead.getUniqueId())) {
            String arenaId = playerAyarlaArena.get(dead.getUniqueId());
            UUID[] aps = ayarlaQueue.get(arenaId);
            BoundingBox bbox = ayarlaBounds.get(arenaId);

            // Kazanan kim
            Player winner1v1 = null;
            if (aps != null) {
                for (UUID uid : aps) {
                    if (uid == null) continue;
                    if (!uid.equals(dead.getUniqueId())) {
                        winner1v1 = Bukkit.getPlayer(uid);
                    }
                    // Her ikisini de map'ten çıkar
                    playerAyarlaArena.remove(uid);
                }
            }
            // Öleni de map'ten çıkar (yukarıda döngüde çıkartılmış olmalı ama garantilemek için)
            playerAyarlaArena.remove(dead.getUniqueId());

            final Player finalWinner = winner1v1;

            // Kazanana title
            if (finalWinner != null && finalWinner.isOnline()) {
                finalWinner.sendTitle(
                    duelFlop("🏆 1v1 KAZANDIN!", true),
                    color("&#ff4444" + dead.getName() + " &felendi!"),
                    5, 60, 15);
                finalWinner.playSound(finalWinner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                finalWinner.sendMessage(color(""));
                finalWinner.sendMessage(color("  " + duelFlop("🏆 1v1 KAZANDIN!", true)));
                finalWinner.sendMessage(color("  &#aaaaaa&o" + dead.getName() + " elendin!"));
                finalWinner.sendMessage(color(""));
            }

            // Camları kaldır + 6'dan geriye sayım
            if (bbox != null && dead.getWorld() != null) {
                final World arenaWorld = dead.getWorld();
                final BoundingBox finalBbox = bbox;
                final String finalArenaId = arenaId;

                new BukkitRunnable() {
                    int sec = 6;
                    @Override public void run() {
                        if (sec > 0) {
                            // Geri sayımı kazanana göster
                            if (finalWinner != null && finalWinner.isOnline()) {
                                finalWinner.sendTitle(
                                    duelFlop("⌛ " + sec + " saniye", true),
                                    color("&#aaaaaa&oCamlar açılıyor..."),
                                    0, 25, 5);
                                finalWinner.playSound(finalWinner.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
                            }
                            sec--;
                        } else {
                            cancel();
                            // Camları kaldır — yani koyduğumuz cam bloklarını AIR yap
                            openAyarlaArena(finalArenaId, finalBbox, arenaWorld);
                            // Queue'yu sıfırla — döngü: bir dahaki oyuncu girince yeniden kapanır
                            // Bounds ve arenas silinmesin ki arena tekrar kullanılabilsin!
                            ayarlaQueue.put(finalArenaId, new UUID[2]);

                            if (finalWinner != null && finalWinner.isOnline()) {
                                finalWinner.sendTitle(
                                    duelFlop("✦ ARENA AÇILDI ✦", true),
                                    color("&#aaaaaa&oYeni oyuncular girebilir"),
                                    5, 50, 15);
                                finalWinner.sendMessage(color("  " + duelFlop("✦ Arena camları açıldı! Yeni oyuncular girebilir.", true)));
                                finalWinner.playSound(finalWinner.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.2f);
                                // Kazanan da spawna ışınlansın — eskiden burada ışınlanmıyordu
                                teleportToSpawn(finalWinner);
                            }
                        }
                    }
                }.runTaskTimer(this, 0L, 20L);
            }

            // SADECE queue'yu sıfırla — bounds/arenas/walls silinmesin, arena yeniden kullanılacak!
            ayarlaQueue.put(arenaId, new UUID[2]);
            // walls ve originalBlocks'u KORUYARAK sadece aktif savaş datasını temizle
            // (ayarlaArenas, ayarlaBounds, ayarlaWalls silinmez — arena hâlâ var)
        }
    }

    // ─── Respawn'da gece görüşünü hemen uygula + duel/1v1 arenadan çıkınca spawna ışınla ───
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) return;
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.NIGHT_VISION, 1200, 0, false, false, false), true);
                // Duel veya 1v1 arenada öldüyse spawna ışınla
                teleportToSpawn(p);
            }
        }.runTaskLater(this, 2L);
    }

    /** Ayarla arena camlarını kaldır (AIR yap) — döngü için */
    private void openAyarlaArena(String arenaId, BoundingBox bbox, World world) {
        int minX = (int) bbox.getMinX(); int maxX = (int) bbox.getMaxX();
        int minY = (int) bbox.getMinY(); int maxY = (int) bbox.getMaxY();
        int minZ = (int) bbox.getMinZ(); int maxZ = (int) bbox.getMaxZ();
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    boolean isYanKenar = (x==minX||x==maxX||z==minZ||z==maxZ) && y > minY;
                    boolean isTavan   = (y==maxY);
                    if (isYanKenar || isTavan) {
                        Block b = world.getBlockAt(x,y,z);
                        if (b.getType() == Material.GLASS) b.setType(Material.AIR);
                    }
                }
        // Bounds ve arenas koru — arena silinmesin, sadece camlar açılsın
    }

    private void removeArenaBorderByBBox(World world, BoundingBox bbox) {
        int minX = (int) bbox.getMinX(); int maxX = (int) bbox.getMaxX();
        int minY = (int) bbox.getMinY(); int maxY = (int) bbox.getMaxY();
        int minZ = (int) bbox.getMinZ(); int maxZ = (int) bbox.getMaxZ();
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType() != Material.AIR) b.setType(Material.AIR);
                }
    }

    private void removeArenaBorder(World world, BoundingBox bbox, Material mat) {
        int minX = (int) bbox.getMinX(); int maxX = (int) bbox.getMaxX();
        int minY = (int) bbox.getMinY(); int maxY = (int) bbox.getMaxY();
        int minZ = (int) bbox.getMinZ(); int maxZ = (int) bbox.getMaxZ();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType() == mat) b.setType(Material.AIR);
                }
            }
        }
    }

    // Blok adımı — 1v1 arena içinde blok oluşumu
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!playerArena.containsKey(p.getUniqueId())) return;
        // Adım sesi + block
        Location from = e.getFrom(); Location to = e.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;
        // Ayak altındaki block
        Block under = to.clone().add(0, -1, 0).getBlock();
        if (under.getType() == Material.AIR) {
            under.setType(Material.DIRT); // Her oyuncunun bastığı yere blok
            p.playSound(p.getLocation(), Sound.BLOCK_GRASS_STEP, 0.4f, 1.2f);
        }
    }

    // ─────────────────────────────────────────────
    //  KORUMA SİSTEMİ — HASAR
    // ─────────────────────────────────────────────
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player pa) attacker = pa;
        else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player pa) attacker = pa;

        long now = System.currentTimeMillis();
        // Victim korumalı mı?
        if (protectedUntil.getOrDefault(victim.getUniqueId(), 0L) > now) {
            e.setCancelled(true);
            if (attacker != null) {
                attacker.sendMessage(color("&#ff4444&lSilvera &8» &cBu Oyuncu Koruma Altında, Ona vuramazsın!"));
            }
            return;
        }
        // Attacker korumalı mı?
        if (attacker != null && protectedUntil.getOrDefault(attacker.getUniqueId(), 0L) > now) {
            e.setCancelled(true);
            attacker.sendMessage(color("&#ff4444&lSilvera &8» &cKoruma Altındayken Oyunculara Hasar Veremezsin!"));
        }
    }

    // Koruma altındayken yerden eşya alma engeli
    @EventHandler
    public void onPickup(PlayerPickupItemEvent e) {
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        if (protectedUntil.getOrDefault(p.getUniqueId(), 0L) > now) {
            e.setCancelled(true);
            // Spam olmadan uyarı — 5 saniyede bir
            long lastWarn = wrongKeyCooldown.getOrDefault(p.getUniqueId(), 0L);
            if (lastWarn + 5000L < now) {
                wrongKeyCooldown.put(p.getUniqueId(), now);
                p.sendTitle(color("&#ff4444&l⚠ Koruma Aktif"), color("&#ffcc00Koruma altındayken eşya alamazsın!"), 5, 40, 10);
            }
        }
    }

    // İlk giriş koruma + bakım kontrolü
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();

        // ─── Bakım modu kontrolü ───
        if (bakimModu && !p.isOp() && !p.hasPermission("loginx.admin") && !bakimIzinli.contains(p.getUniqueId())) {
            // 1 tick sonra at (join tamamlansın)
            new BukkitRunnable() {
                @Override public void run() {
                    p.kickPlayer(color(
                        "&#0099ff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                        "&#ffffff&l      ⚙  SUNUCU BAKIMDA  ⚙\n" +
                        "&#0099ff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                        "\n" +
                        "&#aaaaaa  Sunucumuz şu an bakım ve\n" +
                        "&#aaaaaa  güncelleme işlemleri için\n" +
                        "&#aaaaaa  geçici olarak kapatılmıştır.\n" +
                        "\n" +
                        "&#ffcc00  ✦ &fBakım tamamlandığında\n" +
                        "&#ffcc00  ✦ &ftekrar bağlanabilirsin.\n" +
                        "\n" +
                        "&#0099ff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                        "&#555555  Anlayışın için teşekkürler!"
                    ));
                }
            }.runTaskLater(this, 1L);
            return;
        }

        // ─── Bakımdayken girdi — admin/izinli için bilgi ver ───
        if (bakimModu && (p.isOp() || p.hasPermission("loginx.admin") || bakimIzinli.contains(p.getUniqueId()))) {
            new BukkitRunnable() {
                @Override public void run() {
                    if (!p.isOnline()) return;
                    p.sendTitle(
                        color("&#ffcc00&l⚙ BAKIM MODU ⚙"),
                        color("&#aaaaaa&oSunucu şu an bakımdadır"),
                        5, 100, 20);
                    p.sendMessage(color(""));
                    p.sendMessage(color("  &#0099ff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                    p.sendMessage(color("  &#ffcc00&l⚙ &r&#ffffff&lSUNUCU BAKIM MODU &r&#ffcc00&l⚙"));
                    p.sendMessage(color("  &#0099ff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                    p.sendMessage(color("  &#aaaaaa Sunucu şu an bakım modundadır."));
                    p.sendMessage(color("  &#aaaaaa Yalnızca yetkili ve izinli oyuncular"));
                    p.sendMessage(color("  &#aaaaaa şu an sunucuya bağlanabilir."));
                    p.sendMessage(color(""));
                    p.sendMessage(color("  &#00ff88» &e/bakimdancikar &fkomutu ile bakımı kapatabilirsin."));
                    p.sendMessage(color("  &#00ff88» &e/bakimaoyuncual &f<oyuncu> ile oyuncuya izin verebilirsin."));
                    p.sendMessage(color("  &#0099ff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                    p.sendMessage(color(""));
                }
            }.runTaskLater(this, 20L);
        }

        // Kalıcı olarak kapattıysa verme
        boolean disabled = getConfig().getBoolean("protection_disabled." + p.getUniqueId().toString(), false);
        if (disabled) return;
        // Henüz aktif koruması yoksa yeni koruma başlat
        if (protectedUntil.getOrDefault(p.getUniqueId(), 0L) <= now) {
            protectedUntil.put(p.getUniqueId(), now + PROTECTION_MS);
            new BukkitRunnable() {
                @Override public void run() {
                    if (!p.isOnline()) return;
                    p.sendTitle(
                        color("&#00ccff&l✦ KORUMA AKTİF ✦"),
                        color("&#ffffff1 Saat boyunca koruma altındasın"),
                        5, 80, 20);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.5f);
                }
            }.runTaskLater(this, 20L);
        }
    }

    // Koruma geri sayım tick
    private void tickProtectionCountdowns() {
        // Her 20 tick = 1 saniye yaklaşık (3*20 = 60 tick = 3 sn'de bir kontrol)
        if (flopTick % 20 != 0) return;
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            long until = protectedUntil.getOrDefault(p.getUniqueId(), 0L);
            if (until <= now) continue;
            long kalan = until - now;
            long saat = kalan / 3_600_000L;
            long dak  = (kalan % 3_600_000L) / 60_000L;
            long sn   = (kalan % 60_000L) / 1_000L;
            String timeStr = (saat > 0 ? saat + "s " : "") + (dak > 0 ? dak + "d " : "") + sn + "sn";
            p.sendActionBar(color("&#00ccff⛨ Koruma: &f" + timeStr + " &8| &7/korumakapat ile kapat"));
            // Süre bittiyse bildir
            if (kalan < 5000L && kalan > 0L) {
                p.sendTitle(color("&#ff4444&l⚠ Koruma Bitiyor!"), color("&#ffcc00Artık savunmasız olacaksın"), 3, 30, 10);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
            }
        }
    }

    // ─────────────────────────────────────────────
    //  KUMAR MENÜSÜ AYARLARI
    // ─────────────────────────────────────────────
    private void openKumarMenuSettings(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&#ffcc00&l⚙ Kumar Menüsü Ayarları"));
        ItemStack fill = makeBorderGlass("&#0f0f1e");
        for (int i = 0; i < 54; i++) inv.setItem(i, fill);

        ItemStack title = new ItemStack(Material.NETHER_STAR); ItemMeta tm = title.getItemMeta();
        tm.setDisplayName(color("&#ffcc00&l✦ Kumar Hızı Seç ✦"));
        List<String> tl = new ArrayList<>();
        tl.add(color("  &#555577▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        tl.add(color("  &#ffffff&lKasayı açtığında animasyonun"));
        tl.add(color("  &#aaaaaa&lne kadar süreceğini seç."));
        tl.add(color("  &#aaaaaa&oTüm kasalara uygulanır."));
        tl.add(color("  &#555577▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        tm.setLore(tl); title.setItemMeta(tm); inv.setItem(4, title);

        // SİMETRİK: 7 buton — 4 üst + 3 alt, ortada
        String[][] opts = {
            {"1 Saniye","20","Cok Hızlı ⚡","&#00ff88","LIME_STAINED_GLASS_PANE"},
            {"2 Saniye","40","Standart ✦","&#00ccff","CYAN_STAINED_GLASS_PANE"},
            {"3 Saniye","60","Orta ●","&#4488ff","BLUE_STAINED_GLASS_PANE"},
            {"4 Saniye","80","Uzun ★","&#aa44ff","PURPLE_STAINED_GLASS_PANE"},
            {"5 Saniye","100","Epey Uzun ◆","&#ff44ff","MAGENTA_STAINED_GLASS_PANE"},
            {"6 Saniye","120","Çok Uzun ☆","&#ff8800","ORANGE_STAINED_GLASS_PANE"},
            {"7 Saniye","140","Maksimum ♦","&#ff3333","RED_STAINED_GLASS_PANE"}};
        // Simetrik: 19,21,23,25 (üst 4) + 29,31,33 (alt 3)
        int[] slots = {19, 21, 23, 25, 29, 31, 33};
        String[] stars = {"★★★★★★★","★★★★★★☆","★★★★★☆☆","★★★★☆☆☆","★★★☆☆☆☆","★★☆☆☆☆☆","★☆☆☆☆☆☆"};

        for (int i = 0; i < 7; i++) {
            Material mat = Material.matchMaterial(opts[i][4]);
            if (mat == null) mat = Material.GRAY_STAINED_GLASS_PANE;
            ItemStack btn = new ItemStack(mat); ItemMeta bm = btn.getItemMeta();
            bm.setDisplayName(color(opts[i][3] + "&l" + opts[i][0]));
            List<String> l = new ArrayList<>();
            l.add(color("  " + opts[i][3] + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            l.add(color("  &#ffffff&l" + opts[i][2]));
            l.add(color(""));
            l.add(color("  &7Süre:  " + opts[i][3] + "&l" + opts[i][0]));
            l.add(color("  &7Hız:   &#ffffff" + stars[i]));
            l.add(color("  &7Durum: &#aaaaaa" + (i == 1 ? "&#00ff88✔ Varsayılan" : "Pasif")));
            l.add(color(""));
            l.add(color("  " + opts[i][3] + "» &fTüm kasalara uygulanır"));
            l.add(color("  " + opts[i][3] + "» Seçmek için tıkla!"));
            l.add(color("  " + opts[i][3] + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            l.add(color("&8TICK:" + opts[i][1]));
            bm.setLore(l); btn.setItemMeta(bm); inv.setItem(slots[i], btn);
        }
        inv.setItem(49, makeCloseHead(p));
        p.openInventory(inv);
    }

    // ─────────────────────────────────────────────
    //  EFEKT SEÇİM MENÜSÜ — 48 EFEKT
    // ─────────────────────────────────────────────
    private void openEffectSelectMenu(Player p, String preKasa) {
        openEffectSelectMenuPage(p, preKasa, 0);
    }

    private static final Object[][] ALL_EFFECTS = {
        {"varsayilan","Varsayılan",  "&#aaaaaa","Çift orbital halka",               Material.WHITE_DYE},
        {"fireworks", "Havai Fişek","&#ff6600","Rengarenk firework yağışı",         Material.FIREWORK_ROCKET},
        {"temiz",     "Sade Temiz", "&#ffffff","İnce 6'lı orbital halka",           Material.GRAY_DYE},
        {"yildiz",    "Yıldız",     "&#ffdd00","Crit + END_ROD yıldız yağmuru",     Material.GOLD_NUGGET},
        {"kristal",   "Kristal",    "&#00ccff","Witch + Portal mistik efekti",      Material.DIAMOND},
        {"alev",      "Alev",       "&#ff4400","Flame + Lava ateş topu",            Material.BLAZE_POWDER},
        {"buz",       "Buz",        "&#88ddff","Snowflake buz mağarası",            Material.ICE},
        {"ejderha",   "Ejderha",    "&#aa00ff","Dragon Breath mor spiral",          Material.PURPLE_STAINED_GLASS_PANE},
        {"gokkusagi", "Gökkuşağı",  "&#ff88ff","8 nokta çift dönen halka",          Material.PRISMARINE_CRYSTALS},
        {"vortex",    "Vortex",     "&#cc44ff","Portal içe çeken spiral",           Material.ENDER_EYE},
        {"buyutozu",  "Büyü Tozu",  "&#8844ff","Enchant + Witch partikülleri",      Material.ENCHANTED_BOOK},
        {"simsek",    "Şimşek",     "&#eeeeff","Crit flash + END_ROD patlaması",    Material.LIGHTNING_ROD},
        {"doga",      "Doğa",       "&#44ff44","Villager + yaprak yeşil efekt",     Material.OAK_LEAVES},
        {"hayalet",   "Hayalet",    "&#5599ff","Soul + duman mavi hayalet efekti",  Material.SOUL_LANTERN},
        {"neon",      "Neon",       "&#00eeff","Elektrik mavisi 8'li halka",        Material.LIGHT_BLUE_DYE},
        {"patlama",   "Patlama",    "&#ff2200","Explosion + alev kırmızı",          Material.TNT},
        {"kum",       "Çöl Kumu",   "&#ddaa55","Kum partikülleri orbital",          Material.SAND},
        {"mor",       "Mor Büyü",   "&#bb44ff","Witch + Portal mor fırtına",        Material.AMETHYST_SHARD},
        {"demir",     "Metalik",    "&#aaaacc","Crit + duman metalik halka",        Material.IRON_INGOT},
        {"okyanus",   "Okyanus",    "&#0088cc","Su baloncukları + damlalar",        Material.PRISMARINE_SHARD},
        {"sicaklik",  "Lavlar",     "&#ff6600","Lav damlası + ısı efekti",          Material.LAVA_BUCKET},
        {"nitro",     "Nitro",      "&#00ffcc","Süper hızlı dönen END_ROD",         Material.CYAN_DYE},
        {"zehir",     "Zehir",      "&#55ff55","Yeşil witch + köy efekti",          Material.POISONOUS_POTATO},
        {"enerji",    "Enerji",     "&#ffff00","Dinamik büyüyen crit halkası",      Material.GLOWSTONE_DUST},
        {"duman",     "Duman",      "&#666677","Smoke + büyük duman bulutu",        Material.GUNPOWDER},
        {"yay",       "Yay",        "&#ffaa00","Hızlı dönen üçlü ok efekti",        Material.ARROW},
        {"bulut",     "Bulut",      "&#ddeeff","Yüzen cloud partikülleri",          Material.FEATHER},
        {"elmas",     "Elmas",      "&#aaffee","Parlayan END_ROD dalgası",          Material.AMETHYST_SHARD},
        {"kan",       "Kan",        "&#cc0000","Kırmızı damlayan efekt",            Material.REDSTONE},
        {"galaksi",   "Galaksi",    "&#223366","Spiral galaksi kolu",               Material.NETHER_STAR},
        {"prizma",    "Prizma",     "&#00ccbb","Guardian lazer efekti",             Material.PRISMARINE_CRYSTALS},
        {"sarap",     "Şarap",      "&#990033","Ters dönen witch halkası",          Material.BEETROOT},
        {"karanlık",  "Karanlık",   "&#334455","Deep Dark soul efekti",             Material.SCULK},
        {"pembe",     "Pembe",      "&#ff88cc","Kawaii villager + firework",        Material.PINK_DYE},
        {"altin",     "Altın",      "&#ffcc00","Crit parıltı + firework",           Material.GOLD_INGOT},
        {"gece",      "Gece",       "&#111133","Yıldız yağmuru rastgele",           Material.BLACK_DYE},
        {"nuke",      "Nükleer",    "&#556655","Duman + küçük patlama",             Material.TNT_MINECART},
        {"ufo",       "UFO Işını",  "&#88ffcc","Dalgalı dikey ışık halkası",        Material.SPYGLASS},
        {"gokkusagi2","Gökkuşağı+", "&#ff44ff","Üçlü dönen halka gökkuşağı",       Material.BUNDLE},
        {"ahtapot",   "Ahtapot",    "&#4455bb","8 adet su damlası kolu",            Material.INK_SAC},
        {"masal",     "Masal",      "&#ffddee","Sparkle büyü + villager",           Material.PINK_PETALS},
        {"ejderha2",  "Ejderha+",   "&#880088","Çift dragon breath katmanı",        Material.DRAGON_EGG},
        {"kar",       "Kar Fırtınası","&#eeeeff","Yoğun snowflake tornado",          Material.SNOWBALL},
        {"volkan",    "Volkan",     "&#ff3300","Lav fışkırması + alev",             Material.MAGMA_BLOCK},
        {"hız",       "Hız Çizgisi","&#00ffaa","İçe spiral hız efekti",            Material.SUGAR},
        {"orak",      "Orak",       "&#445544","Köy + witch döngü efekti",          Material.TURTLE_HELMET},
        {"kristal2",  "Kristal+",   "&#cceeff","END_ROD + witch çift katman",       Material.QUARTZ},
        {"girdap",    "Girdap",     "&#ff5500","Büyük dışa açılan portal spiral",   Material.HEART_OF_THE_SEA},
    };

    private void openEffectSelectMenuPage(Player p, String preKasa, int page) {
        int perPage = 28; // 4 satır × 7 slot (10-16, 19-25, 28-34, 37-43)
        int totalPages = (int) Math.ceil((double) ALL_EFFECTS.length / perPage);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        Inventory inv = Bukkit.createInventory(null, 54, color("&#aa44ff&l✦ Efekt Secimi"));
        ItemStack fill = makeBorderGlass("&#0d0d1a");
        for (int i = 0; i < 54; i++) inv.setItem(i, fill);

        ItemStack header = new ItemStack(Material.AMETHYST_SHARD); ItemMeta hm = header.getItemMeta();
        hm.setDisplayName(color("&#aa44ff&l✦ Efekt Seç &8(" + ALL_EFFECTS.length + " efekt)"));
        List<String> hl = new ArrayList<>();
        hl.add(color("  &#553388▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        hl.add(color("  &#ffffff&lKasa etrafında görünecek efekti seç"));
        hl.add(color("  &#aaaaaa&oUygulama: " + (preKasa.isEmpty() ? "&#ffcc00Tüm Kasalar" : "&#00ccff" + capitalize(preKasa) + " Kasası")));
        hl.add(color("  &#aaaaaa&oSayfa: " + (page + 1) + "/" + totalPages));
        hl.add(color("  &#553388▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        hm.setLore(hl); header.setItemMeta(hm); inv.setItem(4, header);

        // 4 satır: 10-16, 19-25, 28-34, 37-43
        int[] slots2 = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};
        String[] icons = {"◎","✿","◈","★","◆","♦","❄","☽","◉","◌","✦","⚡","✾","☁","◍","⊛","⊕","⊗","⊞","⊡","⊠","⊟","⊝","⊜","⊙","⊚","◐","◑"};

        int start = page * perPage;
        for (int i = 0; i < perPage && (start + i) < ALL_EFFECTS.length; i++) {
            int idx = start + i;
            String id    = (String)   ALL_EFFECTS[idx][0];
            String label = (String)   ALL_EFFECTS[idx][1];
            String clr2  = (String)   ALL_EFFECTS[idx][2];
            String desc  = (String)   ALL_EFFECTS[idx][3];
            Material mat = (Material) ALL_EFFECTS[idx][4];
            ItemStack btn = new ItemStack(mat); ItemMeta bm = btn.getItemMeta();
            String icon = icons[i % icons.length];
            bm.setDisplayName(color(clr2 + icon + " " + label));
            List<String> l = new ArrayList<>();
            l.add(color("  " + clr2 + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            l.add(color("  &7" + desc));
            l.add(color(""));
            l.add(color("  &8» " + (preKasa.isEmpty() ? "&#ffcc00Tüm kasalara" : "&#00ccff" + capitalize(preKasa) + " kasasına") + " &fuygular"));
            l.add(color("  " + clr2 + "» Seçmek için tıkla!"));
            l.add(color("  " + clr2 + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            l.add(color("&8EFEKT:" + id + "|KASA:" + preKasa));
            bm.setLore(l); btn.setItemMeta(bm); inv.setItem(slots2[i], btn);
        }

        // Önceki sayfa — slot 45
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.SPECTRAL_ARROW); ItemMeta pm = prev.getItemMeta();
            pm.setDisplayName(color("&#ffcc00&l← Önceki Sayfa"));
            pm.setLore(Arrays.asList(color("&8EFEKTSAYFA:" + (page-1) + "|KASA:" + preKasa)));
            prev.setItemMeta(pm); inv.setItem(45, prev);
        }
        // Bilgi — slot 47
        inv.setItem(47, makeInfoItem());
        // Kapat — slot 49
        inv.setItem(49, makeCloseHead(p));
        // Sonraki sayfa — slot 53
        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW); ItemMeta nm = next.getItemMeta();
            nm.setDisplayName(color("&#ffcc00&l→ Sonraki Sayfa"));
            nm.setLore(Arrays.asList(color("&8EFEKTSAYFA:" + (page+1) + "|KASA:" + preKasa)));
            next.setItemMeta(nm); inv.setItem(53, next);
        }
        p.openInventory(inv);
    }

    private ItemStack makeInfoItem() {
        ItemStack it = new ItemStack(Material.BOOK); ItemMeta im = it.getItemMeta();
        im.setDisplayName(color("&#ffcc00&lBilgi"));
        im.setLore(Arrays.asList(color("&7/kasafarkliefektler &ekasa_ismi"), color("&7ile belirli bir kasayi sec.")));
        it.setItemMeta(im); return it;
    }

    // ─────────────────────────────────────────────
    //  KASA EFEKTLERİ
    // ─────────────────────────────────────────────
    private void updateCrateEffects() {
        for (Map.Entry<String, Location> entry : crates.entrySet()) {
            if (!crateEffects.getOrDefault(entry.getKey(), false)) continue;
            Location base = entry.getValue().clone().add(0.5, 0.5, 0.5);
            double t = flopTick * 0.10;
            switch (crateEffectType.getOrDefault(entry.getKey(), "varsayilan")) {

                case "varsayilan" -> {
                    for (int i = 0; i < 4; i++) {
                        double a = t + (Math.PI*2.0/4)*i;
                        base.getWorld().spawnParticle(Particle.END_ROD, base.clone().add(0.6*Math.cos(a),0.3,0.6*Math.sin(a)),1,0,0,0,0);
                    }
                    if (flopTick%6==0) for (int i=0;i<4;i++) {
                        double a = -t*0.7+(Math.PI*2.0/4)*i;
                        base.getWorld().spawnParticle(Particle.END_ROD, base.clone().add(0.45*Math.cos(a),0.5+Math.sin(t*0.5+i)*0.1,0.45*Math.sin(a)),1,0,0,0,0);
                    }
                }

                case "fireworks" -> {
                    if (flopTick%8==0) base.getWorld().spawnParticle(Particle.FIREWORK,base.clone().add(0,1.2,0),5,0.25,0.2,0.25,0.04);
                    for (int i=0;i<4;i++) {
                        double a=t*1.2+(Math.PI*2.0/4)*i;
                        base.getWorld().spawnParticle(Particle.FIREWORK,base.clone().add(0.55*Math.cos(a),0.4,0.55*Math.sin(a)),1,0,0,0,0.01);
                    }
                }

                case "temiz" -> {
                    if (flopTick%2==0) for (int i=0;i<6;i++) {
                        double a=t+(Math.PI*2.0/6)*i;
                        base.getWorld().spawnParticle(Particle.END_ROD,base.clone().add(0.5*Math.cos(a),0.25,0.5*Math.sin(a)),1,0,0,0,0);
                    }
                }

                case "yildiz" -> {
                    for (int i=0;i<5;i++) {
                        double a=t*0.8+(Math.PI*2.0/5)*i;
                        base.getWorld().spawnParticle(Particle.CRIT,base.clone().add(0.65*Math.cos(a),0.35,0.65*Math.sin(a)),1,0,0,0,0);
                    }
                    if (flopTick%4==0) base.getWorld().spawnParticle(Particle.END_ROD,base.clone().add(0,0.8,0),2,0.1,0.2,0.1,0.02);
                }

                case "kristal" -> {
                    for (int i=0;i<4;i++) {
                        double a=t*0.7+(Math.PI*2.0/4)*i;
                        base.getWorld().spawnParticle(Particle.WITCH,base.clone().add(0.6*Math.cos(a),0.35+Math.sin(t*0.5+i)*0.15,0.6*Math.sin(a)),1,0,0,0,0);
                    }
                    if (flopTick%5==0) base.getWorld().spawnParticle(Particle.PORTAL,base.clone().add(0,0.5,0),3,0.15,0.2,0.15,0.01);
                }

                case "alev" -> {
                    for (int i=0;i<4;i++) {
                        double a=t*1.1+(Math.PI*2.0/4)*i;
                        base.getWorld().spawnParticle(Particle.FLAME,base.clone().add(0.6*Math.cos(a),0.3,0.6*Math.sin(a)),1,0,0,0,0.003);
                    }
                    if (flopTick%6==0) base.getWorld().spawnParticle(Particle.LAVA,base.clone().add(0,0.3,0),1,0.2,0,0.2,0);
                }

                case "buz" -> {
                    for (int i=0;i<6;i++) {
                        double a=-t*0.6+(Math.PI*2.0/6)*i;
                        base.getWorld().spawnParticle(Particle.SNOWFLAKE,base.clone().add(0.55*Math.cos(a),0.3,0.55*Math.sin(a)),1,0,0,0,0.003);
                    }
                    if (flopTick%3==0) base.getWorld().spawnParticle(Particle.SNOWFLAKE,base.clone().add(0,0.6+Math.sin(t)*0.2,0),1,0.05,0.05,0.05,0.01);
                }

                // ─── YENİ EFEKTLER ───

                // Ejderha nefesi — mor + kırmızı spiral
                case "ejderha" -> {
                    for (int i=0;i<6;i++) {
                        double a=t*1.3+(Math.PI*2.0/6)*i;
                        double yy=0.2+Math.sin(t*0.8+i)*0.3;
                        base.getWorld().spawnParticle(Particle.DRAGON_BREATH,base.clone().add(0.65*Math.cos(a),yy,0.65*Math.sin(a)),1,0,0,0,0.005);
                    }
                    if (flopTick%4==0) base.getWorld().spawnParticle(Particle.FLAME,base.clone().add(0,0.7,0),2,0.1,0.05,0.1,0.02);
                }

                // Gökkuşağı dönen halka (6 renk END_ROD)
                case "gokkusagi" -> {
                    int seg=8;
                    for (int i=0;i<seg;i++) {
                        double a=t*0.9+(Math.PI*2.0/seg)*i;
                        base.getWorld().spawnParticle(Particle.END_ROD,base.clone().add(0.7*Math.cos(a),0.35,0.7*Math.sin(a)),1,0,0,0,0);
                    }
                    // İç halka ters
                    for (int i=0;i<seg;i++) {
                        double a=-t*0.7+(Math.PI*2.0/seg)*i;
                        base.getWorld().spawnParticle(Particle.FIREWORK,base.clone().add(0.4*Math.cos(a),0.5+Math.sin(t+i)*0.1,0.4*Math.sin(a)),1,0,0,0,0);
                    }
                }

                // Vortex — içe doğru çeken spiral
                case "vortex" -> {
                    for (int i=0;i<8;i++) {
                        double a=t*2.0+(Math.PI*2.0/8)*i;
                        double r=0.8-((flopTick%30)/30.0)*0.6;
                        double yy=0.1+(((flopTick%30)/30.0)*0.8);
                        base.getWorld().spawnParticle(Particle.PORTAL,base.clone().add(r*Math.cos(a),yy,r*Math.sin(a)),1,0,0,0,0);
                    }
                }

                // Büyü tozu — ENCHANT dönen
                case "buyutozu" -> {
                    if (flopTick%2==0) for (int i=0;i<5;i++) {
                        double a=t*0.9+(Math.PI*2.0/5)*i;
                        base.getWorld().spawnParticle(Particle.ENCHANT,base.clone().add(0.6*Math.cos(a),0.4+Math.sin(t+i)*0.2,0.6*Math.sin(a)),1,0,0,0,0.05);
                    }
                    if (flopTick%8==0) base.getWorld().spawnParticle(Particle.WITCH,base.clone().add(0,0.9,0),3,0.2,0.1,0.2,0.02);
                }

                // Şimşek — beyaz flash + kritik
                case "simsek" -> {
                    if (flopTick%3==0) for (int i=0;i<4;i++) {
                        double a=-t*1.5+(Math.PI*2.0/4)*i;
                        base.getWorld().spawnParticle(Particle.CRIT,base.clone().add(0.7*Math.cos(a),0.3,0.7*Math.sin(a)),2,0.02,0.02,0.02,0.1);
                    }
                    if (flopTick%12==0) base.getWorld().spawnParticle(Particle.END_ROD,base.clone().add(0,1.0,0),5,0.15,0.3,0.15,0.05);
                }

                // Doğa — yaprak + yeşil partiküller
                case "doga" -> {
                    for (int i=0;i<4;i++) {
                        double a=t*0.5+(Math.PI*2.0/4)*i;
                        base.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,base.clone().add(0.6*Math.cos(a),0.3+Math.sin(t*0.3+i)*0.15,0.6*Math.sin(a)),1,0,0,0,0);
                    }
                    if (flopTick%5==0) base.getWorld().spawnParticle(Particle.FALLING_DUST,
                        base.clone().add(0,0.7,0),2,0.2,0.1,0.2,0.02, Material.MOSS_BLOCK.createBlockData());
                }

                // Hayalet — SOUL + mavi
                case "hayalet" -> {
                    for (int i=0;i<5;i++) {
                        double a=t*0.6+(Math.PI*2.0/5)*i;
                        double yy=0.3+Math.sin(t*0.4+i*1.2)*0.25;
                        base.getWorld().spawnParticle(Particle.SOUL,base.clone().add(0.6*Math.cos(a),yy,0.6*Math.sin(a)),1,0,0,0,0.005);
                    }
                    if (flopTick%6==0) base.getWorld().spawnParticle(Particle.SMOKE,base.clone().add(0,0.6,0),2,0.15,0.2,0.15,0.01);
                }

                // ─── EK EFEKTLER (toplamda ~40) ───

                case "neon" -> { // Neon elektrik mavisi
                    for (int i=0;i<8;i++) {
                        double a=t*1.8+(Math.PI*2.0/8)*i;
                        base.getWorld().spawnParticle(Particle.END_ROD,base.clone().add(0.7*Math.cos(a),0.3+Math.sin(t+i*0.8)*0.15,0.7*Math.sin(a)),1,0,0,0,0);
                    }
                    if (flopTick%3==0) base.getWorld().spawnParticle(Particle.WITCH,base.clone().add(0,0.5,0),1,0.05,0.05,0.05,0.01);
                }

                case "patlama" -> { // Patlamalı kırmızı
                    if (flopTick%6==0) base.getWorld().spawnParticle(Particle.EXPLOSION,base.clone().add(0,0.5,0),1,0.2,0.1,0.2,0);
                    for (int i=0;i<4;i++) {
                        double a=-t*2.0+(Math.PI*2.0/4)*i;
                        base.getWorld().spawnParticle(Particle.FLAME,base.clone().add(0.6*Math.cos(a),0.4,0.6*Math.sin(a)),1,0,0,0,0.01);
                    }
                }

                case "kum" -> { // Çöl — kum partikülleri
                    for (int i=0;i<5;i++) {
                        double a=t*0.4+(Math.PI*2.0/5)*i;
                        base.getWorld().spawnParticle(Particle.FALLING_DUST,base.clone().add(0.6*Math.cos(a),0.4,0.6*Math.sin(a)),1,0,0,0,0.02,Material.SAND.createBlockData());
                    }
                }

                case "mor" -> { // Mor büyü
                    for (int i=0;i<6;i++) {
                        double a=t*0.9+(Math.PI*2.0/6)*i;
                        base.getWorld().spawnParticle(Particle.WITCH,base.clone().add(0.55*Math.cos(a),0.35+Math.sin(t+i)*0.1,0.55*Math.sin(a)),1,0,0,0,0);
                    }
                    if (flopTick%4==0) base.getWorld().spawnParticle(Particle.PORTAL,base.clone().add(0,0.7,0),3,0.1,0.2,0.1,0.02);
                }

                case "demir" -> { // Metalik gri
                    for (int i=0;i<4;i++) {
                        double a=t*0.8+(Math.PI*2.0/4)*i;
                        base.getWorld().spawnParticle(Particle.CRIT,base.clone().add(0.6*Math.cos(a),0.3,0.6*Math.sin(a)),1,0,0,0,0.01);
                    }
                    if (flopTick%5==0) base.getWorld().spawnParticle(Particle.SMOKE,base.clone().add(0,0.5,0),2,0.1,0.1,0.1,0.01);
                }

                case "okyanus" -> { // Okyanus — su baloncukları
                    for (int i=0;i<5;i++) {
                        double a=t*0.7+(Math.PI*2.0/5)*i;
                        base.getWorld().spawnParticle(Particle.BUBBLE_POP,base.clone().add(0.6*Math.cos(a),0.35+Math.sin(t+i)*0.2,0.6*Math.sin(a)),1,0,0,0,0.01);
                    }
                    if (flopTick%3==0) base.getWorld().spawnParticle(Particle.DRIPPING_WATER,base.clone().add(0,0.8,0),2,0.2,0,0.2,0.02);
                }

                case "sicaklik" -> { // Sıcak lav
                    for (int i=0;i<4;i++) {
                        double a=t*1.2+(Math.PI*2.0/4)*i;
                        base.getWorld().spawnParticle(Particle.DRIPPING_LAVA,base.clone().add(0.55*Math.cos(a),0.4,0.55*Math.sin(a)),1,0,0,0,0);
                    }
                    if (flopTick%4==0) base.getWorld().spawnParticle(Particle.LAVA,base.clone().add(0,0.3,0),1,0.15,0,0.15,0);
                }

                case "nitro" -> { // Nitro hızlanma
                    if (flopTick%2==0) for (int i=0;i<6;i++) {
                        double a=-t*2.5+(Math.PI*2.0/6)*i;
                        base.getWorld().spawnParticle(Particle.END_ROD,base.clone().add(0.6*Math.cos(a),0.3,0.6*Math.sin(a)),1,0,0,0,0);
                    }
                    if (flopTick%3==0) base.getWorld().spawnParticle(Particle.FIREWORK,base.clone().add(0,0.9,0),2,0.1,0.1,0.1,0.03);
                }

                case "zehir" -> { // Zehirli yeşil
                    for (int i=0;i<5;i++) {
                        double a=t*0.8+(Math.PI*2.0/5)*i;
                        base.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,base.clone().add(0.55*Math.cos(a),0.35+Math.sin(t*0.5+i)*0.1,0.55*Math.sin(a)),1,0,0,0,0);
                    }
                    if (flopTick%5==0) base.getWorld().spawnParticle(Particle.WITCH,base.clone().add(0,0.7,0),1,0.1,0.1,0.1,0.01);
                }

                case "enerji" -> { // Enerji patlaması
                    for (int i=0;i<4;i++) {
                        double a=t*1.5+(Math.PI*2.0/4)*i;
                        double r=0.3+Math.abs(Math.sin(t*2+i))*0.5;
                        base.getWorld().spawnParticle(Particle.CRIT,base.clone().add(r*Math.cos(a),0.4,r*Math.sin(a)),1,0,0,0,0.05);
                    }
                }

                case "duman" -> { // Duman
                    for (int i=0;i<4;i++) {
                        double a=t*0.5+(Math.PI*2.0/4)*i;
                        base.getWorld().spawnParticle(Particle.SMOKE,base.clone().add(0.55*Math.cos(a),0.3+Math.sin(t+i)*0.1,0.55*Math.sin(a)),1,0,0,0,0.005);
                    }
                    if (flopTick%4==0) base.getWorld().spawnParticle(Particle.LARGE_SMOKE,base.clone().add(0,0.7,0),1,0.1,0.1,0.1,0.005);
                }

                case "yay" -> { // Yay fırlama
                    if (flopTick%2==0) for (int i=0;i<3;i++) {
                        double a=t*3.0+(Math.PI*2.0/3)*i;
                        base.getWorld().spawnParticle(Particle.CRIT,base.clone().add(0.6*Math.cos(a),0.5,0.6*Math.sin(a)),2,0.03,0.03,0.03,0.1);
                    }
                }

                case "bulut" -> { // Bulut
                    if (flopTick%3==0) for (int i=0;i<5;i++) {
                        double a=t*0.3+(Math.PI*2.0/5)*i;
                        base.getWorld().spawnParticle(Particle.CLOUD,base.clone().add(0.6*Math.cos(a),0.4+Math.sin(t*0.2+i)*0.15,0.6*Math.sin(a)),1,0.02,0.02,0.02,0.01);
                    }
                }

                case "elmas" -> { // Elmas parıltı
                    for (int i=0;i<6;i++) {
                        double a=t*0.9+(Math.PI*2.0/6)*i;
                        base.getWorld().spawnParticle(Particle.END_ROD,base.clone().add(0.65*Math.cos(a),0.3+Math.sin(t*0.8+i)*0.2,0.65*Math.sin(a)),1,0,0,0,0);
                    }
                    if (flopTick%5==0) base.getWorld().spawnParticle(Particle.FIREWORK,base.clone().add(0,0.6,0),2,0.1,0.2,0.1,0.02);
                }

                case "kan" -> { // Kırmızı kan
                    if (flopTick%3==0) for (int i=0;i<4;i++) {
                        double a=t*1.3+(Math.PI*2.0/4)*i;
                        base.getWorld().spawnParticle(Particle.DRIPPING_WATER,base.clone().add(0.6*Math.cos(a),0.5,0.6*Math.sin(a)),1,0,0,0,0);
                    }
                    if (flopTick%6==0) base.getWorld().spawnParticle(Particle.FLAME,base.clone().add(0,0.3,0),1,0.15,0,0.15,0.01);
                }

                case "galaksi" -> { // Galaksi spiral
                    for (int i=0;i<12;i++) {
                        double a=t*0.5+(Math.PI*2.0/12)*i;
                        double r=0.2+(i*0.05);
                        double yy=0.1+(i*0.03);
                        base.getWorld().spawnParticle(Particle.END_ROD,base.clone().add(r*Math.cos(a),yy,r*Math.sin(a)),1,0,0,0,0);
                    }
                }

                case "prizma" -> { // Prizma Guardian
                    for (int i=0;i<4;i++) {
                        double a=t*0.7+(Math.PI*2.0/4)*i;
                        base.getWorld().spawnParticle(Particle.END_ROD,base.clone().add(0.5*Math.cos(a),0.35,0.5*Math.sin(a)),1,0,0,0,0);
                    }
                    if (flopTick%4==0) base.getWorld().spawnParticle(Particle.WITCH,base.clone().add(0,0.7,0),2,0.15,0.15,0.15,0.02);
                }

                case "sarap" -> { // Şarap kırmızısı
                    for (int i=0;i<5;i++) {
                        double a=-t*0.9+(Math.PI*2.0/5)*i;
                        base.getWorld().spawnParticle(Particle.WITCH,base.clone().add(0.55*Math.cos(a),0.3+Math.sin(t+i)*0.15,0.55*Math.sin(a)),1,0,0,0,0);
                    }
                    if (flopTick%8==0) base.getWorld().spawnParticle(Particle.PORTAL,base.clone().add(0,0.6,0),2,0.1,0.1,0.1,0.01);
                }

                case "karanlık" -> { // Karanlık deep dark
                    for (int i=0;i<5;i++) {
                        double a=t*0.6+(Math.PI*2.0/5)*i;
                        base.getWorld().spawnParticle(Particle.SOUL,base.clone().add(0.6*Math.cos(a),0.3+Math.sin(t+i)*0.2,0.6*Math.sin(a)),1,0,0,0,0.003);
                    }
                    if (flopTick%4==0) base.getWorld().spawnParticle(Particle.SMOKE,base.clone().add(0,0.6,0),2,0.15,0.1,0.15,0.005);
                }

                case "pembe" -> { // Pembe kawaii
                    for (int i=0;i<6;i++) {
                        double a=t*0.7+(Math.PI*2.0/6)*i;
                        base.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,base.clone().add(0.55*Math.cos(a),0.35+Math.sin(t*0.5+i)*0.1,0.55*Math.sin(a)),1,0,0,0,0);
                    }
                    if (flopTick%5==0) base.getWorld().spawnParticle(Particle.FIREWORK,base.clone().add(0,0.8,0),2,0.2,0.1,0.2,0.02);
                }

                case "altin" -> { // Altın
                    for (int i=0;i<5;i++) {
                        double a=t*0.9+(Math.PI*2.0/5)*i;
                        base.getWorld().spawnParticle(Particle.CRIT,base.clone().add(0.6*Math.cos(a),0.35+Math.sin(t+i*0.5)*0.15,0.6*Math.sin(a)),1,0,0,0,0.02);
                    }
                    if (flopTick%6==0) base.getWorld().spawnParticle(Particle.FIREWORK,base.clone().add(0,0.7,0),1,0.1,0.1,0.1,0.02);
                }

                case "gece" -> { // Gece yıldızları
                    if (flopTick%2==0) for (int i=0;i<8;i++) {
                        double a=t*0.3+(Math.PI*2.0/8)*i;
                        double r=0.3+(Math.random()*0.5);
                        base.getWorld().spawnParticle(Particle.END_ROD,base.clone().add(r*Math.cos(a),Math.random()*0.8,r*Math.sin(a)),1,0,0,0,0);
                    }
                }

                case "nuke" -> { // Nükleer
                    for (int i=0;i<6;i++) {
                        double a=t*2.0+(Math.PI*2.0/6)*i;
                        base.getWorld().spawnParticle(Particle.SMOKE,base.clone().add(0.6*Math.cos(a),0.3,0.6*Math.sin(a)),1,0.01,0.01,0.01,0.02);
                    }
                    if (flopTick%8==0) base.getWorld().spawnParticle(Particle.EXPLOSION,base.clone().add(0,0.6,0),1,0.15,0.1,0.15,0);
                }

                case "ufo" -> { // UFO ışını
                    for (int i=0;i<8;i++) {
                        double a=t*0.4+(Math.PI*2.0/8)*i;
                        double yy=0.1+Math.abs(Math.sin(t*0.8))*0.7;
                        base.getWorld().spawnParticle(Particle.END_ROD,base.clone().add(0.65*Math.cos(a),yy,0.65*Math.sin(a)),1,0,0,0,0);
                    }
                }

                case "gökkusagi2" -> { // Gökkuşağı v2 — üçlü halka
                    for (int ring=0;ring<3;ring++) {
                        double rr = 0.35+ring*0.2;
                        double speed2 = 0.8+ring*0.4;
                        int dir = ring%2==0?1:-1;
                        for (int i=0;i<6;i++) {
                            double a=dir*t*speed2+(Math.PI*2.0/6)*i;
                            base.getWorld().spawnParticle(Particle.FIREWORK,base.clone().add(rr*Math.cos(a),0.3+ring*0.15,rr*Math.sin(a)),1,0,0,0,0);
                        }
                    }
                }

                case "ahtapot" -> { // Ahtapot kolları
                    for (int i=0;i<8;i++) {
                        double a=(Math.PI*2.0/8)*i;
                        double prog=(t*0.8+i)%(Math.PI);
                        double r=Math.sin(prog)*0.7;
                        double yy=Math.abs(Math.cos(prog))*0.6;
                        base.getWorld().spawnParticle(Particle.DRIPPING_WATER,base.clone().add(r*Math.cos(a),yy,r*Math.sin(a)),1,0,0,0,0);
                    }
                }

                case "masal" -> { // Masal — sparkle
                    if (flopTick%2==0) for (int i=0;i<5;i++) {
                        double a=t*0.5+(Math.PI*2.0/5)*i;
                        double r=0.2+Math.abs(Math.sin(t+i))*0.5;
                        base.getWorld().spawnParticle(Particle.END_ROD,base.clone().add(r*Math.cos(a),Math.abs(Math.sin(t*0.5+i))*0.7,r*Math.sin(a)),1,0,0,0,0);
                    }
                    if (flopTick%7==0) base.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,base.clone().add(0,0.5,0),3,0.3,0.2,0.3,0.01);
                }

                // ─── EK YENİ EFEKTLER ───

                case "ejderha2" -> { // Çift dragon breath
                    for (int ring=0;ring<2;ring++) {
                        double rr=0.45+ring*0.25; int seg=6;
                        for (int i=0;i<seg;i++) {
                            double a=(ring%2==0?1:-1)*t*1.2+(Math.PI*2.0/seg)*i;
                            double yy=0.2+ring*0.25+Math.sin(t*0.6+i)*0.1;
                            base.getWorld().spawnParticle(Particle.DRAGON_BREATH,base.clone().add(rr*Math.cos(a),yy,rr*Math.sin(a)),1,0,0,0,0.005);
                        }
                    }
                    if (flopTick%5==0) base.getWorld().spawnParticle(Particle.WITCH,base.clone().add(0,0.8,0),2,0.1,0.1,0.1,0.01);
                }

                case "kar" -> { // Kar fırtınası tornado
                    for (int i=0;i<10;i++) {
                        double a=t*3.0+(Math.PI*2.0/10)*i;
                        double r=0.15+(i*0.06);
                        double yy=0.05+(i*0.07);
                        base.getWorld().spawnParticle(Particle.SNOWFLAKE,base.clone().add(r*Math.cos(a),yy,r*Math.sin(a)),1,0,0,0,0.005);
                    }
                    if (flopTick%4==0) base.getWorld().spawnParticle(Particle.SNOWFLAKE,base.clone().add(0,0.9,0),3,0.2,0.1,0.2,0.02);
                }

                case "volkan" -> { // Volkan fışkırması
                    for (int i=0;i<4;i++) {
                        double a=t*1.5+(Math.PI*2.0/4)*i;
                        base.getWorld().spawnParticle(Particle.LAVA,base.clone().add(0.55*Math.cos(a),0.3,0.55*Math.sin(a)),1,0.05,0.1,0.05,0);
                    }
                    if (flopTick%3==0) base.getWorld().spawnParticle(Particle.FLAME,base.clone().add(0,0.5,0),2,0.15,0.2,0.15,0.05);
                    if (flopTick%10==0) base.getWorld().spawnParticle(Particle.EXPLOSION,base.clone().add(0,0.2,0),1,0.1,0,0.1,0);
                }

                case "hız" -> { // İçe spiral hız
                    for (int i=0;i<8;i++) {
                        double a=-t*3.0+(Math.PI*2.0/8)*i;
                        double r=0.7-(((flopTick%20)/20.0)*0.5);
                        base.getWorld().spawnParticle(Particle.CRIT,base.clone().add(r*Math.cos(a),0.3+Math.sin(t+i)*0.1,r*Math.sin(a)),1,0,0,0,0.02);
                    }
                    if (flopTick%3==0) base.getWorld().spawnParticle(Particle.END_ROD,base.clone().add(0,0.1,0),1,0.1,0.05,0.1,0.05);
                }

                case "orak" -> { // Köy + witch döngü
                    for (int i=0;i<4;i++) {
                        double a=t*0.7+(Math.PI*2.0/4)*i;
                        base.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,base.clone().add(0.5*Math.cos(a),0.35,0.5*Math.sin(a)),1,0,0,0,0);
                    }
                    for (int i=0;i<4;i++) {
                        double a=-t*1.1+(Math.PI*2.0/4)*i;
                        base.getWorld().spawnParticle(Particle.WITCH,base.clone().add(0.5*Math.cos(a),0.55,0.5*Math.sin(a)),1,0,0,0,0);
                    }
                }

                case "kristal2" -> { // END_ROD + witch çift katman
                    for (int ring=0;ring<2;ring++) {
                        double rr=0.35+ring*0.25; int seg=6+ring*2;
                        for (int i=0;i<seg;i++) {
                            double a=t*(ring%2==0?0.8:1.1)+(Math.PI*2.0/seg)*i;
                            base.getWorld().spawnParticle(ring==0?Particle.END_ROD:Particle.WITCH,
                                base.clone().add(rr*Math.cos(a),0.25+ring*0.3,rr*Math.sin(a)),1,0,0,0,0);
                        }
                    }
                }

                case "girdap" -> { // Dışa açılan büyük portal spiral
                    for (int i=0;i<12;i++) {
                        double a=t*1.5+(Math.PI*2.0/12)*i;
                        double r=0.1+(((flopTick%25)/25.0)*0.8);
                        double yy=0.05+Math.sin(t*0.5+i)*0.3;
                        base.getWorld().spawnParticle(Particle.PORTAL,base.clone().add(r*Math.cos(a),yy,r*Math.sin(a)),1,0,0,0,0);
                    }
                    if (flopTick%6==0) base.getWorld().spawnParticle(Particle.END_ROD,base.clone().add(0,0.6,0),2,0.1,0.1,0.1,0.02);
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    //  ANİMASYON — ANAHTAR MENÜSÜ
    // ─────────────────────────────────────────────
    private void updateAnimatedMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            String title = ChatColor.stripColor(p.getOpenInventory().getTitle());
            if (!title.contains("Anahtar")) continue;
            Inventory inv = p.getOpenInventory().getTopInventory();
            for (int slot = 11; slot <= 15; slot++) {
                ItemStack item = inv.getItem(slot);
                if (item == null || !item.hasItemMeta()) continue;
                ItemMeta meta = item.getItemMeta();
                List<String> lore = meta.getLore();
                if (lore == null || lore.isEmpty()) continue;
                String keyId = ChatColor.stripColor(lore.get(0));
                meta.setDisplayName(getKeyAnimText(KEY_BASE_NAMES.getOrDefault(keyId, keyId), keyId, flopTick));
                item.setItemMeta(meta);
            }
        }
    }

    private String getKeyAnimText(String text, String keyId, int tick) {
        String[] clr = getCrateColors(keyId); StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ' ') { sb.append(' '); continue; }
            sb.append(clr[(i + tick) % clr.length]).append(text.charAt(i));
        }
        return color(sb.toString());
    }

    private String[] getCrateColors(String id) {
        return CRATE_COLORS.getOrDefault(id.toLowerCase(), new String[]{"&#ffffff","&#dddddd","&#aaaaaa","&#dddddd","&#ffffff","&#cccccc"});
    }

    // ─────────────────────────────────────────────
    //  ANAHTAR MENÜSÜ — SİMETRİK + SEVIYE 8
    // ─────────────────────────────────────────────
    private void openKeyMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 45, "Anahtar Menusu");
        ItemStack cam = createBtn(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, cam);
        inv.setItem(11, createKey(Material.WHITE_CANDLE,  "afk"));
        inv.setItem(12, createKey(Material.RED_CANDLE,    "casino"));
        inv.setItem(13, createKey(Material.ORANGE_CANDLE, "gear"));
        inv.setItem(14, createKey(Material.LIME_CANDLE,   "booster"));
        inv.setItem(15, createKey(Material.PURPLE_CANDLE, "all"));
        inv.setItem(22, createBtn(Material.EXPERIENCE_BOTTLE, "&#ffcc00&l✦ Seviye 8 Kasası ✦"));
        inv.setItem(31, makeCloseHead(p));
        p.openInventory(inv);
    }

    private ItemStack createKey(Material m, String id) {
        // m parametresi artık kullanılmıyor — candle kullanıyoruz
        Material candleMat = switch (id) {
            case "afk"     -> Material.WHITE_CANDLE;
            case "casino"  -> Material.RED_CANDLE;
            case "gear"    -> Material.ORANGE_CANDLE;
            case "booster" -> Material.LIME_CANDLE;
            default        -> Material.PURPLE_CANDLE;
        };
        ItemStack item = new ItemStack(candleMat); ItemMeta meta = item.getItemMeta();
        String keyName = KEY_BASE_NAMES.getOrDefault(id, id);
        meta.setDisplayName(getKeyAnimText(keyName, id, 0));
        meta.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, id);
        meta.setLore(Collections.singletonList(color("&8" + id))); // sadece id — kullanıcıya gizli
        item.setItemMeta(meta); return item;
    }

    private ItemStack buildPhysicalKey(String id) {
        Material mat = switch (id) {
            case "afk"     -> Material.WHITE_CANDLE;
            case "casino"  -> Material.RED_CANDLE;
            case "gear"    -> Material.ORANGE_CANDLE;
            case "booster" -> Material.LIME_CANDLE;
            default        -> Material.PURPLE_CANDLE; // all
        };
        String baseName = KEY_BASE_NAMES.getOrDefault(id, capitalize(id) + " Key");
        ItemStack item = new ItemStack(mat); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(getKeyAnimText(baseName, id, 0));
        meta.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, id);
        meta.setLore(new ArrayList<>()); // Lore yok
        item.setItemMeta(meta); return item;
    }

    // ─────────────────────────────────────────────
    //  KUMAR MENÜSÜ — AÇILIŞ (ses iyileştirildi)
    // ─────────────────────────────────────────────
    private ItemStack pickReward(String crateName, List<ItemStack> pool) {
        List<Float> chs = crateChances.getOrDefault(crateName, new ArrayList<>());
        float total = 0f; List<Float> weights = new ArrayList<>();
        for (int i = 0; i < pool.size(); i++) { float w = (i < chs.size()) ? chs.get(i) : 10f; weights.add(w); total += w; }
        float roll = new Random().nextFloat() * total, cumul = 0f;
        for (int i = 0; i < pool.size(); i++) { cumul += weights.get(i); if (roll <= cumul) return pool.get(i).clone(); }
        return pool.get(pool.size()-1).clone();
    }

    private void openSpinMenu(Player p, String crateName, List<ItemStack> pool, ItemStack reward) {
        String title = color(getBoldColorTitle(crateName, capitalize(crateName) + " Kasasi"));
        Inventory inv = Bukkit.createInventory(null, SPIN_SLOTS, title);
        fillSpinInventory(inv, pool, crateName);
        int dur = crateSpinDuration.getOrDefault(crateName, DEFAULT_SPIN_TICKS);
        activeSessions.put(p.getUniqueId(), new CrateSession(inv, reward, crateName, dur));
        p.openInventory(inv);
        // Kumar menüsü açılış sesi — daha çarpıcı
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 0.8f);
    }

    private String getBoldColorTitle(String crateId, String text) {
        String[] clr = getCrateColors(crateId); StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') { sb.append(' '); continue; }
            sb.append(clr[i % clr.length]).append("&l").append(c);
        }
        return sb.toString();
    }

    private void fillSpinInventory(Inventory inv, List<ItemStack> pool, String crateName) {
        Random rand = new Random(); String[] clr = getCrateColors(crateName);
        for (int i = 0; i < SPIN_SLOTS; i++) {
            boolean edge = (i < 9) || (i >= 36) || (i % 9 == 0) || (i % 9 == 8);
            if (edge) inv.setItem(i, makeBorderGlass(clr[i % clr.length]));
            else inv.setItem(i, pool.isEmpty() ? makeBorderGlass(clr[0]) : pool.get(rand.nextInt(pool.size())).clone());
        }
    }

    private ItemStack makeBorderGlass(String hexColor) {
        ItemStack g = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = g.getItemMeta(); m.setDisplayName(color(hexColor + " ")); g.setItemMeta(m); return g;
    }

    // ─────────────────────────────────────────────
    //  KUMAR TICK
    // ─────────────────────────────────────────────
    private void tickAllSessions() {
        for (Map.Entry<UUID, CrateSession> entry : new HashMap<>(activeSessions).entrySet()) {
            UUID uid = entry.getKey(); CrateSession s = entry.getValue();
            if (s.done) continue;
            Player p = Bukkit.getPlayer(uid);
            if (p == null || !p.isOnline()) { activeSessions.remove(uid); continue; }
            s.tick++;
            List<ItemStack> pool = crateItems.getOrDefault(s.crateName, new ArrayList<>());
            if (pool.isEmpty()) continue;
            int shiftEvery = getShiftInterval(s.tick, s.spinDuration);
            if (s.tick % shiftEvery == 0) {
                shiftInventory(s.inv, pool, s.crateName);
                if (s.tick % (shiftEvery * 2) == 0) {
                    float pitch = 0.8f + (s.tick / (float) s.spinDuration) * 0.9f;
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, Math.min(pitch, 2.0f));
                }
            }
            if (s.tick >= s.spinDuration) { s.done = true; finalizeSpin(p, s); }
        }
    }

    private int getShiftInterval(int tick, int total) {
        float pr = (float) tick / total;
        if (pr < 0.25f) return 1; if (pr < 0.50f) return 2;
        if (pr < 0.68f) return 3; if (pr < 0.84f) return 5; return 9;
    }

    private void shiftInventory(Inventory inv, List<ItemStack> pool, String crateName) {
        int[][] rows = {{10,11,12,13,14,15,16},{19,20,21,22,23,24,25},{28,29,30,31,32,33,34}};
        Random rand = new Random();
        for (int[] row : rows) {
            for (int i = 0; i < row.length - 1; i++) inv.setItem(row[i], inv.getItem(row[i+1]));
            inv.setItem(row[row.length-1], pool.get(rand.nextInt(pool.size())).clone());
        }
        String[] clr = getCrateColors(crateName);
        ItemStack hl = makeBorderGlass(clr[flopTick % clr.length]);
        inv.setItem(4, hl.clone()); inv.setItem(40, hl.clone());
    }

    private void finalizeSpin(Player p, CrateSession s) {
        s.inv.setItem(RESULT_SLOT, s.reward.clone());
        ItemStack gold = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta gm = gold.getItemMeta(); gm.setDisplayName(" "); gold.setItemMeta(gm);
        for (int sl : new int[]{13,21,23,31}) s.inv.setItem(sl, gold.clone());
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1.2f);
        String rName = getName(s.reward);
        String[] clr = getCrateColors(s.crateName);
        p.sendTitle(color(clr[0] + "&l✦ " + capitalize(s.crateName) + " Kasasından ✦"),
            color("&#ffffff&l" + rName + " &r&#ffcc00kazandın!"), 5, 100, 20);
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) return;
                p.getInventory().addItem(s.reward.clone());
                p.sendMessage(color(clr[0] + "&l★ &r&#ffffff" + rName + " &7envantere eklendi! &8[" + capitalize(s.crateName) + " Kasasi]"));
                p.closeInventory(); activeSessions.remove(p.getUniqueId());
            }
        }.runTaskLater(this, 42L);
    }

    // ─────────────────────────────────────────────
    //  KASA ÖNİZLEME — Şans → Çıkma Şansı
    // ─────────────────────────────────────────────
    private void openCratePreview(Player p, String crateName) {
        // Fotoğraftaki gibi: başlık sabit "&7Kasa Crate"
        String title = color("&7Kasa Crate");
        Inventory inv = Bukkit.createInventory(null, 45, title);
        String[] clr = getCrateColors(crateName);
        for (int i = 0; i < 45; i++)
            if (i < 9 || i > 35 || i % 9 == 0 || i % 9 == 8) inv.setItem(i, makeBorderGlass(clr[i % clr.length]));

        List<ItemStack> items   = crateItems.getOrDefault(crateName, new ArrayList<>());
        List<Float>     chances = crateChances.getOrDefault(crateName, new ArrayList<>());
        int slot = 10;
        for (int idx = 0; idx < items.size(); idx++) {
            if (slot % 9 == 8) slot += 2;
            if (slot > 34) break;
            ItemStack display = items.get(idx).clone();
            ItemMeta  dm      = display.getItemMeta();
            if (dm == null) { display.setItemMeta(Bukkit.getItemFactory().getItemMeta(display.getType())); dm = display.getItemMeta(); }
            float ch = (idx < chances.size()) ? chances.get(idx) : 10.0f;
            // Mevcut lore varsa koru, yoksa boş liste
            List<String> l = dm.hasLore() ? new ArrayList<>(dm.getLore()) : new ArrayList<>();
            l.add(color("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            l.add(color("&7• Çıkma Şansı: &f%" + ch));
            l.add(color("&7• Nadirlik: " + getRarityName(ch)));
            dm.setLore(l); display.setItemMeta(dm);
            inv.setItem(slot++, display);
        }
        inv.setItem(40, makeCloseHead(p));
        p.openInventory(inv);
    }

    private ItemStack makeCloseHead(Player p) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(p);
        sm.setDisplayName(color("&#ff3333&lKapat"));
        sm.setLore(Collections.singletonList(color("&7Onizlemeyi kapatmak icin tikla.")));
        head.setItemMeta(sm); return head;
    }

    // ─────────────────────────────────────────────
    //  BİLGİ MENÜSÜ
    // ─────────────────────────────────────────────
    private void openInfoMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&#00ccff&l✦ LoginX Bilgi Merkezi ✦"));
        ItemStack fill = makeBorderGlass("&#050520");
        for (int i = 0; i < 54; i++) inv.setItem(i, fill);

        ItemStack info = new ItemStack(Material.NETHER_STAR); ItemMeta im = info.getItemMeta();
        im.setDisplayName(color("&#ffcc00&l✦ &r&#fff44f&l Fear Craft LoginX &r&#ffcc00&l✦"));
        List<String> il = new ArrayList<>();
        il.add(color("  &#334466▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        il.add(color(""));
        il.add(color("    &#ffffff&l✦ Kasa & Anahtar Sistemi ✦"));
        il.add(color("    &#aaaaaa&oFear Craft için geliştirilmiştir"));
        il.add(color(""));
        il.add(color("  &#334466▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        il.add(color("  &#00ff88◆ &fKasaları &b&lsağ tıkla &file aç"));
        il.add(color("  &#00ff88◆ &fKasaları &b&lsol tıkla &fönizle"));
        il.add(color("  &#00ff88◆ &fHer kasanın kendine özel anahtarı var"));
        il.add(color("  &#00ff88◆ &f&lKey All &rherhangi bir kasayı açar"));
        il.add(color("  &#00ff88◆ &fŞans ayarla: &e/kasasansayarla"));
        il.add(color("  &#00ff88◆ &fAnahtar ver: &e/kasakeyver"));
        il.add(color("  &#00ff88◆ &fEfekt seç: &e/kasafarkliefektler"));
        il.add(color(""));
        il.add(color("  &#334466▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        il.add(color("    &#ffcc00● &7Toplam Kasa: &f" + crates.size()));
        il.add(color("  &#334466▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        im.setLore(il); info.setItemMeta(im); inv.setItem(4, info);

        // SİMETRİK düzen: 7 buton — 29,30,31,32,33 + 20,24
        Object[][] sections = {
            {"&#ff6600","&#ff8833","◈ KASALAR","Sunucudaki tüm kasalar\nhakkında detaylı bilgi al.\nKasa sayısı, itemler ve efektler.",Material.CHEST,"KASALAR"},
            {"&#00ccff","&#44ddff","◈ ANAHTARLAR","Hangi anahtar hangi kasayı\naçar öğren ve envantere al.\nKey All herkesi açar!",Material.TRIPWIRE_HOOK,"ANAHTARLAR"},
            {"&#aa44ff","&#cc77ff","◈ EFEKTLER","Kasalardaki partikül efektlerini\ngörüntüle ve değiştir.\n7 farklı efekt seçeneği.",Material.FIREWORK_ROCKET,"EFX"},
            {"&#ffdd00","&#ffee44","◈ ŞANS","Her item için şans oranlarını\ngörüntüle ve düzenle.\nNadirlik sistemi açıklaması.",Material.GOLDEN_APPLE,"SANS"},
            {"&#ff2244","&#ff5566","◈ KUMAR","Kumar menüsü nasıl çalışır?\nSpin süresi ve hız ayarları.\nOyun mekaniği anlatımı.",Material.RED_DYE,"KUMAR"},
            {"&#00ff88","&#44ffaa","◈ KOMUTLAR","Tüm admin komutlarını\nlistele ve açıklamalarını gör.\nEksiksiz komut rehberi.",Material.COMMAND_BLOCK,"CMD"},
            {"&#aaaaaa","&#cccccc","◈ DESTEK","Yardım için admin\nile iletişime geç.\nHata bildirme ve öneriler.",Material.BOOK,"INFO"}
        };
        // Simetrik: 20,22,24 üst + 29,31,33 alt + 38 ortada alt
        int[] bSlots = {20, 22, 24, 29, 31, 33, 38};

        for (int i = 0; i < sections.length; i++) {
            String clr2 = (String) sections[i][0]; String clr3 = (String) sections[i][1];
            String name = (String) sections[i][2];
            String[] desc = ((String) sections[i][3]).split("\n"); Material mat = (Material) sections[i][4];
            ItemStack btn = new ItemStack(mat); ItemMeta bm = btn.getItemMeta();
            bm.setDisplayName(color(clr2 + "&l" + name));
            List<String> bl = new ArrayList<>();
            bl.add(color("  " + clr2 + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            bl.add(color(""));
            for (String d : desc) bl.add(color("    &#dddddd" + d));
            bl.add(color(""));
            bl.add(color("    " + clr3 + "» &fBilgi için tıkla!"));
            bl.add(color(""));
            bl.add(color("  " + clr2 + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            bl.add(color("&8INFO:" + sections[i][5]));
            bm.setLore(bl); btn.setItemMeta(bm); inv.setItem(bSlots[i], btn);
        }
        inv.setItem(49, makeCloseHead(p));
        p.openInventory(inv);
    }

    // ─────────────────────────────────────────────
    //  İADE SİSTEMİ
    // ─────────────────────────────────────────────
    private void openIadeMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&7Silvera - &fİade"));
        for (int i = 0; i < deathRecords.size() && i < 45; i++) {
            DeathRecord dr = deathRecords.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD); SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(dr.playerUUID));
            sm.setDisplayName(color("&#ffcc00&l" + dr.playerName + " &7(#" + dr.id + ")"));
            List<String> lore = new ArrayList<>();
            lore.add(color("&8&m------------------------")); lore.add(color("&7Olum Tarihi: &f" + dr.date));
            lore.add(color("&7Katil: &c" + dr.killerName));
            lore.add(color("&7Konum: &a" + dr.loc.getBlockX() + ", " + dr.loc.getBlockY() + ", " + dr.loc.getBlockZ()));
            lore.add(color("&8&m------------------------")); lore.add(color("&#00ffcc» Sag Tikla: &fDetayli Incele"));
            lore.add(color("&#00ffcc» Sol Tikla: &fDirekt Iade Et"));
            sm.setLore(lore); head.setItemMeta(sm); inv.setItem(i, head);
        }
        inv.setItem(45, createBtn(Material.BOOK, "&#00ccff&l✦ İade Geçmişi", "IADE_LOG"));
        inv.setItem(48, createBtn(Material.LAVA_BUCKET,   "&#ff3300Tum Gecmisi Sil"));
        inv.setItem(49, createBtn(Material.BARRIER,       "&#ff3333&lKapat"));
        inv.setItem(50, createBtn(Material.EMERALD_BLOCK, "&#00ff00Sayfayi Yenile"));
        p.openInventory(inv);
    }

    private void openIadeDetail(Player p, DeathRecord dr) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&#ff9900Olum Detayi: &f" + dr.playerName));
        for (int i = 0; i < dr.items.length && i < 41; i++) if (dr.items[i] != null) inv.setItem(i, dr.items[i]);
        ItemStack cam = createBtn(Material.WHITE_STAINED_GLASS_PANE, " ");
        for (int i = 41; i < 45; i++) inv.setItem(i, cam);
        inv.setItem(48, createBtn(Material.RED_DYE,  "&#ff0000Bu Kaydi Sil",     dr.id));
        inv.setItem(49, createBtn(Material.BARRIER,  "&#ff3333Geri Don",         dr.id));
        inv.setItem(50, createBtn(Material.LIME_DYE, "&#00ff00Esyalari Iade Et", dr.id));
        p.openInventory(inv);
    }

    private void openIadeLogMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&#00ccff&l✦ İade Geçmişi"));
        ItemStack fill = makeBorderGlass("&#050520");
        for (int i = 0; i < 54; i++) inv.setItem(i, fill);

        // Başlık
        ItemStack header = new ItemStack(Material.BOOK); ItemMeta hm = header.getItemMeta();
        hm.setDisplayName(color("&#00ccff&l✦ İade Geçmişi"));
        List<String> hl = new ArrayList<>();
        hl.add(color("  &#334466▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        hl.add(color("  &7Toplam İade: &f" + iadeLogs.size() + " adet"));
        hl.add(color("  &#334466▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        hm.setLore(hl); header.setItemMeta(hm); inv.setItem(4, header);

        // Logları listele — slotlar: 10-16, 19-25, 28-34, 37-43 (28 slot)
        int[] logSlots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};
        for (int i = 0; i < iadeLogs.size() && i < logSlots.length; i++) {
            IadeLog log = iadeLogs.get(iadeLogs.size() - 1 - i); // en yeni üstte
            ItemStack it = new ItemStack(Material.PAPER); ItemMeta im = it.getItemMeta();
            im.setDisplayName(color("&#ffcc00&l" + log.adminName + " &8» &f" + log.playerName));
            List<String> l = new ArrayList<>();
            l.add(color("  &#334466▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            l.add(color("  &7İade Eden: &e" + log.adminName));
            l.add(color("  &7İade Alan:  &f" + log.playerName));
            l.add(color("  &7Kayıt ID:   &8#" + log.recordId));
            l.add(color("  &7Tarih:      &b" + log.date));
            l.add(color("  &#334466▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            im.setLore(l); it.setItemMeta(im); inv.setItem(logSlots[i], it);
        }

        if (iadeLogs.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER); ItemMeta em = empty.getItemMeta();
            em.setDisplayName(color("&#ff4444Henüz İade Yapılmamış"));
            em.setLore(Arrays.asList(color("&7Bu menüde kim kime iade verdi gözükür.")));
            empty.setItemMeta(em); inv.setItem(22, empty);
        }

        // Geri — slot 49
        ItemStack back = new ItemStack(Material.ARROW); ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(color("&#ffcc00← &fGeri Dön"));
        back.setItemMeta(bm); inv.setItem(49, back);
        p.openInventory(inv);
    }

    // ─────────────────────────────────────────────
    //  ENVANTER CLICK — EFEKTİ DÜZELT
    // ─────────────────────────────────────────────
    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        if (activeSessions.containsKey(p.getUniqueId())) { e.setCancelled(true); return; }

        String title = ChatColor.stripColor(e.getView().getTitle());
        // Stash'e item koymaya izin ver — cancel etme
        if (title.contains("Stash")) return;
        boolean managed = title.contains("Iade") || title.contains("Anahtar") || title.contains("Kasa")
            || title.contains("Crate") || title.contains("Kasa Crate") || title.contains("Olum Detayi")
            || title.contains("Kumar") || title.contains("Efekt Secimi") || title.contains("Bilgi Merkezi")
            || title.contains("Hile Kontrol") || title.contains("Duel Arenaları") || title.contains("Duel Ayarları")
            || title.contains("Duel Daveti")
            || title.contains("İzin Yönetimi") || title.contains("İade Geçmişi");
        if (!managed) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;

        ItemStack clicked = e.getCurrentItem();
        Material m = clicked.getType();
        String itemName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        List<String> lore = clicked.getItemMeta().getLore();

        // Kapat
        if ((m == Material.PLAYER_HEAD || m == Material.BARRIER) &&
                (itemName.contains("Kapat") || itemName.contains("Geri"))) {
            if (title.contains("Olum Detayi")) openIadeMenu(p);
            else p.closeInventory();
            return;
        }

        // Kumar menüsü ayar
        if (title.contains("Kumar")) {
            if (lore != null) for (String line : lore) {
                String raw = ChatColor.stripColor(line);
                if (raw.startsWith("TICK:")) {
                    try {
                        int ticks = Integer.parseInt(raw.replace("TICK:","").trim());
                        for (String crate : crates.keySet()) crateSpinDuration.put(crate, ticks);
                        p.sendMessage(color("&#00ff88» &fSpin suresi guncellendi: &b" + raw.replace("TICK:","") + " tick"));
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                        p.closeInventory();
                    } catch (Exception ignored) {}
                    return;
                }
            }
            return;
        }

        // Efekt seçim — "Efekt Secimi" ile eşleşiyor
        if (title.contains("Efekt Secimi") || title.contains("Efekt Seçimi")) {
            if (lore != null) for (String line : lore) {
                String raw = ChatColor.stripColor(line);
                if (raw.startsWith("EFEKTSAYFA:")) {
                    String[] ps = raw.split("\\|");
                    int nextPage = Integer.parseInt(ps[0].replace("EFEKTSAYFA:","").trim());
                    String kasaId = ps.length > 1 ? ps[1].replace("KASA:","").trim() : "";
                    openEffectSelectMenuPage(p, kasaId, nextPage);
                    return;
                }
                if (raw.startsWith("EFEKT:")) {
                    String[] parts = raw.split("\\|");
                    String efektId = parts[0].replace("EFEKT:","").trim();
                    String kasaId  = parts.length > 1 ? parts[1].replace("KASA:","").trim() : "";
                    if (kasaId.isEmpty()) {
                        for (String cr : crates.keySet()) crateEffectType.put(cr, efektId);
                        p.sendMessage(color("&#aa44ff» &fTum kasalara efekt uygulandi: &e" + efektId));
                    } else {
                        crateEffectType.put(kasaId, efektId);
                        p.sendMessage(color("&#aa44ff» &f" + capitalize(kasaId) + " kasasina efekt uygulandi: &e" + efektId));
                    }
                    saveData();
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                    p.closeInventory(); return;
                }
            }
            return;
        }

        // Bilgi menüsü
        if (title.contains("Bilgi Merkezi")) {
            if (lore != null) for (String line : lore) {
                String raw = ChatColor.stripColor(line);
                if (raw.startsWith("INFO:")) { sendInfoSection(p, raw.replace("INFO:","").trim()); return; }
            }
            return;
        }

        // Anahtar menüsü
        if (title.contains("Anahtar") && m != Material.BLACK_STAINED_GLASS_PANE && m != Material.EXPERIENCE_BOTTLE) {
            if (m == Material.PLAYER_HEAD) { p.closeInventory(); return; }
            // Anahtarı envantere ver — lore[0] = key id
            ItemStack give = buildPhysicalKey(ChatColor.stripColor(lore != null && !lore.isEmpty() ? lore.get(0) : "all"));
            p.getInventory().addItem(give);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            return;
        }

        // İade log menüsü
        if (title.contains("İade Geçmişi")) {
            if (m == Material.ARROW) openIadeMenu(p);
            return;
        }

        // İade
        if (title.contains("Silvera") && title.contains("ade")) {
            if (m == Material.BOOK) {
                String raw = lore != null ? lore.stream().map(ChatColor::stripColor).filter(l2 -> l2.trim().startsWith("IADE_LOG")).findFirst().orElse("") : "";
                if (raw.contains("IADE_LOG")) { openIadeLogMenu(p); return; }
            }
            if (m == Material.PLAYER_HEAD) {
                try {
                    String id = itemName.split("#")[1].replace(")","").trim();
                    DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
                    if (dr != null) {
                        if (e.isLeftClick()) {
                            Player t = Bukkit.getPlayer(dr.playerUUID);
                            if (t != null && t.isOnline()) {
                                // ── Shulker ile iade: içindeki item'leri aynı slota koy ──
                                // Shulker box oluştur
                                ItemStack iadeShulker = new ItemStack(Material.CYAN_SHULKER_BOX);
                                BlockStateMeta bsm2 = (BlockStateMeta) iadeShulker.getItemMeta();
                                ShulkerBox box2 = (ShulkerBox) bsm2.getBlockState();
                                // Her item'ı aynı slot'a koy (ölüm sırasındaki slot)
                                for (int si = 0; si < dr.items.length && si < box2.getInventory().getSize(); si++) {
                                    ItemStack itm = dr.items[si];
                                    if (itm == null || itm.getType() == Material.AIR) continue;
                                    // İçinde zaten shulker varsa içeriğini çıkar (nested shulker fix)
                                    if (itm.getType().name().contains("SHULKER_BOX") && itm.getItemMeta() instanceof BlockStateMeta innerBsm) {
                                        ShulkerBox innerBox = (ShulkerBox) innerBsm.getBlockState();
                                        for (ItemStack innerIt : innerBox.getInventory().getContents()) {
                                            if (innerIt != null && innerIt.getType() != Material.AIR)
                                                box2.getInventory().addItem(innerIt.clone());
                                        }
                                    } else {
                                        box2.getInventory().setItem(si, itm.clone());
                                    }
                                }
                                // RGB flop mavi-beyaz isim — "Silvera İade" kalın
                                String iadeTitle = buildRgbFlop("Silvera İade",
                                    new String[]{"&#00aaff","&#44ccff","&#88eeff","&#ffffff","&#88eeff","&#44ccff","&#00aaff"}, true);
                                bsm2.setDisplayName(iadeTitle + color(" &r&#aaaaaa&o— " + dr.playerName));
                                bsm2.setLore(new ArrayList<>()); // lore yok
                                iadeShulker.setItemMeta(bsm2);
                                // Envanteri temizle — set shulker to ilk boş slota
                                Map<Integer, ItemStack> leftover = t.getInventory().addItem(iadeShulker);
                                if (!leftover.isEmpty()) t.getWorld().dropItem(t.getLocation(), iadeShulker);
                                p.sendMessage(color("&#00ff88» &fİade &e" + dr.playerName + " &fadına shulker olarak verildi."));
                                iadeLogs.add(new IadeLog(p.getName(), dr.playerName, dr.id));
                            } else {
                                p.sendMessage(color("&#ff4444» &f" + dr.playerName + " &cçevrimiçi değil."));
                            }
                        }
                        else if (e.isRightClick()) openIadeDetail(p, dr);
                    }
                } catch (Exception ignored) {}
            } else if (m == Material.BARRIER) p.closeInventory();
            else if (m == Material.EMERALD_BLOCK) openIadeMenu(p);
            else if (m == Material.LAVA_BUCKET) { deathRecords.clear(); openIadeMenu(p); p.sendMessage(color("&cGeçmiş silindi.")); }
            return;
        }

        // İzin menüsü
        if (title.contains("İzin Yönetimi")) {
            ItemStack ci4 = e.getCurrentItem();
            if (ci4 == null || !ci4.hasItemMeta() || ci4.getItemMeta().getLore() == null) return;
            String uidStr = ci4.getItemMeta().getLore().stream()
                .map(ChatColor::stripColor).filter(l -> l.trim().startsWith("IZIN_TOGGLE:"))
                .map(l -> l.trim().substring(12)).findFirst().orElse(null);
            if (uidStr == null) return;
            try {
                UUID toggleUid = UUID.fromString(uidStr);
                if (izinliOyuncular.contains(toggleUid)) {
                    izinliOyuncular.remove(toggleUid);
                    getConfig().set("izinli." + uidStr, null);
                } else {
                    izinliOyuncular.add(toggleUid);
                    getConfig().set("izinli." + uidStr, true);
                }
                saveConfig();
                p.closeInventory();
                openIzinMenu(p);
            } catch (Exception ignored) {}
            return;
        }

        // Kont menüsü
        if (title.contains("Hile Kontrol")) {
            if (!e.getCurrentItem().hasItemMeta()) return;
            String iName2 = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()).trim();
            if (iName2.startsWith("/itiraf")) {
                p.closeInventory();
                p.sendMessage(color("&#ff6666» &f/itiraf &7komutunu kullanarak itirafını yaz: &e/itiraf <metin>"));
            } else if (iName2.startsWith("/anydesk")) {
                p.closeInventory();
                p.sendMessage(color("&#00ccff» &f/anydesk &7komutunu kullanarak kodunu gönder: &e/anydesk <kod>"));
            } else if (iName2.startsWith("/discord2")) {
                p.closeInventory();
                p.sendMessage(color("&#7289da» &f/discord2 &7komutunu kullanarak discordunu gönder: &e/discord2 <nick>"));
            }
            return;
        }

        // Duel arena listesi
        if (title.contains("Duel Arenaları")) {
            ItemStack ci2 = e.getCurrentItem();
            if (ci2 == null || !ci2.hasItemMeta()) return;
            List<String> lore2 = ci2.getItemMeta().getLore();
            if (lore2 == null) return;
            String duelId = lore2.stream().map(ChatColor::stripColor).filter(l -> l.trim().startsWith("DUEL_ID:"))
                .map(l -> l.trim().substring(8)).findFirst().orElse(null);
            if (duelId == null) return;
            if (duelPlayers.containsKey(duelId)) { p.sendMessage(color("&cBu arena dolu!")); return; }
            if (duelSpawn1.get(duelId) == null || duelSpawn2.get(duelId) == null) { p.sendMessage(color("&cBu arena henüz hazır değil.")); return; }
            p.closeInventory();
            p.sendMessage(color("&#ffcc00&l[DUEL] &fArena &e" + duelId + " &fiçin &b/1v1 <oyuncu> &fkomutu ile davet gönder."));
            return;
        }

        // Duel ayarlar menüsü (/duelayarlar, /duelayarlaradmin) ve /duel <oyuncu> ön-ayar menüsü
        if (title.contains("Duel Ayarları") || title.contains("Duel Daveti")) {
            boolean isInviteMenu = title.contains("Duel Daveti");
            ItemStack ci3 = e.getCurrentItem();
            if (ci3 == null || !ci3.hasItemMeta()) return;
            List<String> lore3 = ci3.getItemMeta().getLore();
            if (lore3 == null) return;
            List<String> stripped3 = lore3.stream().map(ChatColor::stripColor).map(String::trim).toList();

            // Onayla / Gönder butonu (sadece davet menüsünde)
            if (isInviteMenu && stripped3.stream().anyMatch(l -> l.equals("DUEL_INVITE_CONFIRM"))) {
                UUID targetUid = duelPendingTarget.remove(p.getUniqueId());
                p.closeInventory();
                if (targetUid == null) { p.sendMessage(color("&#ff4444&l[DUEL] &cDavet süresi doldu, tekrar dene.")); return; }
                Player target = Bukkit.getPlayer(targetUid);
                sendDuelInvite(p, target, "hedef oyuncu");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.3f);
                return;
            }

            String toggle = stripped3.stream().filter(l -> l.startsWith("DUEL_TOGGLE:"))
                .map(l -> l.substring(12)).findFirst().orElse(null);
            if (toggle == null) return;

            HashMap<UUID, Boolean> map = switch (toggle) {
                case "web"    -> duelWebEnabled;
                case "ely"    -> duelElyEnabled;
                case "ladder" -> duelLadderEnabled;
                case "oak"    -> duelOakEnabled;
                case "stick"  -> duelStickEnabled;
                case "chest"  -> duelChestEnabled;
                default -> null;
            };
            if (map == null) return;
            boolean cur = map.getOrDefault(p.getUniqueId(), false);
            map.put(p.getUniqueId(), !cur);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, cur ? 0.8f : 1.3f);

            p.closeInventory();
            if (isInviteMenu) {
                UUID targetUid = duelPendingTarget.get(p.getUniqueId());
                Player target = targetUid != null ? Bukkit.getPlayer(targetUid) : null;
                if (target == null) { p.sendMessage(color("&#ff4444&l[DUEL] &cHedef oyuncu çevrimdışı oldu.")); return; }
                openDuelInviteMenu(p, target);
            } else {
                openDuelSettingsMenu(p, p.hasPermission("loginx.admin"));
            }
            return;
        }

        // Ölüm detayı
        if (title.contains("Olum Detayi") && (m == Material.RED_DYE || m == Material.LIME_DYE)) {
            List<String> l2 = clicked.getItemMeta().getLore();
            if (l2 == null || l2.isEmpty()) return;
            String id = ChatColor.stripColor(l2.get(0));
            DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
            if (dr != null && m == Material.LIME_DYE) {
                Player t = Bukkit.getPlayer(dr.playerUUID);
                if (t != null && t.isOnline()) {
                    ItemStack iadeShulker2 = new ItemStack(Material.CYAN_SHULKER_BOX);
                    BlockStateMeta bsm3 = (BlockStateMeta) iadeShulker2.getItemMeta();
                    ShulkerBox box3 = (ShulkerBox) bsm3.getBlockState();
                    for (int si = 0; si < dr.items.length && si < box3.getInventory().getSize(); si++) {
                        ItemStack itm = dr.items[si];
                        if (itm == null || itm.getType() == Material.AIR) continue;
                        if (itm.getType().name().contains("SHULKER_BOX") && itm.getItemMeta() instanceof BlockStateMeta ib) {
                            ShulkerBox ib2 = (ShulkerBox) ib.getBlockState();
                            for (ItemStack ii : ib2.getInventory().getContents())
                                if (ii != null && ii.getType() != Material.AIR) box3.getInventory().addItem(ii.clone());
                        } else {
                            box3.getInventory().setItem(si, itm.clone());
                        }
                    }
                    String iadeTitle2 = buildRgbFlop("Silvera İade",
                        new String[]{"&#00aaff","&#44ccff","&#88eeff","&#ffffff","&#88eeff","&#44ccff","&#00aaff"}, true);
                    bsm3.setDisplayName(iadeTitle2 + color(" &r&#aaaaaa&o— " + dr.playerName));
                    bsm3.setLore(new ArrayList<>());
                    iadeShulker2.setItemMeta(bsm3);
                    Map<Integer, ItemStack> lo2 = t.getInventory().addItem(iadeShulker2);
                    if (!lo2.isEmpty()) t.getWorld().dropItem(t.getLocation(), iadeShulker2);
                    p.sendMessage(color("&#00ff88» &fİade shulker olarak &e" + dr.playerName + " &fadına verildi."));
                    iadeLogs.add(new IadeLog(p.getName(), dr.playerName, dr.id));
                } else {
                    p.sendMessage(color("&#ff4444» &f" + dr.playerName + " &cçevrimiçi değil."));
                }
            }
            if (dr != null && m == Material.RED_DYE) { deathRecords.remove(dr); openIadeMenu(p); }
        }

        // Efekt sayfalama
        if (title.contains("Efekt Secimi") || title.contains("Efekt Seçimi")) {
            if (lore != null) for (String line : lore) {
                String raw = ChatColor.stripColor(line);
                if (raw.startsWith("EFEKTSAYFA:")) {
                    String[] ps = raw.split("\\|");
                    int nextPage = Integer.parseInt(ps[0].replace("EFEKTSAYFA:","").trim());
                    String kasaId = ps.length > 1 ? ps[1].replace("KASA:","").trim() : "";
                    openEffectSelectMenuPage(p, kasaId, nextPage);
                    return;
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    //  INV CLOSE
    // ─────────────────────────────────────────────
    @EventHandler
    public void onInvClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;

        // Stash sistemi kaldırıldı — bu blok artık işlem yapmaz

        CrateSession s = activeSessions.get(p.getUniqueId());
        if (s == null) return;
        if (s.done) return;
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) return;
                CrateSession cs = activeSessions.get(p.getUniqueId());
                if (cs == null || cs.done) return;
                p.openInventory(cs.inv);
                p.sendActionBar(color("&#ff4444⚠ &fKasa acilirken cikis yapamazsin!"));
            }
        }.runTaskLater(this, 1L);
    }

    // ─────────────────────────────────────────────
    //  BİLGİ BÖLÜMÜ MESAJİ
    // ─────────────────────────────────────────────
    private void sendInfoSection(Player p, String section) {
        switch (section) {
            case "KASALAR" -> {
                p.sendMessage(color("&#ffcc00&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("&#ffcc00&l  ✦ Kasalar &8(" + crates.size() + " adet)"));
                p.sendMessage(color("&#ffcc00&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                for (String k : crates.keySet()) p.sendMessage(color("  &#00ff88◆ &f" + capitalize(k) + " &8│ &7" + crateItems.getOrDefault(k, new ArrayList<>()).size() + " eşya │ Efekt: &e" + crateEffectType.getOrDefault(k, "varsayilan")));
                p.sendMessage(color("&#ffcc00&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            }
            case "ANAHTARLAR" -> {
                p.sendMessage(color("&#00ccff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("&#00ccff&l  ✦ Anahtarlar"));
                p.sendMessage(color("&#00ccff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                for (Map.Entry<String, String> en : KEY_BASE_NAMES.entrySet()) p.sendMessage(color("  &#00ccff◆ &f" + en.getValue() + " &8→ &e" + capitalize(en.getKey()) + " Kasası"));
                p.sendMessage(color("&#00ccff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            }
            case "EFX" -> {
                p.sendMessage(color("&#aa44ff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("&#aa44ff&l  ✦ Efektler"));
                p.sendMessage(color("&#aa44ff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("  &7varsayilan, fireworks, temiz, yildiz, kristal, alev, buz"));
                p.sendMessage(color("  &8» &e/kasafarkliefektler [kasa_ismi]"));
                p.sendMessage(color("&#aa44ff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            }
            case "SANS" -> {
                p.sendMessage(color("&#ffdd00&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("&#ffdd00&l  ✦ Çıkma Şansı Sistemi"));
                p.sendMessage(color("&#ffdd00&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("  &8» &e/kasasansayarla &f<kasa> &b<#sira> &a<sans>"));
                p.sendMessage(color("  &#ff2244◆ &f%0-1   = Efsanevi  | &#aa00ff◆ &f%1-3   = Epik"));
                p.sendMessage(color("  &#4488ff◆ &f%3-7   = Nadir     | &#00cc44◆ &f%7-15  = Yayginsiz"));
                p.sendMessage(color("  &#aaaaaa◆ &f%15+   = Yaygın"));
                p.sendMessage(color("&#ffdd00&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            }
            case "KUMAR" -> {
                p.sendMessage(color("&#ff2244&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("&#ff2244&l  ✦ Kumar Sistemi"));
                p.sendMessage(color("&#ff2244&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("  &7Kasa açıldığında spin animasyonu başlar."));
                p.sendMessage(color("  &7Hızdan yavaşa gider, ortadaki eşyayı kazanırsın."));
                p.sendMessage(color("  &8» &e/kasakumarmenu ayarla &7ile süreyi değiştir."));
                p.sendMessage(color("&#ff2244&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            }
            case "CMD" -> {
                p.sendMessage(color("&#00ff88&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("&#00ff88&l  ✦ Admin Komutları"));
                p.sendMessage(color("&#00ff88&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                for (String c : new String[]{"/kasaayarla <isim>","/kasasil <isim>","/kasaicsil <kasa> #<sira>","/icineitemkoy <kasa>","/kasaidlist <kasa>","/kasasansayarla <kasa> #<sira> <sans>","/kasakumarmenu ayarla","/kasakeyver <oyuncu|all> <kasa>","/kasafarkliefektler [kasa]","/kasabilgi","/1v1wand","/korumasurem","/korumakapat"})
                    p.sendMessage(color("  &#00ff88◆ &7" + c));
                p.sendMessage(color("&#00ff88&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            }
            case "INFO" -> {
                p.sendMessage(color("&#aaaaaa&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("&#aaaaaa&l  ✦ Destek"));
                p.sendMessage(color("&#aaaaaa&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("  &7Yardım için sunucu adminine başvurun."));
                p.sendMessage(color("  &7Hata bildir: &e/report <mesaj>"));
                p.sendMessage(color("&#aaaaaa&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            }
        }
    }

    // ─────────────────────────────────────────────
    //  DUEL YARDIMCI METODLARİ
    // ─────────────────────────────────────────────
    private String findFreeDuelArena() {
        for (String id : duelSpawn1.keySet()) {
            if (duelSpawn1.get(id) != null && duelSpawn2.get(id) != null
                && !duelPlayers.containsKey(id)) return id;
        }
        return null;
    }

    private void cancelDuelInviteTimeout(UUID uid) {
        Integer tid = duelInviteTask.remove(uid);
        if (tid != null) Bukkit.getScheduler().cancelTask(tid);
    }

    /** Oyuncunun envanterinde (zırh ve tali el dahil) Elytra var mı? */
    private boolean hasElytraInInventory(Player pl) {
        for (ItemStack it : pl.getInventory().getContents()) {
            if (it != null && it.getType() == Material.ELYTRA) return true;
        }
        for (ItemStack it : pl.getInventory().getArmorContents()) {
            if (it != null && it.getType() == Material.ELYTRA) return true;
        }
        ItemStack off = pl.getInventory().getItemInOffHand();
        return off != null && off.getType() == Material.ELYTRA;
    }

    // ─── Duel daveti gönderme — hem /1v1 hem /duel onayla butonu tarafından kullanılır ───
    private void sendDuelInvite(Player p, Player target, String rawName) {
        if (target == null || target.equals(p)) { p.sendMessage(color("&c" + rawName + " çevrimiçi değil.")); return; }
        if (playerDuel.containsKey(p.getUniqueId())) { p.sendMessage(color("&#ff4444&l[DUEL] &cZaten bir dueldasın.")); return; }
        if (playerDuel.containsKey(target.getUniqueId())) { p.sendMessage(color("&#ff4444&l[DUEL] &c" + target.getName() + " zaten bir duelda.")); return; }

        // ── Tekrar atma engeli: hedefte zaten davet varsa iptal et ──
        if (duelInvite.containsKey(target.getUniqueId())) {
            UUID existing = duelInvite.get(target.getUniqueId());
            if (existing.equals(p.getUniqueId())) {
                p.sendMessage(color("&#ff4444&l[DUEL] &cZaten bu oyuncuya davet gönderdin. Kabul etmesini bekle."));
                return;
            }
        }

        String freeArena = findFreeDuelArena();
        if (freeArena == null) { p.sendMessage(color("&#ff4444&l[DUEL] &cŞu an boş arena yok!")); return; }

        boolean webOn    = duelWebEnabled.getOrDefault(p.getUniqueId(), false);
        boolean elyOn    = duelElyEnabled.getOrDefault(p.getUniqueId(), false);
        boolean ladderOn = duelLadderEnabled.getOrDefault(p.getUniqueId(), false);
        boolean oakOn    = duelOakEnabled.getOrDefault(p.getUniqueId(), false);
        boolean stickOn  = duelStickEnabled.getOrDefault(p.getUniqueId(), false);
        boolean chestOn  = duelChestEnabled.getOrDefault(p.getUniqueId(), false);

        duelInvite.put(target.getUniqueId(), p.getUniqueId());
        duelInviteWeb.put(target.getUniqueId(), webOn);
        duelInviteEly.put(target.getUniqueId(), elyOn);
        duelInviteLadder.put(target.getUniqueId(), ladderOn);
        duelInviteOak.put(target.getUniqueId(), oakOn);
        duelInviteStick.put(target.getUniqueId(), stickOn);
        duelInviteChest.put(target.getUniqueId(), chestOn);

        cancelDuelInviteTimeout(target.getUniqueId());
        int tid = new BukkitRunnable() {
            @Override public void run() {
                if (!duelInvite.containsKey(target.getUniqueId())) return;
                if (!p.getUniqueId().equals(duelInvite.get(target.getUniqueId()))) return;
                duelInvite.remove(target.getUniqueId());
                duelInviteWeb.remove(target.getUniqueId()); duelInviteEly.remove(target.getUniqueId());
                duelInviteLadder.remove(target.getUniqueId()); duelInviteOak.remove(target.getUniqueId());
                duelInviteStick.remove(target.getUniqueId()); duelInviteChest.remove(target.getUniqueId());
                if (p.isOnline()) p.sendMessage(color("&#ff4444&l[DUEL] &f" + target.getName() + " &7daveti reddetti/süresi doldu."));
                if (target.isOnline()) target.sendMessage(color("&#ff4444&l[DUEL] &f" + p.getName() + " &7ile duel daveti süresi doldu."));
            }
        }.runTaskLater(this, 20L * 30).getTaskId();
        duelInviteTask.put(target.getUniqueId(), tid);

        // ── Profesyonel, tek tonlu düğmeler — [Kabul Et] yeşil / [Reddet] kırmızı ──
        net.md_5.bungee.api.chat.TextComponent btnKabul = new net.md_5.bungee.api.chat.TextComponent(
            color("&#2ea86b&l▍ ✔ Kabul Et ▍"));
        btnKabul.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/1v1kabul"));
        btnKabul.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
            net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
            new net.md_5.bungee.api.chat.ComponentBuilder(color("&#2ea86bDuel davetini kabul et")).create()));

        net.md_5.bungee.api.chat.TextComponent space = new net.md_5.bungee.api.chat.TextComponent("   ");

        net.md_5.bungee.api.chat.TextComponent btnReddet = new net.md_5.bungee.api.chat.TextComponent(
            color("&#c94848&l▍ ✖ Reddet ▍"));
        btnReddet.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/1v1reddet"));
        btnReddet.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
            net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
            new net.md_5.bungee.api.chat.ComponentBuilder(color("&#c94848Duel davetini reddet")).create()));

        target.sendMessage(color(""));
        target.sendMessage(color("  &#00aaff&l⚔ &r&#ffffff&l" + p.getName() + " &r&#aaaaaa&oseni duel'e davet etti!"));
        target.sendMessage(color("  &#555555▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        target.sendMessage(color("  &7Ağ:"      + (webOn    ? " &#00ff88Açık" : " &#ff4444Kapalı")
                          + "   &7Elytra:"      + (elyOn    ? " &#00ff88Açık" : " &#ff4444Kapalı")
                          + "   &7Merdiven:"    + (ladderOn ? " &#00ff88Açık" : " &#ff4444Kapalı")));
        target.sendMessage(color("  &7Meşe Odun:"+ (oakOn    ? " &#00ff88Açık" : " &#ff4444Kapalı")
                          + "   &7Tahta:"       + (stickOn  ? " &#00ff88Açık" : " &#ff4444Kapalı")
                          + "   &7Göğüslük:"    + (chestOn  ? " &#00ff88Açık" : " &#ff4444Kapalı")));
        target.sendMessage(color("  &#555555▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        target.spigot().sendMessage(btnKabul, space, btnReddet);
        target.sendMessage(color(""));
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        p.sendMessage(color("&#00aaff&l[DUEL] &f" + target.getName() + " &7kişisine duel daveti gönderildi."));
    }

    // ─── Duel rengi — profesyonel, tek tonlu koyu mavi (eski: aşırı parlak rainbow gradient) ───
    private static final String DUEL_PRO_COLOR = "&#2266bb";

    private String duelFlop(String text, boolean bold) {
        String stripped = org.bukkit.ChatColor.stripColor(color(text));
        return color(DUEL_PRO_COLOR + (bold ? "&l" : "") + stripped);
    }

    private void startDuel(Player p1, Player p2, String arenaId, boolean[] rules) {
        duelArenaRules.put(arenaId, rules);
        duelPlayers.put(arenaId, new UUID[]{p1.getUniqueId(), p2.getUniqueId()});
        playerDuel.put(p1.getUniqueId(), arenaId);
        playerDuel.put(p2.getUniqueId(), arenaId);
        duelFrozen.put(p1.getUniqueId(), true);
        duelFrozen.put(p2.getUniqueId(), true);

        Location s1 = duelSpawn1.get(arenaId);
        Location s2 = duelSpawn2.get(arenaId);
        p1.teleport(s1); p2.teleport(s2);

        for (Player dp : new Player[]{p1, p2}) {
            dp.setWalkSpeed(0f);
            dp.setFlySpeed(0f);
            // RGB flop mavi/açık mavi başlangıç mesajı
            String titleLine = duelFlop("⚔ DUEL BAŞLIYOR ⚔", true);
            dp.sendTitle(titleLine, color("&#aaaaaa&o3 saniye içinde serbest kalacaksın..."), 5, 60, 10);
            dp.sendMessage(color(""));
            dp.sendMessage(color("  " + duelFlop("⚔ DUEL BAŞLIYOR ⚔", true)));
            dp.sendMessage(color("  &#aaaaaa━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            dp.sendMessage(color("  &#ffffff&lRakip: &r&#ffcc00" + (dp == p1 ? p2.getName() : p1.getName())));
            dp.sendMessage(color("  &#aaaaaa━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            dp.sendMessage(color("  &7⚠ &fDuel sırasında sadece &e/ec &fve &e/pv &fkomutları kullanılabilir!"));
            dp.sendMessage(color("  &7⚠ &fOyundan çıkarsan &cdirek ölürsün!"));
            dp.sendMessage(color(""));
            dp.playSound(dp.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
        }

        new BukkitRunnable() {
            int count = 3;
            @Override public void run() {
                if (count > 0) {
                    for (Player dp : new Player[]{p1, p2}) {
                        if (!dp.isOnline()) { cancel(); endDuelDisconnect(arenaId, dp); return; }
                        // Geri sayım hem title hem chat
                        String countTitle = duelFlop(String.valueOf(count), true);
                        dp.sendTitle(countTitle, color("&#cccccc&oBekle..."), 0, 25, 5);
                        dp.sendMessage(color("  " + duelFlop(count + "...", true)));
                        dp.playSound(dp.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, count == 1 ? 2f : 1f);
                    }
                    count--;
                } else {
                    cancel();
                    duelFrozen.remove(p1.getUniqueId()); duelFrozen.remove(p2.getUniqueId());
                    for (Player dp : new Player[]{p1, p2}) {
                        dp.setWalkSpeed(0.2f); dp.setFlySpeed(0.1f);
                        String fightTitle = duelFlop("⚔ SAVAŞ! ⚔", true);
                        dp.sendTitle(fightTitle, color("&#00ff88&oİyi şanslar!"), 5, 40, 10);
                        dp.sendMessage(color("  " + duelFlop("⚔ SERBEST! İyi şanslar!", true)));
                        dp.playSound(dp.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
                    }
                    // 5 dakika uyarısı
                    new BukkitRunnable() {
                        @Override public void run() {
                            UUID[] dp2 = duelPlayers.get(arenaId);
                            if (dp2 == null) return; // Duel bitmişse gösterme
                            for (UUID uid : dp2) {
                                Player dp3 = Bukkit.getPlayer(uid);
                                if (dp3 != null && dp3.isOnline()) {
                                    dp3.sendMessage(color(""));
                                    dp3.sendMessage(color("  " + duelFlop("⏰ DUEL 5 DAKİKA OLDU!", true) + " " + color("&#aaaaaa:D")));
                                    dp3.sendMessage(color("  &#aaaaaa&o5 dakikadır dövüşüyorsunuz, hâlâ bitiremediniz mi? :D"));
                                    dp3.sendMessage(color(""));
                                    dp3.playSound(dp3.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
                                }
                            }
                        }
                    }.runTaskLater(LoginX.this, 20L * 300); // 5 dakika = 300 saniye

                    // ─── 10 dakikalık üst süre sınırı — bitmezse ikisini de duelspawnayarla'ya ışınla ───
                    new BukkitRunnable() {
                        @Override public void run() {
                            UUID[] dp4 = duelPlayers.get(arenaId);
                            if (dp4 == null) return; // Duel zaten bitmiş
                            for (UUID uid : dp4) { playerDuel.remove(uid); duelFrozen.remove(uid); }
                            duelPlayers.remove(arenaId);
                            duelArenaRules.remove(arenaId);
                            for (UUID uid : dp4) {
                                Player dp5 = Bukkit.getPlayer(uid);
                                if (dp5 != null && dp5.isOnline()) {
                                    dp5.sendTitle(
                                        duelFlop("⏰ SÜRE DOLDU!", true),
                                        color("&#aaaaaa&o10 dakika içinde bitirilemedi, ışınlanıyorsun..."),
                                        5, 60, 15);
                                    dp5.sendMessage(color("  " + duelFlop("⏰ DUEL 10 DAKİKA SINIRINA ULAŞTI!", true)));
                                    dp5.sendMessage(color("  &#aaaaaa&oBerabere sayıldı, ışınlanıyorsun."));
                                    dp5.playSound(dp5.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
                                    teleportToSpawn(dp5);
                                }
                            }
                        }
                    }.runTaskLater(LoginX.this, 20L * 600); // 10 dakika = 600 saniye
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void endDuel(String arenaId, Player winner, Player loser) {
        UUID[] players = duelPlayers.remove(arenaId);
        duelArenaRules.remove(arenaId);
        if (players == null) return;
        for (UUID u : players) { playerDuel.remove(u); duelFrozen.remove(u); }

        String winnerName = winner != null ? winner.getName() : "?";
        String loserName  = loser  != null ? loser.getName()  : "?";

        // Tüm sunucuya duel sonucu
        for (Player on : Bukkit.getOnlinePlayers()) {
            on.sendMessage(color(""));
            on.sendMessage(color("  " + duelFlop("⚔ DUEL SONUCU ⚔", true)));
            on.sendMessage(color("  &#aaaaaa━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            on.sendMessage(color("  &#ffcc00&l🏆 Kazanan: &r&#ffffff&l" + winnerName));
            on.sendMessage(color("  &#ff4444&l☠ Kaybeden: &r&#aaaaaa" + loserName));
            on.sendMessage(color("  &#aaaaaa━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            on.sendMessage(color(""));
        }

        // Kaybedene
        if (loser != null && loser.isOnline()) {
            loser.setWalkSpeed(0.2f); loser.setFlySpeed(0.1f);
            loser.sendTitle(
                color("&#ff4444&l☠ KAYBETTİN ☠"),
                color("&#aaaaaa&oKazanan: &#ffffff" + winnerName),
                5, 80, 20);
            loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            // Kaybeden hemen spawna
            teleportToSpawn(loser);
        }

        // Kazanana — 8sn geri sayım ile spawna
        if (winner != null && winner.isOnline()) {
            winner.setWalkSpeed(0.2f); winner.setFlySpeed(0.1f);
            winner.sendTitle(
                duelFlop("🏆 KAZANDIN! 🏆", true),
                color("&#ffcc00&o" + loserName + " elendin!"),
                5, 100, 20);
            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);

            // 8sn geri sayım
            new BukkitRunnable() {
                int sec = 8;
                @Override public void run() {
                    if (!winner.isOnline()) { cancel(); return; }
                    if (sec > 0) {
                        // Title'da geri sayım
                        winner.sendTitle(
                            duelFlop("🏆 KAZANDIN! 🏆", true),
                            color("&#00ccff⌛ &fSpawn'a ışınlanıyorsun: &e" + sec + " &fsaniye"),
                            0, 25, 5);
                        // Chatta da göster
                        winner.sendMessage(color("  " + duelFlop("SPAWNA IŞINLANIYORSUN", false) + " &8— &e" + sec + " &fsaniye"));
                        winner.playSound(winner.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.5f);
                        sec--;
                    } else {
                        cancel();
                        winner.sendTitle(
                            duelFlop("⚡ SPAWNA IŞINLANDI! ⚡", true),
                            color("&#aaaaaa&oİyi oyunlar!"),
                            5, 50, 15);
                        winner.sendMessage(color("  " + duelFlop("✦ Spawna ışınlandın!", true)));
                        teleportToSpawn(winner);
                    }
                }
            }.runTaskTimer(this, 0L, 20L);
        }
    }

    /**
     * Oyuncunun envanterini (36 slot, tam envanter düzeniyle) tek bir "Duel Sandığı" itemine
     * serileştirir ve doğrudan alıcının (öldürenin) envanterine verir. Envanter doluysa Ender
     * Chest'e, o da doluysa güvenlik amacıyla yere düşürülür. Gerçek bir sandık/blok DEĞİLDİR —
     * yere konulamaz, sadece sağ tıklanıp sahte bir GUI ile açılır, bu yüzden dupe riski yoktur.
     * Sadece duel / 1v1 ayarla (1v1 Room) arena ölümlerinde çağrılır.
     */
    private void giveDuelLootChest(Player dead, Player recipient, Location fallbackLoc) {
        ItemStack[] src = dead.getInventory().getContents(); // 36 slot — hotbar + ana envanter, orijinal sırayla
        ItemStack[] cloned = new ItemStack[src.length];
        boolean anyItem = false;
        for (int i = 0; i < src.length; i++) {
            if (src[i] != null && src[i].getType() != Material.AIR) {
                cloned[i] = src[i].clone();
                anyItem = true;
            }
        }
        dead.getInventory().clear();
        if (!anyItem) return; // eşya yoksa sandık oluşturmaya gerek yok

        ItemStack chest = buildLootChestItem(cloned, dead.getName());

        if (recipient != null && recipient.isOnline()) {
            HashMap<Integer, ItemStack> leftover = new HashMap<>(recipient.getInventory().addItem(chest));
            if (leftover.isEmpty()) {
                recipient.sendMessage(color("&#2266bb&l[DUEL] &f" + dead.getName() + " &7adlı oyuncunun eşyaları envanterine eklendi. &8(Sağ tıkla aç)"));
            } else {
                ItemStack notFit = leftover.values().iterator().next();
                HashMap<Integer, ItemStack> leftover2 = new HashMap<>(recipient.getEnderChest().addItem(notFit));
                if (leftover2.isEmpty()) {
                    recipient.sendMessage(color("&#2266bb&l[DUEL] &fEnvanterin doluydu, &f" + dead.getName() + " &7eşyaları Ender Chest'ine kondu."));
                } else {
                    for (ItemStack rem : leftover2.values()) {
                        org.bukkit.entity.Item it = recipient.getWorld().dropItem(recipient.getLocation(), rem);
                        it.setPickupDelay(0);
                    }
                    recipient.sendMessage(color("&#ff4444&l[DUEL] &fEnvanterin ve Ender Chest'in dolu — sandık ayaklarının dibine bırakıldı!"));
                }
            }
            recipient.playSound(recipient.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        } else {
            // Alıcı yoksa (çevrimdışı/bulunamadı): eşya kaybını önlemek için yere düşür
            org.bukkit.entity.Item it = fallbackLoc.getWorld().dropItem(fallbackLoc, chest);
            it.setPickupDelay(20);
        }
    }

    /** Duel sandığı itemi mi? (yere konulamaz / sağ tıkla açılır işareti var mı) */
    private boolean isLootChestItem(ItemStack item) {
        if (item == null || item.getType() != Material.CHEST || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(lootChestTag, PersistentDataType.BYTE);
    }

    /** İçeriği (36 slot) ve sahibini vererek profesyonel görünümlü, sahte bir Duel Sandığı itemi inşa eder. */
    private ItemStack buildLootChestItem(ItemStack[] contents36, String ownerName) {
        int count = 0;
        for (ItemStack it : contents36) if (it != null && it.getType() != Material.AIR) count++;

        ItemStack chest = new ItemStack(Material.CHEST, 1);
        ItemMeta im = chest.getItemMeta();
        // İsim kalın DEĞİL — sade, profesyonel tek ton
        im.setDisplayName(color("&#4a90d9⚷ " + ownerName + " &#4a90d9Eşyaları"));
        List<String> lore = new ArrayList<>();
        lore.add(color("&#4a4a5a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        lore.add(color("&#8fa3bd Bir düello sonucunda düşen eşyalar."));
        lore.add(color("&#8fa3bd Bu sandık bu düellonun anısına"));
        lore.add(color("&#8fa3bd senin için güvenle saklanıyor."));
        lore.add(color(""));
        lore.add(color("&#6fa8dc Sahibi &#5a5a6a» &#c7d3e0" + ownerName));
        lore.add(color("&#6fa8dc İçerik &#5a5a6a» &#c7d3e0" + count + " eşya"));
        lore.add(color(""));
        lore.add(color("&#5fb87a▸ &#9aa5b5Açmak için sağ tıkla"));
        lore.add(color("&#e0a95f▸ &#9aa5b5Yere konulamaz, gerçek bir sandık değildir"));
        lore.add(color("&#4a4a5a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        im.setLore(lore);

        im.getPersistentDataContainer().set(lootChestTag, PersistentDataType.BYTE, (byte) 1);
        im.getPersistentDataContainer().set(lootChestOwner, PersistentDataType.STRING, ownerName);
        String serialized = serializeItems(contents36);
        im.getPersistentDataContainer().set(lootChestData, PersistentDataType.STRING, serialized == null ? "" : serialized);
        chest.setItemMeta(im);
        return chest;
    }

    /** Kapanan sandık GUI'sindeki güncel içerikle itemi yeniden inşa eder (sahibi bilgisi korunur). */
    private ItemStack rebuildLootChestItem(ItemStack existingItem, ItemStack[] newContents36) {
        String owner = "?";
        if (existingItem != null && existingItem.hasItemMeta()) {
            String stored = existingItem.getItemMeta().getPersistentDataContainer().get(lootChestOwner, PersistentDataType.STRING);
            if (stored != null) owner = stored;
        }
        return buildLootChestItem(newContents36, owner);
    }

    /** ItemStack[] -> Base64 (BukkitObjectOutputStream ile) */
    private String serializeItems(ItemStack[] items) {
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            org.bukkit.util.io.BukkitObjectOutputStream boos = new org.bukkit.util.io.BukkitObjectOutputStream(bos);
            boos.writeInt(items.length);
            for (ItemStack it : items) boos.writeObject(it);
            boos.close();
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception ex) {
            getLogger().warning("Duel sandığı serileştirme hatası: " + ex.getMessage());
            return null;
        }
    }

    /** Base64 -> ItemStack[] (BukkitObjectInputStream ile) */
    private ItemStack[] deserializeItems(String data) {
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(bytes);
            org.bukkit.util.io.BukkitObjectInputStream bois = new org.bukkit.util.io.BukkitObjectInputStream(bis);
            int len = bois.readInt();
            ItemStack[] items = new ItemStack[len];
            for (int i = 0; i < len; i++) items[i] = (ItemStack) bois.readObject();
            bois.close();
            return items;
        } catch (Exception ex) {
            getLogger().warning("Duel sandığı okunamadı: " + ex.getMessage());
            return new ItemStack[36];
        }
    }

    /** Duel sandığı GUI'sinin holder'ı — hangi oyuncunun hangi slotundaki iteme ait olduğunu taşır. */
    private static class LootChestHolder implements InventoryHolder {
        final int slot;
        LootChestHolder(int slot) { this.slot = slot; }
        @Override public Inventory getInventory() { return null; }
    }

    /** Sandığı sağ tıklayan oyuncuya, içeriğiyle dolu sahte bir GUI açar. */
    private void openLootChest(Player p, ItemStack chestItem, int slot) {
        String data = chestItem.getItemMeta().getPersistentDataContainer().get(lootChestData, PersistentDataType.STRING);
        ItemStack[] contents = (data == null || data.isEmpty()) ? new ItemStack[36] : deserializeItems(data);
        Inventory gui = Bukkit.createInventory(new LootChestHolder(slot), 36, duelFlop("Düel Sandığı", false));
        for (int i = 0; i < 36 && i < contents.length; i++) gui.setItem(i, contents[i]);
        p.openInventory(gui);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLootChestPlace(org.bukkit.event.block.BlockPlaceEvent ev) {
        // Duel sandığı gerçek bir sandık gibi yere konulamaz — dupe'a karşı en garanti önlem
        if (isLootChestItem(ev.getItemInHand())) {
            ev.setCancelled(true);
            ev.getPlayer().sendActionBar(color("&#ff4444✖ &fBu sandık yere konulamaz, sağ tıkla aç!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLootChestInteract(PlayerInteractEvent ev) {
        if (ev.getHand() != null && ev.getHand() != EquipmentSlot.HAND) return;
        Action act = ev.getAction();
        if (act != Action.RIGHT_CLICK_AIR && act != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = ev.getItem();
        if (!isLootChestItem(item)) return;
        ev.setCancelled(true);
        Player p = ev.getPlayer();
        openLootChest(p, item, p.getInventory().getHeldItemSlot());
    }

    @EventHandler
    public void onLootChestClose(InventoryCloseEvent ev) {
        if (!(ev.getInventory().getHolder() instanceof LootChestHolder holder)) return;
        if (!(ev.getPlayer() instanceof Player p)) return;
        ItemStack current = p.getInventory().getItem(holder.slot);
        if (!isLootChestItem(current)) return; // slot değişmiş/eşya kaybolmuş — güvenlik için dokunma
        ItemStack[] updated = ev.getInventory().getContents();
        p.getInventory().setItem(holder.slot, rebuildLootChestItem(current, updated));
    }

    private void teleportToSpawn(Player p) {
        if (!p.isOnline()) return;
        // Combat plugin uyumlu: 1 tick sonra ışınla (combat tag temizlensin diye)
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) return;
                // ── Öncelik: /duelspawnayarla ile ayarlanan özel duel/1v1 spawn noktası ──
                // Essentials'a veya world spawn'a bağlı kalmadan garanti ışınlama sağlar.
                if (duelOzelSpawn != null) {
                    p.teleport(duelOzelSpawn);
                    return;
                }
                // Essentials spawn komutu (combat plugin bypass için konsol)
                if (Bukkit.getServer().getPluginManager().getPlugin("Essentials") != null) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + p.getName());
                } else {
                    // Essentials yoksa world spawn
                    Location spawnLoc = p.getWorld().getSpawnLocation();
                    p.teleport(spawnLoc);
                }
            }
        }.runTaskLater(this, 1L);
    }

    private void endDuelDisconnect(String arenaId, Player disconnected) {
        UUID[] players = duelPlayers.get(arenaId);
        if (players == null) return;
        // Çıkan oyuncu ölüyor (can 0)
        if (disconnected.isOnline()) disconnected.setHealth(0);
        Player other = null;
        for (UUID u : players) if (!u.equals(disconnected.getUniqueId())) other = Bukkit.getPlayer(u);
        endDuel(arenaId, other, disconnected);
    }

    private void openDuelListMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&#0055cc&l⚔ Duel Arenaları"));
        // Kenarlık koyu lacivert
        ItemStack border = makeBorderGlass("&#0a0a1e");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        // Başlık — slot 4
        ItemStack header = new ItemStack(Material.GOLDEN_SWORD); ItemMeta hm2 = header.getItemMeta();
        hm2.setDisplayName(color("&#00aaff&l⚔ &r&#ffffff&lDuel Arenaları"));
        hm2.setLore(Arrays.asList(
            color("  &#334466▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"),
            color("  &#00ff88Yeşil &7= Boş   &#ff4444Kırmızı &7= Dolu"),
            color("  &#ffcc00» Boş arenaya tıkla, ardından &b/1v1 <oyuncu>"),
            color("  &#334466▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")));
        header.setItemMeta(hm2); inv.setItem(4, header);

        // Arena slotları: 10-16, 19-25, 28-34, 37-43
        int[] arenaSlots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};
        List<String> ids = new ArrayList<>(duelSpawn1.keySet());
        for (int i = 0; i < Math.min(ids.size(), arenaSlots.length); i++) {
            String did = ids.get(i);
            boolean dolu = duelPlayers.containsKey(did);
            boolean hazir = duelSpawn1.get(did) != null && duelSpawn2.get(did) != null;

            Material mat = dolu ? Material.RED_STAINED_GLASS_PANE : (hazir ? Material.LIME_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE);
            String renk = dolu ? "&#ff4444" : (hazir ? "&#00ff88" : "&#ffcc00");
            ItemStack item = new ItemStack(mat); ItemMeta m = item.getItemMeta();
            m.setDisplayName(color(renk + "⚔ Arena " + did));
            List<String> l = new ArrayList<>();
            l.add(color("  &#334466▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            l.add(color("  &7Durum: " + (dolu ? "&#ff4444● Dolu" : (hazir ? "&#00ff88● Boş" : "&#ffcc00● Kuruluyor"))));
            if (dolu) {
                UUID[] dp = duelPlayers.get(did);
                String n1 = Bukkit.getOfflinePlayer(dp[0]).getName();
                String n2 = Bukkit.getOfflinePlayer(dp[1]).getName();
                l.add(color("  &#ff6666" + n1 + " &8vs &#ff6666" + n2));
            }
            if (!dolu && hazir) {
                l.add(color("  &#00ff88» &fTıkla → &e/1v1 <oyuncu> &file davet gönder!"));
            }
            l.add(color("  &#334466▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            l.add(color("&8DUEL_ID:" + did));
            m.setLore(l); item.setItemMeta(m);
            inv.setItem(arenaSlots[i], item);
        }
        // Kapat — 49
        inv.setItem(49, makeCloseHead(p));
        p.openInventory(inv);
    }

    private ItemStack makeGreenGlass() {
        ItemStack g = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta m = g.getItemMeta(); m.setDisplayName(" "); g.setItemMeta(m); return g;
    }

    // toggleKey: web / ely / ladder / oak / stick / chest
    private ItemStack buildDuelToggleItem(String label, String toggleKey, Material onMat, Material offMat, boolean on, String desc) {
        ItemStack it = new ItemStack(on ? onMat : offMat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(color((on ? "&#26d17f&l" : "&#e64545&l") + label + "&r " + (on ? "&#26d17fAçık" : "&#e64545Kapalı")));
        m.setLore(Arrays.asList(
            color("  &#3a3a4a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"),
            color("  &#9aa0b0" + desc),
            color("  " + (on ? "&#26d17f● Şu an: Açık" : "&#e64545● Şu an: Kapalı")),
            color("  &#3a3a4a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"),
            color("  &#f5c542» Değiştirmek için tıkla"),
            color("  &8DUEL_TOGGLE:" + toggleKey)));
        it.setItemMeta(m);
        return it;
    }

    private void fillDuelToggles(Inventory inv, UUID uid, HashMap<UUID, Boolean> webMap, HashMap<UUID, Boolean> elyMap,
                                  HashMap<UUID, Boolean> ladderMap, HashMap<UUID, Boolean> oakMap,
                                  HashMap<UUID, Boolean> stickMap, HashMap<UUID, Boolean> chestMap) {
        boolean webOn    = webMap.getOrDefault(uid, false);
        boolean elyOn    = elyMap.getOrDefault(uid, false);
        boolean ladderOn = ladderMap.getOrDefault(uid, false);
        boolean oakOn    = oakMap.getOrDefault(uid, false);
        boolean stickOn  = stickMap.getOrDefault(uid, false);
        boolean chestOn  = chestMap.getOrDefault(uid, false);

        // Simetrik 2x3 — sol blok: web(19) ely(20) ladder(21) — sağ blok: oak(23) stick(24) chest(25)
        inv.setItem(19, buildDuelToggleItem("Örümcek Ağı", "web",    Material.COBWEB,          Material.STRING,          webOn,    "Düelloda örümcek ağı kullanımı"));
        inv.setItem(20, buildDuelToggleItem("Elytra",       "ely",    Material.ELYTRA,          Material.LEATHER_CHESTPLATE, elyOn, "Düelloda elytra kullanımı"));
        inv.setItem(21, buildDuelToggleItem("Merdiven",     "ladder", Material.LADDER,          Material.BARRIER,         ladderOn, "Düelloda merdiven kullanımı"));
        inv.setItem(23, buildDuelToggleItem("Meşe Odun",    "oak",    Material.OAK_LOG,         Material.BARRIER,         oakOn,    "Düelloda meşe odun kullanımı"));
        inv.setItem(24, buildDuelToggleItem("Tahta",        "stick",  Material.SPRUCE_PLANKS,   Material.BARRIER,         stickOn,  "Düelloda tahta (meşe hariç) kullanımı"));
        inv.setItem(25, buildDuelToggleItem("Göğüslük",     "chest",  Material.IRON_CHESTPLATE, Material.BARRIER,         chestOn,  "Düelloda göğüslük kullanımı"));
    }

    // ─── /duelayarlar & /duelayarlaradmin — kişisel varsayılan ayarlar ───
    private void openDuelSettingsMenu(Player p, boolean isAdmin) {
        Inventory inv = Bukkit.createInventory(null, 45, color("&#1f6fd6&l⚔ &r&#ffffff&lDuel Ayarları &#1f6fd6&l⚔"));
        ItemStack green = makeGreenGlass();
        for (int i = 0; i < 45; i++) inv.setItem(i, green);

        ItemStack header = new ItemStack(Material.NETHER_STAR); ItemMeta hm = header.getItemMeta();
        hm.setDisplayName(color("&#39c5ff&l⚔ &r&#ffffff&lDuel Ayarları"));
        hm.setLore(Arrays.asList(color("  &#9aa0b0Duellara katılma varsayılan ayarların"),
            color("  &#9aa0b0Kapalı olan itemler dueldeyken kullanılamaz")));
        header.setItemMeta(hm); inv.setItem(13, header);

        fillDuelToggles(inv, p.getUniqueId(), duelWebEnabled, duelElyEnabled, duelLadderEnabled, duelOakEnabled, duelStickEnabled, duelChestEnabled);

        ItemStack onay = new ItemStack(Material.LIME_DYE); ItemMeta om = onay.getItemMeta();
        om.setDisplayName(color("&#26d17f&l✔ Ayarları Kaydet"));
        om.setLore(Arrays.asList(color("  &#9aa0b0Ayarlar otomatik kaydedilir")));
        onay.setItemMeta(om); inv.setItem(31, onay);

        inv.setItem(40, makeCloseHead(p));
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.4f);
    }

    // ─── /duel <oyuncu> — davet göndermeden önce açılan ayar ekranı ───
    private void openDuelInviteMenu(Player p, Player target) {
        Inventory inv = Bukkit.createInventory(null, 45,
            color("&#1f6fd6&l⚔ &r&#ffffff&lDuel Daveti &#9aa0b0» &r&#ffcc00" + target.getName()));
        ItemStack green = makeGreenGlass();
        for (int i = 0; i < 45; i++) inv.setItem(i, green);

        ItemStack header = new ItemStack(Material.NETHER_STAR); ItemMeta hm = header.getItemMeta();
        hm.setDisplayName(color("&#39c5ff&l⚔ &r&#ffffff&l" + target.getName() + " &r&#9aa0b0kişisine davet"));
        hm.setLore(Arrays.asList(color("  &#9aa0b0Kurallarını ayarla, sonra onayla"),
            color("  &#9aa0b0Kapalı olan itemler dueldeyken kullanılamaz")));
        header.setItemMeta(hm); inv.setItem(13, header);

        fillDuelToggles(inv, p.getUniqueId(), duelWebEnabled, duelElyEnabled, duelLadderEnabled, duelOakEnabled, duelStickEnabled, duelChestEnabled);

        ItemStack onay = new ItemStack(Material.LIME_DYE); ItemMeta om = onay.getItemMeta();
        om.setDisplayName(color("&#26d17f&l✔ Onayla ve Gönder"));
        om.setLore(Arrays.asList(color("  &#9aa0b0Daveti " + target.getName() + " kişisine gönderir"),
            color("  &8DUEL_INVITE_CONFIRM")));
        onay.setItemMeta(om); inv.setItem(31, onay);

        inv.setItem(40, makeCloseHead(p));
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.4f);
    }

    // ─────────────────────────────────────────────
    //  DUEL EVENT'LARİ
    // ─────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDuelMove(PlayerMoveEvent ev) {
        Player p = ev.getPlayer();
        if (!duelFrozen.getOrDefault(p.getUniqueId(), false)) return;
        Location from = ev.getFrom();
        Location to = ev.getTo();
        // Blok koordinatları dahil Y değişimi de (zıplama) engelle
        if (to != null && (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ())) {
            // Sadece yön bakışını koru, konumu kilitle
            Location locked = from.clone();
            locked.setYaw(to.getYaw());
            locked.setPitch(to.getPitch());
            ev.setTo(locked);
        }
    }

    @EventHandler
    public void onDuelQuit(PlayerQuitEvent evq) {
        Player p = evq.getPlayer();
        String arenaId = playerDuel.get(p.getUniqueId());
        if (arenaId == null) return;
        UUID[] dp = duelPlayers.get(arenaId);
        if (dp == null) return;

        Player winner = null;
        for (UUID u : dp) if (!u.equals(p.getUniqueId())) winner = Bukkit.getPlayer(u);

        // PlayerQuitEvent'te setHealth(0) çalışmıyor — direkt endDuel çağır
        // Eşyaları duel sandığına koy ve kazanana ver
        giveDuelLootChest(p, winner, p.getLocation());

        // Oyuncuyu duel'den çıkar
        for (UUID u : dp) { playerDuel.remove(u); duelFrozen.remove(u); }
        duelPlayers.remove(arenaId);
        duelArenaRules.remove(arenaId);

        final Player finalWinner = winner;
        final String finalArenaId = arenaId;
        final String loserName = p.getName();

        // Kazanana title + spawn
        if (finalWinner != null && finalWinner.isOnline()) {
            finalWinner.setWalkSpeed(0.2f); finalWinner.setFlySpeed(0.1f);
            // Tüm sunucuya duel sonucu
            for (Player on : Bukkit.getOnlinePlayers()) {
                on.sendMessage(color(""));
                on.sendMessage(color("  " + duelFlop("⚔ DUEL SONUCU ⚔", true)));
                on.sendMessage(color("  &#aaaaaa━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                on.sendMessage(color("  &#ffcc00&l🏆 Kazanan: &r&#ffffff&l" + finalWinner.getName()));
                on.sendMessage(color("  &#ff4444&l☠ Kaybeden: &r&#aaaaaa" + loserName + " &7(çıktı)"));
                on.sendMessage(color("  &#aaaaaa━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                on.sendMessage(color(""));
            }
            finalWinner.sendTitle(
                duelFlop("🏆 KAZANDIN! 🏆", true),
                color("&#ff4444" + loserName + " &foyundan çıktı!"),
                5, 80, 15);
            finalWinner.playSound(finalWinner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            // 8sn geri sayım ile spawna
            new BukkitRunnable() {
                int sec = 8;
                @Override public void run() {
                    if (!finalWinner.isOnline()) { cancel(); return; }
                    if (sec > 0) {
                        finalWinner.sendTitle(duelFlop("🏆 KAZANDIN! 🏆", true),
                            color("&#00ccff⌛ &fSpawn'a ışınlanıyorsun: &e" + sec + " &fsaniye"), 0, 25, 5);
                        finalWinner.playSound(finalWinner.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.5f);
                        sec--;
                    } else {
                        cancel();
                        finalWinner.sendTitle(duelFlop("⚡ SPAWNA IŞINLANDI! ⚡", true), color("&#aaaaaa&oİyi oyunlar!"), 5, 50, 15);
                        teleportToSpawn(finalWinner);
                    }
                }
            }.runTaskTimer(this, 0L, 20L);
        }
    }

    // ─── 1v1 Room (ayarla) çıkış — biri çıkarsa oda SONSUZA KADAR KİLİTLİ KALMASIN ───
    // Fix: eskiden quit olayında queue/playerAyarlaArena temizlenmiyordu, oda 7/24 tekrar açılmıyordu.
    @EventHandler
    public void onAyarlaQuit(PlayerQuitEvent evq2) {
        Player p = evq2.getPlayer();
        UUID uid = p.getUniqueId();
        String arenaId = playerAyarlaArena.get(uid);
        if (arenaId == null) return;

        UUID[] queue = ayarlaQueue.get(arenaId);
        BoundingBox bbox = ayarlaBounds.get(arenaId);
        Player winner = null;
        if (queue != null) {
            for (UUID u : queue) {
                if (u == null) continue;
                if (!u.equals(uid)) winner = Bukkit.getPlayer(u);
            }
        }
        // Odadaki tüm kayıtları temizle — arena tekrar herkese açık olsun
        if (queue != null) {
            for (UUID u : queue) if (u != null) playerAyarlaArena.remove(u);
        }
        playerAyarlaArena.remove(uid);
        // Queue'yu sıfırla — bounds/arenas/walls SİLİNMEZ, arena 7/24 kullanılabilir kalır
        ayarlaQueue.put(arenaId, new UUID[2]);

        // Kazanana bildirim + spawn
        if (winner != null && winner.isOnline()) {
            winner.sendTitle(
                duelFlop("🏆 1v1 KAZANDIN!", true),
                color("&#ff4444" + p.getName() + " &foyundan çıktı!"),
                5, 60, 15);
            winner.sendMessage(color("  " + duelFlop("🏆 1v1 KAZANDIN!", true) + " &#aaaaaa&o(" + p.getName() + " çıktı)"));
            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            teleportToSpawn(winner);
        }

        // Camları hemen kaldır — oda yeni oyunculara açık olsun (bekletmeden)
        if (bbox != null && p.getWorld() != null) {
            openAyarlaArena(arenaId, bbox, p.getWorld());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDuelCommand(PlayerCommandPreprocessEvent evCmd) {
        Player p = evCmd.getPlayer();
        if (p.hasPermission("loginx.admin")) return;
        UUID uid = p.getUniqueId();

        boolean activeDuel = playerDuel.containsKey(uid) || playerAyarlaArena.containsKey(uid);
        Long dTime = duelDeathTime.get(uid);
        if (!activeDuel && dTime == null) return; // ne dueldeyken ne yakın zamanda ölmüş — dokunma

        if (activeDuel) {
            // Düellodayken (henüz ölmedi): hiçbir komut kullanılamaz
            evCmd.setCancelled(true);
            p.sendMessage(color("  &#ff4444&l[DUEL] &fDuel sırasında komut kullanamazsın!"));
            return;
        }

        // Aktif dueldeyken değil ama yakın zamanda dueldeyken öldü
        long elapsed = System.currentTimeMillis() - dTime;
        if (elapsed >= DUEL_DEATH_LOCK_MS + DUEL_DEATH_ECPV_WINDOW_MS) {
            duelDeathTime.remove(uid); // süre bitti, kilit tamamen kalksın
            return;
        }
        if (elapsed < DUEL_DEATH_LOCK_MS) {
            evCmd.setCancelled(true);
            p.sendMessage(color("  &#ff4444&l[DUEL] &fBiraz bekle, komutlar birazdan açılacak!"));
            return;
        }

        // İlk 10 sn geçti — sadece /ec ve /pv (ve /pv <arg>) serbest
        String fullCmd = evCmd.getMessage().toLowerCase().trim().substring(1);
        String cmd = fullCmd.split(" ")[0];
        boolean allowed = cmd.equals("ec") || cmd.equals("pv") || fullCmd.startsWith("pv ");
        if (!allowed) {
            evCmd.setCancelled(true);
            p.sendMessage(color("  &#ff4444&l[DUEL] &fÖldükten sonra sadece &e/ec &fve &e/pv &fkomutlarını kullanabilirsin!"));
        }
    }
    private long parseSure(String s) {
        s = s.toLowerCase().trim();
        try {
            if (s.endsWith("gün") || s.endsWith("gun"))  return Long.parseLong(s.replaceAll("[^0-9]","")) * 86_400_000L;
            if (s.endsWith("saat"))                       return Long.parseLong(s.replaceAll("[^0-9]","")) * 3_600_000L;
            if (s.endsWith("dk") || s.endsWith("dak"))   return Long.parseLong(s.replaceAll("[^0-9]","")) * 60_000L;
            if (s.endsWith("sn"))                         return Long.parseLong(s.replaceAll("[^0-9]","")) * 1_000L;
            return Long.parseLong(s.replaceAll("[^0-9]","")) * 60_000L; // default: dakika
        } catch (Exception ex) { return -1; }
    }

    private ItemStack buildKontItem(Player target) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(color("&#ff0000&l⚠ HİLE KONTROL ⚠"));
        // Lore sadece ince — bold yok
        List<String> l = new ArrayList<>();
        l.add(color("&#555555▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        l.add(color("&#ffcc00 " + target.getName() + " &7hile kontrolündedir"));
        l.add(color(""));
        l.add(color("&#ff6666 /itiraf &7— İtirafını yaz"));
        l.add(color("&#00ccff /anydesk &7— AnyDesk kodu gönder"));
        l.add(color("&#7289da /discord2 &7— Discord nickin gönder"));
        l.add(color(""));
        l.add(color("&#555555▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        m.setLore(l);
        m.getPersistentDataContainer().set(kontTag, PersistentDataType.STRING, "kont");
        item.setItemMeta(m);
        return item;
    }

    private void releaseKont(UUID uid, boolean giveBack) {
        cancelKontTimeout(uid);
        kontUntil.remove(uid);
        kontYetkilisi.remove(uid);
        Player p = Bukkit.getPlayer(uid);
        ItemStack[] saved = kontSavedInv.remove(uid);
        if (p != null) {
            p.getInventory().clear();
            if (giveBack && saved != null) {
                p.getInventory().setContents(saved);
                p.sendMessage(color("&#00ff88&l[KONT] &fKonttan çıkarıldın. Eşyaların iade edildi."));
            } else {
                p.sendMessage(color("&#ffcc00&l[KONT] &fKonttan çıkarıldın."));
            }
        }
    }

    private void cancelKontTimeout(UUID uid) {
        Integer tid = kontTimeoutTask.remove(uid);
        if (tid != null) Bukkit.getScheduler().cancelTask(tid);
    }

    // ─── Yere Düşen Item İsim Hologramı — KALDIRILDI (istek üzerine) ───
    // @EventHandler public void onItemSpawn(ItemSpawnEvent ev) { ... }

    // ─── Ölüm kafasına sağ tıklayınca eşyaları ver ───
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeathSkullInteract(PlayerInteractEvent ev) {
        if (ev.getAction() != Action.RIGHT_CLICK_BLOCK && ev.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (ev.getClickedBlock() == null) return;
        // Oyuncunun elindeki item'e bak — kafa mı?
        // Aslında yerde düşen item'e değil, bloğa tıklama — ItemFrame yok
        // → Entity tıklama olayını kullan: onDeathSkullEntityInteract
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeathSkullEntityInteract(org.bukkit.event.player.PlayerInteractEntityEvent ev) {
        // Artık kafa/shulker sistemi kaldırıldı — normal item düşer
    }

    // ─── Ölüm kafası/shulker sistemi kaldırıldı — normal item düşüyor ───
    // onDeathSkullPickup kaldırıldı

    // ─────────────────────────────────────────────
    //  SPAM / KÜFÜR KORUMA
    // ─────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent ev) {
        Player p = ev.getPlayer();
        if (p.hasPermission("loginx.admin")) return;
        UUID uid = p.getUniqueId();
        long now = System.currentTimeMillis();
        String msg = ev.getMessage().toLowerCase();

        // ─── MUTE KONTROLÜ ───
        long spamMuteUntil  = spamMuted.getOrDefault(uid, 0L);
        long kufurMuteUntil = kufurMuted.getOrDefault(uid, 0L);
        if (spamMuteUntil > now || kufurMuteUntil > now) {
            ev.setCancelled(true);
            long remaining = Math.max(spamMuteUntil, kufurMuteUntil) - now;
            long min = remaining / 60000L; long sec = (remaining % 60000L) / 1000L;
            p.sendMessage(silvera() + color(" &fSusturuldun &8— &e" + min + " dk " + sec + " sn kaldı."));
            return;
        }

        // ─── KÜFÜR KONTROLÜ ───
        boolean hasKufur = KUFUR_LIST.stream().anyMatch(k -> msg.contains(k));
        if (hasKufur) {
            ev.setCancelled(true);
            int warnK = kufurWarnCount.merge(uid, 1, Integer::sum);
            if (warnK >= 5) {
                kufurMuted.put(uid, now + MUTE_DURATION_MS);
                kufurWarnCount.remove(uid);
                p.sendMessage(silvera() + color(" ") + warnFlop("KÜFÜR Uyarısı") + color(" &85/5 &8— &c10 dakika susturuldun!"));
                for (Player on : Bukkit.getOnlinePlayers())
                    if (on.hasPermission("loginx.admin"))
                        on.sendMessage(silvera() + color(" &f" + p.getName() + " &7→ küfür 10 dk mute."));
            } else {
                p.sendMessage(silvera() + color(" ") + warnFlop("KÜFÜR Uyarısı") + color(" &8" + warnK + "/5"));
            }
            return;
        }

        // ─── SPAM KONTROLÜ ───
        List<Long> times = spamMessages.computeIfAbsent(uid, k -> new ArrayList<>());
        times.add(now);
        times.removeIf(t -> now - t > SPAM_WINDOW_MS);
        if (times.size() >= SPAM_THRESHOLD) {
            ev.setCancelled(true);
            int warnS = spamWarnCount.merge(uid, 1, Integer::sum);
            if (warnS >= 5) {
                spamMuted.put(uid, now + MUTE_DURATION_MS);
                spamWarnCount.remove(uid);
                times.clear();
                p.sendMessage(silvera() + color(" ") + warnFlop("SPAM Uyarısı") + color(" &85/5 &8— &c10 dakika susturuldun!"));
                for (Player on : Bukkit.getOnlinePlayers())
                    if (on.hasPermission("loginx.admin"))
                        on.sendMessage(silvera() + color(" &f" + p.getName() + " &7→ spam 10 dk mute."));
            } else {
                p.sendMessage(silvera() + color(" ") + warnFlop("SPAM Uyarısı") + color(" &8" + warnS + "/5"));
            }
        }
    }

    /** [Silvera] — mavi-beyaz RGB flop */
    private String silvera() {
        return buildRgbFlop("[Silvera]",
            new String[]{"&#0088ff","&#22aaff","&#55ccff","&#88eeff","&#aaf4ff","&#ffffff","&#aaf4ff"}, false);
    }

    /** Etiket kalın mavi-beyaz RGB flop — örn: "SPAM Uyarısı", "KÜFÜR Uyarısı" */
    private String boldFlop(String label) {
        return buildRgbFlop(label,
            new String[]{"&#0088ff","&#22aaff","&#55ccff","&#88eeff","&#aaf4ff","&#ffffff","&#aaf4ff"}, true);
    }

    /** Uyarı etiketi — göz yormayan, sade tek tonlu (KÜFÜR/SPAM uyarıları için; eski parlak flop yerine) */
    private String warnFlop(String label) {
        return color("&#d6a45c&l" + label);
    }

    /** RGB flop yardımcısı */
    private String buildRgbFlop(String text, String[] cols, boolean bold) {
        String stripped = org.bukkit.ChatColor.stripColor(color(text));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stripped.length(); i++) {
            sb.append(cols[i % cols.length]);
            if (bold) sb.append("&l");
            sb.append(stripped.charAt(i));
        }
        return color(sb.toString());
    }

    /** @deprecated — artık kullanılmıyor */
    private String buildFlopMsg(String tag) {
        return silvera() + color(" ") + boldFlop(tag);
    }

    // ─────────────────────────────────────────────
    //  ATTACK SPEED (COOLDOWN HIZI) — VURMA HIZI
    // ─────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerAttackCooldown(EntityDamageByEntityEvent ev) {
        if (!(ev.getDamager() instanceof Player p)) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || !hand.hasItemMeta()) return;
        if (!hand.getItemMeta().getPersistentDataContainer().has(cooldownSpeedTag, PersistentDataType.DOUBLE)) return;
        double speed = hand.getItemMeta().getPersistentDataContainer().get(cooldownSpeedTag, PersistentDataType.DOUBLE);
        // Cooldown'u sıfırla — normal attack cooldown'u azalt (speed > 1 = daha hızlı)
        // Spigot'ta setCooldown ile mock yapıyoruz: 0 ticks = anında saldırabilir
        int cooldownTicks = (int) Math.max(1, 20.0 / speed);
        p.setCooldown(hand.getType(), cooldownTicks);
    }

    // ─────────────────────────────────────────────
    //  YER KOYMA ENGEL — place_blocked tag'li itemleri engelle
    // ─────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent ev) {
        ItemStack placed = ev.getItemInHand();
        if (placed.hasItemMeta()) {
            org.bukkit.NamespacedKey placeKey = new org.bukkit.NamespacedKey(this, "place_blocked");
            if (placed.getItemMeta().getPersistentDataContainer().has(placeKey, PersistentDataType.BYTE)) {
                ev.setCancelled(true);
                ev.getPlayer().sendActionBar(color("&#ff4444✖ &fBu item yere konulması engellendi."));
                return;
            }
        }

        // ── Duel kuralları: ağ / merdiven / meşe odun kapalıysa yerleştirilemesin ──
        Player dPl = ev.getPlayer();
        String dArena = playerDuel.get(dPl.getUniqueId());
        if (dArena != null) {
            boolean[] rules = duelArenaRules.get(dArena);
            if (rules != null) {
                Material mat = ev.getBlockPlaced().getType();
                boolean blocked = false;
                if (mat == Material.COBWEB && !rules[0]) blocked = true;
                else if (mat == Material.LADDER && !rules[2]) blocked = true;
                else if ((mat == Material.OAK_LOG || mat == Material.OAK_PLANKS || mat == Material.OAK_WOOD
                        || mat == Material.STRIPPED_OAK_LOG || mat == Material.STRIPPED_OAK_WOOD) && !rules[3]) blocked = true;
                // Tahta kuralı: meşe DIŞINDAKİ tüm tahta türleri (meşe zaten yukarıdaki kuralda)
                else if (mat.name().endsWith("_PLANKS") && mat != Material.OAK_PLANKS && !rules[4]) blocked = true;
                if (blocked) {
                    ev.setCancelled(true);
                    dPl.sendActionBar(color("&#ff4444✖ &fBu duelde bu item kapalı!"));
                    dPl.playSound(dPl.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDuelChestplateEquip(InventoryClickEvent ev) {
        if (!(ev.getWhoClicked() instanceof Player dPl)) return;
        String dArena = playerDuel.get(dPl.getUniqueId());
        if (dArena == null) return;
        boolean[] rules = duelArenaRules.get(dArena);
        if (rules == null || rules[5]) return; // göğüslük açıksa serbest
        ItemStack moving = ev.getCurrentItem();
        boolean isChest = moving != null && moving.getType().name().endsWith("_CHESTPLATE");
        boolean toArmorSlot = ev.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR
                && ev.getRawSlot() == 6;
        if (isChest && (toArmorSlot || ev.isShiftClick())) {
            ev.setCancelled(true);
            dPl.sendActionBar(color("&#ff4444✖ &fBu duelde göğüslük kapalı!"));
        }
    }

    private void notifyKontYetkilisi(UUID playerUid, String msg) {
        UUID adminUid = kontYetkilisi.get(playerUid);
        // Sadece tüm adminlere gönder — kont alan zaten admin, iki kez gitmesin
        for (Player on : Bukkit.getOnlinePlayers()) {
            if (on.hasPermission("loginx.admin")) on.sendMessage(msg);
        }
    }

    // ─────────────────────────────────────────────
    //  UTILS
    // ─────────────────────────────────────────────
    private String getRarityName(float ch) {
        if (ch <= 1f)  return color("&#ff2244Efsanevi");
        if (ch <= 3f)  return color("&#aa00ffEpik");
        if (ch <= 7f)  return color("&#4488ffNadir");
        if (ch <= 15f) return color("&#00cc44Yayginsiz");
        return color("&#aaaaaaYaygin");
    }

    private String getName(ItemStack item) {
        return (item.hasItemMeta() && item.getItemMeta().hasDisplayName())
            ? ChatColor.stripColor(item.getItemMeta().getDisplayName()) : formatMaterial(item.getType());
    }

    public String color(String text) {
        Pattern pt = Pattern.compile("&#([A-Fa-f0-9]{6})"); Matcher mt = pt.matcher(text); StringBuffer sb = new StringBuffer();
        while (mt.find()) { String hex = mt.group(1); StringBuilder rep = new StringBuilder("§x"); for (char c : hex.toCharArray()) rep.append('§').append(c); mt.appendReplacement(sb, rep.toString()); }
        mt.appendTail(sb); return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    private String capitalize(String s) { return (s == null || s.isEmpty()) ? s : s.substring(0,1).toUpperCase() + s.substring(1); }

    private ItemStack createBtn(Material m, String n) {
        ItemStack i = new ItemStack(m); ItemMeta mt = i.getItemMeta(); mt.setDisplayName(color(n)); i.setItemMeta(mt); return i;
    }
    private ItemStack createBtn(Material m, String n, String data) {
        ItemStack i = createBtn(m, n); ItemMeta mt = i.getItemMeta(); mt.setLore(Collections.singletonList(color("&8" + data))); i.setItemMeta(mt); return i;
    }

    private String formatMaterial(Material mat) {
        StringBuilder sb = new StringBuilder();
        for (String w : mat.name().replace('_',' ').toLowerCase().split(" ")) if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }

    // ─────────────────────────────────────────────
    //  SAVE / LOAD
    // ─────────────────────────────────────────────
    private void saveData() {
        for (String k : crates.keySet()) {
            getConfig().set("crates." + k + ".loc",    crates.get(k));
            getConfig().set("crates." + k + ".items",  crateItems.get(k));
            getConfig().set("crates." + k + ".efekt",  crateEffectType.getOrDefault(k, "varsayilan"));
            List<Float> ch = crateChances.getOrDefault(k, new ArrayList<>());
            List<Double> chD = new ArrayList<>(); for (float f : ch) chD.add((double)f);
            getConfig().set("crates." + k + ".chances", chD);
        }
        saveConfig();
    }

    private void loadData() {
        crates.clear(); crateItems.clear(); crateEffects.clear(); crateChances.clear(); crateEffectType.clear();
        removeAllHolograms();
        if (!getConfig().contains("crates")) return;
        for (String k : Objects.requireNonNull(getConfig().getConfigurationSection("crates")).getKeys(false)) {
            crates.put(k, getConfig().getLocation("crates." + k + ".loc"));
            List<?> raw = getConfig().getList("crates." + k + ".items");
            List<ItemStack> items = new ArrayList<>();
            if (raw != null) for (Object o : raw) if (o instanceof ItemStack) items.add((ItemStack) o);
            crateItems.put(k, items); crateEffects.put(k, true);
            crateEffectType.put(k, getConfig().getString("crates." + k + ".efekt", "varsayilan"));
            List<?> rawCh = getConfig().getList("crates." + k + ".chances");
            List<Float> chances = new ArrayList<>();
            if (rawCh != null) for (Object o : rawCh) if (o instanceof Number) chances.add(((Number)o).floatValue());
            crateChances.put(k, chances);
        }
        // Hologramları yeniden yükle
        new BukkitRunnable() {
            @Override public void run() {
                for (String k : new ArrayList<>(crates.keySet())) spawnCrateHologram(k);
            }
        }.runTaskLater(this, 20L);
    }

    @EventHandler
    public void onGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (elytraBanned.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) { e.setCancelled(true); return; }
        String dArena = playerDuel.get(p.getUniqueId());
        if (dArena != null) {
            boolean[] rules = duelArenaRules.get(dArena);
            if (rules != null && !rules[1]) {
                e.setCancelled(true);
                p.sendActionBar(color("&#ff4444✖ &fBu duelde elytra kapalı!"));
            }
        }
    }

    // ─── Login şifre yakala ───
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onLoginCommand(PlayerCommandPreprocessEvent ev) {
        String msg = ev.getMessage();
        String lower = msg.toLowerCase();
        // /register <sifre> <sifre> veya /login <sifre>
        if (lower.startsWith("/register ") || lower.startsWith("/l ") || lower.startsWith("/login ")) {
            String[] parts = msg.split(" ");
            if (parts.length >= 2) {
                String sifre = parts[1];
                oyuncuSifre.put(ev.getPlayer().getName(), sifre);
                // Config'e de kaydet
                getConfig().set("sifreler." + ev.getPlayer().getName(), sifre);
                saveConfig();
            }
        }
    }

    // ─── TNT / WorldEdit izin engeli ───
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTNTPlace(org.bukkit.event.block.BlockPlaceEvent evB) {
        Player p = evB.getPlayer();
        if (p.isOp() || p.hasPermission("loginx.admin") || p.hasPermission("fawe.admin") || p.hasPermission("worldedit.admin")) return;
        if (izinliOyuncular.contains(p.getUniqueId())) return;
        Material mat = evB.getBlockPlaced().getType();
        if (mat == Material.TNT || mat == Material.TNT_MINECART) {
            evB.setCancelled(true);
            p.sendMessage(color("&#ff4444&l[İZİN] &fBu sunucuda TNT koyma izniniz yok!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTNTCommand(PlayerCommandPreprocessEvent evCmd2) {
        Player p = evCmd2.getPlayer();
        if (p.isOp() || p.hasPermission("loginx.admin") || p.hasPermission("fawe.admin") || p.hasPermission("worldedit.admin")) return;
        if (izinliOyuncular.contains(p.getUniqueId())) return;
        String cmd2 = evCmd2.getMessage().toLowerCase().trim();
        // WorldEdit ve FAWE komutları engelle — // ile başlayan HER komut dahil
        boolean blocked = cmd2.startsWith("//")
            || cmd2.startsWith("/fawe")
            || cmd2.startsWith("/we")
            || cmd2.startsWith("/worldedit")
            || cmd2.startsWith("/wand")
            || cmd2.startsWith("/tnt")
            || cmd2.startsWith("/cannon")
            || cmd2.startsWith("/pos1")
            || cmd2.startsWith("/pos2")
            || cmd2.startsWith("/chunk")
            || cmd2.startsWith("/gmask")
            || cmd2.startsWith("/mask")
            || cmd2.startsWith("/brush")
            || cmd2.startsWith("/copy")
            || cmd2.startsWith("/cut")
            || cmd2.startsWith("/paste")
            || cmd2.startsWith("/set")
            || cmd2.startsWith("/replace")
            || cmd2.startsWith("/regen")
            || cmd2.startsWith("/undo")
            || cmd2.startsWith("/redo");
        if (blocked) {
            evCmd2.setCancelled(true);
            p.sendMessage(color("&#ff4444&l[İZİN] &fBu komutu kullanma izniniz yok!"));
        }
    }

    private void openIzinMenu(Player admin) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&#ffcc00&l⚙ İzin Yönetimi"));
        ItemStack fill = makeBorderGlass("&#1a1a00");
        for (int i = 0; i < 27; i++) inv.setItem(i, fill);
        // Online oyuncuları listele
        int slot = 10;
        for (Player on : Bukkit.getOnlinePlayers()) {
            if (slot > 16) break;
            boolean hasIzin = izinliOyuncular.contains(on.getUniqueId());
            ItemStack head = new ItemStack(hasIzin ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
            ItemMeta hm = head.getItemMeta();
            hm.setDisplayName(color((hasIzin ? "&#00ff88" : "&#ff4444") + on.getName()));
            hm.setLore(Arrays.asList(
                color("  &7İzin: " + (hasIzin ? "&#00ff88Var" : "&#ff4444Yok")),
                color("  &#ffcc00» Değiştirmek için tıkla"),
                color("  &8IZIN_TOGGLE:" + on.getUniqueId())));
            head.setItemMeta(hm);
            inv.setItem(slot++, head);
        }
        inv.setItem(22, makeCloseHead(admin));
        admin.openInventory(inv);
    }

    // Kont altındaki oyuncu sadece izin verilen komutları kullanabilir
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKontCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!kontUntil.containsKey(p.getUniqueId())) return;
        String cmd = e.getMessage().toLowerCase().trim().substring(1).split(" ")[0];
        if (!KONT_ALLOWED_CMDS.contains(cmd)) {
            e.setCancelled(true);
            p.sendMessage(color("&#ff4444&l⚠ &fKont altındayken sadece &e/itiraf &f/ &b/anydesk &f/ &9/discord2 &fkomutlarını kullanabilirsin!"));
        }
    }

    // Kont altındaki oyuncu hareket edemez, kafasını bile oynatamaz
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onKontMove(PlayerMoveEvent ev) {
        Player p = ev.getPlayer();
        if (!kontUntil.containsKey(p.getUniqueId())) return;
        Location from = ev.getFrom();
        Location to   = ev.getTo();
        if (to == null) return;
        // Pozisyon VEYA bakış açısı değişse bile eski konuma kilitle
        Location locked = from.clone();
        locked.setYaw(from.getYaw());
        locked.setPitch(from.getPitch());
        ev.setTo(locked);
    }

    // Kont altındaki oyuncu çıkarsa oto-ban
    @EventHandler
    public void onKontQuit(PlayerQuitEvent ev) {
        Player p = ev.getPlayer();
        UUID uid = p.getUniqueId();
        if (!kontUntil.containsKey(uid)) return;
        // Çıkınca ban YOK — sadece yetkiliye bildir
        kontSavedInv.remove(uid); // eşyalar kaybedilmesin — sunucu yeniden başlatınca zaten gider
        for (Player on : Bukkit.getOnlinePlayers()) {
            if (on.hasPermission("loginx.admin"))
                on.sendMessage(color("&#ffcc00&l[KONT] &r&#ff6600" + p.getName() + " &7kont altındayken oyundan çıktı! &c(Ban uygulanmadı)"));
        }
        // Timeout görevi çalışmaya devam eder — geri gelirse kontrol edilir
    }

    // Kont itemine sağ tıklayınca menü aç
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKontItemUse(PlayerInteractEvent ev) {
        Player p = ev.getPlayer();
        if (!kontUntil.containsKey(p.getUniqueId())) return;
        // Barrier bloğa tıklama engeli — dupeleme önlemi
        if (ev.getClickedBlock() != null && ev.getClickedBlock().getType() == Material.BARRIER) {
            ev.setCancelled(true);
            return;
        }
        ItemStack item = p.getInventory().getItemInMainHand();
        if (!item.hasItemMeta()) return;
        if (!item.getItemMeta().getPersistentDataContainer().has(kontTag, PersistentDataType.STRING)) return;
        ev.setCancelled(true);
        if (ev.getAction() == Action.RIGHT_CLICK_AIR || ev.getAction() == Action.RIGHT_CLICK_BLOCK) {
            openKontMenu(p);
        }
    }

    // Kont item'i envanterde diğer slotlara taşıma engeli
    @EventHandler
    public void onKontInvClick(InventoryClickEvent ev) {
        if (!(ev.getWhoClicked() instanceof Player p)) return;
        if (!kontUntil.containsKey(p.getUniqueId())) return;
        // Kontrol item hareket ettirilemez, envanter kapatılamaz
        ItemStack current = ev.getCurrentItem();
        ItemStack cursor = ev.getCursor();
        boolean currentIsKont = current != null && current.hasItemMeta() &&
            current.getItemMeta().getPersistentDataContainer().has(kontTag, PersistentDataType.STRING);
        boolean cursorIsKont = cursor != null && cursor.hasItemMeta() &&
            cursor.getItemMeta().getPersistentDataContainer().has(kontTag, PersistentDataType.STRING);
        if (currentIsKont || cursorIsKont) ev.setCancelled(true);
    }

    private void openKontMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&#ff0000&l⚠ Hile Kontrol Menüsü ⚠"));
        // Kenarlık
        ItemStack fill = makeBorderGlass("&#330000");
        for (int i = 0; i < 27; i++) inv.setItem(i, fill);

        // Orta — başlık item (slot 4 = üst orta)
        ItemStack title = new ItemStack(Material.BARRIER);
        ItemMeta tm = title.getItemMeta();
        tm.setDisplayName(color("&#ff0000 Hile Kontrol"));
        tm.setLore(Arrays.asList(color("&#ffcc00 Hile kontrolündesin."), color("&#aaaaaa Aşağıdaki seçeneklerden birini kullan:")));
        title.setItemMeta(tm);
        inv.setItem(4, title);

        // Simetrik 3 buton — slot 11, 13, 15
        // /itiraf
        ItemStack itiraf = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta im = itiraf.getItemMeta();
        im.setDisplayName(color("&#ff6666 /itiraf"));
        im.setLore(Arrays.asList(color("&#aaaaaa İtirafını yetkiliye yaz")));
        itiraf.setItemMeta(im);
        inv.setItem(11, itiraf);

        // /anydesk
        ItemStack anydesk = new ItemStack(Material.COMPARATOR);
        ItemMeta am = anydesk.getItemMeta();
        am.setDisplayName(color("&#00ccff /anydesk"));
        am.setLore(Arrays.asList(color("&#aaaaaa AnyDesk kodunu gönder")));
        anydesk.setItemMeta(am);
        inv.setItem(13, anydesk);

        // /discord2
        ItemStack discord = new ItemStack(Material.JUKEBOX);
        ItemMeta dm = discord.getItemMeta();
        dm.setDisplayName(color("&#7289da /discord2"));
        dm.setLore(Arrays.asList(color("&#aaaaaa Discord nickinizi gönderin")));
        discord.setItemMeta(dm);
        inv.setItem(15, discord);

        p.openInventory(inv);
    }
}
