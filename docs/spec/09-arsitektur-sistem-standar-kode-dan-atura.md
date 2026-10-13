## 9. Arsitektur sistem, standar kode, dan aturan agent

Backend dibuat sebagai satu API tanpa status di depan satu database relasional. App dan web hanya berbicara ke API, dan satu-satunya integrasi eksternal, Fonnte, hanya dipanggil oleh backend; kuncinya tidak pernah keluar dari server.

&#91;embedded content: arsitektur backend · komponen dan siapa memanggil siapa\]

**Prinsip arsitektur backend**

1. **Netral terhadap stack.** OpenAPI adalah satu-satunya antarmuka; tidak ada perilaku app yang bergantung pada implementasi server.
2. **Satu database relasional dengan transaksi.** Data transaksi append-only, dan koreksi berupa record baru.
3. **API tanpa status.** Sesi dan token tidak bergantung pada memori satu instance, sehingga bisa dijalankan lebih dari satu instance.
4. **Semua endpoint tulis idempotent** berdasarkan ID dari tablet.
5. **Server tidak percaya total dari klien.** Total dihitung ulang dan dibandingkan.
6. **Pekerjaan lambat lewat antrean.** WhatsApp dan pengingat berjalan di worker supaya tidak memblokir request.
7. **Rahasia di luar kode.** Kunci Fonnte dan penandatangan token disimpan sebagai rahasia lingkungan, bukan di repositori.
8. **Audit dan log.** Perubahan data sensitif masuk audit log. Log aplikasi tidak memuat data pribadi.
9. **Waktu.** Disimpan dalam UTC, ditampilkan di zona waktu usaha, dan server selalu mencatat `received_at`.

**Kemampuan wajib backend (apa pun produknya)**

| Kemampuan | Dipakai untuk |
| --- | --- |
| Transaksi database (ACID) | Penerimaan bundel utuh, idempotency |
| Antrean tugas dan worker | WhatsApp, pengingat |
| Penjadwal (cron) | Pengingat cucian tidak diambil, pengeluaran berulang, backup, pembersihan |
| Penyimpanan file | Foto nota pengeluaran dan kas keluar |
| HTTPS dengan domain dan sertifikat otomatis | Semua koneksi dari app dan web |
| Manajemen rahasia | Kunci Fonnte, penandatangan token |
| Backup harian dengan uji pemulihan | Pemulihan data |
| Pemantauan dan log terpusat | Error, latensi, kedalaman antrean |
| Pembatasan laju (rate limiting) | Login PIN dan login web |

**Arsitektur Android**

App memakai Kotlin dan Jetpack Compose dengan arsitektur berlapis dan offline-first: layar hanya membaca dari Room, dan jaringan hanya menjadi tugas sinkron di belakang layar.

- **Stack:** Kotlin, Jetpack Compose dengan layout adaptif tablet (landscape), Navigation Compose, satu Activity per aplikasi; MVVM dengan aliran data satu arah (StateFlow) dan Hilt; Room (migrasi wajib diuji), WorkManager, Retrofit dan OkHttp dengan kotlinx.serialization; CameraX dan ML Kit untuk scan QR tag; Firebase Crashlytics; DataStore dengan Tink untuk data sensitif (kunci enkripsi di Android Keystore; Jetpack Security Crypto tidak dipakai karena sudah deprecated). Sinkron utama berupa coroutine di foreground, dengan WorkManager sebagai cadangan. `minSdk` usulan 29.
- **Alur panggilan:** UI, ViewModel, UseCase (hanya untuk logika bisnis bersama), Repository, lalu Room atau Retrofit.
- **Modul:** `:app-cafe` dan `:app-laundry` (cangkang tipis), `:feature:cafe`, `:feature:laundry`, `:feature:shift`, `:feature:payment`, `:feature:admin`, `:core:domain`, `:core:data`, `:core:sync`, `:core:auth`, `:core:printer`, `:core:ui`, dan `:build-logic`.

**Dua aplikasi dari satu repositori (permintaan owner, v2.1)**

Owner meminta dua aplikasi Android terpisah dengan tampilan, ikon, dan pembaruan sendiri. Keduanya dibangun dari satu repositori dan satu logika bersama; yang dipisah hanya cangkang aplikasi, sehingga aturan uang, sync, login, shift, dan printer ditulis satu kali.

```text
/app-cafe          cangkang: Application, manifest, navigasi, DI, tema, ikon, nama
/app-laundry       cangkang yang sama untuk laundry
/feature
  /cafe            POS, meja, tiket dapur
  /laundry         order, tag, status, pengambilan
  /shift           buka dan tutup shift, laporan Z
  /payment         Tunai dan QRIS Statis
  /admin           menu admin tablet, diagnostik
/core
  /domain          rumus uang, aturan bisnis (Kotlin murni)
  /data            Room, repositori
  /sync            outbox, worker
  /auth            login PIN, token
  /printer         ESC/POS
  /ui              komponen dan token desain bersama
/build-logic       konvensi Gradle bersama
/contracts         openapi.yaml, test-vectors.json
```

**Aturan dependensi (ditegakkan di CI)**

1. Aplikasi bergantung pada feature dan core. Feature bergantung pada core, dan secara eksplisit :feature:cafe serta :feature:laundry boleh memakai :feature:shift dan :feature:payment. :feature:shift dan :feature:payment tidak boleh bergantung pada feature usaha; kebutuhan mereka atas data order dilewatkan lewat interface di :core:domain.
2. `:feature:cafe` dan `:feature:laundry` tidak boleh saling bergantung.
3. Modul aplikasi hanya berisi wiring dan branding (navigasi, DI, tema, ikon, nama). Tidak boleh ada logika bisnis di sana.
4. Build gagal bila aturan di atas dilanggar (uji dependensi modul).

**Branding per aplikasi**

- Komponen UI (tombol, kartu, dialog) ada di `:core:ui`; palet warna, logo, ikon adaptif, dan nama aplikasi ada di cangkang masing-masing, sehingga tampilannya jelas berbeda tanpa dua pustaka komponen.
- Application ID per aplikasi, dan `.debug` untuk build uji.

**Versi dan pembaruan**

- `versionCode` dan `versionName` independen per aplikasi, dengan tag git per aplikasi dan catatan rilis masing-masing (bagian 16).

**Aturan arsitektur Android**

1. UI tidak pernah memanggil jaringan. Layar membaca Room lewat Flow.
2. Logika uang (total, pembulatan, kas) berada di modul domain Kotlin murni tanpa dependensi Android, dan diuji dengan kasus uji bagian 7.
3. Printer di balik interface `PrinterService`, sehingga merek printer dapat diganti.
4. Pembayaran di balik interface metode bayar (Tunai, QRIS Statis), sehingga metode baru bisa ditambah tanpa mengubah alur transaksi.
5. Notifikasi WhatsApp berada di backend. App hanya menampilkan status.
6. Kegagalan dibungkus sebagai hasil (success atau error) dan ditampilkan sebagai pesan ramah tanpa istilah teknis.

**Standar kode**

- Uang selalu bilangan bulat rupiah (`Long`), tidak pernah `Float` atau `Double`. Waktu memakai `Instant` (UTC) dan hanya diformat di UI.
- Tidak ada logika bisnis di Composable. Semua teks UI di resource (Bahasa Indonesia).
- Format dan analisis statis dengan ktlint dan detekt. Pull request kecil per fitur.
- Tidak ada secret atau kunci API di repositori atau app. Log tidak memuat data pribadi.
- Setiap perubahan skema Room disertai migrasi dan tesnya.

**Praktik Android modern (wajib untuk kode baru)**

- KSP (bukan kapt) untuk Room dan Hilt, dan version catalog (`libs.versions.toml`) untuk semua versi dependensi.
- Compose membaca StateFlow dengan `collectAsStateWithLifecycle()`, dan navigasi memakai rute type-safe (Navigation Compose dengan rute `@Serializable`).
- Edge-to-edge dengan `targetSdk` 35 atau lebih tinggi: semua layar menangani inset sistem.
- Izin Bluetooth runtime (`BLUETOOTH_CONNECT`) diminta saat dipakai, dengan penjelasan di layar.
- Data sensitif memakai DataStore dengan Tink AEAD, dan kunci enkripsinya disimpan di Android Keystore. Jetpack Security Crypto tidak dipakai karena sudah deprecated.
- Acuan keamanan aplikasi: OWASP MASVS (penyimpanan, jaringan, platform), melengkapi bagian 15.

**Aturan kerja AI agent (versi ringkas ada di `AGENTS.md`)**

1. Baca spesifikasi ini dan OpenAPI sebelum mengubah kode. Jangan mengarang endpoint atau field.
2. Kerjakan satu fitur kecil per langkah, dengan satu commit per langkah.
3. Jalankan `./gradlew assembleDebug`, `test`, dan `lint` sebelum menyatakan selesai.
4. Jangan menambah dependensi tanpa alasan tertulis. Versi library dicek ke dokumentasi resmi, bukan dari ingatan.
5. Jangan mengubah logika uang tanpa menambah dan menjalankan tes kasus uji.
6. Jangan mengklaim sudah menguji printer atau tablet fisik. Minta pengguna menguji dan melaporkan hasilnya.
7. Bila spesifikasi ambigu atau saling bertentangan, tulis sebagai pertanyaan terbuka, jangan menebak.
