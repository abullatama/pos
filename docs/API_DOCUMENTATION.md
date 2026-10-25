# Dokumentasi Lengkap API Kasir (Laundry & Cafe)

> **Versi API**: v1 (Android: v0.3.0, Web Admin: v0.1.0)  
> **Terakhir Diperbarui**: Oktober 2026  
> **Backend Framework**: Laravel 12 (PHP 8.2+)  
> **Database Engine**: PostgreSQL 16+ (Zona Waktu: UTC)  
> **Lisensi**: Proprietary  

Dokumentasi ini mencakup spesifikasi teknis, alur integrasi, kontrak endpoint, skema payload, dan aturan domain untuk seluruh API pada sistem **Kasir Laundry & Cafe**.

---

## DAFTAR ISI

1. [Prinsip Arsitektur & Aturan Baku Sistem](#1-prinsip-arsitektur--aturan-baku-sistem)
2. [Standar Komunikasi & Format Error](#2-standar-komunikasi--format-error)
3. [API Tablet Android (POS Offline-First)](#3-api-tablet-android-pos-offline-first)
   - [3.1 Konvensi Header & Keamanan](#31-konvensi-header--keamanan)
   - [3.2 Alur Siklus Hidup Perangkat](#32-alur-siklus-hidup-perangkat)
   - [3.3 Autentikasi Kasir & Verifier PIN Offline](#33-autentikasi-kasir--verifier-pin-offline)
   - [3.4 Sinkronisasi Master Data (Bootstrap & Delta Changes)](#34-sinkronisasi-master-data-bootstrap--delta-changes)
   - [3.5 Shift Kasir & Tutup Paksa](#35-shift-kasir--tutup-paksa)
   - [3.6 Mesin Sinkronisasi Bundel Outbox (`POST /v1/sync/bundles`)](#36-mesin-sinkronisasi-bundel-outbox-post-v1syncbundles)
   - [3.7 Rincian Skema Event Bundel](#37-rincian-skema-event-bundel)
   - [3.8 Endpoint Pendukung Tablet (Pelanggan, File, Notifikasi)](#38-endpoint-pendukung-tablet-pelanggan-file-notifikasi)
4. [API Web Owner & Manager (Real-time Backoffice)](#4-api-web-owner--manager-real-time-backoffice)
   - [4.1 Konvensi Autentikasi, Sesi, dan CSRF](#41-konvensi-autentikasi-sesi-dan-csrf)
   - [4.2 Autentikasi, TOTP MFA, dan Reset Password](#42-autentikasi-totp-mfa-dan-reset-password)
   - [4.3 Dashboard & Pemantauan Sistem](#43-dashboard--pemantauan-sistem)
   - [4.4 Manajemen Perangkat & Antrean Masalah](#44-manajemen-perangkat--antrean-masalah)
   - [4.5 Manajemen Pengguna & Reset PIN](#45-manajemen-pengguna--reset-pin)
   - [4.6 Pengaturan & Master Data](#46-pengaturan--master-data)
   - [4.7 Shift, Laporan Z Server, dan Transaksi](#47-shift-laporan-z-server-dan-transaksi)
   - [4.8 Modul Keuangan & Inventaris (Khusus Owner)](#48-modul-keuangan--inventaris-khusus-owner)
   - [4.9 Laporan Bisnis, Laba Rugi, & Deteksi 13 Anomali](#49-laporan-bisnis-laba-rugi--deteksi-13-anomali)
5. [Endpoint Operasional & Kesiapan Server (`/health` & `/ready`)](#5-endpoint-operasional--kesiapan-server-health--ready)
6. [Tugas Terjadwal & Otomasi WhatsApp](#6-tugas-terjadwal--otomasi-whatsapp)
7. [Spesifikasi Rumus Domain Finansial](#7-spesifikasi-rumus-domain-finansial)

---

## 1. Prinsip Arsitektur & Aturan Baku Sistem

1. **Uang Selalu Bilangan Bulat Rupiah (`bigint`)**:
   - Tidak pernah menggunakan tipe data `float` atau `double`. Seluruh nilai mata uang direpresentasikan dalam Rupiah penuh (contoh: Rp15.000 ditulis integer `15000`).
2. **Waktu Disimpan UTC (`timestamptz`)**:
   - Seluruh stempel waktu input dikonversi dan disimpan dalam UTC. Server selalu mencatat waktu penerimaan payload fisik (`received_at`).
3. **Data Transaksi Append-Only**:
   - Tabel transaksi inti (`order_lines`, `corrections`, `laundry_status_events`, `laundry_pickups`, `cash_outs`, `shift_adjustments`, `audit_logs`) dilindungi PostgreSQL Trigger `prevent_modify()`.
   - Tidak ada operasi `UPDATE` atau `DELETE` pada data transaksi. Koreksi dicatat sebagai baris mutasi baru.
4. **Zero-Trust Hitungan Tablet**:
   - Server tidak mempercayai angka total yang dikirimkan oleh tablet Android. Seluruh subtotal, diskon preset, biaya layanan, pajak, pembulatan, dan tarif gramasi laundry dihitung ulang secara ketat di lapisan `app/Domain` dengan **toleransi selisih Rp0**.
5. **Idempotensi Berbasis UUID Tablet**:
   - Klien tablet wajib membuat UUID unik untuk `bundle_id`, `event_id`, `order_id`, `line_id`, `payment_id`, `cashout_id`, dan `correction_id`.
   - Operasi pengiriman ulang bundel yang sama akan menghasilkan status `DUPLICATE` tanpa memproses ulang transaksi.
6. **Isolasi Mutasi Bersamaan**:
   - Pemrosesan bundel sinkronisasi dilindungi oleh PostgreSQL Transactional Advisory Locks: `pg_advisory_xact_lock(hashtext(bundle_id))`.
7. **Filter Kepemilikan Data Otomatis**:
   - Query data selalu difilter berdasarkan identitas `business_unit_id` dari token otorisasi perangkat atau sesi login web, bukan dari parameter masukan pengguna.

---

## 2. Standar Komunikasi & Format Error

### Format Respons Sukses
Setiap respons sukses berstatus 200/201/202 (kecuali 204 No Content) menyertakan waktu server dalam format ISO 8601 UTC:
```json
{
  "data": { ... },
  "server_time": "2026-10-20T10:15:30Z"
}
```

### Format Respons Error
Seluruh error HTTP (4xx dan 5xx) menghasilkan skema JSON terpadu:
```json
{
  "code": "VALIDATION_FAILED",
  "message": "Data tidak valid.",
  "details": {
    "errors": {
      "name": ["Field name wajib diisi."]
    }
  },
  "request_id": "req-uuid-12345"
}
```

### Kamus Kode Error Server
| Kode Error | HTTP Status | Keterangan |
|---|---|---|
| `UNAUTHENTICATED` | 401 | Kredensial tidak ada, token JWT kedaluwarsa, atau sesi habis. |
| `PIN_INVALID` | 401 | PIN kasir salah. |
| `INVALID_CREDENTIALS`| 401 | Email atau password login web salah. |
| `FORBIDDEN` | 403 | Peran (*role*) pengguna tidak memiliki hak akses ke resource/unit usaha ini. |
| `DEVICE_REVOKED` | 403 | Akses perangkat tablet telah dicabut oleh Owner. |
| `NOT_FOUND` | 404 | Data tidak ditemukan atau milik unit usaha lain. |
| `DEVICE_ALREADY_REGISTERED` | 409 | `device_id` sudah terdaftar dalam sistem. |
| `INVALID_STATE` | 409 | Status entitas saat ini tidak memungkinkan aksi tersebut. |
| `CURSOR_EXPIRED` | 410 | Kursor sinkronisasi berumur $>30$ hari (wajib bootstrap ulang). |
| `CSRF_MISMATCH` | 419 | Token proteksi CSRF web tidak cocok / kedaluwarsa. |
| `CODE_INVALID` | 422 | Kode registrasi perangkat tidak valid atau sudah kedaluwarsa. |
| `VALIDATION_FAILED`| 422 | Struktur masukan tidak memenuhi aturan validasi. |
| `ACCOUNT_LOCKED` | 423 | Akun kasir terkunci karena salah PIN 3x berturut-turut (`retry_after` detik di `details`). |
| `UPGRADE_REQUIRED` | 426 | Versi aplikasi Android berada di bawah versi minimum server (`min_app_version`). |
| `RATE_LIMITED` | 429 | Batas laju pemanggilan API terlampaui. |
| `PAYLOAD_TOO_LARGE`| 413 | Ukuran bundel atau berkas melebihi batas yang diizinkan. |

---

## 3. API Tablet Android (POS Offline-First)

**URL Dasar**: `https://api.kasir.domain/v1` (Lokal: `http://localhost:8000/v1`)  
**Kontrak Spesifikasi**: `openapi/android/openapi.yaml`

### 3.1 Konvensi Header & Keamanan

Setiap panggilan API dari tablet Android wajib mengirimkan header berikut sesuai tingkat otentikasinya:

| Header | Wajib Pada | Keterangan |
|---|---|---|
| `X-Device-Credential` | Semua endpoint pasca-aktivasi | Kredensial rahasia perangkat (didapat saat approval). |
| `Authorization` | Endpoint operasional kasir | Token Bearer JWT: `Bearer <access_token>`. |
| `X-App-Version` / `X-AppVersion` | Endpoint bisnis & otentikasi | Nomor versi aplikasi Android (misal: `1.2.0`). |
| `X-Request-Id` | Dianjurkan pada setiap request | UUID pelacakan unik dari tablet (dikembalikan jika terjadi error). |
| `Accept` | Semua | Wajib bernilai `application/json`. |

---

### 3.2 Alur Siklus Hidup Perangkat

#### A. Registrasi Perangkat
Mendaftarkan perangkat tablet baru menggunakan kode pendaftaran sekali pakai dari Web Owner.

- **Endpoint**: `POST /v1/devices/register`
- **Autentikasi**: Publik (Dilindungi Rate Limiter `device-register`)
- **Request Body**:
```json
{
  "registration_code": "REG-839201",
  "device_id": "dev-c1",
  "name": "Tablet Kasir Utama Cafe"
}
```
- **Respons (202 Accepted)**:
```json
{
  "device_id": "dev-c1",
  "name": "Tablet Kasir Utama Cafe",
  "code": "C1",
  "status": "PENDING",
  "server_time": "2026-10-20T10:00:00Z"
}
```

#### B. Pengecekan Status & Pengambilan Kredensial Perangkat
Digunakan untuk polling status persetujuan atau mengambil kredensial perangkat saat pertama kali disetujui.

- **Endpoint**: `GET /v1/devices/me`
- **Autentikasi**: 
  - Saat belum punya kredensial: Header `X-Device-Id` + `X-Registration-Code`.
  - Saat sudah aktif: Header `X-Device-Credential`.
- **Respons Saat Sudah Disetujui (200 OK)**:
```json
{
  "device_id": "dev-c1",
  "name": "Tablet Kasir Utama Cafe",
  "code": "C1",
  "business_unit_id": 1,
  "status": "ACTIVE",
  "device_credential": "sec_dev_98f4e2... (hanya dikirim sekali saat aktivasi)",
  "server_time": "2026-10-20T10:05:00Z"
}
```

#### C. Heartbeat Perangkat
Mengirimkan status periodik tablet ke server agar Owner dapat memantau kesehatan operasional.

- **Endpoint**: `POST /v1/devices/heartbeat`
- **Autentikasi**: Header `X-Device-Credential` (Device Status `ACTIVE`)
- **Request Body**:
```json
{
  "app_version": "1.2.0",
  "outbox_pending": 0,
  "outbox_problem": 0,
  "battery_percent": 85,
  "last_sync_at": "2026-10-20T09:58:00Z",
  "printer_status": "ok"
}
```
*Nilai printer_status yang dianjurkan: `ok`, `offline`, `paper_out`, `error`, `unknown`.*
- **Respons**: `204 No Content`

---

### 3.3 Autentikasi Kasir & Verifier PIN Offline

#### A. Daftar Akun Kasir Unit Usaha
Menampilkan daftar akun staf yang diizinkan bertugas pada unit usaha tablet ini (untuk layar pemilihan profil di tablet).

- **Endpoint**: `GET /v1/accounts`
- **Autentikasi**: Header `X-Device-Credential` + `X-App-Version`
- **Respons (200 OK)**:
```json
{
  "accounts": [
    {
      "id": 3,
      "name": "Budi Santoso",
      "role": "kasir"
    },
    {
      "id": 2,
      "name": "Siti Aminah",
      "role": "manager"
    }
  ],
  "server_time": "2026-10-20T10:06:00Z"
}
```

#### B. Login PIN Kasir
- **Endpoint**: `POST /v1/auth/pin-login`
- **Autentikasi**: Header `X-Device-Credential` + `X-App-Version`
- **Request Body**:
```json
{
  "user_id": 3,
  "pin": "1234"
}
```
- **Respons (200 OK)**:
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "ref_90a1f4...",
  "expires_in": 900,
  "user": {
    "id": 3,
    "name": "Budi Santoso",
    "role": "kasir"
  },
  "server_time": "2026-10-20T10:07:00Z"
}
```

#### C. Perpanjangan Token (Refresh Token)
Menukar refresh token lama dengan pasangan access token dan refresh token baru (mekanisme rotasi token).

- **Endpoint**: `POST /v1/auth/refresh`
- **Autentikasi**: Header `X-Device-Credential` + `X-App-Version`
- **Request Body**:
```json
{
  "refresh_token": "ref_90a1f4..."
}
```
- **Respons (200 OK)**:
```json
{
  "access_token": "eyJhbGciOiJIUz...",
  "refresh_token": "ref_b87c12...",
  "expires_in": 900,
  "server_time": "2026-10-20T10:20:00Z"
}
```

#### D. Algoritma Verifikasi PIN Kasir Offline (Tanpa Internet)
Pada data master `users` saat bootstrap/changes, server menyertakan parameter kriptografis verifier PIN khusus perangkat tersebut:
- `pin_kdf_salt` (Base64)
- `pin_kdf_iterations` (120.000)
- `pin_verifier` (Base64)

**Langkah Validasi di Tablet**:
1. `kdf_bytes = PBKDF2-HMAC-SHA256(pin_string, salt=base64_decode(pin_kdf_salt), iterations=120000, key_length=32)`
2. `hmac_key = LowercaseHex(SHA256(device_credential))` (sebagai string ASCII 64 karakter)
3. `calculated_verifier = Base64(HMAC-SHA256(key=hmac_key, data=kdf_bytes))`
4. Jika `calculated_verifier == user.pin_verifier` (bandingkan konstan-waktu), maka PIN valid secara offline.

---

### 3.4 Sinkronisasi Master Data (Bootstrap & Delta Changes)

#### A. Bootstrap Data Master (Inisialisasi Pertama)
Mengunduh seluruh katalog master data secara bertahap menggunakan kursor.

- **Endpoint**: `GET /v1/bootstrap?cursor={kursor}&limit=200`
- **Autentikasi**: Header `X-Device-Credential` + `Authorization: Bearer <token>`
- **Urutan Seksi Data**:
  - **Cafe**: `business_units` $\rightarrow$ `settings` $\rightarrow$ `users` $\rightarrow$ `menu_items` $\rightarrow$ `menu_options`
  - **Laundry**: `business_units` $\rightarrow$ `settings` $\rightarrow$ `users` $\rightarrow$ `laundry_services` $\rightarrow$ `customers`
- **Respons (200 OK)**:
```json
{
  "section": "menu_items",
  "records": [
    {
      "id": 12,
      "business_unit_id": 1,
      "name": "Kopi Susu Gula Aren",
      "category": "Minuman",
      "price": 22000,
      "active": true,
      "sold_out": false
    }
  ],
  "next_cursor": "eyJ0eXBlIjoiYm9vdHN0cmFwIi...",
  "done": false,
  "delta_cursor": "eyJ0eXBlIjoiY2hhbmdlcyIsInNlcSI6NDIwNX0=",
  "server_time": "2026-10-20T10:08:00Z"
}
```
*Catatan: Simpan nilai `delta_cursor` dari halaman terakhir (`done: true`) sebagai titik awal polling sinkronisasi delta.*

#### B. Polling Perubahan Data Master (Delta Changes)
Mengambil mutasi penambahan, pembaruan, atau soft-delete pada master data sejak urutan sequence terakhir.

- **Endpoint**: `GET /v1/changes?cursor={delta_cursor}&limit=200`
- **Autentikasi**: Header `X-Device-Credential` + `Authorization: Bearer <token>`
- **Respons (200 OK)**:
```json
{
  "changes": [
    {
      "table": "menu_items",
      "op": "upsert",
      "record": {
        "id": 12,
        "name": "Kopi Susu Gula Aren",
        "price": 24000,
        "sold_out": true
      }
    },
    {
      "table": "menu_items",
      "op": "delete",
      "record": {
        "id": 15
      }
    }
  ],
  "next_cursor": "eyJ0eXBlIjoiY2hhbmdlcyIsInNlcSI6NDIzMH0=",
  "has_more": false,
  "server_time": "2026-10-20T10:15:00Z"
}
```

#### C. Jam Server & Konfigurasi Unit Usaha
- `GET /v1/time`: Mengembalikan stempel waktu server terkini untuk memverifikasi akurasi jam tablet.
- `GET /v1/config`: Mengambil pengaturan unit usaha, daftar putih fitur, dan label metode bayar (`cash` = Tunai, `qris_static` = QRIS).

---

### 3.5 Shift Kasir & Tutup Paksa

#### A. Cek Shift Berjalan Perangkat Ini
- **Endpoint**: `GET /v1/shifts/current`
- **Autentikasi**: Header `X-Device-Credential` + `Authorization: Bearer <token>`
- **Respons (200 OK)**:
```json
{
  "shift": {
    "id": "a5e9f821-4321-4f1a-9abc-1234567890ab",
    "business_unit_id": 1,
    "user_id": 3,
    "user_name": "Budi Santoso",
    "opened_at": "2026-10-20T08:00:00Z",
    "opening_cash": 200000,
    "status": "open"
  },
  "server_time": "2026-10-20T10:16:00Z"
}
```
*Catatan: Respons tidak memuat hitungan uang kas server untuk menjaga integritas prinsip "blind cash count" bagi kasir.*

#### B. Tutup Paksa Shift dari Tablet (Manager / Owner)
Digunakan ketika kasir yang bertugas tidak dapat menutup shift secara normal.

- **Endpoint**: `POST /v1/shifts/{id}/forceclose`
- **Autentikasi**: Wajib peran `manager` atau `owner`.
- **Request Body**:
```json
{
  "note": "Kasir sakit mendadak dan pulang sebelum toko tutup."
}
```
- **Respons**: `200 OK`

---

### 3.6 Mesin Sinkronisasi Bundel Outbox (`POST /v1/sync/bundles`)

Endpoint utama pengiriman seluruh mutasi operasional tablet ke server.

- **Endpoint**: `POST /v1/sync/bundles`
- **Perlindungan**: Advisory Lock per `bundle_id`, Transactional Rollback, Rate Limiter `sync`.
- **Batas Payload**: Maksimal 50 bundel per request, maksimal 100 event per bundel. `occurred_at` tidak boleh lebih dari 5 menit di masa depan atau lebih tua dari 90 hari.

#### Struktur Request Pengiriman Bundel
```json
{
  "bundles": [
    {
      "bundle_id": "c1f7289b-90a1-4321-b123-abcdef012345",
      "device_id": "dev-c1",
      "shift_id": "a5e9f821-4321-4f1a-9abc-1234567890ab",
      "events": [
        {
          "event_id": "e01-uuid...",
          "type": "order.created",
          "occurred_at": "2026-10-20T10:10:00Z",
          "payload": { ... }
        },
        {
          "event_id": "e02-uuid...",
          "type": "payment.recorded",
          "occurred_at": "2026-10-20T10:11:00Z",
          "payload": { ... }
        }
      ]
    }
  ]
}
```

#### Status Respons Pemrosesan Bundel
Setiap bundel dievaluasi secara independen dan atomik:

```json
{
  "results": [
    {
      "bundle_id": "c1f7289b-90a1-4321-b123-abcdef012345",
      "status": "ACCEPTED",
      "server_received_at": "2026-10-20T10:12:00Z"
    },
    {
      "bundle_id": "d2e8391a-1234-4321-b123-098765fedcba",
      "status": "REJECTED",
      "reason_code": "TOTAL_MISMATCH",
      "message": "Subtotal hitung ulang 65000, diterima 60000",
      "retryable": false
    }
  ],
  "server_time": "2026-10-20T10:12:00Z"
}
```

#### Tabel Evaluasi Status Bundel
| Status | `retryable` | Tindakan Aplikasi Tablet Android |
|---|---|---|
| `ACCEPTED` | - | Sukses. Hapus bundel dari outbox lokal. |
| `DUPLICATE`| - | Sudah pernah diproses sebelumnya. Anggap sukses, hapus dari outbox. |
| `REJECTED` | `true` | Masalah sementara (misal: shift belum terbuka di server). Biarkan di outbox, coba kirim lagi nanti dengan backoff. |
| `REJECTED` | `false`| Penolakan permanen (pelanggaran validasi/total). Pindahkan bundel ke antrean bermasalah (*outbox problem*), jangan dikirim ulang secara otomatis. |

---

### 3.7 Rincian Skema Event Bundel

#### 1. `shift.opened`
Membuka shift kasir di laci kas.
```json
{
  "user_id": 3,
  "opening_cash": 200000
}
```

#### 2. `shift.closed`
Menutup shift kasir dan mencocokkan fisik uang tunai serta verifikasi transaksi QRIS statis.
```json
{
  "counted_cash": 485000,
  "expected_cash": 487000,
  "difference": -2000,
  "difference_reason": "Salah kembalian pelanggan",
  "cash_left": 200000,
  "cash_deposited": 285000,
  "qris_checked_payment_ids": [
    "pay-uuid-001",
    "pay-uuid-002"
  ]
}
```

#### 3. `order.created` (Cafe)
Membuat pesanan makanan / minuman di Cafe.
```json
{
  "order_id": "ord-uuid-cafe-01",
  "display_number": "C1-0014",
  "order_type": "dine_in",
  "table_label": "Meja 4",
  "discount_rule": {
    "preset": "Promo Member 10%"
  },
  "lines": [
    {
      "line_id": "line-uuid-01",
      "item_ref_id": 12,
      "name": "Kopi Susu Gula Aren",
      "quantity": 2,
      "unit_price": 22000,
      "options": [
        { "option_id": 4, "name": "Extra Shot", "price_delta": 4000 }
      ]
    }
  ],
  "subtotal": 52000,
  "discount": 5200,
  "service_charge": 0,
  "tax": 4680,
  "rounding": 20,
  "total": 51500
}
```

#### 4. `order.created` (Laundry)
Membuat pesanan cucian. Mendukung pembuatan pelanggan inline langsung dari tablet.
```json
{
  "order_id": "ord-uuid-laundry-01",
  "display_number": "L1-0025",
  "order_type": "laundry",
  "customer": {
    "name": "Ibu Ratna",
    "phone": "081234567890"
  },
  "lines": [
    {
      "line_id": "line-uuid-02",
      "item_ref_id": 5,
      "name": "Cuci Setrika Reguler",
      "unit": "kg",
      "weight_grams": 4300,
      "express": false,
      "rate": 7000,
      "line_total": 31500
    }
  ],
  "subtotal": 31500,
  "discount": 0,
  "service_charge": 0,
  "tax": 0,
  "rounding": 0,
  "total": 31500
}
```

#### 5. `order.items_added`
Menambah menu pesanan cafe yang sedang berjalan sebelum dibayar.
```json
{
  "order_id": "ord-uuid-cafe-01",
  "lines": [
    {
      "line_id": "line-uuid-03",
      "item_ref_id": 8,
      "name": "Croissant Butter",
      "quantity": 1,
      "unit_price": 28000,
      "options": []
    }
  ],
  "subtotal": 80000,
  "discount": 8000,
  "service_charge": 0,
  "tax": 7200,
  "rounding": 0,
  "total": 79200
}
```
*Perhatian: Angka subtotal, diskon, dan total pada event ini adalah akumulasi total pesanan baru secara keseluruhan.*

#### 6. `order.sent_to_kitchen`
Mencatat daftar baris pesanan cafe yang sudah dicetak ke bagian dapur.
```json
{
  "order_id": "ord-uuid-cafe-01",
  "line_ids": [
    "line-uuid-01",
    "line-uuid-03"
  ]
}
```

#### 7. `order.table_moved`
Memindahkan nomor meja pesanan makan di tempat.
```json
{
  "order_id": "ord-uuid-cafe-01",
  "table_label": "Meja 9 (VIP)"
}
```

#### 8. `payment.recorded`
Mencatat pembayaran pesanan (Tunai atau QRIS Statis).
```json
{
  "payment_id": "pay-uuid-99",
  "order_id": "ord-uuid-cafe-01",
  "method": "cash",
  "amount": 79200,
  "tendered": 100000
}
```

#### 9. `laundry.status_changed`
Memperbarui siklus pengerjaan cucian dan mencatat pengambilan barang (termasuk pengambilan bertahap / sebagian).
```json
{
  "order_id": "ord-uuid-laundry-01",
  "status": "diambil",
  "user_id": 4,
  "reason": null,
  "approved_by": null,
  "line_ids": [
    "line-uuid-02"
  ]
}
```
- Jika `status` = `diambil` dan `line_ids` diisi: mencatat item yang diambil pada `laundry_pickups`. Status pesanan tetap `siap` hingga seluruh item selesai diambil.
- Jika `status` = `diambil` tanpa `line_ids`: menandai seluruh item sisa telah diambil penuh.
- Order yang belum lunas membutuhkan `approved_by` (ID manager/owner).

#### 10. `cashout.recorded`
Mencatat pengeluaran kas kecil dari laci kasir (pembelian bahan darurat, es batu, dll).
```json
{
  "cashout_id": "csh-uuid-11",
  "amount": 25000,
  "reason": "Beli es batu kristal 2 pack",
  "category": "Operasional",
  "receipt_file_id": "fil-uuid-01",
  "approved_by": 2
}
```
*Jika nominal melebihi pengaturan `cash_out_limit`, maka `approved_by` dan `receipt_file_id` wajib disertakan.*

#### 11. `correction.recorded`
Mencatat koreksi transaksi (Append-Only).
```json
{
  "correction_id": "cor-uuid-55",
  "type": "weight_adjust",
  "order_id": "ord-uuid-laundry-01",
  "order_line_id": "line-uuid-02",
  "payment_id": null,
  "amount": 3500,
  "reason_code": "salah_input",
  "reason_note": "Timbangan awal keliru",
  "approved_by": 2,
  "detail": {
    "new_weight_grams": 4800
  }
}
```
- **Tipe Valid**: `void`, `refund`, `manual_discount`, `comp`, `weight_adjust`, `price_adjust`.
- **Reason Code Valid**: `salah_input`, `pelanggan_batal`, `komplain_kualitas`, `salah_buat`, `promo`, `lainnya` (wajib isi `reason_note`).

---

### 3.8 Endpoint Pendukung Tablet (Pelanggan, File, Notifikasi)

#### A. Pencarian Pelanggan Laundry
- **Endpoint**: `GET /v1/customers/search?q={query}`
- **Autentikasi**: Header `X-Device-Credential` + Peran `kasir`/`manager` pada tablet Laundry.
- **Respons (200 OK)**:
```json
{
  "customers": [
    {
      "id": 8,
      "name": "Ratna Dewi",
      "phone": "6281234567890",
      "wa_opt_out": false
    }
  ],
  "server_time": "2026-10-20T10:18:00Z"
}
```

#### B. Unggah Foto Bukti Nota / Foto Cucian
- **Endpoint**: `POST /v1/files`
- **Content-Type**: `multipart/form-data`
- **Field**: `file` (Gambar JPEG/PNG/WEBP, maksimal 5 MB), `kind` (`expense_receipt` atau `laundry_condition`).
- **Respons (201 Created)**:
```json
{
  "id": "fil-uuid-01",
  "filename": "receipt_102026.jpg",
  "size": 184200,
  "mime_type": "image/jpeg",
  "server_time": "2026-10-20T10:19:00Z"
}
```

#### C. Cek Status Pengiriman WhatsApp Pesanan
- **Endpoint**: `GET /v1/orders/{id}/notification-status`
- **Respons (200 OK)**:
```json
{
  "notifications": [
    {
      "kind": "siap",
      "status": "sent",
      "attempts": 1,
      "last_attempt_at": "2026-10-20T10:15:00Z"
    }
  ],
  "server_time": "2026-10-20T10:20:00Z"
}
```

---

## 4. API Web Owner & Manager (Real-time Backoffice)

**URL Dasar**: `https://api.kasir.domain/v1/web` (Lokal: `http://localhost:8000/v1/web`)  
**Kontrak Spesifikasi**: `openapi/web/openapi.json` (61 endpoint)

### 4.1 Konvensi Autentikasi, Sesi, dan CSRF

- **Autentikasi**: Menggunakan Cookie Sesi terenkripsi Laravel (`SameSite=Lax`). Klien wajib menyertakan opsi `withCredentials: true` (Axios) atau `credentials: 'include'` (Fetch).
- **Proteksi CSRF**:
  1. Panggil `GET /v1/web/csrf-cookie` saat inisialisasi aplikasi frontend. Server menyematkan cookie `XSRF-TOKEN`.
  2. Setiap request mutasi (`POST`, `PUT`, `PATCH`, `DELETE`) wajib menyertakan header `X-XSRF-TOKEN` berisi nilai cookie tersebut.
- **Hak Akses Berdasarkan Peran**:
  - `owner`: Akses penuh ke seluruh fitur, mutasi data, laporan finansial, audit log, persetujuan perangkat.
  - `manager`: Akses hanya baca (*read-only*) terbatas pada unit usaha yang ditugaskan kepadanya. Tidak memiliki akses ke data HPP, margin, pengeluaran, P&L, atau manajemen perangkat.

---

### 4.2 Autentikasi, TOTP MFA, dan Reset Password

```
                       Alur Login Web
                             │
            POST /auth/login {email, password}
                             ▼
              ┌──────────────────────────────┐
              │ Status Respons yang Diterima │
              └──────────────┬───────────────┘
                             │
     ┌───────────────────────┼────────────────────────┐
     ▼                       ▼                        ▼
[AUTHENTICATED]        [MFA_REQUIRED]       [MFA_SETUP_REQUIRED]
  Selesai Masuk     POST /auth/mfa/verify     POST /auth/mfa/setup
                       {code / recovery}                │
                                              POST /auth/mfa/confirm
                                                        │
                                              Dapatkan Recovery Codes
```

#### A. Inisialisasi Cookie CSRF
- **Endpoint**: `GET /v1/web/csrf-cookie`
- **Respons**: `204 No Content` (Menyematkan cookie `XSRF-TOKEN`).

#### B. Login Akun Web
- **Endpoint**: `POST /v1/web/auth/login`
- **Request Body**:
```json
{
  "email": "owner@kasir.test",
  "password": "PasswordKuat123!"
}
```
- **Respons Jika Langsung Masuk (200 OK)**:
```json
{
  "status": "AUTHENTICATED",
  "user": {
    "id": 1,
    "name": "Bapak Hendra (Owner)",
    "email": "owner@kasir.test",
    "role": "owner",
    "mfa_enabled": true,
    "business_units": [
      { "id": 1, "name": "Kopi Senja Cafe", "type": "cafe", "timezone": "Asia/Makassar" },
      { "id": 2, "name": "Bersih Kilat Laundry", "type": "laundry", "timezone": "Asia/Makassar" }
    ]
  },
  "server_time": "2026-10-20T10:21:00Z"
}
```
- **Respons Jika Butuh MFA (200 OK)**:
```json
{
  "status": "MFA_REQUIRED",
  "user": null,
  "server_time": "2026-10-20T10:21:00Z"
}
```

#### C. Verifikasi Kode TOTP MFA
- **Endpoint**: `POST /v1/web/auth/mfa/verify`
- **Request Body (Menggunakan Kode Authenticator)**:
```json
{
  "code": "482910"
}
```
- **Request Body (Menggunakan Kode Pemulihan Cadangan)**:
```json
{
  "recovery_code": "ABCD-1234-EFGH"
}
```
- **Respons (200 OK)**:
```json
{
  "status": "AUTHENTICATED",
  "user": { ... },
  "recovery_codes_left": 9,
  "server_time": "2026-10-20T10:22:00Z"
}
```

#### D. Setup Pertama Kali MFA TOTP (Wajib bagi Owner)
1. `POST /v1/web/auth/mfa/setup`: Menghasilkan rahasia TOTP, URL QR code (`otpauth://`), dan instruksi manual.
2. `POST /v1/web/auth/mfa/confirm`:
   ```json
   { "code": "629104" }
   ```
   Server mengaktifkan MFA dan mengembalikan **10 Kode Pemulihan** sekali tayang.

#### E. Profil Sesi Login
- **Endpoint**: `GET /v1/web/me`
- **Respons (200 OK)**:
```json
{
  "user": { ... },
  "session": {
    "expires_at": "2026-10-20T22:21:00Z"
  },
  "server_time": "2026-10-20T10:23:00Z"
}
```

#### F. Logout Akun
- **Endpoint**: `POST /v1/web/auth/logout`
- **Respons**: `204 No Content`

---

### 4.3 Dashboard & Pemantauan Sistem

- **Endpoint**: `GET /v1/web/dashboard`
- **Hak Akses**: `owner`, `manager`
- **Respons (200 OK)**:
```json
{
  "today_summary": {
    "gross_sales": 3250000,
    "net_sales": 3020000,
    "cash_collected": 1850000,
    "qris_collected": 1170000,
    "order_count": 84
  },
  "alerts": {
    "stale_devices": [
      { "device_id": "dev-c2", "name": "Tablet Kasir Bar", "last_heartbeat": "2026-10-20T07:10:00Z" }
    ],
    "sync_problems_count": 1,
    "whatsapp_circuit_open": false,
    "unpicked_laundry_count": 14
  },
  "server_time": "2026-10-20T10:25:00Z"
}
```

---

### 4.4 Manajemen Perangkat & Antrean Masalah

#### A. Daftar Perangkat Kasir
- **Endpoint**: `GET /v1/web/devices`
- **Hak Akses**: `owner`, `manager`
- **Respons (200 OK)**:
```json
{
  "data": [
    {
      "id": "dev-c1",
      "business_unit_id": 1,
      "code": "C1",
      "name": "Tablet Kasir Utama Cafe",
      "status": "aktif",
      "last_app_version": "1.2.0",
      "last_battery_percent": 88,
      "printer_status": "ok",
      "last_seen_at": "2026-10-20T10:20:00Z"
    }
  ]
}
```

#### B. Pembuatan Kode Pendaftaran Tablet
- **Endpoint**: `POST /v1/web/registration-codes`
- **Hak Akses**: `owner`
- **Request Body**:
```json
{
  "business_unit_id": 1
}
```
- **Respons (201 Created)**:
```json
{
  "code": "REG-839201",
  "business_unit_id": 1,
  "expires_at": "2026-10-20T10:55:00Z"
}
```

#### C. Persetujuan & Pencabutan Tablet
- `POST /v1/web/devices/{id}/approve`: Menyetujui pendaftaran perangkat.
- `POST /v1/web/devices/{id}/revoke`: Mencabut akses tablet secara permanen (`confirm: true` wajib diisi).

#### D. Antrean Bundel Bermasalah (*Sync Problems*)
- `GET /v1/web/sync-problems`: Melihat bundel yang ditolak permanen server.
- `GET /v1/web/sync-problems/{bundleId}`: Memeriksa isi payload bundel mentah yang gagal untuk audit teknis.
- `GET /v1/web/sync-problems/export`: Ekspor seluruh catatan kesalahan sinkronisasi.

---

### 4.5 Manajemen Pengguna & Reset PIN

- `GET /v1/web/users`: Menampilkan daftar pengguna (filter `role`, `active`, `business_unit_id`, `q`).
- `POST /v1/web/users`: Menambah karyawan/pengguna baru.
- `PATCH /v1/web/users/{id}`: Mengubah informasi dasar pengguna.
- `POST /v1/web/users/{id}/deactivate`: Menonaktifkan pengguna (`confirm: true`).
- `POST /v1/web/users/{id}/activate`: Mengaktifkan kembali akun pengguna.
- `POST /v1/web/users/{id}/reset-pin`: Mengatur ulang PIN kasir (4–6 digit). Server otomatis menghitung ulang *salt* dan verifier offline PBKDF2.

---

### 4.6 Pengaturan & Master Data

#### A. Pengaturan Sistem (`settings`)
- `GET /v1/web/settings`: Mengambil daftar parameter konfigurasi global dan per unit usaha.
- `PUT /v1/web/settings`: Mengubah nilai pengaturan.
  ```json
  {
    "business_unit_id": 1,
    "values": {
      "cash_tolerance": 2000,
      "cash_out_limit": 100000,
      "tax_bp": 1000,
      "rounding_multiple": 100
    }
  }
  ```

#### B. Master Menu Cafe & Layanan Laundry
- `GET`, `POST`, `PATCH`, `DELETE /v1/web/menu-items`: CRUD katalog produk cafe.
- `PATCH /v1/web/menu-items/{id}/sold-out`: Mengubah status stok habis (dapat dilakukan Manager).
- `GET`, `POST`, `PATCH`, `DELETE /v1/web/laundry-services`: CRUD tarif layanan laundry (satuan `kg` atau `pcs`, tarif kilat/reguler, batas gramasi minimum).

---

### 4.7 Shift, Laporan Z Server, dan Transaksi

#### A. Laporan Z Versi Server
Dihitung secara langsung dan independen oleh server dari data transaksi yang diterima.

- **Endpoint**: `GET /v1/web/shifts/{id}/z-report`
- **Hak Akses**: `owner`, `manager`
- **Respons (200 OK)**:
```json
{
  "cash": {
    "opening_cash": 200000,
    "cash_sales": 850000,
    "cash_dp_settlement": 120000,
    "cash_refunds": 25000,
    "cash_outs": 50000,
    "expected_cash": 1095000,
    "counted_cash": 1093000,
    "tablet_expected_cash": 1095000,
    "tablet_difference": -2000,
    "server_difference": -2000,
    "adjustments_total": 0,
    "adjusted_difference": -2000,
    "expected_mismatch": false,
    "stored_stale": false
  },
  "sales": {
    "total_count": 42,
    "gross": 1450000,
    "discount": 50000,
    "tax": 140000,
    "service": 0,
    "cancelled": 1,
    "net": 1540000
  },
  "payments_by_method": {
    "cash": 945000,
    "qris_static": 595000
  },
  "qris_check": {
    "total_count": 18,
    "checked_count": 18,
    "unchecked_count": 0
  },
  "unpaid_orders": []
}
```

#### B. Penyesuaian Kas Shift & Tutup Paksa
- `POST /v1/web/shifts/{id}/adjustments`: Owner menambahkan catatan penyesuaian kas (append-only) atas selisih uang di laci kasir (`amount` bertanda positif/negatif, `reason` 5–500 karakter).
- `POST /v1/web/shifts/{id}/force-close`: Owner menutup paksa shift yang terbengkalai (`note`, `confirm: true`).

---

### 4.8 Modul Keuangan & Inventaris (Khusus Owner)

1. **Beban Operasional (`/expenses`)**:
   - `GET /v1/web/expenses`: Riwayat biaya pengeluaran toko (filter tanggal, kategori, unit usaha, atau beban bersama).
   - `POST /v1/web/expenses`: Mencatat pengeluaran operasional baru (mendukung lampiran bukti foto via `file_id`).
2. **Biaya Berulang (`/recurring-expenses`)**:
   - `GET`, `POST`, `PATCH`, `DELETE /v1/web/recurring-expenses`: Mengatur biaya rutin bulanan (sewa tempat, tagihan WiFi, gaji karyawan). Tanggal 29–31 otomatis diproses di hari terakhir bulan berjalan.
3. **Stok Opname Akhir Bulan (`/stock-counts/{period}`)**:
   - `GET /v1/web/stock-counts/{period}`: Melihat persediaan bahan baku pada periode `YYYY-MM`.
   - `PUT /v1/web/stock-counts/{period}`: Mengganti seluruh daftar persediaan bahan baku pada periode tersebut untuk perhitungan HPP di Laba Rugi.
4. **Rekonsiliasi QRIS Statis (`/reconciliations`)**:
   - `GET /v1/web/reconciliations`: Membandingkan total penerimaan QRIS di sistem kasir dengan dana cair di rekening koran merchant.

---

### 4.9 Laporan Bisnis, Laba Rugi, & Deteksi 13 Anomali

#### A. Laporan Laba Rugi Bulanan
- **Endpoint**: `GET /v1/web/reports/profit-loss?month=2026-10`
- **Hak Akses**: Khusus `owner`
- **Formula**:
  $$\text{Pendapatan Bersih} = \text{Penjualan Kotor} - \text{Diskon} - \text{Refund/Void} \pm \text{Koreksi Penimbangan}$$
  $$\text{HPP Bahan Baku} = \text{Stok Awal} + \text{Pembelian Bahan Periode Ini} - \text{Stok Akhir}$$
  $$\text{Laba Bersih} = \text{Pendapatan Bersih} - \text{HPP} - \text{Total Beban Operasional}$$

#### B. Laporan Deteksi 13 Anomali (*Exception Report*)
- **Endpoint**: `GET /v1/web/exceptions?from=YYYY-MM-DD&to=YYYY-MM-DD`
- **Hak Akses**: `owner`, `manager`
- **13 Jenis Temuan Otomatis**:
  1. `cashier_ratio`: Rasio void, refund, dan diskon kasir tertentu jauh melampaui rata-rata kasir lain.
  2. `cash_difference`: Selisih uang kas laci saat tutup shift melewati ambang toleransi (`cash_tolerance`).
  3. `cash_streak`: Kasir mengalami tekor selisih kas negatif berturut-turut.
  4. `expected_mismatch`: Perhitungan kas seharusnya versi tablet kasir berbeda dari hasil kalkulasi server.
  5. `picked_unpaid`: Cucian diserahkan ke pelanggan padahal pesanan belum lunas tanpa izin Manager/Owner.
  6. `cash_out_over_limit`: Pengeluaran kas laci melebihi batas batas maksimal (`cash_out_limit`).
  7. `cash_out_frequency`: Frekuensi kas keluar terlalu sering dalam satu shift kerja.
  8. `outside_hours`: Transaksi penjualan terjadi di luar jam operasional resmi unit usaha.
  9. `qris_difference`: Selisih antara uang QRIS tercatat dengan nilai settlement rekening merchant.
  10. `kg_deviation`: Rata-rata timbangan per order kasir tertentu menyimpang drastis dari kasir lain.
  11. `comp`: Transaksi makanan/minuman gratis atau diskon 100%.
  12. `stale_sync`: Tablet kasir sengaja tidak terhubung ke jaringan internet saat jam buka.
  13. `price_deviation`: Harga transaksi satuan yang diinput kasir berbeda dari katalog master data.
- **Review Temuan**: `POST /v1/web/exceptions/reviews` memungkinkan Owner menandai temuan telah diinvestigasi dengan menyertakan catatan penyelesaian.

---

## 5. Endpoint Operasional & Kesiapan Server (`/health` & `/ready`)

Disediakan di `routes/ops.php` tanpa otentikasi (wajib dilindungi atau dibatasi pada tingkat reverse proxy Nginx/Cloudflare).

### A. Health Check (Liveness Probe)
- **Endpoint**: `GET /health`
- **Fungsi**: Memeriksa proses PHP-FPM / Web Server aktif (tanpa koneksi basis data).
- **Respons (200 OK)**:
```json
{
  "status": "ok"
}
```

### B. Readiness Check (Readiness Probe)
- **Endpoint**: `GET /ready`
- **Fungsi**: Memeriksa konektivitas PostgreSQL, cache storage, izin penulisan disk, dan denyut scheduler terakhir.
- **Respons (200 OK)**:
```json
{
  "status": "ready",
  "checks": {
    "database": "ok",
    "cache": "ok",
    "storage": "ok",
    "scheduler": "ok"
  }
}
```
*Jika komponen kritis gagal, endpoint mengembalikan HTTP status 503 Service Unavailable.*

---

## 6. Tugas Terjadwal & Otomasi WhatsApp

Dijalankan secara periodik melalui Laravel Scheduler (`routes/console.php`):

| Waktu | Perintah Konsol Artisan | Keterangan |
|---|---|---|
| Setiap Menit | `php artisan kasir:whatsapp-send` | Memproses antrean notifikasi WhatsApp Fonnte (dengan jeda 3–8 detik dan Circuit Breaker otomatis). |
| Setiap Menit | `scheduler-heartbeat` | Memperbarui stempel waktu detak scheduler di cache untuk endpoint `/ready`. |
| Pukul 00:30 | `php artisan kasir:recurring-expenses` | Menghasilkan pengeluaran berulang bulanan (sewa, langganan, dll). |
| Pukul 03:00 | `php artisan kasir:cleanup` | Membersihkan token kedaluwarsa, attempt PIN lama, dan payload bundel ditolak $>90$ hari. |
| Pukul 03:30 | `php artisan queue:prune-failed` | Membersihkan job antrean gagal lama ($>30$ hari). |
| Pukul 09:00 | `php artisan kasir:laundry-reminders` | Membuat notifikasi pengingat WhatsApp bagi cucian yang belum diambil pelanggan $\ge$ `unpicked_days`. |

---

## 7. Spesifikasi Rumus Domain Finansial

Seluruh perhitungan keuangan berpusat murni di direktori `app/Domain/` untuk menjamin kepatuhan aturan *Single Source of Truth*.

### 1. Pesanan Cafe (`App\Domain\Pricing\CafeTotalCalculator`)
- **Diskon**:
  - Tipe Persen: $\text{Diskon} = \lfloor \frac{\text{Subtotal} \times \text{BasisPoin} + 5000}{10000} \rfloor$
  - Tipe Nominal: $\text{Diskon} = \min(\text{Nominal}, \text{Subtotal})$
- **Biaya Layanan (*Service Charge*)**:
  $$\text{ServiceCharge} = \lfloor \frac{(\text{Subtotal} - \text{Diskon}) \times \text{ServiceBp} + 5000}{10000} \rfloor$$
- **Pajak (*Tax*)**:
  $$\text{Pajak} = \lfloor \frac{(\text{Subtotal} - \text{Diskon} + \text{ServiceCharge}) \times \text{TaxBp} + 5000}{10000} \rfloor$$
- **Pembulatan Kasir**:
  Dibulatkan ke kelipatan terdekat (default Rp100, setengah ke atas):
  $$\text{Total} = \lfloor \frac{(\text{SebelumPembulatan}) \times 2 + \text{Kelipatan}}{\text{Kelipatan} \times 2} \rfloor \times \text{Kelipatan}$$

### 2. Layanan Laundry (`App\Domain\Pricing\LaundryLineCalculator`)
- **Layanan Kiloan (`kg`)**:
  - Berat dibulatkan ke kelipatan gram berikutnya (`step_grams`) dan tidak boleh kurang dari berat minimum (`min_grams`):
    $$\text{GramTerhitung} = \max(\text{min\_grams}, \lfloor \frac{\text{weight\_grams} + \text{step\_grams} - 1}{\text{step\_grams}} \rfloor \times \text{step\_grams})$$
  - Total Harga Baris:
    $$\text{LineTotal} = \lfloor \frac{\text{GramTerhitung} \times \text{TarifKg} + 500}{1000} \rfloor$$
- **Layanan Satuan (`pcs`)**:
  $$\text{LineTotal} = \text{JumlahPcs} \times \text{TarifPcs}$$

### 3. Kas Laci Seharusnya (`App\Domain\Cash\CashCalculator`)
$$\text{Kas Seharusnya} = \text{Kas Awal} + \text{Penjualan Tunai} + \text{DP/Pelunasan Tunai} - \text{Refund Tunai} - \text{Kas Keluar}$$
$$\text{Selisih Kas} = \text{Uang Fisik Dihitung Kasir} - \text{Kas Seharusnya}$$
*(Nilai selisih negatif menandakan laci kasir mengalami tekor).*
