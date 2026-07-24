package com.goldsonhwy.yellowplayer.ui.screens.directory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.goldsonhwy.yellowplayer.data.model.SambaServer
import com.goldsonhwy.yellowplayer.ui.navigation.Routes
import com.goldsonhwy.yellowplayer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SambaConfigScreen(
    navController: NavController,
    viewModel: SambaConfigViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<SambaServer?>(null) }
    var showDiscoveredDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.discoveredHosts) {
        if (uiState.discoveredHosts.isNotEmpty()) showDiscoveredDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Samba 共享", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "返回", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "添加服务器", tint = Yellow500)
                    }
                }
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            Card(
                onClick = { viewModel.discoverServers() },
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.isDiscovering) {
                        Surface(shape = MaterialTheme.shapes.small, color = Yellow500.copy(alpha = 0.15f), modifier = Modifier.size(24.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("…", color = Yellow500, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Icon(Icons.Default.Search, null, tint = Yellow500, modifier = Modifier.size(24.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.isDiscovering) "正在搜索局域网设备…" else "自动搜索局域网设备",
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (uiState.discoveredHosts.isNotEmpty()) "发现 ${uiState.discoveredHosts.size} 台设备" else "扫描当前 WiFi 子网的 445 端口",
                            color = if (uiState.discoveredHosts.isNotEmpty()) Yellow500 else TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    if (!uiState.isDiscovering) Icon(Icons.Default.ChevronRight, null, tint = TextHint, modifier = Modifier.size(20.dp))
                }
            }

            if (uiState.isDiscovering) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(Yellow500.copy(alpha = 0.45f)))
            }

            if (uiState.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(uiState.error ?: "", color = LikeRed, fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))

            if (uiState.servers.isEmpty() && !uiState.isDiscovering) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Lan, null, tint = TextHint, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("没有已保存的服务器", color = TextSecondary, fontSize = 16.sp)
                        Text("点击上方搜索或右上角 + 添加", color = TextHint, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(uiState.servers, key = { it.id }) { server ->
                        SambaServerCard(
                            server = server,
                            onClick = { navController.navigate(Routes.sambaBrowse(server.id)) },
                            onEdit = { editingServer = it },
                            onDelete = { viewModel.deleteServer(it) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog || editingServer != null) {
        SambaServerDialog(
            server = editingServer,
            onDismiss = {
                showAddDialog = false
                editingServer = null
            },
            onSave = { server ->
                viewModel.saveServer(server)
                showAddDialog = false
                editingServer = null
            }
        )
    }

    if (showDiscoveredDialog && uiState.discoveredHosts.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showDiscoveredDialog = false },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("发现 ${uiState.discoveredHosts.size} 台设备", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(uiState.discoveredHosts) { host ->
                        Surface(
                            onClick = {
                                showDiscoveredDialog = false
                                editingServer = SambaServer(host = host, displayName = host)
                            },
                            color = DarkSurfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Dns, null, tint = Yellow500, modifier = Modifier.size(24.dp))
                                Column {
                                    Text(host, color = TextPrimary, fontSize = 15.sp)
                                    Text("点击后填写共享名/账号", color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiscoveredDialog = false }) {
                    Text("关闭", color = Yellow500)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SambaServerCard(
    server: SambaServer,
    onClick: () -> Unit,
    onEdit: (SambaServer) -> Unit,
    onDelete: (SambaServer) -> Unit
) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant), shape = MaterialTheme.shapes.medium) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Dns, null, tint = Yellow500, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(server.displayName.ifEmpty { server.host }, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Text("${server.host}:${server.port}/${server.shareName.ifEmpty { "<未填共享名>" }}", color = TextSecondary, fontSize = 13.sp)
            }
            IconButton(onClick = { onEdit(server) }) { Icon(Icons.Default.Edit, "编辑", tint = TextHint) }
            IconButton(onClick = { onDelete(server) }) { Icon(Icons.Default.Delete, "删除", tint = TextHint) }
        }
    }
}

@Composable
private fun SambaServerDialog(
    server: SambaServer?,
    onDismiss: () -> Unit,
    onSave: (SambaServer) -> Unit
) {
    var name by remember(server) { mutableStateOf(server?.displayName ?: server?.name ?: "") }
    var host by remember(server) { mutableStateOf(server?.host ?: "") }
    var port by remember(server) { mutableStateOf((server?.port ?: 445).toString()) }
    var shareName by remember(server) { mutableStateOf(server?.shareName ?: "") }
    var username by remember(server) { mutableStateOf(server?.username ?: "") }
    var password by remember(server) { mutableStateOf(server?.password ?: "") }
    var showPassword by remember(server) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text(if (server == null || server.id == 0L) "添加 Samba 服务器" else "编辑服务器", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("显示名称（可选）") }, colors = textFieldColors(), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("服务器地址 *") }, placeholder = { Text("192.168.1.100", color = TextHint) }, colors = textFieldColors(), singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("端口") }, colors = textFieldColors(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(120.dp))
                    OutlinedTextField(value = shareName, onValueChange = { shareName = it }, label = { Text("共享名") }, placeholder = { Text("Video", color = TextHint) }, colors = textFieldColors(), singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("用户名") }, colors = textFieldColors(), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    colors = textFieldColors(),
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                if (showPassword) "隐藏密码" else "显示密码",
                                tint = TextSecondary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (host.isNotBlank()) {
                        onSave(SambaServer(id = server?.id ?: 0, displayName = name.ifEmpty { host }, host = host, port = port.toIntOrNull() ?: 445, shareName = shareName, username = username, password = password))
                    }
                },
                enabled = host.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Yellow500)
            ) { Text("保存", color = Black) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) } }
    )
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Yellow500,
    focusedBorderColor = Yellow500,
    unfocusedBorderColor = TextHint,
    focusedLabelColor = Yellow500,
    unfocusedLabelColor = TextSecondary
)
