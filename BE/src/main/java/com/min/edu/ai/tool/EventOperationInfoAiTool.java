package com.min.edu.ai.tool;

import com.min.edu.ai.dto.EventOperationAiContext;
import com.min.edu.event.domain.Event;
import com.min.edu.event.service.EventOperationAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventOperationInfoAiTool
        implements AiTool<EventOperationInfoAiTool.Input, EventOperationAiContext> {

    private static final String TOOL_NAME = "getEventOperationInfo";

    private final EventOperationAccessService eventOperationAccessService;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public EventOperationAiContext execute(Input input, AiToolContext context) {
        Event event = eventOperationAccessService.requireOperationalAccess(
            context.eventId(),
            context.memberId()
        );
        return EventOperationAiContext.from(event);
    }

    public record Input() {
    }
}
