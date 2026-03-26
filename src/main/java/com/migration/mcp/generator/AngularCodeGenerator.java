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

    /**
     * Gera todos os arquivos Angular para um modulo baseado em um form Delphi.
     * Retorna Map<nomeArquivo, conteudo>.
     */
    public Map<String, String> generateModule(DfmForm form, DelphiClass dc) {
        Map<String, String> files = new LinkedHashMap<>();
        String baseName = sanitizeName(form.getFormName());
        String kebab = toKebabCase(baseName);

        files.put(kebab + "/" + kebab + ".module.ts", genModule(baseName, kebab));
        files.put(kebab + "/" + kebab + ".routing.ts", genRouting(baseName, kebab));
        files.put(kebab + "/models/" + kebab + ".model.ts", genModel(baseName, dc, form));
        files.put(kebab + "/models/pesquisa-" + kebab + ".model.ts", genPesquisaModel(baseName, dc));
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
        StringBuilder sb = new StringBuilder();
        sb.append("export interface ").append(pascal).append("Model {\n");
        sb.append("  id: number;\n");
        for (DelphiField f : nonComponentFields(dc)) {
            sb.append("  ").append(toCamelCase(removePrefix(f.getName()))).append(": ").append(tsType(f)).append(";\n");
        }
        sb.append("  isEdicao?: boolean;\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String genPesquisaModel(String name, DelphiClass dc) {
        String pascal = toPascalCase(name);
        StringBuilder sb = new StringBuilder();
        sb.append("import { LazyLoadDto } from '@shared/models/lazy-load-dto.model';\n\n");
        sb.append("export interface Pesquisa").append(pascal).append("Model {\n");
        for (DelphiField f : nonComponentFields(dc)) {
            sb.append("  ").append(toCamelCase(removePrefix(f.getName()))).append("?: string;\n");
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
        List<DelphiField> fields = nonComponentFields(dc);
        int pct = fields.isEmpty() ? 100 : 100 / fields.size();

        StringBuilder cols = new StringBuilder();
        for (DelphiField f : fields) {
            String field = toCamelCase(removePrefix(f.getName()));
            cols.append("      { field: '").append(field).append("', header: '").append(removePrefix(f.getName())).append("', width: '").append(pct).append("%' },\n");
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
        List<DelphiField> fields = nonComponentFields(dc);

        StringBuilder formControls = new StringBuilder();
        for (DelphiField f : fields) {
            String field = toCamelCase(removePrefix(f.getName()));
            formControls.append("      ").append(field).append(": [''],\n");
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
        StringBuilder sb = new StringBuilder();
        sb.append("<app-filtro (onPesquisar)=\"handlePesquisar()\">\n");
        sb.append("  <div class=\"row\">\n");
        for (DelphiField f : nonComponentFields(dc)) {
            String field = toCamelCase(removePrefix(f.getName()));
            String label = removePrefix(f.getName());
            sb.append("    <div class=\"col-md-4\">\n");
            sb.append("      <label>").append(label).append("</label>\n");
            sb.append("      <input pInputText [formControl]=\"form.get('").append(field).append("')\" class=\"form-control\" />\n");
            sb.append("    </div>\n");
        }
        sb.append("  </div>\n");
        sb.append("</app-filtro>\n");
        return sb.toString();
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
        List<DelphiField> fields = nonComponentFields(dc);

        StringBuilder formControls = new StringBuilder();
        for (DelphiField f : fields) {
            String field = toCamelCase(removePrefix(f.getName()));
            formControls.append("      ").append(field).append(": [''],\n");
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
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"container-fluid\">\n");
        sb.append("  <form [formGroup]=\"form\">\n");
        sb.append("    <div class=\"row\">\n");

        for (DelphiField f : nonComponentFields(dc)) {
            String field = toCamelCase(removePrefix(f.getName()));
            String label = removePrefix(f.getName());
            String type = f.getDelphiType() != null ? f.getDelphiType().toLowerCase() : "";

            sb.append("      <div class=\"col-md-4 form-group\">\n");
            sb.append("        <label>").append(label).append("</label>\n");

            if (type.contains("boolean")) {
                sb.append("        <p-checkbox formControlName=\"").append(field).append("\" [binary]=\"true\" label=\"").append(label).append("\"></p-checkbox>\n");
            } else if (type.contains("currency") || type.contains("double") || type.contains("extended")) {
                sb.append("        <input pInputText formControlName=\"").append(field).append("\" class=\"form-control\" currencyMask />\n");
            } else {
                sb.append("        <input pInputText formControlName=\"").append(field).append("\" class=\"form-control\" />\n");
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
}
