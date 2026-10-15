import { csvCell } from './report-csv';

describe('csvCell', () => {
  it.each(['=1+1', '+SUM(A1:A2)', '-2+3', '@SUM(A1)', '  =1+1'])('neutralizes spreadsheet formula input %s', (value) => {
    expect(csvCell(value)).toBe(`"'${value}"`);
  });

  it('quotes and escapes ordinary text without modifying it', () => {
    expect(csvCell('Team "North"')).toBe('"Team ""North"""');
  });
});