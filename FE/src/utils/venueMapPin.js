// 평면도 핀에 부스 코드 전체를 표시하되, 코드 길이에 맞춰 글자 크기를 줄여 잘리지 않게 한다.
// 핀 자체는 18px 고정(VenueMapPins)이라 길이 구간별로 폰트를 줄여야 "A-10" 같은 코드도 안 잘린다.
const PIN_FONT_SIZE_BY_LENGTH = [
  { maxLength: 2, className: "text-[8px]" }, // "A1", "12" 등
  { maxLength: 4, className: "text-[6.5px]" }, // "A-1", "B-12" 등
];
const PIN_FONT_SIZE_FALLBACK = "text-[5.5px]"; // 5자 이상

const pinFontSizeClass = (code) => {
  const len = (code ?? "?").length;
  const match = PIN_FONT_SIZE_BY_LENGTH.find((entry) => len <= entry.maxLength);
  return match ? match.className : PIN_FONT_SIZE_FALLBACK;
};

export { pinFontSizeClass };
