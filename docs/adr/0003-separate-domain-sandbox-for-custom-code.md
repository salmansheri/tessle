# Custom HTML and React Pages run on a separate domain

Custom code runs in a sandboxed iframe (no `allow-same-origin`) served from a completely different registrable domain than the app, not a sibling subdomain, so it can never share cookie scope with the platform. It talks to the platform only through a postMessage SDK using short-lived scoped tokens. Publishing custom code needs its own permission, separate from publishing a Page. Per-Page unique subdomains were deferred, not rejected.
