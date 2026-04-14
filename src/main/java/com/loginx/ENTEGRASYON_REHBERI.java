// ═══════════════════════════════════════════════════════════════════════════
//  LoginX.java  →  onEnable() içine eklemen gereken kod
// ═══════════════════════════════════════════════════════════════════════════
//
// Mevcut onEnable() metodunun SONUNA şunları ekle:
//
//   // ── Kılıç Sistemi ──────────────────────────────────────────────────────
//   SwordManager swordManager = new SwordManager(this);
//
//   SwordCommand swordCmd = new SwordCommand(this, swordManager);
//   getCommand("shulkerkilicver").setExecutor(swordCmd);
//   getCommand("endermankilicver").setExecutor(swordCmd);
//   getCommand("orumcekkilicver").setExecutor(swordCmd);
//   getCommand("phantomkilicver").setExecutor(swordCmd);
//   getCommand("golemkilicver").setExecutor(swordCmd);
//   getCommand("creeperkilicver").setExecutor(swordCmd);
//   getCommand("kilicvermenu").setExecutor(swordCmd);
//   // ───────────────────────────────────────────────────────────────────────
//
// Bağımlılıklar (import):
//   import com.loginx.SwordManager;
//   import com.loginx.SwordCommand;
//
// DOSYA YAPISI (src/main/java/com/loginx/ altına koy):
//   └── SwordManager.java   ← kılıç mantığı + olaylar
//   └── SwordCommand.java   ← komutlar + menü
//   └── TrapX.java          ← değişmedi
//   └── EconomyBridge.java  ← değişmedi
//   └── LoginX.java         ← onEnable() güncellemesi gerekli
//
// ═══════════════════════════════════════════════════════════════════════════
//   KILIC ÖZELLİKLERİ ÖZET TABLOSU
// ═══════════════════════════════════════════════════════════════════════════
//
//  Kılıç           Komut               Efekt                  Cooldown
//  ─────────────   ─────────────────   ─────────────────────  ────────
//  Shulker         /shulkerkilicver    Levitasyon+Yavaşlık    30s
//  Enderman        /endermankilicver   2.5 blok fırlatma      30s
//  Örümcek         /orumcekkilicver    Örümcek ağı (3.5s)     30s
//  Phantom         /phantomkilicver    Elytra engeli (2 kişi) 230s
//  Golem           /golemkilicver      Direnç boz + Yavaşlık4 230s
//  Creeper         /creeperkilicver    TNT patlama efekti     30s
//
//  Menü:           /kilicvermenu       → RGB animasyonlu GUI
//
// ═══════════════════════════════════════════════════════════════════════════
//   RGB COUNTDOWN SİSTEMİ
// ═══════════════════════════════════════════════════════════════════════════
//
//  Her kılıç özelliği kullanılınca ActionBar'da görünür:
//  ⏱ Bekleme: Xs  ■■■■■■■■■■■■■■■■■■■■  (dolu/boş bloklar)
//
//  Renk: Kılıç rengiyle blendlanmış dönen RGB (HSB hue geçişi).
//  Shulker  → Mor   tonu
//  Enderman → Mavi  tonu
//  Örümcek  → Yeşil tonu
//  Phantom  → Cyan  tonu
//  Golem    → Beyaz tonu
//  Creeper  → Açık  Yeşil tonu
//
// ═══════════════════════════════════════════════════════════════════════════
