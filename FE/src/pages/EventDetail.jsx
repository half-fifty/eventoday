import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import TopNav from "../components/TopNav.jsx";
import Footer from "../components/Footer.jsx";
import Icon from "../components/Icon.jsx";
import { eventApi } from "../api/eventApi.js";

const formatDateTime = (value) => value
  ? new Date(value).toLocaleString("ko-KR", { dateStyle: "long", timeStyle: "short" })
  : "미정";

export default function EventDetail() {
  const { eventId } = useParams();
  const [event, setEvent] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    setEvent(null);
    eventApi.detail(eventId)
      .then((result) => !cancelled && setEvent(result?.data || null))
      .catch((requestError) => !cancelled && setError(requestError.message || "행사를 불러오지 못했습니다."))
      .finally(() => !cancelled && setLoading(false));
    return () => { cancelled = true; };
  }, [eventId]);

  return (
    <div className="bg-surface min-h-screen text-on-surface">
      <TopNav active="events" />
      <main className="pt-[76px] max-w-[1000px] mx-auto px-lg pb-xxl">
        {loading && <p className="py-xxl text-center text-ink-muted">행사 정보를 불러오는 중입니다.</p>}
        {error && (
          <div className="bg-error/10 border border-error/20 text-error rounded-xl p-lg">
            {error}<Link to="/" className="underline ml-sm">행사 목록으로</Link>
          </div>
        )}
        {event && (
          <>
            <section className="rounded-2xl p-xl md:p-xxl text-white bg-gradient-to-br from-primary-focus to-secondary mb-xl">
              <p className="text-caption text-white/70 mb-sm">{event.eventType}</p>
              <h1 className="font-display-lg text-[32px] md:text-[42px] mb-sm">{event.name}</h1>
              <p className="text-white/80">{event.shortDescription}</p>
            </section>
            <div className="grid md:grid-cols-[1fr_320px] gap-xl">
              <section className="space-y-xl">
                <div><h2 className="font-display-md text-[22px] mb-md">행사 소개</h2><p className="whitespace-pre-wrap leading-7">{event.description}</p></div>
                <div><h2 className="font-display-md text-[22px] mb-md">운영 기능</h2>
                  <div className="flex flex-wrap gap-sm">
                    {event.boothRecruitmentEnabled && <span className="px-md py-xs bg-primary/10 text-primary rounded-full text-caption">부스 모집</span>}
                    {event.venueMapEnabled && <span className="px-md py-xs bg-primary/10 text-primary rounded-full text-caption">평면도</span>}
                    {event.boothReservationEnabled && <span className="px-md py-xs bg-primary/10 text-primary rounded-full text-caption">부스 예약</span>}
                  </div>
                </div>
              </section>
              <aside className="bg-white border border-hairline rounded-2xl p-lg h-fit space-y-md">
                <p className="flex gap-sm"><Icon name="calendar_month" /><span>{formatDateTime(event.startAt)}<br/>~ {formatDateTime(event.endAt)}</span></p>
                <p className="flex gap-sm"><Icon name="location_on" /><span>{event.venueName}<br/><span className="text-caption text-ink-muted">{event.address}</span></span></p>
                <div className="border-t border-hairline pt-md">
                  <p className="text-caption text-ink-muted">입장 가격</p>
                  <p className="font-display-md text-[22px]">{Number(event.ticketPrice) === 0 ? "무료" : `${Number(event.ticketPrice).toLocaleString("ko-KR")}원`}</p>
                </div>
                <button className="w-full py-sm bg-primary text-white rounded-full font-body-strong">티켓 구매하기</button>
              </aside>
            </div>
          </>
        )}
      </main>
      <Footer />
    </div>
  );
}
