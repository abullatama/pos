# Daftar Layar (v2.2)

Kode layar dipakai sebagai nama berkas di `docs/ui/screens/` dan di tiket. Pola mengacu ke `layout-standard.md`. Rujukan menunjuk ke ID kebutuhan di `docs/spec/`.

## Bersama (kedua aplikasi, tema mengikuti aplikasi)

| Kode | Layar | Pola | Isi utama | Rujukan |
| --- | --- | --- | --- | --- |
| S01 | Daftarkan Tablet | Penuh fokus | Kolom kode pendaftaran, status Menunggu persetujuan, Aktif, atau Dicabut | SET-02 |
| S02 | Memuat Data Awal | Penuh fokus | Progres per bagian, tombol Coba lagi saat gagal | BOO-01 sampai 03 |
| S03 | Masuk dengan PIN | Penuh fokus | Pilih nama (kartu), tombol angka, pesan akun terkunci dengan hitung mundur | AUT-01 sampai 04 |
| S04 | Buka Shift | Penuh fokus | Hitung kas awal per pecahan, total, alasan bila beda dari standar | SHF-01 sampai 04 |
| S05 | Pembayaran | Penuh fokus | Total, pilihan Tunai atau QRIS, bagi bayar dua metode | PAY |
| S06 | Bayar Tunai | Penuh fokus | Uang diterima (nominal cepat), kembalian, tombol Terima pembayaran | PAY, K2 |
| S07 | Bayar QRIS Statis | Penuh fokus | Total, petunjuk "pastikan lewat notifikasi merchant", konfirmasi nominal, tombol Pembayaran diterima | PAY |
| S08 | Pembayaran Berhasil | Penuh fokus | Ringkasan, status cetak, Cetak ulang (SALINAN), Pesanan baru | PRT |
| S09 | Tutup Shift: Hitung Kas | Penuh fokus | Hitung kas buta per pecahan (angka sistem disembunyikan) | TUP-02 |
| S10 | Tutup Shift: Selisih dan QRIS | 60/40 | Kas seharusnya, hitungan, selisih, alasan; daftar QRIS untuk ditandai Sudah dicek | TUP-03, 04 |
| S11 | Tutup Shift: Catatan Kas dan Laporan Z | Penuh fokus | Ditinggal di laci, disetor atau dibawa (penerima), validasi alokasi, Laporan Z, Cetak | TUP-05 sampai 07 |
| S12 | Kas Keluar | Formulir + ringkasan | Nominal, kategori, alasan, foto nota, batas dan PIN manager | KAS |
| S13 | Riwayat Transaksi | Daftar + detail 40/60 | Daftar hari ini dan shift lalu, detail, aksi Void, Refund, Cetak ulang | COR |
| S14 | PIN Manager (dialog) | Dialog | Tombol angka, alasan dari daftar tetap, nilai yang disetujui | COR-01 sampai 07 |
| S15 | Menu Admin Tablet | Daftar | Wi-Fi, printer, keluar kios, versi, status sinkron (PIN owner) | SET-05 |
| S16 | Status Sinkron dan Antrean Bermasalah | Daftar + detail 40/60 | Menunggu, Bermasalah dengan alasan, Coba lagi, Ekspor | SYN |
| S17 | Layar Terkunci | Penuh fokus | Buka dengan PIN; varian "Tablet dicabut, hubungi owner" | AUT-05, DEVICE_REVOKED |

Keadaan lintas layar (bukan layar sendiri): banner luring "Menunggu sinkron", indikator sinkron hijau, kuning, merah, dan peringatan jam tablet.

## Cafe (AA Coffeeshop)

| Kode | Layar | Pola | Isi utama | Rujukan |
| --- | --- | --- | --- | --- |
| C01 | Kasir | Pilih + keranjang 65/35 | Kategori, kisi menu (kartu, harga, tanda Habis), keranjang, diskon, tombol Kirim dan Bayar | CAF-01 sampai 07 |
| C02 | Pilih Varian dan Add-on | Lembar bawah | Varian, add-on, jumlah, catatan, harga satuan terhitung | CAF-01 |
| C03 | Tipe Pesanan dan Meja | Dialog | Dine-in (nomor meja) atau take-away (nama atau nomor antrean) | CAF-02 |
| C04 | Pesanan Terbuka | Kisi | Kartu per meja: total, lama, status Terbuka | CAF-04, 08 |
| C05 | Detail Pesanan Terbuka | Daftar + detail | Tambah item, pindah meja, Kirim item baru, Bayar | CAF-07, 08 |
| C06 | Diskon | Dialog | Preset (5%, 10%, Rp5.000) dan manual dengan PIN manager | COR-01 |
| C07 | Menu Habis | Daftar | Tandai Habis atau Tersedia per menu | CAF-09 |
| C08 | Serah Terima Pesanan Terbuka | Penuh fokus | Daftar pesanan terbuka yang dibawa ke shift berikutnya saat tutup shift | TUP-01 |

## Laundry (Athia Laundry House)

| Kode | Layar | Pola | Isi utama | Rujukan |
| --- | --- | --- | --- | --- |
| L01 | Beranda Laundry | Penuh | Ringkasan hari ini (order, Siap diambil, belum lunas), pintasan Order Baru dan Scan | AUT-03 |
| L02 | Pelanggan | Dialog | Cari nomor HP, atau buat baru (nama, nomor HP) | LDR-01 |
| L03 | Order Baru | Formulir + ringkasan 60/40 | Layanan per kg atau per pcs, berat atau jumlah, express, catatan khusus, estimasi selesai, ringkasan harga | LDR-02 sampai 05 |
| L04 | Cetak Nota dan Tag | Penuh fokus | Pratinjau, status cetak, Cetak ulang | PRT |
| L05 | Daftar Cucian | Daftar + detail 40/60 | Filter status, kartu dengan chip status dan pembayaran | LDR |
| L06 | Detail Cucian | Panel detail | Riwayat status, ubah status, Terima sisa pembayaran, Kirim via WA manual, status notifikasi | LDR-06 sampai 10 |
| L07 | Scan Tag | Penuh fokus | Kamera, bingkai QR, hasil pindai, ubah status satu ketukan | LDR |
| L08 | Pengambilan | Dialog | Baris layanan, jumlah yang diambil, penanda terakhir; PIN manager bila belum lunas | LDR-11 |
| L09 | Cuci Ulang | Dialog | Alasan wajib dan PIN manager | LDR-12 |
| L10 | Beranda Staf Laundry | Penuh | Scan dulu, daftar cucian; tidak butuh shift | AUT-03, 06 |

## Web owner (di luar lingkup tablet)

Dikerjakan pengembang web. Layar karantina (flag sinkron), rekonsiliasi QRIS, dan laporan ada di `docs/spec/14-*`.

## Urutan pembuatan di Stitch

1. Kerangka (bilah atas + dok) untuk cafe, lalu laundry.
2. C01, L03 (dua layar paling padat), lalu S05 sampai S08.
3. S09 sampai S11, S12 sampai S14.
4. Sisanya. Tinjau dengan tangkapan layar setiap 3 sampai 4 layar.
