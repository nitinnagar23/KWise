package com.example

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.auth.SetupScreen
import com.example.ui.billing.NewBillScreen
import com.example.ui.bills.BillsScreen
import com.example.ui.cashbook.CashbookScreen
import com.example.ui.collections.CollectionsScreen
import com.example.ui.estimate.EstimateCalculatorScreen
import com.example.ui.home.HomeScreen
import com.example.ui.inventory.InventoryScreen
import com.example.ui.ledger.CustomerDetailScreen
import com.example.ui.ledger.CustomersScreen
import com.example.ui.ledger.LedgerScreen
import com.example.ui.navigation.Screen
import com.example.ui.onboarding.FirstTimeSetupWizardScreen
import com.example.ui.onboarding.OnboardingPreferences
import com.example.ui.onboarding.QuickStartDestination
import com.example.ui.onboarding.WelcomeTourScreen
import com.example.ui.purchases.PurchasesScreen
import com.example.ui.rates.DailyRatesScreen
import com.example.ui.reports.ReportsScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.staff.StaffManagementScreen
import com.example.ui.theme.CGreen
import com.example.ui.theme.CGreenContainer
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.util.ProvideAppLanguage
import com.example.util.tr

class MainActivity : FragmentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val repository = mainViewModel.repository
            val settings by mainViewModel.settings.collectAsState()
            val activeUser by mainViewModel.activeUser.collectAsState()

            val isDarkTheme = when (settings?.themeMode) {
                "DARK" -> true
                "SYSTEM" -> isSystemInDarkTheme()
                else -> false
            }
            val languageCode = settings?.language ?: "en"

            ProvideAppLanguage(languageCode = languageCode) {
                MyApplicationTheme(darkTheme = isDarkTheme) {
                    var showSetupManually by remember { mutableStateOf(false) }
                    var showFirstTimeWizard by remember { mutableStateOf(false) }
                    var firstTimeWizardInitialStep by remember { mutableStateOf(1) }
                    var showWelcomeTour by remember { mutableStateOf(false) }
                    var pendingQuickStartRoute by remember { mutableStateOf<String?>(null) }

                    if (activeUser == null || showSetupManually) {
                        BackHandler(enabled = showSetupManually) {
                            showSetupManually = false
                        }
                        SetupScreen(
                            repository = repository,
                            onSetupComplete = {
                                showSetupManually = false
                                val currentShop = settings?.shopName.orEmpty()
                                val isDefaultShop = currentShop.isBlank() ||
                                    currentShop == "KhataWise Store" ||
                                    currentShop == "Pashu Aahar Store"
                                if (!OnboardingPreferences.isSetupCompleted(this@MainActivity) && isDefaultShop) {
                                    firstTimeWizardInitialStep = 1
                                    showFirstTimeWizard = true
                                }
                            },
                            onBack = if (activeUser != null && showSetupManually) {
                                { showSetupManually = false }
                            } else null
                        )
                    } else if (showWelcomeTour) {
                        WelcomeTourScreen(
                            upiId = settings?.shopUpiId?.ifBlank { "999999@upi" } ?: "999999@upi",
                            onFinishTour = {
                                showWelcomeTour = false
                                showFirstTimeWizard = false
                            }
                        )
                    } else if (showFirstTimeWizard) {
                        FirstTimeSetupWizardScreen(
                            repository = repository,
                            currentUser = activeUser,
                            initialStep = firstTimeWizardInitialStep,
                            onFinishSetup = { destination ->
                                showFirstTimeWizard = false
                                pendingQuickStartRoute = when (destination) {
                                    QuickStartDestination.ADD_FIRST_ITEM -> Screen.Inventory.route
                                    QuickStartDestination.ADD_CUSTOMERS -> Screen.Customers.route
                                    QuickStartDestination.SET_TODAY_RATES -> Screen.DailyRates.route
                                    QuickStartDestination.INVITE_STAFF -> Screen.StaffManagement.route
                                    QuickStartDestination.HOME -> null
                                }
                            },
                            onLaunchTour = {
                                showFirstTimeWizard = false
                                showWelcomeTour = true
                            },
                            onBackOrCancel = {
                                showFirstTimeWizard = false
                            }
                        )
                    } else {
                        val navController = rememberNavController()
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

                        androidx.compose.runtime.LaunchedEffect(pendingQuickStartRoute) {
                            val targetRoute = pendingQuickStartRoute
                            if (targetRoute != null) {
                                pendingQuickStartRoute = null
                                navController.navigate(targetRoute)
                            }
                        }

                        val bottomNavItems = Screen.BottomNavScreens

                        val shouldShowTopBar = false

                        Scaffold(
                            containerColor = FlatDarkBg,
                            topBar = {
                                if (shouldShowTopBar) {
                                    TopAppBar(
                                        title = {
                                            Text(
                                                text = when (currentRoute) {
                                                    Screen.Purchases.route -> tr("Purchases & Hisab Kitab", "खरीद और हिसाब-किताब")
                                                    Screen.Reports.route -> tr("Reports, Profit & Insights", "रिपोर्ट, मुनाफा और विश्लेषण")
                                                    else -> "KhataWise"
                                                },
                                                fontWeight = FontWeight.Medium,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = FlatTextPrimary
                                            )
                                        },
                                        navigationIcon = {
                                            IconButton(
                                                onClick = { navController.popBackStack() },
                                                modifier = Modifier.testTag("top_back_btn")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = "Back",
                                                    tint = FlatTextPrimary
                                                )
                                            }
                                        },
                                        actions = {
                                            if (currentRoute != Screen.Settings.route) {
                                                IconButton(
                                                    onClick = { navController.navigate(Screen.Settings.route) },
                                                    modifier = Modifier.testTag("top_settings_btn")
                                                ) {
                                                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = FlatTextMuted)
                                                }
                                            }
                                        },
                                        colors = TopAppBarDefaults.topAppBarColors(
                                            containerColor = FlatDarkBg,
                                            titleContentColor = FlatTextPrimary
                                        )
                                    )
                                }
                            },
                            bottomBar = {
                                val showBottomBar = currentRoute in listOf(
                                    Screen.Home.route,
                                    Screen.Ledger.route,
                                    Screen.Estimate.route,
                                    Screen.Settings.route
                                )
                                if (showBottomBar) {
                                    val navBorderColor = FlatDarkBorder
                                    val navContainerColor = MaterialTheme.colorScheme.surface
                                    val selectedGreen = if (isDarkTheme) CGreen else androidx.compose.ui.graphics.Color(0xFF0E6F43)
                                    val selectedPillBg = if (isDarkTheme) CGreenContainer else androidx.compose.ui.graphics.Color(0xFFDDF3E8)
                                    val unselectedColor = if (isDarkTheme) FlatTextMuted else androidx.compose.ui.graphics.Color(0xFF525866)
                                    NavigationBar(
                                        containerColor = navContainerColor,
                                        tonalElevation = 0.dp,
                                        modifier = Modifier.drawBehind {
                                            drawLine(
                                                color = navBorderColor,
                                                start = Offset(0f, 0f),
                                                end = Offset(size.width, 0f),
                                                strokeWidth = 1.dp.toPx()
                                            )
                                        }
                                    ) {
                                        bottomNavItems.forEach { screen ->
                                            val localizedLabel = when (screen.route) {
                                                Screen.Home.route -> tr("Home", "होम")
                                                Screen.Ledger.route -> tr("Ledger", "खाता")
                                                Screen.Estimate.route -> tr("Calculator", "कैलकुलेटर")
                                                Screen.Settings.route -> tr("Settings", "सेटिंग्स")
                                                else -> screen.title
                                            }
                                            NavigationBarItem(
                                                selected = currentRoute == screen.route,
                                                onClick = {
                                                    if (currentRoute != screen.route) {
                                                        navController.navigate(screen.route) {
                                                            popUpTo(navController.graph.findStartDestination().id) {
                                                                saveState = true
                                                            }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    }
                                                },
                                                icon = {
                                                    if (screen.icon != null) {
                                                        Icon(screen.icon, contentDescription = localizedLabel)
                                                    }
                                                },
                                                label = {
                                                    Text(
                                                        text = localizedLabel,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = if (currentRoute == screen.route) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                },
                                                colors = NavigationBarItemDefaults.colors(
                                                    selectedIconColor = selectedGreen,
                                                    selectedTextColor = selectedGreen,
                                                    unselectedIconColor = unselectedColor,
                                                    unselectedTextColor = unselectedColor,
                                                    indicatorColor = selectedPillBg
                                                ),
                                                modifier = Modifier.testTag("nav_${screen.route}")
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                NavHost(
                                    navController = navController,
                                    startDestination = Screen.Home.route
                                ) {
                                    composable(Screen.Home.route) {
                                        HomeScreen(
                                            repository = repository,
                                            currentUser = activeUser,
                                            onNavigateToNewBill = { navController.navigate(Screen.NewBill.route) },
                                            onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                                            onNavigateToDailyRates = { navController.navigate(Screen.DailyRates.route) },
                                            onNavigateToEstimate = { navController.navigate(Screen.Estimate.route) },
                                            onNavigateToLedger = { navController.navigate(Screen.Ledger.route) },
                                            onNavigateToPurchases = { navController.navigate(Screen.Purchases.route) },
                                            onNavigateToCustomers = { navController.navigate(Screen.Customers.route) },
                                            onNavigateToInventory = { navController.navigate(Screen.Inventory.route) },
                                            onNavigateToBills = { navController.navigate(Screen.Bills.route) },
                                            onNavigateToCollections = { navController.navigate(Screen.Collections.route) },
                                            onNavigateToStaff = { navController.navigate(Screen.StaffManagement.route) },
                                            onSelectCustomer = { custId ->
                                                navController.navigate(Screen.CustomerDetail.createRoute(custId))
                                            }
                                        )
                                    }

                                    composable(Screen.NewBill.route) {
                                        NewBillScreen(
                                            repository = repository,
                                            currentUser = activeUser,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    composable(Screen.DailyRates.route) {
                                        DailyRatesScreen(
                                            repository = repository,
                                            currentUser = activeUser,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    composable(Screen.Ledger.route) {
                                        LedgerScreen(
                                            repository = repository,
                                            currentUser = activeUser,
                                            onCustomerClick = { custId ->
                                                navController.navigate(Screen.CustomerDetail.createRoute(custId))
                                            },
                                            onNavigateToNewBill = {
                                                navController.navigate(Screen.NewBill.route)
                                            }
                                        )
                                    }

                                    composable(Screen.Customers.route) {
                                        CustomersScreen(
                                            repository = repository,
                                            currentUser = activeUser,
                                            onCustomerClick = { custId ->
                                                navController.navigate(Screen.CustomerDetail.createRoute(custId))
                                            },
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    composable(Screen.Estimate.route) {
                                        EstimateCalculatorScreen(
                                            repository = repository,
                                            currentUser = activeUser,
                                            onBack = null,
                                            onNavigateToDailyRates = { navController.navigate(Screen.DailyRates.route) }
                                        )
                                    }

                                    composable(Screen.Purchases.route) {
                                        PurchasesScreen(
                                            repository = repository,
                                            currentUser = activeUser,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    composable(Screen.Reports.route) {
                                        ReportsScreen(
                                            repository = repository,
                                            currentUser = activeUser,
                                            onBack = { navController.popBackStack() },
                                            onNavigateToCollections = { navController.navigate(Screen.Collections.route) }
                                        )
                                    }

                                    composable(Screen.Settings.route) {
                                        SettingsScreen(
                                            repository = repository,
                                            currentUser = activeUser,
                                            onSwitchUser = { showSetupManually = true },
                                            onBack = null,
                                            onNavigateToCustomers = { navController.navigate(Screen.Customers.route) },
                                            onNavigateToCashbook = { navController.navigate(Screen.Cashbook.route) },
                                            onNavigateToReports = { navController.navigate(Screen.Reports.route) },
                                            onNavigateToSupplierLedger = { navController.navigate(Screen.Purchases.route) },
                                            onNavigateToBills = { navController.navigate(Screen.Bills.route) },
                                            onNavigateToInventory = { navController.navigate(Screen.Inventory.route) },
                                            onNavigateToCollections = { navController.navigate(Screen.Collections.route) },
                                            onNavigateToStaffManagement = { navController.navigate(Screen.StaffManagement.route) },
                                            onSelectCustomer = { custId ->
                                                navController.navigate(Screen.CustomerDetail.createRoute(custId))
                                            },
                                            onOpenSetupWizard = { step ->
                                                firstTimeWizardInitialStep = step
                                                showFirstTimeWizard = true
                                            },
                                            onOpenWelcomeTour = {
                                                showWelcomeTour = true
                                            }
                                        )
                                    }

                                    composable(Screen.StaffManagement.route) {
                                        StaffManagementScreen(
                                            repository = repository,
                                            currentUser = activeUser,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    composable(Screen.Cashbook.route) {
                                        CashbookScreen(
                                            repository = repository,
                                            currentUser = activeUser,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    composable(Screen.Bills.route) {
                                        BillsScreen(
                                            repository = repository,
                                            currentUser = activeUser,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    composable(Screen.Inventory.route) {
                                        InventoryScreen(
                                            repository = repository,
                                            currentUser = activeUser,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    composable(Screen.Collections.route) {
                                        CollectionsScreen(
                                            repository = repository,
                                            currentUser = activeUser,
                                            onBack = { navController.popBackStack() },
                                            onCustomerClick = { custId ->
                                                navController.navigate(Screen.CustomerDetail.createRoute(custId))
                                            }
                                        )
                                    }

                                    composable(
                                        route = Screen.CustomerDetail.route,
                                        arguments = listOf(navArgument("customerId") { type = NavType.StringType })
                                    ) { backStackEntry ->
                                        val custId = backStackEntry.arguments?.getString("customerId") ?: ""
                                        CustomerDetailScreen(
                                            customerId = custId,
                                            repository = repository,
                                            currentUser = activeUser,
                                            onBack = { navController.popBackStack() },
                                            onNavigateToNewBill = { navController.navigate(Screen.NewBill.route) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
