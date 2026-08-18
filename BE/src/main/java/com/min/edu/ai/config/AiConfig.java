package com.min.edu.ai.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean(name = "copilotChatModel")
    public ChatModel copilotChatModel(AiProperties properties) {
        Client client = Client.builder()
            .apiKey(properties.apiKey())
            .httpOptions(HttpOptions.builder()
                .timeout((int) properties.readTimeout().toMillis())
                .build())
            .build();

        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
            .model(properties.model())
            .maxOutputTokens(properties.maxOutputTokens())
            .build();

        return GoogleGenAiChatModel.builder()
            .genAiClient(client)
            .options(options)
            .build();
    }
}
