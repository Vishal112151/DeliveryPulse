import { Component, input } from '@angular/core';
import { LucideDynamicIcon, LucideIconInput } from '@lucide/angular';

@Component({
  selector: 'lucide-icon',
  standalone: true,
  imports: [LucideDynamicIcon],
  template: '<svg [lucideIcon]="img()" [style.width.px]="size()" [style.height.px]="size()"></svg>',
  styles: [':host { display: inline-flex; flex: none; line-height: 0; }'],
})
export class LucideIconCompatComponent {
  readonly img = input.required<LucideIconInput>();
  readonly size = input<number>(18);
}