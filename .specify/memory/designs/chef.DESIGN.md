---
name: Brutalism
role: chef
themeId: chef
colors:
  surface: "#FFFFFF"
  surface-elevated: "#F4F4F0"
  surface-subtle: "#E7E7E0"
  text: "#111827"
  text-secondary: "#374151"
  border-subtle: "#737373"
  border-strong: "#111827"
  primary: "#DD614C"
  on-primary: "#111827"
  primary-hover: "#E67966"
  primary-border: "#111827"
  secondary: "#DAA144"
  on-secondary: "#111827"
  secondary-hover: "#C68D32"
  secondary-border: "#111827"
  danger-solid: "#B91C1C"
  on-danger: "#FFFFFF"
  danger-hover: "#991B1B"
  focus-ring: "#111827"
  neutral: "#FFFFFF"
  success: "#15803D"
  warning: "#B45309"
  danger: "#B91C1C"
semantics:
  neutral:
    bg: "#F3F4F6"
    fg: "#111827"
    border: "#111827"
  info:
    bg: "#E0F2FE"
    fg: "#0C4A6E"
    border: "#0369A1"
  success:
    bg: "#DCFCE7"
    fg: "#14532D"
    border: "#15803D"
  warning:
    bg: "#FEF3C7"
    fg: "#78350F"
    border: "#B45309"
  danger:
    bg: "#FEE2E2"
    fg: "#7F1D1D"
    border: "#B91C1C"
typography:
  h1:
    fontFamily: "Darker Grotesque"
    fontSize: 3rem
  body-md:
    fontFamily: "Darker Grotesque"
    fontSize: 1rem
  label-caps:
    fontFamily: "JetBrains Mono"
    fontSize: 0.75rem
  sourceScale: "desktop-first expressive scale"
  weights: "Darker Grotesque: 500, 600, 700, 800; JetBrains Mono: 400, 500, 600"
  fallbackStack:
    display: "'Darker Grotesque', ui-sans-serif, system-ui, sans-serif"
    sans: "'Darker Grotesque', ui-sans-serif, system-ui, sans-serif"
    mono: "'JetBrains Mono', ui-monospace, monospace"
rounded:
  sm: 0px
  md: 2px
  lg: 4px
spacing:
  sm: 4px
  md: 8px
  sourceScale: "4/8/12/16/24/32"
elevation:
  borderWidth: 2px
  shadowElevated: "4px 4px 0px #111827"
  backdropBlur: none
dataViz:
  series:
    - "#B83B28"
    - "#8F6210"
    - "#15803D"
    - "#1D4ED8"
    - "#374151"
  gridStroke: "#D4D4CE"
  axisStroke: "#111827"
  axisLabelFg: "#111827"
  tooltip:
    bg: "#111827"
    fg: "#FFFFFF"
    border: "#111827"
  cursorFill: "rgba(17, 24, 39, 0.08)"
  fontFamilySans: "'Darker Grotesque', ui-sans-serif, system-ui, sans-serif"
  fontFamilyMono: "'JetBrains Mono', ui-monospace, monospace"
---

## Overview

Raw, functional Brutalist aesthetic inspired by architectural clarity: crisp light surfaces, hard `#111827` borders, sharp corners, and immediate one-tap kitchen ergonomics (preserving Spec 005 C-08 desktop navigation). Remediated under Spec 024 for full WCAG 2.2 AA compliance.

## Style Foundations

- **Visual style:** bold, functional Brutalism
- **Typography scale:** desktop-first expressive scale
- **Typography fonts:** primary=Darker Grotesque, display=Darker Grotesque, mono=JetBrains Mono (self-hosted via `@fontsource/*`, `font-display: swap`)
- **Typography weights:** Darker Grotesque 500/600/700/800; JetBrains Mono 400/500/600
- **Color palette:** primary, secondary, neutral, info, success, warning, danger
- **Spacing scale:** 4/8/12/16/24/32 px
- **Radius scale:** sm=0px, md=2px, lg=4px; border-width=2px; hard offset shadow `4px 4px 0px #111827`

## Colors

- **Surface (`#FFFFFF`) / Surface Elevated (`#F4F4F0`) / Surface Subtle (`#E7E7E0`):** High-clarity kitchen display canvas.
- **Text (`#111827`, 17.74:1 on `#FFFFFF`) / Text Secondary (`#374151`, 10.31:1 on `#FFFFFF`):** Direct, unadorned ink hierarchy.
- **Border Subtle (`#737373`) / Border Strong (`#111827`, 17.74:1 on `#FFFFFF`):** Hard structural framing.
- **Primary (`#DD614C`) / On-Primary (`#111827`, 4.97:1) / Primary Hover (`#E67966`, 6.16:1 with `#111827`) / Primary Border (`#111827`, 17.74:1):** Terracotta accent paired with dark ink and lighter hover to preserve >= 4.5:1 contrast.
- **Secondary (`#DAA144`) / On-Secondary (`#111827`, 7.74:1) / Secondary Hover (`#C68D32`, 6.13:1) / Secondary Border (`#111827`, 17.74:1):** Warm ochre accent.
- **Danger Solid (`#B91C1C`) / On-Danger (`#FFFFFF`, 6.47:1) / Danger Hover (`#991B1B`, 8.31:1):** Critical kitchen alert action pair.
- **Focus Ring (`#111827`, 17.74:1 on `#FFFFFF`):** High-contrast keyboard focus indicator.

## Semantic Triads (WCAG 2.2 AA)

- **Neutral:** bg `#F3F4F6` / fg `#111827` / border `#111827`
- **Info:** bg `#E0F2FE` / fg `#0C4A6E` / border `#0369A1`
- **Success:** bg `#DCFCE7` / fg `#14532D` / border `#15803D`
- **Warning:** bg `#FEF3C7` / fg `#78350F` / border `#B45309`
- **Danger:** bg `#FEE2E2` / fg `#7F1D1D` / border `#B91C1C`

## Data Visualization (`dataViz`)

- **Series:** `["#B83B28", "#8F6210", "#15803D", "#1D4ED8", "#374151"]` (all >= 3.0:1 on white and elevated stone surface)
- **Grid / Axis / Label:** gridStroke `#D4D4CE`, axisStroke `#111827`, axisLabelFg `#111827`
- **Tooltip:** bg `#111827`, fg `#FFFFFF`, border `#111827`; cursorFill `rgba(17, 24, 39, 0.08)`
- **Typography:** Darker Grotesque (labels) + JetBrains Mono (timers and quantities)
