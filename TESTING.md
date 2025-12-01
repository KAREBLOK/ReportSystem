# ReportSystem - Yeni Özellikler Test Rehberi

Bu dokümanda eklediğimiz yeni teknik özelliklerin nasıl test edileceğini bulabilirsiniz.

## 📋 Ön Hazırlık

### 1. Projeyi Derleyin
```bash
mvn clean package -DskipTests
```

### 2. Plugin'i Sunucuya Kurun
- `spigot/target/ReportSystem-Spigot-1.0.0.jar` dosyasını `plugins/` klasörüne kopyalayın
- Sunucuyu başlatın

### 3. Config Dosyasını Kontrol Edin
`plugins/ReportSystem/config.yml` dosyasını açın ve yeni ayarları görün:
- `telemetry` bölümü
- `discord-webhook` bölümü
- `error-reporting` bölümü
- `update-channel` ayarı

---

## 🧪 Test Komutları

Tüm testler için `/rstest` komutunu kullanacaksınız.

### Yardım Menüsü
```
/rstest
```
Tüm mevcut test komutlarını gösterir.

---

## 1️⃣ Telemetri Sistemi Testi

### Test Komutu:
```
/rstest telemetry
```

### Ne Test Ediliyor?
- ✅ Telemetri etkin mi?
- ✅ Server UUID oluşturulmuş mu?
- ✅ Telemetri verisi gönderilebiliyor mu?
- ✅ Event tracking çalışıyor mu?

### Beklenen Çıktı:
```
[Test] Testing Telemetry System...
[Test] Server UUID: 550e8400-e29b-41d4-a716-446655440000
[Test] Sending telemetry data...
[Test] ✓ Telemetry sent successfully!
[Test] Tracking custom event...
[Test] Event tracked!
```

### Kontrol Noktaları:
1. **Console Log:** Telemetri mesajlarını kontrol edin
2. **Config:** `config.yml` içinde `server-uuid` otomatik oluşturulmuş mu?
3. **API Endpoint:** (Şu anda olmayabilir - normal)

### Config Ayarları:
```yaml
telemetry:
  enabled: true  # false yaparak kapatabilirsiniz
  server-uuid: ""  # Otomatik oluşturulur
```

---

## 2️⃣ Discord Webhook Testi

### Test Komutu:
```
/rstest webhook
```

### Ne Test Ediliyor?
- ✅ Webhook etkin mi?
- ✅ Test bildirimi gönderilebiliyor mu?
- ✅ Embed formatı doğru mu?

### Önce Webhook URL Ekleyin:
`config.yml` dosyasını düzenleyin:
```yaml
discord-webhook:
  enabled: true
  url: "DISCORD_WEBHOOK_URL_BURAYA"
  events:
    new-report: true
    report-closed: true
    punishment: true
```

### Discord Webhook URL Alma:
1. Discord sunucunuzda bir kanala gidin
2. Kanal Ayarları → Entegrasyonlar → Webhooks
3. "Yeni Webhook" oluşturun
4. URL'yi kopyalayın

### Beklenen Çıktı:
```
[Test] Testing Discord Webhook...
[Test] Sending test notification...
[Test] ✓ Webhook notification sent!
[Test] Check your Discord channel
[Test] ✓ Custom notification sent!
```

### Discord'da Göreceğiniz:
- 🔔 Bir embed mesaj (rapor bildirimi)
- 🔔 Bir custom bildirim mesajı

---

## 3️⃣ Hata Raporlama Sistemi Testi

### Test Komutu:
```
/rstest error
```

### Ne Test Ediliyor?
- ✅ Error reporter etkin mi?
- ✅ Hatalar loglanıyor mu?
- ✅ Error istatistikleri tutuluyor mu?
- ✅ Log dosyaları oluşuyor mu?

### Beklenen Çıktı:
```
[Test] Testing Error Reporter...
[Test] Creating test exception...
[Test] ✓ Error logged successfully!
[Test] Check plugins/ReportSystem/error-logs/ folder
[Test] Recent error logs:
  - error-2024-01-20.log
[Test] Error statistics:
  1x - java.lang.RuntimeException|TestCommand.testErrorReporter:123
```

### Kontrol Noktaları:
1. **Log Klasörü:** `plugins/ReportSystem/error-logs/` klasörüne bakın
2. **Log Dosyası:** `error-YYYY-MM-DD.log` dosyası oluşmuş mu?
3. **İçerik:** Log dosyasını açıp test hatanızı görün

### Gerçek Hata Testi:
Bir rapor oluştururken kasıtlı olarak hata yaparsanız, otomatik loglanacaktır.

---

## 4️⃣ Update Checker Testi

### Test Komutu:
```
/rstest update
```

### Ne Test Ediliyor?
- ✅ Update checker çalışıyor mu?
- ✅ Versiyon karşılaştırması yapılıyor mu?

### Beklenen Çıktı:
```
[Test] Testing Update Checker...
[Test] Current version: 1.0.0
[Test] Checking for updates...
[Test] Note: This requires valid Spigot resource ID
[Test] Check console for update information
```

### Console'da Görecekleriniz:
- Eğer güncelleme varsa:
  ```
  ========================================
  ReportSystem Update Available!
  Current Version: 1.0.0
  New Version: 1.1.0
  Download: https://www.spigotmc.org/resources/12345/
  ========================================
  ```

### Admin Join Bildirimi:
Op yetkili biri sunucuya girdiğinde, güncelleme varsa bildirim görecek.

---

## 5️⃣ Beta Kanal Sistemi Testi

### Mevcut Kanalı Görme:
```
/rstest beta
```

### Kanal Değiştirme:
```
/rstest beta stable   # Stable sürümler
/rstest beta beta     # Beta sürümler
/rstest beta alpha    # Alpha sürümler
/rstest beta dev      # Development builds
```

### Ne Test Ediliyor?
- ✅ Kanal sistemi çalışıyor mu?
- ✅ Kanal değişimi kaydediliyor mu?
- ✅ Farklı kanallarda güncelleme kontrolü yapılabiliyor mu?

### Beklenen Çıktı:
```
[Test] Testing Beta Channel System...
[Test] ✓ Channel changed to: beta
[Test] Checking for updates on this channel...
[Test] Latest version: 1.1.0-beta.1
[Test] Pre-release: true
```

### Config'e Kaydedilme:
```yaml
general:
  update-channel: "beta"  # Seçtiğiniz kanal
```

---

## 6️⃣ Sistem İstatistikleri

### Test Komutu:
```
/rstest stats
```

### Ne Gösterir?
- 📊 Tüm sistemlerin durumu
- 📊 Telemetri bilgileri
- 📊 Webhook durumu
- 📊 Error istatistikleri
- 📊 Update kanalı

### Örnek Çıktı:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
System Statistics

Telemetry:
  Enabled: true
  Server UUID: 550e8400-e29b-41d4-a716-446655440000

Discord Webhook:
  Enabled: true

Error Reporter:
  Enabled: true
  Total errors: 3
  Recent logs: 2

Update Channel:
  Current: stable
  Version: 1.0.0
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 🔍 Gerçek Senaryolar ile Test

### Senaryo 1: Rapor Oluşturma ile Webhook Testi
1. Discord webhook'u aktif edin
2. Normal bir rapor oluşturun: `/report TestPlayer Hile kullanıyor`
3. Discord kanalınızı kontrol edin - bildirim geldi mi?

### Senaryo 2: Hata Loglama Gerçek Test
1. Bir hata oluşturacak aksiyon yapın (örn: var olmayan bir raporu açmaya çalışın)
2. Error log klasörünü kontrol edin
3. Hata detaylarını inceleyin

### Senaryo 3: Telemetri Periyodik Gönderim
1. Telemetri'yi etkinleştirin
2. Sunucuyu çalışır durumda bırakın
3. İlk telemetri 5 dakika sonra gönderilecek
4. Sonrasında her 24 saatte bir gönderilecek
5. Console loglarında göreceksiniz

---

## 📝 Test Checklist

Tüm özellikleri test ettikten sonra bu listeyi doldurun:

- [ ] **Telemetri**
  - [ ] Sistem aktif
  - [ ] UUID oluşturuldu
  - [ ] Veri gönderimi test edildi
  - [ ] Event tracking çalışıyor

- [ ] **Discord Webhook**
  - [ ] Webhook URL eklendi
  - [ ] Test bildirimi gönderildi
  - [ ] Discord'da mesaj görüldü
  - [ ] Gerçek rapor bildirimi test edildi

- [ ] **Error Reporter**
  - [ ] Test hatası loglandı
  - [ ] Log dosyası oluşturuldu
  - [ ] İstatistikler çalışıyor
  - [ ] Gerçek hata yakalandı

- [ ] **Update Checker**
  - [ ] Güncelleme kontrolü yapıldı
  - [ ] Console mesajı görüldü
  - [ ] Admin join bildirimi test edildi

- [ ] **Beta Channel**
  - [ ] Kanal değiştirildi
  - [ ] Config'e kaydedildi
  - [ ] Farklı kanallar test edildi

- [ ] **Sistem İstatistikleri**
  - [ ] Stats komutu çalıştı
  - [ ] Tüm bilgiler görüldü

---

## ⚙️ Config Referansı

### Tam Config Örneği:
```yaml
general:
  check-updates: true
  update-channel: "stable"  # stable, beta, alpha, dev

telemetry:
  enabled: true
  server-uuid: ""  # Auto-generated

discord-webhook:
  enabled: true
  url: "https://discord.com/api/webhooks/..."
  events:
    new-report: true
    report-closed: true
    punishment: true

error-reporting:
  enabled: true
```

---

## 🐛 Sorun Giderme

### Telemetri Çalışmıyor
- Config'te `enabled: true` olduğundan emin olun
- API endpoint henüz yoksa normal (localhost testinde)
- Console'da hata mesajı var mı kontrol edin

### Webhook Mesaj Göndermiyor
- URL doğru mu?
- Discord'da webhook hala aktif mi?
- `enabled: true` olduğundan emin olun
- Test komutuyla önce deneyin

### Error Logger Çalışmıyor
- `error-reporting.enabled: true` olmalı
- `plugins/ReportSystem/error-logs/` klasörü var mı?
- Yazma izinleri var mı?

### Update Checker Hata Veriyor
- Internet bağlantısı var mı?
- Spigot API'ye erişim var mı?
- Resource ID doğru mu? (şu an test için 12345)

---

## 📊 Beklenen Performans

### Telemetri
- İlk gönderim: 5 dakika sonra
- Periyodik: Her 24 saatte bir
- Veri boyutu: ~500 bytes

### Webhook
- Gönderim süresi: <1 saniye
- Asenkron çalışır (sunucuyu bloklamaz)

### Error Reporter
- Log yazma: <10ms
- Dosya boyutu: ~2KB per error
- Otomatik temizleme: 7 gün sonra (config ile ayarlanabilir)

---

## ✅ Test Başarılı!

Tüm testler başarılı olduyunda:
1. ✅ Tüm sistemler çalışıyor
2. ✅ Config ayarları doğru
3. ✅ Loglar oluşuyor
4. ✅ Webhook mesajları geliyor
5. ✅ Update kontrolü yapılıyor

Artık production'a hazırsınız! 🎉

---

## 💡 İpuçları

1. **Production'da:** `telemetry.enabled` true bırakın - geliştirmeye yardımcı olur
2. **Discord Webhook:** Sadece önemli eventler için aktif edin
3. **Error Reporting:** Enabled bırakın - bug tespiti için kritik
4. **Update Channel:** Stable kullanın (beta sadece test için)
5. **Test Komutu:** Production'da kaldırabilirsiniz (güvenlik)

---

## 📞 Destek

Sorun yaşarsanız:
- `plugins/ReportSystem/error-logs/` klasörünü kontrol edin
- Console loglarını inceleyin
- `/rstest stats` ile sistem durumunu görün
