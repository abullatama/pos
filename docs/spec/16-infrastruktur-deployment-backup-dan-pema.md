## 16. Infrastruktur, deployment, backup, dan pemantauan

Beban yang diasumsikan kecil, sehingga satu server aplikasi dan satu database sudah cukup, tetapi disiplin operasional (backup yang teruji, deployment yang bisa dibatalkan, dan peringatan dini) tetap wajib karena data yang disimpan adalah uang. Pilihan hosting dibiarkan terbuka; bagian ini hanya menetapkan persyaratannya.

**Persyaratan hosting**

- Alamat publik dengan domain dan HTTPS (sertifikat otomatis), karena app tablet harus bisa menjangkaunya.
- Lokasi dengan latensi baik ke Indonesia (usulan).
- Firewall: hanya HTTPS yang terbuka ke publik, akses admin dibatasi dan memakai kunci, bukan password.
- Pembaruan keamanan OS otomatis dan sinkronisasi waktu (NTP), karena banyak aturan bergantung pada waktu.
- Database dan penyimpanan file tidak boleh terbuka ke internet.

**Lingkungan dan deployment**

1. Tiga lingkungan: lokal, staging (data fiktif), dan produksi. Konfigurasi dan rahasia terpisah per lingkungan.
2. Build yang bisa direproduksi (disarankan sebagai container image). Pipeline CI menjalankan build, tes, dan lint di setiap perubahan.
3. Staging dideploy otomatis. Produksi dideploy dengan persetujuan manual dan bisa dikembalikan ke versi sebelumnya.
4. Migrasi database bersifat maju saja dan kompatibel mundur (tambah kolom dulu, hapus belakangan), karena tablet tidak update serempak dan app versi lama masih harus bisa berjalan.
5. Versi API lama tetap didukung sampai semua tablet memenuhi versi minimum.
6. Perubahan berisiko dikendalikan dengan saklar fitur dari `GET /v1/config`.

**Dua aplikasi: build, versi, dan rilis**

- CI membangun kedua aplikasi dari satu pipeline. Perubahan pada `:core` atau fitur bersama memicu uji dan build keduanya; perubahan pada fitur satu usaha hanya memicu aplikasi itu.
- `versionCode` dan `versionName` independen per aplikasi. Tag git per aplikasi (`cafe-v1.2.0`, `laundry-v1.0.3`) dengan catatan rilis masing-masing.
- Rilis bertahap per aplikasi (satu tablet dulu). Update hanya saat tidak ada shift aktif dan outbox kosong.
- Distribusi: ponsel penguji lewat Firebase App Distribution (satu grup per aplikasi); tablet produksi lewat APK bertanda tangan dari rilis internal, dipasang senyap oleh app (device owner) atau lewat USB, karena tablet kios tidak boleh punya akun Google.
- Versi API lama didukung sampai tablet kedua aplikasi memenuhi `min_app_version` masing-masing.

**Backup dan pemulihan**

| Hal | Persyaratan | Usulan |
| --- | --- | --- |
| Frekuensi | Backup database penuh harian | Ditambah log perubahan untuk pemulihan ke titik waktu bila didukung |
| Lokasi | Salinan di luar server utama | Penyedia atau wilayah berbeda |
| Enkripsi | Backup terenkripsi | Kunci disimpan terpisah dari backup |
| Retensi | Beberapa generasi | 14 harian, 8 mingguan, 6 bulanan |
| Cakupan | Database dan penyimpanan file (foto nota) |  |
| Uji pemulihan | Dipulihkan ke staging dan diverifikasi | Bulanan, dengan pengecekan jumlah baris dan total transaksi |
| Target | Kehilangan data dan waktu pulih maksimal | Kehilangan data paling lama 24 jam (lebih baik 1 jam dengan log perubahan) dan waktu pulih paling lama 4 jam |

Runbook pemulihan ditulis dan diuji oleh orang yang berbeda dari penulisnya. Backup yang belum pernah diuji dianggap belum ada.

**Pemantauan dan peringatan**

- Endpoint kesehatan (`/health` untuk proses hidup, `/ready` untuk siap melayani termasuk koneksi database) dan pemeriksaan uptime dari luar.
- Metrik: latensi dan tingkat error API, kedalaman dan usia antrean tugas, jumlah bundel `ACCEPTED`, `DUPLICATE`, dan `REJECTED` per kode, serta usia outbox terlama dari heartbeat tablet.
- Log terpusat dengan retensi terbatas, tanpa data pribadi, dan setiap request membawa `X-Request-Id`. Pelacakan error otomatis dengan notifikasi.
- Peringatan ke pembuat backend: API tidak sehat, tingkat error tinggi, antrean macet, backup gagal, disk hampir penuh, sertifikat hampir kedaluwarsa, serta kegagalan beruntun pada Fonnte.
- Peringatan ke owner (lewat web): tablet tidak sinkron, selisih kas besar, dan rekonsiliasi QRIS yang selisih.

**Ekspor data dan pemeliharaan**

- Semua laporan dapat diekspor ke CSV. Ekspor penuh data (dump) tersedia untuk owner atas permintaan, dan setiap ekspor tercatat di audit log.
- Jendela pemeliharaan dini hari, dengan patch OS, rotasi rahasia, dan pembersihan data lama sesuai retensi.
- Biaya bulanan berjalan (server, database, penyimpanan, domain, Fonnte) dicatat di bagian 19 untuk disepakati siapa yang menanggung.
