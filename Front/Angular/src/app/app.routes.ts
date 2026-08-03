import { Routes } from '@angular/router';
import { IngredientListComponent } from './components/ingredient-list/ingredient-list.component';
import { ShoppingListViewComponent } from './components/shopping-list-view/shopping-list-view.component';
import { ShoppingModeComponent } from './components/shopping-mode/shopping-mode.component';

export const routes: Routes = [
  { path: '', redirectTo: 'inventory', pathMatch: 'full' },
  { path: 'inventory', component: IngredientListComponent },
  { path: 'shopping-list', component: ShoppingListViewComponent },
  { path: 'shop', component: ShoppingModeComponent },
];
