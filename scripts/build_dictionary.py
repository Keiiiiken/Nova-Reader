"""Build the offline Spanish dictionary from Spanish Wiktionary via Kaikki.
Definitions are CC BY-SA 4.0; see docs/DICCIONARIO-LICENCIA.md.
Run with Python 3; downloads are cached outside the Android project.
"""
import gzip,json,sqlite3,ssl,urllib.request,unicodedata,re,os
from pathlib import Path
URL='https://kaikki.org/eswiktionary/Espa%C3%B1ol/kaikki.org-dictionary-Espa%C3%B1ol.jsonl'
cache=Path(os.environ.get('NOVA_DICTIONARY_CACHE', Path.home()/'.cache'/'nova-reader-dictionary'));cache.mkdir(parents=True,exist_ok=True)
source=cache/'eswiktionary.jsonl'
if not source.exists():
    ctx=ssl.create_default_context()
    with urllib.request.urlopen(URL,context=ctx,timeout=60) as r,source.with_suffix('.pending').open('wb') as out:
        while chunk:=r.read(1024*1024): out.write(chunk)
    source.with_suffix('.pending').rename(source)
db=cache/'es-v1.sqlite';db.unlink(missing_ok=True)
conn=sqlite3.connect(db)
conn.execute('CREATE TABLE entries(word TEXT PRIMARY KEY, folded TEXT NOT NULL, payload TEXT NOT NULL) WITHOUT ROWID')
conn.execute('CREATE TABLE forms(word TEXT PRIMARY KEY, lemma TEXT NOT NULL) WITHOUT ROWID')
forms={}
entries={}
for line in source.open(encoding='utf-8'):
    item=json.loads(line)
    if item.get('lang_code') != 'es': continue
    word=unicodedata.normalize('NFC',item['word'].lower().strip())
    if len(word)>80 or not word: continue
    for sense in item.get('senses',[]):
        lemmas=[x['word'].lower().strip() for x in sense.get('form_of',[]) if 'word' in x]
        if lemmas:
            forms.setdefault(word,lemmas[0])
            continue
        for gloss in sense.get('glosses',[]):
            # Some Spanish gerund/participle records lack a structured form_of.
            form=re.fullmatch(r'(?:Gerundio|Participio) de ([^ .]+)\.?',gloss)
            if form:
                forms.setdefault(word,form.group(1).lower())
                continue
            glosses=entries.setdefault(word,[])
            value={'pos':item.get('pos',''),'definition':gloss}
            if value not in glosses and len(glosses)<12: glosses.append(value)
for word,glosses in sorted(entries.items()):
    if not glosses: continue
    folded=''.join(c for c in unicodedata.normalize('NFD',word) if not unicodedata.combining(c))
    conn.execute('INSERT INTO entries VALUES(?,?,?)',(word,folded,json.dumps(glosses,ensure_ascii=False,separators=(',',':'))))
for word,lemma in sorted(forms.items()):
    if word not in entries and lemma in entries: conn.execute('INSERT INTO forms VALUES(?,?)',(word,lemma))
conn.execute('CREATE INDEX folded_idx ON entries(folded COLLATE NOCASE)');conn.commit();conn.execute('VACUUM')
count=conn.execute('SELECT COUNT(*) FROM entries').fetchone()[0]
form_count=conn.execute('SELECT COUNT(*) FROM forms').fetchone()[0]
for word in ['libro','efímero','luz','novela']:
    row=conn.execute('SELECT payload FROM entries WHERE word=?',(word,)).fetchone()
    print(word, row[0][:250] if row else 'missing',flush=True)
conn.close()
asset=cache/'dictionary-es-v1.sqlite.gz'
assets=Path(__file__).resolve().parents[1]/'app/src/main/assets'
asset.parent.mkdir(parents=True,exist_ok=True)
with db.open('rb') as inp,asset.open('wb') as out:
    with gzip.GzipFile(fileobj=out,mode='wb',mtime=0) as gz:
        while chunk:=inp.read(1024*1024): gz.write(chunk)
(assets/'dictionary-info.json').write_text(json.dumps({'language':'es','entries':count,'forms':form_count,'source':URL,'license':'CC BY-SA 4.0','edition':'Spanish Wiktionary','version':1},ensure_ascii=False),encoding='utf-8')
print('Entries:',count,'forms:',form_count,'DB bytes:',db.stat().st_size,'compressed:',asset.stat().st_size,flush=True)

parts=assets/'dictionary-es-v1';parts.mkdir(parents=True,exist_ok=True)
for old in parts.glob('part-*'):old.unlink()
with asset.open('rb') as inp:
    index=0
    while chunk:=inp.read(147456):
        (parts/f'part-{index:03d}.bin').write_bytes(chunk);index+=1
