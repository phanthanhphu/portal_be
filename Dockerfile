
# ===== BUILD STAGE =====
FROM gradle:8.7-jdk17 AS build

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

RUN chmod +x gradlew

RUN ./gradlew dependencies --no-daemon || true

COPY src src

RUN ./gradlew bootJar -x test --no-daemon


# ===== RUN STAGE =====
# Ubuntu 24.04 LTS - Java 17
FROM eclipse-temurin:17-jre-noble

WORKDIR /app

# Install LibreOffice and required fonts
RUN set -eux; \
    apt-get -o Acquire::Retries=5 update; \
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
        --no-install-recommends \
        libreoffice \
        fontconfig \
        fonts-dejavu \
        fonts-liberation; \
    rm -rf /var/lib/apt/lists/*

# LibreOffice configuration
ENV LIBREOFFICE_PATH=/usr/bin/soffice

# Application
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
