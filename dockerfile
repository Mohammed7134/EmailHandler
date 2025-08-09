FROM maven:3.9.4-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package

FROM mcr.microsoft.com/azure-functions/java:4-java17

ENV AzureWebJobsScriptRoot=/home/site/wwwroot \
    AzureFunctionsJobHost__Logging__Console__IsEnabled=true

COPY --from=build /app/target/azure-functions/EmailHandler-*/ /home/site/wwwroot/

EXPOSE 7071
