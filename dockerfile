# Use a base image with Java 17 and Maven installed
FROM maven:3.8.7-openjdk-17 AS build

# Set working directory inside container
WORKDIR /app

# Copy your pom.xml and source code
COPY pom.xml .
COPY src ./src

# Build your project and package Azure Functions
RUN mvn clean package

# Use the official Azure Functions Java runtime image for running the function app
FROM mcr.microsoft.com/azure-functions/java:4-java17

# Set environment variables for the function app
ENV AzureWebJobsScriptRoot=/home/site/wwwroot \
    AzureFunctionsJobHost__Logging__Console__IsEnabled=true

# Copy the built function app from the build stage to the runtime location
COPY --from=build /app/target/azure-functions/EmailHandler-*/ /home/site/wwwroot/

# Expose the Azure Functions port
EXPOSE 7071

# Start the Azure Functions host
CMD [ "java", "-jar", "/azure-functions-host/Microsoft.Azure.Functions.Host.dll" ]
