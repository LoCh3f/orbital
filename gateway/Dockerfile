FROM eclipse-temurin:21-jdk as builder

WORKDIR /application
COPY . .
RUN chmod +x ./gradlew
RUN  ./gradlew :gateway:build -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /application/gateway/build/libs/gateway-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
