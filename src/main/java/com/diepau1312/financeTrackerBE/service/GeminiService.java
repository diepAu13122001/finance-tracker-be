package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.ai.AIParseResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiService {

  private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent";

  private final WebClient.Builder webClientBuilder;
  private final ObjectMapper objectMapper;

  /**
   * Parse text tiếng Việt thành thông tin giao dịch.
   * <p>
   * Ví dụ input:  "ăn trưa 45k ở KFC"
   * Ví dụ output: { type: EXPENSE, amount: 45000, note: "ăn trưa KFC", suggestedCategory: "Ăn uống" }
   * <p>
   * Dùng WebClient (reactive) thay RestTemplate — Spring Boot 3 khuyến khích dùng WebClient.
   */
  public AIParseResponse parseTransaction(String text, String apiKey) {
    String prompt = buildPrompt(text);

    Map<String, Object> requestBody = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

    try {
      // Lấy cả status code để xử lý 429 riêng
      var response = webClientBuilder.build().post().uri(GEMINI_URL + "?key=" + apiKey).bodyValue(requestBody).retrieve()
          // Xử lý lỗi HTTP trước khi đọc body
          .onStatus(status -> status.value() == 429, clientResponse -> Mono.error(new RuntimeException("RATE_LIMIT"))).onStatus(status -> status.value() == 400, clientResponse -> Mono.error(new RuntimeException("BAD_REQUEST"))).onStatus(status -> status.value() == 403, clientResponse -> Mono.error(new RuntimeException("INVALID_KEY"))).bodyToMono(String.class).block();

      return parseGeminiResponse(response, text);

    } catch (RuntimeException e) {
      // Map error code sang message tiếng Việt rõ ràng
      String message = switch (e.getMessage()) {
        case "RATE_LIMIT" -> "Gọi API quá nhanh. Đợi 1 phút rồi thử lại.";
        case "BAD_REQUEST" -> "Request không hợp lệ. Kiểm tra lại API key.";
        case "INVALID_KEY" -> "API key không có quyền truy cập. Kiểm tra lại.";
        default -> "Lỗi kết nối: " + e.getMessage();
      };

      log.warn("Gemini API error [{}]: {}", e.getMessage(), text);
      return AIParseResponse.builder().success(false).rawText(text).errorMessage(message).build();
    }
  }

  /**
   * Prompt engineering:
   * - Chỉ trả về JSON thuần (không markdown, không giải thích)
   * - Ví dụ cụ thể giúp model hiểu đúng format
   * - Fallback type = EXPENSE nếu không rõ
   */
  private String buildPrompt(String text) {
    return """
        Phân tích đoạn văn tiếng Việt sau và trả về JSON thuần (không có ```json, không giải thích):
        
        Text: "%s"
        
        Trả về JSON với format CHÍNH XÁC này:
        {"type":"EXPENSE","amount":45000,"note":"ăn trưa","suggestedCategory":"Ăn uống"}
        
        Quy tắc:
        - type: "INCOME" nếu là thu nhập (lương, thưởng, bán hàng), còn lại là "EXPENSE"
        - amount: số nguyên VND (45k=45000, 1.5tr=1500000, 2m=2000000)
        - note: tóm tắt ngắn gọn bằng tiếng Việt
        - suggestedCategory: một trong [Ăn uống, Di chuyển, Mua sắm, Giải trí, Sức khỏe, Giáo dục, Lương, Đầu tư, Khác]
        """.formatted(text);
  }

  /**
   * Parse JSON response từ Gemini.
   * Gemini trả về nested JSON: candidates[0].content.parts[0].text
   */
  private AIParseResponse parseGeminiResponse(String responseJson, String originalText) {
    try {
      JsonNode root = objectMapper.readTree(responseJson);
      // Đọc text từ response Gemini
      String content = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();

      // Parse JSON kết quả Gemini trả về
      JsonNode result = objectMapper.readTree(content.trim());

      return AIParseResponse.builder().success(true).rawText(originalText).type(result.path("type").asText("EXPENSE")).amount(result.path("amount").asLong(0)).note(result.path("note").asText("")).suggestedCategory(result.path("suggestedCategory").asText()).build();

    } catch (Exception e) {
      log.warn("Failed to parse Gemini response: {}", e.getMessage());
      return AIParseResponse.builder().success(false).rawText(originalText).errorMessage("Không thể phân tích kết quả từ AI").build();
    }
  }
}