package com.min.edu.organization.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.organization.dto.BusinessVerificationRequestDto;
import com.min.edu.organization.dto.BusinessVerificationResponseDto;
import com.min.edu.organization.dto.NtsBusinessValidationRequestDto;
import com.min.edu.organization.dto.NtsBusinessValidationResponseDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BusinessVerificationService {

    private static final String VALID_CODE = "01";
    private static final String ACTIVE_BUSINESS_CODE = "01";

    private final NtsBusinessApiClient ntsBusinessApiClient;

    public BusinessVerificationResponseDto verify(
            BusinessVerificationRequestDto request) {
        NtsBusinessValidationRequestDto.Business business =
            new NtsBusinessValidationRequestDto.Business(
                request.getBusinessNumber(),
                request.getStartDate(),
                request.getRepresentativeName().trim()
            );

        NtsBusinessValidationRequestDto ntsRequest =
            new NtsBusinessValidationRequestDto(List.of(business));

        NtsBusinessValidationResponseDto ntsResponse =
            ntsBusinessApiClient.validate(ntsRequest);

        if (ntsResponse == null
                || ntsResponse.getData() == null
                || ntsResponse.getData().isEmpty()) {
            throw new BusinessException(
                GlobalErrorCode.NTS_BUSINESS_API_UNAVAILABLE
            );
        }

        NtsBusinessValidationResponseDto.Result result =
            ntsResponse.getData().get(0);

        boolean valid = VALID_CODE.equals(result.getValid());
        boolean active = valid
            && result.getStatus() != null
            && ACTIVE_BUSINESS_CODE.equals(
                result.getStatus().getBusinessStatusCode()
            );

        return new BusinessVerificationResponseDto(valid, active);
    }
}
