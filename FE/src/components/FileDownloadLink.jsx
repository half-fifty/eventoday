import { fileDownloadUrl } from "../api/fileApi.js";
import { apiBlobRequest } from "../api/apiClient.js";
import Icon from "./Icon.jsx";

const formatFileSize = (bytes) => {
  if (bytes == null) return "";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

/**
 * 공통 파일 다운로드 컴포넌트 (WBS-194)
 * - 보기: 새 탭에서 열기
 * - 다운로드: fetch → Blob 강제 다운로드 (S3 cross-origin 대응)
 */
export default function FileDownloadLink({ fileId, downloadUrl, fileName = "파일", fileSize, className = "" }) {
  const href = downloadUrl || (fileId ? fileDownloadUrl(fileId) : null);
  if (!href) return null;

  const handleDownload = async () => {
    try {
      let blob;
      if (!downloadUrl && fileId) {
        // 내부 API 다운로드: 인증 쿠키 포함 + 401 시 토큰 재발급 처리 (apiClient와 동일 정책)
        // API가 다른 오리진이면 일반 fetch는 쿠키를 보내지 않아 비공개 파일 다운로드가 실패한다
        blob = await apiBlobRequest(`/v1/files/${fileId}/download`);
      } else {
        // Presigned URL(S3 등 외부): 인증 쿠키를 붙이면 CORS 오류가 나므로 일반 fetch
        const res = await fetch(href);
        if (!res.ok) throw new Error("다운로드 실패");
        blob = await res.blob();
      }
      const blobUrl = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = blobUrl;
      a.download = fileName;
      document.body.appendChild(a);
      a.click();
      a.remove();
      // click 직후 동기 해제 시 브라우저에 따라 다운로드가 시작되기 전에
      // blob URL이 해제될 수 있으므로 다음 태스크로 미룬다
      setTimeout(() => URL.revokeObjectURL(blobUrl), 1000);
    } catch {
      // CORS 등 실패 시 새 탭 폴백
      window.open(href, "_blank", "noopener,noreferrer");
    }
  };

  return (
      <div className={`inline-flex items-center gap-xs border border-hairline rounded-xl px-md py-sm text-caption text-on-surface ${className}`}>
        <Icon name="attach_file" className="text-[16px] text-ink-muted flex-shrink-0" />
        <span className="truncate max-w-[180px]">{fileName}</span>
        {fileSize != null && (
            <span className="text-[11px] text-ink-muted flex-shrink-0">{formatFileSize(fileSize)}</span>
        )}
        {/* 보기 버튼 */}
        <a
          href={href}
          target="_blank"
          rel="noopener noreferrer"
          className="ml-xs text-ink-muted hover:text-primary transition-colors"
          title="새 탭에서 보기"
        >
          <Icon name="open_in_new" className="text-[16px]" />
        </a>
  {/* 다운로드 버튼 */}
  <button
      type="button"
      onClick={handleDownload}
      className="text-ink-muted hover:text-primary transition-colors"
      title="파일 다운로드"
  >
    <Icon name="download" className="text-[16px]" />
  </button>
</div>
);
}