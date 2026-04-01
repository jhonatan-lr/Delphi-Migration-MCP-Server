package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Representa uma Unit Delphi analisada
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DelphiUnit {

    private String unitName;
    private String filePath;
    private String unitType; // "form", "datamodule", "class", "utility"
    private List<DelphiClass> classes = new ArrayList<>();
    private List<String> uses = new ArrayList<>();         // units importadas
    private List<String> globalVariables = new ArrayList<>();
    private List<DelphiProcedure> globalProcedures = new ArrayList<>();
    private List<SqlQuery> sqlQueries = new ArrayList<>();
    private List<BusinessRule> businessRules = new ArrayList<>();
    private String rawContent;

    /** Forms/telas chamados a partir desta unit (MakeShowModal, ShowModal, Create) */
    private List<String> calledForms = new ArrayList<>();

    /** Navegação detalhada entre forms: tipo de chamada, parâmetros passados, contexto */
    private List<Map<String, String>> formNavigations = new ArrayList<>();

    /** Constantes locais definidas no bloco const da unit: nome → valor */
    private Map<String, String> constants = new LinkedHashMap<>();

    /** Tipos enum definidos na unit: [{name, values:[...], javaEnum}] */
    private List<Map<String, Object>> enumTypes = new ArrayList<>();

    /** Regras de estado de botões (AfterScroll + Click handlers) */
    private List<ButtonStateRule> buttonStateRules = new ArrayList<>();

    /** Regras de colorização de células do grid (CalcCellColors) */
    private List<CalcCellColorRule> calcCellColorRules = new ArrayList<>();

    /** Inicialização de formulário (FormShow/FormCreate: defaults, combos, auto-loads) */
    private List<FormInitialization> formInitializations = new ArrayList<>();

    /** Fronteiras de transação (StartTransaction/Commit/Rollback) → @Transactional */
    private List<TransactionBoundary> transactionBoundaries = new ArrayList<>();

    // getters e setters
    public String getUnitName() { return unitName; }
    public void setUnitName(String unitName) { this.unitName = unitName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getUnitType() { return unitType; }
    public void setUnitType(String unitType) { this.unitType = unitType; }

    public List<DelphiClass> getClasses() { return classes; }
    public void setClasses(List<DelphiClass> classes) { this.classes = classes; }

    public List<String> getUses() { return uses; }
    public void setUses(List<String> uses) { this.uses = uses; }

    public List<String> getGlobalVariables() { return globalVariables; }
    public void setGlobalVariables(List<String> globalVariables) { this.globalVariables = globalVariables; }

    public List<DelphiProcedure> getGlobalProcedures() { return globalProcedures; }
    public void setGlobalProcedures(List<DelphiProcedure> procs) { this.globalProcedures = procs; }

    public List<SqlQuery> getSqlQueries() { return sqlQueries; }
    public void setSqlQueries(List<SqlQuery> sqlQueries) { this.sqlQueries = sqlQueries; }

    public List<BusinessRule> getBusinessRules() { return businessRules; }
    public void setBusinessRules(List<BusinessRule> businessRules) { this.businessRules = businessRules; }

    public String getRawContent() { return rawContent; }
    public void setRawContent(String rawContent) { this.rawContent = rawContent; }

    public List<String> getCalledForms() { return calledForms; }
    public void setCalledForms(List<String> calledForms) { this.calledForms = calledForms; }

    public List<Map<String, String>> getFormNavigations() { return formNavigations; }
    public void setFormNavigations(List<Map<String, String>> formNavigations) { this.formNavigations = formNavigations; }

    public Map<String, String> getConstants() { return constants; }
    public void setConstants(Map<String, String> constants) { this.constants = constants; }

    public List<Map<String, Object>> getEnumTypes() { return enumTypes; }
    public void setEnumTypes(List<Map<String, Object>> enumTypes) { this.enumTypes = enumTypes; }

    public List<ButtonStateRule> getButtonStateRules() { return buttonStateRules; }
    public void setButtonStateRules(List<ButtonStateRule> buttonStateRules) { this.buttonStateRules = buttonStateRules; }

    public List<CalcCellColorRule> getCalcCellColorRules() { return calcCellColorRules; }
    public void setCalcCellColorRules(List<CalcCellColorRule> calcCellColorRules) { this.calcCellColorRules = calcCellColorRules; }

    public List<FormInitialization> getFormInitializations() { return formInitializations; }
    public void setFormInitializations(List<FormInitialization> formInitializations) { this.formInitializations = formInitializations; }

    public List<TransactionBoundary> getTransactionBoundaries() { return transactionBoundaries; }
    public void setTransactionBoundaries(List<TransactionBoundary> transactionBoundaries) { this.transactionBoundaries = transactionBoundaries; }
}
