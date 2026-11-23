/*
 * 글꼴 파일은 번들에 포함해 배포한다. 외부 CDN을 거치지 않는다.
 *
 * Pretendard는 본문과 제목(--sans), Outfit은 영문 이름표(--display)에 쓴다.
 *
 * 둘 다 가변 글꼴이라 파일 하나가 굵기 전부를 담는다. Pretendard는 dynamic-subset을 쓴다.
 * 한글 글꼴은 통째로 받으면 1MB를 넘어서, 글자 범위별로 쪼갠 파일 92개 가운데 화면에 실제로
 * 나온 범위만 받는다. 정적 파일을 굵기마다 받으면 4MB가 넘는다.
 *
 * 날짜와 글 번호는 고정폭이어야 해서 --mono(IBM Plex Mono)를 그대로 쓴다. 그 글꼴 파일은
 * 아직 넣지 않아 OS 고정폭 글꼴로 뒤를 받친다.
 */
import 'pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css'
import '@fontsource-variable/outfit'

import './styles/tokens.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
