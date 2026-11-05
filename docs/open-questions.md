# Pertanyaan Terbuka dan Nilai Sementara

Aturan untuk agent: kerjakan dengan **nilai sementara** di bawah, simpan sebagai pengaturan (bukan angka tertanam di kode), dan jangan menganggapnya final. Bila perlu keputusan baru, tambahkan baris di sini dan lanjutkan dengan item lain.

| ID | Pertanyaan | Nilai sementara | Perlu sebelum | Pemutus |
| --- | --- | --- | --- | --- |
| OQ-01 | Package name final | **Selesai:** Nama usaha dan package ID resmi: `a3coffeeshop` (`id.a3coffeeshop`) dan `athialoundry` (`id.athialoundry`). Kata "tenang" dieliminasi total. | - | Selesai |
| OQ-02 | Siapa pemilik akun Firebase, Fonnte, keystore | Akun Google khusus proyek dengan kedua dev sebagai Owner (disarankan) | M1 | Tim |
| OQ-03 | Apakah laundry kena pajak, berapa | 0% | M2 | Owner |
| OQ-04 | Harga menu sudah termasuk pajak (mode "termasuk")? Rumus belum ada di spek | Mode "ditambahkan"; vektor C4 berstatus `proposed` | M2 | Owner dan tim |
| OQ-05 | Biaya layanan cafe | Nonaktif (0%) | M2 | Owner |
| OQ-06 | Kas awal shift bawaan | Rp200.000 | M3 | Owner |
| OQ-07 | Toleransi selisih kas saat tutup shift | Rp2.000 | M3 | Owner |
| OQ-08 | Batas kas keluar tanpa PIN manager | Rp50.000 | M3 | Owner |
| OQ-09 | Berat minimum dan kelipatan laundry | 1 kg minimum, kelipatan 0,5 kg | M2 | Owner |
| OQ-10 | Tarif per kg reguler dan express, tarif per pcs | Contoh di `seed/` (7.000 dan 10.000 per kg) | M2 | Owner |
| OQ-11 | Estimasi selesai | Laundry reguler 2 hari, express 6 jam | M2 | Owner |
| OQ-12 | Retensi event sinkron di tablet (hari) | 30 hari (diputuskan v2.2, satu angka) | - | Selesai |
| OQ-13 | Jam buka cafe dan laundry | Dari panduan merek: kedua usaha 07.00 – 21.00 (WITA); tidak membatasi penjualan, hanya untuk struk | M3 | Owner |
| OQ-14 | Apakah nomor struk harus berurutan tanpa celah | Tidak wajib; unik per perangkat per hari | M2 | Owner dan akuntan |
| OQ-15 | Preset diskon | 5% dan 10% (contoh di `seed/`) | M2 | Owner |
| OQ-16 | Template pesan WhatsApp | Teks contoh di spek bagian 13 | M4 | Owner |
| OQ-17 | Modal per porsi/kg: diisi owner atau dari data tablet | Diisi owner di web | M4 | Owner |
| OQ-18 | `TOTAL_MISMATCH`: tolak atau terima dan tandai | **Diputuskan v2.2:** terima, tandai, karantina | - | Selesai |
| OQ-19 | Stack backend dan web | Belum dipilih (lihat D-11) | M0 | Pengerja backend |
| OQ-21 | Satu QRIS merchant per usaha atau satu untuk keduanya | Disarankan satu per usaha; rekonsiliasi per usaha bila begitu | M3 | Owner |
| OQ-22 | Batas nilai dan umur refund transaksi lama di tablet | Rp100.000 dan 14 hari | M3 | Owner |
| OQ-23 | Pengambilan sebagian laundry per kg: per jumlah potong atau penanda terakhir saja | Jumlah potong opsional, penanda terakhir wajib | M2 | Owner dan tim |
| OQ-24 | Pemasangan senyap APK oleh device owner dan akun Mi di HyperOS | **Selesai (Spike M0):** Pada tablet dengan akun Mi/Google, Android melarang `dpm set-device-owner` tanpa factory reset. Cadangan resmi: LockTask (Screen Pinning) terbukti mengunci kasir di POS; update APK mandiri tanpa Google Play memakai `PackageInstaller.Session` (muncul prompt 1-ketuk kasir) atau sideload USB | - | Selesai |
| OQ-25 | Alamat dan kontak toko untuk kepala struk | **Selesai:** Owner: DEVENTRI SILAMATITI BULUT. Alamat kedua usaha: Jl. Trans Sulawesi Kelurahan Bintauna. Telp/WA: 0823-4778-5986. Header memuat logo resmi (ESC/POS raster). | - | Selesai |
| OQ-26 | Backend Laravel 12 (a3-api.nen.asia) belum mendaftarkan tipe event 'menu.sold_out_changed' di POST /v1/sync/bundles | Mitigasi B2 aktif di Android: penandaan menu habis memperbarui Room lokal, pengiriman outbox dinonaktifkan sementara dan auto-drained agar tidak memicu pesan error server | M2 | Pengerja Backend |
| OQ-27 | Backend Laravel 12 (a3-api.nen.asia) belum mendaftarkan tipe event 'customer.created' di POST /v1/sync/bundles | Mitigasi aktif di Android: data pelanggan dikirim inline via 'customer' di event 'order.created' sesuai docs/API_DOCUMENTATION.md; aturan mutual exclusion ditegakkan (hanya customer inline untuk pelanggan baru, atau customer_id untuk pelanggan lama, tidak pernah bersamaan); event customer.created difilter di SyncSender & auto-drain/recovery aktif agar tidak memicu pesan error server dan pesan WhatsApp otomatis tetap terkirim normal | M2 | Pengerja Backend |
| OQ-28 | Backend Laravel 12 (a3-api.nen.asia) mewajibkan 'bundles.*.shift_id' (required|uuid) pada seluruh bundel POST /v1/sync/bundles | Mitigasi aktif di Android: seluruh bundel selalu menyertakan shift_id valid non-null (memenuhi validator FormRequest Laravel) dan menyisipkan event 'shift.opened' secara atomik di awal bundel saat mendeteksi penolakan 'shift belum diterima server' agar server mencatat shift terlebih dahulu sebelum menerima pesanan/pembayaran | M2/M3 | Pengerja Backend |
| OQ-29 | PIN manager/owner kini diperiksa terhadap akun dari server (verifier PBKDF2 per perangkat, spek 3.3.D), tanpa PIN bawaan. Syarat: tablet sinkron online sekali agar data `users` terunduh; hanya akun aktif berperan manager/owner yang punya PIN yang bisa menyetujui. Gerbang admin memakai manager atau owner | Tablet lama mengunduh ulang master data sekali setelah pembaruan (penanda users_bootstrap_v1). Pastikan akun manager/owner tertaut ke unit usaha di server dan sudah punya PIN | Go-live | Owner dan pengerja backend |
| OQ-30 | Server tidak menerima diskon bebas di order.created (hanya preset terdaftar, nama persis). Diskon manual/komplimen dikirim sebagai order harga penuh lalu koreksi manual_discount/comp sebesar selisih total (tablet menghitung diskon sebelum pajak; server mencatat pengurangan flat setelah pajak, hasil tagihan sama persis) | Diterapkan di tablet. Komplimen = koreksi comp bila total setelah diskon nol | M2 | Owner (konfirmasi aturan) |
| OQ-31 | Menambah item ke pesanan terbuka yang sudah berdiskon ditolak di tablet: total gabungan dihitung ulang tanpa diskon, sedangkan server mempertahankan diskon asli | Ditolak dengan pesan jelas. Solusi penuh butuh menyimpan jenis diskon per order (migrasi) | Pasca go-live | Owner dan tim |
| OQ-32 | Pengguna pelaku (user_id) pada event laundry dan shift memakai cadangan 4 atau 2 bila id kasir tidak numerik; id penyetuju sudah ketat (angka dari persetujuan PIN). Cadangan pelaku belum diganti | Dibiarkan; periksa bahwa id kasir dari login server selalu numerik | Pasca go-live | Tim |
| OQ-33 | Tablet hanya menghitung persen bulat. Pajak, biaya layanan, dan preset persen di server dalam basis poin; nilai yang bukan kelipatan 100 bp (mis. 1050) diabaikan di tablet | Owner memakai persen bulat di pengaturan web | Go-live | Owner |
| OQ-20 | Pembatasan latar belakang HyperOS pada Redmi Pad 2 | Diuji di M0; mitigasi: izin pengecualian baterai dan peringatan di layar | M0 | Hasil spike (M0 dikonfirmasi selesai; catat hasilnya di sini) |

## Untuk dipastikan di M0 dengan perangkat asli

- Cetak dari iWare C58BT lewat Bluetooth dan USB: lebar 32 karakter, QR, pemotongan, laci kas (RJ11).
- Sinkron WorkManager bertahan saat layar mati dan aplikasi di latar belakang selama beberapa jam.
- Mode kios (device owner) di HyperOS 2.
- Kamera tablet memindai QR tag di kertas roll.
| OQ-34 | Notifikasi WhatsApp laundry otomatis sering tidak terkirim; penyebab di sisi server belum diketahui (kode backend tidak ada di repo ini). Sebelum laundry 1.0.4, layar pesanan membaca status tanpa token login sehingga selalu "Belum Dikirim", jadi kenyataan pengiriman tidak terlihat | Laundry 1.0.4: koneksi bertoken, status gagal-periksa dibedakan ("Status Tak Terbaca" dengan kode), pesan terbaru yang dibaca. Minta pengelola backend memeriksa: scheduler kasir:whatsapp-send berjalan tiap menit (/ready), isi tabel notifications (status, attempts, last_error), circuit breaker, sesi perangkat Fonnte. Teks pesan manual belum diubah (ditunda) | Go-live | Pengembang backend |
| OQ-35 | Pelanggan laundry bernomor sama tetapi id berbeda menimpa baris lama (REPLACE pada nomor unik) sehingga pesanan lama kehilangan pelanggan (tampil "Pelanggan Athia", tanpa nomor, tombol WA hilang). Terbukti pada tablet setelah pencarian pelanggan dari server berjalan (laundry 1.0.5). Diperbaiki hanya di pencarian (laundry 1.0.6). Jalur lain yang masih memakai REPLACE: pembuatan pelanggan baru di createLaundryOrderWithCustomer bila nomor sudah ada, dan pesanan lama yang sudah terdampak tidak dipulihkan otomatis | Pencarian aman. Pelanggan baru dengan nomor yang sudah ada belum dicegah; perlu keputusan: gunakan pelanggan lama (ubah perilaku event customer.created) atau tolak dengan pesan. Pesanan uji yang terdampak diabaikan | Go-live | Pengembang |
