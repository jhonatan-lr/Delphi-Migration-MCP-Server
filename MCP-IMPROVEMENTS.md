# MCP Improvements — Gaps Identificados nos Testes

Gaps concretos identificados durante testes com 4+ telas reais do Logus ERP.
Organizados por prioridade de impacto no código gerado.

---

## ALTO IMPACTO

### 1. Gerador Angular: DataGridItem não segue padrão real

**Problema:** O grid gerado usa spread operator (`...item`) em vez de construir `DataGridItem` com array de cells e callbacks.

**Hoje gera:**
```typescript
this.listaGrid = result.listVO.map(item => ({
  ...item,
  editar: () => this.service.setModoEditar(item),
  excluir: () => this.service.handleDeletar(item.id)
}));
```

**Deveria gerar:**
```typescript
this.listaGrid = result.listVO.map(item => this.buildDataGridItem(item));

private buildDataGridItem(model: Model): DataGridItem {
  return {
    item: [
      { field: model.campo1, tooltip: model.campo1, textAlign: DataGridTextAlignEnum.center },
      { field: model.campo2, tooltip: model.campo2, textAlign: DataGridTextAlignEnum.left },
    ],
    loadLazy: (event) => this.loadLazy(event),
    editar: () => this.btnAlterar(model),
    desativar: () => this.btnDesativar(model),
    historico: () => this.btnHistorico(model.id),
  };
}
```

**Dados disponíveis:** `gridColumns` do DFM já tem field + header. Usar para gerar as cells.

---

### 2. Gerador Angular: Tela Monitor vs CRUD

**Problema:** O gerador sempre gera padrão CRUD (Container/Grid/Filtros/Cadastro). Telas de monitor não têm cadastro — têm ações de negócio.

**Heurística para detectar:** Se `buttonStateRules` tem ações como `business_method` (Cancelar, Reativar) e NÃO tem método `bbtSalvarClick`, é uma tela monitor.

**Impacto:** ~30% das telas do Logus são monitores, não CRUDs.

---

### 3. Gerador Angular: Service não segue padrão BehaviorSubject

**Problema:** O service gerado usa nomes genéricos (`grid$`, `selecionado$`). O padrão real usa `getGrid()`, `get{Feature}Selecionado()`, `changePage()`.

**Deveria seguir:** Padrão documentado no CLAUDE.md seção 5 (Feature Service).

---

### 4. Gerador Java: Repository JPQL não usa campos reais

**Problema:** O Repository gera `SELECT NEW Vo(e)` genérico. Deveria gerar `SELECT NEW Vo(e.campo1, e.campo2, ...)` com os campos do GridVo.

**Dados disponíveis:** `gridColumns` do DFM + `datasetFields` do DFM + `knownTables.columns` do banco.

---

## MEDIO IMPACTO

### 5. CalcCellColorRules: Legenda sem labels reais

**Problema:** Labels da legenda de cores (lblVerde→"Automático") não são detectados do DFM porque o parser .pas não tem acesso ao Caption do DFM.

**Solução:** Cruzar com `analyze_dfm_form` — labels TLabel com nome `lbl{Cor}` têm Caption no DFM. Passar DFM form para `extractCalcCellColorRules`.

---

### 6. Gerador Angular: Container não segue padrão Pages

**Problema:** O container gerado usa `isListMode`/`isEditMode` em vez do padrão `{Feature}Pages` enum com `changePage()`.

**Deveria seguir:** Padrão documentado no CLAUDE.md seção 1 (Container Component).

---

### 7. Gerador Angular: HTTP Service paths incorretos

**Problema:** O HTTP service gera endpoints como `/api/{feature}/...`. O padrão real usa `URL_API` importado de `app/startup.service` + constante `URL` local.

---

### 8. ButtonStateRules: bbtPesquisaFornecedor classificado como business_method

**Problema:** `bbtPesquisaFornecedorDisplayClick` que abre `TfrmPesquisaFornecedor` é classificado como `business_method` mas deveria ser `lookup` (busca de fornecedor).

**Solução:** Adicionar tipo `lookup` quando o target form é `PesquisaXxx` ou `ConsultaXxx`.

---

## BAIXO IMPACTO

### 9. Nomes de formControl: prefixos Delphi ainda aparecem

**Problema:** Alguns formControlNames mantêm prefixo Delphi: `lucFilial` → deveria ser `filial`.

**Solução:** O `componentToFormControl` já remove prefixos, mas o `extractFiltroFields` chama `toCamelCase` antes, que só faz lowercase da primeira letra sem remover prefixo.

---

### 10. CrossFormDataFlow: params não tipados

**Problema:** Quando o MakeShowModal recebe variáveis locais (não FieldByName), o tipo não é detectado.

```delphi
TfrmManutencaoPedidoAutomatico.MakeShowModal(lucFilial.KeyValue)
// KeyValue é Variant, tipo real não é detectável
```

**Solução:** Aceitar como limitação. Anotar `type: "unknown"` e deixar o dev resolver.
