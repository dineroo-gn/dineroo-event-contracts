plugins {
    kotlin("jvm") version "2.1.20"
    `maven-publish`
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

group = "com.cgf.contracts"
version = "1.0-SNAPSHOT"


java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.avro:avro:1.12.0")
    testImplementation(kotlin("test"))
}

avro {
    setCreateSetters(true)
    fieldVisibility.set("PRIVATE")
    outputCharacterEncoding.set("UTF-8")
    stringType.set("String")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}


publishing {
    publications {
        create<MavenPublication>("avroSchemas") {
            from(components["java"])
            groupId = "com.example"
            artifactId = "avro-schemas"
            version = "1.0.0"
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/dineroo-gn/dineroo-events-contracts")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
