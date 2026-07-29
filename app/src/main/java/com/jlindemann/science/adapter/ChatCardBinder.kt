package com.jlindemann.science.adapter

import android.view.View
import android.widget.ImageView
import com.jlindemann.science.R
import com.jlindemann.science.ai.cards.AbundanceReducer
import com.jlindemann.science.ai.cards.ChatCard
import com.jlindemann.science.ai.cards.ChatCardKind
import com.jlindemann.science.ai.cards.EmissionSpectrumUrl
import com.jlindemann.science.ai.cards.IonizationSeriesReducer
import com.jlindemann.science.ai.cards.IsotopeSeriesReducer
import com.jlindemann.science.ai.cards.NfpaLabeller
import com.jlindemann.science.ai.cards.PoissonBandReducer
import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.extensions.CrystalStructureView
import com.jlindemann.science.views.AbundanceBarsView
import com.jlindemann.science.views.ElectronShellView
import com.jlindemann.science.views.IonizationSeriesView
import com.jlindemann.science.views.IsotopeDecayView
import com.jlindemann.science.views.NfpaDiamondBinder
import com.jlindemann.science.views.PoissonBandView
import com.squareup.picasso.Picasso

/**
 * Puts a [ChatCard]'s data into an already-inflated card view.
 *
 * Stateless, and every id is resolved against the card view rather than an Activity, so several
 * card rows can be on screen at once without colliding. The card carries only a reference, so the
 * numbers are read back out of the [KnowledgeStore] here — which is where they already live.
 *
 * @param resolveElement supplies the typed record; returns null before the agent has loaded, in
 *   which case the host simply hides the card rather than showing an empty frame.
 * @param strings resolved per bind, like everything else here. It used to be passed by value, and
 *   the only host builds the binder before `initialize()` has run — so it was always null, and the
 *   two kinds that need it, the abundance profile and the hazard diamond, could never bind at all.
 *   Reading it per bind also means a card labels itself in the language the conversation switched
 *   to, rather than the one the panel was opened in.
 */
class ChatCardBinder(
    private val resolveElement: (String) -> ElementRecord?,
    private val strings: () -> StringProvider?,
    private val store: () -> KnowledgeStore?
) {

    /** @return false when the card cannot be filled, so the caller hides its host. */
    fun bind(cardView: View, card: ChatCard): Boolean {
        val element = resolveElement(card.elementKey) ?: return false
        return when (card.kind) {
            ChatCardKind.ELECTRON_SHELL -> {
                val shells = (element.value("electron_shells")
                        as? com.jlindemann.science.ai.data.FieldValue.Text)?.raw ?: return false
                cardView.findViewById<ElectronShellView>(R.id.ai_card_shell)
                    ?.setShellData(shells, element.symbol) != null
            }
            ChatCardKind.CRYSTAL_STRUCTURE -> {
                val raw = when (val value = element.value("crystal_structure")) {
                    is com.jlindemann.science.ai.data.FieldValue.Enum -> value.localized
                    is com.jlindemann.science.ai.data.FieldValue.Text -> value.raw
                    else -> null
                } ?: return false
                val view = cardView.findViewById<CrystalStructureView>(R.id.ai_card_crystal)
                    ?: return false
                view.autoRotate = false
                view.interactive = false
                view.crystalSystem = raw
                true
            }
            ChatCardKind.POISSON_BAND -> {
                val knowledge = store() ?: return false
                val band = PoissonBandReducer.of(element, knowledge) ?: return false
                cardView.findViewById<PoissonBandView>(R.id.ai_card_poisson)?.setBand(band) != null
            }
            ChatCardKind.IONIZATION_SERIES -> {
                val series = IonizationSeriesReducer.of(element) ?: return false
                cardView.findViewById<IonizationSeriesView>(R.id.ai_card_ionization)
                    ?.setSeries(series) != null
            }
            ChatCardKind.ISOTOPE_DECAY -> {
                val series = IsotopeSeriesReducer.of(element) ?: return false
                cardView.findViewById<IsotopeDecayView>(R.id.ai_card_isotope)?.setSeries(series) != null
            }
            ChatCardKind.ABUNDANCE -> {
                val provider = strings() ?: return false
                val profile = AbundanceReducer.of(element, provider) ?: return false
                cardView.findViewById<AbundanceBarsView>(R.id.ai_card_abundance)
                    ?.setProfile(profile) != null
            }
            ChatCardKind.NFPA_DIAMOND -> {
                val provider = strings() ?: return false
                val nfpa = element.nfpa?.takeIf { NfpaLabeller.hasAnyRating(it) } ?: return false
                NfpaDiamondBinder.bindDiamond(cardView, NfpaLabeller.labels(nfpa, provider))
                true
            }
            ChatCardKind.EMISSION_SPECTRUM -> {
                val image = cardView.findViewById<ImageView>(R.id.ai_card_emission) ?: return false
                Picasso.get().load(EmissionSpectrumUrl.of(element.symbol)).into(image)
                true
            }
        }
    }

    /**
     * Release anything the card holds before its row is reused.
     *
     * Two things genuinely matter here: a pooled `CrystalStructureView` would otherwise keep its
     * animator running for a row that is no longer on screen, and an in-flight Picasso request would
     * land on whatever message the recycled row now shows.
     */
    fun unbind(cardView: View?) {
        if (cardView == null) return
        cardView.findViewById<CrystalStructureView>(R.id.ai_card_crystal)?.autoRotate = false
        cardView.findViewById<ImageView>(R.id.ai_card_emission)?.let { Picasso.get().cancelRequest(it) }
    }
}
