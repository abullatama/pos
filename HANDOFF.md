# HANDOFF: Sesi Implementasi Track Transaksi Laundry M2 Selesai (F19–F24)

## 1. Tujuan Sesi yang Telah Selesai
Menyelesaikan seluruh alur fungsional, operasional, dan antarmuka untuk aplikasi kasir **Athia Laundry House** pada Milestone M2 (F19 sampai F24): pencarian/pembuatan pelanggan, form order per kg & per pcs dengan express (Test Vector L1–L3), cetak tag fisik QR 58 mm, pemindaian status CameraX, pengambilan pakaian sebagian (`laundry_pickups`), cuci ulang komplain dengan PIN manager, dan proteksi penyerahan cucian belum lunas.

---

## 2. Yang Sudah Selesai (Fitur F19–F24 Lengkap)

- **F19: Cari atau Buat Pelanggan (LDR-01, Dialog L02):**
  - Entitas `CustomerEntity` dan `CustomerDao` di `:core:data` dengan penjaminan keunikan nomor telepon ter-normalisasi (`PhoneNumberUtil`).
  - Dialog L02 (`CustomerSelectionDialog`) dengan tab *Cari Pelanggan* dan form *Pelanggan Baru*.
  - Event `customer.created` otomatis disertakan dalam bundel outbox pesanan baru secara atomik tanpa mensyaratkan `shift_id`.
- **F20: Order Laundry per kg & pcs dengan Express (LDR-02–05, L03, Test Vector L1–L3):**
  - Auto-seeding katalog awal `INITIAL_LAUNDRY_SERVICES` (Kiloan Reguler 7k/10k, Cuci Saja 5k, Setrika Saja 5k, Selimut 25k/35k, Bed Cover 40k/55k, Jas 35k, Sepatu 30k) di `DefaultLaundryOrderRepository`.
  - Layar L03 (`LaundryOrderScreen`) pola split-screen 60/40 tablet landscape 11": stepper berat taktil (-/+ 0,5 kg), stepper pcs, saklar express, catatan khusus noda/rapuh, estimasi selesai dinamis, dan 3 opsi pembayaran (`Bayar saat Ambil`, `Lunas di Muka`, `DP`).
  - Teruji 100% dan lulus presisi tanpa selisih 1 rupiah pun terhadap Test Vector: L1 (kiloan + pcs = Rp53.000), L2 (berat minimum = Rp7.000), dan L3 (express = Rp25.000).
- **F21: Cetak Tag QR 58 mm & Scan Status Satu Arah (LDR-06, LDR-08, PRT-03, L07):**
  - `LaundryTagFormatter` di `:core:printer`: format tag kertas roll thermal 58 mm memuat nomor order besar (`L1-0012`), nama & HP pelanggan, rincian pakaian/berat/pcs, catatan noda, dan QR code bitmap 128x128 terpusat **tanpa harga** (PRT-03).
  - Transisi status satu arah (`received`/`open` $\to$ `processing` $\to$ `ready`) dan event outbox `laundry.status_changed` tanpa `shift_id` di `OrderTransactionManager`.
  - Layar L07 (`LaundryScanScreen`) & `LaundryScanViewModel`: kamera viewfinder CameraX + ZXing HybridBinarizer dengan bingkai reticle 24 dp Velvet Rose, bilah pencarian nomor nota manual, kartu rincian pakaian, tombol transisi status satu ketukan, dan tombol cetak tag fisik.
  - Auto-printing tag pakaian secara *non-blocking* saat pesanan baru disimpan di `LaundryOrderScreen`.
- **F22: Pengambilan Sebagian Per Baris (LDR-11, `laundry_pickups`):**
  - Entitas `LaundryPickupEntity` dan `LaundryPickupDao` di `:core:data`.
  - `recordLaundryPickups` di `OrderTransactionManager`: mencatat pengambilan pakaian per baris layanan ke Room dan event outbox `laundry.picked_up`.
  - Status pesanan tetap `ready` jika masih ada baris yang belum diambil, dan bertransisi ke `picked_up` (`Diambil`) hanya jika seluruh baris telah berstatus pengambilan terakhir (`is_final = true`).
- **F23: Cuci Ulang Komplain Pelanggan (LDR-12, Dialog L09):**
  - Status `ready` (Siap) dapat dikembalikan ke `processing` (Diproses) tanpa tagihan biaya baru.
  - Wajib memilih alasan dari daftar audit tetap (`Noda belum hilang`, `Masih apek / bau`, `Kusut / setrika ulang`, dll) dan diotorisasi PIN Manager lokal (Dialog S14).
  - Menghasilkan event outbox `laundry.status_changed` dengan flag `rework: true`, `reason`, dan `approved_by: "u-manager-1"`.
- **F24: Proteksi Penyerahan Cucian Belum Lunas (LDR-10, Dialog S14):**
  - Penyerahan pakaian dengan sisa tagihan > 0 wajib diotorisasi PIN Manager. Jika kasir mencoba menyerahkan tanpa PIN, transaksi ditolak seketika (`IllegalStateException`).
  - Jika disetujui Manager, event `laundry.picked_up` menyertakan `approved_by: "u-manager-1"` dan masuk laporan pengecualian.
  - Proteksi lockout salah PIN 3x terkunci 5 menit (COR-07) terintegrasi.
- **Layar L05 (Daftar Cucian) & L06 (Detail Cucian):**
  - `LaundryOrdersScreen` pola split 40/60:
    - Panel kiri (40%): Segmented tab filter (`Semua`, `Diterima`, `Diproses`, `Siap`, `Diambil`), kartu cucian memuat nomor order, waktu, nama pelanggan, chip status cucian, dan chip status pembayaran (`Lunas`, `DP`, `Belum Lunas`).
    - Panel kanan (60%): Detail pesanan, avatar inisial, ringkasan keuangan, timeline progres status, rincian pakaian per baris, tombol penyerahan L08, tombol cuci ulang L09, tombol cetak tag, dan tombol pelunasan pembayaran yang terhubung langsung ke `PaymentScreen` (S05–S08).
- **Semua Test Suite & Build Hijau 100%:**
  - Seluruh unit test di semua modul (`./gradlew test`) lulus 100% (termasuk `RoomOutboxTransactionTest`, `LaundryOrderViewModelTest`, `LaundryScanViewModelTest`, `LaundryOrdersViewModelTest`, `LaundryTagFormatterTest`).
  - Build kedua APK debug sukses (`:app-cafe:assembleDebug` dan `:app-laundry:assembleDebug`).
  - Aturan isolasi dependensi modul (`verifyModuleDependencies`) lulus tanpa pelanggaran.

---

## 3. Yang Belum Selesai (Langkah untuk Sesi Baru)

Sisa fitur Milestone M2 yang siap dikerjakan di sesi berikutnya:
1. **F27 (Struk, Nota, dan Antrean Cetak Otomatis saat Printer Putus):**
   - Implementasi `PrintQueueManager` di `:core:printer` yang menampung antrean cetakan saat Bluetooth SPP putus dan otomatis mencetak ulang saat tersambung kembali.
   - Penyematan watermark/label `*** SALINAN ***` pada cetak ulang struk/nota/tag (PRT-05).
2. **F28 (Sinkron Menerima Flag Server tanpa Menganggap Gagal):**
   - Menangani respons server `ACCEPTED` dengan `flags[]` (karantina) tanpa menandai outbox gagal.
3. **F29 (Antrean Bermasalah REJECTED dengan Alasan & Retry):**
   - Pengelolaan event yang ditolak server tanpa menghapus data secara diam-diam.

---

## 4. Keputusan Teknis Penting

- **Aturan Bebas Shift Staf Laundry:** Seluruh event `customer.created`, `laundry.status_changed`, dan `laundry.picked_up` tidak mensyaratkan `shift_id` (`shift_id = null`), sehingga staf laundry dapat memindai tag dan memperbarui status kapan saja tanpa membuka/menutup shift kasir.
- **PRT-03 Strict Compliance:** Format tag fisik laundry dilarang memuat harga atau nominal rupiah sama sekali.
- **Siklus Status Satu Arah:** Diterima $\to$ Diproses $\to$ Siap $\to$ Diambil. Mundur status hanya boleh melalui Cuci Ulang (`rework: true`) dengan PIN Manager.
- **Status Pembayaran Terpisah dari Status Cucian:** Order laundry bisa berstatus `ready` (Siap) meskipun status pembayarannya masih `Belum Lunas` atau `DP`; penyerahannya diatur oleh proteksi F24.

---

## 5. File yang Perlu Dibaca Pertama Kali di Sesi Baru
- `HANDOFF.md` (dokumen ini)
- `feature_list.json` (baris 260–294 untuk F27, F28, F29)
- `docs/spec/05-kebutuhan-fungsional-c-pembayaran-struk-.md` (bagian PRT-03 dan PRT-05)
- `docs/spec/12-sinkronisasi-dan-validasi-server.md` (bagian penanganan flag dan reject)
