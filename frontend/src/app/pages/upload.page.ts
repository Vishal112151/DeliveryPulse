import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, ViewChild, inject, signal } from '@angular/core';
import {
  LucideCircleAlert as AlertCircle,
  LucideCheck as Check,
  LucideFileSpreadsheet as FileSpreadsheet,
  LucideFileText as FileText,
  LucideUpload as Upload,
  LucideX as X,
} from '@lucide/angular';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ImportSummary } from '../core/api.models';
import { DeliveryPulseApi } from '../core/deliverypulse-api.service';
import { LucideIconCompatComponent } from '../shared/lucide-icon-compat.component';
import { errorMessage } from '../shared/view-helpers';

@Component({
  standalone: true,
  imports: [CommonModule, LucideIconCompatComponent],
  template: `
    <section class="page-heading"><div><div class="eyebrow">DATA INTAKE</div><h1>Upload project data</h1><p class="heading-copy">Import a multi-project workbook or a single-project CSV into the portfolio.</p></div></section>
    <div class="upload-layout">
      <section class="surface upload-surface">
        <div class="surface-heading"><div><div class="eyebrow">FILE IMPORT</div><h2>Select a source file</h2></div><span class="limit-badge">MAX 10 MB</span></div>
        <div class="dropzone" [class.dragging]="dragging()" [class.has-file]="selectedFile()" (dragover)="onDragOver($event)" (dragleave)="dragging.set(false)" (drop)="onDrop($event)">
          <input #fileInput class="file-input" type="file" accept=".xlsx,.xls,.csv" (change)="onFileInput($event)" aria-label="Choose an Excel or CSV project file" />
          @if (selectedFile(); as file) {
            <div class="selected-file"><div class="file-icon"><lucide-icon [img]="file.name.toLowerCase().endsWith('.csv') ? CsvIcon : ExcelIcon" [size]="20" /></div><div class="selected-meta"><strong>{{ file.name }}</strong><span>{{ prettySize(file.size) }}</span></div><button class="icon-button" type="button" aria-label="Remove selected file" (click)="clearFile()"><lucide-icon [img]="CloseIcon" [size]="16" /></button></div>
          } @else {
            <div class="upload-symbol"><lucide-icon [img]="UploadIcon" [size]="21" /></div>
            <strong>Drop your workbook or CSV here</strong>
            <span>Excel workbooks can contain multiple project sheets</span>
            <button class="button button-secondary" type="button" (click)="fileInput.click()">Browse files</button>
            <small>.xlsx · .xls · .csv</small>
          }
        </div>
        @if (validationError()) { <div class="inline-error" role="alert"><lucide-icon [img]="AlertIcon" [size]="15" />{{ validationError() }}</div> }
        @if (uploadError()) { <div class="inline-error api-error" role="alert"><lucide-icon [img]="AlertIcon" [size]="15" />{{ uploadError() }}</div> }
        <div class="upload-actions"><span class="privacy-note">Files are validated before any records are committed.</span><button class="button button-primary" type="button" (click)="upload()" [disabled]="!selectedFile() || uploading()">
          @if (uploading()) { <span class="spinner"></span> Importing… } @else { <lucide-icon [img]="UploadIcon" [size]="15" /> Import file }
        </button></div>
      </section>

      @if (summary(); as result) {
        <section class="surface result-surface" aria-live="polite">
          <div class="result-heading"><div class="success-mark" [class.rejected]="result.errors.length > 0"><lucide-icon [img]="result.errors.length > 0 ? AlertIcon : CheckIcon" [size]="17" /></div><div><div class="eyebrow">{{ result.errors.length > 0 ? 'IMPORT REJECTED' : 'IMPORT COMPLETE' }}</div><h2>{{ result.errors.length > 0 ? 'Validation summary' : 'Import summary' }}</h2></div></div>
          <div class="result-metrics"><div><strong>{{ result.projectCount }}</strong><span>projects</span></div><div><strong>{{ result.importedTaskCount }}</strong><span>tasks</span></div><div><strong>{{ result.skippedWorksheets.length }}</strong><span>skipped sheets</span></div></div>
          @if (result.importedProjects.length) { <div class="result-section"><h3>Imported projects</h3><div class="chip-list">@for (project of result.importedProjects; track project) { <span class="name-chip">{{ project }}</span> }</div></div> }
          @if (result.skippedWorksheets.length) { <div class="result-section"><h3>Skipped worksheets</h3><div class="chip-list">@for (sheet of result.skippedWorksheets; track sheet) { <span class="muted-chip">{{ sheet }}</span> }</div></div> }
          @if (result.warnings.length) { <div class="issue-list warning-list"><h3>Warnings · {{ result.warnings.length }}</h3>@for (issue of result.warnings; track $index) { <p><strong>{{ issue.worksheet }}{{ issue.row ? ' · row ' + issue.row : '' }}</strong> {{ issue.message }}</p> }</div> }
          @if (result.errors.length) { <div class="issue-list error-list"><h3>Validation errors · {{ result.errors.length }}</h3>@for (issue of result.errors; track $index) { <p><strong>{{ issue.worksheet }}{{ issue.row ? ' · row ' + issue.row : '' }}{{ issue.column ? ' · ' + issue.column : '' }}</strong> {{ issue.message }}</p> }</div> }
        </section>
      }
    </div>
    <section class="format-strip"><div class="format-icon excel"><lucide-icon [img]="ExcelIcon" [size]="18" /></div><div><strong>Excel workbook</strong><span>Every relevant worksheet becomes a separate project. Instruction sheets are skipped.</span></div><div class="format-icon csv"><lucide-icon [img]="CsvIcon" [size]="18" /></div><div><strong>CSV file</strong><span>One project per file. The project name comes from the filename.</span></div></section>
  `,
  styles: [`
    :host { display:block; }.page-heading { margin-bottom:25px; }.eyebrow { color:#829087; font-size:9px; font-weight:800; letter-spacing:1.35px; }h1 { margin:7px 0 6px; color:#23332d; font-size:27px; font-weight:720; }.heading-copy { margin:0; color:#77827a; font-size:12px; }
    .upload-layout { display:grid; grid-template-columns:minmax(0,1fr) minmax(280px,.78fr); align-items:start; gap:15px; }.surface { border:1px solid #e5e9e3; border-radius:7px; background:#fff; }.upload-surface,.result-surface { padding:20px; }.surface-heading,.result-heading { display:flex; align-items:center; justify-content:space-between; gap:14px; }.surface-heading h2,.result-heading h2 { margin:5px 0 0; color:#293a32; font-size:15px; }.limit-badge { padding:5px 7px; border:1px solid #e4e9e2; border-radius:4px; color:#77847b; font-size:9px; font-weight:750; letter-spacing:.5px; }
    .dropzone { position:relative; display:flex; min-height:265px; align-items:center; justify-content:center; flex-direction:column; gap:10px; margin-top:20px; border:1px dashed #cbd5c9; border-radius:6px; background:#fbfcfa; transition:border 140ms ease,background 140ms ease; }.dropzone.dragging,.dropzone.has-file { border-color:#8eaa76; background:#f7faf3; }.file-input { position:absolute; width:1px; height:1px; opacity:0; pointer-events:none; }.upload-symbol,.file-icon { display:grid; width:42px; height:42px; place-items:center; border-radius:50%; color:#648350; background:#edf4e7; }.dropzone>strong { color:#34463b; font-size:13px; }.dropzone>span { color:#849087; font-size:10px; }.dropzone small { color:#a1aaa3; font-size:9px; }
    .button { display:inline-flex; min-height:36px; align-items:center; justify-content:center; gap:8px; padding:0 13px; border:1px solid transparent; border-radius:6px; font:inherit; font-size:11px; font-weight:700; text-decoration:none; cursor:pointer; }.button:disabled { opacity:.5; cursor:not-allowed; }.button-secondary { border-color:#dfe5de; color:#3c5146; background:#fff; }.button-primary { color:#1d3329; background:#c1ef74; }.icon-button { display:grid; width:32px; height:32px; place-items:center; border:1px solid #e2e7e1; border-radius:6px; color:#617067; background:#fff; cursor:pointer; }
    .selected-file { display:flex; width:min(440px,90%); align-items:center; gap:12px; padding:12px; border:1px solid #e5e9e3; border-radius:6px; background:#fff; }.file-icon { flex:none; width:36px; height:36px; border-radius:6px; }.selected-meta { display:grid; min-width:0; gap:4px; flex:1; }.selected-meta strong { overflow:hidden; color:#33443a; font-size:11px; text-overflow:ellipsis; white-space:nowrap; }.selected-meta span { color:#89938c; font-size:10px; }.upload-actions { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-top:16px; }.privacy-note { color:#89938c; font-size:9px; }.inline-error { display:flex; gap:7px; align-items:flex-start; margin-top:12px; color:#ad5142; font-size:10px; }.api-error { padding:10px; border-radius:5px; background:#fff4f0; }
    .success-mark { display:grid; width:34px; height:34px; place-items:center; border-radius:50%; color:#4b7944; background:#edf5e8; }.success-mark.rejected { color:#b34f40; background:#faeae6; }.result-heading { justify-content:flex-start; }.result-metrics { display:grid; grid-template-columns:repeat(3,1fr); margin:20px 0 16px; padding:13px 0; border-block:1px solid #edf0ec; }.result-metrics div { display:grid; gap:4px; }.result-metrics strong { color:#304137; font-size:22px; }.result-metrics span { color:#8a948c; font-size:9px; }.result-section { padding:10px 0; border-bottom:1px solid #edf0ec; }.result-section h3,.issue-list h3 { margin:0 0 9px; color:#53645a; font-size:10px; }.chip-list { display:flex; flex-wrap:wrap; gap:6px; }.name-chip,.muted-chip { padding:5px 7px; border-radius:4px; color:#52704a; background:#eff5e9; font-size:9px; }.muted-chip { color:#77827a; background:#f0f2ef; }.issue-list { margin-top:14px; }.issue-list p { margin:7px 0 0; color:#7b766b; font-size:10px; line-height:1.45; }.issue-list p strong { color:#7a6240; }.error-list p strong { color:#aa5545; }.warning-list h3 { color:#98713c; }.error-list h3 { color:#ad5142; }
    .format-strip { display:grid; grid-template-columns:38px minmax(0,1fr) 38px minmax(0,1fr); align-items:center; gap:12px; margin-top:15px; padding:14px 17px; border:1px solid #e5e9e3; border-radius:7px; background:#fff; }.format-icon { display:grid; width:34px; height:34px; place-items:center; border-radius:6px; }.format-icon.excel { color:#46754a; background:#eef4e9; }.format-icon.csv { color:#90733a; background:#f6f1df; }.format-strip strong,.format-strip span { display:block; }.format-strip strong { margin-bottom:4px; color:#405047; font-size:10px; }.format-strip span { color:#89938c; font-size:9px; line-height:1.45; }
    .spinner { width:13px; height:13px; border:2px solid #dce5d8; border-top-color:#6d8a58; border-radius:50%; animation:spin .7s linear infinite; }@keyframes spin { to { transform:rotate(360deg); } }
    @media(max-width:900px){.upload-layout{grid-template-columns:1fr;}}@media(max-width:600px){.upload-surface,.result-surface{padding:15px;}.dropzone{min-height:235px;}.upload-actions{align-items:flex-start;flex-direction:column;}.format-strip{grid-template-columns:34px minmax(0,1fr);}.format-icon.csv{grid-column:1;}.format-strip>div:nth-of-type(4){grid-column:2;}}
  `],
})
export class UploadPage {
  private readonly api = inject(DeliveryPulseApi);
  private readonly destroyRef = inject(DestroyRef);
  @ViewChild('fileInput') private fileInput?: ElementRef<HTMLInputElement>;
  readonly selectedFile = signal<File | null>(null);
  readonly dragging = signal(false);
  readonly uploading = signal(false);
  readonly validationError = signal('');
  readonly uploadError = signal('');
  readonly summary = signal<ImportSummary | null>(null);
  protected readonly UploadIcon = Upload;
  protected readonly ExcelIcon = FileSpreadsheet;
  protected readonly CsvIcon = FileText;
  protected readonly AlertIcon = AlertCircle;
  protected readonly CheckIcon = Check;
  protected readonly CloseIcon = X;

  onDragOver(event: DragEvent): void { event.preventDefault(); this.dragging.set(true); }
  onDrop(event: DragEvent): void {
    event.preventDefault(); this.dragging.set(false);
    const file = event.dataTransfer?.files.item(0);
    if (file) this.selectFile(file);
  }
  onFileInput(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.item(0);
    if (file) this.selectFile(file);
  }
  clearFile(): void { this.selectedFile.set(null); this.summary.set(null); if (this.fileInput) this.fileInput.nativeElement.value = ''; }
  prettySize(bytes: number): string { return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`; }

  upload(): void {
    const file = this.selectedFile();
    if (!file || this.uploading()) return;
    this.uploading.set(true); this.uploadError.set(''); this.summary.set(null);
    this.api.upload(file).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (result) => { this.summary.set(result); this.uploading.set(false); },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && (error.status === 422 || error.status === 409)
            && this.isImportSummary(error.error)) {
          this.summary.set(error.error);
          this.uploadError.set(error.status === 409
            ? 'No records were imported because one or more project names already exist.'
            : 'No records were imported. Correct the listed validation issues and upload again.');
        } else {
          this.uploadError.set(errorMessage(error));
        }
        this.uploading.set(false);
      },
    });
  }

  private selectFile(file: File): void {
    this.validationError.set(''); this.uploadError.set(''); this.summary.set(null);
    const extensionAllowed = /\.(xlsx|xls|csv)$/i.test(file.name);
    if (!extensionAllowed) { this.selectedFile.set(null); this.validationError.set('Choose an .xlsx, .xls, or .csv file.'); return; }
    if (file.size > 10 * 1024 * 1024) { this.selectedFile.set(null); this.validationError.set('File exceeds the 10 MB upload limit.'); return; }
    if (!file.size) { this.selectedFile.set(null); this.validationError.set('The selected file is empty.'); return; }
    this.selectedFile.set(file);
  }

  private isImportSummary(value: unknown): value is ImportSummary {
    if (!value || typeof value !== 'object') return false;
    const summary = value as Partial<ImportSummary>;
    return Array.isArray(summary.errors) && Array.isArray(summary.warnings)
      && Array.isArray(summary.importedProjects) && Array.isArray(summary.skippedWorksheets)
      && typeof summary.projectCount === 'number' && typeof summary.importedTaskCount === 'number';
  }
}