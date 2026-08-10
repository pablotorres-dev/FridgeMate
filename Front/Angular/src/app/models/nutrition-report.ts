export type NutrientLevel = 'LOW' | 'MODERATE' | 'GOOD';

export interface NutrientAmount {
  name: string;
  amount: string;
}

export interface Micronutrient {
  name: string;
  amount: string;
  level: NutrientLevel;
  sources: string[];
}

export interface NutritionReport {
  summary: string;
  totalCalories: number;
  macros: NutrientAmount[];
  micronutrients: Micronutrient[];
  gaps: string[];
}
