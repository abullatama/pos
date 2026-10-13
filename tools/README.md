# tools

- `verify_vectors.py`: memeriksa `contracts/test-vectors.json` dengan perhitungan bilangan bulat referensi. Jalankan `python3 -I tools/verify_vectors.py`. Bila rumus di `core:domain` diubah, ubah vektor dan skrip ini bersama-sama, lalu minta persetujuan manusia.
- `validate_openapi.py`: memeriksa `contracts/openapi.yaml` (YAML, semua `$ref`, tiap tipe event punya tepat satu skema) dan memvalidasi `contracts/examples/events.json` terhadap skema. Butuh `pyyaml` dan `jsonschema`.
