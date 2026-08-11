package com.min.edu.event.service;

/**
 * 공지·자료 첨부파일 교체 이벤트 (CONTENT-API-004)
 * - 교체 전 파일(oldFileId)을 트랜잭션 커밋 이후에 정리하기 위해 발행한다
 * - 커밋 전에 삭제하면 이후 롤백 시 S3 객체가 복구되지 않아
 *   되살아난 콘텐츠가 다운로드 불가능한 파일을 참조하게 된다
 */
public record EventContentFileReplaced(Long contentId, Long oldFileId) {}
