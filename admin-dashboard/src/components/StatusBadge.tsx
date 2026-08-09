interface Props {
  value: string
}

const COLORS: Record<string, string> = {
  ACTIVE: 'bg-green-100 text-green-800',
  REVOKED: 'bg-red-100 text-red-800',
  EXPIRED: 'bg-gray-100 text-gray-600',
  UP: 'bg-green-100 text-green-800',
  DOWN: 'bg-red-100 text-red-800',
  UNKNOWN: 'bg-gray-100 text-gray-600',
}

export default function StatusBadge({ value }: Props) {
  const cls = COLORS[value] ?? 'bg-gray-100 text-gray-700'
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${cls}`}>
      {value}
    </span>
  )
}
