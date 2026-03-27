package com.migration.mcp.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Agrupa todos os resultados de análise de uma unit Delphi para uso nos geradores.
 * Permite que os geradores consultem regras de negócio, validações, etc.
 */
public class AnalysisContext {

    private List<FormInitialization> formInitialization = new ArrayList<>();
    private List<ButtonStateRule> buttonStateRules = new ArrayList<>();
    private List<FieldValidationRule> fieldValidationRules = new ArrayList<>();
    private List<CalcCellColorRule> calcCellColorRules = new ArrayList<>();
    private List<DependentFieldRule> dependentFieldRules = new ArrayList<>();
    private List<DatasetEventRule> datasetEventRules = new ArrayList<>();
    private List<TransactionBoundary> transactionBoundaries = new ArrayList<>();
    private List<CrossFormDataFlow> crossFormDataFlow = new ArrayList<>();

    public List<FormInitialization> getFormInitialization() { return formInitialization; }
    public void setFormInitialization(List<FormInitialization> v) { this.formInitialization = v; }
    public List<ButtonStateRule> getButtonStateRules() { return buttonStateRules; }
    public void setButtonStateRules(List<ButtonStateRule> v) { this.buttonStateRules = v; }
    public List<FieldValidationRule> getFieldValidationRules() { return fieldValidationRules; }
    public void setFieldValidationRules(List<FieldValidationRule> v) { this.fieldValidationRules = v; }
    public List<CalcCellColorRule> getCalcCellColorRules() { return calcCellColorRules; }
    public void setCalcCellColorRules(List<CalcCellColorRule> v) { this.calcCellColorRules = v; }
    public List<DependentFieldRule> getDependentFieldRules() { return dependentFieldRules; }
    public void setDependentFieldRules(List<DependentFieldRule> v) { this.dependentFieldRules = v; }
    public List<DatasetEventRule> getDatasetEventRules() { return datasetEventRules; }
    public void setDatasetEventRules(List<DatasetEventRule> v) { this.datasetEventRules = v; }
    public List<TransactionBoundary> getTransactionBoundaries() { return transactionBoundaries; }
    public void setTransactionBoundaries(List<TransactionBoundary> v) { this.transactionBoundaries = v; }
    public List<CrossFormDataFlow> getCrossFormDataFlow() { return crossFormDataFlow; }
    public void setCrossFormDataFlow(List<CrossFormDataFlow> v) { this.crossFormDataFlow = v; }
}
