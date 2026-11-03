"""Read-only structural checks of the application's own synthetic score exports."""
import csv
import io
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parent
csv_rows = list(csv.reader(io.StringIO((root / "synthetic-exam-score.csv").read_text(encoding="utf-8-sig"))))
with zipfile.ZipFile(root / "synthetic-exam-score.xlsx") as archive:
    ns = {"s": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
    shared = []
    if "xl/sharedStrings.xml" in archive.namelist():
        document = ET.fromstring(archive.read("xl/sharedStrings.xml"))
        shared = ["".join(item.itertext()) for item in document.findall("s:si", ns)]
    sheet = ET.fromstring(archive.read("xl/worksheets/sheet1.xml"))
    xlsx_rows = []
    for row in sheet.findall(".//s:sheetData/s:row", ns):
        values = []
        for cell in row.findall("s:c", ns):
            value = cell.find("s:v", ns)
            if cell.attrib.get("t") == "s" and value is not None:
                values.append(shared[int(value.text)])
            elif cell.attrib.get("t") == "inlineStr":
                values.append("".join(cell.find("s:is", ns).itertext()))
            else:
                values.append(value.text if value is not None else "")
        xlsx_rows.append(values)
result = {
    "csvRows": len(csv_rows),
    "xlsxRows": len(xlsx_rows),
    "columns": len(csv_rows[0]),
    "headersEqual": csv_rows[0] == xlsx_rows[0],
    "singleDemoSeedStudent": len(csv_rows) == 2 and csv_rows[1][0] == "3",
    "csvScore95": csv_rows[1][3] == "95",
    "xlsxScore95": float(xlsx_rows[1][3]) == 95,
    "testExamPrefix": csv_rows[1][2].startswith("other-20261103"),
    "scope": "Structural content inspection only; no visual rendering or formula recalculation claim.",
}
(root / "export-inspection.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
print(json.dumps(result))
assert all(result[k] for k in ["headersEqual", "singleDemoSeedStudent", "csvScore95", "xlsxScore95", "testExamPrefix"])
