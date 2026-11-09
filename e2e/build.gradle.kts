plugins {
    java
    id("com.diffplug.spotless") version "8.10.4"
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

// Pinned to the Selenium Grid image version used by compose.yaml (infra/README.md),
// so the client and the server speak the same WebDriver protocol version.
val seleniumVersion = "4.51.0"
val allureVersion = "2.35.5"
val axeVersion = "4.14.0"
val allureResultsDir = layout.buildDirectory.dir("allure-results")

dependencies {
    testImplementation("org.seleniumhq.selenium:selenium-java:$seleniumVersion")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    // Allure.step(...) and Allure.addAttachment(...) are plain Java API calls here: no @Step
    // annotations are used, so the AspectJ weaving some Allure Gradle plugins add is not needed.
    testImplementation("io.qameta.allure:allure-junit5:$allureVersion")
    // Accessibility scans (support/Accessibility.java). Its own Selenium and WebDriverManager are
    // left out: the pinned Selenium above is used, and the Grid provides the browser.
    testImplementation("com.deque.html.axe-core:selenium:$axeVersion") {
        exclude(group = "org.seleniumhq.selenium")
        exclude(group = "io.github.bonigarcia")
        exclude(group = "org.junit.vintage")
    }
    // Selenium logs through SLF4J; a provider avoids "no provider" warnings in the report
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.20")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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

tasks.test {
    useJUnitPlatform()
    // Matches EnvironmentReportExtension and e2e.yml's "allure-results" lookup under build/
    systemProperty("allure.results.directory", allureResultsDir.get().asFile.path)
    // The e2e version shown in the Allure report header (EnvironmentReportExtension)
    systemProperty("e2e.version", project.version.toString())
    // SELENIUM_URL and E2E_BASE_URL (Config.java) are read from the environment as-is;
    // Compose (e2e service) and CI set them, local Gradle runs fall back to their defaults.
    outputs.upToDateWhen { false } // always re-run: these are live journeys, not unit tests
}
