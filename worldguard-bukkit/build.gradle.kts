import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.FileCollection

plugins {
    `java-library`
    id("buildlogic.platform")
}

fun privateUniverseSpigotClasspath(paths: String): FileCollection {
    var classpath: FileCollection = files()
    paths.split(File.pathSeparator)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .forEach { path ->
            val entry = file(path)
            check(entry.exists()) {
                "Configured UniverseSpigot API classpath entry is not readable."
            }
            classpath = classpath + if (entry.isDirectory) {
                fileTree(entry) {
                    include("**/*.jar")
                }
            } else {
                files(entry)
            }
        }
    return classpath
}

dependencies {
    "api"(project(":worldguard-core"))
    "api"(libs.worldedit.bukkit) { isTransitive = false }
    "compileOnly"(libs.commandbook) { isTransitive = false }

    "compileOnly"(libs.jetbrains.annotations) {
        because("Resolving Spigot annotations")
    }
    "testCompileOnly"(libs.jetbrains.annotations) {
        because("Resolving Spigot annotations")
    }
    val configuredUniverseSpigotApiClasspath = providers.gradleProperty("universeSpigotApiClasspath")
        .orElse(providers.environmentVariable("UNIVERSESPIGOT_API_CLASSPATH"))
        .orElse(providers.gradleProperty("universeSpigotApiJar"))
        .orElse(providers.environmentVariable("UNIVERSESPIGOT_API_JAR"))
    val defaultUniverseSpigotApiJar = rootProject.file("local-libs/universe-spigot-api.jar")
    val defaultUniverseSpigotApiDirectory = rootProject.file("local-libs/universe-spigot-api")

    when {
        configuredUniverseSpigotApiClasspath.isPresent -> {
            "compileOnly"(privateUniverseSpigotClasspath(configuredUniverseSpigotApiClasspath.get()))
        }
        defaultUniverseSpigotApiDirectory.isDirectory -> {
            "compileOnly"(fileTree(defaultUniverseSpigotApiDirectory) {
                include("**/*.jar")
            })
        }
        defaultUniverseSpigotApiJar.isFile -> {
            "compileOnly"(files(defaultUniverseSpigotApiJar))
        }
    }
    "compileOnly"(libs.canvasApi) {
        exclude("org.slf4j", "slf4j-api")
        exclude("junit", "junit")
    }

    "implementation"(libs.paperLib)
    "implementation"(libs.bstats.bukkit)
}

tasks.named<Copy>("processResources") {
    val internalVersion = project.ext["internalVersion"]
    inputs.property("internalVersion", internalVersion)
    filesMatching("plugin.yml") {
        expand("internalVersion" to internalVersion)
    }
}

tasks.named<ShadowJar>("shadowJar") {
    val runtimeClasspath = project.configurations.named("runtimeClasspath")

    from({
        val worldEditBukkitJars = runtimeClasspath.get()
            .filter { it.name.startsWith("worldedit-bukkit-") && it.name.endsWith(".jar") }
        check(!worldEditBukkitJars.isEmpty) {
            "Unable to locate the WorldEdit Bukkit runtime jar for WEPIF packaging."
        }
        worldEditBukkitJars.map { zipTree(it) }
    }) {
        include("com/sk89q/wepif/**")
    }

    from({
        val worldEditCoreJars = runtimeClasspath.get()
            .filter { it.name.startsWith("worldedit-core-") && it.name.endsWith(".jar") }
        check(!worldEditCoreJars.isEmpty) {
            "Unable to locate the WorldEdit Core runtime jar for WEPIF support packaging."
        }
        worldEditCoreJars.map { zipTree(it) }
    }) {
        include("com/sk89q/worldedit/internal/util/LogManagerCompat.class")
        include("com/sk89q/worldedit/util/report/**")
        exclude("com/sk89q/worldedit/util/report/ConfigReport.class")
        include("com/sk89q/util/yaml/**")
        include("com/sk89q/util/StringUtil.class")
        include("com/sk89q/worldedit/math/BlockVector2.class")
        include("com/sk89q/worldedit/math/BlockVector3.class")
        include("com/sk89q/worldedit/math/BlockVector3\$YzxOrderComparator.class")
        include("com/sk89q/worldedit/math/Vector2.class")
        include("com/sk89q/worldedit/math/Vector3.class")
        include("com/sk89q/worldedit/math/Vector3\$YzxOrderComparator.class")
    }

    dependencies {
        include(dependency(":worldguard-core"))
        include(dependency("org.bstats:"))
        include(dependency("io.papermc:paperlib"))

        relocate("org.bstats", "com.sk89q.worldguard.bukkit.bstats")
        relocate("io.papermc.lib", "com.sk89q.worldguard.bukkit.paperlib")
    }
}

tasks.named("assemble").configure {
    dependsOn("shadowJar")
}

configure<PublishingExtension> {
    publications.named<MavenPublication>("maven") {
        from(components["java"])
    }
}
