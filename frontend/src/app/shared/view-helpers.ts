import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetail } from '../core/api.models';

export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as ProblemDetail | null;
    if (body?.detail) return body.detail;
    if (error.status === 0) return 'Cannot reach the DeliveryPulse API. Check that the backend is running.';
    return `The API request failed (${error.status}).`;
  }
  return 'Something went wrong while loading this view.';
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return 'Not set';
  const date = new Date(`${value.slice(0, 10)}T12:00:00`);
  return new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', year: 'numeric' }).format(date);
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return 'Not analyzed';
  return new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' })
    .format(new Date(value));
}

export function titleCase(value: string): string {
  return value.toLowerCase().split('_').map((word) => word.charAt(0).toUpperCase() + word.slice(1)).join(' ');
}