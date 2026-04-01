# Delphi Migration MCP — Roadmap

## Milestone 1: Parser & Generator Quality

### Phase 1 — Parser improvements
**Goal:** Aumentar cobertura do parser para campos privados, CalcCellColors complexo e SQL dinâmico em métodos auxiliares.

**Status:** In progress

### Phase 2 — Parser gaps round 3 (7 novos gaps)
**Goal:** Corrigir 7 gaps identificados no benchmark de 5 novas telas: TLogusWinControl.EnableComponent → enableConditions, CalcCellColors → calcCellColorRules, TLogusMessage.Warning+vContinua → guard_validation, Conexao.StartTransaction → transactionBoundaries, SQL dinâmico case...of, parâmetro de método capturado como field, param name duplicado em branches.

**Depends on:** Phase 1
**Plans:** 3 plans

Plans:
- [x] 02-01-PLAN.md — EnableComponent para non-button components + guard_validation com vContinua
- [x] 02-02-PLAN.md — CalcCellColors com ABrush.Color + Conexao.StartTransaction
- [x] 02-03-PLAN.md — SQL case...of branches + excluir params de metodo dos fields + dedup ParamByName
