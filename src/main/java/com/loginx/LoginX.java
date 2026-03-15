package com.loginx;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoginX extends JavaPlugin implements Listener {

    // --- SİSTEM DEĞİŞKENLERİ ---
    private boolean chatMuted = false;
    private final Set<UUID> staffChatEnabled = new HashSet<>();
    private final Set<UUID> frozenPlayers = new HashSet<>(); // Dondurulan oyuncular
    private final HashMap<UUID, Long> chatCooldown = new HashMap<>(); // Anti-Spam
    
    // Küfür & Reklam Filtresi
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
            getLogger().warning("LoginX2 yuklenirken bir sorun olustu veya sinif bulunamadi!");
        }

        startAutoBroadcaster();
        getLogger().info("LoginX Premium Core Sistemleri Aktif Edildi!");
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
                if (Bukkit.getOnlinePlayers().isEmpty()) return; 
                Bukkit.broadcastMessage(color(duyurular.get(duyuruIndex)));
                duyuruIndex++;
                if (duyuruIndex >= duyurular.size()) duyuruIndex = 0;
            }
        }.runTaskTimer(this, 20L * 60, 20L * 180); 
    }

    // --- GELİŞMİŞ GİRİŞ / ÇIKIŞ (EFEKTLER VE ÖZEL EŞYA) ---
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        
        e.setJoinMessage(color("&#00FF00[+] &7" + p.getName() + " sunucuya katıldı!"));
        p.sendTitle(color("&#FF69B4&lHOŞ GELDİN"), color("&fUmarım keyifli vakit geçirirsin"), 10, 60, 10);
        
        // Enderman Işınlanma Efekti ve Sesi
        p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation(), 150, 0.5, 1.0, 0.5, 0.1);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        // İlk Girişe Özel RGB Açıklamalı Eşya Verme (Sadece ilk giriş)
        if (!p.hasPlayedBefore()) {
            ItemStack starterItem = new ItemStack(Material.COMPASS);
            ItemMeta meta = starterItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color("&#00FFFF&lSunucu Rehberi"));
                List<String> lore = new ArrayList<>();
                lore.add(color("&7Bu eşya size yol gösterecektir."));
                lore.add("");
                lore.add(color("&#FF69B4Sahibi: &f" + p.getName()));
                lore.add(color("&#00FF00Sağ tıklayarak menüyü açın!"));
                meta.setLore(lore);
                starterItem.setItemMeta(meta);
            }
            p.getInventory().addItem(starterItem);
            p.sendMessage(color("&#00FFFF[!] &fAramıza ilk defa katıldığın için sana özel rehber eşyası verdik!"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        e.setQuitMessage(color("&#FF0000[-] &7" + e.getPlayer().getName() + " sunucudan ayrıldı."));
        staffChatEnabled.remove(e.getPlayer().getUniqueId());
        frozenPlayers.remove(e.getPlayer().getUniqueId());
    }

    // --- SOHBET YÖNETİCİSİ (MENTION, SPAM, KÜFÜR, MUTE, SC) ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        String msg = e.getMessage();
        String lowerMsg = msg.toLowerCase();

        // 1. Yetkili Sohbeti (Staff Chat)
        if (staffChatEnabled.contains(p.getUniqueId())) {
            e.setCancelled(true);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission("loginx.staff")) {
                    online.sendMessage(color("&#FFA500[Yetkili] &f" + p.getName() + ": &e" + msg));
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

        // 3. Anti-Spam (3 saniye bekleme süresi)
        if (!p.hasPermission("loginx.admin")) {
            if (chatCooldown.containsKey(p.getUniqueId())) {
                long timeLeft = (chatCooldown.get(p.getUniqueId()) - System.currentTimeMillis()) / 1000;
                if (timeLeft > 0) {
                    e.setCancelled(true);
                    p.sendMessage(color("&#FF0000[!] &cTekrar mesaj göndermek için " + timeLeft + " saniye beklemelisin!"));
                    return;
                }
            }
            chatCooldown.put(p.getUniqueId(), System.currentTimeMillis() + 3000); // 3000ms = 3 saniye
        }

        // 4. Küfür ve Reklam Koruması
        if (!p.hasPermission("loginx.admin")) {
            for (String yasakli : yasakliKelimeler) {
                if (lowerMsg.contains(yasakli)) {
                    e.setCancelled(true);
                    p.sendMessage(color("&#FF0000[!] &cMesajınız yasaklı kelime içerdiği için engellendi!"));
                    return;
                }
            }
        }

        // 5. Mention (@OyuncuBahsetme Sistemi)
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (msg.contains(target.getName())) {
                // Mesajdaki oyuncu ismini renklendir
                e.setMessage(msg.replace(target.getName(), color("&#00FFFF@" + target.getName() + "&r")));
                // Bahsedilen oyuncuya bildirim sesi gönder
                target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
                target.sendTitle("", color("&#00FFFF" + p.getName() + " &7senden bahsetti!"), 10, 40, 10);
            }
        }
    }

    // --- OYUNCU VE DÜNYA KORUMA SİSTEMLERİ ---

    // 1. Oyuncu Hareketi: Dondurma ve Boşluk (Void) Kurtarma
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        
        // Dondurulmuş oyuncu hareket edemez
        if (frozenPlayers.contains(p.getUniqueId())) {
            // Sadece kafa çevirmesine izin ver, yürümesini engelle
            if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getZ() != e.getTo().getZ()) {
                e.setTo(e.getFrom()); 
                p.sendMessage(color("&#FF0000[!] &cŞüpheli işlemler sebebiyle donduruldunuz!"));
            }
        }

        // Void Kurtarma: Oyuncu -64 altına düşerse doğma noktasına ışınla (eşyaları gitmez)
        if (p.getLocation().getY() < -64) {
            Location spawn = p.getWorld().getSpawnLocation();
            p.teleport(spawn);
            p.setFallDistance(0); // Düşme hasarını sıfırla
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            p.sendMessage(color("&#00FFFF[!] &fBoşluğa düştüğün için başlangıç noktasına kurtarıldın!"));
        }
    }

    // 2. Blok Koyma: Anti-Grief
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Material type = e.getBlock().getType();
        
        if (type == Material.TNT || type == Material.BEDROCK || type == Material.LAVA || type == Material.LAVA_BUCKET) {
            if (!p.hasPermission("loginx.build")) {
                e.setCancelled(true);
                p.sendMessage(color("&#FF0000[!] &cBu tehlikeli bloğu koymak için yetkiniz yok!"));
            }
        }
    }

    // 3. Patlama Koruması: Creeper ve Wither blok kırmasın
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        // Patlama bloklara zarar vermesin ama can götürsün
        e.blockList().clear(); 
    }

    // --- KOMUT YÖNETİCİSİ ---
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        
        // /sil (Chat Temizleme)
        if (cmd.getName().equalsIgnoreCase("sil")) {
            if (!sender.hasPermission("loginx.admin")) { sender.sendMessage(color("&#FF0000Yetkiniz yok!")); return true; }
            for (int i = 0; i < 100; i++) Bukkit.broadcastMessage(""); 
            Bukkit.broadcastMessage(color("&#00FF00[!] &aSohbet " + sender.getName() + " tarafından temizlendi!"));
            return true;
        }

        // /sustur (Chat Kapatma/Açma)
        if (cmd.getName().equalsIgnoreCase("sustur")) {
            if (!sender.hasPermission("loginx.admin")) { sender.sendMessage(color("&#FF0000Yetkiniz yok!")); return true; }
            chatMuted = !chatMuted;
            if (chatMuted) Bukkit.broadcastMessage(color("&#FF0000[!] &cSohbet " + sender.getName() + " tarafından KAPATILDI!"));
            else Bukkit.broadcastMessage(color("&#00FF00[!] &aSohbet " + sender.getName() + " tarafından AÇILDI!"));
            return true;
        }

        // /sc (Yetkili Sohbetine Giriş/Çıkış)
        if (cmd.getName().equalsIgnoreCase("sc")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Sadece oyun ici!"); return true; }
            if (!player.hasPermission("loginx.staff")) { player.sendMessage(color("&#FF0000Yetkiniz yok!")); return true; }
            
            UUID uuid = player.getUniqueId();
            if (staffChatEnabled.contains(uuid)) {
                staffChatEnabled.remove(uuid);
                player.sendMessage(color("&#FF0000[!] &cYetkili sohbetinden çıktınız."));
            } else {
                staffChatEnabled.add(uuid);
                player.sendMessage(color("&#00FF00[!] &aYetkili sohbetine girdiniz."));
            }
            return true;
        }

        // /dondur <oyuncu> (Yeni Özellik)
        if (cmd.getName().equalsIgnoreCase("dondur")) {
            if (!sender.hasPermission("loginx.admin")) { sender.sendMessage(color("&#FF0000Yetkiniz yok!")); return true; }
            if (args.length == 0) { sender.sendMessage(color("&#FF0000Kullanım: /dondur <oyuncu>")); return true; }
            
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { sender.sendMessage(color("&#FF0000Oyuncu bulunamadı!")); return true; }

            UUID targetId = target.getUniqueId();
            if (frozenPlayers.contains(targetId)) {
                frozenPlayers.remove(targetId);
                target.sendMessage(color("&#00FF00[!] &aDondurma işleminiz kaldırıldı, hareket edebilirsiniz."));
                sender.sendMessage(color("&#00FF00" + target.getName() + " adlı oyuncunun buzu çözüldü."));
            } else {
                frozenPlayers.add(targetId);
                target.sendTitle(color("&#FF0000&lDONDURULDUN!"), color("&fHile kontrolü yapılıyor, çıkış yapma!"), 10, 100, 10);
                sender.sendMessage(color("&#FF0000" + target.getName() + " adlı oyuncu donduruldu."));
            }
            return true;
        }

        return true;
    }

    // --- YARDIMCI METOT (HEX RENK DESTEĞİ) ---
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
