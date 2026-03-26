package com.migration.mcp.server;

import com.migration.mcp.tools.DelphiMigrationTools;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delphi Migration MCP Server — Entry Point
 *
 * MCP Server especialista em:
 *  - Analisar código-fonte Delphi (.pas, .dfm)
 *  - Extrair classes, métodos, queries SQL e regras de negócio
 *  - Gerar planos de migração completos (Markdown + JSON)
 *  - Gerar código Java/Spring Boot e Angular equivalente
 *
 * Comunicação via STDIO (padrão MCP), compatível com Claude Desktop,
 * VS Code MCP extension e qualquer cliente MCP.
 */
public class DelphiMigrationMcpServer {

    private static final Logger log = LoggerFactory.getLogger(DelphiMigrationMcpServer.class);

    public static void main(String[] args) {
        log.info("=== Delphi Migration MCP Server v1.0.0 ===");

        var tools = new DelphiMigrationTools();

        var server = McpServer.sync(new StdioServerTransportProvider())
                .serverInfo(new McpSchema.Implementation("delphi-migration-mcp", "1.0.0"))
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(tools.all())
                .build();

        log.info("Servidor iniciado. {} tools disponíveis:", tools.all().size());
        tools.all().forEach(t -> log.info("  ✓ {}", t.tool().name()));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Encerrando servidor...");
            server.closeGracefully();
        }));
    }
}
