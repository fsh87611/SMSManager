package com.faraj.smsapp

import android.Manifest
import androidx.compose.material3.ExperimentalMaterial3Api
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Recipient(
    val name: String,
    val phone: String
)

data class SimCard(
    val subscriptionId: Int,
    val displayName: String,
    val slotIndex: Int
)

data class MessageLog(
    val id: Long,
    val recipientName: String,
    val recipientPhone: String,
    val status: String,
    val time: String,
    val simName: String,
    val message: String
)

private val Context.smsDataStore by preferencesDataStore(
    name = "faraj_sms_data"
)

private val RECIPIENTS_KEY =
    stringPreferencesKey("recipients")

private val LOGS_KEY =
    stringPreferencesKey("message_logs")

private fun normalizePhoneNumber(value: String): String {
    var phone = value.trim()
        .replace(" ", "")
        .replace("-", "")
        .replace("(", "")
        .replace(")", "")

    when {
        phone.startsWith("+970") -> {
            phone = "0" + phone.removePrefix("+970")
        }

        phone.startsWith("00970") -> {
            phone = "0" + phone.removePrefix("00970")
        }

        phone.startsWith("970") -> {
            phone = "0" + phone.removePrefix("970")
        }
    }

    return phone
}

private fun currentTime(): String {
    return SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss",
        Locale.getDefault()
    ).format(Date())
}

private fun recipientsToJson(
    recipients: List<Recipient>
): String {
    val array = JSONArray()

    recipients.forEach { recipient ->
        val objectItem = JSONObject()
        objectItem.put("name", recipient.name)
        objectItem.put("phone", recipient.phone)
        array.put(objectItem)
    }

    return array.toString()
}

private fun recipientsFromJson(
    json: String?
): List<Recipient> {
    if (json.isNullOrBlank()) {
        return emptyList()
    }

    return try {
        val array = JSONArray(json)
        val result = mutableListOf<Recipient>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)

            result.add(
                Recipient(
                    name = item.optString("name"),
                    phone = item.optString("phone")
                )
            )
        }

        result
    } catch (_: Exception) {
        emptyList()
    }
}

private fun messageLogsToJson(
    logs: List<MessageLog>
): String {
    val array = JSONArray()

    logs.forEach { log ->
        val objectItem = JSONObject()

        objectItem.put("id", log.id)
        objectItem.put("recipientName", log.recipientName)
        objectItem.put("recipientPhone", log.recipientPhone)
        objectItem.put("status", log.status)
        objectItem.put("time", log.time)
        objectItem.put("simName", log.simName)
        objectItem.put("message", log.message)

        array.put(objectItem)
    }

    return array.toString()
}

private fun messageLogsFromJson(
    json: String?
): List<MessageLog> {
    if (json.isNullOrBlank()) {
        return emptyList()
    }

    return try {
        val array = JSONArray(json)
        val result = mutableListOf<MessageLog>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)

            result.add(
                MessageLog(
                    id = item.optLong("id"),
                    recipientName = item.optString("recipientName"),
                    recipientPhone = item.optString("recipientPhone"),
                    status = item.optString("status"),
                    time = item.optString("time"),
                    simName = item.optString("simName"),
                    message = item.optString("message")
                )
            )
        }

        result
    } catch (_: Exception) {
        emptyList()
    }
}

private fun readCsvFile(
    context: Context,
    uri: Uri
): List<Recipient> {
    return try {
        val inputStream =
            context.contentResolver.openInputStream(uri)
                ?: return emptyList()

        val text = inputStream.bufferedReader(Charsets.UTF_8).use {
            it.readText()
        }

        val result = mutableListOf<Recipient>()
        val existingPhones = mutableSetOf<String>()

        text.lines().forEach { line ->
            if (line.isBlank()) return@forEach

            val separator = when {
                line.contains(";") -> ";"
                line.contains("\t") -> "\t"
                else -> ","
            }

            val columns = line.split(separator)

            if (columns.size < 2) {
                return@forEach
            }

            var name = columns[0].trim()
            var phone = columns[1].trim()

            val firstLooksPhone =
                columns[0].filter { it.isDigit() }.length >= 7

            val secondLooksPhone =
                columns[1].filter { it.isDigit() }.length >= 7

            if (firstLooksPhone && !secondLooksPhone) {
                phone = columns[0].trim()
                name = columns[1].trim()
            }

            val normalized = normalizePhoneNumber(phone)

            if (
                normalized.filter { it.isDigit() }.length >= 7 &&
                !normalized.equals("phone", true) &&
                !normalized.equals("mobile", true) &&
                !existingPhones.contains(normalized)
            ) {
                result.add(
                    Recipient(
                        name = name.ifBlank { "بدون اسم" },
                        phone = normalized
                    )
                )

                existingPhones.add(normalized)
            }
        }

        result
    } catch (_: Exception) {
        emptyList()
    }
}

private fun readExcelFile(
    context: Context,
    uri: Uri
): List<Recipient> {
    return try {
        val inputStream =
            context.contentResolver.openInputStream(uri)
                ?: return emptyList()

        val workbook = XSSFWorkbook(inputStream)
        val sheet = workbook.getSheetAt(0)

        val result = mutableListOf<Recipient>()
        val existingPhones = mutableSetOf<String>()

        for (row in sheet) {
            val first =
                row.getCell(0)?.toString()?.trim().orEmpty()

            val second =
                row.getCell(1)?.toString()?.trim().orEmpty()

            if (first.isBlank() && second.isBlank()) {
                continue
            }

            var name = first
            var phone = second

            val firstLooksPhone =
                first.filter { it.isDigit() }.length >= 7

            val secondLooksPhone =
                second.filter { it.isDigit() }.length >= 7

            if (firstLooksPhone && !secondLooksPhone) {
                phone = first
                name = second
            }

            val normalized = normalizePhoneNumber(phone)

            if (
                normalized.filter { it.isDigit() }.length >= 7 &&
                !normalized.equals("phone", true) &&
                !normalized.equals("mobile", true) &&
                !existingPhones.contains(normalized)
            ) {
                result.add(
                    Recipient(
                        name = name.ifBlank { "بدون اسم" },
                        phone = normalized
                    )
                )

                existingPhones.add(normalized)
            }
        }

        workbook.close()

        result
    } catch (_: Exception) {
        emptyList()
    }
}

private fun getAvailableSimCards(
    context: Context
): List<SimCard> {
    if (
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return emptyList()
    }

    return try {
        val manager =
            context.getSystemService(
                Context.TELEPHONY_SUBSCRIPTION_SERVICE
            ) as SubscriptionManager

        val subscriptions =
            manager.activeSubscriptionInfoList ?: emptyList()

        subscriptions.mapIndexed { index, info ->
            SimCard(
                subscriptionId = info.subscriptionId,
                displayName =
                    info.displayName?.toString()
                        ?.ifBlank { "SIM ${index + 1}" }
                        ?: "SIM ${index + 1}",
                slotIndex = info.simSlotIndex
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions(
            arrayOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_PHONE_STATE
            ),
            1001
        )

        setContent {
            SmsManagerApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsManagerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentPage by remember {
        mutableStateOf(0)
    }

    var recipients by remember {
        mutableStateOf<List<Recipient>>(emptyList())
    }

    var messageLogs by remember {
        mutableStateOf<List<MessageLog>>(emptyList())
    }

    var selectedPhones by remember {
        mutableStateOf<Set<String>>(emptySet())
    }

    var simCards by remember {
        mutableStateOf<List<SimCard>>(emptyList())
    }

    var selectedSimId by remember {
        mutableStateOf<Int?>(null)
    }

    var messageText by remember {
        mutableStateOf("")
    }

    var statusMessage by remember {
        mutableStateOf("")
    }

    var showAddDialog by remember {
        mutableStateOf(false)
    }

    var showEditDialog by remember {
        mutableStateOf(false)
    }

    var editingRecipient by remember {
        mutableStateOf<Recipient?>(null)
    }

    var newName by remember {
        mutableStateOf("")
    }

    var newPhone by remember {
        mutableStateOf("")
    }

    var dataLoaded by remember {
        mutableStateOf(false)
    }

    val filePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri == null) {
                return@rememberLauncherForActivityResult
            }

            scope.launch {
                val imported =
                    if (
                        uri.toString().lowercase()
                            .contains(".csv")
                    ) {
                        readCsvFile(context, uri)
                    } else {
                        readExcelFile(context, uri)
                    }

                if (imported.isNotEmpty()) {
                    recipients = imported
                    selectedPhones = emptySet()

                    context.smsDataStore.edit { preferences ->
                        preferences[RECIPIENTS_KEY] =
                            recipientsToJson(imported)
                    }

                    statusMessage =
                        "تم استيراد ${imported.size} مستلم بنجاح"
                } else {
                    statusMessage =
                        "لم يتم العثور على مستلمين صالحين في الملف"
                }
            }
        }

    LaunchedEffect(Unit) {
        val preferences =
            context.smsDataStore.data.first()

        val savedRecipients =
            recipientsFromJson(
                preferences[RECIPIENTS_KEY]
            )

        val savedLogs =
            messageLogsFromJson(
                preferences[LOGS_KEY]
            )

        recipients =
            if (savedRecipients.isNotEmpty()) {
                savedRecipients
            } else {
                listOf(
                    Recipient("فرج جمال شلح", "0599000000"),
                    Recipient("مستلم تجريبي 2", "0599111111"),
                    Recipient("مستلم تجريبي 3", "0599222222")
                )
            }

        messageLogs = savedLogs

        simCards =
            getAvailableSimCards(context)

        selectedSimId =
            simCards.firstOrNull()?.subscriptionId

        dataLoaded = true
    }

    fun saveAll() {
        scope.launch {
            context.smsDataStore.edit { preferences ->
                preferences[RECIPIENTS_KEY] =
                    recipientsToJson(recipients)

                preferences[LOGS_KEY] =
                    messageLogsToJson(messageLogs)
            }
        }
    }

    if (!dataLoaded) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("جاري تحميل Faraj SMS...")
        }

        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Faraj SMS",
                            style =
                                MaterialTheme.typography.titleLarge
                        )

                        Text(
                            "نظام الرسائل الجماعية",
                            style =
                                MaterialTheme.typography.labelMedium
                        )
                    }
                }
            )
        },

        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentPage == 0,
                    onClick = {
                        currentPage = 0
                    },
                    icon = {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "الرئيسية"
                        )
                    },
                    label = {
                        Text("الرئيسية")
                    }
                )

                NavigationBarItem(
                    selected = currentPage == 1,
                    onClick = {
                        currentPage = 1
                    },
                    icon = {
                        Icon(
                            Icons.Default.People,
                            contentDescription = "المستلمون"
                        )
                    },
                    label = {
                        Text("المستلمون")
                    }
                )

                NavigationBarItem(
                    selected = currentPage == 2,
                    onClick = {
                        currentPage = 2
                    },
                    icon = {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "إرسال"
                        )
                    },
                    label = {
                        Text("إرسال")
                    }
                )

                NavigationBarItem(
                    selected = currentPage == 3,
                    onClick = {
                        currentPage = 3
                    },
                    icon = {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "السجل"
                        )
                    },
                    label = {
                        Text("السجل")
                    }
                )
            }
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp)
        ) {

            when (currentPage) {

                0 -> {
                    HomeScreen(
                        recipients = recipients,
                        selectedCount = selectedPhones.size,
                        sentCount = messageLogs.count {
                            it.status == "تم الإرسال"
                        },
                        onOpenRecipients = {
                            currentPage = 1
                        },
                        onOpenSend = {
                            currentPage = 2
                        }
                    )
                }

                1 -> {
                    RecipientsScreen(
                        recipients = recipients,
                        selectedPhones = selectedPhones,
                        onSelectionChanged = { phone, selected ->
                            selectedPhones =
                                if (selected) {
                                    selectedPhones + phone
                                } else {
                                    selectedPhones - phone
                                }
                        },
                        onSelectAll = {
                            selectedPhones =
                                recipients
                                    .map { it.phone }
                                    .toSet()
                        },
                        onDeselectAll = {
                            selectedPhones = emptySet()
                        },
                        onImport = {
                            filePicker.launch(
                                arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "application/vnd.ms-excel",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                )
                            )
                        },
                        onAdd = {
                            newName = ""
                            newPhone = ""
                            showAddDialog = true
                        },
                        onEdit = { recipient ->
                            editingRecipient = recipient
                            newName = recipient.name
                            newPhone = recipient.phone
                            showEditDialog = true
                        },
                        onDelete = { recipient ->
                            recipients =
                                recipients.filterNot {
                                    it.phone == recipient.phone
                                }

                            selectedPhones =
                                selectedPhones - recipient.phone

                            saveAll()
                        }
                    )
                }

                2 -> {
                    SendScreen(
                        recipients = recipients,
                        selectedPhones = selectedPhones,
                        simCards = simCards,
                        selectedSimId = selectedSimId,
                        messageText = messageText,
                        statusMessage = statusMessage,
                        onSimSelected = {
                            selectedSimId = it
                        },
                        onRefreshSims = {
                            simCards =
                                getAvailableSimCards(context)

                            if (
                                selectedSimId == null &&
                                simCards.isNotEmpty()
                            ) {
                                selectedSimId =
                                    simCards.first().subscriptionId
                            }

                            statusMessage =
                                if (simCards.isEmpty()) {
                                    "لم يتم العثور على شرائح SIM"
                                } else {
                                    "تم تحديث الشرائح"
                                }
                        },
                        onMessageChanged = {
                            messageText = it
                        },
                        onSend = {

                            val targets =
                                if (selectedPhones.isEmpty()) {
                                    recipients
                                } else {
                                    recipients.filter {
                                        selectedPhones.contains(
                                            it.phone
                                        )
                                    }
                                }

                            when {
                                messageText.isBlank() -> {
                                    statusMessage =
                                        "اكتب نص الرسالة أولاً"
                                }

                                targets.isEmpty() -> {
                                    statusMessage =
                                        "لا يوجد مستلمون"
                                }

                                selectedSimId == null -> {
                                    statusMessage =
                                        "اختر شريحة SIM أولاً"
                                }

                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.SEND_SMS
                                ) != PackageManager.PERMISSION_GRANTED -> {

                                    statusMessage =
                                        "صلاحية إرسال SMS غير مفعلة. فعّل الصلاحية من إعدادات الهاتف."
                                }

                                else -> {

                                    val simId =
                                        selectedSimId!!

                                    val selectedSim =
                                        simCards.find {
                                            it.subscriptionId == simId
                                        }

                                    val simName =
                                        selectedSim
                                            ?.displayName
                                            ?: "SIM"

                                    try {

                                        val smsManager =
                                            SmsManager
                                                .getSmsManagerForSubscriptionId(
                                                    simId
                                                )

                                        val parts =
                                            smsManager.divideMessage(
                                                messageText
                                            )

                                        val newLogs =
                                            mutableListOf<MessageLog>()

                                        var successCount = 0
                                        var failedCount = 0

                                        targets.forEach { recipient ->

                                            try {

                                                if (
                                                    parts.size == 1
                                                ) {

                                                    smsManager
                                                        .sendTextMessage(
                                                            recipient.phone,
                                                            null,
                                                            messageText,
                                                            null,
                                                            null
                                                        )

                                                } else {

                                                    smsManager
                                                        .sendMultipartTextMessage(
                                                            recipient.phone,
                                                            null,
                                                            parts,
                                                            null,
                                                            null
                                                        )
                                                }

                                                successCount++

                                                newLogs.add(
                                                    MessageLog(
                                                        id =
                                                            System.currentTimeMillis() +
                                                                newLogs.size,
                                                        recipientName =
                                                            recipient.name,
                                                        recipientPhone =
                                                            recipient.phone,
                                                        status =
                                                            "تم الإرسال",
                                                        time =
                                                            currentTime(),
                                                        simName =
                                                            simName,
                                                        message =
                                                            messageText
                                                    )
                                                )

                                            } catch (_: Exception) {

                                                failedCount++

                                                newLogs.add(
                                                    MessageLog(
                                                        id =
                                                            System.currentTimeMillis() +
                                                                newLogs.size,
                                                        recipientName =
                                                            recipient.name,
                                                        recipientPhone =
                                                            recipient.phone,
                                                        status =
                                                            "فشل الإرسال",
                                                        time =
                                                            currentTime(),
                                                        simName =
                                                            simName,
                                                        message =
                                                            messageText
                                                    )
                                                )
                                            }
                                        }

                                        messageLogs =
                                            newLogs + messageLogs

                                        saveAll()

                                        statusMessage =
                                            "تم الإرسال: $successCount | فشل: $failedCount\nالشريحة: $simName"

                                    } catch (exception: Exception) {

                                        statusMessage =
                                            "حدث خطأ أثناء الإرسال: ${exception.message}"
                                    }
                                }
                            }
                        }
                    )
                }

                3 -> {
                    HistoryScreen(
                        logs = messageLogs,
                        onClear = {
                            messageLogs = emptyList()
                            saveAll()
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
            },
            title = {
                Text("إضافة مستلم")
            },
            text = {

                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    OutlinedTextField(
                        value = newName,
                        onValueChange = {
                            newName = it
                        },
                        label = {
                            Text("الاسم")
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = {
                            newPhone = it
                        },
                        label = {
                            Text("رقم الهاتف")
                        },
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Phone
                            ),
                        modifier =
                            Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {

                        val phone =
                            normalizePhoneNumber(newPhone)

                        if (
                            newName.isNotBlank() &&
                            phone.isNotBlank()
                        ) {

                            recipients =
                                recipients + Recipient(
                                    newName.trim(),
                                    phone
                                )

                            saveAll()

                            showAddDialog = false
                        }
                    }
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                    }
                ) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showEditDialog) {

        AlertDialog(
            onDismissRequest = {
                showEditDialog = false
            },
            title = {
                Text("تعديل المستلم")
            },
            text = {

                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    OutlinedTextField(
                        value = newName,
                        onValueChange = {
                            newName = it
                        },
                        label = {
                            Text("الاسم")
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = {
                            newPhone = it
                        },
                        label = {
                            Text("رقم الهاتف")
                        },
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Phone
                            ),
                        modifier =
                            Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {

                        val old =
                            editingRecipient

                        if (old != null) {

                            val newPhoneNormalized =
                                normalizePhoneNumber(
                                    newPhone
                                )

                            recipients =
                                recipients.map {

                                    if (
                                        it.phone ==
                                        old.phone
                                    ) {
                                        Recipient(
                                            newName.trim(),
                                            newPhoneNormalized
                                        )
                                    } else {
                                        it
                                    }
                                }

                            if (
                                selectedPhones.contains(
                                    old.phone
                                )
                            ) {

                                selectedPhones =
                                    selectedPhones
                                        .filterNot {
                                            it == old.phone
                                        }
                                        .toSet() +
                                        newPhoneNormalized
                            }

                            saveAll()

                            showEditDialog = false
                            editingRecipient = null
                        }
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEditDialog = false
                        editingRecipient = null
                    }
                ) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun HomeScreen(
    recipients: List<Recipient>,
    selectedCount: Int,
    sentCount: Int,
    onOpenRecipients: () -> Unit,
    onOpenSend: () -> Unit
) {

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        item {

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {

                Column(
                    modifier =
                        Modifier.padding(20.dp)
                ) {

                    Text(
                        "مرحبا بك",
                        style =
                            MaterialTheme.typography.headlineSmall
                    )

                    Spacer(
                        modifier =
                            Modifier.height(6.dp)
                    )

                    Text(
                        "إدارة وإرسال الرسائل من هاتفك باستخدام شريحة SIM."
                    )
                }
            }
        }

        item {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                StatCard(
                    title = "المستلمون",
                    value = recipients.size.toString(),
                    modifier =
                        Modifier.weight(1f)
                )

                StatCard(
                    title = "المحددون",
                    value = selectedCount.toString(),
                    modifier =
                        Modifier.weight(1f)
                )

                StatCard(
                    title = "تم الإرسال",
                    value = sentCount.toString(),
                    modifier =
                        Modifier.weight(1f)
                )
            }
        }

        item {

            Button(
                onClick = onOpenRecipients,
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Icon(
                    Icons.Default.People,
                    contentDescription = null
                )

                Spacer(
                    modifier =
                        Modifier.padding(horizontal = 4.dp)
                )

                Text("إدارة المستلمين")
            }
        }

        item {

            Button(
                onClick = onOpenSend,
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Icon(
                    Icons.Default.Send,
                    contentDescription = null
                )

                Spacer(
                    modifier =
                        Modifier.padding(horizontal = 4.dp)
                )

                Text("إرسال رسالة")
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {

    Card(
        modifier = modifier
    ) {

        Column(
            modifier =
                Modifier.padding(12.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Text(
                value,
                style =
                    MaterialTheme.typography.headlineSmall
            )

            Text(title)
        }
    }
}

@Composable
fun RecipientsScreen(
    recipients: List<Recipient>,
    selectedPhones: Set<String>,
    onSelectionChanged: (
        String,
        Boolean
    ) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onImport: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Recipient) -> Unit,
    onDelete: (Recipient) -> Unit
) {

    var searchText by remember {
        mutableStateOf("")
    }

    val filteredRecipients =
        recipients.filter {

            it.name.contains(
                searchText,
                ignoreCase = true
            ) ||
            it.phone.contains(searchText)
        }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        OutlinedTextField(
            value = searchText,
            onValueChange = {
                searchText = it
            },
            modifier =
                Modifier.fillMaxWidth(),
            label = {
                Text("بحث بالاسم أو رقم الهاتف")
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null
                )
            },
            trailingIcon = {

                if (searchText.isNotEmpty()) {

                    IconButton(
                        onClick = {
                            searchText = ""
                        }
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "مسح"
                        )
                    }
                }
            }
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {

            OutlinedButton(
                onClick = onSelectAll,
                modifier =
                    Modifier.weight(1f)
            ) {
                Text("تحديد الكل")
            }

            OutlinedButton(
                onClick = onDeselectAll,
                modifier =
                    Modifier.weight(1f)
            ) {
                Text("إلغاء التحديد")
            }
        }

        Spacer(
            modifier =
                Modifier.height(6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {

            OutlinedButton(
                onClick = onImport,
                modifier =
                    Modifier.weight(1f)
            ) {

                Icon(
                    Icons.Default.FileOpen,
                    contentDescription = null
                )

                Spacer(
                    modifier =
                        Modifier.padding(horizontal = 3.dp)
                )

                Text("Excel / CSV")
            }

            Button(
                onClick = onAdd,
                modifier =
                    Modifier.weight(1f)
            ) {

                Icon(
                    Icons.Default.Add,
                    contentDescription = null
                )

                Spacer(
                    modifier =
                        Modifier.padding(horizontal = 3.dp)
                )

                Text("إضافة")
            }
        }

        Spacer(
            modifier =
                Modifier.height(6.dp)
        )

        Text(
            "المستلمون: ${recipients.size} | المحددون: ${selectedPhones.size}",
            style =
                MaterialTheme.typography.labelLarge
        )

        Spacer(
            modifier =
                Modifier.height(6.dp)
        )

        LazyColumn(
            modifier =
                Modifier.fillMaxSize()
        ) {

            items(
                filteredRecipients,
                key = {
                    it.phone
                }
            ) { recipient ->

                RecipientRow(
                    recipient = recipient,
                    selected =
                        selectedPhones.contains(
                            recipient.phone
                        ),
                    onSelected = {
                        onSelectionChanged(
                            recipient.phone,
                            it
                        )
                    },
                    onEdit = {
                        onEdit(recipient)
                    },
                    onDelete = {
                        onDelete(recipient)
                    }
                )
            }
        }
    }
}

@Composable
fun RecipientRow(
    recipient: Recipient,
    selected: Boolean,
    onSelected: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
    ) {

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Checkbox(
                checked = selected,
                onCheckedChange = onSelected
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    recipient.name,
                    style =
                        MaterialTheme.typography.titleMedium
                )

                Text(
                    recipient.phone,
                    style =
                        MaterialTheme.typography.bodyMedium
                )
            }

            IconButton(
                onClick = onEdit
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "تعديل"
                )
            }

            IconButton(
                onClick = onDelete
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "حذف"
                )
            }
        }
    }
}

@Composable
fun SendScreen(
    recipients: List<Recipient>,
    selectedPhones: Set<String>,
    simCards: List<SimCard>,
    selectedSimId: Int?,
    messageText: String,
    statusMessage: String,
    onSimSelected: (Int) -> Unit,
    onRefreshSims: () -> Unit,
    onMessageChanged: (String) -> Unit,
    onSend: () -> Unit
) {

    val targetCount =
        if (selectedPhones.isEmpty()) {
            recipients.size
        } else {
            recipients.count {
                selectedPhones.contains(it.phone)
            }
        }

    var simMenuExpanded by remember {
        mutableStateOf(false)
    }

    val selectedSim =
        simCards.find {
            it.subscriptionId == selectedSimId
        }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {

        Card(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Column(
                modifier =
                    Modifier.padding(14.dp)
            ) {

                Text(
                    "عدد المستلمين: $targetCount",
                    style =
                        MaterialTheme.typography.titleMedium
                )

                Text(
                    if (selectedPhones.isEmpty()) {
                        "سيتم الإرسال إلى جميع المستلمين"
                    } else {
                        "سيتم الإرسال إلى المستلمين المحددين فقط"
                    }
                )
            }
        }

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                OutlinedButton(
                    onClick = {
                        simMenuExpanded = true
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        selectedSim?.displayName
                            ?: "اختر شريحة SIM"
                    )
                }

                DropdownMenu(
                    expanded = simMenuExpanded,
                    onDismissRequest = {
                        simMenuExpanded = false
                    }
                ) {

                    if (simCards.isEmpty()) {

                        DropdownMenuItem(
                            text = {
                                Text(
                                    "لا توجد شرائح متاحة"
                                )
                            },
                            onClick = {
                                simMenuExpanded = false
                            }
                        )

                    } else {

                        simCards.forEach { sim ->

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${sim.displayName} - SIM ${sim.slotIndex + 1}"
                                    )
                                },
                                onClick = {
                                    onSimSelected(
                                        sim.subscriptionId
                                    )
                                    simMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onRefreshSims
            ) {

                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "تحديث الشرائح"
                )
            }
        }

        OutlinedTextField(
            value = messageText,
            onValueChange = onMessageChanged,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(190.dp),
            label = {
                Text("نص الرسالة")
            },
            placeholder = {
                Text("اكتب الرسالة هنا...")
            }
        )

        Text(
            "عدد الأحرف: ${messageText.length}",
            style =
                MaterialTheme.typography.labelMedium
        )

        Text(
            "الرسائل الطويلة مدعومة ويتم تقسيمها تلقائيًا إلى عدة أجزاء SMS.",
            style =
                MaterialTheme.typography.bodySmall
        )

        Button(
            onClick = onSend,
            modifier =
                Modifier.fillMaxWidth(),
            enabled =
                targetCount > 0 &&
                selectedSimId != null
        ) {

            Icon(
                Icons.Default.Send,
                contentDescription = null
            )

            Spacer(
                modifier =
                    Modifier.padding(horizontal = 4.dp)
            )

            Text("إرسال عبر SIM")
        }

        if (statusMessage.isNotBlank()) {

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    statusMessage,
                    modifier =
                        Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
fun HistoryScreen(
    logs: List<MessageLog>,
    onClear: () -> Unit
) {

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            Text(
                "سجل الرسائل",
                style =
                    MaterialTheme.typography.headlineSmall
            )

            if (logs.isNotEmpty()) {

                TextButton(
                    onClick = onClear
                ) {
                    Text("مسح السجل")
                }
            }
        }

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        if (logs.isEmpty()) {

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    "لا توجد رسائل في السجل حاليًا.",
                    modifier =
                        Modifier.padding(16.dp)
                )
            }

        } else {

            LazyColumn(
                modifier =
                    Modifier.fillMaxSize()
            ) {

                items(
                    logs,
                    key = {
                        it.id
                    }
                ) { log ->

                    MessageLogRow(log)
                }
            }
        }
    }
}

@Composable
fun MessageLogRow(
    log: MessageLog
) {

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
    ) {

        ListItem(
            headlineContent = {
                Text(log.recipientName)
            },
            supportingContent = {

                Column {

                    Text(log.recipientPhone)

                    Text(
                        log.message,
                        maxLines = 3
                    )

                    Text(
                        "${log.time} | ${log.simName}"
                    )
                }
            },
            trailingContent = {

                Text(
                    log.status,
                    style =
                        MaterialTheme.typography.labelMedium
                )
            }
        )
    }
}