#!/usr/bin/env python3
"""
Extrai as 54 matrizes curriculares oficiais da UFOP (2026/2) a partir do painel
consolidado da PROGRAD e dos PDFs oficiais individuais das matrizes, gerando o
asset compacto JSON `android-app/app/src/main/assets/ufop_matrizes_2026_2.json`.

Uso:
    python scripts/build_ufop_curriculum_asset.py
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import re
import sys
from pathlib import Path
from typing import Any

import pdfplumber

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

DEFAULT_SUMMARY_PDF = Path(
    os.environ.get(
        "UFOP_SUMMARY_PDF",
        str(Path.home() / "Downloads" / "Matrizes Curriculares UFOP (2026-2).pdf"),
    )
)
DEFAULT_MATRICES_DIR = Path(
    os.environ.get("UFOP_MATRICES_DIR", str(Path.home() / "Downloads"))
)
DEFAULT_OUTPUT_JSON = (
    Path(__file__).resolve().parent.parent
    / "android-app"
    / "app"
    / "src"
    / "main"
    / "assets"
    / "ufop_matrizes_2026_2.json"
)

# Mapeamento oficial dos prefixos de código de disciplina para os 48 departamentos da UFOP
# (sincronizado com `courseCodePrefixToDeptId` em CampusContracts.kt + atividades curriculares).
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
    # Atividades curriculares oficiais
    "ATV": ("ufop", "ATIVIDADES"),
    "ATC": ("ufop", "ATIVIDADES"),
    "ATE": ("ufop", "ATIVIDADES"),
    # Disciplinas de pesquisa/TCC específicas (ex.: Artes Cênicas)
    "TCC": ("deart", "DEART"),
}

# Arquivos especiais cujo nome não segue o padrão `matriz<CODE><N>2026_2.pdf`
EXPLICIT_PDF_OVERRIDES: dict[str, str] = {
    "MTL": "matrizmtl42024_1_2.pdf",
    "MUS": "matriz_de_musica.pdf",
    "TUR": "matriz_curricular.pdf",
}

SUBJECT_CODE_RE = re.compile(r"^[A-Z]{2,4}\d{2,4}[A-Z]?$")
ACTIVITY_CODE_RE = re.compile(r"^[A-Z]{2,4}\d{0,4}[A-Z]?$")
MATRIX_2026_FILE_RE = re.compile(r"^matriz([A-Z0-9]{2,3})\d+2026_2\.pdf$", re.IGNORECASE)


def collapse_ws(text: str | None) -> str:
    if not text:
        return ""
    return re.sub(r"\s+", " ", text.replace("\n", " ")).strip()


def parse_pt_int(raw: str | None) -> int:
    clean = collapse_ws(raw)
    if not clean or clean in {"-", "—", "N/A"}:
        return 0
    digits = re.sub(r"[^\d]", "", clean)
    return int(digits) if digits else 0


def parse_chs_che(raw: str | None, is_activity: bool = False, name: str = "") -> tuple[int, int]:
    clean = collapse_ws(raw)
    if not clean or clean == "-":
        return (0, 0)
    if "/" in clean:
        left, right = clean.split("/", 1)
        return (parse_pt_int(left), parse_pt_int(right))
    chs = parse_pt_int(clean)
    if is_activity and ("EXTENS" in name.upper()):
        return (chs, chs)
    return (chs, 0)


def parse_prerequisites(raw: str | None) -> list[str]:
    clean = collapse_ws(raw)
    if not clean or clean in {"-", "—", "NENHUM"}:
        return []
    # Substituir vírgulas/ponto-e-vírgula por espaço para tokenização uniforme
    normalized = re.sub(r"[,;]+", " ", clean)
    raw_tokens = normalized.split()
    result: list[str] = []
    i = 0
    while i < len(raw_tokens):
        tok = raw_tokens[i].strip().upper()
        if not tok or tok in {"-", "E", "OU"}:
            i += 1
            continue
        # Ex.: "1500 horas" ou "1500"
        if tok.isdigit():
            if i + 1 < len(raw_tokens) and raw_tokens[i + 1].lower().startswith("hora"):
                i += 1
            formatted = f"{int(tok)}h"
            if formatted not in result:
                result.append(formatted)
        elif re.match(r"^\d+H$", tok):
            formatted = f"{int(tok[:-1])}h"
            if formatted not in result:
                result.append(formatted)
        elif re.match(r"^[A-Z]{2,4}\d{2,4}[A-Z]?$", tok):
            if tok not in result:
                result.append(tok)
        else:
            # Preservar caso algum pré-requisito especial apareça
            clean_tok = tok.strip("()[]")
            if clean_tok and clean_tok not in result:
                result.append(clean_tok)
        i += 1
    return result


def resolve_department(subject_code: str, is_activity: bool) -> tuple[str, str]:
    upper = subject_code.strip().upper()
    prefix = upper[:3]
    if is_activity and prefix in {"ATV", "ATC", "ATE"}:
        return ("ufop", "ATIVIDADES")
    if prefix in COURSE_PREFIX_TO_DEPT:
        return COURSE_PREFIX_TO_DEPT[prefix]
    if is_activity:
        return ("ufop", "ATIVIDADES")
    return ("ufop", prefix if prefix else "UFOP")


def parse_master_courses_page1(summary_pdf_path: Path) -> list[dict[str, Any]]:
    """Extrai os 54 cursos oficiais da página 1 do Painel de Matrizes Curriculares UFOP 2026/2."""
    courses: list[dict[str, Any]] = []
    with pdfplumber.open(str(summary_pdf_path)) as pdf:
        if not pdf.pages:
            raise RuntimeError(f"PDF vazio: {summary_pdf_path}")
        tables = pdf.pages[0].extract_tables() or []
        if not tables:
            raise RuntimeError(f"Nenhuma tabela encontrada na página 1 de {summary_pdf_path}")
        master_table = tables[0]

    header_seen = False
    for row in master_table:
        if not row or len(row) < 12:
            continue
        c0 = collapse_ws(row[0])
        c1 = collapse_ws(row[1])

        if not header_seen:
            if c0.lower() == "curso" and c1.lower().startswith("cód"):
                header_seen = True
            continue

        if not c0 or c0.lower().startswith("total geral"):
            continue

        # Corrige quebra de célula conhecida do PDF, ex.:
        # ['Engenharia de Produção (João Monlevade', ') PJM', ...]
        if c1.startswith(")"):
            c0 = f"{c0})"
            c1 = c1[1:].strip()

        # Corrige qualquer outro fechamento de parêntese ou espaço residual
        c0 = re.sub(r"\(\s+", "(", c0)
        c0 = re.sub(r"\s+\)", ")", c0)
        code = c1.upper().strip()

        degree = collapse_ws(row[2])
        campus = collapse_ws(row[3])
        curriculum_version = collapse_ws(row[4])
        if curriculum_version == "-":
            curriculum_version = ""

        ch_mandatory = parse_pt_int(row[5])
        ch_optional = parse_pt_int(row[6])
        ch_elective = parse_pt_int(row[7])
        ch_extension = parse_pt_int(row[8])
        ch_activities = parse_pt_int(row[9])
        ch_total = parse_pt_int(row[10])
        status = collapse_ws(row[11])

        courses.append(
            {
                "code": code,
                "name": c0,
                "degree": degree,
                "campus": campus,
                "curriculumVersion": curriculum_version,
                "chMandatory": ch_mandatory,
                "chOptional": ch_optional,
                "chElective": ch_elective,
                "chExtension": ch_extension,
                "chActivities": ch_activities,
                "chTotal": ch_total,
                "status": status,
                "subjects": [],
            }
        )

    return courses


def discover_matrix_pdfs(matrices_dir: Path) -> dict[str, Path]:
    """Mapeia código oficial de curso (3 letras / código UFOP) -> arquivo PDF 2026/2."""
    mapping: dict[str, Path] = {}

    # 1. Padrão oficial 2026/2: matriz<CODE><CURRICULO>2026_2.pdf
    for pdf_str in sorted(glob.glob(str(matrices_dir / "matriz*.pdf"))):
        pdf_path = Path(pdf_str)
        m = MATRIX_2026_FILE_RE.match(pdf_path.name)
        if m:
            code = m.group(1).upper()
            mapping[code] = pdf_path

    # 2. Overrides explícitos (MTL 2024/1, MUS 2019/1, TUR 2018/2)
    for code, filename in EXPLICIT_PDF_OVERRIDES.items():
        candidate = matrices_dir / filename
        if candidate.exists() and code not in mapping:
            mapping[code] = candidate

    return mapping


def parse_individual_matrix_pdf(pdf_path: Path) -> tuple[list[dict[str, Any]], dict[str, int]]:
    """
    Extrai disciplinas obrigatórias e atividades curriculares de um PDF individual
    de matriz da UFOP (formato oficial PROGRAD), além dos totais de carga horária.
    """
    subjects: list[dict[str, Any]] = []
    seen_codes: set[str] = set()
    summary_hours: dict[str, int] = {}

    with pdfplumber.open(str(pdf_path)) as pdf:
        for page in pdf.pages:
            for tbl in page.extract_tables() or []:
                if not tbl or not tbl[0]:
                    continue
                header_cells = [collapse_ws(c).upper() for c in tbl[0]]
                header_joined = " | ".join(header_cells)

                # Tabela de disciplinas obrigatórias
                if "CÓDIGO" in header_joined and "DISCIPLINAS OBRIGAT" in header_joined:
                    has_chs_che = any("CHS/CHE" in h for h in header_cells)
                    for row in tbl[1:]:
                        if not row or len(row) < 6:
                            continue
                        code = collapse_ws(row[0]).upper()
                        name = collapse_ws(row[1])
                        if not code or not name or not SUBJECT_CODE_RE.match(code):
                            continue
                        if code in seen_codes:
                            continue
                        seen_codes.add(code)

                        prereqs = parse_prerequisites(row[2] if len(row) > 2 else "")
                        if has_chs_che:
                            chs, che = parse_chs_che(row[3] if len(row) > 3 else "")
                        else:
                            chs = parse_pt_int(row[3] if len(row) > 3 else "")
                            che = 0
                        cha = parse_pt_int(row[4] if len(row) > 4 else "")
                        if cha == 0 and chs > 0:
                            cha = round(chs * 1.2)
                        weekly_t = parse_pt_int(row[5] if len(row) > 5 else "")
                        weekly_p = parse_pt_int(row[6] if len(row) > 6 else "")
                        period = parse_pt_int(row[7] if len(row) > 7 else "")
                        period = max(0, min(12, period))
                        dept_id, dept_code = resolve_department(code, is_activity=False)

                        subjects.append(
                            {
                                "code": code,
                                "name": name,
                                "departmentId": dept_id,
                                "departmentCode": dept_code,
                                "period": period,
                                "type": "Obrigatória",
                                "chs": chs,
                                "che": che,
                                "cha": cha,
                                "weeklyTheory": weekly_t,
                                "weeklyPractice": weekly_p,
                                "prerequisites": prereqs,
                            }
                        )

                # Tabela de atividades curriculares (Estágio, TCC, AACC, Extensão)
                elif "CÓDIGO" in header_joined and "ATIVIDADE" in header_joined:
                    for row in tbl[1:]:
                        if not row or len(row) < 5:
                            continue
                        code = collapse_ws(row[0]).upper()
                        name = collapse_ws(row[1])
                        if not code or not name or not ACTIVITY_CODE_RE.match(code):
                            continue
                        if code in seen_codes:
                            continue
                        seen_codes.add(code)

                        prereqs = parse_prerequisites(row[2] if len(row) > 2 else "")
                        # Colunas: CÓDIGO | ATIVIDADES | PRÉ-REQUISITO | CARÁTER | CHS | PER
                        chs_raw = row[4] if len(row) > 4 else ""
                        per_raw = row[5] if len(row) > 5 else ""
                        chs, che = parse_chs_che(chs_raw, is_activity=True, name=name)
                        cha = round(chs * 1.2) if chs > 0 else 0
                        period = max(0, min(12, parse_pt_int(per_raw)))
                        dept_id, dept_code = resolve_department(code, is_activity=True)

                        subjects.append(
                            {
                                "code": code,
                                "name": name,
                                "departmentId": dept_id,
                                "departmentCode": dept_code,
                                "period": period,
                                "type": "Atividade",
                                "chs": chs,
                                "che": che,
                                "cha": cha,
                                "weeklyTheory": 0,
                                "weeklyPractice": 0,
                                "prerequisites": prereqs,
                            }
                        )

                # Tabela de resumo de integralização (validação/fallback de carga horária)
                elif "COMPONENTES CURRICULARES EXIGIDOS PARA INTEGRALIZA" in header_joined and "NO CURSO" in header_joined:
                    if len(tbl) >= 3:
                        labels = (tbl[1][0] or "").splitlines()
                        values = (tbl[1][-1] or "").splitlines()
                        for lbl, val in zip(labels, values):
                            l_up = lbl.strip().upper()
                            v_int = parse_pt_int(val)
                            if "OBRIGAT" in l_up:
                                summary_hours["chMandatory"] = v_int
                            elif "ELETIV" in l_up:
                                summary_hours["chElective"] = v_int
                            elif "OPTATIV" in l_up:
                                summary_hours["chOptional"] = v_int
                            elif "ATIVIDADE" in l_up:
                                summary_hours["chActivities"] = v_int
                            elif "EXTENS" in l_up:
                                summary_hours["chExtension"] = v_int
                        total_val = parse_pt_int(tbl[2][-1])
                        if total_val > 0:
                            summary_hours["chTotal"] = total_val

    # Ordenação determinística: períodos 1..12 primeiro, depois atividades (period == 0), depois código
    subjects.sort(key=lambda s: (s["period"] == 0, s["period"], s["type"] != "Obrigatória", s["code"]))
    return subjects, summary_hours


def parse_supplemental_detailed_pages(summary_pdf_path: Path) -> dict[str, list[dict[str, Any]]]:
    """
    Fallback suplementar das páginas 2..4 do PDF consolidado (AUT e ADM), usado apenas
    se o PDF individual de um curso não estiver disponível no diretório de matrizes.
    """
    fallback: dict[str, list[dict[str, Any]]] = {}
    with pdfplumber.open(str(summary_pdf_path)) as pdf:
        for page in pdf.pages[1:]:
            tables = page.extract_tables() or []
            if not tables:
                continue
            tbl = tables[0]
            course_code = ""
            for r in tbl[:4]:
                line = collapse_ws(r[0] if r else "")
                m = re.search(r"\(([A-Z0-9]{2,4})\)", line)
                if m:
                    course_code = m.group(1).upper()
                    break
            if not course_code:
                continue

            items: list[dict[str, Any]] = []
            seen: set[str] = set()
            header_seen = False
            for r in tbl:
                if not r or len(r) < 9:
                    continue
                c0 = collapse_ws(r[0])
                c1 = collapse_ws(r[1]).upper()
                if not header_seen:
                    if c0.lower() == "curso" and c1.startswith("CÓD"):
                        header_seen = True
                    continue
                if not c1 or c0.lower().startswith("total geral") or not ACTIVITY_CODE_RE.match(c1):
                    continue
                if c1 in seen:
                    continue
                seen.add(c1)

                name = collapse_ws(r[2])
                per_cell = collapse_ws(r[3])
                type_cell = collapse_ws(r[4]).lower()
                is_activity = "atividade" in type_cell or c1.startswith(("ATV", "ATC", "ATE"))
                per_match = re.search(r"(\d{1,2})\s*º", per_cell)
                period = int(per_match.group(1)) if per_match else 0
                chs, che = parse_chs_che(r[5], is_activity=is_activity, name=name)
                cha = parse_pt_int(r[6])
                aulas = collapse_ws(r[7])
                weekly_t, weekly_p = 0, 0
                if "/" in aulas:
                    p_t, p_p = aulas.split("/", 1)
                    weekly_t, weekly_p = parse_pt_int(p_t), parse_pt_int(p_p)
                prereqs = parse_prerequisites(r[8])
                dept_id, dept_code = resolve_department(c1, is_activity=is_activity)
                items.append(
                    {
                        "code": c1,
                        "name": name,
                        "departmentId": dept_id,
                        "departmentCode": dept_code,
                        "period": period,
                        "type": "Atividade" if is_activity else "Obrigatória",
                        "chs": chs,
                        "che": che,
                        "cha": cha,
                        "weeklyTheory": weekly_t,
                        "weeklyPractice": weekly_p,
                        "prerequisites": prereqs,
                    }
                )
            if items:
                fallback[course_code] = items
    return fallback


def build_asset(summary_pdf: Path, matrices_dir: Path, output_json: Path) -> dict[str, Any]:
    courses = parse_master_courses_page1(summary_pdf)
    pdf_map = discover_matrix_pdfs(matrices_dir)
    supplemental = parse_supplemental_detailed_pages(summary_pdf)

    detailed_count = 0
    total_subjects = 0

    for course in courses:
        code = course["code"]
        pdf_path = pdf_map.get(code)
        if pdf_path and pdf_path.exists():
            subjects, summary_hours = parse_individual_matrix_pdf(pdf_path)
            course["subjects"] = subjects
            # Preencher carga horária ausente caso necessário
            for field in ("chMandatory", "chOptional", "chElective", "chExtension", "chActivities", "chTotal"):
                if course[field] == 0 and summary_hours.get(field, 0) > 0:
                    course[field] = summary_hours[field]
            if subjects and course["status"].lower() == "pendente":
                course["status"] = "Coletada"
        elif code in supplemental:
            course["subjects"] = supplemental[code]

        if course["subjects"]:
            detailed_count += 1
            total_subjects += len(course["subjects"])

    # Ordenação alfabética determinística por nome oficial
    courses.sort(key=lambda c: (c["name"].casefold(), c["code"]))

    payload = {
        "schemaVersion": 1,
        "semester": "2026/2",
        "source": "PROGRAD/UFOP Matrizes Curriculares 2026/2",
        "totalCourses": len(courses),
        "detailedCourses": detailed_count,
        "totalSubjects": total_subjects,
        "courses": courses,
    }

    output_json.parent.mkdir(parents=True, exist_ok=True)
    with output_json.open("w", encoding="utf-8", newline="\n") as fp:
        json.dump(payload, fp, ensure_ascii=False, separators=(",", ":"))

    return payload


def validate_payload(payload: dict[str, Any], output_json: Path) -> None:
    courses = payload["courses"]
    assert len(courses) == 54, f"Esperado 54 cursos, obtido {len(courses)}"

    codes = [c["code"] for c in courses]
    assert len(codes) == len(set(codes)), "Códigos de curso duplicados!"

    detailed = [c for c in courses if c["subjects"]]
    assert len(detailed) >= 45, f"Esperado >= 45 cursos detalhados, obtido {len(detailed)}"

    # Verifica ausência de artefatos de quebra de tabela em nomes e códigos
    for c in courses:
        assert "(" not in c["code"] and ")" not in c["code"], f"Artefato em código: {c['code']}"
        assert c["name"].count("(") == c["name"].count(")"), f"Parênteses desbalanceados em nome: {c['name']}"
        assert not c["name"].endswith("("), f"Nome truncado: {c['name']}"
        for s in c["subjects"]:
            assert s["type"] in {"Obrigatória", "Atividade"}, f"Tipo inválido: {s}"
            assert 0 <= s["period"] <= 12, f"Período inválido: {s}"
            assert "\n" not in s["name"], f"Quebra de linha em disciplina: {s}"

    size_kb = output_json.stat().st_size / 1024.0
    print(f"[OK] Asset gerado: {output_json}")
    print(
        f"[OK] Cursos: {len(courses)} | Com matriz detalhada: {len(detailed)} | "
        f"Total de componentes: {payload['totalSubjects']} | Tamanho: {size_kb:.1f} KB"
    )
    missing = [f"{c['code']} ({c['name']})" for c in courses if not c["subjects"]]
    print(f"[INFO] Cursos apenas com resumo ({len(missing)}): {', '.join(missing)}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Gera ufop_matrizes_2026_2.json a partir dos PDFs oficiais da UFOP.")
    parser.add_argument("--summary-pdf", type=Path, default=DEFAULT_SUMMARY_PDF)
    parser.add_argument("--matrices-dir", type=Path, default=DEFAULT_MATRICES_DIR)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT_JSON)
    args = parser.parse_args()

    payload = build_asset(args.summary_pdf, args.matrices_dir, args.output)
    validate_payload(payload, args.output)


if __name__ == "__main__":
    main()
