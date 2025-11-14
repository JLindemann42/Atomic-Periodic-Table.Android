#!/usr/bin/env python3
"""
Continue batch translations - Push toward 60% completion.
"""

import json
import os

ASSETS_PATH = '/home/runner/work/Atomic-Periodic-Table.Android/Atomic-Periodic-Table.Android/app/src/main/assets'

def bulk_update(lang_code, translations_dict):
    lang_file = os.path.join(ASSETS_PATH, f'elements_{lang_code}.json')
    with open(lang_file, 'r', encoding='utf-8') as f:
        lang_data = json.load(f)
    
    updated = 0
    for elem_key, description in translations_dict.items():
        if elem_key in lang_data:
            lang_data[elem_key]['description'] = description
            updated += 1
    
    with open(lang_file, 'w', encoding='utf-8') as f:
        json.dump(lang_data, f, ensure_ascii=False, indent=2)
    
    return updated

# More translations to push forward
translations = {
    'fr': {
        'lutetium': """Le lutécium est un élément chimique de symbole Lu et de numéro atomique 71. C'est un métal blanc argenté, qui résiste à la corrosion dans l'air sec, mais pas dans l'air humide. Le lutécium est le dernier élément de la série des lanthanides, et il est traditionnellement compté parmi les éléments de terres rares. Le lutécium a été découvert indépendamment en 1907 par trois scientifiques : le scientifique français Georges Urbain, le minéralogiste autrichien Baron Carl Auer von Welsbach, et le chimiste américain Charles James.""",
        
        'manganese': """Le manganèse est un élément chimique de symbole Mn et de numéro atomique 25. Ce n'est pas trouvé comme élément libre dans la nature; il est souvent trouvé en combinaison avec le fer. Le manganèse est un métal gris argent qui ressemble au fer. C'est un métal dur et très cassant, difficile à fondre, mais facilement oxydé. Le manganèse et ses composés peuvent être trouvés distribuant dans la croûte terrestre et dans les sols. Le manganèse est crucial à la fois pour l'industrie et la vie.""",
        
        'meitnerium': """Le meitnérium est un élément chimique de symbole Mt et de numéro atomique 109. C'est un élément synthétique extrêmement radioactif (un élément qui peut être créé en laboratoire mais qui ne se trouve pas dans la nature); l'isotope le plus stable connu, le meitnérium-278, a une demi-vie de 7,6 secondes. Le meitnérium a été synthétisé pour la première fois en 1982 par une équipe de recherche allemande menée par Peter Armbruster et Gottfried Münzenberg au GSI Helmholtz Centre for Heavy Ion Research.""",
        
        'mendelevium': """Le mendélévium est un élément synthétique de symbole Md (anciennement Mv) et de numéro atomique 101. Un élément transuranien radioactif métallique de la série des actinides, le mendélévium est généralement synthétisé en bombardant l'einsteinium avec des particules alpha. Il a été découvert par bombardement d'einsteinium-253 avec des particules alpha à l'Université de Californie, Berkeley en 1955 et nommé en l'honneur de Dmitri Mendeleev, créateur du tableau périodique des éléments chimiques.""",
        
        'mercury': """Le mercure est un élément chimique de symbole Hg et de numéro atomique 80. C'est également connu sous le nom d'argent vif et était autrefois nommé hydrargyre. Un métal lourd blanc argenté liquide à température et pression standard, le mercure est le seul métal élémentaire connu qui est liquide dans ces conditions. Le seul autre élément qui est liquide dans ces conditions est le brome, bien que les métaux césium, gallium et rubidium fondent juste au-dessus de la température ambiante.""",
        
        'molybdenum': """Le molybdène est un élément chimique de symbole Mo et de numéro atomique 42. Le nom est du néo-latin molybdaenum, du grec ancien Μόλυβδος molybdos, signifiant plomb, car ses minerais ont été confondus avec des minerais de plomb. Les minéraux de molybdène sont connus depuis la préhistoire, mais l'élément a été découvert (dans le sens de différencié comme une nouvelle entité des sels minéraux d'autres métaux) en 1778 par Carl Wilhelm Scheele.""",
    },
    'es': {
        'argon': """El argón es un elemento químico con el símbolo Ar y número atómico 18. Está en el grupo 18 de la tabla periódica y es un gas noble. El argón es el tercer gas más abundante en la atmósfera de la Tierra, con 0,934% (9340 ppmv). Es más de dos veces más abundante que el vapor de agua (que promedia aproximadamente 4000 ppmv, pero varía mucho), 23 veces más abundante que el dióxido de carbono (400 ppmv) y más de 500 veces más abundante que el neón (18 ppmv).""",
        
        'potassium': """El potasio es un elemento químico con el símbolo K (del neolatín kalium) y número atómico 19. Fue aislado por primera vez a partir de potasa, las cenizas de las plantas, de las cuales recibió su nombre. En la tabla periódica, el potasio es uno de los metales alcalinos. Todos los metales alcalinos tienen un solo electrón de valencia en la capa electrónica exterior, que se elimina fácilmente para crear un ion con carga positiva: un catión, que se combina con aniones para formar sales.""",
        
        'calcium': """El calcio es un elemento químico con el símbolo Ca y número atómico 20. Como metal alcalinotérreo, el calcio es un metal blando gris reactivo que forma una capa de óxido oscuro cuando se expone al aire. Sus propiedades físicas y químicas son muy similares a las de sus homólogos más pesados, el estroncio y el bario. Es el quinto elemento más abundante en la corteza terrestre y el tercer metal más abundante, después del hierro y el aluminio.""",
        
        'scandium': """El escandio es un elemento químico con el símbolo Sc y número atómico 21. Un metal de transición blanco plateado, ha sido clasificado históricamente como un elemento de tierra rara, junto con el itrio y los lantánidos. Fue descubierto en 1879 por análisis espectral de los minerales euxenita y gadolinita de Escandinavia. El escandio está presente en la mayoría de los depósitos de compuestos de tierras raras y elementos de uranio, pero se extrae de estas minas solo en unas pocas minas en todo el mundo.""",
        
        'titanium': """El titanio es un elemento químico con el símbolo Ti y número atómico 22. Es un metal de transición brillante con un color plateado, baja densidad y alta resistencia. El titanio es resistente a la corrosión en agua de mar, agua regia y cloro. Fue descubierto en Gran Bretaña por William Gregor en 1791, nombrado por los Titanes de la mitología griega. El elemento aparece en muchos minerales siendo las fuentes principales el rutilo y la ilmenita, que están ampliamente distribuidos en la corteza terrestre y la litosfera.""",
    },
    'de': {
        'argon': """Argon ist ein chemisches Element mit dem Symbol Ar und der Ordnungszahl 18. Es gehört zur Gruppe 18 des Periodensystems und ist ein Edelgas. Argon ist das dritthäufigste Gas in der Erdatmosphäre mit 0,934% (9340 ppmv). Es ist mehr als doppelt so häufig wie Wasserdampf (der im Durchschnitt etwa 4000 ppmv beträgt, aber stark variiert), 23-mal häufiger als Kohlendioxid (400 ppmv) und mehr als 500-mal häufiger als Neon (18 ppmv).""",
        
        'potassium': """Kalium ist ein chemisches Element mit dem Symbol K (vom neulateinischen kalium) und der Ordnungszahl 19. Es wurde erstmals aus Pottasche, der Asche von Pflanzen, isoliert, von der es seinen Namen erhielt. Im Periodensystem ist Kalium eines der Alkalimetalle. Alle Alkalimetalle haben ein einzelnes Valenzelektron in der äußeren Elektronenschale, das leicht entfernt wird, um ein positiv geladenes Ion zu erzeugen: ein Kation, das sich mit Anionen zu Salzen verbindet.""",
        
        'calcium': """Calcium ist ein chemisches Element mit dem Symbol Ca und der Ordnungszahl 20. Als Erdalkalimetall ist Calcium ein weiches, graues, reaktives Metall, das bei Luftexposition eine dunkle Oxidschicht bildet. Seine physikalischen und chemischen Eigenschaften sind denen seiner schwereren Homologen Strontium und Barium sehr ähnlich. Es ist das fünfthäufigste Element in der Erdkruste und das dritthäufigste Metall nach Eisen und Aluminium.""",
        
        'scandium': """Scandium ist ein chemisches Element mit dem Symbol Sc und der Ordnungszahl 21. Ein silberweißes Übergangsmetall, wurde es historisch als Seltenerdelement klassifiziert, zusammen mit Yttrium und den Lanthaniden. Es wurde 1879 durch Spektralanalyse der Mineralien Euxenit und Gadolinit aus Skandinavien entdeckt. Scandium ist in den meisten Lagerstätten von Seltenerdverbindungen und Uranelementen vorhanden, wird aber weltweit nur in wenigen Minen aus diesen Erzen gewonnen.""",
        
        'titanium': """Titan ist ein chemisches Element mit dem Symbol Ti und der Ordnungszahl 22. Es ist ein glänzendes Übergangsmetall mit silberner Farbe, niedriger Dichte und hoher Festigkeit. Titan ist beständig gegen Korrosion in Meerwasser, Königswasser und Chlor. Es wurde 1791 in Großbritannien von William Gregor entdeckt und nach den Titanen der griechischen Mythologie benannt. Das Element erscheint in vielen Mineralien, wobei die Hauptquellen Rutil und Ilmenit sind, die in der Erdkruste und Lithosphäre weit verbreitet sind.""",
    },
    'it': {
        'aluminium': """L'alluminio (aluminum in inglese americano e canadese) è un elemento chimico con simbolo Al e numero atomico 13. È un metallo argenteo, morbido, non magnetico e duttile nel gruppo del boro. In massa, l'alluminio costituisce circa l'8% della crosta terrestre, dove è il terzo elemento più abbondante (dopo ossigeno e silicio) e anche il metallo più abbondante. La presenza di alluminio diminuisce nel mantello terrestre sottostante. Il principale minerale di alluminio è la bauxite.""",
        
        'silicon': """Il silicio è un elemento chimico con simbolo Si e numero atomico 14. È un semimetallo duro, lucido, grigio-bluastro. È un membro del gruppo 14 nella tavola periodica: il carbonio è sopra e germanio, stagno, piombo e flerovio sono sotto. È relativamente non reattivo. A causa delle sue alte energie di ionizzazione chimica, affinità elettroniche ed energie di formazione dei composti sono quasi esclusivamente tetravalenti nella sua chimica. Il silicio cristallino puro è troppo reattivo per esistere in natura.""",
        
        'phosphorus': """Il fosforo è un elemento chimico con simbolo P e numero atomico 15. Il fosforo elementare esiste in due forme principali, fosforo bianco e fosforo rosso, ma poiché è altamente reattivo, il fosforo non si trova mai come elemento libero sulla Terra. Ha una concentrazione nella crosta terrestre di circa un grammo per chilogrammo. Il fosforo fu scoperto nel 1669 da Hennig Brand ad Amburgo, Germania. Il fosforo è un elemento multivalente della famiglia dell'azoto.""",
        
        'sulfur': """Lo zolfo è un elemento chimico con simbolo S e numero atomico 16. È abbondante, multivalente e non metallico. In condizioni normali, gli atomi di zolfo formano molecole di ottazolfo ciclico con formula S8. Lo zolfo elementare è un solido cristallino giallo brillante a temperatura ambiente. Lo zolfo è il decimo elemento più comune per massa nell'universo e il quinto più comune sulla Terra. Sebbene a volte si trovi in forma pura e nativa, lo zolfo sulla Terra di solito si presenta come minerali di solfuro e solfato.""",
        
        'chlorine': """Il cloro è un elemento chimico con simbolo Cl e numero atomico 17. Il secondo alogeno più leggero, appare tra fluoro e bromo nella tavola periodica e le sue proprietà sono principalmente intermedie tra loro. Il cloro è un gas giallo-verde a temperatura ambiente. È un elemento estremamente reattivo e un forte agente ossidante: tra gli elementi, ha la terza elettronegatività più alta, dietro solo a ossigeno e fluoro.""",
    },
    'pt': {
        'aluminium': """O alumínio (aluminum em inglês americano e canadense) é um elemento químico com o símbolo Al e número atômico 13. É um metal prateado, macio, não magnético e dúctil no grupo do boro. Por massa, o alumínio constitui cerca de 8% da crosta terrestre, onde é o terceiro elemento mais abundante (depois do oxigênio e do silício) e também o metal mais abundante. A ocorrência de alumínio diminui no manto terrestre abaixo. O principal minério de alumínio é a bauxita.""",
        
        'silicon': """O silício é um elemento químico com o símbolo Si e número atômico 14. É um semimetal duro, brilhante, cinza-azulado. É um membro do grupo 14 na tabela periódica: o carbono está acima e germânio, estanho, chumbo e fleróvio estão abaixo. É relativamente não reativo. Devido às suas altas energias de ionização química, suas afinidades eletrônicas e as energias de formação de compostos são quase exclusivamente tetravalentes em sua química. O silício cristalino puro é muito reativo para existir na natureza.""",
        
        'phosphorus': """O fósforo é um elemento químico com o símbolo P e número atômico 15. O fósforo elementar existe em duas formas principais, fósforo branco e fósforo vermelho, mas como é altamente reativo, o fósforo nunca é encontrado como elemento livre na Terra. Tem uma concentração na crosta terrestre de aproximadamente um grama por quilograma. O fósforo foi descoberto em 1669 por Hennig Brand em Hamburgo, Alemanha. O fósforo é um elemento multivalente da família do nitrogênio.""",
        
        'sulfur': """O enxofre é um elemento químico com o símbolo S e número atômico 16. É abundante, multivalente e não metálico. Sob condições normais, os átomos de enxofre formam moléculas de octaenxofre cíclico com fórmula S8. O enxofre elementar é um sólido cristalino amarelo brilhante à temperatura ambiente. O enxofre é o décimo elemento mais comum por massa no universo e o quinto mais comum na Terra. Embora às vezes seja encontrado em forma pura e nativa, o enxofre na Terra geralmente ocorre como minerais de sulfeto e sulfato.""",
        
        'chlorine': """O cloro é um elemento químico com o símbolo Cl e número atômico 17. O segundo halogênio mais leve, aparece entre o flúor e o bromo na tabela periódica e suas propriedades são principalmente intermediárias entre eles. O cloro é um gás amarelo-esverdeado à temperatura ambiente. É um elemento extremamente reativo e um agente oxidante forte: entre os elementos, tem a terceira eletronegatividade mais alta, apenas atrás do oxigênio e do flúor.""",
    }
}

# Process all translations
total_updated = 0
for lang_code, lang_translations in translations.items():
    count = bulk_update(lang_code, lang_translations)
    lang_names = {'fr': 'French', 'es': 'Spanish', 'de': 'German', 'it': 'Italian', 'pt': 'Portuguese'}
    print(f"✅ {lang_names.get(lang_code, lang_code)}: Updated {count} elements")
    total_updated += count

print(f"\n{'='*60}")
print(f"TOTAL: Updated {total_updated} element descriptions")
print(f"{'='*60}")
