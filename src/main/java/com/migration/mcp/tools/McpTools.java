package com.migration.mcp.tools;

import com.migration.mcp.generator.JavaCodeGenerator;
import com.migration.mcp.generator.MigrationPlanGenerator;
import com.migration.mcp.model.*;
import com.migration.mcp.parser.DelphiSourceParser;
import com.migration.mcp.parser.DfmFormParser;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TOOL 1: Analyze Delphi Unit (.pas)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
class AnalyzeDelphiUnitTool extends BaseTool {

    private final DelphiSourceParser parser = new DelphiSourceParser();

    @Override
    public McpServerFeatures.SyncToolSpecification getSpecification() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "analyze_delphi_unit",
                "Analisa um arquivo .pas (Pascal/Delphi) e extrai sua estrutura completa: classes, " +
                "métodos, campos, dependências (uses), queries SQL e regras de negócio. " +
                "Aceita conteúdo do arquivo OU caminho no filesystem. " +
                "Use include_body=false (padrão) para output compacto, true para incluir corpo dos métodos.",
                """
                {
                  "type": "object",
                  "properties": {
                    "content": {"type": "string", "description": "Conteúdo do arquivo .pas (alternativa ao file_path)"},
                    "file_path": {"type": "string", "description": "Caminho para o arquivo .pas no filesystem"},
                    "unit_name": {"type": "string", "description": "Nome da unit (opcional, extraído automaticamente)"},
                    "include_body": {"type": "boolean", "description": "Incluir corpo dos métodos no resultado (padrão: false)"}
                  },
                  "required": []
                }
                """
        );
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> withLogging("analyze_delphi_unit", args, () -> {
                String content = getContent(args);
                String filePath = optionalString(args, "file_path", "unknown.pas");
                boolean includeBody = args.containsKey("include_body") && Boolean.TRUE.equals(args.get("include_body"));
                DelphiUnit unit = parser.parse(content, filePath);
                // Remove bodies para reduzir output (de 140K para ~15K)
                if (!includeBody) {
                    for (DelphiClass dc : unit.getClasses()) {
                        for (DelphiProcedure proc : dc.getMethods()) {
                            proc.setBody(null);
                        }
                    }
                }
                unit.setRawContent(null); // nunca incluir raw content na análise
                return success(unit);
        }));
    }

    private String getContent(Map<String, Object> args) throws IOException {
        if (args.containsKey("content")) {
            String content = requireString(args, "content");
            // Conteúdo válido precisa ter pelo menos 10 chars (menor unit possível)
            if (content != null && !content.isBlank() && content.trim().length() >= 10) return content;
        }
        if (args.containsKey("file_path")) {
            String path = requireString(args, "file_path");
            return readFileWithFallback(Path.of(path));
        }
        throw new IOException("Informe 'file_path' ou 'content' com o conteúdo do arquivo.");
    }

    /** Lê arquivo tentando UTF-8 primeiro, depois ISO-8859-1 (arquivos Delphi legados) */
    static String readFileWithFallback(java.nio.file.Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("Arquivo não encontrado: " + path.toAbsolutePath() +
                    "\nVerifique o caminho e a extensão (.pas, .dfm).");
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (java.nio.charset.MalformedInputException e) {
            return Files.readString(path, java.nio.charset.Charset.forName("ISO-8859-1"));
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TOOL 2: Analyze DFM Form
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
class AnalyzeDfmFormTool extends BaseTool {

    private final DfmFormParser parser = new DfmFormParser();

    @Override
    public McpServerFeatures.SyncToolSpecification getSpecification() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "analyze_dfm_form",
                "Analisa um arquivo .dfm (Delphi Form) e mapeia seus componentes visuais para " +
                "equivalentes Angular Material. Gera template HTML e TypeScript do componente Angular.",
                buildInputSchema(
                        "content", "string", "Conteúdo do arquivo .dfm",
                        "file_path", "string", "Caminho para o arquivo .dfm no filesystem"
                )
        );
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> withLogging("analyze_dfm_form", args, () -> {
                String content = args.containsKey("content") && !requireString(args, "content").isBlank() && requireString(args, "content").trim().length() >= 10
                        ? requireString(args, "content")
                        : AnalyzeDelphiUnitTool.readFileWithFallback(Path.of(requireString(args, "file_path")));
                DfmForm form = parser.parse(content);
                return success(form);
        }));
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TOOL 3: Extract SQL Queries
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
class ExtractSqlQueresTool extends BaseTool {

    private final DelphiSourceParser parser = new DelphiSourceParser();

    @Override
    public McpServerFeatures.SyncToolSpecification getSpecification() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "extract_sql_queries",
                "Extrai todas as queries SQL encontradas em código Delphi — incluindo SQL inline em " +
                "TQuery.SQL.Text, .SQL.Add(), e strings SQL. Para cada query, sugere equivalente " +
                "JPA/JPQL e método de Spring Data Repository.",
                buildInputSchema(
                        "content", "string", "Conteúdo do código Delphi (.pas)",
                        "file_path", "string", "Caminho para o arquivo .pas"
                )
        );
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> withLogging("extract_sql_queries", args, () -> {
                String content = args.containsKey("content") && !requireString(args, "content").isBlank() && requireString(args, "content").trim().length() >= 10
                        ? requireString(args, "content")
                        : AnalyzeDelphiUnitTool.readFileWithFallback(Path.of(requireString(args, "file_path")));
                List<SqlQuery> queries = parser.extractSqlQueries(content);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("totalFound", queries.size());
                result.put("queries", queries);
                return success(result);
        }));
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TOOL 4: Extract Business Rules
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
class ExtractBusinessRules extends BaseTool {

    private final DelphiSourceParser parser = new DelphiSourceParser();

    @Override
    public McpServerFeatures.SyncToolSpecification getSpecification() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "extract_business_rules",
                "Extrai regras de negócio do código Delphi: validações (if/then com ShowMessage ou raise), " +
                "cálculos complexos, verificações de consistência. Também detecta lógica de inicialização " +
                "de tela (FormShow/FormCreate): valores default, pré-seleções de combos, auto-loads e " +
                "estados iniciais de componentes. Detecta regras de estado de botões (AfterScroll + Click): " +
                "condições de habilitação, confirmações, ações executadas e permissões. " +
                "Extrai validações por campo (ValidacaoOk → Validators Angular), " +
                "colorização de grids (CalcCellColors → [ngClass]), dependências entre campos " +
                "(Exit/Change), eventos de dataset (AfterInsert/CalcFields), sequences (BeforeUpdateRecord), " +
                "transações (StartTransaction/Commit) e fluxo entre telas (MakeShowModal com params). " +
                "Para cada regra, fornece estratégia de migração e código Java/Angular sugerido.",
                buildInputSchema(
                        "content", "string", "Conteúdo do código Delphi (.pas)",
                        "file_path", "string", "Caminho para o arquivo .pas"
                )
        );
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> withLogging("extract_business_rules", args, () -> {
                String content = args.containsKey("content") && !requireString(args, "content").isBlank() && requireString(args, "content").trim().length() >= 10
                        ? requireString(args, "content")
                        : AnalyzeDelphiUnitTool.readFileWithFallback(Path.of(requireString(args, "file_path")));
                List<BusinessRule> rules = parser.extractBusinessRules(content);
                List<FormInitialization> formInits = parser.extractFormInitialization(content);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("totalFound", rules.size());
                result.put("byType", rules.stream().collect(Collectors.groupingBy(BusinessRule::getRuleType, Collectors.counting())));
                result.put("rules", rules);

                // Form initialization (FormShow/FormCreate/FormActivate)
                if (!formInits.isEmpty()) {
                    int totalInit = formInits.stream().mapToInt(FormInitialization::totalDetected).sum();
                    result.put("formInitializationTotal", totalInit);
                    result.put("formInitialization", formInits);
                }

                // Button state rules (AfterScroll + Click handlers)
                List<ButtonStateRule> buttonRules = parser.extractButtonStateRules(content);
                if (!buttonRules.isEmpty()) {
                    result.put("buttonStateRulesTotal", buttonRules.size());
                    result.put("buttonStateRules", buttonRules);
                }

                // Field validation rules (ValidacaoOk → per-field structured rules)
                List<FieldValidationRule> fieldValidations = parser.extractFieldValidationRules(content);
                if (!fieldValidations.isEmpty()) {
                    result.put("fieldValidationRulesTotal", fieldValidations.size());
                    result.put("fieldValidationRules", fieldValidations);
                }

                // CalcCellColors (grid cell color coding)
                List<CalcCellColorRule> colorRules = parser.extractCalcCellColorRules(content);
                if (!colorRules.isEmpty()) {
                    result.put("calcCellColorRulesTotal", colorRules.size());
                    result.put("calcCellColorRules", colorRules);
                }

                // Dependent field logic (Exit/Change → field cascades)
                List<DependentFieldRule> depFields = parser.extractDependentFieldRules(content);
                if (!depFields.isEmpty()) {
                    result.put("dependentFieldRulesTotal", depFields.size());
                    result.put("dependentFieldRules", depFields);
                }

                // Dataset event rules (AfterInsert, CalcFields, etc)
                List<DatasetEventRule> dsEvents = parser.extractDatasetEventRules(content);
                if (!dsEvents.isEmpty()) {
                    result.put("datasetEventRulesTotal", dsEvents.size());
                    result.put("datasetEventRules", dsEvents);
                }

                // Provider update rules (BeforeUpdateRecord → sequences + key propagation)
                List<ProviderUpdateRule> provRules = parser.extractProviderUpdateRules(content);
                if (!provRules.isEmpty()) {
                    result.put("providerUpdateRulesTotal", provRules.size());
                    result.put("providerUpdateRules", provRules);
                }

                // Transaction boundaries (StartTransaction/Commit/Rollback)
                List<TransactionBoundary> txBounds = parser.extractTransactionBoundaries(content);
                if (!txBounds.isEmpty()) {
                    result.put("transactionBoundariesTotal", txBounds.size());
                    result.put("transactionBoundaries", txBounds);
                }

                // Cross-form data flow (params in, return, callback)
                List<CrossFormDataFlow> crossFlows = parser.extractCrossFormDataFlow(content);
                if (!crossFlows.isEmpty()) {
                    result.put("crossFormDataFlowTotal", crossFlows.size());
                    result.put("crossFormDataFlow", crossFlows);
                }

                return success(result);
        }));
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TOOL 5: Generate Migration Plan
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
class GenerateMigrationPlan extends BaseTool {

    private final DelphiSourceParser sourceParser = new DelphiSourceParser();
    private final DfmFormParser dfmParser = new DfmFormParser();
    private final MigrationPlanGenerator planGenerator = new MigrationPlanGenerator();

    @Override
    public McpServerFeatures.SyncToolSpecification getSpecification() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "generate_migration_plan",
                "Gera um plano de migração completo e estruturado a partir de uma lista de arquivos " +
                "Delphi (.pas e .dfm). O plano inclui: resumo da análise, estimativa de esforço, " +
                "arquitetura sugerida (Spring Boot + Angular), fases de migração com tasks, " +
                "riscos identificados e recomendações. Retorna tanto JSON estruturado quanto Markdown.",
                """
                {
                  "type": "object",
                  "properties": {
                    "project_name": {"type": "string", "description": "Nome do projeto"},
                    "pas_files": {"type": "array", "items": {"type": "string"}, "description": "Lista de caminhos de arquivos .pas"},
                    "dfm_files": {"type": "array", "items": {"type": "string"}, "description": "Lista de caminhos de arquivos .dfm"},
                    "pas_contents": {"type": "array", "items": {"type": "string"}, "description": "Conteúdos dos arquivos .pas (alternativa a pas_files)"},
                    "dfm_contents": {"type": "array", "items": {"type": "string"}, "description": "Conteúdos dos arquivos .dfm (alternativa a dfm_files)"},
                    "output_format": {"type": "string", "enum": ["json", "markdown", "both"], "description": "Formato do output"}
                  },
                  "required": ["project_name"]
                }
                """
        );
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> withLogging("generate_migration_plan", args, () -> {
                String projectName = requireString(args, "project_name");
                String format = optionalString(args, "output_format", "both");

                List<DelphiUnit> units = new ArrayList<>();
                List<DfmForm> forms = new ArrayList<>();

                // Processa .pas
                log.info("  Processando arquivos .pas...");
                processFiles(args, "pas_files", "pas_contents", ".pas", (content, path) ->
                        units.add(sourceParser.parse(content, path)));
                log.info("  {} units parseadas", units.size());

                // Processa .dfm
                log.info("  Processando arquivos .dfm...");
                processFiles(args, "dfm_files", "dfm_contents", ".dfm", (content, path) ->
                        forms.add(dfmParser.parse(content)));
                log.info("  {} forms parseados", forms.size());

                log.info("  Gerando plano de migração...");
                MigrationPlan plan = planGenerator.generate(units, forms, projectName);

                Map<String, Object> result = new LinkedHashMap<>();
                if ("json".equals(format) || "both".equals(format)) {
                    result.put("plan", plan);
                }
                if ("markdown".equals(format) || "both".equals(format)) {
                    result.put("markdown", planGenerator.toMarkdown(plan));
                }
                return success(result);
        }));
    }

    @FunctionalInterface
    interface FileProcessor {
        void process(String content, String path) throws IOException;
    }

    @SuppressWarnings("unchecked")
    private void processFiles(Map<String, Object> args, String filesKey, String contentsKey,
                              String ext, FileProcessor processor) throws IOException {
        if (args.containsKey(filesKey)) {
            List<String> paths = (List<String>) args.get(filesKey);
            for (String p : paths) {
                processor.process(AnalyzeDelphiUnitTool.readFileWithFallback(Path.of(p)), p);
            }
        } else if (args.containsKey(contentsKey)) {
            List<String> contents = (List<String>) args.get(contentsKey);
            for (int i = 0; i < contents.size(); i++) {
                processor.process(contents.get(i), "file" + i + ext);
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TOOL 6: Generate Java Class
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
class GenerateJavaClassTool extends BaseTool {

    private final DelphiSourceParser parser = new DelphiSourceParser();
    private final DfmFormParser dfmParser = new DfmFormParser();
    private final JavaCodeGenerator generator = new JavaCodeGenerator();

    @Override
    public McpServerFeatures.SyncToolSpecification getSpecification() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "generate_java_class",
                "Converte uma unit/classe Delphi em código Java para Spring Boot. " +
                "Gera: @Entity JPA, JpaRepository, @Service com regras de negócio migradas, " +
                "e @RestController com endpoints CRUD completos. " +
                "Passe também o dfm_file_path para extrair campos automaticamente.",
                """
                {
                  "type": "object",
                  "properties": {
                    "content": {"type": "string", "description": "Conteúdo do arquivo .pas"},
                    "file_path": {"type": "string", "description": "Caminho do arquivo .pas"},
                    "dfm_file_path": {"type": "string", "description": "Caminho do arquivo .dfm (para extrair campos)"},
                    "package_name": {"type": "string", "description": "Package Java base (ex: com.empresa.projeto)"},
                    "generate": {"type": "array", "items": {"type": "string"}, "description": "O que gerar: entity, repository, service, controller (padrão: todos)"}
                  },
                  "required": ["package_name"]
                }
                """
        );
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> withLogging("generate_java_class", args, () -> {
                String content = args.containsKey("content") && !requireString(args, "content").isBlank() && requireString(args, "content").trim().length() >= 10
                        ? requireString(args, "content")
                        : AnalyzeDelphiUnitTool.readFileWithFallback(Path.of(requireString(args, "file_path")));

                String packageName = requireString(args, "package_name");
                @SuppressWarnings("unchecked")
                List<String> generate = args.containsKey("generate")
                        ? (List<String>) args.get("generate")
                        : List.of("entity", "repository", "service", "resource", "dto", "vo");

                DelphiUnit unit = parser.parse(content, optionalString(args, "file_path", "input.pas"));
                log.info("  Unit parseada: {} classes, {} queries, {} regras",
                        unit.getClasses().size(), unit.getSqlQueries().size(), unit.getBusinessRules().size());

                // Parseia DFM para extrair campos se disponível
                List<DfmForm.DatasetField> dfmFields = null;
                if (args.containsKey("dfm_file_path")) {
                    try {
                        String dfmContent = AnalyzeDelphiUnitTool.readFileWithFallback(
                                Path.of(requireString(args, "dfm_file_path")));
                        DfmForm dfmForm = dfmParser.parse(dfmContent);
                        dfmFields = dfmForm.getDatasetFields();
                        log.info("  DFM parseado: {} campos de dataset, {} colunas de grid",
                                dfmFields.size(), dfmForm.getGridColumns().size());
                    } catch (Exception e) {
                        log.warn("  Não foi possível parsear DFM: {}", e.getMessage());
                    }
                } else {
                    // Tenta inferir o .dfm a partir do .pas
                    String pasPath = optionalString(args, "file_path", "");
                    if (!pasPath.isEmpty()) {
                        String dfmPath = pasPath.replaceAll("(?i)\\.pas$", ".dfm");
                        if (java.nio.file.Files.exists(Path.of(dfmPath))) {
                            try {
                                String dfmContent = AnalyzeDelphiUnitTool.readFileWithFallback(Path.of(dfmPath));
                                DfmForm dfmForm = dfmParser.parse(dfmContent);
                                dfmFields = dfmForm.getDatasetFields();
                                log.info("  DFM auto-detectado: {} campos de dataset", dfmFields.size());
                            } catch (Exception e) {
                                log.warn("  Não foi possível parsear DFM auto-detectado: {}", e.getMessage());
                            }
                        }
                    }
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("unitAnalysis", Map.of(
                        "unitName", unit.getUnitName() != null ? unit.getUnitName() : "unknown",
                        "classesFound", unit.getClasses().size(),
                        "sqlQueriesFound", unit.getSqlQueries().size(),
                        "businessRulesFound", unit.getBusinessRules().size(),
                        "dfmFieldsFound", dfmFields != null ? dfmFields.size() : 0
                ));

                Map<String, String> generatedFiles = new LinkedHashMap<>();
                final List<DfmForm.DatasetField> finalDfmFields = dfmFields;

                for (DelphiClass dc : unit.getClasses()) {
                    String baseName = dc.getName().replaceAll("^T", "");
                    log.info("  Gerando código para classe: {}", baseName);
                    // Item 5: remove prefixo frm/frmXxx dos nomes de arquivo
                    String cleanBase = baseName.replaceAll("^(?i)(frm|Frm)", "");
                    if (cleanBase.isEmpty()) cleanBase = baseName;

                    if (generate.contains("entity")) {
                        // ══ Nova abordagem: entity vem da SQL, não do dataset ══
                        // 1. Extrair tabelas únicas do FROM das SQLs
                        // 2. Filtrar tabelas de infraestrutura (cad*, que já existem)
                        // 3. Gerar 1 entity por tabela real

                        TargetPatterns tp = ProjectProfileStore.getInstance().getPatterns();
                        Map<String, String> entityTables = resolveEntityTables(unit.getSqlQueries(), tp);

                        if (entityTables.isEmpty()) {
                            // Fallback: 1 entity genérica
                            String mainTable = extractMainTable(unit.getSqlQueries(), 0);
                            generatedFiles.put(cleanBase + "Entity.java",
                                    generator.generateEntity(dc, packageName, finalDfmFields, mainTable));
                        } else {
                            // Agrupa dfmFields por dataset para associar campos a cada entity
                            Map<String, List<DfmForm.DatasetField>> byDataset = new LinkedHashMap<>();
                            if (finalDfmFields != null) {
                                for (DfmForm.DatasetField df : finalDfmFields) {
                                    String ds = df.getDatasetName() != null ? df.getDatasetName() : "default";
                                    byDataset.computeIfAbsent(ds, k -> new ArrayList<>()).add(df);
                                }
                            }

                            for (Map.Entry<String, String> tableEntry : entityTables.entrySet()) {
                                String tableName = tableEntry.getKey();
                                String entityName = tableEntry.getValue().replace("Entity", "");

                                // Encontrar campos: tenta match dataset→tabela, senão usa todos os dfmFields
                                List<DfmForm.DatasetField> entityFields = findFieldsForTable(tableName, byDataset, tp);
                                if (entityFields == null || entityFields.isEmpty()) {
                                    entityFields = finalDfmFields; // fallback
                                }

                                generatedFiles.put(entityName + "Entity.java",
                                        generator.generateEntity(dc, packageName, entityFields, tableName, entityName));
                            }
                        }
                    }
                    // Tabela principal e entityClassName para Repository/Vo/PesquisaDto
                    String tableForRepo = extractMainTable(unit.getSqlQueries(), 0);
                    String entityClassForRepo = null;
                    if (tableForRepo != null) {
                        TargetPatterns tpRepo = ProjectProfileStore.getInstance().getPatterns();
                        if (tpRepo != null && tpRepo.getKnownTables().containsKey(tableForRepo)) {
                            entityClassForRepo = tpRepo.getKnownTables().get(tableForRepo).getEntity();
                            if (entityClassForRepo != null) entityClassForRepo = entityClassForRepo.replace("Entity", "");
                        }
                    }
                    String repoBase = entityClassForRepo != null ? entityClassForRepo : cleanBase;
                    if (generate.contains("repository")) {
                        generatedFiles.put(repoBase + "Repository.java", generator.generateRepository(dc, packageName, tableForRepo, finalDfmFields, entityClassForRepo));
                    }
                    if (generate.contains("service")) {
                        generatedFiles.put(cleanBase + "Service.java", generator.generateService(dc, packageName, unit.getSqlQueries(), unit.getBusinessRules()));
                    }
                    if (generate.contains("controller") || generate.contains("resource")) {
                        generatedFiles.put(cleanBase + "Resource.java", generator.generateController(dc, packageName));
                    }
                    if (generate.contains("dto")) {
                        generatedFiles.put(cleanBase + "Dto.java", generator.generateDto(dc, packageName, finalDfmFields));
                        generatedFiles.put("Pesquisa" + repoBase + "Dto.java", generator.generatePesquisaDto(dc, packageName, finalDfmFields, tableForRepo, entityClassForRepo));
                    }
                    if (generate.contains("vo")) {
                        generatedFiles.put("Grid" + repoBase + "Vo.java", generator.generateVo(dc, packageName, finalDfmFields, tableForRepo, entityClassForRepo));
                    }
                }

                log.info("  {} arquivos Java gerados", generatedFiles.size());
                result.put("generatedFiles", generatedFiles);
                return success(result);
        }));
    }

    /** Extrai tabela principal da N-ésima SQL SELECT */
    private String extractMainTable(List<SqlQuery> queries, int index) {
        int count = 0;
        for (SqlQuery sq : queries) {
            if ("SELECT".equals(sq.getQueryType()) && sq.getTablesUsed() != null && !sq.getTablesUsed().isEmpty()) {
                if (count == index) return sq.getTablesUsed().get(0).toLowerCase();
                count++;
            }
        }
        return null;
    }

    /** Tabelas de infraestrutura que já existem como entities no projeto — não gerar */
    private static final Set<String> INFRA_TABLE_PREFIXES = Set.of(
            "cadfil", "cadprod", "cadassoc", "cadforn", "cadcli", "caduser",
            "cadsecao", "cadgrupo", "cadsubgr", "caddepto", "cadmunicipio",
            "cadbanco", "cadncm", "caduf", "cadcfop", "cadcompr", "cadopescfop",
            "cadoprec", "cadoppag", "cadfrec", "cadfpag", "cadclpag", "cadccust",
            "bdomnfe", "bdomnfs", "estmven", "estmped", "cadncfop", "cadparam"
    );

    /**
     * Resolve quais entities gerar a partir das SQLs.
     * Retorna Map<tableName, entityClassName> com tabelas reais (não infra).
     *
     * Lógica: identifica a tabela principal da tela (primeira SQL SELECT) e só gera
     * entities para ela e seus details (via masterDetailRelationships). Tabelas de
     * SQLs auxiliares (lookups, subqueries) viram @ManyToOne, não entities separadas.
     */
    private Map<String, String> resolveEntityTables(List<SqlQuery> queries, TargetPatterns tp) {
        // Passo 1: coletar todas as tabelas candidatas
        Map<String, String> candidates = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();

        for (SqlQuery sq : queries) {
            if (sq.getTablesUsed() == null || sq.getTablesUsed().isEmpty()) continue;

            String mainTable = findBusinessTable(sq.getTablesUsed(), sq.getSql());
            if (mainTable == null) continue;

            if (mainTable.startsWith("tmp") || mainTable.equals("temp") || !seen.add(mainTable)) continue;

            if (INFRA_TABLE_PREFIXES.contains(mainTable)) continue;
            if (mainTable.startsWith("cad") && !mainTable.contains("prop") && !mainTable.contains("solic")) continue;

            if (isOnlyInSubquery(mainTable, sq.getSql())) continue;

            String entityName;
            if (tp != null && tp.getKnownTables().containsKey(mainTable)) {
                entityName = tp.getKnownTables().get(mainTable).getEntity();
            } else {
                entityName = inferEntityFromTable(mainTable);
            }

            if (entityName != null) {
                candidates.put(mainTable, entityName);
            }
        }

        // Passo 2: filtrar — só manter a tabela principal + seus details
        if (candidates.size() <= 1) return candidates;

        Map<String, String> result = new LinkedHashMap<>();
        // Tabela principal: prioriza estm* (master de negócio) sobre bdo*, mov*, etc.
        String primaryTable = electPrimaryTable(candidates.keySet());
        result.put(primaryTable, candidates.get(primaryTable));

        // Incluir tabelas que são detail da principal (via masterDetailRelationships ou prefixo estd/estm)
        String primaryPrefix = extractTablePrefix(primaryTable);
        for (Map.Entry<String, String> e : candidates.entrySet()) {
            String table = e.getKey();
            if (table.equals(primaryTable)) continue;

            // Detail via masterDetailRelationships
            if (tp != null && tp.getMasterDetailRelationships().containsKey(table)) {
                String master = tp.getMasterDetailRelationships().get(table).getMaster();
                if (master != null && master.equals(primaryTable)) {
                    result.put(table, e.getValue());
                    continue;
                }
            }

            // Detail por convenção: mesmo prefixo de negócio (estmpropcc → estdsoliccompracc)
            // ou estd* com mesmo sufixo que estm*
            if (primaryTable.startsWith("estm") && table.startsWith("estd")) {
                result.put(table, e.getValue());
                continue;
            }
            if (primaryTable.startsWith("estm") && table.startsWith("estm") && sharesSuffix(primaryTable, table)) {
                result.put(table, e.getValue());
                continue;
            }

            // Tabelas com mesmo prefixo de negócio (ex: estmpropcc e estmsoliccompracc ambas da feature)
            if (primaryPrefix != null && table.startsWith(primaryPrefix)) {
                result.put(table, e.getValue());
            }
        }

        return result;
    }

    /**
     * Elege a tabela principal da tela entre as candidatas.
     * Prioridade: estm* (master de negócio) > estd* > demais (bdo*, mov*, etc.)
     * Se nenhuma estm/estd, usa a primeira candidata.
     */
    private String electPrimaryTable(Set<String> candidates) {
        // Prioridade 1: estm* (master)
        for (String t : candidates) {
            if (t.startsWith("estm")) return t;
        }
        // Prioridade 2: qualquer tabela que não seja bdo/mov/log (auxiliares)
        for (String t : candidates) {
            if (!t.startsWith("bdo") && !t.startsWith("mov") && !t.startsWith("log")) return t;
        }
        // Fallback: primeira
        return candidates.iterator().next();
    }

    /** Extrai o prefixo de módulo da tabela: estmpropcc → estm, bdoprpre → bdo */
    private String extractTablePrefix(String tableName) {
        if (tableName.startsWith("estm") || tableName.startsWith("estd")) return tableName.substring(0, 4);
        if (tableName.matches("^(cad|bdo|mov|log|fis|fin|rec|pag|ctb|vnd|cmp).*")) return tableName.substring(0, 3);
        return null;
    }

    /** Verifica se duas tabelas compartilham sufixo de negócio: estmpropcc e estmsoliccompracc → false */
    private boolean sharesSuffix(String t1, String t2) {
        String s1 = t1.length() > 4 ? t1.substring(4) : t1;
        String s2 = t2.length() > 4 ? t2.substring(4) : t2;
        return s1.equals(s2);
    }

    /** Fix 3: Encontra a tabela de negócio no FROM (a primeira que NÃO é infraestrutura) */
    private String findBusinessTable(List<String> tablesUsed, String sql) {
        // Tenta encontrar tabela não-infra na lista
        for (String t : tablesUsed) {
            String lower = t.toLowerCase();
            if (!INFRA_TABLE_PREFIXES.contains(lower) &&
                !(lower.startsWith("cad") && !lower.contains("prop") && !lower.contains("solic"))) {
                return lower;
            }
        }
        // Se todas são infra, retorna a primeira
        return tablesUsed.isEmpty() ? null : tablesUsed.get(0).toLowerCase();
    }

    /** Fix 2: Verifica se a tabela só aparece dentro de parênteses (subquery) */
    private boolean isOnlyInSubquery(String tableName, String sql) {
        if (sql == null) return false;
        String lower = sql.toLowerCase();
        // Remove conteúdo entre parênteses (simplificado: 1 nível)
        String withoutParens = lower.replaceAll("\\([^)]*\\)", " __SUB__ ");
        // Verifica se a tabela aparece no SQL sem parênteses
        return !withoutParens.contains(" " + tableName + " ") &&
               !withoutParens.contains(" " + tableName + ",") &&
               !withoutParens.startsWith(tableName + " ");
    }

    /** Infere nome de entity a partir do nome da tabela: estmpropcc → PropostaCCEntity */
    private String inferEntityFromTable(String tableName) {
        String clean = tableName;
        boolean isDetail = false;
        if (clean.matches("^estd.*")) { isDetail = true; clean = clean.substring(4); }
        else if (clean.matches("^estm.*")) { clean = clean.substring(4); }
        else if (clean.matches("^(cad|bdo|mov|log|fis|fin|rec|pag|ctb|vnd|cmp).*")) {
            clean = clean.substring(3);
        }
        // snake to PascalCase
        String pascal = "";
        for (String part : clean.split("_")) {
            if (!part.isEmpty()) pascal += part.substring(0, 1).toUpperCase() + part.substring(1);
        }
        if (pascal.isEmpty()) pascal = tableName;
        return (isDetail ? "Item" : "") + pascal + "Entity";
    }

    /** Encontra campos DFM para uma tabela específica */
    private List<DfmForm.DatasetField> findFieldsForTable(String tableName,
            Map<String, List<DfmForm.DatasetField>> byDataset, TargetPatterns tp) {
        // Se só tem 1 dataset, retorna ele
        if (byDataset.size() == 1) return byDataset.values().iterator().next();

        // Tenta match: tabela detail → dataset com "Produto"/"Item"/"Detalhe"
        boolean isDetail = tableName.matches("^estd.*") ||
                (tp != null && tp.getMasterDetailRelationships().containsKey(tableName));

        for (Map.Entry<String, List<DfmForm.DatasetField>> e : byDataset.entrySet()) {
            String dsLower = e.getKey().toLowerCase();
            boolean dsIsDetail = dsLower.contains("produto") || dsLower.contains("item") ||
                                 dsLower.contains("detalhe") || dsLower.contains("solic");
            // Filtrar datasets de combo/filtro
            if (dsLower.contains("filtro") || dsLower.contains("combo") ||
                dsLower.contains("lookup") || dsLower.contains("display") ||
                dsLower.contains("selecao") || e.getValue().size() <= 2) {
                continue;
            }
            if (isDetail == dsIsDetail) return e.getValue();
        }

        // Fallback: primeiro dataset não-filtro
        for (Map.Entry<String, List<DfmForm.DatasetField>> e : byDataset.entrySet()) {
            if (e.getValue().size() > 2) return e.getValue();
        }
        return null;
    }

    /** Encontra tabela para entity consultando knownTables, masterDetail e SQLs */
    private String findTableForEntity(String entityName, List<String> sqlTables, int fallbackIdx) {
        ProjectProfileStore store = ProjectProfileStore.getInstance();
        TargetPatterns tp = store.getPatterns();

        if (tp != null) {
            // 1. Busca exata no knownTables pelo entityName
            for (Map.Entry<String, TargetPatterns.TablePattern> e : tp.getKnownTables().entrySet()) {
                if (e.getValue().getEntity() != null) {
                    String eName = e.getValue().getEntity().replace("Entity", "");
                    if (eName.equalsIgnoreCase(entityName)) {
                        return e.getKey();
                    }
                }
            }

            // 2. Match parcial: "ManutencaoPedidoAutomatico" → procura entity que contenha "PedidoAutomatico"
            boolean isItem = entityName.toLowerCase().startsWith("item");
            for (Map.Entry<String, TargetPatterns.TablePattern> e : tp.getKnownTables().entrySet()) {
                if (e.getValue().getEntity() != null) {
                    String eName = e.getValue().getEntity().replace("Entity", "");
                    boolean eIsItem = eName.toLowerCase().startsWith("item");
                    // Match: ambos são item OU ambos são master
                    if (isItem == eIsItem) {
                        // Verifica se a tabela aparece nas SQLs extraídas
                        if (sqlTables.contains(e.getKey())) {
                            return e.getKey();
                        }
                    }
                }
            }
        }

        // 3. Fallback: usa a SQL na posição correspondente
        if (fallbackIdx < sqlTables.size()) {
            return sqlTables.get(fallbackIdx);
        }
        return null;
    }

    /** Infere nome da entity a partir do nome do dataset:
     *  cdsPedido → PedidoAutomatico (mantém o baseName)
     *  cdsProdutos → ItemPedidoAutomatico (prefixo Item)
     *  cdsFiltroSecao → FiltroSecao (usa o nome do dataset)
     */
    private String inferEntityName(String datasetName, String baseName) {
        if (datasetName == null) return baseName;
        // Remove prefixos cds/qry/dts/dsp
        String clean = datasetName.replaceAll("^(?i)(cds|qry|dts|dsp)", "");
        if (clean.isEmpty()) return baseName;

        // Se o dataset é o "master" (Pedido, Dados, etc.), usa o baseName
        String lower = clean.toLowerCase();
        if (lower.equals("pedido") || lower.equals("dados") || lower.equals("master") ||
            lower.contains("pedido") && !lower.contains("produto") && !lower.contains("item")) {
            return baseName;
        }
        // Se é "Produtos", "Itens", etc., prefixo Item
        if (lower.contains("produto") || lower.contains("iten") || lower.contains("detalhe")) {
            return "Item" + baseName;
        }
        // Se é "Filtro", "Log", etc., usa o nome do dataset
        return clean;
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TOOL 7: Generate Angular Component
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
class GenerateAngularComponent extends BaseTool {

    private final DfmFormParser dfmParser = new DfmFormParser();
    private final DelphiSourceParser sourceParser = new DelphiSourceParser();
    private final com.migration.mcp.generator.AngularCodeGenerator angularGenerator = new com.migration.mcp.generator.AngularCodeGenerator();

    @Override
    public McpServerFeatures.SyncToolSpecification getSpecification() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "generate_angular_component",
                "Converte um formulario Delphi (.dfm + .pas) em um modulo Angular completo no padrao " +
                "PrimeNG com Container/Grid/Filtros/Cadastro, Service com BehaviorSubject, HTTP service, " +
                "models TypeScript e modulos com lazy loading. Gera 17 arquivos prontos para uso.",
                """
                {
                  "type": "object",
                  "properties": {
                    "content": {"type": "string", "description": "Conteudo do arquivo .dfm"},
                    "file_path": {"type": "string", "description": "Caminho do arquivo .dfm"},
                    "pas_content": {"type": "string", "description": "Conteudo do arquivo .pas (para campos e regras)"},
                    "pas_file_path": {"type": "string", "description": "Caminho do arquivo .pas"}
                  }
                }
                """
        );
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> withLogging("generate_angular_component", args, () -> {
                // Parse DFM
                String dfmContent = args.containsKey("content") && !requireString(args, "content").isBlank() && requireString(args, "content").trim().length() >= 10
                        ? requireString(args, "content")
                        : AnalyzeDelphiUnitTool.readFileWithFallback(Path.of(requireString(args, "file_path")));
                DfmForm form = dfmParser.parse(dfmContent);
                log.info("  Form parseado: {} ({} componentes)", form.getFormName(), form.getComponents().size());

                // Parse PAS (opcional, para campos)
                DelphiClass dc = null;
                if (args.containsKey("pas_content") || args.containsKey("pas_file_path")) {
                    String pasContent = args.containsKey("pas_content") && !requireString(args, "pas_content").isBlank() && requireString(args, "pas_content").trim().length() >= 10
                            ? requireString(args, "pas_content")
                            : AnalyzeDelphiUnitTool.readFileWithFallback(Path.of(requireString(args, "pas_file_path")));
                    DelphiUnit unit = sourceParser.parse(pasContent, "input.pas");
                    if (!unit.getClasses().isEmpty()) {
                        dc = unit.getClasses().get(0);
                    }
                    log.info("  PAS parseado: {} classes", unit.getClasses().size());
                }

                // Extrai AnalysisContext se PAS disponível
                AnalysisContext analysisCtx = null;
                if (dc != null) {
                    String fullPasContent = args.containsKey("pas_content") && !requireString(args, "pas_content").isBlank() && requireString(args, "pas_content").trim().length() >= 10
                            ? requireString(args, "pas_content")
                            : (args.containsKey("pas_file_path") ? AnalyzeDelphiUnitTool.readFileWithFallback(Path.of(requireString(args, "pas_file_path"))) : null);
                    if (fullPasContent != null) {
                        analysisCtx = new AnalysisContext();
                        analysisCtx.setFormInitialization(sourceParser.extractFormInitialization(fullPasContent));
                        analysisCtx.setFieldValidationRules(sourceParser.extractFieldValidationRules(fullPasContent));
                        analysisCtx.setCalcCellColorRules(sourceParser.extractCalcCellColorRules(fullPasContent));
                        analysisCtx.setDependentFieldRules(sourceParser.extractDependentFieldRules(fullPasContent));
                    }
                }

                // Gera modulo Angular completo
                log.info("  Gerando módulo Angular...");
                Map<String, String> generatedFiles = analysisCtx != null
                        ? angularGenerator.generateModule(form, dc, null, analysisCtx)
                        : angularGenerator.generateModule(form, dc);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("formName", form.getFormName());
                result.put("angularComponentName", form.getAngularComponentName());
                result.put("suggestedRoute", form.getSuggestedRoute());
                result.put("totalFilesGenerated", generatedFiles.size());
                result.put("componentMapping", form.getComponents().stream()
                        .map(c -> Map.of(
                                "delphi", c.getName() + " : " + c.getDelphiType(),
                                "angular", c.getAngularEquivalent(),
                                "events", c.getEvents()
                        ))
                        .collect(Collectors.toList()));
                result.put("files", generatedFiles);
                log.info("  {} arquivos Angular gerados", generatedFiles.size());
                return success(result);
        }));
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TOOL 8: Analyze Full Project
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
class AnalyzeProjectTool extends BaseTool {

    private final DelphiSourceParser sourceParser = new DelphiSourceParser();
    private final DfmFormParser dfmParser = new DfmFormParser();

    @Override
    public McpServerFeatures.SyncToolSpecification getSpecification() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "analyze_delphi_project",
                "Analisa um projeto Delphi inteiro varrendo um diretório recursivamente. " +
                "Encontra todos os arquivos .pas e .dfm, analisa cada um e retorna um inventário " +
                "completo do projeto com estatísticas de complexidade e dependências.",
                """
                {
                  "type": "object",
                  "properties": {
                    "project_dir": {"type": "string", "description": "Diretório raiz do projeto Delphi"},
                    "include_source": {"type": "boolean", "description": "Incluir código-fonte no resultado (padrão: false)"},
                    "max_files": {"type": "integer", "description": "Limite de arquivos a processar (padrão: 200)"}
                  },
                  "required": ["project_dir"]
                }
                """
        );
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> withLogging("analyze_delphi_project", args, () -> {
                String projectDir = requireString(args, "project_dir");
                boolean includeSource = Boolean.parseBoolean(optionalString(args, "include_source", "false"));
                int maxFiles = Integer.parseInt(optionalString(args, "max_files", "200"));

                Path root = Path.of(projectDir);
                if (!Files.exists(root)) return error("Diretório não encontrado: " + projectDir);

                List<Map<String, Object>> pasResults = new ArrayList<>();
                List<Map<String, Object>> dfmResults = new ArrayList<>();
                int[] counts = {0};
                int[] errors = {0};

                // Collect .pas files first to know total
                log.info("  Coletando arquivos .pas...");
                List<Path> pasFiles = Files.walk(root)
                        .filter(p -> p.toString().toLowerCase().endsWith(".pas"))
                        .limit(maxFiles)
                        .collect(java.util.stream.Collectors.toList());
                log.info("  {} arquivos .pas encontrados (max={})", pasFiles.size(), maxFiles);

                int pasTotal = pasFiles.size();
                int logInterval = Math.max(1, pasTotal / 10);
                for (int i = 0; i < pasTotal; i++) {
                    Path p = pasFiles.get(i);
                    try {
                        String content = AnalyzeDelphiUnitTool.readFileWithFallback(p);
                        DelphiUnit unit = sourceParser.parse(content, p.toString());
                        if (!includeSource) unit.setRawContent(null);

                        Map<String, Object> summary = new LinkedHashMap<>();
                        summary.put("file", root.relativize(p).toString());
                        summary.put("unitName", unit.getUnitName());
                        summary.put("unitType", unit.getUnitType());
                        summary.put("classes", unit.getClasses().size());
                        summary.put("methods", unit.getClasses().stream().mapToInt(c -> c.getMethods().size()).sum());
                        summary.put("sqlQueries", unit.getSqlQueries().size());
                        summary.put("businessRules", unit.getBusinessRules().size());
                        summary.put("uses", unit.getUses().size());
                        if (includeSource) summary.put("unit", unit);
                        pasResults.add(summary);
                        counts[0]++;
                    } catch (Exception e) {
                        errors[0]++;
                        log.warn("Erro processando {}: {}", p, e.getMessage());
                    }
                    if ((i + 1) % logInterval == 0 || i == pasTotal - 1) {
                        log.info("  .pas: {}/{} ({}%) — {} OK, {} erros",
                                i + 1, pasTotal, (int)((i + 1) * 100.0 / pasTotal), counts[0], errors[0]);
                    }
                }

                // Walk .dfm files
                log.info("  Coletando arquivos .dfm...");
                List<Path> dfmFiles = Files.walk(root)
                        .filter(p -> p.toString().toLowerCase().endsWith(".dfm"))
                        .limit(maxFiles)
                        .collect(java.util.stream.Collectors.toList());
                log.info("  {} arquivos .dfm encontrados", dfmFiles.size());

                int dfmTotal = dfmFiles.size();
                for (int i = 0; i < dfmTotal; i++) {
                    Path p = dfmFiles.get(i);
                    try {
                        String content = AnalyzeDelphiUnitTool.readFileWithFallback(p);
                        DfmForm form = dfmParser.parse(content);
                        Map<String, Object> summary = new LinkedHashMap<>();
                        summary.put("file", root.relativize(p).toString());
                        summary.put("formName", form.getFormName());
                        summary.put("formType", form.getFormType());
                        summary.put("caption", form.getCaption());
                        summary.put("components", form.getComponents().size());
                        summary.put("angularComponentName", form.getAngularComponentName());
                        summary.put("suggestedRoute", form.getSuggestedRoute());
                        dfmResults.add(summary);
                    } catch (Exception e) {
                        log.warn("Erro processando DFM {}: {}", p, e.getMessage());
                    }
                    if ((i + 1) % Math.max(1, dfmTotal / 5) == 0 || i == dfmTotal - 1) {
                        log.info("  .dfm: {}/{} ({}%)", i + 1, dfmTotal, (int)((i + 1) * 100.0 / dfmTotal));
                    }
                }

                // Agrega estatísticas
                int totalClasses = pasResults.stream().mapToInt(m -> (int) m.get("classes")).sum();
                int totalSql = pasResults.stream().mapToInt(m -> (int) m.get("sqlQueries")).sum();
                int totalRules = pasResults.stream().mapToInt(m -> (int) m.get("businessRules")).sum();

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("projectDir", projectDir);
                result.put("statistics", Map.of(
                        "totalPasFiles", pasResults.size(),
                        "totalDfmFiles", dfmResults.size(),
                        "totalClasses", totalClasses,
                        "totalSqlQueries", totalSql,
                        "totalBusinessRules", totalRules,
                        "estimatedComplexity", totalClasses + totalSql + totalRules < 100 ? "medium" :
                                totalClasses + totalSql + totalRules < 300 ? "high" : "very_high"
                ));
                result.put("units", pasResults);
                result.put("forms", dfmResults);

                log.info("  Análise completa: {} .pas + {} .dfm | {} classes, {} queries, {} regras",
                        pasResults.size(), dfmResults.size(), totalClasses, totalSql, totalRules);
                return success(result);
        }));
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TOOL 9: Generate Full Module (Java + Angular + Plan em uma chamada)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
class GenerateFullModuleTool extends BaseTool {

    private final DelphiSourceParser sourceParser = new DelphiSourceParser();
    private final DfmFormParser dfmParser = new DfmFormParser();
    private final com.migration.mcp.generator.JavaCodeGenerator javaGenerator = new com.migration.mcp.generator.JavaCodeGenerator();
    private final com.migration.mcp.generator.AngularCodeGenerator angularGenerator = new com.migration.mcp.generator.AngularCodeGenerator();

    @Override
    public McpServerFeatures.SyncToolSpecification getSpecification() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "generate_full_module",
                "Gera o modulo completo de migracao a partir de um par .pas + .dfm: " +
                "7 arquivos Java (Entity, Repository, Service, Resource, DTO, PesquisaDTO, GridVo) + " +
                "17 arquivos Angular (Module, Routing, Container, Grid, Filtros, Cadastro, Service, HttpService, Models). " +
                "Opcionalmente salva os arquivos em disco com output_dir.",
                """
                {
                  "type": "object",
                  "properties": {
                    "pas_file_path": {"type": "string", "description": "Caminho do arquivo .pas"},
                    "dfm_file_path": {"type": "string", "description": "Caminho do arquivo .dfm (inferido do .pas se omitido)"},
                    "package_name": {"type": "string", "description": "Package Java base (ex: logus.corporativo.api.modulo)"},
                    "output_dir": {"type": "string", "description": "Diretorio para salvar os arquivos gerados (opcional)"}
                  },
                  "required": ["pas_file_path", "package_name"]
                }
                """
        );
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> withLogging("generate_full_module", args, () -> {
                String pasPath = requireString(args, "pas_file_path");
                String packageName = requireString(args, "package_name");
                String outputDir = optionalString(args, "output_dir", null);

                // Resolve DFM path
                String dfmPath = args.containsKey("dfm_file_path")
                        ? requireString(args, "dfm_file_path")
                        : pasPath.replaceAll("(?i)\\.pas$", ".dfm");

                // Parse PAS
                String pasContent = AnalyzeDelphiUnitTool.readFileWithFallback(Path.of(pasPath));
                DelphiUnit unit = sourceParser.parse(pasContent, pasPath);
                log.info("  PAS: {} classes, {} métodos, {} queries, {} regras",
                        unit.getClasses().size(),
                        unit.getClasses().stream().mapToInt(c -> c.getMethods().size()).sum(),
                        unit.getSqlQueries().size(), unit.getBusinessRules().size());

                // Parse DFM
                DfmForm form = null;
                List<DfmForm.DatasetField> dfmFields = null;
                if (Files.exists(Path.of(dfmPath))) {
                    String dfmContent = AnalyzeDelphiUnitTool.readFileWithFallback(Path.of(dfmPath));
                    form = dfmParser.parse(dfmContent);
                    dfmFields = form.getDatasetFields();
                    log.info("  DFM: {} componentes, {} campos dataset, {} colunas grid",
                            form.getComponents().size(), dfmFields.size(), form.getGridColumns().size());
                } else {
                    log.warn("  DFM não encontrado: {}", dfmPath);
                }

                Map<String, Object> result = new LinkedHashMap<>();

                // Extrai tabela principal da SQL (usada por Java e Angular)
                String mainTable = null;
                for (SqlQuery sq : unit.getSqlQueries()) {
                    if ("SELECT".equals(sq.getQueryType()) && sq.getTablesUsed() != null && !sq.getTablesUsed().isEmpty()) {
                        mainTable = sq.getTablesUsed().get(0).toLowerCase();
                        break;
                    }
                }

                // Resolve entityClassName consistente via knownTables
                String entityClassName = null;
                if (mainTable != null) {
                    TargetPatterns tp = ProjectProfileStore.getInstance().getPatterns();
                    if (tp != null && tp.getKnownTables().containsKey(mainTable)) {
                        entityClassName = tp.getKnownTables().get(mainTable).getEntity();
                        if (entityClassName != null) entityClassName = entityClassName.replace("Entity", "");
                    }
                }

                // ── Extrair AnalysisContext para os geradores ──
                AnalysisContext analysisCtx = new AnalysisContext();
                analysisCtx.setFormInitialization(sourceParser.extractFormInitialization(pasContent));
                analysisCtx.setButtonStateRules(sourceParser.extractButtonStateRules(pasContent));
                analysisCtx.setFieldValidationRules(sourceParser.extractFieldValidationRules(pasContent));
                analysisCtx.setCalcCellColorRules(sourceParser.extractCalcCellColorRules(pasContent));
                analysisCtx.setDependentFieldRules(sourceParser.extractDependentFieldRules(pasContent));
                analysisCtx.setDatasetEventRules(sourceParser.extractDatasetEventRules(pasContent));
                analysisCtx.setTransactionBoundaries(sourceParser.extractTransactionBoundaries(pasContent));
                analysisCtx.setCrossFormDataFlow(sourceParser.extractCrossFormDataFlow(pasContent));

                // ── Java (7 arquivos) ──
                Map<String, String> javaFiles = new LinkedHashMap<>();
                for (DelphiClass dc : unit.getClasses()) {
                    String baseName = entityClassName != null ? entityClassName :
                        dc.getName().replaceAll("^T", "").replaceAll("^(?i)(frm|Frm)", "");
                    if (baseName.isEmpty()) baseName = dc.getName().replaceAll("^T", "");

                    javaFiles.put(baseName + "Entity.java", javaGenerator.generateEntity(dc, packageName, dfmFields, mainTable, entityClassName));
                    javaFiles.put(baseName + "Repository.java", javaGenerator.generateRepository(dc, packageName, mainTable, dfmFields, entityClassName));
                    javaFiles.put(baseName + "Service.java", javaGenerator.generateService(dc, packageName, unit.getSqlQueries(), unit.getBusinessRules(), analysisCtx));
                    javaFiles.put(baseName + "Resource.java", javaGenerator.generateController(dc, packageName));
                    javaFiles.put(baseName + "Dto.java", javaGenerator.generateDto(dc, packageName, dfmFields));
                    javaFiles.put("Pesquisa" + baseName + "Dto.java", javaGenerator.generatePesquisaDto(dc, packageName, dfmFields, mainTable, entityClassName));
                    javaFiles.put("Grid" + baseName + "Vo.java", javaGenerator.generateVo(dc, packageName, dfmFields, mainTable, entityClassName));
                }
                result.put("javaFiles", javaFiles);

                // ── Angular (17 arquivos) ──
                Map<String, String> angularFiles = new LinkedHashMap<>();
                if (form != null) {
                    DelphiClass dc = unit.getClasses().isEmpty() ? null : unit.getClasses().get(0);
                    angularFiles = angularGenerator.generateModule(form, dc, mainTable, analysisCtx);
                }
                result.put("angularFiles", angularFiles);

                // ── Resumo ──
                result.put("summary", Map.of(
                        "pasFile", pasPath,
                        "dfmFile", dfmPath,
                        "javaFilesCount", javaFiles.size(),
                        "angularFilesCount", angularFiles.size(),
                        "totalFilesGenerated", javaFiles.size() + angularFiles.size(),
                        "methods", unit.getClasses().stream().mapToInt(c -> c.getMethods().size()).sum(),
                        "sqlQueries", unit.getSqlQueries().size(),
                        "businessRules", unit.getBusinessRules().size(),
                        "dfmComponents", form != null ? form.getComponents().size() : 0,
                        "gridColumns", form != null ? form.getGridColumns().size() : 0
                ));

                // ── Item 10: Salvar em disco ──
                if (outputDir != null && !outputDir.isBlank()) {
                    Path outPath = Path.of(outputDir);
                    int saved = 0;

                    // Java
                    Path javaPath = outPath.resolve("java");
                    for (Map.Entry<String, String> e : javaFiles.entrySet()) {
                        Path filePath = javaPath.resolve(e.getKey());
                        Files.createDirectories(filePath.getParent());
                        Files.writeString(filePath, e.getValue(), StandardCharsets.UTF_8);
                        saved++;
                    }

                    // Angular
                    Path angularPath = outPath.resolve("angular");
                    for (Map.Entry<String, String> e : angularFiles.entrySet()) {
                        Path filePath = angularPath.resolve(e.getKey());
                        Files.createDirectories(filePath.getParent());
                        Files.writeString(filePath, e.getValue(), StandardCharsets.UTF_8);
                        saved++;
                    }

                    result.put("outputDir", outputDir);
                    result.put("filesSavedToDisk", saved);
                    log.info("  {} arquivos salvos em {}", saved, outputDir);
                }

                log.info("  Total: {} Java + {} Angular = {} arquivos", javaFiles.size(), angularFiles.size(),
                        javaFiles.size() + angularFiles.size());
                return success(result);
        }));
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TOOL: learn_database — Extrai metadados do banco Informix (SOMENTE LEITURA)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
class LearnDatabaseTool extends BaseTool {

    private final com.migration.mcp.parser.DatabaseLearner learner = new com.migration.mcp.parser.DatabaseLearner();

    @Override
    public McpServerFeatures.SyncToolSpecification getSpecification() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "learn_database",
                "Conecta ao banco Informix via JDBC e extrai metadados reais: tabelas, colunas, tipos, " +
                "PKs, FKs. Enriquece o entity-patterns.json com dados 100% confiáveis. " +
                "SOMENTE LEITURA — nunca executa INSERT/UPDATE/DELETE/DROP/ALTER.",
                """
                {
                  "type": "object",
                  "properties": {
                    "jdbc_url": {"type": "string", "description": "Connection string JDBC (ex: jdbc:informix-sqli://host:port/db:INFORMIXSERVER=srv)"},
                    "username": {"type": "string", "description": "Usuário do banco"},
                    "password": {"type": "string", "description": "Senha do banco"},
                    "tables_filter": {"type": "array", "items": {"type": "string"}, "description": "Prefixos de tabelas a extrair (ex: ['est', 'cad']). Se omitido, extrai tudo."}
                  },
                  "required": ["jdbc_url", "username", "password"]
                }
                """
        );
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> withLogging("learn_database", args, () -> {
                String jdbcUrl = requireString(args, "jdbc_url");
                String username = requireString(args, "username");
                String password = requireString(args, "password");

                @SuppressWarnings("unchecked")
                List<String> tablesFilter = args.containsKey("tables_filter")
                        ? (List<String>) args.get("tables_filter")
                        : null;

                // SEGURANÇA: verifica que a URL não contém comandos perigosos
                String urlLower = jdbcUrl.toLowerCase();
                if (urlLower.contains("drop") || urlLower.contains("delete") || urlLower.contains("truncate")) {
                    return error("URL rejeitada por conter palavras perigosas.");
                }

                TargetPatterns patterns = learner.learn(jdbcUrl, username, password, tablesFilter);

                // Persiste o resultado
                ProjectProfileStore store = ProjectProfileStore.getInstance();
                // Merge com patterns existentes (preserva columnNameExpansions e knownEnums manuais)
                TargetPatterns existing = store.getPatterns();
                if (existing != null) {
                    // Preserva expansões manuais (curadas sobrescrevem inferidas)
                    patterns.getColumnNameExpansions().putAll(existing.getColumnNameExpansions());
                    // Preserva enums manuais (banco não tem essa info)
                    patterns.getKnownEnums().putAll(existing.getKnownEnums());
                    // Preserva stringForeignKeys manuais
                    patterns.setStringForeignKeys(existing.getStringForeignKeys());
                    // FKs curadas (do JSON existente) sobrescrevem as inferidas pelo banco
                    // Banco infere nomes genéricos (CadfilEntity), JSON tem nomes reais (FilialEntity)
                    patterns.getKnownForeignKeys().putAll(existing.getKnownForeignKeys());
                }

                // Salva em disco
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                        .enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
                Path patternsFile = Path.of(System.getProperty("user.home", "C:\\Users\\Usuario"), ".delphi-mcp", "entity-patterns.json");
                Files.createDirectories(patternsFile.getParent());
                mapper.writeValue(patternsFile.toFile(), patterns);
                log.info("Patterns salvos em {}", patternsFile);

                // Recarrega
                store.loadPatterns(null);

                return success(Map.of(
                        "status", "Metadados do banco extraídos com sucesso",
                        "tablesExtracted", patterns.getKnownTables().size(),
                        "foreignKeysExtracted", patterns.getKnownForeignKeys().size(),
                        "masterDetailRelationships", patterns.getMasterDetailRelationships().size(),
                        "savedTo", patternsFile.toString()
                ));
        }));
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TOOL: load_target_patterns — Carrega entity-patterns.json
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
class LoadTargetPatternsTool extends BaseTool {
    @Override
    public McpServerFeatures.SyncToolSpecification getSpecification() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "load_target_patterns",
                "Carrega entity-patterns.json com regras do projeto alvo: expansão de abreviações, " +
                "FKs conhecidas, enums, tabelas e relacionamentos master-detail. " +
                "Se não informar file_path, carrega de ~/.delphi-mcp/entity-patterns.json automaticamente.",
                """
                {
                  "type": "object",
                  "properties": {
                    "file_path": {"type": "string", "description": "Caminho do entity-patterns.json (opcional)"}
                  }
                }
                """
        );
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> withLogging("load_target_patterns", args, () -> {
                String filePath = optionalString(args, "file_path", null);
                ProjectProfileStore store = ProjectProfileStore.getInstance();
                store.loadPatterns(filePath);
                TargetPatterns tp = store.getPatterns();
                return success(Map.of(
                        "status", "Patterns carregados com sucesso",
                        "columnNameExpansions", tp.getColumnNameExpansions().size(),
                        "knownForeignKeys", tp.getKnownForeignKeys().size(),
                        "knownEnums", tp.getKnownEnums().size(),
                        "knownTables", tp.getKnownTables().size(),
                        "masterDetailRelationships", tp.getMasterDetailRelationships().size()
                ));
        }));
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TOOL: get_usage_guide — Manual de uso para agentes de IA
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
class GetUsageGuideTool extends BaseTool {

    @Override
    public McpServerFeatures.SyncToolSpecification getSpecification() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "get_usage_guide",
                "Retorna o manual completo de uso do Delphi Migration MCP Server. " +
                "Chame esta tool PRIMEIRO para aprender como usar todas as outras tools corretamente. " +
                "Inclui: fluxo recomendado, exemplos de chamadas, padrões do projeto alvo (Logus ERP), " +
                "e dicas para gerar código de qualidade.",
                """
                {
                  "type": "object",
                  "properties": {
                    "section": {"type": "string", "description": "Seção específica: 'quickstart', 'tools', 'patterns', 'examples', 'all' (padrão: all)"}
                  }
                }
                """
        );
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> withLogging("get_usage_guide", args, () -> {
                String section = optionalString(args, "section", "all");
                StringBuilder guide = new StringBuilder();

                if (section.equals("all") || section.equals("quickstart")) {
                    guide.append(QUICKSTART);
                }
                if (section.equals("all") || section.equals("tools")) {
                    guide.append(TOOLS_REFERENCE);
                }
                if (section.equals("all") || section.equals("patterns")) {
                    guide.append(PATTERNS);
                }
                if (section.equals("all") || section.equals("examples")) {
                    guide.append(EXAMPLES);
                }

                // Adiciona info do perfil aprendido se disponível
                ProjectProfileStore store = ProjectProfileStore.getInstance();
                if (store.hasProfile()) {
                    ProjectProfile p = store.get();
                    guide.append("\n## Perfil Aprendido Atual\n\n");
                    guide.append("- **Projeto:** ").append(p.getProjectName()).append("\n");
                    guide.append("- **Arquivos:** ").append(p.getTotalFilesScanned()).append("\n");
                    guide.append("- **Delphi:** ").append(p.getDetectedDelphiVersion()).append("\n");
                    guide.append("- **BD:** ").append(p.getDbTechnology()).append(" / ").append(p.getDbVendor()).append("\n");
                    guide.append("- **Prefixos:** form=").append(p.getNaming().getFormPrefix())
                          .append(", unit=").append(p.getNaming().getUnitPrefix())
                          .append(", query=").append(p.getNaming().getQueryPrefix()).append("\n");
                    guide.append("\n> Perfil já carregado. Não precisa rodar learn_repository novamente.\n");
                } else {
                    guide.append("\n## ⚠️ Nenhum perfil aprendido\n\n");
                    guide.append("Execute `learn_repository` primeiro com o caminho do projeto Delphi.\n");
                }

                return success(Map.of("guide", guide.toString()));
        }));
    }

    // ── Conteúdo do guia ──────────────────────────────────────────────────────

    private static final String QUICKSTART = """
## Quickstart — Fluxo Recomendado

### 1. Aprender o repositório (uma vez)
```
learn_repository(repository_path: "C:\\caminho\\do\\projeto-delphi")
```
Varre todos os .pas e .dfm e aprende: prefixos, BD, módulos, SQL, componentes.
O perfil é persistido em disco — sobrevive a reinícios.

### 2. Aprender o banco de dados (uma vez)
```
learn_database(
  jdbc_url: "jdbc:informix-sqli://192.168.0.231:9088/bd_desenv_m:INFORMIXSERVER=ol_saturno",
  username: "informix",
  password: "informix",
  tables_filter: ["est", "cad", "bdo", "mov", "log", "rec", "pag", "fis", "fin", "ctb", "vnd", "cmp"]
)
```
Extrai metadados reais do Informix: 1549 tabelas com **colunas** (nome, tipo, javaType, nullable),
PKs, FKs e master-detail. Salva em `~/.delphi-mcp/entity-patterns.json`. Roda em ~9 segundos.
**Só precisa rodar de novo se o schema do banco mudar.**

Os metadados do banco são usados para:
- **Fallback de campos**: Quando .pas/.dfm não têm campos, usa colunas reais do banco
- **Filtro de campos fantasma**: Remove campos do DFM que não existem na tabela real
- **Tipos corretos**: Corrige tipos "chutados" com o tipo real (CHAR→String, INTEGER→Integer)
- **FKs do banco**: Descobre @ManyToOne automaticamente via constraints do banco
- **nullable**: Gera `@Column(nullable = false)` com dados reais

### 3. Migrar uma tela completa (Java + Angular)
```
generate_full_module(
  pas_file_path: "C:\\projeto\\f_MinhaTela.pas",
  package_name: "logus.corporativo.api.meumodulo",
  output_dir: "C:\\output"    // opcional: salva em disco
)
```
Gera **24 arquivos** de uma vez:
- 7 Java: Entity, Repository (com JPQL), Service, Resource, DTO, PesquisaDTO, GridVo
- 17 Angular: Module, Routing, Container, Grid, Filtros, Cadastro, Services, Models

### 4. Gerar só partes específicas
```
generate_java_class(
  file_path: "C:\\projeto\\f_MinhaTela.pas",
  dfm_file_path: "C:\\projeto\\f_MinhaTela.dfm",
  package_name: "logus.corporativo.api.meumodulo",
  generate: ["entity", "repository", "vo", "dto"]
)
```
**IMPORTANTE:** Para gerar Repository + GridVo + PesquisaDto consistentes, incluir
`"entity"` no array `generate`. O Repository precisa dos campos da Entity para construir
o `SELECT NEW Vo(...)` JPQL. Se gerar apenas `["repository"]` sem `"entity"`, os campos
ficam vazios.

Os nomes dos arquivos gerados são **derivados da tabela do banco** (via `knownTables`),
não do nome do form Delphi. Ex: `f_ManutencaoPedidoAutomatico.pas` → tabela `estmpedautomatico`
→ `PedautomaticoEntity`, `PedautomaticoRepository`, `GridPedautomaticoVo`.

### 5. Analisar antes de migrar (opcional)
```
analyze_delphi_unit(file_path: "C:\\projeto\\f_Tela.pas")
analyze_dfm_form(file_path: "C:\\projeto\\f_Tela.dfm")
extract_sql_queries(file_path: "C:\\projeto\\f_Tela.pas")
extract_business_rules(file_path: "C:\\projeto\\f_Tela.pas")
detect_inconsistencies(file_path: "C:\\projeto\\f_Tela.pas")
```

### 6. Gerar plano de migração
```
generate_migration_plan(
  project_name: "Meu Módulo",
  pas_files: ["f_Tela1.pas", "f_Tela2.pas"],
  dfm_files: ["f_Tela1.dfm", "f_Tela2.dfm"],
  output_format: "markdown"
)
```

""";

    private static final String TOOLS_REFERENCE = """
## Referência de Tools (16 tools)

### Aprendizado e Configuração
| Tool | Quando usar |
|------|-------------|
| `learn_repository` | **Uma vez** no início. Passe o caminho raiz do projeto Delphi. |
| `learn_database` | **Uma vez**. Extrai tabelas, colunas, PKs, FKs do Informix. Persistido em disco (~9 seg). |
| `get_learned_profile` | Para ver o que foi aprendido (BD, prefixos, módulos). |
| `clear_learned_profile` | Para trocar de projeto. |
| `load_target_patterns` | Recarrega entity-patterns.json (após editar sem reiniciar MCP). |
| `get_usage_guide` | Este manual. Chame para aprender a usar o MCP. |

### Análise
| Tool | Input | Output |
|------|-------|--------|
| `analyze_delphi_unit` | .pas (file_path) | Classes, métodos, SQL, regras, dependências (calledForms) |
| `analyze_dfm_form` | .dfm (file_path) | Componentes mapeados PrimeNG, gridColumns, datasetFields |
| `extract_sql_queries` | .pas (file_path) | SQLs completas com JPQL sugerido e @Param tipados |
| `extract_business_rules` | .pas (file_path) | Validações, inicialização de tela (FormShow) e máquina de estados dos botões (AfterScroll + Click) |
| `analyze_delphi_project` | diretório | Inventário completo (todos .pas + .dfm) |
| `detect_inconsistencies` | .pas ou .dfm | Desvios do padrão do projeto (nomenclatura, SQL, componentes) |

### Geração
| Tool | Input | Output |
|------|-------|--------|
| `generate_full_module` | .pas + package | **24+ arquivos** Java + Angular + salva em disco |
| `generate_java_class` | .pas + package | N entities (master-detail) + Repository/Service/Resource/DTO/VO |
| `generate_angular_component` | .dfm + .pas | 17 arquivos Angular (Module/Container/Grid/Filtros/Cadastro) |
| `generate_migration_plan` | lista .pas + .dfm | Plano completo com fases, riscos e estimativas |

### Dicas importantes
- Todas as tools aceitam `file_path` — passe o caminho absoluto do arquivo.
- O campo `content` é opcional (use apenas se quiser enviar o conteúdo diretamente).
- `analyze_delphi_unit` tem `include_body: false` (padrão) para output compacto.
- **Sempre passe o arquivo .pas COMPLETO** (via `file_path` de preferência). O parser \
depende da estrutura hierárquica da unit Delphi (unit → interface → type → implementation) \
para identificar contexto de classes, métodos e componentes. Fragmentos isolados de código \
(ex: só o corpo de um FormShow sem a declaração da unit/class) serão ignorados ou terão \
resultados incompletos. O `content` é opcional — se omitido ou menor que 10 caracteres, \
o MCP lê automaticamente do `file_path`.

## extract_business_rules — 5 seções de output

A tool `extract_business_rules` retorna até 10 seções complementares numa única chamada:

### 1. rules — Validações e cálculos
- Validações com `TLogusMessage.Warning`, `raise Exception`, `ShowMessage`
- Cálculos complexos (atribuições numéricas)
- Cada regra inclui `suggestedJavaCode` e classifica como backend vs frontend

### 2. formInitialization — Lógica de abertura da tela (FormShow/FormCreate)
- **defaultValues**: campos inicializados com data atual, valores fixos
- **conditionalDefaults**: campos pré-selecionados e desabilitados condicionalmente
- **comboPreselections**: itens pré-selecionados em combos multi-select
- **autoLoads**: chamadas a Carregar*/Load* executadas ao abrir
- **initialStates**: campos desabilitados/ocultos na inicialização

### 3. buttonStateRules — Máquina de estados dos botões (AfterScroll + Click)
Combina informações de **quando** o botão está habilitado (AfterScroll) com **o que** \
ele faz ao ser clicado (Click handler):
- **enableConditions**: condições do `EnableComponent` no AfterScroll (suporta multi-linha)
- **confirmMessage**: texto do `TLogusMessage.Confirm` com template `{campo}` para variáveis
- **action**: método de negócio chamado (ex: `TPedidoAutomatico.Cancelar`)
- **actionType**: classificação — `business_method`, `navigation`, `report`, `search`, `crud`
- **requiresPermission**: referências a `Parametros.X.Y.Z` nas condições
- **fieldReferences**: campos do dataset usados nas condições (ex: `dat_conf`)
- **migrationHints**: sugestões concretas para Angular (ConfirmationService, [disabled], Router)

### 4. fieldValidationRules — Validações estruturadas por campo (ValidacaoOk)
Extrai validações do método `ValidacaoOk` / `ValidacaoOkNovoPedido` estruturadas por campo:
- **required**: campo.Date = 0, campo.Text = EmptyStr, campo.KeyValue = Null
- **cross_field**: comparação entre campos (date range max 90 dias, date order)
- **pattern**: StrToInt no try/except → numeric only
- **length**: Length(campo.Text) < N ou > N
- Cada regra inclui `angularValidator` (ex: `Validators.required`) e `angularCode` para \
custom validators (ex: `maxDateRange(90)`)

### 5. calcCellColorRules — Colorização condicional do grid (CalcCellColors)
Extrai lógica de cores de células do `CalcCellColors`:
- **conditionField**: campo que determina a cor (ex: `flg_tp_pedido`)
- **colorMappings**: valor → cor CSS (ex: 1=green, 2=blue, 3=yellow, 4=orange)
- **label**: texto da legenda associado à cor (detectado por labels lblVerde, lblAzul)
- **angularCode**: método `getColorClass()` pronto para usar com `[ngClass]` no PrimeNG

### 6. dependentFieldRules — Cascata entre campos (Exit/Change events)
Detecta quando mudar um campo afeta outros (preencher, limpar, habilitar):
- **triggerField/event**: campo e evento que dispara (ex: `edtCodigoFornecedor` / `Exit`)
- **effects**: lista de campos afetados com ação (fill, disable) e source/fallback
- **validationMessage**: mensagem de erro se o valor é inválido
- Mapeia para `valueChanges.pipe(switchMap(...))` ou `(blur)` no Angular

### 7. datasetEventRules — Eventos de dataset (AfterInsert, CalcFields, etc)
- **AfterInsert/OnNewRecord** → defaults de novo registro (`Service.preencherEntidade()`)
- **CalcFields** → campos calculados (getter no Angular ou campo derivado no GridVo)
- **BeforeDelete** → guard / cascade delete
- **AfterPost** → hooks pós-operação

### 8. providerUpdateRules — Sequences e propagação de chaves (BeforeUpdateRecord)
- **sequences**: `Conexao.Next('tabela', 'campo')` → `@GeneratedValue` no Entity
- **keyPropagation**: FK master→detail propagada antes do save
- Mapeia para `@GeneratedValue` + `Service.salvar()` propaga FK nos itens

### 9. transactionBoundaries — Fronteiras de transação
- **method**: método com `StartTransaction/Commit/Rollback`
- **operations**: lista de operações dentro da transação (ApplyUpdates, ExecSQL, GravarLog)
- Mapeia para `@Transactional` no Spring Service

### 10. crossFormDataFlow — Fluxo de dados entre telas
- **targetForm/method**: qual tela é chamada e como (MakeShowModal, ShowModal)
- **paramsIn**: parâmetros passados com campo e tipo extraídos
- **expectedReturn**: retorno esperado (mrOk, mrCancel)
- **onSuccessAction**: ação executada após retorno bem-sucedido
- Mapeia para Router.navigate com queryParams ou Dialog PrimeNG com callback

Exemplo de uso:
```
extract_business_rules(file_path: "C:\\projeto\\f_MinhaTela.pas")
```

## Geradores consomem AnalysisContext

Os geradores `generate_full_module` e `generate_angular_component` extraem automaticamente \
todas as 10 seções do `extract_business_rules` e usam para enriquecer o código gerado:

### Angular — o que muda com o contexto
| Seção usada | Onde aplica | Resultado |
|---|---|---|
| `fieldValidationRules` | Filtros + Cadastro `buildFormGroup()` | `Validators.required`, `Validators.pattern`, `minLength`/`maxLength` |
| `formInitialization` | Filtros `buildFormGroup()` defaults | `dataEmissaoDe: [new Date()]`, combos com `[1, 2]` |
| `formInitialization` | Filtros `ngOnInit()` | `handlePesquisar()` só se houver autoLoad no FormShow |
| `calcCellColorRules` | Grid component | Método `getColorClass(value)` com switch/case pronto |

### Java — o que muda com o contexto
| Seção usada | Onde aplica | Resultado |
|---|---|---|
| `datasetEventRules` | Service `save()` | Comentários com defaults de novo registro (AfterInsert) |
| `transactionBoundaries` | Service `save()` | Comentários com operações da transação original |

Sem o contexto (ex: chamando `generate_angular_component` sem `pas_file_path`), \
o código é gerado no formato padrão — os campos ficam com `[null]` e sem validators.

## Entity Patterns (entity-patterns.json)

### O que é
Arquivo JSON em `~/.delphi-mcp/entity-patterns.json` com metadados do banco Informix
(via `learn_database`) + regras curadas do projeto. Carregado automaticamente ao iniciar.

### O que contém
| Seção | Entradas | Função |
|-------|----------|--------|
| `columnNameExpansions` | 91 | "cancel" → "cancelamento", "prev" → "previsao" |
| `knownForeignKeys` | 796 | "cdg_filial" → "FilialEntity" (@ManyToOne) |
| `stringForeignKeys` | 5 | "cdg_fornecedor" (CNPJ, não é FK de entity) |
| `knownEnums` | 65 | "flg_status_pedido" → SituacaoPedidoAutomaticoEnum + Converter |
| `knownTables` | 1549 | "estmpedautomatico" → {entity, pk, **columns**[]} |
| `masterDetailRelationships` | 15 | "estdpedautomatico" → master: estmpedautomatico |

Cada tabela em `knownTables` agora inclui **columns** com nome, tipo Informix, tipo Java,
nullable e length — dados reais do banco usados como fallback quando o .pas/.dfm não tem campos.

### Como funciona
- **Carregamento automático**: O MCP lê o JSON ao iniciar (`/mcp` para reiniciar).
- **Recarregar sem reiniciar**: `load_target_patterns()` ou `load_target_patterns(file_path: "caminho")`.
- **Prioridade**: patterns > heurística. Se o campo não está no JSON, usa a heurística padrão.
- **Editar o JSON**: Qualquer IA pode editar o JSON para adicionar novos padrões.
  Depois de editar, rodar `load_target_patterns()` para recarregar.

### Como a geração de Entity usa os patterns
1. **Nomes**: Consulta `columnNameExpansions` primeiro. Ex: "canc_pend_auto" → "cancelaPendenciaAutomatica"
2. **FKs**: Consulta `knownForeignKeys`. Ex: "cdg_filial" → `@ManyToOne FilialEntity filial`
3. **Enums**: Consulta `knownEnums`. Ex: "flg_status_pedido" → `@Convert(converter = SituacaoPedidoAutomaticoConverter.class)`
4. **Tabelas**: Consulta `knownTables` para `@Table(name)` e PK.
5. **Master-detail**: Consulta `masterDetailRelationships` para separar em N entities.

### Fluxo de melhoria iterativa
1. Rodar `generate_java_class` para uma tela
2. Comparar entity gerada vs entity real
3. Se encontrou gap, editar `~/.delphi-mcp/entity-patterns.json`
4. `load_target_patterns()` para recarregar
5. Testar novamente — o output melhora sem recompilar o MCP

""";

    private static final String PATTERNS = """
## Padrões do Projeto Alvo (Logus ERP)

### Backend — logus-corporativo-api
- **Java 8** / Spring Boot 2.1.2
- **Camadas:** Resource → Service → Repository → Entity
- **DTOs:** DTO (entrada/saída), VO (grid), PesquisaDto (filtros + LazyLoadDto), ResultDto (paginação)
- **@Autowired** field injection (não constructor injection)
- **Swagger 2:** @ApiOperation, @ApiParam (springfox, não springdoc)
- **Endpoints:** POST /pesquisar, POST /save, GET /getById/{id}, DELETE /delete/{id}
- **Error handling:** ValidationException → 409, Exception → 500
- **Entity:** javax.persistence (não jakarta), Integer como tipo de ID
- **Repository:** `JpaRepository` (sem JpaSpecificationExecutor), sem @Transactional, JPQL com `SELECT NEW Vo(...)`, `String FROM` constante, `Page<GridVo> pesquisar(Pageable)`, `List<GridVo> exportar()`, filtros `(:param IS NULL OR ...)`
- **GridVo:** `@LazyLoadField`, constructor all-args (para SELECT NEW), datas como String (formatarData), FKs como Integer
- **PesquisaDto:** `LazyLoadDto lazyDto`, FKs→Integer, datas→String, enums→Integer

### Frontend — logus-corporativo-web
- **Angular 10** / PrimeNG 11 (NÃO Angular Material)
- **Padrão:** Container / Grid / Filtros / Cadastro (4 sub-components por feature)
- **Service:** BehaviorSubject como state store (grid$, selecionado$, alterarEditar$)
- **HTTP Service:** separado em shared/services/http/
- **Shared components:** DataGrid, Filtro, Button, ComponenteBasico, BotoesExportar
- **ChangeDetectionStrategy.OnPush** no Container
- **Lazy loading** via RouterModule.forChild

### Mapeamento Delphi → PrimeNG
| Delphi | Angular (PrimeNG) |
|--------|-------------------|
| TLgCorporativoLookupComboEdit | `<p-dropdown [filter]="true" [showClear]="true">` |
| TJvDateEdit | `<p-calendar dateFormat="dd/mm/yy" [showIcon]="true">` |
| TwwDBGrid | `<p-table>` (shared DataGrid com paginator) |
| TLgBitBtn / PngBitBtn | `<button pButton>` com ícone PrimeNG |
| TGroupBox | `<p-fieldset legend="...">` |
| TEdit | `<input pInputText>` |
| TCheckBox | `<p-checkbox>` |
| TPageControl | `<p-tabView>` |
| TClientDataSet | BehaviorSubject no Service |

""";

    private static final String EXAMPLES = """
## Exemplos Práticos

### Migrar uma tela do zero
```
// 1. Se ainda não aprendeu o repositório:
learn_repository(repository_path: "C:\\des-jhs\\projeto\\delphi-corporativo")

// 2. Gerar tudo de uma vez:
generate_full_module(
  pas_file_path: "C:\\des-jhs\\projeto\\Financeiro\\f_ConciliacaoPagamentos.pas",
  package_name: "logus.corporativo.api.financeiro",
  output_dir: "C:\\output\\financeiro"
)
```

### Analisar complexidade antes de migrar
```
analyze_delphi_project(
  project_dir: "C:\\des-jhs\\projeto\\GerenciamentoArmazenagem",
  max_files: 50
)
```

### Extrair todas as SQLs de uma tela
```
extract_sql_queries(file_path: "C:\\des-jhs\\projeto\\f_pedido.pas")
```
Retorna: SQL completa + @Query nativeQuery + JPQL + @Param tipados + tabelas + JOINs

### Ver dependências entre telas
```
analyze_delphi_unit(file_path: "C:\\des-jhs\\projeto\\f_MonitorPedido.pas")
```
O campo `calledForms` mostra quais forms são chamados (MakeShowModal, Create, ShowModal).

### Gerar Entity + Repository + GridVo + PesquisaDto consistentes
```
generate_java_class(
  file_path: "C:\\des-jhs\\projeto\\f_Cliente.pas",
  dfm_file_path: "C:\\des-jhs\\projeto\\f_Cliente.dfm",
  package_name: "logus.corporativo.api.clientes",
  generate: ["entity", "repository", "vo", "dto"]
)
```
**Nota:** Incluir `"entity"` é importante — Repository usa os campos da Entity para
construir `SELECT NEW Vo(...)`, e o nome do arquivo (ex: `ClienteRepository.java`)
é derivado da tabela do banco via `knownTables`.

Inclua `dfm_file_path` para melhor extração de campos. Sem ele, usa apenas o .pas.

### Gerar só o Angular (sem Java)
```
generate_angular_component(
  file_path: "C:\\des-jhs\\projeto\\f_Cliente.dfm",
  pas_file_path: "C:\\des-jhs\\projeto\\f_Cliente.pas"
)
```

### Aprender o banco (uma vez, persistido em disco)
```
learn_database(
  jdbc_url: "jdbc:informix-sqli://192.168.0.231:9088/bd_desenv_m:INFORMIXSERVER=ol_saturno",
  username: "informix",
  password: "informix",
  tables_filter: ["est", "cad", "bdo", "mov", "log", "rec", "pag", "fis", "fin", "ctb", "vnd", "cmp"]
)
```
Extrai 1549 tabelas com colunas reais em ~9 segundos. O resultado fica em
`~/.delphi-mcp/entity-patterns.json` e é carregado automaticamente ao reiniciar o MCP.

## Limitações Conhecidas (revisão manual necessária)

### SQL Extraction
- **SQL dinâmico com if/else**: Quando métodos auxiliares adicionam SQL.Add condicionalmente
  (ex: AdicionarSQLProgramacaoPreco), os dois branches ficam concatenados na mesma query.
  O campo `conditionalBranch` anota que há variantes, mas o dev precisa separar manualmente.
- **"condição detectada" genérica**: ~15% dos conditionalBranch não conseguem extrair a condição
  real do `if`. Isso acontece quando: código comentado com `{== TICKET ==}` contém SELECTs antigos,
  ou o `if` está num método chamador fora do range analisado.
- **Queries de métodos auxiliares**: Métodos que só adicionam JOINs/WHEREs (sem SQL.Clear/Open)
  têm suas linhas agregadas na query do método chamador. A query fica completa mas mistura
  fragmentos de métodos diferentes.

### Tipos de Dados
- **TFloatField com prefixo cdg_/nmr_**: Mapeado como Integer (heurística). Correto na maioria
  dos casos, mas `nmr_docto` (texto concatenado "NF-Serie") é exceção — TStringField tem
  prioridade e resolve esses casos. Se um campo vier com tipo errado, verifique o tipo
  original no DFM (TStringField vs TFloatField vs TIntegerField).

### Regras de Negócio
- **TLogusMessage.Confirm → frontend**: Classificado corretamente como FRONTEND, mas a mensagem
  extraída pode estar incompleta (cortada na primeira aspa simples).
- **Cálculos**: Apenas linhas isoladas de atribuição são detectadas. Loops com cálculos
  acumulados (ex: soma de 6 meses) não são agrupados num bloco lógico.

### Angular Gerado
- **analyze_dfm_form**: Gera template inline genérico (p-table, p-toolbar). Para código no
  padrão Logus (app-data-grid, app-filtro, app-button), use `generate_angular_component`
  que gera 17 arquivos no padrão Container/Grid/Filtros/Cadastro.
- **Filtros do DFM**: Labels associados a campos são detectados por heurística de nome
  (lblFilial → lucFilial). Se o label não casar, o campo aparece sem label no HTML.

### Geral
- **Código gerado é ponto de partida**: Sempre requer revisão manual. Entidades precisam
  de ajuste de @Column, Services precisam da lógica real, e o Angular precisa de integração
  com o módulo existente (routing, menu, permissões).

""";
}
