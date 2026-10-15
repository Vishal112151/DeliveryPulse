// Dev-server proxy to the Spring Boot API.
// Proxied calls are server-to-server, so the browser's Origin header is dropped;
// otherwise POST requests fail backend CORS checks whenever the dev server runs on
// a host/port other than FRONTEND_ORIGIN (for example 127.0.0.1:4200).
const target = process.env.API_PROXY_TARGET ?? 'http://localhost:8080';

const backend = {
  target,
  secure: false,
  changeOrigin: true,
  configure: (proxy) => proxy.on('proxyReq', (proxyRequest) => proxyRequest.removeHeader('origin')),
};

export default {
  '/api': backend,
  '/actuator': backend,
};
