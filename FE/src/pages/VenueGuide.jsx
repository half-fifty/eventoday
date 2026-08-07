import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import TopNav from "../components/TopNav.jsx";
import Footer from "../components/Footer.jsx";
import Icon from "../components/Icon.jsx";

const venueRows = [
  ["kintex","KINTEX","킨텍스","경기","고양","경기도 고양시 일산서구 킨텍스로 217-60","031-810-8114","https://www.kintex.com","#2563eb",37.6689333,126.7458026,"left"],
  ["songdo","SONGDO","송도컨벤시아","인천","인천","인천광역시 연수구 센트럴로 123","032-210-1114","https://www.songdoconvensia.com","#0891b2",37.3887098,126.6468363,"left"],
  ["coex","COEX","코엑스","서울","서울","서울특별시 강남구 영동대로 513","02-6000-0114","https://www.coex.co.kr","#ec4899",37.5118242,127.0591586,"left"],
  ["setec","SETEC","세텍","서울","서울","서울특별시 강남구 남부순환로 3104","02-2222-3800","https://www.setec.or.kr","#f97316",37.4956684,127.0719299,"left"],
  ["suwon","SUWON MESSE","수원메쎄","경기","수원","경기도 수원시 권선구 세화로134번길 37","031-304-9300","https://www.suwonmesse.com","#ef4444",37.2668139,126.9972897,"left"],
  ["osco","OSCO","오스코","충북","청주","충청북도 청주시 흥덕구 오송읍 오송생명로 250","043-238-8000","https://osco.or.kr","#7c3aed",36.6265269,127.3354599,"left"],
  ["dcc","DCC","대전컨벤션센터","대전","대전","대전광역시 유성구 엑스포로 107","042-250-1100","https://www.dcckorea.or.kr","#4f46e5",36.3752314,127.3916651,"left"],
  ["kdj","KDJ CENTER","김대중컨벤션센터","광주","광주","광주광역시 서구 상무누리로 30","062-611-2000","https://kdjcenter.gjto.or.kr","#be123c",35.1466981,126.8404444,"left"],
  ["icc","ICC JEJU","제주국제컨벤션센터","제주","제주","제주특별자치도 서귀포시 중문관광로 224","064-735-1000","https://www.iccjeju.co.kr","#059669",33.2414129,126.4244414,"left"],
  ["gumico","GUMICO","구미코","경북","구미","경상북도 구미시 산동읍 첨단기업1로 49","054-477-2700","https://www.gumico.com","#9333ea",36.1415401,128.4424831,"right"],
  ["adco","ADCO","안동국제컨벤션센터","경북","안동","경상북도 안동시 도산면 월천길 301","054-857-9919","https://www.andong.go.kr/adco","#991b1b",36.6958037,128.8432820,"right"],
  ["exco","EXCO","엑스코","대구","대구","대구광역시 북구 엑스코로 10","053-601-5000","https://www.exco.co.kr","#e11d48",35.9071773,128.6130296,"right"],
  ["hico","HICO","경주화백컨벤션센터","경북","경주","경상북도 경주시 보문로 507","054-702-1000","https://www.hico.or.kr","#ea580c",35.8385344,129.2880128,"right"],
  ["ceco","CECO","창원컨벤션센터","경남","창원","경상남도 창원시 성산구 원이대로 362","055-212-1000","https://www.ceco.co.kr","#0f766e",35.2384064,128.6568952,"right"],
  ["bexco","BEXCO","부산 벡스코","부산","부산","부산광역시 해운대구 APEC로 55","051-740-7300","https://www.bexco.co.kr","#0369a1",35.1691716,129.1362562,"right"],
];

// 이 지도는 실제 경위도 투영도가 아니라 지역 모양을 편집한 도안이므로,
// 마커는 도안의 800×1200 viewBox 안에서 검증한 전시장별 앵커를 사용한다.
const mapAnchors = {
  kintex: [35, 27], songdo: [33, 31], coex: [40, 29], setec: [42, 30], suwon: [40, 34],
  osco: [46, 43], dcc: [47, 47], kdj: [39, 61], icc: [22, 93],
  gumico: [64, 50], adco: [69, 42], exco: [68, 55], hico: [74, 56],
  ceco: [66, 62], bexco: [72, 63],
};

const venues = venueRows.map(([id,short,name,region,city,address,phone,site,color,latitude,longitude,side])=>({
  id,short,name,region,city,address,phone,site,color,latitude,longitude,side,
  x: mapAnchors[id][0], y: mapAnchors[id][1],
}));

const venueImages = {
  kintex: {
    src: "https://commons.wikimedia.org/wiki/Special:Redirect/file/킨텍스.jpg?width=960",
    credit: "Wikimedia Commons",
  },
  songdo: {
    src: "https://commons.wikimedia.org/wiki/Special:Redirect/file/Songdo%20Convensia%20and%20Central%20Park%20View.jpg?width=960",
    credit: "Ken Eckert · Wikimedia Commons",
  },
  coex: {
    src: "https://commons.wikimedia.org/wiki/Special:Redirect/file/Seoul-COEX.jpg?width=960",
    credit: "Erik Möller · Wikimedia Commons",
  },
  setec: {
    src: "https://commons.wikimedia.org/wiki/Special:Redirect/file/SETEC%20and%20entrance.JPG?width=960",
    credit: "Wikimedia Commons",
  },
  suwon: {
    src: "https://commons.wikimedia.org/wiki/Special:Redirect/file/20250517%20수원메쎄.jpg?width=960",
    credit: "Striker9498 · Wikimedia Commons",
  },
  osco: {
    src: "https://commons.wikimedia.org/wiki/Special:Redirect/file/20250607%20청주%20오스코%20내부%202층.jpg?width=960",
    credit: "Striker9498 · Wikimedia Commons",
  },
  dcc: {
    src: "https://commons.wikimedia.org/wiki/Special:Redirect/file/Daejeon%20Convention%20Center%20I.jpg?width=960",
    credit: "Dr.JamesKwon · Wikimedia Commons",
  },
  kdj: {
    src: "https://commons.wikimedia.org/wiki/Special:Redirect/file/Kimdaejung%20convention%20center%2020190521%20155808.jpg?width=960",
    credit: "LERK · Wikimedia Commons",
  },
  icc: {
    src: "https://commons.wikimedia.org/wiki/Special:Redirect/file/2006iccjeju173.jpg?width=960",
    credit: "Wikimedia Commons",
  },
  gumico: {
    src: "https://gumico.com/_next/static/media/banner_1.700dcd2a.png",
    credit: "GUMICO 공식 홈페이지",
  },
  adco: {
    src: "https://www.andong.go.kr/theme/img/main/img_visual_01.jpg",
    credit: "ADCO 공식 홈페이지",
  },
  exco: {
    src: "https://www.exco.co.kr/kakaoimg.jpg",
    credit: "EXCO 공식 홈페이지",
  },
  hico: {
    src: "https://www.hico.or.kr/img/visual_new022.jpg",
    credit: "HICO 공식 홈페이지",
  },
  ceco: {
    src: "https://commons.wikimedia.org/wiki/Special:Redirect/file/Changwon%20Exhibition%20Convention%20Center%20in%202013.jpg?width=960",
    credit: "Choi2451 · Wikimedia Commons",
  },
  bexco: {
    src: "https://commons.wikimedia.org/wiki/Special:Redirect/file/Busan%20BEXCO.jpg?width=960",
    credit: "M.M.Minderhoud · Wikimedia Commons",
  },
};

function VenueCard({ venue, selected, onSelect }) {
  return <button type="button" onClick={()=>onSelect(venue.id)} className={`venue-list-card ${selected?"is-selected":""}`} style={{"--venue-color":venue.color}}>
    <span className="venue-card-accent"/><span className="min-w-0">
      <span className="flex items-center justify-between gap-2"><strong className="venue-wordmark">{venue.short}</strong><span className="venue-region">{venue.region}</span></span>
      <span className="mt-1.5 block truncate text-[12px] font-bold text-slate-700">{venue.name}</span>
      <span className="mt-1 flex items-center gap-1 truncate text-[10px] text-slate-400"><Icon name="location_on" className="text-[12px]"/>{venue.address}</span>
    </span>
  </button>;
}

function MapStage({filtered,selected,onSelect}) {
  const visible = new Set(filtered.map(v=>v.id));
  return <div className="venue-map-stage">
    <div className="venue-map-glow"/>
    <div className="venue-map-canvas">
      <img
        className="venue-korea-map"
        src="https://upload.wikimedia.org/wikipedia/commons/d/dc/Map_of_South_Korea-blank.svg"
        alt="대한민국 행정구역 지도"
      />
      {venues.map(v=><button key={v.id} type="button" aria-label={`${v.name} 선택`} onClick={()=>onSelect(v.id)} className={`venue-marker ${selected.id===v.id?"is-active":""} ${visible.has(v.id)?"":"is-dimmed"}`} style={{left:`${v.x}%`,top:`${v.y}%`,"--venue-color":v.color}}><span className="venue-marker-ring"/><span className="venue-marker-dot"/><span className="venue-marker-label">{v.short}</span></button>)}
    </div>
    <div key={selected.id} className="venue-detail-card" style={{"--venue-color":selected.color}}>
      <div className={`venue-detail-visual ${venueImages[selected.id] ? "has-photo" : ""}`}>
        {venueImages[selected.id] ? <img src={venueImages[selected.id].src} alt={`${selected.name} 전경`} className="venue-detail-photo"/> : <><span className="venue-detail-grid"/><Icon name="apartment" className="relative text-[54px] text-white/90"/></>}
        <span className="venue-detail-shade"/>
        <span className="absolute bottom-3 left-4 text-[9px] font-black tracking-[.22em] text-white/90">EVENTODAY VENUE</span>
        {venueImages[selected.id] && <span className="venue-photo-credit">{venueImages[selected.id].credit}</span>}
      </div>
      <div className="p-5"><div className="flex items-start justify-between gap-3"><div><p className="text-[10px] font-black tracking-[.17em]" style={{color:selected.color}}>{selected.short}</p><h2 className="mt-1 text-[20px] font-black tracking-tight">{selected.name}</h2></div><span className="rounded-full bg-slate-100 px-3 py-1 text-[10px] font-bold text-slate-600">{selected.city}</span></div>
      <p className="mt-3 flex gap-2 text-[11px] leading-5 text-slate-500"><Icon name="location_on" className="mt-0.5 text-[14px]"/>{selected.address}</p><p className="mt-1 flex gap-2 text-[11px] text-slate-500"><Icon name="call" className="text-[14px]"/>{selected.phone}</p>
      <div className="mt-4 grid grid-cols-2 gap-2"><a href={selected.site} target="_blank" rel="noreferrer" className="venue-detail-button secondary">홈페이지 <Icon name="open_in_new" className="text-[13px]"/></a><Link to={`/events?venue=${encodeURIComponent(selected.name)}`} className="venue-detail-button primary">행사 보기 <Icon name="arrow_forward" className="text-[13px]"/></Link></div></div>
    </div>
  </div>;
}

export default function VenueGuide(){
  const [selectedId,setSelectedId]=useState("coex"),[keyword,setKeyword]=useState(""),[region,setRegion]=useState("전체");
  useEffect(() => {
    const preloaders = Object.values(venueImages).map(({ src }) => {
      const image = new Image();
      image.decoding = "async";
      image.src = src;
      return image;
    });
    return () => preloaders.forEach((image) => { image.onload = null; image.onerror = null; });
  }, []);
  const regions=["전체",...new Set(venues.map(v=>v.region))];
  const filtered=useMemo(()=>venues.filter(v=>{const q=keyword.trim().toLowerCase();return(region==="전체"||v.region===region)&&(!q||`${v.name} ${v.short} ${v.address}`.toLowerCase().includes(q));}),[keyword,region]);
  const selected=venues.find(v=>v.id===selectedId)||venues[0];
  return <div className="min-h-screen bg-[#f7f9fc] text-slate-900"><TopNav active="venues"/><main className="pt-[44px]">
    <section className="venue-hero"><div className="mx-auto max-w-[1280px] px-lg py-12 md:py-16"><div className="flex flex-col gap-7 lg:flex-row lg:items-end lg:justify-between"><div><p className="mb-3 text-[11px] font-black uppercase tracking-[.28em] text-primary">Exhibition venues in Korea</p><h1 className="text-[34px] font-black tracking-[-.04em] text-slate-950 md:text-[52px]">대한민국 전시장을<br className="hidden sm:block"/> 한눈에 만나보세요.</h1><p className="mt-4 max-w-xl text-sm leading-6 text-slate-500">전국 주요 전시장의 위치와 시설 정보를 살펴보고, 지금 열리는 행사까지 바로 확인하세요.</p></div><div className="venue-search-box"><Icon name="search" className="text-[20px] text-slate-400"/><input value={keyword} onChange={e=>setKeyword(e.target.value)} placeholder="전시장명 또는 지역 검색" aria-label="전시장 검색"/>{keyword&&<button type="button" onClick={()=>setKeyword("")}><Icon name="close" className="text-[17px]"/></button>}</div></div><div className="mt-8 flex gap-2 overflow-x-auto pb-2 hide-scrollbar">{regions.map(r=><button key={r} type="button" onClick={()=>setRegion(r)} className={`venue-filter-chip ${region===r?"is-active":""}`}>{r}</button>)}</div></div></section>
    <section className="mx-auto max-w-[1440px] px-4 py-8 md:px-8 md:py-12"><div className="mb-5 flex items-center justify-between px-1"><p className="text-sm font-bold text-slate-700"><span className="text-primary">{filtered.length}</span>개의 전시장</p><p className="hidden text-[12px] text-slate-400 md:block">카드나 지도 위 마커를 선택해 보세요</p></div>
    {!filtered.length?<div className="rounded-[28px] border bg-white py-24 text-center"><Icon name="search_off" className="text-[40px] text-slate-300"/><p className="mt-3 font-bold">검색 결과가 없습니다.</p><button type="button" onClick={()=>{setKeyword("");setRegion("전체")}} className="mt-4 text-sm font-bold text-primary">전체 전시장 보기</button></div>:<div className="venue-infographic"><aside className="venue-side-list">{filtered.filter(v=>v.side==="left").map(v=><VenueCard key={v.id} venue={v} selected={selectedId===v.id} onSelect={setSelectedId}/>)}</aside><MapStage filtered={filtered} selected={selected} onSelect={setSelectedId}/><aside className="venue-side-list">{filtered.filter(v=>v.side==="right").map(v=><VenueCard key={v.id} venue={v} selected={selectedId===v.id} onSelect={setSelectedId}/>)}</aside></div>}</section>
  </main><Footer/></div>;
}
