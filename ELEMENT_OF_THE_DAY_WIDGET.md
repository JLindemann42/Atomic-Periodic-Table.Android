# Element of the Day Widget

## Overview
The Element of the Day widget displays a different chemical element each day on the home screen. It cycles through all 118 elements in the periodic table based on the day of the year.

## Features
- **Daily Element**: Shows a different element each day (Day 1 = Hydrogen, Day 2 = Helium, etc.)
- **Element Information**: Displays:
  - Element symbol (large, prominent)
  - Element name (localized)
  - Atomic number
  - Scrollable description
- **Interactive**: Clicking the widget opens the app and navigates directly to the element's detail page
- **Adaptive Design**: 
  - Matches app theme with rounded corners
  - Supports Material You design on Android 12+ with system accent colors
  - Supports light/dark themes

## Implementation Details

### Files Added
- `ElementOfTheDayWidget.kt`: Widget provider class
- `layout/element_of_the_day_widget.xml`: Widget layout for Android <12
- `layout-v31/element_of_the_day_widget.xml`: Widget layout for Android 12+ with Material You
- `xml/element_of_the_day_widget_info.xml`: Widget configuration

### Files Modified
- `AndroidManifest.xml`: Added widget receiver declaration
- `MainActivity.kt`: Added handler for widget intent to open specific element
- `strings.xml`: Added widget-related string resources

### Widget Configuration
- **Update Period**: 24 hours (86400000 milliseconds)
- **Minimum Size**: 250dp x 180dp
- **Target Size**: 4 cells wide x 3 cells high
- **Resize Mode**: Both horizontal and vertical

### Element Selection Algorithm
The widget uses the day of year (1-365/366) to select an element:
```kotlin
val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
val index = (dayOfYear - 1) % 118  // Cycles through all 118 elements
val elementKey = ELEMENT_KEYS[index]
```

## Usage
1. Long-press on the home screen
2. Select "Widgets"
3. Find "Element of the Day" widget
4. Drag and drop onto home screen
5. The widget will automatically show today's element
6. Click the widget to view detailed information about the element in the app

## Technical Notes
- Element data is loaded from localized JSON files using `ElementDataLoader`
- Widget updates are handled by the Android system based on the update period
- Coroutines are used for asynchronous data loading to avoid blocking the UI
- PendingIntent is properly configured for Android 12+ with immutable flag
