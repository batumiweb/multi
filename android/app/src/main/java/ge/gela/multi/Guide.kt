package ge.gela.multi

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** ტელეფონის ბრენდი — თითოეულს თავისი ჩაშენებული „ორმაგი აპები“ აქვს */
enum class Brand(val title: String) {
    SAMSUNG("Samsung"), XIAOMI("Xiaomi / Redmi / POCO"), OPPO("OPPO / Realme / OnePlus"),
    VIVO("Vivo"), HUAWEI("Huawei / Honor"), OTHER("სხვა")
}

fun detectBrand(): Brand {
    val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
    return when {
        "samsung" in m -> Brand.SAMSUNG
        "xiaomi" in m || "redmi" in m || "poco" in m -> Brand.XIAOMI
        "oppo" in m || "realme" in m || "oneplus" in m -> Brand.OPPO
        "vivo" in m || "iqoo" in m -> Brand.VIVO
        "huawei" in m || "honor" in m -> Brand.HUAWEI
        else -> Brand.OTHER
    }
}

fun isInstalled(ctx: Context, pkg: String) =
    try { ctx.packageManager.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }

/** ცდილობს რიგრიგობით გახსნას — პირველი რაც იმუშავებს */
fun openFirst(ctx: Context, vararg intents: Intent) {
    for (i in intents) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) {}
    }
    try { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
}

fun launchApp(ctx: Context, pkg: String): Intent? = ctx.packageManager.getLaunchIntentForPackage(pkg)
fun playStore(pkg: String) = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))

data class Way(
    val title: String,
    val adds: String,          // რამდენ ასლს/ექაუნთს ამატებს
    val steps: String,
    val button: String? = null,
    val action: ((Context) -> Unit)? = null,
)

fun waysFor(ctx: Context, brand: Brand): List<Way> {
    val list = mutableListOf<Way>()
    val sdk = Build.VERSION.SDK_INT

    list += Way(
        "1. „მულტის“ კლონი",
        "+1 ასლი ნებისმიერ აპზე (ბანკიც)",
        "„აპების კლონი“ ჩანართში შექმენი სივრცე და დააჭირე „კლონი“ საჭირო აპზე."
    )

    if (sdk >= 35) list += Way(
        "2. პირადი სივრცე (Private Space)",
        "+1 ასლი ნებისმიერ აპზე (ბანკიც)",
        "პარამეტრები → უსაფრთხოება და კონფიდენციალურობა → პირადი სივრცე → შექმნა. " +
            "შემდეგ აპების სიის ბოლოში გამოჩნდება საკეტიანი სივრცე — იქიდან Play Store-ით დააყენე ბანკი/აპი.",
        "პარამეტრების გახსნა"
    ) { c ->
        openFirst(c, Intent("android.settings.PRIVATE_SPACE_SETTINGS"), Intent(Settings.ACTION_SECURITY_SETTINGS))
    }

    when (brand) {
        Brand.SAMSUNG -> {
            list += Way(
                "Samsung Secure Folder (დაცული საქაღალდე)",
                "+1 ასლი ნებისმიერ აპზე (ბანკიც)",
                "პარამეტრები → უსაფრთხოება და კონფიდენციალურობა → მეტი უსაფრთხოების პარამეტრი → Secure Folder. " +
                    "შიგნით „+ აპების დამატება“.",
                "Secure Folder-ის გახსნა"
            ) { c ->
                openFirst(c, *listOfNotNull(launchApp(c, "com.samsung.knox.securefolder")).toTypedArray(),
                    Intent(Settings.ACTION_SECURITY_SETTINGS))
            }
            list += Way(
                "Samsung Dual Messenger",
                "+1 ასლი: WhatsApp, Facebook, Messenger, Viber, Telegram…",
                "პარამეტრები → დამატებითი ფუნქციები (Advanced features) → Dual Messenger → ჩართე საჭირო აპზე.",
                "პარამეტრების გახსნა"
            ) { c -> openFirst(c, Intent(Settings.ACTION_SETTINGS)) }
        }
        Brand.XIAOMI -> {
            list += Way(
                "Xiaomi „ორმაგი აპები“ (Dual apps)",
                "+1 ასლი: WhatsApp, Facebook, Messenger და სხვ.",
                "პარამეტრები → აპები → ორმაგი აპები (Dual apps) → ჩართე საჭირო აპზე.",
                "პარამეტრების გახსნა"
            ) { c ->
                openFirst(c,
                    Intent().setClassName("com.miui.securitycore", "com.miui.securityspace.ui.activity.DualAppsActivity"),
                    Intent(Settings.ACTION_SETTINGS))
            }
            list += Way(
                "Xiaomi „მეორე სივრცე“ (Second space)",
                "+1 ასლი ნებისმიერ აპზე (ბანკიც), ცალკე ტელეფონივით",
                "პარამეტრები → სპეციალური ფუნქციები → მეორე სივრცე. სივრცეებს შორის გადართვა ცალკე პაროლით/თითით.",
                "პარამეტრების გახსნა"
            ) { c -> openFirst(c, Intent(Settings.ACTION_SETTINGS)) }
        }
        Brand.OPPO -> list += Way(
            "App Cloner / Parallel Apps",
            "+1 ასლი: WhatsApp, Facebook, Messenger და სხვ.",
            "პარამეტრები → აპები → App Cloner (OnePlus-ზე: Parallel Apps) → ჩართე საჭირო აპზე.",
            "პარამეტრების გახსნა"
        ) { c -> openFirst(c, Intent(Settings.ACTION_SETTINGS)) }
        Brand.VIVO -> list += Way(
            "Vivo App Clone",
            "+1 ასლი: WhatsApp, Facebook, Messenger და სხვ.",
            "პარამეტრები → აპები → App Clone → ჩართე საჭირო აპზე.",
            "პარამეტრების გახსნა"
        ) { c -> openFirst(c, Intent(Settings.ACTION_SETTINGS)) }
        Brand.HUAWEI -> list += Way(
            "App Twin",
            "+1 ასლი: WhatsApp, Facebook, Messenger",
            "პარამეტრები → აპები → App Twin → ჩართე საჭირო აპზე.",
            "პარამეტრების გახსნა"
        ) { c -> openFirst(c, Intent(Settings.ACTION_SETTINGS)) }
        Brand.OTHER -> {}
    }
    return list
}

data class AppTip(val title: String, val text: String, val button: String? = null, val action: ((Context) -> Unit)? = null)

fun appTips(): List<AppTip> = listOf(
    AppTip(
        "WhatsApp — 2 ექაუნთი ერთ აპში",
        "WhatsApp → ⋮ → პარამეტრები → ექაუნთის გვერდით ისარი → „ექაუნთის დამატება“. ყოველ ექაუნთს თავისი ნომერი სჭირდება.",
        "WhatsApp-ის გახსნა"
    ) { c -> openFirst(c, *listOfNotNull(launchApp(c, "com.whatsapp")).toTypedArray(), playStore("com.whatsapp")) },
    AppTip(
        "WhatsApp Business — კიდევ 2",
        "ცალკე, უფასო აპია ოფიციალურად. მასაც აქვს „ექაუნთის დამატება“. მაღაზიისთვის საუკეთესოა.",
        "WhatsApp Business"
    ) { c -> openFirst(c, *listOfNotNull(launchApp(c, "com.whatsapp.w4b")).toTypedArray(), playStore("com.whatsapp.w4b")) },
    AppTip(
        "Facebook / Messenger",
        "აპში: მენიუ → შენი სახელის გვერდით ისარი → „სხვა პროფილის დამატება“. მე-3, მე-4… ექაუნთი — „ვებ ექაუნთების“ ჩანართში, შეუზღუდავად.",
    ),
    AppTip(
        "ბანკი",
        "ბანკის აპები ოფიციალურ სივრცეებში მუშაობს: „მულტის“ კლონი, პირადი სივრცე, Secure Folder / მეორე სივრცე. " +
            "Parallel Space-ის ტიპის აპებში ბანკები სპეციალურად ბლოკავენ — ეს მათი დაცვაა.",
    ),
)

@Composable
fun GuideTab() {
    val ctx = LocalContext.current
    val brand = remember { detectBrand() }
    val ways = remember { waysFor(ctx, brand) }
    val bankCopies = 1 + ways.count { "ბანკიც" in it.adds }
    val msgCopies = bankCopies + ways.count { "WhatsApp" in it.adds }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = IndigoLight)) {
                Column(Modifier.padding(16.dp)) {
                    Text("შენი ტელეფონი: ${brand.title}, Android ${Build.VERSION.RELEASE}",
                        fontWeight = FontWeight.Bold, color = IndigoDark)
                    Spacer(Modifier.height(8.dp))
                    Text("ბანკი და ნებისმიერი აპი: $bankCopies ასლამდე", fontSize = 15.sp)
                    Text("WhatsApp / Messenger / Facebook აპი: $msgCopies ასლამდე", fontSize = 15.sp)
                    Text("WhatsApp (+ Business, თითოში 2 ექაუნთი): ${msgCopies * 2 * 2}-მდე", fontSize = 15.sp)
                    Text("Facebook / Messenger ვებით: შეუზღუდავი", fontSize = 15.sp)
                }
            }
        }
        item { SectionTitle("ასლების გზები ამ ტელეფონზე") }
        items(ways) { w ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(w.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(w.adds, color = Indigo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(w.steps, fontSize = 14.sp, color = Color.DarkGray)
                    if (w.button != null && w.action != null) {
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = { w.action.invoke(ctx) }) { Text(w.button) }
                    }
                }
            }
        }
        item { SectionTitle("აპების ჩაშენებული ექაუნთები") }
        items(appTips()) { t ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(t.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(t.text, fontSize = 14.sp, color = Color.DarkGray)
                    if (t.button != null && t.action != null) {
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = { t.action.invoke(ctx) }) { Text(t.button) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
