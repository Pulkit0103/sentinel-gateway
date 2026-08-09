'use client'

import { useEffect, useState } from 'react'
import { fetchPolicies, getToken } from '@/lib/api'
import type { PolicyInfo } from '@/lib/types'
import TokenSetup from '@/components/TokenSetup'

export default function PoliciesPage() {
  const [policies, setPolicies] = useState<PolicyInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [hasToken, setHasToken] = useState(false)

  useEffect(() => { setHasToken(!!getToken()) }, [])

  useEffect(() => {
    if (!hasToken) { setLoading(false); return }
    let cancelled = false
    fetchPolicies()
      .then(data => { if (!cancelled) { setPolicies(data); setLoading(false) } })
      .catch(e => { if (!cancelled) { setError(String(e)); setLoading(false) } })
    return () => { cancelled = true }
  }, [hasToken])

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Security Policies</h1>

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
                {['Policy ID', 'Route', 'MFA Required', 'Signing Required', 'Allowed Methods', 'Rate Limit'].map(h => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {policies.map(p => (
                <tr key={p.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-xs font-mono text-gray-900">{p.policyId}</td>
                  <td className="px-4 py-3 text-xs font-mono text-blue-700">{p.routeId}</td>
                  <td className="px-4 py-3">
                    <BoolBadge value={p.requireMfa} />
                  </td>
                  <td className="px-4 py-3">
                    <BoolBadge value={p.requestSigningRequired} />
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-600">{p.allowedMethods ?? 'ALL'}</td>
                  <td className="px-4 py-3 text-xs text-gray-600">{p.rateLimitPolicy}</td>
                </tr>
              ))}
              {policies.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-sm text-gray-400">
                    No policies found
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

function BoolBadge({ value }: { value: boolean }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
      value ? 'bg-orange-100 text-orange-800' : 'bg-gray-100 text-gray-500'
    }`}>
      {value ? 'YES' : 'NO'}
    </span>
  )
}
