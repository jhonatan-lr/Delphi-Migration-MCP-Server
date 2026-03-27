package com.migration.mcp.generator;

import com.migration.mcp.model.*;
import com.migration.mcp.model.ProjectProfileStore;
import java.util.*;

/**
 * Gera codigo Java (Spring Boot 2.x / Java 8) a partir de estruturas Delphi analisadas.
 *
 * Segue o padrao da API logus-corporativo-api:
 *  - Entity com getters/setters manuais (sem Lombok)
 *  - Repository com JpaRepository + JpaSpecificationExecutor
 *  - Service com @Autowired field injection + ResultDto + LazyLoadDto
 *  - Resource (controller) com try/catch padrao (ValidationException->409, Exception->500)
 *  - DTO (dados de entrada) + PesquisaDto (filtros) + Vo (grid/view)
 *  - javax.persistence (Java 8 / Spring Boot 2.x)
 */
public class JavaCodeGenerator {

    // ── Profile helpers ──────────────────────────────────────────────────────

    private ProjectProfile profile() {
        return ProjectProfileStore.getInstance().get();
    }

    private String fieldPrefix() {
        return ProjectProfileStore.getInstance().fieldPrefix();
    }

    private String cleanClassNameWithProfile(String name) {
        String cleaned = cleanClassName(name);
        ProjectProfile p = profile();
        if (p != null) {
            String fp = p.getNaming().getFormPrefix();
            String dp = p.getNaming().getDataModulePrefix();
            if (!fp.isEmpty() && cleaned.toLowerCase().startsWith(fp.toLowerCase())) {
                cleaned = cleaned.substring(fp.length());
            }
            if (!dp.isEmpty() && cleaned.toLowerCase().startsWith(dp.toLowerCase())) {
                cleaned = cleaned.substring(dp.length());
            }
        }
        if (!cleaned.isEmpty()) {
            cleaned = Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
        }
        return cleaned.isEmpty() ? cleanClassName(name) : cleaned;
    }

    private String toTableName(String entityName) {
        ProjectProfile p = profile();
        String style = p != null ? p.getNaming().getTableNamingStyle() : "UPPER_SNAKE";
        switch (style) {
            case "lower_snake": return toSnakeCase(entityName).toLowerCase();
            case "PascalCase":  return entityName;
            default:            return toSnakeCase(entityName).toUpperCase();
        }
    }

    private String toColumnName(String fieldName) {
        String prefix = fieldPrefix();
        String clean = fieldName;
        if (clean.startsWith(prefix) && clean.length() > prefix.length()) {
            clean = clean.substring(prefix.length());
        }
        ProjectProfile p = profile();
        String style = p != null ? p.getNaming().getColumnNamingStyle() : "UPPER_SNAKE";
        switch (style) {
            case "lower_snake": return toSnakeCase(clean).toLowerCase();
            case "PascalCase":  return clean;
            default:            return toSnakeCase(clean).toUpperCase();
        }
    }

    // ── Entity ───────────────────────────────────────────────────────────────

    public String generateEntity(DelphiClass dc, String packageName) {
        return generateEntity(dc, packageName, null, null);
    }

    public String generateEntity(DelphiClass dc, String packageName, List<DfmForm.DatasetField> dfmFields) {
        return generateEntity(dc, packageName, dfmFields, null);
    }

    /**
     * Gera Entity JPA no padrão logus-corporativo-api:
     * - @Table com nome real da tabela (lowercase)
     * - @GenericGenerator(strategy = "increment")
     * - LogusDateTime para datas
     * - @ManyToOne para FKs conhecidas
     * - Boolean para flb_
     * - Getters/setters manuais com this.
     * - Nomes descritivos (cdg_filial → codigoFilial)
     *
     * @param tableName nome real da tabela no banco (ex: "estmpedautomatico"). Se null, infere do nome da classe.
     */
    public String generateEntity(DelphiClass dc, String packageName,
                                  List<DfmForm.DatasetField> dfmFields, String tableName) {
        return generateEntity(dc, packageName, dfmFields, tableName, null);
    }

    public String generateEntity(DelphiClass dc, String packageName,
                                  List<DfmForm.DatasetField> dfmFields, String tableName,
                                  String entityClassName) {
        StringBuilder sb = new StringBuilder();
        String baseName = cleanClassNameWithProfile(dc.getName());
        // Fix 5: usar entityClassName passado (para detail entities)
        String entityClass = entityClassName != null ? entityClassName + "Entity" : baseName + "Entity";

        // Resolver @Table(name): aceita tableName da SQL, consulta knownTables só se null
        if (tableName == null || tableName.isBlank()) {
            TargetPatterns tp = patterns();
            if (tp != null) {
                String lookupName = entityClassName != null ? entityClassName : baseName;
                for (Map.Entry<String, TargetPatterns.TablePattern> e : tp.getKnownTables().entrySet()) {
                    if (e.getValue().getEntity() != null) {
                        String eName = e.getValue().getEntity().replace("Entity", "");
                        if (eName.equalsIgnoreCase(lookupName) || eName.equalsIgnoreCase(baseName)) {
                            tableName = e.getKey();
                            break;
                        }
                    }
                }
            }
            if (tableName == null || tableName.isBlank()) {
                tableName = toTableName(baseName).toLowerCase();
            }
        }

        // ── Imports ──
        sb.append("package logus.corporativo.api.entity;\n\n");
        sb.append("import javax.persistence.*;\n");
        sb.append("import org.hibernate.annotations.GenericGenerator;\n");
        sb.append("import org.hibernate.annotations.Type;\n");
        sb.append("import logusretail.manager.type.LogusDateTime;\n");
        sb.append("import java.io.Serializable;\n");
        sb.append("import java.math.BigDecimal;\n\n");

        // ── Class declaration ──
        sb.append("@Entity\n");
        sb.append("@Table(name = \"").append(tableName).append("\")\n");
        sb.append("public class ").append(entityClass).append(" implements Serializable {\n\n");
        sb.append("  private static final long serialVersionUID = 1L;\n\n");

        // ── Resolve fields ──
        List<EntityField> fields = resolveEntityFields(dc, dfmFields, tableName);

        // ── Detect PK ──
        String pkColumn = detectPkColumn(fields, tableName);

        // ── @Id ──
        sb.append("  @Id\n");
        sb.append("  @GeneratedValue(generator = \"generator\")\n");
        sb.append("  @GenericGenerator(name = \"generator\", strategy = \"increment\")\n");
        sb.append("  @Column(name = \"").append(pkColumn).append("\")\n");
        sb.append("  private Integer id;\n\n");

        // ── Fields ──
        for (EntityField f : fields) {
            if (f.colName.equals(pkColumn)) continue; // PK já gerada acima

            // @ManyToOne para FKs conhecidas
            if (f.manyToOneEntity != null) {
                sb.append("  @ManyToOne(fetch = FetchType.LAZY)\n");
                sb.append("  @JoinColumn(name = \"").append(f.colName).append("\")\n");
                sb.append("  private ").append(f.manyToOneEntity).append(" ").append(f.javaName).append(";\n\n");
                continue;
            }

            // LogusDateTime para datas
            if (f.isDate) {
                sb.append("  @Column(name = \"").append(f.colName).append("\")\n");
                sb.append("  @Type(type = \"logus.corporativo.api.entity.persistant.PersistantLogusDateTime\")\n");
                sb.append("  private LogusDateTime ").append(f.javaName).append(";\n\n");
                continue;
            }

            // Enum com @Convert
            if (f.isEnum) {
                String converterName = f.converterClassName != null
                        ? f.converterClassName
                        : f.enumClassName.replace("Enum", "Converter");
                sb.append("  @Column(name = \"").append(f.colName).append("\")\n");
                sb.append("  @Convert(converter = ").append(converterName).append(".class)\n");
                sb.append("  private ").append(f.enumClassName).append(" ").append(f.javaName).append(";\n\n");
                continue;
            }

            // Campo normal
            sb.append("  @Column(name = \"").append(f.colName).append("\"");
            if (!f.nullable) sb.append(", nullable = false");
            sb.append(")\n");
            sb.append("  private ").append(f.javaType).append(" ").append(f.javaName).append(";\n\n");
        }

        // ── Constructor ──
        sb.append("  public ").append(entityClass).append("() {\n");
        sb.append("    super();\n");
        sb.append("  }\n\n");

        // ── Getters & Setters manuais com this. ──
        sb.append("  public Integer getId() {\n    return this.id;\n  }\n\n");
        sb.append("  public void setId(Integer id) {\n    this.id = id;\n  }\n\n");

        for (EntityField f : fields) {
            if (f.colName.equals(pkColumn)) continue;
            String type = f.manyToOneEntity != null ? f.manyToOneEntity : (f.isDate ? "LogusDateTime" : f.javaType);
            String cap = capitalize(f.javaName);
            sb.append("  public ").append(type).append(" get").append(cap).append("() {\n");
            sb.append("    return this.").append(f.javaName).append(";\n  }\n\n");
            sb.append("  public void set").append(cap).append("(").append(type).append(" ").append(f.javaName).append(") {\n");
            sb.append("    this.").append(f.javaName).append(" = ").append(f.javaName).append(";\n  }\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    // ── Entity Field Resolution ──────────────────────────────────────────────

    /** Campo resolvido para entity com todas as informações de mapeamento */
    private static class EntityField {
        String colName;           // nome da coluna no banco (lowercase)
        String javaName;          // nome Java descritivo (camelCase)
        String javaType;          // Integer, String, BigDecimal, Boolean
        boolean isDate;           // usa LogusDateTime
        boolean isEnum;           // flg_ → enum com @Convert
        String enumClassName;     // ex: SituacaoPedidoAutomaticoEnum
        String converterClassName; // ex: SituacaoPedidoAutomaticoConverter
        String manyToOneEntity;   // ex: "FilialEntity" se é FK, null se campo simples
        int priority;             // flg_=10, cdg_=5, dcr_=1 (para resolver colisões)
        boolean nullable = true;  // do banco: false → gera @Column(nullable = false)
    }

    /** FKs conhecidas do projeto Logus → Entity correspondente */
    private static final Map<String, String> KNOWN_FK_ENTITIES = new LinkedHashMap<>();
    static {
        KNOWN_FK_ENTITIES.put("cdg_filial", "FilialEntity");
        KNOWN_FK_ENTITIES.put("cdg_produto", "EmbalagemEntity");
        KNOWN_FK_ENTITIES.put("cdg_secao", "SecaoEntity");
        KNOWN_FK_ENTITIES.put("cdg_grupo", "GrupoEntity");
        KNOWN_FK_ENTITIES.put("cdg_subgrupo", "SubGrupoEntity");
        KNOWN_FK_ENTITIES.put("cdg_depto", "DepartamentoEntity");
    }

    /** Resolve campos: converte nomes técnicos para descritivos, resolve colisões por prioridade */
    private List<EntityField> resolveEntityFields(DelphiClass dc, List<DfmForm.DatasetField> dfmFields, String tableName) {
        // Coleta todos os campos com prioridade
        Map<String, EntityField> byJavaName = new LinkedHashMap<>();

        // Tenta do DelphiClass primeiro
        for (DelphiField f : dc.getFields()) {
            if (f.isComponent()) continue;
            String colName = f.getName().toLowerCase();
            if (isCampoDeTela(colName)) continue;
            EntityField ef = mapToEntityField(colName, f.getJavaType());
            if (ef != null && !ef.javaName.equals("id") && !ef.javaName.isEmpty()) {
                addOrReplace(byJavaName, ef);
            }
        }

        // Fallback 1: DFM fields
        if (byJavaName.isEmpty() && dfmFields != null) {
            for (DfmForm.DatasetField df : dfmFields) {
                if (isCampoDeTela(df.getName())) continue;
                EntityField ef = mapToEntityField(df.getName(), df.getDelphiType());
                if (ef != null && !ef.javaName.equals("id") && !ef.javaName.isEmpty()) {
                    addOrReplace(byJavaName, ef);
                }
            }
        }

        // Fallback 2: Colunas reais do banco (via TargetPatterns)
        if (byJavaName.isEmpty()) {
            TargetPatterns tp = patterns();
            if (tp != null && tableName != null) {
                TargetPatterns.TablePattern table = tp.getKnownTables().get(tableName);
                if (table != null && table.getColumns() != null) {
                    for (TargetPatterns.ColumnPattern col : table.getColumns()) {
                        EntityField ef = mapToEntityFieldFromDb(col);
                        if (ef != null && !ef.javaName.equals("id") && !ef.javaName.isEmpty()) {
                            addOrReplace(byJavaName, ef);
                        }
                    }
                }
            }
        }

        // Filtro: remover campos fantasma do DFM que não existem no banco real
        if (!byJavaName.isEmpty()) {
            TargetPatterns tp = patterns();
            if (tp != null && tableName != null) {
                TargetPatterns.TablePattern table = tp.getKnownTables().get(tableName);
                if (table != null && table.getColumns() != null) {
                    Set<String> realCols = new HashSet<>();
                    for (TargetPatterns.ColumnPattern c : table.getColumns()) {
                        realCols.add(c.getName());
                    }
                    byJavaName.values().removeIf(ef ->
                        ef.colName != null && !realCols.contains(ef.colName));
                }
            }
        }

        // Fix 3: Se filtro removeu tudo (entity ficou só com @Id), usar colunas do banco
        if (byJavaName.isEmpty()) {
            TargetPatterns tp = patterns();
            if (tp != null && tableName != null) {
                TargetPatterns.TablePattern table = tp.getKnownTables().get(tableName);
                if (table != null && table.getColumns() != null) {
                    for (TargetPatterns.ColumnPattern col : table.getColumns()) {
                        EntityField ef = mapToEntityFieldFromDb(col);
                        if (ef != null && !ef.javaName.equals("id") && !ef.javaName.isEmpty()) {
                            addOrReplace(byJavaName, ef);
                        }
                    }
                }
            }
        }

        // Enriquecimento: corrigir tipos com dados reais do banco
        enrichWithDbMetadata(byJavaName, tableName);

        return new ArrayList<>(byJavaName.values());
    }

    /** Resolve campos sem tableName (backward compat) */
    private List<EntityField> resolveEntityFields(DelphiClass dc, List<DfmForm.DatasetField> dfmFields) {
        return resolveEntityFields(dc, dfmFields, null);
    }

    /** Cria EntityField a partir de coluna real do banco (sem heurísticas — tipo é ground truth) */
    private EntityField mapToEntityFieldFromDb(TargetPatterns.ColumnPattern col) {
        if (col.getName() == null || col.getName().isBlank()) return null;
        String colName = col.getName();

        EntityField ef = new EntityField();
        ef.colName = colName;
        ef.javaName = toDescriptiveJavaName(colName);
        ef.javaType = col.getJavaType(); // tipo real do BD
        ef.nullable = col.isNullable();

        TargetPatterns tp = patterns();

        // String FKs
        if (tp != null && tp.getStringForeignKeys().contains(colName)) {
            ef.javaType = "String";
            ef.priority = 5;
            return ef;
        }

        // @ManyToOne: prioridade KNOWN_FK_ENTITIES (curados) > knownForeignKeys (banco) > heurística
        if (colName.startsWith("cdg_")) {
            String fkEntity = null;
            if (KNOWN_FK_ENTITIES.containsKey(colName)) {
                fkEntity = KNOWN_FK_ENTITIES.get(colName);
            } else if (tp != null && tp.getKnownForeignKeys().containsKey(colName)) {
                fkEntity = tp.getKnownForeignKeys().get(colName);
            }
            if (fkEntity != null) {
                ef.manyToOneEntity = fkEntity;
                ef.javaName = colName.replace("cdg_", "").replace("_", "");
                ef.priority = 5;
                return ef;
            }
        }

        // Enum
        if (tp != null && tp.getKnownEnums().containsKey(colName)) {
            TargetPatterns.EnumPattern ep = tp.getKnownEnums().get(colName);
            ef.isEnum = true;
            ef.enumClassName = ep.getEnumClass();
            ef.converterClassName = ep.getConverterClass();
            ef.javaType = ep.getEnumClass();
            ef.priority = 10;
            return ef;
        }

        // flb_ → Boolean (padrão Logus: CHAR(1) no banco com BooleanConverter)
        if (colName.startsWith("flb_")) {
            ef.javaType = "Boolean";
            ef.priority = 10;
            return ef;
        }

        // flg_ → enum inferido
        if (colName.startsWith("flg_")) {
            ef.isEnum = true;
            String enumSuffix = snakeToCamel(colName.substring(4));
            ef.enumClassName = capitalize(enumSuffix) + "Enum";
            ef.javaType = ef.enumClassName;
            ef.priority = 10;
            return ef;
        }

        // Datas
        if ("LogusDateTime".equals(col.getJavaType())) {
            ef.isDate = true;
        }

        // Prioridade para resolver colisões
        if (colName.startsWith("flb_")) ef.priority = 10;
        else if (colName.startsWith("cdg_") || colName.startsWith("nmr_") || colName.startsWith("dat_")) ef.priority = 5;
        else if (colName.startsWith("dcr_")) ef.priority = 1;
        else ef.priority = 3;

        return ef;
    }

    /** Enriquece campos existentes com tipos reais do banco (corrige heurísticas erradas) */
    private void enrichWithDbMetadata(Map<String, EntityField> byJavaName, String tableName) {
        TargetPatterns tp = patterns();
        if (tp == null || tableName == null) return;
        TargetPatterns.TablePattern table = tp.getKnownTables().get(tableName);
        if (table == null || table.getColumns() == null) return;

        Map<String, TargetPatterns.ColumnPattern> dbCols = new HashMap<>();
        for (TargetPatterns.ColumnPattern c : table.getColumns()) {
            dbCols.put(c.getName(), c);
        }
        for (EntityField ef : byJavaName.values()) {
            TargetPatterns.ColumnPattern dbCol = dbCols.get(ef.colName);
            if (dbCol != null && !ef.isEnum && ef.manyToOneEntity == null) {
                // flb_ é Boolean no projeto (BooleanConverter), banco diz CHAR(1) — não sobrescrever
                if (ef.colName != null && ef.colName.startsWith("flb_")) {
                    ef.javaType = "Boolean";
                } else {
                    ef.javaType = dbCol.getJavaType();
                }
                ef.nullable = dbCol.isNullable();
                if ("LogusDateTime".equals(dbCol.getJavaType())) {
                    ef.isDate = true;
                }
            }
        }
    }

    /** Adiciona campo ou substitui se o novo tem prioridade maior (flg_ > dcr_) */
    private void addOrReplace(Map<String, EntityField> map, EntityField ef) {
        EntityField existing = map.get(ef.javaName);
        if (existing == null || ef.priority > existing.priority) {
            map.put(ef.javaName, ef);
        }
    }

    /** Retorna o TargetPatterns carregado (ou null) */
    private TargetPatterns patterns() {
        return ProjectProfileStore.getInstance().getPatterns();
    }

    /** Verifica se o campo é de tela (calculado) e não uma coluna real do banco */
    private boolean isCampoDeTela(String colName) {
        if (colName == null) return true;
        // Campos com prefixo padrão do banco são reais
        if (colName.matches("^(cdg_|dat_|nmr_|dcr_|qtd_|val_|pct_|flb_|flg_|sgl_|hor_|id).*")) {
            return false;
        }
        // Campos que começam com nome de dataset (selecao*, produtos*, display*) são calculados
        if (colName.matches("^(selecao|produtos|display|consulta|filtro|combo|lista|detalhe).*")) {
            return true;
        }
        return false; // caso duvidoso: manter
    }

    /** Mapeia um campo Delphi para EntityField com nome descritivo e tipo correto */
    private EntityField mapToEntityField(String colName, String delphiType) {
        if (colName == null || colName.isBlank()) return null;
        colName = colName.toLowerCase();

        EntityField ef = new EntityField();
        ef.colName = colName;

        // Nome Java descritivo: primeiro tenta patterns, depois heurística
        ef.javaName = toDescriptiveJavaName(colName);

        TargetPatterns tp = patterns();

        // String FKs (CNPJ, códigos texto) — não são @ManyToOne
        if (tp != null && tp.getStringForeignKeys().contains(colName)) {
            ef.javaType = "String";
            ef.priority = 5;
            return ef;
        }

        // @ManyToOne: somente para campos cdg_* (códigos são FKs, nmr_ não)
        // Prioridade: KNOWN_FK_ENTITIES (curados do projeto) > knownForeignKeys (podem ter nomes do banco)
        String fkEntity = null;
        if (colName.startsWith("cdg_")) {
            if (KNOWN_FK_ENTITIES.containsKey(colName)) {
                fkEntity = KNOWN_FK_ENTITIES.get(colName);
            } else if (tp != null && tp.getKnownForeignKeys().containsKey(colName)) {
                fkEntity = tp.getKnownForeignKeys().get(colName);
            }
        }
        if (fkEntity != null) {
            ef.manyToOneEntity = fkEntity;
            ef.javaName = colName.replace("cdg_", "").replace("_", "");
            ef.priority = 5;
            return ef;
        }

        // Enum: primeiro tenta patterns
        if (tp != null && tp.getKnownEnums().containsKey(colName)) {
            TargetPatterns.EnumPattern ep = tp.getKnownEnums().get(colName);
            ef.isEnum = true;
            ef.enumClassName = ep.getEnumClass();
            ef.converterClassName = ep.getConverterClass();
            ef.javaType = ep.getEnumClass();
            ef.priority = 10;
            return ef;
        }

        // Prioridade para resolver colisões (flg_ > cdg_ > dcr_)
        if (colName.startsWith("flg_") || colName.startsWith("flb_")) ef.priority = 10;
        else if (colName.startsWith("cdg_") || colName.startsWith("nmr_") || colName.startsWith("dat_")) ef.priority = 5;
        else if (colName.startsWith("dcr_")) ef.priority = 1;
        else ef.priority = 3;

        // Tipo baseado no prefixo + tipo Delphi
        if (colName.startsWith("dat_")) {
            ef.isDate = true;
            ef.javaType = "LogusDateTime";
        } else if (colName.startsWith("flb_")) {
            ef.javaType = "Boolean";
        } else if (colName.startsWith("flg_")) {
            ef.isEnum = true;
            // Gera nome do enum: flg_status_pedido → StatusPedidoEnum
            String enumSuffix = snakeToCamel(colName.substring(4));
            ef.enumClassName = capitalize(enumSuffix) + "Enum";
            ef.javaType = ef.enumClassName;
        } else if (colName.startsWith("qtd_") || colName.startsWith("val_") || colName.startsWith("pct_")) {
            ef.javaType = "BigDecimal";
        } else if (colName.startsWith("cdg_") || colName.startsWith("nmr_")) {
            // TStringField → String, senão Integer
            if (delphiType != null && (delphiType.contains("String") || delphiType.contains("Memo"))) {
                ef.javaType = "String";
            } else {
                ef.javaType = "Integer";
            }
        } else {
            ef.javaType = "String"; // dcr_, sgl_, etc.
        }

        return ef;
    }

    /** Abreviações comuns no banco Informix/Logus → nome descritivo */
    private static final Map<String, String> ABBREVIATIONS = new LinkedHashMap<>();
    static {
        ABBREVIATIONS.put("ped", "pedido");
        ABBREVIATIONS.put("auto", "automatico");
        ABBREVIATIONS.put("cancel", "cancelamento");
        ABBREVIATIONS.put("canc", "cancelamento");
        ABBREVIATIONS.put("prev", "previsao");
        ABBREVIATIONS.put("conf", "confirmacao");
        ABBREVIATIONS.put("tp", "tipo");
        ABBREVIATIONS.put("emb", "embalagem");
        ABBREVIATIONS.put("vnd", "venda");
        ABBREVIATIONS.put("med", "media");
        ABBREVIATIONS.put("orig", "original");
        ABBREVIATIONS.put("pend", "pendencia");
        ABBREVIATIONS.put("preco", "preco");
        ABBREVIATIONS.put("forn", "fornecedor");
        ABBREVIATIONS.put("prod", "produto");
        ABBREVIATIONS.put("docto", "documento");
        ABBREVIATIONS.put("lancto", "lancamento");
        ABBREVIATIONS.put("oper", "operacao");
        ABBREVIATIONS.put("estq", "estoque");
        ABBREVIATIONS.put("rec", "recebimento");
        ABBREVIATIONS.put("pag", "pagamento");
        ABBREVIATIONS.put("ativ", "atividade");
        ABBREVIATIONS.put("desc", "desconto");
        ABBREVIATIONS.put("mov", "movimento");
        ABBREVIATIONS.put("sist", "sistema");
    }

    /** Converte nome de coluna técnico para nome Java descritivo */
    private String toDescriptiveJavaName(String colName) {
        // 1. Tenta match exato no patterns (mais preciso)
        TargetPatterns tp = patterns();
        if (tp != null) {
            // Tenta o sufixo sem prefixo técnico (ex: "canc_pend_auto" de "flb_canc_pend_auto")
            String suffixForLookup = colName.replaceAll("^(cdg_|dcr_|nmr_|dat_|qtd_|val_|pct_|flb_|flg_|sgl_)", "");
            String expanded = tp.getColumnNameExpansions().get(suffixForLookup);
            if (expanded != null) {
                // Adiciona prefixo descritivo se necessário
                if (colName.startsWith("dat_")) return "data" + capitalize(expanded);
                if (colName.startsWith("nmr_")) return "numero" + capitalize(expanded);
                if (colName.startsWith("qtd_")) return "quantidade" + capitalize(expanded);
                if (colName.startsWith("val_")) return "valor" + capitalize(expanded);
                if (colName.startsWith("pct_")) return "percentual" + capitalize(expanded);
                if (colName.startsWith("sgl_")) return "sigla" + capitalize(expanded);
                return expanded;
            }
        }

        // 2. Fallback: heurística de prefixo + ABBREVIATIONS
        // Remove prefixo técnico
        String suffix = colName;
        String prefix = "";
        if (suffix.startsWith("cdg_")) suffix = suffix.substring(4);
        else if (suffix.startsWith("dcr_")) { suffix = suffix.substring(4); }
        else if (suffix.startsWith("nmr_")) { prefix = "numero"; suffix = suffix.substring(4); }
        else if (suffix.startsWith("dat_")) { prefix = "data"; suffix = suffix.substring(4); }
        else if (suffix.startsWith("qtd_")) { prefix = "quantidade"; suffix = suffix.substring(4); }
        else if (suffix.startsWith("val_")) { prefix = "valor"; suffix = suffix.substring(4); }
        else if (suffix.startsWith("pct_")) { prefix = "percentual"; suffix = suffix.substring(4); }
        else if (suffix.startsWith("flb_")) suffix = suffix.substring(4);
        else if (suffix.startsWith("flg_")) suffix = suffix.substring(4);
        else if (suffix.startsWith("sgl_")) { prefix = "sigla"; suffix = suffix.substring(4); }

        // Se não removeu nada, retorna snakeToCamel
        if (suffix.equals(colName)) return snakeToCamel(colName);

        // Expande abreviações: patterns primeiro, depois ABBREVIATIONS
        String[] parts = suffix.split("_");
        StringBuilder expanded = new StringBuilder();
        TargetPatterns tpLocal = patterns();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            String exp = null;
            // Fix 6: consulta patterns para cada parte
            if (tpLocal != null) {
                exp = tpLocal.getColumnNameExpansions().get(part.toLowerCase());
            }
            if (exp == null) {
                exp = ABBREVIATIONS.getOrDefault(part.toLowerCase(), part);
            }
            expanded.append(capitalize(exp));
        }

        if (!prefix.isEmpty()) {
            return prefix + expanded;
        }
        // Primeiro char lowercase
        String result = expanded.toString();
        if (!result.isEmpty()) {
            result = Character.toLowerCase(result.charAt(0)) + result.substring(1);
        }
        return result;
    }

    /** Detecta a coluna PK — primeiro tenta patterns, depois heurística */
    private String detectPkColumn(List<EntityField> fields, String tableName) {
        // Tenta patterns
        TargetPatterns tp = patterns();
        if (tp != null && tableName != null && tp.getKnownTables().containsKey(tableName)) {
            return tp.getKnownTables().get(tableName).getPk();
        }
        // Procura campo cdg_ que parece ser PK
        for (EntityField f : fields) {
            if (f.colName.startsWith("cdg_") && f.colName.contains("_ped_") && !f.colName.contains("filial")) {
                return f.colName; // ex: cdg_ped_auto
            }
        }
        // Procura primeiro cdg_ que não é FK conhecida
        for (EntityField f : fields) {
            if (f.colName.startsWith("cdg_") && !KNOWN_FK_ENTITIES.containsKey(f.colName)) {
                return f.colName;
            }
        }
        // Fallback: primeiro campo inteiro
        for (EntityField f : fields) {
            if ("Integer".equals(f.javaType)) return f.colName;
        }
        return "id";
    }

    // ── Repository ───────────────────────────────────────────────────────────

    public String generateRepository(DelphiClass dc, String packageName) {
        String baseName = cleanClassNameWithProfile(dc.getName());
        String entityClass = baseName + "Entity";
        String repoName = baseName + "Repository";

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(".repository;\n\n");
        sb.append("import ").append(packageName).append(".entity.").append(entityClass).append(";\n");
        sb.append("import org.springframework.data.jpa.repository.JpaRepository;\n");
        sb.append("import org.springframework.data.jpa.repository.JpaSpecificationExecutor;\n");
        sb.append("import org.springframework.data.jpa.repository.Query;\n");
        sb.append("import org.springframework.data.repository.query.Param;\n");
        sb.append("import org.springframework.stereotype.Repository;\n");
        sb.append("import org.springframework.transaction.annotation.Transactional;\n\n");
        sb.append("import java.util.List;\n\n");

        sb.append("@Repository\n");
        sb.append("@Transactional(readOnly = true)\n");
        sb.append("public interface ").append(repoName);
        sb.append(" extends JpaRepository<").append(entityClass).append(", Integer>");
        sb.append(", JpaSpecificationExecutor<").append(entityClass).append("> {\n\n");

        sb.append("    // TODO: Adicionar metodos de consulta baseados nas queries SQL extraidas\n\n");

        sb.append("}\n");
        return sb.toString();
    }

    // ── Service ──────────────────────────────────────────────────────────────

    public String generateService(DelphiClass dc, String packageName,
                                   List<SqlQuery> sqlQueries, List<BusinessRule> rules) {
        String baseName = cleanClassNameWithProfile(dc.getName());
        String entityClass = baseName + "Entity";
        String repoName = baseName + "Repository";
        String serviceName = baseName + "Service";
        String dtoName = baseName + "Dto";
        String pesquisaDtoName = "Pesquisa" + baseName + "Dto";
        String voName = baseName + "GridVo";

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(".service;\n\n");
        sb.append("import ").append(packageName).append(".entity.").append(entityClass).append(";\n");
        sb.append("import ").append(packageName).append(".repository.").append(repoName).append(";\n");
        sb.append("import ").append(packageName).append(".dto.").append(toLowerFirst(baseName)).append(".").append(dtoName).append(";\n");
        sb.append("import ").append(packageName).append(".dto.").append(toLowerFirst(baseName)).append(".").append(pesquisaDtoName).append(";\n");
        sb.append("import ").append(packageName).append(".vo.").append(toLowerFirst(baseName)).append(".").append(voName).append(";\n");
        sb.append("import ").append(packageName).append(".dto.generic.ResultDto;\n");
        sb.append("import ").append(packageName).append(".helper.ServiceHelper;\n");
        sb.append("import ").append(packageName).append(".config.security.AuthFacade;\n");
        sb.append("import logus.exception.ValidationException;\n\n");
        sb.append("import org.springframework.beans.factory.annotation.Autowired;\n");
        sb.append("import org.springframework.data.domain.Page;\n");
        sb.append("import org.springframework.data.domain.Pageable;\n");
        sb.append("import org.springframework.stereotype.Service;\n");
        sb.append("import org.springframework.transaction.annotation.Transactional;\n\n");
        sb.append("import java.math.BigInteger;\n");
        sb.append("import java.util.*;\n\n");

        sb.append("@Service\n");
        sb.append("public class ").append(serviceName).append(" {\n\n");

        // Autowired fields
        sb.append("    @Autowired\n");
        sb.append("    private ").append(repoName).append(" repository;\n\n");
        sb.append("    @Autowired\n");
        sb.append("    private AuthFacade authFacade;\n\n");

        // pesquisar
        sb.append("    public ResultDto<").append(voName).append("> pesquisar(").append(pesquisaDtoName).append(" dto) {\n");
        sb.append("        Pageable pageRequest = ServiceHelper.getInstance()\n");
        sb.append("                .createPageRequest(dto.getLazyDto(), ").append(voName).append(".class, \"id\");\n\n");
        sb.append("        Page<").append(voName).append("> page = this.repository.findAll(pageRequest)\n");
        sb.append("                .map(").append(voName).append("::new);\n\n");
        sb.append("        return new ResultDto<>(dto.getLazyDto(), page.getContent(),\n");
        sb.append("                BigInteger.valueOf(page.getTotalElements()));\n");
        sb.append("    }\n\n");

        // getById
        sb.append("    public ").append(dtoName).append(" getById(Integer id) {\n");
        sb.append("        ").append(entityClass).append(" entity = this.repository.findById(id)\n");
        sb.append("                .orElseThrow(() -> new ValidationException(\"Registro nao encontrado. ID: \" + id));\n");
        sb.append("        return new ").append(dtoName).append("(entity);\n");
        sb.append("    }\n\n");

        // save
        sb.append("    @Transactional\n");
        sb.append("    public void save(").append(dtoName).append(" dto) {\n");
        if (!rules.isEmpty()) {
            sb.append("        validate(dto);\n");
        }
        sb.append("        ").append(entityClass).append(" entity;\n");
        sb.append("        if (dto.getIsEdicao() != null && dto.getIsEdicao()) {\n");
        sb.append("            entity = this.repository.findById(dto.getId())\n");
        sb.append("                    .orElseThrow(() -> new ValidationException(\"Registro nao encontrado para edicao.\"));\n");
        sb.append("        } else {\n");
        sb.append("            entity = new ").append(entityClass).append("();\n");
        sb.append("        }\n");
        sb.append("        // TODO: copiar campos do DTO para a Entity\n");
        sb.append("        this.repository.save(entity);\n");
        sb.append("    }\n\n");

        // delete
        sb.append("    @Transactional\n");
        sb.append("    public void delete(Integer id) {\n");
        sb.append("        ").append(entityClass).append(" entity = this.repository.findById(id)\n");
        sb.append("                .orElseThrow(() -> new ValidationException(\"Registro nao encontrado para exclusao.\"));\n");
        sb.append("        this.repository.delete(entity);\n");
        sb.append("    }\n\n");

        // findAll (mantido para compatibilidade com testes)
        sb.append("    public List<").append(entityClass).append("> findAll() {\n");
        sb.append("        return this.repository.findAll();\n");
        sb.append("    }\n\n");

        // findById (mantido para compatibilidade)
        sb.append("    public Optional<").append(entityClass).append("> findById(Integer id) {\n");
        sb.append("        return this.repository.findById(id);\n");
        sb.append("    }\n\n");

        // deleteById (mantido para compatibilidade)
        sb.append("    @Transactional\n");
        sb.append("    public void deleteById(Integer id) {\n");
        sb.append("        this.repository.deleteById(id);\n");
        sb.append("    }\n\n");

        // validate
        if (!rules.isEmpty()) {
            sb.append("    private void validate(").append(dtoName).append(" dto) {\n");
            for (BusinessRule rule : rules) {
                sb.append("        // ").append(rule.getDescription()).append("\n");
                if (rule.getSuggestedJavaCode() != null) {
                    for (String line : rule.getSuggestedJavaCode().split("\n")) {
                        sb.append("        ").append(line).append("\n");
                    }
                    sb.append("\n");
                }
            }
            sb.append("    }\n\n");
        }

        // SQL comments
        for (int i = 0; i < Math.min(sqlQueries.size(), 5); i++) {
            SqlQuery q = sqlQueries.get(i);
            sb.append("    // Migrado de SQL: ").append(q.getId()).append("\n");
            String sqlPreview = q.getSql().replaceAll("\n", " ");
            sb.append("    // Original: ").append(sqlPreview.substring(0, Math.min(60, sqlPreview.length()))).append("...\n");
            if (q.getRepositoryMethod() != null) {
                sb.append("    // Sugestao: ").append(q.getRepositoryMethod()).append("\n\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    // ── Resource (Controller) ────────────────────────────────────────────────

    public String generateController(DelphiClass dc, String packageName) {
        String baseName = cleanClassNameWithProfile(dc.getName());
        String serviceName = baseName + "Service";
        String resourceName = baseName + "Resource";
        String dtoName = baseName + "Dto";
        String pesquisaDtoName = "Pesquisa" + baseName + "Dto";
        String voName = baseName + "GridVo";
        String urlPath = "/" + toLowerFirst(baseName);

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(".resource;\n\n");
        sb.append("import ").append(packageName).append(".service.").append(serviceName).append(";\n");
        sb.append("import ").append(packageName).append(".dto.").append(toLowerFirst(baseName)).append(".").append(dtoName).append(";\n");
        sb.append("import ").append(packageName).append(".dto.").append(toLowerFirst(baseName)).append(".").append(pesquisaDtoName).append(";\n");
        sb.append("import ").append(packageName).append(".vo.").append(toLowerFirst(baseName)).append(".").append(voName).append(";\n");
        sb.append("import ").append(packageName).append(".dto.generic.ResultDto;\n");
        sb.append("import logus.exception.LogusException;\n");
        sb.append("import logus.exception.ValidationException;\n\n");
        sb.append("import io.swagger.annotations.ApiOperation;\n");
        sb.append("import io.swagger.annotations.ApiParam;\n");
        sb.append("import org.springframework.beans.factory.annotation.Autowired;\n");
        sb.append("import org.springframework.http.HttpStatus;\n");
        sb.append("import org.springframework.http.ResponseEntity;\n");
        sb.append("import org.springframework.web.bind.annotation.*;\n\n");
        sb.append("import javax.servlet.http.HttpServletRequest;\n");
        sb.append("import javax.validation.Valid;\n\n");

        sb.append("@RestController\n");
        sb.append("@RequestMapping(\"").append(urlPath).append("\")\n");
        sb.append("@CrossOrigin\n");
        sb.append("public class ").append(resourceName).append(" {\n\n");

        sb.append("    @Autowired\n");
        sb.append("    private ").append(serviceName).append(" service;\n\n");

        // pesquisar
        sb.append("    @PostMapping(\"/pesquisar\")\n");
        sb.append("    @ApiParam(value = \"").append(pesquisaDtoName).append("\", required = true)\n");
        sb.append("    @ApiOperation(value = \"Pesquisa registros\", response = ").append(voName).append(".class, responseContainer = \"List\")\n");
        sb.append("    public ResponseEntity pesquisar(@RequestBody ").append(pesquisaDtoName).append(" dto, HttpServletRequest req) {\n");
        sb.append("        try {\n");
        sb.append("            return new ResponseEntity<>(this.service.pesquisar(dto), HttpStatus.OK);\n");
        sb.append("        } catch (ValidationException e) {\n");
        sb.append("            return new ResponseEntity<>(e.getMessage(), HttpStatus.CONFLICT);\n");
        sb.append("        } catch (Exception e) {\n");
        sb.append("            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        // getById
        sb.append("    @GetMapping(\"/getById/{id}\")\n");
        sb.append("    @ApiParam(value = \"Id\", required = true)\n");
        sb.append("    @ApiOperation(value = \"Retorna o registro por ID\")\n");
        sb.append("    public ResponseEntity getById(@PathVariable Integer id, HttpServletRequest req) {\n");
        sb.append("        try {\n");
        sb.append("            return new ResponseEntity<>(this.service.getById(id), HttpStatus.OK);\n");
        sb.append("        } catch (ValidationException e) {\n");
        sb.append("            return new ResponseEntity<>(e.getMessage(), HttpStatus.CONFLICT);\n");
        sb.append("        } catch (Exception e) {\n");
        sb.append("            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        // save
        sb.append("    @PostMapping(\"/save\")\n");
        sb.append("    @ApiParam(value = \"").append(dtoName).append("\", required = true)\n");
        sb.append("    @ApiOperation(value = \"Salva registro\")\n");
        sb.append("    public ResponseEntity save(@Valid @RequestBody ").append(dtoName).append(" dto, HttpServletRequest req) {\n");
        sb.append("        try {\n");
        sb.append("            this.service.save(dto);\n");
        sb.append("            return new ResponseEntity<>(HttpStatus.OK);\n");
        sb.append("        } catch (ValidationException e) {\n");
        sb.append("            return new ResponseEntity<>(e.getMessage(), HttpStatus.CONFLICT);\n");
        sb.append("        } catch (Exception e) {\n");
        sb.append("            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        // delete
        sb.append("    @DeleteMapping(\"/delete/{id}\")\n");
        sb.append("    @ApiParam(value = \"Id\", required = true)\n");
        sb.append("    @ApiOperation(value = \"Deleta o registro por ID\")\n");
        sb.append("    public ResponseEntity delete(@PathVariable Integer id, HttpServletRequest req) {\n");
        sb.append("        try {\n");
        sb.append("            this.service.delete(id);\n");
        sb.append("            return new ResponseEntity<>(HttpStatus.OK);\n");
        sb.append("        } catch (ValidationException e) {\n");
        sb.append("            return new ResponseEntity<>(e.getMessage(), HttpStatus.CONFLICT);\n");
        sb.append("        } catch (Exception e) {\n");
        sb.append("            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);\n");
        sb.append("        }\n");
        sb.append("    }\n\n");

        sb.append("}\n");
        return sb.toString();
    }

    // ── DTO ──────────────────────────────────────────────────────────────────

    public String generateDto(DelphiClass dc, String packageName) {
        return generateDto(dc, packageName, null);
    }

    public String generateDto(DelphiClass dc, String packageName, List<DfmForm.DatasetField> dfmFields) {
        String baseName = cleanClassNameWithProfile(dc.getName());
        String entityClass = baseName + "Entity";
        String dtoName = baseName + "Dto";

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(".dto.").append(toLowerFirst(baseName)).append(";\n\n");
        sb.append("import ").append(packageName).append(".entity.").append(entityClass).append(";\n\n");
        sb.append("import java.io.Serializable;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.util.Date;\n\n");

        sb.append("public class ").append(dtoName).append(" implements Serializable {\n\n");
        sb.append("    private static final long serialVersionUID = 1L;\n\n");
        sb.append("    private Integer id;\n");
        sb.append("    private Boolean isEdicao;\n");

        List<String[]> fieldList = buildFieldList(dc, dfmFields);
        for (String[] f : fieldList) {
            sb.append("    private ").append(f[0]).append(" ").append(f[1]).append(";\n");
        }

        // Constructors
        sb.append("\n    public ").append(dtoName).append("() {\n        super();\n    }\n\n");
        sb.append("    public ").append(dtoName).append("(final ").append(entityClass).append(" entity) {\n");
        sb.append("        super();\n");
        sb.append("        this.id = entity.getId();\n");
        for (String[] f : fieldList) {
            sb.append("        this.").append(f[1]).append(" = entity.get").append(capitalize(f[1])).append("();\n");
        }
        sb.append("    }\n\n");

        // Getters & Setters
        sb.append("    public Integer getId() { return id; }\n");
        sb.append("    public void setId(Integer id) { this.id = id; }\n");
        sb.append("    public Boolean getIsEdicao() { return isEdicao; }\n");
        sb.append("    public void setIsEdicao(Boolean isEdicao) { this.isEdicao = isEdicao; }\n\n");

        for (String[] f : fieldList) {
            String cap = capitalize(f[1]);
            sb.append("    public ").append(f[0]).append(" get").append(cap).append("() { return ").append(f[1]).append("; }\n");
            sb.append("    public void set").append(cap).append("(").append(f[0]).append(" ").append(f[1]).append(") { this.").append(f[1]).append(" = ").append(f[1]).append("; }\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    // ── PesquisaDto ──────────────────────────────────────────────────────────

    public String generatePesquisaDto(DelphiClass dc, String packageName) {
        return generatePesquisaDto(dc, packageName, null);
    }

    public String generatePesquisaDto(DelphiClass dc, String packageName, List<DfmForm.DatasetField> dfmFields) {
        String baseName = cleanClassNameWithProfile(dc.getName());
        String pesquisaDtoName = "Pesquisa" + baseName + "Dto";

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(".dto.").append(toLowerFirst(baseName)).append(";\n\n");
        sb.append("import ").append(packageName).append(".lazyload.dto.LazyLoadDto;\n\n");
        sb.append("import java.io.Serializable;\n\n");

        sb.append("public class ").append(pesquisaDtoName).append(" implements Serializable {\n\n");
        sb.append("    private static final long serialVersionUID = 1L;\n\n");
        sb.append("    private LazyLoadDto lazyDto;\n");

        List<String[]> fieldList = buildFieldList(dc, dfmFields);
        for (String[] f : fieldList) {
            sb.append("    private String ").append(f[1]).append(";\n");
        }

        sb.append("\n    public LazyLoadDto getLazyDto() { return lazyDto; }\n");
        sb.append("    public void setLazyDto(LazyLoadDto lazyDto) { this.lazyDto = lazyDto; }\n\n");

        for (String[] f : fieldList) {
            String cap = capitalize(f[1]);
            sb.append("    public String get").append(cap).append("() { return ").append(f[1]).append("; }\n");
            sb.append("    public void set").append(cap).append("(String ").append(f[1]).append(") { this.").append(f[1]).append(" = ").append(f[1]).append("; }\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    // ── GridVo ───────────────────────────────────────────────────────────────

    public String generateVo(DelphiClass dc, String packageName) {
        return generateVo(dc, packageName, null);
    }

    public String generateVo(DelphiClass dc, String packageName, List<DfmForm.DatasetField> dfmFields) {
        String baseName = cleanClassNameWithProfile(dc.getName());
        String entityClass = baseName + "Entity";
        String voName = baseName + "GridVo";

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(".vo.").append(toLowerFirst(baseName)).append(";\n\n");
        sb.append("import ").append(packageName).append(".entity.").append(entityClass).append(";\n\n");
        sb.append("import java.io.Serializable;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.util.Date;\n\n");

        sb.append("public class ").append(voName).append(" implements Serializable {\n\n");
        sb.append("    private static final long serialVersionUID = 1L;\n\n");
        sb.append("    private Integer id;\n");

        // VO usa apenas campos visíveis (se veio do DFM)
        List<String[]> fieldList = buildFieldList(dc, dfmFields, true);
        for (String[] f : fieldList) {
            sb.append("    private ").append(f[0]).append(" ").append(f[1]).append(";\n");
        }

        // Constructor from Entity
        sb.append("\n    public ").append(voName).append("() { super(); }\n\n");
        sb.append("    public ").append(voName).append("(final ").append(entityClass).append(" entity) {\n");
        sb.append("        this.id = entity.getId();\n");
        for (String[] f : fieldList) {
            sb.append("        this.").append(f[1]).append(" = entity.get").append(capitalize(f[1])).append("();\n");
        }
        sb.append("    }\n\n");

        // Getters & Setters
        sb.append("    public Integer getId() { return id; }\n");
        sb.append("    public void setId(Integer id) { this.id = id; }\n\n");

        for (String[] f : fieldList) {
            String cap = capitalize(f[1]);
            sb.append("    public ").append(f[0]).append(" get").append(cap).append("() { return ").append(f[1]).append("; }\n");
            sb.append("    public void set").append(cap).append("(").append(f[0]).append(" ").append(f[1]).append(") { this.").append(f[1]).append(" = ").append(f[1]).append("; }\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    /** Monta lista de campos: primeiro tenta DelphiClass, se vazio usa dfmFields */
    private List<String[]> buildFieldList(DelphiClass dc, List<DfmForm.DatasetField> dfmFields) {
        return buildFieldList(dc, dfmFields, false);
    }

    private List<String[]> buildFieldList(DelphiClass dc, List<DfmForm.DatasetField> dfmFields, boolean onlyVisible) {
        List<String[]> fieldList = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Tenta do DelphiClass
        for (DelphiField field : dc.getFields()) {
            if (field.isComponent()) continue;
            String javaType = mapToApiType(field.getJavaType());
            String javaField = toCamelCase(removeFieldPrefix(field.getName()));
            if (!javaField.isEmpty() && !javaField.equals("id") && seen.add(javaField)) {
                fieldList.add(new String[]{javaType, javaField});
            }
        }

        // Fallback: usa DFM fields
        if (fieldList.isEmpty() && dfmFields != null) {
            for (DfmForm.DatasetField df : dfmFields) {
                if (onlyVisible && !df.isVisible()) continue;
                String javaField = snakeToCamel(df.getName());
                if (!javaField.isEmpty() && !javaField.equals("id") && seen.add(javaField)) {
                    fieldList.add(new String[]{df.getJavaType(), javaField});
                }
            }
        }
        return fieldList;
    }

    // ── Utils ────────────────────────────────────────────────────────────────

    private String cleanClassName(String name) {
        return name.replaceAll("(?i)^T(Form|frm|Frm|f_|F_|DM|DataModule)?", "T")
                   .replaceAll("(?i)(Form|frm|DM|DataModule)$", "")
                   .replaceAll("^T", "");
    }

    private String removeFieldPrefix(String fieldName) {
        String prefix = fieldPrefix();
        if (fieldName.startsWith(prefix) && fieldName.length() > prefix.length()) {
            return fieldName.substring(prefix.length());
        }
        return fieldName;
    }

    private String mapToApiType(String javaType) {
        if (javaType == null) return "String";
        switch (javaType) {
            case "Long":          return "Integer";
            case "LocalDateTime": return "Date";
            case "BigDecimal":    return "BigDecimal";
            case "Integer":       return "Integer";
            case "Boolean":       return "Boolean";
            case "Double":        return "BigDecimal";
            case "Float":         return "BigDecimal";
            case "String":        return "String";
            case "Character":     return "String";
            default:              return "String";
        }
    }

    private String toSnakeCase(String s) {
        return s.replaceAll("([A-Z])", "_$1").toLowerCase().replaceAll("^_", "");
    }

    private String toCamelCase(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private String toLowerFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String toKebabCase(String s) {
        return s.replaceAll("([A-Z])", "-$1").toLowerCase().replaceAll("^-", "");
    }

    /** Converte snake_case para camelCase: cdg_ped_auto -> cdgPedAuto */
    private String snakeToCamel(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (char c : s.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else {
                sb.append(nextUpper ? Character.toUpperCase(c) : c);
                nextUpper = false;
            }
        }
        // Garante que começa com minúscula
        if (sb.length() > 0) sb.setCharAt(0, Character.toLowerCase(sb.charAt(0)));
        return sb.toString();
    }
}
