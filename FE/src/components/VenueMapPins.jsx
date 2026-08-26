import { fileDownloadUrl } from "../api/fileApi.js";
import { pinFontSizeClass } from "../utils/venueMapPin.js";

// 박스 자체를 이 비율로 고정해, 원본 해상도가 제각각인 평면도 이미지도 화면에서 항상 같은 크기로 보이게 한다.
const CONTAINER_ASPECT = 4 / 3;

// object-fit:contain으로 이미지를 넣으면 원본 비율에 따라 위아래 또는 좌우에 여백(레터박스)이 생긴다.
// 부스 핀 좌표(xRatio/yRatio)는 "실제 이미지가 그려지는 영역" 기준이라, 컨테이너 전체가 아니라
// 이 여백을 뺀 영역 기준으로 환산해야 핀이 이미지 위 정확한 위치에 맞는다.
const getImageRect = (originalWidth, originalHeight) => {
  const imageAspect = originalWidth && originalHeight ? originalWidth / originalHeight : CONTAINER_ASPECT;

  if (imageAspect >= CONTAINER_ASPECT) {
    // 이미지가 컨테이너보다 상대적으로 넓다 -> 가로를 꽉 채우고 위아래에 여백이 생긴다.
    const heightPercent = (CONTAINER_ASPECT / imageAspect) * 100;
    return { widthPercent: 100, heightPercent, leftPercent: 0, topPercent: (100 - heightPercent) / 2 };
  }
  // 이미지가 컨테이너보다 상대적으로 좁다 -> 세로를 꽉 채우고 좌우에 여백이 생긴다.
  const widthPercent = (imageAspect / CONTAINER_ASPECT) * 100;
  return { widthPercent, heightPercent: 100, leftPercent: (100 - widthPercent) / 2, topPercent: 0 };
};

// 평면도 이미지 위에 부스 핀을 절대좌표(xRatio/yRatio)로 겹쳐 그린다.
// 원본 해상도가 제각각인 이미지도 고정 비율 박스 안에 항상 같은 크기로 표시한다(object-fit: contain, 크롭 없음).
export default function VenueMapPins({ venueMap, onPinClick, pinClassName }) {
  const rect = getImageRect(venueMap.originalWidth, venueMap.originalHeight);

  return (
    <div className="relative w-full aspect-[4/3] rounded-lg bg-surface-container-low overflow-hidden select-none">
      <img
        src={fileDownloadUrl(venueMap.imageFileId)}
        alt={`${venueMap.floorName} 평면도`}
        className="absolute inset-0 w-full h-full object-contain"
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
          style={{
            left: `${rect.leftPercent + rect.widthPercent * Number(p.xRatio)}%`,
            top: `${rect.topPercent + rect.heightPercent * Number(p.yRatio)}%`,
          }}
        >
          {p.boothCode || "?"}
        </button>
      ))}
    </div>
  );
}
