package cn.edu.shmtu.terminal.android.ui.hotwater

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.R
import cn.edu.shmtu.terminal.android.domain.model.HotWaterBuilding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotWaterScreen(
    onBack: () -> Unit,
    viewModel: HotWaterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var captchaInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("热水查询") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_home),
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { /* reload */ }) {
                        Text("刷新")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.followedBuildings.isNotEmpty()) {
                    item {
                        Text(
                            text = "关注的楼栋",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(
                        uiState.buildings.filter { it.isFollowed }
                            .sortedBy { it.buildingNumber }
                    ) { building ->
                        BuildingCard(
                            building = building,
                            onToggleFollow = { viewModel.toggleFollow(it) }
                        )
                    }
                }

                if (uiState.buildings.isNotEmpty()) {
                    item {
                        Text(
                            text = "全部楼栋",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(
                        uiState.buildings.sortedBy { it.buildingNumber }
                    ) { building ->
                        BuildingCard(
                            building = building,
                            onToggleFollow = { viewModel.toggleFollow(it) }
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (uiState.showCaptchaDialog) {
        CaptchaDialog(
            captchaImage = uiState.captchaImage,
            captchaInput = captchaInput,
            onCaptchaInputChange = { captchaInput = it },
            onConfirm = {
                if (captchaInput.isNotBlank()) {
                    viewModel.submitCaptcha(captchaInput)
                    captchaInput = ""
                }
            },
            onDismiss = {
                viewModel.dismissCaptchaDialog()
                captchaInput = ""
            },
            focusManager = focusManager
        )
    }
}

@Composable
private fun BuildingCard(
    building: HotWaterBuilding,
    onToggleFollow: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors()
    ) {
        ListItem(
            headlineContent = {
                Text("${building.buildingNumber}号楼")
            },
            supportingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "温度: ${building.temperature}℃",
                        color = if (building.temperature > 40)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                    Text(text = "水位: ${building.waterLevel}%")
                }
            },
            trailingContent = {
                TextButton(onClick = { onToggleFollow(building.buildingNumber) }) {
                    Text(if (building.isFollowed) "取消关注" else "关注")
                }
            }
        )
    }
}

@Composable
private fun CaptchaDialog(
    captchaImage: ByteArray?,
    captchaInput: String,
    onCaptchaInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    focusManager: FocusManager
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("请输入验证码") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "请输入下方验证码的计算结果",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                captchaImage?.let { imageData ->
                    val bitmap = remember(imageData) {
                        BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                    }
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "验证码图片",
                            modifier = Modifier.height(80.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = captchaInput,
                    onValueChange = onCaptchaInputChange,
                    label = { Text("计算结果") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            onConfirm()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = captchaInput.isNotBlank()
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
