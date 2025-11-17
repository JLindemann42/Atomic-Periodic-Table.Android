#!/usr/bin/env python3
"""
Push toward 60%+ overall completion.
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

# Continue with more translations
translations = {
    'fr': {
        'praseodymium': """Le praséodyme est un élément chimique de symbole Pr et de numéro atomique 59. C'est le troisième membre de la série des lanthanides et est considéré comme l'un des métaux de terres rares. C'est un métal ductile blanc argenté mou, apprécié pour ses propriétés magnétiques, électriques, chimiques et optiques. Il est trop réactif pour être trouvé sous forme libre dans la nature. Le praséodyme réagit toujours rapidement avec l'oxygène atmosphérique, formant une couche d'oxyde verte qui s'écaille.""",
        
        'promethium': """Le prométhium est un élément chimique de symbole Pm et de numéro atomique 61. Tous ses isotopes sont radioactifs; il est extrêmement rare, avec seulement environ 500-600 grammes naturellement présents dans la croûte terrestre à tout moment. Le prométhium est l'un des deux seuls éléments radioactifs qui sont suivis dans le tableau périodique par des éléments avec des formes stables. Le prométhium a été découvert en 1945 à l'Oak Ridge National Laboratory par séparation et analyse des produits de fission de l'uranium.""",
        
        'protactinium': """Le protactinium est un élément chimique de symbole Pa et de numéro atomique 91. C'est un élément actinide dense, blanc argenté, radioactif qui réagit facilement avec l'oxygène, la vapeur d'eau et les acides inorganiques. Le protactinium forme divers composés chimiques où il est généralement présent dans l'état d'oxydation +5, mais il peut aussi assumer l'état +4 dans certains composés. En raison de sa rareté, de sa radioactivité élevée et de sa toxicité élevée, il n'y a actuellement aucune utilisation du protactinium en dehors de la recherche scientifique.""",
        
        'radium': """Le radium est un élément chimique de symbole Ra et de numéro atomique 88. C'est le sixième élément du groupe 2 du tableau périodique, également connu sous le nom de métaux alcalino-terreux. Le radium pur est presque incolore, mais il s'oxyde facilement lors de son exposition à l'air, devenant noir. Le radium est un élément hautement radioactif et est le métal alcalino-terreux le plus lourd. Son isotope le plus stable, le radium-226, a une demi-vie de 1600 ans et se désintègre en gaz radon.""",
        
        'radon': """Le radon est un élément chimique de symbole Rn et de numéro atomique 86. C'est un gaz noble radioactif, incolore, inodore et insipide. Le radon se produit naturellement comme produit de désintégration du radium. Son isotope le plus stable, le radon-222, a une demi-vie de 3,8 jours. Le radon est l'un des agents cancérigènes les plus lourds et dangereux, et est considéré comme une menace pour la santé en raison de ses niveaux élevés dans de nombreuses maisons.""",
    },
    'es': {
        'strontium': """El estroncio es el elemento químico con el símbolo Sr y número atómico 38. Un metal alcalinotérreo, el estroncio es un metal blanco plateado amarillento suave que es altamente reactivo químicamente. El metal forma una capa de óxido oscuro cuando se expone al aire. El estroncio tiene propiedades físicas y químicas similares a las de sus dos vecinos verticales en la tabla periódica, calcio y bario. Ocurre naturalmente principalmente en los minerales celestina y estroncianita, y se extrae principalmente del primero.""",
        
        'yttrium': """El itrio es un elemento químico con el símbolo Y y número atómico 39. Es un metal de transición blanco plateado químicamente similar a los lantánidos y ha sido históricamente clasificado como un elemento de "tierra rara". El itrio casi siempre se encuentra en combinación con elementos de tierras raras en minerales de tierras raras, y nunca se encuentra en la naturaleza como elemento libre. Su único isótopo estable, 89Y, también es su único isótopo que ocurre naturalmente.""",
        
        'zirconium': """El circonio es un elemento químico con el símbolo Zr y número atómico 40. El nombre circonio se toma del nombre del mineral circón, la fuente más importante de circonio. Es un metal de transición blanco grisáceo brillante y fuerte que se asemeja al hafnio y, en menor medida, al titanio. El circonio se utiliza principalmente como refractario y opacificador, aunque se utilizan pequeñas cantidades como agente de aleación por su fuerte resistencia a la corrosión.""",
        
        'niobium': """El niobio, anteriormente conocido como columbio, es un elemento químico con el símbolo Nb y número atómico 41. Es un metal de transición gris cristalino blando y dúctil. El niobio puro tiene una dureza Mohs similar a la del titanio puro, y tiene propiedades químicas similares al tantalio. El niobio se encuentra en el mineral niobita (también llamado columbita) y se utiliza principalmente en aleaciones de acero de alta resistencia, en particular en tuberías.""",
        
        'molybdenum': """El molibdeno es un elemento químico con el símbolo Mo y número atómico 42. El nombre proviene del neolatín molibdeno, del griego antiguo Μόλυβδος molybdos, que significa plomo, ya que sus minerales se confundieron con minerales de plomo. Los minerales de molibdeno se conocen desde la prehistoria, pero el elemento fue descubierto (en el sentido de diferenciado como una nueva entidad de las sales minerales de otros metales) en 1778 por Carl Wilhelm Scheele.""",
    },
    'de': {
        'strontium': """Strontium ist das chemische Element mit dem Symbol Sr und der Ordnungszahl 38. Ein Erdalkalimetall, Strontium ist ein weiches, silbrig-weißes gelbliches Metall, das chemisch hochreaktiv ist. Das Metall bildet eine dunkle Oxidschicht, wenn es der Luft ausgesetzt wird. Strontium hat physikalische und chemische Eigenschaften ähnlich seinen zwei vertikalen Nachbarn im Periodensystem, Calcium und Barium. Es kommt natürlich hauptsächlich in den Mineralien Coelestin und Strontianit vor und wird hauptsächlich aus ersterem gewonnen.""",
        
        'yttrium': """Yttrium ist ein chemisches Element mit dem Symbol Y und der Ordnungszahl 39. Es ist ein silbrig-weißes Übergangsmetall, das chemisch den Lanthaniden ähnelt und historisch als "Seltenerdelement" klassifiziert wurde. Yttrium findet sich fast immer in Kombination mit Seltenerdelementen in Seltenerdemineralien und kommt niemals in der Natur als freies Element vor. Sein einziges stabiles Isotop, 89Y, ist auch sein einziges natürlich vorkommendes Isotop.""",
        
        'zirconium': """Zirkonium ist ein chemisches Element mit dem Symbol Zr und der Ordnungszahl 40. Der Name Zirkonium leitet sich vom Namen des Minerals Zirkon ab, der wichtigsten Quelle für Zirkonium. Es ist ein glänzendes grau-weißes, starkes Übergangsmetall, das Hafnium und in geringerem Maße Titan ähnelt. Zirkonium wird hauptsächlich als Feuerfestmaterial und Trübungsmittel verwendet, obwohl kleine Mengen als Legierungsmittel wegen seiner starken Korrosionsbeständigkeit verwendet werden.""",
        
        'niobium': """Niob, früher als Columbium bekannt, ist ein chemisches Element mit dem Symbol Nb und der Ordnungszahl 41. Es ist ein weiches, duktiles, grau kristallines Übergangsmetall. Reines Niob hat eine Mohs-Härte ähnlich der von reinem Titan und hat chemische Eigenschaften ähnlich dem Tantal. Niob kommt im Mineral Niobit (auch Columbit genannt) vor und wird hauptsächlich in hochfesten Stahllegierungen verwendet, insbesondere in Rohrleitungen.""",
        
        'molybdenum': """Molybdän ist ein chemisches Element mit dem Symbol Mo und der Ordnungszahl 42. Der Name stammt vom neulateinischen Molybdän, vom altgriechischen Μόλυβδος molybdos, was Blei bedeutet, da seine Erze mit Bleierzen verwechselt wurden. Molybdänmineralien sind seit der Vorgeschichte bekannt, aber das Element wurde 1778 von Carl Wilhelm Scheele entdeckt (im Sinne von als neue Entität von den Mineralsalzen anderer Metalle unterschieden).""",
    },
    'it': {
        'arsenic': """L'arsenico è un elemento chimico con simbolo As e numero atomico 33. L'arsenico si trova in molti minerali, di solito in combinazione con zolfo e metalli, ma anche come cristallo elementare puro. L'arsenico è un metalloide. Ha varie apparenze allotropiche, ma solo la forma grigia, che ha una struttura cristallina metallica, è importante industrialmente. Il principale minerale di arsenico è l'arsenopirite. L'arsenico è usato in diodi, laser e semiconduttori.""",
        
        'selenium': """Il selenio è un elemento chimico con simbolo Se e numero atomico 34. È un non metallo (più raramente considerato un metalloide) con proprietà che sono intermedie tra gli elementi sopra e sotto nella tavola periodica, zolfo e tellurio, e ha anche somiglianze con l'arsenico. Raramente si presenta nella sua forma elementare o come composti di selenio puro nella crosta terrestre. Il selenio (dal greco σελήνη selene, che significa "Luna") fu scoperto nel 1817 da Jöns Jacob Berzelius.""",
        
        'bromine': """Il bromo è un elemento chimico con simbolo Br e numero atomico 35. È il terzo alogeno più leggero ed è un liquido rosso fumante a temperatura ambiente che evapora facilmente per formare un gas giallo rossastro di colore simile. Le sue proprietà sono intermedie tra quelle del cloro e dello iodio. Isolato indipendentemente da due chimici, Carl Jacob Löwig (nel 1825) e Antoine Jérôme Balard (nel 1826), il suo nome derivò dal greco antico bromos ("puzzo"), riferendosi al suo odore acre e sgradevole.""",
        
        'krypton': """Il kripton (dal greco antico: κρυπτός, romanizzato: kryptos 'il nascosto') è un elemento chimico con simbolo Kr e numero atomico 36. È un gas incolore, inodore, insapore, non tossico, un gas nobile. Il kripton si trova nell'atmosfera terrestre in tracce; l'aria contiene 1 ppm. Il kripton fu scoperto in Gran Bretagna nel 1898 da William Ramsay e Morris Travers in residui lasciati dall'evaporazione di componenti d'aria liquida.""",
        
        'rubidium': """Il rubidio è l'elemento chimico con simbolo Rb e numero atomico 37. Il rubidio è un metallo molto morbido, bianco argenteo nel gruppo dei metalli alcalini. Il metallo rubidio condivide somiglianze con il potassio e il cesio nella sua apparenza fisica, morbidezza e conduttività. Il rubidio si infiamma immediatamente al contatto con l'aria e reagisce violentemente con l'acqua, accendendo il gas idrogeno rilasciato. Come tutti gli altri metalli alcalini, il rubidio reagisce violentemente con l'acqua e forma idrossido di rubidio.""",
    },
    'pt': {
        'arsenic': """O arsênio é um elemento químico com o símbolo As e número atômico 33. O arsênio ocorre em muitos minerais, geralmente em combinação com enxofre e metais, mas também como um cristal elementar puro. O arsênio é um metaloide. Tem várias aparências alotrópicas, mas apenas a forma cinza, que tem uma estrutura cristalina metálica, é importante industrialmente. O principal mineral de arsênio é a arsenopirita. O arsênio é usado em diodos, lasers e semicondutores.""",
        
        'selenium': """O selênio é um elemento químico com o símbolo Se e número atômico 34. É um não-metal (mais raramente considerado um metaloide) com propriedades que são intermediárias entre os elementos acima e abaixo na tabela periódica, enxofre e telúrio, e também tem semelhanças com o arsênio. Raramente ocorre em sua forma elementar ou como compostos de selênio puro na crosta terrestre. O selênio (do grego σελήνη selene, que significa "Lua") foi descoberto em 1817 por Jöns Jacob Berzelius.""",
        
        'bromine': """O bromo é um elemento químico com o símbolo Br e número atômico 35. É o terceiro halogênio mais leve e é um líquido vermelho fumegante à temperatura ambiente que evapora facilmente para formar um gás amarelo avermelhado de cor semelhante. Suas propriedades são intermediárias entre as do cloro e do iodo. Isolado independentemente por dois químicos, Carl Jacob Löwig (em 1825) e Antoine Jérôme Balard (em 1826), seu nome derivou do grego antigo bromos ("fedor"), referindo-se ao seu odor acre e desagradável.""",
        
        'krypton': """O criptônio (do grego antigo: κρυπτός, romanizado: kryptos 'o oculto') é um elemento químico com o símbolo Kr e número atômico 36. É um gás incolor, inodoro, insípido, não tóxico, um gás nobre. O criptônio é encontrado na atmosfera terrestre em traços; o ar contém 1 ppm. O criptônio foi descoberto na Grã-Bretanha em 1898 por William Ramsay e Morris Travers em resíduos deixados da evaporação de componentes de ar líquido.""",
        
        'rubidium': """O rubídio é o elemento químico com o símbolo Rb e número atômico 37. O rubídio é um metal muito macio, branco prateado no grupo dos metais alcalinos. O metal rubídio compartilha semelhanças com o potássio e o césio em sua aparência física, maciez e condutividade. O rubídio inflama imediatamente ao contato com o ar e reage violentamente com a água, acendendo o gás hidrogênio liberado. Como todos os outros metais alcalinos, o rubídio reage violentamente com a água e forma hidróxido de rubídio.""",
    },
    'zh': {
        'oxygen': """氧是化学元素，符号为O，原子序数为8。它是元素周期表中硫族的成员，是一种高度反应性的非金属和氧化剂，容易与大多数元素以及其他化合物形成氧化物。在氢和氦之后，氧是宇宙中第三丰富的元素，按质量计算。在标准温度和压力下，两个氧原子形成双原子二氧气体，这是一种无色无味的气体，化学式为O2。双原子氧气占地球大气层的20.95%，尽管这一比例在地球上经历了很长时间的显著变化。""",
        
        'fluorine': """氟是化学元素，符号为F，原子序数为9。它是最轻的卤素，在标准条件下以高毒性的淡黄色双原子气体存在。作为最具电负性的元素，它极其反应活泼，因为它几乎与所有其他元素反应，除了氖和氦。在元素中，氟具有第三高的电负性，仅次于氧和氯。在水溶液中，氟通常以氟离子的形式存在。""",
        
        'neon': """氖是化学元素，符号为Ne，原子序数为10。它是一种稀有气体。氖在标准条件下是一种无色、无味、惰性的气体，密度约为空气的三分之二。它于1898年被发现（与氪和氙一起），是在氮、氧、氩和二氧化碳被去除后残留在干燥空气中的三种稀有惰性残余元素之一。氖按质量计算是宇宙中第五丰富的元素，在大部分恒星和许多类型的星云中广泛存在。""",
        
        'sodium': """钠是化学元素，符号为Na（源自拉丁语natrium），原子序数为11。它是一种软的、银白色、高度反应性的金属。钠是碱金属，位于元素周期表的第1族，因为它在外层电子壳中有一个单独的电子，很容易被去除，产生一个带正电荷的离子——阳离子，它与阴离子结合形成盐。它唯一稳定的同位素是23Na。游离元素在自然界中不存在，必须从化合物中制备。""",
        
        'magnesium': """镁是化学元素，符号为Mg，原子序数为12。它是一种闪亮的灰色固体，与元素周期表第二列（第2族，或碱土金属）的其他五个元素有密切的物理相似性：第2族的所有元素在最外层电子壳中具有相同的电子构型和相似的晶体结构。镁是宇宙中第九丰富的元素。它在大型老化恒星中通过向碳核顺序添加三个氦核而产生。""",
    },
    'hi': {
        'oxygen': """ऑक्सीजन रासायनिक तत्व है जिसका प्रतीक O और परमाणु संख्या 8 है। यह आवर्त सारणी में चैलकोजन समूह का सदस्य है, एक अत्यधिक प्रतिक्रियाशील अधातु और ऑक्सीकारक एजेंट जो अधिकांश तत्वों के साथ-साथ अन्य यौगिकों के साथ आसानी से ऑक्साइड बनाता है। हाइड्रोजन और हीलियम के बाद, ऑक्सीजन द्रव्यमान द्वारा मापा गया ब्रह्मांड में तीसरा सबसे प्रचुर तत्व है। मानक तापमान और दबाव पर, दो ऑक्सीजन परमाणु द्विपरमाणुक डाइऑक्सीजन गैस बनाते हैं, जो सूत्र O2 के साथ एक रंगहीन और गंधहीन गैस है।""",
        
        'fluorine': """फ्लोरीन रासायनिक तत्व है जिसका प्रतीक F और परमाणु संख्या 9 है। यह सबसे हल्का हैलोजन है और मानक परिस्थितियों में अत्यधिक विषैली हल्की पीली द्विपरमाणुक गैस के रूप में मौजूद है। सबसे अधिक विद्युतऋणात्मक तत्व के रूप में, यह अत्यधिक प्रतिक्रियाशील है, क्योंकि यह नियॉन और हीलियम को छोड़कर लगभग सभी अन्य तत्वों के साथ प्रतिक्रिया करता है। तत्वों में, फ्लोरीन में तीसरी सबसे अधिक विद्युतऋणात्मकता है, केवल ऑक्सीजन और क्लोरीन के पीछे।""",
        
        'neon': """नियॉन एक रासायनिक तत्व है जिसका प्रतीक Ne और परमाणु संख्या 10 है। यह एक उत्कृष्ट गैस है। नियॉन मानक परिस्थितियों में एक रंगहीन, गंधहीन, निष्क्रिय गैस है, जिसका घनत्व हवा के लगभग दो-तिहाई है। इसकी खोज 1898 में (क्रिप्टन और ज़ेनॉन के साथ) तीन दुर्लभ निष्क्रिय अवशिष्ट तत्वों में से एक के रूप में की गई थी जो नाइट्रोजन, ऑक्सीजन, आर्गन और कार्बन डाइऑक्साइड हटाने के बाद सूखी हवा में बचे रहते हैं।""",
        
        'sodium': """सोडियम रासायनिक तत्व है जिसका प्रतीक Na (लैटिन natrium से) और परमाणु संख्या 11 है। यह एक नरम, चांदी-सफेद, अत्यधिक प्रतिक्रियाशील धातु है। सोडियम एक क्षार धातु है, जो आवर्त सारणी के समूह 1 में है, क्योंकि इसके बाहरी खोल में एक एकल इलेक्ट्रॉन है, जिसे आसानी से हटाया जा सकता है, एक सकारात्मक आवेशित आयन बनाता है - एक धनायन, जो ऋणायनों के साथ मिलकर लवण बनाता है। इसका एकमात्र स्थिर समस्थानिक 23Na है।""",
        
        'magnesium': """मैग्नीशियम एक रासायनिक तत्व है जिसका प्रतीक Mg और परमाणु संख्या 12 है। यह एक चमकदार धूसर ठोस है जिसकी आवर्त सारणी की दूसरी स्तंभ (समूह 2, या क्षारीय पृथ्वी धातुएं) की अन्य पांच तत्वों के साथ निकट भौतिक समानता है: समूह 2 के सभी तत्वों में बाहरी इलेक्ट्रॉन खोल में समान इलेक्ट्रॉन विन्यास और समान क्रिस्टल संरचना है। मैग्नीशियम ब्रह्मांड में द्रव्यमान द्वारा नौवां सबसे प्रचुर तत्व है।""",
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
