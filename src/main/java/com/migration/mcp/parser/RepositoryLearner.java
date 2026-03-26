package com.migration.mcp.parser;

import com.migration.mcp.model.ProjectProfile;
import com.migration.mcp.model.ProjectProfile.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * Varre um repositório Delphi local e constrói um ProjectProfile
 * com todos os padrões detectados: nomenclatura, DB, SQL, componentes, módulos.
 */
public class RepositoryLearner {

    private static final Logger log = LoggerFactory.getLogger(RepositoryLearner.class);

    // ── Detecção de tecnologia de BD ──────────────────────────────────────────
    private static final Map<String, String> DB_TECH_SIGNATURES = new LinkedHashMap<>();
    static {
        DB_TECH_SIGNATURES.put("TFDConnection|TFDQuery|TFDTable|TFDStoredProc", "FireDAC");
        DB_TECH_SIGNATURES.put("TADOConnection|TADOQuery|TADOTable",             "ADO");
        DB_TECH_SIGNATURES.put("TIBDatabase|TIBQuery|TIBTable|TIBTransaction",  "IBX (InterBase/Firebird)");
        DB_TECH_SIGNATURES.put("TZConnection|TZQuery|TZTable",                  "ZeosLib");
        DB_TECH_SIGNATURES.put("TUniConnection|TUniQuery",                      "UniDAC");
        DB_TECH_SIGNATURES.put("TDatabase|TTable|TQuery|TBatchMove",            "BDE");
        DB_TECH_SIGNATURES.put("TSQLConnection|TSQLQuery",                      "dbExpress");
    }

    private static final Map<String, String> DB_VENDOR_SIGNATURES = new LinkedHashMap<>();
    static {
        DB_VENDOR_SIGNATURES.put("MSSQL|SQL Server|sqloledb|SQLNCLI",          "SQL Server");
        DB_VENDOR_SIGNATURES.put("Informix|informix|ifx|SQLI",                 "Informix");
        DB_VENDOR_SIGNATURES.put("Firebird|Interbase|gds32|fbclient",          "Firebird");
        DB_VENDOR_SIGNATURES.put("Oracle|OraOLEDB|ora",                         "Oracle");
        DB_VENDOR_SIGNATURES.put("MySQL|mysql",                                  "MySQL");
        DB_VENDOR_SIGNATURES.put("PostgreSQL|postgres|npgsql",                  "PostgreSQL");
        DB_VENDOR_SIGNATURES.put("SQLite|sqlite3",                              "SQLite");
    }

    private static final Map<String, String> THIRD_PARTY_SIGNATURES = new LinkedHashMap<>();
    static {
        THIRD_PARTY_SIGNATURES.put("TcxGrid|TdxGrid|TcxDBGrid|DevExpress",     "DevExpress");
        THIRD_PARTY_SIGNATURES.put("TRvReport|TRvSystem",                       "Rave Reports");
        THIRD_PARTY_SIGNATURES.put("TfrxReport|TfrxDBDataset",                  "FastReport");
        THIRD_PARTY_SIGNATURES.put("TQRBand|TQuickRep",                         "QuickReport");
        THIRD_PARTY_SIGNATURES.put("TMS|AdvGrid|TAdvEdit",                      "TMS Components");
        THIRD_PARTY_SIGNATURES.put("TElegantRibbon|TRibbon",                    "Ribbon UI");
        THIRD_PARTY_SIGNATURES.put("TACBrNFe|TACBrBoleto|ACBr",               "ACBr");
        THIRD_PARTY_SIGNATURES.put("TEthereal|TJvEdit|JvValidateEdit|JEDI|Jv\\w+",  "JEDI/JVCL");
        THIRD_PARTY_SIGNATURES.put("TwwDBGrid|TwwEdit|Wwdbigrd|Wwdbgrid|InfoPower","InfoPower");
        THIRD_PARTY_SIGNATURES.put("LgBitBtn|PngBitBtn|LgCorporativo|u_Logus",    "Logus Custom Components");
        THIRD_PARTY_SIGNATURES.put("nfdNFe|nfdNFe2|ComunicadorSefaz",             "NFe/Sefaz Integration");
        THIRD_PARTY_SIGNATURES.put("TClientDataSet|TDataSetProvider",              "ClientDataSet/DataSnap");
        THIRD_PARTY_SIGNATURES.put("TIndy|TIdHTTP|TIdTCPClient",               "Indy");
        THIRD_PARTY_SIGNATURES.put("Synapse|TBlockSocket",                      "Synapse");
    }

    // ── Padrões de versão Delphi ──────────────────────────────────────────────
    private static final Map<String, String> VERSION_HINTS = new LinkedHashMap<>();
    static {
        VERSION_HINTS.put("\\{\\$IFDEF VER150\\}|Delphi 7",                    "Delphi 7");
        VERSION_HINTS.put("\\{\\$IFDEF VER180\\}|Delphi 2006",                 "Delphi 2006");
        VERSION_HINTS.put("\\{\\$IFDEF VER200\\}|Delphi 2009",                 "Delphi 2009");
        VERSION_HINTS.put("\\{\\$IFDEF VER220\\}|Delphi XE",                   "Delphi XE");
        VERSION_HINTS.put("\\{\\$IFDEF VER230\\}|Delphi XE2",                  "Delphi XE2");
        VERSION_HINTS.put("\\{\\$IFDEF VER260\\}|Delphi XE5",                  "Delphi XE5");
        VERSION_HINTS.put("\\{\\$IFDEF VER280\\}|Delphi XE7",                  "Delphi XE7");
        VERSION_HINTS.put("\\{\\$IFDEF VER290\\}|Delphi XE8",                  "Delphi XE8");
        VERSION_HINTS.put("\\{\\$IFDEF VER300\\}|Delphi 10 Seattle",           "Delphi 10 Seattle");
        VERSION_HINTS.put("\\{\\$IFDEF VER310\\}|Delphi 10.1 Berlin",          "Delphi 10.1 Berlin");
        VERSION_HINTS.put("\\{\\$IFDEF VER320\\}|Delphi 10.2 Tokyo",           "Delphi 10.2 Tokyo");
        VERSION_HINTS.put("\\{\\$IFDEF VER330\\}|Delphi 10.3 Rio",             "Delphi 10.3 Rio");
        VERSION_HINTS.put("\\{\\$IFDEF VER340\\}|Delphi 10.4 Sydney",          "Delphi 10.4 Sydney");
        VERSION_HINTS.put("\\{\\$IFDEF VER350\\}|Delphi 11",                   "Delphi 11 Alexandria");
        VERSION_HINTS.put("\\{\\$IFDEF VER360\\}|Delphi 12",                   "Delphi 12 Athens");
    }

    // ─────────────────────────────────────────────────────────────────────────

    public ProjectProfile learn(String repositoryPath) throws IOException {
        Path root = Path.of(repositoryPath);
        if (!Files.exists(root)) throw new IOException("Diretório não encontrado: " + repositoryPath);

        long totalStart = System.currentTimeMillis();
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║  LEARN REPOSITORY — Início                                  ║");
        log.info("╚══════════════════════════════════════════════════════════════╝");
        log.info("Repositório: {}", repositoryPath);

        ProjectProfile profile = new ProjectProfile();
        profile.setRepositoryPath(repositoryPath);
        profile.setProjectName(root.getFileName().toString());
        profile.setLearnedAt(LocalDateTime.now().toString());

        // Coleta todos os arquivos .pas e .dfm
        long t = System.currentTimeMillis();
        List<Path> pasFiles = collectFiles(root, ".pas");
        List<Path> dfmFiles = collectFiles(root, ".dfm");
        profile.setTotalFilesScanned(pasFiles.size() + dfmFiles.size());
        log.info("[1/11] Coleta de arquivos: {} .pas + {} .dfm = {} total ({}ms)",
                pasFiles.size(), dfmFiles.size(), pasFiles.size() + dfmFiles.size(), elapsed(t));

        // Lê todos os conteúdos (com limite de tamanho para arquivos grandes)
        t = System.currentTimeMillis();
        List<String> allPasContents = readContents(pasFiles);
        long totalChars = allPasContents.stream().mapToLong(String::length).sum();
        log.info("[2/11] Leitura de {} arquivos .pas: {} chars total ({} MB) — {}ms",
                allPasContents.size(), totalChars,
                String.format("%.1f", totalChars / (1024.0 * 1024.0)), elapsed(t));

        // Análises — cada etapa logada com tempo
        t = System.currentTimeMillis();
        learnNamingConventions(profile, pasFiles, dfmFiles, allPasContents);
        log.info("[3/11]  Nomenclatura detectada — {}ms (formPrefix={}, unitPrefix={}, queryPrefix={})",
                elapsed(t), profile.getNaming().getFormPrefix(),
                profile.getNaming().getUnitPrefix(), profile.getNaming().getQueryPrefix());

        t = System.currentTimeMillis();
        learnDbTechnology(profile, allPasContents);
        log.info("[4/11]  Tecnologia BD: {} / {} — {}ms",
                profile.getDbTechnology(), profile.getDbVendor(), elapsed(t));

        t = System.currentTimeMillis();
        learnThirdPartyLibs(profile, allPasContents);
        log.info("[5/11]  Bibliotecas 3rd-party: {} — {}ms",
                profile.getThirdPartyLibs(), elapsed(t));

        t = System.currentTimeMillis();
        learnDelphiVersion(profile, allPasContents);
        log.info("[6/11]  Versão Delphi: {} — {}ms",
                profile.getDetectedDelphiVersion(), elapsed(t));

        t = System.currentTimeMillis();
        learnFolderStructure(profile, root, pasFiles, dfmFiles);
        log.info("[7/11]  Estrutura de pastas — {}ms", elapsed(t));

        t = System.currentTimeMillis();
        learnModules(profile, root, pasFiles, dfmFiles);
        log.info("[8/11]  Módulos: {} detectados — {}ms",
                profile.getModules().size(), elapsed(t));

        t = System.currentTimeMillis();
        learnSqlConventions(profile, allPasContents);
        log.info("[9/11]  Convenções SQL: param={}, storedProcs={}, dynamicSql={} — {}ms",
                profile.getSqlConventions().getParamStyle(),
                profile.getSqlConventions().isUsesStoredProcs(),
                profile.getSqlConventions().isUsesDynamicSql(), elapsed(t));

        t = System.currentTimeMillis();
        learnCodePatterns(profile, allPasContents);
        log.info("[10/11] Padrões de código: validação={}, threads={}, interfaces={} — {}ms",
                profile.getCodePatterns().getValidationStyle(),
                profile.getCodePatterns().isUsesThreads(),
                profile.getCodePatterns().isUsesInterfaces(), elapsed(t));

        t = System.currentTimeMillis();
        learnComponentFrequency(profile, allPasContents);
        learnTables(profile, allPasContents);
        log.info("[11/11] Componentes e tabelas: {} componentes, {} tabelas — {}ms",
                profile.getTopComponents().size(), profile.getDetectedTables().size(), elapsed(t));

        long totalMs = System.currentTimeMillis() - totalStart;
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║  LEARN REPOSITORY — Concluído em {} ({})        ",
                formatDuration(totalMs), totalMs + "ms");
        log.info("║  {} arquivos | versão={} | BD={} | {} módulos | {} tabelas",
                profile.getTotalFilesScanned(), profile.getDetectedDelphiVersion(),
                profile.getDbTechnology(), profile.getModules().size(),
                profile.getDetectedTables().size());
        log.info("╚══════════════════════════════════════════════════════════════╝");

        return profile;
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    private static String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60_000) return String.format("%.1fs", ms / 1000.0);
        long min = ms / 60_000;
        long sec = (ms % 60_000) / 1000;
        return min + "m" + sec + "s";
    }

    // ── Coleta de arquivos ────────────────────────────────────────────────────

    private List<Path> collectFiles(Path root, String ext) throws IOException {
        return Files.walk(root)
                .filter(p -> p.toString().toLowerCase().endsWith(ext))
                .filter(p -> !p.toString().contains("__history"))
                .filter(p -> !p.toString().contains("backup"))
                .sorted()
                .collect(Collectors.toList());
    }

    private List<String> readContents(List<Path> files) {
        List<String> contents = new ArrayList<>();
        int total = files.size();
        int readOk = 0;
        int readFail = 0;
        int logInterval = Math.max(1, total / 10); // loga a cada 10%
        for (int i = 0; i < total; i++) {
            Path f = files.get(i);
            try {
                String content = Files.readString(f, StandardCharsets.UTF_8);
                contents.add(content);
                readOk++;
            } catch (Exception e) {
                try {
                    // Tenta Latin-1 para arquivos legados
                    String content = Files.readString(f, java.nio.charset.Charset.forName("ISO-8859-1"));
                    contents.add(content);
                    readOk++;
                } catch (Exception ex) {
                    readFail++;
                    log.warn("Não foi possível ler {}: {}", f, ex.getMessage());
                }
            }
            if ((i + 1) % logInterval == 0 || i == total - 1) {
                int pct = (int) ((i + 1) * 100.0 / total);
                log.info("  Lendo arquivos: {}/{} ({}%) — {} OK, {} falhas",
                        i + 1, total, pct, readOk, readFail);
            }
        }
        return contents;
    }

    // ── Aprendizado de nomenclatura ───────────────────────────────────────────

    private void learnNamingConventions(ProjectProfile profile,
                                        List<Path> pasFiles, List<Path> dfmFiles,
                                        List<String> contents) {
        NamingConventions n = profile.getNaming();

        // Aprende prefixo de form a partir dos nomes de arquivo
        Map<String, Integer> formPrefixes = new HashMap<>();
        for (Path f : pasFiles) {
            String name = f.getFileName().toString().replace(".pas", "").replace(".PAS", "");
            if (name.length() > 3) {
                // Detecta prefixos como f_, frm, Frm, fFrm, Form
                for (String prefix : List.of("f_", "F_", "frm", "Frm", "fFrm", "Form", "TForm")) {
                    if (name.startsWith(prefix)) {
                        formPrefixes.merge(prefix, 1, Integer::sum);
                        break;
                    }
                }
            }
        }
        if (!formPrefixes.isEmpty()) {
            n.setFormPrefix(topKey(formPrefixes));
        }

        // Prefixo de unit
        Map<String, Integer> unitPrefixes = new HashMap<>();
        for (Path f : pasFiles) {
            String name = f.getFileName().toString().replace(".pas", "");
            for (String prefix : List.of("u_", "U_", "u", "Un", "Unit")) {
                if (name.startsWith(prefix) && name.length() > prefix.length() &&
                        Character.isUpperCase(name.charAt(prefix.length()))) {
                    unitPrefixes.merge(prefix, 1, Integer::sum);
                    break;
                }
            }
        }
        if (!unitPrefixes.isEmpty()) n.setUnitPrefix(topKey(unitPrefixes));

        // Prefixo de report
        Map<String, Integer> reportPrefixes = new HashMap<>();
        for (Path f : pasFiles) {
            String name = f.getFileName().toString().replace(".pas", "");
            for (String prefix : List.of("r_", "R_", "rpt", "Rpt", "rep")) {
                if (name.startsWith(prefix)) {
                    reportPrefixes.merge(prefix, 1, Integer::sum);
                    break;
                }
            }
        }
        if (!reportPrefixes.isEmpty()) n.setReportPrefix(topKey(reportPrefixes));

        // Prefixo de DataModule a partir dos conteúdos (itera sem join)
        Map<String, Integer> dmPrefixes = new HashMap<>();
        Pattern dmPat = Pattern.compile("(?i)(\\w+)\\s*=\\s*class\\s*\\(TDataModule\\)");
        collectMatches(contents, dmPat, 1, cls -> {
            String withoutT = cls.startsWith("T") ? cls.substring(1) : cls;
            String prefix = withoutT.replaceAll("[A-Z].*", "");
            if (!prefix.isEmpty() && prefix.length() <= 4) {
                dmPrefixes.merge(prefix, 1, Integer::sum);
            }
        });
        if (!dmPrefixes.isEmpty()) n.setDataModulePrefix(topKey(dmPrefixes));

        // Prefixo de campo privado (F, _, m_)
        Map<String, Integer> fieldPrefixes = new HashMap<>();
        Pattern fieldPat = Pattern.compile("(?i)private[\\s\\S]{0,2000}?(F|_|m_)(\\w+)\\s*:");
        collectMatches(contents, fieldPat, 1, g -> fieldPrefixes.merge(g, 1, Integer::sum));
        if (!fieldPrefixes.isEmpty()) n.setFieldPrefix(topKey(fieldPrefixes));

        // Prefixo de query
        Map<String, Integer> queryPrefixes = new HashMap<>();
        Pattern qryPat = Pattern.compile("(?i)(\\w+)\\s*:\\s*T(?:FDQuery|ADOQuery|IBQuery|ZQuery|UniQuery|Query|SQLQuery)");
        collectMatches(contents, qryPat, 1, varName -> {
            String prefix = varName.replaceAll("[A-Z].*", "").toLowerCase();
            if (!prefix.isEmpty() && prefix.length() <= 5) {
                queryPrefixes.merge(prefix, 1, Integer::sum);
            }
        });
        if (!queryPrefixes.isEmpty()) n.setQueryPrefix(topKey(queryPrefixes));

        // Estilo de nomenclatura de tabelas SQL (UPPER_SNAKE, PascalCase, lower_snake)
        Map<String, Integer> tableStyles = new HashMap<>();
        Pattern tablePat = Pattern.compile("(?i)FROM\\s+(\\w+)");
        collectMatches(contents, tablePat, 1, tbl -> {
            if (tbl.equals(tbl.toUpperCase()) && tbl.matches("[A-Z_]+")) tableStyles.merge("UPPER_SNAKE", 1, Integer::sum);
            else if (tbl.contains("_") && tbl.equals(tbl.toLowerCase())) tableStyles.merge("lower_snake", 1, Integer::sum);
            else if (Character.isUpperCase(tbl.charAt(0)) && !tbl.contains("_")) tableStyles.merge("PascalCase", 1, Integer::sum);
        });
        if (!tableStyles.isEmpty()) n.setTableNamingStyle(topKey(tableStyles));
    }

    // ── Tecnologia de banco ───────────────────────────────────────────────────

    private void learnDbTechnology(ProjectProfile profile, List<String> contents) {
        for (Map.Entry<String, String> entry : DB_TECH_SIGNATURES.entrySet()) {
            if (anyMatch(contents, entry.getKey())) {
                profile.setDbTechnology(entry.getValue());
                log.debug("Tecnologia de BD detectada: {}", entry.getValue());
                break;
            }
        }
        if (profile.getDbTechnology() == null) profile.setDbTechnology("Desconhecida");

        for (Map.Entry<String, String> entry : DB_VENDOR_SIGNATURES.entrySet()) {
            if (anyMatch(contents, "(?i)" + entry.getKey())) {
                profile.setDbVendor(entry.getValue());
                log.debug("Vendor de BD detectado: {}", entry.getValue());
                break;
            }
        }
    }

    // ── Bibliotecas de terceiros ──────────────────────────────────────────────

    private void learnThirdPartyLibs(ProjectProfile profile, List<String> contents) {
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, String> entry : THIRD_PARTY_SIGNATURES.entrySet()) {
            if (anyMatch(contents, entry.getKey())) {
                found.add(entry.getValue());
            }
        }
        profile.setThirdPartyLibs(found);
    }

    // ── Versão do Delphi ──────────────────────────────────────────────────────

    private void learnDelphiVersion(ProjectProfile profile, List<String> contents) {
        for (Map.Entry<String, String> entry : VERSION_HINTS.entrySet()) {
            if (anyMatch(contents, entry.getKey())) {
                profile.setDetectedDelphiVersion(entry.getValue());
                return;
            }
        }
        // Inferência por tecnologia
        String tech = profile.getDbTechnology();
        if (tech != null) {
            if (tech.contains("FireDAC"))  profile.setDetectedDelphiVersion("XE3+ (por FireDAC)");
            else if (tech.contains("BDE")) profile.setDetectedDelphiVersion("Delphi 7 ou anterior (por BDE)");
            else if (tech.contains("ADO")) profile.setDetectedDelphiVersion("Delphi 6+ (por ADO)");
        }
        if (profile.getDetectedDelphiVersion() == null) profile.setDetectedDelphiVersion("Não detectada");
    }

    // ── Estrutura de pastas ───────────────────────────────────────────────────

    private void learnFolderStructure(ProjectProfile profile, Path root,
                                      List<Path> pasFiles, List<Path> dfmFiles) throws IOException {
        FolderStructure fs = profile.getFolderStructure();

        // Top-level folders
        List<String> topFolders = new ArrayList<>();
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                  .map(p -> p.getFileName().toString())
                  .filter(n -> !n.startsWith(".") && !n.equals("__history") && !n.equals("backup"))
                  .sorted()
                  .forEach(topFolders::add);
        }
        fs.setTopLevelFolders(topFolders);

        // Detecta pastas funcionais por densidade de arquivos
        Map<String, Long> folderDensity = new HashMap<>();
        for (Path f : pasFiles) {
            String folder = root.relativize(f.getParent()).toString();
            folderDensity.merge(folder, 1L, Long::sum);
        }

        // Identifica pastas especializadas pelo nome
        for (String folder : folderDensity.keySet()) {
            String lower = folder.toLowerCase();
            if (lower.contains("form") || lower.contains("view") || lower.contains("tela")) {
                fs.setFormsFolder(folder);
            }
            if (lower.contains("datamodule") || lower.contains("data") || lower.contains("dm")) {
                fs.setDataModulesFolder(folder);
            }
            if (lower.contains("frame") || lower.contains("fra")) {
                fs.setFramesFolder(folder);
            }
            if (lower.contains("report") || lower.contains("relat") || lower.contains("rpt")) {
                fs.setReportsFolder(folder);
            }
            if (lower.contains("util") || lower.contains("helper") || lower.contains("common")) {
                fs.setUtilsFolder(folder);
            }
        }
    }

    // ── Módulos do projeto ────────────────────────────────────────────────────

    private void learnModules(ProjectProfile profile, Path root,
                               List<Path> pasFiles, List<Path> dfmFiles) throws IOException {
        List<ProjectModule> modules = new ArrayList<>();

        // Agrupa arquivos por subdiretório de primeiro nível
        Map<String, List<Path>> byFolder = new LinkedHashMap<>();
        for (Path f : pasFiles) {
            Path relative = root.relativize(f);
            String folder = relative.getNameCount() > 1
                    ? relative.getName(0).toString()
                    : "(raiz)";
            byFolder.computeIfAbsent(folder, k -> new ArrayList<>()).add(f);
        }

        for (Map.Entry<String, List<Path>> entry : byFolder.entrySet()) {
            if (entry.getValue().isEmpty()) continue; // ignora pastas vazias

            ProjectModule mod = new ProjectModule();
            mod.setName(capitalize(entry.getKey().replace("_", " ").replace("-", " ")));
            mod.setFolderPath(entry.getKey());
            mod.setUnitCount((int) entry.getValue().stream()
                    .filter(p -> !p.toString().toLowerCase().contains("frm") && !p.toString().toLowerCase().contains("form"))
                    .count());

            long formCount = dfmFiles.stream()
                    .filter(p -> root.relativize(p).getNameCount() > 1 &&
                                 root.relativize(p).getName(0).toString().equals(entry.getKey()))
                    .count();
            mod.setFormCount((int) formCount);

            // Identifica forms principais (arquivos .dfm maiores)
            dfmFiles.stream()
                    .filter(p -> root.relativize(p).getNameCount() > 1 &&
                                 root.relativize(p).getName(0).toString().equals(entry.getKey()))
                    .limit(3)
                    .map(p -> p.getFileName().toString().replace(".dfm", ""))
                    .forEach(mod.getMainForms()::add);

            modules.add(mod);
        }

        // Ordena por quantidade de units (módulos maiores primeiro)
        modules.sort((a, b) -> (b.getUnitCount() + b.getFormCount()) - (a.getUnitCount() + a.getFormCount()));
        profile.setModules(modules);
    }

    // ── Convenções de SQL ─────────────────────────────────────────────────────

    private void learnSqlConventions(ProjectProfile profile, List<String> contents) {
        SqlConventions sql = profile.getSqlConventions();

        // Estilo de parâmetro
        int namedColon  = countMatchesAll(contents, ":\\b[A-Z_]+\\b");
        int namedAt     = countMatchesAll(contents, "@[A-Z_]+");
        int positional  = countMatchesAll(contents, "\\?");
        if (namedAt > namedColon && namedAt > positional)      sql.setParamStyle("named_at");
        else if (positional > namedColon && positional > namedAt) sql.setParamStyle("positional");
        else                                                     sql.setParamStyle("named_colon");

        // Stored procedures
        sql.setUsesStoredProcs(
                anyMatch(contents, "(?i)T(?:FD|ADO|IB|Z|Uni)?StoredProc|EXEC(UTE)?\\s+\\w+"));

        // Views
        sql.setUsesViews(
                anyMatch(contents, "(?i)FROM\\s+V[W_]\\w+|FROM\\s+VW\\w+|VIEW"));

        // SQL dinâmico (concatenação perigosa)
        sql.setUsesDynamicSql(
                anyMatch(contents, "(?i)\\.SQL\\.Add\\s*\\(.*\\+"));

        // Tabelas mais acessadas
        Map<String, Integer> tableFreq = new HashMap<>();
        Pattern tblPat = Pattern.compile("(?i)(?:FROM|JOIN|INTO|UPDATE)\\s+(\\w{3,30})");
        collectMatches(contents, tblPat, 1, g -> {
            String t = g.toUpperCase();
            if (!isReservedSqlWord(t)) tableFreq.merge(t, 1, Integer::sum);
        });
        sql.setTopTables(top10Keys(tableFreq));

        // Padrão de conexão
        if (anyMatch(contents, "(?i)dm\\w*\\.Con|DataModule.*Connection")) {
            sql.setConnectionPattern("DataModule");
        } else {
            sql.setConnectionPattern("Global/Singleton");
        }
    }

    // ── Padrões de código ─────────────────────────────────────────────────────

    private void learnCodePatterns(ProjectProfile profile, List<String> contents) {
        CodePatterns cp = profile.getCodePatterns();

        // Estilo de validação
        int showMsg  = countMatchesAll(contents, "(?i)ShowMessage\\s*\\(");
        int msgDlg   = countMatchesAll(contents, "(?i)MessageDlg\\s*\\(");
        int raiseEx  = countMatchesAll(contents, "(?i)raise\\s+Exception");
        if (raiseEx > showMsg)      cp.setValidationStyle("raise");
        else if (msgDlg > showMsg)  cp.setValidationStyle("messagedlg");
        else                        cp.setValidationStyle("showmessage");

        // Herança customizada
        cp.setUsesInheritance(
                anyMatch(contents, "(?i)class\\s*\\(T(?!Form|Object|DataModule|Frame|Thread|Component)[A-Z]\\w+\\)"));

        // Interfaces
        cp.setUsesInterfaces(
                anyMatch(contents, "(?i)\\bimplements\\b|IInterface|TInterfacedObject"));

        // Threads
        cp.setUsesThreads(
                anyMatch(contents, "(?i)TThread|TTask|TParallel|BeginThread"));

        // Timers
        cp.setUsesTimers(
                anyMatch(contents, "(?i)TTimer"));

        // Tratamento de erros
        cp.setErrorHandling(
                countMatchesAll(contents, "(?i)try") > 10 ? "try_except" : "raise");

        // Utils mais usadas
        Map<String, Integer> utils = new HashMap<>();
        Pattern usesPat = Pattern.compile("(?i)uses[^;]+;");
        for (String c : contents) {
            Matcher um = usesPat.matcher(c);
            while (um.find()) {
                for (String unit : um.group(0).replaceAll("(?i)uses\\s*", "").replace(";", "").split(",")) {
                    String u = unit.trim().replaceAll("\\s+.*", "");
                    if (u.length() > 2 && !isDelphiStdLib(u)) utils.merge(u, 1, Integer::sum);
                }
            }
        }
        cp.setCommonUtils(top10Keys(utils));
    }

    // ── Frequência de componentes ─────────────────────────────────────────────

    private void learnComponentFrequency(ProjectProfile profile, List<String> contents) {
        Map<String, Integer> freq = new HashMap<>();

        Pattern compPat = Pattern.compile("(?i)(T(?:FD|ADO|IB|Z|Uni|DB|SQL|cx|dx|ww|Jv|QR)?(?:Query|Table|Grid|Edit|Button|Panel|Form|Memo|Label|ComboBox|CheckBox|RadioButton|Timer|Image|Tree|Tab|Ribbon|Connection|Database|ClientDataSet|DataSource|DataSetProvider|QuickRep|Band|StoredProc|ValidateEdit|Lookup)\\w*)");
        collectMatches(contents, compPat, 1, g -> freq.merge(g, 1, Integer::sum));

        // Ordena por frequência
        Map<String, Integer> sorted = freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(20)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        profile.setComponentFrequency(sorted);
        profile.setTopComponents(new ArrayList<>(sorted.keySet()).subList(0, Math.min(10, sorted.size())));
    }

    // ── Tabelas detectadas ────────────────────────────────────────────────────

    private void learnTables(ProjectProfile profile, List<String> contents) {
        Set<String> tables = new LinkedHashSet<>();
        Pattern tablePat = Pattern.compile("(?i)(?:FROM|JOIN|INTO|UPDATE)\\s+(\\w{3,40})(?:\\s|\\n|,|\\()");
        collectMatches(contents, tablePat, 1, g -> {
            String t = g.toUpperCase();
            if (!isReservedSqlWord(t) && !t.startsWith("T") && t.length() > 2) {
                tables.add(t);
            }
        });

        // DataModules
        Set<String> dms = new LinkedHashSet<>();
        Pattern dmPat = Pattern.compile("(?i)(T\\w+)\\s*=\\s*class\\s*\\(TDataModule\\)");
        collectMatches(contents, dmPat, 1, dms::add);

        profile.setDetectedTables(new ArrayList<>(tables));
        profile.setDetectedDataModules(new ArrayList<>(dms));
    }

    // ── Helpers para iterar sem String.join ─────────────────────────────────

    /** Retorna true se o pattern encontra match em qualquer conteúdo da lista */
    private boolean anyMatch(List<String> contents, String regex) {
        Pattern p = Pattern.compile(regex);
        for (String c : contents) {
            if (p.matcher(c).find()) return true;
        }
        return false;
    }

    /** Conta total de matches do pattern em todos os conteúdos da lista */
    private int countMatchesAll(List<String> contents, String regex) {
        Pattern p = Pattern.compile(regex);
        int total = 0;
        for (String c : contents) {
            Matcher m = p.matcher(c);
            while (m.find()) total++;
        }
        return total;
    }

    /** Coleta todos os matches de um grupo em todos os conteúdos */
    private void collectMatches(List<String> contents, Pattern pattern, int group,
                                 java.util.function.Consumer<String> consumer) {
        for (String c : contents) {
            Matcher m = pattern.matcher(c);
            while (m.find()) consumer.accept(m.group(group));
        }
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private <K> K topKey(Map<K, Integer> map) {
        return map.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private List<String> top10Keys(Map<String, Integer> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private boolean isReservedSqlWord(String word) {
        Set<String> reserved = Set.of("SELECT","FROM","WHERE","AND","OR","NOT","IN","IS","NULL",
                "JOIN","INNER","LEFT","RIGHT","OUTER","ON","GROUP","BY","ORDER","HAVING",
                "UNION","ALL","DISTINCT","AS","SET","VALUES","INTO","TABLE","VIEW",
                "EXEC","EXECUTE","BEGIN","END","CASE","WHEN","THEN","ELSE","IF","EXISTS",
                "COUNT","SUM","MAX","MIN","AVG","UPPER","LOWER","TRIM","SUBSTRING",
                "VARCHAR","INTEGER","INT","DATE","CHAR","DECIMAL","NUMERIC","BOOLEAN",
                "THE","WITH","TOP","LIMIT","OFFSET","ASC","DESC","INDEX","PRIMARY","KEY");
        return reserved.contains(word.toUpperCase());
    }

    private boolean isDelphiStdLib(String unit) {
        return unit.matches("(?i)SysUtils|Classes|Forms|Controls|Graphics|Windows|Messages|" +
                "Dialogs|StdCtrls|ExtCtrls|ComCtrls|DBGrids|DB|Variants|Math|" +
                "DateUtils|StrUtils|IOUtils|System|Types|SysInit");
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
