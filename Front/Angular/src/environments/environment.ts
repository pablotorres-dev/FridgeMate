export const environment = {
  production: true,
  // In production the Angular build is served by the Spring Boot app itself,
  // so the API lives on the same origin — a relative path keeps it working
  // on any domain without rebuilding.
  apiUrl: '/api',
};
