package com.example.ui.billing

import androidx.compose.runtime.Composable
import com.example.data.repository.CattleFeedRepository
import com.example.model.User

data class BillCartLine(
    val id: String = java.util.UUID.randomUUID().toString(),
    val productId: String,
    val bagsCount: Int = 1,
    val looseKgStr: String = "",
    val customBagRateStr: String = "",
    val customLooseRateStr: String = ""
)

@Composable
fun NewBillScreen(
    repository: CattleFeedRepository,
    currentUser: User?,
    onBack: () -> Unit
) {
    BillingScreen(
        repository = repository,
        currentUser = currentUser,
        onBack = onBack
    )
}
