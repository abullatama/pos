# SPESIFIKASI BUKU NOTA KERTAS CADANGAN & PROTOKOL INPUT SUSULAN
**a3coffeeshop & athialoundry**
*(Lampiran Wajib Paket Go-Live F44)*

---

## 1. TUJUAN & FUNGSI BUKU NOTA CADANGAN

Buku nota manual kertas adalah **jalur pengaman transaksi fisik (Fail-safe)** saat tablet kasir mengalami mati total, kerusakan layar, baterai habis, atau sedang dalam perbaikan teknis.

Dengan tersedianya buku nota manual bernomor urut tercetak, operasional penjualan di outlet tidak boleh berhenti sedetik pun sekalipun perangkat elektronik padam.

---

## 2. SPESIFIKASI FISIK CETAK BUKU NOTA

| Komponen | Spesifikasi Teknis |
|---|---|
| **Jenis Kertas** | Kertas NCR (*Carbonless Copy Paper*) 2 Rangkap (2-Ply) |
| **Warna Kertas** | • **Rangkap 1 (Top/Atas):** Putih (diberikan kepada tamu/pelanggan)<br>• **Rangkap 2 (Bottom/Bawah):** Kuning atau Merah Muda (arsip kasir untuk rekonsiliasi & audit) |
| **Ukuran Buku** | 1/4 Folio (sekitar 10,5 cm x 16,5 cm) atau 1/3 Folio landscape |
| **Penomoran** | Tercetak nomor urut otomatis (*Numbering Stamped*) 4 digit per buku, 50 lembar per buku |
| **Format Penomoran** | • **Cafe:** `M-CAF-0001` s/d `M-CAF-9999`<br>• **Laundry:** `M-LDR-0001` s/d `M-LDR-9999` |
| **Penyimpanan** | Wajib berada di laci kas kasir (bagian bawah rak uang) bersama 2 buah pulpen aktif |

---

## 3. TATA LETAK & CONTOH DESAIN LEMBAR NOTA MANUAL

```
+-----------------------------------------------------------------------+
|  A3 COFFEESHOP / ATHIA LOUNDRY                     No: M-CAF-0001     |
|  Jl. Trans Sulawesi Kelurahan Bintauna | WA: 0823-4778-5986           |
|-----------------------------------------------------------------------|
|  Tanggal : ___________________       Meja / Tamu : _________________  |
|  Jam     : ___________________       Kasir       : _________________  |
|-----------------------------------------------------------------------|
|  No | Menu / Layanan          | Qty / Kg | Harga Satuan |   Subtotal  |
|-----+-------------------------+----------+--------------+-------------|
|  1. |                         |          |              |             |
|  2. |                         |          |              |             |
|  3. |                         |          |              |             |
|  4. |                         |          |              |             |
|  5. |                         |          |              |             |
|-----------------------------------------------------------------------|
|  Diskon (Jika Ada) : Rp ___________________                           |
|  TOTAL PEMBAYARAN  : Rp ___________________                           |
|  Metode Pembayaran : [ ] Tunai (Diterima: Rp ________  Kembali: Rp __)|
|                      [ ] QRIS Statis (Ref No/Jam: ___________________)|
|-----------------------------------------------------------------------|
|  Tanda Terima Pelanggan,                  Kasir Bertugas,             |
|                                                                       |
|  ( ____________________ )                 ( ____________________ )    |
|                                                                       |
|  [ ] SUDAH DIINPUT KE POS | Tgl Input: _________ | Kasir Input: _____ |
+-----------------------------------------------------------------------+
```

---

## 4. PROSEDUR OPERASIONAL PENGISIAN NOTA MANUAL

1. **Pengeluaran Nota:** Buku nota hanya boleh dibuka jika tablet mati atau tidak dapat digunakan untuk memproses transaksi.
2. **Penulisan Wajib:**
   - Nama kasir bertugas.
   - Tanggal dan jam transaksi saat uang diterima.
   - Rincian item secara lengkap beserta jumlah kuantitas/berat cucian.
   - Metode bayar yang digunakan pelanggan (**Tunai** atau **QRIS Statis**).
3. **Penyerahan:**
   - **Lembar 1 (Putih):** Diserahkan ke pelanggan sebagai bukti transaksi sah.
   - **Lembar 2 (Kuning/Merah):** Tetap menempel di buku / dimasukkan ke laci kas sebagai dokumen sumber input susulan.
4. **Dilarang Merobek Lembar Arsip Bawah:** Lembar arsip wajib tetap terikat pada buku nota untuk keperluan audit fisik oleh Owner.

---

## 5. PROTOKOL INPUT SUSULAN KE APLIKASI A3 POS

Setelah tablet menyala dan aplikasi POS normal kembali, kasir wajib segera memindahkan data dari nota manual fisik ke dalam aplikasi dengan langkah-langkah berikut:

### Langkah 1: Pengecekan Shift Aktif
- Pastikan kasir telah membuka shift kasir di tablet (atau menggunakan shift berjalan).

### Langkah 2: Pembuatan Pesanan Susulan di POS
- Input seluruh item pesanan persis sesuai yang tercatat pada lembar nota manual fisik.
- Atur diskon jika pada nota manual terdapat potongan harga.

### Langkah 3: Pencantuman Penanda "Input Susulan" (Wajib Audit)
- Pada bagian Catatan Pesanan / Catatan Item, kasir **WAJIB** mengetik:
  ```
  Input Susulan Nota M-CAF-0001
  ```
  *(sesuaikan nomor nota manual fisik yang bersangkutan)*.
- Penandaan ini penting agar Owner di Web Dashboard dapat membedakan mana pesanan waktu-nyata dan mana pesanan hasil input susulan setelah gangguan teknis.

### Langkah 4: Penyelesaian Pembayaran Sesuai Fakta Fisik
- Pilih metode pembayaran sesuai yang tercatat di nota manual:
  - Jika **Tunai**: Masukkan nominal uang yang diterima saat nota manual dibuat.
  - Jika **QRIS Statis**: Pilih metode QRIS Statis dan selesaikan tanpa menarik uang dua kali.

### Langkah 5: Stempel & Pengarsipan Lembar Manual
- Pada kotak di dasar nota manual fisik:
  - Centang kotak `[x] SUDAH DIINPUT KE POS`.
  - Tulis tanggal, jam, dan paraf kasir yang melakukan input susulan.
- Lembar nota manual yang telah diinput disimpan rapi di folder arsip harian.

---

## 6. REKONSILIASI KAS SAAT TUTUP SHIFT

1. Saat melakukan **Hitung Kas Buta (TUP-02)** pada penutupan shift:
   - Uang tunai dari transaksi nota manual yang sudah diinput susulan akan otomatis terhitung ke dalam kas sistem.
   - Kasir menghitung seluruh uang fisik riil di laci kas (termasuk hasil penjualan nota manual).
2. Jika ada nota manual yang **belum sempat diinput susulan** sebelum shift ditutup:
   - Kasir **wajib menginput seluruh nota manual terlebih dahulu** sebelum menekan tombol Tutup Shift.
   - Jika tablet rusak permanen hingga akhir shift, hitung total nominal nota manual secara manual dan catat pada kolom alasan selisih kas fisik.
