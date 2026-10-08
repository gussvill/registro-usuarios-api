package com.registro.usuarios;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import jakarta.persistence.Entity;

/**
 * Las decisiones de capas del diseño, expresadas como reglas. Seleccionan por patrón de paquete (no
 * por el paquete exacto de producción), de modo que la misma regla pueda ejecutarse contra los
 * fixtures escritos para romperla; la prueba de producción solo importa {@code
 * com.registro.usuarios}.
 */
final class ArchitectureRules {

  /** El dominio compila solo con el JDK: sin contenedor, ORM, JSON, JWT ni biblioteca OpenAPI. */
  static final ArchRule DOMAIN_IS_FREE_OF_FRAMEWORKS =
      noClasses()
          .that()
          .resideInAPackage("com.registro..domain..")
          .should()
          .dependOnClassesThat(
              resideInAnyPackage(
                  "org.springframework..",
                  "jakarta..",
                  "org.hibernate..",
                  "com.fasterxml..",
                  "tools.jackson..",
                  "io.jsonwebtoken..",
                  "io.swagger.."))
          .because(
              "las reglas de negocio deben poder probarse sin ningún framework en el classpath");

  /**
   * La capa de aplicación puede leer exactamente una cosa de Spring: la anotación de transacción.
   */
  static final ArchRule APPLICATION_USES_ONLY_THE_DOMAIN_AND_THE_TRANSACTION_ANNOTATION =
      classes()
          .that()
          .resideInAPackage("com.registro..application..")
          .should()
          .onlyDependOnClassesThat(
              resideInAnyPackage(
                  "com.registro..domain..",
                  "com.registro..application..",
                  "java..",
                  "org.springframework.transaction.annotation.."))
          .because(
              "el caso de uso se acota con una lista de permitidos, no con una lista de lo prohibido");

  /**
   * Las dependencias apuntan hacia adentro: infraestructura a aplicación a dominio, nunca al revés.
   */
  static final ArchRule DEPENDENCIES_POINT_INWARDS =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Domain")
          .definedBy("com.registro..domain..")
          .layer("Application")
          .definedBy("com.registro..application..")
          .layer("Infrastructure")
          .definedBy("com.registro..infrastructure..")
          .whereLayer("Domain")
          .mayOnlyBeAccessedByLayers("Application", "Infrastructure")
          .whereLayer("Application")
          .mayOnlyBeAccessedByLayers("Infrastructure")
          .whereLayer("Infrastructure")
          .mayNotBeAccessedByAnyLayer();

  /** Un adaptador nunca usa a otro: web, persistencia, seguridad y config permanecen separados. */
  static final ArchRule ADAPTERS_DO_NOT_DEPEND_ON_EACH_OTHER =
      slices()
          .matching("com.registro..infrastructure.(*)..")
          .should()
          .notDependOnEachOther()
          .because(
              "cada adaptador habla con el dominio, así que reemplazar un adaptador no arrastra a ningún otro");

  /**
   * El adaptador web llega al caso de uso a través de su puerto de entrada, que vive en un
   * subpaquete de la capa de aplicación; las clases directamente en el paquete de aplicación son
   * implementaciones.
   */
  static final ArchRule WEB_DEPENDS_ON_THE_INBOUND_PORT_NOT_ON_THE_IMPLEMENTATION =
      noClasses()
          .that()
          .resideInAPackage("com.registro..infrastructure.web..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.registro..application")
          .because("la capa web depende de una abstracción que pertenece a la capa de aplicación");

  /** Una entidad de persistencia nunca cruza la capa web. */
  static final ArchRule WEB_DOES_NOT_USE_JPA_ENTITIES =
      noClasses()
          .that()
          .resideInAPackage("com.registro..infrastructure.web..")
          .should()
          .dependOnClassesThat()
          .areAnnotatedWith(Entity.class)
          .because("la capa web habla con records de petición y respuesta, nunca con entidades");

  /**
   * Dos paquetes que se necesitan mutuamente son en la práctica uno solo. Cada paquete bajo la raíz
   * dada es una porción. Es una función de la raíz, y no una constante, porque los fixtures de las
   * demás reglas forman ciclos entre sí sin querer (una clase de dominio que usa la aplicación y
   * una de aplicación que usa el dominio): la prueba de mordida ejecuta la regla solo contra el par
   * de paquetes escrito para romperla.
   */
  static ArchRule packagesAreFreeOfCycles(String rootPackage) {
    return slices()
        .matching(rootPackage + ".(**)")
        .should()
        .beFreeOfCycles()
        .because(
            "un paquete que depende de otro que depende de él no separa nada, y la dirección de las dependencias deja de significar algo");
  }

  static final ArchRule NO_FIELD_INJECTION =
      GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

  static final ArchRule JPA_ENTITIES_LIVE_IN_PERSISTENCE =
      classes()
          .that()
          .areAnnotatedWith(Entity.class)
          .should()
          .resideInAPackage("com.registro..infrastructure.persistence..")
          .because("el agregado no lleva anotaciones: la entidad es un detalle de persistencia");

  private ArchitectureRules() {}
}
