package com.migration.mcp.tools;

import com.migration.mcp.model.ProjectProfile;
import com.migration.mcp.model.ProjectProfileStore;
import com.migration.mcp.parser.RepositoryLearner;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tool: learn_repository
 * Varre um repositório Delphi local e constrói o ProjectProfile,
 * que passa a guiar todas as outras ferramentas de geração.
 */
public class LearnRepositoryTool extends BaseTool {

    private final RepositoryLearner learner = new RepositoryLearner();

    @Override
    public McpServerFeatures.SyncToolSpecification getSpecification() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "learn_repository",
                """
                Analisa um repositório Delphi local completo e aprende seus padrões específicos:
                prefixos de nomenclatura (forms, classes, queries, campos), tecnologia de banco de dados
                (FireDAC/ADO/BDE/IBX), vendor do banco (SQL Server/Firebird/Oracle), versão do Delphi,
                bibliotecas de terceiros (DevExpress/FastReport/ACBr), estrutura de pastas, módulos,
                convenções de SQL (estilo de parâmetro, uso de stored procs, tabelas principais) e
                padrões de código (validação, tratamento de erros).
                
                Após executar esta ferramenta, todas as outras ferramentas (generate_java_class,
                generate_angular_component, generate_migration_plan) passam a gerar código respeitando
                os padrões específicos deste repositório.
                
                O perfil aprendido é persistido em ~/.delphi-mcp/project-profile.json e sobrevive
                a reinícios do servidor MCP.
                """,
                """
                {
                  "type": "object",
                  "properties": {
                    "repository_path": {
                      "type": "string",
                      "description": "Caminho absoluto para a pasta raiz do repositório Delphi"
                    },
                    "project_name": {
                      "type": "string",
                      "description": "Nome do projeto (opcional — usa o nome da pasta se omitido)"
                    }
                  },
                  "required": ["repository_path"]
                }
                """
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> withLogging("learn_repository", args, () -> {
                String repoPath = requireString(args, "repository_path");

                ProjectProfile profile = learner.learn(repoPath);

                // Sobrescreve nome se fornecido
                if (args.containsKey("project_name")) {
                    profile.setProjectName(requireString(args, "project_name"));
                }

                // Persiste o perfil
                ProjectProfileStore.getInstance().save(profile);

                // Monta resumo para o usuário
                Map<String, Object> result = buildLearningSummary(profile);
                return success(result);
        }));
    }

    private Map<String, Object> buildLearningSummary(ProjectProfile profile) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "✅ Repositório aprendido com sucesso!");
        result.put("projectName", profile.getProjectName());
        result.put("repositoryPath", profile.getRepositoryPath());
        result.put("filesScanned", profile.getTotalFilesScanned());

        // Tecnologia
        Map<String, Object> tech = new LinkedHashMap<>();
        tech.put("delphiVersion",     profile.getDetectedDelphiVersion());
        tech.put("dbTechnology",      profile.getDbTechnology());
        tech.put("dbVendor",          profile.getDbVendor());
        tech.put("thirdPartyLibs",    profile.getThirdPartyLibs());
        result.put("technology", tech);

        // Nomenclatura aprendida
        Map<String, Object> naming = new LinkedHashMap<>();
        ProjectProfile.NamingConventions n = profile.getNaming();
        naming.put("formPrefix",       n.getFormPrefix()       + "  (ex: " + n.getFormPrefix() + "Cliente)");
        naming.put("unitPrefix",       n.getUnitPrefix()       + "  (ex: " + n.getUnitPrefix() + "Cliente)");
        naming.put("dataModulePrefix", n.getDataModulePrefix() + "  (ex: " + n.getDataModulePrefix() + "Principal)");
        naming.put("queryPrefix",      n.getQueryPrefix()      + "  (ex: " + n.getQueryPrefix() + "Clientes)");
        naming.put("fieldPrefix",      n.getFieldPrefix()      + "  (ex: " + n.getFieldPrefix() + "Nome)");
        naming.put("tableNamingStyle", n.getTableNamingStyle());
        result.put("namingConventions", naming);

        // SQL
        Map<String, Object> sql = new LinkedHashMap<>();
        ProjectProfile.SqlConventions s = profile.getSqlConventions();
        sql.put("paramStyle",      s.getParamStyle());
        sql.put("usesStoredProcs", s.isUsesStoredProcs());
        sql.put("usesDynamicSql",  s.isUsesDynamicSql());
        sql.put("connectionPattern", s.getConnectionPattern());
        sql.put("topTables",       s.getTopTables());
        result.put("sqlConventions", sql);

        // Módulos
        result.put("modulesDetected", profile.getModules().size());
        result.put("modules", profile.getModules().stream()
                .map(m -> m.getName() + " (" + m.getUnitCount() + " units, " + m.getFormCount() + " forms)")
                .toList());

        // Top componentes
        result.put("topComponents", profile.getTopComponents());

        // Padrões
        Map<String, Object> patterns = new LinkedHashMap<>();
        ProjectProfile.CodePatterns cp = profile.getCodePatterns();
        patterns.put("validationStyle", cp.getValidationStyle());
        patterns.put("usesInterfaces",  cp.isUsesInterfaces());
        patterns.put("usesThreads",     cp.isUsesThreads());
        patterns.put("errorHandling",   cp.getErrorHandling());
        result.put("codePatterns", patterns);

        result.put("suggestion", "Agora use generate_java_class, generate_angular_component ou " +
                "generate_migration_plan — o código gerado respeitará os padrões deste projeto.");

        result.put("suggestedBasePackage", ProjectProfileStore.getInstance().suggestBasePackage());

        return result;
    }
}
