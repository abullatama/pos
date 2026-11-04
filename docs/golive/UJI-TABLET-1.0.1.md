# Daftar Uji Tablet: Versi 1.0.1 (versionCode 10001)

Untuk: uji di Redmi Pad 2 dengan APK rilis bertanda tangan dan server nyata (data uji, bukan data produksi).
Aplikasi: **A3 Coffeeshop** (`id.a3coffeeshop`) dan **Athia Laundry** (`id.athialoundry`).

Tujuan: memastikan tidak ada bug kritis sebelum tablet diserahkan ke owner. Setiap skenario dicatat Lulus atau Gagal. Satu kegagalan di bagian **Blocker** menghentikan rilis.

## 0. Syarat sebelum mulai (sisi server, minta pengerja backend)

| # | Syarat | Mengapa |
| --- | --- | --- |
| 1 | Ada akun **manager** (minimal satu) dan **owner**, aktif, **sudah punya PIN**, dan tertaut ke unit cafe dan unit laundry | PIN manager tidak lagi bawaan. Tanpa akun ini, diskon manual, void, refund, kas keluar besar, tutup shift paksa, dan menu admin selalu menolak |
| 2 | Pengaturan cafe: `tax_bp` kelipatan 100 (mis. 1000 untuk 10%), `tax_mode` = `exclusive`, `rounding_multiple` = 100, `service_charge_enabled` sesuai kebutuhan | Tablet hanya menghitung persen bulat dan mode pajak ditambahkan |
| 3 | `discount_presets` berisi minimal satu preset persen (mis. "Promo 10%", 1000 bp) dan satu nominal (mis. "Potong 5rb", 5000), nama persis | Nama preset dipakai apa adanya; tidak ada preset bawaan di tablet |
| 4 | Tablet cafe dan laundry sudah terdaftar dan disetujui; ada akun kasir | |
| 5 | Layanan laundry dan menu cafe sudah diisi | |

## 1. Pemasangan

APK ada di `D:\Android\A3-releases\1.0.1-code10001\` (cek `SHA256SUMS.txt`).

1. **Jika di tablet pernah terpasang build debug**, uninstall dulu (tanda tangan berbeda, tidak bisa ditimpa). Uninstall menghapus data lokal: pastikan antrean sinkron sudah kosong atau data itu memang data uji.
2. Pasang APK rilis: `adb install -r app-cafe-release.apk` dan `adb install -r app-laundry-release.apk`.
3. Buka aplikasi dalam kondisi **online**. Tunggu indikator sinkron hijau agar master data dan **data akun manager** terunduh.
4. Pada tablet yang sudah terpasang versi sebelumnya: buka online sekali. Data akun diunduh ulang otomatis satu kali.

Periksa: tombol "Mode Demo / Lewati" **tidak ada** di layar pendaftaran. Versi di aplikasi 1.0.1.

## 2. Blocker (wajib lulus semua)

| # | Skenario | Hasil yang diharapkan | Lulus? |
| --- | --- | --- | --- |
| B1 | Cafe: pesanan dine-in 2 item, bayar tunai tanpa diskon | Total di tablet = total di server (web owner). Struk tercetak | |
| B2 | Cafe: pesanan dengan **diskon preset persen** | Server menerima; total sama persis; tidak ada bundel di antrean bermasalah | |
| B3 | Cafe: **diskon manual** (persen), PIN manager benar | Di server: order harga penuh + koreksi manual_discount; tagihan = yang dibayar; koreksi menunjuk **manager yang benar** | |
| B4 | Cafe: PIN **4444** dan **9999** | Ditolak (bukan PIN bawaan lagi) | |
| B5 | Offline: matikan internet, buat 5 transaksi campur (tunai, QRIS, diskon), hidupkan lagi | Semua tersinkron; jumlah dan total sama dengan tablet; tidak ada ganda | |
| B6 | Putus jaringan di tengah pengiriman, lalu pulihkan | Tidak ada transaksi ganda di server | |
| B7 | Laundry: order kiloan, bayar lunas, cetak tag QR | Total server = tablet; pajak 0; tag tercetak dan terbaca saat dipindai | |
| B8 | Akhir uji: buka antrean bermasalah | **Kosong** | |

## 3. Cafe

| # | Skenario | Hasil yang diharapkan |
| --- | --- | --- |
| C1 | Takeaway tunai dengan kembalian | Kembalian benar, total sama dengan server |
| C2 | Pembayaran **QRIS statis** | Tercatat sebagai QRIS; masuk daftar cek QRIS saat tutup shift |
| C3 | Diskon preset **nominal** | Total sama dengan server |
| C4 | Diskon manual **nominal** dan **komplimen 100%** | Koreksi `manual_discount` dan `comp` di server; tagihan sesuai |
| C5 | PIN salah 3 kali berturut-turut | Terkunci 5 menit; PIN benar pun ditolak selama terkunci |
| C6 | Tambah item ke pesanan terbuka **tanpa diskon** | Berhasil; tiket dapur hanya item baru, tanpa harga |
| C7 | Tambah item ke pesanan terbuka **berdiskon** | Ditolak dengan pesan jelas (batasan OQ-31) |
| C8 | Pindah meja; tandai menu habis | Tercatat; menu habis diredupkan |
| C9 | Void dan refund tunai dengan PIN manager | Berhasil; struk refund memuat **nama manager**; server menerima koreksi |
| C10 | Kas keluar ≤ Rp50.000 tanpa PIN; > Rp50.000 dengan foto nota dan PIN manager | Sesuai aturan; struk memuat nama manager |
| C11 | Tutup shift (hitung buta, selisih, alokasi kas, Laporan Z) | Kas seharusnya = hitungan server; Laporan Z tercetak |
| C12 | Tutup paksa shift dengan PIN manager | Berhasil; server mencatat manager yang benar |
| C13 | Buka menu Admin dengan PIN manager/owner | Terbuka dengan PIN benar; PIN lama 9999 ditolak |

## 4. Laundry

| # | Skenario | Hasil yang diharapkan |
| --- | --- | --- |
| L1 | Order **express** dan order **satuan (pcs)** | Total sesuai tarif; server menerima |
| L2 | Order dengan **DP**, lalu pelunasan | Status pembayaran benar di server |
| L3 | Ubah status lewat **scan QR tag** (Diproses, Siap) | Status berpindah satu arah; terkirim ke server |
| L4 | Ambil cucian **belum lunas** | Meminta PIN manager; berhasil dengan PIN benar |
| L5 | Pengambilan sebagian per baris | Tercatat per baris; status tetap Siap sampai semua diambil |
| L6 | Cuci ulang dengan alasan dan PIN manager | Berhasil dan tercatat |
| L7 | Notifikasi WhatsApp "cucian siap" | Status pengiriman terlihat; tombol kirim manual berfungsi |

## 5. Ketahanan

| # | Skenario | Hasil yang diharapkan |
| --- | --- | --- |
| S1 | Restart tablet saat antrean berisi | Setelah menyala, antrean tetap dan terkirim |
| S2 | Token habis / login ulang kasir | Data transaksi tidak hilang dan tidak masuk antrean bermasalah; terkirim setelah login |
| S3 | Printer mati lalu dinyalakan; ketuk status printer | Status berubah sesuai kondisi nyata |
| S4 | Pakai semua alur utama di **APK rilis** (R8) | Tidak ada crash. Bila crash: simpan teks error dan kirim bersama `mapping.txt` |
| S5 | **F43: uji offline 24 jam** (ikuti `PROTOKOL-UJI-OFFLINE-24JAM.md`) | Jumlah dan total transaksi tablet = server |

## 6. Rekonsiliasi akhir

Isi setelah semua sinkron (indikator hijau, antrean kosong):

| Item | Tablet | Server (web owner) | Sama? |
| --- | --- | --- | --- |
| Jumlah transaksi | | | |
| Total penjualan | | | |
| Tunai diterima | | | |
| QRIS | | | |
| Kas seharusnya akhir shift | | | |

## 7. Jika ada yang gagal

Catat per skenario: nomor, langkah, hasil nyata, dan foto layar. Khusus bundel di **antrean bermasalah**, salin teks pesan errornya persis. Pesan itu menentukan apakah masalahnya di tablet atau di server.

Hentikan uji dan laporkan segera bila terjadi: total tablet berbeda dengan server tanpa penjelasan, transaksi hilang atau ganda, crash di alur bayar, atau PIN bawaan lama masih diterima.

## 8. Hasil

| Tanggal | Penguji | Perangkat | Versi | Blocker lulus | Catatan |
| --- | --- | --- | --- | --- | --- |
| | | | 1.0.1 (10001) | / 8 | |
