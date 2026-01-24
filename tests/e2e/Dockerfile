FROM mcr.microsoft.com/playwright:v1.50.0-noble

WORKDIR /app

# Copy package files
COPY package.json ./

# Install dependencies
RUN npm install

# Copy test files
COPY . .

# Default command
CMD ["npx", "playwright", "test"]
