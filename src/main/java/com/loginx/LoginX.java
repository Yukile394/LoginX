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
    private static final long PROTECTION_MS = 3600_000L; // 1 saat
    private final HashMap<UUID, Integer> protectionTask = new HashMap<>();
    private final HashMap<UUID, Boolean> pendingProtOff  = new HashMap<>();

    // ─── Stash Sistemi ───
    private final HashMap<UUID, ItemStack[]> playerStash = new HashMap<>();
    private final HashMap<UUID, Boolean>     stashOpen   = new HashMap<>();

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

    // ─── DUEL Sistemi ───
    // DuelArena: id, spawn1, spawn2, dolu mu
    private final HashMap<String, Location>  duelSpawn1    = new HashMap<>(); // duelId -> spawn1
    private final HashMap<String, Location>  duelSpawn2    = new HashMap<>(); // duelId -> spawn2
    private final HashMap<String, UUID[]>    duelPlayers   = new HashMap<>(); // duelId -> [p1,p2] (aktif)
    private final HashMap<UUID, String>      playerDuel    = new HashMap<>(); // uid -> duelId
    // Duel ayarları (oyuncu bazlı)
    private final HashMap<UUID, Boolean>     duelWebEnabled  = new HashMap<>(); // ağ kullan mı
    private final HashMap<UUID, Boolean>     duelElyEnabled  = new HashMap<>(); // elytra kullan mı
    // Duel davetiyeleri
    private final HashMap<UUID, UUID>        duelInvite    = new HashMap<>(); // davet alan -> gönderen
    private final HashMap<UUID, Boolean>     duelInviteWeb = new HashMap<>();
    private final HashMap<UUID, Boolean>     duelInviteEly = new HashMap<>();
    private final HashMap<UUID, Integer>     duelInviteTask= new HashMap<>(); // timeout task
    // Duel countdown/freeze
    private final HashMap<UUID, Boolean>     duelFrozen    = new HashMap<>();

    // ─── Kontrol (Kont) Sistemi ───
    private Location kontYeri = null;
    private final HashMap<UUID, Long>         kontUntil       = new HashMap<>();
    private final HashMap<UUID, UUID>         kontYetkilisi   = new HashMap<>();
    private final HashMap<UUID, Integer>      kontTimeoutTask = new HashMap<>();
    private final HashMap<UUID, ItemStack[]>  kontSavedInv    = new HashMap<>(); // RAM'de sakla
    private NamespacedKey kontTag;

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
        CRATE_COLORS.put("all",     new String[]{"&#ff0000","&#ff6600","&#ffff00","&#00ff00","&#00ffff","&#aa00ff","&#ff00cc","&#ff0000"});
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
        saveDefaultConfig(); loadData();
        // Kont yeri config'den yükle
        if (getConfig().contains("kontyeri")) kontYeri = getConfig().getLocation("kontyeri");
        // Duel arenaları yükle
        if (getConfig().contains("duels")) {
            for (String did : getConfig().getConfigurationSection("duels").getKeys(false)) {
                duelSpawn1.put(did, getConfig().getLocation("duels." + did + ".spawn1"));
                duelSpawn2.put(did, getConfig().getLocation("duels." + did + ".spawn2"));
            }
        }

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
            p.sendMessage(color("&#00ff88&l[DUEL] &f" + did + " &7— Spawn 1 ayarlandı: &b" + p.getBlockX() + "," + p.getBlockY() + "," + p.getBlockZ()));
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
            p.sendMessage(color("&#00ff88&l[DUEL] &f" + did + " &7— Spawn 2 ayarlandı: &b" + p.getBlockX() + "," + p.getBlockY() + "," + p.getBlockZ()));
            return true;
        }

        // /dueller — duel arenalarını listele / gir
        if (cmdName.equals("dueller")) {
            if (!(sender instanceof Player p)) return true;
            openDuelListMenu(p); return true;
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

        // /1v1 (oyuncu) — duel daveti gönder
        if (cmdName.equals("1v1")) {
            if (!(sender instanceof Player p)) return true;
            if (args.length < 1) { p.sendMessage(color("&cKullanim: /1v1 <oyuncu>")); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || target.equals(p)) { p.sendMessage(color("&cGüvenli oyuncu değil.")); return true; }
            if (playerDuel.containsKey(p.getUniqueId())) { p.sendMessage(color("&cZaten bir dueldasın.")); return true; }
            if (playerDuel.containsKey(target.getUniqueId())) { p.sendMessage(color("&c" + target.getName() + " zaten bir duelda.")); return true; }
            // Boş duel arenası bul
            String freeArena = findFreeDuelArena();
            if (freeArena == null) { p.sendMessage(color("&#ff4444&l[DUEL] &cŞu an boş arena yok!")); return true; }
            // Ayarları sakla
            duelInvite.put(target.getUniqueId(), p.getUniqueId());
            duelInviteWeb.put(target.getUniqueId(), duelWebEnabled.getOrDefault(p.getUniqueId(), false));
            duelInviteEly.put(target.getUniqueId(), duelElyEnabled.getOrDefault(p.getUniqueId(), false));
            // Timeout 30sn
            cancelDuelInviteTimeout(target.getUniqueId());
            int tid = new BukkitRunnable() {
                @Override public void run() {
                    if (!duelInvite.containsKey(target.getUniqueId())) return;
                    duelInvite.remove(target.getUniqueId());
                    duelInviteWeb.remove(target.getUniqueId());
                    duelInviteEly.remove(target.getUniqueId());
                    p.sendMessage(color("&#ff4444&l[DUEL] &f" + target.getName() + " &7daveti reddetti/süresi doldu."));
                    target.sendMessage(color("&#ff4444&l[DUEL] &f" + p.getName() + " &7ile duel daveti süresi doldu."));
                }
            }.runTaskLater(this, 20L * 30).getTaskId();
            duelInviteTask.put(target.getUniqueId(), tid);
            // Ayar bilgileri
            boolean webOn = duelWebEnabled.getOrDefault(p.getUniqueId(), false);
            boolean elyOn = duelElyEnabled.getOrDefault(p.getUniqueId(), false);
            // Hedef oyuncuya davet mesajı
            target.sendMessage(color(""));
            target.sendMessage(color("  &#ff6600&l⚔ &r&#ffcc00&l1v1 Daveti &r&#ff6600&l⚔"));
            target.sendMessage(color("  &#aaaaaa" + p.getName() + " seni düello'ya davet etti!"));
            target.sendMessage(color("  &7Örümcek Ağı: " + (webOn ? "&#00ff88Açık" : "&#ff4444Kapalı")));
            target.sendMessage(color("  &7Elytra: " + (elyOn ? "&#00ff88Açık" : "&#ff4444Kapalı")));
            target.sendMessage(color("  &#aaaaaa▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            target.sendMessage(color("  &#00ff88&l[ Kabul Et ]  /1v1kabul   &r  &#ff4444&l[ Reddet ]  /1v1reddet"));
            target.sendMessage(color(""));
            target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
            p.sendMessage(color("&#ffcc00&l[DUEL] &f" + target.getName() + " &7kişisine duel daveti gönderildi."));
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
            boolean webOn = duelInviteWeb.getOrDefault(p.getUniqueId(), false);
            boolean elyOn = duelInviteEly.getOrDefault(p.getUniqueId(), false);
            duelInvite.remove(p.getUniqueId());
            duelInviteWeb.remove(p.getUniqueId());
            duelInviteEly.remove(p.getUniqueId());
            String freeArena = findFreeDuelArena();
            if (freeArena == null) { p.sendMessage(color("&#ff4444&l[DUEL] &cBoş arena kalmadı!")); return true; }
            startDuel(inviter, p, freeArena, webOn, elyOn);
            return true;
        }

        // /1v1reddet
        if (cmdName.equals("1v1reddet")) {
            if (!(sender instanceof Player p)) return true;
            UUID inviterUid = duelInvite.remove(p.getUniqueId());
            if (inviterUid == null) { p.sendMessage(color("&cBekleyen duel davetin yok.")); return true; }
            cancelDuelInviteTimeout(p.getUniqueId());
            duelInviteWeb.remove(p.getUniqueId()); duelInviteEly.remove(p.getUniqueId());
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

        // /stash — toggle aç/kapa
        if (cmdName.equals("stash")) {
            if (!(sender instanceof Player p)) return true;
            boolean isOpen = stashOpen.getOrDefault(p.getUniqueId(), false);
            if (isOpen) {
                stashOpen.put(p.getUniqueId(), false);
                // Envanterde kalan itemleri kaydet
                if (p.getOpenInventory().getTopInventory().getSize() == 27) {
                    ItemStack[] contents = p.getOpenInventory().getTopInventory().getContents();
                    playerStash.put(p.getUniqueId(), contents.clone());
                }
                p.closeInventory();
                p.sendMessage(color("&#00ccff&lStash &8» &fStash kapatıldı."));
            } else {
                stashOpen.put(p.getUniqueId(), true);
                Inventory stashInv = Bukkit.createInventory(null, 27, color("&#00ccff&l✦ Stash ✦"));
                ItemStack[] existing = playerStash.get(p.getUniqueId());
                if (existing != null) {
                    for (int i = 0; i < Math.min(existing.length, 27); i++)
                        if (existing[i] != null) stashInv.setItem(i, existing[i].clone());
                }
                p.openInventory(stashInv);
                p.sendMessage(color("&#00ccff&lStash &8» &fStash açıldı."));
            }
            return true;
        }

        // /cooldownhizıayarla <hiz>
        if (cmdName.equals("cooldownhiziayarla")) {
            if (!(sender instanceof Player p)) return true;
            if (args.length < 1) { p.sendMessage(color("&cKullanim: /cooldownhiziayarla <hiz>")); return true; }
            double speed;
            try { speed = Double.parseDouble(args[0].replace(",",".")); }
            catch (NumberFormatException ex) { p.sendMessage(color("&cGeçersiz hız değeri.")); return true; }
            if (speed <= 0 || speed > 100) { p.sendMessage(color("&cHız 0-100 arasında olmalı.")); return true; }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) { p.sendMessage(color("&cElinizde bir item tutmalısınız.")); return true; }
            ItemMeta hm = hand.getItemMeta();
            hm.getPersistentDataContainer().set(cooldownSpeedTag, PersistentDataType.DOUBLE, speed);
            hand.setItemMeta(hm);
            p.getInventory().setItemInMainHand(hand);
            p.sendMessage(color("&#00ff88&lCooldown &8» &fElinizde tuttuğunuz iteme &b" + speed + "x &fhız ayarlandı."));
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

            UUID uid = target.getUniqueId();
            kontUntil.put(uid, System.currentTimeMillis() + sureMs);
            kontYetkilisi.put(uid, admin.getUniqueId());

            // Eşyaları RAM'e kaydet
            kontSavedInv.put(uid, target.getInventory().getContents().clone());
            target.getInventory().clear();

            // Kont itemi — 9 slotlu hotbar'ın tam ortası = slot 4
            ItemStack kontItem = buildKontItem(target);
            target.getInventory().setItem(4, kontItem);

            // Kont yerine ışınla
            target.teleport(kontYeri);

            // Hareket kilitle
            target.setWalkSpeed(0f);
            target.setFlySpeed(0f);

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
                    // Hâlâ konttaysa ve oyuncu hâlâ bağlıysa/bağlı değilse ban
                    if (!kontUntil.containsKey(uid)) return;
                    Player tp = Bukkit.getPlayer(uid);
                    String name = tp != null ? tp.getName() : Bukkit.getOfflinePlayer(uid).getName();
                    Bukkit.getBanList(BanList.Type.NAME).addBan(name,
                        "Kont Kaçışı — 3 dakikada cevap vermedi", new Date(System.currentTimeMillis() + 7*24*3600_000L), "LoginX-Kont");
                    if (tp != null) tp.kickPlayer(color("&#ff0000Kont Kaçışı &f— 7 Gün Tempban\n&#ffcc00Sunucuya geri dön ve durumu açıkla."));
                    kontUntil.remove(uid); kontYetkilisi.remove(uid);
                    for (Player on : Bukkit.getOnlinePlayers()) {
                        if (on.hasPermission("loginx.admin"))
                            on.sendMessage(color("&#ff0000&l[KONT] &r&#ff6600" + name + " &c3 dakikada cevap vermedi — 7 gün tempban uygulandı!"));
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

        // /kontyerisil (isim) — kayıtlı kont yerini sil
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

        // /itiraf — kont sırasında
        if (cmdName.equals("itiraf")) {
            if (!(sender instanceof Player p)) return true;
            if (!kontUntil.containsKey(p.getUniqueId())) { p.sendMessage(color("&cKont altında değilsin.")); return true; }
            String msg = args.length > 0 ? String.join(" ", args) : "(boş)";
            notifyKontYetkilisi(p.getUniqueId(), color("&#ff6600&l[İTİRAF] &r&#ffcc00" + p.getName() + ": &f" + msg));
            p.sendMessage(color("&#00ff88» &fİtirafın yetkililere iletildi."));
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

        // Admin gerektiren komutlar
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

    // Oyuncu 1v1ayarla arenasına girince: 2. oyuncu girdiğinde kapat
    @EventHandler
    public void onPlayerMoveAyarla(PlayerMoveEvent ev) {
        Player p = ev.getPlayer();
        if (playerAyarlaArena.containsKey(p.getUniqueId())) return;
        Location to = ev.getTo(); if (to == null) return;

        for (Map.Entry<String, BoundingBox> entry : ayarlaBounds.entrySet()) {
            String arenaId = entry.getKey();
            BoundingBox bbox = entry.getValue();
            if (!bbox.contains(to.toVector())) continue;

            UUID[] queue = ayarlaQueue.computeIfAbsent(arenaId, k -> new UUID[2]);
            // İlk oyuncuyu kaydet
            if (queue[0] == null) {
                queue[0] = p.getUniqueId();
                playerAyarlaArena.put(p.getUniqueId(), arenaId);
                p.sendMessage(color("&#ffaa00&l1v1 &8» &fArenaya girdiniz! 2. oyuncu bekleniyor."));
            } else if (queue[1] == null && !queue[0].equals(p.getUniqueId())) {
                queue[1] = p.getUniqueId();
                playerAyarlaArena.put(p.getUniqueId(), arenaId);
                // 2 kişi var — duvarları kapat
                closeAyarlaArena(arenaId, bbox, p.getWorld());
                for (UUID uid : queue) {
                    Player ap = Bukkit.getPlayer(uid);
                    if (ap != null) ap.sendMessage(color("&#ff4444&l1v1 BAŞLADI! &fArena kapatıldı."));
                }
            }
        }
    }

    private void closeAyarlaArena(String arenaId, BoundingBox bbox, World world) {
        ItemStack[] walls = ayarlaWalls.getOrDefault(arenaId, new ItemStack[]{new ItemStack(Material.GLASS)});
        Material wallMat = walls.length > 0 && walls[0] != null ? walls[0].getType() : Material.GLASS;
        if (wallMat == Material.AIR) wallMat = Material.GLASS;
        int minX = (int) bbox.getMinX(); int maxX = (int) bbox.getMaxX();
        int minY = (int) bbox.getMinY(); int maxY = (int) bbox.getMaxY();
        int minZ = (int) bbox.getMinZ(); int maxZ = (int) bbox.getMaxZ();
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    boolean edge = x==minX||x==maxX||y==minY||y==maxY||z==minZ||z==maxZ;
                    if (edge) world.getBlockAt(x,y,z).setType(wallMat);
                }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        Player killer = dead.getKiller();
        deathRecords.add(0, new DeathRecord(dead,
            killer != null ? killer.getName() : "Bilinmiyor",
            dead.getInventory().getContents()));

        // ─── Totem patlaması — öldürenin ekranında ───
        if (killer != null) {
            killer.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                killer.getLocation().add(0, 1, 0), 80, 0.5, 0.5, 0.5, 0.3);
            killer.playSound(killer.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
            // Aynı zamanda ölen oyuncu konumunda da patlasın
            dead.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                dead.getLocation().add(0, 1, 0), 60, 0.4, 0.4, 0.4, 0.25);
        }

        // ─── Stash düşür — Shulker içinde ───
        ItemStack[] stashContents = playerStash.get(dead.getUniqueId());
        if (stashContents != null) {
            ItemStack shulker = new ItemStack(Material.CYAN_SHULKER_BOX);
            BlockStateMeta bsm = (BlockStateMeta) shulker.getItemMeta();
            ShulkerBox box = (ShulkerBox) bsm.getBlockState();
            for (int i = 0; i < Math.min(stashContents.length, box.getInventory().getSize()); i++) {
                if (stashContents[i] != null) box.getInventory().setItem(i, stashContents[i].clone());
            }
            bsm.setBlockState(box);
            bsm.setDisplayName(color("&#00ccff" + dead.getName() + " &#ffffff- Stash"));
            bsm.setLore(new ArrayList<>());
            shulker.setItemMeta(bsm);
            dead.getWorld().dropItemNaturally(dead.getLocation(), shulker);
            playerStash.remove(dead.getUniqueId());
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

        // 1v1ayarla arena — ölünce duvarları kaldır
        if (playerAyarlaArena.containsKey(dead.getUniqueId())) {
            String arenaId = playerAyarlaArena.get(dead.getUniqueId());
            UUID[] aps = ayarlaQueue.get(arenaId);
            BoundingBox bbox = ayarlaBounds.get(arenaId);
            if (bbox != null && dead.getWorld() != null) {
                removeArenaBorderByBBox(dead.getWorld(), bbox);
            }
            if (aps != null) {
                for (UUID uid : aps) {
                    playerAyarlaArena.remove(uid);
                    Player ap = Bukkit.getPlayer(uid);
                    if (ap != null) {
                        ap.sendTitle(color("&#ff4444&l⚔ 1v1 BİTTİ"), color("&#ffcc00" + dead.getName() + " &felendi!"), 5, 60, 15);
                        ap.playSound(ap.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                    }
                }
            }
            ayarlaQueue.remove(arenaId);
            ayarlaBounds.remove(arenaId);
            ayarlaArenas.remove(arenaId);
            ayarlaWalls.remove(arenaId);
        }
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

    // İlk giriş koruma
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
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
    //  EFEKT SEÇİM MENÜSÜ — DÜZELTİLDİ
    // ─────────────────────────────────────────────
    private void openEffectSelectMenu(Player p, String preKasa) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&#aa44ff&l✦ Efekt Secimi"));
        ItemStack fill = makeBorderGlass("&#0d0d1a");
        for (int i = 0; i < 54; i++) inv.setItem(i, fill);

        ItemStack header = new ItemStack(Material.AMETHYST_SHARD); ItemMeta hm = header.getItemMeta();
        hm.setDisplayName(color("&#aa44ff&l✦ Efekt Seç ✦"));
        List<String> hl = new ArrayList<>();
        hl.add(color("  &#553388▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        hl.add(color("  &#ffffff&lKasa etrafında görünecek efekti seç"));
        hl.add(color("  &#aaaaaa&oUygulama: " + (preKasa.isEmpty() ? "&#ffcc00Tüm Kasalar" : "&#00ccff" + capitalize(preKasa) + " Kasası")));
        hl.add(color("  &#553388▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        hm.setLore(hl); header.setItemMeta(hm); inv.setItem(4, header);

        // 14 efekt — 2 satır: 10-16 (7) + 19-25 (7)
        Object[][] effects = {
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
            {"doga",      "Doğa",       "&#44ff44","Villager + Composter yeşil efekt",  Material.OAK_LEAVES},
            {"hayalet",   "Hayalet",    "&#5599ff","Soul + Ash mavi hayalet efekti",    Material.SOUL_LANTERN}
        };
        // Simetrik 2 satır: 10-16 ve 19-25
        int[] slots2 = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25};
        String[] icons = {"◎","✿","◈","★","◆","♦","❄","☽","◉","◌","✦","⚡","✾","☁"};

        for (int i = 0; i < effects.length; i++) {
            String id    = (String)   effects[i][0];
            String label = (String)   effects[i][1];
            String clr2  = (String)   effects[i][2];
            String desc  = (String)   effects[i][3];
            Material mat = (Material) effects[i][4];
            ItemStack btn = new ItemStack(mat); ItemMeta bm = btn.getItemMeta();
            bm.setDisplayName(color(clr2 + icons[i] + " " + label));
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
        inv.setItem(47, makeInfoItem());
        inv.setItem(49, makeCloseHead(p));
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
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0,1,0), 60, 0.5, 0.5, 0.5, 0.2);
        String rName = getName(s.reward);
        String[] clr = getCrateColors(s.crateName);
        p.sendTitle(color(clr[0] + "&l✦ HAYIRLI OLSUN! ✦"),
            color("&#ffffff&l" + rName + " &r" + clr[2] + "» " + capitalize(s.crateName) + " Kasasindan"), 5, 100, 20);
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
        Inventory inv = Bukkit.createInventory(null, 54, color("&7Fear Craft - &fIade"));
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
            || title.contains("Hile Kontrol") || title.contains("Duel Arenaları") || title.contains("Duel Ayarları");
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

        // İade
        if (title.contains("Fear Craft") && title.contains("Iade")) {
            if (m == Material.PLAYER_HEAD) {
                try {
                    String id = itemName.split("#")[1].replace(")","").trim();
                    DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
                    if (dr != null) {
                        if (e.isLeftClick()) { Player t = Bukkit.getPlayer(dr.playerUUID); if (t != null && t.isOnline()) { t.getInventory().setContents(dr.items); p.sendMessage(color("&aIade edildi.")); } }
                        else if (e.isRightClick()) openIadeDetail(p, dr);
                    }
                } catch (Exception ignored) {}
            } else if (m == Material.BARRIER) p.closeInventory();
            else if (m == Material.EMERALD_BLOCK) openIadeMenu(p);
            else if (m == Material.LAVA_BUCKET) { deathRecords.clear(); openIadeMenu(p); p.sendMessage(color("&cGecmis silindi.")); }
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

        // Duel ayarlar menüsü
        if (title.contains("Duel Ayarları")) {
            ItemStack ci3 = e.getCurrentItem();
            if (ci3 == null || !ci3.hasItemMeta()) return;
            List<String> lore3 = ci3.getItemMeta().getLore();
            if (lore3 == null) return;
            String toggle = lore3.stream().map(ChatColor::stripColor).filter(l -> l.trim().startsWith("DUEL_TOGGLE:"))
                .map(l -> l.trim().substring(12)).findFirst().orElse(null);
            if (toggle == null) return;
            if (toggle.equals("web")) {
                boolean cur = duelWebEnabled.getOrDefault(p.getUniqueId(), false);
                duelWebEnabled.put(p.getUniqueId(), !cur);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, cur ? 0.8f : 1.2f);
            } else if (toggle.equals("ely")) {
                boolean cur = duelElyEnabled.getOrDefault(p.getUniqueId(), false);
                duelElyEnabled.put(p.getUniqueId(), !cur);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, cur ? 0.8f : 1.2f);
            }
            p.closeInventory();
            openDuelSettingsMenu(p, p.hasPermission("loginx.admin"));
            return;
        }

        // Ölüm detayı
        if (title.contains("Olum Detayi") && (m == Material.RED_DYE || m == Material.LIME_DYE)) {
            List<String> l2 = clicked.getItemMeta().getLore();
            if (l2 == null || l2.isEmpty()) return;
            String id = ChatColor.stripColor(l2.get(0));
            DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
            if (dr != null && m == Material.LIME_DYE) { Player t = Bukkit.getPlayer(dr.playerUUID); if (t != null && t.isOnline()) { t.getInventory().setContents(dr.items); p.sendMessage(color("&aIade edildi.")); } }
            if (dr != null && m == Material.RED_DYE) { deathRecords.remove(dr); openIadeMenu(p); }
        }
    }

    // ─────────────────────────────────────────────
    //  INV CLOSE
    // ─────────────────────────────────────────────
    @EventHandler
    public void onInvClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;

        // Stash kapat — içeriği kaydet
        String stashTitle = ChatColor.stripColor(e.getView().getTitle());
        if (stashTitle.contains("Stash")) {
            ItemStack[] contents = e.getInventory().getContents();
            playerStash.put(p.getUniqueId(), contents.clone());
            stashOpen.put(p.getUniqueId(), false);
        }

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

    private void startDuel(Player p1, Player p2, String arenaId, boolean webOn, boolean elyOn) {
        duelPlayers.put(arenaId, new UUID[]{p1.getUniqueId(), p2.getUniqueId()});
        playerDuel.put(p1.getUniqueId(), arenaId);
        playerDuel.put(p2.getUniqueId(), arenaId);
        duelFrozen.put(p1.getUniqueId(), true);
        duelFrozen.put(p2.getUniqueId(), true);

        Location s1 = duelSpawn1.get(arenaId);
        Location s2 = duelSpawn2.get(arenaId);
        p1.teleport(s1); p2.teleport(s2);

        // Ağ / elytra ayarlarını uygula
        if (!webOn) { /* web yasak — entity damage event'inde kontrol */ }

        // 3'ten geriye say + freeze
        for (Player dp : new Player[]{p1, p2}) {
            dp.setWalkSpeed(0f); dp.setFlySpeed(0f);
        }
        new BukkitRunnable() {
            int count = 3;
            @Override public void run() {
                if (count > 0) {
                    for (Player dp : new Player[]{p1, p2}) {
                        if (!dp.isOnline()) { cancel(); endDuelDisconnect(arenaId, dp); return; }
                        dp.sendTitle(
                            color("&#ff4444&l" + count),
                            color("&#aaaaaa&o⚔ Duel başlıyor..."),
                            0, 25, 5);
                        dp.playSound(dp.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, count == 1 ? 2f : 1f);
                    }
                    count--;
                } else {
                    cancel();
                    duelFrozen.remove(p1.getUniqueId()); duelFrozen.remove(p2.getUniqueId());
                    for (Player dp : new Player[]{p1, p2}) {
                        dp.setWalkSpeed(0.2f); dp.setFlySpeed(0.1f);
                        dp.sendTitle(color("&#00ff88&l⚔ DUEL BAŞLADI!"), color("&#ffcc00İyi şanslar!"), 3, 30, 7);
                        dp.playSound(dp.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void endDuel(String arenaId, Player winner, Player loser) {
        UUID[] players = duelPlayers.remove(arenaId);
        if (players == null) return;
        for (UUID u : players) { playerDuel.remove(u); duelFrozen.remove(u); }
        if (winner != null && winner.isOnline()) {
            winner.sendTitle(color("&#ffcc00&l⚔ KAZANDIN! ⚔"), color("&#ffffff" + (loser != null ? loser.getName() : "?") + " &f elendi!"), 5, 80, 15);
            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            winner.setWalkSpeed(0.2f); winner.setFlySpeed(0.1f);
        }
        if (loser != null && loser.isOnline()) {
            loser.sendTitle(color("&#ff4444&l⚔ KAYBETTİN! ⚔"), color("&#aaaaaa&oKazanan: " + (winner != null ? winner.getName() : "?")), 5, 80, 15);
            loser.setWalkSpeed(0.2f); loser.setFlySpeed(0.1f);
        }
        // Totem patlaması winner'da
        if (winner != null) winner.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, winner.getLocation().add(0,1,0), 60, 0.5,0.5,0.5,0.2);
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
        List<String> ids = new ArrayList<>(duelSpawn1.keySet());
        int size = ids.isEmpty() ? 27 : Math.max(27, ((ids.size() / 9) + 1) * 9);
        size = Math.min(size, 54);
        Inventory inv = Bukkit.createInventory(null, size, color("&#ff6600&l⚔ Duel Arenaları"));
        ItemStack fill = makeBorderGlass("&#1a0000");
        for (int i = 0; i < size; i++) inv.setItem(i, fill);

        int slot = 10;
        for (String did : ids) {
            boolean dolu = duelPlayers.containsKey(did);
            boolean hazir = duelSpawn1.get(did) != null && duelSpawn2.get(did) != null;
            Material mat = dolu ? Material.RED_STAINED_GLASS_PANE : (hazir ? Material.GREEN_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE);
            String renk = dolu ? "&#ff4444" : (hazir ? "&#00ff88" : "&#ffcc00");
            ItemStack item = new ItemStack(mat); ItemMeta m = item.getItemMeta();
            m.setDisplayName(color(renk + "&l⚔ Arena " + did));
            List<String> l = new ArrayList<>();
            l.add(color("  &#555555▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            l.add(color("  &7Durum: " + (dolu ? "&#ff4444Dolu" : (hazir ? "&#00ff88Boş" : "&#ffcc00Hazırlanıyor"))));
            if (dolu) {
                UUID[] dp = duelPlayers.get(did);
                String n1 = Bukkit.getOfflinePlayer(dp[0]).getName();
                String n2 = Bukkit.getOfflinePlayer(dp[1]).getName();
                l.add(color("  &7Oyuncular: &f" + n1 + " &7vs &f" + n2));
            }
            l.add(color("  &#555555▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
            if (!dolu && hazir) l.add(color("  &#00ff88» Duel başlatmak için tıkla!"));
            l.add(color("  &8DUEL_ID:" + did));
            m.setLore(l); item.setItemMeta(m);
            if (slot < size - 9) { inv.setItem(slot, item); slot++; if (slot % 9 == 8) slot += 2; }
        }
        inv.setItem(size - 5, makeCloseHead(p));
        p.openInventory(inv);
    }

    private void openDuelSettingsMenu(Player p, boolean isAdmin) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&#ff6600&l⚔ Duel Ayarları"));
        ItemStack fill = makeBorderGlass("&#1a0a00");
        for (int i = 0; i < 27; i++) inv.setItem(i, fill);

        boolean webOn = duelWebEnabled.getOrDefault(p.getUniqueId(), false);
        boolean elyOn = duelElyEnabled.getOrDefault(p.getUniqueId(), false);

        // Başlık — slot 4
        ItemStack header = new ItemStack(Material.BLAZE_ROD); ItemMeta hm = header.getItemMeta();
        hm.setDisplayName(color("&#ff6600&l⚔ Duel Ayarları"));
        hm.setLore(Arrays.asList(color("&#aaaaaa Duellara katılma ayarların")));
        header.setItemMeta(hm); inv.setItem(4, header);

        // Örümcek Ağı — slot 11
        ItemStack web = new ItemStack(webOn ? Material.COBWEB : Material.STRING);
        ItemMeta wm = web.getItemMeta();
        wm.setDisplayName(color((webOn ? "&#00ff88" : "&#ff4444") + "Örümcek Ağı: " + (webOn ? "Açık" : "Kapalı")));
        wm.setLore(Arrays.asList(
            color("  &#555555▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"),
            color("  &7Düelloda örümcek ağı"),
            color("  &7kullanılsın mı?"),
            color("  " + (webOn ? "&#00ff88Şu an: Açık" : "&#ff4444Şu an: Kapalı")),
            color("  &#555555▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"),
            color("  &#ffcc00» Değiştirmek için tıkla"),
            color("  &8DUEL_TOGGLE:web")));
        web.setItemMeta(wm); inv.setItem(11, web);

        // Elytra — slot 13
        ItemStack ely = new ItemStack(elyOn ? Material.ELYTRA : Material.LEATHER_CHESTPLATE);
        ItemMeta em = ely.getItemMeta();
        em.setDisplayName(color((elyOn ? "&#00ff88" : "&#ff4444") + "Elytra: " + (elyOn ? "Açık" : "Kapalı")));
        em.setLore(Arrays.asList(
            color("  &#555555▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"),
            color("  &7Düelloda elytra"),
            color("  &7kullanılsın mı?"),
            color("  " + (elyOn ? "&#00ff88Şu an: Açık" : "&#ff4444Şu an: Kapalı")),
            color("  &#555555▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"),
            color("  &#ffcc00» Değiştirmek için tıkla"),
            color("  &8DUEL_TOGGLE:ely")));
        ely.setItemMeta(em); inv.setItem(13, ely);

        // Onay (Yeşil ok) — slot 15
        ItemStack onay = new ItemStack(Material.LIME_DYE); ItemMeta om = onay.getItemMeta();
        om.setDisplayName(color("&#00ff88&l✔ Ayarları Kaydet"));
        om.setLore(Arrays.asList(color("  &7Ayarlar otomatik kaydedilir"), color("  &#00ff88» Kapat")));
        onay.setItemMeta(om); inv.setItem(15, onay);

        // Kapat — slot 22
        inv.setItem(22, makeCloseHead(p));
        p.openInventory(inv);
    }

    // ─────────────────────────────────────────────
    //  DUEL EVENT'LARİ
    // ─────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDuelMove(PlayerMoveEvent ev) {
        Player p = ev.getPlayer();
        if (duelFrozen.getOrDefault(p.getUniqueId(), false)) {
            Location from = ev.getFrom(); Location to = ev.getTo();
            if (to != null && (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()))
                ev.setTo(from.clone().setDirection(to.getDirection()));
        }
    }

    @EventHandler
    public void onDuelQuit(PlayerQuitEvent evq) {
        Player p = evq.getPlayer();
        String arenaId = playerDuel.get(p.getUniqueId());
        if (arenaId == null) return;
        // Bağlantı kesen ölür, diğeri kazanır
        UUID[] dp = duelPlayers.get(arenaId);
        if (dp == null) return;
        Player winner = null;
        for (UUID u : dp) if (!u.equals(p.getUniqueId())) winner = Bukkit.getPlayer(u);
        for (UUID u : dp) { playerDuel.remove(u); duelFrozen.remove(u); }
        duelPlayers.remove(arenaId);
        if (winner != null && winner.isOnline()) {
            winner.sendTitle(color("&#00ff88&l⚔ KAZANDIN!"), color("&#ff4444" + p.getName() + " &foyundan çıktı!"), 5,80,15);
            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE,1f,1.2f);
            winner.setWalkSpeed(0.2f); winner.setFlySpeed(0.1f);
        }
        for (Player on : Bukkit.getOnlinePlayers())
            if (on.hasPermission("loginx.admin") || on.getWorld().equals(p.getWorld()))
                on.sendMessage(color("&#ff4444&l[DUEL] &f" + p.getName() + " &7oyundan çıktı — &f" + (winner != null ? winner.getName() : "?") + " &7kazandı!"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDuelCommand(PlayerCommandPreprocessEvent evCmd) {
        Player p = evCmd.getPlayer();
        if (!playerDuel.containsKey(p.getUniqueId())) return;
        if (p.hasPermission("loginx.admin")) return;
        String cmd = evCmd.getMessage().toLowerCase().trim().substring(1).split(" ")[0];
        Set<String> allowed = new HashSet<>(Arrays.asList("ec","pv","enderchest","pvault","1v1reddet"));
        if (!allowed.contains(cmd) && !cmd.startsWith("pv")) {
            evCmd.setCancelled(true);
            p.sendMessage(color("&#ff4444&l[DUEL] &fDuel sırasında sadece &e/ec &fve &e/pv &fkomutlarını kullanabilirsin!"));
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
            p.setWalkSpeed(0.2f);
            p.setFlySpeed(0.1f);
            p.getInventory().clear();
            if (giveBack && saved != null) {
                p.getInventory().setContents(saved);
                p.sendMessage(color("&#00ff88&l[KONT] &fKonttan çıkarıldın. Eşyaların iade edildi."));
            } else if (giveBack) {
                p.sendMessage(color("&#ffcc00&l[KONT] &fKonttan çıkarıldın."));
            }
        }
    }

    private void cancelKontTimeout(UUID uid) {
        Integer tid = kontTimeoutTask.remove(uid);
        if (tid != null) Bukkit.getScheduler().cancelTask(tid);
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
        if (e.getEntity() instanceof Player p && elytraBanned.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) e.setCancelled(true);
    }

    // ─────────────────────────────────────────────
    //  KONT EVENT'LARİ
    // ─────────────────────────────────────────────

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

    // Kont altındaki oyuncu hareket edemez
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKontMove(PlayerMoveEvent ev) {
        Player p = ev.getPlayer();
        if (!kontUntil.containsKey(p.getUniqueId())) return;
        Location from = ev.getFrom(); Location to = ev.getTo();
        if (to == null) return;
        // Sadece bakış açısını değiştirmesine izin ver, yürüme yok
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            ev.setTo(from.clone().setDirection(to.getDirection()));
        }
    }

    // Kont altındaki oyuncu çıkarsa oto-ban
    @EventHandler
    public void onKontQuit(PlayerQuitEvent ev) {
        Player p = ev.getPlayer();
        UUID uid = p.getUniqueId();
        if (!kontUntil.containsKey(uid)) return;
        cancelKontTimeout(uid);
        kontUntil.remove(uid);
        kontYetkilisi.remove(uid);
        // 7 gün tempban
        Bukkit.getBanList(BanList.Type.NAME).addBan(p.getName(),
            "Kont Kaçışı — Oyundan Çıktı", new Date(System.currentTimeMillis() + 7L*24*3600_000L), "LoginX-Kont");
        for (Player on : Bukkit.getOnlinePlayers()) {
            if (on.hasPermission("loginx.admin"))
                on.sendMessage(color("&#ff0000&l[KONT] &r&#ff6600" + p.getName() + " &ckonttan kaçtı (çıkış yaptı) — 7 gün tempban!"));
        }
    }

    // Kont itemine sağ tıklayınca menü aç
    @EventHandler
    public void onKontItemUse(PlayerInteractEvent ev) {
        Player p = ev.getPlayer();
        if (!kontUntil.containsKey(p.getUniqueId())) return;
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
