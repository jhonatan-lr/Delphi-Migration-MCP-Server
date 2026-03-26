package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.*;

/**
 * Perfil aprendido do repositório Delphi.
 * Armazena todos os padrões detectados para guiar a geração de código.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectProfile {

    // ── Identificação ─────────────────────────────────────────────────────────
    private String projectName;
    private String repositoryPath;
    private String learnedAt;
    private int totalFilesScanned;

    // ── Versão / Tecnologia ───────────────────────────────────────────────────
    private String detectedDelphiVersion;   // "Delphi 7", "XE8", "10.4 Sydney"...
    private String dbTechnology;            // "FireDAC", "BDE", "ADO", "IBX", "Zeos"
    private String dbVendor;                // "MSSQL", "Firebird", "Oracle", "MySQL", "PostgreSQL"
    private List<String> thirdPartyLibs = new ArrayList<>(); // DevExpress, FastReport, etc.

    // ── Prefixos de nomenclatura ──────────────────────────────────────────────
    private NamingConventions naming = new NamingConventions();

    // ── Estrutura de pastas ───────────────────────────────────────────────────
    private List<ProjectModule> modules = new ArrayList<>();
    private FolderStructure folderStructure = new FolderStructure();

    // ── Padrões de SQL ────────────────────────────────────────────────────────
    private SqlConventions sqlConventions = new SqlConventions();

    // ── Padrões de código ─────────────────────────────────────────────────────
    private CodePatterns codePatterns = new CodePatterns();

    // ── Componentes mais usados ───────────────────────────────────────────────
    private Map<String, Integer> componentFrequency = new LinkedHashMap<>(); // TFDQuery -> 87 ocorrências
    private List<String> topComponents = new ArrayList<>();

    // ── Entidades / Tabelas detectadas ────────────────────────────────────────
    private List<String> detectedTables = new ArrayList<>();
    private List<String> detectedDataModules = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NamingConventions {
        private String formPrefix        = "frm";   // frmCliente, frmPedido
        private String dataModulePrefix  = "dm";    // dmConexao, dmPrincipal
        private String framePrefix       = "fra";
        private String classPrefix       = "T";     // TCliente, TPedido
        private String queryPrefix       = "qry";   // qryClientes, qryPedidos
        private String dataSourcePrefix  = "ds";    // dsClientes
        private String tablePrefix       = "tbl";
        private String fieldPrefix       = "F";     // FId, FNome (campos privados)
        private String paramPrefix       = ":";     // :PARAM ou @PARAM ou ?
        private String variablePrefix    = "v";     // vTotal, vDesconto
        private String unitPrefix        = "u";     // uCliente, uPedido
        private String reportPrefix      = "r";     // rCliente, r_histpadrao
        private String selectionPrefix   = "s";     // s_AnaliseComportamento
        private String objectPrefix      = "o";     // o_pedido (business object)

        // Convenções de tabela SQL
        private String tableNamingStyle  = "UPPER_SNAKE";  // PEDIDO_ITEM ou PedidoItem
        private String columnNamingStyle = "UPPER_SNAKE";  // DATA_CADASTRO

        public String getFormPrefix() { return formPrefix; }
        public void setFormPrefix(String v) { this.formPrefix = v; }
        public String getDataModulePrefix() { return dataModulePrefix; }
        public void setDataModulePrefix(String v) { this.dataModulePrefix = v; }
        public String getFramePrefix() { return framePrefix; }
        public void setFramePrefix(String v) { this.framePrefix = v; }
        public String getClassPrefix() { return classPrefix; }
        public void setClassPrefix(String v) { this.classPrefix = v; }
        public String getQueryPrefix() { return queryPrefix; }
        public void setQueryPrefix(String v) { this.queryPrefix = v; }
        public String getDataSourcePrefix() { return dataSourcePrefix; }
        public void setDataSourcePrefix(String v) { this.dataSourcePrefix = v; }
        public String getTablePrefix() { return tablePrefix; }
        public void setTablePrefix(String v) { this.tablePrefix = v; }
        public String getFieldPrefix() { return fieldPrefix; }
        public void setFieldPrefix(String v) { this.fieldPrefix = v; }
        public String getParamPrefix() { return paramPrefix; }
        public void setParamPrefix(String v) { this.paramPrefix = v; }
        public String getVariablePrefix() { return variablePrefix; }
        public void setVariablePrefix(String v) { this.variablePrefix = v; }
        public String getUnitPrefix() { return unitPrefix; }
        public void setUnitPrefix(String v) { this.unitPrefix = v; }
        public String getReportPrefix() { return reportPrefix; }
        public void setReportPrefix(String v) { this.reportPrefix = v; }
        public String getSelectionPrefix() { return selectionPrefix; }
        public void setSelectionPrefix(String v) { this.selectionPrefix = v; }
        public String getObjectPrefix() { return objectPrefix; }
        public void setObjectPrefix(String v) { this.objectPrefix = v; }
        public String getTableNamingStyle() { return tableNamingStyle; }
        public void setTableNamingStyle(String v) { this.tableNamingStyle = v; }
        public String getColumnNamingStyle() { return columnNamingStyle; }
        public void setColumnNamingStyle(String v) { this.columnNamingStyle = v; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProjectModule {
        private String name;             // "Vendas", "Financeiro", "Estoque"
        private String folderPath;       // "src/vendas"
        private int formCount;
        private int unitCount;
        private List<String> mainForms = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getFolderPath() { return folderPath; }
        public void setFolderPath(String folderPath) { this.folderPath = folderPath; }
        public int getFormCount() { return formCount; }
        public void setFormCount(int formCount) { this.formCount = formCount; }
        public int getUnitCount() { return unitCount; }
        public void setUnitCount(int unitCount) { this.unitCount = unitCount; }
        public List<String> getMainForms() { return mainForms; }
        public void setMainForms(List<String> mainForms) { this.mainForms = mainForms; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FolderStructure {
        private List<String> topLevelFolders = new ArrayList<>();
        private String formsFolder;       // onde ficam os .dfm / forms
        private String dataModulesFolder; // onde ficam os DataModules
        private String framesFolder;
        private String reportsFolder;     // QuickReport / FastReport
        private String utilsFolder;       // units utilitárias

        public List<String> getTopLevelFolders() { return topLevelFolders; }
        public void setTopLevelFolders(List<String> v) { this.topLevelFolders = v; }
        public String getFormsFolder() { return formsFolder; }
        public void setFormsFolder(String v) { this.formsFolder = v; }
        public String getDataModulesFolder() { return dataModulesFolder; }
        public void setDataModulesFolder(String v) { this.dataModulesFolder = v; }
        public String getFramesFolder() { return framesFolder; }
        public void setFramesFolder(String v) { this.framesFolder = v; }
        public String getReportsFolder() { return reportsFolder; }
        public void setReportsFolder(String v) { this.reportsFolder = v; }
        public String getUtilsFolder() { return utilsFolder; }
        public void setUtilsFolder(String v) { this.utilsFolder = v; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SqlConventions {
        private String paramStyle        = "named_colon";  // :PARAM, @PARAM, ?
        private boolean usesStoredProcs  = false;
        private boolean usesViews        = false;
        private boolean usesDynamicSql   = false;  // concatenação de string SQL
        private String caseStyle         = "UPPER"; // SELECT, FROM uppercase
        private List<String> commonJoins = new ArrayList<>(); // padrões de JOIN mais usados
        private List<String> topTables   = new ArrayList<>(); // tabelas mais acessadas
        private String connectionPattern = "DataModule"; // onde fica a conexão

        public String getParamStyle() { return paramStyle; }
        public void setParamStyle(String v) { this.paramStyle = v; }
        public boolean isUsesStoredProcs() { return usesStoredProcs; }
        public void setUsesStoredProcs(boolean v) { this.usesStoredProcs = v; }
        public boolean isUsesViews() { return usesViews; }
        public void setUsesViews(boolean v) { this.usesViews = v; }
        public boolean isUsesDynamicSql() { return usesDynamicSql; }
        public void setUsesDynamicSql(boolean v) { this.usesDynamicSql = v; }
        public String getCaseStyle() { return caseStyle; }
        public void setCaseStyle(String v) { this.caseStyle = v; }
        public List<String> getCommonJoins() { return commonJoins; }
        public void setCommonJoins(List<String> v) { this.commonJoins = v; }
        public List<String> getTopTables() { return topTables; }
        public void setTopTables(List<String> v) { this.topTables = v; }
        public String getConnectionPattern() { return connectionPattern; }
        public void setConnectionPattern(String v) { this.connectionPattern = v; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CodePatterns {
        private String validationStyle   = "showmessage"; // "showmessage", "raise", "messagedlg"
        private boolean usesInheritance  = false;
        private boolean usesInterfaces   = false;
        private boolean usesThreads      = false;
        private boolean usesTimers       = false;
        private String errorHandling     = "try_except";  // "try_except", "raise"
        private List<String> commonUtils = new ArrayList<>(); // units de utils mais usadas

        public String getValidationStyle() { return validationStyle; }
        public void setValidationStyle(String v) { this.validationStyle = v; }
        public boolean isUsesInheritance() { return usesInheritance; }
        public void setUsesInheritance(boolean v) { this.usesInheritance = v; }
        public boolean isUsesInterfaces() { return usesInterfaces; }
        public void setUsesInterfaces(boolean v) { this.usesInterfaces = v; }
        public boolean isUsesThreads() { return usesThreads; }
        public void setUsesThreads(boolean v) { this.usesThreads = v; }
        public boolean isUsesTimers() { return usesTimers; }
        public void setUsesTimers(boolean v) { this.usesTimers = v; }
        public String getErrorHandling() { return errorHandling; }
        public void setErrorHandling(String v) { this.errorHandling = v; }
        public List<String> getCommonUtils() { return commonUtils; }
        public void setCommonUtils(List<String> v) { this.commonUtils = v; }
    }

    // ── Getters / Setters raiz ────────────────────────────────────────────────
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getRepositoryPath() { return repositoryPath; }
    public void setRepositoryPath(String repositoryPath) { this.repositoryPath = repositoryPath; }
    public String getLearnedAt() { return learnedAt; }
    public void setLearnedAt(String learnedAt) { this.learnedAt = learnedAt; }
    public int getTotalFilesScanned() { return totalFilesScanned; }
    public void setTotalFilesScanned(int totalFilesScanned) { this.totalFilesScanned = totalFilesScanned; }
    public String getDetectedDelphiVersion() { return detectedDelphiVersion; }
    public void setDetectedDelphiVersion(String v) { this.detectedDelphiVersion = v; }
    public String getDbTechnology() { return dbTechnology; }
    public void setDbTechnology(String dbTechnology) { this.dbTechnology = dbTechnology; }
    public String getDbVendor() { return dbVendor; }
    public void setDbVendor(String dbVendor) { this.dbVendor = dbVendor; }
    public List<String> getThirdPartyLibs() { return thirdPartyLibs; }
    public void setThirdPartyLibs(List<String> thirdPartyLibs) { this.thirdPartyLibs = thirdPartyLibs; }
    public NamingConventions getNaming() { return naming; }
    public void setNaming(NamingConventions naming) { this.naming = naming; }
    public List<ProjectModule> getModules() { return modules; }
    public void setModules(List<ProjectModule> modules) { this.modules = modules; }
    public FolderStructure getFolderStructure() { return folderStructure; }
    public void setFolderStructure(FolderStructure folderStructure) { this.folderStructure = folderStructure; }
    public SqlConventions getSqlConventions() { return sqlConventions; }
    public void setSqlConventions(SqlConventions sqlConventions) { this.sqlConventions = sqlConventions; }
    public CodePatterns getCodePatterns() { return codePatterns; }
    public void setCodePatterns(CodePatterns codePatterns) { this.codePatterns = codePatterns; }
    public Map<String, Integer> getComponentFrequency() { return componentFrequency; }
    public void setComponentFrequency(Map<String, Integer> componentFrequency) { this.componentFrequency = componentFrequency; }
    public List<String> getTopComponents() { return topComponents; }
    public void setTopComponents(List<String> topComponents) { this.topComponents = topComponents; }
    public List<String> getDetectedTables() { return detectedTables; }
    public void setDetectedTables(List<String> detectedTables) { this.detectedTables = detectedTables; }
    public List<String> getDetectedDataModules() { return detectedDataModules; }
    public void setDetectedDataModules(List<String> detectedDataModules) { this.detectedDataModules = detectedDataModules; }
}
