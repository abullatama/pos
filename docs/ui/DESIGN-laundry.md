# DESIGN.md: Aplikasi Kasir Athia Laundry House (tablet, landscape)

Sistem desain antarmuka kasir untuk aplikasi **laundry**. Diturunkan dari panduan merek "Athia Laundry House" (bersih, elegan, ramah) dan disesuaikan untuk layar sentuh tablet. Bentuk komponen, jarak, dan tipografi mengikuti `layout-standard.md` dan sama dengan aplikasi cafe; yang berbeda hanya warna, bentuk sudut, dan nama.

## 1. Karakter

Bersih, lembut, higienis, ramah. Kartu membulat besar, ruang putih lapang, aksen mawar. Layar tetap fungsional: staf harus bisa mengisi berat dan status dengan cepat.

## 2. Warna

| Token | Nama | HEX | Kegunaan |
| --- | --- | --- | --- |
| `canvas` | Crisp Linen | `#FAFAFB` | Latar layar |
| `surface` | Pristine White | `#FFFFFF` | Kartu, panel, dialog, dok navigasi |
| `accent` | Velvet Rose | `#D81B60` | Tombol aksi utama, item navigasi aktif, sorotan |
| `on-accent` | Putih | `#FFFFFF` | Teks di atas aksen |
| `ink` | Deep Plum | `#6A0934` | Judul, bilah atas |
| `ink-body` | Slate Charcoal | `#1E293B` | Teks isi |
| `ink-muted` | Neutral Grey | `#64748B` | Keterangan, metadata |
| `accent-soft` | Powder Rose | `#FCE4EC` | Item terpilih, chip netral, latar lembut |
| `border` | Garis mawar tipis | `rgba(216,27,96,0.10)` | Batas kartu |
| `whatsapp` | Emerald WhatsApp | `#25D366` | Hanya tombol "Kirim via WA" |

Aturan khusus: teks di atas `whatsapp` memakai `ink-body` (kontras 7,38:1), bukan putih (1,98:1, gagal).

Warna fungsi (tidak bergantung pada merek, selalu disertai ikon dan label):

| Fungsi | Teks | Latar |
| --- | --- | --- |
| Sukses | `#15683A` | `#E3F4EA` |
| Peringatan | `#8A5300` | `#FFF4DD` |
| Bahaya | `#B3261E` | `#FBE7E5` |
| Info | `#2F4F9D` | `#E8EEFB` |

Kontras terukur: putih di atas mawar 4,95:1; mawar di atas linen 4,74:1; abu muted di atas linen 4,56:1 (hanya untuk teks 14 px ke atas); plum di atas powder rose 10,25:1. Abu `ink-muted` di atas `accent-soft` hanya 3,95:1 (gagal), jadi di atas latar `accent-soft` teks memakai `ink-body`.

Catatan: mawar mirip merah. Karena itu status Bahaya tidak pernah hanya berupa warna: selalu ikon peringatan dan kata ("Belum lunas", "Gagal"). Aksen mawar hanya untuk aksi, bukan untuk status.

## 3. Tipografi

| Peran | Font | Ukuran | Catatan |
| --- | --- | --- | --- |
| Judul layar | Playfair Display, Semibold | 28/36 | Hanya judul; tidak untuk angka |
| Isi dan antarmuka | Plus Jakarta Sans | 16/24 | Semua teks operasional |
| Angka uang dan berat | Plus Jakarta Sans Semibold | 20 sampai 40 | Rata kanan, angka tabular bila tersedia |
| Meta dan chip | Plus Jakarta Sans SemiBold | 14/20 | Panduan merek 12 px dinaikkan ke 14 px; huruf kapital hanya untuk chip |

## 4. Bentuk dan komponen

- **Sudut:** kartu dan panel 20 px, kolom input 16 px, tombol dan chip bentuk pil (`9999px`), dok navigasi pil.
- **Kartu:** latar `surface`, garis `border`, bayangan `0 4px 20px -2px rgba(216,27,96,0.06)`. Item terpilih: latar `accent-soft` dan garis `accent`.
- **Tombol utama:** latar `accent`, teks putih, tinggi 56 px, label menyebut aksinya ("Simpan order", "Terima pembayaran"). **Sekunder:** garis 1,5 px `accent`, teks `accent`, hover `accent-soft`. **Bahaya:** teks dan garis `Bahaya`.
- **Bilah navigasi samping kiri (Navigation Rail 76 px):** latar `surface`, monogram "AL" di pucuk atas, garis pemisah mawar halus, item aktif berlatar `accent` bentuk pil (`9999px`) dengan teks putih, tombol cepat admin di dasar bawah.
- **Ikon:** garis medium 1,5 sampai 2 px, ujung membulat.
- **Motif gelembung air:** hanya sangat halus di keadaan kosong (daftar kosong), tidak di layar kerja.

## 5. Status laundry

| Status | Chip |
| --- | --- |
| Diterima | Info |
| Diproses | Peringatan |
| Siap | Sukses |
| Diambil | Netral (`accent-soft`, teks `ink-body`) |

Status pembayaran: Belum lunas (Bahaya), DP (Peringatan), Lunas (Sukses).

## 6. Identitas di layar dan struk

- Logo Athia Laundry House (berkas logo diunggah ke Stitch; jangan digambar ulang) di kiri bilah atas.
- Struk dan nota 58 mm hanya hitam-putih. Isi kepala: Athia Laundry House, Jl. Trans Sulawesi, Bintauna, WA +62 823-4778-5986, `Buka Setiap Hari: 07.00 – 21.00 WITA`.
