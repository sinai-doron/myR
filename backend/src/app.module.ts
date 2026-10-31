import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { ReceiptsModule } from './receipts/receipts.module';
import { RendererModule } from './renderer/renderer.module';
import { ExtractorModule } from './extractor/extractor.module';
import { StorageModule } from './storage/storage.module';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    ReceiptsModule,
    RendererModule,
    ExtractorModule,
    StorageModule,
  ],
})
export class AppModule {}
