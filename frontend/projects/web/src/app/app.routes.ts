import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { AuthSession } from './core/auth/auth-session';
import { Shell } from './layout/shell/shell';

/**
 * Every feature is lazy-loaded (docs/technical_analysis.md §3.4): adding one does not grow the
 * initial bundle. `/` leads to the profile of a logged-in user, or to the login page. Unknown paths
 * render the not-found page inside the shell.
 */
export const routes: Routes = [
  {
    path: '',
    component: Shell,
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: () => (inject(AuthSession).isAuthenticated() ? '/me' : '/login'),
      },
      {
        path: '',
        loadChildren: () =>
          import('./features/accounts/accounts.routes').then((m) => m.ACCOUNTS_ROUTES),
      },
      {
        path: '',
        loadChildren: () =>
          import('./features/profile/profile.routes').then((m) => m.PROFILE_ROUTES),
      },
      {
        path: '**',
        title: 'Page not found',
        loadComponent: () => import('./layout/not-found/not-found').then((m) => m.NotFound),
      },
    ],
  },
];
