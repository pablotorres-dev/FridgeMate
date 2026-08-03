import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ShoppingListEntry } from '../models/shopping-list-entry';
import { ShoppingListItem } from '../models/shopping-list-item';

@Injectable({ providedIn: 'root' })
export class ShoppingListService {
  private readonly baseUrl = `${environment.apiUrl}/shopping-list`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<ShoppingListItem[]> {
    return this.http.get<ShoppingListItem[]>(this.baseUrl);
  }

  getNeeded(): Observable<ShoppingListEntry[]> {
    return this.http.get<ShoppingListEntry[]>(`${this.baseUrl}/needed`);
  }

  create(item: ShoppingListItem): Observable<ShoppingListItem> {
    return this.http.post<ShoppingListItem>(this.baseUrl, item);
  }

  update(id: number, item: ShoppingListItem): Observable<ShoppingListItem> {
    return this.http.put<ShoppingListItem>(`${this.baseUrl}/${id}`, item);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
