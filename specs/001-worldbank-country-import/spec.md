# Feature Specification: Importación y Consulta de Países desde el Banco Mundial

**Feature Branch**: `001-worldbank-country-import`

**Created**: 2026-10-29

**Status**: Draft

**Input**: User description: "Construir un microservicio que consulte información de países desde
la API pública del Banco Mundial, persista los datos relevantes y los exponga mediante una API REST
propia (POST /api/countries/import?code={code}, GET /api/countries, GET /api/countries/{id}),
con validaciones, manejo de errores por código HTTP, despliegue con Docker Compose, Swagger y README."

## Clarifications

### Session 2026-10-29

- Q: Si el país ya existe, ¿se consulta al Banco Mundial? → A: Sí, siempre se consulta de nuevo
  y se actualizan los datos (200). Si la consulta falla y el país ya existía, se responde con la
  información previa almacenada (200) en lugar de error.
- Q: ¿Qué es `{id}` en `GET /api/countries/{id}`? → A: El código ISO alpha-2 del país
  (p. ej. `/api/countries/CO`). No se expone un identificador interno.
- Q: ¿Cómo distingue el consumidor una respuesta con datos previos (fallback) de una
  actualizada? → A: No se distingue; responde 200 con los datos previos y la falla solo queda
  en logs.
- Q: ¿Se guardan las agregaciones del Banco Mundial (p. ej. `WLD`, `LCN`, `EUU`)? → A: Sí,
  se guardan como cualquier país, con los campos faltantes vacíos.
- Q: ¿Tiempo máximo de espera al Banco Mundial? → A: 10 segundos por defecto, configurable en
  `.env.dev`.

## User Scenarios & Testing *(mandatory)*

Actor principal: **sistema o desarrollador consumidor interno**, autenticado con el token Bearer
configurado para el entorno.

### User Story 1 - Importar un país desde el Banco Mundial (Priority: P1)

El consumidor envía el código de un país (2 o 3 letras). El sistema obtiene la información del país
desde el Banco Mundial, extrae los campos relevantes y los guarda en su almacén propio. Si el país
no estaba registrado se crea; si ya estaba registrado se actualizan sus datos.

**Why this priority**: sin importación no existen datos para consultar; es el núcleo del negocio.

**Independent Test**: enviar `POST /api/countries/import?code=CO` con token válido y verificar que
la respuesta contiene los datos de Colombia y que el registro queda almacenado.

**Acceptance Scenarios**:

1. **Given** Colombia no está almacenado, **When** se importa con `code=CO` (o `code=COL`),
   **Then** se responde `201 Created` con el país almacenado: alpha-2 `CO`, alpha-3 `COL`, nombre
   `Colombia`, capital `Bogota`, región `Latin America & Caribbean`, nivel de ingresos
   `Upper middle income`, latitud `4.60987`, longitud `-74.082`.
2. **Given** Colombia ya está almacenado, **When** se importa nuevamente con `code=CO` o
   `code=COL`, **Then** se actualizan sus datos con la información vigente del Banco Mundial, se
   responde `200 OK` y no se crea un registro duplicado.
3. **Given** el Banco Mundial devuelve un país sin capital ni coordenadas (p. ej. una agregación
   como `WLD`, `LCN` o `EUU`), **When** se importa, **Then** el país se almacena con los campos disponibles y los
   faltantes vacíos, sin error.
4. **Given** el código no corresponde a ningún país en el Banco Mundial (respuesta con
   `message` "Invalid value"), **When** se importa, **Then** se responde `404 Not Found` con un
   mensaje amigable y no se almacena nada.
5. **Given** el código es vacío, tiene longitud distinta de 2–3 o contiene caracteres no
   alfabéticos (p. ej. `C`, `COLO`, `C1`, `  `), **When** se importa, **Then** se responde
   `400 Bad Request` sin consultar al Banco Mundial.
6. **Given** el país **no** está almacenado y el Banco Mundial no responde, excede el tiempo de
   espera o devuelve una respuesta inesperada, **When** se importa, **Then** se responde
   `502 Bad Gateway` con un mensaje amigable y no se almacena nada.
7. **Given** el país **ya** está almacenado y el Banco Mundial no responde, excede el tiempo de
   espera o devuelve una respuesta inesperada, **When** se importa, **Then** se responde `200 OK`
   con la información previa almacenada, sin modificarla, y la falla queda registrada en logs.

---

### User Story 2 - Listar los países almacenados (Priority: P2)

El consumidor obtiene todos los países almacenados, sin depender de la disponibilidad del Banco
Mundial.

**Why this priority**: es el principal uso interno de los datos importados.

**Independent Test**: con datos precargados, invocar `GET /api/countries` y verificar el arreglo.

**Acceptance Scenarios**:

1. **Given** hay N países almacenados, **When** se consulta `GET /api/countries`, **Then** se
   responde `200 OK` con un arreglo JSON de N elementos.
2. **Given** no hay países almacenados, **When** se consulta, **Then** se responde `200 OK` con `[]`.
3. **Given** el Banco Mundial está caído, **When** se consulta, **Then** la respuesta es la misma
   que si estuviera disponible.

---

### User Story 3 - Consultar un país por código alpha-2 (Priority: P3)

El consumidor obtiene el detalle de un país almacenado a partir de su código ISO alpha-2, que es
el `{id}` de la ruta.

**Why this priority**: complementa al listado para consultas puntuales.

**Independent Test**: importar Colombia y consultar `GET /api/countries/CO`.

**Acceptance Scenarios**:

1. **Given** Colombia está almacenado, **When** se consulta `GET /api/countries/CO` (o `co`),
   **Then** se responde `200 OK` con ese país.
2. **Given** no hay un país almacenado con alpha-2 `{id}`, **When** se consulta, **Then** se
   responde `404 Not Found` con mensaje amigable (sin consultar al Banco Mundial).
3. **Given** `{id}` no tiene exactamente 2 caracteres alfabéticos (p. ej. `COL`, `C1`, `123`),
   **When** se consulta, **Then** se responde `400 Bad Request`.

---

### User Story 4 - Acceso protegido por token (Priority: P1)

Solo los consumidores que presenten el token Bearer configurado pueden usar cualquiera de las tres
operaciones.

**Why this priority**: exigido por la constitución (Principio VI, NON-NEGOTIABLE).

**Independent Test**: invocar cada operación sin token, con token incorrecto y con token correcto.

**Acceptance Scenarios**:

1. **Given** una solicitud sin encabezado `Authorization`, **When** se invoca cualquier operación,
   **Then** se responde `403 Forbidden` con mensaje amigable y no se ejecuta la operación.
2. **Given** un token Bearer distinto al configurado, **When** se invoca, **Then** se responde
   `403 Forbidden`.
3. **Given** el token configurado, **When** se invoca, **Then** la operación se ejecuta normalmente.

---

### Edge Cases

- Códigos en minúsculas (`co`, `col`): se aceptan y se normalizan a mayúsculas.
- Espacios alrededor del código: el código se recorta antes de validar; si queda vacío → 400.
- Parámetro `code` ausente en la importación → 400.
- Importación con alpha-3 (`COL`) de un país previamente importado con alpha-2 (`CO`): se detecta
  como el mismo país (por alpha-2) y se actualiza → 200.
- Valores con espacios sobrantes en la respuesta del Banco Mundial (p. ej.
  `"Latin America & Caribbean "`): se almacenan recortados.
- Agregaciones del Banco Mundial (región `Aggregates`): se aceptan y almacenan igual que un
  país; no se filtran del listado.
- Latitud/longitud vacías o no numéricas en la respuesta del Banco Mundial: se almacenan vacías.
- País ya almacenado y el Banco Mundial responde con `message` o lista vacía al reconsultarlo:
  se trata como falla de consulta → `200 OK` con la información previa (FR-004).
- Respuesta del Banco Mundial con estructura inesperada o lista vacía de países → 502 si no es
  interpretable; 404 si indica explícitamente que el país no existe.
- Ruta inexistente o método HTTP no soportado: se responde con el código HTTP correspondiente
  (404 / 405) y el mismo formato de error amigable.
- Error inesperado interno (p. ej. base de datos no disponible) → 500 con mensaje amigable, sin
  detalles internos.

## Requirements *(mandatory)*

### Functional Requirements

**Importación**

- **FR-001**: El sistema MUST exponer la operación `POST /api/countries/import?code={code}`.
- **FR-002**: El sistema MUST validar `code`: obligatorio, no vacío, de 2 o 3 caracteres,
  exclusivamente alfabéticos (A–Z, sin distinguir mayúsculas). Si no cumple → `400` sin consultar
  al Banco Mundial.
- **FR-003**: Antes de consultar al Banco Mundial, el sistema MUST verificar si el país ya existe
  en su almacén, buscando por alpha-2 (si el código tiene 2 letras) o alpha-3 (si tiene 3).
- **FR-004**: Cuando el país ya existe en el almacén, el sistema MUST consultar nuevamente al
  Banco Mundial, actualizar todos los campos de FR-006 con la información obtenida y responder
  `200 OK` con el país actualizado. Si esa consulta falla (por cualquier causa de FR-010), el
  sistema MUST responder `200 OK` con la información previa almacenada, sin modificarla, y
  registrar la falla en logs (nivel WARN). La respuesta MUST ser idéntica en formato a una
  actualización exitosa; no incluye indicador de que los datos no se refrescaron.
- **FR-005**: Cuando el país no existe en el almacén, el sistema MUST consultar al Banco Mundial
  (`GET {URL_BASE}/country/{code}?format=json`), almacenar el país y responder `201 Created` con el
  país almacenado.
- **FR-006**: El sistema MUST extraer y almacenar estos campos de la respuesta del Banco Mundial:

  | Campo del sistema  | Origen en la respuesta del Banco Mundial |
  |--------------------|------------------------------------------|
  | Código ISO alpha-2 | `iso2Code`                               |
  | Código ISO alpha-3 | `id`                                     |
  | Nombre             | `name`                                   |
  | Capital            | `capitalCity`                            |
  | Región             | `region.value`                           |
  | Nivel de ingresos  | `incomeLevel.value`                      |
  | Latitud            | `latitude` (numérico decimal)            |
  | Longitud           | `longitude` (numérico decimal)           |

- **FR-007**: Si un campo no existe, es nulo, vacío o no interpretable en la respuesta del Banco
  Mundial, el sistema MUST dejarlo vacío (nulo) y almacenar los demás campos disponibles. La única
  excepción es el alpha-2: sin él no es posible identificar el país y se trata como respuesta no
  interpretable (FR-010 / FR-004).
- **FR-008**: El código ISO alpha-2 MUST ser el identificador único de negocio: nunca pueden
  existir dos países almacenados con el mismo alpha-2.
- **FR-009**: Si el país no está almacenado y el Banco Mundial responde con el bloque `message`
  (p. ej. id `120`, "Invalid value") o con una lista de países vacía, el sistema MUST responder
  `404 Not Found`.
- **FR-010**: Si el país no está almacenado y el Banco Mundial no está disponible, excede el
  tiempo máximo de espera (10 segundos por defecto, configurable según FR-021) o devuelve un error HTTP o una estructura no interpretable, el sistema MUST
  responder `502 Bad Gateway`. En ningún caso una falla del Banco Mundial MUST modificar los
  datos almacenados (si el país ya existía aplica FR-004).

**Consulta**

- **FR-011**: El sistema MUST exponer `GET /api/countries`, que responde `200 OK` con un arreglo
  JSON de todos los países almacenados, o `[]` si no hay registros.
- **FR-012**: El sistema MUST exponer `GET /api/countries/{id}`, donde `{id}` es el código ISO
  alpha-2 (exactamente 2 letras, sin distinguir mayúsculas; si no → `400`). Responde `200 OK` con
  el país o `404 Not Found` si no está almacenado.
- **FR-013**: Las operaciones de consulta MUST responder únicamente con datos almacenados y MUST
  funcionar aunque el Banco Mundial no esté disponible.
- **FR-014**: Cada país devuelto MUST incluir: alpha-2, alpha-3, nombre, capital, región, nivel
  de ingresos, latitud y longitud (vacíos como `null`). No se expone identificador interno.

**Seguridad**

- **FR-015**: Las tres operaciones MUST exigir `Authorization: Bearer <token>`, donde el token es
  el valor configurado en el archivo de entorno de despliegue. Sin token o con token inválido →
  `403 Forbidden`.
- **FR-016**: La documentación interactiva (Swagger) MUST ser accesible para consulta y permitir
  enviar el token Bearer al probar las operaciones.

**Errores**

- **FR-017**: Todo error MUST responder con un cuerpo uniforme y amigable que incluya: código
  HTTP, descripción del error, mensaje legible, fecha-hora y ruta invocada; sin trazas ni detalles
  internos.
- **FR-018**: Ningún error o excepción MUST quedar sin manejar: los no previstos responden `500`
  con el formato de FR-017 y quedan registrados en logs.
- **FR-019**: Mapeo de códigos: `400` validación, `403` autenticación, `404` no encontrado,
  `405` método no soportado, `500` error interno, `502` falla del Banco Mundial.

**Configuración y despliegue**

- **FR-020**: La URL base de la API del Banco Mundial MUST ser configurable desde el archivo
  `.env.dev`, sin recompilar (valor por defecto `https://api.worldbank.org/v2`).
- **FR-021**: El archivo `.env.dev` MUST contener: token Bearer de acceso, URL base del Banco
  Mundial, tiempo máximo de espera al Banco Mundial en segundos (por defecto `10`) y
  credenciales/conexión de la base de datos.
- **FR-022**: Todo el sistema (servicio + base de datos) MUST desplegarse con un solo comando de
  Docker Compose usando `.env.dev`.

**Documentación entregable**

- **FR-023**: El sistema MUST publicar la documentación Swagger/OpenAPI de las tres operaciones,
  incluyendo esquemas, códigos de respuesta y esquema de seguridad Bearer.
- **FR-024**: El repositorio MUST incluir un `README` con: requisitos previos, pasos de despliegue
  con Docker Compose, variables de `.env.dev`, descripción de cada operación REST con ejemplos de
  solicitud/respuesta y códigos de error, y la URL del Swagger.

### Key Entities

- **País (Country)**: país importado desde el Banco Mundial. Se identifica por su código ISO
  alpha-2 (único, obligatorio). Atributos: alpha-3, nombre, capital, región, nivel de ingresos,
  latitud, longitud.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un consumidor puede importar un país válido y obtener sus datos en menos de 5
  segundos en condiciones normales del Banco Mundial.
- **SC-002**: Importar el mismo país 10 veces (mezclando alpha-2 y alpha-3) deja exactamente 1
  registro almacenado.
- **SC-003**: Con el Banco Mundial inaccesible, el 100 % de las consultas de listado y detalle,
  y el 100 % de las importaciones de países ya almacenados, responden con éxito con los datos
  previos.
- **SC-004**: El 100 % de las solicitudes sin token válido son rechazadas.
- **SC-005**: El 100 % de los errores devueltos tienen el formato uniforme y un código HTTP
  coherente con la causa; ninguna respuesta expone trazas internas.
- **SC-006**: Una persona nueva en el proyecto despliega el sistema y realiza su primera
  importación exitosa siguiendo solo el README en menos de 15 minutos.
- **SC-007**: Cambiar la URL del Banco Mundial requiere solo editar `.env.dev` y reiniciar.

## Assumptions

- Hay un único token Bearer estático por entorno; no se requieren usuarios, roles ni emisión de
  tokens.
- Para ausencia o invalidez del token se usa `403`, conforme a la constitución v1.0.0 (aunque el
  estándar HTTP sugiere `401`).
- El código de país es insensible a mayúsculas y se normaliza a mayúsculas.
- La importación procesa un país por solicitud; no hay importación masiva ni sincronización
  programada.
- Latitud y longitud se almacenan como valores decimales; los textos vacíos se tratan como nulos.
- La documentación Swagger es pública (no requiere token para verla); las operaciones sí lo
  requieren.
- Fuera de alcance: eliminación o edición manual de países, paginación y filtros del listado.
