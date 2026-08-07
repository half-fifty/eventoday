package com.min.edu.event.domain;

import java.util.Arrays;

public enum RegionCode {
    SEOUL("서울"), BUSAN("부산"), DAEGU("대구"), INCHEON("인천"), GWANGJU("광주"),
    DAEJEON("대전"), ULSAN("울산"), SEJONG("세종"), GYEONGGI("경기"), GANGWON("강원"),
    CHUNGBUK("충북", "충청북도"), CHUNGNAM("충남", "충청남도"), JEONBUK("전북", "전라북도", "전북특별자치도"),
    JEONNAM("전남", "전라남도"), GYEONGBUK("경북", "경상북도"), GYEONGNAM("경남", "경상남도"),
    JEJU("제주", "제주특별자치도");

    private final String[] prefixes;

    RegionCode(String... prefixes) { this.prefixes = prefixes; }

    public static RegionCode fromAddress(String address) {
        if (address == null || address.isBlank()) return null;
        String first = address.trim().split("\\s+", 2)[0];
        return Arrays.stream(values())
                .filter(region -> Arrays.stream(region.prefixes).anyMatch(first::startsWith))
                .findFirst().orElse(null);
    }
}
