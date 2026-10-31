import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
  VertexAI,
  GenerativeModel,
  FunctionDeclarationSchemaType,
} from '@google-cloud/vertexai';
import { ExtractedReceipt } from '../receipts/receipt-data';

const EXTRACTION_PROMPT = `You are a receipt data extractor. Given a PDF of a receipt, extract the structured data.

Rules:
- Extract ALL line items with name, quantity, unit price, and total price
- Prices should be numeric values (not strings)
- Currency should be ISO 4217 code (ILS for Israeli Shekel, USD, EUR, etc.)
- Date should be ISO 8601 format (YYYY-MM-DD)
- If a field is not present, use null
- The content may be in Hebrew — extract field values as they appear`;

const RECEIPT_SCHEMA = {
  type: FunctionDeclarationSchemaType.OBJECT,
  properties: {
    merchant: { type: FunctionDeclarationSchemaType.STRING },
    date: { type: FunctionDeclarationSchemaType.STRING, nullable: true },
    items: {
      type: FunctionDeclarationSchemaType.ARRAY,
      items: {
        type: FunctionDeclarationSchemaType.OBJECT,
        properties: {
          name: { type: FunctionDeclarationSchemaType.STRING },
          quantity: { type: FunctionDeclarationSchemaType.NUMBER },
          unitPrice: { type: FunctionDeclarationSchemaType.NUMBER },
          totalPrice: { type: FunctionDeclarationSchemaType.NUMBER },
        },
        required: ['name', 'quantity', 'unitPrice', 'totalPrice'],
      },
    },
    subtotal: { type: FunctionDeclarationSchemaType.NUMBER, nullable: true },
    tax: { type: FunctionDeclarationSchemaType.NUMBER, nullable: true },
    total: { type: FunctionDeclarationSchemaType.NUMBER },
    currency: { type: FunctionDeclarationSchemaType.STRING },
    storeLocation: {
      type: FunctionDeclarationSchemaType.STRING,
      nullable: true,
    },
    paymentMethod: {
      type: FunctionDeclarationSchemaType.STRING,
      nullable: true,
    },
  },
  required: ['merchant', 'items', 'total', 'currency'],
};

@Injectable()
export class ExtractorService {
  private readonly logger = new Logger(ExtractorService.name);
  private readonly model: GenerativeModel;

  constructor(private readonly configService: ConfigService) {
    const vertexAi = new VertexAI({
      project: this.configService.getOrThrow('GCP_PROJECT_ID'),
      location: 'me-west1',
    });

    this.model = vertexAi.getGenerativeModel({
      model: 'gemini-2.0-flash',
      generationConfig: {
        responseMimeType: 'application/json',
        responseSchema: RECEIPT_SCHEMA,
      },
    });
  }

  async extract(pdf: Buffer): Promise<ExtractedReceipt> {
    this.logger.log(`Extracting from PDF (${pdf.length} bytes)`);

    const result = await this.model.generateContent({
      contents: [
        {
          role: 'user',
          parts: [
            {
              inlineData: {
                mimeType: 'application/pdf',
                data: pdf.toString('base64'),
              },
            },
            { text: EXTRACTION_PROMPT },
          ],
        },
      ],
    });

    const text = result.response.candidates?.[0]?.content?.parts?.[0]?.text;

    if (!text) {
      throw new Error('Gemini returned empty response');
    }

    const extracted: ExtractedReceipt = JSON.parse(text);
    this.logger.log(
      `Extracted: ${extracted.merchant}, ${extracted.items.length} items, total=${extracted.total}`,
    );

    return extracted;
  }
}
