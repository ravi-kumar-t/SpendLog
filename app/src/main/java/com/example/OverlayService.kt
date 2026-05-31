package com.example

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.data.AppDatabase
import com.example.data.ExpenseRepository
import com.example.data.MerchantMapping
import com.example.data.Transaction
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var fakeLifecycle: FakeLifecycleOwner? = null

    private lateinit var database: AppDatabase
    private lateinit var repository: ExpenseRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        repository = ExpenseRepository(database.expenseDao())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val amount = intent?.getFloatExtra("extracted_amount", 0.0f) ?: intent?.getFloatExtra("amount", 0.0f) ?: 0.0f
        val merchantRaw = intent?.getStringExtra("extracted_merchant") ?: intent?.getStringExtra("merchant") ?: "Unknown Merchant"
        val merchantCleaned = SmsParser.cleanMerchant(merchantRaw)

        // Prelink database mapping lookup
        serviceScope.launch {
            val mapping = withContext(Dispatchers.IO) {
                repository.getMerchantMapping(merchantCleaned)
            }
            val initialText = mapping?.default_item ?: ""
            val selection = if (initialText.isNotEmpty()) TextRange(0, initialText.length) else TextRange.Zero

            setupWindow(amount, merchantCleaned, merchantRaw, initialText, selection)
        }

        return START_NOT_STICKY
    }

    private fun setupWindow(
        amount: Float,
        merchantCleaned: String,
        merchantRaw: String,
        initialText: String,
        initialSelection: TextRange
    ) {
        // Remove previous instance if existing to prevent WindowManager leaks
        removeOverlay()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        fakeLifecycle = FakeLifecycleOwner().apply { 
            onCreate()
            onStart() 
        }

        val localComposeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(fakeLifecycle)
            setViewTreeViewModelStoreOwner(fakeLifecycle)
            setViewTreeSavedStateRegistryOwner(fakeLifecycle)

            setContent {
                MyApplicationTheme {
                    OverlayPopupScreen(
                        amount = amount,
                        merchantCleaned = merchantCleaned,
                        merchantRaw = merchantRaw,
                        initialText = initialText,
                        initialSelection = initialSelection,
                        onDismiss = {
                            stopSelf()
                        },
                        onConfirm = { itemDescription, category ->
                            serviceScope.launch {
                                val finalMerchant = if (category == "Personal Transfers") {
                                    SmsParser.extractRecipientName(itemDescription, merchantCleaned)
                                } else {
                                    merchantCleaned
                                }
                                withContext(Dispatchers.IO) {
                                    repository.upsertMerchantMapping(
                                        MerchantMapping(
                                            merchant_name = merchantCleaned,
                                            default_item = itemDescription,
                                            default_category = category
                                        )
                                    )
                                    repository.insertTransaction(
                                        Transaction(
                                            amount = amount,
                                            merchant = finalMerchant,
                                            item_description = itemDescription,
                                            category = category,
                                            timestamp = System.currentTimeMillis()
                                        )
                                    )
                                }
                                stopSelf()
                            }
                        }
                    )
                }
            }
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        // Explicitly clear FLAG_NOT_FOCUSABLE to allow typing keyboard entries
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()

        try {
            windowManager?.addView(localComposeView, params)
            composeView = localComposeView
        } catch (e: Exception) {
            Log.e("OverlayService", "Failed to add overlay window layout: ${e.message}")
        }
    }

    private fun removeOverlay() {
        composeView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.e("OverlayService", "Failed to clear layout template view: ${e.message}")
            }
            composeView = null
        }
        fakeLifecycle?.onDestroy()
        fakeLifecycle = null
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }
}

// Simulated Lifecycle Owner details for WindowManager background draw
class FakeLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = controller.savedStateRegistry

    fun onCreate() {
        controller.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onStart() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

@Composable
fun OverlayPopupScreen(
    amount: Float,
    merchantCleaned: String,
    merchantRaw: String,
    initialText: String,
    initialSelection: TextRange,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var itemDescriptionValue by remember {
        mutableStateOf(TextFieldValue(initialText, initialSelection))
    }
    var errorMessage by remember { mutableStateOf("") }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(200)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val dynamicCategory = remember(itemDescriptionValue.text) {
        SmsParser.mapItemToCategory(itemDescriptionValue.text)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFFFDF8F6), // Warm Linen
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            )
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Drag handle template
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(6.dp)
                    .background(Color(0xFFD6C2BA), CircleShape)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Brand details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "💸 Spent ₹${String.format(Locale.getDefault(), "%.2f", amount)}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF201A18)
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "at $merchantCleaned",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF85736B)
                        )
                    )
                }

                val isMapped = initialText.isNotEmpty()
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isMapped) Color(0xFFE8F5E9) else Color(0xFFFFD9CC),
                            shape = RoundedCornerShape(50)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isMapped) "MAPPED MATCH" else "NEW VENDOR",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isMapped) Color(0xFF2E7D32) else Color(0xFF311005)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // What did you buy label
            Text(
                text = "WHAT DID YOU BUY?",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF85736B),
                    letterSpacing = 1.2.sp
                ),
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
            )

            OutlinedTextField(
                value = itemDescriptionValue,
                onValueChange = { itemDescriptionValue = it },
                placeholder = { Text("e.g. Chai, Samosa, Spotify, Fuel", color = Color(0xFF85736B).copy(alpha = 0.6f)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("overlay_item_input"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val trimmed = itemDescriptionValue.text.trim()
                        if (trimmed.isEmpty()) {
                            errorMessage = "Please describe what you purchased!"
                        } else {
                            keyboardController?.hide()
                            onConfirm(trimmed, dynamicCategory)
                        }
                    }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF3E9E5),
                    unfocusedContainerColor = Color(0xFFF3E9E5),
                    focusedBorderColor = Color(0xFF311005),
                    unfocusedBorderColor = Color(0xFFD6C2BA),
                    focusedTextColor = Color(0xFF201A18),
                    unfocusedTextColor = Color(0xFF201A18)
                )
            )

            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Filter tags
            Text(
                text = "CATEGORY DETECTED",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF85736B),
                    letterSpacing = 1.2.sp
                ),
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )

            val categories = listOf("Food & Drinks", "Groceries & Shopping", "Travel & Transport", "Bills & Utilities", "Medical & Healthcare", "Personal Transfers", "Other")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isActive = dynamicCategory == cat
                    val badgeBg = if (isActive) Color(0xFF311005) else Color.White
                    val badgeText = if (isActive) Color.White else Color(0xFF52443D)
                    val badgeBorder = if (isActive) Color(0xFF311005) else Color(0xFFD6C2BA)

                    Box(
                        modifier = Modifier
                            .background(badgeBg, RoundedCornerShape(50))
                            .clickable {
                                val newText = when (cat) {
                                    "Food & Drinks" -> "Food / Drink"
                                    "Groceries & Shopping" -> "Grocery / Shopping"
                                    "Travel & Transport" -> "Fuel Travel"
                                    "Bills & Utilities" -> "Recharge Bill"
                                    "Medical & Healthcare" -> "Pharmacy Medical"
                                    "Personal Transfers" -> "Send to Pavan"
                                    else -> "Item"
                                }
                                itemDescriptionValue = TextFieldValue(
                                    text = newText,
                                    selection = TextRange(0, newText.length)
                                )
                            }
                            .border(1.dp, badgeBorder, RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "${getEmojiForCategory(cat)} $cat",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = badgeText
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("cancel_expense_button"),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFFD6C2BA)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF52443D)
                    )
                ) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Button(
                    onClick = {
                        val trimmed = itemDescriptionValue.text.trim()
                        if (trimmed.isEmpty()) {
                            errorMessage = "Please describe what you purchased!"
                        } else {
                            keyboardController?.hide()
                            onConfirm(trimmed, dynamicCategory)
                        }
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .height(52.dp)
                        .testTag("confirm_expense_button"),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF311005),
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirm Icon"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Confirm Expense",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
