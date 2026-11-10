import { Injectable, inject } from '@angular/core';
import { Api } from '../../core/api/generated/api';
import { getCurrentUser } from '../../core/api/generated/fn/users/get-current-user';
import { User } from '../../core/api/generated/models/user';

/** Data access of the profile feature. */
@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly api = inject(Api);

  /** The logged-in user, always read from the backend (never from the session response). */
  currentUser(): Promise<User> {
    return this.api.invoke(getCurrentUser);
  }
}
