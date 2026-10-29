---

description: "Task list for feature 001-worldbank-country-import"
---

# Tasks: Importación y Consulta de Países desde el Banco Mundial

**Input**: Design documents from `/specs/001-worldbank-country-import/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/countries-api.yaml, quickstart.md

**Tests**: INCLUIDOS. La constitución (Flujo de Desarrollo y Puertas de Calidad) exige por cada
endpoint pruebas de éxito, rechazo sin token (403) y error principal (400/404), más una prueba de
resiliencia con el Banco Mundial caído.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

- Proyecto Maven único en la raíz del repositorio.
- Código: `src/main/java/com/restapibank/` — Pruebas: `src/test/java/com/restapibank/`
- Código generado (no editar, no versionar): `target/generated-sources/openapi/`
- Paquetes generados: `com.restapibank.infrastructure.adapter.in.rest.api` (interfaz
  `CountriesApi`) y `com.restapibank.infrastructure.adapter.in.rest.model` (`Country`,
  `ErrorResponse`). El DTO generado `Country` se referencia por nombre completo donde choque con
  `com.restapibank.domain.model.Country`.

## Reglas globales (aplican a todas las tareas)

- `domain` y `application` MUST NOT importar `org.springframework.*`, `jakarta.*`,
  `tools.jackson.*`/`com.fasterxml.*` ni paquetes generados (Constitución, Principios II y III).
- Construir exclusivamente lo descrito en `spec.md`. Ante contradicción: detenerse y reportar
  (Principio VII).
- Mensajes de error al usuario en español, legibles, sin trazas.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Crear `pom.xml` en la raíz: parent `org.springframework.boot:spring-boot-starter-parent` (última versión estable 4.x), `groupId` `com.restapibank`, `artifactId` `rest-api-bank`, `<java.version>25</java.version>`. Dependencias: `spring-boot-starter-webmvc` (o `spring-boot-starter-web` si es el nombre vigente en la versión elegida), `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `org.postgresql:postgresql` (runtime), `org.springdoc:springdoc-openapi-starter-webmvc-ui` (línea 3.x compatible con Boot 4). Test: `spring-boot-starter-test` (y los starters de test por tecnología de Boot 4 para webmvc y data-jpa si aplican), `spring-boot-testcontainers`, Testcontainers PostgreSQL y JUnit Jupiter. Verificar en Maven Central los nombres de artefacto exactos para Boot 4 antes de fijarlos
- [ ] T002 Generar Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`, Maven 3.9.x) en la raíz; si no hay Maven local, usar `docker run --rm -v "$PWD":/app -w /app maven:3.9-eclipse-temurin-25 mvn -N wrapper:wrapper`
- [ ] T003 [P] Crear `.gitignore` (`target/`, `.idea/`, `*.iml`, `.vscode/`) y `.dockerignore` (`target/`, `.git/`, `specs/`, `.specify/`, `.claude/`) en la raíz
- [ ] T004 [P] Copiar `specs/001-worldbank-country-import/contracts/countries-api.yaml` sin cambios a `src/main/resources/static/openapi.yaml` (fuente de la generación y del Swagger UI)
- [ ] T005 Configurar `org.openapitools:openapi-generator-maven-plugin` (última 7.x) en `pom.xml`: goal `generate`, `inputSpec=${project.basedir}/src/main/resources/static/openapi.yaml`, `generatorName=spring`, `apiPackage=com.restapibank.infrastructure.adapter.in.rest.api`, `modelPackage=com.restapibank.infrastructure.adapter.in.rest.model`, `configOptions`: `interfaceOnly=true`, `useSpringBoot3=true` (o `useSpringBoot4=true` si existe), `useBeanValidation=true`, `useTags=true`, `openApiNullable=false`, `documentationProvider=none`, `annotationLibrary=none`, `skipDefaultInterface=false`, `dateLibrary=java8`; salida en `target/generated-sources/openapi` (research R3). Confirmar que compila y que existe la interfaz `CountriesApi` con `importCountry`, `getCountries`, `getCountryById`
- [ ] T006 Crear `src/main/java/com/restapibank/RestApiBankApplication.java` con `@SpringBootApplication` y `main`
- [ ] T007 Crear `src/main/resources/application.yml`: `server.port: ${SERVER_PORT:8080}`; datasource `jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${POSTGRES_DB}` con `${POSTGRES_USER}`/`${POSTGRES_PASSWORD}`; `spring.jpa.hibernate.ddl-auto: validate`; `spring.jpa.open-in-view: false`; `spring.sql.init.mode: always`; `app.security.bearer-token: ${API_BEARER_TOKEN:}`; `app.world-bank.base-url: ${WORLD_BANK_BASE_URL:https://api.worldbank.org/v2}`; `app.world-bank.timeout-seconds: ${WORLD_BANK_TIMEOUT_SECONDS:10}`; `springdoc.api-docs.enabled: false`; `springdoc.swagger-ui.url: /openapi.yaml`
- [ ] T008 [P] Crear `src/main/resources/schema.sql` con `CREATE TABLE IF NOT EXISTS country` exactamente según data-model.md: `id BIGSERIAL PRIMARY KEY`, `alpha2_code VARCHAR(2) NOT NULL UNIQUE`, `alpha3_code VARCHAR(3)` (nullable), `name VARCHAR(255)`, `capital VARCHAR(255)`, `region VARCHAR(255)`, `income_level VARCHAR(255)` (nullable), `latitude NUMERIC(9,6)`, `longitude NUMERIC(9,6)` (nullable), `created_at TIMESTAMPTZ NOT NULL`, `updated_at TIMESTAMPTZ NOT NULL`; más `CREATE INDEX IF NOT EXISTS idx_country_alpha3 ON country (alpha3_code)`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Dominio, persistencia, manejo de errores, seguridad y esqueleto REST que todas las historias necesitan

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T009 [P] Crear `src/main/java/com/restapibank/domain/model/Country.java` como `record Country(String alpha2Code, String alpha3Code, String name, String capital, String region, String incomeLevel, BigDecimal latitude, BigDecimal longitude)`; constructor compacto: `alpha2Code` obligatorio (no nulo/no vacío, si no `IllegalArgumentException`), códigos en mayúsculas, textos con `strip()` y vacíos → `null` ("textos recortados; vacíos → `null`")
- [ ] T010 [P] Crear `src/main/java/com/restapibank/domain/model/CountryCode.java` (value object, `record CountryCode(String value)`): fábrica `of(String raw)` que aplica `strip()` + mayúsculas y valida "obligatorio, no vacío, `^[A-Z]{2,3}$` tras normalizar"; si falla lanza `InvalidCountryCodeException`; métodos `isAlpha2()` (longitud 2) e `isAlpha3()` (longitud 3)
- [ ] T011 [P] Crear excepciones de dominio (extienden `RuntimeException`, mensaje en español) en `src/main/java/com/restapibank/domain/exception/`: `InvalidCountryCodeException.java`, `CountryNotFoundException.java` (recibe el código), `ExternalServiceException.java` (mensaje + causa)
- [ ] T012 [P] Crear `src/main/java/com/restapibank/application/port/out/CountryRepositoryPort.java`: `Optional<Country> findByAlpha2(String)`, `Optional<Country> findByAlpha3(String)`, `List<Country> findAll()`, `Country save(Country)` (upsert por alpha-2: inserta o actualiza)
- [ ] T013 [P] Crear `src/main/java/com/restapibank/application/port/out/CountryProviderPort.java`: `Optional<Country> fetchByCode(CountryCode code)` — vacío = el Banco Mundial indica que no existe; lanza `ExternalServiceException` ante cualquier falla (data-model.md, "Resultados del puerto")
- [ ] T014 [P] Crear `src/main/java/com/restapibank/infrastructure/adapter/out/persistence/CountryEntity.java` (`@Entity @Table(name="country")`) con columnas de schema.sql: `id` (`@GeneratedValue(strategy=IDENTITY)`), `alpha2Code` (`length=2, nullable=false, unique=true`), `alpha3Code` (`length=3`), `name`, `capital`, `region`, `incomeLevel`, `latitude`/`longitude` (`BigDecimal`, `precision=9, scale=6`), `createdAt`/`updatedAt` (`OffsetDateTime`, `nullable=false`, asignados con `@PrePersist`/`@PreUpdate`)
- [ ] T015 Crear `src/main/java/com/restapibank/infrastructure/adapter/out/persistence/CountryJpaRepository.java` (`JpaRepository<CountryEntity, Long>`) con `findByAlpha2Code(String)`, `findByAlpha3Code(String)` y `findAllByOrderByAlpha2CodeAsc()` (depende de T014)
- [ ] T016 Crear `src/main/java/com/restapibank/infrastructure/adapter/out/persistence/CountryPersistenceAdapter.java` (`@Component`, implementa `CountryRepositoryPort`): mapea entidad ↔ `Country`; `findAll` ordenado por alpha-2; `save` busca por alpha-2, actualiza los 7 campos si existe o inserta si no; si el insert lanza `DataIntegrityViolationException` (carrera por `UNIQUE(alpha2_code)`), reintenta una vez como actualización (research R8). `id`, `createdAt`, `updatedAt` nunca salen del adaptador (depende de T012, T015)
- [ ] T017 [P] Crear `src/main/java/com/restapibank/infrastructure/adapter/in/rest/ErrorResponseFactory.java` (`@Component`): `ErrorResponse create(HttpStatus status, String message, String path)` usando el DTO generado `ErrorResponse` (`status`, `error` = reason phrase, `message`, `timestamp` = `OffsetDateTime.now()`, `path`); y `void write(HttpServletResponse, HttpStatus, String message, String path)` que serializa a JSON con el mapper JSON de Spring (para uso desde filtros)
- [ ] T018 Crear `src/main/java/com/restapibank/infrastructure/adapter/in/rest/GlobalExceptionHandler.java` (`@RestControllerAdvice`) con la tabla de research R11: 400 ← `InvalidCountryCodeException`, `HandlerMethodValidationException`, `ConstraintViolationException`, `MethodArgumentNotValidException`, `MissingServletRequestParameterException`, `MethodArgumentTypeMismatchException`; 404 ← `CountryNotFoundException`, `NoResourceFoundException`; 405 ← `HttpRequestMethodNotSupportedException`; 502 ← `ExternalServiceException` (log WARN); 500 ← `Exception` (log ERROR con stack trace, mensaje genérico "Ocurrió un error interno inesperado."). Todas responden `ResponseEntity<ErrorResponse>` vía `ErrorResponseFactory` (depende de T011, T017)
- [ ] T019 Crear `src/main/java/com/restapibank/infrastructure/security/BearerTokenFilter.java` (`OncePerRequestFilter`, no `@Component`): aplica solo a rutas que empiezan por `/api/`; extrae `Authorization: Bearer <token>`; compara con el token configurado usando `MessageDigest.isEqual` sobre bytes UTF-8; si falta, el esquema no es `Bearer` o no coincide → `ErrorResponseFactory.write(response, FORBIDDEN, "Token de acceso ausente o inválido.", uri)` y no continúa la cadena (depende de T017)
- [ ] T020 Crear `src/main/java/com/restapibank/infrastructure/adapter/in/rest/CountryController.java` (`@RestController`, implementa `CountriesApi` generada) sin sobrescribir aún ningún método (los defaults generados responden 501) (depende de T005)
- [ ] T021 Crear `src/main/java/com/restapibank/infrastructure/config/ApplicationConfig.java` (`@Configuration`): leer `app.security.bearer-token`; si está vacío o en blanco lanzar `IllegalStateException("API_BEARER_TOKEN no está configurado")` al arrancar (research R10); registrar `FilterRegistrationBean<BearerTokenFilter>` con URL pattern `/api/*` y orden alto (depende de T019)
- [ ] T022 [P] Crear `src/test/java/com/restapibank/domain/model/CountryCodeTest.java`: válidos `CO`, `col` → `COL`, ` co ` → `CO`; inválidos (lanza `InvalidCountryCodeException`) `null`, `""`, `"  "`, `C`, `COLO`, `C1`, `1W`, `C-O`; `isAlpha2`/`isAlpha3`
- [ ] T023 [P] Crear `src/test/java/com/restapibank/infrastructure/adapter/in/rest/AbstractIntegrationTest.java`: `@SpringBootTest`, `@AutoConfigureMockMvc`, `@Testcontainers` con `PostgreSQLContainer` (`postgres:17-alpine`) `@ServiceConnection` estático; propiedad `app.security.bearer-token=test-token` (vía `@TestPropertySource`); `@MockitoBean CountryProviderPort`; `@BeforeEach` que vacía la tabla `country` con `JdbcTemplate`; helper `bearer()` que retorna `"Bearer test-token"`

**Checkpoint**: `./mvnw verify` compila (código generado incluido) y `CountryCodeTest` pasa

---

## Phase 3: User Story 1 - Importar un país desde el Banco Mundial (Priority: P1) 🎯 MVP

**Goal**: `POST /api/countries/import?code={code}` crea (201) o actualiza (200) un país desde el Banco Mundial, con fallback a datos previos

**Independent Test**: `POST /api/countries/import?code=CO` con token válido devuelve 201 con Colombia y el registro queda en BD; repetir con `COL` devuelve 200 sin duplicar

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T024 [P] [US1] Crear `src/test/java/com/restapibank/application/service/ImportCountryServiceTest.java` (Mockito, puertos mockeados) cubriendo data-model.md "Transiciones": no existe + WB OK → `created=true`; existe + WB OK → `created=false` con datos nuevos guardados; no existe + WB vacío → `CountryNotFoundException`; no existe + WB `ExternalServiceException` → se propaga; existe + WB vacío → devuelve existente, `created=false`, `save` nunca llamado; existe + WB falla → igual; búsqueda previa por alpha-3 cuando el código tiene 3 letras; código inválido → `InvalidCountryCodeException` sin llamar al proveedor; upsert usa el `alpha2Code` devuelto por WB (importar `COL` cuando existe `CO` → `created=false`)
- [ ] T025 [P] [US1] Crear `src/test/java/com/restapibank/infrastructure/adapter/out/worldbank/WorldBankCountryAdapterTest.java` con `MockRestServiceServer` sobre un `RestClient.Builder`: respuesta exitosa de Colombia de spec.md → `Country` con `CO`, `COL`, `Colombia`, `Bogota`, `Latin America & Caribbean` (recortado), `Upper middle income`, `4.60987`, `-74.082`; agregación con `capitalCity`/`latitude`/`longitude` vacíos → esos campos `null`; latitud no numérica → `null`; respuesta `[{"message":[{"id":"120",...}]}]` → `Optional.empty()`; `[{...}, []]` y `[{...}, null]` → `Optional.empty()`; HTTP 500 → `ExternalServiceException`; JSON inválido → `ExternalServiceException`; objeto sin `iso2Code` → `ExternalServiceException`; URL invocada `{base}/country/CO?format=json`
- [ ] T026 [P] [US1] Crear `src/test/java/com/restapibank/infrastructure/adapter/in/rest/ImportCountryIntegrationTest.java` (extiende `AbstractIntegrationTest`, proveedor mockeado): 201 + cuerpo con 8 campos y `alpha2Code=CO`; segunda importación `code=col` → 200 y `SELECT count(*)` = 1; códigos `C1`, `COLO`, `""`, `"   "` y sin parámetro `code` → 400 con `ErrorResponse` (`status`, `error`, `message`, `timestamp`, `path`); proveedor vacío → 404; proveedor lanza `ExternalServiceException` con país nuevo → 502 y nada guardado; con país ya guardado → 200 con datos previos sin cambios; sin token → 403

### Implementation for User Story 1

- [ ] T027 [P] [US1] Crear `src/main/java/com/restapibank/application/port/in/ImportResult.java` (`record ImportResult(Country country, boolean created)`) y `src/main/java/com/restapibank/application/port/in/ImportCountryUseCase.java` (`ImportResult importCountry(String code)`)
- [ ] T028 [P] [US1] Crear `src/main/java/com/restapibank/infrastructure/adapter/out/worldbank/WorldBankProperties.java` (`@ConfigurationProperties(prefix="app.world-bank")` record con `baseUrl` y `timeoutSeconds` default 10) y habilitarlo con `@EnableConfigurationProperties` en `src/main/java/com/restapibank/infrastructure/config/ApplicationConfig.java`
- [ ] T029 [US1] Crear `src/main/java/com/restapibank/infrastructure/adapter/out/worldbank/WorldBankCountryAdapter.java` (`@Component`, implementa `CountryProviderPort`): `GET {baseUrl}/country/{code}?format=json` con `RestClient`, leer cuerpo como `JsonNode` e interpretar según research R5; mapear según research R6 / data-model.md (alpha-2 ← `iso2Code`, alpha-3 ← `id`, nombre ← `name`, capital ← `capitalCity`, región ← `region.value`, ingresos ← `incomeLevel.value`, latitud/longitud ← `latitude`/`longitude` como `BigDecimal`, vacío o no parseable → `null`); envolver toda `RestClientException`, `IOException`/error de parseo o estructura desconocida en `ExternalServiceException` (depende de T013, T028)
- [ ] T030 [US1] Crear `src/main/java/com/restapibank/application/service/ImportCountryService.java` (sin anotaciones Spring, implementa `ImportCountryUseCase`, recibe `CountryRepositoryPort` y `CountryProviderPort` por constructor) con el flujo de research R7: `CountryCode.of` → buscar existente (alpha-2 si `isAlpha2`, alpha-3 si `isAlpha3`) → `fetchByCode` → si vacío o `ExternalServiceException`: existente presente → log WARN con `System.Logger` y `ImportResult(existente, false)`; ausente → `CountryNotFoundException` o relanzar → si éxito: `created = repository.findByAlpha2(fetched.alpha2Code()).isEmpty()`, `save(fetched)`, retornar `ImportResult(saved, created)` (depende de T010, T011, T012, T013, T027)
- [ ] T031 [P] [US1] Crear `src/main/java/com/restapibank/infrastructure/adapter/in/rest/CountryRestMapper.java`: `toDto(domain Country)` → DTO generado `Country` (copia los 8 campos; `latitude`/`longitude` `BigDecimal` → tipo numérico del DTO) y `toDtoList(List<Country>)`
- [ ] T032 [US1] En `src/main/java/com/restapibank/infrastructure/config/ApplicationConfig.java` declarar `@Bean RestClient worldBankRestClient(RestClient.Builder, WorldBankProperties)` con `JdkClientHttpRequestFactory` (`HttpClient` con `connectTimeout` y `readTimeout` = `timeoutSeconds`) y `baseUrl`, y `@Bean ImportCountryUseCase importCountryUseCase(CountryRepositoryPort, CountryProviderPort)` → `new ImportCountryService(...)`; inyectar el `RestClient` en `WorldBankCountryAdapter` (depende de T029, T030)
- [ ] T033 [US1] En `src/main/java/com/restapibank/infrastructure/adapter/in/rest/CountryController.java` sobrescribir `importCountry(String code)`: delegar en `ImportCountryUseCase`, responder `ResponseEntity.status(created ? 201 : 200).body(mapper.toDto(country))` (depende de T020, T031, T032)

**Checkpoint**: T024–T026 en verde; US1 funcional y demostrable

---

## Phase 4: User Story 4 - Acceso protegido por token (Priority: P1)

**Goal**: Ninguna operación de `/api/**` se ejecuta sin el token Bearer configurado; Swagger permanece público

**Independent Test**: invocar las tres operaciones sin token, con token incorrecto y con el token correcto

### Tests for User Story 4 ⚠️

- [ ] T034 [P] [US4] Crear `src/test/java/com/restapibank/infrastructure/adapter/in/rest/SecurityIntegrationTest.java` (extiende `AbstractIntegrationTest`): para `POST /api/countries/import?code=CO`, `GET /api/countries`, `GET /api/countries/CO`: sin encabezado → 403; `Bearer wrong` → 403; `Basic dGVzdA==` → 403; cuerpo 403 con formato `ErrorResponse` y `Content-Type: application/json`; el proveedor mockeado nunca es invocado en los rechazos; `GET /openapi.yaml` y `GET /swagger-ui/index.html` sin token → 200
- [ ] T035 [P] [US4] Crear `src/test/java/com/restapibank/infrastructure/config/ApplicationConfigTest.java`: con `app.security.bearer-token` vacío o en blanco el contexto no arranca (`IllegalStateException` en la causa), usando `ApplicationContextRunner` con `ApplicationConfig`

### Implementation for User Story 4

- [ ] T036 [US4] Ajustar `src/main/java/com/restapibank/infrastructure/security/BearerTokenFilter.java` y `src/main/java/com/restapibank/infrastructure/config/ApplicationConfig.java` solo si T034/T035 fallan (la implementación base es T019/T021); confirmar que el filtro también cubre rutas `/api/**` inexistentes (403 antes que 404)

**Checkpoint**: US1 + US4 = MVP seguro

---

## Phase 5: User Story 2 - Listar los países almacenados (Priority: P2)

**Goal**: `GET /api/countries` devuelve todos los países almacenados (o `[]`), sin depender del Banco Mundial

**Independent Test**: con datos precargados en BD, `GET /api/countries` devuelve el arreglo; con el proveedor fallando la respuesta es idéntica

### Tests for User Story 2 ⚠️

- [ ] T037 [P] [US2] Crear `src/test/java/com/restapibank/application/service/QueryCountriesServiceTest.java` con casos de `findAll`: lista vacía → `[]`; N países → N, en el orden que entrega el repositorio
- [ ] T038 [P] [US2] Crear `src/test/java/com/restapibank/infrastructure/adapter/in/rest/ListCountriesIntegrationTest.java` (extiende `AbstractIntegrationTest`, datos insertados con `JdbcTemplate`): BD vacía → 200 `[]`; 2 países → 200 con 2 elementos ordenados por `alpha2Code` y campos nulos como `null`; resiliencia: proveedor mockeado lanzando `ExternalServiceException` → misma respuesta 200 y el proveedor nunca es invocado (Principio IV, SC-003); sin token → 403

### Implementation for User Story 2

- [ ] T039 [US2] Crear `src/main/java/com/restapibank/application/port/in/QueryCountriesUseCase.java` con `List<Country> findAll()` y `src/main/java/com/restapibank/application/service/QueryCountriesService.java` (sin anotaciones Spring) que delega en `CountryRepositoryPort.findAll()`
- [ ] T040 [US2] En `src/main/java/com/restapibank/infrastructure/config/ApplicationConfig.java` declarar `@Bean QueryCountriesUseCase queryCountriesUseCase(CountryRepositoryPort)` (depende de T039)
- [ ] T041 [US2] En `src/main/java/com/restapibank/infrastructure/adapter/in/rest/CountryController.java` sobrescribir `getCountries()` → `ResponseEntity.ok(mapper.toDtoList(useCase.findAll()))` (depende de T031, T040)

**Checkpoint**: US1, US4 y US2 funcionan de forma independiente

---

## Phase 6: User Story 3 - Consultar un país por código alpha-2 (Priority: P3)

**Goal**: `GET /api/countries/{id}` con `{id}` = alpha-2 devuelve el país almacenado

**Independent Test**: con Colombia en BD, `GET /api/countries/co` → 200; `ZZ` → 404; `COL` → 400

### Tests for User Story 3 ⚠️

- [ ] T042 [US3] Agregar a `src/test/java/com/restapibank/application/service/QueryCountriesServiceTest.java` casos de `findByAlpha2`: `co` encontrado → país; `ZZ` ausente → `CountryNotFoundException`; `COL` (alpha-3) → `InvalidCountryCodeException`; `C1` → `InvalidCountryCodeException`
- [ ] T043 [P] [US3] Crear `src/test/java/com/restapibank/infrastructure/adapter/in/rest/GetCountryIntegrationTest.java` (extiende `AbstractIntegrationTest`): `/api/countries/CO` y `/api/countries/co` → 200; `/api/countries/ZZ` → 404 con `ErrorResponse`; `/api/countries/COL`, `/api/countries/C1`, `/api/countries/123` → 400; proveedor nunca invocado; sin token → 403

### Implementation for User Story 3

- [ ] T044 [US3] Agregar `Country findByAlpha2(String id)` a `src/main/java/com/restapibank/application/port/in/QueryCountriesUseCase.java` e implementarlo en `src/main/java/com/restapibank/application/service/QueryCountriesService.java`: `CountryCode.of(id)`; si no `isAlpha2()` → `InvalidCountryCodeException`; buscar en repositorio; ausente → `CountryNotFoundException` (depende de T039)
- [ ] T045 [US3] En `src/main/java/com/restapibank/infrastructure/adapter/in/rest/CountryController.java` sobrescribir `getCountryById(String id)` → `ResponseEntity.ok(mapper.toDto(useCase.findByAlpha2(id)))` (depende de T044)

**Checkpoint**: las cuatro historias funcionan de forma independiente

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Errores transversales, despliegue y documentación entregable

- [ ] T046 [P] Crear `src/test/java/com/restapibank/infrastructure/adapter/in/rest/ErrorHandlingIntegrationTest.java` (extiende `AbstractIntegrationTest`): `DELETE /api/countries` con token → 405 con `ErrorResponse`; `GET /api/unknown` con token → 404 con `ErrorResponse`; proveedor mockeado lanzando `RuntimeException` en importación de país nuevo → 500 con mensaje genérico y sin stack trace en el cuerpo (FR-017–FR-019)
- [ ] T047 [P] Crear `Dockerfile` multi-stage en la raíz: etapa `build` `maven:3.9-eclipse-temurin-25` (copiar `pom.xml`, `mvn -B dependency:go-offline`, copiar `src`, `mvn -B -DskipTests package`); etapa runtime `eclipse-temurin:25-jre` con usuario no root, `COPY` del jar, `EXPOSE 8080`, `ENTRYPOINT ["java","-jar","/app/app.jar"]`
- [ ] T048 [P] Crear `docker-compose.yml` en la raíz: servicio `postgres` (`postgres:17-alpine`, `env_file: .env.dev`, volumen `pgdata`, healthcheck `pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}`); servicio `app` (`build: .`, `env_file: .env.dev`, `environment: DB_HOST=postgres`, `ports: "${SERVER_PORT:-8080}:8080"`, `depends_on: postgres: condition: service_healthy`, `restart: unless-stopped`); volumen `pgdata`
- [ ] T049 [P] Crear `.env.dev` en la raíz con: `API_BEARER_TOKEN` (valor de desarrollo aleatorio de ≥32 caracteres), `WORLD_BANK_BASE_URL=https://api.worldbank.org/v2`, `WORLD_BANK_TIMEOUT_SECONDS=10`, `POSTGRES_DB=countries`, `POSTGRES_USER=countries`, `POSTGRES_PASSWORD=countries`, `DB_HOST=postgres`, `DB_PORT=5432`, `SERVER_PORT=8080`, con comentario de cabecera indicando que es solo para desarrollo
- [ ] T050 [P] Crear `README.md` en la raíz (español): descripción; arquitectura hexagonal (capas y puertos); requisitos (Docker); despliegue `docker compose --env-file .env.dev up --build -d`; tabla de variables de `.env.dev`; URL de Swagger `http://localhost:8080/swagger-ui/index.html` y cómo usar "Authorize"; por cada operación (`POST /api/countries/import?code=`, `GET /api/countries`, `GET /api/countries/{id}`) ejemplo `curl` con Bearer, respuesta de ejemplo y códigos posibles; formato `ErrorResponse`; comportamiento ante caída del Banco Mundial (fallback 200 / 502); limitación conocida: agregaciones con alpha-2 alfanumérico (p. ej. `1W`) no se consultan por `{id}`; cómo ejecutar pruebas (`./mvnw verify`, requiere Docker)
- [ ] T051 Verificar que `src/main/java/com/restapibank/domain/` y `src/main/java/com/restapibank/application/` no importan frameworks: `grep -rE "import (org\.springframework|jakarta|tools\.jackson|com\.fasterxml|com\.restapibank\.infrastructure)" src/main/java/com/restapibank/domain src/main/java/com/restapibank/application` debe devolver vacío
- [ ] T052 Ejecutar `./mvnw verify` (o `docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "$PWD":/app -w /app maven:3.9-eclipse-temurin-25 mvn -B verify` si no hay Java 25 local) y dejar todas las pruebas en verde
- [ ] T053 Ejecutar los escenarios 1–15 de `specs/001-worldbank-country-import/quickstart.md` contra `docker compose --env-file .env.dev up --build -d` y registrar cualquier desviación

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias. T005 depende de T001 y T004.
- **Foundational (Phase 2)**: depende de Setup; BLOQUEA todas las historias.
- **US1 (Phase 3)** y **US4 (Phase 4)**: dependen de Foundational. US4 usa los endpoints de US1–US3 en sus pruebas; las rutas aún no implementadas responden 501 tras pasar el filtro, por lo que el 403 se verifica igualmente.
- **US2 (Phase 5)**: depende de Foundational; usa `CountryRestMapper` (T031) de US1.
- **US3 (Phase 6)**: depende de US2 (T039: mismo puerto/servicio de consulta).
- **Polish (Phase 7)**: T046–T050 pueden empezar tras Foundational; T051–T053 al final.

### User Story Dependencies

- **US1 (P1)**: independiente tras Foundational.
- **US4 (P1)**: independiente tras Foundational (el filtro está en T019/T021).
- **US2 (P2)**: independiente funcionalmente; comparte T031 con US1.
- **US3 (P3)**: extiende archivos creados en US2 (T039, T037).

### Within Each User Story

- Pruebas primero; deben fallar antes de implementar.
- Puertos → servicios → adaptadores → wiring → controlador.

### Parallel Opportunities

- Setup: T003, T004, T008 en paralelo.
- Foundational: T009–T014 en paralelo; luego T017, T022, T023 en paralelo.
- US1: T024–T026 en paralelo; T027, T028, T031 en paralelo.
- US4: T034, T035 en paralelo.
- US2: T037, T038 en paralelo.
- Polish: T046–T050 en paralelo.

---

## Parallel Example: User Story 1

```bash
# Pruebas de US1 en paralelo:
Task: "ImportCountryServiceTest en src/test/java/com/restapibank/application/service/ImportCountryServiceTest.java"
Task: "WorldBankCountryAdapterTest en src/test/java/com/restapibank/infrastructure/adapter/out/worldbank/WorldBankCountryAdapterTest.java"
Task: "ImportCountryIntegrationTest en src/test/java/com/restapibank/infrastructure/adapter/in/rest/ImportCountryIntegrationTest.java"

# Piezas independientes de US1 en paralelo:
Task: "ImportResult + ImportCountryUseCase en src/main/java/com/restapibank/application/port/in/"
Task: "WorldBankProperties en src/main/java/com/restapibank/infrastructure/adapter/out/worldbank/WorldBankProperties.java"
Task: "CountryRestMapper en src/main/java/com/restapibank/infrastructure/adapter/in/rest/CountryRestMapper.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 + 4)

1. Phase 1: Setup
2. Phase 2: Foundational
3. Phase 3: US1 (importación)
4. Phase 4: US4 (seguridad verificada)
5. **STOP and VALIDATE**: escenarios 1, 2, 4–8, 14, 15 de quickstart.md

### Incremental Delivery

1. Setup + Foundational → base lista
2. US1 + US4 → MVP seguro
3. US2 → listado con resiliencia verificada
4. US3 → consulta por alpha-2
5. Polish → Docker, `.env.dev`, README, validación completa

---

## Notes

- [P] = archivos distintos, sin dependencias pendientes.
- Hallazgo abierto (plan.md, Principio VII): agregaciones con alpha-2 alfanumérico (`1W`, `Z4`) se almacenan pero `GET /api/countries/{id}` las rechaza con 400. Se implementa la spec tal cual; cambiarla antes de `/speckit-implement` si se desea otro comportamiento.
- No editar `target/generated-sources/`; todo cambio de API empieza en `contracts/countries-api.yaml` y se copia a `src/main/resources/static/openapi.yaml`.
