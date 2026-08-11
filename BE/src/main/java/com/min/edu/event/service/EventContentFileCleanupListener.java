package com.min.edu.event.service;

import com.min.edu.file.domain.FileAsset;
import com.min.edu.file.repository.FileAssetRepository;
import com.min.edu.file.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 공지·자료 첨부파일 교체 후 기존 파일 정리 리스너
 * - AFTER_COMMIT: 콘텐츠 수정이 실제로 커밋된 뒤에만 기존 파일을 삭제한다
 * - S3 삭제가 실패하면 FileAsset 레코드를 남겨 둔다 (추후 재시도·수동 정리 가능)
 * - EventReviewNotificationListener와 동일한 AFTER_COMMIT 패턴
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventContentFileCleanupListener {

    private final FileAssetRepository fileAssetRepository;
    private final FileStorageService fileStorageService;

    // 커밋 이후 실행되므로 별도 트랜잭션에서 삭제를 처리한다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanupReplacedFile(EventContentFileReplaced event) {
        FileAsset old = fileAssetRepository.findById(event.oldFileId()).orElse(null);
        if (old == null) {
            return;
        }

        try {
            fileStorageService.delete(old.getStorageKey());
        } catch (Exception exception) {
            // S3 삭제 실패 시 DB 레코드를 지우지 않는다 (지우면 고아 객체를 추적할 수 없음)
            log.warn("교체된 기존 파일 S3 삭제 실패 - contentId={}, fileId={}",
                    event.contentId(), event.oldFileId(), exception);
            return;
        }

        fileAssetRepository.delete(old);
    }
}
