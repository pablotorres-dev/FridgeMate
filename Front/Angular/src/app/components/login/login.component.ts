import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

const DEMO_CREDENTIALS = { username: 'demo', password: 'demo1234' };

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {
  mode: 'login' | 'register' = 'login';
  username = '';
  password = '';
  error: string | null = null;
  submitting = false;

  constructor(
    private authService: AuthService,
    private router: Router,
  ) {}

  get isRegister(): boolean {
    return this.mode === 'register';
  }

  switchMode(): void {
    this.mode = this.isRegister ? 'login' : 'register';
    this.error = null;
  }

  submit(): void {
    const credentials = { username: this.username.trim(), password: this.password };
    this.run(this.isRegister ? this.authService.register(credentials) : this.authService.login(credentials));
  }

  /** One-click way in for anyone who just wants to look around. */
  useDemoAccount(): void {
    this.run(this.authService.login(DEMO_CREDENTIALS));
  }

  private run(request: ReturnType<AuthService['login']>): void {
    this.submitting = true;
    this.error = null;
    request.subscribe({
      next: () => this.router.navigate(['/inventory']),
      error: (err: HttpErrorResponse) => {
        this.error = this.describe(err);
        this.submitting = false;
      },
    });
  }

  private describe(error: HttpErrorResponse): string {
    switch (error.status) {
      case 0:
        return "Couldn't reach the server. Please try again.";
      case 400:
        return 'Username must be at least 3 characters and password at least 6.';
      case 401:
        return 'Wrong username or password.';
      case 409:
        return 'That username is already taken.';
      default:
        return 'Something went wrong. Please try again.';
    }
  }
}
