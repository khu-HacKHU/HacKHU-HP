import { ACTIVITIES, CLUB_NAME, CONTACTS, INTRO_PARAGRAPHS } from './clubIntro'
import type { Activity, Contact } from './clubIntro'
import s from './ClubIntroPage.module.css'

type ClubIntroPageProps = {
  // 기본은 clubIntro.ts의 내용. 테스트에서 바꿔 넣을 수 있게 열어 둔다
  intro?: string[]
  activities?: Activity[]
  contacts?: Contact[]
}

// 동아리 소개 화면: 소개, 활동, 문의, 로그인하지 않아도 모두 보인다
export function ClubIntroPage({
  intro = INTRO_PARAGRAPHS,
  activities = ACTIVITIES,
  contacts = CONTACTS,
}: ClubIntroPageProps) {
  return (
    <article className={s.page}>
      <h1 className={s.title}>{CLUB_NAME} 소개</h1>

      <section className={s.section} aria-labelledby="club-intro-about">
        <h2 className={s.sectionTitle} id="club-intro-about">
          소개
        </h2>
        {intro.map((paragraph) => (
          <p className={s.paragraph} key={paragraph}>
            {paragraph}
          </p>
        ))}
      </section>

      <section className={s.section} aria-labelledby="club-intro-activities">
        <h2 className={s.sectionTitle} id="club-intro-activities">
          활동
        </h2>
        <ul className={s.list}>
          {activities.map((activity) => (
            <li className={s.activity} key={activity.name}>
              <strong className={s.activityName}>{activity.name}</strong>
              <p className={s.activityDescription}>{activity.description}</p>
            </li>
          ))}
        </ul>
      </section>

      <section className={s.section} aria-labelledby="club-intro-contact">
        <h2 className={s.sectionTitle} id="club-intro-contact">
          문의
        </h2>
        {contacts.length === 0 ? (
          <p className={s.empty}>연락처를 준비하고 있어요.</p>
        ) : (
          <ul className={s.list}>
            {contacts.map((contact) => (
              <li key={contact.label}>
                <ContactLink contact={contact} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </article>
  )
}

function ContactLink({ contact }: { contact: Contact }) {
  if (contact.kind === 'email') {
    return (
      <>
        {contact.label}:{' '}
        <a className={s.link} href={`mailto:${contact.address}`}>
          {contact.address}
        </a>
      </>
    )
  }
  // https 주소만 링크로 만든다. javascript: 같은 주소가 잘못 들어가도 실행되지 않게 한다 (CWE-79)
  if (!contact.url.startsWith('https://')) return <>{contact.label}</>
  // 새 탭으로 여는 외부 링크는 원래 창을 조작하지 못하게 noopener를 붙인다
  return (
    <a
      className={s.link}
      href={contact.url}
      target="_blank"
      rel="noopener noreferrer"
    >
      {contact.label}
    </a>
  )
}
