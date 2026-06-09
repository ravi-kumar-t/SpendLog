package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.BorderStroke
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.ExpenseRepository
import com.example.data.Transaction
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.SmsParser
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

enum class AppScreen {
    HOME,
    DASHBOARD,
    CATEGORIES,
    CATEGORIES_DETAIL
}

data class CategoryCardInfo(
    val name: String,
    val count: Int,
    val totalSpent: Float
)

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

    var expandedMerchants by remember(selectedMonth) { mutableStateOf(setOf<String>()) }

    var selectedCategoryFilter by remember { mutableStateOf("All") }

    var currentScreen by remember { mutableStateOf(AppScreen.DASHBOARD) }

    var selectedCategoryDetail by remember { mutableStateOf("") }

    var showManualEntry by remember { mutableStateOf(false) }

    BackHandler(enabled = currentScreen == AppScreen.CATEGORIES_DETAIL) {
        // Clear the deep-link pointer target and route safely back to the home main feed screen
        viewModel.clearCategorySelection() 
        currentScreen = AppScreen.HOME 
    }

    BackHandler(enabled = currentScreen == AppScreen.CATEGORIES) {
        currentScreen = AppScreen.HOME
    }

    val filteredTransactions = remember(transactionList, selectedMonth) {
        val sdfMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        transactionList.filter {
            sdfMonth.format(Date(it.timestamp)) == selectedMonth
        }
    }

    val categoryStats = remember(filteredTransactions) {
        val baseCategories = listOf(
            "Food & Drinks",
            "Groceries & Shopping",
            "Travel & Transport",
            "Bills & Utilities",
            "Medical & Healthcare",
            "Other",
            "Personal Transfers"
        )
        val transactionCategories = filteredTransactions.map { it.category }.distinct()
        val allCategories = (baseCategories + transactionCategories).distinct()

        allCategories.map { cat ->
            val txs = filteredTransactions.filter { it.category == cat && it.type != "TYPE_INCOME" }
            CategoryCardInfo(
                name = cat,
                count = txs.size,
                totalSpent = txs.sumOf { it.amount.toDouble() }.toFloat()
            )
        }.filter { it.count > 0 }
    }

    val totalExpense = remember(filteredTransactions) {
        filteredTransactions
            .filter { it.category != "Personal Transfers" && it.type != "TYPE_INCOME" }
            .sumOf { it.amount.toDouble() }.toFloat()
    }

    val categorySums = remember(filteredTransactions) {
        val sums = mutableMapOf<String, Float>()
        val defaultCategories = listOf("Food & Drinks", "Groceries & Shopping", "Travel & Transport", "Bills & Utilities", "Medical & Healthcare", "Other")
        defaultCategories.forEach { sums[it] = 0.0f }
        
        filteredTransactions.forEach { tx ->
            val cat = tx.category
            if (cat != "Personal Transfers" && tx.type != "TYPE_INCOME") {
                sums[cat] = (sums[cat] ?: 0.0f) + tx.amount
            }
        }
        sums
    }

    AnimatedContent(
        targetState = currentScreen,
        label = "screen_transition",
        modifier = modifier.fillMaxSize()
    ) { screen ->
        when (screen) {
            AppScreen.HOME, AppScreen.DASHBOARD -> {
                Scaffold(
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showManualEntry = true },
                            containerColor = Color(0xFF311005),
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier
                                .border(1.dp, Color(0xFFD6C2BA).copy(alpha = 0.4f), CircleShape)
                                .testTag("add_manual_expense_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Expense",
                                tint = Color.White
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
        // --- 2. Dashboard Summary Block (Sleek Interface Peach Card) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 0.dp)
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
                        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp)
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
                        val expenseCount = remember(filteredTransactions) {
                            filteredTransactions.count { it.category != "Personal Transfers" && it.type != "TYPE_INCOME" }
                        }
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$expenseCount transactions",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF311005)
                                )
                            )
                        }

                        // Interactive Button: Show Category Breakdown
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(50))
                                .clip(RoundedCornerShape(50))
                                .clickable { currentScreen = AppScreen.CATEGORIES }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("category_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = "Category",
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFF311005)
                                )
                                Text(
                                    text = "Category",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF311005)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(11.dp))

        // --- Category Filter Panel of Buttons ---
        CategoryFilterPanel(
            selectedCategory = selectedCategoryFilter,
            onCategorySelected = { selectedCategoryFilter = it }
        )

        Spacer(modifier = Modifier.height(0.dp))

        // --- 3. Header & Clear Button ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 8.dp),
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
                onClick = { viewModel.triggerClearConfirmation() },
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
                    text = "Clear",
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
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = "No Transactions Icon",
                        modifier = Modifier
                            .size(48.dp)
                            .padding(bottom = 12.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
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
            val listToRender = remember(filteredTransactions, selectedCategoryFilter) {
                if (selectedCategoryFilter == "All") {
                    filteredTransactions
                } else {
                    filteredTransactions.filter { it.category == selectedCategoryFilter }
                }.sortedByDescending { it.timestamp }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(listToRender, key = { it.id }) { transaction ->
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
            }

            AppScreen.CATEGORIES -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF201A18))
                ) {
                    // Top Navigation Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { currentScreen = AppScreen.HOME },
                            modifier = Modifier.testTag("categories_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Categories",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    if (categoryStats.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No category payments recorded for this month.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFC4B4AE),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            items(categoryStats) { stat ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCategoryFilter = stat.name
                                            selectedCategoryDetail = stat.name
                                            currentScreen = AppScreen.CATEGORIES_DETAIL
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left category circle icon (borderless & flat style on dark background)
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(Color.White.copy(alpha = 0.08f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = getIconForCategory(stat.name),
                                                contentDescription = stat.name,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Category Name on top (white), Payments counter underneath (muted subtext)
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = stat.name,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            val paymentText = if (stat.count == 1) "1 Payment" else "${stat.count} Payments"
                                            Text(
                                                text = paymentText,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color(0xFFC4B4AE)
                                                )
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Total spent sum (stacked over "Spent" label)
                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "₹" + String.format(Locale.getDefault(), "%,.0f", stat.totalSpent),
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Spent",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color(0xFFC4B4AE)
                                                )
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Chevron arrow next to the spent currency stack
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = "Chevron icon",
                                            tint = Color(0xFFC4B4AE),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = 0.12f),
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(start = 52.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AppScreen.CATEGORIES_DETAIL -> {
                val detailTxs = remember(filteredTransactions, selectedCategoryDetail) {
                    filteredTransactions.filter { it.category == selectedCategoryDetail && it.type != "TYPE_INCOME" }
                }
                val detailTotalSpent = remember(detailTxs) {
                    detailTxs.sumOf { it.amount.toDouble() }.toFloat()
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF201A18))
                ) {
                    // Top Navigation Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.clearCategorySelection()
                                currentScreen = AppScreen.HOME
                            },
                            modifier = Modifier.testTag("categories_detail_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = selectedCategoryDetail,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Summary Banner - Sleek clean micro-card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF311005) // Premium deep espresso cocoa card
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD6C2BA).copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "Total Spent: ₹" + String.format(Locale.getDefault(), "%,.2f", detailTotalSpent),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFD9CC)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (detailTxs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No transactions found for this category.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFC4B4AE),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Borderless List Overhaul: Flat LazyColumn showing transactions of this category
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            items(detailTxs) { tx ->
                                val formattedTime = remember(tx.timestamp) {
                                    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                                    val sdfDate = SimpleDateFormat("dd MMM", Locale.getDefault())
                                    "${sdfDate.format(Date(tx.timestamp))} • ${sdf.format(Date(tx.timestamp))}"
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left category circle icon
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(Color.White.copy(alpha = 0.08f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = getIconForCategory(tx.category),
                                                contentDescription = tx.category,
                                                modifier = Modifier.size(20.dp),
                                                tint = Color.White
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Transaction details layout
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = tx.item_description,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            
                                            Spacer(modifier = Modifier.height(2.dp))

                                            Text(
                                                text = tx.merchant,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color(0xFFC4B4AE),
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
                                                text = "₹" + String.format(Locale.getDefault(), "%,.2f", tx.amount),
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            )
                                            
                                            Spacer(modifier = Modifier.height(2.dp))

                                            Text(
                                                text = formattedTime,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color(0xFFC4B4AE)
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = 0.12f),
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(start = 52.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (viewModel.showClearConfirmationDialog) {
        Dialog(
            onDismissRequest = { viewModel.dismissClearConfirmation() }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("clear_confirmation_dialog"),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF201A18)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Clear History?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Text(
                        text = "This will permanently delete your local transaction ledger. This action cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE6DCD8)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { viewModel.dismissClearConfirmation() }
                        ) {
                            Text(
                                text = "Cancel",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF9E928F),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        TextButton(
                            onClick = {
                                viewModel.confirmClearDatabase()
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color(0xFFFFB4AB)
                            )
                        ) {
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showManualEntry) {
        var itemDescription by remember { mutableStateOf("") }
        var amountText by remember { mutableStateOf("") }
        var merchantInput by remember { mutableStateOf("") }
        val selectedCategory = viewModel.predictedCategory
        var validationError by remember { mutableStateOf("") }

        val trimmedDesc = itemDescription.trim()
        val vocabMatch = if (trimmedDesc.isEmpty()) null else SmsParser.matchVocabulary(trimmedDesc)
        val suggestionWord = vocabMatch?.key ?: ""
        val suggestionSuffix = if (suggestionWord.isNotEmpty() && suggestionWord.length > itemDescription.length) {
            val lowerWord = suggestionWord.lowercase(Locale.ROOT)
            val lowerInput = itemDescription.lowercase(Locale.ROOT)
            if (lowerWord.startsWith(lowerInput)) {
                suggestionWord.substring(itemDescription.length)
            } else ""
        } else ""

        LaunchedEffect(itemDescription) {
            viewModel.predictCategoryForInput(itemDescription)
        }

        Dialog(
            onDismissRequest = {
                showManualEntry = false
                viewModel.clearPrediction()
            }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .testTag("manual_entry_dialog"),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFFFDF8F6), // bg-WarmLinen
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Add Expense",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF201A18)
                            )
                        )
                        IconButton(
                            onClick = {
                                showManualEntry = false
                                viewModel.clearPrediction()
                            },
                            modifier = Modifier.testTag("manual_entry_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Manual Input",
                                tint = Color(0xFF85736B)
                            )
                        }
                    }

                    // Fields:
                    // 1. What did you buy?
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "WHAT DID YOU BUY?",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF85736B),
                                letterSpacing = 1.2.sp
                            )
                        )
                        OutlinedTextField(
                            value = itemDescription,
                            onValueChange = { inputString ->
                                // 1. Immediately write the typed characters to your active string state tracker variable
                                itemDescription = inputString 
                                
                                // 2. Explicitly kick off the ViewModel auto-categorization method right on the change event loop
                                viewModel.predictCategoryForInput(inputString)
                            },
                            placeholder = { Text("e.g. Snacks, Coffee, Taxi", color = Color(0xFF85736B).copy(alpha = 0.6f)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_item_input")
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown && 
                                        (keyEvent.key == Key.Tab || keyEvent.key == Key.Enter)) {
                                        if (suggestionWord.isNotEmpty() && suggestionSuffix.isNotEmpty()) {
                                            itemDescription = suggestionWord
                                            viewModel.predictCategoryForInput(suggestionWord)
                                            true
                                        } else {
                                            false
                                        }
                                    } else {
                                        false
                                    }
                                },
                            singleLine = true,
                            visualTransformation = SuggestionVisualTransformation(suggestionSuffix),
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
                        if (suggestionWord.isNotEmpty() && suggestionSuffix.isNotEmpty()) {
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Press Tab/Enter or tap to complete:",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF85736B).copy(alpha = 0.8f), fontSize = 11.sp)
                                )
                                Surface(
                                    onClick = {
                                        itemDescription = suggestionWord
                                        viewModel.predictCategoryForInput(suggestionWord)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF311005).copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, Color(0xFF311005).copy(alpha = 0.2f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = suggestionWord,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF311005),
                                                fontSize = 11.sp
                                            )
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Complete",
                                            tint = Color(0xFF311005),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Amount and Where did you pay side by side OR stacked
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "AMOUNT (₹)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF85736B),
                                    letterSpacing = 1.2.sp
                                )
                            )
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it },
                                placeholder = { Text("0.00", color = Color(0xFF85736B).copy(alpha = 0.6f)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manual_amount_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
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
                        }

                        Column(
                            modifier = Modifier.weight(1.5f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "WHERE DID YOU PAY?",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF85736B),
                                    letterSpacing = 1.2.sp
                                )
                            )
                            OutlinedTextField(
                                value = merchantInput,
                                onValueChange = { merchantInput = it },
                                placeholder = { Text("e.g. Starbucks, Uber", color = Color(0xFF85736B).copy(alpha = 0.6f)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manual_merchant_input"),
                                singleLine = true,
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
                        }
                    }

                    // 3. Category Selector
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "CATEGORY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF85736B),
                                letterSpacing = 1.2.sp
                            )
                        )

                        val baseCategories = listOf(
                            "Food & Drinks",
                            "Groceries & Shopping",
                            "Travel & Transport",
                            "Bills & Utilities",
                            "Medical & Healthcare",
                            "Personal Transfers",
                            "Other"
                        )
                        val manualCategories = if (selectedCategory.isNotEmpty() && !baseCategories.contains(selectedCategory)) {
                            baseCategories + selectedCategory
                        } else {
                            baseCategories
                        }
                        val sortedCategories = if (selectedCategory.isNotEmpty()) {
                            val matchedCat = manualCategories.firstOrNull { it.equals(selectedCategory, ignoreCase = true) }
                            if (matchedCat != null) {
                                listOf(matchedCat) + manualCategories.filter { !it.equals(selectedCategory, ignoreCase = true) }
                            } else {
                                manualCategories
                            }
                        } else {
                            manualCategories
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            sortedCategories.forEach { cat ->
                                val isActive = selectedCategory == cat
                                val badgeBg = if (isActive) Color(0xFF311005) else Color.White
                                val badgeText = if (isActive) Color.White else Color(0xFF52443D)
                                val badgeBorder = if (isActive) Color(0xFF311005) else Color(0xFFD6C2BA)

                                Box(
                                    modifier = Modifier
                                        .background(badgeBg, RoundedCornerShape(50))
                                        .clickable { viewModel.setPredictedCategoryManually(cat) }
                                        .border(1.dp, badgeBorder, RoundedCornerShape(50))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .testTag("manual_category_chip_$cat")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = getIconForCategory(cat),
                                            contentDescription = cat,
                                            modifier = Modifier.size(14.dp),
                                            tint = badgeText
                                        )
                                        Text(
                                            text = cat,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = badgeText
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (validationError.isNotEmpty()) {
                        Text(
                            text = validationError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    // Save button
                    Button(
                        onClick = {
                            val trimmedItem = itemDescription.trim()
                            val amt = amountText.toFloatOrNull() ?: 0f
                            val trimmedMerchant = merchantInput.trim()

                            if (trimmedItem.isEmpty()) {
                                validationError = "Please describe what did you buy!"
                            } else if (amt <= 0.0f) {
                                validationError = "Please enter a valid amount!"
                            } else if (trimmedMerchant.isEmpty()) {
                                validationError = "Please specify where you paid!"
                            } else {
                                val finalCategory = if (selectedCategory.isNotEmpty()) selectedCategory else "Other"
                                viewModel.insertManualTransaction(
                                    itemDescription = trimmedItem,
                                    amount = amt,
                                    merchantName = trimmedMerchant,
                                    category = finalCategory
                                )
                                showManualEntry = false
                                // reset states
                                itemDescription = ""
                                amountText = ""
                                merchantInput = ""
                                viewModel.clearPrediction()
                                validationError = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_manual_expense_button"),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF311005),
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save Entry Icon"
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save Entry",
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
fun PersonalTransfersSection(
    transfers: List<Transaction>,
    modifier: Modifier = Modifier
) {
    var isParentExpanded by remember { mutableStateOf(true) }
    var expandedPeople by remember { mutableStateOf(setOf<String>()) }

    val groupedByPerson = remember(transfers) {
        transfers.groupBy { it.merchant }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E9E5).copy(alpha = 0.5f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEFE6E2))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // Expanded Master Folder Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isParentExpanded = !isParentExpanded }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF5C6BC0), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Personal Transfers",
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = "Personal Transfers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF201A18)
                        )
                        Text(
                            text = "${groupedByPerson.size} people • ${transfers.size} sent",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF85736B)
                        )
                    }
                }

                Icon(
                    imageVector = if (isParentExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = if (isParentExpanded) "Collapse Transfers" else "Expand Transfers",
                    tint = Color(0xFF52443D)
                )
            }

            AnimatedVisibility(
                visible = isParentExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedByPerson.forEach { (person, personTransfers) ->
                        val isPersonExpanded = expandedPeople.contains(person)
                        val totalSent = personTransfers.sumOf { it.amount.toDouble() }.toFloat()

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEFE6E2).copy(alpha = 0.8f))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Person Row (Sub-folder Card header)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedPeople = if (isPersonExpanded) {
                                                expandedPeople - person
                                            } else {
                                                expandedPeople + person
                                            }
                                        }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(Color(0xFFEFEFEF), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "📁", fontSize = 16.sp)
                                        }
                                        Text(
                                            text = person,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF201A18)
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "₹${String.format(Locale.getDefault(), "%,.2f", totalSent)}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF5C6BC0)
                                        )

                                        Icon(
                                            imageVector = if (isPersonExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                            contentDescription = "Toggle folder Details",
                                            tint = Color(0xFF85736B),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                AnimatedVisibility(
                                    visible = isPersonExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFFDF8F6))
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        personTransfers.sortedByDescending { it.timestamp }.forEach { tx ->
                                            val formattedTime = remember(tx.timestamp) {
                                                val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                                                sdf.format(Date(tx.timestamp))
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = tx.item_description,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFF201A18)
                                                    )
                                                    Text(
                                                        text = formattedTime,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF85736B)
                                                    )
                                                }

                                                Text(
                                                    text = "₹${String.format(Locale.getDefault(), "%,.2f", tx.amount)}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF201A18)
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
    }
}

@Composable
fun MerchantGroupFolder(
    merchantName: String,
    transactions: List<Transaction>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalAmount = remember(transactions) {
        transactions.sumOf { it.amount.toDouble() }.toFloat()
    }
    
    // Sort transactions inside the folder chronologically (newest first)
    val sortedTransactions = remember(transactions) {
        transactions.sortedByDescending { it.timestamp }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEFE6E2))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular icon/emoji matching Sleek theme (bg-[#f3e9e5])
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFF3E9E5), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Store,
                        contentDescription = "Merchant Folder Icon",
                        modifier = Modifier.size(24.dp),
                        tint = Color(0xFF53433F)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = merchantName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF201A18)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${transactions.size} shopping items",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF85736B),
                            fontWeight = FontWeight.Normal
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "₹" + String.format(Locale.getDefault(), "%,.2f", totalAmount),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF201A18)
                        )
                    )

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse transactions" else "Expand transactions",
                        tint = Color(0xFF85736B),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFDF8F6))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = Color(0xFFEFE6E2), thickness = 0.5.dp)
                    
                    sortedTransactions.forEachIndexed { index, tx ->
                        val formattedTime = remember(tx.timestamp) {
                            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                            sdf.format(Date(tx.timestamp))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Color(0xFFF3E9E5), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = getIconForCategory(tx.category),
                                        contentDescription = tx.category,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color(0xFF53433F)
                                    )
                                }
                                Column {
                                    Text(
                                        text = tx.item_description,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF201A18)
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formattedTime,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF85736B)
                                        )
                                    )
                                }
                            }

                            Text(
                                text = (if (tx.type == "TYPE_INCOME") "+ ₹" else "₹") + String.format(Locale.getDefault(), "%,.2f", tx.amount),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (tx.type == "TYPE_INCOME") Color(0xFF2E7D32) else Color(0xFF201A18)
                                )
                            )
                        }

                        if (index < sortedTransactions.size - 1) {
                            HorizontalDivider(color = Color(0xFFEFE6E2).copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                    }
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
    val formattedTime = remember(transaction.timestamp) {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        sdf.format(Date(transaction.timestamp))
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle category background matching Sleek theme
            val categoryColor = getColorForCategory(transaction.category)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(categoryColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getIconForCategory(transaction.category),
                    contentDescription = transaction.category,
                    modifier = Modifier.size(20.dp),
                    tint = categoryColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Transaction details layout matching Paytm pattern
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = transaction.item_description,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${transaction.merchant} • ${transaction.category}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
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
                    text = (if (transaction.type == "TYPE_INCOME") "+ ₹" else "₹") + String.format(Locale.getDefault(), "%,.2f", transaction.amount),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (transaction.type == "TYPE_INCOME") Color(0xFF2E7D32) else MaterialTheme.colorScheme.onBackground
                    )
                )
                
                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                )
            }
        }
        
        HorizontalDivider(
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(start = 52.dp)
        )
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
                            text = "Spent ₹${String.format(Locale.getDefault(), "%.2f", viewModel.currentAmount)}",
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
                                    // Let user tap tag to pre-fill standard category search
                                    val currentText = viewModel.itemDescriptionValue.text
                                    val newText = when (cat) {
                                        "Food & Drinks" -> "Food / Drink"
                                        "Groceries & Shopping" -> "Grocery / Shopping"
                                        "Travel & Transport" -> "Fuel Travel"
                                        "Bills & Utilities" -> "Recharge Bill"
                                        "Medical & Healthcare" -> "Pharmacy Medical"
                                        "Personal Transfers" -> "Send to Pavan"
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = getIconForCategory(cat),
                                    contentDescription = cat,
                                    modifier = Modifier.size(16.dp),
                                    tint = badgeText
                                )
                                Text(
                                    text = cat,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = badgeText
                                    )
                                )
                            }
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
fun CategoryFilterPanel(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf(
        "All",
        "Food & Drinks",
        "Groceries & Shopping",
        "Travel & Transport",
        "Bills & Utilities",
        "Medical & Healthcare",
        "Other",
        "Personal Transfers"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 0.dp)
    ) {
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.padding(top = 0.dp, bottom = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            items(categories.size) { index ->
                val category = categories[index]
                val isSelected = category == selectedCategory
                val containerColor = if (isSelected) Color(0xFF311005) else Color(0xFFFDF8F6)
                val contentColor = if (isSelected) Color.White else Color(0xFF52443D)
                val borderColor = if (isSelected) Color(0xFF311005) else Color(0xFFEFE6E2)

                Card(
                    onClick = { onCategorySelected(category) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = containerColor),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                    modifier = Modifier.testTag("filter_category_$category")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (category != "All") {
                            Icon(
                                imageVector = getIconForCategory(category),
                                contentDescription = category,
                                modifier = Modifier.size(16.dp),
                                tint = contentColor
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = "All Categories",
                                modifier = Modifier.size(16.dp),
                                tint = contentColor
                            )
                        }
                        Text(
                            text = category,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                        )
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

fun getIconForCategory(category: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (category) {
        "Food & Drinks" -> Icons.Default.Restaurant
        "Groceries & Shopping", "Groceries", "Shopping" -> Icons.Default.ShoppingCart
        "Travel & Transport" -> Icons.Default.DirectionsCar
        "Bills & Utilities" -> Icons.Default.Receipt
        "Medical & Healthcare" -> Icons.Default.LocalHospital
        "Personal Transfers" -> Icons.Default.Person
        else -> Icons.Default.Label
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
        "Personal Transfers" -> Color(0xFF5C6BC0) // Indigo Blue
        else -> Color(0xFF90A4AE) // Slate Grey
    }
}

class SuggestionVisualTransformation(private val suggestionSuffix: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        if (suggestionSuffix.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val annotatedString = AnnotatedString.Builder().apply {
            append(originalText)
            pushStyle(SpanStyle(color = Color.Gray.copy(alpha = 0.6f)))
            append(suggestionSuffix)
            pop()
        }.toAnnotatedString()

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return offset
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (offset > originalText.length) {
                    return originalText.length
                }
                return offset
            }
        }

        return TransformedText(annotatedString, offsetMapping)
    }
}
