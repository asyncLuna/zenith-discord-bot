plugins {
  java
  id("org.springframework.boot") version "4.0.6"
  id("io.spring.dependency-management") version "1.1.7"
  id("com.diffplug.spotless") version "7.0.2"
}

group = "dev.asyncluna"

version = "0.3.0"

description = "zenith-discord-bot"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

repositories { mavenCentral() }

extra["sentryVersion"] = "8.27.0"

extra["discord4jVersion"] = "3.3.2"

extra["commonsLang3Version"] = "3.20.0"

extra["commonsTextVersion"] = "1.15.0"

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
  implementation("org.springframework.boot:spring-boot-starter-quartz")
  implementation("io.sentry:sentry-spring-boot-4-starter")
  implementation("com.discord4j:discord4j-core:${property("discord4jVersion")}")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.apache.commons:commons-lang3:${property("commonsLang3Version")}")
  implementation("org.apache.commons:commons-text:${property("commonsTextVersion")}")
  compileOnly("org.projectlombok:lombok")
  developmentOnly("org.springframework.boot:spring-boot-devtools")
  annotationProcessor("org.projectlombok:lombok")
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive-test")
  testImplementation("org.springframework.boot:spring-boot-starter-quartz-test")
  testCompileOnly("org.projectlombok:lombok")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testAnnotationProcessor("org.projectlombok:lombok")
}

dependencyManagement { imports { mavenBom("io.sentry:sentry-bom:${property("sentryVersion")}") } }

tasks.withType<Test> { useJUnitPlatform() }

spotless {
  java {
    target("src/**/*.java")
    palantirJavaFormat()
    trimTrailingWhitespace()
    endWithNewline()
  }
  kotlinGradle {
    target("*.gradle.kts")
    ktfmt()
    trimTrailingWhitespace()
    endWithNewline()
  }
}
