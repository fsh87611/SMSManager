package com.faraj.smsapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

data class Recipient(val name: String, val phone: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmsManagerApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsManagerApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("") }
    var recipients by remember {
        mutableStateOf(
            listOf(
                Recipient("أحمد محمد", "0590000000"),
                Recipient("محمد علي", "0591111111"),
                Recipient("سارة محمود", "0592222222")
            )
        )
    }
    var sent by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS
        )
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("نظام SMS الجماعي") },
                    navigationIcon = {
                        IconButton(onClick = {}) { Icon(Icons.Default.Menu, null) }
                    },
                    actions = {
                        Text("SIM 1", modifier = Modifier.padding(end = 16.dp))
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    val labels = listOf("الرئيسية", "المستلمون", "إرسال", "السجل")
                    val icons = listOf(
                        Icons.Default.Home, Icons.Default.People,
                        Icons.Default.Send, Icons.Default.History
                    )
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(icons[index], null) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (selectedTab) {
                    0 -> HomeScreen(recipients.size, sent, failed) { selectedTab = 2 }
                    1 -> RecipientsScreen(recipients, { showAdd = true }) {
                        recipients = recipients.filterNot { it.phone == it.phone }
                    }
                    2 -> SendScreen(
                        message = message,
                        onMessageChange = { message = it },
                        recipientCount = recipients.size,
                        status = status,
                        onSend = {
                            if (message.isBlank()) {
                                status = "اكتب الرسالة أولاً"
                            } else {
                                var ok = 0
                                var bad = 0
                                try {
                                    val sms = SmsManager.getDefault()
                                    recipients.forEach {
                                        try {
                                            sms.sendTextMessage(it.phone, null, message, null, null)
                                            ok++
                                        } catch (_: Exception) { bad++ }
                                    }
                                    sent += ok
                                    failed += bad
                                    status = "تم بدء إرسال $ok رسالة"
                                } catch (_: Exception) {
                                    status = "تعذر الوصول إلى خدمة SMS"
                                }
                            }
                        }
                    )
                    3 -> HistoryScreen(sent, failed)
                }
            }
        }

        if (showAdd) {
            AlertDialog(
                onDismissRequest = { showAdd = false },
                title = { Text("إضافة مستلم") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(newName, { newName = it }, label = { Text("الاسم") })
                        OutlinedTextField(newPhone, { newPhone = it }, label = { Text("رقم الهاتف") })
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newName.isNotBlank() && newPhone.isNotBlank()) {
                            recipients = recipients + Recipient(newName, newPhone)
                        }
                        newName = ""; newPhone = ""; showAdd = false
                    }) { Text("إضافة") }
                },
                dismissButton = {
                    TextButton(onClick = { showAdd = false }) { Text("إلغاء") }
                }
            )
        }
    }
}

@Composable
fun HomeScreen(count: Int, sent: Int, failed: Int, onSend: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("مرحباً بك 👋", style = MaterialTheme.typography.headlineSmall)
            Text("إدارة وإرسال الرسائل من هاتفك باستخدام شريحة SIM.")
        }
        item { StatCard("إجمالي المستلمين", count.toString()) }
        item { StatCard("الرسائل المرسلة", sent.toString()) }
        item { StatCard("الفاشلة", failed.toString()) }
        item {
            Button(
                onClick = onSend,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Send, null)
                Spacer(Modifier.width(8.dp))
                Text("إرسال رسالة جديدة")
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
fun RecipientsScreen(
    recipients: List<Recipient>,
    onAdd: () -> Unit,
    onDelete: (Recipient) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("المستلمون", style = MaterialTheme.typography.headlineSmall)
            FilledTonalButton(onClick = onAdd) {
                Icon(Icons.Default.PersonAdd, null)
                Spacer(Modifier.width(6.dp))
                Text("إضافة")
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(recipients) { r ->
                ListItem(
                    headlineContent = { Text(r.name) },
                    supportingContent = { Text(r.phone) },
                    leadingContent = { Icon(Icons.Default.Person, null) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun SendScreen(
    message: String,
    onMessageChange: (String) -> Unit,
    recipientCount: Int,
    status: String,
    onSend: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("إرسال رسالة", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("عدد المستلمين: $recipientCount")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = message,
            onValueChange = onMessageChange,
            modifier = Modifier.fillMaxWidth().height(180.dp),
            label = { Text("نص الرسالة") },
            supportingText = { Text("${message.length} حرف") }
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSend,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Send, null)
            Spacer(Modifier.width(8.dp))
            Text("إرسال عبر SIM")
        }
        if (status.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(status)
        }
    }
}

@Composable
fun HistoryScreen(sent: Int, failed: Int) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("سجل الإرسال", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        ListItem(
            headlineContent = { Text("إحصائيات الإرسال") },
            supportingContent = { Text("ناجحة: $sent   •   فاشلة: $failed") },
            leadingContent = { Icon(Icons.Default.History, null) }
        )
    }
}
