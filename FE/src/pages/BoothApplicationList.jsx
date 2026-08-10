import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import TopNav from "../components/TopNav.jsx";
import Footer from "../components/Footer.jsx";
import Icon from "../components/Icon.jsx";
import FileDownloadLink from "../components/FileDownloadLink.jsx";
import { ApiError } from "../api/apiClient.js";
import {
  listMyApplications,
  getApplication,
  cancelApplication,
  listApplicationFiles,
} from "../api/boothApplicationApi.js";
import { getPreviousDayStatistics } from "../api/statisticsApi.js";
import useAuth from "../hooks/useAuth.js";

// 신청 상태별 뱃지 라벨·스타일 (BoothApplicationStatus)
const STATUS_BADGE = {
  SUBMITTED: { label: "신청됨", cls: "bg-primary/10 text-primary-focus" },
  UNDER_REVIEW: { label: "검토 중", cls: "bg-status-pending/10 text-status-pending" },
  APPROVED: { label: "승인됨", cls: "bg-status-available/10 text-status-available" },
  REJECTED: { label: "반려됨", cls: "bg-status-visited/10 text-status-visited" },
  CANCELLED: { label: "취소됨", cls: "bg-surface-container text-ink-muted" },
};

const formatDate = (iso) => {
  if (!iso) return "-";
  const d = new Date(iso);
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`;
};

// 첨부파일 구분 라벨
const FILE_TYPE_LABEL = { ESTIMATE: "견적서", OTHER: "기타" };

export default function BoothApplicationList() {
  const { member } = useAuth();

  // 참가기업(EXHIBITOR) 조직 - 목록 조회에 organizationId 필요
  const exhibitorOrg =
    member?.organization?.organizationType === "EXHIBITOR" ? member.organization : null;

  const [applications, setApplications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // 상세 사이드 패널
  const [selectedId, setSelectedId] = useState(null);
  const [detail, setDetail] = useState(null);
  const [files, setFiles] = useState([]);
  const [detailLoading, setDetailLoading] = useState(false);

  // 취소 처리
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState("");

  // STAT-API-002: 승인된 신청의 전날 부스 통계
  const [prevStat, setPrevStat] = useState(null);
  const [prevStatError, setPrevStatError] = useState("");

  // APP-API-002 내 조직 신청 목록 로드
  useEffect(() => {
    if (!exhibitorOrg) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError("");
    listMyApplications(exhibitorOrg.organizationId)
      .then((data) => {
        if (!cancelled) setApplications(Array.isArray(data) ? data : []);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "신청 목록을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [exhibitorOrg?.organizationId]);

  // APP-API-003 상세 + APP-API-009 첨부파일 동시 로드
  const openDetail = (applicationId) => {
    setSelectedId(applicationId);
    setDetail(null);
    setFiles([]);
    setCancelError("");
    setDetailLoading(true);
    setPrevStat(null);
    setPrevStatError("");
    Promise.all([getApplication(applicationId), listApplicationFiles(applicationId)])
      .then(([app, fileList]) => {
        setDetail(app);
        setFiles(Array.isArray(fileList) ? fileList : []);
        // 승인된 신청이면 배정된 부스의 전날 통계 조회 (실패해도 상세 표시에 영향 없음)
        if (app?.status === "APPROVED" && app?.boothId) {
          getPreviousDayStatistics(app.boothId)
            .then((stat) => setPrevStat(stat))
            .catch((err) => setPrevStatError(err.message || "전날 통계를 불러오지 못했습니다."));
        }
      })
      .catch((err) => {
        setCancelError(err instanceof ApiError ? err.message : "상세 정보를 불러오지 못했습니다.");
      })
      .finally(() => setDetailLoading(false));
  };

  const closeDetail = () => {
    setSelectedId(null);
    setDetail(null);
    setFiles([]);
    setCancelError("");
    setPrevStat(null);
    setPrevStatError("");
  };

  // APP-API-004 신청 취소 (SUBMITTED 상태만 가능)
  const handleCancel = async () => {
    if (!selectedId || cancelling) return;
    if (!window.confirm("신청을 취소하시겠습니까? 취소 후에는 되돌릴 수 없습니다.")) return;
    setCancelling(true);
    setCancelError("");
    try {
      await cancelApplication(selectedId);
      setApplications((prev) =>
        prev.map((app) => (app.id === selectedId ? { ...app, status: "CANCELLED" } : app))
      );
      closeDetail();
    } catch (err) {
      setCancelError(err instanceof ApiError ? err.message : "취소에 실패했습니다.");
    } finally {
      setCancelling(false);
    }
  };

  const badge = (status) =>
    STATUS_BADGE[status] ?? { label: status, cls: "bg-surface-container text-ink-muted" };

  return (
    <div className="bg-surface text-on-surface min-h-screen">
      <TopNav />

      <main className="max-w-[900px] mx-auto pt-[80px] pb-section px-lg">
        <div className="mb-xl">
          <h1 className="font-display-lg text-[28px] font-semibold">내 부스 신청 현황</h1>
          <p className="text-caption text-ink-muted mt-xs">참가기업으로 신청한 부스 신청서 목록입니다.</p>
        </div>

        {/* 참가기업 조직 없음 */}
        {!loading && !exhibitorOrg && (
          <div className="bg-white border border-hairline rounded-xl p-xl text-center space-y-md">
            <Icon name="business" className="text-[40px] text-ink-muted" />
            <p className="text-body text-ink-muted">참가기업 계정으로 로그인해야 신청 현황을 볼 수 있습니다.</p>
            <Link to="/business/signup" className="inline-block px-lg py-sm bg-primary text-white rounded-full text-caption font-body-strong">
              기업 회원가입
            </Link>
          </div>
        )}

        {loading && <div className="text-center py-xl text-ink-muted text-caption">불러오는 중...</div>}

        {error && (
          <div className="bg-white border border-hairline rounded-xl p-lg text-caption text-error">{error}</div>
        )}

        {/* 신청 목록 */}
        {!loading && !error && exhibitorOrg && (
          applications.length === 0 ? (
            <div className="bg-white border border-hairline rounded-xl p-xl text-center space-y-md">
              <Icon name="inbox" className="text-[40px] text-ink-muted" />
              <p className="text-body text-ink-muted">신청 내역이 없습니다.</p>
              <Link to="/recruitments" className="inline-block px-lg py-sm bg-primary text-white rounded-full text-caption font-body-strong">
                모집 공고 보기
              </Link>
            </div>
          ) : (
            <div className="space-y-sm">
              {applications.map((app) => {
                const b = badge(app.status);
                return (
                  <button
                    key={app.id}
                    onClick={() => openDetail(app.id)}
                    className="w-full text-left bg-white border border-hairline rounded-xl p-lg hover:shadow-sm transition-shadow flex items-center gap-md"
                  >
                    <div className="w-10 h-10 rounded-lg bg-surface-container flex items-center justify-center flex-shrink-0">
                      <Icon name="description" className="text-[18px] text-ink-muted" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="font-body-strong text-[14px] truncate">{app.teamName}</p>
                      <p className="text-caption text-ink-muted">
                        신청번호 {app.applicationNo} · 제출일 {formatDate(app.submittedAt)}
                      </p>
                    </div>
                    <span className={`text-[11px] font-bold px-sm py-1 rounded-full whitespace-nowrap ${b.cls}`}>
                      {b.label}
                    </span>
                    <Icon name="chevron_right" className="text-ink-muted text-[18px]" />
                  </button>
                );
              })}
            </div>
          )
        )}
      </main>

      <Footer />

      {/* 상세 사이드 패널 */}
      {selectedId && (
        <>
          <div onClick={closeDetail} className="fixed inset-0 bg-black/40 z-40" />
          <aside className="fixed right-0 top-0 bottom-0 w-full max-w-[480px] bg-white z-50 flex flex-col shadow-2xl">
            <div className="flex items-center justify-between px-lg py-md border-b border-hairline">
              <h2 className="font-body-strong text-[16px]">신청 상세</h2>
              <button onClick={closeDetail} aria-label="닫기">
                <Icon name="close" className="text-ink-muted" />
              </button>
            </div>

            {detailLoading ? (
              <div className="flex-1 flex items-center justify-center text-caption text-ink-muted">불러오는 중...</div>
            ) : detail ? (
              <div className="flex-1 overflow-y-auto px-lg py-lg space-y-lg">
                <div className="flex items-center gap-sm">
                  <span className={`text-[12px] font-bold px-sm py-1 rounded-full ${badge(detail.status).cls}`}>
                    {badge(detail.status).label}
                  </span>
                  <span className="text-caption text-ink-muted">신청번호 {detail.applicationNo}</span>
                </div>

                {/* 기본 정보 */}
                <div className="bg-surface-pearl rounded-xl p-lg space-y-sm">
                  <DetailRow label="기업명" value={detail.teamName} />
                  <DetailRow label="담당자" value={detail.contactName} />
                  <DetailRow label="이메일" value={detail.contactEmail} />
                  <DetailRow label="연락처" value={detail.contactPhone} />
                  <DetailRow label="제출일" value={formatDate(detail.submittedAt)} />
                  {detail.expectedVisitors != null && (
                    <DetailRow label="예상 방문객" value={`${detail.expectedVisitors}명`} />
                  )}
                </div>

                <div className="space-y-sm">
                  <p className="text-caption font-body-strong">활동 소개</p>
                  <p className="text-body bg-surface-pearl rounded-lg p-md whitespace-pre-line">{detail.activityDescription}</p>
                </div>
                <div className="space-y-sm">
                  <p className="text-caption font-body-strong">전시 내용</p>
                  <p className="text-body bg-surface-pearl rounded-lg p-md whitespace-pre-line">{detail.exhibitionContent}</p>
                </div>
                {detail.applicationReason && (
                  <div className="space-y-sm">
                    <p className="text-caption font-body-strong">신청 사유</p>
                    <p className="text-body bg-surface-pearl rounded-lg p-md whitespace-pre-line">{detail.applicationReason}</p>
                  </div>
                )}

                {/* 설비 요청 */}
                <div className="bg-surface-pearl rounded-xl p-lg space-y-sm">
                  <p className="text-caption font-body-strong mb-sm">설비 요청 사항</p>
                  <DetailRow label="전기" value={detail.electricityRequired ? "필요" : "불필요"} />
                  <DetailRow label="급수" value={detail.waterRequired ? "필요" : "불필요"} />
                  <DetailRow label="배수" value={detail.drainageRequired ? "필요" : "불필요"} />
                  <DetailRow label="인터넷" value={detail.internetRequired ? "필요" : "불필요"} />
                </div>

                {/* 첨부파일 - Presigned downloadUrl 사용 */}
                {files.length > 0 && (
                  <div className="space-y-sm">
                    <p className="text-caption font-body-strong">첨부파일</p>
                    <div className="flex flex-col gap-xs items-start">
                      {files.map((file) => (
                        <FileDownloadLink
                          key={file.fileId}
                          downloadUrl={file.downloadUrl}
                          fileId={file.fileId}
                          fileName={`[${FILE_TYPE_LABEL[file.fileType] || file.fileType}] ${file.originalName}`}
                          fileSize={file.fileSize}
                        />
                      ))}
                    </div>
                  </div>
                )}

                {/* STAT-API-002: 승인된 신청 - 배정 부스의 전날 통계 */}
                {detail.status === "APPROVED" && (
                  <div className="space-y-sm">
                    <p className="text-caption font-body-strong">전날 부스 통계</p>
                    {prevStatError ? (
                      <p className="text-caption text-ink-muted bg-surface-pearl rounded-lg p-md">{prevStatError}</p>
                    ) : !prevStat ? (
                      <p className="text-caption text-ink-muted bg-surface-pearl rounded-lg p-md">통계를 불러오는 중...</p>
                    ) : (
                      <div className="bg-surface-pearl rounded-xl p-lg space-y-md">
                        <p className="text-caption text-ink-muted">{prevStat.statDate} 기준</p>
                        <div className="grid grid-cols-3 gap-sm text-center">
                          <div className="bg-white rounded-lg p-md">
                            <p className="text-caption text-ink-muted">예약</p>
                            <p className="font-display-md text-[20px]">{prevStat.totalReservationCount.toLocaleString()}</p>
                          </div>
                          <div className="bg-white rounded-lg p-md">
                            <p className="text-caption text-ink-muted">QR 방문</p>
                            <p className="font-display-md text-[20px]">{prevStat.totalQrScanCount.toLocaleString()}</p>
                          </div>
                          <div className="bg-white rounded-lg p-md">
                            <p className="text-caption text-ink-muted">노쇼</p>
                            <p className="font-display-md text-[20px]">{prevStat.totalNoShowCount.toLocaleString()}</p>
                          </div>
                        </div>
                        {/* 시간대별 상세: 집계가 있는 시간대만 표시 */}
                        {(prevStat.hourlyStats || []).length > 0 && (
                          <div className="space-y-xs">
                            <p className="text-caption font-body-strong">시간대별 상세</p>
                            {prevStat.hourlyStats.map((s) => (
                              <div key={s.statHour} className="flex items-center gap-sm text-caption">
                                <span className="w-[44px] text-ink-muted flex-shrink-0">{s.statHour}시</span>
                                <span className="text-on-surface">예약 {s.reservationCount} · 방문 {s.qrScanCount} · 노쇼 {s.noShowCount}</span>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                )}

                {cancelError && <p className="text-caption text-error">{cancelError}</p>}
              </div>
            ) : (
              cancelError && (
                <div className="flex-1 flex items-center justify-center text-caption text-error px-lg">{cancelError}</div>
              )
            )}

            {/* SUBMITTED 상태만 취소 가능 */}
            {detail?.status === "SUBMITTED" && (
              <div className="px-lg py-md border-t border-hairline">
                <button
                  onClick={handleCancel}
                  disabled={cancelling}
                  className="w-full h-[48px] rounded-xl border border-status-visited text-status-visited font-body-strong text-caption disabled:opacity-40"
                >
                  {cancelling ? "취소 중..." : "신청 취소"}
                </button>
              </div>
            )}
          </aside>
        </>
      )}
    </div>
  );
}

// 라벨-값 한 줄 표시 공통 컴포넌트
function DetailRow({ label, value }) {
  return (
    <div className="flex gap-sm">
      <span className="text-caption text-ink-muted w-[72px] flex-shrink-0">{label}</span>
      <span className="text-caption text-on-surface">{value}</span>
    </div>
  );
}
