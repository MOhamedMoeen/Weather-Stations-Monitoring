FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
# Download dependencies first (layer caching)
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -q -DskipTests

# ─── Runtime image ───────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/untitled-1.0-SNAPSHOT.jar app.jar

# Default: port 8080, data dir /app/bitcask-data
# Override via docker-compose command: ["8080", "/app/bitcask-data"]
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["8080", "/app/bitcask-data"]g