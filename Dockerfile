FROM eclipse-temurin:17-jre-jammy

RUN useradd --system --create-home --uid 10001 appuser

WORKDIR /app
COPY --chown=appuser:appuser app.jar app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
