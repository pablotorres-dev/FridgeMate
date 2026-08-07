import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../services/auth.service';

/** Keeps the app's pages behind a valid session, sending anyone else to the login screen. */
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.loadSession().pipe(
    map((user) => (user ? true : router.createUrlTree(['/login']))),
  );
};

/** The mirror image: someone already signed in has no reason to see the login screen. */
export const guestGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.loadSession().pipe(
    map((user) => (user ? router.createUrlTree(['/inventory']) : true)),
  );
};
