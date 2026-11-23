/*
 * 아직 만들지 않은 화면을 받는 자리 (#57).
 *
 * 헤더 메뉴는 디자인 시스템 7절이 정한 다섯 개를 모두 보여 준다. 그런데 게시판과 내 정보
 * 화면은 아직 없다. 메뉴에서 눌렀을 때 "없는 주소입니다"가 나오면 안 만든 것인지 주소를
 * 잘못 쓴 것인지 알 수 없으므로, 각 자리가 무엇인지 말해 주는 화면으로 받는다.
 *
 * 두 기능 이상(게시판, 인증권한)이 쓰므로 components/에 둔다 (frontend/AGENTS.md "폴더 구조").
 */
import { Link } from 'react-router'
import { useAuth } from '../lib/useAuth'
import s from './Placeholder.module.css'

type NotReadyProps = {
  /** 이 자리에 들어올 화면 이름. 예: 공지 목록 */
  title: string
  /** 그 화면이 할 일 한 줄. */
  body: string
}

/** 만들 예정인 화면 자리. */
export function NotReady({ title, body }: NotReadyProps) {
  return (
    <section className={s.page}>
      <div className={s.card}>
        <h1 className={s.title}>{title}</h1>
        <p className={s.body}>{body}</p>
        <p className={s.note}>이 화면은 아직 준비 중입니다.</p>
        <Link className={s.button} to="/">
          첫 화면으로
        </Link>
      </div>
    </section>
  )
}

/**
 * 동아리원만 보는 게시판 자리.
 *
 * 자유게시판과 자료게시판은 방문자에게도 메뉴에 보이고, 누르면 회원 전용 안내가 나온다
 * (디자인 시스템 7절). 권한 검사는 서버가 한다. 이 화면은 안내일 뿐이다.
 */
export function MemberOnly({ title, body }: NotReadyProps) {
  const { session, restoring } = useAuth()

  if (restoring) {
    // 복구가 끝나기 전에는 로그인 여부를 단정하지 않는다. 안내가 깜빡이는 것을 막는다.
    return null
  }

  if (session === null) {
    return (
      <section className={s.page}>
        <div className={s.card}>
          <h1 className={s.title}>{title}</h1>
          <p className={s.body}>{body}</p>
          <p className={s.warn}>
            동아리원만 볼 수 있습니다.
            <br />
            먼저 로그인하세요.
          </p>
          <div className={s.actions}>
            <Link className={s.primaryButton} to="/login">
              로그인하기
            </Link>
            <Link className={s.button} to="/signup">
              가입하기
            </Link>
          </div>
        </div>
      </section>
    )
  }

  if (session.user.role === 'PENDING') {
    return (
      <section className={s.page}>
        <div className={s.card}>
          <h1 className={s.title}>{title}</h1>
          <p className={s.body}>{body}</p>
          <p className={s.warn}>
            운영진이 가입을 승인하면 볼 수 있습니다.
            <br />
            승인 전에는 열리지 않습니다.
          </p>
          <Link className={s.button} to="/">
            첫 화면으로
          </Link>
        </div>
      </section>
    )
  }

  return <NotReady title={title} body={body} />
}
