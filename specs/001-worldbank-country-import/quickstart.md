# Quickstart / Validación: Importación y Consulta de Países

Guía para comprobar de punta a punta que la feature cumple la spec. Contrato:
[contracts/countries-api.yaml](contracts/countries-api.yaml). Modelo: [data-model.md](data-model.md).

## Prerrequisitos

- Docker y Docker Compose.
- `curl` (y opcionalmente `jq`).

## 1. Configurar y levantar

```bash
# .env.dev (valores de ejemplo)
API_BEARER_TOKEN=dev-secret-token
WORLD_BANK_BASE_URL=https://api.worldbank.org/v2
WORLD_BANK_TIMEOUT_SECONDS=10
POSTGRES_DB=countries
POSTGRES_USER=countries
POSTGRES_PASSWORD=countries
SERVER_PORT=8080

docker compose --env-file .env.dev up --build -d
```

Esperado: servicios `app` y `postgres` en estado healthy/running.

```bash
export TOKEN=dev-secret-token
export API=http://localhost:8080
```

## 2. Escenarios

| # | Comando | Esperado | Spec |
|---|---------|----------|------|
| 1 | `curl -i $API/api/countries` | 403 + ErrorResponse | US4, FR-015 |
| 2 | `curl -i -H "Authorization: Bearer x" $API/api/countries` | 403 | US4 |
| 3 | `curl -i -H "Authorization: Bearer $TOKEN" $API/api/countries` (BD vacía) | 200 `[]` | FR-011 |
| 4 | `curl -i -X POST -H "Authorization: Bearer $TOKEN" "$API/api/countries/import?code=CO"` | 201, Colombia con los 8 campos | US1-1 |
| 5 | Repetir #4 con `code=col` | 200, mismo país; listado sigue con 1 elemento | US1-2, SC-002 |
| 6 | `POST .../import?code=C1`, `code=COLO`, `code=` y sin `code` | 400 | US1-5, FR-002 |
| 7 | `POST .../import?code=XX` | 404 | US1-4, FR-009 |
| 8 | `POST .../import?code=LCN` | 201, alpha-2 `ZJ`, capital/latitud/longitud `null` | US1-3 |
| 9 | `GET $API/api/countries/{id}` con el `id` devuelto en #4 | 200 Colombia | US3-1 |
| 10 | `GET $API/api/countries/999` | 404 | US3-2 |
| 11 | `GET $API/api/countries/CO` y `/abc` y `/0` | 400 | US3-3 |
| 12 | `DELETE $API/api/countries` | 405 + ErrorResponse | FR-019 |

## 3. Resiliencia (Banco Mundial caído)

```bash
# Apuntar a una URL inaccesible y reiniciar solo la app
WORLD_BANK_BASE_URL=http://10.255.255.1 WORLD_BANK_TIMEOUT_SECONDS=2 \
  docker compose --env-file .env.dev up -d app
```

| # | Comando | Esperado | Spec |
|---|---------|----------|------|
| 13 | `GET $API/api/countries` | 200 con los datos previos | SC-003 |
| 14 | `POST .../import?code=CO` (ya existe) | 200 con datos previos; log WARN | FR-004 |
| 15 | `POST .../import?code=EC` (no existe) | 502 tras ~2 s | FR-010 |

## 4. Documentación

- Swagger UI: `http://localhost:8080/swagger-ui/index.html` (sin token; usar "Authorize" para
  probar las operaciones).

## 5. Pruebas automatizadas

```bash
./mvnw verify     # requiere Docker para Testcontainers
```

Esperado: todas las pruebas unitarias, de adaptador e integración en verde.
