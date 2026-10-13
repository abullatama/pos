## 4. Kebutuhan fungsional B: transaksi cafe dan laundry

**Transaksi cafe**

Status pesanan: Terbuka, Lunas, Dibatalkan.

| ID | Kebutuhan |
| --- | --- |
| CAF-01 | Kasir memilih menu, varian, add-on, dan catatan. Item masuk keranjang yang tersimpan otomatis di Room (draft), jadi aman bila app tertutup. |
| CAF-02 | Pilih tipe pesanan: dine-in (nomor meja) atau take-away (nama atau nomor antrean). |
| CAF-03 | Nomor order aman saat offline: kode tablet + urutan harian, contoh `C1-0007`. |
| CAF-04 | Tombol Kirim mencetak tiket dapur atau bar. Pesanan dine-in menjadi pesanan terbuka per meja dan dibayar saat selesai. |
| CAF-05 | Harga jual dan modal per porsi disalin ke baris transaksi saat item ditambahkan (snapshot). Perubahan harga di web tidak mengubah pesanan yang berjalan. |
| CAF-06 | Total dihitung lokal dengan rumus bagian 7, lalu divalidasi ulang oleh backend. |
| CAF-07 | Penambahan pesanan hanya mencetak item baru. Tombol Kirim dikunci sesaat setelah diketuk agar tidak tercetak ganda. |
| CAF-08 | Pesanan terbuka mendukung tambah item dan pindah meja. |
| CAF-09 | Menu dapat ditandai Habis secara manual dan penanda ikut tersinkron. Angka stok tidak pernah memblokir penjualan, dan MVP tidak memotong stok bahan per resep. |
| CAF-10 | Item dihapus bebas sebelum dikirim ke dapur. Setelah dikirim, pembatalannya adalah void (PIN manager, bagian 5). |

**Transaksi laundry**

Status cucian: Diterima, Diproses, Siap, Diambil.

| ID | Kebutuhan |
| --- | --- |
| LDR-01 | Cari pelanggan dengan nomor HP atau buat baru (nama dan nomor HP saja). Pelanggan baru dikirim sebagai event customer.created di bundel yang sama dengan ordernya. |
| LDR-02 | Pilih layanan: per kg (cuci + setrika, cuci saja, setrika saja) atau per pcs (selimut, bed cover, jas, sepatu). Input berat atau jumlah. |
| LDR-03 | Hitung jumlah pcs bersama pelanggan dan catat. Catatan khusus wajib untuk noda atau barang rapuh; foto opsional. |
| LDR-04 | Estimasi selesai terisi otomatis dari layanan (reguler atau express) dan dapat diubah. |
| LDR-05 | Pembayaran: lunas di muka, DP, atau bayar saat ambil (bagian 5). |
| LDR-06 | Cetak nota dan tag. Nomor order aman saat offline, contoh `L1-0012`. |
| LDR-07 | Harga dari master: minimal berat, pembulatan, dan tarif express. Kasir tidak menghitung manual. |
| LDR-08 | Status disimpan sebagai event log append-only (siapa, kapan). Staf scan QR tag, atau mengetik nomor order, untuk mengubah status dan untuk pengambilan. |
| LDR-09 | Status Siap memicu backend mengirim WhatsApp lewat Fonnte. Tombol Kirim via WA manual selalu tersedia sebagai cadangan. |
| LDR-10 | Penyerahan (Diambil) hanya bisa selesai bila status lunas. Bila belum lunas, butuh PIN manager dan kasusnya masuk laporan pengecualian. |
| LDR-11 | Pengambilan sebagian didukung dan dicatat per baris layanan (event laundry.picked\_up, tabel laundry\_pickups: jumlah atau potongan yang diambil, penanda terakhir, siapa, kapan). Order berstatus Diambil bila semua baris sudah ditandai terakhir; selama belum, sisanya tetap Siap. Pengambilan sebagian atas order belum lunas butuh PIN manager. |
| LDR-12 | Cuci ulang karena komplain: status kembali ke Diproses dengan alasan dan PIN manager, tanpa tagihan baru. |
| LDR-13 | Koreksi berat setelah nota tercetak butuh PIN manager dan masuk audit log. |
| LDR-14 | Cucian yang tidak diambil lebih dari N hari (Pengaturan) masuk daftar Belum diambil dan memicu pengingat WhatsApp. |
