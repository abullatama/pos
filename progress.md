# Progress

Diisi agent di akhir setiap sesi: apa yang selesai, apa yang dites, apa yang menunggu manusia.

| Tanggal | Ringkasan | Fitur (id) | Catatan |
| --- | --- | --- | --- |
| 2026-10-12 | Paket repositori awal dibuat; spek diperbarui ke v2.2 (hasil tinjauan kontradiksi) | - | Belum ada kode. Mulai dari M0. Backend/web AGENTS.md menunggu pemilihan stack (D-11) |
| 2026-10-12 | Koreksi status: M0 belum selesai. Fitur F01-F05 berstatus passes: false dan siap dikerjakan | - | Memulai spike M0: uji printer, sinkron latar belakang, mode kios, scan QR, dan validasi kontrak/mock server |
| 2026-10-12 | Spike F01 Printer iWare C58BT (Bluetooth SPP & USB) selesai dan terverifikasi di hardware fisik | F01 | Lolos uji hardware di Redmi Pad 2 & iWare C58BT: teks 32 karakter pas 1 baris, QR raster bitmap 128x128 dots jelas, tail feed 6 baris tuntas melewati gerigi pemotong kertas. Status F01: passes = true |
| 2026-10-12 | Validasi kontrak OpenAPI v0 & pengujian mock server lokal berhasil penuh | F05 | Skema contracts/openapi.yaml valid 100% (paths=15, skema=23, event=14, bebas ref putus & bebas ambiguitas oneOf). Seluruh 15 endpoint berhasil diuji dan dijawab mock server tools/mock_server.js. Status F05: passes = true |
| 2026-10-14 | Spike F02 Uji Sinkron Tiruan (Foreground Coroutine + WorkManager) di Redmi Pad 2 terverifikasi | F02 | Lolos verifikasi hardware fisik di Redmi Pad 2 (HyperOS 2 / Android 16): foreground loop berjalan kontinu saat layar mati, dan WorkManager sukses dipicu secara periodik (15 menit) maupun on-demand tanpa dimatikan oleh sistem. Status F02: passes = true |
| 2026-10-14 | Spike F03 Kiosk Mode & Update APK Senyap di Redmi Pad 2 terverifikasi | F03 | Lolos verifikasi di Redmi Pad 2 (HyperOS 2 / Android 16): LockTask (Screen Pinning) terbukti mengunci kasir di POS (Back/Home/Recents diblokir). Device Owner dpm ditolak OS bila akun Mi/Google aktif (dicatat di OQ-24); update mandiri tanpa Google Play terverifikasi via PackageInstaller.Session dengan prompt konfirmasi kasir. Status F03: passes = true |
