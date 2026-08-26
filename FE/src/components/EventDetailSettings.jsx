import RichTextEditor from "./RichTextEditor.jsx";
import Icon from "./Icon.jsx";
import { EVENT_DETAIL_DISPLAY_TYPES } from "./EventDetailPresentation.jsx";

export default function EventDetailSettings({ form, onChange, disabled = false }) {
  return (
    <div className="space-y-lg">
      <div>
        <p className="text-caption text-primary mb-xs">DETAIL PAGE</p>
        <h2 className="font-display-md text-[22px]">상세 페이지 표현 방식</h2>
        <p className="mt-xs text-caption text-ink-muted">방문자가 상세정보 탭에서 처음 보게 될 콘텐츠를 선택합니다.</p>
      </div>
      <div className="grid gap-sm md:grid-cols-3">
        {EVENT_DETAIL_DISPLAY_TYPES.map((option) => {
          const selected = (form.detailDisplayType || "IMAGE_GALLERY") === option.value;
          return (
            <button key={option.value} type="button" disabled={disabled} onClick={() => onChange("detailDisplayType", option.value)} className={`rounded-xl border p-md text-left transition disabled:opacity-50 ${selected ? "border-primary bg-primary/5 ring-1 ring-primary" : "border-hairline bg-white hover:border-primary/50"}`}>
              <Icon name={option.icon} className={selected ? "text-primary" : "text-ink-muted"} />
              <strong className="mt-sm block text-sm">{option.label}</strong>
              <span className="mt-xs block text-caption leading-5 text-ink-muted">{option.description}</span>
            </button>
          );
        })}
      </div>
      <label className="block">
        행사 공식 홈페이지 URL {form.detailDisplayType === "EXTERNAL_SITE" ? "*" : "(선택)"}
        <input disabled={disabled} required={form.detailDisplayType === "EXTERNAL_SITE"} type="url" maxLength={1000} value={form.officialWebsiteUrl || ""} onChange={(event) => onChange("officialWebsiteUrl", event.target.value)} placeholder="https://행사공식사이트.com" className="mt-xs h-11 w-full rounded-lg border border-hairline bg-white px-md outline-none focus:border-primary disabled:bg-surface-container" />
        <span className="mt-xs block text-caption text-ink-muted">상세 페이지에서 공식 사이트 링크를 제공합니다. 공식 사이트 표시 방식에서는 사이트를 페이지 안에 먼저 보여주고, 해당 사이트가 iframe을 차단하면 포스터형 바로가기 화면을 제공합니다.</span>
      </label>
      {form.detailDisplayType === "RICH_TEXT" && (
        <div>
          <p className="mb-xs text-sm">상세 설명 *</p>
          <RichTextEditor value={form.description || ""} onChange={(value) => onChange("description", value)} disabled={disabled} minHeight={360} />
        </div>
      )}
      {form.detailDisplayType === "IMAGE_GALLERY" && (
        <p className="rounded-lg bg-surface-pearl p-md text-caption leading-6 text-ink-muted">아래에서 상세 이미지를 바로 추가하고 노출 순서를 정할 수 있습니다.</p>
      )}
    </div>
  );
}
