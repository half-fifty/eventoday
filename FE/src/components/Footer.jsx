import { Link } from "react-router-dom";

// Full site footer used on the home page. Other pages use compact variants inline.
export default function Footer() {
  return (
    <footer className="w-full py-section bg-surface-container-low border-t border-hairline">
      <div className="grid gap-lg px-lg max-w-[1200px] mx-auto sm:grid-cols-2 md:grid-cols-3">
        <div>
          <div className="font-hero-display text-tagline text-on-surface mb-md">EvenToday</div>
          <p className="text-caption text-on-surface-variant leading-relaxed">
            박람회·행사 예약 및 부스 운영 관리 플랫폼
          </p>
        </div>
        <div className="flex flex-col gap-sm">
          <h4 className="text-caption font-body-strong mb-xs">이용 안내</h4>
          <Link className="text-caption text-on-surface-variant hover:underline" to="/notices/12">이용약관</Link>
          <Link className="text-caption text-on-surface-variant hover:underline" to="/notices/11">개인정보처리방침</Link>
          <Link className="text-caption text-on-surface-variant hover:underline" to="/refund-policy">환불 정책</Link>
        </div>
        <div className="flex flex-col gap-sm">
          <h4 className="text-caption font-body-strong mb-xs">고객센터</h4>
          <p className="text-caption text-on-surface-variant">02-1234-5678</p>
          <p className="text-caption text-on-surface-variant">support@eventoday.com</p>
        </div>
      </div>
      <div className="max-w-[1200px] mx-auto px-lg mt-xl pt-lg border-t border-divider-soft text-center">
        <p className="text-[12px] text-ink-muted">© 2026 EvenToday. All rights reserved.</p>
      </div>
    </footer>
  );
}
