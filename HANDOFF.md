# HANDOFF: Tuntas 4 Tahap Penyelarasan Sinkronisasi Backend Laravel 12 & Kesiapan Uji Offline 24 Jam (F43)

## 1. Ringkasan Perubahan Sesi Ini

Sesi ini menuntaskan **Rencana 4 Tahap Penyelarasan Sinkronisasi & Integrasi Backend Laravel 12** (`https://a3-api.nen.asia/v1/` dan `docs/API_DOCUMENTATION.md`) secara menyeluruh, mencakup seluruh 14 jenis event outbox, mekanisme autentikasi tangguh, dan kesiapan operasional offline-first:

---

### A. Tahap 1: Ketahanan Autentikasi & Idempotensi Retriabel (F11, F28, F29, SYN-05, SYN-06)
1. **`TokenAuthenticator` OkHttp (`:core:sync`):**
   - Mengatasi potensi dead-lock dan infinite refresh loop: loop protection maksimal 3 percobaan beruntun (`priorResponse`).
   - Concurrency-safe: deteksi pergantian token otomatis oleh thread lain dan sinkronisasi `Mutex` saat memanggil `AuthManager.refreshToken()`.
2. **Proteksi HTTP 401 Unauthorized:**
   - Respon 401 tidak lagi menandai data kasir sebagai `FAILED`, melainkan tetap berstatus `PENDING` dengan `incrementAttempts()`.
   - Menjamin fakta transaksi uang kasir tidak masuk Antrean Bermasalah saat token sesi habis dan otomatis terkirim begitu kasir login kembali.
3. **Preservasi Idempotensi `bundle_id` pada Retry:**
   - `OutboxDao.retryAllFailedWithDeviceId` hanya meregenerasi `bundle_id` jika ID perangkat fisik berubah.
   - Pada percobaan kirim ulang di perangkat yang sama, `bundle_id` asli tetap dipertahankan agar respons `DUPLICATE` server bekerja secara idempoten.

---

### B. Tahap 2: Koreksi Transaksi, Void, & Refund (`correction.recorded`, F35, F36, COR-01)
1. **`CorrectionReasonMapper` (`:core:data`):**
   - Pemetaan dwiarah kode alasan koreksi antara domain OpenAPI (`wrong_input`, `customer_cancelled`, `quality_complaint`, `wrong_made`, `promo`, `other`) dan validator Laravel 12 (`salah_input`, `pelanggan_batal`, `komplain_kualitas`, `salah_buat`, `promo`, `lainnya`).
2. **Dual-Compatibility Payload di `OrderTransactionManager` & `SyncSender`:**
   - Menyertakan pasangan field kompatibel:
     - `order_line_id` (single) dan `order_line_ids` (array UUID).
     - `payment_id` dan `original_payment_id`.
     - `reason_note` dan `note` (wajib terisi jika `lainnya`).
     - `approved_by` numerik `Long` (User ID).

---

### C. Tahap 3: Siklus Shift Kasir & Siklus Cucian Laundry (F30-F32, F38, F21, F22, F24)
1. **`LaundryStatusMapper` (`:core:data`):**
   - Pemetaan dwiarah status cucian antara OpenAPI (`received`, `processing`, `ready`, `picked_up`) dan backend Laravel 12 (`diterima`, `diproses`, `siap`, `diambil`).
   - Normalisasi `user_id` dan `approved_by` menjadi numerik `Long`.
2. **Penyelarasan Siklus Shift di `OrderTransactionManager` & `SyncSender`:**
   - `shift.closed`: Otomatis menghitung dan menyertakan `expected_cash` (K1: modal + penjualan tunai + DP tunai - refund tunai - kas keluar) dan `difference`, serta jaminan array `qris_checked_payment_ids` dan `handover_order_ids`.
   - `shift.force_closed`: Menyertakan `approved_by` numerik dan jaminan field `note` non-kosong.
3. **Penyelarasan Siklus Laundry:**
   - `advanceLaundryOrderStatus`: Menyertakan `user_id` numerik staf dan `backend_status`.
   - `recordLaundryPickups`: Dual-compatibility `line_ids` array dan status `"diambil"`.

---

### D. Tahap 4: Endpoint Pendukung Tablet, Pelanggan, & Kalibrasi Waktu Server (F19, F12, LDR-01, SYN-06)
1. **Pencarian Pelanggan Laundry Online & Offline-First (`GET /v1/customers/search`):**
   - Endpoint `GET /v1/customers/search?q={query}` di `SyncApiService` dengan DTO `CustomerSearchResponse` dan `RemoteCustomerDto` (`LenientIdStringSerializer`).
   - `DefaultLaundryOrderRepository.searchCustomers` memancarkan Flow Room lokal secara instan, memanggil server live di background, dan meng-upsert pelanggan baru ke Room dengan nomor HP ternormalisasi `08...` (`PhoneNumberUtil`).
2. **Kalibrasi Waktu Server & Pencegahan Karantina `CLOCK_SKEW` (`GET /v1/time`):**
   - Endpoint `GET /v1/time` di `SyncMasterApiService` dan `MasterDataSyncManager.syncServerTime()`.
   - Menghitung offset jam tablet terhadap UTC server (`server_clock_offset_ms`) dan menyimpannya ke `sync_state`.
   - `SyncSender` mengalibrasikan `occurred_at` otomatis menggunakan offset server saat terjadi penolakan `CLOCK_SKEW`.
3. **Penyelarasan Seluruh Event Sekunder:**
   - `customer.created`: UUID deterministik dan nomor telepon bersih `08...`.
   - `menu.sold_out_changed`: tipe boolean murni (`true`/`false`).
   - `order.sent_to_kitchen`: jaminan keberadaan array `line_ids`.
   - Penanganan deteksi error dan regenerasi hash untuk seluruh 14 jenis event outbox.

---

## 2. File yang Dimodifikasi dan Dibuat

1. **Modul Inti & Arsitektur (`:core`):**
   - `core/data/src/main/kotlin/id/a3/core/data/util/LaundryStatusMapper.kt` (Dibuat)
   - `core/data/src/main/kotlin/id/a3/core/data/util/CorrectionReasonMapper.kt` (Dibuat)
   - `core/data/src/main/kotlin/id/a3/core/data/repository/OrderTransactionManager.kt` (Dimodifikasi)
   - `core/sync/src/main/kotlin/id/a3/core/sync/api/TokenAuthenticator.kt` (Dibuat)
   - `core/sync/src/main/kotlin/id/a3/core/sync/api/SyncApiService.kt` & `SyncMasterApiService.kt` (Dimodifikasi)
   - `core/sync/src/main/kotlin/id/a3/core/sync/model/SyncModels.kt` (Dimodifikasi)
   - `core/sync/src/main/kotlin/id/a3/core/sync/MasterDataSyncManager.kt` (Dimodifikasi)
   - `core/sync/src/main/kotlin/id/a3/core/sync/SyncSender.kt` (Dimodifikasi)
2. **Modul Fitur & Cangkang (`:feature` & `:app`):**
   - `feature/laundry/src/main/kotlin/id/a3/feature/laundry/repository/LaundryOrderRepository.kt` (Dimodifikasi)
   - `feature/laundry/src/main/kotlin/id/a3/feature/laundry/presentation/LaundryOrdersViewModel.kt` (Dimodifikasi)
   - `app-cafe/src/main/java/id/a3coffeeshop/MainActivity.kt` (Dimodifikasi)
   - `app-laundry/src/main/java/id/athialoundry/MainActivity.kt` (Dimodifikasi)
3. **Pengujian Unit Baru & Terperinci:**
   - `core/data/src/test/kotlin/id/a3/core/data/util/LaundryStatusMapperTest.kt` (5 skenario, Lulus)
   - `core/data/src/test/kotlin/id/a3/core/data/util/CorrectionReasonMapperTest.kt` (4 skenario, Lulus)
   - `feature/laundry/src/test/kotlin/id/a3/feature/laundry/repository/CustomerSearchSyncTest.kt` (Lulus)
   - `core/sync/src/test/kotlin/id/a3/core/sync/MasterDataSyncManagerTest.kt` (5 skenario, Lulus)
   - `core/sync/src/test/kotlin/id/a3/core/sync/SyncSenderIdempotencyTest.kt` (53 skenario, Lulus)
   - `core/sync/src/test/kotlin/id/a3/core/sync/api/TokenAuthenticatorTest.kt` (4 skenario, Lulus)
4. **Dokumentasi & Pelacakan:**
   - `progress.md`: Pencatatan log implementasi Tahap 1 s/d Tahap 4.

---

## 3. Status Pengujian & Kompilasi APK

- **Unit Test Seluruh Modul:**
  `./gradlew test` $\rightarrow$ **BUILD SUCCESSFUL** (282 test lulus 100%).
- **Uji Dependensi Arsitektur:**
  `./gradlew verifyModuleDependencies` $\rightarrow$ **BUILD SUCCESSFUL** (Isolasi modul F07 valid 100%).
- **Compile APK Debug Kedua Aplikasi:**
  `./gradlew :app-cafe:assembleDebug :app-laundry:assembleDebug` $\rightarrow$ **BUILD SUCCESSFUL** (sukses tanpa galat).
- **Lokasi Berkas APK Siap Pasang:**
  - Cafe: `app-cafe/build/outputs/apk/debug/app-cafe-debug.apk`
  - Laundry: `app-laundry/build/outputs/apk/debug/app-laundry-debug.apk`

---

## 4. Panduan untuk Sesi Baru (Instruksi Pengujian & Operasional)

1. **Deploy APK ke Tablet Fisik (Redmi Pad 2):**
   ```powershell
   adb install -r app-cafe\build\outputs\apk\debug\app-cafe-debug.apk
   adb install -r app-laundry\build\outputs\apk\debug\app-laundry-debug.apk
   ```
2. **Pemeriksaan Status Awal:**
   - Buka aplikasi `a3coffeeshop` dan `athialoundry`.
   - Pastikan perangkat terdaftar berstatus **ACTIVE**.
   - Login kasir dengan PIN staf.
   - Amati TopBar: Indikator Sinkron wajib berwarna **Hijau ("Tersinkron")** dan antrean outbox bernilai 0.
3. **Eksekusi Pengujian Terakhir: Uji Offline 24 Jam (F43):**
   - Ikuti instruksi pada `docs/golive/PROTOKOL-UJI-OFFLINE-24JAM.md`.
   - Aktifkan Mode Pesawat (Airplane Mode) pada tablet dan jalankan operasional penjualan selama 24 jam.
   - Sambungkan kembali ke internet pada T+24 jam dan pastikan seluruh transaksi outbox terkirim (ACCEPTED) ke live server tanpa ada yang masuk antrean bermasalah.
