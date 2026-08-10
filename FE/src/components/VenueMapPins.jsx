import { fileDownloadUrl } from "../api/fileApi.js";
import { pinFontSizeClass } from "../utils/venueMapPin.js";

// 평면도 이미지 위에 부스 핀을 절대좌표(xRatio/yRatio)로 겹쳐 그린다.
export default function VenueMapPins({ venueMap, onPinClick, pinClassName }) {
  return (
    <div className="relative inline-block max-w-full select-none">
      <img
        src={fileDownloadUrl(venueMap.imageFileId)}
        alt={`${venueMap.floorName} 평면도`}
        className="block max-w-full rounded-lg"
      />
      {(venueMap.positions ?? []).map((p) => (
        <button
          key={p.boothId}
          type="button"
          onClick={() => onPinClick?.(p)}
          title={p.displayName || p.boothCode}
          aria-label={`부스 ${p.displayName || p.boothCode}`}
          // 18px는 좁은 배치도 위에서 겹치지 않을 최소 크기로 맞춘 값 (핀이 너무 크면 인접 부스를 가림).
          className={`absolute -translate-x-1/2 -translate-y-full min-w-[18px] h-[18px] px-1 flex items-center justify-center text-white font-bold leading-none rounded-full border border-white shadow-md hover:scale-110 transition-transform whitespace-nowrap ${pinFontSizeClass(p.boothCode)} ${
            pinClassName ? pinClassName(p) : "bg-primary"
          }`}
          style={{ left: `${Number(p.xRatio) * 100}%`, top: `${Number(p.yRatio) * 100}%` }}
        >
          {p.boothCode || "?"}
        </button>
      ))}
    </div>
  );
}
