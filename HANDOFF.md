# HANDOFF: Penguncian Navigasi Pasca Tutup Shift & Isolasi Riwayat Selesai Antar-Shift

## 1. Ringkasan Masalah & Solusi yang Selesai Dikerjakan

Sesi ini menyelesaikan dua investigasi mendalam dan perbaikan arsitektur POS Cafe sesuai *best practice* dan panduan UI/UX:

### A. Masalah 1: Menu Navigasi Tetap Bisa Diakses Pasca Tutup Shift & Cetak Laporan Z
* **Akar Masalah Utama:**
  1. Pada `MainActivity.kt` (`app-cafe`), `cafeNavItems` sebelumnya dibungkus dalam `remember { ... }` tanpa kunci dependensi `isShiftActive` dan setiap `NavItem` di-hardcode `enabled = true`. Akibatnya, saat shift kasir ditutup di database dan `activeShift` berubah menjadi `null`, objek navigasi tidak pernah diperbarui dan tetap mengizinkan kasir mengklik seluruh menu.
  2. Handler klik navigasi `onNavItemSelected` tidak memeriksa `isShiftActive`, melainkan hanya mengecek `item.enabled` (yang selalu `true`).
  3. Rute `CafeDestinations.PESANAN` dan `CafeDestinations.HABIS` pada `CafeNavHost.kt` sama sekali tidak memiliki pelindung status shift (*shift guard*), sehingga kasir dapat membuka dan berinteraksi bebas dengan meja aktif dan menu habis walaupun shift telah ditutup.
  4. Tidak ada pengarah otomatis (*auto-redirect*) kembali ke rute `SHIFT` saat `activeShift` menjadi `null`.
* **Solusi Tuntas yang Diimplementasikan:**
  1. Menjadikan `cafeNavItems` reaktif penuh dengan `remember(isShiftActive)`: menu **Kasir**, **Pesanan**, **Menu Habis**, dan **Kas Keluar** otomatis berstatus `enabled = isShiftActive` (tampil redup 30% dan *unclickable* di Navigation Rail saat shift belum buka / telah ditutup), sementara menu **Shift** tetap `enabled = true`.
  2. Menambahkan validasi ganda pada `onNavItemSelected`: navigasi hanya dieksekusi jika `isShiftActive == true` atau item yang dipilih adalah `CafeDestinations.SHIFT`.
  3. Menambahkan pengarah reaktif otomatis `LaunchedEffect(isShiftActive)`: jika `isShiftActive` berubah menjadi `false` dan posisi rute saat ini bukan `SHIFT` maupun `ADMIN`, sistem seketika mengembalikan kasir ke rute `CafeDestinations.SHIFT` (`OpenShiftScreen`).
  4. Menambahkan komponen pelindung *Empty State* `A3EmptyState` ("Shift Belum Dibuka") pada rute `PESANAN` dan `HABIS` di `CafeNavHost.kt`.

---

### B. Masalah 2: Shift Lain Bisa Melihat Riwayat Pesanan Selesai Shift Sebelumnya
* **Akar Masalah Utama:**
  1. Kueri Room DAO `OrderDao.observeCompletedOrdersWithLines()` sebelumnya mengeksekusi `SELECT * FROM orders WHERE status IN ('paid', 'refunded', 'void', 'partial_refund') ORDER BY createdAt DESC` tanpa memfilter berdasarkan `shiftId`.
  2. Kueri tanpa filter ini sengaja dipakai untuk mengakomodasi aturan bisnis **F36 (Refund Transaksi Lampau / COR-05)** agar kasir dapat mencari nota lama jika pelanggan datang komplain di hari berikutnya (maksimal Rp 100.000 dan maksimal 14 hari).
  3. Namun dampaknya, seluruh transaksi dari shift-shift terdahulu bercampur baur ke dalam daftar transaksi shift aktif. Kasir yang baru membuka shift langsung melihat angka omzet puluhan nota dan jutaan rupiah di header (`completedOrders.sumOf { it.order.total }`), yang melanggar akuntabilitas dan privasi kasir.
* **Solusi Tuntas yang Diimplementasikan:**
  1. Menambahkan kueri Room terfilter pada `OrderDao.kt` dan diekspos di `OrderTransactionManager.kt`:
     `observeCompletedOrdersWithLinesForShift(shiftId: String)`
  2. Memastikan pembaruan status pembayaran pada `OrderTransactionManager.recordPayment` mengikat `shiftId` yang menerima uang ke tabel `orders` (`updatePaymentStatus(..., shiftId)`).
  3. Mengelompokkan dan memisahkan data pada `CafeOpenOrdersScreen.kt` menggunakan sub-filter segmented pill:
     - **`Shift Berjalan` (Default):** Hanya menampilkan transaksi yang dilunasi pada shift kasir saat ini. Angka ringkasan dan total omzet rupiah di header murni menghitung pencapaian shift kasir berjalan (`currentShiftCompletedOrders.sumOf { it.order.total }`). Indikator jumlah pada tab utama `Riwayat Selesai (N)` juga murni menghitung transaksi shift aktif.
     - **`Shift Lalu (F36)`:** Menampilkan transaksi dari shift-shift sebelumnya lengkap dengan lencana `"Shift Lalu"`. Kasir tetap dapat mencari nota lama untuk kebutuhan cetak salinan struk (`PRT-05`) atau refund tunai terotorisasi PIN Manager sesuai ambang batas aturan F36 / COR-05.

---

## 2. Modul & File yang Diubah

1. **`app-cafe/src/main/java/id/tenang/kasir/cafe/MainActivity.kt`**
   - Mengikat `isShiftActive` ke `remember(isShiftActive)` pada `cafeNavItems`.
   - Menambahkan pengarah otomatis `LaunchedEffect(isShiftActive)`.
   - Menambahkan penjagaan ganda pada `onNavItemSelected`.
2. **`app-cafe/src/main/java/id/tenang/kasir/cafe/navigation/CafeNavHost.kt`**
   - Menambahkan pelindung *Empty State* `A3EmptyState` pada rute `CafeDestinations.PESANAN` dan `CafeDestinations.HABIS` jika `activeShift == null`.
3. **`core/data/src/main/kotlin/id/a3/core/data/local/dao/OrderDao.kt`**
   - Menambahkan kueri `@Transaction observeCompletedOrdersWithLinesForShift(shiftId: String)`.
   - Memperbarui `updatePaymentStatus` agar menyimpan `shiftId` pembayaran.
4. **`core/data/src/main/kotlin/id/a3/core/data/repository/OrderTransactionManager.kt`**
   - Menambahkan fungsi `observeCompletedOrdersForShift(shiftId: String)`.
   - Mengirimkan `effectiveShiftId` ke `orderDao.updatePaymentStatus`.
5. **`feature/cafe/src/main/kotlin/id/a3/feature/cafe/presentation/CafeMenuViewModel.kt`**
   - Menyediakan `allCompletedOrders` dan `completedOrders` untuk kompatibilitas reaktif penuh.
6. **`feature/cafe/src/main/kotlin/id/a3/feature/cafe/presentation/screen/CafeOpenOrdersScreen.kt`**
   - Menambahkan enum `CompletedShiftScope (CURRENT_SHIFT, PAST_SHIFTS)`.
   - Menambahkan sub-filter segmented pill `[Shift Berjalan] [Shift Lalu (F36)]`.
   - Mengisolasi perhitungan omzet dan jumlah nota di header sesuai lingkup shift yang dipilih.
   - Menyelaraskan teks *Empty State* untuk masing-masing kondisi.

---

## 3. Status Pengujian & Kualitas Kode

- **Unit Test Room & Data:**
  `./gradlew :core:data:testDebugUnitTest` $\rightarrow$ **100% LULUS**
- **Unit Test Cafe & Domain:**
  `./gradlew :feature:cafe:testDebugUnitTest` $\rightarrow$ **100% LULUS**
- **Unit Test Shift:**
  `./gradlew :feature:shift:testDebugUnitTest` $\rightarrow$ **100% LULUS**
- **Kompilasi APK Debug:**
  `./gradlew :app-cafe:assembleDebug` $\rightarrow$ **BUILD SUCCESSFUL**

---

## 4. Panduan untuk Sesi Baru

- **Status Repositori:** Bersih, seluruh perubahan telah diuji dan siap diuji langsung pada perangkat tablet Redmi Pad 2.
- **Langkah Verifikasi Manual di Hardware:**
  1. Pasang APK debug terbaru `app-cafe-debug.apk` di tablet.
  2. Lakukan buka shift di menu Shift dengan kas awal.
  3. Lakukan beberapa transaksi pesanan di Kasir hingga lunas.
  4. Buka menu Pesanan $\rightarrow$ tab *Riwayat Selesai*: pastikan muncul di tab *Shift Berjalan* dengan omzet akurat.
  5. Buka menu Shift $\rightarrow$ lakukan Tutup Shift & Cetak Laporan Z.
  6. Perhatikan setelah kembali ke layar Shift: pastikan menu Kasir, Pesanan, Menu Habis, dan Kas di Left Rail tampak redup dan tidak dapat diklik.
