# Stage 1: Build the jar with Maven
FROM maven:3.9.4-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# Stage 2: Run the app
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=build /app/target/EmailHandler-1.0-SNAPSHOT.jar ./app.jar

EXPOSE 7071

CMD ["java", "-jar", "app.jar"]
