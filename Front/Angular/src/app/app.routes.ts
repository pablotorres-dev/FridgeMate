import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './guards/auth.guard';

/**
 * Pages are loaded on demand rather than bundled together, so a first visit
 * downloads little more than the login screen. That matters most on a phone
 * on mobile data, which is where this app is actually used.
 */
export const routes: Routes = [
  { path: '', redirectTo: 'inventory', pathMatch: 'full' },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./components/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'inventory',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./components/ingredient-list/ingredient-list.component').then((m) => m.IngredientListComponent),
  },
  {
    path: 'shopping-list',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./components/shopping-list-view/shopping-list-view.component').then((m) => m.ShoppingListViewComponent),
  },
  {
    path: 'shop',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./components/shopping-mode/shopping-mode.component').then((m) => m.ShoppingModeComponent),
  },
];
