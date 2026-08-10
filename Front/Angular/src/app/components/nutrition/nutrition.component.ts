import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { NutritionReport } from '../../models/nutrition-report';
import { NutritionService } from '../../services/nutrition.service';

@Component({
  selector: 'app-nutrition',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './nutrition.component.html',
  styleUrl: './nutrition.component.css',
})
export class NutritionComponent implements OnInit {
  report: NutritionReport | null = null;
  loading = false;
  error: string | null = null;
  available = true;

  constructor(private nutritionService: NutritionService) {}

  ngOnInit(): void {
    // Checked up front so the page can explain itself rather than only
    // failing once someone presses the button.
    this.nutritionService.getStatus().subscribe({
      next: (status) => (this.available = status.available),
      error: () => (this.available = false),
    });
  }

  analyse(): void {
    this.loading = true;
    this.error = null;
    this.nutritionService.analyse().subscribe({
      next: (report) => {
        this.report = report;
        this.loading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.error = this.describe(err);
        this.loading = false;
      },
    });
  }

  levelClass(level: string): string {
    return `level-${level.toLowerCase()}`;
  }

  private describe(error: HttpErrorResponse): string {
    switch (error.status) {
      case 0:
        return "Couldn't reach the server. Please try again.";
      case 400:
        return 'Add some items to your inventory first — there is nothing to analyse yet.';
      case 502:
        return "The nutrition service didn't respond. Please try again in a moment.";
      case 503:
        return 'Nutrition analysis is not configured on this server.';
      default:
        return 'Something went wrong. Please try again.';
    }
  }
}
