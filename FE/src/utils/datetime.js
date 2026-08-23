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

// Date -> "YYYY-MM-DD" (로컬 기준). toISOString()은 UTC라 KST 00:00~08:59에는 하루 전 날짜가 나온다.
const toLocalDateString = (date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
};

export { toIsoOffset, toDatetimeLocal, toLocalDateString };
