package com.aaii.yctamember

/**
 * Yangon Region's 2022 14-district layout.
 * YCTA's requested 44-township view omits Cocokyun (Coco Islands);
 * the full regional administrative total is 45.
 */
object YctaGeography {
    data class Township(
        val english: String,
        val myanmar: String,
        val aliases: List<String> = emptyList()
    ) {
        val searchTerms: List<String> get() = listOf(myanmar, english) + aliases
    }

    data class District(
        val english: String,
        val myanmar: String,
        val townships: List<Township>
    )

    private fun t(en: String, mm: String, vararg aliases: String) =
        Township(en, mm, aliases.toList())

    val districts = listOf(
        District("Taikkyi", "တိုက်ကြီး", listOf(
            t("Taikkyi", "တိုက်ကြီး", "Taik Kyi", "Taikgyi")
        )),
        District("Hlegu", "လှည်းကူး", listOf(
            t("Hlegu", "လှည်းကူး", "Hle Gu")
        )),
        District("Hmawbi", "မှော်ဘီ", listOf(
            t("Hmawbi", "မှော်ဘီ"),
            t("Htantabin", "ထန်းတပင်", "Htantapin")
        )),
        District("Mingaladon", "မင်္ဂလာဒုံ", listOf(
            t("Mingaladon", "မင်္ဂလာဒုံ"),
            t("Shwepyitha", "ရွှေပြည်သာ", "Shwe Pyi Thar")
        )),
        District("Insein", "အင်းစိန်", listOf(
            t("Insein", "အင်းစိန်"),
            t("Hlaingthaya (East)", "လှိုင်သာယာ (အရှေ့ပိုင်း)",
                "East Hlaingthaya", "Hlaing Thar Yar East", "လှိုင်သာယာအရှေ့ပိုင်း"),
            t("Hlaingthaya (West)", "လှိုင်သာယာ (အနောက်ပိုင်း)",
                "West Hlaingthaya", "Hlaing Thar Yar West", "လှိုင်သာယာအနောက်ပိုင်း")
        )),
        District("Thanlyin", "သန်လျင်", listOf(
            t("Thanlyin", "သန်လျင်", "Syriam"),
            t("Thongwa", "သုံးခွ", "Thongwa"),
            t("Kyauktan", "ကျောက်တန်း"),
            t("Kayan", "ခရမ်း")
        )),
        District("Twantay", "တွံတေး", listOf(
            t("Twante", "တွံတေး", "Twantay"),
            t("Kungyangon", "ကွမ်းခြံကုန်း", "Kungyangone"),
            t("Kawhmu", "ကော့မှူး"),
            t("Seikkyi Kanaungto", "ဆိပ်ကြီးခနောင်တို", "Seikkyi Khanaungto"),
            t("Dala", "ဒလ", "Dallah")
        )),
        District("Kyauktada", "ကျောက်တံတား", listOf(
            t("Kyauktada", "ကျောက်တံတား"),
            t("Pabedan", "ပန်းဘဲတန်း"),
            t("Lanmadaw", "လမ်းမတော်"),
            t("Latha", "လသာ"),
            t("Dagon", "ဒဂုံ")
        )),
        District("Ahlon", "အလုံ", listOf(
            t("Ahlon", "အလုံ", "Ahlone"),
            t("Kyeemyindaing", "ကြည့်မြင်တိုင်", "Kyimyindaing"),
            t("Sanchaung", "စမ်းချောင်း")
        )),
        District("Mayangon", "မရမ်းကုန်း", listOf(
            t("Mayangon", "မရမ်းကုန်း", "Mayangone"),
            t("Hlaing", "လှိုင်"),
            t("North Okkalapa", "မြောက်ဥက္ကလာပ", "North Okkalapa Township")
        )),
        District("Thingangyun", "သင်္ဃန်းကျွန်း", listOf(
            t("Thingangyun", "သင်္ဃန်းကျွန်း", "Thingangyung"),
            t("South Okkalapa", "တောင်ဥက္ကလာပ"),
            t("Tamwe", "တာမွေ"),
            t("Yankin", "ရန်ကင်း")
        )),
        District("Botahtaung", "ဗိုလ်တထောင်", listOf(
            t("Botataung", "ဗိုလ်တထောင်", "Botahtaung"),
            t("Dawbon", "ဒေါပုံ", "Dawpone"),
            t("Mingala Taungnyunt", "မင်္ဂလာတောင်ညွန့်",
                "Mingalar Taung Nyunt"),
            t("Pazundaung", "ပုဇွန်တောင်", "Pazundaung"),
            t("Thaketa", "သာကေတ")
        )),
        District("Dagon Myothit", "ဒဂုံမြို့သစ်", listOf(
            t("Dagon Seikkan", "ဒဂုံဆိပ်ကမ်း", "Dagon Myothit Seikkan"),
            t("South Dagon", "ဒဂုံမြို့သစ် (တောင်ပိုင်း)", "Dagon South", "တောင်ဒဂုံ"),
            t("North Dagon", "ဒဂုံမြို့သစ် (မြောက်ပိုင်း)", "Dagon North", "မြောက်ဒဂုံ"),
            t("East Dagon", "ဒဂုံမြို့သစ် (အရှေ့ပိုင်း)", "Dagon East", "အရှေ့ဒဂုံ")
        )),
        District("Kamayut", "ကမာရွတ်", listOf(
            t("Kamayut", "ကမာရွတ်"),
            t("Bahan", "ဗဟန်း")
        ))
    )

    val townshipCount: Int get() = districts.sumOf { it.townships.size }

    fun normalized(text: String): String =
        text.lowercase().replace(Regex("[\\s._()/-]"), "")
            .replace("township", "").replace("မြို့နယ်", "")

    fun matchesTownship(profileDistrict: String, town: Township): Boolean {
        val profile = normalized(profileDistrict)
        if (profile.isBlank()) return false
        val terms = town.searchTerms.map(::normalized)
        // Avoid treating both Hlaingthaya subdivisions as the same township
        // unless the original source explicitly names east/west.
        if (town.english.startsWith("Hlaingthaya")) {
            val east = town.english.contains("East")
            val direction = if (east) listOf("east","အရှေ့") else listOf("west","အနောက်")
            return terms.any { it.length >= 3 && profile.contains(it) } &&
                direction.any { profile.contains(normalized(it)) }
        }
        return terms.any { it.length >= 3 && profile.contains(it) }
    }
}
