# HANDOFF: Sesi Optimasi UI/UX & Performa POS Cafe Selesai

## 1. Tujuan Tugas Saat Ini
Menyelesaikan investigasi dan perbaikan komprehensif atas kendala UI/UX dan performa navigasi pada aplikasi POS AA Coffeeshop (Redmi Pad 2 landscape 11"), memastikan seluruh alur kasir instan, bebas lag, dan berstandar emas industri sebelum melangkah ke Track Laundry Milestone M2.

## 2. Yang Sudah Selesai (Termasuk Hasil Investigasi & Perbaikan UI/UX Terakhir)
- **Non-blocking Hardware Execution (ADR D-19):** Menghilangkan delay 3–10 detik saat menekan "Langsung Bayar di Kasir" atau menggeser "Kirim ke Dapur". Pembuatan pesanan lokal di Room (< 2ms) langsung mengembalikan hasil seketika dan memicu navigasi 0 detik; pencetakan tiket dapur didelegasikan ke background worker asynchronous (`ioDispatcher`).
- **Verifikasi Koneksi Fisik Printer Nyata (checkConnection):** Menghilangkan status "Printer Siap" palsu. `DefaultPrinterService` kini memverifikasi socket RFCOMM nyata di background. Jika printer mati atau tidak aktif, status akurat menampilkan "Printer Putus". Badge printer di `TopBar` kini interaktif (dapat diketuk kasir untuk cek ulang seketika setelah menyalakan printer) dan menampilkan status "Memeriksa...".
- **Format Waktu Meja Terbuka Human-Friendly:** Mengubah tampilan durasi meja di `CafeOpenOrdersScreen` dari menit mentah (230 mnt) menjadi format jam dan menit yang ramah kasir (misal: "3 jam 50 menit", "1 jam", "Baru saja" jika < 1 mnt) baik di kartu ringkasan maupun di panel detail.
- **Standardisasi Navigasi SingleTop & Eliminasi Tombol Macet:** Memastikan seluruh perpindahan layar top-level (`KASIR`, `PESANAN`, `HABIS`, `SHIFT`, `LAINNYA`) dan aksi cepat (Kirim ke Dapur, Pesanan Baru) memakai kontrak `popUpTo(findStartDestination().id) { saveState = true }`, `launchSingleTop = true`, dan `restoreState = true`. Menghilangkan duplikasi tumpukan rute (`[KASIR, PESANAN, KASIR]`) yang sebelumnya memblokir klik Navigation Rail.
- **Standar Aksesibilitas Touch Target 44dp:** Tombol "Pesanan Baru" dan Segmented Tab di `CafeOpenOrdersScreen` diperbesar menjadi 44 dp (dari sebelumnya 29 dp) dengan padding 16 dp dan elevasi taktil sehingga sentuhan jari tablet tidak pernah meleset; teks anti-terpotong dengan pemisahan baris judul dan tab.
- **Snappy Transitions (100ms in / 80ms out):** Transisi navigasi antar menu utama pada `CafeNavHost` dan `LaundryNavHost` diganti dari crossfade default Compose (~700ms) ke Fast Fade (100ms / 80ms), mengeliminasi beban *alpha blending* berat pada GPU Snapdragon 680 / Adreno 610.
- **Eliminasi Keyboard Jitter (adjustPan):** `windowSoftInputMode="adjustPan"` di `AndroidManifest.xml` kedua app mencegah keyboard virtual menciutkan viewport split-screen 65/35 atau mengangkat panel struk secara paksa.
- **Optimasi Recomposition & Banner Memoization:** `allProducts` dipisahkan dari `displayedProducts`; pemetaan ikon dan statistik kategori di-memoize dengan `remember(allProducts, categories)` sehingga perpindahan kategori menu instan tanpa flicker atau frame-drop.
- **Tab Riwayat Selesai (Shift Ini):** Layar Pesanan (`CafeOpenOrdersScreen`) kini memiliki Segmented Tab `[Meja Terbuka]` dan `[Riwayat Selesai]` lengkap dengan ringkasan omzet shift ini dan tombol Cetak Ulang Salinan (`*** SALINAN ***` PRT-05).
- **Semua Test Suite & Build Hijau:** Seluruh unit test di `core:printer`, `feature:cafe` (18 test), `feature:payment`, `core:ui`, serta build `:app-cafe:assembleDebug` dan `:app-laundry:assembleDebug` berhasil tuntas 100%.

## 3. Yang Belum Selesai / Masalah Terbuka
- Track Laundry Milestone M2 (F19–F24) siap dikerjakan: pencarian/pembuatan pelanggan baru (`customer.created` & Dialog L02), order laundry per kg dan per pcs (L01/L03, test vector L1–L3), cetak tag QR 58 mm dan pemindaian status CameraX (L07), pengambilan sebagian (`laundry_pickups`), dan cuci ulang PIN manager.
- Cetak struk belum mendukung antrean cetak otomatis saat printer putus di tengah jalan (F27).
- Penerimaan flag server pada outbox sync (F28).

## 4. Keputusan Teknis Penting
- **ADR D-18 (Quiet Luxury & Left Nav Rail):** Transisi tablet landscape 11" ke Left Nav Rail 68 dp berlatar gelap `#141414`, item aktif putih murni, kanvas krem `#F4F2E6`, dan aksen Deep Forest Green (`#0A3D24`).
- **ADR D-19 (Non-blocking Hardware & SingleTop Navigation):** Operasi printer eksternal wajib asynchronous; navigasi top-level wajib SingleTop; transisi snappy 100ms; touch target minimal 44dp; windowSoftInputMode adjustPan.
- **Pemisahan Dapur & Kasir:** Tiket dapur termal 58 mm tidak memuat harga sama sekali (PRT-03) dan hanya mencetak item baru (CAF-07).
- **Auto-Connect Bluetooth SPP:** Mengaitkan printer terpasang (`RPP02N`) secara otomatis begitu izin runtime `BLUETOOTH_CONNECT` diberikan.

## 5. Langkah Berikutnya (Berurutan untuk Sesi Baru)
1. **Memulai Track Laundry M2 (F19):** Tambahkan `CustomerEntity` dan `CustomerDao` di `:core:data`, buat Dialog L02 Cari/Buat Pelanggan dengan event `customer.created` di `:feature:laundry`.
2. **Implementasi F20 (Order Laundry kg & pcs):** Bangun form order L01/L03 dengan perhitungan Test Vector L1–L3 di `LaundryOrderViewModel`.
3. **Implementasi F21 (Tag QR & Scan Status):** Bangun `LaundryTagFormatter` 58 mm di `:core:printer` dan hubungkan `LaundryScanScreen` (CameraX L07).
4. **Implementasi F22–F24:** Pengambilan sebagian (`laundry_pickups`), cuci ulang PIN manager, dan proteksi ambil belum lunas.

## 6. File yang Perlu Dibaca Pertama
- `feature_list.json` (baris 184–240 untuk spesifikasi F19–F24)
- `docs/spec/04-kebutuhan-fungsional-b-transaksi-cafe-da.md` (bagian transaksi laundry)
- `docs/ui/DESIGN-laundry.md` (sistem desain Athia Laundry House)
- `contracts/test-vectors.json` (test vectors L1–L4)
- `docs/decisions.md` (khususnya D-18 dan D-19)
