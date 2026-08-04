import { useEffect, useRef, useState } from "react";

let sdkPromise;

const loadKakaoMap = () => {
  if (window.kakao?.maps) return Promise.resolve(window.kakao.maps);
  if (sdkPromise) return sdkPromise;
  const appKey = import.meta.env.VITE_KAKAO_MAP_JS_KEY;
  if (!appKey) return Promise.reject(new Error("카카오맵 JavaScript 키가 없습니다."));
  sdkPromise = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false`;
    script.onload = () => window.kakao.maps.load(() => resolve(window.kakao.maps));
    script.onerror = () => reject(new Error("카카오맵을 불러오지 못했습니다."));
    document.head.appendChild(script);
  }).catch((error) => {
    sdkPromise = undefined;
    throw error;
  });
  return sdkPromise;
};

export default function KakaoMapPreview({ latitude, longitude, venueName }) {
  const containerRef = useRef(null);
  const [error, setError] = useState("");
  const hasSelectedPlace = Boolean(latitude && longitude);
  const displayLatitude = latitude || 37.511824182641504;
  const displayLongitude = longitude || 127.05915859162609;
  const displayName = hasSelectedPlace ? venueName : "코엑스 (예시 위치)";

  useEffect(() => {
    if (!containerRef.current) return;
    let cancelled = false;
    loadKakaoMap()
      .then((maps) => {
        if (cancelled || !containerRef.current) return;
        const position = new maps.LatLng(Number(displayLatitude), Number(displayLongitude));
        const map = new maps.Map(containerRef.current, { center: position, level: 4 });
        new maps.Marker({ map, position, title: displayName });
      })
      .catch((requestError) => !cancelled && setError(requestError.message));
    return () => { cancelled = true; };
  }, [displayLatitude, displayLongitude, displayName]);

  return <div className="relative">
    <div ref={containerRef} className="h-[240px] rounded-xl overflow-hidden border border-hairline" aria-label={`${displayName} 지도 위치`} />
    {!hasSelectedPlace && <div className="absolute top-md left-md right-md bg-white/95 border border-hairline rounded-lg p-sm text-caption shadow-sm pointer-events-none"><strong>예시 위치 · 코엑스</strong><span className="text-ink-muted ml-sm">장소를 검색해 선택하면 실제 위치로 변경됩니다.</span></div>}
    {error && <p className="absolute inset-x-md bottom-md bg-white/95 text-error text-caption p-sm rounded-lg">{error}</p>}
  </div>;
}
