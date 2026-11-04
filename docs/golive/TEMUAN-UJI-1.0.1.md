# Temuan Uji Tablet 1.0.1 (non-blocker)

Sumber: uji di Redmi Pad 2, APK rilis 1.0.1 (10001), server nyata (data uji), 5 Nov 2026.
Hasil blocker: B1 sampai B8 **lulus semua (8/8)**. Bagian C, L, S, dan rekonsiliasi (bagian 6) belum dijalankan.

Semua temuan di bawah bukan blocker. Jangan mengubah rumus uang atau perilaku sinkron.

## UI-1: Daftar item tertutup di panel kanan (cafe)
- Gejala: pada pesanan dine-in dengan diskon preset 10%, rincian harga di bawah daftar (subtotal, diskon, pajak, pembulatan) memakan tinggi panel kanan sebelum tombol "Kirim ke dapur". Daftar item jadi hampir tidak terlihat.
- Contoh angka: 2 item (25.000 + 20.000), diskon -4.500, pajak +4.050, pembulatan +50, total 44.600. Hitungan benar; masalahnya hanya tata letak.
- Harapan: daftar item tetap terlihat (tinggi minimum atau bisa di-scroll), rincian harga ringkas.
- Foto layar: belum ada.

## UI-2: Pesan PIN terlalu umum
- Gejala: PIN manager salah menampilkan "pin manager salah, terkunci, atau akun manager belum tersinkron". Kasir tidak tahu penyebab sebenarnya.
- Harapan: pesan dibedakan (PIN salah, terkunci beserta sisa waktu, akun belum tersinkron). Teks di resource string, Bahasa Indonesia, tanpa istilah teknis.
- Catatan: kunci 3 kali salah = 5 menit sudah benar (jangan diubah).

## UI-3: Riwayat transaksi selesai tidak menampilkan jenis pembayaran (cafe)
- Gejala: di tablet, riwayat transaksi selesai tidak menunjukkan Tunai atau QRIS. Di server tipenya benar.
- Harapan: tampilkan jenis pembayaran per transaksi.
- Belum dicek: apakah laundry sama.

## AUTH-1: Layar tiba-tiba pindah ke pilih akun dan PIN (cafe)
- Gejala: internet hidup, tablet didiamkan beberapa saat, lalu menambah pesanan baru, layar tiba-tiba pindah ke pilih akun dan PIN kasir. Pesanan tidak hilang setelah login ulang.
- Hasil pencarian awal: tidak ditemukan pengatur waktu tidak aktif di kode produksi (`core/auth`, `app-cafe`, `feature/cafe`); kata "idle" hanya muncul di komentar tes `AuthManagerTest`.
- Dugaan (belum terbukti): sesi kasir berakhir karena token habis atau diperbarui saat tablet kembali online. Terkait skenario S2.
- Yang dibutuhkan: pola waktu bila terulang (berapa lama sejak login atau sejak online, indikator sinkron sebelumnya, tombol yang baru ditekan). Jangan menebak; mulai dari tempat sesi dikunci, lalu konfirmasi ke pengguna bila tidak ketemu dalam 2 sampai 3 pembacaan.

## WEB-1: Tampilan pesanan diskon manual di web owner (backend, bukan repositori ini)
- Pesanan diskon manual tampil sebagai order harga penuh (pajak dari harga penuh), lalu koreksi manual_discount, tanpa baris pembulatan. Total tetap sama dengan tablet.
- Ini sesuai desain (diskon manual = order harga penuh + koreksi). Urutan tablet (diskon, lalu pajak, lalu pembulatan) sudah sesuai aturan.
- Saran untuk backend: tampilkan "tagihan setelah koreksi", hubungkan koreksi ke order asal dengan nama manager, dan jelaskan perlakuan pembulatan.

## Status perbaikan (5 Nov 2026)
- UI-1: selesai di kode. Daftar item punya tinggi minimum 140 dp, judul "Payment Details" dihapus, jarak rincian harga dirapatkan. Rumus tidak disentuh. Perlu dilihat di tablet (`needs_device`).
- UI-2: selesai. Dialog diskon manual dan dialog void/refund memakai `ApprovalResult.failureMessage()`: PIN salah (dengan sisa percobaan), terkunci (dengan menit), akun manager belum tersinkron. Aturan kunci 5 menit tidak diubah.
- UI-3: selesai untuk cafe. Kartu riwayat selesai menampilkan Tunai, QRIS, atau Tunai + QRIS dari tabel `payments` (query baca saja, tanpa migrasi). Laundry belum dicek.
- AUTH-1: belum disentuh, menunggu pola waktu dari pengguna.

## Hasil uji tablet perbaikan (5 Nov 2026)
UI-1, UI-2, dan UI-3 (cafe) diuji di Redmi Pad 2 dengan APK rilis bertanda tangan, dipasang sebagai update tanpa uninstall. Hasil: sesuai harapan. UI-3 laundry dan AUTH-1 masih terbuka.

## AUTH-1: tindakan (5 Nov 2026)
- Dibangun auto-lock idle sesuai AUT-05: `IdleLockController` di `core:auth`, dipasang di `app-cafe` (2 menit tanpa sentuhan, jam monoton). Aksi hanya `lock()`, tanpa logout; token dan transaksi tidak tersentuh. Laundry belum dipasang.
- Temuan: `initialize()` dan `refreshToken()` sebelumnya bisa membuka kembali layar yang dikunci. Kini ada penanda kunci idle yang dihormati keduanya; dibuka hanya lewat PIN.
- Penyebab layar pindah sendiri BELUM terbukti. Hipotesis: sinkron latar menerima 401, `refreshToken()` gagal, lalu `logout()` (MainActivity cafe baris sinkron data master). `refreshToken()` juga mengembalikan false pada gangguan jaringan sesaat. Proses dimatikan sistem kurang mungkin: `initialize()` membuka layar bila token masih ada.
- Log diagnostik tanpa data sensitif, tag `AuthDiag`: lock, logout, penyebab refresh gagal, dan awal aplikasi. Saat terulang: `adb logcat -d | grep AuthDiag`.
- Perbaikan `refreshToken` terhadap gangguan jaringan ditunda sampai log membuktikan.
- Perlu uji tablet: layar terkunci setelah 2 menit, keranjang utuh setelah PIN, buka kunci offline.
