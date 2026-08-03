import { Component } from '@angular/core';
import { ShoppingListManageComponent } from '../shopping-list-manage/shopping-list-manage.component';
import { ShoppingListNeededComponent } from '../shopping-list-needed/shopping-list-needed.component';

@Component({
  selector: 'app-shopping-list-view',
  standalone: true,
  imports: [ShoppingListNeededComponent, ShoppingListManageComponent],
  templateUrl: './shopping-list-view.component.html',
  styleUrl: './shopping-list-view.component.css',
})
export class ShoppingListViewComponent {}
