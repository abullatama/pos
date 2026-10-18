# HANDOFF: Sesi Implementasi Milestone M2 Selesai Penuh (F15–F29) & Logo Laundry

## 1. Tujuan Sesi yang Telah Selesai
Menyelesaikan seluruh alur fungsional, operasional, arsitektur sinkronisasi, antrean cetak offline, dan penyempurnaan UI untuk Milestone M2 (F15 sampai F29). Milestone M2 resmi tuntas 100% (15 dari 15 fitur lolos). Selain itu, logo resmi **Athia Laundry House** (`loundry.jpeg`) telah terintegrasi penuh ke dalam sistem desain tema tablet.

---

## 2. Yang Sudah Selesai (Fitur M2 Tuntas 100%)

- **F15: Katalog Menu, Varian, & Add-on (C01 & C02):**
  - Katalog kasir POS responsif landscape 11", varian single/multi-choice, kalkulasi realtime, dan struk keranjang.
- **F16: Diskon Preset, Service Charge, Pajak, & Pembulatan (Dialog C06 & S14):**
  - Kalkulasi presisi lolos Test Vector C1–C3 tanpa selisih 1 rupiah. Proteksi PIN Manager untuk diskon manual.
- **F17: Dine-in, Takeaway, Pindah Meja (C05), & Tanda Habis (C07):**
  - Event outbox atomik `order.table_moved` dan `menu.sold_out_changed`. Redup otomatis katalog menu habis.
- **F18: Kirim ke Dapur & Tiket Dapur Tanpa Harga (PRT-03, C04/C05):**
  - Format ESC/POS tiket dapur hanya memuat item baru tanpa harga sama sekali. Geser A3SwipeButton untuk kirim.
- **F19: Cari atau Buat Pelanggan Laundry (L02):**
  - Normalisasi nomor telepon 08 (`PhoneNumberUtil`), event outbox `customer.created` bebas shift.
- **F20: Order Laundry per kg & pcs dengan Express (L03, Test Vector L1–L3):**
  - Stepper taktil berat & pcs, saklar express, 3 opsi pembayaran (`Bayar saat Ambil`, `Lunas di Muka`, `DP`).
- **F21: Cetak Tag QR 58 mm & Scan Status Satu Arah (L07):**
  - Tag roll 58 mm tanpa harga (PRT-03), scan kamera CameraX + ZXing HybridBinarizer, tombol transisi status.
- **F22: Pengambilan Sebagian Per Baris (`laundry_pickups`, L08):**
  - Entitas `LaundryPickupEntity`, transisi ke `picked_up` hanya jika seluruh baris `is_final = true`.
- **F23: Cuci Ulang Komplain Pelanggan (L09):**
  - Status Siap kembali ke Diproses tanpa biaya baru, alasan audit tetap, dan PIN Manager.
- **F24: Proteksi Penyerahan Cucian Belum Lunas (Dialog S14):**
  - Penyerahan dengan sisa tagihan > 0 wajib PIN Manager; lockout salah PIN 3x terkunci 5 menit (COR-07).
- **F25: Pembayaran Tunai & Kembalian (S06):**
  - Tombol nominal cepat, kembalian akurat (CashCalculator, Test Vector K2), struk pelanggan 58 mm.
- **F26: Pembayaran QRIS Statis Offline (S07):**
  - Panduan merchant/soundbox offline tanpa payment gateway (PAY-03, PAY-04).
- **F27: Antrean Cetak Otomatis saat Printer Putus & Cetak Ulang SALINAN (PRT-02, PRT-05):**
  - `PrintQueueManager` di `:core:printer`: antrean FIFO otomatis saat printer putus/mati (PRT-01, PRT-02).
  - Saat printer tersambung kembali (`Connected`), antrean `PENDING` otomatis dicetak berurutan (*auto-drain*).
  - Format cetak ulang (`CustomerReceiptFormatter`, `KitchenTicketFormatter`, `LaundryTagFormatter`) menyematkan watermark tegas `*** SALINAN ***` dan catatan audit kasir/waktu (PRT-05), tetap patuh PRT-03 tanpa harga pada tiket & tag.
  - `PrinterStatusBadge` di TopBar menampilkan indikator jumlah antrean jika printer putus (misal: *Printer Putus (2 antre)*).
- **F28: Sinkron Menerima Flag Server tanpa Menganggap Gagal:**
  - Respon server `ACCEPTED` dengan `flags[]` (karantina: `TOTAL_MISMATCH`, `SHIFT_RANGE`, dll) ditandai selesai (`SENT`), menyimpan `flagsJson` ke Room, dan tidak pernah masuk ke antrean bermasalah.
- **F29: Antrean Bermasalah REJECTED dengan Alasan & Retry:**
  - Bundel `REJECTED` non-retryable masuk status `FAILED` membawa `reasonCode` dan `lastError` tanpa menghapus payload event (append-only & aman dari retensi pembersihan).
  - `SyncProblemQueueManager` dan `OutboxDao` menyediakan pemantauan reaktif (`observeFailedCount`, `observeProblemBundles`) serta aksi coba lagi (`retryFailedBundle`, `retryAllProblems`, `retryAndSync`) yang mereset status ke `PENDING`.
- **Pembaruan Logo Laundry Resmi di TopBar (`loundry.jpeg`):**
  - Berkas sumber `loundry.jpeg` dikonversi dan dipangkas bersih menjadi `ic_laundry_logo.png` (32-bit ARGB transparan) di `:core:ui`.
  - Abstraksi multi-tema via `LocalAppLogo` di `AppDesignTokens.kt`: `CafeTheme` menyuplai logo A3, `LaundryTheme` menyuplai logo Athia Laundry. `TopBar` mengadopsi `A3Theme.logoRes` dinamis.

---

## 3. Yang Belum Selesai (Langkah untuk Sesi Baru: Milestone M3)

Milestone M2 telah tuntas 100%. Langkah pengembangan selanjutnya beralih ke **Milestone M3 (Shift Kasir & Pengendalian Kas)**:
1. **F30 (Buka Shift dengan Kas Awal per Pecahan):**
   - Layar S02 Buka Shift: input pecahan uang kertas & logam, validasi kas awal bawaan (Rp200.000, OQ-06), alasan jika berbeda dari standar.
2. **F31 (Tutup Shift: Hitung Kas Buta, Selisih, Alasan, Laporan Z):**
   - Layar S10 Hitung Kas Fisik tanpa melihat total sistem, perhitungan selisih kas (Test Vector K1), cetak Laporan Z ESC/POS 58 mm.
3. **F32 (Catatan Kas saat Tutup Shift dengan Validasi Alokasi):**
   - Kas ditinggal di laci + kas disetor ke owner = kas dihitung fisik.
4. **F33 (Serah Terima Pesanan Terbuka ke Shift Berikutnya - Cafe):**
   - Pesanan meja yang belum lunas diserahkan ke shift baru; pembayaran dicatat di shift penerima.
5. **F34–F40:** Rekonsiliasi QRIS saat tutup shift (F34), void/refund tunai offline PIN Manager (F35), refund transaksi lama (F36), kas keluar + foto nota (F37), tutup paksa (F38), mode kios & update APK (F39), migrasi Room data lama (F40).

---

## 4. Keputusan Teknis Penting

- **Antrean Cetak Non-Blocking:** Kegagalan printer Bluetooth/USB tidak pernah membatalkan transaksi; cetakan ditampung di `PrintQueueManager` dan otomatis dicetak saat perangkat terhubung kembali.
- **Audit Cetak Ulang:** Seluruh salinan cetak (`CustomerReceiptFormatter`, `KitchenTicketFormatter`, `LaundryTagFormatter`) menyertakan label `*** SALINAN ***` dan metadata siapa yang mencetak serta waktunya.
- **Append-Only Sync Integrity:** Event bermasalah (`FAILED`) dan yang belum sinkron (`PENDING`) tidak pernah dihapus oleh retensi otomatis `deleteSentOlderThan`. Hanya event `SENT` berumur > 30 hari yang dibersihkan.
- **Multi-Brand Design System:** `TopBar` menggunakan `A3Theme.logoRes` melalui CompositionLocal, menghasilkan tampilan logo brand yang berbeda secara otomatis untuk Cafe dan Laundry tanpa if-else di komponen.

---

## 5. File yang Perlu Dibaca Pertama Kali di Sesi Baru
- `HANDOFF.md` (dokumen ini)
- `feature_list.json` (baris 295–404 untuk Milestone M3)
- `docs/milestones/M3-shift-kas.md`
- `docs/spec/06-kebutuhan-fungsional-d-tutup-shift-sync-.md`
