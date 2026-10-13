## 2. Peran dan hak akses

Ada empat peran, dan setiap aksi sensitif dikunci dengan PIN manager. Akses yang berlaku adalah **irisan** antara izin tablet (usaha yang didaftarkan) dan izin akun. Peran ditegakkan di server pada setiap permintaan, tidak pernah dipercaya dari klien.

**Peran**

| Peran | Tugas |
| --- | --- |
| Kasir | Melayani transaksi di usaha tablet itu, membuka dan menutup shift sendiri |
| Staf Laundry | Hanya scan tag dan mengubah status cucian; tanpa akses pembayaran atau laporan |
| Manager | Pemegang PIN manager (bisa owner sendiri atau satu orang kepercayaan) yang menyetujui aksi sensitif |
| Owner | Mengelola semuanya lewat web, serta menu admin tablet dengan PIN owner |

**Matriks izin di tablet**

| Aksi | Kasir | Staf Laundry | Manager | Owner |
| --- | --- | --- | --- | --- |
| Login tablet dengan PIN | Ya | Ya | Ya | Ya |
| Buka dan tutup shift | Ya | Tidak | Ya | Web (tutup paksa) |
| Buat order, terima pembayaran | Ya | Tidak | Ya | Tidak |
| Scan tag dan ubah status laundry | Ya | Ya | Ya | Tidak |
| Hapus item sebelum dikirim ke dapur | Ya | Tidak | Ya | Tidak |
| Diskon preset | Ya | Tidak | Ya | Tidak |
| Diskon manual, gratis, komplimen | PIN manager | Tidak | Ya | Tidak |
| Void item setelah dikirim ke dapur | PIN manager | Tidak | Ya | Tidak |
| Void atau refund transaksi lunas | PIN manager | Tidak | Ya | Tidak |
| Ubah harga atau berat setelah nota | PIN manager | Tidak | Ya | Tidak |
| Kas keluar sampai batas | Ya | Tidak | Ya | Tidak |
| Kas keluar di atas batas | PIN manager | Tidak | Ya | Tidak |
| Cetak ulang struk (berlabel SALINAN) | Ya | Tidak | Ya | Tidak |
| Menu admin tablet (printer, WiFi, keluar kiosk) | Tidak | Tidak | Tidak | PIN owner |
| Daftarkan atau cabut tablet, kelola akun dan pengaturan | Tidak | Tidak | Tidak | Web |
| Koreksi setelah shift ditutup | Tidak | Tidak | Tidak | Web |
| Laporan Z shift sendiri | Ya | Tidak | Terbatas | Web |
| Laporan penjualan, pengecualian, laba-rugi | Tidak | Tidak | Terbatas (web) | Web |

**Aturan akun**

1. Satu PIN untuk satu orang, tidak dibagi. Kasir memakai PIN 4-6 digit; owner dan manager memakai password kuat di web.
2. PIN salah 3 kali mengunci akun sementara. Akun karyawan yang keluar dinonaktifkan dari web.
3. Satu tablet hanya punya satu pengguna aktif pada satu waktu. Layar terkunci otomatis saat idle tanpa logout.
4. PIN manager tidak boleh diketahui kasir. Semua penggunaannya tercatat di audit log.

Hak akses per layar web owner ada di bagian 14, dan otorisasi per kelompok endpoint di bagian 11.
