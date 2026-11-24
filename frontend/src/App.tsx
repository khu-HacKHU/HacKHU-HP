/*
 * 경로 정의를 한곳에 모은다 (frontend/AGENTS.md "라우팅").
 */
import { BrowserRouter, Link, Route, Routes } from 'react-router'
import { Header } from './components/Header'
import { PendingNotice } from './components/PendingNotice'
import { AuthProvider } from './components/AuthProvider'
import { LoginPage } from './features/auth/LoginPage'
import { SignupPage } from './features/auth/SignupPage'
import { GalleryPage } from './features/gallery/GalleryPage'
import { useAuth } from './lib/useAuth'
import './styles/tokens.css'

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Header />
        <PendingNotice />
        <main>
          <Routes>
            <Route path="/" element={<GalleryRoute />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/signup" element={<SignupPage />} />
            <Route path="/password" element={<PasswordChangeNotReady />} />
            <Route path="*" element={<NotFound />} />
          </Routes>
        </main>
      </AuthProvider>
    </BrowserRouter>
  )
}

// 앨범 올리기 버튼은 동아리원 이상(MEMBER, STAFF, ADMIN)에게만 보인다 (gallery USER-STORIES 2절,
// backend 5-1). 로그인하지 않았거나 승인 대기(PENDING)면 숨긴다. 화면 편의일 뿐이고 권한 검사는
// 서버가 한다 (frontend/AGENTS.md "인증 상태").
function GalleryRoute() {
  const role = useAuth().session?.user.role
  return <GalleryPage canUpload={role !== undefined && role !== 'PENDING'} />
}

// 임시 비밀번호로 로그인하면 여기로 온다 (auth 4-2-4). 화면은 #51에서 만들고 이 자리를 대체한다.
function PasswordChangeNotReady() {
  return (
    <p>
      비밀번호를 변경해야 이용할 수 있습니다. 변경 화면은 아직 준비 중입니다.
      운영진에게 문의해주세요.
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
