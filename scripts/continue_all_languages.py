#!/usr/bin/env python3
"""
Continue translations - Push toward 60% overall, start Chinese & Hindi.
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

# Comprehensive batch across all languages including Chinese and Hindi
translations = {
    'fr': {
        'phosphorus': """Le phosphore est un élément chimique de symbole P et de numéro atomique 15. Le phosphore élémentaire existe sous deux formes principales, le phosphore blanc et le phosphore rouge, mais parce qu'il est très réactif, le phosphore n'est jamais trouvé comme élément libre sur Terre. Il a une concentration dans la croûte terrestre d'environ un gramme par kilogramme. Le phosphore a été découvert en 1669 par Hennig Brand à Hambourg, en Allemagne. Le phosphore est un élément multivalent de la famille de l'azote. Il est essentiel à tous les organismes vivants.""",
        
        'platinum': """Le platine est un élément chimique de symbole Pt et de numéro atomique 78. C'est un métal de transition dense, malléable, ductile, hautement non réactif, précieux, blanc argenté. Son nom provient de l'espagnol platino, diminutif de plata "argent". Le platine est un membre du groupe du platine et du groupe 10 du tableau périodique des éléments. Il a six isotopes naturels. C'est l'un des éléments les plus rares de la croûte terrestre, avec une abondance moyenne d'environ 5 μg/kg.""",
        
        'plutonium': """Le plutonium est un élément chimique de symbole Pu et de numéro atomique 94. C'est un élément actinide radioactif, l'un des éléments transuraniens. Le plutonium a l'apparence d'un métal blanc argenté qui devient jaune ternissant lorsqu'il est exposé à l'air, et noircit finalement. C'est un métal radioactif qui peut s'accumuler dans les os, ce qui rend la manipulation du plutonium dangereuse. Le plutonium est principalement synthétisé dans les réacteurs nucléaires à partir de l'uranium-238.""",
        
        'polonium': """Le polonium est un élément chimique de symbole Po et de numéro atomique 84. Un métal post-transition argenté rare et hautement radioactif, le polonium est chimiquement similaire au sélénium et au tellure, bien qu'il ait des propriétés plus métalliques que ceux-ci. C'est le seul élément avec une structure cristalline cubique simple à température standard. Le polonium a été découvert en 1898 par Marie et Pierre Curie, quand il a été extrait du minerai d'uranium pitchblende, et nommé d'après la Pologne, le pays natal de Marie.""",
        
        'potassium': """Le potassium est un élément chimique de symbole K (du néo-latin kalium) et de numéro atomique 19. Il a été isolé pour la première fois à partir de la potasse, les cendres des plantes, d'où il tire son nom. Dans le tableau périodique, le potassium est l'un des métaux alcalins. Tous les métaux alcalins ont un seul électron de valence dans la couche électronique externe, qui est facilement retiré pour créer un ion de charge positive - un cation, qui se combine avec des anions pour former des sels. Le potassium dans la nature n'apparaît que dans des sels ioniques.""",
    },
    'es': {
        'arsenic': """El arsénico es un elemento químico con el símbolo As y número atómico 33. El arsénico ocurre en muchos minerales, generalmente en combinación con azufre y metales, pero también como un cristal elemental puro. El arsénico es un metaloide. Tiene varias apariencias alotrópicas, pero solo la forma gris, que tiene una estructura cristalina metálica, es importante industrialmente. El mineral principal de arsénico es la arsenopirita. El arsénico se usa en diodos, láseres y semiconductores.""",
        
        'selenium': """El selenio es un elemento químico con el símbolo Se y número atómico 34. Es un no metal (más raramente considerado un metaloide) con propiedades que son intermedias entre los elementos arriba y debajo en la tabla periódica, azufre y telurio, y también tiene similitudes con el arsénico. Rara vez ocurre en su forma elemental o como compuestos de selenio puro en la corteza terrestre. El selenio (del griego σελήνη selene, que significa "Luna") fue descubierto en 1817 por Jöns Jacob Berzelius.""",
        
        'bromine': """El bromo es un elemento químico con el símbolo Br y número atómico 35. Es el tercer halógeno más ligero y es un líquido rojo humeante a temperatura ambiente que se evapora fácilmente para formar un gas amarillo rojizo de color similar. Sus propiedades son intermedias entre las del cloro y el yodo. Aislado independientemente por dos químicos, Carl Jacob Löwig (en 1825) y Antoine Jérôme Balard (en 1826), su nombre se derivó del griego antiguo bromos ("hedor"), refiriéndose a su olor acre y desagradable.""",
        
        'krypton': """El criptón (del griego antiguo: κρυπτός, romanizado: kryptos 'el oculto') es un elemento químico con el símbolo Kr y número atómico 36. Es un gas incoloro, inodoro, insípido, no tóxico, un gas noble. El criptón se encuentra en la atmósfera de la Tierra en trazas; el aire contiene 1 ppm. El criptón fue descubierto en Gran Bretaña en 1898 por William Ramsay y Morris Travers en residuos dejados de la evaporación de componentes de aire líquido.""",
        
        'rubidium': """El rubidio es el elemento químico con el símbolo Rb y número atómico 37. El rubidio es un metal muy suave, de color blanco plateado en el grupo de metales alcalinos. El metal rubidio comparte similitudes con el potasio y el cesio en su apariencia física, suavidad y conductividad. El rubidio se inflama inmediatamente al contacto con el aire y reacciona violentamente con el agua, encendiendo el gas hidrógeno liberado. Como todos los otros metales alcalinos, el rubidio reacciona violentamente con el agua y forma hidróxido de rubidio.""",
    },
    'de': {
        'arsenic': """Arsen ist ein chemisches Element mit dem Symbol As und der Ordnungszahl 33. Arsen kommt in vielen Mineralien vor, normalerweise in Verbindung mit Schwefel und Metallen, aber auch als reiner elementarer Kristall. Arsen ist ein Halbmetall. Es hat verschiedene allotrope Erscheinungen, aber nur die graue Form, die eine metallische Kristallstruktur hat, ist industriell wichtig. Das Haupterzmineral von Arsen ist Arsenopyrit. Arsen wird in Dioden, Lasern und Halbleitern verwendet.""",
        
        'selenium': """Selen ist ein chemisches Element mit dem Symbol Se und der Ordnungszahl 34. Es ist ein Nichtmetall (seltener als Halbmetall betrachtet) mit Eigenschaften, die zwischen den Elementen darüber und darunter im Periodensystem liegen, Schwefel und Tellur, und hat auch Ähnlichkeiten mit Arsen. Es kommt selten in seiner elementaren Form oder als reine Selenverbindungen in der Erdkruste vor. Selen (vom griechischen σελήνη selene, was "Mond" bedeutet) wurde 1817 von Jöns Jacob Berzelius entdeckt.""",
        
        'bromine': """Brom ist ein chemisches Element mit dem Symbol Br und der Ordnungszahl 35. Es ist das drittleichteste Halogen und ist eine rauchende rote Flüssigkeit bei Raumtemperatur, die leicht verdampft, um ein gelb-rotes Gas ähnlicher Farbe zu bilden. Seine Eigenschaften liegen zwischen denen von Chlor und Iod. Unabhängig von zwei Chemikern isoliert, Carl Jacob Löwig (1825) und Antoine Jérôme Balard (1826), wurde sein Name vom altgriechischen bromos ("Gestank") abgeleitet, was sich auf seinen scharfen und unangenehmen Geruch bezieht.""",
        
        'krypton': """Krypton (vom altgriechischen: κρυπτός, romanisiert: kryptos 'der Verborgene') ist ein chemisches Element mit dem Symbol Kr und der Ordnungszahl 36. Es ist ein farbloses, geruchloses, geschmackloses, ungiftiges Edelgas. Krypton kommt in der Erdatmosphäre in Spuren vor; Luft enthält 1 ppm. Krypton wurde 1898 in Großbritannien von William Ramsay und Morris Travers in Rückständen entdeckt, die nach der Verdampfung von flüssigen Luftbestandteilen übrig blieben.""",
        
        'rubidium': """Rubidium ist das chemische Element mit dem Symbol Rb und der Ordnungszahl 37. Rubidium ist ein sehr weiches, silbrig-weißes Metall in der Alkalimetallgruppe. Rubidiummetall teilt Ähnlichkeiten mit Kalium und Cäsium in seinem physischen Aussehen, seiner Weichheit und Leitfähigkeit. Rubidium entzündet sich sofort bei Kontakt mit Luft und reagiert heftig mit Wasser, wobei das freigesetzte Wasserstoffgas entzündet wird. Wie alle anderen Alkalimetalle reagiert Rubidium heftig mit Wasser und bildet Rubidiumhydroxid.""",
    },
    'it': {
        'nickel': """Il nichel è un elemento chimico con simbolo Ni e numero atomico 28. È un metallo bianco argenteo brillante con una leggera sfumatura dorata. Il nichel appartiene ai metalli di transizione ed è duro e duttile. Il nichel puro, in polvere per massimizzare l'area superficiale reattiva, mostra una significativa attività chimica, ma i pezzi più grandi sono lenti a reagire con l'aria in condizioni standard perché uno strato di ossido si forma sulla superficie e previene ulteriore corrosione (passivazione).""",
        
        'copper': """Il rame è un elemento chimico con simbolo Cu (dal latino: cuprum) e numero atomico 29. È un metallo morbido, malleabile e duttile con conduttività termica ed elettrica molto alta. Una superficie di rame appena esposta ha un colore arancione rossastro. Il rame è usato come conduttore di calore ed elettricità, come materiale da costruzione e come costituente di varie leghe metalliche, come l'argento sterling usato in gioielleria, il cupronickel usato per fare accessori marini e monete, e il costantana usato in estensimetri e termocoppie per la misurazione della temperatura.""",
        
        'zinc': """Lo zinco è un elemento chimico con simbolo Zn e numero atomico 30. Lo zinco è un metallo leggermente fragile a temperatura ambiente e ha un aspetto bianco-grigiastro argenteo quando l'ossidazione è rimossa. È il primo elemento del gruppo 12 della tavola periodica. In alcuni aspetti, lo zinco è chimicamente simile al magnesio: entrambi gli elementi mostrano solo uno stato di ossidazione normale (+2), e gli ioni Zn2+ e Mg2+ sono di dimensioni simili. Lo zinco è il 24° elemento più abbondante nella crosta terrestre e ha cinque isotopi stabili.""",
        
        'gallium': """Il gallio è un elemento chimico con simbolo Ga e numero atomico 31. Il gallio elementare è un metallo morbido, argenteo a temperatura e pressione standard; tuttavia, è fragile a freddo, e si scioglie a una temperatura leggermente superiore alla temperatura ambiente a 29,76 °C (85,57 °F), e quindi si scioglierà nella mano di una persona. Il punto di fusione del gallio è usato come punto di riferimento della temperatura. Il gallio si trova naturalmente in tracce nella bauxite e nei minerali di zinco.""",
        
        'germanium': """Il germanio è un elemento chimico con simbolo Ge e numero atomico 32. È un semimetallo grigio-biancastro lucido e duro nel gruppo del carbonio, chimicamente simile ai suoi vicini di gruppo stagno e silicio. Il germanio elementare puro è un semiconduttore con un aspetto simile al silicio elementare. Come il silicio, il germanio reagisce naturalmente e forma complessi con l'ossigeno in natura. Poiché si combina raramente con altri elementi come un minerale, il germanio è stato scoperto relativamente tardi nella storia della chimica.""",
    },
    'pt': {
        'nickel': """O níquel é um elemento químico com o símbolo Ni e número atômico 28. É um metal brilhante branco prateado com uma leve tonalidade dourada. O níquel pertence aos metais de transição e é duro e dúctil. O níquel puro, em pó para maximizar a área de superfície reativa, mostra atividade química significativa, mas peças maiores são lentas para reagir com o ar em condições padrão porque uma camada de óxido se forma na superfície e previne maior corrosão (passivação).""",
        
        'copper': """O cobre é um elemento químico com o símbolo Cu (do latim: cuprum) e número atômico 29. É um metal macio, maleável e dúctil com condutividade térmica e elétrica muito alta. Uma superfície de cobre recém-exposta tem uma cor laranja avermelhada. O cobre é usado como condutor de calor e eletricidade, como material de construção e como constituinte de várias ligas metálicas, como a prata de lei usada em joalheria, o cuproníquel usado para fazer ferragens marítimas e moedas, e o constantan usado em extensômetros e termopares para medição de temperatura.""",
        
        'zinc': """O zinco é um elemento químico com o símbolo Zn e número atômico 30. O zinco é um metal ligeiramente frágil à temperatura ambiente e tem uma aparência branca-acinzentada prateada quando a oxidação é removida. É o primeiro elemento do grupo 12 da tabela periódica. Em alguns aspectos, o zinco é quimicamente semelhante ao magnésio: ambos os elementos exibem apenas um estado de oxidação normal (+2), e os íons Zn2+ e Mg2+ são de tamanho semelhante. O zinco é o 24º elemento mais abundante na crosta terrestre e tem cinco isótopos estáveis.""",
        
        'gallium': """O gálio é um elemento químico com o símbolo Ga e número atômico 31. O gálio elementar é um metal macio, prateado à temperatura e pressão padrão; no entanto, é quebradiço no frio, e derrete a uma temperatura ligeiramente acima da temperatura ambiente a 29,76 °C (85,57 °F), e portanto derreterá na mão de uma pessoa. O ponto de fusão do gálio é usado como ponto de referência de temperatura. O gálio é encontrado naturalmente em traços na bauxita e minérios de zinco.""",
        
        'germanium': """O germânio é um elemento químico com o símbolo Ge e número atômico 32. É um semimetal duro, brilhante, cinza-esbranquiçado no grupo do carbono, quimicamente semelhante aos seus vizinhos de grupo estanho e silício. O germânio elementar puro é um semicondutor com uma aparência semelhante ao silício elementar. Como o silício, o germânio reage naturalmente e forma complexos com oxigênio na natureza. Porque raramente se combina com outros elementos como um mineral, o germânio foi descoberto relativamente tarde na história da química.""",
    },
    'zh': {
        # Start Chinese translations
        'hydrogen': """氢是化学元素，符号为H，原子序数为1。标准原子量为1.008，氢是元素周期表中最轻的元素。氢是宇宙中最丰富的化学物质，约占所有重子质量的75%。非残余恒星主要由等离子态的氢组成。最常见的氢同位素（符号1H）由一个质子、一个电子和零个中子组成。在地球上，氢以原子形式极为罕见，而分子形式H2或双氢主要是从碳氢化合物工业生产或通过水的电解产生。""",
        
        'helium': """氦（源自希腊语：ἥλιος，罗马化：Helios，意为"太阳"）是化学元素，符号为He，原子序数为2。它是一种无色、无味、无毒、惰性的单原子气体，是元素周期表中稀有气体族的第一个。它的沸点是所有元素中最低的。氦是可观测宇宙中第二轻和第二丰富的元素（仅次于氢），约占元素总质量的24%。氦在地球上稀少，因为大多数已经逃逸到太空中。""",
        
        'lithium': """锂是化学元素，符号为Li，原子序数为3。它是一种软的银白色碱金属。在标准条件下，它是最轻的金属和最轻的固体元素。像所有碱金属一样，锂具有高度反应性和腐蚀性，在空气中迅速氧化成暗黑色。它从不以自由形式存在于自然界中，只以化合物形式出现，通常是离子化合物。锂存在于许多硅酸盐伟晶岩化合物中，是恒星核合成的产物。""",
        
        'carbon': """碳是化学元素，符号为C，原子序数为6。它是非金属和四价的——提供四个电子来形成共价化学键。它属于元素周期表的第14族。碳仅占地壳的0.025%，但对生命至关重要。三种同位素自然存在，其中12C和13C是稳定的，而14C是一种放射性核素，半衰期约为5730年。碳是自古以来已知的少数元素之一，以木炭、煤烟和石墨的形式被使用。""",
        
        'nitrogen': """氮是化学元素，符号为N，原子序数为7。它于1772年首次被苏格兰医生丹尼尔·卢瑟福发现和分离。尽管卡尔·威廉·舍勒和亨利·卡文迪什大约在同一时间独立进行了相同的实验，但卢瑟福观察到它是空气的组成部分，并首先发表了他的发现。氮在标准条件下是一种无色无味的双原子气体，约占地球大气层的78%，使其成为大气中最常见的元素。""",
    },
    'hi': {
        # Start Hindi translations
        'hydrogen': """हाइड्रोजन रासायनिक तत्व है जिसका प्रतीक H और परमाणु संख्या 1 है। मानक परमाणु भार 1.008 के साथ, हाइड्रोजन आवर्त सारणी में सबसे हल्का तत्व है। हाइड्रोजन ब्रह्मांड में सबसे प्रचुर रासायनिक पदार्थ है, जो सभी बैरियोनिक द्रव्यमान का लगभग 75% हिस्सा बनाता है। गैर-अवशेष तारे मुख्य रूप से प्लाज्मा अवस्था में हाइड्रोजन से बने होते हैं। सबसे आम हाइड्रोजन आइसोटोप (प्रतीक 1H) में एक प्रोटॉन, एक इलेक्ट्रॉन और शून्य न्यूट्रॉन होते हैं।""",
        
        'helium': """हीलियम (ग्रीक से: ἥλιος, रोमनीकृत: Helios, शाब्दिक 'सूर्य') एक रासायनिक तत्व है जिसका प्रतीक He और परमाणु संख्या 2 है। यह एक रंगहीन, गंधहीन, स्वादहीन, गैर-विषैला, निष्क्रिय, एकपरमाणुक गैस है, जो आवर्त सारणी में उत्कृष्ट गैसों के समूह में पहला है। इसका क्वथनांक सभी तत्वों में सबसे कम है। हीलियम प्रेक्षणीय ब्रह्मांड में दूसरा सबसे हल्का और दूसरा सबसे प्रचुर तत्व है (हाइड्रोजन के बाद), जो कुल तात्विक द्रव्यमान का लगभग 24% हिस्सा बनाता है।""",
        
        'lithium': """लिथियम एक रासायनिक तत्व है जिसका प्रतीक Li और परमाणु संख्या 3 है। यह एक नरम, चांदी-सफेद क्षार धातु है। मानक परिस्थितियों में, यह सबसे हल्की धातु और सबसे हल्का ठोस तत्व है। सभी क्षार धातुओं की तरह, लिथियम अत्यधिक प्रतिक्रियाशील और संक्षारक है, और हवा में तेजी से ऑक्सीकृत होकर एक सुस्त काले रंग में बदल जाता है। यह कभी भी प्रकृति में मुक्त रूप में नहीं पाया जाता है, और केवल यौगिकों में प्रकट होता है, जो आम तौर पर आयनिक होते हैं।""",
        
        'carbon': """कार्बन एक रासायनिक तत्व है जिसका प्रतीक C और परमाणु संख्या 6 है। यह गैर-धातु और चतुर्संयोजक है - सहसंयोजक रासायनिक बंधन बनाने के लिए चार इलेक्ट्रॉन उपलब्ध कराता है। यह आवर्त सारणी के समूह 14 से संबंधित है। कार्बन पृथ्वी की पपड़ी का केवल 0.025% बनाता है, लेकिन जीवन के लिए महत्वपूर्ण है। तीन आइसोटोप प्राकृतिक रूप से होते हैं, 12C और 13C स्थिर हैं, जबकि 14C एक रेडियोन्यूक्लाइड है, जो लगभग 5730 वर्षों की अर्ध-आयु के साथ क्षय होता है।""",
        
        'nitrogen': """नाइट्रोजन रासायनिक तत्व है जिसका प्रतीक N और परमाणु संख्या 7 है। इसे पहली बार 1772 में स्कॉटिश चिकित्सक डैनियल रदरफोर्ड द्वारा खोजा और अलग किया गया था। हालांकि कार्ल विल्हेम शीले और हेनरी कैवेंडिश ने लगभग उसी समय स्वतंत्र रूप से वही प्रयोग किया था, रदरफोर्ड ने देखा कि यह हवा का एक घटक था और पहले अपने निष्कर्ष प्रकाशित किए। नाइट्रोजन मानक परिस्थितियों में एक रंगहीन और गंधहीन द्विपरमाणुक गैस है और पृथ्वी के वायुमंडल का लगभग 78% हिस्सा बनाती है।""",
    },
}

# Process all translations
total_updated = 0
for lang_code, lang_translations in translations.items():
    count = bulk_update(lang_code, lang_translations)
    lang_names = {
        'fr': 'French', 'es': 'Spanish', 'de': 'German', 
        'it': 'Italian', 'pt': 'Portuguese', 'zh': 'Chinese', 'hi': 'Hindi'
    }
    print(f"✅ {lang_names.get(lang_code, lang_code)}: Updated {count} elements")
    total_updated += count

print(f"\n{'='*60}")
print(f"TOTAL: Updated {total_updated} element descriptions")
print(f"{'='*60}")
