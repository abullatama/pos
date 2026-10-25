# HANDOFF: Pemisahan Kas Keluar, Eliminasi Triplikasi Admin, & Penyatuan State Shift (SSOT)

## 1. Tujuan Sesi yang Telah Selesai
Menuntaskan **penyempurnaan arsitektur antarmuka & alur shift** berdasarkan temuan pengujian langsung pada tablet Redmi Pad 2:

1. **Pemisahan Kas Keluar Operasional (S12) Menjadi Menu & Halaman Mandiri:**
   - Kas keluar operasional (*petty cash* laci kasir—seperti es batu, gas, kemasan) dipisahkan dari halaman Shift.
   - Menambahkan item navigasi mandiri **"Kas Keluar"** (`Icons.Outlined.Payments`) pada Left Navigation Rail di kedua aplikasi (`app-cafe` dan `app-laundry`).
   - Halaman `CashOutScreen` berdiri sendiri dengan keypad 3x4 taktil, tombol preset cepat (+10k, +20k, +50k, +100k), chip kategori, foto nota kamera, proteksi PIN Manager `4444` untuk nominal $>$ Rp 50.000, dan cetak bukti kas keluar 58 mm.
   - Tombol kas keluar pada `ActiveShiftCard` dihilangkan agar halaman Shift murni dan fokus pada pembukaan & penutupan shift.

2. **Eliminasi Triplikasi Menu Admin & Penghapusan Menu "Lainnya":**
   - Menghapus menu ambigu `"Lainnya"` dari daftar menu tengah Left Navigation Rail di kedua aplikasi.
   - Menghilangkan kapsul tombol Admin dari TopBar di `AppShell.kt`, mengembalikan TopBar murni sebagai bilah telemetri & status (Logo, Tanggal, Kasir/Shift, Sync status, Printer status).
   - Menetapkan **satu-satunya akses Admin** di posisi standar emas industri POS internasional: tombol ikon gerigi di **dasar kiri bawah Left Navigation Rail** (`A3_NAV_RAIL_ADMIN_TAG`).

3. **Penyatuan Sumber Data Shift (Single Source of Truth / SSOT via Hoisting):**
   - Mengatasi *dual source of truth* di mana `MainActivity` sebelumnya membuat instance query Room terpisah yang lambat terbarui dibanding `ShiftViewModel` di dalam NavHost.
   - Meng-*hoist* `ShiftViewModel` ke tingkat `MainActivity` dan meneruskannya ke `CafeNavHost` serta `LaundryNavHost`.
   - **Hasil:** TopBar dan seluruh menu navigasi membaca `StateFlow` yang sama secara *real-time*. Begitu kasir mengetuk *"Buka Shift Sekarang"*, TopBar seketika terbarui menampilkan: `Nama Kasir · Shift Aktif · Kas Rp 200.000` dan seluruh tombol navigasi seketika aktif tanpa delay.

4. **Gerbang Status Menu Navigasi (`enabled = false` Sebelum Buka Shift):**
   - Memperluas `NavItem` di `:core:ui` dengan properti `enabled: Boolean = true`.
   - Pada komponen `A3NavigationRail`, item dengan `enabled = false` otomatis tampil redup (alpha 30%), berlatar transparan, dan tidak dapat diklik (`clickable(enabled = false)`).
   - Saat shift belum dibuka, menu transaksi (Kasir, Pesanan, Menu Habis, Kas Keluar pada Cafe; Beranda, Order Baru, Cucian, Kas Keluar pada Laundry) otomatis berstatus `enabled = false`, membimbing kasir langsung ke alur Buka Shift (S04).

5. **Penjagaan Spatial Continuity (Sidebar Navigasi Tetap Tampil di Layar Shift):**
   - Memperbaiki aturan `isFocusFlowActive`: mode layar penuh fokus yang menyembunyikan bilah navigasi kiri dibatasi **HANYA** saat alur pembayaran penuh (`PEMBAYARAN`) aktif.
   - Bilah navigasi samping kiri (`A3NavigationRail`) **tetap terlihat kokoh** di layar Shift, sehingga kasir tidak lagi kehilangan orientasi navigasi.

6. **Singleton AppDatabase:**
   - Memastikan `AppDatabase.build(context)` menggunakan pola singleton thread-safe (`@Volatile private var INSTANCE`) sehingga seluruh modul dan worker berbagi koneksi dan `InvalidationTracker` Room yang sama.

---

## 2. Rincian Modul & Kode yang Diubah

### A. Modul `:core:ui`
- **`FloatingBottomBar.kt` & `A3NavigationRail.kt`:**
  * Properti `NavItem` memiliki `enabled: Boolean = true`.
  * Visualisasi item nonaktif (alpha 30%, tanpa background aktif, interaksi klik dimatikan).
- **`AppShell.kt`:**
  * Menghapus pemanggilan `onAdminClick` pada `TopBar` (diserahkan sepenuhnya ke jangkar bawah `A3NavigationRail`).

### B. Modul `:core:data`
- **`AppDatabase.kt`:**
  * Singleton thread-safe `INSTANCE` pada `AppDatabase.build(context)`.

### C. Modul `app-cafe`
- **`CafeNavHost.kt`:**
  * Menambahkan rute `CafeDestinations.KAS_KELUAR` dan `CafeDestinations.ADMIN` (menggantikan `LAINNYA`).
  * `CafeNavHost` menerima `shiftViewModel` yang di-hoist dari `MainActivity`.
  * Membersihkan `ActiveShiftCard` dari tombol kas keluar.
- **`MainActivity.kt`:**
  * `ShiftViewModel` di-hoist di tingkat Activity sebagai SSOT.
  * 5 menu Left Rail: Kasir, Pesanan, Menu Habis, Kas Keluar, Shift.
  * Status `enabled` menu transaksi dikaitkan langsung ke `isShiftActive`.
  * `isFocusFlowActive` disederhanakan: hanya aktif pada `PEMBAYARAN`.

### D. Modul `app-laundry`
- **`LaundryNavHost.kt`:**
  * Menambahkan rute `LaundryDestinations.KAS_KELUAR`.
  * `LaundryNavHost` menerima `shiftViewModel` yang di-hoist dari `MainActivity`.
  * Membersihkan `ActiveShiftCard` dari tombol kas keluar.
- **`MainActivity.kt`:**
  * `ShiftViewModel` di-hoist di tingkat Activity sebagai SSOT.
  * 5 menu Left Rail: Beranda, Order Baru, Cucian, Kas Keluar, Shift.
  * Status `enabled` menu transaksi dikaitkan langsung ke `isShiftActive`.
  * `isFocusFlowActive` hanya aktif pada `PEMBAYARAN`.

---

## 3. Status Terkini Proyek

- **Milestone M0 (Spike & Hardware):** 5 dari 5 tuntas (100%)
- **Milestone M1 (Fondasi & Multi-modul):** 9 dari 9 tuntas (100%)
- **Milestone M2 (Transaksi Cafe & Laundry):** 15 dari 15 tuntas (100%)
- **Milestone M3 (Shift, Kas, & Kios Admin):** 11 dari 11 tuntas (100%)
- **Milestone M4 (Laporan, WA, & Go-live):** 0 dari 4 (0%) — **Milestone Aktif**
- **Total Fitur Selesai:** **40 dari 44 fitur tuntas (90,9%)**.
- **Kondisi Pengujian:** Seluruh unit test proyek (`:core:ui:test`, `:feature:*:test`, `:core:data:test`, dll) lulus 100%. Kompilasi kedua APK debug berhasil tuntas dan terpasang di perangkat fisik Redmi Pad 2.
- **Kondisi Layar Tablet Saat Ini:** Berada dalam status **Pristine Reset (AA Coffeeshop)**, menampilkan form *Buka Shift Baru* (Kasir Budi Santoso, Kas Awal Rp 200.000) siap untuk uji transaksi.

---

## 4. Panduan untuk Sesi Baru (Memulai Milestone M4)

Ketika memulai sesi berikutnya, lanjutkan ke **Milestone M4** dengan urutan fitur:
1. **F42 (Tombol Kirim via WA Manual dan Status Notifikasi Laundry) — *Prioritas 1*:**
   * Menampilkan chip status notifikasi dari server (`none`, `queued`, `sent`, `failed`) via polling `GET /v1/orders/{id}/notification-status`.
   * Tombol hijau taktil `#25D366` **"Kirim via WA"** untuk membuka `Intent` WhatsApp resmi dengan teks template transaksi (nama pelanggan, nota, total/sisa, estimasi).
2. **F41 (Heartbeat dan Crashlytics) — *Prioritas 2*:**
   * Telemetri periodik perangkat (`POST /v1/devices/heartbeat`) via foreground coroutine & WorkManager cadangan.
   * Crash logger lokal aman tanpa data sensitif.
3. **F43 (Uji Offline 24 Jam):**
   * Verifikasi hardware di Redmi Pad 2 (`needs_device: true`).
4. **F44 (Paket Go-live):**
   * SOP darurat kasir dan template nota kertas cadangan.

---

## 5. Kredensial & Pintasan Pengujian Tablet
- **Pintasan Demo Awal:** Tombol *"Masuk Mode Demo / Lewati"* di layar registrasi perangkat.
- **PIN Kasir (Shift & Transaksi):** `1234`
- **PIN Manager (Otorisasi Khusus):** `4444` *(Diskon manual, void/refund, kas keluar > 50rb, cuci ulang, ambil belum lunas, tutup paksa)*.
- **PIN Owner (Menu Admin S15):** `9999` *(Kios, Wi-Fi, uji cetak struk)*.
