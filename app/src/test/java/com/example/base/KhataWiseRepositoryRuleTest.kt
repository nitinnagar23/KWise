package com.example.base

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

class KhataWiseRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun storeOwner_canWriteStoreProfileAndCustomer() = runBlocking {
        val uid = signInTestUser("merchant@khatawise.com")

        // 1. Write store profile
        val userRef = firestore.collection("users").document(uid)
        userRef.set(mapOf("storeName" to "KhataWise Main Branch")).await()

        val userDoc = userRef.get().await()
        assertEquals("KhataWise Main Branch", userDoc.getString("storeName"))

        // 2. Add customer
        val custRef = userRef.collection("customers").document("cust_test_1")
        custRef.set(
            mapOf(
                "id" to "cust_test_1",
                "name" to "Karan Sharma",
                "phone" to "9876543210",
                "currentBalance" to 4500.0
            )
        ).await()

        val custDoc = custRef.get().await()
        assertEquals("Karan Sharma", custDoc.getString("name"))
    }

    @Test
    fun unauthenticatedAccess_isDenied() = runBlocking {
        // Sign out
        auth.signOut()

        try {
            firestore.collection("users").document("someone").get().await()
            fail("Expected PERMISSION_DENIED for unauthenticated access")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected firestore exception", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
        }
    }

    @Test
    fun crossUserAccess_isDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_owner@khatawise.com")
        firestore.collection("users").document(aliceUid).set(mapOf("storeName" to "Alice Feed Store")).await()

        // Sign in as Bob
        val bobUid = signInTestUser("bob_intruder@khatawise.com")

        try {
            firestore.collection("users").document(aliceUid).get().await()
            fail("Expected PERMISSION_DENIED for cross user read")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull(firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
        }
    }
}
