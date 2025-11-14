#!/usr/bin/env python3
"""
Fix Urdu file - Translate all English element names, groups, appearances, and phases to Urdu.
"""

import json
import re

# Load Urdu elements file
with open('app/src/main/assets/elements_ur.json', 'r', encoding='utf-8') as f:
    ur_data = json.load(f)

# Urdu translations for element names
elements_ur = {
    "Hydrogen": "ہائیڈروجن",
    "Helium": "ہیلیم",
    "Lithium": "لیتھیم",
    "Beryllium": "بیریلیم",
    "Boron": "بوران",
    "Carbon": "کاربن",
    "Nitrogen": "نائٹروجن",
    "Oxygen": "آکسیجن",
    "Fluorine": "فلورین",
    "Neon": "نیون",
    "Sodium": "سوڈیم",
    "Magnesium": "میگنیشیم",
    "Aluminium": "ایلومینیم",
    "Silicon": "سلیکون",
    "Phosphorus": "فاسفورس",
    "Sulfur": "سلفر",
    "Chlorine": "کلورین",
    "Argon": "آرگون",
    "Potassium": "پوٹاشیم",
    "Calcium": "کیلشیم",
    "Scandium": "سکینڈیم",
    "Titanium": "ٹائٹینیم",
    "Vanadium": "وینیڈیم",
    "Chromium": "کرومیم",
    "Manganese": "مینگنیز",
    "Iron": "آئرن",
    "Cobalt": "کوبالٹ",
    "Nickel": "نکل",
    "Copper": "تانبا",
    "Zinc": "زنک",
    "Gallium": "گیلیم",
    "Germanium": "جرمینیم",
    "Arsenic": "آرسینک",
    "Selenium": "سیلینیم",
    "Bromine": "برومین",
    "Krypton": "کرپٹون",
    "Rubidium": "روبیڈیم",
    "Strontium": "سٹرونشیم",
    "Yttrium": "یٹریم",
    "Zirconium": "زرکونیم",
    "Niobium": "نیوبیم",
    "Molybdenum": "مولبڈینم",
    "Technetium": "ٹیکنیشیم",
    "Ruthenium": "روتھینیم",
    "Rhodium": "روڈیم",
    "Palladium": "پیلیڈیم",
    "Silver": "چاندی",
    "Cadmium": "کیڈمیم",
    "Indium": "انڈیم",
    "Tin": "ٹن",
    "Antimony": "اینٹیمنی",
    "Tellurium": "ٹیلوریم",
    "Iodine": "آئوڈین",
    "Xenon": "زینون",
    "Caesium": "سیزیم",
    "Barium": "بیریم",
    "Lanthanum": "لینتھینم",
    "Cerium": "سیریم",
    "Praseodymium": "پریسیوڈیمیم",
    "Neodymium": "نیوڈیمیم",
    "Promethium": "پرومیتھیم",
    "Samarium": "سماریم",
    "Europium": "یوروپیم",
    "Gadolinium": "گیڈولینیم",
    "Terbium": "ٹربیم",
    "Dysprosium": "ڈیسپروزیم",
    "Holmium": "ہولمیم",
    "Erbium": "اربیم",
    "Thulium": "تھولیم",
    "Ytterbium": "یٹربیم",
    "Lutetium": "لوٹیشیم",
    "Hafnium": "ہافنیم",
    "Tantalum": "ٹینٹلم",
    "Tungsten": "ٹنگسٹن",
    "Rhenium": "رینیم",
    "Osmium": "آزمیم",
    "Iridium": "ایریڈیم",
    "Platinum": "پلاٹینم",
    "Gold": "سونا",
    "Mercury": "پارہ",
    "Thallium": "تھیلیم",
    "Lead": "سیسہ",
    "Bismuth": "بسمتھ",
    "Polonium": "پولونیم",
    "Astatine": "ایسٹاٹین",
    "Radon": "ریڈون",
    "Francium": "فرانسیم",
    "Radium": "ریڈیم",
    "Actinium": "ایکٹینیم",
    "Thorium": "تھوریم",
    "Protactinium": "پروٹیکٹینیم",
    "Uranium": "یورینیم",
    "Neptunium": "نیپچونیم",
    "Plutonium": "پلوٹونیم",
    "Americium": "امریکیم",
    "Curium": "کیوریم",
    "Berkelium": "برکیلیم",
    "Californium": "کیلیفورنیم",
    "Einsteinium": "آئن سٹائینیم",
    "Fermium": "فرمیم",
    "Mendelevium": "مینڈیلیویم",
    "Nobelium": "نوبیلیم",
    "Lawrencium": "لارنسیم",
    "Rutherfordium": "رودرفورڈیم",
    "Dubnium": "ڈبنیم",
    "Seaborgium": "سیبورگیم",
    "Bohrium": "بوہریم",
    "Hassium": "ہاسیم",
    "Meitnerium": "میٹنیریم",
    "Darmstadtium": "ڈارمسٹیڈیم",
    "Roentgenium": "رونٹجینیم",
    "Copernicium": "کوپرنیکیم",
    "Nihonium": "نیہونیم",
    "Flerovium": "فلیرویم",
    "Moscovium": "ماسکویم",
    "Livermorium": "لیورموریم",
    "Tennessine": "ٹینیسین",
    "Oganesson": "اوگانیسون",
}

# Element groups in Urdu
groups_ur = {
    "Actinide": "ایکٹینائیڈ",
    "Actinides": "ایکٹینائیڈز",
    "Alkali Metals": "الکلی دھاتیں",
    "Alkaline Earth Metals": "الکلائن ارتھ میٹلز",
    "Halogens": "ہالوجن",
    "Lanthanoid": "لینتھانائیڈ",
    "Lanthanoids": "لینتھانائیڈز",
    "Noble Gases": "نوبل گیسیں",
    "Other Nonmetals": "دیگر غیر دھاتیں",
    "Post-Transition Metals": "پوسٹ ٹرانزیشن میٹلز",
    "Transition Metals": "ٹرانزیشن میٹلز",
}

# Phases in Urdu
phases_ur = {
    "Solid": "ٹھوس",
    "Liquid": "مائع",
    "Gas": "گیس",
}

# Common appearance terms
def translate_appearance(text):
    """Translate appearance descriptions to Urdu."""
    if text == "---":
        return "---"
    
    # Common translations
    replacements = {
        "Silvery white, blue glow": "چاندی سفید، نیلی چمک",
        "Silvery white": "چاندی سفید",
        "Silvery White": "چاندی سفید",
        "Silvery Metallic": "چاندی دھاتی",
        "Silvery": "چاندی جیسا",
        "Colorless Gas": "بے رنگ گیس",
        "Colorless": "بے رنگ",
        "Yellow": "پیلا",
        "Red": "سرخ",
        "Reddish- Brown": "سرخی مائل بھورا",
        "Reddish-Brown": "سرخی مائل بھورا",
        "Gray": "سرمئی",
        "Grey": "سرمئی",
        "Silvery White or Gray": "چاندی سفید یا سرمئی",
        "unknown, probably metallic": "نامعلوم، شاید دھاتی",
        "Pale yellow Gas": "ہلکا پیلا گیس",
        "Pale yellow": "ہلکا پیلا",
        "Blue": "نیلا",
        "Silver": "چاندی",
        "Black": "سیاہ",
        "White": "سفید",
        "Lustrous gray": "چمکدار سرمئی",
        "Metallic": "دھاتی",
        "Copper red": "تانبے کا سرخ",
    }
    
    # Try exact match first
    if text in replacements:
        return replacements[text]
    
    # Try partial replacements
    result = text
    for eng, ur in replacements.items():
        result = result.replace(eng, ur)
    
    # Remove English labels if still present
    result = re.sub(r'\s*\(ظاہری شکل\)\s*', '', result)
    
    return result if result != text else text

# Update all elements
updated_count = 0
for elem_key, elem_data in ur_data.items():
    # Translate element name
    if 'element' in elem_data:
        old_val = elem_data['element']
        # Extract English name
        match = re.match(r'^([A-Za-z\s]+)', old_val)
        if match:
            eng_name = match.group(1).strip()
            if eng_name in elements_ur:
                elem_data['element'] = elements_ur[eng_name]
                updated_count += 1
    
    # Translate element_group
    if 'element_group' in elem_data:
        old_val = elem_data['element_group']
        match = re.match(r'^([A-Za-z\s\-]+)', old_val)
        if match:
            eng_group = match.group(1).strip()
            if eng_group in groups_ur:
                elem_data['element_group'] = groups_ur[eng_group]
                updated_count += 1
    
    # Translate element_appearance
    if 'element_appearance' in elem_data:
        old_val = elem_data['element_appearance']
        # Remove Urdu label suffix if present
        eng_appearance = re.sub(r'\s*\(ظاہری شکل\)\s*$', '', old_val)
        translated = translate_appearance(eng_appearance)
        if translated != old_val:
            elem_data['element_appearance'] = translated
            updated_count += 1
    
    # Translate element_phase
    if 'element_phase' in elem_data:
        old_val = elem_data['element_phase']
        match = re.match(r'^([A-Za-z]+)', old_val)
        if match:
            eng_phase = match.group(1).strip()
            if eng_phase in phases_ur:
                elem_data['element_phase'] = phases_ur[eng_phase]
                updated_count += 1

# Save updated file
with open('app/src/main/assets/elements_ur.json', 'w', encoding='utf-8') as f:
    json.dump(ur_data, f, ensure_ascii=False, indent=2)

print(f"✅ Urdu file updated!")
print(f"Total field updates: {updated_count}")
print(f"\nSample element (hydrogen):")
h = ur_data['hydrogen']
print(f"  element: {h.get('element', 'N/A')}")
print(f"  element_group: {h.get('element_group', 'N/A')}")
print(f"  element_appearance: {h.get('element_appearance', 'N/A')}")
print(f"  element_phase: {h.get('element_phase', 'N/A')}")
