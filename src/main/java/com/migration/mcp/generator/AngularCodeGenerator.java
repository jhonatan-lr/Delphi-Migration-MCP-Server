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

    /**
     * Gera todos os arquivos Angular para um modulo baseado em um form Delphi.
     * Retorna Map<nomeArquivo, conteudo>.
     */
    public Map<String, String> generateModule(DfmForm form, DelphiClass dc, String tableName) {
        this.currentTableName = tableName;
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

        StringBuilder cols = new StringBuilder();
        for (DfmForm.GridColumn gc : gridCols) {
            String field = snakeToCamel(gc.getField());
            String header = gc.getHeader();
            if (gc.getSubHeader() != null && !gc.getSubHeader().isEmpty()) {
                header = gc.getSubHeader() + " " + header;
            }
            cols.append("    { field: '").append(field).append("', header: '").append(header).append("' },\n");
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
               "  ngOnDestroy(): void {\n" +
               "    this.destroy$.next();\n" +
               "    this.destroy$.complete();\n" +
               "  }\n" +
               "}\n";
    }

    private String genGridHtml(String kebab) {
        return "<div class=\"row\">\n" +
               "  <div class=\"col-12\">\n" +
               "    <app-button label=\"Novo\" icon=\"fa fa-plus\" (onClick)=\"btnNovo()\"></app-button>\n" +
               "    <app-botoes-exportar (onExportar)=\"exportarDados()\"></app-botoes-exportar>\n" +
               "  </div>\n" +
               "</div>\n\n" +
               "<app-data-grid\n" +
               "  [colunas]=\"colunas\"\n" +
               "  [listaGrid]=\"listaGrid\"\n" +
               "  [totalRegistros]=\"totalRegistros\"\n" +
               "  (onLazy)=\"service.loadLazy($event)\"\n" +
               "  (onEditar)=\"service.setModoEditar($event)\"\n" +
               "  (onExcluir)=\"service.handleDeletar($event.id)\">\n" +
               "</app-data-grid>\n";
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

        StringBuilder formControls = new StringBuilder();
        for (FiltroField f : filtros) {
            formControls.append("      ").append(f.name).append(": [null],\n");
        }

        return "import { Component, OnInit } from '@angular/core';\n" +
               "import { FormBuilder, FormGroup } from '@angular/forms';\n" +
               "import { " + pascal + "Service } from '../../services/" + kebab + ".service';\n" +
               "import { Pesquisa" + pascal + "Model } from '../../models/pesquisa-" + kebab + ".model';\n\n" +
               "@Component({\n" +
               "  selector: 'app-" + kebab + "-filtros',\n" +
               "  templateUrl: './" + kebab + "-filtros.component.html'\n" +
               "})\n" +
               "export class " + pascal + "FiltrosComponent implements OnInit {\n\n" +
               "  form: FormGroup;\n\n" +
               "  constructor(\n" +
               "    private fb: FormBuilder,\n" +
               "    private service: " + pascal + "Service\n" +
               "  ) {\n" +
               "    this.form = this.fb.group({\n" +
               formControls +
               "    });\n" +
               "  }\n\n" +
               "  ngOnInit(): void {\n" +
               "    this.handlePesquisar();\n" +
               "  }\n\n" +
               "  handlePesquisar(): void {\n" +
               "    const filtros: Pesquisa" + pascal + "Model = this.form.getRawValue();\n" +
               "    this.service.handlePesquisar(filtros);\n" +
               "  }\n" +
               "}\n";
    }

    private String genFiltrosHtml(String kebab, DelphiClass dc, DfmForm form) {
        List<FiltroField> filtros = extractFiltroFields(form);
        StringBuilder sb = new StringBuilder();
        sb.append("<app-filtro (onPesquisar)=\"handlePesquisar()\">\n");
        sb.append("  <div class=\"row\">\n");
        for (FiltroField f : filtros) {
            String colSize = f.type.equals("calendar") ? "col-md-2" : f.type.equals("dropdown") ? "col-md-3" : "col-md-3";
            sb.append("    <div class=\"").append(colSize).append("\">\n");
            sb.append("      <label>").append(f.label).append("</label>\n");
            switch (f.type) {
                case "dropdown":
                    sb.append("      <p-dropdown [options]=\"").append(f.name).append("Options\" formControlName=\"")
                      .append(f.name).append("\" [filter]=\"true\" [showClear]=\"true\" placeholder=\"Selecione...\"></p-dropdown>\n");
                    break;
                case "calendar":
                    sb.append("      <p-calendar formControlName=\"").append(f.name).append("\" dateFormat=\"dd/mm/yy\" [showIcon]=\"true\"></p-calendar>\n");
                    break;
                case "checkbox":
                    sb.append("      <p-checkbox formControlName=\"").append(f.name).append("\" label=\"").append(f.label).append("\"></p-checkbox>\n");
                    break;
                default:
                    sb.append("      <input pInputText formControlName=\"").append(f.name).append("\" class=\"form-control\" />\n");
                    break;
            }
            sb.append("    </div>\n");
        }
        sb.append("  </div>\n");
        sb.append("</app-filtro>\n");
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
        // Fallback: usa Caption ou nome
        return comp.getProperties().getOrDefault("Caption",
               comp.getProperties().getOrDefault("Hint", comp.getName()));
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
            formControls.append("      ").append(f.camelName).append(": [null],\n");
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
        sb.append("<div class=\"container-fluid\">\n");
        sb.append("  <form [formGroup]=\"form\">\n");
        sb.append("    <div class=\"row\">\n");

        for (ResolvedField f : fields) {
            sb.append("      <div class=\"col-md-4 form-group\">\n");
            sb.append("        <label>").append(f.label).append("</label>\n");

            if ("boolean".equals(f.tsType)) {
                sb.append("        <p-checkbox formControlName=\"").append(f.camelName).append("\" [binary]=\"true\" label=\"").append(f.label).append("\"></p-checkbox>\n");
            } else if ("BigDecimal".equals(f.javaType)) {
                sb.append("        <input pInputText formControlName=\"").append(f.camelName).append("\" class=\"form-control\" currencyMask />\n");
            } else if ("Date".equals(f.javaType)) {
                sb.append("        <p-calendar formControlName=\"").append(f.camelName).append("\" dateFormat=\"dd/mm/yy\" [showIcon]=\"true\"></p-calendar>\n");
            } else {
                sb.append("        <input pInputText formControlName=\"").append(f.camelName).append("\" class=\"form-control\" />\n");
            }

            sb.append("      </div>\n");
        }

        sb.append("    </div>\n\n");
        sb.append("    <div class=\"row mt-3\">\n");
        sb.append("      <div class=\"col-12\">\n");
        sb.append("        <app-button label=\"Salvar\" icon=\"fa fa-check\" (onClick)=\"salvar()\"></app-button>\n");
        sb.append("        <app-button label=\"Voltar\" icon=\"fa fa-arrow-left\" styleClass=\"ui-button-secondary\" (onClick)=\"voltar()\"></app-button>\n");
        sb.append("      </div>\n");
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

    private String snakeToCamel(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (char c : s.toCharArray()) {
            if (c == '_') { nextUpper = true; }
            else { sb.append(nextUpper ? Character.toUpperCase(c) : c); nextUpper = false; }
        }
        if (sb.length() > 0) sb.setCharAt(0, Character.toLowerCase(sb.charAt(0)));
        return sb.toString();
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
}
