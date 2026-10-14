# Rencana Implementasi: Milestone M1 - Fondasi & Dua Cangkang Aplikasi (F06 & F07)

## Ringkasan Proyek
Membangun fondasi arsitektur multi-modul Android modern untuk dua aplikasi kasir terpisah (**AA Coffeeshop** / `app-cafe` dan **Athia Laundry House** / `app-laundry`) dari satu repositori bersama sesuai standar industri dan spesifikasi `docs/architecture.md`, `docs/spec/09-*`, serta aturan `AGENTS.md`.

---

## Modul Target (Struktur Arsitektur)

```text
A3/
├── build-logic/                # Convention plugins Gradle (Android, Compose, Kotlin, Lint)
├── app-cafe/                   # Cangkang tipis AA Coffeeshop (id.tenang.kasir.cafe)
├── app-laundry/                # Cangkang tipis Athia Laundry House (id.tenang.kasir.laundry)
├── core/
│   ├── domain/                 # Kotlin murni (tanpa Android): entitas, rumus uang Long, use cases
│   ├── data/                   # Room database, DAO, entity, repository implementasi
│   ├── sync/                   # Outbox pattern, delta sync, worker
│   ├── auth/                   # Autentikasi PIN kasir & token terenkripsi (DataStore + Tink)
│   ├── printer/                # PrinterService & ESC/POS (migrasi dari spike M0)
│   └── ui/                     # Design tokens & komponen UI Compose bersama
└── feature/
    ├── cafe/                   # Alur POS Cafe, meja, tiket dapur
    ├── laundry/                # Alur Order Laundry, tag, status satu arah, pengambilan
    ├── shift/                  # Buka/tutup shift, kas modal, laporan Z
    ├── payment/                # Alur pembayaran (Tunai & QRIS Statis)
    └── admin/                  # Pengaturan tablet, diagnostik
```

---

## Aturan Dependensi (Enforced via F07)
1. `app-cafe` dan `app-laundry` hanya berisi wiring (DI, navigasi, branding/tema, manifest) tanpa logika bisnis.
2. `feature:cafe` dan `feature:laundry` **DILARANG** saling bergantung. Keduanya boleh memakai `feature:shift` dan `feature:payment`.
3. `feature:shift` dan `feature:payment` **DILARANG** bergantung pada feature usaha (`cafe` atau `laundry`); komunikasi data lewat interface di `core:domain`.
4. `core:domain` adalah pustaka Kotlin murni tanpa dependensi Android SDK.
5. Uji dependensi modul dijalankan otomatis via task Gradle khusus (F07).

---

## Tahapan Rencana Kerja

### Tahap 1: Setup Build-Logic & Dependency Management (F06)
- Konfigurasi `build-logic` dengan Gradle Convention Plugins:
  - `id.a3.android.application`: konfigurasi standar app (`compileSdk=36`, `minSdk=29`, `targetSdk=36`, ProGuard, Java 17).
  - `id.a3.android.library`: konfigurasi Android library bersama.
  - `id.a3.android.compose`: konfigurasi Jetpack Compose & compiler Compose.
  - `id.a3.jvm.library`: konfigurasi modul Java/Kotlin murni (untuk `core:domain`).
- Perbarui `gradle/libs.versions.toml` dengan pustaka standar (Room, Hilt/Koin, Serialization, Coroutines, Tink, DataStore).

### Tahap 2: Pembuatan Modul Inti (`core/*`) dan Fitur (`feature/*`) (F06)
- Buat modul `core:domain` (Kotlin murni).
- Buat modul `core:data`, `core:sync`, `core:auth`, `core:printer`, `core:ui`.
- Buat modul `feature:cafe`, `feature:laundry`, `feature:shift`, `feature:payment`, `feature:admin`.
- Daftarkan semua modul di `settings.gradle.kts`.

### Tahap 3: Pembuatan Dua Cangkang Aplikasi (`app-cafe` & `app-laundry`) (F06)
- Setup `app-cafe` dengan package `id.tenang.kasir.cafe`, icon, tema AA Coffeeshop, dan manifest.
- Setup `app-laundry` dengan package `id.tenang.kasir.laundry`, icon, tema Athia Laundry House, dan manifest.
- Pertahankan modul `:app` lama sebagai spike runner agar pengujian fisik F01/F02/F03/F04 tetap terjaga.

### Tahap 4: Uji Dependensi Modul (F07)
- Buat task Gradle / unit test arsitektur untuk memvalidasi aturan ketergantungan modul:
  - Verifikasi bahwa `feature:cafe` tidak mengimpor `feature:laundry` dan sebaliknya.
  - Verifikasi bahwa `feature:shift` & `feature:payment` tidak mengimpor `feature:cafe` / `feature:laundry`.
  - Verifikasi `core:domain` bebas dari Android SDK.

### Tahap 5: Verifikasi Build & Eksekusi
- Jalankan perintah `./gradlew :app-cafe:assembleDebug :app-laundry:assembleDebug`.
- Jalankan uji verifikasi ketergantungan modul.
- Jalankan pemeriksaan lint/ktlint bila terkonfigurasi.

---

## Kriteria Sukses
1. `./gradlew :app-cafe:assembleDebug :app-laundry:assembleDebug` berhasil 100%.
2. Uji dependensi modul berhasil memvalidasi isolasi antar fitur.
3. Status `F06` dan `F07` di `feature_list.json` siap ditandai `passes: true`.
