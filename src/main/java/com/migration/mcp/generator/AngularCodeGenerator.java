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
        return "import { Injectable } from '@angular/core';\n" +
               "import { BehaviorSubject } from 'rxjs';\n" +
               "import { first } from 'rxjs/operators';\n" +
               "import { Http" + pascal + "Service } from 'app/modules/shared/services/http/http-" + kebab + ".service';\n" +
               "import { " + pascal + "Model } from '../models/" + kebab + ".model';\n" +
               "import { Pesquisa" + pascal + "Model } from '../models/pesquisa-" + kebab + ".model';\n" +
               "import { Result } from '@shared/models/result.model';\n" +
               "import { Title } from '@shared/services/custom-title.service';\n" +
               "import { LoaderService } from '@shared/services/loader.service';\n" +
               "import { SharedMessageService } from '@shared/services/shared-message.service';\n" +
               "import { ErroDispacherService } from '@shared/services/erro-dispacher.service';\n" +
               "import { UtilsService } from '@shared/services/utils.service';\n" +
               "import { TipoTitulo } from '@shared/models/tipo-titulo.enum';\n\n" +
               "@Injectable({ providedIn: 'root' })\n" +
               "export class " + pascal + "Service {\n\n" +
               "  private tituloSubject = new BehaviorSubject<string>('" + pascal + "');\n" +
               "  titulo$ = this.tituloSubject.asObservable();\n\n" +
               "  private gridSubject = new BehaviorSubject<Result<" + pascal + "Model>>(null);\n" +
               "  grid$ = this.gridSubject.asObservable();\n\n" +
               "  private selecionadoSubject = new BehaviorSubject<" + pascal + "Model>(null);\n" +
               "  selecionado$ = this.selecionadoSubject.asObservable();\n\n" +
               "  private alterarEditarSubject = new BehaviorSubject<boolean>(false);\n" +
               "  alterarEditar$ = this.alterarEditarSubject.asObservable();\n\n" +
               "  private filtros: Pesquisa" + pascal + "Model = {};\n\n" +
               "  constructor(\n" +
               "    private httpService: Http" + pascal + "Service,\n" +
               "    private title: Title,\n" +
               "    private loader: LoaderService,\n" +
               "    private message: SharedMessageService,\n" +
               "    private erroDispacher: ErroDispacherService,\n" +
               "    private utils: UtilsService\n" +
               "  ) { }\n\n" +
               "  handlePesquisar(filtros: Pesquisa" + pascal + "Model): void {\n" +
               "    this.filtros = filtros;\n" +
               "    this.loader.setLoading(true);\n" +
               "    this.httpService.pesquisar(filtros).pipe(first()).subscribe(\n" +
               "      (res) => { this.gridSubject.next(res); this.loader.setLoading(false); },\n" +
               "      (err) => { this.erroDispacher.dispatch(err); this.loader.setLoading(false); }\n" +
               "    );\n" +
               "  }\n\n" +
               "  loadLazy(event: any): void {\n" +
               "    this.filtros.lazyDto = this.utils.getLazyDto(event);\n" +
               "    this.handlePesquisar(this.filtros);\n" +
               "  }\n\n" +
               "  handleSalvar(model: " + pascal + "Model): void {\n" +
               "    this.loader.setLoading(true);\n" +
               "    this.httpService.save(model).pipe(first()).subscribe(\n" +
               "      () => {\n" +
               "        this.message.showSuccess('Registro salvo com sucesso.');\n" +
               "        this.setModoLista();\n" +
               "        this.handlePesquisar(this.filtros);\n" +
               "      },\n" +
               "      (err) => { this.erroDispacher.dispatch(err); this.loader.setLoading(false); }\n" +
               "    );\n" +
               "  }\n\n" +
               "  handleDeletar(id: number): void {\n" +
               "    this.loader.setLoading(true);\n" +
               "    this.httpService.delete(id).pipe(first()).subscribe(\n" +
               "      () => {\n" +
               "        this.message.showSuccess('Registro excluido com sucesso.');\n" +
               "        this.handlePesquisar(this.filtros);\n" +
               "      },\n" +
               "      (err) => { this.erroDispacher.dispatch(err); this.loader.setLoading(false); }\n" +
               "    );\n" +
               "  }\n\n" +
               "  setModoNovo(): void {\n" +
               "    this.selecionadoSubject.next(null);\n" +
               "    this.alterarEditarSubject.next(true);\n" +
               "    this.tituloSubject.next('" + pascal + " ' + TipoTitulo.NOVO);\n" +
               "  }\n\n" +
               "  setModoEditar(item: " + pascal + "Model): void {\n" +
               "    this.selecionadoSubject.next(item);\n" +
               "    this.alterarEditarSubject.next(true);\n" +
               "    this.tituloSubject.next('" + pascal + " ' + TipoTitulo.EDITAR);\n" +
               "  }\n\n" +
               "  setModoLista(): void {\n" +
               "    this.alterarEditarSubject.next(false);\n" +
               "    this.tituloSubject.next('" + pascal + "');\n" +
               "  }\n" +
               "}\n";
    }

    // ── HTTP Service ─────────────────────────────────────────────────────────

    private String genHttpService(String name, String kebab) {
        String pascal = toPascalCase(name);
        return "import { Injectable } from '@angular/core';\n" +
               "import { HttpClient } from '@angular/common/http';\n" +
               "import { Observable } from 'rxjs';\n" +
               "import { URL_API } from '@shared/services/startup.service';\n" +
               "import { " + pascal + "Model } from 'app/" + kebab + "/models/" + kebab + ".model';\n" +
               "import { Pesquisa" + pascal + "Model } from 'app/" + kebab + "/models/pesquisa-" + kebab + ".model';\n" +
               "import { Result } from '@shared/models/result.model';\n\n" +
               "@Injectable({ providedIn: 'root' })\n" +
               "export class Http" + pascal + "Service {\n\n" +
               "  constructor(private http: HttpClient) { }\n\n" +
               "  pesquisar(filtros: Pesquisa" + pascal + "Model): Observable<Result<" + pascal + "Model>> {\n" +
               "    return this.http.post<Result<" + pascal + "Model>>(`${URL_API}" + kebab + "/pesquisar`, filtros);\n" +
               "  }\n\n" +
               "  getById(id: number): Observable<" + pascal + "Model> {\n" +
               "    return this.http.get<" + pascal + "Model>(`${URL_API}" + kebab + "/getById/${id}`);\n" +
               "  }\n\n" +
               "  save(model: " + pascal + "Model): Observable<void> {\n" +
               "    return this.http.post<void>(`${URL_API}" + kebab + "/save`, model);\n" +
               "  }\n\n" +
               "  delete(id: number): Observable<void> {\n" +
               "    return this.http.delete<void>(`${URL_API}" + kebab + "/delete/${id}`);\n" +
               "  }\n\n" +
               "  exportar(filtros: Pesquisa" + pascal + "Model): Observable<any> {\n" +
               "    return this.http.post(`${URL_API}" + kebab + "/exportar`, filtros);\n" +
               "  }\n" +
               "}\n";
    }

    // ── Container ────────────────────────────────────────────────────────────

    private String genContainer(String name, String kebab) {
        String pascal = toPascalCase(name);
        return "import { Component, OnInit, OnDestroy, ChangeDetectionStrategy } from '@angular/core';\n" +
               "import { Observable, Subject } from 'rxjs';\n" +
               "import { takeUntil, filter } from 'rxjs/operators';\n" +
               "import { " + pascal + "Service } from '../../services/" + kebab + ".service';\n" +
               "import { LoaderService } from '@shared/services/loader.service';\n" +
               "import { ErroDispacherService } from '@shared/services/erro-dispacher.service';\n" +
               "import { ManipulaErrorService } from '@shared/services/manipula-error.service';\n" +
               "import { Title } from '@shared/services/custom-title.service';\n\n" +
               "@Component({\n" +
               "  selector: 'app-" + kebab + "-container',\n" +
               "  templateUrl: './" + kebab + "-container.component.html',\n" +
               "  changeDetection: ChangeDetectionStrategy.OnPush\n" +
               "})\n" +
               "export class " + pascal + "ContainerComponent implements OnInit, OnDestroy {\n\n" +
               "  titulo$: Observable<string>;\n" +
               "  loading$: Observable<boolean>;\n" +
               "  alterarEditar$: Observable<boolean>;\n\n" +
               "  private destroy$ = new Subject<void>();\n\n" +
               "  constructor(\n" +
               "    private service: " + pascal + "Service,\n" +
               "    private title: Title,\n" +
               "    private loader: LoaderService,\n" +
               "    private erroDispacher: ErroDispacherService,\n" +
               "    private manipulaError: ManipulaErrorService\n" +
               "  ) { }\n\n" +
               "  ngOnInit(): void {\n" +
               "    this.titulo$ = this.service.titulo$;\n" +
               "    this.loading$ = this.loader.loading$;\n" +
               "    this.alterarEditar$ = this.service.alterarEditar$;\n\n" +
               "    this.erroDispacher.erro$.pipe(\n" +
               "      takeUntil(this.destroy$),\n" +
               "      filter(err => !!err)\n" +
               "    ).subscribe(err => {\n" +
               "      this.loader.setLoading(false);\n" +
               "      this.manipulaError.handle(err);\n" +
               "    });\n" +
               "  }\n\n" +
               "  ngOnDestroy(): void {\n" +
               "    this.destroy$.next();\n" +
               "    this.destroy$.complete();\n" +
               "  }\n" +
               "}\n";
    }

    private String genContainerHtml(String name, String kebab, DfmForm form) {
        String title = form.getCaption() != null ? form.getCaption() : toPascalCase(name);
        return "<app-componente-basico [titulo]=\"titulo$ | async\" [loading]=\"loading$ | async\">\n" +
               "  <ng-container *ngIf=\"!(alterarEditar$ | async); else cadastro\">\n" +
               "    <app-" + kebab + "-filtros></app-" + kebab + "-filtros>\n" +
               "    <app-" + kebab + "-grid></app-" + kebab + "-grid>\n" +
               "  </ng-container>\n" +
               "  <ng-template #cadastro>\n" +
               "    <app-" + kebab + "-cadastro></app-" + kebab + "-cadastro>\n" +
               "  </ng-template>\n" +
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

        StringBuilder cols = new StringBuilder();
        for (DfmForm.GridColumn gc : gridCols) {
            String field = snakeToCamel(gc.getField());
            String header = gc.getHeader();
            if (gc.getSubHeader() != null && !gc.getSubHeader().isEmpty()) {
                header = gc.getSubHeader() + " " + header;
            }
            int widthPct = Math.max(5, Math.round((float) gc.getWidthChars() / totalChars * 95));
            cols.append("    { field: '").append(field).append("', header: '").append(header)
                .append("', width: '").append(widthPct).append("%' },\n");
        }

        return "import { Component, OnInit, OnDestroy } from '@angular/core';\n" +
               "import { Subject } from 'rxjs';\n" +
               "import { takeUntil } from 'rxjs/operators';\n" +
               "import { " + pascal + "Service } from '../../services/" + kebab + ".service';\n" +
               "import { " + pascal + "Model } from '../../models/" + kebab + ".model';\n" +
               "import { DataGridColunasModel } from '@shared/models/data-grid-colunas.model';\n" +
               "import { DataGridItem } from '@shared/models/data-grid-item.model';\n\n" +
               "@Component({\n" +
               "  selector: 'app-" + kebab + "-grid',\n" +
               "  templateUrl: './" + kebab + "-grid.component.html'\n" +
               "})\n" +
               "export class " + pascal + "GridComponent implements OnInit, OnDestroy {\n\n" +
               "  colunas: DataGridColunasModel[] = [\n" +
               cols +
               "  ];\n\n" +
               "  listaGrid: DataGridItem[] = [];\n" +
               "  totalRegistros = 0;\n\n" +
               "  private destroy$ = new Subject<void>();\n\n" +
               "  constructor(private service: " + pascal + "Service) { }\n\n" +
               "  ngOnInit(): void {\n" +
               "    this.service.grid$.pipe(takeUntil(this.destroy$)).subscribe(result => {\n" +
               "      if (result) {\n" +
               "        this.listaGrid = result.listVO.map(item => ({\n" +
               "          ...item,\n" +
               "          loadLazy: (event) => this.service.loadLazy(event),\n" +
               "          editar: () => this.service.setModoEditar(item),\n" +
               "          excluir: () => this.service.handleDeletar(item.id)\n" +
               "        }));\n" +
               "        this.totalRegistros = result.lazyDto?.totalRegistros || 0;\n" +
               "      }\n" +
               "    });\n" +
               "  }\n\n" +
               "  btnNovo(): void {\n" +
               "    this.service.setModoNovo();\n" +
               "  }\n\n" +
               genColorClassMethod() +
               "  ngOnDestroy(): void {\n" +
               "    this.destroy$.next();\n" +
               "    this.destroy$.complete();\n" +
               "  }\n" +
               "}\n";
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
            String defaultVal = defaultValues.getOrDefault(f.name, "null");
            String validator = getValidatorForField(f.name);
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
        String name;   // formControlName
        String label;  // label do campo
        String type;   // "input", "dropdown", "calendar", "checkbox"
        FiltroField(String name, String label, String type) {
            this.name = name; this.label = label; this.type = type;
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
            String label = findLabelForComponent(c, labelCaptions);

            if (type.contains("LookupCombo") || type.contains("LgCorporativo") || type.contains("DBLookupCombo")) {
                filtros.add(new FiltroField(name, label, "dropdown"));
            } else if (type.contains("DateEdit") || type.contains("Calendar")) {
                filtros.add(new FiltroField(name, label, "calendar"));
            } else if (type.equals("TEdit") || type.equals("TMaskEdit")) {
                filtros.add(new FiltroField(name, label, "input"));
            } else if (type.contains("CheckBox") && !type.contains("DB")) {
                String cbLabel = c.getProperties().getOrDefault("Caption", c.getName());
                filtros.add(new FiltroField(name, cbLabel, "checkbox"));
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

    /** Extrai valores default dos filtros via formInitialization */
    private Map<String, String> getDefaultValuesForFiltros() {
        Map<String, String> defaults = new LinkedHashMap<>();
        if (ctx == null) return defaults;

        for (FormInitialization fi : ctx.getFormInitialization()) {
            for (FormInitialization.DefaultValue dv : fi.getDefaultValues()) {
                String fieldName = componentToFormControl(dv.getComponent());
                String value = mapDefaultValue(dv.getValue(), dv.getProperty());
                defaults.put(fieldName, value);
            }
            for (FormInitialization.ComboPreselection cp : fi.getComboPreselections()) {
                String fieldName = componentToFormControl(cp.getComponent());
                if (cp.getSelectedKeys() != null && !cp.getSelectedKeys().isEmpty()) {
                    // Se são números simples, gera array
                    boolean allNumeric = cp.getSelectedKeys().stream().allMatch(k -> k.matches("\\d+"));
                    if (allNumeric) {
                        defaults.put(fieldName, "[" + String.join(", ", cp.getSelectedKeys()) + "]");
                    }
                }
            }
        }
        return defaults;
    }

    /** Mapeia valor Delphi para TypeScript */
    private String mapDefaultValue(String delphiValue, String property) {
        if (delphiValue == null) return "null";
        String lower = delphiValue.toLowerCase().trim();
        // Date - N (ex: Conexao.Date - 30 → subtrai N dias)
        java.util.regex.Matcher dateMinus = java.util.regex.Pattern.compile(
                "(?i)(?:conexao\\.date|vhoje)\\s*-\\s*(\\d+)").matcher(delphiValue);
        if (dateMinus.find()) {
            return "new Date(new Date().setDate(new Date().getDate() - " + dateMinus.group(1) + "))";
        }
        java.util.regex.Matcher datePlus = java.util.regex.Pattern.compile(
                "(?i)(?:conexao\\.date|vhoje)\\s*\\+\\s*(\\d+)").matcher(delphiValue);
        if (datePlus.find()) {
            return "new Date(new Date().setDate(new Date().getDate() + " + datePlus.group(1) + "))";
        }
        if (lower.contains("conexao.date") || lower.contains("conexao.now") || lower.equals("vhoje") ||
            (property != null && property.equalsIgnoreCase("Date") &&
             (lower.contains("date") || lower.contains("now") || lower.contains("hoje")))) {
            return "new Date()";
        }
        if (lower.equals("true") || lower.equals("false")) return lower;
        if (lower.matches("\\d+")) return delphiValue;
        if (lower.equals("''") || lower.equals("emptystr")) return "''";
        return "null /* " + delphiValue + " */";
    }

    /** Converte nome de componente Delphi para formControlName Angular */
    private String componentToFormControl(String component) {
        if (component == null) return "";
        // Remove prefixos Delphi: edt, luc, cds, chk, etc
        String name = component.replaceAll("^(?i)(edt|luc|cds|dts|bbt|lbl|grp|grd|pnl|chk|rdb|cbx)", "");
        if (name.isEmpty()) return component.toLowerCase();
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /** Busca Angular Validator para um campo via fieldValidationRules */
    private String getValidatorForField(String fieldName) {
        if (ctx == null) return null;
        List<String> validators = new ArrayList<>();
        for (FieldValidationRule fvr : ctx.getFieldValidationRules()) {
            String fvrField = componentToFormControl(fvr.getField());
            if (fvrField.equalsIgnoreCase(fieldName)) {
                if ("required".equals(fvr.getValidationType())) {
                    if (!validators.contains("Validators.required")) validators.add("Validators.required");
                } else if ("pattern".equals(fvr.getValidationType()) && "numeric_only".equals(fvr.getValue())) {
                    validators.add("Validators.pattern('^[0-9]*$')");
                } else if ("length".equals(fvr.getValidationType())) {
                    if ("minLength".equals(fvr.getOperator())) {
                        validators.add("Validators.minLength(" + fvr.getValue() + ")");
                    } else {
                        validators.add("Validators.maxLength(" + fvr.getValue() + ")");
                    }
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
        return ctx.getFieldValidationRules().stream()
                .anyMatch(r -> componentToFormControl(r.getField()).equalsIgnoreCase(fieldName)
                        && "required".equals(r.getValidationType()));
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
