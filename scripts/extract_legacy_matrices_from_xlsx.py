#!/usr/bin/env python3
"""
Converte a planilha oficial `Matrizes Curriculares UFOP (2026_2).xlsx` em um
JSON leve e estruturado (`android-app/app/src/main/assets/ufop_matrizes_antigas.json`)
para integração de matrizes curriculares antigas, faixas de vigência por ano/semestre
de ingresso e referências oficiais (PDFs/PPCs/equivalências) sem alterar o contrato
de `ufop_matrizes_2026_2.json`.

Não exige dependências externas (usa apenas biblioteca padrão Python).

Uso:
    python scripts/extract_legacy_matrices_from_xlsx.py
    python scripts/extract_legacy_matrices_from_xlsx.py --xlsx "/caminho/para/planilha.xlsx"
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import unicodedata
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_XLSX = Path(
    os.environ.get(
        "UFOP_MATRICES_XLSX",
        str(Path.home() / "Downloads" / "Matrizes Curriculares UFOP (2026_2).xlsx"),
    )
)
DEFAULT_BASE_JSON = (
    REPO_ROOT / "android-app" / "app" / "src" / "main" / "assets" / "ufop_matrizes_2026_2.json"
)
DEFAULT_OUTPUT_JSON = (
    REPO_ROOT / "android-app" / "app" / "src" / "main" / "assets" / "ufop_matrizes_antigas.json"
)

NS = {
    "main": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
}
HYPERLINK_RE = re.compile(r'HYPERLINK\(\s*"([^"]+)"\s*,\s*"([^"]*)"\s*\)', re.IGNORECASE)
PERIOD_RE = re.compile(r"(\d{1,2})\s*º")

# Mapeamento sincronizado com build_ufop_curriculum_asset.py
COURSE_PREFIX_TO_DEPT: dict[str, tuple[str, str]] = {
    "ABI": ("deabi", "DEABI"),
    "ACL": ("deacl", "DEACL"),
    "ALI": ("deali", "DEALI"),
    "AMB": ("deamb", "DEAMB"),
    "ARQ": ("dearq", "DEARQ"),
    "ART": ("deart", "DEART"),
    "BIO": ("debio", "DEBIO"),
    "BEV": ("debio", "DEBIO"),
    "CAD": ("decad", "DECAD"),
    "ADM": ("decad", "DECAD"),
    "CAT": ("decat", "DECAT"),
    "CBI": ("decbi", "DECBI"),
    "CEA": ("decea", "DECEA"),
    "CGP": ("decgp", "DECGP"),
    "CIV": ("deciv", "DECIV"),
    "BCC": ("decom", "DECOM"),
    "COM": ("decom", "DECOM"),
    "CPA": ("decpa", "DECPA"),
    "CSI": ("decsi", "DECSI"),
    "CSO": ("decso", "DECSO"),
    "DIR": ("dedir", "DEDIR"),
    "ECO": ("deeco", "DEECO"),
    "PED": ("deedu", "DEEDU"),
    "EDU": ("deedu", "DEEDU"),
    "MIF": ("deedu", "DEEDU"),
    "EFD": ("deefd", "DEEFD"),
    "EDF": ("deefd", "DEEFD"),
    "ELT": ("deelt", "DEELT"),
    "EMA": ("deema", "DEEMA"),
    "ENP": ("deenp", "DEENP"),
    "EST": ("deest", "DEEST"),
    "ETE": ("deete", "DEETE"),
    "DTE": ("deete", "DEETE"),
    "EAD": ("deete", "DEETE"),
    "FAR": ("defar", "DEFAR"),
    "FIL": ("defil", "DEFIL"),
    "FIS": ("defis", "DEFIS"),
    "GEO": ("degeo", "DEGEO"),
    "GEP": ("degep", "DEGEP"),
    "HIS": ("dehis", "DEHIS"),
    "ISH": ("deish", "DEISH"),
    "JOR": ("dejor", "DEJOR"),
    "LET": ("delet", "DELET"),
    "MTM": ("demat", "DEMAT"),
    "MAT": ("demat", "DEMAT"),
    "MEC": ("demec", "DEMEC"),
    "MET": ("demet", "DEMET"),
    "MIN": ("demin", "DEMIN"),
    "MED": ("demsc", "DEMSC"),
    "MSC": ("demsc", "DEMSC"),
    "MUL": ("demul", "DEMUL"),
    "MUS": ("demus", "DEMUS"),
    "NUT": ("dencs", "DENCS"),
    "NCS": ("dencs", "DENCS"),
    "PRO": ("depro", "DEPRO"),
    "EPR": ("depro", "DEPRO"),
    "QUI": ("dequi", "DEQUI"),
    "SSO": ("desso", "DESSO"),
    "TUR": ("detur", "DETUR"),
    "URB": ("deurb", "DEURB"),
    "ATV": ("ufop", "ATIVIDADES"),
    "ATC": ("ufop", "ATIVIDADES"),
    "ATE": ("ufop", "ATIVIDADES"),
    "TCC": ("deart", "DEART"),
}

# Mapeamento explícito dos nomes de curso da aba "Matrizes Antigas e PPCs"
# para os códigos oficiais dos 54 cursos em `ufop_matrizes_2026_2.json`.
LEGACY_COURSE_NAME_TO_CODES: dict[str, list[str]] = {
    "farmacia": ["FAR"],
    "medicina": ["MED"],
    "engenharia ambiental": ["AMB"],
    "engenharia de minas": ["MIN"],
    "engenharia de controle e automacao": ["AUT"],
    "engenharia de producao": ["PRO"],
    "engenharia geologica": ["GEO"],
    "matematica (licenciatura)": ["MTL"],
    "matematica (bacharelado)": ["MTB"],
    "fisica (bacharelado)": ["FSB"],
    "inteligencia artificial": ["IA"],
    "quimica (licenciatura)": ["QLI"],
    "quimica industrial": ["QUI"],
    "engenharia de computacao": ["CJM"],
    "engenharia eletrica": ["EJM"],
    "sistemas de informacao": ["SJM"],
    "letras portugues (ltp)": ["LTP"],
    "letras ingles (lti)": ["LTI"],
    "letras licenciaturas (colet lic)": ["LTP", "LTI"],
    "letras bacharelados (colet bac)": ["LTE", "LTT"],
    "pedagogia (coped)": ["PED"],
    "jornalismo": ["JOR"],
    "administracao": ["ADM"],
    "nutricao": ["NUT"],
    "artes cenicas (licenciatura)": ["ACL"],
    "filosofia (bacharelado)": ["FLB"],
    "filosofia (licenciatura)": ["FLL"],
    "museologia": ["MUL"],
    "turismo": ["TUR"],
    "direito": ["DIR"],
    "educacao fisica (bacharelado cobef)": ["EFB"],
    "educacao fisica (licenciatura colef)": ["EFL"],
    "musica (licenciatura)": ["MUS"],
    "historia (licenciatura)": ["HIL"],
    "historia (bacharelado)": ["HIB"],
    "ciencias economicas": ["ECO"],
    "ciencia da computacao": ["COM"],
    "estatistica": ["EST"],
    "administracao publica (ead)": ["D06"],
    "administracao publica (presencial)": ["D06"],
}


def normalize_key(text: str) -> str:
    nfd = unicodedata.normalize("NFD", text.strip())
    no_marks = "".join(ch for ch in nfd if unicodedata.category(ch) != "Mn")
    cleaned = re.sub(r"[^a-zA-Z0-9()]+", " ", no_marks).lower()
    cleaned = re.sub(r"\(\s+", "(", cleaned)
    cleaned = re.sub(r"\s+\)", ")", cleaned)
    return re.sub(r"\s+", " ", cleaned).strip()


def slugify(text: str) -> str:
    nfd = unicodedata.normalize("NFD", text.strip())
    no_marks = "".join(ch for ch in nfd if unicodedata.category(ch) != "Mn")
    return re.sub(r"[^a-z0-9]+", "_", no_marks.lower()).strip("_")


def resolve_department(subject_code: str) -> tuple[str, str]:
    upper = subject_code.strip().upper()
    prefix = re.match(r"^[A-Z]+", upper)
    p = prefix.group(0) if prefix else ""
    if p in COURSE_PREFIX_TO_DEPT:
        return COURSE_PREFIX_TO_DEPT[p]
    return ("ufop", p if p else "UFOP")


def parse_xlsx(path: Path) -> dict[str, list[tuple[int, dict[str, dict[str, str | None]]]]]:
    sheets: dict[str, list[tuple[int, dict[str, dict[str, str | None]]]]] = {}
    with zipfile.ZipFile(path) as z:
        shared_strings: list[str] = []
        if "xl/sharedStrings.xml" in z.namelist():
            root = ET.fromstring(z.read("xl/sharedStrings.xml"))
            for si in root.findall("main:si", NS):
                shared_strings.append(
                    "".join(
                        t.text or ""
                        for t in si.iter("{http://schemas.openxmlformats.org/spreadsheetml/2006/main}t")
                    )
                )

        wb = ET.fromstring(z.read("xl/workbook.xml"))
        rels = ET.fromstring(z.read("xl/_rels/workbook.xml.rels"))
        rid_to_target = {rel.attrib["Id"]: rel.attrib["Target"] for rel in rels}

        for sh in wb.findall(".//main:sheet", NS):
            name = sh.attrib["name"]
            rid = sh.attrib["{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"]
            target = rid_to_target[rid]
            if not target.startswith("xl/"):
                target = "xl/" + target.lstrip("/")

            sroot = ET.fromstring(z.read(target))
            rows: list[tuple[int, dict[str, dict[str, str | None]]]] = []
            for r in sroot.findall(".//main:sheetData/main:row", NS):
                rnum = int(r.attrib.get("r", 0))
                row: dict[str, dict[str, str | None]] = {}
                for c in r.findall("main:c", NS):
                    ref = c.attrib.get("r", "")
                    col = "".join(ch for ch in ref if ch.isalpha())
                    t = c.attrib.get("t")
                    v = c.find("main:v", NS)
                    f = c.find("main:f", NS)
                    val: str | None = None
                    if t == "s" and v is not None and v.text is not None:
                        val = shared_strings[int(v.text)]
                    elif t == "inlineStr":
                        is_el = c.find("main:is", NS)
                        if is_el is not None:
                            val = "".join(
                                el.text or ""
                                for el in is_el.iter(
                                    "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}t"
                                )
                            )
                    elif v is not None:
                        val = v.text

                    url: str | None = None
                    if f is not None and f.text:
                        m = HYPERLINK_RE.search(f.text)
                        if m:
                            url = m.group(1).strip()
                            if not val:
                                val = m.group(2).strip()

                    if val is not None or url is not None:
                        row[col] = {
                            "v": val.strip() if isinstance(val, str) else val,
                            "url": url,
                        }
                if row:
                    rows.append((rnum, row))
            sheets[name] = rows
    return sheets


def infer_validity_range(
    course_codes: list[str],
    version_title: str,
    details: str,
    base_courses: dict[str, dict[str, Any]],
) -> dict[str, Any]:
    """
    Infere faixa de vigência (ano/semestre de ingresso) a partir do título e detalhes
    da referência histórica, sem inventar dados quando não há semestre explícito.
    """
    combined = f"{version_title} {details}"
    lower = combined.lower()

    # Regras explícitas verificadas diretamente na planilha:
    if course_codes == ["FAR"]:
        return {
            "effectiveFromYear": None,
            "effectiveFromSemester": None,
            "effectiveUntilYear": 2026,
            "effectiveUntilSemester": 1,
            "entryRuleSummary": "Ingressantes até 2026/1 (Currículos 3 e 4 / 2024/1, antes do Currículo 5 de 2026/2)",
        }
    if course_codes == ["AUT"]:
        return {
            "effectiveFromYear": 2024,
            "effectiveFromSemester": 1,
            "effectiveUntilYear": 2025,
            "effectiveUntilSemester": 2,
            "entryRuleSummary": "Ingressantes até 2025/2 (Currículo 2, antes do Currículo 3 de 2026/1+)",
        }
    if course_codes == ["AMB"] and "2016/2" in combined:
        return {
            "effectiveFromYear": 2016,
            "effectiveFromSemester": 2,
            "effectiveUntilYear": 2026,
            "effectiveUntilSemester": 1,
            "entryRuleSummary": "Grade 2016/2 (ingressantes 2016/2 até 2026/1)",
        }
    if course_codes == ["MIN"] and "2013/2" in combined:
        return {
            "effectiveFromYear": 2013,
            "effectiveFromSemester": 2,
            "effectiveUntilYear": 2026,
            "effectiveUntilSemester": 1,
            "entryRuleSummary": "Matriz 2013/2 (ingressantes 2013/2 até 2026/1)",
        }
    if course_codes == ["FSB"] and "2021/1" in combined:
        return {
            "effectiveFromYear": None,
            "effectiveFromSemester": None,
            "effectiveUntilYear": 2026,
            "effectiveUntilSemester": 1,
            "entryRuleSummary": "Currículo 2 (antes de 2021/1) e Currículo 3 (desde 2021/1)",
        }
    if course_codes == ["JOR"] and "2015.1" in combined:
        return {
            "effectiveFromYear": None,
            "effectiveFromSemester": None,
            "effectiveUntilYear": 2026,
            "effectiveUntilSemester": 1,
            "entryRuleSummary": "PPC até 2015/1 (2.700h) e PPC a partir de 2015/2 (3.000h)",
        }
    if course_codes == ["MUL"] and "2022" in combined:
        return {
            "effectiveFromYear": None,
            "effectiveFromSemester": None,
            "effectiveUntilYear": 2026,
            "effectiveUntilSemester": 1,
            "entryRuleSummary": "Currículo 1 (ingresso até 2022/2) e Matriz a partir de 2023/1",
        }
    if course_codes == ["TUR"] and "2018/2" in combined:
        return {
            "effectiveFromYear": 2018,
            "effectiveFromSemester": 2,
            "effectiveUntilYear": None,
            "effectiveUntilSemester": None,
            "entryRuleSummary": "Matriz 2018/2 (Currículo 3 — vigente no catálogo atual)",
        }
    if course_codes == ["D06"] and "2021/2" in combined:
        return {
            "effectiveFromYear": 2021,
            "effectiveFromSemester": 2,
            "effectiveUntilYear": None,
            "effectiveUntilSemester": None,
            "entryRuleSummary": "Currículo 3 — 2021/2 (EaD, 3.120h)",
        }
    if set(course_codes) == {"LTE", "LTT"} and "2019/2" in combined:
        return {
            "effectiveFromYear": 2019,
            "effectiveFromSemester": 2,
            "effectiveUntilYear": 2026,
            "effectiveUntilSemester": 1,
            "entryRuleSummary": "Reformulação curricular 2019/2 e fluxogramas históricos",
        }

    # Fallback genérico baseado na versão atual do curso no catálogo 2026/2
    first_base = base_courses.get(course_codes[0]) if course_codes else None
    base_ver = (first_base or {}).get("curriculumVersion", "")
    until_year = 2026 if "2026/2" in base_ver else None
    until_sem = 1 if until_year == 2026 else None
    if "currículo anterior" in lower or "currículos" in lower or "equivalência" in lower:
        summary = "Referência para matrizes anteriores à atualização vigente"
    else:
        summary = "Página/PPC oficial do curso para consulta de matrizes e equivalências"

    return {
        "effectiveFromYear": None,
        "effectiveFromSemester": None,
        "effectiveUntilYear": until_year,
        "effectiveUntilSemester": until_sem,
        "entryRuleSummary": summary,
    }


def extract_farmacia_curriculo_4(
    sheets: dict[str, list[tuple[int, dict[str, dict[str, str | None]]]]],
    base_courses: dict[str, dict[str, Any]],
) -> dict[str, Any]:
    rows = sheets["Farmácia (Currículo 4 - EFAR)"]
    subjects: list[dict[str, Any]] = []
    seen_codes: set[str] = set()
    source_url = "https://escoladefarmacia.ufop.br/grade-curricular"

    for rnum, row in rows:
        if rnum < 8:
            continue
        raw_type = (row.get("A", {}).get("v") or "").strip()
        raw_period = (row.get("B", {}).get("v") or "").strip()
        code = (row.get("C", {}).get("v") or "").strip().upper()
        name = (row.get("D", {}).get("v") or "").strip()
        if not raw_type or raw_type.lower().startswith("total"):
            continue
        if not code or not name or code in seen_codes:
            continue
        seen_codes.add(code)

        if row.get("E", {}).get("url"):
            source_url = row["E"]["url"] or source_url

        m_per = PERIOD_RE.search(raw_period)
        period = int(m_per.group(1)) if m_per else 0
        dept_id, dept_code = resolve_department(code)

        subjects.append(
            {
                "code": code,
                "name": name,
                "departmentId": dept_id,
                "departmentCode": dept_code,
                "period": period,
                "type": raw_type,
                "chs": 0,
                "che": 0,
                "cha": 0,
                "weeklyTheory": 0,
                "weeklyPractice": 0,
                "prerequisites": [],
            }
        )

    subjects.sort(
        key=lambda s: (
            s["type"] != "Obrigatória",
            s["period"] == 0,
            s["period"],
            s["code"],
        )
    )

    far_base = base_courses.get("FAR", {})
    return {
        "code": "FAR",
        "name": far_base.get("name", "Farmácia"),
        "degree": far_base.get("degree", "Bacharelado"),
        "campus": far_base.get("campus", "Ouro Preto"),
        "curriculumVersion": "2024/1 (Currículo 4)",
        "chMandatory": 0,
        "chOptional": 0,
        "chElective": 0,
        "chExtension": 0,
        "chActivities": 0,
        "chTotal": 0,
        "status": "Vigente para ingressantes até 2026/1 (Grade EFAR — sem CH individual na fonte)",
        "effectiveFromYear": 2018,
        "effectiveFromSemester": 1,
        "effectiveUntilYear": 2026,
        "effectiveUntilSemester": 1,
        "workloadIncomplete": True,
        "sourceUrl": source_url,
        "subjects": subjects,
    }


def extract_current_course_links(
    sheets: dict[str, list[tuple[int, dict[str, dict[str, str | None]]]]]
) -> dict[str, dict[str, str | None]]:
    links: dict[str, dict[str, str | None]] = {}
    for rnum, row in sheets["Resumo dos Cursos"]:
        if rnum < 8:
            continue
        curso = row.get("A", {}).get("v") or ""
        code = (row.get("B", {}).get("v") or "").strip().upper()
        if not code or curso.lower().startswith("total"):
            continue
        links[code] = {
            "officialMatrixUrl": row.get("M", {}).get("url"),
            "drivePdfUrl": row.get("N", {}).get("url"),
            "sheetStatus": row.get("L", {}).get("v"),
        }
    return links


def extract_legacy_references(
    sheets: dict[str, list[tuple[int, dict[str, dict[str, str | None]]]]],
    base_courses: dict[str, dict[str, Any]],
) -> list[dict[str, Any]]:
    refs: list[dict[str, Any]] = []
    used_ids: set[str] = set()

    for rnum, row in sheets["Matrizes Antigas e PPCs"]:
        if rnum < 8:
            continue
        unit = (row.get("A", {}).get("v") or "").strip()
        course_label = (row.get("B", {}).get("v") or "").strip()
        if not course_label or unit.lower().startswith("total"):
            continue

        version_title = (row.get("C", {}).get("v") or "").strip()
        details = (row.get("D", {}).get("v") or "").strip()
        access_type = (row.get("E", {}).get("v") or "").strip()
        url = (row.get("F", {}).get("url") or "").strip()

        norm_name = normalize_key(course_label)
        course_codes = LEGACY_COURSE_NAME_TO_CODES.get(norm_name)
        if not course_codes:
            raise ValueError(
                f"Curso não mapeado na linha {rnum} de 'Matrizes Antigas e PPCs': "
                f"{course_label!r} (normalizado: {norm_name!r})"
            )

        for code in course_codes:
            if code not in base_courses:
                raise ValueError(f"Código de curso inválido '{code}' (ausente no catálogo base)")

        validity = infer_validity_range(course_codes, version_title, details, base_courses)

        base_id = slugify(f"{'_'.join(course_codes)}_{version_title}")[:64]
        ref_id = base_id
        suffix = 2
        while ref_id in used_ids:
            ref_id = f"{base_id}_{suffix}"
            suffix += 1
        used_ids.add(ref_id)

        refs.append(
            {
                "id": ref_id,
                "courseCodes": course_codes,
                "courseName": course_label,
                "academicUnit": unit,
                "versionTitle": version_title,
                "details": details,
                "accessType": access_type,
                "url": url,
                "effectiveFromYear": validity["effectiveFromYear"],
                "effectiveFromSemester": validity["effectiveFromSemester"],
                "effectiveUntilYear": validity["effectiveUntilYear"],
                "effectiveUntilSemester": validity["effectiveUntilSemester"],
                "entryRuleSummary": validity["entryRuleSummary"],
            }
        )

    return refs


def build_legacy_asset(xlsx_path: Path, base_json_path: Path, output_json_path: Path) -> dict[str, Any]:
    base_data = json.loads(base_json_path.read_text(encoding="utf-8"))
    base_courses = {c["code"].strip().upper(): c for c in base_data["courses"]}

    sheets = parse_xlsx(xlsx_path)

    historical_curricula = [extract_farmacia_curriculo_4(sheets, base_courses)]
    legacy_references = extract_legacy_references(sheets, base_courses)
    current_links = extract_current_course_links(sheets)

    mapped_codes = sorted({code for ref in legacy_references for code in ref["courseCodes"]})
    total_hist_subjects = sum(len(hc["subjects"]) for hc in historical_curricula)

    payload: dict[str, Any] = {
        "schemaVersion": 1,
        "source": "Matrizes Curriculares UFOP (2026_2).xlsx",
        "baseCatalogFile": base_json_path.name,
        "summary": {
            "historicalCurriculaCount": len(historical_curricula),
            "historicalSubjectsCount": total_hist_subjects,
            "legacyReferenceCount": len(legacy_references),
            "mappedCourseCodesCount": len(mapped_codes),
            "currentCatalogCoursesWithLinks": len(current_links),
        },
        "historicalCurricula": historical_curricula,
        "legacyReferences": legacy_references,
        "courseOfficialLinks": current_links,
    }

    output_json_path.parent.mkdir(parents=True, exist_ok=True)
    with output_json_path.open("w", encoding="utf-8", newline="\n") as fp:
        json.dump(payload, fp, ensure_ascii=False, separators=(",", ":"))

    return payload


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Extrai matrizes antigas e referências oficiais da planilha UFOP 2026/2 para JSON leve."
    )
    parser.add_argument("--xlsx", type=Path, default=DEFAULT_XLSX)
    parser.add_argument("--base-json", type=Path, default=DEFAULT_BASE_JSON)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT_JSON)
    args = parser.parse_args()

    payload = build_legacy_asset(args.xlsx, args.base_json, args.output)
    size_kb = args.output.stat().st_size / 1024.0
    s = payload["summary"]
    print(f"[OK] JSON leve gerado: {args.output}")
    print(
        f"[OK] Matrizes históricas completas: {s['historicalCurriculaCount']} "
        f"({s['historicalSubjectsCount']} componentes) | "
        f"Referências/PPCs antigos: {s['legacyReferenceCount']} "
        f"(cobrindo {s['mappedCourseCodesCount']} cursos) | "
        f"Links oficiais 2026/2: {s['currentCatalogCoursesWithLinks']} | "
        f"Tamanho: {size_kb:.1f} KB"
    )


if __name__ == "__main__":
    main()
