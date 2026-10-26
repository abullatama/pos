# HANDOFF: Paritas POS Laundry dengan Cafe (TopBar Kas, Gerbang Shift, & Refund)

## 1. Ringkasan Perubahan yang Selesai Dikerjakan

Sesi ini menyelesaikan penyelarasan menyeluruh aplikasi **Laundry (Athia Laundry House)** dengan seluruh perbaikan yang telah diterapkan pada aplikasi **Cafe (AA Coffeeshop)** pada tanggal 25 Oktober 2026, dengan tetap mematuhi aturan operasional khusus laundry (*AGENTS.md Aturan 10*):

### A. Latar Belakang & Masalah
1. **Kebocoran Saldo Kas di TopBar Laundry (Pelanggaran TUP-02):**
   TopBar Laundry sebelumnya masih menampilkan nominal uang modal (`"Shift Aktif · Kas Rp 200.000"`). Hal ini membocorkan informasi kas yang bertentangan dengan prinsip *Hitung Kas Buta* (*blind cash count*) dan menyisakan karakter dot pemisah (`·`) yang sudah dihapus di Cafe.
2. **Inkonsistensi Nama Menu di Navigation Rail:**
   Menu pencatatan kas di Cafe telah diselaraskan menjadi satu kata (`"Kas"`), sementara di Laundry masih menggunakan `"Kas Keluar"`.
3. **Absennya Penguncian Shift pada Rail Navigasi Laundry:**
   Seluruh item navigasi di Laundry sebelumnya selalu `enabled = true` tanpa mempedulikan status shift. Kasir dapat membuka *Order Baru* dan *Kas Keluar* meski shift kasir belum dibuka.
4. **Ketiadaan Auto-Redirect Saat Shift Tutup:**
   Aplikasi Laundry tidak memiliki pengarah rute reaktif ke layar Shift saat shift ditutup/selesai.
5. **Tombol Refund Tunai di Layar Cucian:**
   Logika bisnis refund pada `LaundryOrdersViewModel` sudah siap, namun tombol pemicu refund belum tersedia pada panel detail pesanan cucian.

---

### B. Solusi yang Diimplementasikan

1. **Penyelarasan TopBar Laundry (`app-laundry/MainActivity.kt`):**
   - Mengubah `shiftInfo` menjadi `if (activeShift != null) null else "Belum Buka Shift"`.
   - Menghapus import `formatRupiah` yang tidak lagi digunakan.
   - Format TopBar Laundry kini bersih: hanya menampilkan identitas kasir bertugas (`[Icon] Siti Rahma`) saat shift aktif, dan `"Belum Buka Shift"` saat shift nonaktif, bebas dot dan bebas bocoran saldo kas.

2. **Standarisasi Nama Menu Rail & Gerbang Shift Cerdas (`app-laundry/MainActivity.kt`):**
   - Nama menu di Left Rail diselaraskan menjadi **`"Kas"`** (seragam dengan *Beranda*, *Order Baru*, *Cucian*, *Shift*).
   - Mematuhi *AGENTS.md Aturan 10* (*"Event laundry.* tidak terikat shift; sesi staf laundry tidak menutup shift"*) & *Gerbang Shift Cerdas (Opsi 1)*:
     - **`BERANDA`:** `enabled = true` (staf cuci bebas melihat metriks & antrean siap).
     - **`CUCIAN`:** `enabled = true` (staf cuci bebas melihat pesanan & memajukan status fisik: Mulai Cuci, Siap, Ambil).
     - **`SHIFT`:** `enabled = true` (kasir bebas membuka/mengelola shift).
     - **`ORDER` (Order Baru):** `enabled = isShiftActive` (terkunci redup 30% dan unclickable jika shift belum dibuka).
     - **`KAS_KELUAR` (Kas):** `enabled = isShiftActive` (terkunci redup 30% dan unclickable jika shift belum dibuka).
   - Menambahkan pengaman ganda pada `onNavItemSelected` dan `LaunchedEffect(isShiftActive)` auto-redirect ke `SHIFT` jika kasir berada di `ORDER` atau `KAS_KELUAR` saat shift menjadi nonaktif.

3. **Integrasi Tombol Aksi Refund Tunai (`feature/laundry/presentation/LaundryOrdersScreen.kt`):**
   - Menambahkan tombol aksi **"Refund Tunai [Rp XX.XXX] (PIN Manager)"** pada `OrderDetailPanel` jika `order.paidAmount > 0` dan `order.status != "refunded"`.
   - Dilindungi pengecekan shift kasir aktif: jika belum buka shift, kasir diminta membuka shift terlebih dahulu karena refund tunai memotong kas laci (*COR-04 / KAS-01*).
   - Terintegrasi langsung dengan dialog otorisasi PIN Manager terenkripsi (*COR-07*) dan menambahkan status chip bahaya `"Di-Refund"` (`StatusToken.DANGER`).

---

## 2. File yang Dimodifikasi

1. **`app-laundry/src/main/java/id/tenang/kasir/laundry/MainActivity.kt`**
   - Sanitasi TopBar (hapus bocoran kas & dot), standarisasi nama menu `"Kas"`, gerbang shift cerdas `laundryNavItems`, auto-redirect shift, dan pengaman navigasi.
2. **`feature/laundry/src/main/kotlin/id/a3/feature/laundry/presentation/LaundryOrdersScreen.kt`**
   - Penambahan status badge `"Di-Refund"` dan tombol aksi *Refund Tunai* berotorisasi PIN Manager.
3. **`progress.md`**
   - Mencatat penuntasan paritas penuh fitur POS Laundry dengan Cafe.
4. **`HANDOFF.md`**
   - Memperbarui dokumentasi handoff untuk sesi baru.

---

## 3. Status Pengujian & Kualitas Kode

- **Unit Test Modul Terkait:**
  `./gradlew :feature:laundry:testDebugUnitTest :feature:shift:testDebugUnitTest :core:ui:testDebugUnitTest` $\rightarrow$ **BUILD SUCCESSFUL** (100% lulus tanpa kegagalan).
- **Compile APK Debug Kedua Aplikasi:**
  `./gradlew :app-cafe:assembleDebug :app-laundry:assembleDebug` $\rightarrow$ **BUILD SUCCESSFUL** (100% lulus dalam 13 detik).
- **Status Git:** Bersih, seluruh file termodifikasi telah teruji dan siap di-commit.

---

## 4. Panduan Pengujian di Tablet (Redmi Pad 2) untuk Sesi Baru

1. **Uji Penguncian Menu Shift Belum Buka (Laundry):**
   - Buka aplikasi Laundry saat belum ada shift aktif.
   - Amati Left Navigation Rail: Menu **Order Baru** dan **Kas** tampil redup (alpha 30%) dan tidak dapat diklik.
   - Menu **Beranda**, **Cucian**, dan **Shift** tetap menyala dan dapat dibuka oleh staf.
   - TopBar menampilkan: `[Icon Person] Siti Rahma   Belum Buka Shift` (tanpa dot `·` dan tanpa angka saldo kas).
2. **Uji Buka Shift:**
   - Masuk ke tab **Shift** $\rightarrow$ buka shift kasir dengan kas awal.
   - Menu **Order Baru** dan **Kas** otomatis menyala terang (100% aktif).
   - TopBar menampilkan: `[Icon Person] Siti Rahma` secara minimalis dan rapi.
3. **Uji Refund Tunai:**
   - Buka menu **Cucian** $\rightarrow$ pilih pesanan yang sudah memiliki pembayaran (`paidAmount > 0`).
   - Tekan tombol **"Refund Tunai [Rp XX.XXX] (PIN Manager)"** di bagian bawah panel detail.
   - Masukkan PIN Manager `4444`: uang tunai dikembalikan, mutasi outbox tercatat, dan status pesanan berubah menjadi `Di-Refund`.
4. **Uji Tutup Shift & Auto-Redirect:**
   - Tutup shift kasir dari menu **Shift**.
   - Buka pesanan atau kas keluar $\rightarrow$ tutup paksa shift: aplikasi seketika mengunci kembali menu kasir dan mengarahkan tampilan ke rute **Shift**.
