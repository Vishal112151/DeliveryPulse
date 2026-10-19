import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { signInAs } from './core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';
import { provideLucideIcons, LucideActivity, LucideArrowLeft, LucideArrowUpDown, LucideArrowUpRight,
  LucideCheck, LucideCircleAlert, LucideClock3, LucideDownload, LucideFileSpreadsheet, LucideFileText,
  LucideFolderKanban, LucideLayoutDashboard, LucideMenu, LucidePrinter, LucideRefreshCw, LucideSearch,
  LucideTriangleAlert, LucideUpload, LucideX } from '@lucide/angular';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting(), provideLucideIcons(LucideActivity, LucideArrowLeft, LucideArrowUpDown,
        LucideArrowUpRight, LucideCheck, LucideCircleAlert, LucideClock3, LucideDownload,
        LucideFileSpreadsheet, LucideFileText, LucideFolderKanban, LucideLayoutDashboard, LucideMenu,
        LucidePrinter, LucideRefreshCw, LucideSearch, LucideTriangleAlert, LucideUpload, LucideX)],
    })
      .compileComponents();
  });

  it('should create the app', () => {
    signInAs('ADMIN');
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('renders the DeliveryPulse workspace navigation', async () => {
    signInAs('ADMIN');
    const fixture = TestBed.createComponent(App);
    TestBed.inject(HttpTestingController).expectOne('/actuator/health').flush({ status: 'UP' });
    fixture.detectChanges();
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.brand-name')?.textContent).toContain('deliverypulse');
    expect(compiled.querySelectorAll('.nav-link').length).toBe(6);
  });

  it('shows the API as offline when the health check fails', () => {
    signInAs('ADMIN');
    const fixture = TestBed.createComponent(App);
    TestBed.inject(HttpTestingController).expectOne('/actuator/health')
      .flush(null, { status: 0, statusText: 'Unknown Error' });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.service-state')?.textContent).toBe('offline');
  });

  it('shows the API as online when the health check reports UP', () => {
    signInAs('ADMIN');
    const fixture = TestBed.createComponent(App);
    TestBed.inject(HttpTestingController).expectOne('/actuator/health').flush({ status: 'UP' });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.service-state')?.textContent).toBe('online');
  });
});
