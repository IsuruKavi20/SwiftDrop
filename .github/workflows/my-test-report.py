"""Summarises Isuru's test results and the coverage of Isuru's classes only (run from backend/).

Reads surefire XML (test counts) and JaCoCo's CSV (covered/missed lines and branches per class),
keeps only the classes Isuru owns, and prints a Markdown table for the GitHub run summary.
"""
import csv
import glob
import os
import re

MINE_PACKAGES = [
    r"^com\.swiftdrop\.backend\.identity(\.|$)",
    r"^com\.swiftdrop\.backend\.security$",
    r"^com\.swiftdrop\.backend\.shared\.error$",
    r"^com\.swiftdrop\.backend\.ai\.security$",
]
# classes in my packages that a teammate built: never counted
NOT_MINE = {"com.swiftdrop.backend.identity.client": ["MerchantTokenIntrospection"]}
MINE_CLASSES = {
    "com.swiftdrop.backend.notification.service": ["CustomerStatusListener", "OutboxResubmitter", "TrackingLinks"],
}


def mine(package, cls):
    top = cls.split(".")[0]   # inner classes (Outer.Inner) belong to their outer class
    if top in NOT_MINE.get(package, []):
        return False
    return any(re.match(p, package) for p in MINE_PACKAGES) or top in MINE_CLASSES.get(package, [])


def pct(covered, total):
    return f"{covered * 100 / total:.1f}%" if total else "-"


def tests():
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    rows = []
    for path in sorted(glob.glob("target/surefire-reports/TEST-*.xml")):
        head = open(path, encoding="utf-8").read(2000)
        name = re.search(r'testsuite[^>]*name="([^"]+)"', head).group(1)
        counts = {k: int(v) for k, v in re.findall(r'(tests|failures|errors|skipped)="(\d+)"', head)}
        for key in totals:
            totals[key] += counts.get(key, 0)
        rows.append((name.replace("com.swiftdrop.backend.", ""), counts))
    return totals, rows


def coverage():
    per_package = {}
    path = "target/site/jacoco/jacoco.csv"
    if not os.path.exists(path):
        return per_package
    for row in csv.DictReader(open(path, encoding="utf-8")):
        if not mine(row["PACKAGE"], row["CLASS"]):
            continue
        sums = per_package.setdefault(row["PACKAGE"], [0, 0, 0, 0])
        for i, key in enumerate(["LINE_COVERED", "LINE_MISSED", "BRANCH_COVERED", "BRANCH_MISSED"]):
            sums[i] += int(row[key])
    return per_package


totals, rows = tests()
print("## My backend tests (Isuru)")
print(f"- Commit: `{os.environ.get('GITHUB_SHA', 'local')}` on `{os.environ.get('GITHUB_REF_NAME', 'local')}`")
print(f"- Run: {os.environ.get('GITHUB_SERVER_URL', '')}/{os.environ.get('GITHUB_REPOSITORY', '')}"
      f"/actions/runs/{os.environ.get('GITHUB_RUN_ID', '')}")
print(f"- **Tests run {totals['tests']}, failures {totals['failures']}, errors {totals['errors']}, "
      f"skipped {totals['skipped']}**\n")
print("| Test class | Tests | Failures | Errors |\n|---|---|---|---|")
for name, c in rows:
    print(f"| {name} | {c.get('tests', 0)} | {c.get('failures', 0)} | {c.get('errors', 0)} |")

packages = coverage()
print("\n## Coverage of my code only (JaCoCo)\n")
print("| Package | Lines | Branches |\n|---|---|---|")
all_sums = [0, 0, 0, 0]
for package, (lc, lm, bc, bm) in sorted(packages.items()):
    all_sums = [a + b for a, b in zip(all_sums, [lc, lm, bc, bm])]
    print(f"| {package.replace('com.swiftdrop.backend.', '')} | {pct(lc, lc + lm)} ({lc}/{lc + lm}) "
          f"| {pct(bc, bc + bm)} ({bc}/{bc + bm}) |")
lc, lm, bc, bm = all_sums
print(f"| **Total** | **{pct(lc, lc + lm)}** ({lc}/{lc + lm}) | **{pct(bc, bc + bm)}** ({bc}/{bc + bm}) |")
