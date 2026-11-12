# ---------- Stage 1: build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# prima i file di configurazione Gradle: strato in cache finché le dipendenze non cambiano
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon -q dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon -q bootJar -x test

# Gradle produce anche un "-plain.jar" che non ci serve
RUN cp $(ls build/libs/*.jar | grep -v plain) app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

# cartella dei documenti caricati, scrivibile dall'utente non privilegiato
RUN mkdir -p /data/documents && chown -R app:app /data

USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
