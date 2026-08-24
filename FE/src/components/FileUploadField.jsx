import { useState } from "react";
import { uploadFile } from "../api/fileApi.js";
import Icon from "./Icon.jsx";

// validate: 업로드 전 검증 함수 (선택). 파일을 받아 에러 메시지(string) 또는 null 반환
export default function FileUploadField({ label, value, onChange, onFileSelected, accept = "image/*", required = false, validate, disabled = false }) {
  const [uploading, setUploading] = useState(false);
  const [fileName, setFileName] = useState("");
  const [error, setError] = useState("");
  const upload = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    // 업로드 전 검증 - accept 속성은 우회 가능하므로 JS에서 한 번 더 확인
    if (validate) {
      const validationError = validate(file);
      if (validationError) {
        setError(validationError);
        event.target.value = "";
        return;
      }
    }
    onFileSelected?.(file);
    setUploading(true); setError("");
    try {
      const result = await uploadFile(file, "PUBLIC");
      onChange(result.fileId);
      setFileName(result.originalName || file.name);
    } catch (requestError) {
      setError(requestError.message || "파일 업로드에 실패했습니다.");
      event.target.value = "";
    } finally { setUploading(false); }
  };
  return <div className="space-y-sm">
    <label className="font-body-strong">{label}{required ? " *" : ""}</label>
    <label className={`flex items-center gap-md border rounded-xl p-md cursor-pointer ${value ? "border-primary bg-primary/5" : "border-hairline"}`}>
      <Icon name={value ? "check_circle" : "cloud_upload"} className={value ? "text-primary" : "text-ink-muted"} />
      <span className="flex-1 text-sm">{uploading ? "업로드 중..." : fileName || (value ? "업로드 완료" : "이미지 파일을 선택하세요")}</span>
      <input type="file" accept={accept} className="hidden" disabled={uploading || disabled} onChange={upload} />
    </label>
    {error && <p className="text-caption text-error">{error}</p>}
  </div>;
}
