FROM node:24-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk-jammy AS backend
WORKDIR /build/backend
COPY backend/ ./
COPY --from=frontend /build/frontend/dist/ ./src/main/resources/static/
RUN chmod +x mvnw && ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system tradesphere && useradd --system --gid tradesphere tradesphere
COPY --from=backend --chown=tradesphere:tradesphere /build/backend/target/tradesphere-0.0.1-SNAPSHOT.jar app.jar
USER tradesphere
ENV SPRING_PROFILES_ACTIVE=demo SERVER_ADDRESS=0.0.0.0 SERVER_PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
