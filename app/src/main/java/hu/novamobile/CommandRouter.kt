package hu.novamobile

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import kotlin.math.max

object CommandRouter {

    enum class ResultType {
        EXECUTED,
        CLARIFICATION,
        UNKNOWN
    }

    data class Result(
        val type: ResultType,
        val message: String
    )

    private data class Command(
        val id: String,
        val aliases: List<String>,
        val execute: (Context) -> String
    )

    private data class Candidate(
        val command: Command,
        val score: Double
    )

    /*
     * Rengeteg természetes magyar megfogalmazást generálunk.
     */
    private val starters = listOf(
        "",
        "nyisd meg",
        "nyisd ki",
        "inditsd el",
        "inditsd",
        "nyisd",
        "mutasd",
        "mutasd meg",
        "menj",
        "menjunk",
        "lepj be",
        "nyisd meg nekem",
        "nyisd ki nekem",
        "inditsd el nekem",
        "mutasd nekem",
        "hozd elo",
        "hozd fel",
        "szeretnem megnyitni",
        "szeretnem megnezni",
        "meg tudod nyitni",
        "megnyitnad",
        "inditsd nekem",
        "inditsd el nekem"
    )

    private val fillers = listOf(
        "",
        "a",
        "az",
        "nekem",
        "legyszi",
        "kerlek",
        "most",
        "gyorsan",
        "mar",
        "kerlek szepen"
    )

    private fun normalize(text: String): String {
        return MainActivity.normalize(text)
    }

    /*
     * Egy aliasból rengeteg lehetséges mondat készül.
     */
    private fun generatedPhrases(
        vararg aliases: String
    ): List<String> {

        val result = mutableSetOf<String>()

        for (aliasRaw in aliases) {

            val alias = normalize(aliasRaw)

            result += alias

            for (starter in starters) {

                for (filler in fillers) {

                    val phrase =
                        listOf(
                            starter,
                            filler,
                            alias
                        )
                            .filter { it.isNotBlank() }
                            .joinToString(" ")

                    if (phrase.isNotBlank()) {
                        result += phrase
                    }
                }
            }
        }

        return result.toList()
    }

    private fun openSettings(
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

            try {

                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                        )
                )

            } catch (_: Exception) {
            }
        }
    }

    private fun app(
        id: String,
        label: String,
        packageName: String,
        vararg aliases: String
    ): Command {

        return Command(
            id = id,
            aliases =
                generatedPhrases(
                    label,
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

    private val commands: List<Command> = listOf(

        // =========================
        // RENDSZER
        // =========================

        Command(
            "wifi",
            generatedPhrases(
                "wifi",
                "wi fi",
                "wif i",
                "vezetek nelkuli halozat",
                "vezetek nelkuli kapcsolat",
                "wifi beallitasok"
            )
        ) {
            openSettings(
                Settings.ACTION_WIFI_SETTINGS,
                "Megnyitom a Wi-Fi beállításokat."
            )(it)
        },

        Command(
            "bluetooth",
            generatedPhrases(
                "bluetooth",
                "blu tooth",
                "bluetooth beallitasok",
                "bluetooth kapcsolat"
            )
        ) {
            openSettings(
                Settings.ACTION_BLUETOOTH_SETTINGS,
                "Megnyitom a Bluetooth beállításokat."
            )(it)
        },

        Command(
            "mobilhalozat",
            generatedPhrases(
                "mobilhalozat",
                "mobil halozat",
                "mobilnet",
                "mobil internet",
                "sim",
                "sim beallitasok",
                "mobil adat"
            )
        ) {
            openSettings(
                Settings.ACTION_NETWORK_OPERATOR_SETTINGS,
                "Megnyitom a mobilhálózati beállításokat."
            )(it)
        },

        Command(
            "kijelzo",
            generatedPhrases(
                "kijelzo",
                "kepernyo",
                "kepernyo beallitasok",
                "kijelzo beallitasok",
                "display"
            )
        ) {
            openSettings(
                Settings.ACTION_DISPLAY_SETTINGS,
                "Megnyitom a kijelző beállításait."
            )(it)
        },

        Command(
            "ertesitesek",
            generatedPhrases(
                "ertesitesek",
                "ertesitesi beallitasok",
                "ertesites",
                "jelzesek",
                "ertesitesi beallitas"
            )
        ) {
            openSettings(
                "android.settings.NOTIFICATION_SETTINGS",
                "Megnyitom az értesítési beállításokat."
            )(it)
        },

        Command(
            "hely",
            generatedPhrases(
                "helymeghatarozas",
                "helyadatok",
                "hely",
                "gps",
                "helyzet",
                "helyszolgaltatas"
            )
        ) {
            openSettings(
                Settings.ACTION_LOCATION_SOURCE_SETTINGS,
                "Megnyitom a helymeghatározás beállításait."
            )(it)
        },

        Command(
            "hotspot",
            generatedPhrases(
                "hotspot",
                "mobil hotspot",
                "wifi hotspot",
                "internet megosztas",
                "net megosztas"
            )
        ) {
            openSettings(
                "android.settings.TETHER_SETTINGS",
                "Megnyitom a hotspot beállításait."
            )(it)
        },

        Command(
            "vpn",
            generatedPhrases(
                "vpn",
                "vpn beallitasok",
                "virtualis maganh alozat",
                "virtualis maganhalozat"
            )
        ) {
            openSettings(
                Settings.ACTION_VPN_SETTINGS,
                "Megnyitom a VPN beállításokat."
            )(it)
        },

        Command(
            "beallitasok",
            generatedPhrases(
                "beallitasok",
                "telefon beallitasok",
                "rendszerbeallitasok",
                "rendszer",
                "beallitas"
            )
        ) {
            openSettings(
                Settings.ACTION_SETTINGS,
                "Megnyitom a beállításokat."
            )(it)
        },

        // =========================
        // HANG
        // =========================

        Command(
            "hangero",
            generatedPhrases(
                "hangero",
                "hangerő",
                "hang",
                "media hang",
                "hang beallitasok"
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
        },

        Command(
            "fenyero",
            generatedPhrases(
                "fenyero",
                "fenyero beallitas",
                "kepernyo fenyereje",
                "kepernyo vilagossag",
                "vilagossag",
                "fenyesseg"
            )
        ) { context ->

            if (Settings.System.canWrite(context)) {

                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    180
                )

                "A fényerőt feljebb állítottam."

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

                "A fényerő módosításához engedély szükséges."
            }
        },

        // =========================
        // INFORMÁCIÓ
        // =========================

        Command(
            "akku",
            generatedPhrases(
                "akkumulator",
                "akku",
                "akku allapot",
                "akkumulator allapot",
                "toltottseg",
                "toltottsegem"
            )
        ) { context ->

            val battery =
                context.getSystemService(
                    Context.BATTERY_SERVICE
                ) as BatteryManager

            val percent =
                battery.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CAPACITY
                )

            "Az akkumulátor töltöttsége $percent százalék."
        },

        // =========================
        // FÁJLOK
        // =========================

        Command(
            "fajlok",
            generatedPhrases(
                "fajlkezelo",
                "fajlok",
                "fajlok kezelese",
                "dokumentumok",
                "dokumentumok megnyitasa",
                "fileok",
                "file kezelo"
            )
        ) { context ->

            launch(
                context,
                Intent(
                    Intent.ACTION_OPEN_DOCUMENT
                ).apply {
                    type = "*/*"
                    addCategory(
                        Intent.CATEGORY_OPENABLE
                    )
                }
            )

            "Megnyitom a fájlkezelőt."
        },

        // =========================
        // APP GROUP
        // =========================

        app(
            "youtube",
            "YouTube",
            "com.google.android.youtube",
            "youtube",
            "jutub",
            "jútub",
            "youtube app",
            "youtube alkalmazas",
            "video youtube"
        ),

        app(
            "chrome",
            "Chrome",
            "com.android.chrome",
            "chrome",
            "google chrome",
            "bongeszo",
            "internet",
            "webes bongeszo"
        ),

        app(
            "kamera",
            "Kamera",
            "com.android.camera",
            "kamera",
            "camera",
            "fenykepezo",
            "fotozas"
        ),

        app(
            "telefon",
            "Telefon",
            "com.google.android.dialer",
            "telefon",
            "hivas",
            "telefon app",
            "tarcsazo"
        ),

        app(
            "uzenetek",
            "Üzenetek",
            "com.google.android.apps.messaging",
            "uzenetek",
            "sms",
            "uzenet",
            "messaging"
        ),

        app(
            "gmail",
            "Gmail",
            "com.google.android.gm",
            "gmail",
            "email",
            "mail",
            "levelezes"
        ),

        app(
            "play",
            "Play Áruház",
            "com.android.vending",
            "play aruhaz",
            "play store",
            "play",
            "google play",
            "aruhaz"
        ),

        app(
            "discord",
            "Discord",
            "com.discord",
            "discord",
            "diszkord",
            "disco",
            "dc"
        ),

        app(
            "instagram",
            "Instagram",
            "com.instagram.android",
            "instagram",
            "insta",
            "insta app"
        ),

        app(
            "tiktok",
            "TikTok",
            "com.zhiliaoapp.musically",
            "tiktok",
            "tik tok",
            "tik tok app"
        ),

        app(
            "facebook",
            "Facebook",
            "com.facebook.katana",
            "facebook",
            "fészbuk",
            "feszbuk"
        ),

        app(
            "messenger",
            "Messenger",
            "com.facebook.orca",
            "messenger",
            "messzi",
            "messenger app"
        ),

        app(
            "whatsapp",
            "WhatsApp",
            "com.whatsapp",
            "whatsapp",
            "vacak",
            "wacap",
            "whattsapp"
        ),

        app(
            "telegram",
            "Telegram",
            "org.telegram.messenger",
            "telegram",
            "tele",
            "telegram app"
        ),

        app(
            "snapchat",
            "Snapchat",
            "com.snapchat.android",
            "snapchat",
            "snap",
            "snap chat"
        ),

        app(
            "reddit",
            "Reddit",
            "com.reddit.frontpage",
            "reddit",
            "reddit app"
        ),

        app(
            "linkedin",
            "LinkedIn",
            "com.linkedin.android",
            "linkedin",
            "linkdin",
            "linked in"
        ),

        app(
            "pinterest",
            "Pinterest",
            "com.pinterest",
            "pinterest",
            "pintereszt"
        ),

        app(
            "teams",
            "Microsoft Teams",
            "com.microsoft.teams",
            "teams",
            "microsoft teams",
            "team",
            "teams app"
        ),

        app(
            "zoom",
            "Zoom",
            "us.zoom.videomeetings",
            "zoom",
            "zoom meeting",
            "videomeeting"
        ),

        app(
            "meet",
            "Google Meet",
            "com.google.android.apps.tachyon",
            "meet",
            "google meet",
            "videohivas"
        ),

        app(
            "spotify",
            "Spotify",
            "com.spotify.music",
            "spotify",
            "spoty",
            "spotifi",
            "zene"
        ),

        app(
            "steam",
            "Steam",
            "com.valvesoftware.android.steam.community",
            "steam",
            "sztím",
            "steam app"
        ),

        app(
            "twitch",
            "Twitch",
            "tv.twitch.android.app",
            "twitch",
            "twics",
            "twitsh"
        ),

        app(
            "netflix",
            "Netflix",
            "com.netflix.mediaclient",
            "netflix",
            "netflx",
            "netflix app"
        ),

        app(
            "disney",
            "Disney Plus",
            "com.disney.disneyplus",
            "disney",
            "disney plus",
            "disney+"
        ),

        app(
            "prime",
            "Prime Video",
            "com.amazon.avod.thirdpartyclient",
            "prime video",
            "amazon prime",
            "prime"
        ),

        app(
            "vlc",
            "VLC",
            "org.videolan.vlc",
            "vlc",
            "video lejatszo"
        ),

        app(
            "waze",
            "Waze",
            "com.waze",
            "waze",
            "vejsz",
            "navigacio"
        ),

        app(
            "uber",
            "Uber",
            "com.ubercab",
            "uber",
            "uber app"
        ),

        app(
            "bolt",
            "Bolt",
            "ee.mtakso.client",
            "bolt",
            "bolt taxi",
            "bolt app"
        ),

        app(
            "drive",
            "Google Drive",
            "com.google.android.apps.docs",
            "google drive",
            "drive",
            "gdrive",
            "meghajto"
        ),

        app(
            "revolut",
            "Revolut",
            "com.revolut.revolut",
            "revolut",
            "revo",
            "revolut app"
        ),

        app(
            "amazon",
            "Amazon",
            "com.amazon.mShop.android.shopping",
            "amazon",
            "amazon app"
        ),

        app(
            "ebay",
            "eBay",
            "com.ebay.mobile",
            "ebay",
            "ebay app"
        ),

        app(
            "photos",
            "Google Fotók",
            "com.google.android.apps.photos",
            "fotok",
            "google fotok",
            "photos",
            "kepek"
        )
    )

    fun execute(
        context: Context,
        utterance: String
    ): Result {

        val input = normalize(utterance)

        if (input.isBlank()) {
            return Result(
                ResultType.UNKNOWN,
                "Nem hallottam a parancsot."
            )
        }

        val candidates =
            commands
                .map { command ->
                    Candidate(
                        command,
                        calculateScore(
                            input,
                            command
                        )
                    )
                }
                .sortedByDescending {
                    it.score
                }

        val best =
            candidates.firstOrNull()

        if (best == null || best.score < 0.70) {

            return Result(
                ResultType.UNKNOWN,
                "Ezt nem sikerült felismernem. Próbáld meg másképp."
            )
        }

        val second =
            candidates
                .getOrNull(1)

        /*
         * Ha két parancs nagyon közel van egymáshoz,
         * nem találgatunk.
         */
        if (
            second != null &&
            second.score >= 0.70 &&
            kotlin.math.abs(
                best.score - second.score
            ) < 0.08
        ) {

            return Result(
                ResultType.CLARIFICATION,
                "Ezt nem teljesen értettem. Azt szeretnéd, hogy ${displayName(best.command)} vagy ${displayName(second.command)}?"
            )
        }

        return try {

            Result(
                ResultType.EXECUTED,
                best.command.execute(context)
            )

        } catch (_: Exception) {

            Result(
                ResultType.UNKNOWN,
                "Megtaláltam a parancsot, de a telefon nem tudta megnyitni."
            )
        }
    }

    private fun displayName(
        command: Command
    ): String {

        return when (command.id) {

            "wifi" -> "a Wi-Fi-t"
            "bluetooth" -> "a Bluetooth-t"
            "mobilhalozat" -> "a mobilhálózatot"
            "kijelzo" -> "a kijelzőt"
            "ertesitesek" -> "az értesítéseket"
            "hely" -> "a helymeghatározást"
            "hotspot" -> "a hotspotot"
            "vpn" -> "a VPN-t"
            "beallitasok" -> "a beállításokat"
            "hangero" -> "a hangerőt"
            "fenyero" -> "a fényerőt"
            "akku" -> "az akkumulátort"
            "fajlok" -> "a fájlokat"

            else ->
                command.aliases
                    .firstOrNull()
                    ?: command.id
        }
    }

    private fun calculateScore(
        input: String,
        command: Command
    ): Double {

        var best = 0.0

        for (phrase in command.aliases) {

            val score =
                compareText(
                    input,
                    phrase
                )

            best =
                max(
                    best,
                    score
                )

            if (best >= 0.98) {
                return best
            }
        }

        return best
    }

    private fun compareText(
        input: String,
        target: String
    ): Double {

        if (input == target) {
            return 1.0
        }

        if (
            input.contains(target) ||
            target.contains(input)
        ) {

            val ratio =
                minOf(
                    input.length,
                    target.length
                ).toDouble() /
                    max(
                        input.length,
                        target.length
                    )

            return 0.75 + ratio * 0.25
        }

        val inputWords =
            input.split(" ")

        val targetWords =
            target.split(" ")

        var total = 0.0
        var matched = 0

        for (word in inputWords) {

            if (word.length < 2) continue

            var localBest = 0.0

            for (targetWord in targetWords) {

                val similarity =
                    MainActivity.fuzzySimilarity(
                        word,
                        targetWord
                    )

                if (similarity > localBest) {
                    localBest = similarity
                }
            }

            if (localBest >= 0.55) {
                total += localBest
                matched++
            }
        }

        if (matched == 0) {
            return 0.0
        }

        val wordScore =
            total / matched

        val fullScore =
            MainActivity.fuzzySimilarity(
                input,
                target
            )

        return (
            wordScore * 0.65 +
            fullScore * 0.35
        )
    }
}
