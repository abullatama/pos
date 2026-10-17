# Standar Kerangka Layar dan Tata Letak

Berlaku untuk kedua aplikasi (A3 POS Cafe dan Athia Laundry). Kerangka dibuat sekali di `core:ui` (`AppShell`).

## Kerangka Tablet Landscape 11"

```
+-------------------------------------------------------------------------------+
| Bilah Atas 58–64 px (Menyatu Kanvas): [Logo A3]   Tanggal │ Profil · Sync · Print|
+----------+------------------------------------+-------------------------------+
|          |                                    | STRUK PESANAN MANDIRI (36%)   |
| ☕ Kasir  |                                    | (Elevated Drawer 24px)        |
|          |         AREA KATALOG MENU          | [Dine In] [Take Away] [Online]|
| 📋 Order |             (64% - 100%)           |                               |
|          |   (Search bar pil + banner dinamis | Pelanggan │ Meja              |
| 🚫 Habis |    + grid produk 4-5 kolom adaptif)| ----------------------------- |
|          |                                    | Daftar Item (Terurai Rapi)    |
| ⏰ Shift  |                                    | ----------------------------- |
|          |                                    | Subtotal / Pajak / Total      |
| ⚙️ Admin  |                                    | [ --> Swipe to Action >>> ]   |
+----------+------------------------------------+-------------------------------+
  68 dp                Area Katalog                       Panel Struk
 (Floating)
```

- **Bilah Atas (TopBar):** Tinggi 58–64 px menyatu dengan warna kanvas krem `#F4F2E6` tanpa bayangan dan sekat kaku. Menghormati *status bar* tablet. Sisi kiri menampilkan logo resmi transparan murni dan tanggal dinamis (tanpa pengulangan nama toko dan tanpa garis pembatas). Sisi kanan memuat kapsul pil putih bersih untuk profil kasir, status sinkronisasi, printer, dan admin.
- **Bilah Navigasi Samping Kiri Mengambang (Floating Left Rail):** Lebar 68 px mengambang mandiri dengan margin sekeliling, bayangan difusi lembut, dan sudut melengkung modern (20 px atau pil). Item menu aktif berlatar putih murni `#FFFFFF` dengan ikon/teks gelap. Otomatis tersembunyi (*slideOutHorizontally*) saat alur fokus penuh (pembayaran, shift) aktif.
- **Bebas Tabrakan Vertikal:** Seluruh area katalog dan panel struk bebas dari padding bawah artifisial.
- **Panel Struk Melayang Mandiri (Elevated Drawer 24 px):** Berdiri terpisah sebagai kartu berlatar putih dengan elevasi lembut (tanpa border hitam kaku luar). Default tertutup saat kosong (katalog 5 kolom) dan otomatis terbuka saat item ditambahkan (katalog 4 kolom). Dilengkapi tombol tutup (`>`), 3 Segmented Pills (`[Dine In] [Take Away] [Order Online]`), input pelanggan/meja, daftar pesanan terurai multiline, dan tombol geser adaptif `A3SwipeButton`.

---

## Menu Navigasi

| Aplikasi | Item |
| --- | --- |
| Cafe | Kasir (C01), Pesanan (C04), Menu Habis (C07), Shift, Lainnya (Admin) |
| Laundry | Beranda (L01), Order Baru (L03), Cucian (L05), Scan (L07), Lainnya (Admin) |

---

## Pola Layar

| Pola | Pembagian | Dipakai untuk |
| --- | --- | --- |
| Daftar + detail | 40 / 60 | Pesanan Meja, Daftar Cucian |
| Pilih + struk adaptif | 64 / 36 (atau 100 saat tertutup) | Kasir cafe (katalog kiri, struk melayang kanan) |
| Formulir + ringkasan | 60 / 40 | Order laundry (input kiri, ringkasan harga kanan) |
| Layar penuh fokus | 100 | Pembayaran, tutup shift, PIN login, registrasi tablet |

---

## Komponen Wajib

- **Modal Verifikasi Pesanan (C02):** Seluruh menu saat diketuk wajib membuka modal verifikasi pesanan untuk kuantitas, catatan khusus barista, dan opsi varian/add-on sebelum masuk ke struk.
- **Search Bar Kapsul Pil:** Tinggi 48 px berbentuk pil penuh (`9999px`) dengan ikon kaca pembesar dan shortcut `⌘K`.
- **Banner Kategori Slidable:** Baris banner kartu kategori horizontal yang dinamis membaca dari database dan dapat digeser bebas.
- **Nama Produk Multi-baris:** Alokasi baris mandiri (`minLines=2, maxLines=2`) agar nama panjang tidak pernah terpotong.
- **Angka Uang:** Format `Rp 61.400`, rata kanan, angka tabular (`fontFeatureSettings = "tnum"`).
- **Tombol Geser (Swipe-to-Action):** Trek pil 54 px dengan thumb lingkaran geser ke kanan untuk konfirmasi pesanan/bayar.
- **Dialog Konfirmasi:** Tombol aksi menyebutkan aksinya secara eksplisit ("Terima pembayaran", bukan "OK").
- **Keadaan Kosong, Memuat, dan Galat:** Tampil informatif untuk setiap daftar.

---

## Larangan

- Tidak ada kartu bergaya dokumen kaku atau chip berisi istilah teknis (ID, UUID mentah).
- Tidak ada garis border hitam kaku tebal pada kontainer luar kartu kategori dan panel struk.
- Tidak ada elemen navigasi yang melayang menutupi panel transaksi kasir atau tombol aksi di sisi kanan.
- Tidak ada teks nama menu atau rincian add-on yang terpotong elipsis di struk.
- Tidak memakai emoji sebagai ikon grafis antarmuka.
- Tidak memakai warna merek untuk status; status memakai warna fungsi.
