---
name: Mermaid Pocket
purpose: PWA to receive shared Mermaid text and render zoomable, pannable diagrams
type: webapp
---

# Mermaid Pocket

A tiny, static, installable Progressive Web App (PWA) for receiving Mermaid syntax from another app using the **Web Share Target API** and displaying it with **pinch/scroll zoom and drag-to-pan**.

## Features

- Android/Chromium **Share → Mermaid Pocket** support after installing the PWA.
- Reads shared `text` (also accepts a URL or title if the text field is empty).
- Accepts plain Mermaid syntax or Markdown fenced blocks tagged `mermaid`.
- Renders the diagram automatically when shared, pasted, or edited.
- Drag to pan; mouse-wheel or pinch with two fingers to zoom; **+ / − / Fit** buttons.
- Copy source, send source using `navigator.share()`, or download rendered SVG.
- Saves the last diagram in local storage; clears incoming shared text from the URL.
- Offline-capable app shell via service worker; cache-on-use for Mermaid's CDN modules.
- No framework, build process, npm install, backend, or API key.

## Running locally

Serve these files on localhost (not via `file://`). For example:

```bash
cd mermaid-pocket
python3 -m http.server 8080
```

Then visit http://localhost:8080 in Chrome, Edge, or another modern browser. Try pasting Mermaid text into the editor or opening:

```
http://localhost:8080/index.html?text=flowchart%20LR%0A%20%20A--%3EB
```

## Install & receive shared text

1. Deploy the folder **as-is** to any static HTTPS host (for example, GitHub Pages, Netlify, or Cloudflare Pages). HTTPS is required outside localhost.
2. Open the deployed site in **Chrome for Android** and install / add the PWA to the device.
3. From any app that shares plain text, use **Share → Mermaid Pocket**. The diagram appears automatically.

**Important:** Web Share Target is supported only on platforms/browsers that expose installed PWAs as share destinations. Android Chromium is the intended target; Safari and desktop browsers may not offer that integration. The in-app Share button uses the separate outgoing Web Share API where available, or copies the text otherwise.

## Mermaid dependency / offline note

The app loads Mermaid v12.2.0 from jsDelivr on first render:

```
https://cdn.jsdelivr.net/npm/mermaid@12.2.0/dist/mermaid.esm.min.mjs
```

The PWA's own files are precached for offline use. Mermaid and its dynamically imported modules are cached at runtime while controlled by the service worker. **An internet connection is needed for the initial Mermaid load**; offline diagram rendering may not work until all required Mermaid modules have been cached. For guaranteed offline rendering from first launch, bundle Mermaid locally and update `MERMAID_MODULE` in `app.js` and the service worker's precache list.

## Files

- `index.html` — app interface
- `styles.css` — responsive styles
- `app.js` — Web Share Target handler, Mermaid renderer, pan/zoom, controls
- `manifest.webmanifest` — PWA metadata and the share target registration
- `sw.js` — offline app shell and Mermaid CDN runtime cache
- `icons/` — install icons

## Notes

Rendering and editing happen client-side without a diagram-processing backend. However, **the Share Target uses GET, so incoming text appears in the initial request URL and may be logged by your static host**. Avoid sharing secrets. The app removes those parameters from browser history once loaded. It also makes network requests for the Mermaid JavaScript library. Mermaid runs with `securityLevel: 'strict'` to restrict potentially unsafe features in incoming diagrams.
