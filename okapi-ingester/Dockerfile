FROM eclipse-temurin:21-jre

LABEL org.opencontainers.image.title="Okapi Ingester" \
      org.opencontainers.image.description="Okapi ingester handles OTEL / Prometheus writes and serving queries. This layer is stateless and can be scaled up and down depending on traffic. Ingestion via Kafka is supported but not necessary as ingester ships with a durable local WAL that does local buffering and thus doesn't require an additional queue such as Kafka. Ingester should only be exposed to an internal network and never to users." \
      org.opencontainers.image.source="https://github.com/okapi-core/engine"

WORKDIR /app

# Copy the prepackaged Spring Boot fat jar
ARG JAR_FILE=target/okapi-ingester-*.jar
RUN echo "Copying file ${JAR_FILE}"
COPY ${JAR_FILE} /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar","--spring.profiles.active=ch"]
