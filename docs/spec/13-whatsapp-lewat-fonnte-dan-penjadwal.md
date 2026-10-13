## 13. WhatsApp lewat Fonnte dan penjadwal

Backend mengirim WhatsApp dari antrean tugas, tidak pernah dari app. Kegagalan Fonnte tidak boleh mengganggu operasional karena tombol Kirim via WA manual di app selalu tersedia. Fonnte adalah layanan tidak resmi, jadi desain di bawah sengaja berhati-hati terhadap risiko nomor diblokir. Endpoint, autentikasi, dan batas Fonnte dibaca dari dokumentasi resminya saat implementasi, bukan dari dokumen ini.

**Pesan yang dikirim (hanya laundry, hanya transaksional)**

| Pemicu | Isi pesan | Catatan |
| --- | --- | --- |
| Order laundry dibuat | Nomor order, total, status bayar, estimasi selesai | Berfungsi juga sebagai kontrol: pelanggan menjadi pengawas |
| Status berubah menjadi Siap | Cucian siap diambil, nomor order, sisa tagihan bila ada, jam buka | Pemicu: `laundry.status_changed` |
| Cucian tidak diambil lebih dari N hari | Pengingat singkat | Maksimal 2 pengingat per order (usulan) |

Template disimpan di Pengaturan dan diedit owner. Variabel yang tersedia: nama, nomor order, total, sisa, estimasi, nama usaha, dan jam buka. Hindari tautan di dalam pesan, dan jangan menyertakan data pribadi lain.

**Alur pengiriman**

1. Saat event diterima, backend membuat record `notifications` berstatus antre dalam transaksi database yang sama (pola outbox di sisi server), sehingga pesan tidak hilang bila proses mati.
2. Worker mengambil antrean satu per satu dan memeriksa: nomor valid (dinormalisasi ke format internasional), dan pelanggan tidak menolak notifikasi (`customers.wa_opt_out`, usulan penambahan).
3. Worker mengirim lewat antarmuka `MessageProvider.send(to, text)` dengan batas waktu, lalu mencatat hasil: terkirim atau gagal, `provider_ref`, dan `last_error`.
4. Bila gagal, ulang dengan exponential backoff, maksimal beberapa kali dalam 24 jam (usulan 5). Setelah itu status menjadi gagal.
5. Kunci unik (order\_id, kind, nomor pengingat) mencegah pesan ganda saat event diterima dua kali.
6. Status dikembalikan lewat `GET /v1/orders/{id}/notification-status`. Bila gagal, app menampilkan tombol Kirim via WA manual.

**Mengurangi risiko blokir**

- Nomor khusus usaha (WhatsApp Business), terpisah dari nomor pribadi atau nomor utama.
- Hanya pesan transaksional ke pelanggan yang memberi nomornya saat order. Tidak ada pesan promosi massal.
- Kirim bergantian dengan jeda acak antar-pesan (beberapa detik) dan batas harian yang konservatif. Angka pastinya diatur lewat pengaturan, bukan ditanam di kode.
- Pemutus sirkuit: bila terjadi gagal beruntun, pengiriman dijeda otomatis dan owner diberi tahu.
- Bila Fonnte menyediakan status koneksi perangkat, cek berkala dan beri tahu owner saat sesi terputus supaya bisa dipindai ulang.
- Token Fonnte hanya sebagai rahasia lingkungan di backend. Log memasker nomor telepon dan tidak menyimpan isi pesan lengkap.

**Penjadwal**

Semua jadwal mengikuti zona waktu usaha.

| Tugas | Jadwal (usulan) | Fungsi |
| --- | --- | --- |
| Pengingat cucian tidak diambil | Harian, pagi | Membuat notifikasi untuk order Siap yang melewati N hari |
| Pengeluaran berulang | Harian | Membuat `expenses` dari `recurring_expenses` pada tanggalnya, sekali per periode |
| Deteksi tablet tidak sinkron | Setiap 15 menit | Peringatan ke owner bila `last_sync_at` melewati ambang |
| Pengingat catat pengeluaran | Mingguan | Memberi tahu owner bila belum ada catatan pengeluaran minggu itu; kanal (web, email, atau WhatsApp) belum diputuskan |
| Backup database | Harian | Lihat bagian 16 |
| Pembersihan | Harian | Menghapus kode pendaftaran kedaluwarsa, token lama, dan heartbeat lama sesuai retensi |
