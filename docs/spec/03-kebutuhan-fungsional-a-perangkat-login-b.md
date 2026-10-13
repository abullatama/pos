## 3. Kebutuhan fungsional A: perangkat, login, bootstrap, buka shift

Kebutuhan fungsional (bagian 3 sampai 6) diurutkan mengikuti alur harian: setup perangkat (sekali), login, bootstrap data, buka shift, transaksi, pembayaran, struk, koreksi bila perlu, tutup shift, dengan sync engine berjalan di belakang semuanya. Tiap kebutuhan punya ID agar bisa dirujuk di tiket dan tes.

**Setup perangkat (sekali per tablet)**

| ID | Kebutuhan |
| --- | --- |
| SET-01 | Pasang app lewat jalur distribusi privat tanpa akun Google di tablet (lihat catatan perangkat), lalu masukkan kode pendaftaran sekali pakai dari web owner (bagian 15). |
| SET-02 | Daftarkan tablet (ID perangkat) ke satu usaha: cafe atau laundry. Status perangkat: Menunggu persetujuan, Aktif, Dicabut. Owner menyetujui dari web. |
| SET-03 | Hubungkan printer (Bluetooth atau USB) dan jalankan test print. |
| SET-04 | Aktifkan kiosk mode dan kecualikan app dari battery optimization. App terbuka otomatis saat tablet menyala. Lock task butuh tablet disetel sebagai device owner (kondisi baru atau habis reset, tanpa akun Google; akun Mi umumnya juga dihitung dan diuji di M0); fallback: screen pinning. |
| SET-05 | Menu admin tersembunyi (PIN owner) untuk Wi-Fi, printer, dan keluar kiosk. Kasir tidak bisa membuka app lain. |
| SET-06 | Satu tablet melayani satu usaha. Token terikat ke perangkat. Owner dapat mencabut izin dari web kapan saja. |

**Catatan perangkat (Redmi Pad 2 dan iWare C58BT)**

- `minSdk` usulan 29 agar tablet cadangan yang lebih tua masih bisa dipakai. Bluetooth di Android 12 ke atas butuh izin runtime `BLUETOOTH_CONNECT`; pairing printer lewat pengaturan Bluetooth tablet.
- HyperOS umumnya agresif membatasi app di latar belakang. Karena app kiosk hampir selalu di foreground, sinkron utama berupa coroutine foreground dan WorkManager hanya cadangan. Uji keduanya di tablet ini sejak M0, dan atur pengecualian baterai serta autostart untuk kedua aplikasi (dugaan dari pengalaman umum perangkat Xiaomi, belum diuji di tablet ini).
- Kiosk mode dengan device owner butuh tablet yang baru atau habis reset. Device owner mensyaratkan tablet tanpa akun, sedangkan Firebase App Distribution membutuhkan akun Google di perangkat. Karena itu tablet produksi memakai APK bertanda tangan dari rilis internal yang dipasang senyap oleh app lewat PackageInstaller (hak device owner) atau lewat USB terjadwal, dan Firebase App Distribution hanya untuk ponsel penguji. Akun Mi di perangkat Xiaomi umumnya juga dihitung. Semuanya diuji di M0.
- Printer: lebar kertas 58 mm, lebar cetak maksimal 48 mm (384 dot per baris, sekitar 32 karakter per baris pada font standar; pastikan lewat test print). Printer tidak berbaterai sehingga harus dicolok ke listrik, dan punya port RJ11 untuk laci kas (nonaktif default).

**Login dan sesi**

| ID | Kebutuhan |
| --- | --- |
| AUT-01 | Layar login menampilkan hanya akun yang berizin untuk usaha tablet itu, diambil dari data lokal sehingga muncul juga saat offline. |
| AUT-02 | Kasir memilih nama dan memasukkan PIN. Server menerbitkan access token (pendek) dan refresh token, disimpan terenkripsi (DataStore dengan Tink; kunci enkripsinya di Android Keystore). Token memuat peran dan unit usaha. |
| AUT-03 | App membuka layar sesuai peran: kasir ke POS usaha tablet itu, staf laundry ke layar scan status, yang tidak membutuhkan shift terbuka. |
| AUT-04 | Login pertama wajib online. Selanjutnya buka kunci dengan PIN offline (PIN dicek lokal dalam bentuk verifier) selama sesi masih valid. |
| AUT-05 | Layar terkunci otomatis saat idle (usulan 2 menit) tanpa logout, sehingga keranjang dan transaksi berjalan tidak hilang. |
| AUT-06 | Shift milik kasir yang membukanya, dan ganti kasir saat shift aktif mengharuskan tutup shift lebih dulu (kas dihitung per kasir). Login staf laundry bukan pergantian kasir: tidak menutup shift, dan event statusnya (laundry.status\_changed, laundry.picked\_up) tidak terikat ke shift sehingga bisa dicatat sebelum kasir membuka shift. |
| AUT-07 | Token kedaluwarsa diperpanjang otomatis. Bila gagal, app meminta login ulang tanpa menghapus data yang belum terkirim. |

**Bootstrap data**

| ID | Kebutuhan |
| --- | --- |
| BOO-01 | Tablet meminta paket data awal untuk usahanya, per halaman: menu, varian, add-on, harga (cafe) atau layanan dan tarif (laundry), pajak, biaya layanan, aturan pembulatan, preset diskon, akun berizin (verifier PIN), pelanggan aktif, identitas usaha untuk struk, dan nilai Pengaturan. |
| BOO-02 | Data disimpan di Room, lalu penanda (cursor) disimpan. Layar kasir terbuka setelah bootstrap sukses. |
| BOO-03 | Transaksi diblokir sampai bootstrap selesai. Bila terputus, lanjut dari bagian terakhir. Gagal total menampilkan pesan jelas dan tombol Coba lagi. |
| BOO-04 | Setelah itu hanya perubahan yang diambil (delta sync). Data yang dihapus di web hanya ditandai nonaktif (soft delete). |
| BOO-05 | Pelanggan laundry tidak diunduh semuanya: simpan yang aktif atau terbaru, dan cari ke server untuk sisanya saat online. |
| BOO-06 | Migrasi Room diuji dengan data lama. Backend menolak versi app di bawah versi minimum. |

**Buka shift**

| ID | Kebutuhan |
| --- | --- |
| SHF-01 | Bila tidak ada shift aktif, layar Buka Shift muncul otomatis dan POS belum bisa dibuka. |
| SHF-02 | Kasir menghitung uang di laci dan mengisi kas awal per pecahan. App membuat shift dengan UUID di tablet: kasir, tablet, usaha, jam buka, kas awal, status OPEN. |
| SHF-03 | Satu shift aktif per tablet. Setiap transaksi wajib terikat ke shift. |
| SHF-04 | Kas awal standar ditetapkan owner (Pengaturan). Angka yang berbeda dari standar wajib beralasan, dan perubahan setelah dikonfirmasi butuh PIN manager. |
| SHF-05 | Shift kemarin belum ditutup: app meminta ditutup dulu. Bila kasirnya tidak ada, manager menutup paksa dengan catatan, di tablet (event shift.force\_closed, bisa offline) atau dari web. |
| SHF-06 | Jam tablet dibandingkan dengan jam server saat online. Selisih besar menampilkan peringatan. |
