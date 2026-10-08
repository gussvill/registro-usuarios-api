package com.registro.usuarios;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Una regla que no puede fallar no protege nada. Cada regla se ejecuta contra un fixture escrito
 * para romperla, y el informe debe nombrar la clase que la rompe: una regla que falla por otro
 * motivo (un paquete vacío, un patrón mal escrito) no cuenta como probada. Los fixtures viven en el
 * árbol de pruebas bajo un paquete raíz distinto del de la aplicación, de modo que ni el escaneo de
 * componentes ni {@link ArchitectureTest} los ven nunca.
 */
class ArchitectureRulesBiteTest {

  private static final JavaClasses FIXTURES =
      new ClassFileImporter().importPackages("com.registro.archfixture");

  private static Arguments bite(String ruleName, String offender) {
    return Arguments.of(ruleName, offender);
  }

  /**
   * La regla se busca por el nombre de su constante, así que renombrar una regla rompe esta tabla.
   */
  private static ArchRule rule(String constantName) {
    try {
      Field field = ArchitectureRules.class.getDeclaredField(constantName);
      return (ArchRule) field.get(null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("No rule named " + constantName, e);
    }
  }

  static Stream<Arguments> rulesAndTheClassesThatBreakThem() {
    return Stream.of(
        bite("DOMAIN_IS_FREE_OF_FRAMEWORKS", "SpringCoupledPolicy"),
        bite("DOMAIN_IS_FREE_OF_FRAMEWORKS", "JpaCoupledRecord"),
        bite("DOMAIN_IS_FREE_OF_FRAMEWORKS", "HibernateCoupledValue"),
        bite("DOMAIN_IS_FREE_OF_FRAMEWORKS", "Jackson2CoupledValue"),
        bite("DOMAIN_IS_FREE_OF_FRAMEWORKS", "Jackson3CoupledMapper"),
        bite("DOMAIN_IS_FREE_OF_FRAMEWORKS", "JjwtCoupledIssuer"),
        bite("DOMAIN_IS_FREE_OF_FRAMEWORKS", "SwaggerCoupledModel"),
        bite(
            "APPLICATION_USES_ONLY_THE_DOMAIN_AND_THE_TRANSACTION_ANNOTATION",
            "SpringStereotypeUseCase"),
        bite("APPLICATION_USES_ONLY_THE_DOMAIN_AND_THE_TRANSACTION_ANNOTATION", "LoggingUseCase"),
        bite("DEPENDENCIES_POINT_INWARDS", "ReachesApplication"),
        bite("DEPENDENCIES_POINT_INWARDS", "ReachesInfrastructure"),
        bite("ADAPTERS_DO_NOT_DEPEND_ON_EACH_OTHER", "ReachesPersistence"),
        bite("ADAPTERS_DO_NOT_DEPEND_ON_EACH_OTHER", "ReachesWeb"),
        bite("WEB_DOES_NOT_USE_JPA_ENTITIES", "UsesEntity"),
        bite(
            "WEB_DEPENDS_ON_THE_INBOUND_PORT_NOT_ON_THE_IMPLEMENTATION",
            "ReachesUseCaseImplementation"),
        bite("NO_FIELD_INJECTION", "FieldInjected"),
        bite("JPA_ENTITIES_LIVE_IN_PERSISTENCE", "MisplacedEntity"));
  }

  @ParameterizedTest(name = "{0} rejects {1}")
  @MethodSource("rulesAndTheClassesThatBreakThem")
  void theRuleRejectsItsFixtureAndNamesIt(String ruleName, String offender) {
    EvaluationResult result = rule(ruleName).evaluate(FIXTURES);

    assertThat(result.hasViolation()).as("%s debería fallar en %s", ruleName, offender).isTrue();
    assertThat(result.getFailureReport().toString()).contains(offender);
  }

  /**
   * La regla de ciclos es una función de la raíz (ver {@link
   * ArchitectureRules#packagesAreFreeOfCycles}), así que no entra en la tabla de constantes: se
   * ejecuta contra el par de paquetes cíclicos y debe nombrar a las dos clases.
   */
  @Test
  void theCycleRuleRejectsTheCyclicPairAndNamesBothClasses() {
    EvaluationResult result =
        ArchitectureRules.packagesAreFreeOfCycles("com.registro.archfixture.cycle")
            .evaluate(FIXTURES);

    assertThat(result.hasViolation()).isTrue();
    assertThat(result.getFailureReport().toString())
        .contains("Cycle detected")
        .contains("cycle.ledger.LedgerEntry")
        .contains("cycle.billing.Invoice");
  }

  @Test
  void theCycleRuleAcceptsAcyclicFixturePackages() {
    // Los fixtures de las demás reglas no forman ciclos entre sus subpaquetes de infraestructura.
    EvaluationResult result =
        ArchitectureRules.packagesAreFreeOfCycles("com.registro.archfixture.infrastructure")
            .evaluate(FIXTURES);

    assertThat(result.hasViolation()).isFalse();
  }

  @Test
  void theAllowListAcceptsTheTransactionAnnotationWhileRejectingOtherSpringTypes() {
    EvaluationResult result =
        rule("APPLICATION_USES_ONLY_THE_DOMAIN_AND_THE_TRANSACTION_ANNOTATION").evaluate(FIXTURES);

    assertThat(result.getFailureReport().toString())
        .contains("SpringStereotypeUseCase")
        .doesNotContain("TransactionalUseCase");
  }

  @Test
  void aWebClassThatUsesTheInboundPortIsNotReportedByThePortRule() {
    EvaluationResult result =
        rule("WEB_DEPENDS_ON_THE_INBOUND_PORT_NOT_ON_THE_IMPLEMENTATION").evaluate(FIXTURES);

    assertThat(result.getFailureReport().toString())
        .contains("ReachesUseCaseImplementation")
        .doesNotContain("UsesInboundPort");
  }

  @Test
  void noViolationOriginatesFromAnInnocentFixture() {
    // Una clase limpia puede ser destino de una dependencia prohibida, pero nunca la culpable:
    // el origen es el primer <...> de cada línea de detalle.
    for (String name : ruleNames()) {
      List<String> details = rule(name).evaluate(FIXTURES).getFailureReport().getDetails();
      assertThat(details).as("%s: las violaciones a inspeccionar", name).isNotEmpty();
      for (String detail : details) {
        String origin = detail.substring(detail.indexOf('<'), detail.indexOf('>') + 1);
        assertThat(origin)
            .as("%s: %s", name, detail)
            .doesNotContain("CleanDomainType")
            .doesNotContain("CleanAdapter")
            .doesNotContain("UsesInboundPort")
            .doesNotContain("TransactionalUseCase");
      }
    }
  }

  @Test
  void everyRuleHasAtLeastOneFixtureThatBreaksIt() {
    Set<String> covered = new TreeSet<>();
    rulesAndTheClassesThatBreakThem()
        .forEach(arguments -> covered.add((String) arguments.get()[0]));

    assertThat(covered).containsExactlyElementsOf(new TreeSet<>(ruleNames()));
  }

  @Test
  void theFixturesAreOutsideTheApplicationPackage() {
    assertThat(FIXTURES.size()).isPositive();
    assertThat(FIXTURES.stream().map(c -> c.getPackageName()))
        .allMatch(name -> name.startsWith("com.registro.archfixture"))
        .noneMatch(name -> name.startsWith("com.registro.usuarios"));
  }

  private static List<String> ruleNames() {
    List<String> names = new ArrayList<>();
    for (Field field : ArchitectureRules.class.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())
          && ArchRule.class.isAssignableFrom(field.getType())) {
        names.add(field.getName());
      }
    }
    return names;
  }
}
