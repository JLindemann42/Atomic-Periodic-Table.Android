#!/usr/bin/env python3
"""
Efficient batch translator for element descriptions.
Updates multiple elements across multiple languages in one go.
"""

import json
import os

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

# Define translations for multiple languages
translations = {
    'fr': {
        # Continuing French from hafnium onwards
        'hassium': """Le hassium est un élément chimique de symbole Hs et de numéro atomique 108. C'est un élément synthétique (un élément qui peut être créé en laboratoire mais qui ne se trouve pas dans la nature) et radioactif; l'isotope le plus stable connu, le hassium-270, a une demi-vie d'environ 10 secondes. On s'attend à ce qu'il soit un solide dans des conditions normales. Le hassium a été synthétisé pour la première fois en 1984 par une équipe allemande dirigée par Peter Armbruster et Gottfried Münzenberg au GSI Helmholtz Centre for Heavy Ion Research à Darmstadt.""",
        
        'helium': """L'hélium (du grec : ἥλιος, romanisé : Helios, litt. 'Soleil') est un élément chimique de symbole He et de numéro atomique 2. C'est un gaz incolore, inodore, insipide, non toxique, inerte et monoatomique, le premier du groupe des gaz nobles dans le tableau périodique. Son point d'ébullition est le plus bas parmi tous les éléments. L'hélium est le deuxième élément le plus léger et le deuxième le plus abondant dans l'univers observable (après l'hydrogène), représentant environ 24% de la masse élémentaire totale.""",
        
        'holmium': """L'holmium est un élément chimique de symbole Ho et de numéro atomique 67. Faisant partie de la série des lanthanides, l'holmium est un élément de terre rare. L'holmium a été découvert par les chimistes suisses Marc Delafontaine et Jacques-Louis Soret en 1878. Son oxyde a été isolé pour la première fois de terres rares en 1878 par Per Teodor Cleve. L'élément porte le nom de Stockholm, Suède. L'holmium élémentaire est un métal relativement mou et malléable assez résistant à la corrosion et stable dans l'air sec à température ambiante.""",
        
        'hydrogen': """L'hydrogène est l'élément chimique de symbole H et de numéro atomique 1. Avec un poids atomique standard de 1,008, l'hydrogène est l'élément le plus léger du tableau périodique. L'hydrogène est la substance chimique la plus abondante dans l'univers, constituant environ 75% de toute la masse baryonique. Les étoiles non résiduelles sont principalement composées d'hydrogène à l'état plasma. L'isotope d'hydrogène le plus courant (symbole 1H) se compose d'un proton, d'un électron et d'aucun neutron.""",
        
        'indium': """L'indium est un élément chimique de symbole In et de numéro atomique 49. L'indium est le métal le plus mou qui n'est pas un métal alcalin. C'est un métal post-transition blanc argenté brillant. Le spectre de l'indium est dominé par une ligne indigo, d'où son nom. L'indium a un point de fusion plus élevé que le sodium et le gallium, mais inférieur au lithium et à l'étain. Chimiquement, l'indium est similaire au gallium et au thallium, et il est en grande partie intermédiaire entre les deux en termes de ses propriétés.""",
    },
    'es': {
        # Spanish translations
        'hydrogen': """El hidrógeno es el elemento químico con el símbolo H y número atómico 1. Con un peso atómico estándar de 1,008, el hidrógeno es el elemento más ligero de la tabla periódica. El hidrógeno es la sustancia química más abundante en el universo, constituyendo aproximadamente el 75% de toda la masa bariónica. Las estrellas no remanentes están compuestas principalmente de hidrógeno en estado de plasma. El isótopo de hidrógeno más común (símbolo 1H) consiste en un protón, un electrón y ningún neutrón.""",
        
        'helium': """El helio (del griego: ἥλιος, romanizado: Helios, lit. 'Sol') es un elemento químico con el símbolo He y número atómico 2. Es un gas incoloro, inodoro, insípido, no tóxico, inerte y monoatómico, el primero del grupo de gases nobles en la tabla periódica. Su punto de ebullición es el más bajo entre todos los elementos. El helio es el segundo elemento más ligero y el segundo más abundante en el universo observable (después del hidrógeno), representando aproximadamente el 24% de la masa elemental total.""",
        
        'lithium': """El litio es un elemento químico con el símbolo Li y número atómico 3. Es un metal alcalino blando, de color blanco plateado. Bajo condiciones estándar, es el metal más ligero y el elemento sólido más ligero. Como todos los metales alcalinos, el litio es altamente reactivo y corrosivo, y se oxida rápidamente en el aire a un color negro apagado. Nunca se encuentra libremente en la naturaleza, y solo aparece en compuestos, que generalmente son iónicos.""",
        
        'carbon': """El carbono es un elemento químico con el símbolo C y número atómico 6. Es no metálico y tetravalente, haciendo cuatro electrones disponibles para formar enlaces químicos covalentes. Pertenece al grupo 14 de la tabla periódica. El carbono constituye solo el 0,025% de la corteza terrestre, pero es crucial para la vida. Tres isótopos ocurren naturalmente, siendo 12C y 13C estables, mientras que 14C es un radionúclido, desintegrándose con una vida media de aproximadamente 5730 años.""",
        
        'nitrogen': """El nitrógeno es el elemento químico con el símbolo N y número atómico 7. Fue descubierto y aislado por primera vez por el médico escocés Daniel Rutherford en 1772. Aunque Carl Wilhelm Scheele y Henry Cavendish habían realizado independientemente el mismo experimento aproximadamente al mismo tiempo, Rutherford observó que era un constituyente del aire y publicó sus hallazgos primero. El nitrógeno es un gas diatómico incoloro e inodoro en condiciones estándar.""",
    },
    'de': {
        # German translations
        'hydrogen': """Wasserstoff ist das chemische Element mit dem Symbol H und der Ordnungszahl 1. Mit einem Standardatomgewicht von 1,008 ist Wasserstoff das leichteste Element im Periodensystem. Wasserstoff ist die häufigste chemische Substanz im Universum und macht etwa 75% der gesamten baryonischen Masse aus. Nicht-stellare Überreste bestehen hauptsächlich aus Wasserstoff im Plasmazustand. Das häufigste Wasserstoffisotop (Symbol 1H) besteht aus einem Proton, einem Elektron und keinem Neutron.""",
        
        'helium': """Helium (vom griechischen: ἥλιος, romanisiert: Helios, wörtl. 'Sonne') ist ein chemisches Element mit dem Symbol He und der Ordnungszahl 2. Es ist ein farbloses, geruchloses, geschmackloses, ungiftiges, inertes, einatomiges Gas, das erste in der Gruppe der Edelgase im Periodensystem. Sein Siedepunkt ist der niedrigste unter allen Elementen. Helium ist das zweitleichteste und zweithäufigste Element im beobachtbaren Universum (nach Wasserstoff) und macht etwa 24% der gesamten Elementmasse aus.""",
        
        'lithium': """Lithium ist ein chemisches Element mit dem Symbol Li und der Ordnungszahl 3. Es ist ein weiches, silberweißes Alkalimetall. Unter Standardbedingungen ist es das leichteste Metall und das leichteste feste Element. Wie alle Alkalimetalle ist Lithium hochreaktiv und korrosiv und oxidiert schnell an der Luft zu einer matten schwarzen Farbe. Es kommt niemals frei in der Natur vor und erscheint nur in Verbindungen, die normalerweise ionisch sind.""",
        
        'carbon': """Kohlenstoff ist ein chemisches Element mit dem Symbol C und der Ordnungszahl 6. Es ist ein Nichtmetall und tetravalent - es stellt vier Elektronen zur Verfügung, um kovalente chemische Bindungen zu bilden. Es gehört zur Gruppe 14 des Periodensystems. Kohlenstoff macht nur 0,025% der Erdkruste aus, ist aber für das Leben entscheidend. Drei Isotope kommen natürlich vor, wobei 12C und 13C stabil sind, während 14C ein Radionuklid ist, das mit einer Halbwertszeit von etwa 5730 Jahren zerfällt.""",
        
        'nitrogen': """Stickstoff ist das chemische Element mit dem Symbol N und der Ordnungszahl 7. Es wurde erstmals 1772 vom schottischen Arzt Daniel Rutherford entdeckt und isoliert. Obwohl Carl Wilhelm Scheele und Henry Cavendish ungefähr zur gleichen Zeit unabhängig voneinander das gleiche Experiment durchgeführt hatten, beobachtete Rutherford, dass es ein Bestandteil der Luft war, und veröffentlichte seine Erkenntnisse zuerst. Stickstoff ist unter Standardbedingungen ein farbloses und geruchloses zweiatomiges Gas.""",
    }
}

# Process all translations
total_updated = 0
for lang_code, lang_translations in translations.items():
    count = bulk_update(lang_code, lang_translations)
    print(f"✅ {lang_code.upper()}: Updated {count} elements")
    total_updated += count

print(f"\n{'='*60}")
print(f"TOTAL: Updated {total_updated} element descriptions")
print(f"{'='*60}")
