import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Two jobs, both needed because the auth token lives in a cookie:
 *
 * 1. `withCredentials` so the browser attaches the cookie. It does that on its
 *    own in production (same origin), but not during development, where the dev
 *    server and the API sit on different ports.
 * 2. Turning a 401 into a redirect to the login page, which is what happens when
 *    the token finally expires while the app is open.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const authService = inject(AuthService);

  return next(req.clone({ withCredentials: true })).pipe(
    catchError((error: HttpErrorResponse) => {
      // The session check on startup is expected to 401 when signed out —
      // letting it redirect would trap the login page in a loop.
      const isSessionProbe = req.url.endsWith('/auth/me');
      if (error.status === 401 && !isSessionProbe) {
        authService.currentUser = null;
        router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};
