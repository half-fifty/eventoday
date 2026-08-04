package com.min.edu.file.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 저장소 추상화 인터페이스.
 * S3, MinIO 등 구현체를 교체할 수 있도록 분리한다.
 */
public interface FileStorageService {

    /**
     * 파일을 저장소에 업로드하고 고유 저장 경로(storageKey)를 반환한다.
     *
     * @param file 업로드할 파일
     * @return 저장소 내 고유 경로 (storageKey)
     */
    String upload(MultipartFile file);

    /**
     * storageKey에 해당하는 파일의 다운로드 URL을 반환한다.
     *
     * @param storageKey 저장소 내 고유 경로
     * @return 다운로드 가능한 URL
     */
    String getDownloadUrl(String storageKey);

    /**
     * storageKey에 해당하는 파일의 Presigned URL을 생성한다.
     * PRIVATE 파일을 일시적으로 다운로드할 수 있는 임시 URL이다.
     *
     * @param storageKey 저장소 내 고유 경로
     * @return 만료 시간이 있는 Presigned URL
     */
    String generatePresignedUrl(String storageKey);

    /**
     * storageKey에 해당하는 파일을 저장소에서 삭제한다.
     *
     * @param storageKey 저장소 내 고유 경로
     */
    void delete(String storageKey);
}