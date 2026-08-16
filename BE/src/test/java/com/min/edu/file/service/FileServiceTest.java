package com.min.edu.file.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.min.edu.file.domain.FileAccessLevel;
import com.min.edu.file.domain.FileAsset;
import com.min.edu.file.repository.FileAssetRepository;
import com.min.edu.file.storage.FileStorageService;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock private FileAssetRepository fileAssetRepository;
    @Mock private FileStorageService fileStorageService;

    private FileService service() {
        return new FileService(fileAssetRepository, fileStorageService);
    }

    private FileAsset fileAsset() {
        return FileAsset.builder()
            .id(1L)
            .uploadedBy(1L)
            .storageKey("certificates/1234.pdf")
            .originalName("사업자등록증.pdf")
            .mimeType("application/pdf")
            .fileSize(1024L)
            .accessLevel(FileAccessLevel.PRIVATE)
            .createdAt(OffsetDateTime.now())
            .build();
    }

    @Test
    void deleteFile_existingFile_deletesStorageObjectAndMetadata() {
        FileAsset fileAsset = fileAsset();
        given(fileAssetRepository.findById(1L)).willReturn(Optional.of(fileAsset));

        service().deleteFile(1L);

        verify(fileStorageService).delete("certificates/1234.pdf");
        verify(fileAssetRepository).delete(fileAsset);
    }

    @Test
    void deleteFile_missingFile_doesNothing() {
        given(fileAssetRepository.findById(1L)).willReturn(Optional.empty());

        service().deleteFile(1L);

        verify(fileStorageService, never()).delete(org.mockito.ArgumentMatchers.anyString());
        verify(fileAssetRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }
}
