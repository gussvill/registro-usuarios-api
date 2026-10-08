package com.registro.usuarios;

import static org.assertj.core.api.Assertions.assertThat;

import com.registro.usuarios.application.RegisterUserUseCase;
import com.registro.usuarios.domain.model.User;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** The architecture rules, run on the compiled production classes (tests are not analysed). */
class ArchitectureTest {

  private static final JavaClasses PRODUCTION =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.registro.usuarios");

  @Test
  void theImportSeesTheProductionClassesOfEveryLayerAndNoTestClass() {
    assertThat(PRODUCTION.contain(User.class)).isTrue();
    assertThat(PRODUCTION.contain(RegisterUserUseCase.class)).isTrue();
    assertThat(PRODUCTION.stream().map(c -> c.getPackageName()))
        .anyMatch(name -> name.endsWith(".infrastructure.web"))
        .anyMatch(name -> name.endsWith(".infrastructure.persistence"))
        .anyMatch(name -> name.endsWith(".infrastructure.security"))
        .anyMatch(name -> name.endsWith(".infrastructure.config"))
        .noneMatch(name -> name.contains("archfixture"));
    assertThat(PRODUCTION.stream().map(c -> c.getName()))
        .noneMatch(name -> name.endsWith("Test") || name.endsWith("Tests"));
  }

  @Test
  void domainDoesNotDependOnAnyFramework() {
    ArchitectureRules.DOMAIN_IS_FREE_OF_FRAMEWORKS.check(PRODUCTION);
  }

  @Test
  void applicationDependsOnlyOnTheDomainAndTheTransactionAnnotation() {
    ArchitectureRules.APPLICATION_USES_ONLY_THE_DOMAIN_AND_THE_TRANSACTION_ANNOTATION.check(
        PRODUCTION);
  }

  @Test
  void dependenciesPointInwards() {
    ArchitectureRules.DEPENDENCIES_POINT_INWARDS.check(PRODUCTION);
  }

  @Test
  void adaptersDoNotDependOnEachOther() {
    ArchitectureRules.ADAPTERS_DO_NOT_DEPEND_ON_EACH_OTHER.check(PRODUCTION);
  }

  @Test
  void theWebLayerDoesNotUseJpaEntities() {
    ArchitectureRules.WEB_DOES_NOT_USE_JPA_ENTITIES.check(PRODUCTION);
  }

  @Test
  void theWebLayerDependsOnTheInboundPortNotOnTheImplementation() {
    ArchitectureRules.WEB_DEPENDS_ON_THE_INBOUND_PORT_NOT_ON_THE_IMPLEMENTATION.check(PRODUCTION);
  }

  @Test
  void noClassUsesFieldInjection() {
    ArchitectureRules.NO_FIELD_INJECTION.check(PRODUCTION);
  }

  @Test
  void jpaEntitiesLiveInThePersistenceAdapter() {
    ArchitectureRules.JPA_ENTITIES_LIVE_IN_PERSISTENCE.check(PRODUCTION);
  }
}
