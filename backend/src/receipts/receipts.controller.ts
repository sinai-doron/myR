import {
  Body,
  Controller,
  Get,
  Headers,
  Param,
  Post,
  Res,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Response } from 'express';
import { ProcessReceiptDto } from './dto/process-receipt.dto';
import { ReceiptsService } from './receipts.service';
import { ProcessedReceipt } from './receipt-data';

@Controller('receipts')
export class ReceiptsController {
  constructor(
    private readonly receiptsService: ReceiptsService,
    private readonly configService: ConfigService,
  ) {}

  @Post('process')
  async process(
    @Body() dto: ProcessReceiptDto,
    @Headers('x-api-key') apiKey: string,
  ): Promise<ProcessedReceipt> {
    this.validateApiKey(apiKey);
    return this.receiptsService.process(dto.url, dto.clientId);
  }

  @Get(':clientId/pdf')
  async getPdf(
    @Param('clientId') clientId: string,
    @Headers('x-api-key') apiKey: string,
    @Res() res: Response,
  ) {
    this.validateApiKey(apiKey);
    const pdf = await this.receiptsService.getPdf(clientId);
    res.set({
      'Content-Type': 'application/pdf',
      'Content-Disposition': `inline; filename="${clientId}.pdf"`,
    });
    res.send(pdf);
  }

  private validateApiKey(apiKey: string) {
    const expected = this.configService.getOrThrow<string>('API_KEY');
    if (apiKey !== expected) {
      throw new UnauthorizedException();
    }
  }
}
