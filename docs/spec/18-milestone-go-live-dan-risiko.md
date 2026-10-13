## 18. Milestone, go-live, dan risiko

MVP dikerjakan dalam lima milestone dengan satu gerbang lulus di akhir tiap tahap, dan kedua usaha go-live bersamaan hanya setelah masa paralel 1-2 minggu tanpa selisih yang tak terjelaskan. Pengerjaan internal berurutan per modul, tetapi rilis ke staf dilakukan bersama untuk cafe dan laundry.

&#91;embedded content: roadmap MVP · 5 milestone, 5 gerbang\]

**Masa paralel dan go-live**

- Selama 1-2 minggu pencatatan lama tetap berjalan di kedua usaha, dan selisih dibandingkan setiap hari.
- Buku nota kertas bernomor dan prosedur darurat sudah tercetak sebelum hari pertama.
- Staf dilatih sekitar satu jam.
- Owner memutuskan lanjut atau tahan berdasarkan laporan selisih.

**Risiko dan mitigasi**

| Risiko | Dampak | Mitigasi |
| --- | --- | --- |
| Dua usaha go-live sekaligus | Bug sync atau printer mengganggu keduanya | Masa paralel, nota kertas cadangan, prosedur darurat, uji lebih berat di M2 dan M3 |
| Printer atau sync di background bermasalah di tablet asli | Cetak atau sinkron tidak andal | Spike di M0 sebelum modul lain dibangun |
| Fonnte tidak resmi, nomor bisa diblokir | Notifikasi laundry gagal | Nomor khusus usaha, hanya pesan transaksional, tombol Kirim via WA manual |
| Owner lupa mencatat pengeluaran | Laba terlihat lebih tinggi dari kenyataan | Pengingat mingguan, kategori tetap, pengeluaran berulang, cocokkan laba dengan saldo kas dan bank tiap akhir bulan |
| Kontrak API berubah tanpa kabar | App dan backend tidak cocok | OpenAPI sebagai sumber kebenaran, perubahan lewat pull request dengan changelog, mock server, staging |
| Tablet hilang sebelum sinkron | Data yang belum terkirim hilang | Sinkron segera tiap transaksi, jangan reset sebelum outbox kosong, cabut izin dari web |
| Tidak ada pemilik perawatan setelah rilis | Bug tidak tertangani | Tentukan penanggung jawab (bagian 19) |

**Penyesuaian untuk dua aplikasi**

- M1 membuat kedua cangkang aplikasi dan `:build-logic` sekalipun cangkang kedua hampir kosong; memecah satu aplikasi menjadi dua di tengah jalan jauh lebih mahal.
- Risiko tambahan: dua aplikasi menyimpang bila logika disalin ke cangkang. Penanganan: aturan "cangkang hanya wiring" dan uji dependensi modul di CI.
- Beban rilis dan uji bertambah. Penanganan: satu pipeline CI untuk kedua aplikasi dan matriks uji per aplikasi (bagian 17).
- Satu keystore per aplikasi berarti dua kunci yang harus dicadangkan dan dijaga.
