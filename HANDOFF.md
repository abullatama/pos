# HANDOFF: Redesain UI/UX Daftar Pesanan Cafe (Header Detail Struk 2-Baris, Summary Strip Penjualan, & Standarisasi Separator)

## 1. Ringkasan Perubahan yang Selesai Dikerjakan

Sesi ini menyelesaikan perbaikan visual dan pengalaman pengguna (UI/UX) pada menu **Daftar Pesanan** aplikasi Cafe (`CafeOpenOrdersScreen.kt`) dengan menerapkan prinsip *design-taste-frontend*, *antislop-ui*, dan *ui-ux-pro-max*:

### A. Perbaikan Subtitle di Bawah Judul "Daftar Pesanan" (Panel Kiri)
* **Masalah Sebelumnya:**
  - Teks subtitle sebelumnya menggabungkan jumlah pesanan, lingkup shift, dan angka uang dalam satu baris: `"${currentShiftCompletedOrders.size} pesanan shift ini • ${formatRupiah(...)}"`.
  - Jumlah pesanan redundan dengan angka yang sudah ada di tab `Riwayat Selesai (X)` dan sub-filter `Shift Berjalan (X)`.
  - Nominal uang tidak memiliki label yang jelas, menciptakan kerancuan seolah-olah itu adalah total kas fisik laci.
  - Teks rentan terpotong (*ellipsis*) karena berebut ruang dengan tombol *"Pesanan Baru"*.
* **Solusi yang Diimplementasikan:**
  - Subtitle dibersihkan menjadi deskripsi fungsi halaman yang murni dan elegan:
    - Tab Meja Terbuka: `"Kelola pesanan meja aktif"`
    - Tab Riwayat Selesai: `"Riwayat transaksi selesai"`
  - Menghilangkan duplikasi teks jumlah pesanan dan karakter dot (`•`).

### B. Penambahan Summary Strip Penjualan Shift Terpilih (Opsi A)
* **Solusi yang Diimplementasikan:**
  - Membangun kartu ringkasan penjualan mandiri (*Summary Strip*) tepat di bawah sub-filter shift (*Shift Berjalan / Shift Sebelumnya*).
  - Menggunakan container `Surface` dengan sudut melengkung 10dp, background `colors.surfaceAlt`, dan border halus `0.8dp Color(0xFFE5E7EB)`.
  - Menampilkan label semantik yang jelas: `"Total Penjualan Shift Ini"` atau `"Total Penjualan Shift Lalu"` dan nilai rupiah yang terformat tebal (`fontWeight = FontWeight.Bold`) berwarna aksen (`colors.accent`).
  - Nilai penjualan kini relevan langsung dengan daftar struk yang sedang dilihat tanpa mengotori judul halaman.

### C. Redesain Header Detail Pesanan Selesai Menjadi 2 Baris (Panel Kanan)
* **Masalah Sebelumnya:**
  - Seluruh elemen identitas struk (ikon status, nomor struk, nama meja, tanggal panjang) dan 3 tombol aksi (*Cetak Salinan*, *Void*, *Refund Tunai*) dipaksakan berada dalam satu baris horizontal (`Row` dengan `Arrangement.SpaceBetween`).
  - Pada layar tablet landscape (terutama saat teks tanggal panjang), teks dan tombol saling bertabrakan, bergesekan, atau terpotong.
* **Solusi yang Diimplementasikan:**
  - Memisahkan **Identitas Transaksi** dan **Aksi Transaksi** menjadi **2 baris independen** (*zero-collision layout*):
    - **Baris 1 (Identitas & Status Transaksi):**
      - Kiri: Lingkaran ikon status (44dp, ukuran ikon 22dp terpusat) + Kolom judul struk (`Struk #${order.displayNumber}`) dan keterangan meja serta tanggal (`"$tableOrType - $dateStr"`).
      - Kanan: Badge status transaksi (`A3StatusChip`: *Lunas*, *Refund*, atau *Void*).
    - **Baris 2 (Toolbar Tombol Aksi):**
      - Diletakkan di bawah Baris 1 dengan *vertical spacing* 14dp.
      - Berisi tombol-tombol aksi tertata rapi secara horizontal (*spacing* 8dp, tinggi 38dp, sudut melengkung 8dp, padding 14dp x 8dp):
        1. **Cetak Salinan:** Background `surfaceAlt`, ikon `Print` 16dp, teks aksen hijau tebal.
        2. **Void (Kondisional):** Background `surfaceAlt`, ikon `Cancel` 16dp, teks abu-abu semi-bold.
        3. **Refund Tunai (Kondisional):** Background merah lembut (`0xFFFFEBEE`), border merah muda (`0xFFFFCDD2`), ikon `MoneyOff` 16dp, teks merah tebal.
  - Membersihkan duplikasi chip status di bagian bawah struk agar informasi bagian bawah fokus pada total pembayaran.

### D. Standarisasi Separator (Eliminasi Karakter Dot / Bullet '•')
* Mengganti seluruh karakter `•` menjadi `-` pada:
  - Header detail pesanan terbuka (`"Pesanan #${order.displayNumber} - Terbuka - $detailDuration"`).
  - Kartu riwayat pesanan selesai panel kiri (`"$tableOrType - $timeFormatted"`).
  - Header detail pesanan selesai panel kanan (`"$tableOrType - $dateStr"`).
  - Memperpendek format tanggal menjadi lebih ringkas: `"dd MMM yyyy, HH:mm 'WIB'"` (contoh: `25 Okt 2026, 14:30 WIB`).

---

## 2. Modul & File yang Diubah

1. **`feature/cafe/src/main/kotlin/id/a3/feature/cafe/presentation/screen/CafeOpenOrdersScreen.kt`**
   - Menambahkan import `androidx.compose.foundation.BorderStroke`.
   - Mengubah teks subtitle di bawah judul "Daftar Pesanan".
   - Menambahkan komponen Summary Strip Total Penjualan Shift Terpilih.
   - Merombak `CompletedOrderDetailView` menjadi struktur 2 baris (Baris 1: info + status chip, Baris 2: toolbar aksi).
   - Mengganti seluruh separator `•` menjadi `-`.
   - Membersihkan duplikasi status chip di footer rincian keuangan struk selesai.
2. **`progress.md`**
   - Menambahkan catatan progres untuk redesain UI/UX Daftar Pesanan Cafe.
3. **`HANDOFF.md`**
   - Mendokumentasikan perubahan visual dan panduan untuk sesi berikutnya.

---

## 3. Status Pengujian & Kualitas Kode

- **Unit Test Cafe:**
  `./gradlew :feature:cafe:testDebugUnitTest` $\rightarrow$ **BUILD SUCCESSFUL** (68 tasks, 100% lulus).
- **Compile APK Debug:**
  `./gradlew :app-cafe:assembleDebug` $\rightarrow$ **BUILD SUCCESSFUL** (210 tasks, 100% sukses).
- **Audit Karakter:**
  `grep '•'` pada `CafeOpenOrdersScreen.kt` $\rightarrow$ **0 matches** (seluruh dot pemisah telah bersih).

---

## 4. Panduan untuk Memulai Sesi Baru

- **Status Git:** Perubahan telah siap di-commit dan diverifikasi.
- **Rekomendasi Uji Visual di Hardware Fisik (Redmi Pad 2):**
  1. Jalankan aplikasi Cafe di tablet.
  2. Buka menu **Daftar Pesanan** (ikon kwitansi di rail kiri).
  3. Perhatikan subtitle di bawah judul: terlihat bersih tanpa teks angka yang berhimpitan dengan tombol *Pesanan Baru*.
  4. Masuk ke tab **Riwayat Selesai**: periksa tampilan *Summary Strip* total penjualan shift di bawah pill filter shift.
  5. Pilih salah satu transaksi selesai: perhatikan header panel kanan sekarang memiliki 2 baris terpisah (nama meja & waktu berpemisah `-` di baris atas sejajar dengan status Lunas, dan tombol Cetak Salinan, Void, Refund tertata rapi di baris bawahnya tanpa ada teks yang terpotong/bertabrakan).
