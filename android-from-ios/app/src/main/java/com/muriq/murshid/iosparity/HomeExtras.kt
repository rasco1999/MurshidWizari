package com.muriq.murshid.iosparity

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.muriq.murshid.iosparity.ui.*
import kotlinx.coroutines.delay
import java.lang.Exception

private data class Slide(val title: String, val file: String)

private val slides = listOf(
    Slide("تحدي المليون", "million.webp"),
    Slide("أعلن هنا", "advertise-here.webp"),
    Slide("الاختبارات", "tests.webp"),
    Slide("النجاح", "study.webp")
)

@Composable
fun HomeSlider(vm: MurshidViewModel) {
    val pager = rememberPagerState(pageCount = { slides.size })
    val context = LocalContext.current
    val network = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }
    val metered = try { network?.isActiveNetworkMetered ?: true } catch (_: Exception) { true }
    LaunchedEffect(pager.currentPage, metered) {
        if (!metered) {
            delay(5500)
            pager.animateScrollToPage((pager.currentPage + 1) % slides.size)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(220.dp)) { page ->
            val slide = slides[page]
            Box(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(MurshidNavy, MurshidBlue)))
                    .clickable {
                        when (page) {
                            0 -> vm.openFeature(Feature.CONTEST)
                            1 -> context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://t.me/r_cs4")))
                            2 -> vm.selectTab(1)
                            3 -> vm.openFeature(Feature.INSIGHTS)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = "https://www.mur-iq.com/assets/img/hero/${slide.file}?v=20260928-v56",
                    contentDescription = slide.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = painterResource(R.drawable.murshid_original)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            slides.indices.forEach { i ->
                Box(
                    Modifier.padding(horizontal = 3.dp)
                        .size(width = if (i == pager.currentPage) 23.dp else 7.dp, height = 7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (i == pager.currentPage) MurshidBlue
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.19f)
                        )
                )
            }
        }
    }
}

@Composable
fun OfficialSocialLinks() {
    val ctx = LocalContext.current
    val urls = listOf(
        Triple("إنستغرام", "https://www.instagram.com/murshid.iq/", Color(0xFFC43C84)),
        Triple("فيسبوك", "https://www.facebook.com/Murshidiq/", Color(0xFF2154C6)),
        Triple("تيليجرام", "https://t.me/El_Murshed_iq", Color(0xFF2492CA))
    )
    MurshidCard {
        Text("تابعنا على منصات التواصل", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            urls.forEach { (name, url, color) ->
                Column(
                    Modifier.weight(1f).clickable {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }.padding(5.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(49.dp).clip(RoundedCornerShape(15.dp)).background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(when (name) {
                            "إنستغرام" -> Icons.Default.PhotoCamera
                            "فيسبوك" -> Icons.Default.Public
                            else -> Icons.Default.Send
                        }, null, tint = Color.White)
                    }
                    Text(name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text("اضغط على الأيقونة لزيارة حساب المنصة الرسمي",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
