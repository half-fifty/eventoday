package com.min.edu.booth.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Getter만으로는 Jackson이 private 필드를 역직렬화하지 못해 항상 빈 객체가 되므로 Setter가 필요하다.
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiChatResponse {

    private List<Choice> choices;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {

        private Message message;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {

        private String content;
    }
}
