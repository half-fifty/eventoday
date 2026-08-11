package com.min.edu.admission.controller;

import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.dto.AdmissionTicketDtos;
import com.min.edu.admission.service.AdmissionTicketQueryService;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AdmissionTicketController {

    private final AdmissionTicketQueryService admissionTicketQueryService;

    public AdmissionTicketController(AdmissionTicketQueryService admissionTicketQueryService) {
        this.admissionTicketQueryService = admissionTicketQueryService;
    }

    @GetMapping("/members/me/admission-tickets")
    public ApiResponse<Page<AdmissionTicketDtos.MyListResponse>> getMyAdmissionTickets(
            @RequestParam(required = false) AdmissionTicketStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(
            admissionTicketQueryService.getMyAdmissionTickets(status, actor, page, size)
        );
    }

    @GetMapping("/members/me/admission-tickets/{admissionTicketId}")
    public ApiResponse<AdmissionTicketDtos.DetailResponse> getMyAdmissionTicketDetail(
            @PathVariable Long admissionTicketId,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(
            admissionTicketQueryService.getMyAdmissionTicketDetail(admissionTicketId, actor)
        );
    }

    @GetMapping(
        value = "/members/me/admission-tickets/{admissionTicketId}/qr",
        produces = MediaType.IMAGE_PNG_VALUE
    )
    public ResponseEntity<byte[]> getMyAdmissionTicketQr(
            @PathVariable Long admissionTicketId,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(admissionTicketQueryService.getMyAdmissionTicketQr(admissionTicketId, actor));
    }

    @GetMapping("/ticket-orders/{orderNo}/admission-tickets")
    public ApiResponse<List<AdmissionTicketDtos.MyListResponse>> getGuestOrderAdmissionTickets(
            @PathVariable String orderNo,
            @RequestHeader(value = "X-Order-Access-Token", required = false)
            String orderAccessToken) {
        return ApiResponse.success(
            admissionTicketQueryService.getGuestOrderAdmissionTickets(orderNo, orderAccessToken)
        );
    }

    @GetMapping("/ticket-orders/{orderNo}/admission-tickets/{admissionTicketId}")
    public ApiResponse<AdmissionTicketDtos.DetailResponse> getGuestOrderAdmissionTicketDetail(
            @PathVariable String orderNo,
            @PathVariable Long admissionTicketId,
            @RequestHeader(value = "X-Order-Access-Token", required = false)
            String orderAccessToken) {
        return ApiResponse.success(
            admissionTicketQueryService.getGuestOrderAdmissionTicketDetail(
                orderNo,
                orderAccessToken,
                admissionTicketId
            )
        );
    }

    @GetMapping(
        value = "/ticket-orders/{orderNo}/admission-tickets/{admissionTicketId}/qr",
        produces = MediaType.IMAGE_PNG_VALUE
    )
    public ResponseEntity<byte[]> getGuestOrderAdmissionTicketQr(
            @PathVariable String orderNo,
            @PathVariable Long admissionTicketId,
            @RequestHeader(value = "X-Order-Access-Token", required = false)
            String orderAccessToken) {
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(admissionTicketQueryService.getGuestOrderAdmissionTicketQr(
                orderNo,
                orderAccessToken,
                admissionTicketId
            ));
    }

    @GetMapping("/events/{eventId}/admission-tickets")
    public ApiResponse<Page<AdmissionTicketDtos.EventListResponse>> getEventAdmissionTickets(
            @PathVariable Long eventId,
            @RequestParam(required = false) AdmissionTicketStatus status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(
            admissionTicketQueryService.getEventAdmissionTickets(eventId, status, actor, page, size)
        );
    }
}
