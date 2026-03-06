package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoginX extends JavaPlugin implements Listener {

    // --- YENİ SİSTEM DEĞİŞKENLERİ ---
    private boolean chatMuted = false;
    private final Set<UUID> staffChatEnabled = new HashSet<>();
    
    // Küfür & Reklam Filtresi için yasaklı kelimeler (İstediğin gibi artırabilirsin)
    private final List<String> yasakliKelimeler = Arrays.asList("küfür1", "küfür2", "amk", "aq", "sg", ".com", ".net", ".tr");
    
    // Otomatik duyuru mesajları
    private final List<String> duyurular = Arrays.asList(
        "&#FFB6C1[Duyuru] &fSunucumuza destek olmak için mağazamıza göz atın!",
        "&#FFB6C1[Duyuru] &fKurallara uymayanları Discord üzerinden bildirebilirsiniz.",
        "&#FFB6C1[Duyuru] &fYetkili alımlarımız yakında başlayacaktır!"
    );
    private int duyuruIndex = 0;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        
        // --- LoginX2 MODÜLÜ BAĞLANTISI ---
        try {
            LoginX2 modul = new LoginX2(this); 
            Bukkit.getPluginManager().registerEvents(modul, this);
            getLogger().info("LoginX2 modulu basariyla aktif edildi!");
        } catch (Throwable t) {
            getLogger().warning("LoginX2 yuklenirken bir sorun olustu!");
        }

        startAutoBroadcaster();
        getLogger().info("LoginX Core Sistemleri (Chat, SC, Anti-Grief) Aktif!");
    }

    @Override
    public void onDisable() {
        getLogger().info("LoginX Core devre disi.");
    }

    // --- OTOMATİK DUYURU SİSTEMİ ---
    private void startAutoBroadcaster() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (Bukkit.getOnlinePlayers().isEmpty()) return; // Kimse yoksa duyuru atma
                
                Bukkit.broadcastMessage(color(duyurular.get(duyuruIndex)));
                duyuruIndex++;
                if (duyuruIndex >= duyurular.size()) duyuruIndex = 0;
            }
        }.runTaskTimer(this, 20L * 60, 20L * 180); // Her 3 dakikada bir (180 saniye)
    }

    // --- GELİŞMİŞ GİRİŞ / ÇIKIŞ ---
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        
        // Giriş mesajını özelleştir
        e.setJoinMessage(color("&#00FF00[+] &7" + p.getName() + " sunucuya katıldı!"));
        
        // Ekrana karşılama yazısı (Title) gönder
        p.sendTitle(color("&#FF69B4&lHOŞ GELDİN"), color("&fUmarım keyifli vakit geçirirsin"), 10, 60, 10);
        
        // Hoş geldin sesi çal
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        e.setQuitMessage(color("&#FF0000[-] &7" + e.getPlayer().getName() + " sunucudan ayrıldı."));
        staffChatEnabled.remove(e.getPlayer().getUniqueId());
    }

    // --- SOHBET YÖNETİCİSİ (KÜFÜR KORUMASI & MUTE & SC) ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        String msg = e.getMessage().toLowerCase();

        // 1. Yetkili Sohbeti (Staff Chat) Kontrolü
        if (staffChatEnabled.contains(p.getUniqueId())) {
            e.setCancelled(true);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission("loginx.staff")) {
                    online.sendMessage(color("&#FFA500[Yetkili] &f" + p.getName() + ": &e" + e.getMessage()));
                }
            }
            return;
        }

        // 2. Chat Kapalıysa Engelle
        if (chatMuted && !p.hasPermission("loginx.admin")) {
            e.setCancelled(true);
            p.sendMessage(color("&#FF0000[!] &cSohbet şu anda yetkililer tarafından kapatılmıştır!"));
            return;
        }

        // 3. Küfür ve Reklam Koruması
        if (!p.hasPermission("loginx.admin")) {
            for (String yasakli : yasakliKelimeler) {
                if (msg.contains(yasakli)) {
                    e.setCancelled(true);
                    p.sendMessage(color("&#FF0000[!] &cMesajınız yasaklı kelime içerdiği için engellendi!"));
                    // İstersen burada kelimeyi yıldızlayabilirsin: e.setMessage(msg.replace(yasakli, "***"));
                    return;
                }
            }
        }
    }

    // --- ANTI-GRIEF KORUMASI ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Material type = e.getBlock().getType();
        
        // Tehlikeli blokların koyulmasını engelle
        if (type == Material.TNT || type == Material.BEDROCK || type == Material.LAVA || type == Material.LAVA_BUCKET) {
            if (!p.hasPermission("loginx.build")) {
                e.setCancelled(true);
                p.sendMessage(color("&#FF0000[!] &cBu bloğu koymak için yetkiniz yok!"));
            }
        }
    }

    // --- YENİ KOMUTLAR ---
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        
        // /sil (Chat Temizleme)
        if (cmd.getName().equalsIgnoreCase("sil")) {
            if (!sender.hasPermission("loginx.admin")) {
                sender.sendMessage(color("&#FF0000Yetkiniz yok!")); return true;
            }
            for (int i = 0; i < 100; i++) {
                Bukkit.broadcastMessage(""); // Chat'i boşluklarla doldur
            }
            Bukkit.broadcastMessage(color("&#00FF00[!] &aSohbet " + sender.getName() + " tarafından temizlendi!"));
            return true;
        }

        // /sustur (Chat Kapatma/Açma)
        if (cmd.getName().equalsIgnoreCase("sustur")) {
            if (!sender.hasPermission("loginx.admin")) {
                sender.sendMessage(color("&#FF0000Yetkiniz yok!")); return true;
            }
            chatMuted = !chatMuted;
            if (chatMuted) {
                Bukkit.broadcastMessage(color("&#FF0000[!] &cSohbet " + sender.getName() + " tarafından KAPATILDI!"));
            } else {
                Bukkit.broadcastMessage(color("&#00FF00[!] &aSohbet " + sender.getName() + " tarafından AÇILDI!"));
            }
            return true;
        }

        // /sc (Yetkili Sohbetine Giriş/Çıkış)
        if (cmd.getName().equalsIgnoreCase("sc")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Bu komut sadece oyundan kullanilabilir."); return true;
            }
            if (!player.hasPermission("loginx.staff")) {
                player.sendMessage(color("&#FF0000Yetkiniz yok!")); return true;
            }
            
            UUID uuid = player.getUniqueId();
            if (staffChatEnabled.contains(uuid)) {
                staffChatEnabled.remove(uuid);
                player.sendMessage(color("&#FF0000[!] &cYetkili sohbetinden çıktınız, normal sohbettesiniz."));
            } else {
                staffChatEnabled.add(uuid);
                player.sendMessage(color("&#00FF00[!] &aYetkili sohbetine girdiniz, yazdıklarınızı sadece yetkililer görebilir."));
            }
            return true;
        }

        return true;
    }

    // --- YARDIMCI METOTLAR (LoginX2 için de gerekli) ---
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
        }
                
