import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import Icon from "../components/Icon.jsx";
import NotificationBell from "../components/NotificationBell.jsx";
import { ApiError } from "../api/apiClient.js";
import { getGuideBoothDetail, addBoothInterest, removeBoothInterest } from "../api/boothApi.js";
import useAuth from "../hooks/useAuth.js";

export default function BoothDetail() {
  const [params] = useSearchParams();
  const eventId = params.get("eventId");
  const boothId = params.get("boothId");
  const { isAuthenticated } = useAuth();

  const [booth, setBooth] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [togglingInterest, setTogglingInterest] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    setBooth(null);

    if (!eventId || !boothId) {
      setError("잘못된 접근입니다. QR 코드를 다시 스캔해 주세요.");
      setLoading(false);
      return;
    }

    getGuideBoothDetail(eventId, boothId)
      .then((data) => { if (!cancelled) setBooth(data); })
      .catch((requestError) => {
        if (!cancelled) {
          setError(requestError instanceof ApiError && requestError.status === 404
            ? "부스 정보를 찾을 수 없습니다."
            : "부스 정보를 불러오지 못했습니다.");
        }
      })
      .finally(() => { if (!cancelled) setLoading(false); });

    return () => { cancelled = true; };
  }, [eventId, boothId]);

  const toggleInterest = async () => {
    if (!booth || togglingInterest) return;
    setTogglingInterest(true);
    try {
      if (booth.isInterested) {
        await removeBoothInterest(boothId);
      } else {
        await addBoothInterest(boothId);
      }
      setBooth((prev) => prev && { ...prev, isInterested: !prev.isInterested });
    } catch {
      // 조용히 무시: 관심 등록은 부가 기능이라 실패해도 상세 화면 표시에는 영향 없음
    } finally {
      setTogglingInterest(false);
    }
  };

  return (
    <div className="bg-surface-container-lowest text-on-surface min-h-screen">
      {/* Top Nav */}
      <header className="fixed top-0 w-full h-[44px] z-[100] bg-black flex justify-between items-center px-lg">
        <Link to="/" className="font-hero-display text-tagline text-white">EvenToday</Link>
        <div className="flex items-center gap-sm">
          <NotificationBell />
          <Link to={eventId ? `/event-ongoing?eventId=${eventId}` : "/"} className="text-white/80 hover:text-white text-nav-link font-nav-link flex items-center gap-1">
            <Icon name="arrow_back" className="text-[18px]" /> 행사로 돌아가기
          </Link>
        </div>
      </header>

      <main className="pt-[44px] pb-xxl">
        <div className="max-w-[1200px] mx-auto px-lg py-xl">
          {loading && <p className="py-xxl text-center text-ink-muted">부스 정보를 불러오는 중입니다.</p>}

          {!loading && error && (
            <div className="bg-white border border-hairline rounded-2xl p-xxl text-center text-ink-muted">
              <Icon name="error_outline" className="text-[32px] block mb-sm" />
              {error}
            </div>
          )}

          {!loading && !error && booth && (
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-xl items-start">
              {/* Left: Info */}
              <div className="lg:col-span-7 space-y-lg">
                <div className="rounded-3xl overflow-hidden aspect-[16/10] flex items-center justify-center text-white relative bg-gradient-to-br from-primary-focus to-secondary">
                  <Icon name="storefront" className="text-[72px] opacity-90" />
                  <span className="absolute top-lg left-lg px-md py-1.5 text-caption font-bold rounded-full bg-white/90 text-primary">
                    {booth.boothCode}
                  </span>
                </div>

                {booth.shortIntro && (
                  <p className="text-body-strong text-on-surface-variant">{booth.shortIntro}</p>
                )}

                {booth.description && (
                  <div className="border-t border-hairline pt-lg">
                    <h3 className="font-body-strong text-body-strong mb-sm">부스 소개</h3>
                    <p className="text-body text-on-surface-variant leading-relaxed whitespace-pre-line">{booth.description}</p>
                  </div>
                )}

                <div className="border-t border-hairline pt-lg">
                  <h3 className="font-body-strong text-body-strong mb-sm">평균 별점</h3>
                  {booth.averageRating != null ? (
                    <div className="flex items-center gap-sm">
                      <span className="font-display-md text-[26px]">{booth.averageRating.toFixed(1)}</span>
                      <div className="flex">
                        {[1, 2, 3, 4, 5].map((i) => (
                          <Icon key={i} name="star" fill={i <= Math.round(booth.averageRating)} className={`text-[18px] ${i <= Math.round(booth.averageRating) ? "text-amber-500" : "text-hairline"}`} />
                        ))}
                      </div>
                      <span className="text-caption text-ink-muted">방문객 후기 {booth.reviewCount ?? 0}건</span>
                    </div>
                  ) : (
                    <p className="text-caption text-ink-muted">아직 등록된 후기가 없습니다.</p>
                  )}
                </div>
              </div>

              {/* Right: Summary panel */}
              <div className="lg:col-span-5">
                <div className="sticky top-[80px] bg-white rounded-2xl border border-hairline shadow-lg overflow-hidden">
                  <div className="p-xl">
                    {booth.boothType && <p className="text-primary font-body-strong text-tagline mb-1">{booth.boothType}</p>}
                    <h1 className="font-display-lg text-display-lg mb-sm leading-tight">{booth.displayName || booth.boothCode}</h1>
                    {booth.location && (
                      <p className="flex items-center gap-1 text-caption text-ink-muted mb-lg">
                        <Icon name="location_on" className="text-[16px]" /> {booth.location}
                      </p>
                    )}

                    <div className={`rounded-xl p-md mb-lg flex items-center gap-sm ${booth.hasAvailableSlots ? "bg-status-available/10" : "bg-surface-container"}`}>
                      <Icon name={booth.hasAvailableSlots ? "event_available" : "event_busy"} className={booth.hasAvailableSlots ? "text-status-available" : "text-ink-muted"} />
                      <div>
                        <p className={`font-body-strong ${booth.hasAvailableSlots ? "text-status-available" : "text-ink-muted"}`}>
                          {booth.hasAvailableSlots ? "예약 가능한 시간이 있어요" : "현재 예약 가능한 시간이 없어요"}
                        </p>
                      </div>
                    </div>

                    <button
                      onClick={toggleInterest}
                      disabled={!isAuthenticated || togglingInterest}
                      title={!isAuthenticated ? "로그인 후 이용할 수 있어요" : undefined}
                      className="w-full h-[48px] rounded-xl font-body-strong border border-hairline flex items-center justify-center gap-xs disabled:opacity-40 disabled:cursor-not-allowed mb-sm"
                    >
                      {booth.isInterested ? (
                        <><Icon name="favorite" fill className="text-primary" /> 관심 등록됨</>
                      ) : (
                        <><Icon name="favorite_border" /> 관심 등록</>
                      )}
                    </button>

                    <button
                      disabled
                      title="부스 예약 기능은 준비 중입니다"
                      className="w-full h-[48px] bg-primary text-white rounded-xl font-body-strong opacity-40 cursor-not-allowed"
                    >
                      예약하기 (준비 중)
                    </button>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      </main>

      <footer className="w-full py-section bg-surface-container-low border-t border-hairline">
        <div className="max-w-[1200px] mx-auto px-lg text-center">
          <p className="text-[12px] text-ink-muted">© 2026 EvenToday. All rights reserved.</p>
        </div>
      </footer>
    </div>
  );
}
