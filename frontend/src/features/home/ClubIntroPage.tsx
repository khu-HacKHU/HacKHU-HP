import { ACTIVITIES, CLUB_NAME, CONTACTS, INTRO_PARAGRAPHS } from './clubIntro'
import type { Activity, Contact } from './clubIntro'

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
    <article>
      <h2>{CLUB_NAME} 소개</h2>

      <section aria-labelledby="club-intro-about">
        <h3 id="club-intro-about">소개</h3>
        {intro.map((paragraph) => (
          <p key={paragraph}>{paragraph}</p>
        ))}
      </section>

      <section aria-labelledby="club-intro-activities">
        <h3 id="club-intro-activities">활동</h3>
        <ul>
          {activities.map((activity) => (
            <li key={activity.name}>
              <strong>{activity.name}</strong>
              <p>{activity.description}</p>
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="club-intro-contact">
        <h3 id="club-intro-contact">문의</h3>
        {contacts.length === 0 ? (
          <p>연락처를 준비하고 있어요.</p>
        ) : (
          <ul>
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
        <a href={`mailto:${contact.address}`}>{contact.address}</a>
      </>
    )
  }
  // https 주소만 링크로 만든다. javascript: 같은 주소가 잘못 들어가도 실행되지 않게 한다 (CWE-79)
  if (!contact.url.startsWith('https://')) return <>{contact.label}</>
  // 새 탭으로 여는 외부 링크는 원래 창을 조작하지 못하게 noopener를 붙인다
  return (
    <a href={contact.url} target="_blank" rel="noopener noreferrer">
      {contact.label}
    </a>
  )
}
