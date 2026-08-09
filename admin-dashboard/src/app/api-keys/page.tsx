'use client'

import { useEffect, useState } from 'react'
import { fetchApiKeys, createApiKey, revokeApiKey, getToken } from '@/lib/api'
import type { ApiKeyInfo, CreatedApiKey, CreateApiKeyRequest } from '@/lib/types'
import TokenSetup from '@/components/TokenSetup'
import StatusBadge from '@/components/StatusBadge'

export default function ApiKeysPage() {
  const [keys, setKeys] = useState<ApiKeyInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [hasToken, setHasToken] = useState(false)
  const [createdKey, setCreatedKey] = useState<CreatedApiKey | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState<CreateApiKeyRequest>({ clientId: '', tenantId: '', scopes: '' })
  const [formError, setFormError] = useState<string | null>(null)

  useEffect(() => { setHasToken(!!getToken()) }, [])

  async function loadKeys() {
    try {
      const data = await fetchApiKeys()
      setKeys(data)
    } catch (e) {
      setError(String(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!hasToken) { setLoading(false); return }
    loadKeys()
  }, [hasToken])

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    setFormError(null)
    try {
      const created = await createApiKey(form)
      setCreatedKey(created)
      setShowForm(false)
      setForm({ clientId: '', tenantId: '', scopes: '' })
      await loadKeys()
    } catch (e) {
      setFormError(String(e))
    }
  }

  async function handleRevoke(id: number) {
    if (!confirm('Revoke this API key?')) return
    try {
      await revokeApiKey(id)
      await loadKeys()
    } catch (e) {
      setError(String(e))
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">API Keys</h1>
        {hasToken && (
          <button
            onClick={() => setShowForm(!showForm)}
            className="bg-blue-600 text-white text-sm px-4 py-2 rounded hover:bg-blue-700 transition-colors"
          >
            {showForm ? 'Cancel' : '+ New Key'}
          </button>
        )}
      </div>

      {!hasToken && <TokenSetup onTokenSet={() => setHasToken(true)} />}

      {createdKey && (
        <div className="bg-green-50 border border-green-300 rounded-lg p-4 mb-6">
          <p className="text-sm font-medium text-green-800 mb-1">
            Key created — copy the raw key now. It will not be shown again.
          </p>
          <code className="block bg-green-100 rounded px-3 py-2 text-xs font-mono break-all text-green-900">
            {createdKey.rawKey}
          </code>
          <button
            onClick={() => setCreatedKey(null)}
            className="mt-2 text-xs text-green-700 underline"
          >
            Dismiss
          </button>
        </div>
      )}

      {showForm && (
        <form onSubmit={handleCreate} className="bg-white border border-gray-200 rounded-lg p-4 mb-6 space-y-3">
          <h2 className="text-sm font-semibold text-gray-700">Create new API key</h2>
          {formError && (
            <p className="text-xs text-red-600">{formError}</p>
          )}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-gray-600 mb-1">Client ID *</label>
              <input
                required
                value={form.clientId}
                onChange={e => setForm({ ...form, clientId: e.target.value })}
                className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-600 mb-1">Tenant ID</label>
              <input
                value={form.tenantId}
                onChange={e => setForm({ ...form, tenantId: e.target.value })}
                className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm"
              />
            </div>
          </div>
          <div>
            <label className="block text-xs text-gray-600 mb-1">Scopes (space-separated)</label>
            <input
              value={form.scopes}
              onChange={e => setForm({ ...form, scopes: e.target.value })}
              placeholder="read write admin"
              className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm"
            />
          </div>
          <button
            type="submit"
            className="bg-blue-600 text-white text-sm px-4 py-2 rounded hover:bg-blue-700 transition-colors"
          >
            Create Key
          </button>
        </form>
      )}

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
                {['ID', 'Client ID', 'Tenant', 'Status', 'Scopes', 'Created', 'Actions'].map(h => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {keys.map(k => (
                <tr key={k.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-xs text-gray-500">{k.id}</td>
                  <td className="px-4 py-3 text-xs font-mono text-gray-900">{k.clientId}</td>
                  <td className="px-4 py-3 text-xs text-gray-600">{k.tenantId ?? '—'}</td>
                  <td className="px-4 py-3"><StatusBadge value={k.status} /></td>
                  <td className="px-4 py-3 text-xs text-gray-600 font-mono">{k.scopes ?? '—'}</td>
                  <td className="px-4 py-3 text-xs text-gray-500">
                    {k.createdAt ? new Date(k.createdAt).toLocaleDateString() : '—'}
                  </td>
                  <td className="px-4 py-3">
                    {k.status === 'ACTIVE' && (
                      <button
                        onClick={() => handleRevoke(k.id)}
                        className="text-xs text-red-600 hover:text-red-800 font-medium"
                      >
                        Revoke
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {keys.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-4 py-8 text-center text-sm text-gray-400">
                    No API keys found
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
