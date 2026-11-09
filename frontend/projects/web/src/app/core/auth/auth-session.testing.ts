import { SessionResponse } from '../api/generated/models/session-response';

/** A session response for tests, with the given access token. */
export function sessionFor(token: string): SessionResponse {
  return {
    accessToken: token,
    expiresIn: 600,
    user: {
      id: '7b2f4c1e-9a8d-4b6e-8f3a-2d1c0b9e8a7f',
      email: 'jane@example.com',
      displayName: 'Jane',
      createdAt: '2026-11-05T10:15:30Z',
    },
  };
}
