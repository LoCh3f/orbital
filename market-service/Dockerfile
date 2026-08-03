FROM eclipse-temurin:21-jdk as builder

WORKDIR /application
COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew :market-service:build -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /application/market-service/build/libs/market-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
