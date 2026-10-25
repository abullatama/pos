# HANDOFF: Perbaikan UI/UX Menu Tutup Shift Kasir (Unified Vertical Scroll, Eliminasi Kode Spek, & Proteksi Tinggi Tombol)

## 1. Ringkasan Perubahan yang Selesai Dikerjakan

Sesi ini menyelesaikan perbaikan pengalaman pengguna (UI/UX) dan tata letak visual pada wizard **Penutupan Shift Kasir** (`CloseShiftScreen.kt` & `CloseShiftViewModel.kt`) di aplikasi Cafe dan Laundry sesuai prinsip *antislop-ui* dan *ui-ux-pro-max*:

### A. Eliminasi Menyeluruh Kode Spesifikasi Teknis (UI & ViewModel)
* **Masalah Sebelumnya:**
  - Kode spesifikasi internal developer (`TUP-01`, `TUP-02`, `TUP-03`, `TUP-04`, `TUP-06`, `TUP-08`) bocor ke antarmuka pengguna pada beberapa elemen:
    - Label kolom alasan: `"Alasan Selisih Kas (Wajib diisi - TUP-03):"`
    - Peringatan hitung buta: `"Hitung Kas Buta (TUP-02)"`
    - Header verifikasi QRIS: `"Verifikasi QRIS Statis (TUP-04)"`
    - Header alokasi kas: `"Catatan Alokasi Kas (TUP-06)"`
    - Pesan error validasi ViewModel: `"(TUP-03)"` dan `"(TUP-06)"`
    - Banner offline alarmis: `"JANGAN RESET TABLET (TUP-08)"`
* **Solusi yang Diimplementasikan:**
  - Seluruh kode teknis spek dihapus dari antarmuka pengguna.
  - Teks **"Wajib diisi"** pada alasan selisih kas tetap dipertahankan dengan format bersih: **`Alasan Selisih Kas (Wajib diisi):`**. Ini sangat penting agar kasir memahami mengapa tombol *"Lanjut ke Alokasi Kas"* terkunci saat selisih kas fisik melebihi batas toleransi wajar (> Rp 2.000).
  - Pesan error validasi ViewModel dibersihkan menjadi kalimat bahasa Indonesia yang ramah pengguna.

### B. Tahap 2: Unified Vertical Scroll pada Kartu Evaluasi Selisih Kas
* **Masalah Sebelumnya:**
  - Kartu Evaluasi Selisih Kas di sisi kanan (40%) sebelumnya memisahkan konten formulir atas dan tombol aksi bawah secara kaku (`Arrangement.SpaceBetween`).
  - Ketika selisih kas melebihi toleransi dan kolom alasan muncul, bagian bawah input teks langsung menempel (berdempetan 0 dp) dengan tombol *"Lanjut ke Alokasi Kas"*.
* **Solusi yang Diimplementasikan:**
  - Mengubah seluruh kartu Evaluasi Selisih Kas menjadi **satu kontainer scroll vertikal utuh** (`Modifier.verticalScroll(rememberScrollState())`).
  - Rincian arus kas, status toleransi, input alasan selisih kas, dan kedua tombol aksi (*"Lanjut ke Alokasi Kas"* serta *"Hitung Ulang Kas"*) mengalir bersama secara alami.
  - Terdapat jarak pemisah teratur (`Spacer(height = 4.dp)` dan `spacedBy(8.dp)`) antara input dan tombol aksi, sehingga elemen tidak lagi berdempetan atau terpotong.

### C. Tahap 3: Resolusi Tombol Pipih & Unified Vertical Scroll Kartu Alokasi Kas
* **Masalah Sebelumnya:**
  - Tombol aksi *"Tutup Shift & Cetak Laporan Z"* dan *"Kembali ke Selisih"* menjadi sangat pipih / terkompresi karena terdesak oleh dua card di atasnya:
    1. Card Validasi Alokasi yang tebal dan memanjang ketika input nama penerima muncul.
    2. Banner Offline tebal berwarna oranye yang ditempatkan secara permanen di wadah tombol aksi bawah.
* **Solusi yang Diimplementasikan:**
  - Menerapkan arsitektur **Unified Vertical Scroll** yang sama pada kartu Catatan Alokasi Kas: seluruh form input, ringkasan keseimbangan alokasi, catatan offline, dan tombol-tombol aksi berada dalam satu aliran scroll utuh.
  - **Proteksi Tinggi Tombol:** Kedua tombol aksi dijamin selalu memiliki tinggi penuh standar `56 dp` yang kokoh dan mudah disentuh (tidak akan pernah pipih atau tertekan).
  - **Redesain Card Alokasi:** Menyederhanakan kartu alokasi menjadi strip status ramping (*compact balance indicator*) yang menampilkan status keseimbangan (*"Alokasi Sesuai Kas Fisik"* / *"Alokasi Belum Seimbang"*) beserta nominal kekurangan/kelebihan kas secara real-time.
  - **Relokasi Catatan Offline:** Menghilangkan banner alarmis dari wadah tombol aksi dan mengubahnya menjadi catatan kaki informatif yang tenang di dalam formulir (*"Data shift dan Laporan Z tersimpan lokal dan disinkronkan otomatis saat online."*).

---

## 2. File yang Dimodifikasi

1. **`feature/shift/src/main/kotlin/id/a3/feature/shift/presentation/CloseShiftScreen.kt`**
   - Menghapus seluruh tag spek internal `TUP-xx` pada banner order terbuka, peringatan hitung buta, verifikasi QRIS, label alasan selisih, dan judul alokasi kas.
   - Mengubah kartu Evaluasi Selisih Kas (Tahap 2) menjadi satu kesatuan scroll vertikal utuh bersama tombol-tombol aksi.
   - Mengubah kartu Catatan Alokasi Kas (Tahap 3) menjadi satu kesatuan scroll vertikal utuh bersama tombol-tombol aksi.
   - Menyederhanakan strip validasi alokasi kas dan merelokasi catatan offline dari wadah tombol aksi.
2. **`feature/shift/src/main/kotlin/id/a3/feature/shift/presentation/CloseShiftViewModel.kt`**
   - Membersihkan teks error validasi dari embel-embel `(TUP-03)` dan `(TUP-06)`.
3. **`progress.md`**
   - Menambahkan catatan progres sesi penutupan shift ke dalam tabel milestone.
4. **`HANDOFF.md`**
   - Dokumentasi lengkap hasil perbaikan, verifikasi, dan panduan sesi baru.

---

## 3. Status Pengujian & Kualitas Kode

- **Unit Test Feature Shift:**
  `./gradlew :feature:shift:testDebugUnitTest` $\rightarrow$ **BUILD SUCCESSFUL** (100% lulus).
- **Compile APK Debug Cafe:**
  `./gradlew :app-cafe:assembleDebug` $\rightarrow$ **BUILD SUCCESSFUL** (100% lulus).
- **Compile APK Debug Laundry:**
  `./gradlew :app-laundry:assembleDebug` $\rightarrow$ **BUILD SUCCESSFUL** (100% lulus).

---

## 4. Panduan untuk Memulai Sesi Baru

- **Status Git:** Seluruh modifikasi telah diverifikasi, lolos uji, dan siap di-commit.
- **Rekomendasi Uji Visual di Tablet (Redmi Pad 2):**
  1. Jalankan aplikasi Cafe atau Laundry dengan shift aktif.
  2. Buka menu **Shift** $\rightarrow$ tekan **Tutup Shift**.
  3. **Tahap 1 (Hitung Kas Fisik):** Masukkan pecahan uang yang sengaja berselisih > Rp 2.000 (misal Rp 10.000 lebih sedikit dari penjualan).
  4. **Tahap 2 (Selisih & QRIS):** 
     - Perhatikan kolom *"Alasan Selisih Kas (Wajib diisi):"* muncul rapi tanpa kode `- TUP-03`.
     - Coba lakukan scroll pada kartu kanan: formulir dan tombol *"Lanjut ke Alokasi Kas"* mengalir bersama secara halus tanpa ada elemen yang saling menindih atau berdempetan.
     - Isi alasan selisih, lalu tekan *"Lanjut ke Alokasi Kas"*.
  5. **Tahap 3 (Alokasi & Laporan Z):**
     - Perhatikan kartu alokasi di sisi kiri: strip status alokasi tampil ramping dan elegan.
     - Masukkan nominal uang disetor sehingga kolom input *"Nama Penerima"* muncul.
     - Perhatikan tombol *"Tutup Shift & Cetak Laporan Z"* di bagian bawah: tombol tetap kokoh dengan tinggi 56 dp penuh dan tidak lagi pipih.
