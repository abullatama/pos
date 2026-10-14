// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register("verifyModuleDependencies") {
    group = "verification"
    description = "Enforces architecture boundaries between app, feature, and core modules (F07)."
    notCompatibleWithConfigurationCache("Inspects dynamic project dependency graph across all subprojects")

    doLast {
        val violations = mutableListOf<String>()

        allprojects.forEach { proj ->
            val projPath = proj.path

            // Rule 0: core:domain pure Kotlin without Android
            if (projPath == ":core:domain") {
                if (proj.plugins.hasPlugin("com.android.application") || proj.plugins.hasPlugin("com.android.library")) {
                    violations.add("VIOLATION: :core:domain must be pure Kotlin (cannot apply Android plugins).")
                }
            }

            // Inspect project dependencies
            proj.configurations.configureEach {
                dependencies.withType<ProjectDependency>().forEach { dep ->
                    val targetPath = dep.path

                    // Rule 1: feature:cafe cannot depend on feature:laundry
                    if (projPath == ":feature:cafe" && targetPath == ":feature:laundry") {
                        violations.add("VIOLATION: :feature:cafe must not depend on :feature:laundry.")
                    }

                    // Rule 2: feature:laundry cannot depend on feature:cafe
                    if (projPath == ":feature:laundry" && targetPath == ":feature:cafe") {
                        violations.add("VIOLATION: :feature:laundry must not depend on :feature:cafe.")
                    }

                    // Rule 3: feature:shift / payment / admin cannot depend on feature:cafe or feature:laundry
                    if ((projPath == ":feature:shift" || projPath == ":feature:payment" || projPath == ":feature:admin") &&
                        (targetPath == ":feature:cafe" || targetPath == ":feature:laundry")
                    ) {
                        violations.add("VIOLATION: $projPath must not depend on business feature $targetPath.")
                    }

                    // Rule 4: core:* cannot depend on any feature:*
                    if (projPath.startsWith(":core:") && targetPath.startsWith(":feature:")) {
                        violations.add("VIOLATION: $projPath must not depend on feature module $targetPath.")
                    }

                    // Rule 5: core:domain cannot depend on any other module
                    if (projPath == ":core:domain" && targetPath != ":core:domain") {
                        violations.add("VIOLATION: :core:domain must not depend on any module, but depends on $targetPath.")
                    }
                }
            }
        }

        // Rule 6: Application shells must be thin (no business logic: Repository, UseCase, Dao, Entity)
        val forbiddenKeywords = listOf("Repository", "UseCase", "Dao", "Entity")
        listOf(":app-cafe", ":app-laundry").forEach { path ->
            val appProj = findProject(path)
            val srcDir = appProj?.file("src/main/java")
            if (srcDir != null && srcDir.exists()) {
                srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                    val content = file.readText()
                    forbiddenKeywords.forEach { kw ->
                        if (content.contains("class .*$kw".toRegex()) || content.contains("interface .*$kw".toRegex())) {
                            violations.add("VIOLATION: Application shell $path contains business logic symbol ($kw) in ${file.name}.")
                        }
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException("Module dependency verification failed (F07):\n" + violations.joinToString("\n"))
        } else {
            logger.lifecycle("SUCCESS: All architecture and module dependency boundaries passed (F07)!")
        }
    }
}
