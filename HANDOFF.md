# HANDOFF: Sesi Tuntas Milestone M3 Penuh (F39 & F40 Selesai)

## 1. Tujuan Sesi yang Telah Selesai
Menuntaskan **2 fitur penutup Milestone M3** sehingga **Milestone M3 resmi tuntas 100% (11 dari 11 fitur lulus)**:
1. **F40 (Migrasi Room Diuji dengan Data Lama):**
   - Penegakan kriteria penerimaan: *"Database versi lama berisi outbox bermigrasi tanpa kehilangan data"*.
   - Uji migrasi otomatis berurutan v1 $\rightarrow$ v2 $\rightarrow$ v3 $\rightarrow$ v4 $\rightarrow$ v5 $\rightarrow$ v6 di `:core:data` (`RoomMigrationTest.kt`).
   - Menyisipkan data legacy nyata pada skema v1: pesanan lunas `C1-0001`, baris item pesanan, pelanggan legacy `Ibu Nurhayati`, menu, dan outbox menunggu sinkron (`PENDING`).
   - Membuktikan 100% data legacy utuh setelah migrasi v6, kolom baru `reasonCode` dan `flagsJson` default NULL aman, seluruh tabel baru (`shifts`, `payments`, `corrections`, `cash_outs`) beserta indeksnya terbentuk dan dapat menerima transaksi baru, serta `OutboxDao.getUnsyncedCount()` membaca data akurat.
2. **F39 (Mode Kios dan Pembaruan App Sesuai Aturan OPS-03):**
   - Penegakan kriteria penerimaan: *"Update tidak berjalan saat shift aktif atau outbox tidak kosong"*.
   - `UpdatePreconditionValidator` di `:feature:admin`:
     * Syarat 1: Tidak ada shift aktif (`ShiftDao.getActiveShift() == null`). Jika ada shift aktif, update diblokir: *"Shift aktif kasir '[Nama]' masih berjalan. Tutup shift terlebih dahulu sebelum memperbarui aplikasi."*
     * Syarat 2: Antrean outbox harus bersih (`OutboxDao.getUnsyncedCount() == 0`). Jika ada data belum tersinkron, update diblokir: *"Terdapat X transaksi/event di outbox yang belum tersinkron ke server. Pastikan seluruh data tersinkron terlebih dahulu."*
     * Jika kedua syarat terpenuhi: status `UpdateEligibility.Allowed` (Aman untuk diperbarui).
   - `KioskManager` di `:feature:admin`:
     * Mengelola aktivasi dan pelepasan mode Lock Task (`Activity.startLockTask()` dan `Activity.stopLockTask()`) untuk memblokir navigasi Back/Home/Recents kasir (SET-04, SET-05).
     * Pintasan ke pengaturan Wi-Fi Android (`Settings.ACTION_WIFI_SETTINGS`).
   - `ApkInstallerHelper` di `:feature:admin`:
     * Penanganan sesi instalasi mandiri via `PackageInstaller.Session` untuk jalur distribusi APK privat tanpa Google Play.
   - Layar S15 Menu Admin Tablet (`AdminMenuScreen`, `AdminViewModel`, `AdminViewModelFactory`):
     * Proteksi PIN Owner (default `"9999"`, lockout salah 3x terkunci 5 menit).
     * Panel 1: Pengaturan Hardware & Jaringan (Wi-Fi, status printer & tombol Uji Cetak struk 58 mm, status antrean outbox).
     * Panel 2: Kontrol Mode Kios (Status Lock Task, tombol Kunci Layar Kios & Lepaskan Kios).
     * Panel 3: Evaluasi Kepatuhan Pembaruan APK OPS-03 secara real-time dan pemilih berkas APK pembaruan.
   - Navigasi & Integrasi Antarmuka:
     * `app-cafe`: Rute `CafeDestinations.LAINNYA` dan tombol Admin di TopBar/Rail membuka `AdminMenuScreen`.
     * `app-laundry`: Rute `LaundryDestinations.ADMIN` dan tombol Admin di TopBar/Rail membuka `AdminMenuScreen`.

Progress proyek saat ini: **Milestone M0 (5/5, 100%), Milestone M1 (9/9, 100%), Milestone M2 (15/15, 100%), Milestone M3 (11/11, 100%)**.
Total fitur tuntas: **40 dari 44 fitur tuntas (90,9%)**. Seluruh unit test lulus 100% dan kedua APK debug sukses dibangun.

---

## 2. Yang Sudah Selesai di Sesi Ini

### A. Modul `:core:data` (F40 Migrasi Room & Query Outbox)
- **`OutboxDao.kt`:**
  - Penambahan query `getUnsyncedCount(): Int` dan `observeUnsyncedCount(): Flow<Int>` (`SELECT COUNT(*) FROM outbox WHERE status != 'SENT'`).
- **`RoomMigrationTest.kt`:**
  - Pengujian migrasi otomatis end-to-end v1 $\rightarrow$ v6 oleh Room Engine dengan data riil legacy outbox & transaksi.
  - Pengujian migrasi inkremental per versi memvalidasi penambahan kolom `reasonCode`/`flagsJson`, tabel `shifts`, `payments`, `corrections`, `cash_outs`, dan seluruh indeks pendukungnya.

### B. Modul `:feature:admin` (F39 Kiosk Mode & App Update OPS-03)
- **`UpdatePreconditionValidator.kt`:**
  - Evaluasi kelayakan update mematuhi aturan OPS-03 (`UpdateEligibility.Allowed` vs `UpdateEligibility.Blocked`).
- **`KioskManager.kt`:**
  - Pengendali `startLockTask()`, `stopLockTask()`, `isLockTaskActive()`, dan `openWifiSettings()`.
- **`ApkInstallerHelper.kt`:**
  - Instalasi paket APK privat mandiri via `PackageInstaller.Session`.
- **`AdminViewModel.kt` & `AdminViewModelFactory.kt`:**
  - Manajemen state S15: verifikasi PIN Owner, lockout proteksi, observasi reaktif shift aktif dan outbox, pengujian koneksi dan uji cetak printer 58 mm, evaluasi kelayakan update, serta eksekusi pemasangan APK.
- **`AdminMenuScreen.kt` (Layar S15):**
  - Layout tablet landscape 11" pola split 2-kolom mengikuti prinsip Quiet Luxury dan antislop UI (bebas emoji, touch target $\ge 48\text{ dp}$, kontras AA tinggi).
  - Gerbang Otorisasi PIN Owner dengan 4-digit indicator dots dan `A3Keypad` taktil 3x4.
  - Dashboard Admin: status perangkat, koneksi Wi-Fi, uji cetak printer, kontrol mode kios, indikator kepatuhan OPS-03, dan aksi pembaruan APK.
- **`UpdatePreconditionValidatorTest.kt`:**
  - 4 kasus uji unit test lulus 100% (bebas shift & outbox kosong, ada shift aktif, ada outbox pending, dan kombinasi keduanya).

### C. Modul Cangkang (`app-cafe` & `app-laundry`)
- **`CafeNavHost.kt`:**
  - Integrasi `AdminViewModel` dan penggantian placeholder `CafeDestinations.LAINNYA` dengan `AdminMenuScreen`.
- **`LaundryNavHost.kt` & `MainActivity.kt`:**
  - Penambahan rute `LaundryDestinations.ADMIN` terhubung ke `AdminMenuScreen` via klik tombol Admin di TopBar dan Rail.

---

## 3. Yang Belum Selesai (Langkah untuk Sesi Baru: Memulai Milestone Terakhir M4)

Sisa 4 fitur penutup proyek pada **Milestone M4 (Go-live, stabilisasi, dan operasional)**:
1. **F41 (Heartbeat dan Crashlytics):**
   - Telemetri status perangkat berkala (`POST /v1/devices/me/heartbeat`) dan integrasi pelaporan crash/galat tanpa data sensitif pelanggan (OPS-01, OPS-02).
2. **F42 (Tombol Kirim via WA Manual dan Status Notifikasi):**
   - Fitur spesifik laundry: tombol kirim WA manual dan tampilan status notifikasi server (`none`, `queued`, `sent`, `failed`) pada kartu rincian cucian (L06 / LDR-09).
3. **F43 (Uji Offline 24 Jam):**
   - Pengujian operasional offline 24 jam penuh: memastikan integritas transaksi lokal, rekonsiliasi kas, dan sinkronisasi tuntas saat koneksi pulih (`needs_device: true`).
4. **F44 (Paket Go-live: Nota Kertas Cadangan dan SOP Darurat):**
   - Dokumen SOP darurat satu halaman saat tablet/printer/internet mati dan template buku nota manual bernomor.

---

## 4. Keputusan Teknis Penting

- **Aturan Pembaruan OPS-03:**
  - Pembaruan aplikasi mandiri WAJIB ditolak bila ada kasir yang belum menutup shift atau ada data transaksi lokal yang belum terkirim ke server (`status != 'SENT'`). Hal ini mencegah terputusnya transaksi atau inkonsistensi saat APK dimuat ulang oleh sistem OS.
- **Isolasi Mode Kios:**
  - Mode Kios (Lock Task) diakses dan dikendalikan melalui PIN Owner (`"9999"`), bukan PIN kasir atau manager, memastikan staf kasir toko tetap terkunci di dalam antarmuka POS.
- **Migrasi Maju Saja (Forward Migration):**
  - Database Room versi 6 kompatibel mundur secara penuh dengan database versi 1 hingga 5, terbukti lolos pengujian tanpa kehilangan 1 baris pun data transaksi lama.

---

## 5. File yang Perlu Dibaca Pertama Kali di Sesi Baru
- `HANDOFF.md` (dokumen ini)
- `feature_list.json` (baris 404–445 untuk Milestone M4)
- `docs/milestones/M4-laporan-golive.md`
- `docs/spec/13-whatsapp-lewat-fonnte-dan-penjadwal.md`
- `docs/spec/16-infrastruktur-deployment-backup-dan-pema.md`
- `docs/spec/17-skenario-uji-dan-kriteria-lulus.md`

---

## 6. Panduan Pengujian Langsung Hardware (Tablet & Printer)

Untuk pengujian fisik di perangkat Redmi Pad 2 dan printer iWare C58BT:
- **Perintah Pasang APK:**
  ```bash
  ./gradlew :app-cafe:installDebug
  ./gradlew :app-laundry:installDebug
  ```
- **Kredensial & PIN Sistem:**
  * **Pintasan Demo:** Tombol *"Masuk Mode Demo / Lewati"* di layar pendaftaran perangkat awal.
  * **PIN Kasir (Shift & Transaksi):** `1234`
  * **PIN Manager (Otorisasi Khusus):** `4444` (Diskon manual, void/refund, kas keluar > 50rb, cuci ulang, ambil belum lunas, tutup paksa shift. Proteksi: salah 3x lockout 5 menit).
  * **PIN Owner (Menu Admin S15):** `9999` (Uji cetak ESC/POS 58 mm, buka Wi-Fi, kunci/lepas mode kios LockTask, pasang APK update. Proteksi: salah 3x lockout 5 menit).

