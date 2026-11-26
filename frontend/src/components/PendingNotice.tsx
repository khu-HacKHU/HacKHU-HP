/*
 * 승인 대기 안내 (#26 완료 조건).
 *
 * 승인 대기 회원은 로그인은 되지만 공개된 글만 볼 수 있다 (auth 4-2-4, PRODUCT.md 역할).
 * 어느 화면에 있어도 지금 상태를 알 수 있게 헤더 아래에 둔다.
 */
import { useAuth } from '../lib/useAuth'
import s from './PendingNotice.module.css'

export function PendingNotice() {
  const { session } = useAuth()
  if (session === null || session.user.role !== 'PENDING') return null

  return (
    <p className={s.notice} role="status">
      승인 대기 중입니다.
      <br />
      운영진이 승인하면 동아리원이 되어 자유게시판과 자료게시판을 볼 수
      있습니다.
    </p>
  )
}
