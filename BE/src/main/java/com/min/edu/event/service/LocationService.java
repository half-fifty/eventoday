package com.min.edu.event.service;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.dto.LocationDtos;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class LocationService {
    private final RestClient kakaoClient;

    public LocationService(@Value("${kakao.map.rest-api-key}") String restApiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.kakaoClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader("Authorization", "KakaoAK " + restApiKey)
                .build();
    }

    public List<LocationDtos.Place> search(String query) {
        try {
            LocationDtos.KakaoResponse response = kakaoClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v2/local/search/keyword.json")
                        .queryParam("query", query).queryParam("size", 10).build())
                .retrieve()
                .body(LocationDtos.KakaoResponse.class);
            if (response == null || response.documents() == null) return List.of();
            return response.documents().stream().map(LocationDtos.KakaoDocument::toPlace).toList();
        } catch (RestClientException exception) {
            throw new BusinessException(GlobalErrorCode.KAKAO_MAP_API_UNAVAILABLE, exception);
        }
    }

    public LocationDtos.PostalCode findPostalCode(String address) {
        try {
            LocationDtos.PostalCodeResponse response = kakaoClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v2/local/search/address.json")
                        .queryParam("query", address).queryParam("size", 1).build())
                .retrieve()
                .body(LocationDtos.PostalCodeResponse.class);
            if (response == null || response.documents() == null || response.documents().isEmpty()
                    || response.documents().getFirst().roadAddress() == null) {
                return new LocationDtos.PostalCode("");
            }
            return new LocationDtos.PostalCode(response.documents().getFirst().roadAddress().zoneNo());
        } catch (RestClientException exception) {
            throw new BusinessException(GlobalErrorCode.KAKAO_MAP_API_UNAVAILABLE, exception);
        }
    }
}
