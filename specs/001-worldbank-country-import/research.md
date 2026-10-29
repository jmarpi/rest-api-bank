# Research: Importación y Consulta de Países desde el Banco Mundial

**Feature**: `001-worldbank-country-import` | **Date**: 2026-10-29

## R1. Herramienta de build

- **Decision**: Maven con Maven Wrapper (`mvnw`) versionado en el repositorio.
- **Rationale**: la constitución permite Maven o Gradle. El entorno local no tiene ninguno
  instalado; el wrapper y la imagen Docker de build eliminan esa dependencia. El plugin
  `openapi-generator-maven-plugin` es el camino más directo para la generación aislada.
- **Alternatives considered**: Gradle (Kotlin DSL) — igual de válido, pero agrega un lenguaje de
  configuración más y no aporta nada a este alcance.

## R2. Versiones de plataforma

- **Decision**: Java 25 (LTS) y Spring Boot 4.x (última versión estable 4.x disponible al
  implementar, fijada en `pom.xml`). Imagen de build `maven:3.9-eclipse-temurin-25`, imagen de
  runtime `eclipse-temurin:25-jre`.
- **Rationale**: exigido por la constitución. El JDK local es 27; el build oficial se hace en
  Docker con Java 25 para garantizar reproducibilidad (`maven.compiler.release=25`).
- **Alternatives considered**: ninguna (restricción constitucional).

## R3. API First y generación aislada

- **Decision**: contrato OpenAPI 3.0 en `src/main/resources/static/openapi.yaml` (copia del
  aprobado en `contracts/countries-api.yaml`). `openapi-generator-maven-plugin` (generator
  `spring`) genera en `target/generated-sources/openapi` con:
  `interfaceOnly=true`, `useSpringBoot3=true` (anotaciones `jakarta.*`, compatibles con Boot 4),
  `useBeanValidation=true`, `useTags=true`, `openApiNullable=false`,
  `documentationProvider=none`, `annotationLibrary=none`, `skipDefaultInterface=false`
  (los métodos por defecto generados responden 501 hasta que el controlador los implementa, lo
  que permite entregar los endpoints historia por historia).
  Paquetes generados: `com.restapibank.infrastructure.adapter.in.rest.api` y `...rest.model`.
- **Rationale**: `annotationLibrary=none` y `openApiNullable=false` evitan dependencias extra
  (`swagger-annotations`, `jackson-databind-nullable`). El código generado vive solo en `target/`,
  nunca se edita ni se versiona (Principio III).
- **Riesgo**: compatibilidad del generador con Spring Boot 4 / Jackson 3. Mitigación: usar la
  última versión 7.x del plugin; los DTOs generados solo usan anotaciones `com.fasterxml.jackson.
  annotation.*`, que Jackson 3 sigue soportando. Si el generador ofrece `useSpringBoot4`, usarlo.

## R4. Swagger UI

- **Decision**: `springdoc-openapi-starter-webmvc-ui` (línea 3.x, compatible con Boot 4) solo
  como visor: `springdoc.api-docs.enabled=false` y `springdoc.swagger-ui.url=/openapi.yaml`.
- **Rationale**: FR-023 exige Swagger. Se sirve el mismo contrato que genera el código, así que
  la documentación nunca diverge de la implementación.
- **Alternatives considered**: webjar `swagger-ui` + página HTML propia (más archivos manuales);
  generar el spec desde anotaciones (contradice API First).

## R5. Cliente del Banco Mundial

- **Decision**: `RestClient` (incluido en Spring Web) con `JdkClientHttpRequestFactory`,
  timeout de conexión y lectura = `WORLD_BANK_TIMEOUT_SECONDS` (default 10). La respuesta se lee
  como árbol JSON (`JsonNode`) porque es un arreglo heterogéneo: `[ {paginación}, [países] ]` o
  `[ { "message": [...] } ]`.
- **Interpretación** (adaptador → resultado del puerto):
  - Arreglo con 2 elementos y el segundo es arreglo no vacío → país encontrado (primer elemento).
  - Primer elemento con `message`, o segundo elemento vacío/null → país no encontrado.
  - Error de red, timeout, HTTP ≠ 2xx, JSON inválido, estructura desconocida o `iso2Code`
    vacío → falla de servicio externo.
- **Rationale**: sin dependencias nuevas; el adaptador encapsula toda la rareza del formato.
- **Alternatives considered**: DTOs tipados con deserializador custom (más código para un
  formato inestable); OpenFeign (dependencia extra).

## R6. Mapeo y normalización

- **Decision**: alpha-2 ← `iso2Code`, alpha-3 ← `id`, nombre ← `name`, capital ←
  `capitalCity`, región ← `region.value`, ingresos ← `incomeLevel.value`, latitud/longitud ←
  `latitude`/`longitude` parseados a `BigDecimal`. Todo texto se recorta (`strip`); texto vacío
  o no parseable → `null`. Códigos en mayúsculas.
- **Rationale**: FR-006, FR-007 y casos borde (espacio final en `"Latin America & Caribbean "`).

## R7. Flujo de importación (FR-003, FR-004, FR-005)

- **Decision**:
  1. Normalizar y validar código (dominio `CountryCode`).
  2. Buscar existente: alpha-2 si longitud 2, alpha-3 si longitud 3.
  3. Consultar Banco Mundial.
  4. Si falla o "no encontrado": existente → devolver existente (200, log WARN);
     no existente → 404 (no encontrado) o 502 (falla).
  5. Si éxito: upsert por `iso2Code` de la respuesta → `created=true` (201) o `false` (200).
- **Rationale**: aplica literalmente las clarificaciones del 2026-10-29. El upsert usa el alpha-2
  de la respuesta para que `CO` y `COL` nunca dupliquen (FR-008, SC-002).

## R8. Concurrencia (pendiente de clarify, resuelto aquí)

- **Decision**: restricción `UNIQUE` en `alpha2` en base de datos. Si el `INSERT` falla por
  violación de unicidad (dos importaciones simultáneas del mismo país nuevo), se reintenta una vez
  como actualización y se responde 200.
- **Rationale**: la garantía la da la base de datos, sin locks ni infraestructura extra.

## R9. Persistencia y esquema

- **Decision**: Spring Data JPA + driver PostgreSQL. Esquema creado con `schema.sql`
  (`CREATE TABLE IF NOT EXISTS`, `spring.sql.init.mode=always`) y
  `spring.jpa.hibernate.ddl-auto=validate`.
- **Rationale**: esquema explícito y versionado sin agregar Flyway/Liquibase (sin sobreingeniería).
- **Alternatives considered**: Flyway (dependencia extra para una sola tabla);
  `ddl-auto=update` (esquema implícito, no recomendado).

## R10. Seguridad Bearer

- **Decision**: `OncePerRequestFilter` propio registrado para `/api/*`. Compara el token con
  `MessageDigest.isEqual` (tiempo constante). Ausente/inválido → 403 con el `ErrorResponse`
  uniforme escrito por el propio filtro. Swagger (`/swagger-ui/**`, `/openapi.yaml`) es público.
  La aplicación falla al arrancar si `API_BEARER_TOKEN` está vacío.
- **Rationale**: Principio VI pide la solución más simple; Spring Security no es requerido.
  Fail-fast evita desplegar una API abierta por error de configuración.

## R11. Manejo de errores

- **Decision**: `@RestControllerAdvice` único que mapea:

  | Excepción | HTTP |
  |-----------|------|
  | `InvalidCountryCodeException` (dominio), `HandlerMethodValidationException`, `ConstraintViolationException`, `MissingServletRequestParameterException`, `MethodArgumentTypeMismatchException` | 400 |
  | `CountryNotFoundException` (dominio), `NoResourceFoundException` | 404 |
  | `HttpRequestMethodNotSupportedException` | 405 |
  | `ExternalServiceException` (dominio) | 502 |
  | `Exception` (cualquier otra) | 500 + log ERROR |

  Cuerpo: `ErrorResponse { status, error, message, timestamp, path }` (DTO generado).
- **Rationale**: FR-017–FR-019; ninguna excepción sin manejar.

## R12. Pruebas

- **Decision**: JUnit 5 + Mockito + AssertJ (`spring-boot-starter-test`); Testcontainers
  PostgreSQL para integración; `MockRestServiceServer` para el adaptador del Banco Mundial.
  - Unit: `CountryCode`, `ImportCountryService` (todas las ramas de R7, con puertos mockeados).
  - Adaptador WB: éxito, `message`, lista vacía, HTTP 500, timeout/IO, JSON inválido.
  - Integración (MockMvc + Testcontainers + `@MockitoBean` del puerto WB): cada endpoint con
    200/201, 403 sin token, 400 y 404; resiliencia con WB caído (Principio IV).
- **Rationale**: dependencias solo de test; cumplen las puertas de calidad de la constitución.
