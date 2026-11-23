<div align="center">

# ReportSystem

**Minecraft Sunucuları İçin Yeni Nesil Görsel Replay, Overwatch İnceleme ve Oyuncu Moderasyon Sistemi**

[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/)
[![Minecraft](https://img.shields.io/badge/Minecraft-Paper%201.21%2B-blue.svg)](https://papermc.io/)
[![PacketEvents](https://img.shields.io/badge/PacketEvents-2.14.0%2B-green.svg)](https://github.com/retrooper/packetevents)
[![Sürüm](https://img.shields.io/badge/S%C3%BCr%C3%BCm-v2.2.0-brightgreen.svg)](https://github.com/KAREBLOK/ReportSystem/releases)
[![Lisans](https://img.shields.io/badge/Lisans-MIT-yellow.svg)](LICENSE)
[![Discord](https://img.shields.io/badge/Discord-Bize%20Kat%C4%B1l%C4%B1n-5865F2.svg?logo=discord&logoColor=white)](https://discord.com/invite/WZc5bE9cK8)

[Click here for English Documentation (README.md)](README.md)

</div>

---

## Genel Bakış

**ReportSystem**, modern Minecraft ağları (Paper, Purpur, Velocity, BungeeCord) için sıfırdan yüksek performans odaklı olarak geliştirilmiş kapsamlı bir raporlama ve oyuncu denetim ekosistemidir.

Eski usul, yetersiz metin logları veya kolayca taklit edilebilen sohbet ekran görüntüleri yerine ReportSystem; rapor anında şüphelinin hareketlerini **kare kare paket seviyesinde kaydeden Replay sistemi**, CS:GO tarzı **topluluk destekli Overwatch inceleme mekanizması**, hile tespitinde otomatik tetiklenen anti-cheat kancaları, etkileşimli GUI menüleri ve çok kanallı bildirim sistemiyle sunucu yönetiminize tam kontrol sağlar.

---

## İçindekiler

- [Öne Çıkan Özellikler](#öne-çıkan-özellikler)
- [Sistem Gereksinimleri](#sistem-gereksinimleri)
- [Kurulum Rehberi](#kurulum-rehberi)
  - [Tekil Sunucu Kurulumu (Paper)](#tekil-sunucu-kurulumu-paper)
  - [Ağ / Proxy Kurulumu (Velocity / BungeeCord)](#ağ--proxy-kurulumu-velocity--bungeecord)
- [2.2.0'a Yükseltme](#220a-yükseltme)
- [Temel Sistemler](#temel-sistemler)
  - [1. Görsel Replay Motoru](#1-görsel-replay-motoru)
  - [2. Overwatch Topluluk İnceleme Sistemi](#2-overwatch-topluluk-i̇nceleme-sistemi)
  - [3. Etkileşimli NPC Sistemi](#3-etkileşimli-npc-sistemi)
  - [4. Ceza Sistemi ve Animasyonlu Ban](#4-ceza-sistemi-ve-animasyonlu-ban)
  - [5. Akıllı Anti-Cheat Entegrasyonu](#5-akıllı-anti-cheat-entegrasyonu)
  - [6. Çok Kanallı Bildirim Sistemi](#6-çok-kanallı-bildirim-sistemi)
  - [7. Discord Webhook Entegrasyonu](#7-discord-webhook-entegrasyonu)
- [Yapılandırma Dosyası (config.yml)](#yapılandırma-dosyası-configyml)
- [Komutlar ve Kısayollar](#komutlar-ve-kısayollar)
- [Yetkiler (Permissions)](#yetkiler-permissions)
- [PlaceholderAPI Değişkenleri](#placeholderapi-değişkenleri)
- [Performans ve Optimizasyon](#performans-ve-optimizasyon)
- [Sorun Giderme ve S.S.S.](#sorun-giderme-ve-sss)
- [Destek ve Topluluk](#destek-ve-topluluk)

---

## Öne Çıkan Özellikler

- **Kare Hassasiyetinde Görsel Replay:** **53'ten fazla paket eylem türünü** (yürüme, koşma, zıplama, yüzme, eğilme, süzülme/elytra, kafa hareketleri, vuruşlar, yay çekme, kalkan engelleme, eşya düşürme/alma ve blok etkileşimleri) kaydeder.
- **Hotbar Kontrol Kumandası:** Replay izlerken Duraklat/Oynat, 10 Saniye İleri/Geri Sar, Hızı Ayarla (0.25x - 2x), Şüpheliye Işınlan ve Ayrıntılı Görsel Ayarları (hitbox, izler vb.) yönet.
- **Overwatch İnceleme Sistemi:** Güvenilir oyuncuların isimleri gizlenmiş anonim tekrarları izlemesini, şüpheliyi oylamasını ve XP/Seviye/Rütbe kazanmasını sağlayın.
- **Açık Topluluk Oylaması (v2.1.5+):** Rapor havuzuna tüm oyuncuların oy vermesine olanak tanıyın (`auto-complete-queue: false`). Yetkililer `/reports` üzerinden oy dağılımını canlı görüp son kararı verebilir.
- **Animasyonlu Ban Gösterisi:** Hilecileri cezalandırırken gökten örs düşürün, yıldırım çarptırın, hareketsiz dondurun ve sunucu geneline özel ölüm mesajıyla duyurun.
- **Anti-Cheat Kancaları:** **Polar**, **Vulcan** ve **GrimAC** ile doğrudan entegrasyon. Belirli bir şüphe puanı aşıldığında otomatik 30 saniyelik kayıt başlatır ve Overwatch kuyruğuna aktarır.
- **Çok Kanallı Bildirimler:** Yetkililere anlık Toast (Başarım tarzı popup), Ekran Ortası Title, Actionbar kayan yazı, özel sesler ve tıklanabilir JSON sohbet bildirimleri.
- **Proxy & Ağ Senkronizasyonu:** BungeeCord ve Velocity desteği. Merkezi MySQL (HikariCP) havuzu ile tüm sunucularda anlık rapor ve ceza senkronizasyonu.
- **PlaceholderAPI Desteği:** Rapor sayılarını, güven puanını, Overwatch rütbesini ve başarı istatistiklerini Scoreboard veya TAB üzerinde gösterin.
- **Discord Webhook Entegrasyonu:** Renkli embed pencereleri, oyuncu kafaları ve tıklanabilir butonlarla raporları ve cezaları Discord'a aktarın.

---

## Sistem Gereksinimleri

| Bileşen | Minimum Gereksinim | Önerilen |
| :--- | :--- | :--- |
| **Platform** | Paper 1.21+ (veya Purpur gibi bir Paper türevi) | En güncel Paper |
| **Java** | Java 17+ (eklentinin bayt kodu hedefi) | Paper sürümünüzün istediği Java (Paper 1.21 için Java 21) |
| **PacketEvents** | **v2.13+ (ZORUNLU)** | **v2.14.0+** (Minecraft 26.3 için gerekli) |
| **Veritabanı** | SQLite (Tek sunucular için dahili) | MySQL 8.0+ (HikariCP) |
| **Proxy (Opsiyonel)** | BungeeCord veya Velocity 3.4+ (Velocity jarı Java 21 ister) | Velocity 3.5+ |

> [!NOTE]
> **Folia desteklenmez.** Eklenti `folia-supported` bildirmez ve standart Bukkit zamanlayıcısını kullanır.

> [!IMPORTANT]
> **PacketEvents kütüphanesi zorunludur.** Replay kayıt ve oynatma motoru doğrudan PacketEvents paket sarmalayıcılarına dayanır. PacketEvents kurulu olmadığında eklenti güvenli bir şekilde kendini devre dışı bırakır.

---

## Kurulum Rehberi

### Tekil Sunucu Kurulumu (Paper)

1. En son çıkan `ReportSystem-Spigot-2.2.0.jar` ve `packetevents-spigot.jar` dosyalarını indirin.
2. İki JAR dosyasını da sunucunuzun `plugins/` klasörüne kopyalayın.
3. Ayar dosyalarının ve yerel SQLite veritabanının oluşması için sunucuyu bir kez başlatın.
4. `plugins/ReportSystem-Spigot/config.yml` dosyasını ihtiyacınıza göre düzenleyin. Dil için `language: "tr"` kullanabilirsiniz.
5. Sunucuyu yeniden başlatın veya `/rs reload` komutunu çalıştırın.

### Ağ / Proxy Kurulumu (Velocity / BungeeCord)

```mermaid
graph LR
    P[Proxy: Velocity / BungeeCord] <--> B1[Paper Sunucusu 1]
    P <--> B2[Paper Sunucusu 2]
    B1 <--> DB[(Merkezi MySQL Veritabanı)]
    B2 <--> DB
```

1. **Alt Sunucular (Paper):**
   - Her bir alt sunucunun `plugins/` klasörüne `ReportSystem-Spigot-2.2.0.jar` ve `packetevents-spigot.jar` ekleyin.
   - `plugins/ReportSystem-Spigot/config.yml` dosyasında:
     - `database.type: "mysql"` yapın.
     - MySQL bağlantı bilgilerinizi (`host`, `port`, `database`, `username`, `password`) girin.
     - `bungeecord.enabled: true` yapın ve `bungeecord.display-name` değerini sunucunun **proxy'de kayıtlı adıyla birebir aynı** yazın (ör. `"survival-1"`).
     - `bungeecord.message-secret` değerini tüm alt sunucularda ve proxy'de aynı yazın (tırnak içinde).
2. **Proxy Sunucusu:**
   - `ReportSystem-Bungee-2.2.0.jar` (BungeeCord) veya `ReportSystem-Velocity-2.2.0.jar` (Velocity) dosyasını proxy'nin `plugins/` klasörüne ekleyin.
   - Proxy config dosyasında aynı MySQL bilgilerini ve aynı `message-secret` değerini yapılandırın.
3. Tüm ağı yeniden başlatın.

---

## 2.2.0'a Yükseltme

2.2.0, proxy-sunucu mesaj protokolünü ve bazı varsayılan davranışları değiştirir. Güncellemeden önce bu listeyi okuyun.

1. **Proxy ve TÜM alt sunucuları birlikte 2.2.0'a güncelleyin.** Karışık 2.1.x / 2.2.0 ağları çalışmaz. `message-secret` her yerde aynı olmalı (proxy `message-secret`, Spigot `bungeecord.message-secret`) ve sunucu saatleri NTP ile eşitlenmiş olmalı (mesajlar 30 saniyelik pencere içinde kabul edilir). Sunucular arası replay için Spigot `bungeecord.display-name` değeri proxy config'indeki sunucu adıyla aynı olmalı.
2. **`message-secret` değerini tırnak içinde yazın** (ör. `message-secret: "abc123"`). Tırnaksız, sayıya benzeyen değerler YAML tarafından dönüştürülür ve eşleşmez.
3. **Proxy ban kontrolü:** artık giriş doğrulamasından sonra (LoginEvent) ve UUID ile yapılır. Esas kaynak veritabanıdır; bellekteki liste yalnızca yedektir. Yalnızca isimle kaydedilmiş eski banlar, oyuncunun ilk girişinde UUID'sine bağlanır. Proxy `warn` artık çalışıyor.
4. **Hibrit premium/cracked ağlar** (FastLogin veya kullanıcı adı öneki kullanılmayan Floodgate): aynı isim farklı UUID'lere ait olabilir. Yalnızca isimle verilmiş banlar, o isimle ilk giren hesabın UUID'sine bağlanır; yükseltmeden sonra bu banları gözden geçirin ve cezaları mümkünse çevrimiçi oyunculara verin.
5. **Spigot MySQL havuz ayarları** (`database.mysql.pool.*`) artık gerçekten uygulanıyor (önceden yok sayılıyordu). Önerilen `connection-timeout` değeri `5000`; mevcut dosyanızda hâlâ `30000` varsa elle düşürün. Yeni `database.mysql.ssl-mode` (varsayılan `DISABLED` = eski davranış), proxy config'lerinde de var.
6. Replay veya Overwatch vakası izlerken yalnızca `replay.viewer-allowed-commands` listesindeki komutlar kullanılabilir (yetkililer dahil). `reportsystem.view.all` yetkisi olmayan izleyiciler, izlenen oyuncudan `replay.viewer-max-distance` (48 blok) ötesine gidemez; Overwatch "replay bitti" ekranında hareket kilitlidir.
7. `language` değeri `tr` dışında olan sunucular (ör. `en`) GUI'leri artık `plugins/ReportSystem-Spigot/guis/en/` klasöründen okur. `guis/*.yml` dosyalarını özelleştirdiyseniz değişikliklerinizi oraya taşıyın.
8. **Kayıt limitleri:** `replay.limits.*`. Boyut sınırını aşan replay artık silinmez, sonundan kısaltılır. Çökme kurtarma için `recording-journal/` klasörü kullanılır.
9. Replay verisi bir güvenlik filtresiyle okunur; tanınmayan veri içeren kayıtlar açılmaz ve konsola yazılır.
10. **Bağımlılıklar:** sqlite-jdbc 3.54.1.0 (Paper ilk açılışta indirir), mysql-connector-j 26.10.0, Minecraft 26.3 için PacketEvents 2.14.0+. Connector/J 26.x resmi olarak **MySQL 8.0 ve üzerini** destekler.
11. Özel süre alanında `perm` / `-1` / `forever` artık geçersiz süre sayılır; kalıcı ceza için "permanent" seçin.
12. Yeni veritabanı sütunları `resolved_by` ve `resolved_at` otomatik eklenir. PlaceholderAPI değerleri en fazla 30 saniye gecikmeyle güncellenir. Proxy bildirimleri `reportsystem.notify` yetkisine gider (eski, tanımsız `reportsystem.staff` yerine).
13. Spigot jar'ı artık karartılmış (obfuscate) değildir (ProGuard derlemeden kaldırıldı).

---

## Temel Sistemler

### 1. Görsel Replay Motoru

Replay motoru sanal NPC paketleri ve yerel chunk önbelleğiyle çalışır. Gerçek dünyadaki bloklara veya canlı oyunculara hiçbir müdahalede bulunmadan şüphelinin hareketlerini birebir yeniden oynatır.

#### Kaydedilen 53+ Eylem Türü
- **Hareket ve Fizik:** X/Y/Z koordinatları, yaw, pitch, kafa açısı, eğilme (sneaking), koşma (sprinting), yüzme, zıplama, sürünme, elytra uçuşu.
- **Savaş ve Silahlar:** Sol tık kılıç savurma, yakın dövüş vuruşları, kritik vuruşlar, yay çekme/fırlatma, arbalet doldurma, kalkan engelleme, totem patlama efekti.
- **Envanter ve Eşyalar:** Seçili slot değiştirme, zırh giyme, sol el takası, eşya fırlatma, eşya toplama, yemek yeme, iksir içme.
- **Dünya Etkileşimi:** Blok kırma animasyonları, blok koyma, sandık/kutu açma, örs ve büyü masası kullanımı.
- **Araçlar ve Canlılar:** Ata, bota, maden arabasına binme, yönlendirme, araçtan inme.
- **Durum Efektleri:** Hasar alma tepkileri, iksir partikülleri, alev alma, sönme, ölüm, yeniden doğma, dünya/boyut değiştirme, oyun modu değiştirme.

#### Hotbar Kumanda Kontrolleri
Replay izlemeye başladığınızda envanterinize kontrol eşyaları yerleştirilir:

```
[ Slot 1 ]  Duraklat / Devam Et
[ Slot 2 ]  10 Saniye Geri Sar
[ Slot 3 ]  10 Saniye İleri Sar
[ Slot 4 ]  Replay'den Çık
[ Slot 5 ]  Hız Ayarı (0.25x | 0.5x | 1.0x | 1.5x | 2.0x)
[ Slot 6 ]  Şüpheliye Işınlan
[ Slot 8 ]  Görünüm Ayarları (Hitbox, izler, yakındaki oyuncular)
```

---

### 2. Overwatch Topluluk İnceleme Sistemi

Counter-Strike'ın Overwatch sisteminden esinlenilen bu modül; güvenilir oyuncuların şikayet edilmiş kişileri tarafsızca incelemesine ve topluluğun kendi kendini denetlemesine imkan tanır.

#### Seviye ve Rütbe İlerlemesi

| Rütbe | Gerekli XP | İsabet Oranı Şartı | Ayrılacıklar |
| :--- | :--- | :--- | :--- |
| **BRONZE** | `0 - 499 XP` | - | Standart inceleme kuyruğu |
| **SILVER** | `500 - 1.499 XP` | - | Öncelikli vaka atamaları |
| **GOLD** | `1.500 - 3.499 XP` | %75+ Doğruluk | Yüksek oy ağırlığı çarpanı |
| **DIAMOND** | `3.500+ XP` | %85+ Doğruluk | Hızlı vaka onayları ve özel ödüller |

- **Anonimlik:** Ön yargıyı önlemek için şüpheli ve raporlayan isimleri gizlenir (Örn: `Şüpheli #842`).
- **Puanlama Sistemi:** Doğru karar veren oyuncular XP kazanır. Hatalı veya rastgele oy verenlerin güven puanı ve oy ağırlığı düşer.
- **Açık Topluluk Oylaması (v2.1.5+):** Sunucular `auto-complete-queue: false` modunu açarak sınırsız sayıda oyuncunun oy vermesini sağlayabilir. Yetkililer `/reports` detay panelinde oy oranlarını canlı görüp ceza verebilir veya davayı kapatabilir.

---

### 3. Etkileşimli NPC Sistemi

Hub veya lobi dünyalarınıza PacketEvents destekli sanal Overwatch NPC'leri yerleştirebilirsiniz:

- **Kalıcı:** Konumlar ve ayarlar veritabanında saklanır; sunucu yeniden başlatıldığında otomatik geri yüklenir.
- **Özelleştirilebilir:** Oyuncu skini, isim etiketleri ve havada asılı hologramlar eklenebilir.
- **Canlı Davranış:** NPC'ler yanlarından geçen oyunculara dinamik olarak başlarını çevirip bakar.

```bash
/overwatch npc create &b&lOVERWATCH &7(Tıkla)
/overwatch npc skin <npc> Steve
/overwatch npc look <npc>
/overwatch npc move <npc>
/overwatch npc delete <id>
```

---

### 4. Ceza Sistemi ve Animasyonlu Ban

Dahili ceza sisteminin yanı sıra **LiteBans** ve **AdvancedBan** eklentileriyle tam entegrasyon sunar.

#### Animasyonlu Ban Deneyimi
Hilecileri etkili bir görsel akışla cezalandırın:
1. Şüpheli oyuncu anında olduğu yerde dondurulur.
2. Gökyüzünün en yüksek noktasından kafasına bir örs düşer.
3. Bulunduğu konuma şimşekler ve yıldırımlar çarpar.
4. Özel ölüm duyurusu tüm sunucuya geçilir ve oyuncu sunucudan atılır.

> [!NOTE]
> Animasyonlu ban için hedef oyuncunun **çevrimiçi** olması gerekir. Çevrimdışı ise ceza standart şekilde anında işlenir.

---

### 5. Akıllı Anti-Cheat Entegrasyonu

ReportSystem piyasadaki hile koruma eklentileriyle uyumlu çalışarak hile tespit anında otomatik kayıt alır:

```
[Anti-Cheat Uyarısı] -> [Şüphe Puanı Birikir] -> [Eşik Aşılır] -> [Otomatik 30s Kayıt + Overwatch Kuyruğu + Discord Bildirimi]
```

- **Desteklenen Eklentiler:**
  - **Polar Anti-Cheat:** Combat ML (makine öğrenimi), hareket, reach ve mitigation sinyallerini dinler.
  - **Vulcan Anti-Cheat:** 35+ kontrol türü (KillAura, Scaffold, Speed, Flight). Vulcan `config.yml` içinde `settings.enable-api: true` olmalıdır.
  - **GrimAC:** Gelişmiş paket tahmin tabanlı fizik simülasyonu.
- **Puan Azalma Sistemi:** Lag anlarında yanlış pozitifleri önlemek için şüphe puanı her 60 saniyede bir %50 oranında azalır.

#### Şüphe Puanı Tablosu
| Kontrol Türü | Puan | Açıklama |
| :--- | :--- | :--- |
| **Cloud Combat Behavior** | `+0.50` | Makine öğrenimi tabanlı KillAura/Aimbot tespiti (Polar ML) |
| **Auto Clicker** | `+0.45` | İnsanüstü CPS ve tıklama tutarlılığı tespiti |
| **KillAura** | `+0.40` | Çoklu hedef, ani açı kilitlenmeleri ve savaş paketi anomalileri |
| **Scaffold** | `+0.35` | İnsanüstü yol yapma rotasyonları ve blok koyma hızları |
| **Reach** | `+0.30` | Standart vurma mesafesini aşan vuruşlar |
| **Flight** | `+0.15` | Dikey hareket ve havada asılı kalma anormallikleri |
| **Speed** | `+0.10` | Yatay hız ve ivme ihlalleri |
| **Movement / Lag** | `+0.05` | Küçük hareket anomalileri (lag filtresi) |

---

### 6. Çok Kanallı Bildirim Sistemi

Yeni bir rapor oluşturulduğunda görevli yetkililere farklı kanallardan anında haber verilir:

- **Toast Bildirimi:** Ekranın sağ üst köşesinde başarım tarzı açılır pencere.
- **Ekran Başlığı (Title):** Ekranın ortasında büyük uyarı başlığı ve alt başlık.
- **Action Bar:** Hotbar üzerinde kayan kesintisiz uyarı yazısı.
- **Özel Sesler:** Özelleştirilebilir bildirim sesi ve ses tonu.
- **Etkileşimli Sohbet:** Tıklanabilir ve üzerine gelindiğinde detay gösteren JSON mesajları (`[Işınlan]`, `[İncele]`).

---

### 7. Discord Webhook Entegrasyonu

Oyun içindeki olayları doğrudan yetkili Discord kanalınıza aktarın:

- **Embed Mesajları:** Yeni Raporlar (Altın Sarısı), Replay Tamamlandı (Mavi), Cezalar (Kırmızı), Kararlar (Yeşil).
- **Etkileşimli Butonlar:** Rapor detaylarına doğrudan yönlendiren butonlar.
- **Çoklu Dil Desteği:** Sunucu dil ayarınıza göre bildirimler Türkçe veya İngilizce gönderilir.

---

## Yapılandırma Dosyası (config.yml)

### Örnek Yapılandırma

`plugins/ReportSystem-Spigot/config.yml` dosyasından bir kesit (oluşturulan dosyada tüm anahtarlar açıklamalarıyla bulunur):

```yaml
general:
  debug: false
  language: "tr"               # "tr" (Türkçe) veya "en" (İngilizce)
  check-updates: true

database:
  type: sqlite                 # "sqlite" veya "mysql" (proxy ağlarında mysql kullanın)
  mysql:
    host: "localhost"
    port: 3306
    database: "reportsystem"
    username: "root"
    password: "CHANGE_THIS_PASSWORD"
    ssl-mode: "DISABLED"
    pool:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 5000
      idle-timeout: 600000
      max-lifetime: 1800000

bungeecord:
  enabled: false
  display-name: ""             # Proxy config'indeki sunucu adıyla aynı olmalı
  message-secret: ""           # Proxy'de ve tüm alt sunucularda aynı değer, tırnak içinde

reports:
  require-online: true
  cooldown: 60
  global-rate-limit: 5
  max-reports-per-player: 3

replay:
  enabled: true
  auto-record: true
  recording-duration: 45
  max-recordings: 3
  max-file-size: 5
  auto-delete-days: 7

overwatch:
  enabled: true
  auto-complete-queue: false   # false: açık topluluk oylaması (v2.1.5+)
  min-reviewers: 3             # Yalnızca auto-complete-queue true iken kullanılır
```

### 2.2.0 ile eklenen yapılandırma anahtarları

| Anahtar | Varsayılan | Açıklama |
| :--- | :--- | :--- |
| `database.mysql.ssl-mode` | `DISABLED` | Connector/J `sslMode`: `DISABLED`, `PREFERRED`, `REQUIRED`, `VERIFY_CA`, `VERIFY_IDENTITY`. Proxy config'lerinde de var. |
| `database.mysql.pool.*` | yukarıya bakın | HikariCP havuzu (`maximum-pool-size`, `minimum-idle`, `connection-timeout`, `idle-timeout`, `max-lifetime`); artık gerçekten uygulanıyor. |
| `replay.limits.max-actions` | `150000` | Kayıt başına en fazla eylem (`0` veya `-1` = sınırsız). |
| `replay.limits.max-actions-per-type-per-second` | `400` | Saniyede aynı türden en fazla eylem (`0` veya `-1` = sınırsız). |
| `replay.crash-recovery.enabled` | `true` | Süren kayıtları düzenli olarak `recording-journal/` klasörüne yazar; çökmeden sonraki açılışta veritabanına kaydeder. |
| `replay.crash-recovery.interval-seconds` | `10` | Ara kayıt aralığı (en az 2). |
| `replay.viewer-allowed-commands` | ReportSystem komutları, özel mesaj, `helpop` | Replay / Overwatch vakası izlerken yalnızca bu komut adları çalışır. |
| `replay.viewer-max-distance` | `48` | İzleyicinin izlenen oyuncudan uzaklaşabileceği en fazla mesafe (blok); `0` = kapalı. `reportsystem.view.all` muaftır. |
| `guis/en/` (klasör) | - | İngilizce GUI dosyaları; `language` değeri `tr` değilse kullanılır. |

---

## Komutlar ve Kısayollar

### Oyuncu ve Yetkili Komutları
| Komut | Kısayollar | Yetki | Açıklama |
| :--- | :--- | :--- | :--- |
| `/report <oyuncu> [sebep]` | `/rapor`, `/reportplayer`, `/raporla` | `reportsystem.report` | Oyuncuyu raporlar (rapor GUI'sini açar) |
| `/reports [sayfa]` | `/raporlar`, `/reportlist`, `/raporlistesi` | `reportsystem.view.all` | Yetkili rapor listesini açar (tüm raporlar) |
| `/overwatch` | `/ow`, `/overwatchsystem` | `reportsystem.overwatch` | Overwatch ana menüsünü açar |
| `/overwatch stats [oyuncu]` | `/ow stats` | `reportsystem.overwatch` | Overwatch istatistiklerini gösterir (çevrimiçi oyuncu) |
| `/overwatch leaderboard` | `/ow lb`, `/ow top` | `reportsystem.overwatch` | Müfettiş sıralamasını açar |
| `/overwatch help` | `/ow help` | `reportsystem.overwatch` | Overwatch komut yardımını gösterir |

### Yönetici Komutları
`/reportsystem` kısayolları: `/rs`, `/rsystem`, `/raporsistem`.

| Komut | Yetki | Açıklama |
| :--- | :--- | :--- |
| `/reportsystem reload` | `reportsystem.admin` | Yapılandırma ve dil dosyalarını yeniler |
| `/reportsystem stats` | `reportsystem.admin` | Rapor istatistiklerini gösterir |
| `/reportsystem info` | `reportsystem.admin` | Eklenti bilgisini gösterir |
| `/reportsystem lang` | `reportsystem.admin` | Etkin dil dosyasını gösterir |
| `/reportsystem debug <true/false>` | `reportsystem.admin` | Debug kayıtlarını açar/kapatır |
| `/reportsystem testnotif` | `reportsystem.admin` | Kendinize deneme toast bildirimi gönderir |
| `/reportsystem checkadv` | `reportsystem.admin` | Başarım (toast) durumunu kontrol eder |
| `/reportsystem ban <oyuncu> <sebep> [süre]` | `reportsystem.admin` | Oyuncuyu banlar |
| `/reportsystem unban <oyuncu>` | `reportsystem.admin` | Banı kaldırır |
| `/reportsystem unmute <oyuncu>` | `reportsystem.admin` | Susturmayı kaldırır |
| `/reportsystem webreplay <raporId>` | `reportsystem.admin` | Web replay bağlantısı üretir (web replay açıksa) |
| `/overwatch addqueue <raporId> [öncelik]` | `reportsystem.overwatch.admin` | Raporu Overwatch kuyruğuna ekler |
| `/overwatch npc <create/delete/list/skin/look/move/name/select>` | `reportsystem.overwatch.admin` | Overwatch NPC'lerini yönetir |

Proxy eklentileri (BungeeCord / Velocity) komut kaydetmez.

---

## Yetkiler (Permissions)

`plugin.yml` içinde tanımlı yetkiler:

```
reportsystem.*                     # Varsayılan: op    - Tüm ReportSystem yetkileri (bypass.anticheat hariç aşağıdakiler)
reportsystem.report                # Varsayılan: true  - Rapor oluşturma
reportsystem.report.others         # Varsayılan: op    - Başkaları adına rapor oluşturma
reportsystem.view                  # Varsayılan: true  - Kendi raporlarını görme
reportsystem.view.all              # Varsayılan: op    - Tüm raporları görme (/reports); replay izleyici mesafe sınırından muaf
reportsystem.admin                 # Varsayılan: op    - /reportsystem yönetici komutları
reportsystem.notify                # Varsayılan: op    - Rapor bildirimlerini alma (proxy eklentileri de kullanır)
reportsystem.manage                # Varsayılan: op    - Raporları kabul/red etme
reportsystem.punish                # Varsayılan: op    - Ceza verme (ban, mute, kick, warn)
reportsystem.delete                # Varsayılan: op    - Rapor silme
reportsystem.cooldown.bypass       # Varsayılan: op    - Rapor bekleme süresini atlama
reportsystem.exempt                # Varsayılan: op    - Raporlanamama
reportsystem.overwatch             # Varsayılan: op    - Overwatch sistemini kullanma
reportsystem.overwatch.admin       # Varsayılan: op    - Overwatch yönetici komutları (NPC ve kuyruk yönetimi)
reportsystem.bypass.anticheat      # Varsayılan: false - Replay izleyenlere otomatik verilir; anti-cheat'ler bunu bypass kuralı olarak kullanabilir
```

---

## PlaceholderAPI Değişkenleri

| Değişken (Placeholder) | Örnek Çıktı | Açıklama |
| :--- | :--- | :--- |
| `%reportsystem_reports%` | `14` | Oyuncuya açılan toplam rapor sayısı |
| `%reportsystem_trust_level%` | `İyi` | Hesap güven derecesi (Mükemmel, İyi, Orta, Kötü, Kritik) |
| `%reportsystem_trust_points%` | `0` | Ceza puanı (zamanla azalır) |
| `%reportsystem_overwatch_rank%` | `GOLD` | Overwatch rütbesi (BRONZE, SILVER, GOLD, DIAMOND) |
| `%reportsystem_overwatch_level%` | `12` | Müfettiş seviyesi |
| `%reportsystem_overwatch_xp%` | `1850` | Toplam kazanılan inceleme tecrübe puanı |
| `%reportsystem_overwatch_reviews%` | `47` | Karara bağlanan vaka sayısı |
| `%reportsystem_overwatch_accuracy%`| `%91.4` | Doğruluk yüzdesi (50+ inceleme sonrası açılır) |

---

## Performans ve Optimizasyon

ReportSystem, yüksek oyunculu sunucularda sıfır TPS kaybı hedefiyle mimarilendirilmiştir:

- **Asenkron I/O:** Tüm veritabanı (SQLite/MySQL) sorguları ve disk kayıtları ana sunucu iş parçacığından bağımsız worker iş parçacıklarında çalışır. `performance.async-database: true` ana sunucunun asla takılmamasını garanti eder.
- **Akıllı Bellek Önbelleği (LRU Cache):** Raporlar ve kuyruk listesi bellekte önbelleğe alınır (`performance.cache.max-size: 100`, `expiry: 10m`). GUI pencereleri açılırken veritabanı gereksiz yere yorulmaz.
- **Düşük Depolama Kullanımı:** Ortalama 45 saniyelik bir replay dosyası yalnızca **50–200 KB** yer tutar. Günde 100 rapor alınan bir sunucuda günlük veri artışı 20 MB'ın altındadır.
- **Hareket Eşiği Optimizasyonu:** `movement-threshold: 0.05` ayarı gereksiz mikro titremeleri filtreleyerek kayıt boyutunu görsel kalite kaybı olmadan **%40–60 oranında küçültür**.

---

## Sorun Giderme ve S.S.S.

### Sıkça Sorulan Sorular

<details>
<summary><b>PacketEvents olmadan çalışır mı?</b></summary>
<br>
<b>Hayır.</b> Replay sistemi PacketEvents kütüphanesine bağımlıdır. Yüklü olmadığında eklenti çalışmaz.
</details>

<details>
<summary><b>İki yetkili aynı replay'i aynı anda izleyebilir mi?</b></summary>
<br>
<b>Evet.</b> Tüm replay varlıkları istemciye özel sanal paketlerdir. İzleyiciler birbirini görmez ve etkilemez.
</details>

<details>
<summary><b>Replay sırasındaki oklar veya iksirler gerçek oyunculara zarar verir mi?</b></summary>
<br>
<b>Hayır.</b> Replay mermileri ve efektleri sahte paketlerdir; gerçek dünyaya ve oyunculara hiçbir hasar vermezler.
</details>

<details>
<summary><b>Overwatch NPC'leri sunucu yeniden başladığında silinir mi?</b></summary>
<br>
<b>Hayır.</b> NPC konumları ve skin bilgileri veritabanında saklanır ve sunucu açılışında yeniden canlandırılır.
</details>

### Olası Sorunlar ve Çözümleri

- **Eklenti başlamıyor:**
  - Java sürümünüzün 17 veya üzeri olduğunu ve Paper sürümünüzün gereksinimini karşıladığını kontrol edin (`java -version`).
  - PacketEvents'in yüklü ve sunucu sürümünüzle uyumlu olduğundan emin olun (Minecraft 26.3 için 2.14.0+).
- **Replay kaydedilmiyor:**
  - `config.yml` içinde `replay.auto-record: true` olduğunu kontrol edin.
  - Replay'ler veritabanında saklanır: konsolda veritabanı hatası olup olmadığını ve sunucunun `plugins/ReportSystem-Spigot/` klasörüne (çökme kurtarma günlüğü) yazabildiğini kontrol edin.
- **Veritabanı Bağlantı Hatası:**
  - MySQL bilgilerinizi, portunuzu ve sunucu güvenlik duvarı ayarlarınızı kontrol edin.

---

## Destek ve Topluluk

- **Discord Sunucumuz:** [discord.gg/WZc5bE9cK8](https://discord.com/invite/WZc5bE9cK8)
- **Hata Bildirimi:** Hataları ve istekleri [GitHub Issues](https://github.com/KAREBLOK/ReportSystem/issues) üzerinden iletebilirsiniz.
- **Web Sitemiz:** [kareblok.tc](https://kareblok.tc)

---

<div align="center">
ReportSystem, <b>KAREBLOK</b> tarafından geliştirilmekte ve sürdürülmektedir.<br>
<a href="LICENSE">MIT Lisansı</a> altında sunulmaktadır.
</div>
