import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import Icon from "./Icon.jsx";
import NotificationBell from "./NotificationBell.jsx";
import useAuth from "../hooks/useAuth.js";

// Global top nav used on the main marketing/browse pages.
// `active` marks which primary link is highlighted (matches original per-page markup).
export default function TopNav({ active = "events" }) {
  const navigate = useNavigate();
  const { member, loading, isAuthenticated, logout } = useAuth();
  const [loggingOut, setLoggingOut] = useState(false);
  const linkBase = "font-nav-link text-nav-link transition-colors";
  const activeCls = "text-primary-on-dark font-bold border-b-2 border-primary-on-dark pb-1";
  const idleCls = "text-white/80 hover:text-white";

  const handleLogout = async () => {
    setLoggingOut(true);

    try {
      await logout();
      navigate("/");
    } catch (error) {
      window.alert("로그아웃에 실패했습니다. 다시 시도해 주세요.");
    } finally {
      setLoggingOut(false);
    }
  };

  return (
    <header className="fixed top-0 w-full h-[44px] z-[100] bg-black flex justify-between items-center px-lg">
      <Link to="/" className="font-hero-display text-tagline text-white">
        EvenToday
      </Link>
      <nav className="hidden md:flex gap-xl h-full items-center">
        <Link className={`${linkBase} ${active === "events" ? activeCls : idleCls}`} to="/">
          전체 행사
        </Link>
        <Link className={`${linkBase} ${active === "recruiting" ? activeCls : idleCls}`} to="/recruitments">
          부스 모집 공고
        </Link>
        <Link className={`${linkBase} ${active === "venues" ? activeCls : idleCls}`} to="/venues">
          전시장 안내
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
        {!loading && (
          isAuthenticated ? (
            <>
              <NotificationBell />
              <Link
                to="/mypage"
                className="max-w-[160px] truncate text-white text-nav-link font-nav-link hover:text-primary-on-dark transition-colors"
              >
                {member.nickname}님
              </Link>
              <button
                type="button"
                onClick={handleLogout}
                disabled={loggingOut}
                className="rounded-full border border-white/30 px-md py-1.5 text-white/80 text-nav-link font-nav-link transition-colors hover:border-white/60 hover:text-white disabled:cursor-not-allowed disabled:opacity-50"
              >
                {loggingOut ? "처리 중" : "로그아웃"}
              </button>
            </>
          ) : (
            <Link
              to="/login"
              className="bg-primary-container text-white px-md py-1.5 rounded-full text-nav-link font-nav-link active:scale-95 transition-transform"
            >
              로그인
            </Link>
          )
        )}
      </div>
    </header>
  );
}
