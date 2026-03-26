package com.migration.mcp.parser;

import com.migration.mcp.model.DfmComponent;
import com.migration.mcp.model.DfmForm;
import com.migration.mcp.model.ProjectProfile;
import com.migration.mcp.model.ProjectProfileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.*;

/**
 * Parser para arquivos .DFM (Delphi Form)
 * Converte definições de formulário em estrutura de componentes Angular.
 */
public class DfmFormParser {

    private static final Logger log = LoggerFactory.getLogger(DfmFormParser.class);

    // Mapeamento Delphi → Angular Material / HTML
    private static final Map<String, String> COMPONENT_MAP = new LinkedHashMap<>();

    static {
        // ── Componentes padrão VCL → PrimeNG (padrão Logus Web Angular 10) ──

        // Botões
        COMPONENT_MAP.put("TButton",        "<button pButton> (PrimeNG)");
        COMPONENT_MAP.put("TBitBtn",        "<button pButton> com ícone (PrimeNG)");
        COMPONENT_MAP.put("TSpeedButton",   "<button pButton icon='pi pi-...' class='p-button-text'>");

        // Inputs
        COMPONENT_MAP.put("TEdit",          "<input pInputText> (PrimeNG)");
        COMPONENT_MAP.put("TMaskEdit",      "<p-inputMask> (PrimeNG)");
        COMPONENT_MAP.put("TLabeledEdit",   "<span class='p-float-label'><input pInputText></span>");
        COMPONENT_MAP.put("TMemo",          "<textarea pInputTextarea> (PrimeNG)");
        COMPONENT_MAP.put("TRichEdit",      "<p-editor> (PrimeNG)");

        // Labels
        COMPONENT_MAP.put("TLabel",         "<label>");
        COMPONENT_MAP.put("TStaticText",    "<span>");

        // Containers / Layout
        COMPONENT_MAP.put("TPanel",         "<p-panel> ou <div class='p-grid'>");
        COMPONENT_MAP.put("TGroupBox",      "<p-fieldset legend='...'> (PrimeNG)");
        COMPONENT_MAP.put("TTabControl",    "<p-tabView> (PrimeNG)");
        COMPONENT_MAP.put("TPageControl",   "<p-tabView> (PrimeNG)");
        COMPONENT_MAP.put("TTabSheet",      "<p-tabPanel> (PrimeNG)");
        COMPONENT_MAP.put("TScrollBox",     "<div style='overflow:auto'>");

        // Seleção
        COMPONENT_MAP.put("TCheckBox",      "<p-checkbox> (PrimeNG)");
        COMPONENT_MAP.put("TRadioButton",   "<p-radioButton> (PrimeNG)");
        COMPONENT_MAP.put("TRadioGroup",    "<div *ngFor> + <p-radioButton>");
        COMPONENT_MAP.put("TComboBox",      "<p-dropdown> (PrimeNG)");
        COMPONENT_MAP.put("TListBox",       "<p-listbox> (PrimeNG)");

        // Grids / Tabelas
        COMPONENT_MAP.put("TDBGrid",        "<p-table> com [paginator]='true' (PrimeNG DataGrid)");
        COMPONENT_MAP.put("TStringGrid",    "<p-table> (PrimeNG)");
        COMPONENT_MAP.put("TListView",      "<p-table> ou <p-dataView>");
        COMPONENT_MAP.put("TTreeView",      "<p-tree> (PrimeNG)");

        // Mídia / Visual
        COMPONENT_MAP.put("TImage",         "<img> ou <p-image>");
        COMPONENT_MAP.put("TDateTimePicker","<p-calendar> (PrimeNG)");
        COMPONENT_MAP.put("TCalendar",      "<p-calendar [inline]='true'> (PrimeNG)");
        COMPONENT_MAP.put("TProgressBar",   "<p-progressBar> (PrimeNG)");
        COMPONENT_MAP.put("TTrackBar",      "<p-slider> (PrimeNG)");
        COMPONENT_MAP.put("TScrollBar",     "CSS overflow + scrolling");

        // Menu / Toolbar
        COMPONENT_MAP.put("TMainMenu",      "<p-menubar> (PrimeNG)");
        COMPONENT_MAP.put("TPopupMenu",     "<p-contextMenu> (PrimeNG)");
        COMPONENT_MAP.put("TToolBar",       "<p-toolbar> (PrimeNG)");
        COMPONENT_MAP.put("TStatusBar",     "<p-toolbar> (rodapé)");

        // DB-aware → FormControl + PrimeNG
        COMPONENT_MAP.put("TDBEdit",        "<input pInputText formControlName='...'>");
        COMPONENT_MAP.put("TDBComboBox",    "<p-dropdown formControlName='...'>");
        COMPONENT_MAP.put("TDBCheckBox",    "<p-checkbox formControlName='...'>");
        COMPONENT_MAP.put("TDBMemo",        "<textarea pInputTextarea formControlName='...'>");
        COMPONENT_MAP.put("TDBLookupComboBox", "<p-dropdown [filter]='true' formControlName='...'>");
        COMPONENT_MAP.put("TDBNavigator",   "<p-toolbar> com botões CRUD (Logus shared DataGrid)");

        // QuickReport → JasperReports
        COMPONENT_MAP.put("TQuickRep",      "JasperReports / PDF generation");
        COMPONENT_MAP.put("TQRBand",        "Report section (JasperReports band)");
        COMPONENT_MAP.put("TQRLabel",       "Report label (JasperReports static text)");
        COMPONENT_MAP.put("TQRDBText",      "Report field (JasperReports text field)");
        COMPONENT_MAP.put("TQRMemo",        "Report memo (JasperReports text element)");
        COMPONENT_MAP.put("TQRSysData",     "Report system data (page number, date)");
        COMPONENT_MAP.put("TQRShape",       "Report shape (line/rectangle)");
        COMPONENT_MAP.put("TQRImage",       "Report image (logo)");
        COMPONENT_MAP.put("TQRExpr",        "Report expression (calculated field)");
        COMPONENT_MAP.put("TQRGroup",       "Report group (JasperReports group)");
        COMPONENT_MAP.put("TQRSubDetail",   "Report sub-detail (JasperReports subreport)");

        // InfoPower → PrimeNG DataGrid
        COMPONENT_MAP.put("TwwDBGrid",      "<p-table> com [paginator]='true' [sortMode]='multiple' (Logus shared DataGrid)");
        COMPONENT_MAP.put("TwwDBComboBox",  "<p-dropdown [filter]='true'>");
        COMPONENT_MAP.put("TwwDBEdit",      "<input pInputText formControlName='...'>");
        COMPONENT_MAP.put("TwwDBLookupCombo","<p-dropdown [filter]='true' com BehaviorSubject>");

        // JEDI/JVCL → PrimeNG
        COMPONENT_MAP.put("TJvValidateEdit","<input pInputText> + Validators (ReactiveFormsModule)");
        COMPONENT_MAP.put("JvValidateEdit", "<input pInputText> + Validators (ReactiveFormsModule)");
        COMPONENT_MAP.put("TJvDateEdit",    "<p-calendar dateFormat='dd/mm/yy'> (PrimeNG)");
        COMPONENT_MAP.put("JvDateEdit",     "<p-calendar dateFormat='dd/mm/yy'> (PrimeNG)");
        COMPONENT_MAP.put("TJvToolEdit",    "<p-inputGroup> com <button pButton> suffix");
        COMPONENT_MAP.put("JvToolEdit",     "<p-inputGroup> com <button pButton> suffix");
        COMPONENT_MAP.put("JvDBLookup",     "<p-dropdown [filter]='true'>");
        COMPONENT_MAP.put("TJvDBLookupCombo","<p-dropdown [filter]='true' formControlName='...'>");
        COMPONENT_MAP.put("JvDBLookupCombo", "<p-dropdown [filter]='true' formControlName='...'>");

        // Logus custom → PrimeNG (padrão Logus Web)
        COMPONENT_MAP.put("TLgBitBtn",      "<button pButton> com ícone (padrão Logus)");
        COMPONENT_MAP.put("LgBitBtn",       "<button pButton> com ícone (padrão Logus)");
        COMPONENT_MAP.put("TPngBitBtn",     "<button pButton> com ícone PNG");
        COMPONENT_MAP.put("PngBitBtn",      "<button pButton> com ícone PNG");
        COMPONENT_MAP.put("TLgCorporativoLookupComboEdit", "<p-dropdown [filter]='true' [showClear]='true'> com BehaviorSubject (Logus LookupCombo)");
        COMPONENT_MAP.put("LgCorporativoLookupComboEdit",  "<p-dropdown [filter]='true' [showClear]='true'> com BehaviorSubject (Logus LookupCombo)");

        // Data components → Backend (Spring Boot / JPA) + Frontend (Angular Service)
        COMPONENT_MAP.put("TQuery",         "Backend: Spring @Repository + JPA | Frontend: HttpClient + Observable");
        COMPONENT_MAP.put("TSQLQuery",      "Backend: Spring @Repository + JPA | Frontend: HttpClient + Observable");
        COMPONENT_MAP.put("TTable",         "Backend: Spring @Repository + JPA Entity | Frontend: HttpClient + Observable");
        COMPONENT_MAP.put("TDatabase",      "Backend: Spring DataSource + connection pool");
        COMPONENT_MAP.put("TSQLConnection", "Backend: Spring DataSource + connection pool");
        COMPONENT_MAP.put("TClientDataSet", "Frontend: BehaviorSubject<T[]> no Angular Service");
        COMPONENT_MAP.put("TDataSetProvider","Backend: @RestController (data provider)");
        COMPONENT_MAP.put("TDataSource",    "Frontend: BehaviorSubject<T[]> no Angular Service (binding)");
        COMPONENT_MAP.put("TIBQuery",       "Backend: Spring @Repository + JPA | Frontend: HttpClient + Observable");
        COMPONENT_MAP.put("TFDQuery",       "Backend: Spring @Repository + JPA | Frontend: HttpClient + Observable");
        COMPONENT_MAP.put("TTimer",         "Frontend: RxJS interval() / timer() | Backend: @Scheduled");
    }

    public DfmForm parse(String content) {
        DfmForm form = new DfmForm();

        // Extrai nome e tipo do form
        Pattern headerPattern = Pattern.compile("(?i)object\\s+(\\w+)\\s*:\\s*(\\w+)");
        Matcher m = headerPattern.matcher(content);
        if (m.find()) {
            form.setFormName(m.group(1));
            form.setFormType(m.group(2));
        }

        // Caption — decodifica caracteres Delphi
        Pattern captionPat = Pattern.compile("(?i)Caption\\s*=\\s*([^\n]+)");
        Matcher cm = captionPat.matcher(content);
        if (cm.find()) form.setCaption(decodeDfmString(cm.group(1).trim().replaceAll("^'|'$", "")));

        // Width / Height
        Pattern widthPat = Pattern.compile("(?i)\\bWidth\\s*=\\s*(\\d+)");
        Matcher wm = widthPat.matcher(content);
        if (wm.find()) form.setWidth(Integer.parseInt(wm.group(1)));

        Pattern heightPat = Pattern.compile("(?i)\\bHeight\\s*=\\s*(\\d+)");
        Matcher hm = heightPat.matcher(content);
        if (hm.find()) form.setHeight(Integer.parseInt(hm.group(1)));

        // Componentes
        form.setComponents(extractComponents(content));

        // Extrai colunas do grid (TwwDBGrid Selected.Strings)
        form.setGridColumns(extractGridColumns(content));

        // Extrai campos dos datasets (TClientDataSet, TSQLQuery, etc.)
        form.setDatasetFields(extractDatasetFields(content));

        // Angular metadata
        form.setAngularComponentName(toAngularComponentName(form.getFormName()));
        form.setSuggestedRoute("/" + toKebabCase(sanitizeFormName(form.getFormName())));

        // Gera template HTML Angular
        form.setAngularTemplate(generateAngularTemplate(form));
        form.setAngularComponentTs(generateAngularComponent(form));

        log.debug("Parsed DFM form '{}': {} components", form.getFormName(), form.getComponents().size());
        return form;
    }

    private List<DfmComponent> extractComponents(String content) {
        List<DfmComponent> components = new ArrayList<>();
        // Match: object <Name>: <Type> ... end
        Pattern objPattern = Pattern.compile(
                "(?i)\\bobject\\s+(\\w+)\\s*:\\s*(\\w+)[\\s\\S]*?(?=\\bobject\\b|\\bend\\b)",
                Pattern.DOTALL);
        Matcher m = objPattern.matcher(content);

        while (m.find()) {
            String compName = m.group(1);
            String compType = m.group(2);
            String compBlock = m.group(0);

            DfmComponent comp = new DfmComponent();
            comp.setName(compName);
            comp.setDelphiType(compType);
            comp.setAngularEquivalent(COMPONENT_MAP.getOrDefault(compType, "<div> (sem mapeamento direto)"));

            // Extrai propriedades relevantes
            Map<String, String> props = extractProperties(compBlock);
            comp.setProperties(props);

            // Detecta eventos
            comp.setEvents(extractEvents(compBlock));

            components.add(comp);
        }
        return components;
    }

    private Map<String, String> extractProperties(String block) {
        Map<String, String> props = new LinkedHashMap<>();
        String[] relevantProps = {"Caption", "Text", "Hint", "Enabled", "Visible",
                "ReadOnly", "Required", "MaxLength", "TabOrder", "Color", "Font"};

        for (String prop : relevantProps) {
            Pattern p = Pattern.compile("(?i)\\b" + prop + "\\s*=\\s*([^\n]+)");
            Matcher m = p.matcher(block);
            if (m.find()) {
                String val = m.group(1).trim().replaceAll("^'|'$", "");
                // Item 6: decodifica captions Delphi (#231#227 → ção)
                if (val.contains("#")) val = decodeDfmString(val);
                props.put(prop, val);
            }
        }
        return props;
    }

    private List<String> extractEvents(String block) {
        List<String> events = new ArrayList<>();
        // Item 7: pattern mais restritivo — On seguido de letra maiúscula + pelo menos 3 chars
        Pattern ep = Pattern.compile("\\b(On[A-Z]\\w{2,})\\s*=\\s*(\\w+)");
        Matcher m = ep.matcher(block);
        while (m.find()) {
            String evtName = m.group(1);
            String handler = m.group(2);
            // Filtra falsos positivos: propriedades que começam com "On" mas não são eventos
            if (evtName.matches("(?i)OnWidth|OnHeight|OnTop|OnLeft")) continue;
            events.add(evtName + " → " + handler);
        }
        return events;
    }

    // ── Grid Columns & Dataset Fields Extraction ──────────────────────────────

    /**
     * Extrai colunas do TwwDBGrid a partir do Selected.Strings do DFM.
     * Formato: 'field_name\twidth\tHeader\tF\tSubHeader'
     */
    private List<DfmForm.GridColumn> extractGridColumns(String content) {
        List<DfmForm.GridColumn> columns = new ArrayList<>();
        // Encontra bloco Selected.Strings = (...)
        Pattern selectedPat = Pattern.compile(
                "(?i)Selected\\.Strings\\s*=\\s*\\(([\\s\\S]*?)\\)", Pattern.DOTALL);
        Matcher m = selectedPat.matcher(content);
        while (m.find()) {
            String block = m.group(1);
            // No DFM, cada linha usa #9 como tab: 'field'#9'width'#9'Header'#9'F'#9'SubHeader'
            // Primeiro normaliza: junta fragmentos 'xxx'#9'yyy' em uma string com tab
            String[] lines = block.split("\n");
            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty()) continue;
                // Remove aspas e substitui #9 por tab, remove + de concatenação
                String normalized = line.replaceAll("'\\s*#9\\s*'", "\t")
                                        .replaceAll("^'|'$", "")
                                        .replaceAll("\\+\\s*$", "")
                                        .trim();
                // Também tenta tab literal
                if (!normalized.contains("\t")) {
                    normalized = line.replaceAll("'", "").trim();
                    // Tenta separar por 2+ espaços
                    if (!normalized.contains("\t")) continue;
                }
                String[] parts = normalized.split("\t");
                if (parts.length >= 3) {
                    String field = parts[0].trim();
                    int width = 10;
                    try { width = Integer.parseInt(parts[1].trim()); } catch (Exception ignored) {}
                    // Item 5: limpar tabs e espaços extras dos headers
                    String header = decodeDfmString(parts[2].trim()).replaceAll("[\\t\\r\\n]+", " ").trim();
                    String subHeader = parts.length >= 5
                            ? decodeDfmString(parts[4].trim()).replaceAll("[\\t\\r\\n]+", " ").trim() : "";
                    // Pula campos de 1 char de largura sem header (ícones visuais)
                    if (width <= 1 && header.trim().isEmpty()) continue;
                    columns.add(new DfmForm.GridColumn(field, header, subHeader, width));
                }
            }
        }
        return columns;
    }

    /**
     * Extrai campos dos TClientDataSet / TSQLQuery definidos como sub-objects no DFM.
     * Formato: object cdsNomeField: TIntegerField ... end
     * Detecta campos visíveis/invisíveis.
     */
    private List<DfmForm.DatasetField> extractDatasetFields(String content) {
        List<DfmForm.DatasetField> fields = new ArrayList<>();
        Set<String> seen = new java.util.LinkedHashSet<>();

        // Encontra objetos que são Field types: TIntegerField, TStringField, TFloatField, etc.
        Pattern fieldPat = Pattern.compile(
                "(?i)object\\s+(\\w+)\\s*:\\s*(T(?:Integer|String|Float|Date|DateTime|Time|Boolean|Currency|BCD|Smallint|Largeint|Word|SQLTimeStamp|Memo|Blob|Wide|FMTBcd)Field)([\\s\\S]*?)(?=\\bobject\\b|\\bend\\b)",
                Pattern.DOTALL);
        Matcher m = fieldPat.matcher(content);
        while (m.find()) {
            String fullName = m.group(1); // ex: cdsPedidocdg_ped_auto
            String delphiType = m.group(2); // ex: TIntegerField
            String block = m.group(3);

            // Extrai nome do campo real (remove prefixo do dataset)
            // cdsPedidocdg_ped_auto -> cdg_ped_auto (remove "cdsPedido")
            // qryProdutoscdg_produto -> cdg_produto (remove "qryProdutos")
            String fieldName = extractFieldName(fullName);
            if (fieldName.isEmpty() || seen.contains(fieldName)) continue;
            seen.add(fieldName);

            DfmForm.DatasetField df = new DfmForm.DatasetField(fieldName, delphiType);

            // Verifica Visible = False
            if (block.matches("(?si).*Visible\\s*=\\s*False.*")) {
                df.setVisible(false);
            }

            fields.add(df);
        }
        return fields;
    }

    /**
     * Extrai o nome real do campo removendo o prefixo do dataset.
     * cdsPedidocdg_ped_auto -> cdg_ped_auto
     * qryProdutosdcr_produto -> dcr_produto
     */
    private String extractFieldName(String fullName) {
        // Tenta achar o ponto onde começa o campo real (geralmente com prefixo cdg_, dcr_, nmr_, dat_, flg_, flb_, qtd_, val_)
        String[] knownPrefixes = {"cdg_", "dcr_", "nmr_", "dat_", "flg_", "flb_", "qtd_", "val_", "id"};
        for (String prefix : knownPrefixes) {
            int idx = fullName.toLowerCase().indexOf(prefix);
            if (idx > 0) {
                return fullName.substring(idx);
            }
        }
        // Fallback: procura último segmento antes de underscore
        int lastUpper = -1;
        for (int i = fullName.length() - 1; i > 0; i--) {
            if (Character.isUpperCase(fullName.charAt(i))) {
                lastUpper = i;
                break;
            }
        }
        return lastUpper > 0 ? fullName.substring(lastUpper) : fullName;
    }

    // ── Angular Code Generation ───────────────────────────────────────────────

    private String generateAngularTemplate(DfmForm form) {
        StringBuilder html = new StringBuilder();
        String componentName = toKebabCase(sanitizeFormName(form.getFormName()));
        String title = form.getCaption() != null ? form.getCaption() : form.getFormName();

        html.append("<div class=\"").append(componentName).append("-container\">\n");
        html.append("  <p-panel header=\"").append(title).append("\">\n");
        html.append("    <form [formGroup]=\"form\">\n\n");
        html.append("      <!-- Filtros -->\n");
        html.append("      <div class=\"p-grid p-fluid\">\n");

        boolean hasGrid = false;
        List<DfmComponent> buttons = new ArrayList<>();

        for (DfmComponent comp : form.getComponents()) {
            String type = comp.getDelphiType();
            if (type.contains("Grid") || type.contains("ListView") || type.contains("wwDB")) {
                hasGrid = true;
                continue;
            }
            if (type.contains("BitBtn") || type.contains("Button") || type.contains("LgBitBtn") || type.contains("PngBitBtn")) {
                buttons.add(comp);
                continue;
            }
            // Componentes não visuais, DataSource, ClientDataSet — omitidos
            if (type.contains("DataSource") || type.contains("ClientDataSet") || type.contains("DataSetProvider") ||
                type.contains("Query") || type.contains("Table") || type.contains("Connection")) {
                continue;
            }
            String template = generateComponentTemplate(comp);
            if (!template.isEmpty()) {
                html.append("        ").append(template).append("\n");
            }
        }

        html.append("      </div>\n\n");

        // Botão pesquisar separado
        html.append("      <!-- Ações de pesquisa -->\n");
        html.append("      <div class=\"p-grid p-mt-2\">\n");
        for (DfmComponent btn : buttons) {
            String caption = btn.getProperties().getOrDefault("Caption", btn.getName());
            String clickMethod = extractClickMethod(btn);
            if (caption.toLowerCase().contains("pesquis") || caption.toLowerCase().contains("buscar")) {
                html.append("        <div class=\"p-col-fixed\">\n");
                html.append("          <button pButton type=\"button\" label=\"").append(caption)
                    .append("\" icon=\"pi pi-search\"").append(clickMethod).append("></button>\n");
                html.append("        </div>\n");
            }
        }
        html.append("      </div>\n\n");

        if (hasGrid) {
            html.append("      <!-- Grid de dados (Logus shared DataGrid) -->\n");
            html.append("      <p-table [value]=\"items\" [paginator]=\"true\" [rows]=\"25\"\n");
            html.append("               [rowsPerPageOptions]=\"[10, 25, 50]\" [sortMode]=\"'multiple'\"\n");
            html.append("               [responsive]=\"true\" (onRowDblClick)=\"onRowDblClick($event)\">\n");
            html.append("        <ng-template pTemplate=\"header\">\n");
            html.append("          <tr>\n");
            html.append("            <!-- TODO: Definir colunas baseado no grid Delphi -->\n");
            html.append("            <th pSortableColumn=\"id\">ID <p-sortIcon field=\"id\"></p-sortIcon></th>\n");
            html.append("          </tr>\n");
            html.append("        </ng-template>\n");
            html.append("        <ng-template pTemplate=\"body\" let-row>\n");
            html.append("          <tr>\n");
            html.append("            <td>{{ row.id }}</td>\n");
            html.append("          </tr>\n");
            html.append("        </ng-template>\n");
            html.append("      </p-table>\n\n");
        }

        // Barra de botões de ação
        html.append("      <!-- Barra de ações -->\n");
        html.append("      <p-toolbar>\n");
        html.append("        <div class=\"p-toolbar-group-left\">\n");
        for (DfmComponent btn : buttons) {
            String caption = btn.getProperties().getOrDefault("Caption", btn.getName());
            if (caption.toLowerCase().contains("pesquis") || caption.toLowerCase().contains("buscar")) continue;
            String clickMethod = extractClickMethod(btn);
            String icon = guessButtonIcon(caption);
            String styleClass = guessButtonStyleClass(caption);
            html.append("          <button pButton type=\"button\" label=\"").append(caption).append("\"");
            if (!icon.isEmpty()) html.append(" icon=\"").append(icon).append("\"");
            if (!styleClass.isEmpty()) html.append(" class=\"").append(styleClass).append("\"");
            html.append(clickMethod).append("></button>\n");
        }
        html.append("        </div>\n");
        html.append("      </p-toolbar>\n\n");

        html.append("    </form>\n");
        html.append("  </p-panel>\n");
        html.append("</div>\n");

        return html.toString();
    }

    private String extractClickMethod(DfmComponent comp) {
        for (String evt : comp.getEvents()) {
            if (evt.contains("Click")) {
                String method = evt.split("→")[1].trim();
                // Converte nome Delphi para Angular: bbtCancelarClick → cancelar()
                String angularMethod = method.replaceAll("(?i)^bbt|Click$", "");
                angularMethod = Character.toLowerCase(angularMethod.charAt(0)) + angularMethod.substring(1);
                return " (click)=\"" + angularMethod + "()\"";
            }
        }
        return "";
    }

    private String guessButtonIcon(String caption) {
        String lower = caption.toLowerCase();
        if (lower.contains("novo") || lower.contains("incluir") || lower.contains("adicionar")) return "pi pi-plus";
        if (lower.contains("cancelar")) return "pi pi-times";
        if (lower.contains("reativar") || lower.contains("ativar")) return "pi pi-refresh";
        if (lower.contains("imprimir") || lower.contains("relat")) return "pi pi-print";
        if (lower.contains("log") || lower.contains("histórico") || lower.contains("manutencao")) return "pi pi-list";
        if (lower.contains("sair") || lower.contains("fechar")) return "pi pi-sign-out";
        if (lower.contains("salvar") || lower.contains("gravar")) return "pi pi-save";
        if (lower.contains("excluir") || lower.contains("deletar")) return "pi pi-trash";
        if (lower.contains("editar") || lower.contains("alterar")) return "pi pi-pencil";
        return "";
    }

    private String guessButtonStyleClass(String caption) {
        String lower = caption.toLowerCase();
        if (lower.contains("cancelar") || lower.contains("excluir") || lower.contains("deletar")) return "p-button-danger p-mr-2";
        if (lower.contains("sair") || lower.contains("fechar")) return "p-button-secondary p-mr-2";
        if (lower.contains("log") || lower.contains("manutencao")) return "p-button-info p-mr-2";
        return "p-button-success p-mr-2";
    }

    private String generateComponentTemplate(DfmComponent comp) {
        String type = comp.getDelphiType();
        String name = toCamelCase(comp.getName());
        String label = comp.getProperties().getOrDefault("Caption",
                       comp.getProperties().getOrDefault("Text", comp.getName()));
        boolean readOnly = "True".equalsIgnoreCase(comp.getProperties().getOrDefault("ReadOnly", "False"));

        // Logus LookupComboEdit → p-dropdown
        if (type.contains("LookupCombo") || type.contains("LgCorporativo")) {
            return "<div class=\"p-col-12 p-md-3\">\n" +
                   "          <label>" + label + "</label>\n" +
                   "          <p-dropdown [options]=\"" + name + "Options\" formControlName=\"" + name + "\"\n" +
                   "                     [filter]=\"true\" [showClear]=\"true\" placeholder=\"Selecione...\"\n" +
                   "                     optionLabel=\"label\" optionValue=\"value\"></p-dropdown>\n" +
                   "        </div>";
        }
        // JvDateEdit → p-calendar
        if (type.contains("DateEdit") || type.contains("DateTimePicker") || type.contains("Calendar")) {
            return "<div class=\"p-col-12 p-md-2\">\n" +
                   "          <label>" + label + "</label>\n" +
                   "          <p-calendar formControlName=\"" + name + "\" dateFormat=\"dd/mm/yy\"\n" +
                   "                     [showIcon]=\"true\"></p-calendar>\n" +
                   "        </div>";
        }
        // Edit / MaskEdit → pInputText
        if (type.contains("Edit") && !type.contains("DB")) {
            return "<div class=\"p-col-12 p-md-3\">\n" +
                   "          <label>" + label + "</label>\n" +
                   "          <input pInputText formControlName=\"" + name + "\"" +
                   (readOnly ? " [readonly]=\"true\"" : "") + ">\n" +
                   "        </div>";
        }
        // Memo → pInputTextarea
        if (type.equals("TMemo") || type.equals("TRichEdit") || type.equals("TDBMemo")) {
            return "<div class=\"p-col-12\">\n" +
                   "          <label>" + label + "</label>\n" +
                   "          <textarea pInputTextarea formControlName=\"" + name + "\" rows=\"4\"></textarea>\n" +
                   "        </div>";
        }
        // ComboBox → p-dropdown
        if (type.contains("ComboBox")) {
            return "<div class=\"p-col-12 p-md-3\">\n" +
                   "          <label>" + label + "</label>\n" +
                   "          <p-dropdown [options]=\"" + name + "Options\" formControlName=\"" + name + "\"\n" +
                   "                     placeholder=\"Selecione...\" optionLabel=\"label\" optionValue=\"value\"></p-dropdown>\n" +
                   "        </div>";
        }
        // CheckBox → p-checkbox
        if (type.contains("CheckBox")) {
            return "<div class=\"p-col-12 p-md-3\">\n" +
                   "          <p-checkbox formControlName=\"" + name + "\" label=\"" + label + "\"></p-checkbox>\n" +
                   "        </div>";
        }
        // GroupBox → p-fieldset
        if (type.equals("TGroupBox")) {
            return "<!-- " + label + " -->\n        <p-fieldset legend=\"" + label + "\">";
        }
        // Label
        if (type.contains("Label") || type.equals("TStaticText")) {
            // Labels dentro de grupos já são gerados com seus campos
            return "";
        }
        // Panel
        if (type.equals("TPanel")) {
            return "";
        }
        return "";
    }

    private String generateAngularComponent(DfmForm form) {
        String className = sanitizeFormName(form.getFormName()) + "Component";
        String kebabName = toKebabCase(sanitizeFormName(form.getFormName()));
        StringBuilder ts = new StringBuilder();

        ts.append("import { Component, OnInit } from '@angular/core';\n");
        ts.append("import { FormBuilder, FormGroup } from '@angular/forms';\n");
        ts.append("import { BehaviorSubject } from 'rxjs';\n");
        ts.append("// import { PedidoAutomaticoService } from '../services/pedido-automatico.service';\n\n");

        ts.append("@Component({\n");
        ts.append("  selector: 'app-").append(kebabName).append("',\n");
        ts.append("  templateUrl: './").append(kebabName).append(".component.html',\n");
        ts.append("  styleUrls: ['./").append(kebabName).append(".component.scss']\n");
        ts.append("})\n");
        ts.append("export class ").append(className).append(" implements OnInit {\n\n");

        ts.append("  form: FormGroup;\n");
        ts.append("  items: any[] = [];\n\n");

        // Gera arrays de options para dropdowns
        for (DfmComponent comp : form.getComponents()) {
            if (isDropdown(comp.getDelphiType())) {
                String name = toCamelCase(comp.getName());
                ts.append("  ").append(name).append("Options: any[] = [];\n");
            }
        }
        ts.append("\n");

        ts.append("  constructor(private fb: FormBuilder) {\n");
        ts.append("    this.form = this.fb.group({\n");
        for (DfmComponent comp : form.getComponents()) {
            if (isFormControl(comp.getDelphiType())) {
                String name = toCamelCase(comp.getName());
                ts.append("      ").append(name).append(": [null],\n");
            }
        }
        ts.append("    });\n");
        ts.append("  }\n\n");

        ts.append("  ngOnInit(): void {\n");
        ts.append("    this.loadDropdowns();\n");
        ts.append("    this.pesquisar();\n");
        ts.append("  }\n\n");

        ts.append("  loadDropdowns(): void {\n");
        ts.append("    // TODO: Carregar opções dos dropdowns via service\n");
        for (DfmComponent comp : form.getComponents()) {
            if (isDropdown(comp.getDelphiType())) {
                String name = toCamelCase(comp.getName());
                ts.append("    // this.service.get").append(Character.toUpperCase(name.charAt(0)))
                  .append(name.substring(1)).append("().subscribe(data => this.").append(name).append("Options = data);\n");
            }
        }
        ts.append("  }\n\n");

        ts.append("  pesquisar(): void {\n");
        ts.append("    // TODO: Chamar service com filtros do form\n");
        ts.append("    // const filtros = this.form.value;\n");
        ts.append("    // this.service.pesquisar(filtros).subscribe(data => this.items = data);\n");
        ts.append("  }\n\n");

        // Gera métodos para cada botão com evento click
        for (DfmComponent comp : form.getComponents()) {
            String clickMethod = null;
            for (String evt : comp.getEvents()) {
                if (evt.contains("Click")) {
                    clickMethod = evt.split("→")[1].trim();
                    break;
                }
            }
            if (clickMethod != null) {
                String caption = comp.getProperties().getOrDefault("Caption", comp.getName());
                if (caption.toLowerCase().contains("pesquis")) continue; // já gerado acima
                String angularMethod = clickMethod.replaceAll("(?i)^bbt|Click$", "");
                angularMethod = Character.toLowerCase(angularMethod.charAt(0)) + angularMethod.substring(1);
                ts.append("  ").append(angularMethod).append("(): void {\n");
                ts.append("    // TODO: Implementar — originalmente ").append(clickMethod).append("\n");
                ts.append("  }\n\n");
            }
        }

        ts.append("  onRowDblClick(event: any): void {\n");
        ts.append("    // TODO: Abrir detalhe do registro selecionado\n");
        ts.append("  }\n");
        ts.append("}\n");

        return ts.toString();
    }

    private boolean isFormControl(String type) {
        return type.contains("Edit") || type.contains("Memo") || type.contains("ComboBox") ||
               type.contains("CheckBox") || type.contains("DateTimePicker") || type.contains("Radio") ||
               type.contains("LookupCombo") || type.contains("LgCorporativo") || type.contains("Dropdown");
    }

    private boolean isDropdown(String type) {
        return type.contains("LookupCombo") || type.contains("LgCorporativo") ||
               type.contains("ComboBox") && !type.contains("Check");
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private String sanitizeFormName(String name) {
        if (name == null) return "Form";
        String result = name;

        // Usa o prefixo aprendido do projeto se disponível
        ProjectProfile profile = ProjectProfileStore.getInstance().get();
        if (profile != null) {
            String fp = profile.getNaming().getFormPrefix();
            String dp = profile.getNaming().getDataModulePrefix();
            String frap = profile.getNaming().getFramePrefix();
            // Remove prefixo com ou sem 'T' inicial
            for (String prefix : new String[]{fp, dp, frap, "T" + fp, "T" + dp}) {
                if (prefix != null && !prefix.isEmpty() &&
                        result.toLowerCase().startsWith(prefix.toLowerCase())) {
                    result = result.substring(prefix.length());
                    break;
                }
            }
        } else {
            // Fallback genérico — handles f_, frm, T prefix patterns
            result = result.replaceAll("(?i)^(T)?(f_|F_|frm|fra|Form)", "")
                           .replaceAll("(?i)(Form|frm)$", "");
        }

        // Garante que começa com maiúscula
        if (!result.isEmpty()) {
            result = Character.toUpperCase(result.charAt(0)) + result.substring(1);
        }
        return result.isEmpty() ? name : result;
    }

    private String toAngularComponentName(String formName) {
        return sanitizeFormName(formName) + "Component";
    }

    private String toKebabCase(String s) {
        if (s == null || s.isEmpty()) return "component";
        return s.replaceAll("([A-Z])", "-$1").toLowerCase().replaceAll("^-", "");
    }

    private String toCamelCase(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    public static Map<String, String> getComponentMap() {
        return Collections.unmodifiableMap(COMPONENT_MAP);
    }

    /** Decodifica strings Delphi com caracteres especiais: 'Manuten'#231#227'o' → 'Manutenção' */
    static String decodeDfmString(String s) {
        if (s == null || !s.contains("#")) return s;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            if (s.charAt(i) == '#' && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1))) {
                // Coleta dígitos após #
                int j = i + 1;
                while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
                try {
                    int code = Integer.parseInt(s.substring(i + 1, j));
                    sb.append((char) code);
                } catch (NumberFormatException e) {
                    sb.append(s, i, j);
                }
                i = j;
            } else if (s.charAt(i) == '\'') {
                i++; // pula aspas simples usadas como delimitadores
            } else {
                sb.append(s.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }
}
