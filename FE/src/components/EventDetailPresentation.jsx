import { fileDownloadUrl } from "../api/fileApi.js";
import Icon from "./Icon.jsx";
import RichTextViewer from "./RichTextViewer.jsx";
import { useState } from "react";

export const EVENT_DETAIL_DISPLAY_TYPES = [
  { value: "IMAGE_GALLERY", label: "상세 이미지", description: "세로형 이미지를 순서대로 보여줍니다.", icon: "image" },
  { value: "RICH_TEXT", label: "텍스트 + 이미지", description: "에디터로 글, 이미지, 표와 링크를 구성합니다.", icon: "edit_note" },
  { value: "EXTERNAL_SITE", label: "공식 사이트", description: "행사 공식 홈페이지를 페이지 안에서 보여줍니다.", icon: "language" },
];

export default function EventDetailPresentation({ event, detailImages = [], preview = false }) {
  const type = event?.detailDisplayType || "IMAGE_GALLERY";
  const [expanded, setExpanded] = useState(false);

  if (type === "EXTERNAL_SITE") {
    if (!event?.officialWebsiteUrl) return <Empty icon="link_off" text="등록된 행사 공식 사이트 주소가 없습니다." />;
    return (
      <a href={event.officialWebsiteUrl} target="_blank" rel="noopener noreferrer" className="group mx-auto block max-w-[860px] overflow-hidden rounded-xl border border-hairline bg-[#343944] shadow-sm transition hover:-translate-y-0.5 hover:shadow-lg">
        <div className={`${preview ? "min-h-[230px]" : "min-h-[320px]"} relative grid place-items-center overflow-hidden px-xl py-xxl text-center text-white`}>
          {event.representativeFileId && <img src={fileDownloadUrl(event.representativeFileId)} alt="" className="absolute inset-0 h-full w-full object-cover opacity-20 blur-[2px] transition group-hover:scale-105" />}
          <div className="absolute inset-0 bg-gradient-to-r from-[#303540]/95 to-[#4b5260]/85" />
          <div className="relative">
            <Icon name="open_in_new" className="text-[48px]" />
            <h2 className="mt-md font-display-md text-[26px]">행사 공식 사이트 바로가기</h2>
            <p className="mt-sm text-sm text-white/75">{event.name}</p>
            <span className="mt-lg inline-flex items-center gap-xs rounded-full bg-white px-lg py-sm font-body-strong text-[#303540]">사이트 방문하기 <Icon name="arrow_forward" /></span>
          </div>
        </div>
      </a>
    );
  }

  if (type === "RICH_TEXT") {
    return event?.description
      ? <CollapsibleContent expanded={expanded} onToggle={() => setExpanded((value) => !value)}><RichTextViewer html={event.description} /></CollapsibleContent>
      : <Empty icon="edit_note" text="등록된 상세 설명이 없습니다." />;
  }

  if (detailImages.length > 0) {
    return (
      <div className="mx-auto max-w-[860px] overflow-hidden bg-white">
        {detailImages.map((image, index) => (
          <DetailImage key={image.id || `${image.fileId}-${index}`} image={image} eventName={event.name} index={index} />
        ))}
      </div>
    );
  }

  if (event?.description) {
    return (
      <div className="mx-auto max-w-[860px] rounded-xl border border-hairline bg-white px-lg py-xl">
        <div className="mb-lg flex items-start gap-sm rounded-lg bg-primary/5 p-md text-caption text-on-surface-variant">
          <Icon name="info" className="text-primary" />
          <p>등록된 상세 이미지가 없어 행사 소개를 대신 보여드립니다.</p>
        </div>
        <CollapsibleContent expanded={expanded} onToggle={() => setExpanded((value) => !value)}><RichTextViewer html={event.description} /></CollapsibleContent>
      </div>
    );
  }

  return <Empty icon="image" text={preview ? "저장 후 등록한 상세 이미지가 이 영역에 순서대로 표시됩니다." : "등록된 상세정보 이미지와 설명이 없습니다."} />;
}

function CollapsibleContent({ expanded, onToggle, children }) {
  return (
    <div className="mx-auto max-w-[860px] rounded-lg bg-white px-lg py-xl">
      <div className={`relative overflow-hidden transition-[max-height] duration-300 ${expanded ? "max-h-none" : "max-h-[520px]"}`}>
        {children}
        {!expanded && <div className="pointer-events-none absolute inset-x-0 bottom-0 h-28 bg-gradient-to-t from-white to-transparent" />}
      </div>
      <button type="button" onClick={onToggle} className="mx-auto mt-lg flex items-center gap-xs rounded-full border border-hairline bg-white px-xl py-sm font-body-strong hover:border-primary hover:text-primary">
        {expanded ? "접기" : "상세 내용 더보기"}<Icon name={expanded ? "expand_less" : "expand_more"} />
      </button>
    </div>
  );
}

function DetailImage({ image, eventName, index }) {
  const [failed, setFailed] = useState(false);
  if (failed) {
    return (
      <div className="grid min-h-48 place-items-center border-b border-hairline bg-surface-container px-lg py-xl text-center">
        <div>
          <Icon name="broken_image" className="text-[40px] text-ink-muted" />
          <p className="mt-sm font-body-strong">상세 이미지 {index + 1}을 불러오지 못했습니다.</p>
          <button type="button" onClick={() => setFailed(false)} className="mt-md rounded-full border border-hairline bg-white px-md py-xs text-caption font-body-strong">다시 불러오기</button>
        </div>
      </div>
    );
  }
  return (
    <img
      src={fileDownloadUrl(image.fileId)}
      alt={image.altText || `${eventName} 상세정보 ${index + 1}`}
      className="block h-auto w-full bg-surface-container"
      loading={index === 0 ? "eager" : "lazy"}
      decoding="async"
      fetchPriority={index === 0 ? "high" : "auto"}
      onError={() => setFailed(true)}
    />
  );
}

function Empty({ icon, text }) {
  return (
    <div className="border-y border-hairline py-xxl text-center">
      <Icon name={icon} className="text-[44px] text-ink-muted" />
      <p className="mt-md font-body-strong">{text}</p>
      <p className="mt-xs text-caption text-ink-muted">행사 기본 정보는 상단에서 확인할 수 있습니다.</p>
    </div>
  );
}
