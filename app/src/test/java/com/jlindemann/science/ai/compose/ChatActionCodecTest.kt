package com.jlindemann.science.ai.compose

import com.jlindemann.science.ai.data.DeepLinkTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatActionCodecTest {

    /**
     * The balancer chip carries the equation across, and an equation contains the two characters
     * the codec is most likely to be tripped by: the `=` it splits arg pairs on, and the `>` of the
     * arrow. Decoding splits on the *first* `=`, so a value holding more of them survives — this
     * test is what says so.
     */
    @Test
    fun anEquationSurvivesTheRoundTrip() {
        for (equation in listOf("C3H8 + O2 -> CO2 + H2O", "H2 = O2 -> H2O", "Fe + O2 → Fe2O3")) {
            val action = ChatAction(
                label = "Open Equation Balancer",
                target = DeepLinkTarget.REACTION_BALANCER,
                args = mapOf(DeepLinkNavigator.ARG_EQUATION to equation)
            )
            val decoded = ChatActionCodec.decode(ChatActionCodec.encode(listOf(action)))
            assertEquals(equation, listOf(action), decoded)
        }
    }

    /** Several actions on one message must not bleed into each other. */
    @Test
    fun actionsStaySeparate() {
        val actions = listOf(
            ChatAction("Open Equation Balancer", DeepLinkTarget.REACTION_BALANCER,
                mapOf(DeepLinkNavigator.ARG_EQUATION to "Fe + O2 -> Fe2O3")),
            ChatAction("Open Iron", DeepLinkTarget.ELEMENT_INFO, mapOf("key" to "iron"))
        )
        assertEquals(actions, ChatActionCodec.decode(ChatActionCodec.encode(actions)))
    }

    @Test
    fun nothingToAttachEncodesToNull() {
        assertEquals(null, ChatActionCodec.encode(emptyList()))
        assertEquals(emptyList<ChatAction>(), ChatActionCodec.decode(null))
    }
}
