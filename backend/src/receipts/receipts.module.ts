import { Module } from '@nestjs/common';
import { ReceiptsController } from './receipts.controller';
import { ReceiptsService } from './receipts.service';
import { RendererModule } from '../renderer/renderer.module';
import { ExtractorModule } from '../extractor/extractor.module';
import { StorageModule } from '../storage/storage.module';

@Module({
  imports: [RendererModule, ExtractorModule, StorageModule],
  controllers: [ReceiptsController],
  providers: [ReceiptsService],
})
export class ReceiptsModule {}
