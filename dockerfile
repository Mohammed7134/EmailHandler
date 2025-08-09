# Stage 1: Build the JAR using Maven
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /app

# Copy everything (src/, pom.xml, etc.)
COPY . .

# Build the JAR (skip tests optionally)
RUN mvn clean package -DskipTests

# Stage 2: Run the app using a lighter image
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# Copy the built JAR from stage 1
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
