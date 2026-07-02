# VastBrowser Design Assets

Brand assets and web design tokens for VastBrowser / Mango Developers.

## Source logos

| File | Description |
|---|---|
| `fullname-logo-no-bg.png` | White "VAST Browser" wordmark + V mark, transparent bg, 2000×2000. For dark/colored backgrounds. |
| `vast-logo-only-no-bg.png` | White V mark only, transparent bg, 2048×2048. For dark/colored backgrounds. |
| `fullname-logo-dark-no-bg.png` | Dark (#1f2025) recolor of the wordmark. For light backgrounds. Generated from the white original (alpha-preserving recolor). |
| `vast-logo-only-dark-no-bg.png` | Dark (#1f2025) recolor of the V mark. For light backgrounds. |
| `web/*-trimmed.png` | Alpha-trimmed, ≤600px copies of all four logos for direct web embedding (no transparent padding). |

**Rule:** every derived asset (banners, icons, hero art, favicons) must be generated
from these source logos — never redraw the mark by hand.

Recolor recipe (Pillow): split RGBA, keep the alpha channel, fill RGB with the target
color. See git history of this folder for the exact script.

## Icon set (`icons/`)

Hand-authored 24×24 stroke SVGs used on the landing page feature cards. Style rules
(matched to bevel.health's minimal line-icon look):

- `viewBox="0 0 24 24"`, `fill="none"`, `stroke="currentColor"`
- `stroke-width="1.8"`, round caps and joins
- Single color via `currentColor` so CSS controls the tint
- No emojis anywhere in web properties — always use these or new SVGs in the same style

| File | Used for |
|---|---|
| `cursor.svg` | Remote-first cursor feature |
| `engine.svg` | Browser engine (globe) |
| `shield.svg` | Ad blocking / privacy |
| `layers.svg` | Two flavors |
| `refresh.svg` | Automatic updates |
| `verified.svg` | Signed, verifiable builds |
| `download.svg` | Download buttons |

## Web design tokens (landing page)

The landing page (`mangodevelopers.github.io/apps/vast-browser/`) replicates the
design language of [bevel.health](https://www.bevel.health/) (a Webflow site). Exact
values extracted from their published stylesheets:

### Colors

| Token | Value | Use |
|---|---|---|
| Page background | `#f3f6f7` | body |
| Dark / ink | `#1f2025` | text, primary buttons |
| Light tint | `#ebf0f8` | subtle fills |
| Hero gradient | `#d2e5ff → #fff9ee` | rounded hero panel, top to bottom |
| Coral | `#ffab94` | accent, beta badge |
| Cyan | `#7ddcff` | accent |
| Lilac | `#b9a6ff` | accent |
| Mint | `#3fffc2` | accent |
| Blue | `#415eee` | accent |
| Accent tints | `color-mix(in hsl, <accent> 20%/40%, transparent)` | icon chips, soft fills |

### Typography

- **Headings:** Hanken Grotesk (Google Fonts) — free stand-in for Bevel's commercial
  Gilroy. Semibold (600), letter-spacing `-0.03em`, line-height 1.
  - H1: `clamp(3rem, 2.857vw + 2.429rem, 5rem)`
  - H2: `clamp(2.5rem, 2.143vw + 2.071rem, 4rem)`
- **Body:** SF Pro system stack, exactly as Bevel:
  `-apple-system, BlinkMacSystemFont, Inter, "Segoe UI", sans-serif`,
  17px, weight 500, line-height 1.4, letter-spacing `.01em`
- **Eyebrow labels:** 0.875rem, weight 500, uppercase, letter-spacing `.1em`

### Components

- **Frosted nav pill** (the floating menu): `position: fixed` full-width wrapper,
  centered inner pill with `backdrop-filter: blur(.75rem)`, `background: #fff9`,
  `border-radius: 2rem`, `padding: .5rem`. Link hover: color → `#1f202599`, `.15s`.
- **Buttons:** pill radius (`8rem`), padding `.75rem 1.5rem`, 1rem/500.
  - Primary: bg `#1f2025`, white text; hover bg `#000`
  - Secondary: transparent bg, `1px` border `#000`; hover bg `#000`, white text
  - Glass (on imagery): `backdrop-filter: blur(.75rem)`, bg `#ffffff1a`,
    `box-shadow: inset 0 1px #fff, inset 0 0 .25rem #ffffff40`
- **Hero panel:** inset rounded container — `border-radius: 1.5rem`,
  `width: calc(100% - 1rem)`, sky gradient background, content masked with a bottom
  fade (`mask-image: linear-gradient(#000 76%, transparent)`).
- **Cards:** white, `border-radius: 2rem`, shadow `0 2px 1rem #00000026` used sparingly.
- **Animations:** scroll-triggered fade-up (opacity 0 + translateY(24px) → visible,
  `.6s cubic-bezier(.25,.46,.45,.94)`, staggered ~80ms) via IntersectionObserver;
  color/background transitions `.15s–.3s`.

### Beta indicator

While the app is pre-1.0, the landing page shows an "Early Preview — Beta Version"
glass pill and a coral notice strip. Both carry `data-beta` attributes and are hidden
by JS when the latest GitHub release tag is ≥ 1.0.0 — remove nothing manually.
