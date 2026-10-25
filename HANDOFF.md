# HANDOFF: Dialog Peringatan & Persetujuan Kunci Hitungan Kas (Tutup Shift)

## 1. Ringkasan Perubahan yang Selesai Dikerjakan

Sesi ini mengimplementasikan gerbang pengaman berupa dialog peringatan (*warning*) dan persetujuan eksplisit (*agreement checkbox*) sebelum kasir berpindah dari **Tahap 1 (Hitung Kas Buta)** ke **Tahap 2 (Selisih & QRIS)** pada menu Tutup Shift Kasir (`CloseShiftScreen.kt` & `CloseShiftViewModel.kt`) di aplikasi Cafe (dan Laundry) sesuai aturan bisnis `TUP-03` dan standar desain *antislop-ui*:

### A. Latar Belakang & Masalah
- **Aturan Bisnis TUP-03:** Setelah melangkah ke Tahap 2, angka penjualan sistem akan terbuka dan nominal kas fisik yang telah diinput kasir langsung **DIKUNCI**. Kasir tidak dapat kembali atau mengubah hitungan kas tanpa otorisasi PIN Manager (4444).
- **Celah Sebelumnya:** Kasir yang menekan tombol *"Lanjut ke Evaluasi Selisih"* langsung berpindah ke Tahap 2 seketika tanpa peringatan awal. Jika kasir salah ketik/salah hitung karena terburu-buru, kasir langsung terkunci dan membutuhkan campur tangan Manager untuk hitung ulang.

### B. Solusi yang Diimplementasikan
1. **Ekstensi Komponen Dialog (`core:ui` / `A3Dialog.kt`):**
   - Menambahkan parameter `confirmEnabled: Boolean = true` pada komponen dialog bersama `A3Dialog`.
   - Tombol aksi utama dialog (`confirmLabel`) kini dapat dinonaktifkan secara deklaratif berdasarkan kondisi (misalnya checkbox persetujuan belum dicentang).
   - Perubahan ini non-breaking dan kompatibel penuh dengan seluruh penggunaan `A3Dialog` di modul lain.

2. **State & Alur ViewModel (`feature:shift` / `CloseShiftViewModel.kt`):**
   - Menambahkan field state `isStep1ConfirmationDialogOpen: Boolean = false` pada `CloseShiftUiState`.
   - Menambahkan method `openStep1ConfirmationDialog()` yang hanya membuka dialog jika `countedCash > 0L`.
   - Menambahkan method `closeStep1ConfirmationDialog()` untuk membatalkan/menutup dialog tanpa berpindah step.
   - Mengintegrasikan pembersihan flag dialog pada `proceedToStep2()`, `initializeWithShift()`, dan `resetState()`.

3. **Dialog Peringatan & Persetujuan (`feature:shift` / `CloseShiftScreen.kt`):**
   - Tombol *"Lanjut ke Evaluasi Selisih"* pada Step 1 kini memicu `openStep1ConfirmationDialog()`.
   - Dialog konfirmasi menampilkan:
     - **Judul:** `"Konfirmasi Hitungan Kas Fisik"`
     - **Ringkasan Kas:** Kartu kanvas berteks tebal dan besar (28 sp) menampilkan total uang fisik yang diinput (`formatRupiah(uiState.countedCash)`).
     - **Banner Peringatan (Warning):** Menggunakan warna status peringatan (`colors.status.warning`), menginformasikan bahwa nominal kas akan dikunci dan tidak bisa diubah tanpa otorisasi PIN Manager.
     - **Checkbox Persetujuan:** Touch target nyaman $\ge$ 48 dp dengan seluruh baris dapat diklik (*clickable row*), berisi teks pernyataan teliti kasir:
       > *"Saya menyatakan telah menghitung seluruh fisik uang tunai di laci kasir dengan teliti dan yakin nominalnya sudah sesuai."*
     - **Tombol Aksi:**
       - *"Periksa Ulang"* (Ghost): Menutup dialog agar kasir dapat memeriksa ulang lembaran uang.
       - *"Kunci & Lanjut ke Selisih"* (Primary): Hanya aktif jika checkbox persetujuan telah dicentang (`confirmEnabled = isAgreementChecked`). Saat ditekan, memanggil `proceedToStep2()`.

---

## 2. File yang Dimodifikasi

1. **`core/ui/src/main/kotlin/id/a3/core/ui/component/A3Dialog.kt`**
   - Menambahkan parameter `confirmEnabled: Boolean = true` dan meneruskannya ke `A3Button`.
2. **`feature/shift/src/main/kotlin/id/a3/feature/shift/presentation/CloseShiftViewModel.kt`**
   - Menambahkan state `isStep1ConfirmationDialogOpen`, `openStep1ConfirmationDialog()`, dan `closeStep1ConfirmationDialog()`.
3. **`feature/shift/src/main/kotlin/id/a3/feature/shift/presentation/CloseShiftScreen.kt`**
   - Mengubah aksi tombol lanjut Step 1 ke pembukaan dialog konfirmasi dan merender modal `A3Dialog` persetujuan.
4. **`feature/shift/src/test/kotlin/id/a3/feature/shift/presentation/CloseShiftViewModelTest.kt`**
   - Menambahkan skenario pengujian unit untuk pembukaan, penutupan, dan validasi dialog konfirmasi Step 1.
5. **`progress.md`**
   - Menambahkan entri progres tanggal 2026-10-26.
6. **`HANDOFF.md`**
   - Memperbarui dokumentasi handoff untuk sesi baru.

---

## 3. Status Pengujian & Kualitas Kode

- **Unit Test Feature Shift & Core UI:**
  `./gradlew :feature:shift:testDebugUnitTest :core:ui:testDebugUnitTest` $\rightarrow$ **BUILD SUCCESSFUL** (100% lulus tanpa kegagalan).
- **Compile APK Debug Cafe:**
  `./gradlew :app-cafe:assembleDebug` $\rightarrow$ **BUILD SUCCESSFUL** (100% lulus).
- **Git Status:** Bersih, semua perubahan telah diuji dan siap di-commit.

---

## 4. Panduan Pengujian Visual di Tablet (Redmi Pad 2) untuk Sesi Baru

1. Jalankan aplikasi Cafe pada tablet Redmi Pad 2 dengan shift aktif.
2. Buka menu **Shift** $\rightarrow$ tekan **Tutup Shift**.
3. **Tahap 1 (Hitung Kas Fisik):**
   - Masukkan lembaran uang kas di laci kasir (misal: 5 lembar Rp 100.000).
   - Tekan tombol **"Lanjut ke Evaluasi Selisih"**.
4. **Verifikasi Dialog Konfirmasi:**
   - Muncul dialog `"Konfirmasi Hitungan Kas Fisik"`.
   - Nominal total kas `Rp 500.000` tertulis jelas di bagian atas dialog.
   - Muncul banner peringatan audit berwarna oranye/peringatan: hitungan akan dikunci dan membutuhkan PIN Manager untuk kembali.
   - Tombol **"Kunci & Lanjut ke Selisih"** dalam kondisi nonaktif (disabled/abu-abu).
   - Centang checkbox persetujuan atau ketuk baris teks persetujuan: tombol **"Kunci & Lanjut ke Selisih"** langsung aktif.
   - Tekan **"Periksa Ulang"**: dialog tertutup dan kasir kembali ke layar hitung pecahan uang.
   - Buka kembali dialog, centang persetujuan, dan tekan **"Kunci & Lanjut ke Selisih"**: aplikasi berhasil masuk ke Tahap 2 (Selisih & QRIS).
