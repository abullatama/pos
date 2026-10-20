# HANDOFF: Penyempurnaan Alur Laundry (9 Poin) & Penerapan Gerbang Shift Cerdas (Opsi 1)

## 1. Tujuan Sesi yang Telah Selesai
Menuntaskan **9 poin evaluasi & penyempurnaan alur kasir laundry** serta menerapkan **Gerbang Shift Cerdas (Opsi 1)** pada kedua aplikasi (`app-laundry` dan `app-cafe`):

1. **Pemisahan Status Pembayaran vs Status Tahapan Fisik Cucian:**
   - Memperbaiki bug kritis di mana pembayaran lunas pesanan laundry sebelumnya menimpa `order.status` menjadi `"paid"`, yang menyebabkannya hilang dari tab filter tahapan cucian (Diterima, Diproses, Siap).
   - Di `OrderTransactionManager.recordPayment()`, pesanan laundry (`orderType == "laundry"`) kini mempertahankan status fisiknya (`open`, `processing`, `ready`, atau `picked_up`) dan hanya memperbarui `paidAmount`.
   - Pesanan yang dibayar lunas di muka tetap berada di tab tahapan cucian yang sesuai dengan indikator status Lunas (Sisa Rp 0).

2. **Nomor Nota Berkelanjutan & Anti-Bentrok Antar-Hari:**
   - Mengganti penghitungan nomor urut yang sebelumnya mereset ke `0001` setiap hari dengan penghitungan total pesanan berkelanjutan per tablet (`countTotalOrdersForPrefix`), sehingga urutan berjalan kontinu (`L1-0001`, `L1-0002`, `L1-0003`, dst) tanpa risiko bentrok antar-hari di rak pakaian.
   - Menambahkan urutan `ORDER BY createdAt DESC LIMIT 1` pada kueri Room `getOrderByDisplayNumber` dan `getOrderWithLinesByDisplayNumber` sehingga pencarian nota selalu mengembalikan pesanan terbaru yang aktif.

3. **Input Pencarian Nomor Nota Ramah Angka Murni:**
   - Mengubah keyboard pencarian nota di Layar Cucian menjadi angka murni (`KeyboardType.Number`).
   - Menambahkan kueri pencarian fleksibel `findLaundryOrderFlexible`: kasir cukup mengetik angka saja (misal `12` atau `0012`), sistem secara otomatis mencocokkan nota `L1-0012`. Kasir tidak perlu repot mengetik huruf `"L"` dan tanda `"-"`.

4. **Penyederhanaan Navigasi & Aksi Status Cucian Langsung (L06):**
   - Menghapus item navigasi `"Scan QR"` dari Left Navigation Rail agar navigasi lebih ringkas dan fokus.
   - Menambahkan tombol aksi status langsung pada panel rincian pakaian di Layar Cucian (`LaundryOrdersScreen` / L06):
     * Status **Diterima** $\rightarrow$ Tombol **"Mulai Cuci (Tandai Diproses)"**
     * Status **Diproses** $\rightarrow$ Tombol **"Selesai Cuci (Tandai Siap Diambil)"**
     * Status **Siap** $\rightarrow$ Tombol **"Ambil Cucian"** & **"Cuci Ulang"**
   - Menambahkan tombol pintasan kamera **"Pindai Tag QR"** di samping kolom pencarian nota untuk pemindaian instan tanpa berpindah menu.

5. **Standardisasi Label Navigasi Menu Shift:**
   - Mengubah label menu ke-4 di Left Rail `app-laundry` dari sebelumnya `"Lainnya"` menjadi **"Shift"** dengan ikon jam (`Icons.Outlined.AccessTime`), seragam dengan `app-cafe` dan standar POS.

6. **Pembersihan Teks Jargon & Tanda Kurung Berlebih:**
   - Menghapus seluruh kode dokumen teknis internal developer seperti `(LDR-01)`, `(LDR-03)`, `(LDR-12)`, `(COR-07)`, `(L09 / F23)` dari antarmuka kasir.
   - Menyederhanakan label: `Kiloan (Kg)` $\rightarrow$ `Kiloan`, `Satuan (Pcs)` $\rightarrow$ `Satuan`, `Ambil Cucian (Penyerahan)` $\rightarrow$ `Ambil Cucian`, `Catatan Khusus (Noda / Kerusakan / Instruksi)` $\rightarrow$ `Catatan Khusus`.

7. **Penerapan Gerbang Shift Cerdas (Opsi 1):**
   - **Laundry POS (`app-laundry`):**
     * Layar Order Baru (L03) menampilkan gerbang visual elegan `A3EmptyState` jika shift kasir belum dibuka (`activeShift == null`), membimbing kasir langsung ke alur Buka Shift (S04) sebelum mencatat order baru.
     * Navigasi Left Rail dan tombol "+ Order Baru" di Beranda otomatis mengarahkan ke Shift jika belum ada shift aktif.
     * Layar Cucian (L05/L06) tetap terbuka penuh bagi staf cuci untuk memajukan status pakaian tanpa shift, namun tombol **"Terima Pelunasan Sisa"** diproteksi dengan dialog Buka Shift jika belum ada shift aktif.
   - **Cafe POS (`app-cafe`):**
     * Layar Kasir POS (C01) terkunci rapi dengan `A3EmptyState` gerbang Buka Shift jika shift belum dibuka.
     * Tombol proses pembayaran pada Layar Pesanan (C04) diproteksi validasi shift aktif.
     * Navigasi Left Rail mengarahkan kasir langsung ke Buka Shift jika shift belum aktif.

---

## 2. Yang Sudah Selesai di Sesi Ini

### A. Modul `:core:data`
- **`OrderTransactionManager.kt`:**
  * Logika `recordPayment` diperbarui: hanya pesanan cafe yang mengubah status ke `paid`/`dp`, sedangkan pesanan laundry mempertahankan status fisik aslinya.
- **`OrderDao.kt`:**
  * Penambahan kueri `countTotalOrdersForPrefix(prefix)` untuk penomoran berkelanjutan.
  * Penambahan `ORDER BY createdAt DESC LIMIT 1` pada kueri pengambilan berdasarkan nomor nota.
  * Penambahan kueri `findLaundryOrderFlexible(query)` untuk pencarian angka murni.
- **`RoomOutboxTransactionTest.kt`:**
  * Penambahan unit test `recordPayment on laundry order updates paidAmount and preserves physical status` (lulus 100%).

### B. Modul `:feature:laundry`
- **`DefaultLaundryOrderRepository.kt`:**
  * Integrasi `countTotalOrdersForPrefix` pada `getNextDisplayNumber`.
  * Implementasi `findOrderByNumberFlexible`.
- **`LaundryOrdersViewModel.kt`:**
  * Penambahan `advanceStatusToProcessing()` dan `advanceStatusToReady()`.
  * Integrasi pencarian reaktif fleksibel dan pencarian via scan payload.
- **`LaundryOrdersScreen.kt` (L05 & L06):**
  * Penambahan bilah pencarian nota angka murni & tombol kamera Scan QR di panel kiri.
  * Penambahan tombol aksi status langsung di panel rincian pesanan.
  * Proteksi dialog Buka Shift pada tombol "Terima Pelunasan Sisa".
  * Pembersihan seluruh teks istilah teknis dalam kurung.
- **`LaundryOrderScreen.kt` (L03):**
  * Penambahan gerbang visual `A3EmptyState` jika `hasActiveShift == false`.
  * Dialog konfirmasi buka shift saat kasir mencoba pembayaran di kasir tanpa shift aktif.
- **Unit Test Suite `:feature:laundry`:**
  * Penambahan pengujian `advanceStatusToProcessing and advanceStatusToReady transitions status and updates messages` dan `onSearchQueryChange filters and selects order with pure numeric input` pada `LaundryOrdersViewModelTest` (28/28 tests lulus 100%).

### C. Modul Cangkang (`app-laundry` & `app-cafe`)
- **`app-laundry/MainActivity.kt` & `LaundryNavHost.kt`:**
  * Navigasi Left Rail disederhanakan (4 item: Beranda, Order Baru, Cucian, Shift).
  * Pengalihan rute pintar ke `SHIFT` jika kasir mengakses Order Baru saat shift belum buka.
  * Penerusan `hasActiveShift` dan `shiftId` aktif ke layar pesanan dan pembayaran.
- **`app-cafe/MainActivity.kt` & `CafeNavHost.kt`:**
  * Gerbang `A3EmptyState` pada rute `KASIR` saat shift belum dibuka.
  * Pengalihan rute pintar ke `SHIFT` pada Left Rail.
  * Proteksi pembayaran pesanan terbuka saat belum buka shift.

---

## 3. Status Terkini Proyek

- **Milestone M0 (Spike & Hardware):** 5 dari 5 tuntas (100%)
- **Milestone M1 (Fondasi & Multi-modul):** 9 dari 9 tuntas (100%)
- **Milestone M2 (Transaksi Cafe & Laundry):** 15 dari 15 tuntas (100%)
- **Milestone M3 (Shift, Kas, & Kios Admin):** 11 dari 11 tuntas (100%)
- **Milestone M4 (Laporan, WA, & Go-live):** 0 dari 4 (0%) — **Milestone Aktif**
- **Total Fitur Selesai:** **40 dari 44 fitur tuntas (90,9%)**.
- **Kondisi Kode:** Seluruh unit test proyek lulus 100%, kompilasi kedua aplikasi (`app-laundry` dan `app-cafe`) berhasil tuntas tanpa error.

---

## 4. Langkah untuk Sesi Baru (Memulai Milestone Terakhir M4)

Sisa 4 fitur penutup proyek pada **Milestone M4**:
1. **F42 (Tombol Kirim via WA Manual dan Status Notifikasi) — *Rekomendasi Utama Sesi Berikutnya*:**
   - Fitur spesifik laundry: tombol kirim WA manual (hijau taktil `#25D366`) dan tampilan chip status notifikasi server (`none`, `queued`, `sent`, `failed`) pada kartu rincian cucian (L06 / LDR-09).
   - Menghubungkan client endpoint `GET /v1/orders/{id}/notification-status`.
2. **F41 (Heartbeat dan Crashlytics):**
   - Telemetri status perangkat berkala (`POST /v1/devices/heartbeat`) dan integrasi pelaporan crash/galat tanpa data sensitif pelanggan (OPS-01, OPS-02).
3. **F43 (Uji Offline 24 Jam):**
   - Pengujian operasional offline 24 jam penuh pada tablet fisik Redmi Pad 2 (`needs_device: true`).
4. **F44 (Paket Go-live: Nota Kertas Cadangan dan SOP Darurat):**
   - Dokumen SOP darurat satu halaman saat tablet/printer/internet mati dan template buku nota manual bernomor.

---

## 5. File Referensi Penting untuk Sesi Baru

- `HANDOFF.md` (dokumen ini)
- `feature_list.json` (baris 404–445 untuk Milestone M4)
- `docs/milestones/M4-laporan-golive.md`
- `docs/spec/13-whatsapp-lewat-fonnte-dan-penjadwal.md`
- `docs/spec/16-infrastruktur-deployment-backup-dan-pema.md`
- `docs/spec/17-skenario-uji-dan-kriteria-lulus.md`

---

## 6. Panduan Pengujian Hardware & Demo

Untuk pengujian fisik di perangkat Redmi Pad 2 dan printer iWare C58BT:
- **Perintah Pasang APK:**
  ```bash
  ./gradlew :app-cafe:installDebug
  ./gradlew :app-laundry:installDebug
  ```
- **Kredensial & PIN Sistem:**
  * **Pintasan Demo:** Tombol *"Masuk Mode Demo / Lewati"* di layar pendaftaran perangkat awal.
  * **PIN Kasir (Shift & Transaksi):** `1234`
  * **PIN Manager (Otorisasi Khusus):** `4444` (Diskon manual, void/refund, kas keluar > 50rb, cuci ulang, ambil belum lunas, tutup paksa shift. Proteksi: salah 3x lockout 5 menit).
  * **PIN Owner (Menu Admin S15):** `9999` (Uji cetak ESC/POS 58 mm, buka Wi-Fi, kunci/lepas mode kios LockTask, pasang APK update. Proteksi: salah 3x lockout 5 menit).
