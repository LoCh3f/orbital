FROM eclipse-temurin:21-jdk as builder

WORKDIR /application
COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew :news-service:shadowJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /application/news-service/build/libs/app.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
