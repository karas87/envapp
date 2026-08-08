# Changelog

Tüm önemli değişiklikler bu dosyada belgelenmiştir.  
Format: [Keep a Changelog](https://keepachangelog.com/tr/1.0.0/)

---

## [3.0.0] - 03.06.2026

### Eklendi
- Credentials
  - Ortam ekleme/yükleme getirildi.
  - Credential eklerken password göster/gizle butonu eklendi.
- Search
  - Arama için paralel dosya okuma optimizasyonları yapıldı.
  - Arama sonuçlarında test class'larının gösterilmesi sağlandı.
  - Arama sonuçlarının grid üzerinde gösterilmesi sağlandı.
  - Arama sonuçlarının içinde client-side search desteği getirildi.
  - Arama sonuçlarına özet pop-up'ı eklendi.
  - Arama sonuçlarındaki kolonların yanına kopyalama butonu eklendi.
  - Arama sonuçlarındaki Test dosyalarının yeşil renkte gösterilmesi sağlandı.
  - Aramaya index desteği getirildi.
  - Arama için indexleme kullanılacaksa index yenile ve sil butonları ile index güncellenmesi sağlandı.
- Token
  - Token sekmesi tasarlandı ve JWT token oluşturma desteği getirildi.
  - Token ortamlarının credential ortamlarıyla aynı şekilde yönetilmesi sağlandı.
  - Yenile butonu ile ortamların yenilenmesi sağlandı.
  - Ortamlara bağlı uygun URL'lerin credentials'tan alınarak gösterilmesi sağlandı.
  - Organizasyon bilgisinin seçilen ortama ve kulancıya göre gösterilmesi sağlandı.
  - Token alındığında (ortam - kullanıcı - organizasyon - token alma tarihi) bilgisinin cookie label'ının yanında gösterilmesi sağlandı.
- Terminal
  - Terminal sekmesi tasarlandı ve gömülü terminal desteği getirildi.
  - Shell seçimi(cmd/powershell) sağlandı.
  - Shell, Path ve Java versiyonu seçimi sağlandı.
  - Seçilen kombinasyonların kaydedilmesi ve sonraki açılışlarda gelmesi sağlandı.
  - Terminal'i çalıştırabilmek için uygun java sürümlerinin listelenmesi sağlandı.
  - Çalıştır ve Durdur butonları ile Webterminal'in başlatılması ve durdurulması sağlandı.
  - Temizle butonu ile logların temizlenmesi sağlandı.
  - Bat dosyasının çalıştırılması ve logların gösterilmesi sağlandı.
- Git
  - Git sekmesi tasarlandı ve workspace klasörü seçme desteği getirildi.
  - Seçilen klasördeki tüm Git projeleri otomatik olarak taranarak listelendi.
  - Her proje için aktif branch adı ve versiyon bilgisi gösterildi.
  - Seçilen projelerde Fetch, Pull ve Checkout işlemlerinin çalıştırılması sağlandı.
  - Branch veya tag girişiyle Checkout işlemi sağlandı.
  - Tek tıkla `release` branch'ine geçiş (Checkout Release) butonu eklendi.
  - Checkout Release & Pull birleşik işlem butonu eklendi.
  - JAVA projeler için Maven build komutları eklendi: Clean Install, Clean Install (Skip Tests), Clean Install (4C), Clean Install (4C, Skip Tests).
  - REACT projeler için Yarn Install komutu eklendi.
  - Seçilen projenin tipine göre build butonları dinamik olarak gösterildi/gizlendi.
  - İşlem çıktıları konsol/log alanına anlık olarak aktarıldı.
  - İşlemi Durdur butonu ile çalışan process tree'nin sonlandırılması sağlandı.
---

## [2.0.0]

### Eklendi
- Sistem tepsisi (System Tray) desteği: tek tıkla göster/gizle, çıkış menüsü
- Toast bildirimi (panoya kopyalama onayı)
- Dark tema CSS (`dark-theme.css`)
- Tablo sütunlarında satır içi düzenleme (inline edit)
- URL sütununda tıklanabilir `Hyperlink` + 📋 kopyalama butonu
- Anahtar (`key`) arama/filtreleme (`FilteredList`)
---

## [1.0.0]

### Eklendi
- Temel kimlik bilgisi yönetimi (Key, URL, Username, Password)
- Ortam bazlı JSON veri depolama (`data/<Ortam>.json`)
- JavaFX tablo görünümü ile CRUD işlemleri

---

### Teknik
- Java 21, JavaFX 21.0.6
- Jackson Databind 2.21.2
- Lombok 1.18.38
- Maven Shade Plugin ile fat JAR üretimi

