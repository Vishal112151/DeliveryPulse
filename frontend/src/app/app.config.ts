import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import {
  LucideActivity as Activity,
  LucideCircleAlert as AlertCircle,
  LucideArrowLeft as ArrowLeft,
  LucideArrowUpDown as ArrowUpDown,
  LucideArrowUpRight as ArrowUpRight,
  LucideCheck as Check,
  LucideClock3 as Clock3,
  LucideDownload as Download,
  LucideFileSpreadsheet as FileSpreadsheet,
  LucideFileText as FileText,
  LucideFolderKanban as FolderKanban,
  LucideLayoutDashboard as LayoutDashboard,
  LucideMenu as Menu,
  LucidePrinter as Printer,
  LucideRefreshCw as RefreshCw,
  LucideSearch as Search,
  LucideTriangleAlert as TriangleAlert,
  LucideUpload as Upload,
  LucideX as X,
  provideLucideIcons,
} from '@lucide/angular';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideLucideIcons(Activity, AlertCircle, ArrowLeft, ArrowUpDown, ArrowUpRight,
      Check, Clock3, Download, FileSpreadsheet, FileText, FolderKanban, LayoutDashboard,
      Menu, Printer, RefreshCw, Search, TriangleAlert, Upload, X),
  ]
};
