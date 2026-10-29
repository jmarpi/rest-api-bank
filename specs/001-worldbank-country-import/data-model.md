# Data Model: Importación y Consulta de Países

**Feature**: `001-worldbank-country-import` | **Date**: 2026-10-29

## Dominio

### `CountryCode` (value object)

Código recibido en la importación (`POST /api/countries/import?code=`).

| Regla | Detalle |
|-------|---------|
| Normalización | `strip()` + mayúsculas |
| Validación | obligatorio, no vacío, `^[A-Z]{2,3}$` tras normalizar → si no, `InvalidCountryCodeException` |
| Tipo | `isAlpha2()` (longitud 2) / `isAlpha3()` (longitud 3) |

`GET /api/countries/{id}` no usa `CountryCode`: `{id}` es el identificador numérico (FR-012).

### `Country` (entidad de dominio, record inmutable)

| Campo | Tipo | Obligatorio | Origen Banco Mundial |
|-------|------|-------------|----------------------|
| `id` | Long | No (`null` hasta almacenarse) | — (asignado por la BD) |
| `alpha2Code` | String (2, mayúsculas) | Sí — identidad de negocio | `iso2Code` |
| `alpha3Code` | String (3, mayúsculas) | No | `id` |
| `name` | String | No | `name` |
| `capital` | String | No | `capitalCity` |
| `region` | String | No | `region.value` |
| `incomeLevel` | String | No | `incomeLevel.value` |
| `latitude` | BigDecimal | No | `latitude` |
| `longitude` | BigDecimal | No | `longitude` |

Reglas: textos recortados; vacíos → `null`; coordenadas no parseables → `null`.
Igualdad e identidad por `alpha2Code`.

### Resultados del puerto `CountryProviderPort` (Banco Mundial)

| Resultado | Significado |
|-----------|-------------|
| `Optional<Country>` presente | país encontrado y mapeado |
| `Optional.empty()` | el Banco Mundial indica que el código no existe (`message` o lista vacía) |
| `ExternalServiceException` | caída, timeout, HTTP ≠ 2xx, JSON inválido, estructura inesperada, sin `iso2Code` |

### `ImportResult`

`record ImportResult(Country country, boolean created)` → `created` decide 201/200.

## Persistencia

### Tabla `country`

| Columna | Tipo | Restricciones |
|---------|------|---------------|
| `id` | `BIGSERIAL` | PK; expuesto como `id` y usado en `GET /api/countries/{id}`; no cambia al actualizar |
| `alpha2_code` | `VARCHAR(2)` | `NOT NULL`, `UNIQUE` |
| `alpha3_code` | `VARCHAR(3)` | nullable, índice |
| `name` | `VARCHAR(255)` | nullable |
| `capital` | `VARCHAR(255)` | nullable |
| `region` | `VARCHAR(255)` | nullable |
| `income_level` | `VARCHAR(255)` | nullable |
| `latitude` | `NUMERIC(9,6)` | nullable |
| `longitude` | `NUMERIC(9,6)` | nullable |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` (auditoría interna, no expuesta) |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL` (auditoría interna, no expuesta) |

`CountryEntity` (JPA) vive solo en `infrastructure`; el adaptador mapea entidad ↔ `Country`.

## Transiciones (importación)

```text
             ┌───────────── WB OK ─────────────► INSERT → 201 Created
 (no existe) ┤── WB "no existe" ────────────────► 404 Not Found
             └── WB falla ──────────────────────► 502 Bad Gateway

             ┌───────────── WB OK ─────────────► UPDATE → 200 OK (datos nuevos)
 (existe)    ┤── WB "no existe" ────────────────► 200 OK (datos previos, log WARN)
             └── WB falla ──────────────────────► 200 OK (datos previos, log WARN)
```

Concurrencia: `INSERT` con violación de `UNIQUE(alpha2_code)` → reintento como `UPDATE` → 200.
