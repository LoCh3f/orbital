FROM eclipse-temurin:21-jdk as builder

WORKDIR /application
COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew :news-service:build -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /application/news-service/build/libs/news-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
