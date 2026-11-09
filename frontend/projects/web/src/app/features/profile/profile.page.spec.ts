import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { User } from '../../core/api/generated/models/user';
import { ProfilePage } from './profile.page';

const ME = '/api/users/me';
const JANE: User = {
  id: '7b2f4c1e-9a8d-4b6e-8f3a-2d1c0b9e8a7f',
  email: 'jane@example.com',
  displayName: 'Jane',
  createdAt: '2026-11-05T10:15:30Z',
};

describe('ProfilePage', () => {
  let fixture: ComponentFixture<ProfilePage>;
  let backend: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProfilePage);
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  const element = (testId: string): HTMLElement | null =>
    fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);

  async function settle(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
  }

  it('shows a loading state, then the profile read from the backend', async () => {
    expect(element('profile-loading')?.getAttribute('role')).toBe('status');

    backend.expectOne(ME).flush(JANE);
    await settle();

    expect(element('profile-loading')).toBeNull();
    expect(element('profile-display-name')?.tagName).toBe('H1');
    expect(element('profile-display-name')?.textContent?.trim()).toBe('Jane');
    expect(element('profile-email')?.textContent?.trim()).toBe('jane@example.com');
    const since = element('profile-member-since')!;
    expect(since.getAttribute('datetime')).toBe('2026-11-05T10:15:30Z');
    expect(since.textContent?.trim()).toBe(
      new Intl.DateTimeFormat(undefined, { dateStyle: 'long' }).format(new Date(JANE.createdAt)),
    );
  });

  it('renders the display name as text, never as HTML', async () => {
    const hostile = '<img src=x onerror=alert(1)>';
    backend.expectOne(ME).flush({ ...JANE, displayName: hostile });
    await settle();

    const name = element('profile-display-name')!;
    expect(name.textContent?.trim()).toBe(hostile);
    expect(name.querySelector('img')).toBeNull();
  });

  it('shows the error banner with the trace id on a server error, and can try again', async () => {
    backend
      .expectOne(ME)
      .flush(
        {
          type: 'about:blank',
          title: 'Error',
          status: 500,
          traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
        },
        { status: 500, statusText: 'Server Error' },
      );
    await settle();

    expect(element('profile-error-banner')?.getAttribute('role')).toBe('alert');
    expect(element('profile-trace-id')?.textContent).toBe('4bf92f3577b34da6a3ce929d0e0e4736');
    expect(element('profile-display-name')).toBeNull();

    element('profile-retry')!.click();
    await fixture.whenStable();
    expect(element('profile-loading')).not.toBeNull();
    backend.expectOne(ME).flush(JANE);
    await settle();
    expect(element('profile-display-name')?.textContent?.trim()).toBe('Jane');
  });

  it('shows the banner without trace id when the backend cannot be reached', async () => {
    backend.expectOne(ME).error(new ProgressEvent('error'));
    await settle();

    expect(element('profile-error-banner')).not.toBeNull();
    expect(element('profile-trace-id')).toBeNull();
  });
});
