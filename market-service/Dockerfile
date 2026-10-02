FROM eclipse-temurin:21-jdk as builder

WORKDIR /application
COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew :market-service:shadowJar -x test

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /application/market-service/build/libs/app.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
