package com.min.edu.ai.prompt;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class PromptProvider {

    public String get(PromptType type) {
        if (type == null) {
            throw new BusinessException(GlobalErrorCode.AI_RESPONSE_INVALID);
        }
        ClassPathResource resource = new ClassPathResource(type.resourcePath());
        if (!resource.exists()) {
            throw new BusinessException(GlobalErrorCode.AI_RESPONSE_INVALID);
        }
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BusinessException(GlobalErrorCode.AI_RESPONSE_INVALID, exception);
        }
    }
}
