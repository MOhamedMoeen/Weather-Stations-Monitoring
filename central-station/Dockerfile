FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy and install root parent POM first
COPY pom.xml ./pom.xml
RUN mvn install -N -DskipTests -B

# Copy and install BitCask module
COPY bitcaks/pom.xml ./bitcaks/pom.xml
COPY bitcaks/src ./bitcaks/src
RUN cd bitcaks && mvn install -DskipTests -B

# Now build central station
COPY central-station/pom.xml ./central-station/pom.xml
RUN cd central-station && mvn dependency:go-offline -B
COPY central-station/src ./central-station/src
RUN cd central-station && mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=builder /app/central-station/target/central-station-1.0-SNAPSHOT.jar app.jar
CMD ["java", "-jar", "app.jar"]