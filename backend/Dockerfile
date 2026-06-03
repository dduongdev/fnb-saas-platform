# ============================================================
#  Dockerfile — FNB SaaS Platform
#  Multi-stage build:
#    Stage 1: Build React frontend (Node.js)
#    Stage 2: Build Spring Boot backend (Maven) + copy frontend
#    Stage 3: Runtime (Java JRE)
# ============================================================

# ------------------------------------
#  Stage 1: Build React Frontend
# ------------------------------------
FROM node:20-alpine AS frontend-build

WORKDIR /app

# Cache npm dependencies separately
COPY frontend/package*.json ./frontend/
RUN cd frontend && npm ci

# Build frontend
COPY frontend/ ./frontend/
# VITE_API_URL='' để frontend gọi API relative (cùng origin)
RUN cd frontend && VITE_API_URL='' npm run build

# ------------------------------------
#  Stage 2: Build Spring Boot Backend
#  + Copy frontend build vào static resources
# ------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS backend-build

WORKDIR /app

# Copy source code (bao gồm cả pom.xml)
COPY backend/pom.xml backend/src ./

# Copy frontend build vào thư mục static của Spring Boot
COPY --from=frontend-build /app/frontend/dist/ ./src/main/resources/static/

# Build JAR (skip tests để tăng tốc build Docker)
RUN mvn clean package -DskipTests

# ------------------------------------
#  Stage 3: Runtime
# ------------------------------------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Tạo user non-root để chạy ứng dụng (bảo mật hơn)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=backend-build /app/target/*.jar app.jar

# Cấu múi giờ cho container
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Ho_Chi_Minh /etc/localtime && \
    echo "Asia/Ho_Chi_Minh" > /etc/timezone

USER appuser

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
