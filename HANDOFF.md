# HANDOFF: Redesain Menu Kas, Fitur Riwayat Kas Keluar Shift, & Eliminasi Lag Keyboard 60 FPS

## 1. Ringkasan Pekerjaan Sesi Ini

Sesi ini menyelesaikan perbaikan menyeluruh pada menu **Kas (Kas Keluar)** di aplikasi Cafe dan infrastruktur antarmuka sistem:

1. **Redesain Antarmuka Menu Kas (Kas Keluar) Sesuai Standar UI/UX Tablet:**
   - **Penyelarasan Menu Rail:** Nama menu navigasi di Left Rail diselaraskan dari `"Kas Keluar"` menjadi `"Kas"` agar seragam satu kata dengan menu lainnya (*Kasir*, *Pesanan*, *Shift*).
   - **Pembersihan Tombol Kembali:** Menghapus tombol kembali (`ArrowBack`) di header atas karena menu Kas merupakan tab utama di Navigation Rail, bukan halaman tumpukan (*nested*).
   - **Keypad Numerik Proporsional Tablet (`A3Keypad`):** Keypad numerik dibatasi lebarnya secara proporsional (`widthIn(max = 340.dp)`), tombol angka dan backspace diberi border halus tegas (`1.dp, colors.border`) dan radius `10.dp`. Tombol **`C` (Clear)** diaktifkan untuk mereset nominal langsung, dan tombol **`000`** dipindahkan ke baris preset ribuan bersama `+10rb`, `+20rb`, `+50rb`, `+100rb`.
   - **Chip Kategori Responsif (`FlowRow`):** Mengganti `Row` kaku dengan `FlowRow(spacedBy = 8.dp)` sehingga 5 chip kategori membungkus otomatis menjadi 2 baris rapi tanpa menabrak batas kartu atau teks terpotong.
   - **Tombol Aksi Berjangkar (*Pinned Bottom Action*):** Tombol simpan (*"Catat Kas Keluar"* / *"Minta Otorisasi Manager"*) berjangkar rapi di dasar kartu sehingga selalu terlihat dan tidak menempel pada input field.

2. **Implementasi Fitur Riwayat Kas Keluar Shift Ini (Opsi A — Tab Control):**
   - **Segmented Tab Control:** Menyediakan 2 tab bersih di atas layar:
     - **Tab 1: Catat Kas Keluar** (formulir pencatatan kas keluar 50/50 split).
     - **Tab 2: Riwayat Shift Ini (N)** (tampilan dashboard riwayat pengeluaran shift aktif).
   - **`CashOutHistoryView` (Pola Split 36/64):**
     - **Panel Kiri (36%): Ringkasan Kas Keluar Shift Ini:** Total uang keluar yang memotong laci kas (`A3MoneyText` besar), frekuensi pengeluaran, akumulasi pengeluaran per kategori (Bahan baku, Operasional, dll), dan tombol cepat `+ Catat Kas Keluar Baru`.
     - **Panel Kanan (64%): Daftar Kartu Riwayat Pengeluaran (`LazyColumn`):** Setiap item menampilkan jam pencatatan, chip kategori, nominal, deskripsi/keperluan belanja, status foto nota, status otorisasi PIN Manager / Kasir Mandiri, dan tombol **"Cetak Salinan"** (*Reprint*) yang mencetak struk thermal 58 mm bertanda `*** SALINAN ***` (sesuai aturan PRT-05).
     - Menampilkan `A3EmptyState` jika belum ada pengeluaran pada shift berjalan.
   - **Dukungan Data Room Real-Time:** `ShiftRepository` dan `DefaultShiftRepository` memaparkan `observeCashOutsForShift(shiftId): Flow<List<CashOutEntity>>` yang langsung mendelegasikan ke `CashOutDao`.
   - **Alur Sukses Fleksibel:** Setelah kas keluar dicatat, dialog sukses menyediakan opsi *"Lihat Riwayat"* atau *"Catat Lagi"*.

3. **Penyelesaian Anomali & Eliminasi Penuh Lag Keyboard Virtual (60 FPS Fix):**
   - **Mengatasi Lag Ekstrem 60 FPS (`AppShell.kt`):** Mengganti pembacaan `WindowInsets.safeDrawing` dengan `WindowInsets.navigationBars` pada padding bawah viewport konten. Sebelumnya, `safeDrawing` menyertakan `ime` (keyboard virtual) sehingga pembukaan keyboard memicu perubahan padding dan rekomposisi massal di pucuk hirarki 60–120 kali/detik saat animasi keyboard berlangsung. Dengan `navigationBars`, padding bawah tetap konstan mengawal bilah navigasi sistem.
   - **Mengatasi Layar yang Terangkat / Collapse ke Atas (`AndroidManifest.xml`):** Mengubah `android:windowSoftInputMode="adjustPan"` menjadi `"adjustResize"` di `app-cafe` dan `app-laundry`. WindowManager Android tidak lagi menggeser paksa jendela fisik aplikasi ke atas saat mengetik. TopBar dan header tetap kokoh (*rock-solid*).
   - **Aksi Keyboard Selesai (`A3TextField.kt` & `CashOutScreen.kt`):** `A3TextField` kini mendukung dan meneruskan parameter `keyboardActions: KeyboardActions`. Kolom "Keperluan Pengeluaran" dikonfigurasi dengan `ImeAction.Done` dan `focusManager.clearFocus()` sehingga keyboard otomatis tertutup rapi saat tombol centang/Done ditekan.
   - **Ketahanan Panel Kiri:** Menambahkan penggulungan independen `.verticalScroll(rememberScrollState())` pada Panel Kiri agar tidak terjadi *clipping* atau tabrakan saat keyboard muncul di layar dengan resolusi vertikal terbatas.

---

## 2. Rincian Modul & Kode yang Diubah

### A. Modul `:core:ui`
- **`AppShell.kt`:** Mengganti `WindowInsets.safeDrawing` dengan `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` untuk padding bawah konten.
- **`A3Keypad.kt`:** Menambahkan border halus 1dp pada `KeypadCell` dan tombol backspace.
- **`A3TextField.kt`:** Menambahkan parameter `keyboardActions: KeyboardActions = KeyboardActions.Default` dan meneruskannya ke `BasicTextField`.

### B. Modul `:feature:shift`
- **`ShiftRepository.kt` & `DefaultShiftRepository.kt`:** Menambahkan `observeCashOutsForShift(shiftId): Flow<List<CashOutEntity>>`.
- **`CashOutViewModel.kt`:** Menambahkan `CashOutTab` (`INPUT`, `HISTORY`), observasi reaktif riwayat kas keluar shift aktif, fungsi `selectTab`, `resetSuccess`, dan `reprintCashOut`.
- **`CashOutScreen.kt`:** Segmented Tab Bar, penghapusan tombol kembali header, keypad proporsional 340dp, chip kategori `FlowRow`, `ImeAction.Done` pada text field, dan komponen `CashOutHistoryView` dengan cetak salinan struk.
- **`CashOutViewModelTest.kt` & `ShiftViewModelTest.kt`:** Unit test baru untuk perpindahan tab, observasi data Room, reset form, dan cetak salinan struk.

### C. Modul `app-cafe` & `app-laundry`
- **`AndroidManifest.xml` (keduanya):** Mengubah `windowSoftInputMode` menjadi `"adjustResize"`.
- **`MainActivity.kt` (`app-cafe`):** Menyelaraskan nama menu navigasi menjadi `"Kas"`.

---

## 3. Status Terkini Proyek

- **Milestone M0 (Spike & Hardware):** 5 dari 5 tuntas (100%)
- **Milestone M1 (Fondasi & Multi-modul):** 9 dari 9 tuntas (100%)
- **Milestone M2 (Transaksi Cafe & Laundry):** 15 dari 15 tuntas (100%)
- **Milestone M3 (Shift, Kas, & Kios Admin):** 11 dari 11 tuntas (100%)
- **Milestone M4 (Laporan, WA, & Go-live):** 0 dari 4 (0%) — **Milestone Aktif**
- **Total Fitur Selesai:** **40 dari 44 fitur tuntas (90,9%)**.
- **Kondisi Pengujian:** Seluruh unit test proyek (`:core:ui:test`, `:feature:shift:test`, dll) lulus 100%. Kompilasi kedua APK debug berhasil tuntas tanpa error.

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
