# ClaimsHub

Mini sistema assicurativo per la gestione di **polizze** e **sinistri**, composto da due microservizi REST. Progetto di pratica per esercitarsi su Java, Spring, sicurezza, testing e integrazione tra servizi.

> Stato: i due servizi sono completi e testati. I componenti AWS, Oracle, Docker, Jenkins e SonarQube sono simulati o rimandati (vedi la tabella "Cosa è simulato").

## Architettura

```
                 POST /auth/login
   client  ───────────────────────────►  policy-service  (8080)
     │                                    Spring MVC, Spring JDBC, H2 (modalità Oracle)
     │  Authorization: Bearer <JWT>       emette e valida i JWT
     │
     │                                         ▲
     │  Authorization: Bearer <JWT>            │ WebClient
     └──────────────────────────►  claims-service  (8081)
                                    WebFlux, valida lo stesso JWT
                                    propaga token, X-Request-Id, X-Client-App
                                    │
                       ┌────────────┼─────────────────┐
                       ▼            ▼                 ▼
               DocumentStore   ClaimHistoryStore   NotificationPublisher
               (porta, S3)     (porta, DynamoDB)   (porta, SQS)
```

Ogni sinistro appartiene a una polizza: alla creazione, claims-service chiede a policy-service se la polizza esiste ed è `ACTIVE`.

### Architettura a porte

claims-service non parla mai direttamente con AWS. Il service dipende da **interfacce** (porte), e le tecnologie concrete sono **adapter** intercambiabili. Oggi gli adapter sono in memoria o su file locali. Per passare ad AWS si scrive un nuovo adapter che implementa la stessa interfaccia, senza modificare il service.

## Stack

| Area | Tecnologie |
|---|---|
| Linguaggio | Java 21 |
| Framework | Spring Boot (Spring MVC, Spring WebFlux), Spring JDBC |
| Sicurezza | Spring Security, OAuth2 Resource Server, JWT (HS256) |
| Dati | H2 in modalità Oracle |
| Build | Maven (policy-service), Gradle (claims-service) |
| Test | JUnit 5, Mockito, MockMvc, WebTestClient, StepVerifier, MockWebServer, JaCoCo |
| API docs | Swagger / OpenAPI (springdoc) |

## Struttura del repository

```
claimshub/
├── policy-service/    # Maven, porta 8080
├── claims-service/    # Gradle, porta 8081
└── README.md
```

## Come avviare il progetto

Requisiti: JDK 21, Git. Maven e Gradle arrivano dai wrapper (`mvnw`, `gradlew`).

I due servizi devono condividere lo **stesso segreto JWT**. Genera una chiave casuale (PowerShell):

```powershell
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

**policy-service** (porta 8080):

```powershell
cd policy-service
$env:JWT_SECRET = "<la chiave>"
.\mvnw.cmd spring-boot:run
```

**claims-service** (porta 8081), in un altro terminale:

```powershell
cd claims-service
$env:JWT_SECRET = "<la stessa chiave>"
.\gradlew.bat bootRun
```

Il segreto non va mai scritto nel codice né committato.

### Utenti demo (solo per pratica)

| Utente | Password | Ruolo |
|---|---|---|
| `agent` | `agent123` | AGENT |
| `admin` | `admin123` | ADMIN |

Gli utenti sono in memoria. In un sistema reale starebbero in un database o in un identity provider.

## API

Tutte le chiamate a `/api/**` richiedono il token (`Authorization: Bearer ...`). Su policy-service serve anche l'header `X-Client-App`.

### policy-service (8080)

| Metodo | Percorso | Ruoli | Descrizione |
|---|---|---|---|
| POST | `/auth/login` | pubblico | emette il JWT |
| GET | `/api/policies` | AGENT, ADMIN | ricerca con filtri, ordinamento e paginazione |
| GET | `/api/policies/{id}` | AGENT, ADMIN | dettaglio |
| GET | `/api/policies/stats` | AGENT, ADMIN | statistiche per tipo e stato |
| POST | `/api/policies` | ADMIN | crea una polizza |
| GET | `/swagger-ui.html` | pubblico | documentazione OpenAPI |

Filtri di ricerca: `type`, `status`, `minPremium`, `maxPremium`, `holderName`, `startFrom`, `startTo`, più `page`, `size` (max 100), `sortBy`, `desc`.

### claims-service (8081)

| Metodo | Percorso | Ruoli | Descrizione |
|---|---|---|---|
| GET | `/api/claims`, `/api/claims/{id}` | AGENT, ADMIN | elenco e dettaglio |
| POST | `/api/claims` | AGENT, ADMIN | apre un sinistro (la polizza deve essere ACTIVE) |
| PATCH | `/api/claims/{id}/status` | ADMIN | cambia stato |
| GET | `/api/claims/{id}/history` | AGENT, ADMIN | storico eventi |
| POST | `/api/claims/{id}/documents` | AGENT, ADMIN | carica un documento (PDF, PNG, JPEG, max 5 MB) |
| GET | `/api/claims/{id}/documents` | AGENT, ADMIN | elenco documenti |
| GET | `/api/claims/{id}/documents/{docId}` | AGENT, ADMIN | scarica un documento |

Macchina a stati dei sinistri: `OPEN → IN_REVIEW → APPROVED | REJECTED`. `APPROVED` e `REJECTED` sono finali.

### Formato degli errori

```json
{ "timestamp": "...", "status": 404, "error": "Not Found", "message": "Claim not found: ..." }
```

Codici principali: 400 validazione, 401 non autenticato, 403 non autorizzato, 404 non trovato, 409 conflitto (duplicato o transizione non valida), 413 file troppo grande, 422 polizza non valida per il sinistro, 502 policy-service non raggiungibile.

## Scelte di sicurezza

- Le regole di accesso terminano con `denyAll`: ciò che non è permesso esplicitamente è vietato.
- Il JWT è firmato; il segreto arriva da variabile d'ambiente.
- Messaggio identico per utente inesistente e password sbagliata.
- `X-Request-Id` accettato solo in formato sicuro (contro la log injection) e propagato tra i servizi.
- L'ordinamento della ricerca usa una whitelist di colonne (contro la SQL injection); i parametri sono sempre bind.
- Upload: whitelist dei tipi, limite di dimensione, chiave di archiviazione generata dal server, nome file ripulito, `Content-Disposition: attachment` e `nosniff` in download.
- Un documento si scarica solo passando il sinistro a cui appartiene.

## Test

```powershell
cd policy-service ; .\mvnw.cmd test          # report copertura: target/site/jacoco/index.html
cd claims-service ; .\gradlew.bat test       # report copertura: build/reports/jacoco/test/html/index.html
```

| Livello | Strumenti |
|---|---|
| Unit test dei service | JUnit 5, Mockito, StepVerifier |
| Repository su database | `@SpringBootTest` con H2 |
| Client HTTP verso policy-service | MockWebServer (200, 404, 401, 500, timeout, server spento, header propagati) |
| API e sicurezza | MockMvc (policy-service), WebTestClient (claims-service) |

## Cosa è simulato

| Componente | Oggi | Sostituto previsto | Dove cambia |
|---|---|---|---|
| OracleDB | H2 in modalità Oracle | OracleDB in container | solo configurazione del datasource |
| S3 (documenti) | cartella locale (`LocalDocumentStore`) | `S3DocumentStore` su LocalStack | nuovo adapter di `DocumentStore` |
| DynamoDB (storico) | mappa in memoria | adapter DynamoDB su LocalStack | nuovo adapter di `ClaimHistoryStore` |
| SQS (notifiche) | log (`LoggingNotificationPublisher`) | adapter SQS su LocalStack | nuovo adapter di `NotificationPublisher` |
| Metadati dei sinistri e dei documenti | mappe in memoria | DynamoDB o database | nuovi adapter delle porte repository |
| SonarQube, Nexus IQ | non presenti | SonarQube e OWASP Dependency-Check in container | pipeline CI |