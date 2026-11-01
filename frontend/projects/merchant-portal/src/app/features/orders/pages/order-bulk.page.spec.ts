import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { BulkResult } from '../../../core/models/order.model';
import { buttonByText, element, query } from '../../../core/testing/dom';
import { OrderBulkPage } from './order-bulk.page';

const BULK_URL = '/api/v1/merchants/me/orders/bulk';

const MIXED_RESULT: BulkResult = {
  totalRows: 3,
  created: 1,
  failed: 2,
  results: [
    { row: 2, status: 'CREATED', trackingNo: 'SD24000123', orderId: 'order-2' },
    { row: 3, status: 'FAILED', errors: ['dropoffCity: must not be blank'] },
    { row: 4, status: 'FAILED', errors: ['=HYPERLINK("http://evil.example")'] },
  ],
};

function csvFile(name = 'orders.csv', content = 'externalRef,recipientName\r\n'): File {
  return new File([content], name, { type: 'text/csv' });
}

describe('OrderBulkPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<OrderBulkPage>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OrderBulkPage);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const root = () => element(fixture);
  const text = () => (root().textContent ?? '').replace(/\s+/g, ' ');
  const fileInput = () => query<HTMLInputElement>(fixture, 'input[type="file"]');

  function transfer(file: File): DataTransfer {
    const transfer = new DataTransfer();
    transfer.items.add(file);
    return transfer;
  }

  function pick(file: File): void {
    fileInput().files = transfer(file).files;
    fileInput().dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function uploadButton(): HTMLButtonElement {
    return buttonByText(fixture, 'Upload and create orders');
  }

  function uploadWithResponse(result: BulkResult): void {
    pick(csvFile());
    uploadButton().click();
    http.expectOne(BULK_URL).flush(result);
    fixture.detectChanges();
  }

  /** Captures what the page offers for download, without triggering a real download. */
  function captureDownload(): { blob: () => Blob; name: () => string } {
    let blob: Blob | undefined;
    let name = '';
    spyOn(URL, 'createObjectURL').and.callFake((object) => {
      blob = object as Blob;
      return 'blob:test';
    });
    spyOn(URL, 'revokeObjectURL');
    spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(function (this: HTMLAnchorElement) {
      name = this.download;
    });
    return { blob: () => blob as Blob, name: () => name };
  }

  describe('template', () => {
    it('downloads the CSV template through a temporary link', () => {
      const download = captureDownload();

      buttonByText(fixture, 'Download template').click();
      const request = http.expectOne(`${BULK_URL}/template`);
      expect(request.request.responseType).toBe('blob');
      request.flush(new Blob(['externalRef,recipientName'], { type: 'text/csv' }));

      expect(download.name()).toBe('swiftdrop-bulk-orders-template.csv');
      expect(download.blob().size).toBeGreaterThan(0);
    });

    it('shows an error when the template cannot be loaded', () => {
      buttonByText(fixture, 'Download template').click();
      http
        .expectOne(`${BULK_URL}/template`)
        .flush(null, { status: 503, statusText: 'Unavailable' });
      fixture.detectChanges();

      expect(root().querySelector('[role="alert"]')?.textContent).toContain(
        'temporarily unavailable',
      );
      expect(buttonByText(fixture, 'Download template').disabled).toBeFalse();
    });
  });

  describe('choosing a file', () => {
    it('accepts only CSV files in the picker and is reachable by keyboard', () => {
      expect(fileInput().accept).toBe('.csv,text/csv');
      expect(fileInput().tabIndex).toBe(0);
      expect(query<HTMLLabelElement>(fixture, 'label.dropzone').htmlFor).toBe(fileInput().id);
      expect(uploadButton().disabled).toBeTrue();
    });

    it('shows the picked file and enables the upload', () => {
      pick(csvFile('orders.csv', 'a'.repeat(2048)));

      expect(text()).toContain('Selected file: orders.csv (2.0 KiB)');
      expect(uploadButton().disabled).toBeFalse();
    });

    it('accepts a file dropped on the drop area and highlights the area while dragging over it', () => {
      const dropzone = query<HTMLElement>(fixture, 'label.dropzone');

      dropzone.dispatchEvent(new DragEvent('dragover', { cancelable: true }));
      fixture.detectChanges();
      expect(dropzone.classList).toContain('dropzone--active');

      dropzone.dispatchEvent(
        new DragEvent('drop', { cancelable: true, dataTransfer: transfer(csvFile('dropped.csv')) }),
      );
      fixture.detectChanges();

      expect(dropzone.classList).not.toContain('dropzone--active');
      expect(text()).toContain('Selected file: dropped.csv');
    });

    it('stops highlighting when the drag leaves', () => {
      const dropzone = query<HTMLElement>(fixture, 'label.dropzone');
      dropzone.dispatchEvent(new DragEvent('dragover', { cancelable: true }));
      dropzone.dispatchEvent(new DragEvent('dragleave'));
      fixture.detectChanges();

      expect(dropzone.classList).not.toContain('dropzone--active');
    });

    it('rejects a file that is not a CSV', () => {
      pick(csvFile('orders.xlsx'));

      expect(root().querySelector('[role="alert"]')?.textContent).toContain('Choose a .csv file');
      expect(uploadButton().disabled).toBeTrue();
    });

    it('rejects a file over 1 MiB without contacting the server', () => {
      pick(csvFile('big.csv', 'x'.repeat(1024 * 1024 + 1)));

      expect(root().querySelector('[role="alert"]')?.textContent).toContain('over the 1 MiB limit');
      expect(uploadButton().disabled).toBeTrue();
      http.expectNone(BULK_URL);
    });

    it('removes the chosen file', () => {
      pick(csvFile());

      buttonByText(fixture, 'Remove').click();
      fixture.detectChanges();

      expect(text()).not.toContain('Selected file');
      expect(uploadButton().disabled).toBeTrue();
    });
  });

  describe('uploading', () => {
    it('sends the file as multipart form data and shows a busy state until the server answers', () => {
      pick(csvFile('orders.csv'));

      uploadButton().click();
      fixture.detectChanges();

      const request = http.expectOne(BULK_URL);
      expect(request.request.method).toBe('POST');
      expect((request.request.body as FormData).get('file')).toEqual(jasmine.any(File));
      expect(((request.request.body as FormData).get('file') as File).name).toBe('orders.csv');
      expect(buttonByText(fixture, 'Uploading...').disabled).toBeTrue();
      expect(root().querySelector('app-spinner')?.textContent).toContain('Creating your orders');
      expect(fileInput().disabled).toBeTrue();

      request.flush(MIXED_RESULT);
    });

    it('does not start a second upload while one is running', () => {
      pick(csvFile());
      uploadButton().click();
      uploadButton().click();

      const requests = http.match(BULK_URL);
      expect(requests.length).toBe(1);
      requests[0].flush(MIXED_RESULT);
    });

    it('summarises how many rows were created and failed', () => {
      uploadWithResponse(MIXED_RESULT);

      const counts = Array.from(root().querySelectorAll('.counts li')).map((item) =>
        Array.from(item.children).map((part) => part.textContent),
      );
      expect(counts).toEqual([
        ['3', 'Rows'],
        ['1', 'Created'],
        ['2', 'Failed'],
      ]);
      expect(root().querySelector('[role="status"]')?.textContent).toContain(
        '2 of 3 rows could not be created',
      );
    });

    it('lists every row in a table with its tracking number or errors', () => {
      uploadWithResponse(MIXED_RESULT);

      const rows = Array.from(root().querySelectorAll('app-bulk-result-table tbody tr'));
      expect(rows.length).toBe(3);
      expect(rows[0].textContent).toContain('SD24000123');
      expect(rows[1].textContent).toContain('dropoffCity: must not be blank');
    });

    it('celebrates a fully successful upload and offers no failure report', () => {
      uploadWithResponse({
        totalRows: 1,
        created: 1,
        failed: 0,
        results: [MIXED_RESULT.results[0]],
      });

      expect(text()).toContain('All 1 orders were created.');
      expect(() => buttonByText(fixture, 'Download failed rows')).toThrow();
    });

    it('downloads a report of the failed rows with formula-looking text neutralised', async () => {
      uploadWithResponse(MIXED_RESULT);
      const download = captureDownload();

      buttonByText(fixture, 'Download failed rows').click();

      expect(download.name()).toBe('swiftdrop-bulk-failed-rows.csv');
      expect(download.blob().type).toBe('text/csv;charset=utf-8');
      expect(await download.blob().text()).toBe(
        'row,errors\r\n3,dropoffCity: must not be blank\r\n4,"\'=HYPERLINK(""http://evil.example"")"\r\n',
      );
    });

    it('starts over with another file', () => {
      uploadWithResponse(MIXED_RESULT);

      buttonByText(fixture, 'Upload another file').click();
      fixture.detectChanges();

      expect(root().querySelector('app-bulk-result-table')).toBeNull();
      expect(text()).toContain('2. Upload your file');
      expect(uploadButton().disabled).toBeTrue();
    });
  });

  describe('whole-file rejections', () => {
    function rejectWith(status: number, code: string, message: string): void {
      pick(csvFile());
      uploadButton().click();
      http.expectOne(BULK_URL).flush({ code, message }, { status, statusText: 'Error' });
      fixture.detectChanges();
    }

    it('explains a 400 (for example a wrong header) and points to the template', () => {
      rejectWith(400, 'VALIDATION_FAILED', 'The CSV header does not match the template.');

      expect(root().querySelector('[role="alert"]')?.textContent).toContain(
        'The CSV header does not match the template.',
      );
      expect(text()).toContain('check that your column names and their order match it');
      expect(root().querySelector('app-bulk-result-table')).toBeNull();
    });

    it('explains a 413 and states the limits', () => {
      rejectWith(413, 'PAYLOAD_TOO_LARGE', 'The file is too large.');

      expect(root().querySelector('[role="alert"]')?.textContent).toContain(
        'The file is too large.',
      );
      expect(text()).toContain('up to 500 rows and be 1 MiB in size');
    });

    it('keeps the file selected so it can be fixed and sent again', () => {
      rejectWith(400, 'VALIDATION_FAILED', 'Bad file');

      expect(text()).toContain('Selected file: orders.csv');
      expect(uploadButton().disabled).toBeFalse();

      uploadButton().click();
      http.expectOne(BULK_URL).flush(MIXED_RESULT);
      fixture.detectChanges();
      expect(root().querySelector('[role="alert"]')).toBeNull();
    });
  });
});
