package com.min.edu.ai.dto;

import com.min.edu.ai.tool.AiTool;
import com.min.edu.ai.tool.AiToolContext;
import java.util.List;

public record AiChatRequest(
        String systemPrompt,
        String userPrompt,
        List<AiTool<?, ?>> tools,
        AiToolContext toolContext) {

    public AiChatRequest(String systemPrompt, String userPrompt) {
        this(systemPrompt, userPrompt, List.of(), null);
    }
}
