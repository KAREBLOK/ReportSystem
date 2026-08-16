# 🛡️ ReportSystem - Gelişmiş Oyuncu Raporlama ve Replay Sistemi

🇬🇧 [Click here for the English version](README.md)

ReportSystem, Minecraft sunucuları için geliştirilmiş yeni nesil, zengin özellikli bir raporlama ve moderasyon sistemidir. Geleneksel metin tabanlı rapor eklentilerinin yerini alarak tam etkileşimli GUI'ler, **görsel tekrar (replay) kayıtları** ve Overwatch (CS:GO tarzı) topluluk inceleme sistemi sunar!

Son derece optimize edilmiştir ve Spigot, BungeeCord ile Velocity ağlarını destekler.

## ✨ Özellikler

- 🎥 **Replay Sistemi:** Rapor edilen oyuncunun hareketlerini, savaşını ve blok kırmalarını otomatik olarak kaydeder, yetkililerin tam olarak ne olduğunu izlemesini sağlar.
- ⚖️ **Overwatch Sistemi:** Güvenilir oyuncularınızın raporları incelemesine ve kararlar (Suçlu/Suçsuz) vermesine olanak tanır, tıpkı CS:GO'daki gibi!
- 🤖 **Hile Koruması Entegrasyonları:** **Polar**, **Vulcan** veya **GrimAC** tarafından şüpheli bulunan oyuncuları otomatik olarak kaydeder ve raporlar.
- 🌐 **Sunucular Arası Destek (Cross-Server):** BungeeCord ve Velocity ağları için tam destek. Raporlar ve cezalar küresel olarak senkronize edilir!
- 🖥️ **Etkileşimli GUI'ler:** Raporları yönetin, tekrarları izleyin ve özelleştirilebilir menüler aracılığıyla oyuncuları kolayca cezalandırın.
- 📊 **Güven Seviyesi Sistemi (Trust Level):** Oyuncular, raporlarının doğruluğuna ve davranışlarına göre güven puanı kazanır veya kaybeder.
- 💬 **Discord Webhook'ları:** Detaylı rapor ve ceza loglarını doğrudan Discord sunucunuza gönderir.
- 💾 **Veritabanı Desteği:** SQLite (Yerel) veya MySQL (Ağ) ile sorunsuz çalışır.

## 🚀 Kurulum

1. Releases bölümünden en son `.jar` dosyasını indirin (veya kendiniz derleyin).
2. Dosyayı sunucunuzun `plugins/` klasörüne yerleştirin.
   - *Bir ağ (network) kullanıyorsanız, Spigot sunucularına VE BungeeCord/Velocity `plugins/` klasörüne koyun.*
3. Yapılandırma dosyalarının oluşması için sunucunuzu yeniden başlatın.
4. `config.yml` dosyasını isteğinize göre düzenleyin (Ağ kullanıyorsanız MySQL'i ayarlayın).
5. Sunucuyu bir kez daha yeniden başlatın. Kullanıma hazırsınız!

## ⚙️ Komutlar ve Yetkiler

- `/report <oyuncu> <sebep>` - Rapor GUI'sini açar (`reportsystem.report`)
- `/reports` - Yetkili rapor yönetim GUI'sini açar (`reportsystem.admin`)
- `/overwatch` - Overwatch menüsünü açar (`reportsystem.overwatch`)

## 🌐 Bağlantılar
- **Web Sitesi ve Destek:** [kareblok.tc](https://kareblok.tc)

---
*KAREBLOK tarafından geliştirilmiştir. MIT Lisansı altındadır.*
