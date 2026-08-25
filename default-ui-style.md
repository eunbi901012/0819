# shadcn-admin UI/UX Reverse-Engineering Analysis

**Target:** when the user provides no external reference site, create a polished shadcn/ui + Tailwind CSS default visual system that feels professionally designed across CMS, admin, portal, dashboard, and homepage-style business applications. The shadcn-admin demo remains the structural baseline for app shell, component tree, reusable Tailwind/CSS recipes, and the `/sign-in-2` login page, but the generated UI must add enough brand personality, visual hierarchy, iconography, typography, and interaction detail to avoid a plain or monotonous template look.

**Analysis basis:** live demo routes plus the public source structure for `satnaing/shadcn-admin`. The source is the strongest fidelity anchor because the live site is a Vite/TanStack SPA and the DOM is generated client-side. This document therefore treats the public source components, route tree, design tokens, Tailwind classes, and feature folders as the implementation reference.

**Important legal note:** the original project is MIT licensed. If you copy source, assets, or substantial code directly, preserve the license and attribution. This document is an implementation/specification guide, not a license replacement.

---

## 1. Product/UI Overview

shadcn-admin is a modern admin dashboard template built around a **left sidebar shell**, **top header**, **fixed or scrollable content area**, **shadcn/ui primitives**, and **Tailwind CSS tokens**. The visual language is compact, neutral, dense enough for productivity tools, and strongly componentized.

The UI uses these repeated patterns:

- **Sidebar-first navigation** with collapsible groups, nested menus, team switcher, user menu, and rail.
- **Header bar** with sidebar trigger, optional top navigation, global search, theme switch, config drawer, and profile dropdown.
- **Main content wrapper** with predictable padding, max-width constraints, and optional fixed-height scrolling.
- **Card-based data display** for stats, charts, integrations, forms, and empty states.
- **Data table system** with toolbar search, faceted filters, column visibility, pagination, row selection, and bulk actions.
- **Responsive-first behavior**: mobile collapses sidebar, hides desktop top nav, swaps settings nav to select, and changes chats into an overlay conversation view.
- **CSS variable theme** using OKLCH color tokens and dark-mode aliases.

The most important areas to reproduce for a 90% identical result are:

1. Theme tokens and global base CSS.
2. Authenticated shell: `AuthenticatedLayout -> AppSidebar -> Header -> Main`.
3. Sidebar data and nested navigation behavior.
4. Header height, sticky/fixed behavior, and scroll shadow.
5. Data table toolbar, table, pagination, filters, and empty-row behavior.
6. `/sign-in-2` split login layout with light/dark hero images.
7. Page-specific grids and responsive breakpoints.

---

## 1.1 Default Reference-Site Fallback: Professional CMS/Homepage Design Layer

Use this section when the user did **not** provide a design reference URL, screenshot, Figma, brand guide, or explicit visual style. The goal is not to make a flashy marketing page; the goal is to make an ordinary CMS/admin/business site look like it was shaped by a senior product UI designer instead of a raw component library.

### 1.1.1 Design Direction

Default visual direction:

```txt
Style: polished enterprise SaaS / public-institution CMS
Base: shadcn/ui + Tailwind CSS + lucide-react
Tone: clean, reliable, editorial, modern, lightly premium
Density: compact for admin tables, more breathable for dashboards/homepages
Personality: subtle accent surfaces, editorial typography, rich empty states, meaningful icons
Avoid: plain gray-only cards, repeated identical white boxes, generic dashboard placeholders
```

The UI should feel trustworthy for government, university, education, enterprise, association, healthcare, and internal operation systems. It should work for both:

- **CMS/admin screens:** menus, users, roles, content, notices, files, audit, settings, batch jobs.
- **Homepage/portal screens:** hero, announcements, quick links, service cards, statistics, timeline, FAQ, contact/help sections.

### 1.1.2 Designer-Level Visual Differentiators

Every generated app should include at least **6 of these 10** visual differentiators when the stack supports them:

| Design point | How to implement with shadcn + Tailwind | Why it matters |
|---|---|---|
| Layered surface system | Use `bg-background`, `bg-muted/30`, `bg-card`, `border`, `shadow-sm`, and one tinted `bg-primary/5` or `bg-accent/30` section per major page | Prevents the app from becoming a flat wall of white cards |
| Accent hero/header band | Add a top page band with `bg-gradient-to-br from-primary/10 via-background to-secondary/20`, decorative blurred circles, or subtle grid pattern | Gives dashboards and homepages a designed first impression |
| Metric cards with icon capsules | Put a Lucide icon in a rounded `bg-primary/10 text-primary` capsule, not only a number and label | Makes stats scannable and visually memorable |
| Editorial section headers | Use eyebrow text, title, short helper copy, and right-side actions | Creates hierarchy like a professional product page |
| Meaningful empty states | Include illustration-like icon cluster, clear title, helper text, primary action, and secondary doc/help link | Empty screens still look intentional |
| Status color language | Use semantic badges with soft backgrounds: success/blue/warning/destructive/neutral | Makes admin state readable without noisy colors |
| Alternating content rhythm | Mix table, cards, timeline, summary strip, and quick-action grid instead of one repeated card layout | Avoids monotonous CMS screens |
| Premium login/auth page | Split layout, branded accent panel, role badges, trusted-system copy, and tasteful gradient/image placeholder | First screen feels custom even without reference site |
| Icon-led navigation | Add consistent Lucide icons for each primary menu group and domain action | Users understand modules faster |
| Micro-interactions | `transition-colors`, `hover:-translate-y-0.5`, `hover:shadow-md`, focus rings, skeleton loading | Gives tactile quality without heavy animation |

### 1.1.3 Default Brand Palettes

Pick **one** palette based on the domain. Do not use random bright colors. Keep one primary, one secondary/accent, one success, one warning, one destructive.

| Domain clue | Palette name | Primary | Accent | Feel |
|---|---|---|---|---|
| government, public, education, university, CMS | Civic Blue | `oklch(0.48 0.16 250)` | `oklch(0.72 0.12 210)` | reliable, official, calm |
| enterprise, operations, workflow, audit | Slate Indigo | `oklch(0.45 0.14 275)` | `oklch(0.70 0.10 250)` | structured, premium, technical |
| healthcare, welfare, safety | Trust Teal | `oklch(0.50 0.13 190)` | `oklch(0.76 0.11 165)` | clean, safe, humane |
| finance, approval, statistics | Deep Navy | `oklch(0.38 0.13 255)` | `oklch(0.69 0.12 85)` | confident, executive |
| culture, event, media, homepage | Warm Coral | `oklch(0.58 0.17 35)` | `oklch(0.78 0.10 70)` | friendly, editorial |

Default if uncertain: **Civic Blue**.

Token recipe:

```css
:root {
  --background: oklch(0.99 0.004 255);
  --foreground: oklch(0.20 0.025 255);
  --card: oklch(1 0 0);
  --card-foreground: var(--foreground);
  --primary: oklch(0.48 0.16 250);
  --primary-foreground: oklch(0.99 0.004 255);
  --secondary: oklch(0.95 0.025 250);
  --secondary-foreground: oklch(0.25 0.045 250);
  --muted: oklch(0.96 0.012 255);
  --muted-foreground: oklch(0.52 0.035 255);
  --accent: oklch(0.94 0.035 230);
  --accent-foreground: oklch(0.28 0.08 245);
  --border: oklch(0.90 0.015 255);
  --ring: var(--primary);
}
```

Dark mode should not simply invert. Use deep navy/slate backgrounds and keep accent surfaces visible:

```css
.dark {
  --background: oklch(0.16 0.025 255);
  --foreground: oklch(0.96 0.006 255);
  --card: oklch(0.20 0.025 255);
  --muted: oklch(0.24 0.025 255);
  --border: oklch(1 0 0 / 12%);
  --primary: oklch(0.72 0.13 245);
}
```

### 1.1.4 Typography System

Use fonts intentionally. Do not leave every text node at the browser or default Tailwind size.

Recommended default stack:

```txt
Korean + Latin UI: Pretendard Variable, Inter, system-ui, sans-serif
English/number-heavy dashboards: Inter, Pretendard, system-ui, sans-serif
Homepage/editorial headings: Manrope or Plus Jakarta Sans for headings + Pretendard/Inter for body
Monospace IDs/logs: JetBrains Mono or ui-monospace
```

Implementation rule:

```css
:root {
  --font-sans: "Pretendard Variable", Inter, system-ui, sans-serif;
  --font-heading: Manrope, "Pretendard Variable", Inter, system-ui, sans-serif;
  --font-mono: "JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, monospace;
}
```

Type scale:

| Use | Tailwind recipe |
|---|---|
| Page title | `text-2xl sm:text-3xl font-semibold tracking-tight` |
| Homepage hero title | `text-4xl sm:text-5xl lg:text-6xl font-semibold tracking-[-0.04em]` |
| Section title | `text-xl sm:text-2xl font-semibold tracking-tight` |
| Card title | `text-sm font-medium text-muted-foreground` + important value `text-2xl font-semibold tracking-tight` |
| Table text | `text-sm`, IDs use `font-mono text-xs` |
| Helper copy | `text-sm text-muted-foreground leading-6` |
| Eyebrow | `text-xs font-semibold uppercase tracking-[0.18em] text-primary` |

### 1.1.5 Iconography Rules

Use `lucide-react` by default. If Tabler icons exist, reserve them for brand/integration icons. Use icons as semantic wayfinding, not decoration.

Default module icon map:

| Module / screen type | Preferred icons |
|---|---|
| Dashboard / overview | `LayoutDashboard`, `Activity`, `Gauge` |
| Users / members | `Users`, `UserRound`, `UserCog` |
| Roles / permissions | `ShieldCheck`, `KeyRound`, `LockKeyhole` |
| Menus / navigation | `Menu`, `PanelLeft`, `Route` |
| Notices / posts / CMS content | `Megaphone`, `Newspaper`, `FileText` |
| Attachments / files | `Paperclip`, `FolderOpen`, `UploadCloud`, `Download` |
| Search / filters | `Search`, `SlidersHorizontal`, `ListFilter` |
| Audit / logs | `ScrollText`, `History`, `Fingerprint` |
| Settings | `Settings`, `SlidersHorizontal` |
| Batch / jobs | `ServerCog`, `Clock3`, `RefreshCw` |
| Security / privacy | `Shield`, `ScanLine`, `EyeOff` |
| Help / FAQ | `CircleHelp`, `BookOpen`, `MessagesSquare` |
| Success / completed | `CheckCircle2`, `BadgeCheck` |
| Warning / pending | `TriangleAlert`, `Clock` |
| Error / destructive | `CircleX`, `OctagonAlert`, `Trash2` |

Icon sizing:

```txt
Sidebar/menu icons: size-4
Button icons: size-4
Metric card icons: size-5 inside size-10 rounded-xl capsule
Hero feature icons: size-5 or size-6 inside size-11/12 rounded-2xl capsule
Empty state icon: size-8 inside size-14 rounded-2xl soft surface
```

### 1.1.6 CMS/Admin Page Composition Patterns

#### Dashboard / admin home

Use this composition instead of a plain 4-card grid:

```tsx
PageHeaderBand
  Eyebrow "운영 대시보드"
  Title
  Description
  Quick actions
  Right: date range / primary CTA

SummaryStrip
  3-5 MetricCard(icon capsule, trend badge, micro chart or sparkline placeholder)

ContentGrid
  Left: primary chart/card/table span 2 columns
  Right: task queue / recent activity / system status timeline

QuickLinkGrid
  domain cards with icons, count badges, last updated text
```

Tailwind recipe:

```txt
Header band:
  relative overflow-hidden rounded-2xl border bg-gradient-to-br
  from-primary/10 via-background to-secondary/30 p-6 shadow-sm
  before/after absolute blur-3xl opacity-40 decorative circles

Metric card:
  rounded-2xl border bg-card/95 p-5 shadow-sm transition-all
  hover:-translate-y-0.5 hover:shadow-md
```

#### List/table page

```tsx
PageHeader(title, description, primary action)
FilterCommandBar(search, date/status filters, view options)
InsightRow(optional: total/active/pending/error chips)
DataTable
Pagination
```

Design requirements:

- Add an icon to the primary action button.
- Use a soft colored `Badge` for status and role values.
- Use a compact metadata row below title columns for date/owner/type.
- Use skeleton rows for loading and a rich empty state for no results.

#### Create/edit/detail page

Use a two-column layout on desktop:

```tsx
Main form card          Side rail
- grouped sections      - status card
- sticky footer actions  - help checklist
- validation messages    - recent activity/audit
```

Tailwind recipe:

```txt
Form layout:
  grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]
Section card:
  rounded-2xl border bg-card p-6 shadow-sm
Sticky action footer:
  sticky bottom-0 border-t bg-background/90 backdrop-blur supports-[backdrop-filter]:bg-background/70
```

### 1.1.7 Homepage/Portal Composition Patterns

When a generated app includes a public homepage or portal route, use a more editorial layout than the admin shell.

```tsx
PublicHeader(logo, nav, search, login)
HeroSection(title, subtitle, CTA, secondary CTA, trust badges)
ServiceShortcutGrid
NoticeAndNewsSection
StatisticsBand
ProcessTimeline or FeatureCards
FAQ / Help / ContactSection
Footer
```

Default homepage visual recipe:

```txt
Hero:
  min-h-[520px] rounded-b-[2rem] or full-width section
  bg-gradient-to-br from-primary/10 via-background to-accent/30
  decorative grid/dot pattern with opacity-30
  title max-w-3xl, supporting copy max-w-2xl

Service cards:
  group rounded-2xl border bg-card p-5 shadow-sm
  hover:border-primary/30 hover:shadow-md
  icon capsule + title + 2-line description + arrow icon

Notice list:
  segmented tabs or badges by category
  each row has category badge, title, date, optional pin/new icon
```

### 1.1.8 Decoration Without Visual Noise

Use restrained decoration to avoid monotony:

- Gradient bands on hero/page headers.
- Soft radial blobs: `absolute -top-24 right-8 size-64 rounded-full bg-primary/10 blur-3xl`.
- Subtle grid pattern using CSS background image, opacity `0.25` or less.
- Card hover lift no more than `-translate-y-0.5`.
- Use `border-primary/20` and `bg-primary/5` for active surfaces.
- Use one illustration-like empty state icon cluster instead of stock images.

Do **not**:

- Put gradients on every card.
- Use more than two accent hues per page.
- Use glassmorphism everywhere.
- Use random emojis as icons.
- Add decorative elements that reduce table readability.

### 1.1.9 Professional Detail Checklist

Before considering the generated UI acceptable, verify these details:

```txt
[ ] Every primary page has title, helper copy, and a clear primary action.
[ ] Dashboard is not only four identical cards; it has at least one chart/list/timeline/quick-link section.
[ ] Tables have search/filter/sort/pagination affordances when data is list-like.
[ ] Empty, loading, error, permission, and success states are visually designed.
[ ] Icons are consistent in stroke width, size, and semantic meaning.
[ ] Status badges use a defined semantic palette.
[ ] Form pages group fields into sections and show validation near the field.
[ ] Login page has a branded side panel or premium card treatment.
[ ] Homepage/portal routes use hero + service shortcuts + notices/news + footer.
[ ] Mobile layout preserves hierarchy and does not collapse into cramped tables only.
```

### 1.1.10 Default Component Add-ons

If time is limited, implement these add-ons first because they create the highest visual impact:

1. `PageHeader` with eyebrow, title, description, actions, and optional gradient background.
2. `MetricCard` with icon capsule, trend badge, and helper copy.
3. `StatusBadge` semantic variants.
4. `EmptyState` with icon cluster and action buttons.
5. `FilterToolbar` with search, status select, date range placeholder, and view/options button.
6. `SectionCard` with title, description, content, and optional side action.
7. `PublicHero` and `ServiceShortcutCard` for portal/homepage pages.

Example class recipes:

```txt
PageHeader gradient:
  relative overflow-hidden rounded-2xl border bg-gradient-to-br
  from-primary/10 via-background to-accent/30 p-6 shadow-sm

Icon capsule:
  inline-flex size-10 items-center justify-center rounded-xl
  bg-primary/10 text-primary ring-1 ring-primary/15

Status badge success:
  border-emerald-200 bg-emerald-50 text-emerald-700
  dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-300

Empty state:
  mx-auto flex max-w-md flex-col items-center justify-center rounded-2xl
  border border-dashed bg-muted/30 p-10 text-center
```

### 1.1.11 Applying This Layer With shadcn-admin

Keep shadcn-admin as the reliable structure:

```text
App shell, sidebar, header, data table, forms, dialogs, settings layout = shadcn-admin baseline
Visual richness, palette choice, page headers, icon capsules, empty states, portal/homepage sections = this fallback layer
```

When the request is for a CMS or homepage and there is no external reference, do **not** generate a bare admin template. Apply this fallback layer so the result has an immediately recognizable design point.

## 2. Route and Page Inventory

The route tree includes the following major page groups.

| Route | Page / Function | Shell | Primary Layout Pattern |
|---|---|---|---|
| `/` | Dashboard | Authenticated | Header + Main + Tabs + cards + chart + recent sales |
| `/apps/` | App integrations | Authenticated | Header + fixed Main + filter toolbar + card grid |
| `/chats/` | Chats / inbox | Authenticated | Header + fixed Main + two-pane chat UI |
| `/tasks/` | Tasks | Authenticated | Header + Main + title actions + data table + dialogs |
| `/users/` | Users | Authenticated | Header + Main + title actions + data table + dialogs |
| `/settings/` | Settings profile | Authenticated settings layout | Settings title + side nav + form content section |
| `/settings/account` | Account settings | Authenticated settings layout | Form content section |
| `/settings/appearance` | Appearance settings | Authenticated settings layout | Theme/style preference form |
| `/settings/notifications` | Notification settings | Authenticated settings layout | Notification preference form |
| `/settings/display` | Display settings | Authenticated settings layout | Display preference form |
| `/help-center/` | Help center | Authenticated | Coming-soon centered empty state |
| `/sign-in` | Standard sign in | Auth card shell | Centered logo + card form |
| `/sign-in-2` | Split sign in | Custom auth split shell | Left logo/form + right image hero |
| `/sign-up` | Sign up | Auth card shell | Centered logo + card form |
| `/forgot-password` | Forgot password | Auth card shell | Centered logo + card form |
| `/otp` | Two-factor OTP | Auth card shell | Centered logo + card form |
| `/clerk` | Clerk integration root | Authenticated/Clerk provider | Optional integration / missing-key alert |
| `/clerk/sign-in` | Clerk sign in | Clerk route group | Clerk auth component |
| `/clerk/sign-up` | Clerk sign up | Clerk route group | Clerk auth component |
| `/clerk/user-management` | Clerk user management | Clerk route group | Clerk user/account component |
| `/401` | Unauthorized | Error shell | Centered error copy + action buttons |
| `/403` | Forbidden | Error shell | Centered error copy + action buttons |
| `/404` | Not found | Error shell | Centered error copy + action buttons |
| `/500` | General error | Error shell | Centered error copy + action buttons |
| `/503` | Maintenance | Error shell | Centered error copy + action buttons |
| `/errors/$error` | Dynamic error route | Error shell | Error switcher based on param |

---

## 3. Global Application Architecture

### 3.1 Root Provider Tree

The application is a Vite React SPA using TanStack Router. The root runtime structure is approximately:

```tsx
StrictMode
  QueryClientProvider
    ThemeProvider
      FontProvider
        DirectionProvider
          RouterProvider
```

The root route renders:

```tsx
RootDocument
  NavigationProgress
  Outlet              // current route
  Toaster             // sonner toast, duration around 5000ms
  Devtools            // router/query devtools in development
```

### 3.2 Authenticated Shell Tree

Every authenticated page uses the same shell. Recreate this first before implementing individual pages.

```tsx
AuthenticatedLayout
  SearchProvider
    LayoutProvider
      SidebarProvider(defaultOpen from sidebar_state cookie)
        SkipToMain
        AppSidebar
          Sidebar
            SidebarHeader
              TeamSwitcher
            SidebarContent
              NavGroup "General"
              NavGroup "Pages"
              NavGroup "Other"
            SidebarFooter
              NavUser
            SidebarRail
        SidebarInset
          PageRoute
            Header
            Main
```

### 3.3 Authenticated Shell CSS Recipe

Use these layout behaviors:

```txt
SidebarInset:
  @container/content
  has-data-[variant=inset]:min-h-[calc(100svh-theme(spacing.4))]
  peer-data-[variant=inset]:m-2
  peer-data-[variant=inset]:ms-0
  peer-data-[variant=inset]:rounded-xl
  peer-data-[variant=inset]:shadow-sm
  peer-data-[variant=inset]:peer-data-[state=collapsed]:ms-2
```

The shell should support two main modes:

- **Normal content pages:** content scrolls naturally; `Main` has container width constraints.
- **Fixed app pages:** `Main fixed` consumes remaining viewport height under the header and manages internal scrolling.

---

## 4. Core Layout Components

## 4.1 Header

The `Header` appears on almost every authenticated page.

### Component tree

```tsx
Header
  div.header-inner
    SidebarTrigger
    Separator(vertical)
    children
      TopNav?        // dashboard only
      Search
      ThemeSwitch
      ConfigDrawer
      ProfileDropdown
```

### CSS / Tailwind recipe

```txt
Header root:
  z-50 h-16
  fixed? "fixed top-0 right-0 left-auto w-[calc(100%-var(--sidebar-width))]"
  scroll state: shadow-sm backdrop-blur-sm bg-background/95

Header inner:
  flex h-full items-center gap-3 p-4 sm:gap-4

SidebarTrigger:
  variant="outline"
  size="icon"
  className="scale-125 sm:scale-100"

Separator:
  orientation="vertical"
  className="h-6"
```

### UX notes

- Header height is consistently **64px** (`h-16`).
- Fixed pages use header offset calculations; do not add random top margins.
- The scroll-shadow/backdrop-blur behavior is important for fidelity.
- On dashboard, `TopNav` sits between the sidebar trigger and the right-side controls.
- On other pages, `Search` gets `me-auto`, pushing theme/config/profile to the right.

---

## 4.2 Main

`Main` standardizes content padding and max width.

### CSS / Tailwind recipe

```txt
Base:
  px-4 py-6

Fixed mode:
  fixed height: calc(100svh - header height)
  flex grow flex-col overflow-hidden

Non-fluid / max-width behavior:
  @7xl/content:mx-auto
  @7xl/content:w-full
  @7xl/content:max-w-7xl
```

### Rebuild rule

Use `Main fixed` for pages with internal scroll panes:

- `/apps/`
- `/chats/`
- settings layout content area
- any page where toolbar stays visible while list/table scrolls

Use normal `Main` for dashboard, users, tasks unless the original source passes `fixed`.

---

## 4.3 TopNav

Used mainly on the dashboard header.

### Desktop behavior

```txt
Desktop nav wrapper:
  hidden items-center space-x-4 lg:flex

Link:
  text-sm font-medium transition-colors hover:text-primary
  active: text-primary / foreground
  inactive: text-muted-foreground
  disabled: opacity / pointer disabled behavior
```

### Mobile behavior

```txt
Mobile dropdown trigger:
  md:size-7 lg:hidden
```

Mobile top nav collapses into a dropdown-style trigger instead of rendering a full nav row.

---

## 5. Sidebar and Navigation

## 5.1 AppSidebar Component Tree

```tsx
AppSidebar(collapsible, variant)
  Sidebar(collapsible, variant)
    SidebarHeader
      TeamSwitcher(teams)
    SidebarContent
      navGroups.map(NavGroup)
    SidebarFooter
      NavUser(user)
    SidebarRail
```

### Important sidebar props

```txt
Sidebar:
  collapsible={collapsible}
  variant={variant}
```

The layout context controls visual settings such as `collapsible` and `variant`, exposed through the config drawer.

---

## 5.2 Sidebar Navigation Data

Use these nav groups to match the demo.

### General

```txt
Dashboard -> /
Tasks -> /tasks
Apps -> /apps
Chats -> /chats       badge: 3
Users -> /users
Secured by Clerk
  Sign In -> /clerk/sign-in
  Sign Up -> /clerk/sign-up
  User Management -> /clerk/user-management
```

### Pages

```txt
Auth
  Sign In -> /sign-in
  Sign In 2 -> /sign-in-2
  Sign Up -> /sign-up
  Forgot Password -> /forgot-password
  OTP -> /otp

Errors
  Unauthorized -> /401
  Forbidden -> /403
  Not Found -> /404
  Internal Server Error -> /500
  Maintenance Error -> /503
```

### Other

```txt
Settings
  Profile -> /settings
  Account -> /settings/account
  Appearance -> /settings/appearance
  Notifications -> /settings/notifications
  Display -> /settings/display

Help Center -> /help-center
```

---

## 5.3 NavGroup Behavior

### Component tree

```tsx
NavGroup
  SidebarGroup
    SidebarGroupLabel
    SidebarMenu
      SidebarMenuItem
        // no children
        SidebarMenuButton asChild
          Link
            Icon
            title
            NavBadge?

        // with children, expanded sidebar
        Collapsible
          CollapsibleTrigger asChild
            SidebarMenuButton
              Icon
              title
              NavBadge?
              ChevronRight
          CollapsibleContent
            SidebarMenuSub
              SidebarMenuSubItem
                SidebarMenuSubButton asChild
                  Link
```

### CSS / Tailwind details

```txt
Badge:
  ms-auto rounded-full px-1 py-0 text-xs

Chevron:
  ms-auto transition-transform duration-200
  group-data-[state=open]/collapsible:rotate-90
  rtl:rotate-180

Collapsed submenu:
  use DropdownMenu when sidebar collapsed and not mobile
```

### UX notes

- Parent groups open by default if a child route is active.
- Active state should apply to both direct links and submenu links.
- In collapsed mode, child menus become dropdown flyouts.
- Use the same icon set from Lucide and Tabler brand icons where possible.

---

## 5.4 TeamSwitcher

### Component tree

```tsx
TeamSwitcher
  DropdownMenu
    DropdownMenuTrigger asChild
      SidebarMenuButton(size="lg")
        activeTeam.logo square
        activeTeam.name
        activeTeam.plan
        ChevronsUpDown
    DropdownMenuContent(width matches trigger, min-w-56, rounded-lg)
      Label "Teams"
      team items with shortcut
      Separator
      Add team
```

### CSS recipe

```txt
Trigger:
  size="lg"

Logo square:
  size-8 rounded-lg
  bg-sidebar-primary text-sidebar-primary-foreground

Dropdown:
  w-[--radix-dropdown-menu-trigger-width]
  min-w-56 rounded-lg
```

---

## 5.5 NavUser / Profile Dropdown

### Component tree

```tsx
NavUser
  DropdownMenu
    DropdownMenuTrigger asChild
      SidebarMenuButton(size="lg")
        Avatar(size-8 rounded-lg)
        name/email text stack
        ChevronsUpDown
    DropdownMenuContent
      user card/header
      Account
      Billing
      Notifications
      Sign out
      SignOutDialog
```

### CSS recipe

```txt
Avatar:
  h-8 w-8 rounded-lg

Text stack:
  grid flex-1 text-start text-sm leading-tight
  name: truncate font-medium
  email: truncate text-xs

Dropdown:
  min-w-56 rounded-lg
  side based on mobile/desktop
```

---

## 6. Theme, Global CSS, and Design Tokens

## 6.1 Fonts

Theme CSS maps fonts through Tailwind theme variables:

```txt
--font-sans: Inter
--font-manrope: Manrope
```

Use Inter as the main UI font. Manrope is available but the general admin UI reads as Inter-like neutral sans.

---

## 6.2 Radius System

```css
:root {
  --radius: 0.625rem;
}
```

Tailwind radius aliases are derived from this value:

```txt
rounded-sm: calc(var(--radius) - 4px)
rounded-md: calc(var(--radius) - 2px)
rounded-lg: var(--radius)
rounded-xl: calc(var(--radius) + 4px)
```

Most components use `rounded-md`, cards use `rounded-xl`, and dropdowns often use `rounded-lg`.

---

## 6.3 Color Tokens

The project uses OKLCH CSS variables and maps them to Tailwind color tokens through `@theme inline`.

### Core tokens

```css
--background
--foreground
--card
--card-foreground
--popover
--popover-foreground
--primary
--primary-foreground
--secondary
--secondary-foreground
--muted
--muted-foreground
--accent
--accent-foreground
--destructive
--border
--input
--ring
```

### Chart tokens

```css
--chart-1
--chart-2
--chart-3
--chart-4
--chart-5
```

### Sidebar tokens

```css
--sidebar
--sidebar-foreground
--sidebar-primary
--sidebar-primary-foreground
--sidebar-accent
--sidebar-accent-foreground
--sidebar-border
--sidebar-ring
```

### Rebuild rule

For high visual fidelity, copy the token names and values exactly from `src/styles/theme.css`. Most component classes reference semantic tokens, so one mismatched token can shift the whole UI.

---

## 6.4 Global Base CSS

The global stylesheet has these important base behaviors:

```css
@import 'tailwindcss';
@import 'tw-animate-css';
@import './theme.css';
```

```txt
Global *:
  border-border
  outline-ring/50
  scrollbar-thin
  scrollbar-track-transparent
  scrollbar-thumb-border

html:
  overflow-x-hidden

body:
  min-h-svh w-full bg-background text-foreground
  has-[div[data-variant='inset']]:bg-sidebar

body[data-scroll-locked]:
  overflow unset / preserve layout width

button and [role='button']:
  cursor-pointer

mobile input/select/textarea:
  font-size: 16px to avoid iOS zoom
```

### Custom utility classes

```css
.container {
  margin-inline: auto;
  padding-inline: 2rem;
}

.no-scrollbar {
  scrollbar-width: none;
}
.no-scrollbar::-webkit-scrollbar {
  display: none;
}

.faded-bottom {
  position: relative;
}
.faded-bottom:after {
  pointer-events: none;
  position: absolute;
  inset-inline: 0;
  bottom: 0;
  height: 8rem;
  background: linear-gradient(to top, var(--background), transparent);
}

.CollapsibleContent[data-state='open'] {
  animation: slideDown 300ms ease-out;
}
.CollapsibleContent[data-state='closed'] {
  animation: slideUp 300ms ease-out;
}
```

---

## 7. Frequently Used Component CSS Recipes

## 7.1 Button

Base visual language:

```txt
inline-flex items-center justify-center gap-2
whitespace-nowrap rounded-md text-sm font-medium
transition-all
focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-[3px]
disabled:pointer-events-none disabled:opacity-50
svg:size-4 svg:shrink-0
```

### Variants

```txt
default:
  bg-primary text-primary-foreground shadow-xs hover:bg-primary/90

outline:
  border bg-background shadow-xs hover:bg-accent hover:text-accent-foreground
  dark:bg-input/30 dark:border-input dark:hover:bg-input/50

secondary:
  bg-secondary text-secondary-foreground shadow-xs hover:bg-secondary/80

ghost:
  hover:bg-accent hover:text-accent-foreground dark:hover:bg-accent/50

link:
  text-primary underline-offset-4 hover:underline

destructive:
  bg-destructive text-white shadow-xs hover:bg-destructive/90
```

### Sizes

```txt
default: h-9 px-4 py-2 has-[>svg]:px-3
sm:      h-8 rounded-md gap-1.5 px-3 has-[>svg]:px-2.5
lg:      h-10 rounded-md px-6 has-[>svg]:px-4
icon:    size-9
```

---

## 7.2 Card

Cards are the backbone of dashboard stats, charts, forms, and integration tiles.

```txt
Card:
  bg-card text-card-foreground
  flex flex-col gap-6
  rounded-xl border py-6 shadow-sm

CardHeader:
  grid auto-rows-min grid-rows-[auto_auto]
  items-start gap-1.5 px-6
  has-data-[slot=card-action]:grid-cols-[1fr_auto]

CardTitle:
  leading-none font-semibold

CardDescription:
  text-muted-foreground text-sm

CardContent:
  px-6

CardFooter:
  flex items-center px-6
```

Dashboard stat cards adjust padding:

```txt
Card className="gap-4"
CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2"
CardTitle className="text-sm font-medium"
CardContent -> value text-2xl font-bold + helper text-xs text-muted-foreground
```

---

## 7.3 Inputs and Form Fields

### Input recipe

```txt
flex h-9 w-full min-w-0 rounded-md border border-input
bg-transparent px-3 py-1 text-base shadow-xs transition-colors
placeholder:text-muted-foreground
focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-[3px]
disabled:cursor-not-allowed disabled:opacity-50
md:text-sm
```

### Form field recipe

```tsx
FormField
  FormItem
    FormLabel
    FormControl
      Input / PasswordInput / Select / RadioGroup
    FormMessage
```

### UX rules

- Labels are short and left-aligned.
- Error messages appear directly below fields.
- Auth forms place secondary links, such as “Forgot password?”, on the same row as the label.
- Form spacing is compact: usually `grid gap-2`, `space-y-4`, or `gap-4` depending on context.

---

## 7.4 Tables and Data Tables

### Generic table wrapper

```txt
Table container:
  overflow-hidden rounded-md border

Table:
  w-full caption-bottom text-sm
  min-w-xl on task/user pages

Table row:
  border-b transition-colors
  hover:bg-muted/50
  data-[state=selected]:bg-muted

Table head:
  h-10 px-2 text-left align-middle font-medium text-muted-foreground

Table cell:
  p-2 align-middle

Empty row:
  h-24 text-center
```

### DataTableToolbar tree

```tsx
DataTableToolbar
  div.flex.items-center.justify-between
    div.flex.flex-1.flex-col-reverse.sm:flex-row
      Input(search)
      FacetedFilter[]
      ResetButton?     // appears only when filters active
    DataTableViewOptions
```

### DataTableToolbar CSS

```txt
Wrapper:
  flex items-center justify-between

Left group:
  flex flex-1 flex-col-reverse items-start gap-y-2
  sm:flex-row sm:items-center sm:space-x-2

Search input:
  h-8 w-[150px] lg:w-[250px]

Filter group:
  flex gap-x-2

Reset button:
  h-8 px-2 lg:px-3

View options trigger:
  ms-auto hidden h-8 lg:flex
```

### Faceted filter CSS

```txt
Trigger:
  h-8 border-dashed
  size="sm"

Popover:
  w-50 p-0

Option checkbox square:
  size-4 rounded-sm border border-primary
  selected: bg-primary text-primary-foreground
```

### Pagination CSS

```txt
Pagination wrapper:
  flex items-center justify-between overflow-clip px-2
  @max-2xl/content:flex-col-reverse @max-2xl/content:gap-4

Page size select:
  h-8 w-17.5

Buttons:
  size-8 p-0
  first/last hidden on @max-md/content
```

---

## 7.5 Dropdowns, Popovers, Selects

Common sizing:

```txt
DropdownMenuContent:
  min-w-56 rounded-lg
  sideOffset=4

SelectTrigger:
  h-9
  specific widths per page, for example w-36 on Apps type filter

PopoverContent:
  p-0 for command-style filter lists
```

The design relies on Radix primitives. Keep focus rings and keyboard behavior intact.

---

## 7.6 Tabs

Dashboard tabs use vertical spacing and horizontal overflow.

```txt
Tabs root:
  space-y-4

TabsList:
  overflow-x-auto

TabsTrigger:
  compact, rounded, shadcn default

Disabled triggers:
  disabled Reports and Notifications visible but inactive
```

---

## 7.7 Dialogs and Drawers

Dialogs are used for:

- Task create/update/delete/import dialogs.
- User create/update/delete/import dialogs.
- New chat dialog.
- Sign-out confirmation.
- Config drawer.

General behavior:

```txt
DialogContent:
  rounded-lg border bg-background shadow-lg

DialogHeader:
  title + description

DialogFooter:
  actions aligned end, stacked on small screens if needed

ConfigDrawer:
  opened from Header control
  edits layout theme/sidebar settings
```

---

## 7.8 Scroll Areas and Faded Bottoms

The app frequently hides native scrollbars and uses fading at the bottom of long lists.

```txt
List pages:
  faded-bottom no-scrollbar grid/list overflow-auto pb-16

Settings content:
  faded-bottom h-full w-full overflow-y-auto scroll-smooth pe-4 pb-12

Chats conversation:
  flex flex-1 flex-col-reverse gap-4 overflow-y-auto
```

---

# 8. Page-by-Page UI/UX and Layout Analysis

---

## 8.1 `/sign-in-2` — Primary Login Page to Recreate

This is the login page specified as the reference login screen. It is not the same as the standard `/sign-in` card page. It uses a **two-column split layout** on desktop and a centered single-column form on mobile.

### High-level layout

```tsx
SignIn2
  div.auth-shell
    div.left-auth-column
      div.brand-and-form-column
        BrandHeader
          Logo
          "Shadcn Admin"
        div.form-stack
          div.heading-stack
            h2 "Login to your account"
            p "Enter your email and password below to log into your account"
            Link "Don't have an account? Sign up"
          UserAuthForm
          TermsPrivacyText
    div.right-hero-column
      img.dashboard-light
      img.dashboard-dark
```

### Outer shell CSS

```txt
relative container grid h-svh flex-col items-center justify-center
lg:max-w-none lg:grid-cols-2 lg:px-0
```

This creates:

- Full small-viewport height (`h-svh`) instead of regular `h-screen`.
- Centered content on mobile.
- Two equal desktop columns from the `lg` breakpoint.
- No horizontal padding on large screens so the hero reaches the viewport edge.

### Left column CSS

```txt
Left column root:
  lg:p-8

Brand + form column:
  mx-auto flex w-full flex-col justify-center space-y-2 py-8
  sm:w-120 sm:p-8

Brand row:
  mb-4 flex items-center justify-center

Logo:
  me-2

Brand text:
  text-xl font-medium
```

### Form stack CSS

```txt
Form wrapper:
  mx-auto flex w-full max-w-sm flex-col justify-center space-y-2

Heading block:
  flex flex-col space-y-2 text-start

Title:
  text-lg font-semibold tracking-tight

Description:
  text-sm text-muted-foreground

Sign-up link:
  underline underline-offset-4 hover:text-primary
```

### UserAuthForm structure

```tsx
UserAuthForm
  Form
    form.grid.gap-2
      Email field
      Password field
        label row
          "Password"
          Link "Forgot password?"
      Submit Button
        Loader2? / LogIn icon?
        "Sign in"
      Divider
        "Or continue with"
      Social buttons row
        GitHub
        Facebook
```

Expected form behavior:

- Email and password are validated with a schema.
- Password must be present and at least 7 characters.
- Submit shows loading state.
- Toast feedback appears during login.
- Mock successful auth redirects to dashboard or intended route.

### Terms / privacy text CSS

```txt
px-8 text-center text-sm text-muted-foreground

Links:
  underline underline-offset-4 hover:text-primary
```

### Right hero column CSS

```txt
Hero root:
  relative h-full overflow-hidden bg-muted max-lg:hidden

Hero image:
  absolute top-[15%] left-20 h-full w-full
  object-cover object-top-left
  dark:hidden / dark:block pair
  select-none
```

The right column is hidden below `lg`; this is important. Do not show a cropped hero on tablet/mobile if you want to match the original.

### Desktop visual composition

- Left half: quiet auth area with logo at top of form stack.
- Right half: muted background with a large dashboard screenshot shifted down and right.
- The screenshot is intentionally oversized/cropped by the `overflow-hidden` parent.
- Dark mode swaps the hero image to a dark dashboard screenshot.
- The form itself is narrow (`max-w-sm`) even though the left column is wider.

### Mobile visual composition

- Only the form column renders.
- Content stays vertically centered within `h-svh`.
- `container` padding gives comfortable side spacing.
- Width resolves to `w-full`, capped by `max-w-sm` for the form.

### Fidelity checklist for `/sign-in-2`

- [ ] Use `h-svh`, not `h-screen`.
- [ ] Use `lg:grid-cols-2` split.
- [ ] Hide hero with `max-lg:hidden`.
- [ ] Use `sm:w-120` for the internal auth column.
- [ ] Use `max-w-sm` for the actual form stack.
- [ ] Use two hero images, one light and one dark.
- [ ] Position hero image with `top-[15%] left-20`.
- [ ] Keep title at `text-lg`, not oversized.
- [ ] Keep links underlined with `underline-offset-4`.
- [ ] Use same auth form controls and social button row.

---

## 8.2 `/sign-in` — Standard Login Card

The standard sign-in page uses the shared `AuthLayout` and a card.

### Component tree

```tsx
AuthLayout
  LogoRow
  Card(max-w-sm gap-4)
    CardHeader
      CardTitle "Login to your account"
      CardDescription
        text + Sign up link
    CardContent
      UserAuthForm
    CardFooter
      TermsPrivacyText
```

### AuthLayout CSS

```txt
Outer:
  container grid h-svh max-w-none items-center justify-center

Inner:
  mx-auto flex w-full flex-col justify-center space-y-2 py-8 sm:p-8

Logo row:
  mb-4 flex items-center justify-center
```

### Card CSS

```txt
Card:
  max-w-sm gap-4

Title:
  text-lg tracking-tight

Footer text:
  px-8 text-center text-sm text-muted-foreground
```

### UX note

Use this page for a compact centered auth flow. It is visually simpler than `/sign-in-2` and does not include the dashboard screenshot hero.

---

## 8.3 `/sign-up` — Registration

### Component tree

```tsx
AuthLayout
  LogoRow
  Card(max-w-sm gap-4)
    CardHeader
      CardTitle "Create an account"
      CardDescription with Sign in link
    CardContent
      SignUpForm
    CardFooter
      TermsPrivacyText
```

### Layout notes

- Same centered card shell as `/sign-in`.
- Card width remains compact.
- Links use underline and hover primary behavior.
- Footer terms mirror sign-in page.

---

## 8.4 `/forgot-password`

### Component tree

```tsx
AuthLayout
  LogoRow
  Card(max-w-sm sm:min-w-sm gap-4)
    CardHeader
      CardTitle "Forgot Password"
      CardDescription multi-line helper text
    CardContent
      ForgotPasswordForm
    CardFooter
      Sign-up helper link
```

### UI details

- The description is more instructional and can span two lines.
- Footer uses centered/balanced text.
- The form should be a single email field plus submit/back action depending on implementation.

---

## 8.5 `/otp`

### Component tree

```tsx
AuthLayout
  LogoRow
  Card(max-w-md gap-4)
    CardHeader
      CardTitle "Two-factor Authentication"
      CardDescription
    CardContent
      OtpForm
    CardFooter
      Resend link
```

### UI details

- Slightly wider than the standard auth card: `max-w-md`.
- Title is smaller/compact: `text-base tracking-tight`.
- OTP input should use grouped boxes or a compact segmented input.

---

## 8.6 `/` — Dashboard

The dashboard is the primary landing page after authentication.

### Component tree

```tsx
Dashboard
  Header
    TopNav
    Search
    ThemeSwitch
    ConfigDrawer
    ProfileDropdown
  Main
    TitleRow
      h1 "Dashboard"
      Button "Download"
    Tabs(default="overview")
      TabsList
        Overview
        Analytics
        Reports(disabled)
        Notifications(disabled)
      TabsContent "overview"
        StatCardGrid
          StatCard x4
        AnalyticsGrid
          Card OverviewChart
          Card RecentSales
      TabsContent "analytics"
        Analytics
```

### Header layout

Dashboard is one of the only pages with `TopNav` in the header.

```txt
Header children:
  TopNav links: Overview active, Customers disabled, Products disabled, Settings disabled
  div.ml-auto.flex.items-center.space-x-4
    Search
    ThemeSwitch
    ConfigDrawer
    ProfileDropdown
```

### Main title row CSS

```txt
flex items-center justify-between space-y-2

Title:
  text-2xl font-bold tracking-tight

Button:
  size default, icon optional
```

### Tabs CSS

```txt
Tabs root:
  space-y-4

TabsList:
  overflow-x-auto

TabsContent overview:
  space-y-4
```

### Stat card grid

```txt
Grid:
  grid gap-4 sm:grid-cols-2 lg:grid-cols-4

Card:
  gap-4

CardHeader:
  flex flex-row items-center justify-between space-y-0 pb-2

CardTitle:
  text-sm font-medium

Value:
  text-2xl font-bold

Helper:
  text-xs text-muted-foreground
```

### Chart / Recent sales grid

```txt
Grid:
  grid grid-cols-1 gap-4 lg:grid-cols-7

Overview chart card:
  col-span-1 lg:col-span-4

Recent sales card:
  col-span-1 lg:col-span-3
```

### UX notes

- The top of the page is compact: title row then tabs immediately.
- Disabled tabs are visible to communicate future functionality.
- Cards use consistent shadow/border/radius from the design system.
- The second row establishes the dashboard feel: one large analytical chart plus a narrower activity card.

---

## 8.7 `/apps/` — App Integrations

The apps page is a filterable integration marketplace/list.

### Component tree

```tsx
Apps
  Header(fixed)
    Search(me-auto)
    ThemeSwitch
    ConfigDrawer
    ProfileDropdown
  Main(fixed)
    PageHeading
      h1 "App Integrations"
      p description
    ControlToolbar
      Input search
      Select app type
      Sort button/dropdown
    Separator
    AppGrid
      AppCard xN
```

### Main layout CSS

```txt
Main:
  fixed

Heading:
  mb-2 flex items-center justify-between space-y-2
  h1 text-2xl font-bold tracking-tight
  p text-muted-foreground
```

### Controls row CSS

```txt
Toolbar:
  my-4 flex items-end justify-between sm:my-0 sm:items-center

Left controls:
  flex flex-col gap-4 sm:my-4 sm:flex-row

Search input:
  h-9 w-40 lg:w-62.5

Type select trigger:
  w-36

Sort trigger:
  w-16
```

### Separator

```txt
Separator:
  shadow
```

### Grid/list CSS

```txt
App grid:
  faded-bottom no-scrollbar grid gap-4 overflow-auto pt-4 pb-16
  md:grid-cols-2 lg:grid-cols-3
```

### App card tree

```tsx
AppCard
  div.logo-title-row
    logo box
    div
      title
      description
    Button(connect/connected)
```

### App card CSS

```txt
Card root:
  rounded-lg border p-4 hover:shadow-md

Logo:
  size-10 rounded-lg bg-muted p-2

Action button:
  variant="outline"
  size="sm"
  connected state uses blue accent classes

Description:
  line-clamp text-muted-foreground
```

### UX behavior

- Search syncs to URL query state.
- Type filter supports all / connected / not connected.
- Sort supports ascending / descending.
- The list itself scrolls while the header/toolbar remains stable.

---

## 8.8 `/chats/` — Chat Inbox

The chats page is one of the most layout-sensitive pages. It has a responsive two-pane layout with mobile overlay behavior.

### Component tree

```tsx
Chats
  Header(fixed)
    Search(me-auto)
    ThemeSwitch
    ConfigDrawer
    ProfileDropdown
  Main(fixed)
    section.chat-layout
      ConversationListPane
        MobileStickyHeader
          h1 "Inbox"
          NewChatButton
        SearchBox
        ScrollArea
          ConversationListItem xN
      ConversationPane
        ChatHeader
          BackButton(mobile)
          UserAvatarName
          Call/Video/Info actions
        MessageScrollArea
          DateGroup xN
            MessageBubble xN
        MessageComposer
      EmptyConversationState?  // when no conversation selected
      NewChatDialog
```

### Root chat section CSS

```txt
section:
  flex h-full gap-6
```

### Left pane CSS

```txt
Conversation list pane:
  w-full sm:w-56 lg:w-72 2xl:w-80
```

### Mobile sticky heading

```txt
Header in list pane:
  sticky top-0 z-10 -mx-4 bg-background px-4 pb-3 shadow-md
  sm:static sm:z-auto sm:mx-0 sm:p-0 sm:shadow-none
```

### Search box CSS

```txt
Search label wrapper:
  flex h-9 w-full items-center rounded-md border border-input px-3
  focus-within:ring-1 focus-within:ring-ring

Input:
  w-full flex-1 bg-inherit text-sm focus-visible:outline-hidden
```

### Chat list CSS

```txt
Scroll area:
  -mx-3 h-full overflow-scroll p-3

Conversation item:
  group flex w-full rounded-md px-2 py-2 text-start text-sm
  hover:bg-accent hover:text-accent-foreground
  active on desktop: sm:bg-muted
```

### Right conversation pane CSS

```txt
Conversation pane:
  absolute inset-0 start-full z-50 hidden w-full flex-1 flex-col
  border bg-background shadow-xs
  sm:static sm:z-auto sm:flex sm:rounded-md

Mobile selected state:
  inset-s-0 flex
```

### Chat header CSS

```txt
Header:
  mb-1 flex flex-none justify-between bg-card p-4 shadow-lg sm:rounded-t-md

Avatar:
  size-9 lg:size-11

Mobile back button:
  visible below sm, hidden on desktop

Action icons:
  hidden on smallest screens, visible as viewport grows
```

### Message area CSS

```txt
Message scroll:
  flex flex-1 flex-col-reverse gap-4 overflow-y-auto

Date label:
  centered small muted text
```

### Chat bubble CSS

```txt
Bubble base:
  chat-box max-w-72 px-3 py-2 wrap-break-word shadow-lg

Own message:
  self-end rounded-[16px_16px_0_16px]
  bg-primary/90 text-primary-foreground/75

Other message:
  self-start rounded-[16px_16px_16px_0]
  bg-muted
```

### Composer CSS

```txt
Form:
  flex w-full flex-none gap-2

Input wrapper:
  flex flex-1 items-center rounded-md border border-input bg-card px-2 py-1
  focus-within:ring-1 focus-within:ring-ring

Attachment icons:
  plus/image/paperclip controlled by breakpoints

Send:
  desktop icon button + mobile button behavior
```

### Empty state

```txt
Centered column:
  m-auto flex flex-col items-center justify-center gap-2

Icon:
  MessagesSquare, large muted icon

Button:
  Send message
```

### UX notes

- On mobile, selecting a conversation slides/reveals the conversation pane over the list.
- On desktop, both list and conversation remain visible.
- Message order uses reverse flex so the latest messages anchor near the composer.
- The chat page is the clearest example of `Main fixed` + internal scroll panes.

---

## 8.9 `/tasks/` — Task Management Table

The tasks page is a CRUD/table page with dialogs and URL-synced table state.

### Component tree

```tsx
Tasks
  TasksProvider
    Header(fixed)
      Search(me-auto)
      ThemeSwitch
      ConfigDrawer
      ProfileDropdown
    Main
      TitleActionRow
        h2 "Tasks"
        p description
        TasksPrimaryButtons
      TasksTable
        DataTableToolbar
        Table
        DataTablePagination
        DataTableBulkActions
      TasksDialogs
```

### Main CSS

```txt
Main:
  flex flex-1 flex-col gap-4 sm:gap-6
```

### Title/action row CSS

```txt
Row:
  flex flex-wrap items-end justify-between gap-2

Title:
  text-2xl font-bold tracking-tight

Description:
  text-muted-foreground
```

### Task table behavior

URL/table state includes:

```txt
page index: default 1
page size: default 10
globalFilter: title/id search
columnFilters:
  status
  priority
sorting
column visibility
row selection
```

### Task table CSS

```txt
Outer:
  max-sm:has-[div[role="toolbar"]]:mb-16
  flex flex-1 flex-col gap-4

Toolbar search placeholder:
  "Filter by title or ID..."

Filters:
  Status
  Priority

Table wrapper:
  overflow-hidden rounded-md border

Table:
  min-w-xl

Empty row:
  h-24 text-center

Pagination:
  mt-auto
```

### UX notes

- Table controls are compact and productivity-focused.
- Filters are faceted, not giant dropdowns.
- Bulk actions appear only after row selection.
- Dialogs should be context-driven through the provider/store to match behavior.

---

## 8.10 `/users/` — User Management Table

The users page mirrors the tasks page but with user-specific columns, filters, and dialogs.

### Component tree

```tsx
Users
  UsersProvider
    Header(fixed)
      Search(me-auto)
      ThemeSwitch
      ConfigDrawer
      ProfileDropdown
    Main
      TitleActionRow
        h2 "User List"
        p description
        UsersPrimaryButtons
      UsersTable
        DataTableToolbar
        Table
        DataTablePagination
        DataTableBulkActions
      UsersDialogs
```

### Main CSS

```txt
Main:
  flex flex-1 flex-col gap-4 sm:gap-6
```

### Toolbar details

```txt
Search placeholder:
  "Filter users..."

Search key:
  username

Faceted filters:
  status: active / inactive / invited / suspended
  role
```

### Users table CSS

```txt
Outer:
  max-sm:has-[div[role="toolbar"]]:mb-16
  flex flex-1 flex-col gap-4

Table wrapper:
  overflow-hidden rounded-md border

Row:
  group/row

Table head/cell background behavior:
  bg-background
  group-hover/row:bg-muted
  group-data-[state=selected]/row:bg-muted

Empty row:
  h-24 text-center
```

### UX notes

- Users table has a denser identity-management feel than the task table.
- Preserve row hover and selected row background behavior.
- Use avatars/status badges/role badges consistently if implementing columns from mock data.

---

## 8.11 `/settings/` and Settings Subpages

Settings uses a nested layout with a secondary settings navigation, separate from the global sidebar.

### Component tree

```tsx
SettingsLayout
  Header(fixed)
    Search(me-auto)
    ThemeSwitch
    ConfigDrawer
    ProfileDropdown
  Main(fixed)
    SettingsHeading
      h1 "Settings"
      p "Manage your account settings and set e-mail preferences."
    Separator
    div.settings-body
      aside.settings-nav
        SidebarNav
          MobileSelect
          DesktopNavLinks
      section.settings-content
        Outlet
          ContentSection
            Header
              h3
              p
            Separator
            ScrollArea
              Form
```

### Main heading CSS

```txt
Title:
  text-2xl font-bold tracking-tight md:text-3xl

Description:
  text-muted-foreground

Separator:
  my-4 lg:my-6
```

### Settings body CSS

```txt
Body:
  flex flex-1 flex-col space-y-2 overflow-hidden md:space-y-2
  lg:flex-row lg:space-y-0 lg:space-x-12

Aside:
  top-0 lg:sticky lg:w-1/5

Content:
  flex w-full overflow-y-hidden p-1
```

### Settings SidebarNav behavior

```txt
Mobile:
  Select visible only below md
  Select wrapper: p-1 md:hidden
  SelectTrigger: h-12 sm:w-48

Desktop/tablet:
  ScrollArea horizontal on md
  nav flex row then lg:flex-col

Link base:
  buttonVariants({ variant: 'ghost' })
  justify-start

Active:
  bg-muted hover:bg-accent

Inactive:
  hover:bg-accent hover:underline
```

### ContentSection CSS

```txt
Section root:
  flex flex-1 flex-col

Header title:
  text-lg font-medium

Header description:
  text-sm text-muted-foreground

Separator:
  my-4 flex-none

Scroll area:
  faded-bottom h-full w-full overflow-y-auto scroll-smooth pe-4 pb-12

Inner form width:
  -mx-1 px-1.5 lg:max-w-xl
```

### Settings subpages

#### `/settings/` — Profile

```tsx
ContentSection
  title="Profile"
  desc="This is how others will see you on the site."
  ProfileForm
```

Typical fields: username/display name, bio, URLs or profile details depending on form implementation.

#### `/settings/account` — Account

```tsx
ContentSection
  title="Account"
  desc="Update your account settings. Set your preferred language and timezone."
  AccountForm
```

Typical fields: name, date/language/timezone preferences.

#### `/settings/appearance` — Appearance

```tsx
ContentSection
  title="Appearance"
  AppearanceForm
```

Expected controls: theme/font/display style choices using radio/select style inputs.

#### `/settings/notifications` — Notifications

```tsx
ContentSection
  title="Notifications"
  NotificationsForm
```

Expected controls: notification preference toggles/checklists.

#### `/settings/display` — Display

```tsx
ContentSection
  title="Display"
  DisplayForm
```

Expected controls: UI density, sidebar, or display preference choices.

### UX notes

- Settings content intentionally caps form width (`lg:max-w-xl`), leaving whitespace on wide screens.
- The secondary nav is horizontal on medium screens and vertical on large screens.
- Mobile settings navigation becomes a select, which is important for usability.

---

## 8.12 `/help-center/` — Coming Soon Page

Help Center currently renders a generic coming-soon state.

### Component tree

```tsx
HelpCenterRoute
  ComingSoon
    div.full-height
      div.centered-stack
        TelescopeIcon
        h1 "Coming Soon"
        p helper text
```

### CSS recipe

```txt
Outer:
  h-svh

Inner:
  m-auto flex h-full w-full flex-col items-center justify-center gap-2

Icon:
  size around 72

Title:
  text-4xl leading-tight font-bold

Paragraph:
  text-center text-muted-foreground
```

### UX note

The coming-soon component should look intentional, not like a blank page. Keep the icon large and centered.

---

## 8.13 `/clerk` and Clerk Pages

Clerk integration is modular and optional.

### `/clerk` root behavior

```tsx
ClerkRoute
  if missing publishable key:
    MissingClerkPubKey
  else:
    ClerkProvider
      AuthenticatedLayout
        Outlet
```

### Missing key alert layout

```tsx
Main
  Alert
    AlertTitle "No Publishable Key Found!"
    AlertDescription
      instructions for adding VITE_CLERK_PUBLISHABLE_KEY
      code block style snippets
      note that Clerk integration is optional
```

### Clerk route group

Expected child pages:

```txt
/clerk/sign-in
/clerk/sign-up
/clerk/user-management
```

These are separate from the mock auth pages. They are intended for real Clerk auth widgets and can be removed if Clerk is not used.

### UX notes

- Do not mix Clerk pages with mock auth pages unless intentionally integrating auth.
- Keep Clerk isolated under `src/routes/clerk` to preserve optional modular behavior.
- The sidebar labels this group “Secured by Clerk.”

---

## 8.14 Error Pages: `/401`, `/403`, `/404`, `/500`, `/503`, `/errors/$error`

Error pages use a minimal centered layout with large status code, title, description, and two actions.

### Common component tree

```tsx
ErrorPage
  div.centered-error-shell
    h1 statusCode
    h2 title
    p description
    div.actions
      Button "Go Back"
      Button "Back to Home"
```

### Common actions

```txt
Go Back:
  router.history.go(-1)

Back to Home:
  navigate({ to: '/' })
```

### Copy examples

```txt
401:
  Unauthorized Access
  Please log in with the appropriate credentials to access this resource.

404:
  Oops! Page Not Found!
  It seems like the page you're looking for does not exist or might have been removed.

500:
  Oops! Something went wrong :')
  We apologize for the inconvenience. Please try again later.
```

### UX notes

- Keep error pages plain and centered.
- Buttons should use regular `Button` variants, not custom large CTAs.
- Dynamic `/errors/$error` can switch between known error components.

---

# 9. Reusable Page Patterns

## 9.1 Standard Authenticated Page Pattern

Use this for dashboard-like and CRUD pages.

```tsx
function Page() {
  return (
    <>
      <Header fixed>
        <Search className="me-auto" />
        <ThemeSwitch />
        <ConfigDrawer />
        <ProfileDropdown />
      </Header>
      <Main className="flex flex-1 flex-col gap-4 sm:gap-6">
        <div className="flex flex-wrap items-end justify-between gap-2">
          <div>
            <h2 className="text-2xl font-bold tracking-tight">Title</h2>
            <p className="text-muted-foreground">Description</p>
          </div>
          <PrimaryButtons />
        </div>
        <Content />
      </Main>
    </>
  )
}
```

## 9.2 Fixed List/Grid Page Pattern

Use this for pages like Apps.

```tsx
<Header fixed>
  <Search className="me-auto" />
  <ThemeSwitch />
  <ConfigDrawer />
  <ProfileDropdown />
</Header>
<Main fixed>
  <PageHeading />
  <Toolbar />
  <Separator className="shadow" />
  <div className="faded-bottom no-scrollbar grid gap-4 overflow-auto pt-4 pb-16 md:grid-cols-2 lg:grid-cols-3">
    {items.map(...)}
  </div>
</Main>
```

## 9.3 CRUD DataTable Page Pattern

```tsx
<Provider>
  <Header fixed>...</Header>
  <Main className="flex flex-1 flex-col gap-4 sm:gap-6">
    <TitleActionRow />
    <DataTable />
    <Dialogs />
  </Main>
</Provider>
```

The exact visual match depends more on the shared DataTable components than on the individual page.

## 9.4 Settings Form Page Pattern

```tsx
<ContentSection title="..." desc="...">
  <Form className="space-y-8">
    <FormField />
    <FormField />
    <Button>Update</Button>
  </Form>
</ContentSection>
```

---

# 10. Implementation Blueprint for a 90% Identical Build

## 10.1 Recommended File Structure

```txt
src/
  assets/
    logo.tsx
    dashboard-light.png
    dashboard-dark.png
    brand-icons.tsx
  components/
    layout/
      authenticated-layout.tsx
      header.tsx
      main.tsx
      top-nav.tsx
    data-table/
      toolbar.tsx
      faceted-filter.tsx
      pagination.tsx
      view-options.tsx
      bulk-actions.tsx
    ui/
      button.tsx
      card.tsx
      input.tsx
      table.tsx
      sidebar.tsx
      dropdown-menu.tsx
      select.tsx
      tabs.tsx
      dialog.tsx
      popover.tsx
      command.tsx
    app-sidebar.tsx
    nav-group.tsx
    nav-user.tsx
    team-switcher.tsx
    search.tsx
    command-menu.tsx
    config-drawer.tsx
    theme-switch.tsx
    profile-dropdown.tsx
    password-input.tsx
    coming-soon.tsx
  context/
    layout-context.tsx
  features/
    auth/
    dashboard/
    apps/
    chats/
    tasks/
    users/
    settings/
    errors/
  routes/
  styles/
    index.css
    theme.css
```

---

## 10.2 Build Order

### Phase 1 — Design system

1. Install/configure Tailwind and shadcn/ui primitives.
2. Copy the semantic CSS variables and dark-mode variables.
3. Implement `Button`, `Card`, `Input`, `Table`, `Dropdown`, `Dialog`, `Select`, `Tabs`, `Sidebar` primitives.
4. Add global utilities: `container`, `no-scrollbar`, `faded-bottom`, collapsible animations.

### Phase 2 — App shell

1. Implement root providers.
2. Implement `AuthenticatedLayout`.
3. Implement `Header`, `Main`, and `TopNav`.
4. Implement sidebar components and nav data.
5. Add `Search`, `ThemeSwitch`, `ConfigDrawer`, `ProfileDropdown`.

### Phase 3 — Auth pages

1. Implement shared `AuthLayout`.
2. Implement `/sign-in-2` first because it is the requested login target.
3. Add `/sign-in`, `/sign-up`, `/forgot-password`, `/otp`.
4. Implement `UserAuthForm` and shared password input/social button styling.

### Phase 4 — Core pages

1. Dashboard.
2. Apps.
3. Tasks and Users, after DataTable is complete.
4. Chats.
5. Settings.
6. Help Center and errors.
7. Clerk optional routes.

---

## 10.3 Fidelity-Critical CSS Checklist

- [ ] `h-svh` used for full-screen auth and coming-soon layouts.
- [ ] Header is exactly `h-16`.
- [ ] `Main` uses `px-4 py-6`.
- [ ] Wide content uses container query max width: `@7xl/content:max-w-7xl`.
- [ ] Sidebar background uses `--sidebar` tokens.
- [ ] Body switches background when inset sidebar variant is active.
- [ ] All borders use `border-border`; all rings use `ring-ring/50`.
- [ ] All form controls use `h-9` default height.
- [ ] Data table search inputs use `h-8`.
- [ ] Cards use `rounded-xl border shadow-sm`.
- [ ] App cards use `rounded-lg border p-4 hover:shadow-md`.
- [ ] Chat bubbles use asymmetric 16px border radii.
- [ ] Settings body uses `lg:w-1/5` aside and `lg:max-w-xl` forms.
- [ ] Desktop sidebar collapsed mode has dropdown submenu behavior.
- [ ] Mobile settings nav uses Select, not a cramped vertical sidebar.
- [ ] `/sign-in-2` hero is hidden under `lg`.

---

# 11. Page-Level Component Trees for Quick Rebuild

## Dashboard

```txt
Dashboard
├─ Header
│  ├─ TopNav
│  ├─ Search
│  ├─ ThemeSwitch
│  ├─ ConfigDrawer
│  └─ ProfileDropdown
└─ Main
   ├─ TitleRow
   └─ Tabs
      ├─ TabsList
      ├─ OverviewContent
      │  ├─ StatCardGrid
      │  └─ ChartRecentSalesGrid
      └─ AnalyticsContent
```

## Apps

```txt
Apps
├─ Header
└─ Main(fixed)
   ├─ PageHeading
   ├─ FiltersToolbar
   │  ├─ SearchInput
   │  ├─ TypeSelect
   │  └─ SortDropdown
   ├─ Separator
   └─ ScrollableAppGrid
      └─ AppCard[]
```

## Chats

```txt
Chats
├─ Header
└─ Main(fixed)
   └─ ChatLayout
      ├─ ConversationListPane
      │  ├─ InboxHeader
      │  ├─ SearchBox
      │  └─ ConversationList
      ├─ ConversationPane
      │  ├─ ChatHeader
      │  ├─ MessageList
      │  └─ Composer
      ├─ EmptyState
      └─ NewChatDialog
```

## Tasks

```txt
Tasks
└─ TasksProvider
   ├─ Header
   └─ Main
      ├─ TitleActionRow
      ├─ TasksTable
      │  ├─ DataTableToolbar
      │  ├─ Table
      │  ├─ Pagination
      │  └─ BulkActions
      └─ TasksDialogs
```

## Users

```txt
Users
└─ UsersProvider
   ├─ Header
   └─ Main
      ├─ TitleActionRow
      ├─ UsersTable
      │  ├─ DataTableToolbar
      │  ├─ Table
      │  ├─ Pagination
      │  └─ BulkActions
      └─ UsersDialogs
```

## Settings

```txt
SettingsLayout
├─ Header
└─ Main(fixed)
   ├─ SettingsHeading
   ├─ Separator
   └─ SettingsBody
      ├─ SettingsSidebarNav
      │  ├─ MobileSelect
      │  └─ DesktopLinks
      └─ ContentOutlet
         └─ ContentSection
            ├─ SectionHeader
            ├─ Separator
            └─ ScrollableFormArea
```

## Sign In 2

```txt
SignIn2
└─ AuthSplitShell
   ├─ LeftColumn
   │  └─ AuthColumn
   │     ├─ BrandRow
   │     ├─ HeadingText
   │     ├─ UserAuthForm
   │     └─ TermsText
   └─ RightHeroColumn
      ├─ DashboardLightImage
      └─ DashboardDarkImage
```

---

# 12. Interaction and State Notes

## 12.1 URL-backed state

Several pages sync state to the URL:

- Apps: search term, app type, sort.
- Tasks: pagination, filters, global search.
- Users: pagination, username filter, status filter, role filter.

For fidelity, preserve URL query behavior. It makes refresh/back/forward feel like the demo.

## 12.2 Theme and layout state

- Theme switch should support light/dark/system-like behavior if matching the original broader architecture.
- Sidebar open state is read from/written to a `sidebar_state` cookie.
- Config drawer changes layout options such as sidebar style, collapsed behavior, and possibly content layout.

## 12.3 Accessibility details

- Keep Radix/shadcn primitives for keyboard navigation.
- Preserve focus-visible rings.
- Use buttons for interactive controls, not clickable divs.
- Use labels for form fields.
- Keep `SkipToMain` in the authenticated shell.
- Use semantic table elements for data tables.

---

# 13. Visual Design Heuristics

## Spacing

```txt
Page padding:       px-4 py-6
Header padding:     p-4
Section gaps:       gap-4 sm:gap-6
Card internal:      px-6 py-6 / gap-6
Toolbar controls:   h-8 or h-9
Auth form width:    max-w-sm
Settings form:      lg:max-w-xl
```

## Typography

```txt
Page h1/h2:
  text-2xl font-bold tracking-tight
  settings h1 adds md:text-3xl

Card title:
  text-sm font-medium for stat cards
  text-lg / leading-none font-semibold for regular cards

Descriptions:
  text-sm text-muted-foreground

Table text:
  text-sm

Small helper text:
  text-xs text-muted-foreground
```

## Borders and shadows

```txt
Default border:     border / border-border
Cards:              border shadow-sm
Header on scroll:   shadow-sm backdrop-blur-sm
App card hover:     hover:shadow-md
Chat header:        shadow-lg
Chat bubbles:       shadow-lg
```

## Responsiveness

```txt
sm:
  auth internal padding, table/list behavior, chat desktop starts changing

md:
  settings nav switches from select to nav links

lg:
  sidebar/top nav desktop behavior
  sign-in-2 becomes two-column
  dashboard cards become 4 columns
  apps grid becomes 3 columns
  settings layout becomes side-by-side

2xl:
  chat list pane grows wider

container queries:
  used for main content max-width and data table pagination responsiveness
```

---

# 14. Asset Notes

To match the site closely, prepare these assets:

```txt
Logo component:
  used in auth pages and sidebar branding/team areas

Dashboard hero images:
  dashboard-light
  dashboard-dark
  used only by /sign-in-2 hero panel

Brand icons:
  GitHub
  Facebook
  app integration logos

User/team avatars:
  sidebar profile
  chat list
  recent sales
  user table
```

If exact assets are not copied, use visually similar placeholders but preserve dimensions, radius, and object-fit behavior.

---

# 15. Final Rebuild Checklist

Use this checklist to confirm the clone is close enough.

## Shell

- [ ] Sidebar groups and route labels match.
- [ ] Sidebar can collapse and nested groups still work.
- [ ] Team switcher appears at top of sidebar.
- [ ] User dropdown appears at bottom of sidebar.
- [ ] Header height and controls match across pages.
- [ ] Search/theme/config/profile order is consistent.

## Auth

- [ ] `/sign-in-2` split layout is exact at `lg` and hidden hero below `lg`.
- [ ] Standard auth pages use centered card layout.
- [ ] Auth cards use same widths and copy/link styling.

## Dashboard

- [ ] 4 stat cards responsive grid.
- [ ] Chart/recent-sales grid uses 7-column desktop split.
- [ ] Dashboard top nav appears only where expected.

## Apps

- [ ] Search/type/sort toolbar matches widths.
- [ ] Grid scrolls internally with faded bottom.
- [ ] App cards have logo box, title, description, and action button.

## Chats

- [ ] Desktop two-pane layout.
- [ ] Mobile conversation pane overlays list.
- [ ] Bubble radii and colors match own vs other messages.
- [ ] Composer layout changes across breakpoints.

## Tasks/Users

- [ ] Toolbar search and faceted filters match.
- [ ] Table wrapper is rounded and bordered.
- [ ] Pagination matches button sizes and responsive hiding.
- [ ] Bulk actions appear only with row selection.

## Settings

- [ ] Mobile settings nav is Select.
- [ ] Desktop settings nav is side navigation.
- [ ] Content forms are max-width limited.
- [ ] Scroll area has faded bottom and internal padding.

## Theme/CSS

- [ ] OKLCH tokens match source.
- [ ] Dark mode is token-driven.
- [ ] Radius system matches `--radius: 0.625rem`.
- [ ] Focus rings, borders, muted text, and hover states are consistent.

---

## 16. Minimal CSS Token Skeleton

Use the real token values from the source for final fidelity. This skeleton shows the required shape.

```css
:root {
  --radius: 0.625rem;

  --background: ...;
  --foreground: ...;
  --card: ...;
  --card-foreground: ...;
  --popover: ...;
  --popover-foreground: ...;
  --primary: ...;
  --primary-foreground: ...;
  --secondary: ...;
  --secondary-foreground: ...;
  --muted: ...;
  --muted-foreground: ...;
  --accent: ...;
  --accent-foreground: ...;
  --destructive: ...;
  --border: ...;
  --input: ...;
  --ring: ...;

  --chart-1: ...;
  --chart-2: ...;
  --chart-3: ...;
  --chart-4: ...;
  --chart-5: ...;

  --sidebar: ...;
  --sidebar-foreground: ...;
  --sidebar-primary: ...;
  --sidebar-primary-foreground: ...;
  --sidebar-accent: ...;
  --sidebar-accent-foreground: ...;
  --sidebar-border: ...;
  --sidebar-ring: ...;
}

.dark {
  --background: ...;
  --foreground: ...;
  --card: ...;
  --card-foreground: ...;
  --popover: ...;
  --popover-foreground: ...;
  --primary: ...;
  --primary-foreground: ...;
  --secondary: ...;
  --secondary-foreground: ...;
  --muted: ...;
  --muted-foreground: ...;
  --accent: ...;
  --accent-foreground: ...;
  --destructive: ...;
  --border: ...;
  --input: ...;
  --ring: ...;

  --chart-1: ...;
  --chart-2: ...;
  --chart-3: ...;
  --chart-4: ...;
  --chart-5: ...;

  --sidebar: ...;
  --sidebar-foreground: ...;
  --sidebar-primary: ...;
  --sidebar-primary-foreground: ...;
  --sidebar-accent: ...;
  --sidebar-accent-foreground: ...;
  --sidebar-border: ...;
  --sidebar-ring: ...;
}
```

---

## 17. Practical 90% Match Strategy

To make a site that feels 90% identical, avoid redesigning primitives. The site’s identity comes from **composition and exact utility classes**, not from exotic visuals.

Priority order:

1. Copy the theme variables and global utilities.
2. Build the authenticated shell exactly.
3. Build the sidebar data and nested behavior exactly.
4. Implement `/sign-in-2` with the exact split layout and hero behavior.
5. Implement `Header`, `Main`, cards, buttons, inputs, and tables as shared components.
6. Implement the page layouts using the same grid/flex classes.
7. Fill content with equivalent mock data and icons.
8. Fine-tune responsive behavior page by page.

Most visible mismatches will come from these mistakes:

- Using `h-screen` instead of `h-svh`.
- Making the auth form too wide.
- Forgetting the fixed `h-16` header.
- Replacing the sidebar with a generic drawer.
- Not using container queries for max width/pagination.
- Using different radius/color tokens.
- Omitting the faded bottom scroll treatment.
- Making tables too spacious.
- Showing the `/sign-in-2` hero on tablet/mobile.

---

## 18. Source Files to Use as Implementation Anchors

Use these files from the original source as the most important references when rebuilding:

```txt
src/styles/theme.css
src/styles/index.css
src/routes/routeTree.gen.ts
src/routes/__root.tsx
src/routes/_authenticated/route.tsx
src/components/layout/authenticated-layout.tsx
src/components/layout/header.tsx
src/components/layout/main.tsx
src/components/layout/top-nav.tsx
src/components/layout/app-sidebar.tsx
src/components/layout/data/sidebar-data.ts
src/components/layout/nav-group.tsx
src/components/layout/nav-user.tsx
src/components/layout/team-switcher.tsx
src/components/data-table/toolbar.tsx
src/components/data-table/faceted-filter.tsx
src/components/data-table/pagination.tsx
src/components/data-table/view-options.tsx
src/features/auth/auth-layout.tsx
src/features/auth/sign-in/sign-in-2.tsx
src/features/auth/sign-in/components/user-auth-form.tsx
src/features/dashboard/index.tsx
src/features/apps/index.tsx
src/features/chats/index.tsx
src/features/tasks/index.tsx
src/features/tasks/components/tasks-table.tsx
src/features/users/index.tsx
src/features/users/components/users-table.tsx
src/features/settings/index.tsx
src/features/settings/components/sidebar-nav.tsx
src/features/settings/components/content-section.tsx
src/features/errors/*
```