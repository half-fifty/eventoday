package com.min.edu.event.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.domain.RegionCode;
import com.min.edu.event.dto.EventDtos;
import com.min.edu.event.service.EventService;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class EventController {
    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/me/managed-organizations")
    public ApiResponse<List<EventDtos.ManagedOrganization>> findManagedOrganizations(
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(eventService.findManagedOrganizations(actor));
    }

    @GetMapping("/events")
    public ApiResponse<Page<EventDtos.Summary>> findPublicEvents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) RegionCode regionCode,
            @RequestParam(required = false) List<String> exhibitCategoryCodes,
            @RequestParam(required = false) String venueName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTo,
            Pageable pageable) {
        return ApiResponse.success(eventService.findPublicEvents(keyword, eventType, regionCode,
                exhibitCategoryCodes, venueName, startFrom, startTo, pageable));
    }

    @GetMapping("/exhibit-categories")
    public ApiResponse<List<EventDtos.ExhibitCategoryResponse>> findExhibitCategories() {
        return ApiResponse.success(eventService.findExhibitCategories());
    }

    @GetMapping("/events/{eventId}")
    public ApiResponse<EventDtos.PublicDetail> getPublicEvent(@PathVariable Long eventId) {
        return ApiResponse.success(eventService.getPublicEvent(eventId));
    }

    @GetMapping("/organizations/{organizationId}/events")
    public ApiResponse<Page<EventDtos.Summary>> findOrganizationEvents(@PathVariable Long organizationId,
            @AuthenticationPrincipal AuthenticatedMemberDto actor, Pageable pageable) {
        return ApiResponse.success(eventService.findOrganizationEvents(organizationId, actor, pageable));
    }

    @PostMapping("/organizations/{organizationId}/events")
    public ApiResponse<EventDtos.Detail> create(@PathVariable Long organizationId,
            @Valid @RequestBody EventDtos.SaveRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(eventService.create(organizationId, request, actor));
    }

    @GetMapping("/organizations/{organizationId}/events/{eventId}")
    public ApiResponse<EventDtos.Detail> getManagedEvent(@PathVariable Long organizationId,
            @PathVariable Long eventId, @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(eventService.getManagedEvent(organizationId, eventId, actor));
    }

    @PatchMapping("/organizations/{organizationId}/events/{eventId}")
    public ApiResponse<EventDtos.Detail> update(@PathVariable Long organizationId, @PathVariable Long eventId,
            @Valid @RequestBody EventDtos.SaveRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(eventService.update(organizationId, eventId, request, actor));
    }

    @PostMapping("/events/{eventId}/submission")
    public ApiResponse<Void> submit(@PathVariable Long eventId, @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        eventService.submit(eventId, actor); return ApiResponse.success();
    }
    @PostMapping("/events/{eventId}/publication")
    public ApiResponse<Void> publish(@PathVariable Long eventId, @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        eventService.publish(eventId, actor); return ApiResponse.success();
    }
    @PostMapping("/events/{eventId}/cancellation")
    public ApiResponse<Void> cancel(@PathVariable Long eventId, @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        eventService.cancel(eventId, actor); return ApiResponse.success();
    }

    @GetMapping("/admin/events")
    public ApiResponse<Page<EventDtos.Summary>> findAdminEvents(@RequestParam(required = false) EventStatus status,
            @AuthenticationPrincipal AuthenticatedMemberDto actor, Pageable pageable) {
        return ApiResponse.success(eventService.findAdminEvents(status, actor, pageable));
    }
    @GetMapping("/admin/events/{eventId}")
    public ApiResponse<EventDtos.Detail> getAdminEvent(@PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(eventService.getAdminEvent(eventId, actor));
    }
    @PostMapping("/admin/events/{eventId}/approval")
    public ApiResponse<Void> approve(@PathVariable Long eventId, @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        eventService.approve(eventId, actor); return ApiResponse.success();
    }
    @PostMapping("/admin/events/{eventId}/rejection")
    public ApiResponse<Void> reject(@PathVariable Long eventId,
            @Valid @RequestBody EventDtos.RejectionRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        eventService.reject(eventId, request.reason(), actor); return ApiResponse.success();
    }
    @PostMapping("/admin/events/{eventId}/suspension")
    public ApiResponse<Void> suspend(@PathVariable Long eventId, @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        eventService.suspend(eventId, actor); return ApiResponse.success();
    }

    @GetMapping("/events/{eventId}/members")
    public ApiResponse<List<EventDtos.MemberResponse>> getMembers(@PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(eventService.getMembers(eventId, actor));
    }
    @PostMapping("/events/{eventId}/members")
    public ApiResponse<EventDtos.MemberResponse> addMember(@PathVariable Long eventId,
            @Valid @RequestBody EventDtos.MemberRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(eventService.addMember(eventId, request, actor));
    }
    @PatchMapping("/events/{eventId}/members/{memberId}")
    public ApiResponse<EventDtos.MemberResponse> updateMember(@PathVariable Long eventId, @PathVariable Long memberId,
            @Valid @RequestBody EventDtos.MemberUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(eventService.updateMember(eventId, memberId, request, actor));
    }
    @DeleteMapping("/events/{eventId}/members/{memberId}")
    public ApiResponse<Void> removeMember(@PathVariable Long eventId, @PathVariable Long memberId,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        eventService.removeMember(eventId, memberId, actor); return ApiResponse.success();
    }
}

@RestController
class MeAdmissionEventController {
    private final EventService eventService;

    MeAdmissionEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/me/admission-events")
    public ApiResponse<List<EventDtos.AdmissionEventResponse>> findMyAdmissionEvents(
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(eventService.findMyAdmissionEvents(actor));
    }
}
