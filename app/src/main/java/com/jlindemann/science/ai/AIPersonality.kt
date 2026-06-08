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
        "atomic number" to "The atomic number tells us how many protons are in the nucleus. It's what makes each element unique!",
        "atomic mass" to "The atomic mass is the total mass of protons and neutrons in an atom. It helps us understand the atom's weight!",
        "boiling point" to "The boiling point is the temperature at which a liquid becomes a gas. Pretty cool, right?",
        "melting point" to "The melting point is when a solid turns into a liquid. Temperature matters! 🔥",
        "density" to "Density tells us how tightly packed an element's atoms are. Some elements are super dense!",
        "electron configuration" to "This shows how electrons are arranged around the nucleus. It's like the atom's fingerprint!",
        "oxidation state" to "Oxidation states show how many electrons an atom can lose, gain, or share. Chemistry magic! ✨",
        "crystal structure" to "The way atoms are arranged in a solid pattern. Think of it as atomic LEGO! 🧱",
        "electronegativity" to "How much an atom 'wants' electrons. High values mean they attract electrons strongly!",
        "ionization energy" to "The energy needed to remove an electron from an atom. Tough electrons stick around longer! 💪"
    )
    
    fun getGreeting(): String = greetings.random()
    
    fun getEncouragement(): String = encouragements.random()
    
    fun getElementInfoHint(topic: String): String? {
        return elementTemplates[topic.lowercase()]
    }
    
    fun formatElementResponse(elementName: String, property: String, value: String): String {
        return when {
            value.isEmpty() -> "I don't have data about $elementName's $property, sorry!"
            else -> "$elementName's $property is $value. Fascinating! 🔬"
        }
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
