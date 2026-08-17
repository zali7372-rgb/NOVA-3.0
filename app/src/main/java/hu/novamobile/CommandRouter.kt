package hu.novamobile

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings

object CommandRouter {

    private data class AppCommand(
        val names: List<String>,
        val packageName: String,
        val label: String
    )

    private val apps = listOf(

        AppCommand(
            listOf("youtube", "jutyub", "youtube app", "videok"),
            "com.google.android.youtube",
            "YouTube"
        ),

        AppCommand(
            listOf("chrome", "google chrome", "bongeszo", "internet"),
            "com.android.chrome",
            "Chrome"
        ),

        AppCommand(
            listOf("kamera", "fenykepezogep", "foto"),
            "com.google.android.camera2",
            "Kamera"
        ),

        AppCommand(
            listOf("discord", "diszkord"),
            "com.discord",
            "Discord"
        ),

        AppCommand(
            listOf("instagram", "insta"),
            "com.instagram.android",
            "Instagram"
        ),

        AppCommand(
            listOf("tiktok", "tik tok"),
            "com.zhiliaoapp.musically",
            "TikTok"
        ),

        AppCommand(
            listOf("facebook", "fészbuk"),
            "com.facebook.katana",
            "Facebook"
        ),

        AppCommand(
            listOf("messenger", "messenger uzenetek"),
            "com.facebook.orca",
            "Messenger"
        ),

        AppCommand(
            listOf("whatsapp", "vatsapp"),
            "com.whatsapp",
            "WhatsApp"
        ),

        AppCommand(
            listOf("telegram", "telegrám"),
            "org.telegram.messenger",
            "Telegram"
        ),

        AppCommand(
            listOf("snapchat", "snap"),
            "com.snapchat.android",
            "Snapchat"
        ),

        AppCommand(
            listOf("spotify", "zene", "zenet"),
            "com.spotify.music",
            "Spotify"
        ),

        AppCommand(
            listOf("netflix"),
            "com.netflix.mediaclient",
            "Netflix"
        ),

        AppCommand(
            listOf("twitch"),
            "tv.twitch.android.app",
            "Twitch"
        ),

        AppCommand(
            listOf("steam"),
            "com.valvesoftware.android.steam.community",
            "Steam"
        ),

        AppCommand(
            listOf("vlc"),
            "org.videolan.vlc",
            "VLC"
        ),

        AppCommand(
            listOf("waze"),
            "com.waze",
            "Waze"
        ),

        AppCommand(
            listOf("bolt"),
            "ee.mtakso.client",
            "Bolt"
        ),

        AppCommand(
            listOf("uber"),
            "com.ubercab",
            "Uber"
        ),

        AppCommand(
            listOf("gmail", "gmail alkalmazas", "levelezés"),
            "com.google.android.gm",
            "Gmail"
        ),

        AppCommand(
            listOf("drive", "google drive"),
            "com.google.android.apps.docs",
            "Google Drive"
        ),

        AppCommand(
            listOf("revolut"),
            "com.revolut.revolut",
            "Revolut"
        ),

        AppCommand(
            listOf("amazon"),
            "com.amazon.mShop.android.shopping",
            "Amazon"
        ),

        AppCommand(
            listOf("ebay"),
            "com.ebay.mobile",
            "eBay"
        ),

        AppCommand(
            listOf("reddit"),
            "com.reddit.frontpage",
            "Reddit"
        ),

        AppCommand(
            listOf("linkedin"),
            "com.linkedin.android",
            "LinkedIn"
        ),

        AppCommand(
            listOf("pinterest"),
            "com.pinterest",
            "Pinterest"
        ),

        AppCommand(
            listOf("teams", "microsoft teams"),
            "com.microsoft.teams",
            "Microsoft Teams"
        ),

        AppCommand(
            listOf("zoom"),
            "us.zoom.videomeetings",
            "Zoom"
        ),

        AppCommand(
            listOf("meet", "google meet"),
            "com.google.android.apps.tachyon",
            "Google Meet"
        ),

        AppCommand(
            listOf("fotok", "google fotok", "kepek"),
            "com.google.android.apps.photos",
            "Google Fotók"
        )
    )

    fun execute(
        context: Context,
        utterance: String
    ): String {

        val text =
            MainActivity.normalize(utterance)

        /*
         * APP INDÍTÁS
         */

        val app =
            apps.firstOrNull { app ->

                app.names.any { name ->

                    containsFlexible(
                        text,
                        name
                    )
                }
            }

        if (app != null) {

            return launchApp(
                context,
                app
            )
        }

        /*
         * WIFI
         */

        if (
            containsAny(
                text,
                "wifi",
                "wi fi",
                "wlan",
                "vezetek nelkuli halozat"
            )
        ) {

            return openSettings(
                context,
                Settings.ACTION_WIFI_SETTINGS,
                "Megnyitom a Wi-Fi beállításokat."
            )
        }

        /*
         * BLUETOOTH
         */

        if (
            containsAny(
                text,
                "bluetooth",
                "blutusz"
            )
        ) {

            return openSettings(
                context,
                Settings.ACTION_BLUETOOTH_SETTINGS,
                "Megnyitom a Bluetooth beállításokat."
            )
        }

        /*
         * MOBILHÁLÓZAT
         */

        if (
            containsAny(
                text,
                "mobilhalozat",
                "mobil halozat",
                "sim",
                "mobilnet",
                "mobil internet"
            )
        ) {

            return openSettings(
                context,
                Settings.ACTION_NETWORK_OPERATOR_SETTINGS,
                "Megnyitom a mobilhálózati beállításokat."
            )
        }

        /*
         * HANGERŐ
         */

        if (
            containsAny(
                text,
                "hangero",
                "hangosits",
                "hangosabb",
                "feljebb a hangerot"
            )
        ) {

            val audio =
                context.getSystemService(
                    Context.AUDIO_SERVICE
                ) as AudioManager

            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI
            )

            return "Feljebb vettem a hangerőt."
        }

        /*
         * FÉNYERŐ
         */

        if (
            containsAny(
                text,
                "fenyero",
                "kepernyo fenyereje",
                "vilagossag",
                "fenyesebb"
            )
        ) {

            if (
                Settings.System.canWrite(context)
            ) {

                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    180
                )

                return "Feljebb vettem a fényerőt."
            }

            openSettings(
                context,
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse(
                        "package:${context.packageName}"
                    )
                ),
                "A fényerő módosításához engedély szükséges."
            )

            return "A fényerő módosításához engedély szükséges."
        }

        /*
         * AKKUMULÁTOR
         */

        if (
            containsAny(
                text,
                "akkumulator",
                "akku",
                "toltes",
                "hany szazalek"
            )
        ) {

            val battery =
                context.getSystemService(
                    Context.BATTERY_SERVICE
                ) as BatteryManager

            val level =
                battery.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CAPACITY
                )

            return "Az akkumulátor töltöttsége $level százalék."
        }

        /*
         * KIJELZŐ
         */

        if (
            containsAny(
                text,
                "kijelzo",
                "kepernyo beallitas",
                "kepernyo"
            )
        ) {

            return openSettings(
                context,
                Settings.ACTION_DISPLAY_SETTINGS,
                "Megnyitom a kijelző beállításait."
            )
        }

        /*
         * ÉRTESÍTÉSEK
         */

        if (
            containsAny(
                text,
                "ertesites",
                "ertesitesek",
                "ertesitesi beallitas"
            )
        ) {

            return openSettings(
                context,
                Intent(
                    "android.settings.NOTIFICATION_SETTINGS"
                ),
                "Megnyitom az értesítési beállításokat."
            )
        }

        /*
         * HELYMEGHATÁROZÁS
         */

        if (
            containsAny(
                text,
                "gps",
                "helymeghatarozas",
                "helyadat",
                "helyzet"
            )
        ) {

            return openSettings(
                context,
                Settings.ACTION_LOCATION_SOURCE_SETTINGS,
                "Megnyitom a helymeghatározás beállításait."
            )
        }

        /*
         * HOTSPOT
         */

        if (
            containsAny(
                text,
                "hotspot",
                "internet megosztas",
                "wifi megosztas"
            )
        ) {

            return openSettings(
                context,
                Intent(
                    "android.settings.TETHER_SETTINGS"
                ),
                "Megnyitom a hotspot beállításait."
            )
        }

        /*
         * VPN
         */

        if (
            containsAny(
                text,
                "vpn",
                "virtualis maganhalozat"
            )
        ) {

            return openSettings(
                context,
                Settings.ACTION_VPN_SETTINGS,
                "Megnyitom a VPN beállításait."
            )
        }

        /*
         * TÁRHELY
         */

        if (
            containsAny(
                text,
                "tarhely",
                "tarhelyem",
                "memoria",
                "szabad hely"
            )
        ) {

            return openSettings(
                context,
                Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
                "Megnyitom a tárhely beállításait."
            )
        }

        /*
         * ÁLTALÁNOS BEÁLLÍTÁSOK
         */

        if (
            containsAny(
                text,
                "beallitasok",
                "rendszerbeallitas",
                "telefon beallitas"
            )
        ) {

            return openSettings(
                context,
                Settings.ACTION_SETTINGS,
                "Megnyitom a beállításokat."
            )
        }

        /*
         * FÁJLOK
         */

        if (
            containsAny(
                text,
                "fajlok",
                "fajlkezelo",
                "dokumentumok",
                "fileok"
            )
        ) {

            val intent =
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "*/*"
                    addCategory(
                        Intent.CATEGORY_OPENABLE
                    )
                }

            launch(context, intent)

            return "Megnyitom a fájlokat."
        }

        /*
         * ÓRA / ÉBRESZTŐ
         */

        if (
            containsAny(
                text,
                "ora",
                "ebreszto",
                "riaszto",
                "alarm"
            )
        ) {

            launch(
                context,
                Intent(
                    "android.intent.action.SHOW_ALARMS"
                )
            )

            return "Megnyitom az ébresztőt."
        }

        /*
         * NAPTÁR
         */

        if (
            containsAny(
                text,
                "naptar",
                "calendar",
                "esemenyek"
            )
        ) {

            launch(
                context,
                Intent(
                    Intent.ACTION_MAIN
                ).addCategory(
                    "android.intent.category.APP_CALENDAR"
                )
            )

            return "Megnyitom a naptárat."
        }

        /*
         * SZÁMOLÓGÉP
         */

        if (
            containsAny(
                text,
                "szamologep",
                "kalkulator",
                "szamolas"
            )
        ) {

            launch(
                context,
                Intent(
                    Intent.ACTION_MAIN
                ).addCategory(
                    "android.intent.category.APP_CALCULATOR"
                )
            )

            return "Megnyitom a számológépet."
        }

        /*
         * TÉRKÉP
         */

        if (
            containsAny(
                text,
                "terkep",
                "google maps",
                "maps",
                "navigacio"
            )
        ) {

            launch(
                context,
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q=")
                )
            )

            return "Megnyitom a térképet."
        }

        return "Ezt a parancsot még nem ismerem fel."
    }

    private fun launchApp(
        context: Context,
        app: AppCommand
    ): String {

        val intent =
            context.packageManager
                .getLaunchIntentForPackage(
                    app.packageName
                )

        if (intent == null) {

            return "A ${app.label} alkalmazás nincs telepítve."
        }

        launch(
            context,
            intent
        )

        return "Megnyitom a ${app.label} alkalmazást."
    }

    private fun containsAny(
        text: String,
        vararg words: String
    ): Boolean {

        return words.any { word ->

            containsFlexible(
                text,
                word
            )
        }
    }

    private fun containsFlexible(
        text: String,
        target: String
    ): Boolean {

        val cleanTarget =
            MainActivity.normalize(target)

        if (text.contains(cleanTarget)) {
            return true
        }

        val words =
            text.split(" ")

        return words.any { word ->

            if (word.length < 4) {
                false
            } else {

                levenshtein(
                    word,
                    cleanTarget
                ) <= 1
            }
        }
    }

    private fun openSettings(
        context: Context,
        action: String,
        fallbackMessage: String
    ): String {

        launch(
            context,
            Intent(action)
        )

        return fallbackMessage
    }

    private fun openSettings(
        context: Context,
        intent: Intent,
        fallbackMessage: String
    ): String {

        launch(
            context,
            intent
        )

        return fallbackMessage
    }

    private fun launch(
        context: Context,
        intent: Intent
    ) {

        try {

            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )

            context.startActivity(intent)

        } catch (_: Exception) {

            try {

                context.startActivity(
                    Intent(
                        Settings.ACTION_SETTINGS
                    ).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                )

            } catch (_: Exception) {
            }
        }
    }

    private fun levenshtein(
        a: String,
        b: String
    ): Int {

        if (a == b) return 0

        if (a.isEmpty()) return b.length

        if (b.isEmpty()) return a.length

        val costs =
            IntArray(b.length + 1) {
                it
            }

        for (i in a.indices) {

            var previous = i

            costs[0] = i + 1

            for (j in b.indices) {

                val current =
                    costs[j + 1]

                costs[j + 1] =
                    minOf(
                        costs[j + 1] + 1,
                        costs[j] + 1,
                        previous +
                            if (a[i] == b[j]) 0 else 1
                    )

                previous = current
            }
        }

        return costs[b.length]
    }
}
