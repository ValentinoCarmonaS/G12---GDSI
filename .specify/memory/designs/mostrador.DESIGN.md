---
name: Bold
role: mostrador
themeId: mostrador
colors:
  surface: "#111111"
  surface-elevated: "#1C1C1F"
  surface-subtle: "#26262B"
  text: "#F8FAFC"
  text-secondary: "#CBD5E1"
  border-subtle: "#3F3F46"
  border-strong: "#71717A"
  primary: "#0077BC"
  on-primary: "#FFFFFF"
  primary-hover: "#00639E"
  primary-border: "#38BDF8"
  secondary: "#009866"
  on-secondary: "#0A0A0A"
  secondary-hover: "#00B377"
  secondary-border: "#34D399"
  danger-solid: "#DC2626"
  on-danger: "#FFFFFF"
  danger-hover: "#B91C1C"
  focus-ring: "#38BDF8"
  neutral: "#111111"
  success: "#22C55E"
  warning: "#F59E0B"
  danger: "#F87171"
semantics:
  neutral:
    bg: "#27272A"
    fg: "#F4F4F5"
    border: "#71717A"
  info:
    bg: "#082F49"
    fg: "#BAE6FD"
    border: "#38BDF8"
  success:
    bg: "#052E16"
    fg: "#BBF7D0"
    border: "#22C55E"
  warning:
    bg: "#451A03"
    fg: "#FDE68A"
    border: "#F59E0B"
  danger:
    bg: "#450A0A"
    fg: "#FECACA"
    border: "#F87171"
typography:
  h1:
    fontFamily: "Archivo Black"
    fontSize: 3rem
  body-md:
    fontFamily: "Archivo"
    fontSize: 1rem
  label-caps:
    fontFamily: "JetBrains Mono"
    fontSize: 0.75rem
  sourceScale: "desktop-first expressive scale"
  weights: "Archivo Black: 400; Archivo: 400, 500, 600, 700; JetBrains Mono: 400, 500, 600"
  fallbackStack:
    display: "'Archivo Black', 'Arial Black', sans-serif"
    sans: "'Archivo', ui-sans-serif, system-ui, sans-serif"
    mono: "'JetBrains Mono', ui-monospace, monospace"
rounded:
  sm: 4px
  md: 8px
  lg: 12px
spacing:
  sm: 4px
  md: 8px
  sourceScale: "4/8/12/16/24/32"
elevation:
  borderWidth: 2px
  shadowElevated: "0 8px 24px rgba(0, 0, 0, 0.45)"
  backdropBlur: none
dataViz:
  series:
    - "#38BDF8"
    - "#34D399"
    - "#FBBF24"
    - "#F87171"
    - "#C084FC"
  gridStroke: "#2E2E34"
  axisStroke: "#71717A"
  axisLabelFg: "#CBD5E1"
  tooltip:
    bg: "#1C1C1F"
    fg: "#F8FAFC"
    border: "#71717A"
  cursorFill: "rgba(56, 189, 248, 0.14)"
  fontFamilySans: "'Archivo', ui-sans-serif, system-ui, sans-serif"
  fontFamilyMono: "'JetBrains Mono', ui-monospace, monospace"
---

## Overview

Strong visual presence with heavyweight display typography, high-contrast dark surfaces, and commanding operational layouts for rapid counter dispatch. Remediated under Spec 024 to resolve WCAG 2.2 AA contrast (`#F8FAFC` on `#111111`, `#0A0A0A` on `#009866`) and operational body readability (`Archivo`).

## Style Foundations

- **Visual style:** bold, dark high-contrast operational
- **Typography scale:** desktop-first expressive scale
- **Typography fonts:** display=Archivo Black (400), primary/body=Archivo (400/500/600/700), mono=JetBrains Mono (400/500/600) (self-hosted via `@fontsource/*`, `font-display: swap`)
- **Color palette:** primary, secondary, neutral, info, success, warning, danger
- **Spacing scale:** 4/8/12/16/24/32 px
- **Radius scale:** sm=4px, md=8px, lg=12px; border-width=2px

## Colors

- **Surface (`#111111`) / Surface Elevated (`#1C1C1F`) / Surface Subtle (`#26262B`):** Dark dispatch canvas and raised panels.
- **Text (`#F8FAFC`, 18.05:1 on `#111111`) / Text Secondary (`#CBD5E1`, 12.72:1 on `#111111`):** High-contrast light ink on dark surfaces.
- **Border Subtle (`#3F3F46`) / Border Strong (`#71717A`, 3.91:1 on `#111111`):** Crisp dark-mode borders.
- **Primary (`#0077BC`) / On-Primary (`#FFFFFF`, 4.81:1) / Primary Hover (`#00639E`, 6.41:1) / Primary Border (`#38BDF8`, 8.81:1 on `#111111`):** Signature cobalt accent.
- **Secondary (`#009866`) / On-Secondary (`#0A0A0A`, 5.36:1) / Secondary Hover (`#00B377`, 7.27:1) / Secondary Border (`#34D399`, 9.82:1 on `#111111`):** Emerald dispatch accent with dark ink to meet WCAG 2.2 AA.
- **Danger Solid (`#DC2626`) / On-Danger (`#FFFFFF`, 4.83:1) / Danger Hover (`#B91C1C`, 6.47:1):** Critical alert action pair.
- **Focus Ring (`#38BDF8`, 8.81:1 on `#111111`):** High-visibility keyboard focus indicator.

## Semantic Triads (WCAG 2.2 AA)

- **Neutral:** bg `#27272A` / fg `#F4F4F5` / border `#71717A`
- **Info:** bg `#082F49` / fg `#BAE6FD` / border `#38BDF8`
- **Success:** bg `#052E16` / fg `#BBF7D0` / border `#22C55E`
- **Warning:** bg `#451A03` / fg `#FDE68A` / border `#F59E0B`
- **Danger:** bg `#450A0A` / fg `#FECACA` / border `#F87171`

## Data Visualization (`dataViz`)

- **Series:** `["#38BDF8", "#34D399", "#FBBF24", "#F87171", "#C084FC"]` (all >= 3.0:1 on dark surface and elevated surface)
- **Grid / Axis / Label:** gridStroke `#2E2E34`, axisStroke `#71717A`, axisLabelFg `#CBD5E1`
- **Tooltip:** bg `#1C1C1F`, fg `#F8FAFC`, border `#71717A`; cursorFill `rgba(56, 189, 248, 0.14)`
- **Typography:** Archivo (labels) + JetBrains Mono (numeric values and pickup PINs)
