# M1: Fondasi dan dua cangkang aplikasi

Tujuan: proyek Gradle berjalan untuk dua aplikasi, dengan inti bersama yang bisa menyimpan dan menyinkronkan data tiruan.

Baca: `AGENTS.md`, `docs/architecture.md`, `docs/spec/09-*`, `docs/spec/10-*`, `docs/spec/12-*`.

## Hasil yang diminta

- Modul sesuai `docs/architecture.md`, termasuk `build-logic`, `app-cafe`, `app-laundry` (cangkang kedua boleh hampir kosong), dan uji dependensi modul di CI.
- `core:domain` dengan rumus uang dan lulus `contracts/test-vectors.json` (jalankan `tools/verify_vectors.py` sebagai acuan angka).
- Room, DAO, outbox, dan pengirim sinkron (bundel idempoten) terhadap mock server.
- Pendaftaran perangkat (kode sekali pakai), login PIN, token terenkripsi (DataStore + Tink), bootstrap, dan delta sync.
- Kerangka layar (`AppShell`, navigasi bawah mengambang) sesuai `docs/ui/layout-standard.md`.
- CI membangun kedua aplikasi; keystore dan package name final (OQ-01) sebelum rilis pertama.

## Gerbang lulus

- Rumus: semua test vector lulus. Sync: bundel yang dikirim dua kali tetap satu transaksi (tes otomatis).
- Kedua aplikasi terpasang di tablet, login, bootstrap sampai selesai, dan data tiruan tersinkron.
