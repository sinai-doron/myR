import { Injectable, Logger, OnModuleDestroy } from '@nestjs/common';
import puppeteer, { Browser } from 'puppeteer';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

@Injectable()
export class RendererService implements OnModuleDestroy {
  private readonly logger = new Logger(RendererService.name);
  private browser: Browser | null = null;

  private async getBrowser(): Promise<Browser> {
    if (!this.browser || !this.browser.connected) {
      const executablePath =
        process.env.PUPPETEER_EXECUTABLE_PATH || undefined;
      this.browser = await puppeteer.launch({
        headless: true,
        executablePath,
        args: [
          '--no-sandbox',
          '--disable-setuid-sandbox',
          '--disable-dev-shm-usage',
          '--disable-gpu',
        ],
      });
    }
    return this.browser;
  }

  async render(url: string): Promise<Buffer> {
    const browser = await this.getBrowser();
    const page = await browser.newPage();
    const downloadPath = fs.mkdtempSync(path.join(os.tmpdir(), 'receipt-'));

    try {
      this.logger.log(`Navigating: ${url}`);
      await page.setViewport({ width: 1080, height: 1920 });

      const client = await page.createCDPSession();
      await client.send('Browser.setDownloadBehavior', {
        behavior: 'allowAndName',
        downloadPath,
        eventsEnabled: true,
      });

      await page.goto(url, { waitUntil: 'networkidle2', timeout: 30_000 });

      // Wait for JS redirects to settle
      let settled = false;
      for (let i = 0; i < 3 && !settled; i++) {
        try {
          await page.waitForNavigation({
            waitUntil: 'networkidle2',
            timeout: 5_000,
          });
          this.logger.log(`Redirected to: ${page.url()}`);
        } catch {
          settled = true;
        }
      }

      await new Promise((r) => setTimeout(r, 2000));

      // Try to find and click a download button
      const downloadButton = await page.$(
        'button.iconDownlond, [class*="download"], a[href*="download"], a[download]',
      );

      if (downloadButton) {
        this.logger.log('Found download button, clicking...');

        const downloadDone = new Promise<string>((resolve, reject) => {
          const timeout = setTimeout(
            () => reject(new Error('Download timeout')),
            30_000,
          );
          client.on('Browser.downloadProgress', (event: any) => {
            if (event.state === 'completed') {
              clearTimeout(timeout);
              resolve(event.guid);
            } else if (event.state === 'canceled') {
              clearTimeout(timeout);
              reject(new Error('Download canceled'));
            }
          });
        });

        await downloadButton.click();
        const guid = await downloadDone;
        const filePath = path.join(downloadPath, guid);
        const pdf = fs.readFileSync(filePath);
        this.logger.log(`Downloaded vendor PDF: ${pdf.length} bytes`);
        fs.rmSync(downloadPath, { recursive: true, force: true });
        return pdf;
      }

      // Fallback: generate PDF from page
      this.logger.log('No download button found, generating PDF from page');
      const pdf = Buffer.from(
        await page.pdf({ format: 'A4', printBackground: true }),
      );
      this.logger.log(`Generated PDF: ${pdf.length} bytes`);
      fs.rmSync(downloadPath, { recursive: true, force: true });
      return pdf;
    } finally {
      await page.close();
    }
  }

  async onModuleDestroy() {
    if (this.browser) {
      await this.browser.close();
    }
  }
}
