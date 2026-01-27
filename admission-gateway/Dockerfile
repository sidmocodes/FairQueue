FROM eclipse-temurin:17-jdk-alpine as builder
WORKDIR /app
COPY pom.xml .
COPY fairqueue-common fairqueue-common
COPY admission-gateway admission-gateway
RUN apk add --no-cache maven
RUN mvn -pl fairqueue-common clean install -DskipTests
RUN mvn -pl admission-gateway clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/admission-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
