export interface ReceiptLineItem {
  name: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
}

export interface ExtractedReceipt {
  merchant: string;
  date: string | null;
  items: ReceiptLineItem[];
  subtotal: number | null;
  tax: number | null;
  total: number;
  currency: string;
  storeLocation: string | null;
  paymentMethod: string | null;
}

export interface ProcessedReceipt {
  clientId: string;
  extracted: ExtractedReceipt;
  snapshotUrl: string;
}
