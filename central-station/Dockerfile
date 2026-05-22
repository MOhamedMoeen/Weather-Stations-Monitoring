FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy the entire monorepo (root pom.xml + all modules)
COPY . .

RUN mvn clean install -DskipTests
RUN mvn -pl central-station -am package -DskipTests

FROM eclipse-temurin:17-jdk
WORKDIR /app

COPY --from=builder /app/central-station/target/*.jar app.jar
RUN mkdir -p /app/archivedData
CMD ["java", "-jar", "app.jar"]