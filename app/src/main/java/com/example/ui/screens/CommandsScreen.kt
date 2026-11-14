package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.voice.VoiceCommandCatalog

private data class DynamicCommandCard(
    val id: String,
    val category: String,
    val title: String,
    val phraseVariants: String,
    val spokenResponse: String,
    val testPhrase: String,
    val confirmationRequired: Boolean = false
)

private val parameterizedCards = listOf(
    DynamicCommandCard(
        id = "param_apps",
        category = "8. Приложения (универсальный поиск)",
        title = "Открыть любое установленное приложение",
        phraseVariants = "«Открой [название]» · «Запусти Телеграм» · «Зайди в Сбербанк» · «Включи Настройки» · «Мне нужен Озон»",
        spokenResponse = "Находит любое установленное приложение по русскому или английскому названию и открывает его",
        testPhrase = "открой настройки"
    ),
    DynamicCommandCard(
        id = "param_youtube",
        category = "9. Управление YouTube и видео",
        title = "Поиск на YouTube одной фразой и закрытие",
        phraseVariants = "«Открой ютуб и найди [что]» · «Найди видео про [тему]» · «Найди новости» · «Закрой»",
        spokenResponse = "Открывает YouTube с нужным запросом или мгновенно закрывает плеер словом «Закрой»",
        testPhrase = "найди новости"
    ),
    DynamicCommandCard(
        id = "param_calls",
        category = "6. Телефонные звонки",
        title = "Позвонить контакту, по номеру или в WhatsApp",
        phraseVariants = "«Позвони [имя]» · «Набери [имя] по вацапу» · «Видеозвонок [имя]» · «Позвони на номер 8 900 ...»",
        spokenResponse = "Находит контакт с учётом падежей, при совпадении предлагает «первый / второй» и просит подтверждение",
        testPhrase = "покажи пропущенные звонки",
        confirmationRequired = true
    ),
    DynamicCommandCard(
        id = "param_messages",
        category = "7. WhatsApp и сообщения",
        title = "Написать или ответить на сообщение (с подтверждением)",
        phraseVariants = "«Напиши [имя] [текст]» · «Отправь сообщение [имя] [текст]» · «Ответь [имя] [текст]»",
        spokenResponse = "Громко перечитывает получателя и текст сообщения и отправляет только после ответа «Да»",
        testPhrase = "отправь сообщение дочери я дома",
        confirmationRequired = true
    ),
    DynamicCommandCard(
        id = "param_ui_click",
        category = "10. Чтение экрана и уведомления",
        title = "Нажать кнопку на экране голосом",
        phraseVariants = "«Нажми [текст кнопки]» · «Кликни отправить» · «Выбери далее»",
        spokenResponse = "Ищет элемент на текущем экране и нажимает на него (через Спецвозможности)",
        testPhrase = "прочитай экран"
    ),
    DynamicCommandCard(
        id = "param_timers",
        category = "12. Будильники и напоминания",
        title = "Будильник, таймер и голосовое напоминание",
        phraseVariants = "«Поставь будильник на 7 утра» · «Поставь таймер на 10 минут» · «Напомни принять лекарство в 18:00» · «Напомни попить воды через 30 минут»",
        spokenResponse = "Ставит системный будильник, таймер или озвученное напоминание в нужное время",
        testPhrase = "какие у меня напоминания"
    ),
    DynamicCommandCard(
        id = "param_maps",
        category = "14. Карты и передвижение",
        title = "Построить маршрут и найти место рядом",
        phraseVariants = "«Построй маршрут до аптеки» · «Как добраться домой» · «Где ближайшая больница»",
        spokenResponse = "Открывает установленный навигатор или карты с готовым поиском места",
        testPhrase = "открой карты"
    )
)

@Composable
fun CommandsScreen(
    onSpeakCommandGuide: (String) -> Unit,
    onRunCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("Все") }

    val catalogCards = remember {
        val fromCatalog = VoiceCommandCatalog.entries.map { entry ->
            DynamicCommandCard(
                id = "cat_${entry.intentId}",
                category = entry.category,
                title = entry.canonicalAction,
                phraseVariants = entry.phrases.joinToString(" · ") { "«${it.replaceFirstChar { c -> c.uppercase() }}»" },
                spokenResponse = entry.spokenResponse,
                testPhrase = entry.sampleTestPhrase,
                confirmationRequired = entry.confirmationRequired
            )
        }
        parameterizedCards + fromCatalog
    }

    val categories = remember(catalogCards) {
        listOf("Все") + catalogCards.map { it.category }.distinct()
    }

    val filteredCards = remember(catalogCards, searchQuery, selectedCategory) {
        val q = searchQuery.trim().lowercase()
        catalogCards.filter { item ->
            val catOk = selectedCategory == "Все" || item.category == selectedCategory
            val qOk = q.isBlank() ||
                item.title.lowercase().contains(q) ||
                item.phraseVariants.lowercase().contains(q) ||
                item.category.lowercase().contains(q)
            catOk && qOk
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Единый словарь команд",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Не нужно запоминать точную фразу: все синонимы ведут к одному действию. Нажмите «Озвучить» или «Проверить».",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Поиск по командам (например: фонарик, вайфай, звонок)") },
                leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("commands_search_input")
            )
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories, key = { it }) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("filter_chip_${cat.hashCode()}")
                    )
                }
            }
        }

        items(filteredCards, key = { it.id }) { item ->
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = item.category,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        if (item.confirmationRequired) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "С подтверждением",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = item.phraseVariants,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.spokenResponse,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSpeakCommandGuide("${item.title}. Варианты фраз: ${item.phraseVariants}") },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                                .testTag("speak_guide_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Озвучить")
                        }
                        Button(
                            onClick = { onRunCommand(item.testPhrase) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                                .testTag("run_guide_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Проверить")
                        }
                    }
                }
            }
        }
    }
}
