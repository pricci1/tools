// No build tools required. Mermaid loads from jsDelivr on first use.
// It is cached by the service worker on subsequent controlled visits.
const MERMAID_MODULE = 'https://cdn.jsdelivr.net/npm/mermaid@12.2.0/dist/mermaid.esm.min.mjs';
const STORAGE_KEY = 'mermaid-pocket-source-v1';
const SAMPLE = `flowchart LR
    A["Share Mermaid text"] --> B["Mermaid Pocket"]
    B --> C{"Valid diagram?"}
    C -->|Yes| D["Zoom & pan"]
    C -->|No| E["Edit source"]
    E --> B`;

const el = {
  source: document.querySelector('#sourceInput'),
  viewer: document.querySelector('.viewer-panel'),
  viewport: document.querySelector('#viewport'),
  layer: document.querySelector('#diagramLayer'),
  empty: document.querySelector('#emptyState'),
  error: document.querySelector('#errorBox'),
  errorMessage: document.querySelector('#errorMessage'),
  status: document.querySelector('#status'),
  statusText: document.querySelector('#statusText'),
  zoomLevel: document.querySelector('#zoomLevel'),
  render: document.querySelector('#renderBtn'),
  example: document.querySelector('#exampleBtn'),
  copy: document.querySelector('#copyBtn'),
  share: document.querySelector('#shareBtn'),
  install: document.querySelector('#installBtn'),
  download: document.querySelector('#downloadBtn'),
  zoomIn: document.querySelector('#zoomInBtn'),
  zoomOut: document.querySelector('#zoomOutBtn'),
  fit: document.querySelector('#fitBtn'),
  fullscreen: document.querySelector('#fullscreenBtn')
};

const view = { x: 0, y: 0, scale: 1, width: 0, height: 0 };
// Install SW early, so the initial CDN import may be cached.
const serviceWorkerRegistration = ('serviceWorker' in navigator)
  ? navigator.serviceWorker.register('./sw.js', { scope: './' })
      .catch((error) => { console.warn('Service worker registration failed', error); return null; })
  : Promise.resolve(null);

async function allowServiceWorkerToControl() {
  if (!('serviceWorker' in navigator) || !window.isSecureContext) return;
  // On first install, activation and clients.claim() can lag page load.
  try {
    const registration = await serviceWorkerRegistration;
    if (!registration || navigator.serviceWorker.controller) return;
    await Promise.race([
      new Promise((resolve) => navigator.serviceWorker.addEventListener('controllerchange', resolve, { once: true })),
      new Promise((resolve) => setTimeout(resolve, 2500))
    ]);
  } catch (_) { /* App is still usable online without a SW. */ }
}

const pointers = new Map();
let lastPinch = null;
let modulePromise = null;
let renderNumber = 0;
let latestRequest = 0;
let renderQueue = Promise.resolve();
let debounceId;
let deferredInstallPrompt = null;

function report(message, state = 'ready') {
  el.status.dataset.state = state;
  el.statusText.textContent = message;
}

function putError(message) {
  el.errorMessage.textContent = String(message);
  el.error.hidden = false;
  report('Render failed', 'error');
}

function clearError() { el.error.hidden = true; }

// Share sheets may send raw syntax or a ```mermaid Markdown code fence.
function diagramSource(text) {
  const trimmed = String(text || '').trim();
  const fenced = trimmed.match(/```(?:mermaid|mmd)\s*\r?\n([\s\S]*?)\r?\n```/i);
  if (fenced) return fenced[1].trim();
  return trimmed.replace(/^```\s*\r?\n([\s\S]*?)\r?\n```$/i, '$1').trim();
}

function saveSource() {
  try { localStorage.setItem(STORAGE_KEY, el.source.value); } catch (_) { /* storage unavailable */ }
}

function readInitialSource() {
  const params = new URLSearchParams(window.location.search);
  const fromShare = ['text', 'url', 'title'].some((key) => params.has(key));
  if (fromShare) {
    // Text has precedence; links and titles are only used when no text was sent.
    const value = [params.get('text'), params.get('url'), params.get('title')]
      .find((item) => item && item.trim());
    el.source.value = value || '';
    saveSource();
    // Do not leave potentially private shared content in the URL/history.
    history.replaceState(null, '', window.location.pathname + window.location.hash);
    report('Shared text received');
    return;
  }
  try { el.source.value = localStorage.getItem(STORAGE_KEY) ?? SAMPLE; }
  catch (_) { el.source.value = SAMPLE; }
}

async function getMermaid() {
  if (!modulePromise) {
    modulePromise = allowServiceWorkerToControl().then(() => import(MERMAID_MODULE)).then(({ default: mermaid }) => {
      mermaid.initialize({
        startOnLoad: false,
        securityLevel: 'strict',
        theme: 'default',
        themeVariables: { fontFamily: 'Inter, Arial, sans-serif' },
        flowchart: { htmlLabels: false, useMaxWidth: false }
      });
      return mermaid;
    }).catch((err) => {
      modulePromise = null; // Allow retry after temporary offline/network errors.
      throw err;
    });
  }
  return modulePromise;
}

function clamp(number, min, max) { return Math.min(max, Math.max(min, number)); }
function redrawTransform() {
  el.layer.style.transform = `translate(${view.x}px, ${view.y}px) scale(${view.scale})`;
  el.zoomLevel.textContent = `${Math.round(view.scale * 100)}%`;
}

function scaleAt(nextScale, localX, localY) {
  if (!view.width) return;
  const scale = clamp(nextScale, .02, 16);
  const worldX = (localX - view.x) / view.scale;
  const worldY = (localY - view.y) / view.scale;
  view.scale = scale;
  view.x = localX - worldX * scale;
  view.y = localY - worldY * scale;
  redrawTransform();
}

function zoomBy(factor) {
  scaleAt(view.scale * factor, el.viewport.clientWidth / 2, el.viewport.clientHeight / 2);
}

function fitView() {
  if (!view.width || !view.height) return;
  const boundsW = el.viewport.clientWidth;
  const boundsH = el.viewport.clientHeight;
  if (!boundsW || !boundsH) return;
  const padding = 36;
  view.scale = clamp(Math.min((boundsW - padding * 2) / view.width,
    (boundsH - padding * 2) / view.height, 2), .02, 16);
  view.x = (boundsW - view.width * view.scale) / 2;
  view.y = (boundsH - view.height * view.scale) / 2;
  redrawTransform();
}

function positionInViewport(clientX, clientY) {
  const bounds = el.viewport.getBoundingClientRect();
  return { x: clientX - bounds.left, y: clientY - bounds.top };
}

function pinchInfo() {
  if (pointers.size < 2) return null;
  const [p1, p2] = [...pointers.values()].slice(0, 2);
  return {
    x: (p1.x + p2.x) / 2,
    y: (p1.y + p2.y) / 2,
    distance: Math.hypot(p1.x - p2.x, p1.y - p2.y)
  };
}

el.viewport.addEventListener('wheel', (event) => {
  if (!view.width) return;
  event.preventDefault();
  const { x, y } = positionInViewport(event.clientX, event.clientY);
  scaleAt(view.scale * Math.exp(-event.deltaY * .0015), x, y);
}, { passive: false });

el.viewport.addEventListener('pointerdown', (event) => {
  if (!view.width || event.button !== 0) return;
  const point = positionInViewport(event.clientX, event.clientY);
  pointers.set(event.pointerId, point);
  el.viewport.setPointerCapture(event.pointerId);
  lastPinch = pinchInfo();
});

el.viewport.addEventListener('pointermove', (event) => {
  if (!pointers.has(event.pointerId)) return;
  const previous = pointers.get(event.pointerId);
  const current = positionInViewport(event.clientX, event.clientY);
  pointers.set(event.pointerId, current);
  if (pointers.size === 1) {
    view.x += current.x - previous.x;
    view.y += current.y - previous.y;
    redrawTransform();
  } else {
    const now = pinchInfo();
    if (now && lastPinch && lastPinch.distance > 0 && now.distance > 0) {
      scaleAt(view.scale * (now.distance / lastPinch.distance), lastPinch.x, lastPinch.y);
      view.x += now.x - lastPinch.x;
      view.y += now.y - lastPinch.y;
      redrawTransform();
    }
    lastPinch = now;
  }
});

function pointerEnd(event) {
  pointers.delete(event.pointerId);
  lastPinch = pinchInfo();
}
el.viewport.addEventListener('pointerup', pointerEnd);
el.viewport.addEventListener('pointercancel', pointerEnd);
el.viewport.addEventListener('lostpointercapture', pointerEnd);
el.viewport.addEventListener('keydown', (event) => {
  if (event.key === '+' || event.key === '=') { event.preventDefault(); zoomBy(1.22); }
  if (event.key === '-') { event.preventDefault(); zoomBy(1 / 1.22); }
  if (event.key === '0') { event.preventDefault(); fitView(); }
});
el.zoomIn.addEventListener('click', () => zoomBy(1.25));
el.zoomOut.addEventListener('click', () => zoomBy(.8));
el.fit.addEventListener('click', fitView);

function setFullscreen(active) {
  el.viewer.classList.toggle('is-fullscreen', active);
  el.fullscreen.setAttribute('aria-pressed', String(active));
  el.fullscreen.title = active ? 'Exit fullscreen' : 'Fullscreen diagram';
  el.fullscreen.setAttribute('aria-label', el.fullscreen.title);
  requestAnimationFrame(fitView);
  if (!active) el.fullscreen.focus();
}

el.fullscreen.addEventListener('click', async () => {
  if (document.fullscreenElement === el.viewer) {
    try { await document.exitFullscreen(); }
    catch (_) { report('Could not exit fullscreen', 'error'); }
    return;
  }
  const active = !el.viewer.classList.contains('is-fullscreen');
  setFullscreen(active);
  if (active && document.fullscreenEnabled && el.viewer.requestFullscreen) {
    try { await el.viewer.requestFullscreen(); }
    catch (_) { /* Keep the page-filling view if native fullscreen is unavailable. */ }
  }
});
document.addEventListener('fullscreenchange', () => {
  setFullscreen(document.fullscreenElement === el.viewer);
});
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape' && el.viewer.classList.contains('is-fullscreen')) {
    event.preventDefault();
    el.fullscreen.click();
  }
});

function setSvgDimensions(svg) {
  const viewBox = svg.viewBox?.baseVal;
  let w = viewBox?.width || 0;
  let h = viewBox?.height || 0;
  if (!(w > 0 && h > 0)) {
    try {
      const box = svg.getBBox();
      w = box.width;
      h = box.height;
    } catch (_) { /* DOM may not be laid out yet */ }
  }
  view.width = clamp(w || 800, 1, 50000);
  view.height = clamp(h || 500, 1, 50000);
  svg.setAttribute('width', String(view.width));
  svg.setAttribute('height', String(view.height));
  svg.style.width = `${view.width}px`;
  svg.style.height = `${view.height}px`;
  svg.style.maxWidth = 'none';
}

function queueRender() {
  const request = ++latestRequest;
  // Serial rendering avoids Mermaid's temporary SVG nodes colliding.
  renderQueue = renderQueue.catch(() => {}).then(async () => {
    if (request !== latestRequest) return;
    const source = diagramSource(el.source.value);
    clearError();
    if (!source) {
      el.layer.replaceChildren();
      view.width = view.height = 0;
      el.empty.hidden = false;
      el.zoomLevel.textContent = '—';
      report('No diagram text');
      return;
    }
    report('Rendering diagram…', 'working');
    try {
      const mermaid = await getMermaid();
      if (request !== latestRequest) return;
      const id = `pocket-mermaid-${++renderNumber}`;
      const { svg, bindFunctions } = await mermaid.render(id, source);
      if (request !== latestRequest) return;
      el.layer.innerHTML = svg;
      if (bindFunctions) bindFunctions(el.layer);
      const element = el.layer.querySelector('svg');
      if (!element) throw new Error('The renderer returned no SVG.');
      setSvgDimensions(element);
      el.empty.hidden = true;
      clearError();
      fitView();
      report('Diagram updated');
    } catch (error) {
      if (request !== latestRequest) return;
      const maybeNetworkError = !navigator.onLine || error instanceof TypeError;
      putError(maybeNetworkError
        ? 'Mermaid could not load. Connect to the internet for the first render, then try again.\n\n' + (error?.message || '')
        : (error?.message || 'Invalid Mermaid syntax.'));
      if (!el.layer.querySelector('svg')) el.empty.hidden = false;
    }
  });
}

el.source.addEventListener('input', () => {
  saveSource();
  clearTimeout(debounceId);
  debounceId = setTimeout(queueRender, 650);
});
el.source.addEventListener('keydown', (event) => {
  if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
    event.preventDefault();
    clearTimeout(debounceId);
    queueRender();
  }
  if (event.key === 'Tab' && !event.metaKey && !event.ctrlKey && !event.shiftKey) {
    event.preventDefault();
    const begin = el.source.selectionStart;
    el.source.setRangeText('  ', begin, el.source.selectionEnd, 'end');
    el.source.dispatchEvent(new Event('input', { bubbles: true }));
  }
});
el.render.addEventListener('click', () => { clearTimeout(debounceId); queueRender(); });
el.example.addEventListener('click', () => { el.source.value = SAMPLE; saveSource(); clearTimeout(debounceId); queueRender(); });

async function copyText() {
  const text = el.source.value;
  try {
    if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(text);
    else {
      el.source.focus();
      el.source.select();
      if (!document.execCommand('copy')) throw new Error('Clipboard not supported');
    }
    report('Mermaid text copied');
  } catch (_) { report('Could not copy text', 'error'); }
}
el.copy.addEventListener('click', copyText);
el.share.addEventListener('click', async () => {
  if (!el.source.value.trim()) { report('Enter some Mermaid text first', 'error'); return; }
  if (!navigator.share) { await copyText(); return; }
  try { await navigator.share({ title: 'Mermaid diagram', text: el.source.value }); report('Shared successfully'); }
  catch (error) { if (error?.name !== 'AbortError') report('Share unavailable', 'error'); }
});

el.download.addEventListener('click', () => {
  const diagram = el.layer.querySelector('svg');
  if (!diagram) { report('Render a diagram before saving', 'error'); return; }
  const clone = diagram.cloneNode(true);
  clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
  const xml = new XMLSerializer().serializeToString(clone);
  const blob = new Blob([xml], { type: 'image/svg+xml;charset=utf-8' });
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = 'mermaid-diagram.svg';
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
  report('SVG downloaded');
});

// Keep fit after orientation / window size changes.
let resizeId;
window.addEventListener('resize', () => {
  clearTimeout(resizeId);
  resizeId = setTimeout(fitView, 160);
});

// Chromium exposes this event when the app is installable.
window.addEventListener('beforeinstallprompt', (event) => {
  event.preventDefault();
  deferredInstallPrompt = event;
  el.install.hidden = false;
});
el.install.addEventListener('click', async () => {
  if (!deferredInstallPrompt) return;
  deferredInstallPrompt.prompt();
  await deferredInstallPrompt.userChoice;
  deferredInstallPrompt = null;
  el.install.hidden = true;
});
window.addEventListener('appinstalled', () => { el.install.hidden = true; report('App installed'); });

readInitialSource();
queueRender();
