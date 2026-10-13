## 14. Web owner dan laporan

Web owner terdiri dari 16 layar yang dirancang agar rutinitas owner tidak lebih dari beberapa menit per hari: satu layar Beranda untuk peringatan, lalu layar khusus untuk pengeluaran, pengecualian, dan laba-rugi. Hak akses di bawah usulan awal dan perlu dikonfirmasi (bagian 19).

**Daftar layar dan hak akses**

| Layar | Isi | Owner | Manager |
| --- | --- | --- | --- |
| Beranda | Hari ini per usaha: penjualan bersih, jumlah transaksi, uang diterima per metode, shift aktif. Peringatan: selisih kas, tablet tidak sinkron, QRIS belum direkonsiliasi, pengeluaran belum dicatat, data belum lengkap | Penuh | Tanpa laba dan modal |
| Perangkat | Daftar tablet (status, versi, sinkron terakhir, outbox, baterai, printer), buat kode pendaftaran, setujui dan cabut, antrean bermasalah (lihat, coba lagi, ekspor) | Penuh | Lihat |
| Pengguna | Akun, peran, usaha berizin, reset PIN, nonaktifkan, riwayat status | Penuh | Tidak |
| Master data cafe | Menu, kategori, varian, add-on, harga, modal per porsi, tanda Habis | Penuh | Tanda Habis |
| Master data laundry | Layanan, tarif, express, berat minimum, kelipatan, estimasi, modal per kg | Penuh | Tidak |
| Pengaturan | Nilai Pengaturan (bagian 8), preset diskon, label metode bayar, jam buka, identitas struk, template WhatsApp, versi minimum | Penuh | Tidak |
| Shift | Daftar dan detail shift, laporan Z, tutup paksa dengan catatan, catatan penyesuaian setelah ditutup | Penuh | Lihat dan laporan Z |
| Transaksi | Pencarian order dan pembayaran, detail dengan lini masa event, tautan ke audit log | Penuh | Lihat |
| Laundry | Order aktif per status, belum tertagih, belum diambil, status kirim WhatsApp | Penuh | Lihat |
| Laporan | Penjualan harian, uang diterima per metode, margin estimasi, tren biaya, ekspor CSV | Penuh | Penjualan dan uang diterima |
| Pengecualian | Temuan dari laporan pengecualian, tinjau dengan catatan | Penuh | Lihat |
| Pengeluaran | Input cepat, pengeluaran berulang, daftar dan filter, foto nota | Penuh | Tidak |
| Stok akhir bulanan | Nilai stok akhir sekitar 10 bahan utama per periode | Penuh | Tidak |
| Laba-rugi | Per usaha dan gabungan, dengan banner kelengkapan | Penuh | Tidak |
| Rekonsiliasi QRIS | Input total QRIS Statis harian, perbandingan dengan total di app, status dan catatan | Penuh | Tidak |
| Audit log | Pencarian dan filter berdasarkan aktor, aksi, objek, dan waktu | Penuh | Tidak |

**Rutinitas yang dirancang**

1. Harian (sekitar 5 menit): buka Beranda, tinjau peringatan dan laporan Z kemarin, lalu isi total QRIS di Rekonsiliasi.
2. Mingguan (sekitar 10 menit): catat pengeluaran dan tinjau Pengecualian.
3. Bulanan (sekitar 15 menit): isi stok akhir, lihat laba-rugi, dan bandingkan dengan saldo kas dan bank.

**Persyaratan antarmuka**

- Responsif sehingga nyaman dipakai di HP owner. Bahasa Indonesia, format Rupiah (Rp1.234.567), dan tanggal sesuai lokal.
- Input pengeluaran maksimal tiga isian utama: kategori, nominal, dan usaha. Tanggal terisi hari ini, dan foto nota opsional lewat kamera HP.
- Tabel mendukung pagination, filter yang bisa disimpan, dan ekspor CSV.
- Konfirmasi eksplisit untuk aksi berisiko: tutup paksa, nonaktifkan akun, cabut perangkat.
- Keadaan kosong, memuat, dan gagal dirancang untuk semua layar. Sesi kedaluwarsa menampilkan pesan jelas dan tidak menghilangkan isian yang belum disimpan.
- Dukungan peramban modern versi terkini, dengan aksesibilitas dasar (kontras, navigasi keyboard, label).

**Aturan umum laporan**

1. Semua laporan dihitung dari data transaksi yang sudah diterima server, dengan tanggal bisnis berdasarkan `occurred_at` di zona waktu usaha. Filter bawaan: bulan berjalan, semua usaha. Setiap laporan bisa diekspor ke CSV.
2. Banner peringatan Data belum lengkap tampil bila ada tablet dengan outbox menunggu atau antrean bermasalah, karena angka bisa berubah setelah sinkron.
3. Omzet (nilai order) dan uang diterima dipisah. Order laundry yang belum lunas masuk omzet, tetapi belum masuk uang diterima.
4. Pajak dan biaya layanan yang dipungut ditampilkan di baris terpisah dan tidak dihitung sebagai pendapatan (usulan, perlu dikonfirmasi owner).
5. Modal dan margin hanya terlihat oleh owner.

**Laporan inti**

| Laporan | Kolom dan rumus | Filter |
| --- | --- | --- |
| Penjualan harian | Tanggal, usaha, jumlah transaksi, penjualan kotor (jumlah subtotal), diskon, refund dan void, penjualan bersih (kotor dikurangi diskon dan refund atau void), pajak dan biaya layanan (terpisah) | Tanggal, usaha, kasir |
| Uang diterima per metode | Tunai dan QRIS Statis: Tunai: pembayaran dikurangi refund (semua refund bersifat tunai, termasuk atas pembayaran QRIS); QRIS Statis: total bruto pembayaran, tidak dikurangi refund | Tanggal, usaha, metode |
| Laporan Z per shift | Salinan server dari laporan Z: kas awal, penjualan tunai, DP dan pelunasan tunai, refund tunai, kas keluar, kas seharusnya (hitungan server), kas dihitung (tablet), selisih, alasan, tanda cek QRIS Statis, jumlah order belum lunas | Tanggal, usaha, kasir, shift |
| Laundry belum tertagih | Nomor order, pelanggan, tanggal, total, dibayar, sisa, umur dalam hari | Usaha, umur |
| Laundry belum diambil | Order berstatus Siap lebih dari N hari | N dari Pengaturan |
| Margin estimasi (owner) | Per menu atau per layanan: (harga snapshot dikurangi modal snapshot) dikali jumlah, berlabel Estimasi | Periode, usaha |
| Tren biaya (owner) | Food cost persen = pembelian bahan dibagi penjualan bersih (cafe). Modal per kg bulanan = biaya bahan habis pakai, listrik, dan air dibagi total kg diproses (laundry) | Bulan |

**Laporan pengecualian**

Ambang adalah nilai Pengaturan yang diatur owner; angka di kolom Ambang awal hanyalah usulan titik mulai. Setiap temuan bisa dibuka sampai ke transaksi dan audit log-nya, dan dapat ditandai Ditinjau dengan catatan.

| Laporan | Definisi | Ambang awal |
| --- | --- | --- |
| Void, refund, diskon per kasir | Jumlah dan nilai per kasir per hari dan minggu, dibandingkan dengan kasir lain | Ditandai bila sekitar dua kali rata-rata kasir lain |
| Selisih kas | Shift dengan selisih di luar toleransi, dan kasir dengan selisih negatif beruntun | Toleransi dari Pengaturan, 3 shift beruntun |
| Diambil tanpa lunas | Order laundry berstatus Diambil dengan sisa tagihan lebih dari nol | Semua kasus |
| Kas keluar | Kas Keluar di atas batas atau terlalu sering dalam satu shift | Batas dari Pengaturan, usulan lebih dari 3 kali per shift |
| Transaksi di luar jam buka | Order atau pembayaran dengan `occurred_at` di luar jam buka usaha | Toleransi 30 menit |
| Selisih QRIS | Rekonsiliasi QRIS Statis berstatus Selisih | Toleransi nol |
| Rata-rata kg per order | Laundry: rata-rata berat per order per kasir | Ditandai bila jauh dari rata-rata kasir lain |
| Item gratis dan komplimen | Total nilai dan jumlah per kasir dan periode | Dilaporkan setiap periode |
| Tablet tidak sinkron | `last_sync_at` lebih tua dari ambang saat jam buka | Usulan 2 jam |
| Harga tidak sesuai master | Baris order yang ditandai deviasi harga | Semua kasus |
| Kas seharusnya berbeda | Tutup shift yang hitungan servernya berbeda dari tablet | Semua kasus |

**Laba-rugi bulanan (per usaha dan gabungan)**

Rumus mengikuti bagian 7. Pendapatan adalah penjualan bersih tanpa pajak. Pembelian bahan adalah `expenses` berkategori Bahan baku. Stok awal adalah stok akhir bulan sebelumnya dari `stock_counts`. Biaya operasional adalah semua pengeluaran selain Bahan baku, termasuk biaya admin pembayaran yang diinput owner. Pencatatan berbasis kas: pengeluaran dihitung pada tanggal bayar. Biaya bersama (`business_unit_id` kosong) tampil di baris terpisah dan hanya masuk ke total gabungan; cara membaginya per usaha belum diputuskan (bagian 19). Banner Belum lengkap tampil bila stok akhir bulan itu belum diisi, atau bila kategori rutin seperti Sewa dan Gaji belum tercatat, supaya laba tidak terbaca lebih tinggi dari kenyataan. Pengecekan akhir bulan (opsional): owner mengisi saldo kas dan bank awal dan akhir, lalu sistem membandingkan perubahan saldo dengan laba yang dilaporkan.
