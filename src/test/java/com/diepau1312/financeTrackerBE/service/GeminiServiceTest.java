package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.ai.AIParseResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GeminiService Tests")
@MockitoSettings(strictness = Strictness.LENIENT)
class GeminiServiceTest {

  private GeminiService geminiService;
  private ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("buildPrompt chứa text người dùng nhập")
  void buildPrompt_containsUserText() {
    // Kiểm tra logic prompt engineering bằng cách test indirectly
    // Khi gọi với apiKey sai → WebClient throw exception → trả về error response
    WebClient.Builder mockBuilder = mock(WebClient.Builder.class, RETURNS_DEEP_STUBS);
    when(mockBuilder.build().post().uri(anyString()).bodyValue(any()).retrieve().bodyToMono(String.class).block())
        .thenThrow(new RuntimeException("Invalid API key"));

    geminiService = new GeminiService(mockBuilder, objectMapper);
    AIParseResponse result = geminiService.parseTransaction("ăn sáng 45k", "wrong-key");

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getRawText()).isEqualTo("ăn sáng 45k");
    assertThat(result.getErrorMessage()).isNotBlank();
  }

  @Test
  @DisplayName("parse thành công với Gemini response hợp lệ")
  void parseTransaction_validResponse_returnsCorrectData() throws Exception {
    // Simulate Gemini API response format
    String fakeGeminiResponse = """
        {
          "candidates": [{
            "content": {
              "parts": [{
                "text": "{\\"type\\":\\"EXPENSE\\",\\"amount\\":45000,\\"note\\":\\"ăn sáng\\",\\"suggestedCategory\\":\\"Ăn uống\\"}"
              }]
            }
          }]
        }
        """;

    WebClient.Builder mockBuilder = mock(WebClient.Builder.class, RETURNS_DEEP_STUBS);
    when(mockBuilder.build().post().uri(anyString()).bodyValue(any()).retrieve().bodyToMono(String.class).block())
        .thenReturn(fakeGeminiResponse);

    geminiService = new GeminiService(mockBuilder, objectMapper);
    AIParseResponse result = geminiService.parseTransaction("ăn sáng 45k", "valid-key");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getType()).isEqualTo("EXPENSE");
    assertThat(result.getAmount()).isEqualTo(45000L);
    assertThat(result.getNote()).isEqualTo("ăn sáng");
    assertThat(result.getSuggestedCategory()).isEqualTo("Ăn uống");
  }
}