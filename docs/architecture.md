# Arsitektur (ringkas)

Rincian lengkap: `docs/spec/09-*`, `10-*`, `11-*`, `12-*`.

## Gambaran

```
 Tablet kafe (app-cafe)      Tablet laundry (app-laundry)
        |  Room + outbox            |  Room + outbox
        +-----------+---------------+
                    | HTTPS, header X-AppId
              Backend (satu API, satu DB)  --->  Fonnte (WhatsApp), penjadwal
                    |
              Web owner (laporan, pengaturan, rekonsiliasi QRIS statis)
```

## Modul Android

```
app-cafe/       app-laundry/        cangkang: manifest, nama, ikon, tema, navigasi, DI
feature/cafe    feature/laundry     layar dan ViewModel per usaha
feature/shift   feature/payment     feature/admin     dipakai bersama
core/domain     Kotlin murni: model, rumus, aturan, use case
core/data       Room, DAO, repositori
core/sync       outbox, worker, delta sync
core/auth       PIN, token, perangkat
core/printer    PrinterService (ESC/POS, Bluetooth, USB)
core/ui         komponen dan tema bersama
build-logic/    convention plugin Gradle
```

Aturan ketergantungan (diperiksa CI):

- `app-*` bergantung pada `feature:*` dan `core:*`; `feature:*` hanya pada `core:*`.
- `feature:cafe` dan `feature:laundry` tidak saling bergantung.
- `core:domain` tidak bergantung pada Android.
- Tidak ada logika bisnis di `app-*`.

## Dua aplikasi, satu repositori

| Aspek | Cafe | Laundry |
| --- | --- | --- |
| Package | `id.tenang.kasir.cafe` | `id.tenang.kasir.laundry` |
| Keystore | sendiri | sendiri |
| Versi | `versionCode`/`versionName` sendiri | sendiri |
| Tag git | `cafe-vX.Y.Z` | `laundry-vX.Y.Z` |
| Firebase | satu proyek, aplikasi terpisah (plus `.debug`) | idem |
| Konfigurasi | `GET /v1/config` dengan `X-AppId` | idem |

`X-AppId` hanya untuk versi minimum, `UPGRADE_REQUIRED`, dan pemantauan. Otorisasi memakai peran dan perangkat.

## Alur data

1. Layar menulis ke Room (satu transaksi lokal: data + baris outbox).
2. Worker mengirim bundel ke `POST /v1/sync/bundles`.
3. Server memvalidasi, menghitung ulang total, menjawab per bundel.
4. Worker menarik perubahan lewat `GET /v1/changes?cursor=...` dan memperbarui master data.

## Standar

- Uang `Long`; waktu UTC; ID UUID dari perangkat.
- Layar tidak memanggil jaringan.
- Printer dan metode bayar di balik interface.
- Log tanpa data sensitif.
- CI: build kedua aplikasi, tes unit (rumus: test vector), lint, ktlint, detekt, cek ketergantungan modul.

## Backend dan web

Stack belum dipilih (D-11). Kontrak ada di `contracts/openapi.yaml`. Persyaratan tak tergantung stack: API stateless, penulisan idempoten, tabel transaksi append-only, `change_seq` untuk delta, soft delete untuk master data, migrasi database berversi.
