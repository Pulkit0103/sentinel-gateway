import type {
  ApiKeyInfo,
  CreatedApiKey,
  CreateApiKeyRequest,
  HealthStatus,
  MetricResponse,
  PolicyInfo,
  RouteInfo,
} from './types'

const GATEWAY_URL =
  process.env.NEXT_PUBLIC_GATEWAY_URL ?? 'http://localhost:8080'

export const TOKEN_KEY = 'sentinel-admin-token'

export function getToken(): string | null {
  if (typeof window === 'undefined') return null
  return window.localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  window.localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  window.localStorage.removeItem(TOKEN_KEY)
}

async function request<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const token = getToken()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const res = await fetch(`${GATEWAY_URL}${path}`, { ...options, headers })
  if (!res.ok) {
    throw new Error(`HTTP ${res.status} ${res.statusText} — ${path}`)
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

// ── Public (no auth needed) ───────────────────────────────────────────────────

export async function fetchHealth(): Promise<HealthStatus> {
  return request<HealthStatus>('/actuator/health')
}

export async function fetchMetric(name: string): Promise<MetricResponse> {
  return request<MetricResponse>(`/actuator/metrics/${name}`)
}

// ── Admin API (ROLE_ADMIN required) ───────────────────────────────────────────

export async function fetchRoutes(): Promise<RouteInfo[]> {
  return request<RouteInfo[]>('/admin/routes')
}

export async function fetchApiKeys(): Promise<ApiKeyInfo[]> {
  return request<ApiKeyInfo[]>('/admin/api-keys')
}

export async function createApiKey(body: CreateApiKeyRequest): Promise<CreatedApiKey> {
  return request<CreatedApiKey>('/admin/api-keys', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function revokeApiKey(id: number): Promise<void> {
  await request<void>(`/admin/api-keys/${id}`, { method: 'DELETE' })
}

export async function fetchPolicies(): Promise<PolicyInfo[]> {
  return request<PolicyInfo[]>('/admin/policies')
}
