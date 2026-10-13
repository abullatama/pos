# Progress

Diisi agent di akhir setiap sesi: apa yang selesai, apa yang dites, apa yang menunggu manusia.

| Tanggal | Ringkasan | Fitur (id) | Catatan |
| --- | --- | --- | --- |
| 2026-10-12 | Paket repositori awal dibuat; spek diperbarui ke v2.2 (hasil tinjauan kontradiksi) | - | Belum ada kode. Mulai dari M0. Backend/web AGENTS.md menunggu pemilihan stack (D-11) |
| 2026-10-12 | Koreksi status: M0 belum selesai. Fitur F01-F05 berstatus passes: false dan siap dikerjakan | - | Memulai spike M0: uji printer, sinkron latar belakang, mode kios, scan QR, dan validasi kontrak/mock server |
| 2026-10-12 | Spike F01 Printer iWare C58BT (Bluetooth SPP & USB) selesai dan terverifikasi di hardware fisik | F01 | Lolos uji hardware di Redmi Pad 2 & iWare C58BT: teks 32 karakter pas 1 baris, QR raster bitmap 128x128 dots jelas, tail feed 6 baris tuntas melewati gerigi pemotong kertas. Status F01: passes = true |
| 2026-10-12 | Validasi kontrak OpenAPI v0 & pengujian mock server lokal berhasil penuh | F05 | Skema contracts/openapi.yaml valid 100% (paths=15, skema=23, event=14, bebas ref putus & bebas ambiguitas oneOf). Seluruh 15 endpoint berhasil diuji dan dijawab mock server tools/mock_server.js. Status F05: passes = true |
