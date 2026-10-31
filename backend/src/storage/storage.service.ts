import { Injectable, Logger, NotFoundException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as fs from 'fs/promises';
import * as path from 'path';

@Injectable()
export class StorageService {
  private readonly logger = new Logger(StorageService.name);
  private readonly localDir: string;

  constructor(private readonly configService: ConfigService) {
    this.localDir = this.configService.get('STORAGE_PATH') || './receipts-data';
  }

  async uploadPdf(clientId: string, pdf: Buffer): Promise<string> {
    await fs.mkdir(this.localDir, { recursive: true });
    const filePath = path.join(this.localDir, `${clientId}.pdf`);
    await fs.writeFile(filePath, pdf);
    const abs = path.resolve(filePath);
    this.logger.log(`Saved: ${abs} (${pdf.length} bytes)`);
    return abs;
  }

  async getPdf(clientId: string): Promise<Buffer> {
    const filePath = path.join(this.localDir, `${clientId}.pdf`);
    try {
      return await fs.readFile(filePath);
    } catch {
      throw new NotFoundException(`PDF not found for ${clientId}`);
    }
  }
}
