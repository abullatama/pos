# HANDOFF: Sesi Buka/Tutup Shift Terpadu (F30–F34) & Penyempurnaan POS Laundry

## 1. Tujuan Sesi yang Telah Selesai
1. Menyempurnakan alur operasional dan sistem desain POS Laundry (Layar Beranda L01, generator nomor urut harian otomatis, identitas toko dinamis struk, pembersihan label UI, dan alur pembayaran DP).
2. Mengimplementasikan fitur siklus shift dan pengendalian kas Milestone M3:
   - **F30:** Buka shift dengan kas awal per pecahan uang (Layar S04, Room `ShiftEntity` & `ShiftDao`, event `shift.opened`, validasi standar Rp200.000).
   - **F31:** Tutup shift dengan hitung kas buta (S09), evaluasi selisih kas terhadap rumus K1 (S10), alasan selisih jika melebihi batas toleransi Rp2.000 (OQ-07), dan format cetak Laporan Z ESC/POS 58 mm (`ZReportFormatter`).
   - **F32:** Catatan alokasi kas saat tutup shift (laci + setor ke owner) dengan validasi keseimbangan alokasi (TUP-06) dan pencatatan penerima uang (`cash_taken_by`).
   - **F33:** Serah terima pesanan terbuka ke shift berikutnya (TUP-01: ID pesanan terbuka dibawa di `handover_order_ids` tanpa dibatalkan; pembayaran dicatat di shift penerima).
   - **F34:** Ceklis verifikasi transaksi QRIS Statis saat tutup shift (TUP-04: daftar ID pembayaran QRIS dibawa di array `qris_checked_payment_ids` pada event `shift.closed`).

Progress M3 saat ini: **5 dari 11 fitur tuntas (45,5%)**. Total proyek: **34 dari 44 fitur tuntas (77,3%)**. Seluruh unit test lulus 100% dan kedua APK debug sukses dibangun.

---

## 2. Yang Sudah Selesai di Sesi Ini

### A. Penyempurnaan POS Laundry
- **Layar Beranda L01 (`LaundryHomeScreen` & `LaundryHomeViewModel`):**
  - Menggantikan placeholder kosong dengan dashboard metriks reaktif harian: *Order Hari Ini*, *Siap Diambil*, *Belum Lunas*.
  - Dua tombol pintasan aksi cepat: *Order Baru* (Velvet Rose) dan *Scan Tag Cucian*.
  - Daftar reaktif cucian siap diambil dengan penyerahan cepat ke detail pesanan.
- **Generator Nomor Tampil Otomatis:**
  - Menambahkan query `countOrdersForPrefixAndDate` di `OrderDao` untuk menghitung urutan harian per prefix (`L1-` dan `C1-`).
  - Menghapus string hardcoded `"L1-0001"`. Nomor order baru kini otomatis bertambah secara dinamis (`L1-0001`, `L1-0002`, dst).
- **Identitas Toko Dinamis pada Struk:**
  - Mengabstraksi identitas toko pada `PaymentViewModel` dan `CustomerReceiptData`.
  - Struk Laundry mencetak nama resmi **ATHIA LAUNDRY HOUSE**, alamat **Jl. Trans Sulawesi, Bintauna**, dan kontak **+62 823-4778-5986** (OQ-25), bukan AA Coffeeshop.
- **Penyempurnaan Alur DP (Down Payment):**
  - Menambahkan pilihan nominal DP (Rp 10.000, Rp 20.000, 50%) pada saat opsi *Uang Muka (DP)* dipilih di layar order baru.
  - Mengirim nominal DP aktual dan flag `isDp = true` ke `PaymentViewModel`, sehingga status order di Room tersimpan sebagai `dp` (bukan langsung `paid`) dan sisa tagihan terhitung akurat.
- **Pembersihan Teks Antarmuka Pengguna:**
  - Menghapus seluruh kebocoran kode dokumen teknis spek dari UI kasir (seperti `(L05)`, `(L08)`, `(F22)`, `(F23)`, dan `(F24)`).

### B. Milestone M3: Buka Shift (F30)
- **Database Room (`AppDatabase` v3 & `MIGRATION_2_3`):**
  - Entitas `ShiftEntity` dan `ShiftDao` di `:core:data`.
  - Transaksi atomik `OrderTransactionManager.openShift(...)` menyimpan shift ke Room dan mencatat event outbox `shift.opened` membawa `opening_cash_breakdown`.
  - Penegakan aturan **SHF-03**: Menolak membuka shift baru jika masih ada shift berstatus `open` pada tablet.
- **Model Pecahan & Layar S04 Buka Shift (`OpenShiftScreen` & `ShiftViewModel`):**
  - Model `CashDenomination` dan `StandardDenominations` dengan seluruh pecahan resmi Indonesia (Rp 100k s/d Rp 100 koin) serta preset bawaan Rp 200.000 (OQ-06).
  - Layout split-screen 60/40 dengan stepper taktil 48dp, tombol pintasan *"Isi Standar (Rp 200.000)"*, dan validasi alasan selisih jika berbeda dari standar (SHF-04).
  - Indikator shift di TopBar membaca status riil dari Room: menampilkan nama kasir & nominal kas awal jika aktif, atau status *"Belum Buka Shift"*.

### C. Milestone M3: Tutup Shift Terpadu (F31, F32, F33, F34)
- **Pencatatan Pembayaran Per Shift (`PaymentEntity`, `PaymentDao`, `AppDatabase` v4 & `MIGRATION_3_4`):**
  - Tabel `payments` merekam `shiftId` yang menerima pembayaran kasir secara append-only.
  - Query otomatis `getCashSalesForShift`, `getCashDpForShift`, `getQrisTotalForShift`, dan `getQrisPaymentsForShift`.
- **Format Cetak Laporan Z ESC/POS 58 mm (`ZReportFormatter` di `:core:printer`):**
  - Lebar 32 kolom kertas roll termal 58 mm untuk iWare C58BT.
  - Memuat ringkasan penjualan, rincian per metode bayar, pengendalian kas fisik laci (K1), selisih kas, alokasi kas laci/setor, dan watermark tegas `*** SALINAN LAPORAN Z ***` (PRT-05).
- **Layar CloseShiftScreen Wizard 3-Tahap di `:feature:shift`:**
  - **Tahap 1 (S09 Hitung Kas Buta / TUP-02):** Tabel stepper taktil 48dp pecahan uang fisik dengan angka sistem disembunyikan 100%.
  - **Tahap 2 (S10 Selisih K1 & Ceklis QRIS / TUP-03, TUP-04):** Pola 60/40 tablet: daftar transaksi QRIS Statis shift ini dengan checklist *"Sudah Dicek"* dan perbandingan Kas Seharusnya (K1) vs Dihitung, toleransi selisih Rp 2.000 (OQ-07), dan alasan wajib jika selisih > Rp 2.000.
  - **Tahap 3 (S11 Alokasi Kas & Laporan Z / TUP-05, TUP-06):** Input uang ditinggal di laci (default Rp 200.000) dan disetor ke owner, validasi keseimbangan alokasi seimbang realtime, input penerima uang (`cash_taken_by`), dan pratinjau Laporan Z.
- **Transaksi Atomik `OrderTransactionManager.closeShift(...)`:**
  - Mengubah status `ShiftEntity` menjadi `"closed"`.
  - Menyusun dan memasukkan event outbox `shift.closed` membawa `counted_cash`, `difference_reason`, `cash_left`, `cash_deposited`, `cash_taken_by`, `qris_checked_payment_ids`, dan `handover_order_ids`.

---

## 3. Yang Belum Selesai (Langkah untuk Sesi Baru: Lanjutan Milestone M3)

Sisa 6 fitur Milestone M3:
1. **F35 (Void, Refund Tunai, dan Diskon Manual PIN Manager Lokal):**
   - Layar S13 Riwayat Transaksi & Dialog S14 PIN Manager.
   - Event outbox `correction.recorded` (tipe `refund` atau `void`, alasan dari daftar audit tetap, `approved_by` PIN Manager). Mengurangi kas seharusnya di shift berjalan (COR-04).
2. **F36 (Refund Transaksi Shift Sebelumnya dengan Batas Nilai dan Umur):**
   - Refund atas komplain transaksi lama: manager di tablet jika $\le \text{Rp } 100.000$ dan umur $\le 14$ hari (OQ-22); di atas itu diarahkan ke owner web (COR-05).
3. **F37 (Kas Keluar dengan Foto Nota dan Batas tanpa PIN):**
   - Layar S12 Kas Keluar: input nominal, alasan, kategori (Bahan baku, Operasional, dll), foto nota via kamera tablet. Batas tanpa PIN = Rp 50.000 (OQ-08); di atas itu wajib PIN Manager (KAS-01, KAS-02, event `cashout.recorded`).
4. **F38 (Tutup Shift Offline dan Tutup Paksa dari Tablet):**
   - Tutup shift offline dengan banner peringatan data menunggu sinkron (TUP-08).
   - Tutup paksa shift kasir yang tidak hadir oleh Manager (`shift.force_closed` membawa `note` dan `approved_by`).
5. **F39 (Mode Kios dan Pembaruan App):**
   - Integrasi LockTask (Screen Pinning) dan pemblokiran update saat shift aktif atau outbox belum kosong (OPS-03).
6. **F40 (Migrasi Room Diuji dengan Data Lama):**
   - Unit test migrasi Room v1 $\rightarrow$ v2 $\rightarrow$ v3 $\rightarrow$ v4 memastikan tidak ada kehilangan data transaksi atau outbox.

---

## 4. Keputusan Teknis Penting

- **Dual Database Version Migration (v2 $\rightarrow$ v3 $\rightarrow$ v4):**
  - v3 menambahkan tabel `shifts` (`ShiftEntity`).
  - v4 menambahkan tabel `payments` (`PaymentEntity`) dengan indeks `orderId`, `shiftId`, dan `idempotencyKey` unik.
- **Kepatuhan Test Vector K1 & Toleransi OQ-07:**
  - Perhitungan kas seharusnya saat tutup shift patuh rumus: $\text{Kas Awal} + \text{Penjualan Tunai} + \text{DP/Lunas Tunai} - \text{Refund Tunai} - \text{Kas Keluar}$.
  - Toleransi selisih kas $=\text{Rp } 2.000$. Selisih di atas itu mewajibkan kasir memasukkan alasan audit sebelum dapat melanjutkan wizard penutupan.
- **TUP-06 Validasi Keseimbangan Kas:**
  - `cashLeft + cashDeposited == countedCash` ditegakkan di level UI (`CloseShiftScreen`) dan level database transaksi (`OrderTransactionManager.closeShift`).
  - Jika kas disetor $> 0$, nama penerima uang (`cashTakenBy`) wajib diisi.

---

## 5. File yang Perlu Dibaca Pertama Kali di Sesi Baru
- `HANDOFF.md` (dokumen ini)
- `feature_list.json` (baris 340–405 untuk sisa fitur M3)
- `docs/milestones/M3-shift-kas.md`
- `docs/spec/05-kebutuhan-fungsional-c-pembayaran-struk-.md` (bagian koreksi & kas keluar)
- `docs/spec/06-kebutuhan-fungsional-d-tutup-shift-sync-.md`
