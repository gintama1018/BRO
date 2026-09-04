---
name: Liquid System
colors:
  surface: '#f9f9ff'
  surface-dim: '#d3daea'
  surface-bright: '#f9f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f0f3ff'
  surface-container: '#e7eefe'
  surface-container-high: '#e2e8f8'
  surface-container-highest: '#dce2f3'
  on-surface: '#151c27'
  on-surface-variant: '#45464c'
  inverse-surface: '#2a313d'
  inverse-on-surface: '#ebf1ff'
  outline: '#76777d'
  outline-variant: '#c6c6cd'
  surface-tint: '#575e70'
  primary: '#000000'
  on-primary: '#ffffff'
  primary-container: '#141b2b'
  on-primary-container: '#7d8497'
  inverse-primary: '#c0c6db'
  secondary: '#006c49'
  on-secondary: '#ffffff'
  secondary-container: '#6cf8bb'
  on-secondary-container: '#00714d'
  tertiary: '#000000'
  on-tertiary: '#ffffff'
  tertiary-container: '#2a1700'
  on-tertiary-container: '#b87500'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dce2f7'
  primary-fixed-dim: '#c0c6db'
  on-primary-fixed: '#141b2b'
  on-primary-fixed-variant: '#404758'
  secondary-fixed: '#6ffbbe'
  secondary-fixed-dim: '#4edea3'
  on-secondary-fixed: '#002113'
  on-secondary-fixed-variant: '#005236'
  tertiary-fixed: '#ffddb8'
  tertiary-fixed-dim: '#ffb95f'
  on-tertiary-fixed: '#2a1700'
  on-tertiary-fixed-variant: '#653e00'
  background: '#f9f9ff'
  on-background: '#151c27'
  surface-variant: '#dce2f3'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 3.5rem
    fontWeight: '600'
    lineHeight: 4rem
    letterSpacing: -0.03em
  display-sm:
    fontFamily: Inter
    fontSize: 2.25rem
    fontWeight: '600'
    lineHeight: 2.75rem
    letterSpacing: -0.025em
  headline-lg:
    fontFamily: Inter
    fontSize: 1.5rem
    fontWeight: '600'
    lineHeight: 2rem
    letterSpacing: -0.02em
  headline-sm:
    fontFamily: Inter
    fontSize: 1.125rem
    fontWeight: '600'
    lineHeight: 1.5rem
    letterSpacing: -0.015em
  body-lg:
    fontFamily: Inter
    fontSize: 1rem
    fontWeight: '400'
    lineHeight: 1.5rem
    letterSpacing: -0.011em
  body-md:
    fontFamily: Inter
    fontSize: 0.875rem
    fontWeight: '400'
    lineHeight: 1.25rem
    letterSpacing: -0.006em
  body-sm:
    fontFamily: Inter
    fontSize: 0.8125rem
    fontWeight: '400'
    lineHeight: 1.125rem
    letterSpacing: 0em
  label-lg:
    fontFamily: Inter
    fontSize: 0.875rem
    fontWeight: '500'
    lineHeight: 1.25rem
    letterSpacing: -0.01em
  label-md:
    fontFamily: Inter
    fontSize: 0.75rem
    fontWeight: '500'
    lineHeight: 1rem
    letterSpacing: 0.01em
  label-sm:
    fontFamily: Inter
    fontSize: 0.6875rem
    fontWeight: '600'
    lineHeight: 0.875rem
    letterSpacing: 0.025em
  mono-code:
    fontFamily: JetBrains Mono
    fontSize: 0.75rem
    fontWeight: '400'
    lineHeight: 1rem
    letterSpacing: -0.01em
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
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
  chrome-height-desktop: 3rem
  chrome-height-mobile: 3.5rem
  island-margin-floating: 1rem
  gutter-desktop: 1.5rem
  gutter-mobile: 1rem
---

## Brand & Style

The design system projects absolute tranquility, crystalline clarity, and computational sovereignty. Built for a local-first, intelligence-augmented browser, the interface recedes to celebrate user content while projecting deliberate physical presence through translucent layers, light refraction, and precision mechanics. 

### Brand Personality & Philosophy
- **Quietly Intelligent:** Intelligence is expressed through instant utility, contextual awareness, and zero latency—never through decorative iridescent gradients, decorative sparkle icons, or futuristic tropes.
- **Sovereign & Secure:** Privacy is rendered as calm confidence rather than paranoid alerts. Visual affordances for security are restrained, authoritative, and tactfully integrated into core browser chrome.
- **Physical Optical Fidelity:** Taking direct cues from precision-milled hardware and fluid OS optics, the interface behaves like layered optical glass: light gathers at boundaries, surfaces exhibit subtle specular response, and content underneath blurs smoothly without visual noise.

### Style Architecture
The visual movement combines **Apple-grade restraint** with an advanced **Liquid Glass material system**. Surfaces employ high-density background filtration (`backdrop-filter: blur(24px) saturate(180%)`), layered interior specular highlights, sub-pixel hairline perimeter strokes, and floating floating-island structures that glide over web content.

## Colors

The palette is engineered around an architectural gallery concept: neutral off-whites, tinted silvers, and graphite typography create a whisper-quiet baseline, reserving chromatic saturation strictly for stateful verification, local model telemetry, and security clearance.

### Canvas & Base Materials
- **Canvas Base (`#F8F9FA`):** Soft, unpolluted off-white reflecting diffuse natural light.
- **Canvas Substrate (`#F2F4F7`):** Recessed silver neutral used for window framing, inactive tab troughs, and background split-views.
- **Glass Primary (`rgba(255, 255, 255, 0.72)`):** The default optical state for floating navigation bars, omnibox controls, and action pills with multi-pass blur.
- **Glass Elevated (`rgba(255, 255, 255, 0.88)`):** Opaque-leaning glass for sheets, modal overlays, context flyouts, and command palettes.
- **Glass Recessed (`rgba(0, 0, 0, 0.03)`):** Inset wells, address bar inputs, and passive track containers.

### Typography & Structure
- **Text Primary (`#111827`):** Deep graphite black with high optical density for razor-sharp legibility across high-DPI screens.
- **Text Secondary (`#4B5563`):** Neutral charcoal for subheaders, domain extensions, and secondary actions.
- **Text Tertiary (`#6B7280`):** Muted slate for metadata, local AI inference timestamps, and protocol badges.
- **Border Specular Light (`rgba(255, 255, 255, 0.60)`):** Top and leading edge inner highlights simulating rim lighting on cut glass.
- **Border Hairline Dark (`rgba(0, 0, 0, 0.06)`):** Structural boundary definition for translucent elements over pure-white web pages.

### Semantic Telemetry & Security
Security indicators avoid garish alarms, using refined botanical and mineral tones:
- **Verified / Local Safe (`#10B981`):** Muted emerald. Represents on-device sandboxing, encrypted tunnels, and authenticated identity.
- **Advisory / Suspicious (`#F59E0B`):** Warm solar amber. Denotes passive tracking attempts, unsigned certificates, or elevated resource draw.
- **Critical / Restricted (`#EF4444`):** Controlled crimson. Dictates hard threat intercepts, malicious script blocking, and network isolation.

## Typography

Typography prioritizes clarity, structural density, and micro-scale legibility. Driven by **Inter**, the type scale utilizes micro-tuned negative tracking on titles to emulate native Apple system typography (SF Pro), switching to neutral tracking for data-dense lists and URL strings.

### Hierarchy Guidelines
- **Optical Tracking:** Headers feature tight tracking (`-0.025em` to `-0.015em`) to unify word shapes into cohesive units. Technical readouts and badges feature slightly loosened tracking (`0.01em` to `0.025em`) to retain legibility at small sizes.
- **Numbers & Metrics:** Tabular figures (`tnum`) must be enforced for URL latency markers, shield counters, token counters, and download status bars.
- **Monospace Integration:** URL paths, cryptographic hashes, and local model parameter indicators leverage **JetBrains Mono** at optical small sizes (`0.75rem`), seamlessly tucked beside primary sans-serif labels.

## Layout & Spacing

The layout model is governed by an **island and canvas architecture**. Web content occupies a base viewport canvas, while browser chrome, local AI inspectors, and security shields float above as coordinated physical glass islands.

### Rhythm & Grid
- **Atomic 4px Metric Base:** All paddings, control heights, and spatial gaps conform to increments of 4px (`0.25rem`), prioritizing dense, ergonomic touch and cursor zones.
- **Floating Chrome Ergonomics:**
  - **Desktop:** The primary address and orchestration island floats detached from top window boundaries by `1rem`, anchored horizontally with dynamic centering up to a maximum width of `768px`. Side rails for local model contexts float with `0.75rem` perimeter clearance.
  - **Mobile:** The primary interaction bar docks permanently at the bottom thumb zone with `1rem` safe margin elevation, housing navigation, privacy status, and the intelligence trigger within an unbroken pill structure.
- **Responsive Adaptations:**
  - **Desktop (>= 1024px):** Dual-split canvas capable of running a 65/35 view (Web Viewport / Local Intelligence Workspace). Chrome auto-condenses into a compact glass pill on deep down-scroll.
  - **Tablet (768px - 1023px):** Collapsible sidebar collapses to floating vertical icon strip; search and security indicators condense to an interactive pill anchor.
  - **Mobile (< 768px):** Full edge-to-edge content presentation; top chrome holds minimal host indicators while bottom floating pill manages 100% of single-handed traversal.

## Elevation & Depth

Elevation eschews deep, muddy dropshadows in favor of compound micro-shadows, directional illumination, and dynamic optical backdrop filtration.

### The Glass Elevation Stack

1. **Level 0 (Base Canvas):** Flat, opaque canvas `#F8F9FA` with zero elevation.
2. **Level 1 (Subordinate Wells & Tabs):**
   - Background: `rgba(0, 0, 0, 0.03)` or `rgba(255, 255, 255, 0.5)`
   - Border: `1px solid rgba(0, 0, 0, 0.04)`
   - Shadow: None; recessed tactile sensation.
3. **Level 2 (Floating Islands & Chrome Pills):**
   - Background: `rgba(255, 255, 255, 0.72)`
   - Backdrop Filter: `blur(24px) saturate(180%)`
   - Border: Dual-boundary system. Outer border: `1px solid rgba(0, 0, 0, 0.06)`. Inner top shadow/highlight: `inset 0 1px 0 0 rgba(255, 255, 255, 0.6)`.
   - Shadow: `0 8px 32px -4px rgba(17, 24, 39, 0.06), 0 2px 8px -2px rgba(17, 24, 39, 0.04)`
4. **Level 3 (Sheets, Menus & Security Interstitials):**
   - Background: `rgba(255, 255, 255, 0.88)`
   - Backdrop Filter: `blur(32px) saturate(200%)`
   - Border: `1px solid rgba(0, 0, 0, 0.08)`, `inset 0 1px 0 0 rgba(255, 255, 255, 0.8)`
   - Shadow: `0 20px 48px -8px rgba(17, 24, 39, 0.10), 0 8px 16px -4px rgba(17, 24, 39, 0.04)`

## Shapes

The geometric signature is grounded in continuous curvature (superellipses / squircles) and soft capsule pills. Elements never present sharp, defensive vertices, reflecting an organic, approachable physical lens.

### Shape Geometry Rules
- **Full Pills (`rounded-full` / `9999px`):** Primary action buttons, security verification badges, floating navigation chrome, input containers, and tab chips.
- **Architectural Cards (`rounded-2xl` / `1rem` to `1.5rem`):** Local model inspector panels, permission prompt dialogs, download panels, and security intelligence cards.
- **Micro Elements (`rounded-lg` / `0.5rem`):** Inset form elements, keybind badges, and dropdown list selection highlights.

## Components

### Floating Omnibox / Search & Shield Bar
- **Geometry:** Continuous capsule pill (`height: 44px`, `rounded-full`).
- **Surface:** `rgba(255, 255, 255, 0.75)` with `backdrop-filter: blur(24px)`.
- **Border:** Dual-tone perimeter (`rgba(0, 0, 0, 0.06)` outer, `rgba(255, 255, 255, 0.6)` inner inset highlight).
- **Structure:** Left anchor hosts the Security Glyph & Shield Badge; center hosts domain string with graphite black hostname and muted slate pathing; right anchor hosts the Local Intelligence Capsule button.

### Security Shield Pills & Badges
- **Safe / Private State:** Emerald hairline container (`rgba(16, 185, 129, 0.12)` background, `rgba(16, 185, 129, 0.25)` stroke, `#10B981` text). Includes a static micro dot (`6px`) indicating zero cloud leaks.
- **Caution / Tracking Blocked State:** Warm amber pill with crisp tabular counter (`rgba(245, 158, 11, 0.1)` background, `#F59E0B` label).
- **Threat Intercept State:** Controlled crimson badge with structured micro-label (`#EF4444`). Never displays flashing or chaotic animations.

### Buttons & Controls
- **Primary Action (Intelligence / Action):** Graphite black solid (`#111827`), white text (`#FFFFFF`), `rounded-full`, subtle top inner highlight `rgba(255, 255, 255, 0.15)`.
- **Secondary Action (Glass Button):** Pill capsule, `rgba(0, 0, 0, 0.04)` background hovering to `rgba(0, 0, 0, 0.07)`, `1px solid rgba(0, 0, 0, 0.06)`.
- **Ghost Action:** Icon-only transparent circle, transitioning to `rgba(0, 0, 0, 0.05)` on hover with smooth spring transitions.

### Cards & Panels (Local AI & Privacy Dashboard)
- **Container:** `rounded-2xl`, glass elevated surface with `backdrop-filter: blur(32px)`.
- **Header:** Integrated divider-free title row; label accompanied by hardware acceleration status pill (`Local CoreML / WebGPU` badge in JetBrains Mono).
- **Metrics Layout:** High-density key-value pairs formatted with muted charcoal labels and deep graphite numerical values.

### Inputs & Controls
- **Input Fields:** Recessed well (`rgba(0, 0, 0, 0.03)`), `rounded-full` or `rounded-xl`, clear focus ring: `0 0 0 2px rgba(17, 24, 39, 0.08)`. No vivid blue outlines.
- **Switches & Toggles:** Precision iOS-style track (`width: 44px`, `height: 24px`), neutral track off (`rgba(0, 0, 0, 0.1)`), active fill `#111827`, pure white tactile knob with ambient micro-shadow.
- **Checkboxes & Radios:** Minimal graphite circular/squircle selectors with discrete check indicators.