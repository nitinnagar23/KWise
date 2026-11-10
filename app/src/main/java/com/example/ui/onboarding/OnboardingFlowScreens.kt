package com.example.ui.onboarding

import com.example.ui.theme.KwSurface
import com.example.ui.theme.kw
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.GoogleAuthManager
import com.example.data.repository.CattleFeedRepository
import com.example.model.User
import com.example.ui.components.QrCodeView
import com.example.util.tr
import kotlinx.coroutines.launch
import java.math.BigDecimal

// Colors matching KhataWise – Onboarding & states.pdf
private val OnbPageBg: Color get() = kw(0xFFFBFBF9)
private val OnbCardBorder: Color get() = kw(0xFFE6E8EC)
private val OnbNavyText: Color get() = kw(0xFF0F1D2E)
private val OnbSubtleText: Color get() = kw(0xFF5A6578)
private val OnbBrandGreen: Color get() = kw(0xFF0E6F43)
private val OnbSoftGreenBg: Color get() = kw(0xFFDDF3E8)
private val OnbFieldBg: Color get() = kw(0xFFFFFFFF)
private val OnbFieldBorder: Color get() = kw(0xFFCBD5E1)
private val OnbErrorRed: Color get() = kw(0xFFB91C1C)
enum class QuickStartDestination {
    HOME,
    ADD_FIRST_ITEM,
    ADD_CUSTOMERS,
    SET_TODAY_RATES,
    INVITE_STAFF
}

object OnboardingPreferences {
    private const val PREFS_NAME = "khatawise_onboarding_prefs"
    private const val KEY_SETUP_DONE = "first_time_setup_done"
    private const val KEY_TOUR_DONE = "welcome_tour_done"
    private const val KEY_SHOP_TYPE = "shop_type"
    private const val KEY_VILLAGE_CITY = "village_or_city"
    private const val KEY_FULL_ADDRESS = "full_address"
    private const val KEY_HAS_GSTIN = "has_gstin"
    private const val KEY_PRICES_INCLUDE_GST = "prices_include_gst"
    private const val KEY_BILL_LANGUAGE = "bill_language"
    private const val KEY_BILL_PREFIX = "bill_prefix"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isSetupCompleted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SETUP_DONE, false)

    fun setSetupCompleted(context: Context, completed: Boolean) {
        prefs(context).edit().putBoolean(KEY_SETUP_DONE, completed).apply()
    }

    fun isWelcomeTourCompleted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TOUR_DONE, false)

    fun setWelcomeTourCompleted(context: Context, completed: Boolean) {
        prefs(context).edit().putBoolean(KEY_TOUR_DONE, completed).apply()
    }

    fun getShopType(context: Context): String =
        prefs(context).getString(KEY_SHOP_TYPE, "Feed & grain") ?: "Feed & grain"

    fun getVillageOrCity(context: Context): String =
        prefs(context).getString(KEY_VILLAGE_CITY, "") ?: ""

    fun getFullAddress(context: Context): String =
        prefs(context).getString(KEY_FULL_ADDRESS, "") ?: ""

    fun getHasGstin(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAS_GSTIN, false)

    fun getPricesIncludeGst(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PRICES_INCLUDE_GST, false)

    fun getBillLanguage(context: Context): String =
        prefs(context).getString(KEY_BILL_LANGUAGE, "English") ?: "English"

    fun getBillPrefix(context: Context): String =
        prefs(context).getString(KEY_BILL_PREFIX, "") ?: ""

    fun saveExtendedShopSetup(
        context: Context,
        shopType: String,
        villageOrCity: String,
        fullAddress: String,
        hasGstin: Boolean,
        pricesIncludeGst: Boolean,
        billLanguage: String,
        billPrefix: String
    ) {
        prefs(context).edit()
            .putString(KEY_SHOP_TYPE, shopType)
            .putString(KEY_VILLAGE_CITY, villageOrCity)
            .putString(KEY_FULL_ADDRESS, fullAddress)
            .putBoolean(KEY_HAS_GSTIN, hasGstin)
            .putBoolean(KEY_PRICES_INCLUDE_GST, pricesIncludeGst)
            .putString(KEY_BILL_LANGUAGE, billLanguage)
            .putString(KEY_BILL_PREFIX, billPrefix)
            .apply()
    }
}

/**
 * 3-Step First-Time Setup Wizard (Pages 1, 2, 3 of KhataWise – Onboarding & states.pdf)
 * + seamlessly launches the 4-Slide Welcome Tour when the user taps "Take a quick tour".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FirstTimeSetupWizardScreen(
    repository: CattleFeedRepository,
    currentUser: User?,
    initialStep: Int = 1,
    onFinishSetup: (QuickStartDestination) -> Unit,
    onLaunchTour: () -> Unit,
    onBackOrCancel: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by repository.settingsFlow.collectAsState(initial = null)
    val googleUser = GoogleAuthManager.currentUser.value

    var currentStep by remember { mutableIntStateOf(initialStep.coerceIn(1, 3)) }

    val resolvedOwnerName = remember(currentUser, googleUser) {
        currentUser?.name?.takeIf { it.isNotBlank() }
            ?: googleUser?.displayName?.takeIf { it.isNotBlank() }
            ?: googleUser?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "Owner"
    }
    val resolvedEmail = remember(currentUser, googleUser) {
        currentUser?.email?.takeIf { it.isNotBlank() }
            ?: googleUser?.email?.takeIf { it.isNotBlank() }
            ?: ""
    }

    // Step 1 fields
    var shopName by remember(settings) {
        val current = settings?.shopName.orEmpty()
        mutableStateOf(
            if (current.isBlank() || current == "KhataWise Store" || current == "Pashu Aahar Store") "" else current
        )
    }
    var selectedShopType by remember {
        mutableStateOf(OnboardingPreferences.getShopType(context))
    }
    var ownerName by remember(resolvedOwnerName) {
        mutableStateOf(resolvedOwnerName)
    }
    var mobileNumber by remember(settings, currentUser) {
        val raw = settings?.shopPhone?.takeIf { it.isNotBlank() }
            ?: currentUser?.phone.orEmpty()
        mutableStateOf(raw.removePrefix("+91").trim())
    }
    var villageOrCity by remember(settings) {
        val savedVillage = OnboardingPreferences.getVillageOrCity(context)
        mutableStateOf(
            savedVillage.ifBlank { settings?.shopAddress.orEmpty() }
        )
    }
    var fullAddress by remember {
        mutableStateOf(OnboardingPreferences.getFullAddress(context))
    }

    var step1Error by remember { mutableStateOf<String?>(null) }

    // Step 2 fields
    var upiId by remember(settings) {
        mutableStateOf(settings?.shopUpiId.orEmpty())
    }
    var isUpiVerified by remember(settings) {
        mutableStateOf(!settings?.shopUpiId.isNullOrBlank() && isValidUpiFormat(settings?.shopUpiId.orEmpty()))
    }
    var upiValidationError by remember { mutableStateOf<String?>(null) }

    var hasGstin by remember(settings) {
        mutableStateOf(
            OnboardingPreferences.getHasGstin(context) || !settings?.shopGstin.isNullOrBlank()
        )
    }
    var gstinInput by remember(settings) {
        mutableStateOf(settings?.shopGstin.orEmpty())
    }
    var pricesIncludeGst by remember {
        mutableStateOf(OnboardingPreferences.getPricesIncludeGst(context))
    }
    var billLanguage by remember(settings) {
        val saved = OnboardingPreferences.getBillLanguage(context)
        mutableStateOf(
            if (settings?.language == "hi" && saved == "English") "हिंदी" else saved
        )
    }
    var billPrefix by remember {
        mutableStateOf(OnboardingPreferences.getBillPrefix(context))
    }

    val requiredFieldsMsg = tr(
        "Please enter shop name, mobile number, and village/city.",
        "कृपया दुकान का नाम, मोबाइल नंबर और गाँव/शहर दर्ज करें।"
    )
    val invalidUpiMsg = tr(
        "Enter a valid UPI ID, like name@bank",
        "सही UPI ID दर्ज करें, जैसे name@bank"
    )

    fun persistAllShopDetails() {
        val finalShopName = shopName.trim().ifEmpty { "KhataWise Store" }
        val combinedAddress = buildString {
            if (villageOrCity.trim().isNotEmpty()) append(villageOrCity.trim())
            if (fullAddress.trim().isNotEmpty()) {
                if (isNotEmpty()) append(", ")
                append(fullAddress.trim())
            }
        }
        val formattedPhone = if (mobileNumber.trim().isNotEmpty()) {
            val clean = mobileNumber.trim().removePrefix("+91").trim()
            "+91 $clean"
        } else ""

        OnboardingPreferences.saveExtendedShopSetup(
            context = context,
            shopType = selectedShopType,
            villageOrCity = villageOrCity.trim(),
            fullAddress = fullAddress.trim(),
            hasGstin = hasGstin,
            pricesIncludeGst = pricesIncludeGst,
            billLanguage = billLanguage,
            billPrefix = billPrefix.trim()
        )
        OnboardingPreferences.setSetupCompleted(context, true)

        scope.launch {
            repository.updateStoreProfile(
                shopName = finalShopName,
                shopAddress = combinedAddress,
                shopPhone = formattedPhone,
                shopGstin = if (hasGstin) gstinInput.trim() else "",
                shopUpiId = upiId.trim(),
                watchThreshold = settings?.watchThreshold ?: BigDecimal("2000"),
                criticalThreshold = settings?.criticalThreshold ?: BigDecimal("5000"),
                performedByRole = currentUser?.role
            )
            if (currentUser != null && ownerName.trim().isNotEmpty()) {
                repository.updateAdminProfile(currentUser.id, ownerName.trim())
            }
            if (billLanguage == "हिंदी") {
                repository.updateLanguage("hi")
            } else if (billLanguage == "English") {
                repository.updateLanguage("en")
            }
        }
    }

    BackHandler {
        when {
            currentStep > 1 -> currentStep -= 1
            onBackOrCancel != null -> onBackOrCancel()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnbPageBg)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        when (currentStep) {
            1 -> {
                // ---------------------------------------------------------
                // STEP 1 OF 3: Tell us about your shop (Page 1 of PDF)
                // ---------------------------------------------------------
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (onBackOrCancel != null) {
                        Surface(
                            onClick = onBackOrCancel,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("setup_step1_back_btn"),
                            shape = RoundedCornerShape(12.dp),
                            color = KwSurface,
                            border = BorderStroke(1.dp, OnbCardBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = OnbNavyText,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    ThreeSegmentProgressBar(completedSegments = 1)

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = tr("Step 1 of 3", "चरण 1 / 3"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnbSubtleText
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = tr("Tell us about your shop", "अपनी दुकान के बारे में बताएं"),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnbNavyText,
                        letterSpacing = (-0.3).sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = tr(
                            "This shows on your bills. You can change it later in Settings.",
                            "यह आपके बिलों पर दिखेगा। आप इसे बाद में सेटिंग्स में बदल सकते हैं।"
                        ),
                        fontSize = 14.5.sp,
                        color = OnbSubtleText,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Signed-in Google Account Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = KwSurface,
                        border = BorderStroke(1.dp, OnbCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                val initial = resolvedOwnerName.trim().take(1).uppercase().ifEmpty { "K" }
                                Surface(
                                    modifier = Modifier.size(44.dp),
                                    shape = CircleShape,
                                    color = kw(0xFFE6EEF6)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = initial,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OnbNavyText
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = resolvedOwnerName,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OnbNavyText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (resolvedEmail.isNotBlank()) {
                                        Text(
                                            text = resolvedEmail,
                                            fontSize = 13.sp,
                                            color = OnbSubtleText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = OnbSoftGreenBg
                            ) {
                                Text(
                                    text = tr("Signed in", "साइन इन"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnbBrandGreen,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // SHOP NAME *
                    SetupFieldLabel(label = tr("SHOP NAME", "दुकान का नाम"), required = true)
                    SetupInputBox(
                        value = shopName,
                        onValueChange = {
                            shopName = it
                            step1Error = null
                        },
                        placeholder = tr("e.g. Shop Name", "जैसे शॉप का नाम"),
                        testTag = "onboarding_shop_name_input"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // SHOP TYPE
                    SetupFieldLabel(label = tr("SHOP TYPE", "दुकान का प्रकार"), required = false)
                    val shopTypes = listOf(
                        "Kirana / Grocery" to tr("Kirana / Grocery", "किराना / ग्रोसरी"),
                        "Feed & grain" to tr("Feed & grain", "पशु आहार और अनाज"),
                        "General store" to tr("General store", "जनरल स्टोर"),
                        "Other" to tr("Other", "अन्य")
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        shopTypes.forEach { (key, label) ->
                            val isSelected = selectedShopType == key
                            Surface(
                                onClick = { selectedShopType = key },
                                shape = RoundedCornerShape(50),
                                color = if (isSelected) OnbBrandGreen else kw(0xFFF1F2F6),
                                modifier = Modifier.testTag("shop_type_chip_${key.lowercase().replace(" ", "_")}")
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else OnbNavyText,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // OWNER NAME (with "Auto" pill inside right)
                    SetupFieldLabel(label = tr("OWNER NAME", "मालिक का नाम"), required = false)
                    SetupInputBox(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        placeholder = resolvedOwnerName,
                        trailingContent = {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = kw(0xFFF1F2F6)
                            ) {
                                Text(
                                    text = tr("Auto", "ऑटो"),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnbSubtleText,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        },
                        testTag = "onboarding_owner_name_input"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // MOBILE NUMBER *
                    SetupFieldLabel(label = tr("MOBILE NUMBER", "मोबाइल नंबर"), required = true)
                    SetupInputBox(
                        value = mobileNumber,
                        onValueChange = {
                            mobileNumber = it.filter { ch -> ch.isDigit() || ch == ' ' }.take(12)
                            step1Error = null
                        },
                        placeholder = "Mobile number",
                        leadingPrefix = "+91",
                        keyboardType = KeyboardType.Phone,
                        testTag = "onboarding_mobile_input"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // VILLAGE / CITY *
                    SetupFieldLabel(label = tr("VILLAGE / CITY", "गाँव / शहर"), required = true)
                    SetupInputBox(
                        value = villageOrCity,
                        onValueChange = {
                            villageOrCity = it
                            step1Error = null
                        },
                        placeholder = tr("Enter village or city", "गाँव या शहर का नाम"),
                        testTag = "onboarding_village_input"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // FULL ADDRESS (OPTIONAL)
                    SetupFieldLabel(
                        label = tr("FULL ADDRESS (OPTIONAL)", "पूरा पता (वैकल्पिक)"),
                        required = false
                    )
                    SetupInputBox(
                        value = fullAddress,
                        onValueChange = { fullAddress = it },
                        placeholder = tr("Shop no., street, landmark", "दुकान नंबर, गली, लैंडमार्क"),
                        testTag = "onboarding_address_input"
                    )

                    if (step1Error != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = step1Error!!,
                            color = OnbErrorRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Bottom Continue Button
                Button(
                    onClick = {
                        if (shopName.trim().isEmpty()) {
                            step1Error = requiredFieldsMsg
                            return@Button
                        }
                        step1Error = null
                        currentStep = 2
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OnbBrandGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("onboarding_step1_continue_btn")
                ) {
                    Text(
                        text = tr("Continue", "आगे बढ़ें"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            2 -> {
                // ---------------------------------------------------------
                // STEP 2 OF 3: How do you get paid? (Page 2 of PDF)
                // ---------------------------------------------------------
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Top Row: Back button + Skip button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { currentStep = 1 },
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("onboarding_step2_back_btn"),
                            shape = RoundedCornerShape(12.dp),
                            color = KwSurface,
                            border = BorderStroke(1.dp, OnbCardBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = OnbNavyText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                persistAllShopDetails()
                                currentStep = 3
                            },
                            modifier = Modifier.testTag("onboarding_step2_skip_btn")
                        ) {
                            Text(
                                text = tr("Skip", "छोड़ें"),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnbSubtleText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    ThreeSegmentProgressBar(completedSegments = 2)

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = tr("Step 2 of 3", "चरण 2 / 3"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnbSubtleText
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = tr("How do you get paid?", "भुगतान कैसे प्राप्त करें?"),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnbNavyText,
                        letterSpacing = (-0.3).sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = tr(
                            "Add your UPI ID so every bill has a Pay-now QR.",
                            "अपनी UPI ID जोड़ें ताकि हर बिल पर तुरंत भुगतान QR कोड दिखे।"
                        ),
                        fontSize = 14.5.sp,
                        color = OnbSubtleText,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // UPI ID Field with inline "Verify" button & inline error state (Page 2 & Page 11)
                    SetupFieldLabel(label = "UPI ID", required = false)
                    SetupInputBox(
                        value = upiId,
                        onValueChange = { updated ->
                            upiId = updated
                            isUpiVerified = false
                            upiValidationError = if (updated.endsWith("@")) {
                                invalidUpiMsg
                            } else {
                                null
                            }
                        },
                        placeholder = "999999@upi",
                        isError = upiValidationError != null,
                        trailingContent = {
                            Text(
                                text = tr("Verify", "सत्यापित करें"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnbBrandGreen,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isValidUpiFormat(upiId.trim())) {
                                            isUpiVerified = true
                                            upiValidationError = null
                                        } else {
                                            isUpiVerified = false
                                            upiValidationError = invalidUpiMsg
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("onboarding_verify_upi_btn")
                            )
                        },
                        testTag = "onboarding_upi_input"
                    )

                    if (upiValidationError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = upiValidationError!!,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnbErrorRed,
                            modifier = Modifier.testTag("onboarding_upi_error_text")
                        )
                    } else if (isUpiVerified && upiId.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = OnbSoftGreenBg
                        ) {
                            Text(
                                text = "${tr("Verified", "सत्यापित")} · ${ownerName.ifBlank { resolvedOwnerName }}",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnbBrandGreen,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // QR Preview Card
                    val qrUpiTarget = upiId.trim().ifBlank { "shop@upi" }
                    val qrShopLabel = shopName.trim().ifBlank { "KhataWise Store" }
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = KwSurface,
                        border = BorderStroke(1.dp, OnbCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            QrCodeView(
                                data = "upi://pay?pa=$qrUpiTarget&pn=$qrShopLabel&cu=INR",
                                modifier = Modifier.size(76.dp)
                            )
                            Text(
                                text = tr(
                                    "Customers scan this on bills and PDFs to pay you directly.",
                                    "ग्राहक सीधे भुगतान करने के लिए बिल और PDF पर इसे स्कैन करते हैं।"
                                ),
                                fontSize = 13.5.sp,
                                color = OnbSubtleText,
                                lineHeight = 19.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // GSTIN Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = KwSurface,
                        border = BorderStroke(1.dp, OnbCardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tr("I have a GSTIN", "मेरे पास GSTIN है"),
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OnbNavyText
                                    )
                                    Text(
                                        text = tr("Adds GSTIN and HSN to bills", "बिलों में GSTIN और HSN जोड़ता है"),
                                        fontSize = 13.sp,
                                        color = OnbSubtleText
                                    )
                                }
                                Switch(
                                    checked = hasGstin,
                                    onCheckedChange = { hasGstin = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = OnbBrandGreen,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = kw(0xFFCBD5E1)
                                    ),
                                    modifier = Modifier.testTag("onboarding_has_gstin_switch")
                                )
                            }

                            if (hasGstin) {
                                Spacer(modifier = Modifier.height(12.dp))
                                SetupFieldLabel(label = "GSTIN", required = false)
                                SetupInputBox(
                                    value = gstinInput,
                                    onValueChange = { gstinInput = it.uppercase().take(15) },
                                    placeholder = "08ABCDE1234F1Z5",
                                    testTag = "onboarding_gstin_input"
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = tr("Prices include GST", "कीमतों में GST शामिल है"),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnbNavyText
                                )
                                Switch(
                                    checked = pricesIncludeGst,
                                    onCheckedChange = { pricesIncludeGst = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = OnbBrandGreen,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = kw(0xFFCBD5E1)
                                    ),
                                    modifier = Modifier.testTag("onboarding_prices_include_gst_switch")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // BILL LANGUAGE (English | हिंदी | Both)
                    SetupFieldLabel(label = tr("BILL LANGUAGE", "बिल की भाषा"), required = false)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = kw(0xFFF1F2F6)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("English", "हिंदी", "Both").forEach { langOpt ->
                                val isSelected = billLanguage == langOpt
                                Surface(
                                    onClick = { billLanguage = langOpt },
                                    shape = RoundedCornerShape(11.dp),
                                    color = if (isSelected) OnbBrandGreen else Color.Transparent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .testTag("bill_lang_opt_$langOpt")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = langOpt,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else OnbNavyText
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // BILL NUMBER PREFIX (OPTIONAL)
                    SetupFieldLabel(
                        label = tr("BILL NUMBER PREFIX (OPTIONAL)", "बिल नंबर प्रीफिक्स (वैकल्पिक)"),
                        required = false
                    )
                    SetupInputBox(
                        value = billPrefix,
                        onValueChange = { billPrefix = it.uppercase().take(8) },
                        placeholder = "e.g. KB-",
                        testTag = "onboarding_bill_prefix_input"
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Bottom Continue Button
                Button(
                    onClick = {
                        if (upiId.isNotBlank() && !isValidUpiFormat(upiId.trim())) {
                            upiValidationError = invalidUpiMsg
                            return@Button
                        }
                        persistAllShopDetails()
                        currentStep = 3
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OnbBrandGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("onboarding_step2_continue_btn")
                ) {
                    Text(
                        text = tr("Continue", "आगे बढ़ें"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            else -> {
                // ---------------------------------------------------------
                // STEP 3 OF 3: <Shop Name> is ready (Page 3 of PDF)
                // ---------------------------------------------------------
                val readyShopName = shopName.trim().ifBlank {
                    settings?.shopName?.takeIf { it.isNotBlank() } ?: "KhataWise Store"
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(28.dp))

                    // Soft Green Checkmark Circle
                    Surface(
                        modifier = Modifier.size(88.dp),
                        shape = CircleShape,
                        color = OnbSoftGreenBg
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Ready",
                                tint = OnbBrandGreen,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = tr("$readyShopName is ready", "$readyShopName तैयार है"),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnbNavyText,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = tr(
                            "A few quick things help you get started.",
                            "शुरू करने के लिए कुछ आसान कदम।"
                        ),
                        fontSize = 15.sp,
                        color = OnbSubtleText,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(26.dp))

                    // 4 Quick-Start Action Cards
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ReadyActionCard(
                            title = tr("Add your first item", "अपना पहला आइटम जोड़ें"),
                            subtitle = tr("Stock and rates for billing", "बिलिंग के लिए स्टॉक और रेट"),
                            icon = Icons.Outlined.Inventory2,
                            iconBg = kw(0xFFFEF3C7),
                            iconTint = kw(0xFF92400E),
                            onClick = { onFinishSetup(QuickStartDestination.ADD_FIRST_ITEM) },
                            testTag = "ready_action_add_item"
                        )
                        ReadyActionCard(
                            title = tr("Add customers", "ग्राहक जोड़ें"),
                            subtitle = tr("Type them in or import from contacts", "नाम लिखें या संपर्कों से जोड़ें"),
                            icon = Icons.Outlined.PersonAdd,
                            iconBg = OnbSoftGreenBg,
                            iconTint = OnbBrandGreen,
                            onClick = { onFinishSetup(QuickStartDestination.ADD_CUSTOMERS) },
                            testTag = "ready_action_add_customers"
                        )
                        ReadyActionCard(
                            title = tr("Set today's rates", "आज का भाव सेट करें"),
                            subtitle = tr("1 kg, 5 kg and per bag", "1 किग्रा, 5 किग्रा और प्रति बोरी"),
                            icon = Icons.Outlined.LocalOffer,
                            iconBg = kw(0xFFFEF3C7),
                            iconTint = kw(0xFF92400E),
                            onClick = { onFinishSetup(QuickStartDestination.SET_TODAY_RATES) },
                            testTag = "ready_action_set_rates"
                        )
                        ReadyActionCard(
                            title = tr("Invite staff", "स्टाफ जोड़ें"),
                            subtitle = tr("Cashiers and managers", "कैशियर और मैनेजर"),
                            icon = Icons.Outlined.Shield,
                            iconBg = kw(0xFFE0ECF8),
                            iconTint = kw(0xFF1E4D8C),
                            onClick = { onFinishSetup(QuickStartDestination.INVITE_STAFF) },
                            testTag = "ready_action_invite_staff"
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Bottom CTA Buttons: "Go to Home" & "Take a quick tour"
                Button(
                    onClick = { onFinishSetup(QuickStartDestination.HOME) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OnbBrandGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("onboarding_go_to_home_btn")
                ) {
                    Text(
                        text = tr("Go to Home", "होम पर जाएं"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onLaunchTour,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("onboarding_take_tour_btn")
                ) {
                    Text(
                        text = tr("Take a quick tour", "एक त्वरित टूर लें"),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnbBrandGreen
                    )
                }
            }
        }
    }
}

/**
 * 4-Slide Welcome Tour (Pages 4, 5, 6, 7 of KhataWise – Onboarding & states.pdf)
 */
@Composable
fun WelcomeTourScreen(
    upiId: String = "shop@upi",
    onFinishTour: () -> Unit
) {
    val context = LocalContext.current
    var slideIndex by remember { mutableIntStateOf(0) }

    BackHandler {
        if (slideIndex > 0) {
            slideIndex -= 1
        } else {
            OnboardingPreferences.setWelcomeTourCompleted(context, true)
            onFinishTour()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnbPageBg)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top-right Skip button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = {
                    OnboardingPreferences.setWelcomeTourCompleted(context, true)
                    onFinishTour()
                },
                modifier = Modifier.testTag("welcome_tour_skip_btn")
            ) {
                Text(
                    text = tr("Skip", "छोड़ें"),
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnbSubtleText
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Illustration Container (Soft Mint Gradient Rounded Box)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            kw(0xFFE3F4EC),
                            kw(0xFFF6F8FB)
                        )
                    )
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (slideIndex) {
                0 -> TourSlide1Illustration()
                1 -> TourSlide2Illustration(upiId = upiId.ifBlank { "999999@upi" })
                2 -> TourSlide3Illustration()
                else -> TourSlide4Illustration()
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        val titleText = when (slideIndex) {
            0 -> tr("Make a bill in seconds", "कुछ ही सेकंड में बिल बनाएं")
            1 -> tr("Never lose track of udhari", "उधारी का पूरा हिसाब रखें")
            2 -> tr("Know your stock and profit", "अपना स्टॉक और मुनाफा जानें")
            else -> tr("Safe with staff and customers", "स्टाफ और ग्राहकों के साथ सुरक्षित")
        }

        val subtitleText = when (slideIndex) {
            0 -> tr(
                "Tap items or use your voice. Send the bill on WhatsApp as a PDF or image.",
                "आइटम टैप करें या आवाज़ से बोलें। व्हाट्सएप पर PDF या फोटो के रूप में बिल भेजें।"
            )
            1 -> tr(
                "See who owes what, set promise dates and send reminders in one tap.",
                "देखें किस पर कितना बकाया है, वायदा तारीख सेट करें और एक टैप में रिमाइंडर भेजें।"
            )
            2 -> tr(
                "Get low-stock alerts, daily cash and clear profit reports.",
                "कम स्टॉक अलर्ट, रोज़ का कैश और स्पष्ट मुनाफा रिपोर्ट पाएं।"
            )
            else -> tr(
                "Give each person a role, hide numbers when needed, and lock with your fingerprint.",
                "प्रत्येक व्यक्ति को रोल दें, ज़रूरत पड़ने पर आंकड़े छुपाएं और फिंगरप्रिंट से लॉक करें।"
            )
        }

        Text(
            text = titleText,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            color = OnbNavyText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = subtitleText,
            fontSize = 15.sp,
            color = OnbSubtleText,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Pagination Dots (Active green capsule + 3 gray circles)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0..3) {
                val isCurrent = i == slideIndex
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(if (isCurrent) 26.dp else 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (isCurrent) OnbBrandGreen else kw(0xFFCBD5E1))
                        .clickable { slideIndex = i }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom Primary Button: "Next" on slides 0..2, "Get started" on slide 3
        Button(
            onClick = {
                if (slideIndex < 3) {
                    slideIndex += 1
                } else {
                    OnboardingPreferences.setWelcomeTourCompleted(context, true)
                    onFinishTour()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = OnbBrandGreen,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("welcome_tour_primary_btn")
        ) {
            Text(
                text = if (slideIndex < 3) tr("Next", "आगे") else tr("Get started", "शुरू करें"),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TourSlide1Illustration() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = KwSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = kw(0xFFE6EEF6)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "RY",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = kw(0xFF1E4D8C)
                        )
                    }
                }
                Text(
                    text = "Customer 1",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnbNavyText
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Cattle Feed 2 bags", fontSize = 13.5.sp, color = OnbSubtleText)
                Text("₹2,700", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = OnbNavyText)
            }
            HorizontalDivider(
                color = kw(0xFFF1F5F9),
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Chana Churi 5 kg", fontSize = 13.5.sp, color = OnbSubtleText)
                Text("₹230", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = OnbNavyText)
            }
            HorizontalDivider(
                color = kw(0xFFF1F5F9),
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OnbNavyText)
                Text("₹2,930", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnbNavyText)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(10.dp),
                color = OnbBrandGreen
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = tr("Save & share bill", "बिल सेव और शेयर करें"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun TourSlide2Illustration(upiId: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.End,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = KwSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = kw(0xFFE6EEF6)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "KM",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = kw(0xFF1E4D8C)
                                )
                            }
                        }
                        Text(
                            text = "Kailash Meena",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnbNavyText
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = kw(0xFFFEE2E2)
                    ) {
                        Text(
                            text = "Overdue 8 days",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = kw(0xFF991B1B),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = kw(0xFFE6EEF6)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "RY",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = kw(0xFF1E4D8C)
                                )
                            }
                        }
                        Text(
                            text = "Customer 1",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnbNavyText
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = kw(0xFFFEE2E2)
                    ) {
                        Text(
                            text = "Due today",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = kw(0xFF991B1B),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .shadow(4.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = OnbSoftGreenBg
        ) {
            Text(
                text = "Namaste, your udhari is due. Pay by UPI: $upiId",
                fontSize = 13.sp,
                color = OnbNavyText,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun TourSlide3Illustration() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = KwSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = "GROSS PROFIT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = OnbSubtleText,
                letterSpacing = 0.6.sp
            )
            Text(
                text = "22% margin",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = OnbBrandGreen
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Bar + Line Chart Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
            ) {
                val barHeights = listOf(0.58f, 0.82f, 0.68f, 0.94f, 0.78f)
                val barCount = barHeights.size
                val spacing = size.width * 0.06f
                val barWidth = (size.width - spacing * (barCount - 1)) / barCount
                val points = mutableListOf<Offset>()

                barHeights.forEachIndexed { idx, ratio ->
                    val x = idx * (barWidth + spacing)
                    val h = size.height * ratio
                    val y = size.height - h
                    drawRoundRect(
                        color = kw(0xFFBFDBFE),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(8.dp.toPx())
                    )
                    points.add(Offset(x + barWidth / 2f, y + h * 0.2f))
                }

                val linePath = Path().apply {
                    points.forEachIndexed { idx, pt ->
                        if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                    }
                }
                drawPath(
                    path = linePath,
                    color = OnbBrandGreen,
                    style = Stroke(
                        width = 3.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = kw(0xFFFEF3C7)
                ) {
                    Text(
                        text = "3 items low",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = kw(0xFF92400E),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = OnbSoftGreenBg
                ) {
                    Text(
                        text = "Cash ₹6,390",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnbBrandGreen,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TourSlide4Illustration() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = KwSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = kw(0xFFF1F5F9)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = OnbNavyText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = "Numbers hidden from\ncustomers",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnbNavyText,
                        lineHeight = 19.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = OnbNavyText
                    ) {
                        Text(
                            text = "Admin",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = kw(0xFFE0ECF8)
                    ) {
                        Text(
                            text = "Manager",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = kw(0xFF1E4D8C),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = kw(0xFFF1F2F6)
                    ) {
                        Text(
                            text = "Cashier",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnbNavyText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = KwSurface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Fingerprint,
                    contentDescription = null,
                    tint = OnbBrandGreen,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Fingerprint or PIN to open",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnbNavyText
                )
            }
        }
    }
}

@Composable
private fun ThreeSegmentProgressBar(completedSegments: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (idx in 1..3) {
            val active = idx <= completedSegments
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) OnbBrandGreen else kw(0xFFE2E8F0))
            )
        }
    }
}

@Composable
private fun SetupFieldLabel(label: String, required: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = OnbSubtleText,
            letterSpacing = 0.6.sp
        )
        if (required) {
            Text(
                text = " *",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = OnbErrorRed
            )
        }
    }
}

@Composable
private fun SetupInputBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingPrefix: String? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailingContent: (@Composable () -> Unit)? = null,
    testTag: String
) {
    val borderColor = if (isError) OnbErrorRed else OnbFieldBorder
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        color = OnbFieldBg,
        border = BorderStroke(if (isError) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingPrefix != null) {
                Text(
                    text = leadingPrefix,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnbSubtleText
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 15.5.sp,
                        color = kw(0xFF94A3B8)
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnbNavyText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(testTag)
                )
            }

            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailingContent()
            }
        }
    }
}

@Composable
private fun ReadyActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = KwSurface,
        border = BorderStroke(1.dp, OnbCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = iconBg
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnbNavyText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = OnbSubtleText
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = OnbSubtleText,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

fun isValidUpiFormat(upi: String): Boolean {
    val trimmed = upi.trim()
    val atIndex = trimmed.indexOf('@')
    if (atIndex <= 0 || atIndex == trimmed.lastIndex) return false
    val handle = trimmed.substring(0, atIndex)
    val bank = trimmed.substring(atIndex + 1)
    return handle.length >= 2 && bank.length >= 2 && !trimmed.contains(' ')
}
