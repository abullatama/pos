## 5. Kebutuhan fungsional C: pembayaran, struk, koreksi, kas keluar

**Pembayaran (Tunai dan QRIS Statis)**

Layar bayar menampilkan total dan dua metode: **Tunai** dan **QRIS**. QRIS di MVP adalah QRIS Statis (stiker QR milik usaha). Nama label dapat diubah di Pengaturan.

| ID | Kebutuhan |
| --- | --- |
| PAY-01 | Tunai: kasir mengisi uang diterima, kembalian dihitung otomatis. Tersedia tombol Uang pas dan nominal cepat. Selisih pembulatan dicatat sebagai baris tersendiri. |
| PAY-02 | QRIS Statis: app menampilkan nominal besar; pelanggan memasukkan nominal sendiri di aplikasinya. |
| PAY-03 | Kasir memastikan pembayaran lewat notifikasi aplikasi merchant atau soundbox, bukan screenshot pelanggan, lalu menekan Pembayaran diterima dan mengonfirmasi nominal. |
| PAY-04 | Tunai dan QRIS Statis dapat dicatat saat tablet offline. Tidak ada metode pembayaran yang butuh internet. |
| PAY-05 | Split hanya berdasarkan nominal (dua metode). Split per item tidak ada. |
| PAY-06 | DP dan pelunasan laundry adalah pembayaran terpisah pada order yang sama. Status pembayaran order: Belum lunas, DP, Lunas. |
| PAY-07 | Setiap percobaan bayar memiliki idempotency key (UUID dari tablet), dan setiap pembayaran dicatat dengan metode, jumlah, waktu, kasir, dan shift. |
| PAY-08 | Biaya admin pembayaran (MDR) dicatat sebagai pengeluaran yang diinput owner di web. |
| PAY-09 | Rekonsiliasi QRIS Statis dilakukan dua lapis: kasir mencocokkan daftar QRIS Statis shift saat tutup shift (bagian 6), dan owner membandingkan total harian dengan laporan merchant di web (bagian 14). |

**Struk dan nota**

| ID | Kebutuhan |
| --- | --- |
| PRT-01 | Transaksi disimpan lebih dulu, baru dicetak. Kegagalan printer tidak membatalkan transaksi. |
| PRT-02 | Cetakan masuk antrean cetak (Menunggu, Berhasil, Gagal) dengan retry otomatis dan tombol Cetak ulang. |
| PRT-03 | Jenis cetakan: struk pelanggan, tiket dapur atau bar (tanpa harga, hanya item baru), nota laundry, tag laundry, dan laporan Z. |
| PRT-04 | Satu printer per outlet, jadi semua cetakan keluar dari printer yang sama. Tiket dapur dicetak sebelum struk, setiap cetakan berjudul besar, dan auto-cutter dipakai bila ada. |
| PRT-05 | Cetak ulang berlabel SALINAN dan tercatat (siapa, kapan). |
| PRT-06 | Nomor struk unik tanpa bentrok antar tablet meski offline, berurutan: kode tablet + urutan. Apakah nomor harus berurutan tanpa lompat ditanyakan ke owner (bagian 19). |
| PRT-07 | Perintah buka laci kas tersedia dan nonaktif secara default (Pengaturan). Lebar kertas 58 atau 80 mm diatur di menu admin dengan tombol Test print. |
| PRT-08 | Struk memuat data pelanggan seminimal mungkin. Struk digital lewat WhatsApp hanya untuk order laundry (pelanggan cafe tidak punya nomor tercatat), dikirim backend sebagai teks tanpa lampiran. |

**Catatan tag laundry:** printer iWare C58BT hanya memakai kertas thermal roll 58 mm (klaim ini berasal dari listing seri C58BT Pro, bukan dari model yang dipakai, jadi diuji sendiri di M0). Tag laundry dicetak di kertas roll biasa, dipotong, lalu dilekatkan atau diklip ke cucian. Kertas thermal bisa pudar atau basah di lingkungan lembap, jadi setiap tag juga memuat nomor order yang ditulis tangan sebagai cadangan. Bila owner ingin tag stiker, perlu printer label dan itu di luar MVP.

**Koreksi: void, refund, dan diskon**

Tidak ada yang dihapus. Setiap koreksi adalah catatan baru yang menunjuk ke transaksi asalnya, dengan alasan wajib dari daftar: salah input, pelanggan batal, komplain kualitas, salah buat, promo, lainnya (+catatan). Penjualan bersih = penjualan kotor dikurangi diskon dikurangi refund atau void.

| ID | Kebutuhan |
| --- | --- |
| COR-01 | Diskon preset (dibuat owner) boleh dipakai kasir. Diskon manual, gratis, atau komplimen butuh PIN manager dan alasan. |
| COR-02 | Void item setelah dikirim ke dapur: PIN manager dan alasan. Bahan yang terbuang otomatis ikut terhitung di biaya bahan (HPP dari pembelian dan stok akhir), jadi tidak ada input terpisah. |
| COR-03 | Refund atau void transaksi lunas: PIN manager, pilih transaksi, seluruh atau sebagian item, alasan, pengembalian, struk refund. |
| COR-04 | Refund selalu tunai: refund tunai mengurangi kas seharusnya di shift berjalan. Refund atas pembayaran QRIS dikembalikan tunai dan dicatat sebagai refund tunai atas pembayaran QRIS. Refund tidak pernah mengurangi total QRIS; rekonsiliasi QRIS memakai total bruto (bagian 7). |
| COR-05 | Void atau refund transaksi lunas di shift yang sama: PIN manager. Atas transaksi shift sebelumnya (komplain datang belakangan): manager di tablet dengan PIN manager, dicatat di shift berjalan sebagai refund tunai yang mengurangi laci hari itu, dengan alasan wajib dan tanda Refund transaksi lama di laporan pengecualian. Di atas batas nilai atau umur transaksi (Pengaturan, usulan Rp100.000 dan 14 hari): hanya owner dari web, dengan sumber dana dicatat (kas keluar atau transfer). |
| COR-06 | Diskon dihitung sesuai urutan rumus bagian 7 dan tampil di struk. Nilai gratis atau komplimen tampil sebagai biaya di laporan. |
| COR-07 | PIN manager salah 3 kali mengunci akun sementara. Semua aksi masuk audit log (siapa, kapan, alasan, nilai). |
| COR-08 | Void, refund tunai, dan diskon tetap bisa dilakukan saat offline (PIN manager diverifikasi lokal). |

**Kas keluar**

| ID | Kebutuhan |
| --- | --- |
| KAS-01 | Kas Keluar mencatat nominal, alasan, dan foto nota; mengurangi kas seharusnya shift. |
| KAS-02 | Sampai batas (Pengaturan) kasir boleh tanpa PIN. Di atas batas butuh PIN manager dan foto nota wajib. |
