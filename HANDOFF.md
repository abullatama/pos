# HANDOFF: Perbaikan Alur Kas Seharusnya Tutup Shift & Eliminasi Menyeluruh Elemen Hardcoded

## 1. Ringkasan Pekerjaan Sesi Ini

Sesi ini menyelesaikan dua investigasi besar dan optimalisasi arsitektur menyeluruh pada aplikasi **Cafe** dan **Laundry**:

1. **Perbaikan Masalah Penjualan Tunai Tidak Terbaca saat Tutup Shift:**
   - **Akar Masalah:** `CafeMenuViewModel` sebelumnya meng-hardcode `shiftId = "shift-001"` pada pembuatan pesanan, dan `PaymentViewModel` tidak meneruskan `shiftId` ke transaksi database Room. Akibatnya, baris pembayaran tersimpan dengan ID shift statis `"shift-001"`, sementara kasir menutup shift dengan UUID riil, sehingga kueri penjualan tunai mengembalikan Rp 0 dan kas seharusnya dihitung keliru.
   - **Solusi Tuntas:** 
     * `CafeMenuViewModel` kini dinamis mengikat `activeShift.id` dan `cashierId`.
     * `PaymentTransaction`, `PaymentUiState`, dan `PaymentRepository` kini meneruskan `shiftId` aktif.
     * `OrderTransactionManager` memiliki fallback otomatis ke `shiftDao.getActiveShift()?.id` jika `shiftId` tidak dipasok secara eksplisit.
     * `DefaultShiftRepository` menghitung penjualan kotor dan diskon riil dari database (mengeliminasi hardcoded `discount = 0L`).

2. **Detail Arus Kas Sistem & Banner Pesanan Terbuka di Layar Tutup Shift (S10):**
   - Menambahkan buku mutasi arus kas laci transparan pada kartu evaluasi selisih kas di Tahap 2:
     * `(+) Kas Awal Modal`
     * `(+) Penjualan Tunai`
     * `(+) DP & Pelunasan Tunai`
     * `(-) Kas Keluar (Petty Cash)`
     * `(-) Refund Tunai`
     * `(=) Total Kas Seharusnya (Sistem)` vs `Kas Dihitung (Fisik)` $\rightarrow$ `Selisih Kas`
   - Menambahkan banner informatif kepatuhan **TUP-01 / C08**: *"Terdapat X pesanan cafe masih terbuka. Pesanan ini otomatis diserahterimakan ke shift kasir berikutnya."*

3. **Eliminasi Menyeluruh Elemen Hardcoded Lintas Modul:**
   - **Penomoran Nota Dinamis Cafe:** Mengimplementasikan `getNextDisplayNumber("C1-")` di `CafeOrderRepository` yang menghitung urutan harian di Room SQLite (`countOrdersForPrefixAndDate`) sesuai Rule 8 `AGENTS.md`, mengeliminasi nomor nota statis `#C1-0001`.
   - **Sentralisasi Verifikasi PIN Manager & Owner:** Membangun `ManagerPinVerifier` di `:core:domain:security` dengan enkripsi hash SHA-256 + salt tetap, mengeliminasi pengecekan plaintext `"4444"` dan `"9999"` di 6 ViewModel (`ShiftViewModel`, `CloseShiftViewModel`, `CashOutViewModel`, `CafeMenuViewModel`, `LaundryOrdersViewModel`, dan `AdminViewModel`).
   - **Profil Toko Dinamis pada Struk:** `CashOutScreen`, `CashOutViewModel`, dan `LaundryTagFormatter` kini menerima parameter dinamis `storeName`, `storeAddress`, dan `storePhone` dari konfigurasi NavHost masing-masing aplikasi (`AA COFFEESHOP` untuk Cafe dan `ATHIA LAUNDRY HOUSE` untuk Laundry).
   - **Identitas Kasir Dinamis pada Laundry:** `LaundryOrderScreen` dan tag cucian kini mengikat nama dan ID kasir bertugas dari shift aktif.
   - **Device ID Dinamis:** `registeredDeviceId` diangkat (*state hoisting*) di `MainActivity` dari `DeviceManager.getDeviceId()` untuk kedua aplikasi dan diteruskan ke `AdminViewModelFactory` dan `OrderTransactionManager` (outbox event membawa device ID tablet riil).
   - **Label Pajak Bahasa Indonesia:** Mengubah label hardcoded `"Tax (10%)"` di `CafePosScreen` menjadi `"Pajak"`.

---

## 2. Modul & File yang Diperbarui

### A. Modul `:core:domain`
- **`ManagerPinVerifier.kt` (BARU):** Komponen terpusat otorisasi manajerial dan owner berbasis hash SHA-256 dan salt tetap (`DEFAULT_MANAGER_PIN_HASH` untuk seed dev `4444`).

### B. Modul `:core:data`
- **`OrderDao.kt`:** Menambahkan kueri `getDiscountTotalForShift` dan `getGrossSubtotalForShift` untuk kalkulasi akuntansi shift riil.
- **`OrderTransactionManager.kt`:** 
  * Resolusi shift cerdas pada `recordPayment` dan `createAndPayOrder`: `effectiveShiftId = shiftId ?: activeShift?.id ?: currentOrder.shiftId`.
  * Resolusi device ID otomatis pada event outbox: `effectiveDeviceId = activeShift?.deviceId ?: deviceId`.
- **`RoomOutboxTransactionTest.kt`:** Menambahkan unit test verifikasi `recordPayment without explicit shiftId links to active shift and counts toward cashSales`.

### C. Modul `:core:printer`
- **`LaundryTagFormatter.kt`:** Menambahkan properti `storeName` pada `LaundryTagData` dan menghapus teks toko hardcoded.

### D. Modul `:feature:shift`
- **`ShiftRepository.kt`:** Mengintegrasikan total diskon riil dan subtotal pesanan pada `ShiftSummary`.
- **`CloseShiftScreen.kt`:** Menambahkan rincian arus kas sistem (buku mutasi laci K1) dan banner serah terima pesanan terbuka (TUP-01).
- **`ShiftViewModel.kt`, `CloseShiftViewModel.kt`, `CashOutViewModel.kt`:** Menggunakan `ManagerPinVerifier.verify(pin)`.
- **`CashOutScreen.kt` & `CashOutViewModel.kt`:** Mendukung `setStoreProfile(name, address, phone)` dinamis.

### E. Modul `:feature:cafe`
- **`CafeOrderRepository.kt` & `CafeMenuViewModel.kt`:** Penomoran nota harian dinamis `getNextDisplayNumber("C1-")`, pengikatan `activeShift.id` dan `cashierId`.
- **`CafePosScreen.kt`:** Mengubah `"Tax (10%)"` menjadi `"Pajak"`.

### F. Modul `:feature:laundry`
- **`LaundryOrderViewModel.kt`, `LaundryOrderScreen.kt`, `LaundryOrdersViewModel.kt`, `LaundryScanViewModel.kt`:** Mendukung `cashierId`, `cashierName`, dan `storeName` dinamis pada pembuatan order dan tag cucian.
- **`LaundryOrdersViewModel.kt`:** Menggunakan `ManagerPinVerifier.verify(pin)`.

### G. Modul `:feature:payment`
- **`PaymentRepository.kt` & `PaymentViewModel.kt`:** Meneruskan `shiftId` ke transaksi pembayaran database.

### H. Modul `:feature:admin`
- **`AdminViewModel.kt`, `AdminViewModelFactory.kt`, `AdminViewModelTest.kt`:** Menggunakan verifikasi hash SHA-256 `ownerPinHash` dan `deviceId` dinamis.

### I. Modul `app-cafe` & `app-laundry`
- **`MainActivity.kt` & `NavHost.kt`:** Hoisting `registeredDeviceId`, penerusan `shiftId` saat inisialisasi pembayaran, dan penyaluran profil toko ke layar kas keluar.

---

## 3. Status Terkini Proyek

- **Milestone M0 s/d M3:** 100% Selesai & Terverifikasi
- **Milestone M4 (Laporan, WA, & Go-live):** 0 dari 4 (0%) — **Milestone Aktif Berikutnya**
- **Kondisi Pengujian:**
  - Seluruh unit test proyek (`:core:domain:test`, `:core:data:test`, `:core:printer:test`, `:core:auth:test`, `:core:sync:test`, `:core:ui:test`, `:feature:shift:test`, `:feature:payment:test`, `:feature:cafe:test`, `:feature:laundry:test`, `:feature:admin:test`) **100% LULUS (GREEN)**.
  - Kompilasi build kedua APK (`:app-cafe:assembleDebug` dan `:app-laundry:assembleDebug`) **SUKSES**.

---

## 4. Panduan untuk Memulai Sesi Baru (Milestone M4)

Ketika sesi baru dimulai, lanjutkan ke implementasi fitur **Milestone M4**:

1. **F42 (Tombol Kirim via WA Manual dan Status Notifikasi Laundry) — *Prioritas 1*:**
   - Polling status notifikasi dari server (`none`, `queued`, `sent`, `failed`) via `GET /v1/orders/{id}/notification-status`.
   - Tombol taktil hijau `#25D366` **"Kirim via WA"** untuk membuka `Intent` WhatsApp resmi dengan template pesan pelanggan.
2. **F41 (Heartbeat dan Crashlytics) — *Prioritas 2*:**
   - Telemetri periodik perangkat (`POST /v1/devices/heartbeat`) via coroutine foreground dan WorkManager cadangan.
   - Crash reporting aman tanpa data sensitif pelanggan (OPS-01).
3. **F43 (Uji Offline 24 Jam):**
   - Verifikasi ketahanan offline di tablet Redmi Pad 2 (`needs_device: true`).
4. **F44 (Paket Go-live):**
   - SOP darurat kertas nota cadangan dan checklist peluncuran.

---

## 5. Kredensial & Pintasan Pengujian Tablet
- **Pintasan Demo Awal:** Tombol *"Masuk Mode Demo / Lewati"* di layar registrasi perangkat.
- **PIN Kasir (Shift & Transaksi):** `1234`
- **PIN Manager (Otorisasi Khusus):** `4444` *(Diskon manual, void/refund, kas keluar > 50rb, cuci ulang, ambil belum lunas, tutup paksa)*.
- **PIN Owner (Menu Admin S15):** `9999` *(Kios, Wi-Fi, uji cetak struk)*.
