package com.loginx.trap;

import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trap önlerine RGB animasyonlu hologram koyar.
 *
 * Görünüm (fotoğraftaki gibi):
 *   ╔══════════════════╗
 *   ║  ★ TRAP #1 ★     ║  ← RGB gradient, pembe-beyaz animasyonlu
 *   ║  Sahip: PlayerX  ║
 *   ║  Banka: $50,000  ║
 *   ║  Üyeler: 3       ║
 *   ╚══════════════════╝
 */
public class TrapHologramManager {

    private final Plugin plugin;

    // trapId -> o trapa ait ArmorStand listesi
    private final HashMap<Integer, List<ArmorStand>> trapHolograms = new HashMap<>();

    // Animasyon için renk adımı
    private int colorStep = 0;

    public TrapHologramManager(Plugin plugin) {
        this.plugin = plugin;
        startAnimation();
    }

    // =========================================================
    //  HOLOGRAM OLUŞTURMA
    // =========================================================

    public void spawnTrapHologram(TrapData td, Location baseLoc) {
        // Önce eskisini kaldır
        removeHologram(td.getId());

        List<String> lines = buildLines(td, colorStep);
        List<ArmorStand> stands = spawnLines(baseLoc, lines);
        trapHolograms.put(td.getId(), stands);
    }

    private List<ArmorStand> spawnLines(Location base, List<String> lines) {
        List<ArmorStand> result = new ArrayList<>();
        double y = 0;
        for (String line : lines) {
            Location loc = base.clone().add(0, y, 0);
            ArmorStand stand = (ArmorStand) base.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setCustomNameVisible(true);
            stand.setCustomName(line);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setBasePlate(false);
            stand.setSmall(true);
            result.add(stand);
            y -= 0.28; // yukarıdan aşağı
        }
        return result;
    }

    // =========================================================
    //  GÜNCELLEME
    // =========================================================

    public void updateHologram(TrapData td) {
        List<ArmorStand> stands = trapHolograms.get(td.getId());
        if (stands == null || stands.isEmpty()) return;
        List<String> lines = buildLines(td, colorStep);
        for (int i = 0; i < Math.min(stands.size(), lines.size()); i++) {
            ArmorStand stand = stands.get(i);
            if (stand != null && !stand.isDead()) {
                stand.setCustomName(lines.get(i));
            }
        }
    }

    public void removeHologram(int trapId) {
        List<ArmorStand> stands = trapHolograms.remove(trapId);
        if (stands == null) return;
        for (ArmorStand stand : stands) {
            if (stand != null && !stand.isDead()) stand.remove();
        }
    }

    public void clearAll() {
        for (List<ArmorStand> stands : trapHolograms.values()) {
            for (ArmorStand stand : stands) {
                if (stand != null && !stand.isDead()) stand.remove();
            }
        }
        trapHolograms.clear();
    }

    // =========================================================
    //  ANİMASYON (5 saniyede bir renk değişimi)
    // =========================================================

    private void startAnimation() {
        new BukkitRunnable() {
            @Override
            public void run() {
                colorStep = (colorStep + 1) % 360;
                // Tüm hologramları güncelle
                for (Map.Entry<Integer, List<ArmorStand>> entry : trapHolograms.entrySet()) {
                    TrapData td = findTrap(entry.getKey());
                    if (td == null) continue;
                    List<String> lines = buildLines(td, colorStep);
                    List<ArmorStand> stands = entry.getValue();
                    for (int i = 0; i < Math.min(stands.size(), lines.size()); i++) {
                        ArmorStand stand = stands.get(i);
                        if (stand != null && !stand.isDead()) {
                            stand.setCustomName(lines.get(i));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 40L); // 2 saniyede bir
    }

    // =========================================================
    //  SATIR OLUŞTURMA
    // =========================================================

    /**
     * Trap için 5 satırlık hologram içeriği üretir.
     * Başlık satırı RGB gradient ile renklenir.
     */
    private List<String> buildLines(TrapData td, int step) {
        List<String> lines = new ArrayList<>();

        // ─── Başlık (RGB gradient) ───
        String title = "  ★  TRAP #" + td.getId() + "  ★  ";
        lines.add(gradientText(title, step));

        // ─── Bölücü ───
        lines.add(hexColor("FF69B4") + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // ─── Sahip ───
        lines.add(hexColor("FFB3DE") + "Sahip » " + ChatColor.WHITE + td.getOwnerName());

        // ─── Banka ───
        lines.add(hexColor("FF85C8") + "Banka » " + hexColor("00FF88")
                + "$" + String.format("%,.0f", td.getBank()));

        // ─── Üye ───
        lines.add(hexColor("FFB3DE") + "Üyeler » " + ChatColor.WHITE
                + td.getMemberCount() + " Üye");

        // ─── Boyut ───
        lines.add(hexColor("FF85C8") + "Boyut » " + ChatColor.WHITE + td.getSizeString());

        // ─── Satış ───
        if (td.isForSale()) {
            lines.add(hexColor("FFD700") + "✦ SATIŞTA » " + hexColor("00FF88")
                    + "$" + String.format("%,.0f", td.getSalePrice()));
        } else {
            lines.add(hexColor("AAAAAA") + "Satılık: " + ChatColor.RED + "Hayır");
        }

        // ─── Alt bölücü ───
        lines.add(hexColor("FF69B4") + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        return lines;
    }

    // =========================================================
    //  RGB GRADİENT YARDIMCI
    // =========================================================

    /**
     * Metni hue döngüsüyle pembe-beyaz-mor geçişli RGB'ye boyar.
     * step parametresi animasyon adımı.
     */
    private String gradientText(String text, int step) {
        StringBuilder sb = new StringBuilder();
        char[] chars = text.toCharArray();
        int len = chars.length;
        for (int i = 0; i < len; i++) {
            // Hue: pembe (310°) → mor (280°) → pembe geçişi
            float hue = ((step + i * (360f / len)) % 360) / 360f;
            // Pembe-beyaz-mor bandı için saturation & brightness ayarı
            float sat = 0.6f + 0.4f * (float) Math.abs(Math.sin(Math.toRadians(step + i * 10)));
            float bri = 0.9f + 0.1f * (float) Math.abs(Math.cos(Math.toRadians(step + i * 8)));
            java.awt.Color c = java.awt.Color.getHSBColor(hue, sat, Math.min(bri, 1f));
            sb.append(hexColor(String.format("%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue())));
            sb.append(chars[i]);
        }
        return sb.toString();
    }

    /** §x§R§R§G§G§B§B formatında hex renk kodu üretir */
    private String hexColor(String hex) {
        StringBuilder sb = new StringBuilder("§x");
        for (char c : hex.toCharArray()) {
            sb.append("§").append(c);
        }
        return sb.toString();
    }

    // =========================================================
    //  YARDIMCI
    // =========================================================

    private TrapData findTrap(int id) {
        // Plugin üzerinden erişim - bu sınıf TrapManager'a referans almıyor,
        // o yüzden dışarıdan setManager ile bağlanır
        return trapManagerRef != null ? trapManagerRef.getTrap(id) : null;
    }

    private TrapManager trapManagerRef;
    public void setTrapManager(TrapManager manager) {
        this.trapManagerRef = manager;
    }
}

