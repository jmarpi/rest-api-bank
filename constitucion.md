
## 1 logica principal del negocio
- El objetivo es consumir una api de bancos y almacenar en una base de datos, para luego usar esta informacion para otras apis
## 2 Stack tecnologico 
- Usar Springboot 4
- Usar java 25
- build tool Maven o Gradle
- Base de datos Postgres
- Dependencias usar:
    - Spring Web
    - Spring Data JPA
    - Jakarta Validation
- Generar un dockerfile y docker compose para despliegue
- Generar un .env.dev para despligue , este archivo debe contener la api key para consumo de servicio mediante bearer token
## 3 Arquitectura
- Usar arquitectura hexagonal (puertos y adaptadores) 
- no usar sobreingenieria
- las interfaces deben ser simples y mantenibles
- Api first y generacion aislada
- La funcionalidad del sistema no debe depender de agentes externos, en este caso si el consumo de la api del world bank falla, el sistema debe funcionar
## 4 Manejo de errores
- Los errores deben ser amigables y devolver un error de acuerdo al codigo http (404,400,500,5002,403)
## 5 Seguridad
- El sistema no debe permitir consumo de api sin auth bearer token que se genera en el .env.dev
## 6 Comportamiento de la IA del agente
- Construir estrictamente lo que se encuentra en el archivo spec.md
- Si encuentra contradicciones o fallas en la logica detenerse y advertir del error y solicitar actualizacion de spec.md
