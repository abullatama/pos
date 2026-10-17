# Dokumen Spesifikasi Desain UI/UX: Green Grounds Coffee

Dokumen ini mendefinisikan sistem desain (*Design System*), tata letak (*layout*), arsitektur komponen, dan interaksi visual untuk ekosistem aplikasi **Green Grounds Coffee**, yang terdiri dari dua platform utama:
1. **Tablet / Desktop POS (Point of Sale)** — Digunakan oleh kasir untuk mengelola pesanan di tempat (*Dine In*), bungkus (*Take Away*), dan pesanan daring (*Order Online*).
2. **Mobile Customer App (iOS/Android)** — Digunakan oleh pelanggan untuk memesan menu, memantau status pesanan secara langsung, dan mengakses program loyalitas.

---

## 1. Identitas Visual & Filosofi Desain

* **Gaya Visual**: *Modern Organic & Clean Utility*. Menggabungkan nuansa hangat khas kedai kopi melalui latar belakang warna krem lembut (*warm cream*) dengan aksen hijau hutan tua (*deep forest green*) yang tegas, bersih, dan terstruktur.
* **Karakteristik Utama**:
  * Penggunaan sudut membulat (*rounded corners* dan *pill shapes*) secara konsisten pada tombol, kolom pencarian, dan kartu produk.
  * Pemisahan elemen menggunakan garis tepi tipis (*1px solid border*) berwarna hijau tua alih-alih bayangan tebal (*heavy drop shadows*), menciptakan kesan ringan dan rapi.
  * Kode warna kontekstual berdasarkan kategori menu (Hijau untuk *Coffee*, Merah Muda/Salm untuk *Tea*, dan Kuning Mustard/Ochre untuk *Snack*).

---

## 2. Palet Warna (Color Tokens)

Berikut adalah spesifikasi token warna yang diekstraksi dari desain UI:

### Warna Dasar & Permukaan (Background & Surface)
| Token | Hex Estimasi | Penggunaan |
| :--- | :--- | :--- |
| `--bg-canvas` | `#F4F2E6` | Latar belakang utama aplikasi (POS & Splash/Home Mobile) |
| `--surface-white` | `#FFFFFF` | Latar belakang kartu produk, panel struk, input, dan *bottom sheet* |
| `--surface-subtle` | `#F8F8F8` | Latar belakang kotak gambar produk pada tampilan daftar mobile |
| `--border-primary` | `#2B6CB0` / `#3B6E52` | Garis tepi kartu produk, input pencarian, dan kontainer (`1px solid #3B6E52`) |
| `--border-muted` | `#E5E7EB` | Garis pemisah (*divider*) daftar item pada aplikasi mobile dan detail pembayaran |

### Warna Merek & Aksen Kategori (Brand & Category Accents)
| Token | Hex Estimasi | Penggunaan |
| :--- | :--- | :--- |
| `--brand-green-primary` | `#055B2C` | Warna utama merek, kartu kategori *Coffee* aktif, tombol utama (CTA), tombol `+` mobile |
| `--brand-green-dark` | `#062614` | Kartu pelacak pesanan aktif (*Your Order*) & *Bottom Navigation Bar* pada mobile |
| `--accent-tea-coral` | `#F8A695` | Warna latar belakang kartu kategori *Tea* saat aktif (*selected*) |
| `--accent-snack-ochre` | `#CE9A48` | Warna latar belakang kartu kategori *Snack* saat aktif (POS & Header Mobile) |

### Warna Semantik & Status (Semantic & Status)
| Token | Hex Estimasi | Penggunaan |
| :--- | :--- | :--- |
| `--status-danger` | `#F65B5B` | Badge *"Need to re-stock"*, indikator notifikasi merah, dan badge keranjang belanja |
| `--status-warning-star` | `#FACC15` | Ikon bintang rating produk pada aplikasi mobile |

### Warna Tipografi (Typography Colors)
| Token | Hex Estimasi | Penggunaan |
| :--- | :--- | :--- |
| `--text-primary` | `#141414` | Judul produk, nama pelanggan, nominal harga total |
| `--text-secondary` | `#6B7280` | Harga satuan, deskripsi produk, sub-label formulir, jumlah item |
| `--text-brand` | `#055B2C` | Tanggal pada header POS, teks pada latar belakang terang |
| `--text-on-dark` | `#FFFFFF` | Teks pada kartu kategori aktif, tombol utama, dan navigasi bawah mobile |

---

## 3. Tipografi (Typography System)

Desain menggunakan keluarga huruf **Geometric Sans-Serif** yang modern dan mudah dibaca pada berbagai ukuran layar.

* **Font Utama (UI & Body)**: `Plus Jakarta Sans`, `Poppins`, atau `Inter`
* **Font Logo**: Geometric Sans-Serif Bold/Black, *All-Caps*, dengan *line-height* rapat (`0.95`) dan *letter-spacing* sedikit rapat (`-0.02em`).

### Skala Tipografi (Type Scale)
1. **Heading 1 (Judul Kategori Banner / Header Mobile)**: `24px` – `28px` | `Font-Weight: 700 (Bold)`
2. **Heading 2 (Judul Seksi Mobile - "People Choice")**: `18px` | `Font-Weight: 700 (Bold)`
3. **Title Card (Nama Produk POS & Mobile)**: `14px` – `15px` | `Font-Weight: 700 (Bold)`
4. **Receipt / Modal Header ("Purchase Receipt")**: `15px` | `Font-Weight: 700 (Bold)`
5. **Body / Input Text**: `13px` – `14px` | `Font-Weight: 500 (Medium)`
6. **Caption / Deskripsi Produk / Sub-label**: `11px` – `12px` | `Font-Weight: 400 (Regular)`
7. **Badge / Pill Status**: `11px` | `Font-Weight: 500 (Medium)`

---

## 4. Sistem Grid, Spasi & Radius (Layout & Spacing)

* **Border Radius**:
  * `--radius-pill`: `9999px` (Digunakan pada Search Bar, Tombol CTA, Tab Order, Input Form, Badge Status).
  * `--radius-card-lg`: `20px` – `24px` (Digunakan pada Panel Struk, Kartu Kategori, Kartu Status Order Mobile).
  * `--radius-card-md`: `16px` (Digunakan pada Kartu Produk di Grid POS).
  * `--radius-thumb`: `12px` (Digunakan pada thumbnail gambar produk di Mobile dan Daftar Pesanan).
* **Spasi Grid (POS)**:
  * Padding Kontainer Utama: `24px` – `32px`
  * Jarak Antar Kartu (*Grid Gap*): `14px` – `16px`

---

## 5. Spesifikasi Platform 1: Tablet / Web POS

Antarmuka POS memiliki tata letak adaptif yang berubah berdasarkan status panel transaksi di sisi kanan.

### 5.1. Top Header Bar
Berada di bagian paling atas dengan latar belakang menyatu dengan `--bg-canvas`:
* **Sisi Kiri**:
  * Logo **GREEN GROUNDS COFFEE** (rata kiri, dengan ikon biji kopi di atas huruf 'G').
  * Informasi Tanggal (`Thursday, 23 June`) menggunakan warna `--text-brand` (`#055B2C`), `font-weight: 600`.
* **Sisi Kanan**:
  * Indikator Total Pesanan: Teks `Total : 20 Orders` (`13px`, teks abu-abu tua).
  * Tombol **Report**: Bentuk pil putih (`#FFFFFF`), teks `Report` disertai ikon dokumen di sebelah kanan.
  * Tombol **Notifikasi**: Lingkaran putih (`40px x 40px`) dengan ikon lonceng dan badge bulat merah (`1`) di sudut kanan atas.
  * **Profil Kasir**: Kapsul/pil putih berisi foto avatar bulat di kiri, nama (`Samantha W` - Bold), dan jabatan (`Cashier` - Regular, abu-abu).

### 5.2. Search Bar
* Lebar penuh mengikuti area konten utama (menyusut otomatis saat panel struk kanan terbuka).
* Tinggi: `48px`, Latar belakang: `#FFFFFF`, Border: `1px solid #3B6E52`, Radius: `9999px`.
* **Elemen Dalam**: Ikon kaca pembesar (`Search`) dan placeholder di sisi kiri; ikon pintasan keyboard (`⌘`) berwarna hijau di sisi kanan.

### 5.3. Kartu Kategori Menu (Category Banner Cards)
Terdiri dari 3 kolom dengan lebar setara (`grid-template-columns: repeat(3, 1fr)`), tinggi sekitar `120px`:
* **Struktur Kartu**:
  * Sudut Kiri Atas: Badge status berbentuk pil (`Available` dengan border gelap/putih, atau `Need to re-stock ⓘ` dengan latar merah `#F65B5B` dan teks putih).
  * Sudut Kiri Bawah: Nama Kategori (`Coffee`, `Tea`, `Snack`) ukuran besar dan jumlah item di bawahnya (`50 Items`, `20 Items`, `10 Items`).
  * Sudut Kanan Bawah: Ilustrasi vektor *line-art* abstrak berukuran besar yang terpotong (*clipped*) oleh batas kartu (Latte Art untuk *Coffee*, Es Batu/Daun untuk *Tea*, Kemasan/Croissant untuk *Snack*).
* **State Interaksi (3 Variasi Layar)**:
  1. **Coffee Aktif**: Latar kartu *Coffee* menjadi `#055B2C` (Hijau Tua), teks dan border badge menjadi putih. Kartu *Tea* dan *Snack* berlatar putih.
  2. **Tea Aktif**: Latar kartu *Tea* menjadi `#F8A695` (Coral/Salmon Pink), teks tetap gelap (`#141414`), ilustrasi vektor berwarna putih semi-transparan. Ilustrasi pada kartu *Coffee* berubah menjadi hijau `#055B2C` dan *Snack* menjadi cokelat keemasan `#CE9A48`.
  3. **Snack Aktif**: Latar kartu *Snack* menjadi `#CE9A48` (Mustard Gold), teks dan badge menjadi putih.

### 5.4. Grid Katalog Produk (Product Grid)
* **Perilaku Responsif**:
  * **Mode Katalog Penuh (Tanpa Panel Struk)**: Menampilkan **5 kolom x 3 baris** (15 item per halaman).
  * **Mode Transaksi Aktif (Dengan Panel Struk)**: Menyusut menjadi **4 kolom x 3 baris** (12 item terlihat).
* **Komponen Kartu Produk**:
  * Kontainer: Latar `#FFFFFF`, border `1px solid #3B6E52`, radius `16px`, padding `14px`.
  * Area Gambar: Berada di tengah atas, tinggi sekitar `85px`, objek minuman/makanan terisolasi dengan latar belakang bersih (transparan/putih).
  * Informasi Produk (Kiri Bawah): Nama produk dalam teks tebal (`14px`) dan harga di bawahnya (`$4.2`, `12px`, abu-abu).
  * Tombol Tambah (Kanan Bawah): Tombol bulat (`36px x 36px`), latar putih, border `1px solid #3B6E52`, ikon `+` di tengah.

### 5.5. Panel Samping: Purchase Receipt Drawer
Muncul di sisi kanan layar (lebar sekitar `320px` – `360px`), latar belakang `#FFFFFF`, radius `24px`.
1. **Header Struk**:
   * Kiri: Tombol bulat hijau (`#055B2C`) dengan ikon panah kanan (`>`) untuk menutup/menyembunyikan panel.
   * Tengah: Teks `Purchase Receipt` (Bold) dan nomor ID `#27362` di bawahnya.
   * Kanan: Tombol bulat putih dengan border hijau dan ikon daftar (`☰` / list).
2. **Pemilih Tipe Pesanan (Segmented Pill Control)**:
   * Kontainer pil dengan border `1px solid #3B6E52`.
   * 3 Opsi: `Dine In`, `Take Away`, `Order Online`.
   * Tab aktif (`Dine In`) memiliki latar penuh `#055B2C` dan teks putih.
3. **Input Informasi Pelanggan**:
   * Tata letak 2 kolom (`Customer name` dan `Table`).
   * Input berbentuk pil (`border: 1px solid #3B6E52`, tinggi `40px`). Kolom `Table` memiliki ikon chevron dropdown (`v`) di sisi kanan (`B12 - Indoor`).
4. **Daftar Pesanan (Order List)**:
   * Kontainer berbingkai (`border: 1px solid #3B6E52`, radius `16px`, tinggi fleksibel/scrollable).
   * **Item Baris**:
     * Kiri: Thumbnail produk (`48px x 48px`) dengan latar krem lembut.
     * Tengah: Nama item (`Americano`), detail harga & ukuran (`$8.4 x2 • Medium`), serta indikator catatan (`📄 Less Sugar`).
     * Kanan: Total harga baris (`$16.8` tebal) di atas, dan kontrol kuantitas (*stepper* `- 2 +` berbentuk pil abu-abu muda) di bawah.
5. **Rincian Pembayaran (Payment Details)**:
   * Baris `Subtotal` (`$59`) dan `Tax` (`$5.9`).
   * Garis pemisah tipis (`1px solid #F0F0F0`).
   * Baris `Total` (`$64.8` tebal).
6. **Tombol Utama (Slide / Click to Place Order)**:
   * Bentuk pil penuh, latar `#055B2C`, tinggi `52px`.
   * Kiri: Lingkaran putih dengan ikon panah kanan (`→`).
   * Tengah: Teks `Place Order $26.4` (Putih, Semi-Bold).
   * Kanan: Ikon tiga chevron (`>>>`) yang mengindikasikan interaksi geser (*swipe*) atau konfirmasi cepat.

---

## 6. Spesifikasi Platform 2: Aplikasi Mobile (iOS)

Dirancang untuk layar smartphone (rasio 390x844 pt) dengan pendekatan navigasi *bottom bar* dan *sheet* berlapis.

### 6.1. Layar 1: Splash Screen
* Latar belakang: `--bg-canvas` (`#F4F2E6`).
* Dekorasi: Ilustrasi garis vektor abstrak berukuran besar dengan opasitas rendah (`#E5E3D5`) di sudut kiri atas, kanan tengah, dan kiri bawah.
* Tengah: Logo **GREEN GROUNDS COFFEE** berwarna hijau `#055B2C`.
* Bawah: Teks versi aplikasi `App V.2.2.1` (`12px`, abu-abu) tepat di atas *iOS Home Indicator*.

### 6.2. Layar 2: Home Screen
1. **Top App Bar**:
   * Kiri: Logo merek berukuran kompak.
   * Kanan: Dua tombol ikon bulat berlatar putih (Notifikasi dengan badge merah `1`, dan Menu Hamburger `≡`).
2. **Carousel Banner Promosi**:
   * Kartu putih dengan sudut membulat (`20px`), menampilkan indikator titik *slider* di kiri atas.
   * Konten Kiri: Judul `REFER & EARN`, deskripsi singkat cashback 10%, dan tombol pil hitam/hijau gelap `Refer Now`.
   * Konten Kanan: Foto staf/barista mengenakan celemek hijau.
3. **Kartu Pelacak Pesanan Aktif (Live Order Tracker)**:
   * Latar belakang: Hijau Sangat Gelap (`#062614`), radius `20px`, padding `16px`.
   * Baris Atas: `Your Order (+)` di kiri, ID pesanan `#27362` di kanan.
   * Baris Tengah: `Estimated Time` di kiri, waktu hitung mundur `1:02` di kanan.
   * **Progress Bar**: Track horizontal dengan bagian terisi berwarna hijau cerah (`#10893E`), titik lingkaran indikator di tengah, dan sisa track bermotif garis diagonal (*striped pattern*).
   * Baris Bawah: Teks status `Your order is being prepared, please wait.` (`12px`, hijau muda/putih redup).
4. **Seksi Rekomendasi ("People Choice")**:
   * Berada di dalam kontainer putih dengan sudut atas membulat.
   * Header Seksi: Judul `People Choice(4)` di kiri, tautan `See all` di kanan.
   * **Kartu Produk Horizontal (List Item)**:
     * Sisi Kiri (70%): Nama produk (`Pour Over`), badge bintang dan skor (`⭐ 4.8`) di sebelahnya, deskripsi 2 baris (`A balanced mix of espresso...`), dan harga (`$3.8`) di bagian bawah.
     * Sisi Kanan (30%): Kotak gambar produk (`80px x 80px`, latar `#F8F8F8`, radius `14px`) dengan tombol bulat hijau (`#055B2C`) berikon `+` putih yang menempel (*overlapping*) di sudut kanan atas gambar.
5. **Bottom Navigation Bar**:
   * Kontainer melayang/menempel di bawah dengan latar `#062614`, sudut membulat (`24px`).
   * 4 Menu Navigasi: `Home` (Aktif, dengan indikator garis putih di bawahnya), `Order`, `Starred`, `Profile`.

### 6.3. Layar 3: Category Menu Screen ("Snack")
1. **Colored Category Header**:
   * Latar belakang menggunakan warna identitas kategori (`#CE9A48` untuk *Snack*) lengkap dengan motif watermark vektor di latar belakang.
   * Baris Navigasi Atas: Tombol bulat kembali (`←`) di kiri, judul kategori `Snack` (`22px`, putih, tebal) di tengah, dan tombol keranjang belanja di kanan dengan badge merah (`2`).
   * **Search Bar Transparan**: Berbentuk pil dengan latar `rgba(255, 255, 255, 0.25)`, ikon kaca pembesar dan teks placeholder putih/krem.
2. **Kontainer Menu (Bottom Sheet Style)**:
   * Latar belakang `#FFFFFF` dengan sudut atas membulat (`border-radius: 28px 28px 0 0`).
   * **Sub-Category Filter Pills**: Baris tab horizontal yang dapat digeser (`All`, `Special`, `Snacks`, `Salad`). Tab aktif (`All`) berlatar `#062614` dengan teks putih; tab tidak aktif berlatar `#F4F5F7` dengan teks gelap.
   * **Daftar Produk**: Menampilkan daftar item secara vertikal menggunakan komponen **Kartu Produk Horizontal** yang sama seperti pada bagian *People Choice* di Home Screen, dipisahkan oleh garis pembatas tipis (`1px solid #F0F0F0`).

---

## 7. Referensi Implementasi CSS Variables

Salin blok kode berikut untuk memulai pengaturan *stylesheet* dasar pada proyek web atau aplikasi hibrida Anda:

```css
:root {
  /* Colors - Backgrounds & Surfaces */
  --bg-canvas: #F4F2E6;
  --surface-white: #FFFFFF;
  --surface-muted: #F6F6F6;
  
  /* Colors - Brand & Categories */
  --brand-primary: #055B2C;
  --brand-dark: #062614;
  --category-coffee: #055B2C;
  --category-tea: #F8A695;
  --category-snack: #CE9A48;
  
  /* Colors - Status & Accents */
  --status-danger: #F65B5B;
  --rating-star: #FACC15;
  
  /* Colors - Text & Borders */
  --text-main: #141414;
  --text-muted: #6B7280;
  --text-inverse: #FFFFFF;
  --border-green: #3B6E52;
  --border-light: #E5E7EB;

  /* Border Radius */
  --radius-pill: 9999px;
  --radius-xl: 24px;
  --radius-lg: 16px;
  --radius-md: 12px;

  /* Typography */
  --font-sans: 'Plus Jakarta Sans', 'Poppins', -apple-system, BlinkMacSystemFont, sans-serif;
}
```