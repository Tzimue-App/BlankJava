import { Routes } from '@angular/router';
import { anonymousGuard } from '../../core/auth/auth.guards';
import { LoginPage } from './login/login.page';
import { RegisterPage } from './register/register.page';

/** Pages of anonymous visitors: create an account, log in. */
export const ACCOUNTS_ROUTES: Routes = [
  {
    path: 'register',
    component: RegisterPage,
    canActivate: [anonymousGuard],
    title: $localize`:@@register.pageTitle:Create an account`,
  },
  {
    path: 'login',
    component: LoginPage,
    canActivate: [anonymousGuard],
    title: $localize`:@@login.pageTitle:Log in`,
  },
];
