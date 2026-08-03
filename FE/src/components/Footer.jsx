import { Link } from "react-router-dom";

// Full site footer used on the home page. Other pages use compact variants inline.
export default function Footer() {
  return (
    <footer className="w-full py-section bg-surface-container-low border-t border-hairline">
      <div className="grid grid-cols-2 md:grid-cols-4 gap-lg px-lg max-w-[1200px] mx-auto">
        <div className="col-span-2 md:col-span-1">
          <div className="font-hero-display text-tagline text-on-surface mb-md">EXPO HUB</div>
          <p className="text-caption text-on-surface-variant leading-relaxed">
            박람회·행사 예약 및 부스 운영 관리 플랫폼
          </p>
        </div>
        <div className="flex flex-col gap-sm">
          <h4 className="text-caption font-body-strong mb-xs">서비스</h4>
          <Link className="text-caption text-on-surface-variant hover:underline" to="/">
            전체 행사
          </Link>
          <Link className="text-caption text-on-surface-variant hover:underline" to="/recruitments">
            부스 모집 공고
          </Link>
          <Link className="text-caption text-on-surface-variant hover:underline" to="/mypage">
            마이페이지
          </Link>
        </div>
        <div className="flex flex-col gap-sm">
          <h4 className="text-caption font-body-strong mb-xs">이용 안내</h4>
          <a className="text-caption text-on-surface-variant hover:underline" href="#">이용약관</a>
          <a className="text-caption text-on-surface-variant hover:underline" href="#">개인정보처리방침</a>
          <a className="text-caption text-on-surface-variant hover:underline" href="#">환불 정책</a>
        </div>
        <div className="flex flex-col gap-sm">
          <h4 className="text-caption font-body-strong mb-xs">고객센터</h4>
          <p className="text-caption text-on-surface-variant">02-1234-5678</p>
          <p className="text-caption text-on-surface-variant">support@expohub.com</p>
        </div>
      </div>
      <div className="max-w-[1200px] mx-auto px-lg mt-xl pt-lg border-t border-divider-soft text-center">
        <p className="text-[12px] text-ink-muted">© 2026 EXPO HUB. All rights reserved.</p>
      </div>
    </footer>
  );
}
