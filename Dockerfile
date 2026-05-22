# ---- Stage 1: Build ----
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY backend/pom.xml .
# Pre-download dependencies for layer caching
RUN mvn dependency:go-offline -B
COPY backend/src ./src
RUN mvn clean package -DskipTests -B

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre-alpine
LABEL maintainer="dwaigx"
LABEL description="小登哥的灵魂合盘 - Soulmate Compatibility H5 App"

WORKDIR /app

# Copy built jar
COPY --from=builder /app/target/zodiac-dewey.jar app.jar

# Copy frontend static files
COPY frontend/ /app/static/

# Runtime data directory for SQLite
RUN mkdir -p /app/data

EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
