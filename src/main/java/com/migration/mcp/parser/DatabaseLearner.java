package com.migration.mcp.parser;

import com.migration.mcp.model.TargetPatterns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * Conecta ao banco Informix e extrai metadados reais:
 * tabelas, colunas, tipos, PKs, FKs.
 *
 * SOMENTE LEITURA — NUNCA executa INSERT, UPDATE, DELETE, DROP, ALTER, CREATE.
 */
public class DatabaseLearner {

  private static final Logger log = LoggerFactory.getLogger(DatabaseLearner.class);

  /**
   * Extrai metadados do banco e retorna TargetPatterns enriquecido.
   */
  public TargetPatterns learn(String jdbcUrl, String username, String password,
                               List<String> tablesFilter) throws Exception {
    log.info("Conectando ao banco: {}", jdbcUrl.replaceAll("password=\\w+", "password=***"));

    // Carrega driver Informix
    Class.forName("com.informix.jdbc.IfxDriver");

    TargetPatterns patterns = new TargetPatterns();

    try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
      conn.setReadOnly(true); // SEGURANÇA: somente leitura
      log.info("Conectado com sucesso. ReadOnly={}", conn.isReadOnly());

      // 1. Tabelas
      Map<Integer, String> tableMap = extractTables(conn, tablesFilter);
      log.info("Tabelas encontradas: {}", tableMap.size());

      // 2. Colunas de cada tabela
      Map<String, Map<String, ColumnInfo>> allColumns = new LinkedHashMap<>();
      for (Map.Entry<Integer, String> t : tableMap.entrySet()) {
        Map<String, ColumnInfo> cols = extractColumns(conn, t.getKey());
        allColumns.put(t.getValue(), cols);
      }

      // 3. PKs
      Map<String, List<String>> allPks = new LinkedHashMap<>();
      for (Map.Entry<Integer, String> t : tableMap.entrySet()) {
        List<String> pk = extractPrimaryKey(conn, t.getKey());
        if (!pk.isEmpty()) allPks.put(t.getValue(), pk);
      }
      log.info("Tabelas com PK: {}", allPks.size());

      // 4. FKs
      Map<String, List<FkInfo>> allFks = extractAllForeignKeys(conn, tableMap);
      log.info("Foreign keys encontradas: {}", allFks.values().stream().mapToInt(List::size).sum());

      // 5. Montar TargetPatterns
      buildPatterns(patterns, allColumns, allPks, allFks, tableMap);

      log.info("Patterns gerados: {} tabelas, {} FKs",
              patterns.getKnownTables().size(), patterns.getKnownForeignKeys().size());
    }

    return patterns;
  }

  // ── Queries (SOMENTE SELECT) ──────────────────────────────────────────────

  private Map<Integer, String> extractTables(Connection conn, List<String> filter) throws SQLException {
    Map<Integer, String> tables = new LinkedHashMap<>();
    String sql = "SELECT t.tabid, TRIM(t.tabname) AS table_name " +
                 "FROM systables t WHERE t.tabtype = 'T' AND t.tabid >= 100 ORDER BY t.tabname";
    try (PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        String name = rs.getString("table_name").toLowerCase();
        int tabid = rs.getInt("tabid");
        // Aplicar filtro se informado
        if (filter != null && !filter.isEmpty()) {
          boolean match = false;
          for (String prefix : filter) {
            if (name.startsWith(prefix.toLowerCase())) { match = true; break; }
          }
          if (!match) continue;
        }
        tables.put(tabid, name);
      }
    }
    return tables;
  }

  private Map<String, ColumnInfo> extractColumns(Connection conn, int tabid) throws SQLException {
    Map<String, ColumnInfo> cols = new LinkedHashMap<>();
    String sql = "SELECT TRIM(c.colname) AS col_name, c.colno, c.coltype, c.collength " +
                 "FROM syscolumns c WHERE c.tabid = ? ORDER BY c.colno";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, tabid);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          ColumnInfo ci = new ColumnInfo();
          ci.name = rs.getString("col_name").toLowerCase();
          ci.colNo = rs.getInt("colno");
          ci.colType = rs.getInt("coltype");
          ci.colLength = rs.getInt("collength");
          ci.nullable = ci.colType < 256;
          ci.typeName = mapInformixType(ci.colType % 256);
          ci.javaType = mapToJavaType(ci.typeName, ci.name);
          cols.put(ci.name, ci);
        }
      }
    }
    return cols;
  }

  private List<String> extractPrimaryKey(Connection conn, int tabid) throws SQLException {
    List<String> pkCols = new ArrayList<>();
    String sql = "SELECT i.part1,i.part2,i.part3,i.part4,i.part5,i.part6,i.part7,i.part8 " +
                 "FROM sysconstraints c JOIN sysindexes i ON c.idxname = i.idxname " +
                 "WHERE c.tabid = ? AND c.constrtype = 'P'";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, tabid);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          for (int i = 1; i <= 8; i++) {
            int colNo = Math.abs(rs.getInt("part" + i));
            if (colNo > 0) {
              String colName = resolveColumnName(conn, tabid, colNo);
              if (colName != null) pkCols.add(colName);
            }
          }
        }
      }
    }
    return pkCols;
  }

  private Map<String, List<FkInfo>> extractAllForeignKeys(Connection conn,
                                                           Map<Integer, String> tableMap) throws SQLException {
    Map<String, List<FkInfo>> result = new LinkedHashMap<>();
    String sql = "SELECT rc.tabid AS fk_tabid, " +
                 "ri.part1 AS fk_col1, ri.part2 AS fk_col2, " +
                 "rc2.tabid AS ref_tabid, " +
                 "ri2.part1 AS ref_col1, ri2.part2 AS ref_col2 " +
                 "FROM sysconstraints rc " +
                 "JOIN sysindexes ri ON rc.idxname = ri.idxname " +
                 "JOIN sysreferences sr ON rc.constrid = sr.constrid " +
                 "JOIN sysconstraints rc2 ON sr.primary = rc2.constrid " +
                 "JOIN sysindexes ri2 ON rc2.idxname = ri2.idxname " +
                 "WHERE rc.constrtype = 'R'";
    try (PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        int fkTabid = rs.getInt("fk_tabid");
        int refTabid = rs.getInt("ref_tabid");
        String fkTable = tableMap.get(fkTabid);
        String refTable = tableMap.get(refTabid);
        if (fkTable == null || refTable == null) continue;

        int fkCol1 = Math.abs(rs.getInt("fk_col1"));
        int refCol1 = Math.abs(rs.getInt("ref_col1"));
        String fkColName = resolveColumnName(conn, fkTabid, fkCol1);
        String refColName = resolveColumnName(conn, refTabid, refCol1);

        FkInfo fk = new FkInfo();
        fk.fkTable = fkTable;
        fk.fkColumn = fkColName;
        fk.refTable = refTable;
        fk.refColumn = refColName;

        result.computeIfAbsent(fkTable, k -> new ArrayList<>()).add(fk);
      }
    }
    return result;
  }

  private String resolveColumnName(Connection conn, int tabid, int colNo) throws SQLException {
    String sql = "SELECT TRIM(colname) AS col_name FROM syscolumns WHERE tabid = ? AND colno = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, tabid);
      ps.setInt(2, colNo);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getString("col_name").toLowerCase() : null;
      }
    }
  }

  // ── Mapeamentos ──────────────────────────────────────────────────────────

  private String mapInformixType(int typeCode) {
    return switch (typeCode) {
      case 0 -> "CHAR";
      case 1 -> "SMALLINT";
      case 2 -> "INTEGER";
      case 3 -> "FLOAT";
      case 4 -> "SMALLFLOAT";
      case 5 -> "DECIMAL";
      case 6 -> "SERIAL";
      case 7 -> "DATE";
      case 8 -> "MONEY";
      case 10 -> "DATETIME";
      case 13 -> "VARCHAR";
      case 14 -> "INTERVAL";
      case 15 -> "NCHAR";
      case 16 -> "NVARCHAR";
      case 17 -> "INT8";
      case 18 -> "SERIAL8";
      case 40, 43 -> "LVARCHAR";
      case 41, 45 -> "BOOLEAN";
      case 52 -> "BIGINT";
      case 53 -> "BIGSERIAL";
      default -> "OTHER";
    };
  }

  private String mapToJavaType(String informixType, String colName) {
    return switch (informixType) {
      case "CHAR", "VARCHAR", "NCHAR", "NVARCHAR", "LVARCHAR" -> "String";
      case "SMALLINT", "INTEGER" -> "Integer";
      case "INT8", "BIGINT" -> "Long";
      case "SERIAL" -> "Integer";
      case "SERIAL8", "BIGSERIAL" -> "Long";
      case "FLOAT", "SMALLFLOAT" -> "Double";
      case "DECIMAL", "MONEY" -> "BigDecimal";
      case "DATE", "DATETIME" -> "LogusDateTime";
      case "BOOLEAN" -> "Boolean";
      default -> "String";
    };
  }

  // ── Montar TargetPatterns ────────────────────────────────────────────────

  private void buildPatterns(TargetPatterns patterns,
                              Map<String, Map<String, ColumnInfo>> allColumns,
                              Map<String, List<String>> allPks,
                              Map<String, List<FkInfo>> allFks,
                              Map<Integer, String> tableMap) {

    // knownTables com colunas reais
    for (Map.Entry<String, Map<String, ColumnInfo>> e : allColumns.entrySet()) {
      String tableName = e.getKey();
      Map<String, ColumnInfo> cols = e.getValue();

      TargetPatterns.TablePattern tp = new TargetPatterns.TablePattern();
      // PK
      List<String> pk = allPks.get(tableName);
      if (pk != null && !pk.isEmpty()) {
        tp.setPk(pk.get(0));
      }
      // Entity name inferido
      tp.setEntity(inferEntityName(tableName));
      // SERIAL detection
      for (ColumnInfo ci : cols.values()) {
        if (ci.typeName.equals("SERIAL") || ci.typeName.equals("SERIAL8") || ci.typeName.equals("BIGSERIAL")) {
          tp.setNaturalKey(false);
          break;
        }
      }
      patterns.getKnownTables().put(tableName, tp);
    }

    // knownForeignKeys
    for (Map.Entry<String, List<FkInfo>> e : allFks.entrySet()) {
      for (FkInfo fk : e.getValue()) {
        String key = fk.fkColumn;
        // Mapeia para entity name da tabela referenciada
        String refEntity = inferEntityName(fk.refTable);
        patterns.getKnownForeignKeys().put(key, refEntity);
      }
    }

    // masterDetailRelationships
    for (Map.Entry<String, List<FkInfo>> e : allFks.entrySet()) {
      String tableName = e.getKey();
      // Se tabela detail (estd*) aponta para master (estm*)
      if (tableName.startsWith("estd")) {
        for (FkInfo fk : e.getValue()) {
          if (fk.refTable.startsWith("estm")) {
            TargetPatterns.MasterDetailPattern md = new TargetPatterns.MasterDetailPattern();
            md.setMaster(fk.refTable);
            md.setFk(fk.fkColumn);
            patterns.getMasterDetailRelationships().put(tableName, md);
          }
        }
      }
    }
  }

  private String inferEntityName(String tableName) {
    String clean = tableName;
    boolean isDetail = false;
    if (clean.matches("^estd.*")) { isDetail = true; clean = clean.substring(4); }
    else if (clean.matches("^estm.*")) { clean = clean.substring(4); }
    else if (clean.matches("^(cad|bdo|mov|log|fis|fin|rec|pag|ctb|vnd|cmp).*")) {
      clean = clean.substring(3);
    }
    StringBuilder pascal = new StringBuilder();
    for (String part : clean.split("[_]")) {
      if (!part.isEmpty()) pascal.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
    }
    if (pascal.isEmpty()) pascal.append(tableName);
    return (isDetail ? "Item" : "") + pascal + "Entity";
  }

  // ── Inner classes ──────────────────────────────────────────────────────────

  static class ColumnInfo {
    String name;
    int colNo;
    int colType;
    int colLength;
    boolean nullable;
    String typeName;
    String javaType;
  }

  static class FkInfo {
    String fkTable;
    String fkColumn;
    String refTable;
    String refColumn;
  }
}
