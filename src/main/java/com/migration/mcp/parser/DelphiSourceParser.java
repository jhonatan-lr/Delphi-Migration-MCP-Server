package com.migration.mcp.parser;

import com.migration.mcp.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.*;

/**
 * Parser de código-fonte Delphi (.pas)
 * Extrai estrutura de classes, métodos, campos, SQL e regras de negócio.
 */
public class DelphiSourceParser {

    private static final Logger log = LoggerFactory.getLogger(DelphiSourceParser.class);

    // ── Patterns de extração ──────────────────────────────────────────────────

    private static final Pattern UNIT_NAME_PATTERN =
            Pattern.compile("(?i)^\\s*unit\\s+(\\w+)\\s*;", Pattern.MULTILINE);

    private static final Pattern USES_PATTERN =
            Pattern.compile("(?i)uses\\s+([^;]+);", Pattern.DOTALL);

    private static final Pattern CLASS_DECLARATION_PATTERN =
            Pattern.compile("(?i)(\\w+)\\s*=\\s*class\\s*(?:\\(([^)]+)\\))?", Pattern.MULTILINE);

    private static final Pattern PROCEDURE_PATTERN =
            Pattern.compile("(?i)(?:procedure|function)\\s+(\\w+\\.)?([\\w]+)\\s*(?:\\(([^)]*)\\))?\\s*(?::\\s*(\\w+))?\\s*;",
                    Pattern.MULTILINE);

    private static final Pattern FIELD_PATTERN =
            Pattern.compile("(?i)^\\s*(\\w+)\\s*:\\s*([\\w<>\\[\\]]+)\\s*;", Pattern.MULTILINE);

    // SQL: .SQL.Text :=  (single assignment)
    private static final Pattern SQL_TEXT_ASSIGN_PATTERN =
            Pattern.compile("(?i)(?:\\.|\\b)SQL\\.Text\\s*:=\\s*'([^']+)'", Pattern.MULTILINE);

    // Padrão para encontrar cada linha SQL.Add('...')
    // Em Delphi, strings são delimitadas por ' (aspas simples). " dentro da string é conteúdo normal.
    // Aceita tanto .SQL.Add (com prefixo objeto) quanto SQL.Add (dentro de with...do)
    private static final Pattern SQL_ADD_LINE_PATTERN =
            Pattern.compile("(?i)(?:\\.|\\b)SQL\\.Add\\s*\\(\\s*'([^']*)'\\s*\\)");

    private static final Pattern SQL_MULTILINE_PATTERN =
            Pattern.compile("(?i)(?:SELECT|INSERT|UPDATE|DELETE|EXEC|EXECUTE|CALL)\\s+[\\s\\S]{5,500}?(?:FROM|INTO|TABLE|PROC)\\s+\\w+",
                    Pattern.MULTILINE);

    private static final Pattern COMPONENT_TYPE_PATTERN =
            Pattern.compile("(?i)(T(?:Query|Table|StoredProc|SQLQuery|SQLConnection|IBQuery|ZQuery|ADOQuery|ADOConnection|FDQuery|FDConnection|UniQuery|UniConnection|Database|ClientDataSet|DataSetProvider|DataSource))");

    private static final Pattern EVENT_HANDLER_PATTERN =
            Pattern.compile("(?i)procedure\\s+(\\w+)\\.(On\\w+|\\w+Click|\\w+Change|\\w+Exit|\\w+Enter|\\w+KeyDown|\\w+KeyPress)\\s*\\(");

    private static final Pattern SHOWMESSAGE_PATTERN =
            Pattern.compile("(?i)(?:ShowMessage|MessageDlg|raise\\s+Exception|Application\\.MessageBox)\\s*\\(\\s*['\"]([^'\"]+)['\"]");

    private static final Pattern IF_VALIDATION_PATTERN =
            Pattern.compile("(?i)if\\s+(.{10,150})\\s+then\\s*(?:begin)?[\\s\\n]*((?:ShowMessage|raise|MessageDlg|Exit|Abort)[^;]{0,200})",
                    Pattern.DOTALL);

    // ──────────────────────────────────────────────────────────────────────────

    public DelphiUnit parse(String content, String filePath) {
        DelphiUnit unit = new DelphiUnit();
        unit.setFilePath(filePath);
        unit.setRawContent(content);

        // Remove comentários antes de parsear
        String cleaned = removeComments(content);

        // Unit name
        Matcher m = UNIT_NAME_PATTERN.matcher(cleaned);
        if (m.find()) {
            unit.setUnitName(m.group(1));
        }

        // Uses clause
        unit.setUses(extractUses(cleaned));

        // Classes
        unit.setClasses(extractClasses(cleaned));

        // Detecta tipo da unit
        unit.setUnitType(detectUnitType(cleaned, unit));

        // SQL Queries
        unit.setSqlQueries(extractSqlQueries(cleaned));

        // Business Rules
        unit.setBusinessRules(extractBusinessRules(cleaned));

        // Global procedures
        unit.setGlobalProcedures(extractGlobalProcedures(cleaned));

        // Item 12: Dependências entre forms (chamadas a outros forms)
        List<Map<String, String>> navigations = new ArrayList<>();
        unit.setCalledForms(extractCalledForms(cleaned, navigations));
        unit.setFormNavigations(navigations);

        log.debug("Parsed unit '{}': {} classes, {} SQL, {} rules, {} called forms",
                unit.getUnitName(),
                unit.getClasses().size(),
                unit.getSqlQueries().size(),
                unit.getBusinessRules().size(),
                unit.getCalledForms().size());

        return unit;
    }

    // ── Remoção de comentários ────────────────────────────────────────────────

    private String removeComments(String src) {
        // Remove { ... } e (* ... *)
        src = src.replaceAll("\\{[^}]*}", " ");
        src = src.replaceAll("\\(\\*[\\s\\S]*?\\*\\)", " ");
        // Remove // line comments
        src = src.replaceAll("//[^\n]*", " ");
        return src;
    }

    // ── Uses ─────────────────────────────────────────────────────────────────

    private List<String> extractUses(String src) {
        List<String> uses = new ArrayList<>();
        Matcher m = USES_PATTERN.matcher(src);
        while (m.find()) {
            String[] parts = m.group(1).split(",");
            for (String p : parts) {
                String unit = p.trim().replaceAll("\\s+in\\s+.*", "").trim();
                if (!unit.isEmpty()) uses.add(unit);
            }
        }
        return uses;
    }

    // ── Classes ──────────────────────────────────────────────────────────────

    private List<DelphiClass> extractClasses(String src) {
        List<DelphiClass> classes = new ArrayList<>();
        Matcher m = CLASS_DECLARATION_PATTERN.matcher(src);

        while (m.find()) {
            String className = m.group(1);
            // Filtra palavras reservadas
            if (isReservedWord(className)) continue;

            DelphiClass dc = new DelphiClass();
            dc.setName(className);
            dc.setParentClass(m.group(2) != null ? m.group(2).trim() : "TObject");
            dc.setClassType(detectClassType(className, m.group(2)));
            dc.setMigrationSuggestion(suggestClassMigration(dc.getClassType(), className));

            // Extrai campos e métodos do bloco da classe
            String classBlock = extractClassBlock(src, m.start());
            dc.setFields(extractFields(classBlock));
            dc.setMethods(extractMethods(src, className));

            classes.add(dc);
        }
        return classes;
    }

    private String extractClassBlock(String src, int start) {
        int depth = 0;
        int i = start;
        StringBuilder sb = new StringBuilder();
        while (i < src.length()) {
            char c = src.charAt(i);
            sb.append(c);
            String sub = src.substring(i).toLowerCase();
            if (sub.startsWith("begin") || sub.startsWith("class") || sub.startsWith("record")) depth++;
            if (sub.startsWith("end")) {
                if (depth <= 1) break;
                depth--;
            }
            i++;
            if (sb.length() > 5000) break; // limit
        }
        return sb.toString();
    }

    private List<DelphiField> extractFields(String block) {
        List<DelphiField> fields = new ArrayList<>();
        // Extrai secção private/protected/public
        Pattern section = Pattern.compile("(?i)(?:private|protected|public|published)\\s*([\\s\\S]*?)(?=(?:private|protected|public|published|end\\b)|$)");
        Matcher sm = section.matcher(block);
        while (sm.find()) {
            Matcher fm = FIELD_PATTERN.matcher(sm.group(1));
            while (fm.find()) {
                if (isReservedWord(fm.group(1))) continue;
                DelphiField f = new DelphiField();
                f.setName(fm.group(1));
                f.setDelphiType(fm.group(2));
                f.setJavaType(mapDelphiTypeToJava(fm.group(2)));
                f.setComponent(isComponentType(fm.group(2)));
                fields.add(f);
            }
        }
        return fields;
    }

    private List<DelphiProcedure> extractMethods(String src, String className) {
        List<DelphiProcedure> methods = new ArrayList<>();
        Pattern classMethodPattern = Pattern.compile(
                "(?i)(procedure|function)\\s+" + Pattern.quote(className) + "\\.(\\w+)\\s*(?:\\(([^)]*)\\))?\\s*(?::\\s*(\\w+))?\\s*;[\\s\\S]*?(?=^(?:procedure|function|initialization|finalization|end\\.))",
                Pattern.MULTILINE);

        Matcher m = classMethodPattern.matcher(src);
        while (m.find()) {
            DelphiProcedure proc = new DelphiProcedure();
            proc.setType(m.group(1).toLowerCase());
            proc.setName(m.group(2));
            if (m.group(3) != null) {
                proc.setParameters(Arrays.asList(m.group(3).split(";")));
            }
            proc.setReturnType(m.group(4));
            proc.setJavaReturnType(mapDelphiTypeToJava(m.group(4)));

            // Detecta event handlers
            Matcher evtM = EVENT_HANDLER_PATTERN.matcher(m.group(0));
            if (evtM.find()) {
                proc.setEventHandler(true);
                proc.setEventType(evtM.group(2));
                proc.setMigrationNotes("Event handler → Angular (event binding)");
            }

            // Body para análise posterior
            proc.setBody(m.group(0).substring(0, Math.min(m.group(0).length(), 5000)));
            methods.add(proc);
        }
        return methods;
    }

    private List<DelphiProcedure> extractGlobalProcedures(String src) {
        List<DelphiProcedure> procs = new ArrayList<>();
        Pattern globalProc = Pattern.compile(
                "(?i)^(procedure|function)\\s+(\\w+)\\s*(?:\\(([^)]*)\\))?\\s*(?::\\s*(\\w+))?\\s*;",
                Pattern.MULTILINE);
        Matcher m = globalProc.matcher(src);
        while (m.find()) {
            if (isReservedWord(m.group(2))) continue;
            DelphiProcedure p = new DelphiProcedure();
            p.setType(m.group(1).toLowerCase());
            p.setName(m.group(2));
            p.setReturnType(m.group(4));
            p.setJavaReturnType(mapDelphiTypeToJava(m.group(4)));
            procs.add(p);
        }
        return procs;
    }

    // ── SQL Extraction (stateful parser) ────────────────────────────────────

    /**
     * Parser stateful de SQL em código Delphi.
     *
     * Estratégia:
     * 1. Encontra cada início de bloco SQL: SQL.Clear ou primeiro SQL.Add/SQL.Text
     * 2. Encontra o fim do bloco: Open, ExecSQL, ExecProc, Close, ou próximo SQL.Clear
     * 3. Dentro do range [início, fim], extrai TODOS os SQL.Add('...') — incluindo
     *    os que estão dentro de if/then/else (SQL dinâmico condicional)
     * 4. Concatena tudo numa única query limpa
     *
     * Isso resolve o problema de SQL.Add dentro de blocos condicionais.
     */
    public List<SqlQuery> extractSqlQueries(String src) {
        List<SqlQuery> queries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int idx = 0;

        // ── Fase 1: SQL.Text := '...' (atribuição direta) ──
        Matcher m1 = SQL_TEXT_ASSIGN_PATTERN.matcher(src);
        while (m1.find()) {
            String sql = m1.group(1);
            if (sql != null && sql.trim().length() > 5) {
                String key = sql.trim().substring(0, Math.min(40, sql.trim().length()));
                if (seen.add(key)) {
                    queries.add(buildSqlQuery(sql.trim(), idx++, src, m1.start()));
                }
            }
        }

        // ── Fase 2: Blocos SQL.Clear ... SQL.Add ... Open/ExecSQL ──
        // Encontra todos os marcadores
        List<int[]> markers = new ArrayList<>(); // [posição, tipo] tipo: 0=Clear, 1=Add, 2=Open/Exec

        // Marcadores de início (SQL.Clear)
        Matcher clearM = Pattern.compile("(?i)(?:\\.|\\b)SQL\\.Clear\\b").matcher(src);
        while (clearM.find()) markers.add(new int[]{clearM.start(), 0});

        // Marcadores de conteúdo (SQL.Add)
        Matcher addM = SQL_ADD_LINE_PATTERN.matcher(src);
        while (addM.find()) markers.add(new int[]{addM.start(), 1});

        // Marcadores de fim (Open, ExecSQL, ExecProc)
        Matcher endM = Pattern.compile("(?i)(?:\\.|\\b)(?:Open|ExecSQL|ExecProc)\\b").matcher(src);
        while (endM.find()) markers.add(new int[]{endM.start(), 2});

        // Ordena por posição
        markers.sort((a, b) -> Integer.compare(a[0], b[0]));

        // Agrupa: de cada Clear (ou primeiro Add) até o próximo Open/Exec
        int state = 0; // 0=fora, 1=dentro de bloco SQL
        int blockStart = -1;
        int blockEnd = -1;

        for (int i = 0; i < markers.size(); i++) {
            int pos = markers.get(i)[0];
            int type = markers.get(i)[1];

            if (type == 0) { // SQL.Clear → início de bloco
                if (state == 1 && blockStart >= 0) {
                    idx = addFragmentsAsQueries(src, blockStart, pos, queries, seen, idx);
                }
                blockStart = pos;
                state = 1;
            } else if (type == 1 && state == 0) { // SQL.Add sem Clear anterior → início implícito
                blockStart = pos;
                state = 1;
            } else if (type == 2 && state == 1) { // Open/ExecSQL → fim do bloco
                blockEnd = pos;
                idx = addFragmentsAsQueries(src, blockStart, blockEnd, queries, seen, idx);
                state = 0;
                blockStart = -1;
            }
        }

        // Bloco final sem Open
        if (state == 1 && blockStart >= 0) {
            idx = addFragmentsAsQueries(src, blockStart, src.length(), queries, seen, idx);
        }

        return queries;
    }

    /**
     * Extrai SQL.Add('...') de um range, detectando branches if/else.
     * Retorna lista de SqlFragment: cada um com o SQL e a condição (se houver).
     */
    private List<SqlFragment> extractSqlFragmentsFromRange(String src, int start, int end) {
        // Expande o start para trás até o begin/procedure/function mais próximo
        // para capturar o if antes do primeiro SQL.Add
        int expandedStart = start;
        String before = src.substring(Math.max(0, start - 500), start);
        // Procura o último begin, procedure ou function antes do start
        int lastBegin = Math.max(
                before.lastIndexOf("begin"),
                Math.max(before.lastIndexOf("procedure"), before.lastIndexOf("function")));
        if (lastBegin >= 0) {
            expandedStart = Math.max(0, start - 500) + lastBegin;
        }
        String range = src.substring(expandedStart, Math.min(end, src.length()));
        List<SqlFragment> fragments = new ArrayList<>();

        // Detecta padrão: SQL.Add ... end + else + begin ... SQL.Add
        // Encontra posições de SQL.Add e de "end" + "else" + "begin"
        List<int[]> addPositions = new ArrayList<>(); // [posição no range, endPos]
        List<String> addContents = new ArrayList<>();
        Matcher addM = SQL_ADD_LINE_PATTERN.matcher(range);
        while (addM.find()) {
            addPositions.add(new int[]{addM.start(), addM.end()});
            addContents.add(addM.group(1));
        }

        if (addPositions.isEmpty()) {
            // Tenta SQL.Text :=
            Matcher textM = SQL_TEXT_ASSIGN_PATTERN.matcher(range);
            if (textM.find()) {
                fragments.add(new SqlFragment(textM.group(1).trim(), null));
            }
            return fragments;
        }

        // Detecta "end" seguido de "else" entre SQL.Add consecutivos
        // Padrão: SQL.Add(A) ... end ... else ... begin ... SQL.Add(B)
        // Aceita qualquer conteúdo entre end e else (pode ter ParamByName, comentários, etc.)
        Pattern elsePattern = Pattern.compile("(?si)\\bend\\b.*?\\belse\\b.*?\\bbegin\\b");

        // Encontra pontos de corte (posições de end/else/begin entre SQL.Adds)
        List<Integer> branchCuts = new ArrayList<>();
        for (int i = 0; i < addPositions.size() - 1; i++) {
            int gapStart = addPositions.get(i)[1];
            int gapEnd = addPositions.get(i + 1)[0];
            String gap = range.substring(gapStart, gapEnd);
            if (elsePattern.matcher(gap).find()) {
                branchCuts.add(i + 1);
            }
        }

        // Se tem branch cut, procura o if ANTES do primeiro SQL.Add do branch
        String ifCondition = null;
        if (!branchCuts.isEmpty()) {
            // Procura o if mais próximo antes do branch cut (olha até 500 chars antes)
            int firstCutAddIdx = branchCuts.get(0);
            int searchFrom = firstCutAddIdx > 0 ? addPositions.get(0)[0] : 0;
            // Procura o if em todo o range antes do primeiro SQL.Add do primeiro branch
            int firstAddOfBranch = addPositions.get(0)[0];
            String searchArea = range.substring(0, firstAddOfBranch);
            Pattern ifPattern = Pattern.compile("(?i)\\bif\\b\\s*\\(([^)]+)\\)\\s*then\\s*\\bbegin\\b");
            Matcher ifM = ifPattern.matcher(searchArea);
            // Pega o ÚLTIMO if encontrado (mais próximo do SQL.Add)
            while (ifM.find()) {
                ifCondition = ifM.group(1).trim();
            }
            // Se não achou antes do primeiro add, procura no range todo
            if (ifCondition == null) {
                int cutPos = addPositions.get(branchCuts.get(0))[0];
                String beforeCut = range.substring(0, cutPos);
                ifM = ifPattern.matcher(beforeCut);
                while (ifM.find()) {
                    ifCondition = ifM.group(1).trim();
                }
            }
        }

        // Junta tudo numa query só (branches são fragmentos da mesma query)
        StringBuilder sql = new StringBuilder();
        for (String content : addContents) {
            if (content != null && !content.isBlank()) {
                if (sql.length() > 0) sql.append(" ");
                sql.append(content.trim());
            }
        }
        if (sql.length() > 0) {
            String note = null;
            // Detecta branches: via branchCuts ou via SQL duplicada (mesmo SELECT aparece 2+ vezes)
            boolean hasBranch = !branchCuts.isEmpty();
            if (!hasBranch) {
                // Heurística: se o SQL tem FROM duplicado, provavelmente tem branches
                // Remove comentários Delphi { } e // antes de contar (evita falso positivo)
                String cleanRange = range.replaceAll("(?s)\\{[^}]*\\}", " ")     // remove { ... }
                                        .replaceAll("(?s)\\(\\*.*?\\*\\)", " ")  // remove (* ... *)
                                        .replaceAll("//[^\n]*", " ");            // remove // ...
                // Reconta SQL.Add limpos
                Matcher cleanAddM = SQL_ADD_LINE_PATTERN.matcher(cleanRange);
                StringBuilder cleanSql = new StringBuilder();
                while (cleanAddM.find()) {
                    if (cleanAddM.group(1) != null && !cleanAddM.group(1).isBlank()) {
                        if (cleanSql.length() > 0) cleanSql.append(" ");
                        cleanSql.append(cleanAddM.group(1).trim());
                    }
                }
                String upper = cleanSql.length() > 0 ? cleanSql.toString().toUpperCase() : sql.toString().toUpperCase();
                int fromCount = countOccurrences(upper, " FROM ");
                if (fromCount >= 2) hasBranch = true;
            }
            if (hasBranch) {
                // Se não tem ifCondition do branchCut, busca ifs nos GAPS entre SQL.Adds
                if (ifCondition == null) {
                    Pattern ifPat = Pattern.compile("(?i)\\bif\\b\\s*\\(([^)]+)\\)\\s*then\\b");
                    Set<String> conditions = new LinkedHashSet<>();
                    // Busca nos gaps entre SQL.Adds consecutivos
                    for (int i = 0; i < addPositions.size() - 1; i++) {
                        int gapStart = addPositions.get(i)[1];
                        int gapEnd = addPositions.get(i + 1)[0];
                        if (gapEnd > gapStart) {
                            String gap = range.substring(gapStart, gapEnd);
                            Matcher gapIfM = ifPat.matcher(gap);
                            while (gapIfM.find()) {
                                String cond = gapIfM.group(1).trim();
                                if (!cond.contains("Enabled") && !cond.contains("Visible") &&
                                    !cond.contains(".State") && !cond.contains("IsEmpty") &&
                                    cond.length() < 80) {
                                    conditions.add(cond);
                                }
                            }
                        }
                    }
                    // Também busca antes do primeiro SQL.Add
                    if (addPositions.get(0)[0] > 0) {
                        String beforeFirst = range.substring(0, addPositions.get(0)[0]);
                        Matcher bfM = ifPat.matcher(beforeFirst);
                        while (bfM.find()) {
                            String cond = bfM.group(1).trim();
                            if (!cond.contains("Enabled") && !cond.contains("Visible") &&
                                !cond.contains(".State") && !cond.contains("IsEmpty") &&
                                cond.length() < 80) {
                                conditions.add(cond);
                            }
                        }
                    }
                    if (!conditions.isEmpty()) {
                        ifCondition = String.join(" | ", conditions);
                    }
                }
                String condText = ifCondition != null
                        ? "if (" + ifCondition + ")"
                        : "condição detectada";
                note = "Contém branch condicional: " + condText + " — "
                     + "variantes no JOIN/WHERE dependendo da condição";
            }
            // Post-processing: detecta queries grudadas (2+ SELECT/INSERT/UPDATE/DELETE na mesma string)
            String fullSql = sql.toString();
            List<String> splitQueries = splitGluedQueries(fullSql);
            if (splitQueries.size() > 1) {
                for (String sq : splitQueries) {
                    fragments.add(new SqlFragment(sq.trim(), note));
                }
            } else {
                fragments.add(new SqlFragment(fullSql, note));
            }
        }
        return fragments;
    }

    /**
     * Detecta quando 2+ queries estão grudadas (ex: "select * from A select * from B")
     * e separa em queries individuais.
     * Ignora subselects (precedidos por ( ou IN ou EXISTS).
     */
    private List<String> splitGluedQueries(String sql) {
        List<String> queries = new ArrayList<>();
        Pattern startPattern = Pattern.compile("(?i)\\b(SELECT|INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\b");
        Matcher m = startPattern.matcher(sql);
        List<Integer> starts = new ArrayList<>();
        while (m.find()) {
            int pos = m.start();
            if (pos == 0) { starts.add(pos); continue; }

            // Ignora subselects e SELECTs dentro de expressões
            String before = sql.substring(Math.max(0, pos - 30), pos).trim().toUpperCase();
            if (before.endsWith("(") || before.endsWith("IN") || before.endsWith("EXISTS") ||
                before.endsWith("=") || before.endsWith("CASE") || before.endsWith("WHEN") ||
                before.endsWith("THEN") || before.endsWith("ELSE") || before.endsWith(",") ||
                before.endsWith("||")) {
                continue;
            }

            // Só aceita como nova query se a anterior contém FROM/INTO/SET (query completa)
            String preceding = sql.substring(starts.isEmpty() ? 0 : starts.get(starts.size() - 1), pos).toUpperCase();
            if (preceding.contains(" FROM ") || preceding.contains(" INTO ") || preceding.contains(" SET ")) {
                starts.add(pos);
            }
        }
        if (starts.size() <= 1) return queries;

        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = i + 1 < starts.size() ? starts.get(i + 1) : sql.length();
            String part = sql.substring(from, to).trim();
            if (part.length() > 15) {
                queries.add(part);
            }
        }
        return queries;
    }

    /** Adiciona fragments como queries, anotando branches condicionais */
    private int addFragmentsAsQueries(String src, int start, int end,
                                       List<SqlQuery> queries, Set<String> seen, int idx) {
        List<SqlFragment> frags = extractSqlFragmentsFromRange(src, start, end);
        for (SqlFragment frag : frags) {
            if (frag.sql == null || frag.sql.length() <= 5) continue;
            String key = frag.sql.substring(0, Math.min(40, frag.sql.length()));
            if (!seen.add(key)) continue;

            SqlQuery q = buildSqlQuery(frag.sql, idx++, src, start);
            if (frag.conditionalBranch != null) {
                q.setConditionalBranch(frag.conditionalBranch);
            }
            queries.add(q);
        }
        return idx;
    }

    /** Backward compat: retorna primeira query do range (ou null) */
    private String extractSqlFromRange(String src, int start, int end) {
        List<SqlFragment> frags = extractSqlFragmentsFromRange(src, start, end);
        return frags.isEmpty() ? null : frags.get(0).sql;
    }

    private static class SqlFragment {
        String sql;
        String conditionalBranch; // null se não condicional
        SqlFragment(String sql, String conditionalBranch) {
            this.sql = sql;
            this.conditionalBranch = conditionalBranch;
        }
    }

    private SqlQuery buildSqlQuery(String sql, int idx, String src, int pos) {
        SqlQuery q = new SqlQuery();
        q.setId("SQL_" + (idx + 1));
        q.setSql(sql);
        q.setQueryType(detectQueryType(sql));
        q.setTablesUsed(extractTables(sql));
        q.setContext(extractContext(src, pos));
        q.setJpaEquivalent(suggestJpa(sql));
        q.setRepositoryMethod(suggestRepositoryMethod(sql));
        return q;
    }

    private String detectQueryType(String sql) {
        String upper = sql.trim().toUpperCase();
        if (upper.startsWith("SELECT")) return "SELECT";
        if (upper.startsWith("INSERT")) return "INSERT";
        if (upper.startsWith("UPDATE")) return "UPDATE";
        if (upper.startsWith("DELETE")) return "DELETE";
        if (upper.startsWith("EXEC") || upper.startsWith("CALL")) return "STORED_PROC";
        return "UNKNOWN";
    }

    private List<String> extractTables(String sql) {
        List<String> tables = new ArrayList<>();
        Pattern tbl = Pattern.compile("(?i)(?:FROM|JOIN|INTO|UPDATE|TABLE)\\s+(\\w+)");
        Matcher m = tbl.matcher(sql);
        while (m.find()) tables.add(m.group(1));
        return tables;
    }

    private String extractContext(String src, int pos) {
        int start = Math.max(0, pos - 200);
        String snippet = src.substring(start, pos);
        Matcher m = Pattern.compile("(?i)(procedure|function)\\s+(\\w+\\.\\w+|\\w+)").matcher(snippet);
        String ctx = "global";
        while (m.find()) ctx = m.group(2);
        return ctx;
    }

    private String suggestJpa(String sql) {
        String type = detectQueryType(sql);
        List<String> tables = extractTables(sql);
        String mainTable = tables.isEmpty() ? "Entity" : toPascalCase(tables.get(0));

        // Extrai parâmetros (:param_name)
        List<String> params = new ArrayList<>();
        Matcher pm = Pattern.compile(":(\\w+)").matcher(sql);
        while (pm.find()) {
            String p = pm.group(1);
            if (!params.contains(p)) params.add(p);
        }

        // Extrai colunas do SELECT
        List<String> selectCols = new ArrayList<>();
        Matcher colM = Pattern.compile("(?i)select\\s+(.+?)\\s+from", Pattern.DOTALL).matcher(sql);
        if (colM.find()) {
            String colStr = colM.group(1).replaceAll("\\s+", " ").trim();
            if (!colStr.equals("*") && colStr.length() < 200) {
                // Simplifica: pega os aliases ou nomes de campo
                for (String part : colStr.split(",")) {
                    String col = part.trim();
                    // Pega alias se existir (xxx as alias, ou xxx alias)
                    Matcher aliasM = Pattern.compile("(?i)(?:as\\s+)?(\\w+)\\s*$").matcher(col);
                    if (aliasM.find()) selectCols.add(aliasM.group(1));
                }
            }
        }

        // Extrai JOINs
        List<String> joins = new ArrayList<>();
        Matcher jm = Pattern.compile("(?i)((?:inner|left|right|outer)?\\s*join)\\s+(\\w+)\\s+(\\w+)?\\s+on\\s+([^\\s]+\\s*=\\s*[^\\s]+)").matcher(sql);
        while (jm.find()) {
            joins.add(jm.group(2)); // tabela do join
        }

        StringBuilder jpa = new StringBuilder();

        switch (type) {
            case "SELECT" -> {
                // Gera @Query nativeQuery com a SQL original simplificada
                jpa.append("// Opção 1: Native Query\n");
                jpa.append("@Query(value = \"").append(sql.length() > 300 ? sql.substring(0, 300) + "...\"" : sql + "\"");
                jpa.append(",\n       nativeQuery = true)\n");

                // Params
                String paramsStr = params.stream()
                        .map(p -> "@Param(\"" + p + "\") " + guessParamType(p) + " " + snakeToCamel(p))
                        .collect(java.util.stream.Collectors.joining(", "));
                jpa.append("List<Object[]> findBy(").append(paramsStr).append(");\n\n");

                // Opção 2: JPQL
                jpa.append("// Opção 2: JPQL (requer @Entity mapeada)\n");
                jpa.append("@Query(\"SELECT e FROM ").append(mainTable).append("Entity e");
                if (!params.isEmpty()) {
                    jpa.append(" WHERE ");
                    jpa.append(params.stream()
                            .map(p -> "e." + snakeToCamel(p) + " = :" + p)
                            .collect(java.util.stream.Collectors.joining(" AND ")));
                }
                jpa.append("\")\n");
                jpa.append("List<").append(mainTable).append("Entity> findBy");
                jpa.append(params.stream().map(p -> toPascalCase(snakeToCamel(p))).collect(java.util.stream.Collectors.joining("And")));
                jpa.append("(").append(paramsStr).append(");");

                if (!joins.isEmpty()) {
                    jpa.append("\n\n// JOINs detectados: ").append(String.join(", ", joins));
                    jpa.append("\n// Considerar @ManyToOne / @OneToMany nas entidades");
                }
            }
            case "INSERT" -> jpa.append("repository.save(new ").append(mainTable).append("Entity(...));");
            case "UPDATE" -> jpa.append("repository.save(existing").append(mainTable).append(");");
            case "DELETE" -> jpa.append("repository.deleteById(id);");
            default -> jpa.append("@Procedure(name = \"").append(mainTable).append("\")\nvoid execute(...);\n// Avaliar mover lógica para Java");
        }
        return jpa.toString();
    }

    private String guessParamType(String paramName) {
        String lower = paramName.toLowerCase();
        if (lower.startsWith("dat_") || lower.contains("date")) return "Date";
        if (lower.startsWith("cdg_") || lower.startsWith("nmr_") || lower.startsWith("id")) return "Integer";
        if (lower.startsWith("flg_") || lower.startsWith("flb_")) return "String";
        if (lower.startsWith("val_") || lower.startsWith("qtd_")) return "BigDecimal";
        return "String";
    }

    private String snakeToCamel(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (char c : s.toCharArray()) {
            if (c == '_') { nextUpper = true; }
            else { sb.append(nextUpper ? Character.toUpperCase(c) : c); nextUpper = false; }
        }
        if (sb.length() > 0) sb.setCharAt(0, Character.toLowerCase(sb.charAt(0)));
        return sb.toString();
    }

    private String suggestRepositoryMethod(String sql) {
        List<String> tables = extractTables(sql);
        String entity = tables.isEmpty() ? "Entity" : toPascalCase(tables.get(0));
        String type = detectQueryType(sql);

        // Extrai parâmetros
        List<String> params = new ArrayList<>();
        Matcher pm = Pattern.compile(":(\\w+)").matcher(sql);
        while (pm.find()) {
            String p = pm.group(1);
            if (!params.contains(p)) params.add(p);
        }

        return switch (type) {
            case "SELECT" -> {
                String methodName = "findAll" + entity + "By" +
                        params.stream().map(p -> toPascalCase(snakeToCamel(p))).collect(java.util.stream.Collectors.joining("And"));
                String paramsStr = params.stream()
                        .map(p -> guessParamType(p) + " " + snakeToCamel(p))
                        .collect(java.util.stream.Collectors.joining(", "));
                yield "List<" + entity + "Entity> " + methodName + "(" + paramsStr + ")";
            }
            case "INSERT", "UPDATE" -> entity + "Entity save(" + entity + "Entity entity)";
            case "DELETE" -> "void delete" + entity + "ById(Integer id)";
            default -> "void execute" + entity + "Procedure(...)";
        };
    }

    // ── Called Forms (dependências entre telas) ─────────────────────────────

    private List<String> extractCalledForms(String src, List<Map<String, String>> navigations) {
        Set<String> forms = new LinkedHashSet<>();

        // Padrão 1: TfrmXxx.MakeShowModal(...) — com parâmetros
        Matcher m1 = Pattern.compile("(?i)(T\\w+)\\.MakeShowModal\\s*\\(([^)]*)?\\)").matcher(src);
        while (m1.find()) {
            String form = m1.group(1);
            String params = m1.group(2) != null ? m1.group(2).trim() : "";
            forms.add(form);
            Map<String, String> nav = new LinkedHashMap<>();
            nav.put("target", form);
            nav.put("method", "MakeShowModal");
            nav.put("params", params);
            nav.put("context", extractContext(src, m1.start()));
            navigations.add(nav);
        }

        // Padrão 2: Application.CreateForm(TfrmXxx, ...)
        Matcher m2 = Pattern.compile("(?i)Application\\.CreateForm\\s*\\(\\s*(T\\w+)").matcher(src);
        while (m2.find()) {
            forms.add(m2.group(1));
            Map<String, String> nav = new LinkedHashMap<>();
            nav.put("target", m2.group(1));
            nav.put("method", "CreateForm");
            nav.put("context", extractContext(src, m2.start()));
            navigations.add(nav);
        }

        // Padrão 3: TfrmXxx.Create(nil) ... ShowModal
        Matcher m3 = Pattern.compile("(?i)(Tfrm\\w+)\\.Create\\s*\\(").matcher(src);
        while (m3.find()) {
            forms.add(m3.group(1));
            // Procura atribuições de propriedade antes do ShowModal (vForm.Prop := valor)
            String afterCreate = src.substring(m3.end(), Math.min(m3.end() + 500, src.length()));
            List<String> assignments = new ArrayList<>();
            Matcher propM = Pattern.compile("(?i)\\w+\\.(\\w+)\\s*:=\\s*([^;]+);").matcher(afterCreate);
            while (propM.find() && assignments.size() < 5) {
                assignments.add(propM.group(1) + " := " + propM.group(2).trim());
            }
            Map<String, String> nav = new LinkedHashMap<>();
            nav.put("target", m3.group(1));
            nav.put("method", "Create+ShowModal");
            if (!assignments.isEmpty()) {
                nav.put("paramsPassedBeforeShow", String.join("; ", assignments));
            }
            nav.put("context", extractContext(src, m3.start()));
            navigations.add(nav);
        }

        // Padrão 4: Trel*.Imprimir(...)
        Matcher m5 = Pattern.compile("(?i)(T(?:rel|dtm|fra)\\w+)\\.(?:Imprimir|Execute|Create|MakeShowModal)\\s*\\(([^)]*)?\\)").matcher(src);
        while (m5.find()) {
            String form = m5.group(1);
            String params = m5.group(2) != null ? m5.group(2).trim() : "";
            forms.add(form);
            Map<String, String> nav = new LinkedHashMap<>();
            nav.put("target", form);
            nav.put("method", m5.group(0).replaceAll("(?i)^T\\w+\\.", "").replaceAll("\\(.*", ""));
            nav.put("params", params);
            nav.put("context", extractContext(src, m5.start()));
            navigations.add(nav);
        }

        // Remove a própria classe (self references)
        forms.removeIf(f -> src.contains(f + " = class"));
        navigations.removeIf(n -> src.contains(n.get("target") + " = class"));

        return new ArrayList<>(forms);
    }

    // ── Form Initialization (FormShow / FormCreate / FormActivate) ─────────

    /**
     * Extrai lógica de inicialização de tela de métodos FormShow, FormCreate,
     * FormActivate e Create (constructor). Detecta:
     * - DEFAULT_VALUE: atribuições a campos visuais (.Date, .Text, .KeyValue, etc.)
     * - CONDITIONAL_DEFAULT: atribuições dentro de if/then
     * - COMBO_PRESELECTION: seleção programática de itens (.Selected := True, .KeyValue)
     * - AUTO_LOAD: chamadas a métodos de carga (Carregar*, Pesquisar*, Listar*, Load*)
     * - INITIAL_STATE: DisableComponent / EnableComponent / .Enabled := / .Visible :=
     */
    public List<FormInitialization> extractFormInitialization(String src) {
        List<FormInitialization> results = new ArrayList<>();

        // Encontra o corpo de cada método de inicialização
        // Padrão: procedure TClassName.FormShow(Sender: TObject);  ...body...
        String[] initMethods = {"FormShow", "FormCreate", "FormActivate", "Create"};

        for (String methodName : initMethods) {
            Pattern methodPattern = Pattern.compile(
                    "(?i)procedure\\s+\\w+\\." + methodName + "\\s*\\([^)]*\\)\\s*;[\\s\\S]*?(?=^(?:procedure|function|initialization|finalization|end\\.))",
                    Pattern.MULTILINE);
            Matcher mm = methodPattern.matcher(src);
            if (mm.find()) {
                String body = mm.group(0);
                if (body.length() > 10000) body = body.substring(0, 10000);

                FormInitialization init = parseInitBody(body, methodName);
                if (init.totalDetected() > 0) {
                    results.add(init);
                }
            }
        }

        return results;
    }

    private FormInitialization parseInitBody(String body, String context) {
        FormInitialization init = new FormInitialization();
        init.setContext(context);

        // ── 1. Detecta atribuições condicionais (if ... then ... campo.Prop := valor) ──
        // Precisa rodar ANTES dos default values simples para marcar ranges já consumidos
        List<int[]> conditionalRanges = new ArrayList<>();
        extractConditionalDefaults(body, context, init, conditionalRanges);

        // ── 2. Default values simples (fora de blocos condicionais) ──
        extractDefaultValues(body, context, init, conditionalRanges);

        // ── 3. Combo pre-selection (Items.Items[i].Selected := True, loops com Selected) ──
        extractComboPreselections(body, context, init);

        // ── 4. Auto-load (chamadas a Carregar*, Pesquisar*, Listar*, Load*, Buscar*) ──
        extractAutoLoads(body, context, init);

        // ── 5. Initial state (DisableComponent, EnableComponent, .Enabled :=, .Visible :=) ──
        extractInitialStates(body, context, init, conditionalRanges);

        return init;
    }

    // ── 1. Default Values ──────────────────────────────────────────────────

    private static final Pattern DEFAULT_VALUE_PATTERN = Pattern.compile(
            "(?i)(\\w+)\\.(Date|Text|KeyValue|Value|Checked|ItemIndex|Caption|EditValue|DisplayValue)\\s*:=\\s*([^;]+);");

    private void extractDefaultValues(String body, String context, FormInitialization init, List<int[]> conditionalRanges) {
        Matcher m = DEFAULT_VALUE_PATTERN.matcher(body);
        while (m.find()) {
            // Pula se está dentro de um range condicional já processado
            if (isInRange(m.start(), conditionalRanges)) continue;

            String component = m.group(1);
            String property = m.group(2);
            String value = m.group(3).trim();

            // Ignora variáveis locais e self
            if (component.equalsIgnoreCase("Self") || component.equalsIgnoreCase("Result")) continue;

            FormInitialization.DefaultValue dv = new FormInitialization.DefaultValue();
            dv.setComponent(component);
            dv.setProperty(property);
            dv.setValue(value);
            dv.setContext(context);
            dv.setDescription(describeDefaultValue(component, property, value));
            dv.setMigration(suggestDefaultMigration(property, value));
            init.getDefaultValues().add(dv);
        }
    }

    private String describeDefaultValue(String component, String property, String value) {
        String valueLower = value.toLowerCase();
        if (valueLower.contains("conexao.date") || valueLower.contains("date") || valueLower.contains("now"))
            return component + " inicializado com data atual do servidor";
        if (valueLower.contains("''") || valueLower.equals("''" ))
            return component + " inicializado como vazio";
        if (valueLower.equals("0") || valueLower.equals("false") || valueLower.equals("true"))
            return component + "." + property + " inicializado com " + value;
        return component + "." + property + " inicializado com " + value;
    }

    private String suggestDefaultMigration(String property, String value) {
        String propLower = property.toLowerCase();
        String valLower = value.toLowerCase();
        if (propLower.equals("date")) {
            if (valLower.contains("conexao.date") || valLower.contains("now") || valLower.contains("date"))
                return "Inicializar campo no buildFormGroup() com new Date()";
            return "Inicializar campo com valor de data no buildFormGroup()";
        }
        if (propLower.equals("keyvalue"))
            return "Pré-selecionar dropdown/autocomplete no buildFormGroup() ou ngOnInit";
        if (propLower.equals("checked"))
            return "Inicializar checkbox no buildFormGroup() com " + value;
        if (propLower.equals("itemindex"))
            return "Inicializar dropdown selectedIndex no buildFormGroup()";
        if (propLower.equals("text") || propLower.equals("caption"))
            return "Inicializar campo texto no buildFormGroup() com '" + value + "'";
        return "Inicializar campo no buildFormGroup() com valor: " + value;
    }

    // ── 2. Conditional Defaults ────────────────────────────────────────────

    // Padrão: if (condição) then begin ... campo.Prop := valor; ... end;
    private static final Pattern IF_BLOCK_PATTERN = Pattern.compile(
            "(?i)if\\s+(.{5,300}?)\\s+then\\s*(?:begin)?([\\s\\S]*?)(?:end\\s*;|(?=\\belse\\b))",
            Pattern.DOTALL);

    private void extractConditionalDefaults(String body, String context, FormInitialization init, List<int[]> conditionalRanges) {
        Matcher ifM = IF_BLOCK_PATTERN.matcher(body);
        while (ifM.find()) {
            String condition = ifM.group(1).trim().replaceAll("\\s+", " ");
            String block = ifM.group(2);
            int blockStart = ifM.start();
            int blockEnd = ifM.end();

            // Procura atribuições a campos visuais dentro do bloco condicional
            Matcher assignM = DEFAULT_VALUE_PATTERN.matcher(block);
            boolean foundAssignment = false;

            while (assignM.find()) {
                String component = assignM.group(1);
                String property = assignM.group(2);
                String value = assignM.group(3).trim();

                if (component.equalsIgnoreCase("Self") || component.equalsIgnoreCase("Result")) continue;

                // Verifica se há DisableComponent no mesmo bloco
                boolean disabled = block.toLowerCase().contains("disablecomponent(" + component.toLowerCase() + ")") ||
                                   block.toLowerCase().contains("disablecomponent( " + component.toLowerCase());

                FormInitialization.ConditionalDefault cd = new FormInitialization.ConditionalDefault();
                cd.setComponent(component);
                cd.setCondition(condition);
                cd.setValue(value);
                cd.setDisabled(disabled);
                cd.setDescription(describeConditionalDefault(component, condition, disabled));
                cd.setMigration(suggestConditionalMigration(component, condition, disabled));
                init.getConditionalDefaults().add(cd);
                foundAssignment = true;
            }

            if (foundAssignment) {
                conditionalRanges.add(new int[]{blockStart, blockEnd});
            }
        }
    }

    private String describeConditionalDefault(String component, String condition, boolean disabled) {
        String desc = component + " pré-selecionado quando " + condition;
        if (disabled) desc += " e desabilitado";
        return desc;
    }

    private String suggestConditionalMigration(String component, String condition, boolean disabled) {
        if (disabled)
            return "Se condição (" + condition + ") verdadeira, pré-selecionar e desabilitar campo no ngOnInit";
        return "Se condição (" + condition + ") verdadeira, pré-selecionar campo no ngOnInit";
    }

    // ── 3. Combo Pre-selection ─────────────────────────────────────────────

    private void extractComboPreselections(String body, String context, FormInitialization init) {
        // Padrão 1: loop com Items.Items[i].Selected := True e comparação de Key
        // for i := ... if ((Items.Items[i].Key = '1') or ...) then Items.Items[i].Selected := True;
        Pattern loopSelectPattern = Pattern.compile(
                "(?i)for\\s+\\w+\\s*:=[\\s\\S]*?Selected\\s*:=\\s*True[\\s\\S]*?;",
                Pattern.DOTALL);
        Matcher m1 = loopSelectPattern.matcher(body);
        while (m1.find()) {
            String block = m1.group(0);
            // Extrai as keys comparadas: Key = '1', Key = '2', etc.
            List<String> keys = new ArrayList<>();
            Matcher keyM = Pattern.compile("(?i)Key\\s*=\\s*'([^']*)'").matcher(block);
            while (keyM.find()) {
                keys.add(keyM.group(1));
            }

            // Tenta encontrar o componente (procura no contexto antes do loop)
            String component = findComponentBeforePosition(body, m1.start());

            FormInitialization.ComboPreselection cp = new FormInitialization.ComboPreselection();
            cp.setComponent(component);
            cp.setSelectedKeys(keys.isEmpty() ? null : keys);
            cp.setDescription("Pré-seleciona itens " + (keys.isEmpty() ? "no combo" : keys.toString()) + " ao abrir a tela");
            cp.setMigration("Inicializar formControl com " + (keys.isEmpty() ? "valores pré-selecionados" : keys.toString()) + " no buildFormGroup()");
            init.getComboPreselections().add(cp);
        }

        // Padrão 2: Atribuição direta combo.KeyValue := valor (fora de condicionais simples)
        Pattern keyValuePattern = Pattern.compile(
                "(?i)(\\w+)\\.KeyValue\\s*:=\\s*([^;]+);");
        Matcher m2 = keyValuePattern.matcher(body);
        while (m2.find()) {
            String component = m2.group(1);
            String value = m2.group(2).trim();

            // Verifica se já foi capturado como conditional default
            boolean alreadyCaptured = init.getConditionalDefaults().stream()
                    .anyMatch(cd -> cd.getComponent().equalsIgnoreCase(component));
            if (alreadyCaptured) continue;

            FormInitialization.ComboPreselection cp = new FormInitialization.ComboPreselection();
            cp.setComponent(component);
            cp.setSelectedKeys(List.of(value));
            cp.setDescription(component + " pré-selecionado com valor " + value);
            cp.setMigration("Inicializar formControl com valor " + value + " no buildFormGroup()");
            init.getComboPreselections().add(cp);
        }

        // Padrão 3: SelectAll / CheckAll em multiselect
        Pattern selectAllPattern = Pattern.compile(
                "(?i)(\\w+)\\.(SelectAll|CheckAll|SetAllSelected)\\b");
        Matcher m3 = selectAllPattern.matcher(body);
        while (m3.find()) {
            FormInitialization.ComboPreselection cp = new FormInitialization.ComboPreselection();
            cp.setComponent(m3.group(1));
            cp.setDescription(m3.group(1) + " — todos os itens pré-selecionados ao abrir");
            cp.setMigration("Usar [ngSelectAllItensPredefined]='true' no app-dropdown-multiselect");
            init.getComboPreselections().add(cp);
        }
    }

    private String findComponentBeforePosition(String body, int pos) {
        // Procura o componente mais próximo antes da posição (padrão: with ComponentName do)
        String before = body.substring(Math.max(0, pos - 500), pos);
        Matcher wm = Pattern.compile("(?i)(?:with\\s+)(\\w+)").matcher(before);
        String last = null;
        while (wm.find()) last = wm.group(1);
        if (last != null) return last;

        // Procura atribuição a .Items ou referência a componente
        Matcher cm = Pattern.compile("(?i)(\\w+)\\.Items").matcher(before);
        while (cm.find()) last = cm.group(1);
        return last != null ? last : "unknown";
    }

    // ── 4. Auto-load ───────────────────────────────────────────────────────

    private static final Pattern AUTO_LOAD_PATTERN = Pattern.compile(
            "(?i)(?:^|;|\\bthen\\b|\\bdo\\b|\\bbegin\\b)\\s*((?:Carregar|Pesquisar|Listar|Load|Buscar|Atualizar|Consultar|Preencher|Montar|Popular|Refresh)\\w*)\\s*(?:\\([^)]*\\))?\\s*;",
            Pattern.MULTILINE);

    private void extractAutoLoads(String body, String context, FormInitialization init) {
        Matcher m = AUTO_LOAD_PATTERN.matcher(body);
        Set<String> seen = new HashSet<>();
        while (m.find()) {
            String method = m.group(1);
            if (!seen.add(method.toLowerCase())) continue;

            FormInitialization.AutoLoad al = new FormInitialization.AutoLoad();
            al.setMethod(method);
            al.setContext(context);
            al.setDescription("Pesquisa/carga executada automaticamente ao abrir a tela");
            al.setMigration("Chamar handlePesquisar() no ngAfterViewInit após inicializar filtros");
            init.getAutoLoads().add(al);
        }
    }

    // ── 5. Initial State (Disable/Enable/Visible) ─────────────────────────

    private void extractInitialStates(String body, String context, FormInitialization init, List<int[]> conditionalRanges) {
        // Padrão 1: TLogusWinControl.DisableComponent(campo)
        Pattern disablePattern = Pattern.compile(
                "(?i)TLogusWinControl\\.DisableComponent\\s*\\(\\s*(\\w+)\\s*\\)");
        Matcher m1 = disablePattern.matcher(body);
        while (m1.find()) {
            // Pula se já capturado como conditional default
            if (isInRange(m1.start(), conditionalRanges)) continue;

            String component = m1.group(1);
            boolean alreadyCaptured = init.getConditionalDefaults().stream()
                    .anyMatch(cd -> cd.getComponent().equalsIgnoreCase(component) && cd.isDisabled());
            if (alreadyCaptured) continue;

            FormInitialization.InitialState is = new FormInitialization.InitialState();
            is.setComponent(component);
            is.setState("disabled");
            is.setDescription(component + " sempre desabilitado ao abrir (preenchido automaticamente)");
            is.setMigration("Adicionar [disabled]='true' ou readonly no input");
            init.getInitialStates().add(is);
        }

        // Padrão 2: TLogusWinControl.EnableComponent(campo, condição)
        Pattern enablePattern = Pattern.compile(
                "(?i)TLogusWinControl\\.EnableComponent\\s*\\(\\s*(\\w+)\\s*(?:,\\s*([^)]+))?\\)");
        Matcher m2 = enablePattern.matcher(body);
        while (m2.find()) {
            if (isInRange(m2.start(), conditionalRanges)) continue;

            FormInitialization.InitialState is = new FormInitialization.InitialState();
            is.setComponent(m2.group(1));
            is.setState("enabled");
            if (m2.group(2) != null) is.setCondition(m2.group(2).trim());
            is.setDescription(m2.group(1) + " habilitado" + (m2.group(2) != null ? " quando " + m2.group(2).trim() : ""));
            is.setMigration("Controlar com [disabled] binding condicional no template");
            init.getInitialStates().add(is);
        }

        // Padrão 3: campo.Enabled := False / campo.Visible := False (fora de condicionais)
        Pattern propStatePattern = Pattern.compile(
                "(?i)(\\w+)\\.(Enabled|Visible|ReadOnly)\\s*:=\\s*(True|False)\\s*;");
        Matcher m3 = propStatePattern.matcher(body);
        while (m3.find()) {
            if (isInRange(m3.start(), conditionalRanges)) continue;
            if (m3.group(1).equalsIgnoreCase("Self") || m3.group(1).equalsIgnoreCase("Result")) continue;

            String component = m3.group(1);
            String property = m3.group(2);
            boolean boolValue = m3.group(3).equalsIgnoreCase("True");

            String state;
            if (property.equalsIgnoreCase("Enabled")) state = boolValue ? "enabled" : "disabled";
            else if (property.equalsIgnoreCase("Visible")) state = boolValue ? "visible" : "hidden";
            else state = boolValue ? "readonly" : "editable";

            FormInitialization.InitialState is = new FormInitialization.InitialState();
            is.setComponent(component);
            is.setState(state);
            is.setDescription(component + " " + state + " ao abrir a tela");
            is.setMigration(suggestStateMigration(state));
            init.getInitialStates().add(is);
        }
    }

    private String suggestStateMigration(String state) {
        return switch (state) {
            case "disabled" -> "Adicionar [disabled]='true' ou readonly no input";
            case "hidden" -> "Usar *ngIf='false' ou [hidden]='true' no template";
            case "readonly" -> "Adicionar readonly no input ou [readOnly]='true'";
            default -> "Verificar estado inicial do componente no template Angular";
        };
    }

    private boolean isInRange(int pos, List<int[]> ranges) {
        for (int[] range : ranges) {
            if (pos >= range[0] && pos <= range[1]) return true;
        }
        return false;
    }

    // ── Button State Rules (AfterScroll + Click handlers) ──────────────────

    /**
     * Extrai regras de estado de botões combinando:
     * 1. AfterScroll → EnableComponent(button, condition) → quando habilitado
     * 2. Click handlers → o que cada botão faz (confirmação, ação, navegação)
     */
    public List<ButtonStateRule> extractButtonStateRules(String src) {
        Map<String, ButtonStateRule> ruleMap = new LinkedHashMap<>();

        // ── Pass A: AfterScroll → EnableComponent ──
        extractEnableComponentFromAfterScroll(src, ruleMap);

        // ── Pass B: Click handlers → ações ──
        extractClickHandlerActions(src, ruleMap);

        // ── Pass C: Gera migration hints ──
        for (ButtonStateRule rule : ruleMap.values()) {
            generateMigrationHints(rule);
        }

        return new ArrayList<>(ruleMap.values());
    }

    private void extractEnableComponentFromAfterScroll(String src, Map<String, ButtonStateRule> ruleMap) {
        // Encontra todos os métodos AfterScroll
        Pattern afterScrollPattern = Pattern.compile(
                "(?i)procedure\\s+\\w+\\.(\\w+AfterScroll)\\s*\\([^)]*\\)\\s*;[\\s\\S]*?(?=^(?:procedure|function|initialization|finalization|end\\.))",
                Pattern.MULTILINE);
        Matcher mm = afterScrollPattern.matcher(src);

        while (mm.find()) {
            String methodName = mm.group(1);
            String body = mm.group(0);
            if (body.length() > 15000) body = body.substring(0, 15000);

            // Extrai nome do dataset do método (cdsPedidosAutomaticosAfterScroll → cdsPedidosAutomaticos)
            String dataset = methodName.replaceAll("(?i)AfterScroll$", "");

            // Encontra cada EnableComponent usando scanner de parênteses balanceados
            Pattern enableStart = Pattern.compile("(?i)TLogusWinControl\\.EnableComponent\\s*\\(");
            Matcher em = enableStart.matcher(body);

            while (em.find()) {
                int openParen = em.end() - 1; // posição do '('
                String args = extractBalancedParens(body, openParen);
                if (args == null) continue;

                // Separa buttonName da condition no primeiro ',' top-level
                int firstComma = findTopLevelComma(args);
                if (firstComma < 0) continue;

                String buttonName = args.substring(0, firstComma).trim();
                String condition = args.substring(firstComma + 1).trim();

                ButtonStateRule rule = ruleMap.computeIfAbsent(buttonName.toLowerCase(),
                        k -> { ButtonStateRule r = new ButtonStateRule(); r.setButtonName(buttonName); return r; });
                rule.setDataset(dataset);
                rule.setEnableConditionRaw(condition);

                // Extrai condições individuais (split por 'and'/'or' top-level)
                parseEnableConditions(condition, rule);

                // Extrai field references: FieldByName('campo')
                Matcher fm = Pattern.compile("(?i)FieldByName\\s*\\(\\s*'([^']+)'\\s*\\)").matcher(condition);
                while (fm.find()) {
                    String field = fm.group(1);
                    if (!rule.getFieldReferences().contains(field)) {
                        rule.getFieldReferences().add(field);
                    }
                }

                // Extrai permissões: Parametros.X.Y.Z
                Matcher pm = Pattern.compile("(?i)(Parametros(?:\\.\\w+)+)").matcher(condition);
                if (pm.find()) {
                    rule.setRequiresPermission(pm.group(1));
                }
            }
        }
    }

    private void extractClickHandlerActions(String src, Map<String, ButtonStateRule> ruleMap) {
        // Encontra todos os métodos bbtXxxClick
        Pattern clickPattern = Pattern.compile(
                "(?i)procedure\\s+\\w+\\.(bbt\\w+Click)\\s*\\([^)]*\\)\\s*;[\\s\\S]*?(?=^(?:procedure|function|initialization|finalization|end\\.))",
                Pattern.MULTILINE);
        Matcher mm = clickPattern.matcher(src);

        while (mm.find()) {
            String methodName = mm.group(1);
            String body = mm.group(0);
            if (body.length() > 10000) body = body.substring(0, 10000);

            // bbtCancelarClick → bbtCancelar
            String buttonName = methodName.replaceAll("(?i)Click$", "");

            ButtonStateRule rule = ruleMap.computeIfAbsent(buttonName.toLowerCase(),
                    k -> { ButtonStateRule r = new ButtonStateRule(); r.setButtonName(buttonName); return r; });
            rule.setSourceMethod(methodName);

            // Detecta confirmação: TLogusMessage.Confirm('...' + expr + '...')
            Matcher cm = Pattern.compile("(?i)TLogusMessage\\.Confirm\\s*\\(").matcher(body);
            if (cm.find()) {
                String rawMsg = extractBalancedParens(body, cm.end() - 1);
                if (rawMsg != null) {
                    rule.setConfirmMessage(cleanConfirmMessage(rawMsg));
                }
            }

            // Detecta tipo de ação e ação principal
            detectActionFromBody(body, buttonName, rule);
        }
    }

    private void detectActionFromBody(String body, String buttonName, ButtonStateRule rule) {
        String lowerBody = body.toLowerCase();

        // Navega para outra tela: TfrmXxx.MakeShowModal ou TfrmXxx.ShowModal
        Matcher navM = Pattern.compile("(?i)(T\\w+)\\.(?:MakeShowModal|ShowModal|Show)\\s*(?:\\(|;)").matcher(body);
        if (navM.find()) {
            String targetForm = navM.group(1);
            // Se o form é o mesmo da unit, ignora (chamada recursiva)
            if (!targetForm.equalsIgnoreCase("Self")) {
                rule.setActionType("navigation");
                rule.setAction(targetForm + "." + (body.contains("MakeShowModal") ? "MakeShowModal" : "ShowModal"));
                return;
            }
        }

        // Relatório: Imprimir, Relatorio
        if (lowerBody.contains(".imprimir") || lowerBody.contains("relatorio")) {
            Matcher repM = Pattern.compile("(?i)(T\\w+)\\.Imprimir\\s*\\(").matcher(body);
            if (repM.find()) {
                rule.setActionType("report");
                rule.setAction(repM.group(1) + ".Imprimir");
                return;
            }
            rule.setActionType("report");
            rule.setAction("Imprimir");
            return;
        }

        // Business method: variável local de tipo TXxx chamando um método
        // Detecta var block: var\n  vNome: TTipo;\n  ...begin
        Matcher varBlock = Pattern.compile("(?is)\\bvar\\b(.*?)\\bbegin\\b").matcher(body);
        if (varBlock.find()) {
            String vars = varBlock.group(1);
            // Extrai variáveis tipadas: vNome: TTipo
            Matcher varM = Pattern.compile("(\\w+)\\s*:\\s*(T\\w+)\\s*;").matcher(vars);
            while (varM.find()) {
                String varName = varM.group(1);
                String varType = varM.group(2);
                // Procura chamada: varName.Method (não Create, não Free)
                Pattern callP = Pattern.compile("(?i)\\b" + Pattern.quote(varName) + "\\.(\\w+)\\s*(?:;|\\()");
                Matcher callM = callP.matcher(body);
                while (callM.find()) {
                    String method = callM.group(1);
                    if (!method.equalsIgnoreCase("Create") && !method.equalsIgnoreCase("Free") &&
                        !method.equalsIgnoreCase("Destroy")) {
                        rule.setActionType("business_method");
                        rule.setAction(varType + "." + method);
                        return;
                    }
                }
            }
        }

        // Pesquisar / Carregar: chamada a método local
        if (lowerBody.contains("carregar") || lowerBody.contains("pesquisar") || lowerBody.contains("listar")) {
            rule.setActionType("search");
            Matcher searchM = Pattern.compile("(?i)(Carregar\\w+|Pesquisar\\w*|Listar\\w*)").matcher(body);
            if (searchM.find()) rule.setAction(searchM.group(1));
            return;
        }

        // Navegação para página: changePage, Novo, Editar
        String btnLower = buttonName.toLowerCase();
        if (btnLower.contains("novo") || btnLower.contains("incluir") || btnLower.contains("editar")) {
            rule.setActionType("crud");
            rule.setAction(btnLower.contains("novo") ? "Novo registro" : "Editar registro");
            return;
        }

        // Sair
        if (btnLower.contains("sair") || btnLower.contains("fechar") || lowerBody.contains("close") || lowerBody.contains("modalresult")) {
            rule.setActionType("navigation");
            rule.setAction("Fechar tela");
            return;
        }
    }

    private void parseEnableConditions(String condition, ButtonStateRule rule) {
        // Divide por 'and' e 'or' preservando a estrutura
        // Remove parênteses externas e normaliza espaços
        String clean = condition.replaceAll("\\s+", " ").trim();
        // Split simples por and/or — cada parte é uma condição individual
        String[] parts = clean.split("(?i)\\b(?:and|or)\\b");
        for (String part : parts) {
            String trimmed = part.trim().replaceAll("^\\(+|\\)+$", "").trim();
            if (!trimmed.isEmpty() && trimmed.length() > 2) {
                rule.getEnableConditions().add(trimmed);
            }
        }
    }

    private String cleanConfirmMessage(String raw) {
        // 'Deseja cancelar ' + variable + '?'  →  "Deseja cancelar {variable}?"
        StringBuilder result = new StringBuilder();
        String[] parts = raw.split("\\+");
        for (String part : parts) {
            String trimmed = part.trim();
            // Primeiro: checa se é FieldByName (contém aspas internas mas não é string literal)
            Matcher fbm = Pattern.compile("(?i)FieldByName\\s*\\(\\s*'([^']+)'\\s*\\)").matcher(trimmed);
            if (fbm.find()) {
                result.append("{").append(fbm.group(1)).append("}");
            } else if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
                // String literal pura: 'texto'
                result.append(trimmed.substring(1, trimmed.length() - 1));
            } else {
                Matcher strLit = Pattern.compile("'([^']*)'").matcher(trimmed);
                if (strLit.find()) {
                    result.append(strLit.group(1));
                } else {
                    // Other variable
                    String varName = trimmed.replaceAll("(?i)\\.As\\w+$", "").trim();
                    if (!varName.isEmpty()) result.append("{").append(varName).append("}");
                }
            }
        }
        return result.toString();
    }

    /**
     * Extrai conteúdo entre parênteses balanceados.
     * @param src texto fonte
     * @param openPos posição do '(' de abertura
     * @return conteúdo entre parênteses (sem os parênteses externos), ou null se não encontrar
     */
    private String extractBalancedParens(String src, int openPos) {
        if (openPos >= src.length() || src.charAt(openPos) != '(') return null;
        int depth = 0;
        for (int i = openPos; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return src.substring(openPos + 1, i);
                }
            }
        }
        return null;
    }

    /** Encontra a posição da primeira vírgula no nível top (depth=0) */
    private int findTopLevelComma(String args) {
        int depth = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) return i;
        }
        return -1;
    }

    private void generateMigrationHints(ButtonStateRule rule) {
        List<String> hints = rule.getMigrationHints();

        if (rule.getConfirmMessage() != null) {
            hints.add("Usar ConfirmationService do PrimeNG: this.confirmationService.confirm({ message: '" +
                       rule.getConfirmMessage() + "', accept: () => { ... } })");
        }

        if (!rule.getEnableConditions().isEmpty()) {
            hints.add("Controlar [disabled] no template com base no registro selecionado da grid");
        }

        if (rule.getRequiresPermission() != null) {
            hints.add("Verificar permissão '" + rule.getRequiresPermission() + "' via endpoint de parâmetros/perfil");
        }

        if ("navigation".equals(rule.getActionType())) {
            hints.add("Usar Router.navigate() ou abrir modal (dialog do PrimeNG)");
        } else if ("business_method".equals(rule.getActionType())) {
            hints.add("Chamar endpoint REST correspondente: " + rule.getAction());
        } else if ("report".equals(rule.getActionType())) {
            hints.add("Gerar relatório via endpoint de report (download PDF/Excel)");
        }

        if (rule.getDataset() != null && !rule.getEnableConditions().isEmpty()) {
            hints.add("No Angular: subscribar ao selecionado$ do Service e recalcular disabled no AfterScroll equivalente");
        }
    }

    // ── Field Validation Rules (ValidacaoOk → per-field structured rules) ──

    /**
     * Extrai validações estruturadas por campo de métodos ValidacaoOk.
     * Detecta: required, cross-field (date range, order), pattern (numeric), custom.
     */
    public List<FieldValidationRule> extractFieldValidationRules(String src) {
        List<FieldValidationRule> rules = new ArrayList<>();

        // Encontra métodos Validacao* e ValidacaoOk*
        Pattern methodPattern = Pattern.compile(
                "(?i)function\\s+\\w+\\.(Validacao\\w*)\\s*(?:\\([^)]*\\))?\\s*:\\s*Boolean\\s*;[\\s\\S]*?(?=^(?:procedure|function|initialization|finalization|end\\.))",
                Pattern.MULTILINE);
        Matcher mm = methodPattern.matcher(src);

        while (mm.find()) {
            String methodName = mm.group(1);
            String body = mm.group(0);
            if (body.length() > 15000) body = body.substring(0, 15000);

            extractFieldValidationsFromBody(body, methodName, rules);
        }

        return rules;
    }

    private void extractFieldValidationsFromBody(String body, String methodName, List<FieldValidationRule> rules) {
        // Padrão 1: campo.Date = 0 ou campo.Text = EmptyStr → REQUIRED
        // if (edtDataEmissaoDe.Date = 0) then ... TLogusMessage.Warning('msg')
        Pattern requiredDatePat = Pattern.compile(
                "(?i)if\\s*\\(\\s*(\\w+)\\.Date\\s*=\\s*0\\s*\\)\\s*then[\\s\\S]*?TLogusMessage\\.Warning\\s*\\(\\s*'([^']*)'");
        Matcher m1 = requiredDatePat.matcher(body);
        while (m1.find()) {
            FieldValidationRule rule = new FieldValidationRule();
            rule.setField(m1.group(1));
            rule.setValidationType("required");
            rule.setMessage(m1.group(2));
            rule.setSourceMethod(methodName);
            rule.setAngularValidator("Validators.required");
            rules.add(rule);
        }

        // Padrão: campo.Text = EmptyStr ou campo.Text = '' → REQUIRED (text field)
        Pattern requiredTextPat = Pattern.compile(
                "(?i)if\\s*\\(\\s*(\\w+)\\.Text\\s*=\\s*(?:EmptyStr|'')\\s*\\)\\s*then[\\s\\S]*?TLogusMessage\\.Warning\\s*\\(\\s*'([^']*)'");
        Matcher m1b = requiredTextPat.matcher(body);
        while (m1b.find()) {
            FieldValidationRule rule = new FieldValidationRule();
            rule.setField(m1b.group(1));
            rule.setValidationType("required");
            rule.setMessage(m1b.group(2));
            rule.setSourceMethod(methodName);
            rule.setAngularValidator("Validators.required");
            rules.add(rule);
        }

        // Padrão: campo.KeyValue = Null → REQUIRED (lookup/combo)
        Pattern requiredLookupPat = Pattern.compile(
                "(?i)if\\s*\\(\\s*(\\w+)\\.KeyValue\\s*=\\s*Null\\s*\\)\\s*then[\\s\\S]*?TLogusMessage\\.Warning\\s*\\(\\s*'([^']*)'");
        Matcher m1c = requiredLookupPat.matcher(body);
        while (m1c.find()) {
            FieldValidationRule rule = new FieldValidationRule();
            rule.setField(m1c.group(1));
            rule.setValidationType("required");
            rule.setMessage(m1c.group(2));
            rule.setSourceMethod(methodName);
            rule.setAngularValidator("Validators.required");
            rules.add(rule);
        }

        // Padrão 2: (campoB.Date - campoA.Date) > N → MAX_RANGE (cross-field)
        Pattern maxRangePat = Pattern.compile(
                "(?i)if\\s*\\(\\s*\\(\\s*(\\w+)\\.Date\\s*-\\s*(\\w+)\\.Date\\s*\\)\\s*>\\s*(\\d+)\\s*\\)\\s*then[\\s\\S]*?TLogusMessage\\.Warning\\s*\\(\\s*'([^']*)'");
        Matcher m2 = maxRangePat.matcher(body);
        while (m2.find()) {
            FieldValidationRule rule = new FieldValidationRule();
            rule.setField(m2.group(1));
            rule.setRelatedField(m2.group(2));
            rule.setValidationType("cross_field");
            rule.setOperator("max_range");
            rule.setValue(m2.group(3));
            rule.setMessage(m2.group(4));
            rule.setSourceMethod(methodName);
            rule.setAngularValidator("Custom validator: maxDateRange(" + m2.group(3) + ")");
            rule.setAngularCode("// Cross-field validator\n" +
                    "static maxDateRange(days: number): ValidatorFn {\n" +
                    "  return (group: AbstractControl): ValidationErrors | null => {\n" +
                    "    const de = group.get('" + toCamelCase(m2.group(2)) + "')?.value;\n" +
                    "    const ate = group.get('" + toCamelCase(m2.group(1)) + "')?.value;\n" +
                    "    if (de && ate && diffDays(ate, de) > " + m2.group(3) + ") {\n" +
                    "      return { maxDateRange: { max: " + m2.group(3) + " } };\n" +
                    "    }\n" +
                    "    return null;\n" +
                    "  };\n" +
                    "}");
            rules.add(rule);
        }

        // Padrão 3: campoB.Date < campoA.Date → DATE_ORDER (cross-field)
        Pattern dateOrderPat = Pattern.compile(
                "(?i)if\\s*\\(\\s*(\\w+)\\.Date\\s*<\\s*(\\w+)\\.Date\\s*\\)\\s*then[\\s\\S]*?TLogusMessage\\.Warning\\s*\\(\\s*'([^']*)'");
        Matcher m3 = dateOrderPat.matcher(body);
        while (m3.find()) {
            FieldValidationRule rule = new FieldValidationRule();
            rule.setField(m3.group(1));
            rule.setRelatedField(m3.group(2));
            rule.setValidationType("cross_field");
            rule.setOperator(">=");
            rule.setMessage(m3.group(3));
            rule.setSourceMethod(methodName);
            rule.setAngularValidator("Custom validator: dateGreaterThanOrEqual('" + toCamelCase(m3.group(2)) + "')");
            rules.add(rule);
        }

        // Padrão 4: StrToInt(campo.Text) com except → PATTERN (numeric only)
        Pattern numericPat = Pattern.compile(
                "(?i)(?:try[\\s\\S]*?)?StrToInt\\s*\\(\\s*(\\w+)\\.Text\\s*\\)[\\s\\S]*?except[\\s\\S]*?TLogusMessage\\.Warning\\s*\\(\\s*'([^']*)'");
        Matcher m4 = numericPat.matcher(body);
        while (m4.find()) {
            FieldValidationRule rule = new FieldValidationRule();
            rule.setField(m4.group(1));
            rule.setValidationType("pattern");
            rule.setValue("numeric_only");
            rule.setMessage(m4.group(2));
            rule.setSourceMethod(methodName);
            rule.setAngularValidator("Validators.pattern('^[0-9]*$')");
            rules.add(rule);
        }

        // Padrão 5: StrToFloat / StrToCurr → decimal pattern
        Pattern decimalPat = Pattern.compile(
                "(?i)(?:try[\\s\\S]*?)?(?:StrToFloat|StrToCurr)\\s*\\(\\s*(\\w+)\\.Text\\s*\\)[\\s\\S]*?except[\\s\\S]*?TLogusMessage\\.Warning\\s*\\(\\s*'([^']*)'");
        Matcher m5 = decimalPat.matcher(body);
        while (m5.find()) {
            FieldValidationRule rule = new FieldValidationRule();
            rule.setField(m5.group(1));
            rule.setValidationType("pattern");
            rule.setValue("decimal");
            rule.setMessage(m5.group(2));
            rule.setSourceMethod(methodName);
            rule.setAngularValidator("Validators.pattern('^[0-9]+(\\\\.[0-9]+)?$')");
            rules.add(rule);
        }

        // Padrão 6: Length(campo.Text) > N ou < N → MAX/MIN LENGTH
        Pattern lengthPat = Pattern.compile(
                "(?i)if\\s*\\(\\s*Length\\s*\\(\\s*(\\w+)\\.Text\\s*\\)\\s*([<>])\\s*(\\d+)\\s*\\)\\s*then[\\s\\S]*?TLogusMessage\\.Warning\\s*\\(\\s*'([^']*)'");
        Matcher m6 = lengthPat.matcher(body);
        while (m6.find()) {
            FieldValidationRule rule = new FieldValidationRule();
            rule.setField(m6.group(1));
            rule.setValidationType("length");
            rule.setOperator(m6.group(2).equals("<") ? "minLength" : "maxLength");
            rule.setValue(m6.group(3));
            rule.setMessage(m6.group(4));
            rule.setSourceMethod(methodName);
            rule.setAngularValidator(m6.group(2).equals("<")
                    ? "Validators.minLength(" + m6.group(3) + ")"
                    : "Validators.maxLength(" + m6.group(3) + ")");
            rules.add(rule);
        }
    }

    private String toCamelCase(String componentName) {
        // edtDataEmissaoDe → dataEmissaoDe (remove prefixo edt/luc/cds/etc)
        String name = componentName.replaceAll("^(?i)(edt|luc|cds|dts|bbt|lbl|grp|grd|pnl|chk)", "");
        if (name.isEmpty()) return componentName;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    // ── CalcCellColors (Grid cell color coding) ─────────────────────────────

    /**
     * Extrai lógica de colorização condicional de grids (CalcCellColors events).
     * Detecta: campo de condição, mapeamento valor→cor, e gera [ngClass] sugerido.
     */
    public List<CalcCellColorRule> extractCalcCellColorRules(String src) {
        List<CalcCellColorRule> rules = new ArrayList<>();

        // Encontra métodos CalcCellColors
        Pattern methodPattern = Pattern.compile(
                "(?i)procedure\\s+\\w+\\.(\\w+CalcCellColors)\\s*\\([^)]*\\)\\s*;[\\s\\S]*?(?=^(?:procedure|function|initialization|finalization|end\\.))",
                Pattern.MULTILINE);
        Matcher mm = methodPattern.matcher(src);

        while (mm.find()) {
            String methodName = mm.group(1);
            String body = mm.group(0);
            if (body.length() > 10000) body = body.substring(0, 10000);

            // Extrai grid name: grdPedidoAutomaticoCalcCellColors → grdPedidoAutomatico
            String gridName = methodName.replaceAll("(?i)CalcCellColors$", "");

            CalcCellColorRule rule = new CalcCellColorRule();
            rule.setGridName(gridName);

            // Detecta campo de condição: FieldByName('campo').AsInteger ou .AsString
            Matcher fieldM = Pattern.compile("(?i)(?:case\\s+)?\\w+\\.FieldByName\\s*\\(\\s*'([^']+)'\\s*\\)\\.As(Integer|String)").matcher(body);
            if (fieldM.find()) {
                rule.setConditionField(fieldM.group(1));
            }

            // Detecta case/of com cores: N: AFont.Color := clXxx
            Pattern casePat = Pattern.compile("(?i)(\\d+)\\s*:\\s*AFont\\.Color\\s*:=\\s*(cl\\w+)");
            Matcher cm = casePat.matcher(body);
            while (cm.find()) {
                String value = cm.group(1);
                String delphiColor = cm.group(2);
                String cssColor = mapDelphiColor(delphiColor);
                String cssClass = "text-" + cssColor;
                rule.getColorMappings().add(new CalcCellColorRule.ColorMapping(value, cssColor, cssClass, null));
            }

            // Detecta if/then com cores: if (campo = N) then AFont.Color := clXxx
            Pattern ifColorPat = Pattern.compile("(?i)(?:=\\s*(\\d+)|'([^']+)')\\s*(?:then|:)\\s*[\\s\\S]*?AFont\\.Color\\s*:=\\s*(cl\\w+)");
            if (rule.getColorMappings().isEmpty()) {
                Matcher icm = ifColorPat.matcher(body);
                while (icm.find()) {
                    String value = icm.group(1) != null ? icm.group(1) : icm.group(2);
                    String delphiColor = icm.group(3);
                    String cssColor = mapDelphiColor(delphiColor);
                    rule.getColorMappings().add(new CalcCellColorRule.ColorMapping(value, cssColor, "text-" + cssColor, null));
                }
            }

            // Tenta associar labels de legenda (lblVerde, lblAzul etc)
            enrichWithLegendLabels(src, rule);

            // Gera código Angular sugerido
            if (!rule.getColorMappings().isEmpty()) {
                rule.setAngularCode(generateAngularColorCode(rule));
                rules.add(rule);
            }
        }

        return rules;
    }

    private void enrichWithLegendLabels(String src, CalcCellColorRule rule) {
        // Procura labels como lblVerde, lblAzul no type section
        // E tenta mapear pela cor: verde→green, azul→blue
        Map<String, String> colorToLabel = new LinkedHashMap<>();

        // Procura labels com Caption no DFM ou Text atribuído
        // Padrão: lblVerde: TLabel / lblAzul: TLabel no type section
        // E depois Caption = 'Automático' no DFM
        // Como estamos no .pas, procura atribuições: lblVerde.Caption := 'Automático'
        Pattern labelPat = Pattern.compile("(?i)(lbl\\w+)\\.Caption\\s*:=\\s*'([^']*)'");
        Matcher lm = labelPat.matcher(src);
        while (lm.find()) {
            String labelName = lm.group(1).toLowerCase();
            String caption = lm.group(2);
            // Mapeia nome do label para cor
            if (labelName.contains("verde") || labelName.contains("green")) colorToLabel.put("green", caption);
            else if (labelName.contains("azul") || labelName.contains("blue")) colorToLabel.put("blue", caption);
            else if (labelName.contains("amarelo") || labelName.contains("yellow")) colorToLabel.put("yellow", caption);
            else if (labelName.contains("laranja") || labelName.contains("orange")) colorToLabel.put("orange", caption);
            else if (labelName.contains("vermelho") || labelName.contains("red")) colorToLabel.put("red", caption);
        }

        // Também procura labels declaradas no type section: lblVerde: TLabel → tenta pegar
        // caption do DFM que pode estar na mesma string (menos provável no .pas)
        // Tenta pelo nome do label que contém a cor + "Automatico", "Manual" etc
        // Procura variáveis com nomes sugestivos: lblAutomatico, lblManual, lblTemporario
        Pattern typeLabelPat = Pattern.compile("(?i)(lbl(\\w+))\\s*:\\s*TLabel");
        Matcher tlm = typeLabelPat.matcher(src);
        while (tlm.find()) {
            String labelName = tlm.group(1).toLowerCase();
            String labelText = tlm.group(2);
            // Se o label NÃO é uma cor, pode ser a legenda de uma cor
            if (!labelName.contains("verde") && !labelName.contains("azul") &&
                !labelName.contains("amarelo") && !labelName.contains("laranja") &&
                !labelName.contains("vermelho") && !labelName.contains("data") &&
                !labelName.contains("filial") && !labelName.contains("secao") &&
                !labelName.contains("tipo") && !labelName.contains("situacao") &&
                !labelName.contains("hint") && !labelName.contains("numero") &&
                !labelName.contains("previsao") && !labelName.contains("fornecedor")) {
                // É um label de legenda potencial como lblAutomatico, lblManual
                // Não temos como mapear valor→label sem contexto adicional, mas registramos
            }
        }

        // Associa labels encontradas aos color mappings
        for (CalcCellColorRule.ColorMapping mapping : rule.getColorMappings()) {
            String label = colorToLabel.get(mapping.getColor());
            if (label != null) {
                mapping.setLabel(label);
            }
        }
    }

    private String generateAngularColorCode(CalcCellColorRule rule) {
        StringBuilder sb = new StringBuilder();
        sb.append("// No template do Grid, adicionar [ngClass] na coluna:\n");
        sb.append("// [ngClass]=\"getColorClass(row.").append(rule.getConditionField()).append(")\"\n\n");
        sb.append("getColorClass(value: number): string {\n");
        sb.append("  switch (value) {\n");
        for (CalcCellColorRule.ColorMapping mapping : rule.getColorMappings()) {
            sb.append("    case ").append(mapping.getValue()).append(": return '").append(mapping.getCssClass()).append("';");
            if (mapping.getLabel() != null) {
                sb.append(" // ").append(mapping.getLabel());
            }
            sb.append("\n");
        }
        sb.append("    default: return '';\n");
        sb.append("  }\n");
        sb.append("}");
        return sb.toString();
    }

    private String mapDelphiColor(String delphiColor) {
        if (delphiColor == null) return "inherit";
        return switch (delphiColor.toLowerCase()) {
            case "clgreen" -> "green";
            case "clblue" -> "blue";
            case "clyellow" -> "yellow";
            case "clred" -> "red";
            case "clwhite" -> "white";
            case "clblack" -> "black";
            case "clgray", "clgrey" -> "gray";
            case "clnavy" -> "navy";
            case "clmaroon" -> "maroon";
            case "clpurple" -> "purple";
            case "clteal" -> "teal";
            case "clolive" -> "olive";
            case "claqua" -> "aqua";
            case "clfuchsia" -> "fuchsia";
            case "clsilver" -> "silver";
            case "cllime" -> "lime";
            case "clwindowtext" -> "inherit";
            case "clwindow" -> "transparent";
            case "clweborange" -> "orange";
            case "clwebgreen" -> "green";
            default -> delphiColor.replaceAll("(?i)^cl", "").toLowerCase();
        };
    }

    // ── Business Rules ───────────────────────────────────────────────────────

    public List<BusinessRule> extractBusinessRules(String src) {
        List<BusinessRule> rules = new ArrayList<>();
        int idx = 0;

        // Validações com ShowMessage / raise Exception
        Matcher m1 = IF_VALIDATION_PATTERN.matcher(src);
        while (m1.find()) {
            BusinessRule rule = new BusinessRule();
            rule.setId("BR_" + (++idx));
            rule.setRuleType("validation");
            rule.setDescription("Validação: " + m1.group(1).trim().replaceAll("\\s+", " "));
            String collapsed = m1.group(0).replaceAll("\\s+", " ");
            rule.setSourceCode(collapsed.substring(0, Math.min(200, collapsed.length())));
            rule.setComplexity(estimateComplexity(m1.group(1)));
            rule.setMigrationStrategy("Mover para camada de Service (@Service) com Bean Validation ou validação manual");
            rule.setSuggestedJavaCode(generateValidationJava(m1.group(1), m1.group(2)));
            rules.add(rule);
        }

        // Cálculos (atribuições numéricas complexas)
        Pattern calcPattern = Pattern.compile("(?i)\\w+\\s*:=\\s*[\\w\\s+\\-*/(),.]+(?:[+\\-*/])[\\w\\s+\\-*/(),.]+;");
        Matcher m2 = calcPattern.matcher(src);
        while (m2.find()) {
            String expr = m2.group(0);
            if (expr.length() > 30) { // só expressões complexas
                BusinessRule rule = new BusinessRule();
                rule.setId("BR_" + (++idx));
                rule.setRuleType("calculation");
                rule.setDescription("Cálculo: " + expr.substring(0, Math.min(80, expr.length())));
                rule.setSourceCode(expr);
                rule.setComplexity("medium");
                rule.setMigrationStrategy("Extrair para método de Service com testes unitários");
                rules.add(rule);
            }
        }

        // Item 4: deduplicar regras pelo sourceCode (mantém a primeira ocorrência)
        Set<String> seenRules = new LinkedHashSet<>();
        List<BusinessRule> dedupedRules = new ArrayList<>();
        for (BusinessRule rule : rules) {
            String key = rule.getSourceCode() != null ? rule.getSourceCode().trim() : rule.getDescription();
            if (seenRules.add(key)) {
                dedupedRules.add(rule);
            }
        }
        // Re-numera IDs
        for (int i = 0; i < dedupedRules.size(); i++) {
            dedupedRules.get(i).setId("BR_" + (i + 1));
        }
        return dedupedRules;
    }

    private String generateValidationJava(String condition, String action) {
        String msg = "";
        Matcher msgM = Pattern.compile("'([^']+)'").matcher(action);
        if (msgM.find()) msg = msgM.group(1);

        String cond = condition.trim();

        // Detecta padrões que são UI/frontend (não migrar para Service)
        if (cond.contains("TLogusMessage.Confirm") || cond.contains("MessageDlg") ||
            cond.contains("Application.MessageBox")) {
            // Extrai a mensagem da confirmação
            Matcher confirmMsg = Pattern.compile("'([^']+)'").matcher(cond);
            String confirmText = confirmMsg.find() ? confirmMsg.group(1) : msg;
            return "// ⚠️ FRONTEND: Confirmação do usuário — implementar no Angular (dialog/confirm)\n" +
                   "// Mensagem: \"" + confirmText + "\"\n" +
                   "// No Angular: usar ConfirmationService do PrimeNG\n" +
                   "// this.confirmationService.confirm({ message: '" + confirmText + "', accept: () => { ... } });";
        }

        // Detecta ShowMessage/Warning (apenas exibição, não validação de backend)
        if (cond.contains("ShowMessage") || action.contains("ShowMessage") ||
            cond.contains("TLogusMessage.Warning")) {
            return "// ⚠️ FRONTEND: Mensagem de aviso — implementar no Angular\n" +
                   "// this.messageService.add({ severity: 'warn', summary: 'Aviso', detail: '" + msg + "' });";
        }

        // Detecta TLogusWinControl (verificação de estado de componente UI)
        if (cond.contains("TLogusWinControl") || cond.contains("EnabledComponent") ||
            cond.contains(".Enabled") || cond.contains(".Visible")) {
            return "// ⚠️ FRONTEND: Verificação de estado de componente UI — não migrar para Service\n" +
                   "// No Angular: usar [disabled] binding ou *ngIf no template\n" +
                   "// Condição original: " + cond;
        }

        // Detecta isEmpty (validação real de backend)
        if (cond.contains("IsEmpty") || cond.contains(".IsEmpty")) {
            String entity = cond.replaceAll(".*?(\\w+)\\.IsEmpty.*", "$1");
            return "// Validação backend\n" +
                   "if (lista == null || lista.isEmpty()) {\n" +
                   "    throw new ValidationException(\"Nenhum registro encontrado.\");\n" +
                   "}";
        }

        // Validação genérica
        String javaCond = cond.replace("not ", "!")
                              .replace("(!", "(!")
                              .replace(":=", "=");
        return "// Validação backend\n" +
               "if (" + javaCond + ") {\n" +
               "    throw new ValidationException(\"" + (msg.isEmpty() ? "Validação falhou" : msg) + "\");\n" +
               "}";
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String detectUnitType(String src, DelphiUnit unit) {
        String lower = src.toLowerCase();
        if (lower.contains("= class(tform)") || lower.contains("= class(tframe)")) return "form";
        if (lower.contains("= class(tdatamodule)")) return "datamodule";
        if (lower.contains("= class(tthread)")) return "thread";
        if (lower.contains("= class(tservice)")) return "service";
        return "class";
    }

    private String detectClassType(String name, String parent) {
        if (parent == null) return "class";
        String p = parent.toLowerCase();
        if (p.contains("tform")) return "TForm";
        if (p.contains("tdatamodule")) return "TDataModule";
        if (p.contains("tthread")) return "TThread";
        if (p.contains("tframe")) return "TFrame";
        if (p.contains("tinterfacedobject") || p.contains("tinterfaced")) return "Interface";
        return parent.trim();
    }

    private String suggestClassMigration(String classType, String name) {
        return switch (classType) {
            case "TForm"        -> "@Component Angular: " + toKebabCase(name.replace("TForm", "").replace("frm", "").replace("Form", "")) + ".component";
            case "TDataModule"  -> "@Service Spring Boot: " + name.replace("TDM", "").replace("DataModule", "") + "Service";
            case "TThread"      -> "@Async Spring + CompletableFuture ou @Scheduled";
            case "TFrame"       -> "Angular Shared Component / Reusable Component";
            default             -> "@Component ou @Service Spring Boot";
        };
    }

    private String mapDelphiTypeToJava(String delphiType) {
        if (delphiType == null) return "void";
        return switch (delphiType.toLowerCase()) {
            case "integer", "smallint", "shortint", "byte", "word", "longint", "cardinal" -> "Integer";
            case "int64", "uint64", "qword" -> "Long";
            case "string", "ansistring", "widestring", "shortstring" -> "String";
            case "char", "ansichar", "widechar" -> "Character";
            case "boolean" -> "Boolean";
            case "double", "extended", "real" -> "Double";
            case "single" -> "Float";
            case "currency" -> "BigDecimal";
            case "tdatetime", "tdate", "ttime" -> "LocalDateTime";
            case "variant" -> "Object";
            case "tobject" -> "Object";
            default -> delphiType; // mantém o nome para tipos customizados
        };
    }

    private boolean isComponentType(String type) {
        return type != null && (
                type.startsWith("T") && (
                        type.contains("Query") || type.contains("Table") ||
                        type.contains("Button") || type.contains("Edit") ||
                        type.contains("Label") || type.contains("Grid") ||
                        type.contains("Panel") || type.contains("Memo") ||
                        type.contains("Image") || type.contains("Timer") ||
                        type.contains("Connection") || type.contains("DataSource") ||
                        type.contains("Database") || type.contains("ClientDataSet") ||
                        type.contains("DataSetProvider") || type.contains("StoredProc") ||
                        type.contains("CheckBox") || type.contains("ComboBox") ||
                        type.contains("QuickRep") || type.contains("QR")
                )
        );
    }

    private String estimateComplexity(String condition) {
        long andOr = condition.chars().filter(c -> c == 'a').count(); // rough
        int len = condition.length();
        if (len < 50) return "low";
        if (len < 150) return "medium";
        return "high";
    }

    private boolean isReservedWord(String word) {
        Set<String> reserved = Set.of("if", "then", "else", "begin", "end", "for", "while",
                "do", "repeat", "until", "case", "of", "with", "try", "except",
                "finally", "raise", "class", "record", "array", "set", "type",
                "var", "const", "procedure", "function", "unit", "uses", "interface",
                "implementation", "initialization", "finalization", "inherited", "self");
        return reserved.contains(word.toLowerCase());
    }

    private String toPascalCase(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private String toKebabCase(String s) {
        return s.replaceAll("([A-Z])", "-$1").toLowerCase().replaceAll("^-", "");
    }

    private int countOccurrences(String text, String sub) {
        int count = 0, idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) { count++; idx += sub.length(); }
        return count;
    }
}
