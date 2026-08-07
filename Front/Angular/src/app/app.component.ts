import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-root',
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'fridgemate';

  constructor(
    private authService: AuthService,
    private router: Router,
  ) {}

  get username(): string | null {
    return this.authService.currentUser?.username ?? null;
  }

  /** The nav belongs to the app itself, so it stays hidden on the login screen. */
  get isSignedIn(): boolean {
    return this.authService.currentUser !== null;
  }

  logout(): void {
    this.authService.logout().subscribe(() => this.router.navigate(['/login']));
  }
}
