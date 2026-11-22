# Personal test report: guide for Claude

Give this file to Claude (Claude Code, opened in your SwiftDrop folder) and say:
> "Read docs/testing/TEST-REPORT-GUIDE.md and make my test report. My parts are: <list your modules/features>."

Everything below is written for Claude.

---

## Instructions for Claude

You are helping one SwiftDrop team member write **their own** test report and coverage summary. Each member
reports only the code they built. Do not test, change or report other members' code.

### Rules
- Ask the member which packages/classes/pages are theirs if not given. Confirm the list before running anything.
- Never edit teammates' code. Never change the team `pom.xml`, `package.json` or `ci.yml`.
- New tests are fine only for the member's own code, on a feature branch, after the member agrees.
- Never put secrets in code, the report or screenshots.
- The member runs git commands themselves: give them the commands, do not commit or push.
- Explain in simple English. Report honest numbers, including what is below target.

### Step 1: find the member's code and tests
1. Backend code is in `backend/src/main/java/com/swiftdrop/backend/<module>/`, tests in
   `backend/src/test/java/...` (same package). Frontend code in `frontend/src/features/<feature>/`.
2. Make two lists: (a) their main classes/packages, (b) the test classes that test them.
3. Show both lists to the member and wait for "yes".

### Step 2: measure (backend)
From `backend/`, run only their tests with JaCoCo called from the command line (no pom change):
```
./mvnw org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.13:report -Dtest="<comma separated test classes or patterns>" -Dsurefire.failIfNoSpecifiedTests=false
```
- Results: `target/surefire-reports/TEST-*.xml` (tests, failures, errors, skipped).
- Coverage: `target/site/jacoco/jacoco.csv`. Keep only rows for the member's packages/classes. Per package sum
  `LINE_COVERED`, `LINE_MISSED`, `BRANCH_COVERED`, `BRANCH_MISSED`.
  - Line coverage = LINE_COVERED / (LINE_COVERED + LINE_MISSED)
  - Branch coverage = BRANCH_COVERED / (BRANCH_COVERED + BRANCH_MISSED)
- Docker must be running (integration tests use Testcontainers).

Frontend tests (if any), from `frontend/`: `npm ci` then `npx tsx --test <test files>`.

### Step 3: CI evidence (proof the numbers are real)
Copy `.github/workflows/my-test-report.yml` and `my-test-report.py` to new names for this member
(e.g. `report-<name>.yml` / `report-<name>.py`) and change:
- the workflow `name:` and the trigger branch (`report/<name>-tests`),
- the `TESTS=` line to the member's test classes,
- `MINE_PACKAGES` and `MINE_CLASSES` in the `.py` to the member's code,
- the frontend test file paths (or remove the frontend job if none).

The member pushes branch `report/<name>-tests` **to their own fork** (never open a PR from it). From the run
page take: run link, commit SHA, date/time, and the summary tables.

### Step 4: improve (optional, ask first)
If coverage of their code is below 80%, list the untested lines/branches (JaCoCo HTML: `target/site/jacoco/index.html`,
red = not run, yellow = only some branches) and suggest tests for error and security paths first.

### Step 5: write the report
Use the structure below exactly. Fill every `<...>`. Put it in `learning/report/` (git-ignored) or give it in chat
so the member can paste it into Google Docs.

---

## Report structure

```
# SwiftDrop Test Report and Coverage Summary: <member's area>

Member: <name>        Project: SwiftDrop (Spring Boot + React)        Report date: <date>

## 1. Evidence
| Item | Value |
| Repository | <repo URL> |
| Workflow | <workflow name and file> |
| Run | <run link> |
| Commit tested | <SHA> (branch <branch>) |
| Run date / time | <date time UTC> |
| Tools | JUnit 5, AssertJ, Spring Boot Test, Testcontainers, JaCoCo 0.8.13, <others> |

## 2. Scope
My parts (tested and measured): <module/feature: one line what it does>, ...
Not in scope: other members' modules and the separate auth service repository.

## 3. Test results
Backend: <N> tests run, <F> failures, <E> errors, <S> skipped.
| Test class | Type (Unit / Integration / Architecture) | Tests | Result |
Frontend: <N> tests, <passed> passed.

## 4. Coverage (JaCoCo, my classes only)
| Package | Line coverage (covered/total) | Branch coverage (covered/total) |
| **Total** | ... | ... |
Target 80%: lines <meets / below>, branches <meets / below>.
How it is calculated: <one-line formula for line and branch coverage; only my classes counted>.
Improvement (if tests were added): before <numbers> -> after <numbers>.

## 5. Security and error checks covered by tests
- <each security or failure behaviour a test proves, e.g. "a customer cannot read another customer's parcel (403)">

## 6. Limits and next steps
- <what is below target and why, missing test types (E2E), things faked in tests>

## 7. How to repeat
<exact backend command, frontend command, or "run the workflow from the Actions tab">
```

### Notes for the report
- **Unit test**: one class alone, no Spring, no database. **Integration test**: real Spring and/or a test
  database (`@SpringBootTest`, `@DataJpaTest`, Testcontainers). **Architecture**: `ModularityTest`.
- Check the type by looking at the test class's annotations; do not guess.
- Packages with no `if/else` show branch coverage "no branches", not 0%.
