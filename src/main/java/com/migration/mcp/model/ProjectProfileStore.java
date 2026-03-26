package com.migration.mcp.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;

/**
 * Singleton que armazena o ProjectProfile aprendido.
 *
 * O perfil é mantido em memória durante a sessão e persistido em disco
 * em um diretório fixo para sobreviver a reinícios do servidor.
 * Caminho fixo garante que CLI e VS Code leiam do mesmo local.
 */
public class ProjectProfileStore {

    private static final Logger log = LoggerFactory.getLogger(ProjectProfileStore.class);

    /** Diretório fixo — mesmo para CLI, VS Code, testes, qualquer processo */
    private static final Path PROFILE_DIR;
    private static final Path PROFILE_FILE;

    static {
        Path dir;
        try {
            dir = Path.of(System.getProperty("user.home", "C:\\Users\\Usuario"), ".delphi-mcp");
        } catch (Exception e) {
            dir = Path.of("C:\\Users\\Usuario\\.delphi-mcp");
        }
        PROFILE_DIR = dir;
        PROFILE_FILE = dir.resolve("project-profile.json");
    }

    // INSTANCE deve vir DEPOIS do static block para que PROFILE_FILE já esteja inicializado
    private static final ProjectProfileStore INSTANCE = new ProjectProfileStore();

    private static final Path PATTERNS_FILE = PROFILE_DIR != null ? PROFILE_DIR.resolve("entity-patterns.json") : null;

    private final ObjectMapper mapper;
    private ProjectProfile current;
    private TargetPatterns patterns;

    private ProjectProfileStore() {
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.current = loadFromDisk();
        this.patterns = loadPatternsFromDisk();
        log.info("ProfileStore inicializado — dir={}, perfilCarregado={}, patternsCarregado={}",
                PROFILE_DIR, current != null, patterns != null);
    }

    public static ProjectProfileStore getInstance() {
        return INSTANCE;
    }

    // ── Acesso ───────────────────────────────────────────────────────────────

    public boolean hasProfile() {
        return current != null;
    }

    public ProjectProfile get() {
        return current;
    }

    public void save(ProjectProfile profile) {
        this.current = profile;
        persistToDisk(profile);
        log.info("Perfil salvo: projeto='{}', tecnologia={}, {} módulos detectados",
                profile.getProjectName(), profile.getDbTechnology(), profile.getModules().size());
    }

    public void clear() {
        this.current = null;
        try {
            Files.deleteIfExists(PROFILE_FILE);
        } catch (IOException e) {
            log.warn("Não foi possível remover o arquivo de perfil: {}", e.getMessage());
        }
    }

    // ── Helpers para os geradores ─────────────────────────────────────────────

    /** Retorna o prefixo de form aprendido, ou "frm" como fallback */
    public String formPrefix() {
        return current != null ? current.getNaming().getFormPrefix() : "frm";
    }

    /** Retorna o prefixo de datamodule aprendido */
    public String dmPrefix() {
        return current != null ? current.getNaming().getDataModulePrefix() : "dm";
    }

    /** Retorna a tecnologia de banco detectada */
    public String dbTechnology() {
        return current != null && current.getDbTechnology() != null
                ? current.getDbTechnology() : "JPA/Hibernate";
    }

    /** Retorna o estilo de parâmetro SQL detectado */
    public String paramStyle() {
        return current != null ? current.getSqlConventions().getParamStyle() : "named_colon";
    }

    /** Retorna true se o projeto usa stored procedures */
    public boolean usesStoredProcs() {
        return current != null && current.getSqlConventions().isUsesStoredProcs();
    }

    /** Retorna o prefixo de campo privado (ex: "F" para FId, FNome) */
    public String fieldPrefix() {
        return current != null ? current.getNaming().getFieldPrefix() : "F";
    }

    /** Retorna o package Java base sugerido com base no nome do projeto */
    public String suggestBasePackage() {
        if (current == null) return "com.empresa.projeto";
        String name = current.getProjectName()
                .toLowerCase()
                .replaceAll("[^a-z0-9]", ".");
        return "br.com." + name;
    }

    // ── Target Patterns ─────────────────────────────────────────────────────

    public TargetPatterns getPatterns() { return patterns; }

    public boolean hasPatterns() { return patterns != null; }

    public void loadPatterns(String filePath) throws IOException {
        Path path = filePath != null ? Path.of(filePath) : PATTERNS_FILE;
        if (path != null && Files.exists(path)) {
            this.patterns = mapper.readValue(path.toFile(), TargetPatterns.class);
            log.info("Patterns carregados: {} expansions, {} FKs, {} enums, {} tables",
                    patterns.getColumnNameExpansions().size(),
                    patterns.getKnownForeignKeys().size(),
                    patterns.getKnownEnums().size(),
                    patterns.getKnownTables().size());
        } else {
            throw new IOException("Arquivo de patterns não encontrado: " + path);
        }
    }

    private TargetPatterns loadPatternsFromDisk() {
        if (PATTERNS_FILE == null || !Files.exists(PATTERNS_FILE)) return null;
        try {
            TargetPatterns p = mapper.readValue(PATTERNS_FILE.toFile(), TargetPatterns.class);
            log.info("Patterns carregados do disco: {} expansions, {} FKs",
                    p.getColumnNameExpansions().size(), p.getKnownForeignKeys().size());
            return p;
        } catch (IOException e) {
            log.warn("Não foi possível carregar patterns: {}", e.getMessage());
            return null;
        }
    }

    // ── Persistência ─────────────────────────────────────────────────────────

    private void persistToDisk(ProjectProfile profile) {
        try {
            Files.createDirectories(PROFILE_DIR);
            mapper.writeValue(PROFILE_FILE.toFile(), profile);
            log.info("Perfil persistido em {}", PROFILE_FILE);
        } catch (IOException e) {
            log.warn("Não foi possível persistir perfil em disco: {}", e.getMessage());
        }
    }

    private ProjectProfile loadFromDisk() {
        if (!Files.exists(PROFILE_FILE)) return null;
        try {
            ProjectProfile profile = mapper.readValue(PROFILE_FILE.toFile(), ProjectProfile.class);
            log.info("Perfil carregado do disco: projeto='{}' de {}", profile.getProjectName(), PROFILE_FILE);
            return profile;
        } catch (IOException e) {
            log.warn("Não foi possível carregar perfil do disco: {}", e.getMessage());
            return null;
        }
    }
}
