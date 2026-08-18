package com.min.edu.ai.tool;

public interface AiTool<I, O> {

    String name();

    O execute(I input, AiToolContext context);
}
