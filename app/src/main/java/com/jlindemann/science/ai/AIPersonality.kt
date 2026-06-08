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
        "Hey! I'm here to make chemistry fun and easy. What element interests you?"
    )
    
    // Encouragement phrases
    private val encouragements = listOf(
        "Great question! 🌟",
        "I love your curiosity! 📚",
        "Excellent! You're getting it! ✨",
        "Nice! Chemistry is awesome, isn't it? 🔬",
        "That's a smart question! 🧠"
    )
    
    // Element info templates
    private val elementTemplates = mapOf(
        "atomic number" to "The atomic number tells us how many protons are in the nucleus. It's what makes each element unique!",
        "atomic mass" to "The atomic mass is the total mass of protons and neutrons in an atom. It helps us understand the atom's weight!",
        "boiling point" to "The boiling point is the temperature at which a liquid becomes a gas. Pretty cool, right?",
        "melting point" to "The melting point is when a solid turns into a liquid. Temperature matters! ��️",
        "density" to "Density tells us how tightly packed an element's atoms are. Some elements are super dense!",
        "electron configuration" to "This shows how electrons are arranged around the nucleus. It's like the atom's fingerprint!",
        "oxidation state" to "Oxidation states show how many electrons an atom can lose, gain, or share. Chemistry magic! ✨"
    )
    
    fun getGreeting(): String = greetings.random()
    
    fun getEncouragement(): String = encouragements.random()
    
    fun getElementInfoHint(topic: String): String? {
        return elementTemplates[topic.lowercase()]
    }
    
    fun formatElementResponse(elementName: String, property: String, value: String): String {
        return "$elementName's $property is $value. Fascinating! 🔬"
    }
    
    fun getRandomFact(): String {
        val facts = listOf(
            "Did you know? Oxygen is the most abundant element in Earth's crust!",
            "Fun fact: Gold never rusts or corrodes. It stays shiny forever!",
            "Here's something cool: Hydrogen is the most abundant element in the universe! 🌌",
            "Amazing: Carbon is the basis for all known life on Earth!",
            "Interesting: Helium is the only element that doesn't solidify under normal pressure!"
        )
        return facts.random()
    }
    
    fun getNoDataResponse(query: String): String {
        val responses = listOf(
            "Hmm, I couldn't find information about that. Try asking about an element like 'oxygen' or 'gold'!",
            "I'm not sure about that one. Why don't you ask me about a specific element?",
            "That's beyond my current knowledge base, but I know tons about elements! Want to learn about one?"
        )
        return responses.random()
    }
}
