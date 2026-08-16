# ReportSystem

[Click here for English Documentation](README.md)

ReportSystem, Minecraft sunucuları için geliştirilmiş yeni nesil, kapsamlı bir oyuncu raporlama ve moderasyon sistemidir. Geleneksel metin tabanlı raporların ötesine geçerek; görsel tekrar (replay) kayıtları, etkileşimli arayüzler (GUI) ve topluluk odaklı bir Overwatch inceleme sistemi sunar.

Ölçeklenebilirlik için tasarlanmış olup, tekli sunucuların yanı sıra BungeeCord ve Velocity ağlarını da tam olarak destekler.

## Temel Özellikler

* **Görsel Replay Sistemi:** Rapor edilen oyuncunun hareketlerini, savaş anlarını ve blok etkileşimlerini otomatik olarak kaydeder. Yetkililer rapor anını saniyesi saniyesine görsel olarak inceleyebilir.
* **Overwatch (Topluluk İncelemesi):** CS:GO'dan ilham alınan bu sistemle, güvenilir oyuncularınız raporları inceleyip karara bağlayabilir.
* **Anti-Cheat Entegrasyonu:** Polar, Vulcan ve GrimAC için doğrudan destek. Şüpheli bulunan oyuncular için otomatik rapor ve kayıt oluşturur.
* **Sunucular Arası Uyumluluk (Cross-Server):** BungeeCord ve Velocity ağları üzerinde raporların, tekrarların ve cezaların anlık senkronizasyonu.
* **Etkileşimli Menüler:** Raporları yönetmek, kayıtları izlemek ve ceza vermek için tamamen GUI tabanlı yönetim.
* **Güven Faktörü (Trust Factor):** Oyuncular, oluşturdukları raporların doğruluk payına göre güven puanı kazanır veya kaybeder.
* **Discord Webhook'ları:** Yeni raporlar ve yetkili işlemleri için eşzamanlı Discord bildirimleri.
* **Veritabanı Desteği:** SQLite (yerel) ve MySQL (ağ) ile sorunsuz entegrasyon.

## Kurulum

1. **Releases** sayfasından derlenmiş en güncel `.jar` dosyasını indirin.
2. Dosyayı sunucunuzun `plugins/` klasörüne yerleştirin.
   > **Not:** Eğer bir proxy ağı kullanıyorsanız, eklentiyi hem alt sunuculara (Spigot/Paper) hem de proxy sunucusuna (BungeeCord/Velocity) kurmalısınız.
3. Ayar dosyalarının oluşması için sunucuyu başlatın.
4. `config.yml` dosyasını yapılandırın (Ağ uyumluluğu için MySQL ayarlarını yapmayı unutmayın).
5. Sunucuyu yeniden başlatın.

## Komutlar ve İzinler

| Komut | Yetki (Permission) | Açıklama |
|---|---|---|
| `/report <oyuncu> <sebep>` | `reportsystem.report` | Raporlama arayüzünü açar. |
| `/reports` | `reportsystem.admin` | Yetkili rapor yönetim panelini açar. |
| `/overwatch` | `reportsystem.overwatch` | Topluluk inceleme (Overwatch) arayüzünü açar. |

## Gereksinimler
* Java 17 veya üzeri
* Bukkit/Spigot/Paper 1.16+
* PacketEvents (Replay sistemi için zorunlu kütüphane)

## Bağlantılar & Destek

Daha fazla dökümantasyon, teknik destek ve tartışmalar için [kareblok.tc](https://kareblok.tc) adresini ziyaret edebilirsiniz.

---
KAREBLOK tarafından geliştirilmiştir. MIT Lisansı altında açık kaynak olarak sunulmaktadır.
