// datetime-local 인풋(로컬 시각 문자열) <-> ISO-8601(UTC) 문자열 변환.
// 그냥 슬라이스하면 UTC 값이 로컬 시각인 것처럼 표시되어 KST 환경에서 9시간 어긋난다.
const toIsoOffset = (datetimeLocalValue) => {
  if (!datetimeLocalValue) return "";

  const date = new Date(datetimeLocalValue);
  if (Number.isNaN(date.getTime())) return "";

  return date.toISOString();
};

const toDatetimeLocal = (isoValue) => {
  if (!isoValue) return "";

  const date = new Date(isoValue);
  if (Number.isNaN(date.getTime())) return "";

  const localMs = date.getTime() - date.getTimezoneOffset() * 60_000;
  return new Date(localMs).toISOString().slice(0, 16);
};

export { toIsoOffset, toDatetimeLocal };
