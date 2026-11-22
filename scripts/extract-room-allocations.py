#!/usr/bin/env python3
"""
Extrai as alocações oficiais de salas 2026.2 (ICEB e Bloco de Salas) a partir dos 10 PDFs da UFOP
usando coordenadas X/Y de bounding box via pdfplumber.
Gera: android-app/app/src/main/assets/ufop_room_allocations_2026_2.json
"""
import json
import os
import re
import sys
from pathlib import Path
import pdfplumber

DEFAULT_PDF_DIR = Path(
    os.environ.get("UFOP_ALLOCATIONS_DIR", str(Path.home() / "Downloads"))
)

PDF_SOURCES = [
    ("ICEB", 1, str(DEFAULT_PDF_DIR / "alocacao_iceb_26-2.segunda-feira_6.pdf")),
    ("ICEB", 2, str(DEFAULT_PDF_DIR / "alocacao_iceb_26-2.terca-feira.pdf")),
    ("ICEB", 3, str(DEFAULT_PDF_DIR / "alocacao_iceb_26-2.quarta-feira_3.pdf")),
    ("ICEB", 4, str(DEFAULT_PDF_DIR / "alocacao_iceb_26-2.quinta-feira_2.pdf")),
    ("ICEB", 5, str(DEFAULT_PDF_DIR / "alocacao_iceb_26-2.sexta-feira_4.pdf")),
    ("Bloco de Salas", 1, str(DEFAULT_PDF_DIR / "alocacao_bloco_de_salas_26-2_segunda-feira_3.pdf")),
    ("Bloco de Salas", 2, str(DEFAULT_PDF_DIR / "alocacao_bloco_de_salas_26-2.terca-feira.docx_2.pdf")),
    ("Bloco de Salas", 3, str(DEFAULT_PDF_DIR / "alocacao_bloco_de_salas_26-2.quarta-feira_1.pdf")),
    ("Bloco de Salas", 4, str(DEFAULT_PDF_DIR / "alocacao_bloco_de_salas_26-2.quinta-feira_0.pdf")),
    ("Bloco de Salas", 5, str(DEFAULT_PDF_DIR / "alocacao_bloco_de_salas_26-2sexta-feira.docx_2.pdf")),
]

TIME_RANGE_RE = re.compile(r"(\d{2}:\d{2})\s*[-–]\s*(\d{2}:\d{2})")
SINGLE_TIME_RE = re.compile(r"^\d{2}:\d{2}$")

# Ignorar siglas soltas ou cabeçalhos que não são códigos de disciplina com dígitos
COURSE_CODE_RE = re.compile(r"\b([A-Z]{3}\d{3}[A-Z]?)\b")


def group_words_by_lines(words, y_tol=3.5):
    lines = []
    for w in sorted(words, key=lambda x: (x["top"], x["x0"])):
        if not lines or abs(w["top"] - lines[-1][0]["top"]) > y_tol:
            lines.append([w])
        else:
            lines[-1].append(w)
    return [sorted(line, key=lambda x: x["x0"]) for line in lines]


def detect_time_on_line(line):
    """
    Retorna (start_time, end_time, max_time_x1, line_y) se a linha contiver um horário HH:MM-HH:MM.
    Caso contrário retorna None.
    """
    # Caso 1: token único "07:30-08:20"
    for w in line:
        if w["x0"] < 110:
            m = TIME_RANGE_RE.search(w["text"])
            if m:
                return m.group(1), m.group(2), w["x1"], w["top"]

    # Caso 2: tokens separados "07:30", "-", "08:20" à esquerda
    left_words = [w for w in line if w["x0"] < 100]
    if len(left_words) >= 2:
        joined = " ".join(w["text"] for w in left_words)
        m = TIME_RANGE_RE.search(joined)
        if m:
            time_words = [
                w for w in left_words
                if SINGLE_TIME_RE.match(w["text"]) or w["text"] in ("-", "–")
            ]
            max_x1 = max((w["x1"] for w in time_words), default=left_words[-1]["x1"])
            return m.group(1), m.group(2), max_x1, line[0]["top"]

    return None


def detect_header_columns(line):
    """
    Se a linha for um cabeçalho de sub-tabela (HORA / Hora / Hho H / SALA 1..23 / 101..214),
    retorna lista de (room_label, center_x). Caso contrário retorna None.
    """
    if detect_time_on_line(line) is not None:
        return None

    texts = [w["text"].strip() for w in line if w["text"].strip()]
    upper_joined = " ".join(texts).upper()
    if "HORÁRIO" in upper_joined or "SEMESTRE" in upper_joined:
        return None

    has_hora = any(
        t.upper().startswith("HORA") or t.upper() in ("HHO", "ORA")
        for t in texts
    )
    room_nums = [t for t in texts if re.fullmatch(r"\d{1,3}", t)]
    if not has_hora and len(room_nums) < 4:
        return None

    columns = []
    i = 0
    while i < len(line):
        w = line[i]
        t = w["text"].strip()
        tu = t.upper()
        if tu in ("HORA", "HHO", "H", "ORA", "-"):
            i += 1
            continue
        if tu == "SALA" and i + 1 < len(line) and re.fullmatch(r"\d{1,3}[A-Z]?", line[i + 1]["text"].strip()):
            nw = line[i + 1]
            num = nw["text"].strip()
            cx = (w["x0"] + nw["x1"]) / 2.0
            columns.append((f"Sala {num}", cx))
            i += 2
            continue
        m_sala = re.fullmatch(r"SALA\s*(\d{1,3}[A-Z]?)", tu)
        if m_sala:
            cx = (w["x0"] + w["x1"]) / 2.0
            columns.append((f"Sala {m_sala.group(1)}", cx))
            i += 1
            continue
        if re.fullmatch(r"\d{1,3}[A-Z]?", t):
            cx = (w["x0"] + w["x1"]) / 2.0
            columns.append((f"Sala {t}", cx))
            i += 1
            continue
        if tu.startswith("AUD") or tu.startswith("LAB"):
            cx = (w["x0"] + w["x1"]) / 2.0
            columns.append((t.title(), cx))
            i += 1
            continue
        i += 1

    return columns if len(columns) >= 3 else None


def expand_turma_spec(spec_text):
    """
    Recebe o sufixo após o código da disciplina (ex: '-64', '-51/52/53', '-21-22T', '-31a33 T', ' 21P', ' T')
    e retorna (turmas_list, kind_str).
    """
    s = spec_text.strip()
    if not s:
        return [], ""

    kind = ""
    # Verifica se termina com T ou P (isolado ou colado a dígito)
    m_kind_end = re.search(r"(?:(?<=\d)|(?<=[\s\-/]))([TP])\s*$", s, re.IGNORECASE)
    if m_kind_end:
        kind = m_kind_end.group(1).upper()
        s = s[:m_kind_end.start(1)].strip()
    elif s.upper() in ("T", "P"):
        return [], s.upper()

    # Limpa hífens/espaços iniciais e finais
    s = re.sub(r"^[\s\-–:]+|[\s\-–:]+$", "", s)
    if not s:
        return [], kind

    # Também captura caso 'T' ou 'P' tenha ficado colado em cada número (ex: "21T/22T")
    turmas = []

    # Verifica intervalos do tipo "31a33" ou "31 a 33"
    def replace_a_range(m):
        a, b = int(m.group(1)), int(m.group(2))
        if a <= b and (b - a) <= 15:
            return "/".join(str(x) for x in range(a, b + 1))
        return m.group(0)

    s = re.sub(r"(\d+)\s*[aA]\s*(\d+)", replace_a_range, s)

    # Verifica se há partes separadas por '/' ou '-'
    # Ex: "51/52/53", "21-22", "1/2", "41-42"
    parts = [p.strip() for p in re.split(r"[/,\s]+", s) if p.strip()]
    for part in parts:
        # Remove eventual T/P residual do token
        m_tp = re.fullmatch(r"(\d+(?:-\d+)*)([TP])?", part, re.IGNORECASE)
        if m_tp:
            if m_tp.group(2) and not kind:
                kind = m_tp.group(2).upper()
            core = m_tp.group(1)
            if "-" in core:
                subnums = [int(x) for x in core.split("-") if x.isdigit()]
                if len(subnums) == 2 and subnums[0] < subnums[1] and (subnums[1] - subnums[0]) <= 10:
                    for x in range(subnums[0], subnums[1] + 1):
                        t_str = str(x).lstrip("0") or "0"
                        if t_str not in turmas:
                            turmas.append(t_str)
                else:
                    for x in subnums:
                        t_str = str(x).lstrip("0") or "0"
                        if t_str not in turmas:
                            turmas.append(t_str)
            else:
                t_str = core.lstrip("0") or "0"
                if t_str not in turmas:
                    turmas.append(t_str)
        else:
            # Extrai quaisquer números presentes
            for num in re.findall(r"\d+", part):
                t_str = num.lstrip("0") or "0"
                if t_str not in turmas:
                    turmas.append(t_str)

    return turmas, kind


def parse_cell_text(raw_cell):
    """
    Recebe o texto concatenado de uma célula (ex: 'FIS118 / FIS701', 'CBI198 EFD348',
    'MSC 003', 'CBI002- 21-22T', 'ACL703-11 T', 'MTM124-64')
    e retorna lista de (course_code, turmas_list, kind).
    """
    text = raw_cell.strip()
    if not text or text in ("-", "–", "---"):
        return []

    # Une código quebrado por espaço entre 3 letras e 3 dígitos (ex: "MSC 003" -> "MSC003", "CBI 002" -> "CBI002")
    # Mas cuidado para não unir "CBI006 21" (onde CBI006 já tem 3 dígitos)
    text = re.sub(r"\b([A-Z]{3})\s+(\d{3})\b", r"\1\2", text.upper())

    matches = list(COURSE_CODE_RE.finditer(text))
    if not matches:
        return []

    parsed = []
    # Caso especial: múltiplos códigos compartilhando a mesma turma no final, ou cada um com a sua
    raw_items = []
    for idx, m in enumerate(matches):
        code = m.group(1).upper()
        start_after = m.end()
        end_before_next = matches[idx + 1].start() if (idx + 1) < len(matches) else len(text)
        suffix = text[start_after:end_before_next]
        # Remove separadores de múltiplos códigos no final do sufixo (ex: " / " em "FIS118 / FIS701")
        suffix = re.sub(r"[/,;]+\s*$", "", suffix).strip()
        turmas, kind = expand_turma_spec(suffix)
        raw_items.append([code, turmas, kind])

    # Se um código anterior não tinha turma/kind mas o próximo tinha e eram separados por '/', propaga se aplicável
    for code, turmas, kind in raw_items:
        parsed.append((code, turmas, kind))

    return parsed


def extract_all_allocations():
    allocations = []
    seen_keys = set()

    for building, day_of_week, pdf_path in PDF_SOURCES:
        if not os.path.exists(pdf_path):
            raise FileNotFoundError(f"PDF não encontrado: {pdf_path}")

        with pdfplumber.open(pdf_path) as pdf:
            for page in pdf.pages:
                words = page.extract_words()
                lines = group_words_by_lines(words, y_tol=3.5)

                # Divide as linhas da página em sub-tabelas delimitadas por linhas de cabeçalho
                subtables = []
                current_cols = None
                current_lines = []
                for line in lines:
                    header_cols = detect_header_columns(line)
                    if header_cols is not None:
                        if current_cols is not None and current_lines:
                            subtables.append((current_cols, current_lines))
                        current_cols = header_cols
                        current_lines = []
                    elif current_cols is not None:
                        upper_line = " ".join(w["text"] for w in line).upper()
                        if "HORÁRIO" in upper_line and "SEMESTRE" in upper_line:
                            continue
                        current_lines.append(line)
                if current_cols is not None and current_lines:
                    subtables.append((current_cols, current_lines))

                for cols, st_lines in subtables:
                    time_rows = []
                    for line in st_lines:
                        t_info = detect_time_on_line(line)
                        if t_info is not None:
                            start_t, end_t, max_x1, line_y = t_info
                            time_rows.append({
                                "start": start_t,
                                "end": end_t,
                                "max_x1": max_x1,
                                "y": line_y,
                                "cells": {room: [] for room, _ in cols},
                            })

                    if not time_rows:
                        continue

                    # Associa todas as palavras das linhas da sub-tabela (inclusive células quebradas em 2 linhas dentro de +-10pt)
                    for line in st_lines:
                        for w in line:
                            w_text = w["text"].strip()
                            if not w_text or w_text in ("-", "–"):
                                continue
                            if "HORÁRIO" in w_text.upper():
                                continue
                            # Ignora palavras que fazem parte da coluna de horário à esquerda
                            nearest_tr = min(time_rows, key=lambda tr: abs(w["top"] - tr["y"]))
                            if abs(w["top"] - nearest_tr["y"]) > 10.0:
                                continue
                            if w["x0"] <= nearest_tr["max_x1"] + 2.0:
                                continue
                            if TIME_RANGE_RE.search(w_text) or SINGLE_TIME_RE.match(w_text):
                                continue

                            wx = (w["x0"] + w["x1"]) / 2.0
                            nearest_room, _ = min(cols, key=lambda col: abs(wx - col[1]))
                            nearest_tr["cells"][nearest_room].append(w)

                    for tr in time_rows:
                        start_time = tr["start"]
                        end_time = tr["end"]
                        for room, _ in cols:
                            r_words = tr["cells"][room]
                            if not r_words:
                                continue
                            r_words_sorted = sorted(r_words, key=lambda x: (round(x["top"] / 3.5), x["x0"]))
                            cell_text = " ".join(w["text"].strip() for w in r_words_sorted)
                            for code, turmas, kind in parse_cell_text(cell_text):
                                dedup_key = (
                                    code,
                                    tuple(turmas),
                                    day_of_week,
                                    start_time,
                                    end_time,
                                    building,
                                    room,
                                    kind,
                                )
                                if dedup_key in seen_keys:
                                    continue
                                seen_keys.add(dedup_key)
                                allocations.append({
                                    "c": code,
                                    "t": turmas,
                                    "d": day_of_week,
                                    "s": start_time,
                                    "e": end_time,
                                    "b": building,
                                    "r": room,
                                    "k": kind,
                                })

    return allocations


def validate_allocations(allocations):
    assert len(allocations) >= 1000, f"Esperado >= 1000 alocações, obtido {len(allocations)}"

    def has_match(code, turma, day, building, room):
        return any(
            a["c"] == code
            and turma in a["t"]
            and a["d"] == day
            and a["b"] == building
            and a["r"] == room
            for a in allocations
        )

    checks = [
        ("MTM124", "64", 1, "ICEB", "Sala 19"),
        ("MTM124", "64", 3, "ICEB", "Sala 10"),
        ("BCC721", "11", 2, "Bloco de Salas", "Sala 205"),
        ("BCC721", "11", 4, "Bloco de Salas", "Sala 106"),
        ("MTM125", "84", 3, "ICEB", "Sala 6"),
        ("MTM125", "84", 5, "ICEB", "Sala 6"),
    ]
    for code, turma, day, building, room in checks:
        ok = has_match(code, turma, day, building, room)
        print(f"  [CHECK] {code}-{turma} d={day} -> {building} / {room}: {'OK' if ok else 'FALHOU'}")
        if not ok:
            raise AssertionError(f"Validação falhou para {code}-{turma} d={day} ({building} / {room})")


def main():
    allocations = extract_all_allocations()
    print(f"Total de alocações extraídas: {len(allocations)}")
    validate_allocations(allocations)

    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_path = os.path.join(
        repo_root,
        "android-app",
        "app",
        "src",
        "main",
        "assets",
        "ufop_room_allocations_2026_2.json",
    )
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    payload = {
        "semester": "2026.2",
        "buildingsCovered": ["ICEB", "Bloco de Salas"],
        "allocations": allocations,
    }
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))
    print(f"JSON gravado com sucesso em: {out_path} ({os.path.getsize(out_path)} bytes)")


if __name__ == "__main__":
    main()
