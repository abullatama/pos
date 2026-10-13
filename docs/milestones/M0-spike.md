# M0: Spike perangkat dan kontrak

Tujuan: membuktikan hal berisiko di tablet dan printer asli, dan membekukan kontrak API v0, sebelum modul lain dibangun.

Baca: `docs/decisions.md` (D-08, D-09, D-15, D-16), `docs/open-questions.md` (OQ-19, OQ-20, OQ-24), `docs/spec/03-*`, `docs/spec/05-*`, `docs/spec/11-*`.

## Hasil yang diminta

1. Aplikasi uji kecil (bukan produksi) yang mencetak ke iWare C58BT lewat Bluetooth dan USB: teks 32 karakter, QR, pemotongan, tanpa laci kas bila belum tersedia.
2. Uji sinkron tiruan di Redmi Pad 2: coroutine foreground sebagai jalur utama, WorkManager sebagai cadangan, layar mati dan app di latar belakang selama beberapa jam.
3. Uji mode kios: device owner (lock task) dan cadangan screen pinning; pasang APK senyap lewat PackageInstaller; catat apakah akun Mi mengganggu.
4. Uji kamera tablet memindai QR dari tag di kertas roll (termasuk kertas agak basah atau pudar).
5. `contracts/openapi.yaml` v0 disepakati dua pengembang, dan mock server dari kontrak itu berjalan.
6. Keputusan stack backend dan web dicatat di `docs/decisions.md` (D-11).

## Gerbang lulus

- Semua butir 1 sampai 4 berstatus lulus atau punya cadangan tertulis, hasilnya dicatat di `progress.md`.
- OpenAPI v0 lolos validator dan mock server menjawab semua endpoint.

Catatan: butir 1 sampai 4 butuh perangkat asli (`needs_device`). Agent menyiapkan kode uji dan prosedur; manusia menjalankan dan melaporkan hasil.
