package com.samuel.flashbooking.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

class LayeredArchitectureTest {
    private final JavaClasses production = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.samuel.flashbooking");

    @Test
    void domainIsIndependentFromFrameworkApplicationAndAdapters() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "..application..",
                        "..infrastructure..", "..interfaces..")
                .check(production);
    }

    @Test
    void applicationDoesNotDependOnWebOrInfrastructureAdapters() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..interfaces..", "..infrastructure..")
                .check(production);
    }

    @Test
    void controllersDoNotAccessPersistenceRepositories() {
        noClasses().that().resideInAPackage("..interfaces.rest..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure.persistence..", "..infrastructure.outbox..")
                .check(production);
        noClasses().that().resideInAPackage("..interfaces.rest..")
                .should().dependOnClassesThat().areAnnotatedWith(Repository.class)
                .check(production);
    }

    @Test
    void productionCodeUsesConstructorInjection() {
        noFields().should().beAnnotatedWith(Autowired.class).check(production);
    }
}
