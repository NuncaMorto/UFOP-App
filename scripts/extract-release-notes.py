#!/usr/bin/env python3
"""Extrai as notas de uma versão do CHANGELOG.md com rodapé <!-- versionCode: N -->."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


def infer_from_gradle(gradle_path: Path) -> tuple[str | None, int | None]:
    if not gradle_path.is_file():
        return None, None
    text = gradle_path.read_text(encoding="utf-8")
    ver_match = re.search(r'\bversionName\s*=\s*"([^"]+)"', text)
    code_match = re.search(r"\bversionCode\s*=\s*(\d+)", text)
    version = ver_match.group(1) if ver_match else None
    code = int(code_match.group(1)) if code_match else None
    return version, code


def extract_section(changelog_text: str, version_bare: str) -> str | None:
    lines = changelog_text.splitlines()
    header_rx = re.compile(
        rf"^##\s+(?:\[)?v?{re.escape(version_bare)}(?:\b|\]|\s|$)"
    )
    captured: list[str] = []
    active = False
    for line in lines:
        if not active:
            if header_rx.match(line):
                active = True
                captured.append(line)
        else:
            if line.startswith("## "):
                break
            captured.append(line)

    while captured and captured[-1].strip() in ("", "---"):
        captured.pop()

    if not captured:
        return None
    return "\n".join(captured)


def build_release_notes(
    version: str,
    version_code: int | None,
    changelog_path: Path,
    strict: bool,
) -> str:
    version_bare = version[1:] if version.startswith("v") else version

    if not changelog_path.is_file():
        if strict:
            raise FileNotFoundError(f"CHANGELOG não encontrado em '{changelog_path}'.")
        body = f"## Vida Acadêmica v{version_bare}"
    else:
        changelog_text = changelog_path.read_text(encoding="utf-8")
        section = extract_section(changelog_text, version_bare)
        if section is None:
            if strict:
                raise ValueError(
                    f"Seção '## v{version_bare}' não encontrada em '{changelog_path}'."
                )
            body = f"## Vida Acadêmica v{version_bare}"
        else:
            body = section

    # Remove eventuais comentários versionCode residuais antes de anexar o oficial.
    cleaned = re.sub(
        r"(?im)^\s*<!--\s*versionCode\s*:\s*\d+\s*-->\s*\r?\n?",
        "",
        body,
    ).rstrip()

    if version_code is not None and version_code > 0:
        return f"{cleaned}\n\n<!-- versionCode: {version_code} -->\n"
    return f"{cleaned}\n"


def main() -> int:
    repo_root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(
        description="Extrai notas da release a partir do CHANGELOG.md."
    )
    parser.add_argument(
        "--version",
        help="Versão alvo (ex.: 1.9.15 ou v1.9.15). Se omitida, lê build.gradle.kts.",
    )
    parser.add_argument(
        "--version-code",
        type=int,
        help="versionCode correspondente. Se omitido, lê build.gradle.kts.",
    )
    parser.add_argument(
        "--changelog",
        default=str(repo_root / "CHANGELOG.md"),
        help="Caminho para o CHANGELOG.md.",
    )
    parser.add_argument(
        "--gradle",
        default=str(repo_root / "android-app" / "app" / "build.gradle.kts"),
        help="Caminho para android-app/app/build.gradle.kts.",
    )
    parser.add_argument(
        "--out",
        help="Arquivo de saída UTF-8 (LF). Se omitido, imprime na saída padrão.",
    )
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Falha com código != 0 se a seção da versão não existir no CHANGELOG.md.",
    )
    args = parser.parse_args()

    gradle_version, gradle_code = infer_from_gradle(Path(args.gradle))
    version = args.version or gradle_version
    if not version:
        print(
            "Erro: informe --version ou garanta versionName em build.gradle.kts.",
            file=sys.stderr,
        )
        return 1

    version_code = args.version_code if args.version_code is not None else gradle_code

    try:
        notes = build_release_notes(
            version=version,
            version_code=version_code,
            changelog_path=Path(args.changelog),
            strict=args.strict,
        )
    except (FileNotFoundError, ValueError) as exc:
        print(f"Erro: {exc}", file=sys.stderr)
        return 1

    if args.out:
        out_path = Path(args.out)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(notes, encoding="utf-8", newline="\n")
    else:
        if hasattr(sys.stdout, "reconfigure"):
            sys.stdout.reconfigure(encoding="utf-8")
        sys.stdout.write(notes)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
