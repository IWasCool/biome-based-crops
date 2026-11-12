"""Fill newly added Minecraft locale text, preserving existing authored translations.

Run with Python 3: py tools/UpdateTranslations.py --catalog <English-key JSON>.
Public UI strings are machine-translated through Google's public Translate endpoint;
the reproducible response cache and coverage report stay in ignored build/.
Unsupported languages retain explicit English fallbacks unless supplied in
build/localization-special.json. No API keys or other credentials are used.
"""

import argparse
import concurrent.futures
import json
import pathlib
import re
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
LANG = ROOT / "src/main/resources/assets/biome_based_crops/lang"
CACHE = ROOT / "build/translation-cache"
FORMAT = re.compile(r"%(?:(\d+)\$)?s")
BRANDS = {"Biome Based Crops", "Biomes Notebook", "Tough As Nails", "Sable"}
PROTECTED = re.compile(FORMAT.pattern + "|" + "|".join(re.escape(brand) for brand in sorted(BRANDS, key=len, reverse=True)))
CACHE_LOCKS = {}
LOCKS_GUARD = threading.Lock()
LOCALE_TARGET = {
    "be_latn": "be", "brb": "nl", "enp": "en", "enws": "en", "esan": "es",
    "en_pt": "en", "en_ud": "en", "lol_us": "en", "fra_de": "de",
    "hal_ua": "uk", "hn_no": "nn", "io_en": "io", "jbo_en": "jbo",
    "no_no": "no", "nn_no": "nn", "qcb_es": "es", "qid": "id",
    "rpr": "ru", "ry_ua": "rue", "sr_cs": "sr", "sr_sp": "sr",
    "val_es": "ca", "vp_vl": "viossa", "zh_cn": "zh-CN",
    "zh_hk": "zh-TW", "zh_tw": "zh-TW", "zlm_arab": "ms",
    "fil_ph": "tl", "tl_ph": "tl", "qya_aa": "qya", "tlh_aa": "tlh",
    "sah_sah": "sah", "se_no": "se", "tzo_mx": "tzo",
}
# Standard parent-language inheritance for regional writing variants.
PARENTS = {"bar": "de", "ksh": "de", "nds": "de", "sxu": "de",
           "fra": "de", "nn": "no", "ovd": "sv", "szl": "pl",
           "lzh": "zh-TW", "vec": "it", "lmo": "it", "fur": "it",
           "ast": "es", "esan": "es", "rue": "uk", "kw": "cy"}


def translation_input(text):
    # Disambiguate Minecraft's physical climate controls for machine translation.
    # Canonical English strings and cache keys remain exactly as authored.
    text = {
        "External Climate Controller Compatibility": "Support for external temperature controllers",
        "Client Tooltips": "Client-side tooltips",
        "Required Native Specimen Blocks": "Required blocks native to this biome",
        "Climate Rules Version": "Version of the climate rules",
        "Biome Specimen Block Overrides": "Custom blocks representing the biome",
        "Legacy Roof Glass Survey Ratio": "Ratio of roof glass for old greenhouse surveys",
        "Electrical Thermoregulator": "Electric temperature regulator",
        "Compressor hums": "The compressor is humming",
        "Next biomes": "Next natural biomes",
    }.get(text, text)
    text = text.replace("Faint air wisps indicate openings.", "Faint air particles indicate openings.")
    text = re.sub(r"\bAC outlets\b", "air outlets of the air conditioners", text)
    text = re.sub(r"\bAC units\b", "air conditioners", text)
    text = re.sub(r"\bAC unit\b", "air conditioner", text)
    return re.sub(r"\bAC\b", "air conditioner", text)


class UnsupportedLanguage(Exception):
    pass


def request_translation(target, text):
    query = urllib.parse.urlencode({"client": "gtx", "sl": "en", "tl": target,
                                   "dt": "t", "q": text})
    url = "https://translate.googleapis.com/translate_a/single?" + query
    for attempt in range(4):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(request, timeout=45) as response:
                result = json.load(response)
            return "".join(segment[0] for segment in result[0] if segment[0])
        except urllib.error.HTTPError as error:
            if error.code == 400:
                raise UnsupportedLanguage(target) from error
            if attempt == 3:
                raise
            time.sleep(1 + attempt * 2)
        except (TimeoutError, OSError):
            if attempt == 3:
                raise
            time.sleep(1 + attempt * 2)


def protect(text):
    arguments = []
    def replace(match):
        arguments.append(match.group())
        return "{" + str(801000 + len(arguments)) + "}"
    return PROTECTED.sub(replace, text), arguments


def restore(text, arguments):
    for index, argument in enumerate(arguments, 1):
        pattern = r"\{\s*" + str(801000 + index) + r"\s*\}"
        text, count = re.subn(pattern, lambda _: argument, text, flags=re.IGNORECASE)
        if count != 1:
            raise ValueError("Translation changed a format argument")
    if "__BBC" in text or "BBC_ARG" in text or re.search(r"\{\s*801\d+\s*\}", text):
        raise ValueError("Unrestored translation argument")
    return text.strip()


def translate_batch(target, texts):
    protected = [protect(translation_input(text)) for text in texts]
    query = "\n".join(f"[{index}] {item[0]}" for index, item in enumerate(protected))
    result = request_translation(target, query)
    markers = list(re.finditer(r"\[\s*(\d+)\s*\]\s*", result))
    translated = []
    if len(markers) == len(texts) and [int(m.group(1)) for m in markers] == list(range(len(texts))):
        for index, marker in enumerate(markers):
            end = markers[index + 1].start() if index + 1 < len(markers) else len(result)
            text = result[marker.end():end].strip().replace("\n", " ")
            try:
                translated.append(restore(text, protected[index][1]))
            except ValueError:
                translated.append(translate_one(target, texts[index]))
    else:
        for text in texts:
            translated.append(translate_one(target, text))
    # A batch can translate most labels while echoing a few longer English labels.
    # Retry just those individually; shared one-word terms may legitimately be identical.
    if any(value not in {source, translation_input(source)} for source, value in zip(texts, translated)):
        for index, (source, value) in enumerate(zip(texts, translated)):
            if value in {source, translation_input(source)} and len(source.split()) >= 3:
                translated[index] = translate_one(target, source)
    return translated


def translate_one(target, text):
    source, arguments = protect(translation_input(text))
    try:
        return restore(request_translation(target, source), arguments)
    except ValueError:
        # Some smaller translation models delete brace-enclosed numbers, while
        # they retain deliberately unpronounceable Latin markers verbatim.
        for index in range(1, len(arguments) + 1):
            source = source.replace("{" + str(801000 + index) + "}", f"ZXQ{index}ZXQ")
        result = request_translation(target, source)
        for index in range(1, len(arguments) + 1):
            result = re.sub(r"ZXQ\s*" + str(index) + r"\s*ZXQ",
                            "{" + str(801000 + index) + "}", result, flags=re.IGNORECASE)
        return restore(result, arguments)


def translate_language(target, texts):
    with LOCKS_GUARD:
        lock = CACHE_LOCKS.setdefault(target, threading.Lock())
    with lock:
        return translate_language_cached(target, texts)


def translate_language_cached(target, texts):
    CACHE.mkdir(parents=True, exist_ok=True)
    cache_file = CACHE / (target + ".json")
    existing = json.loads(cache_file.read_text(encoding="utf-8")) if cache_file.exists() else {}
    missing = [text for text in texts if text not in existing]
    batch, size = [], 0
    for text in missing + [None]:
        if text is None or (size + len(text) > 3800 and batch):
            if batch:
                translated = translate_batch(target, batch)
                existing.update(zip(batch, translated))
                cache_file.write_text(json.dumps(existing, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            batch, size = [], 0
        if text is not None:
            batch.append(text)
            size += len(text) + 10
    if target == "fr":
        existing = {source: value.replace("foules et autres créatures", "mobs et autres créatures")
                    for source, value in existing.items()}
        cache_file.write_text(json.dumps(existing, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return existing


def english_variant(locale, text):
    # English-language game variants keep their own reading styles.
    # Protect product names and format arguments from spelling/reading-style changes too.
    text, arguments = protect(text)
    if locale == "en_pt":
        replacements = {"you": "ye", "your": "yer", "the room": "the cabin",
                        "Room": "Cabin", "room": "cabin", "Previous": "Previous",
                        "No ": "Nay ", "Select": "Pick", "Click": "Tap"}
        for before, after in replacements.items():
            text = re.sub(r"\b" + re.escape(before), lambda _: after, text)
        return restore("Arrr! " + text, arguments)
    if locale == "lol_us":
        replacements = {"the": "da", "The": "Da", "you": "u", "Your": "Ur",
                        "your": "ur", "are": "r", "is": "iz", "Needs": "Needz",
                        "blocks": "blokz", "Blocks": "Blokz", "Climate": "Wether",
                        "climate": "wether", "greenhouse": "growy haus", "Greenhouse": "Growy Haus"}
        for before, after in replacements.items():
            text = re.sub(r"\b" + re.escape(before) + r"\b", lambda _: after, text)
        return restore(text, arguments)
    if locale == "enp":
        replacements = {"Climate": "Weather", "climate": "weather", "Biome": "Landkind",
                        "biome": "landkind", "Greenhouse": "Glasshouse", "greenhouse": "glasshouse",
                        "Requirements": "Needs", "requirements": "needs", "Interior": "Inside",
                        "interior": "inside", "Temperature": "Warmth", "temperature": "warmth",
                        "Configuration": "Settings", "Controller": "Weather Setter"}
        for before, after in replacements.items():
            text = text.replace(before, after)
        return restore(text, arguments)
    if locale == "enws":
        return restore(re.sub(r"\byour\b", "thy", re.sub(r"\byou\b", "thou", text)).replace("Click", "Press"), arguments)
    if locale == "en_ud":
        # Keep formatting arguments unflipped so Minecraft still substitutes them.
        alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!?.,:;()[]<>_&1234567890"
        upside = "ɐqɔpǝɟƃɥıɾʞlɯuodbɹsʇnʌʍxʎz∀ꓭƆᗡƎℲפHIſꓘ˥WNOԀꝹᴚS⊥∩ΛMX⅄Z¡¿˙'::)(][><‾⅋⇂ᄅƐㄣϛ9ㄥ860"
        mapping = dict(zip(alphabet, upside))
        next_argument = 1
        for index, argument in enumerate(arguments):
            if FORMAT.fullmatch(argument):
                arguments[index] = argument if "$" in argument else f"%{next_argument}$s"
                next_argument += 1
        pieces = re.split(r"(\{801\d+\})", text)
        flipped = []
        for part in reversed(pieces):
            flipped.append(part if part.startswith("{801") else "".join(mapping.get(c, c) for c in reversed(part)))
        return restore("".join(flipped), arguments)
    return restore(text, arguments)


def writing_variant(locale, text):
    text, arguments = protect(text)
    if locale == "de_ch":
        return restore(text.replace("ß", "ss"), arguments)
    if locale == "sr_cs":
        cyrillic = "абвгдђежзијклљмнњопрстћуфхцчџш"
        latin = ["a", "b", "v", "g", "d", "đ", "e", "ž", "z", "i", "j", "k", "l", "lj", "m", "n", "nj", "o", "p", "r", "s", "t", "ć", "u", "f", "h", "c", "č", "dž", "š"]
        table = dict(zip(cyrillic, latin))
        table.update({source.upper(): target.title() for source, target in zip(cyrillic, latin)})
        return restore("".join(table.get(char, char) for char in text), arguments)
    if locale == "be_latn":
        table = dict(zip("абвгдеёзйклмнопрстуўфхцчыьэ", ["a", "b", "v", "h", "d", "ie", "io", "z", "j", "k", "l", "m", "n", "o", "p", "r", "s", "t", "u", "ŭ", "f", "ch", "c", "č", "y", "", "e"]))
        table.update({"ж": "ž", "і": "i", "ш": "š", "ю": "iu", "я": "ia"})
        table.update({source.upper(): target.title() for source, target in list(table.items())})
        return restore("".join(table.get(char, char) for char in text), arguments)
    if locale == "qid":
        # Indonesian spelling before the 1972 reform.
        return restore(text.replace("j", "dj").replace("J", "Dj").replace("c", "tj").replace("C", "Tj").replace("u", "oe").replace("U", "Oe").replace("y", "j").replace("Y", "J"), arguments)
    if locale == "rpr":
        return restore(re.sub(r"([бвгджзклмнпрстфхцчшщБВГДЖЗКЛМНПРСТФХЦЧШЩ])\b", r"\1ъ", text), arguments)
    return restore(text, arguments)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--catalog", help="Optional English key catalog to merge into en_us.json")
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--refresh-keys", default="", help="Comma-separated keys to retranslate after terminology changes")
    parser.add_argument("--only-keys", default="", help="Limit work to these comma-separated keys, preserving all other existing entries")
    args = parser.parse_args()
    english = json.loads((LANG / "en_us.json").read_text(encoding="utf-8"))
    if args.catalog:
        english.update(json.loads((ROOT / args.catalog).read_text(encoding="utf-8")))
    (LANG / "en_us.json").write_text(json.dumps(english, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    records = {}
    groups = {}
    refresh_keys = set(filter(None, args.refresh_keys.split(",")))
    only_keys = set(filter(None, args.only_keys.split(",")))
    unknown_keys = (refresh_keys | only_keys) - english.keys()
    if unknown_keys:
        parser.error("Unknown translation keys: " + ", ".join(sorted(unknown_keys)))
    for path in sorted(LANG.glob("*.json")):
        locale = path.stem
        original = json.loads(path.read_text(encoding="utf-8"))
        keys = [key for key, value in english.items()
                if (not only_keys or key in only_keys) and value not in BRANDS
                and (key in refresh_keys or key not in original or original[key] == value)]
        target = LOCALE_TARGET.get(locale, locale.split("_")[0])
        records[locale] = (path, original, keys, target)
        if target != "en":
            groups.setdefault(target, set()).update(english[key] for key in keys)
    results, unsupported, errors, inherited = {}, [], {}, {}
    def work(item):
        target, texts = item
        try:
            if target == "viossa":
                raise UnsupportedLanguage(target)
            values = translate_language(target, sorted(texts))
            # The endpoint sometimes accepts unknown codes but echoes English.
            if values and all(values.get(text) in {text, translation_input(text)} for text in texts):
                raise UnsupportedLanguage(target)
            return target, values, None
        except UnsupportedLanguage:
            parent = PARENTS.get(target)
            if parent:
                return target, translate_language(parent, sorted(texts)), parent
            return target, {}, "unsupported"
        except Exception as error:
            return target, {}, repr(error)
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        for target, values, note in pool.map(work, groups.items()):
            results[target] = values
            if note == "unsupported":
                unsupported.append(target)
            elif note in PARENTS.values():
                inherited[target] = note
            elif note:
                errors[target] = note
            print(f"{target}: {len(values)} strings" + (f" ({note})" if note else ""), flush=True)
    specials_path = ROOT / "build/localization-special.json"
    specials = json.loads(specials_path.read_text(encoding="utf-8")) if specials_path.exists() else {}
    changed = 0
    fallback_locales = []
    for locale, (path, original, keys, target) in records.items():
        translated = dict(original)
        if target in unsupported or target in errors:
            fallback_locales.append(locale)
        for key, value in english.items():
            if key in keys:
                if target == "en":
                    value = english_variant(locale, value)
                else:
                    value = writing_variant(locale, results.get(target, {}).get(value, value))
                translated[key] = value
            elif key not in translated:
                translated[key] = value
        translated.update({key: value for key, value in specials.get(locale, {}).items()
                           if key in keys})
        for key, value in translated.items():
            expected = sorted(FORMAT.findall(english[key]))
            actual = sorted(FORMAT.findall(value))
            if len(expected) != len(actual):
                raise ValueError(f"Format mismatch in {locale}: {key}")
            if "__BBC" in value or "\ufffd" in value:
                raise ValueError(f"Invalid translation in {locale}: {key}")
        ordered = {key: translated[key] for key in english}
        path.write_text(json.dumps(ordered, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        changed += len(keys)
    report = {"locales": len(records), "updated_entries": changed,
              "unsupported_targets": unsupported, "fallback_locales": fallback_locales,
              "parent_language_inheritance": inherited, "errors": errors,
              "writing_system_fallbacks": {"zlm_arab": "Malay Latin script for new text; existing Jawi text preserved"}}
    (ROOT / "build/translation-coverage.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    published_coverage = {key: report[key] for key in
                          ("locales", "fallback_locales", "parent_language_inheritance", "writing_system_fallbacks")}
    published_coverage["source_keys"] = len(english)
    published_coverage["last_refreshed_keys"] = sorted(refresh_keys)
    published_coverage["translation_method"] = "Machine-assisted; native-speaker review is not complete."
    published_coverage["fallback_policy"] = "Existing authored translations are retained. Unsupported new strings use English; regional variants may inherit their parent language."
    (ROOT / "tools/translation-coverage.json").write_text(json.dumps(published_coverage, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report), flush=True)


if __name__ == "__main__":
    main()
