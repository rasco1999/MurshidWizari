package com.muriq.murshid.iosparity

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muriq.murshid.iosparity.notifications.NoticeDelivery
import com.muriq.murshid.iosparity.notifications.StudentNotice
import com.muriq.murshid.iosparity.ui.*

/** Entire inbox is fetched from the student's authenticated server session. */
@Composable
fun NotificationsScreen(vm: MurshidViewModel) {
    val context = LocalContext.current
    val delivery = remember(context) { NoticeDelivery(context.applicationContext) }
    var allowed by remember { mutableStateOf(delivery.alertsEnabled()) }
    var unreadOnly by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = delivery.alertsEnabled()
    }
    val inbox = vm.noticeInbox
    val rows = if (unreadOnly) inbox.items.filter { !it.read } else inbox.items

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionTitle("حساب الطالب", "الإشعارات",
                "تأتي من خادم المنصة لحسابك المسجل فقط.")
        }
        item {
            MurshidCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (allowed) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (allowed) "تنبيهات الهاتف مفعّلة" else "تنبيهات الهاتف غير مفعّلة",
                        modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                }
                Text("داخل التطبيق: مزامنة أثناء الاستخدام. خارج التطبيق: فحص دوري تقريباً كل 15 دقيقة عند تفعيل تذكّر تسجيل الدخول، وفق قيود Android.")
                if (!allowed) {
                    SecondaryAction("السماح بتنبيهات الهاتف") {
                        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else allowed = delivery.alertsEnabled()
                    }
                    Text("إذا سبق رفض الإذن، يمكن تفعيله من إعدادات إشعارات التطبيق.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("غير المقروءة: " + inbox.unread, modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold)
                TextButton(onClick = { vm.markAllNoticesRead() },
                    enabled = inbox.unread > 0 && !vm.noticeLoading && !vm.noticeSaving) {
                    Text("تعليم الكل كمقروء")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !unreadOnly, onClick = { unreadOnly = false }, label = { Text("الكل") })
                FilterChip(selected = unreadOnly, onClick = { unreadOnly = true }, label = { Text("غير المقروءة") })
            }
        }
        if (vm.noticeLoading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        if (vm.noticeError.isNotBlank()) item {
            Text(vm.noticeError, color = MaterialTheme.colorScheme.error)
        }
        if (rows.isEmpty() && !vm.noticeLoading) item {
            EmptyPanel("لا توجد إشعارات", if (unreadOnly) "لا توجد إشعارات غير مقروءة."
                else "عند إرسال إشعار إلى حسابك سيظهر هنا.")
        }
        items(rows, key = { it.id }) { notice ->
            NoticeRow(notice, selected = vm.highlightedNoticeId == notice.id,
                saving = vm.noticeSaving,
                onOpen = { vm.markNoticeRead(notice.id) })
        }
        item {
            SecondaryAction("تحديث الإشعارات", { vm.syncNotices(silent = false) })
        }
    }
}

@Composable
private fun NoticeRow(notice: StudentNotice, selected: Boolean, saving: Boolean, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val surface = if (selected || !notice.read)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    else MaterialTheme.colorScheme.surface
    Column(
        modifier = Modifier.fillMaxWidth().background(surface, shape)
            .clickable(enabled = !saving && !notice.read, onClick = onOpen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!notice.read) Badge { Text("جديد") }
            else Icon(Icons.Default.CheckCircle, contentDescription = "مقروء",
                modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
            Text(notice.title.ifBlank { "إشعار من المرشد الوزاري" },
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        if (notice.body.isNotBlank()) Text(notice.body)
        if (notice.createdAt.isNotBlank())
            Text(notice.createdAt, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!notice.read) Text("اضغط لتحديد الإشعار كمقروء", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary)
    }
}
