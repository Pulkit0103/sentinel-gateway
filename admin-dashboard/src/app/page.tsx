'use client'

import { useEffect, useState } from 'react'
import {
  fetchHealth,
  fetchMetric,
  fetchRoutes,
  fetchApiKeys,
  fetchPolicies,
  getToken,
} from '@/lib/api'
import type { HealthStatus, MetricResponse } from '@/lib/types'
import TokenSetup from '@/components/TokenSetup'
import StatusBadge from '@/components/StatusBadge'

interface Stats {
  totalRoutes: number
  activeKeys: number
  totalPolicies: number
}

export default function DashboardPage() {
  const [hasToken, setHasToken] = useState(false)
  const [health, setHealth] = useState<HealthStatus | null>(null)
  const [requestsMetric, setRequestsMetric] = useState<MetricResponse | null>(null)
  const [stats, setStats] = useState<Stats | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setHasToken(!!getToken())
  }, [])

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      setError(null)
      try {
        const [h, metric, routes, keys, policies] = await Promise.allSettled([
          fetchHealth(),
          fetchMetric('gateway.requests.total'),
          fetchRoutes(),
          fetchApiKeys(),
          fetchPolicies(),
        ])
        if (cancelled) return

        if (h.status === 'fulfilled') setHealth(h.value)
        if (metric.status === 'fulfilled') setRequestsMetric(metric.value)
        if (
          routes.status === 'fulfilled' &&
          keys.status === 'fulfilled' &&
          policies.status === 'fulfilled'
        ) {
          setStats({
            totalRoutes: routes.value.length,
            activeKeys: keys.value.filter(k => k.status === 'ACTIVE').length,
            totalPolicies: policies.value.length,
          })
        }
      } catch (e) {
        if (!cancelled) setError(String(e))
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    load()
    return () => { cancelled = true }
  }, [hasToken])

  const totalRequests = requestsMetric?.measurements.find(
    m => m.statistic === 'COUNT'
  )?.value

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Dashboard</h1>

      {!hasToken && (
        <TokenSetup onTokenSet={() => setHasToken(true)} />
      )}

      {loading && (
        <p className="text-gray-500 text-sm">Loading…</p>
      )}

      {error && (
        <div className="bg-red-50 border border-red-200 rounded p-3 text-sm text-red-700 mb-4">
          {error}
        </div>
      )}

      {!loading && (
        <>
          {health && (
            <div className="mb-6 flex items-center gap-3">
              <span className="text-sm font-medium text-gray-600">Gateway health:</span>
              <StatusBadge value={health.status} />
            </div>
          )}

          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
            <StatCard label="Total Requests" value={totalRequests?.toFixed(0) ?? '—'} />
            <StatCard label="Routes" value={stats?.totalRoutes?.toString() ?? '—'} />
            <StatCard label="Active API Keys" value={stats?.activeKeys?.toString() ?? '—'} />
            <StatCard label="Policies" value={stats?.totalPolicies?.toString() ?? '—'} />
          </div>
        </>
      )}
    </div>
  )
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-white rounded-lg border border-gray-200 p-4">
      <p className="text-xs text-gray-500 uppercase tracking-wide">{label}</p>
      <p className="text-2xl font-bold text-gray-900 mt-1">{value}</p>
    </div>
  )
}
