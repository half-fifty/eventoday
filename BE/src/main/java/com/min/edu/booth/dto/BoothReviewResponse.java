package com.min.edu.booth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewResponse {

    private Long id;

    private Long boothId;

    private Long eventId;           // 부스가 속한 행사 (내 후기 목록에서 행사/부스를 구분하기 위함)

    private String eventName;

    private String boothDisplayName;

    private String boothCode;

    private Long memberId;          // 로그인한 회원 본인 후기인지 프론트에서 판별하는 용도

    private String memberName;  // 사용자 닉네임/이름

    private Short rating;

    private String comment;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;  // ✅ 추가!
}