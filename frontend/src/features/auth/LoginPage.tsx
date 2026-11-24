/*
 * 로그인 화면 (#26, docs/spec/auth/USER-STORIES.md 2절).
 *
 * 실패는 이유를 가리지 않고 같은 안내를 보여 준다. 서버가 이메일 없음과 비밀번호 틀림을
 * 같은 401로 주기 때문이고, 나누어 보여 주면 계정이 있는지 알려 주게 된다 (auth 4-2-4).
 */
import { useId, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../../lib/api'
import s from './AuthForm.module.css'
import { login } from './api'

export function LoginPage() {
  const navigate = useNavigate()
  const emailId = useId()
  const passwordId = useId()
  const errorId = useId()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [sending, setSending] = useState(false)

  const canSubmit = email.trim() !== '' && password !== '' && !sending

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!canSubmit) return
    setError(null)
    setSending(true)
    try {
      const user = await login(email, password)
      // 임시 비밀번호로 로그인하면 비밀번호를 바꿀 때까지 다른 화면을 쓸 수 없다 (auth 4-2-4).
      navigate(user.mustChangePassword ? '/password' : '/')
    } catch (caught) {
      setError(messageOf(caught))
      setSending(false)
    }
  }

  return (
    <section className={s.card}>
      <h1 className={s.title}>로그인</h1>

      <form className={s.form} onSubmit={onSubmit} noValidate>
        {error !== null && (
          <p className={s.alert} id={errorId} role="alert">
            {error}
          </p>
        )}

        <div className={s.field}>
          <label className={s.label} htmlFor={emailId}>
            이메일
          </label>
          <input
            className={s.input}
            id={emailId}
            name="email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            aria-invalid={error !== null}
            aria-describedby={error !== null ? errorId : undefined}
          />
        </div>

        <div className={s.field}>
          <label className={s.label} htmlFor={passwordId}>
            비밀번호
          </label>
          <input
            className={s.input}
            id={passwordId}
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            aria-invalid={error !== null}
            aria-describedby={error !== null ? errorId : undefined}
          />
        </div>

        <button className={s.primary} type="submit" disabled={!canSubmit}>
          {sending ? '로그인하는 중' : '로그인'}
        </button>
      </form>

      <p className={s.footer}>
        계정이 없으면 <Link to="/signup">가입</Link>하세요.
      </p>
    </section>
  )
}

function messageOf(caught: unknown): string {
  if (caught instanceof ApiError) {
    if (caught.status === 429) {
      const seconds = caught.retryAfterSeconds
      return seconds === null
        ? '요청이 많아 잠시 후 다시 시도해주세요.'
        : `요청이 많습니다. ${seconds}초 뒤에 다시 시도해주세요.`
    }
    if (caught.status === 400) {
      return '이메일과 비밀번호를 입력해주세요.'
    }
    // 401을 포함한 나머지. 서버가 왜 실패했는지 알려 주지 않으므로 화면도 나누지 않는다.
    return '이메일 또는 비밀번호가 올바르지 않습니다.'
  }
  return '연결하지 못했습니다. 잠시 후 다시 시도해주세요.'
}
