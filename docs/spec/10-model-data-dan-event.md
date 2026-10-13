## 10. Model data dan event

Data transaksi bersifat append-only dan diidentifikasi UUID yang dibuat di tablet, sedangkan master data dikelola server. Tabel di bawah memakai tipe generik; pembuat backend bebas menyesuaikan nama dan tipe dengan database yang dipilih selama aturan integritasnya tetap.

**Konvensi**

1. Uang: bilangan bulat rupiah (`bigint`). Waktu: timestamp UTC. Teks: `text`.
2. ID transaksi adalah UUID dari tablet. ID master dibuat server.
3. Setiap record transaksi membawa jam tablet (`created_at`) dan, setelah diterima, jam server (`received_at`).
4. Master data memakai `updated_at`, `deleted_at` (soft delete), dan `change_seq` (urutan naik monoton per tabel) sebagai dasar cursor delta, karena `updated_at` saja bisa melewatkan baris dengan waktu yang sama.
5. Koreksi berupa record baru yang menunjuk ke record asal. Tidak ada update atau delete pada transaksi.
6. Setiap tabel yang terkait usaha membawa `business_unit_id`.

**Master, akun, dan konfigurasi**

| Tabel | Kolom penting | Kunci dan aturan |
| --- | --- | --- |
| `business_units` | id, name, type (cafe atau laundry), timezone, opening\_hours | Dua baris pada MVP |
| `users` | id, name, role, pin\_hash, email, password\_hash, active | Unik email; peran: kasir, staf\_laundry, manager, owner |
| `user_business_units` | user\_id, business\_unit\_id | PK gabungan; usaha yang berizin per akun |
| `user_status_history` | user\_id, active\_from, active\_to | Dipakai memvalidasi approver pada `occurred_at` |
| `devices` | id, business\_unit\_id, code, name, status, credential\_hash, app\_version, last\_sync\_at, revoked\_at | Status: menunggu, aktif, dicabut; unik code |
| `registration_codes` | code\_hash, business\_unit\_id, expires\_at, used\_at | Sekali pakai, berlaku singkat |
| `menu_items` | id, business\_unit\_id, name, category, price, cost\_per\_serving, active, sold\_out, change\_seq | `cost_per_serving` hanya untuk laporan owner |
| `menu_options` | id, menu\_item\_id, kind (varian atau add-on), name, price\_delta, cost\_delta, active | Indeks menu\_item\_id |
| `laundry_services` | id, business\_unit\_id, name, unit (kg atau pcs), rate, express\_rate, min\_weight, weight\_step, default\_eta\_hours, cost\_per\_unit, active, change\_seq | Indeks (business\_unit\_id, change\_seq) |
| `customers` | id, name, phone, updated\_at, deleted\_at, change\_seq | Unik phone (dinormalisasi); data minimal; `wa_opt_out` (usulan) |
| `settings` | key, business\_unit\_id (boleh kosong), value, updated\_by, updated\_at | PK (key, business\_unit\_id); riwayat perubahan |
| `expense_categories` | id, name | Bahan baku, Gaji, Sewa, Listrik/Air/Internet, Kemasan, Perawatan, Lain-lain; kategori Biaya admin pembayaran diusulkan (bagian 19) |

**Transaksi dan operasional**

| Tabel | Kolom penting | Kunci dan aturan |
| --- | --- | --- |
| `shifts` | id (UUID), business\_unit\_id, device\_id, user\_id, opened\_at, closed\_at, opening\_cash, counted\_cash, expected\_cash, difference, difference\_reason, cash\_left, cash\_deposited, cash\_taken\_by, status, received\_at | Status: open, closed, force\_closed; satu shift open per perangkat |
| `orders` | id (UUID), display\_number, business\_unit\_id, shift\_id, device\_id, cashier\_id, order\_type, table\_label, customer\_id, status, subtotal, discount, service\_charge, tax, rounding, total, paid\_amount, created\_at, received\_at | Unik (device\_id, business\_date, display\_seq); `status` dan `paid_amount` diperbarui server dari event |
| `order_lines` | id, order\_id, item\_ref\_id, name\_snapshot, quantity, weight, unit\_price\_snapshot, cost\_snapshot, options\_snapshot, note | Tidak berubah setelah dibuat |
| `payments` | id (UUID), order\_id, method (cash atau qris\_static), amount, idempotency\_key, shift\_id, cashier\_id, paid\_at, received\_at | Unik idempotency\_key; tidak ada status karena pembayaran langsung final; shift\_id adalah shift yang menerima pembayaran, bukan shift pembuat order |
| `laundry_status_events` | id, order\_id, status, user\_id, occurred\_at, reason, received\_at | Append-only |
| `corrections` | id, type (void, refund, manual\_discount, comp, weight\_adjust, price\_adjust), order\_id, amount, reason\_code, approved\_by, shift\_id, occurred\_at | Append-only, menunjuk ke transaksi asal |
| `cash_outs` | id, shift\_id, amount, category, reason, receipt\_file\_id, approved\_by, occurred\_at | Mengurangi kas seharusnya |
| `expenses` | id, business\_unit\_id (kosong berarti Bersama), category\_id, amount, expense\_date, note, receipt\_file\_id, recurring\_id, created\_by | Dasar laba-rugi |
| `recurring_expenses` | id, business\_unit\_id, category\_id, amount, day\_of\_month, active, last\_generated\_period | Dibuat otomatis oleh penjadwal |
| `stock_counts` | id, business\_unit\_id, period, material\_name, value | Stok akhir bahan utama, manual bulanan |
| `reconciliations` | id, business\_unit\_id, date, method (qris\_static), app\_total, merchant\_total, difference, status, note | Unik (business\_unit\_id, date, method) |
| `notifications` | id, order\_id, kind (siap atau pengingat), to\_phone, status, attempts, last\_error, provider\_ref, created\_at, sent\_at | Log pengiriman WhatsApp; unik (order\_id, kind, nomor pengingat) |
| `sync_bundles` | bundle\_id (PK), device\_id, status, reason\_code, payload\_hash, received\_at | Catatan idempotency |
| `device_heartbeats` | device\_id, at, app\_version, battery, last\_sync\_at, outbox\_count, printer\_status | Untuk Status Perangkat |
| `files` | id, kind, storage\_key, uploaded\_by, created\_at | Foto nota |
| `finding_reviews` | kunci temuan, status, catatan, peninjau, waktu | Untuk laporan pengecualian |
| `audit_logs` | id, actor\_id, action, object\_type, object\_id, before, after, occurred\_at, device\_id, ip | Append-only dan tidak bisa diubah |
| laundry\_pickups | id (UUID), order\_id, order\_line\_id, quantity, is\_final, note, user\_id, approved\_by, picked\_up\_at, received\_at | Append-only; tidak terikat shift. Order berstatus Diambil bila setiap baris punya pengambilan is\_final |
| shift\_qris\_checks | shift\_id, payment\_id, checked\_by, checked\_at | Append-only; diisi dari event shift.closed (pengganti kolom qris\_checked pada payments) |
| shift\_handovers | from\_shift\_id, order\_id, to\_shift\_id (diisi saat pembayaran pertama diterima) | Pesanan cafe terbuka yang diserahterimakan saat tutup shift |
| sync\_flags | id, bundle\_id, event\_id, flag, detail, status (baru, ditinjau), reviewed\_by, reviewed\_at | Karantina: satu-satunya tabel di grup ini yang boleh diubah (status tinjauan). Flag: TOTAL\_MISMATCH, SHIFT\_RANGE, UNKNOWN\_ITEM, CASHOUT\_UNAPPROVED, CASH\_MISMATCH, CASH\_ALLOCATION, CLOCK\_SKEW, OLD\_EVENT |

**Aturan integritas**

- Kunci asing untuk semua relasi, dan penerimaan satu bundel berjalan dalam satu transaksi database.
- Akun database yang dipakai aplikasi tidak diberi hak ubah atau hapus pada tabel append-only (`audit_logs`, `corrections`, `laundry_status_events`, `cash_outs`, `order_lines`).
- Nomor tampil order unik per perangkat per hari (`business_date` mengikuti zona waktu usaha).
- Kolom modal (`cost_per_serving`, `cost_snapshot`, `cost_per_unit`) tidak ditampilkan di UI app maupun laporan Z. Alternatif yang lebih aman: server mengisi snapshot modal dari riwayat modal pada `occurred_at`, sehingga modal tidak perlu ada di tablet (pertanyaan di bagian 19).
- Indeks tambahan ditentukan setelah ada data nyata.

**Data lokal di tablet saja**

- Outbox event: UUID, tipe, isi, status (Menunggu, Terkirim, Bermasalah), jumlah percobaan, alasan gagal.
- Antrean cetak: jenis cetakan, status (Menunggu, Berhasil, Gagal), referensi.
- Draft keranjang dan draft hitung kas, serta cursor delta sync.

**Tipe event ke server**

`shift.opened`, `shift.closed`, `order.created`, `order.sent_to_kitchen`, `order.items_added`, `payment.recorded`, `laundry.status_changed`, `correction.recorded`, `cashout.recorded`.

**Tambahan v2.2.** Event baru: `customer.created`, `menu.sold_out_changed` (tanda Habis dari tablet; yang terakhir menang berdasarkan `occurred_at`), `order.table_moved`, `laundry.picked_up`, dan `shift.force_closed` (hanya Manager, bisa offline). Event `shift.closed` membawa `counted_cash`, `cash_left`, `cash_deposited`, `cash_taken_by`, `qris_checked_payment_ids[]`, dan `handover_order_ids[]`. Event `laundry.*` dan `customer.created` tidak terikat shift (kolom `shift_id` kosong), sehingga staf laundry bisa memindai sebelum kasir membuka shift dan sesi staf tidak menutup shift.

**Retensi, data awal (seed), dan migrasi (v2.2)**

| Data | Retensi | Catatan |
| --- | --- | --- |
| Event sudah sinkron di tablet | 30 hari (Pengaturan) | Status Menunggu dan Bermasalah tidak pernah dihapus |
| `sync_bundles` di server | Minimal 365 hari | Harus lebih lama daripada retensi tablet agar kiriman ulang tetap dikenali `DUPLICATE` |
| Soft delete master data | Minimal 30 hari | Cursor lebih tua dijawab 410 dan tablet bootstrap ulang; outbox selalu dikirim lebih dulu |
| Transaksi, koreksi, audit log | Tidak dihapus | Bila perlu diarsipkan, bukan dihapus |
| Foto nota | Sama dengan transaksi terkait |  |

- **Seed:** berkas `seed/` di repositori memuat contoh menu, layanan laundry, preset diskon, dan akun uji, hanya untuk lingkungan pengembangan dan uji. Data produksi (menu, tarif, akun) diisi owner lewat web. Skrip seed idempoten dan tidak pernah berjalan di produksi.
- **Migrasi server:** skema berubah lewat migrasi berversi yang berjalan otomatis saat deploy dan diuji di staging dengan salinan data. Skema event versi lama tetap didukung sampai semua tablet memenuhi `min_app_version`.
- **Migrasi Room:** migrasi eksplisit untuk setiap versi, diuji dengan database versi lama yang berisi outbox. Migrasi destruktif dilarang.
