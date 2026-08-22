import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

// 선택한 날짜 기준 전체 행사의 방문수/결제완료수를 막대그래프로 랭킹한다 (관리자센터 전용).
const truncate = (name, max = 10) => (name.length > max ? `${name.slice(0, max)}…` : name);

export default function FunnelEventRankingChart({ events }) {
  const data = events.map((item) => ({
    eventId: item.eventId,
    label: truncate(item.eventName),
    fullName: item.eventName,
    방문수: item.totalSessions,
    결제완료: item.completePaymentCount,
  }));

  return (
    <div className="h-[320px] w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="label" tick={{ fontSize: 12 }} interval={0} angle={-20} textAnchor="end" height={50} />
          <YAxis allowDecimals={false} />
          <Tooltip
            formatter={(value) => Number(value).toLocaleString()}
            labelFormatter={(_, payload) => payload?.[0]?.payload?.fullName || ""}
          />
          <Legend />
          <Bar dataKey="방문수" fill="#004e9f" radius={[6, 6, 0, 0]} />
          <Bar dataKey="결제완료" fill="#34c759" radius={[6, 6, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
