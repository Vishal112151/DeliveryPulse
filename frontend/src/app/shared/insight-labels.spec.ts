import { insightStatusLabel, isDegraded, sourceLabel } from './insight-labels';

describe('insight labels', () => {
  it('labels AI output as a draft and the fallback as deterministic', () => {
    expect(sourceLabel({ source: 'AI' })).toBe('AI-generated draft');
    expect(sourceLabel({ source: 'DETERMINISTIC_FALLBACK' })).toBe('Deterministic summary');
  });

  it('separates configuration states from provider failures', () => {
    expect(isDegraded('DISABLED')).toBe(false);
    expect(isDegraded('NOT_AUTHORIZED')).toBe(false);
    expect(isDegraded('RATE_LIMITED')).toBe(true);
    expect(isDegraded('INVALID_RESPONSE')).toBe(true);
    expect(insightStatusLabel('QUOTA_EXCEEDED')).toBe('AI provider quota exhausted');
  });
});
