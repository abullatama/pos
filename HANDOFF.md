# HANDOFF: Rebranding Identitas (a3coffeeshop & athialoundry), Logo Launcher & Struk 58mm, Sanitasi Keamanan UI, dan Kesiapan Go-Live

## 1. Ringkasan Perubahan Sesi Ini

Sesi ini menyelesaikan pembaruan menyeluruh identitas merek aplikasi kasir, eliminasi tuntas nama kode sementara (*placeholder*) `"tenang"`, pembaruan aset peluncur Android, format cetak struk kasir termal 58 mm, dan sanitasi keamanan antarmuka kasir menjelang fase *Go-Live*:

### A. Rebranding Total Nama Usaha & Eliminasi Kata "Tenang"
1. **Identitas Aplikasi Resmi:**
   - **Cafe:** Nama aplikasi di layar/launcher Android resmi diubah menjadi **`a3coffeeshop`**.
   - **Laundry:** Nama aplikasi di layar/launcher Android resmi diubah menjadi **`athialoundry`**.
2. **Eliminasi Total Package ID "Tenang":**
   - Menghapus package name sementara `id.tenang.kasir.*` (OQ-01 resmi ditutup).
   - `app-cafe/build.gradle.kts`: `namespace = "id.a3coffeeshop"` dan `applicationId = "id.a3coffeeshop"`.
   - `app-laundry/build.gradle.kts`: `namespace = "id.athialoundry"` dan `applicationId = "id.athialoundry"`.
   - Struktur folder dan deklarasi package kelas cangkang (`MainActivity`, `NavHost`, `AppConfig`) dimigrasi sepenuhnya ke `id.a3coffeeshop` dan `id.athialoundry`.
   - Pesan otomatis WhatsApp notifikasi cucian di `feature:laundry` diperbarui menyebut *"athialoundry"*.

---

### B. Ikon Peluncur Aplikasi Baru (Android Adaptive Icon)
1. **`app-cafe` (`a3coffeeshop`):**
   - Menggantikan foreground vektor cangkir kopi lama dengan **Logo Resmi A3** (*triple-A interlocking*) beresolusi tinggi dengan safe zone 68 dp pada seluruh kepadatan layar (*mipmap* mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi).
   - Latar belakang launcher bertekstur *Warm Cream* (`#F4F2E6`).
2. **`app-laundry` (`athialoundry`):**
   - Menggantikan foreground vektor drum mesin cuci lama dengan **Logo Resmi Laundry** (*hanger-house*) beresolusi tinggi dengan safe zone 68 dp.
   - Latar belakang launcher bertekstur *Crisp Linen* (`#FAFAFB`).

---

### C. Pembaruan Kepala Struk & Nota Termal 58 mm (`:core:printer`)
Pada `CustomerReceiptFormatter`, `RefundReceiptFormatter`, `CashOutReceiptFormatter`, dan `ZReportFormatter`:
1. **Logo Grafis Bitmap Terpusat:**
   - Dicetak otomatis di bagian paling atas struk menggunakan perintah ESC/POS raster bitmap standar (`GS v 0`) pada lebar 384 dot dengan proteksi transparansi alpha.
2. **Identitas Usaha, Owner, dan Alamat Resmi (OQ-25 Ditutup):**
   - **Nama Toko:** `A3 COFFEESHOP` / `ATHIA LOUNDRY`.
   - **Nama Owner:** `DEVENTRI SILAMATITI BULUT` (panjang tepat 31 karakter, muat rapi dalam 1 baris kertas thermal tanpa text wrap).
   - **Alamat:** `Jl. Trans Sulawesi Kelurahan Bintauna`.
   - **Kontak/WA:** `0823-4778-5986` untuk kedua unit usaha.

---

### D. Sanitasi Keamanan Antarmuka (UI) & Anti-Bocor Kredensial
1. **Eliminasi Bocoran PIN Owner di Layar Admin (`AdminMenuScreen.kt`):**
   - Menghapus teks developer hardcoded `text = "PIN default bawaan: 9999"`.
   - Menggantinya dengan instruksi netral: `"Masukkan 4 digit PIN otorisasi"`.
2. **Koreksi Label Kas Keluar (`CashOutScreen.kt`):**
   - Mengubah label ambigu `PIN: ${item.approvedBy}` menjadi `Disetujui: ${item.approvedBy}` (*Disetujui: Manager*).
3. **Penyelarasan Dokumen Go-Live (`docs/golive/`):**
   - Menyelaraskan nama usaha dan nama owner pada seluruh dokumen persiapan:
     - `CHECKLIST-HARI-PERTAMA.md`
     - `SOP-DARURAT-OPERASIONAL.md`
     - `BUKU-NOTA-CADANGAN.md`
     - `PANDUAN-PELATIHAN-STAF.md`
     - `PROTOKOL-UJI-OFFLINE-24JAM.md`

---

## 2. File yang Dimodifikasi dan Dibuat

1. **Konfigurasi & Cangkang Aplikasi:**
   - `app-cafe/build.gradle.kts` & `app-laundry/build.gradle.kts`: Pembaruan namespace & applicationId.
   - `app-cafe/src/main/res/values/strings.xml` & `app-laundry/src/main/res/values/strings.xml`: Pembaruan `app_name`.
   - `app-cafe/src/main/java/id/a3coffeeshop/*`: Refaktor package dari `id.tenang.kasir.cafe`.
   - `app-laundry/src/main/java/id/athialoundry/*`: Refaktor package dari `id.tenang.kasir.laundry`.
   - `app-cafe/src/main/res/drawable/` & `app-laundry/src/main/res/drawable/`: Pembaruan background adaptive icon.
   - `app-cafe/src/main/res/mipmap-*/` & `app-laundry/src/main/res/mipmap-*/`: Pembuatan aset ikon launcher resmi.
2. **Modul Printer & Layar Transaksi:**
   - `core/printer/src/main/kotlin/id/a3/core/printer/escpos/EscPosBuilder.kt`: Dukungan raster centered bitmap dengan penanganan transparansi.
   - `core/printer/src/main/kotlin/id/a3/core/printer/format/*`: Integrasi logo bitmap, owner, dan alamat baru pada 4 formatter struk.
   - `feature/payment/src/main/kotlin/id/a3/feature/payment/presentation/PaymentViewModel.kt`: Default store profile baru.
   - `feature/shift/src/main/kotlin/id/a3/feature/shift/presentation/*`: Penyelarasan profil toko dan sanitasi label `Disetujui:`.
   - `feature/admin/src/main/kotlin/id/a3/feature/admin/presentation/*`: Sanitasi teks PIN bawaan dan format uji printer/SOP.
   - `feature/laundry/src/main/kotlin/id/a3/feature/laundry/presentation/LaundryOrdersViewModel.kt`: Pesan WhatsApp athialoundry.
3. **Dokumentasi & Pengujian:**
   - `docs/open-questions.md`: Penutupan OQ-01 dan OQ-25 (Selesai).
   - `docs/golive/*`: Penyelarasan 5 dokumen go-live.
   - `progress.md`: Pencatatan log rekayasa perangkat lunak.
   - Seluruh unit test terkait di `:core:printer`, `:feature:payment`, `:feature:shift`, `:feature:admin`, `:core:sync`, `:core:ui`, dan `:build-logic`.

---

## 3. Status Pengujian & Kompilasi APK

- **Unit Test Modul Terkait:**
  `./gradlew :core:printer:testDebugUnitTest :feature:payment:testDebugUnitTest :feature:admin:testDebugUnitTest :feature:shift:testDebugUnitTest :build-logic:test` $\rightarrow$ **BUILD SUCCESSFUL** (100% lulus tanpa kegagalan).
- **Compile APK Debug Kedua Aplikasi:**
  `./gradlew :app-cafe:assembleDebug :app-laundry:assembleDebug` $\rightarrow$ **BUILD SUCCESSFUL** (sukses dibangun dalam 6 detik).
- **Lokasi Berkas APK Siap Pasang:**
  - Cafe: `app-cafe/build/outputs/apk/debug/app-cafe-debug.apk`
  - Laundry: `app-laundry/build/outputs/apk/debug/app-laundry-debug.apk`

---

## 4. Panduan untuk Sesi Baru (Instruksi Pengujian & Operasional)

1. **Pemasangan APK ke Tablet Fisik (Redmi Pad 2):**
   - Pasang kedua file APK debug ke tablet kasir.
   - Periksa tampilan home screen / app drawer tablet: ikon logo A3 dan Laundry harus tampil tajam, proporsional, dan berlabel `a3coffeeshop` serta `athialoundry`.
2. **Verifikasi Mode Demo (Bypass) & Cetak Struk Fisik:**
   - Buka aplikasi; jika muncul layar registrasi perangkat, tekan tombol `"Masuk Mode Demo / Lewati"`.
   - Buka shift kasir dengan modal awal Rp 200.000.
   - Hubungkan Bluetooth ke printer iWare C58BT dan lakukan transaksi uji.
   - Periksa hasil cetak kertas struk thermal 58 mm:
     - Logo toko tercetak di atas.
     - Nama usaha (`A3 COFFEESHOP` atau `ATHIA LOUNDRY`).
     - Teks `Owner: DEVENTRI SILAMATITI BULUT` pas 1 baris tanpa wrap.
     - Alamat `Jl. Trans Sulawesi Kelurahan Bintauna` dan kontak `0823-4778-5986`.
3. **Penyelesaian Fitur Terakhir F43 (Uji Offline 24 Jam):**
   - F43 adalah satu-satunya fitur yang berstatus `"passes": false` di `feature_list.json` karena menunggu pengujian fisik 24 jam dengan panduan di `docs/golive/PROTOKOL-UJI-OFFLINE-24JAM.md`.
