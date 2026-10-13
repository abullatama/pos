## 1. Ringkasan, lingkup, dan keputusan

Dokumen ini menggabungkan Spesifikasi MVP (Android) dan Spesifikasi Backend dan Web Owner menjadi satu spesifikasi terpadu, dengan **payment gateway dan QRIS Dinamis dihapus dari MVP**. Strukturnya mengikuti urutan umum spesifikasi industri: produk dan lingkup, peran, kebutuhan fungsional, aturan bisnis, arsitektur, data, kontrak antarmuka, keamanan, operasional, pengujian, lalu rencana pelaksanaan.

MVP adalah **dua aplikasi kasir Android terpisah** (satu untuk cafe, satu untuk laundry) yang dibangun dari satu repositori dan satu logika bersama, dengan satu backend, satu database, dan satu web owner untuk satu entitas. Transaksi tetap berjalan saat internet putus. Cafe dan laundry go-live bersamaan, tanpa pilot.

**Aturan rujukan**

- Aturan bisnis dan alur mengikuti bagian 3 sampai 8 dokumen ini.
- Kontrak antarmuka mengikuti berkas OpenAPI; bagian 11 hanyalah catatan awalnya. Setelah OpenAPI ada, berkas itulah yang berlaku.
- Bila ada konflik atau ambiguitas, tulis sebagai pertanyaan terbuka di bagian 19 dan jangan menebak.
- Backend dan app netral terhadap stack: satu-satunya pengikat adalah OpenAPI, sehingga bahasa, framework, dan hosting backend bebas dipilih dan diganti.

**Cara agent memakai dokumen ini:** dokumen sepanjang ini terlalu besar untuk dimuat sekaligus. Agent memuat `AGENTS.md` setiap sesi, lalu hanya bagian yang relevan dari `docs/spec/` (satu berkas per bagian) bersama brief milestone yang sedang dikerjakan di `docs/milestones/`.

**Tujuan**

- Penjualan dan pembayaran tercatat rapi, dan kas shift cocok setiap hari.
- Profit tercatat jelas lewat laba-rugi ringan (omzet otomatis, pengeluaran dicatat, biaya bahan dari pembelian dan stok akhir).
- Kontrol dasar terhadap kecurangan kasir.

**Pengguna dan perangkat**

- Kasir dan staf memakai tablet Android; satu tablet melayani satu usaha, dan ada satu printer thermal per outlet.
- Owner dan manager memakai web owner dan PIN manager di tablet.
- Tim: pembuat app Android (agent coding di Android Studio dan OpenCode) dan pembuat backend dan web.

**Prinsip desain**

1. Offline-first: tablet membaca dan menulis ke database lokal, sinkron di belakang layar.
2. Transaksi hanya bertambah (append-only): koreksi berupa catatan pembalik, tidak ada penghapusan.
3. Master data: server menang. Peran ditegakkan server; UI hanya menyembunyikan.
4. Sederhana untuk karyawan: sedikit ketukan, bahasa jelas, tidak ada istilah teknis.
5. Server tidak percaya total dari klien: total dihitung ulang dan dibandingkan.

**Termasuk dalam MVP**

- Setup perangkat dan kiosk mode, login PIN, bootstrap dan delta sync.
- Shift (buka dan tutup, termasuk tutup paksa), transaksi cafe, transaksi laundry dengan tag.
- Pembayaran **Tunai dan QRIS Statis**.
- Struk dan printer, void, refund, diskon, kas keluar, dan audit log.
- Sync engine offline-first dan penerimaan bundel di server.
- Notifikasi WhatsApp laundry lewat Fonnte (dikirim backend) plus tombol kirim manual.
- Remote config, versi minimum, heartbeat perangkat, backup harian dengan uji pemulihan.
- Diagnostik, crash report, laporan Z, dan semua layar web owner (bagian 14), termasuk laba-rugi ringan.

**Tidak termasuk dalam MVP**

- **Payment gateway dan QRIS Dinamis** (dihapus pada v2).
- Order via QR meja dan pengambilan sendiri oleh pelanggan.
- Stok bahan per resep, par level, dan kedaluwarsa.
- Antar-jemput laundry, split bill per item, WhatsApp resmi (Cloud API).
- Layar dapur atau antrean terpisah, timbangan Bluetooth, lebih dari satu tablet per usaha.
- iOS dan enkripsi database lokal.

**Keputusan yang sudah diambil**

| Topik | Keputusan |
| --- | --- |
| Stack Android | Kotlin, Jetpack Compose, Room, WorkManager, Hilt (dikonfirmasi lewat spike printer dan background sync di M0) |
| Perangkat | Tablet Android, satu tablet per usaha, satu printer per outlet |
| Laci kas | Fitur buka laci disediakan, nonaktif secara default |
| Pembayaran | Tunai dan QRIS Statis |
| WhatsApp | Fonnte, dipanggil dari backend, nomor khusus usaha |
| Profit | Laba-rugi ringan: pengeluaran dicatat, modal per porsi atau per kg sebagai estimasi |
| Rilis | Cafe dan laundry bersamaan, tanpa pilot, dengan masa paralel 1-2 minggu |

**Pembaruan keputusan (v2.1)**

- **Aplikasi:** dua aplikasi Android terpisah (cafe dan laundry) atas permintaan owner, dengan tampilan, ikon, nama, versi, dan pembaruan masing-masing. Tetap satu repositori, satu `:core`, dan satu backend (bagian 9).
- **Package name (usulan, ganti bila nama usaha berbeda):** `id.tenang.kasir.cafe` dan `id.tenang.kasir.laundry`; varian debug dengan akhiran `.debug`.
- **Tablet:** Redmi Pad 2 (11 inci, Android 15, HyperOS 2). **Printer:** iWare C58BT, thermal 58 mm, Bluetooth dan USB, ESC/POS, tanpa baterai.
- **Akun layanan:** Firebase dan Fonnte memakai akun milik salah satu pembuat. Disarankan akun Google khusus dengan kedua pembuat sebagai Owner, dan kredensial Fonnte disimpan di password manager bersama.

**Asumsi beban (usulan awal, mohon dikoreksi)**

| Parameter | Asumsi | Catatan |
| --- | --- | --- |
| Tablet aktif | 2 (satu cafe, satu laundry) | Satu tablet per usaha |
| Transaksi | Sampai sekitar 500 per hari, total dua usaha | Puncak jam sibuk lebih ramai dari rata-rata |
| Pengguna web bersamaan | 1 sampai 3 | Owner dan manager |
| Pertumbuhan data | Sekitar 180 ribu transaksi per tahun | Jauh di bawah batas database relasional tunggal |
| Ketersediaan | Usulan 99% per bulan | Tablet tetap berjualan saat offline |

**Target non-fungsional (usulan, mohon dikoreksi)**

| Aspek | Target | Cara diuji |
| --- | --- | --- |
| Simpan transaksi dari layar bayar ke Room | Di bawah 1 detik, tanpa jaringan | Tablet asli |
| Respons sinkron satu bundel | p95 di bawah 2 detik pada jaringan seluler biasa | Uji beban M2 |
| Cetak struk setelah bayar | Di bawah 5 detik | Printer asli |
| Bootstrap awal | Di bawah 2 menit pada Wi-Fi | Tablet asli |
| Kerja offline | Minimal 24 jam penuh tanpa kehilangan data | Skenario 2 |
| Kehilangan data | Nol transaksi hilang; backup harian, target pemulihan 4 jam | Uji pemulihan |
| Ketersediaan server | 99% per bulan | Pemantauan |
| Tes otomatis | Semua kasus uji rumus lulus; bundel ganda tetap satu transaksi; dependensi modul diperiksa di CI | CI |

**Log perubahan dari versi sebelumnya (penghapusan gateway dan QRIS Dinamis)**

| Area | Perubahan |
| --- | --- |
| Pembayaran | Hanya Tunai dan QRIS Statis. Tidak ada metode, status, atau layar QRIS Dinamis |
| API | Endpoint `POST /v1/payments/qris-dynamic` dan `GET /v1/payments/{id}` dihapus; kode error `PROVIDER_UNAVAILABLE` dihapus |
| Konfigurasi | Saklar fitur `qris_dynamic` dihapus dari Pengaturan dan `GET /v1/config` |
| Data | Tabel `gateway_events` dan kolom `gateway_ref`, `fee_amount`, status Menunggu/Kedaluwarsa/Gagal pada pembayaran dihapus |
| Backend | Adapter penyedia, webhook gateway, polling status QR, dan rekonsiliasi QRIS Dinamis dihapus |
| Penjadwal | Tugas pengecekan QRIS Menunggu dan rekonsiliasi QRIS Dinamis dihapus |
| Offline | Semua metode bayar (Tunai dan QRIS Statis) tersedia saat offline |
| Tutup shift | Prasyarat "QRIS Dinamis Menunggu" dihapus |
| Refund QRIS | Lewat gateway dihapus; refund dikembalikan tunai dan dicatat sebagai refund tunai atas pembayaran QRIS |
| Biaya admin (MDR) | Tidak lagi otomatis dari gateway; diinput owner di web sebagai pengeluaran |
| Dependensi dan risiko | Pemilihan dan pendaftaran gateway, serta risiko keterlambatan pendaftaran, dihapus |

**Log perubahan v2.2 (hasil tinjauan kontradiksi)**

| Temuan | Keputusan | Bagian |
| --- | --- | --- |
| Staf laundry bentrok dengan aturan satu pengguna | Sesi staf tidak menutup shift; event `laundry.*` dan `customer.created` tidak terikat shift | 3, 10, 12 |
| Pengambilan sebagian tanpa model data | Tabel `laundry_pickups` dan event `laundry.picked_up` | 4, 10 |
| Katalog event tidak lengkap | Ditambah `customer.created`, `menu.sold_out_changed`, `order.table_moved`, `laundry.picked_up`, `shift.force_closed` | 10, 11 |
| `qris_checked` melanggar append-only | Kolom dihapus; daftar ID dibawa di `shift.closed`, disimpan di `shift_qris_checks` | 6, 10 |
| Masa simpan event tidak konsisten | 30 hari di tablet; `sync_bundles` minimal 365 hari; umur event tidak pernah menolak penjualan | 6, 8, 10, 12 |
| Refund QRIS vs rekonsiliasi | Refund selalu mengurangi Tunai; rekonsiliasi memakai QRIS bruto | 5, 7, 14 |
| Pendaftaran perangkat ada dua versi | Kode pendaftaran sekali pakai dari web, ditambah persetujuan owner | 3, 15 |
| Aturan dependensi modul | `:feature:cafe` dan `:feature:laundry` boleh memakai `:feature:shift` dan `:feature:payment` | 9 |
| Cuci ulang: alasan atau PIN manager | Alasan dan PIN manager | 4, 12 |
| Struk WhatsApp untuk cafe | Hanya laundry | 5, 13 |
| Tolak atau terima-dan-tandai | Terima, tandai, karantina; tolak hanya untuk otorisasi dan struktur | 12 |
| Pesanan cafe terbuka saat ganti shift | Diserahterimakan ke shift berikutnya | 6, 10 |
| Setoran kas tanpa validasi | Kas ditinggal + disetor atau dibawa harus sama dengan kas dihitung, atau selisih beralasan | 6, 12 |
| Refund transaksi lama hanya owner | Manager di tablet dengan PIN, berbatas nilai dan umur; di atas batas oleh owner di web | 5, 8 |
| Rekonsiliasi QRIS tidak spesifik | Total bruto, tanggal bisnis sama dengan app, disarankan satu QRIS per usaha | 7 |
| Kiosk dan pembaruan tablet | Tablet tanpa akun; APK internal dipasang senyap; Firebase App Distribution hanya untuk ponsel penguji | 3, 16 |
| Sinkron bergantung pada WorkManager | Coroutine foreground utama, WorkManager cadangan | 6, 9 |
| Penyimpanan aman | DataStore dengan Tink; kunci di Android Keystore | 3, 9 |
| Praktik Android modern dan OWASP | Ditambahkan di bagian 9 | 9 |
| Tag thermal di lingkungan lembap | Nomor tulis tangan sebagai cadangan; klaim kertas label diuji sendiri di M0 | 5 |
| Bagian yang hilang | Target non-fungsional, retensi, seed, dan migrasi ditambahkan | 1, 10 |
| `business_id` dan `business_unit_id` | Satu nama: `business_unit_id` | 11, 19 |
| Skenario 4 hanya untuk cafe | Berlaku untuk kedua aplikasi; skenario baru 23 sampai 28 ditambahkan | 17 |
