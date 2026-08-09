'use client'

import { useState } from 'react'
import { setToken } from '@/lib/api'

interface Props {
  onTokenSet: () => void
}

export default function TokenSetup({ onTokenSet }: Props) {
  const [value, setValue] = useState('')

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const trimmed = value.trim()
    if (trimmed) {
      setToken(trimmed)
      onTokenSet()
    }
  }

  return (
    <div className="bg-yellow-50 border border-yellow-300 rounded-lg p-4 mb-6">
      <p className="text-sm font-medium text-yellow-800 mb-2">
        Admin token required — paste a Bearer JWT with{' '}
        <code className="bg-yellow-100 px-1 rounded">ROLE_ADMIN</code>
      </p>
      <form onSubmit={handleSubmit} className="flex gap-2">
        <input
          type="password"
          value={value}
          onChange={e => setValue(e.target.value)}
          placeholder="eyJhbGciOi..."
          className="flex-1 text-sm border border-gray-300 rounded px-3 py-1.5 font-mono"
        />
        <button
          type="submit"
          className="bg-blue-600 text-white text-sm px-4 py-1.5 rounded hover:bg-blue-700 transition-colors"
        >
          Save
        </button>
      </form>
    </div>
  )
}
