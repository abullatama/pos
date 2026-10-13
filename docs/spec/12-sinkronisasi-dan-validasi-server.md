## 12. Sinkronisasi dan validasi server

Setiap bundel diterima atau ditolak secara utuh dalam satu transaksi database, dan mengirim ulang bundel yang sama tidak pernah membuat data ganda. Server tidak percaya total dari tablet: ia menghitung ulang dan membandingkan sampai rupiah. Transaksi bersifat append-only, jadi konflik hampir tidak ada; untuk master data, server menang.

**Alur pemrosesan satu request**

1. Periksa kredensial perangkat (Aktif), token, dan versi app minimum.
2. Untuk tiap bundel (independen satu sama lain), cari `bundle_id` di `sync_bundles`. Bila sudah ada, kembalikan hasil sebelumnya: `DUPLICATE` untuk yang pernah diterima, atau penolakan yang sama untuk yang pernah ditolak.
3. Mulai transaksi database dan kunci baris yang terkait (order dan shift) untuk mencegah balapan.
4. Validasi event satu per satu sesuai urutan, dengan aturan di bawah.
5. Tulis record, perbarui kolom turunan (`status`, `paid_amount`), dan tulis audit log untuk event sensitif.
6. Catat bundel di `sync_bundles` lalu commit. Bila ada kegagalan, batalkan seluruh bundel dan catat penolakan beserta alasannya.
7. Setelah commit, masukkan tugas ke antrean (misalnya WhatsApp saat `laundry.status_changed` menjadi Siap).
8. Balas dengan hasil per bundel dan `server_time`.

**Aturan validasi**

| Aturan | Pemeriksaan | Hasil |
| --- | --- | --- |
| Perangkat dan usaha | `device_id` cocok dengan kredensial, dan semua event untuk usaha perangkat itu | Ditolak: `FORBIDDEN`, `DEVICE_REVOKED` |
| Izin peran | Jenis event sesuai peran pengirim. Staf Laundry hanya boleh `laundry.status_changed` dan `laundry.picked_up` | Ditolak: `FORBIDDEN` |
| Skema | Field wajib, tipe, panjang, dan UUID valid | Ditolak: `VALIDATION_FAILED` |
| Shift | Shift yang dirujuk harus dikenal (bila belum: `DEPENDENCY_MISSING`). `occurred_at` di luar rentang `opened_at` sampai `closed_at` tidak ditolak: diterima dan ditandai. Event `laundry.*` dan `customer.created` tidak mensyaratkan shift. Pembayaran terikat ke shift yang menerimanya, bukan shift pembuat order | Diterima, ditandai `SHIFT_RANGE` |
| Total | Server menghitung ulang dari baris order dengan rumus bagian 7. Bila berbeda, transaksi dicatat sesuai angka tablet (itulah yang dibayar pelanggan), `server_total` disimpan di sampingnya, lalu masuk karantina untuk ditinjau owner | Diterima, ditandai `TOTAL_MISMATCH` |
| Referensi item | `item_ref_id` atau layanan harus dikenal server. Harga snapshot yang berbeda dari master pada `occurred_at` tidak ditolak, tetapi ditandai dan masuk laporan pengecualian | Diterima, ditandai `UNKNOWN_ITEM` |
| Pembayaran | Jumlah lebih dari nol, total alokasi ke order tidak melebihi total order (angka tablet), `idempotency_key` unik, metode hanya `cash` atau `qris_static` | Ditolak: `VALIDATION_FAILED` |
| Koreksi | `approved_by` adalah manager atau owner aktif pada `occurred_at`, alasan dari daftar tetap, dan nilainya tidak melebihi nilai asal | Ditolak: `FORBIDDEN`, `VALIDATION_FAILED` |
| Status laundry | Transisi sah: Diterima, Diproses, Siap, Diambil. Cuci ulang (Siap kembali ke Diproses) wajib beralasan dan `approved_by`. Diambil hanya bila order lunas atau ada `approved_by` | Ditolak: `VALIDATION_FAILED` |
| Pengambilan | `laundry.picked_up` merujuk baris order yang dikenal, dan jumlah kumulatif tidak melebihi jumlah baris | Ditolak: `VALIDATION_FAILED` |
| Kas keluar | Di atas batas seharusnya ada `approved_by` dan foto nota. Uang sudah keluar dari laci, jadi kekurangan dicatat, bukan ditolak | Diterima, ditandai `CASHOUT_UNAPPROVED` |
| Tutup shift | Server menghitung ulang kas seharusnya dan menyimpannya di samping angka dari tablet. Selisih antara keduanya ditandai. `cash_left` + `cash_deposited` harus sama dengan `counted_cash`, atau selisihnya beralasan | Diterima, ditandai `CASH_MISMATCH`, `CASH_ALLOCATION` |
| Waktu | `occurred_at` yang lebih dari 5 menit di masa depan atau lebih tua dari 7 hari saat diterima tidak ditolak. Selisih jam tablet dan server dicatat, dan tanggal bisnis memakai `occurred_at` yang dikoreksi dengan selisih terakhir. Umur event tidak pernah menjadi alasan penolakan | Diterima, ditandai `CLOCK_SKEW`, `OLD_EVENT` |
| Dependensi | Event merujuk record yang belum diterima (misalnya shift atau order dari bundel sebelumnya) | Ditolak sementara: `DEPENDENCY_MISSING` |

Hasil `REJECTED` membawa field `retryable` (usulan penambahan di kontrak). `DEPENDENCY_MISSING` bernilai `true`, dan app mengulang dengan backoff. Penolakan lain bernilai `false`, dan app memasukkannya ke Antrean bermasalah.

**Prinsip v2.2: terima fakta uang, tandai, lalu karantina.** Tablet adalah saksi atas apa yang dibayar pelanggan, jadi menolak bundel membuat penjualan nyata hilang dari laporan. Hanya `FORBIDDEN`, `DEVICE_REVOKED`, `VALIDATION_FAILED`, dan `DEPENDENCY_MISSING` yang menghasilkan `REJECTED`. Pelanggaran lain menghasilkan `ACCEPTED` dengan `flags[]`, disimpan di tabel `sync_flags`, tampil di laporan pengecualian dan antrean Karantina di web, dan memicu peringatan bila melonjak. Tabel `sync_bundles` disimpan minimal 365 hari, jauh lebih lama daripada retensi tablet (30 hari), agar bundel lama yang dikirim ulang tetap dikenali sebagai `DUPLICATE`.

**Urutan dan konkurensi**

- App menjamin event untuk order yang sama dikirim berurutan. Server tidak mengasumsikan urutan antar-order, dan bundel tidak boleh dianggap tiba berurutan antar-order.
- Dua request bersamaan untuk bundel yang sama dilindungi oleh kunci `bundle_id` (kunci unik di `sync_bundles`). Yang kedua menunggu atau menerima hasil `DUPLICATE`.
- Ukuran per request dibatasi (usulan 50 bundel).

**Delta sync master data**

- Cursor bersifat opaque dan diberikan server (versi monotonik berbasis `change_seq`). Halaman default 200 record, dan bisa dilanjutkan dari bagian terakhir.
- Respons memuat record baru, berubah, dan `deleted` agar tablet bisa menyembunyikannya. Soft delete disimpan minimal 30 hari; bila cursor kedaluwarsa server membalas 410 dan app melakukan bootstrap ulang.
- Retry di app hanya untuk 429 dan 5xx dengan exponential backoff dan jitter (usulan 5 detik sampai maksimal 15 menit). Error 422 tidak diulang dan masuk antrean bermasalah.

**Pencatatan dan peringatan**

- Hitung jumlah `ACCEPTED`, `DUPLICATE`, dan `REJECTED` per kode alasan, plus latensi pemrosesan.
- Lonjakan penolakan, terutama `TOTAL_MISMATCH`, memicu peringatan ke pembuat backend dan owner, karena itu tanda perbedaan rumus antara app dan server.
