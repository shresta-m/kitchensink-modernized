package com.kitchensink.arch;




import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Layer rules: controller -> service (ports) <- repository (adapters). Nothing calls upward. */
@AnalyzeClasses(packages = "com.kitchensink", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAnyPackage("..repository..", "..service.impl..");

    @ArchTest
    static final ArchRule servicesDoNotDependOnControllers = noClasses()
            .that().resideInAnyPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    /** The leak fix, enforced: services see domain exceptions and ports, never Spring Data or driver types. */
    @ArchTest
    static final ArchRule servicesAreFreeOfPersistenceTypes = noClasses()
            .that().resideInAnyPackage("..service..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..repository..", "org.springframework.dao..", "org.springframework.data..", "com.mongodb..");

    /** Adapters may implement service ports, but never reach into use-case implementations or the web layer. */
    @ArchTest
    static final ArchRule repositoriesDoNotDependOnServiceImplsOrControllers = noClasses()
            .that().resideInAPackage("..repository..")
            .should().dependOnClassesThat().resideInAnyPackage("..service.impl..", "..controller..");

    @ArchTest
    static final ArchRule modelDoesNotDependOnUpperLayers = noClasses()
            .that().resideInAPackage("..model..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..controller..", "..service..", "..repository..", "..dto..");

    @ArchTest
    static final ArchRule everyDomainExceptionExtendsTheRoot = classes()
            .that().resideInAPackage("..exception..").and().haveSimpleNameEndingWith("Exception")
            .and().areNotAssignableFrom(com.kitchensink.exception.domain.DomainException.class)
            .should().beAssignableTo(com.kitchensink.exception.domain.DomainException.class)
            .allowEmptyShould(true);
}
