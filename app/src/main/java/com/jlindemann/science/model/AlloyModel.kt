package com.jlindemann.science.model

/**
 * A metal alloy.
 *
 * @param base the element the alloy is mostly made of, as a lowercase English element key
 * @param composition the main constituents, in plain language
 * @param description what it is and why the alloy is used instead of the base metal
 */
data class Alloy(
    val name: String,
    val base: String,
    val composition: String,
    val description: String,
    val wiki: String
)

/**
 * Common alloys, so questions like "what is the difference between iron and steel" have an
 * answer.
 *
 * Alloys are not elements, so nothing in the periodic table data covers them, and the agent
 * previously had nothing at all to say. Each entry names the base element, which is what lets an
 * element-versus-alloy question be recognised and answered.
 */
object AlloyModel {
    fun getList(alloys: ArrayList<Alloy>) {
        alloys.add(Alloy("Steel", "iron", "Iron with up to about 2% carbon",
            "Steel is iron alloyed with a small amount of carbon. The carbon atoms sit between the iron atoms and obstruct the planes along which they would otherwise slide, which makes steel far harder and stronger than pure iron. Pure iron is soft and corrodes readily; steel can be tuned across a wide range of hardness and toughness by varying the carbon content and heat treatment.",
            "https://en.wikipedia.org/wiki/Steel"))
        alloys.add(Alloy("Stainless steel", "iron", "Iron, at least 10.5% chromium, often nickel",
            "Stainless steel is steel with enough chromium that a thin, self-repairing chromium oxide layer forms on the surface and blocks further corrosion. Nickel is often added to improve toughness and formability.",
            "https://en.wikipedia.org/wiki/Stainless_steel"))
        alloys.add(Alloy("Cast iron", "iron", "Iron with roughly 2 to 4% carbon",
            "Cast iron holds more carbon than steel, which makes it hard, brittle and easy to cast, but not practical to forge. Its high carbon content also gives it good vibration damping and wear resistance.",
            "https://en.wikipedia.org/wiki/Cast_iron"))
        alloys.add(Alloy("Brass", "copper", "Copper and zinc",
            "Brass is copper alloyed with zinc. It is harder than copper, easy to machine and cast, resists corrosion, and is naturally antimicrobial. The proportion of zinc sets its colour and workability.",
            "https://en.wikipedia.org/wiki/Brass"))
        alloys.add(Alloy("Bronze", "copper", "Copper and tin",
            "Bronze is copper alloyed with tin. It is harder than either constituent and resists corrosion in seawater, which is why it was the defining metal of an age and is still used for bearings, propellers and bells.",
            "https://en.wikipedia.org/wiki/Bronze"))
        alloys.add(Alloy("Solder", "tin", "Tin with lead, silver or copper",
            "Solder is a low-melting alloy used to join metals electrically and mechanically. Traditional solder was tin and lead; modern electronics solder replaces the lead with silver and copper.",
            "https://en.wikipedia.org/wiki/Solder"))
        alloys.add(Alloy("Pewter", "tin", "Mostly tin, with copper and antimony",
            "Pewter is a soft, low-melting tin alloy used for tableware and decorative casting.",
            "https://en.wikipedia.org/wiki/Pewter"))
        alloys.add(Alloy("Duralumin", "aluminium", "Aluminium with copper, magnesium and manganese",
            "Duralumin is an age-hardening aluminium alloy that approaches the strength of soft steel at roughly a third of the density, which made early all-metal aircraft possible.",
            "https://en.wikipedia.org/wiki/Duralumin"))
        alloys.add(Alloy("Ti-6Al-4V", "titanium", "Titanium with 6% aluminium and 4% vanadium",
            "The workhorse titanium alloy, and the reason titanium dominates aerospace: it combines a very high strength-to-weight ratio with excellent corrosion resistance and retains its strength at high temperature.",
            "https://en.wikipedia.org/wiki/Ti-6Al-4V"))
        alloys.add(Alloy("Nichrome", "nickel", "Nickel and chromium",
            "Nichrome has a high electrical resistance and does not oxidise appreciably when hot, which makes it the standard material for heating elements.",
            "https://en.wikipedia.org/wiki/Nichrome"))
        alloys.add(Alloy("Sterling silver", "silver", "92.5% silver, usually with copper",
            "Pure silver is too soft for most objects; adding copper gives sterling silver enough hardness for tableware and jewellery while keeping the colour and lustre.",
            "https://en.wikipedia.org/wiki/Sterling_silver"))
        alloys.add(Alloy("White gold", "gold", "Gold with palladium, nickel or silver",
            "White gold is gold alloyed with a white metal to change its colour, and usually plated with rhodium for a brighter finish.",
            "https://en.wikipedia.org/wiki/Colored_gold"))
        alloys.add(Alloy("Amalgam", "mercury", "Mercury with silver, tin and copper",
            "An amalgam is an alloy of mercury with another metal. Dental amalgam was widely used for fillings because it is durable and sets at body temperature.",
            "https://en.wikipedia.org/wiki/Amalgam_(chemistry)"))
        alloys.add(Alloy("Magnalium", "aluminium", "Aluminium with magnesium",
            "Magnalium is lighter and stiffer than pure aluminium and is used where weight matters more than cost.",
            "https://en.wikipedia.org/wiki/Magnalium"))
        alloys.add(Alloy("Inconel", "nickel", "Nickel with chromium and iron",
            "Inconel is a family of nickel superalloys that keep their strength and resist oxidation at temperatures where steel would fail, so they are used in turbine blades and rocket engines.",
            "https://en.wikipedia.org/wiki/Inconel"))
    }
}
