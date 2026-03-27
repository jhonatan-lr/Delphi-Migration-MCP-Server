package com.migration.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Representa fronteiras de transação (StartTransaction/Commit/Rollback).
 * Mapeia para @Transactional no Spring.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionBoundary {

    private String method;             // "SalvarPedido"
    private List<String> operations = new ArrayList<>();  // ["cdsPedido.ApplyUpdates", "cdsProdutos.ApplyUpdates"]
    private String rollbackOn;         // "Exception"
    private boolean hasExplicitRollback;
    private String migration;

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public List<String> getOperations() { return operations; }
    public void setOperations(List<String> operations) { this.operations = operations; }
    public String getRollbackOn() { return rollbackOn; }
    public void setRollbackOn(String rollbackOn) { this.rollbackOn = rollbackOn; }
    public boolean isHasExplicitRollback() { return hasExplicitRollback; }
    public void setHasExplicitRollback(boolean hasExplicitRollback) { this.hasExplicitRollback = hasExplicitRollback; }
    public String getMigration() { return migration; }
    public void setMigration(String migration) { this.migration = migration; }
}
