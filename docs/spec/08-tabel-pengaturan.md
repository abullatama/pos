## 8. Tabel Pengaturan

Setiap nilai punya default dan penanda tempat mengubahnya, supaya MVP tidak membangun layar pengaturan yang tidak perlu. Halaman Pengaturan di web owner harus dibangun untuk semua item bertanda **Web**. Item **Tertanam** diubah lewat konfigurasi oleh developer. Nilai bertanda "perlu diisi" menunggu keputusan owner, dan angka usulan hanya titik awal. Pengaturan disimpan dengan riwayat perubahan (siapa, kapan, nilai lama dan baru) dan berlaku untuk transaksi baru, tidak untuk transaksi lama.

| Pengaturan | Default atau usulan | Diubah oleh | Tempat di MVP |
| --- | --- | --- | --- |
| Kas awal standar per usaha | Perlu diisi (contoh Rp200.000) | Owner | Web |
| Toleransi selisih kas | Perlu diisi (usulan Rp2.000) | Owner | Web |
| Batas Kas Keluar tanpa PIN | Perlu diisi (usulan Rp50.000) | Owner | Web |
| Preset diskon (nama, persen atau nominal) | Perlu diisi | Owner | Web |
| Persentase dan mode pajak (ditambah atau termasuk) | Perlu diisi | Owner | Web |
| Biaya layanan (aktif, persen) | Nonaktif | Owner | Web |
| Kelipatan pembulatan total | Rp100 | Owner | Web |
| Tarif layanan laundry (per kg, per pcs, express) | Perlu diisi | Owner | Web |
| Berat minimum dan kelipatan berat laundry | Perlu diisi (usulan 1 kg, kelipatan 0,5 kg) | Owner | Web |
| Estimasi selesai per layanan | Perlu diisi (usulan reguler 2 hari, express 6 jam) | Owner | Web |
| Hari sebelum cucian dianggap tidak diambil (N) | Perlu diisi (usulan 14 hari) | Owner | Web |
| Jam buka tiap usaha dan zona waktu | Perlu diisi | Owner | Web |
| Menu, varian, add-on, harga, modal per porsi | Perlu diisi | Owner | Web |
| Modal per kg laundry | Perlu diisi (dihitung bulanan) | Owner | Web |
| Nama usaha, alamat, pesan penutup struk | Perlu diisi | Owner | Web |
| Nomor WhatsApp dan template pesan | Perlu diisi | Owner | Web (backend) |
| Akun karyawan, peran, dan PIN | Perlu diisi | Owner | Web |
| Pengeluaran berulang (sewa, gaji, internet) | Perlu diisi | Owner | Web |
| Label metode bayar | Tunai, QRIS | Owner | Tertanam |
| Lebar kertas printer (58 atau 80 mm) | Sesuai printer | Owner | Menu admin tablet |
| Buka laci kas otomatis | Nonaktif | Owner | Menu admin tablet |
| Auto-lock layar saat idle | Usulan 2 menit | Developer | Tertanam |
| Batas salah PIN sebelum akun terkunci | 3 kali | Developer | Tertanam |
| Daftar alasan void dan refund | Salah input, pelanggan batal, komplain kualitas, salah buat, promo, lainnya | Developer | Tertanam |
| Masa simpan event sinkron di tablet | 30 hari | Developer | Tertanam |
| Versi app minimum | Versi rilis pertama | Developer | Remote config |
| Ambang laporan pengecualian | Usulan di bagian 14 | Owner | Web |
| Batas nilai refund transaksi shift sebelumnya di tablet | Usulan Rp100.000 | Owner | Web |
| Umur maksimum transaksi untuk refund di tablet | Usulan 14 hari | Owner | Web |

Saklar fitur `qris_dynamic` dihapus pada v2. Mekanisme saklar fitur lewat `GET /v1/config` tetap ada untuk perubahan berisiko di masa depan, dengan daftar awal kosong.
