import { Link, NavLink } from "react-router-dom";
import Icon from "./Icon.jsx";

// Global top nav used on the main marketing/browse pages.
// `active` marks which primary link is highlighted (matches original per-page markup).
export default function TopNav({ active = "events" }) {
  const linkBase = "font-nav-link text-nav-link transition-colors";
  const activeCls = "text-primary-on-dark font-bold border-b-2 border-primary-on-dark pb-1";
  const idleCls = "text-white/80 hover:text-white";

  return (
    <header className="fixed top-0 w-full h-[44px] z-[100] bg-black flex justify-between items-center px-lg">
      <Link to="/" className="font-hero-display text-tagline text-white">
        EXPO HUB
      </Link>
      <nav className="hidden md:flex gap-xl h-full items-center">
        <Link className={`${linkBase} ${active === "events" ? activeCls : idleCls}`} to="/">
          전체 행사
        </Link>
        <Link className={`${linkBase} ${active === "recruiting" ? activeCls : idleCls}`} to="/event-recruiting">
          부스 모집 공고
        </Link>
        <Link className={`${linkBase} ${active === "mypage" ? activeCls : idleCls}`} to="/mypage">
          마이페이지
        </Link>
        <Link className={`${linkBase} ${active === "organizer" ? activeCls : idleCls}`} to="/organizer-admin">
          개최자센터
        </Link>
      </nav>
      <div className="flex items-center gap-sm">
        <button className="text-white/80 hover:text-white transition-colors" aria-label="검색">
          <Icon name="search" className="text-[20px]" />
        </button>
        <Link
          to="/login"
          className="bg-primary-container text-white px-md py-1.5 rounded-full text-nav-link font-nav-link active:scale-95 transition-transform"
        >
          로그인
        </Link>
      </div>
    </header>
  );
}
