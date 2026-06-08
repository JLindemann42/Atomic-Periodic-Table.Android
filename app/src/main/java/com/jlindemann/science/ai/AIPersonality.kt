package com.jlindemann.science.ai

/**
 * AI personality and response templates for the AI agent
 * Inspired by Duolingo's friendly and encouraging approach
 */
object AIPersonality {
    
    // Greeting responses
    private val greetings = listOf(
        "Hey there! 👋 I'm your chemistry buddy. Ask me anything about elements!",
        "Welcome! I'm excited to help you learn about chemistry. What would you like to know?",
        "Hello! Ready to explore the periodic table? Ask away! 🧪",
        "Hey! I'm here to make chemistry fun and easy. What element interests you?",
        "Welcome to the chemistry corner! 🔬 Pick an element, any element!",
        "Hi there! I know tons about elements. What's your question?"
    )
    
    // Encouragement phrases
    private val encouragements = listOf(
        "Great question! 🌟",
        "I love your curiosity! 📚",
        "Excellent! You're getting it! ✨",
        "Nice! Chemistry is awesome, isn't it? 🔬",
        "That's a smart question! 🧠",
        "Wonderful! Let me tell you... ⚗️",
        "Perfect timing! Here's the scoop: 📖",
        "I'm happy to explain! 🎓"
    )
    
    // No data responses - more varied to prevent hallucinations
    private val noDataResponses = listOf(
        "Hmm, I couldn't find information about that. Try asking about an element like 'oxygen' or 'gold'!",
        "I'm not sure about that one. Why don't you ask me about a specific element?",
        "That's beyond my current knowledge base, but I know tons about elements! Want to learn about one?",
        "I don't have data on that. Let me know an element you're curious about! 🔍",
        "That's outside my expertise right now. How about asking about a specific element's properties?",
        "I couldn't find that information in my database. Try asking about an element I know! 📊"
    )
    
    // Element info templates - more comprehensive
    private val elementTemplates = mapOf(
        "atomic number" to "The atomic number is its ID on the periodic table, representing the number of protons in its nucleus.",
        "atomic mass" to "The atomic mass tells us the average weight of its atoms, including protons and neutrons.",
        "boiling point" to "That's the temperature where it turns from a liquid into a gas. It's quite a transformation!",
        "melting point" to "This is the exact temperature where it changes from a solid to a liquid.",
        "density" to "Density measures how tightly its atoms are packed together. Some elements are surprisingly heavy for their size!",
        "electron configuration" to "This describes how electrons are distributed in their orbits. It's like a map of the atom's outer layers!",
        "oxidation state" to "Oxidation states describe how many electrons an atom can share or trade during a chemical reaction.",
        "crystal structure" to "This is the geometric pattern that its atoms form when it's a solid. Nature's own architecture!",
        "electronegativity" to "This value shows how strongly an atom attracts electrons. High electronegativity means it's a real 'electron-grabber'!",
        "ionization energy" to "This is the amount of energy required to remove an electron. Some atoms hold on much tighter than others!",
        "category" to "This tells us which family of elements it belongs to, which explains a lot about how it behaves.",
        "group" to "Elements in the same group often share similar chemical personalities!",
        "period" to "The period tells us the number of electron shells the atom has."
    )
    
    fun getGreeting(): String = greetings.random()
    
    fun getEncouragement(): String = encouragements.random()
    
    fun getElementInfoHint(topic: String): String? {
        return elementTemplates[topic.lowercase()]
    }
    
    fun formatElementResponse(elementName: String, property: String, value: String, isRepeat: Boolean = false): String {
        if (value.isEmpty() || value == "---") return "I'm sorry, I don't have the data for $elementName's $property right now."
        
        val hint = getElementInfoHint(property)
        val intro = if (isRepeat) {
            listOf(
                "As I mentioned earlier,",
                "Just to recap,",
                "Right, as we discussed,",
                "To remind you,"
            ).random()
        } else {
            getEncouragement()
        }
        
        return if (hint != null && !isRepeat) {
            "$intro $elementName's $property is $value. $hint"
        } else {
            "$intro $elementName's $property is $value."
        }
    }
    
    fun formatElementOverview(
        elementName: String,
        symbol: String,
        atomicNumber: String,
        category: String,
        group: String,
        appearance: String,
        discovery: String,
        description: String,
        protons: String,
        neutrons: String,
        electrons: String
    ): String {
        val encouragement = getEncouragement()
        val intro = "$encouragement $elementName ($symbol) is a fascinating element! It's atomic number $atomicNumber and belongs to the $category group."
        
        val groupText = if (group.isNotEmpty() && !category.contains(group, ignoreCase = true)) " It is part of the $group series." else ""
        
        val appearanceText = if (appearance.isNotEmpty()) " It typically appears as $appearance." else ""
        
        val isotopeInfo = if (protons.isNotEmpty() && neutrons.isNotEmpty()) {
            " An atom of $elementName contains $protons protons, $electrons electrons, and usually $neutrons neutrons."
        } else if (electrons.isNotEmpty()) {
            " It has $electrons electrons orbiting its nucleus."
        } else ""
        
        val discoveryText = if (discovery.isNotEmpty()) " $discovery" else ""
        
        val descriptionText = if (description.isNotEmpty()) " $description" else ""
        
        return "$intro$groupText$appearanceText$isotopeInfo$discoveryText$descriptionText"
    }

    fun formatComprehensiveResponse(elementName: String, data: Map<String, String>): String {
        val encouragement = getEncouragement()
        val intro = "$encouragement I've got some great details about $elementName for you!"
        
        val facts = data.entries.joinToString(" ") { (prop, value) ->
            when (prop.lowercase()) {
                "symbol" -> "It is represented by the symbol $value."
                "atomic number" -> "Its atomic number is $value, meaning it has $value protons."
                "mass" -> "It has an atomic mass of $value."
                "category" -> "It belongs to the $value category."
                "phase" -> "At standard conditions, it exists as a $value."
                "appearance" -> "Visually, it's described as $value."
                else -> "Its $prop is $value."
            }
        }
        
        return "$intro $facts"
    }
    
    fun getRandomFact(): String {
        val facts = listOf(
            "Did you know? Oxygen is the most abundant element in Earth's crust!",
            "Fun fact: Gold never rusts or corrodes. It stays shiny forever!",
            "Here's something cool: Hydrogen is the most abundant element in the universe! 🌌",
            "Amazing: Carbon is the basis for all known life on Earth!",
            "Interesting: Helium is the only element that doesn't solidify under normal pressure!",
            "Fun fact: Mercury is the only element that is liquid at room temperature! 💧",
            "Did you know? The element francium is so radioactive it would vaporize instantly!",
            "Cool: Diamond and graphite are both made of pure carbon but have completely different properties!",
            "Amazing: A single ounce of gold can be beaten into a sheet 300 square feet in area!",
            "Interesting: Neon doesn't actually form any chemical compounds under normal conditions!"
        )
        return facts.random()
    }
    
    fun getNoDataResponse(query: String): String {
        val response = noDataResponses.random()
        return response
    }
}
