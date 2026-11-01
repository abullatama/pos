# STANDAR OPERASIONAL PROSEDUR (SOP) DARURAT KASIR
**AA Coffeeshop & Athia Laundry House**
*(Wajib Dicetak Ukuran A4 dan Ditempel di Samping / Atas Meja Kasir)*

Dokumen ini adalah instruksi kerja wajib bagi seluruh kasir dan staf saat menghadapi gangguan perangkat keras, jaringan, atau sistem selama jam operasional.

---

## 4 SKENARIO DARURAT UTAMA

### 1. TABLET MATI, HANG, ATAU RUSAK
1. **Ambil Buku Nota Manual Cadangan (NCR 2 Rangkap)** yang tersimpan di bawah laci kas.
2. Tulis secara jelas:
   - Nomor Nota Manual (urutan tidak boleh melompat).
   - Tanggal & Jam transaksi.
   - Nama Tamu / No. Meja (Cafe) atau No. HP & Nama Pelanggan (Laundry).
   - Rincian pesanan/layanan, jumlah (qty/kg/pcs), dan harga.
   - Total nominal dan metode pembayaran (**Tunai** atau **QRIS Statis**).
3. **Serahkan lembar Putih (Asli)** kepada tamu/pelanggan.
4. **Simpan lembar Merah/Kuning (Arsip)** di laci kas.
5. **Saat tablet menyala kembali:**
   - Kasir wajib melakukan **INPUT SUSULAN** ke aplikasi POS satu per satu.
   - Pada kolom Catatan pesanan, ketik wajib: `Input Susulan Nota M-XXXX`.
   - Beri stempel atau paraf `SUDAH DIINPUT KE SISTEM` pada lembar nota manual fisik.

---

### 2. PRINTER KASIR MATI, RUSAK, ATAU KERTAS HABIS
1. **Transaksi PENJUALAN TETAP DILANJUTKAN.** Jangan menolak pelanggan.
2. Uang Tunai atau bukti bayar QRIS Statis tetap diterima seperti biasa.
3. Aplikasi A3 POS memiliki fitur **Antrean Cetak Otomatis (F27)**: transaksi tetap tersimpan aman di sistem.
4. **Ganti kertas roll thermal 58 mm** atau nyalakan ulang printer Bluetooth.
5. Begitu printer kembali terhubung, sistem akan mencetak antrean yang tertunda secara otomatis.
6. Jika struk pelanggan tetap dibutuhkan segera:
   - Buka tab **Riwayat Selesai** di POS.
   - Pilih nomor transaksi pesanan.
   - Tekan tombol **Cetak Ulang Struk (Salinan)**.

---

### 3. INTERNET ATAU WI-FI TOKO PUTUS / MATI
1. **Aplikasi A3 POS beroperasi secara OFFLINE PENUH (Offline-First).**
2. Kasir tetap dapat:
   - Membuat order pesanan baru.
   - Menerima pembayaran Tunai & menghitung kembalian.
   - Menerima pembayaran QRIS Statis.
   - Mencetak struk pelanggan & tiket dapur/tag cucian.
   - Buka shift dan Tutup shift (Laporan Z tetap dicetak lokal).
3. **LARANGAN KERAS SAAT OFFLINE:**
   - **DILARANG LOGOUT** dari aplikasi POS.
   - **DILARANG MENGHAPUS DATA APLIKASI (Clear Storage/Data).**
   - **DILARANG RESET TABLET KE SETELAN PABRIK.**
4. Seluruh transaksi tersimpan aman di antrean Outbox lokal tablet (Room Database).
5. Begitu internet/Wi-Fi menyala kembali, sistem akan mengirim seluruh transaksi ke server secara otomatis tanpa ada data yang hilang.

---

### 4. NOTIFIKASI WHATSAPP GAGAL ATAU SERVER FONNTE DOWN *(Khusus Laundry)*
1. Pelanggan tetap dilayani dan tag fisik tetap dicetak serta disematkan pada pakaian.
2. Jika pelanggan menanyakan notifikasi WhatsApp yang belum masuk:
   - Buka menu **Cucian (Daftar Pesanan)** di POS Laundry.
   - Buka kartu rincian cucian pelanggan terkait.
   - Periksa kartu status notifikasi WhatsApp server.
   - Tekan tombol hijau **Kirim via WA (Manual)**.
   - Aplikasi akan membuka tautan WhatsApp otomatis ke nomor pelanggan yang bersangkutan.

---

## 3 ATURAN EMAS KASIR (ANTI-FRAUD & INTEGRITAS)

1. **REFUND SELALU TUNAI**
   - Segala bentuk retur atau pengembalian dana (baik pesanan asal dibayar Tunai maupun QRIS Statis) **wajib dikembalikan dalam bentuk Uang Tunai dari laci kas** dan diotorisasi PIN Manager. Dilarang transfer balik non-tunai.
2. **VERIFIKASI SOUNDBOX QRIS STATIS**
   - Sebelum menyerahkan pesanan atau nota lunas, kasir **wajib mendengar bunyi notifikasi Soundbox Toko** atau konfirmasi mutasi masuk dari Owner/Manager. Jangan hanya melihat layar HP pelanggan.
3. **HITUNG KAS BUTA (BLIND COUNT)**
   - Saat tutup shift, kasir wajib menghitung uang fisik di laci secara mandiri lembar demi lembar sebelum sistem menampilkan angka penjualan sistem. Dilarang menebak atau menyamakan dengan angka sistem.

---

## KONTAK DARURAT & ESKALASI CEPAT

| Peran | Nama | Nomor WhatsApp / Telepon |
|---|---|---|
| **Owner / Pemilik** | Pak Owner | `0823-4778-5986` |
| **Manager Operasional** | Manager On Duty | Sesuai Shift Berjalan |
| **Dukungan Teknis IT** | Tim Support A3 | `0812-3456-7890` |

*SOP ini berlaku sejak hari pertama Go-Live dan wajib dipatuhi oleh seluruh staf bertugas.*
