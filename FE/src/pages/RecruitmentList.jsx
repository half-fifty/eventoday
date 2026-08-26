import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import TopNav from "../components/TopNav.jsx";
import Footer from "../components/Footer.jsx";
import Icon from "../components/Icon.jsx";
import { ApiError } from "../api/apiClient.js";
import { listPublicRecruitments } from "../api/recruitmentApi.js";

const STATUS_TABS = [
  { value: "", label: "전체" },
  { value: "OPEN", label: "모집 중" },
  { value: "CLOSED", label: "모집 마감" },
  { value: "COMPLETED", label: "모집 완료" },
];

const STATUS_BADGE = {
  OPEN: { label: "모집 중", cls: "bg-primary-container/10 text-primary-focus" },
  CLOSED: { label: "모집 마감", cls: "bg-surface-container-highest text-secondary" },
  COMPLETED: { label: "모집 완료", cls: "bg-status-visited/10 text-status-visited" },
};

const formatDate = (isoValue) => {
  if (!isoValue) return "";
  const d = new Date(isoValue);
  return `${d.getMonth() + 1}.${d.getDate()}`;
};

export default function RecruitmentList() {
  const [status, setStatus] = useState("");
  const [recruitments, setRecruitments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;

    setLoading(true);
    setError("");

    listPublicRecruitments(status || undefined)
      .then((data) => {
        if (!cancelled) setRecruitments(Array.isArray(data) ? data : []);
      })
      .catch((err) => {
        if (!cancelled) {
          setRecruitments([]);
          setError(err instanceof ApiError ? err.message : "모집 공고 목록을 불러오지 못했습니다.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [status]);

  return (
    <div className="bg-surface text-on-surface">
      <TopNav active="recruiting" />

      <main className="pt-[44px]">
        <section className="max-w-[1200px] mx-auto px-lg py-xl">
          <h1 className="font-display-lg text-[28px] mb-xs">부스 모집 공고</h1>
          <p className="text-lead text-on-surface-variant mb-lg">참가를 원하는 행사의 부스 모집 공고를 확인하세요.</p>

          <div className="flex gap-sm mb-lg">
            {STATUS_TABS.map((tab) => (
              <button
                key={tab.value}
                onClick={() => setStatus(tab.value)}
                className={`px-lg py-sm rounded-full text-caption font-body-strong transition-colors ${
                  status === tab.value
                    ? "bg-primary text-white"
                    : "border border-hairline text-on-surface-variant hover:bg-surface-container"
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {loading && <p className="text-caption text-ink-muted">불러오는 중...</p>}
          {error && <p className="text-caption text-error">{error}</p>}

          {!loading && !error && recruitments.length === 0 && (
            <div className="bg-surface-pearl border border-hairline rounded-2xl p-xxl text-center text-ink-muted">
              <Icon name="campaign" className="text-[32px] block mb-sm" />
              해당 조건의 모집 공고가 없습니다.
            </div>
          )}

          {!loading && recruitments.length > 0 && (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-lg">
              {recruitments.map((r) => {
                const badge = STATUS_BADGE[r.status] ?? { label: r.status, cls: "bg-surface-container" };
                return (
                  <Link
                    key={r.id}
                    to={`/recruitments/${r.id}`}
                    className="bg-white border border-hairline rounded-2xl p-lg hover:shadow-lg transition-all-custom"
                  >
                    <span className={`inline-block text-[11px] font-bold px-sm py-1 rounded-full mb-sm ${badge.cls}`}>
                      {badge.label}
                    </span>
                    <h3 className="font-body-strong text-body-strong mb-1">{r.title}</h3>
                    <p className="text-caption text-ink-muted mb-1">{r.participantTarget}</p>
                    <p className="text-caption text-ink-muted">
                      모집 {formatDate(r.recruitmentStartAt)} – {formatDate(r.recruitmentEndAt)}
                    </p>
                  </Link>
                );
              })}
            </div>
          )}
        </section>
      </main>

      <Footer />
    </div>
  );
}
