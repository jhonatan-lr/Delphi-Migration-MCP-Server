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
        unit.setCalledForms(extractCalledForms(cleaned));

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

    // ── SQL Extraction ───────────────────────────────────────────────────────

    public List<SqlQuery> extractSqlQueries(String src) {
        List<SqlQuery> queries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int idx = 0;

        // 1. SQL.Text := '...'
        Matcher m1 = SQL_TEXT_ASSIGN_PATTERN.matcher(src);
        while (m1.find()) {
            String sql = m1.group(1);
            if (sql != null && sql.trim().length() > 5 && seen.add(sql.trim().substring(0, Math.min(40, sql.trim().length())))) {
                queries.add(buildSqlQuery(sql.trim(), idx++, src, m1.start()));
            }
        }

        // 2. SQL.Add('...') — coleta todas as linhas SQL.Add e agrupa por bloco (separado por SQL.Clear)
        {
            // Primeiro encontra posições de SQL.Clear para delimitar blocos
            List<Integer> clearPositions = new ArrayList<>();
            Matcher clearMatcher = Pattern.compile("(?i)(?:\\.|\\b)SQL\\.Clear\\s*;").matcher(src);
            while (clearMatcher.find()) clearPositions.add(clearMatcher.start());

            // Coleta todas as linhas SQL.Add com suas posições
            Matcher addMatcher = SQL_ADD_LINE_PATTERN.matcher(src);
            List<int[]> addPositions = new ArrayList<>();  // [pos, groupStart]
            List<String> addContents = new ArrayList<>();
            while (addMatcher.find()) {
                addPositions.add(new int[]{addMatcher.start(), addMatcher.end()});
                addContents.add(addMatcher.group(1));
            }

            if (!addPositions.isEmpty()) {
                // Agrupa SQL.Add consecutivos (mesmo bloco: entre SQL.Clear ou gap > 500 chars)
                List<List<String>> blocks = new ArrayList<>();
                List<Integer> blockStarts = new ArrayList<>();
                List<String> currentBlock = new ArrayList<>();
                int lastEnd = -1;

                for (int i = 0; i < addPositions.size(); i++) {
                    int pos = addPositions.get(i)[0];
                    boolean newBlock = false;

                    // Novo bloco se: primeiro item, ou há um SQL.Clear entre o último Add e este
                    if (currentBlock.isEmpty()) {
                        newBlock = true;
                    } else {
                        for (int cp : clearPositions) {
                            if (cp > lastEnd && cp < pos) { newBlock = true; break; }
                        }
                        // Ou se o gap é grande demais (provável método diferente)
                        if (pos - lastEnd > 500) newBlock = true;
                    }

                    if (newBlock && !currentBlock.isEmpty()) {
                        blocks.add(currentBlock);
                        currentBlock = new ArrayList<>();
                    }
                    if (currentBlock.isEmpty()) {
                        blockStarts.add(pos);
                    }
                    currentBlock.add(addContents.get(i));
                    lastEnd = addPositions.get(i)[1];
                }
                if (!currentBlock.isEmpty()) blocks.add(currentBlock);

                // Monta cada bloco como uma query
                for (int b = 0; b < blocks.size(); b++) {
                    StringBuilder sb = new StringBuilder();
                    for (String line : blocks.get(b)) {
                        if (sb.length() > 0) sb.append(" ");
                        sb.append(line.trim());
                    }
                    String sql = sb.toString().trim();
                    if (sql.length() > 5 && seen.add(sql.substring(0, Math.min(40, sql.length())))) {
                        queries.add(buildSqlQuery(sql, idx++, src, blockStarts.get(b)));
                    }
                }
            }
        }

        // 3. SQL multi-linha inline (SELECT...FROM, INSERT...INTO, etc.)
        Matcher m3 = SQL_MULTILINE_PATTERN.matcher(src);
        while (m3.find()) {
            String sql = m3.group(0).trim();
            if (!seen.contains(sql.substring(0, Math.min(40, sql.length())))) {
                seen.add(sql.substring(0, Math.min(40, sql.length())));
                queries.add(buildSqlQuery(sql, idx++, src, m3.start()));
            }
        }

        return queries;
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

    private List<String> extractCalledForms(String src) {
        Set<String> forms = new LinkedHashSet<>();

        // Padrão 1: TfrmXxx.MakeShowModal(...)
        Matcher m1 = Pattern.compile("(?i)(T\\w+)\\.MakeShowModal").matcher(src);
        while (m1.find()) forms.add(m1.group(1));

        // Padrão 2: Application.CreateForm(TfrmXxx, ...)
        Matcher m2 = Pattern.compile("(?i)Application\\.CreateForm\\s*\\(\\s*(T\\w+)").matcher(src);
        while (m2.find()) forms.add(m2.group(1));

        // Padrão 3: TfrmXxx.Create(...)
        Matcher m3 = Pattern.compile("(?i)(Tfrm\\w+)\\.Create\\s*\\(").matcher(src);
        while (m3.find()) forms.add(m3.group(1));

        // Padrão 4: TfrmXxx.ShowModal / .Show
        Matcher m4 = Pattern.compile("(?i)(Tfrm\\w+)\\.(?:ShowModal|Show)\\b").matcher(src);
        while (m4.find()) forms.add(m4.group(1));

        // Padrão 5: Trel/Tdtm/Tfra classes chamadas
        Matcher m5 = Pattern.compile("(?i)(T(?:rel|dtm|fra)\\w+)\\.(?:Create|MakeShowModal|Imprimir|Execute)").matcher(src);
        while (m5.find()) forms.add(m5.group(1));

        // Remove a própria classe (self references)
        forms.removeIf(f -> src.contains(f + " = class"));

        return new ArrayList<>(forms);
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
}
