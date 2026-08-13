package com.min.edu.organization.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.organization.domain.OrganizationStatus;
import com.min.edu.organization.domain.OrganizationType;
import com.min.edu.organization.dto.BusinessSignupRequestDto;
import com.min.edu.organization.dto.BusinessSignupResponseDto;
import com.min.edu.organization.dto.BusinessVerificationResponseDto;

@ExtendWith(MockitoExtension.class)
class BusinessSignupServiceTest {

    @Mock private BusinessVerificationService businessVerificationService;
    @Mock private BusinessSignupPersistenceService persistenceService;
    @Mock private PasswordEncoder passwordEncoder;

    private BusinessSignupService service() {
        return new BusinessSignupService(
            businessVerificationService, persistenceService, passwordEncoder
        );
    }

    private BusinessSignupRequestDto exhibitorRequest() {
        BusinessSignupRequestDto request = new BusinessSignupRequestDto();
        setField(request, "organizationType", OrganizationType.EXHIBITOR);
        setField(request, "email", "  Owner@Example.com ");
        setField(request, "password", "password1234");
        setField(request, "managerName", "담당자");
        setField(request, "managerPhone", "01012345678");
        setField(request, "businessNumber", "1234567890");
        setField(request, "startDate", "20200101");
        setField(request, "representativeName", "홍길동");
        setField(request, "organizationName", "부스참가사");
        setField(request, "contactEmail", "contact@example.com");
        setField(request, "contactPhone", "0212345678");
        return request;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Test
    void signup_verifiedBusiness_normalizesEmailAndDelegatesToPersistence() {
        given(businessVerificationService.verify(anyString(), anyString(), anyString()))
            .willReturn(new BusinessVerificationResponseDto(true, true));
        given(passwordEncoder.encode("password1234")).willReturn("encoded-hash");
        given(persistenceService.save(any(), eq("owner@example.com"), eq("encoded-hash"), eq(null)))
            .willReturn(new BusinessSignupResponseDto(1L, 2L, OrganizationStatus.ACTIVE));

        BusinessSignupResponseDto response = service().signup(exhibitorRequest(), null);

        verify(persistenceService).save(any(), eq("owner@example.com"), eq("encoded-hash"), eq(null));
        org.assertj.core.api.Assertions.assertThat(response.getOrganizationStatus())
            .isEqualTo(OrganizationStatus.ACTIVE);
    }

    @Test
    void signup_ntsVerificationInvalid_throwsAndNeverPersists() {
        given(businessVerificationService.verify(anyString(), anyString(), anyString()))
            .willReturn(new BusinessVerificationResponseDto(false, false));

        assertThatThrownBy(() -> service().signup(exhibitorRequest(), null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", GlobalErrorCode.BUSINESS_VERIFICATION_FAILED);

        verify(persistenceService, never()).save(any(), anyString(), anyString(), any());
    }

    @Test
    void signup_ntsBusinessNotActive_throwsAndNeverPersists() {
        given(businessVerificationService.verify(anyString(), anyString(), anyString()))
            .willReturn(new BusinessVerificationResponseDto(true, false));

        assertThatThrownBy(() -> service().signup(exhibitorRequest(), null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", GlobalErrorCode.BUSINESS_NOT_ACTIVE);

        verify(persistenceService, never()).save(any(), anyString(), anyString(), any());
    }
}
