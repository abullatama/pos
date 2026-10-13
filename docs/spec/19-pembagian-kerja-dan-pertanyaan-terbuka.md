## 19. Pembagian kerja dan pertanyaan terbuka

App Android mengerjakan semua yang terjadi di tablet, sedangkan backend dan web mengerjakan data, integrasi eksternal (Fonnte), dan layar owner. Kolom Disepakati berdua berisi hal yang menyentuh app dan backend sekaligus (format, rumus, kode, aturan), jadi harus disamakan dulu sebelum coding. Angka dan kebijakan bisnis tidak diisi di sini dan ditandai untuk ditanyakan ke owner.

**Pembagian per area**

| Area | Android | Backend dan web | Disepakati berdua |
| --- | --- | --- | --- |
| Setup perangkat | Layar setup, kiosk, test printer, menu admin | Kode pendaftaran, persetujuan dan pencabutan perangkat | Status perangkat Menunggu, Aktif, Dicabut dicek saat app dibuka dan tiap sync. Dicabut berarti token ditolak (`DEVICE_REVOKED`); data lokal yang belum terkirim ditahan dan ditinjau owner |
| Login | Layar PIN, Penyimpanan terenkripsi (DataStore + Tink), auto-lock | Endpoint login, token, kunci akun | Access token pendek (usulan 15 menit) dan refresh token berotasi; klaim minimal: user\_id, role, business\_unit\_id, device\_id; PIN di-hash di server; akun terkunci bertahap |
| Bootstrap dan delta | Layar progres, Room, worker delta, migrasi | Endpoint bootstrap dan changes, soft delete | Cursor opaque dari server, 200 record per halaman, bisa resume; soft delete minimal 30 hari; cursor kedaluwarsa dibalas 410 |
| Shift | Buka dan tutup, hitung kas buta, laporan Z | Simpan dan validasi ulang, tutup paksa dari web | Kas seharusnya dihitung ulang server dari event; laporan Z memuat total per metode, void, refund, diskon, hitungan kasir, dan selisih |
| Transaksi cafe | POS, keranjang, tiket dapur, pesanan terbuka | Master menu, validasi harga | Rumus total satu kali dan diuji dengan test vector yang sama; backend menolak `TOTAL_MISMATCH` |
| Transaksi laundry | Order, tag dan scan, status, pengambilan | Master layanan, event status, WhatsApp via Fonnte | Status berjalan satu arah; mundur butuh PIN manager dan tercatat sebagai event; harga disimpan sebagai snapshot |
| Pembayaran | Layar bayar, kembalian, QRIS Statis manual | Pencatatan, rekonsiliasi QRIS Statis harian | Idempotency key = UUID dari tablet per upaya bayar; hanya Tunai dan QRIS Statis |
| Struk dan printer | Printer service, template, antrean cetak | Identitas usaha dan nota digital | Nomor unik tanpa bentrok antar tablet meski offline; field wajib: identitas usaha, nomor, waktu, kasir, item, subtotal/diskon/pajak, metode bayar, total |
| Koreksi | PIN manager, riwayat transaksi, void, refund, diskon | Model transaksi pembalik, preset diskon | Daftar alasan tetap plus catatan untuk Lainnya; semua koreksi berupa transaksi pembalik; refund selalu tunai |
| Sync engine | Outbox, worker, indikator, antrean bermasalah | Endpoint bundel idempotent, validasi, jam server | Bagian 12; retry hanya untuk 429 dan 5xx; 422 tidak diulang dan masuk antrean bermasalah |
| Operasional | Crashlytics, diagnostik, update, tes otomatis | Status Perangkat, notifikasi owner, remote config, backup | Log JSON terstruktur tanpa PIN, token, atau data pribadi; saklar fitur lewat `GET /v1/config` dengan default aman (mati); SOP darurat satu halaman |

**Tugas backend tanpa layar**

- Menerima bundel sinkron dan delta, menghitung ulang total, dan menyimpan jam terima.
- Integrasi Fonnte: kirim WhatsApp saat status Siap, pengingat cucian tidak diambil, log status kirim.
- Remote config, versi minimum, dan notifikasi peringatan ke owner.
- Backup database harian dengan uji pemulihan berkala.

**Cara kerja berdua**

- Kontrak OpenAPI v0 dan mock server dibuat lebih dulu supaya Android bisa berjalan tanpa menunggu backend.
- Perubahan kontrak lewat pull request dengan changelog. Lingkungan staging terpisah dari produksi.
- Pengerjaan internal berurutan per modul; rilis bersama.

**Asumsi yang dipakai**

1. Satu tablet melayani satu usaha, dan ada satu printer per outlet.
2. Staf laundry memakai tablet laundry yang sama untuk scan tag, dengan PIN sendiri.
3. MVP tidak memotong stok bahan per resep. Biaya bahan dihitung dari pembelian dan stok akhir bulanan.
4. WhatsApp laundry lewat Fonnte, dipanggil backend, memakai nomor khusus usaha.
5. Hanya QRIS Statis yang tersedia saat rilis; payment gateway dan QRIS Dinamis dikeluarkan dari MVP.
6. Kontrak OpenAPI adalah sumber kebenaran antara app dan backend.
7. Angka bertanda usulan di bagian 7 dan 8 hanya titik awal sampai owner memutuskan.

**Pertanyaan terbuka (centang yang sudah terjawab, koreksi asumsi yang salah)**

Perangkat

- [x] Tipe printer: iWare C58BT, thermal 58 mm, Bluetooth dan USB, ESC/POS. Lebar kertas di Pengaturan: 58 mm.
- [x] Model tablet: Redmi Pad 2 (11 inci, Android 15, HyperOS 2). `minSdk` usulan 29.

Aturan bisnis

- [ ] Pajak cafe: ada atau tidak, berapa persen, ditambahkan atau sudah termasuk harga. Apakah laundry dikenai pajak.
- [ ] Biaya layanan: dipakai atau tidak, berapa persen.
- [ ] Nilai awal Pengaturan: kas awal standar, toleransi selisih kas, batas Kas Keluar tanpa PIN, dan daftar preset diskon.
- [ ] Daftar tarif laundry: per kg, per pcs, express, berat minimum, kelipatan berat, dan estimasi selesai.
- [ ] Jam buka tiap usaha dan zona waktu.
- [ ] Siapa pemegang PIN manager, dan apakah staf laundry perlu perangkat tambahan.
- [ ] Apakah nomor struk harus berurutan tanpa lompat (kebutuhan owner atau akuntan).
- [ ] Cara membagi biaya bersama antar-usaha di laba-rugi.

Tim dan operasional

- [ ] Nama usaha dan package name final (usulan `id.tenang.kasir.cafe` dan `id.tenang.kasir.laundry`), serta pemilik keystore (satu per aplikasi).
- [x] Akun layanan: Firebase dan Fonnte memakai akun milik salah satu pembuat. Disarankan akun Google khusus dengan kedua pembuat sebagai Owner.
- [ ] Jalur distribusi: tablet produksi lewat APK internal yang dipasang senyap (device owner) atau USB; ponsel penguji lewat Firebase App Distribution. Kompatibilitas HyperOS diuji di M0.
- [ ] Hosting backend, biaya bulanan berjalan (server, Fonnte), dan siapa yang menanggung.
- [ ] Siapa yang merawat app dan backend setelah rilis.
- [ ] Lokasi repositori kontrak OpenAPI dan aturan perubahannya.
- [ ] Kebijakan privasi singkat dan persetujuan pelanggan untuk notifikasi WhatsApp, termasuk penerimaan risiko Fonnte yang tidak resmi.

Teknis (usulan yang perlu disepakati berdua)

- [ ] Diputuskan (v2.2): total tidak cocok, jam tablet melenceng, dan event tua diterima lalu ditandai dan dikarantina; hanya masalah otorisasi atau struktur yang ditolak (bagian 12).
- [ ] Diputuskan (v2.2): kode pendaftaran perangkat sekali pakai dari web, ditambah persetujuan owner (menggantikan antrean permintaan terbuka).
- [ ] Penambahan kontrak: endpoint `POST /v1/files`, field `retryable` pada penolakan, dan kolom `wa_opt_out` pada pelanggan.
- [ ] Kategori pengeluaran Biaya admin pembayaran (diinput owner) ditambahkan ke daftar kategori tetap.
- [ ] Modal di tablet (untuk snapshot) atau snapshot modal diisi server dari riwayat modal.
- [ ] Hak akses layar web untuk Manager (bagian 14) dan kanal pengingat catat pengeluaran.
- [ ] Penanda "QRIS Sudah dicek" per transaksi dibawa sebagai daftar ID di event shift.closed (diputuskan v2.2), bukan sebagai kolom `qris_checked` pada pembayaran.

Tambahan v2.2

- [ ] Satu QRIS merchant per usaha atau satu untuk keduanya (menentukan rekonsiliasi per usaha).
- [ ] Batas nilai dan umur refund transaksi lama di tablet (usulan Rp100.000 dan 14 hari).
- [ ] Pengambilan sebagian untuk laundry per kg: dicatat per jumlah potong atau cukup penanda terakhir.
- [ ] Pemasangan senyap APK oleh device owner dan pengecualian akun Mi di HyperOS (diuji di M0).
