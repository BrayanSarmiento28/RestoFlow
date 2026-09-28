import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PanelMarca } from './panel-marca';

describe('PanelMarca', () => {
  let component: PanelMarca;
  let fixture: ComponentFixture<PanelMarca>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [PanelMarca],
    }).compileComponents();

    fixture = TestBed.createComponent(PanelMarca);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
