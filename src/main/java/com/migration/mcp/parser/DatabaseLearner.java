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
 *
 * Otimizado: usa 4 queries bulk em vez de N+1 por tabela.
 * 1549 tabelas em ~10 segundos em vez de ~25 minutos.
 */
public class DatabaseLearner {

  private static final Logger log = LoggerFactory.getLogger(DatabaseLearner.class);

  /**
   * Extrai metadados do banco e retorna TargetPatterns enriquecido.
   */
  public TargetPatterns learn(String jdbcUrl, String username, String password,
                               List<String> tablesFilter) throws Exception {
    log.info("Conectando ao banco: {}", jdbcUrl.replaceAll("password=\\w+", "password=***"));

    Class.forName("com.informix.jdbc.IfxDriver");

    TargetPatterns patterns = new TargetPatterns();

    try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
      conn.setReadOnly(true);
      log.info("Conectado com sucesso. ReadOnly={}", conn.isReadOnly());

      long t0 = System.currentTimeMillis();

      // 1. Tabelas (1 query)
      Map<Integer, String> tableMap = extractTables(conn, tablesFilter);
      log.info("Tabelas encontradas: {} ({}ms)", tableMap.size(), System.currentTimeMillis() - t0);
      if (tableMap.isEmpty()) {
        log.warn("Nenhuma tabela encontrada com o filtro informado.");
        return patterns;
      }

      // Mapa reverso: tabid -> tableName (para resolver colunas)
      Set<Integer> tabIds = tableMap.keySet();

      // 2. Todas as colunas de todas as tabelas (1 query bulk)
      long t1 = System.currentTimeMillis();
      Map<String, Map<String, ColumnInfo>> allColumns = extractAllColumns(conn, tableMap);
      log.info("Colunas extraídas para {} tabelas ({}ms)", allColumns.size(), System.currentTimeMillis() - t1);

      // Cache colno -> colname por tabid (para resolver PKs e FKs sem queries extras)
      Map<Integer, Map<Integer, String>> colNoCache = buildColNoCache(allColumns, tableMap);

      // 3. Todas as PKs (1 query bulk)
      long t2 = System.currentTimeMillis();
      Map<String, List<String>> allPks = extractAllPrimaryKeys(conn, tableMap, colNoCache);
      log.info("Tabelas com PK: {} ({}ms)", allPks.size(), System.currentTimeMillis() - t2);

      // 4. Todas as FKs (1 query bulk, sem resolveColumnName)
      long t3 = System.currentTimeMillis();
      Map<String, List<FkInfo>> allFks = extractAllForeignKeys(conn, tableMap, colNoCache);
      log.info("Foreign keys encontradas: {} ({}ms)",
              allFks.values().stream().mapToInt(List::size).sum(), System.currentTimeMillis() - t3);

      // 5. Montar TargetPatterns
      buildPatterns(patterns, allColumns, allPks, allFks, tableMap);

      long total = System.currentTimeMillis() - t0;
      log.info("Patterns gerados: {} tabelas, {} FKs — TOTAL: {}ms ({} seg)",
              patterns.getKnownTables().size(), patterns.getKnownForeignKeys().size(),
              total, total / 1000);
    }

    return patterns;
  }

  // ── Query 1: Tabelas ──────────────────────────────────────────────────────

  private Map<Integer, String> extractTables(Connection conn, List<String> filter) throws SQLException {
    Map<Integer, String> tables = new LinkedHashMap<>();
    String sql = "SELECT t.tabid, TRIM(t.tabname) AS table_name " +
                 "FROM systables t WHERE t.tabtype = 'T' AND t.tabid >= 100 ORDER BY t.tabname";
    try (PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        String name = rs.getString("table_name").toLowerCase();
        int tabid = rs.getInt("tabid");
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

  // ── Query 2: Todas as colunas (bulk) ──────────────────────────────────────

  private Map<String, Map<String, ColumnInfo>> extractAllColumns(
          Connection conn, Map<Integer, String> tableMap) throws SQLException {

    Map<String, Map<String, ColumnInfo>> result = new LinkedHashMap<>();
    // Inicializa mapas vazios para todas as tabelas
    for (String tableName : tableMap.values()) {
      result.put(tableName, new LinkedHashMap<>());
    }

    // Uma única query: JOIN systables com syscolumns
    String sql = "SELECT t.tabid, TRIM(t.tabname) AS table_name, " +
                 "TRIM(c.colname) AS col_name, c.colno, c.coltype, c.collength " +
                 "FROM syscolumns c " +
                 "JOIN systables t ON c.tabid = t.tabid " +
                 "WHERE t.tabtype = 'T' AND t.tabid >= 100 " +
                 "ORDER BY t.tabid, c.colno";

    try (PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        int tabid = rs.getInt("tabid");
        String tableName = tableMap.get(tabid);
        if (tableName == null) continue; // tabela não está no filtro

        ColumnInfo ci = new ColumnInfo();
        ci.name = rs.getString("col_name").toLowerCase();
        ci.colNo = rs.getInt("colno");
        ci.colType = rs.getInt("coltype");
        ci.colLength = rs.getInt("collength");
        ci.nullable = ci.colType < 256;
        ci.typeName = mapInformixType(ci.colType % 256);
        ci.javaType = mapToJavaType(ci.typeName, ci.name);

        result.get(tableName).put(ci.name, ci);
      }
    }
    return result;
  }

  // ── Cache colno -> colname (em memória, zero queries) ─────────────────────

  private Map<Integer, Map<Integer, String>> buildColNoCache(
          Map<String, Map<String, ColumnInfo>> allColumns,
          Map<Integer, String> tableMap) {

    // Inverter tableMap: tableName -> tabid
    Map<String, Integer> nameToId = new HashMap<>();
    for (Map.Entry<Integer, String> e : tableMap.entrySet()) {
      nameToId.put(e.getValue(), e.getKey());
    }

    Map<Integer, Map<Integer, String>> cache = new HashMap<>();
    for (Map.Entry<String, Map<String, ColumnInfo>> e : allColumns.entrySet()) {
      Integer tabid = nameToId.get(e.getKey());
      if (tabid == null) continue;
      Map<Integer, String> colNoMap = new HashMap<>();
      for (ColumnInfo ci : e.getValue().values()) {
        colNoMap.put(ci.colNo, ci.name);
      }
      cache.put(tabid, colNoMap);
    }
    return cache;
  }

  // ── Query 3: Todas as PKs (bulk) ─────────────────────────────────────────

  private Map<String, List<String>> extractAllPrimaryKeys(
          Connection conn, Map<Integer, String> tableMap,
          Map<Integer, Map<Integer, String>> colNoCache) throws SQLException {

    Map<String, List<String>> result = new LinkedHashMap<>();

    String sql = "SELECT c.tabid, " +
                 "i.part1, i.part2, i.part3, i.part4, i.part5, i.part6, i.part7, i.part8 " +
                 "FROM sysconstraints c " +
                 "JOIN sysindexes i ON c.idxname = i.idxname " +
                 "WHERE c.constrtype = 'P'";

    try (PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        int tabid = rs.getInt("tabid");
        String tableName = tableMap.get(tabid);
        if (tableName == null) continue;

        Map<Integer, String> colMap = colNoCache.get(tabid);
        if (colMap == null) continue;

        List<String> pkCols = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
          int colNo = Math.abs(rs.getInt("part" + i));
          if (colNo > 0) {
            String colName = colMap.get(colNo);
            if (colName != null) pkCols.add(colName);
          }
        }
        if (!pkCols.isEmpty()) {
          result.put(tableName, pkCols);
        }
      }
    }
    return result;
  }

  // ── Query 4: Todas as FKs (bulk, sem resolveColumnName) ───────────────────

  private Map<String, List<FkInfo>> extractAllForeignKeys(
          Connection conn, Map<Integer, String> tableMap,
          Map<Integer, Map<Integer, String>> colNoCache) throws SQLException {

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

        Map<Integer, String> fkColMap = colNoCache.get(fkTabid);
        Map<Integer, String> refColMap = colNoCache.get(refTabid);
        if (fkColMap == null || refColMap == null) continue;

        int fkCol1 = Math.abs(rs.getInt("fk_col1"));
        int refCol1 = Math.abs(rs.getInt("ref_col1"));

        FkInfo fk = new FkInfo();
        fk.fkTable = fkTable;
        fk.fkColumn = fkColMap.get(fkCol1);
        fk.refTable = refTable;
        fk.refColumn = refColMap.get(refCol1);

        if (fk.fkColumn != null && fk.refColumn != null) {
          result.computeIfAbsent(fkTable, k -> new ArrayList<>()).add(fk);
        }
      }
    }
    return result;
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

    for (Map.Entry<String, Map<String, ColumnInfo>> e : allColumns.entrySet()) {
      String tableName = e.getKey();
      Map<String, ColumnInfo> cols = e.getValue();

      TargetPatterns.TablePattern tp = new TargetPatterns.TablePattern();
      List<String> pk = allPks.get(tableName);
      if (pk != null && !pk.isEmpty()) {
        tp.setPk(pk.get(0));
      }
      tp.setEntity(inferEntityName(tableName));
      // Persistir colunas reais do banco
      List<TargetPatterns.ColumnPattern> columnPatterns = new ArrayList<>();
      for (ColumnInfo ci : cols.values()) {
        if (ci.typeName.equals("SERIAL") || ci.typeName.equals("SERIAL8") || ci.typeName.equals("BIGSERIAL")) {
          tp.setNaturalKey(false);
        }
        TargetPatterns.ColumnPattern cp = new TargetPatterns.ColumnPattern();
        cp.setName(ci.name);
        cp.setTypeName(ci.typeName);
        cp.setJavaType(ci.javaType);
        cp.setNullable(ci.nullable);
        cp.setLength(ci.colLength);
        columnPatterns.add(cp);
      }
      tp.setColumns(columnPatterns);
      patterns.getKnownTables().put(tableName, tp);
    }

    for (Map.Entry<String, List<FkInfo>> e : allFks.entrySet()) {
      for (FkInfo fk : e.getValue()) {
        String refEntity = inferEntityName(fk.refTable);
        patterns.getKnownForeignKeys().put(fk.fkColumn, refEntity);
      }
    }

    for (Map.Entry<String, List<FkInfo>> e : allFks.entrySet()) {
      String tableName = e.getKey();
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
