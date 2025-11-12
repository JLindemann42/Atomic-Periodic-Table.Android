#!/usr/bin/env python3
"""
Add Swedish translations to the project.
"""
import os
import sys
import xml.etree.ElementTree as ET

# Swedish translations
SWEDISH_TRANSLATIONS = {
    "kelvin": "Kelvin",
    "celsius": "Celsius",
    "fahrenheit": "Fahrenheit",
    "element_description_title": "Beskrivning",
    "favorite_settings_general": "Allmänt",
    "favorite_settings_nuclear": "Nukle är",
    "sources_settings_title": "Källor",
    "sources_wwe_text": "Mackenzie L. Davis, (2019). Water and wastewater Engineering: Design Principles and Practise, second edition. ISBN: 978-1-260-13227-4",
    "isotopes_title": "Isotoper",
    "clear_cache_text": "cache_size",
    "submit_bug": "Bugg",
    "info_title": "Information",
    "git_title": "Github",
    "activity_ph_neutral": "Neutral",
    "wikipedia_license": "Wikipedia Commons",
    "sothree_license": "Android Sliding Up Panel",
    "calculator_formula": "Formel",
    "short_voltage": "Spänning",
    "bug": "Bugg",
    "no_results": "Inga resultat",
    "additional_data_item": "- Ytterligare data:",
    "additional_flashcards_games": "Ytterligare kortspel:",
    "additional_properties_requires_pro": "Ytterligare egenskaper kräver PRO-version. Skaffa PRO för att låsa upp mer data och tabeller:",
    "additional_tables_item": "- Ytterligare tabeller:",
    "all_core_features": "Alla kärnfunktioner",
    "and_more_item": "- Och mer",
    "appearance_colon": "Utseende:",
    "atomic_mass_colon": "Atommassa:",
    "atomic_number_label": "Atomnummer:",
    "atomic_radius_calculated_colon": "Atomradie (beräknad):",
    "atomic_radius_empirical_colon": "Atomradie (empirisk):",
    "atomic_weight_relative_atomic_mass": "Atomvikt (relativ atommassa):",
    "balance_equation": "Balansera ekvation:",
    "balance_equation_colon": "Balansera ekvation:",
    "block_colon": "Block:",
    "boiling_point_colon": "Kokpunkt:",
    "brinell_hardness_colon": "Brinellhårdhet:",
    "calculations": "Beräkningar",
    "calculations_stat": "Beräkningar",
    "card_order": "Kortordning",
    "color_label": "Färg: ",
    "constants_table_label": "Konstanttabell:",
    "correct": "Korrekt",
    "correct_answer": "Rätt svar: %s",
    "cristal_structure_label": "Kristallstruktur: ",
    "currently_no_favorites": "Inga favoriter för närvarande",
    "density_colon": "Densitet:",
    "density_label": "Densitet: ",
    "electron_shell_colon": "Elektronskal:",
    "electronegativity_colon": "Elektronegativitet:",
    "elements_included_will_be_shown_here": "Inkluderade element visas här",
    "elements_opened": "Öppnade element",
    "emission_spectrum_colon": "Emissionsspektrum:",
    "enable_zoom_in_tables": "Aktivera zoom i tabeller",
    "experimental": "Experimentell",
    "favorites_colon": "Favoriter:",
    "fifty_lives_in_flashcards": "50 liv i flashcards:",
    "finished_game_xp": "Spelet avslutat: +%dxp",
    "formula_will_be_displayed_here": "Formeln visas här",
    "fusion_heat_colon": "Fusionsvärme:",
    "game_results": "Spelresultat",
    "get_pro_button": "Skaffa PRO",
    "get_pro_plus_button": "Skaffa PRO+",
    "go_online_for_emission_lines": "Gå online för emissionslinjer",
    "go_pro_more_features": "Gå Pro - fler funktioner",
    "go_pro_school_start_sale": "GÅ PRO - SKOLSTART-REA",
    "group_label": "Grupp: ",
    "hardness_label": "Hårdhet: ",
    "isotopes_table_description": "En tabell med isotoper för olika grundämnen samt halveringstid, massa, proton, neutron och nukleondata.",
    "level_x_y": "Nivå X-Y",
    "lives_label": "Liv: %s",
    "lives_lost_detail": "Förlorade liv: %1$d (%2$d per fel/timeout-svar)",
    "lives_unlimited": "Liv: ∞",
    "magnetism_label": "Magnetism: ",
    "melting_point_colon": "Smältpunkt:",
    "most_advanced": "Mest avancerad",
    "new_label": "NYTT",
    "next_life_in": "Nästa liv om %1$d minuter och %2$d sekunder.\nDu får %3$d liv%4$s.",
    "no_image": "Ingen bild",
    "non_pro": "ICKE-PRO",
    "option_placeholder": "Alternativ %d",
    "out_of_lives_hours": "Slut på liv! Fler liv om %1$d timmar, %2$d minuter och %3$d sekunder.",
    "out_of_lives_minutes": "Slut på liv! Fler liv om %1$d minuter och %2$d sekunder.",
    "perfect_all_correct_xp": "Perfekt (Allt rätt): +%dxp",
    "phase_stp_colon": "Fas (STP):",
    "pro_plus_rewards": "PRO+ Belöningar: %s",
    "pro_plus_user": "PRO+ ANVÄNDARE",
    "pro_plus_version": "PRO+ Version",
    "pro_user": "PRO ANVÄNDARE",
    "pro_version": "PRO Version",
    "progress_xp": "%1$d/%2$d",
    "question_text": "Frågetext",
    "questions_correct_xp": "Rätta frågor: +%dxp",
    "rate": "Betygsätt",
    "rate_app": "Betygsätt app",
    "rate_app_description": "Låt oss veta vad du tycker om appen genom att betygsätta den på Play Store!",
    "reaction_balancer": "Reaktionsbalanserare:",
    "score_summary": "Poäng: %1$d/%2$d",
    "searches": "Sökningar",
    "solubility_table": "Löslighetstabell",
    "specific_heat_capacity_colon": "Specifik värmekapacitet:",
    "streak_label": "Svit: ",
    "time_up": "Tiden ute",
    "tools": "Verktyg",
    "total_xp_stat": "Total XP: %d",
    "type_label": "Typ: ",
    "unit_conversion_format": "%1$s %2$s → %3$s %4$s (%5$s)",
    "unlock_advanced_data_and_additional_flashcards": "Lås upp avancerad data och ytterligare flashcards.",
    "unlock_advanced_data_and_extra_atomic_content": "Lås upp avancerad data och extra atomärt innehåll.",
    "usage_statistics": "Användningsstatistik",
    "vaporization_heat_colon": "Förångningsvärme:",
    "wrong": "Fel",
    "xp_bonus": "+%d%% bonus xp",
    "you_have_full_lives": "Du har fulla liv!",
    "your_answer": "Ditt svar: %s",
    "achievement_reached_prefix": "Prestation uppnådd: ",
}

# Strings that should not be translated
SKIP_STRINGS = [
    'bluesky', 'instagram', 'facebook', 'homepage', 'about_author_name',
    'mackenzie_l_davis', 'water_and_wastewater_engineering',
    'sothree_android_sliding_up_panel', 'wikipedia_commons',
    'credits_giancarlo', 'credits_electro_boy', 'app_name',
    'web_client_id', 'pro_price_discount', 'blog'
]

def apply_swedish_translations(repo_path):
    """Apply Swedish translations to string resource files."""
    print("\nApplying Swedish translations...")
    
    res_path = os.path.join(repo_path, "app/src/main/res")
    
    # Read English strings as reference
    en_strings_path = os.path.join(res_path, "values/strings.xml")
    tree = ET.parse(en_strings_path)
    root = tree.getroot()
    en_strings = {}
    for string_elem in root.findall('string'):
        name = string_elem.get('name')
        translatable = string_elem.get('translatable')
        if translatable != 'false':
            en_strings[name] = string_elem.text if string_elem.text else ''
    
    # Process Swedish translation file
    strings_file = os.path.join(res_path, "values-sv-rSE", "strings.xml")
    if not os.path.exists(strings_file):
        print("Swedish translation file not found!")
        return
    
    # Parse existing file
    tree = ET.parse(strings_file)
    root = tree.getroot()
    
    trans_count = 0
    
    # Apply translations
    for string_elem in root.findall('string'):
        name = string_elem.get('name')
        current_text = string_elem.text if string_elem.text else ''
        
        # Skip if not in English strings or should not be translated
        if name not in en_strings or name in SKIP_STRINGS:
            continue
        
        # Check if needs translation and we have a translation for it
        if current_text == en_strings[name] and name in SWEDISH_TRANSLATIONS:
            string_elem.text = SWEDISH_TRANSLATIONS[name]
            trans_count += 1
            print(f"  ✓ {name}")
    
    # Save updated file
    tree.write(strings_file, encoding='utf-8', xml_declaration=True)
    print(f"\nSwedish: Translated {trans_count} strings")

def main():
    """Main function."""
    if len(sys.argv) > 1:
        repo_path = sys.argv[1]
    else:
        repo_path = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    
    apply_swedish_translations(repo_path)
    print("\nSwedish translations complete!")

if __name__ == "__main__":
    main()
