import { Link } from "react-router-dom";
import Footer from "../components/Footer.jsx";
import TopNav from "../components/TopNav.jsx";

export default function RefundPolicy() {
  return (
    <div className="min-h-screen bg-surface-pearl text-on-surface">
      <TopNav />
      <main className="mx-auto max-w-[960px] px-lg py-section">
        <p className="text-caption font-bold text-primary">REFUND POLICY</p>
        <h1 className="mt-xs font-display-lg">취소 및 환불 정책</h1>
        <p className="mt-md text-sm leading-7 text-ink-muted">EvenToday에서 예매한 티켓에 공통으로 적용되는 기준입니다. 행사별 별도 조건이 표시된 경우 해당 조건이 우선 적용됩니다.</p>

        <div className="mt-xl space-y-lg">
          <section className="rounded-lg border border-hairline bg-white p-xl">
            <h2 className="font-display-md text-[20px]">기본 환불 기준</h2>
            <div className="mt-md overflow-hidden rounded-md border border-hairline text-sm">
              <div className="grid grid-cols-[1fr_160px] bg-surface-container px-md py-sm font-body-strong"><span>취소 시점</span><span>환불 기준</span></div>
              <div className="grid grid-cols-[1fr_160px] border-t border-hairline px-md py-md"><span>행사 시작 전이며 입장 처리 전</span><span className="font-body-strong text-primary">전액 환불</span></div>
              <div className="grid grid-cols-[1fr_160px] border-t border-hairline px-md py-md"><span>행사 시작 후 또는 입장 처리 후</span><span className="font-body-strong text-error">환불 불가</span></div>
            </div>
          </section>
          <section className="rounded-lg border border-hairline bg-white p-xl">
            <h2 className="font-display-md text-[20px]">환불 처리 안내</h2>
            <ul className="mt-md list-disc space-y-sm pl-lg text-sm leading-7 text-ink-muted">
              <li>결제수단과 카드사 사정에 따라 실제 환불 완료까지 영업일 기준 시간이 추가될 수 있습니다.</li>
              <li>행사 취소 또는 일정 변경이 발생한 경우 주최자의 별도 공지가 우선 적용됩니다.</li>
              <li>환불 진행 상태는 마이페이지의 주문 상세에서 확인할 수 있습니다.</li>
            </ul>
          </section>
        </div>
        <Link to="/" className="mt-xl inline-flex rounded-md bg-primary px-lg py-sm text-sm font-body-strong text-white">행사 목록으로 돌아가기</Link>
      </main>
      <Footer />
    </div>
  );
}
