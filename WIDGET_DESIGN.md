# Element of the Day Widget - Visual Design

## Widget Appearance

The Element of the Day widget has a clean, material design that matches the app's aesthetic:

```
╔════════════════════════════════════════╗
║     Element of the Day                 ║
║                                        ║
║   H              Hydrogen              ║
║                  Atomic Number: 1      ║
║                                        ║
║ ────────────────────────────────────── ║
║                                        ║
║ Hydrogen is a chemical element with    ║
║ the symbol H and atomic number 1.      ║
║ With a standard atomic weight of       ║
║ 1.008, hydrogen is the lightest       ║
║ element in the periodic table.         ║
║ Hydrogen is the most abundant...       ║
║ (scrollable)                           ║
║                                        ║
╚════════════════════════════════════════╝
```

## Design Elements

### Color Scheme
- **Background**: Rounded corners (28dp radius) with app's chip surface color
- **Android 12+**: Uses system accent colors (Material You)
  - Background: `system_accent1_100`
  - Symbol: `system_accent1_600` (large, prominent)
  - Text: `system_accent2_800` and `system_accent2_700`
- **Pre-Android 12**: Uses app's theme colors
  - Adapts to light/dark theme automatically

### Layout Structure
1. **Title Bar** (top)
   - "Element of the Day" text
   - Centered, bold, 14sp
   - Color: Theme accent text

2. **Element Information** (middle)
   - **Left**: Large element symbol (48sp, bold)
     - Minimum width: 80dp
     - Centered in container
   - **Right**: Element details
     - Element name (20sp, bold)
     - Atomic number (14sp, secondary text)

3. **Divider** (horizontal line)
   - Subtle separator with opacity
   - 1dp height

4. **Description** (bottom, expandable)
   - Scrollable text area
   - 12sp font size
   - 1.2 line spacing for readability
   - Full element description from JSON

### Interactive Behavior
- **On Click**: Opens app and navigates to element detail page
- **Visual Feedback**: Ripple effect on tap
- **Update**: Refreshes daily at midnight

## Size and Placement
- **Minimum Size**: 250dp × 180dp
- **Recommended Size**: 4 cells wide × 3 cells high
- **Resizable**: Can be resized horizontally and vertically
- **Placement**: Home screen or lock screen (Android 12+)

## Theming
The widget automatically adapts to:
- ✅ Light/Dark theme
- ✅ Material You (Android 12+) with dynamic colors
- ✅ App's color scheme on older Android versions
- ✅ User's language for element names and descriptions

## Daily Rotation Schedule
- Day 1 (Jan 1): Hydrogen (H, #1)
- Day 2 (Jan 2): Helium (He, #2)
- Day 3 (Jan 3): Lithium (Li, #3)
- ...continuing through...
- Day 118: Oganesson (Og, #118)
- Day 119: Cycles back to Hydrogen
- And so on throughout the year

Each element gets 3-4 days per year (365 days ÷ 118 elements ≈ 3.1 days each).
