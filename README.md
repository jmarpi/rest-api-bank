# REST API Bank — Países del Banco Mundial

Microservicio que importa información de países desde la API pública del
[Banco Mundial](https://api.worldbank.org/v2/country/CO?format=json), la almacena en PostgreSQL y
la expone mediante una API REST propia protegida con token Bearer.

- **Java 25 · Spring Boot 4 · Maven · PostgreSQL 17**
- Arquitectura **hexagonal** (puertos y adaptadores) y enfoque **API First**: el contrato
  [`openapi.yaml`](src/main/resources/static/openapi.yaml) genera las interfaces y DTOs REST.
- **Resiliente**: las consultas solo leen la base de datos; si el Banco Mundial falla, el sistema
  sigue funcionando.

## Arquitectura

```text
com.restapibank
├── domain           Country, CountryCode y excepciones de negocio (sin frameworks)
├── application
│   ├── port.in      ImportCountryUseCase, QueryCountriesUseCase
│   ├── port.out     CountryProviderPort (Banco Mundial), CountryRepositoryPort (BD)
│   └── service      ImportCountryService, QueryCountriesService
└── infrastructure
    ├── adapter.in.rest           CountryController (implementa la interfaz generada), errores
    ├── adapter.out.worldbank     Cliente HTTP del Banco Mundial
    ├── adapter.out.persistence   JPA / PostgreSQL
    ├── security                  Filtro Bearer
    └── config                    Wiring de beans
```

El código generado desde el contrato vive en `target/generated-sources/openapi` y no se edita.

## Despliegue

**Requisitos**: Docker y Docker Compose.

```bash
docker compose --env-file .env.dev up --build -d
```

Levanta dos servicios: `postgres` (con volumen persistente) y `app` en `http://localhost:8080`.
La aplicación espera a que la base de datos esté saludable y crea la tabla al arrancar.

```bash
docker compose logs -f app     # ver logs
docker compose down            # detener (agregar -v para borrar los datos)
```

### Variables de `.env.dev`

| Variable | Descripción | Valor por defecto |
|----------|-------------|-------------------|
| `API_BEARER_TOKEN` | Token aceptado en `Authorization: Bearer <token>`. **Obligatorio**: sin él la app no arranca | — |
| `WORLD_BANK_BASE_URL` | URL base de la API del Banco Mundial | `https://api.worldbank.org/v2` |
| `WORLD_BANK_TIMEOUT_SECONDS` | Tiempo máximo de conexión y lectura al Banco Mundial | `10` |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Base de datos y credenciales | `countries` |
| `DB_HOST` / `DB_PORT` | Conexión de la app a la BD | `postgres` / `5432` |
| `SERVER_PORT` | Puerto publicado de la app | `8080` |

> `.env.dev` es solo para desarrollo. Cambie el token y las credenciales en otros entornos.

## Swagger

- Swagger UI: <http://localhost:8080/swagger-ui/index.html>
- Contrato OpenAPI: <http://localhost:8080/openapi.yaml>

La documentación es pública. Para probar las operaciones use **Authorize** e ingrese el valor de
`API_BEARER_TOKEN`.

## API REST

Todas las operaciones requieren el encabezado `Authorization: Bearer <API_BEARER_TOKEN>`.

```bash
export TOKEN=$(grep ^API_BEARER_TOKEN .env.dev | cut -d= -f2)
export API=http://localhost:8080
```

### `POST /api/countries/import?code={code}`

Importa un país desde el Banco Mundial. `code` es un código ISO alpha-2 o alpha-3 (2 o 3 letras,
sin distinguir mayúsculas).

```bash
curl -i -X POST -H "Authorization: Bearer $TOKEN" "$API/api/countries/import?code=CO"
```

```json
{
  "id": 1,
  "alpha2Code": "CO",
  "alpha3Code": "COL",
  "name": "Colombia",
  "capital": "Bogota",
  "region": "Latin America & Caribbean",
  "incomeLevel": "Upper middle income",
  "latitude": 4.60987,
  "longitude": -74.082
}
```

| Código | Cuándo |
|--------|--------|
| `201 Created` | El país no existía y se almacenó |
| `200 OK` | El país ya existía y se actualizó. Si el Banco Mundial falla, se devuelven los datos previos almacenados |
| `400 Bad Request` | `code` ausente, vacío, con longitud distinta de 2–3 o con caracteres no alfabéticos |
| `403 Forbidden` | Token ausente o inválido |
| `404 Not Found` | El Banco Mundial no reconoce el código (y no estaba almacenado) |
| `502 Bad Gateway` | El Banco Mundial no responde, excede el tiempo de espera o responde algo inesperado (y el país no estaba almacenado) |

El código ISO alpha-2 es el identificador de negocio: importar `CO` y luego `COL` no duplica el
registro ni cambia su `id`. Los campos que el Banco Mundial no informa se guardan como `null`.

### `GET /api/countries`

Lista todos los países almacenados, ordenados por alpha-2. Devuelve `[]` si no hay registros.

```bash
curl -i -H "Authorization: Bearer $TOKEN" "$API/api/countries"
```

| Código | Cuándo |
|--------|--------|
| `200 OK` | Siempre que el token sea válido (arreglo JSON, posiblemente vacío) |
| `403 Forbidden` | Token ausente o inválido |

### `GET /api/countries/{id}`

Obtiene un país almacenado por su **identificador numérico** (`{id}`), el campo `id` que
devuelven la importación y el listado.

```bash
curl -i -H "Authorization: Bearer $TOKEN" "$API/api/countries/1"
```

| Código | Cuándo |
|--------|--------|
| `200 OK` | País encontrado |
| `400 Bad Request` | `{id}` no es un entero positivo (p. ej. `CO`, `abc`, `0`) |
| `403 Forbidden` | Token ausente o inválido |
| `404 Not Found` | No hay un país almacenado con ese `id` |

### Formato de error

Todos los errores (incluidos `405 Method Not Allowed` y `500 Internal Server Error`) usan el mismo
cuerpo:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "No se encontró el país con id '999'.",
  "timestamp": "2026-10-29T23:30:00.000Z",
  "path": "/api/countries/999"
}
```

## Comportamiento ante fallas del Banco Mundial

- `GET /api/countries` y `GET /api/countries/{id}` nunca llaman al Banco Mundial.
- Reimportar un país existente con el Banco Mundial caído responde `200` con los datos previos;
  la falla queda registrada en los logs (WARN).
- Importar un país nuevo con el Banco Mundial caído responde `502`.
- La aplicación arranca aunque el Banco Mundial no esté disponible.

## Agregaciones del Banco Mundial

El Banco Mundial también devuelve **agregaciones** (p. ej. `LCN`, `EUU`, `WLD`); se almacenan como
cualquier país (con capital y coordenadas en `null`) y se consultan por su `id` como el resto.

## Pruebas

Requieren Docker (Testcontainers levanta PostgreSQL).

```bash
./mvnw verify
```

Sin Java 25 local:

```bash
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "$PWD":/app -w /app \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal --add-host=host.docker.internal:host-gateway \
  maven:3.9-eclipse-temurin-25 mvn -B verify
```
