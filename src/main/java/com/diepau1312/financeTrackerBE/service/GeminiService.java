package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.ai.AIClassifyResponse;
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
   * Ví dụ input: "ăn trưa 45k ở KFC"
   * Ví dụ output: { type: EXPENSE, amount: 45000, note: "ăn trưa KFC",
   * suggestedCategory: "Ăn uống" }
   * <p>
   * Dùng WebClient (reactive) thay RestTemplate — Spring Boot 3 khuyến khích dùng
   * WebClient.
   */
  public AIParseResponse parseTransaction(String text, String apiKey) {
    String prompt = buildPrompt(text);

    Map<String, Object> requestBody = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

    try {
      // Lấy cả status code để xử lý 429 riêng
      var response = webClientBuilder.build().post().uri(GEMINI_URL + "?key=" + apiKey).bodyValue(requestBody)
          .retrieve()
          // Xử lý lỗi HTTP trước khi đọc body
          .onStatus(status -> status.value() == 429, clientResponse -> Mono.error(new RuntimeException("RATE_LIMIT")))
          .onStatus(status -> status.value() == 400, clientResponse -> Mono.error(new RuntimeException("BAD_REQUEST")))
          .onStatus(status -> status.value() == 403, clientResponse -> Mono.error(new RuntimeException("INVALID_KEY")))
          .bodyToMono(String.class).block();

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

      return AIParseResponse.builder().success(true).rawText(originalText).type(result.path("type").asText("EXPENSE"))
          .amount(result.path("amount").asLong(0)).note(result.path("note").asText(""))
          .suggestedCategory(result.path("suggestedCategory").asText()).build();

    } catch (Exception e) {
      log.warn("Failed to parse Gemini response: {}", e.getMessage());
      return AIParseResponse.builder().success(false).rawText(originalText)
          .errorMessage("Không thể phân tích kết quả từ AI").build();
    }
  }

  /**
   * Phân loại sản phẩm gia đình thành category.
   *
   * Tách riêng khỏi parseTransaction vì:
   * - Prompt khác nhau hoàn toàn (phân loại sản phẩm vs parse số tiền)
   * - Response format khác (category string vs amount + type)
   * - Có thể tune riêng cho household use case
   *
   * Ví dụ:
   * Input: "Sữa rửa mặt La Roche-Posay Effaclar"
   * Output: { category: "SKINCARE", subcategory: "Skincare - Làm sạch" }
   */
  public AIClassifyResponse classifyHouseholdItem(String name, String brand, String apiKey) {
    String prompt = buildClassifyPrompt(name, brand);

    Map<String, Object> requestBody = Map.of(
        "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

    try {
      var response = webClientBuilder.build()
          .post()
          .uri(GEMINI_URL + "?key=" + apiKey)
          .bodyValue(requestBody)
          .retrieve()
          .onStatus(s -> s.value() == 429, r -> Mono.error(new RuntimeException("RATE_LIMIT")))
          .onStatus(s -> s.value() == 403, r -> Mono.error(new RuntimeException("INVALID_KEY")))
          .bodyToMono(String.class)
          .block();

      return parseClassifyResponse(response, name);

    } catch (RuntimeException e) {
      String message = switch (e.getMessage()) {
        case "RATE_LIMIT" -> "Gọi API quá nhanh. Đợi 1 phút rồi thử lại.";
        case "INVALID_KEY" -> "API key không hợp lệ.";
        default -> "Lỗi kết nối: " + e.getMessage();
      };
      return AIClassifyResponse.builder()
          .success(false).errorMessage(message).build();
    }
  }

  private String buildClassifyPrompt(String name, String brand) {
    String input = brand != null && !brand.isBlank()
        ? name + " (" + brand + ")"
        : name;

    return """
        Phân loại sản phẩm gia đình này vào đúng 1 category. Trả về JSON thuần:

        Sản phẩm: "%s"

        {"category":"SKINCARE","subcategory":"Skincare - Dưỡng ẩm","reasoning":"Là kem dưỡng da"}

        Quy tắc category:
        - SKINCARE:  mỹ phẩm, chăm sóc da mặt, tóc, cơ thể
        - HOUSECARE: chất tẩy rửa, lau nhà, vệ sinh phòng tắm
        - FOOD:      thực phẩm, đồ uống, gia vị
        - CLOTHES:   quần áo, giày dép, phụ kiện
        - OTHER:     không thuộc 4 loại trên

        Chỉ dùng đúng 5 category trên. Không giải thích thêm ngoài JSON.
        """.formatted(input);
  }

  private AIClassifyResponse parseClassifyResponse(String responseJson, String originalName) {
    try {
      JsonNode root = objectMapper.readTree(responseJson);
      String content = root.path("candidates").get(0)
          .path("content").path("parts").get(0)
          .path("text").asText();
      JsonNode result = objectMapper.readTree(content.trim());

      return AIClassifyResponse.builder()
          .success(true)
          .category(result.path("category").asText("OTHER"))
          .subcategory(result.path("subcategory").asText(""))
          .reasoning(result.path("reasoning").asText(""))
          .build();

    } catch (Exception e) {
      log.warn("Failed to parse classify response for '{}': {}", originalName, e.getMessage());
      return AIClassifyResponse.builder()
          .success(false)
          .errorMessage("Không thể phân tích kết quả từ AI")
          .build();
    }
  }
}