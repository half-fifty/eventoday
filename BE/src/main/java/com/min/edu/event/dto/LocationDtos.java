package com.min.edu.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class LocationDtos {
    private LocationDtos() {}

    public record Place(
            String placeId, String name, String category,
            String address, String roadAddress,
            double latitude, double longitude) {}

    public record KakaoResponse(List<KakaoDocument> documents) {}

    public record PostalCodeResponse(List<AddressDocument> documents) {}

    public record AddressDocument(@JsonProperty("road_address") RoadAddress roadAddress) {}

    public record RoadAddress(@JsonProperty("zone_no") String zoneNo) {}

    public record PostalCode(String postalCode) {}

    public record KakaoDocument(
            String id,
            @JsonProperty("place_name") String placeName,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("road_address_name") String roadAddressName,
            String x,
            String y) {
        public Place toPlace() {
            return new Place(id, placeName, categoryName, addressName, roadAddressName,
                    Double.parseDouble(y), Double.parseDouble(x));
        }
    }
}
