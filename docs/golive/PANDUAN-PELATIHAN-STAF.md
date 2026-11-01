# KURIKULUM & PANDUAN PELATIHAN STAF KASIR (60 MENIT)
**AA Coffeeshop & Athia Laundry House**
*(Persiapan Go-Live Hari Pertama — Spek 18)*

Dokumen ini adalah silabus pelatihan praktis 1 jam bagi seluruh kasir dan staf operasional sebelum hari pertama penggunaan sistem A3 POS.

---

## RUNDOWN PELATIHAN (TOTAL 60 MENIT)

| Waktu | Modul | Topik Utama | Praktik Kasir |
|---|---|---|---|
| 15 Menit | **Modul 1** | Buka Shift & Modal Kas Laci | Menghitung uang modal Rp200.000 per pecahan di tablet |
| 20 Menit | **Modul 2** | Transaksi POS & Pembayaran | Input menu/layanan, diskon, Tunai & kembalian, QRIS soundbox |
| 15 Menit | **Modul 3** | Operasional Khusus Laundry | Timbang kg/pcs, cetak tag, scan status, serah terima, komplain |
| 10 Menit | **Modul 4** | Tutup Shift Buta & SOP Darurat | Hitung kas buta, Laporan Z 58 mm, simulasi tablet mati |

---

## RINCIAN MODUL PELATIHAN

### MODUL 1: BUKA SHIFT & PENGHITUNGAN MODAL KAS (15 Menit)

1. **Konsep Shift Mandiri per Tablet:**
   - 1 tablet hanya menjalankan 1 shift aktif pada satu waktu (SHF-03).
   - Kasir login menggunakan PIN masing-masing (4 digit).
2. **Penghitungan Modal Awal (Float):**
   - Modal standar laci kas adalah **Rp 200.000** (OQ-06).
   - Buka layar **Buka Shift (S04)**.
   - Hitung fisik uang di laci kas per pecahan: Rp100.000, Rp50.000, Rp20.000, Rp10.000, Rp5.000, Rp2.000, Rp1.000, dan koin.
   - Masukkan jumlah lembar/keping ke form stepper taktil di tablet.
3. **Validasi Selisih Modal:**
   - Jika total hitungan fisik berbeda dari Rp 200.000, wajib mengisi alasan selisih kas awal (SHF-04).
   - Tekan tombol **Buka Shift & Masuk Menu Kasir**.

---

### MODUL 2: TRANSAKSI POS & PEMBAYARAN TERPADU (20 Menit)

1. **Navigasi Layar Kasir:**
   - Left Navigation Rail 76 dp: Beranda, Kasir POS, Pesanan Meja, Menu Habis, Shift, Kas Keluar, Admin.
   - Pemilihan kategori menu cepat & pencarian produk (⌘K / ketik nama).
2. **Modal Verifikasi Pesanan (C02):**
   - Setiap menu yang diketuk akan membuka modal verifikasi:
     - Pemilihan varian (misal: Panas / Dingin).
     - Pemilihan add-on (misal: Extra Shot, Sirup Vanilla).
     - Catatan barista/dapur (misal: "Less sugar, gula dipisah").
     - Stepper kuantitas.
3. **Penerapan Diskon (C06):**
   - Diskon preset: 5%, 10%, Rp 5.000 (langsung teraplikasi tanpa PIN).
   - Diskon manual / komplimen: wajib otorisasi PIN Manager (Dialog S14) dan memilih alasan audit tetap (COR-01).
4. **Alur Pembayaran Tunai (S06):**
   - Kasir memasukkan nominal uang yang diterima dari tamu (atau gunakan tombol cepat: Uang Pas, Rp 50.000, Rp 100.000).
   - Sistem menghitung nilai kembalian secara otomatis dan akurat.
   - Laci kas terbuka otomatis dan struk transaksi 58 mm tercetak.
5. **Alur Pembayaran QRIS Statis (S07):**
   - Tamu memindai kode QRIS Statis di meja kasir.
   - **Wajib verifikasi suara Soundbox Toko** atau konfirmasi mutasi masuk dari Owner/Manager sebelum menekan tombol "Konfirmasi Pembayaran Diterima".

---

### MODUL 3: OPERASIONAL KHUSUS LAUNDRY (15 Menit)

1. **Pendaftaran & Pencarian Pelanggan (L02):**
   - Cari berdasarkan nomor WhatsApp atau nama pelanggan.
   - Buat pelanggan baru (nomor dinormalisasi otomatis ke format 08...).
2. **Input Order Baru (L03):**
   - Pilih layanan kiloan atau satuan.
   - Timbang pakaian di timbangan fisik, masukkan berat ke tablet (kelipatan 0,5 kg, minimal 1,0 kg).
   - Masukkan rincian potong pakaian (pcs) bersama pelanggan untuk verifikasi jumlah baju.
   - Aktifkan saklar **Express** jika pelanggan meminta layanan kilat.
   - Pilih opsi pembayaran: **Bayar Sekarang**, **Uang Muka (DP)**, atau **Bayar Nanti (Saat Ambil)**.
3. **Pencetakan & Pemasangan Tag QR Cucian (F21):**
   - Struk thermal mencetak tag pakaian berisi nomor nota besar, nama pelanggan, dan QR code tanpa harga.
   - Sematkan tag thermal pada keranjang/kantong cucian menggunakan peniti tag.
4. **Pemindaian QR Tag untuk Mengubah Status (L07):**
   - Arahkan kamera tablet ke QR tag cucian.
   - Status bergerak satu arah: **Diterima -> Diproses -> Siap -> Diambil**.
   - Sistem mencatat timestamp setiap transisi status.
5. **Penyerahan Cucian & Proteksi Belum Lunas (F22, F24):**
   - Cucian hanya boleh diserahkan jika tagihan telah lunas.
   - Jika masih ada sisa tagihan dan pelanggan belum bayar, penyerahan cucian **wajib diotorisasi PIN Manager**.
   - Pengambilan bertahap/sebagian dicatat per baris pakaian (`laundry_pickups`).
6. **Cuci Ulang Komplain (F23):**
   - Jika pelanggan komplain hasil cucian (noda belum hilang / bau):
   - Tekan tombol **Cuci Ulang**, pilih alasan tetap, dan otorisasi PIN Manager.
   - Status cucian kembali ke **Diproses** tanpa membuat tagihan baru ke pelanggan.

---

### MODUL 4: TUTUP SHIFT BUTA & SOP DARURAT (10 Menit)

1. **Prinsip Hitung Kas Buta (TUP-02):**
   - Sistem **TIDAK PERNAH** membocorkan angka penjualan sistem sebelum kasir selesai menghitung kas fisik.
   - Kasir menghitung seluruh uang tunai di laci kas lembar demi lembar per pecahan (Tahap 1 S09).
2. **Kunci Hitungan & Evaluasi Selisih K1 (Tahap 2 S10):**
   - Kasir mencentang kotak persetujuan kunci hitungan.
   - Angka penjualan sistem dibuka.
   - Rumus K1: `Kas Seharusnya = Kas Awal + Penjualan Tunai + DP Tunai - Kas Keluar - Refund Tunai`.
   - Toleransi selisih kas adalah **Rp 2.000** (OQ-07). Jika selisih melebihi toleransi, wajib mengisi alasan selisih.
   - Kasir dilarang kembali mengubah hitungan kecuali dengan otorisasi PIN Manager (Hitung Ulang Kas).
3. **Alokasi Kas & Cetak Laporan Z (Tahap 3 S11):**
   - Tentukan uang yang ditinggal di laci kas untuk shift berikutnya (standar Rp 200.000).
   - Tentukan uang yang disetor ke Owner.
   - Tekan **Tutup Shift & Cetak Laporan Z**. Struk Laporan Z 58 mm keluar dari printer thermal.
4. **SOP Darurat (Simulasi Cepat):**
   - Apa yang dilakukan jika tablet mati? -> Buka buku nota manual NCR di laci kas.
   - Apa yang dilakukan jika printer mati? -> Lanjut transaksi, cetak ulang salinan nanti.
   - Apa yang dilakukan jika Wi-Fi mati? -> Tetap jualan, data aman di outbox, JANGAN LOGOUT / RESET.
