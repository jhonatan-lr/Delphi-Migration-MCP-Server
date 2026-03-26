package com.migration.mcp.tools;

import com.migration.mcp.model.*;
import com.migration.mcp.parser.DelphiSourceParser;
import com.migration.mcp.parser.DfmFormParser;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Tool: detect_inconsistencies
 *
 * Compara um arquivo .pas ou .dfm com o ProjectProfile aprendido e detecta
 * desvios de padrão: nomenclatura fora do padrão, componentes incomuns,
 * SQL dinâmico não detectado no restante do projeto, etc.
 *
 * Útil para priorizar arquivos que precisam de mais atenção na migração.
 */
public class DetectInconsistenciesTool extends BaseTool {

    private final DelphiSourceParser sourceParser = new DelphiSourceParser();
    private final DfmFormParser dfmParser = new DfmFormParser();

    @Override
    public McpServerFeatures.SyncToolSpecification getSpecification() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "detect_inconsistencies",
                """
                Analisa um arquivo .pas ou .dfm e detecta inconsistências em relação ao
                perfil aprendido do repositório (learn_repository deve ter sido executado antes).
                
                Detecta: nomes fora do padrão de nomenclatura do projeto, componentes incomuns
                (não usados no restante do projeto), SQL dinâmico quando o projeto usa parameterizado,
                validações com estilo diferente do padrão, dependências (uses) incomuns no projeto,
                e outros desvios que podem indicar código legado ou problemático.
                
                Retorna uma lista categorizada de inconsistências com nível de severidade
                (HIGH/MEDIUM/LOW) e sugestão de correção para facilitar a migração.
                """,
                """
                {
                  "type": "object",
                  "properties": {
                    "file_path": {"type": "string", "description": "Caminho do arquivo .pas ou .dfm"},
                    "content":   {"type": "string", "description": "Conteúdo do arquivo (alternativa ao file_path)"},
                    "file_type": {"type": "string", "enum": ["pas", "dfm"], "description": "Tipo do arquivo (inferido pelo caminho se omitido)"}
                  }
                }
                """
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> withLogging("detect_inconsistencies", args, () -> {
                ProjectProfileStore store = ProjectProfileStore.getInstance();
                if (!store.hasProfile()) {
                    return error("Nenhum perfil aprendido. Execute learn_repository primeiro.");
                }

                String content;
                String filePath;
                String fileType;

                if (args.containsKey("content") && !requireString(args, "content").isBlank()) {
                    content = requireString(args, "content");
                    filePath = optionalString(args, "file_path", "input");
                    fileType = optionalString(args, "file_type",
                            filePath.toLowerCase().endsWith(".dfm") ? "dfm" : "pas");
                } else if (args.containsKey("file_path")) {
                    filePath = requireString(args, "file_path");
                    content = AnalyzeDelphiUnitTool.readFileWithFallback(Path.of(filePath));
                    fileType = filePath.toLowerCase().endsWith(".dfm") ? "dfm" : "pas";
                } else {
                    return error("Informe content ou file_path.");
                }

                ProjectProfile profile = store.get();
                List<Inconsistency> issues = new ArrayList<>();

                if ("dfm".equals(fileType)) {
                    DfmForm form = dfmParser.parse(content);
                    issues.addAll(checkDfmNaming(form, profile));
                    issues.addAll(checkUncommonComponents(form, profile));
                } else {
                    DelphiUnit unit = sourceParser.parse(content, filePath);
                    issues.addAll(checkUnitNaming(unit, filePath, profile));
                    issues.addAll(checkSqlConsistency(unit, profile));
                    issues.addAll(checkValidationStyle(content, profile));
                    issues.addAll(checkUncommonDependencies(unit, profile));
                    issues.addAll(checkFieldNaming(unit, profile));
                    issues.addAll(checkQueryNaming(content, profile));
                }

                // Ordena por severidade
                issues.sort(Comparator.comparing(i -> i.severity.order));

                // Monta resultado
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("file", filePath);
                result.put("profileProject", profile.getProjectName());
                result.put("totalIssues", issues.size());
                result.put("summary", Map.of(
                        "HIGH",   issues.stream().filter(i -> i.severity == Severity.HIGH).count(),
                        "MEDIUM", issues.stream().filter(i -> i.severity == Severity.MEDIUM).count(),
                        "LOW",    issues.stream().filter(i -> i.severity == Severity.LOW).count()
                ));
                result.put("inconsistencies", issues.stream().map(Inconsistency::toMap).toList());

                if (issues.isEmpty()) {
                    result.put("verdict", "OK — arquivo segue os padrões do projeto.");
                } else {
                    long high = issues.stream().filter(i -> i.severity == Severity.HIGH).count();
                    result.put("verdict", high > 0
                            ? high + " inconsistência(s) HIGH detectada(s) — atenção especial na migração."
                            : "Apenas inconsistências MEDIUM/LOW — migração relativamente direta.");
                }

                return success(result);
        }));
    }

    // ── Verificações ──────────────────────────────────────────────────────────

    private List<Inconsistency> checkUnitNaming(DelphiUnit unit, String filePath, ProjectProfile profile) {
        List<Inconsistency> issues = new ArrayList<>();
        String expectedPrefix = profile.getNaming().getUnitPrefix();
        String unitName = unit.getUnitName();
        if (unitName == null) return issues;

        String fileName = Path.of(filePath).getFileName().toString().replace(".pas", "").replace(".PAS", "");

        // Verifica prefixo da unit
        if (expectedPrefix != null && !expectedPrefix.isEmpty() &&
                !unitName.toLowerCase().startsWith(expectedPrefix.toLowerCase()) &&
                !fileName.toLowerCase().startsWith(expectedPrefix.toLowerCase())) {
            issues.add(new Inconsistency(
                    Severity.LOW,
                    "naming",
                    "Unit '" + unitName + "' não usa o prefixo padrão do projeto '" + expectedPrefix + "'",
                    "Renomear para '" + expectedPrefix + capitalize(unitName) + "'"
            ));
        }

        // Verifica prefixo das classes
        for (DelphiClass dc : unit.getClasses()) {
            String name = dc.getName();
            if (name == null) continue;

            // Classe de form sem o prefixo de form do projeto
            if ("TForm".equals(dc.getClassType())) {
                String fp = profile.getNaming().getFormPrefix();
                String withoutT = name.startsWith("T") ? name.substring(1) : name;
                if (!withoutT.toLowerCase().startsWith(fp.toLowerCase())) {
                    issues.add(new Inconsistency(
                            Severity.LOW,
                            "naming",
                            "Form '" + name + "' não usa o prefixo '" + fp + "' (padrão detectado no projeto)",
                            "Renomear para 'T" + fp + capitalize(withoutT) + "'"
                    ));
                }
            }
        }
        return issues;
    }

    private List<Inconsistency> checkSqlConsistency(DelphiUnit unit, ProjectProfile profile) {
        List<Inconsistency> issues = new ArrayList<>();
        String projectParamStyle = profile.getSqlConventions().getParamStyle();

        for (SqlQuery q : unit.getSqlQueries()) {
            if (q.getSql() == null) continue;

            // Detecta estilo de parâmetro diferente do projeto
            boolean hasNamedColon = q.getSql().matches(".*:\\b[A-Z_]+\\b.*");
            boolean hasNamedAt    = q.getSql().matches(".*@[A-Z_]+.*");
            boolean hasPositional = q.getSql().contains("?");

            String detectedStyle = hasNamedColon ? "named_colon" : hasNamedAt ? "named_at" : hasPositional ? "positional" : "none";

            if (!"none".equals(detectedStyle) && !detectedStyle.equals(projectParamStyle)) {
                issues.add(new Inconsistency(
                        Severity.MEDIUM,
                        "sql",
                        "Query " + q.getId() + " usa parâmetro estilo '" + detectedStyle + "' mas o projeto usa '" + projectParamStyle + "'",
                        "Padronizar para o estilo '" + projectParamStyle + "' antes de migrar para JPA"
                ));
            }

            // SQL dinâmico quando o projeto não usa
            if (!profile.getSqlConventions().isUsesDynamicSql() && q.getSql().contains(" + ")) {
                issues.add(new Inconsistency(
                        Severity.HIGH,
                        "sql",
                        "SQL dinâmico detectado em " + q.getId() + " — projeto usa SQL parametrizado. Risco de SQL Injection.",
                        "Converter para SQL parametrizado com '" + projectParamStyle + "' antes de migrar"
                ));
            }
        }
        return issues;
    }

    private List<Inconsistency> checkValidationStyle(String content, ProjectProfile profile) {
        List<Inconsistency> issues = new ArrayList<>();
        String projectStyle = profile.getCodePatterns().getValidationStyle();

        boolean hasShowMessage = Pattern.compile("(?i)ShowMessage\\s*\\(").matcher(content).find();
        boolean hasRaise       = Pattern.compile("(?i)raise\\s+Exception").matcher(content).find();
        boolean hasMsgDlg      = Pattern.compile("(?i)MessageDlg\\s*\\(").matcher(content).find();

        // Detecta estilo de validação neste arquivo
        String fileStyle = hasShowMessage ? "showmessage" : hasRaise ? "raise" : hasMsgDlg ? "messagedlg" : "none";

        if (!"none".equals(fileStyle) && !fileStyle.equals(projectStyle)) {
            issues.add(new Inconsistency(
                    Severity.LOW,
                    "code_pattern",
                    "Estilo de validação '" + fileStyle + "' difere do padrão do projeto '" + projectStyle + "'",
                    "Ao migrar, padronizar para o equivalente Java do estilo '" + projectStyle + "'"
            ));
        }
        return issues;
    }

    private List<Inconsistency> checkUncommonDependencies(DelphiUnit unit, ProjectProfile profile) {
        List<Inconsistency> issues = new ArrayList<>();
        List<String> commonUtils = profile.getCodePatterns().getCommonUtils();
        if (commonUtils == null || commonUtils.isEmpty()) return issues;

        for (String dep : unit.getUses()) {
            // Verifica se é uma unit do projeto (não stdlib) que não aparece no perfil
            if (!isDelphiStdLib(dep) && !dep.startsWith("FMX") && !dep.startsWith("VCL") &&
                    !commonUtils.contains(dep) &&
                    !profile.getDbTechnology().toLowerCase().contains(dep.toLowerCase())) {
                issues.add(new Inconsistency(
                        Severity.LOW,
                        "dependency",
                        "Dependência '" + dep + "' não encontrada em outros arquivos do projeto",
                        "Verificar se esta unit ainda é usada ou é código legado"
                ));
            }
        }
        return issues;
    }

    private List<Inconsistency> checkFieldNaming(DelphiUnit unit, ProjectProfile profile) {
        List<Inconsistency> issues = new ArrayList<>();
        String expectedPrefix = profile.getNaming().getFieldPrefix();
        if (expectedPrefix == null || expectedPrefix.isEmpty()) return issues;

        for (DelphiClass dc : unit.getClasses()) {
            for (DelphiField field : dc.getFields()) {
                if (field.isComponent()) continue;
                String name = field.getName();
                if (name == null || name.length() < 2) continue;
                if (!name.startsWith(expectedPrefix)) {
                    issues.add(new Inconsistency(
                            Severity.LOW,
                            "naming",
                            "Campo '" + name + "' em '" + dc.getName() + "' não usa prefixo '" + expectedPrefix + "' (padrão do projeto)",
                            "Renomear para '" + expectedPrefix + capitalize(name) + "'"
                    ));
                }
            }
        }
        return issues;
    }

    private List<Inconsistency> checkQueryNaming(String content, ProjectProfile profile) {
        List<Inconsistency> issues = new ArrayList<>();
        String expectedQryPrefix = profile.getNaming().getQueryPrefix();
        if (expectedQryPrefix == null) return issues;

        Pattern qryDecl = Pattern.compile("(?i)(\\w+)\\s*:\\s*T(?:FDQuery|ADOQuery|IBQuery|ZQuery|UniQuery|Query|SQLQuery)");
        Matcher m = qryDecl.matcher(content);
        while (m.find()) {
            String varName = m.group(1);
            if (!varName.toLowerCase().startsWith(expectedQryPrefix.toLowerCase())) {
                issues.add(new Inconsistency(
                        Severity.LOW,
                        "naming",
                        "Query '" + varName + "' não usa prefixo '" + expectedQryPrefix + "' (padrão do projeto)",
                        "Renomear para '" + expectedQryPrefix + capitalize(varName) + "'"
                ));
            }
        }
        return issues;
    }

    private List<Inconsistency> checkDfmNaming(DfmForm form, ProjectProfile profile) {
        List<Inconsistency> issues = new ArrayList<>();
        String fp = profile.getNaming().getFormPrefix();
        if (form.getFormName() == null || fp == null) return issues;

        String name = form.getFormName();
        String withoutT = name.startsWith("T") ? name.substring(1) : name;
        if (!withoutT.toLowerCase().startsWith(fp.toLowerCase())) {
            issues.add(new Inconsistency(
                    Severity.LOW,
                    "naming",
                    "Form '" + name + "' não usa o prefixo '" + fp + "' detectado no projeto",
                    "Renomear para '" + fp + capitalize(withoutT) + "'"
            ));
        }
        return issues;
    }

    private List<Inconsistency> checkUncommonComponents(DfmForm form, ProjectProfile profile) {
        List<Inconsistency> issues = new ArrayList<>();
        List<String> topComponents = profile.getTopComponents();
        if (topComponents == null || topComponents.isEmpty()) return issues;

        for (DfmComponent comp : form.getComponents()) {
            String type = comp.getDelphiType();
            if (type == null) continue;
            // Detecta componentes que nunca aparecem no restante do projeto
            boolean foundInProfile = topComponents.stream()
                    .anyMatch(tc -> tc.equalsIgnoreCase(type));
            if (!foundInProfile && type.startsWith("T") && !isCommonVclComponent(type)) {
                issues.add(new Inconsistency(
                        Severity.MEDIUM,
                        "component",
                        "Componente '" + comp.getName() + " : " + type + "' não foi encontrado em outros forms do projeto",
                        "Verificar se é um componente de terceiros sem suporte Angular — avaliar substituição"
                ));
            }
        }
        return issues;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isDelphiStdLib(String unit) {
        return unit.matches("(?i)SysUtils|Classes|Forms|Controls|Graphics|Windows|Messages|" +
                "Dialogs|StdCtrls|ExtCtrls|ComCtrls|DBGrids|DB|Variants|Math|" +
                "DateUtils|StrUtils|IOUtils|System|Types|Buttons|Menus|ToolWin|" +
                "ImgList|ActnList|ShellAPI|Registry|IniFiles|XMLDoc|MSXML");
    }

    private boolean isCommonVclComponent(String type) {
        return type.matches("TButton|TEdit|TLabel|TPanel|TMemo|TComboBox|TCheckBox|" +
                "TRadioButton|TGroupBox|TTabControl|TPageControl|TTabSheet|" +
                "TImage|TTimer|TMainMenu|TPopupMenu|TStatusBar|TToolBar|" +
                "TScrollBox|TSplitter|TBevel|TShape|TListBox|TListView|TTreeView|" +
                "TTrackBar|TProgressBar|TDateTimePicker|TCalendar|TBitBtn|TSpeedButton|" +
                "TStringGrid|TDrawGrid|TDBGrid|TDBEdit|TDBMemo|TDBComboBox|TDBCheckBox|" +
                "TDataSource|TDBNavigator|TDBLookupComboBox");
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    enum Severity {
        HIGH(1), MEDIUM(2), LOW(3);
        final int order;
        Severity(int order) { this.order = order; }
    }

    static class Inconsistency {
        final Severity severity;
        final String   category;
        final String   description;
        final String   suggestion;

        Inconsistency(Severity severity, String category, String description, String suggestion) {
            this.severity    = severity;
            this.category    = category;
            this.description = description;
            this.suggestion  = suggestion;
        }

        Map<String, String> toMap() {
            return Map.of(
                    "severity",    severity.name(),
                    "category",    category,
                    "description", description,
                    "suggestion",  suggestion
            );
        }
    }
}
