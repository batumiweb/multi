package ge.gela.multi

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val Indigo = Color(0xFF3949AB)
val IndigoDark = Color(0xFF283593)
val IndigoLight = Color(0xFFE8EAF6)
val Amber = Color(0xFFFFF3E0)
val AmberDark = Color(0xFFB26A00)

data class AppItem(val pkg: String, val label: String, val icon: ImageBitmap, val component: ComponentName)

fun loadApps(ctx: Context, user: UserHandle): List<AppItem> {
    val la = ctx.getSystemService(LauncherApps::class.java)
    return try {
        la.getActivityList(null, user)
            .filter { it.applicationInfo.packageName != ctx.packageName }
            .distinctBy { it.applicationInfo.packageName }
            .map {
                AppItem(
                    it.applicationInfo.packageName,
                    it.label.toString(),
                    it.getBadgedIcon(0).toBitmap(96, 96).asImageBitmap(),
                    it.componentName
                )
            }
            .sortedBy { it.label.lowercase() }
    } catch (e: Exception) { emptyList() }
}

fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

class MainActivity : ComponentActivity() {
    var refreshTick by mutableIntStateOf(0)

    val provision = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode != RESULT_OK && Profile.workUser(this) == null) {
            toast(this, "სივრცე ვერ შეიქმნა. თუ ტელეფონზე უკვე არის სამუშაო პროფილი ან „მეორე სივრცე“, შეიძლება ხელს უშლიდეს.")
        }
        refreshLater()
    }

    fun refreshLater() {
        refreshTick++
        val h = Handler(Looper.getMainLooper())
        h.postDelayed({ refreshTick++ }, 1500)
        h.postDelayed({ refreshTick++ }, 4000)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Indigo, secondary = Indigo)) {
                App(this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTick++
    }
}

@Composable
fun App(act: MainActivity) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            Box(
                Modifier.fillMaxWidth().background(Indigo).statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Column {
                    Text("მულტი", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when (tab) {
                            0 -> "ნებისმიერი აპის მეორე ასლი"
                            1 -> "ვებ ექაუნთები — რამდენიც გინდა"
                            else -> "როგორ მივიღო მეტი ასლი"
                        },
                        color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, null) }, label = { Text("აპების კლონი") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Person, null) }, label = { Text("ვებ ექაუნთები") }
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Info, null) }, label = { Text("მეტი ასლი") }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> CloneTab(act)
                1 -> WebTab()
                else -> GuideTab()
            }
        }
    }
}

// ───────────────────────── კლონები (Work Profile) ─────────────────────────

@Composable
fun CloneTab(act: MainActivity) {
    val ctx = LocalContext.current
    val tick = act.refreshTick
    var work by remember { mutableStateOf<UserHandle?>(null) }
    var paused by remember { mutableStateOf(false) }
    var cloned by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var personal by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmHide by remember { mutableStateOf<AppItem?>(null) }

    LaunchedEffect(tick) {
        val w = Profile.workUser(ctx)
        val result = withContext(Dispatchers.Default) {
            val c = if (w != null) loadApps(ctx, w) else emptyList()
            val p = loadApps(ctx, Process.myUserHandle())
            Pair(c, p)
        }
        work = w
        paused = w != null && Profile.isProfilePaused(ctx, w)
        cloned = result.first
        personal = result.second
        loading = false
    }

    if (Profile.isInsideProfile(ctx)) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("ეს კლონების სივრცის ასლია. გამოიყენე მთავარი „მულტი“ აპი.")
        }
        return
    }

    val w = work
    val clonedPkgs = cloned.map { it.pkg }.toSet()
    val available = personal.filter {
        it.pkg !in clonedPkgs && (query.isBlank() || it.label.contains(query, ignoreCase = true))
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (loading) {
            item { Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { CircularProgressIndicator() } }
            return@LazyColumn
        }

        if (w == null) {
            item { SetupCard(act) }
            return@LazyColumn
        }

        item { InfoCard() }

        if (paused) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Amber)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("კლონები გამორთულია (პაუზაზეა)", fontWeight = FontWeight.Bold, color = AmberDark)
                        Spacer(Modifier.height(4.dp))
                        Text("ჩართე, რომ კლონებმა იმუშაონ და შეტყობინებები მოვიდეს.", fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            try {
                                ctx.getSystemService(UserManager::class.java).requestQuietModeEnabled(false, w)
                            } catch (e: Exception) {
                                toast(ctx, "ჩართე „სამუშაო აპები“ ზედა სწრაფი პარამეტრებიდან")
                            }
                            act.refreshLater()
                        }) { Text("ჩართვა") }
                    }
                }
            }
        }

        item { SectionTitle("შენი კლონები (${cloned.size})") }
        if (cloned.isEmpty()) {
            item {
                Text(
                    "ჯერ კლონი არ გაქვს. ქვემოთ აირჩიე აპი (ბანკი, Facebook, Messenger, WhatsApp…) და დააჭირე „კლონი“.",
                    fontSize = 14.sp, color = Color.Gray
                )
            }
        }
        items(cloned, key = { "c_" + it.pkg }) { app ->
            AppRow(app, buttonText = "გახსნა", onButton = {
                try {
                    ctx.getSystemService(LauncherApps::class.java).startMainActivity(app.component, w, null, null)
                } catch (e: Exception) {
                    toast(ctx, if (paused) "ჯერ ჩართე კლონები" else "ვერ გაიხსნა")
                }
            }, onDelete = { confirmHide = app })
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionTitle("კლონის დამატება")
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("მოძებნე აპი…") }
            )
        }
        items(available, key = { "p_" + it.pkg }) { app ->
            AppRow(app, buttonText = "კლონი", onButton = {
                if (Profile.sendToProfile(ctx, Profile.ACTION_CLONE, app.pkg)) act.refreshLater()
                else toast(ctx, "კლონების სივრცე ვერ მოიძებნა")
            })
        }

        item {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { confirmRemove = true }) {
                Icon(Icons.Default.Delete, null, tint = Color(0xFFC62828))
                Spacer(Modifier.width(6.dp))
                Text("კლონების სივრცის წაშლა", color = Color(0xFFC62828))
            }
        }
    }

    confirmHide?.let { app ->
        AlertDialog(
            onDismissRequest = { confirmHide = null },
            title = { Text("კლონის წაშლა") },
            text = { Text("წავშალო „${app.label}“-ის კლონი? ორიგინალი აპი ხელუხლებელი დარჩება.") },
            confirmButton = {
                TextButton(onClick = {
                    if (Profile.sendToProfile(ctx, Profile.ACTION_HIDE, app.pkg)) act.refreshLater()
                    else toast(ctx, "ვერ მოხერხდა")
                    confirmHide = null
                }) { Text("წაშლა") }
            },
            dismissButton = { TextButton(onClick = { confirmHide = null }) { Text("გაუქმება") } }
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("მთელი სივრცის წაშლა") },
            text = { Text("წაიშლება ყველა კლონი და მათში შესული ექაუნთები. ორიგინალ აპებს არაფერი მოუვა.") },
            confirmButton = {
                TextButton(onClick = {
                    if (Profile.sendToProfile(ctx, Profile.ACTION_REMOVE_PROFILE)) act.refreshLater()
                    else toast(ctx, "ეს სივრცე სხვა აპის შექმნილია — წაშალე პარამეტრებიდან (ანგარიშები → სამუშაო პროფილი)")
                    confirmRemove = false
                }) { Text("წაშლა", color = Color(0xFFC62828)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("გაუქმება") } }
        )
    }
}

@Composable
fun SetupCard(act: MainActivity) {
    val ctx = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = IndigoLight)) {
        Column(Modifier.padding(20.dp)) {
            Text("კლონების სივრცე", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = IndigoDark)
            Spacer(Modifier.height(8.dp))
            Text(
                "ტელეფონზე შეიქმნება ცალკე სივრცე, სადაც ნებისმიერი აპის მეორე ასლს დააყენებ — " +
                    "ბანკი, Facebook, Messenger, WhatsApp. ასლს თავისი ექაუნთი ექნება და ორივეში " +
                    "ერთდროულად იქნები შესული.",
                fontSize = 14.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "შემდეგ ეკრანზე Android გკითხავს თანხმობას — დააჭირე „თანხმობა / შემდეგი“. " +
                    "ეს Android-ის ოფიციალური ფუნქციაა („სამუშაო პროფილი“).",
                fontSize = 13.sp, color = Color.DarkGray
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val i = Profile.provisioningIntent(ctx)
                    if (i.resolveActivity(ctx.packageManager) != null) act.provision.launch(i)
                    else toast(ctx, "შენი ტელეფონი ამას არ უჭერს მხარს, ან სამუშაო პროფილი უკვე არსებობს")
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("სივრცის შექმნა", fontSize = 16.sp) }
        }
    }
}

@Composable
fun InfoCard() {
    Card(colors = CardDefaults.cardColors(containerColor = IndigoLight)) {
        Column(Modifier.padding(14.dp)) {
            Text("• აქ ყოველ აპს 1 კლონი ექნება — მეტისთვის ნახე „მეტი ასლი“", fontSize = 13.sp)
            Text("• კლონებს აიკონზე პატარა ჩანთის ნიშანი აქვთ", fontSize = 13.sp)
            Text("• WhatsApp-ის კლონს სხვა ტელეფონის ნომერი სჭირდება", fontSize = 13.sp)
            Text("• Facebook-ის მე-3, მე-4… ექაუნთი — „ვებ ექაუნთების“ ჩანართში", fontSize = 13.sp)
        }
    }
}

@Composable
fun SectionTitle(s: String) =
    Text(s, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))

@Composable
fun AppRow(app: AppItem, buttonText: String, onButton: () -> Unit, onDelete: (() -> Unit)? = null) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(app.icon, null, Modifier.size(42.dp))
            Spacer(Modifier.width(12.dp))
            Text(app.label, Modifier.weight(1f), fontSize = 15.sp, maxLines = 1)
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "წაშლა", tint = Color.Gray) }
            }
            FilledTonalButton(onClick = onButton) { Text(buttonText) }
        }
    }
}

// ───────────────────────── ვებ ექაუნთები ─────────────────────────

@Composable
fun WebTab() {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(Store.all(ctx)) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WebAccount?>(null) }
    val supported = remember { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!supported) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Amber)) {
                        Text(
                            "განაახლე „Android System WebView“ და „Chrome“ Play Store-დან — " +
                                "მის გარეშე ექაუნთები ერთმანეთისგან არ გაიყოფა.",
                            Modifier.padding(14.dp), fontSize = 13.sp, color = AmberDark
                        )
                    }
                }
            }
            item {
                Text(
                    "აქ ერთი და იგივე სერვისის რამდენიც გინდა იმდენი ექაუნთი დაამატე — " +
                        "ყველა ერთდროულად შესული დარჩება.",
                    fontSize = 14.sp, color = Color.DarkGray
                )
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        "ჯერ ექაუნთი არ გაქვს. დააჭირე „ექაუნთის დამატება“.",
                        Modifier.padding(vertical = 24.dp), color = Color.Gray
                    )
                }
            }
            items(list, key = { it.id }) { acc ->
                AccountRow(acc, onClick = { WebAccountActivity.open(ctx, acc) }, onMore = { editing = acc })
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("ექაუნთის დამატება") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = Indigo,
            contentColor = Color.White
        )
    }

    if (adding) {
        AddAccountDialog(
            existing = list,
            onDismiss = { adding = false },
            onAdd = { name, url, color, desktop ->
                Store.add(ctx, name, url, color, desktop)
                list = Store.all(ctx)
                adding = false
            }
        )
    }

    editing?.let { acc ->
        EditAccountDialog(
            acc,
            onDismiss = { editing = null },
            onRename = { n -> Store.rename(ctx, acc.id, n); list = Store.all(ctx); editing = null },
            onDelete = {
                Store.remove(ctx, acc.id)
                try {
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
                        ProfileStore.getInstance().deleteProfile(acc.profileName)
                } catch (_: Exception) {}
                list = Store.all(ctx)
                editing = null
            }
        )
    }
}

@Composable
fun AccountRow(acc: WebAccount, onClick: () -> Unit, onMore: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color(acc.color)),
                contentAlignment = Alignment.Center
            ) {
                Text(acc.name.take(1).uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(acc.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(Uri.parse(acc.url).host ?: acc.url, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
            }
            IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, "მეტი") }
        }
    }
}

@Composable
fun AddAccountDialog(
    existing: List<WebAccount>,
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String, color: Long, desktop: Boolean) -> Unit,
) {
    var selected by remember { mutableIntStateOf(0) } // SERVICES.size = სხვა საიტი
    var customUrl by remember { mutableStateOf("https://") }
    var name by remember { mutableStateOf("") }

    fun defaultName(): String {
        val base = if (selected < SERVICES.size) SERVICES[selected].name else "საიტი"
        val n = existing.count { it.name.startsWith(base) } + 1
        return "$base $n"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ახალი ექაუნთი") },
        text = {
            Column {
                SERVICES.forEachIndexed { i, s ->
                    ServiceOption(s.name, Color(s.color), selected == i) { selected = i }
                }
                ServiceOption("სხვა საიტი", Color.Gray, selected == SERVICES.size) { selected = SERVICES.size }
                if (selected == SERVICES.size) {
                    OutlinedTextField(
                        customUrl, { customUrl = it }, Modifier.fillMaxWidth(),
                        singleLine = true, label = { Text("მისამართი") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    name, { name = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("სახელი") }, placeholder = { Text(defaultName()) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val finalName = name.trim().ifEmpty { defaultName() }
                if (selected < SERVICES.size) {
                    val s = SERVICES[selected]
                    onAdd(finalName, s.url, s.color, s.desktop)
                } else {
                    var u = customUrl.trim()
                    if (!u.startsWith("http")) u = "https://$u"
                    onAdd(finalName, u, 0xFF607D8B, false)
                }
            }) { Text("დამატება") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("გაუქმება") } }
    )
}

@Composable
fun ServiceOption(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onClick() }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Box(Modifier.size(14.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 15.sp)
    }
}

@Composable
fun EditAccountDialog(acc: WebAccount, onDismiss: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(acc.name) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (confirmDelete) "წავშალო?" else "ექაუნთი") },
        text = {
            if (confirmDelete) {
                Text("„${acc.name}“ წაიშლება და მისი შესვლის მონაცემებიც. ექაუნთი თავად Facebook-ზე/სერვისზე არ წაიშლება.")
            } else {
                Column {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("სახელი") })
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFC62828))
                        Spacer(Modifier.width(6.dp))
                        Text("ექაუნთის წაშლა", color = Color(0xFFC62828))
                    }
                }
            }
        },
        confirmButton = {
            if (confirmDelete) TextButton(onClick = onDelete) { Text("წაშლა", color = Color(0xFFC62828)) }
            else TextButton(onClick = { onRename(name.trim().ifEmpty { acc.name }) }) { Text("შენახვა") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("გაუქმება") } }
    )
}
