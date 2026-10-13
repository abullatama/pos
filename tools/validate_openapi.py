#!/usr/bin/env python3
"""Pemeriksaan kontrak: YAML terbaca, semua $ref terselesaikan, setiap tipe event punya tepat satu skema payload."""
import re, sys, pathlib
import yaml
path = pathlib.Path(__file__).resolve().parent.parent / "contracts" / "openapi.yaml"
text = path.read_text()
spec = yaml.safe_load(text)
errors = []
for ref in sorted(set(re.findall(r"\$ref: '#/([^']+)'", text))):
    node = spec
    for part in ref.split("/"):
        node = node.get(part) if isinstance(node, dict) else None
        if node is None:
            errors.append(f"ref tidak terselesaikan: {ref}"); break
ev = spec["components"]["schemas"]["Event"]
types = ev["properties"]["type"]["enum"]
mapping = ev["x-payload-by-type"]
oneof = [o["$ref"].split("/")[-1] for o in ev["properties"]["payload"]["oneOf"]]
for t in types:
    if t not in mapping: errors.append(f"event tanpa skema: {t}")
    elif mapping[t] not in spec["components"]["schemas"]: errors.append(f"skema hilang: {mapping[t]}")
    elif mapping[t] not in oneof: errors.append(f"skema tidak ada di oneOf: {mapping[t]}")
for t in mapping:
    if t not in types: errors.append(f"mapping untuk tipe yang tidak ada: {t}")
if len(set(mapping.values())) != len(mapping): errors.append("dua tipe memakai skema yang sama")
import json, jsonschema
def conv(n):
    if isinstance(n, dict):
        n = {k: conv(v) for k, v in n.items()}
        if n.get("nullable") and "type" in n: n["type"] = [n["type"], "null"]; n.pop("nullable")
        return n
    return [conv(x) for x in n] if isinstance(n, list) else n
comps = conv(spec["components"])
examples = json.loads((path.parent / "examples" / "events.json").read_text())["examples"]
for t in types:
    if t not in examples: errors.append(f"contoh hilang: {t}"); continue
    schema = {"$ref": f"#/components/schemas/{mapping[t]}", "components": comps}
    try: jsonschema.validate(examples[t], schema)
    except jsonschema.ValidationError as e: errors.append(f"contoh {t} tidak sah: {e.message}")
    others = [m for m in mapping.values() if m != mapping[t]]
    ok = []
    for m in others:
        try: jsonschema.validate(examples[t], {"$ref": f"#/components/schemas/{m}", "components": comps}); ok.append(m)
        except jsonschema.ValidationError: pass
    if ok: errors.append(f"contoh {t} juga cocok dengan skema lain (oneOf ambigu): {ok}")
print(f"path={len(spec['paths'])} skema={len(spec['components']['schemas'])} event={len(types)}")
print("OK" if not errors else "\n".join(errors))
sys.exit(1 if errors else 0)
