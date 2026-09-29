# Imagem do backend Menctor (Quarkus), com o frontend estático embutido.
#
# Build (a partir da RAIZ do repositório, para incluir o frontend):
#   docker build -f backend/Dockerfile -t menctor-backend .
# Rodar (as variáveis MAIL_* vêm do mesmo .env usado no desenvolvimento):
#   docker run --rm -p 8080:8080 --env-file .env menctor-backend

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY backend/src src
# os testes rodam fora da imagem: ./mvnw test
RUN mvn -B -q package -DskipTests

FROM alpine:3.20 AS frontend
COPY . /frontend
RUN rm -rf /frontend/backend

FROM eclipse-temurin:21-jre-alpine
RUN adduser -S -u 1001 menctor
WORKDIR /app
COPY --from=build --chown=1001 /build/target/quarkus-app/ ./
COPY --from=frontend --chown=1001 /frontend ./frontend
ENV PORT=8080 \
    MENCTOR_FRONTEND_DIR=/app/frontend \
    JAVA_OPTS="-Djava.awt.headless=true -XX:MaxRAMPercentage=75"
USER 1001
EXPOSE 8080
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar quarkus-run.jar"]
