const CACHE_NAME = 'smart-event-bar-v1';
const SHELL_ASSETS = [
  '/',
  '/index.html',
];

// Install: cache the app shell
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(SHELL_ASSETS))
  );
  self.skipWaiting();
});

// Activate: clean up old caches
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))
    )
  );
  self.clients.claim();
});

// Fetch: stale-while-revalidate for menu data, cache-first for shell
self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // Stale-while-revalidate for menu API calls
  if (url.pathname.includes('/api/stations/') && url.pathname.includes('/menu')) {
    event.respondWith(
      caches.open(CACHE_NAME).then((cache) =>
        cache.match(event.request).then((cachedResponse) => {
          const fetchPromise = fetch(event.request)
            .then((networkResponse) => {
              cache.put(event.request, networkResponse.clone());
              return networkResponse;
            })
            .catch(() => cachedResponse);

          return cachedResponse || fetchPromise;
        })
      )
    );
    return;
  }

  // Cache-first for static assets
  if (event.request.destination === 'script' ||
      event.request.destination === 'style' ||
      event.request.destination === 'document') {
    event.respondWith(
      caches.match(event.request).then((response) =>
        response || fetch(event.request).then((networkResponse) => {
          return caches.open(CACHE_NAME).then((cache) => {
            cache.put(event.request, networkResponse.clone());
            return networkResponse;
          });
        })
      )
    );
    return;
  }

  // Network-first for everything else
  event.respondWith(
    fetch(event.request).catch(() => caches.match(event.request))
  );
});
