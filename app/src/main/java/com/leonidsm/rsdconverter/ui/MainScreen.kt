package com.leonidsm.rsdconverter.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leonidsm.rsdconverter.data.RatesError
import com.leonidsm.rsdconverter.ui.theme.PositiveGreen
import kotlin.math.abs

private val quickAmounts = listOf("50", "100", "500", "1000")

@Composable
fun MainScreen(viewModel: CurrencyViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val amount = remember(uiState.amountInput) { uiState.amountInput.toDoubleOrNull() ?: 0.0 }
    val record = uiState.currentRecord
    // Only trust the cached/fetched rates if they were fetched for the currency
    // currently selected as the source - otherwise we'd multiply by a stale base.
    val ratesForCurrentBase = record?.current?.takeIf { it.base == uiState.fromCurrency }
    val previousDayRates = record?.previousDayRates
    val targetCurrencies = remember(uiState.watchedCurrencies, uiState.fromCurrency) {
        uiState.watchedCurrencies.filter { it != uiState.fromCurrency }.map { currencyInfo(it) }
    }

    var showSourcePicker by remember { mutableStateOf(false) }
    var showAddCurrencyPicker by remember { mutableStateOf(false) }

    // Auto-refresh only runs while this screen is at least STARTED, and is torn
    // down when the app goes to background - so it stops spending API quota.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.runAutoRefreshWhileVisible()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceVariant),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Header(
                isLoading = uiState.isLoading,
                lastUpdated = ratesForCurrentBase?.fetchedAtEpochMillis,
                hasError = uiState.error != null,
                useDynamicColor = uiState.useDynamicColor,
                onRefresh = viewModel::refresh,
                onToggleDynamicColor = viewModel::toggleDynamicColor,
            )

            AmountInputCard(
                fromCurrency = uiState.fromCurrency,
                amountInput = uiState.amountInput,
                onAmountChange = viewModel::onAmountChange,
                onCurrencyClick = { showSourcePicker = true },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quickAmounts.forEach { value ->
                    SuggestionChip(
                        onClick = { viewModel.onAmountChange(value) },
                        label = { Text(value) },
                    )
                }
            }

            AnimatedVisibility(visible = uiState.error != null, enter = fadeIn(), exit = fadeOut()) {
                ErrorBanner(
                    error = uiState.error ?: RatesError.NoNetwork,
                    hasCachedRates = ratesForCurrentBase != null,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                targetCurrencies.forEach { target ->
                    val rate = ratesForCurrentBase?.rates?.get(target.code)
                    val previousRate = previousDayRates?.get(target.code)
                    val pctChange = if (rate != null && previousRate != null && previousRate != 0.0) {
                        (rate - previousRate) / previousRate * 100.0
                    } else {
                        null
                    }
                    ResultCard(
                        code = target.code,
                        flag = target.flag,
                        baseCode = uiState.fromCurrency,
                        convertedAmount = rate?.let { it * amount },
                        rate = rate,
                        pctChange = pctChange,
                        onSelect = { viewModel.onWatchedCurrencyTap(target.code) },
                        onRemove = { viewModel.removeWatchedCurrency(target.code) },
                    )
                }
                AddCurrencyRow(onClick = { showAddCurrencyPicker = true })
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Курс обновляется автоматически раз в час",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showSourcePicker) {
        CurrencyPickerDialog(
            title = "Из какой валюты",
            options = allCurrencies,
            onDismiss = { showSourcePicker = false },
            onSelect = { code ->
                viewModel.onFromCurrencyChange(code)
                showSourcePicker = false
            },
        )
    }

    if (showAddCurrencyPicker) {
        CurrencyPickerDialog(
            title = "Добавить валюту",
            options = allCurrencies.filter { c ->
                c.code != uiState.fromCurrency && c.code !in uiState.watchedCurrencies
            },
            onDismiss = { showAddCurrencyPicker = false },
            onSelect = { code ->
                viewModel.addWatchedCurrency(code)
                showAddCurrencyPicker = false
            },
        )
    }
}

@Composable
private fun Header(
    isLoading: Boolean,
    lastUpdated: Long?,
    hasError: Boolean,
    useDynamicColor: Boolean,
    onRefresh: () -> Unit,
    onToggleDynamicColor: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Конвертер валют",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = when {
                    lastUpdated == null && isLoading -> "Загрузка..."
                    lastUpdated == null -> "Нет данных для этой валюты"
                    hasError -> "Обновлено ${formatRelativeTime(lastUpdated)} · не удалось обновить"
                    else -> "Обновлено ${formatRelativeTime(lastUpdated)}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleDynamicColor) {
                Icon(
                    imageVector = Icons.Filled.Palette,
                    contentDescription = if (useDynamicColor) {
                        "Цвета: из обоев (нажми для фирменного стиля)"
                    } else {
                        "Цвета: фирменный стиль (нажми для цветов из обоев)"
                    },
                    tint = if (useDynamicColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val infiniteTransition = rememberInfiniteTransition(label = "refresh-rotation")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
                label = "rotation",
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Обновить курс",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(if (isLoading) rotation else 0f),
                )
            }
        }
    }
}

@Composable
private fun AmountInputCard(
    fromCurrency: String,
    amountInput: String,
    onAmountChange: (String) -> Unit,
    onCurrencyClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val selected = currencyInfo(fromCurrency)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onCurrencyClick() },
            ) {
                Text(text = selected.flag, fontSize = 26.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = selected.code,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Выбрать валюту",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            TextField(
                value = amountInput,
                onValueChange = onAmountChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.displayMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CurrencyPickerDialog(
    title: String,
    options: List<CurrencyInfo>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, options) {
        if (query.isBlank()) {
            options
        } else {
            options.filter {
                it.code.contains(query, ignoreCase = true) || it.nameRu.contains(query, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Код или название") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(filtered, key = { it.code }) { currency ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(currency.code) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(text = currency.flag, fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = currency.code,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = currency.nameRu,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                text = "Ничего не найдено",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    code: String,
    flag: String,
    baseCode: String,
    convertedAmount: Double?,
    rate: Double?,
    pctChange: Double?,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = flag, fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = code,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedContent(targetState = convertedAmount, label = "amount-$code") { value ->
                        Text(
                            text = value?.let { formatAmount(it) } ?: "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Убрать валюту",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = rate?.let { "1 $code = ${formatRate(1.0 / it)} $baseCode" } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (pctChange != null && abs(pctChange) >= 0.005) {
                    val isUp = pctChange > 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isUp) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = if (isUp) PositiveGreen else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = formatPercent(abs(pctChange)),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUp) PositiveGreen else MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCurrencyRow(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "+ Добавить валюту",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ErrorBanner(error: RatesError, hasCachedRates: Boolean) {
    val (icon, message) = when (error) {
        RatesError.NoNetwork -> Icons.Filled.WifiOff to if (hasCachedRates) {
            "Нет соединения. Показан последний известный курс."
        } else {
            "Нет соединения и сохранённого курса для этой валюты."
        }
        RatesError.InvalidKey -> Icons.Filled.Key to
            "Неверный API-ключ. Проверьте EXCHANGE_API_KEY в local.properties."
        RatesError.InactiveAccount -> Icons.Filled.Key to
            "Аккаунт API ещё не активирован. Подтвердите почту на exchangerate-api.com."
        RatesError.QuotaReached -> Icons.Filled.HourglassEmpty to
            "Исчерпан лимит запросов на текущий месяц. Показан последний курс."
        is RatesError.UnsupportedCode -> Icons.Filled.ErrorOutline to
            "Одна из валют не поддерживается сервисом курсов."
        is RatesError.Unknown -> Icons.Filled.ErrorOutline to
            ("Не удалось обновить курс" + (error.detail?.let { ": $it" } ?: "."))
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
