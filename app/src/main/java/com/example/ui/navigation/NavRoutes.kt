package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object NewBill : Screen("new_bill", "New Bill")
    object Ledger : Screen("ledger", "Ledger", Icons.AutoMirrored.Filled.MenuBook)
    object Estimate : Screen("estimate", "Calculator", Icons.Default.Calculate)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Customers : Screen("customers", "Customers", Icons.Default.Group)
    object Purchases : Screen("purchases", "Supplier ledger", Icons.Default.LocalShipping)
    object DailyRates : Screen("daily_rates", "Today's rates", Icons.Default.LocalOffer)
    object Reports : Screen("reports", "Reports", Icons.Default.Assessment)
    object Cashbook : Screen("cashbook", "Cashbook")
    object Bills : Screen("bills", "Bills")
    object Inventory : Screen("inventory", "Inventory")
    object Collections : Screen("collections", "Collections")
    object StaffManagement : Screen("staff_management", "Staff & Devices")
    object Setup : Screen("setup", "Staff setup")
    object CustomerDetail : Screen("customer_detail/{customerId}", "Customer ledger") {
        fun createRoute(customerId: String) = "customer_detail/$customerId"
    }

    companion object {
        val BottomNavScreens: List<Screen>
            get() = listOf(Home, Ledger, Estimate, Settings)
    }
}
