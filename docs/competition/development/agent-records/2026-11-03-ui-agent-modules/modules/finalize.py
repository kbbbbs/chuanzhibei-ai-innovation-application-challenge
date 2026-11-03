"""Generate case counts and hashes of the bounded evidence snapshot."""
import hashlib
import json
from pathlib import Path
import re

root = Path(__file__).resolve().parent
text = (root / "TEST-REPORT.md").read_text(encoding="utf-8")
cases = [
    {"id": match[0], "status": match[1], "scope": match[2].strip()}
    for match in re.findall(r"^\| ([A-Z]{2}\d{2}) \| (PASS|FAIL|BLOCKED|NOT_RUN) \| (.+) \|$", text, re.MULTILINE)
]
counts = {status: sum(case["status"] == status for case in cases) for status in ["PASS", "FAIL", "BLOCKED", "NOT_RUN"]}
assert len(cases) == 24 and counts == {"PASS": 19, "FAIL": 1, "BLOCKED": 1, "NOT_RUN": 3}
(root / "results.json").write_text(json.dumps({"cases": cases, "counts": counts}, ensure_ascii=False, indent=2), encoding="utf-8")
hashes = {
    str(file.relative_to(root)): hashlib.sha256(file.read_bytes()).hexdigest()
    for file in sorted(root.rglob("*"))
    if file.is_file() and file.name != "sha256.json"
}
(root / "sha256.json").write_text(json.dumps(hashes, indent=2), encoding="utf-8")
print(json.dumps({"counts": counts, "hashedFiles": len(hashes)}))
