package com.min.edu.file.storage;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

/**
 * AWS S3를 사용하는 파일 저장소 구현체.
 * storageKey 형식: files/{uuid}/{원본파일명}
 */
@Slf4j
@Component
public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;
    private final String bucketName;

    public S3FileStorageService(
            S3Client s3Client,
            @Value("${cloud.aws.s3.bucket}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    @Override
    public String upload(MultipartFile file) {
        // UUID 기반 고유 경로 생성으로 중복 방지
        String storageKey = "files/" + UUID.randomUUID() + "/" + file.getOriginalFilename();

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(file.getBytes()));

        } catch (IOException e) {
            log.error("S3 파일 업로드 실패. storageKey={}", storageKey, e);
            throw new BusinessException(GlobalErrorCode.FILE_UPLOAD_FAILED);
        }

        return storageKey;
    }

    @Override
    public String getDownloadUrl(String storageKey) {
        return s3Client.utilities()
                .getUrl(GetUrlRequest.builder()
                        .bucket(bucketName)
                        .key(storageKey)
                        .build())
                .toString();
    }
}