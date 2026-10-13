# UI

Desain dibuat di Google Stitch lalu diekspor sebagai acuan; kode UI mengikuti desain dan `layout-standard.md`.

## Berkas

- `DESIGN-cafe.md` dan `DESIGN-laundry.md`: sistem desain per aplikasi (warna, tipografi, komponen), diturunkan dari panduan merek AA Coffeeshop dan Athia Laundry House.
- `layout-standard.md`: kerangka layar dan pola tata letak yang sama di kedua aplikasi.
- `screens.md`: daftar layar dengan kode (S, C, L) dan rujukan ke spek.
- `stitch-prompts.md`: prompt Stitch per layar.
- `screens/` (ditambahkan setelah ekspor): gambar dan HTML layar, dinamai menurut kode layar.

## Aturan untuk agent

- Tablet landscape (Redmi Pad 2, 11 inci). Area sentuh minimal 56 px, teks minimal 14 px.
- Semua teks di resource string, Bahasa Indonesia, tanpa istilah teknis.
- Warna dan ukuran dari token tema di `core:ui`, bukan angka tertanam. Token per aplikasi: `DESIGN-cafe.md` dan `DESIGN-laundry.md`.
- Dua aplikasi berbagi komponen dan kerangka; beda di warna, bentuk sudut, ikon, nama, dan menu navigasi.
- Status tidak pernah hanya berupa warna: selalu ikon dan kata.
- Jangan mengarang layar. Bila layar belum ada di `screens.md`, catat di `docs/open-questions.md`.
- Indikator sinkron (hijau, kuning, merah) selalu terlihat di bilah atas.
