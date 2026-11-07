"""Optional read-only workbook audit. Normal builds never require the source XLSX."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
NS = {"x": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}


def workbook_rows(path):
    with ZipFile(path) as archive:
        shared = []
        if "xl/sharedStrings.xml" in archive.namelist():
            shared = ["".join(node.itertext()) for node in ET.fromstring(archive.read("xl/sharedStrings.xml"))]
        rels = {node.attrib["Id"]: node.attrib["Target"] for node in
                ET.fromstring(archive.read("xl/_rels/workbook.xml.rels"))}
        sheets = {}
        for sheet in ET.fromstring(archive.read("xl/workbook.xml")).findall("x:sheets/x:sheet", NS):
            if sheet.attrib["name"] not in ("Pool", "Engineering", "Selection"):
                continue
            target = rels[sheet.attrib["{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"]]
            target = target.lstrip("/") if target.startswith("/") else "xl/" + target
            rows = {}
            for row in ET.fromstring(archive.read(target)).findall("x:sheetData/x:row", NS):
                cells = {}
                for cell in row:
                    value = cell.find("x:v", NS)
                    text = "" if value is None else value.text or ""
                    if cell.attrib.get("t") == "s":
                        text = shared[int(text)]
                    elif cell.attrib.get("t") == "inlineStr":
                        text = "".join(t.text or "" for t in cell.findall(".//x:t", NS))
                    cells[re.sub(r"\d", "", cell.attrib["r"])] = text
                if cells.get("A", "").isdigit():
                    stable_id = int(cells["A"])
                    if stable_id in rows:
                        raise AssertionError(f"Duplicate numeric ID {stable_id} on {sheet.attrib['name']}")
                    rows[stable_id] = (int(row.attrib["r"]), cells)
            sheets[sheet.attrib["name"]] = rows
        return sheets


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("workbook", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "build/achievement-phase9-source-audit.json")
    args = parser.parse_args()
    catalog = json.loads((ROOT / "docs/achievements/catalog.json").read_text(encoding="utf-8"))
    rows = workbook_rows(args.workbook)
    digest = hashlib.sha256(args.workbook.read_bytes()).hexdigest()
    assert digest == catalog["provenance"]["sha256"], "Workbook hash differs from approved catalog provenance"
    ids = {entry["id"] for entry in catalog["achievements"]}
    assert len(ids) == len(catalog["achievements"]) == 69
    for name in ("Pool", "Engineering", "Selection"):
        assert set(rows[name]) == ids, f"Selected ID set differs on {name}"
    checked = []
    for entry in catalog["achievements"]:
        stable_id = entry["id"]
        source = {name: rows[name][stable_id][1] for name in ("Pool", "Engineering", "Selection")}
        assert entry["name"] == source["Pool"].get("B", ""), (stable_id, "name")
        assert entry["action"] == source["Pool"].get("Q", ""), (stable_id, "action")
        for key, sheet, col in (("pool", "Pool", "R"), ("poolImplementation", "Pool", "AE"),
                                ("engineering", "Engineering", "D"), ("constraints", "Engineering", "P"),
                                ("engineeringImplementation", "Engineering", "Q"), ("selection", "Selection", "F")):
            assert entry["notes"][key] == source[sheet].get(col, ""), (stable_id, key)
        for sheet, row_number in entry["sourceRows"].items():
            assert row_number == rows[sheet][stable_id][0], (stable_id, sheet, "source row")
        capabilities = re.findall(r"[A-Z]+", source["Engineering"].get("C", ""))
        assert entry["capabilities"] == capabilities, (stable_id, "capabilities", capabilities)
        checked.append({"id": stable_id, "resourceId": entry["resourceId"], "name": entry["name"],
                        "action": entry["action"], "sourceRows": entry["sourceRows"]})
    assert len({cap for entry in catalog["achievements"] for cap in entry["capabilities"]}) == 16
    result = {"result": "PASS", "workbookSha256": digest, "selectedIds": len(ids), "capabilities": 16,
              "exactNames": 69, "exactActions": 69, "annotationCells": 414, "sourceRowReferences": 207,
              "entries": checked}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in result.items() if key != "entries"}))


if __name__ == "__main__":
    main()
