# Pertanyaan Terbuka dan Nilai Sementara

Aturan untuk agent: kerjakan dengan **nilai sementara** di bawah, simpan sebagai pengaturan (bukan angka tertanam di kode), dan jangan menganggapnya final. Bila perlu keputusan baru, tambahkan baris di sini dan lanjutkan dengan item lain.

| ID | Pertanyaan | Nilai sementara | Perlu sebelum | Pemutus |
| --- | --- | --- | --- | --- |
| OQ-01 | Package name final | Nama usaha sudah diketahui: AA Coffeeshop dan Athia Laundry House. Package name masih placeholder `id.tenang.kasir.cafe` dan `id.tenang.kasir.laundry`; usulan baru perlu persetujuan | M1 (sebelum rilis pertama) | Owner dan tim |
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
| OQ-24 | Pemasangan senyap APK oleh device owner dan akun Mi di HyperOS | Diuji di M0; cadangan: pasang lewat USB | M0 | Hasil spike (M0 dikonfirmasi selesai; catat hasilnya di sini) |
| OQ-25 | Alamat dan kontak AA Coffeeshop untuk kepala struk | Belum ada; laundry: Jl. Trans Sulawesi, Bintauna, WA +62 823-4778-5986 | M2 | Owner |
| OQ-20 | Pembatasan latar belakang HyperOS pada Redmi Pad 2 | Diuji di M0; mitigasi: izin pengecualian baterai dan peringatan di layar | M0 | Hasil spike (M0 dikonfirmasi selesai; catat hasilnya di sini) |

## Untuk dipastikan di M0 dengan perangkat asli

- Cetak dari iWare C58BT lewat Bluetooth dan USB: lebar 32 karakter, QR, pemotongan, laci kas (RJ11).
- Sinkron WorkManager bertahan saat layar mati dan aplikasi di latar belakang selama beberapa jam.
- Mode kios (device owner) di HyperOS 2.
- Kamera tablet memindai QR tag di kertas roll.
