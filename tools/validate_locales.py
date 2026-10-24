"""Validate localized Android string resources against the English source."""

from collections import Counter
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SOURCE = RES / "values/strings.xml"
LOCALES = (
    "zh-rCN", "zh-rTW", "ja", "ko", "fr", "de", "es", "it", "pt-rBR",
    "ru", "uk", "pl", "nl", "sv", "tr", "ar", "iw", "fa", "hi", "bn",
    "in", "vi", "th",
)
PLACEHOLDER = re.compile(r"%(\d+\$)?([a-zA-Z])")


def read_strings(path: Path) -> tuple[dict[str, str], list[str]]:
    root = ET.parse(path).getroot()
    entries = root.findall("string")
    names = [entry.get("name", "") for entry in entries]
    strings = {
        entry.get("name", ""): entry.text or ""
        for entry in entries
    }
    return strings, names


def main() -> int:
    errors: list[str] = []
    try:
        source_root = ET.parse(SOURCE).getroot()
    except (ET.ParseError, OSError) as exc:
        print(f"ERROR: cannot read base resources: {exc}", file=sys.stderr)
        return 1

    source_entries = source_root.findall("string")
    source_names = [entry.get("name", "") for entry in source_entries]
    expected = {
        entry.get("name", ""): entry.text or ""
        for entry in source_entries
        if entry.get("name") and entry.get("translatable") != "false"
    }
    if len(source_names) != len(set(source_names)):
        errors.append("Base strings.xml contains duplicate string names.")

    for locale in LOCALES:
        path = RES / f"values-{locale}" / "strings.xml"
        if not path.is_file():
            errors.append(f"{locale}: missing {path.relative_to(ROOT)}")
            continue
        try:
            localized, names = read_strings(path)
        except (ET.ParseError, OSError) as exc:
            errors.append(f"{locale}: invalid XML ({exc})")
            continue
        if len(names) != len(set(names)):
            errors.append(f"{locale}: duplicate string names")

        translated_names = set(names)
        expected_names = set(expected)
        missing = sorted(expected_names - translated_names)
        extra = sorted(translated_names - expected_names)
        if missing:
            errors.append(f"{locale}: missing keys: {', '.join(missing)}")
        if extra:
            errors.append(f"{locale}: unexpected keys: {', '.join(extra)}")

        for name in sorted(expected_names & translated_names):
            value = localized[name]
            if not value.strip():
                errors.append(f"{locale}: {name} is empty")
                continue
            if Counter(PLACEHOLDER.findall(value)) != Counter(PLACEHOLDER.findall(expected[name])):
                errors.append(
                    f"{locale}: {name} placeholders differ: "
                    f"{PLACEHOLDER.findall(expected[name])} vs {PLACEHOLDER.findall(value)}"
                )

    if errors:
        print("Locale validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        f"OK: {len(LOCALES)} locales; {len(expected)} translatable keys per locale; "
        "all XML files and format placeholders match."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

