package ge.gela.multi

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.widget.Toast

/**
 * "კლონების სივრცე" = Android-ის სამუშაო პროფილი (Managed Profile).
 * ჩვენი აპის ასლი პროფილის შიგნით არის მისი მფლობელი (Profile Owner)
 * და მთავარი პროფილიდან ბრძანებებს cross-profile intent-ებით იღებს.
 */
object Profile {
    const val ACTION_CLONE = "ge.gela.multi.action.CLONE"
    const val ACTION_HIDE = "ge.gela.multi.action.HIDE"
    const val ACTION_REMOVE_PROFILE = "ge.gela.multi.action.REMOVE_PROFILE"
    const val EXTRA_PKG = "pkg"

    fun admin(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)
    fun dpm(ctx: Context): DevicePolicyManager =
        ctx.getSystemService(DevicePolicyManager::class.java)

    /** ვართ თუ არა ახლა სამუშაო პროფილის შიგნით (როგორც მისი მფლობელი) */
    fun isInsideProfile(ctx: Context): Boolean =
        try { dpm(ctx).isProfileOwnerApp(ctx.packageName) } catch (e: Exception) { false }

    /**
     * ჩვენი კლონების სივრცე — ის პროფილი, სადაც „მულტის“ ასლი დგას.
     * (Xiaomi/Honor/Samsung-ის „ორმაგი აპების“ სივრცეებსაც userProfiles აბრუნებს —
     * ისინი ჩვენი არ არის და იქ ბრძანებებს ვერ გავგზავნით.)
     */
    fun workUser(ctx: Context): UserHandle? {
        // ზუსტი ნიშანი: მხოლოდ ჩვენი სივრცე ქმნის ამ „ხიდს“ (cross-profile intent filter)
        if (!hasProfileChannel(ctx)) return null
        val me = Process.myUserHandle()
        val candidates = try {
            ctx.getSystemService(android.content.pm.CrossProfileApps::class.java).targetUserProfiles
        } catch (e: Exception) { emptyList() }
            .ifEmpty { ctx.getSystemService(UserManager::class.java).userProfiles.filter { it != me } }
        // ბრენდების სივრცეები: Samsung Dual Messenger 95, Secure Folder 150+, Xiaomi Dual apps 999
        return candidates.firstOrNull { userId(it) !in KNOWN_OEM_SPACES } ?: candidates.firstOrNull()
    }

    private val KNOWN_OEM_SPACES = setOf(95, 150, 151, 152, 153, 154, 155, 999)

    fun userId(u: UserHandle): Int = u.hashCode() // UserHandle.hashCode() == user id

    fun hasProfileChannel(ctx: Context): Boolean {
        val i = Intent(ACTION_CLONE).addCategory(Intent.CATEGORY_DEFAULT)
        return ctx.packageManager.queryIntentActivities(i, 0)
            .any { it.activityInfo.packageName != ctx.packageName }
    }

    /** დიაგნოსტიკა — ეკრანის ბოლოს პატარა ტექსტად */
    fun debugInfo(ctx: Context): String {
        val me = Process.myUserHandle()
        val all = try { ctx.getSystemService(UserManager::class.java).userProfiles.map { userId(it) } } catch (e: Exception) { emptyList() }
        val targets = try {
            ctx.getSystemService(android.content.pm.CrossProfileApps::class.java).targetUserProfiles.map { userId(it) }
        } catch (e: Exception) { emptyList() }
        return "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · მე=${userId(me)} · " +
            "პროფილები=$all · ასლები=$targets · ხიდი=${hasProfileChannel(ctx)}"
    }

    /** ტელეფონზე სხვა (არა ჩვენი) პროფილი/სივრცე თუ არის */
    fun otherProfiles(ctx: Context): List<UserHandle> {
        val me = Process.myUserHandle()
        val ours = workUser(ctx)
        return ctx.getSystemService(UserManager::class.java).userProfiles
            .filter { it != me && it != ours }
    }

    fun isProfilePaused(ctx: Context, user: UserHandle): Boolean =
        try { ctx.getSystemService(UserManager::class.java).isQuietModeEnabled(user) } catch (e: Exception) { false }

    fun provisioningIntent(ctx: Context): Intent =
        Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, admin(ctx))
            // კამერა, გალერეა და სხვა სისტემური აპები კლონებშიც ხელმისაწვდომი დარჩეს (ბანკებს სჭირდებათ)
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, true)
        }

    /** სრულდება პროფილის შიგნით, შექმნისთანავე */
    fun setupInsideProfile(ctx: Context) {
        if (!isInsideProfile(ctx)) return
        val d = dpm(ctx)
        val a = admin(ctx)
        try { d.setProfileName(a, "მულტი") } catch (_: Exception) {}
        try { d.setProfileEnabled(a) } catch (_: Exception) {}
        try {
            d.clearCrossProfileIntentFilters(a)
            listOf(ACTION_CLONE, ACTION_HIDE, ACTION_REMOVE_PROFILE).forEach { act ->
                val f = IntentFilter(act).apply { addCategory(Intent.CATEGORY_DEFAULT) }
                d.addCrossProfileIntentFilter(a, f, DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED)
            }
        } catch (_: Exception) {}
        // პროფილში ჩვენი აპის მეორე აიკონი არ გვინდა
        try {
            ctx.packageManager.setComponentEnabledSetting(
                ComponentName(ctx, MainActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {}
    }

    /** მთავარი პროფილიდან ბრძანების გაგზავნა სამუშაო პროფილში */
    fun sendToProfile(ctx: Context, action: String, pkg: String? = null): Boolean {
        val i = Intent(action).addCategory(Intent.CATEGORY_DEFAULT)
        if (pkg != null) i.putExtra(EXTRA_PKG, pkg)
        val target = ctx.packageManager.queryIntentActivities(i, 0)
            .firstOrNull { it.activityInfo.packageName != ctx.packageName }
            ?: return false
        i.component = ComponentName(target.activityInfo.packageName, target.activityInfo.name)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(i); true } catch (e: Exception) { false }
    }
}

class AdminReceiver : DeviceAdminReceiver() {
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Profile.setupInsideProfile(context)
    }
}

class ProvisioningSuccessActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Profile.setupInsideProfile(this)
        finish()
    }
}

class ProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val r = Intent()
        if (Build.VERSION.SDK_INT >= 29) {
            r.putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE
            )
        }
        setResult(RESULT_OK, r)
        finish()
    }
}

class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Profile.setupInsideProfile(this)
        setResult(RESULT_OK)
        finish()
    }
}

/** მუშაობს სამუშაო პროფილის შიგნით: აპის კლონირება / დამალვა / სივრცის წაშლა */
class ProfileCommandActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Profile.isInsideProfile(this)) { finish(); return }
        Profile.setupInsideProfile(this)
        val d = Profile.dpm(this)
        val a = Profile.admin(this)
        val pkg = intent.getStringExtra(Profile.EXTRA_PKG)
        when (intent.action) {
            Profile.ACTION_CLONE -> if (pkg != null) clone(d, a, pkg)
            Profile.ACTION_HIDE -> if (pkg != null) {
                try { d.setApplicationHidden(a, pkg, true); toast("კლონი წაიშალა") }
                catch (e: Exception) { toast("ვერ წაიშალა: ${e.message}") }
            }
            Profile.ACTION_REMOVE_PROFILE -> {
                try { d.wipeData(0) } catch (e: Exception) { toast("ვერ წაიშალა: ${e.message}") }
            }
        }
        finish()
    }

    private fun clone(d: DevicePolicyManager, a: ComponentName, pkg: String) {
        // თუ ადრე დამალული იყო — უბრალოდ ვაჩენთ
        try { if (d.isApplicationHidden(a, pkg)) d.setApplicationHidden(a, pkg, false) } catch (_: Exception) {}
        val already = try { packageManager.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }
        if (already) { toast("კლონი მზადაა"); return }

        var ok = try { d.installExistingPackage(a, pkg) } catch (e: Exception) { false }
        if (!ok) ok = try { d.enableSystemApp(a, pkg); true } catch (e: Exception) { false }
        if (ok) {
            toast("კლონი შეიქმნა ✓")
        } else {
            // ბოლო ვარიანტი: Play Store-ს ვხსნით კლონების სივრცეში
            toast("დააყენე Play Store-დან კლონების სივრცეში")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {}
        }
    }

    private fun toast(s: String) = Toast.makeText(applicationContext, s, Toast.LENGTH_LONG).show()
}
