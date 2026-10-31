export interface AppConfig {
  GCP_PROJECT_ID: string;
  API_KEY: string;
  STORAGE_PATH?: string;
}

export function validateEnv(config: Record<string, unknown>): AppConfig {
  const required = ['GCP_PROJECT_ID', 'API_KEY'] as const;

  for (const key of required) {
    if (!config[key]) {
      throw new Error(`Missing required environment variable: ${key}`);
    }
  }

  return {
    GCP_PROJECT_ID: config.GCP_PROJECT_ID as string,
    API_KEY: config.API_KEY as string,
    STORAGE_PATH: (config.STORAGE_PATH as string) || undefined,
  };
}
