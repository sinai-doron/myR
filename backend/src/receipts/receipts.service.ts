import { Injectable, Logger } from '@nestjs/common';
import { RendererService } from '../renderer/renderer.service';
import { ExtractorService } from '../extractor/extractor.service';
import { StorageService } from '../storage/storage.service';
import { ProcessedReceipt } from './receipt-data';

@Injectable()
export class ReceiptsService {
  private readonly logger = new Logger(ReceiptsService.name);

  constructor(
    private readonly renderer: RendererService,
    private readonly extractor: ExtractorService,
    private readonly storage: StorageService,
  ) {}

  async process(url: string, clientId: string): Promise<ProcessedReceipt> {
    this.logger.log(`Processing receipt: ${clientId} — ${url}`);

    const pdf = await this.renderer.render(url);

    const [snapshotUrl, extracted] = await Promise.all([
      this.storage.uploadPdf(clientId, pdf),
      this.extractor.extract(pdf),
    ]);

    this.logger.log(
      `Done: ${clientId} — ${extracted.merchant}, ${extracted.total} ${extracted.currency}`,
    );

    return { clientId, extracted, snapshotUrl };
  }

  async getPdf(clientId: string): Promise<Buffer> {
    return this.storage.getPdf(clientId);
  }
}
