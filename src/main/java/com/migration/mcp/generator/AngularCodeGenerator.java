package com.migration.mcp.generator;

import com.migration.mcp.model.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Gera codigo Angular 10 + PrimeNG no padrao logus-corporativo-web.
 *
 * Padrao: Container / Grid / Filtros / Cadastro
 *  - Module com lazy loading
 *  - Service com BehaviorSubject (state store)
 *  - HTTP service separado em shared/services/http/
 *  - Models como interfaces TypeScript
 *  - Shared components: DataGridComponent, FiltroComponent, ComponenteBasicoComponent
 */
public class AngularCodeGenerator {

    /** Tabela principal do banco — usado para fallback de campos via TargetPatterns */
    private String currentTableName;

    /** Contexto de análise com regras extraídas do .pas */
    private AnalysisContext ctx;

    /**
     * Gera todos os arquivos Angular para um modulo baseado em um form Delphi.
     * Retorna Map<nomeArquivo, conteudo>.
     */
    public Map<String, String> generateModule(DfmForm form, DelphiClass dc, String tableName) {
        this.currentTableName = tableName;
        return generateModule(form, dc);
    }

    public Map<String, String> generateModule(DfmForm form, DelphiClass dc, String tableName, AnalysisContext ctx) {
        this.currentTableName = tableName;
        this.ctx = ctx;
        return generateModule(form, dc);
    }

    public Map<String, String> generateModule(DfmForm form, DelphiClass dc) {
        Map<String, String> files = new LinkedHashMap<>();
        String baseName = sanitizeName(form.getFormName());
        String kebab = toKebabCase(baseName);

        files.put(kebab + "/" + kebab + ".module.ts", genModule(baseName, kebab));
        files.put(kebab + "/" + kebab + ".routing.ts", genRouting(baseName, kebab));
        files.put(kebab + "/models/" + kebab + ".model.ts", genModel(baseName, dc, form));
        files.put(kebab + "/models/pesquisa-" + kebab + ".model.ts", genPesquisaModel(baseName, dc, form));
        files.put(kebab + "/models/" + kebab + ".pages.ts", genPages(baseName));
        files.put(kebab + "/services/" + kebab + ".service.ts", genService(baseName, kebab, dc, form));
        files.put("modules/shared/services/http/http-" + kebab + ".service.ts", genHttpService(baseName, kebab));
        files.put(kebab + "/components/" + kebab + "-container/" + kebab + "-container.component.ts", genContainer(baseName, kebab));
        files.put(kebab + "/components/" + kebab + "-container/" + kebab + "-container.component.html", genContainerHtml(baseName, kebab, form));
        files.put(kebab + "/components/" + kebab + "-grid/" + kebab + "-grid.component.ts", genGrid(baseName, kebab, dc, form));
        files.put(kebab + "/components/" + kebab + "-grid/" + kebab + "-grid.component.html", genGridHtml(kebab));
        files.put(kebab + "/components/" + kebab + "-grid/" + kebab + "-grid.module.ts", genGridModule(baseName, kebab));
        files.put(kebab + "/components/" + kebab + "-filtros/" + kebab + "-filtros.component.ts", genFiltros(baseName, kebab, dc, form));
        files.put(kebab + "/components/" + kebab + "-filtros/" + kebab + "-filtros.component.html", genFiltrosHtml(kebab, dc, form));
        files.put(kebab + "/components/" + kebab + "-filtros/" + kebab + "-filtros.module.ts", genFiltrosModule(baseName, kebab));
        files.put(kebab + "/components/" + kebab + "-cadastro/" + kebab + "-cadastro.component.ts", genCadastro(baseName, kebab, dc, form));
        files.put(kebab + "/components/" + kebab + "-cadastro/" + kebab + "-cadastro.component.html", genCadastroHtml(kebab, dc, form));
        files.put(kebab + "/components/" + kebab + "-cadastro/" + kebab + "-cadastro.module.ts", genCadastroModule(baseName, kebab));

        return files;
    }

    // ── Module ───────────────────────────────────────────────────────────────

    private String genModule(String name, String kebab) {
        String pascal = toPascalCase(name);
        return "import { NgModule } from '@angular/core';\n" +
               "import { CommonModule } from '@angular/common';\n" +
               "import { " + pascal + "RoutingModule } from './" + kebab + ".routing';\n" +
               "import { " + pascal + "ContainerComponent } from './components/" + kebab + "-container/" + kebab + "-container.component';\n" +
               "import { " + pascal + "GridModule } from './components/" + kebab + "-grid/" + kebab + "-grid.module';\n" +
               "import { " + pascal + "FiltrosModule } from './components/" + kebab + "-filtros/" + kebab + "-filtros.module';\n" +
               "import { " + pascal + "CadastroModule } from './components/" + kebab + "-cadastro/" + kebab + "-cadastro.module';\n" +
               "import { ComponenteBasicoModule } from '@shared/components/componente-basico/componente-basico.module';\n\n" +
               "@NgModule({\n" +
               "  declarations: [" + pascal + "ContainerComponent],\n" +
               "  imports: [\n" +
               "    CommonModule,\n" +
               "    " + pascal + "RoutingModule,\n" +
               "    " + pascal + "GridModule,\n" +
               "    " + pascal + "FiltrosModule,\n" +
               "    " + pascal + "CadastroModule,\n" +
               "    ComponenteBasicoModule\n" +
               "  ]\n" +
               "})\n" +
               "export class " + pascal + "Module { }\n";
    }

    // ── Routing ──────────────────────────────────────────────────────────────

    private String genRouting(String name, String kebab) {
        String pascal = toPascalCase(name);
        return "import { NgModule } from '@angular/core';\n" +
               "import { RouterModule } from '@angular/router';\n" +
               "import { " + pascal + "ContainerComponent } from './components/" + kebab + "-container/" + kebab + "-container.component';\n\n" +
               "@NgModule({\n" +
               "  imports: [RouterModule.forChild([{ path: '', component: " + pascal + "ContainerComponent }])],\n" +
               "  exports: [RouterModule]\n" +
               "})\n" +
               "export class " + pascal + "RoutingModule { }\n";
    }

    // ── Model ────────────────────────────────────────────────────────────────

    private String genModel(String name, DelphiClass dc, DfmForm form) {
        String pascal = toPascalCase(name);
        List<ResolvedField> fields = resolveFields(dc, form);
        StringBuilder sb = new StringBuilder();
        sb.append("export interface ").append(pascal).append("Model {\n");
        sb.append("  id: number;\n");
        for (ResolvedField f : fields) {
            sb.append("  ").append(f.camelName).append(": ").append(f.tsType).append(";\n");
        }
        sb.append("  isEdicao?: boolean;\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String genPesquisaModel(String name, DelphiClass dc) {
        return genPesquisaModel(name, dc, null);
    }

    private String genPesquisaModel(String name, DelphiClass dc, DfmForm form) {
        String pascal = toPascalCase(name);
        List<ResolvedField> fields = resolveFields(dc, form);
        StringBuilder sb = new StringBuilder();
        sb.append("import { LazyLoadDto } from '@shared/models/lazy-load-dto.model';\n\n");
        sb.append("export interface Pesquisa").append(pascal).append("Model {\n");
        for (ResolvedField f : fields) {
            sb.append("  ").append(f.camelName).append("?: string;\n");
        }
        sb.append("  lazyDto?: LazyLoadDto;\n");
        sb.append("}\n");
        return sb.toString();
    }

    // ── Service (state store) ────────────────────────────────────────────────

    private String genService(String name, String kebab, DelphiClass dc, DfmForm form) {
        String pascal = toPascalCase(name);
        String pagesImport = pascal + "Pages";
        String title = humanizeComponentName(name);

        StringBuilder sb = new StringBuilder();
        sb.append("import { Injectable } from '@angular/core';\n");
        sb.append("import { BehaviorSubject, Observable } from 'rxjs';\n");
        sb.append("import { first } from 'rxjs/operators';\n");
        sb.append("import { ExportGridModel } from '@shared/botoes-exportar/export-grid.model';\n");
        sb.append("import { Result } from '@shared/data-grid/data-grid-result';\n");
        sb.append("import { ErroDispacherService } from '@shared/services/erro-dispacher.service';\n");
        sb.append("import { Http").append(pascal).append("Service } from '@shared/services/http/http-").append(kebab).append(".service';\n");
        sb.append("import { LoaderService } from '@shared/services/loader.service';\n");
        sb.append("import { SharedMessageService } from '@shared/services/shared-message.service';\n");
        sb.append("import { UtilsService } from 'app/common/utils/utils.service';\n");
        sb.append("import { ConfirmationService } from 'primeng/api';\n");
        sb.append("import { LazyLoadDto } from 'app/common/model/lazy-load.model';\n");
        sb.append("import { ").append(pascal).append("Model } from 'app/").append(kebab).append("/models/").append(kebab).append(".model';\n");
        sb.append("import { Pesquisa").append(pascal).append("Model } from 'app/").append(kebab).append("/models/pesquisa-").append(kebab).append(".model';\n");
        sb.append("import { ").append(pagesImport).append(" } from 'app/").append(kebab).append("/models/").append(kebab).append(".pages';\n\n");

        sb.append("const tituloPrincipal = '").append(title).append("';\n\n");

        sb.append("@Injectable({ providedIn: 'root' })\n");
        sb.append("export class ").append(pascal).append("Service {\n\n");

        // BehaviorSubjects
        sb.append("  private filtros: Pesquisa").append(pascal).append("Model = this.buildFiltrosInicial();\n");
        sb.append("  private gridSubject = new BehaviorSubject<Result<").append(pascal).append("Model>>(undefined);\n");
        sb.append("  private selecionadoSubject = new BehaviorSubject<").append(pascal).append("Model>(undefined);\n");
        sb.append("  private pageSubject = new BehaviorSubject<").append(pagesImport).append(">(").append(pagesImport).append(".Inicio);\n\n");

        // Constructor
        sb.append("  constructor(\n");
        sb.append("    private httpService: Http").append(pascal).append("Service,\n");
        sb.append("    private utilService: UtilsService,\n");
        sb.append("    private loaderService: LoaderService,\n");
        sb.append("    private confirmacaoService: ConfirmationService,\n");
        sb.append("    private sharedMessageService: SharedMessageService,\n");
        sb.append("    private erroDispacherService: ErroDispacherService\n");
        sb.append("  ) { }\n\n");

        // handlePesquisar
        sb.append("  public handlePesquisar(filtros: Pesquisa").append(pascal).append("Model): void {\n");
        sb.append("    this.filtros = filtros;\n");
        sb.append("    this.loaderService.setLoading(true);\n");
        sb.append("    this.httpService.pesquisar(filtros).pipe(first()).subscribe(\n");
        sb.append("      (res) => { this.loaderService.setLoading(false); this.gridSubject.next(res); },\n");
        sb.append("      (err) => { this.erroDispacherService.setErro(err); }\n");
        sb.append("    );\n");
        sb.append("  }\n\n");

        // handleLoadLazy
        sb.append("  public handleLoadLazy(lazyDto: LazyLoadDto): void {\n");
        sb.append("    this.filtros.lazyDto = lazyDto;\n");
        sb.append("    this.handlePesquisar(this.filtros);\n");
        sb.append("  }\n\n");

        // handleSalvar
        sb.append("  public handleSalvar(model: ").append(pascal).append("Model): void {\n");
        sb.append("    this.loaderService.setLoading(true);\n");
        sb.append("    this.httpService.salvar(model).pipe(first()).subscribe(\n");
        sb.append("      () => {\n");
        sb.append("        this.loaderService.setLoading(false);\n");
        sb.append("        this.sharedMessageService.showDadosSalvosComSucessoMessage();\n");
        sb.append("        this.changePage(").append(pagesImport).append(".Inicio);\n");
        sb.append("        this.handlePesquisar(this.filtros);\n");
        sb.append("      },\n");
        sb.append("      (err) => { this.erroDispacherService.setErro(err); }\n");
        sb.append("    );\n");
        sb.append("  }\n\n");

        // handleDesativarOuAtivar
        sb.append("  public handleDesativarOuAtivar(model: ").append(pascal).append("Model): void {\n");
        sb.append("    this.confirmacaoService.confirm({\n");
        sb.append("      message: 'Deseja realmente desativar o registro?',\n");
        sb.append("      header: 'Confirmação',\n");
        sb.append("      icon: 'fa fa-question-circle',\n");
        sb.append("      accept: () => { this.desativarOuAtivar(model.id); },\n");
        sb.append("    });\n");
        sb.append("  }\n\n");

        sb.append("  private desativarOuAtivar(id: number): void {\n");
        sb.append("    this.loaderService.setLoading(true);\n");
        sb.append("    this.httpService.desativarOuAtivar(id).pipe(first()).subscribe(\n");
        sb.append("      () => {\n");
        sb.append("        this.loaderService.setLoading(false);\n");
        sb.append("        this.handlePesquisar(this.filtros);\n");
        sb.append("        this.sharedMessageService.showRegistroExcluidoMessage();\n");
        sb.append("      },\n");
        sb.append("      (err) => { this.erroDispacherService.setErro(err); }\n");
        sb.append("    );\n");
        sb.append("  }\n\n");

        // handleExportarGrid
        sb.append("  public handleExportarGrid(model: ExportGridModel): void {\n");
        sb.append("    this.loaderService.setLoading(true);\n");
        sb.append("    this.httpService.exportarGrid(this.filtros).pipe(first()).subscribe(\n");
        sb.append("      (res) => { this.loaderService.setLoading(false); /* TODO: export logic */ },\n");
        sb.append("      (err) => { this.erroDispacherService.setErro(err); }\n");
        sb.append("    );\n");
        sb.append("  }\n\n");

        // Filtros inicial
        sb.append("  private buildFiltrosInicial(): Pesquisa").append(pascal).append("Model {\n");
        sb.append("    return { status: 1, lazyDto: null };\n");
        sb.append("  }\n\n");

        // Getters / Setters de estado
        sb.append("  public set").append(pascal).append("Selecionado(row: ").append(pascal).append("Model): void {\n");
        sb.append("    this.selecionadoSubject.next(row);\n");
        sb.append("  }\n\n");

        sb.append("  public get").append(pascal).append("Selecionado(): Observable<").append(pascal).append("Model> {\n");
        sb.append("    return this.selecionadoSubject.asObservable();\n");
        sb.append("  }\n\n");

        sb.append("  public getGrid(): Observable<Result<").append(pascal).append("Model>> {\n");
        sb.append("    return this.gridSubject.asObservable();\n");
        sb.append("  }\n\n");

        sb.append("  public changePage(page: ").append(pagesImport).append("): void {\n");
        sb.append("    this.pageSubject.next(page);\n");
        sb.append("  }\n\n");

        sb.append("  public getCurrentPage(): Observable<").append(pagesImport).append("> {\n");
        sb.append("    return this.pageSubject.asObservable();\n");
        sb.append("  }\n");

        sb.append("}\n");
        return sb.toString();
    }

    // ── HTTP Service ─────────────────────────────────────────────────────────

    private String genHttpService(String name, String kebab) {
        String pascal = toPascalCase(name);
        return "import { HttpClient } from '@angular/common/http';\n" +
               "import { Injectable } from '@angular/core';\n" +
               "import { Result } from '@shared/data-grid/data-grid-result';\n" +
               "import { RelatorioModel } from 'app/common/model/relatorio.model';\n" +
               "import { URL_API } from 'app/startup.service';\n" +
               "import { Observable } from 'rxjs';\n\n" +
               "const URL = '" + kebab + "';\n\n" +
               "@Injectable({ providedIn: 'root' })\n" +
               "export class Http" + pascal + "Service {\n\n" +
               "  constructor(private http?: HttpClient) { }\n\n" +
               "  public pesquisar(filtros: any): Observable<Result<any>> {\n" +
               "    return this.http.post<Result<any>>(`${URL_API}${URL}/pesquisar`, filtros);\n" +
               "  }\n\n" +
               "  public exportarGrid(filtros: any): Observable<any[]> {\n" +
               "    return this.http.post<any[]>(`${URL_API}${URL}/exportar`, filtros);\n" +
               "  }\n\n" +
               "  public salvar(model: any): Observable<any> {\n" +
               "    return this.http.post<any>(`${URL_API}${URL}/salvar`, model);\n" +
               "  }\n\n" +
               "  public desativarOuAtivar(id: number): Observable<any> {\n" +
               "    return this.http.put<any>(`${URL_API}${URL}/disableOrEnableById/${id}`, null);\n" +
               "  }\n\n" +
               "  public gerarRelatorio(data: any): Observable<RelatorioModel> {\n" +
               "    return this.http.post<RelatorioModel>(`${URL_API}${URL}/relatorio`, data);\n" +
               "  }\n" +
               "}\n";
    }

    // ── Container ────────────────────────────────────────────────────────────

    private String genPages(String name) {
        String pascal = toPascalCase(name);
        return "export enum " + pascal + "Pages {\n" +
               "  Inicio = 'inicio',\n" +
               "  Novo = 'novo',\n" +
               "  Editar = 'editar',\n" +
               "}\n";
    }

    private String genContainer(String name, String kebab) {
        String pascal = toPascalCase(name);
        String title = humanizeComponentName(name);
        return "import { Component, ChangeDetectionStrategy, OnDestroy } from '@angular/core';\n" +
               "import { Title } from '@shared/services/custom-title.service';\n" +
               "import { ErroDispacherService } from '@shared/services/erro-dispacher.service';\n" +
               "import { LoaderService } from '@shared/services/loader.service';\n" +
               "import { ManipulaErrorService } from '@shared/services/manipula-error.service';\n" +
               "import { Erro } from 'app/common/model/erro.model';\n" +
               "import { " + pascal + "Pages } from 'app/" + kebab + "/models/" + kebab + ".pages';\n" +
               "import { " + pascal + "Service } from 'app/" + kebab + "/services/" + kebab + ".service';\n" +
               "import { Observable, Subscription } from 'rxjs';\n" +
               "import { tap, filter } from 'rxjs/operators';\n\n" +
               "const tituloPrincipal = '" + title + "';\n\n" +
               "@Component({\n" +
               "  selector: 'app-" + kebab + "-container',\n" +
               "  templateUrl: './" + kebab + "-container.component.html',\n" +
               "  changeDetection: ChangeDetectionStrategy.OnPush\n" +
               "})\n" +
               "export class " + pascal + "ContainerComponent implements OnDestroy {\n\n" +
               "  private subscription: Subscription[] = [];\n\n" +
               "  loading$: Observable<boolean>;\n" +
               "  pages = " + pascal + "Pages;\n" +
               "  currentPage: " + pascal + "Pages = " + pascal + "Pages.Inicio;\n\n" +
               "  constructor(\n" +
               "    private titleService: Title,\n" +
               "    private loaderService: LoaderService,\n" +
               "    private manipulaErroService: ManipulaErrorService,\n" +
               "    private erroDispacherService: ErroDispacherService,\n" +
               "    private service: " + pascal + "Service\n" +
               "  ) {\n" +
               "    this.loading$ = this.loaderService.getLoading();\n" +
               "    this.initListners();\n" +
               "  }\n\n" +
               "  private initListners(): void {\n" +
               "    let erroSbscription = this.erroDispacherService.getErro().pipe(\n" +
               "      filter((erro) => erro),\n" +
               "      tap(() => this.loaderService.setLoading(false)),\n" +
               "      tap((erro) => {\n" +
               "        this.manipulaErroService.manipulaErro(erro);\n" +
               "        this.erroDispacherService.setErro(null);\n" +
               "      })\n" +
               "    ).subscribe();\n" +
               "    this.subscription.push(erroSbscription);\n\n" +
               "    let currentPageSubscriptions = this.service.getCurrentPage().pipe(\n" +
               "      tap((page) => (this.currentPage = page)),\n" +
               "      tap(() => {\n" +
               "        switch (this.currentPage) {\n" +
               "          case " + pascal + "Pages.Inicio: this.titleService.setTitle(tituloPrincipal); break;\n" +
               "          case " + pascal + "Pages.Novo: this.titleService.setTitle(tituloPrincipal + ' - Novo'); break;\n" +
               "          case " + pascal + "Pages.Editar: this.titleService.setTitle(tituloPrincipal + ' - Editar'); break;\n" +
               "        }\n" +
               "      })\n" +
               "    ).subscribe();\n" +
               "    this.subscription.push(currentPageSubscriptions);\n" +
               "  }\n\n" +
               "  get erro(): Erro {\n" +
               "    return this.manipulaErroService.erro;\n" +
               "  }\n\n" +
               "  public ngOnDestroy(): void {\n" +
               "    this.subscription.forEach((subscription) => subscription.unsubscribe());\n" +
               "  }\n" +
               "}\n";
    }

    private String genContainerHtml(String name, String kebab, DfmForm form) {
        String pascal = toPascalCase(name);
        return "<div [hidden]=\"currentPage !== pages.Inicio\">\n" +
               "  <app-" + kebab + "-filtros></app-" + kebab + "-filtros>\n" +
               "</div>\n" +
               "<div [hidden]=\"currentPage !== pages.Inicio\">\n" +
               "  <app-" + kebab + "-grid></app-" + kebab + "-grid>\n" +
               "</div>\n" +
               "<div *ngIf=\"currentPage === pages.Novo || currentPage === pages.Editar\">\n" +
               "  <app-" + kebab + "-cadastro></app-" + kebab + "-cadastro>\n" +
               "</div>\n\n" +
               "<app-componente-basico\n" +
               "  [exibirCarregando]=\"loading$ | async\"\n" +
               "  [erroModel]=\"erro\"\n" +
               "  [confirmDialog]=\"true\"\n" +
               "  [msg]=\"[]\">\n" +
               "</app-componente-basico>\n";
    }

    // ── Grid ─────────────────────────────────────────────────────────────────

    private String genGrid(String name, String kebab, DelphiClass dc, DfmForm form) {
        String pascal = toPascalCase(name);
        List<ResolvedField> fields = resolveFields(dc, form);
        List<DfmForm.GridColumn> gridCols = resolveGridColumns(form, fields);

        // Calcula widths proporcionais
        int totalChars = gridCols.stream().mapToInt(DfmForm.GridColumn::getWidthChars).sum();
        if (totalChars <= 0) totalChars = gridCols.size() * 10;

        StringBuilder initColunas = new StringBuilder();
        initColunas.append("  private initColunas(): void {\n");
        initColunas.append("    this.colunas = [\n");
        for (DfmForm.GridColumn gc : gridCols) {
            String field = snakeToCamel(gc.getField());
            String header = gc.getHeader();
            if (gc.getSubHeader() != null && !gc.getSubHeader().isEmpty()) {
                header = gc.getSubHeader() + " " + header;
            }
            int widthPct = Math.max(5, Math.round((float) gc.getWidthChars() / totalChars * 95));
            initColunas.append("      { field: '").append(field).append("', header: '").append(header)
                .append("', width: '").append(widthPct).append("%' },\n");
        }
        initColunas.append("    ];\n");
        initColunas.append("  }\n\n");

        // buildDataGridItem com cells reais
        StringBuilder buildItem = new StringBuilder();
        buildItem.append("  private buildDataGridItem(model: ").append(pascal).append("Model): DataGridItem {\n");
        buildItem.append("    return {\n");
        buildItem.append("      item: [\n");
        for (int i = 0; i < gridCols.size(); i++) {
            DfmForm.GridColumn gc = gridCols.get(i);
            String field = snakeToCamel(gc.getField());
            String align = i == 0 ? "center" : "left";
            buildItem.append("        { field: model.").append(field).append(", tooltip: model.").append(field)
                     .append(", textAlign: DataGridTextAlignEnum.").append(align).append(" },\n");
        }
        buildItem.append("      ],\n");
        buildItem.append("      loadLazy: (event: LazyLoadEvent) => this.loadLazy(event),\n");
        buildItem.append("      editar: () => this.btnAlterar(model),\n");
        buildItem.append("      desativar: () => this.btnDesativar(model),\n");
        buildItem.append("      historico: () => this.btnHistorico(model.id),\n");
        buildItem.append("    };\n");
        buildItem.append("  }\n\n");

        StringBuilder sb = new StringBuilder();
        sb.append("import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';\n");
        sb.append("import { DataGridItem } from '@shared/data-grid/data-grid-item';\n");
        sb.append("import { Result } from '@shared/data-grid/data-grid-result';\n");
        sb.append("import { DataGridTextAlignEnum } from '@shared/data-grid/data-grid-text-align.enum';\n");
        sb.append("import { ").append(pascal).append("Model } from 'app/").append(kebab).append("/models/").append(kebab).append(".model';\n");
        sb.append("import { ").append(pascal).append("Pages } from 'app/").append(kebab).append("/models/").append(kebab).append(".pages';\n");
        sb.append("import { ").append(pascal).append("Service } from 'app/").append(kebab).append("/services/").append(kebab).append(".service';\n");
        sb.append("import { LazyLoadEvent } from 'primeng/api';\n");
        sb.append("import { Subscription } from 'rxjs';\n");
        sb.append("import { filter, tap } from 'rxjs/operators';\n\n");

        sb.append("@Component({\n");
        sb.append("  selector: 'app-").append(kebab).append("-grid',\n");
        sb.append("  templateUrl: './").append(kebab).append("-grid.component.html',\n");
        sb.append("  changeDetection: ChangeDetectionStrategy.OnPush\n");
        sb.append("})\n");
        sb.append("export class ").append(pascal).append("GridComponent implements OnInit, OnDestroy {\n\n");
        sb.append("  private subscription: Subscription[] = [];\n\n");
        sb.append("  listaGrid: any[] = [];\n");
        sb.append("  totalRegistros: number = 0;\n");
        sb.append("  colunas: any[];\n\n");

        sb.append("  constructor(\n");
        sb.append("    private cd: ChangeDetectorRef,\n");
        sb.append("    private service: ").append(pascal).append("Service\n");
        sb.append("  ) {\n");
        sb.append("    this.initColunas();\n");
        sb.append("  }\n\n");

        sb.append(initColunas);

        sb.append("  public ngOnInit(): void {\n");
        sb.append("    this.subscription.push(\n");
        sb.append("      this.service.getGrid().pipe(\n");
        sb.append("        filter((res) => res !== undefined),\n");
        sb.append("        tap((res) => this.handlePesquisar(res)),\n");
        sb.append("        tap(() => this.cd.detectChanges())\n");
        sb.append("      ).subscribe()\n");
        sb.append("    );\n");
        sb.append("  }\n\n");

        sb.append("  private handlePesquisar(resultado: Result<").append(pascal).append("Model>): void {\n");
        sb.append("    this.listaGrid = [];\n");
        sb.append("    this.totalRegistros = resultado.lazyDto.totalRegistros;\n");
        sb.append("    resultado.listVO.forEach((model: ").append(pascal).append("Model) => {\n");
        sb.append("      this.listaGrid.push(this.buildDataGridItem(model));\n");
        sb.append("    });\n");
        sb.append("  }\n\n");

        sb.append(buildItem);

        sb.append("  public loadLazy(event: LazyLoadEvent): void {\n");
        sb.append("    this.service.handleLoadLazy(this.service['utilService'].getLazyDto(event));\n");
        sb.append("  }\n\n");

        sb.append("  public exportarGrid(target: string): void {\n");
        sb.append("    this.service.handleExportarGrid({ target: target, colunas: this.colunas });\n");
        sb.append("  }\n\n");

        sb.append("  public btnAlterar(model: ").append(pascal).append("Model): void {\n");
        sb.append("    this.service.set").append(pascal).append("Selecionado(model);\n");
        sb.append("    this.service.changePage(").append(pascal).append("Pages.Editar);\n");
        sb.append("  }\n\n");

        sb.append("  public btnDesativar(model: ").append(pascal).append("Model): void {\n");
        sb.append("    this.service.handleDesativarOuAtivar(model);\n");
        sb.append("  }\n\n");

        sb.append("  public btnHistorico(id: number): void {\n");
        sb.append("    // TODO: this.service.handleHistorico(id);\n");
        sb.append("  }\n\n");

        sb.append("  public btnNovo(): void {\n");
        sb.append("    this.service.changePage(").append(pascal).append("Pages.Novo);\n");
        sb.append("  }\n\n");

        sb.append(genColorClassMethod());

        sb.append("  public ngOnDestroy(): void {\n");
        sb.append("    this.subscription.forEach((s) => s.unsubscribe());\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String genGridHtml(String kebab) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"row logus-row\">\n");
        sb.append("  <div class=\"col-lg-12\">\n");
        sb.append("    <app-data-grid\n");
        sb.append("      [(ngModel)]=\"listaGrid\"\n");
        sb.append("      [columns]=\"colunas\"\n");
        sb.append("      [totalRecords]=\"totalRegistros\"\n");
        sb.append("      (onLazyLoad)=\"loadLazy($event)\">\n");
        sb.append("    </app-data-grid>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");
        sb.append("<div class=\"row logus-row border-bottom\">\n");
        sb.append("  <div class=\"col-xs-12 col-sm-6 col-md-6 col-lg-6\">\n");
        sb.append("    <app-botoes-exportar (exportarEmitter)=\"exportarGrid($event)\"></app-botoes-exportar>\n");
        sb.append("  </div>\n");
        sb.append("  <div class=\"col-xs-12 col-sm-6 col-md-6 col-lg-6\">\n");
        sb.append("    <app-button ngTypeButton=\"novo\" ngClass=\"float-right\" (onClick)=\"btnNovo()\"></app-button>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");

        // Legenda de cores (se calcCellColorRules disponível)
        if (ctx != null && !ctx.getCalcCellColorRules().isEmpty()) {
            CalcCellColorRule colorRule = ctx.getCalcCellColorRules().get(0);
            if (!colorRule.getColorMappings().isEmpty()) {
                sb.append("<div class=\"row logus-row\" style=\"margin-top: 2px; font-size: 11px\">\n");
                sb.append("  <div class=\"ml-3\" style=\"display: flex; align-items: center; gap: 12px\">\n");
                for (CalcCellColorRule.ColorMapping cm : colorRule.getColorMappings()) {
                    String emoji = mapColorToEmoji(cm.getColor());
                    String label = cm.getLabel() != null ? cm.getLabel() : "Valor " + cm.getValue();
                    sb.append("    <span>").append(emoji).append(" ").append(label).append("</span>\n");
                }
                sb.append("  </div>\n");
                sb.append("</div>\n");
            }
        }

        return sb.toString();
    }

    private String genGridModule(String name, String kebab) {
        String pascal = toPascalCase(name);
        return "import { NgModule } from '@angular/core';\n" +
               "import { CommonModule } from '@angular/common';\n" +
               "import { " + pascal + "GridComponent } from './" + kebab + "-grid.component';\n" +
               "import { DataGridModule } from '@shared/components/data-grid/data-grid.module';\n" +
               "import { ButtonModule } from '@shared/components/button/button.module';\n" +
               "import { BotoesExportarModule } from '@shared/components/botoes-exportar/botoes-exportar.module';\n\n" +
               "@NgModule({\n" +
               "  declarations: [" + pascal + "GridComponent],\n" +
               "  imports: [CommonModule, DataGridModule, ButtonModule, BotoesExportarModule],\n" +
               "  exports: [" + pascal + "GridComponent]\n" +
               "})\n" +
               "export class " + pascal + "GridModule { }\n";
    }

    // ── Filtros ──────────────────────────────────────────────────────────────

    private String genFiltros(String name, String kebab, DelphiClass dc, DfmForm form) {
        String pascal = toPascalCase(name);
        List<FiltroField> filtros = extractFiltroFields(form);

        // Build formControls com defaults e validators do contexto
        StringBuilder formControls = new StringBuilder();
        Map<String, String> defaultValues = getDefaultValuesForFiltros();
        for (FiltroField f : filtros) {
            // f.originalName é o nome Delphi original (lucSituacaoPedido)
            String componentName = f.originalName != null ? f.originalName : f.name;
            String defaultVal = findDefaultForFiltro(componentName, defaultValues);
            String validator = getValidatorForField(componentName);
            formControls.append("      ").append(f.name).append(": [").append(defaultVal);
            if (validator != null) {
                formControls.append(", [").append(validator).append("]");
            }
            formControls.append("],\n");
        }

        // Auto-load no ngOnInit
        boolean hasAutoLoad = ctx != null && ctx.getFormInitialization().stream()
                .anyMatch(fi -> !fi.getAutoLoads().isEmpty());

        String lifecycle = hasAutoLoad ? "AfterViewInit" : "OnInit";
        String lifecycleImpl = hasAutoLoad ? "AfterViewInit" : "OnInit";
        String lifecycleMethod = hasAutoLoad ? "ngAfterViewInit" : "ngOnInit";
        String lifecycleImport = hasAutoLoad
                ? "import { Component, AfterViewInit, ChangeDetectionStrategy } from '@angular/core';\n"
                : "import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';\n";

        return lifecycleImport +
               "import { FormBuilder, FormGroup, Validators } from '@angular/forms';\n" +
               "import { " + pascal + "Service } from '../../services/" + kebab + ".service';\n" +
               "import { Pesquisa" + pascal + "Model } from '../../models/pesquisa-" + kebab + ".model';\n\n" +
               "@Component({\n" +
               "  selector: 'app-" + kebab + "-filtros',\n" +
               "  templateUrl: './" + kebab + "-filtros.component.html',\n" +
               "  changeDetection: ChangeDetectionStrategy.OnPush\n" +
               "})\n" +
               "export class " + pascal + "FiltrosComponent implements " + lifecycleImpl + " {\n\n" +
               "  formGroup: FormGroup;\n\n" +
               "  constructor(\n" +
               "    private formBuilder: FormBuilder,\n" +
               "    private service: " + pascal + "Service\n" +
               "  ) {\n" +
               "    this.formGroup = this.formBuilder.group({\n" +
               formControls +
               "    });\n" +
               "  }\n\n" +
               "  " + lifecycleMethod + "(): void {\n" +
               (hasAutoLoad ? "    setTimeout(() => this.btnPesquisar(), 500);\n" : "") +
               "  }\n\n" +
               "  btnPesquisar(): void {\n" +
               "    const filtros: Pesquisa" + pascal + "Model = { ...this.formGroup.value };\n" +
               "    this.service.handlePesquisar(filtros);\n" +
               "  }\n\n" +
               "  pesquisaEnter(event): void {\n" +
               "    if (event.keyCode === 13) { this.btnPesquisar(); }\n" +
               "  }\n" +
               "}\n";
    }

    private String genFiltrosHtml(String kebab, DelphiClass dc, DfmForm form) {
        List<FiltroField> filtros = extractFiltroFields(form);
        StringBuilder sb = new StringBuilder();
        sb.append("<div [formGroup]=\"formGroup\">\n");
        sb.append("  <app-filtro\n");
        sb.append("    (emmitPesquisar)=\"btnPesquisar()\"\n");
        sb.append("    (keyup)=\"pesquisaEnter($event)\">\n");
        for (FiltroField f : filtros) {
            String width = f.type.equals("calendar") ? "10rem" : "17rem";
            sb.append("    <div class=\"ml-3\" style=\"width: ").append(width).append("\">\n");
            sb.append("      <label>").append(f.label).append("</label>\n");
            switch (f.type) {
                case "dropdown":
                    sb.append("      <app-dropdown-multiselect ngType=\"dropdown\" ngConsulta=\"custom\"\n");
                    sb.append("        ngId=\"").append(f.name).append("\" formControlName=\"").append(f.name).append("\">\n");
                    sb.append("      </app-dropdown-multiselect>\n");
                    break;
                case "calendar":
                    sb.append("      <app-calendar-basico ngId=\"").append(f.name).append("\" formControlName=\"").append(f.name).append("\">\n");
                    sb.append("      </app-calendar-basico>\n");
                    break;
                case "checkbox":
                    sb.append("      <p-checkbox formControlName=\"").append(f.name).append("\" label=\"").append(f.label).append("\"></p-checkbox>\n");
                    break;
                default:
                    sb.append("      <input pInputText id=\"").append(f.name).append("\" formControlName=\"")
                      .append(f.name).append("\" type=\"text\" class=\"uppercase\" />\n");
                    break;
            }
            sb.append("    </div>\n");
        }
        sb.append("  </app-filtro>\n");
        sb.append("</div>\n");
        return sb.toString();
    }

    /** Representa um campo de filtro extraído dos componentes visuais do DFM */
    private static class FiltroField {
        String name;         // formControlName (camelCase)
        String originalName; // nome original Delphi (ex: lucSituacaoPedido)
        String label;        // label do campo
        String type;         // "input", "dropdown", "calendar", "checkbox"
        FiltroField(String name, String originalName, String label, String type) {
            this.name = name; this.originalName = originalName; this.label = label; this.type = type;
        }
    }

    /** Extrai campos de filtro dos componentes visuais do DFM */
    private List<FiltroField> extractFiltroFields(DfmForm form) {
        List<FiltroField> filtros = new ArrayList<>();
        if (form == null || form.getComponents() == null) return filtros;

        // Mapeia labels por posição: lblXxx.Caption -> próximo componente
        Map<String, String> labelCaptions = new LinkedHashMap<>();
        List<DfmComponent> comps = form.getComponents();
        for (int i = 0; i < comps.size(); i++) {
            DfmComponent c = comps.get(i);
            if (c.getDelphiType().equals("TLabel") && c.getProperties().containsKey("Caption")) {
                labelCaptions.put(c.getName(), c.getProperties().get("Caption"));
            }
        }

        for (DfmComponent c : comps) {
            String type = c.getDelphiType();
            String name = toCamelCase(c.getName());
            String originalName = c.getName(); // nome Delphi original para matching com AnalysisContext
            String label = findLabelForComponent(c, labelCaptions);

            if (type.contains("LookupCombo") || type.contains("LgCorporativo") || type.contains("DBLookupCombo")) {
                filtros.add(new FiltroField(name, originalName, label, "dropdown"));
            } else if (type.contains("DateEdit") || type.contains("Calendar")) {
                filtros.add(new FiltroField(name, originalName, label, "calendar"));
            } else if (type.equals("TEdit") || type.equals("TMaskEdit")) {
                filtros.add(new FiltroField(name, originalName, label, "input"));
            } else if (type.contains("CheckBox") && !type.contains("DB")) {
                String cbLabel = c.getProperties().getOrDefault("Caption", c.getName());
                filtros.add(new FiltroField(name, originalName, cbLabel, "checkbox"));
            }
        }
        return filtros;
    }

    /** Tenta achar o label mais provável para um componente (lblXxx para xxxComponent) */
    private String findLabelForComponent(DfmComponent comp, Map<String, String> labelCaptions) {
        String compName = comp.getName().toLowerCase();
        // Tenta lbl + NomeComponente (ex: lblFilial para lucFilial)
        for (Map.Entry<String, String> e : labelCaptions.entrySet()) {
            String lblName = e.getKey().toLowerCase().replace("lbl", "");
            String cName = compName.replaceAll("^(luc|edt|chk|cbo|cmb)", "");
            if (!lblName.isEmpty() && !cName.isEmpty() && (lblName.contains(cName) || cName.contains(lblName))) {
                return e.getValue();
            }
        }
        // Fallback: humaniza nome do componente (edtCodigoFornecedorDisplay → Código Fornecedor)
        String caption = comp.getProperties().getOrDefault("Caption",
               comp.getProperties().getOrDefault("Hint", null));
        if (caption != null && !caption.equals(comp.getName())) return caption;
        return humanizeComponentName(comp.getName());
    }

    private String genFiltrosModule(String name, String kebab) {
        String pascal = toPascalCase(name);
        return "import { NgModule } from '@angular/core';\n" +
               "import { CommonModule } from '@angular/common';\n" +
               "import { FormsModule, ReactiveFormsModule } from '@angular/forms';\n" +
               "import { " + pascal + "FiltrosComponent } from './" + kebab + "-filtros.component';\n" +
               "import { FiltroModule } from '@shared/components/filtro/filtro.module';\n" +
               "import { InputTextModule } from 'primeng/inputtext';\n" +
               "import { DropdownModule } from 'primeng/dropdown';\n" +
               "import { CheckboxModule } from 'primeng/checkbox';\n\n" +
               "@NgModule({\n" +
               "  declarations: [" + pascal + "FiltrosComponent],\n" +
               "  imports: [CommonModule, FormsModule, ReactiveFormsModule, FiltroModule, InputTextModule, DropdownModule, CheckboxModule],\n" +
               "  exports: [" + pascal + "FiltrosComponent]\n" +
               "})\n" +
               "export class " + pascal + "FiltrosModule { }\n";
    }

    // ── Cadastro ─────────────────────────────────────────────────────────────

    private String genCadastro(String name, String kebab, DelphiClass dc, DfmForm form) {
        String pascal = toPascalCase(name);
        List<ResolvedField> fields = resolveFields(dc, form);

        StringBuilder formControls = new StringBuilder();
        for (ResolvedField f : fields) {
            String validator = getValidatorForField(f.camelName);
            formControls.append("      ").append(f.camelName).append(": [null");
            if (validator != null) {
                formControls.append(", [").append(validator).append("]");
            }
            formControls.append("],\n");
        }

        return "import { Component, OnInit, OnDestroy } from '@angular/core';\n" +
               "import { FormBuilder, FormGroup, Validators } from '@angular/forms';\n" +
               "import { Subject } from 'rxjs';\n" +
               "import { takeUntil } from 'rxjs/operators';\n" +
               "import { " + pascal + "Service } from '../../services/" + kebab + ".service';\n" +
               "import { " + pascal + "Model } from '../../models/" + kebab + ".model';\n\n" +
               "@Component({\n" +
               "  selector: 'app-" + kebab + "-cadastro',\n" +
               "  templateUrl: './" + kebab + "-cadastro.component.html'\n" +
               "})\n" +
               "export class " + pascal + "CadastroComponent implements OnInit, OnDestroy {\n\n" +
               "  form: FormGroup;\n" +
               "  isEdicao = false;\n\n" +
               "  private destroy$ = new Subject<void>();\n\n" +
               "  constructor(\n" +
               "    private fb: FormBuilder,\n" +
               "    private service: " + pascal + "Service\n" +
               "  ) {\n" +
               "    this.form = this.fb.group({\n" +
               "      id: [null],\n" +
               formControls +
               "    });\n" +
               "  }\n\n" +
               "  ngOnInit(): void {\n" +
               "    this.service.selecionado$.pipe(takeUntil(this.destroy$)).subscribe(item => {\n" +
               "      if (item) {\n" +
               "        this.isEdicao = true;\n" +
               "        this.form.patchValue(item);\n" +
               "      } else {\n" +
               "        this.isEdicao = false;\n" +
               "        this.form.reset();\n" +
               "      }\n" +
               "    });\n" +
               "  }\n\n" +
               "  salvar(): void {\n" +
               "    if (this.form.invalid) { return; }\n" +
               "    const model: " + pascal + "Model = { ...this.form.getRawValue(), isEdicao: this.isEdicao };\n" +
               "    this.service.handleSalvar(model);\n" +
               "  }\n\n" +
               "  voltar(): void {\n" +
               "    this.service.setModoLista();\n" +
               "  }\n\n" +
               "  ngOnDestroy(): void {\n" +
               "    this.destroy$.next();\n" +
               "    this.destroy$.complete();\n" +
               "  }\n" +
               "}\n";
    }

    private String genCadastroHtml(String kebab, DelphiClass dc, DfmForm form) {
        List<ResolvedField> fields = resolveFields(dc, form);
        StringBuilder sb = new StringBuilder();
        sb.append("<div [formGroup]=\"form\">\n");
        sb.append("  <div class=\"row logus-row\">\n");

        for (ResolvedField f : fields) {
            String width = "20rem";
            boolean isRequired = isFieldRequired(f.camelName);
            sb.append("    <div class=\"ml-3\" style=\"width: ").append(width).append("\">\n");
            sb.append("      <label>").append(f.label);
            if (isRequired) sb.append("<span class=\"ask-obrigatorio\">*</span>");
            sb.append("</label>\n");

            if ("boolean".equals(f.tsType)) {
                sb.append("      <p-checkbox formControlName=\"").append(f.camelName).append("\" [binary]=\"true\" label=\"").append(f.label).append("\"></p-checkbox>\n");
            } else if ("BigDecimal".equals(f.javaType) || "number".equals(f.tsType)) {
                sb.append("      <p-inputNumber formControlName=\"").append(f.camelName).append("\" [useGrouping]=\"false\"></p-inputNumber>\n");
            } else if ("Date".equals(f.javaType) || "LogusDateTime".equals(f.javaType)) {
                sb.append("      <app-calendar-basico ngId=\"").append(f.camelName).append("\" formControlName=\"").append(f.camelName).append("\"></app-calendar-basico>\n");
            } else {
                sb.append("      <input pInputText formControlName=\"").append(f.camelName).append("\" type=\"text\" class=\"uppercase\" />\n");
            }

            if (isRequired) {
                sb.append("      <app-validation-message [validationMessage]=\"validationMessage\" [control]=\"form.controls.").append(f.camelName).append("\"></app-validation-message>\n");
            }
            sb.append("    </div>\n");
        }

        sb.append("  </div>\n");
        sb.append("</div>\n");
        sb.append("<div class=\"row logus-row border-bottom\">\n");
        sb.append("  <div class=\"col-xs-6 col-sm-6 col-md-6 col-lg-12\">\n");
        sb.append("    <app-button ngTypeButton=\"salvar\" ngClass=\"float-right\" (onClick)=\"salvar()\"></app-button>\n");
        sb.append("    <app-button ngTypeButton=\"voltar\" ngClass=\"float-right\" (onClick)=\"voltar()\"></app-button>\n");
        sb.append("  </div>\n");
        sb.append("    </div>\n");
        sb.append("  </form>\n");
        sb.append("</div>\n");
        return sb.toString();
    }

    private String genCadastroModule(String name, String kebab) {
        String pascal = toPascalCase(name);
        return "import { NgModule } from '@angular/core';\n" +
               "import { CommonModule } from '@angular/common';\n" +
               "import { FormsModule, ReactiveFormsModule } from '@angular/forms';\n" +
               "import { " + pascal + "CadastroComponent } from './" + kebab + "-cadastro.component';\n" +
               "import { ButtonModule } from '@shared/components/button/button.module';\n" +
               "import { ValidationMessageModule } from '@shared/components/validation-message/validation-message.module';\n" +
               "import { InputTextModule } from 'primeng/inputtext';\n" +
               "import { DropdownModule } from 'primeng/dropdown';\n" +
               "import { CheckboxModule } from 'primeng/checkbox';\n" +
               "import { CurrencyMaskModule } from 'ng2-currency-mask';\n\n" +
               "@NgModule({\n" +
               "  declarations: [" + pascal + "CadastroComponent],\n" +
               "  imports: [\n" +
               "    CommonModule, FormsModule, ReactiveFormsModule,\n" +
               "    ButtonModule, ValidationMessageModule,\n" +
               "    InputTextModule, DropdownModule, CheckboxModule, CurrencyMaskModule\n" +
               "  ],\n" +
               "  exports: [" + pascal + "CadastroComponent]\n" +
               "})\n" +
               "export class " + pascal + "CadastroModule { }\n";
    }

    // ── Utils ────────────────────────────────────────────────────────────────

    private List<DelphiField> nonComponentFields(DelphiClass dc) {
        if (dc == null || dc.getFields() == null) return Collections.emptyList();
        return dc.getFields().stream()
                .filter(f -> !f.isComponent())
                .collect(Collectors.toList());
    }

    private String sanitizeName(String name) {
        if (name == null) return "Form";
        return name.replaceAll("(?i)^(T)?(f_|F_|frm|fra|Form)", "")
                   .replaceAll("(?i)(Form|frm)$", "");
    }

    private String removePrefix(String fieldName) {
        // Remove F prefix (FNome -> Nome)
        if (fieldName.length() > 1 && fieldName.charAt(0) == 'F' && Character.isUpperCase(fieldName.charAt(1))) {
            return fieldName.substring(1);
        }
        return fieldName;
    }

    private String tsType(DelphiField f) {
        String t = f.getJavaType();
        if (t == null) return "string";
        switch (t) {
            case "Integer": case "Long": case "Double": case "Float": case "BigDecimal": return "number";
            case "Boolean": return "boolean";
            case "LocalDateTime": return "string";
            default: return "string";
        }
    }

    private String toKebabCase(String s) {
        if (s == null || s.isEmpty()) return "component";
        return s.replaceAll("([A-Z])", "-$1").toLowerCase().replaceAll("^-", "");
    }

    private String toPascalCase(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String toCamelCase(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /** Converte snake_case para camelCase usando columnNameExpansions quando disponível */
    private String snakeToCamel(String s) {
        if (s == null || s.isEmpty()) return s;
        // Tenta expandir prefixos via TargetPatterns (cdg→codigo, dcr→descricao, etc)
        Map<String, String> expansions = getColumnNameExpansions();
        String[] parts = s.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            String expanded = expansions.getOrDefault(part, part);
            if (i == 0) {
                sb.append(expanded.toLowerCase());
            } else {
                sb.append(Character.toUpperCase(expanded.charAt(0))).append(expanded.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    private Map<String, String> getColumnNameExpansions() {
        TargetPatterns tp = ProjectProfileStore.getInstance().getPatterns();
        if (tp != null && tp.getColumnNameExpansions() != null) {
            return tp.getColumnNameExpansions();
        }
        // Fallback: expansões mínimas hardcoded
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("cdg", "codigo"); defaults.put("dcr", "descricao"); defaults.put("dat", "data");
        defaults.put("nmr", "numero"); defaults.put("qtd", "quantidade"); defaults.put("val", "valor");
        defaults.put("flg", "flag"); defaults.put("flb", "flag"); defaults.put("pct", "percentual");
        defaults.put("sgl", "sigla"); defaults.put("hor", "hora"); defaults.put("ped", "pedido");
        defaults.put("prod", "produto"); defaults.put("auto", "automatico");
        return defaults;
    }

    /** Representa um campo resolvido (de DelphiClass ou DfmForm.DatasetField) */
    private static class ResolvedField {
        String camelName;  // cdgPedAuto
        String label;      // Ped Auto
        String tsType;     // number
        String javaType;   // Integer
        ResolvedField(String camelName, String label, String tsType, String javaType) {
            this.camelName = camelName; this.label = label; this.tsType = tsType; this.javaType = javaType;
        }
    }

    /** Resolve campos: primeiro tenta DelphiClass, se vazio usa DFM datasetFields */
    private List<ResolvedField> resolveFields(DelphiClass dc, DfmForm form) {
        List<ResolvedField> fields = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Tenta DelphiClass
        for (DelphiField f : nonComponentFields(dc)) {
            String name = toCamelCase(removePrefix(f.getName()));
            if (!name.isEmpty() && !name.equals("id") && seen.add(name)) {
                fields.add(new ResolvedField(name, removePrefix(f.getName()), tsType(f), f.getJavaType()));
            }
        }

        // Fallback 1: DFM datasetFields
        if (fields.isEmpty() && form != null && form.getDatasetFields() != null) {
            for (DfmForm.DatasetField df : form.getDatasetFields()) {
                String name = snakeToCamel(df.getName());
                if (!name.isEmpty() && !name.equals("id") && seen.add(name)) {
                    fields.add(new ResolvedField(name, buildLabel(df.getName()), df.getTsType(), df.getJavaType()));
                }
            }
        }

        // Fallback 2: Colunas reais do banco (via TargetPatterns)
        if (fields.isEmpty() && currentTableName != null) {
            TargetPatterns tp = ProjectProfileStore.getInstance().getPatterns();
            if (tp != null) {
                TargetPatterns.TablePattern table = tp.getKnownTables().get(currentTableName);
                if (table != null && table.getColumns() != null) {
                    for (TargetPatterns.ColumnPattern col : table.getColumns()) {
                        String name = snakeToCamel(col.getName());
                        if (!name.isEmpty() && !name.equals("id") && seen.add(name)) {
                            String tsType = mapJavaTypeToTs(col.getJavaType());
                            fields.add(new ResolvedField(name, buildLabel(col.getName()), tsType, col.getJavaType()));
                        }
                    }
                }
            }
        }

        return fields;
    }

    /** Converte tipo Java → TypeScript */
    private String mapJavaTypeToTs(String javaType) {
        if (javaType == null) return "string";
        return switch (javaType) {
            case "Integer", "Long", "BigDecimal", "Double" -> "number";
            case "Boolean" -> "boolean";
            case "LogusDateTime" -> "Date";
            default -> "string";
        };
    }

    /** Gera label a partir do nome de coluna do banco */
    private String buildLabel(String colName) {
        String label = colName.replaceAll("^(cdg_|dcr_|nmr_|dat_|flg_|flb_|qtd_|val_|pct_|sgl_|hor_)", "")
                              .replace("_", " ");
        String[] words = label.split(" ");
        StringBuilder lb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) lb.append(lb.isEmpty() ? "" : " ")
                               .append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return lb.toString();
    }

    /** Resolve colunas do grid: primeiro tenta gridColumns do DFM, senão usa campos */
    private List<DfmForm.GridColumn> resolveGridColumns(DfmForm form, List<ResolvedField> fields) {
        if (form != null && form.getGridColumns() != null && !form.getGridColumns().isEmpty()) {
            return form.getGridColumns();
        }
        // Fallback: cria colunas a partir dos campos
        List<DfmForm.GridColumn> cols = new ArrayList<>();
        for (ResolvedField f : fields) {
            cols.add(new DfmForm.GridColumn(f.camelName, f.label, "", 10));
        }
        return cols;
    }

    // ── Helpers para AnalysisContext ─────────────────────────────────────────

    /**
     * Extrai defaults para campos de filtro via formInitialization.
     * Chave é o nome do componente Delphi em lowercase (ex: "edtdataemissaode").
     * Quando há 2 entries pro mesmo campo (branch condicional), o último vence (put sobrescreve).
     */
    private Map<String, String> getDefaultValuesForFiltros() {
        Map<String, String> defaults = new LinkedHashMap<>();
        if (ctx == null) return defaults;

        for (FormInitialization fi : ctx.getFormInitialization()) {
            // Default values (datas, textos, etc)
            for (FormInitialization.DefaultValue dv : fi.getDefaultValues()) {
                String key = dv.getComponent().toLowerCase(); // key = nome Delphi lowercase
                String value = mapDefaultValue(dv.getValue(), dv.getProperty());
                defaults.put(key, value); // último vence (branch release)
            }
            // Combo preselections
            for (FormInitialization.ComboPreselection cp : fi.getComboPreselections()) {
                String key = cp.getComponent().toLowerCase();
                if (cp.getSelectedKeys() != null && !cp.getSelectedKeys().isEmpty()) {
                    boolean allNumeric = cp.getSelectedKeys().stream().allMatch(k -> k.matches("\\d+"));
                    boolean isExpression = cp.getSelectedKeys().stream().anyMatch(k -> k.contains(".") || k.contains("("));
                    if (isExpression) {
                        defaults.put(key, "null /* TODO: pré-selecionar " + cp.getSelectedKeys().get(0) + " */");
                    } else if (allNumeric) {
                        if (cp.getSelectedKeys().size() == 1) {
                            defaults.put(key, cp.getSelectedKeys().get(0));
                        } else {
                            defaults.put(key, "[" + String.join(", ", cp.getSelectedKeys()) + "]");
                        }
                    } else {
                        // Strings
                        String joined = cp.getSelectedKeys().stream()
                                .map(k -> "'" + k + "'")
                                .collect(Collectors.joining(", "));
                        defaults.put(key, cp.getSelectedKeys().size() == 1
                                ? "'" + cp.getSelectedKeys().get(0) + "'"
                                : "[" + joined + "]");
                    }
                }
            }
        }
        return defaults;
    }

    /** Mapeia valor Delphi para TypeScript */
    private String mapDefaultValue(String delphiValue, String property) {
        if (delphiValue == null) return "null";
        String val = delphiValue.trim();
        // Date - N (ex: Conexao.Date - 30)
        java.util.regex.Matcher dateMinus = java.util.regex.Pattern.compile(
                "(?i)(?:conexao\\.(?:date|now)|v(?:hoje|dataservidor))\\s*-\\s*(\\d+)").matcher(val);
        if (dateMinus.find()) {
            return "new Date(Date.now() - " + dateMinus.group(1) + " * 86400000)";
        }
        // Date + N
        java.util.regex.Matcher datePlus = java.util.regex.Pattern.compile(
                "(?i)(?:conexao\\.(?:date|now)|v(?:hoje|dataservidor))\\s*\\+\\s*(\\d+)").matcher(val);
        if (datePlus.find()) {
            return "new Date(Date.now() + " + datePlus.group(1) + " * 86400000)";
        }
        // Date exato
        String lower = val.toLowerCase();
        if (lower.matches("(?i)conexao\\.(date|now)") || lower.matches("(?i)v(hoje|dataservidor)")) {
            return "new Date()";
        }
        // Property é Date e valor contém date/now/hoje
        if (property != null && property.equalsIgnoreCase("Date") &&
            (lower.contains("date") || lower.contains("now") || lower.contains("hoje"))) {
            return "new Date()";
        }
        if (lower.equals("true") || lower.equals("false")) return lower;
        if (lower.matches("\\d+")) return val;
        if (lower.equals("''") || lower.equals("emptystr")) return "''";
        return "null /* TODO: " + val + " */";
    }

    /**
     * Busca default para um campo de filtro.
     * Aceita lookup por nome completo Delphi (ex: "lucSituacaoPedido") e por nome sem prefixo.
     */
    private String findDefaultForFiltro(String filtroComponentName, Map<String, String> defaults) {
        // Tenta match direto pelo nome lowercase
        String key = filtroComponentName.toLowerCase();
        if (defaults.containsKey(key)) return defaults.get(key);
        // Tenta match sem prefixo
        String stripped = key.replaceAll("^(edt|luc|cds|dts|bbt|lbl|grp|grd|pnl|chk|rdb|cbx)", "");
        for (Map.Entry<String, String> e : defaults.entrySet()) {
            String defStripped = e.getKey().replaceAll("^(edt|luc|cds|dts|bbt|lbl|grp|grd|pnl|chk|rdb|cbx)", "");
            if (defStripped.equals(stripped)) return e.getValue();
        }
        return "null";
    }

    /**
     * Busca validators para um campo.
     * Aceita lookup por nome completo Delphi e por nome sem prefixo.
     * Deduplicado: mesmo campo + mesmo validator = uma entrada.
     */
    private String getValidatorForField(String filtroComponentName) {
        if (ctx == null) return null;
        Set<String> validators = new LinkedHashSet<>(); // Set para deduplicar
        String key = filtroComponentName.toLowerCase();
        String keyStripped = key.replaceAll("^(edt|luc|cds|dts|bbt|lbl|grp|grd|pnl|chk|rdb|cbx)", "");

        for (FieldValidationRule fvr : ctx.getFieldValidationRules()) {
            String fvrKey = fvr.getField().toLowerCase();
            String fvrStripped = fvrKey.replaceAll("^(edt|luc|cds|dts|bbt|lbl|grp|grd|pnl|chk|rdb|cbx)", "");
            if (fvrKey.equals(key) || fvrStripped.equals(keyStripped)) {
                String validator = fvr.getAngularValidator();
                if (validator != null && !validator.startsWith("Custom")) {
                    validators.add(validator);
                }
            }
        }
        return validators.isEmpty() ? null : String.join(", ", validators);
    }

    /** Humaniza nome de componente Delphi para label Angular */
    private String humanizeComponentName(String name) {
        // edtCodigoFornecedorDisplay → Código Fornecedor
        String stripped = name.replaceAll("^(?i)(edt|luc|cds|dts|bbt|lbl|grp|grd|pnl|chk|rdb|cbx)", "");
        if (stripped.isEmpty()) stripped = name;
        stripped = stripped.replaceAll("Display$", "").replaceAll("Filtro$", "");
        // Split CamelCase
        String spaced = stripped.replaceAll("([A-Z])", " $1").trim();
        // Expande prefixos conhecidos
        Map<String, String> exp = getColumnNameExpansions();
        String[] words = spaced.split(" ");
        StringBuilder lb = new StringBuilder();
        for (String w : words) {
            String lower = w.toLowerCase();
            String expanded = exp.getOrDefault(lower, w);
            if (lb.length() > 0) lb.append(" ");
            lb.append(Character.toUpperCase(expanded.charAt(0))).append(expanded.substring(1));
        }
        return lb.toString();
    }

    /** Verifica se campo é required via fieldValidationRules */
    private boolean isFieldRequired(String fieldName) {
        if (ctx == null) return false;
        String key = fieldName.toLowerCase();
        String keyStripped = key.replaceAll("^(edt|luc|cds|dts|bbt|lbl|grp|grd|pnl|chk|rdb|cbx)", "");
        return ctx.getFieldValidationRules().stream().anyMatch(r -> {
            String rKey = r.getField().toLowerCase();
            String rStripped = rKey.replaceAll("^(edt|luc|cds|dts|bbt|lbl|grp|grd|pnl|chk|rdb|cbx)", "");
            return (rKey.equals(key) || rStripped.equals(keyStripped)) && "required".equals(r.getValidationType());
        });
    }

    private String mapColorToEmoji(String color) {
        return switch (color) {
            case "green" -> "\uD83D\uDFE2"; // 🟢
            case "blue" -> "\uD83D\uDD35";  // 🔵
            case "yellow" -> "\uD83D\uDFE1"; // 🟡
            case "orange" -> "\uD83D\uDFE0"; // 🟠
            case "red" -> "\uD83D\uDD34";    // 🔴
            default -> "●";
        };
    }

    /** Gera método getColorClass() se há calcCellColorRules */
    private String genColorClassMethod() {
        if (ctx == null || ctx.getCalcCellColorRules().isEmpty()) return "";
        CalcCellColorRule colorRule = ctx.getCalcCellColorRules().get(0); // primeiro grid

        StringBuilder sb = new StringBuilder();
        sb.append("  getColorClass(value: number): string {\n");
        sb.append("    switch (value) {\n");
        for (CalcCellColorRule.ColorMapping cm : colorRule.getColorMappings()) {
            sb.append("      case ").append(cm.getValue()).append(": return '").append(cm.getCssClass()).append("';");
            if (cm.getLabel() != null) sb.append(" // ").append(cm.getLabel());
            sb.append("\n");
        }
        sb.append("      default: return '';\n");
        sb.append("    }\n");
        sb.append("  }\n\n");
        return sb.toString();
    }
}
