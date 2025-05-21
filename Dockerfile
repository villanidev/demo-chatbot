# Maven build stage
FROM maven:3.9.9-eclipse-temurin-21-alpine as build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src/ ./src/
RUN mvn clean package -DskipTests=true

# Spring Boot package stage
FROM eclipse-temurin:21-jre-alpine
COPY --from=build app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]