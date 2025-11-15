#!/usr/bin/env python3
"""
Comprehensive translation push - Target 55%+ completion across all languages.
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

# Large comprehensive batch
translations = {
    'fr': {
        'moscovium': """Le moscovium est un élément chimique de symbole Mc et de numéro atomique 115. Il a été synthétisé pour la première fois en 2003 par une équipe de chercheurs russes et américains à l'Institut unifié de recherches nucléaires à Dubna, en Russie. En décembre 2015, il a été reconnu comme l'un des quatre nouveaux éléments par le groupe de travail conjoint des unions internationales de chimie pure et appliquée et de physique pure et appliquée. Le 28 novembre 2016, il a été officiellement nommé moscovium d'après la région de Moscou.""",
        
        'neodymium': """Le néodyme est un élément chimique de symbole Nd et de numéro atomique 60. Le néodyme appartient à la série des lanthanides et est un métal de terre rare. C'est un métal dur, légèrement jaunâtre, argenté qui ternit dans l'air. Le néodyme a été découvert par le chimiste autrichien Carl Auer von Welsbach en 1885. Il constitue actuellement 18% de la production de métaux des terres rares. Le néodyme n'est pas trouvé naturellement dans sa forme métallique ou non mélangé avec d'autres lanthanides, et il est généralement raffiné pour une utilisation générale.""",
        
        'neon': """Le néon est un élément chimique de symbole Ne et de numéro atomique 10. C'est un gaz noble. Le néon est un gaz incolore, inodore et inerte dans des conditions standard, avec environ deux tiers de la densité de l'air. Il a été découvert (avec le krypton et le xénon) en 1898 comme l'un des trois éléments inertes rares résiduels restant dans l'air sec après que l'azote, l'oxygène, l'argon et le dioxyde de carbone ont été éliminés. Le néon est le cinquième élément le plus abondant dans l'univers par masse.""",
        
        'neptunium': """Le neptunium est un élément chimique de symbole Np et de numéro atomique 93. Un élément transuranien radioactif métallique, le neptunium est le premier élément transuranien. Son isotope le plus stable, le neptunium-237, est un sous-produit des réacteurs nucléaires et de la production de plutonium et peut être utilisé comme composant dans les armes nucléaires et comme précurseur dans la production de plutonium-238. Le neptunium est également trouvé en traces dans les minerais d'uranium.""",
        
        'nickel': """Le nickel est un élément chimique de symbole Ni et de numéro atomique 28. C'est un métal blanc argenté brillant avec une légère teinte dorée. Le nickel appartient aux métaux de transition et est dur et ductile. Le nickel pur, en poudre pour maximiser la surface réactive, montre une activité chimique significative, mais les pièces plus grandes sont lentes à réagir avec l'air dans des conditions standard car une couche d'oxyde se forme à la surface et empêche une corrosion supplémentaire (passivation).""",
        
        'nihonium': """Le nihonium est un élément chimique de symbole Nh et de numéro atomique 113. C'est un élément synthétique extrêmement radioactif (un élément qui peut être créé en laboratoire mais qui ne se trouve pas dans la nature). Son isotope le plus stable connu, le nihonium-286, a une demi-vie d'environ 10 secondes. Le nihonium a été synthétisé pour la première fois en 2003 par une équipe de chercheurs japonais du RIKEN et en 2004 par une équipe de chercheurs russes et américains.""",
    },
    'es': {
        'vanadium': """El vanadio es un elemento químico con el símbolo V y número atómico 23. Es un metal de transición duro, de color blanco plateado, dúctil y maleable. El descubrimiento del vanadio se le atribuye generalmente al químico mexicano Andrés Manuel del Río, quien encontró el metal en 1801. El vanadio se encuentra naturalmente en aproximadamente 65 minerales diferentes y en depósitos de combustibles fósiles. Se produce en China y Rusia a partir de escoria de acero; en otros países, se obtiene directamente de magnetita vanadífera.""",
        
        'chromium': """El cromo es un elemento químico con el símbolo Cr y número atómico 24. Es el primer elemento del grupo 6. Es un metal de transición duro, brillante, gris acero y muy frágil. El cromo es el principal elemento de adición en el acero inoxidable, al cual confiere sus propiedades anticorrosivas. El cromo también es muy valorado como un metal que es capaz de tomar un alto pulido mientras resiste el empañamiento. El cromo pulido refleja casi el 70% del espectro visible, con casi el 90% de la luz infrarroja reflejada.""",
        
        'manganese': """El manganeso es un elemento químico con el símbolo Mn y número atómico 25. No se encuentra como elemento libre en la naturaleza; a menudo se encuentra en combinación con el hierro. El manganeso es un metal de color gris plateado que se parece al hierro. Es un metal duro y muy frágil, difícil de fundir, pero fácilmente oxidado. El manganeso y sus compuestos se pueden encontrar distribuidos en la corteza terrestre y en los suelos. El manganeso es crucial tanto para la industria como para la vida.""",
        
        'iron': """El hierro es un elemento químico con el símbolo Fe (del latín: ferrum) y número atómico 26. Es un metal del primer grupo de transición. Es, con mucho, el elemento más común en la Tierra, formando gran parte del núcleo externo e interno de la Tierra. Es el cuarto elemento más común en la corteza terrestre. Su forma metálica comprende principalmente hierro puro o poco aleado en el núcleo interno de la Tierra, y una aleación de hierro-níquel (FeNi) en el núcleo externo de la Tierra.""",
        
        'cobalt': """El cobalto es un elemento químico con el símbolo Co y número atómico 27. Al igual que el níquel, el cobalto se encuentra en la corteza terrestre solo en forma químicamente combinada, excepto por pequeños depósitos encontrados en aleaciones de hierro meteórico natural. El elemento libre, producido por fundición reductora, es un metal duro, brillante, de color gris plateado. El cobalto fue descubierto por el químico sueco Georg Brandt en 1735.""",
    },
    'de': {
        'vanadium': """Vanadium ist ein chemisches Element mit dem Symbol V und der Ordnungszahl 23. Es ist ein hartes, silbrig-weißes, duktiles und formbares Übergangsmetall. Die Entdeckung des Vanadiums wird allgemein dem mexikanischen Chemiker Andrés Manuel del Río zugeschrieben, der das Metall 1801 fand. Vanadium kommt natürlich in etwa 65 verschiedenen Mineralien und in Lagerstätten fossiler Brennstoffe vor. Es wird in China und Russland aus Stahlschlacke produziert; in anderen Ländern wird es direkt aus Vanadium-Magnetit gewonnen.""",
        
        'chromium': """Chrom ist ein chemisches Element mit dem Symbol Cr und der Ordnungszahl 24. Es ist das erste Element der Gruppe 6. Es ist ein hartes, glänzendes, stahlgraues und sehr sprödes Übergangsmetall. Chrom ist das wichtigste Zusatzelement in rostfreiem Stahl, dem es seine korrosionsbeständigen Eigenschaften verleiht. Chrom wird auch sehr als Metall geschätzt, das in der Lage ist, eine hohe Politur zu erhalten und gleichzeitig dem Anlaufen zu widerstehen. Poliertes Chrom reflektiert fast 70% des sichtbaren Spektrums, wobei fast 90% des Infrarotlichts reflektiert werden.""",
        
        'manganese': """Mangan ist ein chemisches Element mit dem Symbol Mn und der Ordnungszahl 25. Es wird nicht als freies Element in der Natur gefunden; es wird oft in Kombination mit Eisen gefunden. Mangan ist ein silbergraues Metall, das Eisen ähnelt. Es ist ein hartes und sehr sprödes Metall, schwer zu schmelzen, aber leicht zu oxidieren. Mangan und seine Verbindungen können in der Erdkruste und in Böden verteilt gefunden werden. Mangan ist sowohl für die Industrie als auch für das Leben entscheidend.""",
        
        'iron': """Eisen ist ein chemisches Element mit dem Symbol Fe (vom lateinischen: ferrum) und der Ordnungszahl 26. Es ist ein Metall der ersten Übergangsgruppe. Es ist bei weitem das häufigste Element auf der Erde und bildet einen Großteil des äußeren und inneren Kerns der Erde. Es ist das vierthäufigste Element in der Erdkruste. Seine metallische Form besteht hauptsächlich aus reinem oder wenig legiertem Eisen im inneren Kern der Erde und einer Eisen-Nickel-Legierung (FeNi) im äußeren Kern der Erde.""",
        
        'cobalt': """Kobalt ist ein chemisches Element mit dem Symbol Co und der Ordnungszahl 27. Wie Nickel wird Kobalt in der Erdkruste nur in chemisch kombinierter Form gefunden, außer bei kleinen Ablagerungen, die in Legierungen aus natürlichem meteorischem Eisen gefunden werden. Das freie Element, das durch reduktive Schmelze hergestellt wird, ist ein hartes, glänzendes, silbergraues Metall. Kobalt wurde 1735 vom schwedischen Chemiker Georg Brandt entdeckt.""",
    },
    'it': {
        'argon': """L'argon è un elemento chimico con simbolo Ar e numero atomico 18. Si trova nel gruppo 18 della tavola periodica ed è un gas nobile. L'argon è il terzo gas più abbondante nell'atmosfera terrestre, con lo 0,934% (9340 ppmv). È più di due volte più abbondante del vapore acqueo (che in media è circa 4000 ppmv, ma varia molto), 23 volte più abbondante dell'anidride carbonica (400 ppmv) e più di 500 volte più abbondante del neon (18 ppmv).""",
        
        'potassium': """Il potassio è un elemento chimico con simbolo K (dal neolatino kalium) e numero atomico 19. Fu isolato per la prima volta dalla potassa, le ceneri delle piante, da cui ha ricevuto il suo nome. Nella tavola periodica, il potassio è uno dei metalli alcalini. Tutti i metalli alcalini hanno un singolo elettrone di valenza nel guscio elettronico esterno, che viene facilmente rimosso per creare uno ione con carica positiva: un catione, che si combina con anioni per formare sali.""",
        
        'calcium': """Il calcio è un elemento chimico con simbolo Ca e numero atomico 20. Come metallo alcalino-terroso, il calcio è un metallo grigio reattivo morbido che forma uno strato di ossido scuro quando esposto all'aria. Le sue proprietà fisiche e chimiche sono molto simili a quelle dei suoi omologhi più pesanti, stronzio e bario. È il quinto elemento più abbondante nella crosta terrestre e il terzo metallo più abbondante, dopo ferro e alluminio.""",
        
        'scandium': """Lo scandio è un elemento chimico con simbolo Sc e numero atomico 21. Un metallo di transizione bianco argenteo, è stato storicamente classificato come elemento delle terre rare, insieme all'ittrio e ai lantanidi. Fu scoperto nel 1879 mediante analisi spettrale dei minerali euxenite e gadolinite dalla Scandinavia. Lo scandio è presente nella maggior parte dei depositi di composti di terre rare e elementi di uranio, ma viene estratto da queste miniere solo in poche miniere in tutto il mondo.""",
        
        'titanium': """Il titanio è un elemento chimico con simbolo Ti e numero atomico 22. È un metallo di transizione brillante con un colore argenteo, bassa densità e alta resistenza. Il titanio è resistente alla corrosione in acqua di mare, acqua regia e cloro. Fu scoperto in Gran Bretagna da William Gregor nel 1791, nominato dai Titani della mitologia greca. L'elemento appare in molti minerali, essendo le fonti principali il rutilo e l'ilmenite, che sono ampiamente distribuiti nella crosta terrestre e nella litosfera.""",
    },
    'pt': {
        'argon': """O argônio é um elemento químico com o símbolo Ar e número atômico 18. Está no grupo 18 da tabela periódica e é um gás nobre. O argônio é o terceiro gás mais abundante na atmosfera da Terra, com 0,934% (9340 ppmv). É mais de duas vezes mais abundante que o vapor de água (que em média é cerca de 4000 ppmv, mas varia muito), 23 vezes mais abundante que o dióxido de carbono (400 ppmv) e mais de 500 vezes mais abundante que o neônio (18 ppmv).""",
        
        'potassium': """O potássio é um elemento químico com o símbolo K (do neolatim kalium) e número atômico 19. Foi isolado pela primeira vez da potassa, as cinzas das plantas, das quais recebeu seu nome. Na tabela periódica, o potássio é um dos metais alcalinos. Todos os metais alcalinos têm um único elétron de valência na camada eletrônica externa, que é facilmente removido para criar um íon com carga positiva: um cátion, que se combina com ânions para formar sais.""",
        
        'calcium': """O cálcio é um elemento químico com o símbolo Ca e número atômico 20. Como um metal alcalino-terroso, o cálcio é um metal cinza reativo macio que forma uma camada de óxido escuro quando exposto ao ar. Suas propriedades físicas e químicas são muito semelhantes às de seus homólogos mais pesados, estrôncio e bário. É o quinto elemento mais abundante na crosta terrestre e o terceiro metal mais abundante, depois do ferro e do alumínio.""",
        
        'scandium': """O escândio é um elemento químico com o símbolo Sc e número atômico 21. Um metal de transição branco prateado, foi historicamente classificado como um elemento de terra rara, juntamente com o ítrio e os lantanídeos. Foi descoberto em 1879 por análise espectral dos minerais euxenita e gadolinita da Escandinávia. O escândio está presente na maioria dos depósitos de compostos de terras raras e elementos de urânio, mas é extraído dessas minas apenas em algumas minas em todo o mundo.""",
        
        'titanium': """O titânio é um elemento químico com o símbolo Ti e número atômico 22. É um metal de transição brilhante com uma cor prateada, baixa densidade e alta resistência. O titânio é resistente à corrosão em água do mar, água régia e cloro. Foi descoberto na Grã-Bretanha por William Gregor em 1791, nomeado pelos Titãs da mitologia grega. O elemento aparece em muitos minerais, sendo as fontes principais o rutilo e a ilmenita, que são amplamente distribuídos na crosta terrestre e na litosfera.""",
    },
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
