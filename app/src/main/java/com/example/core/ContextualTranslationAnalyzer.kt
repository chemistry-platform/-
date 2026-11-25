package com.example.core

import com.example.domain.model.DetectedIdiom
import java.util.Locale

/**
 * Fast, 100% offline, deterministic semantic & contextual analyzer for EN <-> AR.
 * Identifies idioms, phrasal verbs, and polysemous collocations before and after
 * on-device ML Kit inference so contextual phrases (e.g., "running a company" -> "يدير شركة")
 * are never translated literally word-for-word.
 */
object ContextualTranslationAnalyzer {

    data class IdiomKnowledge(
        val phrase: String,
        val englishMeaning: String,
        val arabicMeaning: String,
        val example: String = "",
        val isPhrasalVerb: Boolean = false,
        val usageNote: String? = null
    )

    private data class EnContextRewriteRule(
        val regex: Regex,
        val replacementBuilder: (MatchResult) -> String,
        val literalArBugRegex: Regex? = null,
        val fixedArabicVerb: String? = null
    )

    private data class ArContextRewriteRule(
        val regex: Regex,
        val englishEquivalent: String
    )

    // Curated high-frequency idioms & phrasal verbs used for offline contextual recognition
    // and sentence-level disambiguation alongside live Wiktionary lookups.
    private val CURATED_EN_IDIOMS: List<IdiomKnowledge> = listOf(
        IdiomKnowledge(
            phrase = "in the long run",
            englishMeaning = "over a long period of time; ultimately",
            arabicMeaning = "على المدى الطويل؛ في نهاية المطاف",
            example = "Investing in education pays off in the long run.",
            usageNote = "تعبير اصطلاحي يدل على النتيجة النهائية بعد فترة طويلة"
        ),
        IdiomKnowledge(
            phrase = "in the short run",
            englishMeaning = "over a brief period of time; immediately",
            arabicMeaning = "على المدى القصير",
            example = "The plan looks costly in the short run.",
            usageNote = "تعبير اصطلاحي يشير إلى الفترة القريبة"
        ),
        IdiomKnowledge(
            phrase = "on the run",
            englishMeaning = "fleeing from the authorities or continually busy",
            arabicMeaning = "هاربٌ؛ في حالة فِرار أو انشغال دائم",
            example = "The suspect was on the run for weeks.",
            usageNote = "تعبير اصطلاحي: الهروب أو الاستعجال المتواصل"
        ),
        IdiomKnowledge(
            phrase = "run out of",
            englishMeaning = "to exhaust the supply of something; have none left",
            arabicMeaning = "يَنْفَدُ مِنْ؛ يَسْتَنْفِدُ",
            example = "We are running out of time.",
            isPhrasalVerb = true,
            usageNote = "فعل مركب: run out of + [اسم]"
        ),
        IdiomKnowledge(
            phrase = "run into",
            englishMeaning = "to meet someone unexpectedly or encounter a problem",
            arabicMeaning = "يُصَادِفُ (شخصاً)؛ يَوَاجِهُ (مشكلة)",
            example = "I ran into an old friend at the station.",
            isPhrasalVerb = true,
            usageNote = "فعل مركب متعدٍّ: run into + [person/problem]"
        ),
        IdiomKnowledge(
            phrase = "run across",
            englishMeaning = "to find or meet by chance",
            arabicMeaning = "يَعْثُرُ عَلَى صُدْفَةً؛ يُصَادِفُ",
            example = "She ran across an interesting article yesterday.",
            isPhrasalVerb = true,
            usageNote = "فعل مركب: run across + [something/someone]"
        ),
        IdiomKnowledge(
            phrase = "run over",
            englishMeaning = "to exceed a time limit, review quickly, or hit with a vehicle",
            arabicMeaning = "يَتَجَاوَزُ (الوقت)؛ يُرَاجِعُ سَرِيعاً؛ يَدْهَسُ",
            example = "The meeting ran over by twenty minutes.",
            isPhrasalVerb = true,
            usageNote = "فعل مركب متعدد السياقات حسب المفعول"
        ),
        IdiomKnowledge(
            phrase = "run out of steam",
            englishMeaning = "to lose energy, momentum, or enthusiasm before finishing",
            arabicMeaning = "تَخُورُ قُوَاهُ؛ يَفْقِدُ حَمَاسَهُ أَوْ زَخَمَهُ",
            example = "The campaign ran out of steam halfway through the month.",
            usageNote = "تعبير اصطلاحي يعني التوقف أو الفتور بعد انطلاقة قوية"
        ),
        IdiomKnowledge(
            phrase = "run wild",
            englishMeaning = "to behave without control or restraint",
            arabicMeaning = "يَنْفَلِتُ؛ يَسْرَحُ دُونَ رَقِيبٍ أَوْ قَيْدٍ",
            example = "Her imagination ran wild after reading the story.",
            usageNote = "تعبير اصطلاحي يدل على عدم الانضباط أو جموح الخيال"
        ),
        IdiomKnowledge(
            phrase = "run the risk",
            englishMeaning = "to do something that might result in a bad outcome or loss",
            arabicMeaning = "يُخَاطِرُ بِـ؛ يُعَرِّضُ نَفْسَهُ لِخَطَرِ",
            example = "If you skip backups, you run the risk of losing your files.",
            usageNote = "تعبير اصطلاحي: run the risk of + [noun/-ing]"
        ),
        IdiomKnowledge(
            phrase = "dry run",
            englishMeaning = "a rehearsal or practice session before the actual event",
            arabicMeaning = "تَجْرِبَةٌ تَمْهِيدِيَّةٌ؛ بَرُوفَا قَبْلَ التَّنْفِيذِ الفِعْلِيِّ",
            example = "Let's do a dry run of the presentation before tomorrow.",
            usageNote = "تعبير اصطلاحي عملي"
        ),
        IdiomKnowledge(
            phrase = "hit and run",
            englishMeaning = "a road accident in which the driver flees without stopping, or a brief sudden attack",
            arabicMeaning = "حَادِثُ دَهْسٍ وَفِرَارٍ؛ هُجُومٌ خَاطِفٌ",
            example = "Police are investigating a hit and run near the square.",
            usageNote = "تعبير اصطلاحي قانوني وعام"
        ),
        IdiomKnowledge(
            phrase = "cut and run",
            englishMeaning = "to leave a difficult situation suddenly in order to escape trouble",
            arabicMeaning = "يَلُوذُ بِالْفِرَارِ؛ يَنْسَحِبُ سَرِيعاً عِنْدَ الأَزْمَةِ",
            example = "Instead of solving the crisis, the partner decided to cut and run.",
            usageNote = "تعبير اصطلاحي يدل على الهروب من المسؤولية"
        ),
        IdiomKnowledge(
            phrase = "in the running",
            englishMeaning = "having a realistic chance of winning or being selected",
            arabicMeaning = "فِي دَائِرَةِ الْمُنَافَسَةِ؛ مُرَشَّحٌ بِقُوَّةٍ",
            example = "Three candidates are still in the running for the award.",
            usageNote = "تعبير اصطلاحي في المنافسات والانتخابات"
        ),
        IdiomKnowledge(
            phrase = "spring into action",
            englishMeaning = "to suddenly become active and start working or responding quickly",
            arabicMeaning = "يَنْطَلِقُ لِلْعَمَلِ فَوْراً؛ يَتَحَرَّكُ بِسُرْعَةٍ",
            example = "Firefighters sprang into action as soon as the alarm rang.",
            usageNote = "تعبير اصطلاحي يصف الاستجابة السريعة والحاسمة"
        ),
        IdiomKnowledge(
            phrase = "take action",
            englishMeaning = "to do something official or decisive to deal with a situation",
            arabicMeaning = "يَتَّخِذُ إِجْرَاءً؛ يَتَحَرَّكُ لِمُعَالَجَةِ الأَمْرِ",
            example = "The committee must take action immediately.",
            usageNote = "متلازمة وتعبير شائع في السياق الرسمي والعملي"
        ),
        IdiomKnowledge(
            phrase = "actions speak louder than words",
            englishMeaning = "what people actually do matters more than what they say",
            arabicMeaning = "الأَفْعَالُ أَبْلَغُ مِنَ الأَقْوَالِ",
            example = "Don't just promise to help—actions speak louder than words.",
            usageNote = "مثل إنجليزي شائع"
        ),
        IdiomKnowledge(
            phrase = "out of action",
            englishMeaning = "not working, broken, or unable to participate due to injury",
            arabicMeaning = "خَارِجَ الخِدْمَةِ؛ مُعَطَّلٌ أَوْ غَيْرُ قَادِرٍ عَلَى العَمَلِ",
            example = "The main elevator has been out of action all morning.",
            usageNote = "تعبير اصطلاحي للأجهزة المعطّلة أو اللاعبين المصابين"
        ),
        IdiomKnowledge(
            phrase = "put into action",
            englishMeaning = "to start using a plan, idea, or system in practice",
            arabicMeaning = "يَضَعُ مَوْضِعَ التَّنْفِيذِ",
            example = "It is time to put our strategy into action.",
            usageNote = "تعبير اصطلاحي يعني البدء الفعلي بالتطبيق"
        ),
        IdiomKnowledge(
            phrase = "call it a day",
            englishMeaning = "to stop working for the rest of the day",
            arabicMeaning = "يُنْهِي العَمَلَ لِهَذَا اليَوْمِ؛ يَكْتَفِي بِمَا أَنْجَزَ",
            example = "We've finished the report, so let's call it a day.",
            usageNote = "تعبير اصطلاحي غير رسمي"
        ),
        IdiomKnowledge(
            phrase = "under the weather",
            englishMeaning = "feeling slightly sick or unwell",
            arabicMeaning = "يَشْعُرُ بِتَوَعُّكٍ خَفِيفٍ؛ لَيْسَ عَلَى مَا يُرَامُ صِحِّيّاً",
            example = "I am feeling a bit under the weather today.",
            usageNote = "تعبير اصطلاحي مجازي للحالة الصحية"
        ),
        IdiomKnowledge(
            phrase = "piece of cake",
            englishMeaning = "something very easy to do",
            arabicMeaning = "أَمْرٌ فِي غَايَةِ السُّهُولَةِ",
            example = "The exam was a piece of cake.",
            usageNote = "تعبير اصطلاحي غير رسمي"
        ),
        IdiomKnowledge(
            phrase = "once in a blue moon",
            englishMeaning = "very rarely; almost never",
            arabicMeaning = "نَادِراً جِدّاً؛ فِي النَّادِرِ",
            example = "He visits us once in a blue moon.",
            usageNote = "تعبير اصطلاحي للدلالة على الندرة الشديدة"
        ),
        IdiomKnowledge(
            phrase = "cost an arm and a leg",
            englishMeaning = "to be extremely expensive",
            arabicMeaning = "يُكَلِّفُ مَبْلَغاً بَاهِظاً جِدّاً",
            example = "Repairing the engine cost an arm and a leg.",
            usageNote = "تعبير اصطلاحي للمبالغة في التكلفة"
        ),
        IdiomKnowledge(
            phrase = "break a leg",
            englishMeaning = "used to wish a performer good luck",
            arabicMeaning = "حَظّاً مُوَفَّقاً (تُقَالُ تَمَنِّياً لِلنَّجَاحِ)",
            example = "Break a leg on stage tonight!",
            usageNote = "تعبير اصطلاحي مجازي لا يُقصد به المعنى الحرفي"
        ),
        IdiomKnowledge(
            phrase = "see eye to eye",
            englishMeaning = "to agree fully with someone",
            arabicMeaning = "يَتَّفِقُ تَمَاماً فِي الرَّأْيِ",
            example = "They don't always see eye to eye on politics.",
            usageNote = "تعبير اصطلاحي يدل على التوافق في الرأي"
        ),
        IdiomKnowledge(
            phrase = "at the end of the day",
            englishMeaning = "ultimately; when everything is considered",
            arabicMeaning = "فِي نِهَايَةِ المَطَافِ؛ فِي الخُلَاصَةِ",
            example = "At the end of the day, quality matters most.",
            usageNote = "تعبير اصطلاحي شائع في الكلام والنقاش"
        ),
        IdiomKnowledge(
            phrase = "take for granted",
            englishMeaning = "to fail to appreciate someone or something properly",
            arabicMeaning = "يَعْتَبِرُ الشَّيْءَ مُسَلَّماً بِهِ دُونَ تَقْدِيرٍ",
            example = "We often take clean water for granted.",
            usageNote = "تعبير اصطلاحي شائع"
        ),
        IdiomKnowledge(
            phrase = "bear in mind",
            englishMeaning = "to remember and consider a fact when deciding",
            arabicMeaning = "يَضَعُ فِي الحُسْبَانِ؛ يَتَذَكَّرُ",
            example = "Bear in mind that traffic is heavy at 5 PM.",
            usageNote = "تعبير اصطلاحي رسمي وشائع"
        ),
        IdiomKnowledge(
            phrase = "make up one's mind",
            englishMeaning = "to make a firm decision",
            arabicMeaning = "يَحْسِمُ أَمْرَهُ؛ يَتَّخِذُ قَرَاراً",
            example = "She finally made up her mind to study medicine.",
            usageNote = "تعبير اصطلاحي"
        ),
        IdiomKnowledge(
            phrase = "figure out",
            englishMeaning = "to understand, solve, or discover by thinking",
            arabicMeaning = "يَسْتَنْتِجُ؛ يَحُلُّ؛ يَفْهَمُ",
            example = "I can't figure out how this device works.",
            isPhrasalVerb = true,
            usageNote = "فعل مركب شائع"
        ),
        IdiomKnowledge(
            phrase = "carry out",
            englishMeaning = "to conduct, perform, or complete a task or plan",
            arabicMeaning = "يُنَفِّذُ؛ يُجْرِي",
            example = "Researchers carried out a detailed study.",
            isPhrasalVerb = true,
            usageNote = "فعل مركب متعدٍّ"
        ),
        IdiomKnowledge(
            phrase = "give up",
            englishMeaning = "to stop trying or quit a habit",
            arabicMeaning = "يَسْتَسْلِمُ؛ يُقْلِعُ عَنْ",
            example = "Never give up on your goals.",
            isPhrasalVerb = true,
            usageNote = "فعل مركب"
        ),
        IdiomKnowledge(
            phrase = "look forward to",
            englishMeaning = "to feel excited and pleased about something that will happen",
            arabicMeaning = "يَتَطَلَّعُ بِشَوْقٍ إِلَى",
            example = "I look forward to hearing from you.",
            isPhrasalVerb = true,
            usageNote = "فعل مركب يتبعه اسم أو مصدر (-ing)"
        ),
        IdiomKnowledge(
            phrase = "break the ice",
            englishMeaning = "to say or do something to relieve awkwardness or tension and start conversation",
            arabicMeaning = "يَكْسِرُ الجَلِيدَ؛ يُزِيلُ الحَوَاجِزَ فِي بَدْءِ الحَدِيثِ",
            example = "He told a joke to break the ice at the meeting.",
            usageNote = "تعبير اصطلاحي اجتماعي شائع"
        ),
        IdiomKnowledge(
            phrase = "bite the bullet",
            englishMeaning = "to force yourself to accept or do something difficult or unpleasant",
            arabicMeaning = "يَتَجَرَّعُ الصَّعْبَ؛ يُوَاجِهُ الأَمْرَ الصَّعْبَ بِشَجَاعَةٍ",
            example = "We have to bite the bullet and pay for the repair.",
            usageNote = "تعبير اصطلاحي مجازي"
        ),
        IdiomKnowledge(
            phrase = "hit the nail on the head",
            englishMeaning = "to describe exactly what is causing a situation or problem",
            arabicMeaning = "يُصِيبُ كَبِدَ الحَقِيقَةِ؛ يُصِيبُ المَعْنَى تَمَاماً",
            example = "You hit the nail on the head with that analysis.",
            usageNote = "تعبير اصطلاحي يدل على دقة الملاحظة"
        ),
        IdiomKnowledge(
            phrase = "burn the midnight oil",
            englishMeaning = "to work or study late into the night",
            arabicMeaning = "يَسْهَرُ حَتَّى وَقْتٍ مُتَأَخِّرٍ فِي العَمَلِ أَوِ الدِّرَاسَةِ",
            example = "Students often burn the midnight oil before final exams.",
            usageNote = "تعبير اصطلاحي مجازي عن الاجتهاد والسهر"
        ),
        IdiomKnowledge(
            phrase = "spill the beans",
            englishMeaning = "to reveal a secret or surprise intentionally or by accident",
            arabicMeaning = "يُفْشِي السِّرَّ؛ يَكْشِفُ المَسْتُورَ",
            example = "Don't spill the beans about the surprise party.",
            usageNote = "تعبير اصطلاحي غير رسمي"
        ),
        IdiomKnowledge(
            phrase = "on the same page",
            englishMeaning = "having the same understanding and agreement about how to proceed",
            arabicMeaning = "عَلَى وِفَاقٍ تَامٍّ؛ مُتَّفِقُونَ فِي الفَهْمِ وَالتَّوَجُّهِ",
            example = "Let's review the plan so everyone is on the same page.",
            usageNote = "تعبير اصطلاحي عملي"
        ),
        IdiomKnowledge(
            phrase = "get out of hand",
            englishMeaning = "to become chaotic and impossible to control",
            arabicMeaning = "يَخْرُجُ عَنِ السَّيْطَرَةِ",
            example = "The argument quickly got out of hand.",
            usageNote = "تعبير اصطلاحي يدل على فقدان السيطرة"
        )
    )

    private val CURATED_AR_IDIOMS: List<IdiomKnowledge> = listOf(
        IdiomKnowledge(
            phrase = "على قدم وساق",
            englishMeaning = "in full swing; actively underway",
            arabicMeaning = "تَجْرِي بِجِدٍّ وَنَشَاطٍ مُتَوَاصِلٍ",
            example = "التحضيرات للمؤتمر تجري على قدم وساق.",
            usageNote = "تعبير اصطلاحي عربي فصيح يدل على سرعة العمل وجديته"
        ),
        IdiomKnowledge(
            phrase = "في نهاية المطاف",
            englishMeaning = "in the long run; ultimately",
            arabicMeaning = "أَخِيراً؛ بَعْدَ كُلِّ شَيْءٍ",
            example = "في نهاية المطاف، الحقيقة ستظهر.",
            usageNote = "تعبير اصطلاحي للاستنتاج النهائي"
        ),
        IdiomKnowledge(
            phrase = "بين عشية وضحاها",
            englishMeaning = "overnight; very suddenly",
            arabicMeaning = "فِي وَقْتٍ قَصِيرٍ جِدّاً؛ فَجْأَةً",
            example = "لا يتحقق النجاح بين عشية وضحاها.",
            usageNote = "تعبير اصطلاحي فصيح"
        ),
        IdiomKnowledge(
            phrase = "ضرب عصفورين بحجر واحد",
            englishMeaning = "to kill two birds with one stone",
            arabicMeaning = "حَقَّقَ هَدَفَيْنِ بِعَمَلٍ وَاحِدٍ",
            example = "بالتعلم أثناء السفر، ضرب عصفورين بحجر واحد.",
            usageNote = "مثل وتعبير اصطلاحي شائع"
        ),
        IdiomKnowledge(
            phrase = "أخذ بعين الاعتبار",
            englishMeaning = "to take into consideration; bear in mind",
            arabicMeaning = "رَاعَى؛ حَسَبَ حِسَابَ",
            example = "يجب أخذ الظروف المحيطة بعين الاعتبار.",
            usageNote = "تعبير رسمي وشائع"
        ),
        IdiomKnowledge(
            phrase = "في متناول اليد",
            englishMeaning = "within reach; accessible and available",
            arabicMeaning = "قَرِيبٌ وَسَهْلُ المَنَالِ",
            example = "أصبحت المعلومات اليوم في متناول اليد.",
            usageNote = "تعبير اصطلاحي"
        ),
        IdiomKnowledge(
            phrase = "يدير شركة",
            englishMeaning = "to manage or run a company",
            arabicMeaning = "يَتَوَلَّى إِدَارَةَ شَرِكَةٍ وَتَسْيِيرَ أَعْمَالِهَا",
            example = "هذا الرجل يدير شركة بنجاح.",
            usageNote = "تعبير سياقي (الإدارة وليس الجري)"
        ),
        IdiomKnowledge(
            phrase = "على أحر من الجمر",
            englishMeaning = "on tenterhooks; eagerly and anxiously waiting",
            arabicMeaning = "فِي غَايَةِ الشَّوْقِ وَالتَّرَقُّبِ وَالقَلَقِ",
            example = "ننتظر نتائج الاختبار على أحر من الجمر.",
            usageNote = "تعبير اصطلاحي عربي بليغ يدل على شدة الانتظار"
        ),
        IdiomKnowledge(
            phrase = "في غمضة عين",
            englishMeaning = "in the blink of an eye; instantaneously",
            arabicMeaning = "بِسُرْعَةٍ فَائِقَةٍ؛ فِي لَحْظَةٍ خَاطِفَةٍ",
            example = "تغيّرت الأحوال في غمضة عين.",
            usageNote = "تعبير اصطلاحي للسرعة الشديدة"
        ),
        IdiomKnowledge(
            phrase = "لا يمت بصلة",
            englishMeaning = "to have nothing to do with; completely unrelated",
            arabicMeaning = "لَا عَلَاقَةَ لَهُ بِالأَمْرِ إِطْلَاقاً",
            example = "هذا الادعاء لا يمت بصلة إلى الحقيقة.",
            usageNote = "تعبير اصطلاحي فصيح لنفي أي صلة"
        ),
        IdiomKnowledge(
            phrase = "يصب الزيت على النار",
            englishMeaning = "to add fuel to the fire; worsen a conflict",
            arabicMeaning = "يُزِيدُ الأَزْمَةَ اشْتِعَالاً وَتَفَاقُماً",
            example = "تصريحاته لم تهدّئ الموقف بل صبّت الزيت على النار.",
            usageNote = "تعبير اصطلاحي مجازي"
        ),
        IdiomKnowledge(
            phrase = "من باب أولى",
            englishMeaning = "all the more so; even more importantly",
            arabicMeaning = "بِطَرِيقَةِ الأَحْرَى وَالأَجْدَرِ",
            example = "إذا احترمت القوانين الصغيرة فمن باب أولى احترام الكبرى.",
            usageNote = "تعبير فقهي وفصيح شائع"
        )
    )

    private val EN_FULL_IDIOM_OVERRIDES: Map<String, String> = mapOf(
        "actions speak louder than words" to "الأفعال أبلغ من الأقوال.",
        "in the long run" to "على المدى الطويل",
        "spring into action" to "ينطلق للعمل فوراً",
        "out of action" to "خارج الخدمة",
        "break the ice" to "يكسر الحاجز ويمهّد للحديث",
        "bite the bullet" to "يتجرّع الصعب بشجاعة",
        "hit the nail on the head" to "أصاب كبد الحقيقة",
        "spill the beans" to "يفشي السر",
        "burn the midnight oil" to "يسهر في العمل أو الدراسة"
    )

    private val AR_FULL_IDIOM_OVERRIDES: Map<String, String> = mapOf(
        "الافعال ابلغ من الاقوال" to "Actions speak louder than words.",
        "ضرب عصفورين بحجر واحد" to "Kill two birds with one stone.",
        "على قدم وساق" to "In full swing.",
        "بين عشية وضحاها" to "Overnight.",
        "في نهاية المطاف" to "In the long run.",
        "على احر من الجمر" to "Waiting eagerly on tenterhooks.",
        "في غمضة عين" to "In the blink of an eye.",
        "لا يمت بصلة" to "Has nothing to do with it."
    )

    private val MANAGEMENT_OBJECTS =
        "company|companies|business|businesses|firm|firms|corporation|enterprise|startup|factory|factories|organization|organisation|department|school|shop|store|hotel|restaurant|clinic|hospital|team|project|projects|campaign|operation|operations|agency|office|plant|bank|branch"
    private val COMPUTING_OBJECTS =
        "program|programs|software|app|apps|application|script|scripts|code|command|test|tests|simulation|scan|query|server|system|systems|tool|engine|model|process"
    private val ELECTION_TARGETS =
        "president|office|mayor|governor|parliament|congress|senate|council|election|re-election|reelection|chair|seat"
    private val FLUID_SUBJECTS =
        "water|river|stream|tap|faucet|tears|tear|nose|blood|paint|ink|sweat|liquid"
    private val EXHAUSTION_OBJECTS =
        "time|money|cash|fuel|gas|petrol|water|food|milk|sugar|battery|power|patience|luck|ideas|options|space|stock|supplies"

    private val EN_CONTEXT_RULES: List<EnContextRewriteRule> = listOf(
        // "run/runs/running/ran + management object" -> manage/manages/managing/managed
        EnContextRewriteRule(
            regex = Regex(
                "\\b(run|runs|running|ran)\\s+((?:a|an|the|this|that|my|your|his|her|our|their|several|many|multiple|large|small|successful|own)\\s+)*(?:$MANAGEMENT_OBJECTS)\\b",
                RegexOption.IGNORE_CASE
            ),
            replacementBuilder = { match ->
                val verb = match.groupValues[1]
                val middle = match.value.substring(verb.length)
                mapVerbPreservingTense(verb, base = "manage", third = "manages", ing = "managing", past = "managed") + middle
            },
            literalArBugRegex = Regex("(?<=^|\\s)(يجري|تجري|يركض|تركض|أجري|جرى|ركض)(\\s+(?:هذا|هذه|شركة|شركات|عمل|مؤسسة|مشروع|مصنع))"),
            fixedArabicVerb = "يدير"
        ),
        // "run/runs/running/ran + software/program/test" -> execute/launch/operate
        EnContextRewriteRule(
            regex = Regex(
                "\\b(run|runs|running|ran)\\s+((?:a|an|the|this|that|my|your|his|her|our|their|new|latest)\\s+)*(?:$COMPUTING_OBJECTS)\\b",
                RegexOption.IGNORE_CASE
            ),
            replacementBuilder = { match ->
                val verb = match.groupValues[1]
                val middle = match.value.substring(verb.length)
                mapVerbPreservingTense(verb, base = "execute", third = "executes", ing = "executing", past = "executed") + middle
            }
        ),
        // "run/runs/running/ran + for + office/president/..." -> campaign for
        EnContextRewriteRule(
            regex = Regex(
                "\\b(run|runs|running|ran)\\s+for\\s+(?:the\\s+)?(?:$ELECTION_TARGETS)\\b",
                RegexOption.IGNORE_CASE
            ),
            replacementBuilder = { match ->
                val verb = match.groupValues[1]
                val rest = match.value.substring(verb.length)
                mapVerbPreservingTense(verb, base = "campaign", third = "campaigns", ing = "campaigning", past = "campaigned") + rest
            }
        ),
        // "run/runs/running/ran + out of + ..." -> deplete / run short of
        EnContextRewriteRule(
            regex = Regex(
                "\\b(run|runs|running|ran)\\s+out\\s+of\\s+(?:$EXHAUSTION_OBJECTS)\\b",
                RegexOption.IGNORE_CASE
            ),
            replacementBuilder = { match ->
                val verb = match.groupValues[1]
                val rest = match.value.substring(verb.length + " out of".length)
                mapVerbPreservingTense(verb, base = "lack", third = "lacks", ing = "lacking", past = "lacked") + " enough" + rest
            }
        ),
        // Fluid subject + "is/are/was/were running" -> flowing
        EnContextRewriteRule(
            regex = Regex(
                "\\b($FLUID_SUBJECTS)\\s+(is|are|was|were|keeps?|kept)\\s+running\\b",
                RegexOption.IGNORE_CASE
            ),
            replacementBuilder = { match ->
                "${match.groupValues[1]} ${match.groupValues[2]} flowing"
            }
        ),
        // Idioms inside longer English sentences
        EnContextRewriteRule(
            regex = Regex("\\bspring\\s+into\\s+action\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "immediately start taking action" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bsprang\\s+into\\s+action\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "immediately started taking action" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bout\\s+of\\s+action\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "out of service and not working" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bin\\s+the\\s+long\\s+run\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "over the long term" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bon\\s+the\\s+run\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "fleeing from the authorities" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bunder\\s+the\\s+weather\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "feeling slightly unwell" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bpiece\\s+of\\s+cake\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "very easy task" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bonce\\s+in\\s+a\\s+blue\\s+moon\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "very rarely" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bcost\\s+an\\s+arm\\s+and\\s+a\\s+leg\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "cost a huge amount of money" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bsee\\s+eye\\s+to\\s+eye\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "completely agree" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bat\\s+the\\s+end\\s+of\\s+the\\s+day\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "ultimately" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bbear\\s+in\\s+mind\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "remember and consider" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bbreak(?:s|ing)?\\s+the\\s+ice\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "ease tension and start friendly conversation" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bbroke\\s+the\\s+ice\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "eased tension and started friendly conversation" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bbite\\s+the\\s+bullet\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "accept and endure a difficult situation" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bhit\\s+the\\s+nail\\s+on\\s+the\\s+head\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "state the exact truth about the issue" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bburn(?:s|ing|ed|t)?\\s+the\\s+midnight\\s+oil\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "work late into the night" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bspill(?:s|ing|ed|t)?\\s+the\\s+beans\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "reveal the secret" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bon\\s+the\\s+same\\s+page\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "in full agreement and understanding" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bget(?:s|ting)?\\s+out\\s+of\\s+hand\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "become out of control" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bgot\\s+out\\s+of\\s+hand\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "became out of control" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\brun(?:s|ning)?\\s+out\\s+of\\s+steam\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "lose momentum and energy" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bran\\s+out\\s+of\\s+steam\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "lost momentum and energy" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\brun(?:s|ning)?\\s+wild\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "go completely out of restraint" }
        ),
        EnContextRewriteRule(
            regex = Regex("\\bran\\s+wild\\b", RegexOption.IGNORE_CASE),
            replacementBuilder = { "went completely out of restraint" }
        )
    )

    private val AR_CONTEXT_RULES: List<ArContextRewriteRule> = listOf(
        ArContextRewriteRule(
            regex = Regex("على\\s+قدم\\s+وساق"),
            englishEquivalent = "in full swing"
        ),
        ArContextRewriteRule(
            regex = Regex("بين\\s+عشية\\s+وضحاها"),
            englishEquivalent = "overnight"
        ),
        ArContextRewriteRule(
            regex = Regex("في\\s+نهاية\\s+المطاف"),
            englishEquivalent = "in the long run"
        ),
        ArContextRewriteRule(
            regex = Regex("أخذ\\s+بعين\\s+الاعتبار|يأخذ\\s+بعين\\s+الاعتبار"),
            englishEquivalent = "take into consideration"
        ),
        ArContextRewriteRule(
            regex = Regex("في\\s+متناول\\s+اليد"),
            englishEquivalent = "within reach"
        ),
        ArContextRewriteRule(
            regex = Regex("ضرب\\s+عصفورين\\s+بحجر\\s+واحد"),
            englishEquivalent = "killed two birds with one stone"
        ),
        ArContextRewriteRule(
            regex = Regex("على\\s+أحر\\s+من\\s+الجمر"),
            englishEquivalent = "on tenterhooks with eager anticipation"
        ),
        ArContextRewriteRule(
            regex = Regex("في\\s+غمضة\\s+عين"),
            englishEquivalent = "in the blink of an eye"
        ),
        ArContextRewriteRule(
            regex = Regex("لا\\s+يمت\\s+بصلة"),
            englishEquivalent = "is completely unrelated"
        ),
        ArContextRewriteRule(
            regex = Regex("يصب\\s+الزيت\\s+على\\s+النار"),
            englishEquivalent = "adds fuel to the fire"
        ),
        ArContextRewriteRule(
            regex = Regex("من\\s+باب\\s+أولى"),
            englishEquivalent = "all the more reason"
        )
    )

    /**
     * Checks if the entire short input matches a curated idiom/proverb directly.
     * Used before invoking ML Kit on standalone proverbs or idioms.
     */
    fun findDirectContextualOverride(text: String, sourceCode: String, targetCode: String): String? {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return null
        if (sourceCode == "en" && targetCode == "ar") {
            val normalized = cleaned.lowercase(Locale.US).trimEnd('.', '!', '?')
            return EN_FULL_IDIOM_OVERRIDES[normalized]
        }
        if (sourceCode == "ar" && targetCode == "en") {
            val normalized = ArabicTextUtils.normalizeArabicForMatching(cleaned).trimEnd('.', '!', '؟', '!')
            return AR_FULL_IDIOM_OVERRIDES[normalized]
        }
        return null
    }

    /**
     * Rewrites ambiguous polysemous English collocations and idioms into semantically
     * explicit English phrasing before ML Kit inference while preserving grammar & tense.
     */
    fun prepareEnglishForTranslation(text: String): String {
        val trimmed = text.trim()
        // Never alter dictionary definition prompts ("word: definition") or single words
        if (trimmed.isEmpty() || !trimmed.contains(' ') || trimmed.contains(':')) return text
        var current = trimmed
        for (rule in EN_CONTEXT_RULES) {
            if (rule.regex.containsMatchIn(current)) {
                current = rule.regex.replace(current) { match -> rule.replacementBuilder(match) }
            }
        }
        return current
    }

    /**
     * Rewrites common Arabic idiomatic expressions into natural English hints when translating AR -> EN.
     */
    fun prepareArabicForTranslation(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || !trimmed.contains(' ')) return text
        val stripped = ArabicTextUtils.stripArabicDiacriticsKeepHamza(trimmed)
        var current = trimmed
        var strippedWorking = stripped
        for (rule in AR_CONTEXT_RULES) {
            if (rule.regex.containsMatchIn(strippedWorking)) {
                current = rule.regex.replace(strippedWorking, rule.englishEquivalent)
                strippedWorking = current
            }
        }
        return current
    }

    /**
     * Post-processes ML Kit Arabic output to fix known literal mistranslations of polysemous
     * verbs/collocations (e.g., if raw ML Kit output ever translates "run a company" literally
     * as "يركض شركة" or "يجري شركة").
     */
    fun refineArabicTranslation(sourceEnglish: String, rawArabic: String): String {
        val ar = rawArabic.trim()
        if (ar.isEmpty()) return ar
        val enLower = sourceEnglish.lowercase(Locale.US)

        var refined = ar
        val hasCompanyObj = Regex("\\b(?:$MANAGEMENT_OBJECTS)\\b", RegexOption.IGNORE_CASE).containsMatchIn(enLower)
        if (hasCompanyObj) {
            refined = refined
                .replace(Regex("(?<=^|\\s)يركض\\s+(?=شركة|مؤسسة|عملاً|عمل|مشروعاً|مشروع|فريقاً|مصنعاً)"), "يدير ")
                .replace(Regex("(?<=^|\\s)يجري\\s+(?=شركة|مؤسسة|عملاً|عمل|مشروعاً|مشروع|فريقاً|مصنعاً)"), "يدير ")
                .replace(Regex("(?<=^|\\s)تركض\\s+(?=شركة|مؤسسة|عملاً|عمل|مشروعاً|مشروع|فريقاً|مصنعاً)"), "تدير ")
                .replace(Regex("(?<=^|\\s)تجري\\s+(?=شركة|مؤسسة|عملاً|عمل|مشروعاً|مشروع|فريقاً|مصنعاً)"), "تدير ")
                .replace(Regex("(?<=^|\\s)ركض\\s+(?=شركة|مؤسسة|عملاً|عمل|مشروعاً|مشروع|فريقاً|مصنعاً)"), "أدار ")
                .replace(Regex("(?<=^|\\s)جرى\\s+(?=شركة|مؤسسة|عملاً|عمل|مشروعاً|مشروع|فريقاً|مصنعاً)"), "أدار ")
                // Handle "يجري هذا الرجل شركة" word order
                .replace(Regex("^يجري(\\s+\\S+\\s+\\S+\\s+(?:شركة|مؤسسة|مصنع|مشروع))"), "يدير$1")
                .replace(Regex("^يركض(\\s+\\S+\\s+\\S+\\s+(?:شركة|مؤسسة|مصنع|مشروع))"), "يدير$1")
        }

        return refined.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Post-processes ML Kit English output for naturalness.
     */
    fun refineEnglishTranslation(sourceArabic: String, rawEnglish: String): String {
        val en = rawEnglish.trim()
        if (en.isEmpty()) return en
        val arNorm = ArabicTextUtils.normalizeArabicForMatching(sourceArabic)
        var refined = en
        if (arNorm.contains("يدير شرك") || arNorm.contains("تدير شرك")) {
            refined = refined.replace(Regex("\\brunning a company\\b", RegexOption.IGNORE_CASE), "running a company")
        }
        return refined
    }

    /**
     * Offline recognition of idioms and phrasal verbs inside any English or Arabic sentence.
     * Supports inflected verb forms (run/runs/running/ran, spring/sprang/sprung, take/takes/took/taken).
     */
    fun detectKnownIdiomsInSentence(sentence: String, isArabic: Boolean): List<DetectedIdiom> {
        val trimmed = sentence.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (isArabic) {
            val norm = ArabicTextUtils.normalizeArabicForMatching(trimmed)
            return CURATED_AR_IDIOMS.mapNotNull { item ->
                val normPhrase = ArabicTextUtils.normalizeArabicForMatching(item.phrase)
                if (norm.contains(normPhrase)) {
                    DetectedIdiom(
                        phrase = item.phrase,
                        englishMeaning = item.englishMeaning,
                        arabicMeaning = item.arabicMeaning,
                        isArabicMachine = false,
                        example = item.example,
                        isPhrasalVerb = item.isPhrasalVerb,
                        usageNote = item.usageNote
                    )
                } else {
                    null
                }
            }
        }

        val lower = trimmed.lowercase(Locale.US)
        val detected = ArrayList<DetectedIdiom>()
        for (item in CURATED_EN_IDIOMS) {
            val matched = if (lower.contains(item.phrase)) {
                true
            } else {
                buildInflectedPhraseRegex(item.phrase)?.containsMatchIn(lower) == true
            }
            if (matched) {
                detected.add(
                    DetectedIdiom(
                        phrase = item.phrase,
                        englishMeaning = item.englishMeaning,
                        arabicMeaning = item.arabicMeaning,
                        isArabicMachine = false,
                        example = item.example,
                        isPhrasalVerb = item.isPhrasalVerb,
                        usageNote = item.usageNote
                    )
                )
            }
        }
        return detected.distinctBy { it.phrase.lowercase(Locale.US) }
    }

    /**
     * Exposes curated idiomatic/phrasal knowledge associated with a headword (used to enrich
     * dictionary results when free APIs return only partial lists, without ever inventing fake entries).
     */
    fun getCuratedPhrasesForHeadword(word: String, isArabic: Boolean): List<IdiomKnowledge> {
        if (isArabic) {
            val norm = ArabicTextUtils.normalizeArabicForMatching(word)
            if (norm.length < 2) return emptyList()
            return CURATED_AR_IDIOMS.filter {
                ArabicTextUtils.normalizeArabicForMatching(it.phrase).split(' ').any { tok -> tok == norm }
            }
        }
        val lower = word.trim().lowercase(Locale.US)
        if (lower.length < 2) return emptyList()
        return CURATED_EN_IDIOMS.filter { item ->
            val words = item.phrase.lowercase(Locale.US).split(Regex("\\s+"))
            words.any { w -> w == lower || (lower == "action" && w == "actions") }
        }
    }

    private fun buildInflectedPhraseRegex(phrase: String): Regex? {
        val words = phrase.lowercase(Locale.US).split(Regex("\\s+"))
        if (words.isEmpty()) return null
        val first = when (words[0]) {
            "run" -> "(?:run|runs|running|ran)"
            "spring" -> "(?:spring|springs|springing|sprang|sprung)"
            "take" -> "(?:take|takes|taking|took|taken)"
            "give" -> "(?:give|gives|giving|gave|given)"
            "break" -> "(?:break|breaks|breaking|broke|broken)"
            "carry" -> "(?:carry|carries|carrying|carried)"
            "look" -> "(?:look|looks|looking|looked)"
            "make" -> "(?:make|makes|making|made)"
            "bear" -> "(?:bear|bears|bearing|bore|borne)"
            "put" -> "(?:put|puts|putting)"
            "see" -> "(?:see|sees|seeing|saw|seen)"
            "figure" -> "(?:figure|figures|figuring|figured)"
            "bite" -> "(?:bite|bites|biting|bit|bitten)"
            "hit" -> "(?:hit|hits|hitting)"
            "burn" -> "(?:burn|burns|burning|burned|burnt)"
            "spill" -> "(?:spill|spills|spilling|spilled|spilt)"
            "get" -> "(?:get|gets|getting|got|gotten)"
            "cost" -> "(?:cost|costs|costing)"
            else -> Regex.escape(words[0])
        }
        val rest = words.drop(1).joinToString("\\s+") { tok ->
            if (tok == "one's") "(?:my|your|his|her|our|their|one's)" else Regex.escape(tok)
        }
        val pattern = if (rest.isEmpty()) "\\b$first\\b" else "\\b$first\\s+$rest\\b"
        return Regex(pattern, RegexOption.IGNORE_CASE)
    }

    private fun mapVerbPreservingTense(
        original: String,
        base: String,
        third: String,
        ing: String,
        past: String
    ): String {
        val mapped = when (original.lowercase(Locale.US)) {
            "runs" -> third
            "running" -> ing
            "ran" -> past
            else -> base
        }
        return if (original.firstOrNull()?.isUpperCase() == true) {
            mapped.replaceFirstChar { it.titlecase(Locale.US) }
        } else {
            mapped
        }
    }
}
