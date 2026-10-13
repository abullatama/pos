#!/usr/bin/env python3
"""Acuan referensi rumus uang. Memeriksa contracts/test-vectors.json dengan bilangan bulat saja."""
import json, sys, pathlib
from math import ceil

def pct(amount, p):            # persen per komponen, setengah naik
    return (amount * p * 2 + 100) // 200

def round100(x):               # kelipatan 100, setengah naik
    return ((x + 50) // 100) * 100

def cafe(inp):
    sub = sum(l["unit_price"] * l["quantity"] for l in inp["lines"])
    d = inp.get("discount")
    disc = 0 if not d else (pct(sub, d["value"]) if d["type"] == "percent" else min(d["value"], sub))
    dpp = sub - disc
    svc = pct(dpp, inp["service_percent"])
    if inp["tax_mode"] == "added":
        tax = pct(dpp + svc, inp["tax_percent"])
        before = dpp + svc + tax
    else:                       # included: harga sudah memuat pajak (usulan)
        before = dpp + svc
        tax = (before * inp["tax_percent"] * 2 + (100 + inp["tax_percent"])) // (2 * (100 + inp["tax_percent"]))
    total = round100(before)
    return {"subtotal": sub, "discount": disc, "service_charge": svc, "tax": tax, "rounding": total - before, "total": total}

def laundry(inp):
    if "lines" in inp:
        billed, totals = [], []
        for l in inp["lines"]:
            if l["unit"] == "kg":
                halves = ceil(round(l["weight"] / l["step"], 9))
                w = max(l["min_weight"], halves * l["step"])
                billed.append(w); totals.append(round(w * l["rate"]))
            else:
                totals.append(l["quantity"] * l["rate"])
        return {"billed_weights": billed, "line_totals": totals, "total": sum(totals)}
    rem = inp["total"] - sum(inp["payments"])
    return {"remaining": rem, "payment_status": "dp" if 0 < sum(inp["payments"]) < inp["total"] else "paid", "after_full_payment": "paid"}

def cash(inp):
    if "tendered" in inp:
        return {"change": inp["tendered"] - inp["total"]}
    return {"expected_cash": inp["opening_cash"] + inp["cash_sales"] + inp["cash_dp_and_settlement"] - inp["cash_refunds"] - inp["cash_outs"]}

def main():
    path = pathlib.Path(__file__).resolve().parent.parent / "contracts" / "test-vectors.json"
    data = json.loads(path.read_text())
    bad = 0
    for group, fn in (("cafe", cafe), ("laundry", laundry), ("cash", cash)):
        for c in data[group]:
            got = fn(c["input"])
            ok = all(got.get(k) == v for k, v in c["expected"].items())
            print(("OK  " if ok else "FAIL"), c["id"], c["status"], "" if ok else f"got={got} expected={c['expected']}")
            bad += not ok
    sys.exit(1 if bad else 0)

main()
