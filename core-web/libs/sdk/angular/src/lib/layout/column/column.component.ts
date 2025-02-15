import { ChangeDetectionStrategy, Component, HostBinding, Input, OnInit } from '@angular/core';

import { DotPageAssetLayoutColumn } from '../../models';
import { getPositionStyleClasses } from '../../utils';
import { ContainerComponent } from '../container/container.component';

@Component({
    selector: 'dotcms-column',
    standalone: true,
    imports: [ContainerComponent],
    template: `
        @for (container of column.containers; track $index) {
            <dotcms-container [container]="container" />
        }
    `,
    styleUrl: './column.component.css',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ColumnComponent implements OnInit {
    @Input() column!: DotPageAssetLayoutColumn;
    @HostBinding('class') containerClasses = '';

    ngOnInit() {
        const { startClass, endClass } = getPositionStyleClasses(
            this.column.leftOffset,
            this.column.width + this.column.leftOffset
        );
        this.containerClasses = `${startClass} ${endClass}`;
    }
}
