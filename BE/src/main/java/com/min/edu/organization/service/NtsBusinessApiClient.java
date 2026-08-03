package com.min.edu.organization.service;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.organization.dto.NtsBusinessValidationRequestDto;
import com.min.edu.organization.dto.NtsBusinessValidationResponseDto;

@Component
public class NtsBusinessApiClient {

    private final RestClient restClient;
    private final String serviceKey;

    public NtsBusinessApiClient(
            @Value("${external.nts-business-api.base-url}") String baseUrl,
            @Value("${external.nts-business-api.connect-timeout:3s}")
            Duration connectTimeout,
            @Value("${external.nts-business-api.read-timeout:5s}")
            Duration readTimeout,
            @Value("${external.nts-business-api.service-key:}") String serviceKey) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .build();
        JdkClientHttpRequestFactory requestFactory =
            new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build();
        this.serviceKey = serviceKey;
    }

    public NtsBusinessValidationResponseDto validate(
            NtsBusinessValidationRequestDto request) {
        if (serviceKey.isBlank()) {
            throw new BusinessException(
                GlobalErrorCode.NTS_BUSINESS_API_UNAVAILABLE
            );
        }

        try {
            return restClient.post()
                .uri(uriBuilder -> uriBuilder
                    .path("/validate")
                    .queryParam("serviceKey", "{serviceKey}")
                    .build(serviceKey))
                .body(request)
                .retrieve()
                .body(NtsBusinessValidationResponseDto.class);
        } catch (RestClientException exception) {
            throw new BusinessException(
                GlobalErrorCode.NTS_BUSINESS_API_UNAVAILABLE
            );
        }
    }
}
