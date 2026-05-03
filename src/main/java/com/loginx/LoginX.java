package com.loginx;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.persistence.PersistentDataType;

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
    private final HashMap<String, Location>        crates       = new HashMap<>();
    private final HashMap<String, List<ItemStack>> crateItems   = new HashMap<>();
    private final HashMap<String, Boolean>         crateEffects = new HashMap<>();
    private NamespacedKey keyTag;

    // Kumar menüsü aktif oturumları
    private final HashMap<UUID, CrateSession> activeSessions = new HashMap<>();

    // Yanlış anahtar spam engeli (cooldown)
    private final HashMap<UUID, Long> wrongKeyCooldown = new HashMap<>();
    private static final long WRONG_KEY_COOLDOWN_MS = 3000L;

    // Kasa adına göre renk paletleri (AI-inspired, kasa karakterine göre)
    private static final Map<String, String[]> CRATE_COLORS = new LinkedHashMap<>();
    static {
        // AFK → Gri/Beyaz RGB floplu
        CRATE_COLORS.put("afk",     new String[]{"&#aaaaaa","&#cccccc","&#ffffff","&#eeeeee","&#dddddd","&#bbbbbb"});
        // Casino → Kızıl/Altın
        CRATE_COLORS.put("casino",  new String[]{"&#ff2200","&#ff6600","&#ffaa00","&#ffdd00","&#ff6600","&#ff2200"});
        // Gear → Turuncu/Sarı
        CRATE_COLORS.put("gear",    new String[]{"&#ff8800","&#ffaa00","&#ffdd00","&#ffaa00","&#ff8800","&#ff5500"});
        // Booster → Yeşil
        CRATE_COLORS.put("booster", new String[]{"&#00aa44","&#00cc66","&#00ff88","&#00cc66","&#00aa44","&#008833"});
        // All → Mor/Mavi
        CRATE_COLORS.put("all",     new String[]{"&#aa00ff","&#cc44ff","&#00ccff","&#cc44ff","&#aa00ff","&#7700cc"});
    }

    // Her anahtar için sabit isim — SADECE bu isim görünür, başka bir şey yok
    private static final Map<String, String> KEY_BASE_NAMES = new LinkedHashMap<>();
    static {
        KEY_BASE_NAMES.put("afk",     "Afk Key");
        KEY_BASE_NAMES.put("casino",  "Casino Key");
        KEY_BASE_NAMES.put("gear",    "Gear Key");
        KEY_BASE_NAMES.put("booster", "Booster Key");
        KEY_BASE_NAMES.put("all",     "Key All");
    }

    // Kumar menüsü ayarları
    private static final int SPIN_DURATION_TICKS = 72; // ~3.6 sn (3L tick hızında)
    private static final int SPIN_SLOTS          = 45;
    private static final int RESULT_SLOT         = 22; // 5x9 tam orta

    // ─────────────────────────────────────────────
    //  INNER CLASS: CrateSession
    // ─────────────────────────────────────────────
    private static class CrateSession {
        Inventory inv;
        ItemStack reward;
        int       tick;
        boolean   done;
        String    crateName;

        CrateSession(Inventory inv, ItemStack reward, String crateName) {
            this.inv       = inv;
            this.reward    = reward;
            this.tick      = 0;
            this.done      = false;
            this.crateName = crateName;
        }
    }

    // ─────────────────────────────────────────────
    //  INNER CLASS: DeathRecord
    // ─────────────────────────────────────────────
    private static class DeathRecord {
        String id, playerName, killerName, date;
        UUID   playerUUID;
        Location    loc;
        ItemStack[] items;

        DeathRecord(Player p, String killer, ItemStack[] itms) {
            this.id         = Integer.toHexString(new Random().nextInt(0xFFFF)).toUpperCase();
            this.playerUUID = p.getUniqueId();
            this.playerName = p.getName();
            this.killerName = killer;
            this.loc        = p.getLocation();
            this.items      = itms;
            this.date       = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date());
        }
    }

    // ─────────────────────────────────────────────
    //  ENABLE / DISABLE
    // ─────────────────────────────────────────────
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        keyTag = new NamespacedKey(this, "kasa_anahtari");
        saveDefaultConfig();
        loadData();

        new BukkitRunnable() {
            @Override public void run() {
                flopTick++;
                updateCrateEffects();
                updateAnimatedMenus();
                tickAllSessions();
            }
        }.runTaskTimer(this, 0L, 3L);

        getLogger().info("Fear Craft LoginX V1.3 Aktif!");
    }

    @Override
    public void onDisable() { saveData(); }

    // ─────────────────────────────────────────────
    //  KOMUTLAR
    // ─────────────────────────────────────────────
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (!p.hasPermission("loginx.admin"))  return true;

        switch (cmd.getName().toLowerCase()) {

            case "loginxreload" -> {
                reloadConfig(); loadData();
                p.sendMessage(color("&aLoginX yenilendi!"));
            }

            case "iade"            -> openIadeMenu(p);
            case "kasaanahtarmenu" -> openKeyMenu(p);

            case "kasaayarla" -> {
                if (args.length < 1) { p.sendMessage(color("&cKullanim: /kasaayarla <isim>")); return true; }
                Block b = p.getTargetBlockExact(5);
                if (b == null || b.getType() == Material.AIR) { p.sendMessage(color("&cBir bloga bakin.")); return true; }
                String ad = args[0].toLowerCase();
                crates.put(ad, b.getLocation());
                crateItems.putIfAbsent(ad, new ArrayList<>());
                crateEffects.put(ad, true);
                p.sendMessage(color("&a" + ad + " kasasi ayarlandi!"));
                saveData();
            }

            // /kasasil <kasaismi>
            case "kasasil" -> {
                if (args.length < 1) { p.sendMessage(color("&cKullanim: /kasasil <isim>")); return true; }
                String ad = args[0].toLowerCase();
                if (!crates.containsKey(ad)) { p.sendMessage(color("&cKasa bulunamadi: " + ad)); return true; }
                crates.remove(ad);
                crateItems.remove(ad);
                crateEffects.remove(ad);
                p.sendMessage(color("&c" + ad + " kasasi silindi."));
                saveData();
            }

            // /kasaicsil <kasaismi> #<sira>
            case "kasaicsil" -> {
                if (args.length < 2) { p.sendMessage(color("&cKullanim: /kasaicsil <kasa> #<sira>")); return true; }
                String ad = args[0].toLowerCase();
                List<ItemStack> pool = crateItems.get(ad);
                if (pool == null) { p.sendMessage(color("&cKasa bulunamadi: " + ad)); return true; }
                String siraStr = args[1].replace("#", "").trim();
                int sira;
                try { sira = Integer.parseInt(siraStr) - 1; }
                catch (NumberFormatException ex) { p.sendMessage(color("&cGecersiz sira. Ornek: #1")); return true; }
                if (sira < 0 || sira >= pool.size()) {
                    p.sendMessage(color("&cSira aralik disi. " + ad + " kasasinda " + pool.size() + " item var."));
                    return true;
                }
                ItemStack removed = pool.remove(sira);
                p.sendMessage(color("&c" + ad + " kasasindan #" + (sira + 1) + " (" + removed.getType().name() + ") silindi."));
                saveData();
            }

            case "icineitemkoy" -> {
                if (args.length < 1) { p.sendMessage(color("&cKullanim: /icineitemkoy <kasa>")); return true; }
                ItemStack ih = p.getInventory().getItemInMainHand();
                if (ih.getType() == Material.AIR) { p.sendMessage(color("&cElinizde esya olmali!")); return true; }
                String ad = args[0].toLowerCase();
                if (!crates.containsKey(ad)) { p.sendMessage(color("&cKasa bulunamadi: " + ad)); return true; }
                crateItems.get(ad).add(ih.clone());
                p.sendMessage(color("&aEsya &e" + ad + " &akasasina eklendi. (#" + crateItems.get(ad).size() + ")"));
                saveData();
            }

            case "efektac"   -> { if (args.length > 0) { crateEffects.put(args[0].toLowerCase(), true);  p.sendMessage(color("&aEfekt acildi.")); } }
            case "efektkapa" -> { if (args.length > 0) { crateEffects.put(args[0].toLowerCase(), false); p.sendMessage(color("&cEfekt kapatildi.")); } }
        }
        return true;
    }

    // ─────────────────────────────────────────────
    //  KASA ETKİLEŞİM
    // ─────────────────────────────────────────────
    @EventHandler
    public void onCrateInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        Location loc = e.getClickedBlock().getLocation();
        String activeCrate = null;
        for (Map.Entry<String, Location> entry : crates.entrySet()) {
            if (entry.getValue().equals(loc)) { activeCrate = entry.getKey(); break; }
        }
        if (activeCrate == null) return;
        e.setCancelled(true);

        Player p = e.getPlayer();
        if (activeSessions.containsKey(p.getUniqueId())) return; // zaten spin açık

        // SOL TIKLA → önizleme
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            openCratePreview(p, activeCrate);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        // Anahtar kontrolü — elinde hiç anahtar yok
        ItemStack inHand = p.getInventory().getItemInMainHand();
        if (!inHand.hasItemMeta() ||
            !inHand.getItemMeta().getPersistentDataContainer().has(keyTag, PersistentDataType.STRING)) {
            p.setVelocity(p.getLocation().getDirection().multiply(-0.4).setY(0.3));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            p.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0.5, 1, 0.5), 8, 0.2, 0.2, 0.2, 0.03);
            p.sendTitle(color("&#ff0000&lDUR!"), color("&#ff6666Kasa Anahtarin Yok"), 5, 60, 10);
            return;
        }

        String keyType = inHand.getItemMeta().getPersistentDataContainer().get(keyTag, PersistentDataType.STRING);

        // Yanlış anahtar — cooldownlu, spam olmaz
        if (!keyType.equalsIgnoreCase(activeCrate) && !keyType.equalsIgnoreCase("all")) {
            long now = System.currentTimeMillis();
            UUID uid = p.getUniqueId();
            if (wrongKeyCooldown.getOrDefault(uid, 0L) > now) return;
            wrongKeyCooldown.put(uid, now + WRONG_KEY_COOLDOWN_MS);

            p.setVelocity(p.getLocation().getDirection().multiply(-0.4).setY(0.3));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            p.sendTitle(color("&#ffcc00&lYANLIS ANAHTAR!"), color("&#ffff66Bu anahtar bu kasayi acamaz."), 5, 40, 10);
            return;
        }

        List<ItemStack> pool = crateItems.getOrDefault(activeCrate, new ArrayList<>());
        if (pool.isEmpty()) { p.sendMessage(color("&cBu kasa henuz bos!")); return; }

        // Anahtari tüket
        inHand.setAmount(inHand.getAmount() - 1);

        // Ödül seç
        ItemStack reward = pool.get(new Random().nextInt(pool.size())).clone();

        // Direk kumar menüsünü aç
        openSpinMenu(p, activeCrate, pool, reward);
    }

    // ─────────────────────────────────────────────
    //  KUMAR MENÜSÜ — AÇILIŞ
    //  Başlık: "✦ [CrateAdı] Kasası ✦" kalın renkli
    // ─────────────────────────────────────────────
    private void openSpinMenu(Player p, String crateName, List<ItemStack> pool, ItemStack reward) {
        String displayName = capitalize(crateName) + " Kasasi";
        String title = color(getBoldColorTitle(crateName, displayName));
        Inventory inv = Bukkit.createInventory(null, SPIN_SLOTS, title);
        fillSpinInventory(inv, pool, crateName);
        CrateSession session = new CrateSession(inv, reward, crateName);
        activeSessions.put(p.getUniqueId(), session);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.0f);
    }

    // Kalın renkli kasa ismi (bold + kasa rengi)
    private String getBoldColorTitle(String crateId, String text) {
        String[] clr = getCrateColors(crateId);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') { sb.append(' '); continue; }
            sb.append(clr[i % clr.length]).append("&l").append(c);
        }
        return sb.toString();
    }

    private void fillSpinInventory(Inventory inv, List<ItemStack> pool, String crateName) {
        Random rand  = new Random();
        String[] clr = getCrateColors(crateName);

        for (int i = 0; i < SPIN_SLOTS; i++) {
            boolean edge = (i < 9) || (i >= 36) || (i % 9 == 0) || (i % 9 == 8);
            if (edge) {
                inv.setItem(i, makeBorderGlass(clr[i % clr.length]));
            } else {
                if (pool.isEmpty()) { inv.setItem(i, makeBorderGlass(clr[0])); continue; }
                inv.setItem(i, pool.get(rand.nextInt(pool.size())).clone());
            }
        }
    }

    private ItemStack makeBorderGlass(String hexColor) {
        ItemStack g = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  m = g.getItemMeta();
        m.setDisplayName(color(hexColor + " "));
        g.setItemMeta(m);
        return g;
    }

    // ─────────────────────────────────────────────
    //  KUMAR MENÜSÜ — TICK (hızdan yavaşa, kumar efekti)
    // ─────────────────────────────────────────────
    private void tickAllSessions() {
        for (Map.Entry<UUID, CrateSession> entry : new HashMap<>(activeSessions).entrySet()) {
            UUID uid = entry.getKey();
            CrateSession s = entry.getValue();
            if (s.done) continue;

            Player p = Bukkit.getPlayer(uid);
            if (p == null || !p.isOnline()) { activeSessions.remove(uid); continue; }

            s.tick++;

            List<ItemStack> pool = crateItems.getOrDefault(s.crateName, new ArrayList<>());
            if (pool.isEmpty()) continue;

            int shiftEvery = getShiftInterval(s.tick);
            if (s.tick % shiftEvery == 0) {
                shiftInventory(s.inv, pool, s.crateName);
                // Spin sesi — sadece başlarda sık
                if (s.tick % (shiftEvery * 2) == 0) {
                    float pitch = 0.8f + (s.tick / (float) SPIN_DURATION_TICKS) * 0.8f;
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, pitch);
                }
            }

            if (s.tick >= SPIN_DURATION_TICKS) {
                s.done = true;
                finalizeSpin(p, s);
            }
        }
    }

    private int getShiftInterval(int tick) {
        float progress = (float) tick / SPIN_DURATION_TICKS;
        if (progress < 0.25f) return 1;
        if (progress < 0.50f) return 2;
        if (progress < 0.68f) return 3;
        if (progress < 0.84f) return 5;
        return 9;
    }

    private void shiftInventory(Inventory inv, List<ItemStack> pool, String crateName) {
        // İç slotlar: 3 orta satır (kenar hariç)
        int[][] innerRows = {
            {10,11,12,13,14,15,16},
            {19,20,21,22,23,24,25},
            {28,29,30,31,32,33,34}
        };
        Random rand = new Random();
        for (int[] row : innerRows) {
            for (int i = 0; i < row.length - 1; i++) {
                inv.setItem(row[i], inv.getItem(row[i + 1]));
            }
            inv.setItem(row[row.length - 1], pool.get(rand.nextInt(pool.size())).clone());
        }

        // Orta sütun üst/alt highlight (renk floplu)
        String[] colors = getCrateColors(crateName);
        String hColor = colors[flopTick % colors.length];
        ItemStack hl = makeBorderGlass(hColor);
        inv.setItem(4,  hl.clone());
        inv.setItem(40, hl.clone());
    }

    // ─────────────────────────────────────────────
    //  SPIN BİTİŞ
    // ─────────────────────────────────────────────
    private void finalizeSpin(Player p, CrateSession s) {
        // Orta slota ödülü koy
        s.inv.setItem(RESULT_SLOT, s.reward.clone());

        // Etrafı altın cam çerçeve
        ItemStack gold = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta gm = gold.getItemMeta(); gm.setDisplayName(" "); gold.setItemMeta(gm);
        for (int sl : new int[]{13, 21, 23, 31}) s.inv.setItem(sl, gold.clone());

        // Sesler & partiküller
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 0.5, 0.5, 0.2);

        // Ödül ismi
        String rewardName = (s.reward.hasItemMeta() && s.reward.getItemMeta().hasDisplayName())
            ? ChatColor.stripColor(s.reward.getItemMeta().getDisplayName())
            : formatMaterial(s.reward.getType());

        // ─── SADECE KAZANAN OYUNCUYA GÖRÜNEN TITLE ───
        // Kasa rengine göre dinamik renk
        String[] clr = getCrateColors(s.crateName);
        String titleLine1 = color(clr[0] + "&l\u2728 KUTLUYORUZ! \u2728");
        String titleLine2 = color("&#ffffff&l" + rewardName + " &r" + clr[2] + "» " + capitalize(s.crateName) + " Kasasindan");
        p.sendTitle(titleLine1, titleLine2, 5, 100, 20);

        // 2 sn sonra ver & kapat
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) return;
                p.getInventory().addItem(s.reward.clone());
                p.sendMessage(color(clr[0] + "&l\u2605 &r&#ffffff" + rewardName + " &7envantere eklendi! &8[" + capitalize(s.crateName) + " Kasasi]"));
                p.closeInventory();
                activeSessions.remove(p.getUniqueId());
            }
        }.runTaskLater(this, 42L);
    }

    // ─────────────────────────────────────────────
    //  KASA ÖNİZLEME — "Kasa On Izleme" SİMETRİK RGB ANİMASYONLU BAŞLIK
    //  Başlık: sadece renkler oynuyor, simetrik dalga (ortadan dışa)
    // ─────────────────────────────────────────────
    private void openCratePreview(Player p, String crateName) {
        // "Kasa On Izleme" yerine kasanın adı + simetrik animasyon
        String previewLabel = capitalize(crateName) + " Kasa On Izleme";
        String title = color(getSymmetricAnimTitle(crateName, previewLabel, flopTick));
        Inventory inv = Bukkit.createInventory(null, 45, title);

        String[] clr = getCrateColors(crateName);
        // Kenar cam
        for (int i = 0; i < 45; i++) {
            if (i < 9 || i > 35 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, makeBorderGlass(clr[i % clr.length]));
            }
        }

        // İçindeki itemleri ortaya diz
        List<ItemStack> items = crateItems.getOrDefault(crateName, new ArrayList<>());
        int slot = 10, idx = 1;
        for (ItemStack item : items) {
            if (slot % 9 == 8) slot += 2;
            if (slot > 34) break;
            ItemStack display = item.clone();
            ItemMeta dm = display.getItemMeta();
            if (dm != null) {
                List<String> l = dm.hasLore() ? new ArrayList<>(dm.getLore()) : new ArrayList<>();
                l.add(0, color("&8#" + idx));
                dm.setLore(l);
                display.setItemMeta(dm);
            }
            inv.setItem(slot++, display);
            idx++;
        }
        inv.setItem(40, createBtn(Material.BARRIER, "&#ff3333&lKapat"));
        p.openInventory(inv);
    }

    // ─────────────────────────────────────────────
    //  SİMETRİK ANİMASYON BAŞLIĞI — ortadan dışa dalga, sadece renkler oynar
    // ─────────────────────────────────────────────
    private String getSymmetricAnimTitle(String crateId, String label, int tick) {
        String[] clr = getCrateColors(crateId);
        int len = label.length();
        int mid = len / 2;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            char c = label.charAt(i);
            if (c == ' ') { sb.append(' '); continue; }
            // Ortadan uzaklık = dalga fazı
            int dist = Math.abs(i - mid);
            int ci   = ((dist + tick) % clr.length + clr.length) % clr.length;
            sb.append(clr[ci]).append("&l").append(c);
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────
    //  KASA EFEKTLERİ — ORBİTAL + İTEM DÖNDÜRME
    // ─────────────────────────────────────────────
    private void updateCrateEffects() {
        for (Map.Entry<String, Location> entry : crates.entrySet()) {
            if (!crateEffects.getOrDefault(entry.getKey(), false)) continue;

            Location base = entry.getValue().clone().add(0.5, 0.5, 0.5);
            double   t    = flopTick * 0.15;
            String   cid  = entry.getKey();

            // ── 6 noktalı dönen orbital halka (END_ROD partikülleri) ──
            for (int i = 0; i < 6; i++) {
                double angle = t + (Math.PI * 2.0 / 6) * i;
                double rx = 0.9 * Math.cos(angle);
                double rz = 0.9 * Math.sin(angle);
                double ry = Math.sin(t * 0.8 + i) * 0.3;
                base.getWorld().spawnParticle(
                    Particle.END_ROD,
                    base.clone().add(rx, ry + 0.6, rz),
                    1, 0, 0, 0, 0
                );
            }

            // ── İÇ HALKA: kasanın içindeki itemleri temsil eden küçük FLAME partikülleri döner ──
            List<ItemStack> pool = crateItems.getOrDefault(cid, new ArrayList<>());
            int itemCount = Math.min(pool.size(), 8);
            if (itemCount > 0) {
                for (int i = 0; i < itemCount; i++) {
                    double angle2 = -t * 1.3 + (Math.PI * 2.0 / itemCount) * i;
                    double rx2 = 0.45 * Math.cos(angle2);
                    double rz2 = 0.45 * Math.sin(angle2);
                    double ry2 = 0.5 + Math.sin(t * 1.5 + i * 0.7) * 0.15;
                    base.getWorld().spawnParticle(
                        Particle.FLAME,
                        base.clone().add(rx2, ry2, rz2),
                        1, 0, 0, 0, 0.001
                    );
                }
            }

            // ── Kasanın üstünde yükselen FIREWORK ──
            Location topLoc = entry.getValue().clone().add(0.5, 1.7, 0.5);
            topLoc.getWorld().spawnParticle(Particle.FIREWORK, topLoc, 2, 0.1, 0.05, 0.1, 0.02);

            // ── Her 8 tickte bir TOTEM patlaması (kasanın rengiyle) ──
            if (flopTick % 8 == 0) {
                entry.getValue().clone().add(0.5, 2.1, 0.5).getWorld()
                    .spawnParticle(Particle.TOTEM_OF_UNDYING,
                        entry.getValue().clone().add(0.5, 2.1, 0.5),
                        4, 0.15, 0.15, 0.15, 0.01);
            }

            // ── Dış halka: büyük dönen spiral (ENCHANT) ──
            for (int i = 0; i < 3; i++) {
                double spiralAngle = t * 1.5 + (Math.PI * 2.0 / 3) * i;
                double srx = 1.2 * Math.cos(spiralAngle);
                double srz = 1.2 * Math.sin(spiralAngle);
                double sry = Math.sin(t + i) * 0.5;
                base.getWorld().spawnParticle(
                    Particle.ENCHANT,
                    base.clone().add(srx, sry + 0.5, srz),
                    1, 0, 0, 0, 0
                );
            }
        }
    }

    // ─────────────────────────────────────────────
    //  ANİMASYON — ANAHTAR MENÜSÜ GÜNCELLEMESİ
    //  SADECE anahtar ismi (örn. "Afk Key") görünür, başka bir şey yok
    // ─────────────────────────────────────────────
    private void updateAnimatedMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            InventoryView view  = p.getOpenInventory();
            String        title = ChatColor.stripColor(view.getTitle());
            if (!title.contains("Anahtar")) continue;

            Inventory inv = view.getTopInventory();
            for (int slot = 11; slot <= 15; slot++) {
                ItemStack item = inv.getItem(slot);
                if (item == null || !item.hasItemMeta()) continue;
                ItemMeta      meta = item.getItemMeta();
                List<String>  lore = meta.getLore();
                if (lore == null || lore.isEmpty()) continue;
                String keyId    = ChatColor.stripColor(lore.get(0));
                String baseName = KEY_BASE_NAMES.getOrDefault(keyId, keyId);
                // SADECE baseName (örn. "Afk Key") — başka hiçbir şey yok
                meta.setDisplayName(getKeyAnimText(baseName, keyId, flopTick));
                item.setItemMeta(meta);
            }
        }
    }

    // ─────────────────────────────────────────────
    //  ANAHTAR İSMİ ANİMASYONU — her key kendi rengiyle
    //  AFK → gri/beyaz floplu
    // ─────────────────────────────────────────────
    private String getKeyAnimText(String text, String keyId, int tick) {
        String[] clr = getCrateColors(keyId);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ' ') { sb.append(' '); continue; }
            sb.append(clr[(i + tick) % clr.length]).append(text.charAt(i));
        }
        return color(sb.toString());
    }

    private String[] getCrateColors(String id) {
        return CRATE_COLORS.getOrDefault(id.toLowerCase(),
            new String[]{"&#ffffff","&#dddddd","&#aaaaaa","&#dddddd","&#ffffff","&#cccccc"});
    }

    // ─────────────────────────────────────────────
    //  ANAHTAR MENÜSÜ
    // ─────────────────────────────────────────────
    private void openKeyMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "Anahtar Menusu");
        ItemStack cam = createBtn(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, cam);
        inv.setItem(11, createKey(Material.GRAY_DYE,    "afk"));
        inv.setItem(12, createKey(Material.RED_DYE,     "casino"));
        inv.setItem(13, createKey(Material.ORANGE_DYE,  "gear"));
        inv.setItem(14, createKey(Material.LIME_DYE,    "booster"));
        inv.setItem(15, createKey(Material.NETHER_STAR, "all"));
        p.openInventory(inv);
    }

    // createKey — SADECE baseName görünür (örn. "Afk Key"), logo/ek bilgi yok
    private ItemStack createKey(Material m, String id) {
        String baseName = KEY_BASE_NAMES.getOrDefault(id, id);
        ItemStack item = new ItemStack(m);
        ItemMeta meta  = item.getItemMeta();
        meta.setDisplayName(getKeyAnimText(baseName, id, 0));
        meta.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, id);
        // Lore = sadece gizli id (animasyon için, renksiz görüntülenir)
        meta.setLore(Collections.singletonList(color("&8" + id)));
        item.setItemMeta(meta);
        return item;
    }

    // ─────────────────────────────────────────────
    //  İADE SİSTEMİ (dokunulmadı)
    // ─────────────────────────────────────────────
    private void openIadeMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color("&7Fear Craft - &fIade"));
        for (int i = 0; i < deathRecords.size() && i < 45; i++) {
            DeathRecord dr = deathRecords.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(dr.playerUUID));
            sm.setDisplayName(color("&#ffcc00&l" + dr.playerName + " &7(#" + dr.id + ")"));
            List<String> lore = new ArrayList<>();
            lore.add(color("&8&m------------------------"));
            lore.add(color("&7Olum Tarihi: &f" + dr.date));
            lore.add(color("&7Katil: &c" + dr.killerName));
            lore.add(color("&7Konum: &a" + dr.loc.getBlockX() + ", " + dr.loc.getBlockY() + ", " + dr.loc.getBlockZ()));
            lore.add(color("&8&m------------------------"));
            lore.add(color("&#00ffcc» Sag Tikla: &fDetayli Incele"));
            lore.add(color("&#00ffcc» Sol Tikla: &fDirekt Iade Et"));
            sm.setLore(lore);
            head.setItemMeta(sm);
            inv.setItem(i, head);
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
    //  ENVANTER CLICK
    // ─────────────────────────────────────────────
    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();

        // Kumar menüsü — tüm tıklama engel
        if (activeSessions.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            return;
        }

        String title = ChatColor.stripColor(e.getView().getTitle());
        if (!title.contains("Iade") && !title.contains("Anahtar") &&
            !title.contains("Kasa")  && !title.contains("Olum Detayi")) return;

        e.setCancelled(true);
        if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;

        ItemStack clicked  = e.getCurrentItem();
        Material  m        = clicked.getType();
        String    itemName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        // Anahtar menüsü
        if (title.contains("Anahtar") && m != Material.BLACK_STAINED_GLASS_PANE) {
            p.getInventory().addItem(clicked.clone());
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }
        // İade
        else if (title.contains("Fear Craft") && title.contains("Iade")) {
            if (m == Material.PLAYER_HEAD) {
                try {
                    String id = itemName.split("#")[1].replace(")", "").trim();
                    DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
                    if (dr != null) {
                        if (e.isLeftClick()) {
                            Player t = Bukkit.getPlayer(dr.playerUUID);
                            if (t != null && t.isOnline()) { t.getInventory().setContents(dr.items); p.sendMessage(color("&aIade edildi.")); }
                        } else if (e.isRightClick()) openIadeDetail(p, dr);
                    }
                } catch (Exception ignored) {}
            } else if (m == Material.BARRIER)       p.closeInventory();
            else if (m == Material.EMERALD_BLOCK)   openIadeMenu(p);
            else if (m == Material.LAVA_BUCKET)     { deathRecords.clear(); openIadeMenu(p); p.sendMessage(color("&cGecmis silindi.")); }
        }
        // Ölüm detayı
        else if (title.contains("Olum Detayi")) {
            if (m == Material.BARRIER) openIadeMenu(p);
            else if (m == Material.RED_DYE || m == Material.LIME_DYE) {
                List<String> l = clicked.getItemMeta().getLore();
                if (l == null || l.isEmpty()) return;
                String id = ChatColor.stripColor(l.get(0));
                DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
                if (dr != null && m == Material.LIME_DYE) {
                    Player t = Bukkit.getPlayer(dr.playerUUID);
                    if (t != null && t.isOnline()) { t.getInventory().setContents(dr.items); p.sendMessage(color("&aIade edildi.")); }
                }
                if (dr != null && m == Material.RED_DYE) { deathRecords.remove(dr); openIadeMenu(p); }
            }
        }
        // Kasa önizleme — kapat
        else if (title.contains("Kasa") && m == Material.BARRIER) p.closeInventory();
    }

    @EventHandler
    public void onInvClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        UUID uid = p.getUniqueId();
        CrateSession s = activeSessions.get(uid);
        if (s != null && !s.done) {
            activeSessions.remove(uid);
        }
    }

    // ─────────────────────────────────────────────
    //  ÖLÜM
    // ─────────────────────────────────────────────
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        deathRecords.add(0, new DeathRecord(
            e.getEntity(),
            e.getEntity().getKiller() != null ? e.getEntity().getKiller().getName() : "Bilinmiyor",
            e.getEntity().getInventory().getContents()
        ));
    }

    // ─────────────────────────────────────────────
    //  UTILS
    // ─────────────────────────────────────────────
    public String color(String text) {
        Pattern pt = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher mt = pt.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (mt.find()) {
            String hex = mt.group(1);
            StringBuilder rep = new StringBuilder("§x");
            for (char c : hex.toCharArray()) rep.append('§').append(c);
            mt.appendReplacement(sb, rep.toString());
        }
        mt.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private ItemStack createBtn(Material m, String n) {
        ItemStack i = new ItemStack(m);
        ItemMeta mt = i.getItemMeta();
        mt.setDisplayName(color(n));
        i.setItemMeta(mt);
        return i;
    }

    private ItemStack createBtn(Material m, String n, String hiddenData) {
        ItemStack i = createBtn(m, n);
        ItemMeta mt = i.getItemMeta();
        mt.setLore(Collections.singletonList(color("&8" + hiddenData)));
        i.setItemMeta(mt);
        return i;
    }

    private String formatMaterial(Material mat) {
        String raw = mat.name().replace('_', ' ').toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String w : raw.split(" ")) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    // ─────────────────────────────────────────────
    //  SAVE / LOAD
    // ─────────────────────────────────────────────
    private void saveData() {
        for (String k : crates.keySet()) {
            getConfig().set("crates." + k + ".loc",   crates.get(k));
            getConfig().set("crates." + k + ".items", crateItems.get(k));
        }
        saveConfig();
    }

    private void loadData() {
        crates.clear(); crateItems.clear(); crateEffects.clear();
        if (!getConfig().contains("crates")) return;
        for (String k : Objects.requireNonNull(getConfig().getConfigurationSection("crates")).getKeys(false)) {
            crates.put(k, getConfig().getLocation("crates." + k + ".loc"));
            List<?> raw = getConfig().getList("crates." + k + ".items");
            List<ItemStack> items = new ArrayList<>();
            if (raw != null) for (Object o : raw) if (o instanceof ItemStack) items.add((ItemStack) o);
            crateItems.put(k, items);
            crateEffects.put(k, true);
        }
    }

    @EventHandler
    public void onGlide(EntityToggleGlideEvent e) {
        if (e.getEntity() instanceof Player p &&
            elytraBanned.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) {
            e.setCancelled(true);
        }
    }
}
