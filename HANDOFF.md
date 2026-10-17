# HANDOFF: Sesi Transaksi Cafe & Pembayaran Selesai

## 1. Tujuan Tugas Saat Ini
Mengimplementasikan transaksi POS cafe (F15–F18) dan alur pembayaran penuh fokus Tunai serta QRIS Statis (F25, F26) hingga verifikasi cetak fisik printer termal di tablet Redmi Pad 2.

## 2. Yang Sudah Selesai
- **F15 (Katalog POS & Modal Verifikasi):** `feature/cafe/src/main/kotlin/id/a3/feature/cafe/presentation/screen/CafePosScreen.kt`, `component/CafeVariantBottomSheet.kt`, `CafeMenuViewModel.kt`
- **F16 (Diskon C06 & PIN Manager):** `feature/cafe/src/main/kotlin/id/a3/feature/cafe/presentation/component/CafeDiscountDialog.kt`, `model/CafeDiscountModels.kt`
- **F17 (Pindah Meja C05 & Menu Habis C07):** `feature/cafe/src/main/kotlin/id/a3/feature/cafe/presentation/screen/CafeSoldOutScreen.kt`, `component/CafeMoveTableDialog.kt`
- **F18 (Kirim Dapur & Cetak Bebas Harga):** `core/printer/src/main/kotlin/id/a3/core/printer/format/KitchenTicketFormatter.kt`, `feature/cafe/src/main/kotlin/id/a3/feature/cafe/presentation/screen/CafeOpenOrdersScreen.kt`
- **F25 & F26 (Pembayaran Tunai, QRIS, & Struk Pelanggan):** `feature/payment/src/main/kotlin/id/a3/feature/payment/presentation/PaymentScreen.kt`, `PaymentViewModel.kt`, `repository/PaymentRepository.kt`, `core/printer/src/main/kotlin/id/a3/core/printer/format/CustomerReceiptFormatter.kt`
- **Transaksi Room & Outbox:** `core/data/src/main/kotlin/id/a3/core/data/repository/OrderTransactionManager.kt`, `dao/OrderDao.kt`, `dao/MasterDataDao.kt`
- **Koneksi Printer Bluetooth RPP02N/iWare:** `core/printer/src/main/kotlin/id/a3/core/printer/connection/DefaultPrinterService.kt`, `app-cafe/src/main/java/id/tenang/kasir/cafe/MainActivity.kt`
- **Unit Test Komprehensif:** `feature/cafe/src/test/`, `feature/payment/src/test/`, `core/data/src/test/`, `core/printer/src/test/`
- **Dokumentasi & Status:** `feature_list.json` (F15–F18, F25, F26 passes: true), `progress.md`, `docs/ui/DESIGN-cafe.md`, `docs/decisions.md`

## 3. Yang Belum Selesai / Masalah Terbuka
- Track Laundry Milestone M2 (F19–F24) belum diimplementasikan: pencarian pelanggan (`customer.created`), order kg/pcs, cetak tag QR, cuci ulang PIN manager, dan antrean laundry.
- Cetak struk belum mendukung retry antrean cetak otomatis saat printer putus di tengah jalan (F27).
- Penerimaan flag server pada outbox sync (F28).

## 4. Keputusan Teknis Penting
- **ADR D-18 (Quiet Luxury & Left Nav Rail):** Transisi tablet landscape 11" ke Left Nav Rail 68 dp berlatar gelap `#141414`, item aktif putih murni, kanvas krem `#F4F2E6`, dan aksen Deep Forest Green (`#0A3D24`).
- **Pemisahan Dapur & Kasir:** Tiket dapur termal 58 mm tidak memuat harga sama sekali (PRT-03) dan hanya mencetak item baru (CAF-07).
- **Auto-Connect Bluetooth SPP:** Mengaitkan printer terpasang (`RPP02N`) secara otomatis begitu izin runtime `BLUETOOTH_CONNECT` diberikan.
- **TopBar Reaktif:** Status printer di TopBar membaca StateFlow nyata dari `PrinterService` (bukan nilai hardcoded).

## 5. Langkah Berikutnya (Berurutan)
1. Beralih ke Track Laundry M2: Implementasi F19 (Cari atau buat pelanggan baru via Dialog L02 dan event `customer.created`).
2. Implementasi F20: Layar transaksi laundry per kg dan per pcs dengan express (L01/L03, test vector L1–L3).
3. Implementasi F21: Cetak tag QR struk kertas roll 58 mm dan pemindaian status (L07 CameraX).
4. Implementasi F22–F24: Pengambilan sebagian, cuci ulang PIN manager, dan antrean cucian berjalan.
5. Verifikasi F27 (Antrean cetak) dan F28 (Penanganan flag server pada sinkronisasi).

## 6. File yang Perlu Dibaca Pertama
- `feature_list.json` (baris 184–240 untuk spesifikasi F19–F24)
- `docs/spec/04-kebutuhan-fungsional-b-transaksi-cafe-da.md` (bagian transaksi laundry)
- `docs/ui/DESIGN-laundry.md` (panduan visual dan token tema Athia Laundry House)
- `contracts/test-vectors.json` (test vectors L1–L4)
