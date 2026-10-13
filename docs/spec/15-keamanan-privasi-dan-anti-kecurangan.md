## 15. Keamanan, privasi, dan anti-kecurangan

Sistem ini menyimpan uang, data karyawan, dan nomor telepon pelanggan, jadi keamanan dirancang berlapis: perangkat terdaftar, PIN dengan hash lambat, otorisasi di server pada setiap objek, dan audit log yang tidak bisa diubah. Acuan umum yang dipakai sebagai daftar periksa adalah OWASP Top 10 dan ASVS. Bagian privasi bukan nasihat hukum, jadi kepatuhan UU Pelindungan Data Pribadi perlu dikonfirmasi lebih lanjut.

**Pendaftaran perangkat**

1. Owner membuat kode pendaftaran sekali pakai di web (berlaku singkat, terikat ke satu usaha). Ini usulan yang lebih aman daripada antrean permintaan terbuka dan perlu disepakati dengan pembuat app.
2. Tablet mengirim `POST /v1/devices/register` dengan kode, ID perangkat, dan nama. Status perangkat menjadi Menunggu.
3. Owner menyetujui di web, dan status menjadi Aktif. Tablet mengecek `GET /v1/devices/me` dan menerima kredensial perangkat.
4. Kredensial perangkat berupa token panjang acak. Server hanya menyimpan hash-nya, terikat ke ID perangkat dan usaha.
5. Owner dapat mencabut perangkat kapan saja. Semua permintaan dari perangkat itu ditolak dengan `DEVICE_REVOKED`.

**Login PIN dan token di tablet**

- `POST /v1/auth/pin-login` menerima `user_id` dan PIN bersama kredensial perangkat. Server memeriksa: perangkat Aktif, akun berizin untuk usaha perangkat itu, dan PIN cocok.
- PIN disimpan dengan hash lambat dan bergaram (misalnya argon2id atau bcrypt). Percobaan gagal dihitung per akun dan perangkat. Setelah 3 kali, akun terkunci sementara (usulan 5 menit, makin lama bila berulang).
- Untuk buka kunci offline, tablet menerima verifier turunan per perangkat saat bootstrap, bukan hash utama. Kebocoran satu tablet tidak membuka hash utama, dan pencabutan perangkat memutus penggunaannya.
- Access token berumur pendek (usulan 15 menit) dengan klaim `user_id`, peran, `business_unit_id`, dan `device_id`. Refresh token berotasi, hanya hash-nya disimpan di server, dan setiap perpanjangan memeriksa ulang status akun dan perangkat.
- Akun yang dinonaktifkan atau perangkat yang dicabut berlaku paling lambat saat access token berikutnya habis. Endpoint sensitif memeriksa status langsung di database.

**PIN manager pada aksi offline**

Tablet memverifikasi PIN manager secara lokal, dan event koreksi membawa `approved_by`. Server memeriksa bahwa approver adalah manager atau owner aktif di usaha itu pada waktu kejadian (`occurred_at`), bukan pada waktu terima. Karena itu riwayat status akun (aktif sejak dan nonaktif sejak) harus disimpan. Approver yang tidak berizin menghasilkan penolakan `FORBIDDEN`.

**Web owner**

1. Login email dan password (hash lambat), pembatasan percobaan, dan reset password lewat tautan email sekali pakai yang berumur pendek. Password minimal 12 karakter (usulan), diperiksa terhadap daftar password bocor.
2. MFA berbasis TOTP untuk semua akun web (usulan wajib untuk Owner).
3. Sesi: cookie `HttpOnly`, `Secure`, dan `SameSite`, batas idle (usulan 30 menit) dan batas absolut (usulan 12 jam), dengan perlindungan CSRF.
4. Header keamanan: HSTS, Content-Security-Policy, X-Content-Type-Options, dan larangan di-embed dalam frame. CORS hanya untuk asal web owner sendiri.

**Ancaman dan kontrol teknis**

| Ancaman | Kontrol |
| --- | --- |
| Tablet dicuri atau hilang | Pencabutan perangkat dari web, kiosk mode, verifier PIN per perangkat, dan data yang belum sinkron dibuat sesedikit mungkin lewat sinkron segera |
| PIN ditebak | Hash lambat, penguncian sementara, pembatasan laju, dan PIN tidak pernah dikirim ulang |
| Akun owner diambil alih | Password kuat, MFA, pembatasan percobaan login, notifikasi login dari perangkat baru |
| Akses data usaha lain (IDOR) | Semua query difilter usaha dari token atau perangkat, dan otorisasi diperiksa pada setiap objek |
| Upload berbahaya | Batasi tipe dan ukuran, nama file acak, simpan di luar direktori publik, hapus metadata EXIF, sajikan lewat URL bertanda tangan sementara |
| Penyalahgunaan oleh pihak dengan akses database | Hak ubah dan hapus dicabut untuk tabel append-only, akses database dibatasi dan tercatat, audit log terpisah |
| Kebocoran rahasia | Rahasia lingkungan, rotasi saat staf berganti, tidak ada rahasia di repositori atau log |

**API dan data**

- TLS di semua koneksi. Kueri berparameter, validasi input di server, dan pengodean output untuk mencegah injeksi dan XSS.
- Pembatasan laju per akun, perangkat, dan IP.
- Enkripsi saat disimpan (disk atau database) dan backup terenkripsi.
- Hash untuk PIN, password, kredensial perangkat, dan refresh token. Tidak ada yang disimpan apa adanya.
- Lingkungan staging memakai data fiktif, bukan salinan produksi. Dependensi diperbarui rutin dan dipindai kerentanannya.

**Dua aplikasi: identitas dan penandatanganan**

- Satu keystore per aplikasi, masing-masing dicadangkan di dua tempat berbeda. Kebocoran satu kunci tidak memengaruhi aplikasi lain, dan salah satu aplikasi bisa dipindahkan ke pihak lain bila usahanya dipisah.
- Kode pendaftaran terikat ke satu usaha, dan server menolak event usaha lain dari perangkat itu. Keamanan tidak bergantung pada isi aplikasi: aplikasi cafe memang tidak memuat kode laundry, tetapi server yang menegakkannya.
- Satu proyek Firebase dengan dua app Android terdaftar (plus varian debug). Crashlytics memisahkan laporan per aplikasi.

**Audit log**

Yang wajib tercatat: login web dan PIN gagal beruntun, perubahan master data dan pengaturan, pembuatan, persetujuan, dan pencabutan perangkat, pembuatan, nonaktif, dan reset PIN akun, tutup paksa dan catatan penyesuaian, koreksi, serta ekspor data. Isinya aktor, aksi, objek, nilai sebelum dan sesudah, waktu, perangkat, dan IP. Log bersifat append-only.

**Privasi pelanggan**

- Data pelanggan seminimal mungkin: nama dan nomor HP. Tidak ada data kartu atau dokumen identitas.
- Nomor HP hanya dipakai untuk notifikasi terkait order. Pelanggan dapat berhenti menerima pesan (`wa_opt_out`).
- Permintaan penghapusan data ditangani dengan anonimisasi pelanggan (nama dan nomor dikosongkan), sedangkan transaksi keuangan tetap disimpan tanpa identitas.
- Kebijakan privasi singkat disediakan, dan prosedur bila terjadi kebocoran data ditulis: catat kejadian, nilai dampak, hentikan penyebabnya, lalu beri tahu pihak yang berhak sesuai batas waktu dalam UU PDP. Foto nota dan foto kondisi cucian dibatasi aksesnya hanya untuk peran yang berhak.

**Anti-kecurangan: celah dan kontrolnya di MVP**

Sistem tidak bisa menangkap transaksi yang tidak pernah diinput, jadi pertahanannya adalah membuat penjualan yang tidak dicatat mudah ketahuan dari sumber di luar kendali kasir. Tujuan MVP adalah membuat kecurangan lebih sulit dan lebih cepat ketahuan, bukan menghilangkannya sepenuhnya.

| Celah | Kontrol |
| --- | --- |
| Penjualan tidak diinput (skimming) | Nota dan WhatsApp ke pelanggan, tag wajib untuk setiap cucian, cek rak harian, tren modal per kg dan food cost, CCTV, dan cek acak owner |
| Berat laundry diinput lebih kecil | Koreksi berat butuh PIN manager, rata-rata kg per order dibandingkan antar kasir, owner menimbang acak |
| Cucian diambil tanpa lunas | Penyerahan hanya bila lunas. Override butuh PIN manager dan masuk laporan pengecualian |
| QRIS Statis dialihkan atau tidak cocok | Stiker dilaminasi dan dipasang permanen, owner memeriksa berkala, rekonsiliasi harian dengan laporan merchant, cek QRIS per shift |
| Void, refund, atau diskon palsu | PIN manager, alasan wajib, refund transaksi lama bertanda dan berbatas, ringkasan per kasir. PIN manager tidak diketahui kasir |
| Kas Keluar fiktif | Batas tanpa PIN, foto nota wajib di atas batas, tinjauan mingguan |
| PIN dibagi antar kasir | Satu PIN satu orang, auto-lock, audit log |
| Hapus data atau ubah jam tablet | Kiosk tanpa akses uninstall atau hapus data, jam server dibandingkan jam tablet |
| Selisih kas ditutupi | Hitung buta, hitungan terkunci, tren selisih per kasir |

**Kontrol di luar sistem**

- CCTV di area kasir dan rak cucian, serta cek acak oleh owner.
- Tulisan di meja kasir: pastikan menerima nota atau WhatsApp, dan tanpa nota hubungi pemilik.
- Bila memungkinkan, pisahkan tugas antara yang menerima dan yang menyerahkan cucian.
- Batas yang perlu disadari: skimming murni, yaitu menerima uang, tidak menginput, dan pelanggan tidak memeriksa nota, hanya bisa ditutup oleh kombinasi nota ke pelanggan, CCTV, dan cek acak, bukan oleh aplikasi saja.
