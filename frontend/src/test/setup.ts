import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// Vitest는 globals가 꺼져 있어 Testing Library가 자동 cleanup을 등록하지 못한다. 테스트마다 렌더링한 DOM을 지운다.
afterEach(() => cleanup())
