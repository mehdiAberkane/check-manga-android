"""Résumé de la couverture JaCoCo (lignes, par paquet), en Markdown pour le résumé du run GitHub Actions.

Usage : python3 coverage_summary.py app/build/reports/coverage/test/debug/report.xml >> "$GITHUB_STEP_SUMMARY"
"""
import re
import sys


def main(path: str) -> None:
    report = open(path, encoding="utf-8").read()
    print("### Couverture des tests unitaires (lignes)")
    print()
    print("| Paquet | Couverture |")
    print("|---|---|")
    for match in re.finditer(r'<package name="([^"]+)">(.*?)</package>', report, re.S):
        counters = re.findall(r'<counter type="LINE" missed="(\d+)" covered="(\d+)"/>', match.group(2))
        if counters:
            missed, covered = map(int, counters[-1])
            total = missed + covered
            name = match.group(1).replace("/", ".")
            print(f"| {name} | {100 * covered / total:.1f} % ({covered}/{total}) |")


if __name__ == "__main__":
    main(sys.argv[1])
