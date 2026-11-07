"""Validate phase-9 per-ID evidence references against the approved catalog and test source."""
from __future__ import annotations

import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]


def main():
    catalog = json.loads((ROOT / "docs/achievements/catalog.json").read_text(encoding="utf-8"))
    tests = {}
    for path in (ROOT / "common/src/test").rglob("*.java"):
        tests.setdefault(path.stem, []).append(path)
    evidence = {}
    references = set()
    for name in ("STATUS.md",):
        text = (ROOT / "docs/achievements" / name).read_text(encoding="utf-8")
        for line in text.splitlines():
            match = re.match(r"\| (\d+) \| (.*?) \| (.*?) \|$", line)
            if match:
                stable_id = int(match[1])
                assert stable_id not in evidence, f"Duplicate evidence owner for {stable_id}"
                evidence[stable_id] = {"automated": match[2], "nativeScope": match[3], "report": name}
        references.update(re.findall(r"\b([A-Za-z]+(?:Test|Fixture))\.([a-z][A-Za-z0-9]*)\b", text))
    for class_name, method in sorted(references):
        assert class_name in tests, f"Missing cited test class {class_name}"
        assert any(re.search(r"\b" + re.escape(method) + r"\s*\(", path.read_text(encoding="utf-8"))
                   for path in tests[class_name]), f"Missing cited method {class_name}.{method}"
    ids = {entry["id"] for entry in catalog["achievements"]}
    assert set(evidence) == ids and len(evidence) == 69, "Evidence must cover each approved ID once"
    result = {"result": "PASS", "selectedIds": len(ids), "verifiedMethodReferences": len(references),
              "scope": "Traceability validation only; actual test executions are recorded separately",
              "entries": [{"id": entry["id"], "resourceId": entry["resourceId"], **evidence[entry["id"]]}
                          for entry in catalog["achievements"]]}
    output = ROOT / "build/achievement-phase9-coverage.json"
    output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in result.items() if key != "entries"}))


if __name__ == "__main__":
    main()
