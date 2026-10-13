## 11. Kontrak API

Kontrak ditulis lebih dulu dalam OpenAPI dan menjadi satu-satunya sumber kebenaran antara app dan backend. Bagian ini merinci daftar endpoint menjadi field request dan respons sebagai bahan menulis berkas OpenAPI; setelah OpenAPI ada, berkas itulah yang berlaku. Kontrak web owner ditulis terpisah. Endpoint baru `POST /v1/files` (unggah foto nota kas keluar) perlu disepakati dengan pembuat app. Pada v2 tidak ada endpoint pembayaran: pembayaran masuk lewat bundel sinkron sebagai event `payment.recorded`.

**Konvensi**

- Semua path berawalan `/v1`; versi dikirim app lewat header, dan backend membalas 426 bila di bawah versi minimum.
- Request dan respons berformat JSON, kecuali unggah file. Waktu berformat ISO 8601 UTC. Uang berupa integer rupiah.
- Header: `Authorization: Bearer <access_token>`, `X-Device-Credential`, `X-AppVersion`, `X-AppId` (`cafe` atau `laundry`, hanya untuk versi dan pemantauan, tidak pernah untuk otorisasi), dan `X-Request-Id` (dibuat klien untuk pelacakan).
- Semua endpoint tulis idempotent berdasarkan ID dari tablet. Setiap respons membawa `server_time`.
- Daftar memakai cursor opaque dan `limit`.
- Format error seragam: `{ "code": "VALIDATION_FAILED", "message": "Total tidak cocok", "details": {}, "request_id": "..." }`.

**Endpoint untuk app**

| Endpoint | Request | Respons sukses | Error khas |
| --- | --- | --- | --- |
| `POST /v1/devices/register` | registration\_code, device\_id, name | 202 {status: "PENDING"} | 422 CODE\_INVALID, 409 perangkat sudah terdaftar |
| `GET /v1/devices/me` | (header kredensial) | {status, business\_unit\_id}; kredensial perangkat dikirim satu kali saat pertama kali Aktif | 401, 403 DEVICE\_REVOKED |
| `POST /v1/auth/pin-login` | user\_id, pin | {access\_token, refresh\_token, expires\_in, user: {id, name, role}, business\_unit\_id, server\_time} | 401 PIN\_INVALID, 423 ACCOUNT\_LOCKED (dengan retry\_after), 403 FORBIDDEN |
| `POST /v1/auth/refresh` | refresh\_token | Pasangan token baru | 401 UNAUTHENTICATED |
| `GET /v1/bootstrap` | cursor, limit | {section, records\[\], next\_cursor, done, delta\_cursor} | 426 UPGRADE\_REQUIRED |
| `GET /v1/changes` | cursor, limit | {changes: \[{table, op: upsert atau delete, record}\], next\_cursor, has\_more} | 426, 410 bila cursor kedaluwarsa |
| `GET /v1/customers/search` | q (minimal 3 karakter) | {customers\[\]} | 429 RATE\_LIMITED |
| `POST /v1/sync/bundles` | bundles\[\] (maksimal 50) | {results: \[{bundle\_id, status, reason\_code, message, server\_received\_at}\], server\_time} | 401, 403, 426 |
| `GET /v1/shifts/current` | (kosong) | {shift: {...} atau null} | 401 |
| `POST /v1/shifts/{id}/force-close` | catatan | Shift berstatus force\_closed | 403 (hanya manager atau owner) |
| `GET /v1/orders/{id}/notification-status` | (kosong) | {status: none, queued, sent, atau failed; last\_error; sent\_at} | 404 |
| `GET /v1/config` | (kosong) | {settings, feature\_flags (kosong di MVP), min\_app\_version, labels} | 401 |
| `GET /v1/time` | (kosong) | {server\_time} |  |
| `POST /v1/devices/heartbeat` | app\_version, battery\_percent, last\_sync\_at, outbox\_pending, outbox\_problem, printer\_status | 204 | 401 |
| `POST /v1/files` | Gambar JPEG atau PNG (multipart), file\_id dari tablet, kind | 201 {file\_id} | 413 ukuran terlalu besar, 422 tipe tidak didukung |

**Endpoint untuk web owner:** CRUD master data, pengaturan, akun, pengeluaran, dan stok akhir; daftar dan detail shift; tutup paksa dan catatan penyesuaian; laporan; rekonsiliasi QRIS Statis; Status Perangkat; antrean bermasalah; dan audit log. Bentuknya mengikuti pola REST biasa dan dirinci di OpenAPI. Daftar layar ada di bagian 14.

**Dua aplikasi:** `GET /v1/config` mengembalikan `min_app_version` dan remote config sesuai `app_id` dari header `X-AppId`, sehingga kedua aplikasi bisa dirilis di waktu berbeda. `POST /v1/devices/heartbeat` membawa `app_id`, dan tabel `devices` serta `device_heartbeats` mendapat kolom `app_id`. Usaha tetap ditentukan server dari perangkat yang terdaftar, bukan dari `app_id`.

**Pembaruan kontrak (v2.2)**

- Hasil per bundel: `ACCEPTED` (dengan `flags[]` bila ditandai), `DUPLICATE`, atau `REJECTED`. Hanya `FORBIDDEN`, `DEVICE_REVOKED`, `VALIDATION_FAILED`, dan `DEPENDENCY_MISSING` yang menolak (bagian 12).
- Tutup paksa dari tablet memakai event `shift.force_closed` lewat bundel (hanya Manager). Endpoint REST `force-close` di atas untuk web atau saat online.
- Staf Laundry boleh mengirim `laundry.status_changed` dan `laundry.picked_up`. Event ini dan `customer.created` tidak membutuhkan `shift_id`.
- Klaim token memakai nama `business_unit_id` di seluruh dokumen (bukan `business_id`).

Status Diambil hanya berasal dari `laundry.picked_up` (pengambilan terakhir), sedangkan `laundry.status_changed` hanya membawa Diterima, Diproses, Siap, dan cuci ulang. Payload lengkap seluruh 14 tipe event ada di `contracts/openapi.yaml` (`x-payload-by-type`), dan berkas itu yang berlaku bila berbeda dari contoh di bagian ini.

**Contoh bundel sinkron**

Request:

```json
{
  "bundles": [{
    "bundle_id": "6f1d2c0e-3a54-4b8e-9d41-2f7b8c1a9e01",
    "device_id": "dev-c1",
    "shift_id": "b2a5f3d4-7c18-4e60-a9d2-5c3e1f0b7a22",
    "events": [
      {
        "event_id": "c8e90a11-5f2d-4a37-8b16-d0e4a7c3f5b9",
        "type": "order.created",
        "occurred_at": "2026-10-12T02:15:08Z",
        "payload": {
          "order_id": "41d7b6a2-9e03-4c58-b1f4-6a8d2e5c7f30",
          "display_number": "C1-0007",
          "order_type": "dine_in",
          "table_label": "5",
          "lines": [
            { "item_ref_id": "menu-latte", "name": "Latte", "quantity": 1, "unit_price": 34000 },
            { "item_ref_id": "menu-croissant", "name": "Croissant", "quantity": 1, "unit_price": 28000 }
          ],
          "subtotal": 62000, "discount": 6200, "service_charge": 0,
          "tax": 5580, "rounding": 20, "total": 61400
        }
      },
      {
        "event_id": "0d3a77e5-b1c2-4f90-a6d8-93e5f1b2c4a7",
        "type": "payment.recorded",
        "occurred_at": "2026-10-12T02:16:40Z",
        "payload": {
          "payment_id": "e5b9c1d3-2a47-4f86-90ab-7d6c3e8f1a52",
          "order_id": "41d7b6a2-9e03-4c58-b1f4-6a8d2e5c7f30",
          "method": "cash", "amount": 61400, "tendered": 70000
        }
      }
    ]
  }]
}
```

Respons diterima:

```json
{ "results": [{ "bundle_id": "6f1d2c0e-3a54-4b8e-9d41-2f7b8c1a9e01",
  "status": "ACCEPTED", "server_received_at": "2026-10-12T02:17:02Z" }],
  "server_time": "2026-10-12T02:17:02Z" }
```

Respons diterima dengan tanda (total tidak cocok, dikarantina untuk ditinjau):

```json
{ "results": [{ "bundle_id": "6f1d2c0e-3a54-4b8e-9d41-2f7b8c1a9e01",
  "status": "ACCEPTED", "flags": ["TOTAL_MISMATCH"],
  "message": "Total hitung ulang 61400, tablet 61300; dicatat sesuai tablet dan dikarantina",
  "server_received_at": "2026-10-12T02:17:02Z" }],
  "server_time": "2026-10-12T02:17:02Z" }
```

**Kode error umum**

| Kode | HTTP | Arti | Perilaku app |
| --- | --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Token tidak valid atau kedaluwarsa | Perpanjang token; bila gagal minta login ulang |
| `FORBIDDEN` | 403 | Peran atau usaha tidak berizin | Tidak diulang |
| `DEVICE_REVOKED` | 403 | Perangkat dicabut | Layar terkunci, hubungi owner |
| `ACCOUNT_LOCKED` | 423 | PIN salah berulang | Tampilkan retry\_after |
| `VALIDATION_FAILED` | 422 | Data tidak valid | Tidak diulang, masuk Antrean bermasalah |
| `UPGRADE_REQUIRED` | 426 | Versi app di bawah minimum | Minta update |
| `RATE_LIMITED` | 429 | Terlalu banyak permintaan | Ulang dengan backoff |
| `FEATURE_DISABLED` | 403 | Fitur dimatikan lewat saklar | Sembunyikan fitur |

**Otorisasi per kelompok endpoint**

| Kelompok endpoint | Peran yang boleh | Lingkup |
| --- | --- | --- |
| Pendaftaran perangkat | Siapa pun dengan kode pendaftaran valid | Usaha pada kode |
| Auth (`pin-login`, `refresh`) | Perangkat Aktif | Usaha perangkat |
| Bootstrap, changes, config, time | Kasir, Staf Laundry, Manager | Usaha perangkat |
| Sync bundel | Kasir dan Manager untuk semua jenis event; Staf Laundry hanya `laundry.status_changed` dan `laundry.picked_up` | Usaha perangkat |
| Tutup paksa shift, koreksi setelah ditutup | Manager atau Owner (via web) | Usaha terkait |
| API web owner | Owner penuh, Manager terbatas (bagian 14) | Semua usaha untuk Owner, sesuai izin untuk Manager |

Semua query data selalu difilter oleh usaha dari token atau perangkat, bukan oleh parameter yang dikirim klien.
