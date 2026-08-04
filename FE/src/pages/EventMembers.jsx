import { useEffect, useRef, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { eventApi } from "../api/eventApi.js";

export default function EventMembers() {
  const { eventId } = useParams();
  const [searchParams] = useSearchParams();
  const organizationId = searchParams.get("organizationId") || localStorage.getItem("organizationId");
  const [members, setMembers] = useState([]);
  const [memberId, setMemberId] = useState("");
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

  const add = async (e) => {
    e.preventDefault(); setError("");
    const requestEventId = eventId;
    try {
      await eventApi.addMember(requestEventId, { memberId: Number(memberId), eventRole });
      if (currentEventIdRef.current === requestEventId) setMemberId("");
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

  return <main className="min-h-screen bg-surface-container-low p-lg md:p-xl">
    <section className="max-w-[800px] mx-auto space-y-lg">
      <div className="flex justify-between items-center"><div><p className="text-caption text-primary">EVENT #{eventId}</p><h1 className="font-display-lg text-[28px]">행사 담당자 관리</h1></div><Link to={`/organizer-admin?organizationId=${organizationId || ""}&eventId=${eventId}`}>돌아가기</Link></div>
      <form onSubmit={add} className="bg-white border border-hairline rounded-xl p-lg flex flex-wrap gap-sm">
        <input required type="number" min="1" value={memberId} onChange={(e)=>setMemberId(e.target.value)} placeholder="회원 ID" className="flex-1 min-w-[180px] h-10 border border-hairline rounded-lg px-md"/>
        <select value={eventRole} onChange={(e)=>setEventRole(e.target.value)} className="h-10 border border-hairline rounded-lg px-md"><option value="EVENT_MANAGER">행사 관리자</option><option value="CHECKIN_STAFF">입장 스태프</option></select>
        <button className="h-10 px-lg bg-primary text-white rounded-full">추가</button>
      </form>
      {error && <p className="bg-error/10 text-error p-md rounded-lg">{error}</p>}
      <div className="bg-white border border-hairline rounded-xl divide-y divide-divider-soft">
        {members.length === 0 && <p className="p-lg text-ink-muted">등록된 담당자가 없습니다.</p>}
        {members.map((member)=><div key={member.memberId} className="p-lg flex items-center gap-md">
          <div className="flex-1"><p className="font-body-strong">회원 #{member.memberId}</p><p className="text-caption text-ink-muted">{member.eventRole === "EVENT_MANAGER" ? "행사 관리자" : "입장 스태프"}</p></div>
          <button disabled={pendingMemberIds.has(memberRequestKey(eventId, member.memberId))} onClick={()=>toggle(member)} className="text-caption px-md py-xs border border-hairline rounded-full disabled:opacity-50">{member.active ? "활성" : "비활성"}</button>
          <button disabled={pendingMemberIds.has(memberRequestKey(eventId, member.memberId))} onClick={()=>remove(member.memberId)} className="text-caption text-error disabled:opacity-50">해제</button>
        </div>)}
      </div>
    </section>
  </main>;
}
