FROM gradle:8.13-jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN gradle build -x test --no-daemon

FROM amazoncorretto:21-alpine
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
USER spring:spring
ENTRYPOINT ["java", "-jar", "app.jar"]
