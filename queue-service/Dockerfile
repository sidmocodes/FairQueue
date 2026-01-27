FROM eclipse-temurin:17-jdk-alpine as builder
WORKDIR /app
COPY pom.xml .
COPY fairqueue-common fairqueue-common
COPY queue-service queue-service
RUN apk add --no-cache maven
RUN mvn -pl fairqueue-common clean install -DskipTests
RUN mvn -pl queue-service clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/queue-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
