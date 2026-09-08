---
name: Horizon Glass & Precision
colors:
  surface: '#fcf8fb'
  surface-dim: '#dcd9dc'
  surface-bright: '#fcf8fb'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f6f3f5'
  surface-container: '#f0edef'
  surface-container-high: '#eae7ea'
  surface-container-highest: '#e4e2e4'
  on-surface: '#1b1b1d'
  on-surface-variant: '#414755'
  inverse-surface: '#303032'
  inverse-on-surface: '#f3f0f2'
  outline: '#717786'
  outline-variant: '#c1c6d7'
  surface-tint: '#005bc1'
  primary: '#0058bc'
  on-primary: '#ffffff'
  primary-container: '#0070eb'
  on-primary-container: '#fefcff'
  inverse-primary: '#adc6ff'
  secondary: '#5d5e63'
  on-secondary: '#ffffff'
  secondary-container: '#e0dfe4'
  on-secondary-container: '#626267'
  tertiary: '#8a2bb9'
  on-tertiary: '#ffffff'
  tertiary-container: '#a649d5'
  on-tertiary-container: '#fffbff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d8e2ff'
  primary-fixed-dim: '#adc6ff'
  on-primary-fixed: '#001a41'
  on-primary-fixed-variant: '#004493'
  secondary-fixed: '#e3e2e7'
  secondary-fixed-dim: '#c6c6cb'
  on-secondary-fixed: '#1a1b1f'
  on-secondary-fixed-variant: '#46464b'
  tertiary-fixed: '#f6d9ff'
  tertiary-fixed-dim: '#e8b3ff'
  on-tertiary-fixed: '#310048'
  on-tertiary-fixed-variant: '#7201a2'
  background: '#fcf8fb'
  on-background: '#1b1b1d'
  surface-variant: '#e4e2e4'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 34px
    fontWeight: '700'
    lineHeight: 41px
    letterSpacing: 0.37px
  display-md:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 34px
    letterSpacing: 0.36px
  title-lg:
    fontFamily: Inter
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.26px
  title-md:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 25px
    letterSpacing: -0.45px
  title-sm:
    fontFamily: Inter
    fontSize: 17px
    fontWeight: '600'
    lineHeight: 22px
    letterSpacing: -0.43px
  body-lg:
    fontFamily: Inter
    fontSize: 17px
    fontWeight: '400'
    lineHeight: 22px
    letterSpacing: -0.43px
  body-md:
    fontFamily: Inter
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: -0.23px
  label-md:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 18px
    letterSpacing: -0.08px
  label-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0px
  caption:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 13px
    letterSpacing: 0.06px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  space-2xs: 0.125rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-base: 1rem
  space-lg: 1.25rem
  space-xl: 1.5rem
  space-2xl: 2rem
  space-3xl: 2.5rem
  safe-area-bottom: 2.125rem
  toolbar-height: 2.75rem
  pill-height: 3rem
---

## Brand & Style
The design system embodies the tactile sophistication, physical depth, and fluid motion of modern Apple interfaces. It merges iOS Human Interface Guidelines with hyper-refined optical translucency, delivering an experience that feels deeply integrated into native hardware.

### Design Movements
- **Frosted Glass & Vibrancy (Apple Materials):** Translucent multi-layered surfaces with dynamic background blurring, light-channeling inner bevels, and sub-pixel edge highlights.
- **Thumb-Centric Ergonomics:** Bottom-anchored interactions, fluid drag interactions, and gesture-driven sheets tailored for one-handed reachability.
- **Tactile Restraint:** Clean, unadorned surfaces that defer entirely to web content, activating dimensional presence through blur, scale, and subtle specular lighting only when interacting with chrome.

## Colors
The palette utilizes authentic Apple system values, engineered to work across standard browsing and Private Browsing contexts.

### Color Tokens & Roles
- **System Accent (Primary):** `#007AFF` (Light mode links, active selection states, tab indications) / `#0A84FF` (Dark mode dynamic glow variant).
- **Secondary (System Gray):** `#8E8E93` for structural icons, inactive indicators, and unfocused text.
- **Tertiary (Private Mode Accent):** `#AF52DE` used exclusively for Private Browsing tabs, security indicators, and private session bottom sheet headers.
- **Neutral Surface & Base:**
  - Standard Light: `#F5F5F7` canvas, `#FFFFFF` opaque cards, `rgba(255, 255, 255, 0.72)` active material.
  - Standard Dark: `#000000` canvas, `#1C1C1E` elevated cards, `#2C2C2E` secondary wells, `rgba(30, 30, 30, 0.75)` dark material.
- **Dividers & Borders:**
  - Light mode: Hairline separators use `rgba(60, 60, 67, 0.18)`. Glass perimeter borders use `rgba(0, 0, 0, 0.06)`.
  - Dark mode: Hairline separators use `rgba(84, 84, 88, 0.36)`. Inner perimeter rims use `rgba(255, 255, 255, 0.12)`.

## Typography
Typographic hierarchy follows standard iOS optical scaling principles. 

- Large titles prioritize high-density layout integration with negative tracking for tighter lockups.
- Body and label elements utilize slightly expanded tracking to maintain legibility when rendered over translucent, shifting background content.
- Address bar URL display maps to `body-md` with secure protocols (HTTPS lock icon) and domain origins rendered in weight `600`, while extended paths downgrade to weight `400` with `60%` opacity.

## Layout & Spacing
The layout adheres to dynamic safe area insets and strict iOS screen zones.

- **Mobile Viewport Grid:** Fluid edge-to-edge layout bounded by `16px` horizontal page margins on small phones (< 390px) and `20px` on larger devices.
- **Thumb Zone Structure:** Core interactive elements sit within the bottom `120px` of the viewport. Floating address bars hover `12px` above the bottom safe area (`env(safe-area-inset-bottom)`), collapsing fluidly to an ultra-compact `44px` bar on downward scroll.
- **Favorites & Tabs Grid:** Standard start page uses a 4-column layout on portrait mobile, reflowing to a 6-column grid on tablet and landscape orientations with uniform `16px` gutters.

## Elevation & Depth
Depth is created through optical material density and backdrop refraction rather than deep shadows.

### Material Tiers
- **Ultra-Thin Material (Overlay sheets, menus):** `backdrop-filter: blur(20px) saturate(190%)`, surface fill `rgba(255, 255, 255, 0.65)` (Light) or `rgba(28, 28, 30, 0.65)` (Dark).
- **Regular Glass Material (Floating bottom pill bar, navigation overlays):** `backdrop-filter: blur(30px) saturate(210%)`, surface fill `rgba(255, 255, 255, 0.78)` (Light) or `rgba(32, 32, 35, 0.82)` (Dark).
- **Inner Rims & Edges:** Elements utilize a `1px` inner box shadow (`inset 0 0 0 0.5px rgba(255, 255, 255, 0.45)` in light mode, `inset 0 0 0 0.5px rgba(255, 255, 255, 0.15)` in dark mode) to simulate the physical edge highlights of ground optical glass.

### Shadows
- **Floating Bar Shadow:** `0 8px 24px -4px rgba(0, 0, 0, 0.08), 0 2px 6px 0 rgba(0, 0, 0, 0.04)`.
- **Sheet Modal Shadow:** `0 -12px 32px 0 rgba(0, 0, 0, 0.12)`.

## Shapes
Shapes employ continuous Apple squircle corner smoothing (`corner-smoothing: 60%` or continuous SVG curves):

- **Floating Address / Action Pill:** Fully rounded caps (`9999px`).
- **Cards, Favorites & Grid Items:** `18px` to `22px` radii.
- **Bottom Drawers & Context Sheets:** `32px` on top corners, zeroed at screen bottom.
- **Segmented Controls & Embedded Input Fields:** `10px` to `12px` radii.

## Components

### Floating Address Bar (Pill)
- **Geometry:** Height of `48px`, margins of `16px` on lateral edges, fully rounded capsule.
- **Visuals:** Regular Material (`backdrop-filter: blur(30px)`), `1px` translucent inner highlight, centered text with website favicon and privacy padlock.
- **Scroll Behavior:** Smoothly transitions between expanded `48px` interactive state and collapsed `36px` passive tracking pill at viewport bottom.

### Segmented Controls
- **Track:** Height `32px`, `rgba(120, 120, 128, 0.12)` fill, `8px` corner radius, `2px` internal padding.
- **Thumb:** White `#FFFFFF` in light mode, `#636366` in dark mode, `6px` corner radius, `0 3px 8px rgba(0,0,0,0.12)` ambient drop shadow.

### iOS Switches
- **Dimensions:** Width `51px`, Height `31px`, fully rounded pill track.
- **States:** Inactive track `rgba(120, 120, 128, 0.2)`. Active track `#34C759` (or `#007AFF` for system settings, `#AF52DE` in private browsing). Circle knob `27px` solid white with `0 3px 8px rgba(0,0,0,0.15)` elevation.

### Favorites Icon Tile
- **Dimensions:** `60x60px` square container with `16px` continuous rounded corners.
- **Material:** Light mode uses `#FFFFFF` with `0 2px 8px rgba(0,0,0,0.06)`; Dark mode uses `rgba(255, 255, 255, 0.1)`.
- **Label:** `caption` typography centered underneath with `6px` top spacing, clamped to a single truncated line.

### Bottom Modal Sheets
- **Geometry:** Bounded to safe area top (`10px` gap below dynamic island / status bar), `32px` top border radius.
- **Grabber:** Centered drag indicator `36px` wide by `5px` tall, `rgba(60, 60, 67, 0.3)` background, `5px` radius, positioned `8px` from top edge.
- **Background:** Ultra-Thin Material over dimming backdrop layer (`rgba(0, 0, 0, 0.3)`).