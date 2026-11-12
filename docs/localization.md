# Localization

The language resources cover all 137 languages selectable in Minecraft 1.21.1, using asset index 17 and the bundled US English locale as the reference list. Every locale includes all 110 mod translation keys, with matching formatting arguments and five distinct climate names.

These translations are machine-assisted and have not all been reviewed by native speakers. Regional and historical writing styles are adapted separately. Shared terms and product names can correctly remain identical to English; **Biomes Notebook**, **Tough As Nails**, and **Sable** keep their names in every language.

The November 2026 audit refreshed gameplay guidance across all 137 locales: the notebook reference, sky-glass guidance, and missing-glass status. The notebook's setup paragraph stays focused on greenhouse construction, AC orientation and capacity, and leaks; experimental ship details and direct crop-control explanations are omitted to keep it compact. The audit also repaired 46 English-only configuration entries and nine other English-only interface entries in supported non-English locales, while preserving unrelated authored text.

New text retains explicit English fallbacks in ten less documented languages: Ido (`io_en`), Interslavic (`isv`), Lojban (`jbo_en`), Nahuatl (`nah`), Popoloca (`pls`), Quenya (`qya_aa`), Klingon (`tlh_aa`), Toki Pona (`tok`), Tzotzil (`tzo_mx`), and Viossa (`vp_vl`). Regional variants may inherit a related language. New Jawi (`zlm_arab`) messages use Malay Latin script; existing Jawi text is preserved.

Corrections are welcome in `src/main/resources/assets/biome_based_crops/lang`. Keep the JSON keys unchanged, preserve `%s` arguments or their numbered forms such as `%1$s`, and keep the five climate names distinct.
