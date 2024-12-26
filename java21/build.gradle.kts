plugins {
    java
    id("io.github.goooler.shadow") version "8.1.7"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }

    withSourcesJar()
}

val joptSimpleVersion = "5.0.4"
val asmVersion = "5.2"
val slf4jVersion = "1.8.0-beta2"
val jbAnnotationsVersion = "24.1.0"

val gradleWrapperVersion = "8.5"

val lwtsVersion = "1.1.0-SNAPSHOT"
val shurikenVersion = "0.0.1-SNAPSHOT"

val mixinVersion = "0.8.7-SNAPSHOT"
val fandApiVersion = "1.20.6-R0.1-SNAPSHOT"

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-releases/")
    maven("https://repo.spongepowered.org/repository/maven-public/")
    maven("https://repo.leavesmc.org/snapshots")
    maven("https://repo.fandmc.cn/snapshots")
}

dependencies {
    implementation("net.sf.jopt-simple:jopt-simple:$joptSimpleVersion")
    implementation("org.ow2.asm:asm:$asmVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.jetbrains:annotations:$jbAnnotationsVersion")
    implementation("org.spongepowered:mixin:$mixinVersion")
    // implementation("org.leavesmc.leaves:leaves-api:$leavesApiVersion")
    implementation("io.sigpipe:jbsdiff:1.0")
}

tasks.shadowJar {
    val prefix = "fandclip.libs"
    listOf("org.apache", "org.tukaani", "io.sigpipe").forEach { pack ->
        relocate(pack, "$prefix.$pack")
    }

    exclude("META-INF/LICENSE.txt")
    exclude("META-INF/NOTICE.txt")
}
