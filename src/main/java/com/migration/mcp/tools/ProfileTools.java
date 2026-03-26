package com.migration.mcp.tools;

import com.migration.mcp.model.ProjectProfile;
import com.migration.mcp.model.ProjectProfileStore;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;

/**
 * Tool: get_learned_profile
 * Retorna o perfil que foi aprendido do repositório.
 */
class GetProfileTool extends BaseTool {

    @Override
    public McpServerFeatures.SyncToolSpecification getSpecification() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "get_learned_profile",
                "Retorna o perfil completo aprendido do repositório Delphi pela ferramenta learn_repository. " +
                "Mostra todas as convenções detectadas: nomenclatura, tecnologia de BD, módulos, SQL, componentes.",
                "{\"type\":\"object\",\"properties\":{}}"
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> withLogging("get_learned_profile", args, () -> {
            ProjectProfileStore store = ProjectProfileStore.getInstance();
            if (!store.hasProfile()) {
                return successText("{\"status\": \"Nenhum perfil aprendido ainda. " +
                        "Execute learn_repository primeiro.\"}");
            }
            return success(store.get());
        }));
    }
}

/**
 * Tool: clear_learned_profile
 * Remove o perfil aprendido, voltando ao comportamento genérico.
 */
class ClearProfileTool extends BaseTool {

    @Override
    public McpServerFeatures.SyncToolSpecification getSpecification() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "clear_learned_profile",
                "Remove o perfil aprendido do repositório, voltando ao comportamento genérico. " +
                "Útil para trocar de projeto.",
                "{\"type\":\"object\",\"properties\":{}}"
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> withLogging("clear_learned_profile", args, () -> {
            ProjectProfileStore.getInstance().clear();
            return successText("{\"status\": \"Perfil removido. As ferramentas voltarão ao comportamento genérico.\"}");
        }));
    }
}
