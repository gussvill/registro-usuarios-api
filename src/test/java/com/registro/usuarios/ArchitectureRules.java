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
 * The layering decisions of the design, as rules. They select by package pattern (not by the exact
 * production package), so the same rule can be run against the fixtures written to break it; the
 * production test imports only {@code com.registro.usuarios}.
 */
final class ArchitectureRules {

  /** The domain compiles with the JDK alone: no container, ORM, JSON, JWT or OpenAPI library. */
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
          .because("business rules must be testable without any framework on the classpath");

  /** The application layer may read exactly one thing from Spring: the transaction annotation. */
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
          .because("the use case is fenced by an allow-list, not by a list of what is forbidden");

  /** Dependencies point inwards: infrastructure to application to domain, never the reverse. */
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

  /** One adapter never uses another: web, persistence, security and config stay apart. */
  static final ArchRule ADAPTERS_DO_NOT_DEPEND_ON_EACH_OTHER =
      slices()
          .matching("com.registro..infrastructure.(*)..")
          .should()
          .notDependOnEachOther()
          .because("every adapter talks to the domain, so a replaced adapter drags no other along");

  /** A persistence entity never crosses the web layer. */
  static final ArchRule WEB_DOES_NOT_USE_JPA_ENTITIES =
      noClasses()
          .that()
          .resideInAPackage("com.registro..infrastructure.web..")
          .should()
          .dependOnClassesThat()
          .areAnnotatedWith(Entity.class)
          .because("the web layer speaks in request and response records, never in entities");

  static final ArchRule NO_FIELD_INJECTION =
      GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

  static final ArchRule JPA_ENTITIES_LIVE_IN_PERSISTENCE =
      classes()
          .that()
          .areAnnotatedWith(Entity.class)
          .should()
          .resideInAPackage("com.registro..infrastructure.persistence..")
          .because("the aggregate is not annotated: the entity is a persistence detail");

  private ArchitectureRules() {}
}
