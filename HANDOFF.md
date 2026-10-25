# HANDOFF: Penyempurnaan TopBar POS Cafe (Eliminasi Data Kas & Dot Sesuai Best Practice)

## 1. Ringkasan Pekerjaan Sesi Ini

Sesi ini menyelesaikan optimalisasi antarmuka bilah atas (**TopBar**) pada aplikasi Cafe sesuai kaidah standar industri POS (*Point of Sale Best Practice*):

1. **Eliminasi Angka Kas dari TopBar (`MainActivity.kt`):**
   - Menghapus tampilan saldo kas laci dari topbar saat shift kasir aktif.
   - **Kepatuhan Terhadap Aturan Keamanan Kasir POS (Hitung Kas Buta — TUP-02):** Saldo kas laci yang ada di laci fisik sengaja disembunyikan dari kasir sepanjang shift berjalan guna mencegah kecurangan (*fraud*) dan menjamin kejujuran saat rekonsiliasi tutup shift (*blind cash count*).
   - Menghilangkan kebingungan kasir karena angka sebelumnya hanya merefleksikan kas awal (*openingCash*) dan tidak bergerak saat ada transaksi tunai.

2. **Penghapusan Karakter Dot Pemisah (`TopBar.kt`):**
   - Menghapus karakter dot pemisah (`·`) pada kapsul profil kasir.
   - Mengganti teks gabungan dengan komponen `Text` terpisah yang memanfaatkan *spacing* alami Compose (`Arrangement.spacedBy(8.dp)`).

3. **Format Antarmuka TopBar Bersih & Minimalis:**
   - **Shift Aktif:** Menampilkan identitas kasir yang bertugas secara elegan dan ringkas: `[Icon Person] Kasir` (atau nama kasir, misal `Budi Santoso`).
   - **Belum Buka Shift:** Menampilkan status: `[Icon Person] Kasir   Belum Buka Shift` tanpa karakter dot.

---

## 2. Rincian Modul & Kode yang Diubah

### A. Modul `app-cafe`
- **`MainActivity.kt`:** Menghapus import `formatRupiah` yang tidak lagi digunakan dan mengubah `shiftInfo` menjadi `if (activeShift != null) null else "Belum Buka Shift"`.

### B. Modul `:core:ui`
- **`TopBar.kt`:** Menghilangkan karakter dot `·`, menghapus ketergantungan `buildAnnotatedString` / `SpanStyle` pada kapsul profil kasir, dan memisahkan rendering `staffName` serta `shiftInfo` dengan layout Compose yang bersih.
- **`AppShellTest.kt`:** Memperbarui assertion pengujian unit untuk memverifikasi `Budi Santoso` dan `Shift 1` sebagai node teks terpisah bebas dot.

---

## 3. Status Terkini Proyek

- **Milestone M0 (Spike & Hardware):** 5 dari 5 tuntas (100%)
- **Milestone M1 (Fondasi & Multi-modul):** 9 dari 9 tuntas (100%)
- **Milestone M2 (Transaksi Cafe & Laundry):** 15 dari 15 tuntas (100%)
- **Milestone M3 (Shift, Kas, & Kios Admin):** 11 dari 11 tuntas (100%)
- **Milestone M4 (Laporan, WA, & Go-live):** 0 dari 4 (0%) — **Milestone Aktif**
- **Total Fitur Selesai:** **40 dari 44 fitur tuntas (90,9%)**.
- **Kondisi Pengujian:** Seluruh unit test proyek (`:core:ui:test`, `:feature:cafe:test`, `:feature:shift:test`) lulus 100%. Kompilasi `:app-cafe:assembleDebug` berhasil tuntas.

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
