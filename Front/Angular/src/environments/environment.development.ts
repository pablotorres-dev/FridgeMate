export const environment = {
  production: false,
  // The backend only serves plain HTTP. When the frontend is loaded over
  // HTTPS (self-signed, for camera access on mobile), this becomes a
  // mixed-content request that the browser blocks by default — the user
  // needs to allow "insecure content" for this site to let it through.
  apiUrl: `http://${location.hostname}:8080/api`,
};
