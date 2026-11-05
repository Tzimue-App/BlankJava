import { DOCUMENT } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { STORAGE, WINDOW } from './tokens';

describe('platform tokens', () => {
  it('WINDOW is the default view of the document', () => {
    expect(TestBed.inject(WINDOW)).toBe(TestBed.inject(DOCUMENT).defaultView);
  });

  it('STORAGE is the local storage of the window', () => {
    expect(TestBed.inject(STORAGE)).toBe(TestBed.inject(WINDOW)?.localStorage);
  });

  it('STORAGE and WINDOW are null without a browser window (server rendering)', () => {
    TestBed.configureTestingModule({ providers: [{ provide: WINDOW, useValue: null }] });

    expect(TestBed.inject(STORAGE)).toBeNull();
  });

  it('STORAGE is null when the browser refuses access', () => {
    const blocked = {
      get localStorage(): Storage {
        throw new DOMException('Access denied', 'SecurityError');
      },
    } as unknown as Window;
    TestBed.configureTestingModule({ providers: [{ provide: WINDOW, useValue: blocked }] });

    expect(TestBed.inject(STORAGE)).toBeNull();
  });
});
