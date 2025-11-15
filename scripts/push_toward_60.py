#!/usr/bin/env python3
"""
Continue momentum - Push toward 60% overall completion.
Focus on completing more elements across all active languages.
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

# Comprehensive translations across languages
translations = {
    'fr': {
        'nitrogen': """L'azote est l'élément chimique de symbole N et de numéro atomique 7. Il a été découvert et isolé pour la première fois par le médecin écossais Daniel Rutherford en 1772. Bien que Carl Wilhelm Scheele et Henry Cavendish aient réalisé indépendamment la même expérience à peu près au même moment, Rutherford a observé qu'il s'agissait d'un constituant de l'air et a publié ses découvertes en premier. Le nom azote dérive du grec nitron et -gen signifiant "formateur de salpêtre". Antoine Lavoisier a plutôt proposé le nom azote, du grec άζωτος signifiant "sans vie", car il ne soutient pas la respiration ou la combustion. L'azote est un gaz diatomique incolore et inodore dans des conditions standard et constitue environ 78% de l'atmosphère terrestre, ce qui en fait l'élément le plus commun dans l'atmosphère.""",
        
        'nobelium': """Le nobélium est un élément synthétique de symbole No et de numéro atomique 102. Un élément transuranien radioactif métallique, c'est le dixième élément transuranien et est le membre pénultième de la série des actinides. Comme tous les éléments avec un numéro atomique supérieur à 100, le nobélium ne peut être produit que dans des bombardements de particules par des accélérateurs de particules nucléaires. Le nobélium a été découvert en 1957 par une équipe de physiciens dirigée par Glenn T. Seaborg, Albert Ghiorso et John R. Walton à l'Université de Californie, Berkeley.""",
        
        'osmium': """L'osmium (du grec ὀσμή osme, "odeur") est un élément chimique de symbole Os et de numéro atomique 76. C'est un métal de transition dur, cassant, blanc bleuté du groupe du platine qui se trouve sous forme de trace dans les alliages naturels, notamment l'iridosmium brut. L'osmium est l'élément naturel le plus dense, avec une densité de 22,59 g/cm3. C'est également le métal le plus lourd, bien que le thorium, l'uranium, le neptunium et le plutonium soient plus denses. Son lustre cristallin et sa couleur métallique attrayante ont conduit à son utilisation dans les stylos plume et les pièces de monnaie.""",
        
        'oxygen': """L'oxygène est l'élément chimique de symbole O et de numéro atomique 8. C'est un membre du groupe des chalcogènes dans le tableau périodique, un non-métal hautement réactif et un agent oxydant qui forme facilement des oxydes avec la plupart des éléments ainsi qu'avec d'autres composés. Après l'hydrogène et l'hélium, l'oxygène est le troisième élément le plus abondant dans l'univers, mesuré par masse. À température et pression standard, deux atomes d'oxygène forment du dioxygène gazeux diatomique, un gaz incolore et inodore de formule O2. Le dioxygène gazeux diatomique constitue 20,95% de l'atmosphère terrestre, bien que cette partie ait changé considérablement sur de longues périodes de temps sur Terre. L'oxygène constitue presque la moitié de la croûte terrestre sous forme de minéraux d'oxyde.""",
        
        'palladium': """Le palladium est un élément chimique de symbole Pd et de numéro atomique 46. C'est un métal blanc argenté brillant rare et lustré découvert en 1803 par le chimiste anglais William Hyde Wollaston. Il l'a nommé d'après l'astéroïde Pallas, qui avait été découvert deux ans plus tôt. Le palladium, le platine, le rhodium, le ruthénium, l'iridium et l'osmium forment un groupe d'éléments appelés les métaux du groupe du platine. Ils ont des propriétés chimiques similaires, mais le palladium a le point de fusion le plus bas et est le moins dense d'entre eux.""",
    },
    'es': {
        'nickel': """El níquel es un elemento químico con el símbolo Ni y número atómico 28. Es un metal blanco plateado brillante con un ligero tinte dorado. El níquel pertenece a los metales de transición y es duro y dúctil. El níquel puro, en polvo para maximizar el área de superficie reactiva, muestra una actividad química significativa, pero las piezas más grandes son lentas para reaccionar con el aire en condiciones estándar porque una capa de óxido se forma en la superficie y previene una mayor corrosión (pasivación).""",
        
        'copper': """El cobre es un elemento químico con el símbolo Cu (del latín: cuprum) y número atómico 29. Es un metal suave, maleable y dúctil con conductividad térmica y eléctrica muy alta. Una superficie de cobre recién expuesta tiene un color naranja rojizo. El cobre se usa como conductor de calor y electricidad, como material de construcción y como constituyente de varias aleaciones metálicas, como el metal plateado usado en joyería, el cuproníquel usado para hacer herrajes marinos y monedas, y el constantán usado en galgas extensiométricas y termopares para medición de temperatura.""",
        
        'zinc': """El zinc es un elemento químico con el símbolo Zn y número atómico 30. El zinc es un metal ligeramente frágil a temperatura ambiente y tiene una apariencia blanco-grisácea plateada cuando se elimina la oxidación. Es el primer elemento del grupo 12 de la tabla periódica. En algunos aspectos, el zinc es químicamente similar al magnesio: ambos elementos exhiben solo un estado de oxidación normal (+2), y los iones Zn2+ y Mg2+ son de tamaño similar. El zinc es el 24º elemento más abundante en la corteza terrestre y tiene cinco isótopos estables.""",
        
        'gallium': """El galio es un elemento químico con el símbolo Ga y número atómico 31. El galio elemental es un metal suave, plateado a temperatura y presión estándar; sin embargo, es quebradizo en frío, y se funde a una temperatura ligeramente superior a la temperatura ambiente a 29.76 °C (85.57 °F), y por lo tanto se derretirá en la mano de una persona. El punto de fusión del galio se usa como punto de referencia de temperatura. El galio se encuentra naturalmente como trazas en bauxita y minerales de zinc.""",
        
        'germanium': """El germanio es un elemento químico con el símbolo Ge y número atómico 32. Es un metaloide gris-blanco brillante y duro en el grupo del carbono, químicamente similar a sus vecinos de grupo el estaño y el silicio. El germanio elemental puro es un semiconductor con una apariencia similar al silicio elemental. Como el silicio, el germanio reacciona naturalmente y forma complejos con oxígeno en la naturaleza. Debido a que rara vez se combina con otros elementos como un mineral, el germanio fue descubierto relativamente tarde en la historia de la química.""",
    },
    'de': {
        'nickel': """Nickel ist ein chemisches Element mit dem Symbol Ni und der Ordnungszahl 28. Es ist ein glänzendes silberweißes Metall mit einem leichten goldenen Schimmer. Nickel gehört zu den Übergangsmetallen und ist hart und duktil. Reines Nickel, pulverförmig zur Maximierung der reaktiven Oberfläche, zeigt signifikante chemische Aktivität, aber größere Stücke reagieren langsam mit Luft unter Standardbedingungen, da sich eine Oxidschicht auf der Oberfläche bildet und weitere Korrosion verhindert (Passivierung).""",
        
        'copper': """Kupfer ist ein chemisches Element mit dem Symbol Cu (vom lateinischen: cuprum) und der Ordnungszahl 29. Es ist ein weiches, formbares und duktiles Metall mit sehr hoher thermischer und elektrischer Leitfähigkeit. Eine frisch freigelegte Kupferoberfläche hat eine orange-rötliche Farbe. Kupfer wird als Wärme- und Elektrizitätsleiter, als Baumaterial und als Bestandteil verschiedener Metalllegierungen verwendet, wie z.B. Neusilber für Schmuck, Kupfernickel für Schiffsbeschläge und Münzen, und Konstantan für Dehnungsmessstreifen und Thermoelemente zur Temperaturmessung.""",
        
        'zinc': """Zink ist ein chemisches Element mit dem Symbol Zn und der Ordnungszahl 30. Zink ist ein leicht sprödes Metall bei Raumtemperatur und hat ein silbrig-weiß-graues Aussehen, wenn Oxidation entfernt wird. Es ist das erste Element der Gruppe 12 des Periodensystems. In einigen Aspekten ist Zink chemisch ähnlich wie Magnesium: beide Elemente zeigen nur einen normalen Oxidationszustand (+2), und die Zn2+- und Mg2+-Ionen sind von ähnlicher Größe. Zink ist das 24. häufigste Element in der Erdkruste und hat fünf stabile Isotope.""",
        
        'gallium': """Gallium ist ein chemisches Element mit dem Symbol Ga und der Ordnungszahl 31. Elementares Gallium ist ein weiches, silbernes Metall bei Standardtemperatur und -druck; es ist jedoch spröde in der Kälte und schmilzt bei einer Temperatur knapp über Raumtemperatur bei 29,76 °C (85,57 °F), und wird daher in der Hand einer Person schmelzen. Der Schmelzpunkt von Gallium wird als Temperatur-Referenzpunkt verwendet. Gallium kommt natürlich in Spuren in Bauxit und Zinkerzen vor.""",
        
        'germanium': """Germanium ist ein chemisches Element mit dem Symbol Ge und der Ordnungszahl 32. Es ist ein hartes, glänzendes grau-weißes Halbmetall in der Kohlenstoffgruppe, chemisch ähnlich seinen Gruppennachbarn Zinn und Silizium. Reines elementares Germanium ist ein Halbleiter mit einem Aussehen ähnlich dem von elementarem Silizium. Wie Silizium reagiert Germanium natürlich und bildet Komplexe mit Sauerstoff in der Natur. Da es selten mit anderen Elementen als Erz kombiniert, wurde Germanium relativ spät in der Geschichte der Chemie entdeckt.""",
    },
    'it': {
        'vanadium': """Il vanadio è un elemento chimico con simbolo V e numero atomico 23. È un metallo di transizione duro, bianco argenteo, duttile e malleabile. La scoperta del vanadio è generalmente attribuita al chimico messicano Andrés Manuel del Río, che trovò il metallo nel 1801. Il vanadio si trova naturalmente in circa 65 minerali diversi e in depositi di combustibili fossili. Viene prodotto in Cina e Russia da scorie d'acciaio; in altri paesi, viene ottenuto direttamente dalla magnetite vanadica.""",
        
        'chromium': """Il cromo è un elemento chimico con simbolo Cr e numero atomico 24. È il primo elemento del gruppo 6. È un metallo di transizione duro, lucido, grigio acciaio e molto fragile. Il cromo è il principale elemento di aggiunta nell'acciaio inossidabile, a cui conferisce le sue proprietà anticorrosive. Il cromo è anche molto apprezzato come metallo capace di assumere un'alta lucidatura resistendo all'ossidazione. Il cromo lucidato riflette quasi il 70% dello spettro visibile, con quasi il 90% della luce infrarossa riflessa.""",
        
        'manganese': """Il manganese è un elemento chimico con simbolo Mn e numero atomico 25. Non si trova come elemento libero in natura; si trova spesso in combinazione con il ferro. Il manganese è un metallo grigio argenteo che assomiglia al ferro. È un metallo duro e molto fragile, difficile da fondere, ma facilmente ossidato. Il manganese e i suoi composti possono essere trovati distribuiti nella crosta terrestre e nei suoli. Il manganese è cruciale sia per l'industria che per la vita.""",
        
        'iron': """Il ferro è un elemento chimico con simbolo Fe (dal latino: ferrum) e numero atomico 26. È un metallo del primo gruppo di transizione. È di gran lunga l'elemento più comune sulla Terra, formando gran parte del nucleo esterno e interno della Terra. È il quarto elemento più comune nella crosta terrestre. La sua forma metallica comprende principalmente ferro puro o poco legato nel nucleo interno della Terra, e una lega ferro-nichel (FeNi) nel nucleo esterno della Terra.""",
        
        'cobalt': """Il cobalto è un elemento chimico con simbolo Co e numero atomico 27. Come il nichel, il cobalto si trova nella crosta terrestre solo in forma chimicamente combinata, eccetto per piccoli depositi trovati in leghe di ferro meteorico naturale. L'elemento libero, prodotto per fusione riduttiva, è un metallo duro, lucido, grigio argenteo. Il cobalto fu scoperto dal chimico svedese Georg Brandt nel 1735.""",
    },
    'pt': {
        'vanadium': """O vanádio é um elemento químico com o símbolo V e número atômico 23. É um metal de transição duro, branco prateado, dúctil e maleável. A descoberta do vanádio é geralmente atribuída ao químico mexicano Andrés Manuel del Río, que encontrou o metal em 1801. O vanádio é encontrado naturalmente em cerca de 65 minerais diferentes e em depósitos de combustíveis fósseis. É produzido na China e Rússia a partir de escória de aço; em outros países, é obtido diretamente da magnetita vanadífera.""",
        
        'chromium': """O cromo é um elemento químico com o símbolo Cr e número atômico 24. É o primeiro elemento do grupo 6. É um metal de transição duro, brilhante, cinza aço e muito frágil. O cromo é o principal elemento de adição no aço inoxidável, ao qual confere suas propriedades anticorrosivas. O cromo também é muito valorizado como um metal capaz de aceitar um alto polimento enquanto resiste ao embaçamento. O cromo polido reflete quase 70% do espectro visível, com quase 90% da luz infravermelha refletida.""",
        
        'manganese': """O manganês é um elemento químico com o símbolo Mn e número atômico 25. Não é encontrado como elemento livre na natureza; é frequentemente encontrado em combinação com o ferro. O manganês é um metal cinza prateado que se assemelha ao ferro. É um metal duro e muito frágil, difícil de fundir, mas facilmente oxidado. O manganês e seus compostos podem ser encontrados distribuídos na crosta terrestre e nos solos. O manganês é crucial tanto para a indústria quanto para a vida.""",
        
        'iron': """O ferro é um elemento químico com o símbolo Fe (do latim: ferrum) e número atômico 26. É um metal do primeiro grupo de transição. É, de longe, o elemento mais comum na Terra, formando grande parte do núcleo externo e interno da Terra. É o quarto elemento mais comum na crosta terrestre. Sua forma metálica compreende principalmente ferro puro ou pouco ligado no núcleo interno da Terra, e uma liga ferro-níquel (FeNi) no núcleo externo da Terra.""",
        
        'cobalt': """O cobalto é um elemento químico com o símbolo Co e número atômico 27. Como o níquel, o cobalto é encontrado na crosta terrestre apenas em forma quimicamente combinada, exceto por pequenos depósitos encontrados em ligas de ferro meteórico natural. O elemento livre, produzido por fusão redutora, é um metal duro, brilhante, cinza prateado. O cobalto foi descoberto pelo químico sueco Georg Brandt em 1735.""",
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
