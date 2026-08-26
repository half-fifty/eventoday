package com.min.edu.funnel.service;

import org.springframework.stereotype.Service;

import com.min.edu.funnel.dto.FunnelActionEventDto;
import com.min.edu.funnel.dto.FunnelActionRequest;
import com.min.edu.funnel.producer.FunnelActionProducer;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FunnelActionCollectorService {

    private final FunnelActionProducer funnelActionProducer;

    public void collect(FunnelActionRequest request, String anonymousId, Long userId) {
        FunnelActionEventDto eventDto = FunnelActionEventDto.create(
                request.sessionId(),
                request.eventId(),
                anonymousId,
                userId,
                request.actionType().name(),
                request.occurredAt(),
                request.properties());
        funnelActionProducer.send(eventDto);
    }
}
