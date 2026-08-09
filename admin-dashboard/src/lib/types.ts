export interface RouteInfo {
  routeId: string
  path: string
  serviceUri: string
  methods: string[]
  enabled: boolean
  requiredScopes: string[]
  tenantRequired: boolean
  rateLimitPolicy: string
}

export interface ApiKeyInfo {
  id: number
  clientId: string
  tenantId: string | null
  status: string
  scopes: string | null
  createdAt: string
  expiresAt: string | null
  revokedAt: string | null
}

export interface CreatedApiKey {
  rawKey: string
  id: number
  clientId: string
  tenantId: string | null
  status: string
  scopes: string | null
  createdAt: string
  expiresAt: string | null
}

export interface PolicyInfo {
  id: number
  policyId: string
  routeId: string
  requireMfa: boolean
  requestSigningRequired: boolean
  requiredScopes: string | null
  allowedMethods: string | null
  rateLimitPolicy: string
  createdAt: string
  updatedAt: string
}

export interface HealthStatus {
  status: 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN'
  components?: Record<string, { status: string }>
}

export interface MetricMeasurement {
  statistic: string
  value: number
}

export interface MetricResponse {
  name: string
  measurements: MetricMeasurement[]
  availableTags: { tag: string; values: string[] }[]
}

export interface CreateApiKeyRequest {
  clientId: string
  tenantId: string
  scopes: string
  expiresAt?: string
}
