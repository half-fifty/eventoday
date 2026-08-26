const facility = (icon, title, location, description) => ({ icon, title, location, description });
const parking = (summary, rates = [], contact = "공식 홈페이지에서 최신 정보를 확인해 주세요.") => ({
  summary,
  liveNotice: "실시간 혼잡도는 제공하지 않으며, 공식 홈페이지의 최신 안내를 확인해 주세요.",
  rates,
  destinations: [],
  contact,
});

const venueGuides = [
  {
    id: "coex",
    name: "코엑스",
    aliases: ["코엑스", "coex"],
    officialUrl: "https://www.coex.co.kr/",
    facilitiesUrl: "https://www.coex.co.kr/guide/amenities/",
    parkingUrl: "https://www.coex.co.kr/guide/parking-information/infomation/",
    parkingFeeUrl: "https://www.coex.co.kr/guide/parking-information/fee/",
    verifiedAt: "2026.08.19",
    facilities: [
      { icon: "ev_station", title: "전기차 급속 충전", location: "옥상주차장(RF)", description: "급속 충전기 6대" },
      { icon: "electrical_services", title: "전기차 완속 충전", location: "옥상주차장(RF)", description: "완속 충전기 47대" },
      { icon: "support_agent", title: "주차고객 서비스센터", location: "1층 동문 방향 · 데블스도어 뒤편", description: "09:00~19:00 · 주말·공휴일 10:00~19:00" },
      { icon: "help_center", title: "주차장 현장지원실", location: "지하 2층 44N 구역", description: "시설 이용 문의 02-6000-7514" },
      { icon: "accessible", title: "교통약자 안내", location: "행사장 안내데스크 문의", description: "장애인 주차 및 이동 지원은 방문 전 확인해 주세요." },
      { icon: "info", title: "종합 안내", location: "코엑스 안내데스크", description: "대표번호 02-6000-0114" },
    ],
    facilitiesImages: [
      { label: "코엑스 층별 편의시설 안내", imageUrl: "https://www.coex.co.kr/wp-content/uploads/2025/09/maps-amenities1.png" },
    ],
    parking: {
      summary: "코엑스 주차장은 B2·B3·B4·옥상층으로 운영됩니다. 행사 홀과 입구에 따라 가까운 구역을 확인해 주세요.",
      liveNotice: "실시간 혼잡도와 만차 예상 정보는 코엑스 공식 안내에서 확인할 수 있습니다.",
      rates: [
        { label: "승용차", value: "15분당 1,500원", note: "일일 최대 60,000원" },
        { label: "화물차 2.5t 이상", value: "15분당 3,000원", note: "일일 최대 120,000원" },
        { label: "입차 후 20분 이내", value: "주차료 면제", note: "20분 초과 시 입차 시점부터 정상요금" },
        { label: "장애인·국가유공자", value: "50% 할인", note: "증빙 및 현장 기준 확인" },
      ],
      destinations: [
        { floor: "B2", title: "전시장 A·C홀", description: "방문 전 공식 층별 안내에서 가까운 입구를 확인하세요." },
        { floor: "B2", title: "전시장 B·D홀", description: "행사 개최 홀에 맞는 진입 방향을 확인하세요." },
        { floor: "B3·B4", title: "혼잡 시 대체 주차", description: "현장 유도 표지와 주차요원 안내를 따라 이동하세요." },
        { floor: "RF", title: "전기차 충전", description: "급속·완속 충전 시설이 옥상주차장에 있습니다." },
      ],
      contact: "주차요금 문의 02-6002-7130",
      maps: [
        { key: "roof", group: "층별", label: "옥상", imageUrl: "https://www.coex.co.kr/wp-content/uploads/2026/07/maps-parkingexit1-1.png" },
        { key: "b2", group: "층별", label: "B2", imageUrl: "https://www.coex.co.kr/wp-content/uploads/2026/07/maps-parkingexit8-1.png" },
        { key: "b3-b4", group: "층별", label: "B3·B4", imageUrl: "https://www.coex.co.kr/wp-content/uploads/2026/07/maps-parkingexit3-1.png" },
        { key: "hall-ac", group: "전시장", label: "Hall A·C", imageUrl: "https://www.coex.co.kr/wp-content/uploads/2026/07/maps-parkingexit4-1.png" },
        { key: "hall-bd", group: "전시장", label: "Hall B·D", imageUrl: "https://www.coex.co.kr/wp-content/uploads/2026/07/maps-parkingexit3-1.png" },
        { key: "conference-south", group: "회의실", label: "컨퍼런스룸 남", imageUrl: "https://www.coex.co.kr/wp-content/uploads/2026/07/maps-parkingexit4-1.png" },
        { key: "ballroom", group: "회의실", label: "그랜드볼룸·아셈볼룸", imageUrl: "https://www.coex.co.kr/wp-content/uploads/2026/07/maps-parkingexit7-1.png" },
        { key: "mall", group: "기타", label: "코엑스몰", imageUrl: "https://www.coex.co.kr/wp-content/uploads/2026/07/maps-parkingexit8-1.png" },
        { key: "trade-tower", group: "타워", label: "트레이드타워", imageUrl: "https://www.coex.co.kr/wp-content/uploads/2026/07/maps-parkingexit10-1.png" },
        { key: "asem-tower", group: "타워", label: "아셈타워", imageUrl: "https://www.coex.co.kr/wp-content/uploads/2026/07/maps-parkingexit-asem.png" },
      ],
    },
  },
  {
    id: "kintex",
    name: "킨텍스",
    aliases: ["킨텍스", "kintex"],
    officialUrl: "https://www.kintex.com/",
    facilitiesUrl: "https://www.kintex.com/",
    parkingUrl: "https://parking.kintex.com/",
    verifiedAt: "2026.08.19",
    facilities: [
      facility("info", "종합 안내", "제1·2전시장 1층", "행사 및 시설 이용 안내"),
      facility("medical_services", "응급 서비스", "제1전시장 1층 · 제2전시장 2층", "현장 응급 지원"),
      facility("local_parking", "주차 안내", "제1전시장 1층 · 제2전시장 지하 1층", "주차 관련 업무"),
    ],
    parking: parking("제1·2전시장과 주변 주차장의 운영 상태, 내 차 찾기와 결제 기능을 공식 주차 서비스에서 제공합니다.", [], "킨텍스 주차 관련 문의 031-995-8265"),
  },
  {
    id: "songdo",
    name: "송도컨벤시아",
    aliases: ["송도컨벤시아", "songdo convensia", "songdo"],
    officialUrl: "https://www.songdoconvensia.com/",
    parkingUrl: "https://tour.visitincheon.or.kr/sch/comm/html/UI-SC-0102-004Q-1.do",
    verifiedAt: "2026.08.19",
    facilities: [
      facility("accessible", "접근성 시설", "각 층", "휠체어 이동 가능한 출입구·엘리베이터·화장실"),
      facility("child_care", "수유실", "1층·2층", "관람객 수유 공간"),
      facility("restaurant", "카페·식당", "센터 내부", "입점 시설 운영 여부는 방문 전 확인"),
    ],
    parking: parking("지상 335대, 지하 802대 등 총 1,157대 규모의 주차장을 운영합니다.", [
      { label: "소형", value: "최초 30분 600원", note: "전일 6,000원" },
      { label: "대형", value: "최초 30분 1,200원", note: "전일 12,000원" },
      { label: "할인", value: "요금의 50%", note: "장애인·국가유공자·경차 등" },
    ], "주차관리소 032-210-1170"),
  },
  {
    id: "setec", name: "SETEC", aliases: ["setec", "세텍"], officialUrl: "https://www.setec.or.kr/",
    facilitiesUrl: "https://www.setec.or.kr/front/facility/facility04.do", parkingUrl: "https://www.setec.or.kr/front/traffic/trafficInfo02.do", verifiedAt: "2026.08.19",
    facilities: [facility("deck", "야외 데크", "전시장 외부", "관람객 휴게 공간"), facility("chair", "편의 공간", "전시장 1층", "실내 휴게 공간"), facility("restaurant", "식당", "컨벤션센터 1층", "운영 여부는 방문 전 확인")],
    parking: parking("총 387대 규모로 행사일에는 혼잡할 수 있어 대중교통 이용을 권장합니다.", [
      { label: "기본", value: "최초 30분 1,800원", note: "추가 5분당 300원" }, { label: "일일 최대", value: "21,600원", note: "6시간 이상 자동 적용" },
    ], "주차 문의 02-2187-4665"),
  },
  {
    id: "suwon", name: "수원메쎄", aliases: ["수원메쎄", "suwon messe"], officialUrl: "https://suwonmesse.com/", facilitiesUrl: "https://suwonmesse.com/cs-center/faq-visitor/", parkingUrl: "https://suwonmesse.com/cs-center/faq-visitor/", verifiedAt: "2026.08.19",
    facilities: [facility("lock", "유료 물품보관함", "Hall 2 외부 연결동 C동 1층", "소형·중형·대형 보관함"), facility("child_care", "수유실", "Hall 2 측 로비 여자화장실 우측", "관람객 수유 공간")],
    parking: parking("출입구 인근 무인정산기 또는 출차 정산소를 이용합니다.", [{ label: "시간 요금", value: "1시간 4,200원", note: "" }, { label: "일일 최대", value: "30,000원", note: "" }]),
  },
  {
    id: "osco", name: "청주오스코", aliases: ["오스코", "osco", "청주오스코"], officialUrl: "https://osco.or.kr/", verifiedAt: "2026.08.19",
    facilitiesNotice: "공식 홈페이지에 전시장·회의실 안내는 있으나 관람객용 편의시설 상세 정보는 확인되지 않아 제공하지 않습니다.",
    parkingNotice: "공식 홈페이지에 주차장 메뉴는 있으나 현재 검색 가능한 요금·운영 상세 정보가 확인되지 않아 제공하지 않습니다.",
  },
  {
    id: "dcc", name: "대전컨벤션센터", aliases: ["대전컨벤션센터", "dcc"], officialUrl: "https://www.dcckorea.or.kr/", facilitiesUrl: "https://www.dcckorea.or.kr/content/view.do?contentKey=74", verifiedAt: "2026.08.19",
    facilities: [facility("lock", "물품보관소", "제1전시장", "관람객 물품 보관"), facility("child_care", "수유실·의무실", "제2전시장", "수유 및 응급 지원 공간"), facility("restaurant", "카페·식당·편의점", "제2전시장", "현장 운영 여부 확인 필요")],
    parking: parking("제1전시장 403대, 제2전시장 734대 규모입니다. 공식 사이트에서 주차요금 상세는 확인되지 않았습니다.", [], "주차 문의 제1전시장 042-250-1570·1572 / 제2전시장 042-250-6273"),
  },
  {
    id: "kdj", name: "김대중컨벤션센터", aliases: ["김대중컨벤션센터", "kdj center", "kdj"], officialUrl: "https://kdjcenter.gjto.or.kr/", parkingUrl: "https://kdjcenter.gjto.or.kr/bbs/parking", verifiedAt: "2026.08.19",
    facilitiesNotice: "공식 홈페이지에 편의시설 메뉴는 있으나 검색 가능한 개별 시설 상세 정보가 확인되지 않아 제공하지 않습니다.",
    parking: parking("옥내 462대, 야외1 478대, 야외2 565대 등 총 1,502대 규모입니다.", [
      { label: "옥내", value: "최초 30분 700원", note: "1일 8,000원" }, { label: "야외1", value: "최초 30분 350원", note: "1일 4,000원" }, { label: "야외2", value: "무료", note: "" },
    ], "대표 문의 062-611-2000"),
  },
  {
    id: "icc", name: "ICC JEJU", aliases: ["icc jeju", "제주국제컨벤션센터", "icc제주"], officialUrl: "https://www.iccjeju.co.kr/", facilitiesUrl: "https://iccjeju.co.kr/guide/01_1_2.php", parkingUrl: "https://www.iccjeju.co.kr/guide/03.php", verifiedAt: "2026.08.19",
    facilities: [facility("coffee", "Café 224", "1층", "09:00~18:00"), facility("local_convenience_store", "편의점", "3층 로비", "09:00~18:00")],
    parking: parking("실내·외 구역을 합해 약 400대 규모의 주차장을 운영합니다.", [{ label: "입차 후 1시간", value: "무료", note: "" }, { label: "추가", value: "30분당 1,000원", note: "일 최대 5,000원·대형 10,000원" }, { label: "할인", value: "50%", note: "장애인·국가유공자·다자녀 등 증빙 필요" }], "주차 문의 070-7119-2036"),
  },
  {
    id: "gumico", name: "구미코", aliases: ["구미코", "gumico"], officialUrl: "https://gumico.com/", facilitiesUrl: "https://gumico.com/infra-info/", parkingUrl: "https://gumico.com/infra-info/parking", verifiedAt: "2026.08.19",
    facilitiesNotice: "공식 홈페이지에 부대편의시설 메뉴는 있으나 검색 가능한 개별 시설 상세 정보가 확인되지 않아 제공하지 않습니다.",
    parking: parking("2층 야외주차장에 승용차 184대와 대형버스 8대, 총 192대를 수용합니다.", [{ label: "주차요금", value: "무료", note: "야외주차장 입구 진입 후 2층 출입구 이용" }], "대표 문의 054-477-8000"),
  },
  {
    id: "adco", name: "안동국제컨벤션센터", aliases: ["안동국제컨벤션센터", "adco"], officialUrl: "https://www.andong.go.kr/adco/", verifiedAt: "2026.08.19",
    facilitiesNotice: "공식 홈페이지에서 관람객용 편의시설 상세 정보를 제공하지 않습니다.", parkingNotice: "공식 홈페이지에서 주차요금·주차구역 상세 정보를 제공하지 않습니다.",
  },
  {
    id: "exco", name: "엑스코", aliases: ["엑스코", "exco"], officialUrl: "https://www.exco.co.kr/", verifiedAt: "2026.08.19",
    facilitiesNotice: "공식 홈페이지에서 검색 가능한 관람객 편의시설 상세 정보를 확인하지 못해 제공하지 않습니다.", parkingNotice: "공식 홈페이지에서 검색 가능한 최신 주차요금 상세 정보를 확인하지 못해 제공하지 않습니다.",
  },
  {
    id: "hico", name: "경주화백컨벤션센터", aliases: ["경주화백컨벤션센터", "hico"], officialUrl: "https://www.hico.or.kr/", facilitiesUrl: "https://www.hico.or.kr/images/mice/hico/download/HICO_Facility_Guide.pdf", verifiedAt: "2026.08.19",
    facilities: [facility("restaurant", "레스토랑", "4층", "시설가이드에 안내된 식음시설"), facility("local_parking", "지하 주차장", "지하 1층", "시설가이드에서 위치 확인 가능")],
    parkingNotice: "공식 시설가이드에서 지하주차장 위치는 확인되지만 최신 요금·운영 상세 정보는 제공하지 않습니다.",
  },
  {
    id: "ceco", name: "창원컨벤션센터", aliases: ["창원컨벤션센터", "ceco"], officialUrl: "https://www.ceco.co.kr/", facilitiesUrl: "https://www.ceco.co.kr/bbx/content.php?co_id=02_05_01", parkingUrl: "https://www.ceco.co.kr/bbx/content.php?co_id=03_02_01", verifiedAt: "2026.08.19",
    facilities: [facility("info", "안내데스크", "1층 정문·동문", "09:00~18:00"), facility("restaurant", "뷔페·푸드테리아", "1층", "요일별 운영시간 상이"), facility("coffee", "카페", "1층 중앙안내데스크 맞은편", "08:30~18:00"), facility("local_convenience_store", "편의점", "1층 중앙안내데스크 옆", "24시간")],
    parking: parking("지하 1층부터 지상 3층까지 총 915대를 주차할 수 있습니다.", [], "주차정산소 055-212-1037"),
  },
  {
    id: "bexco", name: "벡스코", aliases: ["벡스코", "bexco"], officialUrl: "https://www.bexco.co.kr/", verifiedAt: "2026.08.19",
    facilitiesNotice: "공식 홈페이지에서 검색 가능한 관람객 편의시설 상세 정보를 확인하지 못해 제공하지 않습니다.", parkingNotice: "공식 홈페이지에서 검색 가능한 최신 주차 상세 페이지를 확인하지 못해 제공하지 않습니다.",
  },
];

const normalizeVenueName = (value) => String(value || "").trim().toLowerCase();

export const getVenueGuide = (venueName) => {
  const normalized = normalizeVenueName(venueName);
  return venueGuides.find((guide) =>
    guide.aliases.some((alias) => normalized.includes(alias.toLowerCase())),
  ) || null;
};
