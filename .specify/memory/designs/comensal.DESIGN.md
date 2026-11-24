---
name: Bento
role: comensal
themeId: comensal
colors:
  surface: "#FFF5E6"
  surface-elevated: "#FFFFFF"
  surface-subtle: "#F5E6D0"
  text: "#111827"
  text-secondary: "#475569"
  border-subtle: "#D6C2A8"
  border-strong: "#7A6855"
  primary: "#FAD4C0"
  on-primary: "#111827"
  primary-hover: "#F3BEA2"
  primary-border: "#9A4A24"
  secondary: "#80A1C1"
  on-secondary: "#111827"
  secondary-hover: "#6B90B4"
  secondary-border: "#3B5E82"
  danger-solid: "#B91C1C"
  on-danger: "#FFFFFF"
  danger-hover: "#991B1B"
  focus-ring: "#1E40AF"
  neutral: "#FFF5E6"
  success: "#15803D"
  warning: "#B45309"
  danger: "#B91C1C"
semantics:
  neutral:
    bg: "#EFE3D0"
    fg: "#1F2937"
    border: "#7A6855"
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
    fontFamily: "DM Sans"
    fontSize: 2rem
  body-md:
    fontFamily: "DM Sans"
    fontSize: 1rem
  label-caps:
    fontFamily: "JetBrains Mono"
    fontSize: 0.75rem
  sourceScale: "12/14/16/20/24/32"
  weights: "DM Sans: 400, 500, 600, 700; JetBrains Mono: 400, 500, 600"
  fallbackStack:
    display: "'DM Sans', ui-sans-serif, system-ui, sans-serif"
    sans: "'DM Sans', ui-sans-serif, system-ui, sans-serif"
    mono: "'JetBrains Mono', ui-monospace, monospace"
rounded:
  sm: 8px
  md: 14px
  lg: 20px
spacing:
  sm: 4px
  md: 8px
  sourceScale: "4/8/12/16/24/32"
elevation:
  borderWidth: 1px
  shadowElevated: "0 2px 10px rgba(17, 24, 39, 0.06)"
  backdropBlur: none
dataViz:
  series:
    - "#9A4A24"
    - "#2B5B84"
    - "#15803D"
    - "#B45309"
    - "#B91C1C"
  gridStroke: "#D6C2A8"
  axisStroke: "#7A6855"
  axisLabelFg: "#374151"
  tooltip:
    bg: "#111827"
    fg: "#FFF5E6"
    border: "#9A4A24"
  cursorFill: "rgba(154, 74, 36, 0.10)"
  fontFamilySans: "'DM Sans', ui-sans-serif, system-ui, sans-serif"
  fontFamilyMono: "'JetBrains Mono', ui-monospace, monospace"
---

## Overview

Modular grid layout with card-like blocks, clear hierarchy, soft spacing, and subtle visual contrast for organized, scannable diner experiences. Remediated under Spec 024 to enforce WCAG 2.2 AA contrast and Zero AI-Slop compliance.

## Style Foundations

- **Visual style:** modern, warm, modular Bento
- **Typography scale:** 12/14/16/20/24/32
- **Typography fonts:** primary=DM Sans, display=DM Sans, mono=JetBrains Mono (self-hosted via `@fontsource/*`, `font-display: swap`)
- **Typography weights:** DM Sans 400/500/600/700; JetBrains Mono 400/500/600
- **Color palette:** primary, secondary, neutral, info, success, warning, danger
- **Spacing scale:** 4/8/12/16/24/32 px
- **Radius scale:** sm=8px, md=14px, lg=20px; border-width=1px

## Colors

- **Surface (`#FFF5E6`) / Surface Elevated (`#FFFFFF`) / Surface Subtle (`#F5E6D0`):** Warm Bento canvas and modular card blocks.
- **Text (`#111827`, 16.44:1 on `#FFF5E6`) / Text Secondary (`#475569`, 7.02:1 on `#FFF5E6`):** High-contrast ink hierarchy.
- **Border Subtle (`#D6C2A8`) / Border Strong (`#7A6855`, 4.94:1 on `#FFF5E6`):** Card and control framing.
- **Primary (`#FAD4C0`) / On-Primary (`#111827`, 12.88:1) / Primary Hover (`#F3BEA2`, 10.73:1) / Primary Border (`#9A4A24`, 5.76:1 on `#FFF5E6`):** Signature warm peach accent with WCAG 2.2 AA 1.4.11 boundary definition.
- **Secondary (`#80A1C1`) / On-Secondary (`#111827`, 6.58:1) / Secondary Hover (`#6B90B4`, 5.30:1) / Secondary Border (`#3B5E82`, 6.26:1 on `#FFF5E6`):** Supporting slate blue accent.
- **Danger Solid (`#B91C1C`) / On-Danger (`#FFFFFF`, 6.47:1) / Danger Hover (`#991B1B`, 8.31:1):** Destructive action pair.
- **Focus Ring (`#1E40AF`, 8.08:1 on `#FFF5E6`):** Keyboard focus indicator.

## Semantic Triads (WCAG 2.2 AA)

- **Neutral:** bg `#EFE3D0` / fg `#1F2937` / border `#7A6855`
- **Info:** bg `#E0F2FE` / fg `#0C4A6E` / border `#0369A1`
- **Success:** bg `#DCFCE7` / fg `#14532D` / border `#15803D`
- **Warning:** bg `#FEF3C7` / fg `#78350F` / border `#B45309`
- **Danger:** bg `#FEE2E2` / fg `#7F1D1D` / border `#B91C1C`

## Data Visualization (`dataViz`)

- **Series:** `["#9A4A24", "#2B5B84", "#15803D", "#B45309", "#B91C1C"]` (all >= 3.0:1 on surface and elevated surface)
- **Grid / Axis / Label:** gridStroke `#D6C2A8`, axisStroke `#7A6855`, axisLabelFg `#374151`
- **Tooltip:** bg `#111827`, fg `#FFF5E6`, border `#9A4A24`; cursorFill `rgba(154, 74, 36, 0.10)`
- **Typography:** DM Sans (labels) + JetBrains Mono (numeric values)

## Layer Isolation Note (Spec 016)

- Role theme tokens use exclusively `--ee-*`.
- Establishment branding (`BrandThemeProvider`) stays isolated under `--brand-*` within the diner storefront without mutating `--ee-*`.
