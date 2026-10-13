## 17. Skenario uji dan kriteria lulus

MVP dianggap lulus bila semua skenario wajib di bawah berhasil di tablet dan printer asli, dan masa paralel tidak menemukan selisih yang tak terjelaskan. Logika uang diuji otomatis (unit test dengan test vector bersama), sedangkan printer, kiosk, dan perilaku background diuji manusia di perangkat sungguhan. Beban uji di M2 dan M3 dua kali lipat karena dua usaha.

**Skenario wajib**

| No | Skenario | Hasil yang diharapkan | Diuji |
| --- | --- | --- | --- |
| 1 | Hitung total cafe dan laundry sesuai kasus uji bagian 7 | Hasil app dan backend identik sampai rupiah | Otomatis |
| 2 | Offline penuh selama beberapa jam, lalu online | Semua transaksi terkirim urut, tanpa duplikat dan tanpa yang hilang | Tablet asli |
| 3 | Bundel dikirim dua kali karena balasan hilang | Server membalas Duplikat, data tidak ganda | Otomatis |
| 4 | Bootstrap terputus di tengah | Dilanjutkan dari bagian terakhir, POS tetap terkunci sampai selesai | Tablet asli |
| 5 | Login pertama online, lalu buka kunci offline dengan PIN | Berhasil selama sesi valid | Tablet asli |
| 6 | PIN salah 3 kali | Akun terkunci sementara | Otomatis |
| 7 | Printer putus lalu tersambung kembali saat transaksi berjalan | Transaksi aman, cetakan antre dan terkirim, cetak ulang berlabel SALINAN | Printer asli |
| 8 | Tablet mati saat keranjang berisi | Draft keranjang kembali saat dibuka | Tablet asli |
| 9 | Pembayaran tunai dengan pembulatan | Kembalian benar, selisih pembulatan tercatat | Otomatis |
| 10 | QRIS Statis dengan konfirmasi manual, termasuk saat offline | Tercatat dengan kasir dan shift, muncul di daftar cek saat tutup shift | Tablet asli |
| 11 | Rekonsiliasi QRIS Statis harian di web | Selisih terhadap angka merchant ditandai dan masuk pengecualian | Otomatis |
| 12 | Void item setelah dikirim ke dapur dan refund transaksi lunas | PIN manager diminta, catatan pembalik terbuat, kas shift terkoreksi | Otomatis dan tablet asli |
| 13 | Laundry: scan tag, ubah status, tanda Siap | Status tercatat dengan pelaku, WhatsApp terkirim oleh backend | Tablet asli |
| 14 | Laundry: ambil cucian belum lunas | Ditolak, kecuali PIN manager, dan masuk laporan pengecualian | Tablet asli |
| 15 | Fonnte gagal | Ikon status gagal, tombol Kirim via WA manual tersedia | Tablet asli |
| 16 | Tutup shift dengan selisih | Alasan wajib, hitungan terkunci, laporan Z tercetak | Tablet asli |
| 17 | Tutup shift saat offline | Tertutup lokal dengan peringatan, sinkron setelah online | Tablet asli |
| 18 | Jam tablet salah | Peringatan muncul, laporan memakai jam server | Tablet asli |
| 19 | Update app dengan data lama | Migrasi Room berhasil, outbox utuh | Tablet asli |
| 20 | Kiosk | Kasir tidak bisa membuka app lain atau menghapus data | Tablet asli |
| 21 | Perangkat dicabut dari web | Permintaan ditolak `DEVICE_REVOKED`, layar terkunci, data lokal tidak dihapus | Tablet asli |
| 22 | Pemulihan backup ke staging | Data pulih dan total transaksi cocok | Manual |
| 23 | Staf laundry memindai dan mengubah status sebelum kasir membuka shift, lalu kasir membuka shift | Status tercatat tanpa shift dan diterima server; shift kasir tidak terganggu | Tablet asli |
| 24 | Pengambilan sebagian cucian, lalu sisanya diambil di hari lain | Dua catatan pengambilan; status Siap sampai pengambilan terakhir, lalu Diambil | Otomatis dan tablet asli |
| 25 | Bundel dengan total yang berbeda dari hitungan server | Diterima dengan tanda TOTAL\_MISMATCH sesuai angka tablet, masuk karantina, dan muncul di laporan penjualan | Otomatis |
| 26 | Event berumur lebih dari 7 hari atau jam tablet melenceng satu jam | Diterima dan ditandai OLD\_EVENT atau CLOCK\_SKEW, tidak pernah ditolak karena umur | Otomatis |
| 27 | Ganti kasir saat satu meja cafe masih makan | Shift lama tertutup, pesanan terbuka berpindah ke shift berikutnya, dan pembayarannya tercatat di shift yang menerima | Tablet asli |
| 28 | Refund cucian shift sebelumnya, dibayar QRIS, saat rekonsiliasi harian | Refund tunai mengurangi laci shift berjalan; total QRIS bruto tidak berubah dan cocok dengan laporan merchant | Otomatis dan tablet asli |

**Kriteria lulus**

1. Semua skenario wajib lulus di tablet dan printer yang akan dipakai.
2. Uji offline 24 jam: jumlah dan total transaksi di tablet sama dengan di server.
3. Selisih kas nol atau dalam toleransi selama masa paralel.
4. Masa paralel 1-2 minggu di kedua usaha dengan pencatatan lama berjalan bersamaan, dan setiap selisih bisa dijelaskan.
5. Staf dilatih sekitar satu jam dan prosedur darurat tercetak serta ditempel.

**Matriks uji per aplikasi**

- Skenario rumus dan sync (1, 2, 3, 6, 9) diuji untuk kedua aplikasi.
- Skenario bootstrap (4) diuji untuk kedua aplikasi. Skenario cafe (12 untuk alur cafe) hanya di aplikasi cafe; skenario laundry (13 dan 14) hanya di aplikasi laundry.
- Skenario perangkat (printer, kiosk, offline, update) dijalankan di tablet masing-masing aplikasi.
- Uji dependensi modul: build gagal bila `:feature:cafe` bergantung pada `:feature:laundry`, atau bila modul aplikasi memuat logika bisnis.
