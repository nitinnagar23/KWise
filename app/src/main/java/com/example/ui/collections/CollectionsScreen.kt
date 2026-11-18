package com.example.ui.collections

import com.example.ui.theme.kw
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.CattleFeedRepository
import com.example.model.Customer
import com.example.model.PaymentEntry
import com.example.model.SyncStatus
import com.example.model.User
import com.example.reminder.ReminderScheduler
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.util.CurrencyUtils
import com.example.util.ShopAnalytics
import com.example.util.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class CollectionsTab {
    PENDING,
    TODAY,
    UPCOMING,
    NO_DATE
}

@Composable
fun CollectionsScreen(
    repository: CattleFeedRepository,
    currentUser: User?,
    onBack: () -> Unit,
    onCustomerClick: (String) -> Unit = {}
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val customers by repository.allCustomers.collectAsState(initial = emptyList())
    val payments by repository.allPayments.collectAsState(initial = emptyList())
    val sales by repository.allSales.collectAsState(initial = emptyList())
    val settings by repository.settingsFlow.collectAsState(initial = null)

    var selectedTab by remember { mutableStateOf(CollectionsTab.PENDING) }
    var metaRefreshTick by remember { mutableIntStateOf(0) }

    val allTabs = remember { CollectionsTab.entries }
    val pagerState = rememberPagerState(
        initialPage = selectedTab.ordinal,
        pageCount = { allTabs.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        val targetTab = allTabs.getOrElse(pagerState.currentPage) { CollectionsTab.PENDING }
        if (selectedTab != targetTab) {
            selectedTab = targetTab
        }
    }

    val activeTab = allTabs.getOrElse(pagerState.currentPage) { selectedTab }

    // ONLY mask total Overdue, total due today, and total coming up (and NO eye icon)
    var maskTotalOverdue by remember { mutableStateOf(true) }
    var maskTotalDueToday by remember { mutableStateOf(true) }
    var maskTotalComingUp by remember { mutableStateOf(true) }

    var customerForSetPromise by remember { mutableStateOf<Customer?>(null) }
    var customerForCollect by remember { mutableStateOf<Customer?>(null) }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(60_000L)
        }
    }

    val dayMs = 24L * 60L * 60L * 1000L
    val startOfToday = remember(nowMillis / 60_000L) {
        ShopAnalytics.getStartOfTodayMillis(nowMillis)
    }
    val endOfToday = remember(startOfToday) {
        startOfToday + dayMs - 1L
    }

    val promiseMetaMap = remember(customers, metaRefreshTick) {
        customers.associate { c ->
            c.id to CollectionsPromisePrefs.load(context, c.id)
        }
    }

    val udhariCustomers = remember(customers) {
        customers.filter { !it.isDeleted && it.totalUdhari > BigDecimal.ZERO }
    }

    val pendingCustomers = remember(udhariCustomers, startOfToday) {
        udhariCustomers
            .filter { it.promiseDate != null && it.promiseDate!! < startOfToday }
            .sortedBy { it.promiseDate ?: 0L }
    }

    val todayCustomers = remember(udhariCustomers, startOfToday, endOfToday) {
        udhariCustomers
            .filter { it.promiseDate != null && it.promiseDate!! in startOfToday..endOfToday }
            .sortedByDescending { it.totalUdhari }
    }

    val upcomingCustomers = remember(udhariCustomers, endOfToday) {
        udhariCustomers
            .filter { it.promiseDate != null && it.promiseDate!! > endOfToday }
            .sortedBy { it.promiseDate ?: Long.MAX_VALUE }
    }

    val noDateCustomers = remember(udhariCustomers) {
        udhariCustomers
            .filter { it.promiseDate == null }
            .sortedByDescending { it.totalUdhari }
    }

    val totalOverdueAmount = remember(pendingCustomers) {
        pendingCustomers.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.totalUdhari) }
    }
    val totalDueTodayAmount = remember(todayCustomers) {
        todayCustomers.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.totalUdhari) }
    }
    val totalComingUpAmount = remember(upcomingCustomers) {
        upcomingCustomers.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.totalUdhari) }
    }

    // Repayments collected today (for the "Collected today" cards at the bottom of Today tab)
    val todayPayments = remember(payments, startOfToday, endOfToday) {
        payments
            .filter { !it.isDeleted && it.date in startOfToday..endOfToday }
            .sortedByDescending { it.date }
    }
    val customerById = remember(customers) {
        customers.associateBy { it.id }
    }

    val hasAnyPaymentByCustomer = remember(payments) {
        payments.filter { !it.isDeleted }.groupBy { it.customerId }
    }
    val oldestSaleDateByCustomer = remember(sales) {
        sales.filter { !it.isDeleted }
            .groupBy { it.customerId }
            .mapValues { (_, list) -> list.minOfOrNull { it.date } }
    }

    val noPhoneToastMsg = tr("No phone number recorded", "कोई फ़ोन नंबर दर्ज नहीं है")
    val whatsappUnavailableMsg = tr("WhatsApp not available", "व्हाट्सएप उपलब्ध नहीं है")
    val promiseSavedMsg = tr("Promise date saved", "वादा तारीख़ सेव हो गई")
    val paymentCollectedMsg = tr("Payment recorded", "भुगतान दर्ज किया गया")
    val remindAllEmptyMsg = tr("No customers to remind in this tab", "इस टैब में रिमाइंड करने के लिए कोई ग्राहक नहीं है")

    fun dialCustomer(customer: Customer) {
        val primaryPhone = customer.phone.ifBlank {
            customer.getTaggedPhones().firstOrNull()?.number ?: ""
        }
        if (primaryPhone.isNotBlank()) {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$primaryPhone"))
            try {
                context.startActivity(intent)
            } catch (_: Exception) {}
        } else {
            Toast.makeText(context, noPhoneToastMsg, Toast.LENGTH_SHORT).show()
        }
    }

    fun sendWhatsAppReminder(customer: Customer) {
        val msg = ShopAnalytics.buildSmartHindiUdhariReminder(
            customer = customer,
            shopName = settings?.shopName ?: "KhataWise Store",
            shopUpiId = settings?.shopUpiId ?: ""
        )
        val primaryPhone = customer.phone.ifBlank {
            customer.getTaggedPhones().firstOrNull()?.number ?: ""
        }
        val phoneDigits = primaryPhone.filter { it.isDigit() }
        val formattedPhone = if (phoneDigits.length == 10) "91$phoneDigits" else phoneDigits
        val uriStr = if (formattedPhone.isNotBlank()) {
            "https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(msg)}"
        } else {
            "https://api.whatsapp.com/send?text=${Uri.encode(msg)}"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr))
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, whatsappUnavailableMsg, Toast.LENGTH_SHORT).show()
        }
    }

    fun remindAllInList(list: List<Customer>) {
        if (list.isEmpty()) {
            Toast.makeText(context, remindAllEmptyMsg, Toast.LENGTH_SHORT).show()
            return
        }
        val firstWithPhone = list.firstOrNull {
            it.phone.isNotBlank() || it.getTaggedPhones().isNotEmpty()
        } ?: list.first()
        sendWhatsAppReminder(firstWithPhone)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlatDarkBg)
    ) {
        // 1. Top Header: Back button + Title/Subtitle + Calendar button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    onClick = onBack,
                    shape = RoundedCornerShape(13.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("collections_back_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = tr("Back", "वापस"),
                            tint = FlatTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = tr("Collections", "वसूली"),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                        color = FlatTextPrimary
                    )
                    Text(
                        text = tr("Promises & reminders", "वादे और रिमाइंडर"),
                        fontSize = 13.sp,
                        color = FlatTextSecondary
                    )
                }
            }

            Surface(
                onClick = {
                    val candidate = when (selectedTab) {
                        CollectionsTab.NO_DATE -> noDateCustomers.firstOrNull()
                        CollectionsTab.PENDING -> pendingCustomers.firstOrNull()
                        CollectionsTab.TODAY -> todayCustomers.firstOrNull()
                        CollectionsTab.UPCOMING -> upcomingCustomers.firstOrNull()
                    } ?: udhariCustomers.firstOrNull()
                    if (candidate != null) {
                        customerForSetPromise = candidate
                    }
                },
                shape = RoundedCornerShape(13.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder),
                modifier = Modifier
                    .size(44.dp)
                    .testTag("collections_calendar_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = tr("Set promise date", "वादा तारीख़ सेट करें"),
                        tint = FlatTextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // 2. 4-Tab Bar: Pending | Today | Upcoming | No Date
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CollectionsTabItem(
                    label = tr("Pending", "बाकी"),
                    count = pendingCustomers.size,
                    badgeBg = ColCrimsonSoftBg,
                    badgeTextColor = ColCrimson,
                    selected = activeTab == CollectionsTab.PENDING,
                    onClick = {
                        selectedTab = CollectionsTab.PENDING
                        scope.launch { pagerState.animateScrollToPage(CollectionsTab.PENDING.ordinal) }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_pending")
                )
                CollectionsTabItem(
                    label = tr("Today", "आज"),
                    count = todayCustomers.size,
                    badgeBg = ColAmberSoftBg,
                    badgeTextColor = ColAmberText,
                    selected = activeTab == CollectionsTab.TODAY,
                    onClick = {
                        selectedTab = CollectionsTab.TODAY
                        scope.launch { pagerState.animateScrollToPage(CollectionsTab.TODAY.ordinal) }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_today")
                )
                CollectionsTabItem(
                    label = tr("Upcoming", "आगामी"),
                    count = upcomingCustomers.size,
                    badgeBg = ColBlueSoftBg,
                    badgeTextColor = ColBlueBadgeText,
                    selected = activeTab == CollectionsTab.UPCOMING,
                    onClick = {
                        selectedTab = CollectionsTab.UPCOMING
                        scope.launch { pagerState.animateScrollToPage(CollectionsTab.UPCOMING.ordinal) }
                    },
                    modifier = Modifier
                        .weight(1.1f)
                        .testTag("tab_upcoming")
                )
                CollectionsTabItem(
                    label = tr("No date", "बिना तारीख़"),
                    count = noDateCustomers.size,
                    badgeBg = ColNoDatePillBg,
                    badgeTextColor = ColNoDatePillText,
                    selected = activeTab == CollectionsTab.NO_DATE,
                    onClick = {
                        selectedTab = CollectionsTab.NO_DATE
                        scope.launch { pagerState.animateScrollToPage(CollectionsTab.NO_DATE.ordinal) }
                    },
                    modifier = Modifier
                        .weight(1.05f)
                        .testTag("tab_no_date")
                )
            }
            HorizontalDivider(color = FlatDarkBorder)
        }

        // 3. Tab Content (Swipeable HorizontalPager)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            beyondViewportPageCount = 1
        ) { pageIndex ->
            val pageTab = allTabs.getOrElse(pageIndex) { CollectionsTab.PENDING }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (pageTab) {
                    CollectionsTab.PENDING -> {
                    // OVERDUE Summary Card (MASKED by default, NO eye icon per user instructions)
                    item {
                        CollectionsSummaryCard(
                            headerLabel = tr("OVERDUE", "अतिदेय (OVERDUE)"),
                            masked = maskTotalOverdue,
                            totalAmount = totalOverdueAmount,
                            amountColor = ColCrimson,
                            subtitle = "${pendingCustomers.size} ${tr("customers · promise date passed", "ग्राहक · वादा तारीख़ बीत गई")}",
                            onToggleMask = { maskTotalOverdue = !maskTotalOverdue },
                            onRemindAll = { remindAllInList(pendingCustomers) },
                            amountTestTag = "collections_total_overdue_text"
                        )
                    }

                    if (pendingCustomers.isEmpty()) {
                        item {
                            EmptyCollectionsPlaceholder(
                                text = tr(
                                    "No overdue collection promises pending.",
                                    "कोई अतिदेय वसूली वादा बाकी नहीं है।"
                                )
                            )
                        }
                    } else {
                        val shortDateFmt = SimpleDateFormat("d MMM", Locale.ENGLISH)
                        items(pendingCustomers, key = { it.id }) { customer ->
                            val pDate = customer.promiseDate ?: startOfToday
                            val daysLate = (((startOfToday - pDate) / dayMs).toInt()).coerceAtLeast(1)
                            val meta = promiseMetaMap[customer.id] ?: CustomerPromiseMeta()
                            val hadPartialPayment = !hasAnyPaymentByCustomer[customer.id].isNullOrEmpty()
                            val promiseCaption = buildString {
                                append("Promised ${shortDateFmt.format(Date(pDate))}")
                                when {
                                    meta.note.isNotBlank() -> append(" · ${meta.note}")
                                    hadPartialPayment -> append(" · paid part, rest pending")
                                    else -> append(" · no payment yet")
                                }
                            }

                            CollectionCustomerCard(
                                customer = customer,
                                statusPillText = "$daysLate ${if (daysLate == 1) "day late" else "days late"}",
                                statusPillBg = ColCrimsonSoftBg,
                                statusPillTextColor = ColCrimson,
                                promiseCaption = promiseCaption,
                                middleActionLabel = tr("Remind", "रिमाइंड"),
                                middleActionOutlinedGreen = false,
                                onCardClick = { onCustomerClick(customer.id) },
                                onSetPromiseClick = { customerForSetPromise = customer },
                                onCallClick = { dialCustomer(customer) },
                                onMiddleActionClick = { sendWhatsAppReminder(customer) },
                                onCollectClick = { customerForCollect = customer }
                            )
                        }
                    }
                }

                CollectionsTab.TODAY -> {
                    // DUE TODAY Summary Card (MASKED by default, NO eye icon per user instructions)
                    item {
                        CollectionsSummaryCard(
                            headerLabel = tr("DUE TODAY", "आज देय (DUE TODAY)"),
                            masked = maskTotalDueToday,
                            totalAmount = totalDueTodayAmount,
                            amountColor = ColAmber,
                            subtitle = "${todayCustomers.size} ${tr("customers promised today", "ग्राहकों ने आज का वादा किया")}",
                            onToggleMask = { maskTotalDueToday = !maskTotalDueToday },
                            onRemindAll = { remindAllInList(todayCustomers) },
                            amountTestTag = "collections_total_due_today_text"
                        )
                    }

                    if (todayCustomers.isEmpty() && todayPayments.isEmpty()) {
                        item {
                            com.example.ui.components.AllCaughtUpStateCard()
                        }
                    } else {
                        items(todayCustomers, key = { it.id }) { customer ->
                            val meta = promiseMetaMap[customer.id] ?: CustomerPromiseMeta()
                            val promiseCaption = if (meta.note.isNotBlank()) {
                                "Promised today · ${meta.note}"
                            } else {
                                "Promised today"
                            }

                            CollectionCustomerCard(
                                customer = customer,
                                statusPillText = tr("Due today", "आज देय"),
                                statusPillBg = ColAmberSoftBg,
                                statusPillTextColor = ColAmberText,
                                promiseCaption = promiseCaption,
                                middleActionLabel = tr("Remind", "रिमाइंड"),
                                middleActionOutlinedGreen = false,
                                onCardClick = { onCustomerClick(customer.id) },
                                onSetPromiseClick = { customerForSetPromise = customer },
                                onCallClick = { dialCustomer(customer) },
                                onMiddleActionClick = { sendWhatsAppReminder(customer) },
                                onCollectClick = { customerForCollect = customer }
                            )
                        }
                    }

                    // "Collected today" cards at the bottom of Today tab
                    if (todayPayments.isNotEmpty()) {
                        items(todayPayments, key = { "collected_${it.id}" }) { payment ->
                            val cust = customerById[payment.customerId]
                            CollectedTodayBannerCard(
                                payment = payment,
                                customer = cust
                            )
                        }
                    }
                }

                CollectionsTab.UPCOMING -> {
                    // COMING UP Summary Card (MASKED by default, NO eye icon per user instructions)
                    item {
                        CollectionsSummaryCard(
                            headerLabel = tr("COMING UP", "आगामी (COMING UP)"),
                            masked = maskTotalComingUp,
                            totalAmount = totalComingUpAmount,
                            amountColor = ColBlue,
                            subtitle = "${upcomingCustomers.size} ${tr("customers · next 7 days", "ग्राहक · अगले 7 दिन")}",
                            onToggleMask = { maskTotalComingUp = !maskTotalComingUp },
                            onRemindAll = { remindAllInList(upcomingCustomers) },
                            amountTestTag = "collections_total_coming_up_text"
                        )
                    }

                    if (upcomingCustomers.isEmpty()) {
                        item {
                            EmptyCollectionsPlaceholder(
                                text = tr(
                                    "No upcoming collection promises scheduled.",
                                    "कोई आगामी वसूली वादा निर्धारित नहीं है।"
                                )
                            )
                        }
                    } else {
                        val headerDateFmt = SimpleDateFormat("EEE d MMM", Locale.ENGLISH)
                        val shortDateFmt = SimpleDateFormat("d MMM", Locale.ENGLISH)
                        val groupedUpcoming = upcomingCustomers.groupBy { c ->
                            val pDate = c.promiseDate ?: (startOfToday + dayMs)
                            ((pDate - startOfToday) / dayMs).toInt().coerceAtLeast(1)
                        }

                        groupedUpcoming.forEach { (daysAhead, groupCustomers) ->
                            val sampleDate = groupCustomers.first().promiseDate ?: (startOfToday + daysAhead * dayMs)
                            val formattedHeaderDate = headerDateFmt.format(Date(sampleDate)).uppercase(Locale.ENGLISH)
                            val sectionHeader = if (daysAhead == 1) {
                                "TOMORROW · $formattedHeaderDate"
                            } else {
                                formattedHeaderDate
                            }

                            item(key = "upcoming_header_$daysAhead") {
                                Text(
                                    text = sectionHeader,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextSecondary,
                                    letterSpacing = 0.6.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            items(groupCustomers, key = { it.id }) { customer ->
                                val meta = promiseMetaMap[customer.id] ?: CustomerPromiseMeta()
                                val pDate = customer.promiseDate ?: sampleDate
                                val promiseCaption = when {
                                    meta.note.isNotBlank() -> {
                                        if (meta.note.startsWith("Promised", ignoreCase = true)) {
                                            meta.note
                                        } else {
                                            "Promised ${meta.note}"
                                        }
                                    }
                                    daysAhead == 1 -> "Promised for tomorrow"
                                    else -> "Promised for ${shortDateFmt.format(Date(pDate))}"
                                }

                                CollectionCustomerCard(
                                    customer = customer,
                                    statusPillText = "In $daysAhead ${if (daysAhead == 1) "day" else "days"}",
                                    statusPillBg = ColBlueSoftBg,
                                    statusPillTextColor = ColBlueBadgeText,
                                    promiseCaption = promiseCaption,
                                    middleActionLabel = tr("Remind", "रिमाइंड"),
                                    middleActionOutlinedGreen = false,
                                    onCardClick = { onCustomerClick(customer.id) },
                                    onSetPromiseClick = { customerForSetPromise = customer },
                                    onCallClick = { dialCustomer(customer) },
                                    onMiddleActionClick = { sendWhatsAppReminder(customer) },
                                    onCollectClick = { customerForCollect = customer }
                                )
                            }
                        }
                    }
                }

                CollectionsTab.NO_DATE -> {
                    // Top Info Banner Card
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("no_date_info_banner"),
                            shape = RoundedCornerShape(16.dp),
                            color = ColNoDateBannerBg,
                            border = BorderStroke(1.dp, FlatDarkBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = tr("Udhari with no promise date", "बिना वादा तारीख़ वाली उधारी"),
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary
                                )
                                Text(
                                    text = tr(
                                        "Set a date so reminders go out on time.",
                                        "तारीख़ सेट करें ताकि रिमाइंडर समय पर भेजे जा सकें।"
                                    ),
                                    fontSize = 13.sp,
                                    color = FlatTextSecondary
                                )
                            }
                        }
                    }

                    if (noDateCustomers.isEmpty()) {
                        item {
                            EmptyCollectionsPlaceholder(
                                text = tr(
                                    "All customers with udhari have a scheduled promise date.",
                                    "सभी उधारी वाले ग्राहकों की वादा तारीख़ सेट है।"
                                )
                            )
                        }
                    } else {
                        val shortDateFmt = SimpleDateFormat("d MMM", Locale.ENGLISH)
                        items(noDateCustomers, key = { it.id }) { customer ->
                            val sinceMillis = oldestSaleDateByCustomer[customer.id] ?: customer.createdAt
                            val caption = "Udhari since ${shortDateFmt.format(Date(sinceMillis))} · no date set"

                            CollectionCustomerCard(
                                customer = customer,
                                statusPillText = tr("No date", "कोई तारीख़ नहीं"),
                                statusPillBg = ColNoDatePillBg,
                                statusPillTextColor = ColNoDatePillText,
                                promiseCaption = caption,
                                middleActionLabel = tr("Set date", "तारीख़ सेट करें"),
                                middleActionOutlinedGreen = true,
                                onCardClick = { onCustomerClick(customer.id) },
                                onSetPromiseClick = { customerForSetPromise = customer },
                                onCallClick = { dialCustomer(customer) },
                                onMiddleActionClick = { customerForSetPromise = customer },
                                onCollectClick = { customerForCollect = customer }
                            )
                        }
                    }
                }
            }
        }
        }
    }

    // Set Promise Date Dialog
    if (customerForSetPromise != null) {
        val target = customerForSetPromise!!
        val existingMeta = promiseMetaMap[target.id] ?: CustomerPromiseMeta()
        val pDate = target.promiseDate
        val statusSubtitle = when {
            pDate == null -> "No date set"
            pDate < startOfToday -> {
                val late = (((startOfToday - pDate) / dayMs).toInt()).coerceAtLeast(1)
                "Overdue $late ${if (late == 1) "day" else "days"}"
            }
            pDate in startOfToday..endOfToday -> "Due today"
            else -> {
                val ahead = (((pDate - startOfToday) / dayMs).toInt()).coerceAtLeast(1)
                "Due in $ahead ${if (ahead == 1) "day" else "days"}"
            }
        }

        SetPromiseDateDialog(
            customer = target,
            existingMeta = existingMeta,
            startOfToday = startOfToday,
            statusSubtitle = statusSubtitle,
            onDismiss = { customerForSetPromise = null },
            onSavePromise = { selectedMillis, promisedAmt, note, whatsappReminder ->
                CollectionsPromisePrefs.save(
                    context = context,
                    customerId = target.id,
                    meta = CustomerPromiseMeta(
                        promisedAmount = promisedAmt,
                        note = note,
                        whatsappReminder = whatsappReminder
                    )
                )
                metaRefreshTick++

                val updatedCustomer = target.copy(
                    promiseDate = selectedMillis,
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = SyncStatus.PENDING
                )
                scope.launch {
                    repository.saveCustomer(updatedCustomer)
                    try {
                        ReminderScheduler.scheduleCustomerReminder(context, updatedCustomer)
                    } catch (_: Throwable) {}
                    Toast.makeText(context, promiseSavedMsg, Toast.LENGTH_SHORT).show()
                    customerForSetPromise = null
                }
            }
        )
    }

    // Collect Payment Dialog
    if (customerForCollect != null) {
        val target = customerForCollect!!
        CollectPaymentDialog(
            customer = target,
            onDismiss = { customerForCollect = null },
            onConfirmCollect = { amount, note, customDateMillis ->
                scope.launch {
                    try {
                        repository.recordPayment(
                            customerId = target.id,
                            amount = amount,
                            note = note,
                            collectedByUserId = currentUser?.id ?: "",
                            actorRole = currentUser?.role,
                            customDateMillis = customDateMillis
                        )
                        Toast.makeText(context, paymentCollectedMsg, Toast.LENGTH_SHORT).show()
                    } catch (_: Throwable) {}
                    customerForCollect = null
                }
            }
        )
    }
}

@Composable
private fun CollectionsTabItem(
    label: String,
    count: Int,
    badgeBg: Color,
    badgeTextColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) ColBrandGreen else FlatTextSecondary,
                maxLines = 1
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = badgeBg
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeTextColor,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(if (selected) ColBrandGreen else Color.Transparent)
        )
    }
}

@Composable
private fun CollectionsSummaryCard(
    headerLabel: String,
    masked: Boolean,
    totalAmount: BigDecimal,
    amountColor: Color,
    subtitle: String,
    onToggleMask: () -> Unit,
    onRemindAll: () -> Unit,
    amountTestTag: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = FlatDarkCard,
        border = BorderStroke(1.dp, FlatDarkBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tapping the summary column reveals/hides the total (NO eye icon per user instructions)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onToggleMask() }
                    .padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = headerLabel,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlatTextSecondary,
                    letterSpacing = 0.6.sp
                )
                Text(
                    text = if (masked) "₹ • • • •" else CurrencyUtils.formatInr(totalAmount),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    color = amountColor,
                    style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.testTag(amountTestTag)
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = FlatTextSecondary
                )
            }

            OutlinedButton(
                onClick = onRemindAll,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, ColBrandGreen),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = FlatDarkCard,
                    contentColor = ColBrandGreen
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                modifier = Modifier.testTag("remind_all_btn")
            ) {
                Text(
                    text = tr("Remind all", "सभी को याद दिलाएं"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColBrandGreen
                )
            }
        }
    }
}

@Composable
private fun CollectionCustomerCard(
    customer: Customer,
    statusPillText: String,
    statusPillBg: Color,
    statusPillTextColor: Color,
    promiseCaption: String,
    middleActionLabel: String,
    middleActionOutlinedGreen: Boolean,
    onCardClick: () -> Unit,
    onSetPromiseClick: () -> Unit,
    onCallClick: () -> Unit,
    onMiddleActionClick: () -> Unit,
    onCollectClick: () -> Unit
) {
    val primaryPhone = remember(customer) {
        customer.phone.ifBlank {
            customer.getTaggedPhones().firstOrNull()?.number ?: ""
        }
    }
    val villageAndPhone = remember(customer.village, primaryPhone) {
        buildString {
            if (customer.village.isNotBlank()) {
                append(customer.village)
                if (primaryPhone.isNotBlank()) append(" · ")
            }
            append(primaryPhone)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("collection_customer_card_${customer.id}"),
        shape = RoundedCornerShape(18.dp),
        color = FlatDarkCard,
        border = BorderStroke(1.dp, FlatDarkBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Avatar + Name/Sub | UNMASKED Amount + Status Pill (NO eye icon)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ColAvatarBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = computeCustomerInitials(customer.name),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColBlue
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = customer.name,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (villageAndPhone.isNotBlank()) {
                            Text(
                                text = villageAndPhone,
                                fontSize = 13.sp,
                                color = FlatTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Per-customer amount is ALWAYS UNMASKED and has NO eye icon per user instructions
                    Text(
                        text = CurrencyUtils.formatInr(customer.totalUdhari),
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColCrimson,
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        modifier = Modifier.testTag("collection_customer_amount_${customer.id}")
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusPillBg,
                        modifier = Modifier.clickable { onSetPromiseClick() }
                    ) {
                        Text(
                            text = statusPillText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusPillTextColor,
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Middle Promise Caption (tapping also opens Set promise date dialog)
            Text(
                text = promiseCaption,
                fontSize = 13.5.sp,
                color = FlatTextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSetPromiseClick() }
            )

            // Bottom 3 Action Buttons Row: Call | Remind (or Set date) | Collect
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Call Button
                Surface(
                    onClick = onCallClick,
                    shape = RoundedCornerShape(12.dp),
                    color = ColSoftGrayBtn,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("collection_call_btn_${customer.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Call,
                            contentDescription = null,
                            tint = FlatTextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tr("Call", "कॉल"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                    }
                }

                // 2. Middle Button: "Remind" or "Set date"
                if (middleActionOutlinedGreen) {
                    Surface(
                        onClick = onMiddleActionClick,
                        shape = RoundedCornerShape(12.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.5.dp, ColBrandGreen),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("collection_set_date_btn_${customer.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarToday,
                                contentDescription = null,
                                tint = ColBrandGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = middleActionLabel,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColBrandGreen
                            )
                        }
                    }
                } else {
                    Surface(
                        onClick = onMiddleActionClick,
                        shape = RoundedCornerShape(12.dp),
                        color = ColSoftGrayBtn,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("collection_remind_btn_${customer.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = middleActionLabel,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextPrimary
                            )
                        }
                    }
                }

                // 3. Collect Button
                Button(
                    onClick = onCollectClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ColBrandGreen,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("collection_collect_btn_${customer.id}")
                ) {
                    Text(
                        text = tr("Collect", "वसूलें"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectedTodayBannerCard(
    payment: PaymentEntry,
    customer: Customer?
) {
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.ENGLISH) }
    val custName = customer?.name ?: "Customer"
    val paidStatusLabel = if (customer != null && customer.totalUdhari <= BigDecimal.ZERO) {
        "$custName paid in full · ${timeFormatter.format(Date(payment.date))}"
    } else {
        "$custName paid ${CurrencyUtils.formatInr(payment.amount)} · ${timeFormatter.format(Date(payment.date))}"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("collected_today_card_${payment.id}"),
        shape = RoundedCornerShape(16.dp),
        color = ColMintBg,
        border = BorderStroke(1.dp, ColMintBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = tr("Collected today", "आज वसूला गया"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColBrandGreen
                )
                Text(
                    text = paidStatusLabel,
                    fontSize = 13.sp,
                    color = kw(0xFF166534)
                )
            }

            Text(
                text = tr("Done", "पूरा हुआ"),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = ColBrandGreen
            )
        }
    }
}

@Composable
private fun EmptyCollectionsPlaceholder(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = FlatDarkCard,
        border = BorderStroke(1.dp, FlatDarkBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 36.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                color = FlatTextMuted,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
