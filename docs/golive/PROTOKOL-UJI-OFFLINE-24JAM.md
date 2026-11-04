# PROTOKOL & LEMBAR AUDIT UJI OFFLINE 24 JAM (F43)
**a3coffeeshop & athialoundry**
*(Verifikasi Hardware Fisik Redmi Pad 2 — Milestone M4)*

> **Dokumen Single Source of Truth (SSOT)** untuk pelaksanaan pengujian fisik offline selama 24 jam penuh pada tablet Redmi Pad 2 sesuai kriteria lulus spek bagian 17 dan aturan kerja `AGENTS_RULES.md`.

---

## 1. TUJUAN & KRITERIA PENERIMAAN

- **Fitur ID:** `F43` (Milestone M4)
- **Aplikasi:** Keduanya (`app-cafe` dan `app-laundry`)
- **Kriteria Penerimaan (Acceptance Criteria):**
  > "Jumlah dan total transaksi di tablet sama dengan di server setelah 24 jam beroperasi penuh tanpa koneksi internet."
- **Penanggung Jawab:**
  - *Perancang Protokol & Arsitektur:* **CodeBuddy**
  - *Eksekutor Hardware & Pelapor Uji:* **Android Studio AI Agent & Tim Human**

---

## 2. JADWAL & FASE PENGUJIAN

| Waktu | Fase | Kegiatan Utama |
|---|---|---|
| **T-0 Jam** | **Fase 1: Persiapan & Pemutusan** | Deploy APK debug, registrasi ACTIVE, kosongkan outbox, aktifkan Mode Pesawat. |
| **T+0 s/d T+24 Jam** | **Fase 2: Operasional 24 Jam Offline** | Menjalankan transaksi penjualan, cetak struk/tag, buka-tutup shift, kas keluar, dan refund secara offline. |
| **T+24 Jam** | **Fase 3: Rekoneksi Internet** | Menghubungkan kembali tablet ke Wi-Fi, amati transmisi outbox otomatis ke server Laravel. |
| **T+24:30 Jam** | **Fase 4: Rekonsiliasi & Audit** | Menyamakan total rupiah dan jumlah data fisik Laporan Z terhadap database backend. |

---

## 3. INSTRUKSI LANGKAH DEMI LANGKAH

### FASE 1: PERSIAPAN AWAL & PEMUTUSAN INTERNET (T-0 Jam)

1. Pasang versi APK debug terbaru ke tablet Redmi Pad 2:
   ```bash
   adb install -r app-cafe/build/outputs/apk/debug/app-cafe-debug.apk
   adb install -r app-laundry/build/outputs/apk/debug/app-laundry-debug.apk
   ```
2. Hubungkan printer thermal **iWare C58BT** via Bluetooth SPP. Lakukan uji cetak di Menu Admin S15.
3. Buka aplikasi, pastikan perangkat terdaftar **ACTIVE** (atau gunakan mode demo bypass jika menguji server staging).
4. Periksa bilah atas (TopBar):
   - Indikator Sinkron: **Hijau ("Tersinkron")**
   - Jumlah Pending: **0**
5. **PUTUSKAN KONEKSI:**
   - Geser notification tray tablet ke bawah.
   - Aktifkan **Mode Pesawat (Airplane Mode)**.
   - Pastikan Wi-Fi dan Data Seluler mati total.
   - Hubungkan kabel charger ke stop kontak agar tablet tetap menyala selama 24 jam.

---

### FASE 2: SKENARIO TRANSAKSI OFFLINE 24 JAM

Penguji menjalankan aktivitas operasional normal di kedua aplikasi. Catat setiap transaksi ke **Lembar Kerja Audit** di bagian 4.

#### A. Skenario a3coffeeshop (`app-cafe`):
1. **Shift Pagi (Jam 08.00):**
   - Masuk menu **Shift** -> **Buka Shift (S04)**.
   - Masukkan kas awal modal Rp 200.000 per pecahan.
2. **Transaksi Penjualan (Minimal 10 Pesanan):**
   - Transaksi 1–4: Kopi varian panas/dingin, add-on syrup (Dine-in).
   - Transaksi 5–7: Makanan & snack (Takeaway).
   - Transaksi 8: Diskon preset 10%.
   - Transaksi 9: Diskon manual Rp 15.000 (Otorisasi PIN Manager 4444).
   - Transaksi 10: Pembayaran QRIS Statis (simulasi konfirmasi soundbox).
   - Cetak struk pelanggan 58 mm untuk setiap transaksi (struk tercetak normal secara offline).
3. **Pengeluaran Kas Kecil (Kas Keluar S12):**
   - Catat kas keluar Rp 25.000 (Kategori: Es Batu, tanpa PIN).
   - Catat kas keluar Rp 60.000 (Kategori: Gas Elpiji, wajib otorisasi PIN Manager).
4. **Catatan Koreksi (Refund Tunai S13):**
   - Buka tab Riwayat Selesai, lakukan Refund Tunai 1 item pesanan (Rp 20.000) dengan PIN Manager. Struk refund tercetak.
5. **Tutup Shift Pagi (Jam 16.00):**
   - Hitung kas buta (Tahap 1).
   - Kunci hitungan & evaluasi selisih K1 (Tahap 2).
   - Alokasi kas laci Rp 200.000 & cetak **Laporan Z Shift Pagi** (Tahap 3).
6. **Shift Malam (Jam 16.00 – 22.00):**
   - Buka Shift Malam kasir baru, lakukan 5 transaksi penjualan tambahan, lalu Tutup Shift Malam dan cetak Laporan Z.

#### B. Skenario athialoundry (`app-laundry`):
1. **Manajemen Pelanggan Offline (L02):**
   - Buat 3 pelanggan baru (Ibu Dewi 0812-1111-2222, Bpk Hendra 0813-3333-4444, Kak Rina 0821-5555-6666).
2. **Order Cucian Baru (L03):**
   - Order 1: Cuci Kiloan Reguler 4,2 kg (dibulatkan ke 4,5 kg), bayar lunas Tunai.
   - Order 2: Cuci Kiloan Express 2,0 kg, bayar DP Rp 10.000.
   - Order 3: Cuci Satuan (2 Jas, 1 Bed Cover), opsi bayar nanti saat ambil.
   - Struk tag QR thermal 58 mm tercetak untuk tiap order.
3. **Pemindaian QR Tag & Transisi Status (L07):**
   - Pindai tag Order 1: ubah status dari *Diterima -> Diproses*.
   - Beberapa jam kemudian, pindai kembali: ubah dari *Diproses -> Siap*.
4. **Penyerahan Cucian (L08) & Komplain Cuci Ulang (L09):**
   - Serahkan Order 1 (lunas) -> status menjadi *Diambil*.
   - Simulasi komplain cuci ulang Order 2 (PIN Manager) -> status kembali ke *Diproses*.
   - Lakukan pelunasan sisa tagihan Order 2 via layar pembayaran tunai.

---

### FASE 3: REKONEKSI & SINKRONISASI OTOMATIS (T+24 Jam)

1. Tepat setelah 24 jam berlalu, periksa TopBar tablet:
   - Indikator Sinkron: **Kuning ("N Menunggu")**
   - Jumlah pending: Menampilkan angka puluhan (misal: **38 Pending**).
2. **NYALAKAN KONEKSI:**
   - Matikan Mode Pesawat (Airplane Mode).
   - Hubungkan kembali tablet ke jaringan Wi-Fi toko yang memiliki akses internet.
3. **Amati Proses Sinkronisasi:**
   - Dalam rentang 5–15 detik, `OutboxSyncManager` foreground loop mendeteksi koneksi dan mulai mengirimkan bundel pending secara berurutan.
   - Angka pending di TopBar berkurang secara bertahap: `38 Menunggu` -> `20 Menunggu` -> `0 Menunggu`.
   - Indikator sinkron berubah menjadi **Hijau ("Tersinkron")**.
   - Periksa Menu Admin S15: Antrean Sinkron menampilkan status *"Tersinkron penuh"*.

---

## 4. LEMBAR KERJA REKONSILIASI & AUDIT HASIL UJI

Bandingkan angka fisik dari Laporan Z tablet dengan data di Web Dashboard / Server (`https://a3-api.nen.asia`):

### A. Rekonsiliasi Usaha Cafe (a3coffeeshop)

| Parameter Rekonsiliasi | Angka Tablet Fisik (Laporan Z) | Angka Server Backend (API) | Status Cocok | Catatan Selisih |
|---|:---:|:---:|:---:|---|
| **Jumlah Total Pesanan** | _____ Nota | _____ Transaksi | [ ] YA / [ ] TDK | |
| **Penjualan Bruto (Gross)** | Rp ____________ | Rp ____________ | [ ] YA / [ ] TDK | |
| **Total Diskon** | Rp ____________ | Rp ____________ | [ ] YA / [ ] TDK | |
| **Total Refund Tunai** | Rp ____________ | Rp ____________ | [ ] YA / [ ] TDK | |
| **Penjualan Bersih (Net)** | Rp ____________ | Rp ____________ | [ ] YA / [ ] TDK | |
| **Uang Masuk Tunai (Cash)** | Rp ____________ | Rp ____________ | [ ] YA / [ ] TDK | |
| **Total Pembayaran QRIS** | Rp ____________ | Rp ____________ | [ ] YA / [ ] TDK | |
| **Total Kas Keluar (Petty Cash)**| Rp ____________ | Rp ____________ | [ ] YA / [ ] TDK | |
| **Status Outbox Room** | 0 Pending, 0 Failed | 100% Accepted / Duplicate | [ ] YA / [ ] TDK | |

### B. Rekonsiliasi Usaha Laundry (athialoundry)

| Parameter Rekonsiliasi | Angka Tablet Fisik | Angka Server Backend (API) | Status Cocok | Catatan Selisih |
|---|:---:|:---:|:---:|---|
| **Jumlah Pelanggan Baru** | _____ Orang | _____ Orang | [ ] YA / [ ] TDK | |
| **Jumlah Order Laundry** | _____ Nota | _____ Transaksi | [ ] YA / [ ] TDK | |
| **Total Berat Cucian Kiloan** | _____ Kg | _____ Kg | [ ] YA / [ ] TDK | |
| **Total Pembayaran Diterima** | Rp ____________ | Rp ____________ | [ ] YA / [ ] TDK | |
| **Status Akhir Cucian** | Sesuai Fisik Rak | Sesuai Status Server | [ ] YA / [ ] TDK | |
| **Status Outbox Room** | 0 Pending, 0 Failed | 100% Accepted / Duplicate | [ ] YA / [ ] TDK | |

---

## 5. FORMAT LAPORAN AKHIR (UNTUK ANDROID STUDIO AI AGENT)

Setelah pengujian fisik 24 jam selesai dan seluruh angka cocok 100%, laporkan hasil menggunakan format sesuai `AGENTS_RULES.md`:

```markdown
| Tanggal | Fitur | Device | Hasil | Catatan |
|---|---|---|---|---|
| YYYY-MM-DD | F43 | Redmi Pad 2 | ✅ PASS | Uji offline 24 jam sukses. 38 transaksi Cafe & Laundry tersinkron utuh ke backend tanpa duplikasi data; omzet fisik Rp X.XXX.XXX cocok 100% dengan database server. |
```

Dan perbarui `feature_list.json`:
```json
{
  "id": "F43",
  "passes": true,
  "notes": "Lolos verifikasi hardware fisik di Redmi Pad 2: Tablet diuji offline 24 jam penuh (Mode Pesawat) memproses puluhan transaksi Cafe & Laundry (buka/tutup shift, penjualan tunai & QRIS, kas keluar, tag laundry, refund). Setelah Wi-Fi dinyalakan kembali, OutboxSyncManager berhasil mensinkronkan seluruh antrean ke server live tanpa data ganda (idempoten); jumlah dan total nominal rupiah di tablet cocok 100% dengan database server."
}
```
