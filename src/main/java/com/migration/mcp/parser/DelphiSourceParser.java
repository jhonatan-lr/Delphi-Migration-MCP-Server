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

        // Constantes e tipos enum locais
        unit.setConstants(extractConstants(cleaned));
        unit.setEnumTypes(extractEnumTypes(cleaned));

        // Regras de estado de botões (AfterScroll + Click handlers)
        List<ButtonStateRule> btnRules = extractButtonStateRules(cleaned);
        if (!btnRules.isEmpty()) unit.setButtonStateRules(btnRules);

        // Regras de colorização de células (CalcCellColors)
        List<CalcCellColorRule> colorRules = extractCalcCellColorRules(cleaned);
        if (!colorRules.isEmpty()) unit.setCalcCellColorRules(colorRules);

        // Inicialização de formulário (FormShow/FormCreate)
        List<FormInitialization> formInits = extractFormInitialization(cleaned);
        if (!formInits.isEmpty()) unit.setFormInitializations(formInits);

        // Fronteiras de transação (StartTransaction/Commit/Rollback → @Transactional)
        List<TransactionBoundary> txBoundaries = extractTransactionBoundaries(cleaned);
        if (!txBoundaries.isEmpty()) unit.setTransactionBoundaries(txBoundaries);

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
            // Only check at word boundaries (previous char not alphanumeric/_)
            if (i == 0 || (!Character.isLetterOrDigit(src.charAt(i - 1)) && src.charAt(i - 1) != '_')) {
                String sub = src.substring(i).toLowerCase();
                // "class" removed intentionally — class procedure/function falsely incremented depth
                if (sub.startsWith("begin") && !isWordChar(src, i + 5)) {
                    depth++;
                } else if (sub.startsWith("record") && !isWordChar(src, i + 6)) {
                    depth++;
                } else if (sub.startsWith("end") && !isWordChar(src, i + 3)) {
                    if (depth <= 1) break;
                    depth--;
                }
            }
            i++;
            if (sb.length() > 20000) break; // limit
        }
        return sb.toString();
    }

    /** True se o caractere na posição idx é letra, dígito ou underscore */
    private boolean isWordChar(String s, int idx) {
        if (idx >= s.length()) return false;
        char c = s.charAt(idx);
        return Character.isLetterOrDigit(c) || c == '_';
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
            // Captura campos F-prefixados com multi-var: FNome1, FNome2: Tipo;
            Pattern fMultiPattern = Pattern.compile(
                "(?i)^\\s*(F[A-Za-z]\\w*(?:\\s*,\\s*F[A-Za-z]\\w*)*)\\s*:\\s*([\\w<>\\[\\]]+)\\s*;",
                Pattern.MULTILINE);
            Matcher fmm = fMultiPattern.matcher(sm.group(1));
            while (fmm.find()) {
                String[] names = fmm.group(1).split("\\s*,\\s*");
                String delphiType = fmm.group(2);
                for (String rawName : names) {
                    final String name = rawName.trim();
                    if (name.isEmpty() || isReservedWord(name)) continue;
                    boolean alreadyAdded = fields.stream().anyMatch(f -> f.getName().equalsIgnoreCase(name));
                    if (!alreadyAdded) {
                        DelphiField f = new DelphiField();
                        f.setName(name);
                        f.setDelphiType(delphiType);
                        f.setJavaType(mapDelphiTypeToJava(delphiType));
                        f.setComponent(false); // F-prefix = estado interno, nao componente visual
                        fields.add(f);
                    }
                }
            }
        }
        // Fallback: scan entire block for F-prefixed fields that section detection may have missed
        Pattern fFallbackPattern = Pattern.compile(
            "(?i)^\\s*(F[A-Za-z]\\w*)\\s*:\\s*([\\w<>\\[\\]]+)\\s*;",
            Pattern.MULTILINE);
        Matcher ffm = fFallbackPattern.matcher(block);
        while (ffm.find()) {
            String name = ffm.group(1).trim();
            if (isReservedWord(name)) continue;
            boolean alreadyAdded = fields.stream().anyMatch(f -> f.getName().equalsIgnoreCase(name));
            if (!alreadyAdded) {
                DelphiField f = new DelphiField();
                f.setName(name);
                f.setDelphiType(ffm.group(2));
                f.setJavaType(mapDelphiTypeToJava(ffm.group(2)));
                f.setComponent(false);
                fields.add(f);
            }
        }

        return fields;
    }

    private List<DelphiProcedure> extractMethods(String src, String className) {
        List<DelphiProcedure> methods = new ArrayList<>();
        // Group 1: optional "class " modifier; Group 2: procedure|function; Group 3: name; Group 4: params; Group 5: return type
        Pattern classMethodPattern = Pattern.compile(
                "(?i)(class\\s+)?(procedure|function)\\s+" + Pattern.quote(className) + "\\.(\\w+)\\s*(?:\\(([^)]*)\\))?\\s*(?::\\s*(\\w+))?\\s*;[\\s\\S]*?(?=^(?:class\\s+(?:procedure|function)|procedure|function|initialization|finalization|end\\.))",
                Pattern.MULTILINE);

        Matcher m = classMethodPattern.matcher(src);
        while (m.find()) {
            DelphiProcedure proc = new DelphiProcedure();
            proc.setType(m.group(2).toLowerCase());
            proc.setName(m.group(3));
            if (m.group(4) != null) {
                proc.setParameters(Arrays.asList(m.group(4).split(";")));
            }
            proc.setReturnType(m.group(5));
            proc.setJavaReturnType(mapDelphiTypeToJava(m.group(5)));
            // Marca class methods (MakeShowModal, factory methods)
            if (m.group(1) != null) {
                proc.setMigrationNotes("class method — Angular: DialogService.open() ou método estático factory");
            }

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
                // Use full normalized text as dedup key — avoids false dedup when queries share a long prefix
                String key = sql.trim().replaceAll("\\s+", " ");
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

        // Marcadores de fim (Open, ExecSQL, ExecProc, Load — usado por LookupComboEdit)
        Matcher endM = Pattern.compile("(?i)(?:\\.|\\b)(?:Open|ExecSQL|ExecProc|Load)\\b").matcher(src);
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
            // Use full normalized text as dedup key — avoids false dedup on queries sharing a long prefix
            String key = frag.sql.trim().replaceAll("\\s+", " ");
            if (!seen.add(key)) continue;

            SqlQuery q = buildSqlQuery(frag.sql, idx++, src, start, end);
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

    // SQL.Add com concatenação: SQL.Add('...' + expr + '...')
    private static final Pattern SQL_ADD_CONCAT_PATTERN =
            Pattern.compile("(?i)(?:\\.|\\b)SQL\\.Add\\s*\\(\\s*'([^']*)'\\s*\\+\\s*([^)]+?)\\s*(?:\\+\\s*'([^']*)')?\\s*\\)");

    // ParamByName('nome').AsType := expr
    private static final Pattern PARAM_BY_NAME_PATTERN =
            Pattern.compile("(?i)ParamByName\\s*\\(\\s*'(\\w+)'\\s*\\)\\s*\\.(As\\w+)\\s*:=\\s*([^;\\n]+)");

    private SqlQuery buildSqlQuery(String sql, int idx, String src, int pos) {
        return buildSqlQuery(sql, idx, src, pos, -1);
    }

    private SqlQuery buildSqlQuery(String sql, int idx, String src, int pos, int blockEnd) {
        SqlQuery q = new SqlQuery();
        q.setId("SQL_" + (idx + 1));
        q.setSql(sql);
        q.setQueryType(detectQueryType(sql));
        q.setTablesUsed(extractTables(sql));
        q.setContext(extractContext(src, pos));
        q.setJpaEquivalent(suggestJpa(sql));
        q.setRepositoryMethod(suggestRepositoryMethod(sql));

        // Extrai injeções dinâmicas no range do bloco SQL
        extractDynamicInjectionsForQuery(src, pos, q);

        // Extrai tipos dos parâmetros :xxx via ParamByName no método (range limitado ao bloco)
        extractParamTypesForQuery(src, pos, blockEnd, q);

        return q;
    }

    /** Detecta SQL.Add('...' + expr + '...') e adiciona em dynamicInjections */
    private void extractDynamicInjectionsForQuery(String src, int blockStart, SqlQuery q) {
        // Procura no range do bloco (200 chars antes até 3000 chars depois)
        int from = Math.max(0, blockStart - 200);
        int to = Math.min(src.length(), blockStart + 3000);
        String range = src.substring(from, to);
        Matcher m = SQL_ADD_CONCAT_PATTERN.matcher(range);
        while (m.find()) {
            String expr = m.group(2).trim();
            // Limpa trailing parens e espaços
            expr = expr.replaceAll("[)\\s]+$", "").trim();
            if (!expr.isEmpty() && !q.getDynamicInjections().contains(expr)) {
                q.getDynamicInjections().add(expr);
            }
        }

        // Detecta chamadas de metodo auxiliar que recebem componente SQL como argumento
        // Padrao: NomeMetodo(ComponenteSQL) ou NomeMetodo(ComponenteSQL.SQL)
        Pattern auxMethodPattern = Pattern.compile(
            "(?i)\\b([A-Z][A-Za-z]+)\\s*\\(\\s*(\\w+)(?:\\.SQL)?\\s*\\)");
        Matcher am = auxMethodPattern.matcher(range);
        Set<String> builtins = Set.of("sql", "add", "clear", "open", "close", "execsql",
            "showmessage", "messagedlg", "parambyname", "fieldbyname", "format",
            "inttostr", "floattostr", "datetostr", "assigned", "inherited");
        while (am.find()) {
            String methodName = am.group(1);
            String componentArg = am.group(2);
            if (!builtins.contains(methodName.toLowerCase())
                    && !builtins.contains(componentArg.toLowerCase())
                    && Character.isUpperCase(methodName.charAt(0))) {
                String injection = "method:" + methodName + "(" + componentArg + ")";
                if (!q.getDynamicInjections().contains(injection)) {
                    q.getDynamicInjections().add(injection);
                }
            }
        }
    }

    /** Extrai ParamByName('x').AsType := valor e anota params com tipo Java */
    private void extractParamTypesForQuery(String src, int blockStart, SqlQuery q) {
        extractParamTypesForQuery(src, blockStart, -1, q);
    }

    private void extractParamTypesForQuery(String src, int blockStart, int blockEnd, SqlQuery q) {
        // Restringe ao bloco da query para evitar params bleeding de queries vizinhas no mesmo método
        // from = blockStart (não blockStart - 500): ParamByName sempre vem APÓS SQL.Clear, nunca antes
        int from = blockStart;
        int to = blockEnd > blockStart
                ? Math.min(src.length(), blockEnd)   // params must be set BEFORE Open/ExecSQL
                : Math.min(src.length(), blockStart + 4000);
        String range = src.substring(from, to);

        // Mapa de nomes de params já no SQL
        java.util.Set<String> sqlParams = new java.util.LinkedHashSet<>();
        Matcher paramM = Pattern.compile(":(\\w+)").matcher(q.getSql() != null ? q.getSql() : "");
        while (paramM.find()) sqlParams.add(paramM.group(1).toLowerCase());

        Matcher m = PARAM_BY_NAME_PATTERN.matcher(range);
        while (m.find()) {
            String name = m.group(1);
            String asType = m.group(2); // AsInteger, AsString, AsDate, etc.
            String bindExpr = m.group(3).trim();
            String javaType = mapDelphiParamType(asType);

            Map<String, String> param = new java.util.LinkedHashMap<>();
            param.put("name", name);
            param.put("javaType", javaType);
            param.put("delphiAsType", asType);
            param.put("bindExpression", bindExpr);
            q.getParams().add(param);
        }

        // Para params no SQL que não têm ParamByName, inferir pelo nome
        if (!sqlParams.isEmpty()) {
            java.util.Set<String> bound = new java.util.HashSet<>();
            for (Map<String, String> p : q.getParams()) bound.add(p.get("name").toLowerCase());
            for (String pname : sqlParams) {
                if (!bound.contains(pname)) {
                    Map<String, String> param = new java.util.LinkedHashMap<>();
                    param.put("name", pname);
                    param.put("javaType", guessParamType(pname));
                    param.put("delphiAsType", "inferred");
                    q.getParams().add(param);
                }
            }
        }
    }

    private String mapDelphiParamType(String asType) {
        if (asType == null) return "String";
        return switch (asType.toLowerCase()) {
            case "asinteger", "assmallint", "asword", "asbyte" -> "Integer";
            case "asfloat", "ascurrency", "asbcd", "asfmtbcd" -> "BigDecimal";
            case "asstring", "asansistring", "aswidestring", "asmemo" -> "String";
            case "asdate" -> "LocalDate";
            case "asdatetime", "astimestamp" -> "LocalDateTime";
            case "asboolean" -> "Boolean";
            case "aslargeint" -> "Long";
            default -> "String";
        };
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

    // ── Constants & Enum Types ────────────────────────────────────────────────

    /**
     * Extrai constantes do bloco const da unit.
     * Ex: paAutomatico = 1; → {"paAutomatico": "1"}
     */
    public Map<String, String> extractConstants(String src) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        // Encontra blocos const (podem ter vários na unit)
        Pattern constBlock = Pattern.compile(
                "(?i)\\bconst\\b([\\s\\S]*?)(?=\\b(?:type|var|procedure|function|begin|implementation|initialization|finalization)\\b)",
                Pattern.MULTILINE);
        Matcher bm = constBlock.matcher(src);
        while (bm.find()) {
            String block = bm.group(1);
            // Extrai cada linha: NomeDaConstante = Valor ;
            Pattern entry = Pattern.compile(
                    "(?i)^\\s*(\\w+)\\s*=\\s*([^;\\n]+?)\\s*;",
                    Pattern.MULTILINE);
            Matcher em = entry.matcher(block);
            while (em.find()) {
                String name = em.group(1).trim();
                String value = em.group(2).trim();
                if (!isReservedWord(name)) {
                    result.put(name, value);
                }
            }
        }
        return result;
    }

    /**
     * Extrai tipos enum do bloco type da unit.
     * Ex: TFlgTipoPedido = (paAutomatico, paManual, paTemporario);
     * Retorna: [{name, values:[...], javaEnum}]
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> extractEnumTypes(String src) {
        List<Map<String, Object>> result = new ArrayList<>();
        // Tipos enum: NomeType = (valor1, valor2, ...)
        Pattern enumPat = Pattern.compile(
                "(?i)^\\s*(T\\w+)\\s*=\\s*\\(([^)]+)\\)\\s*;",
                Pattern.MULTILINE);
        Matcher m = enumPat.matcher(src);
        while (m.find()) {
            String typeName = m.group(1);
            String valuesStr = m.group(2);
            // Extrai valores (separados por vírgula)
            String[] parts = valuesStr.split(",");
            List<String> values = new ArrayList<>();
            for (String p : parts) {
                String v = p.trim().replaceAll("=.*", "").trim(); // remove = value se houver
                if (!v.isEmpty() && !isReservedWord(v)) values.add(v);
            }
            if (values.size() >= 2) { // só enum real, não tipos de 1 valor
                Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("name", typeName);
                entry.put("values", values);
                // Gera sugestão de enum Java/TypeScript
                StringBuilder javaEnum = new StringBuilder("export enum " + typeName.replaceAll("^T", "") + " {\n");
                for (int i = 0; i < values.size(); i++) {
                    javaEnum.append("  ").append(values.get(i).toUpperCase()).append(" = ").append(i + 1);
                    if (i < values.size() - 1) javaEnum.append(",");
                    javaEnum.append("\n");
                }
                javaEnum.append("}");
                entry.put("tsEnum", javaEnum.toString());
                result.add(entry);
            }
        }
        return result;
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
            "(?i)(\\w+)\\.(Date|Text|KeyValue|Value|Checked|ItemIndex|Caption|EditValue|DisplayValue|SortColumn|SortType|SelectedIndex)\\s*:=\\s*([^;]+);");

    private void extractDefaultValues(String body, String context, FormInitialization init, List<int[]> conditionalRanges) {
        Matcher m = DEFAULT_VALUE_PATTERN.matcher(body);
        while (m.find()) {
            // Pula se está dentro de um range condicional já processado
            if (isInRange(m.start(), conditionalRanges)) continue;

            String component = m.group(1);
            String property = m.group(2);
            String value = m.group(3).trim();
            // Limpa lixo que pode vazar após o valor (except, end, begin, etc)
            value = value.replaceAll("(?s)\\s*(?:except|finally|end\\b|begin\\b).*", "").trim();
            if (value.isEmpty()) continue;

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
            // 'Abre' prefix added to capture AbreQuery and similar delegation calls
            "(?i)(?:^|;|\\bthen\\b|\\bdo\\b|\\bbegin\\b)\\s*((?:Carregar|Pesquisar|Listar|Load|Buscar|Atualizar|Consultar|Preencher|Montar|Popular|Refresh|Inicializa|Iniciar|Init|Setup|Abre)\\w*)\\s*(?:\\([^)]*\\))?\\s*;",
            Pattern.MULTILINE);

    /** Direct dataset .Open() calls: QR_xxx.Open; dsXxx.Open; cdsXxx.Open; vQry.Open */
    private static final Pattern DATASET_OPEN_PATTERN = Pattern.compile(
            "(?i)(?:^|;|\\bthen\\b|\\bdo\\b|\\bbegin\\b)\\s*((?:QR_|ds|cds|vQry|\\w*Query)\\w*)\\.Open\\s*;",
            Pattern.MULTILINE);

    private void extractAutoLoads(String body, String context, FormInitialization init) {
        Set<String> seen = new HashSet<>();

        // Named method calls (Carregar*, Pesquisar*, AbreQuery*, etc.)
        Matcher m = AUTO_LOAD_PATTERN.matcher(body);
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

        // Direct dataset .Open() calls (QR_ClientesPag.Open, vQry.Open, etc.)
        Matcher openM = DATASET_OPEN_PATTERN.matcher(body);
        while (openM.find()) {
            String component = openM.group(1);
            String key = component.toLowerCase() + ".open";
            if (!seen.add(key)) continue;

            FormInitialization.AutoLoad al = new FormInitialization.AutoLoad();
            al.setMethod(component + ".Open");
            al.setContext(context);
            al.setDescription("Dataset " + component + " carregado ao abrir a tela");
            al.setMigration("Chamar endpoint de pesquisa no ngAfterViewInit para pré-carregar " + component);
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

        // Padrão 4: chamadas a métodos de configuração de estado (HabilitaComponentes, AtualizaEstado, etc.)
        Pattern stateMethodPattern = Pattern.compile(
                "(?i)(?:^|;|\\bthen\\b|\\bdo\\b|\\bbegin\\b)\\s*((?:Habilita|HabilitaComponentes|HabilitaBotoes|AtualizaEstado|ConfiguraComponentes|ConfiguraEstado|AtualizaComponentes|AjustaComponentes)\\w*)\\s*(?:\\([^)]*\\))?\\s*;",
                Pattern.MULTILINE);
        Matcher m4 = stateMethodPattern.matcher(body);
        Set<String> seenStateMethods = new HashSet<>();
        while (m4.find()) {
            String methodCall = m4.group(1);
            if (!seenStateMethods.add(methodCall.toLowerCase())) continue;
            FormInitialization.InitialState is = new FormInitialization.InitialState();
            is.setComponent(methodCall);
            is.setState("setup_via_method");
            is.setDescription("Estado inicial configurado via " + methodCall + "() — habilita/desabilita componentes dinamicamente");
            is.setMigration("Implementar lógica de " + methodCall + "() no ngOnInit() — chamar handleEstadoInicial() no Service");
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

        // ── Pass D: DataChange handlers → EnableComponent ──
        extractEnableComponentFromDataChange(src, ruleMap);

        // ── Pass E: Helper methods (Habilita*, HabilitaBotoes*, etc.) → EnableComponent ──
        extractEnableComponentFromHelperMethods(src, ruleMap);

        // ── Pass F: Generic event handlers (FormShow, FormCreate, tvwXxx_PChange, etc.) ──
        extractEnableComponentFromGenericHandlers(src, ruleMap);

        // ── Pass C: Gera migration hints ──
        for (ButtonStateRule rule : ruleMap.values()) {
            generateMigrationHints(rule);
        }

        return new ArrayList<>(ruleMap.values());
    }

    /**
     * Shared helper: scans a method body for TLogusWinControl.EnableComponent calls
     * and populates ruleMap with button enable conditions.
     */
    private void extractEnableComponentCalls(String body, String dataset, Map<String, ButtonStateRule> ruleMap) {
        Pattern enableStart = Pattern.compile("(?i)TLogusWinControl\\.EnableComponent\\s*\\(");
        Matcher em = enableStart.matcher(body);

        while (em.find()) {
            int openParen = em.end() - 1;
            String args = extractBalancedParens(body, openParen);
            if (args == null) continue;

            int firstComma = findTopLevelComma(args);
            if (firstComma < 0) continue;

            String buttonName = args.substring(0, firstComma).trim();
            String condition = args.substring(firstComma + 1).trim();

            ButtonStateRule rule = ruleMap.computeIfAbsent(buttonName.toLowerCase(),
                    k -> { ButtonStateRule r = new ButtonStateRule(); r.setButtonName(buttonName); return r; });
            if (rule.getDataset() == null) rule.setDataset(dataset);
            rule.setEnableConditionRaw(condition);
            parseEnableConditions(condition, rule);

            Matcher fm = Pattern.compile("(?i)FieldByName\\s*\\(\\s*'([^']+)'\\s*\\)").matcher(body);
            while (fm.find()) {
                String field = fm.group(1);
                if (!rule.getFieldReferences().contains(field)) {
                    rule.getFieldReferences().add(field);
                }
            }

            Matcher pm = Pattern.compile("(?i)(Parametros(?:\\.\\w+)+)").matcher(condition);
            if (pm.find()) {
                rule.setRequiresPermission(pm.group(1));
            }
        }
    }

    /**
     * Scans a method body for direct conditional Enabled assignments of button-like components:
     *   bbtFoo.Enabled := <non-literal expression>
     * Complements extractEnableComponentCalls for code that does not use TLogusWinControl.EnableComponent.
     */
    private void extractDirectEnabledAssignments(String body, String dataset, Map<String, ButtonStateRule> ruleMap) {
        Pattern enabledPat = Pattern.compile(
            "(?i)(\\w+)\\.Enabled\\s*:=\\s*([^;\\r\\n]{5,});",
            Pattern.MULTILINE);
        Matcher em = enabledPat.matcher(body);
        while (em.find()) {
            String buttonName = em.group(1);
            String condition  = em.group(2).trim();
            // Skip simple boolean literals — those are initial states already handled elsewhere
            if (condition.equalsIgnoreCase("true") || condition.equalsIgnoreCase("false")) continue;
            // Accept button components AND common input/visual components that have Enabled logic
            String lower = buttonName.toLowerCase();
            if (!lower.startsWith("bbt") && !lower.startsWith("btn") && !lower.startsWith("sbt") &&
                !lower.startsWith("bit") && !lower.startsWith("pbt") && !lower.startsWith("edt") &&
                !lower.startsWith("chk") && !lower.startsWith("cbb") && !lower.startsWith("dtp") &&
                !lower.startsWith("grp") && !lower.startsWith("pnl") && !lower.startsWith("luc") &&
                !lower.startsWith("grd") && !lower.startsWith("dbg") &&
                !lower.contains("button") && !lower.contains("edit") && !lower.contains("combo")) continue;

            ButtonStateRule rule = ruleMap.computeIfAbsent(buttonName.toLowerCase(),
                k -> { ButtonStateRule r = new ButtonStateRule(); r.setButtonName(buttonName); return r; });
            if (rule.getDataset() == null) rule.setDataset(dataset);
            rule.setEnableConditionRaw(condition);
            parseEnableConditions(condition, rule);
        }
    }

    private void extractEnableComponentFromAfterScroll(String src, Map<String, ButtonStateRule> ruleMap) {
        Pattern afterScrollPattern = Pattern.compile(
                "(?i)procedure\\s+\\w+\\.(\\w+AfterScroll)\\s*\\([^)]*\\)\\s*;[\\s\\S]*?(?=^(?:procedure|function|initialization|finalization|end\\.))",
                Pattern.MULTILINE);
        Matcher mm = afterScrollPattern.matcher(src);
        while (mm.find()) {
            String methodName = mm.group(1);
            String body = mm.group(0);
            if (body.length() > 15000) body = body.substring(0, 15000);
            String dataset = methodName.replaceAll("(?i)AfterScroll$", "");
            extractEnableComponentCalls(body, dataset, ruleMap);
            extractDirectEnabledAssignments(body, dataset, ruleMap);
        }
    }

    /** Pass D: DataChange event handlers also carry EnableComponent logic */
    private void extractEnableComponentFromDataChange(String src, Map<String, ButtonStateRule> ruleMap) {
        Pattern dataChangePattern = Pattern.compile(
                "(?i)procedure\\s+\\w+\\.(\\w+DataChange)\\s*\\([^)]*\\)\\s*;[\\s\\S]*?(?=^(?:procedure|function|initialization|finalization|end\\.))",
                Pattern.MULTILINE);
        Matcher mm = dataChangePattern.matcher(src);
        while (mm.find()) {
            String methodName = mm.group(1);
            String body = mm.group(0);
            if (body.length() > 15000) body = body.substring(0, 15000);
            String dataset = methodName.replaceAll("(?i)DataChange$", "");
            extractEnableComponentCalls(body, dataset, ruleMap);
            extractDirectEnabledAssignments(body, dataset, ruleMap);
        }
    }

    /** Pass E: Auxiliary methods (HabilitaComponentes, HabilitaBotoes, AjustaComponentes, etc.) */
    private void extractEnableComponentFromHelperMethods(String src, Map<String, ButtonStateRule> ruleMap) {
        Pattern helperPattern = Pattern.compile(
                "(?i)procedure\\s+\\w+\\.((?:Habilita|HabilitaComponentes|HabilitaBotoes|AjustaComponentes|ConfiguraComponentes)\\w*)\\s*(?:\\([^)]*\\))?\\s*;[\\s\\S]*?(?=^(?:procedure|function|initialization|finalization|end\\.))",
                Pattern.MULTILINE);
        Matcher mm = helperPattern.matcher(src);
        while (mm.find()) {
            String body = mm.group(0);
            if (body.length() > 15000) body = body.substring(0, 15000);
            extractEnableComponentCalls(body, "helper", ruleMap);
            extractDirectEnabledAssignments(body, "helper", ruleMap);
        }
    }

    /**
     * Pass F: Catches any procedure that contains button Enabled assignments but was not
     * covered by Passes A/D/E (e.g. FormShow, FormCreate, tvwXxx_PChange, etc.).
     * Uses a quick-check on the body before applying regex to avoid unnecessary work.
     */
    private void extractEnableComponentFromGenericHandlers(String src, Map<String, ButtonStateRule> ruleMap) {
        Pattern anyMethodPattern = Pattern.compile(
                "(?i)procedure\\s+\\w+\\.(\\w+)\\s*(?:\\([^)]*\\))?\\s*;[\\s\\S]*?(?=^(?:procedure|function|initialization|finalization|end\\.))",
                Pattern.MULTILINE);
        Matcher mm = anyMethodPattern.matcher(src);
        while (mm.find()) {
            String methodName = mm.group(1);
            // Skip methods already handled by other passes
            String lower = methodName.toLowerCase();
            if (lower.endsWith("afterscroll") || lower.endsWith("datachange")) continue;
            if (lower.startsWith("habilita") || lower.startsWith("ajustacomponentes") ||
                lower.startsWith("configuracomponentes")) continue;
            String body = mm.group(0);
            // Quick-check: only process bodies that mention Enabled or EnableComponent
            String bodyLower = body.toLowerCase();
            if (!bodyLower.contains(".enabled") && !bodyLower.contains("enablecomponent")) continue;
            if (body.length() > 15000) body = body.substring(0, 15000);
            extractDirectEnabledAssignments(body, methodName, ruleMap);
            extractEnableComponentCalls(body, methodName, ruleMap);
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
        String clean = condition.replaceAll("\\s+", " ").trim();
        // Remove outer parens wrapping the ENTIRE condition
        while (clean.startsWith("(") && findMatchingClose(clean, 0) == clean.length() - 1) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }
        // Split at top-level 'and'/'or' only (respects nested parentheses)
        List<String> parts = splitByTopLevelAndOr(clean);
        for (String part : parts) {
            String trimmed = part.trim().replaceAll("^\\(+|\\)+$", "").trim();
            if (!trimmed.isEmpty() && trimmed.length() > 2) {
                rule.getEnableConditions().add(trimmed);
            }
        }
    }

    /** Returns index of closing ')' matching '(' at openPos, or -1 if unmatched. */
    private int findMatchingClose(String s, int openPos) {
        int depth = 0;
        for (int i = openPos; i < s.length(); i++) {
            if (s.charAt(i) == '(') depth++;
            else if (s.charAt(i) == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /** Splits expr by ' and '/' or ' at depth-0 only (ignores operators inside parens). */
    private List<String> splitByTopLevelAndOr(String expr) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        int len = expr.length();
        String lower = expr.toLowerCase();
        for (int i = 0; i < len; i++) {
            char c = expr.charAt(i);
            if (c == '(') { depth++; continue; }
            if (c == ')') { depth--; continue; }
            if (depth == 0 && c == ' ') {
                // Check for " and " (space + "and " = 5 chars from i)
                if (i + 5 <= len && lower.startsWith("and ", i + 1)) {
                    parts.add(expr.substring(start, i).trim());
                    start = i + 5;
                    i += 4;
                } else if (i + 4 <= len && lower.startsWith("or ", i + 1)) {
                    parts.add(expr.substring(start, i).trim());
                    start = i + 4;
                    i += 3;
                }
            }
        }
        if (start < len) parts.add(expr.substring(start).trim());
        return parts.stream().filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toList());
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

        // Encontra métodos CalcCellColors, DrawColumnCell, DrawCell
        Pattern methodPattern = Pattern.compile(
                "(?i)procedure\\s+\\w+\\.(\\w+(?:CalcCellColou?rs?|DrawColumnCell|DrawCell))\\s*\\([^)]*\\)\\s*;[\\s\\S]*?(?=^(?:procedure|function|initialization|finalization|end\\.))",
                Pattern.MULTILINE);
        Matcher mm = methodPattern.matcher(src);

        while (mm.find()) {
            String methodName = mm.group(1);
            String body = mm.group(0);
            if (body.length() > 10000) body = body.substring(0, 10000);

            boolean isDrawColumnCell = methodName.toLowerCase().contains("drawcolumncell")
                    || methodName.toLowerCase().contains("drawcell");

            // Extrai grid name: grdPedidoAutomaticoCalcCellColors → grdPedidoAutomatico
            String gridName = methodName
                    .replaceAll("(?i)CalcCellColou?rs?$", "")
                    .replaceAll("(?i)DrawColumnCell$", "")
                    .replaceAll("(?i)DrawCell$", "");

            CalcCellColorRule rule = new CalcCellColorRule();
            rule.setGridName(gridName);

            // Detecta campo de condição: FieldByName('campo').AsInteger ou .AsString (qualquer prefixo)
            Matcher fieldM = Pattern.compile("(?i)FieldByName\\s*\\(\\s*'([^']+)'\\s*\\)\\.As(Integer|String)").matcher(body);
            if (fieldM.find()) {
                rule.setConditionField(fieldM.group(1));
            }

            // Detecta case/of com cores: N: AColor := clXxx, N: ABrush.Color := clXxx ou N: AFont.Color := clXxx
            Pattern casePat = Pattern.compile("(?i)(\\d+)\\s*:\\s*(?:AColor|ABrush\\.Color|AFont\\.Color)\\s*:=\\s*(cl\\w+)");
            Matcher cm = casePat.matcher(body);
            while (cm.find()) {
                String value = cm.group(1);
                String delphiColor = cm.group(2);
                String cssColor = mapDelphiColor(delphiColor);
                boolean isBackground = cm.group(0).toLowerCase().contains("abrush");
                String cssClass = isBackground ? "bg-" + cssColor : "text-" + cssColor;
                rule.getColorMappings().add(new CalcCellColorRule.ColorMapping(value, cssColor, cssClass, null));
            }

            // Detecta if/then com cores: if (campo = N) then AColor := clXxx, ABrush.Color := clXxx ou AFont.Color := clXxx
            Pattern ifColorPat = Pattern.compile("(?i)(?:=\\s*(\\d+)|'([^']+)')\\s*(?:then|:)\\s*[\\s\\S]*?(?:AColor|ABrush\\.Color|AFont\\.Color)\\s*:=\\s*(cl\\w+)");
            if (rule.getColorMappings().isEmpty()) {
                Matcher icm = ifColorPat.matcher(body);
                while (icm.find()) {
                    String value = icm.group(1) != null ? icm.group(1) : icm.group(2);
                    String delphiColor = icm.group(3);
                    String cssColor = mapDelphiColor(delphiColor);
                    boolean isBackground = icm.group(0).toLowerCase().contains("abrush");
                    String cssClass = isBackground ? "bg-" + cssColor : "text-" + cssColor;
                    rule.getColorMappings().add(new CalcCellColorRule.ColorMapping(value, cssColor, cssClass, null));
                }
            }

            // Fallback: if (...AsInteger = N) then AColor := clXxx, ABrush.Color := clXxx (sem FieldByName no mesmo if)
            if (rule.getColorMappings().isEmpty()) {
                Pattern ifAColorPat = Pattern.compile(
                    "(?i)if\\s+[^;]+?(?:AsInteger|AsString)\\s*(?:<>|=)\\s*(\\d+|'[^']+')\\s+then[^;]*?(?:AColor|ABrush\\.Color|AFont\\.Color)\\s*:=\\s*(cl\\w+)");
                Matcher iam = ifAColorPat.matcher(body);
                while (iam.find()) {
                    String value = iam.group(1).replace("'", "");
                    String cssColor = mapDelphiColor(iam.group(2));
                    boolean isBackground = iam.group(0).toLowerCase().contains("abrush");
                    String cssClass = isBackground ? "bg-" + cssColor : "text-" + cssColor;
                    rule.getColorMappings().add(new CalcCellColorRule.ColorMapping(value, cssColor, cssClass, null));
                }
            }

            // Tenta associar labels de legenda (lblVerde, lblAzul etc)
            enrichWithLegendLabels(src, rule);

            // DrawColumnCell: renderização customizada completa — requer ng-template manual
            if (isDrawColumnCell) {
                rule.setRenderType("FULL_RENDERER");
                rule.setMigrationNote("DrawColumnCell renderiza canvas customizado (ícones, cores, imagens). "
                        + "Angular: usar <ng-template pTemplate=\"body\"> na coluna do <p-table> com lógica condicional.");
                if (rule.getColorMappings().isEmpty()) {
                    // Mesmo sem color mappings detectados, reportar o handler
                    rule.setAngularCode("// TODO: Implementar renderização customizada da coluna " + gridName
                            + " com <ng-template pTemplate=\"body\" let-row>");
                    rules.add(rule);
                    continue;
                }
            }

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

    // ── Dependent Field Logic (Exit/Change events → field cascades) ────────

    public List<DependentFieldRule> extractDependentFieldRules(String src) {
        List<DependentFieldRule> rules = new ArrayList<>();

        // Encontra métodos de evento de campo: xxxExit, xxxChange, xxxOnSelect, xxxKeyDown
        Pattern eventPattern = Pattern.compile(
                "(?i)procedure\\s+\\w+\\.((\\w+?)(Exit|Change|OnSelect|Enter))\\s*\\([^)]*\\)\\s*;[\\s\\S]*?(?=^(?:procedure|function|initialization|finalization|end\\.))",
                Pattern.MULTILINE);
        Matcher mm = eventPattern.matcher(src);

        while (mm.find()) {
            String methodName = mm.group(1);
            String fieldName = mm.group(2);
            String eventType = mm.group(3);
            String body = mm.group(0);
            if (body.length() > 8000) body = body.substring(0, 8000);

            // Ignora se é um botão (bbt) - já tratado por ButtonStateRules
            if (fieldName.toLowerCase().startsWith("bbt")) continue;

            DependentFieldRule rule = new DependentFieldRule();
            rule.setTriggerField(fieldName);
            rule.setTriggerEvent(eventType);
            rule.setSourceMethod(methodName);

            // Detecta efeitos: campo.Text := valor, campo.KeyValue := valor
            Pattern assignPat = Pattern.compile("(?i)(\\w+)\\.(Text|KeyValue|Caption|Enabled|Visible|Date)\\s*:=\\s*([^;]+);");
            Matcher am = assignPat.matcher(body);
            while (am.find()) {
                String target = am.group(1);
                String prop = am.group(2);
                String value = am.group(3).trim()
                        .replaceAll("(?s)\\s*(?:except|finally|end\\b|begin\\b).*", "").trim();
                if (target.equalsIgnoreCase(fieldName) || target.equalsIgnoreCase("Self") || target.equalsIgnoreCase("Result")) continue;

                String action = prop.equalsIgnoreCase("Enabled") || prop.equalsIgnoreCase("Visible")
                        ? (value.equalsIgnoreCase("True") ? "enable" : "disable")
                        : "fill";
                rule.getEffects().add(new DependentFieldRule.FieldEffect(target, action, value, null));
            }

            // Detecta fallback em else: campo.Text := 'VALOR'
            Matcher elsePat = Pattern.compile("(?i)else\\s*(?:begin)?[\\s\\S]*?(\\w+)\\.Text\\s*:=\\s*'([^']*)'").matcher(body);
            if (elsePat.find() && !rule.getEffects().isEmpty()) {
                // Associa fallback ao último efeito de fill
                for (int i = rule.getEffects().size() - 1; i >= 0; i--) {
                    if ("fill".equals(rule.getEffects().get(i).getAction())) {
                        rule.getEffects().get(i).setFallback(elsePat.group(2));
                        break;
                    }
                }
            }

            // Detecta validação: TLogusMessage.Warning
            Matcher warnPat = Pattern.compile("(?i)TLogusMessage\\.Warning\\s*\\(\\s*'([^']*)'").matcher(body);
            if (warnPat.find()) {
                rule.setValidationMessage(warnPat.group(1));
            }

            // Gera hint Angular
            if (!rule.getEffects().isEmpty() || rule.getValidationMessage() != null) {
                boolean hasLookup = body.toLowerCase().contains("get") || body.toLowerCase().contains("pesquis");
                rule.setAngularHint(hasLookup
                        ? "valueChanges no formControl → switchMap para endpoint de busca"
                        : "valueChanges no formControl → patchValue nos campos dependentes");
                rules.add(rule);
            }
        }

        return rules;
    }

    // ── Dataset Event Rules (AfterInsert, CalcFields, BeforeDelete, etc) ─

    public List<DatasetEventRule> extractDatasetEventRules(String src) {
        List<DatasetEventRule> rules = new ArrayList<>();

        // Eventos de dataset: cdsXxxAfterInsert, cdsXxxCalcFields, cdsXxxBeforeDelete, etc
        String[] events = {"AfterInsert", "BeforeInsert", "AfterPost", "BeforePost",
                "AfterDelete", "BeforeDelete", "CalcFields", "OnFilterRecord",
                "BeforeEdit", "AfterEdit", "AfterCancel", "OnNewRecord"};

        for (String event : events) {
            Pattern pat = Pattern.compile(
                    "(?i)procedure\\s+\\w+\\.(\\w+)" + event + "\\s*\\([^)]*\\)\\s*;[\\s\\S]*?(?=^(?:procedure|function|initialization|finalization|end\\.))",
                    Pattern.MULTILINE);
            Matcher mm = pat.matcher(src);

            while (mm.find()) {
                String dataset = mm.group(1);
                String body = mm.group(0);
                if (body.length() > 10000) body = body.substring(0, 10000);

                DatasetEventRule rule = new DatasetEventRule();
                rule.setDataset(dataset);
                rule.setEvent(event);
                rule.setSourceMethod(dataset + event);

                if (event.equals("AfterInsert") || event.equals("OnNewRecord")) {
                    rule.setEventType("NEW_RECORD_DEFAULTS");
                    // Extrai atribuições: FieldByName('campo').AsXxx := valor
                    Pattern fieldAssign = Pattern.compile(
                            "(?i)FieldByName\\s*\\(\\s*'([^']+)'\\s*\\)\\.As\\w+\\s*:=\\s*([^;]+);");
                    Matcher fa = fieldAssign.matcher(body);
                    while (fa.find()) {
                        String val = fa.group(2).trim()
                                .replaceAll("(?s)\\s*(?:except|finally|end\\b|begin\\b).*", "").trim();
                        rule.getFields().add(new DatasetEventRule.FieldAssignment(fa.group(1), val));
                    }
                    rule.setMigration("Setar defaults no Service.preencherEntidade() ou no Entity constructor");
                } else if (event.equals("CalcFields")) {
                    rule.setEventType("COMPUTED_FIELD");
                    // Extrai campos calculados: FieldByName('campo').AsString := expressão
                    Pattern calcAssign = Pattern.compile(
                            "(?i)FieldByName\\s*\\(\\s*'([^']+)'\\s*\\)\\.As\\w+\\s*:=\\s*([^;]+);");
                    Matcher ca = calcAssign.matcher(body);
                    StringBuilder expr = new StringBuilder();
                    while (ca.find()) {
                        rule.getFields().add(new DatasetEventRule.FieldAssignment(ca.group(1), ca.group(2).trim()));
                        if (expr.length() > 0) expr.append("; ");
                        expr.append(ca.group(1)).append(" = ").append(ca.group(2).trim());
                    }
                    if (expr.length() > 0) rule.setExpression(expr.toString());
                    rule.setMigration("Getter no Angular ou campo derivado no GridVo (SELECT concatenado)");
                } else if (event.contains("Delete")) {
                    rule.setEventType("CASCADE");
                    rule.setMigration("Cascade delete via @OnDelete ou Service.excluir() com lógica explícita");
                } else if (event.contains("Before")) {
                    rule.setEventType("GUARD");
                    rule.setMigration("Validação no Service antes da operação (pre-condition check)");
                } else {
                    rule.setEventType("HOOK");
                    rule.setMigration("Lógica pós-operação no Service (refresh, recálculo, log)");
                }

                // Só adiciona se tem conteúdo útil
                if (!rule.getFields().isEmpty() || rule.getExpression() != null ||
                    event.contains("Delete") || event.contains("Before") || event.equals("OnFilterRecord")) {
                    rules.add(rule);
                }
            }
        }

        return rules;
    }

    // ── Provider Update Rules (BeforeUpdateRecord → sequences + key propagation) ─

    public List<ProviderUpdateRule> extractProviderUpdateRules(String src) {
        List<ProviderUpdateRule> rules = new ArrayList<>();

        // Encontra métodos BeforeUpdateRecord
        Pattern pat = Pattern.compile(
                "(?i)procedure\\s+\\w+\\.(\\w+BeforeUpdateRecord)\\s*\\([^)]*\\)\\s*;[\\s\\S]*?(?=^(?:procedure|function|initialization|finalization|end\\.))",
                Pattern.MULTILINE);
        Matcher mm = pat.matcher(src);

        while (mm.find()) {
            String methodName = mm.group(1);
            String body = mm.group(0);
            if (body.length() > 10000) body = body.substring(0, 10000);

            // Provider name: dspPedidoBeforeUpdateRecord → dspPedido
            String provider = methodName.replaceAll("(?i)BeforeUpdateRecord$", "");

            ProviderUpdateRule rule = new ProviderUpdateRule();
            rule.setProvider(provider);
            rule.setSourceMethod(methodName);

            // Sequences: Conexao.Next('tabela', 'campo') ou Conexao.Next('tabela', 'campo', 'filtro')
            Pattern seqPat = Pattern.compile(
                    "(?i)Conexao\\.Next\\s*\\(\\s*'([^']+)'\\s*,\\s*'([^']+)'(?:\\s*,\\s*([^)]+))?\\s*\\)");
            Matcher sm = seqPat.matcher(body);
            while (sm.find()) {
                ProviderUpdateRule.SequenceRule seq = new ProviderUpdateRule.SequenceRule();
                seq.setTable(sm.group(1));
                seq.setField(sm.group(2));
                if (sm.group(3) != null) {
                    seq.setStrategy("Conexao.Next filtered");
                    seq.setFilter(sm.group(3).trim().replaceAll("^'|'$", ""));
                } else {
                    seq.setStrategy("Conexao.Next global");
                }
                rule.getSequences().add(seq);
            }

            // Key propagation: cdsXxx.FieldByName('campo').AsXxx := FVariable ou DeltaDS
            Pattern propPat = Pattern.compile(
                    "(?i)(\\w+)\\.FieldByName\\s*\\(\\s*'([^']+)'\\s*\\)\\.As\\w+\\s*:=\\s*(F\\w+|\\w+\\.FieldByName\\s*\\(\\s*'([^']+)'\\s*\\))");
            Matcher pm = propPat.matcher(body);
            while (pm.find()) {
                String targetDs = pm.group(1);
                String targetField = pm.group(2);
                // Não é propagação se é o mesmo provider/DeltaDS
                if (targetDs.equalsIgnoreCase("DeltaDS") || targetDs.equalsIgnoreCase(provider)) continue;
                ProviderUpdateRule.KeyPropagation kp = new ProviderUpdateRule.KeyPropagation();
                kp.setTargetDataset(targetDs);
                kp.setTargetField(targetField);
                kp.setSourceField(pm.group(4) != null ? pm.group(4) : targetField);
                rule.getKeyPropagations().add(kp);
            }

            if (!rule.getSequences().isEmpty() || !rule.getKeyPropagations().isEmpty()) {
                rule.setMigration("@GeneratedValue no Entity + Service propaga FK nos itens antes de saveAll");
                rules.add(rule);
            }
        }

        return rules;
    }

    // ── Transaction Boundaries (StartTransaction/Commit/Rollback) ────────

    public List<TransactionBoundary> extractTransactionBoundaries(String src) {
        List<TransactionBoundary> rules = new ArrayList<>();

        // Encontra blocos StartTransaction...Commit...Rollback dentro de métodos
        Pattern txPat = Pattern.compile(
                "(?i)procedure\\s+\\w+\\.(\\w+)\\s*(?:\\([^)]*\\))?\\s*;[\\s\\S]*?(?=^(?:procedure|function|initialization|finalization|end\\.))",
                Pattern.MULTILINE);
        Matcher mm = txPat.matcher(src);

        while (mm.find()) {
            String methodName = mm.group(1);
            String body = mm.group(0);
            if (!body.toLowerCase().contains("starttransaction")) continue;
            if (body.length() > 10000) body = body.substring(0, 10000);

            TransactionBoundary tx = new TransactionBoundary();
            tx.setMethod(methodName);

            // Detecta padrão Conexao-prefixado: Conexao.StartTransaction / Conexao.Commit / Conexao.Rollback
            boolean isConexaoPattern = body.toLowerCase().contains("conexao.starttransaction") ||
                                      body.toLowerCase().contains("conexao.commit");

            // Extrai operações dentro da transação: ApplyUpdates, ExecSQL, comandos de negócio e dataset operations
            Pattern opPat = Pattern.compile("(?i)(\\w+\\.(?:ApplyUpdates|ExecSQL|ExecProc|Execute|Cancelar|Reativar|Salvar|Gravar|Excluir|GravarLog|Post|Delete|Insert|Edit|Append)\\s*(?:\\([^)]*\\))?)");
            Matcher om = opPat.matcher(body);
            while (om.find()) {
                String op = om.group(1).trim();
                if (!tx.getOperations().contains(op)) {
                    tx.getOperations().add(op);
                }
            }

            // Fallback: captura operações de dataset (Post, Delete, Insert) se lista ainda vazia
            if (tx.getOperations().isEmpty()) {
                Pattern datasetOpPat = Pattern.compile("(?i)(\\w+\\.(?:Post|Delete|Insert|Append|Edit)\\b)");
                Matcher dom = datasetOpPat.matcher(body);
                while (dom.find()) {
                    String op = dom.group(1).trim();
                    if (!tx.getOperations().contains(op)) {
                        tx.getOperations().add(op);
                    }
                }
            }

            // Marcador genérico para padrão Conexao quando nenhuma operação específica foi detectada
            if (tx.getOperations().isEmpty() && isConexaoPattern) {
                tx.getOperations().add("Conexao.Transaction (operacoes internas)");
            }

            // Detecta rollback explícito
            tx.setHasExplicitRollback(body.toLowerCase().contains("rollback"));

            // Detecta exception type no except
            Matcher excM = Pattern.compile("(?i)except\\s*(?:on\\s+(\\w+)\\s*:\\s*(\\w+))?").matcher(body);
            if (excM.find() && excM.group(2) != null) {
                tx.setRollbackOn(excM.group(2));
            } else {
                tx.setRollbackOn("Exception");
            }

            if (!tx.getOperations().isEmpty()) {
                String migration = "@Transactional no Service — todas as operações na mesma transação JPA";
                if (isConexaoPattern) {
                    migration = "@Transactional no Service — Conexao.StartTransaction/Commit/Rollback mapeia para Spring @Transactional";
                }
                tx.setMigration(migration);
                rules.add(tx);
            }
        }

        return rules;
    }

    // ── Cross-Form Data Flow (params in, return, callback) ──────────────

    public List<CrossFormDataFlow> extractCrossFormDataFlow(String src) {
        List<CrossFormDataFlow> rules = new ArrayList<>();

        // Encontra todos os métodos da classe
        Pattern methodPat = Pattern.compile(
                "(?i)procedure\\s+\\w+\\.(\\w+)\\s*(?:\\([^)]*\\))?\\s*;[\\s\\S]*?(?=^(?:procedure|function|initialization|finalization|end\\.))",
                Pattern.MULTILINE);
        Matcher mm = methodPat.matcher(src);

        while (mm.find()) {
            String methodName = mm.group(1);
            String body = mm.group(0);
            if (body.length() > 10000) body = body.substring(0, 10000);

            // Encontra chamadas: TfrmXxx.MakeShowModal(...) ou TfrmXxx.ShowModal
            Pattern callPat = Pattern.compile(
                    "(?i)(T\\w+)\\.(MakeShowModal|ShowModal|Show)\\s*(?:\\(([^)]*(?:\\([^)]*\\)[^)]*)*)\\))?");
            Matcher cm = callPat.matcher(body);

            while (cm.find()) {
                String targetForm = cm.group(1);
                String callMethod = cm.group(2);
                String rawParams = cm.group(3);

                CrossFormDataFlow flow = new CrossFormDataFlow();
                flow.setTargetForm(targetForm);
                flow.setMethod(callMethod);
                flow.setSourceMethod(methodName);

                // Extrai parâmetros
                if (rawParams != null && !rawParams.isBlank()) {
                    String[] params = rawParams.split(",(?![^(]*\\))"); // split por vírgula top-level
                    for (String param : params) {
                        String p = param.trim();
                        CrossFormDataFlow.FormParam fp = new CrossFormDataFlow.FormParam();
                        fp.setValue(p);
                        // Extrai field name se FieldByName
                        Matcher fbm = Pattern.compile("(?i)FieldByName\\s*\\(\\s*'([^']+)'\\s*\\)").matcher(p);
                        if (fbm.find()) fp.setField(fbm.group(1));
                        // Extrai tipo: AsInteger, AsString, etc
                        Matcher typM = Pattern.compile("(?i)\\.As(Integer|String|Float|DateTime|Boolean)").matcher(p);
                        if (typM.find()) fp.setType(typM.group(1));
                        flow.getParamsIn().add(fp);
                    }
                }

                // Detecta retorno: if (chamada = mrOk) then ... ação
                // Olha o contexto ao redor da chamada
                int callEnd = cm.end();
                String afterCall = body.substring(callEnd, Math.min(callEnd + 500, body.length()));
                if (afterCall.trim().startsWith("=") || afterCall.contains("mrOk") || afterCall.contains("mrCancel")) {
                    Matcher retM = Pattern.compile("(?i)=\\s*(mr\\w+)").matcher(afterCall);
                    if (retM.find()) flow.setExpectedReturn(retM.group(1));

                    // Ação pós-retorno: then begin ... chamada
                    Matcher postM = Pattern.compile("(?i)then\\s*(?:begin)?\\s*(\\w+(?:\\.\\w+)?(?:\\s*\\([^)]*\\))?)").matcher(afterCall);
                    if (postM.find()) {
                        String action = postM.group(1).trim();
                        if (!action.equalsIgnoreCase("begin")) flow.setOnSuccessAction(action);
                    }
                }

                flow.setMigration(callMethod.contains("Modal")
                        ? "Dialog PrimeNG com callback onClose ou Router.navigate com queryParams"
                        : "Router.navigate para tela destino");
                rules.add(flow);
            }
        }

        return rules;
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

        // Cascade validations: sequential IsEmpty checks in one method
        rules.addAll(extractCascadeValidations(src));

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

    /**
     * Detecta padrão de validação em cascata: método com N>= 2 blocos
     * "if not X.IsEmpty then begin ShowMessage('...'); Exit; end" em sequência.
     * Cada bloco corresponde a uma query de verificação antes de permitir a ação.
     */
    private List<BusinessRule> extractCascadeValidations(String src) {
        List<BusinessRule> rules = new ArrayList<>();
        Pattern methodPat = Pattern.compile(
            "(?i)procedure\\s+\\w+\\.(\\w+)\\s*(?:\\([^)]*\\))?\\s*;[\\s\\S]*?(?=^(?:procedure|function|initialization|finalization|end\\.))",
            Pattern.MULTILINE);
        Matcher mm = methodPat.matcher(src);
        while (mm.find()) {
            String methodName = mm.group(1);
            String body = mm.group(0);
            if (body.length() > 20000) body = body.substring(0, 20000);

            // Find all guard-query checks: if not X.IsEmpty then ... ShowMessage/TLogusMessage.Warning('...')
            // Also handles Delphi 'with X do begin ... if not (IsEmpty) then' (no explicit prefix)
            Pattern isEmptyPat = Pattern.compile(
                "(?i)if\\s+not\\s+(?:(\\w+)\\.IsEmpty|\\(?IsEmpty\\)?)\\s+then[\\s\\S]{0,300}?(?:ShowMessage|TLogusMessage\\.Warning)\\s*\\(\\s*'([^']+)'",
                Pattern.DOTALL);
            Matcher cm = isEmptyPat.matcher(body);
            List<String[]> checks = new ArrayList<>();
            while (cm.find()) {
                String datasetVar = cm.group(1);
                if (datasetVar == null) {
                    // Inside a 'with X do' block — find the nearest 'with' before this match
                    String beforeMatch = body.substring(0, cm.start());
                    Matcher withM = Pattern.compile("(?i)with\\s+(\\w+)\\s+do").matcher(beforeMatch);
                    String lastWithVar = "dataset";
                    while (withM.find()) { lastWithVar = withM.group(1); }
                    datasetVar = lastWithVar;
                }
                checks.add(new String[]{datasetVar, cm.group(2)});
            }
            // Pattern 2: vContinua := False + TLogusMessage.Warning/ShowMessage
            // Matches: if (condition) then begin vContinua := False; TLogusMessage.Warning('msg'); end;
            List<String[]> continuaChecks = new ArrayList<>();
            Pattern continuaPat = Pattern.compile(
                "(?i)if\\s+(.{5,200}?)\\s+then\\s*(?:begin)?[\\s\\S]{0,100}?v(?:Continua|Result|Retorno)\\s*:=\\s*(?:False|false)[\\s\\S]{0,100}?(?:TLogusMessage\\.Warning|ShowMessage)\\s*\\(\\s*'([^']+)'",
                Pattern.DOTALL);
            Matcher contM = continuaPat.matcher(body);
            while (contM.find()) {
                String condition = contM.group(1).trim().replaceAll("\\s+", " ");
                String message = contM.group(2);
                // Avoid duplicating checks already found by the IsEmpty pattern
                boolean alreadyFound = checks.stream().anyMatch(c -> c[1].equals(message));
                if (!alreadyFound) {
                    continuaChecks.add(new String[]{condition, message});
                }
            }

            if (checks.isEmpty() && continuaChecks.isEmpty()) continue;

            // Emit one guard_validation rule per check pair (US2: each pair is a distinct rule)
            for (int i = 0; i < checks.size(); i++) {
                String datasetVar = checks.get(i)[0];
                String message    = checks.get(i)[1];
                BusinessRule guard = new BusinessRule();
                guard.setRuleType("guard_validation");
                guard.setDescription("Verificação antes de prosseguir (" + methodName + "): '" + message + "'");
                guard.setSourceCode("if not " + datasetVar + ".IsEmpty then ShowMessage('" + message + "') + Exit");
                guard.setComplexity("medium");
                guard.setMigrationStrategy(
                    "Criar método de verificação no Repository. " +
                    "Lançar ValidationException(\"" + message + "\") antes de prosseguir.");
                guard.setSuggestedJavaCode(
                    "if (" + snakeToCamel(datasetVar) + "Repository.existsRelated(id)) {\n" +
                    "    throw new ValidationException(\"" + message + "\");\n" +
                    "}");
                rules.add(guard);
            }

            // Emit vContinua-based guard_validation rules
            for (String[] check : continuaChecks) {
                String condition = check[0];
                String message = check[1];
                BusinessRule guard = new BusinessRule();
                guard.setRuleType("guard_validation");
                guard.setDescription("Guard validation (" + methodName + "): '" + message + "'");
                guard.setSourceCode("if " + condition + " then vContinua := False; Warning('" + message + "')");
                guard.setComplexity("medium");
                guard.setMigrationStrategy(
                    "Criar verificação no Service. " +
                    "Lançar ValidationException(\"" + message + "\") antes de prosseguir.");
                guard.setSuggestedJavaCode(
                    "// Guard: " + condition + "\n" +
                    "if (conditionMet) {\n" +
                    "    throw new ValidationException(\"" + message + "\");\n" +
                    "}");
                rules.add(guard);
            }

            // Also emit grouped cascade_validation summary when there are 2+ checks (backward compat)
            int totalChecks = checks.size() + continuaChecks.size();
            if (totalChecks >= 2) {
                StringBuilder desc = new StringBuilder();
                desc.append("Validação em cascata em ").append(methodName)
                    .append(": ").append(totalChecks).append(" verificações sequenciais — ");
                for (int i = 0; i < checks.size(); i++) {
                    if (i > 0) desc.append(" | ");
                    desc.append(checks.get(i)[1]);
                }
                for (int i = 0; i < continuaChecks.size(); i++) {
                    if (i > 0 || !checks.isEmpty()) desc.append(" | ");
                    desc.append(continuaChecks.get(i)[1]);
                }

                StringBuilder javaCode = new StringBuilder();
                javaCode.append("// Validações em cascata — ").append(methodName).append("\n");
                int idx = 1;
                for (int i = 0; i < checks.size(); i++) {
                    javaCode.append("// Verificação ").append(idx++).append("\n");
                    javaCode.append("if (").append(snakeToCamel(checks.get(i)[0])).append("Repository.existsRelated(id)) {\n");
                    javaCode.append("    throw new ValidationException(\"").append(checks.get(i)[1]).append("\");\n");
                    javaCode.append("}\n");
                }
                for (int i = 0; i < continuaChecks.size(); i++) {
                    javaCode.append("// Verificação ").append(idx++).append(" (vContinua guard)\n");
                    javaCode.append("if (conditionMet) {\n");
                    javaCode.append("    throw new ValidationException(\"").append(continuaChecks.get(i)[1]).append("\");\n");
                    javaCode.append("}\n");
                }

                BusinessRule summary = new BusinessRule();
                summary.setRuleType("cascade_validation");
                summary.setDescription(desc.toString());
                summary.setSourceCode("cascade:" + methodName + ":" + totalChecks + "_checks");
                summary.setComplexity("high");
                summary.setMigrationStrategy(
                    "Criar " + totalChecks + " métodos de verificação no Service/Repository. " +
                    "Cada um lança ValidationException com a mensagem original antes de prosseguir.");
                summary.setSuggestedJavaCode(javaCode.toString());
                rules.add(summary);
            }
        }
        return rules;
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
