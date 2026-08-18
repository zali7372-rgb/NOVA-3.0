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

        return MainActivity
            .normalize(input)
            .replace("alkalmazas", "app")
            .replace("alkalmazast", "app")
            .replace("alkalmazás", "app")
            .replace("programot", "app")
            .replace("program", "app")
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }

    private fun words(text: String): List<String> {

        return normalize(text)
            .split(" ")
            .filter {
                it.length >= 2
            }
    }

    // ============================================================
    // LEVENSHTEIN
    // ============================================================

    private fun levenshtein(
        a: String,
        b: String
    ): Int {

        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) {
            it
        }

        for (i in a.indices) {

            val current =
                IntArray(b.length + 1)

            current[0] = i + 1

            for (j in b.indices) {

                val cost =
                    if (a[i] == b[j]) 0 else 1

                current[j + 1] =
                    min(
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

    // ============================================================
    // FUZZY
    // ============================================================

    private fun fuzzySimilarity(
        a: String,
        b: String
    ): Double {

        val aa = normalize(a)
        val bb = normalize(b)

        if (
            aa.isEmpty() ||
            bb.isEmpty()
        ) {
            return 0.0
        }

        if (aa == bb) {
            return 1.0
        }

        val distance =
            levenshtein(
                aa,
                bb
            )

        val longest =
            max(
                aa.length,
                bb.length
            )

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
            normalizedInput ==
            normalizedAlias
        ) {
            return 1.0
        }

        if (
            normalizedInput.contains(
                normalizedAlias
            ) &&
            normalizedAlias.length >= 3
        ) {
            return 0.97
        }

        if (
            normalizedAlias.contains(
                normalizedInput
            ) &&
            normalizedInput.length >= 4
        ) {
            return 0.90
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

        val starters =
            listOf(
                "",
                "nyisd meg",
                "nyisd ki",
                "inditsd el",
                "inditsd",
                "nyisd fel",
                "menj ide",
                "menj a",
                "ugorj a",
                "mutasd",
                "hozd elo",
                "kapcsold be",
                "nyisd meg nekem",
                "nyisd ki nekem",
                "inditsd el a",
                "nyisd meg a",
                "nyisd ki a"
            )

        for (name in names) {

            val clean =
                MainActivity.normalize(name)

            result += clean

            for (starter in starters) {

                if (starter.isBlank()) {

                    result += clean

                } else {

                    result +=
                        "$starter $clean"
                }
            }

            result += "$clean app"
        }

        return result
            .map {
                normalize(it)
            }
            .filter {
                it.isNotBlank()
            }
            .toList()
    }

    // ============================================================
    // APP MEGNYITÁSA
    // ============================================================

    private fun openApp(
        packageName: String,
        label: String
    ): (Context) -> String = { context ->

        try {

            val launchIntent =
                context.packageManager
                    .getLaunchIntentForPackage(
                        packageName
                    )

            if (launchIntent == null) {

                "$label nincs telepítve ezen a telefonon."

            } else {

                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                )

                context.startActivity(
                    launchIntent
                )

                "Megnyitom a $label alkalmazást."
            }

        } catch (_: Exception) {

            "Nem sikerült megnyitnom a $label alkalmazást."
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
                Intent(action).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }

            context.startActivity(intent)

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

                "A kért beállítás nem érhető el, ezért megnyitottam a rendszerbeállításokat."

            } catch (_: Exception) {

                "Nem sikerült megnyitnom a beállításokat."
            }
        }
    }

    // ============================================================
    // PARANCSOK
    // ============================================================

    private val commands =
        listOf(

            Command(
                "wifi",
                "Wi-Fi",
                aliases(
                    "wifi",
                    "wi fi",
                    "wifit",
                    "wifi beallitas",
                    "wifi beallitasok",
                    "vezetek nelkuli halozat",
                    "wifi kapcsolat",
                    "wifi menu"
                ),
                openSettings(
                    Settings.ACTION_WIFI_SETTINGS,
                    "Megnyitom a Wi-Fi beállításokat."
                )
            ),

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
                    "bluetooth menu"
                ),
                openSettings(
                    Settings.ACTION_BLUETOOTH_SETTINGS,
                    "Megnyitom a Bluetooth beállításokat."
                )
            ),

            Command(
                "display",
                "kijelző",
                aliases(
                    "kijelzo",
                    "kijelzo beallitas",
                    "kijelzo beallitasok",
                    "kepernyo",
                    "kepernyo beallitas",
                    "display",
                    "fenyero",
                    "vilagossag"
                ),
                openSettings(
                    Settings.ACTION_DISPLAY_SETTINGS,
                    "Megnyitom a kijelző beállításait."
                )
            ),

            Command(
                "location",
                "helymeghatározás",
                aliases(
                    "gps",
                    "helymeghatarozas",
                    "helyadatok",
                    "helyzet",
                    "lokacio",
                    "location",
                    "gps beallitas"
                ),
                openSettings(
                    Settings.ACTION_LOCATION_SOURCE_SETTINGS,
                    "Megnyitom a helymeghatározás beállításait."
                )
            ),

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
                    "android.settings.NOTIFICATION_SETTINGS",
                    "Megnyitom az értesítési beállításokat."
                )
            ),

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
                    "android.settings.VPN_SETTINGS",
                    "Megnyitom a VPN beállításokat."
                )
            ),

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
                    "beallitas menu"
                ),
                openSettings(
                    Settings.ACTION_SETTINGS,
                    "Megnyitom a rendszerbeállításokat."
                )
            ),

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
                    "storage"
                ),
                openSettings(
                    Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
                    "Megnyitom a tárhely beállításait."
                )
            ),

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
                    "hot spot"
                ),
                openSettings(
                    "android.settings.TETHER_SETTINGS",
                    "Megnyitom a hotspot beállításait."
                )
            ),

            Command(
                "youtube",
                "YouTube",
                aliases(
                    "youtube",
                    "jutub",
                    "youtub",
                    "youtube video",
                    "videok"
                ),
                openApp(
                    "com.google.android.youtube",
                    "YouTube"
                )
            ),

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
                    "bongeszo",
                    "internet"
                ),
                openApp(
                    "com.android.chrome",
                    "Chrome"
                )
            ),

            Command(
                "discord",
                "Discord",
                aliases(
                    "discord",
                    "diszkord",
                    "disscord",
                    "diskord",
                    "discord chat",
                    "dc",
                    "d c"
                ),
                openApp(
                    "com.discord",
                    "Discord"
                )
            ),

            Command(
                "tiktok",
                "TikTok",
                aliases(
                    "tiktok",
                    "tik tok",
                    "tiktokk",
                    "tiktoc",
                    "tiktok video",
                    "rovid videok"
                ),
                openApp(
                    "com.zhiliaoapp.musically",
                    "TikTok"
                )
            ),

            Command(
                "instagram",
                "Instagram",
                aliases(
                    "instagram",
                    "insta",
                    "insta gram",
                    "instagrm",
                    "instat"
                ),
                openApp(
                    "com.instagram.android",
                    "Instagram"
                )
            ),

            Command(
                "facebook",
                "Facebook",
                aliases(
                    "facebook",
                    "facebok",
                    "feszbuk",
                    "face"
                ),
                openApp(
                    "com.facebook.katana",
                    "Facebook"
                )
            ),

            Command(
                "messenger",
                "Messenger",
                aliases(
                    "messenger",
                    "mesenger",
                    "uzenetek messenger",
                    "chat"
                ),
                openApp(
                    "com.facebook.orca",
                    "Messenger"
                )
            ),

            Command(
                "whatsapp",
                "WhatsApp",
                aliases(
                    "whatsapp",
                    "what app",
                    "whats app",
                    "watsapp",
                    "whatsup"
                ),
                openApp(
                    "com.whatsapp",
                    "WhatsApp"
                )
            ),

            Command(
                "telegram",
                "Telegram",
                aliases(
                    "telegram",
                    "telegran"
                ),
                openApp(
                    "org.telegram.messenger",
                    "Telegram"
                )
            ),

            Command(
                "snapchat",
                "Snapchat",
                aliases(
                    "snapchat",
                    "snap chat",
                    "snap",
                    "snapcsat"
                ),
                openApp(
                    "com.snapchat.android",
                    "Snapchat"
                )
            ),

            Command(
                "x",
                "X",
                aliases(
                    "twitter",
                    "x twitter",
                    "twitter app",
                    "eksz"
                ),
                openApp(
                    "com.twitter.android",
                    "X"
                )
            ),

            Command(
                "reddit",
                "Reddit",
                aliases(
                    "reddit",
                    "red it",
                    "redditet"
                ),
                openApp(
                    "com.reddit.frontpage",
                    "Reddit"
                )
            ),

            Command(
                "spotify",
                "Spotify",
                aliases(
                    "spotify",
                    "spoty",
                    "zene",
                    "zenet",
                    "zenelejatszo"
                ),
                openApp(
                    "com.spotify.music",
                    "Spotify"
                )
            ),

            Command(
                "steam",
                "Steam",
                aliases(
                    "steam",
                    "stim"
                ),
                openApp(
                    "com.valvesoftware.android.steam.community",
                    "Steam"
                )
            ),

            Command(
                "twitch",
                "Twitch",
                aliases(
                    "twitch",
                    "tvis",
                    "twics",
                    "streamek"
                ),
                openApp(
                    "tv.twitch.android.app",
                    "Twitch"
                )
            ),

            Command(
                "netflix",
                "Netflix",
                aliases(
                    "netflix",
                    "netfliks",
                    "netfli",
                    "filmek",
                    "sorozatok"
                ),
                openApp(
                    "com.netflix.mediaclient",
                    "Netflix"
                )
            ),

            Command(
                "waze",
                "Waze",
                aliases(
                    "waze",
                    "wejz",
                    "navigacio"
                ),
                openApp(
                    "com.waze",
                    "Waze"
                )
            ),

            Command(
                "uber",
                "Uber",
                aliases(
                    "uber",
                    "ubert",
                    "fuvar"
                ),
                openApp(
                    "com.ubercab",
                    "Uber"
                )
            ),

            Command(
                "bolt",
                "Bolt",
                aliases(
                    "bolt",
                    "boltot",
                    "taxi"
                ),
                openApp(
                    "ee.mtakso.client",
                    "Bolt"
                )
            ),

            Command(
                "gmail",
                "Gmail",
                aliases(
                    "gmail",
                    "g mail",
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
                    "app aruhaz"
                ),
                openApp(
                    "com.android.vending",
                    "Play Áruház"
                )
            ),

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

                if (level >= 0) {
                    "Az akkumulátor töltöttsége $level százalék."
                } else {
                    "Nem tudtam lekérni az akkumulátor töltöttségét."
                }
            },

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
                        ).apply {

                            type = "*/*"

                            addCategory(
                                Intent.CATEGORY_OPENABLE
                            )

                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                        }

                    context.startActivity(intent)

                    "Megnyitom a fájlkezelőt."

                } catch (_: Exception) {

                    "Nem sikerült megnyitnom a fájlkezelőt."
                }
            },

            Command(
                "calculator",
                "számológép",
                aliases(
                    "szamologep",
                    "kalkulator",
                    "calculator",
                    "matek",
                    "szamolni"
                )
            ) { context ->

                try {

                    val intent =
                        Intent(
                            Intent.ACTION_MAIN
                        ).apply {

                            addCategory(
                                "android.intent.category.APP_CALCULATOR"
                            )

                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                        }

                    context.startActivity(intent)

                    "Megnyitom a számológépet."

                } catch (_: Exception) {

                    "Nem találtam számológép alkalmazást."
                }
            },

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
                        ).apply {

                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                        }

                    context.startActivity(intent)

                    "Megnyitom az órát és az ébresztőket."

                } catch (_: Exception) {

                    "Nem sikerült megnyitnom az órát."
                }
            },

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
                            Intent.ACTION_MAIN
                        ).apply {

                            addCategory(
                                "android.intent.category.APP_CALENDAR"
                            )

                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                        }

                    context.startActivity(intent)

                    "Megnyitom a naptárat."

                } catch (_: Exception) {

                    "Nem sikerült megnyitnom a naptárat."
                }
            },

            Command(
                "maps",
                "Google Térkép",
                aliases(
                    "google maps",
                    "google map",
                    "maps",
                    "map",
                    "terkep",
                    "terkepek"
                )
            ) { context ->

                try {

                    val intent =
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                "geo:0,0?q="
                            )
                        ).apply {

                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                        }

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

            if (best >= 0.50) {

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

        // Egy közepesen erős találat
        if (best.score >= 0.65) {

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
