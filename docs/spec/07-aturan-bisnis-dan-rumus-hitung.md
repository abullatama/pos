## 7. Aturan bisnis dan rumus hitung

Semua perhitungan memakai bilangan bulat rupiah dan urutan yang sama di app dan backend. Selisih hasil hitung antara keduanya dianggap bug. Angka contoh adalah kasus uji, bukan keputusan tarif. Rumus ditulis satu kali di modul domain murni dan diuji dengan kumpulan test vector yang sama di Android dan backend.

**Aturan umum**

- Tidak memakai bilangan desimal untuk uang. Persentase dihitung per komponen dan dibulatkan ke rupiah terdekat (setengah ke atas).
- Harga dan modal disalin ke baris transaksi saat item ditambahkan (snapshot).
- Nomor order tampil unik per tablet per hari: kode tablet + urutan (`C1-0007`, `L1-0012`). Identitas sistem tetap UUID.
- Waktu disimpan dalam UTC dan ditampilkan di zona waktu usaha. Tanggal bisnis laporan mengikuti `occurred_at` di zona waktu usaha.

**Total cafe**

```latex
\text{Total} = \text{Subtotal} - \text{Diskon} + \text{Layanan} + \text{Pajak} + \text{Pembulatan}
```

1. Subtotal = jumlah dari harga satuan (termasuk varian dan add-on) dikali jumlah.
2. Diskon dihitung atas subtotal (preset persen atau nominal).
3. Biaya layanan = (subtotal dikurangi diskon) dikali persentase layanan, bila diaktifkan.
4. Pajak = (subtotal dikurangi diskon ditambah biaya layanan) dikali persentase pajak. Mode pajak: ditambahkan di atas harga, atau sudah termasuk dalam harga (Pengaturan).
5. Pembulatan ke kelipatan yang diatur (usulan Rp100 terdekat). Selisihnya dicatat sebagai baris tersendiri.

**Total laundry**

- Per kg: berat dibulatkan ke atas sesuai kelipatan yang diatur (usulan 0,5 kg), dengan berat minimum. Harga = berat terbulatkan dikali tarif per kg. Express memakai tarif khusus per layanan.
- Per pcs: jumlah dikali tarif per pcs.
- Pajak untuk laundry ditentukan owner (bagian 19).

**Kas seharusnya saat tutup shift**

```latex
\text{Kas} = \text{KasAwal} + \text{PenjualanTunai} + \text{DPdanPelunasanTunai} - \text{RefundTunai} - \text{KasKeluar}
```

QRIS tidak masuk laci, jadi tidak ikut hitungan kas. Server menghitung ulang kas seharusnya dari event dan menyimpannya di samping angka dari tablet.

**Profit (laba-rugi ringan)**

```latex
\text{HPP} = \text{Pembelian bahan} + \text{Stok awal} - \text{Stok akhir}
```

```latex
\text{Laba bersih} = \text{Penjualan bersih} - \text{HPP} - \text{Biaya operasional}
```

- Stok akhir dihitung manual untuk bahan utama (sekitar 10 item) sebulan sekali.
- Modal per porsi (cafe) dan modal per kg (laundry) hanya estimasi margin per transaksi. Laporan laba-rugi bulanan tidak memakainya, supaya biaya tidak terhitung dua kali.
- Modal per kg dihitung bulanan: biaya bahan habis pakai, listrik, dan air dibagi total kg yang diproses. Kenaikan mendadak menjadi sinyal cucian yang diproses tapi tidak tercatat.
- Biaya admin pembayaran (MDR) dicatat sebagai pengeluaran yang diinput owner.
- Pengeluaran memakai kategori tetap: Bahan baku, Gaji, Sewa, Listrik/Air/Internet, Kemasan, Perawatan, Lain-lain. Biaya rutin (sewa, gaji, internet) diatur sebagai pengeluaran berulang bulanan.
- Biaya bersama antar-usaha ditandai Bersama.

**Rekonsiliasi QRIS Statis harian (owner, di web)**

Owner mengisi satu angka total QRIS per hari per usaha dari laporan aplikasi merchant, dibandingkan dengan total QRIS Statis di app. Toleransi selisih adalah nol, dan selisih membutuhkan catatan owner serta tampil di laporan pengecualian. Yang dibandingkan adalah total QRIS bruto (sebelum biaya MDR) menurut tanggal bisnis yang sama dengan app (zona waktu usaha, batas hari pukul 00:00). Refund tunai atas pembayaran QRIS tidak mengurangi total QRIS, karena uangnya keluar dari laci, bukan dari merchant. Bila kedua usaha memakai satu QRIS merchant, laporan merchant hanya bisa dibandingkan dengan total gabungan dan angka per usaha tidak bisa dipisah, sehingga disarankan satu QRIS per usaha (pertanyaan terbuka).

**Kasus uji (test vector) awal**

| Kasus | Masukan | Hasil |
| --- | --- | --- |
| Cafe | Subtotal 62.000, diskon preset 10% (6.200), layanan 0%, pajak 10% atas 55.800 (5.580) | Jumlah 61.380, dibulatkan ke Rp100 menjadi **61.400**, pembulatan +20 |
| Laundry | Cuci + setrika reguler Rp7.000 per kg, berat 3,7 kg dibulatkan menjadi 4,0 kg (28.000), ditambah 1 selimut Rp25.000 | Total **53.000** |

Tambahkan kasus tepi (pembulatan setengah naik, diskon nominal, express, berat minimum, pajak termasuk dalam harga, DP lalu pelunasan) ke berkas test vector bersama sebelum M2.
