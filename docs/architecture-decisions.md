# Decisiones de arquitectura

Este documento registra por qué el servicio está construido como está. Cada decisión es un registro
breve con su contexto, la decisión, las alternativas descartadas y las consecuencias. Es autocontenido:
todo lo que menciona está en este repositorio.

El servicio tiene una sola capacidad: `POST /api/v1/users` registra un usuario y responde con el
usuario almacenado, un token firmado y los campos generados. Las decisiones siguientes se derivan de
eso y del enunciado del ejercicio.

## Índice

| Id | Decisión |
|----|----------|
| [ADR-001](#adr-001-un-hexágono-ligero-para-un-servicio-de-un-solo-endpoint) | Un hexágono ligero para un servicio de un solo endpoint |
| [ADR-002](#adr-002-puertos-y-adaptadores-una-razón-para-cada-uno) | Puertos y adaptadores, una razón para cada uno |
| [ADR-003](#adr-003-un-puerto-de-entrada-para-el-caso-de-uso) | Un puerto de entrada para el caso de uso |
| [ADR-004](#adr-004-transactional-en-el-caso-de-uso-es-la-única-concesión-al-framework) | `@Transactional` en el caso de uso es la única concesión al framework |
| [ADR-005](#adr-005-un-modelo-de-persistencia-separado-con-mapeo-de-solo-escritura) | Un modelo de persistencia separado con mapeo de solo escritura |
| [ADR-006](#adr-006-la-validación-vive-en-el-dominio) | La validación vive en el dominio |
| [ADR-007](#adr-007-tipado-estricto-de-strings-en-json) | Tipado estricto de strings en JSON |
| [ADR-008](#adr-008-el-contrato-de-error-mensaje-en-lugar-de-rfc-9457) | El contrato de error `{"mensaje"}` en lugar de RFC 9457 |
| [ADR-009](#adr-009-mapeo-de-estados) | Mapeo de estados |
| [ADR-010](#adr-010-un-controlador-de-errores-para-los-errores-reenviados-a-la-ruta-de-error) | Un controlador de errores para los errores reenviados a la ruta de error |
| [ADR-011](#adr-011-spring-boot-411-y-java-17-frente-al-java-8-del-enunciado) | Spring Boot 4.1.1 y Java 17 frente al "Java 8+" del enunciado |
| [ADR-012](#adr-012-gradle-como-herramienta-de-build) | Gradle como herramienta de build |
| [ADR-013](#adr-013-h2-hibernate-y-un-schemasql-versionado-con-validate) | H2, Hibernate y un `schema.sql` versionado con `validate` |
| [ADR-014](#adr-014-jjwt-con-jackson-2-junto-a-jackson-3) | JJWT con Jackson 2 junto a Jackson 3 |
| [ADR-015](#adr-015-bcrypt-mediante-spring-security-crypto-sin-el-starter-de-seguridad) | BCrypt mediante `spring-security-crypto`, sin el starter de seguridad |
| [ADR-016](#adr-016-el-token-se-persiste-en-claro-y-la-clave-de-firma-es-efímera-salvo-que-se-configure) | El token se persiste en claro y la clave de firma es efímera salvo que se configure |
| [ADR-017](#adr-017-un-strategy-para-el-formato-de-la-contraseña-con-un-valor-por-defecto-débil-a-propósito) | Un Strategy para el formato de la contraseña, con un valor por defecto débil a propósito |
| [ADR-018](#adr-018-el-contrycode-literal) | El `contrycode` literal |
| [ADR-019](#adr-019-normalización-del-correo-y-límites-de-campos) | Normalización del correo y límites de campos |
| [ADR-020](#adr-020-identificador-generado-por-la-aplicación-y-una-única-lectura-del-reloj) | Identificador generado por la aplicación y una única lectura del reloj |
| [ADR-021](#adr-021-compuertas-de-calidad) | Compuertas de calidad |
| [ADR-022](#adr-022-lo-que-se-dejó-fuera-deliberadamente) | Lo que se dejó fuera deliberadamente |

Después de los registros: [componentes](#componentes) y [limitaciones conocidas](#limitaciones-conocidas).

---

## ADR-001: Un hexágono ligero para un servicio de un solo endpoint

**Contexto.** Esto es un compromiso y se declara primero: un servicio tan pequeño normalmente no
necesitaría un hexágono. Tiene un punto de entrada, una base de datos, dos tablas y lógica solo de
creación. Sin el ejercicio sería un controlador, un servicio y un repositorio con un DTO de entrada y
anotaciones de validación, y esa sería la opción más barata. La arquitectura hexagonal rinde cuando el
dominio debe probarse sin infraestructura, cuando hay más de un punto de entrada o una dependencia
externa inestable, o cuando el servicio vivirá más que su framework. Aquí solo se cumple la primera, y
solo en parte.

**Decisión.** El enunciado pide mostrar patrones de diseño y buenas prácticas, así que el servicio se
construye como un hexágono y se mantiene lo más liviano posible. Cinco reglas acotan el costo:

1. Una sola clase de caso de uso, con un único puerto de entrada que la expone (ADR-003).
2. Un puerto existe solo donde hay una dependencia real y reemplazable: la base de datos, el hash y la
   firma del token (ADR-002). El tiempo usa `java.time.Clock`, no un puerto propio.
3. Sin camino de lectura. El puerto del repositorio tiene dos métodos y no hay mapeo desde el
   almacenamiento de vuelta al dominio.
4. Sin biblioteca de mapeo, sin Lombok, sin eventos de dominio, sin CQRS.
5. Cada patrón de la tabla siguiente se nombra con la razón por la que está. Strategy tiene una sola
   implementación de producción; se justifica por lo que hace, que es separar la regla reemplazable
   de los límites fijos (ADR-017).

Patrones en uso y por qué:

| Patrón | Dónde | Por qué está |
|--------|-------|--------------|
| Puertos y adaptadores | `domain.port` e `infrastructure.*` | La base de datos, el hash y la firma son dependencias reemplazables, y las pruebas unitarias las sustituyen por dobles de prueba |
| Value object | `Email`, `UserId`, `Phone` | Normalización y validez en un solo lugar |
| Strategy | `PasswordPolicy` | Aísla el formato de la contraseña, la única regla que el enunciado pide configurable, de los límites fijos que aplica `Password`; una implementación de producción, `RegexPasswordPolicy`, construida desde una propiedad (ADR-017) |
| Adapter | `UserPersistenceAdapter`, `JjwtTokenIssuer`, `BCryptPasswordHasher` | Interfaces de terceros frente a los puertos |
| Builder | `User.Builder` | Tres campos `String` contiguos (`name`, `passwordHash`, `token`) invitan a una llamada posicional que guarda un token como si fuera un hash |

`GlobalExceptionHandler` es un `@RestControllerAdvice`: un mecanismo del framework que da al servicio
un único lugar para traducir errores (ADR-008), no un patrón de diseño de esta lista.

**Alternativas descartadas.**

- Paquetes por capa sin dominio (`controller`, `service`, `repository`): más simple y suficiente para
  el comportamiento, pero no muestra ninguno de los patrones pedidos.
- Paquetes anidados `adapter/in`, `adapter/out` y `application/port/out`: niveles de paquete de más
  para un puñado de clases. Solo existe `application.port`, que contiene el puerto de entrada y su
  comando (ADR-003); los puertos de salida están en `domain.port` (ADR-002).
- Factory, Observer, eventos de dominio y CQRS: nada en este servicio los requiere.

**Consecuencias.**

- Hay más tipos de los que el comportamiento necesita, y quien lee debe seguir un puerto para
  encontrar la clase que hay detrás. Las reglas de ArchUnit y una prueba que demuestra que cada regla
  falla ante una violación mantienen la estructura honesta (ADR-021).
- El dominio se prueba con JUnit simple, sin contexto de Spring ni base de datos.
- Si el servicio sigue siendo un solo endpoint, el diseño por capas habría sido más barato. Si aparece
  un segundo punto de entrada o un segundo almacenamiento, los puertos ya están donde iría el cambio.

## ADR-002: Puertos y adaptadores, una razón para cada uno

**Contexto.** Un puerto es una interfaz que pertenece al lado interno y se implementa desde fuera.
Solo vale la pena si hay algo real al otro lado.

**Decisión.** Tres puertos de salida viven en `domain.port`.

| Puerto | Adaptador | Por qué existe el puerto |
|--------|-----------|--------------------------|
| `UserRepository` (`existsByEmail`, `save`) | `UserPersistenceAdapter`, respaldado por Spring Data JPA; `InMemoryUserRepository`, un doble de prueba en el árbol de pruebas | El caso de uso se prueba sin base de datos |
| `PasswordHasher` (`hash`) | `BCryptPasswordHasher`; un hasher falso, doble de prueba | BCrypt es lento a propósito; las pruebas unitarias no deben pagarlo |
| `TokenIssuer` (`issue`) | `JjwtTokenIssuer`; un emisor falso, doble de prueba | Mantiene JJWT, el manejo de claves y su dependencia de Jackson 2 fuera de las capas internas |

El tiempo no es un puerto: `java.time.Clock` se inyecta directamente y las pruebas usan `Clock.fixed`.

**Alternativas descartadas.**

- Llamar a `JpaRepository` desde el caso de uso: acopla la capa de aplicación a Spring Data y expone
  `deleteAll` y `findAll` a código que solo debe crear.
- Llamar a `BCryptPasswordEncoder` desde el caso de uso: pone Spring Security en la capa de aplicación.
- Un puerto `TimeProvider` propio: el tipo del JDK ya es la abstracción.
- Un puerto proveedor de la política de contraseña: una interfaz cuyo único trabajo es devolver otra
  interfaz. `PasswordPolicy` es un Strategy y la clase de cableado lo construye desde propiedades.

**Consecuencias.** Cada puerto tiene un adaptador de producción. La segunda implementación de cada
puerto, en el árbol de pruebas, es un doble de prueba (un repositorio en memoria, un hasher falso, un
emisor falso), no un segundo adaptador de producción: los puertos se justifican por una dependencia
reemplazable y por la capacidad de prueba, no por una segunda implementación real. Los adaptadores son
privados al paquete e independientes entre sí, lo que una regla de ArchUnit hace cumplir.

## ADR-003: Un puerto de entrada para el caso de uso

**Contexto.** Los diagramas hexagonales muestran un puerto de entrada delante del caso de uso. El
servicio ya tiene puertos de salida (ADR-002): sin el de entrada, cada frontera entre capas sería una
interfaz salvo esta, y el adaptador web dependería de una clase concreta de la capa de aplicación.

**Decisión.** `RegisterUser` (en `application.port`, junto a `RegisterUserCommand`) es la interfaz del
caso de uso, con el único método `register`. `RegisterUserUseCase` la implementa y conserva el
`@Transactional`. `UserController` depende de la interfaz y `ApplicationConfig` expone el bean con ese
tipo. La implementación vive directamente en `application`; el subpaquete `port` es lo que el adaptador
web puede ver, y una regla de ArchUnit prohíbe que una clase de `infrastructure.web` dependa de las
clases de `application` (ADR-021). Se agregan interfaces solo en las fronteras entre capas: no hay
interfaces para value objects, records, entidades, excepciones ni clases de configuración.

**Por qué.** El adaptador web depende de una abstracción cuyo dueño es la capa de aplicación, de forma
simétrica con los puertos de salida, que son abstracciones cuyo dueño es el dominio. Además, el
controlador se prueba con un doble de la interfaz sin tocar una clase concreta que Spring envuelve en un
proxy.

**Alternativas descartadas.** Depender de la clase concreta: una interfaz con un solo implementador
agrega un tipo y un salto, y el argumento era que la extracción se podía hacer cuando apareciera un
segundo llamador. Se descartó porque la frontera entre capas queda sin abstracción hasta entonces y
porque la regla de dependencia deja de poder comprobarse con una prueba de arquitectura.

**Consecuencias.** El costo es un tipo más con una única implementación. El proxy de `@Transactional` es
de clase (CGLIB, el valor por defecto de Boot), así que la implementación sigue sin ser `final` y su
método sigue siendo público; una prueba lo fija.

## ADR-004: `@Transactional` en el caso de uso es la única concesión al framework

**Contexto.** La comprobación de existencia del correo, la inserción del usuario y la de sus teléfonos
son pasos de una misma operación, y la capa de aplicación es la dueña natural de ese límite.

**Decisión.** `RegisterUserUseCase.register` lleva el `@Transactional` de Spring. El caso de uso es el
límite de la transacción para que la comprobación de duplicado, la inserción del usuario y la de sus
teléfonos se confirmen o se deshagan juntas. Es el único tipo del framework en los paquetes `domain` y
`application`. La clase no tiene anotación de estereotipo: `ApplicationConfig` la crea como bean.

| Opción | Veredicto |
|--------|-----------|
| `@Transactional` en el método del caso de uso | Elegida: un import, reconocible de inmediato por cualquier lector |
| Un decorador construido con `TransactionTemplate` en la configuración | Rechazada: mantiene la capa de aplicación libre de Spring, pero agrega una interfaz y una clase cuyo único trabajo es evitar una anotación |
| `@Transactional` en el adaptador de persistencia | Rechazada: la comprobación de existencia y el guardado correrían en transacciones separadas |
| En el controlador | Rechazada: mezcla HTTP con consistencia |

**Qué garantiza la anotación y qué no.**

- Garantiza que `existsByEmail`, el insert de `users` y los de `phones` ocurren en una sola transacción:
  si algo falla después de insertar el usuario, la fila del usuario se deshace con las demás.
- No es lo que garantiza la unicidad del correo. Dos solicitudes concurrentes pueden pasar ambas la
  comprobación; la restricción `UNIQUE` de la base de datos es la que rechaza a la segunda, y el
  adaptador traduce esa violación a un 409.
- Las pruebas de atomicidad (un teléfono rechazado por la base no deja ni el usuario ni ningún
  teléfono) pasarían también sin la anotación, porque `saveAndFlush` de Spring Data es transaccional por
  sí mismo y el usuario y sus teléfonos se guardan en esa misma llamada. Una mutación que quite
  `@Transactional` la detectan solo las pruebas estructurales: la que comprueba que el bean es un proxy
  transaccional de la clase y la que fija que la clase no es `final` y que el método es público. La
  anotación fija el límite de la operación completa; el comportamiento observable de hoy no depende de
  ella.

**Consecuencias.**

- La concesión está acotada por una lista de permitidos de ArchUnit: el paquete `application` solo
  puede depender de `domain`, de sí mismo, de `java.*` y de `org.springframework.transaction.annotation`.
  Una prueba muestra la regla rechazando un `@Service`, un logger y un acceso a `infrastructure`.
- La clase debe seguir sin ser `final` y el método público, porque la anotación funciona mediante un
  proxy. Una prueba fija ambos.
- El hash se calcula dentro de la transacción. Es irrelevante a esta escala y queda registrado aquí.
- La línea de log de éxito vive en el controlador porque la lista de permitidos no deja ningún logger
  en la capa de aplicación.

## ADR-005: Un modelo de persistencia separado con mapeo de solo escritura

**Contexto.** El agregado de dominio `User` es inmutable y se valida al construirse. JPA quiere un
constructor sin argumentos, campos mutables y clases no finales.

**Decisión.** `UserJpaEntity` y `PhoneJpaEntity` viven en `infrastructure.persistence` y están
separadas del modelo de dominio. El mapeo va en un solo sentido, de `User` a la entidad
(`UserJpaEntity.from`). No hay mapeo de vuelta, porque nada lee un usuario en este servicio.

- Los teléfonos son un `@OneToMany(mappedBy, cascade = ALL, orphanRemoval = true)` bidireccional con un
  `@ManyToOne` perezoso, ordenados por su clave de identidad para que el orden enviado sobreviva a una
  recarga.
- `users.id` lo asigna la aplicación (ADR-020), así que la entidad implementa `Persistable` con una
  marca transitoria de "nueva". Sin ella, Spring Data trataría un id asignado como una fila existente y
  emitiría un `SELECT` antes de cada inserción; una prueba comprueba que no hay ninguna consulta previa.

**Alternativas descartadas.**

- Anotar el agregado: el dominio importaría `jakarta.persistence`, perdería su inmutabilidad y
  necesitaría un constructor sin argumentos.
- `@ElementCollection` para los teléfonos: una tabla sin clave primaria.
- Una colección unidireccional con `@JoinColumn`: un insert y un update por teléfono.

**Consecuencias.** Hay código de mapeo que escribir y probar, pero es de solo escritura y corto. Como
la respuesta se construye desde el agregado en memoria y no desde una recarga, los datos devueltos y los
almacenados son idénticos por construcción, lo que una prueba también verifica contra la fila guardada.

## ADR-006: La validación vive en el dominio

**Contexto.** Una solicitud puede romper varias reglas a la vez, y el contrato de errores pide todos
los campos erróneos en un solo mensaje, como máximo un mensaje por campo, tomando la primera regla que
falla en este orden: obligatorio, largo, formato. Los dos formatos (correo y contraseña) son
configuración en tiempo de ejecución.

**Decisión.** Cada regla es una función pura del dominio que devuelve un motivo tipado, `Reason`, un enum del paquete
`domain.model` (`Email.violation`, `User.nameViolation`, `Password.violation`,
`User.phoneListViolations`, que a su vez usa `Phone.violations`). Las reglas de la lista de teléfonos
(una lista ausente es válida, una lista con demasiadas entradas se rechaza sin mirar las entradas, una
entrada nula tiene su propio motivo) viven en el dominio, no en el caso de uso. El caso de uso reúne los
motivos en un conjunto, y un conjunto no vacío se convierte en una sola `InvalidUserDataException`
antes de tocar el repositorio: orquesta y no decide nada. Los value objects llaman a las mismas
funciones en sus constructores, de modo que un `Email` no puede existir en estado inválido si se
construye por otro camino. El dominio no lleva texto para el cliente: `ErrorMessages`, en la capa web,
asigna a cada motivo su mensaje. Los records de solicitud no llevan anotaciones de Bean Validation.

| Opción | Veredicto |
|--------|-----------|
| Reglas como funciones puras en el dominio, reunidas por el caso de uso | Elegida: una definición por regla, el orden es código simple, las pruebas de dominio no necesitan framework |
| Restricciones integradas de Bean Validation | Rechazada: todas las restricciones de un campo se disparan juntas y sin orden, así que un correo vacío reportaría "obligatorio" y "formato" a la vez |
| `@GroupSequence` (obligatorio, luego largo, luego formato) | Rechazada: la secuencia es global, así que cuando un campo falla el primer grupo se omiten los grupos siguientes para todos los campos |
| Una restricción personalizada por campo que delega al dominio | Rechazada: la misma lógica envuelta en ocho tipos de anotación con patrones inyectados por Spring |

**Consecuencias.**

- El largo siempre precede al patrón, así que un valor desmesurado nunca llega al motor de expresiones
  regulares. Una prueba envía un correo de 50.000 caracteres contra un patrón propenso a backtracking
  catastrófico y exige una respuesta en menos de cinco segundos.
- Las reglas de "obligatorio" y de largo en bytes de la contraseña están en `Password`, una clase final
  con una función estática, fuera del Strategy: ninguna implementación de `PasswordPolicy` puede
  saltárselas, y el límite de 72 bytes se mantiene sea cual sea el patrón que configure un operador.
- Bean Validation se sigue usando para los dos records de configuración, de modo que una propiedad
  incorrecta detiene el arranque.

## ADR-007: Tipado estricto de strings en JSON

**Contexto.** Por defecto el mapeador JSON convierte `"name": 123` en el string `"123"`. Un cliente que
envía el tipo equivocado debe recibir un 400, no un valor convertido en silencio.

**Decisión.** `JacksonConfig` registra un customizer del mapeador que hace fallar la coerción de un
entero, un decimal o un booleano a una propiedad de texto. Los arreglos y objetos enviados donde va un
string, y un escalar enviado como entrada de teléfono, ya fallan por defecto.

Un cuerpo con tipos incorrectos falla durante el enlace, antes de que se ejecute el controlador o
cualquier regla de dominio, así que la respuesta es el mensaje genérico de cuerpo y nunca un mensaje de
campo.

**Alternativas descartadas.** Un deserializador propio por campo (repetido en seis campos y fácil de
olvidar en uno nuevo); leer el cuerpo como árbol e inspeccionarlo a mano (se salta el enlace de datos y
el esquema publicado).

**Consecuencias.** Una sola clase cubre todos los campos de texto. Un string de dígitos como `"123"`
sigue siendo un string válido. Un `null` JSON llega al caso de uso como valor ausente.

## ADR-008: El contrato de error `{"mensaje"}` en lugar de RFC 9457

**Contexto.** Spring Boot puede responder los errores como problem details de RFC 9457. El enunciado
fija otra forma: un objeto JSON con una sola clave `mensaje` y el texto exacto
`El correo ya registrado` para un correo duplicado.

**Decisión.** Todo error responde `{"mensaje": "<texto>"}` con un tipo de contenido JSON. Un solo
`@RestControllerAdvice`, `GlobalExceptionHandler`, extiende `ResponseEntityExceptionHandler`, de modo
que cada excepción estándar de Spring MVC pasa por un método sobrescribible y la forma se reemplaza en
un solo lugar. El enunciado fija únicamente el texto del duplicado; los demás mensajes están escritos
en español para ser coherentes con él, figuran en `ErrorMessages` y nunca contienen el valor rechazado.

**La regla del 406.** Si un cliente envía `Accept: application/xml`, escribir el cuerpo del error se
negociaría contra ese encabezado, volvería a fallar y terminaría en un 406 vacío. Toda respuesta de
error fija un `Content-Type` concreto, lo que hace que Spring omita la negociación y escriba JSON. Una
prueba falla sin ello.

**Alternativas descartadas.** `spring.mvc.problemdetails.enabled`: es estándar, pero el enunciado
exige la otra forma. Un bean `ErrorAttributes` propio: el controlador de errores de serie sigue
negociando, así que un navegador recibiría HTML y un 406 un cuerpo vacío.

**Consecuencias.** La forma del error no es la estándar, y el servicio lo dice en lugar de mezclar
ambas. El documento OpenAPI, Swagger UI y la consola H2 son herramientas, no respuestas de la API, y
conservan sus propios formatos.

## ADR-009: Mapeo de estados

| Situación | Estado | `mensaje` |
|-----------|--------|-----------|
| Registrado | 201 | (el usuario) |
| Uno o más campos incumplen una regla | 400 | los mensajes de los campos erróneos, distintos, ordenados y unidos con `"; "` |
| Cuerpo ilegible, mal formado, vacío o con tipos incorrectos | 400 | `El cuerpo de la solicitud no es válido` |
| Cualquier otro 400 producido dentro de la aplicación | 400 | `La solicitud no es válida` |
| Ruta desconocida | 404 | `Recurso no encontrado` |
| Método no permitido en una ruta conocida (se conserva el encabezado `Allow`) | 405 | `Método no permitido` |
| No se puede satisfacer `Accept` | 406 | `Formato de respuesta no aceptable` |
| `Content-Type` no es JSON | 415 | `Tipo de contenido no soportado` |
| Correo ya registrado | 409 | `El correo ya registrado` |
| Cualquier otro 409 sin rechazo tipado | 409 | `La solicitud entra en conflicto con el estado actual del recurso` |
| Cualquier cosa inesperada | 500 | `Error interno del servidor` |

**Decisiones dentro de la tabla.**

- 409 para un duplicado, no 400 ni 422: el conflicto es con el estado del recurso, no con la forma de
  la solicitud.
- Los mensajes se ordenan por el texto en español y se unen con `"; "`, de modo que la respuesta es
  determinista y una prueba puede compararla exactamente. Un motivo repetido aparece una sola vez.
- El cuerpo del 500 es texto fijo. El log del servidor recibe la clase y los frames de la traza de la
  falla y de cada causa, nunca un mensaje de excepción, porque un mensaje puede citar la solicitud (una
  base de datos informa el valor que rechazó). Una prueba hace que un mensaje de base de datos cite un
  valor marcador y exige que ese marcador no aparezca en el log.
- Cualquier 4xx sin fila específica responde `La solicitud no es válida` y conserva su estado; un 413 o
  un 431 no se anuncia como error interno.

**Consecuencias.** El mismo mapeo lo comparten el advice y el controlador de errores mediante
`ErrorMessages.forStatus`, de modo que los dos no pueden divergir.

## ADR-010: Un controlador de errores para los errores reenviados a la ruta de error

**Contexto.** Algunos errores nunca llegan a un método manejador de Spring MVC: un filtro que llama a
`sendError`, o cualquier error que el contenedor de servlets reenvía a su página de error. El
manejador `/error` de serie de Boot negocia su contenido, así que puede responder una página HTML o un
cuerpo vacío.

**Decisión.** `ApiErrorController` reemplaza el manejador `/error` de Boot. Los errores reenviados a
`/error` reciben el mismo cuerpo JSON que cualquier otro error, para todo método y todo valor de
`Accept`, con un tipo de contenido JSON explícito. Un `GET /error` directo, sin estado reenviado, es una
solicitud de una página inexistente y se responde con 404. El controlador está oculto del documento
OpenAPI.

**Alternativas descartadas.**

- Una válvula de reporte de errores de Tomcat, para responder también en JSON las solicitudes que
  Tomcat rechaza antes de elegir una aplicación web (el caso era `GET /api/v1/users/%zz`). Se eliminó:
  es específica de Tomcat, depende del orden de las válvulas en el pipeline del host de Boot y solo
  podía cubrir una parte de los rechazos del contenedor, porque el parser HTTP responde una línea de
  solicitud mal formada o encabezados demasiado grandes antes de que corra cualquier válvula. Dos
  clases, una regla de orden y pruebas por socket crudo compraban una garantía parcial, así que el
  contrato declara su límite en su lugar.
- Solo `ErrorAttributes`: ver ADR-008.

**Consecuencias.**

- **Límite.** Una solicitud que el contenedor de servlets rechaza antes de que corra código de la
  aplicación (una secuencia de porcentaje inválida en la ruta, una línea de solicitud mal formada,
  encabezados demasiado grandes) se responde con la página de error propia del contenedor, normalmente
  HTML, y queda fuera del contrato `mensaje`. Está registrado en las limitaciones conocidas y en el
  README.
- El reenvío se prueba por HTTP real, porque MockMvc no realiza el despacho de errores del contenedor:
  `ErrorPathTest` registra un filtro que existe solo en esa prueba, llama a `sendError` para una ruta
  marcadora (503, 500, 400, 401, 404 y 409; el 503 también con `Accept: text/html` y con `POST`) y
  comprueba el estado, el tipo de contenido JSON y el cuerpo `mensaje` de la respuesta. La rama de un atributo de
  estado que no es un entero, que el contenedor nunca produce, se prueba con una prueba unitaria
  (`ApiErrorControllerTest`).

## ADR-011: Spring Boot 4.1.1 y Java 17 frente al "Java 8+" del enunciado

**Contexto.** El enunciado pide Java 8 o superior. Spring Boot 3 y 4 necesitan al menos Java 17. La
última línea de Boot que corre en Java 8 (2.7) ya no recibe actualizaciones de código abierto.

**Decisión.** Spring Boot 4.1.1 sobre un toolchain de Java 17. Java 17 es "8 o superior" y es la
versión más baja en la que corre una línea de Boot con soporte, de modo que el servicio queda sobre un
framework mantenido sin exigir un JDK más nuevo de lo necesario. El toolchain de Gradle hace que el
build use JDK 17 sea cual sea el JDK que inicia Gradle, y el plugin `foojay-resolver-convention` de
`settings.gradle` permite que Gradle descargue un JDK 17 cuando no hay ninguno instalado.

**Alternativas descartadas.** Boot 2.7 sobre Java 8: coincide literalmente con el enunciado, pero
comienza el proyecto sobre una línea sin mantenimiento. Un Java más nuevo: sube innecesariamente el
mínimo.

**Consecuencias.**

- Quien revise necesita cualquier JDK 17 o superior para iniciar Gradle, que descarga un toolchain de
  JDK 17 si no hay ninguno instalado, o Docker. El README lo dice primero.
- Error Prone está fijado en 2.42.0, la última línea que corre en JDK 17 (ADR-021).
- Algunos tipos de terceros no han alcanzado la generación de Boot 4. JJWT todavía trae Jackson 2
  (ADR-014).

## ADR-012: Gradle como herramienta de build

**Contexto.** El enunciado no nombra una herramienta de build.

**Decisión.** Gradle con DSL Groovy, mediante el wrapper versionado (9.7.1). Un solo
`./gradlew build` compila con Error Prone, verifica el formato, ejecuta todas las pruebas y aplica el
umbral de cobertura. El plugin de Spring Boot para Gradle construye el jar ejecutable, llamado
`app.jar` para que el punto de entrada de la imagen no cambie con la versión del proyecto.

**Alternativas descartadas.** Maven habría funcionado igual de bien. Se eligió Gradle por preferencia:
el wrapper y el toolchain de Java seleccionan el JDK de forma reproducible, y los plugins usados aquí
(Spring Boot, JaCoCo, Spotless, Error Prone) se configuran en un archivo de build corto. No es una
necesidad técnica.

**Consecuencias.** Para iniciar el build solo hace falta un JDK 17 o superior: el wrapper descarga la
versión exacta de Gradle y el resolvedor de toolchains descarga un JDK 17 si no hay ninguno instalado.
El Dockerfile usa el mismo wrapper, así que la imagen y el build local usan un mismo toolchain.

## ADR-013: H2, Hibernate y un `schema.sql` versionado con `validate`

**Contexto.** El enunciado pide una base de datos en memoria y un script que la cree.

**Decisión.**

- H2 en memoria, con la consola de H2 habilitada para inspección, y Hibernate como proveedor JPA. La
  consola solo acepta conexiones locales, así que funciona con `./gradlew bootRun`; con
  `docker run -p` se sirve la página pero rechaza la conexión.
- El esquema es un script versionado, `src/main/resources/schema.sql`, que Spring ejecuta al arrancar.
  Hibernate solo lo valida: `spring.jpa.hibernate.ddl-auto=validate`. El ajuste es explícito porque,
  con una base embebida, Boot usaría por defecto `create-drop`, lo que volvería decorativo al script.
- `spring.jpa.open-in-view=false`, de modo que ninguna sesión de persistencia sobrevive al caso de uso.
- Las marcas de tiempo son `TIMESTAMP(6) WITH TIME ZONE`, de modo que el valor almacenado es un
  instante UTC con precisión de microsegundos.

**Qué verifica `validate` y qué no.** Verifica que cada tabla y columna mapeada exista y que el tipo de
columna sea compatible con el tipo Java mapeado. Una columna declarada `INTEGER` donde se mapea un
string hace fallar el arranque. **No** distingue `TIMESTAMP` de `TIMESTAMP WITH TIME ZONE`. Esa parte
del esquema se demuestra con pruebas que hacen ida y vuelta con instantes de precisión de microsegundos y
comparan el valor almacenado con la respuesta.

**Alternativas descartadas.** `create-drop`: no hay un script revisable. Flyway o Liquibase: correctos
para un servicio real con base persistente, pero una dependencia de más para un script en memoria.

**Consecuencias.** El script es la única fuente del esquema y lo verifican pruebas que arrancan contra
él (columnas en su largo máximo, la restricción `UNIQUE` del correo, la clave foránea de teléfonos a
usuarios). El script es idempotente (`IF NOT EXISTS`). Los datos no sobreviven a un reinicio. Pasar a
una base persistente agregaría una herramienta de migración y reemplazaría H2.

## ADR-014: JJWT con Jackson 2 junto a Jackson 3

**Contexto.** Spring Boot 4.1 serializa JSON con Jackson 3 (`tools.jackson`). JJWT 0.13.0 serializa sus
claims con Jackson 2 (`com.fasterxml.jackson`) mediante su módulo `jjwt-jackson`.

**Decisión.** Usar JJWT y dejar coexistir las dos generaciones de Jackson. No interactúan: Spring MVC
usa Jackson 3 para solicitudes y respuestas, y JJWT usa Jackson 2 de forma privada para escribir los
claims del token. Las anotaciones que usan los records (`@JsonProperty`, `@JsonPropertyOrder`) siguen
en el paquete `com.fasterxml.jackson.annotation` también para Jackson 3.

El token es HS256 (un servicio lo emite y nada lo verifica). Los claims son `sub` (el id del usuario),
`email`, `iat` y `exp`. La clave de firma se construye con los bytes crudos del secreto, y el
constructor de `JjwtTokenIssuer` rechaza un secreto de menos de 32 bytes o una expiración no positiva
con un mensaje que nombra la propiedad y nunca el valor, de modo que la aplicación no arranca en lugar
de responder 500 en el primer registro.

**Alternativas descartadas.**

- RS256 o ES256: correcto cuando otra parte verifica los tokens; aquí sería gestión de claves sin
  consumidor.
- Aplicar un hash a una frase arbitraria para obtener 256 bits: convertiría un secreto de cuatro
  caracteres en una clave "válida" y ocultaría la debilidad.
- `jjwt-gson` o código JWT escrito a mano: posible, pero JJWT es la elección común y una prueba
  demuestra que el par funciona.

**Consecuencias.** Dos generaciones de Jackson están en el classpath de ejecución. Una prueba de
contexto completo interpreta con JJWT un token de una respuesta escrita por Jackson 3, de modo que la
coexistencia se verifica en cada build. Eliminar Jackson 2 más adelante implicaría reemplazar
`jjwt-jackson`.

## ADR-015: BCrypt mediante `spring-security-crypto`, sin el starter de seguridad

**Contexto.** La contraseña debe almacenarse como un hash con sal y nunca devolverse.

**Decisión.** `BCryptPasswordHasher` usa `BCryptPasswordEncoder` con costo 12 de
`spring-security-crypto`, el módulo pequeño sin dependencia web ni de servlets.
`spring-boot-starter-security` no se agrega: su cadena de filtros por defecto respondería 401 fuera del
contrato de errores, y la protección CSRF bloquearía el `POST`.

**Alternativas descartadas.** El starter de seguridad con una configuración permisiva (una superficie
mayor que configurar y en la que equivocarse para una sola función de hash); Argon2 (más fuerte en el
papel, necesita una biblioteca nativa o adicional, y BCrypt es adecuado aquí).

**Consecuencias.** BCrypt solo usa los primeros 72 bytes de una contraseña, así que el dominio rechaza
las más largas con un 400 en lugar de truncarlas en silencio (ADR-006). La clase que envuelve el
codificador es el único lugar que conoce el algoritmo.

## ADR-016: El token se persiste en claro y la clave de firma es efímera salvo que se configure

**Contexto.** El enunciado exige que el token se persista junto con el usuario. Un token almacenado en
claro es una credencial al portador en reposo, algo que normalmente se evitaría. El servicio además debe
correr sin preparación, pero este repositorio es público, así que cualquier secreto de firma que se
suba a él, o se incorpore a la imagen, es un secreto que todos conocen.

**Decisión.** El token se almacena tal como se emite, en `users.token VARCHAR(1024)`. Una prueba
comprueba que el token más largo posible (un correo de 254 caracteres) cabe. El token no se valida en
ninguna solicitud, de modo que nada del servicio depende del valor almacenado.

El secreto y la expiración son propiedades: `app.token.secret` (variable de entorno `TOKEN_SECRET`) y
`app.token.expiration` (15 minutos por defecto, debe ser positiva). **No se distribuye ningún
secreto.** Cuando `app.token.secret` está ausente o vacío, `JjwtTokenIssuer` genera una clave aleatoria
de 256 bits con `SecureRandom` al arrancar y registra una línea `INFO` que indica que se usa una clave
de firma efímera y que los tokens no sobrevivirán a un reinicio; la clave nunca se registra. Cuando hay
un secreto configurado se aplican las reglas de siempre: al menos 32 bytes, de lo contrario el arranque
falla con un mensaje que nombra la propiedad y nunca el valor.

| Opción | Veredicto |
|--------|-----------|
| Una clave aleatoria en cada arranque salvo que se configure un secreto | Elegida: el servicio corre de inmediato, no se publica nada utilizable y un secreto configurado demasiado corto sigue haciendo fallar el arranque |
| Un valor por defecto publicado solo para desarrollo | Rechazada: en un repositorio público y en la imagen es una clave de firma que cualquiera puede usar |
| Sin valor por defecto y arranque fallido sin secreto | Rechazada: quien revisa no puede simplemente ejecutarlo, y nada verifica los tokens de todos modos |

**Consecuencias.** Sin `TOKEN_SECRET`, los tokens se firman con una clave que existe solo en el
proceso, de modo que no se pueden verificar tras un reinicio ni desde otra instancia. Nada en el
servicio verifica un token, así que esto no afecta ningún comportamiento aquí; un despliegue que
entregue tokens a un consumidor debe fijar `TOKEN_SECRET`. Una base de datos filtrada filtra tokens
válidos hasta que expiran. Almacenar un hash del token contradiría el requisito de que se persista para
uso posterior, así que no se hace. Los secretos no se registran en ningún nivel: los records que llevan
una contraseña, un token o un secreto los ocultan en `toString()`, la única línea de log de éxito usa un
correo enmascarado, y las pruebas capturan la salida en DEBUG y TRACE y buscan la contraseña, el hash,
el token y el secreto. Una prueba también falla si las fuentes principales contienen el antiguo
secreto de desarrollo.

## ADR-017: Un Strategy para el formato de la contraseña, con un valor por defecto débil a propósito

**Contexto.** El enunciado hace configurable la regla de contraseña, y su contraseña de ejemplo es
`hunter2`.

**Decisión.** `PasswordPolicy` es una interfaz (un Strategy) con un único método, `isSatisfiedBy`, que
decide solo el formato. `RegexPasswordPolicy` la implementa, construida desde
`app.registration.password-pattern`. El valor por defecto es

```
^(?=.*[A-Za-z])(?=.*[0-9])\S{7,72}$
```

Al menos una letra y un dígito, de 7 a 72 caracteres sin espacios en blanco. Acepta `hunter2` y rechaza
`abc12`. Es débil a propósito, porque el ejemplo del enunciado debe pasar.

**Endurecimiento.** Una propiedad lo reemplaza. Por ejemplo, doce o más caracteres con una minúscula,
una mayúscula, un dígito y un símbolo:

```
^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9\s])\S{12,72}$
```

**Por qué un Strategy aquí.** Se justifica por lo que hace. La contraseña tiene dos tipos de regla:
límites fijos, que no dependen de la configuración (es obligatoria y cabe en 72 bytes UTF-8, el límite
con el que trabaja BCrypt), y un formato reemplazable, que el enunciado pide "configurable". La
interfaz separa ambos: `Password.violation` aplica los límites fijos en este orden, obligatorio, largo,
y solo entonces consulta al `PasswordPolicy` el formato. Los límites viven en una clase final con una
función estática, no en un método por defecto de la interfaz, porque un método por defecto lo puede
sobrescribir una implementación y saltarse el límite. Dicho con franqueza: **hay una sola implementación
de producción**, `RegexPasswordPolicy`, y la interfaz no la fuerza una segunda implementación; las
pruebas la usan con expresiones regulares simples y con contadores de llamadas.

**Por qué el correo no tiene lo mismo.** El correo usa un `Pattern` simple, recibido como parámetro de
`Email.violation` y de `Email.of`. La asimetría tiene dos razones. El enunciado pide que sea
configurable la regla de la contraseña y no la del correo. Y la contraseña tiene límites fijos que deben
quedar fuera de lo reemplazable, mientras que en el correo lo único reemplazable es el formato: su largo
máximo y su normalización ya viven en `Email`, de modo que no hay nada que separar y una interfaz
`EmailPolicy` sería una abstracción más para lo mismo.

**Alternativas descartadas.** Un campo `Pattern` en el caso de uso: funciona, pero deja los límites
fijos y el formato mezclados y sin un punto de extensión con nombre. Un método por defecto en la
interfaz que aplique los límites: era el diseño anterior; un implementador lo podía sobrescribir.

**Consecuencias.** El valor por defecto acepta contraseñas débiles; la debilidad está documentada en el
README y aquí. Una expresión regular inválida detiene el arranque con un mensaje que nombra la
propiedad. Los límites de 72 caracteres y de 72 bytes los aplica `Password` sea cual sea el patrón.

## ADR-018: El `contrycode` literal

**Contexto.** El enunciado escribe el campo de país del teléfono como `contrycode`.

**Decisión.** El campo se escribe exactamente así en solicitudes, respuestas y el documento OpenAPI. El
componente Java es `countryCode` con `@JsonProperty("contrycode")`, y la columna es `country_code`. Se
conserva la errata porque es el contrato en el cable, no se corrige en silencio.

**Alternativas descartadas.** Aceptar ambas grafías: duplica el contrato por una errata, y un cliente
nunca sabría cuál es la real.

**Consecuencias.** Un cliente que envía `countrycode` lo ve ignorado como propiedad desconocida, y el
`contrycode` obligatorio se reporta entonces como ausente.

## ADR-019: Normalización del correo y límites de campos

**Decisión.**

- El correo se pasa a minúsculas con `Locale.ROOT` (para que un locale por defecto turco no lo altere)
  y se almacena en minúsculas. Dos direcciones que difieren solo en mayúsculas son la misma dirección,
  lo que hace la unicidad insensible a mayúsculas. El límite de largo se mide después de pasar a
  minúsculas, porque algunos caracteres se expanden.
- El correo **no se recorta**. Un valor con un espacio inicial o final no está en blanco, llega al
  patrón y lo incumple. El patrón se aplica con `matches()`, así que un salto de línea final se rechaza
  aunque `$` solo lo toleraría. Por eso los patrones se escriben en minúsculas, ya que se aplican al
  valor en minúsculas.
- Los nombres y los campos de teléfono se almacenan exactamente como se reciben.
- Los campos de teléfono son strings, de modo que los ceros iniciales sobreviven. `number` y `citycode`
  contienen solo dígitos ASCII; `contrycode` contiene dígitos ASCII con un `+` inicial opcional, que
  cuenta para su límite. El ejemplo del enunciado (`"1234567"`, `"1"`, `"57"`) es válido. Cada campo se
  verifica en el orden obligatorio, largo, formato, con un motivo tipado propio
  (`PHONE_NUMBER_FORMAT`, `CITY_CODE_FORMAT`, `COUNTRY_CODE_FORMAT`). El esquema OpenAPI publica los
  mismos patrones, que provienen de constantes de `Phone`.
- `phones` puede estar ausente, ser nulo o vacío; se devuelve como `[]`.

| Campo | Límite |
|-------|--------|
| `name` | 255 caracteres |
| `email` | 254 caracteres (tras pasar a minúsculas) |
| `password` | 72 caracteres y 72 bytes UTF-8 |
| `phones` | 10 entradas |
| `number` | 20 caracteres |
| `citycode`, `contrycode` | 10 caracteres cada uno |

Los límites son constantes del dominio. La validación, los largos de columna JPA y el `maxLength` de
OpenAPI las referencian, y `schema.sql` las replica; las pruebas almacenan cada columna en su largo
máximo contra el script, de modo que una discrepancia hace fallar el build.

**Alternativas descartadas.** `jakarta.validation.constraints.Email` acepta `juan@dominio`, que la regla
de formato rechaza. Recortar la entrada oculta un error del cliente.

**Consecuencias.** Los límites son supuestos, porque el enunciado no da ninguno. Todo campo tiene una
cota, de modo que una entrada hostil no puede llegar sin límite al motor de expresiones regulares ni a
la base de datos.

## ADR-020: Identificador generado por la aplicación y una única lectura del reloj

**Decisión.**

- El id del usuario es un UUID aleatorio generado por la aplicación (`UserId.generate()`), porque el
  claim `sub` del token necesita el id antes de que exista la fila. Un id generado por la base de datos
  requeriría una segunda escritura para almacenar el token.
- El caso de uso lee el reloj **una vez**: `clock.instant()` truncado a microsegundos. Ese único valor
  se pasa al emisor de tokens y al builder, que lo asigna a `created`, `modified` y `last_login`. Los
  microsegundos son la precisión de las columnas `TIMESTAMP(6)`, de modo que la respuesta, el token y la
  fila almacenada muestran el mismo instante, y `iat` es ese instante con precisión de segundo.
- El reloj es un bean `java.time.Clock`, de modo que las pruebas lo fijan.

**Consecuencias.** No hay deriva entre la respuesta y la base de datos. La lectura del reloj por parte
del caso de uso está fijada por una prueba que usa un reloj que avanza en cada lectura.

## ADR-021: Compuertas de calidad

| Compuerta | Qué hace cumplir | Notas |
|-----------|------------------|-------|
| JaCoCo | Al menos 80 % de cobertura de líneas sobre `domain` y `application` | El valor medido está en el informe de JaCoCo de cada build. La infraestructura se ejercita con pruebas de corte y de contexto completo, pero no tiene umbral: un porcentaje sobre código de cableado invita a pruebas de configuración |
| Spotless | Google Java Format 1.28.0, sin imports sin usar, sin espacios finales | `spotlessCheck` corre dentro de `check`; `./gradlew spotlessApply` lo corrige |
| Error Prone | Análisis estático dentro del compilador de Java | Fijado en 2.42.0, la última línea que corre en JDK 17 (ADR-011). Junto con `-Xlint:all -Werror` en las fuentes principales |
| ArchUnit (núcleo) | Reglas de capas y de dependencias | Las ocho reglas listadas abajo |
| `.editorconfig` | UTF-8, LF, salto de línea final, indentación | Compartido por los editores |

Las reglas de ArchUnit:

1. `domain` no importa nada de Spring, Jakarta, Hibernate, ninguna generación de Jackson, JJWT ni
   Swagger.
2. `application` depende solo de `domain`, de sí misma, de `java.*` y de la anotación de transacción.
3. Las capas apuntan hacia adentro: el dominio no depende de `application` ni de `infrastructure`, y la
   aplicación no depende de `infrastructure`.
4. `web`, `persistence`, `security` y `config` no dependen entre sí.
5. Ninguna clase usa inyección en campos.
6. Las clases `@Entity` residen en `infrastructure.persistence`.
7. Ninguna clase de `web` usa una entidad JPA.
8. Ninguna clase de `web` depende de las clases del paquete `application`; solo ve el puerto de entrada
   (`application.port`).

**Una regla que no puede fallar no es una regla.** Una segunda clase de pruebas entrega a cada regla
una clase de fixture escrita para romperla (en un paquete de pruebas aparte) y exige que la regla falle
nombrando esa clase. Un conjunto vacío de infractores pasaría en vacío, como ocurre con un patrón de
paquete mal escrito; el propio fallo "no classes selected" de ArchUnit cubre ese caso también.

La biblioteca núcleo de ArchUnit se usa desde pruebas ordinarias; `archunit-junit5` no, porque su motor
apunta a la plataforma JUnit 5 y Boot 4.1.1 administra JUnit 6.

**Consecuencias.** `./gradlew build` es la única compuerta y CI la ejecuta. Las compuertas cuestan
tiempo de build y una compilación más estricta, que es el propósito.

## ADR-022: Lo que se dejó fuera deliberadamente

| Se dejó fuera | Razón |
|---------------|-------|
| Endpoint de login y validación de tokens en las solicitudes | No se pidió; el token solo se emite y se almacena |
| Actualizar, borrar y listar usuarios | No se pidió; el puerto del repositorio tiene dos métodos |
| Starter de Spring Security | Ver ADR-015 |
| Actuator y endpoints de salud | No se pidió; la imagen no tiene `HEALTHCHECK` a propósito, de modo que quien ejecuta el contenedor decide cómo sondearlo |
| Flyway o Liquibase | Ver ADR-013 |
| Bibliotecas de mapeo, Lombok | El mapeo es pequeño; la respuesta es un record, así que un campo olvidado es un error de compilación |
| Eventos de dominio, CQRS, Factory, Observer | Nada en el servicio los requiere |
| Encabezado `Location` en el 201 | No hay `GET` para el recurso; un enlace a un 404 induciría a error |
| Límite de tasa, TLS, CORS | Asuntos de despliegue, fuera del ejercicio |
| Pruebas de mutación, escaneo de vulnerabilidades de dependencias | Fuera del ejercicio; vale la pena agregarlos en un pipeline real |

---

## Componentes

| Componente | Responsabilidad | Por qué existe |
|------------|-----------------|----------------|
| `User` (+ `Builder`) | Agregado de un usuario registrado; inmutable tras `build()`; verifica sus invariantes | Un solo lugar que dice qué es un usuario registrado válido; el builder nombra cada valor |
| `UserId` | Identificador tipado alrededor de un UUID | Evita que un id se mezcle con otro string; se genera antes de que exista la fila |
| `Email` | Value object: en minúsculas, acotado, con formato verificado, enmascarado para logs | Una sola definición de normalización y de unicidad |
| `Phone` | Value object: número, código de ciudad, código de país | Mantiene juntos los tres strings con sus límites y su formato |
| `Reason` | Enum de los motivos tipados de rechazo, en `domain.model` | El dominio dice qué regla se incumplió sin llevar texto ni el valor rechazado |
| `Password` | Límites fijos de la contraseña (obligatoria, 72 bytes) y composición con el formato | Ninguna implementación del Strategy puede saltarse los límites (ADR-017) |
| `PasswordPolicy`, `RegexPasswordPolicy` | Strategy del formato de la contraseña; la expresión regular viene de la configuración | El enunciado exige que el formato sea configurable (ADR-017) |
| `PhoneInput` | Las tres partes de un teléfono tal como se enviaron, antes de validarlas | Permite al dominio validar una lista de teléfonos sin conocer el tipo del llamador |
| `InvalidUserDataException`, `EmailAlreadyRegisteredException` | Rechazos de dominio tipados; sin texto para el cliente | El dominio dice qué salió mal, la capa web decide cómo decirlo |
| `UserRepository`, `PasswordHasher`, `TokenIssuer` | Puertos de salida | El caso de uso se prueba sin base de datos, BCrypt ni JJWT (ADR-002) |
| `RegisterUser`, `RegisterUserUseCase`, `RegisterUserCommand` | El puerto de entrada, su implementación y el comando; la implementación orquesta el registro y es dueña de la transacción | El único servicio de aplicación; el adaptador web depende de la interfaz (ADR-003); el comando oculta la contraseña en `toString()` |
| `UserController`, `UserApi` | HTTP a comando a respuesta; la interfaz lleva las anotaciones OpenAPI | Mantiene el controlador en pocas líneas |
| `RegisterUserRequest`, `PhoneRequest`, `UserResponse`, `PhoneResponse`, `ErrorResponse` | Records JSON | Contrato explícito en el cable; la respuesta no tiene componente de contraseña |
| `UserWebMapper` | Mapeo estático entre records y comando o agregado | Un campo olvidado es un error de compilación; nunca lee el hash de la contraseña |
| `GlobalExceptionHandler` | El único `@RestControllerAdvice` | Un solo punto de traducción para todo error (ADR-008) |
| `ErrorMessages` | El catálogo de mensajes y las búsquedas por estado | Texto para el cliente en un solo lugar, compartido por los dos caminos de error |
| `ApiErrorController` | Cuerpo JSON para los errores reenviados a la ruta de error | ADR-010 |
| `JacksonConfig` | Tipado estricto de strings | ADR-007 |
| `UserPersistenceAdapter`, `UserJpaRepository`, `UserJpaEntity`, `PhoneJpaEntity` | Implementan `UserRepository` con JPA; traducen una violación de unicidad al rechazo de dominio | ADR-005 y la regla de concurrencia: la restricción, no la comprobación previa, garantiza la unicidad |
| `BCryptPasswordHasher` | Implementa `PasswordHasher` | ADR-015 |
| `JjwtTokenIssuer`, `TokenProperties` | Implementa `TokenIssuer`; enlaza `app.token.*`; hace fallar el arranque con un secreto débil y genera una clave efímera si no hay secreto | ADR-014 y ADR-016 |
| `ApplicationConfig`, `RegistrationProperties`, `OpenApiConfig` | Cableado del caso de uso, la política y el reloj; propiedades tipadas `app.registration.*`; metadatos de OpenAPI | Las clases de dominio y de aplicación no llevan anotación de estereotipo, así que el cableado está aquí |
| `schema.sql` | Crea `users` y `phones` | ADR-013 |

### Interfaces y clases abstractas

Cada interfaz está en una frontera entre capas o es un punto de extensión con nombre. Ninguna se agregó
a value objects, records, entidades, excepciones ni configuración.

| Tipo | Implementaciones |
|------|------------------|
| `RegisterUser` (puerto de entrada, `application.port`) | `RegisterUserUseCase` |
| `UserRepository` (puerto de salida) | `UserPersistenceAdapter`; `InMemoryUserRepository` en las pruebas |
| `PasswordHasher` (puerto de salida) | `BCryptPasswordHasher`; un doble de prueba |
| `TokenIssuer` (puerto de salida) | `JjwtTokenIssuer`; un doble de prueba |
| `PasswordPolicy` (Strategy del formato) | `RegexPasswordPolicy` |
| `PhoneInput` (entrada sin validar de un teléfono) | `RegisterUserCommand.PhoneData` |
| `UserApi` (contrato HTTP y anotaciones OpenAPI) | `UserController` |
| `UserJpaRepository` (Spring Data) | Generada por Spring Data en tiempo de ejecución |
| `DomainException` (clase abstracta) | `InvalidUserDataException`, `EmailAlreadyRegisteredException` |

---

## Limitaciones conocidas

- **La barra final responde 404.** `POST /api/v1/users/` (con barra final) no coincide con la ruta y
  se responde con el 404 del contrato (`Recurso no encontrado`). El servicio no hace coincidir ambas
  formas.
- **Sin límite de tamaño del cuerpo.** La aplicación no fija un tamaño máximo para el cuerpo de la
  solicitud más allá de los valores por defecto del servidor. Los campos tienen cotas (ADR-019), pero se
  comprueban después de leer el cuerpo.
- **Rechazos a nivel del contenedor.** Una solicitud que el contenedor de servlets rechaza antes de que
  corra código de la aplicación (una secuencia de porcentaje inválida en la ruta, una línea de
  solicitud mal formada, encabezados demasiado grandes) se responde con la página de error propia del
  contenedor, normalmente HTML, y queda fuera del contrato `mensaje` (ADR-010). Todo lo que llega a la
  aplicación, incluidos los errores reenviados a la ruta de error, está cubierto.
- **Clave de firma efímera y token en claro.** Sin `TOKEN_SECRET` la clave de firma es aleatoria y vive
  solo en el proceso, de modo que los tokens no sobreviven a un reinicio. El token se almacena en claro
  porque el enunciado exige que se persista (ADR-016). Fije `TOKEN_SECRET` cuando los tokens deban
  seguir siendo válidos.
- **Patrón de contraseña débil por defecto.** Acepta el ejemplo del enunciado, `hunter2` (ADR-017).
- **Datos en memoria.** Todo se pierde cuando el proceso se detiene.
- **Las herramientas de desarrollo vienen activadas.** La consola H2 (`/h2-console`) y Swagger UI
  exponen la base de datos y la descripción de la API. Se desactivan con
  `spring.h2.console.enabled=false` y `springdoc.swagger-ui.enabled=false` fuera de desarrollo. La
  consola H2 rechaza las conexiones no locales, así que se puede usar con `bootRun` y no mediante
  `docker run -p`.
- **Enumeración de correos.** El 409 de un duplicado le dice a quien llama si una dirección está
  registrada. El enunciado exige esa respuesta.
- **Formato de correo pragmático.** El patrón por defecto no es RFC 5322: acepta direcciones comunes y
  rechaza valores como `juan@dominio`, que no tienen etiqueta de nivel superior. No se envía ningún
  correo de confirmación.
- **Las fallas inesperadas se registran sin sus mensajes.** El manejador del 500 registra solo clases y
  frames de la traza (ADR-009), de modo que un mensaje de base de datos no puede poner datos de la
  solicitud en el log. El costo es que el log no dice por qué falló una sentencia (qué restricción, qué
  columna): lo que tiene quien mantiene son los frames y la clase de la causa raíz. El propio reporte de
  Hibernate de una sentencia fallida está desactivado por la misma razón
  (`logging.level.org.hibernate.orm.jdbc.error=OFF`): cita el mensaje de la base de datos y, en un
  duplicado, registraría la dirección en claro en WARN. La falla en sí se sigue lanzando, traduciendo o
  registrando por el manejador de excepciones.
- **`validate` de Hibernate y zonas horarias.** No distingue `TIMESTAMP` de `TIMESTAMP WITH TIME ZONE`;
  esa parte del esquema se demuestra con pruebas de ida y vuelta (ADR-013).
- **Hash dentro de la transacción.** BCrypt con costo 12 retiene una conexión de base de datos mientras
  dura. Irrelevante a esta escala (ADR-004).
- **Sin autenticación, límite de tasa ni TLS.** El registro está abierto a cualquier llamador.
