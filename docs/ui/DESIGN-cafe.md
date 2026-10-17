# DESIGN.md: Aplikasi Kasir A3 Coffeeshop (Tablet Landscape 11")

Sistem desain antarmuka kasir untuk aplikasi **A3 Coffeeshop** (tablet landscape 11 inci, target Redmi Pad 2). Mengadopsi filosofi desain **Modern Organic & Clean Utility** (berdasarkan acuan spesifikasi `design2.md` dan `contoh.jpeg`), dipadukan dengan identitas logo resmi **A3** (monogram geometris tiga huruf 'A' saling mengunci).

---

## 1. Karakter Visual

Tenang, organik, bersih, dan berwibawa (*bespoke specialty coffee POS*). Menggabungkan kehangatan kanvas krem lembut dengan kartu putih bersih, garis batas arsitektural halus, dan aksen **Deep Forest Artisan Green** pada titik-titik interaksi fokus. Seluruh komponen interaktif menggunakan bentuk kapsul pil (*pill shapes*) dan sudut membulat modern (16–24 px), bebas dari kesan kaku atau kotak-kotak datar.

---

## 2. Palet Warna (Color Tokens)

### Warna Dasar & Permukaan
| Token | Hex | Kegunaan |
|---|---|---|
| `canvas` | `#F4F2E6` | Latar utama aplikasi (krem hangat menyatu dari atas ke bawah) |
| `surface` | `#FFFFFF` | Latar kartu produk, panel struk melayang, search bar, dan modal sheet |
| `surface-alt` | `#EAE8DF` | Abu-abu semen hangat untuk thumbnail produk, stepper kuantitas, dan chip netral |
| `ink` | `#141414` | Teks judul, angka harga, tombol sekunder, dan garis tepi terstruktur |
| `ink-muted` | `#6B7280` | Keterangan, teks tanggal, harga satuan, dan placeholder |
| `accent` | `#0A3D24` | **Deep Forest Artisan Green**: Warna fokus interaktif (banner aktif, tab pesanan aktif, trek swipe button, tombol tambah produk) |
| `on-accent` | `#FFFFFF` | Teks putih di atas elemen berlatar aksen hijau/hitam |
| `border` | `rgba(20, 20, 20, 0.12)` | Garis rambut pemisah halus (tanpa border hitam tebal) |

### Warna Navigasi Samping Kiri (Floating Left Rail)
- `navBackground`: `#141414` (Obsidian Charcoal pekat melayang).
- `navItemText`: `#9E9E9E` (Abu-abu netral untuk item tidak aktif).
- `navItemActiveBackground`: `#FFFFFF` (Putih murni untuk item menu yang sedang aktif).
- `navItemActiveText`: `#141414` (Hitam pekat untuk ikon & teks item aktif).

### Warna Fungsi (Status Standar)
| Fungsi | Teks | Latar |
|---|---|---|
| Sukses | `#15683A` | `#E3F4EA` |
| Peringatan | `#8A5300` | `#FFF4DD` |
| Bahaya | `#B3261E` | `#FBE7E5` |
| Info | `#2F4F9D` | `#E8EEFB` |

---

## 3. Tipografi

Menggunakan keluarga huruf **Plus Jakarta Sans** (dibundel lokal di APK, bebas dependensi daring):

| Peran | Bobot | Ukuran | Kegunaan & Catatan |
|---|---|---|---|
| Judul Layar & Modal | Bold / SemiBold | 22 – 26 sp | Judul kartu struk, header modal, total belanja |
| Nama Produk Katalog | Bold | 13.5 – 14 sp | Alokasi 2 baris mandiri (`minLines=2, maxLines=2`), tidak terpotong |
| Angka Uang / Harga | Bold / SemiBold | 13 – 26 sp | Angka tabular (`fontFeatureSettings = "tnum"`), rata kanan |
| Teks Isi & Form | Medium / Regular | 13 – 14 sp | Input pencarian, catatan barista, deskripsi |
| Label, Chip, & Meta | SemiBold / Medium | 10 – 12 sp | Lencana `Available`, nama tab segmented, stepper kuantitas |

---

## 4. Bentuk, Geometri & Komponen Utama

- **Sudut & Radius:**
  - Kapsul Pil Penuh (`9999px`): Search bar, tombol swipe, segmented control pesanan, stepper kuantitas, tombol tambah.
  - Kartu Melengkung Besar (`24px`): Panel Struk Pesanan kanan (*Purchase Receipt Drawer*).
  - Kartu Produk & Banner Kategori (`16px`): Banner kategori dan kartu menu di katalog.
  - Modal Bottom Sheet (`24px`): Sudut atas lembar verifikasi pesanan C02.
  - Floating Left Rail (`20px`): Sudut kapsul bilah navigasi samping kiri.

- **Bilah Atas (TopBar):**
  - Menyatu 100% dengan kanvas krem `#F4F2E6` (tanpa kotak latar terpisah dan tanpa bayangan).
  - Menghormati *status bar* sistem tablet agar jam, hari, dan tanggal HyperOS tetap bersih dan terbaca.
  - Sisi Kiri: Logo resmi A3 transparan murni (`ic_a3_logo.png`) berdampingan dengan tanggal dinamis hari ini (*tanpa nama toko berulang dan tanpa garis divider*).
  - Sisi Kanan: Kapsul-kapsul pil putih bersih untuk profil kasir, status sinkron, status printer, dan admin.

- **Bilah Navigasi Kiri Mengambang (Floating Left Dock 68 dp):**
  - Mengambang dengan margin `12.dp` di bawah TopBar dan `12.dp` dari tepi kiri layar.
  - Item menu aktif berlatar putih murni `#FFFFFF` dengan ikon/teks hitam pekat `#141414`.
  - Otomatis tersembunyi saat alur fokus penuh (pembayaran, shift) aktif.

- **Banner Kategori Dinamis & Slidable (Tinggi 115 dp):**
  - Membaca kategori secara dinamis dari database (misal: *Semua, Kopi, Non-kopi, Roti & Makanan*).
  - Dapat digeser horizontal (*slidable*) dengan sentuhan jari.
  - Menampilkan badge status `Available`, hitungan item otomatis (*50 items*), dan watermark artistik monokrom.
  - Kategori aktif berubah latar menjadi **Deep Forest Artisan Green (`#0A3D24`)** dengan teks putih. Bebas dari border hitam kaku.

- **Katalog Menu (Grid 4–5 Kolom Adaptif):**
  - Menampilkan 5 kolom saat panel struk tertutup; otomatis menyusut mulus menjadi 4 kolom saat struk terbuka.
  - Kartu produk berlatar putih bersih dengan bayangan difusi lembut.
  - Nama produk menempati baris mandiri lebar 100% (`maxLines=2, minLines=2`) sehingga nama panjang tidak pernah terpotong.
  - Tombol tambah bulat `(+)` 34 dp beraksen hijau duduk di pojok kanan bawah sejajar dengan harga.

- **Struk Pesanan Melayang Mandiri (Purchase Receipt Drawer 24 dp):**
  - Kartu putih melayang mandiri bersudut `24.dp` dengan elevasi bayangan `8.dp` (bebas dari garis border hitam kaku luar).
  - **Auto-Expand / Collapse:** Default tertutup saat keranjang kosong; otomatis meluncur terbuka saat kasir mengetuk menu baru. Dilengkapi tombol collapse (`>`) di kiri atas struk dan tombol pintasan keranjang saat tertutup.
  - **Segmented Control 3 Opsi:** Kapsul pil `[Dine In] [Take Away] [Order Online]`.
  - **Input Pelanggan & Meja:** Dua kolom kapsul pil berdampingan (*Customer name* dan dropdown *Table*).
  - **Daftar Pesanan Terurai Multiline:** Varian (`• Varian: Dingin`), add-on (`+ Susu Oat (+Rp 8.000)`), dan catatan barista (`📄 Less Sugar`) diurai bertingkat membungkus secara alami tanpa terpotong elipsis.
  - **Tombol Geser Adaptif (`A3SwipeButton`):** Trek kapsul hijau hutan `#0A3D24` dengan thumb putih `→` dan gestur geser ke kanan (*Slide-to-Confirm*):
    - *Dine In* $\rightarrow$ `Geser: Kirim ke Dapur` (membuka meja dan mencetak tiket dapur).
    - *Take Away* $\rightarrow$ `Geser: Bayar Rp XX.XXX` (langsung membuka alur pembayaran).
    - *Order Online* $\rightarrow$ `Geser: Proses Pesanan Ojol`.

---

## 5. Modal Verifikasi Pesanan Wajib (Layar C02)

**Aturan Mutlak:**  
Setiap item menu yang diketuk oleh kasir (baik menu yang memiliki varian/add-on maupun menu standar tanpa opsi) **WAJIB menampilkan Modal Bottom Sheet C02** sebelum dimasukkan ke dalam keranjang:
- Menampilkan nama produk, kategori, dan harga dasar.
- Menampilkan pilihan varian (jika ada) dan pilihan add-on (jika ada).
- Menyediakan kolom input catatan khusus barista/dapur (*"Catatan Khusus Barista / Dapur"*).
- Menyediakan stepper kuantitas fisik (`[-] Qty [+]`).
- Tombol konfirmasi utama: **`Tambah ke Pesanan — Rp XX.XXX`** yang menampilkan total harga satuan terhitung secara real-time.
