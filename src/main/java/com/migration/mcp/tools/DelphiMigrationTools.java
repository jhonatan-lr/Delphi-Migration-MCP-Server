package com.migration.mcp.tools;

import io.modelcontextprotocol.server.McpServerFeatures;

import java.util.List;

/**
 * Factory pública que agrupa todas as ferramentas MCP de migração Delphi.
 */
public class DelphiMigrationTools {

    private final LearnRepositoryTool      learnRepositoryTool;
    private final GetProfileTool           getProfileTool;
    private final ClearProfileTool         clearProfileTool;
    private final DetectInconsistenciesTool detectTool;
    private final AnalyzeDelphiUnitTool    analyzeUnitTool;
    private final AnalyzeDfmFormTool    analyzeDfmTool;
    private final ExtractSqlQueresTool  extractSqlTool;
    private final ExtractBusinessRules  extractRulesTool;
    private final GenerateMigrationPlan generatePlanTool;
    private final GenerateJavaClassTool generateJavaTool;
    private final GenerateAngularComponent generateAngularTool;
    private final AnalyzeProjectTool    analyzeProjectTool;
    private final GenerateFullModuleTool generateFullModuleTool;
    private final GetUsageGuideTool     usageGuideTool;

    public DelphiMigrationTools() {
        this.learnRepositoryTool = new LearnRepositoryTool();
        this.getProfileTool      = new GetProfileTool();
        this.clearProfileTool    = new ClearProfileTool();
        this.detectTool          = new DetectInconsistenciesTool();
        this.analyzeUnitTool     = new AnalyzeDelphiUnitTool();
        this.analyzeDfmTool      = new AnalyzeDfmFormTool();
        this.extractSqlTool      = new ExtractSqlQueresTool();
        this.extractRulesTool    = new ExtractBusinessRules();
        this.generatePlanTool    = new GenerateMigrationPlan();
        this.generateJavaTool    = new GenerateJavaClassTool();
        this.generateAngularTool = new GenerateAngularComponent();
        this.analyzeProjectTool  = new AnalyzeProjectTool();
        this.generateFullModuleTool = new GenerateFullModuleTool();
        this.usageGuideTool      = new GetUsageGuideTool();
    }

    public List<McpServerFeatures.SyncToolSpecification> all() {
        return List.of(
                // ── Manual de uso (chame primeiro!) ──────────────
                usageGuideTool.getSpecification(),
                // ── Aprendizado de repositório ───────────────────
                learnRepositoryTool.getSpecification(),
                getProfileTool.getSpecification(),
                clearProfileTool.getSpecification(),
                // ── Análise ──────────────────────────────────────
                analyzeUnitTool.getSpecification(),
                analyzeDfmTool.getSpecification(),
                extractSqlTool.getSpecification(),
                extractRulesTool.getSpecification(),
                analyzeProjectTool.getSpecification(),
                detectTool.getSpecification(),
                // ── Geração ──────────────────────────────────────
                generatePlanTool.getSpecification(),
                generateJavaTool.getSpecification(),
                generateAngularTool.getSpecification(),
                generateFullModuleTool.getSpecification()
        );
    }
}
