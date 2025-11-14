#!/usr/bin/env python3
"""
Continue comprehensive translations - Push toward 70% overall.
Focus on completing more elements across all languages including Afrikaans.
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

# Large comprehensive batch across all languages
translations = {
    'fr': {
        'rutherfordium': """Le rutherfordium est un élément chimique de symbole Rf et de numéro atomique 104, nommé en l'honneur du physicien néo-zélandais Ernest Rutherford. En tant qu'élément synthétique, le rutherfordium n'est pas trouvé dans la nature et ne peut être créé qu'en laboratoire. C'est un métal de transition radioactif très instable. Les isotopes les plus stables sont le rutherfodium-267, avec une demi-vie d'environ 1,3 heure, et le rutherfordium-268, avec une demi-vie d'environ 1,3 heure également.""",
        
        'samarium': """Le samarium est un élément chimique de symbole Sm et de numéro atomique 62. C'est un métal de terre rare modérément dur, blanc argenté qui s'oxyde facilement dans l'air. En tant que membre typique de la série des lanthanides, le samarium a généralement un état d'oxydation +3. Des composés de samarium(II) sont également connus, notamment le monoxyde SmO, le monochalcogénure SmS, SmSe et SmTe, ainsi que le samarium(II) iodide. Le samarium a été découvert en 1879 par le chimiste français Paul-Émile Lecoq de Boisbaudran.""",
        
        'scandium': """Le scandium est un élément chimique de symbole Sc et de numéro atomique 21. Un métal de transition blanc argenté, il a été historiquement classé comme un élément de terre rare, avec l'yttrium et les lanthanides. Il a été découvert en 1879 par analyse spectrale des minéraux euxénite et gadolinite de Scandinavie. Le scandium est présent dans la plupart des gisements de composés de terres rares et d'uranium, mais il est extrait de ces minerais en quelques endroits seulement.""",
        
        'seaborgium': """Le seaborgium est un élément chimique de symbole Sg et de numéro atomique 106. Il porte le nom du chimiste américain Glenn T. Seaborg. En tant qu'élément synthétique, il peut être créé en laboratoire mais n'est pas observé dans la nature. Il est également radioactif; l'isotope le plus stable connu, le seaborgium-271, a une demi-vie d'environ 2,4 minutes, bien qu'il soit possible que le seaborgium-269 non confirmé ait une demi-vie plus longue de 14 minutes.""",
        
        'selenium': """Le sélénium est un élément chimique de symbole Se et de numéro atomique 34. C'est un non-métal (plus rarement considéré comme un métalloïde) avec des propriétés intermédiaires entre les éléments au-dessus et en dessous dans le tableau périodique, le soufre et le tellure, et a également des similitudes avec l'arsenic. Il se produit rarement sous sa forme élémentaire ou comme des composés de sélénium pur dans la croûte terrestre. Le sélénium (du grec σελήνη selene, signifiant "Lune") a été découvert en 1817 par Jöns Jacob Berzelius.""",
        
        'silicon': """Le silicium est un élément chimique de symbole Si et de numéro atomique 14. C'est un métalloïde brillant, gris-bleu dur. C'est un membre du groupe 14 du tableau périodique: le carbone est au-dessus et le germanium, l'étain, le plomb et le flérovium sont en dessous. Il est relativement inerte. En raison de son énergie d'ionisation chimique élevée, de ses affinités électroniques et de ses énergies de formation de composés, il est presque exclusivement tétravalent dans sa chimie. Le silicium pur cristallin est trop réactif pour être trouvé dans la nature.""",
    },
    'es': {
        'tin': """El estaño es un elemento químico con el símbolo Sn (del latín: stannum) y número atómico 50. El estaño es un metal post-transición blanco plateado en el grupo 14 de la tabla periódica. Se obtiene principalmente del mineral casiterita, que contiene óxido de estaño, SnO2. El estaño muestra similitud química con los elementos vecinos germánio y plomo, y tiene dos estados de oxidación principales, +2 y el ligeramente más estable +4. El estaño es el 49° elemento más abundante y tiene, con 10 isótopos estables, el mayor número de isótopos estables en la tabla periódica.""",
        
        'antimony': """El antimonio es un elemento químico con el símbolo Sb (del latín: stibium) y número atómico 51. Un metaloide gris brillante, se encuentra en la naturaleza principalmente como el mineral sulfuro estibinita (Sb2S3). Los compuestos de antimonio se conocen desde la antigüedad y se molían en polvo para usarse como medicina y cosméticos, a menudo conocidos por su nombre árabe, kohl. El antimonio metálico también era conocido, pero erróneamente fue identificado como plomo al descubrirlo.""",
        
        'tellurium': """El telurio es un elemento químico con el símbolo Te y número atómico 52. Es un metaloide quebradizo, blanco plateado ligeramente brillante que es ocasionalmente encontrado en forma nativa como cristales elementales. El telurio es químicamente relacionado con el selenio y el azufre. Es ocasionalmente encontrado en forma nativa como cristales elementales. El telurio es mucho más común en el universo en su conjunto que en la Tierra. Su extrema rareza en la corteza terrestre, comparable a la del platino, se debe en parte a su formación de un hidruro volátil.""",
        
        'iodine': """El yodo es un elemento químico con el símbolo I y número atómico 53. El halógeno más pesado y menos reactivo (aunque no se conocen las propiedades de astatina), el yodo es un sólido brillante gris oscuro/violeta que se derrite para formar un líquido violeta oscuro a 114 grados Celsius, y hierve a una violeta gas a 184 grados Celsius. Sin embargo, es fácilmente sublimado con calor suave, resultando en un gas violeta brillante. El yodo se produce en muchas formas químicas orgánicas e inorgánicas.""",
        
        'xenon': """El xenón es un elemento químico con el símbolo Xe y número atómico 54. Es un gas noble incoloro, denso, inodoro que se encuentra en la atmósfera terrestre en trazas. Aunque generalmente no reactivo, puede someterse a algunas reacciones químicas como la formación de difluoruro de xenón. Los isótopos radiactivos de xenón naturales se producen como productos a corto plazo en la cadena de desintegración del uranio-238 y plutonio-244, y se crean constantemente mediante fisión nuclear y fisión espontánea en el combustible de reactores nucleares.""",
    },
    'de': {
        'tin': """Zinn ist ein chemisches Element mit dem Symbol Sn (vom lateinischen: stannum) und der Ordnungszahl 50. Zinn ist ein silberweißes Post-Übergangsmetall in Gruppe 14 des Periodensystems. Es wird hauptsächlich aus dem Mineral Kassiterit gewonnen, das Zinnoxid, SnO2, enthält. Zinn zeigt chemische Ähnlichkeit mit den Nachbarelementen Germanium und Blei und hat zwei Hauptoxidationsstufen, +2 und die etwas stabilere +4. Zinn ist das 49. häufigste Element und hat mit 10 stabilen Isotopen die größte Anzahl stabiler Isotope im Periodensystem.""",
        
        'antimony': """Antimon ist ein chemisches Element mit dem Symbol Sb (vom lateinischen: stibium) und der Ordnungszahl 51. Ein glänzendes graues Halbmetall, es kommt in der Natur hauptsächlich als Sulfidmineral Stibnit (Sb2S3) vor. Antimonverbindungen sind seit der Antike bekannt und wurden zu Pulver gemahlen, um als Medizin und Kosmetik verwendet zu werden, oft unter ihrem arabischen Namen Kohl bekannt. Metallisches Antimon war ebenfalls bekannt, wurde aber fälschlicherweise als Blei identifiziert, als es entdeckt wurde.""",
        
        'tellurium': """Tellur ist ein chemisches Element mit dem Symbol Te und der Ordnungszahl 52. Es ist ein sprödes, leicht glänzendes, silbrig-weißes Halbmetall, das gelegentlich in nativer Form als elementare Kristalle gefunden wird. Tellur ist chemisch mit Selen und Schwefel verwandt. Es wird gelegentlich in nativer Form als elementare Kristalle gefunden. Tellur ist im Universum als Ganzes viel häufiger als auf der Erde. Seine extreme Seltenheit in der Erdkruste, vergleichbar mit Platin, ist teilweise auf seine Bildung eines flüchtigen Hydrids zurückzuführen.""",
        
        'iodine': """Iod ist ein chemisches Element mit dem Symbol I und der Ordnungszahl 53. Das schwerste und am wenigsten reaktive Halogen (obwohl die Eigenschaften von Astat nicht bekannt sind), ist Iod ein glänzender dunkelgrau/violetter Feststoff, der schmilzt, um eine dunkelviolette Flüssigkeit bei 114 Grad Celsius zu bilden, und kocht zu einem violetten Gas bei 184 Grad Celsius. Es sublimiert jedoch leicht mit sanfter Wärme, was zu einem leuchtend violetten Gas führt. Iod wird in vielen organischen und anorganischen chemischen Formen hergestellt.""",
        
        'xenon': """Xenon ist ein chemisches Element mit dem Symbol Xe und der Ordnungszahl 54. Es ist ein farbloses, dichtes, geruchloses Edelgas, das in Spuren in der Erdatmosphäre vorkommt. Obwohl im Allgemeinen nicht reaktiv, kann es einige chemische Reaktionen eingehen, wie die Bildung von Xenondifluorid. Natürliche radioaktive Xenon-Isotope werden als kurzlebige Produkte in der Zerfallskette von Uran-238 und Plutonium-244 hergestellt und werden ständig durch Kernspaltung und Spontanspaltung im Kernreaktorbrennstoff erzeugt.""",
    },
    'it': {
        'technetium': """Il tecnezio è un elemento chimico con simbolo Tc e numero atomico 43. È l'elemento di numero atomico più basso senza isotopi stabili. Quasi tutto il tecnezio è prodotto sinteticamente, e solo tracce minuscole sono state trovate in natura. Il tecnezio è stato il primo elemento prodotto prevalentemente artificialmente. Il suo nome deriva dal greco τεχνητός, che significa "artificiale". Molte delle proprietà del tecnezio furono predette da Dmitri Mendeleev prima che l'elemento fosse scoperto.""",
        
        'palladium': """Il palladio è un elemento chimico con simbolo Pd e numero atomico 46. È un metallo bianco argenteo brillante, raro e lucido scoperto nel 1803 dal chimico inglese William Hyde Wollaston. Lo nominò in onore dell'asteroide Pallas, che era stato scoperto due anni prima. Palladio, platino, rodio, rutenio, iridio e osmio formano un gruppo di elementi noti come i metalli del gruppo del platino. Hanno proprietà chimiche simili, ma il palladio ha il punto di fusione più basso ed è il meno denso di loro.""",
        
        'silver': """L'argento è un elemento chimico con simbolo Ag (dal latino: argentum, derivato dal proto-indoeuropeo h₂erǵ: "brillante" o "bianco") e numero atomico 47. Un metallo di transizione morbido, bianco e lucido, mostra la conduttività elettrica, conduttività termica e riflettività più alta di qualsiasi metallo. Il metallo si trova nella crosta terrestre in forma elementare pura e libera ("argento nativo"), come lega con oro e altri metalli, e in minerali come argentite e clorargirite.""",
        
        'cadmium': """Il cadmio è un elemento chimico con simbolo Cd e numero atomico 48. Questo metallo morbido, blu-bianco è chimicamente simile agli altri due metalli stabili del gruppo 12, zinco e mercurio. Come lo zinco, preferisce lo stato di ossidazione +2 nella maggior parte dei suoi composti, e come il mercurio, ha un punto di fusione più basso dei metalli di transizione dei gruppi 3-11. Il cadmio e i suoi congeneri del gruppo 12 non sono sempre considerati metalli di transizione.""",
        
        'indium': """L'indio è un elemento chimico con simbolo In e numero atomico 49. L'indio è il metallo più morbido che non è un metallo alcalino. È un metallo post-transizione bianco argenteo brillante. Lo spettro dell'indio è dominato da una linea indaco, da cui il suo nome. L'indio ha un punto di fusione più alto del sodio e del gallio, ma più basso del litio e dello stagno. Chimicamente, l'indio è simile al gallio e al tallio, ed è in gran parte intermedio tra i due in termini di proprietà.""",
    },
    'pt': {
        'technetium': """O tecnécio é um elemento químico com o símbolo Tc e número atômico 43. É o elemento de menor número atômico sem isótopos estáveis. Quase todo o tecnécio é produzido sinteticamente, e apenas traços minúsculos foram encontrados na natureza. O tecnécio foi o primeiro elemento produzido predominantemente artificialmente. Seu nome deriva do grego τεχνητός, que significa "artificial". Muitas das propriedades do tecnécio foram previstas por Dmitri Mendeleev antes que o elemento fosse descoberto.""",
        
        'palladium': """O paládio é um elemento químico com o símbolo Pd e número atômico 46. É um metal branco prateado brilhante, raro e lustroso descoberto em 1803 pelo químico inglês William Hyde Wollaston. Ele o nomeou em homenagem ao asteroide Pallas, que havia sido descoberto dois anos antes. Paládio, platina, ródio, rutênio, irídio e ósmio formam um grupo de elementos conhecidos como os metais do grupo da platina. Eles têm propriedades químicas semelhantes, mas o paládio tem o ponto de fusão mais baixo e é o menos denso deles.""",
        
        'silver': """A prata é um elemento químico com o símbolo Ag (do latim: argentum, derivado do proto-indo-europeu h₂erǵ: "brilhante" ou "branco") e número atômico 47. Um metal de transição macio, branco e brilhante, exibe a maior condutividade elétrica, condutividade térmica e refletividade de qualquer metal. O metal é encontrado na crosta terrestre em forma elementar pura e livre ("prata nativa"), como liga com ouro e outros metais, e em minerais como argentita e clorargirita.""",
        
        'cadmium': """O cádmio é um elemento químico com o símbolo Cd e número atômico 48. Este metal macio, branco azulado é quimicamente semelhante aos outros dois metais estáveis do grupo 12, zinco e mercúrio. Como o zinco, prefere o estado de oxidação +2 na maioria de seus compostos, e como o mercúrio, tem um ponto de fusão mais baixo que os metais de transição dos grupos 3-11. O cádmio e seus congêneres do grupo 12 nem sempre são considerados metais de transição.""",
        
        'indium': """O índio é um elemento químico com o símbolo In e número atômico 49. O índio é o metal mais macio que não é um metal alcalino. É um metal pós-transição branco prateado brilhante. O espectro do índio é dominado por uma linha índigo, daí seu nome. O índio tem um ponto de fusão mais alto que o sódio e o gálio, mas mais baixo que o lítio e o estanho. Quimicamente, o índio é semelhante ao gálio e ao tálio, e está em grande parte intermediário entre os dois em termos de propriedades.""",
    },
    'zh': {
        'argon': """氩是化学元素，符号为Ar，原子序数为18。它在元素周期表中是第三轻的稀有气体。氩约占地球大气层的0.934%，是大气中第三丰富的气体，也是地壳中最丰富的稀有气体。它的名字来源于希腊语ἀργόν，中性单数形式的ἀργός，意为"懒惰"或"不活跃"，作为其化学惰性的参考。氩于1894年被苏格兰科学家约翰·威廉·斯特拉斯，第三代瑞利男爵，和苏格兰科学家威廉·拉姆齐在英国分离出来。""",
        
        'potassium': """钾是化学元素，符号为K（源自新拉丁语kalium），原子序数为19。它首次从草木灰中分离出来，因此得名。在元素周期表中，钾是碱金属之一。所有碱金属在外层电子壳中都有一个价电子，很容易被去除以产生一个带正电荷的离子——阳离子，它与阴离子结合形成盐。自然界中的钾只以离子盐的形式存在。元素钾是一种软的银白色金属，在空气中很快氧化。""",
        
        'calcium': """钙是化学元素，符号为Ca，原子序数为20。作为碱土金属，钙是一种反应性软金属，在空气中形成暗氧化-氮化物层。其物理和化学性质与较重的同族元素锶和钡最相似。它是宇宙中第五丰富的元素，按质量计算，也是地壳中第五丰富的金属。钙的最常见同位素钙-40由古老恒星中发生的核合成过程形成，构成97%的元素。""",
        
        'scandium': """钪是化学元素，符号为Sc，原子序数为21。银白色过渡金属，历史上被归类为稀土元素，与钇和镧系元素一起。它是在1879年通过光谱分析来自斯堪的纳维亚的黝帘石和硅铍钇矿发现的。钪存在于大多数稀土和铀化合物的矿藏中，但仅在少数地方从这些矿石中提取。每年全球仅生产约10吨钪。""",
        
        'titanium': """钛是化学元素，符号为Ti，原子序数为22。它是一种有光泽的过渡金属，具有银色、低密度和高强度。钛耐腐蚀于海水、王水和氯气。钛于1791年在英国康沃尔被威廉·格雷戈尔发现，并由德国化学家马丁·海因里希·克拉普罗特命名，他独立发现了它。这个名字来自希腊神话中的泰坦。它在地壳中的丰度排名第九，按质量计算占0.6%。""",
    },
    'hi': {
        'argon': """आर्गन रासायनिक तत्व है जिसका प्रतीक Ar और परमाणु संख्या 18 है। यह आवर्त सारणी में तीसरी सबसे हल्की उत्कृष्ट गैस है। आर्गन पृथ्वी के वायुमंडल का लगभग 0.934% है, जो वायुमंडल में तीसरी सबसे प्रचुर गैस है और पृथ्वी की पपड़ी में सबसे प्रचुर उत्कृष्ट गैस है। इसका नाम ग्रीक ἀργόν से लिया गया है, जो ἀργός का तटस्थ एकवचन रूप है, जिसका अर्थ है "आलसी" या "निष्क्रिय", इसकी रासायनिक निष्क्रियता के संदर्भ में।""",
        
        'potassium': """पोटेशियम रासायनिक तत्व है जिसका प्रतीक K (नियो-लैटिन kalium से) और परमाणु संख्या 19 है। इसे पहली बार पोटाश, पौधों की राख से अलग किया गया था, जहां से इसका नाम आया है। आवर्त सारणी में, पोटेशियम क्षार धातुओं में से एक है। सभी क्षार धातुओं के बाहरी इलेक्ट्रॉन खोल में एक संयोजी इलेक्ट्रॉन होता है, जिसे आसानी से हटाया जा सकता है एक सकारात्मक आवेशित आयन बनाने के लिए - एक धनायन, जो ऋणायनों के साथ मिलकर लवण बनाता है।""",
        
        'calcium': """कैल्शियम एक रासायनिक तत्व है जिसका प्रतीक Ca और परमाणु संख्या 20 है। क्षारीय पृथ्वी धातु के रूप में, कैल्शियम एक प्रतिक्रियाशील नरम धातु है जो हवा में एक सुस्त ऑक्साइड-नाइट्राइड परत बनाती है। इसके भौतिक और रासायनिक गुण इसके भारी समूह साथियों स्ट्रोंटियम और बेरियम के सबसे समान हैं। यह द्रव्यमान द्वारा ब्रह्मांड में पांचवां सबसे प्रचुर तत्व है और द्रव्यमान द्वारा पृथ्वी की पपड़ी में पांचवी सबसे प्रचुर धातु भी है।""",
        
        'scandium': """स्कैंडियम एक रासायनिक तत्व है जिसका प्रतीक Sc और परमाणु संख्या 21 है। एक चांदी-सफेद संक्रमण धातु, इसे ऐतिहासिक रूप से दुर्लभ पृथ्वी तत्व के रूप में वर्गीकृत किया गया है, यट्रियम और लैंथेनाइड्स के साथ। यह 1879 में स्कैंडिनेविया से यूक्सेनाइट और गैडोलिनाइट खनिजों के स्पेक्ट्रल विश्लेषण द्वारा खोजा गया था। स्कैंडियम अधिकांश दुर्लभ पृथ्वी और यूरेनियम यौगिक जमाओं में मौजूद है, लेकिन केवल कुछ स्थानों में इन अयस्कों से निकाला जाता है।""",
        
        'titanium': """टाइटेनियम एक रासायनिक तत्व है जिसका प्रतीक Ti और परमाणु संख्या 22 है। यह एक चमकदार संक्रमण धातु है जिसमें चांदी का रंग, कम घनत्व और उच्च शक्ति है। टाइटेनियम समुद्री जल, एक्वा रेजिया और क्लोरीन के प्रति संक्षारण प्रतिरोधी है। टाइटेनियम को 1791 में इंग्लैंड के कॉर्नवाल में विलियम ग्रेगर द्वारा खोजा गया था और जर्मन रसायनज्ञ मार्टिन हेनरिक क्लैप्रोथ द्वारा नामित किया गया था, जिन्होंने इसे स्वतंत्र रूप से खोजा था।""",
    },
    'af': {
        # Start Afrikaans translations
        'hydrogen': """Waterstof is 'n chemiese element met die simbool H en atoomgetal 1. Met 'n standaard atomiese gewig van 1.008 is waterstof die ligste element in die periodieke tabel. Waterstof is die mees algemene chemiese stof in die heelal en maak ongeveer 75% van alle barioniese massa uit. Nie-oorblyfsels sterre bestaan hoofsaaklik uit waterstof in die plasma-toestand. Die mees algemene waterstof-isotoop (simbool 1H) bestaan uit een proton, een elektron en geen neutrone nie.""",
        
        'helium': """Helium (van Grieks: ἥλιος, geromaniseer: Helios, letterlik 'Son') is 'n chemiese element met die simbool He en atoomgetal 2. Dit is 'n kleurlose, reuklose, proelose, nie-giftige, inerte, monoatomiese gas, die eerste in die edelgas-groep in die periodieke tabel. Sy kookpunt is die laagste onder al die elemente. Helium is die tweede ligste en tweede mees algemene element in die waarneembare heelal (na waterstof), wat ongeveer 24% van die totale elementêre massa uitmaak.""",
        
        'lithium': """Litium is 'n chemiese element met die simbool Li en atoomgetal 3. Dit is 'n sagte, silweragtig-wit alkali metaal. Onder standaard toestande is dit die ligste metaal en die ligste soliede element. Soos alle alkali metale is litium hoogs reaktief en korrosief, en oksideer vinnig in lug tot 'n dowwe swart. Dit word nooit as 'n vrye element in die natuur gevind nie, en kom slegs in verbindings voor, tipies ioniese verbindings. Litium kom voor in verskeie petalite silikaat-verbindings.""",
        
        'carbon': """Koolstof is 'n chemiese element met die simbool C en atoomgetal 6. Dit is nie-metaal en vierwaardig—vier elektrone beskikbaar om kovalente chemiese bindings te vorm. Dit behoort tot groep 14 van die periodieke tabel. Koolstof maak slegs 0.025% van die aardkors uit, maar is noodsaaklik vir lewe. Drie isotope kom natuurlik voor, waarvan 12C en 13C stabiel is, terwyl 14C 'n radionuklied is wat verval met 'n halfleeftyd van ongeveer 5730 jaar. Koolstof is een van die min elemente wat sedert antieke tye bekend is.""",
        
        'nitrogen': """Stikstof is 'n chemiese element met die simbool N en atoomgetal 7. Dit is eers in 1772 deur die Skotse geneesheer Daniel Rutherford ontdek en geïsoleer. Alhoewel Carl Wilhelm Scheele en Henry Cavendish onafhanklik dieselfde eksperiment ongeveer dieselfde tyd uitgevoer het, het Rutherford waargeneem dat dit 'n bestanddeel van lug was en sy bevindinge eerste gepubliseer. Stikstof is 'n kleurlose en reuklose diatomies gas onder standaard toestande, en maak ongeveer 78% van die aarde se atmosfeer uit.""",
    },
}

# Process all translations
total_updated = 0
for lang_code, lang_translations in translations.items():
    count = bulk_update(lang_code, lang_translations)
    lang_names = {
        'fr': 'French', 'es': 'Spanish', 'de': 'German', 
        'it': 'Italian', 'pt': 'Portuguese', 'zh': 'Chinese', 'hi': 'Hindi', 'af': 'Afrikaans'
    }
    print(f"✅ {lang_names.get(lang_code, lang_code)}: Updated {count} elements")
    total_updated += count

print(f"\n{'='*60}")
print(f"TOTAL: Updated {total_updated} element descriptions")
print(f"{'='*60}")
