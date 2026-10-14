package id.a3.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModuleDependencyArchitectureTest {

    private fun checkDependencyRules(
        dependencies: Map<String, List<String>>
    ): List<String> {
        val violations = mutableListOf<String>()

        dependencies.forEach { (proj, targetList) ->
            targetList.forEach { target ->
                // Rule 1: feature:cafe cannot depend on feature:laundry
                if (proj == ":feature:cafe" && target == ":feature:laundry") {
                    violations.add("VIOLATION: :feature:cafe must not depend on :feature:laundry.")
                }
                // Rule 2: feature:laundry cannot depend on feature:cafe
                if (proj == ":feature:laundry" && target == ":feature:cafe") {
                    violations.add("VIOLATION: :feature:laundry must not depend on :feature:cafe.")
                }
                // Rule 3: feature:shift / payment / admin cannot depend on feature:cafe or feature:laundry
                if ((proj == ":feature:shift" || proj == ":feature:payment" || proj == ":feature:admin") &&
                    (target == ":feature:cafe" || target == ":feature:laundry")
                ) {
                    violations.add("VIOLATION: $proj must not depend on business feature $target.")
                }
                // Rule 4: core:* cannot depend on any feature:*
                if (proj.startsWith(":core:") && target.startsWith(":feature:")) {
                    violations.add("VIOLATION: $proj must not depend on feature module $target.")
                }
                // Rule 5: core:domain cannot depend on any other module
                if (proj == ":core:domain" && target != ":core:domain") {
                    violations.add("VIOLATION: :core:domain must not depend on any module, but depends on $target.")
                }
            }
        }
        return violations
    }

    private fun checkAppShellBusinessLogic(
        sourceCode: String,
        fileName: String
    ): List<String> {
        val violations = mutableListOf<String>()
        val forbidden = listOf("Repository", "UseCase", "Dao", "Entity")
        forbidden.forEach { kw ->
            if (sourceCode.contains("class .*$kw".toRegex()) || sourceCode.contains("interface .*$kw".toRegex())) {
                violations.add("VIOLATION: Business logic symbol ($kw) found in shell file $fileName.")
            }
        }
        return violations
    }

    @Test
    fun `valid architecture passes with zero violations`() {
        val validDependencies = mapOf(
            ":app-cafe" to listOf(":feature:cafe", ":feature:shift", ":feature:payment", ":core:ui", ":core:domain"),
            ":app-laundry" to listOf(":feature:laundry", ":feature:shift", ":feature:payment", ":core:ui", ":core:domain"),
            ":feature:cafe" to listOf(":core:domain", ":core:ui", ":core:data", ":feature:shift", ":feature:payment"),
            ":feature:laundry" to listOf(":core:domain", ":core:ui", ":core:data", ":feature:shift", ":feature:payment"),
            ":feature:shift" to listOf(":core:domain", ":core:ui", ":core:data"),
            ":feature:payment" to listOf(":core:domain", ":core:ui", ":core:data"),
            ":core:data" to listOf(":core:domain"),
            ":core:domain" to emptyList()
        )

        val violations = checkDependencyRules(validDependencies)
        assertTrue("Expected no violations for valid architecture, but got: $violations", violations.isEmpty())
    }

    @Test
    fun `detects violation when feature cafe depends on feature laundry`() {
        val invalidDependencies = mapOf(
            ":feature:cafe" to listOf(":feature:laundry", ":core:domain")
        )

        val violations = checkDependencyRules(invalidDependencies)
        assertEquals(1, violations.size)
        assertTrue(violations.first().contains(":feature:cafe must not depend on :feature:laundry"))
    }

    @Test
    fun `detects violation when feature laundry depends on feature cafe`() {
        val invalidDependencies = mapOf(
            ":feature:laundry" to listOf(":feature:cafe", ":core:domain")
        )

        val violations = checkDependencyRules(invalidDependencies)
        assertEquals(1, violations.size)
        assertTrue(violations.first().contains(":feature:laundry must not depend on :feature:cafe"))
    }

    @Test
    fun `detects violation when shift or payment depends on business feature`() {
        val invalidShift = mapOf(
            ":feature:shift" to listOf(":feature:cafe")
        )
        val violations = checkDependencyRules(invalidShift)
        assertEquals(1, violations.size)
        assertTrue(violations.first().contains(":feature:shift must not depend on business feature :feature:cafe"))
    }

    @Test
    fun `detects violation when core depends on feature`() {
        val invalidCore = mapOf(
            ":core:data" to listOf(":feature:cafe")
        )
        val violations = checkDependencyRules(invalidCore)
        assertEquals(1, violations.size)
        assertTrue(violations.first().contains(":core:data must not depend on feature module :feature:cafe"))
    }

    @Test
    fun `detects violation when app shell contains business logic`() {
        val badCode = """
            package id.tenang.kasir.cafe
            class OrderRepository {
                fun getOrders() = emptyList<String>()
            }
        """.trimIndent()

        val violations = checkAppShellBusinessLogic(badCode, "OrderRepository.kt")
        assertEquals(1, violations.size)
        assertTrue(violations.first().contains("Repository"))
    }

    @Test
    fun `clean app shell passes business logic check`() {
        val cleanCode = """
            package id.tenang.kasir.cafe
            import android.os.Bundle
            import androidx.activity.ComponentActivity
            class MainActivity : ComponentActivity()
        """.trimIndent()

        val violations = checkAppShellBusinessLogic(cleanCode, "MainActivity.kt")
        assertTrue(violations.isEmpty())
    }
}
