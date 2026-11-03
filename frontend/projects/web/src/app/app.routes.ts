import { Routes } from '@angular/router';
import { Shell } from './layout/shell/shell';

/**
 * Every feature is lazy-loaded (docs/technical_analysis.md §3.4): adding one does not grow the
 * initial bundle. Unknown paths render the not-found page inside the shell.
 */
export const routes: Routes = [
  {
    path: '',
    component: Shell,
    children: [
      {
        path: '',
        loadChildren: () => import('./features/home/home.routes').then((m) => m.HOME_ROUTES),
      },
      {
        path: '**',
        title: 'Page not found',
        loadComponent: () => import('./layout/not-found/not-found').then((m) => m.NotFound),
      },
    ],
  },
];
