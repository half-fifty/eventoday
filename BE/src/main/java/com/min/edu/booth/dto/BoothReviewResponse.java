package com.min.edu.booth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

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

    private boolean hidden;  // 신고 누적/운영자 조치로 숨김 처리됐는지 (본인 리뷰 목록에서만 의미 있음)

    private String hiddenReason;  // 숨김 사유: REPORTED(신고 누적)/MANAGER_HIDDEN(운영자 조치), 숨김 아니면 null

    private boolean reportedByMe;  // 조회하는 회원이 이미 이 리뷰를 신고했는지 (비로그인/본인 리뷰는 항상 false)

    private BoothReviewReplyResponse reply;  // 부스 담당자가 남긴 답글 (없으면 null)

    private List<BoothReviewPhotoResponse> photos;  // 첨부 사진 (없으면 빈 리스트)
}