import { Routes } from '@angular/router';
import { IngredientListComponent } from './components/ingredient-list/ingredient-list.component';
import { LoginComponent } from './components/login/login.component';
import { ShoppingListViewComponent } from './components/shopping-list-view/shopping-list-view.component';
import { ShoppingModeComponent } from './components/shopping-mode/shopping-mode.component';
import { authGuard, guestGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'inventory', pathMatch: 'full' },
  { path: 'login', component: LoginComponent, canActivate: [guestGuard] },
  { path: 'inventory', component: IngredientListComponent, canActivate: [authGuard] },
  { path: 'shopping-list', component: ShoppingListViewComponent, canActivate: [authGuard] },
  { path: 'shop', component: ShoppingModeComponent, canActivate: [authGuard] },
];
