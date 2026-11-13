FROM eclipse-temurin:21-jdk as builder

WORKDIR /application
COPY build.gradle.kts settings.gradle.kts gradle.properties gradlew ./
COPY gradle ./gradle
COPY libs ./libs
COPY gateway ./gateway
COPY market-service/build.gradle.kts ./market-service/build.gradle.kts
COPY news-service/build.gradle.kts ./news-service/build.gradle.kts
COPY orbital-app/build.gradle.kts ./orbital-app/build.gradle.kts
RUN chmod +x ./gradlew
RUN --mount=type=cache,target=/root/.gradle,sharing=locked ./gradlew :gateway:shadowJar -x test

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /application/gateway/build/libs/app.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
