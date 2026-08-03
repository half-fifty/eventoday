import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import TopNav from "../components/TopNav.jsx";
import Footer from "../components/Footer.jsx";
import Icon from "../components/Icon.jsx";
import { ApiError } from "../api/apiClient.js";
import { getPublicRecruitment } from "../api/recruitmentApi.js";

const STATUS_BADGE = {
  OPEN: { label: "모집 중", cls: "bg-primary-container/10 text-primary-focus" },
  CLOSED: { label: "모집 마감", cls: "bg-surface-container-highest text-secondary" },
  COMPLETED: { label: "모집 완료", cls: "bg-status-visited/10 text-status-visited" },
};

const formatDateTime = (isoValue) => {
  if (!isoValue) return "-";
  const d = new Date(isoValue);
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`;
};

export default function RecruitmentDetail() {
  const { recruitmentId } = useParams();
  const [recruitment, setRecruitment] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;

    setLoading(true);
    setError("");
    setRecruitment(null);

    getPublicRecruitment(recruitmentId)
      .then((data) => {
        if (!cancelled) setRecruitment(data);
      })
      .catch((err) => {
        if (!cancelled) {
          if (err instanceof ApiError && err.status === 404) {
            setError("모집 공고를 찾을 수 없거나 아직 공개되지 않았습니다.");
          } else {
            setError(err instanceof ApiError ? err.message : "모집 공고를 불러오지 못했습니다.");
          }
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [recruitmentId]);

  const badge = recruitment ? STATUS_BADGE[recruitment.status] ?? { label: recruitment.status, cls: "bg-surface-container" } : null;

  return (
    <div className="bg-surface text-on-surface">
      <TopNav active="recruiting" />

      <main className="pt-[44px]">
        <div className="max-w-[900px] mx-auto px-lg py-xl">
          <Link to="/recruitments" className="text-caption text-primary font-body-strong inline-flex items-center gap-1 mb-lg">
            <Icon name="arrow_back" className="text-[16px]" /> 모집 공고 목록으로
          </Link>

          {loading && <p className="text-caption text-ink-muted">불러오는 중...</p>}

          {error && (
            <div className="bg-surface-pearl border border-hairline rounded-2xl p-xxl text-center text-ink-muted">
              <Icon name="error_outline" className="text-[32px] block mb-sm" />
              {error}
            </div>
          )}

          {recruitment && (
            <>
              <div className="rounded-3xl overflow-hidden mb-lg flex items-center justify-center text-white h-[160px]" style={{ background: "linear-gradient(135deg,#2b5876,#4e4376)" }}>
                <Icon name="campaign" className="text-[56px] opacity-90" />
              </div>

              <span className={`inline-block text-[12px] font-bold px-md py-1 rounded-full mb-sm ${badge.cls}`}>
                {badge.label}
              </span>
              <h1 className="font-display-lg text-[26px] mb-sm">{recruitment.title}</h1>
              <p className="text-caption text-ink-muted mb-lg">
                모집 기간 {formatDateTime(recruitment.recruitmentStartAt)} – {formatDateTime(recruitment.recruitmentEndAt)}
              </p>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-lg">
                <div className="md:col-span-2 space-y-lg">
                  <div className="border-t border-hairline pt-md">
                    <h4 className="font-body-strong text-body mb-sm">참가 대상</h4>
                    <p className="font-body text-caption text-secondary">{recruitment.participantTarget}</p>
                  </div>
                  {recruitment.qualification && (
                    <div className="border-t border-hairline pt-md">
                      <h4 className="font-body-strong text-body mb-sm">자격 요건</h4>
                      <p className="font-body text-caption text-secondary whitespace-pre-line">{recruitment.qualification}</p>
                    </div>
                  )}
                  {recruitment.selectionMethod && (
                    <div className="border-t border-hairline pt-md">
                      <h4 className="font-body-strong text-body mb-sm">선정 방식</h4>
                      <p className="font-body text-caption text-secondary whitespace-pre-line">{recruitment.selectionMethod}</p>
                    </div>
                  )}
                  {recruitment.notice && (
                    <div className="border-t border-hairline pt-md">
                      <h4 className="font-body-strong text-body mb-sm">안내사항</h4>
                      <p className="font-body text-caption text-secondary whitespace-pre-line">{recruitment.notice}</p>
                    </div>
                  )}
                </div>

                <div className="bg-white rounded-2xl border border-hairline shadow-sm p-lg space-y-md h-fit">
                  <h4 className="font-body-strong text-body">문의처</h4>
                  <p className="text-caption text-secondary">{recruitment.contactName}</p>
                  <p className="text-caption text-secondary">{recruitment.contactEmail}</p>
                  <p className="text-caption text-secondary">{recruitment.contactPhone}</p>
                  <button
                    disabled
                    title="부스 신청 기능은 준비 중입니다"
                    className="w-full h-[48px] bg-primary text-white rounded-xl font-body-strong opacity-40 cursor-not-allowed"
                  >
                    부스 신청하기 (준비 중)
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </main>

      <Footer />
    </div>
  );
}
