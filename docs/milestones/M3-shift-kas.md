# M3: Shift, kas, koreksi, dan rekonsiliasi

Baca: `docs/business-rules.md`, `docs/spec/05-*`, `docs/spec/06-*`, `docs/spec/14-*`.

## Hasil yang diminta

- Buka shift, tutup shift dengan hitung kas buta, kas seharusnya, selisih, alasan, catatan kas (tinggal dan setor), laporan Z.
- Serah terima pesanan cafe terbuka ke shift berikutnya; staf laundry bekerja tanpa shift.
- Cek QRIS Statis saat tutup shift (daftar ID di `shift.closed`).
- Void, refund tunai (termasuk transaksi shift sebelumnya, berbatas), diskon manual, kas keluar dengan foto nota, PIN manager lokal.
- Tutup paksa dari tablet (`shift.force_closed`), kiosk mode, dan pembaruan app.
- Rekonsiliasi QRIS bruto dan laporan pengecualian di web (bagian pengembang web).

## Gerbang lulus

- Skenario 12, 16, 17, 18, 19, 20, 21, 23, 27, 28 lulus; selisih kas nol atau dalam toleransi pada uji internal.
