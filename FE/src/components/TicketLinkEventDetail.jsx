import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { eventApi } from "../api/eventApi.js";
import { fileDownloadUrl } from "../api/fileApi.js";
import { getVenueGuide } from "../data/venueGuides.js";
import Icon from "./Icon.jsx";
import KakaoMapPreview from "./KakaoMapPreview.jsx";
import VenueMapPins from "./VenueMapPins.jsx";
import EventDetailPresentation from "./EventDetailPresentation.jsx";
import FileDownloadLink from "./FileDownloadLink.jsx";
import RichTextViewer from "./RichTextViewer.jsx";
import { formatEventDistance, hasEventCoordinates, rankNearbyEvents } from "../utils/eventRecommendations.js";

const formatDateTime = (value) =>
  value
    ? new Date(value).toLocaleString("ko-KR", {
        dateStyle: "long",
        timeStyle: "short",
      })
    : "미정";

const tabs = [
  ["detail", "상세정보"],
  ["notices", "공지사항"],
  ["booths", "참가 부스"],
  ["map", "배치도"],
  ["venue", "장소정보"],
  ["facilities", "편의시설"],
  ["parking", "주차정보"],
];

export default function TicketLinkEventDetail({
  event,
  detailImages,
  booths,
  loadingBooths,
  boothError,
  venueMaps,
  loadingVenueMaps,
  venueMapError,
  contents = [],
  onBoothClick,
  onPurchase,
  purchaseDisabled,
  purchaseLabel,
}) {
  const [activeTab, setActiveTab] = useState("detail");
  // 공지·자료: 제목을 누르면 본문이 펼쳐진다 (한 번에 하나만)
  const [expandedContentId, setExpandedContentId] = useState(null);
  const [recommendations, setRecommendations] = useState([]);
  const [recommendationsLoading, setRecommendationsLoading] = useState(true);
  const [recommendationsError, setRecommendationsError] = useState("");
  const venueGuide = getVenueGuide(event.venueName);
  const [parkingStatus, setParkingStatus] = useState(null);
  const [parkingStatusError, setParkingStatusError] = useState("");
  const [loadingParkingStatus, setLoadingParkingStatus] = useState(false);
  const [selectedParkingMapKey, setSelectedParkingMapKey] = useState("roof");

  useEffect(() => {
    let cancelled = false;
    setRecommendationsLoading(true);
    setRecommendationsError("");
    eventApi
      .list({ size: 100, sort: "startAt,asc" })
      .then((result) => {
        if (cancelled) return;
        const candidates = result?.data?.content || [];
        setRecommendations(
          rankNearbyEvents(event, candidates, 4),
        );
      })
      .catch(() => {
        if (!cancelled) {
          setRecommendations([]);
          setRecommendationsError("추천 행사를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
      })
      .finally(() => !cancelled && setRecommendationsLoading(false));
    return () => {
      cancelled = true;
    };
  }, [event.id, event.latitude, event.longitude]);

  useEffect(() => {
    if (activeTab !== "parking" || venueGuide?.id !== "coex") return;
    let cancelled = false;
    setLoadingParkingStatus(true);
    setParkingStatusError("");
    eventApi.coexParkingStatus()
      .then((result) => {
        if (!cancelled) setParkingStatus(result?.data || null);
      })
      .catch(() => {
        if (!cancelled) setParkingStatusError("주차 현황을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!cancelled) setLoadingParkingStatus(false);
      });
    return () => {
      cancelled = true;
    };
  }, [activeTab, venueGuide?.id]);

  const directionsUrl = event.latitude && event.longitude
    ? `https://map.kakao.com/link/to/${encodeURIComponent(event.venueName || event.name)},${event.latitude},${event.longitude}`
    : `https://map.kakao.com/link/search/${encodeURIComponent(event.address || event.venueName || event.name)}`;
  const parkingCounts = parkingStatus?.hourlyCounts || [];
  const parkingChartMax = Math.max(parkingStatus?.thresholds?.full || 2600, ...parkingCounts, 1);
  const selectedParkingMap = venueGuide?.parking?.maps?.find(
    (item) => item.key === selectedParkingMapKey,
  ) || venueGuide?.parking?.maps?.[0];
  const parkingMapGroups = [...new Set((venueGuide?.parking?.maps || []).map((item) => item.group))];

  return (
    <>
      <section className="border-b border-hairline bg-white">
        <div className="mx-auto grid max-w-[1080px] gap-xl px-lg py-xl md:grid-cols-[260px_1fr_240px]">
          <div className="overflow-hidden rounded-md border border-hairline bg-surface-container">
            {event.representativeFileId ? (
              <img
                src={fileDownloadUrl(event.representativeFileId)}
                alt={`${event.name} 포스터`}
                className="aspect-[3/4] h-full w-full object-cover"
              />
            ) : (
              <div className="grid aspect-[3/4] place-items-center text-ink-muted">
                <Icon name="image" className="text-[48px]" />
              </div>
            )}
          </div>
          <div className="min-w-0 py-xs">
            <p className="mb-sm text-caption font-bold text-primary">
              {event.eventType}
            </p>
            <h1 className="border-b border-hairline pb-lg font-display-lg text-[28px] leading-tight md:text-[34px]">
              {event.name}
            </h1>
            <dl className="mt-lg grid grid-cols-[88px_1fr] gap-x-md gap-y-md text-sm">
              <dt className="font-body-strong text-ink-muted">기간</dt>
              <dd>
                {formatDateTime(event.startAt)}
                <br />~ {formatDateTime(event.endAt)}
              </dd>
              <dt className="font-body-strong text-ink-muted">장소</dt>
              <dd>
                {event.venueName || "장소 미정"}
                <br />
                <span className="text-caption text-ink-muted">
                  {event.address}
                </span>
              </dd>
              <dt className="font-body-strong text-ink-muted">관람료</dt>
              <dd className="font-body-strong">
                {Number(event.ticketPrice) === 0
                  ? "무료"
                  : `${Number(event.ticketPrice).toLocaleString("ko-KR")}원`}
              </dd>
              <dt className="font-body-strong text-ink-muted">문의</dt>
              <dd>
                {event.contactPhone || event.contactEmail || "문의처 미정"}
              </dd>
            </dl>
          </div>
          <aside className="h-fit rounded-md border border-hairline bg-surface-pearl p-lg md:sticky md:top-[92px]">
            <p className="text-caption text-ink-muted">예매 정보</p>
            <p className="mt-xs font-display-md text-[22px]">
              {Number(event.ticketPrice) === 0
                ? "무료 입장"
                : `${Number(event.ticketPrice).toLocaleString("ko-KR")}원`}
            </p>
            <p className="mt-md text-caption leading-6 text-ink-muted">
              예매 전 행사 기간과 장소를 확인해 주세요.
            </p>
            <button
              disabled={purchaseDisabled}
              onClick={onPurchase}
              className="mt-lg w-full rounded-md bg-primary py-md font-body-strong text-white disabled:bg-surface-container-highest disabled:text-ink-muted"
            >
              {purchaseLabel}
            </button>
          </aside>
        </div>
      </section>

      <nav className="sticky top-[64px] z-20 border-y border-hairline bg-white">
        <div className="mx-auto grid max-w-[1080px] grid-cols-3 px-lg sm:grid-cols-7">
          {tabs.map(([key, label]) => (
            <button
              key={key}
              type="button"
              onClick={() => setActiveTab(key)}
              className={`border-b-2 py-md text-sm font-body-strong transition ${activeTab === key ? "border-primary text-primary" : "border-transparent text-ink-muted hover:text-on-surface"}`}
            >
              {label}
            </button>
          ))}
        </div>
      </nav>

      <section className="mx-auto min-h-[480px] max-w-[1080px] px-lg py-xl">
        {activeTab === "detail" && (
          <EventDetailPresentation event={event} detailImages={detailImages} />
        )}

        {activeTab === "notices" && (
          <div>
            <div className="mb-lg flex items-end justify-between">
              <h2 className="font-display-md text-[24px]">공지 · 자료</h2>
              <span className="text-caption text-ink-muted">{contents.length}개</span>
            </div>

            {!contents.length && (
              <p className="border-y border-hairline py-xl text-center text-ink-muted">
                등록된 공지사항이 없습니다.
              </p>
            )}

            <div className="divide-y divide-divider-soft overflow-hidden rounded-lg border border-hairline bg-white">
              {contents.map((content) => (
                <div key={content.contentId}>
                  <button
                    type="button"
                    onClick={() =>
                      setExpandedContentId(
                        expandedContentId === content.contentId ? null : content.contentId,
                      )
                    }
                    className="flex w-full items-center gap-sm p-lg text-left transition-colors hover:bg-surface-pearl"
                  >
                    <Icon
                      name={content.contentType === "NOTICE" ? "campaign" : "folder"}
                      className="flex-shrink-0 text-[18px] text-ink-muted"
                    />
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-body-strong text-[14px]">
                        {content.pinned && (
                          <Icon name="push_pin" className="mr-1 text-[13px] text-primary" />
                        )}
                        {content.title}
                      </p>
                      <p className="text-caption text-ink-muted">
                        {content.contentType === "NOTICE" ? "공지" : "자료"}
                        {content.version ? ` · v${content.version}` : ""}
                        {content.publishedAt
                          ? ` · ${new Date(content.publishedAt).toLocaleDateString("ko-KR")}`
                          : ""}
                      </p>
                    </div>
                    <Icon
                      name={expandedContentId === content.contentId ? "expand_less" : "expand_more"}
                      className="text-[18px] text-ink-muted"
                    />
                  </button>

                  {expandedContentId === content.contentId && (
                    <div className="space-y-sm px-lg pb-lg">
                      {content.content && (
                        <div className="rounded-lg bg-surface-pearl p-md text-caption">
                          <RichTextViewer html={content.content} />
                        </div>
                      )}
                      {/* downloadUrl: BE가 권한 검증 후 발급한 Presigned URL.
                          fileId만 넘기면 업로더가 아닌 사용자는 FILE_403_001이 난다 */}
                      {(content.downloadUrl || content.fileId) && (
                        <FileDownloadLink
                          fileId={content.fileId}
                          downloadUrl={content.downloadUrl}
                          fileName={content.fileName || "첨부파일"}
                          fileSize={content.fileSize}
                        />
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        {activeTab === "booths" && (
          <div>
            <div className="mb-lg flex items-end justify-between">
              <h2 className="font-display-md text-[24px]">참가 부스</h2>
              <span className="text-caption text-ink-muted">
                {booths.length}개
              </span>
            </div>
            {loadingBooths && (
              <p className="text-caption text-ink-muted">
                참가 부스를 불러오는 중입니다.
              </p>
            )}
            {boothError && (
              <p className="text-caption text-error">{boothError}</p>
            )}
            {!loadingBooths && !boothError && !booths.length && (
              <p className="border-y border-hairline py-xl text-center text-ink-muted">
                공개된 참가 부스가 없습니다.
              </p>
            )}
            <div className="grid gap-md sm:grid-cols-2 lg:grid-cols-3">
              {booths.map((booth) => (
                <button
                  key={booth.id}
                  type="button"
                  onClick={() => onBoothClick(booth)}
                  className="overflow-hidden rounded-lg border border-hairline bg-white text-left hover:border-primary"
                >
                  <div className="aspect-[16/9] bg-surface-container">
                    {booth.representativeFileId ? (
                      <img
                        src={fileDownloadUrl(booth.representativeFileId)}
                        alt=""
                        className="h-full w-full object-cover"
                      />
                    ) : (
                      <div className="grid h-full place-items-center">
                        <Icon name="storefront" />
                      </div>
                    )}
                  </div>
                  <div className="p-md">
                    <p className="text-caption font-bold text-primary">
                      {booth.boothCode}
                    </p>
                    <h3 className="mt-xs font-body-strong">
                      {booth.displayName || booth.boothCode}
                    </h3>
                    <p className="mt-sm line-clamp-2 text-caption text-ink-muted">
                      {booth.exhibitionContent ||
                        booth.shortIntro ||
                        booth.description ||
                        "상세정보 준비 중"}
                    </p>
                  </div>
                </button>
              ))}
            </div>
          </div>
        )}

        {activeTab === "map" && (
          <div>
            <h2 className="mb-lg font-display-md text-[24px]">행사장 배치도</h2>
            {loadingVenueMaps && (
              <p className="text-caption text-ink-muted">
                평면도를 불러오는 중입니다.
              </p>
            )}
            {venueMapError && (
              <p className="text-caption text-error">{venueMapError}</p>
            )}
            {!loadingVenueMaps && !venueMapError && !venueMaps.length && (
              <p className="border-y border-hairline py-xl text-center text-ink-muted">
                등록된 배치도가 없습니다.
              </p>
            )}
            <div className="space-y-xl">
              {venueMaps.map((venueMap) => (
                <div key={venueMap.id}>
                  <h3 className="mb-sm font-body-strong">
                    {venueMap.floorName}
                  </h3>
                  <div className="border border-hairline bg-white p-md">
                    <VenueMapPins
                      venueMap={venueMap}
                      onPinClick={onBoothClick}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {activeTab === "venue" && (
          <div className="space-y-xl">
            <div>
              <div className="mb-lg flex flex-wrap items-end justify-between gap-md">
                <div>
                  <p className="text-caption font-bold text-primary">VENUE</p>
                  <h2 className="mt-xs font-display-md text-[24px]">장소정보</h2>
                  <p className="mt-sm text-sm text-ink-muted">
                    {event.venueName || "장소 미정"} · {[event.address, event.addressDetail].filter(Boolean).join(" ")}
                  </p>
                </div>
                <a
                  href={directionsUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-xs rounded-md border border-hairline bg-white px-lg py-sm text-sm font-body-strong hover:border-primary hover:text-primary"
                >
                  <Icon name="directions" /> 카카오맵 길찾기
                </a>
              </div>
              <KakaoMapPreview
                latitude={event.latitude}
                longitude={event.longitude}
                venueName={event.venueName}
                heightClass="h-[420px] md:h-[560px]"
              />
            </div>

            <div>
              <div className="mb-md flex items-end justify-between">
                <div>
                  <p className="text-caption font-bold text-primary">YOU MAY ALSO LIKE</p>
                  <h2 className="mt-xs font-display-md text-[24px]">추천 전시·행사</h2>
                </div>
                <Link to="/events" className="text-caption font-body-strong text-primary">전체 보기 →</Link>
              </div>
              {recommendationsLoading ? (
                <p className="border-y border-hairline py-xl text-center text-ink-muted">가까운 공개 행사를 찾고 있습니다.</p>
              ) : recommendationsError ? (
                <p className="border-y border-error/20 bg-error/5 py-xl text-center text-error">{recommendationsError}</p>
              ) : recommendations.length ? (
                <>
                {!hasEventCoordinates(event) && (
                  <p className="mb-md rounded-lg bg-surface-pearl p-sm text-caption text-ink-muted">현재 행사의 좌표가 없어 추천 행사를 시작일 순으로 보여드립니다.</p>
                )}
                <div className="grid gap-md sm:grid-cols-2 lg:grid-cols-4">
                  {recommendations.map((recommendation) => (
                    <Link key={recommendation.id} to={`/events/${recommendation.id}`} className="group min-w-0">
                      <div className="aspect-[3/4] overflow-hidden rounded-md bg-surface-container">
                        {recommendation.representativeFileId ? (
                          <img src={fileDownloadUrl(recommendation.representativeFileId)} alt="" className="h-full w-full object-cover transition group-hover:scale-[1.03]" />
                        ) : (
                          <div className="grid h-full place-items-center"><Icon name="event" /></div>
                        )}
                      </div>
                      <p className="mt-sm flex items-center justify-between gap-sm text-caption text-primary">
                        <span>{recommendation.regionCode || "행사"}</span>
                        <span className="text-ink-muted">{formatEventDistance(recommendation.distance)}</span>
                      </p>
                      <h3 className="mt-xs truncate font-body-strong">{recommendation.name}</h3>
                      <p className="mt-xs text-caption text-ink-muted">{formatDateTime(recommendation.startAt)}</p>
                    </Link>
                  ))}
                </div>
                </>
              ) : (
                <p className="border-y border-hairline py-xl text-center text-ink-muted">추천할 공개 행사를 준비하고 있습니다.</p>
              )}
            </div>
          </div>
        )}

        {activeTab === "facilities" && (
          <div className="space-y-xl">
            <div className="flex flex-wrap items-end justify-between gap-md">
              <div>
                <p className="text-caption font-bold text-primary">VISITOR AMENITIES</p>
                <h2 className="mt-xs font-display-md text-[24px]">편의시설</h2>
                <p className="mt-sm text-sm text-ink-muted">
                  {event.venueName || "행사장"} 방문에 필요한 주요 시설을 확인하세요.
                </p>
              </div>
              {venueGuide?.officialUrl && (
                <a
                  href={venueGuide.facilitiesUrl || venueGuide.officialUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-xs rounded-md border border-hairline bg-white px-lg py-sm text-sm font-body-strong hover:border-primary hover:text-primary"
                >
                  <Icon name="open_in_new" /> 공식 시설 안내
                </a>
              )}
            </div>

            {venueGuide?.facilities?.length ? (
              <div className="space-y-lg">
              {(venueGuide.facilitiesImages || []).map((image) => (
                <figure key={image.imageUrl} className="overflow-hidden rounded-lg border border-hairline bg-white">
                  <a href={venueGuide.facilitiesUrl || image.imageUrl} target="_blank" rel="noreferrer" className="block bg-[#f7f7f7] p-md">
                    <img src={image.imageUrl} alt={image.label} className="mx-auto h-auto w-full max-w-[920px]" loading="lazy" />
                  </a>
                  <figcaption className="flex items-center justify-between gap-md border-t border-hairline px-lg py-sm text-caption text-ink-muted">
                    <span>{image.label} · 출처: 코엑스 공식 홈페이지</span>
                    <span className="inline-flex items-center gap-xs text-primary">자세히 보기 <Icon name="open_in_new" /></span>
                  </figcaption>
                </figure>
              ))}
              <div className="grid gap-md sm:grid-cols-2 lg:grid-cols-3">
                {venueGuide.facilities.map((facility) => (
                  <article key={facility.title} className="rounded-lg border border-hairline bg-white p-lg">
                    <div className="grid h-11 w-11 place-items-center rounded-full bg-primary-container/10 text-primary">
                      <Icon name={facility.icon} />
                    </div>
                    <h3 className="mt-md font-body-strong">{facility.title}</h3>
                    <p className="mt-xs text-sm leading-6">{facility.location}</p>
                    <p className="mt-xs text-caption leading-6 text-ink-muted">{facility.description}</p>
                  </article>
                ))}
              </div>
              </div>
            ) : (
              <div className="rounded-lg border border-hairline bg-surface-pearl p-xl text-center">
                <Icon name="apartment" className="text-[40px] text-ink-muted" />
                <h3 className="mt-md font-body-strong">공식 상세 편의시설 정보를 제공하지 않습니다.</h3>
                <p className="mt-xs text-sm leading-6 text-ink-muted">
                  {venueGuide?.facilitiesNotice || "공식 홈페이지에서 확인 가능한 상세 편의시설 정보가 없습니다. 방문 전 행사장에 문의해 주세요."}
                </p>
              </div>
            )}

            <div className="rounded-lg border border-primary/20 bg-primary-container/5 p-lg text-caption leading-6 text-ink-muted">
              <div className="flex gap-sm">
                <Icon name="info" className="mt-[2px] text-primary" />
                <p>
                  시설 위치와 운영시간은 현장 사정에 따라 달라질 수 있습니다.
                  {venueGuide?.verifiedAt && ` 안내 정보 확인일 ${venueGuide.verifiedAt}`}
                </p>
              </div>
            </div>
          </div>
        )}

        {activeTab === "parking" && (
          <div className="space-y-xl">
            <div className="flex flex-wrap items-end justify-between gap-md">
              <div>
                <p className="text-caption font-bold text-primary">PARKING GUIDE</p>
                <h2 className="mt-xs font-display-md text-[24px]">주차정보</h2>
                <p className="mt-sm text-sm text-ink-muted">
                  출발 전 요금과 혼잡도를 확인하면 더욱 편하게 방문할 수 있습니다.
                </p>
              </div>
              {venueGuide?.parkingUrl && (
                <a
                  href={venueGuide.parkingUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-xs rounded-md bg-primary px-lg py-sm text-sm font-body-strong text-white hover:bg-primary-focus"
                >
                  <Icon name="local_parking" /> 실시간 주차 현황
                </a>
              )}
            </div>

            {venueGuide?.parking ? (
              <>
                <section className="overflow-hidden rounded-xl bg-gradient-to-r from-[#102a43] to-[#0f6b78] p-xl text-white">
                  <div className="flex flex-col justify-between gap-lg sm:flex-row sm:items-end">
                    <div>
                      <p className="text-caption font-bold text-white/70">{venueGuide.name} PARKING</p>
                      <h3 className="mt-xs font-display-md text-[24px]">방문 전 주차 현황을 확인하세요</h3>
                      <p className="mt-md max-w-[680px] text-sm leading-7 text-white/80">{venueGuide.parking.liveNotice}</p>
                    </div>
                    <Icon name="directions_car" className="text-[56px] text-white/40" />
                  </div>
                </section>

                {venueGuide.id === "coex" && <section className="grid overflow-hidden rounded-lg border border-hairline bg-white lg:grid-cols-[1fr_240px]">
                  <div className="min-w-0 p-lg">
                    <div className="flex flex-wrap items-start justify-between gap-md">
                      <div>
                        <p className="text-caption font-bold text-primary">시간별 만차 예측</p>
                        <h3 className="mt-xs font-display-md text-[20px]">
                          {parkingStatus?.expectedFullHour !== null && parkingStatus?.expectedFullHour !== undefined
                            ? `예상 만차 시간 ${parkingStatus.expectedFullHour}시`
                            : "예상 만차 시간을 확인 중입니다."}
                        </h3>
                      </div>
                      {parkingStatus?.fetchedAt && (
                        <span className="text-caption text-ink-muted">
                          {new Date(parkingStatus.fetchedAt).toLocaleString("ko-KR")} 확인
                        </span>
                      )}
                    </div>

                    {loadingParkingStatus && (
                      <div className="mt-lg h-[240px] animate-pulse rounded-md bg-surface-container" />
                    )}
                    {!loadingParkingStatus && parkingCounts.length > 0 && (
                      <div className="mt-lg overflow-x-auto pb-sm">
                        <div className="relative flex h-[240px] min-w-[720px] items-end gap-[6px] border-b border-hairline px-sm pt-lg">
                          {["smooth", "congested", "full"].map((level) => {
                            const threshold = parkingStatus.thresholds?.[level];
                            const styles = {
                              smooth: "border-ink-muted/50",
                              congested: "border-[#ff8a00]",
                              full: "border-error",
                            };
                            return threshold ? (
                              <div
                                key={level}
                                className={`pointer-events-none absolute inset-x-0 border-t border-dashed ${styles[level]}`}
                                style={{ bottom: `${(threshold / parkingChartMax) * 100}%` }}
                              />
                            ) : null;
                          })}
                          {parkingCounts.map((count, hour) => (
                            <div key={hour} className="group relative flex h-full flex-1 items-end">
                              <div
                                className={`w-full rounded-t-sm transition group-hover:opacity-80 ${count >= (parkingStatus.thresholds?.full || Infinity) ? "bg-error" : count >= (parkingStatus.thresholds?.congested || Infinity) ? "bg-[#ff9f2f]" : "bg-[#c9dcff]"}`}
                                style={{ height: `${Math.max(2, (count / parkingChartMax) * 100)}%` }}
                                title={`${hour}시 · 예상 ${count.toLocaleString("ko-KR")}대`}
                              />
                              <span className="absolute -bottom-6 left-1/2 -translate-x-1/2 text-[10px] text-ink-muted">
                                {hour % 2 === 0 ? `${String(hour).padStart(2, "0")}시` : ""}
                              </span>
                            </div>
                          ))}
                        </div>
                        <div className="mt-8 flex flex-wrap gap-md text-caption text-ink-muted">
                          <span>■ 예상 주차 대수</span>
                          <span className="text-[#ff8a00]">--- 혼잡 기준</span>
                          <span className="text-error">— 만차 기준</span>
                        </div>
                      </div>
                    )}
                    {!loadingParkingStatus && (!parkingCounts.length || parkingStatusError) && (
                      <div className="mt-lg rounded-md bg-surface-pearl p-lg text-sm text-ink-muted">
                        {parkingStatusError || parkingStatus?.notice || "현재 예측 데이터를 확인할 수 없습니다."}
                      </div>
                    )}
                  </div>
                  <aside className="flex min-h-[240px] flex-col justify-between border-t border-hairline bg-surface-pearl p-lg lg:border-l lg:border-t-0">
                    <div>
                      <p className="text-caption font-bold text-ink-muted">현재 주차장 혼잡도</p>
                      <div className="mt-xl flex items-center gap-md">
                        <span className={`h-12 w-12 rounded-full border-[6px] ${parkingStatus?.status === "FULL" ? "border-error" : parkingStatus?.status === "CONGESTED" ? "border-[#ff8a00]" : "border-primary"}`} />
                        <div>
                          <p className="text-caption text-ink-muted">현재</p>
                          <p className="font-display-md text-[24px]">{parkingStatus?.statusLabel || "확인 중"}</p>
                        </div>
                      </div>
                    </div>
                    <p className="mt-lg text-caption leading-6 text-ink-muted">
                      {parkingStatus?.notice || "코엑스 공식 현황을 확인하고 있습니다."}
                    </p>
                  </aside>
                </section>}

                {venueGuide.parking.maps?.length > 0 && (
                  <section>
                    <h3 className="font-display-md text-[20px]">주차장 입구 안내</h3>
                    <p className="mt-xs text-caption text-ink-muted">층이나 목적지를 선택하면 알맞은 진입 안내를 확인할 수 있습니다.</p>
                    <div className="mt-md grid overflow-hidden rounded-lg border border-hairline bg-white md:grid-cols-[220px_1fr]">
                      <div className="border-b border-hairline p-md md:border-b-0 md:border-r">
                        {parkingMapGroups.map((group) => (
                          <div key={group} className="mb-md last:mb-0">
                            <p className="mb-xs px-sm text-caption font-bold text-ink-muted">{group}</p>
                            <div className="space-y-xs">
                              {venueGuide.parking.maps.filter((item) => item.group === group).map((item) => (
                                <button
                                  key={item.key}
                                  type="button"
                                  onClick={() => setSelectedParkingMapKey(item.key)}
                                  className={`flex w-full items-center justify-between rounded-md px-sm py-sm text-left text-sm transition ${selectedParkingMap?.key === item.key ? "bg-black font-body-strong text-white" : "hover:bg-surface-pearl"}`}
                                >
                                  {item.label}<Icon name="arrow_forward" className="text-[16px]" />
                                </button>
                              ))}
                            </div>
                          </div>
                        ))}
                      </div>
                      <div className="min-w-0 bg-[#f7f7f7] p-md">
                        {selectedParkingMap && (
                          <>
                            <div className="mb-sm flex items-center justify-between gap-md">
                              <p className="font-body-strong">{selectedParkingMap.label}</p>
                              <a href={selectedParkingMap.imageUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-xs text-caption text-primary">
                                원본 크게 보기 <Icon name="open_in_new" />
                              </a>
                            </div>
                            <div className="overflow-auto rounded-md border border-hairline bg-white">
                              <img src={selectedParkingMap.imageUrl} alt={`코엑스 ${selectedParkingMap.label} 주차장 입구 안내`} className="mx-auto h-auto min-w-[620px] max-w-none md:min-w-0 md:max-w-full" />
                            </div>
                          </>
                        )}
                      </div>
                    </div>
                    <p className="mt-sm text-caption text-ink-muted">안내 이미지 출처: 코엑스 공식 주차 안내</p>
                  </section>
                )}

                {(venueGuide.parking.rates || []).length > 0 && <section>
                  <h3 className="font-display-md text-[20px]">기본 주차요금</h3>
                  <p className="mt-xs text-caption text-ink-muted">{venueGuide.parking.summary}</p>
                  <div className="mt-md overflow-hidden rounded-lg border border-hairline bg-white">
                    {(venueGuide.parking.rates || []).map((rate, index) => (
                      <div key={rate.label} className={`grid gap-sm px-lg py-md sm:grid-cols-[180px_1fr_1fr] ${index ? "border-t border-hairline" : ""}`}>
                        <span className="font-body-strong">{rate.label}</span>
                        <span className="text-primary">{rate.value}</span>
                        <span className="text-caption text-ink-muted">{rate.note}</span>
                      </div>
                    ))}
                  </div>
                  {venueGuide.parkingFeeUrl && (
                    <a href={venueGuide.parkingFeeUrl} target="_blank" rel="noreferrer" className="mt-sm inline-flex items-center gap-xs text-caption font-body-strong text-primary">
                      최신 요금과 할인 조건 확인 <Icon name="arrow_outward" />
                    </a>
                  )}
                </section>}

                {(venueGuide.parking.destinations || []).length > 0 && <section>
                  <h3 className="font-display-md text-[20px]">목적지별 주차 안내</h3>
                  <div className="mt-md grid gap-md sm:grid-cols-2">
                    {(venueGuide.parking.destinations || []).map((destination) => (
                      <article key={`${destination.floor}-${destination.title}`} className="rounded-lg border border-hairline bg-white p-lg">
                        <span className="inline-flex rounded-full bg-surface-container px-sm py-xs text-caption font-bold text-primary">{destination.floor}</span>
                        <h4 className="mt-md font-body-strong">{destination.title}</h4>
                        <p className="mt-xs text-caption leading-6 text-ink-muted">{destination.description}</p>
                      </article>
                    ))}
                  </div>
                </section>}

                <section className="flex flex-wrap items-center justify-between gap-md rounded-lg border border-hairline bg-surface-pearl p-lg">
                  <div>
                    <p className="font-body-strong">주차 관련 문의</p>
                    <p className="mt-xs text-sm text-ink-muted">{venueGuide.parking.contact}</p>
                  </div>
                  <p className="text-caption text-ink-muted">정보 확인일 {venueGuide.verifiedAt}</p>
                </section>
              </>
            ) : (
              <div className="rounded-lg border border-hairline bg-surface-pearl p-xl text-center">
                <Icon name="local_parking" className="text-[40px] text-ink-muted" />
                <h3 className="mt-md font-body-strong">공식 상세 주차정보를 제공하지 않습니다.</h3>
                <p className="mt-xs text-sm leading-6 text-ink-muted">
                  {venueGuide?.parkingNotice || "공식 홈페이지에서 확인 가능한 상세 주차정보가 없습니다. 방문 전 행사장에 문의해 주세요."}
                </p>
                {(venueGuide?.parkingUrl || venueGuide?.officialUrl) && (
                  <a
                    href={venueGuide.parkingUrl || venueGuide.officialUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="mt-lg inline-flex items-center gap-xs rounded-md border border-hairline bg-white px-lg py-sm text-sm font-body-strong hover:border-primary hover:text-primary"
                  >
                    공식 안내 확인 <Icon name="open_in_new" />
                  </a>
                )}
              </div>
            )}
          </div>
        )}

      </section>
    </>
  );
}
