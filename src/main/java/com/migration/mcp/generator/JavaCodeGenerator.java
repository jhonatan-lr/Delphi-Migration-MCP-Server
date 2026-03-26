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
        return generateEntity(dc, packageName, null);
    }

    public String generateEntity(DelphiClass dc, String packageName, List<DfmForm.DatasetField> dfmFields) {
        StringBuilder sb = new StringBuilder();
        String baseName = cleanClassNameWithProfile(dc.getName());
        String entityClass = baseName + "Entity";
        String tableName = toTableName(baseName);

        sb.append("package ").append(packageName).append(".entity;\n\n");
        sb.append("import javax.persistence.*;\n");
        sb.append("import java.io.Serializable;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.util.Date;\n\n");

        sb.append("@Entity\n");
        sb.append("@Table(name = \"").append(tableName).append("\")\n");
        sb.append("@Access(AccessType.FIELD)\n");
        sb.append("public class ").append(entityClass).append(" implements Serializable {\n\n");
        sb.append("    private static final long serialVersionUID = 1L;\n\n");

        // Id
        sb.append("    @Id\n");
        sb.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
        sb.append("    @Column(name = \"cdg_id\")\n");
        sb.append("    private Integer id;\n\n");

        // Fields — primeiro tenta do DelphiClass, se vazio usa dfmFields do DFM
        List<String[]> fieldList = new ArrayList<>();
        for (DelphiField field : dc.getFields()) {
            if (field.isComponent()) continue;
            String colName = toColumnName(field.getName());
            String javaType = mapToApiType(field.getJavaType());
            String javaField = toCamelCase(removeFieldPrefix(field.getName()));
            fieldList.add(new String[]{colName, javaType, javaField});
        }

        // Fallback: usa campos extraídos do DFM (TClientDataSet fields)
        if (fieldList.isEmpty() && dfmFields != null && !dfmFields.isEmpty()) {
            Set<String> seenFields = new HashSet<>();
            for (DfmForm.DatasetField df : dfmFields) {
                String colName = df.getName();
                String javaType = df.getJavaType();
                String javaField = snakeToCamel(colName);
                // Evita duplicatas e campos que são o próprio ID
                if (javaField.equals("id") || javaField.isEmpty() || !seenFields.add(javaField)) continue;
                fieldList.add(new String[]{colName, javaType, javaField});
            }
        }

        for (String[] f : fieldList) {
            sb.append("    @Column(name = \"").append(f[0]).append("\")\n");
            sb.append("    private ").append(f[1]).append(" ").append(f[2]).append(";\n\n");
        }

        // Constructor vazio
        sb.append("    public ").append(entityClass).append("() {\n");
        sb.append("        super();\n");
        sb.append("    }\n\n");

        // Getters and Setters
        sb.append("    public Integer getId() { return id; }\n");
        sb.append("    public void setId(Integer id) { this.id = id; }\n\n");

        for (String[] f : fieldList) {
            String cap = capitalize(f[2]);
            sb.append("    public ").append(f[1]).append(" get").append(cap).append("() { return ").append(f[2]).append("; }\n");
            sb.append("    public void set").append(cap).append("(").append(f[1]).append(" ").append(f[2]).append(") { this.").append(f[2]).append(" = ").append(f[2]).append("; }\n\n");
        }

        sb.append("}\n");
        return sb.toString();
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
