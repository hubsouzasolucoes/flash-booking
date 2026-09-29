FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 booking \
    && useradd --system --uid 10001 --gid booking --create-home booking
COPY --from=build /workspace/target/flash-booking-1.0.0.jar app.jar
RUN chown booking:booking app.jar
USER booking
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
