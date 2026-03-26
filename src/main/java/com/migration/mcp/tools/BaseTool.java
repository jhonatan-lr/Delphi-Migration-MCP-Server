package com.migration.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Base para todos os MCP Tools de migração Delphi.
 */
public abstract class BaseTool {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final ObjectMapper objectMapper;

    protected BaseTool() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public abstract McpServerFeatures.SyncToolSpecification getSpecification();

    /**
     * Wraps a tool handler with automatic start/finish/duration/error logging.
     */
    protected McpSchema.CallToolResult withLogging(String toolName, Map<String, Object> args,
                                                    ToolHandler handler) {
        long start = System.currentTimeMillis();
        log.info("[TOOL-START] {} | args: {}", toolName, summarizeArgs(args));
        try {
            McpSchema.CallToolResult result = handler.execute();
            long elapsed = System.currentTimeMillis() - start;
            log.info("[TOOL-END]   {} | OK em {}ms ({})", toolName, elapsed, formatDuration(elapsed));
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[TOOL-FAIL]  {} | ERRO após {}ms ({}): {}", toolName, elapsed, formatDuration(elapsed), e.getMessage(), e);
            return error("Erro em " + toolName + ": " + e.getMessage());
        }
    }

    @FunctionalInterface
    protected interface ToolHandler {
        McpSchema.CallToolResult execute() throws Exception;
    }

    private String summarizeArgs(Map<String, Object> args) {
        if (args == null || args.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, Object> e : args.entrySet()) {
            if (i++ > 0) sb.append(", ");
            String val = e.getValue() != null ? e.getValue().toString() : "null";
            // Trunca valores longos (conteúdo de arquivo)
            if (val.length() > 80) val = val.substring(0, 77) + "...(" + val.length() + " chars)";
            sb.append(e.getKey()).append("=").append(val);
        }
        sb.append("}");
        return sb.toString();
    }

    protected static String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60_000) return String.format("%.1fs", ms / 1000.0);
        long min = ms / 60_000;
        long sec = (ms % 60_000) / 1000;
        return min + "m" + sec + "s";
    }

    protected McpSchema.CallToolResult success(Object result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(json)), false);
        } catch (Exception e) {
            return error("Erro ao serializar resultado: " + e.getMessage());
        }
    }

    protected McpSchema.CallToolResult successText(String text) {
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(text)), false);
    }

    protected McpSchema.CallToolResult error(String message) {
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("{\"error\": \"" + message + "\"}")), true);
    }

    protected String requireString(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val == null) throw new IllegalArgumentException("Parâmetro obrigatório ausente: " + key);
        return val.toString();
    }

    protected String optionalString(Map<String, Object> args, String key, String defaultValue) {
        Object val = args.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    protected String buildInputSchema(String... fieldDefs) {
        StringBuilder sb = new StringBuilder("{\"type\":\"object\",\"properties\":{");
        for (int i = 0; i < fieldDefs.length; i += 3) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(fieldDefs[i]).append("\":{")
              .append("\"type\":\"").append(fieldDefs[i + 1]).append("\",")
              .append("\"description\":\"").append(fieldDefs[i + 2]).append("\"}");
        }
        sb.append("},\"required\":[\"").append(fieldDefs[0]).append("\"]}");
        return sb.toString();
    }
}
