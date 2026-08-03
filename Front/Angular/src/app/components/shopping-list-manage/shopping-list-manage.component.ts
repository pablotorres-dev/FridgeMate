import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ShoppingListItemFormComponent } from '../shopping-list-item-form/shopping-list-item-form.component';
import { ShoppingListItem } from '../../models/shopping-list-item';
import { ShoppingListService } from '../../services/shopping-list.service';

@Component({
  selector: 'app-shopping-list-manage',
  standalone: true,
  imports: [CommonModule, ShoppingListItemFormComponent],
  templateUrl: './shopping-list-manage.component.html',
  styleUrl: './shopping-list-manage.component.css',
})
export class ShoppingListManageComponent implements OnInit {
  items: ShoppingListItem[] = [];
  loading = false;
  error: string | null = null;
  editingItem: ShoppingListItem | null = null;
  showForm = false;

  constructor(private shoppingListService: ShoppingListService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;
    this.shoppingListService.getAll().subscribe({
      next: (data) => {
        this.items = data;
        this.loading = false;
      },
      error: () => {
        this.error = 'No se ha podido conectar con la API. ¿Está el backend arrancado?';
        this.loading = false;
      },
    });
  }

  toggleForm(): void {
    this.showForm = !this.showForm;
    if (!this.showForm) {
      this.editingItem = null;
    }
  }

  editItem(item: ShoppingListItem): void {
    this.editingItem = item;
    this.showForm = true;
  }

  cancelEdit(): void {
    this.editingItem = null;
    this.showForm = false;
  }

  saveItem(item: ShoppingListItem): void {
    const request =
      this.editingItem?.id != null
        ? this.shoppingListService.update(this.editingItem.id, item)
        : this.shoppingListService.create(item);

    request.subscribe(() => {
      this.editingItem = null;
      this.showForm = false;
      this.load();
    });
  }

  deleteItem(id: number | undefined): void {
    if (id == null) {
      return;
    }
    this.shoppingListService.delete(id).subscribe(() => this.load());
  }
}
