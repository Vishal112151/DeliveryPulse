import { AfterViewInit, Component, ElementRef, OnChanges, OnDestroy, SimpleChanges, ViewChild, input } from '@angular/core';
import { Chart, ChartConfiguration, registerables } from 'chart.js';
import { RiskSeverity } from '../core/api.models';

Chart.register(...registerables);

@Component({
  selector: 'app-severity-chart',
  template: '<canvas #canvas aria-label="Risk distribution by severity" role="img"></canvas>',
  styles: [':host { display: block; width: 100%; height: 206px; } canvas { max-width: 100%; }'],
})
export class SeverityChartComponent implements AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('canvas') private canvas!: ElementRef<HTMLCanvasElement>;
  readonly values = input.required<Record<RiskSeverity, number>>();
  private chart?: Chart;
  private initialized = false;

  ngAfterViewInit(): void {
    this.initialized = true;
    this.render();
  }

  ngOnChanges(_changes: SimpleChanges): void {
    this.render();
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  private render(): void {
    if (!this.initialized) return;
    this.chart?.destroy();
    const values = this.values();
    const data = [values.CRITICAL ?? 0, values.HIGH ?? 0, values.MEDIUM ?? 0, values.LOW ?? 0];
    const config: ChartConfiguration<'doughnut'> = {
      type: 'doughnut',
      data: {
        labels: ['Critical', 'High', 'Medium', 'Low'],
        datasets: [{ data: data.some((value) => value > 0) ? data : [1, 0, 0, 0],
          backgroundColor: data.some((value) => value > 0) ? ['#d95a48', '#e99b45', '#e6c75b', '#a9c88c'] : ['#e8ece7'],
          borderColor: '#fff', borderWidth: 4, hoverOffset: 3 }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '72%',
        plugins: { legend: { display: false }, tooltip: { enabled: data.some((value) => value > 0) } },
      },
    };
    this.chart = new Chart(this.canvas.nativeElement, config);
  }
}