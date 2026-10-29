<!--
Sync Impact Report
==================
Version change: (plantilla sin versión) → 1.0.0
Modified principles: N/A (adopción inicial; todos los placeholders reemplazados)
Added principles:
  - I. Propósito de Negocio: Ingesta y Reutilización de Datos Bancarios
  - II. Arquitectura Hexagonal Simple (Puertos y Adaptadores)
  - III. API First con Generación Aislada
  - IV. Resiliencia ante Dependencias Externas
  - V. Errores Amigables y Semántica HTTP
  - VI. Seguridad por Bearer Token (NON-NEGOTIABLE)
  - VII. Desarrollo Guiado Estrictamente por spec.md (NON-NEGOTIABLE)
Added sections:
  - Stack Tecnológico y Despliegue
  - Flujo de Desarrollo y Puertas de Calidad
  - Governance
Removed sections: Ninguna
Templates requiring updates: N/A (las plantillas leen la constitución en tiempo de ejecución)
Follow-up TODOs:
  - Se interpretó el código "5002" de la entrada como 502 (Bad Gateway). Confirmar.
  - La elección final Maven vs Gradle se fija en plan.md.
-->

# REST API Bank Constitution

## Core Principles

### I. Propósito de Negocio: Ingesta y Reutilización de Datos Bancarios

- El sistema MUST consumir la API pública del World Bank y persistir la información obtenida
  en una base de datos PostgreSQL propia.
- La información persistida MUST ser la fuente de datos para las APIs expuestas por el
  sistema; ningún endpoint de consulta MUST depender de una llamada en vivo al World Bank.
- Toda funcionalidad nueva MUST justificarse en relación con este propósito.

**Rationale**: el valor del sistema es desacoplar a los consumidores internos del proveedor
externo, ofreciendo datos propios, estables y consultables.

### II. Arquitectura Hexagonal Simple (Puertos y Adaptadores)

- El código MUST organizarse en tres capas: `domain` (modelo y reglas de negocio, sin
  dependencias de frameworks), `application` (casos de uso y puertos) e `infrastructure`
  (adaptadores: REST de entrada, cliente World Bank, persistencia JPA, seguridad).
- El dominio MUST NOT importar clases de Spring, JPA, Jakarta ni del código generado de la API.
- Los puertos MUST ser interfaces pequeñas, con nombres de negocio y solo los métodos
  realmente usados. Un puerto por responsabilidad (p. ej. consulta externa, persistencia).
- La sobreingeniería está prohibida: sin capas, abstracciones, patrones o módulos que no
  resuelvan una necesidad presente en spec.md (YAGNI). Cualquier complejidad adicional MUST
  justificarse por escrito en plan.md.

**Rationale**: los puertos permiten sustituir el proveedor externo o la base de datos y probar
la lógica de forma aislada, sin pagar el costo de una arquitectura sobredimensionada.

### III. API First con Generación Aislada

- El contrato OpenAPI MUST definirse y aprobarse antes de implementar cualquier endpoint.
- Las interfaces y DTOs del servidor MUST generarse desde el contrato durante el build, en un
  paquete/directorio aislado (p. ej. `target/generated-sources` o `build/generated`) que no
  se edita a mano ni se versiona.
- Los adaptadores REST MUST implementar las interfaces generadas y mapear los DTOs generados
  hacia/desde el dominio; los DTOs generados MUST NOT filtrarse a `domain` ni `application`.
- Todo cambio de API MUST hacerse primero en el contrato OpenAPI.

**Rationale**: el contrato es la fuente única de verdad para consumidores y servidor, y la
generación aislada evita mezclar código generado con código mantenido a mano.

### IV. Resiliencia ante Dependencias Externas

- La falla, lentitud o indisponibilidad de la API del World Bank MUST NOT afectar a los
  endpoints de consulta, que MUST seguir respondiendo con los datos ya persistidos.
- El cliente externo MUST tener timeouts de conexión y lectura configurados.
- Una falla de sincronización MUST registrarse en logs y, si fue solicitada por un endpoint,
  MUST devolverse como error controlado (502) sin corromper ni borrar los datos existentes.
- El arranque de la aplicación MUST NOT depender de que el World Bank esté disponible.

**Rationale**: el sistema existe precisamente para no depender en tiempo real de un agente
externo.

### V. Errores Amigables y Semántica HTTP

- Todos los errores MUST manejarse de forma centralizada (`@RestControllerAdvice`) y devolver
  un cuerpo JSON consistente y comprensible (código, mensaje legible, timestamp, ruta), sin
  stack traces ni detalles internos.
- Los códigos HTTP MUST corresponder a la causa:
  - `400 Bad Request`: validación (Jakarta Validation) o parámetros mal formados.
  - `403 Forbidden`: ausencia de token o token Bearer inválido.
  - `404 Not Found`: recurso inexistente.
  - `500 Internal Server Error`: error inesperado del sistema.
  - `502 Bad Gateway`: falla al consumir la API del World Bank.
- La entrada MUST validarse con Jakarta Validation en el borde (adaptador REST).

**Rationale**: los consumidores necesitan errores predecibles y accionables; la semántica HTTP
correcta permite automatizar su manejo.

### VI. Seguridad por Bearer Token (NON-NEGOTIABLE)

- Todo endpoint de negocio MUST exigir el encabezado `Authorization: Bearer <token>`.
- El token válido MUST leerse de una variable de entorno (provista por `.env.dev` en
  despliegue de desarrollo) y MUST NOT estar codificado en el código fuente.
- Las solicitudes sin token o con token inválido MUST rechazarse con el formato de error del
  Principio V antes de llegar a cualquier caso de uso.
- La validación del token MUST implementarse de la forma más simple posible (p. ej. un filtro
  HTTP), sin introducir frameworks de seguridad no requeridos por spec.md.

**Rationale**: los datos solo deben ser consumidos por clientes autorizados, y la solución debe
ser mínima y verificable.

### VII. Desarrollo Guiado Estrictamente por spec.md (NON-NEGOTIABLE)

- El agente de IA MUST construir exclusivamente lo descrito en `spec.md`; nada de
  funcionalidades, endpoints o dependencias no especificadas.
- Si el agente detecta contradicciones, ambigüedades o fallas de lógica en `spec.md` (o entre
  `spec.md` y esta constitución), MUST detenerse, describir el problema concreto y solicitar
  la actualización de `spec.md` antes de continuar. MUST NOT asumir ni "corregir" por su
  cuenta.

**Rationale**: la especificación es el contrato con el usuario; desviarse de ella, aun con
buena intención, produce trabajo no solicitado y errores ocultos.

## Stack Tecnológico y Despliegue

- **Lenguaje**: Java 25.
- **Framework**: Spring Boot 4.
- **Build**: Maven o Gradle (uno solo; la elección se fija en plan.md).
- **Base de datos**: PostgreSQL.
- **Dependencias permitidas**: Spring Web, Spring Data JPA, Jakarta Validation, el driver de
  PostgreSQL y el plugin de generación OpenAPI requerido por el Principio III. Cualquier otra
  dependencia de runtime MUST justificarse en plan.md.
- **Despliegue**: el repositorio MUST incluir un `Dockerfile` (build multi-stage) y un
  `docker-compose.yml` que levante la aplicación y PostgreSQL.
- **Configuración**: el repositorio MUST incluir `.env.dev` con las variables de despliegue,
  incluyendo la API key usada como Bearer token y las credenciales de base de datos; la
  aplicación MUST leer toda configuración sensible desde variables de entorno.

## Flujo de Desarrollo y Puertas de Calidad

- Flujo obligatorio: `spec.md` → contrato OpenAPI → `plan.md` → `tasks.md` → implementación.
- Antes de implementar, el plan MUST pasar el "Constitution Check" contra los Principios I–VII.
- Cada endpoint MUST tener al menos una prueba que verifique: respuesta exitosa, rechazo sin
  token (403) y el caso de error principal aplicable (400/404).
- La resiliencia (Principio IV) MUST verificarse con una prueba que simule la falla del World
  Bank y confirme que las consultas siguen respondiendo.
- `docker compose up` con `.env.dev` MUST levantar el sistema funcional sin pasos manuales
  adicionales.

## Governance

- Esta constitución prevalece sobre cualquier otra práctica, plantilla o preferencia del
  agente. En caso de conflicto con `spec.md`, aplica el Principio VII: detenerse y reportar.
- Las enmiendas MUST realizarse mediante `/speckit-constitution`, documentar el motivo y
  actualizar el Sync Impact Report.
- Versionado semántico: MAJOR para eliminar o redefinir principios de forma incompatible;
  MINOR para agregar principios/secciones o ampliar guías materialmente; PATCH para
  aclaraciones y redacción.
- Toda revisión de plan, tareas o código MUST verificar el cumplimiento de esta constitución;
  las desviaciones MUST justificarse explícitamente en plan.md (sección Complexity Tracking).

**Version**: 1.0.0 | **Ratified**: 2026-10-29 | **Last Amended**: 2026-10-29
