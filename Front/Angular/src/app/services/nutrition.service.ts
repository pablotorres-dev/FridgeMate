import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { NutritionReport } from '../models/nutrition-report';

@Injectable({ providedIn: 'root' })
export class NutritionService {
  private readonly baseUrl = `${environment.apiUrl}/nutrition`;

  constructor(private http: HttpClient) {}

  /** Whether the server has an AI provider configured at all. */
  getStatus(): Observable<{ available: boolean }> {
    return this.http.get<{ available: boolean }>(`${this.baseUrl}/status`);
  }

  analyse(): Observable<NutritionReport> {
    return this.http.get<NutritionReport>(this.baseUrl);
  }
}
