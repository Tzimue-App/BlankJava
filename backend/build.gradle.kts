import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.26.0"
    id("com.diffplug.spotless") version "8.10.4"
    id("net.ltgt.errorprone") version "5.1.1"
}

group = "com.blankjava"
// version: gradle.properties, managed by bump_version.py

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// The API contract is the source of truth (docs/technical_analysis.md §4.1)
val contract: File = file("../api/openapi.yaml")

// info.version of the contract, exposed as "api" in /actuator/info
val apiVersion: String =
    Regex("""(?m)^info:[ \t]*\n(?:[ \t][^\n]*\n)*?[ \t]+version:[ \t]*["']?([^"'\s]+)""")
        .find(contract.readText())
        ?.groupValues
        ?.get(1)
        ?: "unknown"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    // Swagger UI on the contract, enabled in the dev profile only
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")
    runtimeOnly("org.postgresql:postgresql")

    errorprone("com.google.errorprone:error_prone_core:2.51.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ---------------------------------------------------------------------------
// Code generation from the contract: Spring interfaces and models, never edited
// ---------------------------------------------------------------------------

val generatedSources = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set(contract.path)
    outputDir.set(generatedSources)
    apiPackage.set("com.blankjava.api")
    modelPackage.set("com.blankjava.api.model")
    cleanupOutput.set(true)
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "useSpringBoot4" to "true",
            "useJackson3" to "true",
            "useTags" to "true",
            "skipDefaultInterface" to "true",
            "useResponseEntity" to "true",
            "useBeanValidation" to "true",
            "openApiNullable" to "false",
            "documentationProvider" to "none",
            "annotationLibrary" to "none",
            "sourceFolder" to "src/main/java",
        ),
    )
}

sourceSets {
    main {
        java.srcDir(generatedSources.map { it.dir("src/main/java") })
    }
}

tasks.compileJava {
    dependsOn(tasks.openApiGenerate)
}

// ---------------------------------------------------------------------------
// Build info: version, plus the contract version for /actuator/info
// ---------------------------------------------------------------------------

springBoot {
    buildInfo {
        properties {
            additional.set(mapOf("api" to apiVersion))
        }
    }
}

// The contract is served as /openapi.yaml for Swagger UI (dev profile)
tasks.processResources {
    from(contract) { into("static") }
}

// Only the executable jar is built (Dockerfile extracts its layers)
tasks.jar {
    enabled = false
}

// ---------------------------------------------------------------------------
// Quality: Error Prone, Spotless
// ---------------------------------------------------------------------------

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)
        excludedPaths.set(".*/build/generated/.*")
    }
}

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat("2.102.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

// ---------------------------------------------------------------------------
// Tests: unit and slice tests in "test"; Testcontainers tests (tag "integration",
// need Docker) in "integrationTest", run by "check" only
// ---------------------------------------------------------------------------

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs the integration tests (Testcontainers, requires Docker)."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("integration")
    }
    shouldRunAfter(tasks.test)
}

// ---------------------------------------------------------------------------
// Coverage: JaCoCo over every test task that ran; generated code and the
// bootstrap class excluded. Thresholds: docs/technical_analysis.md §7.1
// ---------------------------------------------------------------------------

jacoco {
    toolVersion = "0.8.16"
}

val coverageExclusions = listOf("com/blankjava/api/**", "com/blankjava/BlankJavaApplication*")
val coverageData = fileTree(layout.buildDirectory.dir("jacoco")) { include("*.exec") }
val coveredClasses =
    sourceSets.main.get().output.classesDirs.asFileTree.matching {
        exclude(coverageExclusions)
    }

tasks.jacocoTestReport {
    mustRunAfter(tasks.test, integrationTest)
    executionData.setFrom(coverageData)
    classDirectories.setFrom(coveredClasses)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    mustRunAfter(tasks.test, integrationTest)
    executionData.setFrom(coverageData)
    classDirectories.setFrom(coveredClasses)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(integrationTest, tasks.jacocoTestCoverageVerification)
}
