private var novaActive = false
private var waitingForClarification = false

private fun handleSpeech(raw: String) {

    val normalized = normalize(raw)

    if (normalized.isBlank()) return

    // ------------------------------------------------------------
    // NOVA ÉBRESZTŐSZÓ
    // ------------------------------------------------------------

    val wakeWords = listOf(
        "nova",
        "novaa",
        "nóva",
        "novaa",
        "novi",
        "nova gyere",
        "hé nova",
        "hej nova",
        "hello nova",
        "szia nova"
    )

    val containsWakeWord =
        wakeWords.any {
            normalized.contains(normalize(it))
        }

    if (containsWakeWord) {
        novaActive = true

        val command = normalized
            .replace(Regex("\\bnova\\b"), "")
            .trim()

        // Csak "Nova" hangzott el.
        if (command.isBlank()) {
            reply("Igen? Hallgatlak.")
            return
        }

        executeNovaCommand(command)
        return
    }

    // ------------------------------------------------------------
    // MÁR AKTÍV NOVA
    // ------------------------------------------------------------

    if (novaActive) {

        if (waitingForClarification) {
            handleClarification(normalized)
            return
        }

        executeNovaCommand(normalized)
        return
    }

    // ------------------------------------------------------------
    // NOVA NINCS AKTIVÁLVA
    // ------------------------------------------------------------

    status.text = "Ébresztőszóra várok: Nova"
}
