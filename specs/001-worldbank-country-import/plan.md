# Implementation Plan: Importación y Consulta de Países desde el Banco Mundial

**Branch**: `001-worldbank-country-import` | **Date**: 2026-10-29 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-worldbank-country-import/spec.md`

## Summary

Microservicio REST que importa países desde la API pública del Banco Mundial
(`GET {base}/country/{code}?format=json`), los persiste en PostgreSQL con el alpha-2 como
identidad de negocio y los expone mediante `POST /api/countries/import`, `GET /api/countries` y
`GET /api/countries/{id}` (id numérico asignado por el sistema). Arquitectura hexagonal mínima: dominio puro, dos casos
de uso, dos puertos de salida (proveedor externo y repositorio). Contrato OpenAPI primero con
interfaces/DTOs generados en `target/`. Resiliencia: las consultas solo leen la BD y la
reimportación de un país existente devuelve los datos previos si el Banco Mundial falla.
Seguridad por un filtro Bearer estático. Despliegue con Dockerfile multi-stage + Docker Compose
configurado por `.env.dev`.

## Technical Context

**Language/Version**: Java 25

**Primary Dependencies**: Spring Boot 4.x (Spring Web, Spring Data JPA, Jakarta Validation),
driver PostgreSQL, springdoc-openapi 3.x (solo Swagger UI), openapi-generator-maven-plugin 7.x
(build-time)

**Storage**: PostgreSQL 17 (contenedor `postgres:17-alpine`)

**Testing**: JUnit 5, Mockito, AssertJ, MockMvc, `MockRestServiceServer`, Testcontainers
(PostgreSQL)

**Target Platform**: Linux, contenedores Docker (Docker Compose)

**Project Type**: web-service (microservicio REST, proyecto único)

**Performance Goals**: importación < 5 s con el Banco Mundial en condiciones normales (SC-001);
consultas servidas desde BD

**Constraints**: timeout al Banco Mundial 10 s configurable; consultas independientes del Banco
Mundial; sin dependencias de runtime fuera de las justificadas

**Scale/Scope**: ~300 registros como máximo (países + agregaciones del Banco Mundial); 3
endpoints; 1 tabla

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cumplimiento en el diseño | Estado |
|-----------|---------------------------|--------|
| I. Propósito de negocio | Importa del Banco Mundial a PostgreSQL; las consultas solo leen BD (FR-013) | ✅ |
| II. Hexagonal simple | `domain` (sin frameworks), `application` (2 casos de uso, 4 puertos de 1–3 métodos, sin anotaciones Spring), `infrastructure` (adaptadores + wiring en `@Configuration`) | ✅ |
| III. API First | `contracts/countries-api.yaml` → `src/main/resources/static/openapi.yaml`; generación en `target/generated-sources/openapi`; DTOs generados solo en el adaptador REST | ✅ |
| IV. Resiliencia | Timeouts configurables; consultas sin llamada externa; fallback a datos previos; arranque sin dependencia del Banco Mundial; 502 solo para país nuevo | ✅ |
| V. Errores HTTP | `@RestControllerAdvice` único + `ErrorResponse` uniforme; 400/403/404/405/500/502 (research R11) | ✅ |
| VI. Bearer (NON-NEGOTIABLE) | `OncePerRequestFilter` sobre `/api/*`, token desde `API_BEARER_TOKEN`, 403, sin Spring Security | ✅ |
| VII. Estricto a spec.md | Solo los 3 endpoints y entregables de la spec; el hallazgo de abajo se reporta, no se "corrige" | ✅ (ver hallazgo) |
| Stack | Java 25, Boot 4, Maven, PostgreSQL; dependencia extra (springdoc) justificada en Complexity Tracking | ✅ |
| Flujo / calidad | Pruebas por endpoint (éxito, 403, 400/404) y prueba de resiliencia (research R12); `docker compose up` sin pasos manuales | ✅ |

**Resultado pre-diseño**: PASS. **Resultado post-diseño** (tras data-model y contrato): PASS.

### ✅ Hallazgo para la spec (Principio VII) — resuelto por la enmienda del 2026-10-29

`{id}` pasó a ser el identificador numérico, por lo que las agregaciones con alpha-2
alfanumérico ya pueden consultarse. Texto original:

Algunas agregaciones del Banco Mundial tienen un `iso2Code` con dígitos (p. ej. `WLD` → `1W`,
`EAS` → `Z4`). La spec permite guardarlas (clarificación 2026-10-29), pero FR-012 exige que
`{id}` tenga exactamente 2 **letras**, así que esos registros aparecerán en `GET /api/countries`
y **no** podrán consultarse con `GET /api/countries/{id}` (responderá 400). El plan implementa la
spec tal cual. Si se desea otra cosa, actualizar `spec.md` (p. ej. permitir `[A-Za-z0-9]{2}` en
FR-012 o rechazar agregaciones) antes de `/speckit-tasks`.

## Project Structure

### Documentation (this feature)

```text
specs/001-worldbank-country-import/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── countries-api.yaml
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks (no creado aquí)
```

### Source Code (repository root)

```text
pom.xml
mvnw, mvnw.cmd, .mvn/wrapper/
Dockerfile
docker-compose.yml
.env.dev
.dockerignore
README.md

src/main/java/com/restapibank/
├── RestApiBankApplication.java
├── domain/
│   ├── model/
│   │   ├── Country.java                 # record
│   │   └── CountryCode.java             # value object: normaliza y valida
│   └── exception/
│       ├── InvalidCountryCodeException.java
│       ├── CountryNotFoundException.java
│       └── ExternalServiceException.java
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── ImportCountryUseCase.java      # ImportResult importCountry(String code)
│   │   │   ├── ImportResult.java              # record(Country, boolean created)
│   │   │   └── QueryCountriesUseCase.java     # findAll(), findByAlpha2(String)
│   │   └── out/
│   │       ├── CountryProviderPort.java       # Optional<Country> fetchByCode(CountryCode)
│   │       └── CountryRepositoryPort.java     # findByAlpha2/findByAlpha3/findAll/save
│   └── service/
│       ├── ImportCountryService.java
│       └── QueryCountriesService.java
└── infrastructure/
    ├── config/
    │   └── ApplicationConfig.java            # beans de servicios, RestClient, filtro
    ├── security/
    │   └── BearerTokenFilter.java
    └── adapter/
        ├── in/rest/
        │   ├── CountryController.java        # implements generated CountriesApi
        │   ├── CountryRestMapper.java
        │   └── GlobalExceptionHandler.java
        └── out/
            ├── worldbank/
            │   ├── WorldBankProperties.java  # baseUrl, timeoutSeconds
            │   └── WorldBankCountryAdapter.java
            └── persistence/
                ├── CountryEntity.java
                ├── CountryJpaRepository.java
                └── CountryPersistenceAdapter.java

src/main/resources/
├── application.yml
├── schema.sql
└── static/openapi.yaml                       # contrato (fuente de la generación)

target/generated-sources/openapi/             # generado, no versionado
└── com/restapibank/infrastructure/adapter/in/rest/{api,model}/

src/test/java/com/restapibank/
├── domain/model/CountryCodeTest.java
├── application/service/ImportCountryServiceTest.java
├── application/service/QueryCountriesServiceTest.java
└── infrastructure/
    ├── config/ApplicationConfigTest.java
    ├── adapter/out/worldbank/WorldBankCountryAdapterTest.java
    └── adapter/in/rest/
        ├── AbstractIntegrationTest.java          # Testcontainers + MockMvc base
        ├── ImportCountryIntegrationTest.java
        ├── SecurityIntegrationTest.java
        ├── ListCountriesIntegrationTest.java
        ├── GetCountryIntegrationTest.java
        └── ErrorHandlingIntegrationTest.java
```

**Structure Decision**: proyecto Maven único con paquetes por capa hexagonal. Sin módulos Maven
separados: la separación por paquetes y la regla "domain no importa frameworks" bastan para este
alcance (Principio II, sin sobreingeniería).

### Configuración (`.env.dev` → variables de entorno)

| Variable | Uso | Default |
|----------|-----|---------|
| `API_BEARER_TOKEN` | token Bearer aceptado (obligatorio; sin él la app no arranca) | — |
| `WORLD_BANK_BASE_URL` | URL base del Banco Mundial | `https://api.worldbank.org/v2` |
| `WORLD_BANK_TIMEOUT_SECONDS` | timeout de conexión y lectura | `10` |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | credenciales (compartidas por app y contenedor de BD) | — |
| `DB_HOST`, `DB_PORT` | conexión de la app | `postgres`, `5432` |
| `SERVER_PORT` | puerto publicado de la app | `8080` |

Docker Compose: servicio `postgres` con healthcheck (`pg_isready`) y volumen; servicio `app`
con `depends_on: condition: service_healthy` y `env_file: .env.dev`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Dependencia de runtime `springdoc-openapi-starter-webmvc-ui` (fuera de la lista de la constitución) | FR-023 exige entregar Swagger de consumo | Webjar de swagger-ui + HTML propio requiere más código manual; generar el spec desde anotaciones contradice API First |
| Plugin `openapi-generator-maven-plugin` (build-time) | Principio III exige generación desde el contrato | Ya contemplado explícitamente por la constitución; se lista por trazabilidad |
| Testcontainers (solo test) | Pruebas de integración contra PostgreSQL real | H2 tendría diferencias de dialecto y también sería una dependencia extra |
