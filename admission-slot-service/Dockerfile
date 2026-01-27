FROM eclipse-temurin:17-jdk-alpine as builder
WORKDIR /app
COPY pom.xml .
COPY fairqueue-common fairqueue-common
COPY admission-slot-service admission-slot-service
RUN apk add --no-cache maven
RUN mvn -pl fairqueue-common clean install -DskipTests
RUN mvn -pl admission-slot-service clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/admission-slot-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
