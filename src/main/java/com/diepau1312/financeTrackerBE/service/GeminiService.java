package com.diepau1312.financeTrackerBE.service;

import com.diepau1312.financeTrackerBE.dto.ai.AIAnalyzeResponse;
import com.diepau1312.financeTrackerBE.dto.ai.AIParseResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiService {

  private static final String GEMINI_URL =
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

  private final WebClient.Builder webClientBuilder;
  private final ObjectMapper objectMapper;

  /**
   * Parse text tiếng Việt thành thông tin giao dịch.
   * Ví dụ: "ăn trưa 45k ở KFC" → {type: EXPENSE, amount: 45000, note: "ăn trưa KFC"}
   */
  public AIParseResponse parseTransaction(String text, String apiKey) {
    String prompt = buildPrompt(text);
    Map<String, Object> requestBody =
        Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

    try {
      var response = webClientBuilder.build()
          .post().uri(GEMINI_URL + "?key=" + apiKey)
          .bodyValue(requestBody).retrieve()
          .onStatus(s -> s.value() == 429, r -> Mono.error(new RuntimeException("RATE_LIMIT")))
          .onStatus(s -> s.value() == 400, r -> Mono.error(new RuntimeException("BAD_REQUEST")))
          .onStatus(s -> s.value() == 403, r -> Mono.error(new RuntimeException("INVALID_KEY")))
          .bodyToMono(String.class).block();

      return parseGeminiResponse(response, text);

    } catch (RuntimeException e) {
      String message = switch (e.getMessage()) {
        case "RATE_LIMIT" -> "Gọi API quá nhanh. Đợi 1 phút rồi thử lại.";
        case "BAD_REQUEST" -> "Request không hợp lệ. Kiểm tra lại API key.";
        case "INVALID_KEY" -> "API key không có quyền truy cập. Kiểm tra lại.";
        default -> "Lỗi kết nối: " + e.getMessage();
      };
      log.warn("Gemini parse error [{}]: {}", e.getMessage(), text);
      return AIParseResponse.builder().success(false).rawText(text).errorMessage(message).build();
    }
  }

  /**
   * Phân tích chi tiêu tháng và đưa ra AI insights.
   * Backend tổng hợp dữ liệu từ TransactionService rồi gọi phương thức này.
   *
   * Design choice: nhận data đã được tổng hợp thay vì query DB trực tiếp
   * → GeminiService chỉ làm 1 việc: call Gemini API
   */
  public AIAnalyzeResponse analyzeSpending(
      long totalIncome, long totalExpense,
      List<String> topExpenseCategories,
      int year, int month, String apiKey) {

    String prompt = buildAnalysisPrompt(
        totalIncome, totalExpense, topExpenseCategories, year, month);
    Map<String, Object> requestBody =
        Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

    try {
      var response = webClientBuilder.build()
          .post().uri(GEMINI_URL + "?key=" + apiKey)
          .bodyValue(requestBody).retrieve()
          .onStatus(s -> s.value() == 429, r -> Mono.error(new RuntimeException("RATE_LIMIT")))
          .onStatus(s -> s.value() == 403, r -> Mono.error(new RuntimeException("INVALID_KEY")))
          .bodyToMono(String.class).block();

      return parseAnalysisResponse(response);

    } catch (RuntimeException e) {
      String message = switch (e.getMessage()) {
        case "RATE_LIMIT" -> "Gọi API quá nhanh. Đợi 1 phút rồi thử lại.";
        case "INVALID_KEY" -> "API key không hợp lệ. Kiểm tra lại.";
        default -> "Lỗi kết nối: " + e.getMessage();
      };
      log.warn("Gemini analyze error [{}]", e.getMessage());
      return AIAnalyzeResponse.builder().success(false).errorMessage(message).build();
    }
  }

  // ── Prompt builders ────────────────────────────────────────────────────────

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
   * Prompt cho analyze-spending:
   * - Cung cấp context rõ ràng (số liệu cụ thể)
   * - Yêu cầu JSON thuần để parse dễ dàng
   * - Giới hạn scope: 1 overview, 1 insight, 1 suggestion, warnings nếu có
   */
  private String buildAnalysisPrompt(
      long totalIncome, long totalExpense,
      List<String> topExpenseCategories, int year, int month) {

    String fmtIncome  = String.format("%,d", totalIncome);
    String fmtExpense = String.format("%,d", totalExpense);
    String fmtBalance = String.format("%,d", totalIncome - totalExpense);
    String categories = topExpenseCategories.isEmpty()
        ? "Chưa có dữ liệu danh mục"
        : String.join("; ", topExpenseCategories);

    return """
        Phân tích chi tiêu tháng %d/%d và trả về JSON thuần (không có ```json, không giải thích):
        
        Dữ liệu tháng %d/%d:
        - Thu nhập: %s VND
        - Chi tiêu: %s VND
        - Còn lại: %s VND
        - Top danh mục chi: %s
        
        Trả về JSON với format CHÍNH XÁC:
        {"overview":"...","topInsight":"...","suggestion":"...","warnings":["..."]}
        
        Quy tắc:
        - overview: 1-2 câu nhận xét tổng quan, thân thiện, bằng tiếng Việt
        - topInsight: 1 insight quan trọng nhất (VD: "Chi ăn uống chiếm 40%% thu nhập")
        - suggestion: 1 gợi ý cụ thể, thực tế để cải thiện tài chính tháng tới
        - warnings: mảng string cảnh báo. Thêm cảnh báo khi chi > thu hoặc chi > 80%% thu. Mảng rỗng [] nếu không có.
        """.formatted(month, year, month, year,
            fmtIncome, fmtExpense, fmtBalance, categories);
  }

  // ── Response parsers ───────────────────────────────────────────────────────

  private AIParseResponse parseGeminiResponse(String responseJson, String originalText) {
    try {
      JsonNode root = objectMapper.readTree(responseJson);
      String content = root.path("candidates").get(0)
          .path("content").path("parts").get(0).path("text").asText();
      JsonNode result = objectMapper.readTree(content.trim());

      return AIParseResponse.builder()
          .success(true)
          .rawText(originalText)
          .type(result.path("type").asText("EXPENSE"))
          .amount(result.path("amount").asLong(0))
          .note(result.path("note").asText(""))
          .suggestedCategory(result.path("suggestedCategory").asText())
          .build();

    } catch (Exception e) {
      log.warn("Failed to parse Gemini parse response: {}", e.getMessage());
      return AIParseResponse.builder()
          .success(false).rawText(originalText)
          .errorMessage("Không thể phân tích kết quả từ AI")
          .build();
    }
  }

  private AIAnalyzeResponse parseAnalysisResponse(String responseJson) {
    try {
      JsonNode root = objectMapper.readTree(responseJson);
      String content = root.path("candidates").get(0)
          .path("content").path("parts").get(0).path("text").asText();
      JsonNode result = objectMapper.readTree(content.trim());

      List<String> warnings = new ArrayList<>();
      result.path("warnings").forEach(w -> warnings.add(w.asText()));

      return AIAnalyzeResponse.builder()
          .success(true)
          .overview(result.path("overview").asText(""))
          .topInsight(result.path("topInsight").asText(""))
          .suggestion(result.path("suggestion").asText(""))
          .warnings(warnings)
          .build();

    } catch (Exception e) {
      log.warn("Failed to parse Gemini analysis response: {}", e.getMessage());
      return AIAnalyzeResponse.builder()
          .success(false)
          .errorMessage("Không thể phân tích kết quả từ AI")
          .build();
    }
  }
}
