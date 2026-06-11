import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.0.0"
}

group = "net.villagerzock"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("info.picocli:picocli:4.7.7")
    implementation("info.picocli:picocli-shell-jline3:4.7.6")
    implementation("org.jline:jline:3.30.0")
    implementation("org.jline:jline-console-ui:3.30.0")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.yaml:snakeyaml:2.5")
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaExec>().configureEach { standardInput = System.`in` }

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "net.villagerzock.cloudcore.core.Main"
        )
    }
}

val generatedResourcesDir = layout.buildDirectory.dir("generated/resources")
val embeddedDir = generatedResourcesDir.map { it.dir("embedded") }

val copyVelocityPluginJar by tasks.registering(Copy::class) {
    dependsOn(":Velocity:shadowJar")

    from(project(":Velocity").layout.buildDirectory.dir("libs")) {
        include("*-all.jar")
        rename { "CloudCore-Velocity.jar" }
    }

    into(embeddedDir)
}

sourceSets {
    main {
        resources {
            srcDir(generatedResourcesDir)
        }
    }
}

tasks.processResources {
    dependsOn(copyVelocityPluginJar)
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(copyVelocityPluginJar)
}