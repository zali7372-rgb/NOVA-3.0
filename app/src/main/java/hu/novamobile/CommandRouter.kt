package hu.novamobile

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import kotlin.math.max
import kotlin.math.min

object CommandRouter {

    enum class ResultType {
        EXECUTED,
        AMBIGUOUS,
        UNKNOWN
    }

    data class Result(
        val type: ResultType,
        val message: String,
        val options: List<String> = emptyList()
    )

    private data class Command(
        val names: List<String>,
        val action: (Context) -> String
    )

    /*
     * Rengeteg természetes magyar megfogalmazás
     * generálása ugyanabból a parancsból.
     */

    private val prefixes = listOf(
        "",
        "nyisd meg",
        "nyisd ki",
        "inditsd el",
        "inditsd",
        "nyisd",
        "mutasd meg",
        "mutasd",
        "menjunk",
        "ugorjunk",
        "lecci nyisd meg",
        "legyszi nyisd meg",
        "kerlek nyisd meg",
        "szeretnem megnyitni",
        "szeretnem elinditani",
        "inditsd el nekem"
    )

    private fun variants(
        vararg names: String
    ): List<String> {

        val result = mutableListOf<String>()

        for (name in names) {

            result.add(name)

            for (prefix in prefixes) {

                if (prefix.isBlank()) {
                    result.add(name)
                } else {
                    result.add(
                        "$prefix $name"
                    )
                }
            }
        }

        return result
            .map(MainActivity::normalize)
            .distinct()
    }

    private fun open(
        action: String,
        message: String
    ): (Context) -> String = { context ->

        launch(
            context,
            Intent(action)
        )

        message
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

            context.startActivity(
                Intent(
                    Settings.ACTION_SETTINGS
                ).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            )
        }
    }

    private val commands =
        mutableListOf<Command>()

    init {

        commands += Command(
            variants(
                "wifi",
                "wi fi",
                "wifi beallitas",
                "wifi beallitasok",
                "vezetek nelkuli halozat",
                "internet",
                "vezetek nelkuli kapcsolat"
            ),
            open(
                Settings.ACTION_WIFI_SETTINGS,
                "Megnyitom a Wi-Fi beállításokat."
            )
        )

        commands += Command(
            variants(
                "bluetooth",
                "bluetooth beallitas",
                "bluetooth beallitasok",
                "bluetooth kapcsolat"
            ),
            open(
                Settings.ACTION_BLUETOOTH_SETTINGS,
                "Megnyitom a Bluetooth beállításokat."
            )
        )

        commands += Command(
            variants(
                "mobilhalozat",
                "mobil halozat",
                "mobilnet",
                "mobil internet",
                "sim",
                "sim beallitasok"
            ),
            open(
                Settings.ACTION_NETWORK_OPERATOR_SETTINGS,
                "Megnyitom a mobilhálózati beállításokat."
            )
        )

        commands += Command(
            variants(
                "kijelzo",
                "kepernyo",
                "kijelzo beallitasok",
                "kepernyo beallitasok",
                "display"
            ),
            open(
                Settings.ACTION_DISPLAY_SETTINGS,
                "Megnyitom a kijelző beállításait."
            )
        )

        commands += Command(
            variants(
                "hangero",
                "hangero beallitas",
                "hang beallitas",
                "media hang",
                "hang"
            )
        ) { context ->

            val audio =
                context.getSystemService(
                    Context.AUDIO_SERVICE
                ) as AudioManager

            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI
            )

            "Feljebb vettem a médiahangot."
        }

        commands += Command(
            variants(
                "fenyero",
                "fenyero beallitas",
                "kepernyo fenyereje",
                "vilagossag"
            )
        ) { context ->

            if (Settings.System.canWrite(context)) {

                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    180
                )

                "A fényerőt feljebb vettem."

            } else {

                launch(
                    context,
                    Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse(
                            "package:${context.packageName}"
                        )
                    )
                )

                "Engedély szükséges a fényerő módosításához."
            }
        }

        commands += Command(
            variants(
                "akkumulator",
                "akku",
                "akku szazalek",
                "toltes",
                "toltotseg"
            )
        ) { context ->

            val battery =
                context.getSystemService(
                    Context.BATTERY_SERVICE
                ) as BatteryManager

            val percentage =
                battery.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CAPACITY
                )

            "Az akkumulátor töltöttsége $percentage százalék."
        }

        commands += Command(
            variants(
                "tarhely",
                "tarhely beallitasok",
                "tarhely informacio",
                "memoria"
            ),
            open(
                Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
                "Megnyitom a tárhely beállításait."
            )
        )

        commands += Command(
            variants(
                "ertesitesek",
                "ertesites",
                "ertesitesi beallitasok",
                "jelzesek"
            ),
            open(
                "android.settings.NOTIFICATION_SETTINGS",
                "Megnyitom az értesítési beállításokat."
            )
        )

        commands += Command(
            variants(
                "helymeghatarozas",
                "hely",
                "gps",
                "helyadatok",
                "lokacio"
            ),
            open(
                Settings.ACTION_LOCATION_SOURCE_SETTINGS,
                "Megnyitom a helymeghatározás beállításait."
            )
        )

        commands += Command(
            variants(
                "hotspot",
                "mobil hotspot",
                "wifi hotspot",
                "internet megosztas"
            ),
            open(
                "android.settings.TETHER_SETTINGS",
                "Megnyitom a hotspot beállításait."
            )
        )

        commands += Command(
            variants(
                "vpn",
                "vpn beallitasok",
                "virtualis maganhálózat",
                "virtualis magan halozat"
            ),
            open(
                Settings.ACTION_VPN_SETTINGS,
                "Megnyitom a VPN beállításait."
            )
        )

        commands += Command(
            variants(
                "beallitasok",
                "beallitas",
                "rendszerbeallitasok",
                "rendszer beallitasok"
            ),
            open(
                Settings.ACTION_SETTINGS,
                "Megnyitom a beállításokat."
            )
        )

        /*
         * APP LISTA
         */

        addApp(
            "youtube",
            "com.google.android.youtube",
            "YouTube",
            "youtube video",
            "youtube alkalmazas",
            "youtube app"
        )

        addApp(
            "chrome",
            "com.android.chrome",
            "Chrome",
            "google chrome",
            "chrome bongeszo",
            "internet"
        )

        addApp(
            "discord",
            "com.discord",
            "Discord",
            "discord alkalmazas",
            "discord app",
            "dc"
        )

        addApp(
            "spotify",
            "com.spotify.music",
            "Spotify",
            "spotify zene",
            "spotify alkalmazas",
            "zene"
        )

        addApp(
            "instagram",
            "com.instagram.android",
            "Instagram",
            "insta",
            "instagram alkalmazas",
            "instagram app"
        )

        addApp(
            "tiktok",
            "com.zhiliaoapp.musically",
            "TikTok",
            "tik tok",
            "tiktok alkalmazas",
            "tiktok app"
        )

        addApp(
            "facebook",
            "com.facebook.katana",
            "Facebook",
            "facebook alkalmazas",
            "facebook app"
        )

        addApp(
            "messenger",
            "com.facebook.orca",
            "Messenger",
            "messenger alkalmazas",
            "messenger app"
        )

        addApp(
            "whatsapp",
            "com.whatsapp",
            "WhatsApp",
            "whatsapp alkalmazas",
            "whatsapp app"
        )

        addApp(
            "telegram",
            "org.telegram.messenger",
            "Telegram",
            "telegram alkalmazas",
            "telegram app"
        )

        addApp(
            "snapchat",
            "com.snapchat.android",
            "Snapchat",
            "snap chat",
            "snapchat alkalmazas"
        )

        addApp(
            "reddit",
            "com.reddit.frontpage",
            "Reddit",
            "reddit alkalmazas",
            "reddit app"
        )

        addApp(
            "twitch",
            "tv.twitch.android.app",
            "Twitch",
            "twitch alkalmazas",
            "twitch app"
        )

        addApp(
            "netflix",
            "com.netflix.mediaclient",
            "Netflix",
            "netflix alkalmazas",
            "netflix app"
        )

        addApp(
            "steam",
            "com.valvesoftware.android.steam.community",
            "Steam",
            "steam alkalmazas",
            "steam app"
        )

        addApp(
            "waze",
            "com.waze",
            "Waze",
            "waze navigacio",
            "waze alkalmazas"
        )

        addApp(
            "bolt",
            "ee.mtakso.client",
            "Bolt",
            "bolt alkalmazas",
            "bolt app"
        )

        addApp(
            "revolut",
            "com.revolut.revolut",
            "Revolut",
            "revolut alkalmazas",
            "revolut app"
        )

        addApp(
            "gmail",
            "com.google.android.gm",
            "Gmail",
            "gmail alkalmazas",
            "gmail app",
            "email"
        )

        addApp(
            "play aruhaz",
            "com.android.vending",
            "Play Áruház",
            "play store",
            "google play",
            "play aruhaz alkalmazas"
        )

        addApp(
            "fotok",
            "com.google.android.apps.photos",
            "Google Fotók",
            "google fotok",
            "photos",
            "kepek"
        )

        addApp(
            "zoom",
            "us.zoom.videomeetings",
            "Zoom",
            "zoom alkalmazas",
            "zoom app"
        )

        addApp(
            "teams",
            "com.microsoft.teams",
            "Microsoft Teams",
            "teams alkalmazas",
            "teams app"
        )
    }

    private fun addApp(
        name: String,
        packageName: String,
        label: String,
        vararg aliases: String
    ) {

        commands += Command(
            variants(
                name,
                *aliases
            )
        ) { context ->

            val intent =
                context.packageManager
                    .getLaunchIntentForPackage(
                        packageName
                    )

            if (intent != null) {

                launch(
                    context,
                    intent
                )

                "Megnyitom: $label."

            } else {

                "A(z) $label alkalmazás nincs telepítve."
            }
        }
    }

    /*
     * ----------------------------------------------------
     * FUZZY MATCHING
     * ----------------------------------------------------
     */

    fun execute(
        context: Context,
        utterance: String
    ): Result {

        val input =
            MainActivity.normalize(utterance)

        if (input.isBlank()) {
            return Result(
                ResultType.UNKNOWN,
                "Nem hallottam parancsot."
            )
        }

        /*
         * Először exact / részleges találat.
         */

        val exact =
            commands.filter { command ->

                command.names.any { phrase ->

                    input == phrase ||
                            input.contains(phrase) ||
                            phrase.contains(input)
                }
            }

        if (exact.size == 1) {

            return Result(
                ResultType.EXECUTED,
                exact.first().action(context)
            )
        }

        /*
         * Ha nincs biztos találat,
         * fuzzy keresés jön.
         */

        val scored =
            commands.map { command ->

                val best =
                    command.names.maxOf { phrase ->
                        similarity(input, phrase)
                    }

                command to best
            }
                .sortedByDescending { it.second }

        val best = scored.getOrNull(0)
        val second = scored.getOrNull(1)

        if (best == null) {

            return Result(
                ResultType.UNKNOWN,
                "Ezt a parancsot nem ismertem fel."
            )
        }

        /*
         * 70% alatt ne találgasson.
         */

        if (best.second < 0.70) {

            return Result(
                ResultType.UNKNOWN,
                "Nem voltam elég biztos benne, hogy mit szeretnél."
            )
        }

        /*
         * Ha két találat nagyon közel van,
         * kérdezzen vissza.
         */

        if (
            second != null &&
            second.second >= 0.70 &&
            best.second - second.second <= 0.08
        ) {

            return Result(
                ResultType.AMBIGUOUS,
                "Nem vagyok teljesen biztos.",
                listOf(
                    displayName(best.first),
                    displayName(second.first)
                )
            )
        }

        return Result(
            ResultType.EXECUTED,
            best.first.action(context)
        )
    }

    private fun displayName(
        command: Command
    ): String {

        return command.names
            .firstOrNull()
            ?: "ismeretlen"
    }

    /*
     * Levenshtein alapú hasonlóság.
     */

    private fun similarity(
        a: String,
        b: String
    ): Double {

        if (a == b) return 1.0

        if (a.isEmpty() || b.isEmpty()) {
            return 0.0
        }

        val distance =
            levenshtein(a, b)

        val maxLength =
            max(a.length, b.length)

        return 1.0 -
                distance.toDouble() /
                maxLength.toDouble()
    }

    private fun levenshtein(
        a: String,
        b: String
    ): Int {

        val previous =
            IntArray(b.length + 1) {
                it
            }

        var current =
            IntArray(b.length + 1)

        for (i in 1..a.length) {

            current[0] = i

            for (j in 1..b.length) {

                val cost =
                    if (a[i - 1] == b[j - 1]) {
                        0
                    } else {
                        1
                    }

                current[j] =
                    min(
                        min(
                            current[j - 1] + 1,
                            previous[j] + 1
                        ),
                        previous[j - 1] + cost
                    )
            }

            for (j in previous.indices) {
                previous[j] = current[j]
            }
        }

        return previous[b.length]
    }
}
