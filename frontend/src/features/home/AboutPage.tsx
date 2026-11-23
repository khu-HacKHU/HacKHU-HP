/*
 * 동아리 소개 (#57).
 *
 * 영역은 home/USER-STORIES.md 2절을 따른다. 소개, 활동, 문의다.
 *
 * 메인 화면과 달리 터미널 표현과 빛 번짐을 쓰지 않는다. 기능을 쓰는 화면은 평범한 한국어
 * 제목으로 두고 배경도 단색이다 (UI 디자인 시스템 1·5절).
 *
 * 배치 규칙은 메인 화면과 같다 (design-taste-frontend). 섹션 하나가 한 화면이고, 섹션 사이를
 * 선으로 나누지 않고, 제목을 섹션 맨 위에 둔다.
 */
import { Link } from 'react-router'
import s from './AboutPage.module.css'

/*
 * 활동 세 가지 (home/USER-STORIES 2절). 제목은 무엇을 하는지로 쓴다.
 * 문구는 화면에서 고칠 수 없다. 바꾸려면 운영진이 홈페이지 담당자에게 요청한다.
 */
const ACTIVITIES = [
  {
    label: 'STUDY',
    title: '매주 스터디로 보안 기초부터 공부합니다',
    body: '입문자도 함께할 수 있도록 차근차근 다룹니다.',
  },
  {
    label: 'PROJECT',
    title: 'CTF 문제를 제작합니다',
    body: '버그 바운티 같은 팀 프로젝트를 함께 진행합니다.',
  },
  {
    label: 'CTF',
    title: '다른 대학 보안 동아리와 CTF를 진행합니다',
    body: '연합 CTF 교류전에 팀으로 참가합니다.',
  },
]

export function AboutPage() {
  return (
    <>
      <section className={s.hero} aria-labelledby="about-title">
        <div className={s.wrap}>
          <div className={s.heroText} data-enter="">
            <p className={s.heroFact}>매주 정기 스터디</p>

            <h1 className={s.heroTitle} id="about-title">
              경희대학교 정보보안 동아리
            </h1>

            <p className={s.heroLead}>
              HacKHU는 경희대학교 최초의 정보보안 동아리입니다.
              <br />
              전공과 학년, 경험에 관계없이 보안에 관심 있는 학생이라면 누구나
              참여할 수 있습니다.
            </p>

            <div className={s.actions}>
              <Link className={s.primaryButton} to="/signup">
                가입하기
              </Link>
              <Link className={s.button} to="/gallery">
                활동 사진 보기
              </Link>
            </div>
          </div>
        </div>
      </section>

      <section
        className={s.section}
        id="activity"
        aria-labelledby="activity-title"
      >
        <div className={s.wrap}>
          <div className={s.sectionHead}>
            <p className={s.sectionLabel}>
              <span className={s.dot} aria-hidden="true" />
              ACTIVITY
            </p>
            <h2 className={s.sectionTitle} id="activity-title">
              하는 일
            </h2>
          </div>

          <ul className={s.cardGrid}>
            {ACTIVITIES.map((activity) => (
              <li className={s.card} key={activity.label}>
                <p className={s.cardLabel}>{activity.label}</p>
                <strong className={s.cardTitle}>{activity.title}</strong>
                <p className={s.cardBody}>{activity.body}</p>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <footer className={s.closing}>
        <div className={s.closingWrap}>
          <div className={s.closingRow}>
            <p className={s.sectionLabel}>
              <span className={s.dot} aria-hidden="true" />
              CONTACT
            </p>
            <h2 className={s.closingTitle}>문의와 가입</h2>
            <p className={s.closingLead}>
              동아리 활동에 궁금한 점이 있으면 이메일로 문의해 주세요.
              <br />
              홈페이지 회원가입과 동아리 지원은 별개로 진행됩니다.
            </p>

            {/*
             * 자리표시자 주소다. 동아리 공용 연락처는 아직 미정이고
             * (home/USER-STORIES 2절), 실제 주소를 받으면 이 한 줄만 바꾼다.
             * 개인 휴대전화 번호는 넣지 않는다.
             */}
            <p className={s.contact} data-assumed="">
              <a className={s.footerLink} href="mailto:hackhu@example.ac.kr">
                hackhu@example.ac.kr
              </a>
            </p>

            <div className={s.actions}>
              <Link className={s.primaryButton} to="/signup">
                가입하기
              </Link>
            </div>
          </div>

          <div className={s.legalRow}>
            <p className={s.footerName}>경희대학교 정보보안 동아리 HacKHU</p>
            <Link className={s.footerLink} to="/">
              첫 화면으로
            </Link>
          </div>
        </div>
      </footer>
    </>
  )
}
