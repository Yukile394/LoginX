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
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

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
    // Kasa özel renk (kasarenkayarla ile ayarlanan, varsayılan CRATE_COLORS'ı ezer)
    private final HashMap<String, String[]>        crateColorOverride = new HashMap<>();
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
    private static final long PROTECTION_MS = 1_500_000L; // 25 dakika
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

    // ─── 1v1 Room (Ayarla) Sistemi — kalıcı, sürekli aktif, kilitlenmez (motor: "1v1 ROOM MOTORU" bölümü) ───
    private final HashMap<UUID, Location>      ayarlaPos1        = new HashMap<>();
    private final HashMap<UUID, Location>      ayarlaPos2        = new HashMap<>();
    private final HashMap<UUID, String>        playerAyarlaArena = new HashMap<>(); // yalnızca düellodaki (aktif/bitiş) oyuncular

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
    // Duel sırasında oyuncuların koyduğu bloklar: arenaId -> koyulan bloklar (duel bitince sessizce silinir)
    private final HashMap<String, java.util.Set<org.bukkit.block.Block>> duelPlacedBlocks = new HashMap<>();
    // ─── /duelspawnayarla ile ayarlanan özel duel/1v1 spawn noktası ───
    private Location duelOzelSpawn = null;
    // ─── Duel berabere teklifi: sadece dueldeyken kullanılabilir ───
    private final HashMap<UUID, UUID> duelDrawRequest = new HashMap<>(); // teklif alan -> teklif eden
    // Aynı arena hızlıca tekrar kullanılırsa eski duelin 5dk/10dk zamanlayıcıları erken tetiklenmesin diye
    // her duel'e (arenaId bazlı) benzersiz bir "maç numarası" veriyoruz — zamanlayıcı çalıştığında hâlâ
    // aynı maç mı diye bu numarayla kontrol ediyoruz.
    private final HashMap<String, Integer> duelMatchToken = new HashMap<>();
    private int duelMatchCounter = 0;

    // ─── Duel Sandığı (loot chest) — gerçek sandık değil, sağ tıkla açılan sahte GUI itemi ───
    private NamespacedKey lootChestTag;   // bu item bir duel sandığı mı (marker)
    private NamespacedKey lootChestData;  // içindeki eşyaların serileştirilmiş (Base64) hali
    private NamespacedKey lootChestOwner; // eşyaların sahibinin adı (lore/yeniden oluşturma için)
    private NamespacedKey lootChestId;    // sandığa özel benzersiz kimlik: kopyalar yığılmaz, her GUI oturumu tek bir sandığa bağlıdır
    private NamespacedKey lootChestVer;   // içerik sürümü: her alımda +1; eski sürümlü bir kopya açılamaz
    private final HashMap<UUID, UUID>    lootChestOpenBy   = new HashMap<>(); // sandık kimliği -> şu an GUI'sini açık tutan oyuncu
    private final HashMap<UUID, Integer> lootChestKnownVer = new HashMap<>(); // bu açılışta görülen en yeni sürüm (boşalan sandık = Integer.MAX_VALUE)
    private NamespacedKey duelGuiTag;     // duel menülerindeki butonların kimliği (arena:<id>, toggle:<anahtar>, confirm)

    // ─── Duel/1v1 Room ölüm sonrası komut kilidi: uid -> ölüm zamanı (ms) ───
    private final HashMap<UUID, Long> duelDeathTime = new HashMap<>();
    private static final long DUEL_DEATH_LOCK_MS       = 10_000L; // ölümden sonraki ilk 10 sn: hiçbir komut yok
    private static final long DUEL_DEATH_ECPV_WINDOW_MS = 60_000L; // sonraki 60 sn: sadece /ec ve /pv

    // ─── Günlük Ödül Sistemi ───
    private NamespacedKey dailyRewardDayTag; // menüdeki gün ikonunun hangi güne ait olduğunu taşır (tıklanabilir mi kontrolü)
    private static final int  DAILY_REWARD_TOTAL_DAYS   = 7;
    private static final long DAILY_REWARD_COOLDOWN_MS  = 72_000_000L; // 20 saat — bir sonraki güne geçiş bekleme süresi

    // ─── /kitkasası tek seferlik ödül sistemi ───
    private static final long KITKASASI_IP_CACHE_NONE = -1L;

    // ─── Kontrol (Kont) Sistemi ───
    private Location kontYeri = null;
    private final HashMap<UUID, Long>         kontUntil       = new HashMap<>();
    private final HashMap<UUID, UUID>         kontYetkilisi   = new HashMap<>();
    private final HashMap<UUID, Integer>      kontTimeoutTask = new HashMap<>();
    private final HashMap<UUID, ItemStack[]>  kontSavedInv    = new HashMap<>();
    private final HashMap<String, Location>   kontYetkiliYeri = new HashMap<>(); // yetkili adı → konum
    private final HashMap<UUID, UUID>         kontChatPartner = new HashMap<>(); // kont özel sohbeti: uid -> karşı taraf uid (çift yönlü)
    private NamespacedKey kontTag;

    // ─── /izinver sistemi ───
    private final Set<UUID> izinliOyuncular = new HashSet<>();

    // ─── LoginX Dinamik Komut İzni (LuckPerms bağlılığı) ───
    // /permkomutyetkiver <lpGrup> <komut> ile bir LuckPerms grubuna LoginX komut yetkisi verilir.
    // Arka planda "lp group <grup> permission set loginx.cmd.<komut> true" komutu konsoldan çalıştırılır
    // ve grup+komut eşleşmesi config.yml -> permkomutlar altında kalıcı olarak kaydedilir.
    private final Set<String> permKomutKayitlari = new HashSet<>(); // "grup:komut" formatında

    // ─── Login/Register şifre kayıt ───
    private final HashMap<String, String> oyuncuSifre = new HashMap<>(); // isim → şifre

    // ─── Spam / Küfür Sistemi ───
    private final HashMap<UUID, List<Long>>  spamMessages    = new HashMap<>();
    private final HashMap<UUID, Integer>     spamWarnCount   = new HashMap<>();
    private final HashMap<UUID, Long>        spamMuted       = new HashMap<>();
    // (küfür sistemi kaldırıldı — spamWarnCount/spamMuted üzerinden sadece spam koruması devam ediyor)
    // Küfür/spam uyarı sistemi renkleri (config.yml -> kufur.*) — kod içinde varsayılan değerler var
    private String kufurUyariRengi = "&#d6a45c";
    private String kufurMuteRengi  = "&#ff4444";
    private String[] silveraFlopColors = {"#0088ff","#22aaff","#55ccff","#88eeff","#aaf4ff","#ffffff","#aaf4ff"};
    private static final long SPAM_WINDOW_MS  = 3000L;  // 3 saniyede kaç mesaj
    private static final int  SPAM_THRESHOLD  = 3;       // bu kadar mesajda uyar
    private static final long MUTE_DURATION_MS = 600_000L; // 10 dakika
    // (küfür kelime listesi kaldırıldı — küfür kontrolü artık uygulanmıyor)

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
        Inventory inv; ItemStack reward; int tick; boolean done; String crateName; int spinDuration; float chance;
        CrateSession(Inventory inv, ItemStack reward, String crateName, int spinDuration, float chance) {
            this.inv = inv; this.reward = reward; this.tick = 0;
            this.done = false; this.crateName = crateName; this.spinDuration = spinDuration; this.chance = chance;
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

    /** Silvera markalı sohbet öneki: "Silvera" pembe, devamı beyaz — kalın değil, işaretsiz. */
    private String silveraPrefix() {
        return color("&#ff9ecbSilvera &f");
    }

    // ─── Arena Wand (Elytra yasaklı bölge) ───
    private final HashMap<UUID, Location> elyWandPos1 = new HashMap<>();
    private final List<ElytraZone>        elytraZones = new ArrayList<>();
    private final HashMap<UUID, Long>     elytraZoneWarnCooldown = new HashMap<>();
    private NamespacedKey arenaWandTag;

    // ─── Çift Zıplama ───
    private static final String DJ_TAG = "Çift Zıplama";
    private final List<ElytraZone>        jumpZones         = new ArrayList<>();
    private final HashMap<UUID, Location> jumpWandPos1      = new HashMap<>();
    private final HashMap<UUID, Location> jumpWandPos2      = new HashMap<>();
    private final Set<UUID>               doubleJumpEnabled = new HashSet<>();   // uçuş izni bu sistem tarafından açılanlar
    private final HashMap<UUID, Long>     doubleJumpReadyAt = new HashMap<>();   // bekleme bitiş zamanı (ms)
    private NamespacedKey doubleJumpWandTag;
    private NamespacedKey lootChestFillerTag;

    private static class ElytraZone {
        String world; BoundingBox box;
        ElytraZone(String world, BoundingBox box) { this.world = world; this.box = box; }
    }

    /** Citizens NPC'leri için "/npc attribute scale <deger>" ile çalışan boyut trait'i. Kalıcıdır. */
    @net.citizensnpcs.api.trait.TraitName("scale")
    public static class ScaleTrait extends net.citizensnpcs.api.trait.Trait {
        @net.citizensnpcs.api.persistence.Persist
        private double scale = 1.0D;

        public ScaleTrait() { super("scale"); }

        public void setScale(double newScale) {
            this.scale = newScale;
            apply();
        }

        public double getScale() { return scale; }

        private void apply() {
            if (npc.isSpawned() && npc.getEntity() instanceof LivingEntity le) {
                org.bukkit.attribute.AttributeInstance attr = le.getAttribute(org.bukkit.attribute.Attribute.GENERIC_SCALE);
                if (attr != null) attr.setBaseValue(scale);
            }
        }

        @Override
        public void onSpawn() { apply(); }
    }

    // ─────────────────────────────────────────────
    //  ENABLE / DISABLE
    // ─────────────────────────────────────────────
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            try {
                net.citizensnpcs.api.CitizensAPI.getTraitFactory()
                    .registerTrait(net.citizensnpcs.api.trait.TraitInfo.create(ScaleTrait.class));
            } catch (Exception ex) {
                getLogger().warning("Citizens 'scale' trait kaydedilemedi: " + ex.getMessage());
            }
        }
        keyTag = new NamespacedKey(this, "kasa_anahtari");
        cooldownSpeedTag = new NamespacedKey(this, "cooldown_hizi");
        kontTag = new NamespacedKey(this, "kont_item");
        lootChestTag = new NamespacedKey(this, "duel_sandigi");
        lootChestData = new NamespacedKey(this, "duel_sandigi_veri");
        lootChestOwner = new NamespacedKey(this, "duel_sandigi_sahibi");
        lootChestId = new NamespacedKey(this, "duel_sandigi_id");
        lootChestVer = new NamespacedKey(this, "duel_sandigi_surum");
        dailyRewardDayTag = new NamespacedKey(this, "gunluk_odul_gun");
        duelGuiTag = new NamespacedKey(this, "duel_menu");
        arenaWandTag = new NamespacedKey(this, "arena_wand");
        doubleJumpWandTag = new NamespacedKey(this, "ciftziplama_wand");
        lootChestFillerTag = new NamespacedKey(this, "duel_sandigi_dolgu");
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
        // Kasa özel renkleri loadData() içinde yükleniyor (crateColors)
        // Perm-komut kayıtlarını yükle (LuckPerms tarafında zaten kalıcı, bu sadece /permkomutlarilistele için)
        if (getConfig().contains("permkomutlar")) {
            for (String grup : getConfig().getConfigurationSection("permkomutlar").getKeys(false)) {
                for (String komut : getConfig().getConfigurationSection("permkomutlar." + grup).getKeys(false)) {
                    permKomutKayitlari.add(grup + ":" + komut);
                }
            }
        }
        // "aac" ve "denemeaac" LuckPerms rolleri kont komutlarını (kontal, kontcikar, kontdurdur)
        // kullanabilsin diye sunucu ilk açıldığında otomatik olarak izin veriliyor.
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            for (String grup : new String[]{"aac", "denemeaac"}) {
                for (String komut : new String[]{"kontal", "kontcikar", "kontdurdur"}) {
                    String permNode = "loginx.cmd." + komut;
                    if (!permKomutKayitlari.contains(grup + ":" + komut)) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp group " + grup + " permission set " + permNode + " true");
                        permKomutKayitlari.add(grup + ":" + komut);
                        getConfig().set("permkomutlar." + grup + "." + komut, true);
                    }
                }
            }
            saveConfig();
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

        initExtras();
        getLogger().info("Fear Craft LoginX V2.0 Aktif!");
    }

    @Override
    public void onDisable() {
        // Hologramları temizle
        removeAllHolograms();
        for (UUID id : new ArrayList<>(doubleJumpEnabled)) {
            Player dp = Bukkit.getPlayer(id);
            if (dp != null) { dp.setFlying(false); dp.setAllowFlight(false); }
        }
        doubleJumpEnabled.clear();
        shutdownExtras();
        saveData();
    }

    // ─────────────────────────────────────────────
    //  KOMUTLAR
    // ─────────────────────────────────────────────
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String cmdName = cmd.getName().toLowerCase();
        if (cmdName.startsWith("esyadusme")) return handleNoDropCommand(sender, cmdName, args);

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

        // ─── /ciftziplama — çift zıplama bölgesi (wand, olustur, saniye, sil) ───
        if (cmdName.equals("ciftziplama")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Bu komut yalnızca oyuncular tarafından kullanılabilir."); return true; }
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(uiErr(DJ_TAG, "Bu komutu kullanma yetkin yok.")); return true; }
            handleDoubleJumpCommand(p, args);
            return true;
        }

        // ─── Günlük Ödül — tüm oyuncular kullanabilir ───
        // "/günlük ödül" iki kelimeli bir komut olduğu için "günlük" ana komut, "ödül" argüman olarak işlenir.
        // plugin.yml içinde "günlük" komutunun tanımlı olması gerekir. Kolaylık olması için "gunlukodul"
        // tek kelimelik alternatifi de aynı menüyü açar.
        if (cmdName.equals("gunlukodul")
                || (cmdName.equals("günlük") && args.length > 0 && (args[0].equalsIgnoreCase("ödül") || args[0].equalsIgnoreCase("odul")))) {
            if (!(sender instanceof Player p)) return true;
            openDailyRewardMenu(p);
            return true;
        }

        // ─── /kitkasası — tek seferlik, IP korumalı ödül ───
        if (cmdName.equals("kitkasası") || cmdName.equals("kitkasasi")) {
            if (!(sender instanceof Player p)) return true;
            handleKitKasasiCommand(p);
            return true;
        }

        // /1v1randomsil (isim) — 1v1 Room arenasını sil
        if (cmdName.equals("1v1randomsil")) {
            if (!(sender instanceof Player p)) return true;
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(color("&cYetkisiz.")); return true; }
            if (args.length < 1) { p.sendMessage(roomErr("Kullanım: " + hl("/1v1randomsil <arenaId>"))); return true; }
            String id = args[0];
            if (!roomDelete(id)) { p.sendMessage(roomErr("Arena bulunamadı: " + hl(id))); return true; }
            p.sendMessage(roomMsg(hl(id) + " arenası silindi."));
            return true;
        }

        // ─────────────────────────────────────────────
        //  DUEL SİSTEMİ KOMUTLARİ
        // ─────────────────────────────────────────────

        // /duelolustur (id örn: #1)
        if (cmdName.equals("duelolustur")) {
            if (!(sender instanceof Player p)) return true;
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(duelErr("Bu komutu kullanma yetkin yok.")); return true; }
            if (args.length < 1) { p.sendMessage(duelErr("Kullanım: /duelolustur <id>")); return true; }
            String did = args[0];
            if (duelSpawn1.containsKey(did)) { p.sendMessage(duelErr("Bu ID zaten kullanılıyor: " + did + ". Önce /duelsil ile sil.")); return true; }
            duelSpawn1.put(did, null); duelSpawn2.put(did, null);
            getConfig().set("duels." + did + ".id", did); saveConfig();
            p.sendMessage(duel("Arena " + hl(did) + " oluşturuldu. Spawn noktaları için " + hl("/duelyeriekle1 " + did) + " ve " + hl("/duelyeriekle2 " + did) + " komutlarını kullan."));
            return true;
        }

        // /duelsil (id)
        if (cmdName.equals("duelsil")) {
            if (!(sender instanceof Player p)) return true;
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(duelErr("Bu komutu kullanma yetkin yok.")); return true; }
            if (args.length < 1) { p.sendMessage(duelErr("Kullanım: /duelsil <id>")); return true; }
            String did = args[0];
            duelSpawn1.remove(did); duelSpawn2.remove(did);
            UUID[] dp = duelPlayers.remove(did);
            duelArenaRules.remove(did);
            if (dp != null) for (UUID u : dp) playerDuel.remove(u);
            getConfig().set("duels." + did, null); saveConfig();
            p.sendMessage(duel("Arena " + hl(did) + " silindi."));
            return true;
        }

        // /duelyeriekle1 (duelid)
        if (cmdName.equals("duelyeriekle1")) {
            if (!(sender instanceof Player p)) return true;
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(duelErr("Bu komutu kullanma yetkin yok.")); return true; }
            if (args.length < 1) { p.sendMessage(duelErr("Kullanım: /duelyeriekle1 <id>")); return true; }
            String did = args[0];
            if (!duelSpawn1.containsKey(did)) { p.sendMessage(duelErr("Arena bulunamadı. Önce /duelolustur " + did + " komutunu kullan.")); return true; }
            duelSpawn1.put(did, p.getLocation().clone());
            getConfig().set("duels." + did + ".spawn1", p.getLocation()); saveConfig();
            p.sendMessage(duel("Arena " + hl(did) + " için spawn 1 ayarlandı: " + hl(p.getLocation().getBlockX() + ", " + p.getLocation().getBlockY() + ", " + p.getLocation().getBlockZ())));
            return true;
        }

        // /duelyeriekle2 (duelid)
        if (cmdName.equals("duelyeriekle2")) {
            if (!(sender instanceof Player p)) return true;
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(duelErr("Bu komutu kullanma yetkin yok.")); return true; }
            if (args.length < 1) { p.sendMessage(duelErr("Kullanım: /duelyeriekle2 <id>")); return true; }
            String did = args[0];
            if (!duelSpawn2.containsKey(did)) { p.sendMessage(duelErr("Arena bulunamadı. Önce /duelolustur " + did + " komutunu kullan.")); return true; }
            duelSpawn2.put(did, p.getLocation().clone());
            getConfig().set("duels." + did + ".spawn2", p.getLocation()); saveConfig();
            p.sendMessage(duel("Arena " + hl(did) + " için spawn 2 ayarlandı: " + hl(p.getLocation().getBlockX() + ", " + p.getLocation().getBlockY() + ", " + p.getLocation().getBlockZ())));
            return true;
        }

        // /dueller — duel arenalarını listele / gir
        if (cmdName.equals("dueller")) {
            if (!(sender instanceof Player p)) return true;
            openDuelListMenu(p); return true;
        }

        // /duelberaberekabul — berabere teklifini kabul et → ikisi de spawna ışınlanır
        if (cmdName.equals("duelberaberekabul")) {
            if (!(sender instanceof Player p)) return true;
            UUID requesterUid = duelDrawRequest.remove(p.getUniqueId());
            if (requesterUid == null) { p.sendMessage(duelErr("Bekleyen bir berabere teklifin yok.")); return true; }
            String arenaId = playerDuel.get(p.getUniqueId());
            Player requester = Bukkit.getPlayer(requesterUid);
            if (arenaId == null || requester == null || !requester.isOnline()
                || !arenaId.equals(playerDuel.get(requesterUid))) {
                p.sendMessage(duelErr("Duel artık geçerli değil.")); return true;
            }
            UUID[] dp = duelPlayers.remove(arenaId);
            duelArenaRules.remove(arenaId);
            duelMatchToken.remove(arenaId);
            if (dp != null) for (UUID u : dp) { playerDuel.remove(u); duelFrozen.remove(u); }

            for (Player dpX : new Player[]{p, requester}) {
                if (dpX != null && dpX.isOnline()) {
                    dpX.sendTitle(
                        duelFlop("Berabere", true),
                        color(UI_SUB + "Spawn noktasına ışınlanıyorsun"),
                        5, 60, 15);
                    dpX.sendMessage(duel("Duel berabere sonuçlandı."));
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
            if (requesterUid == null) { p.sendMessage(duelErr("Bekleyen bir berabere teklifin yok.")); return true; }
            Player requester = Bukkit.getPlayer(requesterUid);
            p.sendMessage(duel("Berabere teklifini reddettin."));
            if (requester != null && requester.isOnline())
                requester.sendMessage(duel(hl(p.getName()) + " berabere teklifini reddetti."));
            return true;
        }

        // /permkomutyetkiver <lpGrup> <komut> — LuckPerms grubuna LoginX komutu kullanma izni verir
        if (cmdName.equals("permkomutyetkiver")) {
            if (!sender.hasPermission("loginx.admin") && !sender.isOp()) { sender.sendMessage(color("&cYetkisiz.")); return true; }
            if (args.length < 2) { sender.sendMessage(color("&cKullanim: /permkomutyetkiver <lpGrup> <komut>")); return true; }
            String grup = args[0].toLowerCase();
            String komut = args[1].toLowerCase();
            String permNode = "loginx.cmd." + komut;

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp group " + grup + " permission set " + permNode + " true");

            permKomutKayitlari.add(grup + ":" + komut);
            getConfig().set("permkomutlar." + grup + "." + komut, true);
            saveConfig();

            sender.sendMessage(color("&#00ff88&l[PERM] &f&e" + grup + " &7grubuna &e/" + komut + " &7komutu için izin verildi. &8(LuckPerms üzerinden)"));
            return true;
        }

        // /permkomutsil <lpGrup> <komut> — LuckPerms grubundan LoginX komut iznini kaldırır
        if (cmdName.equals("permkomutsil")) {
            if (!sender.hasPermission("loginx.admin") && !sender.isOp()) { sender.sendMessage(color("&cYetkisiz.")); return true; }
            if (args.length < 2) { sender.sendMessage(color("&cKullanim: /permkomutsil <lpGrup> <komut>")); return true; }
            String grup = args[0].toLowerCase();
            String komut = args[1].toLowerCase();
            String permNode = "loginx.cmd." + komut;

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp group " + grup + " permission unset " + permNode);

            permKomutKayitlari.remove(grup + ":" + komut);
            getConfig().set("permkomutlar." + grup + "." + komut, null);
            saveConfig();

            sender.sendMessage(color("&#ff4444&l[PERM] &f&e" + grup + " &7grubunun &e/" + komut + " &7komutu izni &ckaldırıldı&7. &8(LuckPerms üzerinden)"));
            return true;
        }

        // /duelspawnayarla — durduğun bloğu duel/1v1 bitince ışınlanılacak özel spawn yap
        if (cmdName.equals("duelspawnayarla")) {
            if (!(sender instanceof Player p)) return true;
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(duelErr("Bu komutu kullanma yetkin yok.")); return true; }
            duelOzelSpawn = p.getLocation().clone();
            getConfig().set("duelozelspawn", duelOzelSpawn); saveConfig();
            p.sendMessage(duel("Duel ve 1v1 sonrası ışınlanılacak nokta ayarlandı: " + hl(duelOzelSpawn.getBlockX() + ", " + duelOzelSpawn.getBlockY() + ", " + duelOzelSpawn.getBlockZ())));
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
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(duelErr("Bu komutu kullanma yetkin yok.")); return true; }
            openDuelSettingsMenu(p, true); return true;
        }

        // /1v1 (oyuncu) — duel daveti gönder (o anki kayıtlı kişisel ayarlarla)
        if (cmdName.equals("1v1")) {
            if (!(sender instanceof Player p)) return true;
            if (args.length < 1) { p.sendMessage(duelErr("Kullanım: /1v1 <oyuncu>")); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            sendDuelInvite(p, target, args.length > 0 ? args[0] : "");
            return true;
        }

        // /duel (oyuncu) — davet göndermeden önce ayar ekranını açar
        if (cmdName.equals("duel")) {
            if (!(sender instanceof Player p)) return true;
            if (args.length < 1) { p.sendMessage(duelErr("Kullanım: /duel <oyuncu>")); return true; }
            if (args[0].equalsIgnoreCase("berabere")) { handleDuelBerabereCommand(p); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || target.equals(p)) { p.sendMessage(duelErr(args[0] + " çevrimiçi değil.")); return true; }
            if (playerDuel.containsKey(p.getUniqueId())) { p.sendMessage(duelErr("Zaten bir dueldasın.")); return true; }
            if (playerDuel.containsKey(target.getUniqueId())) { p.sendMessage(duelErr(target.getName() + " zaten bir duelda.")); return true; }
            duelPendingTarget.put(p.getUniqueId(), target.getUniqueId());
            openDuelInviteMenu(p, target);
            return true;
        }

        // /1v1kabul
        if (cmdName.equals("1v1kabul")) {
            if (!(sender instanceof Player p)) return true;
            UUID inviterUid = duelInvite.get(p.getUniqueId());
            if (inviterUid == null) { p.sendMessage(duelErr("Bekleyen duel davetin yok.")); return true; }
            Player inviter = Bukkit.getPlayer(inviterUid);
            if (inviter == null) { p.sendMessage(duelErr("Davet eden oyuncu çevrimdışı.")); duelInvite.remove(p.getUniqueId()); return true; }
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
                p.sendMessage(duelErr("Bu duelde Elytra kapalı. " + culprit + " envanterindeki Elytra'yı çıkarmadan duel başlayamaz."));
                inviter.sendMessage(duelErr("Bu duelde Elytra kapalı. " + culprit + " envanterindeki Elytra'yı çıkarmadan duel başlayamaz."));
                return true;
            }
            duelInvite.remove(p.getUniqueId());
            duelInviteWeb.remove(p.getUniqueId()); duelInviteEly.remove(p.getUniqueId());
            duelInviteLadder.remove(p.getUniqueId()); duelInviteOak.remove(p.getUniqueId());
            duelInviteStick.remove(p.getUniqueId()); duelInviteChest.remove(p.getUniqueId());
            String freeArena = findFreeDuelArena();
            if (freeArena == null) { p.sendMessage(duelErr("Şu an boş arena yok.")); return true; }
            startDuel(inviter, p, freeArena, rules);
            return true;
        }

        // /1v1reddet
        if (cmdName.equals("1v1reddet")) {
            if (!(sender instanceof Player p)) return true;
            UUID inviterUid = duelInvite.remove(p.getUniqueId());
            if (inviterUid == null) { p.sendMessage(duelErr("Bekleyen duel davetin yok.")); return true; }
            cancelDuelInviteTimeout(p.getUniqueId());
            duelInviteWeb.remove(p.getUniqueId()); duelInviteEly.remove(p.getUniqueId());
            duelInviteLadder.remove(p.getUniqueId()); duelInviteOak.remove(p.getUniqueId());
            duelInviteStick.remove(p.getUniqueId()); duelInviteChest.remove(p.getUniqueId());
            Player inviter = Bukkit.getPlayer(inviterUid);
            p.sendMessage(duel("Daveti reddettin."));
            if (inviter != null) inviter.sendMessage(duel(hl(p.getName()) + " duel davetini reddetti."));
            return true;
        }

        // 1v1 wand
        if (cmdName.equals("1v1wand")) {
            if (!(sender instanceof Player p)) return true;
            ItemStack wand = new ItemStack(Material.GOLDEN_AXE);
            ItemMeta wm = wand.getItemMeta();
            wm.setDisplayName(color("&#ff6600&l&r&#ffcc00&l1v1 Wand &r&#ff6600&l"));
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
            wm.setDisplayName(color("&#ff4400&l&r&#ffaa00&l1v1 Ayarla &r&#ff4400&l"));
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
            if (!(sender instanceof Player p)) { sender.sendMessage("Bu komut yalnızca oyuncular tarafından kullanılabilir."); return true; }
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(uiErr("Bakım", "Bu komutu kullanma yetkin yok.")); return true; }
            bakimModu = true;
            getConfig().set("bakim.aktif", true); saveConfig();
            // Op olmayan ve izinli olmayan herkesi at
            int atilan = 0;
            for (Player on : new ArrayList<>(Bukkit.getOnlinePlayers())) {
                if (on.isOp() || on.hasPermission("loginx.admin") || bakimIzinli.contains(on.getUniqueId())) continue;
                on.kickPlayer(maintenanceKickMessage());
                atilan++;
            }
            for (Player on : Bukkit.getOnlinePlayers())
                if (on.hasPermission("loginx.admin"))
                    { on.sendMessage(uiMsg("Bakım", "Bakım modu açıldı. " + hl(String.valueOf(atilan)) + " oyuncu sunucudan çıkarıldı.")); on.playSound(on.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f); }
            return true;
        }

        // /bakimdancikar — bakım modunu kapat
        if (cmdName.equals("bakimdancikar")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Bu komut yalnızca oyuncular tarafından kullanılabilir."); return true; }
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(uiErr("Bakım", "Bu komutu kullanma yetkin yok.")); return true; }
            bakimModu = false;
            bakimIzinli.clear();
            getConfig().set("bakim.aktif", false); saveConfig();
            p.sendMessage(uiMsg("Bakım", "Bakım modu kapatıldı. Sunucu herkese açık."));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
            for (Player on : Bukkit.getOnlinePlayers())
                if (on.hasPermission("loginx.admin") && !on.equals(p))
                    { on.sendMessage(uiMsg("Bakım", hl(p.getName()) + " bakım modunu kapattı.")); on.playSound(on.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f); }
            return true;
        }

        // /bakimaoyuncual <isim> — bakımdayken belirli oyuncunun girmesine izin ver
        if (cmdName.equals("bakimaoyuncual")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Bu komut yalnızca oyuncular tarafından kullanılabilir."); return true; }
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(uiErr("Bakım", "Bu komutu kullanma yetkin yok.")); return true; }
            if (args.length < 1) { p.sendMessage(uiErr("Bakım", "Kullanım: /bakimaoyuncual <oyuncu>")); return true; }
            // Çevrimiçiyse UUID al, değilse OfflinePlayer
            Player target = Bukkit.getPlayer(args[0]);
            UUID targetUid = target != null ? target.getUniqueId() : Bukkit.getOfflinePlayer(args[0]).getUniqueId();
            bakimIzinli.add(targetUid);
            p.sendMessage(uiMsg("Bakım", hl(args[0]) + " bakım sırasında sunucuya girebilir."));
            if (target != null) target.sendMessage(uiMsg("Bakım", "Bakım sırasında sunucuya girmene izin verildi."));
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
        // Önce yetkilinin KENDİSİNE ÖZEL /kontyeryetkili ile ayarladığı yere bakılır,
        // o yoksa genel /kontyeriayarla yerine ışınlanır. Böylece /kontyeri ve /kontal
        // aynı sonucu (yetkilinin kendi yerini) verir, birbirini ezmez.
        if (cmdName.equals("kontyeri")) {
            if (!(sender instanceof Player p)) return true;
            if (!p.hasPermission("loginx.admin")) { p.sendMessage(color("&cYetkisiz.")); return true; }
            Location kendiYer = kontYetkiliYeri.get(p.getName());
            if (kendiYer != null) {
                hudTp(p, kendiYer);
                p.sendMessage(color("&#00ff88&lKont &8» &fKendi kont yerine ışınlandın."));
                return true;
            }
            if (kontYeri == null) { p.sendMessage(color("&#ff4444&lKont &8» &cKont yeri ayarlanmamış! &e/kontyeriayarla &cveya &e/kontyeryetkili ayarla <isim> &cyaz.")); return true; }
            hudTp(p, kontYeri);
            p.sendMessage(color("&#00ff88&lKont &8» &fGenel kont yerine ışınlandın."));
            return true;
        }

        // /kontal (oyuncu) (süre örnek: 7gün / 30dk / 2saat)  — /kontalirken de aynı komutun takma adı
        if (cmdName.equals("kontal") || cmdName.equals("kontalirken")) {
            if (!(sender instanceof Player admin)) return true;
            if (!canUseLoginXCommand(admin, "kontal")) { admin.sendMessage(color("&cYetkisiz.")); return true; }
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
            hudTp(target, hedefLoc);

            // Yetkili de aynı anda aynı yere ışınlansın (ikisi birlikte kont yerinde olsun)
            hudTp(admin, hedefLoc);

            // Freeze — walkSpeed DEĞİL, event ile dondurulur
            kontUntil.put(uid, System.currentTimeMillis() + sureMs); // yeniden set

            // Title ve ses
            target.sendTitle(
                color("&#ff0000&lHİLE KONTROLE ALINDI!"),
                color("&#ffcc00/itiraf &f&7, &e/anydesk &7veya &b/discord2 &7yaz"),
                10, 120, 30);
            target.playSound(target.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.5f);
            target.playSound(target.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.2f);
            target.sendMessage(color("&#ff0000&l&r&#ffcc00Hile Kontrole Alındın! &r&#aaaaaa&o/itiraf, /anydesk veya /discord2 komutlarından birini kullan."));

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

            // Kont özel sohbeti başlat — kont süresince ikisinin chat mesajları sadece birbirlerine görünür
            kontChatPartner.put(admin.getUniqueId(), uid);
            kontChatPartner.put(uid, admin.getUniqueId());
            admin.sendMessage(color("&#00ff88&l[Hile Kontrol Sohbet] &7açıldı. Chate yazdığın her şey sadece " + target.getName() + " tarafından görünür."));
            target.sendMessage(color("&#00ff88&l[Hile Kontrol Sohbet] &7açıldı. Chate yazdığın her şey sadece " + admin.getName() + " tarafından görünür."));

            admin.sendMessage(color("&#00ff88&l[KONT] &r&f" + target.getName() + " &7kont alındı. Süre: &e" + args[1]));
            return true;
        }

        // /kontcikar (oyuncu) — eşyaları geri ver, serbest bırak
        if (cmdName.equals("kontcikar")) {
            if (!(sender instanceof Player admin)) return true;
            if (!canUseLoginXCommand(admin, "kontcikar")) { admin.sendMessage(color("&cYetkisiz.")); return true; }
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
            admin.sendMessage(uiMsg("Kont", hl(args[0]) + " kontten çıkarıldı, eşyaları iade edildi."));
            sendReleasedPlayerToHub(uid);
            return true;
        }

        // /kontdurdur — 3 dakika boyunca dondurul (kont süresince)
        if (cmdName.equals("kontdurdur")) {
            if (!(sender instanceof Player admin)) return true;
            if (!canUseLoginXCommand(admin, "kontdurdur")) { admin.sendMessage(color("&cYetkisiz.")); return true; }
            if (args.length < 1) { admin.sendMessage(color("&cKullanim: /kontdurdur <oyuncu>")); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { admin.sendMessage(color("&cOyuncu bulunamadı.")); return true; }
            if (!kontUntil.containsKey(target.getUniqueId())) {
                admin.sendMessage(color("&cBu oyuncu kont altında değil.")); return true;
            }
            // Zaten kont başlayınca zaten dondurulmuş — kont yerine ışınla ve yeniden kilitle
            hudTp(target, kontYeri != null ? kontYeri : target.getLocation());
            target.setWalkSpeed(0f);
            target.setFlySpeed(0f);
            target.sendTitle(color("&#ff0000&lDURDURULDUN!"), color("&#ffcc00Kaçmaya çalışma!"), 5, 60, 10);
            target.sendMessage(color("&#ff0000&l&fHile kontrolünden kaçamazsın!"));
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

        // /kontyeriayarlayetkili — kendi konumunu (bakış açın dahil) kişisel kont yerin olarak ayarla
        if (cmdName.equals("kontyeriayarlayetkili")) {
            if (!(sender instanceof Player admin)) return true;
            if (!admin.hasPermission("loginx.admin") && !admin.hasPermission("loginx.kont.control") && !admin.isOp()) {
                admin.sendMessage(color("&cYetkisiz.")); return true;
            }
            Location loc = admin.getLocation().clone(); // yaw/pitch (bakış açısı) dahil
            kontYetkiliYeri.put(admin.getName(), loc);
            getConfig().set("kontyetkili." + admin.getName(), loc);
            saveConfig();
            admin.sendMessage(color("&#00ff88&l[KONT] &fKişisel kont yerin, bakış açınla birlikte bu konuma ayarlandı: &b"
                + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()));
            return true;
        }

        // /kontyeryetkili ayarla <isim>   ya da   /kontyeryetkili sil <isim>
        // Ayrıca eski tek-kelime komutları da destekle (plugin.yml uyumluluğu için)
        if (cmdName.equals("kontyeryetkili") || cmdName.equals("kontyeryetkiliayarla") || cmdName.equals("kontyeryetkilisil")
                || cmdName.equals("kontyeriyetkili") || cmdName.equals("kontyeriyetkiliayarla") || cmdName.equals("kontyeriyetkilisil")) {
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

            case "loginx" -> {
                if (args.length < 1 || !args[0].equalsIgnoreCase("reload")) {
                    p.sendMessage(color("&cKullanim: /loginx reload"));
                    return true;
                }
                reloadConfig(); loadData();
                p.sendMessage(color("&aLoginX yenilendi!"));
            }
            case "arenawand" -> {
                ItemStack wand = createArenaWand();
                p.getInventory().addItem(wand);
                p.sendMessage(silveraPrefix() + "Arena Wand envanterine verildi.");
                p.sendMessage(color("&#9aa5b5&fSağ tık &#9aa5b5= 1. nokta &7 | &fSol tık &#9aa5b5= 2. nokta &7(bölgeyi tamamlar)"));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.6f);
            }
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
                p.sendMessage(color("&#ffcc00&l  " + capitalize(ad) + " Kasası İçeriği &8(" + pool.size() + " eşya)"));
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
                p.sendMessage(color("&#00ff88&l  Şans Güncellendi!"));
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
                            color(clr[0] + "&l" + keyName.toUpperCase()),
                            color("&#ffffff&l» &r&#dddddd" + capitalize(kasaId) + " Kasası Anahtarı &r&#00ff88Envantere Eklendi &r&#ffffff«"),
                            5, 70, 15);
                        on.playSound(on.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.3f);
                        cnt++;
                    }
                    p.sendMessage(color("&#00ff88&l&r&f" + cnt + " oyuncuya &e" + keyName + " &fverildi."));
                    // Sunucu geneli EVENT mesajı
                    for (Player on : Bukkit.getOnlinePlayers()) {
                        on.sendMessage(color(""));
                        on.sendMessage(color("  &#ff6600&l&r&#ffcc00&lEVENT &r&#ff6600&l"));
                        on.sendMessage(color("  &#ffffff&lTüm oyunculara &r" + clr[0] + "&l" + keyName + " &r&#ffffff&lverildi!"));
                        on.sendMessage(color("  &#aaaaaa&oKasanı aç, şansını dene!"));
                        on.sendMessage(color(""));
                    }
                } else {
                    Player tgt = Bukkit.getPlayer(args[0]);
                    if (tgt == null) { p.sendMessage(color("&cOyuncu bulunamadi: " + args[0])); return true; }
                    tgt.getInventory().addItem(key.clone());
                    tgt.sendTitle(
                        color(clr[0] + "&l" + keyName.toUpperCase()),
                        color("&#ffffff&l» &r&#dddddd" + capitalize(kasaId) + " Kasası Anahtarı &r&#00ff88Envantere Eklendi &r&#ffffff«"),
                        5, 70, 15);
                    tgt.playSound(tgt.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.3f);
                    p.sendMessage(color("&#00ff88&l&r&f" + tgt.getName() + " &7oyuncusuna &e" + keyName + " &fverildi."));
                    // Sunucu geneli EVENT mesajı (kişiye özel)
                    for (Player on : Bukkit.getOnlinePlayers()) {
                        on.sendMessage(color(""));
                        on.sendMessage(color("  &#ff6600&l&r&#ffcc00&lEVENT &r&#ff6600&l"));
                        on.sendMessage(color("  &#ffcc00&l" + tgt.getName() + " &r&#ffffff&l" + clr[0] + "&l" + keyName + " &r&#ffffff&lkazandı!"));
                        on.sendMessage(color(""));
                    }
                }
            }

            case "kasarenkayarla" -> {
                if (args.length < 2) {
                    p.sendMessage(color("&cKullanim: /kasarenkayarla <kasaismi> <renk1,renk2,renk3...>"));
                    p.sendMessage(color("&7Örnek: &f/kasarenkayarla gear ff0000,00ff00,0000ff"));
                    p.sendMessage(color("&7Örnek (rgb): &f/kasarenkayarla gear rgb(255,0,0),rgb(0,255,0)"));
                    return true;
                }
                String ad = args[0].toLowerCase();
                if (!crates.containsKey(ad)) { p.sendMessage(color("&cKasa bulunamadi: " + ad)); return true; }
                String renkArg = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).replace(" ", "");
                String[] parsed = parseColorList(renkArg);
                if (parsed == null) {
                    p.sendMessage(color("&cGecersiz renk kodu. Ornek: ff0000,00ff00 veya rgb(255,0,0)"));
                    return true;
                }
                crateColorOverride.put(ad, parsed);
                // Config'e kaydet
                getConfig().set("crateColors." + ad, String.join(",", parsed).replace("&", ""));
                saveConfig();
                spawnCrateHologram(ad); // hologram yeni renkle yenilensin
                p.sendMessage(color("&#00ff88&l&r&f" + capitalize(ad) + " &7kasasının rengi güncellendi. &8(" + parsed.length + " renk)"));
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

        removeStrayCrateHolograms(loc);

        List<Integer> ids = new ArrayList<>();
        String nameLine = buildHologramLine(crateName);

        spawnTextStand(loc, 1.6, nameLine, ids);

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

    /**
     * Eski sürümden dünyada kalmış hologram armor stand'larını (sağ/sol tık satırları ve yinelenen başlıklar) siler.
     * Yalnızca kasanın hemen üstündeki, görünmez, marker ve adı "Kasası" / "sağ tıkla" / "sol tıkla" içeren stand'lara bakar.
     */
    private void removeStrayCrateHolograms(Location crate) {
        if (crate.getWorld() == null) return;
        Location center = crate.clone().add(0.5, 1.3, 0.5);
        for (Entity en : crate.getWorld().getNearbyEntities(center, 1.0, 1.6, 1.0)) {
            if (!(en instanceof ArmorStand as) || as.isVisible() || !as.isMarker() || as.getCustomName() == null) continue;
            String n = ChatColor.stripColor(as.getCustomName());
            if (n == null) continue;
            if (n.contains("Kasası") || n.contains("sağ tıkla") || n.contains("sol tıkla")) as.remove();
        }
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
        stand.setPersistent(false); // restart sonrası dünyada kalmasın
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
            p.sendTitle(color(UI_BAD + "Anahtar Gerekli"), color(UI_SUB + "Bu kasayı açmak için anahtar gerekiyor."), 5, 50, 10);
            return;
        }

        String keyType = inHand.getItemMeta().getPersistentDataContainer().get(keyTag, PersistentDataType.STRING);
        if (!keyType.equalsIgnoreCase(activeCrate) && !keyType.equalsIgnoreCase("all")) {
            long now = System.currentTimeMillis(); UUID uid = p.getUniqueId();
            if (wrongKeyCooldown.getOrDefault(uid, 0L) > now) return;
            wrongKeyCooldown.put(uid, now + WRONG_KEY_COOLDOWN_MS);
            p.setVelocity(p.getLocation().getDirection().multiply(-1.3).setY(0.35));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            p.sendTitle(color(UI_WARN + "Yanlış Anahtar"), color(UI_SUB + "Bu anahtar bu kasayı açamaz."), 5, 40, 10);
            return;
        }

        List<ItemStack> pool = crateItems.getOrDefault(activeCrate, new ArrayList<>());
        if (pool.isEmpty()) { p.sendMessage(color(UI_BAD + "Bu kasa henüz boş.")); return; }

        int rewardIdx = pickRewardIndex(activeCrate, pool);
        inHand.setAmount(inHand.getAmount() - 1);
        openSpinMenu(p, activeCrate, pool, pool.get(rewardIdx).clone(), crateChanceOf(activeCrate, rewardIdx));
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
    //  ARENA WAND (Elytra yasaklı bölge işaretleyici)
    // ─────────────────────────────────────────────
    /** /arenawand ile verilen, loreli özel balta itemini oluşturur. */
    private ItemStack createArenaWand() {
        ItemStack axe = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta im = axe.getItemMeta();
        im.setDisplayName(color("&#ff9ecbSilvera &fArena Wand"));
        List<String> lore = new ArrayList<>();
        lore.add(color("&#4a4a5a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        lore.add(color("&#ff9ecbElytra yasaklı arena bölgesi"));
        lore.add(color("&#ff9ecboluşturmak için kullanılır."));
        lore.add(color(""));
        lore.add(color("&#6fa8dc Sağ Tık &#5a5a6a» &#c7d3e01. nokta seç"));
        lore.add(color("&#6fa8dc Sol Tık  &#5a5a6a» &#c7d3e02. nokta seç ve bölgeyi tamamla"));
        lore.add(color(""));
        lore.add(color("&#9aa5b5Bu bölgede oyuncular elytra açamaz"));
        lore.add(color("&#4a4a5a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        im.setLore(lore);
        im.getPersistentDataContainer().set(arenaWandTag, PersistentDataType.BYTE, (byte) 1);
        axe.setItemMeta(im);
        return axe;
    }

    /** Elinde tuttuğu item bir Arena Wand mı? */
    private boolean isArenaWand(ItemStack item) {
        if (item == null || item.getType() != Material.GOLDEN_AXE || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(arenaWandTag, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onArenaWandUse(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = p.getInventory().getItemInMainHand();
        if (!isArenaWand(item)) return;
        if (e.getClickedBlock() == null) return;
        Action act = e.getAction();
        if (act != Action.RIGHT_CLICK_BLOCK && act != Action.LEFT_CLICK_BLOCK) return;
        e.setCancelled(true);
        Location clicked = e.getClickedBlock().getLocation();

        if (act == Action.RIGHT_CLICK_BLOCK) {
            elyWandPos1.put(p.getUniqueId(), clicked);
            p.sendMessage(silveraPrefix() + "1. nokta seçildi: &#8a8a8a"
                + clicked.getBlockX() + ", " + clicked.getBlockY() + ", " + clicked.getBlockZ());
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        } else {
            Location pos1 = elyWandPos1.get(p.getUniqueId());
            if (pos1 == null || pos1.getWorld() == null || !pos1.getWorld().equals(clicked.getWorld())) {
                p.sendMessage(color("&#ff4444[ARENA] &fÖnce sağ tıkla ile 1. noktayı seç!"));
                return;
            }
            BoundingBox box = BoundingBox.of(pos1, clicked);
            elytraZones.add(new ElytraZone(clicked.getWorld().getName(), box));
            saveArenaZones();
            saveConfig();
            elyWandPos1.remove(p.getUniqueId());
            p.sendMessage(silveraPrefix() + "Bölge oluşturuldu! &#8a8a8a(" + elytraZones.size() + ". bölge)");
            p.sendMessage(color("  &#8a8a8aAlan: &f"
                + (Math.abs(pos1.getBlockX() - clicked.getBlockX()) + 1) + "x"
                + (Math.abs(pos1.getBlockY() - clicked.getBlockY()) + 1) + "x"
                + (Math.abs(pos1.getBlockZ() - clicked.getBlockZ()) + 1)
                + " &#8a8a8a— bu alanda elytra kullanılamaz."));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  1v1 ROOM MOTORU
    //  - Odalar config'e kaydedilir (restart sonrası kaybolmaz)
    //  - Tek bir zamanlayıcı tüm odaları yönetir (giriş/çıkış/başlangıç/bitiş)
    //  - Durum makinesi: BEKLEME -> AKTİF -> BİTİŞ -> BEKLEME (her zaman geri döner)
    //  - Her durumda odada yalnızca 2 oyuncu kalır
    // ═════════════════════════════════════════════════════════════════════
    private static final int ROOM_WAIT = 0, ROOM_ACTIVE = 1, ROOM_ENDING = 2;

    private static class Room {
        final String id, world;
        final int minX, minY, minZ, maxX, maxY, maxZ;
        final BoundingBox box;
        int state = ROOM_WAIT;
        final LinkedHashSet<UUID> inside = new LinkedHashSet<>();
        final HashMap<UUID, Integer> outTicks = new HashMap<>();
        final List<Location> glass = new ArrayList<>();
        UUID a, b, winner;
        long startedAt, endAt;
        int lastSec = -1;

        Room(String id, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
            this.id = id; this.world = world;
            minX = Math.min(x1, x2); maxX = Math.max(x1, x2);
            minY = Math.min(y1, y2); maxY = Math.max(y1, y2);
            minZ = Math.min(z1, z2); maxZ = Math.max(z1, z2);
            box = new BoundingBox(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        }
        UUID opponentOf(UUID u) { if (u == null) return null; if (u.equals(a)) return b; if (u.equals(b)) return a; return null; }
        boolean isDueler(UUID u) { return u != null && (u.equals(a) || u.equals(b)); }
    }

    private final LinkedHashMap<String, Room> rooms = new LinkedHashMap<>();
    private final HashMap<UUID, Long> roomIgnoreUntil = new HashMap<>(); // lobiye yollanan oyuncu kısa süre odaya sayılmaz
    private final HashMap<UUID, Long> roomMsgCd = new HashMap<>();       // giriş/çıkış mesajı tekrarını engeller
    private final HashMap<UUID, Long> roomKickCd = new HashMap<>();
    private final HashSet<UUID> hubOnRespawn = new HashSet<>();
    private java.io.File roomGlassFile;

    // ── Mesaj / ses yardımcıları ──
    private String roomMsg(String body) { return uiMsg("1v1 Odası", body); }
    private String roomErr(String body) { return uiErr("1v1 Odası", body); }
    private void roomTitle(Player p, String t, String s, int in, int stay, int out) {
        if (p != null && p.isOnline()) p.sendTitle(color(t), color(s), in, stay, out);
    }
    private void roomActionBar(Player p, String text) {
        if (p == null || !p.isOnline()) return;
        try {
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(color(text)));
        } catch (Throwable ignored) {}
    }
    private void roomSound(Player p, Sound s, float vol, float pitch) {
        if (p != null && p.isOnline()) p.playSound(p.getLocation(), s, vol, pitch);
    }
    private boolean roomCooldownOk(UUID u, long ms) {
        long now = System.currentTimeMillis();
        Long last = roomMsgCd.get(u);
        if (last != null && now - last < ms) return false;
        roomMsgCd.put(u, now);
        return true;
    }
    private long roomEndWaitMs() { return Math.max(1, getConfig().getInt("oda.bitis-bekleme", 5)) * 1000L; }

    private void roomEnterNotice(Player p) {
        if (!roomCooldownOk(p.getUniqueId(), 1500L)) return;
        p.sendMessage(roomMsg("1v1 odasına girdin. Rakip bekleniyor."));
        roomTitle(p, UI_TEXT + "&l1v1 Odası", UI_SUB + "Rakip bekleniyor", 5, 35, 10);
        roomSound(p, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, 1.3f);
    }
    private void roomLeaveNotice(Player p) {
        if (!roomCooldownOk(p.getUniqueId(), 1500L)) return;
        p.sendMessage(roomMsg("1v1 odasından ayrıldın."));
        roomSound(p, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.3f, 0.8f);
    }
    private void roomKick(Player p, String reason) {
        if (p == null || !p.isOnline()) return;
        Long cd = roomKickCd.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (cd != null && now - cd < 3000L) return;
        roomKickCd.put(p.getUniqueId(), now);
        p.sendMessage(roomErr(reason));
        roomSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 0.4f, 0.9f);
        sendToHub(p);
    }

    /** Oyuncuyu lobiye gönderir: konsoldan hub komutu (config: oda.hub-komutu), olmazsa /hub, yine olmazsa spawn. */
    private void sendToHub(Player p) {
        if (p == null || !p.isOnline()) return;
        final UUID uid = p.getUniqueId();
        roomIgnoreUntil.put(uid, System.currentTimeMillis() + 4000L);
        hudDropNow(p, 3000L);   // yolcu HUD ışınlamayı engellemesin
        p.setWalkSpeed(0.2f); p.setFlySpeed(0.1f);
        String cmd = getConfig().getString("oda.hub-komutu", "hub {oyuncu}").replace("{oyuncu}", p.getName()).trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        boolean ok = false;
        if (!cmd.isEmpty()) {
            try { ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd); } catch (Exception ignored) {}
        }
        if (!ok) {
            try { p.performCommand("hub"); } catch (Exception ignored) {}
        }
        // Konsol komutu "bulundu" sayılsa bile eklenti oyuncuyu taşımamış olabilir (konsol söz dizimi farklı olabilir):
        // 10 tick sonra hâlâ odadaysa oyuncunun kendi /hub'ı denenir.
        final boolean consoleHandled = ok;
        new BukkitRunnable() {
            @Override public void run() {
                Player q = Bukkit.getPlayer(uid);
                if (q == null || !q.isOnline() || q.isDead()) return;
                if (consoleHandled && roomAt(q.getLocation()) != null) {
                    try { q.performCommand("hub"); } catch (Exception ignored) {}
                }
            }
        }.runTaskLater(this, 10L);
        // Güvenlik ağı: hub çalışmadıysa oyuncu odada takılı kalmasın
        new BukkitRunnable() {
            @Override public void run() {
                Player q = Bukkit.getPlayer(uid);
                if (q == null || !q.isOnline() || q.isDead()) return;
                if (roomAt(q.getLocation()) != null) teleportToSpawn(q);
            }
        }.runTaskLater(this, 30L);
    }

    /** Oyuncu şu an BAŞKA bir (var olan) odanın düellosuna bağlı mı? */
    private boolean roomEngagedElsewhere(UUID u, Room self) {
        if (u == null) return false;
        String id = playerAyarlaArena.get(u);
        return id != null && !id.equals(self.id) && rooms.containsKey(id);
    }

    /** İki oda aynı dünyada kesişiyor mu? */
    private Room roomOverlapping(Room n) {
        for (Room o : rooms.values()) {
            if (o == n || !o.world.equals(n.world)) continue;
            if (o.box.overlaps(n.box)) return o;
        }
        return null;
    }

    private Room roomAt(Location l) {
        if (l == null || l.getWorld() == null) return null;
        for (Room r : rooms.values())
            if (r.world.equals(l.getWorld().getName()) && r.box.contains(l.toVector())) return r;
        return null;
    }

    // ── Ekipman puanı (3. oyuncu kararında en düşük olan lobiye gider) ──
    private double gearArmorValue(ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return 0;
        String n = it.getType().name();
        double v = 0;
        if (n.startsWith("NETHERITE_")) v = 8; else if (n.startsWith("DIAMOND_")) v = 6;
        else if (n.startsWith("IRON_")) v = 4; else if (n.startsWith("CHAINMAIL_") || n.startsWith("TURTLE_")) v = 3;
        else if (n.startsWith("GOLDEN_")) v = 2; else if (n.startsWith("LEATHER_")) v = 1;
        else if (it.getType() == Material.ELYTRA) v = 5;
        if (v > 0) for (int lv : it.getEnchantments().values()) v += lv * 0.4;
        return v;
    }
    private double gearWeaponValue(ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return 0;
        String n = it.getType().name();
        double v = 0;
        if (n.endsWith("_SWORD") || n.endsWith("_AXE")) {
            if (n.startsWith("NETHERITE_")) v = 8; else if (n.startsWith("DIAMOND_")) v = 6;
            else if (n.startsWith("IRON_")) v = 4; else if (n.startsWith("STONE_")) v = 2.5;
            else if (n.startsWith("GOLDEN_")) v = 2; else v = 1;
        } else if (it.getType() == Material.MACE) v = 9;
        else if (it.getType() == Material.TRIDENT) v = 5;
        else if (it.getType() == Material.BOW || it.getType() == Material.CROSSBOW) v = 2.5;
        if (v > 0) for (int lv : it.getEnchantments().values()) v += lv * 0.4;
        return v;
    }
    private double gearScore(Player p) {
        if (p == null || !p.isOnline()) return -1;
        double s = 0;
        PlayerInventory inv = p.getInventory();
        for (ItemStack it : inv.getArmorContents()) s += gearArmorValue(it);
        List<ItemStack> all = new ArrayList<>(Arrays.asList(inv.getContents()));
        all.add(inv.getItemInOffHand());
        double bestW = 0, extra = 0; int totems = 0;
        for (ItemStack it : all) {
            if (it == null || it.getType() == Material.AIR) continue;
            bestW = Math.max(bestW, gearWeaponValue(it));
            Material m = it.getType();
            if (m == Material.TOTEM_OF_UNDYING) totems += it.getAmount();
            else if (m == Material.ENCHANTED_GOLDEN_APPLE) extra += it.getAmount() * 0.6;
            else if (m == Material.GOLDEN_APPLE) extra += it.getAmount() * 0.15;
            else if (m == Material.ENDER_PEARL) extra += Math.min(it.getAmount(), 16) * 0.05;
        }
        return s + bestW + Math.min(totems, 6) * 1.5 + Math.min(extra, 8);
    }
    /** En düşük ekipmanlı oyuncu; eşitlikte en son girenin üstüne düşer. */
    private UUID gearLowest(Collection<UUID> ids) {
        UUID worst = null; double best = Double.MAX_VALUE;
        for (UUID u : ids) {
            double sc = gearScore(Bukkit.getPlayer(u));
            if (sc <= best) { best = sc; worst = u; }
        }
        return worst;
    }

    // ── Cam duvarlar (yalnızca boş bloklara konur, yalnızca konulanlar geri alınır) ──
    private void roomCloseGlass(Room r) {
        World w = Bukkit.getWorld(r.world);
        if (w == null) return;
        r.glass.clear();
        for (int x = r.minX; x <= r.maxX; x++)
            for (int y = r.minY; y <= r.maxY; y++)
                for (int z = r.minZ; z <= r.maxZ; z++) {
                    boolean yanKenar = (x == r.minX || x == r.maxX || z == r.minZ || z == r.maxZ) && y > r.minY;
                    boolean tavan = (y == r.maxY);
                    if (!(yanKenar || tavan)) continue;
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType() == Material.AIR) {
                        b.setType(Material.GLASS, false);
                        r.glass.add(b.getLocation());
                    }
                }
        roomGlassStore(r);
    }
    private void roomOpenGlass(Room r) {
        for (Location l : r.glass) {
            if (l.getWorld() == null) continue;
            Block b = l.getBlock();
            if (b.getType() == Material.GLASS) b.setType(Material.AIR, false);
        }
        r.glass.clear();
        roomGlassStore(r);
    }
    /** Cam listesini diske yazar: sunucu maç ortasında kapanırsa açılışta camlar temizlenir. */
    private void roomGlassStore(Room r) {
        try {
            if (roomGlassFile == null) roomGlassFile = new java.io.File(getDataFolder(), "oda-cam.yml");
            org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(roomGlassFile);
            if (r.glass.isEmpty()) { y.set(r.id, null); }
            else {
                List<String> l = new ArrayList<>();
                for (Location loc : r.glass) l.add(loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
                y.set(r.id + ".world", r.world);
                y.set(r.id + ".bloklar", l);
            }
            getDataFolder().mkdirs();
            y.save(roomGlassFile);
        } catch (Exception ex) {
            getLogger().warning("1v1 Room cam kaydı yazılamadı: " + ex.getMessage());
        }
    }
    private void roomGlassRecover() {
        try {
            roomGlassFile = new java.io.File(getDataFolder(), "oda-cam.yml");
            if (!roomGlassFile.exists()) return;
            org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(roomGlassFile);
            for (String id : y.getKeys(false)) {
                World w = Bukkit.getWorld(y.getString(id + ".world", ""));
                if (w == null) continue;
                for (String s : y.getStringList(id + ".bloklar")) {
                    String[] pt = s.split(",");
                    if (pt.length != 3) continue;
                    Block b = w.getBlockAt(Integer.parseInt(pt[0].trim()), Integer.parseInt(pt[1].trim()), Integer.parseInt(pt[2].trim()));
                    if (b.getType() == Material.GLASS) b.setType(Material.AIR, false);
                }
            }
            roomGlassFile.delete();
        } catch (Exception ex) {
            getLogger().warning("1v1 Room cam temizliği başarısız: " + ex.getMessage());
        }
    }

    // ── Kalıcılık ──
    private void saveRooms(boolean writeNow) {
        getConfig().set("oda.arenalar", null);
        for (Room r : rooms.values()) {
            String b = "oda.arenalar." + r.id;
            getConfig().set(b + ".world", r.world);
            getConfig().set(b + ".min", r.minX + "," + r.minY + "," + r.minZ);
            getConfig().set(b + ".max", r.maxX + "," + r.maxY + "," + r.maxZ);
        }
        if (writeNow) saveConfig();
    }
    private void loadRooms() {
        rooms.clear();
        if (!getConfig().isConfigurationSection("oda.arenalar")) return;
        for (String id : getConfig().getConfigurationSection("oda.arenalar").getKeys(false)) {
            try {
                String base = "oda.arenalar." + id;
                String[] mn = getConfig().getString(base + ".min", "").split(",");
                String[] mx = getConfig().getString(base + ".max", "").split(",");
                Room r = new Room(id, getConfig().getString(base + ".world", "world"),
                    Integer.parseInt(mn[0].trim()), Integer.parseInt(mn[1].trim()), Integer.parseInt(mn[2].trim()),
                    Integer.parseInt(mx[0].trim()), Integer.parseInt(mx[1].trim()), Integer.parseInt(mx[2].trim()));
                Room ovl = roomOverlapping(r);
                if (ovl != null) getLogger().warning("1v1 Room '" + id + "' ile '" + ovl.id + "' üst üste biniyor! Birini /1v1randomsil ile sil. (Çifte maç koruması aktif)");
                rooms.put(id, r);
                try { arenaCounter = Math.max(arenaCounter, Integer.parseInt(id.substring(id.lastIndexOf('_') + 1))); } catch (Exception ignored) {}
            } catch (Exception ex) {
                getLogger().warning("1v1 Room yüklenemedi (" + id + "): " + ex.getMessage());
            }
        }
    }
    private boolean roomDelete(String id) {
        Room r = rooms.remove(id);
        if (r == null) return false;
        roomOpenGlass(r);
        if (r.a != null) playerAyarlaArena.remove(r.a, r.id);
        if (r.b != null) playerAyarlaArena.remove(r.b, r.id);
        saveRooms(true);
        return true;
    }

    // ── Wand: sağ tık = 1. köşe, sol tık = 2. köşe + oda kurulumu ──
    @EventHandler
    public void onAyarlaWandUse(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() != Material.BLAZE_ROD) return;
        if (!item.hasItemMeta() || item.getItemMeta().getDisplayName() == null) return;
        String dn = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        if (!dn.contains("1v1 Ayarla")) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        e.setCancelled(true);
        if (e.getClickedBlock() == null) return;
        Location clicked = e.getClickedBlock().getLocation();
        UUID uid = p.getUniqueId();

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ayarlaPos1.put(uid, clicked);
            p.sendMessage(roomMsg("Birinci köşe: " + hl(clicked.getBlockX() + ", " + clicked.getBlockY() + ", " + clicked.getBlockZ())));
            roomSound(p, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4f, 1.4f);
        } else if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            Location l1 = ayarlaPos1.get(uid);
            if (l1 == null) { p.sendMessage(roomErr("Önce sağ tıkla ile birinci köşeyi seç.")); return; }
            if (l1.getWorld() == null || !l1.getWorld().equals(clicked.getWorld())) {
                p.sendMessage(roomErr("İki köşe aynı dünyada olmalı.")); return;
            }
            String id;
            do { id = "ayarla_" + (++arenaCounter); } while (rooms.containsKey(id));
            Room r = new Room(id, clicked.getWorld().getName(),
                l1.getBlockX(), l1.getBlockY(), l1.getBlockZ(),
                clicked.getBlockX(), clicked.getBlockY(), clicked.getBlockZ());
            Room ov = roomOverlapping(r);
            if (ov != null) {
                p.sendMessage(roomErr("Bu oda " + hl(ov.id) + " odasıyla üst üste biniyor. Çifte maç hatası olmaması için kurulmadı."));
                return;
            }
            rooms.put(id, r);
            saveRooms(true);
            ayarlaPos1.remove(uid); ayarlaPos2.remove(uid);
            p.sendMessage(roomMsg("Oda kuruldu. ID: " + hl(id)));
            p.sendMessage(roomMsg("Boyut: " + hl((r.maxX - r.minX + 1) + " x " + (r.maxY - r.minY + 1) + " x " + (r.maxZ - r.minZ + 1))
                + ". İki oyuncu girince oda kapanır, düello bitince kendiliğinden açılır."));
            roomSound(p, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.45f, 1.1f);
        }
    }

    // ── Ana zamanlayıcı ──
    private void tickRooms() {
        if (rooms.isEmpty()) return;
        long nowMs = System.currentTimeMillis();
        roomIgnoreUntil.values().removeIf(t -> t < nowMs);
        roomKickCd.values().removeIf(t -> nowMs - t > 10_000L);
        for (Room r : new ArrayList<>(rooms.values())) {
            try {
                tickRoom(r, nowMs);
            } catch (Throwable t) {
                getLogger().warning("1v1 Room hatası (" + r.id + "): " + t);
                try { roomReset(r, false); } catch (Throwable ignored) {}
            }
        }
    }

    private void tickRoom(Room r, long nowMs) {
        World w = Bukkit.getWorld(r.world);
        if (w == null) return;

        LinkedHashSet<UUID> now = new LinkedHashSet<>();
        for (Player p : w.getPlayers()) {
            if (!p.isOnline() || p.isDead()) continue;
            GameMode gm = p.getGameMode();
            if (gm == GameMode.SPECTATOR || gm == GameMode.CREATIVE) continue;
            Long ig = roomIgnoreUntil.get(p.getUniqueId());
            if (ig != null && ig > nowMs) continue;
            // Başka bir odanın düellosuna bağlı oyuncu bu odada sayılmaz (üst üste binen odalarda çifte maçı engeller)
            if (roomEngagedElsewhere(p.getUniqueId(), r)) continue;
            if (r.box.contains(p.getLocation().toVector())) now.add(p.getUniqueId());
        }

        if (r.state == ROOM_WAIT) {
            // Çıkanlar
            for (Iterator<UUID> it = r.inside.iterator(); it.hasNext(); ) {
                UUID u = it.next();
                if (!now.contains(u)) {
                    it.remove();
                    Player lp = Bukkit.getPlayer(u);
                    if (lp != null && lp.isOnline()) roomLeaveNotice(lp);
                }
            }
            // Girenler
            List<UUID> joined = new ArrayList<>();
            for (UUID u : now) if (r.inside.add(u)) joined.add(u);
            // 3 veya daha fazla oyuncu: en düşük ekipmanlı gönderilir, 2 kişi kalır
            while (r.inside.size() > 2) {
                UUID worst = gearLowest(r.inside);
                if (worst == null) break;
                r.inside.remove(worst);
                joined.remove(worst);
                Player wp = Bukkit.getPlayer(worst);
                roomKick(wp, "Odaya üç oyuncu girdi. Ekipmanı en düşük oyuncu olarak lobiye gönderildin.");
            }
            if (r.inside.size() == 2) {
                roomStart(r);
            } else {
                for (UUID u : joined) {
                    Player jp = Bukkit.getPlayer(u);
                    if (jp != null && jp.isOnline() && r.inside.contains(u)) roomEnterNotice(jp);
                }
            }
            return;
        }

        if (r.state == ROOM_ACTIVE) {
            if (r.a == null || r.b == null) { roomReset(r, false); return; }
            // Bayat (hayalet) oda: oyuncular artık başka bir odanın düellosuna bağlı -> sessizce sıfırla, kimseyi yenik sayma
            if (roomEngagedElsewhere(r.a, r) || roomEngagedElsewhere(r.b, r)) { roomReset(r, false); return; }
            for (UUID du : new UUID[]{r.a, r.b}) {
                Player dp = Bukkit.getPlayer(du);
                if (dp == null || !dp.isOnline()) { roomForfeit(r, du); return; }
                if (dp.isDead()) continue; // ölüm olayı bitişi kendisi yönetir
                if (now.contains(du)) { r.outTicks.remove(du); continue; }
                int n = r.outTicks.merge(du, 1, Integer::sum);
                if (n >= 8) { roomForfeit(r, du); return; } // odanın dışına çıkan düelloyu kaybeder
            }
            // Düelloya ait olmayan herkes lobiye
            for (UUID u : now) {
                if (r.isDueler(u)) continue;
                roomKick(Bukkit.getPlayer(u), "Bu odada düello sürüyor. Lobiye yönlendiriliyorsun.");
            }
            // 10 dakika üst süre
            if (nowMs - r.startedAt >= 600_000L) roomDraw(r);
            return;
        }

        // BİTİŞ: kazanan geri sayım, sonra oda sıfırlanır
        if (nowMs >= r.endAt) { roomReset(r, true); return; }
        int sec = (int) Math.ceil((r.endAt - nowMs) / 1000.0);
        if (sec != r.lastSec) {
            r.lastSec = sec;
            Player win = r.winner != null ? Bukkit.getPlayer(r.winner) : null;
            roomActionBar(win, UI_SUB + "Lobiye yönlendirilmene " + UI_TEXT + sec + UI_SUB + " saniye");
        }
    }

    private void roomStart(Room r) {
        Iterator<UUID> it = r.inside.iterator();
        UUID ua = it.next(), ub = it.next();
        Player pa = Bukkit.getPlayer(ua), pb = Bukkit.getPlayer(ub);
        if (pa == null || !pa.isOnline() || pb == null || !pb.isOnline()) {
            r.inside.removeIf(u -> { Player q = Bukkit.getPlayer(u); return q == null || !q.isOnline(); });
            return;
        }
        if (roomEngagedElsewhere(ua, r) || roomEngagedElsewhere(ub, r)) {
            r.inside.clear(); // oyunculardan biri zaten başka odada düelloda
            return;
        }
        r.a = ua; r.b = ub; r.winner = null;
        r.state = ROOM_ACTIVE; r.startedAt = System.currentTimeMillis(); r.outTicks.clear();
        playerAyarlaArena.put(ua, r.id);
        playerAyarlaArena.put(ub, r.id);
        roomCloseGlass(r);
        roomAnnounceStart(pa, pb);
        roomAnnounceStart(pb, pa);
    }
    private void roomAnnounceStart(Player p, Player opp) {
        p.sendMessage(roomMsg("Düello başladı. Rakibin " + hl(opp.getName()) + ". Oyundan çıkarsan maçı kaybedersin."));
        roomTitle(p, UI_TEXT + "&lDüello Başladı", UI_SUB + "Rakip: " + UI_TEXT + opp.getName(), 5, 45, 10);
        roomSound(p, Sound.BLOCK_NOTE_BLOCK_BELL, 0.45f, 1.1f);
        new BukkitRunnable() { @Override public void run() { roomSound(p, Sound.BLOCK_NOTE_BLOCK_BELL, 0.45f, 1.5f); } }.runTaskLater(this, 4L);
    }

    /** Kazanan belli oldu: ACTIVE -> ENDING. Sadece bir kez çalışır (tekrar mesaj yok). */
    private void roomFinish(Room r, UUID winnerId, UUID loserId, String loserName, boolean loserLeft) {
        if (r.state != ROOM_ACTIVE) return;
        r.state = ROOM_ENDING;
        r.winner = winnerId;
        r.endAt = System.currentTimeMillis() + roomEndWaitMs();
        r.lastSec = -1;
        Player winner = winnerId != null ? Bukkit.getPlayer(winnerId) : null;
        Player loser = loserId != null ? Bukkit.getPlayer(loserId) : null;

        if (winner != null && winner.isOnline()) {
            winner.sendMessage(roomMsg(loserLeft
                ? hl(loserName) + " oyundan ayrıldı. Düelloyu kazandın."
                : "Düelloyu kazandın. " + hl(loserName) + " elendi."));
            roomTitle(winner, UI_OK + "&lZafer", UI_SUB + loserName + (loserLeft ? " oyundan ayrıldı" : " elendi"), 5, 50, 15);
            roomSound(winner, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.1f);
        }
        if (loser != null && loser.isOnline() && !loserLeft) {
            String wn = winner != null ? winner.getName() : "Rakip";
            loser.sendMessage(roomMsg("Düelloyu kaybettin. Kazanan: " + hl(wn) + "."));
            roomTitle(loser, UI_BAD + "&lYenilgi", UI_SUB + "Kazanan: " + UI_TEXT + wn, 5, 50, 15);
            roomSound(loser, Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.8f);
            if (loser.isDead()) hubOnRespawn.add(loserId);
            else sendToHub(loser);
        }
    }

    private void roomForfeit(Room r, UUID loserId) {
        if (r.state != ROOM_ACTIVE) return;
        Player loser = Bukkit.getPlayer(loserId);
        UUID oppId = r.opponentOf(loserId);
        Player winner = oppId != null ? Bukkit.getPlayer(oppId) : null;
        boolean left = loser == null || !loser.isOnline();
        if (!left && !loser.isDead()) {
            giveDuelLootChest(loser, (winner != null && winner.isOnline() && !winner.isDead()) ? winner : null, loser.getLocation());
        }
        roomFinish(r, oppId, loserId, loser != null ? loser.getName() : "Rakip", left);
    }

    private void roomDraw(Room r) {
        for (UUID u : new UUID[]{r.a, r.b}) {
            Player pl = u != null ? Bukkit.getPlayer(u) : null;
            if (pl == null || !pl.isOnline()) continue;
            pl.sendMessage(roomMsg("Süre doldu. Düello berabere sonuçlandı."));
            roomTitle(pl, UI_WARN + "&lSüre Doldu", UI_SUB + "Düello berabere", 5, 50, 15);
            roomSound(pl, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4f, 0.7f);
            if (!pl.isDead()) sendToHub(pl);
        }
        roomReset(r, false);
    }

    /** Odayı her koşulda temiz bekleme durumuna döndürür; camlar açılır. */
    private void roomReset(Room r, boolean sendWinnerToHub) {
        roomOpenGlass(r);
        UUID win = r.winner;
        // Yalnızca bu odaya ait kayıtları sil: oyuncu başka odaya bağlıysa onun kaydına dokunma
        if (r.a != null) playerAyarlaArena.remove(r.a, r.id);
        if (r.b != null) playerAyarlaArena.remove(r.b, r.id);
        r.a = null; r.b = null; r.winner = null;
        r.inside.clear(); r.outTicks.clear();
        r.state = ROOM_WAIT; r.lastSec = -1;
        if (sendWinnerToHub && win != null) {
            Player wp = Bukkit.getPlayer(win);
            if (wp != null && wp.isOnline() && !wp.isDead()) {
                wp.sendMessage(roomMsg("Düello tamamlandı. Lobiye yönlendiriliyorsun."));
                sendToHub(wp);
            }
        }
    }

    private void roomHandleDeath(Player dead) {
        UUID uid = dead.getUniqueId();
        String id = playerAyarlaArena.get(uid);
        if (id == null) return;
        Room r = rooms.get(id);
        if (r == null) { playerAyarlaArena.remove(uid); return; }
        if (r.state == ROOM_ACTIVE && r.isDueler(uid)) {
            roomFinish(r, r.opponentOf(uid), uid, dead.getName(), false);
        } else {
            hubOnRespawn.add(uid); // bitiş sürecinde ölen: yine lobiye
        }
    }

    private void roomHandleQuit(Player p) {
        UUID u = p.getUniqueId();
        hubOnRespawn.remove(u);
        roomIgnoreUntil.remove(u);
        for (Room rr : rooms.values()) rr.inside.remove(u);
        String id = playerAyarlaArena.get(u);
        if (id == null) return;
        Room r = rooms.get(id);
        if (r == null) { playerAyarlaArena.remove(u); return; }
        if (r.state == ROOM_ACTIVE && r.isDueler(u)) {
            UUID oppId = r.opponentOf(u);
            Player winner = oppId != null ? Bukkit.getPlayer(oppId) : null;
            giveDuelLootChest(p, (winner != null && winner.isOnline() && !winner.isDead()) ? winner : null, p.getLocation());
            roomFinish(r, oppId, u, p.getName(), true);
        }
        playerAyarlaArena.remove(u);
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
            captureFullInventory(dead)));

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
            }
            // 1v1 Room: sandık her zaman odadaki rakibe gider (normal düellodaki gibi)
            Room roomOfDeath = rooms.get(playerAyarlaArena.get(dead.getUniqueId()));
            if (roomOfDeath != null) {
                UUID oppId = roomOfDeath.opponentOf(dead.getUniqueId());
                Player opp = oppId != null ? Bukkit.getPlayer(oppId) : null;
                if (opp != null && opp.isOnline() && !opp.isDead()) duelLootRecipient = opp;
            }
            giveDuelLootChest(dead, duelLootRecipient, dropLoc);
            // ── Ölüm sonrası komut kilidi başlasın (10sn hiçbir şey, sonra sadece /ec ve /pv) ──
            duelDeathTime.put(dead.getUniqueId(), System.currentTimeMillis());
        } else if (isNoDropDeath(dead)) {
            // ─── Eşya düşmeme sistemi: bu dünyada (bölge dışında) ölünce envanter aynen kalır ───
            e.setKeepInventory(true);
            e.getDrops().clear();
        } else if (killer != null && dead.getWorld().getName().equalsIgnoreCase("kutu")) {
            // ─── "kutu" dünyası: OYUNCU oyuncuyu öldürürse item düşmesin, envanter aynen kalsın (dupe-safe) ───
            e.setKeepInventory(true);
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

        // ─── Stash varsa: "kutu"da PvP ölümüyse dupe riskine karşı stash'i geri ver, aksi halde tek noktaya dök ───
        boolean kutuPvpDeath = (killer != null && dead.getWorld().getName().equalsIgnoreCase("kutu")) || (!isDuelDeath && isNoDropDeath(dead));
        ItemStack[] stashContents = playerStash.remove(dead.getUniqueId());
        stashOpen.put(dead.getUniqueId(), false);
        if (stashContents != null) {
            if (kutuPvpDeath) {
                for (ItemStack it : stashContents) {
                    if (it == null || it.getType() == Material.AIR) continue;
                    HashMap<Integer, ItemStack> leftover = dead.getInventory().addItem(it.clone());
                    for (ItemStack extra : leftover.values()) {
                        dead.getWorld().dropItemNaturally(dead.getLocation(), extra);
                    }
                }
            } else {
                for (ItemStack it : stashContents) {
                    if (it == null || it.getType() == Material.AIR) continue;
                    org.bukkit.entity.Item dropped = dead.getWorld().dropItem(dropLoc, it.clone());
                    dropped.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    dropped.setPickupDelay(40);
                }
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
                            color(UI_TEXT + "1v1 bitti"),
                            color(UI_SUB + dead.getName() + " elendi"),
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

        // 1v1 Room — ölüm: kazananı belirle, camları geri sayımla aç, odayı tekrar kullanıma hazırla
        roomHandleDeath(dead);
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
                // 1v1 Room'da kaybeden: lobiye (/hub). Diğer durumlar: spawn
                if (hubOnRespawn.remove(p.getUniqueId())) sendToHub(p);
                else teleportToSpawn(p);
            }
        }.runTaskLater(this, 2L);
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
                p.sendTitle(color("&#ff4444&lKoruma Aktif"), color("&#ffcc00Koruma altındayken eşya alamazsın!"), 5, 40, 10);
            }
        }
    }

    // ─── Koruma altındayken portallardan geçiş engeli (vanilla nether/end portalları) ───
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectionPortal(PlayerPortalEvent ev) {
        Player p = ev.getPlayer();
        if (protectedUntil.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) {
            ev.setCancelled(true);
            long now = System.currentTimeMillis();
            long lastWarn = wrongKeyCooldown.getOrDefault(p.getUniqueId(), 0L);
            if (lastWarn + 5000L < now) {
                wrongKeyCooldown.put(p.getUniqueId(), now);
                p.sendTitle(color("&#ff4444&lKoruma Aktif"), color("&#ffcc00Koruma altındayken portallardan geçemezsin!"), 5, 40, 10);
            }
            p.sendActionBar(color("&#ff4444&fKoruma altındayken portallardan geçemezsin!"));
        }
    }

    // ─── Koruma altındayken diğer plugin/portal kaynaklı teleportları da engelle (Multiverse-Portals,
    // AdvancedPortals ve benzeri portal eklentileri dahil — bunlar genelde NETHER_PORTAL/END_PORTAL/
    // END_GATEWAY sebebiyle veya kendi PLUGIN teleportuyla çalışır) ───
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectionTeleport(PlayerTeleportEvent ev) {
        Player p = ev.getPlayer();
        if (protectedUntil.getOrDefault(p.getUniqueId(), 0L) <= System.currentTimeMillis()) return;
        PlayerTeleportEvent.TeleportCause cause = ev.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || cause == PlayerTeleportEvent.TeleportCause.END_PORTAL
                || cause == PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
            ev.setCancelled(true);
            p.sendActionBar(color("&#ff4444&fKoruma altındayken portallardan geçemezsin!"));
            return;
        }
        // Eklenti kaynaklı ışınlanmalar (PLUGIN / COMMAND / UNKNOWN): engellenen warp'a gidiyorsa veya portal eklentisi tetiklediyse iptal
        if (cause == PlayerTeleportEvent.TeleportCause.PLUGIN
                || cause == PlayerTeleportEvent.TeleportCause.COMMAND
                || cause == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            if (protTeleportsToBlockedWarp(ev.getTo())) {
                ev.setCancelled(true);
                protWarn(p, "Koruma altındayken bu warp'a gidemezsin!");
            } else if (protCalledByPortalPlugin()) {
                ev.setCancelled(true);
                protWarn(p, "Koruma altındayken portallardan geçemezsin!");
            }
        }
    }

    // ─── Koruma: engellenen warp'lar ve portal eklentileri ───
    //  config:  koruma.engellenen-warplar: [end]                       -> /warp end (ve oraya giden her ışınlanma)
    //           koruma.warp-komutlari: [warp, ewarp]                   -> "warp" komutunun takma adları
    //           koruma.portal-paketleri: [com.sekwah.advancedportals, com.onarandombox.multiverseportals]
    //             (ışınlanmayı çağıran kodun yığınında bu paketlerden biri varsa portal sayılır — eklentinin kendi API'sine bağlı değildir)
    private boolean protIsProtected(Player p) {
        return protectedUntil.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis();
    }
    private List<String> protList(String path, String... def) {
        List<String> l = getConfig().getStringList(path);
        return l.isEmpty() ? Arrays.asList(def) : l;
    }
    private void protWarn(Player p, String msg) {
        long now = System.currentTimeMillis();
        long lastWarn = wrongKeyCooldown.getOrDefault(p.getUniqueId(), 0L);
        if (lastWarn + 5000L < now) {
            wrongKeyCooldown.put(p.getUniqueId(), now);
            p.sendTitle(color("&#ff4444&lKoruma Aktif"), color("&#ffcc00" + msg), 5, 40, 10);
        }
        p.sendActionBar(color("&#ff4444&f" + msg));
    }
    private boolean protCalledByPortalPlugin() {
        List<String> pk = protList("koruma.portal-paketleri", "com.sekwah.advancedportals", "com.onarandombox.multiverseportals");
        for (StackTraceElement el : new Throwable().getStackTrace()) {
            String cn = el.getClassName();
            for (String s : pk) if (!s.isEmpty() && cn.startsWith(s)) return true;
        }
        return false;
    }
    /** Essentials varsa engellenen warp'ların konumunu okur (yansıtma); hedef o konuma 3 blok yakınsa true. */
    private boolean protTeleportsToBlockedWarp(Location to) {
        if (to == null || to.getWorld() == null) return false;
        try {
            org.bukkit.plugin.Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
            if (ess == null || !ess.isEnabled()) return false;
            Object warps = ess.getClass().getMethod("getWarps").invoke(ess);
            java.lang.reflect.Method gw = warps.getClass().getMethod("getWarp", String.class);
            for (String name : protList("koruma.engellenen-warplar", "end")) {
                try {
                    Object o = gw.invoke(warps, name);
                    if (o instanceof Location wl && wl.getWorld() != null && wl.getWorld().equals(to.getWorld())
                            && wl.distanceSquared(to) <= 9.0) return true;
                } catch (Throwable ignored) { /* warp yok */ }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    // ─── Koruma altındayken /warp end (ve config'teki diğer engelli warp'lar) komutu ───
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectionWarpCommand(PlayerCommandPreprocessEvent ev) {
        Player p = ev.getPlayer();
        if (!protIsProtected(p)) return;
        String raw = ev.getMessage().trim();
        if (raw.startsWith("/")) raw = raw.substring(1);
        String[] parts = raw.split("\\s+");
        if (parts.length < 2) return;
        String label = parts[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');                 // essentials:warp biçimi
        if (colon >= 0) label = label.substring(colon + 1);
        boolean isWarp = false;
        for (String w : protList("koruma.warp-komutlari", "warp", "ewarp")) if (w.equalsIgnoreCase(label)) { isWarp = true; break; }
        if (!isWarp) return;
        String target = parts[1].toLowerCase(Locale.ROOT);
        for (String b : protList("koruma.engellenen-warplar", "end")) {
            if (b.equalsIgnoreCase(target)) {
                ev.setCancelled(true);
                protWarn(p, "Koruma altındayken /warp " + b + " kullanamazsın!");
                return;
            }
        }
    }

    // ─── Fiziksel portal bloğu üstünde durma engeli — kendi olay tetiklemeyen üçüncü parti
    // portal eklentilerine karşı ek güvence (blok üstündeyken geriye it) ───
    // NOT: Hızlı koşarken (sprint) oyuncu tek tikte birden fazla blok ilerleyebildiği için
    // sadece "to" bloğuna bakmak yetersiz kalıyordu — araya giren portal bloğu atlanabiliyordu.
    // Bunu önlemek için "to" konumunun çevresindeki (bounding box + baş/ayak seviyesi) bloklar da
    // taranıyor ve tespit anında oyuncu anında "from" konumuna geri itiliyor (velocity sıfırlanarak).
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectionPortalStand(PlayerMoveEvent ev) {
        Player p = ev.getPlayer();
        if (protectedUntil.getOrDefault(p.getUniqueId(), 0L) <= System.currentTimeMillis()) return;
        Location to = ev.getTo();
        Location from = ev.getFrom();
        if (to == null) return;

        if (isNearPortalBlock(to) || isNearPortalBlock(from)) {
            ev.setCancelled(true);
            ev.setTo(from);
            // Bir sonraki tikte de aynı yerde kalsın diye anında konumu geri sabitle (sprint clip önleme)
            Bukkit.getScheduler().runTask(this, () -> {
                if (p.isOnline()) {
                    hudTp(p, from);
                    p.setVelocity(new Vector(0, 0, 0));
                }
            });
            long now = System.currentTimeMillis();
            long lastWarn = wrongKeyCooldown.getOrDefault(p.getUniqueId(), 0L);
            if (lastWarn + 5000L < now) {
                wrongKeyCooldown.put(p.getUniqueId(), now);
                p.sendTitle(color("&#ff4444&lKoruma Aktif"), color("&#ffcc00Koruma altındayken portallardan geçemezsin!"), 5, 40, 10);
            }
            p.sendActionBar(color("&#ff4444&fKoruma altındayken portallardan geçemezsin!"));
        }
    }

    /** Verilen konumun etrafında (ayak + baş seviyesi, 1 blok yarıçap) portal bloğu var mı kontrol eder.
     *  Sprint sırasında tek tikte birden fazla blok atlanabildiği için sadece tam konum yeterli değil. */
    private boolean isNearPortalBlock(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) { // ayak + baş seviyesi
                for (int dz = -1; dz <= 1; dz++) {
                    Material mat = loc.getWorld().getBlockAt(bx + dx, by + dy, bz + dz).getType();
                    if (mat == Material.NETHER_PORTAL || mat == Material.END_PORTAL || mat == Material.END_GATEWAY) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Bakım modunda atılan veya giriş yapamayan oyunculara gösterilen ekran. Satırlar istemcide ortalanır. */
    private String maintenanceKickMessage() {
        return color(
            UI_TEXT + "Sunucu bakımda\n" +
            "\n" +
            UI_SUB + "Bakım ve güncelleme çalışmaları sürüyor.\n" +
            UI_SUB + "Tamamlandığında tekrar bağlanabilirsin.");
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
                    p.kickPlayer(maintenanceKickMessage());
                }
            }.runTaskLater(this, 1L);
            return;
        }

        // ─── Bakımdayken girdi — admin/izinli için bilgi ver ───
        if (bakimModu && (p.isOp() || p.hasPermission("loginx.admin") || bakimIzinli.contains(p.getUniqueId()))) {
            new BukkitRunnable() {
                @Override public void run() {
                    if (!p.isOnline()) return;
                    p.sendTitle(color(UI_TEXT + "Bakım Modu"), color(UI_SUB + "Sunucu şu an bakımda"), 5, 100, 20);
                    p.sendMessage(uiMsg("Bakım", "Bakım modu açık. Yalnızca yetkililer ve izin verilen oyuncular girebilir."));
                    p.sendMessage(uiMsg("Bakım", "Kapatmak için " + hl("/bakimdancikar") + ", oyuncu eklemek için " + hl("/bakimaoyuncual <oyuncu>") + " kullan."));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.0f);
                }
            }.runTaskLater(this, 20L);
        }

        // Kalıcı olarak kapattıysa verme
        boolean disabled = getConfig().getBoolean("protection_disabled." + p.getUniqueId().toString(), false);
        if (disabled) return;
        // Koruma daha önce (herhangi bir zamanda) hiç verildiyse — restart sonrası bile — bir daha verilmesin
        boolean alreadyGranted = getConfig().getBoolean("protection_granted." + p.getUniqueId().toString(), false);
        if (alreadyGranted) return;
        // Henüz aktif koruması yoksa yeni koruma başlat (yalnızca ilk kez)
        if (protectedUntil.getOrDefault(p.getUniqueId(), 0L) <= now) {
            protectedUntil.put(p.getUniqueId(), now + PROTECTION_MS);
            getConfig().set("protection_granted." + p.getUniqueId().toString(), true);
            saveConfig();
            new BukkitRunnable() {
                @Override public void run() {
                    if (!p.isOnline()) return;
                    p.sendTitle(
                        color("&#00ccff&lKORUMA AKTİF"),
                        color("&#ffffff25 Dakika boyunca koruma altındasın"),
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
            p.sendActionBar(color("&#00ccffKoruma: &f" + timeStr + " &8| &7/korumakapat ile kapat"));
            // Süre bittiyse bildir
            if (kalan < 5000L && kalan > 0L) {
                p.sendTitle(color("&#ff4444&lKoruma Bitiyor!"), color("&#ffcc00Artık savunmasız olacaksın"), 3, 30, 10);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
            }
        }
    }

    // ─────────────────────────────────────────────
    //  KUMAR MENÜSÜ AYARLARI
    // ─────────────────────────────────────────────
    private void openKumarMenuSettings(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&#ffcc00&lKumar Menüsü Ayarları"));
        ItemStack fill = makeBorderGlass("&#0f0f1e");
        for (int i = 0; i < 54; i++) inv.setItem(i, fill);

        ItemStack title = new ItemStack(Material.NETHER_STAR); ItemMeta tm = title.getItemMeta();
        tm.setDisplayName(color("&#ffcc00&lKumar Hızı Seç"));
        List<String> tl = new ArrayList<>();
        tl.add(color("  &#555577▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        tl.add(color("  &#ffffff&lKasayı açtığında animasyonun"));
        tl.add(color("  &#aaaaaa&lne kadar süreceğini seç."));
        tl.add(color("  &#aaaaaa&oTüm kasalara uygulanır."));
        tl.add(color("  &#555577▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        tm.setLore(tl); title.setItemMeta(tm); inv.setItem(4, title);

        // SİMETRİK: 7 buton — 4 üst + 3 alt, ortada
        String[][] opts = {
            {"1 Saniye","20","Çok Hızlı","&#00ff88","LIME_STAINED_GLASS_PANE"},
            {"2 Saniye","40","Standart","&#00ccff","CYAN_STAINED_GLASS_PANE"},
            {"3 Saniye","60","Orta","&#4488ff","BLUE_STAINED_GLASS_PANE"},
            {"4 Saniye","80","Uzun","&#aa44ff","PURPLE_STAINED_GLASS_PANE"},
            {"5 Saniye","100","Epey Uzun","&#ff44ff","MAGENTA_STAINED_GLASS_PANE"},
            {"6 Saniye","120","Çok Uzun","&#ff8800","ORANGE_STAINED_GLASS_PANE"},
            {"7 Saniye","140","Maksimum","&#ff3333","RED_STAINED_GLASS_PANE"}};
        // Simetrik: 19,21,23,25 (üst 4) + 29,31,33 (alt 3)
        int[] slots = {19, 21, 23, 25, 29, 31, 33};

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
            l.add(color("  &7Durum: &#aaaaaa" + (i == 1 ? "&#00ff88Varsayılan" : "Pasif")));
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

        Inventory inv = Bukkit.createInventory(null, 54, color("&#aa44ff&lEfekt Secimi"));
        ItemStack fill = makeBorderGlass("&#0d0d1a");
        for (int i = 0; i < 54; i++) inv.setItem(i, fill);

        ItemStack header = new ItemStack(Material.AMETHYST_SHARD); ItemMeta hm = header.getItemMeta();
        hm.setDisplayName(color("&#aa44ff&lEfekt Seç &8(" + ALL_EFFECTS.length + " efekt)"));
        List<String> hl = new ArrayList<>();
        hl.add(color("  &#553388▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        hl.add(color("  &#ffffff&lKasa etrafında görünecek efekti seç"));
        hl.add(color("  &#aaaaaa&oUygulama: " + (preKasa.isEmpty() ? "&#ffcc00Tüm Kasalar" : "&#00ccff" + capitalize(preKasa) + " Kasası")));
        hl.add(color("  &#aaaaaa&oSayfa: " + (page + 1) + "/" + totalPages));
        hl.add(color("  &#553388▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        hm.setLore(hl); header.setItemMeta(hm); inv.setItem(4, header);

        // 4 satır: 10-16, 19-25, 28-34, 37-43
        int[] slots2 = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};

        int start = page * perPage;
        for (int i = 0; i < perPage && (start + i) < ALL_EFFECTS.length; i++) {
            int idx = start + i;
            String id    = (String)   ALL_EFFECTS[idx][0];
            String label = (String)   ALL_EFFECTS[idx][1];
            String clr2  = (String)   ALL_EFFECTS[idx][2];
            String desc  = (String)   ALL_EFFECTS[idx][3];
            Material mat = (Material) ALL_EFFECTS[idx][4];
            ItemStack btn = new ItemStack(mat); ItemMeta bm = btn.getItemMeta();
            bm.setDisplayName(color(clr2 + label));
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
            pm.setDisplayName(color("&#ffcc00&lÖnceki Sayfa"));
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
            nm.setDisplayName(color("&#ffcc00&lSonraki Sayfa"));
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
        String[] override = crateColorOverride.get(id.toLowerCase());
        if (override != null) return override;
        return CRATE_COLORS.getOrDefault(id.toLowerCase(), new String[]{"&#ffffff","&#dddddd","&#aaaaaa","&#dddddd","&#ffffff","&#cccccc"});
    }

    /** "#ff0000,00ff00,rgb(0,0,255)" gibi girdileri renk kodu dizisine çevirir. */
    private String[] parseColorList(String raw) {
        String[] parts = raw.split(",");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            String s = part.trim();
            if (s.isEmpty()) continue;
            String hex = null;
            // rgb(r,g,b) formatı
            java.util.regex.Matcher rgbM = java.util.regex.Pattern.compile("(?i)rgb\\((\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\)").matcher(s);
            if (rgbM.matches()) {
                int r = Math.min(255, Integer.parseInt(rgbM.group(1)));
                int g = Math.min(255, Integer.parseInt(rgbM.group(2)));
                int b = Math.min(255, Integer.parseInt(rgbM.group(3)));
                hex = String.format("#%02x%02x%02x", r, g, b);
            } else {
                String clean = s.startsWith("#") ? s.substring(1) : (s.startsWith("&#") ? s.substring(2) : s);
                if (clean.matches("(?i)[0-9a-f]{6}")) hex = "#" + clean.toLowerCase();
            }
            if (hex != null) out.add("&" + hex);
        }
        return out.isEmpty() ? null : out.toArray(new String[0]);
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
        inv.setItem(22, createBtn(Material.EXPERIENCE_BOTTLE, "&#ffcc00&lSeviye 8 Kasası"));
        inv.setItem(31, makeCloseHead(p));
        p.openInventory(inv);
    }

    private List<String> standardKeyLore() {
        List<String> lore = new ArrayList<>();
        lore.add(color(" ")); // ilk satır boşluk
        lore.add(color("&#9AFFBEKasa anahtarını kullanmak için &#9AFFBE/warp kasalar"));
        lore.add(color("&#9AFFBEbölümüne giderek anahtarlarınızı kullana bilirsiniz."));
        return lore;
    }

    private ItemStack createKey(Material m, String id) {
        ItemStack item = new ItemStack(Material.FEATHER); ItemMeta meta = item.getItemMeta();
        String keyName = KEY_BASE_NAMES.getOrDefault(id, id);
        meta.setDisplayName(getKeyAnimText(keyName, id, 0));
        meta.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, id);
        meta.setLore(standardKeyLore());
        item.setItemMeta(meta); return item;
    }

    private ItemStack buildPhysicalKey(String id) {
        String baseName = KEY_BASE_NAMES.getOrDefault(id, capitalize(id) + " Key");
        ItemStack item = new ItemStack(Material.FEATHER); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(getKeyAnimText(baseName, id, 0));
        meta.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, id);
        meta.setLore(standardKeyLore());
        item.setItemMeta(meta); return item;
    }

    // ─────────────────────────────────────────────
    //  KUMAR MENÜSÜ — AÇILIŞ (ses iyileştirildi)
    // ─────────────────────────────────────────────
    private int pickRewardIndex(String crateName, List<ItemStack> pool) {
        float total = 0f; List<Float> weights = new ArrayList<>();
        for (int i = 0; i < pool.size(); i++) { float w = crateChanceOf(crateName, i); weights.add(w); total += w; }
        float roll = new Random().nextFloat() * total, cumul = 0f;
        for (int i = 0; i < pool.size(); i++) { cumul += weights.get(i); if (roll <= cumul) return i; }
        return pool.size() - 1;
    }

    /** Ödülün listede görünen çıkma şansı; ayarlanmamışsa varsayılan 10. */
    private float crateChanceOf(String crateName, int idx) {
        List<Float> chs = crateChances.getOrDefault(crateName, new ArrayList<>());
        return (idx >= 0 && idx < chs.size()) ? chs.get(idx) : 10f;
    }

    private void openSpinMenu(Player p, String crateName, List<ItemStack> pool, ItemStack reward, float chance) {
        String title = color(getBoldColorTitle(crateName, capitalize(crateName) + " Kasası"));
        Inventory inv = Bukkit.createInventory(null, SPIN_SLOTS, title);
        fillSpinInventory(inv, pool, crateName);
        int dur = crateSpinDuration.getOrDefault(crateName, DEFAULT_SPIN_TICKS);
        activeSessions.put(p.getUniqueId(), new CrateSession(inv, reward, crateName, dur, chance));
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
        p.sendMessage(crateWinMessage(s.chance, capitalize(s.crateName), getName(s.reward)));
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) return;
                Map<Integer, ItemStack> leftover = p.getInventory().addItem(s.reward.clone());
                for (ItemStack rem : leftover.values()) p.getWorld().dropItem(p.getLocation(), rem);
                p.closeInventory(); activeSessions.remove(p.getUniqueId());
            }
        }.runTaskLater(this, 42L);
    }

    /**
     * Kazanılan itemin çıkma şansına göre tek satırlık kazanma mesajı.
     * Eşikler: %100 ve üzeri, %50 ve üzeri, %30 ve üzeri, daha düşükler.
     */
    private String crateWinMessage(float chance, String crateName, String itemName) {
        String lead, tone;
        if (chance >= 100f)     { lead = "Şansını sikim!";   tone = "&#98a2b3"; }
        else if (chance >= 50f) { lead = "Eh işte!";         tone = "&#d9a85b"; }
        else if (chance >= 30f) { lead = "Yaklaştın ustam!"; tone = "&#7aa2d8"; }
        else                    { lead = "Helal olsun usta!"; tone = "&#6fbf8f"; }
        return color(tone + lead + " " + UI_TEXT + crateName + tone + " kasasından "
            + UI_TEXT + itemName + tone + " kazandın.");
    }

    // ─────────────────────────────────────────────
    //  KASA ÖNİZLEME — Şans → Çıkma Şansı
    // ─────────────────────────────────────────────
    private void openCratePreview(Player p, String crateName) {
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
        sm.setDisplayName(color(UI_BAD + "Kapat"));
        sm.setLore(Collections.singletonList(color(UI_SUB + "Menüyü kapatmak için tıkla.")));
        head.setItemMeta(sm); return head;
    }

    // ─────────────────────────────────────────────
    //  BİLGİ MENÜSÜ
    // ─────────────────────────────────────────────
    private void openInfoMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&#00ccff&lLoginX Bilgi Merkezi"));
        ItemStack fill = makeBorderGlass("&#050520");
        for (int i = 0; i < 54; i++) inv.setItem(i, fill);

        ItemStack info = new ItemStack(Material.NETHER_STAR); ItemMeta im = info.getItemMeta();
        im.setDisplayName(color("&#ffcc00&l&r&#fff44f&l Fear Craft LoginX &r&#ffcc00&l"));
        List<String> il = new ArrayList<>();
        il.add(color("  &#334466▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        il.add(color(""));
        il.add(color("    &#ffffff&lKasa & Anahtar Sistemi"));
        il.add(color("    &#aaaaaa&oFear Craft için geliştirilmiştir"));
        il.add(color(""));
        il.add(color("  &#334466▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        il.add(color("  &#00ff88&fKasaları &b&lsağ tıkla &file aç"));
        il.add(color("  &#00ff88&fKasaları &b&lsol tıkla &fönizle"));
        il.add(color("  &#00ff88&fHer kasanın kendine özel anahtarı var"));
        il.add(color("  &#00ff88&f&lKey All &rherhangi bir kasayı açar"));
        il.add(color("  &#00ff88&fŞans ayarla: &e/kasasansayarla"));
        il.add(color("  &#00ff88&fAnahtar ver: &e/kasakeyver"));
        il.add(color("  &#00ff88&fEfekt seç: &e/kasafarkliefektler"));
        il.add(color(""));
        il.add(color("  &#334466▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        il.add(color("    &#ffcc00&7Toplam Kasa: &f" + crates.size()));
        il.add(color("  &#334466▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        im.setLore(il); info.setItemMeta(im); inv.setItem(4, info);

        // SİMETRİK düzen: 7 buton — 29,30,31,32,33 + 20,24
        Object[][] sections = {
            {"&#ff6600","&#ff8833","KASALAR","Sunucudaki tüm kasalar\nhakkında detaylı bilgi al.\nKasa sayısı, itemler ve efektler.",Material.CHEST,"KASALAR"},
            {"&#00ccff","&#44ddff","ANAHTARLAR","Hangi anahtar hangi kasayı\naçar öğren ve envantere al.\nKey All herkesi açar!",Material.TRIPWIRE_HOOK,"ANAHTARLAR"},
            {"&#aa44ff","&#cc77ff","EFEKTLER","Kasalardaki partikül efektlerini\ngörüntüle ve değiştir.\n7 farklı efekt seçeneği.",Material.FIREWORK_ROCKET,"EFX"},
            {"&#ffdd00","&#ffee44","ŞANS","Her item için şans oranlarını\ngörüntüle ve düzenle.\nNadirlik sistemi açıklaması.",Material.GOLDEN_APPLE,"SANS"},
            {"&#ff2244","&#ff5566","KUMAR","Kumar menüsü nasıl çalışır?\nSpin süresi ve hız ayarları.\nOyun mekaniği anlatımı.",Material.RED_DYE,"KUMAR"},
            {"&#00ff88","&#44ffaa","KOMUTLAR","Tüm admin komutlarını\nlistele ve açıklamalarını gör.\nEksiksiz komut rehberi.",Material.COMMAND_BLOCK,"CMD"},
            {"&#aaaaaa","&#cccccc","DESTEK","Yardım için admin\nile iletişime geç.\nHata bildirme ve öneriler.",Material.BOOK,"INFO"}
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
        inv.setItem(45, createBtn(Material.BOOK, "&#00ccff&lİade Geçmişi", "IADE_LOG"));
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
        Inventory inv = Bukkit.createInventory(null, 54, color("&#00ccff&lİade Geçmişi"));
        ItemStack fill = makeBorderGlass("&#050520");
        for (int i = 0; i < 54; i++) inv.setItem(i, fill);

        // Başlık
        ItemStack header = new ItemStack(Material.BOOK); ItemMeta hm = header.getItemMeta();
        hm.setDisplayName(color("&#00ccff&lİade Geçmişi"));
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
        bm.setDisplayName(color("&#ffcc00&fGeri Dön"));
        back.setItemMeta(bm); inv.setItem(49, back);
        p.openInventory(inv);
    }

    // ─────────────────────────────────────────────
    //  GÜNLÜK ÖDÜL SİSTEMİ
    // ─────────────────────────────────────────────

    /** O güne ait ödülleri döndürür: kasaId -> adet. Sıra korunur (LinkedHashMap). */
    private LinkedHashMap<String, Integer> dailyRewardContentsForDay(int day) {
        LinkedHashMap<String, Integer> m = new LinkedHashMap<>();
        switch (day) {
            case 1 -> m.put("event", 1);                      // Gün 1: Event Kasası
            case 2 -> { m.put("vote", 1); m.put("event", 1); } // Gün 2: Vote Kasası + Event Kasası
            case 3 -> m.put("vote", 1);                        // Gün 3: Vote Kasası
            case 4 -> m.put("event", 2);                       // Gün 4: 2x Event Kasası
            case 5 -> m.put("vote", 2);                        // Gün 5: 2x Vote Kasası
            case 6 -> { m.put("event", 1); m.put("vote", 1); } // Gün 6: Event Kasası + Vote Kasası
            case 7 -> m.put("kit", 2);                         // Son gün: 2x Kit Kasası
            default -> {}
        }
        return m;
    }

    private String dailyRewardKasaDisplayName(String kasaId) {
        return switch (kasaId) {
            case "event" -> "Event Kasası";
            case "vote"  -> "Vote Kasası";
            case "kit"   -> "Kit Kasası";
            default -> capitalize(kasaId) + " Kasası";
        };
    }


    /**
     * Günlük ödül anahtarı inşa eder — görünüşü "Ominous Bottle", adı doğrudan hangi kasayı
     * açtığıdır (örn. "Kit Kasası", "Event Kasası", "Vote Kasası" — /kitkasası ile aynı isimlendirme).
     * Mevcut kasa anahtarı sistemiyle (keyTag) tam uyumludur; sunucuda /kasaayarla event | vote | kit
     * isimli kasalar kurulduğunda bu anahtarlar o kasaları doğrudan açabilir.
     */
    private ItemStack buildDailyRewardKey(String kasaId, int amount) {
        String kasaName = dailyRewardKasaDisplayName(kasaId);
        ItemStack item = new ItemStack(Material.FEATHER, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&r" + getCrateColors(kasaId)[0] + kasaName));
        meta.setLore(standardKeyLore());
        meta.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, kasaId);
        item.setItemMeta(meta);
        return item;
    }

    private int getDailyRewardDay(UUID uid) {
        int d = getConfig().getInt("dailyreward." + uid + ".day", 1);
        if (d < 1 || d > DAILY_REWARD_TOTAL_DAYS) d = 1;
        return d;
    }

    private long getDailyRewardNextClaim(UUID uid) {
        return getConfig().getLong("dailyreward." + uid + ".nextclaim", 0L);
    }

    private void setDailyRewardState(UUID uid, int day, long nextClaim) {
        getConfig().set("dailyreward." + uid + ".day", day);
        getConfig().set("dailyreward." + uid + ".nextclaim", nextClaim);
        saveConfig();
    }

    /** ms cinsinden süreyi "Xs Yd Zsn" formatına çevirir. */
    private String formatDuration(long ms) {
        if (ms < 0) ms = 0;
        long saat = ms / 3_600_000L;
        long dak  = (ms % 3_600_000L) / 60_000L;
        long sn   = (ms % 60_000L) / 1_000L;
        StringBuilder sb = new StringBuilder();
        if (saat > 0) sb.append(saat).append("s ");
        if (dak > 0 || saat > 0) sb.append(dak).append("d ");
        sb.append(sn).append("sn");
        return sb.toString();
    }

    private ItemStack dailyRewardGlassFiller() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta();
        m.setDisplayName(color("&r"));
        pane.setItemMeta(m);
        return pane;
    }

    /** Günlük ödül menüsünü açar/yeniler. Küçük, simetrik 27 slotluk tasarım. */
    /** Günlük ödül menüsünü açar/yeniler. 27 slot: üst ortada bilgi, ortada 7 gün, alt ortada kapat. */
    private void openDailyRewardMenu(Player p) {
        UUID uid = p.getUniqueId();
        int currentDay = getDailyRewardDay(uid);
        long nextClaim = getDailyRewardNextClaim(uid);
        long now = System.currentTimeMillis();
        boolean availableToday = now >= nextClaim;

        Inventory inv = Bukkit.createInventory(null, 27, color(UI_SUB + "Günlük Ödül"));
        ItemStack filler = uiFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        String status = availableToday
            ? UI_OK + "Ödül alınabilir"
            : UI_WARN + "Bekleniyor: " + UI_TEXT + formatDuration(nextClaim - now);
        inv.setItem(4, uiItem(Material.BOOK, UI_TEXT + "Günlük Ödül",
            UI_SUB + "Gün: " + UI_TEXT + currentDay + UI_SUB + " / " + DAILY_REWARD_TOTAL_DAYS,
            status,
            "",
            UI_SUB + "Her gün giriş yapıp sıradaki ödülünü alabilirsin.",
            UI_SUB + "Günler sırayla açılır, birini atlayamazsın.",
            UI_SUB + "Her ödülden sonra 20 saat beklemen gerekir.",
            "",
            UI_SUB + DAILY_REWARD_TOTAL_DAYS + ". günün ödülünü alınca döngü baştan başlar."));

        int[] daySlots = {10, 11, 12, 13, 14, 15, 16};
        for (int day = 1; day <= DAILY_REWARD_TOTAL_DAYS; day++) {
            inv.setItem(daySlots[day - 1], buildDailyRewardDayIcon(day, currentDay, availableToday, now, nextClaim));
        }

        inv.setItem(22, uiItem(Material.BARRIER, UI_BAD + "Kapat"));
        p.openInventory(inv);
    }

    private ItemStack buildDailyRewardDayIcon(int day, int currentDay, boolean availableToday, long now, long nextClaim) {
        boolean isLastDay = day == DAILY_REWARD_TOTAL_DAYS;
        LinkedHashMap<String, Integer> contents = dailyRewardContentsForDay(day);

        Material mat;
        String nameColor;
        String status;
        boolean clickable = false;

        if (day < currentDay) {
            mat = Material.TRIAL_SPAWNER;
            nameColor = UI_SUB;
            status = UI_SUB + "Alındı";
        } else if (day == currentDay) {
            nameColor = UI_TEXT;
            if (availableToday) {
                mat = isLastDay ? Material.ENDER_CHEST : Material.CHEST;
                status = UI_OK + "Hazır, almak için tıkla";
                clickable = true;
            } else {
                mat = Material.CLOCK;
                status = UI_WARN + "Bekleniyor: " + UI_TEXT + formatDuration(nextClaim - now);
            }
        } else {
            mat = Material.IRON_BARS;
            nameColor = UI_DIM;
            status = UI_DIM + "Kilitli";
        }

        String name = nameColor + "Gün " + day + (isLastDay ? UI_DIM + " | " + UI_ACCENT + "Son gün" : "");

        List<String> lore = new ArrayList<>();
        lore.add(status);
        lore.add("");
        if (day < currentDay) {
            lore.add(UI_SUB + "Bu günün ödülünü aldın.");
        } else if (day == currentDay && availableToday) {
            lore.add(UI_SUB + "Ödülün hazır. Tıkladığında anahtarlar");
            lore.add(UI_SUB + "doğrudan envanterine eklenir.");
        } else if (day == currentDay) {
            lore.add(UI_SUB + "Önceki ödülü yeni aldın. Süre dolunca");
            lore.add(UI_SUB + "bu menüden bunu da alabilirsin.");
        } else {
            lore.add(UI_SUB + "Bu güne gelmek için önceki günlerin");
            lore.add(UI_SUB + "ödüllerini almış olman gerekiyor.");
        }
        if (isLastDay) lore.add(UI_SUB + "Son gün. Aldıktan sonra Gün 1'e dönersin.");
        lore.add("");
        lore.add(UI_SUB + "Ödül");
        for (Map.Entry<String, Integer> ent : contents.entrySet()) {
            lore.add(UI_TEXT + "  " + ent.getValue() + "x " + dailyRewardKasaDisplayName(ent.getKey()));
        }

        ItemStack item = uiItem(mat, name, lore.toArray(new String[0]));
        ItemMeta meta = item.getItemMeta();
        if (clickable) meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        meta.getPersistentDataContainer().set(dailyRewardDayTag, PersistentDataType.INTEGER, day);
        item.setItemMeta(meta);
        return item;
    }

    /** /duel berabere — SADECE dueldeyken kullanılabilir. Rakibine berabere teklifi gönderir. */
    private void handleDuelBerabereCommand(Player p) {
        String arenaId = playerDuel.get(p.getUniqueId());
        if (arenaId == null) { p.sendMessage(duelErr("Bu komutu yalnızca dueldeyken kullanabilirsin.")); return; }
        UUID[] dp = duelPlayers.get(arenaId);
        if (dp == null) { p.sendMessage(duelErr("Duel bulunamadı.")); return; }
        Player opponent = null;
        for (UUID u : dp) if (!u.equals(p.getUniqueId())) opponent = Bukkit.getPlayer(u);
        if (opponent == null || !opponent.isOnline()) { p.sendMessage(duelErr("Rakip çevrimdışı.")); return; }

        duelDrawRequest.put(opponent.getUniqueId(), p.getUniqueId());

        net.md_5.bungee.api.chat.TextComponent btnDrawKabul = new net.md_5.bungee.api.chat.TextComponent(
            color(UI_OK + "[Kabul Et]"));
        btnDrawKabul.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/duelberaberekabul"));
        net.md_5.bungee.api.chat.TextComponent spaceDraw = new net.md_5.bungee.api.chat.TextComponent("  ");
        net.md_5.bungee.api.chat.TextComponent btnDrawReddet = new net.md_5.bungee.api.chat.TextComponent(
            color(UI_BAD + "[Reddet]"));
        btnDrawReddet.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/duelberaberereddet"));

        opponent.sendMessage(duel(hl(p.getName()) + " berabere teklif ediyor."));
        opponent.spigot().sendMessage(btnDrawKabul, spaceDraw, btnDrawReddet);
        opponent.playSound(opponent.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.4f);
        p.sendMessage(duel("Berabere teklifin " + hl(opponent.getName()) + " oyuncusuna gönderildi."));
    }

    /** Günlük ödül menüsünde bir güne tıklanınca çalışır; yalnızca sıradaki gün ve süre uygunsa ödül verir. */
    private void handleDailyRewardClick(Player p, int clickedDay) {
        UUID uid = p.getUniqueId();
        int currentDay = getDailyRewardDay(uid);
        long nextClaim = getDailyRewardNextClaim(uid);
        long now = System.currentTimeMillis();

        if (clickedDay != currentDay || now < nextClaim) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return;
        }

        LinkedHashMap<String, Integer> contents = dailyRewardContentsForDay(clickedDay);
        for (Map.Entry<String, Integer> ent : contents.entrySet()) {
            ItemStack key = buildDailyRewardKey(ent.getKey(), ent.getValue());
            Map<Integer, ItemStack> leftover = p.getInventory().addItem(key);
            for (ItemStack rem : leftover.values()) p.getWorld().dropItem(p.getLocation(), rem);
        }

        boolean wasLastDay = clickedDay == DAILY_REWARD_TOTAL_DAYS;
        int newDay = wasLastDay ? 1 : clickedDay + 1;
        setDailyRewardState(uid, newDay, now + DAILY_REWARD_COOLDOWN_MS);

        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        if (wasLastDay) {
            p.sendMessage(uiMsg("Günlük Ödül", "Son günün ödülünü aldın. Döngü Gün 1'den yeniden başlayacak."));
        } else {
            p.sendMessage(uiMsg("Günlük Ödül", "Gün " + hl(String.valueOf(clickedDay)) + " ödülün envanterine eklendi."));
        }

        openDailyRewardMenu(p);
    }

    // ─────────────────────────────────────────────
    //  /kitkasası — TEK SEFERLİK, IP KORUMALI ÖDÜL
    // ─────────────────────────────────────────────

    /** /kitkasası komutunu işler: OP oyuncular sınırsız kullanır; normal oyuncularda tek hesap + tek IP başına tek seferlik, kalıcı olarak config'e kaydedilir. */
    private void handleKitKasasiCommand(Player p) {
        UUID uid = p.getUniqueId();

        // ── OP oyuncular için sınır yok — hiçbir kontrol/kayıt yapılmadan direkt ödül verilir ──
        if (p.isOp()) {
            ItemStack opKey = buildDailyRewardKey("kit", 1);
            Map<Integer, ItemStack> opLeftover = p.getInventory().addItem(opKey);
            for (ItemStack rem : opLeftover.values()) p.getWorld().dropItem(p.getLocation(), rem);

            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            p.sendMessage(uiMsg("Kit Kasası", "Kit Kasası anahtarı envanterine eklendi."));
            return;
        }

        // Zaten bu hesapla almış mı? (restart/relog bağımsız — kalıcı config)
        boolean alreadyClaimedByAccount = getConfig().getBoolean("kitkasasi.claimed." + uid, false);
        if (alreadyClaimedByAccount) {
            p.sendMessage(uiErr("Kit Kasası", "Bu ödülü daha önce aldın."));
            return;
        }

        // IP kontrolü — aynı IP'den daha önce başka bir hesap almış mı?
        String ip = (p.getAddress() != null && p.getAddress().getAddress() != null)
            ? p.getAddress().getAddress().getHostAddress() : null;
        if (ip == null) {
            p.sendMessage(uiErr("Kit Kasası", "IP adresin doğrulanamadı. Tekrar dene."));
            return;
        }
        String ipKey = "kitkasasi.ip." + ip.replace(".", "_");
        String ipOwner = getConfig().getString(ipKey, null);
        if (ipOwner != null && !ipOwner.equalsIgnoreCase(p.getName())) {
            p.sendMessage(uiErr("Kit Kasası", "Bu IP adresinden bir hesap ödülü zaten almış."));
            return;
        }

        // Her şey uygun — ödülü ver ve kalıcı olarak işaretle
        ItemStack key = buildDailyRewardKey("kit", 1);
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(key);
        for (ItemStack rem : leftover.values()) p.getWorld().dropItem(p.getLocation(), rem);

        getConfig().set("kitkasasi.claimed." + uid, true);
        getConfig().set(ipKey, p.getName());
        saveConfig();

        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        p.sendMessage(uiMsg("Kit Kasası", "Kit Kasası anahtarı envanterine eklendi. Bu ödül tek seferliktir."));
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
            || title.contains("Crate") || title.contains("Olum Detayi")
            || title.contains("Kumar") || title.contains("Efekt Secimi") || title.contains("Bilgi Merkezi")
            || title.contains("Hile Kontrol") || title.contains("Duel Arenaları") || title.contains("Duel Ayarları")
            || title.contains("Duel Daveti")
            || title.contains("İzin Yönetimi") || title.contains("İade Geçmişi")
            || title.contains("Günlük Ödül");
        if (!managed) return;
        // Günlük Ödül menüsünde: itemler kesinlikle alınamaz, taşınamaz, sürüklenemez.
        // Sol tık, sağ tık, shift-click, hotbar-swap, drop — hepsi bu satırla tamamen iptal edilir.
        e.setCancelled(true);
        if (title.contains("Günlük Ödül")) {
            p.updateInventory();
            if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
            Integer clickedDay = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(dailyRewardDayTag, PersistentDataType.INTEGER);
            if (clickedDay == null) {
                if (e.getCurrentItem().getType() == Material.BARRIER) p.closeInventory();
                return; // cam / bilgi itemi — hiçbir işlem yapma
            }
            handleDailyRewardClick(p, clickedDay);
            return;
        }
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
                                giveIadeAuto(p, t, dr);
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
            String arenaTag = duelGuiValue(ci2);
            if (arenaTag == null || !arenaTag.startsWith("arena:")) return;
            String duelId = arenaTag.substring(6);
            if (duelPlayers.containsKey(duelId)) { p.sendMessage(duelErr("Bu arena dolu.")); return; }
            if (duelSpawn1.get(duelId) == null || duelSpawn2.get(duelId) == null) { p.sendMessage(duelErr("Bu arena henüz hazır değil.")); return; }
            p.closeInventory();
            p.sendMessage(duel("Arena " + hl(duelId) + " için davet göndermek üzere " + hl("/1v1 <oyuncu>") + " komutunu kullan."));
            return;
        }

        // Duel ayarlar menüsü (/duelayarlar, /duelayarlaradmin) ve /duel <oyuncu> ön-ayar menüsü
        if (title.contains("Duel Ayarları") || title.contains("Duel Daveti")) {
            boolean isInviteMenu = title.contains("Duel Daveti");
            ItemStack ci3 = e.getCurrentItem();
            if (ci3 == null || !ci3.hasItemMeta()) return;
            String btnTag = duelGuiValue(ci3);
            if (btnTag == null) return;

            // Onayla / Gönder butonu (sadece davet menüsünde)
            if (isInviteMenu && btnTag.equals("confirm")) {
                UUID targetUid = duelPendingTarget.remove(p.getUniqueId());
                p.closeInventory();
                if (targetUid == null) { p.sendMessage(duelErr("Davet süresi doldu. Tekrar dene.")); return; }
                Player target = Bukkit.getPlayer(targetUid);
                sendDuelInvite(p, target, "hedef oyuncu");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.3f);
                return;
            }

            if (!btnTag.startsWith("toggle:")) return;
            String toggle = btnTag.substring(7);

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
                if (target == null) { p.sendMessage(duelErr("Oyuncu çevrimdışı oldu.")); return; }
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
                    giveIadeAuto(p, t, dr);
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
                p.sendActionBar(color(UI_BAD + "Kasa açılırken menüyü kapatamazsın."));
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
                p.sendMessage(color("&#ffcc00&l  Kasalar &8(" + crates.size() + " adet)"));
                p.sendMessage(color("&#ffcc00&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                for (String k : crates.keySet()) p.sendMessage(color("  &#00ff88&f" + capitalize(k) + " &8│ &7" + crateItems.getOrDefault(k, new ArrayList<>()).size() + " eşya │ Efekt: &e" + crateEffectType.getOrDefault(k, "varsayilan")));
                p.sendMessage(color("&#ffcc00&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            }
            case "ANAHTARLAR" -> {
                p.sendMessage(color("&#00ccff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("&#00ccff&l  Anahtarlar"));
                p.sendMessage(color("&#00ccff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                for (Map.Entry<String, String> en : KEY_BASE_NAMES.entrySet()) p.sendMessage(color("  &#00ccff&f" + en.getValue() + " &8→ &e" + capitalize(en.getKey()) + " Kasası"));
                p.sendMessage(color("&#00ccff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            }
            case "EFX" -> {
                p.sendMessage(color("&#aa44ff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("&#aa44ff&l  Efektler"));
                p.sendMessage(color("&#aa44ff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("  &7varsayilan, fireworks, temiz, yildiz, kristal, alev, buz"));
                p.sendMessage(color("  &8» &e/kasafarkliefektler [kasa_ismi]"));
                p.sendMessage(color("&#aa44ff&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            }
            case "SANS" -> {
                p.sendMessage(color("&#ffdd00&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("&#ffdd00&l  Çıkma Şansı Sistemi"));
                p.sendMessage(color("&#ffdd00&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("  &8» &e/kasasansayarla &f<kasa> &b<#sira> &a<sans>"));
                p.sendMessage(color("  &#ff2244&f%0-1   = Efsanevi  | &#aa00ff&f%1-3   = Epik"));
                p.sendMessage(color("  &#4488ff&f%3-7   = Nadir     | &#00cc44&f%7-15  = Yayginsiz"));
                p.sendMessage(color("  &#aaaaaa&f%15+   = Yaygın"));
                p.sendMessage(color("&#ffdd00&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            }
            case "KUMAR" -> {
                p.sendMessage(color("&#ff2244&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("&#ff2244&l  Kumar Sistemi"));
                p.sendMessage(color("&#ff2244&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("  &7Kasa açıldığında spin animasyonu başlar."));
                p.sendMessage(color("  &7Hızdan yavaşa gider, ortadaki eşyayı kazanırsın."));
                p.sendMessage(color("  &8» &e/kasakumarmenu ayarla &7ile süreyi değiştir."));
                p.sendMessage(color("&#ff2244&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            }
            case "CMD" -> {
                p.sendMessage(color("&#00ff88&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("&#00ff88&l  Admin Komutları"));
                p.sendMessage(color("&#00ff88&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                for (String c : new String[]{"/kasaayarla <isim>","/kasasil <isim>","/kasaicsil <kasa> #<sira>","/icineitemkoy <kasa>","/kasaidlist <kasa>","/kasasansayarla <kasa> #<sira> <sans>","/kasakumarmenu ayarla","/kasakeyver <oyuncu|all> <kasa>","/kasafarkliefektler [kasa]","/kasabilgi","/1v1wand","/korumasurem","/korumakapat"})
                    p.sendMessage(color("  &#00ff88&7" + c));
                p.sendMessage(color("&#00ff88&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            }
            case "INFO" -> {
                p.sendMessage(color("&#aaaaaa&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                p.sendMessage(color("&#aaaaaa&l  Destek"));
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
        if (target == null || target.equals(p)) { p.sendMessage(duelErr(rawName + " çevrimiçi değil.")); return; }
        if (playerDuel.containsKey(p.getUniqueId())) { p.sendMessage(duelErr("Zaten bir dueldasın.")); return; }
        if (playerDuel.containsKey(target.getUniqueId())) { p.sendMessage(duelErr(target.getName() + " zaten bir duelda.")); return; }

        // ── Tekrar atma engeli: hedefte zaten davet varsa iptal et ──
        if (duelInvite.containsKey(target.getUniqueId())) {
            UUID existing = duelInvite.get(target.getUniqueId());
            if (existing.equals(p.getUniqueId())) {
                p.sendMessage(duelErr("Bu oyuncuya zaten davet gönderdin. Yanıtını bekle."));
                return;
            }
        }

        String freeArena = findFreeDuelArena();
        if (freeArena == null) { p.sendMessage(duelErr("Şu an boş arena yok.")); return; }

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
                if (p.isOnline()) p.sendMessage(duel(hl(target.getName()) + " daveti yanıtlamadı, süre doldu."));
                if (target.isOnline()) target.sendMessage(duel(hl(p.getName()) + " ile duel davetinin süresi doldu."));
            }
        }.runTaskLater(this, 20L * 30).getTaskId();
        duelInviteTask.put(target.getUniqueId(), tid);

        // Kabul / Reddet düğmeleri
        net.md_5.bungee.api.chat.TextComponent btnKabul = new net.md_5.bungee.api.chat.TextComponent(color(UI_OK + "[Kabul Et]"));
        btnKabul.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/1v1kabul"));
        btnKabul.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
            net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
            new net.md_5.bungee.api.chat.ComponentBuilder(color(UI_OK + "Duel davetini kabul et")).create()));

        net.md_5.bungee.api.chat.TextComponent space = new net.md_5.bungee.api.chat.TextComponent("  ");

        net.md_5.bungee.api.chat.TextComponent btnReddet = new net.md_5.bungee.api.chat.TextComponent(color(UI_BAD + "[Reddet]"));
        btnReddet.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/1v1reddet"));
        btnReddet.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
            net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
            new net.md_5.bungee.api.chat.ComponentBuilder(color(UI_BAD + "Duel davetini reddet")).create()));

        String sep = UI_DIM + " | ";
        target.sendMessage(duel(hl(p.getName()) + " seni duele davet etti."));
        target.sendMessage(color(UI_SUB + "Örümcek Ağı: " + duelRuleState(webOn) + sep + UI_SUB + "Elytra: " + duelRuleState(elyOn)
            + sep + UI_SUB + "Merdiven: " + duelRuleState(ladderOn)));
        target.sendMessage(color(UI_SUB + "Meşe Odun: " + duelRuleState(oakOn) + sep + UI_SUB + "Tahta: " + duelRuleState(stickOn)
            + sep + UI_SUB + "Göğüslük: " + duelRuleState(chestOn)));
        target.spigot().sendMessage(btnKabul, space, btnReddet);
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.4f);
        p.sendMessage(duel("Duel daveti " + hl(target.getName()) + " oyuncusuna gönderildi."));
    }

    // ─── Ortak arayüz paleti: Günlük Ödül, Bakım ve Duel aynı renkleri kullanır ───
    private static final String UI_TEXT   = "&#e4e7ec"; // ana metin
    private static final String UI_SUB    = "&#98a2b3"; // ikincil metin
    private static final String UI_DIM    = "&#667085"; // çerçeve / ayraç
    private static final String UI_ACCENT = "&#7aa2d8"; // vurgu
    private static final String UI_OK     = "&#6fbf8f"; // olumlu
    private static final String UI_WARN   = "&#d9a85b"; // bekleme / uyarı
    private static final String UI_BAD    = "&#d46a6a"; // olumsuz

    /** [Etiket] gövde — tek tip mesaj biçimi. */
    private String uiMsg(String tag, String body) {
        return color(UI_DIM + "[" + UI_ACCENT + tag + UI_DIM + "] " + UI_TEXT + body);
    }

    private String uiErr(String tag, String body) {
        return color(UI_DIM + "[" + UI_BAD + tag + UI_DIM + "] " + UI_TEXT + body);
    }

    private String duel(String body)    { return uiMsg("Duel", body); }
    private String duelErr(String body) { return uiErr("Duel", body); }

    /** Mesaj içinde vurgulu parça; sonrasında ana metin rengine döner. */
    private static String hl(String s) { return UI_ACCENT + s + UI_TEXT; }

    private String duelRuleState(boolean on) { return on ? UI_OK + "Açık" : UI_BAD + "Kapalı"; }

    private String duelGuiValue(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(duelGuiTag, PersistentDataType.STRING);
    }

    private String duelFlop(String text, boolean bold) {
        String stripped = org.bukkit.ChatColor.stripColor(color(text));
        return color(UI_ACCENT + (bold ? "&l" : "") + stripped);
    }

    /** Menülerde kullanılan sade gri dolgu camı. */
    private ItemStack uiFiller() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta();
        m.setDisplayName(" ");
        pane.setItemMeta(m);
        return pane;
    }

    /** İsim ve lore satırlarını italik olmayan, tek tip biçimde kuran item üretici. */
    private ItemStack uiItem(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(color("&r" + name));
        if (lore.length > 0) {
            List<String> l = new ArrayList<>();
            for (String x : lore) l.add(color("&r" + x));
            m.setLore(l);
        }
        m.addItemFlags(ItemFlag.values());
        it.setItemMeta(m);
        return it;
    }

    private void startDuel(Player p1, Player p2, String arenaId, boolean[] rules) {
        duelArenaRules.put(arenaId, rules);
        duelPlayers.put(arenaId, new UUID[]{p1.getUniqueId(), p2.getUniqueId()});
        playerDuel.put(p1.getUniqueId(), arenaId);
        playerDuel.put(p2.getUniqueId(), arenaId);
        duelFrozen.put(p1.getUniqueId(), true);
        duelFrozen.put(p2.getUniqueId(), true);
        // Bu maça özel token — arena aynı, ama eski maçların gecikmeli görevleri bu maçı etkilemesin
        final int myMatchToken = ++duelMatchCounter;
        duelMatchToken.put(arenaId, myMatchToken);

        Location s1 = duelSpawn1.get(arenaId);
        Location s2 = duelSpawn2.get(arenaId);
        hudTp(p1, s1); hudTp(p2, s2);

        for (Player dp : new Player[]{p1, p2}) {
            dp.setWalkSpeed(0f);
            dp.setFlySpeed(0f);
            dp.sendTitle(duelFlop("Duel", true), color(UI_SUB + "3 saniye sonra başlıyor"), 5, 60, 10);
            dp.sendMessage(duel("Rakip: " + hl(dp == p1 ? p2.getName() : p1.getName())));
            dp.sendMessage(color(UI_SUB + "Duel sırasında yalnızca " + UI_ACCENT + "/duel berabere" + UI_SUB + " komutu kullanılabilir."));
            dp.sendMessage(color(UI_SUB + "Oyundan çıkarsan maçı kaybedersin."));
            dp.playSound(dp.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
        }

        new BukkitRunnable() {
            int count = 3;
            @Override public void run() {
                if (count > 0) {
                    for (Player dp : new Player[]{p1, p2}) {
                        if (!dp.isOnline()) { cancel(); endDuelDisconnect(arenaId, dp); return; }
                        String countTitle = duelFlop(String.valueOf(count), true);
                        dp.sendTitle(countTitle, color(UI_SUB + "Hazırlan"), 0, 25, 5);
                        dp.playSound(dp.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, count == 1 ? 2f : 1f);
                    }
                    count--;
                } else {
                    cancel();
                    duelFrozen.remove(p1.getUniqueId()); duelFrozen.remove(p2.getUniqueId());
                    for (Player dp : new Player[]{p1, p2}) {
                        dp.setWalkSpeed(0.2f); dp.setFlySpeed(0.1f);
                        String fightTitle = duelFlop("Başla", true);
                        dp.sendTitle(fightTitle, color(UI_OK + "İyi şanslar"), 5, 40, 10);
                        dp.playSound(dp.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.8f);
                    }
                    // 5 dakika uyarısı
                    new BukkitRunnable() {
                        @Override public void run() {
                            Integer tok = duelMatchToken.get(arenaId);
                            if (tok == null || tok != myMatchToken) return; // arena başka bir maça geçmiş, eski görevi yok say
                            UUID[] dp2 = duelPlayers.get(arenaId);
                            if (dp2 == null) return; // Duel bitmişse gösterme
                            for (UUID uid : dp2) {
                                Player dp3 = Bukkit.getPlayer(uid);
                                if (dp3 != null && dp3.isOnline()) {
                                    dp3.sendMessage(duel("Duel 5 dakikadır sürüyor. 10 dakikada bitmezse berabere sayılır."));
                                    dp3.playSound(dp3.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.4f);
                                }
                            }
                        }
                    }.runTaskLater(LoginX.this, 20L * 300); // 5 dakika = 300 saniye

                    // ─── 10 dakikalık üst süre sınırı — bitmezse ikisini de duelspawnayarla'ya ışınla ───
                    new BukkitRunnable() {
                        @Override public void run() {
                            Integer tok = duelMatchToken.get(arenaId);
                            if (tok == null || tok != myMatchToken) return; // arena başka bir maça geçmiş, eski görevi yok say
                            UUID[] dp4 = duelPlayers.get(arenaId);
                            if (dp4 == null) return; // Duel zaten bitmiş
                            for (UUID uid : dp4) { playerDuel.remove(uid); duelFrozen.remove(uid); }
                            duelPlayers.remove(arenaId);
                            duelArenaRules.remove(arenaId);
                            duelMatchToken.remove(arenaId);
                            for (UUID uid : dp4) {
                                Player dp5 = Bukkit.getPlayer(uid);
                                if (dp5 != null && dp5.isOnline()) {
                                    dp5.sendTitle(
                                        duelFlop("Süre doldu", true),
                                        color(UI_SUB + "10 dakikada bitmedi, spawn noktasına ışınlanıyorsun."),
                                        5, 60, 15);
                                    dp5.sendMessage(duel("Duel 10 dakika sınırına ulaştı. Berabere sayıldı."));
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
        duelMatchToken.remove(arenaId);
        if (players == null) return;
        for (UUID u : players) { playerDuel.remove(u); duelFrozen.remove(u); }

        // ── Duel sırasında koyulan bloklar sessizce kaldırılsın ──
        java.util.Set<org.bukkit.block.Block> placed = duelPlacedBlocks.remove(arenaId);
        if (placed != null) {
            for (org.bukkit.block.Block b : placed) {
                if (b != null) b.setType(Material.AIR, false);
            }
        }

        String winnerName = winner != null ? winner.getName() : "?";
        String loserName  = loser  != null ? loser.getName()  : "?";

        // Tüm sunucuya duel sonucu
        for (Player on : Bukkit.getOnlinePlayers()) {
            on.sendMessage(duel(UI_OK + winnerName + UI_TEXT + " duelde " + UI_BAD + loserName + UI_TEXT + " oyuncusunu yendi."));
        }

        // Kaybedene
        if (loser != null && loser.isOnline()) {
            loser.setWalkSpeed(0.2f); loser.setFlySpeed(0.1f);
            loser.sendTitle(
                color(UI_BAD + "Kaybettin"),
                color(UI_SUB + "Kazanan: " + UI_TEXT + winnerName),
                5, 80, 20);
            loser.playSound(loser.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
            // Kaybeden hemen spawna
            teleportToSpawn(loser);
        }

        // Kazanana — 8sn geri sayım ile spawna
        if (winner != null && winner.isOnline()) {
            winner.setWalkSpeed(0.2f); winner.setFlySpeed(0.1f);
            winner.sendTitle(
                duelFlop("Kazandın", true),
                color(UI_SUB + loserName + " elendi"),
                5, 100, 20);
            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);

            // 8sn geri sayım
            new BukkitRunnable() {
                int sec = 8;
                @Override public void run() {
                    if (!winner.isOnline()) { cancel(); return; }
                    if (sec > 0) {
                        // Title'da geri sayım
                        winner.sendTitle(
                            duelFlop("Kazandın", true),
                            color(UI_SUB + "Spawn noktasına ışınlanmana " + UI_TEXT + sec + UI_SUB + " saniye"),
                            0, 25, 5);
                        winner.playSound(winner.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.5f);
                        sec--;
                    } else {
                        cancel();
                        winner.sendTitle(
                            duelFlop("Işınlandın", true),
                            color(UI_SUB + "İyi oyunlar"),
                            5, 50, 15);
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
    /** Oyuncunun envanterini 41 slot olarak yakalar: 0-35 ana envanter, 36-39 zırh (bot/pantolon/göğüs-elytra/kask), 40 offhand. */
    private ItemStack[] captureFullInventory(Player p) {
        ItemStack[] main = p.getInventory().getContents();
        ItemStack[] armor = p.getInventory().getArmorContents();
        ItemStack off = p.getInventory().getItemInOffHand();
        ItemStack[] full = new ItemStack[41];
        for (int i = 0; i < main.length && i < 36; i++) full[i] = main[i];
        for (int i = 0; i < armor.length && i < 4; i++) full[36 + i] = armor[i];
        if (off != null && off.getType() != Material.AIR) full[40] = off;
        return full;
    }

    /**
     * Ölüm kaydındaki eşyaları (36 ana + 4 zırh + 1 offhand) hedef oyuncuya otomatik olarak
     * geri verir: zırh parçaları (elytra dahil) direkt zırh slotlarına, offhand kalkan slotuna,
     * geri kalanı envantere giyer/koyar. Sığmayan eşyalar yere düşürülür.
     */
    private void giveIadeAuto(Player admin, Player target, DeathRecord dr) {
        ItemStack[] armorNow = target.getInventory().getArmorContents();
        for (int i = 0; i < dr.items.length && i < 4; i++) {
            ItemStack it = dr.items[36 + i];
            if (it == null || it.getType() == Material.AIR) continue;
            if (armorNow[i] == null || armorNow[i].getType() == Material.AIR) {
                armorNow[i] = it.clone();
            } else {
                target.getWorld().dropItem(target.getLocation(), it.clone());
            }
        }
        target.getInventory().setArmorContents(armorNow);

        if (dr.items.length > 40 && dr.items[40] != null && dr.items[40].getType() != Material.AIR) {
            ItemStack offNow = target.getInventory().getItemInOffHand();
            if (offNow == null || offNow.getType() == Material.AIR) {
                target.getInventory().setItemInOffHand(dr.items[40].clone());
            } else {
                target.getWorld().dropItem(target.getLocation(), dr.items[40].clone());
            }
        }

        for (int i = 0; i < dr.items.length && i < 36; i++) {
            ItemStack it = dr.items[i];
            if (it == null || it.getType() == Material.AIR) continue;
            Map<Integer, ItemStack> leftover = target.getInventory().addItem(it.clone());
            for (ItemStack rem : leftover.values()) target.getWorld().dropItem(target.getLocation(), rem);
        }

        target.updateInventory();
        target.sendMessage(color("&#00ff88&l[İADE] &fEşyaların &e" + admin.getName() + " &ftarafından iade edildi, set otomatik giyildi."));
        admin.sendMessage(color("&#00ff88» &f" + dr.playerName + " &fadına eşyalar/set otomatik iade edildi."));
        iadeLogs.add(new IadeLog(admin.getName(), dr.playerName, dr.id));
    }

    private void giveDuelLootChest(Player dead, Player recipient, Location fallbackLoc) {
        ItemStack[] mainSrc = dead.getInventory().getContents(); // 36 slot — hotbar + ana envanter, orijinal sırayla
        ItemStack[] armorSrc = dead.getInventory().getArmorContents(); // 4 slot: bot, pantolon, göğüs(elytra dahil), kask
        ItemStack offSrc = dead.getInventory().getItemInOffHand();
        // 41 slot: 0-35 ana envanter, 36-39 zırh, 40 offhand (kalkan)
        ItemStack[] cloned = new ItemStack[41];
        boolean anyItem = false;
        for (int i = 0; i < mainSrc.length && i < 36; i++) {
            if (mainSrc[i] != null && mainSrc[i].getType() != Material.AIR) {
                cloned[i] = mainSrc[i].clone();
                anyItem = true;
            }
        }
        for (int i = 0; i < armorSrc.length && i < 4; i++) {
            if (armorSrc[i] != null && armorSrc[i].getType() != Material.AIR) {
                cloned[36 + i] = armorSrc[i].clone();
                anyItem = true;
            }
        }
        if (offSrc != null && offSrc.getType() != Material.AIR) {
            cloned[40] = offSrc.clone();
            anyItem = true;
        }

        dead.getInventory().clear();
        dead.getInventory().setArmorContents(new ItemStack[4]);
        dead.getInventory().setItemInOffHand(null);
        if (!anyItem) return; // eşya yoksa sandık oluşturmaya gerek yok

        ItemStack chest = buildLootChestItem(cloned, dead.getName());
        if (chest == null) { // serileştirme başarısız: eşyalar kaybolmasın, ölüm noktasına bırak
            for (ItemStack it : cloned) {
                if (it != null && it.getType() != Material.AIR) fallbackLoc.getWorld().dropItem(fallbackLoc, it);
            }
            return;
        }

        if (recipient != null && recipient.isOnline()) {
            HashMap<Integer, ItemStack> leftover = new HashMap<>(recipient.getInventory().addItem(chest));
            if (leftover.isEmpty()) {
                recipient.sendMessage(duel(hl(dead.getName()) + " oyuncusunun eşyaları sandık olarak envanterine eklendi."));
            } else {
                ItemStack notFit = leftover.values().iterator().next();
                HashMap<Integer, ItemStack> leftover2 = new HashMap<>(recipient.getEnderChest().addItem(notFit));
                if (leftover2.isEmpty()) {
                    recipient.sendMessage(duel("Envanterin doluydu. Eşyalar Ender Chest'ine kondu."));
                } else {
                    for (ItemStack rem : leftover2.values()) {
                        org.bukkit.entity.Item it = recipient.getWorld().dropItem(recipient.getLocation(), rem);
                        it.setPickupDelay(0);
                    }
                    recipient.sendMessage(duelErr("Envanterin ve Ender Chest'in dolu. Sandık ayaklarının dibine bırakıldı."));
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

    /** Sandığın benzersiz kimliği (eski/kimliksiz sandıkta null). */
    private UUID lootChestIdOf(ItemStack item) {
        if (!isLootChestItem(item)) return null;
        String sid = item.getItemMeta().getPersistentDataContainer().get(lootChestId, PersistentDataType.STRING);
        if (sid == null) return null;
        try { return UUID.fromString(sid); } catch (IllegalArgumentException ex) { return null; }
    }

    private int lootChestVersionOf(ItemStack item) {
        if (!isLootChestItem(item)) return 0;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(lootChestVer, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    private String lootChestOwnerOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "?";
        String o = item.getItemMeta().getPersistentDataContainer().get(lootChestOwner, PersistentDataType.STRING);
        return o == null ? "?" : o;
    }

    /** Sandık itemin meta'sını (isim, lore, kimlik, sürüm, serileştirilmiş içerik) yazar. Serileştirme başarısızsa false döner ve meta'ya dokunmaz. */
    private boolean applyLootChestMeta(ItemMeta im, ItemStack[] contents, String ownerName, UUID id, int version) {
        String serialized = serializeItems(contents);
        if (serialized == null) return false;
        int count = 0;
        for (ItemStack it : contents) if (it != null && it.getType() != Material.AIR && !isLootChestFiller(it)) count++;

        // İsim kalın DEĞİL — sade, profesyonel tek ton
        im.setDisplayName(color("&r" + UI_TEXT + ownerName + UI_SUB + " eşyaları"));
        List<String> lore = new ArrayList<>();
        lore.add(color("&r" + UI_SUB + "Duel sonucunda düşen eşyalar."));
        lore.add(color(""));
        lore.add(color("&r" + UI_SUB + "Sahibi: " + UI_TEXT + ownerName));
        lore.add(color("&r" + UI_SUB + "İçerik: " + UI_TEXT + count + " eşya"));
        lore.add(color(""));
        lore.add(color("&r" + UI_DIM + "Sağ tıkla aç. Yere konulamaz."));
        im.setLore(lore);
        im.setMaxStackSize(1); // yığılamaz: yığın bölme ile kopyalama yolu kapalı

        im.getPersistentDataContainer().set(lootChestTag, PersistentDataType.BYTE, (byte) 1);
        im.getPersistentDataContainer().set(lootChestOwner, PersistentDataType.STRING, ownerName);
        im.getPersistentDataContainer().set(lootChestId, PersistentDataType.STRING, id.toString());
        im.getPersistentDataContainer().set(lootChestVer, PersistentDataType.INTEGER, version);
        im.getPersistentDataContainer().set(lootChestData, PersistentDataType.STRING, serialized);
        return true;
    }

    /** İçeriği (41 slot) ve sahibini vererek sahte bir Duel Sandığı itemi inşa eder; her sandığın kendine ait benzersiz kimliği vardır. Serileştirme başarısızsa null. */
    private ItemStack buildLootChestItem(ItemStack[] contents36, String ownerName) {
        ItemStack chest = new ItemStack(Material.CHEST, 1);
        ItemMeta im = chest.getItemMeta();
        UUID id = UUID.randomUUID();
        if (!applyLootChestMeta(im, contents36, ownerName, id, 1)) return null;
        chest.setItemMeta(im);
        lootChestKnownVer.put(id, 1);
        return chest;
    }

    /** Oyuncunun envanterinde (zırh/offhand dahil) bu kimlikteki sandığın slotunu bulur; yoksa -1. */
    private int findLootChestSlot(Player p, UUID id) {
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length; i++) if (id.equals(lootChestIdOf(c[i]))) return i;
        return -1;
    }

    /**
     * Eski (kimliksiz) veya yığılmış sandıklara benzersiz kimlik verir. Yığındaki her fazla kopya ayrı bir
     * sandığa dönüşür (hiçbiri kaybolmaz). Okunamayan sandıkta null döner ve hiçbir şeye dokunulmaz.
     */
    private UUID ensureLootChestId(Player p, int slot) {
        ItemStack it = p.getInventory().getItem(slot);
        if (!isLootChestItem(it)) return null;
        UUID id = lootChestIdOf(it);
        int amount = it.getAmount();
        if (id != null && amount == 1) return id;

        String data = it.getItemMeta().getPersistentDataContainer().get(lootChestData, PersistentDataType.STRING);
        ItemStack[] contents = (data == null || data.isEmpty()) ? new ItemStack[41] : deserializeItems(data);
        if (contents == null) return null;
        String owner = lootChestOwnerOf(it);
        ItemStack first = buildLootChestItem(contents, owner);
        if (first == null) return null;
        p.getInventory().setItem(slot, first);
        for (int i = 1; i < amount; i++) {
            ItemStack extra = buildLootChestItem(contents, owner);
            if (extra == null) break;
            for (ItemStack rem : p.getInventory().addItem(extra).values()) {
                p.getWorld().dropItem(p.getLocation(), rem).setPickupDelay(20);
            }
        }
        return lootChestIdOf(first);
    }

    /**
     * GUI'nin yeni içeriğini sandık itemine ANINDA yazar (her alımda). Böylece sandık itemi hiçbir an GUI'den eski/dolu kalmaz.
     * -1: sandık envanterde yok / eski sürüm / yazılamadı (hiçbir şey değişmedi), 0: güncellendi, 1: boşaldı (item silindi).
     */
    private int commitLootChest(Player p, UUID id, ItemStack[] next) {
        int chestSlot = findLootChestSlot(p, id);
        if (chestSlot < 0) return -1;
        ItemStack cur = p.getInventory().getItem(chestSlot);
        int ver = lootChestVersionOf(cur);
        Integer known = lootChestKnownVer.get(id);
        if (known != null && ver < known) return -1;

        boolean empty = true;
        for (ItemStack it : next) if (it != null && it.getType() != Material.AIR) { empty = false; break; }
        if (empty) {
            p.getInventory().setItem(chestSlot, null);
            lootChestKnownVer.put(id, Integer.MAX_VALUE); // bu kimlikteki her kopya artık geçersiz
            return 1;
        }
        ItemMeta im = cur.getItemMeta();
        if (!applyLootChestMeta(im, next, lootChestOwnerOf(cur), id, ver + 1)) return -1;
        cur.setItemMeta(im);
        p.getInventory().setItem(chestSlot, cur);
        lootChestKnownVer.put(id, ver + 1);
        return 0;
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
            return null; // boş sandık sanılıp silinmesin diye null
        }
    }

    /** Duel sandığı GUI'sinin holder'ı — GUI'yi tek bir sandık kimliğine bağlar (slot numarasına değil). */
    private static class LootChestHolder implements InventoryHolder {
        final UUID chestId;
        Inventory inv;
        LootChestHolder(UUID chestId) { this.chestId = chestId; }
        @Override public Inventory getInventory() { return inv; }
    }

    /** Boş sandık slotlarını dolduran gri, Silvera markalı dolgu camı üretir. Alınamaz/yerine eşya konulamaz. */
    private ItemStack lootChestFillerItem() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta im = pane.getItemMeta();
        im.setDisplayName(" ");
        im.getPersistentDataContainer().set(lootChestFillerTag, PersistentDataType.BYTE, (byte) 1);
        pane.setItemMeta(im);
        return pane;
    }

    /** Verilen item, sandığın dolgu camı mı (alınamaz/yer tutucu)? */
    private boolean isLootChestFiller(ItemStack it) {
        if (it == null || it.getType() != Material.GRAY_STAINED_GLASS_PANE || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(lootChestFillerTag, PersistentDataType.BYTE);
    }

    // Yönetilen menülerde (Günlük Ödül dahil) item sürüklemeyi tamamen engeller.
    @EventHandler
    public void onManagedInvDrag(InventoryDragEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        boolean managed = title.contains("Iade") || title.contains("Anahtar") || title.contains("Kasa")
            || title.contains("Crate") || title.contains("Olum Detayi")
            || title.contains("Kumar") || title.contains("Efekt Secimi") || title.contains("Bilgi Merkezi")
            || title.contains("Hile Kontrol") || title.contains("Duel Arenaları") || title.contains("Duel Ayarları")
            || title.contains("Duel Daveti")
            || title.contains("İzin Yönetimi") || title.contains("İade Geçmişi")
            || title.contains("Günlük Ödül");
        if (managed) e.setCancelled(true);
    }

    /** Oyuncunun şu an bir Duel Sandığı GUI'si açık mı? */
    private boolean lootChestOpenFor(Player p) {
        return p.getOpenInventory().getTopInventory().getHolder() instanceof LootChestHolder;
    }

    /** Sandığı sağ tıklayan oyuncuya, içeriğiyle dolu sahte bir GUI açar. Boş slotlar gri camla doldurulur. */
    private void openLootChest(Player p, ItemStack chestItem, int slot) {
        if (lootChestOpenFor(p)) return;
        UUID id = ensureLootChestId(p, slot);
        if (id == null) {
            p.sendActionBar(color(UI_BAD + "Bu sandık okunamadı. Bir yetkiliye haber ver."));
            return;
        }
        ItemStack item = p.getInventory().getItem(slot);
        int ver = lootChestVersionOf(item);
        Integer known = lootChestKnownVer.get(id);
        if (known != null && ver < known) {
            p.sendMessage(color(UI_BAD + "Bu sandık geçersiz (eski bir kopya). Bir yetkiliye haber ver."));
            getLogger().warning("Eski sürümlü Duel Sandığı reddedildi: oyuncu=" + p.getName() + " id=" + id
                + " sürüm=" + ver + " bilinen=" + known);
            return;
        }
        UUID openBy = lootChestOpenBy.get(id);
        if (openBy != null && !openBy.equals(p.getUniqueId())) {
            p.sendActionBar(color(UI_BAD + "Bu sandık şu an başka biri tarafından açık."));
            return;
        }
        String data = item.getItemMeta().getPersistentDataContainer().get(lootChestData, PersistentDataType.STRING);
        ItemStack[] contents = (data == null || data.isEmpty()) ? new ItemStack[41] : deserializeItems(data);
        if (contents == null) {
            p.sendActionBar(color(UI_BAD + "Bu sandık okunamadı. Bir yetkiliye haber ver."));
            return;
        }
        boolean any = false;
        for (int i = 0; i < 41 && i < contents.length; i++) {
            if (contents[i] != null && contents[i].getType() != Material.AIR) { any = true; break; }
        }
        if (!any) { // içi boş sandık: sil
            p.getInventory().setItem(slot, null);
            lootChestKnownVer.put(id, Integer.MAX_VALUE);
            p.sendMessage(color("&#aaaaaa&oDüel sandığı boşaldı ve kayboldu."));
            return;
        }
        lootChestKnownVer.merge(id, ver, Math::max);

        LootChestHolder holder = new LootChestHolder(id);
        // 45 slot (5 satır): 0-35 ana envanter, 36-39 zırh, 40 offhand — geri kalan slotlar dolgu cam
        Inventory gui = Bukkit.createInventory(holder, 45, color(UI_SUB + "Duel Sandığı"));
        holder.inv = gui;
        for (int i = 0; i < 45; i++) {
            ItemStack it = (i < 41 && i < contents.length) ? contents[i] : null;
            if (it != null && it.getType() != Material.AIR) gui.setItem(i, it);
            else gui.setItem(i, lootChestFillerItem());
        }
        lootChestOpenBy.put(id, p.getUniqueId());
        p.openInventory(gui);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }

    /**
     * Sandık GUI'si açıkken HİÇBİR tıklama vanilla işlemeye gitmez: alt envanter tamamen kilitli, imleç (cursor) hiç kullanılmaz.
     * Üstteki bir eşyaya sol/sağ/shift tık = o yığını envantere AL. Alma işlemi sandık itemine anında yazılır.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLootChestClick(InventoryClickEvent ev) {
        Inventory top = ev.getView().getTopInventory();
        if (!(top.getHolder() instanceof LootChestHolder holder)) return;
        ev.setCancelled(true);
        if (!(ev.getWhoClicked() instanceof Player p)) return;
        int raw = ev.getRawSlot();
        if (raw < 0 || raw >= top.getSize()) {
            p.sendActionBar(color(UI_BAD + "Sandık açıkken envanterine dokunamazsın."));
            return;
        }
        switch (ev.getClick()) {
            case LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT -> lootChestTake(p, holder, top, raw);
            default -> { }
        }
    }

    /** GUI'deki bir yığını oyuncuya verir: önce sandık kaydı güncellenir, sonra eşya verilir (aynı tick). */
    private void lootChestTake(Player p, LootChestHolder holder, Inventory gui, int slot) {
        if (slot >= 41) return; // 41-44 yalnızca dolgu
        ItemStack it = gui.getItem(slot);
        if (it == null || it.getType() == Material.AIR || isLootChestFiller(it)) {
            p.sendActionBar(color(UI_SUB + "Burası boş."));
            return;
        }
        int fit = lootChestFit(p, it);
        if (fit <= 0) {
            p.sendActionBar(color(UI_BAD + "Envanterin dolu."));
            return;
        }

        // Alım sonrası sandığın içeriği
        ItemStack[] next = new ItemStack[41];
        for (int i = 0; i < 41; i++) {
            ItemStack cur = gui.getItem(i);
            if (cur == null || cur.getType() == Material.AIR || isLootChestFiller(cur)) continue;
            if (i == slot) {
                if (fit >= cur.getAmount()) continue;
                ItemStack rest = cur.clone();
                rest.setAmount(cur.getAmount() - fit);
                next[i] = rest;
            } else {
                next[i] = cur.clone();
            }
        }

        int result = commitLootChest(p, holder.chestId, next);
        if (result < 0) {
            p.sendMessage(color(UI_BAD + "Bu sandık artık geçerli değil. Menü kapatılıyor."));
            Bukkit.getScheduler().runTask(this, () -> p.closeInventory());
            return;
        }

        ItemStack give = it.clone();
        give.setAmount(fit);
        for (ItemStack rem : p.getInventory().addItem(give).values()) { // sığacağı önceden hesaplandı; yine de eşya asla kaybolmaz
            p.getWorld().dropItem(p.getLocation(), rem).setPickupDelay(0);
        }
        if (fit >= it.getAmount()) {
            gui.setItem(slot, lootChestFillerItem());
        } else {
            ItemStack rest = it.clone();
            rest.setAmount(it.getAmount() - fit);
            gui.setItem(slot, rest);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);

        if (result == 1) {
            p.sendMessage(color("&#aaaaaa&oDüel sandığı boşaldı ve kayboldu."));
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.6f, 0.9f);
            Bukkit.getScheduler().runTask(this, () -> p.closeInventory());
        }
    }

    /** Verilen yığından envantere kaç adet sığar (simülasyon; envanteri değiştirmez). */
    private int lootChestFit(Player p, ItemStack it) {
        int need = it.getAmount();
        int max = it.getMaxStackSize();
        int fit = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s == null || s.getType() == Material.AIR) fit += max;
            else if (s.isSimilar(it) && s.getAmount() < s.getMaxStackSize()) fit += s.getMaxStackSize() - s.getAmount();
            if (fit >= need) return need;
        }
        return Math.min(fit, need);
    }

    /** Sandık GUI'sinde sürüklemenin (alt veya üst envanter) her türlüsü iptal. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLootChestDrag(InventoryDragEvent ev) {
        if (!(ev.getView().getTopInventory().getHolder() instanceof LootChestHolder)) return;
        ev.setCancelled(true);
        if (ev.getWhoClicked() instanceof Player pl) pl.sendActionBar(color(UI_BAD + "Sandık açıkken envanterine dokunamazsın."));
    }

    // Creative istemci tıkları ayrı bir olay: sandık açıkken onu da kapat (item kopyalama yolu)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLootChestCreative(org.bukkit.event.inventory.InventoryCreativeEvent ev) {
        if (ev.getView().getTopInventory().getHolder() instanceof LootChestHolder) ev.setCancelled(true);
    }

    // ── Sandık GUI'si açıkken envanter değiştiren diğer her yol kilitli ──
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLootChestDrop(PlayerDropItemEvent ev) {
        if (lootChestOpenFor(ev.getPlayer())) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLootChestSwapHands(PlayerSwapHandItemsEvent ev) {
        if (lootChestOpenFor(ev.getPlayer())) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLootChestHeldChange(PlayerItemHeldEvent ev) {
        if (lootChestOpenFor(ev.getPlayer())) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLootChestPickup(EntityPickupItemEvent ev) {
        if (ev.getEntity() instanceof Player pl && lootChestOpenFor(pl)) ev.setCancelled(true);
    }

    // Sandık itemi craft malzemesi olamaz (veri kaybolur / kopya yolu olmasın)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLootChestCraft(org.bukkit.event.inventory.PrepareItemCraftEvent ev) {
        for (ItemStack it : ev.getInventory().getMatrix()) {
            if (isLootChestItem(it)) { ev.getInventory().setResult(null); return; }
        }
    }

    @EventHandler
    public void onLootChestQuit(PlayerQuitEvent ev) {
        UUID u = ev.getPlayer().getUniqueId();
        lootChestOpenBy.values().removeIf(v -> v.equals(u));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLootChestPlace(org.bukkit.event.block.BlockPlaceEvent ev) {
        // Duel sandığı gerçek bir sandık gibi yere konulamaz — dupe'a karşı en garanti önlem
        if (isLootChestItem(ev.getItemInHand())) {
            ev.setCancelled(true);
            ev.getPlayer().sendActionBar(color(UI_BAD + "Bu sandık yere konulamaz. Sağ tıkla aç."));
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLootChestClose(InventoryCloseEvent ev) {
        if (!(ev.getInventory().getHolder() instanceof LootChestHolder holder)) return;
        // İçerik her alımda anında sandık itemine yazıldığı için kapanışta envantere HİÇBİR ŞEY yazılmaz
        // (eski "kapanışta geri yaz" mantığı dupe'un kaynağıydı).
        lootChestOpenBy.remove(holder.chestId, ev.getPlayer().getUniqueId());
        if (ev.getPlayer() instanceof Player p) p.playSound(p.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.6f, 0.9f);
    }

    private void teleportToSpawn(Player p) {
        if (!p.isOnline()) return;
        // Combat plugin uyumlu: 1 tick sonra ışınla (combat tag temizlensin diye)
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) return;
                hudDropNow(p, 3000L);
                // ── Öncelik: /duelspawnayarla ile ayarlanan özel duel/1v1 spawn noktası ──
                // Essentials'a veya world spawn'a bağlı kalmadan garanti ışınlama sağlar.
                if (duelOzelSpawn != null) {
                    hudTp(p, duelOzelSpawn);
                    return;
                }
                // Essentials spawn komutu (combat plugin bypass için konsol)
                if (Bukkit.getServer().getPluginManager().getPlugin("Essentials") != null) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + p.getName());
                } else {
                    // Essentials yoksa world spawn
                    Location spawnLoc = p.getWorld().getSpawnLocation();
                    hudTp(p, spawnLoc);
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
        Inventory inv = Bukkit.createInventory(null, 54, color(UI_SUB + "Duel Arenaları"));
        ItemStack border = uiFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        inv.setItem(4, uiItem(Material.GOLDEN_SWORD, UI_TEXT + "Duel Arenaları",
            UI_OK + "Yeşil" + UI_SUB + ": boş   " + UI_BAD + "Kırmızı" + UI_SUB + ": dolu   " + UI_WARN + "Sarı" + UI_SUB + ": hazırlanıyor",
            "",
            UI_SUB + "Boş bir arena seç, ardından " + UI_ACCENT + "/1v1 <oyuncu>" + UI_SUB + " ile davet gönder."));

        int[] arenaSlots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};
        List<String> ids = new ArrayList<>(duelSpawn1.keySet());
        for (int i = 0; i < Math.min(ids.size(), arenaSlots.length); i++) {
            String did = ids.get(i);
            boolean dolu = duelPlayers.containsKey(did);
            boolean hazir = duelSpawn1.get(did) != null && duelSpawn2.get(did) != null;

            Material mat = dolu ? Material.RED_STAINED_GLASS_PANE : (hazir ? Material.LIME_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE);
            String renk = dolu ? UI_BAD : (hazir ? UI_OK : UI_WARN);
            List<String> lore = new ArrayList<>();
            lore.add(UI_SUB + "Durum: " + renk + (dolu ? "Dolu" : (hazir ? "Boş" : "Hazırlanıyor")));
            if (dolu) {
                UUID[] dp = duelPlayers.get(did);
                String n1 = Bukkit.getOfflinePlayer(dp[0]).getName();
                String n2 = Bukkit.getOfflinePlayer(dp[1]).getName();
                lore.add(UI_TEXT + (n1 != null ? n1 : "?") + UI_DIM + " vs " + UI_TEXT + (n2 != null ? n2 : "?"));
            } else if (hazir) {
                lore.add("");
                lore.add(UI_DIM + "Seçmek için tıkla.");
            }
            ItemStack item = uiItem(mat, renk + "Arena " + did, lore.toArray(new String[0]));
            ItemMeta m = item.getItemMeta();
            m.getPersistentDataContainer().set(duelGuiTag, PersistentDataType.STRING, "arena:" + did);
            item.setItemMeta(m);
            inv.setItem(arenaSlots[i], item);
        }
        inv.setItem(49, makeCloseHead(p));
        p.openInventory(inv);
    }



    // toggleKey: web / ely / ladder / oak / stick / chest
    private ItemStack buildDuelToggleItem(String label, String toggleKey, Material onMat, Material offMat, boolean on, String desc) {
        ItemStack it = uiItem(on ? onMat : offMat,
            (on ? UI_OK : UI_BAD) + label + UI_DIM + ": " + duelRuleState(on),
            UI_SUB + desc, "", UI_DIM + "Değiştirmek için tıkla.");
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(duelGuiTag, PersistentDataType.STRING, "toggle:" + toggleKey);
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
        inv.setItem(19, buildDuelToggleItem("Örümcek Ağı", "web",    Material.COBWEB,          Material.STRING,          webOn,    "Duelde örümcek ağı kullanımı"));
        inv.setItem(20, buildDuelToggleItem("Elytra",       "ely",    Material.ELYTRA,          Material.LEATHER_CHESTPLATE, elyOn, "Duelde elytra kullanımı"));
        inv.setItem(21, buildDuelToggleItem("Merdiven",     "ladder", Material.LADDER,          Material.BARRIER,         ladderOn, "Duelde merdiven kullanımı"));
        inv.setItem(23, buildDuelToggleItem("Meşe Odun",    "oak",    Material.OAK_LOG,         Material.BARRIER,         oakOn,    "Duelde meşe odun kullanımı"));
        inv.setItem(24, buildDuelToggleItem("Tahta",        "stick",  Material.SPRUCE_PLANKS,   Material.BARRIER,         stickOn,  "Duelde tahta (meşe hariç) kullanımı"));
        inv.setItem(25, buildDuelToggleItem("Göğüslük",     "chest",  Material.IRON_CHESTPLATE, Material.BARRIER,         chestOn,  "Duelde göğüslük kullanımı"));
    }

    // ─── /duelayarlar & /duelayarlaradmin — kişisel varsayılan ayarlar ───
    private void openDuelSettingsMenu(Player p, boolean isAdmin) {
        Inventory inv = Bukkit.createInventory(null, 45, color(UI_SUB + "Duel Ayarları"));
        ItemStack filler = uiFiller();
        for (int i = 0; i < 45; i++) inv.setItem(i, filler);

        inv.setItem(13, uiItem(Material.BOOK, UI_TEXT + "Duel Ayarları",
            UI_SUB + "Davet gönderirken kullanılan varsayılan kurallar.",
            UI_SUB + "Kapalı olan itemler duelde kullanılamaz."));

        fillDuelToggles(inv, p.getUniqueId(), duelWebEnabled, duelElyEnabled, duelLadderEnabled, duelOakEnabled, duelStickEnabled, duelChestEnabled);

        inv.setItem(40, makeCloseHead(p));
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.4f);
    }

    // ─── /duel <oyuncu> — davet göndermeden önce açılan ayar ekranı ───
    private void openDuelInviteMenu(Player p, Player target) {
        Inventory inv = Bukkit.createInventory(null, 45, color(UI_SUB + "Duel Daveti" + UI_DIM + " | " + UI_TEXT + target.getName()));
        ItemStack filler = uiFiller();
        for (int i = 0; i < 45; i++) inv.setItem(i, filler);

        inv.setItem(13, uiItem(Material.BOOK, UI_TEXT + target.getName() + UI_SUB + " oyuncusuna davet",
            UI_SUB + "Kuralları ayarla, ardından daveti gönder.",
            UI_SUB + "Kapalı olan itemler duelde kullanılamaz."));

        fillDuelToggles(inv, p.getUniqueId(), duelWebEnabled, duelElyEnabled, duelLadderEnabled, duelOakEnabled, duelStickEnabled, duelChestEnabled);

        ItemStack send = uiItem(Material.LIME_DYE, UI_OK + "Daveti Gönder",
            UI_SUB + "Daveti " + UI_TEXT + target.getName() + UI_SUB + " oyuncusuna gönderir.");
        ItemMeta sm = send.getItemMeta();
        sm.getPersistentDataContainer().set(duelGuiTag, PersistentDataType.STRING, "confirm");
        send.setItemMeta(sm);
        inv.setItem(31, send);

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
        duelMatchToken.remove(arenaId);

        // ── Duel sırasında koyulan bloklar sessizce kaldırılsın ──
        java.util.Set<org.bukkit.block.Block> placedQ = duelPlacedBlocks.remove(arenaId);
        if (placedQ != null) {
            for (org.bukkit.block.Block b : placedQ) {
                if (b != null) b.setType(Material.AIR, false);
            }
        }

        final Player finalWinner = winner;
        final String finalArenaId = arenaId;
        final String loserName = p.getName();

        // Kazanana title + spawn
        if (finalWinner != null && finalWinner.isOnline()) {
            finalWinner.setWalkSpeed(0.2f); finalWinner.setFlySpeed(0.1f);
            // Tüm sunucuya duel sonucu
            for (Player on : Bukkit.getOnlinePlayers()) {
                on.sendMessage(duel(UI_OK + finalWinner.getName() + UI_TEXT + " duelde " + UI_BAD + loserName + UI_TEXT + " oyundan çıktığı için kazandı."));
            }
            finalWinner.sendTitle(
                duelFlop("Kazandın", true),
                color(UI_SUB + loserName + " oyundan çıktı"),
                5, 80, 15);
            finalWinner.playSound(finalWinner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            // 8sn geri sayım ile spawna
            new BukkitRunnable() {
                int sec = 8;
                @Override public void run() {
                    if (!finalWinner.isOnline()) { cancel(); return; }
                    if (sec > 0) {
                        finalWinner.sendTitle(duelFlop("Kazandın", true),
                            color(UI_SUB + "Spawn noktasına ışınlanmana " + UI_TEXT + sec + UI_SUB + " saniye"), 0, 25, 5);
                        finalWinner.playSound(finalWinner.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.5f);
                        sec--;
                    } else {
                        cancel();
                        finalWinner.sendTitle(duelFlop("Işınlandın", true), color(UI_SUB + "İyi oyunlar"), 5, 50, 15);
                        teleportToSpawn(finalWinner);
                    }
                }
            }.runTaskTimer(this, 0L, 20L);
        }
    }

    // ─── 1v1 Room çıkış — oda asla kilitli kalmaz (motor: roomHandleQuit) ───
    @EventHandler
    public void onAyarlaQuit(PlayerQuitEvent evq2) {
        roomHandleQuit(evq2.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDuelCommand(PlayerCommandPreprocessEvent evCmd) {
        Player p = evCmd.getPlayer();
        if (p.hasPermission("loginx.admin")) return;
        UUID uid = p.getUniqueId();

        boolean activeDuel = playerDuel.containsKey(uid) || playerAyarlaArena.containsKey(uid);
        Long dTime = duelDeathTime.get(uid);
        if (!activeDuel && dTime == null) return; // ne dueldeyken ne yakın zamanda ölmüş — dokunma

        // Sonuç belli olduktan sonra (1v1 Room: bitiş geri sayımı / ölüm sonrası kilit) lobi komutları (/hub vb.) serbest.
        // Düello hâlâ sürerken (ACTIVE) kilit aynen devam eder — maçtan komutla kaçılamaz.
        boolean roomDecided = false;
        String roomIdOfPlayer = playerAyarlaArena.get(uid);
        if (roomIdOfPlayer != null) {
            Room rr = rooms.get(roomIdOfPlayer);
            roomDecided = rr != null && rr.state != ROOM_ACTIVE;
        }
        boolean duelOver = roomDecided || (!playerDuel.containsKey(uid) && !playerAyarlaArena.containsKey(uid));
        if (duelOver && isLobbyCommand(evCmd.getMessage())) return;

        if (activeDuel) {
            // Düellodayken (henüz ölmedi): sadece berabere teklifi komutları kullanılabilir
            String fullCmdActive = evCmd.getMessage().toLowerCase().trim().substring(1);
            String[] partsActive = fullCmdActive.split(" ");
            String cmdActive = partsActive[0];
            boolean allowedDuelCmd = cmdActive.equals("duelberaberekabul")
                    || cmdActive.equals("duelberaberereddet")
                    || (cmdActive.equals("duel") && partsActive.length > 1 && partsActive[1].equals("berabere"));
            if (allowedDuelCmd) return;

            evCmd.setCancelled(true);
            p.sendMessage(duelErr("Duel sırasında yalnızca " + hl("/duel berabere") + ", " + hl("/duelberaberekabul") + " ve " + hl("/duelberaberereddet") + " komutları kullanılabilir."));
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
            p.sendMessage(duelErr("Komutlar kısa süre sonra açılacak."));
            return;
        }

        // İlk 10 sn geçti — sadece /ec ve /pv (ve /pv <arg>) serbest
        String fullCmd = evCmd.getMessage().toLowerCase().trim().substring(1);
        String cmd = fullCmd.split(" ")[0];
        boolean allowed = cmd.equals("ec") || cmd.equals("pv") || fullCmd.startsWith("pv ");
        if (!allowed) {
            evCmd.setCancelled(true);
            p.sendMessage(duelErr("Ölümden sonra yalnızca " + hl("/ec") + " ve " + hl("/pv") + " komutları kullanılabilir."));
        }
    }
    /** Düello/oda sonucu belli olduktan sonra serbest kalan lobi komutu mu? (config: duel.lobi-komutlari; varsayılan hub, lobby, spawn) */
    private boolean isLobbyCommand(String rawMessage) {
        String c = rawMessage.toLowerCase().trim();
        if (c.startsWith("/")) c = c.substring(1);
        int sp = c.indexOf(' ');
        String name = sp >= 0 ? c.substring(0, sp) : c;
        int colon = name.indexOf(':');           // eklenti:komut biçimi (örn. hubthat:hub)
        if (colon >= 0) name = name.substring(colon + 1);
        List<String> list = getConfig().getStringList("duel.lobi-komutlari");
        if (list.isEmpty()) list = Arrays.asList("hub", "lobby", "spawn");
        for (String entry : list) {
            if (entry != null && entry.trim().replace("/", "").equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** Bir sender'ın belirli bir LoginX komutunu kullanıp kullanamayacağını kontrol eder.
     *  loginx.admin her zaman yeter. Ayrıca /permkomutyetkiver ile açılan dinamik
     *  "loginx.cmd.<komut>" izni de (LuckPerms üzerinden grup bazlı) geçerlidir. */
    private boolean canUseLoginXCommand(CommandSender sender, String komut) {
        if (sender.hasPermission("loginx.admin") || sender.isOp()) return true;
        return sender.hasPermission("loginx.cmd." + komut.toLowerCase());
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
        m.setDisplayName(color("&#ff0000&lHİLE KONTROL"));
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

        // Kont özel sohbetini kapat — ikisi de normal chate döner
        UUID partnerUid = kontChatPartner.remove(uid);
        if (partnerUid != null) {
            kontChatPartner.remove(partnerUid);
            if (p != null) p.sendMessage(color("&#aaaaaa&l[Hile Kontrol Sohbet] &7kapandı. Normal chate döndün."));
            Player partner = Bukkit.getPlayer(partnerUid);
            if (partner != null) partner.sendMessage(color("&#aaaaaa&l[Hile Kontrol Sohbet] &7kapandı. Normal chate döndün."));
        }
    }

    /**
     * Kontten çıkarılan oyuncuyu, eşya iadesi tamamlandıktan hemen sonra konsoldan hub komutuyla gönderir.
     * Komut config.yml içindeki kont.hub-komutu ile değiştirilebilir; {oyuncu} oyuncu adıyla değişir.
     */
    private void sendReleasedPlayerToHub(UUID uid) {
        Player target = Bukkit.getPlayer(uid);
        if (target == null || !target.isOnline() || kontUntil.containsKey(uid)) return;
        target.setWalkSpeed(0.2f);
        target.setFlySpeed(0.1f);
        String cmd = getConfig().getString("kont.hub-komutu", "hub {oyuncu}").replace("{oyuncu}", target.getName()).trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (!cmd.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
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
        UUID uid = p.getUniqueId();

        // ─── Kont özel sohbeti (Hile Kontrol Sohbet) — kont süresince yalnızca iki taraf birbirini görür ───
        UUID kontPartnerUid = kontChatPartner.get(uid);
        if (kontPartnerUid != null) {
            ev.setCancelled(true);
            String temiz = stripEmoji(ev.getMessage());
            String out = color("&#ff9900&l[Hile Kontrol Sohbet] &r&f" + temiz);
            p.sendMessage(out);
            Player partner = Bukkit.getPlayer(kontPartnerUid);
            if (partner != null) partner.sendMessage(out);
            return;
        }

        if (p.hasPermission("loginx.admin")) return;
        long now = System.currentTimeMillis();
        String msg = ev.getMessage().toLowerCase();

        // ─── MUTE KONTROLÜ ───
        long spamMuteUntil  = spamMuted.getOrDefault(uid, 0L);
        if (spamMuteUntil > now) {
            ev.setCancelled(true);
            long remaining = spamMuteUntil - now;
            long min = remaining / 60000L; long sec = (remaining % 60000L) / 1000L;
            p.sendMessage(silvera() + color(" &fSusturuldun &8— &e" + min + " dk " + sec + " sn kaldı."));
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
                p.sendMessage(silvera() + color(" ") + warnFlop("SPAM Uyarısı") + color(" &85/5 &8— " + kufurMuteRengi + "10 dakika susturuldun!"));
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
        return color(kufurUyariRengi + "&l" + label);
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
                ev.getPlayer().sendActionBar(color("&#ff4444&fBu item yere konulması engellendi."));
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
                    dPl.sendActionBar(color(UI_BAD + "Bu duelde bu item kapalı."));
                    dPl.playSound(dPl.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
                    return;
                }
            }
            // ── Duel bitince temizlenmek üzere koyulan bloğu kaydet ──
            duelPlacedBlocks.computeIfAbsent(dArena, k -> new java.util.HashSet<>()).add(ev.getBlockPlaced());
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
            dPl.sendActionBar(color(UI_BAD + "Bu duelde göğüslük kapalı."));
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

    /** Oyuncunun yazdığı metinden gerçek emojileri (renkli pictograph karakterler) temizler.
     *  Sunucunun kendi kullandığı işaretler (⚠ ▬ » vb. UI sembolleri) buna dahil değildir,
     *  sadece klasik emoji blokları (yüz/hayvan/nesne emojileri, bayraklar, ZWJ dizileri) kaldırılır. */
    private String stripEmoji(String text) {
        if (text == null) return text;
        return text.replaceAll(
            "[\\x{1F000}-\\x{1FFFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{FE0F}\\x{200D}]", ""
        ).trim();
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
        saveArenaZones();
        saveRooms(false);
        saveNoDropRegions(false);
        saveConfig();
    }

    /** Elytra yasaklı arena bölgelerini config'e kaydeder. */
    private void saveArenaZones() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ElytraZone z : elytraZones) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("world", z.world);
            m.put("minx", z.box.getMinX()); m.put("miny", z.box.getMinY()); m.put("minz", z.box.getMinZ());
            m.put("maxx", z.box.getMaxX()); m.put("maxy", z.box.getMaxY()); m.put("maxz", z.box.getMaxZ());
            list.add(m);
        }
        getConfig().set("elytrabolgeleri", list);
    }

    /** Elytra yasaklı arena bölgelerini config'den yükler. */
    @SuppressWarnings("unchecked")
    private void loadArenaZones() {
        elytraZones.clear();
        List<?> raw = getConfig().getList("elytrabolgeleri");
        if (raw == null) return;
        for (Object o : raw) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) o;
            try {
                String world = (String) m.get("world");
                double minx = ((Number) m.get("minx")).doubleValue();
                double miny = ((Number) m.get("miny")).doubleValue();
                double minz = ((Number) m.get("minz")).doubleValue();
                double maxx = ((Number) m.get("maxx")).doubleValue();
                double maxy = ((Number) m.get("maxy")).doubleValue();
                double maxz = ((Number) m.get("maxz")).doubleValue();
                elytraZones.add(new ElytraZone(world, new BoundingBox(minx, miny, minz, maxx, maxy, maxz)));
            } catch (Exception ignored) {}
        }
    }

    // ─────────────────────────────────────────────
    //  ÇİFT ZIPLAMA
    // ─────────────────────────────────────────────
    private double doubleJumpCooldownSec() { return Math.max(0.0, getConfig().getDouble("ciftziplama.bekleme", 2.0)); }

    private ItemStack createDoubleJumpWand() {
        ItemStack axe = uiItem(Material.IRON_AXE, UI_TEXT + "Çift Zıplama Wand",
            UI_SUB + "Sağ tık: " + UI_TEXT + "1. nokta",
            UI_SUB + "Sol tık: " + UI_TEXT + "2. nokta",
            "",
            UI_DIM + "Ardından /ciftziplama olustur");
        ItemMeta im = axe.getItemMeta();
        im.getPersistentDataContainer().set(doubleJumpWandTag, PersistentDataType.BYTE, (byte) 1);
        axe.setItemMeta(im);
        return axe;
    }

    private void handleDoubleJumpCommand(Player p, String[] args) {
        UUID id = p.getUniqueId();
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        switch (sub) {
            case "" -> {
                Map<Integer, ItemStack> left = p.getInventory().addItem(createDoubleJumpWand());
                for (ItemStack rem : left.values()) p.getWorld().dropItem(p.getLocation(), rem);
                p.sendMessage(uiMsg(DJ_TAG, "Wand envanterine eklendi. Sağ tık 1. nokta, sol tık 2. nokta; sonra " + hl("/ciftziplama olustur") + "."));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
            }
            case "olustur" -> {
                Location l1 = jumpWandPos1.get(id), l2 = jumpWandPos2.get(id);
                if (l1 == null || l2 == null) { p.sendMessage(uiErr(DJ_TAG, "Önce wand ile iki nokta seç.")); return; }
                if (l1.getWorld() == null || !l1.getWorld().equals(l2.getWorld())) { p.sendMessage(uiErr(DJ_TAG, "İki nokta aynı dünyada olmalı.")); return; }
                BoundingBox box = BoundingBox.of(l1.getBlock(), l2.getBlock());
                jumpZones.add(new ElytraZone(l1.getWorld().getName(), box));
                saveJumpZones(); saveConfig();
                jumpWandPos1.remove(id); jumpWandPos2.remove(id);
                p.sendMessage(uiMsg(DJ_TAG, "Bölge oluşturuldu. Toplam bölge: " + hl(String.valueOf(jumpZones.size()))
                    + ". Alan: " + hl((int) box.getWidthX() + "x" + (int) box.getHeight() + "x" + (int) box.getWidthZ()) + ", üstünde 3 blok yükseklik dahil."));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.4f);
            }
            case "saniye" -> {
                if (args.length < 2) { p.sendMessage(uiErr(DJ_TAG, "Kullanım: /ciftziplama saniye <süre>. Şu an: " + doubleJumpCooldownSec() + " sn")); return; }
                double sec;
                try { sec = Double.parseDouble(args[1].replace(",", ".")); }
                catch (NumberFormatException ex) { p.sendMessage(uiErr(DJ_TAG, "Geçersiz süre.")); return; }
                if (sec < 0.2 || sec > 60) { p.sendMessage(uiErr(DJ_TAG, "Süre 0.2 ile 60 saniye arasında olmalı.")); return; }
                getConfig().set("ciftziplama.bekleme", sec); saveConfig();
                p.sendMessage(uiMsg(DJ_TAG, "Bekleme süresi " + hl(sec + " saniye") + " olarak ayarlandı."));
            }
            case "sil" -> {
                int before = jumpZones.size();
                jumpZones.removeIf(z -> z.world.equals(p.getWorld().getName()) && jumpZoneContains(z, p.getLocation()));
                int removed = before - jumpZones.size();
                if (removed == 0) { p.sendMessage(uiErr(DJ_TAG, "Bulunduğun konumda bölge yok.")); return; }
                saveJumpZones(); saveConfig();
                p.sendMessage(uiMsg(DJ_TAG, hl(String.valueOf(removed)) + " bölge silindi."));
            }
            default -> p.sendMessage(uiErr(DJ_TAG, "Kullanım: /ciftziplama | olustur | saniye <süre> | sil"));
        }
    }

    private boolean jumpZoneContains(ElytraZone z, Location l) {
        BoundingBox b = z.box;
        double y = l.getY() - 0.1;
        return l.getX() >= b.getMinX() && l.getX() < b.getMaxX()
            && l.getZ() >= b.getMinZ() && l.getZ() < b.getMaxZ()
            && y >= b.getMinY() && y < b.getMaxY() + 3;
    }

    private boolean inJumpZone(Location l) {
        if (l == null || l.getWorld() == null) return false;
        for (ElytraZone z : jumpZones) {
            if (z.world.equals(l.getWorld().getName()) && jumpZoneContains(z, l)) return true;
        }
        return false;
    }

    private boolean djEligibleMode(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
    }

    /** Bölge içindeyse uçuş iznini (çift zıplama tetikleyicisi) açar, dışındaysa geri alır. */
    private void refreshDoubleJump(Player p, Location at) {
        UUID id = p.getUniqueId();
        boolean inside = djEligibleMode(p) && inJumpZone(at);
        if (inside) {
            if (doubleJumpEnabled.contains(id)) return;
            if (p.getAllowFlight()) return; // başka bir sistemin uçuş izni, dokunma
            if (doubleJumpReadyAt.getOrDefault(id, 0L) > System.currentTimeMillis()) return; // bekleme görevi açacak
            p.setAllowFlight(true);
            doubleJumpEnabled.add(id);
        } else if (doubleJumpEnabled.remove(id)) {
            p.setFlying(false);
            p.setAllowFlight(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDoubleJumpWandUse(PlayerInteractEvent e) {
        if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() != Material.IRON_AXE || !item.hasItemMeta()
            || !item.getItemMeta().getPersistentDataContainer().has(doubleJumpWandTag, PersistentDataType.BYTE)) return;
        if (e.getClickedBlock() == null) return;
        Action act = e.getAction();
        if (act != Action.RIGHT_CLICK_BLOCK && act != Action.LEFT_CLICK_BLOCK) return;
        e.setCancelled(true);
        if (!p.hasPermission("loginx.admin")) return;
        Location clicked = e.getClickedBlock().getLocation();
        boolean first = act == Action.RIGHT_CLICK_BLOCK;
        (first ? jumpWandPos1 : jumpWandPos2).put(p.getUniqueId(), clicked);
        p.sendMessage(uiMsg(DJ_TAG, (first ? "1." : "2.") + " nokta seçildi: "
            + hl(clicked.getBlockX() + ", " + clicked.getBlockY() + ", " + clicked.getBlockZ())));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, first ? 1.2f : 1.5f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDoubleJumpToggle(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (!e.isFlying() || !doubleJumpEnabled.contains(id)) return;
        e.setCancelled(true);
        p.setFlying(false);
        long now = System.currentTimeMillis();
        if (!djEligibleMode(p) || !inJumpZone(p.getLocation()) || doubleJumpReadyAt.getOrDefault(id, 0L) > now) return;

        Vector dir = p.getLocation().getDirection();
        dir.setY(0);
        if (dir.lengthSquared() > 1.0E-4) dir.normalize(); else dir.zero();
        double horizontal = getConfig().getDouble("ciftziplama.yatay-hiz", 1.1);
        double vertical   = getConfig().getDouble("ciftziplama.dikey-hiz", 0.42);
        p.setVelocity(dir.multiply(horizontal).setY(vertical));
        p.setFallDistance(0f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1f);

        long ms = (long) (doubleJumpCooldownSec() * 1000);
        doubleJumpReadyAt.put(id, now + ms);
        doubleJumpEnabled.remove(id);
        p.setAllowFlight(false);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline()) refreshDoubleJump(p, p.getLocation());
        }, ms / 50 + 2);
    }

    @EventHandler
    public void onDoubleJumpMove(PlayerMoveEvent e) {
        Location f = e.getFrom(), t = e.getTo();
        if (t == null || (f.getBlockX() == t.getBlockX() && f.getBlockY() == t.getBlockY() && f.getBlockZ() == t.getBlockZ())) return;
        if (jumpZones.isEmpty() && doubleJumpEnabled.isEmpty()) return;
        refreshDoubleJump(e.getPlayer(), t);
    }

    @EventHandler
    public void onDoubleJumpTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null || (jumpZones.isEmpty() && doubleJumpEnabled.isEmpty())) return;
        refreshDoubleJump(e.getPlayer(), e.getTo());
    }

    @EventHandler
    public void onDoubleJumpGameMode(PlayerGameModeChangeEvent e) {
        doubleJumpEnabled.remove(e.getPlayer().getUniqueId()); // uçuş iznini oyun modu belirler
    }

    @EventHandler
    public void onDoubleJumpQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (doubleJumpEnabled.remove(id)) { e.getPlayer().setFlying(false); e.getPlayer().setAllowFlight(false); }
        doubleJumpReadyAt.remove(id);
        jumpWandPos1.remove(id); jumpWandPos2.remove(id);
    }

    private void saveJumpZones() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ElytraZone z : jumpZones) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("world", z.world);
            m.put("minx", z.box.getMinX()); m.put("miny", z.box.getMinY()); m.put("minz", z.box.getMinZ());
            m.put("maxx", z.box.getMaxX()); m.put("maxy", z.box.getMaxY()); m.put("maxz", z.box.getMaxZ());
            list.add(m);
        }
        getConfig().set("ciftziplamabolgeleri", list);
    }

    @SuppressWarnings("unchecked")
    private void loadJumpZones() {
        jumpZones.clear();
        List<?> raw = getConfig().getList("ciftziplamabolgeleri");
        if (raw == null) return;
        for (Object o : raw) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) o;
            try {
                jumpZones.add(new ElytraZone((String) m.get("world"), new BoundingBox(
                    ((Number) m.get("minx")).doubleValue(), ((Number) m.get("miny")).doubleValue(), ((Number) m.get("minz")).doubleValue(),
                    ((Number) m.get("maxx")).doubleValue(), ((Number) m.get("maxy")).doubleValue(), ((Number) m.get("maxz")).doubleValue())));
            } catch (Exception ignored) {}
        }
    }

    private void loadData() {
        crates.clear(); crateItems.clear(); crateEffects.clear(); crateChances.clear(); crateEffectType.clear();
        crateColorOverride.clear();
        removeAllHolograms();
        loadArenaZones();
        loadJumpZones();

        // Küfür/spam uyarı renkleri
        kufurUyariRengi = "&" + getConfig().getString("kufur.uyari-rengi", "#d6a45c");
        kufurMuteRengi  = "&" + getConfig().getString("kufur.mute-rengi", "#ff4444");

        // Kasa özel renkleri
        if (getConfig().contains("crateColors")) {
            for (String name : Objects.requireNonNull(getConfig().getConfigurationSection("crateColors")).getKeys(false)) {
                String raw = getConfig().getString("crateColors." + name);
                if (raw != null && !raw.isEmpty()) {
                    String[] parts = raw.split(",");
                    String[] withAmp = new String[parts.length];
                    for (int i = 0; i < parts.length; i++) withAmp[i] = "&" + parts[i];
                    crateColorOverride.put(name, withAmp);
                }
            }
        }

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

    /**
     * Oyuncu elytra yasaklı bölgedeyken elytra takmaya çalışırsa (envanterden tıklayarak,
     * shift-click, sağ tık hızlı giyme vs. — hepsi bu event üzerinden tek noktadan yakalanır),
     * aynı ItemStack referansı korunarak zırh slotundan geri envantere iade edilir.
     * Klonlama yapılmadığı için dupe oluşmaz.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onElytraEquipAttempt(com.destroystokyo.paper.event.player.PlayerArmorChangeEvent ev) {
        if (ev.getSlotType() != com.destroystokyo.paper.event.player.PlayerArmorChangeEvent.SlotType.CHEST) return;
        ItemStack newItem = ev.getNewItem();
        if (newItem == null || newItem.getType() != Material.ELYTRA) return;
        Player p = ev.getPlayer();
        if (!isInAnyElytraZone(p.getLocation())) return;

        Bukkit.getScheduler().runTask(this, () -> {
            ItemStack current = p.getInventory().getChestplate();
            if (current == null || current.getType() != Material.ELYTRA) return; // durum değişmiş, dokunma
            ItemStack oldItem = ev.getOldItem();
            p.getInventory().setChestplate((oldItem != null && oldItem.getType() != Material.AIR) ? oldItem : null);
            HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(current);
            for (ItemStack extra : leftover.values()) p.getWorld().dropItemNaturally(p.getLocation(), extra);
            p.sendMessage(silveraPrefix() + "Bu bölgede Elytra takamazsın!");
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.7f, 0.6f);
        });
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
                p.sendActionBar(color(UI_BAD + "Bu duelde elytra kapalı."));
                return;
            }
        }
        // ── Arena Wand ile işaretlenmiş elytra yasaklı bölgeler ──
        if (isInAnyElytraZone(p.getLocation())) {
            e.setCancelled(true);
            warnElytraZone(p);
        }
    }

    /** Verilen konum, arena wand ile oluşturulmuş elytra yasaklı bölgelerden birinin içinde mi? */
    private boolean isInAnyElytraZone(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        String worldName = loc.getWorld().getName();
        Vector v = loc.toVector();
        for (ElytraZone z : elytraZones) {
            if (z.world.equals(worldName) && z.box.contains(v)) return true;
        }
        return false;
    }

    /** Silvera pembe/gri/beyaz temalı elytra uyarısını gönderir (spam olmasın diye kısa cooldown'lu). */
    private void warnElytraZone(Player p) {
        p.setGliding(false);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.7f, 0.6f);
        long now = System.currentTimeMillis();
        long last = elytraZoneWarnCooldown.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < 2000) return; // 2 saniyede bir mesaj — spam engeli
        elytraZoneWarnCooldown.put(p.getUniqueId(), now);
        p.sendMessage(silveraPrefix() + "Arena Bölgesinde Elytra Kullanamazsın!");
        p.sendActionBar(color("&#8a8a8a&fBu bölgede elytra süzülemez!"));
    }

    /**
     * Her hareket tikinde kontrol eder: oyuncu elytra yasaklı bir bölgedeyken açık kanatla
     * süzülüyorsa (ör. bölgeye dışarıdan uçarak/düşerek girdiyse — bu durumda toggle olayı
     * tekrar tetiklenmez) anında zorla indirir. Bu, EntityToggleGlideEvent'in tek başına
     * yakalayamadığı "zaten süzülürken bölgeye giriş" senaryosunu kapatır.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onElytraZoneMove(PlayerMoveEvent ev) {
        if (elytraZones.isEmpty()) return;
        Player p = ev.getPlayer();
        if (!p.isGliding()) return;
        if (!isInAnyElytraZone(p.getLocation())) return;
        warnElytraZone(p);
    }

    // ─── Citizens: /npc attribute scale <deger> ───
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onNpcAttributeScale(PlayerCommandPreprocessEvent ev) {
        String[] parts = ev.getMessage().trim().split("\\s+");
        if (parts.length != 4) return;
        if (!parts[0].equalsIgnoreCase("/npc")) return;
        if (!parts[1].equalsIgnoreCase("attribute")) return;
        if (!parts[2].equalsIgnoreCase("scale")) return;

        Player p = ev.getPlayer();
        if (!p.hasPermission("citizens.npc.attribute") && !p.hasPermission("citizens.admin")) return; // Citizens kendi mesajını versin

        ev.setCancelled(true); // Citizens'ın kendi attribute komutuyla çakışmasın

        double scaleVal;
        try {
            scaleVal = Double.parseDouble(parts[3].replace(",", "."));
        } catch (NumberFormatException ex) {
            p.sendMessage(silveraPrefix() + "Geçersiz sayı! Örnek: /npc attribute scale 2");
            return;
        }
        if (scaleVal <= 0 || scaleVal > 100) {
            p.sendMessage(silveraPrefix() + "Boyut 0 ile 100 arasında olmalı!");
            return;
        }

        net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getDefaultNPCSelector().getSelected(p);
        if (npc == null) {
            p.sendMessage(silveraPrefix() + "Önce bir NPC seçmelisin! (/npc select)");
            return;
        }

        ScaleTrait trait = npc.getOrAddTrait(ScaleTrait.class);
        trait.setScale(scaleVal);
        p.sendMessage(silveraPrefix() + npc.getName() + " isimli NPC'nin boyutu " + scaleVal + "x yapıldı.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
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
        Inventory inv = Bukkit.createInventory(null, 27, color("&#ffcc00&lİzin Yönetimi"));
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
            p.sendMessage(color("&#ff4444&l&fKont altındayken sadece &e/itiraf &f/ &b/anydesk &f/ &9/discord2 &fkomutlarını kullanabilirsin!"));
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

    // Kont altındaki oyuncu çıkarsa OTO-BAN — ban atılır atılmaz kont'tan da temizlenir (bug önlemi)
    @EventHandler
    public void onKontQuit(PlayerQuitEvent ev) {
        Player p = ev.getPlayer();
        UUID uid = p.getUniqueId();
        if (!kontUntil.containsKey(uid)) return;

        // Oto-ban uygula (isim bazlı, kalıcı)
        String banSebep = color("&#ff0000&lHile Kontrolündeyken Oyundan Çıktın!");
        Bukkit.getBanList(BanList.Type.NAME).addBan(p.getName(), ChatColor.stripColor(banSebep), null, "LoginX-Kont");

        // ÖNEMLİ: Ban açıldığında oyuncu barrier/kont itemiyle takılı kalmasın diye,
        // asıl eşyalarını (kontSavedInv) çıkmadan ÖNCE geri envanterine yaz.
        // Böylece disk'e kaydedilen playerdata gerçek eşyaları içerir, kont itemi kalmaz.
        // Ban zaten girişi engellediği için oyuncu bu eşyalara erişemez, sadece ban açılınca
        // normal envanteriyle geri döner.
        ItemStack[] saved = kontSavedInv.remove(uid);
        p.getInventory().clear();
        if (saved != null) p.getInventory().setContents(saved);

        // Kont verilerini hemen temizle — ban sonrası bug/tekrar giriş sorunlarını önle
        cancelKontTimeout(uid);
        kontUntil.remove(uid);
        kontYetkilisi.remove(uid);

        for (Player on : Bukkit.getOnlinePlayers()) {
            if (on.hasPermission("loginx.admin"))
                on.sendMessage(color("&#ff0000&l[KONT] &r&#ff6600" + p.getName() + " &7kont altındayken oyundan çıktı! &c&lOTO-BAN uygulandı."));
        }
    }

    // Güvenlik ağı: sunucu yeniden başlarsa kontUntil haritası RAM'de sıfırlanır ama
    // oyuncunun envanterinde hâlâ kont (barrier) itemi kalmış olabilir. Girişte temizle.
    @EventHandler
    public void onKontJoinCleanup(PlayerJoinEvent ev) {
        Player p = ev.getPlayer();
        if (kontUntil.containsKey(p.getUniqueId())) return; // hâlâ gerçekten kontta, dokunma
        boolean removed = false;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && it.hasItemMeta()
                    && it.getItemMeta().getPersistentDataContainer().has(kontTag, PersistentDataType.STRING)) {
                p.getInventory().setItem(i, null);
                removed = true;
            }
        }
        if (removed) {
            p.setWalkSpeed(0.2f); p.setFlySpeed(0.1f);
            p.sendMessage(color("&#ffcc00&l[KONT] &fEski kont kaydı temizlendi."));
        }
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
        Inventory inv = Bukkit.createInventory(null, 27, color("&#ff0000&lHile Kontrol Menüsü"));
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

    // ─── Kum/Çakıl vb. yerçekimine bağlı blokların düşmesini engelle (pixel art / build koruması) ───
    // Kum, kırmızı kum, çakıl ve tüm renkli beton tozu (concrete powder) blokları normalde suya değince
    // veya üstlerindeki blok kaldırılınca düşer. Pixel art buildlerde bu istenmiyor, blok koyulduğu
    // yerde kalmalı. BlockPhysicsEvent bu blok tiplerinde iptal edilerek yerçekimi devre dışı bırakılır.
    private static final Set<Material> NO_GRAVITY_BLOCKS;
    static {
        Set<Material> s = new HashSet<>();
        s.add(Material.SAND);
        s.add(Material.RED_SAND);
        s.add(Material.GRAVEL);
        for (Material m : Material.values()) {
            if (m.name().endsWith("_CONCRETE_POWDER")) s.add(m);
        }
        NO_GRAVITY_BLOCKS = s;
    }

    // ─── Kasa anahtarları (Ominous Bottle görünümlü) içilemesin — sadece kasa açmak için kullanılır ───
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKeyConsume(PlayerItemConsumeEvent ev) {
        ItemStack item = ev.getItem();
        if (item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(keyTag, PersistentDataType.STRING)) {
            ev.setCancelled(true);
            Player p = ev.getPlayer();
            p.sendTitle(color("&#ff9ecb&lSilvera"), color("&fBunu İçemezsin!"), 5, 40, 10);
            p.sendActionBar(color("&#ff9ecb&fBunu İçemezsin!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNoGravityPhysics(BlockPhysicsEvent ev) {
        if (NO_GRAVITY_BLOCKS.contains(ev.getBlock().getType())) {
            ev.setCancelled(true);
        }
    }

    // Zaten düşmeye başlamış (ör. dünya yüklenirken/eski falling block) bir blok varsa onu da
    // anında olduğu yere geri koyup entity'yi kaldırır — pixel art bloklarının kaybolmasını önler.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNoGravityFallingBlock(EntityChangeBlockEvent ev) {
        if (!(ev.getEntity() instanceof FallingBlock fb)) return;
        if (NO_GRAVITY_BLOCKS.contains(fb.getBlockData().getMaterial())) {
            ev.setCancelled(true);
            ev.getBlock().setBlockData(fb.getBlockData());
            fb.remove();
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  BAŞLATMA / KAPATMA (yeni sistemler)
    // ═════════════════════════════════════════════════════════════════════
    private final List<Command> dynamicCommands = new ArrayList<>();

    private class DynCmd extends Command {
        DynCmd(String name, String desc, String usage) {
            super(name, desc, usage, new ArrayList<>());
            setPermission("loginx.admin");
        }
        @Override public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.hasPermission("loginx.admin")) { sender.sendMessage(color("&cYetkisiz.")); return true; }
            return LoginX.this.onCommand(sender, this, label, args);
        }
    }

    private void registerDynamicCommand(String name, String desc, String usage) {
        if (getCommand(name) != null) return; // plugin.yml'de varsa onu kullan
        try {
            DynCmd dc = new DynCmd(name, desc, usage);
            Bukkit.getCommandMap().register("loginx", dc);
            dynamicCommands.add(dc);
        } catch (Throwable t) {
            getLogger().warning("Komut kaydedilemedi (" + name + "): " + t);
        }
    }

    private void initExtras() {
        noDropWandKey = new NamespacedKey(this, "esyadusme_wand");
        hudTag = new NamespacedKey(this, "hud_text");

        registerDynamicCommand("esyadusmewand", "Eşya düşme bölgesi baltasını verir", "/esyadusmewand");
        registerDynamicCommand("esyadusmeolustur", "Seçilen bölgede eşya düşme sistemini aktif eder", "/esyadusmeolustur");
        registerDynamicCommand("esyadusmesil", "Eşya düşme bölgesini siler", "/esyadusmesil <id>");

        loadNoDropRegions();
        loadRooms();
        roomGlassRecover();
        hudSweepOrphans();
        hudResetTeams();
        hudLoadPalette();

        // 1v1 Room motoru (3 tick'te bir)
        new BukkitRunnable() {
            @Override public void run() { tickRooms(); }
        }.runTaskTimer(this, 40L, 3L);

        // Oyuncu isim HUD'ı
        final int hudInterval = Math.max(1, getConfig().getInt("hud.guncelleme-tick", 4));
        new BukkitRunnable() {
            @Override public void run() {
                try { hudTick(); } catch (Throwable t) { getLogger().warning("HUD hatası: " + t); }
            }
        }.runTaskTimer(this, 40L, hudInterval);

        // HUD yazılarını oyuncuyu takip ettir (yolcu olarak bindirilmiyor — ışınlanmayı engellemesin)
        final int hudFollow = Math.max(1, getConfig().getInt("hud.konum.takip-tick", 1));
        new BukkitRunnable() {
            @Override public void run() {
                try { hudFollowTick(); } catch (Throwable t) { getLogger().warning("HUD takip hatası: " + t); }
            }
        }.runTaskTimer(this, 41L, hudFollow);
    }

    private void shutdownExtras() {
        try { hudRemoveAll(); hudRestoreTeams(); } catch (Throwable ignored) {}
        for (Room r : rooms.values()) {
            try { if (!r.glass.isEmpty()) roomOpenGlass(r); } catch (Throwable ignored) {}
        }
        try {
            org.bukkit.command.CommandMap map = Bukkit.getCommandMap();
            for (Command c : dynamicCommands) {
                c.unregister(map);
                map.getKnownCommands().values().removeIf(x -> x == c);
            }
        } catch (Throwable ignored) {}
        dynamicCommands.clear();
    }

    // ═════════════════════════════════════════════════════════════════════
    //  EŞYA DÜŞMEME SİSTEMİ
    //  - "mc.minerya.tc" dünyasında ölünce eşya düşmez (config: esyadusme.dunya)
    //  - Yalnızca /esyadusmeolustur ile tanımlanan bölgelerde ölenlerin eşyası düşer
    // ═════════════════════════════════════════════════════════════════════
    private static class NoDropRegion {
        final String id, world;
        final int minX, minY, minZ, maxX, maxY, maxZ;
        final BoundingBox box;
        NoDropRegion(String id, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
            this.id = id; this.world = world;
            minX = Math.min(x1, x2); maxX = Math.max(x1, x2);
            minY = Math.min(y1, y2); maxY = Math.max(y1, y2);
            minZ = Math.min(z1, z2); maxZ = Math.max(z1, z2);
            box = new BoundingBox(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        }
    }

    private final LinkedHashMap<String, NoDropRegion> noDropRegions = new LinkedHashMap<>();
    private final HashMap<UUID, Location> noDropPos1 = new HashMap<>();
    private final HashMap<UUID, Location> noDropPos2 = new HashMap<>();
    private NamespacedKey noDropWandKey;
    private int noDropCounter = 0;

    private String noDropMsg(String body) { return uiMsg("Eşya Düşme", body); }
    private String noDropErr(String body) { return uiErr("Eşya Düşme", body); }

    private boolean inNoDropRegion(Location l) {
        if (l == null || l.getWorld() == null) return false;
        for (NoDropRegion r : noDropRegions.values())
            if (r.world.equalsIgnoreCase(l.getWorld().getName()) && r.box.contains(l.toVector())) return true;
        return false;
    }

    /** true: eşyalar korunur (düşmez). Dünya eşleşiyor ve ölüm eşya-düşme bölgesinin DIŞINDA. */
    private boolean isNoDropDeath(Player dead) {
        String wn = getConfig().getString("esyadusme.dunya", "mc.minerya.tc");
        if (dead.getWorld() == null || !dead.getWorld().getName().equalsIgnoreCase(wn)) return false;
        return !inNoDropRegion(dead.getLocation());
    }

    private boolean isNoDropWand(ItemStack it) {
        if (it == null || it.getType() == Material.AIR || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(noDropWandKey, PersistentDataType.BYTE);
    }

    private ItemStack buildNoDropWand() {
        ItemStack axe = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta m = axe.getItemMeta();
        m.setDisplayName(color("&r" + UI_ACCENT + "&lEşya Düşme Baltası"));
        List<String> lore = new ArrayList<>();
        lore.add(color("&r" + UI_DIM + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        lore.add(color("&r" + UI_SUB + "Sol tık " + UI_TEXT + "→ " + UI_SUB + "Birinci köşe"));
        lore.add(color("&r" + UI_SUB + "Sağ tık " + UI_TEXT + "→ " + UI_SUB + "İkinci köşe"));
        lore.add(color("&r" + UI_SUB + "Ardından " + UI_ACCENT + "/esyadusmeolustur"));
        lore.add(color("&r" + UI_DIM + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        m.setLore(lore);
        m.setUnbreakable(true);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        m.addEnchant(Enchantment.UNBREAKING, 1, true); // parıltı
        m.getPersistentDataContainer().set(noDropWandKey, PersistentDataType.BYTE, (byte) 1);
        axe.setItemMeta(m);
        return axe;
    }

    private boolean handleNoDropCommand(CommandSender sender, String cmdName, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Bu komut sadece oyuncular içindir."); return true; }
        if (!p.hasPermission("loginx.admin")) { p.sendMessage(color("&cYetkisiz.")); return true; }
        UUID uid = p.getUniqueId();

        if (cmdName.equals("esyadusmewand")) {
            p.getInventory().addItem(buildNoDropWand());
            p.sendMessage(noDropMsg("Eşya düşme baltası envanterine eklendi. " + hl("Sol tık") + " birinci, " + hl("sağ tık") + " ikinci köşe."));
            roomSound(p, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4f, 1.2f);
            return true;
        }

        if (cmdName.equals("esyadusmeolustur")) {
            Location l1 = noDropPos1.get(uid), l2 = noDropPos2.get(uid);
            if (l1 == null || l2 == null) {
                p.sendMessage(noDropErr("Önce baltayla iki köşeyi seç. Baltan yoksa " + hl("/esyadusmewand") + " kullan."));
                return true;
            }
            if (l1.getWorld() == null || !l1.getWorld().equals(l2.getWorld())) {
                p.sendMessage(noDropErr("İki köşe aynı dünyada olmalı."));
                return true;
            }
            String id;
            do { id = "bolge_" + (++noDropCounter); } while (noDropRegions.containsKey(id));
            NoDropRegion r = new NoDropRegion(id, l1.getWorld().getName(),
                l1.getBlockX(), l1.getBlockY(), l1.getBlockZ(), l2.getBlockX(), l2.getBlockY(), l2.getBlockZ());
            noDropRegions.put(id, r);
            saveNoDropRegions(true);
            noDropPos1.remove(uid); noDropPos2.remove(uid);
            p.sendMessage(noDropMsg("Bölge oluşturuldu: " + hl(id) + " (" + (r.maxX - r.minX + 1) + " x " + (r.maxY - r.minY + 1) + " x " + (r.maxZ - r.minZ + 1) + ")."));
            p.sendMessage(noDropMsg("Artık yalnızca bu bölgede ölen oyuncuların eşyaları düşer. Diğer yerlerde envanter korunur."));
            roomSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.2f);
            return true;
        }

        if (cmdName.equals("esyadusmesil")) {
            if (args.length < 1) {
                p.sendMessage(noDropMsg("Kullanım: " + hl("/esyadusmesil <id>") + ". Mevcut bölgeler: "
                    + (noDropRegions.isEmpty() ? hl("yok") : hl(String.join(", ", noDropRegions.keySet())))));
                return true;
            }
            if (noDropRegions.remove(args[0]) == null) { p.sendMessage(noDropErr("Bölge bulunamadı: " + hl(args[0]))); return true; }
            saveNoDropRegions(true);
            p.sendMessage(noDropMsg(hl(args[0]) + " bölgesi silindi."));
            return true;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNoDropWand(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.LEFT_CLICK_BLOCK && a != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!isNoDropWand(p.getInventory().getItemInMainHand())) return;
        e.setCancelled(true);
        if (!p.hasPermission("loginx.admin") || e.getClickedBlock() == null) return;
        Location loc = e.getClickedBlock().getLocation();
        String pos = loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
        if (a == Action.LEFT_CLICK_BLOCK) {
            noDropPos1.put(p.getUniqueId(), loc);
            p.sendMessage(noDropMsg("Birinci köşe: " + hl(pos)));
        } else {
            noDropPos2.put(p.getUniqueId(), loc);
            p.sendMessage(noDropMsg("İkinci köşe: " + hl(pos)));
        }
        roomSound(p, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, a == Action.LEFT_CLICK_BLOCK ? 1.2f : 1.5f);
    }

    private void saveNoDropRegions(boolean writeNow) {
        getConfig().set("esyadusme.bolgeler", null);
        for (NoDropRegion r : noDropRegions.values()) {
            String b = "esyadusme.bolgeler." + r.id;
            getConfig().set(b + ".world", r.world);
            getConfig().set(b + ".min", r.minX + "," + r.minY + "," + r.minZ);
            getConfig().set(b + ".max", r.maxX + "," + r.maxY + "," + r.maxZ);
        }
        if (writeNow) saveConfig();
    }

    private void loadNoDropRegions() {
        noDropRegions.clear();
        if (!getConfig().isConfigurationSection("esyadusme.bolgeler")) return;
        for (String id : getConfig().getConfigurationSection("esyadusme.bolgeler").getKeys(false)) {
            try {
                String base = "esyadusme.bolgeler." + id;
                String[] mn = getConfig().getString(base + ".min", "").split(",");
                String[] mx = getConfig().getString(base + ".max", "").split(",");
                noDropRegions.put(id, new NoDropRegion(id, getConfig().getString(base + ".world", "world"),
                    Integer.parseInt(mn[0].trim()), Integer.parseInt(mn[1].trim()), Integer.parseInt(mn[2].trim()),
                    Integer.parseInt(mx[0].trim()), Integer.parseInt(mx[1].trim()), Integer.parseInt(mx[2].trim())));
                try { noDropCounter = Math.max(noDropCounter, Integer.parseInt(id.substring(id.lastIndexOf('_') + 1))); } catch (Exception ignored) {}
            } catch (Exception ex) {
                getLogger().warning("Eşya düşme bölgesi yüklenemedi (" + id + "): " + ex.getMessage());
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  OYUNCU İSİM HUD'I
    //    [Klan adı]   -> yavaş animasyonlu renk geçişi (SimpleClans; klan yoksa boş satır)
    //    Oyuncu adı   -> LuckPerms prefix'i + isim
    //    Can 20       -> gerçek cana göre canlı güncellenir, renk canla birlikte değişir
    //  Hepsi tek TextDisplay (textshadow: true), oyuncunun kendisine görünmez.
    //  Config (hepsi opsiyonel): hud.aktif, hud.textshadow, hud.y-offset, hud.olcek,
    //  hud.guncelleme-tick, hud.duvar-arkasi, hud.prefix-goster, hud.isim-rengi,
    //  hud.vanilla-isim-gizle, hud.klan.renkler, hud.klan.hiz, hud.klan.yayilim,
    //  hud.klan.gosterim (isim|tag), hud.klan.kalin
    // ═════════════════════════════════════════════════════════════════════
    private static final char SEC = '\u00a7';

    private static class Hud {
        TextDisplay display;
        String last = "";
        String clan = null, prefix = "";
        long cacheAt = 0, teamAt = 0, suppressUntil = 0;
    }

    private NamespacedKey hudTag;
    private final HashMap<UUID, Hud> huds = new HashMap<>();
    private double hudPhase = 0;
    private int hudPaletteCounter = 0;
    private int[] hudPalette = {0xff9ecb, 0xffffff, 0xff7ab6, 0xffffff};

    private void hudLoadPalette() {
        List<String> l = getConfig().getStringList("hud.klan.renkler");
        if (l == null || l.size() < 2) return;
        try {
            int[] out = new int[l.size()];
            for (int i = 0; i < out.length; i++) out[i] = Integer.parseInt(l.get(i).replace("#", "").trim(), 16) & 0xFFFFFF;
            hudPalette = out;
        } catch (Exception ex) {
            getLogger().warning("hud.klan.renkler okunamadı, varsayılan palet kullanılıyor.");
        }
    }

    private static String hudHex(int rgb) {
        String h = String.format("%06x", rgb & 0xFFFFFF);
        StringBuilder sb = new StringBuilder().append(SEC).append('x');
        for (char c : h.toCharArray()) sb.append(SEC).append(c);
        return sb.toString();
    }
    private static int hudLerp(int a, int b, double f) {
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * f);
        int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * f);
        int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * f);
        return (r << 16) | (g << 8) | bl;
    }
    private int hudSample(double t) {
        int n = hudPalette.length;
        t = t - Math.floor(t);
        double pos = t * n;
        int i0 = ((int) Math.floor(pos)) % n, i1 = (i0 + 1) % n;
        double f = pos - Math.floor(pos);
        f = f * f * (3 - 2 * f); // yumuşak geçiş
        return hudLerp(hudPalette[i0], hudPalette[i1], f);
    }

    private String hudGradient(String text) {
        double spread = getConfig().getDouble("hud.klan.yayilim", 0.09);
        boolean bold = getConfig().getBoolean("hud.klan.kalin", false);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(hudHex(hudSample(hudPhase + i * spread)));
            if (bold) sb.append(SEC).append('l');
            sb.append(text.charAt(i));
        }
        return sb.toString();
    }

    private int hudHealthColor(double ratio) {
        ratio = Math.max(0, Math.min(1, ratio));
        final int green = 0x5dff7a, yellow = 0xffd84d, red = 0xff4d4d;
        return ratio >= 0.5 ? hudLerp(yellow, green, (ratio - 0.5) / 0.5) : hudLerp(red, yellow, ratio / 0.5);
    }

    // ── HUD yerleşimi (Minecraft ölçülerine göre) ───────────────────────────────────────────────
    //  Vanilla isim etiketi: pivot = oyuncu yüksekliği (p.getHeight) + 0.5; yazı 1/40 blok/piksel (0.025),
    //  satır kutusu 9 piksel, taban çizgisi pivotun 8 piksel altında  => taban çizgisi = h + 0.3.
    //  TextDisplay: 1/40 blok/piksel, satır aralığı 10 piksel, blok TABANDAN yukarı büyür (varlık konumu = blok tabanı).
    //  Ölçek 1.0 olunca yazının piksel yoğunluğu vanilla nick ile birebir aynıdır (bulanık/kırık piksel yok).
    //  Düzen "orta": Klan / [vanilla nick boşluğu] / Can   |   "ust": Klan / Can / [vanilla nick boşluğu]
    private static final double HUD_PX = 0.025;          // 1 yazı pikseli = 1/40 blok
    private static final double HUD_NAME_BASELINE = 0.3; // nick taban çizgisi (h + 0.3)
    private static final int HUD_LINE_PITCH = 10;        // TextDisplay satır aralığı (piksel)

    private double hudScale() {
        return Math.max(0.5, Math.min(2.0, getConfig().getDouble("hud.konum.olcek", 1.0)));
    }
    /** Varlık konumuna (= p.getHeight() hizası) göre dikey kaydırma. Nick satırı boşluğunun taban çizgisini hizalar. */
    private float hudYOffset() {
        double s = hudScale();
        double basePx = getConfig().getDouble("hud.konum.taban-px", 3.5); // blok tabanından ilk satırın taban çizgisine uzaklık (piksel)
        // Alt satır = nick (vanilla nick ile aynı taban çizgisi), üst satır = Klan + Can
        return (float) (HUD_NAME_BASELINE - basePx * HUD_PX * s);
    }

    /** Yolcu modu mu? (true: HUD oyuncuya binmiş, hareket ederken sıfır gecikme; false: bağımsız ve takip eden) */
    private boolean hudPassengerMode() {
        return !"takip".equalsIgnoreCase(getConfig().getString("hud.mod", "yolcu"));
    }

    /**
     * İki satır, tek display:
     *   üst : [Klan]  Can 20      (klan yoksa yalnızca Can — aynı satırda)
     *   alt : Prefix Nick         (vanilla nick gizlenir; arka plan yok, gölge textshadow ayarına bağlı)
     */
    private String hudBuildText(Player p, Hud h) {
        double max = Math.max(1.0, p.getMaxHealth());
        int hp = (int) Math.ceil(Math.max(0, p.getHealth()));
        String hpPart = hudHex(0xf2f4f8) + "Can " + hudHex(hudHealthColor(p.getHealth() / max)) + hp;
        String top = (h.clan != null && !h.clan.isEmpty())
            ? hudGradient(h.clan) + SEC + "r  " + hpPart
            : hpPart;
        String nick;
        if (getConfig().getBoolean("hud.nick-goster", true)) {
            String pre = getConfig().getBoolean("hud.prefix-goster", true) ? h.prefix : "";
            int nameRgb = 0xffffff;
            try { nameRgb = Integer.parseInt(getConfig().getString("hud.isim-rengi", "#ffffff").replace("#", "").trim(), 16) & 0xFFFFFF; } catch (Exception ignored) {}
            nick = pre + SEC + "r" + hudHex(nameRgb) + p.getName();
        } else {
            nick = SEC + "r ";
        }
        return top + "\n" + nick;
    }

    // ── SimpleClans (yansıtma — derleme bağımlılığı gerekmez) ──
    private String hudClanName(Player p) {
        try {
            org.bukkit.plugin.Plugin sc = Bukkit.getPluginManager().getPlugin("SimpleClans");
            if (sc == null || !sc.isEnabled()) return null;
            Object cm = sc.getClass().getMethod("getClanManager").invoke(sc);
            java.lang.reflect.Method gcp;
            try {
                Class<?> cmCls = Class.forName("net.sacredlabyrinth.phaed.simpleclans.managers.ClanManager", true, sc.getClass().getClassLoader());
                gcp = cmCls.getMethod("getClanPlayer", Player.class);
            } catch (ClassNotFoundException cnf) {
                gcp = cm.getClass().getMethod("getClanPlayer", Player.class);
            }
            Object cp = gcp.invoke(cm, p);
            if (cp == null) return null;
            Object clan = cp.getClass().getMethod("getClan").invoke(cp);
            if (clan == null) return null;
            String mode = getConfig().getString("hud.klan.gosterim", "isim");
            Object val = clan.getClass().getMethod(mode.equalsIgnoreCase("tag") ? "getTag" : "getName").invoke(clan);
            if (val == null) return null;
            String s = ChatColor.stripColor(color(String.valueOf(val))).trim();
            return s.isEmpty() ? null : s;
        } catch (Throwable t) {
            return null;
        }
    }

    // ── LuckPerms prefix (yansıtma) ──
    private String hudPrefix(Player p) {
        try {
            org.bukkit.plugin.Plugin lp = Bukkit.getPluginManager().getPlugin("LuckPerms");
            if (lp == null || !lp.isEnabled()) return "";
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = provider.getMethod("get").invoke(null);
            Class<?> apiCls = Class.forName("net.luckperms.api.LuckPerms");
            Object adapter = apiCls.getMethod("getPlayerAdapter", Class.class).invoke(api, Player.class);
            Class<?> adapterCls = Class.forName("net.luckperms.api.platform.PlayerAdapter");
            Object meta = adapterCls.getMethod("getMetaData", Object.class).invoke(adapter, p);
            Class<?> metaCls = Class.forName("net.luckperms.api.cacheddata.CachedMetaData");
            Object pre = metaCls.getMethod("getPrefix").invoke(meta);
            if (pre == null) return "";
            String s = color(String.valueOf(pre));
            return s.endsWith(" ") ? s : s + " ";
        } catch (Throwable t) {
            return "";
        }
    }

    private boolean hudShouldShow(Player p) {
        if (!p.isOnline() || p.isDead()) return false;
        if (p.getGameMode() == GameMode.SPECTATOR) return false;
        if (p.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY)) return false;
        if (p.hasMetadata("vanished")) return false;
        return true;
    }

    private void hudRemoveDisplay(Hud h) {
        if (h == null) return;
        if (h.display != null) { try { h.display.remove(); } catch (Throwable ignored) {} }
        h.display = null; h.last = "";
    }

    private void hudRemoveAll() {
        for (Hud h : huds.values()) hudRemoveDisplay(h);
        huds.clear();
    }

    /** HUD yazısının oyuncunun başının üstündeki konumu. */
    private Location hudAnchor(Player p) {
        Location l = p.getLocation();
        l.setYaw(0f);
        l.setPitch(0f);
        return l.add(0, p.getHeight(), 0);
    }

    /** Eski sürümden kalma, oyuncuya yolcu olarak bindirilmiş HUD yazılarını söker. */
    private void hudDetachLegacyPassengers(Player p) {
        for (Entity e : new ArrayList<>(p.getPassengers())) {
            if (e instanceof TextDisplay td && td.getPersistentDataContainer().has(hudTag, PersistentDataType.BYTE)) {
                p.removePassenger(td);
                td.remove();
            }
        }
    }

    /**
     * HUD yazısı oyuncuya YOLCU olarak bindirilmez: üzerinde yolcu olan bir oyuncuyu ışınlamak (Advanced Portals,
     * warp, hub, /tp) reddedilebilir. Bunun yerine yazı bağımsız durur ve hudFollowTick() ile oyuncuyu takip eder;
     * istemci tarafı yumuşatma (teleport duration) ile akıcı görünür.
     */
    private void hudEnsureDisplay(Player p, Hud h) {
        TextDisplay d = h.display;
        boolean ride = hudPassengerMode();
        if (d != null && d.isValid() && d.getWorld().equals(p.getWorld())
                && (!ride || p.getPassengers().contains(d))) return;
        hudRemoveDisplay(h);
        hudDetachLegacyPassengers(p);
        try {
            TextDisplay nd = p.getWorld().spawn(hudAnchor(p), TextDisplay.class);
            nd.setPersistent(false);
            nd.getPersistentDataContainer().set(hudTag, PersistentDataType.BYTE, (byte) 1);
            nd.setBillboard(Display.Billboard.CENTER);
            nd.setAlignment(TextDisplay.TextAlignment.CENTER);
            nd.setShadowed(getConfig().getBoolean("hud.textshadow", true));
            nd.setSeeThrough(getConfig().getBoolean("hud.duvar-arkasi", false));
            nd.setDefaultBackground(false);
            nd.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            nd.setLineWidth(400);
            nd.setViewRange(1.0f);
            nd.setBrightness(new Display.Brightness(15, 15));
            float sc = (float) hudScale();
            nd.setTransformation(new org.bukkit.util.Transformation(
                new org.joml.Vector3f(0f, hudYOffset(), 0f), new org.joml.AxisAngle4f(),
                new org.joml.Vector3f(sc, sc, sc), new org.joml.AxisAngle4f()));
            // Yumuşatma: istemcinin yazıyı yeni konuma kaç tickte taşıyacağı. 1 = en az gecikme, 3 = oyuncu modeline en yakın akıcılık.
            nd.setTeleportDuration(Math.min(59, Math.max(0, getConfig().getInt("hud.konum.yumusatma-tick", 2))));
            p.hideEntity(this, nd);   // oyuncu kendi HUD'ını görmesin
            if (ride && !p.addPassenger(nd)) { nd.remove(); h.display = null; return; }
            h.display = nd;
            h.last = "";
        } catch (Throwable t) {
            h.display = null;
        }
    }

    /** HUD yazılarını oyuncuların başının üstüne taşır (yalnızca konum değiştiyse). */
    private void hudFollowTick() {
        if (huds.isEmpty() || hudPassengerMode()) return; // yolcu modunda takip gerekmez: istemci konumu oyuncuyla birlikte işler
        for (Map.Entry<UUID, Hud> en : huds.entrySet()) {
            TextDisplay d = en.getValue().display;
            if (d == null || !d.isValid()) continue;
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null || !p.isOnline() || !d.getWorld().equals(p.getWorld())) continue; // dünya değiştiyse hudTick yeniden kurar
            Location target = hudAnchor(p);
            if (d.getLocation().distanceSquared(target) < 1.0e-8) continue; // yalnızca gerçekten aynı konumdaysa atla
            d.teleport(target);
        }
    }

    // ── Yolcu modu güvenliği ─────────────────────────────────────────────────────────────────────
    //  Üzerinde yolcu olan oyuncuyu ışınlamak Paper'da reddedilir. Bu yüzden HUD, ışınlanma öncesinde
    //  sökülür ve sonra (PlayerTeleportEvent -> hudRefreshLater) yeniden bindirilir:
    //   * LoginX'in kendi ışınlamaları  -> hudTp()
    //   * Işınlatan komutlar (hub, warp, spawn, tpa...) -> komut yazıldığı anda sökülür, hud.yolcu.komut-sure sn bekler
    //   * Portal blokları (hud.yolcu.portal-bloklari) yakınında HUD hiç bindirilmez
    private void hudDropNow(Player p, long suppressMs) {
        Hud h = huds.get(p.getUniqueId());
        if (h == null) return;
        if (suppressMs > 0) h.suppressUntil = Math.max(h.suppressUntil, System.currentTimeMillis() + suppressMs);
        hudRemoveDisplay(h);
    }
    private boolean hudTp(Player p, Location l) {
        hudDropNow(p, 0);
        return p.teleport(l);
    }
    private Set<Material> hudPortalMats() {
        Set<Material> s = EnumSet.noneOf(Material.class);
        for (String n : protList("hud.yolcu.portal-bloklari", "NETHER_PORTAL", "END_PORTAL", "END_GATEWAY")) {
            try { s.add(Material.valueOf(n.trim().toUpperCase(Locale.ROOT))); } catch (Exception ignored) {}
        }
        return s;
    }
    private boolean hudNearPortal(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Set<Material> mats = hudPortalMats();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = 0; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    if (mats.contains(loc.getWorld().getBlockAt(bx + dx, by + dy, bz + dz).getType())) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHudTeleportCommand(PlayerCommandPreprocessEvent ev) {
        if (!hudPassengerMode()) return;
        String raw = ev.getMessage().trim();
        if (raw.startsWith("/")) raw = raw.substring(1);
        String label = raw.split("\\s+")[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        for (String c : protList("hud.yolcu.komutlar", "hub", "lobby", "spawn", "warp", "ewarp", "home", "ehome",
                "tpa", "tpaccept", "tpyes", "back", "rtp", "tp", "tpo", "tphere", "kit", "duel", "1v1", "1v1kabul", "dueller")) {
            if (c.equalsIgnoreCase(label)) {
                long ms = getConfig().getLong("hud.yolcu.komut-sure", 8) * 1000L;
                hudDropNow(ev.getPlayer(), ms);
                return;
            }
        }
    }

    /** Nick etiketini izleyen oyuncunun KENDİ scoreboard'u belirler; bu yüzden ana scoreboard + her oyuncunun scoreboard'u işlenir. */
    private List<org.bukkit.scoreboard.Scoreboard> hudBoards() {
        List<org.bukkit.scoreboard.Scoreboard> l = new ArrayList<>();
        try {
            l.add(Bukkit.getScoreboardManager().getMainScoreboard());
            for (Player v : Bukkit.getOnlinePlayers()) {
                org.bukkit.scoreboard.Scoreboard sb = v.getScoreboard();
                boolean dup = false;
                for (org.bukkit.scoreboard.Scoreboard x : l) if (x == sb) { dup = true; break; }
                if (!dup) l.add(sb);
            }
        } catch (Throwable ignored) {}
        return l;
    }
    private final IdentityHashMap<org.bukkit.scoreboard.Scoreboard, Map<String, org.bukkit.scoreboard.Team.OptionStatus>> hudForeignByBoard = new IdentityHashMap<>();

    private void hudHideVanilla(Player p) {
        if (!getConfig().getBoolean("hud.vanilla-isim-gizle", true)) return;
        for (org.bukkit.scoreboard.Scoreboard sb : hudBoards()) {
            try {
                org.bukkit.scoreboard.Team t = sb.getEntryTeam(p.getName());
                if (t == null) {
                    t = sb.getTeam("lxhud");
                    if (t == null) {
                        t = sb.registerNewTeam("lxhud");
                        t.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
                    }
                    t.addEntry(p.getName());
                } else if (!t.getName().equals("lxhud")
                        && t.getOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY) != org.bukkit.scoreboard.Team.OptionStatus.NEVER) {
                    hudForeignByBoard.computeIfAbsent(sb, k -> new HashMap<>())
                        .putIfAbsent(t.getName(), t.getOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY));
                    t.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
                }
            } catch (Throwable ignored) {}
        }
    }

    private void hudResetTeams() {
        for (org.bukkit.scoreboard.Scoreboard sb : hudBoards()) {
            try {
                org.bukkit.scoreboard.Team t = sb.getTeam("lxhud");
                if (t != null) t.unregister();
            } catch (Throwable ignored) {}
        }
    }

    private void hudRestoreTeams() {
        try {
            Set<org.bukkit.scoreboard.Scoreboard> all = Collections.newSetFromMap(new IdentityHashMap<>());
            all.addAll(hudBoards());
            all.addAll(hudForeignByBoard.keySet());
            for (org.bukkit.scoreboard.Scoreboard sb : all) {
                try {
                    org.bukkit.scoreboard.Team t = sb.getTeam("lxhud");
                    if (t != null) t.unregister();
                    Map<String, org.bukkit.scoreboard.Team.OptionStatus> m = hudForeignByBoard.get(sb);
                    if (m != null) for (Map.Entry<String, org.bukkit.scoreboard.Team.OptionStatus> en : m.entrySet()) {
                        org.bukkit.scoreboard.Team ft = sb.getTeam(en.getKey());
                        if (ft != null) ft.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, en.getValue());
                    }
                } catch (Throwable ignored) {}
            }
            hudForeignByBoard.clear();
        } catch (Throwable ignored) {}
    }

    private void hudSweepOrphans() {
        for (World w : Bukkit.getWorlds()) {
            for (TextDisplay td : w.getEntitiesByClass(TextDisplay.class)) {
                if (td.getPersistentDataContainer().has(hudTag, PersistentDataType.BYTE)) td.remove();
            }
        }
    }

    @EventHandler
    public void onHudEntitiesLoad(org.bukkit.event.world.EntitiesLoadEvent e) {
        if (hudTag == null) return;
        for (Entity ent : e.getEntities()) {
            if (ent instanceof TextDisplay td && td.getPersistentDataContainer().has(hudTag, PersistentDataType.BYTE)) td.remove();
        }
    }

    private net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer hudSer;
    /** §x§r§r§g§g§b§b hex biçimini ve satır sonlarını doğru çözen serileştirici. */
    private net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer hudSerializer() {
        if (hudSer == null) {
            hudSer = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.builder()
                .character(SEC).hexColors().useUnusualXRepeatedCharacterHexFormat().build();
        }
        return hudSer;
    }

    private void hudTick() {
        if (!getConfig().getBoolean("hud.aktif", true)) {
            if (!huds.isEmpty()) { hudRemoveAll(); hudRestoreTeams(); }
            return;
        }
        if (++hudPaletteCounter >= 100) { hudPaletteCounter = 0; hudLoadPalette(); }
        hudPhase = (hudPhase + getConfig().getDouble("hud.klan.hiz", 0.012)) % 1.0;
        long nowMs = System.currentTimeMillis();

        for (Player p : Bukkit.getOnlinePlayers()) {
            Hud h = huds.computeIfAbsent(p.getUniqueId(), k -> new Hud());
            if (!hudShouldShow(p)) { hudRemoveDisplay(h); continue; }
            if (nowMs - h.cacheAt > 1000L) {
                h.clan = hudClanName(p);
                h.prefix = hudPrefix(p);
                h.cacheAt = nowMs;
            }
            if (nowMs - h.teamAt > 2000L) { hudHideVanilla(p); h.teamAt = nowMs; }
            if (nowMs < h.suppressUntil || (hudPassengerMode() && hudNearPortal(p.getLocation()))) { hudRemoveDisplay(h); continue; }
            hudEnsureDisplay(p, h);
            if (h.display == null) continue;
            String text = hudBuildText(p, h);
            if (!text.equals(h.last)) {
                h.display.text(hudSerializer().deserialize(text));
                h.last = text;
            }
        }
        // Çıkmış oyuncuların kalıntıları
        huds.entrySet().removeIf(en -> {
            Player op = Bukkit.getPlayer(en.getKey());
            if (op == null || !op.isOnline()) { hudRemoveDisplay(en.getValue()); return true; }
            return false;
        });
    }

    @EventHandler
    public void onHudQuit(PlayerQuitEvent e) {
        Hud h = huds.remove(e.getPlayer().getUniqueId());
        hudRemoveDisplay(h);
        try {
            for (org.bukkit.scoreboard.Scoreboard sb : hudBoards()) {
                org.bukkit.scoreboard.Team t = sb.getTeam("lxhud");
                if (t != null) t.removeEntry(e.getPlayer().getName());
            }
        } catch (Throwable ignored) {}
    }

    // Işınlanma / dünya değişimi: HUD yazısı yeni konumda yeniden kurulur
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHudTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        Hud h = huds.get(p.getUniqueId());
        Location to = e.getTo();
        // Uzak ışınlanmada (başka dünya / 8+ blok) yazıyı hemen kaldır; 2 tick sonra yeni konumda yeniden kurulur
        if (h != null && h.display != null && to != null && to.getWorld() != null
                && (!to.getWorld().equals(e.getFrom().getWorld()) || to.distanceSquared(e.getFrom()) > 64)) {
            hudRemoveDisplay(h);
        }
        hudRefreshLater(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHudWorldChange(PlayerChangedWorldEvent e) { hudRefreshLater(e.getPlayer()); }

    private void hudRefreshLater(Player p) {
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) return;
                Hud h = huds.get(p.getUniqueId());
                if (h == null || !hudShouldShow(p)) return;
                if (System.currentTimeMillis() < h.suppressUntil) return;   // hudTick süre dolunca yeniden kurar
                hudEnsureDisplay(p, h);
            }
        }.runTaskLater(this, 2L);
    }

}
