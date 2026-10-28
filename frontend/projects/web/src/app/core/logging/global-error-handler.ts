import { ErrorHandler, Injectable, inject } from '@angular/core';
import { LoggerService } from './logger';

/** Every uncaught error in the app reaches the backend logs (with the last trace id). */
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  private readonly logger = inject(LoggerService);

  handleError(error: unknown): void {
    this.logger.error('Uncaught error', error);
  }
}
