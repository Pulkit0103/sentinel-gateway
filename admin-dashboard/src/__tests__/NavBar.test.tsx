import '@testing-library/jest-dom'
import React from 'react'
import { render, screen } from '@testing-library/react'

jest.mock('next/navigation', () => ({
  usePathname: () => '/',
}))

jest.mock('next/link', () => ({
  __esModule: true,
  default: function MockLink({
    children,
    href,
  }: {
    children: React.ReactNode
    href: string
  }) {
    return <a href={href}>{children}</a>
  },
}))

import NavBar from '@/components/NavBar'

describe('NavBar', () => {
  it('renders the brand name', () => {
    render(<NavBar />)
    expect(screen.getByText('Sentinel Gateway')).toBeInTheDocument()
  })

  it('renders all navigation links', () => {
    render(<NavBar />)
    expect(screen.getByText('Dashboard')).toBeInTheDocument()
    expect(screen.getByText('Routes')).toBeInTheDocument()
    expect(screen.getByText('API Keys')).toBeInTheDocument()
    expect(screen.getByText('Policies')).toBeInTheDocument()
  })

  it('Dashboard link points to /', () => {
    render(<NavBar />)
    const dashboardLink = screen.getByText('Dashboard').closest('a')
    expect(dashboardLink).toHaveAttribute('href', '/')
  })

  it('Routes link points to /routes', () => {
    render(<NavBar />)
    const routesLink = screen.getByText('Routes').closest('a')
    expect(routesLink).toHaveAttribute('href', '/routes')
  })
})
