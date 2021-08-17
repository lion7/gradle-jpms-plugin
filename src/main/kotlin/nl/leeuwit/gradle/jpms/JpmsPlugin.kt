package nl.leeuwit.gradle.jpms

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.findByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import java.util.spi.ToolProvider

class JpmsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val multiplatform = project.extensions.findByType<KotlinMultiplatformExtension>() ?: return
        val jvmTargets = multiplatform.targets.filterIsInstance<KotlinJvmTarget>().filter { it.withJavaEnabled }
        if (jvmTargets.isEmpty()) {
            project.logger.warn("No Kotlin JVM targets found with Java enabled, can't configure Java 9 source set!")
        }
        jvmTargets.forEach { target ->
            val mainCompilation = target.compilations.getByName("main")
            val moduleName = project.findProperty("jpms.modulename")?.toString() ?: mainCompilation.moduleName.replace('-', '.')
            val inputClasspath = mainCompilation.compileKotlinTask.classpath
            val outputClassesDir = mainCompilation.compileKotlinTask.destinationDirectory.asFile.get()
            val outputJarTask = project.tasks.getByName(target.artifactsTaskName) as Jar

            val java9SourceSet = multiplatform.sourceSets.create("${target.targetName}Java9")
            val java9Compilation = target.compilations.create("java9") {
                compileJavaTaskProvider!!.configure {
                    dependsOn(mainCompilation.compileAllTaskName)
                    source(java9SourceSet.kotlin)
                    sourceCompatibility = JavaVersion.VERSION_1_9.toString()
                    targetCompatibility = JavaVersion.VERSION_1_9.toString()
                    doFirst {
                        classpath = project.files()
                        options.compilerArgs = listOf(
                            "--release", "9",
                            "--module-path", inputClasspath.asPath,
                            "--patch-module", "$moduleName=$outputClassesDir",
                            "-Xlint:-requires-transitive-automatic"
                        )
                    }
                }
            }

            outputJarTask.apply {
                from(java9Compilation.output.classesDirs) {
                    into("META-INF/versions/9")
                }
                manifest {
                    attributes(mapOf("Multi-Release" to true))
                }
            }

            project.tasks.register("${target.targetName}CheckModuleInfo") {
                dependsOn(outputJarTask)
                doLast {
                    val jdeps = ToolProvider.findFirst("jdeps").orElseThrow { IllegalStateException("Tool 'jdeps' is not available") }
                    jdeps.run(
                        System.out, System.err,
                        "--multi-release", "9",
                        "--module-path", (inputClasspath + project.files(outputJarTask)).asPath,
                        "--check", moduleName
                    )
                }
            }
        }
    }

}
