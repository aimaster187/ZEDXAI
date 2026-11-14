package com.titan.zedxai.firebase

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ZedxFirestoreRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserProfile_authenticatedUser_createsWithUserRoleOnly() = runBlocking {
        val uid = signInTestUser("alice_zedx@test.com")
        val repo = ZedxFirestoreRepository(firestore, auth)

        val profile = withTimeout(5000L) {
            repo.ensureUserProfile("alice_zedx@test.com", "Alice Titan", null).getOrThrow()
        }
        assertEquals(uid, profile.userId)
        assertEquals("user", profile.role)
        assertEquals("active", profile.status)
    }

    @Test
    fun selfPromotionToAdmin_isRejectedByRules() = runBlocking {
        val uid = signInTestUser("attacker_zedx@test.com")
        val repo = ZedxFirestoreRepository(firestore, auth)
        withTimeout(5000L) {
            repo.ensureUserProfile("attacker_zedx@test.com", "Attacker", null).getOrThrow()
        }

        val failed = try {
            withTimeout(5000L) {
                firestore.collection("users").document(uid).update("role", "admin").await()
            }
            false
        } catch (e: Exception) {
            e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Expected PERMISSION_DENIED when self-promoting to admin", failed)
    }

    @Test
    fun memoryCrud_ownerIsolation_enforced() = runBlocking {
        val aliceUid = signInTestUser("alice_mem@test.com")
        val aliceRepo = ZedxFirestoreRepository(firestore, auth)
        val memId = "mem_${UUID.randomUUID().toString().take(8)}"

        withTimeout(5000L) {
            aliceRepo.upsertMemory(
                memoryId = memId,
                title = "Preferred Language",
                content = "Respond concisely in Kotlin",
                category = "coding",
                enabled = true
            ).getOrThrow()
        }

        val memories = withTimeout(3000L) {
            aliceRepo.observeMemories(aliceUid).first { list -> list.any { it.id == memId } }
        }
        assertTrue(memories.any { it.id == memId })

        // Switch to Bob and verify cross-user read is denied
        signInTestUser("bob_mem@test.com")
        val bobRepo = ZedxFirestoreRepository(firestore, auth)
        val denied = try {
            withTimeout(3000L) { bobRepo.observeMemories(aliceUid).first() }
            false
        } catch (e: Exception) {
            e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Expected PERMISSION_DENIED for cross-user memory read", denied)
    }

    @Test
    fun usageMetrics_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = ZedxFirestoreRepository(firestore, auth)
        val denied = try {
            withTimeout(3000L) { repo.observeUserUsageMetrics("any_uid").first() }
            false
        } catch (e: Exception) {
            e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Expected PERMISSION_DENIED when unauthenticated", denied)
    }
}
