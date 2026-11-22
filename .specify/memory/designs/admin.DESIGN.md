---
name: Glassmorphism
role: admin
themeId: admin
colors:
  surface: "#F4F7FC"
  surface-elevated: "#FFFFFF"
  surface-elevated-glass: "rgba(255, 255, 255, 0.82)"
  surface-subtle: "#E8EEF8"
  text: "#141414"
  text-secondary: "#475569"
  border-subtle: "#CBD5E1"
  border-strong: "#64748B"
  primary: "#1856FF"
  on-primary: "#FFFFFF"
  primary-hover: "#0F45DB"
  primary-border: "#1856FF"
  secondary: "#3A344E"
  on-secondary: "#FFFFFF"
  secondary-hover: "#2B263B"
  secondary-border: "#3A344E"
  danger-solid: "#C81433"
  on-danger: "#FFFFFF"
  danger-hover: "#A60F29"
  focus-ring: "#1856FF"
  neutral: "#F4F7FC"
  success: "#059652"
  warning: "#C25E18"
  danger: "#C81433"
semantics:
  neutral:
    bg: "#EEF2F6"
    fg: "#1E293B"
    border: "#64748B"
  info:
    bg: "#E8F0FF"
    fg: "#0F369E"
    border: "#1856FF"
  success:
    bg: "#E6FBF0"
    fg: "#045D33"
    border: "#059652"
  warning:
    bg: "#FEF2E8"
    fg: "#8A3E0A"
    border: "#C25E18"
  danger:
    bg: "#FDECEF"
    fg: "#9E0E27"
    border: "#C81433"
typography:
  h1:
    fontFamily: "Plus Jakarta Sans"
    fontSize: 3rem
  body-md:
    fontFamily: "Plus Jakarta Sans"
    fontSize: 1rem
  label-caps:
    fontFamily: "JetBrains Mono"
    fontSize: 0.75rem
  sourceScale: "mobile-first compact scale"
  weights: "Plus Jakarta Sans: 400, 500, 600, 700; JetBrains Mono: 400, 500, 600"
  fallbackStack:
    display: "'Plus Jakarta Sans', ui-sans-serif, system-ui, sans-serif"
    sans: "'Plus Jakarta Sans', ui-sans-serif, system-ui, sans-serif"
    mono: "'JetBrains Mono', ui-monospace, monospace"
rounded:
  sm: 6px
  md: 12px
  lg: 16px
spacing:
  sm: 8px
  md: 16px
  sourceScale: "8/12/16/24/32 (comfortable density mode)"
elevation:
  borderWidth: 1px
  shadowElevated: "0 8px 28px rgba(24, 86, 255, 0.08)"
  surfaceElevatedGlass: "rgba(255, 255, 255, 0.82)"
  backdropBlur: "blur(12px)"
dataViz:
  series:
    - "#1856FF"
    - "#3A344E"
    - "#047843"
    - "#B85410"
    - "#C81433"
  gridStroke: "#CBD5E1"
  axisStroke: "#64748B"
  axisLabelFg: "#334155"
  tooltip:
    bg: "#141414"
    fg: "#FFFFFF"
    border: "#64748B"
  cursorFill: "rgba(24, 86, 255, 0.10)"
  fontFamilySans: "'Plus Jakarta Sans', ui-sans-serif, system-ui, sans-serif"
  fontFamilyMono: "'JetBrains Mono', ui-monospace, monospace"
---

## Overview

Frosted glass enterprise environment with translucent white layers (`rgba(255, 255, 255, 0.82)` + `blur(12px)`) over a cool `#F4F7FC` canvas, crisp borders, and zero generic purple-to-blue gradients. Remediated under Spec 024 to guarantee WCAG 2.2 AA semantic triads and data visualization contrast.

## Style Foundations

- **Visual style:** clean, high-contrast, enterprise Glassmorphism (no purple-to-blue gradients)
- **Typography scale:** mobile-first compact scale
- **Typography fonts:** primary=Plus Jakarta Sans, display=Plus Jakarta Sans, mono=JetBrains Mono (self-hosted via `@fontsource/*`, `font-display: swap`)
- **Typography weights:** Plus Jakarta Sans 400/500/600/700; JetBrains Mono 400/500/600
- **Color palette:** primary, secondary, neutral, info, success, warning, danger, frosted glass surface layers
- **Spacing scale:** 8/12/16/24/32 px (comfortable density mode)
- **Radius scale:** sm=6px, md=12px, lg=16px; border-width=1px; backdrop-filter=`blur(12px)`

## Colors

- **Surface (`#F4F7FC`) / Surface Elevated (`#FFFFFF`, glass `rgba(255, 255, 255, 0.82)` + `blur(12px)`) / Surface Subtle (`#E8EEF8`):** Subtle cool canvas enabling distinguishable frosted glass layers without prohibited gradients.
- **Text (`#141414`, 17.16:1 on `#F4F7FC`) / Text Secondary (`#475569`, 7.06:1 on `#F4F7FC`):** Enterprise-grade legible ink.
- **Border Subtle (`#CBD5E1`) / Border Strong (`#64748B`, 4.43:1 on `#F4F7FC`):** Luminous subtle glass framing and strong control outlines.
- **Primary (`#1856FF`) / On-Primary (`#FFFFFF`, 5.51:1) / Primary Hover (`#0F45DB`, 7.26:1) / Primary Border (`#1856FF`, 5.13:1 on `#F4F7FC`):** Royal blue primary accent.
- **Secondary (`#3A344E`) / On-Secondary (`#FFFFFF`, 11.79:1) / Secondary Hover (`#2B263B`, 14.55:1) / Secondary Border (`#3A344E`, 10.98:1 on `#F4F7FC`):** Deep plum-slate secondary accent.
- **Danger Solid (`#C81433`) / On-Danger (`#FFFFFF`, 5.83:1) / Danger Hover (`#A60F29`, 7.72:1):** Remediated accessible crimson danger pair.
- **Focus Ring (`#1856FF`, 5.13:1 on `#F4F7FC`):** Keyboard focus indicator.

## Semantic Triads (WCAG 2.2 AA)

- **Neutral:** bg `#EEF2F6` / fg `#1E293B` / border `#64748B`
- **Info:** bg `#E8F0FF` / fg `#0F369E` / border `#1856FF`
- **Success:** bg `#E6FBF0` / fg `#045D33` / border `#059652`
- **Warning:** bg `#FEF2E8` / fg `#8A3E0A` / border `#C25E18`
- **Danger:** bg `#FDECEF` / fg `#9E0E27` / border `#C81433`

## Data Visualization (`dataViz`)

- **Series:** `["#1856FF", "#3A344E", "#047843", "#B85410", "#C81433"]` (all >= 3.0:1 on `#F4F7FC` and `#FFFFFF`)
- **Grid / Axis / Label:** gridStroke `#CBD5E1`, axisStroke `#64748B`, axisLabelFg `#334155`
- **Tooltip:** bg `#141414`, fg `#FFFFFF`, border `#64748B`; cursorFill `rgba(24, 86, 255, 0.10)`
- **Typography:** Plus Jakarta Sans (labels) + JetBrains Mono (financial metrics and KPIs)
