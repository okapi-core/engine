FROM eclipse-temurin:21-jre

LABEL org.opencontainers.image.title="Okapi Web" \
      org.opencontainers.image.description="Okapi web is the frontend and API layer for Okapi. It's responsible for serving web UI, handling authentication and authorization. Web is a protected layer that can be exposed to users. All external communication with an Okapi deployment is piped through the web layer." \
      org.opencontainers.image.source="https://github.com/okapi-core/engine"

WORKDIR /app

# Copy the prepackaged Spring Boot fat jar
ARG JAR_FILE=target/okapi-web-*.jar
RUN echo "Copying file ${JAR_FILE}"
COPY ${JAR_FILE} /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]
