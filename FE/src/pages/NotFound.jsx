import { Link } from "react-router-dom";
import TopNav from "../components/TopNav.jsx";
import Footer from "../components/Footer.jsx";

export default function NotFound() {
  return (
    <div className="bg-surface min-h-screen text-on-surface flex flex-col">
      <TopNav />
      <main className="flex-1 flex items-center justify-center px-lg pt-[76px]">
        <section className="text-center max-w-lg py-xxl">
          <p className="text-primary font-display-lg text-[72px] leading-none mb-md">404</p>
          <h1 className="font-display-md text-[28px] mb-sm">페이지를 찾을 수 없습니다</h1>
          <p className="text-ink-muted mb-xl">
            주소가 잘못되었거나 페이지가 이동되었을 수 있습니다.
          </p>
          <Link
            to="/"
            className="inline-flex px-xl py-sm bg-primary text-white rounded-full font-body-strong"
          >
            홈으로 돌아가기
          </Link>
        </section>
      </main>
      <Footer />
    </div>
  );
}
