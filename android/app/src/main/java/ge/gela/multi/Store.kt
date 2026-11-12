package ge.gela.multi

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Service(val name: String, val url: String, val color: Long, val desktop: Boolean = false)

val SERVICES = listOf(
    Service("Facebook", "https://m.facebook.com/", 0xFF1877F2),
    Service("Messenger", "https://m.facebook.com/messages/", 0xFF0084FF),
    Service("Instagram", "https://www.instagram.com/", 0xFFE1306C),
    Service("WhatsApp", "https://web.whatsapp.com/", 0xFF25D366, desktop = true),
    Service("TikTok", "https://www.tiktok.com/", 0xFF111111),
)

data class WebAccount(
    val id: String,
    val name: String,
    val url: String,
    val color: Long,
    val desktop: Boolean,
) {
    val profileName get() = "acc_$id"
}

object Store {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("accounts", Context.MODE_PRIVATE)

    fun all(ctx: Context): List<WebAccount> {
        val raw = prefs(ctx).getString("list", "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            WebAccount(
                o.getString("id"), o.getString("name"), o.getString("url"),
                o.optLong("color", 0xFF3949AB), o.optBoolean("desktop", false)
            )
        }
    }

    fun get(ctx: Context, id: String) = all(ctx).firstOrNull { it.id == id }

    private fun save(ctx: Context, list: List<WebAccount>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)
                .put("color", it.color).put("desktop", it.desktop))
        }
        prefs(ctx).edit().putString("list", arr.toString()).apply()
    }

    fun add(ctx: Context, name: String, url: String, color: Long, desktop: Boolean): WebAccount {
        val acc = WebAccount(UUID.randomUUID().toString().take(8), name, url, color, desktop)
        save(ctx, all(ctx) + acc)
        return acc
    }

    fun rename(ctx: Context, id: String, name: String) =
        save(ctx, all(ctx).map { if (it.id == id) it.copy(name = name) else it })

    fun remove(ctx: Context, id: String) = save(ctx, all(ctx).filter { it.id != id })
}
