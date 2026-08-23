'use client'

import { useEffect, useState } from 'react'
import { fetchRoutes, getToken } from '@/lib/api'
import type { RouteInfo } from '@/lib/types'
import TokenSetup from '@/components/TokenSetup'

export default function RoutesPage() {
  const [routes, setRoutes] = useState<RouteInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [hasToken, setHasToken] = useState(false)

  useEffect(() => {
    setHasToken(!!getToken())
  }, [])

  useEffect(() => {
    if (!hasToken) { setLoading(false); return }
    let cancelled = false
    fetchRoutes()
      .then(data => { if (!cancelled) { setRoutes(data); setLoading(false) } })
      .catch(e => { if (!cancelled) { setError(String(e)); setLoading(false) } })
    return () => { cancelled = true }
  }, [hasToken])

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Routes</h1>

      {!hasToken && <TokenSetup onTokenSet={() => setHasToken(true)} />}

      {loading && <p className="text-gray-500 text-sm">Loading…</p>}

      {error && (
        <div className="bg-red-50 border border-red-200 rounded p-3 text-sm text-red-700 mb-4">
          {error}
        </div>
      )}

      {!loading && !error && (
        <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                {['Route ID', 'Path', 'Service URI', 'Methods', 'Status', 'Rate Limit'].map(h => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {routes.map(r => (
                <tr key={r.routeId} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-mono text-xs text-gray-900">{r.routeId}</td>
                  <td className="px-4 py-3 font-mono text-xs text-blue-700">{r.path}</td>
                  <td className="px-4 py-3 font-mono text-xs text-gray-600 truncate max-w-xs">{r.serviceUri}</td>
                  <td className="px-4 py-3 text-xs text-gray-700">{r.methods.join(', ')}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                      r.enabled ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-500'
                    }`}>
                      {r.enabled ? 'ENABLED' : 'DISABLED'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-600">{r.rateLimitPolicy}</td>
                </tr>
              ))}
              {routes.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-sm text-gray-400">
                    No routes found
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
