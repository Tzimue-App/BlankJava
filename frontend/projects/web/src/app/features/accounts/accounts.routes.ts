import { Routes } from '@angular/router';
import { anonymousGuard } from '../../core/auth/auth.guards';
import { RegisterPage } from './register/register.page';

/** Pages of anonymous visitors: create an account (and log in, #34). */
export const ACCOUNTS_ROUTES: Routes = [
  {
    path: 'register',
    component: RegisterPage,
    canActivate: [anonymousGuard],
    title: $localize`:@@register.pageTitle:Create an account`,
  },
];
