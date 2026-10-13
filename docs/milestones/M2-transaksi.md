# M2: Transaksi cafe dan laundry

Baca: `docs/business-rules.md`, `docs/spec/04-*`, `docs/spec/05-*`, `docs/spec/07-*`, `docs/spec/12-*`.

## Hasil yang diminta

- Cafe: menu, varian, add-on, keranjang, diskon preset, dine-in dan takeaway, pindah meja, tanda Habis, kirim ke dapur.
- Laundry: pelanggan (cari atau buat), order per kg dan per pcs, express, tag QR, scan, status, pengambilan sebagian.
- Pembayaran Tunai dan QRIS Statis (offline juga), DP dan pelunasan laundry, struk, nota, tag, dan antrean cetak.
- Server menandai dan mengarantina total yang berbeda (flag), tidak menolak (D-13).
- Uji beban dua kali lipat karena dua usaha go-live bersamaan.

## Gerbang lulus

- Skenario 1, 2, 3, 4, 9, 10, 13, 14, 24, 25, 26 (spek bagian 17) lulus; yang bertanda tablet asli diuji manusia.
