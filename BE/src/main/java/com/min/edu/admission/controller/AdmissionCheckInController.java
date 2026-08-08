package com.min.edu.admission.controller;

import com.min.edu.admission.domain.AdmissionAction;
import com.min.edu.admission.domain.AdmissionResult;
import com.min.edu.admission.dto.AdmissionCheckInDtos;
import com.min.edu.admission.service.AdmissionCheckInService;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdmissionCheckInController {

    private final AdmissionCheckInService admissionCheckInService;

    public AdmissionCheckInController(AdmissionCheckInService admissionCheckInService) {
        this.admissionCheckInService = admissionCheckInService;
    }

    @PostMapping("/events/{eventId}/admission-checkins")
    public ApiResponse<AdmissionCheckInDtos.CheckInResponse> checkIn(
            @PathVariable Long eventId,
            @RequestBody AdmissionCheckInDtos.CheckInRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(admissionCheckInService.checkIn(eventId, request, actor));
    }

    @PostMapping("/events/{eventId}/admission-tickets/{admissionTicketId}/check-in-cancellation")
    public ApiResponse<AdmissionCheckInDtos.CheckInCancellationResponse> cancelCheckIn(
            @PathVariable Long eventId,
            @PathVariable Long admissionTicketId,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(
            admissionCheckInService.cancelCheckIn(eventId, admissionTicketId, actor)
        );
    }

    @GetMapping("/events/{eventId}/admission-logs")
    public ApiResponse<Page<AdmissionCheckInDtos.LogListResponse>> getEventAdmissionLogs(
            @PathVariable Long eventId,
            @RequestParam(required = false) AdmissionAction action,
            @RequestParam(required = false) AdmissionResult result,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(
            admissionCheckInService.getEventAdmissionLogs(eventId, action, result, actor, page, size)
        );
    }
}
