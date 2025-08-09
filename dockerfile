FROM mcr.microsoft.com/openjdk/jdk:17-ubuntu

# Install dependencies: Node.js, Maven, and libicu for Azure Functions Core Tools
RUN apt-get update && apt-get install -y curl gnupg git libicu-dev && \
    curl -fsSL https://deb.nodesource.com/setup_18.x | bash - && \
    apt-get install -y nodejs maven

# Install Azure Functions Core Tools
RUN npm install -g azure-functions-core-tools@4 --unsafe-perm true

# Set working directory
WORKDIR /app

# Copy project
COPY . .

# Build the project
RUN mvn clean package

# Expose Azure Functions default port
EXPOSE 7071

# Run Azure Functions
CMD ["mvn", "azure-functions:run"]
