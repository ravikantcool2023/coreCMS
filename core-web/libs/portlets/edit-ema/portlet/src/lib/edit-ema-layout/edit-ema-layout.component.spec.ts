import { expect, describe } from '@jest/globals';
import { SpyObject } from '@ngneat/spectator';
import { Spectator, createComponentFactory, mockProvider } from '@ngneat/spectator/jest';
import { MockModule } from 'ng-mocks';
import { of } from 'rxjs';

import { HttpClientTestingModule } from '@angular/common/http/testing';
import { fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';

import { MessageService } from 'primeng/api';

import {
    DotContentTypeService,
    DotContentletLockerService,
    DotExperimentsService,
    DotLanguagesService,
    DotLicenseService,
    DotMessageService,
    DotPageLayoutService,
    DotRouterService
} from '@dotcms/data-access';
import { CoreWebService, LoginService } from '@dotcms/dotcms-js';
import { TemplateBuilderComponent, TemplateBuilderModule } from '@dotcms/template-builder';
import {
    DotExperimentsServiceMock,
    DotLanguagesServiceMock,
    MockDotRouterJestService
} from '@dotcms/utils-testing';

import { EditEmaLayoutComponent } from './edit-ema-layout.component';

import { DotActionUrlService } from '../services/dot-action-url/dot-action-url.service';
import { DotPageApiService } from '../services/dot-page-api.service';
import { UVEStore } from '../store/dot-uve.store';

describe('EditEmaLayoutComponent', () => {
    let spectator: Spectator<EditEmaLayoutComponent>;
    let component: EditEmaLayoutComponent;
    let dotRouter: SpyObject<DotRouterService>;
    let store: SpyObject<InstanceType<typeof UVEStore>>;
    let templateBuilder: TemplateBuilderComponent;
    let layoutService: DotPageLayoutService;
    let messageService: MessageService;
    let addMock: jest.SpyInstance;

    globalThis.structuredClone = jest.fn().mockImplementation((obj) => obj);

    const createComponent = createComponentFactory({
        component: EditEmaLayoutComponent,
        imports: [HttpClientTestingModule, MockModule(TemplateBuilderModule)],
        providers: [
            UVEStore,
            MessageService,
            DotMessageService,
            DotActionUrlService,
            mockProvider(Router),
            mockProvider(ActivatedRoute),
            {
                provide: DotExperimentsService,
                useValue: DotExperimentsServiceMock
            },
            { provide: DotRouterService, useValue: new MockDotRouterJestService(jest) },
            {
                provide: DotLicenseService,
                useValue: {
                    isEnterprise: () => of(true)
                }
            },
            {
                provide: DotPageApiService,
                useValue: {
                    get: () => {
                        return of({
                            containers: {},
                            page: {
                                identifier: 'test'
                            },
                            template: {
                                theme: 'testTheme'
                            },
                            layout: {
                                body: {
                                    rows: [
                                        {
                                            columns: [
                                                {
                                                    containers: [
                                                        {
                                                            identifier: 'test'
                                                        }
                                                    ]
                                                }
                                            ]
                                        }
                                    ]
                                }
                            }
                        });
                    }
                }
            },
            {
                provide: DotPageLayoutService,
                useValue: {
                    save: () => {
                        return of({
                            layout: {}
                        });
                    }
                }
            },
            mockProvider(DotContentTypeService),
            mockProvider(CoreWebService),
            {
                provide: DotLanguagesService,
                useValue: new DotLanguagesServiceMock()
            },
            {
                provide: DotContentletLockerService,
                useValue: {
                    unlock: (_inode: string) => of({})
                }
            },
            {
                provide: LoginService,
                useValue: {
                    getCurrentUser: () => of({})
                }
            }
        ]
    });

    beforeEach(async () => {
        spectator = createComponent();
        component = spectator.component;
        dotRouter = spectator.inject(DotRouterService);
        store = spectator.inject(UVEStore);
        layoutService = spectator.inject(DotPageLayoutService);
        messageService = spectator.inject(MessageService);

        addMock = jest.spyOn(messageService, 'add');

        store.load({
            clientHost: 'http://localhost:3000',
            language_id: '1',
            url: 'test',
            'com.dotmarketing.persona.id': 'SuperCoolDude'
        });

        spectator.detectChanges();

        templateBuilder = spectator.debugElement.query(
            By.css('[data-testId="edit-ema-layout"]')
        ).componentInstance;
    });

    describe('Template Change', () => {
        it('should forbid navigation', () => {
            const spy = jest.spyOn(dotRouter, 'forbidRouteDeactivation');

            templateBuilder.templateChange.emit();

            expect(spy).toHaveBeenCalled();
        });

        it('should trigger a save after 5 secs', fakeAsync(() => {
            const layoutServiceSave = jest.spyOn(layoutService, 'save');

            templateBuilder.templateChange.emit();
            tick(5000);

            expect(layoutServiceSave).toHaveBeenCalled();

            expect(addMock).toHaveBeenNthCalledWith(1, {
                severity: 'info',
                summary: 'Info',
                detail: 'dot.common.message.saving',
                life: 1000
            });

            expect(addMock).toHaveBeenNthCalledWith(2, {
                severity: 'success',
                summary: 'Success',
                detail: 'dot.common.message.saved'
            });
        }));

        it('should unlock navigation after saving', fakeAsync(() => {
            const allowRouting = jest.spyOn(dotRouter, 'allowRouteDeactivation');

            templateBuilder.templateChange.emit();
            tick(6000);

            expect(allowRouting).toHaveBeenCalled();
        }));

        it('should save right away if we request page leave before the 5 secs', () => {
            const saveTemplate = jest.spyOn(component, 'saveTemplate');

            templateBuilder.templateChange.emit();

            dotRouter.requestPageLeave(); // This is what the guard triggers if the page is forbid to navigate

            expect(saveTemplate).toHaveBeenCalled();

            expect(addMock).toHaveBeenNthCalledWith(1, {
                severity: 'info',
                summary: 'Info',
                detail: 'dot.common.message.saving',
                life: 1000
            });

            expect(addMock).toHaveBeenNthCalledWith(2, {
                severity: 'success',
                summary: 'Success',
                detail: 'dot.common.message.saved'
            });
        });
    });
});
