# CHECKLIST PERSIAPAN HARI PERTAMA GO-LIVE
**a3coffeeshop & athialoundry**
*(Lampiran Pelaksanaan Milestone M4 — Spek 18)*

Gunakan checklist ini pada **H-1** (sehari sebelum pembukaan) dan **Hari-H Pagi** (sebelum kasir membuka shift pertama) untuk memastikan seluruh fasilitas fisik dan sistem siap 100%.

---

## 1. CHECKLIST PERLENGKAPAN FISIK & HARDWARE (H-1)

| No | Item Perlengkapan | Standar Kesiapan | Status |
|:--:|---|---|:--:|
| 1 | **Tablet Kasir Redmi Pad 2** | • Layar bersih, terpasang kokoh pada stand tablet counter.<br>• Kabel charger original terhubung ke stop kontak (baterai 100%).<br>• Layar terkunci pada aplikasi A3 POS (*Kiosk Mode LockTask*). | [ ] SIAP |
| 2 | **Printer Thermal iWare C58BT** | • Kabel power adaptor terhubung dan lampu indikator menyala.<br>• Terhubung via Bluetooth SPP ke tablet.<br>• Uji Cetak via Menu Admin berhasil mengeluarkan teks tajam 32 karakter. | [ ] SIAP |
| 3 | **Kertas Roll Thermal 58 mm** | • 1 roll terpasang rapi di dalam printer.<br>• **Minimal 5 roll cadangan** tersimpan di lemari kasir dalam kondisi kering. | [ ] SIAP |
| 4 | **Laci Kas (Cash Drawer RJ11)** | • Kabel RJ11 terhubung dari dasar laci kas ke port printer.<br>• Laci kas membuka otomatis saat transaksi tunai diselesaikan.<br>• Kunci fisik laci kas dipegang oleh Manager / Kasir utama. | [ ] SIAP |
| 5 | **Modal Awal Kas (Float Money)** | • Uang tunai **Rp 200.000** pecahan rapi tersusun di laci:<br>  - Rp 50.000 x 2 lembar = Rp 100.000<br>  - Rp 20.000 x 2 lembar = Rp 40.000<br>  - Rp 10.000 x 3 lembar = Rp 30.000<br>  - Rp 5.000 x 4 lembar = Rp 20.000<br>  - Uang koin kembalian Rp 500 / Rp 1.000 secukupnya. | [ ] SIAP |
| 6 | **Stand Akrilik QRIS Statis** | • Kode QRIS Statis toko bersih, tidak buram, terpasang di meja depan tamu.<br>• Perangkat **Soundbox QRIS** menyala, volume kencang, terhubung ke internet. | [ ] SIAP |
| 7 | **Buku Nota Manual Cadangan (NCR)** | • Minimal 1 buku nota 2 rangkap (50 set) bernomor urut.<br>• Tersimpan di bawah baki uang laci kas bersama **2 pulpen hitam**. | [ ] SIAP |
| 8 | **Lembar SOP Darurat Kasir (A4)** | • Dicetak berwarna/hitam-putih jelas ukuran A4.<br>• **Ditempel permanen** di dinding samping / partisi meja kasir. | [ ] SIAP |
| 9 | **Peniti / Tag Cucian *(Khusus Laundry)*** | • 1 kotak peniti anti-karat / tag fastener untuk menyematkan tag struk thermal ke kantong cucian pelanggan. | [ ] SIAP |

---

## 2. CHECKLIST SISTEM & KREDENSIAL PENGGUNA (H-1)

| No | Komponen Sistem | Parameter Pengujian | Status |
|:--:|---|---|:--:|
| 1 | **Status Perangkat di Web Owner** | Perangkat berstatus **ACTIVE** (bukan Pending atau Revoked) di backend Laravel. | [ ] SIAP |
| 2 | **Koneksi Jaringan & Sinkronisasi** | Indikator sinkron di TopBar berwarna **Hijau (Tersinkron)** atau **Kuning (Menunggu)**, bukan Merah (Error). | [ ] SIAP |
| 3 | **PIN Kasir & Manager** | • Seluruh kasir telah menghafal PIN 4-digit masing-masing.<br>• Manager On Duty memegang PIN Manager untuk otorisasi diskon/void/refund.<br>• PIN Owner tersimpan aman oleh pemilik usaha. | [ ] SIAP |
| 3a | **Versi Aplikasi & Kunci Layar** | • Versi terpasang **cafe 1.0.6 / laundry 1.0.10** (cek di Info aplikasi).<br>• Layar terkunci setelah 2 menit tanpa sentuhan; PIN kasir membuka kembali dan keranjang tetap utuh.<br>• Kasir bertugas login saat **internet menyala** (syarat buka kunci saat internet mati). | [ ] SIAP |
| 4 | **Katalog Menu & Layanan** | Seluruh menu kopi/makanan (Cafe) atau tarif kiloan/satuan/express (Laundry) telah sesuai dengan harga jual toko. | [ ] SIAP |

---

## 3. CHECKLIST OPERASIONAL HARI-H (PAGI HARI SEBELUM BUKA)

- [ ] Nyalakan tablet dan printer thermal iWare C58BT.
- [ ] Buka aplikasi POS (a3coffeeshop / athialoundry).
- [ ] Tekan tombol menu **Shift** -> Masuk ke layar **Buka Shift (S04)**.
- [ ] Masukkan nominal hitungan kas awal fisik Rp 200.000 per pecahan.
- [ ] Konfirmasi pembukaan shift; pastikan nama kasir muncul di bilah atas (TopBar).
- [ ] Lakukan 1 transaksi percobaan (Dry Run):
  - Buat pesanan contoh.
  - Pilih bayar Tunai.
  - Pastikan laci kas terbuka dan struk thermal tercetak sempurna.
  - Lakukan **Void Transaksi** dengan PIN Manager agar pembukuan kembali bersih.
- [ ] Outlet siap melayani pelanggan pertama!

---

## 4. CHECKLIST PENUTUPAN HARI-H (MALAM HARI SAAT TUTUP TOKO)

- [ ] Pastikan tidak ada pesanan meja yang masih menggantung / belum selesai.
- [ ] Buka menu **Shift** -> Tekan **Tutup Shift Kasir**.
- [ ] **Langkah 1:** Hitung uang fisik di laci lembar demi lembar per pecahan (Hitung Kas Buta).
- [ ] **Langkah 2:** Kunci hitungan, periksa evaluasi selisih kas sistem (toleransi Rp 2.000). Jika ada selisih, isi catatan audit.
- [ ] **Langkah 3:** Sisakan modal Rp 200.000 di laci kas untuk kasir esok hari, dan pisahkan uang setoran untuk Owner.
- [ ] Tekan tombol **Tutup Shift & Cetak Laporan Z**.
- [ ] Lampirkan struk Laporan Z bersama amplop uang setoran harian untuk diserahkan ke Owner.
- [ ] Pastikan tablet tetap terhubung ke charger listrik semalaman agar sinkronisasi dan backup latar belakang berjalan lancar.
