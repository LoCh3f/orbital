FROM eclipse-temurin:21-jdk as builder

WORKDIR /application
COPY build.gradle.kts settings.gradle.kts gradle.properties gradlew ./
COPY gradle ./gradle
COPY libs ./libs
COPY market-service ./market-service
COPY gateway/build.gradle.kts ./gateway/build.gradle.kts
COPY news-service/build.gradle.kts ./news-service/build.gradle.kts
COPY orbital-app/build.gradle.kts ./orbital-app/build.gradle.kts
RUN chmod +x ./gradlew
RUN --mount=type=cache,target=/root/.gradle,sharing=locked ./gradlew :market-service:shadowJar -x test

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /application/market-service/build/libs/app.jar app.jar
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
