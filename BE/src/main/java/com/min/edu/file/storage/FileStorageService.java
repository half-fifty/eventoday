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
}