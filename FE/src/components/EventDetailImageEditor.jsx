import { useEffect, useState } from "react";
import { eventApi } from "../api/eventApi.js";
import { fileDownloadUrl } from "../api/fileApi.js";
import FileUploadField from "./FileUploadField.jsx";
import Icon from "./Icon.jsx";

const MAX_IMAGES = 20;

export default function EventDetailImageEditor({
  organizationId,
  eventId,
  disabled = false,
}) {
  const [images, setImages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!organizationId || !eventId) return;
    let cancelled = false;
    setLoading(true);
    eventApi
      .managedDetailImages(organizationId, eventId)
      .then(
        (result) =>
          !cancelled &&
          setImages(
            (result?.data || []).map((image) => ({
              fileId: image.fileId,
              altText: image.altText || "",
            })),
          ),
      )
      .catch(
        (requestError) =>
          !cancelled &&
          setError(
            requestError.message || "상세 이미지를 불러오지 못했습니다.",
          ),
      )
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [organizationId, eventId]);

  const editingDisabled = disabled || saving;
  const update = (index, patch) => {
    if (editingDisabled) return;
    setImages((previous) =>
      previous.map((image, itemIndex) =>
        itemIndex === index ? { ...image, ...patch } : image,
      ),
    );
  };
  const move = (index, direction) => {
    if (editingDisabled) return;
    setImages((previous) => {
      const target = index + direction;
      if (target < 0 || target >= previous.length) return previous;
      const next = [...previous];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  };
  const remove = (index) => {
    if (editingDisabled) return;
    setImages((previous) =>
      previous.filter((_, itemIndex) => itemIndex !== index),
    );
  };
  const save = async () => {
    if (images.some((image) => !image.fileId)) {
      setError("추가한 이미지 파일을 모두 업로드해 주세요.");
      return;
    }
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const result = await eventApi.replaceDetailImages(
        organizationId,
        eventId,
        images,
      );
      setImages(
        (result?.data || []).map((image) => ({
          fileId: image.fileId,
          altText: image.altText || "",
        })),
      );
      setMessage("상세 이미지가 저장되었습니다.");
    } catch (requestError) {
      setError(requestError.message || "상세 이미지를 저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  if (loading)
    return (
      <p className="text-caption text-ink-muted">
        상세 이미지를 불러오는 중입니다.
      </p>
    );
  return (
    <div className="space-y-md">
      <div className="rounded-xl border border-primary/15 bg-primary/5 p-md text-caption leading-6 text-on-surface-variant">
        <strong className="block text-primary">상세 이미지 권장 사양</strong>
        가로 750~1200px의 세로형 JPG·PNG·WEBP를 권장합니다. 등록한 순서대로 여백
        없이 이어서 노출됩니다. 최대 {MAX_IMAGES}장까지 등록할 수 있습니다.
      </div>
      {images.map((image, index) => (
        <article
          key={`${image.fileId || "new"}-${index}`}
          className="rounded-2xl border border-hairline bg-surface-pearl p-md"
        >
          <div className="mb-md flex items-center justify-between gap-sm">
            <strong className="text-sm">상세 이미지 {index + 1}</strong>
            {!disabled && (
              <div className="flex gap-xs">
                <button
                  type="button"
                  aria-label="위로 이동"
                  disabled={editingDisabled || index === 0}
                  onClick={() => move(index, -1)}
                  className="rounded-lg border border-hairline bg-white p-xs disabled:opacity-30"
                >
                  <Icon name="arrow_upward" />
                </button>
                <button
                  type="button"
                  aria-label="아래로 이동"
                  disabled={editingDisabled || index === images.length - 1}
                  onClick={() => move(index, 1)}
                  className="rounded-lg border border-hairline bg-white p-xs disabled:opacity-30"
                >
                  <Icon name="arrow_downward" />
                </button>
                <button
                  type="button"
                  aria-label="이미지 삭제"
                  disabled={editingDisabled}
                  onClick={() => remove(index)}
                  className="rounded-lg border border-error/20 bg-white p-xs text-error"
                >
                  <Icon name="close" />
                </button>
              </div>
            )}
          </div>
          {image.fileId && (
            <img
              src={fileDownloadUrl(image.fileId)}
              alt={image.altText || `상세 이미지 ${index + 1} 미리보기`}
              className="mx-auto mb-md max-h-[420px] max-w-full object-contain"
            />
          )}
          {!disabled && (
            <FileUploadField
              label={image.fileId ? "이미지 교체" : "이미지 업로드"}
              value={image.fileId}
              disabled={editingDisabled}
              onChange={(fileId) => update(index, { fileId })}
            />
          )}
          <label className="mt-md block text-caption">
            대체텍스트
            <input
              disabled={editingDisabled}
              maxLength={300}
              value={image.altText}
              onChange={(event) =>
                update(index, { altText: event.target.value })
              }
              placeholder="이미지 내용을 짧게 설명해 주세요"
              className="mt-xs h-10 w-full rounded-lg border border-hairline bg-white px-md disabled:bg-surface-container"
            />
          </label>
        </article>
      ))}
      {!images.length && (
        <div className="rounded-2xl border border-dashed border-hairline p-xl text-center text-caption text-ink-muted">
          등록된 상세 이미지가 없습니다.
        </div>
      )}
      {!disabled && (
        <div className="flex flex-wrap items-center justify-between gap-sm">
          <button
            type="button"
            disabled={editingDisabled || images.length >= MAX_IMAGES}
            onClick={() =>
              setImages((previous) => [
                ...previous,
                { fileId: null, altText: "" },
              ])
            }
            className="inline-flex items-center gap-xs rounded-full border border-hairline bg-white px-lg py-sm text-caption font-body-strong disabled:opacity-40"
          >
            <Icon name="add_photo_alternate" />
            이미지 추가
          </button>
          <button
            type="button"
            disabled={saving}
            onClick={save}
            className="rounded-full bg-primary px-xl py-sm text-caption font-body-strong text-white disabled:opacity-50"
          >
            {saving ? "저장 중..." : "상세 이미지 저장"}
          </button>
        </div>
      )}
      {message && (
        <p className="text-caption text-status-available">{message}</p>
      )}
      {error && (
        <p role="alert" className="text-caption text-error">
          {error}
        </p>
      )}
    </div>
  );
}
