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

private val Green = Color(0xFF087F5B)
private val Pale = Color(0xFFE9F7F0)
data class Medicine(val brand:String,val generic:String,val strength:String,val form:String,val maker:String,val price:String,val source:String,val checked:String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { OshudhApp() } }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun OshudhApp() {
    var query by remember { mutableStateOf("") }
    var dark by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var showInfo by remember { mutableStateOf(false) }
    var pickedText by remember { mutableStateOf("") }
    val sample = remember { listOf(
        Medicine("Napa","Paracetamol","500 mg","Tablet","Beximco Pharmaceuticals","যাচাই করা হয়নি","নমুনা রেকর্ড—সরকারি ডেটা নয়","—"),
        Medicine("Seclo","Omeprazole","20 mg","Capsule","Square Pharmaceuticals","যাচাই করা হয়নি","নমুনা রেকর্ড—সরকারি ডেটা নয়","—"),
        Medicine("ORS","Oral Rehydration Salts","স্যাশে","Powder","বিভিন্ন প্রস্তুতকারক","প্যাকেটভেদে ভিন্ন","তথ্য যাচাই প্রয়োজন","—")
    ) }
    val filtered = sample.filter { (it.brand+" "+it.generic+" "+it.maker).contains(query,ignoreCase=true) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickedText = if (uri == null) "" else "ছবি নির্বাচন হয়েছে। ছবির লেখা শনাক্ত করার ফিচার পরবর্তী ধাপে যুক্ত হবে।"
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
                            Button(onClick={ { photoPicker.launch("image/*") } },modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)) { Icon(Icons.Default.PhotoCamera,null); Spacer(Modifier.width(8.dp)); Text("প্যাকেটের ছবি নির্বাচন করুন") }
                            if(pickedText.isNotBlank()) Text(pickedText,fontSize=13.sp,color=Green)
                        }
                    }
                }
                item {
                    Card(shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant),modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("স্বাস্থ্য-নিরাপত্তা নোট",fontWeight=FontWeight.Bold)
                            Text("এই অ্যাপ রোগ নির্ণয় বা ডোজ নির্ধারণ করে না। ওষুধ খাওয়ার আগে চিকিৎসক বা ফার্মাসিস্টের পরামর্শ নিন। নিচের রেকর্ডগুলো শুধু UI নমুনা; যাচাইকৃত ডেটাবেস নয়।",fontSize=13.sp)
                        }
                    }
                }
                item { Text(if(tab==1) "সংরক্ষিত ওষুধ" else if(tab==2) "প্রোফাইল ও সেটিংস" else "সার্চের নমুনা",fontSize=19.sp,fontWeight=FontWeight.Bold) }
                if(tab==2) item {
                    Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("অ্যাপ সেটিংস",fontWeight=FontWeight.Bold)
                        Row(verticalAlignment=Alignment.CenterVertically) { Text("ডার্ক মোড",Modifier.weight(1f)); Switch(checked=dark,onCheckedChange={dark=it}) }
                        Text("ডেটাবেস ও মূল্য আপডেট এখনো সংযুক্ত নয়।",fontSize=13.sp)
                    } }
                } else {
                    items(filtered) { med ->
                        Card(shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().clickable { showInfo=true }) {
                            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text(med.brand,fontSize=20.sp,fontWeight=FontWeight.Bold); Text(med.generic,color=MaterialTheme.colorScheme.primary) }
                                    IconButton(onClick={showInfo=true}) { Icon(Icons.Default.BookmarkBorder,contentDescription="সংরক্ষণ") }
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
                    if(filtered.isEmpty()) item { Text("কোনো নমুনা মেলেনি। পূর্ণ ডেটাবেস এখনো যুক্ত হয়নি।") }
                }
                item { Text("তথ্য-সংক্রান্ত অভিযোগ বা ভুল পেলে যাচাই করা সরকারি উৎসের সঙ্গে মিলিয়ে নিন।",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if(showInfo) AlertDialog(onDismissRequest={showInfo=false},title={Text("সংরক্ষণ সুবিধা")},text={Text("এই প্রাথমিক সংস্করণে সংরক্ষণ ও পূর্ণ ডেটাবেস এখনো চালু হয়নি।")},confirmButton={TextButton(onClick={showInfo=false}){Text("ঠিক আছে")}})
    }
}
