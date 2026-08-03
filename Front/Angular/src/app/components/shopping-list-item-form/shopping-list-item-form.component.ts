import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ShoppingListItem } from '../../models/shopping-list-item';

const EMPTY_ITEM: ShoppingListItem = {
  name: '',
  unit: '',
  minQuantity: 1,
};

@Component({
  selector: 'app-shopping-list-item-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './shopping-list-item-form.component.html',
  styleUrl: './shopping-list-item-form.component.css',
})
export class ShoppingListItemFormComponent implements OnChanges {
  @Input() item: ShoppingListItem | null = null;
  @Output() save = new EventEmitter<ShoppingListItem>();
  @Output() cancel = new EventEmitter<void>();

  model: ShoppingListItem = { ...EMPTY_ITEM };

  ngOnChanges(): void {
    this.model = this.item ? { ...this.item } : { ...EMPTY_ITEM };
  }

  get isEditing(): boolean {
    return this.item?.id != null;
  }

  submit(): void {
    this.save.emit({
      ...this.model,
      unit: this.model.unit || undefined,
    });
    if (!this.isEditing) {
      this.model = { ...EMPTY_ITEM };
    }
  }

  cancelEdit(): void {
    this.cancel.emit();
  }
}
