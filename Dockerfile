# Build stage: compile and package with the wrapper, so the image uses the
# same Maven and JDK the repo pins.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN ./mvnw -q -B -DskipTests package

# Runtime stage: a JRE only, nothing to build with.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# MaxRAMPercentage keeps the heap inside a small container (Render's free
# tier gives 512 MB) instead of the JVM guessing from the host.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
