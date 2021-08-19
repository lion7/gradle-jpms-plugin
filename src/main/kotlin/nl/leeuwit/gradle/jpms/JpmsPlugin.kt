package nl.leeuwit.gradle.jpms

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register
import org.gradle.util.GUtil.toCamelCase
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.targets
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.lang.module.ModuleDescriptor
import java.util.spi.ToolProvider

class JpmsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.afterEvaluate {
            configureJava9ModuleInfo()
        }
    }

    private fun Project.configureJava9ModuleInfo() {
        val kotlin = extensions.findByType<KotlinProjectExtension>() ?: return
        val jvmTargets = kotlin.targets.filter { it is KotlinJvmTarget || it is KotlinWithJavaTarget<*> }
        if (jvmTargets.isEmpty()) {
            logger.warn("No Kotlin JVM targets found, can't configure compilation of module-info!")
        }
        jvmTargets.forEach { target ->
            target.compilations.forEach { compilation ->
                val defaultSourceSet = compilation.defaultSourceSet.kotlin
                val moduleInfoSourceFile = defaultSourceSet.find { it.name == "module-info.java" }

                if (moduleInfoSourceFile == null) {
                    logger.warn("No module-info.java file found in ${defaultSourceSet.srcDirs}, can't configure compilation of module-info!")
                } else {
                    val targetName = toCamelCase(target.targetName)
                    val compilationName = if (compilation.name != KotlinCompilation.MAIN_COMPILATION_NAME) toCamelCase(compilation.name) else ""
                    val compileModuleInfoTaskName = "compile${compilationName}ModuleInfo$targetName"
                    val checkModuleInfoTaskName = "check${compilationName}ModuleInfo$targetName"

                    val compileKotlinTask = compilation.compileKotlinTask as AbstractCompile
                    val modulePath = compileKotlinTask.classpath
                    val moduleInfoClassFile = compileKotlinTask.destinationDirectory.file("module-info.class").get().asFile

                    val compileModuleInfoTask = registerCompileModuleInfoTask(compileModuleInfoTaskName, modulePath, compileKotlinTask.destinationDirectory, moduleInfoSourceFile)
                    tasks.getByName(compilation.compileAllTaskName).dependsOn(compileModuleInfoTask)

                    val checkModuleInfoTask = registerCheckModuleInfoTask(checkModuleInfoTaskName, modulePath, moduleInfoClassFile)
                    checkModuleInfoTask.configure { dependsOn(compilation.compileAllTaskName) }
                    tasks.getByName("check").dependsOn(checkModuleInfoTask)
                }
            }
        }
    }

    private fun Project.registerCompileModuleInfoTask(taskName: String, modulePath: FileCollection, destinationDir: DirectoryProperty, moduleInfoSourceFile: File) =
        tasks.register(taskName, JavaCompile::class) {
            dependsOn(modulePath)
            source(moduleInfoSourceFile)
            classpath = files()
            destinationDirectory.set(destinationDir)
            sourceCompatibility = JavaVersion.VERSION_1_9.toString()
            targetCompatibility = JavaVersion.VERSION_1_9.toString()
            doFirst {
                options.compilerArgs = listOf(
                    "--release", "9",
                    "--module-path", modulePath.asPath,
                    "-Xlint:-requires-transitive-automatic"
                )
            }
        }

    private fun Project.registerCheckModuleInfoTask(taskName: String, modulePath: FileCollection, moduleInfoClassFile: File) =
        tasks.register(taskName) {
            dependsOn(modulePath)
            doLast {
                val jdeps = ToolProvider.findFirst("jdeps").orElseThrow { IllegalStateException("Tool 'jdeps' is not available") }
                val moduleDescriptor = moduleInfoClassFile.inputStream().use { ModuleDescriptor.read(it) }
                val moduleName = moduleDescriptor.name()
                val expectedOutput = moduleDescriptor.toJdepsOutput(moduleInfoClassFile)

                val outputCaptureStream = ByteArrayOutputStream()
                val printStream = PrintStream(outputCaptureStream, true, Charsets.UTF_8)
                jdeps.run(
                    printStream, printStream,
                    "--multi-release", "9",
                    "--module-path", (modulePath + files(moduleInfoClassFile.parentFile)).asPath,
                    "--check", moduleName
                )
                val actualOutput = outputCaptureStream.toString(Charsets.UTF_8).trim()

                if (actualOutput != expectedOutput) {
                    throw IllegalStateException("Module-info does not match!\n$actualOutput")
                }
            }
        }

    private fun ModuleDescriptor.toJdepsOutput(file: File, separator: String = System.lineSeparator()) =
        "${name()} (${file.parentFile.toURI().toString().replace("file:", "file://")})$separator  [Module descriptor]$separator" +
                requires().sortedBy { it.name() }.joinToString(separator) { requirement -> "    requires $requirement;" }
}
