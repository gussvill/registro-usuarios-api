# API de registro de usuarios

Servicio REST con un solo endpoint, `POST /api/v1/users`, que registra un usuario y responde con el
usuario almacenado, un JWT firmado y los campos generados. Spring Boot 4.1.1, Java 17, base de datos H2
en memoria, solo JSON.

## Ejecutar

**Requisito:** cualquier JDK 17 o superior para iniciar Gradle (Gradle descarga un toolchain de JDK 17
si no hay ninguno instalado), o Docker.

```
./gradlew bootRun
```

El servicio escucha en `http://localhost:8080`. En otra terminal, registre el usuario de ejemplo del
enunciado:

```
curl -i -X POST http://localhost:8080/api/v1/users \
  -H 'Content-Type: application/json' \
  -d '{"name":"Juan Rodriguez","email":"juan@rodriguez.org","password":"hunter2","phones":[{"number":"1234567","citycode":"1","contrycode":"57"}]}'
```

La documentación interactiva (Swagger UI) está en `http://localhost:8080/swagger-ui.html`.

**Alternativa solo con Docker:**

```
docker build -t registro-usuarios-api .
docker run --rm -p 8080:8080 registro-usuarios-api
```

La imagen tiene dos etapas (JDK 17 para construir, JRE 17 para ejecutar), corre como usuario sin
privilegios y expone el puerto 8080. No necesita configuración.

## Cumplimiento del enunciado

Dónde se cumple cada requisito del enunciado, en su orden:

| Requisito | Dónde se cumple |
|-----------|-----------------|
| Banco de datos en memoria | H2 en memoria: [`application.properties`](src/main/resources/application.properties) (`spring.datasource.url=jdbc:h2:mem:userdb...`) y la consola H2 de [Documentación de la API y base de datos](#documentación-de-la-api-y-base-de-datos) |
| Proceso de build vía Gradle o Maven | Gradle con wrapper: `./gradlew build` ([`build.gradle`](build.gradle)); ver [Pruebas y compuertas de calidad](#pruebas-y-compuertas-de-calidad) |
| Persistencia con JPA | Hibernate mediante Spring Data JPA: [`UserJpaEntity`](src/main/java/com/registro/usuarios/infrastructure/persistence/UserJpaEntity.java) y [`UserPersistenceAdapter`](src/main/java/com/registro/usuarios/infrastructure/persistence/UserPersistenceAdapter.java) |
| Framework Spring Boot | Spring Boot 4.1.1 ([`build.gradle`](build.gradle)) |
| Java 8+ | Java 17 (toolchain de [`build.gradle`](build.gradle)); el motivo está en [Supuestos sobre el enunciado](#supuestos-sobre-el-enunciado) |
| Repositorio público con código fuente y script de creación de BD | [`src/main/resources/schema.sql`](src/main/resources/schema.sql) |
| Readme explicando cómo probarlo | La sección [Ejecutar](#ejecutar), [`scripts/acceptance.sh`](scripts/acceptance.sh) y la [colección de Postman](postman/registro-usuarios-api.postman_collection.json) |
| Diagrama de la solución | [Diagramas](#arquitectura) en `docs/diagrams` (PNG, fuente JSON y versión HTML interactiva) |
| JWT como token | [`JjwtTokenIssuer`](src/main/java/com/registro/usuarios/infrastructure/security/JjwtTokenIssuer.java): HS256 con `sub`, `email`, `iat` y `exp` |
| Pruebas unitarias | `./gradlew test` y el árbol [`src/test/java`](src/test/java) |
| Swagger | `http://localhost:8080/swagger-ui.html` |
| Patrones de diseño y buenas prácticas | [Patrones de diseño aplicados](#patrones-de-diseño-aplicados) y las [decisiones de arquitectura](docs/architecture-decisions.md) |

Y las reglas funcionales del registro:

| Regla del enunciado | Dónde se cumple |
|---------------------|-----------------|
| Solo JSON; los errores son `{"mensaje": "..."}` | [`UserApi`](src/main/java/com/registro/usuarios/infrastructure/web/UserApi.java) (`consumes` y `produces` JSON) y [Formato de error](#formato-de-error) |
| Respuesta con `id` (UUID), `created`, `modified`, `last_login`, `token` e `isactive` | [`UserResponse`](src/main/java/com/registro/usuarios/infrastructure/web/UserResponse.java) y [Qué responde](#qué-responde) |
| Un correo repetido responde `El correo ya registrado` | Estado `409`, en [Códigos de estado](#códigos-de-estado); la unicidad la garantiza la restricción `UNIQUE` de `users.email` |
| El correo se valida con una expresión regular | [`Email`](src/main/java/com/registro/usuarios/domain/model/Email.java), con el patrón de `app.registration.email-pattern` |
| La contraseña se valida con una expresión regular configurable | `app.registration.password-pattern`, en [Configuración](#configuración); [`RegexPasswordPolicy`](src/main/java/com/registro/usuarios/domain/policy/RegexPasswordPolicy.java) |
| El token se persiste con el usuario | Columna `users.token` de [`schema.sql`](src/main/resources/schema.sql); el motivo está en el [ADR-016](docs/architecture-decisions.md#adr-016-el-token-se-persiste-en-claro-y-la-clave-de-firma-es-efímera-salvo-que-se-configure) |

## Qué responde

La respuesta al ejemplo es `201` con este cuerpo (el id, las marcas de tiempo y el token cambian en
cada llamada; aquí el token está abreviado):

```json
{"id":"9048ce8d-03f7-4b80-a63c-e14f767b6cbb","name":"Juan Rodriguez","email":"juan@rodriguez.org","phones":[{"number":"1234567","citycode":"1","contrycode":"57"}],"created":"2026-10-08T00:25:56.057817Z","modified":"2026-10-08T00:25:56.057817Z","last_login":"2026-10-08T00:25:56.057817Z","token":"eyJhbGciOiJIUzI1NiJ9...","isactive":true}
```

`created`, `modified` y `last_login` son el mismo instante. El token es HS256 con los claims `sub` (el
id del usuario), `email`, `iat` y `exp` (15 minutos después de `iat` por defecto). La contraseña nunca
se devuelve. La respuesta `201` lleva el encabezado `Cache-Control: no-store`, porque contiene un token
al portador y ninguna caché debe guardarla.

Si se envía la misma solicitud otra vez, la respuesta es `409`:

```
{"mensaje":"El correo ya registrado"}
```

Una solicitud que rompe varias reglas informa todos los campos erróneos, ordenados y unidos con `"; "`:

```
curl -s -X POST http://localhost:8080/api/v1/users -H 'Content-Type: application/json' \
  -d '{"name":"","email":"bad","password":"x"}'
```

```
{"mensaje":"El correo no tiene un formato válido; El nombre es obligatorio; La contraseña no cumple el formato requerido"}
```

### Códigos de estado

| Estado | Cuándo | `mensaje` |
|--------|--------|-----------|
| 201 | El usuario fue registrado | (el usuario, no un error) |
| 400 | Uno o más campos incumplen una regla | Los mensajes de los campos erróneos, unidos con `"; "` |
| 400 | El cuerpo no es JSON válido, está vacío o un campo tiene el tipo equivocado | `El cuerpo de la solicitud no es válido` |
| 400 | Cualquier otro 400 producido dentro de la aplicación | `La solicitud no es válida` |
| 404 | Ruta desconocida | `Recurso no encontrado` |
| 405 | Método incorrecto en una ruta conocida (se conserva el encabezado `Allow`) | `Método no permitido` |
| 406 | No se puede satisfacer el encabezado `Accept` | `Formato de respuesta no aceptable` |
| 409 | El correo ya está registrado | `El correo ya registrado` |
| 415 | El `Content-Type` no es JSON | `Tipo de contenido no soportado` |
| 500 | Cualquier cosa inesperada | `Error interno del servidor` |

### Formato de error

Todo error es un objeto JSON con una sola clave, como exige el enunciado:

```json
{"mensaje": "..."}
```

El texto está en español. El valor rechazado nunca se repite en la respuesta, y un 500 nunca incluye
texto interno. El formato no es RFC 9457 porque el enunciado fija esta forma; el razonamiento está en
el [ADR-008](docs/architecture-decisions.md#adr-008-el-contrato-de-error-mensaje-en-lugar-de-rfc-9457).

## Configuración

Las propiedades están en [`src/main/resources/application.properties`](src/main/resources/application.properties).
Cualquiera se puede fijar desde el entorno.

| Propiedad | Variable de entorno | Valor por defecto | Significado |
|-----------|---------------------|-------------------|-------------|
| `app.registration.email-pattern` | `APP_REGISTRATION_EMAIL_PATTERN` | `^[a-z0-9._%+-]+@[a-z0-9-]+(\.[a-z0-9-]+)*\.[a-z]{2,}$` | Formato del correo, aplicado a la dirección en minúsculas |
| `app.registration.password-pattern` | `APP_REGISTRATION_PASSWORD_PATTERN` | `^(?=.*[A-Za-z])(?=.*[0-9])\S{7,72}$` | Formato de la contraseña: una letra y un dígito, de 7 a 72 caracteres sin espacios |
| `app.token.secret` | `TOKEN_SECRET` | ninguno | Secreto de firma HS256, de al menos 32 bytes; la aplicación no arranca con uno más corto. Si no se define, se genera una clave aleatoria de 256 bits al arrancar y los tokens no sobreviven a un reinicio |
| `app.token.expiration` | `APP_TOKEN_EXPIRATION` | `15m` | Vigencia del token (`120s`, `15m`, ...); rango permitido: mayor que cero y hasta 24 horas (`24h`); fuera de ese rango la aplicación no arranca |

**La aplicación no distribuye ningún secreto de firma.** Sin `TOKEN_SECRET` el servicio genera una
clave aleatoria en cada arranque y registra una línea `INFO` que lo indica (nunca la clave). Fíjelo
para que los tokens sigan siendo válidos tras un reinicio:

```
docker run --rm -p 8080:8080 -e TOKEN_SECRET='replace-with-at-least-32-random-bytes' registro-usuarios-api
```

**El patrón de contraseña por defecto es débil a propósito**, porque la contraseña de ejemplo del
enunciado, `hunter2`, debe pasar. Para exigir doce o más caracteres con una minúscula, una mayúscula,
un dígito y un símbolo:

```
APP_REGISTRATION_PASSWORD_PATTERN='^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9\s])\S{12,72}$' ./gradlew bootRun
```

Con ese patrón, `hunter2` recibe `400` y `La contraseña no cumple el formato requerido`. El límite de
72 bytes de la contraseña se mantiene sea cual sea el patrón.

La consola H2 y Swagger UI son herramientas de desarrollo y vienen activadas. Para desactivarlas:

```
SPRING_H2_CONSOLE_ENABLED=false SPRINGDOC_SWAGGER_UI_ENABLED=false ./gradlew bootRun
```

## Documentación de la API y base de datos

| Qué | Valor |
|-----|-------|
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Documento OpenAPI | `http://localhost:8080/v3/api-docs` |
| Script de creación | [`src/main/resources/schema.sql`](src/main/resources/schema.sql) |
| Consola H2 | `http://localhost:8080/h2-console` |
| URL JDBC | `jdbc:h2:mem:userdb` |
| Usuario / contraseña | `sa` / (vacía) |

La base de datos es H2 en memoria: se crea al arrancar y se pierde cuando el proceso se detiene. El
script crea `users` (con restricción `UNIQUE` sobre el correo) y `phones` (una fila por teléfono,
enlazada al usuario por una clave foránea). Hibernate solo lo valida (`ddl-auto=validate`); nunca crea
ni altera una tabla.

**La consola H2 solo acepta conexiones locales**: se usa con `./gradlew bootRun`. Con `docker run -p`
la página se sirve, pero rechaza la conexión ("remote connections are disabled").

## Pruebas y compuertas de calidad

```
./gradlew test                       # la suite de pruebas
./gradlew build                      # compila, verifica formato, pruebas y umbral de cobertura
./gradlew clean build --rerun-tasks  # lo mismo desde cero, sin caché
./gradlew spotlessApply              # corrige el formato
bash scripts/acceptance.sh           # con el servicio en marcha: solicitudes reales con curl
npx -y newman run postman/registro-usuarios-api.postman_collection.json   # colección de Postman
```

- **Capas de prueba:** JUnit simple para el dominio y el caso de uso (sin Spring ni base de datos),
  cortes de Spring para los adaptadores web y de persistencia, pruebas de contexto completo sobre HTTP
  real contra H2, y pruebas de arquitectura.
- **Cobertura:** JaCoCo exige al menos 80 % de líneas en `domain` y `application`. La infraestructura
  se mide, pero no tiene umbral. El informe queda en `build/reports/jacoco/test/html/index.html`.
- **Verificaciones estáticas:** Spotless con Google Java Format, Error Prone, `-Xlint:all -Werror` y
  reglas de ArchUnit (el dominio no importa ningún framework, las dependencias apuntan hacia adentro,
  los adaptadores no dependen entre sí, sin ciclos entre paquetes, sin inyección en campos). Una segunda clase de pruebas demuestra
  que cada regla falla cuando una clase de fixture la rompe.
- **Script de aceptación:** hace solicitudes reales con `curl` (el ejemplo del enunciado, el duplicado,
  cuerpos inválidos y mal formados, 404, 405, 406, 415, Swagger UI y el documento OpenAPI) y termina con
  código distinto de cero en el primer fallo. Se puede ejecutar varias veces sobre la misma instancia,
  porque cada ejecución registra una dirección distinta; `BASE_URL` apunta a otra dirección.
- **Colección de Postman:** ver la sección siguiente.
- **CI:** [`.github/workflows/ci.yml`](.github/workflows/ci.yml) ejecuta `./gradlew build` y construye la
  imagen en cada push y pull request a `main`.

## Colección de Postman

[`postman/registro-usuarios-api.postman_collection.json`](postman/registro-usuarios-api.postman_collection.json)
(Postman Collection v2.1) tiene 16 solicitudes con sus pruebas: el registro, el duplicado, cada tipo de
rechazo, 404, 405, 406, 415 y el documento OpenAPI. La solicitud `00` envía el ejemplo literal del
enunciado: responde `201` la primera vez y `409` después, y su prueba acepta ambos. Las demás usan un
correo distinto en cada ejecución, de modo que la colección se puede repetir sobre la misma instancia.

- **Importar en Postman:** *Import*, elegir el archivo. La variable de colección `baseUrl` vale
  `http://localhost:8080`; cámbiela si el servicio escucha en otra dirección.
- **Ejecutar con Newman** (con el servicio en marcha):

```
npx -y newman run postman/registro-usuarios-api.postman_collection.json
npx -y newman run postman/registro-usuarios-api.postman_collection.json --env-var baseUrl=http://localhost:9090
```

## Arquitectura

![Arquitectura hexagonal del servicio](docs/diagrams/components.png)

Las líneas continuas son "usa" y las discontinuas "implementa". Toda dependencia que cruza la frontera
del núcleo (`application` y `domain`) apunta hacia adentro. Las dos flechas que quedan fuera del núcleo
son `lee` (de `ApplicationConfig` a `RegistrationProperties`) y `JPA` (de `UserPersistenceAdapter` a H2).
El diagrama omite que el adaptador web también usa tipos del dominio (`UserWebMapper`,
`GlobalExceptionHandler` y `ErrorMessages`).

![Flujo de registro de un usuario](docs/diagrams/registration-sequence.png)

![Flujo de registro de un usuario: errores](docs/diagrams/registration-sequence-errors.png)

Cada diagrama tiene, en [`docs/diagrams`](docs/diagrams), su fuente en JSON, el PNG de arriba y una versión
HTML interactiva y autocontenida (tema claro y oscuro, búsqueda y zoom) que se abre en el navegador tras
clonar el repositorio:

| Diagrama | Fuente | Interactivo |
|---|---|---|
| Arquitectura | [`components.json`](docs/diagrams/components.json) | [`components.html`](docs/diagrams/components.html) |
| Registro, camino feliz | [`registration-sequence.json`](docs/diagrams/registration-sequence.json) | [`registration-sequence.html`](docs/diagrams/registration-sequence.html) |
| Registro, errores | [`registration-sequence-errors.json`](docs/diagrams/registration-sequence-errors.json) | [`registration-sequence-errors.html`](docs/diagrams/registration-sequence-errors.html) |

- `domain` no tiene framework: `User`, `Email`, `Phone`, `UserId`, las reglas de validación (con sus
  motivos tipados, `Reason`), los límites fijos de la contraseña (`Password`), el formato configurable
  (`PasswordPolicy`) y los tres puertos de salida (`UserRepository`, `PasswordHasher`, `TokenIssuer`).
- `application` tiene el puerto de entrada `RegisterUser` (con su comando) y una sola implementación,
  `RegisterUserUseCase`, que orquesta el registro y es dueña de la transacción. `@Transactional` es el
  único tipo del framework permitido allí.
- `infrastructure` contiene los adaptadores: `web` (controlador, records JSON, manejo de errores),
  `persistence` (JPA), `security` (BCrypt, JJWT) y `config` (cableado y propiedades).

**Por qué un hexágono para un solo endpoint.** Hay puertos donde hay una dependencia reemplazable: la
base de datos, el hash de contraseñas y la firma de tokens. Eso permite probar el dominio y el caso de
uso sin contenedor ni base de datos, y mantiene los tipos de terceros (JPA, BCrypt, JJWT) fuera de las
reglas de negocio. El costo, dicho en una frase: hay más tipos de los que el comportamiento necesita, y
un servicio tan pequeño normalmente no requeriría un hexágono. Cada decisión, con las alternativas
descartadas, está en [`docs/architecture-decisions.md`](docs/architecture-decisions.md#adr-001-un-hexágono-ligero-para-un-servicio-de-un-solo-endpoint).

### Patrones de diseño aplicados

| Patrón | Dónde | Qué problema resuelve aquí |
|--------|-------|----------------------------|
| Puertos y adaptadores (arquitectura hexagonal) | Puerto de entrada [`RegisterUser`](src/main/java/com/registro/usuarios/application/port/RegisterUser.java); puertos de salida [`UserRepository`](src/main/java/com/registro/usuarios/domain/port/UserRepository.java), [`PasswordHasher`](src/main/java/com/registro/usuarios/domain/port/PasswordHasher.java) y [`TokenIssuer`](src/main/java/com/registro/usuarios/domain/port/TokenIssuer.java), implementados en `infrastructure` | El dominio y el caso de uso se prueban sin base de datos, BCrypt ni JJWT |
| Repository | [`UserRepository`](src/main/java/com/registro/usuarios/domain/port/UserRepository.java) y [`UserPersistenceAdapter`](src/main/java/com/registro/usuarios/infrastructure/persistence/UserPersistenceAdapter.java) | El dominio guarda un usuario sin conocer JPA |
| Adapter | [`UserPersistenceAdapter`](src/main/java/com/registro/usuarios/infrastructure/persistence/UserPersistenceAdapter.java), [`BCryptPasswordHasher`](src/main/java/com/registro/usuarios/infrastructure/security/BCryptPasswordHasher.java), [`JjwtTokenIssuer`](src/main/java/com/registro/usuarios/infrastructure/security/JjwtTokenIssuer.java) | Adapta las interfaces de JPA, BCrypt y JJWT a los puertos del dominio |
| Strategy | [`PasswordPolicy`](src/main/java/com/registro/usuarios/domain/policy/PasswordPolicy.java) y [`RegexPasswordPolicy`](src/main/java/com/registro/usuarios/domain/policy/RegexPasswordPolicy.java) | Separa el formato de la contraseña, que el enunciado pide configurable, de los límites fijos de `Password`. Tiene una sola implementación de producción: existe por esa regla configurable, no por variedad |
| Value Object | [`Email`](src/main/java/com/registro/usuarios/domain/model/Email.java), [`UserId`](src/main/java/com/registro/usuarios/domain/model/UserId.java), [`Phone`](src/main/java/com/registro/usuarios/domain/model/Phone.java) | Normalización y validez en un solo lugar; un valor inválido no llega a existir |
| Builder | `User.Builder` en [`User`](src/main/java/com/registro/usuarios/domain/model/User.java) | Evita pasar por error un token donde va un hash: tres `String` contiguos |
| Método de fábrica estático | `User.registration()`, `Email.of` y `UserId.generate()` | Construyen con validación o con el valor generado por la aplicación, sin exponer el constructor. No es el patrón Factory de GoF, que no se usa |
| DTO y Mapper | Records [`RegisterUserRequest`](src/main/java/com/registro/usuarios/infrastructure/web/RegisterUserRequest.java) y [`UserResponse`](src/main/java/com/registro/usuarios/infrastructure/web/UserResponse.java); [`UserWebMapper`](src/main/java/com/registro/usuarios/infrastructure/web/UserWebMapper.java) | El JSON no se acopla al dominio, y la respuesta nunca lee el hash de la contraseña |
| Inyección de dependencias por constructor | [`ApplicationConfig`](src/main/java/com/registro/usuarios/infrastructure/config/ApplicationConfig.java) cablea el caso de uso con sus puertos | Las dependencias son explícitas y se sustituyen en pruebas; una regla de ArchUnit prohíbe la inyección en campos |

El razonamiento de cada uno está en [`docs/architecture-decisions.md`](docs/architecture-decisions.md#adr-001-un-hexágono-ligero-para-un-servicio-de-un-solo-endpoint). Se descartaron a propósito Factory, Observer, los eventos de dominio y CQRS, y no se usan bibliotecas de mapeo ni Lombok ([ADR-001](docs/architecture-decisions.md#adr-001-un-hexágono-ligero-para-un-servicio-de-un-solo-endpoint) y [ADR-022](docs/architecture-decisions.md#adr-022-lo-que-se-dejó-fuera-deliberadamente)).

## Supuestos sobre el enunciado

- **`contrycode` se conserva tal como está escrito.** El enunciado escribe así el campo de país del
  teléfono, de modo que solicitudes, respuestas y el documento OpenAPI lo usan
  ([ADR-018](docs/architecture-decisions.md#adr-018-el-contrycode-literal)).
- **`{"mensaje": "..."}` se usa solo para errores.** Las respuestas exitosas llevan el usuario, sin
  envoltorio.
- **Un correo duplicado es un 409.** El texto es el que da el enunciado.
- **Los teléfonos son opcionales.** `phones` puede estar ausente, ser `null` o vacío y siempre se
  devuelve como arreglo. Cada teléfono necesita sus tres campos.
- **Los campos de teléfono son dígitos.** `number` y `citycode` contienen solo los dígitos 0 a 9;
  `contrycode` son dígitos con un `+` inicial opcional. El ejemplo del enunciado (`"1234567"`, `"1"`,
  `"57"`) es válido; `"123-4567"` o `"57+"` reciben un `400` con su propio mensaje.
- **El correo se pasa a minúsculas y no se recorta.** Dos direcciones que difieren solo en mayúsculas
  son la misma dirección.
- **Los límites de los campos son supuestos**, porque el enunciado no da ninguno: nombre 255
  caracteres, correo 254, contraseña 72 bytes, 10 teléfonos, número de teléfono 20 caracteres, códigos
  de ciudad y de país 10 cada uno.
- **El token se almacena en claro**, porque el enunciado exige que se persista.
- **Java 17 en lugar de "Java 8+".** Spring Boot 3 y posteriores necesitan Java 17
  ([ADR-011](docs/architecture-decisions.md#adr-011-spring-boot-411-y-java-17-frente-al-java-8-del-enunciado)).

## Limitaciones conocidas

- Las solicitudes que el contenedor de servlets rechaza antes de que corra código de la aplicación (una
  secuencia de porcentaje inválida en la ruta, una línea de solicitud mal formada, encabezados
  demasiado grandes) se responden con la página de error propia del contenedor y quedan fuera del
  contrato JSON.
- Sin `TOKEN_SECRET` la clave de firma es efímera y los tokens no sobreviven a un reinicio; el token se
  almacena en claro.
- El patrón de contraseña por defecto es débil a propósito.
- Los datos están en memoria y se pierden al reiniciar.
- La consola H2 y Swagger UI vienen activadas.
- Una barra final (`POST /api/v1/users/`) responde `404`, y la aplicación no fija un tamaño máximo para
  el cuerpo de la solicitud más allá de los valores por defecto del servidor.
- Una respuesta de duplicado le dice a quien llama que una dirección está registrada.

La lista completa, con su razonamiento, está al final de
[`docs/architecture-decisions.md`](docs/architecture-decisions.md#limitaciones-conocidas).
