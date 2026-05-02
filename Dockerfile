# ── Stage 1: Build ────────────────────────────────────────────────────────────
# Dùng image Maven để build — không cần cài Maven trên server
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy pom.xml trước — Docker cache layer này
# Nếu chỉ sửa code (không sửa pom.xml), bước này được dùng từ cache
# → Build nhanh hơn nhiều lần sau
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code và build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
# Image runtime nhỏ hơn — không có Maven, không có source code
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Tạo user non-root để chạy app — bảo mật tốt hơn
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Chỉ copy file JAR từ stage build
COPY --from=builder /app/target/*.jar app.jar

# Expose port
EXPOSE 8081

# Biến môi trường mặc định — override bằng docker-compose
ENV SPRING_PROFILES_ACTIVE=prod

# Chạy app
ENTRYPOINT ["java", "-jar", "app.jar"]