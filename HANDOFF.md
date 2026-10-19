# HANDOFF: Sesi Pengendalian Kas Penuh, Koreksi & Tutup Shift (F35, F36, F37, F38)

## 1. Tujuan Sesi yang Telah Selesai
Mengimplementasikan 4 fitur utama siklus pengendalian kas, koreksi transaksi, dan penutupan shift pada Milestone M3:
1. **F35 (Void, Refund Tunai, dan Diskon Manual dengan PIN Manager Lokal):**
   - Catatan pembalik append-only di Room (`CorrectionEntity` & `CorrectionDao`, Room v5 `MIGRATION_4_5`).
   - Penegakan COR-04: Refund selalu tunai (`refund_method: "cash"`), mengurangi kas laci shift berjalan (`ShiftCashInput.cashRefunds`).
   - Otorisasi PIN Manager lokal offline (PIN `"4444"`, lockout 3x salah terkunci 5 menit COR-07).
   - Event outbox atomik `correction.recorded` sesuai skema OpenAPI.
   - Format cetak struk refund termal 58 mm (`RefundReceiptFormatter`).
2. **F36 (Refund Transaksi Shift Sebelumnya dengan Batas Nilai dan Umur):**
   - Penegakan batas COR-05 & OQ-22 di tablet: transaksi shift lampau dengan nilai $\le \text{Rp } 100.000$ dan umur $\le 14$ hari disetujui di tablet (`isOldTransactionRefund = true`).
   - Transaksi $> \text{Rp } 100.000$ atau $> 14$ hari ditolak di tablet dengan arahan ke Owner Web Dashboard.
3. **F37 (Kas Keluar Operasional dengan Foto Nota dan Batas tanpa PIN):**
   - Entitas Room `CashOutEntity` & `CashOutDao` (Room v6 `MIGRATION_5_6`).
   - Penegakan ambang batas KAS-01, KAS-02 & OQ-08: nominal $\le \text{Rp } 50.000$ bebas PIN; nominal $> \text{Rp } 50.000$ wajib otorisasi PIN Manager (S14) dan wajib foto nota via kamera tablet.
   - Mengurangi kas laci shift berjalan (`cashOuts` pada rumus K1 di `ShiftRepository`).
   - Event outbox atomik `cashout.recorded`.
   - Layar S12 Kas Keluar (`CashOutScreen` & `CashOutViewModel`) dan format struk bukti kas keluar 58 mm (`CashOutReceiptFormatter`).
4. **F38 (Tutup Shift Offline dan Tutup Paksa dari Tablet):**
   - Banner kepatuhan offline TUP-08 (*"Menunggu sinkron, jangan reset tablet"*) pada `CloseShiftScreen` (S11).
   - Dialog tutup paksa `ForceCloseShiftDialog` di `:feature:shift`: catatan audit wajib (`note`), otorisasi PIN Manager, dan eksekusi atomik `OrderTransactionManager.forceCloseShift`.
   - Status shift diperbarui ke `"force_closed"`, seketika membebaskan tablet (`getActiveShift() == null`) sehingga kasir baru dapat membuka shift berikutnya tanpa konflik.
   - Event outbox atomik `shift.force_closed` sesuai skema OpenAPI.

Progress M3 saat ini: **9 dari 11 fitur tuntas (81,8%)**. Total proyek: **38 dari 44 fitur tuntas (86,4%)**. Seluruh unit test lulus 100% dan kedua APK debug sukses dibangun.

---

## 2. Yang Sudah Selesai di Sesi Ini

### A. Modul `:core:data` (Database Room v6 & Outbox Atomik)
- **Room Database v5 & v6:**
  - `MIGRATION_4_5`: Penambahan tabel `corrections` (`CorrectionEntity`) dengan indeks `orderId` dan `shiftId`.
  - `MIGRATION_5_6`: Penambahan tabel `cash_outs` (`CashOutEntity`) dengan indeks `shiftId`.
  - Transaksi Room `OrderDao.observeCompletedOrdersWithLines` mencakup status `'paid'`, `'refunded'`, `'void'`, dan `'partial_refund'`.
- **OrderTransactionManager:**
  - `recordCorrection(...)`: Transaksi atomik koreksi (refund/void), penegakan aturan batas F36, update status order, dan outbox `correction.recorded`.
  - `recordCashOut(...)`: Transaksi atomik kas keluar, penegakan ambang batas Rp 50.000 & foto nota, dan outbox `cashout.recorded`.
  - `forceCloseShift(...)`: Transaksi atomik tutup paksa shift oleh manager dan outbox `shift.force_closed`.

### B. Modul `:feature:shift` (Penyempurnaan Penuh Rumus Kas K1 & Layar)
- **Rumus Kas Seharusnya ($K_1$) 100% Lengkap:**
  - `DefaultShiftRepository.getShiftSummary(shiftId)` secara dinamis menghubungkan refund riil dan kas keluar riil:
    $$\text{Kas Seharusnya } (K_1) = \text{Kas Awal} + \text{Penjualan Tunai} + \text{DP Tunai} - \text{Refund Tunai} - \text{Kas Keluar}$$
- **Layar S12 Kas Keluar (`CashOutScreen` & `CashOutViewModel`):**
  - Layout split 50/50 tablet landscape 11" mengikuti prinsip Quiet Luxury dan antislop UI.
  - Display nominal besar `A3MoneyText`, preset cepat (+10k, +20k, +50k, +100k), keypad numerik 3x4 taktil (`A3Keypad`), segmented chips kategori (*Bahan baku*, *Operasional*, *Perawatan*, *Kemasan*, *Lain-lain*), alasan detail (`A3TextField`), dan penangkap foto nota fisik via kamera tablet.
  - Status ambang batas interaktif dan integrasi dialog otorisasi PIN Manager (S14).
- **Banner Kepatuhan Offline (Aturan TUP-08):**
  - Pada `CloseShiftScreen` (Tahap 3 S11): Banner peringatan oranye tegas mencegah reset tablet sebelum sinkron.
- **Dialog Tutup Paksa (`ForceCloseShiftDialog`):**
  - Input catatan audit alasan dan verifikasi PIN Manager lokal.

### C. Modul `:core:printer` (Format Struk Thermal ESC/POS 58 mm)
- **`RefundReceiptFormatter` & `RefundReceiptData`:**
  - Struk pengembalian dana tunai / pembatalan void 58 mm memuat alasan audit, nominal dana keluar tunai, approved by manager, tanda `[REFUND TRANSAKSI SHIFT LALU]`, dan watermark `*** SALINAN ***` saat dicetak ulang.
- **`CashOutReceiptFormatter` & `CashOutReceiptData`:**
  - Struk bukti kas keluar operasional 58 mm memuat kategori, alasan, nama kasir, approved by manager, status foto bukti nota, dan total dana keluar tunai.

### D. Modul `:core:ui` (Komponen Dialog S14 Otorisasi PIN Manager)
- **`ManagerPinApprovalDialog` (`S14`):**
  - Dialog terpusat 2-kolom: kartu display nominal, banner informatif transaksi lampau, 6 pilihan alasan audit tetap (`wrong_input`, `customer_cancelled`, `quality_complaint`, `wrong_made`, `promo`, `other`), input catatan, 4 digit indikator PIN, keypad taktil 3x4 (`A3Keypad`), dan proteksi lockout 3x salah terkunci 5 menit (COR-07).

### E. Navigasi & Integrasi Antarmuka (`feature:cafe`, `feature:laundry`, `app-cafe`, `app-laundry`)
- **POS Cafe (`CafeOpenOrdersScreen` Tab Riwayat Selesai):**
  - Kartu riwayat reaktif (Lunas, Refund, Void, Refund Sebagian) dan badge "Shift Lalu".
  - Panel detail menyediakan tombol "Cetak Salinan", "Void", dan "Refund Tunai" dengan dialog penolakan batas F36 dan dialog S14.
- **POS Laundry (`LaundryOrdersScreen` & `LaundryOrdersViewModel`):**
  - Penambahan aksi `PendingManagerAction.Refund`.
- **Kartu Shift Aktif (`ActiveShiftCard`):**
  - Tombol aksi *"Catat Kas Keluar"* (Primary) dan link *"Tutup Paksa Shift (Manager)"* terhubung di kedua aplikasi.

---

## 3. Yang Belum Selesai (Langkah untuk Sesi Baru: Lanjutan Milestone M3)

Sisa 2 fitur Milestone M3:
1. **F39 (Mode Kios dan Pembaruan App):**
   - Integrasi LockTask (Screen Pinning) dan pemblokiran update saat shift aktif atau outbox belum kosong (OPS-03).
2. **F40 (Migrasi Room Diuji dengan Data Lama):**
   - Unit test migrasi Room v1 $\rightarrow$ v2 $\rightarrow$ v3 $\rightarrow$ v4 $\rightarrow$ v5 $\rightarrow$ v6 memastikan tidak ada kehilangan data transaksi, shift, koreksi, petty cash, atau outbox.

---

## 4. Keputusan Teknis Penting

- **Dual Database Migration (v4 $\rightarrow$ v5 $\rightarrow$ v6):**
  - v5 menambahkan tabel `corrections` (`CorrectionEntity`).
  - v6 menambahkan tabel `cash_outs` (`CashOutEntity`).
- **COR-04, COR-05 & OQ-22 Penegakan Batas Refund:**
  - Refund selalu tunai mengurangi laci hari berjalan. Transaksi lampau dibatasi $\le \text{Rp } 100.000$ dan $\le 14$ hari di tablet.
- **KAS-01, KAS-02 & OQ-08 Penegakan Batas Kas Keluar:**
  - Batas kas keluar tanpa PIN: Rp 50.000. Di atas Rp 50.000 wajib otorisasi PIN Manager dan foto nota.
- **Immediate Tablet Unlocking pada Tutup Paksa:**
  - Status `force_closed` membebaskan tablet seketika sehingga kasir berikutnya dapat membuka shift baru tanpa terhalang SHF-03.
- **TUP-08 Offline Warning:**
  - Banner peringatan offline ditampilkan visual pada layar penutupan kasir untuk mencegah reset tablet yang menghilangkan data outbox.

---

## 5. File yang Perlu Dibaca Pertama Kali di Sesi Baru
- `HANDOFF.md` (dokumen ini)
- `feature_list.json` (baris 380–405 untuk sisa fitur M3)
- `docs/milestones/M3-shift-kas.md`
- `docs/spec/06-kebutuhan-fungsional-d-tutup-shift-sync-.md` (bagian mode kios & update)
