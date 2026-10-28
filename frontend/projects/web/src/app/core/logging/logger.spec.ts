import { ErrorHandler } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ClientLogShipper } from './client-log-shipper';
import { GlobalErrorHandler } from './global-error-handler';
import { LOG_DEBUG_ENABLED, LoggerService } from './logger';

describe('LoggerService', () => {
  const enqueue = vi.fn();

  const setup = (debugEnabled: boolean) => {
    enqueue.mockReset();
    TestBed.configureTestingModule({
      providers: [
        { provide: ClientLogShipper, useValue: { enqueue } },
        { provide: LOG_DEBUG_ENABLED, useValue: debugEnabled },
        { provide: ErrorHandler, useClass: GlobalErrorHandler },
      ],
    });
    return TestBed.inject(LoggerService);
  };

  beforeEach(() => {
    vi.spyOn(console, 'debug').mockImplementation(() => undefined);
    vi.spyOn(console, 'info').mockImplementation(() => undefined);
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
  });

  afterEach(() => vi.restoreAllMocks());

  it('writes debug messages only when enabled (development builds)', () => {
    setup(false).debug('hidden');
    expect(console.debug).not.toHaveBeenCalled();

    TestBed.resetTestingModule();
    setup(true).debug('shown');
    expect(console.debug).toHaveBeenCalledWith('shown');
  });

  it('keeps info in the console only', () => {
    setup(true).info('Started');

    expect(console.info).toHaveBeenCalledWith('Started');
    expect(enqueue).not.toHaveBeenCalled();
  });

  it('sends warnings and errors to the backend', () => {
    const logger = setup(false);

    logger.warn('Slow page');
    logger.error('Save failed');
    logger.error('Save failed', new TypeError('x is undefined'));
    logger.error('Odd failure', { code: 42 });
    logger.error('Plain failure', 'timeout');

    expect(enqueue.mock.calls).toEqual([
      ['warn', 'Slow page'],
      ['error', 'Save failed'],
      ['error', 'Save failed: TypeError: x is undefined'],
      ['error', 'Odd failure: {"code":42}'],
      ['error', 'Plain failure: timeout'],
    ]);
  });

  it('the global error handler sends uncaught errors to the backend', () => {
    setup(false);

    TestBed.inject(ErrorHandler).handleError(new RangeError('Index out of range'));

    expect(enqueue).toHaveBeenCalledWith('error', 'Uncaught error: RangeError: Index out of range');
    expect(console.error).toHaveBeenCalled();
  });
});
