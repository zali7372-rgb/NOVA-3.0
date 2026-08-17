package hu.novamobile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings

object CommandRouter {

    private data class Command(
        val keywords: List<String>,
        val aliases: List<String> = emptyList(),
        val action: (Context) -> String
    )

    /*
     * Műveleti szavak.
     * Nem egyetlen konkrét mondatot keresünk,
     * hanem az emberi megfogalmazás lényegét.
     */

    private val openWords = listOf(
        "nyisd",
        "nyisd meg",
        "nyisd ki",
        "inditsd",
        "inditsd el",
        "inditsd be",
        "induljon",
        "indits",
        "menj",
        "menj a",
        "lepj",
        "lepj be",
        "mutasd",
        "hozd elo",
        "told be",
        "nyomd meg",
        "kapcsold be",
        "start"
    )

    private fun containsAny(
        text: String,
        words: List<String>
    ): Boolean {
        return words.any {
            text.contains(it)
        }
    }

    private fun openApp(
        packageNames: List<String>,
        label: String
    ): (Context) -> String {

        return { context ->

            var intent: Intent? = null

            for (packageName in packageNames) {

                intent =
                    context.packageManager
                        .getLaunchIntentForPackage(
                            packageName
                        )

                if (intent != null) break
            }

            if (intent != null) {

                try {

                    intent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )

                    context.startActivity(intent)

                    "Megnyitom: $label."

                } catch (_: Exception) {

                    "Nem sikerült megnyitnom: $label."
                }

            } else {

                "A(z) $label alkalmazás nincs telepítve."
            }
        }
    }

    private fun openSettings(
        action: String,
        message: String
    ): (Context) -> String {

        return { context ->

            try {

                context.startActivity(
                    Intent(action).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                        )
                    }
                )

                message

            } catch (_: Exception) {

                try {

                    context.startActivity(
                        Intent(
                            Settings.ACTION_SETTINGS
                        ).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                        }
                    )

                    "Megnyitottam a rendszerbeállításokat."

                } catch (_: Exception) {

                    "Nem sikerült megnyitnom a beállításokat."
                }
            }
        }
    }

    private val commands =
        mutableListOf<Command>().apply {

            /*
             * RENDSZERBEÁLLÍTÁSOK
             */

            add(
                Command(
                    keywords = listOf(
                        "wifi",
                        "wi fi",
                        "vezetek nelkuli",
                        "vezetek nelkuli halozat"
                    ),
                    aliases = listOf(
                        "wifi beallitas",
                        "wifi beallitasok"
                    ),
                    action = openSettings(
                        Settings.ACTION_WIFI_SETTINGS,
                        "Megnyitom a Wi-Fi beállításokat."
                    )
                )
            )

            add(
                Command(
                    keywords = listOf(
                        "bluetooth",
                        "blu tooth"
                    ),
                    aliases = listOf(
                        "bluetooth beallitas",
                        "bluetooth beallitasok"
                    ),
                    action = openSettings(
                        Settings.ACTION_BLUETOOTH_SETTINGS,
                        "Megnyitom a Bluetooth beállításokat."
                    )
                )
            )

            add(
                Command(
                    keywords = listOf(
                        "mobilnet",
                        "mobil net",
                        "mobilhalozat",
                        "mobil halozat",
                        "mobil adat",
                        "mobiladat"
                    ),
                    action = openSettings(
                        Settings.ACTION_NETWORK_OPERATOR_SETTINGS,
                        "Megnyitom a mobilhálózati beállításokat."
                    )
                )
            )

            add(
                Command(
                    keywords = listOf(
                        "kijelzo",
                        "kepernyo",
                        "display"
                    ),
                    action = openSettings(
                        Settings.ACTION_DISPLAY_SETTINGS,
                        "Megnyitom a kijelző beállításait."
                    )
                )
            )

            add(
                Command(
                    keywords = listOf(
                        "ertesites",
                        "ertesitesek",
                        "jelzesek"
                    ),
                    action = openSettings(
                        "android.settings.NOTIFICATION_SETTINGS",
                        "Megnyitom az értesítési beállításokat."
                    )
                )
            )

            add(
                Command(
                    keywords = listOf(
                        "hely",
                        "helymeghatarozas",
                        "gps",
                        "lokacio"
                    ),
                    action = openSettings(
                        Settings.ACTION_LOCATION_SOURCE_SETTINGS,
                        "Megnyitom a helymeghatározás beállításait."
                    )
                )
            )

            add(
                Command(
                    keywords = listOf(
                        "hotspot",
                        "internetmegosztas"
                    ),
                    action = openSettings(
                        "android.settings.TETHER_SETTINGS",
                        "Megnyitom a hotspot beállításait."
                    )
                )
            )

            add(
                Command(
                    keywords = listOf(
                        "vpn",
                        "virtualis maganhálózat"
                    ),
                    action = openSettings(
                        Settings.ACTION_VPN_SETTINGS,
                        "Megnyitom a VPN beállításait."
                    )
                )
            )

            add(
                Command(
                    keywords = listOf(
                        "tarhely",
                        "tárhely"
                    ),
                    action = openSettings(
                        Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
                        "Megnyitom a tárhely beállításait."
                    )
                )
            )

            add(
                Command(
                    keywords = listOf(
                        "rendszerbeallitas",
                        "rendszer beallitas",
                        "beallitasok"
                    ),
                    action = openSettings(
                        Settings.ACTION_SETTINGS,
                        "Megnyitom a rendszerbeállításokat."
                    )
                )
            )

            /*
             * AKKUMULÁTOR
             */

            add(
                Command(
                    keywords = listOf(
                        "akku",
                        "akkumulator",
                        "toltottsag",
                        "hany szazalek"
                    ),
                    action = { context ->

                        val battery =
                            context.getSystemService(
                                Context.BATTERY_SERVICE
                            ) as BatteryManager

                        val percentage =
                            battery.getIntProperty(
                                BatteryManager
                                    .BATTERY_PROPERTY_CAPACITY
                            )

                        "Az akkumulátor töltöttsége $percentage százalék."
                    }
                )
            )

            /*
             * HANGERŐ
             */

            add(
                Command(
                    keywords = listOf(
                        "hangero",
                        "hang eros",
                        "hangosits",
                        "hangosabb"
                    ),
                    action = { context ->

                        val audio =
                            context.getSystemService(
                                Context.AUDIO_SERVICE
                            ) as AudioManager

                        audio.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_RAISE,
                            AudioManager.FLAG_SHOW_UI
                        )

                        "Feljebb vettem a hangerőt."
                    }
                )
            )

            /*
             * FÉNYERŐ
             */

            add(
                Command(
                    keywords = listOf(
                        "fenyero",
                        "fenyereje",
                        "vilagossag",
                        "kepernyo fenyereje"
                    ),
                    action = { context ->

                        if (
                            Settings.System.canWrite(context)
                        ) {

                            Settings.System.putInt(
                                context.contentResolver,
                                Settings.System.SCREEN_BRIGHTNESS,
                                180
                            )

                            "Beállítottam a fényerőt."

                        } else {

                            try {

                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                        Uri.parse(
                                            "package:${context.packageName}"
                                        )
                                    )
                                )

                                "Engedély szükséges a fényerő módosításához."

                            } catch (_: Exception) {

                                "Nem tudom módosítani a fényerőt."
                            }
                        }
                    }
                )
            )

            /*
             * ALKALMAZÁSOK
             */

            addApp(
                "youtube",
                "YouTube",
                "com.google.android.youtube",
                "com.google.android.youtube.tv"
            )

            addApp(
                "chrome",
                "Chrome",
                "com.android.chrome"
            )

            addApp(
                "discord",
                "Discord",
                "com.discord"
            )

            addApp(
                "instagram",
                "Instagram",
                "com.instagram.android"
            )

            addApp(
                "tiktok",
                "TikTok",
                "com.zhiliaoapp.musically"
            )

            addApp(
                "facebook",
                "Facebook",
                "com.facebook.katana"
            )

            addApp(
                "messenger",
                "Messenger",
                "com.facebook.orca"
            )

            addApp(
                "whatsapp",
                "WhatsApp",
                "com.whatsapp"
            )

            addApp(
                "telegram",
                "Telegram",
                "org.telegram.messenger"
            )

            addApp(
                "snapchat",
                "Snapchat",
                "com.snapchat.android"
            )

            addApp(
                "reddit",
                "Reddit",
                "com.reddit.frontpage"
            )

            addApp(
                "spotify",
                "Spotify",
                "com.spotify.music"
            )

            addApp(
                "steam",
                "Steam",
                "com.valvesoftware.android.steam.community"
            )

            addApp(
                "twitch",
                "Twitch",
                "tv.twitch.android.app"
            )

            addApp(
                "netflix",
                "Netflix",
                "com.netflix.mediaclient"
            )

            addApp(
                "vlc",
                "VLC",
                "org.videolan.vlc"
            )

            addApp(
                "waze",
                "Waze",
                "com.waze"
            )

            addApp(
                "bolt",
                "Bolt",
                "ee.mtakso.client"
            )

            addApp(
                "uber",
                "Uber",
                "com.ubercab"
            )

            addApp(
                "gmail",
                "Gmail",
                "com.google.android.gm"
            )

            addApp(
                "drive",
                "Google Drive",
                "com.google.android.apps.docs"
            )

            addApp(
                "revolut",
                "Revolut",
                "com.revolut.revolut"
            )

            addApp(
                "amazon",
                "Amazon",
                "com.amazon.mShop.android.shopping"
            )

            addApp(
                "ebay",
                "eBay",
                "com.ebay.mobile"
            )

            addApp(
                "fotok",
                "Google Fotók",
                "com.google.android.apps.photos"
            )

            addApp(
                "telefon",
                "Telefon",
                "com.google.android.dialer"
            )

            addApp(
                "uzenetek",
                "Üzenetek",
                "com.google.android.apps.messaging"
            )

            addApp(
                "play aruhaz",
                "Play Áruház",
                "com.android.vending"
            )

            /*
             * RENDSZER APP INTENTEK
             */

            add(
                Command(
                    keywords = listOf(
                        "fajlok",
                        "fajlkezelo",
                        "dokumentumok"
                    ),
                    action = { context ->

                        try {

                            val intent =
                                Intent(
                                    Intent.ACTION_OPEN_DOCUMENT
                                ).apply {

                                    type = "*/*"

                                    addCategory(
                                        Intent.CATEGORY_OPENABLE
                                    )
                                }

                            context.startActivity(intent)

                            "Megnyitom a fájlokat."

                        } catch (_: Exception) {

                            "Nem sikerült megnyitnom a fájlkezelőt."
                        }
                    }
                )
            )

            add(
                Command(
                    keywords = listOf(
                        "kalkulator",
                        "szamologep"
                    ),
                    action = { context ->

                        try {

                            val intent =
                                Intent(
                                    "android.intent.action.MAIN"
                                ).apply {

                                    addCategory(
                                        "android.intent.category.APP_CALCULATOR"
                                    )
                                }

                            context.startActivity(intent)

                            "Megnyitom a számológépet."

                        } catch (_: Exception) {

                            "Nem találok számológépet."
                        }
                    }
                )
            )

            add(
                Command(
                    keywords = listOf(
                        "ora",
                        "ebreszto",
                        "riaszto"
                    ),
                    action = { context ->

                        try {

                            context.startActivity(
                                Intent(
                                    "android.intent.action.SHOW_ALARMS"
                                )
                            )

                            "Megnyitom az órát."

                        } catch (_: Exception) {

                            "Nem sikerült megnyitnom az órát."
                        }
                    }
                )
            )

            add(
                Command(
                    keywords = listOf(
                        "naptar",
                        "calendar",
                        "esemenyek"
                    ),
                    action = { context ->

                        try {

                            context.startActivity(
                                Intent(
                                    Intent.ACTION_MAIN
                                ).apply {
                                    addCategory(
                                        "android.intent.category.APP_CALENDAR"
                                    )
                                }
                            )

                            "Megnyitom a naptárat."

                        } catch (_: Exception) {

                            "Nem sikerült megnyitnom a naptárat."
                        }
                    }
                )
            )
        }

    /*
     * App hozzáadása.
     *
     * Nem csak egyetlen név működik.
     * Például YouTube:
     *
     * youtube
     * youtube app
     * youtube alkalmazas
     * yt
     * you tube
     */

    private fun MutableList<Command>.addApp(
        name: String,
        label: String,
        vararg packageNames: String
    ) {

        val aliases =
            when (name) {

                "youtube" ->
                    listOf(
                        "youtube",
                        "you tube",
                        "youtube app",
                        "youtube alkalmazas",
                        "yt"
                    )

                "chrome" ->
                    listOf(
                        "chrome",
                        "google chrome",
                        "chrome app",
                        "bongeszo"
                    )

                "discord" ->
                    listOf(
                        "discord",
                        "discord app",
                        "discord alkalmazas"
                    )

                "instagram" ->
                    listOf(
                        "instagram",
                        "insta",
                        "instagram app"
                    )

                "tiktok" ->
                    listOf(
                        "tiktok",
                        "tik tok",
                        "tiktok app"
                    )

                "messenger" ->
                    listOf(
                        "messenger",
                        "messenger app",
                        "facebook messenger"
                    )

                "spotify" ->
                    listOf(
                        "spotify",
                        "spotify app",
                        "spotify alkalmazas"
                    )

                "facebook" ->
                    listOf(
                        "facebook",
                        "facebook app"
                    )

                "telegram" ->
                    listOf(
                        "telegram",
                        "telegram app"
                    )

                "whatsapp" ->
                    listOf(
                        "whatsapp",
                        "what's app",
                        "whats app",
                        "whatsapp app"
                    )

                else ->
                    listOf(
                        name,
                        "$name app",
                        "$name alkalmazas"
                    )
            }

        add(
            Command(
                keywords = aliases,
                action = openApp(
                    packageNames.toList(),
                    label
                )
            )
        )
    }

    fun execute(
        context: Context,
        utterance: String
    ): String {

        val value =
            MainActivity.normalize(
                utterance
            )

        if (value.isBlank()) {
            return "Nem hallottam a parancsot."
        }

        /*
         * Először az appokat és konkrét célokat
         * keressük, utána az általánosabb parancsokat.
         */

        val command =
            commands.firstOrNull { cmd ->

                cmd.keywords.any { keyword ->
                    value.contains(keyword)
                }

            }

        return command?.action?.invoke(context)
            ?: findInstalledApp(context, value)
            ?: "Ezt a parancsot még nem ismerem."
    }

    /*
     * EXTRA: ha a felhasználó olyan appot mond,
     * amelyik nincs kézzel felvéve a listába,
     * Nova megpróbálja megkeresni a telepített
     * alkalmazások között.
     */

    private fun findInstalledApp(
        context: Context,
        command: String
    ): String? {

        val packageManager =
            context.packageManager

        val apps =
            packageManager.getInstalledApplications(
                PackageManager.GET_META_DATA
            )

        for (app in apps) {

            val label =
                packageManager
                    .getApplicationLabel(app)
                    .toString()

            val normalizedLabel =
                MainActivity.normalize(
                    label
                )

            if (
                normalizedLabel.length >= 3 &&
                command.contains(normalizedLabel)
            ) {

                val intent =
                    packageManager
                        .getLaunchIntentForPackage(
                            app.packageName
                        )

                if (intent != null) {

                    try {

                        intent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                        )

                        context.startActivity(intent)

                        return "Megnyitom: $label."

                    } catch (_: Exception) {
                    }
                }
            }
        }

        return null
    }
}
