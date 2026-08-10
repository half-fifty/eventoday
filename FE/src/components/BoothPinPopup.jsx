import Icon from "./Icon.jsx";

// 평면도 핀 클릭 시 뜨는 부스 정보 팝업.
export default function BoothPinPopup({ booth, onClose }) {
  if (!booth) return null;
  return (
    <div
      className="fixed inset-0 z-[200] bg-black/50 flex items-center justify-center p-lg"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-2xl max-w-[360px] w-full p-xl relative"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className="absolute top-lg right-lg text-ink-muted hover:text-on-surface"
        >
          <Icon name="close" className="text-[22px]" />
        </button>
        <h3 className="font-display-md text-[18px] mb-1">
          {booth.displayName || booth.boothCode}
        </h3>
        <p className="text-caption text-ink-muted">{booth.boothCode}</p>
      </div>
    </div>
  );
}
