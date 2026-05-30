package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.ExpenseRepository
import com.example.data.Transaction
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.SmsParser
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(this)
        val repository = ExpenseRepository(database.expenseDao())
        val factory = MainViewModelFactory(repository)

        // Clean activity-ktx delegated ViewModel instantiation
        val viewModel: MainViewModel by viewModels { factory }

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        ExpenseTrackerDashboard(
                            viewModel = viewModel,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                        
                        // Sliding bottom overlay mimicking system notification draw-over
                        SlidingOverlay(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseTrackerDashboard(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val transactionList by viewModel.transactions.collectAsStateWithLifecycle()

    val availableMonths = remember(transactionList) {
        val months = mutableListOf<String>()
        // Always include current month by default
        val currentMonthStr = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        months.add(currentMonthStr)
        
        val sdfMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        for (t in transactionList) {
            val monthStr = sdfMonth.format(Date(t.timestamp))
            if (!months.contains(monthStr)) {
                months.add(monthStr)
            }
        }
        months.distinct().sortedWith(compareByDescending {
            try {
                SimpleDateFormat("MMMM yyyy", Locale.getDefault()).parse(it) ?: Date(0)
            } catch (e: Exception) {
                Date(0)
            }
        })
    }

    var selectedMonth by remember(availableMonths) {
        mutableStateOf(availableMonths.firstOrNull() ?: SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date()))
    }

    val filteredTransactions = remember(transactionList, selectedMonth) {
        val sdfMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        transactionList.filter {
            sdfMonth.format(Date(it.timestamp)) == selectedMonth
        }
    }

    val totalExpense = remember(filteredTransactions) {
        filteredTransactions.sumOf { it.amount.toDouble() }.toFloat()
    }

    val categorySums = remember(filteredTransactions) {
        val sums = mutableMapOf<String, Float>()
        val defaultCategories = listOf("Food & Drinks", "Groceries & Shopping", "Travel & Transport", "Bills & Utilities", "Medical & Healthcare", "Other")
        defaultCategories.forEach { sums[it] = 0.0f }
        
        filteredTransactions.forEach { tx ->
            val cat = tx.category
            sums[cat] = (sums[cat] ?: 0.0f) + tx.amount
        }
        sums
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- 2. Dashboard Summary Block (Sleek Interface Peach Card) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("total_expense_card"),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD9CC))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Monthly Spending",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF311005).copy(alpha = 0.7f)
                            )
                        )
                        
                        // Month Selector Dropdown
                        MonthSelector(
                            selectedMonth = selectedMonth,
                            availableMonths = availableMonths,
                            onMonthSelected = { selectedMonth = it }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "₹" + String.format(Locale.getDefault(), "%,.2f", totalExpense),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF311005),
                            letterSpacing = (-1).sp
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Badge 1: Transaction count using filtered list
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${filteredTransactions.size} transactions",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF311005)
                                )
                            )
                        }

                        // Badge 2: Dynamic time range context
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$selectedMonth tracking",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF311005)
                                )
                            )
                        }
                    }
                }
            }
        }

        // --- Category Breakdown Panel (Progress Bars) ---
        CategoryBreakdownSection(
            categorySums = categorySums,
            totalExpense = totalExpense
        )

        // --- 3. Header & Clear Button ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Transaction History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            TextButton(
                onClick = { viewModel.onClearDatabase() },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.testTag("clear_db_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear History Icon",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Reset Sandbox",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // --- 4. LazyColumn List ---
        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "💳",
                        fontSize = 48.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = "No recorded transactions yet.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Transactions for $selectedMonth will show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = filteredTransactions,
                    key = { it.id }
                ) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transaction_item_${transaction.id}")
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionItem(
    transaction: Transaction,
    modifier: Modifier = Modifier
) {
    val categoryEmoji = remember(transaction.category) {
        getEmojiForCategory(transaction.category)
    }

    val formattedTime = remember(transaction.timestamp) {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        sdf.format(Date(transaction.timestamp))
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle category background matching Sleek theme (bg-[#f3e9e5])
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFF3E9E5), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = categoryEmoji,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Transaction details layout matching Sleek template
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = transaction.merchant,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF201A18)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${transaction.item_description} • ${transaction.category}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF85736B),
                        fontWeight = FontWeight.Normal
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount and timestamp right-aligned
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "₹" + String.format(Locale.getDefault(), "%.2f", transaction.amount),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF201A18)
                    )
                )
                
                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF85736B)
                    )
                )
            }
        }
    }
}

@Composable
fun SlidingOverlay(
    viewModel: MainViewModel
) {
    if (!viewModel.isOverlayVisible) return

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Dynamic selection & soft keyboard opening execution block
    LaunchedEffect(viewModel.isOverlayVisible) {
        if (viewModel.isOverlayVisible) {
            delay(150) // Safe buffer for presentation slide animation
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val dynamicCategory = remember(viewModel.itemDescriptionValue.text) {
        SmsParser.mapItemToCategory(viewModel.itemDescriptionValue.text)
    }

    // Semi-transparent overlay blocker acting as backdrop
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(enabled = true, onClick = {
                keyboardController?.hide()
                viewModel.onDismissOverlay()
            }),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Prevent click propagation from details card itself
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = true, onClick = {}), // consumes clicks
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = Color(0xFFFDF8F6), // bg-[#fdf8f6] Warm Linen
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding() // Keep clear of navigation bars/safe borders
                    .padding(24.dp)
            ) {
                // Dimple top line for drawer aesthetic
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(6.dp)
                        .background(Color(0xFFD6C2BA), CircleShape)
                        .align(Alignment.CenterHorizontally)
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                // Spend Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "💸 Spent ₹${String.format(Locale.getDefault(), "%.2f", viewModel.currentAmount)}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF201A18)
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "at ${viewModel.currentMerchant}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF85736B)
                            )
                        )
                    }

                    // Preloaded mapping indicator chip or vendor tag
                    val isMapped = !viewModel.itemDescriptionValue.text.isEmpty()
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

                // Input Box with elegant label spacing
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
                    value = viewModel.itemDescriptionValue,
                    onValueChange = { viewModel.itemDescriptionValue = it },
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
                            keyboardController?.hide()
                            viewModel.onConfirmExpense()
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

                if (viewModel.errorMessage.isNotEmpty()) {
                    Text(
                        text = viewModel.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Dynamic Category Horizontal Badges matching Sleek design suggestions
                Text(
                    text = "CATEGORY DETECTED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF85736B),
                        letterSpacing = 1.2.sp
                    ),
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )

                val categories = listOf("Food & Drinks", "Groceries", "Travel & Transport", "Shopping", "Bills & Utilities")
                
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
                                    // Let user tap tag to pre-fill standard category search
                                    val currentText = viewModel.itemDescriptionValue.text
                                    val newText = when (cat) {
                                        "Food & Drinks" -> "Food / Drink"
                                        "Groceries" -> "Grocery"
                                        "Travel & Transport" -> "Fuel Travel"
                                        "Shopping" -> "Shopping Product"
                                        "Bills & Utilities" -> "Recharge Bill"
                                        else -> "Item"
                                    }
                                    viewModel.itemDescriptionValue = TextFieldValue(
                                        text = newText,
                                        selection = androidx.compose.ui.text.TextRange(0, newText.length)
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

                // Actions Button Cluster
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.onDismissOverlay()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("cancel_expense_button"),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD6C2BA)),
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
                            keyboardController?.hide()
                            viewModel.onConfirmExpense()
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
}

@Composable
fun MonthSelector(
    selectedMonth: String,
    availableMonths: List<String>,
    onMonthSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { expanded = true },
            color = Color.White.copy(alpha = 0.6f),
            shape = RoundedCornerShape(50)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = selectedMonth,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF311005)
                    )
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select Month",
                    tint = Color(0xFF311005),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFFFDF8F6))
        ) {
            availableMonths.forEach { month ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = month,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (month == selectedMonth) FontWeight.Bold else FontWeight.Normal,
                                color = Color(0xFF201A18)
                            )
                        )
                    },
                    onClick = {
                        onMonthSelected(month)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun CategoryBreakdownSection(
    categorySums: Map<String, Float>,
    totalExpense: Float,
    modifier: Modifier = Modifier
) {
    val activeCategories = remember(categorySums) {
        categorySums.filter { it.value > 0.0f }.toList().sortedByDescending { it.second }
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF8F6)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEFE6E2))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Category Breakdown",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF201A18),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            if (activeCategories.isEmpty()) {
                Text(
                    text = "No category data available for this month.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF85736B)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    activeCategories.forEach { (category, sum) ->
                        val percentage = if (totalExpense > 0f) (sum / totalExpense) else 0f
                        
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = getEmojiForCategory(category),
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = category,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF201A18)
                                        )
                                    )
                                    Text(
                                        text = "• ${String.format(Locale.getDefault(), "%.0f%%", percentage * 100)}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFF85736B)
                                        )
                                    )
                                }
                                Text(
                                    text = "₹${String.format(Locale.getDefault(), "%,.2f", sum)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF201A18)
                                    )
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            LinearProgressIndicator(
                                progress = { percentage },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = getColorForCategory(category),
                                trackColor = Color(0xFFF3E9E5)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Preset SMS Container Data class
data class PresetSms(
    val label: String,
    val smsText: String
)

// Helper color and emoji mappings to create beautiful dynamic graphics
fun getEmojiForCategory(category: String): String {
    return when (category) {
        "Food & Drinks" -> "🍔"
        "Groceries & Shopping" -> "🛒"
        "Groceries" -> "🛒"
        "Shopping" -> "🛍️"
        "Travel & Transport" -> "🚗"
        "Bills & Utilities" -> "⚡"
        "Medical & Healthcare" -> "💊"
        else -> "🏷️"
    }
}

fun getColorForCategory(category: String): Color {
    return when (category) {
        "Food & Drinks" -> Color(0xFFFF8A65) // Coral Orange
        "Groceries & Shopping" -> Color(0xFF81C784) // Forest Green
        "Groceries" -> Color(0xFF81C784) // Forest Green
        "Shopping" -> Color(0xFFBA68C8) // Violet Purple
        "Travel & Transport" -> Color(0xFF4FC3F7) // Sea Blue
        "Bills & Utilities" -> Color(0xFFFFD54F) // Gold Amber
        "Medical & Healthcare" -> Color(0xFFF06292) // Rose Pink
        else -> Color(0xFF90A4AE) // Slate Grey
    }
}
