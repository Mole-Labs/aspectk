package sample.multiplatform.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sample.multiplatform.aspects.AuditAspect
import sample.multiplatform.aspects.LoggingAspect
import sample.multiplatform.aspects.PermissionAspect
import sample.multiplatform.aspects.TracingAspect
import sample.multiplatform.db.database
import sample.multiplatform.exceptions.DoubleClickException
import sample.multiplatform.exceptions.PermissionDeniedException
import sample.multiplatform.platform.platformName
import sample.multiplatform.service.PaymentService
import sample.multiplatform.service.UserService
import sample.multiplatform.viewmodel.OrderViewModel
import sample.multiplatform.viewmodel.ProductViewModel

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val userService = remember { UserService(database.userDao()) }
    val paymentService = remember { PaymentService() }
    val productVm = remember { ProductViewModel() }
    val orderVm = remember { OrderViewModel() }
    val logs = remember { mutableStateListOf<String>() }
    var statusMessage by remember { mutableStateOf("버튼을 눌러 AspectK Advice 동작을 확인하세요") }
    var isError by remember { mutableStateOf(false) }
    var isLoggedIn by remember { mutableStateOf(false) }

    // 앱 시작 시 초기 권한 부여 및 로그 핸들러 설정
    remember {
        PermissionAspect.grantedPermissions += "READ_USER_DATA"
        PermissionAspect.grantedPermissions += "REFUND"
        LoggingAspect.logger = { message ->
            logs.add(message)
        }
        TracingAspect.logger = { message ->
            logs.add(message)
        }
        AuditAspect.auditLogger = { message ->
            logs.add(message)
        }
    }

    fun runSafely(action: () -> String) {
        try {
            statusMessage = action()
            isError = false
        } catch (e: PermissionDeniedException) {
            statusMessage = "권한 오류: ${e.message}"
            isError = true
        } catch (e: DoubleClickException) {
            statusMessage = "중복 클릭 차단: ${e.message}"
            isError = true
        } catch (e: Exception) {
            statusMessage = "오류: ${e.message}"
            isError = true
        }
    }

    fun runSafelySuspend(action: suspend () -> String) {
        scope.launch {
            try {
                statusMessage = action()
                isError = false
            } catch (e: PermissionDeniedException) {
                statusMessage = "권한 오류: ${e.message}"
                isError = true
            } catch (e: DoubleClickException) {
                statusMessage = "중복 클릭 차단: ${e.message}"
                isError = true
            } catch (e: Exception) {
                statusMessage = "오류: ${e.message}"
                isError = true
            }
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "AspectK Multiplatform Sample",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = "Platform: ${platformName()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    if (isLoggedIn) {
                        OutlinedButton(
                            onClick = {
                                runSafely {
                                    userService.logout()
                                    isLoggedIn = false
                                    PermissionAspect.grantedPermissions -= "ADMIN"
                                    "logout() → 세션 종료"
                                }
                            },
                        ) { Text("Logout") }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 상태 카드
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = statusMessage,
                        modifier = Modifier.padding(12.dp),
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // UserService 버튼들
                Text("UserService", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            runSafely {
                                val result = userService.login("admin", "secret")
                                if (result) {
                                    isLoggedIn = true
                                    PermissionAspect.grantedPermissions += "ADMIN"
                                }
                                "login(admin, ***) → $result"
                            }
                        },
                    ) { Text("login()") }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            runSafelySuspend {
                                val result = userService.createUser("alice", "alice@example.com")
                                "createUser(alice) → $result"
                            }
                        },
                    ) { Text("createUser()") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            runSafelySuspend {
                                val result = userService.getAllUsers()
                                "getAllUsers() → $result"
                            }
                        },
                    ) { Text("getAllUsers()") }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            // 권한 박탈 후 호출 → PermissionDeniedException 데모
                            PermissionAspect.revokeAll()
                            runSafelySuspend {
                                userService.getAllUsers()
                                "unreachable"
                            }
                            // 복원
                            PermissionAspect.grantedPermissions += "ADMIN"
                            PermissionAspect.grantedPermissions += "READ_USER_DATA"
                            PermissionAspect.grantedPermissions += "REFUND"
                        },
                    ) { Text("권한 없이 호출") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            runSafelySuspend {
                                val result = userService.deleteUser("alice")
                                "deleteUser(alice) → $result"
                            }
                        },
                    ) { Text("deleteUser(alice)") }

                    Spacer(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // PaymentService 버튼들
                Text("PaymentService", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            runSafely {
                                val txn = paymentService.requestPayment("order-001", 99.0)
                                "requestPayment() → $txn"
                            }
                        },
                    ) { Text("결제 요청") }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            // 빠른 연속 클릭 → DoubleClickException 데모
                            runSafely {
                                paymentService.requestPayment("order-rapid", 1.0)
                                "unreachable"
                            }
                        },
                    ) { Text("중복 결제 시도") }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ViewModel (inherits=true) 버튼들
                Text("ViewModel (inherits=true)", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            runSafely {
                                val result = productVm.loadData()
                                "ProductViewModel.loadData() → $result"
                            }
                        },
                    ) { Text("ProductVM.loadData()") }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            runSafely {
                                val result = orderVm.submit("Order#NEW")
                                "OrderViewModel.submit() → $result"
                            }
                        },
                    ) { Text("OrderVM.submit()") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            runSafely {
                                productVm.reset()
                                orderVm.reset()
                                "reset() 완료 → ProductVM + OrderVM 각각 Audit 로그 기록됨"
                            }
                        },
                    ) { Text("reset() 모두 호출") }

                    Spacer(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 로그 패널
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Aspect 로그", style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(onClick = { logs.clear() }) {
                        Text("초기화")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Card(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        if (logs.isEmpty()) {
                            item {
                                Text(
                                    text = "아직 로그가 없습니다.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(4.dp),
                                )
                            }
                        }
                        items(logs) { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
