import { fakeAsync, tick } from '@angular/core/testing';
import { downloadBlob } from './download-file';

describe('downloadBlob', () => {
  it('clicks a temporary download link with the file name and then releases the object URL', fakeAsync(() => {
    const blob = new Blob(['a,b'], { type: 'text/csv' });
    spyOn(URL, 'createObjectURL').and.returnValue('blob:test');
    const revoke = spyOn(URL, 'revokeObjectURL');
    const click = spyOn(HTMLAnchorElement.prototype, 'click');

    downloadBlob(blob, 'report.csv');

    const clicked = click.calls.mostRecent().object as HTMLAnchorElement;
    expect(URL.createObjectURL).toHaveBeenCalledWith(blob);
    expect(clicked.download).toBe('report.csv');
    expect(clicked.href).toBe('blob:test');
    expect(clicked.isConnected).toBeFalse();
    expect(revoke).not.toHaveBeenCalled();

    tick();

    expect(revoke).toHaveBeenCalledWith('blob:test');
  }));
});
