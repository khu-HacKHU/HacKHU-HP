import { render, screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import App from './App.tsx'

test('첫 화면에 동아리 이름이 보인다', () => {
  render(<App />)
  expect(screen.getByRole('heading', { name: 'HacKHU' })).toBeDefined()
})
