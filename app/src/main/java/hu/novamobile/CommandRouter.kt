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
        UNKNOWN,
        AMBIGUOUS,
        CLARIFICATION
    }

    data class CommandResult(
        val type: ResultType,
        val response: String,
        val options: List<String> = emptyList()
    )

    private data class Command(
        val id: String,
        val label: String,
        val aliases: List<String>,
        val action: (Context) -> String
    )

    // ============================================================
    // NORMALIZÁLÁS
    // ============================================================

    private fun normalize(input: String): String {
        return MainActivity.normalize(input)
            .replace("alkalmazás", "app")
            .replace("alkalmazast", "app")
            .replace("alkalmazas", "app")
            .replace("programot", "program")
            .replace("program", "app")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun words(text: String): List<String> {
        return normalize(text)
            .split(" ")
            .filter { it.length >= 2 }
    }

    // ============================================================
    // LEVENSHTEIN
    // ============================================================

    private fun levenshtein(a: String, b: String): Int {

        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }

        for (i in a.indices) {

            val current = IntArray(b.length + 1)

            current[0] = i + 1

            for (j in b.indices) {

                val cost =
                    if (a[i] == b[j]) 0 else 1

                current[j + 1] = min(
                    min(
                        current[j] + 1,
                        previous[j + 1] + 1
                    ),
                    previous[j] + cost
                )
            }

            previous = current
        }

        return previous[b.length]
    }

    private fun fuzzySimilarity(
        a: String,
        b: String
    ): Double {

        val aa = normalize(a)
        val bb = normalize(b)

        if (aa.isEmpty() || bb.isEmpty()) {
            return 0.0
        }

        if (aa == bb) {
            return 1.0
        }

        val distance =
            levenshtein(aa, bb)

        val longest =
            max(aa.length, bb.length)

        return 1.0 -
                distance.toDouble() /
                longest.toDouble()
    }

    private fun tokenSimilarity(
        input: String,
        alias: String
    ): Double {

        val inputWords = words(input)
        val aliasWords = words(alias)

        if (
            inputWords.isEmpty() ||
            aliasWords.isEmpty()
        ) {
            return fuzzySimilarity(
                input,
                alias
            )
        }

        var total = 0.0
        var matched = 0

        for (aliasWord in aliasWords) {

            var best = 0.0

            for (inputWord in inputWords) {

                best = max(
                    best,
                    fuzzySimilarity(
                        inputWord,
                        aliasWord
                    )
                )
            }

            total += best

            if (best >= 0.55) {
                matched++
            }
        }

        val average =
            total / aliasWords.size

        val coverage =
            matched.toDouble() /
                    aliasWords.size

        return average * 0.7 +
                coverage * 0.3
    }

    private fun commandSimilarity(
        input: String,
        alias: String
    ): Double {

        val normalizedInput =
            normalize(input)

        val normalizedAlias =
            normalize(alias)

        if (
            normalizedInput.contains(
                normalizedAlias
            )
        ) {
            return 1.0
        }

        if (
            normalizedAlias.contains(
                normalizedInput
            ) &&
            normalizedInput.length >= 3
        ) {
            return 0.92
        }

        return max(
            fuzzySimilarity(
                normalizedInput,
                normalizedAlias
            ),
            tokenSimilarity(
                normalizedInput,
                normalizedAlias
            )
        )
    }

    // ============================================================
    // ALIAS GENERÁTOR
    // ============================================================

    private fun aliases(
        vararg names: String
    ): List<String> {

        val result =
            mutableSetOf<String>()

        val starters = listOf(
            "",
            "nyisd meg",
            "nyisd ki",
            "inditsd el",
            "inditsd",
            "nyisd fel",
            "menj ide",
            "menjunk ide",
            "vigyel ide",
            "mutasd",
            "hozd elo",
            "kapcsold be",
            "nyisd meg nekem",
            "nyisd ki nekem",
            "menj a",
            "ugorj a",
            "inditsd el a",
            "nyisd meg a",
            "nyisd ki a"
        )

        for (name in names) {

            result += name

            for (starter in starters) {

                if (starter.isBlank()) {
                    result += name
                } else {
                    result += "$starter $name"
                }
            }

            result += "$name app"
            result += "$name alkalmazas"
            result += "$name program"
        }

        return result.toList()
    }

    // ============================================================
    // APP MEGNYITÁSA
    // ============================================================

    private fun openApp(
        packageName: String,
        label: String
    ): (Context) -> String = { context ->

        val intent =
            context.packageManager
                .getLaunchIntentForPackage(
                    packageName
                )

        if (intent == null) {

            "$label nincs telepítve ezen a telefonon."

        } else {

            try {

                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )

                context.startActivity(intent)

                "Megnyitom a $label alkalmazást."

            } catch (_: Exception) {

                "Nem sikerült megnyitnom a $label alkalmazást."
            }
        }
    }

    // ============================================================
    // BEÁLLÍTÁSOK
    // ============================================================

    private fun openSettings(
        action: String,
        message: String
    ): (Context) -> String = { context ->

        try {

            val intent =
                Intent(action)

            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )

            context.startActivity(intent)

            message

        } catch (_: Exception) {

            try {

                context.startActivity(
                    Intent(
                        Settings.ACTION_SETTINGS
                    ).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                )

                "A kért beállítás nem érhető el, ezért megnyitottam a rendszerbeállításokat."

            } catch (_: Exception) {

                "Nem sikerült megnyitnom a beállításokat."
            }
        }
    }

    // ============================================================
    // PARANCSOK
    // ============================================================

    private val commands: List<Command> = listOf(

        // --------------------------------------------------------
        // WIFI
        // --------------------------------------------------------

        Command(
            "wifi",
            "Wi-Fi",
            aliases(
                "wifi",
                "wi fi",
                "wifit",
                "wi fit",
                "wifi beallitas",
                "wifi beallitasok",
                "vezetek nelkuli halozat",
                "vezetek nelkuli internet",
                "internet wifi",
                "wifi kapcsolat",
                "wifi menu",
                "wifi menü"
            ),
            openSettings(
                Settings.ACTION_WIFI_SETTINGS,
                "Megnyitom a Wi-Fi beállításokat."
            )
        ),

        // --------------------------------------------------------
        // BLUETOOTH
        // --------------------------------------------------------

        Command(
            "bluetooth",
            "Bluetooth",
            aliases(
                "bluetooth",
                "blutoth",
                "blutooth",
                "blu tut",
                "bluetooth beallitas",
                "bluetooth beallitasok",
                "bluetooth kapcsolat",
                "bluetooth menu",
                "bluetooth menü"
            ),
            openSettings(
                Settings.ACTION_BLUETOOTH_SETTINGS,
                "Megnyitom a Bluetooth beállításokat."
            )
        ),

        // --------------------------------------------------------
        // KIJELZŐ
        // --------------------------------------------------------

        Command(
            "display",
            "kijelző",
            aliases(
                "kijelzo",
                "kijelzo beallitas",
                "kijelzo beallitasok",
                "kepernyo",
                "kepernyo beallitas",
                "kepernyo beallitasok",
                "display",
                "display settings",
                "monitor",
                "fenyero",
                "fenyero beallitas",
                "vilagossag"
            ),
            openSettings(
                Settings.ACTION_DISPLAY_SETTINGS,
                "Megnyitom a kijelző beállításait."
            )
        ),

        // --------------------------------------------------------
        // HELY
        // --------------------------------------------------------

        Command(
            "location",
            "helymeghatározás",
            aliases(
                "gps",
                "helymeghatarozas",
                "helymeghatarozo",
                "helyadatok",
                "helyzet",
                "lokacio",
                "location",
                "hely beallitas",
                "gps beallitas"
            ),
            openSettings(
                Settings.ACTION_LOCATION_SOURCE_SETTINGS,
                "Megnyitom a helymeghatározás beállításait."
            )
        ),

        // --------------------------------------------------------
        // ÉRTESÍTÉSEK
        // --------------------------------------------------------

        Command(
            "notifications",
            "értesítések",
            aliases(
                "ertesites",
                "ertesitesek",
                "ertesitesi beallitas",
                "ertesitesi beallitasok",
                "jelzesek",
                "notifikaciok",
                "notification",
                "notifications"
            ),
            openSettings(
                Settings.ACTION_NOTIFICATION_SETTINGS,
                "Megnyitom az értesítési beállításokat."
            )
        ),

        // --------------------------------------------------------
        // VPN
        // --------------------------------------------------------

        Command(
            "vpn",
            "VPN",
            aliases(
                "vpn",
                "vpn beallitas",
                "vpn beallitasok",
                "virtualis maganhalozat",
                "virtualis magan halozat"
            ),
            openSettings(
                Settings.ACTION_VPN_SETTINGS,
                "Megnyitom a VPN beállításokat."
            )
        ),

        // --------------------------------------------------------
        // RENDSZERBEÁLLÍTÁSOK
        // --------------------------------------------------------

        Command(
            "settings",
            "rendszerbeállítások",
            aliases(
                "beallitas",
                "beallitasok",
                "telefon beallitas",
                "telefon beallitasok",
                "rendszerbeallitas",
                "rendszerbeallitasok",
                "settings",
                "setting",
                "beallitas menu",
                "beallitasok menu"
            ),
            openSettings(
                Settings.ACTION_SETTINGS,
                "Megnyitom a rendszerbeállításokat."
            )
        ),

        // --------------------------------------------------------
        // TÁRHELY
        // --------------------------------------------------------

        Command(
            "storage",
            "tárhely",
            aliases(
                "tarhely",
                "tarhely informacio",
                "tarhely beallitas",
                "tarhely beallitasok",
                "memoria",
                "belso memoria",
                "belsomemoria",
                "storage"
            ),
            openSettings(
                Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
                "Megnyitom a tárhely beállításait."
            )
        ),

        // --------------------------------------------------------
        // HOTSPOT
        // --------------------------------------------------------

        Command(
            "hotspot",
            "mobil hotspot",
            aliases(
                "hotspot",
                "mobil hotspot",
                "wifi hotspot",
                "internet megosztas",
                "net megosztas",
                "internetmegosztas",
                "hot spot",
                "hotspott"
            ),
            openSettings(
                "android.settings.TETHER_SETTINGS",
                "Megnyitom a hotspot beállításait."
            )
        ),

        // --------------------------------------------------------
        // YOUTUBE
        // --------------------------------------------------------

        Command(
            "youtube",
            "YouTube",
            aliases(
                "youtube",
                "jutub",
                "youtub",
                "youtubot",
                "youtube app",
                "youtube alkalmazas",
                "youtube video",
                "videok",
                "videomegoszto"
            ),
            openApp(
                "com.google.android.youtube",
                "YouTube"
            )
        ),

        // --------------------------------------------------------
        // CHROME
        // --------------------------------------------------------

        Command(
            "chrome",
            "Chrome",
            aliases(
                "chrome",
                "krom",
                "crome",
                "chrom",
                "google chrome",
                "chrome bongeszo",
                "google bongeszo",
                "internet",
                "bongeszo"
            ),
            openApp(
                "com.android.chrome",
                "Chrome"
            )
        ),

        // --------------------------------------------------------
        // DISCORD
        // --------------------------------------------------------

        Command(
            "discord",
            "Discord",
            aliases(
                "discord",
                "diszkord",
                "disscord",
                "diskord",
                "discord app",
                "discord alkalmazas",
                "discordot",
                "diszkordot",
                "dc",
                "d c",
                "discord chat"
            ),
            openApp(
                "com.discord",
                "Discord"
            )
        ),

        // --------------------------------------------------------
        // TIKTOK
        // --------------------------------------------------------

        Command(
            "tiktok",
            "TikTok",
            aliases(
                "tiktok",
                "tik tok",
                "tiktokk",
                "tiktoc",
                "tik tokot",
                "tiktok app",
                "tiktok alkalmazas",
                "tiktok video",
                "rovid videok",
                "short videok"
            ),
            openApp(
                "com.zhiliaoapp.musically",
                "TikTok"
            )
        ),

        // --------------------------------------------------------
        // INSTAGRAM
        // --------------------------------------------------------

        Command(
            "instagram",
            "Instagram",
            aliases(
                "instagram",
                "insta",
                "insta gram",
                "instagrm",
                "instagram app",
                "instagram alkalmazas",
                "instat",
                "instat nyisd meg"
            ),
            openApp(
                "com.instagram.android",
                "Instagram"
            )
        ),

        // --------------------------------------------------------
        // FACEBOOK
        // --------------------------------------------------------

        Command(
            "facebook",
            "Facebook",
            aliases(
                "facebook",
                "facebok",
                "facebook app",
                "facebook alkalmazas",
                "feszbuk",
                "face"
            ),
            openApp(
                "com.facebook.katana",
                "Facebook"
            )
        ),

        // --------------------------------------------------------
        // MESSENGER
        // --------------------------------------------------------

        Command(
            "messenger",
            "Messenger",
            aliases(
                "messenger",
                "mesenger",
                "messenger app",
                "messenger alkalmazas",
                "uzenetek messenger",
                "chat"
            ),
            openApp(
                "com.facebook.orca",
                "Messenger"
            )
        ),

        // --------------------------------------------------------
        // WHATSAPP
        // --------------------------------------------------------

        Command(
            "whatsapp",
            "WhatsApp",
            aliases(
                "whatsapp",
                "what app",
                "whats app",
                "watsapp",
                "whatsup",
                "whatsapp app",
                "whatsapp alkalmazas"
            ),
            openApp(
                "com.whatsapp",
                "WhatsApp"
            )
        ),

        // --------------------------------------------------------
        // TELEGRAM
        // --------------------------------------------------------

        Command(
            "telegram",
            "Telegram",
            aliases(
                "telegram",
                "telegran",
                "telegram app",
                "telegram alkalmazas"
            ),
            openApp(
                "org.telegram.messenger",
                "Telegram"
            )
        ),

        // --------------------------------------------------------
        // SNAPCHAT
        // --------------------------------------------------------

        Command(
            "snapchat",
            "Snapchat",
            aliases(
                "snapchat",
                "snap chat",
                "snap",
                "snapcsat",
                "snapcsat app",
                "snapchat alkalmazas"
            ),
            openApp(
                "com.snapchat.android",
                "Snapchat"
            )
        ),

        // --------------------------------------------------------
        // X / TWITTER
        // --------------------------------------------------------

        Command(
            "x",
            "X",
            aliases(
                "twitter",
                "x twitter",
                "twitter app",
                "twitter alkalmazas",
                "eksz",
                "ex"
            ),
            openApp(
                "com.twitter.android",
                "X"
            )
        ),

        // --------------------------------------------------------
        // REDDIT
        // --------------------------------------------------------

        Command(
            "reddit",
            "Reddit",
            aliases(
                "reddit",
                "red it",
                "reddit app",
                "reddit alkalmazas",
                "redditet"
            ),
            openApp(
                "com.reddit.frontpage",
                "Reddit"
            )
        ),

        // --------------------------------------------------------
        // SPOTIFY
        // --------------------------------------------------------

        Command(
            "spotify",
            "Spotify",
            aliases(
                "spotify",
                "spoty",
                "spotify app",
                "spotify alkalmazas",
                "zene",
                "zenet",
                "zenelejatszo"
            ),
            openApp(
                "com.spotify.music",
                "Spotify"
            )
        ),

        // --------------------------------------------------------
        // STEAM
        // --------------------------------------------------------

        Command(
            "steam",
            "Steam",
            aliases(
                "steam",
                "stim",
                "steam app",
                "steam alkalmazas"
            ),
            openApp(
                "com.valvesoftware.android.steam.community",
                "Steam"
            )
        ),

        // --------------------------------------------------------
        // TWITCH
        // --------------------------------------------------------

        Command(
            "twitch",
            "Twitch",
            aliases(
                "twitch",
                "tvis",
                "twics",
                "twitch app",
                "twitch alkalmazas",
                "streamek"
            ),
            openApp(
                "tv.twitch.android.app",
                "Twitch"
            )
        ),

        // --------------------------------------------------------
        // NETFLIX
        // --------------------------------------------------------

        Command(
            "netflix",
            "Netflix",
            aliases(
                "netflix",
                "netfliks",
                "netfli",
                "netflix app",
                "netflix alkalmazas",
                "filmek",
                "sorozatok"
            ),
            openApp(
                "com.netflix.mediaclient",
                "Netflix"
            )
        ),

        // --------------------------------------------------------
        // WAZE
        // --------------------------------------------------------

        Command(
            "waze",
            "Waze",
            aliases(
                "waze",
                "wejz",
                "waze app",
                "waze alkalmazas",
                "navigacio",
                "navigáció"
            ),
            openApp(
                "com.waze",
                "Waze"
            )
        ),

        // --------------------------------------------------------
        // UBER
        // --------------------------------------------------------

        Command(
            "uber",
            "Uber",
            aliases(
                "uber",
                "uber app",
                "uber alkalmazas",
                "ubert",
                "fuvar"
            ),
            openApp(
                "com.ubercab",
                "Uber"
            )
        ),

        // --------------------------------------------------------
        // BOLT
        // --------------------------------------------------------

        Command(
            "bolt",
            "Bolt",
            aliases(
                "bolt",
                "bolt app",
                "bolt alkalmazas",
                "boltot",
                "taxi"
            ),
            openApp(
                "ee.mtakso.client",
                "Bolt"
            )
        ),

        // --------------------------------------------------------
        // GMAIL
        // --------------------------------------------------------

        Command(
            "gmail",
            "Gmail",
            aliases(
                "gmail",
                "g mail",
                "gmail app",
                "gmail alkalmazas",
                "email",
                "e mail",
                "levelek",
                "posta"
            ),
            openApp(
                "com.google.android.gm",
                "Gmail"
            )
        ),

        // --------------------------------------------------------
        // PLAY ÁRUHÁZ
        // --------------------------------------------------------

        Command(
            "playstore",
            "Play Áruház",
            aliases(
                "play aruhaz",
                "play store",
                "playstore",
                "google play",
                "google play store",
                "aruhaz",
                "app aruhaz",
                "alkalmazasok"
            ),
            openApp(
                "com.android.vending",
                "Play Áruház"
            )
        ),

        // --------------------------------------------------------
        // GOOGLE FOTÓK
        // --------------------------------------------------------

        Command(
            "photos",
            "Google Fotók",
            aliases(
                "fotok",
                "google fotok",
                "kepek",
                "galeria",
                "photos"
            ),
            openApp(
                "com.google.android.apps.photos",
                "Google Fotók"
            )
        ),

        // --------------------------------------------------------
        // HANGERŐ
        // --------------------------------------------------------

        Command(
            "volume",
            "hangerő",
            aliases(
                "hangero",
                "hangerő",
                "hangero beallitas",
                "hang beallitas",
                "hang",
                "hangositsd",
                "hangosits",
                "vedd fel a hangot",
                "hangot fel",
                "hangosabb"
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

            "Feljebb vettem a hangerőt."
        },

        // --------------------------------------------------------
        // AKKU
        // --------------------------------------------------------

        Command(
            "battery",
            "akkumulátor",
            aliases(
                "akku",
                "akkumulator",
                "akku szint",
                "akku allapot",
                "akkumulator allapot",
                "toltottseg",
                "hany szazalek az akku",
                "mennyi az akku"
            )
        ) { context ->

            val battery =
                context.getSystemService(
                    Context.BATTERY_SERVICE
                ) as BatteryManager

            val level =
                battery.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CAPACITY
                )

            "Az akkumulátor töltöttsége $level százalék."
        },

        // --------------------------------------------------------
        // FÁJLOK
        // --------------------------------------------------------

        Command(
            "files",
            "fájlkezelő",
            aliases(
                "fajlok",
                "fajlkezelo",
                "dokumentumok",
                "dokumentum",
                "fileok",
                "file kezelo",
                "file manager",
                "mappak"
            )
        ) { context ->

            try {

                val intent =
                    Intent(
                        Intent.ACTION_OPEN_DOCUMENT
                    )
                        .setType("*/*")
                        .addCategory(
                            Intent.CATEGORY_OPENABLE
                        )
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                        )

                context.startActivity(intent)

                "Megnyitom a fájlkezelőt."

            } catch (_: Exception) {

                "Nem sikerült megnyitnom a fájlkezelőt."
            }
        },

        // --------------------------------------------------------
        // SZÁMOLÓGÉP
        // --------------------------------------------------------

        Command(
            "calculator",
            "számológép",
            aliases(
                "szamologep",
                "kalkulator",
                "szamitas",
                "calculator",
                "matek",
                "szamolni"
            )
        ) { context ->

            try {

                val intent =
                    Intent(
                        "android.intent.action.MAIN"
                    ).addCategory(
                        "android.intent.category.APP_CALCULATOR"
                    ).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )

                context.startActivity(intent)

                "Megnyitom a számológépet."

            } catch (_: Exception) {

                "Nem találtam számológép alkalmazást."
            }
        },

        // --------------------------------------------------------
        // ÓRA
        // --------------------------------------------------------

        Command(
            "clock",
            "óra",
            aliases(
                "ora",
                "ebreszto",
                "ebresztoora",
                "riaszto",
                "alarm",
                "clock"
            )
        ) { context ->

            try {

                val intent =
                    Intent(
                        "android.intent.action.SHOW_ALARMS"
                    ).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )

                context.startActivity(intent)

                "Megnyitom az órát és az ébresztőket."

            } catch (_: Exception) {

                "Nem sikerült megnyitnom az órát."
            }
        },

        // --------------------------------------------------------
        // NAPTÁR
        // --------------------------------------------------------

        Command(
            "calendar",
            "naptár",
            aliases(
                "naptar",
                "calendar",
                "esemenyek",
                "programok",
                "talalkozok"
            )
        ) { context ->

            try {

                val intent =
                    Intent(
                        "android.intent.action.MAIN"
                    )
                        .addCategory(
                            "android.intent.category.APP_CALENDAR"
                        )
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                        )

                context.startActivity(intent)

                "Megnyitom a naptárat."

            } catch (_: Exception) {

                "Nem sikerült megnyitnom a naptárat."
            }
        },

        // --------------------------------------------------------
        // TÉRKÉP
        // --------------------------------------------------------

        Command(
            "maps",
            "Google Térkép",
            aliases(
                "google maps",
                "google map",
                "maps",
                "map",
                "terkep",
                "terkepek",
                "navigacio"
            )
        ) { context ->

            try {

                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("geo:0,0?q=")
                    ).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )

                context.startActivity(intent)

                "Megnyitom a Google Térképet."

            } catch (_: Exception) {

                "Nem sikerült megnyitnom a térképet."
            }
        }
    )

    // ============================================================
    // MATCH
    // ============================================================

    private data class Match(
        val command: Command,
        val score: Double
    )

    private fun findMatches(
        input: String
    ): List<Match> {

        val result =
            mutableListOf<Match>()

        for (command in commands) {

            var best = 0.0

            for (alias in command.aliases) {

                best = max(
                    best,
                    commandSimilarity(
                        input,
                        alias
                    )
                )
            }

            if (best >= 0.48) {

                result += Match(
                    command,
                    best
                )
            }
        }

        return result.sortedByDescending {
            it.score
        }
    }

    // ============================================================
    // VÉGREHAJTÁS
    // ============================================================

    fun execute(
        context: Context,
        utterance: String
    ): CommandResult {

        val input =
            normalize(utterance)

        if (input.isBlank()) {

            return CommandResult(
                ResultType.UNKNOWN,
                "Nem hallottam parancsot."
            )
        }

        val matches =
            findMatches(input)

        if (matches.isEmpty()) {

            return CommandResult(
                ResultType.UNKNOWN,
                "Ezt még nem ismertem fel. Mondd másképp."
            )
        }

        val best =
            matches[0]

        // Nagyon biztos találat
        if (best.score >= 0.82) {

            return CommandResult(
                ResultType.EXECUTED,
                best.command.action(context)
            )
        }

        // Két hasonló találat
        if (matches.size >= 2) {

            val second =
                matches[1]

            if (
                second.score >= 0.65 &&
                best.score - second.score <= 0.10
            ) {

                return CommandResult(
                    ResultType.AMBIGUOUS,
                    "Nem vagyok teljesen biztos. Melyiket szeretnéd?",
                    listOf(
                        best.command.label,
                        second.command.label
                    )
                )
            }
        }

        // Közepesen biztos találat
        if (best.score >= 0.62) {

            return CommandResult(
                ResultType.CLARIFICATION,
                "Erre gondoltál: ${best.command.label}?",
                listOf(
                    best.command.label
                )
            )
        }

        return CommandResult(
            ResultType.UNKNOWN,
            "Nem vagyok elég biztos abban, mit mondtál."
        )
    }
}
