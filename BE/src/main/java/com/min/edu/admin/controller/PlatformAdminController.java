package com.min.edu.admin.controller;

import com.min.edu.admin.dto.PlatformAdminDtos;
import com.min.edu.admin.service.PlatformAdminService;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/platform")
public class PlatformAdminController {
    private final PlatformAdminService service;
    public PlatformAdminController(PlatformAdminService service) { this.service = service; }

    @GetMapping("/dashboard")
    public ApiResponse<PlatformAdminDtos.Dashboard> dashboard(@AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(service.dashboard(actor));
    }
    @GetMapping("/accounts")
    public ApiResponse<List<PlatformAdminDtos.Account>> accounts(@AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(service.accounts(actor));
    }
    @PatchMapping("/accounts/{memberId}/status")
    public ApiResponse<PlatformAdminDtos.Account> changeStatus(@PathVariable Long memberId,
            @RequestBody PlatformAdminDtos.AccountStatusRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        if (request == null) throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        return ApiResponse.success(service.changeAccountStatus(memberId, request.status(), actor));
    }
    @GetMapping("/statistics")
    public ApiResponse<PlatformAdminDtos.Statistics> statistics(@AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(service.statistics(actor));
    }
    @GetMapping("/audit")
    public ApiResponse<List<PlatformAdminDtos.AuditEntry>> audit(@AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(service.audit(actor));
    }
}
