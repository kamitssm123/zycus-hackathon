# syntax=docker/dockerfile:1

############################
# deps — cached layer, only re-runs when pom.xml changes
############################
FROM maven:3.9-eclipse-temurin-21 AS deps
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

############################
# dev — hot reload via spring-boot-devtools, source is bind-mounted by compose
############################
FROM deps AS dev
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 8080
CMD ["mvn", "-B", "spring-boot:run", "-Dspring-boot.run.fork=false"]

############################
# build — produces the fat jar
############################
FROM deps AS build
WORKDIR /app
COPY src ./src
RUN mvn -B -q clean package -DskipTests

############################
# runtime — slim, non-root
############################
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
