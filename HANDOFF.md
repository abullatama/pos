# HANDOFF: Isolasi Lifecycle CloseShiftViewModel per ShiftId & Reset State Wizard Tutup Shift

## 1. Ringkasan Masalah & Solusi yang Selesai Dikerjakan

Sesi ini menyelesaikan investigasi mendalam dan perbaikan arsitektur POS (Cafe & Laundry) terkait alur penutupan shift sesuai *best practice* Android/Compose:

### Masalah: Tutup Shift Baru Langsung Berada di Tahap Terakhir (Alokasi & Laporan Z)
* **Gejala:**
  Setelah tutup shift berhasil dilakukan dan Laporan Z dicetak, kasir membuka shift baru. Ketika kasir langsung menekan tombol *"Tutup Shift Sekarang"*, layar langsung berada di tahap terakhir tutup shift yaitu **Tahap 3 (Alokasi & Laporan Z)** dengan data kas dari shift sebelumnya. Saat kasir menekan tombol *Kembali*, layar mundur ke Tahap 2, dan saat kembali lagi ke Tahap 1, sistem **mengunci layar dan mewajibkan PIN Manager** (karena aturan TUP-03: perlindungan hitung kas buta yang melarang kembali ke Tahap 1 tanpa PIN jika angka sistem sudah terlihat di Tahap 2).
* **Akar Masalah Utama:**
  1. **Lifecycle Misalignment / Scoping Terlalu Luas di NavHost:**
     Pada `CafeNavHost.kt` dan `LaundryNavHost.kt`, `CloseShiftViewModel` sebelumnya dideklarasikan sebagai single instance di tingkat teratas `NavHost`:
     `val closeShiftViewModel: CloseShiftViewModel = viewModel(factory = ...)`
     Karena di-scope ke root NavHost/Activity, ViewModel ini bertahan sepanjang masa hidup aplikasi dan tidak pernah di-destroy antar-shift.
  2. **Kurangnya State Reset pada `initializeWithShift`:**
     Ketika alur tutup shift dipanggil ulang untuk shift baru, `viewModel.initializeWithShift()` hanya memperbarui `summary` dan `checkedQrisPaymentIds`, tanpa mereset `currentStep` (tetap `STEP_3_ALLOCATION_AND_Z`), pecahan kas (`denominations`), kas dihitung (`countedCash`), alokasi kas laci/setor, maupun status penutupan (`isSuccess`).
  3. **Deadlock Aturan Anti-Fraud TUP-03:**
     Karena `currentStep` masih berada di Tahap 3, tombol kembali di Tahap 3 memanggil `backToStep2()`. Di Tahap 2, tombol kembali mewajibkan otorisasi PIN Manager via `openRecountPinDialog()`, sehingga kasir terperangkap dan tidak dapat mengisi hitungan kas buta untuk shift baru.

* **Solusi Tuntas yang Diimplementasikan:**
  1. **State Reset Penuh di `CloseShiftViewModel`:**
     - Pada `initializeWithShift()`, seluruh state UI di-reset total ke `CloseShiftUiState(currentStep = CloseShiftStep.STEP_1_BLIND_COUNT, isLoading = true, ...)` dan counter lockout PIN di-reset ke 0.
     - Menyediakan fungsi `resetState()` untuk membersihkan state kembali ke status default murni kapan saja.
  2. **Isolasi Lifecycle ViewModel per ShiftId (`key = activeShift.shiftId`):**
     - Menghapus deklarasi global `closeShiftViewModel` dari root `CafeNavHost` dan `LaundryNavHost`.
     - Meng-scope pembuatan ViewModel tepat saat dialog tutup shift aktif dengan kunci ID shift unik:
       `val closeShiftViewModel: CloseShiftViewModel = viewModel(key = activeShift.shiftId, factory = ShiftViewModelFactory(...))`
       Setiap shift baru dijamin mendapatkan instance ViewModel baru yang terisolasi dari sisa data shift lama.
     - Menambahkan pemanggilan `closeShiftViewModel.resetState()` pada callback `onCloseCompleted` dan `onCancel`.
  3. **Pembersihan pada Tombol Batal di Step 1:**
     - Tombol kembali pada Tahap 1 (`STEP_1_BLIND_COUNT`) di `CloseShiftScreen.kt` otomatis mengeksekusi `viewModel.resetState()` sebelum menutup form (`onCancel()`).
  4. **Pengujian Unit Test Baru di `CloseShiftViewModelTest`:**
     - `initializeWithShift resets state cleanly when closing subsequent shift`: Memastikan pergantian shift dari Tahap 3 langsung mereset wizard ke Tahap 1 dengan data bersih.
     - `resetState restores fresh clean state`: Memvalidasi reset instan seluruh field state.

---

## 2. Modul & File yang Diubah

1. **`feature/shift/src/main/kotlin/id/a3/feature/shift/presentation/CloseShiftViewModel.kt`**
   - Menambahkan fungsi `resetState()`.
   - Mengubah `initializeWithShift()` agar secara tegas membuat `CloseShiftUiState` baru yang bersih di `CloseShiftStep.STEP_1_BLIND_COUNT` dan mereset lockout PIN.
2. **`feature/shift/src/main/kotlin/id/a3/feature/shift/presentation/CloseShiftScreen.kt`**
   - Tombol kembali pada `STEP_1_BLIND_COUNT` memanggil `viewModel.resetState()` sebelum memanggil `onCancel()`.
3. **`app-cafe/src/main/java/id/tenang/kasir/cafe/navigation/CafeNavHost.kt`**
   - Menghapus deklarasi root-level `closeShiftViewModel`.
   - Menggunakan `viewModel(key = activeShift.shiftId, ...)` di dalam blok `isClosingShift && activeShift != null`.
   - Menambahkan pemanggilan `closeShiftViewModel.resetState()` pada `onCloseCompleted` dan `onCancel`.
4. **`app-laundry/src/main/java/id/tenang/kasir/laundry/navigation/LaundryNavHost.kt`**
   - Menghapus deklarasi root-level `closeShiftViewModel`.
   - Menggunakan `viewModel(key = activeShift.shiftId, ...)` di dalam blok `isClosingShift && activeShift != null`.
   - Menambahkan pemanggilan `closeShiftViewModel.resetState()` pada `onCloseCompleted` dan `onCancel`.
5. **`feature/shift/src/test/kotlin/id/a3/feature/shift/presentation/CloseShiftViewModelTest.kt`**
   - Menambahkan unit test `initializeWithShift resets state cleanly when closing subsequent shift`.
   - Menambahkan unit test `resetState restores fresh clean state`.
6. **`progress.md`**
   - Mencatat penyelesaian bug retensi state tutup shift dan isolasi lifecycle ViewModel.

---

## 3. Status Pengujian & Kualitas Kode

- **Unit Test Shift:**
  `./gradlew :feature:shift:testDebugUnitTest` $\rightarrow$ **100% LULUS** (seluruh pengujian lulus, termasuk 2 pengujian baru).
- **Global Test Suite & Build:**
  `./gradlew test :app-cafe:assembleDebug :app-laundry:assembleDebug` $\rightarrow$ **BUILD SUCCESSFUL** (357 actionable tasks berhasil).
- **Git Commit:**
  `3738f64` (`fix(shift, cafe, laundry): isolasi lifecycle CloseShiftViewModel per shift dan reset state wizard tutup shift`).

---

## 4. Panduan untuk Sesi Baru

- **Status Repositori:** Bersih (*clean working tree*), seluruh commit tersimpan di branch `master`.
- **Langkah Verifikasi Manual di Hardware (Redmi Pad 2):**
  1. Pasang APK debug terbaru `app-cafe-debug.apk` atau `app-laundry-debug.apk`.
  2. Buka shift (Shift A) dengan modal kas awal (misal Rp 200.000).
  3. Lakukan tutup shift hingga selesai di Tahap 3 dan cetak Laporan Z.
  4. Buka shift baru (Shift B) dengan modal kas awal.
  5. Langsung tekan tombol *"Tutup Shift Sekarang"*.
  6. **Hasil yang diharapkan:** Layar langsung terbuka di **Tahap 1 (Hitung Kas Buta)** dengan seluruh pecahan kosong (Rp 0), bukan di Tahap 3. Kasir dapat mengisi kas buta atau menekan tombol Kembali tanpa hambatan PIN Manager.
