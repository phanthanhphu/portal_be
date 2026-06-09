# ===== BUILD STAGE =====
FROM gradle:8.7-jdk17 AS build
WORKDIR /app

COPY . .
RUN chmod +x gradlew
RUN ./gradlew clean bootJar -x test --no-daemon

# ===== RUN STAGE =====
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]