import { IsNotEmpty, IsString, IsUrl } from 'class-validator';

export class ProcessReceiptDto {
  @IsUrl()
  @IsNotEmpty()
  url!: string;

  @IsString()
  @IsNotEmpty()
  clientId!: string;
}
