#!/usr/bin/env python3
"""
Large-scale batch translator - Continue translations across languages.
"""

import json
import os
import sys

ASSETS_PATH = '/home/runner/work/Atomic-Periodic-Table.Android/Atomic-Periodic-Table.Android/app/src/main/assets'

def bulk_update(lang_code, translations_dict):
    """Update multiple elements for a language."""
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

# Large translation batch across multiple languages
translations = {
    'fr': {
        # Continue French
        'iodine': """L'iode est un élément chimique de symbole I et de numéro atomique 53. L'halogène le plus lourd des éléments stables, il existe sous forme de solide noir lustré dans des conditions standard qui fond pour former un liquide violet foncé à 114 degrés Celsius, et bout pour former un gaz violet à 184 degrés Celsius. L'élément a été découvert par le chimiste français Bernard Courtois en 1811. Il a été nommé deux ans plus tard par Joseph Louis Gay-Lussac, d'après le grec ἰώδης ioeidēs, signifiant violet ou violet.""",
        
        'iridium': """L'iridium est un élément chimique de symbole Ir et de numéro atomique 77. Un métal de transition très dur, cassant, blanc argenté du groupe du platine, l'iridium est considéré comme le deuxième élément le plus dense (après l'osmium) sur la base de densité mesurée de cristaux simples. C'est également le métal le plus résistant à la corrosion, même à des températures aussi élevées que 2000 °C. Bien que seulement certains composés d'iridium natif soient connus, l'iridium se trouve dans les alliages naturels avec du platine ou de l'osmium brut.""",
        
        'iron': """Le fer est un élément chimique de symbole Fe (du latin : ferrum) et de numéro atomique 26. C'est un métal du premier groupe de transition. C'est de loin l'élément le plus commun sur Terre, formant une grande partie du noyau externe et interne de la Terre. C'est le quatrième élément le plus commun dans la croûte terrestre. Sa forme métallique comprend principalement du fer pur ou peu alliage dans le noyau interne de la Terre, et un alliage fer-nickel (FeNi) dans le noyau externe de la Terre.""",
        
        'krypton': """Le krypton (du grec ancien : κρυπτός, romanisé : kryptos 'le caché') est un élément chimique de symbole Kr et de numéro atomique 36. C'est un gaz incolore, inodore, insipide, non toxique, un gaz noble. Le krypton se trouve dans l'atmosphère terrestre en traces; l'air contient 1 ppm. Le krypton a été découvert en Grande-Bretagne en 1898 par William Ramsay et Morris Travers dans des résidus laissés de l'évaporation de composants d'air liquide.""",
        
        'lanthanum': """Le lanthane est un élément chimique de symbole La et de numéro atomique 57. C'est un métal mou, blanc argenté, malléable, ductile et malléable qui se ternit rapidement lorsqu'il est exposé à l'air et est suffisamment mou pour être coupé avec un couteau. Il a donné son nom à la série des lanthanides, un groupe de 15 éléments similaires entre le lanthane et le lutécium dans le tableau périodique, dont le lanthane est le premier et le prototype. Le lanthane est considéré comme l'une des terres rares.""",
    },
    'es': {
        # Continue Spanish
        'oxygen': """El oxígeno es el elemento químico con el símbolo O y número atómico 8. Es miembro del grupo calcógeno en la tabla periódica, un no metal altamente reactivo y un agente oxidante que fácilmente forma óxidos con la mayoría de los elementos así como con otros compuestos. Después del hidrógeno y el helio, el oxígeno es el tercer elemento más abundante en el universo, medido por masa. A temperatura y presión estándar, dos átomos de oxígeno forman gas dioxígeno diatómico, un gas incoloro e inodoro con la fórmula O2.""",
        
        'fluorine': """El flúor es un elemento químico con el símbolo F y número atómico 9. Es el halógeno más ligero y existe como un gas diatómico amarillo pálido altamente tóxico en condiciones estándar. Como el elemento más electronegativo, es extremadamente reactivo, ya que reacciona con casi todos los demás elementos, excepto el neón y el helio. Entre los elementos, el flúor tiene la tercera electronegatividad más alta, detrás solo del oxígeno y el cloro.""",
        
        'neon': """El neón es un elemento químico con el símbolo Ne y número atómico 10. Es un gas noble. El neón es un gas incoloro, inodoro e inerte en condiciones estándar, con aproximadamente dos tercios de la densidad del aire. Fue descubierto (junto con el criptón y el xenón) en 1898 como uno de los tres elementos raros inertes residuales que permanecen en el aire seco después de que se eliminan el nitrógeno, el oxígeno, el argón y el dióxido de carbono.""",
        
        'sodium': """El sodio es un elemento químico con el símbolo Na (del latín natrium) y número atómico 11. Es un metal blando, blanco plateado, altamente reactivo. El sodio es un metal alcalino, estando en el grupo 1 de la tabla periódica, porque tiene un solo electrón en su capa externa, que se desprende fácilmente, creando un ion con carga positiva: el catión Na+. Su único isótopo estable es 23Na. El elemento libre no se encuentra en la naturaleza y debe prepararse a partir de compuestos.""",
        
        'magnesium': """El magnesio es un elemento químico con el símbolo Mg y número atómico 12. Es un sólido gris brillante que tiene una estrecha semejanza física con los otros cinco elementos de la segunda columna (grupo 2, o metales alcalinotérreos) de la tabla periódica: todos los elementos del grupo 2 tienen la misma configuración electrónica en la capa de electrones más externa y una estructura cristalina similar.""",
    },
    'de': {
        # Continue German
        'oxygen': """Sauerstoff ist das chemische Element mit dem Symbol O und der Ordnungszahl 8. Es ist ein Mitglied der Chalkogengruppe im Periodensystem, ein hochreaktives Nichtmetall und ein Oxidationsmittel, das leicht Oxide mit den meisten Elementen sowie mit anderen Verbindungen bildet. Nach Wasserstoff und Helium ist Sauerstoff das dritthäufigste Element im Universum, gemessen an der Masse. Bei Standardtemperatur und -druck bilden zwei Sauerstoffatome zweiatomiges Disauerstoffgas, ein farbloses und geruchloses Gas mit der Formel O2.""",
        
        'fluorine': """Fluor ist ein chemisches Element mit dem Symbol F und der Ordnungszahl 9. Es ist das leichteste Halogen und existiert unter Standardbedingungen als hochgiftiges, blassgelbes zweiatomiges Gas. Als das elektronegativste Element ist es äußerst reaktiv, da es mit fast allen anderen Elementen reagiert, außer Neon und Helium. Unter den Elementen hat Fluor die dritthöchste Elektronegativität, nur hinter Sauerstoff und Chlor.""",
        
        'neon': """Neon ist ein chemisches Element mit dem Symbol Ne und der Ordnungszahl 10. Es ist ein Edelgas. Neon ist ein farbloses, geruchloses, inertes Gas unter Standardbedingungen mit etwa zwei Dritteln der Dichte von Luft. Es wurde 1898 (zusammen mit Krypton und Xenon) als eines von drei seltenen inerten Restelementen entdeckt, die in trockener Luft verbleiben, nachdem Stickstoff, Sauerstoff, Argon und Kohlendioxid entfernt wurden.""",
        
        'sodium': """Natrium ist ein chemisches Element mit dem Symbol Na (vom lateinischen natrium) und der Ordnungszahl 11. Es ist ein weiches, silberweißes, hochreaktives Metall. Natrium ist ein Alkalimetall, das sich in Gruppe 1 des Periodensystems befindet, weil es ein einzelnes Elektron in seiner äußeren Schale hat, das leicht entfernt wird und ein positiv geladenes Ion erzeugt: das Na+-Kation. Sein einziges stabiles Isotop ist 23Na. Das freie Element kommt in der Natur nicht vor und muss aus Verbindungen hergestellt werden.""",
        
        'magnesium': """Magnesium ist ein chemisches Element mit dem Symbol Mg und der Ordnungszahl 12. Es ist ein glänzender grauer Feststoff, der eine enge physikalische Ähnlichkeit mit den anderen fünf Elementen in der zweiten Spalte (Gruppe 2, oder Erdalkalimetalle) des Periodensystems hat: Alle Elemente der Gruppe 2 haben die gleiche Elektronenkonfiguration in der äußersten Elektronenschale und eine ähnliche Kristallstruktur.""",
    },
    'it': {
        # Start Italian
        'hydrogen': """L'idrogeno è l'elemento chimico con simbolo H e numero atomico 1. Con un peso atomico standard di 1,008, l'idrogeno è l'elemento più leggero della tavola periodica. L'idrogeno è la sostanza chimica più abbondante nell'universo, costituendo circa il 75% di tutta la massa barionica. Le stelle non-remnant sono composte principalmente di idrogeno nello stato di plasma. L'isotopo più comune dell'idrogeno (simbolo 1H) consiste di un protone, un elettrone e nessun neutrone.""",
        
        'helium': """L'elio (dal greco: ἥλιος, romanizzato: Helios, lett. 'Sole') è un elemento chimico con simbolo He e numero atomico 2. È un gas incolore, inodore, insapore, non tossico, inerte e monoatomico, il primo nel gruppo dei gas nobili nella tavola periodica. Il suo punto di ebollizione è il più basso tra tutti gli elementi. L'elio è il secondo elemento più leggero e il secondo più abbondante nell'universo osservabile (dopo l'idrogeno), rappresentando circa il 24% della massa elementare totale.""",
        
        'lithium': """Il litio è un elemento chimico con simbolo Li e numero atomico 3. È un metallo alcalino morbido, di colore bianco argenteo. In condizioni standard, è il metallo più leggero e l'elemento solido più leggero. Come tutti i metalli alcalini, il litio è altamente reattivo e corrosivo, e si ossida rapidamente nell'aria assumendo un colore nero opaco. Non si trova mai libero in natura, e appare solo in composti, che sono generalmente ionici.""",
        
        'carbon': """Il carbonio è un elemento chimico con simbolo C e numero atomico 6. È non metallico e tetravalente - rende disponibili quattro elettroni per formare legami chimici covalenti. Appartiene al gruppo 14 della tavola periodica. Il carbonio costituisce solo lo 0,025% della crosta terrestre, ma è cruciale per la vita. Tre isotopi si verificano naturalmente, con 12C e 13C stabili, mentre 14C è un radionuclide, decadendo con un'emivita di circa 5730 anni.""",
        
        'nitrogen': """L'azoto è l'elemento chimico con simbolo N e numero atomico 7. Fu scoperto e isolato per la prima volta dal medico scozzese Daniel Rutherford nel 1772. Sebbene Carl Wilhelm Scheele e Henry Cavendish avessero condotto indipendentemente lo stesso esperimento all'incirca nello stesso periodo, Rutherford osservò che era un costituente dell'aria e pubblicò per primo le sue scoperte. L'azoto è un gas diatomico incolore e inodore in condizioni standard.""",
    },
    'pt': {
        # Start Portuguese
        'hydrogen': """O hidrogênio é o elemento químico com o símbolo H e número atômico 1. Com um peso atômico padrão de 1,008, o hidrogênio é o elemento mais leve da tabela periódica. O hidrogênio é a substância química mais abundante no universo, constituindo aproximadamente 75% de toda a massa bariônica. As estrelas não-remanescentes são compostas principalmente de hidrogênio no estado de plasma. O isótopo mais comum do hidrogênio (símbolo 1H) consiste em um próton, um elétron e nenhum nêutron.""",
        
        'helium': """O hélio (do grego: ἥλιος, romanizado: Helios, lit. 'Sol') é um elemento químico com o símbolo He e número atômico 2. É um gás incolor, inodoro, insípido, não tóxico, inerte e monoatômico, o primeiro no grupo dos gases nobres na tabela periódica. Seu ponto de ebulição é o mais baixo entre todos os elementos. O hélio é o segundo elemento mais leve e o segundo mais abundante no universo observável (depois do hidrogênio), representando cerca de 24% da massa elementar total.""",
        
        'lithium': """O lítio é um elemento químico com o símbolo Li e número atômico 3. É um metal alcalino macio, de cor branca prateada. Sob condições padrão, é o metal mais leve e o elemento sólido mais leve. Como todos os metais alcalinos, o lítio é altamente reativo e corrosivo, e oxida rapidamente no ar para uma cor preta fosca. Nunca é encontrado livremente na natureza, e aparece apenas em compostos, que geralmente são iônicos.""",
        
        'carbon': """O carbono é um elemento químico com o símbolo C e número atômico 6. É não metálico e tetravalente - disponibiliza quatro elétrons para formar ligações químicas covalentes. Pertence ao grupo 14 da tabela periódica. O carbono constitui apenas 0,025% da crosta terrestre, mas é crucial para a vida. Três isótopos ocorrem naturalmente, sendo 12C e 13C estáveis, enquanto 14C é um radionuclídeo, decaindo com uma meia-vida de aproximadamente 5730 anos.""",
        
        'nitrogen': """O nitrogênio é o elemento químico com o símbolo N e número atômico 7. Foi descoberto e isolado pela primeira vez pelo médico escocês Daniel Rutherford em 1772. Embora Carl Wilhelm Scheele e Henry Cavendish tivessem realizado independentemente o mesmo experimento aproximadamente ao mesmo tempo, Rutherford observou que era um constituinte do ar e publicou suas descobertas primeiro. O nitrogênio é um gás diatômico incolor e inodoro em condições padrão.""",
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
