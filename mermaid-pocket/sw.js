/* Static PWA shell, plus runtime caching for Mermaid's CDN ESM modules. */
const CACHE_VERSION = 'mermaid-pocket-v2';
const SHELL_CACHE = `${CACHE_VERSION}-shell`;
const RUNTIME_CACHE = `${CACHE_VERSION}-runtime`;
const APP_SHELL = [
  './',
  './index.html',
  './styles.css?v=2',
  './app.js?v=2',
  './manifest.webmanifest',
  './icons/icon-192.png',
  './icons/icon-512.png'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(SHELL_CACHE)
      .then((cache) => cache.addAll(APP_SHELL))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys
        .filter((key) => key.startsWith('mermaid-pocket-') && ![SHELL_CACHE, RUNTIME_CACHE].includes(key))
        .map((key) => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  const sameOrigin = url.origin === self.location.origin;
  const mermaidCDN = url.origin === 'https://cdn.jsdelivr.net' && url.pathname.includes('/npm/mermaid@');

  // Handle incoming /index.html?text=... share navigations even if offline.
  if (request.mode === 'navigate' && sameOrigin) {
    event.respondWith((async () => {
      try {
        const response = await fetch(request);
        if (response.ok) return response;
      } catch (_) { /* offline */ }
      return (await caches.match('./index.html')) || Response.error();
    })());
    return;
  }

  if (sameOrigin || mermaidCDN) {
    event.respondWith((async () => {
      const cache = await caches.open(sameOrigin ? SHELL_CACHE : RUNTIME_CACHE);
      const cached = await cache.match(request);
      if (cached) return cached;
      const response = await fetch(request);
      if (response.ok) {
        await cache.put(request, response.clone());
      }
      return response;
    })());
  }
});
