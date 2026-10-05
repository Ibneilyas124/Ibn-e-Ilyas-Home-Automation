package com.ibneilyas.home.domain

/** Turns spoken text (English, Roman Urdu, Urdu script) into canonical English words. */
object Lexicon {
    private val dict = HashMap<String, String>()
    private val digitWords = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")

    private fun norm(s: String): String {
        val sb = StringBuilder()
        for (c in s.lowercase()) {
            val code = c.code
            when {
                code in 0x064B..0x065F || code == 0x0670 || code == 0x0640 -> {}
                code in 0x200B..0x200F -> {}
                c == '\u064A' || c == '\u0649' -> sb.append('\u06CC')
                c == '\u0643' -> sb.append('\u06A9')
                c == '\u0647' || c == '\u06BE' -> sb.append('\u06C1')
                c == '\u0624' -> sb.append('\u0648')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun add(to: String, vararg from: String) {
        for (f in from) dict[norm(f)] = to
    }

    init {
        add("on", "آن", "چلاؤ", "چلاو", "چلا", "چالو", "چلائیں", "جلاؤ", "جلاو", "جلا", "جلائیں", "کھولو", "کھول", "کھولیں", "شروع")
        add("on", "chalao", "chalo", "chala", "chalu", "chaloo", "jalao", "jala", "kholo", "khol")
        add("off", "آف", "بند", "بجھاؤ", "بجھاو", "بجھا", "بجھائیں", "روکو", "روک", "ختم")
        add("off", "band", "bandh", "bujhao", "bujha", "roko", "khatam")
        add("light", "لائٹ", "لائٹس", "لایٹ", "لائٹیں", "بتی", "بتیاں", "روشنی", "batti", "bati", "roshni")
        add("bulb", "بلب", "بلپ", "بلبز")
        add("fan", "پنکھا", "پنکھے", "پنکھہ", "پنکھوں", "فین", "pankha", "pankhay", "pankhe", "panka", "punka")
        add("socket", "ساکٹ", "ساکٹس", "پلگ")
        add("all", "سب", "سبھی", "تمام", "ساری", "سارے", "آل", "sab", "sabhi", "tamam", "saari", "sare", "saray")
    }

    init {
        add("please", "براہ", "مہربانی", "پلیز", "کریں", "کرو", "کر", "کرنا", "دو", "دیں", "دیجیے", "دیجئے")
        add("please", "کو", "کی", "کا", "کے", "میں", "ہے", "ہیں", "اور", "ذرا", "مجھے", "میرے", "میری", "میرا", "اس", "یہ")
        add("please", "karo", "kar", "karna", "do", "dein", "kijiye", "ko", "ki", "ka", "ke", "mein", "hai", "hain", "aur", "zara", "mujhe", "meri", "mera", "mere", "plz")
        add("zero", "صفر", "زیرو", "sifar")
        add("one", "ایک", "ek")
        add("three", "تین", "teen")
        add("four", "چار", "char")
        add("five", "پانچ", "panch")
        add("six", "چھ", "chhe", "chay")
        add("seven", "سات", "saat")
        add("eight", "آٹھ", "aath")
        add("nine", "نو", "nau")
    }

    init {
        add("room", "کمرہ", "کمرا", "کمرے", "روم", "kamra", "kamre", "kamray")
        add("bed", "بیڈ")
        add("drawing", "ڈرائنگ")
        add("kitchen", "کچن")
        add("bath", "باتھ")
        add("garage", "گیراج")
        add("workshop", "ورکشاپ")
        add("living", "لیونگ")
        add("main", "مین")
        add("ceiling", "سیلنگ")
        add("exhaust", "ایگزاسٹ")
        add("night", "نائٹ")
        add("good", "گڈ")
        add("home", "ہوم")
        add("mode", "موڈ")
        add("lamp", "لیمپ")
        add("tube", "ٹیوب")
        add("power", "پاور")
        add("heater", "ہیٹر")
        add("geyser", "گیزر")
        add("motor", "موٹر")
        add("pump", "پمپ")
        add("sarfraz", "سرفراز")
        add("sheraz", "شیراز")
    }


    // URDU + ENGLISH MIX
    init {
        add("on", "اون", "اؤن")
        add("off", "اوف", "اؤف", "اف")
        add("turn", "ٹرن")
        add("switch", "سوئچ", "سویچ")
        add("light", "لائیٹ", "لائیٹس")
    }

    init {
        add("sarfraz", "سرفراز", "سرفرز", "سرفراج", "فراز", "فراج")
        add("sarfraz", "sarfaraz", "sarfarz", "sarfraaz", "sarfraj", "farraz", "faraz")
        add("sheraz", "شیراز", "شراز", "sheeraz", "sherazz", "shiraz", "sheraj")
    }
    fun words(s: String): List<String> =
        norm(s).split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }
            .map { w ->
                val m = dict[w]
                when {
                    m != null -> m
                    w.length == 1 && w[0].digitToIntOrNull() != null -> digitWords[w[0].digitToInt()]
                    w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
                    else -> w
                }
            }
            .filter { it != "s" }
}
