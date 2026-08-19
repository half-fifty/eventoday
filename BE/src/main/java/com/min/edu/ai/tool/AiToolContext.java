package com.min.edu.ai.tool;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.member.domain.PlatformRole;

public record AiToolContext(
        Long memberId,
        PlatformRole platformRole,
        String guestOrderAccessToken,
        String requestId,
        Long eventId) {

    public AiToolContext(
            Long memberId,
            PlatformRole platformRole,
            String guestOrderAccessToken,
            String requestId) {
        this(memberId, platformRole, guestOrderAccessToken, requestId, null);
    }

    public static AiToolContext of(
            AuthenticatedMemberDto actor,
            String guestOrderAccessToken,
            String requestId) {
        return new AiToolContext(
            actor == null ? null : actor.getMemberId(),
            actor == null ? null : actor.getPlatformRole(),
            guestOrderAccessToken,
            requestId,
            null
        );
    }

    public static AiToolContext forEventOperation(
            AuthenticatedMemberDto actor,
            String requestId,
            Long eventId) {
        return new AiToolContext(
            actor == null ? null : actor.getMemberId(),
            actor == null ? null : actor.getPlatformRole(),
            null,
            requestId,
            eventId
        );
    }
}
