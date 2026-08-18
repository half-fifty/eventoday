package com.min.edu.ai.client;

import com.min.edu.ai.tool.AiTool;
import com.min.edu.ai.tool.AiToolContext;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

@Component
public class AiToolCallbackFactory {

    public static final String AI_TOOL_CONTEXT_KEY = "aiToolContext";

    public List<ToolCallback> create(List<AiTool<?, ?>> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        return tools.stream()
            .map(this::create)
            .toList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <I, O> ToolCallback create(AiTool<I, O> tool) {
        return FunctionToolCallback
            .builder(tool.name(), (I input, ToolContext toolContext) -> {
                Object context = toolContext.getContext().get(AI_TOOL_CONTEXT_KEY);
                if (!(context instanceof AiToolContext aiToolContext)) {
                    throw new IllegalStateException("AI tool context is missing.");
                }
                return tool.execute(input, aiToolContext);
            })
            .description("Read-only Eventoday operation tool: " + tool.name())
            .inputType(tool.inputType())
            .build();
    }

    public Map<String, Object> toolContext(AiToolContext aiToolContext) {
        if (aiToolContext == null) {
            return Map.of();
        }
        return Map.of(AI_TOOL_CONTEXT_KEY, aiToolContext);
    }
}
