package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.ai.AIClassifyResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiClassificationTest {

  @Mock
  private WebClient.Builder webClientBuilder;
  @Mock
  private WebClient webClient;
  @Mock
  private WebClient.RequestBodyUriSpec requestBodyUriSpec;
  @Mock
  private WebClient.RequestBodySpec requestBodySpec;
  @Mock
  private WebClient.ResponseSpec responseSpec;
  @Mock
  private WebClient.RequestHeadersSpec headersSpec;

  @InjectMocks
  private GeminiService geminiService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    // Inject ObjectMapper vì @InjectMocks không inject final fields
    var field = getClass().getSuperclass();
    try {
      var f = GeminiService.class.getDeclaredField("objectMapper");
      f.setAccessible(true);
      f.set(geminiService, objectMapper);
    } catch (Exception e) {
      // ignore — ObjectMapper đã được inject qua constructor trong thực tế
    }
  }

  // ── Test 1: Gemini trả SKINCARE đúng ─────────────────────────────────────

  @Test
  @DisplayName("classifyHouseholdItem() — Gemini trả SKINCARE → response đúng")
  void classify_skincare_success() {
    // Giả lập response JSON của Gemini
    String geminiResponse = """
        {
          "candidates": [{
            "content": {
              "parts": [{
                "text": "{\\"category\\":\\"SKINCARE\\",\\"subcategory\\":\\"Skincare - Làm sạch\\",\\"reasoning\\":\\"Sữa rửa mặt là sản phẩm chăm sóc da\\"}"
              }]
            }
          }]
        }
        """;

    // Mock WebClient chain
    when(webClientBuilder.build()).thenReturn(webClient);
    when(webClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
    when(requestBodySpec.bodyValue(any())).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(geminiResponse));

    // Act
    AIClassifyResponse result = geminiService.classifyHouseholdItem(
        "Sữa rửa mặt La Roche-Posay", "La Roche-Posay", "fake-key");

    // Assert
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getCategory()).isEqualTo("SKINCARE");
    assertThat(result.getSubcategory()).isEqualTo("Skincare - Làm sạch");
  }

  // ── Test 2: API key sai (403) → trả error message ────────────────────────

  @Test
  @DisplayName("classifyHouseholdItem() — 403 INVALID_KEY → success=false + error message")
  void classify_invalidKey_returnsError() {
    when(webClientBuilder.build()).thenReturn(webClient);
    when(webClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
    when(requestBodySpec.bodyValue(any())).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    // onStatus cho 403 throw INVALID_KEY
    when(responseSpec.onStatus(any(), any())).thenAnswer(inv -> {
      // Simulate 403 → throw RuntimeException("INVALID_KEY")
      throw new RuntimeException("INVALID_KEY");
    });

    AIClassifyResponse result = geminiService.classifyHouseholdItem(
        "Test product", null, "wrong-key");

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getErrorMessage()).contains("API key");
  }

  // ── Test 3: Gemini trả JSON không hợp lệ → success=false gracefully ──────

  @Test
  @DisplayName("classifyHouseholdItem() — JSON parse fail → success=false, không throw")
  void classify_malformedJson_gracefulFail() {
    // Gemini trả text ngẫu nhiên thay vì JSON
    String badResponse = """
        {
          "candidates": [{
            "content": {
              "parts": [{"text": "Đây là sản phẩm chăm sóc da"}]
            }
          }]
        }
        """;

    when(webClientBuilder.build()).thenReturn(webClient);
    when(webClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
    when(requestBodySpec.bodyValue(any())).thenReturn(headersSpec);
    when(headersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(badResponse));

    // Không được throw exception — phải handle gracefully
    AIClassifyResponse result = geminiService.classifyHouseholdItem(
        "Test", null, "fake-key");

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getErrorMessage()).isNotBlank();
  }
}