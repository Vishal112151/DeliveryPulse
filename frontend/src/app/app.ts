import { HttpClient } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import {
  LucideActivity as Activity,
  LucideFileText as FileText,
  LucideFolderKanban as FolderKanban,
  LucideLayoutDashboard as LayoutDashboard,
  LucideLogOut as LogOut,
  LucideMenu as Menu,
  LucideTriangleAlert as TriangleAlert,
  LucideUpload as Upload,
  LucideUsers as Users,
  type LucideIconInput,
} from '@lucide/angular';
import { AuthService, Role } from './core/auth.service';
import { LucideIconCompatComponent } from './shared/lucide-icon-compat.component';

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LucideIconCompatComponent],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  protected readonly ActivityIcon = Activity;
  protected readonly MenuIcon = Menu;
  protected readonly menuOpen = signal(false);
  private readonly currentPath = signal('');
  protected readonly LogOutIcon = LogOut;
  protected readonly auth = inject(AuthService);
  private readonly allNavigation: { label: string; path: string; icon: LucideIconInput; roles?: Role[] }[] = [
    { label: 'Dashboard', path: '/dashboard', icon: LayoutDashboard },
    { label: 'Upload data', path: '/upload', icon: Upload, roles: ['ADMIN', 'MANAGER'] },
    { label: 'Projects', path: '/projects', icon: FolderKanban },
    { label: 'Risk analysis', path: '/risks', icon: TriangleAlert },
    { label: 'Reports', path: '/reports', icon: FileText },
    { label: 'Users', path: '/users', icon: Users, roles: ['ADMIN'] },
  ];
  /** Menu items for the signed-in user's role. */
  protected readonly navigation = computed(() => {
    const role = this.auth.user()?.role;
    return this.allNavigation.filter((item) => !item.roles || (role && item.roles.includes(role)));
  });
  protected readonly onLoginPage = computed(() => this.currentPath().startsWith('/login'));
  protected readonly initials = computed(() => (this.auth.user()?.displayName ?? '?')
    .split(/\s+/).map((part) => part.charAt(0)).join('').slice(0, 2).toUpperCase());
  protected readonly pageTitle = computed(() => {
    const path = this.currentPath().split('?')[0];
    if (path.startsWith('/projects/')) return 'Project details';
    return this.allNavigation.find((item) => item.path === path)?.label ?? 'Dashboard';
  });

  protected readonly apiState = signal<'checking' | 'up' | 'down'>('checking');

  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);

  constructor() {
    // ponytail: checked once at startup; poll if a live indicator is ever needed.
    inject(HttpClient).get<{ status: string }>('/actuator/health').subscribe({
      next: (health) => this.apiState.set(health.status === 'UP' ? 'up' : 'down'),
      error: () => this.apiState.set('down'),
    });
    this.currentPath.set(this.router.url);
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((event) => this.currentPath.set(event.urlAfterRedirects));
  }
}