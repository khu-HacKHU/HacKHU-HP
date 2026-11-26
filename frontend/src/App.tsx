/*
 * 경로 정의를 한곳에 모은다 (frontend/AGENTS.md "라우팅").
 *
 * 헤더 메뉴(디자인 시스템 7절)가 가리키는 경로는 모두 여기 있어야 한다. 아직 만들지 않은
 * 화면은 Placeholder로 받는다. 메뉴에서 "없는 주소입니다"가 나오면 안 만든 것인지 주소가
 * 틀린 것인지 알 수 없다.
 */
import { BrowserRouter, Link, Route, Routes } from 'react-router'
import { Header } from './components/Header'
import { MemberOnly, NotReady } from './components/Placeholder'
import { PendingNotice } from './components/PendingNotice'
import { AuthProvider } from './components/AuthProvider'
import { LoginPage } from './features/auth/LoginPage'
import { SignupPage } from './features/auth/SignupPage'
import { GalleryPage } from './features/gallery/GalleryPage'
import { AboutPage } from './features/home/AboutPage'
import { HomePage } from './features/home/HomePage'
import './styles/tokens.css'

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Header />
        <PendingNotice />
        {/* 헤더의 "본문으로 건너뛰기"가 여기로 온다 (디자인 시스템 8절). */}
        <main id="main">
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/about" element={<AboutPage />} />
            <Route path="/gallery" element={<GalleryPage />} />

            <Route
              path="/notice"
              element={
                <NotReady
                  title="공지"
                  body="운영진이 올린 동아리 소식을 누구나 읽는 게시판입니다."
                />
              }
            />
            <Route
              path="/free"
              element={
                <MemberOnly
                  title="자유게시판"
                  body="동아리원끼리 이야기하는 게시판입니다."
                />
              }
            />
            <Route
              path="/archive"
              element={
                <MemberOnly
                  title="자료게시판"
                  body="스터디와 활동 자료를 파일로 주고받는 게시판입니다."
                />
              }
            />

            <Route path="/login" element={<LoginPage />} />
            <Route path="/signup" element={<SignupPage />} />
            <Route path="/password" element={<PasswordChangeNotReady />} />
            <Route
              path="/me"
              element={
                <NotReady
                  title="내 정보"
                  body="이메일과 이름, 역할을 보고 비밀번호를 바꾸거나 탈퇴하는 화면입니다."
                />
              }
            />

            <Route
              path="/admin/approvals"
              element={
                <NotReady
                  title="가입 승인"
                  body="운영진이 승인 대기 회원을 승인하거나 거절하는 화면입니다."
                />
              }
            />
            <Route
              path="/admin/members"
              element={
                <NotReady
                  title="회원 관리"
                  body="역할을 바꾸고 비밀번호를 초기화하는 화면입니다."
                />
              }
            />

            <Route path="*" element={<NotFound />} />
          </Routes>
        </main>
      </AuthProvider>
    </BrowserRouter>
  )
}

// 임시 비밀번호로 로그인하면 여기로 온다 (auth 4-2-4). 화면은 #51에서 만들고 이 자리를 대체한다.
function PasswordChangeNotReady() {
  return (
    <p>
      비밀번호를 변경해야 이용할 수 있습니다. 변경 화면은 아직 준비 중입니다.
      운영진에게 문의해 주세요.
    </p>
  )
}

function NotFound() {
  return (
    <p>
      없는 주소입니다. <Link to="/">첫 화면으로</Link>
    </p>
  )
}

export default App
