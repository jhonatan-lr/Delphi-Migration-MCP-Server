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
                  "required": ["content"]
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
            if (content != null && !content.isBlank()) return content;
        }
        String path = requireString(args, "file_path");
        return readFileWithFallback(Path.of(path));
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
                String content = args.containsKey("content") && !requireString(args, "content").isBlank()
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
                String content = args.containsKey("content") && !requireString(args, "content").isBlank()
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
                "cálculos complexos, verificações de consistência. Para cada regra, fornece estratégia de " +
                "migração e código Java sugerido.",
                buildInputSchema(
                        "content", "string", "Conteúdo do código Delphi (.pas)",
                        "file_path", "string", "Caminho para o arquivo .pas"
                )
        );
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> withLogging("extract_business_rules", args, () -> {
                String content = args.containsKey("content") && !requireString(args, "content").isBlank()
                        ? requireString(args, "content")
                        : AnalyzeDelphiUnitTool.readFileWithFallback(Path.of(requireString(args, "file_path")));
                List<BusinessRule> rules = parser.extractBusinessRules(content);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("totalFound", rules.size());
                result.put("byType", rules.stream().collect(Collectors.groupingBy(BusinessRule::getRuleType, Collectors.counting())));
                result.put("rules", rules);
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
                String content = args.containsKey("content") && !requireString(args, "content").isBlank()
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
                        generatedFiles.put(cleanBase + "Entity.java", generator.generateEntity(dc, packageName, finalDfmFields));
                    }
                    if (generate.contains("repository")) {
                        generatedFiles.put(cleanBase + "Repository.java", generator.generateRepository(dc, packageName));
                    }
                    if (generate.contains("service")) {
                        generatedFiles.put(cleanBase + "Service.java", generator.generateService(dc, packageName, unit.getSqlQueries(), unit.getBusinessRules()));
                    }
                    if (generate.contains("controller") || generate.contains("resource")) {
                        generatedFiles.put(cleanBase + "Resource.java", generator.generateController(dc, packageName));
                    }
                    if (generate.contains("dto")) {
                        generatedFiles.put(cleanBase + "Dto.java", generator.generateDto(dc, packageName, finalDfmFields));
                        generatedFiles.put("Pesquisa" + cleanBase + "Dto.java", generator.generatePesquisaDto(dc, packageName, finalDfmFields));
                    }
                    if (generate.contains("vo")) {
                        generatedFiles.put(cleanBase + "GridVo.java", generator.generateVo(dc, packageName, finalDfmFields));
                    }
                }

                log.info("  {} arquivos Java gerados", generatedFiles.size());
                result.put("generatedFiles", generatedFiles);
                return success(result);
        }));
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
                String dfmContent = args.containsKey("content") && !requireString(args, "content").isBlank()
                        ? requireString(args, "content")
                        : AnalyzeDelphiUnitTool.readFileWithFallback(Path.of(requireString(args, "file_path")));
                DfmForm form = dfmParser.parse(dfmContent);
                log.info("  Form parseado: {} ({} componentes)", form.getFormName(), form.getComponents().size());

                // Parse PAS (opcional, para campos)
                DelphiClass dc = null;
                if (args.containsKey("pas_content") || args.containsKey("pas_file_path")) {
                    String pasContent = args.containsKey("pas_content") && !requireString(args, "pas_content").isBlank()
                            ? requireString(args, "pas_content")
                            : AnalyzeDelphiUnitTool.readFileWithFallback(Path.of(requireString(args, "pas_file_path")));
                    DelphiUnit unit = sourceParser.parse(pasContent, "input.pas");
                    if (!unit.getClasses().isEmpty()) {
                        dc = unit.getClasses().get(0);
                    }
                    log.info("  PAS parseado: {} classes", unit.getClasses().size());
                }

                // Gera modulo Angular completo
                log.info("  Gerando módulo Angular...");
                Map<String, String> generatedFiles = angularGenerator.generateModule(form, dc);

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

                // ── Java (7 arquivos) ──
                Map<String, String> javaFiles = new LinkedHashMap<>();
                for (DelphiClass dc : unit.getClasses()) {
                    String baseName = dc.getName().replaceAll("^T", "").replaceAll("^(?i)(frm|Frm)", "");
                    if (baseName.isEmpty()) baseName = dc.getName().replaceAll("^T", "");

                    javaFiles.put(baseName + "Entity.java", javaGenerator.generateEntity(dc, packageName, dfmFields));
                    javaFiles.put(baseName + "Repository.java", javaGenerator.generateRepository(dc, packageName));
                    javaFiles.put(baseName + "Service.java", javaGenerator.generateService(dc, packageName, unit.getSqlQueries(), unit.getBusinessRules()));
                    javaFiles.put(baseName + "Resource.java", javaGenerator.generateController(dc, packageName));
                    javaFiles.put(baseName + "Dto.java", javaGenerator.generateDto(dc, packageName, dfmFields));
                    javaFiles.put("Pesquisa" + baseName + "Dto.java", javaGenerator.generatePesquisaDto(dc, packageName, dfmFields));
                    javaFiles.put(baseName + "GridVo.java", javaGenerator.generateVo(dc, packageName, dfmFields));
                }
                result.put("javaFiles", javaFiles);

                // ── Angular (17 arquivos) ──
                Map<String, String> angularFiles = new LinkedHashMap<>();
                if (form != null) {
                    DelphiClass dc = unit.getClasses().isEmpty() ? null : unit.getClasses().get(0);
                    angularFiles = angularGenerator.generateModule(form, dc);
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
Isso varre todos os .pas e .dfm e aprende: prefixos, BD, módulos, SQL, componentes.
O perfil é persistido em disco — sobrevive a reinícios.

### 2. Migrar uma tela completa (Java + Angular)
```
generate_full_module(
  pas_file_path: "C:\\projeto\\f_MinhaTelaDelphipas",
  package_name: "logus.corporativo.api.meumodulo",
  output_dir: "C:\\output"    // opcional: salva em disco
)
```
Gera **24 arquivos** de uma vez:
- 7 Java: Entity, Repository, Service, Resource, DTO, PesquisaDTO, GridVo
- 17 Angular: Module, Routing, Container, Grid, Filtros, Cadastro, Services, Models

### 3. Analisar antes de migrar (opcional)
```
analyze_delphi_unit(file_path: "C:\\projeto\\f_Tela.pas")
analyze_dfm_form(file_path: "C:\\projeto\\f_Tela.dfm")
extract_sql_queries(file_path: "C:\\projeto\\f_Tela.pas")
extract_business_rules(file_path: "C:\\projeto\\f_Tela.pas")
detect_inconsistencies(file_path: "C:\\projeto\\f_Tela.pas")
```

### 4. Gerar plano de migração
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
## Referência de Tools (14 tools)

### Aprendizado
| Tool | Quando usar |
|------|-------------|
| `learn_repository` | **Uma vez** no início. Passe o caminho raiz do projeto Delphi. |
| `get_learned_profile` | Para ver o que foi aprendido (BD, prefixos, módulos). |
| `clear_learned_profile` | Para trocar de projeto. |
| `get_usage_guide` | Este manual. Chame para aprender a usar o MCP. |

### Análise
| Tool | Input | Output |
|------|-------|--------|
| `analyze_delphi_unit` | .pas (file_path) | Classes, métodos, SQL, regras, dependências (calledForms) |
| `analyze_dfm_form` | .dfm (file_path) | Componentes mapeados PrimeNG, gridColumns, datasetFields |
| `extract_sql_queries` | .pas (file_path) | SQLs completas com JPQL sugerido e @Param tipados |
| `extract_business_rules` | .pas (file_path) | Validações e cálculos com código Java sugerido |
| `analyze_delphi_project` | diretório | Inventário completo (todos .pas + .dfm) |
| `detect_inconsistencies` | .pas ou .dfm | Desvios do padrão do projeto (nomenclatura, SQL, componentes) |

### Geração
| Tool | Input | Output |
|------|-------|--------|
| `generate_full_module` | .pas + package | **24 arquivos** Java + Angular + salva em disco |
| `generate_java_class` | .pas + package | 7 arquivos Java (Entity/Repository/Service/Resource/DTO/VO) |
| `generate_angular_component` | .dfm + .pas | 17 arquivos Angular (Module/Container/Grid/Filtros/Cadastro) |
| `generate_migration_plan` | lista .pas + .dfm | Plano completo com fases, riscos e estimativas |

### Dica importante
Todas as tools aceitam `file_path` — passe o caminho absoluto do arquivo.
O campo `content` é opcional (use apenas se quiser enviar o conteúdo diretamente).

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

### Gerar só o Java (sem Angular)
```
generate_java_class(
  file_path: "C:\\des-jhs\\projeto\\f_Cliente.pas",
  package_name: "logus.corporativo.api.clientes",
  generate: ["entity", "repository", "service"]
)
```

### Gerar só o Angular (sem Java)
```
generate_angular_component(
  file_path: "C:\\des-jhs\\projeto\\f_Cliente.dfm",
  pas_file_path: "C:\\des-jhs\\projeto\\f_Cliente.pas"
)
```

""";
}
