# Keputusan (ADR ringkas)

Cara agent memakai file ini: keputusan berstatus **Diterima** tidak diubah atau dibangun ulang tanpa persetujuan manusia. Bila menemukan alasan kuat untuk mengubahnya, catat di `docs/open-questions.md`, jangan langsung mengubah kode.

## D-01 Satu backend, satu database, dua aplikasi Android

- Status: Diterima
- Konteks: dua usaha milik satu entitas; owner minta dua aplikasi dengan tampilan, ikon, dan pembaruan terpisah.
- Keputusan: satu backend dan satu DB (`business_unit_id` di semua data bisnis), satu web owner, dua aplikasi Android dari satu repositori.
- Akibat: laporan gabungan tanpa rekonsiliasi; pelanggan satu tabel. Aplikasi hanya dibedakan oleh `app_id` untuk versi dan pemantauan, tidak pernah untuk otorisasi.

## D-02 Dua aplikasi, satu repositori, inti bersama

- Status: Diterima
- Keputusan: `app-cafe` dan `app-laundry` hanya cangkang tipis (manifest, navigasi, DI, tema, ikon, nama). Logika di `feature/*` dan `core/*`.
- Aturan: `feature:cafe` dan `feature:laundry` tidak saling bergantung; CI memeriksanya.
- Akibat: perbaikan inti sekali tulis, rilis tetap terpisah.

## D-03 Rilis terpisah per aplikasi

- Status: Diterima
- Keputusan: keystore, `versionCode`/`versionName`, dan tag git per aplikasi (`cafe-v1.2.0`, `laundry-v1.0.3`); rilis bertahap. Satu proyek Firebase berisi kedua aplikasi (plus varian `.debug`).
- Package name sementara: `id.tenang.kasir.cafe` dan `id.tenang.kasir.laundry` (nama usaha belum final; lihat OQ-01). Mengubah package name setelah rilis sama dengan membuat aplikasi baru.

## D-04 Offline-first dengan outbox

- Status: Diterima
- Keputusan: layar membaca dan menulis ke Room; sinkron di belakang layar. Bundel idempoten (UUID dari tablet), respons `ACCEPTED`/`DUPLICATE`/`REJECTED`, delta sync dengan kursor buram.
- Akibat: kasir tidak pernah menunggu jaringan. Server menghitung ulang total.

## D-05 Pembayaran hanya Tunai dan QRIS Statis

- Status: Diterima
- Konteks: payment gateway dan QRIS Dinamis terlalu panjang prosesnya (pendaftaran, webhook, rekonsiliasi).
- Keputusan: tidak ada gateway, QRIS Dinamis, atau webhook pembayaran. QRIS Statis dikonfirmasi kasir dan direkonsiliasi owner per shift dan harian di web. MDR diisi owner secara manual.
- Akibat: jangan membangun fitur terkait. Interface metode bayar dibuat sederhana, tetapi tanpa abstraksi untuk gateway di masa depan.

## D-06 Refund selalu tunai

- Status: Diterima
- Keputusan: refund dicatat sebagai refund tunai, termasuk atas pembayaran QRIS.
- Akibat: tidak ada alur pengembalian dana ke QRIS.

## D-07 Transaksi append-only

- Status: Diterima
- Keputusan: transaksi tidak diedit atau dihapus. Koreksi adalah catatan baru yang menunjuk ke asal dengan alasan dari daftar tetap. Data master memakai soft delete.

## D-08 Printer 58 mm, tag laundry dicetak di kertas roll

- Status: Diterima
- Konteks: iWare C58BT (58 mm, ESC/POS, 384 dot per baris, sekitar 32 karakter). Tidak mendukung stiker label.
- Keputusan: nota, struk, dan tag laundry dicetak di roll; tag memuat QR yang dipindai lewat kamera tablet. Printer di balik `PrinterService`.
- Akibat: perlu spike di M0 pada perangkat asli.

## D-09 Tablet Redmi Pad 2, mode kios

- Status: Diterima, dengan syarat uji M0
- Keputusan: target Android 15 / HyperOS 2, landscape. Mode kios dengan device owner (lock task); cadangan screen pinning. `minSdk` 29.
- Risiko: pembatasan latar belakang HyperOS dapat mematikan sinkron. Wajib diuji di M0.

## D-10 Stack Android

- Status: Diterima
- Keputusan: Kotlin, Jetpack Compose, Material 3, Room, WorkManager, Hilt, Retrofit + OkHttp, Coroutines/Flow, CameraX + ML Kit (scan QR), Crashlytics.
- Aturan: dependensi baru butuh alasan tertulis.

## D-11 Stack backend dan web: belum dipilih

- Status: Terbuka (ditentukan oleh yang mengerjakan backend)
- Keputusan sementara: kontrak lewat `contracts/openapi.yaml` yang netral terhadap stack; API stateless. `AGENTS.md` backend/web ditulis setelah stack dipilih.

## D-12 Uang sebagai bilangan bulat

- Status: Diterima
- Keputusan: `Long` rupiah di Android; integer di API dan DB. Dilarang float untuk uang.

## Tidak dikerjakan dulu

Payment gateway, QRIS Dinamis, webhook pembayaran, rekonsiliasi dinamis, antar-jemput, poin atau member lintas usaha, multi-cabang, aplikasi iOS, split per item, refund non-tunai.

## D-13 Terima, tandai, karantina (menggantikan "tolak bundel")

- Status: Diterima (v2.2)
- Konteks: tablet adalah saksi atas apa yang dibayar pelanggan; menolak bundel membuat penjualan nyata hilang dari laporan, dan jam tablet yang melenceng bisa menolak semuanya selamanya.
- Keputusan: total tidak cocok, jam melenceng, event tua, kas keluar tanpa persetujuan, dan alokasi kas dicatat sesuai angka tablet dengan flag, lalu ditinjau owner. `REJECTED` hanya untuk otorisasi, struktur, dan dependensi.

## D-14 Sesi staf laundry tidak terikat shift

- Status: Diterima (v2.2)
- Keputusan: shift milik kasir yang membukanya. Event `laundry.*` dan `customer.created` tidak punya `shift_id`. Ganti kasir menutup shift; login staf laundry tidak.

## D-15 Sinkron foreground-first, penyimpanan Tink

- Status: Diterima (v2.2)
- Keputusan: sinkron utama berupa coroutine di foreground, WorkManager cadangan. Data sensitif memakai DataStore + Tink, kunci di Android Keystore; Jetpack Security Crypto tidak dipakai (deprecated).

## D-16 Distribusi tablet tanpa akun Google

- Status: Diterima, dengan uji M0 (v2.2)
- Keputusan: device owner mensyaratkan tablet tanpa akun, jadi tablet produksi memakai APK internal yang dipasang senyap atau lewat USB; Firebase App Distribution hanya untuk ponsel penguji.

## D-17 Identitas visual per aplikasi

- Status: Diterima (2026-10-13)
- Keputusan: cafe memakai panduan merek AA Coffeeshop (alabaster, obsidian, amber; sudut 4 px; dok persegi), laundry memakai panduan Athia Laundry House (linen, mawar, plum; sudut membulat; dok pil). Teks panduan merek 12 px dinaikkan ke 14 px untuk tablet, teks putih di atas hijau WhatsApp diganti teks gelap, dan status memakai warna fungsi terpisah dari warna merek. Berkas: `docs/ui/DESIGN-*.md`.

## D-18 Arsitektur UI POS Tablet: Left Navigation Rail & Modern Organic Clean Utility

- Status: Diterima (2026-10-17)
- Konteks: Bilah bawah mengambang (floating bottom bar) memotong 20% ruang vertikal tablet landscape dan menabrak panel keranjang serta tombol aksi di kanan bawah. Gaya kotak kaku 4 dp terasa dingin dan tidak mencerminkan terminal POS modern.
- Keputusan:
  1. Mengadopsi arsitektur Left Navigation Rail (68 dp floating dock) menggantikan bottom bar, membebaskan 100% ruang vertikal konten.
  2. Mengadopsi filosofi visual Modern Organic & Clean Utility (design2.md & contoh.jpeg): sudut melengkung 16–24 dp, search bar pil 48 dp, banner kategori dinamis slidable horizontal, grid produk adaptif 4–5 kolom.
  3. Warna cafe: Palet monokrom bernilai tinggi (kanvas krem hangat `#F4F2E6`, permukaan putih `#FFFFFF`, tinta `#141414`) dengan aksen fokus Deep Forest Artisan Green (`#0A3D24`). Item menu aktif di rail kiri berlatar putih murni (`#FFFFFF`) dengan teks/ikon hitam pekat.
  4. TopBar menyatu penuh dengan kanvas krem, menampilkan logo resmi A3 transparan (tiga 'A' saling mengunci) dan tanggal dinamis (tanpa nama toko berulang dan tanpa garis divider).
  5. Struk pesanan: Purchase receipt drawer 24 dp melayang tanpa border hitam kaku luar, auto-expand saat item ditambahkan dan collapse saat kosong, segmented pill `[Dine In] [Take Away] [Order Online]`, rincian add-on terurai bertingkat, dan tombol geser taktil adaptif `A3SwipeButton` (Solusi B).
  6. Modal verifikasi pesanan C02: Setiap item menu yang diketuk (baik bervarian maupun standar) wajib membuka modal sheet untuk kuantitas dan catatan barista/dapur sebelum masuk ke struk.
- Berkas rujukan: `docs/ui/DESIGN-cafe.md`, `docs/ui/layout-standard.md`, `design2.md`, `contoh.jpeg`.

## D-19 Standar Performa POS Tablet: Non-blocking Hardware Execution, Snappy Transitions, dan SingleTop Navigation

- Status: Diterima (2026-10-18)
- Konteks: Pada tablet Redmi Pad 2 (Snapdragon 680, Adreno 610, 90Hz), ditemukan delay perpindahan layar beberapa detik saat kasir menekan "Langsung Bayar di Kasir" atau menggeser "Kirim ke Dapur", tombol navigasi kadang macet/perlu ditekan berkali-kali, keyboard virtual mengangkat panel struk, dan animasi perpindahan halaman terasa patah-patah.
- Keputusan:
  1. **Non-blocking Hardware Execution:** Operasi hardware fisik eksternal (koneksi dan pencetakan ESC/POS via Bluetooth SPP/USB yang memakan 1,5–5 detik) DILARANG menahan (*suspend/block*) navigasi kasir. Pencatatan pesanan ke database Room lokal (< 5ms) langsung menyelesaikan aksi dan memicu navigasi seketika (0 detik delay); tugas cetak tiket fisik didelegasikan ke background worker coroutine (`viewModelScope.launch(ioDispatcher)`).
  2. **Clean SingleTop Navigation:** Seluruh navigasi top-level (Kasir, Pesanan, Menu Habis, Shift) dan pemicu aksi antar layar (Kirim ke Dapur $\rightarrow$ Pesanan, Pesanan Baru $\rightarrow$ Kasir) WAJIB menggunakan kontrak `popUpTo(findStartDestination().id) { saveState = true }`, `launchSingleTop = true`, dan `restoreState = true`. Dilarang melakukan *raw navigate* tanpa SingleTop yang menyebabkan tumpukan rute duplikat (`[KASIR, PESANAN, KASIR]`) yang merusak sinkronisasi rute aktif dan memblokir klik Navigation Rail.
  3. **Snappy Navigation Transitions:** Transisi navigasi top-level menggunakan Fast Fade (100ms enter / 80ms exit) menggantikan crossfade default Compose (~700ms). Menghilangkan beban *alpha blending* berat dua layar 11" penuh pada GPU tablet, menghasilkan perpindahan menu yang terasa instan dan taktil.
  4. **Touch Target Standard (Aksesibilitas Tablet):** Seluruh elemen interaktif tombol dan tab (seperti tombol "Pesanan Baru" dan tab "Meja Terbuka / Riwayat Selesai") wajib memiliki tinggi sentuh minimal 44–48 dp.
  5. **Soft Input Pan Mode:** `android:windowSoftInputMode="adjustPan"` diterapkan pada manifest kedua aplikasi agar keyboard virtual tidak menciutkan viewport layout split-screen atau mengangkat panel struk secara paksa saat kasir mengetik.
- Berkas rujukan: `feature/cafe/src/main/kotlin/id/a3/feature/cafe/presentation/CafeMenuViewModel.kt`, `app-cafe/src/main/java/id/tenang/kasir/cafe/navigation/CafeNavHost.kt`, `app-cafe/src/main/java/id/tenang/kasir/cafe/MainActivity.kt`.

## D-20
- Keputusan (2026-11-05): notifikasi WhatsApp otomatis laundry hanya dikirim saat status **Siap**. Tidak ada pesan otomatis untuk Diterima (nota), Diproses, Diambil, maupun pengingat belum diambil kecuali pemilik dan pengelola backend menyepakati lain. Disepakati pemilik proyek dengan pengelola backend.
- Alasan: mengurangi jumlah pesan dan risiko nomor Fonnte diblokir; pelanggan hanya perlu tahu kapan cucian siap.
- Dampak: spek bagian 13 (tiga jenis pesan) dan PRT-08 menyebut nota saat pesanan dibuat serta pengingat; spek belum diubah, keputusan ini yang berlaku. Tablet tidak berubah: tablet tetap mengirim semua event laundry.status_changed dan server yang menentukan pesan. Tombol "Kirim via WA" manual tetap tersedia untuk status apa pun.
- Catatan: penerapan di server dan perlakuan cuci ulang (Siap kedua kali) dikonfirmasi pengelola backend.
