FROM eclipse-temurin:17-jdk-alpine as builder
WORKDIR /app
COPY pom.xml .
COPY fairqueue-common fairqueue-common
COPY booking-gate booking-gate
RUN apk add --no-cache maven
RUN mvn -pl fairqueue-common clean install -DskipTests
RUN mvn -pl booking-gate clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/booking-gate/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
