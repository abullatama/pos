# AGENTS_RULES.md — Perjanjian Kerja Antar Agen

## Tujuan

Dokumen ini adalah **single source of truth** untuk pembagian kerja antara dua agen AI yang berkolaborasi dalam project A3 POS:

- **CodeBuddy** — external AI coding assistant (penanggung jawab arsitektur & code quality)
- **Android Studio AI Agent** — internal AI assistant di Android Studio (penanggung jawab hardware execution & deployment)

Tujuan utamanya: **user tidak perlu mengulang penjelasan**. Setiap agen membaca file ini, `feature_list.json`, `progress.md`, dan `docs/open-questions.md` sebelum mengerjakan apapun.

---

## 1. Prinsip Dasar

1. **CodeBuddy adalah penanggung jawab utama arsitektur dan code quality.**
   - Menjaga struktur Gradle multi-module dan architecture rules.
   - Menulis/refactor domain logic, data layer, sync, auth, UI design system.
   - Memastikan unit test, mock server, dan best practice terpenuhi.
   - Mengaudit ulang hasil kerja Android Studio AI Agent sebelum dintegrasikan.

2. **Android Studio AI Agent adalah executor hardware & IDE.**
   - Deploy APK ke Redmi Pad 2.
   - Debug hardware: printer thermal iWare C58BT, kamera tablet, Bluetooth, WorkManager di device.
   - Mengambil logcat, screenshot, dan smoke test di device.
   - Verifikasi fitur yang membutuhkan perangkat fisik (`needs_device: true`).

3. **User adalah final decision maker.**
   - User menyetujui prioritas fitur.
   - User tidak perlu menjelaskan detail teknis jika kedua agen sudah membaca dokumen ini.

---

## 2. Single Source of Truth (SSOT)

Sebelum mengerjakan apapun, setiap agen **WAJIB** membaca:

| File | Fungsi |
|---|---|
| `AGENTS_RULES.md` | Dokumen ini — aturan kerja antar agen |
| `AGENTS.md` | Aturan teknis project (architecture, business rules, constraints) |
| `feature_list.json` | Status semua fitur (passes, needs_device, notes) |
| `progress.md` | Riwayat kerja harian |
| `docs/open-questions.md` | Temuan hardware/UX yang belum terselesaikan |

Setelah mengerjakan apapun, setiap agen **WAJIB** mengupdate:

- `feature_list.json` — ubah `passes` hanya setelah verifikasi.
- `progress.md` — tambahkan baris baru dengan tanggal, fitur, dan catatan.
- `docs/open-questions.md` — tambahkan temuan baru atau tandai selesai.

---

## 3. Mode "Cek Progress & Rencana Selanjutnya"

Ketika user bertanya: **"cek progress terakhir"** atau **"apa selanjutnya?"**, agen **WAJIB** melakukan hal berikut:

1. Baca `feature_list.json`, `progress.md`, `docs/open-questions.md`, `AGENTS_RULES.md`, dan `AGENTS.md`.
2. Hitung status per milestone (M0, M1, M2, M3, M4).
3. Identifikasi bottleneck dan fitur dengan `"needs_device": true`.
4. Usulkan **2-3 langkah selanjutnya** yang paling bernilai.
5. Jelaskan setiap langkah: **siapa yang mengerjakan** (CodeBuddy atau Android Studio AI Agent), dan **mengapa**.
6. **Tanyakan persetujuan user sebelum eksekusi**.

### Format Laporan Wajib

```markdown
## Ringkasan Status A3 POS

| Milestone | Progress | Catatan |
|---|---|---|
| M0 | X/Y selesai | ... |
| M1 | X/Y selesai | ... |
| M2 | belum mulai | ... |
| ... | ... | ... |

## Blocker Utama
1. ...
2. ...

## Rekomendasi Prioritas (pilih satu)
1. **[P1]** Verifikasi fitur X di device — Android Studio AI Agent
2. **[P2]** Implementasi fitur Y — CodeBuddy
3. **[P3]** ...

## Tindakan yang Diusulkan
**Jalankan opsi [nomor].** Jawab "ya, lanjutkan [nomor]" untuk memulai eksekusi.
```

---

## 4. Pembagian Tugas Detail

### CodeBuddy bertanggung jawab untuk:

- Setup dan refactoring Gradle multi-module.
- Arsitektur `:core:domain`, `:core:data`, `:core:sync`, `:core:auth`, `:core:printer`, `:core:ui`.
- UI design system: theme, typography, shared components.
- Unit test, Robolectric test, mock server validation.
- Refactor hasil spike menjadi production-ready code.
- Code review dan audit best practice.
- Menyusun rencana implementasi fitur berikutnya.
- Menentukan kapan fitur dinyatakan selesai secara keseluruhan.

### Android Studio AI Agent bertanggung jawab untuk:

- Deploy APK ke Redmi Pad 2.
- Uji fitur yang membutuhkan device fisik (`"needs_device": true`).
- Debug hardware: iWare C58BT printer (Bluetooth/USB), kamera QR.
- Verifikasi WorkManager background sync di HyperOS 2 / Android 16.
- Mengambil logcat, screenshot, dan video jika perlu.
- Update `feature_list.json` dan `progress.md` setelah uji hardware.
- Melaporkan hasil dalam format yang sudah ditentukan.

---

## 5. Kapan Beralih ke Agen Lain?

### CodeBuddy akan mengarahkan ke Android Studio AI Agent jika:

- Fitur memiliki `"needs_device": true` dan belum diverifikasi.
- Perlu logcat dari Redmi Pad 2.
- Perlu deploy APK untuk smoke test.
- Perlu uji printer/camera/bluetooth secara fisik.
- Code sudah siap diuji tapi membutuhkan validasi hardware.

### Android Studio AI Agent akan mengarahkan ke CodeBuddy jika:

- Menemukan bug yang berhubungan dengan arsitektur atau logic.
- Perlu refactor besar.
- Perlu menambah unit test.
- Perlu membuat shared component/UI.
- Hardware test berhasil dan code perlu dipindahkan dari `:app` spike ke modul production.

---

## 6. Format Laporan dari Masing-Masing Agen

### Laporan dari Android Studio AI Agent (setelah uji hardware)

```markdown
| Tanggal | Fitur | Device | Hasil | Catatan |
|---|---|---|---|---|
| YYYY-MM-DD | F04 | Redmi Pad 2 | ✅ PASS | QR terbaca dalam 1 detik pada cahaya terang |
```

Update `feature_list.json`:

```json
{
  "id": "F04",
  "passes": true,
  "notes": "QR terbaca dalam 1 detik pada cahaya terang"
}
```

### Laporan dari CodeBuddy (setelah selesai coding)

```markdown
| Tanggal | Ringkasan | Fitur (id) | Catatan |
|---|---|---|---|
| YYYY-MM-DD | Refactor spike QR ke :feature:laundry | F21 | Komponen QrScanner reusable siap uji device |
```

---

## 7. Larangan

1. **Jangan ubah `feature_list.json` tanpa bukti.**
   - `passes` hanya boleh `true` setelah test lulus (unit test, mock server, atau device test).

2. **Jangan buat file produksi baru di `:app`.**
   - `:app` hanya untuk spike. Production code harus di modul yang benar (`:core:*`, `:feature:*`, `:app-cafe`, `:app-laundry`).

3. **Jangan menggunakan emoji di UI.**
   - Gunakan vector icon dari `androidx.compose.material.icons` atau custom vector.

4. **Jangan hardcode font size, color, atau shape.**
   - Gunakan `A3Theme.colors`, `A3Theme.shapes`, `A3Theme.typography`.

5. **Jangan lupakan empty/loading/error states** untuk setiap daftar.

6. **Jangan menambah dependency tanpa alasan tertulis.**
   - Cek versi library ke dokumentasi resmi.

---

## 8. Cara Bertanya ke User

Agen boleh bertanya ke user HANYA untuk:

- Menyetujui prioritas fitur berikutnya.
- Memutuskan trade-off arsitektur/UX yang signifikan.
- Mengklarifikasi requirement yang ambigu.

Agen **TIDAK** boleh bertanya untuk hal-hal yang sudah ada di dokumen SSOT ini.

---

## 9. Review Berkala

Setiap kali satu fitur selesai:

1. CodeBuddy review code.
2. Android Studio AI Agent review di device (jika perlu).
3. Kedua belah pihak update tracking file.
4. CodeBuddy yang menentukan kapan fitur bisa dinyatakan selesai secara keseluruhan.

---

## 10. Definisi "Selesai"

Sebuah fitur dianggap selesai jika:

1. Code terintegrasi di modul yang benar.
2. Unit test / mock test lulus (untuk logic).
3. Device test lulus (untuk `needs_device: true`).
4. `feature_list.json` dan `progress.md` sudah diupdate.
5. CodeBuddy menyetujui final merge.

---

## 11. Template Perintah untuk User

### Meminta laporan progress

> "Cek progress terakhir project A3. Baca feature_list.json, progress.md, docs/open-questions.md, AGENTS_RULES.md, dan AGENTS.md. Laporkan status, bottleneck, dan rencana selanjutnya. Jangan eksekusi dulu."

### Menyetujui eksekusi

> "Setuju, lanjutkan opsi [nomor]. Eksekusi dan update progress.md serta feature_list.json."

### Menolak atau mengalihkan ke CodeBuddy

> "Bagus analisanya. Tapi opsi [nomor] sebaiknya dikerjakan CodeBuddy karena melibatkan arsitektur/UI. Tolong arahkan ke CodeBuddy."

---

## 12. Catatan Akhir

- Kedua agen harus menganggap dokumen ini **hidup** dan boleh diupdate dengan persetujuan user.
- Jika terjadi konflik antara `AGENTS_RULES.md` dan `AGENTS.md`, yang menang adalah `AGENTS.md` untuk aturan teknis, dan `AGENTS_RULES.md` untuk aturan pembagian kerja.
- User tidak perlu menjelaskan ulang peran agen jika dokumen ini sudah dibaca dan dipatuhi.
