# DESIGN.md: Aplikasi Kasir AA Coffeeshop (tablet, landscape)

Sistem desain antarmuka kasir untuk aplikasi **cafe**. Diturunkan dari panduan merek "AA Coffeeshop" (Quiet Luxury, Industrial Modern, Warm Brutalism) dan disesuaikan untuk layar sentuh tablet. Bentuk komponen, jarak, dan tipografi mengikuti `layout-standard.md` dan sama dengan aplikasi laundry; yang berbeda hanya warna, bentuk sudut, dan nama.

## 1. Karakter

Tenang, presisi, ruang napas lega, sudut tegas. Tampilan kasir tetap harus cepat dibaca di jam sibuk: hierarki jelas, angka besar, sedikit hiasan.

## 2. Warna

| Token | Nama | HEX | Kegunaan |
| --- | --- | --- | --- |
| `canvas` | Alabaster Warm White | `#FBF9F5` | Latar layar |
| `surface` | Putih | `#FFFFFF` | Kartu, panel, dialog |
| `surface-alt` | Concrete Greige | `#E5E0D8` | Item terpilih, chip netral, pemisah |
| `ink` | Obsidian Black | `#111111` | Judul, ikon, tombol sekunder tegas, navigasi aktif |
| `ink-muted` | Raw Charcoal | `#4A4A4A` | Teks isi, keterangan |
| `accent` | Espresso Amber | `#8E5836` | Tombol aksi utama (Bayar, Simpan), sorotan |
| `on-accent` | Alabaster | `#FBF9F5` | Teks di atas aksen |
| `border` | Garis rambut | `rgba(17,17,17,0.08)` | Batas kartu |

Warna fungsi (tidak bergantung pada merek, selalu disertai ikon dan label):

| Fungsi | Teks | Latar |
| --- | --- | --- |
| Sukses | `#15683A` | `#E3F4EA` |
| Peringatan | `#8A5300` | `#FFF4DD` |
| Bahaya | `#B3261E` | `#FBE7E5` |
| Info | `#2F4F9D` | `#E8EEFB` |

Kontras terukur: amber di atas alabaster 5,55:1; charcoal di atas alabaster 8,43:1; charcoal di atas greige 6,75:1. Semuanya lolos 4,5:1.

## 3. Tipografi

| Peran | Font | Ukuran | Catatan |
| --- | --- | --- | --- |
| Judul layar | Cormorant Garamond, Semibold | 28/36 | Hanya judul dan nama kategori; tidak untuk angka |
| Wordmark "COFFEESHOP" | Montserrat Semibold, huruf kapital | 14 | Tracking 0,22em; di bilah atas di samping monogram |
| Isi dan antarmuka | Plus Jakarta Sans | 16/24 | Semua teks operasional |
| Angka uang | Plus Jakarta Sans Semibold | 20 sampai 40 | Rata kanan, angka tabular bila tersedia |
| Meta dan chip | Plus Jakarta Sans Medium | 14/20 | Panduan merek 12 px dinaikkan ke 14 px agar terbaca di tablet |

## 4. Bentuk dan komponen

- **Sudut:** kartu, kolom input, chip, tombol 4 px (`radius-subtle`). Tidak ada pil. Dok navigasi 8 px.
- **Kartu:** latar `surface`, garis rambut `border`, bayangan `0 4px 24px -2px rgba(17,17,17,0.05)`. Item terpilih: garis `ink` 1 px dan latar `surface-alt`.
- **Tombol utama:** latar `accent`, teks `on-accent`, tinggi 56 px, label menyebut aksinya ("Bayar Rp61.400"). **Sekunder:** garis `ink`, teks `ink`. **Bahaya:** teks dan garis `Bahaya`.
- **Dok navigasi:** mengambang, sudut 8 px, latar `ink`, ikon dan label `canvas`, item aktif latar `surface-alt` dengan teks `ink`.
- **Chip status:** latar dan teks dari tabel warna fungsi, ikon kecil di kiri.
- **Ikon:** garis tipis 1,5 px, ujung tegas (selaras dengan sudut monogram).
- **Ruang kosong:** jarak antar blok minimal 24 px; jangan memadatkan layar.

## 5. Status cafe

| Status | Chip |
| --- | --- |
| Terbuka | Info |
| Lunas | Sukses |
| Dibatalkan | Netral (`surface-alt`, teks `ink-muted`) |
| Habis (menu) | Netral dengan ikon dicoret; kartu menu diredupkan |

## 6. Identitas di layar dan struk

- Monogram "AA" (berkas logo diunggah ke Stitch; jangan digambar ulang) di kiri bilah atas dengan wordmark.
- Struk 58 mm hanya hitam-putih: nama "AA Coffeeshop", alamat dan jam buka. Format jam: `Buka Setiap Hari: 07.00 – 21.00`.
- Alamat cafe belum ada di data (OQ-25).
