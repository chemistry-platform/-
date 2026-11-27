#!/usr/bin/env python3
"""
Builds `dictionary-pack.json.gz` (< 80 MB) from free, open linguistic datasets ONLY:
  1. Wiktionary via kaikki.org dumps (English & Arabic, CC BY-SA 4.0)
  2. Princeton WordNet & Open Multilingual WordNet Arabic (via NLTK wordnet + omw-1.4 / omwn)
  3. OpenSubtitles / HermitDave EN 50k Frequency List
  4. Tatoeba EN–AR bilingual sentence pairs (CC BY 2.0 FR)

Outputs schema v2 compatible with `OfflinePackManager.importPackFromJsonStream`:
{
  "version": 2,
  "generated_at": "...",
  "stats": {...},
  "words": [...],
  "idioms": [...],       // >= 30,000 idioms, phrasal verbs, collocations & phrases
  "reverse_ar": [...]    // vocalized Arabic -> grouped English matches
}
"""

import argparse
import bz2
import datetime
import gzip
import io
import json
import os
import re
import sys
import tarfile
import urllib.request
from collections import defaultdict

MAX_PACK_BYTES = 80 * 1024 * 1024 - (512 * 1024)  # Strict < 80 MB safety margin
MIN_IDIOMS_TARGET = 30_000

KAIKKI_EN_URL = "https://kaikki.org/dictionary/English/kaikki.org-dictionary-English.jsonl.gz"
KAIKKI_AR_URL = "https://kaikki.org/dictionary/Arabic/kaikki.org-dictionary-Arabic.jsonl.gz"
FREQ_EN_URL = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_50k.txt"
OMWN_ARB_URL = "https://raw.githubusercontent.com/omwn/omw-data/main/wns/arb/wn-data-arb.tab"
TATOEBA_ENG_URL = "https://downloads.tatoeba.org/exports/per_language/eng/eng_sentences.tsv.bz2"
TATOEBA_ARA_URL = "https://downloads.tatoeba.org/exports/per_language/ara/ara_sentences.tsv.bz2"
TATOEBA_LINKS_URL = "https://downloads.tatoeba.org/exports/links.tar.bz2"

ARABIC_CHAR_RE = re.compile(r"[\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF]")
ARABIC_DIACRITIC_RE = re.compile(r"[\u064B-\u065F\u0670\u06D6-\u06ED\u0640]")
ASCII_WORD_RE = re.compile(r"^[a-z][a-z' -]{0,60}$")

# Curated high-frequency idioms guaranteed to have accurate Arabic meanings even if Wiktionary lacks ar gloss
CURATED_CORE_IDIOMS = {
    "hit the town": {
        "def": "To go out and enjoy oneself at night in restaurants, clubs, or entertainment venues in a city.",
        "ar": "يخرج للسهر والاستمتاع في المدينة",
        "ex": "We decided to hit the town after finishing our exams.",
        "cat": "IDIOM",
    },
    "break a leg": {
        "def": "Used to wish a performer good luck before a show or performance.",
        "ar": "بالتوفيق؛ حظاً سعيداً",
        "ex": "Break a leg on opening night!",
        "cat": "IDIOM",
    },
    "piece of cake": {
        "def": "Something very easy to accomplish.",
        "ar": "أمر في غاية السهولة",
        "ex": "The final test was a piece of cake.",
        "cat": "IDIOM",
    },
    "under the weather": {
        "def": "Feeling slightly ill, unwell, or fatigued.",
        "ar": "يشعر بتوعك خفيف أو إعياء",
        "ex": "I am feeling a bit under the weather today.",
        "cat": "IDIOM",
    },
    "spill the beans": {
        "def": "To reveal a secret or confidential information, intentionally or accidentally.",
        "ar": "يفشي السر؛ يبوح بالمستور",
        "ex": "Don't spill the beans about the surprise party.",
        "cat": "IDIOM",
    },
    "bite the bullet": {
        "def": "To endure a difficult or unpleasant situation with courage and determination.",
        "ar": "يتحمل الصعاب بشجاعة؛ يواجه أمراً مؤلماً بعزيمة",
        "ex": "He had to bite the bullet and tell the truth.",
        "cat": "IDIOM",
    },
    "once in a blue moon": {
        "def": "Very rarely; almost never.",
        "ar": "فيما ندر؛ نادر الحدوث",
        "ex": "They only visit us once in a blue moon.",
        "cat": "IDIOM",
    },
    "call it a day": {
        "def": "To stop working on something for the rest of the day.",
        "ar": "يتوقف عن العمل لهذا اليوم؛ ينهي العمل",
        "ex": "Let's call it a day and continue tomorrow morning.",
        "cat": "IDIOM",
    },
}


def has_arabic(text: str) -> bool:
    return bool(ARABIC_CHAR_RE.search(text))


def has_diacritics(text: str) -> bool:
    return bool(re.search(r"[\u064B-\u065F\u0670]", text))


def normalize_arabic_key(text: str) -> str:
    if not text:
        return ""
    s = ARABIC_DIACRITIC_RE.sub("", text)
    out = []
    for ch in s:
        if ch in ("\u0622", "\u0623", "\u0625", "\u0671"):
            out.append("\u0627")
        elif ch in ("\u0649", "\u06CC"):
            out.append("\u064A")
        elif ch == "\u0629":
            out.append("\u0647")
        elif ch in ("\u0624", "\u0626", "\u0621"):
            continue
        else:
            out.append(ch)
    cleaned = "".join(out)
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    return cleaned


def clean_text(text: str, max_len: int = 240) -> str:
    if not text:
        return ""
    s = re.sub(r"\s+", " ", text).strip()
    if len(s) > max_len:
        s = s[: max_len - 1].rstrip() + "…"
    return s


def download_file(url: str, dest: str, label: str) -> bool:
    print(f"[download] Fetching {label} from {url} ...")
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "Al-Makhzanji-PackBuilder/2.0 (Open-Source Dictionary)"},
    )
    try:
        with urllib.request.urlopen(req, timeout=300) as resp, open(dest, "wb") as f:
            total = int(resp.headers.get("Content-Length") or 0)
            read_bytes = 0
            while True:
                chunk = resp.read(1024 * 256)
                if not chunk:
                    break
                f.write(chunk)
                read_bytes += len(chunk)
        print(f"[download] Saved {label}: {read_bytes / (1024 * 1024):.1f} MB")
        return True
    except Exception as exc:
        print(f"[warn] Failed to download {label}: {exc}", file=sys.stderr)
        return False


def load_frequency_ranks(cache_dir: str) -> dict:
    path = os.path.join(cache_dir, "en_50k.txt")
    ranks = {}
    if not os.path.exists(path):
        download_file(FREQ_EN_URL, path, "HermitDave EN 50k frequency list")
    if os.path.exists(path):
        with open(path, "r", encoding="utf-8", errors="ignore") as f:
            for idx, line in enumerate(f, start=1):
                parts = line.strip().split()
                if parts:
                    w = parts[0].lower()
                    if w not in ranks:
                        ranks[w] = idx
    print(f"[freq] Loaded {len(ranks)} frequency ranks")
    return ranks


def load_tatoeba_pairs(cache_dir: str, max_pairs: int = 120_000) -> dict:
    """
    Loads English -> (english_sentence, arabic_sentence) examples from Tatoeba open exports.
    Returns dict[lowercase_word_or_phrase, list[(eng, ara)]].
    """
    eng_path = os.path.join(cache_dir, "eng_sentences.tsv.bz2")
    ara_path = os.path.join(cache_dir, "ara_sentences.tsv.bz2")
    links_path = os.path.join(cache_dir, "links.tar.bz2")

    ok = (
        (os.path.exists(eng_path) or download_file(TATOEBA_ENG_URL, eng_path, "Tatoeba ENG"))
        and (os.path.exists(ara_path) or download_file(TATOEBA_ARA_URL, ara_path, "Tatoeba ARA"))
        and (os.path.exists(links_path) or download_file(TATOEBA_LINKS_URL, links_path, "Tatoeba links"))
    )
    if not ok:
        return {}

    ara_map = {}
    try:
        with bz2.open(ara_path, "rt", encoding="utf-8", errors="ignore") as f:
            for line in f:
                parts = line.rstrip("\n").split("\t")
                if len(parts) >= 3 and len(parts[2]) <= 140:
                    ara_map[parts[0]] = parts[2].strip()
    except Exception as exc:
        print(f"[warn] Tatoeba ARA parse error: {exc}", file=sys.stderr)
        return {}

    eng_ids_needed = {}
    try:
        with tarfile.open(links_path, "r:bz2") as tf:
            for member in tf:
                if member.isfile():
                    f = tf.extractfile(member)
                    if f is None:
                        continue
                    for raw in io.TextIOWrapper(f, encoding="utf-8", errors="ignore"):
                        parts = raw.rstrip("\n").split("\t")
                        if len(parts) >= 2 and parts[1] in ara_map:
                            eng_ids_needed.setdefault(parts[0], parts[1])
                            if len(eng_ids_needed) >= max_pairs:
                                break
                    break
    except Exception as exc:
        print(f"[warn] Tatoeba links parse error: {exc}", file=sys.stderr)
        return {}

    word_examples = defaultdict(list)
    try:
        with bz2.open(eng_path, "rt", encoding="utf-8", errors="ignore") as f:
            for line in f:
                parts = line.rstrip("\n").split("\t")
                if len(parts) < 3:
                    continue
                sid = parts[0]
                if sid not in eng_ids_needed:
                    continue
                eng_sent = clean_text(parts[2], 140)
                ara_sent = clean_text(ara_map.get(eng_ids_needed[sid], ""), 140)
                if not eng_sent or not ara_sent:
                    continue
                tokens = re.findall(r"[a-z']+", eng_sent.lower())
                for tok in set(tokens):
                    if len(tok) >= 3 and len(word_examples[tok]) < 2:
                        word_examples[tok].append((eng_sent, ara_sent))
    except Exception as exc:
        print(f"[warn] Tatoeba ENG parse error: {exc}", file=sys.stderr)

    print(f"[tatoeba] Indexed bilingual examples for {len(word_examples)} words")
    return word_examples


def abbreviate_pos(pos: str) -> str:
    p = (pos or "").strip().lower()
    mapping = {
        "verb": "v.",
        "noun": "n.",
        "adjective": "adj.",
        "adj": "adj.",
        "adverb": "adv.",
        "adv": "adv.",
        "preposition": "prep.",
        "conjunction": "conj.",
        "pronoun": "pron.",
        "interjection": "interj.",
        "determiner": "det.",
        "particle": "part.",
        "numeral": "num.",
        "idiom": "idiom",
        "phrase": "phr.",
        "proverb": "prov.",
    }
    return mapping.get(p, "")


def build_preview(senses: list, max_len: int = 16000) -> str:
    grouped = {}
    order = []
    for s in senses:
        abbr = abbreviate_pos(s.get("pos", ""))
        ar = (s.get("ar") or "").strip()
        if not ar:
            continue
        if abbr not in grouped:
            grouped[abbr] = []
            order.append(abbr)
        for piece in re.split(r"[؛;،]", ar):
            piece = piece.strip()
            if piece and has_arabic(piece) and piece not in grouped[abbr]:
                grouped[abbr].append(piece)
    parts = []
    for abbr in order:
        glosses = grouped[abbr]
        if not glosses:
            continue
        joined = "؛ ".join(glosses)
        parts.append(f"{abbr} {joined}" if abbr else joined)
    out = "؛ ".join(parts)
    if len(out) > max_len:
        out = out[:max_len].rstrip()
    return out


def extract_arabic_glosses(entry: dict) -> dict:
    """
    Maps sense_index_or_default -> list[vocalized_or_plain_arabic_translation]
    from kaikki.org English entry `translations` list.
    """
    by_sense = defaultdict(list)
    for tr in entry.get("translations") or []:
        if tr.get("code") == "ar" or tr.get("lang") == "Arabic":
            word = (tr.get("word") or "").strip()
            if not word or not has_arabic(word) or len(word) > 64:
                continue
            sense_key = (tr.get("sense") or "").strip().lower()
            if word not in by_sense[sense_key]:
                by_sense[sense_key].append(word)
    return by_sense


def detect_idiom_category(word: str, pos: str, tags: list) -> str:
    p = (pos or "").lower()
    tag_set = {t.lower() for t in tags}
    if p == "verb" or "phrasal" in tag_set:
        return "PHRASAL"
    if p in ("idiom", "proverb") or "idiomatic" in tag_set or "idiom" in tag_set:
        return "IDIOM"
    if len(word.split()) >= 3:
        return "IDIOM"
    return "PHRASE"


def process_kaikki_english(
    gz_path: str,
    freq_ranks: dict,
    words_map: dict,
    idioms_map: dict,
    reverse_ar: dict,
    tatoeba_examples: dict,
):
    print("[kaikki-en] Streaming English Wiktionary dump...")
    line_count = 0
    with gzip.open(gz_path, "rt", encoding="utf-8", errors="ignore") as f:
        for raw_line in f:
            line_count += 1
            if line_count % 250_000 == 0:
                print(
                    f"[kaikki-en] Processed {line_count:,} lines | "
                    f"words={len(words_map):,}, idioms={len(idioms_map):,}"
                )
            try:
                obj = json.loads(raw_line)
            except Exception:
                continue

            word = (obj.get("word") or "").strip()
            if not word or len(word) > 72:
                continue
            # Skip purely non-Latin/punctuation junk on EN side
            lower = word.lower()
            if not re.match(r"^[a-zA-Z][a-zA-Z0-9' .-]*$", word):
                continue

            pos = (obj.get("pos") or "word").strip().lower()
            senses_raw = obj.get("senses") or []
            if not senses_raw:
                continue

            # Extract IPA
            us_ipa = ""
            uk_ipa = ""
            for snd in obj.get("sounds") or []:
                ipa = (snd.get("ipa") or "").strip()
                if not ipa:
                    continue
                tags = {t.lower() for t in (snd.get("tags") or [])}
                if "us" in tags or "general-american" in tags:
                    if not us_ipa:
                        us_ipa = ipa
                elif "uk" in tags or "received-pronunciation" in tags:
                    if not uk_ipa:
                        uk_ipa = ipa
                elif not us_ipa:
                    us_ipa = ipa

            # Translations from English entry -> Arabic
            tr_by_sense = extract_arabic_glosses(obj)
            all_tr_flat = []
            for lst in tr_by_sense.values():
                for w in lst:
                    if w not in all_tr_flat:
                        all_tr_flat.append(w)

            is_multiword = " " in word
            entry_tags = []
            parsed_senses = []
            word_synonyms = []
            word_antonyms = []
            first_eng_def = ""
            first_ar_meaning = ""
            first_ex = ""
            is_idiomatic_entry = pos in ("idiom", "phrase", "proverb")

            for s in senses_raw[:500]:
                glosses = s.get("glosses") or s.get("raw_glosses") or []
                if not glosses:
                    continue
                eng_def = clean_text(glosses[0], 200)
                if not eng_def or eng_def.startswith("Alternative form of") or eng_def.startswith("Misspelling of"):
                    continue
                if not first_eng_def:
                    first_eng_def = eng_def

                s_tags = [str(t) for t in (s.get("tags") or [])]
                if any(t.lower() in ("idiomatic", "idiom", "figurative") for t in s_tags):
                    is_idiomatic_entry = True
                if "academic" in {t.lower() for t in s_tags}:
                    if "AWL" not in entry_tags:
                        entry_tags.append("AWL")

                # Match sense translations or include all entry Arabic translations on top senses
                matched_ar = []
                for skey, slist in tr_by_sense.items():
                    if not skey or skey in eng_def.lower():
                        for aw in slist:
                            if aw not in matched_ar:
                                matched_ar.append(aw)
                if not matched_ar and len(parsed_senses) == 0 and all_tr_flat:
                    matched_ar = all_tr_flat[:500]

                ar_str = "؛ ".join(matched_ar[:500])
                if ar_str and not first_ar_meaning:
                    first_ar_meaning = ar_str

                examples = []
                for ex_obj in (s.get("examples") or [])[:1]:
                    ex_txt = clean_text(ex_obj.get("text") or "", 140)
                    if ex_txt:
                        examples.append(ex_txt)
                        if not first_ex:
                            first_ex = ex_txt

                syns = [
                    clean_text(x.get("word") or "", 40)
                    for x in (s.get("synonyms") or [])[:10]
                    if (x.get("word") or "").strip()
                ]
                ants = [
                    clean_text(x.get("word") or "", 40)
                    for x in (s.get("antonyms") or [])[:10]
                    if (x.get("word") or "").strip()
                ]
                for x in syns:
                    if x not in word_synonyms and len(word_synonyms) < 25:
                        word_synonyms.append(x)
                for x in ants:
                    if x not in word_antonyms and len(word_antonyms) < 25:
                        word_antonyms.append(x)

                sense_dict = {"pos": pos, "def": eng_def}
                if ar_str:
                    sense_dict["ar"] = ar_str
                if examples:
                    sense_dict["examples"] = examples
                if syns:
                    sense_dict["synonyms"] = syns
                if ants:
                    sense_dict["antonyms"] = ants
                parsed_senses.append(sense_dict)

                # Populate reverse Arabic index for each Arabic translation gloss
                for aw in matched_ar:
                    norm_ar = normalize_arabic_key(aw)
                    if not norm_ar:
                        continue
                    bucket = reverse_ar[(norm_ar, aw)]
                    if len(bucket) < 100:
                        rank = freq_ranks.get(lower, 999999)
                        bucket[lower] = {
                            "en": lower,
                            "pos": pos,
                            "def": clean_text(eng_def, 140),
                            "rank": rank,
                            "src": "wiktionary",
                        }

            if not parsed_senses:
                continue

            # Ensure remaining unmatched Arabic translations from Wiktionary translation tables are preserved
            if all_tr_flat and parsed_senses:
                already_ar = set()
                for ps in parsed_senses:
                    if ps.get("ar"):
                        for part in ps["ar"].split("؛"):
                            already_ar.add(part.strip())
                extra_ar = [w for w in all_tr_flat if w not in already_ar]
                if extra_ar:
                    cur = parsed_senses[0].get("ar", "")
                    merged_ar = (cur.split("؛ ") if cur else []) + extra_ar[:100]
                    parsed_senses[0]["ar"] = "؛ ".join(x for x in merged_ar if x)
                    if not first_ar_meaning:
                        first_ar_meaning = parsed_senses[0]["ar"]
                    for aw in extra_ar[:100]:
                        norm_ar = normalize_arabic_key(aw)
                        if norm_ar:
                            bucket = reverse_ar[(norm_ar, aw)]
                            if len(bucket) < 100:
                                rank_val = freq_ranks.get(lower, 999999)
                                bucket[lower] = {
                                    "en": lower,
                                    "pos": pos,
                                    "def": parsed_senses[0]["def"][:140],
                                    "rank": rank_val,
                                    "src": "wiktionary",
                                }

            rank = freq_ranks.get(lower, 999999)

            # Collect multi-word idioms / phrasal verbs / collocations
            if is_multiword or is_idiomatic_entry:
                if first_eng_def:
                    cat = detect_idiom_category(lower, pos, entry_tags)
                    existing_i = idioms_map.get(lower)
                    ex_ar = ""
                    if not first_ex and lower in tatoeba_examples:
                        first_ex, ex_ar = tatoeba_examples[lower][0]
                    if existing_i is None:
                        idioms_map[lower] = {
                            "phrase": lower,
                            "def": first_eng_def,
                            "ar": first_ar_meaning,
                            "ex": first_ex,
                            "exAr": ex_ar,
                            "cat": cat,
                            "rank": rank if rank != 999999 else 50000 + len(idioms_map),
                        }
                    else:
                        if not existing_i.get("ar") and first_ar_meaning:
                            existing_i["ar"] = first_ar_meaning
                        if not existing_i.get("ex") and first_ex:
                            existing_i["ex"] = first_ex
                        if len(first_eng_def) > len(existing_i.get("def", "")):
                            existing_i["def"] = first_eng_def

            # Single words (and high-value phrases) go into `words_map`
            if not is_multiword or is_idiomatic_entry or rank < 45_000:
                if not parsed_senses[0].get("examples") and lower in tatoeba_examples:
                    parsed_senses[0]["examples"] = [tatoeba_examples[lower][0][0]]

                related_forms = []
                structured_forms = []
                seen_form_keys = set()
                for frm in (obj.get("forms") or [])[:16]:
                    fw = (frm.get("form") or "").strip()
                    if not fw or len(fw) > 40:
                        continue
                    ftags = {str(t).lower() for t in (frm.get("tags") or [])}
                    form_label = ""
                    if "past" in ftags and "participle" in ftags:
                        form_label = "pa. pple."
                    elif "past" in ftags:
                        form_label = "pa. t."
                    elif "third-person" in ftags and "singular" in ftags:
                        form_label = "3rd sg."
                    elif "present" in ftags and "participle" in ftags:
                        form_label = "pres. pple."
                    elif "plural" in ftags:
                        form_label = "pl."
                    elif "comparative" in ftags:
                        form_label = "comp."
                    elif "superlative" in ftags:
                        form_label = "sup."
                    if form_label and (fw.lower() != lower or form_label == "pa. pple."):
                        fk = f"{fw.lower()}|{form_label}"
                        if fk not in seen_form_keys and len(structured_forms) < 10:
                            seen_form_keys.add(fk)
                            structured_forms.append({"form": fw, "label": form_label})
                    if fw.lower() != lower and fw not in related_forms:
                        related_forms.append(fw)
                for dr in (obj.get("derived") or [])[:6]:
                    dw = (dr.get("word") or "").strip()
                    if dw and dw.lower() != lower and len(dw) <= 45 and dw not in related_forms:
                        related_forms.append(dw)

                w_entry = words_map.get(lower)
                if w_entry is None:
                    w_entry = {
                        "word": lower,
                        "lang": "en",
                        "rank": rank,
                        "senses": [],
                    }
                    if us_ipa:
                        w_entry["phonetic"] = us_ipa
                    if uk_ipa:
                        w_entry["ukIpa"] = uk_ipa
                    if entry_tags:
                        w_entry["tags"] = entry_tags
                    if structured_forms:
                        w_entry["forms"] = structured_forms
                    if related_forms:
                        w_entry["family"] = related_forms[:6]
                    if word_synonyms:
                        w_entry["synonyms"] = word_synonyms[:25]
                    if word_antonyms:
                        w_entry["antonyms"] = word_antonyms[:25]
                    words_map[lower] = w_entry
                else:
                    if us_ipa and "phonetic" not in w_entry:
                        w_entry["phonetic"] = us_ipa
                    if uk_ipa and "ukIpa" not in w_entry:
                        w_entry["ukIpa"] = uk_ipa
                    if rank < w_entry.get("rank", 999999):
                        w_entry["rank"] = rank
                    if structured_forms:
                        cur_forms = w_entry.setdefault("forms", [])
                        cur_f_keys = {f"{x.get('form', '').lower()}|{x.get('label', '')}" for x in cur_forms}
                        for sf in structured_forms:
                            sfk = f"{sf['form'].lower()}|{sf['label']}"
                            if sfk not in cur_f_keys and len(cur_forms) < 10:
                                cur_f_keys.add(sfk)
                                cur_forms.append(sf)
                    if related_forms:
                        cur_fam = w_entry.setdefault("family", [])
                        for rf in related_forms:
                            if rf not in cur_fam and len(cur_fam) < 8:
                                cur_fam.append(rf)
                    if word_synonyms:
                        cur_syn = w_entry.setdefault("synonyms", [])
                        for ws in word_synonyms:
                            if ws not in cur_syn and len(cur_syn) < 25:
                                cur_syn.append(ws)
                    if word_antonyms:
                        cur_ant = w_entry.setdefault("antonyms", [])
                        for wa in word_antonyms:
                            if wa not in cur_ant and len(cur_ant) < 25:
                                cur_ant.append(wa)

                for ps in parsed_senses:
                    if len(w_entry["senses"]) < 500:
                        w_entry["senses"].append(ps)

    print(f"[kaikki-en] Finished: {len(words_map):,} EN words, {len(idioms_map):,} idioms, {len(reverse_ar):,} reverse AR forms")


def process_kaikki_arabic(gz_path: str, freq_ranks: dict, words_map: dict, reverse_ar: dict):
    if not os.path.exists(gz_path):
        return
    print("[kaikki-ar] Streaming Arabic Wiktionary dump...")
    line_count = 0
    with gzip.open(gz_path, "rt", encoding="utf-8", errors="ignore") as f:
        for raw_line in f:
            line_count += 1
            if line_count % 100_000 == 0:
                print(f"[kaikki-ar] Processed {line_count:,} lines | reverse_ar={len(reverse_ar):,}")
            try:
                obj = json.loads(raw_line)
            except Exception:
                continue

            voc_word = (obj.get("word") or "").strip()
            if not voc_word or not has_arabic(voc_word) or len(voc_word) > 64:
                continue
            norm_ar = normalize_arabic_key(voc_word)
            if not norm_ar:
                continue

            pos = (obj.get("pos") or "word").strip().lower()
            ar_senses = []
            for s in (obj.get("senses") or [])[:500]:
                glosses = s.get("glosses") or s.get("raw_glosses") or []
                if not glosses:
                    continue
                eng_gloss = clean_text(glosses[0], 180)
                if not eng_gloss:
                    continue
                ar_senses.append({"pos": pos, "def": eng_gloss, "ar": voc_word})

                # Also extract short English headwords from comma/semicolon-separated glosses for reverse index
                for seg in re.split(r"[,;]", eng_gloss):
                    cand = seg.strip().lower()
                    if (
                        2 <= len(cand) <= 36
                        and ASCII_WORD_RE.match(cand)
                        and not cand.startswith("to ")
                        and " " not in cand
                    ) or (
                        cand.startswith("to ") and len(cand) <= 28 and len(cand.split()) == 2
                    ):
                        rank = freq_ranks.get(cand.removeprefix("to "), 999999)
                        bucket = reverse_ar[(norm_ar, voc_word)]
                        if cand not in bucket and len(bucket) < 100:
                            bucket[cand] = {
                                "en": cand,
                                "pos": pos,
                                "def": eng_gloss[:140],
                                "rank": rank,
                                "src": "wiktionary",
                            }

            if ar_senses:
                key = f"ar:{voc_word}"
                existing = words_map.get(key)
                if existing is None:
                    words_map[key] = {
                        "word": voc_word,
                        "lang": "ar",
                        "rank": 100000,
                        "senses": ar_senses,
                    }
                else:
                    for ps in ar_senses:
                        if len(existing["senses"]) < 500:
                            existing["senses"].append(ps)


def enrich_with_wordnet_and_omwn(
    cache_dir: str,
    freq_ranks: dict,
    words_map: dict,
    idioms_map: dict,
    reverse_ar: dict,
):
    print("[wordnet+omwn] Loading NLTK WordNet & Open Multilingual WordNet Arabic...")
    try:
        import nltk
        nltk_data = os.path.join(cache_dir, "nltk_data")
        os.makedirs(nltk_data, exist_ok=True)
        nltk.data.path.insert(0, nltk_data)
        nltk.download("wordnet", download_dir=nltk_data, quiet=True)
        nltk.download("omw-1.4", download_dir=nltk_data, quiet=True)
        from nltk.corpus import wordnet as wn
    except Exception as exc:
        print(f"[warn] NLTK WordNet unavailable: {exc}", file=sys.stderr)
        return

    pos_map = {"n": "noun", "v": "verb", "a": "adjective", "s": "adjective", "r": "adverb"}

    # 1. Enrich idioms (>= 30k guarantee) + reverse_ar from WordNet & OMW Arabic
    synset_by_offset = {}
    for synset in wn.all_synsets():
        pos_full = pos_map.get(synset.pos(), "word")
        definition = clean_text(synset.definition() or "", 180)
        examples = [clean_text(ex, 130) for ex in (synset.examples() or [])[:1] if ex]
        offset_key = f"{synset.offset():08d}-{synset.pos()}"
        synset_by_offset[offset_key] = (synset, pos_full, definition, examples)

        lemmas = synset.lemmas()
        eng_lemmas = [l.name().replace("_", " ").strip() for l in lemmas]
        arb_lemmas = []
        try:
            arb_lemmas = [a.replace("_", " ").strip() for a in synset.lemma_names("arb")]
        except Exception:
            arb_lemmas = []

        # Collect multi-word expressions from WordNet into idioms_map
        for lem_str in eng_lemmas:
            lower = lem_str.lower()
            if " " in lower and ASCII_WORD_RE.match(lower) and definition:
                ar_str = "؛ ".join([a for a in arb_lemmas if has_arabic(a)][:4])
                existing = idioms_map.get(lower)
                if existing is None:
                    idioms_map[lower] = {
                        "phrase": lower,
                        "def": definition,
                        "ar": ar_str,
                        "ex": examples[0] if examples else "",
                        "exAr": "",
                        "cat": "PHRASAL" if pos_full == "verb" else "PHRASE",
                        "rank": freq_ranks.get(lower, 120000 + len(idioms_map)),
                    }
                else:
                    if not existing.get("ar") and ar_str:
                        existing["ar"] = ar_str
                    if not existing.get("ex") and examples:
                        existing["ex"] = examples[0]

        # Populate OMW Arabic -> English reverse index
        for ar_word in arb_lemmas:
            if not has_arabic(ar_word) or len(ar_word) > 64:
                continue
            norm_ar = normalize_arabic_key(ar_word)
            if not norm_ar:
                continue
            bucket = reverse_ar[(norm_ar, ar_word)]
            for en_word in eng_lemmas:
                en_l = en_word.lower()
                if not ASCII_WORD_RE.match(en_l):
                    continue
                if en_l not in bucket and len(bucket) < 100:
                    bucket[en_l] = {
                        "en": en_l,
                        "pos": pos_full,
                        "def": definition[:140],
                        "rank": freq_ranks.get(en_l, 999999),
                        "src": "omwn",
                    }

        # Populate antonyms/synonyms and OMWN Arabic meanings on WordNet single headwords
        ar_synset_str = "؛ ".join([a for a in arb_lemmas if has_arabic(a)])
        for l in lemmas:
            en_l = l.name().replace("_", " ").strip().lower()
            if " " not in en_l and en_l in words_map:
                w_entry = words_map[en_l]
                ants = [a.name().replace("_", " ").strip() for a in l.antonyms()[:6]]
                if ants:
                    cur = w_entry.setdefault("antonyms", [])
                    for a in ants:
                        if a not in cur and len(cur) < 20:
                            cur.append(a)
                if ar_synset_str and definition and len(w_entry["senses"]) < 500:
                    existing_defs = {s.get("def", "").lower() for s in w_entry["senses"]}
                    if definition.lower() not in existing_defs:
                        w_entry["senses"].append({
                            "pos": pos_full,
                            "def": definition,
                            "ar": ar_synset_str,
                        })

    # 2. Also pull directly from omwn/omw-data wn-data-arb.tab (often includes extra vocalized entries!)
    tab_path = os.path.join(cache_dir, "wn-data-arb.tab")
    if os.path.exists(tab_path) or download_file(OMWN_ARB_URL, tab_path, "OMWN Arabic tab"):
        with open(tab_path, "r", encoding="utf-8", errors="ignore") as f:
            for line in f:
                if line.startswith("#") or "\t" not in line:
                    continue
                parts = line.rstrip("\n").split("\t")
                if len(parts) < 3:
                    continue
                syn_id, rel, val = parts[0].strip(), parts[1].strip(), parts[2].strip()
                if not rel.endswith("lemma") or not has_arabic(val):
                    continue
                info = synset_by_offset.get(syn_id)
                if not info:
                    continue
                synset, pos_full, definition, _ = info
                norm_ar = normalize_arabic_key(val)
                if not norm_ar:
                    continue
                bucket = reverse_ar[(norm_ar, val)]
                for l in synset.lemmas():
                    en_l = l.name().replace("_", " ").strip().lower()
                    if ASCII_WORD_RE.match(en_l) and en_l not in bucket and len(bucket) < 100:
                        bucket[en_l] = {
                            "en": en_l,
                            "pos": pos_full,
                            "def": definition[:140],
                            "rank": freq_ranks.get(en_l, 999999),
                            "src": "omwn",
                        }

    print(f"[wordnet+omwn] Total idioms={len(idioms_map):,}, reverse_ar={len(reverse_ar):,}")


def apply_curated_idioms(idioms_map: dict):
    for phrase, meta in CURATED_CORE_IDIOMS.items():
        existing = idioms_map.get(phrase)
        if existing is None:
            idioms_map[phrase] = {
                "phrase": phrase,
                "def": meta["def"],
                "ar": meta["ar"],
                "ex": meta["ex"],
                "exAr": "",
                "cat": meta["cat"],
                "rank": 500,
            }
        else:
            # Always keep curated accurate Arabic idiom meaning rather than missing/noisy one
            existing["ar"] = meta["ar"]
            if not existing.get("def"):
                existing["def"] = meta["def"]
            if not existing.get("ex"):
                existing["ex"] = meta["ex"]
            existing["cat"] = meta["cat"]
            existing["rank"] = min(existing.get("rank", 999999), 500)


def serialize_pack(words_list: list, idioms_list: list, reverse_list: list, out_path: str):
    payload = {
        "version": 2,
        "generated_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "stats": {
            "words": len(words_list),
            "idioms": len(idioms_list),
            "reverse_ar": len(reverse_list),
        },
        "words": words_list,
        "idioms": idioms_list,
        "reverse_ar": reverse_list,
    }
    tmp_path = out_path + ".tmp"
    with gzip.open(tmp_path, "wt", encoding="utf-8", compresslevel=9) as gz:
        json.dump(payload, gz, ensure_ascii=False, separators=(",", ":"))
    os.replace(tmp_path, out_path)
    return os.path.getsize(out_path)


def main():
    parser = argparse.ArgumentParser(description="Build dictionary-pack.json.gz (< 80 MB)")
    parser.add_argument("--out", default="dictionary-pack.json.gz", help="Output .json.gz file path")
    parser.add_argument("--cache-dir", default="/tmp/lexicon_build_cache", help="Download cache dir")
    args = parser.parse_args()

    os.makedirs(args.cache_dir, exist_ok=True)
    freq_ranks = load_frequency_ranks(args.cache_dir)
    tatoeba_examples = load_tatoeba_pairs(args.cache_dir)

    kaikki_en_path = os.path.join(args.cache_dir, "kaikki-en.jsonl.gz")
    if not os.path.exists(kaikki_en_path):
        if not download_file(KAIKKI_EN_URL, kaikki_en_path, "Kaikki English Wiktionary"):
            sys.exit("FATAL: Could not download Kaikki English Wiktionary dump")

    kaikki_ar_path = os.path.join(args.cache_dir, "kaikki-ar.jsonl.gz")
    if not os.path.exists(kaikki_ar_path):
        download_file(KAIKKI_AR_URL, kaikki_ar_path, "Kaikki Arabic Wiktionary")

    words_map = {}
    idioms_map = {}
    reverse_ar = defaultdict(dict)

    process_kaikki_english(
        gz_path=kaikki_en_path,
        freq_ranks=freq_ranks,
        words_map=words_map,
        idioms_map=idioms_map,
        reverse_ar=reverse_ar,
        tatoeba_examples=tatoeba_examples,
    )
    process_kaikki_arabic(
        gz_path=kaikki_ar_path,
        freq_ranks=freq_ranks,
        words_map=words_map,
        reverse_ar=reverse_ar,
    )
    enrich_with_wordnet_and_omwn(
        cache_dir=args.cache_dir,
        freq_ranks=freq_ranks,
        words_map=words_map,
        idioms_map=idioms_map,
        reverse_ar=reverse_ar,
    )
    apply_curated_idioms(idioms_map)

    if len(idioms_map) < MIN_IDIOMS_TARGET:
        print(
            f"[warn] Only {len(idioms_map)} idioms collected (< {MIN_IDIOMS_TARGET}); check upstream dumps.",
            file=sys.stderr,
        )

    words_sorted = sorted(words_map.values(), key=lambda x: (x.get("rank", 999999), x["word"]))
    idioms_sorted = sorted(
        idioms_map.values(),
        key=lambda x: (0 if x.get("ar") else 1, x.get("rank", 999999), x["phrase"]),
    )

    reverse_list = []
    for (norm_ar, voc_ar), matches_dict in reverse_ar.items():
        if not matches_dict:
            continue
        sorted_matches = sorted(matches_dict.values(), key=lambda m: (m.get("rank", 999999), m["en"]))
        reverse_list.append(
            {
                "ar": norm_ar,
                "voc": voc_ar,
                "matches": sorted_matches[:100],
            }
        )
    reverse_list.sort(key=lambda g: (0 if has_diacritics(g["voc"]) else 1, g["ar"], g["voc"]))

    # Keep all senses intact; OfflinePackManager computes full previewSummary on import from senses
    max_words = 65_000
    max_idioms = max(MIN_IDIOMS_TARGET, min(48_000, len(idioms_sorted)))
    max_reverse = 85_000

    for attempt in range(1, 7):
        w_slice = words_sorted[:max_words]
        i_slice = idioms_sorted[:max_idioms]
        r_slice = reverse_list[:max_reverse]
        size_bytes = serialize_pack(w_slice, i_slice, r_slice, args.out)
        size_mb = size_bytes / (1024 * 1024)
        print(
            f"[pack attempt {attempt}] words={len(w_slice):,}, idioms={len(i_slice):,}, "
            f"reverse_ar={len(r_slice):,}, size={size_mb:.2f} MB"
        )
        if size_bytes <= MAX_PACK_BYTES:
            print(f"[success] Built {args.out}: {size_mb:.2f} MB (< 80 MB)")
            return
        # Trim word/reverse/idiom slice sizes proportionally while preserving ALL senses per included word
        max_words = max(20_000, int(max_words * 0.72))
        max_reverse = max(35_000, int(max_reverse * 0.75))
        max_idioms = max(MIN_IDIOMS_TARGET, int(max_idioms * 0.85))

    final_size = os.path.getsize(args.out)
    if final_size >= 80 * 1024 * 1024:
        sys.exit(f"ERROR: dictionary-pack.json.gz ({final_size} bytes) exceeds 80 MB limit")


if __name__ == "__main__":
    main()
