package com.min.edu.ai.client;

import com.min.edu.ai.dto.AiChatRequest;
import com.min.edu.ai.dto.AiChatResult;

public interface AiModelGateway {

    AiChatResult chat(AiChatRequest request);
}
