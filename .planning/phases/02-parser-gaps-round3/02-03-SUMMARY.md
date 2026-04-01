---
phase: 02-parser-gaps-round3
plan: 03
subsystem: parser
tags: [delphi, sql, case-of, field-extraction, param-dedup, java]

# Dependency graph
requires:
  - phase: 02-parser-gaps-round3/02-02
    provides: CalcCellColors, transaction detection, ABrush.Color mapping
provides:
  - case...of SQL branch detection producing separate SqlFragment per branch
  - method parameter exclusion from class fields (Gap 6)
  - ParamByName deduplication in SqlQuery.params (Gap 7)
  - params field on SqlQuery model with javaType mapping
affects: [angular-generator, java-generator, analyze-delphi-unit, sql-extraction]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "case...of SQL branches produce separate SqlFragment with base+branch SQL instead of one invalid concatenation"
    - "Event handler param names excluded via Set.of() filter before FIELD_PATTERN matching"
    - "ParamByName dedup via seenParamNames HashSet (case-insensitive, first occurrence wins)"

key-files:
  created: []
  modified:
    - src/main/java/com/migration/mcp/parser/DelphiSourceParser.java
    - src/main/java/com/migration/mcp/model/SqlQuery.java

key-decisions:
  - "case...of branch splits produce N separate SqlFragments (base SQL + each branch SQL) instead of one concatenated invalid query"
  - "Method param exclusion uses two-step approach: strip procedure/function signatures with replaceAll, then filter eventParams Set"
  - "ParamByName params stored as List<Map<String,String>> on SqlQuery with name/javaType/delphiAsType/bindExpression keys"
  - "mapDelphiParamType covers 9 AsXxx Delphi types; unknown types fall back to Object"
  - "extractParamTypesForQuery searches 200 chars before + 2000 chars after SQL position for ParamByName calls"

requirements-completed: [GAP-5, GAP-6, GAP-7]

# Metrics
duration: 25min
completed: 2026-04-01
---

# Phase 2 Plan 03: Parser Gaps Round 3 (Gap 5+6+7) Summary

**case...of SQL branches produce separate query variants, method params excluded from class fields, and ParamByName deduplicated across conditional branches**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-04-01T20:12:00Z
- **Completed:** 2026-04-01T20:37:00Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Gap 5: `case...of` SQL blocks now produce one SqlFragment per branch (with shared base SQL prefix) instead of an invalid concatenated query containing multiple WHERE clauses
- Gap 6: Procedure/function parameter lists stripped from class section content before FIELD_PATTERN matching; event handler param names (sender, field, acolumn, abrush, afont, etc.) excluded via Set filter
- Gap 7: `extractParamTypesForQuery` method added — extracts `ParamByName('x').AsType := expr` with deduplication via `seenParamNames` HashSet; `SqlQuery` model gains `params` field

## Task Commits

1. **Task 1: Handle case...of SQL branches and exclude method params from fields** - `251c930` (fix)
2. **Task 2: Deduplicate ParamByName entries from conditional branches** - `a1ca04b` (fix)

## Files Created/Modified

- `src/main/java/com/migration/mcp/parser/DelphiSourceParser.java` - Added caseOfBranchPattern, branch-split SQL logic, eventParams exclusion, extractParamTypesForQuery, mapDelphiParamType
- `src/main/java/com/migration/mcp/model/SqlQuery.java` - Added params field (List<Map<String,String>>) with getter/setter

## Decisions Made

- For Gap 5: when `branchCuts` are populated (from either `end...else...begin` or `end;N:begin` patterns), the SQL building path produces N separate SqlFragments instead of one. Each fragment contains: base SQL (lines before first cut) + branch SQL (lines in that branch only). This prevents the invalid "SELECT ... WHERE X SELECT ... WHERE Y" output.
- For Gap 6: two-layer defense: (1) `replaceAll` strips entire `procedure Name(params);` signatures using `(?si)`, eliminating multi-line parameter lists; (2) `eventParams` Set catches any remaining common event handler parameter names that slip through.
- For Gap 7: method extracted as `extractParamTypesForQuery(SqlQuery, String)` rather than inline. The search window is `[pos-200, pos+2000]` to cover ParamByName calls typically found just after `Open`/`ExecSQL` in the same method body.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added params field and mapDelphiParamType to SqlQuery**
- **Found during:** Task 2 (ParamByName deduplication)
- **Issue:** Plan referenced `q.getParams()` and `extractParamTypesForQuery` as if they existed at line 739, but neither the method nor the `params` field on `SqlQuery` existed in this version of the code
- **Fix:** Added `params` field (with `Map` import) and getter/setter to `SqlQuery.java`; created the full `extractParamTypesForQuery` method and `mapDelphiParamType` helper in the parser; wired both into `buildSqlQuery`
- **Files modified:** SqlQuery.java, DelphiSourceParser.java
- **Verification:** Build passes, `grep -c seenParamNames` returns 2
- **Committed in:** a1ca04b (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 missing critical)
**Impact on plan:** Required to implement Gap 7 at all — the `params` field was the missing foundation. No scope creep.

## Issues Encountered

None - all three gaps implemented cleanly. The `java.util.*` wildcard import already covered `Collections`, `Set`, `HashSet`, and `Map`.

## Next Phase Readiness

- All three gaps from plan 02-03 resolved
- Phase 02 (parser-gaps-round3) is now complete — all 7 gaps addressed across plans 02-01, 02-02, 02-03
- SqlQuery.params now available for angular/java generators to emit correct Java method parameters
- Build produces clean jar — ready for MCP reload with `/mcp`

---
*Phase: 02-parser-gaps-round3*
*Completed: 2026-04-01*
