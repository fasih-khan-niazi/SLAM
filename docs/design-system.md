# Design System

Use these tokens in Android (Compose / XML) and the React portal from the first UI screen. Do not invent a second palette later.

## Brand

| Token | Dark | Light |
|-------|------|--------|
| Background | `#0B1220` | `#F4F7FB` |
| Surface | `#111827` | `#FFFFFF` |
| Surface raised | `#1F2937` | `#EEF2F7` |
| Primary | `#14B8A6` | `#0D9488` |
| Primary muted | `#134E4A` | `#CCFBF1` |
| Accent | `#22D3EE` | `#0891B2` |
| Text | `#F8FAFC` | `#0F172A` |
| Text muted | `#94A3B8` | `#64748B` |
| Success | `#10B981` | `#059669` |
| Warning | `#F59E0B` | `#D97706` |
| Danger | `#F43F5E` | `#E11D48` |
| Border | `#1E293B` | `#E2E8F0` |

**Type:** Inter (web), plus system UI on Android. Headings semibold; body regular; 16sp minimum for form text.

**Radius:** 12dp cards, 16dp sheets, 8dp inputs, pill buttons 999.

**Motion:** 180ms ease-out for fades; 240ms for sheets. Haptics on submit, toggle, and destructive confirm (Android).

## Patterns (from day one)

- **Skeletons** on every list and dashboard metric, never a blank flash.
- **Custom modals** for confirmations (PIN change, payment submit, logout). No stock alert dialogs on primary flows.
- **Bottom sheets** on Android for trusted numbers, plan picker, and emergency controls.
- **Dark mode default** on Android; portal follows system with a toggle.
- **Login** is a product screen: logo, short value line, email/password, one primary CTA, quiet secondary link. No leftover template styling.

## Product copy

Write as a shipping product. Avoid “demo”, “test user”, “placeholder”, and academic wording in the UI.
