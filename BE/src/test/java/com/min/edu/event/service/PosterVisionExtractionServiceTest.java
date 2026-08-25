package com.min.edu.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.event.dto.PosterExtractionDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class PosterVisionExtractionServiceTest {

    @Test
    void detectMediaType_usesFileSignatureInsteadOfUnreliableMimeType() {
        assertThat(PosterVisionExtractionService.detectMediaType(new byte[] {
            (byte) 0xff, (byte) 0xd8, (byte) 0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0
        })).isEqualTo("image/jpeg");
        assertThat(PosterVisionExtractionService.detectMediaType(new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0
        })).isEqualTo("image/png");
        assertThat(PosterVisionExtractionService.detectMediaType(new byte[] {
            'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'
        })).isEqualTo("image/webp");
    }

    @Test
    void detectMediaType_rejectsRenamedOrUnsupportedFiles() {
        assertThatThrownBy(() -> PosterVisionExtractionService.detectMediaType(
            new byte[] {'n', 'o', 't', '-', 'a', 'n', '-', 'i', 'm', 'a', 'g', 'e'}
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void sanitize_keepsOnlyUniqueAllowedCategoriesAndLimitsThemToFive() {
        PosterExtractionDto raw = new PosterExtractionDto(
                "행사", null, null, null, null, null, null, null, null,
                null, null,
                List.of("AGRI_FOOD", "INVALID", "AGRI_FOOD", "EDUCATION",
                        "CULTURE_ART", "PUBLIC_DEFENSE", "BEAUTY_COSMETICS", "WEDDING"),
                null);

        PosterExtractionDto sanitized = PosterVisionExtractionService.sanitize(raw);

        assertThat(sanitized.categoryCodes()).containsExactly(
                "AGRI_FOOD", "EDUCATION", "CULTURE_ART", "PUBLIC_DEFENSE", "BEAUTY_COSMETICS");
        assertThat(sanitized.sponsors()).isEmpty();
        assertThat(sanitized.warnings()).isEmpty();
    }
}
