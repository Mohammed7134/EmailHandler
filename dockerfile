FROM mcr.microsoft.com/openjdk/jdk:17-ubuntu

# Install Node.js (needed by Azure Functions Core Tools)
RUN apt-get update && apt-get install -y curl gnupg git && \
    curl -fsSL https://deb.nodesource.com/setup_18.x | bash - && \
    apt-get install -y nodejs

# Install Azure Functions Core Tools
RUN npm install -g azure-functions-core-tools@4 --unsafe-perm true

# Install Maven
RUN apt-get install -y maven

# Set working directory
WORKDIR /app

# Copy project
COPY . .

# Build the project
RUN mvn clean package

# Expose Azure Functions default port
EXPOSE 7071

# Run Azure Functions locally inside container
CMD ["mvn", "azure-functions:run"]
