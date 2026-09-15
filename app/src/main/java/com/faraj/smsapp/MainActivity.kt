package com.faraj.smsapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

import androidx.core.content.ContextCompat

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

import kotlinx.coroutines.flow.first

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject

import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


private val Context.smsDataStore by preferencesDataStore(
    name = "faraj_sms_data"
)

private val RECIPIENTS_KEY =
    stringPreferencesKey("recipients")

private val MESSAGE_LOGS_KEY =
    stringPreferencesKey("message_logs")


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


fun readCsvFile(
    context: Context,
    uri: Uri
): List<Recipient> {

    val recipients = mutableListOf<Recipient>()
    val usedPhones = mutableSetOf<String>()
    val usedPhones = mutableSetOf<String>()

    try {
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            reader.readLines().drop(1).forEach { line ->
                if (line.isBlank()) return@forEach

                val columns = line.split(",")

                if (columns.size >= 2) {
                    val name = columns[0].trim().trim('"')
                    val phone = columns[1].trim().trim('"')

                    if (name.isNotBlank() && phone.isNotBlank() && usedPhones.add(phone)) {
                        recipients.add(Recipient(name = name, phone = phone))
                    }
                }
            }
        }
    } catch (_: Exception) {
    }

    return recipients
}


fun readExcelFile(
    context: Context,
    uri: Uri
): List<Recipient> {

    val recipients = mutableListOf<Recipient>()
    val usedPhones = mutableSetOf<String>()

    try {

        val inputStream: InputStream? =
            context.contentResolver.openInputStream(uri)

        if (inputStream != null) {

            val workbook = XSSFWorkbook(inputStream)

            val sheet = workbook.getSheetAt(0)

            for (row in sheet) {

                if (row.rowNum == 0) {
                    continue
                }

                val name =
                    row.getCell(0)?.toString()?.trim() ?: ""

                val phone =
                    row.getCell(1)?.toString()?.trim() ?: ""

                if (
                    name.isNotBlank() && usedPhones.add(phone) &&
                    phone.isNotBlank()
                ) {

                    recipients.add(
                        Recipient(
                            name = name,
                            phone = phone
                        )
                    )
                }
            }

            workbook.close()
            inputStream.close()
        }

    } catch (_: Exception) {
    }

    return recipients
}


fun getAvailableSimCards(
    context: Context
): List<SimCard> {

    val result = mutableListOf<SimCard>()

    try {

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return result
        }

        val subscriptionManager =
            context.getSystemService(
                Context.TELEPHONY_SUBSCRIPTION_SERVICE
            ) as SubscriptionManager

        val subscriptions =
            subscriptionManager.activeSubscriptionInfoList

        if (subscriptions != null) {

            subscriptions.forEach { info: SubscriptionInfo ->

                val slotIndex =
                    info.simSlotIndex

                val simNumber =
                    if (slotIndex >= 0) {
                        slotIndex + 1
                    } else {
                        result.size + 1
                    }

                val carrierName =
                    info.carrierName
                        ?.toString()
                        ?.trim()
                        ?: ""

                val displayName =
                    if (carrierName.isNotBlank()) {
                        "SIM $simNumber - $carrierName"
                    } else {
                        "SIM $simNumber"
                    }

                result.add(
                    SimCard(
                        subscriptionId =
                            info.subscriptionId,
                        displayName =
                            displayName,
                        slotIndex =
                            slotIndex
                    )
                )
            }
        }

    } catch (_: Exception) {
    }

    return result.sortedBy {
        it.slotIndex
    }
}


fun currentTime(): String {

    val formatter =
        SimpleDateFormat(
            "yyyy/MM/dd HH:mm:ss",
            Locale.getDefault()
        )

    return formatter.format(
        Date()
    )
}


fun recipientsToJson(
    recipients: List<Recipient>
): String {

    val array = JSONArray()

    recipients.forEach { recipient ->

        val objectItem = JSONObject()

        objectItem.put(
            "name",
            recipient.name
        )

        objectItem.put(
            "phone",
            recipient.phone
        )

        array.put(objectItem)
    }

    return array.toString()
}


fun recipientsFromJson(
    json: String
): List<Recipient> {

    val result = mutableListOf<Recipient>()

    try {

        val array =
            JSONArray(json)

        for (index in 0 until array.length()) {

            val item =
                array.getJSONObject(index)

            result.add(
                Recipient(
                    name =
                        item.optString("name"),
                    phone =
                        item.optString("phone")
                )
            )
        }

    } catch (_: Exception) {
    }

    return result
}


fun messageLogsToJson(
    logs: List<MessageLog>
): String {

    val array = JSONArray()

    logs.forEach { log ->

        val objectItem = JSONObject()

        objectItem.put(
            "id",
            log.id
        )

        objectItem.put(
            "recipientName",
            log.recipientName
        )

        objectItem.put(
            "recipientPhone",
            log.recipientPhone
        )

        objectItem.put(
            "status",
            log.status
        )

        objectItem.put(
            "time",
            log.time
        )

        objectItem.put(
            "simName",
            log.simName
        )

        objectItem.put(
            "message",
            log.message
        )

        array.put(objectItem)
    }

    return array.toString()
}


fun messageLogsFromJson(
    json: String
): List<MessageLog> {

    val result = mutableListOf<MessageLog>()

    try {

        val array =
            JSONArray(json)

        for (index in 0 until array.length()) {

            val item =
                array.getJSONObject(index)

            result.add(
                MessageLog(
                    id =
                        item.optLong("id"),

                    recipientName =
                        item.optString("recipientName"),

                    recipientPhone =
                        item.optString("recipientPhone"),

                    status =
                        item.optString("status"),

                    time =
                        item.optString("time"),

                    simName =
                        item.optString("simName"),

                    message =
                        item.optString("message")
                )
            )
        }

    } catch (_: Exception) {
    }

    return result
}


class MainActivity : ComponentActivity() {

    private val requestSmsPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

    private val requestPhoneStatePermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            requestSmsPermission.launch(
                Manifest.permission.SEND_SMS
            )
        }


        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            requestPhoneStatePermission.launch(
                Manifest.permission.READ_PHONE_STATE
            )
        }


        setContent {
            SmsManagerApp()
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsManagerApp() {

    val context =
        androidx.compose.ui.platform.LocalContext.current


    var currentPage by remember {
        mutableStateOf(0)
    }


    val defaultRecipients =
        listOf(

            Recipient(
                "أحمد محمد",
                "0590000000"
            ),

            Recipient(
                "محمد علي",
                "0591111111"
            ),

            Recipient(
                "سارة محمود",
                "0592222222"
            )
        )


    var recipients by remember {

        mutableStateOf(
            defaultRecipients
        )
    }


    var messageLogs by remember {
        mutableStateOf(
            listOf<MessageLog>()
        )
    }


    var dataLoaded by remember {
        mutableStateOf(false)
    }


    var selectedPhones by remember {
        mutableStateOf(setOf<String>())
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


    var editName by remember {
        mutableStateOf("")
    }


    var editPhone by remember {
        mutableStateOf("")
    }


    var messageText by remember {
        mutableStateOf("")
    }


    var statusMessage by remember {
        mutableStateOf("")
    }


    var simCards by remember {

        mutableStateOf(
            getAvailableSimCards(context)
        )
    }


    var selectedSimId by remember {
        mutableStateOf<Int?>(null)
    }


    /*
     * تحميل البيانات المحفوظة عند تشغيل التطبيق
     */
    LaunchedEffect(Unit) {

        try {

            val preferences =
                context.smsDataStore.data.first()

            val savedRecipients =
                preferences[RECIPIENTS_KEY]

            val savedLogs =
                preferences[MESSAGE_LOGS_KEY]


            if (savedRecipients != null) {

                recipients =
                    recipientsFromJson(
                        savedRecipients
                    )
            }


            if (savedLogs != null) {

                messageLogs =
                    messageLogsFromJson(
                        savedLogs
                    )
            }

        } catch (_: Exception) {
        }

        dataLoaded = true
    }


    /*
     * حفظ المستلمين تلقائياً عند أي تغيير
     */
    LaunchedEffect(
        recipients,
        dataLoaded
    ) {

        if (dataLoaded) {

            try {

                context.smsDataStore.edit { preferences ->

                    preferences[RECIPIENTS_KEY] =
                        recipientsToJson(
                            recipients
                        )
                }

            } catch (_: Exception) {
            }
        }
    }


    /*
     * حفظ سجل الرسائل تلقائياً عند أي تغيير
     */
    LaunchedEffect(
        messageLogs,
        dataLoaded
    ) {

        if (dataLoaded) {

            try {

                context.smsDataStore.edit { preferences ->

                    preferences[MESSAGE_LOGS_KEY] =
                        messageLogsToJson(
                            messageLogs
                        )
                }

            } catch (_: Exception) {
            }
        }
    }


    LaunchedEffect(Unit) {

        val availableSims =
            getAvailableSimCards(context)

        simCards =
            availableSims

        if (
            selectedSimId == null &&
            availableSims.isNotEmpty()
        ) {

            selectedSimId =
                availableSims
                    .first()
                    .subscriptionId
        }
    }


    val excelPicker =
        rememberLauncherForActivityResult(

            contract =
                ActivityResultContracts.OpenDocument()

        ) { uri: Uri? ->

            if (uri != null) {

                val mimeType = context.contentResolver.getType(uri) ?: ""

                val importedRecipients =
                    if (mimeType == "text/csv" || mimeType == "text/comma-separated-values") {
                        readCsvFile(context, uri)
                    } else {
                        readExcelFile(context, uri)
                    }
                recipients =
                    importedRecipients

                selectedPhones =
                    emptySet()

                statusMessage =
                    "تم استيراد ${importedRecipients.size} مستلم وحفظ البيانات تلقائياً"
            }
        }


    Scaffold(

        topBar = {

            androidx.compose.material3.TopAppBar(

                title = {
                    Text("Faraj SMS")
                }
            )
        },


        bottomBar = {

            NavigationBar {

                NavigationBarItem(

                    selected =
                        currentPage == 0,

                    onClick = {
                        currentPage = 0
                    },

                    icon = {

                        Icon(
                            Icons.Default.Home,
                            contentDescription =
                                "الرئيسية"
                        )
                    },

                    label = {
                        Text("الرئيسية")
                    }
                )


                NavigationBarItem(

                    selected =
                        currentPage == 1,

                    onClick = {
                        currentPage = 1
                    },

                    icon = {

                        Icon(
                            Icons.Default.People,
                            contentDescription =
                                "المستلمون"
                        )
                    },

                    label = {
                        Text("المستلمون")
                    }
                )


                NavigationBarItem(

                    selected =
                        currentPage == 2,

                    onClick = {
                        currentPage = 2
                    },

                    icon = {

                        Icon(
                            Icons.Default.Send,
                            contentDescription =
                                "إرسال"
                        )
                    },

                    label = {
                        Text("إرسال")
                    }
                )


                NavigationBarItem(

                    selected =
                        currentPage == 3,

                    onClick = {
                        currentPage = 3
                    },

                    icon = {

                        Icon(
                            Icons.Default.History,
                            contentDescription =
                                "السجل"
                        )
                    },

                    label = {
                        Text("السجل")
                    }
                )
            }
        }

    ) { paddingValues ->


        Surface(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
        ) {

            when (currentPage) {

                0 -> {

                    HomeScreen(

                        recipientsCount =
                            recipients.size,

                        selectedCount =
                            selectedPhones.size,

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

                        recipients =
                            recipients,

                        selectedPhones =
                            selectedPhones,


                        onToggleSelection = { phone ->

                            selectedPhones =

                                if (
                                    selectedPhones.contains(
                                        phone
                                    )
                                ) {

                                    selectedPhones - phone

                                } else {

                                    selectedPhones + phone
                                }
                        },


                        onSelectAll = {

                            selectedPhones =

                                selectedPhones +

                                    recipients
                                        .map {
                                            it.phone
                                        }
                                        .toSet()
                        },


                        onDeselectAll = {

                            selectedPhones =
                                emptySet()
                        },


                        onDelete = { recipient ->

                            recipients =
                                recipients.filterNot {

                                    it.phone ==
                                        recipient.phone
                                }

                            selectedPhones =
                                selectedPhones -
                                    recipient.phone
                        },


                        onEdit = { recipient ->

                            editingRecipient =
                                recipient

                            editName =
                                recipient.name

                            editPhone =
                                recipient.phone

                            showEditDialog =
                                true
                        },


                        onAdd = {

                            newName = ""
                            newPhone = ""

                            showAddDialog =
                                true
                        },


                        onImportExcel = {

                            excelPicker.launch(

                                arrayOf(

                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",

                                    "application/vnd.ms-excel",
                                    "text/csv",
                                    "text/comma-separated-values",
                                )
                            )
                        }
                    )
                }


                2 -> {

                    SendScreen(

                        recipientCount =

                            if (
                                selectedPhones.isNotEmpty()
                            ) {

                                selectedPhones.size

                            } else {

                                recipients.size
                            },


                        messageText =
                            messageText,


                        onMessageChange = {
                            messageText = it
                        },


                        statusMessage =
                            statusMessage,


                        simCards =
                            simCards,


                        selectedSimId =
                            selectedSimId,


                        onRefreshSims = {

                            val refreshed =
                                getAvailableSimCards(
                                    context
                                )

                            simCards =
                                refreshed

                            if (
                                refreshed.none {

                                    it.subscriptionId ==
                                        selectedSimId
                                }
                            ) {

                                selectedSimId =
                                    refreshed
                                        .firstOrNull()
                                        ?.subscriptionId
                            }
                        },


                        onSimSelected = { simId ->

                            selectedSimId =
                                simId

                            val selected =
                                simCards.find {

                                    it.subscriptionId ==
                                        simId
                                }

                            statusMessage =

                                if (selected != null) {

                                    "تم اختيار ${selected.displayName}"

                                } else {

                                    ""
                                }
                        },


                        onSend = {

                            val targets =

                                if (
                                    selectedPhones.isNotEmpty()
                                ) {

                                    recipients.filter {

                                        selectedPhones.contains(
                                            it.phone
                                        )
                                    }

                                } else {

                                    recipients
                                }


                            if (
                                messageText.isBlank()
                            ) {

                                statusMessage =
                                    "يرجى كتابة الرسالة أولاً"

                            } else if (
                                targets.isEmpty()
                            ) {

                                statusMessage =
                                    "لا يوجد مستلمون"

                            } else if (
                                simCards.isEmpty()
                            ) {

                                statusMessage =
                                    "لم يتم العثور على شريحة SIM فعالة"

                            } else if (
                                selectedSimId == null
                            ) {

                                statusMessage =
                                    "يرجى اختيار الشريحة المستخدمة"

                            } else {


                                try {

                                    if (

                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.SEND_SMS
                                        ) !=
                                        PackageManager.PERMISSION_GRANTED

                                    ) {

                                        statusMessage =
                                            "يرجى السماح للتطبيق بإرسال الرسائل"

                                    } else {

                                        val smsManager =

                                            SmsManager
                                                .getSmsManagerForSubscriptionId(
                                                    selectedSimId!!
                                                )


                                        val selectedSim =
                                            simCards.find {

                                                it.subscriptionId ==
                                                    selectedSimId
                                            }


                                        val simName =
                                            selectedSim
                                                ?.displayName
                                                ?: "الشريحة المحددة"


                                        var successCount =
                                            0

                                        var failedCount =
                                            0


                                        val newLogs =
                                            mutableListOf<MessageLog>()


                                        targets.forEach { recipient ->

                                            try {

                                                val parts =
                                                    smsManager.divideMessage(
                                                        messageText
                                                    )


                                                smsManager.sendMultipartTextMessage(

                                                    recipient.phone,

                                                    null,

                                                    parts,

                                                    null,

                                                    null
                                                )


                                                successCount++


                                                newLogs.add(

                                                    MessageLog(

                                                        id =
                                                            System.nanoTime(),

                                                        recipientName =
                                                            recipient.name,

                                                        recipientPhone =
                                                            recipient.phone,

                                                        status =
                                                            "نجاح",

                                                        time =
                                                            currentTime(),

                                                        simName =
                                                            simName,

                                                        message =
                                                            messageText
                                                    )
                                                )

                                            } catch (
                                                _: Exception
                                            ) {

                                                failedCount++


                                                newLogs.add(

                                                    MessageLog(

                                                        id =
                                                            System.nanoTime(),

                                                        recipientName =
                                                            recipient.name,

                                                        recipientPhone =
                                                            recipient.phone,

                                                        status =
                                                            "فشل",

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


                                        statusMessage =

                                            "تم الإرسال: $successCount | فشل: $failedCount\nالشريحة: $simName"
                                    }

                                } catch (
                                    _: Exception
                                ) {

                                    statusMessage =
                                        "حدث خطأ أثناء استخدام الشريحة المحددة"
                                }
                            }
                        }
                    )
                }


                3 -> {

                    HistoryScreen(

                        logs =
                            messageLogs,

                        onClearHistory = {

                            messageLogs =
                                emptyList()
                        }
                    )
                }
            }
        }
    }


    if (showAddDialog) {

        AlertDialog(

            onDismissRequest = {

                showAddDialog =
                    false
            },


            title = {
                Text("إضافة مستلم")
            },


            text = {

                Column {

                    OutlinedTextField(

                        value =
                            newName,

                        onValueChange = {
                            newName = it
                        },

                        label = {
                            Text("اسم المستلم")
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        singleLine = true
                    )


                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )


                    OutlinedTextField(

                        value =
                            newPhone,

                        onValueChange = {
                            newPhone = it
                        },

                        label = {
                            Text("رقم الهاتف")
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        singleLine = true,

                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Phone
                            )
                    )
                }
            },


            confirmButton = {

                TextButton(

                    onClick = {

                        if (
                            newName.isNotBlank() &&
                            newPhone.isNotBlank()
                        ) {

                            recipients =

                                recipients +

                                    Recipient(

                                        name =
                                            newName.trim(),

                                        phone =
                                            newPhone.trim()
                                    )

                            newName = ""
                            newPhone = ""

                            showAddDialog =
                                false
                        }
                    }
                ) {

                    Text("إضافة")
                }
            },


            dismissButton = {

                TextButton(

                    onClick = {
                        showAddDialog =
                            false
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

                showEditDialog =
                    false

                editingRecipient =
                    null
            },


            title = {
                Text("تعديل بيانات المستلم")
            },


            text = {

                Column {

                    OutlinedTextField(

                        value =
                            editName,

                        onValueChange = {
                            editName = it
                        },

                        label = {
                            Text("اسم المستلم")
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        singleLine = true
                    )


                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )


                    OutlinedTextField(

                        value =
                            editPhone,

                        onValueChange = {
                            editPhone = it
                        },

                        label = {
                            Text("رقم الهاتف")
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        singleLine = true,

                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Phone
                            )
                    )
                }
            },


            confirmButton = {

                TextButton(

                    onClick = {

                        val oldRecipient =
                            editingRecipient


                        if (

                            oldRecipient != null &&

                            editName.isNotBlank() &&

                            editPhone.isNotBlank()

                        ) {

                            val oldPhone =
                                oldRecipient.phone


                            val updatedRecipient =
                                Recipient(

                                    name =
                                        editName.trim(),

                                    phone =
                                        editPhone.trim()
                                )


                            recipients =

                                recipients.map {

                                    if (
                                        it.phone ==
                                            oldPhone
                                    ) {

                                        updatedRecipient

                                    } else {

                                        it
                                    }
                                }


                            if (
                                selectedPhones.contains(
                                    oldPhone
                                )
                            ) {

                                selectedPhones =

                                    selectedPhones

                                        .filterNot {
                                            it == oldPhone
                                        }

                                        .toSet() +

                                    editPhone.trim()
                            }


                            showEditDialog =
                                false

                            editingRecipient =
                                null
                        }
                    }
                ) {

                    Text("حفظ")
                }
            },


            dismissButton = {

                TextButton(

                    onClick = {

                        showEditDialog =
                            false

                        editingRecipient =
                            null
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

    recipientsCount: Int,

    selectedCount: Int,

    onOpenRecipients: () -> Unit,

    onOpenSend: () -> Unit

) {

    Column(

        modifier =

            Modifier
                .fillMaxSize()
                .padding(20.dp),

        verticalArrangement =
            Arrangement.spacedBy(16.dp)
    ) {

        Text(

            text =
                "مرحباً بك في Faraj SMS",

            style =
                MaterialTheme.typography.headlineSmall
        )


        Card(

            modifier =
                Modifier.fillMaxWidth(),

            shape =
                RoundedCornerShape(16.dp)
        ) {

            Column(

                modifier =
                    Modifier.padding(20.dp),

                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                Text(

                    text =
                        "إدارة الرسائل النصية",

                    style =
                        MaterialTheme.typography.titleLarge
                )


                Text(
                    text =
                        "عدد المستلمين: $recipientsCount"
                )


                Text(
                    text =
                        "المحدد حالياً: $selectedCount"
                )
            }
        }


        Button(

            onClick =
                onOpenRecipients,

            modifier =
                Modifier.fillMaxWidth()
        ) {

            Icon(

                Icons.Default.People,

                contentDescription =
                    null
            )


            Spacer(
                modifier =
                    Modifier.padding(4.dp)
            )


            Text(
                "إدارة المستلمين"
            )
        }


        Button(

            onClick =
                onOpenSend,

            modifier =
                Modifier.fillMaxWidth()
        ) {

            Icon(

                Icons.Default.Send,

                contentDescription =
                    null
            )


            Spacer(
                modifier =
                    Modifier.padding(4.dp)
            )


            Text(
                "إرسال رسالة"
            )
        }
    }
}


@Composable
fun RecipientsScreen(

    recipients: List<Recipient>,

    selectedPhones: Set<String>,

    onToggleSelection: (String) -> Unit,

    onSelectAll: () -> Unit,

    onDeselectAll: () -> Unit,

    onDelete: (Recipient) -> Unit,

    onEdit: (Recipient) -> Unit,

    onAdd: () -> Unit,

    onImportExcel: () -> Unit

) {

    var searchQuery by remember {
        mutableStateOf("")
    }


    val filteredRecipients =

        remember(
            recipients,
            searchQuery
        ) {

            if (
                searchQuery.isBlank()
            ) {

                recipients

            } else {

                recipients.filter {

                    it.name.contains(
                        searchQuery,
                        ignoreCase = true
                    ) ||

                    it.phone.contains(
                        searchQuery,
                        ignoreCase = true
                    )
                }
            }
        }


    val allVisibleSelected =

        filteredRecipients.isNotEmpty() &&

            filteredRecipients.all {

                selectedPhones.contains(
                    it.phone
                )
            }


    Column(

        modifier =

            Modifier
                .fillMaxSize()
                .padding(16.dp)
    ) {

        OutlinedTextField(

            value =
                searchQuery,

            onValueChange = {
                searchQuery = it
            },

            label = {

                Text(
                    "البحث عن اسم أو رقم"
                )
            },


            leadingIcon = {

                Icon(

                    Icons.Default.Search,

                    contentDescription =
                        null
                )
            },


            trailingIcon = {

                if (
                    searchQuery.isNotEmpty()
                ) {

                    IconButton(

                        onClick = {
                            searchQuery = ""
                        }

                    ) {

                        Icon(

                            Icons.Default.Clear,

                            contentDescription =
                                "مسح البحث"
                        )
                    }
                }
            },


            modifier =
                Modifier.fillMaxWidth(),

            singleLine = true
        )


        Spacer(
            modifier =
                Modifier.height(12.dp)
        )


        Card(

            modifier =
                Modifier.fillMaxWidth()
        ) {

            Column(

                modifier =
                    Modifier.padding(12.dp)
            ) {

                Row(

                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.SpaceBetween,

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        text =
                            "المحدد: ${selectedPhones.size}"
                    )


                    Text(
                        text =
                            "إجمالي: ${recipients.size}"
                    )
                }


                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )


                Row(

                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Button(

                        onClick = {

                            if (
                                allVisibleSelected
                            ) {

                                onDeselectAll()

                            } else {

                                onSelectAll()
                            }
                        },

                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(

                            if (
                                allVisibleSelected
                            ) {

                                "إلغاء تحديد الكل"

                            } else {

                                "تحديد الكل"
                            }
                        )
                    }


                    OutlinedButton(

                        onClick =
                            onDeselectAll,

                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            "إلغاء الكل"
                        )
                    }
                }
            }
        }


        Spacer(
            modifier =
                Modifier.height(12.dp)
        )


        Row(

            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            Button(

                onClick =
                    onImportExcel,

                modifier =
                    Modifier.weight(1f)
            ) {

                Icon(

                    Icons.Default.FileOpen,

                    contentDescription =
                        null
                )


                Spacer(
                    modifier =
                        Modifier.padding(3.dp)
                )


                Text("Excel / CSV")
            }


            Button(

                onClick =
                    onAdd,

                modifier =
                    Modifier.weight(1f)
            ) {

                Icon(

                    Icons.Default.Add,

                    contentDescription =
                        null
                )


                Spacer(
                    modifier =
                        Modifier.padding(3.dp)
                )


                Text("إضافة")
            }
        }


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        Text(

            text =
                "نتائج البحث: ${filteredRecipients.size}",

            style =
                MaterialTheme.typography.bodyMedium
        )


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        LazyColumn(

            modifier =
                Modifier.fillMaxSize()

        ) {

            items(

                items =
                    filteredRecipients,

                key = {
                    it.phone
                }

            ) { recipient ->

                ListItem(

                    headlineContent = {

                        Text(
                            recipient.name
                        )
                    },


                    supportingContent = {

                        Text(
                            recipient.phone
                        )
                    },


                    leadingContent = {

                        Checkbox(

                            checked =
                                selectedPhones.contains(
                                    recipient.phone
                                ),

                            onCheckedChange = {

                                onToggleSelection(
                                    recipient.phone
                                )
                            }
                        )
                    },


                    trailingContent = {

                        Row {

                            IconButton(

                                onClick = {
                                    onEdit(recipient)
                                }

                            ) {

                                Icon(

                                    Icons.Default.Edit,

                                    contentDescription =
                                        "تعديل"
                                )
                            }


                            IconButton(

                                onClick = {
                                    onDelete(recipient)
                                }

                            ) {

                                Icon(

                                    Icons.Default.Delete,

                                    contentDescription =
                                        "حذف"
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}


@Composable
fun SendScreen(

    recipientCount: Int,

    messageText: String,

    onMessageChange: (String) -> Unit,

    statusMessage: String,

    simCards: List<SimCard>,

    selectedSimId: Int?,

    onRefreshSims: () -> Unit,

    onSimSelected: (Int) -> Unit,

    onSend: () -> Unit

) {

    var simMenuExpanded by remember {
        mutableStateOf(false)
    }


    val selectedSim =

        simCards.find {

            it.subscriptionId ==
                selectedSimId
        }


    Column(

        modifier =

            Modifier
                .fillMaxSize()
                .padding(20.dp),

        verticalArrangement =
            Arrangement.spacedBy(16.dp)
    ) {

        Card(

            modifier =
                Modifier.fillMaxWidth()
        ) {

            Column(

                modifier =
                    Modifier.padding(16.dp)
            ) {

                Text(

                    text =
                        "إرسال رسالة",

                    style =
                        MaterialTheme.typography.titleLarge
                )


                Spacer(
                    modifier =
                        Modifier.height(6.dp)
                )


                Text(

                    text =
                        "عدد المستلمين: $recipientCount"
                )


                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )


                Text(

                    text =
                        "اختيار الشريحة",

                    style =
                        MaterialTheme.typography.titleMedium
                )


                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )


                if (
                    simCards.isEmpty()
                ) {

                    Card(

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Column(

                            modifier =
                                Modifier.padding(12.dp)
                        ) {

                            Text(

                                text =
                                    "لم يتم العثور على شرائح SIM فعالة."
                            )


                            Spacer(
                                modifier =
                                    Modifier.height(8.dp)
                            )


                            OutlinedButton(

                                onClick =
                                    onRefreshSims

                            ) {

                                Text(
                                    "تحديث الشرائح"
                                )
                            }
                        }
                    }

                } else {

                    Column {

                        OutlinedButton(

                            onClick = {

                                simMenuExpanded =
                                    true
                            },

                            modifier =
                                Modifier.fillMaxWidth()

                        ) {

                            Text(

                                selectedSim
                                    ?.displayName
                                    ?: "اختر الشريحة"
                            )
                        }


                        DropdownMenu(

                            expanded =
                                simMenuExpanded,

                            onDismissRequest = {

                                simMenuExpanded =
                                    false
                            }

                        ) {

                            simCards.forEach { sim ->

                                DropdownMenuItem(

                                    text = {

                                        Text(
                                            sim.displayName
                                        )
                                    },

                                    onClick = {

                                        onSimSelected(
                                            sim.subscriptionId
                                        )

                                        simMenuExpanded =
                                            false
                                    }
                                )
                            }
                        }


                        Spacer(
                            modifier =
                                Modifier.height(6.dp)
                        )


                        OutlinedButton(

                            onClick =
                                onRefreshSims,

                            modifier =
                                Modifier.fillMaxWidth()

                        ) {

                            Text(
                                "تحديث الشرائح"
                            )
                        }
                    }
                }
            }
        }


        OutlinedTextField(

            value =
                messageText,

            onValueChange =
                onMessageChange,

            label = {

                Text(
                    "نص الرسالة"
                )
            },

            modifier =

                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
        )


        Text(

            text =
                "الرسائل الطويلة مدعومة وسيتم تقسيمها تلقائياً إلى عدة أجزاء.",

            style =
                MaterialTheme.typography.bodySmall
        )


        Button(

            onClick =
                onSend,

            modifier =
                Modifier.fillMaxWidth()

        ) {

            Icon(

                Icons.Default.Send,

                contentDescription =
                    null
            )


            Spacer(
                modifier =
                    Modifier.padding(4.dp)
            )


            Text(
                "إرسال الآن"
            )
        }


        if (
            statusMessage.isNotBlank()
        ) {

            Card(

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(

                    text =
                        statusMessage,

                    modifier =
                        Modifier.padding(16.dp)
                )
            }
        }
    }
}


@Composable
fun HistoryScreen(

    logs: List<MessageLog>,

    onClearHistory: () -> Unit

) {

    Column(

        modifier =

            Modifier
                .fillMaxSize()
                .padding(16.dp)
    ) {

        Row(

            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.SpaceBetween,

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(

                text =
                    "سجل الرسائل",

                style =
                    MaterialTheme.typography.headlineSmall
            )


            if (logs.isNotEmpty()) {

                TextButton(

                    onClick =
                        onClearHistory

                ) {

                    Text(
                        "مسح السجل"
                    )
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

                    text =
                        "لا توجد عمليات إرسال مسجلة حالياً.",

                    modifier =
                        Modifier.padding(20.dp)
                )
            }

        } else {

            Text(

                text =
                    "إجمالي العمليات: ${logs.size}",

                style =
                    MaterialTheme.typography.bodyMedium
            )


            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )


            LazyColumn(

                modifier =
                    Modifier.fillMaxSize(),

                verticalArrangement =
                    Arrangement.spacedBy(8.dp)

            ) {

                items(

                    items =
                        logs,

                    key = {
                        it.id
                    }

                ) { log ->

                    Card(

                        modifier =
                            Modifier.fillMaxWidth(),

                        shape =
                            RoundedCornerShape(12.dp)
                    ) {

                        Column(

                            modifier =
                                Modifier.padding(14.dp),

                            verticalArrangement =
                                Arrangement.spacedBy(5.dp)
                        ) {

                            Row(

                                modifier =
                                    Modifier.fillMaxWidth(),

                                horizontalArrangement =
                                    Arrangement.SpaceBetween
                            ) {

                                Text(

                                    text =
                                        log.recipientName,

                                    style =
                                        MaterialTheme.typography.titleMedium
                                )


                                Text(
                                    text =
                                        log.status
                                )
                            }


                            Text(
                                text =
                                    "الهاتف: ${log.recipientPhone}"
                            )


                            Text(
                                text =
                                    "الوقت: ${log.time}"
                            )


                            Text(
                                text =
                                    "الشريحة: ${log.simName}"
                            )


                            Text(
                                text =
                                    "الرسالة: ${log.message}"
                            )
                        }
                    }
                }
            }
        }
    }
}