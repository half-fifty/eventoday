package com.min.edu.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.min.edu.common.exception.GlobalErrorCode;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void success_wrapsDataWithSuccessCode() {
        // Arrange
        String data = "payload";

        // Act
        ApiResponse<String> response = ApiResponse.success(data);

        // Assert
        assertThat(response.getCode()).isEqualTo("200");
        assertThat(response.getData()).isEqualTo(data);
    }

    @Test
    void error_wrapsErrorCodeAndMessage() {
        // Arrange
        GlobalErrorCode errorCode = GlobalErrorCode.ENTITY_NOT_FOUND;

        // Act
        ApiResponse<Void> response = ApiResponse.error(errorCode);

        // Assert
        assertThat(response.getCode()).isEqualTo(errorCode.getCode());
        assertThat(response.getMessage()).isEqualTo(errorCode.getMessage());
        assertThat(response.getData()).isNull();
    }
}
