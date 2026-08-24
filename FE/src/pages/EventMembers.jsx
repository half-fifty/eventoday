import { useEffect, useRef, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { eventApi } from "../api/eventApi.js";
import TopNav from "../components/TopNav.jsx";

export default function EventMembers() {
  const { eventId } = useParams();
  const [searchParams] = useSearchParams();
  const organizationId = searchParams.get("organizationId") || localStorage.getItem("organizationId");
  const [members, setMembers] = useState([]);
  const [memberQuery, setMemberQuery] = useState("");
  const [selectedMember, setSelectedMember] = useState(null);
  const [candidates, setCandidates] = useState([]);
  const [searching, setSearching] = useState(false);
  const [eventRole, setEventRole] = useState("EVENT_MANAGER");
  const [error, setError] = useState("");
  const [pendingMemberIds, setPendingMemberIds] = useState(() => new Set());
  const pendingMemberIdsRef = useRef(new Set());
  const currentEventIdRef = useRef(eventId);
  const loadSequenceByEventRef = useRef(new Map());
  currentEventIdRef.current = eventId;

  const memberRequestKey = (targetEventId, id) => `${targetEventId}:${id}`;
  const beginMemberRequest = (key) => {
    if (pendingMemberIdsRef.current.has(key)) return false;
    const next = new Set(pendingMemberIdsRef.current);
    next.add(key);
    pendingMemberIdsRef.current = next;
    setPendingMemberIds(next);
    return true;
  };
  const finishMemberRequest = (key) => {
    const next = new Set(pendingMemberIdsRef.current);
    next.delete(key);
    pendingMemberIdsRef.current = next;
    setPendingMemberIds(next);
  };

  const load = (targetEventId = eventId) => {
    const sequence = (loadSequenceByEventRef.current.get(targetEventId) || 0) + 1;
    loadSequenceByEventRef.current.set(targetEventId, sequence);
    return eventApi.members(targetEventId)
      .then((result) => {
        if (currentEventIdRef.current === targetEventId
            && loadSequenceByEventRef.current.get(targetEventId) === sequence) {
          setMembers(result?.data || []);
        }
      })
      .catch((requestError) => {
        if (currentEventIdRef.current === targetEventId
            && loadSequenceByEventRef.current.get(targetEventId) === sequence) {
          setError(requestError.message || "담당자를 불러오지 못했습니다.");
        }
      });
  };
  useEffect(() => {
    setMembers([]);
    setError("");
    load(eventId);
  }, [eventId]);

  useEffect(() => {
    if (memberQuery.trim().length < 2 || selectedMember) {
      setCandidates([]);
      return undefined;
    }
    const timer = window.setTimeout(() => {
      setSearching(true);
      eventApi.memberCandidates(eventId, memberQuery.trim())
        .then((result) => setCandidates(result?.data || []))
        .catch((requestError) => setError(requestError.message || "담당자를 검색하지 못했습니다."))
        .finally(() => setSearching(false));
    }, 300);
    return () => window.clearTimeout(timer);
  }, [eventId, memberQuery, selectedMember]);

  const add = async (e) => {
    e.preventDefault(); setError("");
    const requestEventId = eventId;
    try {
      if (!selectedMember) {
        setError("이메일 또는 계정 이름으로 담당자를 검색해 선택해 주세요.");
        return;
      }
      await eventApi.addMember(requestEventId, { memberId: selectedMember.memberId, eventRole });
      if (currentEventIdRef.current === requestEventId) {
        setMemberQuery("");
        setSelectedMember(null);
      }
      await load(requestEventId);
    } catch (requestError) {
      if (currentEventIdRef.current === requestEventId) {
        setError(requestError.message || "담당자를 추가하지 못했습니다.");
      }
    }
  };
  const toggle = async (member) => {
    const requestEventId = eventId;
    const requestKey = memberRequestKey(requestEventId, member.memberId);
    if (!beginMemberRequest(requestKey)) return;
    setError("");
    try {
      await eventApi.updateMember(requestEventId, member.memberId, { eventRole: member.eventRole, active: !member.active });
      await load(requestEventId);
    } catch (requestError) {
      if (currentEventIdRef.current === requestEventId) {
        setError(requestError.message || "담당자 상태를 변경하지 못했습니다.");
      }
    } finally { finishMemberRequest(requestKey); }
  };
  const remove = async (id) => {
    const requestEventId = eventId;
    const requestKey = memberRequestKey(requestEventId, id);
    if (!beginMemberRequest(requestKey)) return;
    setError("");
    try { await eventApi.removeMember(requestEventId, id); await load(requestEventId); }
    catch (requestError) {
      if (currentEventIdRef.current === requestEventId) {
        setError(requestError.message || "담당자를 제거하지 못했습니다.");
      }
    }
    finally { finishMemberRequest(requestKey); }
  };

  return <><TopNav active="organizer" /><main className="min-h-screen bg-surface-container-low px-lg pb-xl pt-[76px] md:px-xl">
    <section className="max-w-[800px] mx-auto space-y-lg">
      <div className="flex justify-between items-center"><div><p className="text-caption text-primary">ORGANIZER CENTER</p><h1 className="font-display-lg text-[28px]">행사 담당자 관리</h1></div><Link to={`/organizer-admin?organizationId=${organizationId || ""}&eventId=${eventId}`}>돌아가기</Link></div>
      <form onSubmit={add} className="relative bg-white border border-hairline rounded-xl p-lg flex flex-wrap gap-sm">
        <div className="relative flex-1 min-w-[240px]">
          <input required value={memberQuery} onChange={(e)=>{ setMemberQuery(e.target.value); setSelectedMember(null); }} placeholder="이메일 또는 계정 이름 검색" className="w-full h-10 border border-hairline rounded-lg px-md"/>
          {!selectedMember && memberQuery.trim().length >= 2 && (
            <div className="absolute left-0 right-0 top-11 z-20 max-h-56 overflow-y-auto rounded-xl border border-hairline bg-white shadow-xl">
              {searching ? <p className="p-md text-caption text-ink-muted">검색 중...</p> : candidates.length === 0 ? <p className="p-md text-caption text-ink-muted">검색 결과가 없습니다.</p> : candidates.map((candidate) => (
                <button key={candidate.memberId} type="button" onClick={()=>{ setSelectedMember(candidate); setMemberQuery(`${candidate.nickname} (${candidate.email})`); setCandidates([]); }} className="block w-full border-b border-divider-soft p-md text-left last:border-0 hover:bg-surface-container">
                  <span className="block font-body-strong text-sm">{candidate.nickname}</span><span className="text-caption text-ink-muted">{candidate.email}</span>
                </button>
              ))}
            </div>
          )}
        </div>
        <select value={eventRole} onChange={(e)=>setEventRole(e.target.value)} className="h-10 border border-hairline rounded-lg px-md"><option value="EVENT_MANAGER">행사 관리자</option><option value="CHECKIN_STAFF">입장 스태프</option></select>
        <button className="h-10 px-lg bg-primary text-white rounded-full">추가</button>
      </form>
      {error && <p className="bg-error/10 text-error p-md rounded-lg">{error}</p>}
      <div className="bg-white border border-hairline rounded-xl divide-y divide-divider-soft">
        {members.length === 0 && <p className="p-lg text-ink-muted">등록된 담당자가 없습니다.</p>}
        {members.map((member)=><div key={member.memberId} className="p-lg flex items-center gap-md">
          <div className="flex-1"><p className="font-body-strong">{member.nickname || member.email || "담당자"}</p><p className="text-caption text-ink-muted">{member.email || "계정 정보 없음"} · {member.eventRole === "EVENT_MANAGER" ? "행사 관리자" : "입장 스태프"}</p></div>
          <button disabled={pendingMemberIds.has(memberRequestKey(eventId, member.memberId))} onClick={()=>toggle(member)} className="text-caption px-md py-xs border border-hairline rounded-full disabled:opacity-50">{member.active ? "활성" : "비활성"}</button>
          <button disabled={pendingMemberIds.has(memberRequestKey(eventId, member.memberId))} onClick={()=>remove(member.memberId)} className="text-caption text-error disabled:opacity-50">해제</button>
        </div>)}
      </div>
    </section>
  </main></>;
}
