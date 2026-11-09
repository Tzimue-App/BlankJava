import { Routes } from '@angular/router';
import { authGuard } from '../../core/auth/auth.guards';
import { ProfilePage } from './profile.page';

/** Pages of a logged-in user. */
export const PROFILE_ROUTES: Routes = [
  {
    path: 'me',
    component: ProfilePage,
    canActivate: [authGuard],
    title: $localize`:@@profile.pageTitle:My profile`,
  },
];
