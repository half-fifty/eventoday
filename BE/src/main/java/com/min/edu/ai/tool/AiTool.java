package com.min.edu.ai.tool;

public interface AiTool<I, O> {

    String name();

    Class<I> inputType();

    O execute(I input, AiToolContext context);
}
