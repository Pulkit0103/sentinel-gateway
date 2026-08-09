'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'

const NAV_LINKS = [
  { href: '/', label: 'Dashboard' },
  { href: '/routes', label: 'Routes' },
  { href: '/api-keys', label: 'API Keys' },
  { href: '/policies', label: 'Policies' },
]

export default function NavBar() {
  const pathname = usePathname()

  return (
    <nav className="bg-gray-900 text-white px-6 py-3 flex items-center gap-8">
      <span className="font-bold text-lg tracking-tight">Sentinel Gateway</span>
      <div className="flex gap-4">
        {NAV_LINKS.map(({ href, label }) => (
          <Link
            key={href}
            href={href}
            className={`text-sm px-3 py-1 rounded transition-colors ${
              pathname === href
                ? 'bg-blue-600 text-white'
                : 'text-gray-300 hover:text-white hover:bg-gray-700'
            }`}
          >
            {label}
          </Link>
        ))}
      </div>
    </nav>
  )
}
