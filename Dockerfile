# Las imágenes base se nombran con una etiqueta de versión exacta, nunca con una móvil como "17-jre" o "latest".
# Una etiqueta aún puede volver a publicarse: para una release, añada el digest que informa
# `docker buildx imagetools inspect <image>:<tag>` como <image>:<tag>@sha256:<digest>.

# Etapa 1: compila el jar con el wrapper de Gradle del repositorio y lo divide en capas.
FROM eclipse-temurin:17.0.20.1_1-jdk-noble AS builder
WORKDIR /builder
# Primero el wrapper y los scripts de build, luego una capa que descarga las dependencias
# (`resolveDependencies` resuelve toda configuración, incluidos los classpath de runtime y de pruebas):
# la distribución de Gradle y los jars de dependencias quedan en caché hasta que cambie uno de estos
# archivos, de modo que un cambio bajo src/ no los vuelve a descargar.
COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
RUN ./gradlew --no-daemon resolveDependencies
COPY src/ src/
# Las pruebas se ejecutan en el workflow de CI y en `./gradlew build`; la construcción de la imagen solo empaqueta.
RUN ./gradlew --no-daemon bootJar -x test \
    && java -Djarmode=tools -jar build/libs/app.jar extract --layers --destination extracted

# Etapa 2: imagen de runtime solo con un JRE.
FROM eclipse-temurin:17.0.20.1_1-jre-noble
WORKDIR /application
RUN groupadd --system app && useradd --system --gid app --no-create-home app
# Primero las capas que menos cambian, para que un cambio de código solo reemplace la última.
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./
USER app
EXPOSE 8080
# El heap como porcentaje del límite del contenedor, en lugar de un -Xmx fijo que se desvía de él.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
# No se incluye ningún secreto de firma en la imagen. Sin TOKEN_SECRET la aplicación genera una clave
# aleatoria al arrancar y sus tokens no sobreviven a un reinicio; para mantenerlos válidos, pase un
# secreto de al menos 32 bytes:
#   docker run -e TOKEN_SECRET=... -p 8080:8080 <image>
# Sin instrucción HEALTHCHECK a propósito: quien ejecuta el contenedor decide cómo sondearlo.
ENTRYPOINT ["java", "-jar", "app.jar"]
