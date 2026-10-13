# Standar Kerangka Layar dan Tata Letak

Berlaku untuk kedua aplikasi. Kerangka dibuat sekali di `core:ui` (`AppShell`).

## Kerangka

```
+--------------------------------------------------------------+
| Bilah atas 64 px: nama usaha | kasir/shift | sinkron | printer |
+--------------------------------------------------------------+
|                                                              |
|                 Area konten utama                            |
|                                                              |
|            [ Navigasi bawah mengambang ]                     |
+--------------------------------------------------------------+
```

- **Bilah atas**: tinggi 64 px. Kiri: nama usaha. Kanan: nama kasir dan shift, indikator sinkron, status printer, tombol menu admin (PIN owner).
- **Navigasi bawah mengambang**: bentuk mengikuti merek (laundry: pil; cafe: persegi panjang bersudut 8 px, bukan pil), di tengah area konten (bukan tengah layar bila ada panel samping), jarak dari tepi bawah 16 px, bayangan lembut. Item aktif memakai warna aksen. Maksimal 5 item.
- Isi konten diberi padding bawah agar tidak tertutup navigasi.
- **Navigasi disembunyikan** pada alur yang tidak boleh terputus: pembayaran, buka dan tutup shift, input PIN, cetak.
- Tombol aksi utama (Bayar, Simpan) ada di panel kanan atau dasar konten, bukan di navigasi.

## Menu navigasi

| Aplikasi | Item |
| --- | --- |
| Cafe | Kasir (C01), Pesanan (C04), Menu Habis (C07), Shift, Lainnya |
| Laundry | Beranda (L01), Order Baru (L03), Cucian (L05), Scan (L07), Lainnya |

## Pola layar

| Pola | Pembagian | Dipakai untuk |
| --- | --- | --- |
| Daftar + detail | 40 / 60 | Pesanan, Cucian |
| Pilih + keranjang | 65 / 35 | Kasir cafe (menu kiri, keranjang kanan) |
| Formulir + ringkasan | 60 / 40 | Order laundry (input kiri, ringkasan harga kanan) |
| Layar penuh fokus | 100 | Pembayaran, tutup shift, PIN |

## Komponen wajib

- Kartu bersudut membulat besar, tanpa garis tebal; hierarki dengan jarak dan warna lembut.
- Chip status: warna tetap per status (Diterima, Diproses, Siap, Diambil; Belum lunas, DP, Lunas).
- Angka uang: format `Rp 61.400`, rata kanan, huruf tebal pada total.
- Dialog konfirmasi untuk aksi yang tidak bisa dibatalkan, dengan tombol yang menyebut aksinya ("Terima pembayaran", bukan "OK").
- Keadaan kosong, memuat, dan galat untuk setiap daftar.
- Varian luring: banner "Menunggu sinkron" tanpa menghalangi pekerjaan.

## Larangan

- Tidak ada kartu bergaya dokumen atau chip berisi istilah teknis (ID, UUID, status sinkron mentah).
- Tidak ada navigasi bawah yang menempel penuh lebar (harus mengambang).
- Tidak memakai warna merek untuk status; status memakai warna fungsi dari `DESIGN-*.md`.
- Tidak ada target sentuh di bawah 56 px.
