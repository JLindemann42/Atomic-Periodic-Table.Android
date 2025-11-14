#!/usr/bin/env python3
"""
Mega batch translator - Continue with more elements across all languages.
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

# Mega batch translations
translations = {
    'fr': {
        # Continue French - more common elements
        'lawrencium': """Le lawrencium est un élément chimique de symbole Lr (anciennement Lw) et de numéro atomique 103. C'est un élément synthétique radioactif (un élément qui peut être créé en laboratoire mais qui ne se trouve pas dans la nature) et est le dernier membre de la série des actinides. Comme tous les éléments avec un numéro atomique supérieur à 100, le lawrencium ne peut être produit que dans des bombardements de particules par des accélérateurs de particules nucléaires. Le lawrencium a été synthétisé pour la première fois par l'équipe nucléaire américaine dirigée par Albert Ghiorso le 14 février 1961.""",
        
        'lead': """Le plomb est un élément chimique de symbole Pb (du latin : plumbum) et de numéro atomique 82. C'est un métal lourd plus dense que la plupart des matériaux communs. Le plomb est mou et malléable, et a également un point de fusion relativement bas. Lorsqu'il est fraîchement coupé, le plomb est de couleur blanc bleuté argenté avec une teinte brillante; il ternit à une couleur gris terne lorsqu'il est exposé à l'air. Le plomb a le numéro atomique le plus élevé de tous les éléments stables et trois de ses isotopes sont des points finaux de grandes chaînes de désintégration nucléaire d'éléments plus lourds.""",
        
        'lithium': """Le lithium est un élément chimique de symbole Li et de numéro atomique 3. C'est un métal alcalin mou, de couleur blanc argenté. Dans des conditions standard, c'est le métal le plus léger et l'élément solide le plus léger. Comme tous les métaux alcalins, le lithium est hautement réactif et corrosif, et s'oxyde rapidement dans l'air à une couleur noire terne. Il n'est jamais trouvé librement dans la nature, et n'apparaît que dans des composés, qui sont généralement ioniques. Le lithium apparaît dans un certain nombre de composés de silicate de pegmatite.""",
        
        'lutetium': """Le lutécium est un élément chimique de symbole Lu et de numéro atomique 71. C'est un métal blanc argenté, qui résiste à la corrosion dans l'air sec, mais pas dans l'air humide. Le lutécium est le dernier élément de la série des lanthanides, et il est traditionnellement compté parmi les éléments de terres rares. Le lutécium a été découvert indépendamment en 1907 par trois scientifiques : le scientifique français Georges Urbain, le minéralogiste autrichien Baron Carl Auer von Welsbach, et le chimiste américain Charles James.""",
        
        'magnesium': """Le magnésium est un élément chimique de symbole Mg et de numéro atomique 12. C'est un solide gris brillant qui a une ressemblance physique étroite avec les cinq autres éléments de la deuxième colonne (groupe 2, ou métaux alcalino-terreux) du tableau périodique : tous les éléments du groupe 2 ont la même configuration électronique dans la couche électronique la plus externe et une structure cristalline similaire. Le magnésium est le neuvième élément le plus abondant dans l'univers. Il est produit dans de grandes étoiles vieillissantes par addition séquentielle de trois noyaux d'hélium à un noyau de carbone.""",
    },
    'es': {
        # Continue Spanish
        'aluminium': """El aluminio (aluminum en inglés americano y canadiense) es un elemento químico con el símbolo Al y número atómico 13. Es un metal plateado, suave, no magnético y dúctil en el grupo del boro. Por masa, el aluminio constituye aproximadamente el 8% de la corteza terrestre, donde es el tercer elemento más abundante (después del oxígeno y el silicio) y también el metal más abundante. La presencia de aluminio disminuye en el manto terrestre debajo. El principal mineral de aluminio es la bauxita.""",
        
        'silicon': """El silicio es un elemento químico con el símbolo Si y número atómico 14. Es un metaloide de color gris-azul brillante y duro. Es un miembro del grupo 14 en la tabla periódica: el carbono está encima y el germanio, el estaño, el plomo y el flerovio debajo. Es relativamente poco reactivo. Debido a sus altas energías de ionización químicas, sus afinidades electrónicas y las energías de formación de compuestos son casi exclusivamente tetravalentes en su química. El silicio cristalino puro es demasiado reactivo para existir en la naturaleza.""",
        
        'phosphorus': """El fósforo es un elemento químico con el símbolo P y número atómico 15. El fósforo elemental existe en dos formas principales, fósforo blanco y fósforo rojo, pero debido a que es altamente reactivo, el fósforo nunca se encuentra como elemento libre en la Tierra. Tiene una concentración en la corteza terrestre de aproximadamente un gramo por kilogramo. El fósforo fue descubierto en 1669 por Hennig Brand en Hamburgo, Alemania. El fósforo es un elemento multivalente de la familia del nitrógeno.""",
        
        'sulfur': """El azufre es un elemento químico con el símbolo S y número atómico 16. Es abundante, multivalente y no metálico. En condiciones normales, los átomos de azufre forman moléculas de octasulfuro cíclico con fórmula S8. El azufre elemental es un sólido cristalino amarillo brillante a temperatura ambiente. El azufre es el décimo elemento más común por masa en el universo y el quinto más común en la Tierra. Aunque a veces se encuentra en forma pura y nativa, el azufre en la Tierra generalmente ocurre como minerales de sulfuro y sulfato.""",
        
        'chlorine': """El cloro es un elemento químico con el símbolo Cl y número atómico 17. El segundo halógeno más ligero, aparece entre el flúor y el bromo en la tabla periódica y sus propiedades son principalmente intermedias entre ellos. El cloro es un gas amarillo verdoso a temperatura ambiente. Es un elemento extremadamente reactivo y un agente oxidante fuerte: entre los elementos, tiene la tercera electronegatividad más alta, solo detrás del oxígeno y el flúor.""",
    },
    'de': {
        # Continue German
        'aluminium': """Aluminium (aluminum im amerikanischen und kanadischen Englisch) ist ein chemisches Element mit dem Symbol Al und der Ordnungszahl 13. Es ist ein silbrig-weißes, weiches, nicht magnetisches und duktiles Metall in der Borgruppe. Nach Masse macht Aluminium etwa 8% der Erdkruste aus, wo es das dritthäufigste Element (nach Sauerstoff und Silizium) und auch das häufigste Metall ist. Das Vorkommen von Aluminium nimmt im Erdmantel darunter jedoch ab. Das Haupterz von Aluminium ist Bauxit.""",
        
        'silicon': """Silizium ist ein chemisches Element mit dem Symbol Si und der Ordnungszahl 14. Es ist ein hartes, glänzendes grau-blaues Halbmetall. Es ist ein Mitglied der Gruppe 14 im Periodensystem: Kohlenstoff steht darüber und Germanium, Zinn, Blei und Flerovium darunter. Es ist relativ unreaktiv. Aufgrund seiner hohen chemischen Ionisierungsenergien, seiner Elektronenaffinitäten und der Bildungsenergien von Verbindungen ist es in seiner Chemie fast ausschließlich vierwertig. Reines kristallines Silizium ist zu reaktiv, um in der Natur zu existieren.""",
        
        'phosphorus': """Phosphor ist ein chemisches Element mit dem Symbol P und der Ordnungszahl 15. Elementarer Phosphor existiert in zwei Hauptformen, weißer Phosphor und roter Phosphor, aber weil er hochreaktiv ist, wird Phosphor niemals als freies Element auf der Erde gefunden. Es hat eine Konzentration in der Erdkruste von etwa einem Gramm pro Kilogramm. Phosphor wurde 1669 von Hennig Brand in Hamburg, Deutschland, entdeckt. Phosphor ist ein multivalentes Element der Stickstoff-Familie.""",
        
        'sulfur': """Schwefel ist ein chemisches Element mit dem Symbol S und der Ordnungszahl 16. Es ist reichlich vorhanden, multivalent und nicht metallisch. Unter normalen Bedingungen bilden Schwefelatome zyklische Octaschwefel-Moleküle mit der Formel S8. Elementarer Schwefel ist ein hellgelber kristalliner Feststoff bei Raumtemperatur. Schwefel ist das zehnthäufigste Element nach Masse im Universum und das fünfthäufigste auf der Erde. Obwohl es manchmal in reiner, nativer Form gefunden wird, kommt Schwefel auf der Erde normalerweise als Sulfid- und Sulfat-Minerale vor.""",
        
        'chlorine': """Chlor ist ein chemisches Element mit dem Symbol Cl und der Ordnungszahl 17. Das zweit leichteste Halogen erscheint zwischen Fluor und Brom im Periodensystem, und seine Eigenschaften liegen hauptsächlich zwischen ihnen. Chlor ist ein gelb-grünes Gas bei Raumtemperatur. Es ist ein äußerst reaktives Element und ein starkes Oxidationsmittel: Unter den Elementen hat es die dritthöchste Elektronegativität, nur hinter Sauerstoff und Fluor.""",
    },
    'it': {
        # Continue Italian
        'oxygen': """L'ossigeno è l'elemento chimico con simbolo O e numero atomico 8. È membro del gruppo dei calcogeni nella tavola periodica, un non-metallo altamente reattivo e un agente ossidante che forma facilmente ossidi con la maggior parte degli elementi così come con altri composti. Dopo l'idrogeno e l'elio, l'ossigeno è il terzo elemento più abbondante nell'universo, misurato in massa. A temperatura e pressione standard, due atomi di ossigeno formano gas diossigeno diatomico, un gas incolore e inodore con la formula O2.""",
        
        'fluorine': """Il fluoro è un elemento chimico con simbolo F e numero atomico 9. È l'alogeno più leggero ed esiste come gas diatomico giallo pallido altamente tossico in condizioni standard. Come l'elemento più elettronegativo, è estremamente reattivo, poiché reagisce con quasi tutti gli altri elementi, eccetto il neon e l'elio. Tra gli elementi, il fluoro ha la terza elettronegatività più alta, dietro solo all'ossigeno e al cloro.""",
        
        'neon': """Il neon è un elemento chimico con simbolo Ne e numero atomico 10. È un gas nobile. Il neon è un gas incolore, inodore e inerte in condizioni standard, con circa due terzi della densità dell'aria. Fu scoperto (insieme al kripton e allo xenon) nel 1898 come uno dei tre rari elementi inerti residui che rimangono nell'aria secca dopo che sono stati rimossi azoto, ossigeno, argon e anidride carbonica.""",
        
        'sodium': """Il sodio è un elemento chimico con simbolo Na (dal latino natrium) e numero atomico 11. È un metallo morbido, bianco argenteo, altamente reattivo. Il sodio è un metallo alcalino, essendo nel gruppo 1 della tavola periodica, perché ha un singolo elettrone nel suo guscio esterno, che viene facilmente rimosso, creando uno ione con carica positiva: il catione Na+. Il suo unico isotopo stabile è 23Na. L'elemento libero non si trova in natura e deve essere preparato da composti.""",
        
        'magnesium': """Il magnesio è un elemento chimico con simbolo Mg e numero atomico 12. È un solido grigio lucido che ha una stretta somiglianza fisica con gli altri cinque elementi della seconda colonna (gruppo 2, o metalli alcalino-terrosi) della tavola periodica: tutti gli elementi del gruppo 2 hanno la stessa configurazione elettronica nel guscio elettronico più esterno e una struttura cristallina simile.""",
    },
    'pt': {
        # Continue Portuguese
        'oxygen': """O oxigênio é o elemento químico com o símbolo O e número atômico 8. É membro do grupo dos calcogênios na tabela periódica, um não-metal altamente reativo e um agente oxidante que forma facilmente óxidos com a maioria dos elementos assim como com outros compostos. Após o hidrogênio e o hélio, o oxigênio é o terceiro elemento mais abundante no universo, medido em massa. A temperatura e pressão padrão, dois átomos de oxigênio formam gás dioxigênio diatômico, um gás incolor e inodoro com a fórmula O2.""",
        
        'fluorine': """O flúor é um elemento químico com o símbolo F e número atômico 9. É o halogênio mais leve e existe como um gás diatômico amarelo pálido altamente tóxico em condições padrão. Como o elemento mais eletronegativo, é extremamente reativo, pois reage com quase todos os outros elementos, exceto neônio e hélio. Entre os elementos, o flúor tem a terceira eletronegatividade mais alta, atrás apenas do oxigênio e do cloro.""",
        
        'neon': """O neônio é um elemento químico com o símbolo Ne e número atômico 10. É um gás nobre. O neônio é um gás incolor, inodoro e inerte em condições padrão, com aproximadamente dois terços da densidade do ar. Foi descoberto (juntamente com criptônio e xenônio) em 1898 como um dos três elementos inertes raros residuais que permanecem no ar seco depois que nitrogênio, oxigênio, argônio e dióxido de carbono são removidos.""",
        
        'sodium': """O sódio é um elemento químico com o símbolo Na (do latim natrium) e número atômico 11. É um metal macio, branco prateado, altamente reativo. O sódio é um metal alcalino, estando no grupo 1 da tabela periódica, porque tem um único elétron em sua camada externa, que é facilmente removido, criando um íon com carga positiva: o cátion Na+. Seu único isótopo estável é 23Na. O elemento livre não é encontrado na natureza e deve ser preparado a partir de compostos.""",
        
        'magnesium': """O magnésio é um elemento químico com o símbolo Mg e número atômico 12. É um sólido cinza brilhante que tem uma estreita semelhança física com os outros cinco elementos da segunda coluna (grupo 2, ou metais alcalino-terrosos) da tabela periódica: todos os elementos do grupo 2 têm a mesma configuração eletrônica na camada eletrônica mais externa e uma estrutura cristalina semelhante.""",
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
