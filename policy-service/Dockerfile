# ---------- Stage 1: build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# prima solo i file che descrivono le dipendenze: finché non cambiano, questo strato resta in cache
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline

# poi il codice: cambia spesso, quindi sta dopo
COPY src src
RUN ./mvnw -q package -DskipTests

# estrae il jar in "strati" (Spring Boot layers): dipendenze, loader, snapshot, applicazione
RUN cp target/*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app

# dal più stabile al più mutevole: a ogni build cambia quasi solo l'ultimo strato
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
