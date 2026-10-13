# Prompt Google Stitch

Cara pakai: satu proyek Stitch per aplikasi (cafe dan laundry), perangkat Tablet landscape. Urutan: (1) tempel isi `DESIGN-cafe.md` atau `DESIGN-laundry.md` sebagai design system atau konteks, (2) unggah logo (monogram AA atau logo Athia) sebagai gambar, (3) jalankan Prompt 0 sampai jadi dan kunci, (4) satu prompt = satu layar, (5) setiap 3 sampai 4 layar, periksa konsistensi dan kirim tangkapan layar ke tim untuk dinilai. Jangan menempelkan beberapa layar dalam satu prompt.

Aturan untuk semua prompt: teks antarmuka Bahasa Indonesia; target sentuh minimal 56 px; teks minimal 14 px; angka uang format `Rp 61.400` rata kanan; tanpa istilah teknis (ID, UUID, status sinkron mentah); tanpa kartu bergaya dokumen.

## Prompt 0: Kerangka (kerjakan dulu)

### Cafe

```
Rancang kerangka aplikasi kasir tablet landscape untuk "AA Coffeeshop" dengan design system terlampir. Satu layar contoh: Kasir.
Bilah atas setinggi 64 px: kiri monogram AA terunggah dan teks COFFEESHOP (huruf kapital, tracking lebar); kanan: "Rina · Shift 1", ikon sinkron hijau berlabel "Tersinkron", ikon printer berlabel "Printer siap", ikon menu admin.
Navigasi bawah MENGAMBANG, persegi panjang bersudut 8 px (bukan pil), latar hitam #111111, di tengah area konten, jarak 16 px dari tepi bawah, bayangan lembut. Lima item dengan ikon garis tipis dan label: Kasir (aktif, latar greige teks hitam), Pesanan, Menu Habis, Shift, Lainnya.
Area konten kosong berlatar #FBF9F5 dengan judul "Kasir" bergaya Cormorant Garamond.
Jangan menaruh tombol aksi di navigasi. Sisakan ruang bawah agar konten tidak tertutup navigasi.
```

### Laundry

```
Rancang kerangka aplikasi kasir tablet landscape untuk "Athia Laundry House" dengan design system terlampir. Satu layar contoh: Beranda.
Bilah atas setinggi 64 px, latar putih: kiri logo Athia terunggah; kanan: "Sari · Shift 1", ikon sinkron hijau berlabel "Tersinkron", ikon printer berlabel "Printer siap", ikon menu admin.
Navigasi bawah MENGAMBANG berbentuk pil, latar putih dengan bayangan lembut, di tengah area konten, jarak 16 px dari tepi bawah. Lima item dengan ikon garis membulat dan label: Beranda (aktif, latar mawar #D81B60 teks putih), Order Baru, Cucian, Scan, Lainnya.
Area konten kosong berlatar #FAFAFB dengan judul "Beranda" bergaya Playfair Display.
Jangan menaruh tombol aksi di navigasi.
```

## Cafe

### C01 Kasir

```
Layar "Kasir" di kerangka AA Coffeeshop yang sudah ada. Pola 65/35.
Kiri (65%): tab kategori "Kopi", "Roti", "Non-kopi"; di bawahnya kisi kartu menu 4 kolom, tiap kartu berisi nama dan harga: Latte Rp 34.000, Americano Rp 26.000, Croissant Rp 28.000, Teh tarik Rp 22.000, dan tiga menu lain. Satu kartu "Croissant" berstatus Habis: diredupkan dengan chip "Habis".
Kanan (35%): panel keranjang berjudul "Meja 5 · Dine-in". Isi: "Latte x1  Rp 34.000", "Croissant x1  Rp 28.000". Rincian: Subtotal Rp 62.000, Diskon 10% -Rp 6.200, Pajak 10% Rp 5.580, Pembulatan Rp 20, lalu Total tebal besar "Rp 61.400".
Di bawah panel: tombol sekunder "Kirim ke dapur" dan tombol utama amber "Bayar Rp 61.400". Tautan kecil "Diskon" dan "Catatan".
```

### C02 Pilih Varian dan Add-on

```
Lembar bawah di atas layar Kasir untuk menu "Latte". Judul "Latte · Rp 34.000". Grup "Suhu": Panas (terpilih), Dingin. Grup "Tambahan": Shot tambahan +Rp 6.000 (terpilih), Susu oat +Rp 8.000. Kolom jumlah dengan tombol - dan +, kolom "Catatan". Tombol utama "Tambah ke keranjang · Rp 40.000".
```

### C03 Tipe Pesanan dan Meja

```
Dialog "Tipe pesanan" di atas layar Kasir. Dua pilihan besar: "Dine-in" (terpilih) dengan kisi nomor meja 1 sampai 12 (meja 5 terpilih, meja 3 bertanda "terisi"), dan "Take-away" dengan kolom nama atau nomor antrean. Tombol "Lanjut".
```

### C04 Pesanan Terbuka

```
Layar "Pesanan" berisi kisi kartu pesanan terbuka per meja. Tiap kartu: "Meja 5", jumlah item "3 item", total "Rp 61.400", lama "12 menit", chip "Terbuka" (info). Enam kartu, satu lebih lama dari 45 menit diberi ikon jam. Tombol "Pesanan baru" di kanan atas.
```

### C05 Detail Pesanan Terbuka

```
Layar pola 40/60. Kiri: daftar pesanan terbuka (Meja 3, Meja 5 terpilih, Meja 8). Kanan: detail Meja 5 dengan daftar item, tombol "Tambah item", "Pindah meja", "Kirim item baru ke dapur", dan tombol utama "Bayar Rp 61.400".
```

### C06 Diskon

```
Dialog "Diskon". Preset sebagai kartu pilihan: "Diskon 5%", "Diskon 10%" (terpilih), "Potongan Rp 5.000". Di bawah, bagian "Diskon manual" terkunci dengan ikon gembok dan keterangan "Butuh PIN manager". Tombol "Terapkan".
```

### C07 Menu Habis

```
Layar "Menu Habis": daftar menu per kategori dengan saklar "Tersedia / Habis" di tiap baris. Croissant bersaklar Habis. Kolom cari di atas.
```

### C08 Serah Terima Pesanan Terbuka

```
Layar penuh fokus saat tutup shift (tanpa navigasi): judul "Pesanan masih terbuka". Keterangan "2 pesanan akan dibawa ke shift berikutnya". Daftar: Meja 5 Rp 61.400, Meja 8 Rp 87.100. Tombol "Lanjut tutup shift" dan "Kembali".
```

## Laundry

### L01 Beranda Laundry

```
Layar "Beranda" di kerangka Athia Laundry House. Tiga kartu ringkasan: "Order hari ini 14", "Siap diambil 6", "Belum lunas 3". Dua tombol besar: "Order baru" (utama, mawar) dan "Scan tag" (sekunder). Di bawahnya daftar "Siap diambil" dengan 4 baris: nomor L1-0012, nama "Budi", chip Siap, sisa tagihan.
```

### L02 Pelanggan

```
Dialog "Pelanggan". Kolom cari nomor HP berisi "0812". Hasil: "Budi · 0812 3456 7890" dan "Sari · 0812 1111 2222". Tautan "Pelanggan baru" membuka dua kolom: Nama, Nomor HP. Tombol "Pilih pelanggan".
```

### L03 Order Baru

```
Layar "Order baru" pola 60/40. Kiri: kartu pelanggan "Budi · 0812 3456 7890"; pilihan layanan sebagai kartu: "Kiloan reguler Rp 7.000/kg" (terpilih), "Selimut Rp 25.000", "Bed cover Rp 40.000"; kolom berat besar "3,7 kg" dengan tombol angka; saklar "Express"; kolom "Catatan khusus (noda, barang rapuh)"; estimasi selesai "Kamis, 15 Okt · 17.00".
Kanan: ringkasan: "Kiloan reguler 4,0 kg (dibulatkan) Rp 28.000", "Selimut x1 Rp 25.000", Total tebal "Rp 53.000". Opsi bayar: "Lunas di muka", "DP", "Bayar saat ambil". Tombol utama "Simpan order".
```

### L04 Cetak Nota dan Tag

```
Layar penuh fokus: tanda centang besar, "Order L1-0012 tersimpan". Dua pratinjau struk 58 mm bergaya kertas: "Nota" dan "Tag" (berisi QR dan nomor order). Status cetak "Mencetak..." lalu "Tercetak". Tombol "Cetak ulang" dan "Order baru".
```

### L05 Daftar Cucian

```
Layar pola 40/60. Kiri: tab filter "Semua", "Diterima", "Diproses", "Siap", "Diambil"; kartu cucian dengan nomor, nama, chip status dan chip pembayaran: L1-0012 Budi Siap Belum lunas; L1-0011 Sari Diproses Lunas; L1-0010 Dewi Diterima DP. Kanan: detail L1-0012.
```

### L06 Detail Cucian

```
Panel detail cucian L1-0012: nama, daftar layanan, total Rp 53.000, DP Rp 20.000, sisa Rp 33.000. Riwayat status dalam garis waktu: Diterima, Diproses, Siap. Tombol utama "Terima pelunasan Rp 33.000", tombol "Ambil cucian", dan tombol hijau WhatsApp (teks gelap) "Kirim via WA" dengan status "Notifikasi terkirim".
```

### L07 Scan Tag

```
Layar penuh fokus "Scan tag": pratinjau kamera dengan bingkai QR membulat. Setelah terpindai, kartu hasil di bawah: "L1-0012 · Budi · Diproses" dengan tombol besar "Tandai Siap".
```

### L08 Pengambilan

```
Dialog "Pengambilan". Daftar baris layanan: "Kiloan reguler 4,0 kg" dengan pilihan "Diambil semua", "Selimut x1" dengan jumlah diambil 0 dari 1. Saklar "Ini pengambilan terakhir". Peringatan "Belum lunas: butuh PIN manager" bila ada sisa. Tombol "Catat pengambilan".
```

### L09 Cuci Ulang

```
Dialog "Cuci ulang": pilihan alasan (Noda belum hilang, Bau, Rusak, Lainnya) dan tombol "Minta PIN manager". Keterangan "Cucian kembali ke Diproses tanpa tagihan baru".
```

### L10 Beranda Staf Laundry

```
Layar "Beranda" untuk staf laundry: tombol scan sangat besar di tengah "Scan tag", di bawahnya daftar cucian Diproses dan Siap. Navigasi hanya Scan dan Cucian. Tidak ada informasi kas atau shift.
```

## Bersama (jalankan sekali per aplikasi; tema mengikuti design system)

### S03 Masuk dengan PIN

```
Layar penuh fokus "Masuk". Kiri: kartu nama kasir (Rina terpilih, Dewi, Manager). Kanan: tombol angka besar 0-9 dan hapus, titik PIN 4 digit. Varian akun terkunci: "Terkunci sementara. Coba lagi dalam 4:32".
```

### S04 Buka Shift

```
Layar penuh fokus "Buka shift": tabel pecahan (Rp 100.000, 50.000, 20.000, 10.000, 5.000, 2.000, 1.000, koin) dengan jumlah lembar dan subtotal per baris; total kas awal "Rp 200.000"; keterangan "Standar: Rp 200.000"; tombol utama "Buka shift".
```

### S05 Pembayaran

```
Layar penuh fokus "Pembayaran": total sangat besar "Rp 61.400". Dua kartu pilihan: "Tunai" dan "QRIS". Tautan "Bagi dua metode". Tombol "Kembali" dan "Lanjut".
```

### S06 Bayar Tunai

```
Layar "Tunai": total Rp 61.400; kolom "Uang diterima" Rp 70.000; tombol nominal cepat "Uang pas", "Rp 70.000", "Rp 100.000"; kartu kembalian "Kembalian Rp 8.600" menonjol; tombol utama "Terima pembayaran".
```

### S07 Bayar QRIS Statis

```
Layar "QRIS": total Rp 61.400, petunjuk "Minta pelanggan memindai stiker QRIS. Pastikan uang masuk lewat notifikasi aplikasi merchant, bukan tangkapan layar." Kolom konfirmasi nominal, tombol utama "Pembayaran diterima".
```

### S08 Pembayaran Berhasil

```
Layar "Pembayaran berhasil": centang besar, ringkasan (Tunai Rp 70.000, kembalian Rp 8.600), status cetak struk, tombol "Cetak ulang" dan "Pesanan baru".
```

### S09 sampai S11 Tutup Shift

```
S09: layar penuh fokus "Hitung kas": tabel pecahan seperti Buka Shift, total hitungan; angka sistem TIDAK ditampilkan. Tombol "Lanjut".
S10: pola 60/40 "Selisih dan QRIS": kiri daftar transaksi QRIS shift ini dengan kotak centang "Sudah dicek" per baris; kanan Kas seharusnya Rp 725.000, hitungan Rp 723.000, selisih -Rp 2.000 (peringatan), kolom alasan.
S11: layar penuh fokus "Catatan kas": ditinggal di laci Rp 200.000, disetor Rp 523.000, penerima; validasi "Total sama dengan hitungan"; pratinjau Laporan Z; tombol "Tutup shift dan cetak".
```

### S12 Kas Keluar

```
Layar "Kas keluar": nominal Rp 30.000, kategori, alasan "Beli es batu", tombol foto nota, peringatan "Di atas Rp 50.000 butuh PIN manager dan foto nota". Tombol "Catat kas keluar".
```

### S13 Riwayat dan S14 PIN Manager

```
S13: pola 40/60: daftar transaksi dengan chip Lunas; detail dengan tombol "Void item", "Refund tunai", "Cetak ulang (SALINAN)". Transaksi shift sebelumnya diberi chip "Shift lalu".
S14: dialog "PIN Manager": tombol angka, pilihan alasan (Salah input, Pelanggan batal, Komplain kualitas, Salah buat, Promo, Lainnya), nilai "Refund tunai Rp 10.000", tombol "Setujui".
```

### S15 sampai S17

```
S15: daftar "Menu admin" (PIN owner): Wi-Fi, Printer, Status sinkron, Versi aplikasi, Keluar dari mode kios.
S16: pola 40/60 "Status sinkron": Menunggu 3, Bermasalah 1 dengan alasan dalam bahasa biasa dan tombol "Coba lagi".
S17: layar terkunci "Masukkan PIN"; varian "Tablet ini dicabut. Hubungi owner."
```

## Prompt perbaikan (pakai setelah tangkapan layar tidak sesuai)

- "Ubah hanya [bagian]. Pertahankan layout, warna, dan font lain apa adanya."
- "Navigasi bawah harus mengambang, tidak menempel tepi layar."
- "Ganti teks teknis menjadi bahasa biasa."
- "Perbesar target sentuh menjadi minimal 56 px dan teks minimal 14 px."
