package hu.novamobile

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings

/** Hungarian intents are deliberately expanded to 15 natural phrasings per action. */
object CommandRouter {
    private data class Command(val phrases: List<String>, val run: (Context) -> String)
    private fun phrases(vararg names: String): List<String> {
        val starters = listOf("nyisd meg a ", "nyisd ki a ", "kapcsold be a ", "mutasd a ", "menjunk a ")
        return names.flatMap { name -> starters.map { it + name } }.take(15)
    }
    private fun open(action: String, answer: String) = { c: Context -> launch(c, Intent(action)); answer }
    private fun launch(c: Context, i: Intent) { try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i) } catch (_: Exception) { c.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    private val commands: List<Command> = listOf(
        Command(phrases("wifi", "wi fi", "vezetek nelkuli halozat"), open(Settings.ACTION_WIFI_SETTINGS, "Megnyitom a Wi-Fi beállításokat.")),
        Command(phrases("bluetooth", "bluetooth beallitasok", "bluetooth kapcsolat"), open(Settings.ACTION_BLUETOOTH_SETTINGS, "Megnyitom a Bluetooth beállításokat.")),
        Command(phrases("mobilhalozat", "mobil halozat", "sim beallitasok"), open(Settings.ACTION_NETWORK_OPERATOR_SETTINGS, "Megnyitom a mobilhálózati beállításokat.")),
        Command(phrases("hangero", "hangerobeallitasok", "hang beallitasok"), { c -> val audio=c.getSystemService(Context.AUDIO_SERVICE) as AudioManager; audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); "Feljebb vettem a médiahangot." }),
        Command(phrases("fenyero", "kepernyo fenyereje", "vilagossag"), { c -> if (Settings.System.canWrite(c)) { Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 180); "A fényerőt közepesen magasra állítottam." } else { launch(c, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${c.packageName}"))); "A fényerő módosításához add meg a szükséges engedélyt." } }),
        Command(phrases("akkumulator", "akku", "akkumulator informacio"), { c -> val b=c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager; "Az akkumulátor töltöttsége ${b.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} százalék." }),
        Command(phrases("tarhely", "tarhely beallitasok", "tarhely informacio"), open(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "Megnyitom a tárhely beállításait.")),
        Command(phrases("kijelzo", "kepernyo", "kijelzo beallitasok"), open(Settings.ACTION_DISPLAY_SETTINGS, "Megnyitom a kijelző beállításait.")),
        Command(phrases("ertesitesek", "ertesitesi beallitasok", "jelzesek"), open("android.settings.NOTIFICATION_SETTINGS", "Megnyitom az értesítési beállításokat.")),
        Command(phrases("helymeghatarozas", "helyadatok", "gps"), open(Settings.ACTION_LOCATION_SOURCE_SETTINGS, "Megnyitom a helymeghatározás beállításait.")),
        Command(phrases("hotspot", "mobil hotspot", "wifi hotspot"), open("android.settings.TETHER_SETTINGS", "Megnyitom a hotspot beállításait.")),
        Command(phrases("vpn", "vpn beallitasok", "virtualis maganhalozat"), open(Settings.ACTION_VPN_SETTINGS, "Megnyitom a VPN beállításait.")),
        Command(phrases("rendszerbeallitasok", "beallitasok", "rendszer"), open(Settings.ACTION_SETTINGS, "Megnyitom a rendszerbeállításokat.")),
        app("youtube", "com.google.android.youtube", "YouTube"), app("chrome", "com.android.chrome", "Chrome"), app("kamera", "com.android.camera", "Kamera"),
        maps(), app("telefon", "com.google.android.dialer", "Telefon"), app("uzenetek", "com.google.android.apps.messaging", "Üzenetek"),
        app("gmail", "com.google.android.gm", "Gmail"), app("play aruhaz", "com.android.vending", "Play Áruház"),
        Command(phrases("fajlkezelo", "fajlok", "dokumentumok"), { c -> launch(c, Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)); "Megnyitom a fájlkezelőt." }),
        Command(phrases("szamologep", "kalkulator", "szamitasok"), { c -> launch(c, Intent("android.intent.action.MAIN").addCategory("android.intent.category.APP_CALCULATOR")); "Megnyitom a számológépet." }),
        Command(phrases("ora", "ebreszto", "riaszto"), { c -> launch(c, Intent("android.intent.action.SHOW_ALARMS")); "Megnyitom az órát." }),
        Command(phrases("naptar", "calendar", "esemenyek"), { c -> launch(c, Intent("android.intent.action.MAIN").addCategory("android.intent.category.APP_CALENDAR")); "Megnyitom a naptárat." }),
        app("fotok", "com.google.android.apps.photos", "Fotók")
    )
    private fun app(name:String, packageName:String, label:String): Command = Command(phrases(name, "$name alkalmazas", "$name app"), { c -> val intent=c.packageManager.getLaunchIntentForPackage(packageName); if(intent != null) { launch(c,intent); "Megnyitom: $label." } else "A(z) $label alkalmazás nincs telepítve." })
    private fun maps(): Command = Command(phrases("google maps", "terkep", "terkepek"), { c -> launch(c, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))); "Megnyitom a Google Térképet." })
    fun execute(context:Context, utterance:String):String { val value=MainActivity.normalize(utterance); return commands.firstOrNull { command -> command.phrases.any { phrase -> value.contains(phrase) || phrase.contains(value) } }?.run?.invoke(context) ?: "Ezt a parancsot még nem ismertem fel. Próbáld például: Nova, nyisd meg a Wi-Fi beállításokat." }
}
