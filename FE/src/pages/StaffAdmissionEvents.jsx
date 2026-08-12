import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { eventApi } from "../api/eventApi.js";
import Icon from "../components/Icon.jsx";
import TopNav from "../components/TopNav.jsx";

const roleLabel = {
  EVENT_MANAGER: "행사 관리자",
  CHECKIN_STAFF: "입장 스태프",
};

const formatDate = (value) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";
  return date.toLocaleDateString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
};

export default function StaffAdmissionEvents() {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    eventApi.admissionEvents()
      .then((result) => {
        if (!active) return;
        setEvents(result?.data || []);
      })
      .catch((requestError) => {
        if (!active) return;
        setEvents([]);
        setError(requestError.message || "담당 행사 목록을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="min-h-screen bg-surface-container-low text-on-surface">
      <TopNav active="mypage" />
      <main className="mx-auto max-w-[880px] px-lg pb-xxl pt-[96px]">
        <div className="mb-lg">
          <p className="text-caption text-primary">ADMISSION STAFF</p>
          <h1 className="font-display-lg text-[30px]">현장 입장 업무</h1>
          <p className="mt-xs text-caption text-ink-muted">
            담당자로 배정된 행사의 현장 입장 관리 화면으로 이동합니다.
          </p>
        </div>

        {loading && (
          <div className="rounded-2xl border border-hairline bg-white p-xl text-caption text-ink-muted">
            담당 행사 목록을 불러오는 중입니다.
          </div>
        )}

        {!loading && error && (
          <div className="rounded-2xl border border-error/30 bg-white p-xl text-caption text-error">
            {error}
          </div>
        )}

        {!loading && !error && events.length === 0 && (
          <div className="rounded-2xl border border-hairline bg-white p-xl text-center">
            <Icon name="event_busy" className="text-[36px] text-ink-muted" />
            <p className="mt-sm font-body-strong">담당 중인 현장 입장 업무가 없습니다.</p>
            <p className="mt-xs text-caption text-ink-muted">
              행사 담당자로 배정되면 이곳에서 입장 관리를 시작할 수 있습니다.
            </p>
          </div>
        )}

        {!loading && !error && events.length > 0 && (
          <div className="grid gap-md">
            {events.map((event) => (
              <article
                key={`${event.eventId}-${event.role}`}
                className="rounded-2xl border border-hairline bg-white p-lg shadow-sm"
              >
                <div className="flex flex-col gap-md sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <p className="truncate font-body-strong">{event.eventName}</p>
                    <p className="mt-xs text-caption text-ink-muted">
                      {formatDate(event.startAt)} ~ {formatDate(event.endAt)}
                    </p>
                    <span className="mt-sm inline-flex rounded-full bg-primary-container/10 px-sm py-1 text-[11px] font-bold text-primary-focus">
                      역할: {roleLabel[event.role] || event.role}
                    </span>
                  </div>
                  <Link
                    to={`/events/${event.eventId}/admission`}
                    className="inline-flex items-center justify-center gap-xs rounded-full bg-primary px-lg py-sm text-caption font-body-strong text-white transition hover:brightness-95"
                  >
                    <Icon name="qr_code_scanner" className="text-[18px]" />
                    입장 관리 시작
                  </Link>
                </div>
              </article>
            ))}
          </div>
        )}
      </main>
    </div>
  );
}
