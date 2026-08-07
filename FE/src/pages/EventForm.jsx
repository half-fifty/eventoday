import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { eventApi } from "../api/eventApi.js";
import { locationApi } from "../api/locationApi.js";
import KakaoMapPreview from "../components/KakaoMapPreview.jsx";
import Icon from "../components/Icon.jsx";
import FileUploadField from "../components/FileUploadField.jsx";
import { EXHIBIT_CATEGORIES } from "../constants/eventOptions.js";
import TopNav from "../components/TopNav.jsx";

const emptyForm = {
  name: "", eventType: "EXPO", shortDescription: "", description: "",
  venueName: "", address: "", contactEmail: "", contactPhone: "", postalCode: "", addressDetail: "",
  latitude: null, longitude: null, kakaoPlaceId: null,
  startAt: "", endAt: "", ticketSalesStartAt: "", ticketSalesEndAt: "",
  ticketPrice: 0, ticketTotalQuantity: 100, ticketPurchaseLimit: 1,
  representativeFileId: null, boothRecruitmentEnabled: false,
  venueMapEnabled: false, boothReservationEnabled: false, noShowGraceMinutes: 10,
  exhibitCategoryCodes: [],
};

const toInputDateTime = (value) => {
  if (!value) return "";
  const date = new Date(value);
  const pad = (number) => String(number).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};
const toOffsetDateTime = (value) => value ? new Date(value).toISOString() : null;

export default function EventForm() {
  const { eventId } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const requestedOrganizationId = searchParams.get("organizationId") || localStorage.getItem("organizationId") || "";
  const [organizationId, setOrganizationId] = useState(requestedOrganizationId);
  const [organizations, setOrganizations] = useState([]);
  const [organizationLoading, setOrganizationLoading] = useState(true);
  const [organizationLoadFailed, setOrganizationLoadFailed] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [loading, setLoading] = useState(Boolean(eventId));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [placeQuery, setPlaceQuery] = useState("");
  const [places, setPlaces] = useState([]);
  const [searching, setSearching] = useState(false);
  const [placeError, setPlaceError] = useState("");
  const placeSearchSequence = useRef(0);
  const postalCodeSequence = useRef(0);

  useEffect(() => {
    eventApi.managedOrganizations()
      .then((result) => {
        const available = result?.data || [];
        setOrganizations(available);
        const allowedRequested = available.some((organization) => String(organization.id) === String(requestedOrganizationId));
        const selected = allowedRequested ? String(requestedOrganizationId)
          : available.length === 1 ? String(available[0].id) : "";
        setOrganizationId(selected);
        if (selected) localStorage.setItem("organizationId", selected);
      })
      .catch((requestError) => {
        setOrganizationLoadFailed(true);
        setError(requestError.status === 401
          ? "행사를 등록하려면 먼저 로그인해 주세요."
          : "운영 가능한 조직 정보를 불러오지 못했습니다.");
      })
      .finally(() => setOrganizationLoading(false));
  }, [requestedOrganizationId]);

  useEffect(() => {
    if (!eventId) {
      setForm(emptyForm);
      setLoading(false);
      return;
    }
    if (organizationLoading) {
      setLoading(true);
      return;
    }
    if (!organizationId) {
      setForm(emptyForm);
      setLoading(false);
      return;
    }
    let cancelled = false;
    setForm(emptyForm);
    setError("");
    setLoading(true);
    eventApi.managedDetail(organizationId, eventId)
      .then((result) => {
        if (cancelled) return;
        const data = result?.data;
        setForm({ ...emptyForm, ...data, startAt: toInputDateTime(data.startAt),
          endAt: toInputDateTime(data.endAt), ticketSalesStartAt: toInputDateTime(data.ticketSalesStartAt),
          ticketSalesEndAt: toInputDateTime(data.ticketSalesEndAt) });
      })
      .catch((requestError) => !cancelled && setError(requestError.message || "행사를 불러오지 못했습니다."))
      .finally(() => !cancelled && setLoading(false));
    return () => { cancelled = true; };
  }, [eventId, organizationId, organizationLoading]);

  const change = (key, value) => setForm((previous) => ({ ...previous, [key]: value }));
  const toggleCategory = (code) => setForm((previous) => ({ ...previous, exhibitCategoryCodes: previous.exhibitCategoryCodes.includes(code) ? previous.exhibitCategoryCodes.filter((item) => item !== code) : [...previous.exhibitCategoryCodes, code] }));
  const searchPlaces = async (e) => {
    e.preventDefault();
    if (placeQuery.trim().length < 2) {
      placeSearchSequence.current += 1;
      setPlaces([]);
      setSearching(false);
      setPlaceError("두 글자 이상 입력해 주세요.");
      return;
    }
    const sequence = ++placeSearchSequence.current;
    setSearching(true); setPlaceError("");
    try {
      const result = await locationApi.search(placeQuery.trim());
      if (sequence !== placeSearchSequence.current) return;
      setPlaces(result?.data || []);
      if (!result?.data?.length) setPlaceError("검색 결과가 없습니다.");
    } catch (requestError) {
      if (sequence === placeSearchSequence.current) setPlaceError(requestError.message || "장소를 검색하지 못했습니다.");
    } finally {
      if (sequence === placeSearchSequence.current) setSearching(false);
    }
  };
  const changePlaceQuery = (value) => {
    placeSearchSequence.current += 1;
    setPlaceQuery(value);
    setPlaces([]);
    setSearching(false);
    setPlaceError("");
  };
  const selectPlace = async (place) => {
    const sequence = ++postalCodeSequence.current;
    const selectedAddress = place.roadAddress || place.address;
    setForm((previous) => ({ ...previous, venueName: place.name,
      address: selectedAddress, postalCode: "", latitude: place.latitude,
      longitude: place.longitude, kakaoPlaceId: place.placeId }));
    setPlaces([]); setPlaceQuery(place.name);
    try {
      const result = await locationApi.postalCode(selectedAddress);
      if (sequence === postalCodeSequence.current) change("postalCode", result?.data?.postalCode || "");
    } catch {
      if (sequence === postalCodeSequence.current) setPlaceError("장소는 선택했지만 우편번호를 자동 조회하지 못했습니다.");
    }
  };
  const submit = async (e) => {
    e.preventDefault();
    if (!organizationId) { setError("행사를 등록할 운영 조직을 선택해 주세요."); return; }
    if (!form.exhibitCategoryCodes.length) { setError("전시품목을 하나 이상 선택해 주세요."); return; }
    if (new Date(form.startAt) >= new Date(form.endAt)) { setError("행사 종료는 시작 이후여야 합니다."); return; }
    setSaving(true); setError("");
    const payload = { ...form, ticketPrice: Number(form.ticketPrice),
      ticketTotalQuantity: Number(form.ticketTotalQuantity), ticketPurchaseLimit: Number(form.ticketPurchaseLimit),
      noShowGraceMinutes: Number(form.noShowGraceMinutes), startAt: toOffsetDateTime(form.startAt),
      endAt: toOffsetDateTime(form.endAt), ticketSalesStartAt: toOffsetDateTime(form.ticketSalesStartAt),
      ticketSalesEndAt: toOffsetDateTime(form.ticketSalesEndAt) };
    try {
      const result = eventId ? await eventApi.update(organizationId, eventId, payload)
        : await eventApi.create(organizationId, payload);
      navigate(`/organizer-admin?organizationId=${organizationId}&eventId=${result.data.id}`);
    } catch (requestError) { setError(requestError.message || "저장하지 못했습니다."); }
    finally { setSaving(false); }
  };

  const field = "w-full h-11 border border-hairline rounded-lg px-md bg-white outline-none focus:border-primary";
  const section = "bg-white border border-hairline rounded-2xl p-lg md:p-xl space-y-lg";
  if (loading) return <div className="min-h-screen grid place-items-center">행사를 불러오는 중입니다.</div>;
  return <><TopNav active="organizer" /><main className="min-h-screen bg-surface-container-low px-lg pb-xl pt-[76px]">
    <form onSubmit={submit} className="max-w-[1040px] mx-auto space-y-lg">
      <header className="flex justify-between items-end gap-md"><div><p className="text-caption text-primary">ORGANIZER CENTER</p><h1 className="font-display-lg text-[32px]">{eventId ? "행사 수정" : "새 행사 등록"}</h1><p className="text-ink-muted mt-xs">행사 공개 전까지 언제든 수정할 수 있습니다.</p></div><Link to={`/organizer-admin?organizationId=${organizationId || ""}`} className="text-caption">돌아가기</Link></header>
      {error && <p className="bg-error/10 border border-error/20 text-error p-md rounded-xl">{error}</p>}

      <section className={section}>
        <div><p className="text-caption text-primary mb-xs">ORGANIZATION</p><h2 className="font-display-md text-[22px]">운영 조직</h2></div>
        {organizationLoading ? <p className="text-ink-muted">운영 가능한 조직을 확인하는 중입니다.</p>
          : organizationLoadFailed ? <p className="text-ink-muted">로그인 후 운영 조직을 확인할 수 있습니다.</p>
          : organizations.length === 0 ? <p className="text-error">행사를 등록할 수 있는 운영 조직이 없습니다. 조직의 OWNER 또는 MANAGER 권한이 필요합니다.</p>
          : <label>행사를 등록할 조직 *<select required className={field} value={organizationId} onChange={(e)=>{setOrganizationId(e.target.value); localStorage.setItem("organizationId", e.target.value);}}><option value="">조직을 선택하세요</option>{organizations.map((organization)=><option key={organization.id} value={organization.id}>{organization.name}</option>)}</select></label>}
      </section>

      <section className={section}>
        <div><p className="text-caption text-primary mb-xs">01</p><h2 className="font-display-md text-[22px]">기본 정보</h2></div>
        <div className="grid md:grid-cols-2 gap-md">
          <label>행사명 *<input required maxLength={200} className={field} value={form.name} onChange={(e)=>change("name",e.target.value)} placeholder="행사명을 입력하세요"/></label>
          <label>행사 유형 *<select className={field} value={form.eventType} onChange={(e)=>change("eventType",e.target.value)}><option value="EXPO">박람회</option><option value="EXHIBITION">전시회</option><option value="SEMINAR">세미나</option><option value="CONFERENCE">컨퍼런스</option></select></label>
          <label className="md:col-span-2">한 줄 소개<input maxLength={300} className={field} value={form.shortDescription || ""} onChange={(e)=>change("shortDescription",e.target.value)} placeholder="목록에 표시할 짧은 소개"/></label>
          <label className="md:col-span-2">상세 설명 *<textarea required className={`${field} h-36 py-md`} value={form.description} onChange={(e)=>change("description",e.target.value)} placeholder="행사의 목적과 주요 프로그램을 소개해 주세요"/></label>
        </div>
      </section>

      <section className={section}>
        <div><p className="text-caption text-primary mb-xs">02</p><h2 className="font-display-md text-[22px]">행사 장소</h2><p className="text-caption text-ink-muted mt-xs">시설명을 검색하면 주소와 지도 위치가 자동 입력됩니다.</p></div>
        <div className="grid lg:grid-cols-2 gap-lg">
          <div className="space-y-md">
            <div><label>장소 검색</label><div className="flex gap-sm mt-xs"><input className={field} value={placeQuery} onChange={(e)=>changePlaceQuery(e.target.value)} onKeyDown={(e)=>{ if(e.key === "Enter") searchPlaces(e); }} placeholder="예: 코엑스, 킨텍스"/><button type="button" onClick={searchPlaces} disabled={searching} className="shrink-0 px-lg bg-primary text-white rounded-lg disabled:opacity-50"><Icon name="search"/> {searching ? "검색 중" : "검색"}</button></div></div>
            {placeError && <p className="text-caption text-error">{placeError}</p>}
            {places.length > 0 && <div className="border border-hairline rounded-xl divide-y divide-divider-soft max-h-64 overflow-y-auto">{places.map((place)=><button type="button" key={place.placeId} onClick={()=>selectPlace(place)} className="w-full text-left p-md hover:bg-primary/5"><p className="font-body-strong">{place.name}</p><p className="text-caption text-ink-muted">{place.roadAddress || place.address}</p><p className="text-[11px] text-ink-muted mt-xs">{place.category}</p></button>)}</div>}
            <div className="grid grid-cols-[120px_1fr] gap-md"><label>우편번호<input maxLength={10} className={field} value={form.postalCode || ""} onChange={(e)=>change("postalCode",e.target.value)}/></label><label>장소명 *<input required className={field} value={form.venueName} onChange={(e)=>change("venueName",e.target.value)}/></label></div>
            <label>도로명 주소 *<input required className={field} value={form.address} onChange={(e)=>change("address",e.target.value)}/></label>
            <label>상세 주소<input maxLength={200} className={field} value={form.addressDetail || ""} onChange={(e)=>change("addressDetail",e.target.value)} placeholder="홀, 층, 출입구 등"/></label>
          </div>
          <KakaoMapPreview latitude={form.latitude} longitude={form.longitude} venueName={form.venueName}/>
        </div>
      </section>

      <section className={section}>
        <div><p className="text-caption text-primary mb-xs">03</p><h2 className="font-display-md text-[22px]">일정 및 티켓</h2></div>
        <div className="grid md:grid-cols-2 gap-md">
          <label>행사 시작 *<input required type="datetime-local" className={field} value={form.startAt} onChange={(e)=>change("startAt",e.target.value)}/></label><label>행사 종료 *<input required type="datetime-local" className={field} value={form.endAt} onChange={(e)=>change("endAt",e.target.value)}/></label>
          <label>티켓 판매 시작<input type="datetime-local" className={field} value={form.ticketSalesStartAt || ""} onChange={(e)=>change("ticketSalesStartAt",e.target.value)}/></label><label>티켓 판매 종료<input type="datetime-local" className={field} value={form.ticketSalesEndAt || ""} onChange={(e)=>change("ticketSalesEndAt",e.target.value)}/></label>
          <label>가격<input min="0" type="number" className={field} value={form.ticketPrice} onChange={(e)=>change("ticketPrice",e.target.value)}/></label><label>총 티켓 수량<input min="0" type="number" className={field} value={form.ticketTotalQuantity} onChange={(e)=>change("ticketTotalQuantity",e.target.value)}/></label>
          <label>1회 구매 제한<input min="1" type="number" className={field} value={form.ticketPurchaseLimit} onChange={(e)=>change("ticketPurchaseLimit",e.target.value)}/></label><label>노쇼 유예 시간(분)<input min="0" type="number" className={field} value={form.noShowGraceMinutes} onChange={(e)=>change("noShowGraceMinutes",e.target.value)}/></label>
        </div>
      </section>

      <section className={section}>
        <div><p className="text-caption text-primary mb-xs">04</p><h2 className="font-display-md text-[22px]">문의처 및 포스터</h2><p className="text-caption text-ink-muted mt-xs">방문객에게 공개되는 공식 문의처입니다. 개인번호보다 행사 대표번호 사용을 권장합니다.</p></div>
        <div className="grid md:grid-cols-2 gap-md">
          <label>담당자 이메일 *<input required type="email" maxLength={255} className={field} value={form.contactEmail} onChange={(e)=>change("contactEmail",e.target.value)} placeholder="event@example.com"/></label>
          <label>담당자 연락처 *<input required type="tel" maxLength={30} pattern="[0-9-]{9,14}" title="숫자와 하이픈을 사용해 입력해 주세요." className={field} value={form.contactPhone} onChange={(e)=>change("contactPhone",e.target.value.replace(/[^0-9-]/g, ""))} placeholder="02-1234-5678"/><span className="block text-caption text-ink-muted mt-xs">예: 02-1234-5678, 031-123-4567</span></label>
        </div>
        <FileUploadField label="행사 포스터" required value={form.representativeFileId}
          onChange={(fileId) => change("representativeFileId", fileId)} />
        <div className="rounded-xl bg-primary/5 border border-primary/10 p-md text-caption text-on-surface-variant"><strong className="block text-primary mb-xs">포스터 권장 사양</strong>세로형 3:4 비율, 최소 900×1200px 이미지를 권장합니다. 행사 목록과 상세 페이지에 동일한 이미지가 사용됩니다.</div>
      </section>

      <section className={section}>
        <div><p className="text-caption text-primary mb-xs">02</p><h2 className="font-display-md text-[22px]">전시품목</h2><p className="text-caption text-ink-muted mt-xs">행사에서 다루는 품목을 최대 5개까지 선택해 주세요.</p></div>
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-sm">{EXHIBIT_CATEGORIES.map(([code,label]) => { const selected = form.exhibitCategoryCodes.includes(code); return <label key={code} className={`flex items-center gap-sm border rounded-xl p-md cursor-pointer ${selected ? "border-primary bg-primary/5 text-primary" : "border-hairline"}`}><input type="checkbox" checked={selected} disabled={!selected && form.exhibitCategoryCodes.length >= 5} onChange={() => toggleCategory(code)} /><span className="text-caption font-body-strong">{label}</span></label>; })}</div>
      </section>

      <section className={section}>
        <div><p className="text-caption text-primary mb-xs">05</p><h2 className="font-display-md text-[22px]">운영 기능</h2></div>
        <div className="grid md:grid-cols-3 gap-md">{[["boothRecruitmentEnabled","부스 모집","참가 조직의 부스 신청을 받습니다."],["venueMapEnabled","평면도","행사장 평면도를 제공합니다."],["boothReservationEnabled","부스 예약","방문자가 부스 시간을 예약합니다."]].map(([key,label,help])=><label key={key} className={`border rounded-xl p-md cursor-pointer ${form[key] ? "border-primary bg-primary/5" : "border-hairline"}`}><span className="flex gap-sm items-center"><input type="checkbox" checked={form[key]} onChange={(e)=>change(key,e.target.checked)}/><strong>{label}</strong></span><span className="block text-caption text-ink-muted mt-sm">{help}</span></label>)}</div>
      </section>
      <div className="sticky bottom-md bg-white/90 backdrop-blur border border-hairline rounded-2xl p-md flex justify-between items-center shadow-lg"><p className="text-caption text-ink-muted hidden sm:block">필수 항목을 확인한 후 저장해 주세요.</p><button disabled={saving} className="w-full sm:w-auto px-xxl py-md bg-primary text-white rounded-full font-body-strong disabled:opacity-50">{saving ? "저장 중..." : "행사 저장"}</button></div>
    </form>
  </main></>;
}
