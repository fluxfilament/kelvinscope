#!/usr/bin/env python3
"""Add or replace interface strings in all six languages at once.

Every string in values/strings.xml needs its counterpart in values-ru, -de,
-es, -fr and -pt, or Lint fails the build with MissingTranslation. Adding one
string meant six hand edits; this takes them from one JSON file instead:

    {
      "row_example": {
        "en": "Example", "ru": "Пример", "de": "Beispiel",
        "es": "Ejemplo", "fr": "Exemple", "pt": "Exemplo"
      }
    }

Usage, from the repository root:

    python3 tools/add_strings.py new_strings.json

A name already present is replaced in place of being duplicated; new ones go
at the end of each file. Apostrophes are escaped for Android, & and < become
entities, and French gets its non-breaking space before : ; ? !.

Write %% for a literal percent only in strings that are formatted with
arguments; a string set with setText(R.string.x) shows %% as two characters.
Plurals are not handled: add those by hand.
"""
import json
import os
import re
import sys

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'res')
FOLDERS = {
    'en': 'values', 'ru': 'values-ru', 'de': 'values-de',
    'es': 'values-es', 'fr': 'values-fr', 'pt': 'values-pt',
}


def escape(text, lang):
    text = text.replace('&', '&amp;').replace('<', '&lt;')
    text = re.sub(r"(?<!\\)'", r"\\'", text)
    if lang == 'fr':
        text = re.sub(r' ([:;?!])', ' \\1', text)
    return text


def main(path, res=RES):
    data = json.load(open(path, encoding='utf-8'))
    for name, translations in data.items():
        missing = [lang for lang in FOLDERS if lang not in translations]
        if missing:
            sys.exit(f'{name}: no {", ".join(missing)}')
    for lang, folder in FOLDERS.items():
        file = os.path.join(res, folder, 'strings.xml')
        text = open(file, encoding='utf-8').read()
        added = ''
        for name, translations in data.items():
            line = f'    <string name="{name}">{escape(translations[lang], lang)}</string>\n'
            pattern = rf'    <string name="{re.escape(name)}">.*?</string>\n'
            if re.search(pattern, text, flags=re.S):
                text = re.sub(pattern, lambda _: line, text, count=1, flags=re.S)
            else:
                added += line
        text = text.replace('</resources>', added + '</resources>')
        open(file, 'w', encoding='utf-8').write(text)
    print(f'{len(data)} strings in {len(FOLDERS)} languages')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
