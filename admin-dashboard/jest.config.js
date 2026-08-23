const nextJest = require('next/jest')

const createJestConfig = nextJest({ dir: './' })

const customConfig = {
  testEnvironment: 'jsdom',
}

module.exports = createJestConfig(customConfig)
