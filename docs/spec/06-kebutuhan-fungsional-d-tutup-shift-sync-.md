## 6. Kebutuhan fungsional D: tutup shift, sync di tablet, operasional

**Tutup shift**

| ID | Kebutuhan |
| --- | --- |
| TUP-01 | Pesanan cafe yang masih terbuka tidak menghalangi tutup shift: pesanan itu diserahterimakan ke shift berikutnya (daftarnya dibawa di event shift.closed) dan dibayar di shift yang menerima pembayaran. Bila sampai akhir hari bisnis tidak ada shift lanjutan, pesanan masuk laporan pengecualian. Order laundry belum lunas tidak menghalangi dan masuk daftar belum tertagih. |
| TUP-02 | Hitung kas buta: kasir mengisi uang di laci per pecahan, angka sistem disembunyikan. Hanya tunai yang dihitung. |
| TUP-03 | Sistem menampilkan kas seharusnya, hitungan kasir, dan selisih. Selisih di atas toleransi (Pengaturan) wajib beralasan. Setelah dikonfirmasi, hitungan terkunci dan mengubahnya butuh PIN manager. |
| TUP-04 | Cek QRIS Statis: app menampilkan daftar transaksi QRIS Statis shift ini untuk dicocokkan dengan riwayat aplikasi merchant, lalu kasir menandai Sudah dicek. Daftar ID yang dicek dibawa di event shift.closed, bukan kolom pada pembayaran, karena pembayaran bersifat append-only. |
| TUP-05 | Laporan Z: penjualan kotor, diskon, refund atau void, penjualan bersih, rincian per metode (Tunai, QRIS Statis), kas keluar, jumlah transaksi, dan order laundry belum lunas. Tanpa modal dan margin. Dicetak dan dikirim ke backend. |
| TUP-06 | Catatan kas: kasir mengisi uang yang ditinggalkan di laci (kas awal shift berikutnya) dan yang disetor atau dibawa pulang. Validasi: yang ditinggal di laci ditambah yang disetor atau dibawa harus sama dengan kas dihitung; bila tidak, selisihnya wajib beralasan, dan uang yang dibawa wajib mencatat penerimanya. |
| TUP-07 | Status shift menjadi Ditutup, lalu data dikirim lewat outbox. |
| TUP-08 | Offline: shift tetap bisa ditutup lokal dengan peringatan Menunggu sinkron, jangan reset tablet. |
| TUP-09 | Tablet mati di tengah proses: hitungan yang belum dikonfirmasi tersimpan sebagai draft. |
| TUP-10 | Koreksi setelah ditutup hanya oleh owner dari web berupa catatan penyesuaian dengan alasan dan audit log. Shift lama tidak diedit. |
| TUP-11 | Selisih besar atau berulang ditandai di web owner dan masuk ringkasan per kasir. |

**Sync engine di tablet**

Aturan teknis penerimaan di server ada di bagian 12.

| ID | Kebutuhan |
| --- | --- |
| SYN-01 | Setiap aksi penting (order dibuat, dikirim ke dapur, pembayaran, status laundry, void, refund, diskon, kas keluar, buka dan tutup shift) menyimpan data dan event dalam satu transaksi database. |
| SYN-02 | Event masuk outbox berstatus Menunggu dengan UUID dari tablet yang berfungsi sebagai idempotency key. |
| SYN-03 | Sinkron utama berjalan sebagai coroutine di foreground (app kiosk hampir selalu di depan), dan WorkManager menjadi cadangan saat app di latar belakang, sehingga kelancaran sinkron tidak bergantung pada pembatasan HyperOS. Pengirim mengirim bundel per transaksi (order + item + pembayaran). Server membalas per bundel: Diterima, Duplikat (dianggap sukses), atau Ditolak (dengan alasan). |
| SYN-04 | Setelah push, app menarik perubahan dari server (delta sync). Urutan: kirim dulu, lalu tarik, supaya data lokal tidak tertimpa sebelum terkirim. |
| SYN-05 | Pemicu: segera setelah transaksi (bila online), saat koneksi kembali, saat app ke foreground, periodik sebagai cadangan, dan tombol Sinkron sekarang. Hanya satu worker berjalan pada satu waktu; urutan kirim per entitas dijaga. |
| SYN-06 | Gagal sementara: ulang dengan exponential backoff dan jitter. Gagal permanen: masuk Antrean bermasalah dengan alasan, bisa dicoba lagi atau diekspor oleh manager atau owner, dan tidak pernah dihapus diam-diam. |
| SYN-07 | Indikator di pojok layar: hijau (semua terkirim), kuning (ada yang menunggu atau offline), merah (ada yang bermasalah). Peringatan muncul bila data menunggu terlalu lama dan saat tutup shift. |
| SYN-08 | Logout tidak menghapus outbox. Event yang sudah sinkron dibersihkan setelah 30 hari (nilai Pengaturan, satu angka), sedangkan yang menunggu atau bermasalah tidak dihapus. |
| SYN-09 | Risiko tablet hilang sebelum sempat sinkron: sinkron segera setiap transaksi, jangan reset tablet sebelum outbox kosong, dan owner dapat melihat sinkron terakhir tiap tablet di web. |
| SYN-10 | Event membawa jam tablet, server mencatat jam terima. Selisih jam besar menampilkan peringatan. |

**Operasional dan keamanan di tablet**

| ID | Kebutuhan |
| --- | --- |
| OPS-01 | Crash report (misal Firebase Crashlytics) tanpa data pelanggan. |
| OPS-02 | Halaman diagnostik di tablet (PIN owner): versi, sinkron terakhir, jumlah outbox, test printer, tes koneksi server, jam tablet vs server, dan ekspor log. |
| OPS-03 | Distribusi lewat jalur privat. Update hanya saat tidak ada shift aktif dan outbox kosong, di luar jam ramai, dan dirilis bertahap (satu tablet dulu). |
| OPS-04 | Versi minimum ditegakkan backend. Migrasi database diuji dengan data lama. |
| OPS-05 | Remote config jarak jauh: batas toleransi, preset diskon, nomor WhatsApp, dan versi minimum. |
| OPS-06 | Token terenkripsi (DataStore dengan Tink, kunci di Android Keystore), verifier PIN dalam bentuk hash, semua koneksi TLS, kiosk mode, dan kunci layar. |
| OPS-07 | Kunci API (Fonnte) hanya di backend, tidak pernah di tablet. |
| OPS-08 | Tablet hilang atau dicuri: owner mencabut izinnya dari web. Karyawan keluar: akun dinonaktifkan. |

**Prosedur darurat (dicetak dan ditempel)**

| Masalah | Tindakan |
| --- | --- |
| Tablet mati atau rusak | Pakai buku nota kertas bernomor, lalu input susulan ditandai input susulan |
| Printer mati | Penjualan lanjut, cetak ulang atau struk digital nanti |
| Internet mati | Tunai dan QRIS Statis tetap berjalan |
| Fonnte atau WhatsApp gagal | Tombol Kirim via WA manual |
