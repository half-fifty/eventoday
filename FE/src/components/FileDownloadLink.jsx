import { fileDownloadUrl } from "../api/fileApi.js";
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
      const res = await fetch(href);
      if (!res.ok) throw new Error("다운로드 실패");
      const blob = await res.blob();
      const blobUrl = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = blobUrl;
      a.download = fileName;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(blobUrl);
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