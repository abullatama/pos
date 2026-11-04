# AGENTS.md: Aplikasi Kasir Laundry & Cafe

Aturan kerja untuk coding agent. Sumber kebenaran: `docs/spec/` (Spesifikasi Terpadu MVP v2.2), `contracts/openapi.yaml`, dan `contracts/test-vectors.json`. Jika file ini atau asumsimu bertentangan dengan ketiganya, ketiganya yang menang. Jika ada yang tidak jelas, tulis di `docs/open-questions.md` dan minta keputusan manusia. Jangan menebak.

> **Pembagian kerja antar agen AI** ada di `AGENTS_RULES.md`. Baca file tersebut sebelum memulai sesi, terutama jika project dikerjakan bersama CodeBuddy (external AI) dan Android Studio AI Agent (internal AI).

## Apa yang dibangun

- **Dua aplikasi Android terpisah** (cafe dan laundry) dari satu repositori, satu backend, satu database, satu web owner. Offline-first.
- Pembayaran hanya **Tunai** dan **QRIS Statis**. Tidak ada payment gateway, QRIS Dinamis, atau webhook pembayaran. Jangan membangunnya.
- Perangkat target: tablet Redmi Pad 2 (Android 15, landscape), printer iWare C58BT (58 mm, ESC/POS, Bluetooth).

## Struktur repositori

```
app-cafe/  app-laundry/     cangkang tipis (manifest, navigasi, DI, tema, ikon, nama)
feature/{cafe,laundry,shift,payment,admin}
core/{domain,data,sync,auth,printer,ui}
build-logic/
contracts/                  openapi.yaml, test-vectors.json
docs/                       business-rules, architecture, decisions, open-questions, ui, milestones, spec
seed/                       data contoh
```

## Aturan yang tidak boleh dilanggar

1. **Uang = `Long` rupiah.** Dilarang `Float`/`Double` untuk uang.
2. **Rumus hanya di `:core:domain`** (Kotlin murni). Urutan tetap: subtotal, diskon, biaya layanan, pajak, pembulatan Rp100 setengah naik. Setiap perubahan rumus wajib lolos `contracts/test-vectors.json`.
3. **Cangkang aplikasi hanya wiring dan branding.** Tidak ada logika bisnis di `app-cafe` atau `app-laundry`.
4. **`feature:cafe` dan `feature:laundry` tidak boleh saling bergantung.** Keduanya boleh memakai `feature:shift` dan `feature:payment`; shift dan payment tidak boleh bergantung pada feature usaha (pakai interface di `core:domain`). Aplikasi bergantung pada feature dan core.
5. **Layar tidak memanggil jaringan.** Layar membaca Room lewat Flow; repositori dan worker mengurus sinkron.
6. **Append-only.** Transaksi tidak diedit atau dihapus. Koreksi = catatan baru yang menunjuk ke transaksi asal, dengan alasan dari daftar tetap.
7. **Snapshot.** Harga dan modal disalin ke baris transaksi saat dibuat.
8. **ID dari perangkat (UUID).** Nomor tampil = kode tablet + urutan harian (`C1-0007`, `L1-0012`).
9. **Server menentukan.** Total dihitung ulang server; master data mengikuti server. `app_id` hanya untuk versi dan pemantauan, tidak pernah untuk otorisasi. Fakta uang dari tablet **diterima lalu ditandai (flag) dan dikarantina**, tidak ditolak; hanya otorisasi, struktur, dan dependensi yang `REJECTED`. Umur event tidak pernah jadi alasan menolak.
10. **Status laundry satu arah:** Diterima, Diproses, Siap, Diambil. Cuci ulang wajib alasan dan PIN manager. Diambil hanya bila lunas, atau dengan PIN manager. Pengambilan sebagian dicatat per baris (`laundry_pickups`). Event `laundry.*` tidak terikat shift; sesi staf laundry tidak menutup shift.
11. **PIN manager** untuk: diskon manual/gratis/komplimen, void item setelah dikirim, void/refund transaksi lunas, ubah harga atau berat setelah nota, kas keluar di atas batas. Semua masuk audit log.
12. **Refund selalu tunai**, termasuk refund atas pembayaran QRIS.
13. **Tidak ada rahasia di repositori:** kunci API, token, keystore, `google-services.json` produksi, kata sandi.
14. **Log tanpa data sensitif:** PIN, token, nomor HP lengkap, data pelanggan.

## Dokumen: baca sesuai tugas

| Tugas | Baca |
| --- | --- |
| Mulai sesi | `progress.md`, `feature_list.json`, brief milestone di `docs/milestones/` |
| Rumus, status, izin, kode error | `docs/business-rules.md`, `contracts/test-vectors.json` |
| Struktur modul, dua aplikasi, versi, CI | `docs/architecture.md` |
| API dan sinkron | `contracts/openapi.yaml`, `docs/spec/` bagian 11 dan 12 |
| Model data | `docs/spec/` bagian 10 |
| UI dan layout | `docs/ui/` |
| Mengapa sesuatu diputuskan | `docs/decisions.md` |
| Hal yang belum diputuskan (dengan nilai sementara) | `docs/open-questions.md` |

Jangan memuat seluruh spek sekaligus; baca bagian yang relevan saja.

## Perintah

```
./gradlew :app-cafe:assembleDebug :app-laundry:assembleDebug
./gradlew test lint
```

Perbarui bagian ini setelah proyek Gradle dibuat di M1.

## Protokol sesi

1. Jalankan `pwd`, `git log -5`, lalu baca `progress.md`.
2. Pilih **satu** fitur dengan `"passes": false` di milestone aktif dari `feature_list.json`.
3. Kerjakan dengan tes yang menyertainya. Rumus: test vector. Sync: bundel yang dikirim dua kali tetap satu transaksi.
4. Ubah `"passes"` menjadi `true` hanya setelah tes lulus. Fitur yang butuh perangkat asli (printer, kiosk, background sync) ditandai `"needs_device": true`: minta manusia mengujinya dan melaporkan hasil, jangan mengklaim sudah menguji.
5. Commit dengan pesan jelas, lalu tambahkan ringkasan ke `progress.md`.
6. Dilarang menghapus fitur atau mengubah deskripsinya di `feature_list.json`; hanya `passes` dan `notes` yang boleh diubah.

## Offline, ringkas

- Boleh offline: buat order/pesanan, terima Tunai dan QRIS Statis, ubah status laundry, cetak, buka shift, tutup shift (dengan peringatan Menunggu sinkron), void/refund tunai/diskon (PIN manager diverifikasi lokal).
- Butuh online: ubah Pengaturan/akun/menu (web), laporan, WhatsApp (dikirim backend).
- Kirim dulu, lalu tarik perubahan. Logout tidak menghapus outbox. Indikator sinkron selalu terlihat.
- Sinkron utama = coroutine di foreground; WorkManager hanya cadangan. Data sensitif: DataStore + Tink (kunci di Android Keystore); jangan pakai Jetpack Security Crypto. Praktik Android modern: spek bagian 9.

## Cara bekerja

- Jangan menambah dependensi tanpa alasan tertulis; cek versi library ke dokumentasi resmi.
- Teks antarmuka di resource string, Bahasa Indonesia, tanpa istilah teknis.
- Printer di balik `PrinterService`; pembayaran di balik interface metode bayar (Tunai, QRIS Statis).
- Jika menemukan bug atau celah di spek, catat di `docs/open-questions.md`; jangan diam-diam mengubah perilaku.

## Investigasi Bug & Efisiensi Konteks

1. **Kunci Cakupan Modul (Strict Scoping):** Jika laporan masalah spesifik pada satu aplikasi (misal: Cafe), dilarang membuka modul lain (Laundry, Spike, Web) kecuali ada ketergantungan kode yang terbukti langsung.
2. **Dilarang Over-Investigation Riwayat Git:** Jangan menelusuri commit masa lalu (`git log -p`, `git show commit_lama`) untuk mencari penyebab bug aktif, kecuali pengguna secara eksplisit meminta pelacakan riwayat perubahan. Fokus langsung pada status kode saat ini (*HEAD*).
3. **Targeted Reading:** Gunakan `grep` dan baca irisan baris spesifik (30–50 baris di sekitar aksi/navigasi yang bermasalah). Dilarang membaca file ratusan baris secara utuh jika hanya memeriksa *event handler* atau navigasi.
4. **Batas Investigasi & Konfirmasi (Max 3 Langkah):** Jika dalam 2–3 kali pembacaan alat (*tool calls*) tidak ditemukan baris kode penyebab langsung, agen **wajib berhenti menggali**, laporkan hipotesis awal, dan konfirmasi kondisi layar/perilaku kepada pengguna. Jangan berspekulasi dengan membuka puluhan berkas.

