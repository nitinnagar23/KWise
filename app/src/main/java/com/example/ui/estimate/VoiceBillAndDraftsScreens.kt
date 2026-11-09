package com.example.ui.estimate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.Customer
import com.example.model.EstimateDraft
import com.example.model.Product
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.ui.theme.FlatTextSecondary
import com.example.util.CurrencyUtils
import com.example.util.tr
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val VoiceGreen = Color(0xFF0E6F43)
private val VoiceGreenSoft = Color(0xFFDDF3E8)
private val VoiceGreenRingMid = Color(0xFFC5EBD8)
private val VoiceGreenRingOuter = Color(0xFFEAF7F0)
private val VoiceRed = Color(0xFFB91C1C)
private val VoiceRedSoft = Color(0xFFFCE4E4)
private val VoiceAmber = Color(0xFF8A4B08)
private val VoiceAmberBorder = Color(0xFFF59E0B)
private val VoiceAmberSoft = Color(0xFFFCEBD2)
private val VoiceAmberCardBg = Color(0xFFFFFBF2)
private val VoiceBlueSoft = Color(0xFFE8EEF7)
private val VoiceBlueText = Color(0xFF1E3A8A)
private val VoiceChipIdleBg = Color(0xFFF1F1F7)

data class VoiceParsedItemCandidate(
    val id: String,
    val rawPhrase: String,
    val quantityCount: Int,
    val isBagUnit: Boolean,
    val matchedProduct: Product?,
    val candidateProducts: List<Product>,
    val isAmbiguous: Boolean,
    val isSkipped: Boolean = false
) {
    val weightKg: BigDecimal
        get() {
            val prod = matchedProduct
            return if (isBagUnit) {
                val bagKg = prod?.kgPerBag?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal("50")
                bagKg.multiply(BigDecimal(quantityCount))
            } else {
                BigDecimal(quantityCount)
            }
        }

    val lineAmount: BigDecimal
        get() {
            val prod = matchedProduct ?: return BigDecimal.ZERO
            return if (isBagUnit && !prod.isLooseOnly) {
                prod.bagSellingPrice.multiply(BigDecimal(quantityCount)).setScale(0, RoundingMode.HALF_UP)
            } else {
                prod.effectiveLooseRatePerKg.multiply(weightKg).setScale(0, RoundingMode.HALF_UP)
            }
        }
}

data class VoiceResolvedLineItem(
    val product: Product,
    val bags: Int,
    val looseKg: String
)

// ============================================================================
// PAGES 7 & 8: VOICE BILL FLOW (Listening + Check Your Items)
// ============================================================================
@Composable
fun VoiceBillFlowScreen(
    products: List<Product>,
    customers: List<Customer>,
    onClose: () -> Unit,
    onAddItemsToBill: (List<VoiceResolvedLineItem>, Customer?) -> Unit
) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(1) } // 1 = Listening (Page 7), 2 = Check your items (Page 8)

    var transcript by remember {
        mutableStateOf("Do bori cattle feed, ek bori khal, paanch kilo chana — Ramesh ke naam")
    }
    var isEditingTranscript by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }

    var matchedCustomer by remember { mutableStateOf<Customer?>(null) }
    var showCustomerPickerDialog by remember { mutableStateOf(false) }
    var parsedItems by remember { mutableStateOf<List<VoiceParsedItemCandidate>>(emptyList()) }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    fun startSpeechRecognition() {
        val recognizer = speechRecognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try {
            isListening = true
            recognizer.startListening(intent)
        } catch (_: Exception) {
            isListening = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startSpeechRecognition()
        }
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
            }
            override fun onBeginningOfSpeech() {
                isListening = true
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }
            override fun onError(error: Int) {
                isListening = false
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty() && matches[0].isNotBlank()) {
                    transcript = matches[0]
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!partial.isNullOrEmpty() && partial[0].isNotBlank()) {
                    transcript = partial[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer?.setRecognitionListener(listener)
        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            startSpeechRecognition()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun parseTranscriptAndMoveToReview() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        isListening = false
        val (cust, items) = parseVoiceTranscript(transcript, products, customers)
        matchedCustomer = cust
        parsedItems = items
        step = 2
    }

    BackHandler {
        if (step == 2) step = 1 else onClose()
    }

    if (step == 1) {
        // ============================================================
        // PAGE 7: Voice bill (Listening)
        // ============================================================
        val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")
        val outerPulse by infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "outer_pulse"
        )

        Scaffold(
            containerColor = FlatDarkBg,
            bottomBar = {
                Surface(
                    color = FlatDarkBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { parseTranscriptAndMoveToReview() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("voice_tap_to_stop_btn"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VoiceRed,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tr("Tap to stop", "रोकने के लिए टैप करें"),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        TextButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("voice_cancel_btn")
                        ) {
                            Text(
                                text = tr("Cancel", "रद्द करें"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlatTextSecondary
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onClose,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("voice_close_btn"),
                        shape = RoundedCornerShape(13.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr("Voice bill", "बोलकर बिल बनाएं"),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = tr("Speak the items. We'll add them.", "सामान बोलें, हम जोड़ देंगे।"),
                            fontSize = 13.sp,
                            color = FlatTextSecondary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = VoiceGreenSoft
                    ) {
                        Text(
                            text = "हिंदी + English",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = VoiceGreen,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Concentric Pulsing Green Mic Circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(164.dp)
                        .scale(outerPulse)
                        .clip(CircleShape)
                        .background(VoiceGreenRingOuter)
                        .clickable {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                startSpeechRecognition()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(124.dp)
                            .clip(CircleShape)
                            .background(VoiceGreenRingMid)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(86.dp)
                                .clip(CircleShape)
                                .background(VoiceGreen)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Microphone",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = tr("Listening...", "सुन रहे हैं..."),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = VoiceGreen
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Equalizer soundwave bars
                val barHeights = listOf(12.dp, 22.dp, 16.dp, 28.dp, 20.dp, 24.dp, 14.dp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(30.dp)
                ) {
                    barHeights.forEach { h ->
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(h)
                                .clip(RoundedCornerShape(50))
                                .background(VoiceGreen)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // WHAT WE HEARD Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isEditingTranscript = !isEditingTranscript }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr("WHAT WE HEARD", "हमने क्या सुना"),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FlatTextSecondary,
                                letterSpacing = 0.6.sp
                            )
                            Text(
                                text = if (isEditingTranscript) tr("Done", "हो गया") else tr("Edit", "बदलें"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = VoiceGreen
                            )
                        }

                        if (isEditingTranscript) {
                            OutlinedTextField(
                                value = transcript,
                                onValueChange = { transcript = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("voice_transcript_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = VoiceGreen,
                                    unfocusedBorderColor = FlatDarkBorder
                                )
                            )
                        } else {
                            Text(
                                text = buildAnnotatedString {
                                    append("“")
                                    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = FlatTextPrimary)) {
                                        append(transcript)
                                    }
                                    append("”")
                                },
                                fontSize = 16.sp,
                                lineHeight = 23.sp,
                                color = FlatTextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Try saying card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = VoiceChipIdleBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = tr("Try saying", "ऐसे बोलकर देखें"),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FlatTextSecondary
                        )

                        val examples = listOf(
                            "“Ek bori makka”" to "1 bag Maize",
                            "“Do kilo bajra”" to "2 kg Bajra",
                            "“Sunita ko udhari mein”" to "Udhari to Sunita"
                        )
                        examples.forEach { (phrase, meaning) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        transcript = phrase.removePrefix("“").removeSuffix("”")
                                    }
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = phrase,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = FlatTextPrimary
                                )
                                Text(
                                    text = meaning,
                                    fontSize = 13.sp,
                                    color = FlatTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // ============================================================
        // PAGE 8: Check your items (Voice parsed review)
        // ============================================================
        val activeItems = parsedItems.filter { !it.isSkipped }
        val resolvedItems = activeItems.filter { !it.isAmbiguous && it.matchedProduct != null }
        val unresolvedCount = activeItems.count { it.isAmbiguous || it.matchedProduct == null }
        val totalSoFar = resolvedItems.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.lineAmount) }

        Scaffold(
            containerColor = FlatDarkBg,
            bottomBar = {
                Surface(
                    color = FlatDarkBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val finalResolved = resolvedItems.mapNotNull { candidate ->
                                    val prod = candidate.matchedProduct ?: return@mapNotNull null
                                    if (candidate.isBagUnit && !prod.isLooseOnly) {
                                        VoiceResolvedLineItem(
                                            product = prod,
                                            bags = candidate.quantityCount.coerceAtLeast(1),
                                            looseKg = "0"
                                        )
                                    } else {
                                        VoiceResolvedLineItem(
                                            product = prod,
                                            bags = 0,
                                            looseKg = candidate.weightKg.stripTrailingZeros().toPlainString()
                                        )
                                    }
                                }
                                onAddItemsToBill(finalResolved, matchedCustomer)
                            },
                            enabled = resolvedItems.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("voice_add_items_to_bill_btn"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VoiceGreen,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = tr(
                                    "Add ${ activeItems.size } items to bill",
                                    "बिल में ${ activeItems.size } आइटम जोड़ें"
                                ),
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                step = 1
                                startSpeechRecognition()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("voice_speak_again_btn"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, FlatDarkBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = FlatTextPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tr("Speak again", "फिर से बोलें"),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { step = 1 },
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(13.dp),
                        color = FlatDarkCard,
                        border = BorderStroke(1.dp, FlatDarkBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = FlatTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = tr("Check your items", "अपने आइटम जांचें"),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FlatTextPrimary
                        )
                        val foundCount = activeItems.size + (if (matchedCustomer != null) 1 else 0)
                        Text(
                            text = tr("We found $foundCount things", "हमें $foundCount चीज़ें मिलीं"),
                            fontSize = 13.sp,
                            color = FlatTextSecondary
                        )
                    }
                }

                // You said card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = VoiceChipIdleBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = tr("You said", "आपने कहा"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextSecondary
                        )
                        Text(
                            text = "“$transcript”",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FlatTextPrimary
                        )
                    }
                }

                // Customer matched card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = tr("Customer", "ग्राहक"),
                                fontSize = 13.5.sp,
                                color = FlatTextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = VoiceGreenSoft
                            ) {
                                Text(
                                    text = "${matchedCustomer?.name ?: "Ramesh Yadav"} ✓",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = VoiceGreen,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Text(
                            text = tr("Change", "बदलें"),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = VoiceGreen,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showCustomerPickerDialog = true }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                // Parsed Item Cards
                activeItems.forEach { candidate ->
                    if (!candidate.isAmbiguous && candidate.matchedProduct != null) {
                        val prod = candidate.matchedProduct
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = FlatDarkCard,
                            border = BorderStroke(1.dp, FlatDarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(VoiceGreenSoft)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = VoiceGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = prod.feedName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = FlatTextPrimary
                                    )
                                    val desc = if (candidate.isBagUnit && !prod.isLooseOnly) {
                                        val bagWord = if (candidate.quantityCount == 1) "bag" else "bags"
                                        "${candidate.quantityCount} $bagWord · ${CurrencyUtils.formatWeight(candidate.weightKg)} kg"
                                    } else {
                                        "${CurrencyUtils.formatWeight(candidate.weightKg)} kg · Loose"
                                    }
                                    Text(
                                        text = desc,
                                        fontSize = 12.5.sp,
                                        color = FlatTextSecondary
                                    )
                                }

                                Text(
                                    text = CurrencyUtils.formatInr(candidate.lineAmount),
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = FlatTextPrimary
                                )

                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove item",
                                    tint = FlatTextSecondary,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            parsedItems = parsedItems.map {
                                                if (it.id == candidate.id) it.copy(isSkipped = true) else it
                                            }
                                        }
                                        .padding(2.dp)
                                )
                            }
                        }
                    } else {
                        // Ambiguous item card (e.g., "khal" · 1 bag -> Which khal?)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = VoiceAmberCardBg,
                            border = BorderStroke(1.5.dp, VoiceAmberBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(VoiceAmberSoft)
                                    ) {
                                        Text(
                                            text = "?",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = VoiceAmber
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = candidate.rawPhrase,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = FlatTextPrimary
                                        )
                                        Text(
                                            text = tr("Which khal?", "कौन सी खल?"),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = VoiceAmber
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    candidate.candidateProducts.forEach { optionProd ->
                                        Surface(
                                            onClick = {
                                                parsedItems = parsedItems.map {
                                                    if (it.id == candidate.id) {
                                                        it.copy(
                                                            matchedProduct = optionProd,
                                                            isAmbiguous = false
                                                        )
                                                    } else it
                                                }
                                            },
                                            shape = RoundedCornerShape(50),
                                            color = Color.White,
                                            border = BorderStroke(1.dp, FlatDarkBorder)
                                        ) {
                                            Text(
                                                text = optionProd.feedName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = FlatTextPrimary,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = {
                                            parsedItems = parsedItems.map {
                                                if (it.id == candidate.id) it.copy(isSkipped = true) else it
                                            }
                                        },
                                        shape = RoundedCornerShape(50),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, FlatDarkBorder)
                                    ) {
                                        Text(
                                            text = tr("Skip", "छोड़ें"),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = FlatTextSecondary,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Total so far card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = tr("Total so far", "अब तक का कुल"),
                                fontSize = 12.sp,
                                color = FlatTextSecondary
                            )
                            val countSummary = if (unresolvedCount > 0) {
                                "${resolvedItems.size} items (+$unresolvedCount to pick)"
                            } else {
                                "${resolvedItems.size} items"
                            }
                            Text(
                                text = countSummary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FlatTextPrimary
                            )
                        }

                        Text(
                            text = CurrencyUtils.formatInr(totalSoFar),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = VoiceGreen
                        )
                    }
                }
            }
        }
    }

    if (showCustomerPickerDialog) {
        VoiceCustomerPickerDialog(
            customers = customers,
            onDismiss = { showCustomerPickerDialog = false },
            onSelectCustomer = { cust ->
                matchedCustomer = cust
                showCustomerPickerDialog = false
            }
        )
    }
}

private fun parseVoiceTranscript(
    transcript: String,
    products: List<Product>,
    customers: List<Customer>
): Pair<Customer?, List<VoiceParsedItemCandidate>> {
    val lower = transcript.lowercase(Locale.ROOT)

    // 1. Match customer name if mentioned
    val matchedCust = customers.firstOrNull { cust ->
        val firstToken = cust.name.trim().split(Regex("\\s+")).firstOrNull()?.lowercase(Locale.ROOT) ?: ""
        firstToken.length >= 3 && lower.contains(firstToken)
    } ?: customers.firstOrNull()

    // 2. Number words dictionary (Hindi + English)
    val numberMap = mapOf(
        "ek" to 1, "one" to 1, "1" to 1, "एक" to 1,
        "do" to 2, "two" to 2, "2" to 2, "दो" to 2,
        "teen" to 3, "three" to 3, "3" to 3, "तीन" to 3,
        "chaar" to 4, "char" to 4, "four" to 4, "4" to 4, "चार" to 4,
        "paanch" to 5, "panch" to 5, "five" to 5, "5" to 5, "पांच" to 5,
        "chhah" to 6, "che" to 6, "six" to 6, "6" to 6,
        "saat" to 7, "seven" to 7, "7" to 7,
        "aath" to 8, "eight" to 8, "8" to 8,
        "nau" to 9, "nine" to 9, "9" to 9,
        "das" to 10, "ten" to 10, "10" to 10,
        "bees" to 20, "20" to 20,
        "pachas" to 50, "50" to 50
    )

    val clauses = transcript
        .split(Regex("[,—\\-]|\\baur\\b|\\band\\b", RegexOption.IGNORE_CASE))
        .map { it.trim() }
        .filter { it.isNotBlank() }

    val results = mutableListOf<VoiceParsedItemCandidate>()

    clauses.forEachIndexed { idx, clause ->
        val cLower = clause.lowercase(Locale.ROOT)
        // Skip pure customer clauses like "Ramesh ke naam" or "Sunita ko udhari mein"
        if (cLower.contains("ke naam") || cLower.contains("udhari mein")) {
            return@forEachIndexed
        }

        val words = cLower.split(Regex("\\s+"))
        val qty = words.firstNotNullOfOrNull { w ->
            numberMap[w] ?: w.toIntOrNull()
        } ?: 1

        val isBag = cLower.contains("bori") || cLower.contains("bag") || cLower.contains("katta") || cLower.contains("बोरी")

        // Check if ambiguous "khal" without specifying mustard/binola
        val mentionsKhal = cLower.contains("khal") || cLower.contains("cake") || cLower.contains("खल")
        val specifiesKhalType = cLower.contains("mustard") || cLower.contains("sarson") || cLower.contains("binola") || cLower.contains("cotton")

        if (mentionsKhal && !specifiesKhalType) {
            val khalCandidates = products.filter {
                val n = it.feedName.lowercase(Locale.ROOT)
                n.contains("khal") || n.contains("cake") || n.contains("mustard") || n.contains("binola")
            }.ifEmpty {
                products.take(2)
            }
            val unitWord = if (isBag) "bag" else "kg"
            results.add(
                VoiceParsedItemCandidate(
                    id = "voice_item_$idx",
                    rawPhrase = "“khal” · $qty $unitWord",
                    quantityCount = qty,
                    isBagUnit = isBag,
                    matchedProduct = null,
                    candidateProducts = khalCandidates.take(2),
                    isAmbiguous = true
                )
            )
            return@forEachIndexed
        }

        // Otherwise match product by name tokens
        val matchedProd = products.firstOrNull { prod ->
            val pLower = prod.feedName.lowercase(Locale.ROOT)
            words.any { w -> w.length >= 3 && pLower.contains(w) }
        } ?: when {
            cLower.contains("cattle") || cLower.contains("feed") || cLower.contains("pashu") ->
                products.firstOrNull { !it.isLooseOnly } ?: products.firstOrNull()
            cLower.contains("chana") || cLower.contains("churi") || cLower.contains("makka") || cLower.contains("bajra") ->
                products.firstOrNull { it.isLooseOnly } ?: products.lastOrNull()
            else -> products.getOrNull(idx % products.size.coerceAtLeast(1))
        }

        if (matchedProd != null) {
            results.add(
                VoiceParsedItemCandidate(
                    id = "voice_item_$idx",
                    rawPhrase = clause,
                    quantityCount = qty,
                    isBagUnit = isBag && !matchedProd.isLooseOnly,
                    matchedProduct = matchedProd,
                    candidateProducts = emptyList(),
                    isAmbiguous = false
                )
            )
        }
    }

    if (results.isEmpty() && products.isNotEmpty()) {
        val firstProd = products.first()
        results.add(
            VoiceParsedItemCandidate(
                id = "voice_default_0",
                rawPhrase = firstProd.feedName,
                quantityCount = 1,
                isBagUnit = !firstProd.isLooseOnly,
                matchedProduct = firstProd,
                candidateProducts = emptyList(),
                isAmbiguous = false
            )
        )
    }

    return matchedCust to results
}

@Composable
private fun VoiceCustomerPickerDialog(
    customers: List<Customer>,
    onDismiss: () -> Unit,
    onSelectCustomer: (Customer) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(customers, query) {
        if (query.isBlank()) customers
        else customers.filter {
            it.name.contains(query, ignoreCase = true) || it.phone.contains(query)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FlatDarkCard,
        title = {
            Text(
                text = tr("Select Customer", "ग्राहक चुनें"),
                fontWeight = FontWeight.ExtraBold,
                color = FlatTextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(tr("Search customer...", "ग्राहक खोजें...")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filtered.forEach { cust ->
                        Surface(
                            onClick = { onSelectCustomer(cust) },
                            shape = RoundedCornerShape(10.dp),
                            color = FlatDarkBg,
                            border = BorderStroke(1.dp, FlatDarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cust.name,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary,
                                    fontSize = 14.sp
                                )
                                if (cust.phone.isNotBlank()) {
                                    Text(
                                        text = cust.phone,
                                        color = FlatTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Close", "बंद करें"), color = VoiceGreen, fontWeight = FontWeight.Bold)
            }
        }
    )
}

// ============================================================================
// PAGE 9: DRAFTS · SAVED ESTIMATES FULL SCREEN
// ============================================================================
@Composable
fun EstimateDraftsFullScreen(
    drafts: List<EstimateDraft>,
    shopName: String,
    onBack: () -> Unit,
    onOpenDraft: (EstimateDraft) -> Unit,
    onDeleteDraft: (EstimateDraft) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // All, Items, Budget
    var revealedDeleteDraftId by remember { mutableStateOf<String?>(null) }

    BackHandler { onBack() }

    val filteredDrafts = remember(drafts, searchQuery, selectedFilter) {
        drafts.filter { draft ->
            val meta = draft.parseMetadata()
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim()
                draft.title.contains(q, ignoreCase = true) ||
                    meta.customerName.contains(q, ignoreCase = true) ||
                    meta.items.any { it.productName.contains(q, ignoreCase = true) }
            }
            val matchesFilter = when (selectedFilter) {
                "Items" -> meta.mode != "BUDGET"
                "Budget" -> meta.mode == "BUDGET"
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FlatDarkBg)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("drafts_back_btn"),
                shape = RoundedCornerShape(13.dp),
                color = FlatDarkCard,
                border = BorderStroke(1.dp, FlatDarkBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = FlatTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = tr("Drafts", "ड्राफ्ट"),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FlatTextPrimary
                )
                Text(
                    text = tr("${drafts.size} saved estimates", "${drafts.size} सेव किए गए एस्टीमेट"),
                    fontSize = 13.sp,
                    color = FlatTextSecondary
                )
            }
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = tr("Search drafts...", "ड्राफ्ट खोजें..."),
                    color = FlatTextMuted,
                    fontSize = 14.5.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = FlatTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = FlatDarkCard,
                unfocusedContainerColor = FlatDarkCard,
                focusedBorderColor = VoiceGreen,
                unfocusedBorderColor = FlatDarkBorder,
                focusedTextColor = FlatTextPrimary,
                unfocusedTextColor = FlatTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("drafts_search_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Chips: All, Items, Budget
        val filterOptions = listOf(
            "All" to tr("All", "सभी"),
            "Items" to tr("Items", "सामान"),
            "Budget" to tr("Budget", "बजट")
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterOptions.forEach { (key, label) ->
                val selected = selectedFilter == key
                Surface(
                    onClick = { selectedFilter = key },
                    shape = RoundedCornerShape(50),
                    color = if (selected) VoiceGreen else VoiceChipIdleBg,
                    modifier = Modifier.testTag("drafts_filter_${key.lowercase(Locale.ROOT)}")
                ) {
                    Text(
                        text = label,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else FlatTextPrimary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredDrafts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tr("No saved drafts found.", "कोई सेव किया गया ड्राफ्ट नहीं मिला।"),
                    color = FlatTextSecondary,
                    fontSize = 14.5.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredDrafts, key = { it.id }) { draft ->
                    DraftEstimateCard(
                        draft = draft,
                        isDeleteRevealed = revealedDeleteDraftId == draft.id,
                        onToggleRevealDelete = {
                            revealedDeleteDraftId = if (revealedDeleteDraftId == draft.id) null else draft.id
                        },
                        onOpen = { onOpenDraft(draft) },
                        onShare = {
                            val meta = draft.parseMetadata()
                            val shareText = buildString {
                                append("📋 *${shopName.ifBlank { "KhataWise" }} - Estimate*\n")
                                append("*${draft.title}*\n")
                                append("------------------------\n")
                                meta.items.forEachIndexed { idx, item ->
                                    val qtyPart = buildString {
                                        if (item.bags > 0) append("${item.bags} bags")
                                        val lKg = item.looseKg.toBigDecimalOrNull() ?: BigDecimal.ZERO
                                        if (lKg > BigDecimal.ZERO) {
                                            if (isNotEmpty()) append(" + ")
                                            append("${lKg.stripTrailingZeros().toPlainString()} kg")
                                        }
                                    }
                                    append("${idx + 1}. ${item.productName}: $qtyPart\n")
                                }
                                append("------------------------\n")
                                append("Total Weight: ${CurrencyUtils.formatWeight(draft.totalWeightKg)} kg\n")
                                append("Total Estimate: ${CurrencyUtils.formatInr(draft.totalAmount)}\n")
                            }
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Estimate"))
                        },
                        onDelete = { onDeleteDraft(draft) }
                    )
                }

                item {
                    Text(
                        text = tr(
                            "Drafts are removed after 30 days. Swipe left to delete.",
                            "ड्राफ्ट 30 दिनों के बाद हटा दिए जाते हैं। हटाने के लिए बाएं स्वाइप करें।"
                        ),
                        fontSize = 12.sp,
                        color = FlatTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DraftEstimateCard(
    draft: EstimateDraft,
    isDeleteRevealed: Boolean,
    onToggleRevealDelete: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val meta = remember(draft.itemsJson) { draft.parseMetadata() }
    val isBudget = meta.mode == "BUDGET"
    val itemCount = meta.items.size.coerceAtLeast(1)
    val dateLabel = remember(draft.updatedAt) { formatRelativeDraftDate(draft.updatedAt) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VoiceRed)
            .pointerInput(draft.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragOffset < -40f && !isDeleteRevealed) {
                            onToggleRevealDelete()
                        } else if (dragOffset > 40f && isDeleteRevealed) {
                            onToggleRevealDelete()
                        }
                        dragOffset = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragOffset += dragAmount
                    }
                )
            }
    ) {
        // Right-aligned Delete button revealed on swipe-left
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clickable { onDelete() }
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tr("Delete", "हटाएं"),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp
            )
        }

        // Foreground Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = FlatDarkCard,
            border = BorderStroke(1.dp, FlatDarkBorder),
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(
                        x = if (isDeleteRevealed) (-88).dp.roundToPx() else 0,
                        y = 0
                    )
                }
                .clickable {
                    if (isDeleteRevealed) onToggleRevealDelete() else onOpen()
                }
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = draft.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FlatTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subSummary = if (isBudget) {
                            val names = meta.items.joinToString(" + ") { it.productName }.ifBlank { "Loose items" }
                            "$names · ${CurrencyUtils.formatWeight(draft.totalWeightKg)} kg"
                        } else {
                            "$itemCount ${if (itemCount == 1) "item" else "items"} · ${CurrencyUtils.formatWeight(draft.totalWeightKg)} kg"
                        }
                        Text(
                            text = subSummary,
                            fontSize = 12.5.sp,
                            color = FlatTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isBudget) VoiceAmberSoft else VoiceBlueSoft
                            ) {
                                Text(
                                    text = if (isBudget) tr("Budget", "बजट") else tr("Items", "सामान"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isBudget) VoiceAmber else VoiceBlueText,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                )
                            }
                            Text(
                                text = dateLabel,
                                fontSize = 12.sp,
                                color = FlatTextSecondary
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = CurrencyUtils.formatInr(draft.totalAmount),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FlatTextPrimary
                        )
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete option",
                            tint = FlatTextMuted,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(18.dp)
                                .clickable { onToggleRevealDelete() }
                        )
                    }
                }

                if (!isDeleteRevealed) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = onOpen,
                            shape = RoundedCornerShape(11.dp),
                            color = VoiceGreenSoft,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("draft_open_${draft.id}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = tr("Open", "खोलें"),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = VoiceGreen
                                )
                            }
                        }

                        Surface(
                            onClick = onShare,
                            shape = RoundedCornerShape(11.dp),
                            color = FlatDarkCard,
                            border = BorderStroke(1.dp, FlatDarkBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("draft_share_${draft.id}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = tr("Share", "शेयर"),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlatTextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatRelativeDraftDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val oneDay = 24L * 60L * 60L * 1000L
    val startToday = now - (now % oneDay)
    val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    val dayFmt = SimpleDateFormat("dd MMM", Locale.getDefault())
    return when {
        timestamp >= startToday -> "Today, ${timeFmt.format(Date(timestamp))}"
        timestamp >= startToday - oneDay -> "Yesterday"
        else -> dayFmt.format(Date(timestamp))
    }
}

// ============================================================================
// PAGE 10: SAVE AS DRAFT MODAL BOTTOM SHEET
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveAsDraftBottomSheet(
    initialDraftName: String,
    initialCustomer: Customer?,
    customers: List<Customer>,
    itemsSummaryTitle: String,
    itemsCountAndWeightSubtitle: String,
    totalAmount: BigDecimal,
    sampleBagRateLabel: String,
    onDismiss: () -> Unit,
    onSaveDraft: (draftName: String, selectedCustomer: Customer?, lockRates: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draftName by remember(initialDraftName) { mutableStateOf(initialDraftName) }
    var chosenCustomer by remember(initialCustomer) { mutableStateOf(initialCustomer) }
    var lockTodayRates by remember { mutableStateOf(true) }
    var showCustomerPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FlatDarkCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = tr("Save as draft", "ड्राफ्ट के रूप में सेव करें"),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FlatTextPrimary
                )
                Text(
                    text = tr(
                        "Open it later from Drafts, or share it as a quote.",
                        "इसे बाद में ड्राफ्ट से खोलें, या कोटेशन के रूप में शेयर करें।"
                    ),
                    fontSize = 13.sp,
                    color = FlatTextSecondary
                )
            }

            // DRAFT NAME
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = tr("DRAFT NAME", "ड्राफ्ट का नाम"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FlatTextSecondary,
                    letterSpacing = 0.6.sp
                )
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    placeholder = { Text(tr("e.g. Ramesh · feed", "जैसे रमेश · फीड")) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = FlatDarkCard,
                        unfocusedContainerColor = FlatDarkCard,
                        focusedBorderColor = VoiceGreen,
                        unfocusedBorderColor = VoiceGreen,
                        focusedTextColor = FlatTextPrimary,
                        unfocusedTextColor = FlatTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("draft_title_input")
                )
            }

            // FOR CUSTOMER (OPTIONAL)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = tr("FOR CUSTOMER (OPTIONAL)", "ग्राहक के लिए (वैकल्पिक)"),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FlatTextSecondary,
                    letterSpacing = 0.6.sp
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = FlatDarkCard,
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (chosenCustomer != null) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = VoiceGreenSoft
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = chosenCustomer!!.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = VoiceGreen
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove customer",
                                        tint = VoiceGreen,
                                        modifier = Modifier
                                            .size(15.dp)
                                            .clickable { chosenCustomer = null }
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = tr("No customer selected", "कोई ग्राहक नहीं चुना गया"),
                                fontSize = 13.5.sp,
                                color = FlatTextSecondary
                            )
                        }

                        Text(
                            text = tr("Change", "बदलें"),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = VoiceGreen,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showCustomerPicker = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Items & Total Summary Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = VoiceChipIdleBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = itemsSummaryTitle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = FlatTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = itemsCountAndWeightSubtitle,
                            fontSize = 12.5.sp,
                            color = FlatTextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = CurrencyUtils.formatInr(totalAmount),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FlatTextPrimary
                    )
                }
            }

            // Lock today's rates switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr("Lock today's rates", "आज के भाव लॉक करें"),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = FlatTextPrimary
                    )
                    Text(
                        text = tr(
                            "Keep $sampleBagRateLabel even if rates change tomorrow",
                            "कल भाव बदलने पर भी $sampleBagRateLabel बनाए रखें"
                        ),
                        fontSize = 12.sp,
                        color = FlatTextSecondary
                    )
                }
                Switch(
                    checked = lockTodayRates,
                    onCheckedChange = { lockTodayRates = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = VoiceGreen
                    )
                )
            }

            // Bottom Buttons: Cancel + Save draft
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, FlatDarkBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FlatTextPrimary)
                ) {
                    Text(
                        text = tr("Cancel", "रद्द करें"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Button(
                    onClick = {
                        onSaveDraft(draftName, chosenCustomer, lockTodayRates)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("confirm_save_draft_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VoiceGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = tr("Save draft", "ड्राफ्ट सेव करें"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }

    if (showCustomerPicker) {
        VoiceCustomerPickerDialog(
            customers = customers,
            onDismiss = { showCustomerPicker = false },
            onSelectCustomer = { cust ->
                chosenCustomer = cust
                if (draftName.isBlank() || draftName.contains("·")) {
                    val firstName = cust.name.trim().split(Regex("\\s+")).firstOrNull() ?: cust.name
                    draftName = "$firstName · feed"
                }
                showCustomerPicker = false
            }
        )
    }
}
