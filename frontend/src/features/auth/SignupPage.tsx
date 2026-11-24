/*
 * 가입 화면 (#26, docs/spec/auth/USER-STORIES.md 1절).
 *
 * 가입은 토큰을 주지 않는다. 성공하면 로그인시키지 않고 승인 대기 안내를 보여 준다 (auth 4-2-3).
 */
import { useId, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../../lib/api'
import s from './AuthForm.module.css'
import { signup } from './api'

export function SignupPage() {
  const emailId = useId()
  const nameId = useId()
  const passwordId = useId()
  const errorId = useId()

  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<ApiError | Error | null>(null)
  const [sending, setSending] = useState(false)
  const [done, setDone] = useState(false)

  const canSubmit =
    email.trim() !== '' && name.trim() !== '' && password !== '' && !sending

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!canSubmit) return
    setError(null)
    setSending(true)
    try {
      await signup({ email, name, password })
      setDone(true)
    } catch (caught) {
      setError(caught instanceof Error ? caught : new Error('알 수 없는 오류'))
      setSending(false)
    }
  }

  if (done) {
    return (
      <section className={s.card}>
        <h1 className={s.title}>가입 신청을 받았습니다</h1>
        <p className={s.notice}>
          운영진이 승인하면 동아리원이 됩니다. 승인 전에는 공개된 글만 볼 수
          있습니다.
        </p>
        <p className={s.footer}>
          승인을 기다리는 동안 <Link to="/login">로그인</Link>해서 상태를 볼 수
          있습니다.
        </p>
      </section>
    )
  }

  const apiError = error instanceof ApiError ? error : null
  const summary = error === null ? null : summaryOf(error)

  return (
    <section className={s.card}>
      <h1 className={s.title}>가입</h1>

      <form className={s.form} onSubmit={onSubmit} noValidate>
        {summary !== null && (
          <p className={s.alert} id={errorId} role="alert">
            {summary}
          </p>
        )}

        <Field
          id={emailId}
          label="이메일"
          type="email"
          autoComplete="email"
          value={email}
          onChange={setEmail}
          reason={apiError?.reasonFor('email') ?? null}
          describedBy={summary !== null ? errorId : undefined}
        />

        <Field
          id={nameId}
          label="이름"
          type="text"
          autoComplete="name"
          value={name}
          onChange={setName}
          reason={apiError?.reasonFor('name') ?? null}
          hint="1자 이상 30자 이하"
        />

        <Field
          id={passwordId}
          label="비밀번호"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={setPassword}
          reason={apiError?.reasonFor('password') ?? null}
        />

        <button className={s.primary} type="submit" disabled={!canSubmit}>
          {sending ? '가입하는 중' : '가입'}
        </button>
      </form>

      <p className={s.footer}>
        이미 계정이 있으면 <Link to="/login">로그인</Link>하세요.
      </p>
    </section>
  )
}

type FieldProps = {
  id: string
  label: string
  type: string
  autoComplete: string
  value: string
  onChange: (value: string) => void
  reason: string | null
  hint?: string
  describedBy?: string
}

function Field(props: FieldProps) {
  const reasonId = `${props.id}-reason`
  const hintId = `${props.id}-hint`
  const describedBy =
    [
      props.reason !== null ? reasonId : null,
      props.hint !== undefined ? hintId : null,
      props.describedBy ?? null,
    ]
      .filter((it) => it !== null)
      .join(' ') || undefined

  return (
    <div className={s.field}>
      <label className={s.label} htmlFor={props.id}>
        {props.label}
      </label>
      <input
        className={s.input}
        id={props.id}
        name={props.id}
        type={props.type}
        autoComplete={props.autoComplete}
        value={props.value}
        onChange={(event) => props.onChange(event.target.value)}
        aria-invalid={props.reason !== null}
        aria-describedby={describedBy}
      />
      {props.hint !== undefined && (
        <span className={s.hint} id={hintId}>
          {props.hint}
        </span>
      )}
      {props.reason !== null && (
        <span className={s.fieldError} id={reasonId}>
          {props.reason}
        </span>
      )}
    </div>
  )
}

function summaryOf(error: Error): string {
  if (!(error instanceof ApiError)) {
    return '연결하지 못했습니다. 잠시 후 다시 시도해주세요.'
  }
  if (error.status === 409) {
    return '이미 가입된 이메일입니다.'
  }
  if (error.status === 429) {
    const seconds = error.retryAfterSeconds
    return seconds === null
      ? '요청이 많아 잠시 후 다시 시도해주세요.'
      : `요청이 많습니다. ${seconds}초 뒤에 다시 시도해주세요.`
  }
  if (error.status === 400) {
    return '입력한 내용을 다시 확인해주세요.'
  }
  return '가입하지 못했습니다. 잠시 후 다시 시도해주세요.'
}
