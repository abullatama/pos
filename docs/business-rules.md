# Aturan Bisnis (ringkasan untuk agent)

Ringkasan dari spek terpadu v2.1 bagian 2, 7, 8, 11, 12. Jika berbeda dengan spek atau test vector, spek dan test vector yang menang. Kasus uji ada di `contracts/test-vectors.json`.

## Status

| Objek | Nilai | Catatan |
| --- | --- | --- |
| Cucian | Diterima, Diproses, Siap, Diambil | Satu arah. Cuci ulang: Siap kembali ke Diproses dengan alasan dan PIN manager, tanpa tagihan baru |
| Pembayaran order laundry | Belum lunas, DP, Lunas | DP dan pelunasan adalah pembayaran terpisah |
| Pesanan cafe | Terbuka, Lunas, Dibatalkan | Dine-in dibayar saat selesai |
| Shift | open, closed, force_closed | Satu shift open per tablet |
| Perangkat | Menunggu, Aktif, Dicabut | Dicabut: `DEVICE_REVOKED` |
| Cetakan | Menunggu, Berhasil, Gagal | Retry otomatis |
| Outbox | Menunggu, Terkirim, Bermasalah | Bermasalah tidak pernah dihapus diam-diam |

Status Diambil hanya lahir dari event `laundry.picked_up` (pengambilan terakhir); `laundry.status_changed` hanya membawa Diterima, Diproses, Siap, dan cuci ulang. Aturan status laundry: Diambil hanya bila lunas atau ada `approved_by` (PIN manager). Pengambilan sebagian dicatat per baris layanan (`laundry_pickups`, event `laundry.picked_up`); order menjadi Diambil bila semua baris punya pengambilan terakhir, sisanya tetap Siap. Event `laundry.*` dan `customer.created` tidak terikat shift, dan sesi staf laundry tidak menutup shift.

## Rumus

Semua uang bilangan bulat rupiah. Persentase dihitung per komponen dan dibulatkan ke rupiah terdekat (setengah naik).

**Cafe**

```
subtotal   = sum(harga_satuan_termasuk_varian_addon x jumlah)
diskon     = preset persen atau nominal, atas subtotal (maksimal sama dengan subtotal)
dpp        = subtotal - diskon
layanan    = dpp x persen_layanan            (bila aktif)
pajak      = (dpp + layanan) x persen_pajak  (mode "ditambahkan"; mode "termasuk": lihat OQ-04)
sebelum    = dpp + layanan + pajak
total      = pembulatan(sebelum) ke kelipatan Rp100, setengah naik
pembulatan = total - sebelum   (disimpan sebagai baris tersendiri)
```

**Laundry**

```
per kg : berat_tertagih = max(berat_min, ceil(berat / kelipatan) x kelipatan)
         harga = berat_tertagih x tarif_per_kg   (express: tarif express)
per pcs: harga = jumlah x tarif_per_pcs
```

Pajak laundry belum diputuskan (OQ-03). Default sementara: 0%.

**Kas seharusnya saat tutup shift**

```
kas = kas_awal + penjualan_tunai + dp_dan_pelunasan_tunai - refund_tunai - kas_keluar
```

QRIS tidak masuk laci. Server menghitung ulang dan menyimpannya di samping angka tablet.

**Kembalian tunai** = uang_diterima - total. Kurang bayar ditolak, kecuali DP laundry.

**Laba-rugi ringan**

```
HPP              = pembelian_bahan + stok_awal - stok_akhir
laba_bersih      = penjualan_bersih - HPP - biaya_operasional
penjualan_bersih = penjualan_kotor - diskon - refund_atau_void
```

Modal per porsi dan per kg hanya untuk estimasi margin, tidak dipakai di laba-rugi bulanan.

## Izin ringkas

| Aksi | Kasir | Staf Laundry | Manager | Owner |
| --- | --- | --- | --- | --- |
| Buat order, terima pembayaran | Ya | Tidak | Ya | Tidak |
| Scan tag, ubah status laundry | Ya | Ya | Ya | Tidak |
| Diskon preset | Ya | Tidak | Ya | Tidak |
| Diskon manual/gratis/komplimen | PIN manager | Tidak | Ya | Tidak |
| Void item setelah dikirim | PIN manager | Tidak | Ya | Tidak |
| Void/refund transaksi lunas | PIN manager | Tidak | Ya | Tidak |
| Ubah harga atau berat setelah nota | PIN manager | Tidak | Ya | Tidak |
| Kas keluar sampai batas | Ya | Tidak | Ya | Tidak |
| Kas keluar di atas batas | PIN manager | Tidak | Ya | Tidak |
| Cetak ulang (berlabel SALINAN) | Ya | Tidak | Ya | Tidak |
| Menu admin tablet | Tidak | Tidak | Tidak | PIN owner |
| Kelola tablet, akun, pengaturan, koreksi setelah shift ditutup | Tidak | Tidak | Tidak | Web |

Void atau refund transaksi lunas di shift yang sama: PIN manager. Atas transaksi shift sebelumnya: manager di tablet dengan PIN manager, dicatat di shift berjalan sebagai refund tunai (mengurangi laci hari itu), bertanda di laporan pengecualian, dibatasi nilai dan umur (usulan Rp100.000 dan 14 hari, OQ-22). Di atas batas: hanya owner dari web dengan sumber dana dicatat. Refund selalu tunai.

## Pembayaran

- Metode: `cash` dan `qris_static`. Tidak ada metode lain.
- Refund selalu mengurangi Tunai. Total QRIS bruto tidak berkurang karena refund; rekonsiliasi QRIS memakai total bruto (sebelum MDR) per tanggal bisnis.
- Pesanan cafe terbuka saat tutup shift diserahterimakan ke shift berikutnya; pembayaran tercatat di shift yang menerimanya.
- Tutup shift: kas ditinggal di laci + disetor atau dibawa harus sama dengan kas dihitung, atau selisih beralasan; uang yang dibawa mencatat penerimanya.
- QRIS Statis: kasir memastikan lewat notifikasi aplikasi merchant atau soundbox, bukan screenshot, lalu menekan Pembayaran diterima dan mengonfirmasi nominal. Bisa offline.
- Split hanya berdasarkan nominal (dua metode); tidak ada split per item.
- Setiap percobaan bayar punya `idempotency_key` (UUID dari tablet).

## Nomor dan waktu

- Nomor tampil: kode tablet + urutan harian, unik per perangkat per hari (`C1-0007`, `L1-0012`). Identitas sistem tetap UUID.
- Waktu disimpan UTC, ditampilkan di zona waktu usaha. Tanggal bisnis laporan mengikuti `occurred_at`.

## Kode error

| Kode | HTTP | Perilaku app |
| --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Perpanjang token; bila gagal, login ulang |
| `FORBIDDEN` | 403 | Tidak diulang |
| `DEVICE_REVOKED` | 403 | Layar terkunci, hubungi owner |
| `ACCOUNT_LOCKED` | 423 | Tampilkan `retry_after` |
| `VALIDATION_FAILED` | 422 | Tidak diulang, masuk Antrean bermasalah |
| `UPGRADE_REQUIRED` | 426 | Minta update |
| `RATE_LIMITED` | 429 | Ulang dengan backoff |
| `FEATURE_DISABLED` | 403 | Sembunyikan fitur |
| `DEPENDENCY_MISSING` | (alasan REJECTED) | Diulang dengan backoff (`retryable: true`) |

Flag (hasil `ACCEPTED` dengan `flags[]`, masuk karantina): `TOTAL_MISMATCH`, `SHIFT_RANGE`, `UNKNOWN_ITEM`, `CASHOUT_UNAPPROVED`, `CASH_MISMATCH`, `CASH_ALLOCATION`, `CLOCK_SKEW`, `OLD_EVENT`. Transaksi dicatat sesuai angka tablet; `server_total` disimpan di sampingnya.

## Sync, ringkas

- Satu bundel = satu transaksi utuh. Respons per bundel: `ACCEPTED`, `DUPLICATE` (dianggap sukses), `REJECTED` + `reason_code`.
- Event untuk order yang sama dikirim berurutan; antar-order tidak diasumsikan berurutan.
- Retry hanya untuk 429 dan 5xx (backoff eksponensial dengan jitter). 422 tidak diulang.
- Retensi: event sinkron di tablet 30 hari (Menunggu dan Bermasalah tidak dihapus); `sync_bundles` di server minimal 365 hari.
- Kirim dulu, lalu tarik perubahan (delta). Master data: server menang.
