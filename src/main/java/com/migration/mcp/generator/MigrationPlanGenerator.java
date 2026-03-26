package com.migration.mcp.generator;

import com.migration.mcp.model.*;
import com.migration.mcp.model.ProjectProfileStore;
import java.time.LocalDate;
import java.util.*;

/**
 * Gera plano de migração estruturado a partir da análise do projeto Delphi.
 */
public class MigrationPlanGenerator {

    public MigrationPlan generate(List<DelphiUnit> units, List<DfmForm> forms, String projectName) {
        MigrationPlan plan = new MigrationPlan();
        plan.setProjectName(projectName);
        plan.setAnalysisDate(LocalDate.now().toString());

        // Summary
        MigrationPlan.Summary summary = buildSummary(units, forms);
        plan.setSummary(summary);

        // Architecture — usa dados do perfil se disponível
        plan.setArchitectureSuggestion(buildArchitectureSuggestion(units));

        // Phases — enriquece com dados do perfil
        plan.setPhases(buildPhases(units, forms, summary));

        // Risks — inclui riscos específicos do perfil
        plan.setRisks(identifyRisks(units, forms, summary));

        // Recommendations — inclui recomendações específicas do projeto
        plan.setRecommendations(buildRecommendations(units, forms));

        return plan;
    }

    private ProjectProfile profile() {
        return ProjectProfileStore.getInstance().get();
    }

    private MigrationPlan.Summary buildSummary(List<DelphiUnit> units, List<DfmForm> forms) {
        MigrationPlan.Summary s = new MigrationPlan.Summary();
        s.setTotalUnits(units.size());
        s.setTotalForms(forms.size());

        int classes = units.stream().mapToInt(u -> u.getClasses().size()).sum();
        int methods = units.stream()
                .flatMap(u -> u.getClasses().stream())
                .mapToInt(c -> c.getMethods().size()).sum();
        int sql = units.stream().mapToInt(u -> u.getSqlQueries().size()).sum();
        int rules = units.stream().mapToInt(u -> u.getBusinessRules().size()).sum();

        s.setTotalClasses(classes);
        s.setTotalMethods(methods);
        s.setTotalSqlQueries(sql);
        s.setTotalBusinessRules(rules);

        // Estima complexidade
        int totalItems = classes + sql + rules + forms.size();
        String complexity;
        String effort;
        if (totalItems < 50) {
            complexity = "low";
            effort = "4 a 8 semanas";
        } else if (totalItems < 150) {
            complexity = "medium";
            effort = "3 a 6 meses";
        } else if (totalItems < 400) {
            complexity = "high";
            effort = "6 a 12 meses";
        } else {
            complexity = "very_high";
            effort = "12 a 24 meses";
        }
        s.setEstimatedComplexity(complexity);
        s.setEstimatedEffortWeeks(effort);
        return s;
    }

    private MigrationPlan.ArchitectureSuggestion buildArchitectureSuggestion(List<DelphiUnit> units) {
        MigrationPlan.ArchitectureSuggestion arch = new MigrationPlan.ArchitectureSuggestion();
        arch.setBackendFramework("Spring Boot 2.1.x (Java 8)");
        arch.setFrontendFramework("Angular 10 com PrimeNG 11");

        ProjectProfile p = profile();

        // Estratégia de BD baseada no perfil
        if (p != null && p.getDbVendor() != null) {
            String dbStrategy = switch (p.getDbVendor()) {
                case "Firebird"    -> "JPA/Hibernate + Flyway  |  Driver: org.firebirdsql:jaybird";
                case "SQL Server"  -> "JPA/Hibernate + Flyway  |  BD: SQL Server  |  Driver: com.microsoft.sqlserver:mssql-jdbc";
                case "Informix"    -> "JPA/Hibernate + Flyway  |  BD: Informix  |  Driver: com.ibm.informix:jdbc  |  Dialect: org.hibernate.dialect.InformixDialect";
                case "Oracle"      -> "JPA/Hibernate + Flyway  |  Driver: com.oracle.database.jdbc:ojdbc11";
                case "MySQL"       -> "JPA/Hibernate + Flyway  |  Driver: com.mysql:mysql-connector-j";
                case "PostgreSQL"  -> "JPA/Hibernate + Flyway  |  Driver: org.postgresql:postgresql";
                default            -> "JPA/Hibernate + Flyway";
            };
            arch.setDatabaseStrategy(dbStrategy);
        } else {
            arch.setDatabaseStrategy("JPA/Hibernate + Flyway para migrations + HikariCP connection pool");
        }

        arch.setAuthStrategy("Spring Security + JWT (stateless) + BCrypt");

        // Dependências enriquecidas com base no perfil
        List<String> deps = new ArrayList<>(List.of(
                "spring-boot-starter-web (2.1.x)",
                "spring-boot-starter-data-jpa",
                "spring-boot-starter-security",
                "spring-boot-starter-validation",
                "spring-boot-starter-actuator",
                "springfox-swagger2 (Swagger 2)",
                "lombok",
                "mapstruct",
                "io.jsonwebtoken:jjwt",
                "primeng (11.x)  ← componentes UI",
                "primeicons",
                "rxjs"
        ));

        // Adiciona libs específicas para o vendor de BD detectado
        if (p != null && p.getDbVendor() != null) {
            String jdbcDep = switch (p.getDbVendor()) {
                case "Firebird"   -> "org.firebirdsql:jaybird";
                case "SQL Server" -> "com.microsoft.sqlserver:mssql-jdbc";
                case "Informix"   -> "com.ibm.informix:jdbc";
                case "Oracle"     -> "com.oracle.database.jdbc:ojdbc11";
                case "MySQL"      -> "com.mysql:mysql-connector-j";
                case "PostgreSQL" -> "org.postgresql:postgresql";
                default -> null;
            };
            if (jdbcDep != null) deps.add(jdbcDep);
        }

        // Adiciona sugestões para libs de terceiros detectadas
        if (p != null) {
            if (p.getThirdPartyLibs().contains("FastReport") || p.getThirdPartyLibs().contains("QuickReport")) {
                deps.add("net.sf.jasperreports:jasperreports  ← substitui FastReport/QuickReport");
            }
            if (p.getThirdPartyLibs().contains("ACBr")) {
                deps.add("⚠️  ACBr não tem equivalente Java direto — avaliar libs fiscais Java");
            }
            if (p.getThirdPartyLibs().contains("Indy") || p.getThirdPartyLibs().contains("Synapse")) {
                deps.add("spring-boot-starter-webflux  ← substitui comunicação TCP/HTTP do Indy/Synapse");
            }
            if (p.getCodePatterns().isUsesThreads()) {
                deps.add("spring-boot-starter-batch  ← para processos assíncronos (TThread/TTask)");
            }
        }

        arch.setSuggestedDependencies(deps);
        arch.setProjectStructure(buildProjectStructure(p, units));
        return arch;
    }

    private String buildProjectStructure(ProjectProfile p, List<DelphiUnit> units) {
        String formPrefix  = p != null ? p.getNaming().getFormPrefix()       : "frm";
        String dmPrefix    = p != null ? p.getNaming().getDataModulePrefix()  : "dm";
        String queryPrefix = p != null ? p.getNaming().getQueryPrefix()       : "qry";

        StringBuilder sb = new StringBuilder();
        sb.append("backend/\n");
        sb.append("  src/main/java/\n");
        sb.append("    resource/       ← @RestController  (padrão Logus: Resource + DTO + VO + ResultDto)\n");
        sb.append("    service/        ← @Service         (migrado de lógica dos DataModules ").append(dmPrefix).append("*)\n");
        sb.append("    repository/     ← JpaRepository    (migrado de ").append(queryPrefix).append("* / TTable)\n");
        sb.append("    entity/         ← @Entity          (migrado de classes de dados)\n");
        sb.append("    dto/            ← DTOs de request/response\n");
        sb.append("    vo/             ← Value Objects (padrão Logus)\n");
        sb.append("    config/         ← Segurança, CORS, DataSource\n");
        sb.append("    exception/      ← BusinessException + @ControllerAdvice\n");

        if (p != null && p.getSqlConventions().isUsesStoredProcs()) {
            sb.append("    procedure/      ← @Procedure wrappers (StoredProcs detectadas)\n");
        }

        sb.append("frontend/\n");
        sb.append("  src/app/\n");
        sb.append("    core/           ← Serviços singleton, guards, interceptors\n");
        sb.append("    shared/         ← Componentes reutilizáveis (DataGrid compartilhado)\n");
        sb.append("    common/         ← Models, enums, interfaces\n");

        // Gera features baseado nos arquivos analisados, não do perfil inteiro
        sb.append("    modules/\n");
        Set<String> modulesFromUnits = new LinkedHashSet<>();
        for (DelphiUnit u : units) {
            String path = u.getFilePath();
            if (path != null) {
                // Extrai nome do módulo da pasta do arquivo
                String normalized = path.replace("\\", "/");
                String[] parts = normalized.split("/");
                for (int i = 0; i < parts.length - 1; i++) {
                    if (parts[i].equals("delphi-corporativo") && i + 1 < parts.length - 1) {
                        modulesFromUnits.add(parts[i + 1]);
                        break;
                    }
                }
            }
        }
        if (!modulesFromUnits.isEmpty()) {
            for (String mod : modulesFromUnits) {
                String modRoute = mod.toLowerCase().replace(" ", "-");
                sb.append("      ").append(modRoute).append("/\n");
                sb.append("        container/   ← Container components (smart)\n");
                sb.append("        grid/        ← Grid/listagem components\n");
                sb.append("        filtros/     ← Filtros components\n");
                sb.append("        cadastro/    ← Cadastro/edição components\n");
                sb.append("        services/    ← Services HTTP\n");
            }
        } else {
            sb.append("      feature/        ← Um módulo Angular por módulo funcional\n");
        }

        return sb.toString();
    }

    private List<MigrationPlan.Phase> buildPhases(List<DelphiUnit> units, List<DfmForm> forms, MigrationPlan.Summary summary) {
        List<MigrationPlan.Phase> phases = new ArrayList<>();

        // Fase 1 — Fundação
        MigrationPlan.Phase phase1 = new MigrationPlan.Phase();
        phase1.setPhaseNumber(1);
        phase1.setName("Fundação e Infraestrutura");
        phase1.setDescription("Configurar a estrutura base do projeto Spring Boot e Angular antes de migrar qualquer funcionalidade.");
        phase1.setEstimatedWeeks("2 semanas");
        phase1.setPriority("critical");
        phase1.setTasks(List.of(
                "Criar projeto Spring Boot 2.1.x com Maven (padrão Logus API)",
                "Configurar Spring Security + JWT (padrão Logus: field injection, Swagger 2)",
                "Configurar conexão com banco de dados (datasource, Flyway)",
                "Criar módulo Angular 10 no logus-corporativo-web com PrimeNG 11",
                "Configurar proxy e CORS entre frontend e backend",
                "Definir padrões de código (Resource/DTO/VO/ResultDto padrão Logus)",
                "Configurar CI/CD pipeline",
                "Criar scripts de migration Flyway para o schema existente"
        ));
        phases.add(phase1);

        // Fase 2 — Domínio / Entidades
        MigrationPlan.Phase phase2 = new MigrationPlan.Phase();
        phase2.setPhaseNumber(2);
        phase2.setName("Migração do Domínio e Persistência");
        phase2.setDescription("Migrar classes de dados, queries SQL e repositórios.");
        phase2.setEstimatedWeeks("3 a 5 semanas");
        phase2.setPriority("high");
        List<String> phase2Tasks = new ArrayList<>(List.of(
                "Criar @Entity JPA para cada classe de dados identificada (" + summary.getTotalClasses() + " classes)",
                "Criar JpaRepository para cada entidade",
                "Migrar " + summary.getTotalSqlQueries() + " queries SQL para JPQL / métodos de repository",
                "Implementar paginação e ordenação com Pageable",
                "Criar DTOs e mappers (MapStruct)"
        ));
        if (summary.getTotalSqlQueries() > 20) {
            phase2Tasks.add("ATENÇÃO: " + summary.getTotalSqlQueries() + " queries SQL — priorizar queries críticas primeiro");
        }
        phase2.setTasks(phase2Tasks);
        phases.add(phase2);

        // Fase 3 — Regras de negócio
        MigrationPlan.Phase phase3 = new MigrationPlan.Phase();
        phase3.setPhaseNumber(3);
        phase3.setName("Migração das Regras de Negócio");
        phase3.setDescription("Mover lógica de negócio dos Forms/Units Delphi para Services Spring Boot.");
        phase3.setEstimatedWeeks("3 a 6 semanas");
        phase3.setPriority("high");
        List<String> phase3Tasks = new ArrayList<>(List.of(
                "Implementar @Service para cada módulo funcional",
                "Migrar " + summary.getTotalBusinessRules() + " regras de negócio identificadas",
                "Substituir ShowMessage/Exception Delphi por BusinessException Java",
                "Implementar Bean Validation (@NotNull, @Size, @Pattern) nas entidades",
                "Criar @ControllerAdvice para tratamento global de exceções",
                "Escrever testes unitários (JUnit 5 + Mockito) para cada Service"
        ));
        phase3.setTasks(phase3Tasks);
        phases.add(phase3);

        // Fase 4 — APIs REST
        MigrationPlan.Phase phase4 = new MigrationPlan.Phase();
        phase4.setPhaseNumber(4);
        phase4.setName("Criação das APIs REST");
        phase4.setDescription("Expor a lógica de negócio através de endpoints REST.");
        phase4.setEstimatedWeeks("2 a 3 semanas");
        phase4.setPriority("high");
        phase4.setTasks(List.of(
                "Criar @RestController para cada entidade/módulo",
                "Implementar endpoints CRUD completos",
                "Configurar Swagger/OpenAPI 3 para documentação",
                "Implementar paginação, filtros e ordenação nas listagens",
                "Configurar rate limiting e validação de input",
                "Criar testes de integração com @SpringBootTest"
        ));
        phases.add(phase4);

        // Fase 5 — Frontend Angular
        MigrationPlan.Phase phase5 = new MigrationPlan.Phase();
        phase5.setPhaseNumber(5);
        phase5.setName("Migração do Frontend para Angular");
        phase5.setDescription("Converter " + forms.size() + " formulários Delphi em componentes Angular.");
        phase5.setEstimatedWeeks(forms.size() < 10 ? "3 semanas" : forms.size() < 30 ? "6 a 8 semanas" : "12+ semanas");
        phase5.setPriority("high");

        List<String> phase5Tasks = new ArrayList<>(List.of(
                "Criar Angular Services para consumir APIs REST (padrão Logus: BehaviorSubject state)",
                "Reutilizar AuthService + interceptor JWT existente no logus-corporativo-web",
                "Configurar Angular Router com guards de autenticação",
                "Criar " + forms.size() + " componentes Angular (padrão Logus: Container/Grid/Filtros/Cadastro)",
                "Implementar Reactive Forms para todos os formulários",
                "Criar componentes de tabela com p-table PrimeNG (shared DataGrid Logus)",
                "Usar p-dropdown com [filter] para lookups (substitui TLgCorporativoLookupComboEdit)",
                "Usar p-calendar para campos de data (substitui TJvDateEdit)"
        ));

        // Adiciona tasks específicas por formulário analisado
        for (DfmForm form : forms) {
            if (form.getFormName() != null) {
                int compCount = form.getComponents() != null ? form.getComponents().size() : 0;
                phase5Tasks.add(String.format("Migrar '%s' (%d componentes) → componente Angular",
                        form.getFormName(), compCount));
            }
        }

        phase5.setTasks(phase5Tasks);
        phases.add(phase5);

        // Fase 6 — Testes e Homologação
        MigrationPlan.Phase phase6 = new MigrationPlan.Phase();
        phase6.setPhaseNumber(6);
        phase6.setName("Testes, Homologação e Deploy");
        phase6.setDescription("Validar a migração completa com os usuários e preparar para produção.");
        phase6.setEstimatedWeeks("3 a 4 semanas");
        phase6.setPriority("critical");
        phase6.setTasks(List.of(
                "Testes de regressão funcional comparando comportamento Delphi vs Java/Angular",
                "Testes de performance (JMeter) — especialmente queries críticas",
                "Testes de segurança (OWASP ZAP, penetration testing básico)",
                "UAT (User Acceptance Testing) com usuários-chave",
                "Configurar monitoramento (Actuator + Prometheus + Grafana)",
                "Configurar logging centralizado (ELK Stack ou Loki)",
                "Deploy em staging, validação final e cutover para produção",
                "Documentar manual de operação do novo sistema"
        ));
        phases.add(phase6);

        return phases;
    }

    private List<String> identifyRisks(List<DelphiUnit> units, List<DfmForm> forms, MigrationPlan.Summary summary) {
        List<String> risks = new ArrayList<>();
        ProjectProfile p = profile();

        if (summary.getTotalSqlQueries() > 30) {
            risks.add("ALTO: Muitas queries SQL inline (" + summary.getTotalSqlQueries() + ") — risco de perda de lógica de filtros/relatórios específicos");
        }
        if (summary.getTotalBusinessRules() > 50) {
            risks.add("ALTO: Grande quantidade de regras de negócio (" + summary.getTotalBusinessRules() + ") — risco de incompletude na migração");
        }
        if (forms.size() > 30) {
            risks.add("MÉDIO: Muitos formulários (" + forms.size() + ") — risco de atrasos no frontend");
        }

        // Riscos específicos do perfil aprendido
        if (p != null) {
            if (p.getSqlConventions().isUsesDynamicSql()) {
                risks.add("ALTO: SQL dinâmico detectado (concatenação de strings) — risco de SQL Injection e difícil migração para JPA");
            }
            if (p.getSqlConventions().isUsesStoredProcs()) {
                risks.add("MÉDIO: Stored procedures detectadas — avaliar mover lógica para Java ou manter como @Procedure");
            }
            if (p.getThirdPartyLibs().contains("ACBr")) {
                risks.add("ALTO: ACBr detectado — sem equivalente Java direto, requer avaliação de solução fiscal separada");
            }
            if (p.getThirdPartyLibs().contains("DevExpress")) {
                risks.add("MÉDIO: DevExpress detectado — grids complexos precisarão de equivalente Angular (AG Grid ou DevExtreme for Angular)");
            }
            if (p.getThirdPartyLibs().contains("FastReport") || p.getThirdPartyLibs().contains("QuickReport")) {
                risks.add("MÉDIO: Relatórios " + p.getThirdPartyLibs() + " detectados — migrar para JasperReports ou similar");
            }
            if (p.getCodePatterns().isUsesThreads()) {
                risks.add("MÉDIO: TThread/TTask detectado — lógica assíncrona requer atenção especial na migração");
            }
            if (p.getDetectedDelphiVersion() != null && p.getDetectedDelphiVersion().contains("7")) {
                risks.add("ALTO: Delphi 7 detectado — provável uso de BDE e DFM binário, maior esforço de migração");
            }
        }

        risks.addAll(List.of(
                "MÉDIO: Lógica de negócio misturada com eventos de UI nos Forms — necessita refatoração durante migração",
                "MÉDIO: Usuários acostumados com interface Delphi — resistência à mudança de UX",
                "BAIXO: Campos 'Variant' do Delphi precisam de análise caso a caso para tipagem Java",
                "BAIXO: Tratamento de transações implícito do Delphi precisa ser explicitado com @Transactional"
        ));

        return risks;
    }

    private List<String> buildRecommendations(List<DelphiUnit> units, List<DfmForm> forms) {
        return List.of(
                "Adotar abordagem de migração incremental (strangler fig pattern) — rodar Delphi e Java em paralelo durante transição",
                "Priorizar módulos com menos dependências para validar a arquitetura antes dos módulos críticos",
                "Criar testes automatizados ANTES de migrar cada módulo para garantir paridade de comportamento",
                "Documentar cada regra de negócio identificada antes de migrar — reuniões com especialistas de domínio",
                "Utilizar feature flags para ativar/desativar funcionalidades migradas em produção com segurança",
                "Considerar GraphQL ou OpenAPI-first para definir contratos de API antes de implementar",
                "Avaliar o uso de Spring Batch para processos batch que existiam como rotinas Delphi",
                "Implementar cache (Redis/Caffeine) nas consultas mais frequentes identificadas nas queries extraídas",
                "Treinar a equipe em Spring Boot, Angular e conceitos modernos de arquitetura antes de iniciar"
        );
    }

    /**
     * Gera o plano como Markdown estruturado
     */
    public String toMarkdown(MigrationPlan plan) {
        StringBuilder md = new StringBuilder();
        MigrationPlan.Summary s = plan.getSummary();

        md.append("# 📋 Plano de Migração Delphi → Java/Angular\n\n");
        md.append("**Projeto:** ").append(plan.getProjectName()).append("\n");
        md.append("**Data da análise:** ").append(plan.getAnalysisDate()).append("\n\n");

        md.append("## 📊 Resumo da Análise\n\n");
        md.append("| Métrica | Total |\n|---|---|\n");
        md.append("| Units analisadas | ").append(s.getTotalUnits()).append(" |\n");
        md.append("| Formulários (DFM) | ").append(s.getTotalForms()).append(" |\n");
        md.append("| Classes/Tipos | ").append(s.getTotalClasses()).append(" |\n");
        md.append("| Métodos/Procedures | ").append(s.getTotalMethods()).append(" |\n");
        md.append("| Queries SQL encontradas | ").append(s.getTotalSqlQueries()).append(" |\n");
        md.append("| Regras de negócio | ").append(s.getTotalBusinessRules()).append(" |\n");
        md.append("| **Complexidade estimada** | **").append(s.getEstimatedComplexity().toUpperCase()).append("** |\n");
        md.append("| **Esforço estimado** | **").append(s.getEstimatedEffortWeeks()).append("** |\n\n");

        md.append("## 🏗️ Arquitetura Sugerida\n\n");
        MigrationPlan.ArchitectureSuggestion arch = plan.getArchitectureSuggestion();
        md.append("- **Backend:** ").append(arch.getBackendFramework()).append("\n");
        md.append("- **Frontend:** ").append(arch.getFrontendFramework()).append("\n");
        md.append("- **Banco de dados:** ").append(arch.getDatabaseStrategy()).append("\n");
        md.append("- **Autenticação:** ").append(arch.getAuthStrategy()).append("\n\n");
        md.append("```\n").append(arch.getProjectStructure()).append("```\n\n");

        md.append("## 🗺️ Fases de Migração\n\n");
        for (MigrationPlan.Phase phase : plan.getPhases()) {
            md.append("### Fase ").append(phase.getPhaseNumber()).append(": ").append(phase.getName());
            md.append(" `").append(phase.getEstimatedWeeks()).append("`\n\n");
            md.append(phase.getDescription()).append("\n\n");
            for (String task : phase.getTasks()) {
                md.append("- [ ] ").append(task).append("\n");
            }
            md.append("\n");
        }

        md.append("## ⚠️ Riscos Identificados\n\n");
        for (String risk : plan.getRisks()) {
            md.append("- ").append(risk).append("\n");
        }

        md.append("\n## 💡 Recomendações\n\n");
        for (String rec : plan.getRecommendations()) {
            md.append("- ").append(rec).append("\n");
        }

        return md.toString();
    }
}
