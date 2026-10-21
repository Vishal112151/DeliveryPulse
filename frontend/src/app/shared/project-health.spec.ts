import { healthCounts, projectHealth } from './project-health';

describe('projectHealth', () => {
  it('is Off track with any critical risk', () => {
    expect(projectHealth({ CRITICAL: 2, HIGH: 5 }, true)).toEqual({ level: 'OFF_TRACK', label: 'Off track', reason: '2 critical risks open' });
  });

  it('is At risk with high but no critical risks', () => {
    expect(projectHealth({ HIGH: 1, MEDIUM: 3 }, true).level).toBe('AT_RISK');
  });

  it('is On track with only medium and low risks', () => {
    expect(projectHealth({ MEDIUM: 4, LOW: 1 }, true).level).toBe('ON_TRACK');
  });

  it('is Not analyzed before the first analysis, whatever the counts', () => {
    expect(projectHealth({ CRITICAL: 1 }, false).level).toBe('NOT_ANALYZED');
  });

  it('counts projects by level', () => {
    expect(healthCounts([
      { risksBySeverity: { CRITICAL: 1 }, analysisTimestamp: '2026-10-21T00:00:00Z' },
      { risksBySeverity: {}, analysisTimestamp: '2026-10-21T00:00:00Z' },
      { risksBySeverity: {}, analysisTimestamp: null },
    ])).toEqual({ OFF_TRACK: 1, AT_RISK: 0, ON_TRACK: 1, NOT_ANALYZED: 1 });
  });
});
