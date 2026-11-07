import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ConfirmModal } from './ConfirmModal'

const baseProps = {
  title: '공지 삭제',
  message: '정말 삭제하시겠어요?',
  confirmLabel: '삭제',
  cancelLabel: '취소',
}

describe('ConfirmModal', () => {
  it('open이 false면 아무것도 보이지 않는다', () => {
    render(
      <ConfirmModal
        {...baseProps}
        open={false}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    )
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('open이 true면 제목과 메시지가 보인다', () => {
    render(
      <ConfirmModal
        {...baseProps}
        open
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    )
    expect(screen.getByRole('dialog')).toBeTruthy()
    expect(screen.getByText('정말 삭제하시겠어요?')).toBeTruthy()
  })

  it('삭제 버튼을 누르면 onConfirm이 호출된다', async () => {
    const onConfirm = vi.fn()
    render(
      <ConfirmModal
        {...baseProps}
        open
        onConfirm={onConfirm}
        onCancel={vi.fn()}
      />,
    )
    await userEvent.click(screen.getByRole('button', { name: '삭제' }))
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  it('취소 버튼을 누르면 onCancel이 호출된다', async () => {
    const onCancel = vi.fn()
    render(
      <ConfirmModal
        {...baseProps}
        open
        onConfirm={vi.fn()}
        onCancel={onCancel}
      />,
    )
    await userEvent.click(screen.getByRole('button', { name: '취소' }))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })
})
