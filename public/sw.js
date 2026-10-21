const CACHE = "jus-merdeka-shell-v2";
const SHELL = ["/offline.html", "/icon.svg", "/icon-maskable.svg", "/manifest.webmanifest"];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(SHELL)));
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key)))));
  self.clients.claim();
});

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET" || event.request.url.includes("/api/")) return;
  if (event.request.mode === "navigate") {
    event.respondWith(fetch(event.request).catch(() => caches.match("/offline.html")));
    return;
  }
  const url = new URL(event.request.url);
  if (url.origin === self.location.origin && (url.pathname.startsWith("/_next/static/") || SHELL.includes(url.pathname))) {
    event.respondWith(caches.match(event.request).then((cached) => cached || fetch(event.request).then((response) => { const copy=response.clone();void caches.open(CACHE).then((cache)=>cache.put(event.request,copy));return response; })));
  }
});
