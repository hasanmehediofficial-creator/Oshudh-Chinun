package com.oshudhchinun.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.File

private val Green = Color(0xFF087F5B)
private val Pale = Color(0xFFE9F7F0)
data class Medicine(val brand:String,val generic:String,val strength:String,val form:String,val maker:String,val price:String,val source:String,val checked:String)
private const val DATA_URL = "https://raw.githubusercontent.com/hasanmehediofficial-creator/Oshudh-Chinun/main/data/medicines.json"
private fun parseDataset(json: String): List<Medicine> {
    val root = JSONObject(json)
    val records = root.getJSONArray("records")
    return (0 until records.length()).map { i ->
        val item = records.getJSONObject(i)
        fun value(key: String, fallback: String = "তথ্য নেই"): String {
            val v = item.opt(key)
            return if (v == null || v == JSONObject.NULL || v.toString().isBlank()) fallback else v.toString()
        }
        Medicine(value("brand", "নাম নেই"), value("generic"), value("strength"), value("form"),
            value("manufacturer"), value("price", "দাম প্রকাশিত নয়"),
            value("priceSource", "ডেটাসেটের উৎস যাচাই প্রয়োজন"),
            value("clinicalReviewedAt", value("priceCheckedAt", "যাচাইয়ের তারিখ নেই")))
    }
}
private fun readCachedDataset(context: Context): String? =
    runCatching { File(context.filesDir, "medicines-cache.json").takeIf { it.exists() }?.readText() }.getOrNull()
        ?: runCatching { context.assets.open("medicines.json").bufferedReader().use { it.readText() } }.getOrNull()
private suspend fun syncDataset(context: Context): Pair<List<Medicine>, String> = withContext(Dispatchers.IO) {
    val connection = URL(DATA_URL).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"
        connection.connect()
        require(connection.responseCode in 200..299) { "Dataset server returned an error" }
        val json = connection.inputStream.bufferedReader().use { it.readText() }
        val parsed = parseDataset(json)
        require(parsed.isNotEmpty()) { "Empty dataset" }
        File(context.filesDir, "medicines-cache.json").writeText(json)
        parsed to JSONObject(json).optString("datasetVersion", "অজানা")
    } finally { connection.disconnect() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { OshudhApp() } }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun OshudhApp() {
    var query by remember { mutableStateOf("") }
    var dark by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var selectedMedicine by remember { mutableStateOf<Medicine?>(null) }
    var pickedText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("oshudh_chinun", Context.MODE_PRIVATE) }
    var savedBrands by remember { mutableStateOf(prefs.getStringSet("saved_brands", emptySet())?.toSet() ?: emptySet()) }
    fun toggleSaved(brand: String) {
        val next = if (savedBrands.contains(brand)) savedBrands - brand else savedBrands + brand
        savedBrands = next
        prefs.edit().putStringSet("saved_brands", next).apply()
    }
    var medicines by remember { mutableStateOf<List<Medicine>>(emptyList()) }
    var dataStatus by remember { mutableStateOf("ডেটাসেট লোড হচ্ছে…") }
    var syncing by remember { mutableStateOf(false) }
    var lastSync by remember { mutableStateOf(prefs.getString("last_sync", "এখনো সফল আপডেট নেই") ?: "এখনো সফল আপডেট নেই") }
    var datasetVersion by remember { mutableStateOf("অজানা") }
    val scope = rememberCoroutineScope()
    fun loadCachedDataset() {
        val cached = readCachedDataset(context)
        if (cached == null) {
            dataStatus = "ক্যাশ নেই; অনলাইনে সংযোগের চেষ্টা চলছে"
            return
        }
        runCatching { parseDataset(cached) }.onSuccess { records ->
            medicines = records
            datasetVersion = runCatching { JSONObject(cached).optString("datasetVersion", "অজানা") }.getOrDefault("অজানা")
            dataStatus = "অফলাইন ক্যাশ • ${records.size}টি রেকর্ড"
        }.onFailure { dataStatus = "ক্যাশের ডেটা পড়া যায়নি" }
    }
    fun refreshDataset() {
        if (syncing) return
        syncing = true
        scope.launch {
            try {
                val (records, version) = syncDataset(context)
                medicines = records
                datasetVersion = version
                val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
                lastSync = stamp
                prefs.edit().putString("last_sync", stamp).apply()
                dataStatus = "সিঙ্ক সফল • ${records.size}টি রেকর্ড"
            } catch (_: Exception) {
                if (medicines.isEmpty()) loadCachedDataset()
                dataStatus = if (medicines.isEmpty()) "সিঙ্ক ব্যর্থ; ডেটা পাওয়া যায়নি" else "সিঙ্ক ব্যর্থ • ক্যাশ দেখানো হচ্ছে"
            } finally { syncing = false }
        }
    }
    LaunchedEffect(Unit) {
        loadCachedDataset()
        refreshDataset()
    }
    val filtered = medicines.filter { (it.brand+" "+it.generic+" "+it.maker).contains(query,ignoreCase=true) }
    val visibleMedicines = if (tab == 1) medicines.filter { savedBrands.contains(it.brand) && (query.isBlank() || (it.brand+" "+it.generic+" "+it.maker).contains(query, ignoreCase=true)) } else filtered
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            pickedText = ""
        } else {
            pickedText = "ছবির লেখা পড়া হচ্ছে…"
            try {
                val image = InputImage.fromFilePath(context, uri)
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(image)
                    .addOnSuccessListener { result ->
                        val extracted = result.text.trim()
                        pickedText = if (extracted.isBlank()) "ছবিতে পড়ার মতো লেখা পাওয়া যায়নি।" else "ছবি থেকে পাওয়া লেখা: $extracted"
                        if (extracted.isNotBlank()) query = extracted.lines().first().take(80)
                    }
                    .addOnFailureListener { pickedText = "ছবির লেখা পড়া যায়নি। পরিষ্কার ছবি দিয়ে আবার চেষ্টা করুন।" }
            } catch (e: Exception) {
                pickedText = "ছবি খুলতে সমস্যা হয়েছে। অন্য ছবি নির্বাচন করুন।"
            }
        }
    }
    MaterialTheme(colorScheme = if(dark) darkColorScheme(primary=Color(0xFF69D6A5)) else lightColorScheme(primary=Green, secondary=Green)) {
        Scaffold(containerColor=MaterialTheme.colorScheme.background, bottomBar={
            NavigationBar { listOf("হোম","সংরক্ষিত","প্রোফাইল").forEachIndexed { i, label ->
                NavigationBarItem(selected=tab==i,onClick={tab=i},icon={ Icon(if(i==0) Icons.Default.Home else if(i==1) Icons.Default.Bookmark else Icons.Default.Person,contentDescription=label) },label={Text(label)})
            } }
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=18.dp), verticalArrangement=Arrangement.spacedBy(14.dp), contentPadding=PaddingValues(top=18.dp,bottom=24.dp)) {
                item {
                    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) { Text("ওষুধ চিনুন",fontSize=27.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary); Text("ওষুধের তথ্য খুঁজুন",color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        IconButton(onClick={dark=!dark}) { Icon(if(dark) Icons.Default.LightMode else Icons.Default.DarkMode,contentDescription="থিম বদলান") }
                    }
                }
                item {
                    Card(colors=CardDefaults.cardColors(containerColor=Pale),shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("ওষুধ খুঁজুন",fontSize=21.sp,fontWeight=FontWeight.Bold,color=Green)
                            Text("ব্র্যান্ড, জেনেরিক বা প্রস্তুতকারকের নাম লিখুন।",color=Color(0xFF255C49))
                            OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("যেমন: Napa, Paracetamol")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true)
                            Button(onClick={ photoPicker.launch("image/*") },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)) { Icon(Icons.Default.PhotoCamera,null); Spacer(Modifier.width(8.dp)); Text("প্যাকেটের ছবি নির্বাচন করুন") }
                            if(pickedText.isNotBlank()) Text(pickedText,fontSize=13.sp,color=Green)
                        }
                    }
                }
                item {
                    Card(shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("অ্যাপের ভেতরেই ওষুধের তথ্য", fontSize=18.sp, fontWeight=FontWeight.Bold, color=MaterialTheme.colorScheme.primary)
                            Text("লক্ষ্য: সার্চ, বিস্তারিত তথ্য ও সংরক্ষণ—সব এই অ্যাপের ভেতরে। বর্তমানে ৫০০টি ক্যাটালগ রেকর্ড যুক্ত আছে; জেনেরিক নাম, বর্তমান দাম ও চিকিৎসাসংক্রান্ত তথ্যের যাচাই চলছে।")
                            Text("ডেটা অবস্থা: ৫০০টি ক্যাটালগ রেকর্ড • সর্বশেষ সফল আপডেট: নেই", fontSize=12.sp, color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    Card(shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant),modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("স্বাস্থ্য-নিরাপত্তা নোট",fontWeight=FontWeight.Bold)
                            Text("এই অ্যাপ রোগ নির্ণয় বা ডোজ নির্ধারণ করে না। ৫০০টি ক্যাটালগ রেকর্ডে জেনেরিক নাম, দাম ও ক্লিনিক্যাল তথ্য সম্পূর্ণ যাচাই করা হয়নি। ওষুধ খাওয়ার আগে চিকিৎসক বা ফার্মাসিস্টের পরামর্শ নিন।",fontSize=13.sp)
                        }
                    }
                }
                item { Text(if(tab==1) "সংরক্ষিত ওষুধ" else if(tab==2) "প্রোফাইল ও সেটিংস" else "সার্চের নমুনা",fontSize=19.sp,fontWeight=FontWeight.Bold) }
                if(tab==2) item {
                    Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("অ্যাপ সেটিংস",fontWeight=FontWeight.Bold)
                        Row(verticalAlignment=Alignment.CenterVertically) { Text("ডার্ক মোড",Modifier.weight(1f)); Switch(checked=dark,onCheckedChange={dark=it}) }
                        Text("ডেটাসেট সংস্করণ: $datasetVersion • $dataStatus",fontSize=13.sp)
                        Text("সর্বশেষ সফল সিঙ্ক: $lastSync",fontSize=13.sp)
                        Button(onClick = { refreshDataset() }, enabled = !syncing) { Text(if (syncing) "আপডেট হচ্ছে…" else "এখনই ডেটা আপডেট করুন") }
                    } }
                } else {
                    items(visibleMedicines) { med ->
                        Card(shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().clickable { selectedMedicine = med }) {
                            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text(med.brand,fontSize=20.sp,fontWeight=FontWeight.Bold); Text(med.generic,color=MaterialTheme.colorScheme.primary) }
                                    IconButton(onClick={toggleSaved(med.brand)}) { Icon(if(savedBrands.contains(med.brand)) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,contentDescription=if(savedBrands.contains(med.brand)) "সংরক্ষণ থেকে বাদ দিন" else "সংরক্ষণ করুন") }
                                }
                                Text("${med.strength} • ${med.form}")
                                Text("প্রস্তুতকারক: ${med.maker}",fontSize=13.sp)
                                Text("দাম: ${med.price}",fontWeight=FontWeight.SemiBold)
                                HorizontalDivider()
                                Text("তথ্যসূত্র: ${med.source}",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("যাচাইয়ের তারিখ: ${med.checked}",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if(visibleMedicines.isEmpty()) item { Text(if(tab==1) "এখনো কোনো সংরক্ষিত ওষুধ মেলেনি। হোমে গিয়ে বুকমার্ক আইকনে চাপ দিন।" else "কোনো নমুনা মেলেনি। পূর্ণ ডেটাবেস এখনো যুক্ত হয়নি।") }
                }
                item { Text("তথ্য-সংক্রান্ত অভিযোগ বা ভুল পেলে যাচাই করা সরকারি উৎসের সঙ্গে মিলিয়ে নিন।",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        selectedMedicine?.let { med ->
            AlertDialog(
                onDismissRequest = { selectedMedicine = null },
                title = { Text(med.brand, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("জেনেরিক: ${med.generic}")
                        Text("শক্তি/ফর্ম: ${med.strength} • ${med.form}")
                        Text("প্রস্তুতকারক: ${med.maker}")
                        HorizontalDivider()
                        Text("ব্যবহার / কী কাজে লাগে", fontWeight = FontWeight.Bold)
                        Text("যাচাইকৃত তথ্য এখনো এই নমুনা রেকর্ডে যোগ করা হয়নি।")
                        Text("পার্শ্বপ্রতিক্রিয়া ও সতর্কতা", fontWeight = FontWeight.Bold)
                        Text("যাচাইকৃত তথ্য অনুপস্থিত—অনুমান করে দেখানো হচ্ছে না।")
                        Text("মূল্য: ${med.price}")
                        Text("উৎস: ${med.source}")
                        Text("যাচাইয়ের তারিখ: ${med.checked}")
                        Text("নিরাপত্তা: এই স্ক্রিন থেকে রোগ নির্ণয় বা ডোজ নির্ধারণ করা হয় না।")
                    }
                },
                confirmButton = { TextButton(onClick = { selectedMedicine = null }) { Text("বন্ধ করুন") } }
            )
        }
    }
}
