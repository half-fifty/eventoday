package com.min.edu.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void businessException_returnsMappedErrorCodeAndStatus() throws Exception {
        // Arrange
        // ThrowingController throws BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND)

        // Act & Assert
        mockMvc.perform(get("/test/business-exception"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.ENTITY_NOT_FOUND.getCode()))
            .andExpect(jsonPath("$.message").value(GlobalErrorCode.ENTITY_NOT_FOUND.getMessage()));
    }

    @Test
    void unhandledException_returnsInternalServerError() throws Exception {
        // Arrange
        // ThrowingController throws a plain IllegalStateException

        // Act & Assert
        mockMvc.perform(get("/test/unknown-exception"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.INTERNAL_SERVER_ERROR.getCode()));
    }

    @RestController
    private static class ThrowingController {

        @GetMapping("/test/business-exception")
        public void throwBusinessException() {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }

        @GetMapping("/test/unknown-exception")
        public void throwUnknownException() {
            throw new IllegalStateException("boom");
        }
    }
}
