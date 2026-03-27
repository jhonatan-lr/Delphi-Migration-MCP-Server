# Delphi Migration MCP Server

## REGRA CRITICA DE SEGURANCA — BANCO DE DADOS

**NUNCA executar INSERT, UPDATE, DELETE, DROP, ALTER, CREATE, TRUNCATE ou qualquer comando que modifique o banco de dados.**

O acesso ao banco Informix é SOMENTE LEITURA. A tool `learn_database` usa apenas SELECT em system catalog tables (systables, syscolumns, sysconstraints, sysindexes, sysreferences).

- Banco de desenvolvimento: `bd_desenv_m`
- Connection: `jdbc:informix-sqli://192.168.0.231:9088/bd_desenv_m:INFORMIXSERVER=ol_saturno`
- Credenciais: informix/informix
- **PROIBIDO**: INSERT, UPDATE, DELETE, DROP, ALTER, CREATE, TRUNCATE, EXEC, CALL
- **PERMITIDO**: SELECT apenas em system catalog tables (sys*)

Se qualquer query que nao seja SELECT puro for detectada, a tool DEVE rejeitar e retornar erro.

## Build

Este projeto requer **JDK 17**. O `JAVA_HOME` da maquina aponta para JDK 8 (usado pelo Logus), entao sempre setar explicitamente:

```bash
JAVA_HOME="C:/Program Files/Java/jdk-17" mvn compile
JAVA_HOME="C:/Program Files/Java/jdk-17" mvn package -DskipTests
JAVA_HOME="C:/Program Files/Java/jdk-17" mvn test
```

Apos build, reiniciar o MCP com `/mcp` no Claude Code para carregar o jar novo.

## Stack

- Java 17, Maven (compilacao do MCP server)
- MCP SDK `io.modelcontextprotocol.sdk:mcp:0.9.0`
- Jackson para JSON, SLF4J + Logback para logs
- Sem Spring Boot — servidor MCP standalone via stdio

## Estrutura do codigo

```
src/main/java/com/migration/mcp/
  server/       -> Entry point (MCP SDK + STDIO)
  tools/        -> 12 MCP Tools (McpTools.java, DetectInconsistenciesTool.java, LearnRepositoryTool.java)
  parser/
    DelphiSourceParser.java   <- Parser .pas (metodos, SQL, regras de negocio)
    DfmFormParser.java        <- Parser .dfm (componentes visuais, mapeamento PrimeNG)
    RepositoryLearner.java    <- Varredura do repositorio (5400+ arquivos)
  generator/
    JavaCodeGenerator.java    <- 7 arquivos Java (padrao Logus API)
    AngularCodeGenerator.java <- 17 arquivos Angular (padrao Logus Web)
    MigrationPlanGenerator.java <- Plano de migracao
  model/
    ProjectProfile.java       <- Perfil completo do projeto aprendido
    ProjectProfileStore.java  <- Singleton + persistencia em disco
```

## Convencoes CRITICAS (nao violar)

### OOM Prevention
- `RepositoryLearner` NUNCA concatenar todos os conteudos em uma string (String.join) — usar helpers `anyMatch`, `countMatchesAll`, `collectMatches` que iteram arquivo por arquivo
- Perfil persistido em caminho fixo: `C:\Users\Usuario\.delphi-mcp\project-profile.json`
- `.mcp.json` usa `-Xmx1024m` para o heap

### Charset
- Arquivos Delphi legados usam ISO-8859-1, nao UTF-8
- SEMPRE usar `AnalyzeDelphiUnitTool.readFileWithFallback(path)` em vez de `Files.readString(path, UTF_8)` direto
- O fallback tenta UTF-8 primeiro, cai para ISO-8859-1 em `MalformedInputException`

### Static initialization order
- Em `ProjectProfileStore`: INSTANCE deve vir DEPOIS do static block que inicializa PROFILE_DIR/PROFILE_FILE
- Campos static sao inicializados na ordem de declaracao em Java

### Tools - content vs file_path
- Todas as tools aceitam `content` e `file_path`
- Se `content` estiver vazio/em branco (isBlank), ler do `file_path` automaticamente
- Padrao: `args.containsKey("content") && !requireString(args, "content").isBlank()`

## Projeto alvo: Logus ERP

O MCP foi construido para migrar o **Logus ERP** — sistema Delphi 7 com 5400+ arquivos.

### Padrao da API (logus-corporativo-api)
- Java 8 / Spring Boot 2.1.2
- Camadas: Resource (controller), Service, Repository, Entity
- DTOs: DTO (entrada), VO (saida grid), PesquisaDto (filtros com LazyLoadDto), ResultDto (paginacao)
- `@Autowired` field injection (nao constructor injection)
- Swagger 2 (`@ApiOperation`, `@ApiParam`, springfox)
- Endpoints: `POST /pesquisar`, `POST /save`, `GET /getById/{id}`, `DELETE /delete/{id}`
- Error handling: `ValidationException` -> 409, `Exception` -> 500

### Padrao do Web (logus-corporativo-web)
- Angular 10 / PrimeNG 11 (NAO Angular Material)
- Padrao Container / Grid / Filtros / Cadastro (4 sub-components por feature)
- Service como state store com BehaviorSubject (grid$, selecionado$, alterarEditar$)
- HTTP service separado em `shared/services/http/`
- Shared components: DataGrid, Filtro, Button, ComponenteBasico, BotoesExportar
- ChangeDetectionStrategy.OnPush no Container
- Lazy loading via RouterModule.forChild

### Mapeamento de componentes (DFM -> PrimeNG)
- `TLgCorporativoLookupComboEdit` -> `<p-dropdown [filter]='true' [showClear]='true'>`
- `TLgBitBtn` / `PngBitBtn` -> `<button pButton>`
- `TJvDateEdit` -> `<p-calendar dateFormat='dd/mm/yy'>`
- `TJvValidateEdit` -> `<input pInputText> + Validators`
- `TwwDBGrid` -> `<p-table>` com paginator (shared DataGrid)
- `TGroupBox` -> `<p-fieldset legend='...'>`
- `TEdit` -> `<input pInputText>`
- `TCheckBox` -> `<p-checkbox>`
- `TClientDataSet` -> `BehaviorSubject<T[]>` no Service
- `TPageControl` -> `<p-tabView>`

## Melhorias pendentes (backlog)

### Alto impacto
1. SQL extraction truncada — queries vem cortadas, precisa concatenar todos os SQL.Add corretamente
2. Campos da Entity vazios — extrair campos do TClientDataSet/TSQLQuery do .dfm para gerar @Column
3. Validacoes no Java com sintaxe Delphi — converter para Java real
4. TJvDBLookupCombo sem mapeamento PrimeNG

### Medio impacto
5. Filtros component vazio — preencher com campos do .dfm
6. Grid sem colunas — extrair do TwwDBGrid.Selected.Strings
7. Model/Cadastro sem campos — preencher com dados da analise
8. Decodificar captions Delphi (#231#227 -> cedilha)

### Baixo impacto
9. Eventos falsos (ont -> False, ons -> False) sao propriedades parseadas como eventos
10. Prefixo 'frm' nos nomes de arquivo Java — remover

## README.md

O README.md deve ser mantido atualizado quando houver mudancas significativas nas tools, mapeamentos ou padroes de geracao. Ele serve como documentacao publica do projeto.


# Logus Corporativo Web - Padrões do Projeto

## Visão Geral
Projeto Angular com PrimeNG. Arquitetura baseada em feature modules com lazy loading, smart/dumb components, e gerenciamento de estado via BehaviorSubject nos services.

**Módulo de referência canônico:** `src/app/condicao-pagamento/` (CRUD simples e completo).

---

## Estrutura de Pastas de uma Feature CRUD

```
src/app/{nome-feature}/
├── components/
│   ├── {nome-feature}-container/
│   │   ├── {nome-feature}-container.component.ts
│   │   ├── {nome-feature}-container.component.html
│   │   └── {nome-feature}-container.module.ts
│   ├── {nome-feature}-grid/
│   │   ├── {nome-feature}-grid.component.ts
│   │   ├── {nome-feature}-grid.component.html
│   │   └── {nome-feature}-grid.module.ts
│   ├── {nome-feature}-cadastro/
│   │   ├── {nome-feature}-cadastro.component.ts
│   │   ├── {nome-feature}-cadastro.component.html
│   │   └── {nome-feature}-cadastro.module.ts
│   └── {nome-feature}-filtros/
│       ├── {nome-feature}-filtros.component.ts
│       ├── {nome-feature}-filtros.component.html
│       └── {nome-feature}-filtros.module.ts
├── models/
│   ├── {nome-feature}.model.ts
│   ├── {nome-feature}-filtros.model.ts
│   └── {nome-feature}.pages.ts
├── services/
│   └── {nome-feature}.service.ts
├── validators/
│   └── {nome-feature}.validator.ts
├── {nome-feature}.module.ts
└── {nome-feature}.routing.ts
```

O HTTP service fica em: `src/app/modules/shared/services/http/http-{nome-feature}.service.ts`

---

## Convenções Gerais

- **ChangeDetectionStrategy.OnPush** em TODOS os componentes
- **Reactive Forms** com `FormBuilder` (nunca template-driven forms)
- **BehaviorSubject** para estado nos services (nunca stores/ngrx)
- **`.pipe(first())`** em todas as chamadas HTTP dentro dos services
- **`@Injectable({ providedIn: "root" })`** em todos os services
- **Métodos `handleX()`** nos services para operações de negócio
- **Subscription cleanup** no `ngOnDestroy()` com array de `Subscription[]`
- **Lazy loading** via `loadChildren` no `src/app/app.route.ts`
- **Cada sub-component tem seu próprio módulo** (não ficam todos no feature module)
- **Sem CSS separado** na maioria dos componentes (usa classes globais do projeto)
- **Imports com alias `@shared`** apontam para `src/app/modules/shared`
- **URL_API** importado de `app/startup.service`

---

## 1. Container Component (Smart Component)

O container é o orquestrador da feature. Gerencia estado de página, loading e erros.

```typescript
import { Component, ChangeDetectionStrategy, OnDestroy } from "@angular/core";
import { Title } from "@shared/services/custom-title.service";
import { ErroDispacherService } from "@shared/services/erro-dispacher.service";
import { LoaderService } from "@shared/services/loader.service";
import { ManipulaErrorService } from "@shared/services/manipula-error.service";
import { Erro } from "app/common/model/erro.model";
import { {NomeFeature}Pages } from "app/{nome-feature}/models/{nome-feature}.pages";
import { {NomeFeature}Service } from "app/{nome-feature}/services/{nome-feature}.service";
import { Observable, Subscription } from "rxjs";
import { tap, filter } from "rxjs/operators";

const tituloPrincipal = "Título da Tela";

@Component({
  selector: "app-{nome-feature}-container",
  templateUrl: "./{nome-feature}-container.component.html",
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class {NomeFeature}ContainerComponent implements OnDestroy {
  private subscription: Subscription[] = [];

  loading$: Observable<boolean>;
  pages = {NomeFeature}Pages;
  currentPage: {NomeFeature}Pages = {NomeFeature}Pages.Inicio;

  constructor(
    private titleService: Title,
    private loaderService: LoaderService,
    private manipulaErroService: ManipulaErrorService,
    private erroDispacherService: ErroDispacherService,
    private service: {NomeFeature}Service
  ) {
    this.loading$ = this.loaderService.getLoading();
    this.initListners();
  }

  private initListners(): void {
    let erroSbscription = this.erroDispacherService
      .getErro()
      .pipe(
        filter((erro) => erro),
        tap(() => this.loaderService.setLoading(false)),
        tap((erro) => {
          this.manipulaErroService.manipulaErro(erro);
          this.erroDispacherService.setErro(null);
        })
      )
      .subscribe();
    this.subscription.push(erroSbscription);

    let currentPageSubscriptions = this.service
      .getCurrentPage()
      .pipe(
        tap((page) => (this.currentPage = page)),
        tap(() => {
          switch (this.currentPage) {
            case {NomeFeature}Pages.Inicio:
              this.titleService.setTitle(tituloPrincipal);
              break;
            case {NomeFeature}Pages.Novo:
              this.titleService.setTitle(tituloPrincipal + " - Novo");
              break;
            case {NomeFeature}Pages.Editar:
              this.titleService.setTitle(tituloPrincipal + " - Editar");
              break;
          }
        })
      )
      .subscribe();
    this.subscription.push(currentPageSubscriptions);
  }

  get erro(): Erro {
    return this.manipulaErroService.erro;
  }

  public ngOnDestroy(): void {
    this.subscription.forEach((subscription) => subscription.unsubscribe());
  }
}
```

### Template do Container

```html
<div [hidden]="currentPage !== pages.Inicio">
  <app-{nome-feature}-filtros></app-{nome-feature}-filtros>
</div>
<div [hidden]="currentPage !== pages.Inicio">
  <app-{nome-feature}-grid></app-{nome-feature}-grid>
</div>
<div *ngIf="currentPage === pages.Novo || currentPage === pages.Editar">
  <app-{nome-feature}-cadastro></app-{nome-feature}-cadastro>
</div>

<app-componente-basico
  [exibirCarregando]="loading$ | async"
  [erroModel]="erro"
  [confirmDialog]="true"
  [msg]="[]"
>
</app-componente-basico>
```

**Nota:** filtros e grid usam `[hidden]` (ficam no DOM), cadastro usa `*ngIf` (é criado/destruído).

### Module do Container

```typescript
@NgModule({
  declarations: [{NomeFeature}ContainerComponent],
  imports: [CommonModule],
  exports: [{NomeFeature}ContainerComponent],
})
export class {NomeFeature}ContainerModule {}
```

---

## 2. Grid Component

Exibe a lista de registros com `app-data-grid`, botões de exportar, novo e relatório.

```typescript
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from "@angular/core";
import { ConfiguracaoRelatorioControllerModel } from "@shared/configuracao-relatorio/model/configuracao-relatorio-controler.model";
import { ConfiguracaoRelatorioService } from "@shared/configuracao-relatorio/services/configuracao-relatorio.service";
import { DataGridItem } from "@shared/data-grid/data-grid-item";
import { Result } from "@shared/data-grid/data-grid-result";
import { DataGridTextAlignEnum } from "@shared/data-grid/data-grid-text-align.enum";
import { TipoRelatorioEnum } from "app/common/enum/tipo-relatorio.enum";
import { UtilsService } from "app/common/utils/utils.service";
import { {NomeFeature}Model } from "app/{nome-feature}/models/{nome-feature}.model";
import { {NomeFeature}Pages } from "app/{nome-feature}/models/{nome-feature}.pages";
import { {NomeFeature}Service } from "app/{nome-feature}/services/{nome-feature}.service";
import { LazyLoadEvent } from "primeng/api";
import { Subscription } from "rxjs";
import { filter, tap } from "rxjs/operators";

@Component({
  selector: "app-{nome-feature}-grid",
  templateUrl: "./{nome-feature}-grid.component.html",
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class {NomeFeature}GridComponent implements OnInit, OnDestroy {
  private subscription: Subscription[] = [];

  listaGrid: any[] = [];
  totalRegistros: number = 0;
  colunas: any[];

  constructor(
    private cd: ChangeDetectorRef,
    private utilService: UtilsService,
    private configuracaoRelatorioService: ConfiguracaoRelatorioService,
    private service: {NomeFeature}Service
  ) {
    this.initConfigRelatorio();
    this.initColunas();
  }

  private initConfigRelatorio(): void {
    let controllerConfigRelatorio: ConfiguracaoRelatorioControllerModel = {
      controller: "{nomeFeatureBackend}",
      actionGet: "getConfigRelatorio",
      actionSave: "saveConfigRelatorio",
    };
    this.configuracaoRelatorioService.initController(controllerConfigRelatorio);
    this.configuracaoRelatorioService.setConfiguracaoRelatorioByTipoRelatorio(TipoRelatorioEnum.ANALITICO);
  }

  private initColunas(): void {
    this.colunas = [
      { field: "campo1", header: "Campo 1", width: "10%" },
      { field: "campo2", header: "Campo 2", width: "40%" },
      { field: "status", header: "Status", width: "7%" },
    ];
  }

  public ngOnInit(): void {
    this.subscription.push(
      this.service
        .getGrid()
        .pipe(filter((res) => res !== undefined))
        .pipe(tap((res) => this.handlePesquisar(res)))
        .pipe(tap(() => this.cd.detectChanges()))
        .subscribe()
    );
  }

  private handlePesquisar(resultado: Result<{NomeFeature}Model>): void {
    this.listaGrid = [];
    this.totalRegistros = resultado.lazyDto.totalRegistros;
    resultado.listVO.forEach((model: {NomeFeature}Model) => {
      this.listaGrid.push(this.buildDataGridItem(model));
    });
  }

  private buildDataGridItem(model: {NomeFeature}Model): DataGridItem {
    return {
      item: [
        { field: model.campo1, tooltip: model.campo1, textAlign: DataGridTextAlignEnum.center },
        { field: model.campo2, tooltip: model.campo2, textAlign: DataGridTextAlignEnum.left },
        { field: model.status, tooltip: model.status, textAlign: DataGridTextAlignEnum.center, statusAtivoInativo: true },
      ],
      itemDesativado: model.status != null,
      loadLazy: (event: LazyLoadEvent) => this.loadLazy(event),
      editar: () => this.btnAlterar(model),
      desativar: () => this.btnDesativar(model),
      historico: () => this.btnHistorico(model.id),
    };
  }

  public loadLazy(event: LazyLoadEvent): void {
    this.service.handleLoadLazy(this.utilService.getLazyDto(event));
  }

  public exportarGrid(target: string): void {
    this.service.handleExportarGrid({ target: target, colunas: this.colunas });
  }

  public btnDesativar(model: {NomeFeature}Model): void {
    this.service.handleDesativarOuAtivar(model);
  }

  public btnHistorico(id: number): void {
    this.service.handleHistorico(id);
  }

  public btnAlterar(model: {NomeFeature}Model): void {
    this.service.set{NomeFeature}Selecionado(model);
    this.service.changePage({NomeFeature}Pages.Editar);
  }

  public btnNovo(): void {
    this.service.changePage({NomeFeature}Pages.Novo);
  }

  public btnImprimir(target: string): void {
    // Relatório com configuração
    let relatorioFiltros = { tipoArquivo: target, tipoRelatorio: 1,
      configuracaoRelatorio: this.configuracaoRelatorioService.obterValorConfiguracaoRelatorioAtual() };
    this.configuracaoRelatorioService.gerarRelatorioComValidacao(
      relatorioFiltros, (filtros) => this.service.handleGerarRelatorio(filtros)
    );
  }

  public ngOnDestroy(): void {
    this.subscription.forEach((s) => s.unsubscribe());
  }
}
```

### Template do Grid

```html
<div class="row logus-row">
  <div class="col-lg-12">
    <app-data-grid
      [(ngModel)]="listaGrid"
      [columns]="colunas"
      [widthAcoes]="10"
      [totalRecords]="totalRegistros"
      (onLazyLoad)="loadLazy($event)"
    >
    </app-data-grid>
  </div>
</div>
<div class="row logus-row border-bottom">
  <div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
    <app-botoes-exportar (exportarEmitter)="exportarGrid($event)">
    </app-botoes-exportar>
  </div>
  <div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
    <app-button ngTypeButton="novo" ngClass="float-right" (onClick)="btnNovo()"></app-button>
    <app-report-button ngClass="p-button-secondary" class="float-right ml-2" (emittClickPrincipal)="btnImprimir($event)"></app-report-button>
    <app-configuracao-relatorio style="padding-left: 10px" ngClass="float-right"></app-configuracao-relatorio>
  </div>
</div>
```

### Module do Grid

```typescript
@NgModule({
  declarations: [{NomeFeature}GridComponent],
  imports: [
    CommonModule, FormsModule, ButtonModule, DataGridModule,
    ReportButtonModule, ReactiveFormsModule, BotoesExportarModule,
    ConfiguracaoRelatorioModule,
  ],
  exports: [{NomeFeature}GridComponent],
})
export class {NomeFeature}GridModule {}
```

---

## 3. Filtros Component

Formulário de pesquisa com `app-filtro`.

```typescript
import { Component, ChangeDetectionStrategy, Renderer2 } from "@angular/core";
import { FormBuilder, FormGroup, Validators } from "@angular/forms";
import { SharedMessageService } from "@shared/services/shared-message.service";
import { ValidationMessage } from "@shared/validation-message/validation-message";
import { BaseValidacaoService } from "app/common/utils/base/base-validacao.service";
import { {NomeFeature}FiltrosModel } from "app/{nome-feature}/models/{nome-feature}-filtros.model";
import { {NomeFeature}Service } from "app/{nome-feature}/services/{nome-feature}.service";

@Component({
  selector: "app-{nome-feature}-filtros",
  templateUrl: "./{nome-feature}-filtros.component.html",
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class {NomeFeature}FiltrosComponent {
  formGroup: FormGroup;
  validationMessage: ValidationMessage;

  constructor(
    private formBuilder: FormBuilder,
    private sharedMessageService: SharedMessageService,
    private baseValidacaoService: BaseValidacaoService,
    private service: {NomeFeature}Service,
    private renderer: Renderer2
  ) {
    this.validationMessage = { visibled: false, service: this.baseValidacaoService };
    this.buildFormGroup();
  }

  private buildFormGroup(): void {
    this.formGroup = this.formBuilder.group({
      campo1: [null, null],
      status: [1, null],
    });
  }

  public btnPesquisar(): void {
    let filtros: {NomeFeature}FiltrosModel = { ...this.formGroup.value };
    this.service.handlePesquisar(filtros);
  }

  public focusInFirstFilter(): void {
    this.renderer.selectRootElement("#primeiroCampo").focus();
  }

  public pesquisaEnter(event): void {
    if (event.keyCode == 13) {
      this.btnPesquisar();
    }
  }
}
```

### Template do Filtros

```html
<div [formGroup]="formGroup">
  <app-filtro
    (emmitOnAfterToggle)="focusInFirstFilter()"
    (emmitPesquisar)="btnPesquisar()"
    (keyup)="pesquisaEnter($event)"
  >
    <div class="ml-3" style="width: 20rem">
      <label>Campo</label>
      <input pInputText id="primeiroCampo" formControlName="campo1" type="text" maxlength="50" class="uppercase" />
    </div>
    <div class="ml-3" style="width: 17rem">
      <app-panel header="Status">
        <div>
          <p-radioButton label="Ativos" value="1" formControlName="status"></p-radioButton>
          <p-radioButton label="Inativos" value="2" formControlName="status"></p-radioButton>
          <p-radioButton label="Todos" value="3" formControlName="status"></p-radioButton>
        </div>
      </app-panel>
    </div>
  </app-filtro>
</div>
```

### Module do Filtros

```typescript
@NgModule({
  declarations: [{NomeFeature}FiltrosComponent],
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule, FiltroModule,
    InputTextModule, DropdownMultiselectModule, CheckboxModule,
    ValidationMessageModule, PanelModule, RadioButtonModule,
  ],
  exports: [{NomeFeature}FiltrosComponent],
})
export class {NomeFeature}FiltrosModule {}
```

---

## 4. Cadastro Component (Formulário)

Formulário de criação/edição com Reactive Forms.

```typescript
import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, OnInit, ViewChild } from "@angular/core";
import { FormBuilder, FormGroup, Validators } from "@angular/forms";
import { SharedMessageService } from "@shared/services/shared-message.service";
import { ValidationMessage } from "@shared/validation-message/validation-message";
import { BaseValidacaoService } from "app/common/utils/base/base-validacao.service";
import { {NomeFeature}Model } from "app/{nome-feature}/models/{nome-feature}.model";
import { {NomeFeature}Pages } from "app/{nome-feature}/models/{nome-feature}.pages";
import { {NomeFeature}Service } from "app/{nome-feature}/services/{nome-feature}.service";
import { Subscription } from "rxjs";
import { tap } from "rxjs/operators";

@Component({
  selector: "app-{nome-feature}-cadastro",
  templateUrl: "./{nome-feature}-cadastro.component.html",
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class {NomeFeature}CadastroComponent implements OnInit, OnDestroy, AfterViewInit {
  @ViewChild("focoPrimeiroCampo") focoPrimeiroCampo: { input: { nativeElement: { focus: () => void } } };

  private subscription: Subscription;
  isEdicao: boolean = false;
  validationMessage: ValidationMessage;
  formGroup: FormGroup;

  constructor(
    private service: {NomeFeature}Service,
    private baseValidacaoService: BaseValidacaoService,
    private sharedMessageService: SharedMessageService,
    private formBuilder: FormBuilder
  ) {
    this.validationMessage = { visibled: false, service: this.baseValidacaoService };
  }

  public ngOnInit(): void {
    this.formGroup = this.formBuilder.group({
      id: [null],
      campo1: [null, [Validators.required]],
      campo2: [null, [Validators.required]],
    });
    this.initListners();
  }

  public ngAfterViewInit(): void {
    this.focoPrimeiroCampo.input.nativeElement.focus();
  }

  private initListners(): void {
    this.subscription = this.service
      .get{NomeFeature}Selecionado()
      .pipe(
        tap((param) => {
          if (param) {
            setTimeout(() => {
              this.isEdicao = true;
              this.formGroup.patchValue({
                id: param.id,
                campo1: param.campo1,
                campo2: param.campo2,
              });
            }, 100);
          }
        })
      )
      .subscribe();
  }

  public btnSalvar(): void {
    let dados: {NomeFeature}Model = {
      id: this.formGroup.controls.id.value,
      campo1: this.formGroup.controls.campo1.value,
      campo2: this.formGroup.controls.campo2.value,
    };

    if (!this.formGroup.valid) {
      this.validationMessage.visibled = true;
      return;
    }

    this.service.set{NomeFeature}Selecionado(null);
    this.service.handleSalvar(dados);
  }

  public btnVoltar(): void {
    this.service.set{NomeFeature}Selecionado(null);
    this.service.changePage({NomeFeature}Pages.Inicio);
  }

  public ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
```

### Template do Cadastro

```html
<div [formGroup]="formGroup">
  <div class="row logus-row">
    <div class="ml-3" style="width: 20rem">
      <label>Campo 1<span class="ask-obrigatorio">*</span></label>
      <input pInputText #focoPrimeiroCampo formControlName="campo1" type="text" maxlength="50" class="uppercase" />
      <app-validation-message [validationMessage]="validationMessage" [control]="formGroup.controls.campo1"></app-validation-message>
    </div>
  </div>
</div>
<div class="row logus-row border-bottom">
  <div class="col-xs-6 col-sm-6 col-md-6 col-lg-12">
    <app-button ngTypeButton="salvar" ngClass="float-right" (onClick)="btnSalvar()"></app-button>
    <app-button ngTypeButton="voltar" ngClass="float-right" (onClick)="btnVoltar()"></app-button>
  </div>
</div>
```

### Module do Cadastro

```typescript
@NgModule({
  declarations: [{NomeFeature}CadastroComponent],
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule, ButtonModule,
    ValidationMessageModule, InputTextModule, InputNumberModule,
    DropdownMultiselectModule, TooltipModule,
  ],
  exports: [{NomeFeature}CadastroComponent],
})
export class {NomeFeature}CadastroModule {}
```

---

## 5. Feature Service (Gerenciamento de Estado)

O service centraliza toda a lógica de negócio e estado da feature.

```typescript
import { Injectable } from "@angular/core";
import { ExportGridModel } from "@shared/botoes-exportar/export-grid.model";
import { Result } from "@shared/data-grid/data-grid-result";
import { LogModel } from "@shared/log/model/log.model";
import { LogService } from "@shared/log/services/log.service";
import { ErroDispacherService } from "@shared/services/erro-dispacher.service";
import { Http{NomeFeature}Service } from "@shared/services/http/http-{nome-feature}.service";
import { LoaderService } from "@shared/services/loader.service";
import { SharedMessageService } from "@shared/services/shared-message.service";
import { LazyLoadDto } from "app/common/model/lazy-load.model";
import { RelatorioModel } from "app/common/model/relatorio.model";
import { ExportDataModel } from "app/common/utils/export-data-table/export/file-exporter";
import { FileExporterHelper } from "app/common/utils/export-data-table/export/file-exporter-helper";
import { UtilsService } from "app/common/utils/utils.service";
import { ConfirmationService } from "primeng/api";
import { BehaviorSubject, Observable } from "rxjs";
import { first } from "rxjs/operators";

const tituloPrincipal = "Título da Feature";

@Injectable({ providedIn: "root" })
export class {NomeFeature}Service {
  private filtros: {NomeFeature}FiltrosModel = this.buildFiltrosInicial();
  private gridSubject = new BehaviorSubject<Result<{NomeFeature}Model>>(undefined);
  private selecionadoSubject = new BehaviorSubject<{NomeFeature}Model>(undefined);
  private pageSubject = new BehaviorSubject<{NomeFeature}Pages>({NomeFeature}Pages.Inicio);

  constructor(
    private logService: LogService,
    private httpService: Http{NomeFeature}Service,
    private utilService: UtilsService,
    private loaderService: LoaderService,
    private confirmacaoService: ConfirmationService,
    private sharedMessageService: SharedMessageService,
    private erroDispacherService: ErroDispacherService
  ) {}

  // --- Handlers (métodos públicos de ação) ---

  public handlePesquisar(filtros: {NomeFeature}FiltrosModel): void {
    this.filtros = filtros;
    this.loaderService.setLoading(true);
    this.httpService.pesquisar(filtros).pipe(first()).subscribe(
      (res) => { this.loaderService.setLoading(false); this.gridSubject.next(res); },
      (err) => { this.erroDispacherService.setErro(err); }
    );
  }

  public handleLoadLazy(lazyDto: LazyLoadDto): void {
    this.filtros.lazyDto = lazyDto;
    this.handlePesquisar(this.filtros);
  }

  public handleExportarGrid(model: ExportGridModel): void {
    this.loaderService.setLoading(true);
    this.httpService.exportarGrid(this.filtros).pipe(first()).subscribe(
      (res) => {
        this.loaderService.setLoading(false);
        if (res.length <= 0) {
          this.sharedMessageService.showNaoExistemDadosParaExportarMessage();
        } else {
          let dataExporter = new FileExporterHelper();
          let dados: ExportDataModel = { target: model.target, cols: model.colunas, data: res, fileName: tituloPrincipal };
          dataExporter.export(dados);
        }
      },
      (err) => { this.erroDispacherService.setErro(err); }
    );
  }

  public handleSalvar(model: {NomeFeature}Model): void {
    this.loaderService.setLoading(true);
    this.httpService.salvar(model).pipe(first()).subscribe(
      () => {
        this.loaderService.setLoading(false);
        this.sharedMessageService.showDadosSalvosComSucessoMessage();
        this.changePage({NomeFeature}Pages.Inicio);
        this.handlePesquisar(this.filtros);
      },
      (err) => { this.erroDispacherService.setErro(err); }
    );
  }

  public handleDesativarOuAtivar(model: {NomeFeature}Model): void {
    this.confirmacaoService.confirm({
      message: "Deseja realmente " + (model.status ? "ativar" : "desativar") + " o registro?",
      header: "Confirmação",
      icon: "fa fa-question-circle",
      accept: () => { this.desativarOuAtivar(model.id); },
      reject: () => { return; },
    });
  }

  private desativarOuAtivar(id: number): void {
    this.loaderService.setLoading(true);
    this.httpService.desativarOuAtivar(id).pipe(first()).subscribe(
      () => {
        this.loaderService.setLoading(false);
        this.handlePesquisar(this.filtros);
        this.sharedMessageService.showRegistroExcluidoMessage();
      },
      (err) => { this.erroDispacherService.setErro(err); }
    );
  }

  public handleHistorico(id: number): void {
    let log: LogModel = {
      titulo: tituloPrincipal + " - Histórico",
      lazyLoadLog: null,
      controller: "log{NomeFeature}",
      action: "listAllDetalhe",
      parameter: id,
      reordenar: false,
      utilizaExportacaoPrimeNG: true,
    };
    this.logService.carregaLog(log);
  }

  // --- Filtros inicial ---

  private buildFiltrosInicial(): {NomeFeature}FiltrosModel {
    return { status: 1, lazyDto: null };
  }

  // --- Getters/Setters de estado ---

  public set{NomeFeature}Selecionado(row: {NomeFeature}Model): void {
    this.selecionadoSubject.next(row);
  }

  public get{NomeFeature}Selecionado(): Observable<{NomeFeature}Model> {
    return this.selecionadoSubject.asObservable();
  }

  public getGrid(): Observable<Result<{NomeFeature}Model>> {
    return this.gridSubject.asObservable();
  }

  public changePage(page: {NomeFeature}Pages): void {
    this.pageSubject.next(page);
  }

  public getCurrentPage(): Observable<{NomeFeature}Pages> {
    return this.pageSubject.asObservable();
  }
}
```

---

## 6. HTTP Service

Fica na pasta shared. Responsável apenas por chamadas HTTP.

```typescript
import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Result } from "@shared/data-grid/data-grid-result";
import { RelatorioModel } from "app/common/model/relatorio.model";
import { URL_API } from "app/startup.service";
import { Observable } from "rxjs";

const URL = "{nomeFeatureBackend}";

@Injectable({ providedIn: "root" })
export class Http{NomeFeature}Service {
  constructor(private http?: HttpClient) {}

  public pesquisar(filtros: {NomeFeature}FiltrosModel): Observable<Result<{NomeFeature}Model>> {
    return this.http.post<Result<{NomeFeature}Model>>(`${URL_API}${URL}/pesquisar`, filtros);
  }

  public exportarGrid(filtros: {NomeFeature}FiltrosModel): Observable<{NomeFeature}Model[]> {
    return this.http.post<{NomeFeature}Model[]>(`${URL_API}${URL}/exportar`, filtros);
  }

  public salvar(model: {NomeFeature}Model): Observable<any> {
    return this.http.post<any>(`${URL_API}${URL}/salvar`, model);
  }

  public desativarOuAtivar(id: number): Observable<any> {
    return this.http.put<any>(`${URL_API}${URL}/disableOrEnableById/${id}`, null);
  }

  public gerarRelatorio(data: any): Observable<RelatorioModel> {
    return this.http.post<RelatorioModel>(`${URL_API}${URL}/relatorio`, data);
  }
}
```

---

## 7. Models

### Model principal (interface)

```typescript
export interface {NomeFeature}Model {
  id?: number;
  campo1?: string;
  campo2?: string;
  status?: string;
}
```

### Filtros model

```typescript
import { LazyLoadDto } from "app/common/model/lazy-load.model";

export interface {NomeFeature}FiltrosModel {
  campo1?: string;
  status?: number;
  lazyDto?: LazyLoadDto;
}
```

### Pages enum

```typescript
export enum {NomeFeature}Pages {
  Inicio = "inicio",
  Novo = "novo",
  Editar = "editar",
}
```

---

## 8. Validators

Funções puras exportadas (não classes).

```typescript
import { SharedMessageService } from "@shared/services/shared-message.service";

export function is{NomeFeature}Valid(
  model: {NomeFeature}Model,
  sharedMessageService: SharedMessageService
): boolean {
  // Validações de negócio
  return true;
}

export function is{NomeFeature}FiltrosValid(model: {NomeFeature}FiltrosModel): boolean {
  if (model == null) return false;
  return true;
}
```

---

## 9. Feature Module

```typescript
@NgModule({
  declarations: [{NomeFeature}ContainerComponent],
  imports: [
    CommonModule,
    ComponenteBasicoModule,
    {NomeFeature}CadastroModule,
    {NomeFeature}FiltrosModule,
    {NomeFeature}GridModule,
    {NomeFeature}Routing,
  ],
  exports: [{NomeFeature}ContainerComponent],
})
export class {NomeFeature}Module {}
```

---

## 10. Routing Module

```typescript
import { RouterModule, Routes } from "@angular/router";
import { NgModule } from "@angular/core";

export const routes: Routes = [
  { path: "", component: {NomeFeature}ContainerComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class {NomeFeature}Routing {}
```

---

## 11. Registro da Rota no App (Lazy Loading)

Em `src/app/app.route.ts`, adicionar:

```typescript
{ path: '{nome-feature}', loadChildren: () => import('./{nome-feature}/{nome-feature}.module').then((m) => m.{NomeFeature}Module) },
```

---

## Componentes Shared Disponíveis

| Componente | Import | Uso |
|---|---|---|
| `app-data-grid` | `DataGridModule` de `@shared/data-grid/data-grid.module` | Grid principal com lazy load |
| `app-button` | `ButtonModule` de `@shared/button/button.module` | Botões (novo, salvar, voltar, excluir, etc.) |
| `app-filtro` | `FiltroModule` de `@shared/filtro/filtro.module` | Container de filtros com toggle |
| `app-validation-message` | `ValidationMessageModule` de `@shared/validation-message/validation-message.module` | Mensagens de validação |
| `app-dropdown-multiselect` | `DropdownMultiselectModule` de `@shared/dropdown-multiselect/dropdown-multiselect.module` | Dropdown/multiselect |
| `app-botoes-exportar` | `BotoesExportarModule` de `@shared/botoes-exportar/botoes-exportar.module` | Botões Excel/CSV |
| `app-componente-basico` | `ComponenteBasicoModule` de `@shared/componente-basico/componente-basico.module` | Loading, erro, confirm dialog |
| `app-report-button` | `ReportButtonModule` de `@shared/report-button/report.button.module` | Botão de relatório PDF/CSV |
| `app-configuracao-relatorio` | `ConfiguracaoRelatorioModule` de `@shared/configuracao-relatorio/configuracao-relatorio.module` | Config de relatório |
| `app-panel` | `PanelModule` de `@shared/panel/panel.module` | Painel com header |
| `app-auto-complete` | `AutoCompleteModule` de `@shared/auto-complete/auto-complete.module` | Autocomplete customizado |

## Services Shared Disponíveis

| Service | Import | Uso |
|---|---|---|
| `LoaderService` | `@shared/services/loader.service` | Loading spinner global |
| `ErroDispacherService` | `@shared/services/erro-dispacher.service` | Despacho de erros |
| `ManipulaErrorService` | `@shared/services/manipula-error.service` | Exibição de erros |
| `SharedMessageService` | `@shared/services/shared-message.service` | Mensagens toast |
| `Title` | `@shared/services/custom-title.service` | Título da página |
| `LogService` | `@shared/log/services/log.service` | Histórico/auditoria |
| `ConfiguracaoRelatorioService` | `@shared/configuracao-relatorio/services/configuracao-relatorio.service` | Config relatórios |
| `BaseValidacaoService` | `app/common/utils/base/base-validacao.service` | Validações comuns |
| `UtilsService` | `app/common/utils/utils.service` | Utilitários gerais |
| `ConfirmationService` | `primeng/api` | Diálogos de confirmação |

## 12. Padrões de Campos em Filtros

### Filial (Dropdown/Multiselect)

Sempre usar `app-dropdown-multiselect` com `ngConsulta="filial"` (built-in). Internamente chama `GET /filial/listComboDtoFiscal/false`. Se a tela exigir um endpoint diferente (ex: `listAbastecidasCd`), usar `ngConsulta="custom"` com `[listaCombo$]` alimentado pelo service.

```html
<!-- Single select -->
<app-dropdown-multiselect ngType="dropdown" ngConsulta="filial" ngId="filialFiltro" formControlName="filialFiltro">
</app-dropdown-multiselect>

<!-- Multi select -->
<app-dropdown-multiselect ngType="multiselect" ngConsulta="filial" ngId="filiais" formControlName="filiais"
  [ngSelectAllItensPredefined]="true">
</app-dropdown-multiselect>

<!-- Custom endpoint (quando ngConsulta="filial" não atende) -->
<app-dropdown-multiselect ngType="dropdown" ngConsulta="custom" [listaCombo$]="filiaisCustom$"
  ngId="filialFiltro" formControlName="filialFiltro">
</app-dropdown-multiselect>
```

**Atributos comuns:**
- `ngConsulta="filial"` — combo padrão de filiais ativas (built-in)
- `[ngSelectAllItensPredefined]="true"` — pré-seleciona todos os itens
- `[ngPrimeiraOpcaoSelecionada]="true"` — seleciona o primeiro item
- `[desabilitado]="true"` — desabilita o campo
- `(onLoaded)="callback()"` — evento após carregar dados
- `(onChange)="callback()"` — evento ao mudar seleção

### Fornecedor (AutoComplete)

Sempre usar `app-auto-complete-fornecedores`. Busca por CNPJ, CPF, Razão Social ou Nome Fantasia. Mínimo 3 caracteres. Retorna `FornecedorComboModel { value: string, label: string, desabilitado: boolean }`.

```html
<app-auto-complete-fornecedores ngId="codFornecedor" formControlName="fornecedor">
</app-auto-complete-fornecedores>
```

**Extração do valor para o DTO:**
```typescript
// Padrão mais comum (null-safe)
codigoFornecedor: this.formGroup.controls.fornecedor.value?.value,

// Alternativa com variável intermediária
let fornecedor = this.formGroup.controls.fornecedor.value;
codigoFornecedor: fornecedor ? fornecedor.value : undefined,
```

**Importante:** `codigoFornecedor` é sempre `string` nos DTOs (CNPJ/CPF).

### Datas (Calendar)

Usar `app-calendar-basico`. Padrão De/Até para intervalos de período.

```html
<div class="col-xs-12 col-sm-6 col-md-6 col-lg-2">
  <label class="first">Data Inicial</label>
  <app-calendar-basico ngId="dataInicial" tooltipPosition="top" pTooltip="Data Inicial"
    formControlName="dataInicial">
  </app-calendar-basico>
  <app-validation-message [validationMessage]="validationMessage" [control]="formGroup.controls.dataInicial">
  </app-validation-message>
</div>
```

### Dropdown/Multiselect Customizado (BehaviorSubject)

Quando o combo vem de um endpoint específico, usar `ngConsulta="custom"` com `[listaCombo$]` (Observable).

```html
<app-dropdown-multiselect ngType="multiselect" ngConsulta="custom" [listaCombo$]="situacoes$"
  ngId="situacaoFiltro" formControlName="situacaoFiltro">
</app-dropdown-multiselect>
```

O Observable deve emitir `{ label: string, value: number }[]`.

### Divisão Mercadológica

Componente hierárquico (departamento → seção → grupo → subgrupo). Usar como `ControlValueAccessor` com `formControlName`.

```html
<div class="col-12">
  <app-divisao-mercadologica
    [ngSelectAllItensDepartamentos]="true"
    [ngSelectAllItensSecoes]="true"
    [ngSelectAllItensGrupos]="true"
    [ngSelectAllItensSubGrupos]="true"
    formControlName="divisaoMercadologica">
  </app-divisao-mercadologica>
</div>
```

**Extração do valor para o DTO:**
```typescript
let divisao = this.formGroup.controls.divisaoMercadologica.value;
listaDepartamento: divisao?.departamentos || [],
listaSecao: divisao?.secoes || [],
listaGrupo: divisao?.grupos || [],
listaSubGrupo: divisao?.subGrupos || [],
```

### Input Numérico

```html
<p-inputNumber formControlName="codigoPedido" [useGrouping]="false" [maxlength]="10">
</p-inputNumber>
```

### Composição Completa de Filtro (app-filtro)

```html
<div [formGroup]="formGroup">
  <app-filtro
    (emmitPesquisar)="btnPesquisar()"
    (emmitOnAfterToggle)="focusInFirstFilter()"
    (keydown.enter)="btnPesquisar()"
  >
    <!-- campos aqui -->
  </app-filtro>
</div>
```

---

## Tipos de Botão (ngTypeButton)

`novo`, `salvar`, `voltar`, `excluir`, `pesquisar`, `cancelar`, `limpar`, `confirmar`, `imprimir`, `custom`

Atalhos: Alt+N (novo), Alt+S (salvar), Alt+V (voltar)

## Classes CSS do Projeto

- `logus-row` - linha padrão
- `border-bottom` - borda inferior na row de botões
- `uppercase` - texto maiúsculo
- `float-right` - alinhamento à direita
- `ask-obrigatorio` - asterisco de campo obrigatório (*)
- `td-reticencias` - truncar texto em tabelas
- `center` - centralizar texto
