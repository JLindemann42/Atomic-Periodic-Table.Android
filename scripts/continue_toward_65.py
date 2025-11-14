#!/usr/bin/env python3
"""
Continue comprehensive translations - Push toward 65% overall.
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

# Comprehensive batch across all languages
translations = {
    'fr': {
        'rhenium': """Le rhénium est un élément chimique de symbole Re et de numéro atomique 75. C'est un métal de transition blanc argenté, lourd, de troisième rangée qui résiste à la corrosion et à l'oxydation. Le rhénium ressemble au manganèse et au technétium chimiquement et est principalement obtenu comme sous-produit de l'extraction et du raffinage du molybdène. Le rhénium a la troisième plus haute température de fusion et la deuxième plus haute température d'ébullition de tous les éléments.""",
        
        'rhodium': """Le rhodium est un élément chimique de symbole Rh et de numéro atomique 45. C'est un métal de transition rare, dur, blanc argenté et chimiquement inerte qui est un métal précieux. C'est un métal noble et un membre du groupe du platine. Il n'a qu'un seul isotope naturel, 103Rh. Le rhodium naturel est généralement trouvé sous forme de métal libre, allié avec des métaux similaires et rarement sous forme de composé chimique dans des minéraux comme le bowieite et la rhodplumsite.""",
        
        'roentgenium': """Le roentgenium est un élément chimique de symbole Rg et de numéro atomique 111. C'est un élément synthétique extrêmement radioactif qui peut être créé en laboratoire mais qui ne se trouve pas dans la nature; l'isotope le plus stable connu, le roentgenium-282, a une demi-vie d'environ 2 minutes. Le roentgenium a été synthétisé pour la première fois en 1994 par le GSI Helmholtz Centre for Heavy Ion Research près de Darmstadt, en Allemagne. Il porte le nom du physicien Wilhelm Röntgen, qui a découvert les rayons X.""",
        
        'rubidium': """Le rubidium est un élément chimique de symbole Rb et de numéro atomique 37. Le rubidium est un métal alcalin très mou, de couleur blanc argenté, avec un éclat métallique. Le métal rubidium partage des similitudes avec le potassium et le césium dans son apparence physique, sa douceur et sa conductivité. Le rubidium s'enflamme immédiatement au contact de l'air et réagit violemment avec l'eau, enflammant le gaz hydrogène libéré. Comme tous les autres métaux alcalins, le rubidium réagit violemment avec l'eau et forme de l'hydroxyde de rubidium.""",
        
        'ruthenium': """Le ruthénium est un élément chimique de symbole Ru et de numéro atomique 44. C'est un métal de transition rare appartenant au groupe du platine du tableau périodique. Comme les autres métaux du groupe du platine, le ruthénium est inerte à la plupart des autres produits chimiques. Le chimiste russe d'origine balte Karl Ernst Claus a découvert l'élément en 1844 à l'Université d'État de Kazan, en Russie, et l'a nommé ruthénium en l'honneur de la Russie. Le ruthénium se trouve généralement avec d'autres métaux du groupe du platine dans les minéraux d'oural et en Amérique du Nord et du Sud.""",
    },
    'es': {
        'technetium': """El tecnecio es un elemento químico con el símbolo Tc y número atómico 43. Es el elemento de número atómico más bajo sin isótopos estables. Casi todo el tecnecio es producido sintéticamente, y solo se han encontrado trazas minúsculas en la naturaleza. El tecnecio fue el primer elemento producido predominantemente artificialmente. Su nombre proviene del griego τεχνητός, que significa "artificial". Muchas de las propiedades del tecnecio se predijeron por Dmitri Mendeleev antes de que el elemento fuera descubierto.""",
        
        'palladium': """El paladio es un elemento químico con el símbolo Pd y número atómico 46. Es un metal blanco plateado brillante, raro y brillante descubierto en 1803 por el químico inglés William Hyde Wollaston. Lo nombró en honor al asteroide Pallas, que había sido descubierto dos años antes. El paladio, platino, rodio, rutenio, iridio y osmio forman un grupo de elementos conocidos como los metales del grupo del platino. Tienen propiedades químicas similares, pero el paladio tiene el punto de fusión más bajo y es el menos denso de ellos.""",
        
        'silver': """La plata es un elemento químico con el símbolo Ag (del latín: argentum, derivado del griego protoindoeuropeo h₂erǵ: "brillante" o "blanco") y número atómico 47. Un metal de transición suave, blanco y brillante, exhibe la conductividad eléctrica, conductividad térmica y reflectividad más alta de cualquier metal. El metal se encuentra en la corteza terrestre en forma elemental pura y libre ("plata nativa"), como una aleación con oro y otros metales, y en minerales como argentita y clorargirita.""",
        
        'cadmium': """El cadmio es un elemento químico con el símbolo Cd y número atómico 48. Este metal suave, blanco azulado es químicamente similar a los otros dos metales estables del grupo 12, zinc y mercurio. Como el zinc, prefiere el estado de oxidación +2 en la mayoría de sus compuestos, y como el mercurio, tiene un punto de fusión más bajo que los metales de transición de los grupos 3 a 11. El cadmio y sus congéneres del grupo 12 no siempre se consideran metales de transición.""",
        
        'indium': """El indio es un elemento químico con el símbolo In y número atómico 49. El indio es el metal más suave que no es un metal alcalino. Es un metal de post-transición blanco plateado brillante. El espectro del indio está dominado por una línea índigo, de ahí su nombre. El indio tiene un punto de fusión más alto que el sodio y el galio, pero más bajo que el litio y el estaño. Químicamente, el indio es similar al galio y al talio, y está en gran medida intermedio entre los dos en términos de sus propiedades.""",
    },
    'de': {
        'technetium': """Technetium ist ein chemisches Element mit dem Symbol Tc und der Ordnungszahl 43. Es ist das Element mit der niedrigsten Ordnungszahl ohne stabile Isotope. Fast alles Technetium wird synthetisch hergestellt, und nur winzige Spuren wurden in der Natur gefunden. Technetium war das erste vorwiegend künstlich hergestellte Element. Sein Name leitet sich vom griechischen τεχνητός ab, was "künstlich" bedeutet. Viele der Eigenschaften von Technetium wurden von Dmitri Mendelejew vorhergesagt, bevor das Element entdeckt wurde.""",
        
        'palladium': """Palladium ist ein chemisches Element mit dem Symbol Pd und der Ordnungszahl 46. Es ist ein glänzendes, seltenes, glänzendes silberweißes Metall, das 1803 vom englischen Chemiker William Hyde Wollaston entdeckt wurde. Er benannte es nach dem Asteroiden Pallas, der zwei Jahre zuvor entdeckt worden war. Palladium, Platin, Rhodium, Ruthenium, Iridium und Osmium bilden eine Gruppe von Elementen, die als Platinmetalle bekannt sind. Sie haben ähnliche chemische Eigenschaften, aber Palladium hat den niedrigsten Schmelzpunkt und ist das am wenigsten dichte von ihnen.""",
        
        'silver': """Silber ist ein chemisches Element mit dem Symbol Ag (vom lateinischen: argentum, abgeleitet vom proto-indoeuropäischen h₂erǵ: "glänzend" oder "weiß") und der Ordnungszahl 47. Ein weiches, weißes, glänzendes Übergangsmetall, es zeigt die höchste elektrische Leitfähigkeit, Wärmeleitfähigkeit und Reflektivität aller Metalle. Das Metall kommt in der Erdkruste in reiner, freier elementarer Form ("natives Silber"), als Legierung mit Gold und anderen Metallen sowie in Mineralien wie Argentit und Chlorargyrit vor.""",
        
        'cadmium': """Cadmium ist ein chemisches Element mit dem Symbol Cd und der Ordnungszahl 48. Dieses weiche, blau-weiße Metall ist chemisch ähnlich den beiden anderen stabilen Metallen der Gruppe 12, Zink und Quecksilber. Wie Zink bevorzugt es den Oxidationszustand +2 in den meisten seiner Verbindungen, und wie Quecksilber hat es einen niedrigeren Schmelzpunkt als die Übergangsmetalle der Gruppen 3 bis 11. Cadmium und seine Gruppe-12-Verwandten werden nicht immer als Übergangsmetalle betrachtet.""",
        
        'indium': """Indium ist ein chemisches Element mit dem Symbol In und der Ordnungszahl 49. Indium ist das weichste Metall, das kein Alkalimetall ist. Es ist ein glänzendes silberweißes Post-Übergangsmetall. Das Spektrum von Indium wird von einer Indigolinie dominiert, daher sein Name. Indium hat einen höheren Schmelzpunkt als Natrium und Gallium, aber niedriger als Lithium und Zinn. Chemisch ist Indium Gallium und Thallium ähnlich und liegt weitgehend zwischen beiden in Bezug auf seine Eigenschaften.""",
    },
    'it': {
        'strontium': """Lo stronzio è l'elemento chimico con simbolo Sr e numero atomico 38. Un metallo alcalino-terroso, lo stronzio è un metallo morbido bianco argenteo giallastro che è altamente reattivo chimicamente. Il metallo forma uno strato di ossido scuro quando esposto all'aria. Lo stronzio ha proprietà fisiche e chimiche simili ai suoi due vicini verticali nella tavola periodica, calcio e bario. Si trova naturalmente principalmente nei minerali celestina e stronzianite, ed è estratto principalmente dal primo.""",
        
        'yttrium': """L'ittrio è un elemento chimico con simbolo Y e numero atomico 39. È un metallo di transizione bianco argenteo chimicamente simile ai lantanoidi ed è stato storicamente classificato come elemento delle "terre rare". L'ittrio si trova quasi sempre in combinazione con elementi delle terre rare nei minerali delle terre rare, e non si trova mai in natura come elemento libero. Il suo unico isotopo stabile, 89Y, è anche il suo unico isotopo naturale.""",
        
        'zirconium': """Lo zirconio è un elemento chimico con simbolo Zr e numero atomico 40. Il nome zirconio deriva dal nome del minerale zircone, la fonte più importante di zirconio. È un metallo di transizione brillante bianco grigiastro e forte che assomiglia all'afnio e, in misura minore, al titanio. Lo zirconio è utilizzato principalmente come refrattario e opacificante, sebbene piccole quantità siano utilizzate come agente legante per la sua forte resistenza alla corrosione.""",
        
        'niobium': """Il niobio, precedentemente noto come columbio, è un elemento chimico con simbolo Nb e numero atomico 41. È un metallo di transizione cristallino grigio morbido e duttile. Il niobio puro ha una durezza Mohs simile a quella del titanio puro e ha proprietà chimiche simili al tantalio. Il niobio si trova nel minerale niobite (chiamato anche columbite) ed è utilizzato principalmente in leghe di acciaio ad alta resistenza, in particolare in tubazioni.""",
        
        'molybdenum': """Il molibdeno è un elemento chimico con simbolo Mo e numero atomico 42. Il nome deriva dal neolatino molybdenum, dal greco antico Μόλυβδος molybdos, che significa piombo, poiché i suoi minerali erano confusi con minerali di piombo. I minerali di molibdeno sono conosciuti dalla preistoria, ma l'elemento fu scoperto (nel senso di differenziato come nuova entità dai sali minerali di altri metalli) nel 1778 da Carl Wilhelm Scheele.""",
    },
    'pt': {
        'strontium': """O estrôncio é o elemento químico com o símbolo Sr e número atômico 38. Um metal alcalino-terroso, o estrôncio é um metal macio branco prateado amarelado que é altamente reativo quimicamente. O metal forma uma camada de óxido escuro quando exposto ao ar. O estrôncio tem propriedades físicas e químicas semelhantes aos seus dois vizinhos verticais na tabela periódica, cálcio e bário. Ocorre naturalmente principalmente nos minerais celestina e estroncianita, e é extraído principalmente do primeiro.""",
        
        'yttrium': """O ítrio é um elemento químico com o símbolo Y e número atômico 39. É um metal de transição branco prateado quimicamente semelhante aos lantanídeos e foi historicamente classificado como elemento de "terra rara". O ítrio é quase sempre encontrado em combinação com elementos de terras raras em minerais de terras raras, e nunca é encontrado na natureza como elemento livre. Seu único isótopo estável, 89Y, é também seu único isótopo natural.""",
        
        'zirconium': """O zircônio é um elemento químico com o símbolo Zr e número atômico 40. O nome zircônio deriva do nome do mineral zircão, a fonte mais importante de zircônio. É um metal de transição brilhante branco-acinzentado e forte que se assemelha ao háfnio e, em menor grau, ao titânio. O zircônio é usado principalmente como refratário e opacificador, embora pequenas quantidades sejam usadas como agente de liga por sua forte resistência à corrosão.""",
        
        'niobium': """O nióbio, anteriormente conhecido como colúmbio, é um elemento químico com o símbolo Nb e número atômico 41. É um metal de transição cristalino cinza macio e dúctil. O nióbio puro tem uma dureza Mohs semelhante à do titânio puro e tem propriedades químicas semelhantes ao tântalo. O nióbio é encontrado no mineral niobita (também chamado columbita) e é usado principalmente em ligas de aço de alta resistência, particularmente em tubulações.""",
        
        'molybdenum': """O molibdênio é um elemento químico com o símbolo Mo e número atômico 42. O nome vem do neolatim molibdênio, do grego antigo Μόλυβδος molybdos, que significa chumbo, pois seus minerais eram confundidos com minerais de chumbo. Os minerais de molibdênio são conhecidos desde a pré-história, mas o elemento foi descoberto (no sentido de diferenciado como nova entidade dos sais minerais de outros metais) em 1778 por Carl Wilhelm Scheele.""",
    },
    'zh': {
        'aluminium': """铝（美式和加拿大英语为aluminum）是化学元素，符号为Al，原子序数为13。它是硼族中的银白色、软的、非磁性和延展性金属。按质量计算，铝约占地壳的8%，是仅次于氧和硅的第三丰富元素，也是最丰富的金属。铝在下方的地幔中的存在减少。铝的主要矿石是铝土矿。铝金属非常活泼，因此天然标本稀少且仅限于极端还原环境。""",
        
        'silicon': """硅是化学元素，符号为Si，原子序数为14。它是一种硬的、有光泽的灰蓝色类金属。它是元素周期表第14族的成员：碳在上方，锗、锡、铅和鈇在下方。它相对不活泼。由于其高化学电离能、电子亲和力和化合物的形成能量，在其化学中几乎完全是四价的。纯晶体硅太活泼，无法在自然界中存在。硅约占地壳的27.7%。""",
        
        'phosphorus': """磷是化学元素，符号为P，原子序数为15。元素磷以两种主要形式存在，白磷和红磷，但由于其高度反应性，磷从未在地球上作为自由元素被发现。它在地壳中的浓度约为每千克一克。磷于1669年由汉尼格·布兰德在德国汉堡发现。磷是氮族的多价元素。它对所有生物体都是必不可少的。磷是DNA、RNA和ATP的关键成分。""",
        
        'sulfur': """硫是化学元素，符号为S，原子序数为16。它是丰富的、多价的和非金属的。在正常条件下，硫原子形成环状八硫分子，化学式为S8。元素硫在室温下是一种明亮的黄色结晶固体。硫是宇宙中按质量计算第十丰富的元素，也是地球上第五丰富的元素。虽然有时以纯净的天然形式被发现，但地球上的硫通常以硫化物和硫酸盐矿物的形式存在。""",
        
        'chlorine': """氯是化学元素，符号为Cl，原子序数为17。第二轻的卤素，它在元素周期表中出现在氟和溴之间，其性质主要介于两者之间。氯在室温下是一种黄绿色气体。它是一种极其反应性的元素和强氧化剂：在元素中，它具有第三高的电负性，仅次于氧和氟。氯是自然界中最常见的卤素元素。""",
    },
    'hi': {
        'aluminium': """एल्युमीनियम (अमेरिकी और कैनेडियन अंग्रेजी में aluminum) एक रासायनिक तत्व है जिसका प्रतीक Al और परमाणु संख्या 13 है। यह बोरॉन समूह में एक चांदी-सफेद, नरम, गैर-चुंबकीय और लचीली धातु है। द्रव्यमान के अनुसार, एल्युमीनियम पृथ्वी की पपड़ी का लगभग 8% बनाता है, जहां यह (ऑक्सीजन और सिलिकॉन के बाद) तीसरा सबसे प्रचुर तत्व है और सबसे प्रचुर धातु भी है। एल्युमीनियम का मुख्य अयस्क बॉक्साइट है।""",
        
        'silicon': """सिलिकॉन एक रासायनिक तत्व है जिसका प्रतीक Si और परमाणु संख्या 14 है। यह एक कठोर, चमकदार ग्रे-नीला धातुमल है। यह आवर्त सारणी में समूह 14 का सदस्य है: कार्बन ऊपर है और जर्मेनियम, टिन, सीसा और फ्लेरोवियम नीचे हैं। यह अपेक्षाकृत अप्रतिक्रियाशील है। अपनी उच्च रासायनिक आयनीकरण ऊर्जा, इलेक्ट्रॉन आत्मीयताओं और यौगिकों की गठन ऊर्जाओं के कारण, यह अपनी रसायन विज्ञान में लगभग विशेष रूप से चतुर्संयोजक है।""",
        
        'phosphorus': """फास्फोरस एक रासायनिक तत्व है जिसका प्रतीक P और परमाणु संख्या 15 है। तात्विक फास्फोरस दो मुख्य रूपों में मौजूद है, सफेद फास्फोरस और लाल फास्फोरस, लेकिन क्योंकि यह अत्यधिक प्रतिक्रियाशील है, फास्फोरस को पृथ्वी पर कभी भी मुक्त तत्व के रूप में नहीं पाया जाता है। इसकी पृथ्वी की पपड़ी में एकाग्रता प्रति किलोग्राम लगभग एक ग्राम है। फास्फोरस की खोज 1669 में जर्मनी के हैम्बर्ग में हेनिग ब्रांड द्वारा की गई थी।""",
        
        'sulfur': """सल्फर एक रासायनिक तत्व है जिसका प्रतीक S और परमाणु संख्या 16 है। यह प्रचुर, बहुसंयोजक और अधातु है। सामान्य परिस्थितियों में, सल्फर परमाणु सूत्र S8 के साथ चक्रीय अष्टसल्फर अणु बनाते हैं। तात्विक सल्फर कमरे के तापमान पर एक चमकीली पीली क्रिस्टलीय ठोस है। सल्फर द्रव्यमान द्वारा ब्रह्मांड में दसवां सबसे आम तत्व है और पृथ्वी पर पांचवां सबसे आम है। हालांकि कभी-कभी शुद्ध, मूल रूप में पाया जाता है, पृथ्वी पर सल्फर आमतौर पर सल्फाइड और सल्फेट खनिजों के रूप में होता है।""",
        
        'chlorine': """क्लोरीन एक रासायनिक तत्व है जिसका प्रतीक Cl और परमाणु संख्या 17 है। दूसरा सबसे हल्का हैलोजन, यह आवर्त सारणी में फ्लोरीन और ब्रोमीन के बीच प्रकट होता है और इसके गुण मुख्य रूप से उनके बीच मध्यवर्ती हैं। क्लोरीन कमरे के तापमान पर एक पीली-हरी गैस है। यह एक अत्यधिक प्रतिक्रियाशील तत्व और एक मजबूत ऑक्सीकारक एजेंट है: तत्वों में, इसमें तीसरी सबसे अधिक विद्युतऋणात्मकता है, केवल ऑक्सीजन और फ्लोरीन के पीछे।""",
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
