package com.min.edu.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.min.edu.ai.tool.AiTool;
import com.min.edu.ai.tool.AiToolContext;
import com.min.edu.member.domain.PlatformRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionException;

class AiToolCallbackFactoryTest {

    private final AiToolCallbackFactory factory = new AiToolCallbackFactory();

    @Test
    void createsCallbackWithToolNameAndInputSchemaWithoutServerContext() {
        List<ToolCallback> callbacks = factory.create(List.of(new StubTool()));

        assertThat(callbacks).hasSize(1);
        ToolCallback callback = callbacks.get(0);
        assertThat(callback.getToolDefinition().name()).isEqualTo("getTicketOrderStatus");
        assertThat(callback.getToolDefinition().inputSchema()).contains("orderNo");
        assertThat(callback.getToolDefinition().inputSchema())
            .doesNotContain("memberId", "eventId", "platformRole", "guestOrderAccessToken");
    }

    @Test
    void passesServerAiToolContextToEventodayTool() {
        StubTool tool = new StubTool();
        ToolCallback callback = factory.create(List.of(tool)).get(0);
        AiToolContext aiToolContext =
            new AiToolContext(10L, PlatformRole.USER, null, "req-1", 100L);

        String result = callback.call(
            "{\"orderNo\":\"ORDER-1\"}",
            new ToolContext(factory.toolContext(aiToolContext))
        );

        assertThat(tool.receivedInput.orderNo()).isEqualTo("ORDER-1");
        assertThat(tool.receivedContext).isSameAs(aiToolContext);
        assertThat(result).contains("ORDER-1", "100");
    }

    @Test
    void rejectsCallbackInvocationWithoutServerContext() {
        ToolCallback callback = factory.create(List.of(new StubTool())).get(0);

        assertThatThrownBy(() -> callback.call(
            "{\"orderNo\":\"ORDER-1\"}",
            new ToolContext(java.util.Map.of())
        )).isInstanceOf(ToolExecutionException.class)
            .hasMessageContaining("AI tool context");
    }

    private static class StubTool implements AiTool<StubInput, StubOutput> {
        private StubInput receivedInput;
        private AiToolContext receivedContext;

        @Override
        public String name() {
            return "getTicketOrderStatus";
        }

        @Override
        public Class<StubInput> inputType() {
            return StubInput.class;
        }

        @Override
        public StubOutput execute(StubInput input, AiToolContext context) {
            receivedInput = input;
            receivedContext = context;
            return new StubOutput(input.orderNo(), context.eventId());
        }
    }

    private record StubInput(String orderNo) {
    }

    private record StubOutput(String orderNo, Long eventId) {
    }
}
